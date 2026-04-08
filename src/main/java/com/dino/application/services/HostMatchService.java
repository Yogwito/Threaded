package com.dino.application.services;

import com.dino.application.levels.Box;
import com.dino.application.levels.Coin;
import com.dino.application.levels.LevelData;
import com.dino.application.levels.LevelLoader;
import com.dino.application.levels.Platform;
import com.dino.config.GameConfig;
import com.dino.domain.entities.ButtonSwitch;
import com.dino.domain.entities.CollectibleItem;
import com.dino.domain.entities.Door;
import com.dino.domain.entities.ExitZone;
import com.dino.domain.entities.PlatformTile;
import com.dino.domain.entities.Player;
import com.dino.domain.entities.PushBlock;
import com.dino.domain.events.EventNames;
import com.dino.domain.rules.GameRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulación autoritativa del host.
 *
 * <p>Recibe input de todos los jugadores, avanza la física, resuelve colisiones,
 * actualiza puntaje y decide transiciones críticas como reinicio de sala,
 * avance de nivel y fin de campaña. Es el corazón lógico de la partida: los
 * clientes no duplican esta lógica, solo la representan visualmente.</p>
 */
public class HostMatchService {
    private static final double COLLISION_SOUND_COOLDOWN = 0.12;
    private static final double THREAD_SOUND_COOLDOWN = 0.16;

    private final SessionService sessionService;
    private final EventBus eventBus;
    private final Map<String, InputState> playerInputs = new HashMap<>();
    private boolean gameOver = false;
    private boolean buttonScoreAwarded = false;
    private int nextFinishOrder = 1;
    private double collisionSoundCooldownRemaining = 0;
    private double threadSoundCooldownRemaining = 0;

    /**
     * Construye la simulación del host con acceso al estado compartido y al bus
     * de eventos.
     */
    public HostMatchService(SessionService sessionService, EventBus eventBus) {
        this.sessionService = sessionService;
        this.eventBus = eventBus;
    }

    /**
     * Inicializa el mundo desde el primer nivel y reinicia contadores globales.
     */
    public void initWorld() {
        sessionService.setCurrentLevelIndex(0);
        sessionService.setTotalLevels(Math.max(1, LevelLoader.countAvailableLevels()));
        sessionService.setElapsedTime(0);
        sessionService.setGameRunning(true);
        sessionService.setRoomResetCount(0);
        sessionService.setRoomResetReason("");
        playerInputs.clear();
        gameOver = false;
        buttonScoreAwarded = false;
        nextFinishOrder = 1;
        loadLevel(0, true);
    }

    /**
     * Registra el último objetivo de movimiento apuntado por un jugador.
     *
     * @param playerId identificador del jugador
     * @param targetX coordenada X del mouse en mundo
     * @param targetY coordenada Y del mouse en mundo
     */
    public void handleMoveTarget(String playerId, double targetX, double targetY) {
        InputState state = playerInputs.computeIfAbsent(playerId, ignored -> new InputState());
        state.targetX = targetX;
        state.targetY = targetY;
        state.hasTarget = true;
        Player player = sessionService.getPlayers().get(playerId);
        if (player != null) {
            player.setTargetX(targetX);
            player.setTargetY(targetY);
        }
    }

    /**
     * Encola un salto discreto para el siguiente tick del host.
     *
     * @param playerId identificador del jugador
     */
    public void handleJump(String playerId) {
        InputState state = playerInputs.computeIfAbsent(playerId, ignored -> new InputState());
        state.jumpQueued = true;
    }

    /**
     * Avanza una iteración de simulación del host.
     *
     * <p>El orden importa: primero se actualiza movimiento individual, luego se
     * aplica el hilo, después colisiones, objetos, puntaje y por último
     * transiciones de nivel o reinicios.</p>
     *
     * @param dt tiempo transcurrido en segundos desde el tick anterior
     */
    public void tick(double dt) {
        if (gameOver || !sessionService.isGameRunning()) return;
        sessionService.setElapsedTime(sessionService.getElapsedTime() + dt);
        collisionSoundCooldownRemaining = Math.max(0, collisionSoundCooldownRemaining - dt);
        threadSoundCooldownRemaining = Math.max(0, threadSoundCooldownRemaining - dt);

        List<Player> players = new ArrayList<>(sessionService.getPlayers().values());
        for (Player player : players) {
            if (!player.isConnected()) continue;
            updatePlayer(player, dt);
        }

        applyThreadElasticity(players, dt);
        resolvePlayerCollisions(players);
        updatePushBlocks(players, dt);

        updateButtonAndDoor(players);
        updateExitState(players);
        updateCoins(players);

        for (Player player : players) {
            if (player.isConnected() && player.isAlive() && player.getY() > GameConfig.FALL_RESET_Y) {
                player.setAlive(false);
                player.setDeaths(player.getDeaths() + 1);
                awardScore(player, -GameConfig.SCORE_FALL_PENALTY, "cayo al vacio");
                eventBus.publish(EventNames.PLAYER_DIED, Map.of("playerId", player.getId()));
                resetRoom("Caida al vacio");
                return;
            }
            if (player.isConnected() && player.isAlive() && isTouchingHazard(player)) {
                player.setAlive(false);
                player.setDeaths(player.getDeaths() + 1);
                awardScore(player, -GameConfig.SCORE_FALL_PENALTY, "toco un hazard");
                eventBus.publish(EventNames.PLAYER_DIED, Map.of("playerId", player.getId()));
                resetRoom("Hazard");
                return;
            }
        }

        if (GameRules.allConnectedPlayersAtExit(players)) {
            advanceLevelOrFinish();
        }
    }

    /**
     * Actualiza movimiento, salto, gravedad y colisiones base de un jugador.
     *
     * <p>Incluye control horizontal por objetivo, coyote time y jump buffer para
     * que la sensación de control sea menos rígida.</p>
     */
    private void updatePlayer(Player player, double dt) {
        InputState input = playerInputs.computeIfAbsent(player.getId(), ignored -> new InputState());
        double previousX = player.getX();
        double previousY = player.getY();
        double previousVx = player.getVx();
        double previousVy = player.getVy();
        double moveDirection = 0;
        if (input.hasTarget) {
            double dx = input.targetX - player.getCenterX();
            if (Math.abs(dx) > GameConfig.TARGET_REACHED_TOLERANCE) {
                moveDirection = Math.signum(dx);
            } else {
                moveDirection = 0;
            }
            player.setTargetX(input.targetX);
            player.setTargetY(input.targetY);
        }
        double targetVelocity = moveDirection * GameConfig.MOVE_SPEED;
        double velocityDelta = targetVelocity - player.getVx();
        double acceleration;
        if (moveDirection == 0) {
            acceleration = GameConfig.MOVE_FRICTION;
        } else {
            acceleration = player.isGrounded() ? GameConfig.MOVE_ACCELERATION : GameConfig.AIR_MOVE_ACCELERATION;
        }
        double maxDelta = acceleration * dt;
        if (Math.abs(velocityDelta) > maxDelta) {
            velocityDelta = Math.signum(velocityDelta) * maxDelta;
        }
        player.setVx(player.getVx() + velocityDelta);

        if (player.isGrounded()) {
            player.setCoyoteTimer(GameConfig.COYOTE_TIME_SECONDS);
        } else {
            player.setCoyoteTimer(Math.max(0, player.getCoyoteTimer() - dt));
        }

        if (input.jumpQueued) {
            input.jumpBufferTimer = GameConfig.JUMP_BUFFER_SECONDS;
        } else {
            input.jumpBufferTimer = Math.max(0, input.jumpBufferTimer - dt);
        }

        if (input.jumpBufferTimer > 0 && player.isGrounded()) {
            player.setVy(GameConfig.JUMP_FORCE);
            player.setGrounded(false);
            player.setCoyoteTimer(0);
            input.jumpBufferTimer = 0;
            eventBus.publish(EventNames.PLAYER_JUMPED, Map.of("playerId", player.getId()));
        }
        input.jumpQueued = false;

        player.setVy(player.getVy() + GameConfig.GRAVITY * dt);

        player.setX(player.getX() + player.getVx() * dt);
        resolveHorizontalCollisions(player);

        player.setY(player.getY() + player.getVy() * dt);
        resolveVerticalCollisions(player);

        if (input.jumpBufferTimer > 0 && player.isGrounded()) {
            player.setVy(GameConfig.JUMP_FORCE);
            player.setGrounded(false);
            player.setCoyoteTimer(0);
            input.jumpBufferTimer = 0;
            eventBus.publish(EventNames.PLAYER_JUMPED, Map.of("playerId", player.getId()));
        }

        clampPlayer(player);
        if (GameRules.violatesThreadDistance(player, sessionService.getPlayers().values())) {
            player.setX(previousX);
            player.setY(previousY);
            player.setVx(0);
            // Damp but don't zero vy: zeroing it caused "floating" jitter (gravity re-adds vy every frame)
            player.setVy(player.getVy() * 0.4);
        }
    }

    private void resolveHorizontalCollisions(Player player) {
        for (PlatformTile platform : sessionService.getPlatforms()) {
            if (!GameRules.intersects(player, platform)) continue;
            if (player.getVx() > 0) {
                player.setX(platform.getX() - player.getWidth());
            } else if (player.getVx() < 0) {
                player.setX(platform.getX() + platform.getWidth());
            }
            player.setVx(0);
        }

        Door door = sessionService.getDoor();
        if (GameRules.intersects(player, door)) {
            if (player.getVx() > 0) {
                player.setX(door.getX() - player.getWidth());
            } else if (player.getVx() < 0) {
                player.setX(door.getX() + door.getWidth());
            }
            player.setVx(0);
        }

        for (PushBlock block : sessionService.getPushBlocks()) {
            if (!GameRules.intersects(player, block)) continue;
            if (player.getVx() > 0) {
                player.setX(block.getX() - player.getWidth());
                block.setVx(Math.min(GameConfig.PUSH_BLOCK_MAX_SPEED,
                    Math.max(block.getVx(), player.getVx() * GameConfig.PUSH_BLOCK_PUSH_IMPULSE)));
            } else if (player.getVx() < 0) {
                player.setX(block.getX() + block.getWidth());
                block.setVx(Math.max(-GameConfig.PUSH_BLOCK_MAX_SPEED,
                    Math.min(block.getVx(), player.getVx() * GameConfig.PUSH_BLOCK_PUSH_IMPULSE)));
            }
            player.setVx(player.getVx() * 0.55);
        }
    }

    private void resolveVerticalCollisions(Player player) {
        player.setGrounded(false);
        for (PlatformTile platform : sessionService.getPlatforms()) {
            if (!GameRules.intersects(player, platform)) continue;
            if (player.getVy() > 0) {
                player.setY(platform.getY() - player.getHeight());
                player.setVy(0);
                player.setGrounded(true);
                player.setCoyoteTimer(GameConfig.COYOTE_TIME_SECONDS);
            } else if (player.getVy() < 0) {
                player.setY(platform.getY() + platform.getHeight());
                player.setVy(0);
            }
        }

        Door door = sessionService.getDoor();
        if (GameRules.intersects(player, door)) {
            if (player.getVy() > 0) {
                player.setY(door.getY() - player.getHeight());
                player.setGrounded(true);
                player.setCoyoteTimer(GameConfig.COYOTE_TIME_SECONDS);
            } else {
                player.setY(door.getY() + door.getHeight());
            }
            player.setVy(0);
        }

        for (PushBlock block : sessionService.getPushBlocks()) {
            if (!GameRules.intersects(player, block)) continue;
            if (player.getVy() > 0) {
                player.setY(block.getY() - player.getHeight());
                player.setVy(0);
                player.setGrounded(true);
                player.setCoyoteTimer(GameConfig.COYOTE_TIME_SECONDS);
            } else if (player.getVy() < 0) {
                player.setY(block.getY() + block.getHeight());
                player.setVy(0);
            }
        }
    }

    private void clampPlayer(Player player) {
        player.setX(Math.max(0, Math.min(GameConfig.LEVEL_WIDTH - player.getWidth(), player.getX())));
        if (player.getY() < 0) {
            player.setY(0);
            player.setVy(0);
        }
    }

    private void applyThreadElasticity(List<Player> players, double dt) {
        List<Player> connected = players.stream()
            .filter(player -> player.isConnected() && player.isAlive())
            .sorted((a, b) -> Double.compare(a.getX(), b.getX()))
            .toList();

        for (int i = 0; i < connected.size() - 1; i++) {
            Player a = connected.get(i);
            Player b = connected.get(i + 1);
            double dx = b.getCenterX() - a.getCenterX();
            double dy = b.getCenterY() - a.getCenterY();
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance <= GameConfig.THREAD_REST_DISTANCE || distance == 0) continue;

            double stretch = distance - GameConfig.THREAD_REST_DISTANCE;
            double nx = dx / distance;
            double ny = dy / distance;
            double relativeVelocity = (b.getVx() - a.getVx()) * nx + (b.getVy() - a.getVy()) * ny;
            double springForce = stretch * GameConfig.THREAD_PULL_FACTOR;
            double dampingForce = relativeVelocity * GameConfig.THREAD_DAMPING;
            // El hilo funciona como resorte amortiguado: corrige sin teletransportar.
            double pull = Math.min(Math.max(0, (springForce - dampingForce) * dt), stretch * 0.65);

            double aMobility = a.isGrounded() ? 0.42 : 0.58;
            double bMobility = b.isGrounded() ? 0.42 : 0.58;
            double totalMobility = aMobility + bMobility;
            double aShare = totalMobility == 0 ? 0.5 : bMobility / totalMobility;
            double bShare = totalMobility == 0 ? 0.5 : aMobility / totalMobility;

            if (stretch > 8 && threadSoundCooldownRemaining <= 0) {
                threadSoundCooldownRemaining = THREAD_SOUND_COOLDOWN;
                eventBus.publish(EventNames.THREAD_STRETCHED, Map.of(
                    "playerA", a.getId(),
                    "playerB", b.getId(),
                    "stretch", stretch
                ));
            }

            a.setX(a.getX() + nx * pull * aShare);
            a.setY(a.getY() + ny * pull * GameConfig.THREAD_VERTICAL_PULL * aShare);
            b.setX(b.getX() - nx * pull * bShare);
            b.setY(b.getY() - ny * pull * GameConfig.THREAD_VERTICAL_PULL * bShare);

            a.setVx(a.getVx() + nx * pull * 0.55);
            b.setVx(b.getVx() - nx * pull * 0.55);
            if (!a.isGrounded()) a.setVy(a.getVy() + ny * pull * 0.22);
            if (!b.isGrounded()) b.setVy(b.getVy() - ny * pull * 0.22);

            clampPlayer(a);
            resolveHorizontalCollisions(a);
            resolveVerticalCollisions(a);
            clampPlayer(b);
            resolveHorizontalCollisions(b);
            resolveVerticalCollisions(b);

            if (distance > GameConfig.THREAD_HARD_LIMIT) {
                double excess = distance - GameConfig.THREAD_HARD_LIMIT;
                a.setX(a.getX() + nx * excess * aShare);
                b.setX(b.getX() - nx * excess * bShare);
                a.setVx(a.getVx() * 0.65);
                b.setVx(b.getVx() * 0.65);
                clampPlayer(a);
                resolveHorizontalCollisions(a);
                resolveVerticalCollisions(a);
                clampPlayer(b);
                resolveHorizontalCollisions(b);
                resolveVerticalCollisions(b);
            }
        }
    }

    private void resolvePlayerCollisions(List<Player> players) {
        List<Player> connected = players.stream()
            .filter(player -> player.isConnected() && player.isAlive())
            .toList();

        for (int i = 0; i < connected.size(); i++) {
            for (int j = i + 1; j < connected.size(); j++) {
                Player a = connected.get(i);
                Player b = connected.get(j);
                if (!GameRules.intersects(a, b)) continue;

                double overlapX = Math.min(a.getX() + a.getWidth(), b.getX() + b.getWidth()) - Math.max(a.getX(), b.getX());
                double overlapY = Math.min(a.getY() + a.getHeight(), b.getY() + b.getHeight()) - Math.max(a.getY(), b.getY());
                if (overlapX <= 0 || overlapY <= 0) continue;

                if (!resolveVerticalPlayerContact(a, b, overlapY) && !resolveVerticalPlayerContact(b, a, overlapY)) {
                    resolveSidePlayerContact(a, b, overlapX);
                }

                if (collisionSoundCooldownRemaining <= 0) {
                    collisionSoundCooldownRemaining = COLLISION_SOUND_COOLDOWN;
                    eventBus.publish(EventNames.PLAYER_COLLIDED, Map.of(
                        "playerA", a.getId(),
                        "playerB", b.getId()
                    ));
                }

                clampPlayer(a);
                clampPlayer(b);
                resolveHorizontalCollisions(a);
                resolveVerticalCollisions(a);
                resolveHorizontalCollisions(b);
                resolveVerticalCollisions(b);
            }
        }
    }

    private boolean resolveVerticalPlayerContact(Player topCandidate, Player bottomCandidate, double overlapY) {
        double topBottom = topCandidate.getY() + topCandidate.getHeight();
        double bottomTop = bottomCandidate.getY();
        double contactGap = topBottom - bottomTop;
        boolean topIsActuallyAbove = topCandidate.getCenterY() < bottomCandidate.getCenterY();
        boolean topMovingDownIntoBottom = topCandidate.getVy() >= bottomCandidate.getVy() - 30;
        boolean shallowTopContact = contactGap > 0 && contactGap <= GameConfig.PLAYER_COLLISION_CONTACT_MARGIN;
        boolean mostlyVertical = overlapY <= Math.min(topCandidate.getHeight(), bottomCandidate.getHeight()) * 0.45;
        if (!topIsActuallyAbove || !topMovingDownIntoBottom || !(shallowTopContact || mostlyVertical)) {
            return false;
        }

        topCandidate.setY(bottomCandidate.getY() - topCandidate.getHeight() - 0.01);
        topCandidate.setVy(0);
        topCandidate.setGrounded(true);
        topCandidate.setCoyoteTimer(GameConfig.COYOTE_TIME_SECONDS);
        topCandidate.setVx(topCandidate.getVx() * (1.0 - GameConfig.PLAYER_COLLISION_CARRY_RATIO)
            + bottomCandidate.getVx() * GameConfig.PLAYER_COLLISION_CARRY_RATIO);

        if (bottomCandidate.getVy() < 0) {
            bottomCandidate.setVy(bottomCandidate.getVy() * 0.35);
        }
        return true;
    }

    private void resolveSidePlayerContact(Player a, Player b, double overlapX) {
        double push = overlapX + 0.01;
        double aMobility = a.isGrounded() ? 0.38 : 0.62;
        double bMobility = b.isGrounded() ? 0.38 : 0.62;
        double totalMobility = aMobility + bMobility;
        double aShare = totalMobility == 0 ? 0.5 : aMobility / totalMobility;
        double bShare = totalMobility == 0 ? 0.5 : bMobility / totalMobility;
        if (a.getCenterX() < b.getCenterX()) {
            a.setX(a.getX() - push * aShare);
            b.setX(b.getX() + push * bShare);
        } else {
            a.setX(a.getX() + push * aShare);
            b.setX(b.getX() - push * bShare);
        }
        a.setVx(a.getVx() * GameConfig.PLAYER_COLLISION_VELOCITY_DAMPING);
        b.setVx(b.getVx() * GameConfig.PLAYER_COLLISION_VELOCITY_DAMPING);
    }

    private void updateButtonAndDoor(List<Player> players) {
        ButtonSwitch button = sessionService.getButtonSwitch();
        Door door = sessionService.getDoor();
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
            eventBus.publish(EventNames.BUTTON_STATE_CHANGED, Map.of("pressed", pressed));
        }
    }

    private void updatePushBlocks(List<Player> players, double dt) {
        // Los bloques se simulan solo en el host y luego se replican por snapshot.
        for (PushBlock block : sessionService.getPushBlocks()) {
            block.setVy(block.getVy() + GameConfig.PUSH_BLOCK_GRAVITY * dt);

            double vx = block.getVx();
            if (Math.abs(vx) > 0.001) {
                double friction = GameConfig.PUSH_BLOCK_FRICTION * dt;
                if (Math.abs(vx) <= friction) {
                    vx = 0;
                } else {
                    vx -= Math.signum(vx) * friction;
                }
            }
            block.setVx(Math.max(-GameConfig.PUSH_BLOCK_MAX_SPEED, Math.min(GameConfig.PUSH_BLOCK_MAX_SPEED, vx)));

            block.setX(block.getX() + block.getVx() * dt);
            resolvePushBlockHorizontalCollisions(block, players);

            block.setY(block.getY() + block.getVy() * dt);
            resolvePushBlockVerticalCollisions(block);
            clampPushBlock(block);
        }
    }

    private void resolvePushBlockHorizontalCollisions(PushBlock block, List<Player> players) {
        for (PlatformTile platform : sessionService.getPlatforms()) {
            if (!GameRules.intersects(block, platform)) continue;
            if (block.getVx() > 0) {
                block.setX(platform.getX() - block.getWidth());
            } else if (block.getVx() < 0) {
                block.setX(platform.getX() + platform.getWidth());
            }
            block.setVx(0);
        }

        Door door = sessionService.getDoor();
        if (GameRules.intersects(block, door)) {
            if (block.getVx() > 0) {
                block.setX(door.getX() - block.getWidth());
            } else if (block.getVx() < 0) {
                block.setX(door.getX() + door.getWidth());
            }
            block.setVx(0);
        }

        for (Player player : players) {
            if (!player.isConnected() || !player.isAlive()) continue;
            if (!GameRules.intersects(player, block)) continue;
            double blockCenterX = block.getX() + block.getWidth() / 2.0;
            if (blockCenterX < player.getCenterX()) {
                player.setX(block.getX() + block.getWidth());
            } else {
                player.setX(block.getX() - player.getWidth());
            }
            player.setVx(player.getVx() * 0.5);
        }
    }

    private void resolvePushBlockVerticalCollisions(PushBlock block) {
        for (PlatformTile platform : sessionService.getPlatforms()) {
            if (!GameRules.intersects(block, platform)) continue;
            if (block.getVy() > 0) {
                block.setY(platform.getY() - block.getHeight());
            } else if (block.getVy() < 0) {
                block.setY(platform.getY() + platform.getHeight());
            }
            block.setVy(0);
        }

        Door door = sessionService.getDoor();
        if (GameRules.intersects(block, door)) {
            if (block.getVy() > 0) {
                block.setY(door.getY() - block.getHeight());
            } else if (block.getVy() < 0) {
                block.setY(door.getY() + door.getHeight());
            }
            block.setVy(0);
        }
    }

    private void clampPushBlock(PushBlock block) {
        block.setX(Math.max(0, Math.min(GameConfig.LEVEL_WIDTH - block.getWidth(), block.getX())));
        if (block.getY() < 0) {
            block.setY(0);
            block.setVy(0);
        }
    }

    private void updateExitState(List<Player> players) {
        ExitZone exitZone = sessionService.getExitZone();
        for (Player player : players) {
            if (!player.isConnected()) continue;
            boolean wasAtExit = player.isAtExit();
            boolean isAtExit = GameRules.isInsideExit(player, exitZone);
            player.setAtExit(isAtExit);
            if (!wasAtExit && isAtExit) {
                player.setFinishOrder(nextFinishOrder++);
                awardScore(player, scoreForFinishOrder(player.getFinishOrder()), "llego a la salida");
                eventBus.publish(EventNames.PLAYER_REACHED_EXIT, Map.of(
                    "playerId", player.getId(),
                    "finishOrder", player.getFinishOrder()
                ));
            }
        }
    }

    /**
     * Reinicia la sala actual completa para todos los jugadores conectados.
     *
     * @param reason texto corto para UI y debug
     */
    private void resetRoom(String reason) {
        sessionService.setRoomResetCount(sessionService.getRoomResetCount() + 1);
        sessionService.setRoomResetReason(reason);
        resetRoomState();
        eventBus.publish(EventNames.ROOM_RESET, Map.of("reason", reason));
    }

    /**
     * Avanza al siguiente nivel o finaliza la campaña si era el último.
     */
    private void advanceLevelOrFinish() {
        int nextLevel = sessionService.getCurrentLevelIndex() + 1;
        if (nextLevel >= sessionService.getTotalLevels()) {
            gameOver = true;
            sessionService.setGameRunning(false);
            eventBus.publish(EventNames.LEVEL_COMPLETED, Map.of(
                "elapsedTime", sessionService.getElapsedTime(),
                "levelIndex", sessionService.getCurrentLevelIndex()
            ));
            eventBus.publish(EventNames.GAME_OVER, Map.of("elapsedTime", sessionService.getElapsedTime()));
            return;
        }

        loadLevel(nextLevel, false);
        eventBus.publish(EventNames.LEVEL_ADVANCED, Map.of("levelIndex", nextLevel));
    }

    /**
     * Carga la geometría y objetos de una sala concreta.
     *
     * @param levelIndex índice del nivel a cargar
     * @param resetScores si {@code true}, también reinicia puntajes y caídas
     */
    private void loadLevel(int levelIndex, boolean resetScores) {
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

    private void updateCoins(List<Player> players) {
        for (CollectibleItem coin : sessionService.getCoins()) {
            if (!coin.isActive()) continue;
            for (Player player : players) {
                if (!player.isConnected() || !player.isAlive()) continue;
                if (GameRules.intersects(player.getX(), player.getY(), player.getWidth(), player.getHeight(),
                        coin.getX(), coin.getY(), GameConfig.COIN_SIZE, GameConfig.COIN_SIZE)) {
                    coin.setActive(false);
                    awardScore(player, coin.getPoints(), "recogió moneda");
                    eventBus.publish(EventNames.COIN_COLLECTED, Map.of(
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

    private void awardScore(Player player, int delta, String reason) {
        if (delta == 0) return;
        player.addScore(delta);
        eventBus.publish(EventNames.SCORE_CHANGED, Map.of(
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

    private static final class InputState {
        double targetX;
        double targetY;
        boolean hasTarget;
        boolean jumpQueued;
        double jumpBufferTimer;
    }
}
