package com.dino.application.services;

import com.dino.domain.entities.Player;
import com.dino.domain.events.EventNames;
import com.dino.infrastructure.serialization.MessageSerializer;
import com.dino.infrastructure.serialization.MessageType;
import com.dino.infrastructure.serialization.ProtocolMessageValidator;
import com.dino.testsupport.FakeNetworkPeer;
import com.dino.testsupport.RecordingEventPublisher;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias para {@link LobbySessionCoordinator}.
 *
 * <p>Cubre validación de mensajes de lobby, prevención de duplicados y la
 * transición controlada hacia gameplay cuando llega la señal correcta.</p>
 */
class LobbySessionCoordinatorTest {
    @Test
    void hostIgnoresMalformedJoinMessages() {
        RecordingEventPublisher events = new RecordingEventPublisher();
        SessionService session = new SessionService(events);
        SessionLifecycleService lifecycle = new SessionLifecycleService(session);
        FakeNetworkPeer peer = new FakeNetworkPeer();
        MessageSerializer serializer = new MessageSerializer();

        lifecycle.configureAsHost("host-1", "Host", "127.0.0.1", 7000, 2);
        session.addPlayer(new Player("host-1", "Host", "red"));

        LobbySessionCoordinator coordinator = new LobbySessionCoordinator(
            session,
            lifecycle,
            events,
            peer,
            serializer,
            new ProtocolMessageValidator(),
            () -> null
        );

        peer.queueIncoming(
            Map.of("type", MessageType.JOIN.wireValue(), "playerId", "remote-1"),
            new InetSocketAddress("127.0.0.1", 7001)
        );

        LobbySignal signal = coordinator.pollNetworkTick();

        assertEquals(LobbySignal.NONE, signal);
        assertEquals(1, session.getPlayersSnapshot().size());
        assertEquals(0, events.count(EventNames.PLAYER_JOINED));
    }

    @Test
    void clientTransitionsToGameplayOnlyOnceOnStartGame() {
        RecordingEventPublisher events = new RecordingEventPublisher();
        SessionService session = new SessionService(events);
        SessionLifecycleService lifecycle = new SessionLifecycleService(session);
        FakeNetworkPeer peer = new FakeNetworkPeer();
        MessageSerializer serializer = new MessageSerializer();

        lifecycle.configureAsClient("client-1", "Client", "127.0.0.1", 7001, "127.0.0.1", 7000);
        session.addPlayer(new Player("client-1", "Client", "blue"));

        LobbySessionCoordinator coordinator = new LobbySessionCoordinator(
            session,
            lifecycle,
            events,
            peer,
            serializer,
            new ProtocolMessageValidator(),
            () -> null
        );

        peer.queueIncoming(
            serializer.build(MessageType.START_GAME, "seq", 1L, "players", java.util.List.of()),
            new InetSocketAddress("127.0.0.1", 7000)
        );

        LobbySignal firstSignal = coordinator.pollNetworkTick();

        assertEquals(LobbySignal.START_GAME, firstSignal);
        assertEquals(SessionPhase.PLAYING, session.getPhase());
        assertEquals(1, events.count(EventNames.GAME_STARTED));

        peer.queueIncoming(
            serializer.build(MessageType.START_GAME, "seq", 2L, "players", java.util.List.of()),
            new InetSocketAddress("127.0.0.1", 7000)
        );

        assertEquals(LobbySignal.NONE, coordinator.pollNetworkTick());
        assertEquals(1, events.count(EventNames.GAME_STARTED));
    }

    @Test
    void clientSendsExplicitReadyStateAndUpdatesLocalSnapshot() throws Exception {
        RecordingEventPublisher events = new RecordingEventPublisher();
        SessionService session = new SessionService(events);
        SessionLifecycleService lifecycle = new SessionLifecycleService(session);
        FakeNetworkPeer peer = new FakeNetworkPeer();
        MessageSerializer serializer = new MessageSerializer();

        lifecycle.configureAsClient("client-1", "Client", "127.0.0.1", 7001, "127.0.0.1", 7000);
        Player localPlayer = new Player("client-1", "Client", "blue");
        localPlayer.setReady(true);
        session.addPlayer(localPlayer);

        LobbySessionCoordinator coordinator = new LobbySessionCoordinator(
            session,
            lifecycle,
            events,
            peer,
            serializer,
            new ProtocolMessageValidator(),
            () -> null
        );

        coordinator.setLocalReady(false);

        assertFalse(session.getPlayersSnapshot().getFirst().isReady());
        assertEquals(1, peer.getSentMessages().size());
        assertEquals(false, peer.getSentMessages().getFirst().payload().get("ready"));
        assertEquals(1, events.count(EventNames.PLAYER_READY));
    }

    @Test
    void hostAppliesExplicitReadyStateFromClientMessage() {
        RecordingEventPublisher events = new RecordingEventPublisher();
        SessionService session = new SessionService(events);
        SessionLifecycleService lifecycle = new SessionLifecycleService(session);
        FakeNetworkPeer peer = new FakeNetworkPeer();
        MessageSerializer serializer = new MessageSerializer();

        lifecycle.configureAsHost("host-1", "Host", "127.0.0.1", 7000, 2);
        session.addPlayer(new Player("host-1", "Host", "red"));
        Player remotePlayer = new Player("remote-1", "Remote", "blue");
        remotePlayer.setReady(true);
        session.addPlayer(remotePlayer);

        LobbySessionCoordinator coordinator = new LobbySessionCoordinator(
            session,
            lifecycle,
            events,
            peer,
            serializer,
            new ProtocolMessageValidator(),
            () -> null
        );

        peer.queueIncoming(
            serializer.build(MessageType.READY, "playerId", "remote-1", "ready", false),
            new InetSocketAddress("127.0.0.1", 7001)
        );

        assertEquals(LobbySignal.NONE, coordinator.pollNetworkTick());
        assertTrue(session.getPlayersSnapshot().stream()
            .filter(player -> "remote-1".equals(player.getId()))
            .findFirst()
            .isPresent());
        assertFalse(session.getPlayersSnapshot().stream()
            .filter(player -> "remote-1".equals(player.getId()))
            .findFirst()
            .orElseThrow()
            .isReady());
        assertEquals(1, events.count(EventNames.PLAYER_READY));
    }
}
