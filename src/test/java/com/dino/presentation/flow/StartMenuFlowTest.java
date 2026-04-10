package com.dino.presentation.flow;

import com.dino.application.services.SessionLifecycleService;
import com.dino.application.services.SessionService;
import com.dino.application.usecases.CreateSessionUseCase;
import com.dino.application.usecases.JoinSessionUseCase;
import com.dino.testsupport.FakeNetworkPeer;
import com.dino.testsupport.RecordingEventPublisher;
import com.dino.testsupport.RecordingSceneNavigation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartMenuFlowTest {
    @Test
    void createLobbyInitializesHostAndNavigatesToLobby() throws Exception {
        RecordingEventPublisher events = new RecordingEventPublisher();
        SessionService session = new SessionService(events);
        SessionLifecycleService lifecycle = new SessionLifecycleService(session);
        FakeNetworkPeer peer = new FakeNetworkPeer();
        RecordingSceneNavigation navigation = new RecordingSceneNavigation();
        StartMenuFlow flow = new StartMenuFlow(
            () -> peer,
            () -> new CreateSessionUseCase(session, lifecycle, peer, events),
            () -> new JoinSessionUseCase(session, lifecycle, peer, new com.dino.infrastructure.serialization.MessageSerializer(), events),
            peer::close,
            navigation
        );

        flow.createLobby("Host", "127.0.0.1", 7000, 2);

        assertEquals("lobby", navigation.getLastScreen());
        assertTrue(peer.isBound());
        assertTrue(session.isHost());
        assertNotNull(session.getLocalPlayerId());
        assertEquals(1, session.getPlayersSnapshot().size());
    }
}
