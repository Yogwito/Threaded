package com.dino.presentation.flow;

import com.dino.application.services.EventBus;
import com.dino.application.services.SessionLifecycleService;
import com.dino.application.services.SessionService;
import com.dino.domain.entities.Player;
import com.dino.domain.events.EventNames;
import com.dino.presentation.components.EventLogObserver;
import com.dino.presentation.components.ScoreBoardObserver;
import com.dino.presentation.render.GameRenderStateFactory;
import com.dino.presentation.viewmodel.GameHudViewModelFactory;
import com.dino.testsupport.RecordingSceneNavigation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GameScreenFlowTest {
    @Test
    void bindSceneEventsEmitsFeedbackOnlyForLocalPlayer() {
        EventBus events = new EventBus();
        SessionService session = new SessionService(events);
        SessionLifecycleService lifecycle = new SessionLifecycleService(session);
        lifecycle.configureAsClient("local-1", "Client", "127.0.0.1", 7001, "127.0.0.1", 7000);
        session.addPlayer(new Player("local-1", "Client", "blue"));

        GameScreenFlow flow = new GameScreenFlow(
            session,
            events,
            new ScoreBoardObserver(events),
            new EventLogObserver(events, session),
            () -> null,
            new RecordingSceneNavigation(),
            () -> { },
            new GameRenderStateFactory(),
            new GameHudViewModelFactory()
        );

        AtomicInteger refreshes = new AtomicInteger();
        AtomicInteger gameOvers = new AtomicInteger();
        AtomicReference<GameplayFeedback> feedback = new AtomicReference<>();
        var subscription = flow.bindSceneEvents(refreshes::incrementAndGet, gameOvers::incrementAndGet, feedback::set);

        events.publish(EventNames.COIN_COLLECTED, Map.of("playerId", "other"));
        assertNull(feedback.get());

        events.publish(EventNames.COIN_COLLECTED, Map.of("playerId", "local-1"));
        events.publish(EventNames.SNAPSHOT_RECEIVED, Map.of("seq", 1L));

        subscription.unsubscribe();

        assertEquals("Moneda recogida", feedback.get().text());
        assertEquals(1, refreshes.get());
        assertEquals(0, gameOvers.get());
    }

    @Test
    void showGameOverNavigatesToFinalScreen() throws Exception {
        EventBus events = new EventBus();
        SessionService session = new SessionService(events);
        RecordingSceneNavigation navigation = new RecordingSceneNavigation();
        GameScreenFlow flow = new GameScreenFlow(
            session,
            events,
            new ScoreBoardObserver(events),
            new EventLogObserver(events, session),
            () -> null,
            navigation,
            () -> { },
            new GameRenderStateFactory(),
            new GameHudViewModelFactory()
        );

        flow.showGameOver();

        assertEquals("gameOver", navigation.getLastScreen());
    }
}
