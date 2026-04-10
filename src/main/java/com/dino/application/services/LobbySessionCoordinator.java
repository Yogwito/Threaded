package com.dino.application.services;

import com.dino.config.GameConfig;
import com.dino.domain.entities.Player;
import com.dino.domain.events.EventNames;
import com.dino.infrastructure.network.NetworkPeer;
import com.dino.infrastructure.serialization.MessageType;
import com.dino.infrastructure.serialization.MessageSerializer;
import com.dino.infrastructure.serialization.ProtocolMessageValidator;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Coordinador del flujo de lobby sobre UDP.
 *
 * <p>Encapsula el protocolo previo a la partida: `JOIN`, `READY`,
 * `LOBBY_SNAPSHOT`, `START_GAME` y desconexiones. Los controladores de JavaFX
 * delegan en esta clase la coordinación de red y conservan solo la lógica de
 * vista y navegación.</p>
 */
public final class LobbySessionCoordinator {
    private final SessionService sessionService;
    private final SessionLifecycleService lifecycleService;
    private final EventPublisher eventPublisher;
    private final NetworkPeer networkPeer;
    private final MessageSerializer serializer;
    private final ProtocolMessageValidator validator;
    private final Supplier<HostMatchService> hostMatchFactory;

    private long lastLobbyBroadcastAt = 0;
    private boolean startTransitionTriggered = false;

    /**
     * Crea un coordinador ligado al runtime actual.
     *
     * @param sessionService estado compartido de lobby y partida
     * @param eventPublisher publicador de eventos internos
     * @param networkPeer transporte UDP ya enlazado
     * @param serializer constructor de mensajes del protocolo
     * @param validator validador estructural del protocolo UDP
     * @param hostMatchFactory fábrica perezosa de la simulación autoritativa
     */
    public LobbySessionCoordinator(SessionService sessionService,
                                   SessionLifecycleService lifecycleService,
                                   EventPublisher eventPublisher,
                                   NetworkPeer networkPeer,
                                   MessageSerializer serializer,
                                   ProtocolMessageValidator validator,
                                   Supplier<HostMatchService> hostMatchFactory) {
        this.sessionService = sessionService;
        this.lifecycleService = lifecycleService;
        this.eventPublisher = eventPublisher;
        this.networkPeer = networkPeer;
        this.serializer = serializer;
        this.validator = validator;
        this.hostMatchFactory = hostMatchFactory;
    }

    /**
     * Marca al jugador local como listo y lo propaga por red según su rol.
     *
     * @throws Exception si el cliente no puede enviar el mensaje `READY`
     */
    public void markLocalReady() throws Exception {
        if (!lifecycleService.isInPhase(SessionPhase.LOBBY)) {
            return;
        }

        if (sessionService.isHost()) {
            sessionService.markPlayerReady(sessionService.getLocalPlayerId(), true);
            eventPublisher.publish(EventNames.PLAYER_READY, Map.of("playerId", sessionService.getLocalPlayerId()));
            broadcastLobbySnapshot();
            return;
        }

        Map<String, Object> msg = serializer.build(
            MessageType.READY,
            "playerId", sessionService.getLocalPlayerId()
        );
        networkPeer.send(msg, InetAddress.getByName(sessionService.getHostIp()), sessionService.getHostPort());
    }

    /**
     * Inicializa la simulación del host y difunde el arranque de partida.
     */
    public void startGame() {
        if (!lifecycleService.isInPhase(SessionPhase.LOBBY)) {
            return;
        }

        HostMatchService hostMatchService = hostMatchFactory.get();
        hostMatchService.initWorld();
        lifecycleService.enterGameplay();

        Map<String, Object> startMessage = buildTypedSnapshot(MessageType.START_GAME);
        networkPeer.broadcastBurst(startMessage, sessionService.getRemotePeerAddresses(),
            GameConfig.CRITICAL_BROADCAST_REPEATS, GameConfig.CRITICAL_BROADCAST_DELAY_MS);
        eventPublisher.publish(EventNames.GAME_STARTED, Map.of());
    }

    /**
     * Ejecuta una iteración de polling del lobby.
     *
     * <p>Cuando la instancia local es host, aprovecha el tick para refrescar el
     * snapshot de sala con una cadencia moderada. Después procesa, como mucho,
     * un mensaje disponible del socket no bloqueante y lo traduce a una señal
     * consumible por la UI.</p>
     *
     * @return señal producida por el mensaje procesado, si hubo alguno
     */
    public LobbySignal pollNetworkTick() {
        if (!lifecycleService.isInPhase(SessionPhase.LOBBY)) {
            return LobbySignal.NONE;
        }

        if (sessionService.isHost()) {
            broadcastLobbySnapshotIfDue();
        }

        var received = networkPeer.receive();
        if (received.isEmpty()) {
            return LobbySignal.NONE;
        }
        return handleIncomingMessage(received.get().getKey(), received.get().getValue());
    }

    /**
     * Atiende un mensaje entrante del protocolo de lobby ya recibido por red.
     *
     * @param msg mensaje ya deserializado
     * @param sender peer que envió el mensaje
     * @return señal de alto nivel para la UI
     */
    public LobbySignal handleIncomingMessage(Map<String, Object> msg, InetSocketAddress sender) {
        var resolvedType = validator.resolveType(msg);
        if (resolvedType.isEmpty()) return LobbySignal.NONE;
        MessageType type = resolvedType.get();
        if (!validator.isAcceptedInLobby(type, sessionService.isHost())) return LobbySignal.NONE;
        if (!validator.hasRequiredFields(type, msg)) return LobbySignal.NONE;

        if (sessionService.isHost()) {
            handleHostMessage(type, msg, sender);
            return LobbySignal.NONE;
        }

        if (type == MessageType.START_GAME) {
            if (startTransitionTriggered) return LobbySignal.NONE;
            startTransitionTriggered = true;
            lifecycleService.enterGameplay();
            sessionService.applyAuthoritativeSnapshot(msg);
            eventPublisher.publish(EventNames.GAME_STARTED, Map.of());
            return LobbySignal.START_GAME;
        }
        if (type == MessageType.LOBBY_SNAPSHOT) {
            sessionService.applyAuthoritativeSnapshot(msg);
        }
        return LobbySignal.NONE;
    }

    /**
     * Emite un snapshot de lobby solo si venció el intervalo de refresco.
     */
    public void broadcastLobbySnapshotIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastLobbyBroadcastAt < 250) return;
        broadcastLobbySnapshot();
        lastLobbyBroadcastAt = now;
    }

    /**
     * Difunde el estado actual del lobby a todos los peers remotos.
     */
    public void broadcastLobbySnapshot() {
        if (sessionService.getRemotePeerAddresses().isEmpty()) return;
        Map<String, Object> snapshot = buildTypedSnapshot(MessageType.LOBBY_SNAPSHOT);
        networkPeer.broadcast(snapshot, sessionService.getRemotePeerAddresses());
    }

    private void handleHostMessage(MessageType type, Map<String, Object> msg, InetSocketAddress sender) {
        if (type == MessageType.JOIN) {
            String playerId = (String) msg.get("playerId");
            String name = (String) msg.getOrDefault("name", "Jugador");
            if (playerId == null || playerId.isBlank()) return;

            Player player = sessionService.findPlayer(playerId);
            if (player == null) {
                player = new Player(playerId, name, nextPlayerColor());
                sessionService.addPlayer(player);
            } else {
                player.setName(name);
                player.setConnected(true);
            }
            sessionService.registerPeerAddress(playerId, sender);
            eventPublisher.publish(EventNames.PLAYER_JOINED, Map.of("playerId", playerId, "name", name));
            broadcastLobbySnapshot();
            return;
        }

        if (type == MessageType.READY) {
            String playerId = (String) msg.get("playerId");
            sessionService.markPlayerReady(playerId, true);
            eventPublisher.publish(EventNames.PLAYER_READY, msg);
            broadcastLobbySnapshot();
            return;
        }

        if (type == MessageType.DISCONNECT) {
            sessionService.removePlayer((String) msg.get("playerId"));
            broadcastLobbySnapshot();
        }
    }

    private String nextPlayerColor() {
        String[] colors = {"red", "blue", "green", "yellow"};
        int index = Math.max(0, sessionService.getPlayerCount()) % colors.length;
        return colors[index];
    }

    private Map<String, Object> buildTypedSnapshot(MessageType type) {
        Map<String, Object> snapshot = sessionService.buildAuthoritativeSnapshot();
        snapshot.put("type", type.wireValue());
        return snapshot;
    }
}
