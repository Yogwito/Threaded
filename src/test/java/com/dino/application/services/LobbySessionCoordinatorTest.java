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
}
