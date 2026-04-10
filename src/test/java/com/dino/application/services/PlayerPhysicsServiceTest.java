package com.dino.application.services;

import com.dino.domain.entities.Player;
import com.dino.testsupport.RecordingEventPublisher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerPhysicsServiceTest {
    @Test
    void syncInputsToPlayersClearsStaleAimAfterRespawn() {
        SessionWorldState worldState = new SessionWorldState();
        RecordingEventPublisher events = new RecordingEventPublisher();
        ThreadConstraintService threadConstraintService = new ThreadConstraintService(worldState, events);
        PlayerPhysicsService physicsService = new PlayerPhysicsService(worldState, events, threadConstraintService);

        Player player = new Player("p1", "Host", "blue");
        player.setX(96);
        player.setY(160);
        player.setGrounded(true);
        worldState.players().put(player.getId(), player);

        physicsService.handleMoveTarget(player.getId(), 900, 120);

        double respawnTargetX = player.getCenterX();
        double respawnTargetY = player.getCenterY();
        player.setTargetX(respawnTargetX);
        player.setTargetY(respawnTargetY);
        physicsService.syncInputsToPlayers(List.of(player));
        physicsService.updatePlayers(List.of(player), 0.016);

        assertEquals(96.0, player.getX(), 0.0001);
        assertEquals(0.0, player.getVx(), 0.0001);
        assertEquals(respawnTargetX, player.getTargetX(), 0.0001);
        assertEquals(respawnTargetY, player.getTargetY(), 0.0001);
    }
}
