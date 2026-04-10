package com.dino.presentation.flow;

import com.dino.application.services.EventBus;
import com.dino.application.services.LobbySignal;
import com.dino.application.services.SessionLifecycleService;
import com.dino.application.services.SessionService;
import com.dino.domain.entities.Player;
import com.dino.domain.events.EventNames;
import com.dino.presentation.viewmodel.LobbyPlayerListFormatter;
import com.dino.testsupport.RecordingSceneNavigation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyScreenFlowTest {
    @Test
    void bindLobbyUpdatesRefreshesOnRelevantEvents() {
        EventBus events = new EventBus();
        SessionService session = new SessionService(events);
        SessionLifecycleService lifecycle = new SessionLifecycleService(session);
        lifecycle.configureAsHost("host-1", "Host", "127.0.0.1", 7000, 2);
        session.addPlayer(new Player("host-1", "Host", "red"));

        LobbyScreenFlow flow = new LobbyScreenFlow(
            session,
            events,
            () -> null,
            new RecordingSceneNavigation(),
            new LobbyPlayerListFormatter()
        );

        AtomicInteger refreshCount = new AtomicInteger();
        var subscription = flow.bindLobbyUpdates(refreshCount::incrementAndGet);

        events.publish(EventNames.PLAYER_JOINED, Map.of("playerId", "host-1"));
        events.publish(EventNames.PLAYER_READY, Map.of("playerId", "host-1"));
        events.publish(EventNames.SNAPSHOT_RECEIVED, Map.of("seq", 1L));

        subscription.unsubscribe();

        assertEquals(3, refreshCount.get());
        assertEquals(1, flow.playerEntries().size());
        assertTrue(flow.isHost());
    }

    @Test
    void handleStartSignalNavigatesToGameplay() throws Exception {
        EventBus events = new EventBus();
        SessionService session = new SessionService(events);
        RecordingSceneNavigation navigation = new RecordingSceneNavigation();
        LobbyScreenFlow flow = new LobbyScreenFlow(
            session,
            events,
            () -> null,
            navigation,
            new LobbyPlayerListFormatter()
        );

        flow.handleSignal(LobbySignal.START_GAME);

        assertEquals("game", navigation.getLastScreen());
    }
}
