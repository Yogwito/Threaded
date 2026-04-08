package com.dino.application.services;

import com.dino.application.levels.Box;
import com.dino.application.levels.Coin;
import com.dino.application.levels.LevelData;
import com.dino.application.levels.LevelLoader;
import com.dino.application.levels.Platform;
import com.dino.config.GameConfig;
import com.dino.domain.entities.ButtonSwitch;
import com.dino.domain.entities.CollectibleItem;
import com.dino.domain.entities.ExitZone;
import com.dino.domain.entities.PlatformTile;
import com.dino.domain.entities.Player;
import com.dino.domain.entities.PushBlock;
import com.dino.domain.events.EventNames;
import com.dino.domain.rules.GameRules;

import java.util.List;
import java.util.Map;

/**
 * Gestiona score, hazards, progreso de campaña y reseteos de sala.
 *
 * <p>Su responsabilidad es coordinar la parte de reglas de juego que no
 * pertenecen a la física base: monedas, salida, carga de niveles, fallos de
 * sala, score y finalización de campaña.</p>
 */
public final class LevelFlowService {
    private final SessionService sessionService;
    private final EventPublisher eventPublisher;

    private boolean buttonScoreAwarded = false;
    private int nextFinishOrder = 1;

    public LevelFlowService(SessionService sessionService, EventPublisher eventPublisher) {
        this.sessionService = sessionService;
        this.eventPublisher = eventPublisher;
    }

    public void resetState() {
        buttonScoreAwarded = false;
        nextFinishOrder = 1;
    }

    public void loadLevel(int levelIndex, boolean resetScores) {
        sessionService.setCurrentLevelIndex(levelIndex);
        sessionService.getPlatforms().clear();
        sessionService.getSpecialPlatforms().clear();
        sessionService.getHazards().clear();
        sessionService.getCheckpoints().clear();
        sessionService.getSpawnPoints().clear();
        sessionService.getPushBlocks().clear();
        sessionService.getCoins().clear();
        LevelData levelData = LevelLoader.loadLevel(levelIndex + 1);
        applyLevelData(levelData, levelIndex);

        if (resetScores) {
            for (Player player : sessionService.getPlayers().values()) {
                player.setScore(0);
                player.setDeaths(0);
            }
        }
        buttonScoreAwarded = false;
        resetRoomState();
    }

    public void updateButtonAndDoor(List<Player> players) {
        ButtonSwitch button = sessionService.getButtonSwitch();
        var door = sessionService.getDoor();
        if (button == null || door == null) return;

        boolean pressed = false;
        Player presser = null;
        for (Player player : players) {
            if (!player.isConnected()) continue;
            if (GameRules.isPressingButton(player, button)) {
                pressed = true;
                presser = player;
                break;
            }
        }

        boolean changed = button.isPressed() != pressed;
        button.setPressed(pressed);
        if (pressed) {
            door.setOpen(true);
            if (!buttonScoreAwarded && presser != null) {
                buttonScoreAwarded = true;
                awardScore(presser, GameConfig.SCORE_BUTTON_PRESS, "activo el boton");
            }
        }
        if (changed) {
            eventPublisher.publish(EventNames.BUTTON_STATE_CHANGED, Map.of("pressed", pressed));
        }
    }

    public void updateExitState(List<Player> players) {
        ExitZone exitZone = sessionService.getExitZone();
        for (Player player : players) {
            if (!player.isConnected()) continue;
            boolean wasAtExit = player.isAtExit();
            boolean isAtExit = GameRules.isInsideExit(player, exitZone);
            player.setAtExit(isAtExit);
            if (!wasAtExit && isAtExit) {
                player.setFinishOrder(nextFinishOrder++);
                awardScore(player, scoreForFinishOrder(player.getFinishOrder()), "llego a la salida");
                eventPublisher.publish(EventNames.PLAYER_REACHED_EXIT, Map.of(
                    "playerId", player.getId(),
                    "finishOrder", player.getFinishOrder()
                ));
            }
        }
    }

    public void updateCoins(List<Player> players) {
        for (CollectibleItem coin : sessionService.getCoins()) {
            if (!coin.isActive()) continue;
            for (Player player : players) {
                if (!player.isConnected() || !player.isAlive()) continue;
                if (GameRules.intersects(player.getX(), player.getY(), player.getWidth(), player.getHeight(),
                    coin.getX(), coin.getY(), GameConfig.COIN_SIZE, GameConfig.COIN_SIZE)) {
                    coin.setActive(false);
                    awardScore(player, coin.getPoints(), "recogió moneda");
                    eventPublisher.publish(EventNames.COIN_COLLECTED, Map.of(
                        "playerId", player.getId(),
                        "coinId", coin.getId(),
                        "points", coin.getPoints(),
                        "x", coin.getX(),
                        "y", coin.getY()
                    ));
                    break;
                }
            }
        }
    }

    /**
     * Procesa muertes por caída o hazards. Si ocurre una falla, reinicia la
     * sala actual y corta el tick.
     *
     * @return {@code true} si el tick debe abortarse porque hubo reset
     */
    public boolean resolveFailures(List<Player> players) {
        for (Player player : players) {
            if (player.isConnected() && player.isAlive() && player.getY() > GameConfig.FALL_RESET_Y) {
                player.setAlive(false);
                player.setDeaths(player.getDeaths() + 1);
                awardScore(player, -GameConfig.SCORE_FALL_PENALTY, "cayo al vacio");
                eventPublisher.publish(EventNames.PLAYER_DIED, Map.of("playerId", player.getId()));
                resetRoom("Caida al vacio");
                return true;
            }
            if (player.isConnected() && player.isAlive() && isTouchingHazard(player)) {
                player.setAlive(false);
                player.setDeaths(player.getDeaths() + 1);
                awardScore(player, -GameConfig.SCORE_FALL_PENALTY, "toco un hazard");
                eventPublisher.publish(EventNames.PLAYER_DIED, Map.of("playerId", player.getId()));
                resetRoom("Hazard");
                return true;
            }
        }
        return false;
    }

    /**
     * Avanza al siguiente nivel o finaliza la campaña si ya no quedan más.
     *
     * @return {@code true} si la campaña terminó y el host debe dejar de
     *         simular ticks
     */
    public boolean advanceLevelOrFinish() {
        int nextLevel = sessionService.getCurrentLevelIndex() + 1;
        if (nextLevel >= sessionService.getTotalLevels()) {
            sessionService.setGameRunning(false);
            eventPublisher.publish(EventNames.LEVEL_COMPLETED, Map.of(
                "elapsedTime", sessionService.getElapsedTime(),
                "levelIndex", sessionService.getCurrentLevelIndex()
            ));
            eventPublisher.publish(EventNames.GAME_OVER, Map.of("elapsedTime", sessionService.getElapsedTime()));
            return true;
        }

        loadLevel(nextLevel, false);
        eventPublisher.publish(EventNames.LEVEL_ADVANCED, Map.of("levelIndex", nextLevel));
        return false;
    }

    private void resetRoom(String reason) {
        sessionService.setRoomResetCount(sessionService.getRoomResetCount() + 1);
        sessionService.setRoomResetReason(reason);
        resetRoomState();
        eventPublisher.publish(EventNames.ROOM_RESET, Map.of("reason", reason));
    }

    private void applyLevelData(LevelData levelData, int levelIndex) {
        sessionService.setCurrentLevelIndex(levelIndex);
        sessionService.setCurrentLevelName(levelData.getName());
        sessionService.setCurrentBackground(levelData.getBackground());
        sessionService.setCurrentTileSize(levelData.getTileSize());

        int index = 0;
        for (Platform platform : levelData.getPlatforms()) {
            sessionService.getPlatforms().add(toPlatformTile("platform_" + index++, platform));
        }

        int specialIndex = 0;
        for (Platform platform : levelData.getSpecialPlatforms()) {
            PlatformTile tile = toPlatformTile("special_" + specialIndex++, platform);
            sessionService.getPlatforms().add(tile);
            sessionService.getSpecialPlatforms().add(new PlatformTile(tile.getId(), tile.getX(), tile.getY(), tile.getWidth(), tile.getHeight()));
        }

        int hazardIndex = 0;
        for (Platform hazard : levelData.getHazards()) {
            sessionService.getHazards().add(toPlatformTile("hazard_" + hazardIndex++, hazard));
        }

        int checkpointIndex = 0;
        for (Platform checkpoint : levelData.getCheckpoints()) {
            sessionService.getCheckpoints().add(toPlatformTile("checkpoint_" + checkpointIndex++, checkpoint));
        }

        for (double[] spawn : levelData.getSpawnPoints()) {
            sessionService.getSpawnPoints().add(new double[]{spawn[0], spawn[1]});
        }

        int boxIndex = 0;
        for (Box box : levelData.getBoxes()) {
            PushBlock pushBlock = new PushBlock("box_" + boxIndex++, box.getX(), box.getY(), box.getWidth(), box.getHeight());
            pushBlock.setHomeX(box.getX());
            pushBlock.setHomeY(box.getY());
            sessionService.getPushBlocks().add(pushBlock);
        }

        sessionService.setButtonSwitch(null);
        sessionService.setDoor(null);
        sessionService.setExitZone(createExitZone(levelData.getGoals()));

        double coinOffset = (levelData.getTileSize() - GameConfig.COIN_SIZE) / 2.0;
        int coinIndex = 0;
        for (Coin coin : levelData.getCoins()) {
            sessionService.getCoins().add(new CollectibleItem(
                "coin_" + coinIndex++,
                coin.getX() + coinOffset,
                coin.getY() + coinOffset,
                coin.getPoints()
            ));
        }
    }

    private ExitZone createExitZone(List<Platform> goals) {
        if (goals.isEmpty()) return new ExitZone(0, 0, GameConfig.EXIT_WIDTH, GameConfig.EXIT_HEIGHT);
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Platform goal : goals) {
            minX = Math.min(minX, goal.getX());
            minY = Math.min(minY, goal.getY());
            maxX = Math.max(maxX, goal.getX() + goal.getWidth());
            maxY = Math.max(maxY, goal.getY() + goal.getHeight());
        }
        return new ExitZone(minX, minY, maxX - minX, maxY - minY);
    }

    private PlatformTile toPlatformTile(String id, Platform platform) {
        return new PlatformTile(id, platform.getX(), platform.getY(), platform.getWidth(), platform.getHeight());
    }

    private boolean isTouchingHazard(Player player) {
        for (PlatformTile hazard : sessionService.getHazards()) {
            if (GameRules.intersects(player.getX(), player.getY(), player.getWidth(), player.getHeight(),
                hazard.getX(), hazard.getY(), hazard.getWidth(), hazard.getHeight())) {
                return true;
            }
        }
        return false;
    }

    private void resetRoomState() {
        int index = 0;
        List<double[]> spawns = sessionService.getSpawnPoints();
        for (Player player : sessionService.getPlayers().values()) {
            if (!player.isConnected()) continue;
            double[] spawn = spawns.get(Math.min(index, spawns.size() - 1));
            player.setX(spawn[0]);
            player.setY(spawn[1]);
            player.setVx(0);
            player.setVy(0);
            player.setCoyoteTimer(0);
            player.setGrounded(false);
            player.setAlive(true);
            player.setAtExit(false);
            player.setFinishOrder(0);
            player.setTargetX(spawn[0] + player.getWidth() / 2.0);
            player.setTargetY(spawn[1] + player.getHeight() / 2.0);
            index++;
        }
        if (sessionService.getButtonSwitch() != null) sessionService.getButtonSwitch().setPressed(false);
        if (sessionService.getDoor() != null) sessionService.getDoor().setOpen(false);
        for (PushBlock block : sessionService.getPushBlocks()) {
            block.setX(block.getHomeX());
            block.setY(block.getHomeY());
            block.setVx(0);
            block.setVy(0);
        }
        for (CollectibleItem coin : sessionService.getCoins()) coin.setActive(true);
        nextFinishOrder = 1;
    }

    private void awardScore(Player player, int delta, String reason) {
        if (delta == 0) return;
        player.addScore(delta);
        eventPublisher.publish(EventNames.SCORE_CHANGED, Map.of(
            "playerId", player.getId(),
            "score", player.getScore(),
            "delta", delta,
            "reason", reason
        ));
    }

    private int scoreForFinishOrder(int order) {
        return switch (order) {
            case 1 -> GameConfig.SCORE_FIRST_EXIT;
            case 2 -> GameConfig.SCORE_SECOND_EXIT;
            default -> GameConfig.SCORE_LATE_EXIT;
        };
    }
}
