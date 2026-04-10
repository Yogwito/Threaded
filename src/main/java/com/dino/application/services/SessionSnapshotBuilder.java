package com.dino.application.services;

import com.dino.domain.entities.ButtonSwitch;
import com.dino.domain.entities.CollectibleItem;
import com.dino.domain.entities.Door;
import com.dino.domain.entities.ExitZone;
import com.dino.domain.entities.PlatformTile;
import com.dino.domain.entities.Player;
import com.dino.domain.entities.PushBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Constructor de snapshots UDP a partir del estado vivo de una sesión.
 *
 * <p>Separa del store principal la responsabilidad de proyectar entidades y
 * metadatos del runtime a un payload serializable. Esto permite probar el
 * contrato del snapshot sin arrastrar lógica de transición o coordinación.</p>
 */
final class SessionSnapshotBuilder {
    /**
     * Construye un snapshot completo listo para serializarse y enviarse.
     *
     * @param session sesión fuente del estado
     * @param sequence secuencia monotónica del snapshot
     * @return payload serializable del estado actual
     */
    Map<String, Object> buildSnapshot(SessionService session, long sequence) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("seq", sequence);
        snapshot.put("elapsedTime", session.getElapsedTime());
        snapshot.put("gameRunning", session.isGameRunning());
        snapshot.put("roomResetCount", session.getRoomResetCount());
        snapshot.put("roomResetReason", session.getRoomResetReason());
        snapshot.put("currentLevelIndex", session.getCurrentLevelIndex());
        snapshot.put("totalLevels", session.getTotalLevels());
        snapshot.put("currentLevelName", session.getCurrentLevelName());
        snapshot.put("currentBackground", session.getCurrentBackground());
        snapshot.put("currentTileSize", session.getCurrentTileSize());

        List<Map<String, Object>> playerList = new ArrayList<>();
        for (Player player : session.getPlayers().values()) {
            Map<String, Object> playerData = new HashMap<>();
            playerData.put("id", player.getId());
            playerData.put("name", player.getName());
            playerData.put("color", player.getColor());
            playerData.put("x", player.getX());
            playerData.put("y", player.getY());
            playerData.put("vx", player.getVx());
            playerData.put("vy", player.getVy());
            playerData.put("coyoteTimer", player.getCoyoteTimer());
            playerData.put("grounded", player.isGrounded());
            playerData.put("alive", player.isAlive());
            playerData.put("atExit", player.isAtExit());
            playerData.put("targetX", player.getTargetX());
            playerData.put("targetY", player.getTargetY());
            playerData.put("score", player.getScore());
            playerData.put("deaths", player.getDeaths());
            playerData.put("finishOrder", player.getFinishOrder());
            playerData.put("connected", player.isConnected());
            playerData.put("ready", player.isReady());
            playerList.add(playerData);
        }
        snapshot.put("players", playerList);

        snapshot.put("platforms", writePlatformTiles(session.getPlatforms()));
        snapshot.put("specialPlatforms", writePlatformTiles(session.getSpecialPlatforms()));
        snapshot.put("hazards", writePlatformTiles(session.getHazards()));
        snapshot.put("checkpoints", writePlatformTiles(session.getCheckpoints()));

        List<Map<String, Object>> spawnList = new ArrayList<>();
        for (double[] spawn : session.getSpawnPoints()) {
            Map<String, Object> raw = new HashMap<>();
            raw.put("x", spawn[0]);
            raw.put("y", spawn[1]);
            spawnList.add(raw);
        }
        snapshot.put("spawnPoints", spawnList);

        if (session.getButtonSwitch() != null) {
            ButtonSwitch button = session.getButtonSwitch();
            Map<String, Object> raw = new HashMap<>();
            raw.put("id", button.getId());
            raw.put("x", button.getX());
            raw.put("y", button.getY());
            raw.put("width", button.getWidth());
            raw.put("height", button.getHeight());
            raw.put("pressed", button.isPressed());
            snapshot.put("buttonSwitch", raw);
        }

        if (session.getDoor() != null) {
            Door door = session.getDoor();
            Map<String, Object> raw = new HashMap<>();
            raw.put("id", door.getId());
            raw.put("x", door.getX());
            raw.put("y", door.getY());
            raw.put("width", door.getWidth());
            raw.put("height", door.getHeight());
            raw.put("open", door.isOpen());
            snapshot.put("door", raw);
        }

        if (session.getExitZone() != null) {
            ExitZone exitZone = session.getExitZone();
            Map<String, Object> raw = new HashMap<>();
            raw.put("x", exitZone.getX());
            raw.put("y", exitZone.getY());
            raw.put("width", exitZone.getWidth());
            raw.put("height", exitZone.getHeight());
            snapshot.put("exitZone", raw);
        }

        List<Map<String, Object>> pushBlockList = new ArrayList<>();
        for (PushBlock block : session.getPushBlocks()) {
            Map<String, Object> raw = new HashMap<>();
            raw.put("id", block.getId());
            raw.put("x", block.getX());
            raw.put("y", block.getY());
            raw.put("width", block.getWidth());
            raw.put("height", block.getHeight());
            raw.put("vx", block.getVx());
            raw.put("vy", block.getVy());
            raw.put("homeX", block.getHomeX());
            raw.put("homeY", block.getHomeY());
            pushBlockList.add(raw);
        }
        snapshot.put("pushBlocks", pushBlockList);

        List<Map<String, Object>> coinList = new ArrayList<>();
        for (CollectibleItem coin : session.getCoins()) {
            Map<String, Object> raw = new HashMap<>();
            raw.put("id", coin.getId());
            raw.put("x", coin.getX());
            raw.put("y", coin.getY());
            raw.put("points", coin.getPoints());
            raw.put("active", coin.isActive());
            coinList.add(raw);
        }
        snapshot.put("coins", coinList);

        return snapshot;
    }

    private List<Map<String, Object>> writePlatformTiles(List<PlatformTile> tiles) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (PlatformTile tile : tiles) {
            Map<String, Object> raw = new HashMap<>();
            raw.put("id", tile.getId());
            raw.put("x", tile.getX());
            raw.put("y", tile.getY());
            raw.put("width", tile.getWidth());
            raw.put("height", tile.getHeight());
            result.add(raw);
        }
        return result;
    }
}
