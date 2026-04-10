package com.dino.application.services;

import com.dino.config.GameConfig;
import com.dino.domain.entities.Player;
import com.dino.domain.events.EventNames;
import com.dino.domain.rules.GameRules;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Resuelve contactos directos entre jugadores.
 *
 * <p>Separa de {@link PlayerPhysicsService} la lógica de colisión jugador contra
 * jugador, apilamiento y enfriamiento del sonido de impacto. De esta forma la
 * física base puede concentrarse en movimiento individual y colisiones contra
 * mundo.</p>
 */
public final class PlayerContactService {
    private static final double COLLISION_SOUND_COOLDOWN = 0.12;
    private static final double PLAYER_STACK_HORIZONTAL_INSET = 4.0;
    private static final double PLAYER_STACK_FEET_TOLERANCE = 6.0;
    private static final double PLAYER_STACK_ASCENT_TOLERANCE = -10.0;

    private final EventPublisher eventPublisher;
    private double collisionSoundCooldownRemaining = 0;

    /**
     * Crea el resolvedor de contactos entre jugadores.
     *
     * @param eventPublisher publicador de eventos internos
     */
    public PlayerContactService(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Reinicia el estado temporal del resolvedor.
     */
    public void resetState() {
        collisionSoundCooldownRemaining = 0;
    }

    /**
     * Avanza cooldowns internos del resolvedor.
     *
     * @param dt delta temporal en segundos
     */
    public void tickCooldowns(double dt) {
        collisionSoundCooldownRemaining = Math.max(0, collisionSoundCooldownRemaining - dt);
    }

    /**
     * Resuelve colisiones, apilamiento y grounded derivado entre jugadores.
     *
     * @param players jugadores conectados del frame actual
     * @param stabilizer callback para reestabilizar jugadores tras empujes
     */
    public void resolvePlayerInteractions(List<Player> players, Consumer<Player> stabilizer) {
        resolvePlayerCollisions(players, stabilizer);
        refreshPlayerStackingGroundState(players);
    }

    private void resolvePlayerCollisions(List<Player> players, Consumer<Player> stabilizer) {
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

                stabilizer.accept(a);
                stabilizer.accept(b);
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
}
