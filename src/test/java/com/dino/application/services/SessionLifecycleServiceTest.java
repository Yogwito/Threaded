package com.dino.application.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionLifecycleServiceTest {
    @Test
    void hostConfigurationClearsPreviousStateAndEntersLobby() {
        SessionService session = new SessionService(new EventBus());
        SessionLifecycleService lifecycle = new SessionLifecycleService(session);

        session.addPlayer(new com.dino.domain.entities.Player("stale", "Stale", "red"));
        lifecycle.configureAsHost("host-1", "Host", "127.0.0.1", 7000, 2);

        assertEquals(SessionPhase.LOBBY, lifecycle.currentPhase());
        assertTrue(session.isHost());
        assertEquals("host-1", session.getLocalPlayerId());
        assertTrue(session.getPlayersSnapshot().isEmpty());
    }

    @Test
    void enterGameOverMarksMatchAsNotRunning() {
        SessionService session = new SessionService(new EventBus());
        SessionLifecycleService lifecycle = new SessionLifecycleService(session);

        lifecycle.configureAsClient("client-1", "Client", "127.0.0.1", 7001, "127.0.0.1", 7000);
        lifecycle.enterGameplay();
        assertTrue(session.isGameRunning());

        lifecycle.enterGameOver();

        assertEquals(SessionPhase.GAME_OVER, lifecycle.currentPhase());
        assertFalse(session.isGameRunning());
    }
}
