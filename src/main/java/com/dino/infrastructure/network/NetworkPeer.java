package com.dino.infrastructure.network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Abstracción pequeña del transporte de red usado por el juego.
 *
 * <p>Permite desacoplar controladores y casos de uso de la implementación
 * concreta basada en {@link java.net.DatagramSocket}.</p>
 */
public interface NetworkPeer {
    /**
     * Enlaza el transporte a una IP/puerto local.
     *
     * @param ip dirección local a usar
     * @param port puerto local UDP
     * @throws IOException si el socket no puede abrirse o enlazarse
     */
    void bind(String ip, int port) throws IOException;

    /**
     * Envía un mensaje al destino indicado.
     *
     * @param data payload lógico del mensaje
     * @param addr IP de destino
     * @param port puerto de destino
     */
    void send(Map<String, Object> data, InetAddress addr, int port);

    /**
     * Reenvía un mismo mensaje a varios peers remotos.
     *
     * @param data payload lógico del mensaje
     * @param addrs destinos remotos
     */
    void broadcast(Map<String, Object> data, List<InetSocketAddress> addrs);

    /**
     * Repite varias veces un broadcast crítico.
     *
     * @param data payload lógico del mensaje
     * @param addrs destinos remotos
     * @param repeats cantidad total de emisiones
     * @param delayMs pausa entre repeticiones en milisegundos
     */
    void broadcastBurst(Map<String, Object> data, List<InetSocketAddress> addrs, int repeats, int delayMs);

    /**
     * Intenta recibir un mensaje sin bloqueo largo.
     *
     * @return mensaje y remitente si había datos disponibles
     */
    Optional<Map.Entry<Map<String, Object>, InetSocketAddress>> receive();

    /**
     * Cierra el transporte y libera el recurso subyacente.
     */
    void close();

    /**
     * Indica si el peer está listo para enviar y recibir datagramas.
     *
     * @return {@code true} si el transporte está enlazado y abierto
     */
    boolean isBound();
}
