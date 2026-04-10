package com.dino.application.services;

import com.dino.config.GameConfig;
import com.dino.domain.entities.Door;
import com.dino.domain.entities.PlatformTile;
import com.dino.domain.entities.Player;
import com.dino.domain.entities.PushBlock;
import com.dino.domain.rules.GameRules;

import java.util.List;

/**
 * Simulación específica de bloques empujables.
 *
 * <p>Extrae de {@link PlayerPhysicsService} el movimiento, fricción, gravedad y
 * colisiones de {@link PushBlock}, manteniendo separada la física de entidades
 * especiales respecto al movimiento base del jugador.</p>
 */
public final class PushBlockPhysicsService {
    private final SessionWorldState worldState;

    /**
     * Crea el simulador de bloques del mundo actual.
     *
     * @param worldState estado mutable del mundo
     */
    public PushBlockPhysicsService(SessionWorldState worldState) {
        this.worldState = worldState;
    }

    /**
     * Actualiza movimiento, gravedad y colisiones de bloques empujables.
     *
     * @param players jugadores que pueden empujar o ser desplazados por bloques
     * @param dt delta temporal del frame en segundos
     */
    public void updatePushBlocks(List<Player> players, double dt) {
        for (PushBlock block : worldState.pushBlocks()) {
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
            if (Math.abs(vx) < 6.0) {
                vx = 0;
            }
            block.setVx(Math.max(-GameConfig.PUSH_BLOCK_MAX_SPEED, Math.min(GameConfig.PUSH_BLOCK_MAX_SPEED, vx)));

            block.setX(block.getX() + block.getVx() * dt);
            resolveHorizontalCollisions(block, players);

            block.setY(block.getY() + block.getVy() * dt);
            resolveVerticalCollisions(block);
            clamp(block);
        }
    }

    private void resolveHorizontalCollisions(PushBlock block, List<Player> players) {
        for (PlatformTile platform : worldState.platforms()) {
            if (!GameRules.intersects(block, platform)) continue;
            if (block.getVx() > 0) {
                block.setX(platform.getX() - block.getWidth());
            } else if (block.getVx() < 0) {
                block.setX(platform.getX() + platform.getWidth());
            }
            block.setVx(0);
        }

        Door door = worldState.door();
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

    private void resolveVerticalCollisions(PushBlock block) {
        for (PlatformTile platform : worldState.platforms()) {
            if (!GameRules.intersects(block, platform)) continue;
            if (block.getVy() > 0) {
                block.setY(platform.getY() - block.getHeight());
            } else if (block.getVy() < 0) {
                block.setY(platform.getY() + platform.getHeight());
            }
            block.setVy(0);
        }

        Door door = worldState.door();
        if (GameRules.intersects(block, door)) {
            if (block.getVy() > 0) {
                block.setY(door.getY() - block.getHeight());
            } else if (block.getVy() < 0) {
                block.setY(door.getY() + door.getHeight());
            }
            block.setVy(0);
        }
    }

    private void clamp(PushBlock block) {
        block.setX(Math.max(0, Math.min(GameConfig.LEVEL_WIDTH - block.getWidth(), block.getX())));
        if (block.getY() < 0) {
            block.setY(0);
            block.setVy(0);
        }
        if (Math.abs(block.getVy()) < 4.0) {
            block.setVy(0);
        }
    }
}
