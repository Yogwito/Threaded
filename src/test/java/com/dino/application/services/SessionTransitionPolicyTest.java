package com.dino.application.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTransitionPolicyTest {
    private final SessionTransitionPolicy policy = new SessionTransitionPolicy();

    @Test
    void acceptsNominalLifecycleTransitions() {
        assertTrue(policy.canTransition(SessionPhase.IDLE, SessionPhase.LOBBY));
        assertTrue(policy.canTransition(SessionPhase.LOBBY, SessionPhase.PLAYING));
        assertTrue(policy.canTransition(SessionPhase.PLAYING, SessionPhase.GAME_OVER));
        assertTrue(policy.canTransition(SessionPhase.GAME_OVER, SessionPhase.IDLE));
    }

    @Test
    void rejectsInvalidShortcuts() {
        assertFalse(policy.canTransition(SessionPhase.IDLE, SessionPhase.PLAYING));
        assertFalse(policy.canTransition(SessionPhase.LOBBY, SessionPhase.GAME_OVER));
        assertFalse(policy.canTransition(SessionPhase.PLAYING, SessionPhase.LOBBY));
    }

    @Test
    void exposesAllowedTargetsPerPhase() {
        assertEquals(1, policy.allowedTargetsFrom(SessionPhase.IDLE).size());
        assertTrue(policy.allowedTargetsFrom(SessionPhase.LOBBY).contains(SessionPhase.PLAYING));
        assertTrue(policy.allowedTargetsFrom(SessionPhase.LOBBY).contains(SessionPhase.IDLE));
    }

    @Test
    void throwsDescriptiveErrorForInvalidTransition() {
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> policy.requireTransition(SessionPhase.IDLE, SessionPhase.PLAYING)
        );

        assertTrue(error.getMessage().contains("IDLE"));
        assertTrue(error.getMessage().contains("PLAYING"));
    }
}
