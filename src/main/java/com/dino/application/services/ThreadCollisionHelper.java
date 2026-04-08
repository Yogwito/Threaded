package com.dino.application.services;

import com.dino.config.GameConfig;
import com.dino.domain.entities.Door;
import com.dino.domain.entities.PlatformTile;
import com.dino.domain.entities.Player;
import com.dino.domain.entities.PushBlock;
import com.dino.domain.rules.GameRules;

import java.util.Collection;

/**
 * Helper acotado para aplicar correcciones posicionales del hilo sin atravesar
 * sólidos.
 */
public final class ThreadCollisionHelper {
    private ThreadCollisionHelper() {}

    /**
     * Intenta mover al jugador por componente usando pasos cortos; si una
     * componente colisiona, la recorta cancelando el resto de ese eje.
     */
    public static void applyValidatedDelta(Player player,
                                           double dx,
                                           double dy,
                                           Collection<PlatformTile> platforms,
                                           Door door,
                                           Collection<PushBlock> pushBlocks) {
        if (player == null) return;
        moveAxis(player, dx, true, platforms, door, pushBlocks);
        moveAxis(player, dy, false, platforms, door, pushBlocks);
    }

    private static void moveAxis(Player player,
                                 double delta,
                                 boolean horizontal,
                                 Collection<PlatformTile> platforms,
                                 Door door,
                                 Collection<PushBlock> pushBlocks) {
        if (Math.abs(delta) < 1e-9) return;

        double remaining = delta;
        double direction = Math.signum(delta);
        while (Math.abs(remaining) > 1e-9) {
            double step = direction * Math.min(Math.abs(remaining), GameConfig.THREAD_POSITION_STEP);
            double targetX = horizontal ? clampX(player.getX() + step, player.getWidth()) : player.getX();
            double targetY = horizontal ? player.getY() : clampY(player.getY() + step);

            if (GameRules.intersectsAnySolid(targetX, targetY, player.getWidth(), player.getHeight(),
                platforms, door, pushBlocks)) {
                break;
            }

            if (horizontal) {
                player.setX(targetX);
            } else {
                player.setY(targetY);
            }
            remaining -= step;
        }
    }

    private static double clampX(double x, double width) {
        return Math.max(0, Math.min(GameConfig.LEVEL_WIDTH - width, x));
    }

    private static double clampY(double y) {
        return Math.max(0, y);
    }
}
