package com.dino.application.services;

import com.dino.domain.entities.ButtonSwitch;
import com.dino.domain.entities.CollectibleItem;
import com.dino.domain.entities.Door;
import com.dino.domain.entities.ExitZone;
import com.dino.domain.entities.PlatformTile;
import com.dino.domain.entities.Player;
import com.dino.domain.entities.PushBlock;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Traduce entre el estado vivo de {@link SessionService} y snapshots
 * serializables por UDP.
 *
 * <p>Extrae del servicio de sesión la responsabilidad de mapear entidades a
 * estructuras de red y viceversa, manteniendo en `SessionService` únicamente la
 * coordinación del estado y la secuencia de snapshots.</p>
 */
final class SessionSnapshotMapper {
    @SuppressWarnings("unchecked")
    void applySnapshot(SessionService session, Map<String, Object> data) {
        if (data.containsKey("elapsedTime")) session.setElapsedTime(((Number) data.get("elapsedTime")).doubleValue());
        if (data.containsKey("gameRunning")) session.setGameRunning((Boolean) data.get("gameRunning"));
        if (data.containsKey("roomResetCount")) session.setRoomResetCount(((Number) data.get("roomResetCount")).intValue());
        if (data.containsKey("roomResetReason")) session.setRoomResetReason(String.valueOf(data.get("roomResetReason")));
        if (data.containsKey("currentLevelIndex")) session.setCurrentLevelIndex(((Number) data.get("currentLevelIndex")).intValue());
        if (data.containsKey("totalLevels")) session.setTotalLevels(((Number) data.get("totalLevels")).intValue());
        if (data.containsKey("currentLevelName")) session.setCurrentLevelName(String.valueOf(data.get("currentLevelName")));
        if (data.containsKey("currentBackground")) session.setCurrentBackground(String.valueOf(data.get("currentBackground")));
        if (data.containsKey("currentTileSize")) session.setCurrentTileSize(((Number) data.get("currentTileSize")).intValue());

        if (data.containsKey("players")) {
            Set<String> snapshotPlayerIds = new HashSet<>();
            for (Map<String, Object> pd : (List<Map<String, Object>>) data.get("players")) {
                String id = (String) pd.get("id");
                snapshotPlayerIds.add(id);
                Player p = session.getPlayers().computeIfAbsent(id, k -> new Player());
                p.setId(id);
                p.setName((String) pd.getOrDefault("name", p.getName()));
                p.setColor((String) pd.getOrDefault("color", p.getColor()));
                p.setX(((Number) pd.getOrDefault("x", 0)).doubleValue());
                p.setY(((Number) pd.getOrDefault("y", 0)).doubleValue());
                p.setVx(((Number) pd.getOrDefault("vx", 0)).doubleValue());
                p.setVy(((Number) pd.getOrDefault("vy", 0)).doubleValue());
                p.setCoyoteTimer(((Number) pd.getOrDefault("coyoteTimer", 0)).doubleValue());
                p.setGrounded((Boolean) pd.getOrDefault("grounded", false));
                p.setAlive((Boolean) pd.getOrDefault("alive", true));
                p.setAtExit((Boolean) pd.getOrDefault("atExit", false));
                p.setTargetX(((Number) pd.getOrDefault("targetX", p.getX())).doubleValue());
                p.setTargetY(((Number) pd.getOrDefault("targetY", p.getY())).doubleValue());
                p.setScore(((Number) pd.getOrDefault("score", 0)).intValue());
                p.setDeaths(((Number) pd.getOrDefault("deaths", 0)).intValue());
                p.setFinishOrder(((Number) pd.getOrDefault("finishOrder", 0)).intValue());
                p.setConnected((Boolean) pd.getOrDefault("connected", true));
                p.setReady((Boolean) pd.getOrDefault("ready", false));
            }

            List<String> staleIds = new ArrayList<>();
            for (String playerId : new ArrayList<>(session.getPlayers().keySet())) {
                if (!snapshotPlayerIds.contains(playerId)) {
                    staleIds.add(playerId);
                }
            }
            staleIds.forEach(session::removePlayer);
        }

        if (data.containsKey("platforms")) {
            session.getPlatforms().clear();
            for (Map<String, Object> raw : (List<Map<String, Object>>) data.get("platforms")) {
                session.getPlatforms().add(readPlatformTile(raw));
            }
        }

        if (data.containsKey("specialPlatforms")) {
            session.getSpecialPlatforms().clear();
            for (Map<String, Object> raw : (List<Map<String, Object>>) data.get("specialPlatforms")) {
                session.getSpecialPlatforms().add(readPlatformTile(raw));
            }
        }

        if (data.containsKey("hazards")) {
            session.getHazards().clear();
            for (Map<String, Object> raw : (List<Map<String, Object>>) data.get("hazards")) {
                session.getHazards().add(readPlatformTile(raw));
            }
        }

        if (data.containsKey("checkpoints")) {
            session.getCheckpoints().clear();
            for (Map<String, Object> raw : (List<Map<String, Object>>) data.get("checkpoints")) {
                session.getCheckpoints().add(readPlatformTile(raw));
            }
        }

        if (data.containsKey("spawnPoints")) {
            session.getSpawnPoints().clear();
            for (Map<String, Object> raw : (List<Map<String, Object>>) data.get("spawnPoints")) {
                session.getSpawnPoints().add(new double[]{
                    ((Number) raw.get("x")).doubleValue(),
                    ((Number) raw.get("y")).doubleValue()
                });
            }
        }

        if (data.containsKey("buttonSwitch")) {
            Map<String, Object> raw = (Map<String, Object>) data.get("buttonSwitch");
            ButtonSwitch button = new ButtonSwitch();
            button.setId((String) raw.get("id"));
            button.setX(((Number) raw.get("x")).doubleValue());
            button.setY(((Number) raw.get("y")).doubleValue());
            button.setWidth(((Number) raw.get("width")).doubleValue());
            button.setHeight(((Number) raw.get("height")).doubleValue());
            button.setPressed((Boolean) raw.getOrDefault("pressed", false));
            session.setButtonSwitch(button);
        } else {
            session.setButtonSwitch(null);
        }

        if (data.containsKey("door")) {
            Map<String, Object> raw = (Map<String, Object>) data.get("door");
            Door value = new Door();
            value.setId((String) raw.get("id"));
            value.setX(((Number) raw.get("x")).doubleValue());
            value.setY(((Number) raw.get("y")).doubleValue());
            value.setWidth(((Number) raw.get("width")).doubleValue());
            value.setHeight(((Number) raw.get("height")).doubleValue());
            value.setOpen((Boolean) raw.getOrDefault("open", false));
            session.setDoor(value);
        } else {
            session.setDoor(null);
        }

        if (data.containsKey("exitZone")) {
            Map<String, Object> raw = (Map<String, Object>) data.get("exitZone");
            ExitZone value = new ExitZone();
            value.setX(((Number) raw.get("x")).doubleValue());
            value.setY(((Number) raw.get("y")).doubleValue());
            value.setWidth(((Number) raw.get("width")).doubleValue());
            value.setHeight(((Number) raw.get("height")).doubleValue());
            session.setExitZone(value);
        } else {
            session.setExitZone(null);
        }

        if (data.containsKey("pushBlocks")) {
            session.getPushBlocks().clear();
            for (Map<String, Object> raw : (List<Map<String, Object>>) data.get("pushBlocks")) {
                PushBlock block = new PushBlock();
                block.setId((String) raw.get("id"));
                block.setX(((Number) raw.get("x")).doubleValue());
                block.setY(((Number) raw.get("y")).doubleValue());
                block.setWidth(((Number) raw.get("width")).doubleValue());
                block.setHeight(((Number) raw.get("height")).doubleValue());
                block.setVx(((Number) raw.getOrDefault("vx", 0)).doubleValue());
                block.setVy(((Number) raw.getOrDefault("vy", 0)).doubleValue());
                block.setHomeX(((Number) raw.getOrDefault("homeX", block.getX())).doubleValue());
                block.setHomeY(((Number) raw.getOrDefault("homeY", block.getY())).doubleValue());
                session.getPushBlocks().add(block);
            }
        }

        if (data.containsKey("coins")) {
            session.getCoins().clear();
            for (Map<String, Object> raw : (List<Map<String, Object>>) data.get("coins")) {
                CollectibleItem c = new CollectibleItem(
                    (String) raw.get("id"),
                    ((Number) raw.get("x")).doubleValue(),
                    ((Number) raw.get("y")).doubleValue(),
                    ((Number) raw.get("points")).intValue()
                );
                c.setActive((Boolean) raw.getOrDefault("active", true));
                session.getCoins().add(c);
            }
        }
    }

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
        for (Player p : session.getPlayers().values()) {
            Map<String, Object> pd = new HashMap<>();
            pd.put("id", p.getId());
            pd.put("name", p.getName());
            pd.put("color", p.getColor());
            pd.put("x", p.getX());
            pd.put("y", p.getY());
            pd.put("vx", p.getVx());
            pd.put("vy", p.getVy());
            pd.put("coyoteTimer", p.getCoyoteTimer());
            pd.put("grounded", p.isGrounded());
            pd.put("alive", p.isAlive());
            pd.put("atExit", p.isAtExit());
            pd.put("targetX", p.getTargetX());
            pd.put("targetY", p.getTargetY());
            pd.put("score", p.getScore());
            pd.put("deaths", p.getDeaths());
            pd.put("finishOrder", p.getFinishOrder());
            pd.put("connected", p.isConnected());
            pd.put("ready", p.isReady());
            playerList.add(pd);
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
            ButtonSwitch buttonSwitch = session.getButtonSwitch();
            Map<String, Object> raw = new HashMap<>();
            raw.put("id", buttonSwitch.getId());
            raw.put("x", buttonSwitch.getX());
            raw.put("y", buttonSwitch.getY());
            raw.put("width", buttonSwitch.getWidth());
            raw.put("height", buttonSwitch.getHeight());
            raw.put("pressed", buttonSwitch.isPressed());
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

        List<Map<String, Object>> blockList = new ArrayList<>();
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
            blockList.add(raw);
        }
        snapshot.put("pushBlocks", blockList);

        List<Map<String, Object>> coinList = new ArrayList<>();
        for (CollectibleItem c : session.getCoins()) {
            Map<String, Object> raw = new HashMap<>();
            raw.put("id", c.getId());
            raw.put("x", c.getX());
            raw.put("y", c.getY());
            raw.put("points", c.getPoints());
            raw.put("active", c.isActive());
            coinList.add(raw);
        }
        snapshot.put("coins", coinList);

        return snapshot;
    }

    private static List<Map<String, Object>> writePlatformTiles(List<PlatformTile> tiles) {
        List<Map<String, Object>> rawTiles = new ArrayList<>();
        for (PlatformTile tile : tiles) {
            rawTiles.add(writePlatformTile(tile));
        }
        return rawTiles;
    }

    private static PlatformTile readPlatformTile(Map<String, Object> raw) {
        PlatformTile tile = new PlatformTile();
        tile.setId((String) raw.get("id"));
        tile.setX(((Number) raw.get("x")).doubleValue());
        tile.setY(((Number) raw.get("y")).doubleValue());
        tile.setWidth(((Number) raw.get("width")).doubleValue());
        tile.setHeight(((Number) raw.get("height")).doubleValue());
        return tile;
    }

    private static Map<String, Object> writePlatformTile(PlatformTile tile) {
        Map<String, Object> raw = new HashMap<>();
        raw.put("id", tile.getId());
        raw.put("x", tile.getX());
        raw.put("y", tile.getY());
        raw.put("width", tile.getWidth());
        raw.put("height", tile.getHeight());
        return raw;
    }
}
