package com.dino.application.services;

import com.dino.config.GameConfig;
import com.dino.domain.entities.Door;
import com.dino.domain.entities.PlatformTile;
import com.dino.domain.entities.Player;
import com.dino.domain.entities.PushBlock;
import com.dino.domain.events.EventNames;
import com.dino.domain.rules.GameRules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simulación de física base de jugadores y bloques empujables en el host.
 *
 * <p>Encapsula movimiento individual, gravedad y colisiones base de jugadores.
 * Las interacciones entre jugadores y la física de bloques se delegan en
 * servicios especializados para reducir el tamaño del monolito original. No
 * decide score ni progreso de nivel; solo resuelve la parte física del
 * mundo.</p>
 */
public final class PlayerPhysicsService {
    private static final double PUSH_BLOCK_SOUND_COOLDOWN = 0.18;
    private final SessionWorldState worldState;
    private final EventPublisher eventPublisher;
    private final ThreadConstraintService threadConstraintService;
    private final PlayerContactService playerContactService;
    private final PushBlockPhysicsService pushBlockPhysicsService;
    private final Map<String, InputState> playerInputs = new HashMap<>();
    private double pushBlockSoundCooldownRemaining = 0;

    /**
     * Crea el simulador de física base de la sesión.
     *
     * @param worldState estado del mundo sobre el que se simula
     * @param eventPublisher publicador de eventos internos
     * @param threadConstraintService servicio encargado del hilo entre jugadores
     */
    public PlayerPhysicsService(SessionWorldState worldState,
                                EventPublisher eventPublisher,
                                ThreadConstraintService threadConstraintService) {
        this.worldState = worldState;
        this.eventPublisher = eventPublisher;
        this.threadConstraintService = threadConstraintService;
        this.playerContactService = new PlayerContactService(eventPublisher);
        this.pushBlockPhysicsService = new PushBlockPhysicsService(worldState);
    }

    /**
     * Reinicia buffers de input y cooldowns internos.
     */
    public void resetState() {
        playerInputs.clear();
        playerContactService.resetState();
        pushBlockSoundCooldownRemaining = 0;
    }

    /**
     * Avanza los cooldowns internos de sonidos y contactos.
     *
     * @param dt delta temporal en segundos
     */
    public void tickCooldowns(double dt) {
        playerContactService.tickCooldowns(dt);
        pushBlockSoundCooldownRemaining = Math.max(0, pushBlockSoundCooldownRemaining - dt);
    }

    /**
     * Registra el objetivo de movimiento más reciente de un jugador.
     *
     * @param playerId jugador al que pertenece el input
     * @param targetX objetivo X en mundo
     * @param targetY objetivo Y en mundo
     */
    public void handleMoveTarget(String playerId, double targetX, double targetY) {
        InputState state = playerInputs.computeIfAbsent(playerId, ignored -> new InputState());
        state.targetX = targetX;
        state.targetY = targetY;
        state.hasTarget = true;
        Player player = worldState.players().get(playerId);
        if (player != null) {
            player.setTargetX(targetX);
            player.setTargetY(targetY);
        }
    }

    /**
     * Encola un salto para el siguiente paso de simulación del jugador.
     *
     * @param playerId jugador que solicitó el salto
     */
    public void handleJump(String playerId) {
        InputState state = playerInputs.computeIfAbsent(playerId, ignored -> new InputState());
        state.jumpQueued = true;
    }

    /**
     * Re-sincroniza los buffers de input con el objetivo actual almacenado en
     * cada jugador.
     *
     * <p>Se usa después de respawns, resets de sala o cambios de nivel para
     * evitar que un objetivo de mouse antiguo siga arrastrando al jugador desde
     * el primer tick del nuevo estado visible.</p>
     *
     * @param players jugadores cuya intención local debe alinearse con su
     *                objetivo actual en el mundo
     */
    public void syncInputsToPlayers(List<Player> players) {
        for (Player player : players) {
            InputState state = playerInputs.computeIfAbsent(player.getId(), ignored -> new InputState());
            state.targetX = player.getTargetX();
            state.targetY = player.getTargetY();
            state.hasTarget = true;
            state.jumpQueued = false;
            state.jumpBufferTimer = 0;
        }
    }

    /**
     * Actualiza movimiento, gravedad y colisiones base de todos los jugadores.
     *
     * @param players jugadores a simular
     * @param dt delta temporal del frame en segundos
     */
    public void updatePlayers(List<Player> players, double dt) {
        for (Player player : players) {
            if (!player.isConnected()) continue;
            updatePlayer(player, dt);
        }
    }

    /**
     * Resuelve contactos entre jugadores y recalcula apilamiento/grounded.
     *
     * @param players jugadores conectados del frame actual
     */
    public void resolvePlayerInteractions(List<Player> players) {
        playerContactService.resolvePlayerInteractions(players, this::stabilizePlayer);
    }

    /**
     * Actualiza movimiento, gravedad y colisiones de bloques empujables.
     *
     * @param players jugadores que pueden empujar o ser desplazados por bloques
     * @param dt delta temporal del frame en segundos
     */
    public void updatePushBlocks(List<Player> players, double dt) {
        pushBlockPhysicsService.updatePushBlocks(players, dt);
    }

    /**
     * Revalida límites y colisiones de un jugador después de una corrección.
     *
     * @param player jugador a estabilizar
     */
    public void stabilizePlayer(Player player) {
        clampPlayer(player);
        resolveHorizontalCollisions(player);
        resolveVerticalCollisions(player);
    }

    private void updatePlayer(Player player, double dt) {
        InputState input = playerInputs.computeIfAbsent(player.getId(), ignored -> new InputState());
        double previousX = player.getX();
        double previousY = player.getY();
        double moveDirection = 0;
        if (input.hasTarget) {
            double dx = input.targetX - player.getCenterX();
            if (Math.abs(dx) > GameConfig.TARGET_REACHED_TOLERANCE) {
                moveDirection = Math.signum(dx);
            }
            player.setTargetX(input.targetX);
            player.setTargetY(input.targetY);
        }

        double targetVelocity = moveDirection * GameConfig.MOVE_SPEED;
        double velocityDelta = targetVelocity - player.getVx();
        double acceleration = moveDirection == 0
            ? GameConfig.MOVE_FRICTION
            : (player.isGrounded() ? GameConfig.MOVE_ACCELERATION : GameConfig.AIR_MOVE_ACCELERATION);
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
            eventPublisher.publish(EventNames.PLAYER_JUMPED, Map.of("playerId", player.getId()));
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
            eventPublisher.publish(EventNames.PLAYER_JUMPED, Map.of("playerId", player.getId()));
        }

        clampPlayer(player);
        if (threadConstraintService.violatesAdjacentHardLimit(player)) {
            player.setX(previousX);
            player.setY(previousY);
            threadConstraintService.cancelSeparatingVelocityAgainstThreadNeighbors(player);
            player.setVx(player.getVx() * 0.85);
            player.setVy(player.getVy() * 0.6);
            stabilizePlayer(player);
        }
    }

    private void resolveHorizontalCollisions(Player player) {
        for (PlatformTile platform : worldState.platforms()) {
            if (!GameRules.intersects(player, platform)) continue;
            if (player.getVx() > 0) {
                player.setX(platform.getX() - player.getWidth());
            } else if (player.getVx() < 0) {
                player.setX(platform.getX() + platform.getWidth());
            }
            player.setVx(0);
        }

        Door door = worldState.door();
        if (GameRules.intersects(player, door)) {
            if (player.getVx() > 0) {
                player.setX(door.getX() - player.getWidth());
            } else if (player.getVx() < 0) {
                player.setX(door.getX() + door.getWidth());
            }
            player.setVx(0);
        }

        for (PushBlock block : worldState.pushBlocks()) {
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
            publishPushBlockFeedback(player, block);
        }
    }

    private void resolveVerticalCollisions(Player player) {
        player.setGrounded(false);
        for (PlatformTile platform : worldState.platforms()) {
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

        Door door = worldState.door();
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

        for (PushBlock block : worldState.pushBlocks()) {
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

    private void publishPushBlockFeedback(Player player, PushBlock block) {
        if (pushBlockSoundCooldownRemaining > 0 || Math.abs(block.getVx()) < 18) {
            return;
        }
        pushBlockSoundCooldownRemaining = PUSH_BLOCK_SOUND_COOLDOWN;
        eventPublisher.publish(EventNames.PUSH_BLOCK_MOVED, Map.of(
            "playerId", player.getId(),
            "blockId", block.getId(),
            "speed", block.getVx()
        ));
    }

    private static final class InputState {
        double targetX;
        double targetY;
        boolean hasTarget;
        boolean jumpQueued;
        double jumpBufferTimer;
    }
}
