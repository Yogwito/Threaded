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
 * <p>Encapsula movimiento, gravedad, colisiones, contactos entre jugadores,
 * apilamiento y actualización de push blocks. No decide score ni progreso de
 * nivel; solo resuelve la parte física del mundo.</p>
 */
public final class PlayerPhysicsService {
    private static final double COLLISION_SOUND_COOLDOWN = 0.12;
    private static final double PLAYER_STACK_HORIZONTAL_INSET = 4.0;
    private static final double PLAYER_STACK_FEET_TOLERANCE = 6.0;
    private static final double PLAYER_STACK_ASCENT_TOLERANCE = -10.0;

    private final SessionService sessionService;
    private final EventPublisher eventPublisher;
    private final ThreadConstraintService threadConstraintService;
    private final Map<String, InputState> playerInputs = new HashMap<>();

    private double collisionSoundCooldownRemaining = 0;

    public PlayerPhysicsService(SessionService sessionService,
                                EventPublisher eventPublisher,
                                ThreadConstraintService threadConstraintService) {
        this.sessionService = sessionService;
        this.eventPublisher = eventPublisher;
        this.threadConstraintService = threadConstraintService;
    }

    public void resetState() {
        playerInputs.clear();
        collisionSoundCooldownRemaining = 0;
    }

    public void tickCooldowns(double dt) {
        collisionSoundCooldownRemaining = Math.max(0, collisionSoundCooldownRemaining - dt);
    }

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

    public void handleJump(String playerId) {
        InputState state = playerInputs.computeIfAbsent(playerId, ignored -> new InputState());
        state.jumpQueued = true;
    }

    public void updatePlayers(List<Player> players, double dt) {
        for (Player player : players) {
            if (!player.isConnected()) continue;
            updatePlayer(player, dt);
        }
    }

    public void resolvePlayerInteractions(List<Player> players) {
        resolvePlayerCollisions(players);
        refreshPlayerStackingGroundState(players);
    }

    public void updatePushBlocks(List<Player> players, double dt) {
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
                    eventPublisher.publish(EventNames.PLAYER_COLLIDED, Map.of(
                        "playerA", a.getId(),
                        "playerB", b.getId()
                    ));
                }

                stabilizePlayer(a);
                stabilizePlayer(b);
            }
        }
    }

    private void refreshPlayerStackingGroundState(List<Player> players) {
        for (Player player : players) {
            if (player == null || !player.isAlive() || !player.isConnected()) {
                continue;
            }

            double playerBottom = player.getY() + player.getHeight();
            double playerLeft = player.getX();
            double playerRight = player.getX() + player.getWidth();

            for (Player other : players) {
                if (other == null || other == player || !other.isAlive() || !other.isConnected()) {
                    continue;
                }

                double otherTop = other.getY();
                double otherLeft = other.getX();
                double otherRight = other.getX() + other.getWidth();

                boolean horizontalOverlap = playerRight > otherLeft + PLAYER_STACK_HORIZONTAL_INSET
                    && playerLeft < otherRight - PLAYER_STACK_HORIZONTAL_INSET;
                boolean feetTouchingTop = Math.abs(playerBottom - otherTop) <= PLAYER_STACK_FEET_TOLERANCE;
                boolean descendingOrResting = player.getVy() >= PLAYER_STACK_ASCENT_TOLERANCE;
                boolean playerIsAbove = player.getCenterY() < other.getCenterY();

                if (!horizontalOverlap || !feetTouchingTop || !descendingOrResting || !playerIsAbove) {
                    continue;
                }

                player.setGrounded(true);
                player.setCoyoteTimer(GameConfig.COYOTE_TIME_SECONDS);
                if (player.getVy() > 0.0) {
                    player.setVy(0.0);
                }
                player.setY(otherTop - player.getHeight());
                break;
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

    private static final class InputState {
        double targetX;
        double targetY;
        boolean hasTarget;
        boolean jumpQueued;
        double jumpBufferTimer;
    }
}
