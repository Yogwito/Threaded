package com.dino.application.services;

import com.dino.domain.entities.ButtonSwitch;
import com.dino.domain.entities.CollectibleItem;
import com.dino.domain.entities.Door;
import com.dino.domain.entities.ExitZone;
import com.dino.domain.entities.PlatformTile;
import com.dino.domain.entities.Player;
import com.dino.domain.entities.PushBlock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Proyector de snapshots UDP sobre el estado vivo de una sesión.
 *
 * <p>Extrae del store principal la lógica de traducción desde payloads de red a
 * entidades mutables del juego. Mantener esta responsabilidad fuera de
 * {@link SessionService} reduce acoplamiento y facilita probar el protocolo por
 * separado del resto del runtime.</p>
 */
final class SessionSnapshotApplier {
    /**
     * Aplica un snapshot ya deserializado sobre la sesión indicada.
     *
     * @param session sesión destino a actualizar
     * @param data snapshot recibido por red
     */
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
                Player player = session.getPlayers().computeIfAbsent(id, ignored -> new Player());
                player.setId(id);
                player.setName((String) pd.getOrDefault("name", player.getName()));
                player.setColor((String) pd.getOrDefault("color", player.getColor()));
                player.setX(((Number) pd.getOrDefault("x", 0)).doubleValue());
                player.setY(((Number) pd.getOrDefault("y", 0)).doubleValue());
                player.setVx(((Number) pd.getOrDefault("vx", 0)).doubleValue());
                player.setVy(((Number) pd.getOrDefault("vy", 0)).doubleValue());
                player.setCoyoteTimer(((Number) pd.getOrDefault("coyoteTimer", 0)).doubleValue());
                player.setGrounded((Boolean) pd.getOrDefault("grounded", false));
                player.setAlive((Boolean) pd.getOrDefault("alive", true));
                player.setAtExit((Boolean) pd.getOrDefault("atExit", false));
                player.setTargetX(((Number) pd.getOrDefault("targetX", player.getX())).doubleValue());
                player.setTargetY(((Number) pd.getOrDefault("targetY", player.getY())).doubleValue());
                player.setScore(((Number) pd.getOrDefault("score", 0)).intValue());
                player.setDeaths(((Number) pd.getOrDefault("deaths", 0)).intValue());
                player.setFinishOrder(((Number) pd.getOrDefault("finishOrder", 0)).intValue());
                player.setConnected((Boolean) pd.getOrDefault("connected", true));
                player.setReady((Boolean) pd.getOrDefault("ready", false));
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
            Door door = new Door();
            door.setId((String) raw.get("id"));
            door.setX(((Number) raw.get("x")).doubleValue());
            door.setY(((Number) raw.get("y")).doubleValue());
            door.setWidth(((Number) raw.get("width")).doubleValue());
            door.setHeight(((Number) raw.get("height")).doubleValue());
            door.setOpen((Boolean) raw.getOrDefault("open", false));
            session.setDoor(door);
        } else {
            session.setDoor(null);
        }

        if (data.containsKey("exitZone")) {
            Map<String, Object> raw = (Map<String, Object>) data.get("exitZone");
            ExitZone exitZone = new ExitZone();
            exitZone.setX(((Number) raw.get("x")).doubleValue());
            exitZone.setY(((Number) raw.get("y")).doubleValue());
            exitZone.setWidth(((Number) raw.get("width")).doubleValue());
            exitZone.setHeight(((Number) raw.get("height")).doubleValue());
            session.setExitZone(exitZone);
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
                CollectibleItem collectible = new CollectibleItem(
                    (String) raw.get("id"),
                    ((Number) raw.get("x")).doubleValue(),
                    ((Number) raw.get("y")).doubleValue(),
                    ((Number) raw.get("points")).intValue()
                );
                collectible.setActive((Boolean) raw.getOrDefault("active", true));
                session.getCoins().add(collectible);
            }
        }
    }

    private PlatformTile readPlatformTile(Map<String, Object> raw) {
        PlatformTile tile = new PlatformTile();
        tile.setId((String) raw.get("id"));
        tile.setX(((Number) raw.get("x")).doubleValue());
        tile.setY(((Number) raw.get("y")).doubleValue());
        tile.setWidth(((Number) raw.get("width")).doubleValue());
        tile.setHeight(((Number) raw.get("height")).doubleValue());
        return tile;
    }
}
