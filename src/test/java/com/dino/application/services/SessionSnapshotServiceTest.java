package com.dino.application.services;

import com.dino.domain.entities.ButtonSwitch;
import com.dino.domain.entities.CollectibleItem;
import com.dino.domain.entities.Door;
import com.dino.domain.entities.ExitZone;
import com.dino.domain.entities.PlatformTile;
import com.dino.domain.entities.Player;
import com.dino.domain.entities.PushBlock;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionSnapshotServiceTest {
    @Test
    void snapshotRoundTripPreservesCoreState() {
        EventBus events = new EventBus();
        SessionService source = new SessionService(events);
        SessionLifecycleService lifecycle = new SessionLifecycleService(source);
        lifecycle.configureAsHost("host-1", "Host", "127.0.0.1", 7000, 2);

        Player host = new Player("host-1", "Host", "red");
        host.setX(100);
        host.setY(200);
        host.setScore(42);
        host.setReady(true);
        source.addPlayer(host);
        source.getPlatforms().add(new PlatformTile("p1", 0, 300, 64, 64));
        source.getSpecialPlatforms().add(new PlatformTile("sp1", 64, 300, 64, 64));
        source.getHazards().add(new PlatformTile("hz1", 128, 300, 64, 64));
        source.getCheckpoints().add(new PlatformTile("cp1", 192, 300, 64, 64));
        source.getSpawnPoints().add(new double[]{10, 20});
        source.setButtonSwitch(new ButtonSwitch("btn", 10, 10, 32, 8));
        source.setDoor(new Door("door", 50, 20, 32, 64));
        source.setExitZone(new ExitZone(500, 200, 64, 64));
        PushBlock block = new PushBlock("box", 250, 250, 64, 64);
        block.setHomeX(250);
        block.setHomeY(250);
        source.getPushBlocks().add(block);
        source.getCoins().add(new CollectibleItem("coin", 300, 220, 10));
        source.setCurrentLevelIndex(2);
        source.setCurrentLevelName("Nivel de prueba");
        source.setCurrentBackground("forest");
        source.setCurrentTileSize(64);
        source.setElapsedTime(12.5);
        source.setRoomResetCount(1);
        source.setRoomResetReason("Hazard");
        source.setGameRunning(true);

        Map<String, Object> snapshot = source.buildAuthoritativeSnapshot();

        SessionService target = new SessionService(new EventBus());
        target.applyAuthoritativeSnapshot(snapshot);

        assertEquals(1, target.getPlayersSnapshot().size());
        assertEquals("Host", target.getPlayersSnapshot().getFirst().getName());
        assertEquals(42, target.getPlayersSnapshot().getFirst().getScore());
        assertEquals(1, target.getPlatformsSnapshot().size());
        assertEquals(1, target.getSpecialPlatformsSnapshot().size());
        assertEquals(1, target.getHazardsSnapshot().size());
        assertEquals(1, target.getCheckpointsSnapshot().size());
        assertEquals(1, target.getPushBlocksSnapshot().size());
        assertEquals(1, target.getCoinsSnapshot().size());
        assertEquals("Nivel de prueba", target.getCurrentLevelName());
        assertEquals("forest", target.getCurrentBackground());
        assertEquals(12.5, target.getElapsedTime());
        assertTrue(target.isGameRunning());
    }

    @Test
    void rejectsOutOfOrderSnapshots() {
        SessionService session = new SessionService(new EventBus());
        session.applyAuthoritativeSnapshot(Map.of(
            "seq", 2L,
            "players", java.util.List.of(),
            "elapsedTime", 8.0
        ));

        session.applyAuthoritativeSnapshot(Map.of(
            "seq", 1L,
            "players", java.util.List.of(),
            "elapsedTime", 2.0
        ));

        assertEquals(8.0, session.getElapsedTime());
    }

    @Test
    void snapshotTransitionToGameOverLeavesMatchStopped() {
        SessionService session = new SessionService(new EventBus());
        SessionLifecycleService lifecycle = new SessionLifecycleService(session);
        lifecycle.configureAsClient("client-1", "Client", "127.0.0.1", 7001, "127.0.0.1", 7000);
        lifecycle.enterGameplay();
        assertTrue(session.isGameRunning());

        lifecycle.enterGameOver();

        assertFalse(session.isGameRunning());
    }
}
