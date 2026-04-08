package com.dino.application.services;

import com.dino.config.GameConfig;
import com.dino.domain.entities.Player;
import com.dino.domain.events.EventNames;
import com.dino.domain.rules.GameRules;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Encapsula la simulación del hilo entre jugadores conectados.
 *
 * <p>Opera sobre la cadena fija por orden de unión, aplica tensión suave o
 * dura según la distancia, y evita correcciones posicionales a través de
 * sólidos cuando existe obstrucción geométrica.</p>
 */
public final class ThreadConstraintService {
    private static final double THREAD_SOUND_COOLDOWN = 0.16;

    private final SessionService sessionService;
    private final EventPublisher eventPublisher;
    private double threadSoundCooldownRemaining = 0;

    public ThreadConstraintService(SessionService sessionService, EventPublisher eventPublisher) {
        this.sessionService = sessionService;
        this.eventPublisher = eventPublisher;
    }

    public void resetState() {
        threadSoundCooldownRemaining = 0;
    }

    public void tickCooldowns(double dt) {
        threadSoundCooldownRemaining = Math.max(0, threadSoundCooldownRemaining - dt);
    }

    public boolean violatesAdjacentHardLimit(Player player) {
        return GameRules.violatesAdjacentThreadHardLimit(player, sessionService.getPlayers().values());
    }

    public void cancelSeparatingVelocityAgainstThreadNeighbors(Player player) {
        for (Player neighbor : GameRules.getThreadNeighbors(player, sessionService.getPlayers().values())) {
            double dx = neighbor.getCenterX() - player.getCenterX();
            double dy = neighbor.getCenterY() - player.getCenterY();
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance == 0 || distance <= GameConfig.THREAD_HARD_LIMIT) continue;

            double nx = dx / distance;
            double ny = dy / distance;
            double awaySpeed = -(player.getVx() * nx + player.getVy() * ny);
            if (awaySpeed <= 0) continue;

            player.setVx(player.getVx() + nx * awaySpeed);
            player.setVy(player.getVy() + ny * awaySpeed);
        }
    }

    /**
     * Aplica la restricción del hilo entre vecinos adyacentes de la cadena.
     *
     * @param players jugadores de la sesión actual
     * @param dt delta del tick
     * @param stabilizer callback que revalida colisiones después de aplicar
     *                   correcciones del hilo
     */
    public void applyThreadElasticity(List<Player> players, double dt, Consumer<Player> stabilizer) {
        List<Player> connected = GameRules.getConnectedPlayersInThreadOrder(players);
        double tickScale = Math.max(0.6, Math.min(1.4, dt * GameConfig.FPS));
        double correctionBudget = GameConfig.THREAD_MAX_POSITION_CORRECTION_PER_TICK * tickScale;

        for (int i = 0; i < connected.size() - 1; i++) {
            Player a = connected.get(i);
            Player b = connected.get(i + 1);
            double dx = b.getCenterX() - a.getCenterX();
            double dy = b.getCenterY() - a.getCenterY();
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance == 0 || distance <= GameConfig.THREAD_REST_DISTANCE) continue;

            double nx = dx / distance;
            double ny = dy / distance;
            double stretchFromRest = distance - GameConfig.THREAD_REST_DISTANCE;
            double softStretch = Math.max(0, Math.min(distance, GameConfig.THREAD_MAX_DISTANCE) - GameConfig.THREAD_REST_DISTANCE);
            double hardStretch = Math.max(0, Math.min(distance, GameConfig.THREAD_HARD_LIMIT) - GameConfig.THREAD_MAX_DISTANCE);
            double relativeVelocity = (b.getVx() - a.getVx()) * nx + (b.getVy() - a.getVy()) * ny;
            double separatingSpeed = Math.max(0, relativeVelocity);
            double closingSpeed = Math.max(0, -relativeVelocity);
            boolean obstructed = GameRules.isThreadObstructed(a, b,
                sessionService.getPlatforms(), sessionService.getDoor(), sessionService.getPushBlocks(),
                GameConfig.THREAD_OBSTRUCTION_MARGIN);

            double aMobility = threadMobility(a);
            double bMobility = threadMobility(b);
            double totalMobility = aMobility + bMobility;
            double aShare = totalMobility == 0 ? 0.5 : aMobility / totalMobility;
            double bShare = totalMobility == 0 ? 0.5 : bMobility / totalMobility;

            if (stretchFromRest > 8 && threadSoundCooldownRemaining <= 0) {
                threadSoundCooldownRemaining = THREAD_SOUND_COOLDOWN;
                eventPublisher.publish(EventNames.THREAD_STRETCHED, Map.of(
                    "playerA", a.getId(),
                    "playerB", b.getId(),
                    "stretch", stretchFromRest
                ));
            }

            double springImpulse = 0;
            double dampingImpulse = separatingSpeed * Math.min(1.0,
                (distance > GameConfig.THREAD_MAX_DISTANCE ? GameConfig.THREAD_HARD_DAMPING : GameConfig.THREAD_DAMPING) * dt);
            double cancelImpulse = 0;
            double positionCorrection = 0;

            if (!obstructed) {
                springImpulse += softStretch * GameConfig.THREAD_PULL_FACTOR * dt;
                springImpulse += hardStretch * GameConfig.THREAD_HARD_PULL_FACTOR * dt;
                springImpulse = Math.max(0, springImpulse - closingSpeed * GameConfig.THREAD_DAMPING * dt * 0.6);

                if (distance > GameConfig.THREAD_MAX_DISTANCE) {
                    cancelImpulse = Math.max(cancelImpulse, separatingSpeed * 0.25);
                    positionCorrection = Math.min(
                        correctionBudget * 0.18 + (distance - GameConfig.THREAD_MAX_DISTANCE) * 0.35,
                        correctionBudget * 0.72
                    );
                } else {
                    positionCorrection = Math.min(softStretch * 0.08, correctionBudget * 0.18);
                }

                if (distance > GameConfig.THREAD_HARD_LIMIT) {
                    cancelImpulse = Math.max(cancelImpulse,
                        separatingSpeed * GameConfig.THREAD_SEPARATION_CANCEL_FACTOR);
                    positionCorrection = Math.min(
                        correctionBudget * 0.45 + (distance - GameConfig.THREAD_HARD_LIMIT),
                        correctionBudget
                    );
                }

                if (closingSpeed > 0) {
                    positionCorrection = Math.max(0, positionCorrection - closingSpeed * dt * 0.2);
                }
            } else {
                dampingImpulse = 0;
                cancelImpulse = separatingSpeed * (distance > GameConfig.THREAD_HARD_LIMIT
                    ? GameConfig.THREAD_SEPARATION_CANCEL_FACTOR
                    : 0.55);
            }

            double velocityImpulse = springImpulse + dampingImpulse + cancelImpulse;
            if (velocityImpulse > 0) {
                applyThreadVelocityImpulse(a, b, nx, ny, velocityImpulse, aShare, bShare);
            }

            if (!obstructed && positionCorrection > 0) {
                applyThreadPositionCorrection(a, b, nx, ny, positionCorrection, aShare, bShare);
            }

            stabilizer.accept(a);
            stabilizer.accept(b);
        }
    }

    private double threadMobility(Player player) {
        return player.isGrounded() ? 0.35 : 0.65;
    }

    private double threadVerticalFactor(Player player) {
        return player.isGrounded() ? GameConfig.THREAD_GROUNDED_VERTICAL_FACTOR : GameConfig.THREAD_AIR_VERTICAL_FACTOR;
    }

    private void applyThreadVelocityImpulse(Player a, Player b, double nx, double ny,
                                            double impulse, double aShare, double bShare) {
        double aImpulse = impulse * aShare;
        double bImpulse = impulse * bShare;

        a.setVx(a.getVx() + nx * aImpulse);
        b.setVx(b.getVx() - nx * bImpulse);
        a.setVy(a.getVy() + ny * aImpulse * threadVerticalFactor(a));
        b.setVy(b.getVy() - ny * bImpulse * threadVerticalFactor(b));
    }

    private void applyThreadPositionCorrection(Player a, Player b, double nx, double ny,
                                               double correction, double aShare, double bShare) {
        ThreadCollisionHelper.applyValidatedDelta(
            a,
            nx * correction * aShare,
            ny * correction * aShare * threadVerticalFactor(a),
            sessionService.getPlatforms(),
            sessionService.getDoor(),
            sessionService.getPushBlocks()
        );
        ThreadCollisionHelper.applyValidatedDelta(
            b,
            -nx * correction * bShare,
            -ny * correction * bShare * threadVerticalFactor(b),
            sessionService.getPlatforms(),
            sessionService.getDoor(),
            sessionService.getPushBlocks()
        );
    }
}
