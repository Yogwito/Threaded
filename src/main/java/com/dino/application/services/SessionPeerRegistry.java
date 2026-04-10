package com.dino.application.services;

import com.dino.domain.entities.Player;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Registro especializado de peers remotos observados durante una sesión.
 *
 * <p>Extrae de {@link SessionService} el detalle de direcciones UDP y marcas de
 * actividad reciente. El estado principal de la partida conserva solo la
 * información del dominio, mientras que este componente encapsula las
 * preocupaciones auxiliares de conectividad.</p>
 */
public final class SessionPeerRegistry {
    private final Map<String, InetSocketAddress> peerAddresses = new LinkedHashMap<>();
    private final Map<String, Long> peerLastSeenMillis = new LinkedHashMap<>();

    /**
     * Elimina toda la información de peers almacenada.
     */
    public void clear() {
        peerAddresses.clear();
        peerLastSeenMillis.clear();
    }

    /**
     * Registra o actualiza la dirección remota conocida de un peer.
     *
     * @param playerId identificador del peer
     * @param address dirección UDP observada
     */
    public void register(String playerId, InetSocketAddress address) {
        if (playerId == null || address == null) return;
        peerAddresses.put(playerId, address);
        peerLastSeenMillis.put(playerId, System.currentTimeMillis());
    }

    /**
     * Marca actividad reciente para un peer ya conocido.
     *
     * @param playerId identificador del peer
     */
    public void markSeen(String playerId) {
        if (playerId != null) {
            peerLastSeenMillis.put(playerId, System.currentTimeMillis());
        }
    }

    /**
     * Elimina por completo la información de red asociada a un peer.
     *
     * @param playerId identificador del peer
     */
    public void remove(String playerId) {
        if (playerId == null) return;
        peerAddresses.remove(playerId);
        peerLastSeenMillis.remove(playerId);
    }

    /**
     * Lista direcciones remotas, excluyendo el peer local.
     *
     * @param localPlayerId identificador del jugador local
     * @return destinos remotos actualmente conocidos
     */
    public List<InetSocketAddress> getRemoteAddresses(String localPlayerId) {
        List<InetSocketAddress> remotes = new ArrayList<>();
        for (Map.Entry<String, InetSocketAddress> entry : peerAddresses.entrySet()) {
            if (!Objects.equals(entry.getKey(), localPlayerId)) {
                remotes.add(entry.getValue());
            }
        }
        return remotes;
    }

    /**
     * Calcula cuánto tiempo pasó desde la última actividad de un peer.
     *
     * @param playerId identificador del peer
     * @return edad en milisegundos o {@code null} si no hay registro
     */
    public Long getAgeMillis(String playerId) {
        Long lastSeen = peerLastSeenMillis.get(playerId);
        if (lastSeen == null) return null;
        return Math.max(0L, System.currentTimeMillis() - lastSeen);
    }

    /**
     * Marca como desconectados a los peers que excedieron un timeout.
     *
     * @param players jugadores de la sesión
     * @param localPlayerId identificador local
     * @param isHost indica si esta instancia actúa como host
     * @param timeoutMillis tiempo máximo sin actividad
     * @return ids de peers expirados
     */
    public List<String> expireInactivePlayers(Map<String, Player> players,
                                              String localPlayerId,
                                              boolean isHost,
                                              long timeoutMillis) {
        long now = System.currentTimeMillis();
        List<String> expired = new ArrayList<>();
        for (Player player : players.values()) {
            if (!player.isConnected()) continue;
            if (Objects.equals(player.getId(), localPlayerId) && isHost) continue;
            Long lastSeen = peerLastSeenMillis.get(player.getId());
            if (lastSeen == null || now - lastSeen <= timeoutMillis) continue;

            player.setConnected(false);
            peerAddresses.remove(player.getId());
            expired.add(player.getId());
        }
        expired.forEach(peerLastSeenMillis::remove);
        return expired;
    }
}
