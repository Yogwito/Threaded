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

    @Override
    public void bind(String ip, int port) throws IOException {
        bound = true;
    }

    @Override
    public void send(Map<String, Object> data, InetAddress addr, int port) {
        sentMessages.add(new SentMessage(data, addr, port));
    }

    @Override
    public void broadcast(Map<String, Object> data, List<InetSocketAddress> addrs) {
        broadcastMessages.add(new BroadcastMessage(data, List.copyOf(addrs)));
    }

    @Override
    public void broadcastBurst(Map<String, Object> data, List<InetSocketAddress> addrs, int repeats, int delayMs) {
        burstMessages.add(new BurstBroadcastMessage(data, List.copyOf(addrs), repeats, delayMs));
    }

    @Override
    public Optional<Map.Entry<Map<String, Object>, InetSocketAddress>> receive() {
        return Optional.ofNullable(incomingMessages.poll());
    }

    @Override
    public void close() {
        bound = false;
    }

    @Override
    public boolean isBound() {
        return bound;
    }

    public void queueIncoming(Map<String, Object> message, InetSocketAddress sender) {
        incomingMessages.add(new AbstractMap.SimpleEntry<>(message, sender));
    }

    public List<SentMessage> getSentMessages() {
        return List.copyOf(sentMessages);
    }

    public List<BroadcastMessage> getBroadcastMessages() {
        return List.copyOf(broadcastMessages);
    }

    public List<BurstBroadcastMessage> getBurstMessages() {
        return List.copyOf(burstMessages);
    }

    /**
     * Mensaje puntual enviado a un destino específico.
     */
    public record SentMessage(Map<String, Object> payload, InetAddress address, int port) {
    }

    /**
     * Broadcast simple emitido a una colección de peers.
     */
    public record BroadcastMessage(Map<String, Object> payload, List<InetSocketAddress> addresses) {
    }

    /**
     * Broadcast crítico emitido con repeticiones.
     */
    public record BurstBroadcastMessage(Map<String, Object> payload,
                                        List<InetSocketAddress> addresses,
                                        int repeats,
                                        int delayMs) {
    }
}
