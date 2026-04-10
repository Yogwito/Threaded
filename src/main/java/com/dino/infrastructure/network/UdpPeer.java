package com.dino.infrastructure.network;

import com.dino.infrastructure.serialization.MessageCodec;

import java.io.IOException;
import java.net.*;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adaptador mínimo sobre {@link DatagramSocket} para la comunicación UDP.
 *
 * <p>Su responsabilidad es encapsular el envío y recepción de mapas
 * serializados como JSON, manteniendo una API pequeña para el resto del
 * proyecto. No implementa reglas de juego ni sincronización avanzada; solo
 * transporte.</p>
 */
public class UdpPeer implements NetworkPeer {
    private static final Logger LOGGER = Logger.getLogger(UdpPeer.class.getName());
    private DatagramSocket socket;
    private final MessageCodec codec;
    private static final int BUFFER_SIZE = 65535;
    private final byte[] receiveBuffer = new byte[BUFFER_SIZE];

    /**
     * Crea un peer UDP respaldado por el codec de mensajes indicado.
     *
     * @param codec estrategia usada para codificar y decodificar mensajes
     */
    public UdpPeer(MessageCodec codec) {
        this.codec = codec;
    }

    /**
     * Enlaza el socket UDP local.
     *
     * @param ip IP local a usar; si es {@code 0.0.0.0} o vacía, escucha en todas
     *           las interfaces
     * @param port puerto local
     * @throws IOException si el socket no puede abrirse
     */
    public void bind(String ip, int port) throws IOException {
        socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        if (ip == null || ip.isBlank() || ip.equals("0.0.0.0")) {
            socket.bind(new InetSocketAddress(Inet4Address.getByName("0.0.0.0"), port));
        } else {
            socket.bind(new InetSocketAddress(Inet4Address.getByName(ip), port));
        }
        socket.setSoTimeout(1);
    }

    /**
     * Envía un datagrama UDP al destino indicado.
     *
     * @param data mensaje ya construido como mapa
     * @param addr dirección IP destino
     * @param port puerto destino
     */
    public void send(Map<String, Object> data, InetAddress addr, int port) {
        if (socket == null || socket.isClosed()) {
            LOGGER.log(Level.FINE, "Se ignora envio UDP porque el socket no esta activo");
            return;
        }
        try {
            byte[] bytes = codec.serialize(data);
            socket.send(new DatagramPacket(bytes, bytes.length, addr, port));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error enviando UDP a {0}:{1}", new Object[]{addr, port});
            LOGGER.log(Level.FINE, "Detalle del fallo de envio UDP", e);
        }
    }

    /**
     * Envía el mismo mensaje a varios peers.
     *
     * @param data contenido a enviar
     * @param addrs lista de destinos remotos
     */
    public void broadcast(Map<String, Object> data, List<InetSocketAddress> addrs) {
        for (InetSocketAddress addr : addrs) {
            send(data, addr.getAddress(), addr.getPort());
        }
    }

    /**
     * Repite varias veces un envío importante para reducir la pérdida percibida.
     *
     * <p>Se usa en transiciones críticas como {@code START_GAME} y
     * {@code GAME_OVER}, donde perder un único datagrama sería muy visible.</p>
     *
     * @param data mensaje a reenviar
     * @param addrs peers destino
     * @param repeats cantidad total de emisiones, incluida la primera
     * @param delayMs pausa entre repeticiones
     */
    public void broadcastBurst(Map<String, Object> data, List<InetSocketAddress> addrs, int repeats, int delayMs) {
        if (repeats <= 0) return;
        broadcast(data, addrs);
        if (repeats == 1) return;
        Thread.ofPlatform().daemon(true).start(() -> {
            for (int i = 1; i < repeats; i++) {
                broadcast(data, addrs);
                if (i + 1 < repeats) {
                    try {
                        Thread.sleep(Math.max(1, delayMs));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
    }

    /**
     * Intenta recibir un datagrama sin bloquear perceptiblemente el loop.
     *
     * @return mensaje y dirección de origen si hubo datos; vacío en timeout o error
     */
    @SuppressWarnings("unchecked")
    public Optional<Map.Entry<Map<String, Object>, InetSocketAddress>> receive() {
        if (socket == null || socket.isClosed()) return Optional.empty();
        try {
            DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(packet);
            byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
            Map<String, Object> msg = codec.deserialize(data);
            InetSocketAddress sender = new InetSocketAddress(packet.getAddress(), packet.getPort());
            return Optional.of(Map.entry(msg, sender));
        } catch (SocketTimeoutException e) {
            return Optional.empty();
        } catch (IOException e) {
            if (socket != null && !socket.isClosed()) {
                LOGGER.log(Level.FINE, "Fallo recibiendo UDP", e);
            }
            return Optional.empty();
        }
    }

    /**
     * Cierra el socket si está abierto.
     */
    public void close() {
        if (socket != null && !socket.isClosed()) socket.close();
    }

    /**
     * Indica si el socket está listo para enviar y recibir.
     *
     * @return {@code true} si el socket fue enlazado y no está cerrado
     */
    public boolean isBound() {
        return socket != null && socket.isBound() && !socket.isClosed();
    }
}
