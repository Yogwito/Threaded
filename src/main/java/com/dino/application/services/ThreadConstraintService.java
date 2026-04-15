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
    private final SessionWorldState worldState;
    private final EventPublisher eventPublisher;
    private double threadSoundCooldownRemaining = 0;

    /**
     * Crea el servicio de restricción del hilo para la sesión actual.
     *
     * @param worldState estado del mundo del que se leen jugadores y sólidos
     * @param eventPublisher publicador de eventos internos
     */
    public ThreadConstraintService(SessionWorldState worldState, EventPublisher eventPublisher) {
        this.worldState = worldState;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Reinicia cooldowns y estado transitorio del servicio.
     */
    public void resetState() {
        threadSoundCooldownRemaining = 0;
    }

    /**
     * Avanza el cooldown del sonido asociado al estiramiento del hilo.
     *
     * @param dt delta temporal en segundos
     */
    public void tickCooldowns(double dt) {
        threadSoundCooldownRemaining = Math.max(0.0, threadSoundCooldownRemaining - dt);
    }

    /**
     * Verifica si un jugador viola el límite duro respecto a sus vecinos.
     *
     * @param player jugador movido o evaluado
     * @return {@code true} si excede el límite duro del hilo
     */
    public boolean violatesAdjacentHardLimit(Player player) {
        return GameRules.violatesAdjacentThreadHardLimit(player, worldState.players().values());
    }

    /**
     * Cancela la componente de velocidad que sigue separando al jugador del hilo.
     *
     * @param player jugador sobre el que se corrige la velocidad
     */
    public void cancelSeparatingVelocityAgainstThreadNeighbors(Player player) {
        for (Player neighbor : GameRules.getThreadNeighbors(player, worldState.players().values())) {
            double dx = neighbor.getCenterX() - player.getCenterX();
            double dy = neighbor.getCenterY() - player.getCenterY();
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance < 1.0 || distance <= GameConfig.THREAD_HARD_LIMIT) continue;

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

            // Guard: distancia demasiado pequeña para calcular dirección confiable
            if (distance < 1.0) continue;

            // Normalizar solo cuando la magnitud lo garantiza (distance >= 1.0 asegurado arriba)
            double nx = dx / distance;
            double ny = dy / distance;

            // Caso de solapamiento casi total: separación suave fija en lugar de corrección proporcional
            if (distance < GameConfig.PLAYER_WIDTH * 0.5) {
                double aMobility = threadMobility(a);
                double bMobility = threadMobility(b);
                double totalMobility = aMobility + bMobility;
                double aShare = totalMobility == 0 ? 0.5 : aMobility / totalMobility;
                double bShare = totalMobility == 0 ? 0.5 : bMobility / totalMobility;
                // Impulso separador: -nx/-ny aleja a ambos jugadores entre sí
                applyThreadVelocityImpulse(a, b, -nx, -ny, 1.5, aShare, bShare);
                stabilizer.accept(a);
                stabilizer.accept(b);
                continue;
            }

            // Tensión solo activa cuando la distancia supera la zona de reposo
            if (distance <= GameConfig.THREAD_REST_DISTANCE) continue;

            double stretchFromRest = distance - GameConfig.THREAD_REST_DISTANCE;
            double softStretch = Math.max(0, Math.min(distance, GameConfig.THREAD_MAX_DISTANCE) - GameConfig.THREAD_REST_DISTANCE);
            double hardStretch = Math.max(0, Math.min(distance, GameConfig.THREAD_HARD_LIMIT) - GameConfig.THREAD_MAX_DISTANCE);
            double relativeVelocity = (b.getVx() - a.getVx()) * nx + (b.getVy() - a.getVy()) * ny;
            double separatingSpeed = Math.max(0, relativeVelocity);
            double closingSpeed = Math.max(0, -relativeVelocity);
            boolean obstructed = GameRules.isThreadObstructed(a, b,
                worldState.platforms(), worldState.door(), worldState.pushBlocks(),
                GameConfig.THREAD_OBSTRUCTION_MARGIN);

            double aMobility = threadMobility(a);
            double bMobility = threadMobility(b);
            double totalMobility = aMobility + bMobility;
            double aShare = totalMobility == 0 ? 0.5 : aMobility / totalMobility;
            double bShare = totalMobility == 0 ? 0.5 : bMobility / totalMobility;

            if (stretchFromRest > GameConfig.THREAD_STRETCH_SOUND_THRESHOLD && threadSoundCooldownRemaining <= 0) {
                threadSoundCooldownRemaining = GameConfig.THREAD_SOUND_COOLDOWN_SECONDS;
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

    /**
     * Estima cuánta corrección del hilo puede absorber un jugador.
     *
     * @param player jugador evaluado
     * @return factor relativo de movilidad para repartir impulsos
     */
    private double threadMobility(Player player) {
        return player.isGrounded() ? GameConfig.THREAD_MOBILITY_GROUNDED : GameConfig.THREAD_MOBILITY_AIR;
    }

    /**
     * Ajusta cuánto del impulso vertical del hilo recibe un jugador.
     *
     * @param player jugador evaluado
     * @return factor vertical según si está en suelo o aire
     */
    private double threadVerticalFactor(Player player) {
        return player.isGrounded() ? GameConfig.THREAD_GROUNDED_VERTICAL_FACTOR : GameConfig.THREAD_AIR_VERTICAL_FACTOR;
    }

    /**
     * Aplica un impulso de velocidad opuesto a dos vecinos tensados por el hilo.
     *
     * @param a jugador del extremo A
     * @param b jugador del extremo B
     * @param nx componente X normalizada entre ambos
     * @param ny componente Y normalizada entre ambos
     * @param impulse magnitud total del impulso a repartir
     * @param aShare proporción asignada al jugador A
     * @param bShare proporción asignada al jugador B
     */
    private void applyThreadVelocityImpulse(Player a, Player b, double nx, double ny,
                                            double impulse, double aShare, double bShare) {
        double aImpulse = impulse * aShare;
        double bImpulse = impulse * bShare;

        a.setVx(a.getVx() + nx * aImpulse);
        b.setVx(b.getVx() - nx * bImpulse);
        a.setVy(a.getVy() + ny * aImpulse * threadVerticalFactor(a));
        b.setVy(b.getVy() - ny * bImpulse * threadVerticalFactor(b));
    }

    /**
     * Intenta corregir la separación de dos jugadores moviéndolos en posición.
     *
     * <p>La corrección pasa por {@link ThreadCollisionHelper} para no atravesar
     * geometría sólida mientras el hilo reacomoda a ambos extremos.</p>
     *
     * @param a jugador del extremo A de la pareja tensada
     * @param b jugador del extremo B de la pareja tensada
     * @param nx componente X del vector unitario de A hacia B
     * @param ny componente Y del vector unitario de A hacia B
     * @param correction distancia total de corrección posicional a repartir
     * @param aShare fracción de la corrección asignada al jugador A
     * @param bShare fracción de la corrección asignada al jugador B
     */
    private void applyThreadPositionCorrection(Player a, Player b, double nx, double ny,
                                               double correction, double aShare, double bShare) {
        ThreadCollisionHelper.applyValidatedDelta(
            a,
            nx * correction * aShare,
            ny * correction * aShare * threadVerticalFactor(a),
            worldState.platforms(),
            worldState.door(),
            worldState.pushBlocks()
        );
        ThreadCollisionHelper.applyValidatedDelta(
            b,
            -nx * correction * bShare,
            -ny * correction * bShare * threadVerticalFactor(b),
            worldState.platforms(),
            worldState.door(),
            worldState.pushBlocks()
        );
    }
}
