package com.dino.testsupport;

import com.dino.infrastructure.network.NetworkPeer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

/**
 * Implementación en memoria de {@link NetworkPeer} para pruebas unitarias.
 */
public final class FakeNetworkPeer implements NetworkPeer {
    private final Queue<Map.Entry<Map<String, Object>, InetSocketAddress>> incomingMessages = new ArrayDeque<>();
    private final List<SentMessage> sentMessages = new ArrayList<>();
    private final List<BroadcastMessage> broadcastMessages = new ArrayList<>();
    private final List<BurstBroadcastMessage> burstMessages = new ArrayList<>();

    private boolean bound;

    /**
     * Marca el peer como enlazado sin abrir sockets reales.
     *
     * @param ip IP local solicitada por la prueba
     * @param port puerto local solicitado por la prueba
     * @throws IOException nunca se produce en esta implementación en memoria
     */
    @Override
    public void bind(String ip, int port) throws IOException {
        bound = true;
    }

    /**
     * Registra un envío directo realizado por el código bajo prueba.
     *
     * @param data payload lógico enviado
     * @param addr dirección remota de destino
     * @param port puerto remoto de destino
     */
    @Override
    public void send(Map<String, Object> data, InetAddress addr, int port) {
        sentMessages.add(new SentMessage(data, addr, port));
    }

    /**
     * Registra un broadcast simple sin emitir tráfico real.
     *
     * @param data payload lógico compartido
     * @param addrs destinos incluidos en el broadcast
     */
    @Override
    public void broadcast(Map<String, Object> data, List<InetSocketAddress> addrs) {
        broadcastMessages.add(new BroadcastMessage(data, List.copyOf(addrs)));
    }

    /**
     * Registra un broadcast crítico con repeticiones.
     *
     * @param data payload lógico compartido
     * @param addrs destinos incluidos en la ráfaga
     * @param repeats cantidad de repeticiones solicitadas
     * @param delayMs pausa declarada entre repeticiones
     */
    @Override
    public void broadcastBurst(Map<String, Object> data, List<InetSocketAddress> addrs, int repeats, int delayMs) {
        burstMessages.add(new BurstBroadcastMessage(data, List.copyOf(addrs), repeats, delayMs));
    }

    /**
     * Devuelve el siguiente mensaje previamente encolado por la prueba.
     *
     * @return payload y remitente simulados si había mensajes pendientes
     */
    @Override
    public Optional<Map.Entry<Map<String, Object>, InetSocketAddress>> receive() {
        return Optional.ofNullable(incomingMessages.poll());
    }

    /**
     * Marca el peer como cerrado.
     */
    @Override
    public void close() {
        bound = false;
    }

    /**
     * Indica si la prueba ya pidió enlazar este peer.
     *
     * @return {@code true} cuando {@link #bind(String, int)} fue invocado y no
     *         se cerró después
     */
    @Override
    public boolean isBound() {
        return bound;
    }

    /**
     * Encola un mensaje entrante que será consumido por {@link #receive()}.
     *
     * @param message payload a simular como recibido
     * @param sender dirección remota asociada al mensaje
     */
    public void queueIncoming(Map<String, Object> message, InetSocketAddress sender) {
        incomingMessages.add(new AbstractMap.SimpleEntry<>(message, sender));
    }

    /**
     * Retorna una copia inmutable de los envíos directos registrados.
     *
     * @return mensajes enviados a un único destino
     */
    public List<SentMessage> getSentMessages() {
        return List.copyOf(sentMessages);
    }

    /**
     * Retorna una copia inmutable de los broadcasts simples registrados.
     *
     * @return broadcasts emitidos sin ráfaga
     */
    public List<BroadcastMessage> getBroadcastMessages() {
        return List.copyOf(broadcastMessages);
    }

    /**
     * Retorna una copia inmutable de los broadcasts críticos registrados.
     *
     * @return broadcasts emitidos con repeticiones
     */
    public List<BurstBroadcastMessage> getBurstMessages() {
        return List.copyOf(burstMessages);
    }

    /**
     * Mensaje puntual enviado a un destino específico.
     *
     * @param payload contenido serializable del mensaje
     * @param address dirección remota de destino
     * @param port puerto remoto de destino
     */
    public record SentMessage(Map<String, Object> payload, InetAddress address, int port) {
    }

    /**
     * Broadcast simple emitido a una colección de peers.
     *
     * @param payload contenido serializable compartido
     * @param addresses destinos incluidos en el broadcast
     */
    public record BroadcastMessage(Map<String, Object> payload, List<InetSocketAddress> addresses) {
    }

    /**
     * Broadcast crítico emitido con repeticiones.
     *
     * @param payload contenido serializable compartido
     * @param addresses destinos incluidos en la ráfaga
     * @param repeats cantidad de repeticiones emitidas
     * @param delayMs pausa entre repeticiones en milisegundos
     */
    public record BurstBroadcastMessage(Map<String, Object> payload,
                                        List<InetSocketAddress> addresses,
                                        int repeats,
                                        int delayMs) {
    }
}
