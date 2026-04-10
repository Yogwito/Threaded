package com.dino.application.services;

import com.dino.config.GameConfig;
import com.dino.infrastructure.network.NetworkPeer;
import com.dino.infrastructure.serialization.MessageType;
import com.dino.infrastructure.serialization.MessageSerializer;
import com.dino.infrastructure.serialization.ProtocolMessageValidator;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Coordinador del flujo de partida en vivo.
 *
 * <p>Gestiona input local, polling de red, snapshots del host y mensajes
 * críticos como `GAME_OVER`. Su responsabilidad es orquestar el protocolo de
 * gameplay sin mezclar render JavaFX ni actualización de HUD.</p>
 */
public final class GameplaySessionCoordinator {
    private static final Logger LOGGER = Logger.getLogger(GameplaySessionCoordinator.class.getName());
    private static final double SNAPSHOT_INTERVAL = 1.0 / GameConfig.SNAPSHOT_RATE_HZ;

    private final SessionService sessionService;
    private final SessionLifecycleService lifecycleService;
    private final NetworkPeer networkPeer;
    private final MessageSerializer serializer;
    private final ProtocolMessageValidator validator;
    private final HostMatchService hostMatchService;

    private InetAddress hostAddress;
    private double snapshotTimer = 0;

    /**
     * Crea un coordinador ligado al runtime actual.
     *
     * @param sessionService estado compartido de la sesión
     * @param networkPeer transporte UDP activo
     * @param serializer constructor de mensajes del protocolo
     * @param validator validador estructural del protocolo UDP
     * @param hostMatchService simulación autoritativa del host, o {@code null} en clientes
     */
    public GameplaySessionCoordinator(SessionService sessionService,
                                      SessionLifecycleService lifecycleService,
                                      NetworkPeer networkPeer,
                                      MessageSerializer serializer,
                                      ProtocolMessageValidator validator,
                                      HostMatchService hostMatchService) {
        this.sessionService = sessionService;
        this.lifecycleService = lifecycleService;
        this.networkPeer = networkPeer;
        this.serializer = serializer;
        this.validator = validator;
        this.hostMatchService = hostMatchService;
        resolveHostAddress();
    }

    /**
     * Avanza la lógica de red/snapshots asociada a un frame del juego.
     *
     * @param dt delta de tiempo en segundos
     */
    public void advanceFrame(double dt) {
        if (!lifecycleService.isInPhase(SessionPhase.PLAYING)) {
            return;
        }

        if (sessionService.isHost() && hostMatchService != null) {
                hostMatchService.tick(dt);
                snapshotTimer += dt;
                if (snapshotTimer >= SNAPSHOT_INTERVAL) {
                    snapshotTimer = snapshotTimer % SNAPSHOT_INTERVAL;
                    broadcastLiveSnapshot();
                }
        }
    }

    /**
     * Vacía el socket no bloqueante y procesa todos los mensajes disponibles.
     *
     * @return señal agregada producida por el lote de mensajes
     */
    public GameplaySignal pollIncomingMessages() {
        if (!lifecycleService.isInPhase(SessionPhase.PLAYING)) {
            return GameplaySignal.NONE;
        }

        GameplaySignal signal = GameplaySignal.NONE;
        while (true) {
            var received = networkPeer.receive();
            if (received.isEmpty()) return signal;
            GameplaySignal messageSignal = handleIncomingMessage(received.get().getKey(), received.get().getValue());
            if (messageSignal == GameplaySignal.GAME_OVER) {
                signal = GameplaySignal.GAME_OVER;
            }
        }
    }

    /**
     * Envía o aplica localmente un nuevo objetivo de movimiento.
     *
     * @param playerId jugador local
     * @param worldX objetivo X en mundo
     * @param worldY objetivo Y en mundo
     */
    public void sendAim(String playerId, double worldX, double worldY) {
        if (playerId == null || !lifecycleService.isInPhase(SessionPhase.PLAYING)) return;

        if (sessionService.isHost()) {
            if (hostMatchService != null) {
                hostMatchService.handleMoveTarget(playerId, worldX, worldY);
            }
            return;
        }

        try {
            Map<String, Object> msg = serializer.build(
                MessageType.MOVE_TARGET,
                "playerId", playerId,
                "targetX", worldX,
                "targetY", worldY
            );
            networkPeer.send(msg, hostAddress, sessionService.getHostPort());
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "No se pudo enviar MOVE_TARGET del jugador {0}", playerId);
            LOGGER.log(Level.FINER, "Detalle del error enviando MOVE_TARGET", e);
        }
    }

    /**
     * Envía o aplica localmente un salto discreto.
     *
     * @param playerId jugador local
     */
    public void sendJump(String playerId) {
        if (playerId == null || !lifecycleService.isInPhase(SessionPhase.PLAYING)) return;

        if (sessionService.isHost()) {
            if (hostMatchService != null) {
                hostMatchService.handleJump(playerId);
            }
            return;
        }

        try {
            Map<String, Object> msg = serializer.build(
                MessageType.JUMP,
                "playerId", playerId
            );
            networkPeer.send(msg, hostAddress, sessionService.getHostPort());
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "No se pudo enviar JUMP del jugador {0}", playerId);
            LOGGER.log(Level.FINER, "Detalle del error enviando JUMP", e);
        }
    }

    /**
     * Difunde el cierre de partida a todos los clientes.
     */
    public void broadcastGameOver() {
        if (!sessionService.isHost()) {
            return;
        }
        if (!lifecycleService.isInPhase(SessionPhase.PLAYING)
            && !lifecycleService.isInPhase(SessionPhase.GAME_OVER)) {
            return;
        }
        Map<String, Object> payload = sessionService.buildAuthoritativeSnapshot();
        payload.put("type", MessageType.GAME_OVER.wireValue());
        networkPeer.broadcastBurst(payload, sessionService.getRemotePeerAddresses(),
            GameConfig.CRITICAL_BROADCAST_REPEATS, GameConfig.CRITICAL_BROADCAST_DELAY_MS);
    }

    private GameplaySignal handleIncomingMessage(Map<String, Object> msg, InetSocketAddress sender) {
        var resolvedType = validator.resolveType(msg);
        if (resolvedType.isEmpty()) return GameplaySignal.NONE;
        MessageType type = resolvedType.get();
        if (!validator.isAcceptedInGameplay(type, sessionService.isHost())) return GameplaySignal.NONE;
        if (!validator.hasRequiredFields(type, msg)) return GameplaySignal.NONE;

        if (sessionService.isHost()) {
            handleHostNetworkMessage(type, msg, sender);
            return GameplaySignal.NONE;
        }

        if (type == MessageType.SNAPSHOT) {
            sessionService.applyAuthoritativeSnapshot(msg);
            return GameplaySignal.NONE;
        }
        if (type == MessageType.GAME_OVER) {
            lifecycleService.enterGameOver();
            sessionService.applyAuthoritativeSnapshot(msg);
            return GameplaySignal.GAME_OVER;
        }
        return GameplaySignal.NONE;
    }

    private void handleHostNetworkMessage(MessageType type, Map<String, Object> msg, InetSocketAddress sender) {
        if (hostMatchService == null) return;

        if (type == MessageType.MOVE_TARGET) {
            String playerId = (String) msg.get("playerId");
            Number targetX = (Number) msg.get("targetX");
            Number targetY = (Number) msg.get("targetY");
            if (playerId != null && targetX != null && targetY != null) {
                sessionService.registerPeerAddress(playerId, sender);
                hostMatchService.handleMoveTarget(playerId, targetX.doubleValue(), targetY.doubleValue());
            }
            return;
        }

        if (type == MessageType.JUMP) {
            String playerId = (String) msg.get("playerId");
            if (playerId != null) {
                sessionService.registerPeerAddress(playerId, sender);
                hostMatchService.handleJump(playerId);
            }
            return;
        }

        if (type == MessageType.DISCONNECT) {
            String playerId = (String) msg.get("playerId");
            sessionService.markPlayerConnected(playerId, false);
            sessionService.removePeerAddress(playerId);
            broadcastLiveSnapshot();
        }
    }

    private void resolveHostAddress() {
        try {
            hostAddress = InetAddress.getByName(sessionService.getHostIp());
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "No se pudo resolver la IP del host {0}", sessionService.getHostIp());
            LOGGER.log(Level.FINER, "Detalle del fallo al resolver host", e);
        }
    }

    private void broadcastLiveSnapshot() {
        Map<String, Object> snapshot = sessionService.buildAuthoritativeSnapshot();
        snapshot.put("type", MessageType.SNAPSHOT.wireValue());
        sessionService.applyAuthoritativeSnapshot(snapshot);
        networkPeer.broadcast(snapshot, sessionService.getRemotePeerAddresses());
    }
}
