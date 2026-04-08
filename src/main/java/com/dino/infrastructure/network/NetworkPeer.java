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
    void bind(String ip, int port) throws IOException;
    void send(Map<String, Object> data, InetAddress addr, int port);
    void broadcast(Map<String, Object> data, List<InetSocketAddress> addrs);
    void broadcastBurst(Map<String, Object> data, List<InetSocketAddress> addrs, int repeats, int delayMs);
    Optional<Map.Entry<Map<String, Object>, InetSocketAddress>> receive();
    void close();
    boolean isBound();
}
