package com.dino.application.services;

import com.dino.application.levels.LevelCatalog;
import com.dino.infrastructure.serialization.MessageSerializer;
import com.dino.infrastructure.serialization.MessageType;
import com.dino.infrastructure.serialization.ProtocolMessageValidator;
import com.dino.testsupport.FakeNetworkPeer;
import com.dino.testsupport.RecordingEventPublisher;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameplaySessionCoordinatorTest {
    @Test
    void clientTransitionsToGameOverWhenMessageArrives() {
        RecordingEventPublisher events = new RecordingEventPublisher();
        SessionService session = new SessionService(events);
        SessionLifecycleService lifecycle = new SessionLifecycleService(session);
        FakeNetworkPeer peer = new FakeNetworkPeer();
        MessageSerializer serializer = new MessageSerializer();

        lifecycle.configureAsClient("client-1", "Client", "127.0.0.1", 7001, "127.0.0.1", 7000);
        lifecycle.enterGameplay();

        GameplaySessionCoordinator coordinator = new GameplaySessionCoordinator(
            session,
            lifecycle,
            peer,
            serializer,
            new ProtocolMessageValidator(),
            null
        );

        peer.queueIncoming(
            serializer.build(MessageType.GAME_OVER, "seq", 5L, "players", java.util.List.of()),
            new InetSocketAddress("127.0.0.1", 7000)
        );

        GameplaySignal signal = coordinator.pollIncomingMessages();

        assertEquals(GameplaySignal.GAME_OVER, signal);
        assertEquals(SessionPhase.GAME_OVER, session.getPhase());
    }

    @Test
    void hostCanBroadcastGameOverAfterSimulationAlreadyClosed() {
        RecordingEventPublisher events = new RecordingEventPublisher();
        SessionService session = new SessionService(events);
        SessionLifecycleService lifecycle = new SessionLifecycleService(session);
        FakeNetworkPeer peer = new FakeNetworkPeer();
        MessageSerializer serializer = new MessageSerializer();

        lifecycle.configureAsHost("host-1", "Host", "127.0.0.1", 7000, 2);
        session.registerPeerAddress("remote-1", new InetSocketAddress("127.0.0.1", 7002));
        lifecycle.enterGameplay();
        lifecycle.enterGameOver();

        GameplaySessionCoordinator coordinator = new GameplaySessionCoordinator(
            session,
            lifecycle,
            peer,
            serializer,
            new ProtocolMessageValidator(),
            null
        );

        coordinator.broadcastGameOver();

        assertEquals(1, peer.getBurstMessages().size());
        assertEquals(MessageType.GAME_OVER.wireValue(), peer.getBurstMessages().getFirst().payload().get("type"));
        assertEquals(1, peer.getBurstMessages().getFirst().addresses().size());
    }

    @Test
    void hostKeepsSnapshotCadenceAfterASlowFrame() {
        RecordingEventPublisher events = new RecordingEventPublisher();
        SessionService session = new SessionService(events);
        SessionLifecycleService lifecycle = new SessionLifecycleService(session);
        FakeNetworkPeer peer = new FakeNetworkPeer();
        MessageSerializer serializer = new MessageSerializer();

        lifecycle.configureAsHost("host-1", "Host", "127.0.0.1", 7000, 2);
        session.registerPeerAddress("remote-1", new InetSocketAddress("127.0.0.1", 7002));
        lifecycle.enterGameplay();

        HostMatchService hostMatchService = new HostMatchService(
            session,
            lifecycle,
            events,
            new LevelCatalog() {
                @Override
                public com.dino.application.levels.LevelData loadLevel(int levelNumber) {
                    return null;
                }

                @Override
                public int countAvailableLevels() {
                    return 1;
                }
            }
        );

        GameplaySessionCoordinator coordinator = new GameplaySessionCoordinator(
            session,
            lifecycle,
            peer,
            serializer,
            new ProtocolMessageValidator(),
            hostMatchService
        );

        coordinator.advanceFrame(0.05);
        coordinator.advanceFrame(0.02);

        assertEquals(2, peer.getBroadcastMessages().size());
        assertEquals(MessageType.SNAPSHOT.wireValue(), peer.getBroadcastMessages().getFirst().payload().get("type"));
    }
}
