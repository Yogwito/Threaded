package com.dino.infrastructure.serialization;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolMessageValidatorTest {
    private final ProtocolMessageValidator validator = new ProtocolMessageValidator();

    @Test
    void resolvesKnownWireType() {
        assertEquals(MessageType.JOIN, validator.resolveType(Map.of("type", "JOIN")).orElseThrow());
    }

    @Test
    void rejectsJoinWithoutRequiredFields() {
        assertFalse(validator.hasRequiredFields(MessageType.JOIN, Map.of("type", "JOIN", "playerId", "p-1")));
        assertFalse(validator.hasRequiredFields(MessageType.JOIN, Map.of("type", "JOIN", "name", "Alice")));
    }

    @Test
    void validatesMoveTargetCoordinatesAndPhaseRules() {
        assertTrue(validator.hasRequiredFields(
            MessageType.MOVE_TARGET,
            Map.of("type", "MOVE_TARGET", "playerId", "p-1", "targetX", 12.5, "targetY", -4.0)
        ));
        assertFalse(validator.hasRequiredFields(
            MessageType.MOVE_TARGET,
            Map.of("type", "MOVE_TARGET", "playerId", "p-1", "targetX", 12.5)
        ));
        assertTrue(validator.isAcceptedInGameplay(MessageType.MOVE_TARGET, true));
        assertFalse(validator.isAcceptedInGameplay(MessageType.MOVE_TARGET, false));
    }

    @Test
    void requiresSnapshotEnvelopeFieldsForCriticalSnapshots() {
        assertTrue(validator.hasRequiredFields(
            MessageType.SNAPSHOT,
            Map.of("type", "SNAPSHOT", "seq", 1L, "players", java.util.List.of())
        ));
        assertFalse(validator.hasRequiredFields(
            MessageType.SNAPSHOT,
            Map.of("type", "SNAPSHOT", "seq", 1L)
        ));
        assertFalse(validator.hasRequiredFields(
            MessageType.GAME_OVER,
            Map.of("type", "GAME_OVER", "players", java.util.List.of())
        ));
    }
}
