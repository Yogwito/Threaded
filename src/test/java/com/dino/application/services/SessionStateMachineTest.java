package com.dino.application.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStateMachineTest {
    @Test
    void followsNominalSessionLifecycle() {
        SessionStateMachine machine = new SessionStateMachine();

        assertEquals(SessionPhase.IDLE, machine.currentPhase());

        machine.enterLobby();
        machine.enterGameplay();
        machine.enterGameOver();

        assertEquals(SessionPhase.GAME_OVER, machine.currentPhase());

        machine.reset();

        assertEquals(SessionPhase.IDLE, machine.currentPhase());
    }

    @Test
    void rejectsInvalidDirectTransition() {
        SessionStateMachine machine = new SessionStateMachine();

        IllegalStateException error = assertThrows(IllegalStateException.class, machine::enterGameplay);

        assertTrue(error.getMessage().contains("IDLE"));
        assertTrue(error.getMessage().contains("PLAYING"));
    }
}
