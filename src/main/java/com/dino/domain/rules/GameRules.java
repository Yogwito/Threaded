package com.dino.domain.rules;

import com.dino.config.GameConfig;
import com.dino.domain.entities.ButtonSwitch;
import com.dino.domain.entities.Door;
import com.dino.domain.entities.ExitZone;
import com.dino.domain.entities.PlatformTile;
import com.dino.domain.entities.Player;
import com.dino.domain.entities.PushBlock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Reglas puras del dominio.
 *
 * <p>Concentra validaciones y cálculos sin efectos secundarios: colisiones,
 * activación de zonas, restricción del hilo y condiciones de salida. Esto evita
 * duplicar reglas entre la simulación del host y la presentación.</p>
 */
public final class GameRules {
    /** Clase utilitaria: no debe instanciarse. */
    private GameRules() {}

    /**
     * Evalúa intersección AABB entre dos rectángulos.
     *
     * @param ax coordenada X del primer rectángulo
     * @param ay coordenada Y del primer rectángulo
     * @param aw ancho del primer rectángulo
     * @param ah alto del primer rectángulo
     * @param bx coordenada X del segundo rectángulo
     * @param by coordenada Y del segundo rectángulo
     * @param bw ancho del segundo rectángulo
     * @param bh alto del segundo rectángulo
     * @return {@code true} si ambos rectángulos se superponen
     */
    public static boolean intersects(double ax, double ay, double aw, double ah,
                                     double bx, double by, double bw, double bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    /**
     * Determina si el jugador está colisionando con una plataforma.
     *
     * @param player jugador a evaluar
     * @param platform plataforma sólida candidata
     * @return {@code true} si ambos volúmenes se intersectan
     */
    public static boolean intersects(Player player, PlatformTile platform) {
        return intersects(player.getX(), player.getY(), player.getWidth(), player.getHeight(),
            platform.getX(), platform.getY(), platform.getWidth(), platform.getHeight());
    }

    /**
     * Determina si el jugador colisiona con una puerta cerrada.
     *
     * @param player jugador a evaluar
     * @param door puerta candidata
     * @return {@code true} si la puerta está cerrada y ambos volúmenes se cruzan
     */
    public static boolean intersects(Player player, Door door) {
        if (door == null || door.isOpen()) return false;
        return intersects(player.getX(), player.getY(), player.getWidth(), player.getHeight(),
            door.getX(), door.getY(), door.getWidth(), door.getHeight());
    }

    /**
     * Determina si dos jugadores se superponen.
     *
     * @param a primer jugador
     * @param b segundo jugador
     * @return {@code true} si sus AABB actuales se superponen
     */
    public static boolean intersects(Player a, Player b) {
        if (a == null || b == null || a == b) return false;
        return intersects(a.getX(), a.getY(), a.getWidth(), a.getHeight(),
            b.getX(), b.getY(), b.getWidth(), b.getHeight());
    }

    /**
     * Determina si un jugador empuja o colisiona con un bloque móvil.
     *
     * @param player jugador a evaluar
     * @param block bloque empujable candidato
     * @return {@code true} si el jugador intersecta el bloque
     */
    public static boolean intersects(Player player, PushBlock block) {
        if (player == null || block == null) return false;
        return intersects(player.getX(), player.getY(), player.getWidth(), player.getHeight(),
            block.getX(), block.getY(), block.getWidth(), block.getHeight());
    }

    /**
     * Determina si un rectángulo arbitrario intersecta cualquier sólido
     * relevante para el hilo.
     *
     * @param x coordenada X del rectángulo evaluado
     * @param y coordenada Y del rectángulo evaluado
     * @param width ancho del rectángulo evaluado
     * @param height alto del rectángulo evaluado
     * @param platforms plataformas sólidas del nivel
     * @param door puerta del nivel
     * @param pushBlocks bloques empujables presentes
     * @return {@code true} si el rectángulo toca algún sólido relevante
     */
    public static boolean intersectsAnySolid(double x, double y, double width, double height,
                                             Collection<PlatformTile> platforms,
                                             Door door,
                                             Collection<PushBlock> pushBlocks) {
        if (platforms != null) {
            for (PlatformTile platform : platforms) {
                if (platform != null && intersects(x, y, width, height,
                    platform.getX(), platform.getY(), platform.getWidth(), platform.getHeight())) {
                    return true;
                }
            }
        }

        if (door != null && !door.isOpen() && intersects(x, y, width, height,
            door.getX(), door.getY(), door.getWidth(), door.getHeight())) {
            return true;
        }

        if (pushBlocks != null) {
            for (PushBlock block : pushBlocks) {
                if (block != null && intersects(x, y, width, height,
                    block.getX(), block.getY(), block.getWidth(), block.getHeight())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Determina si un jugador intersecta cualquier sólido relevante para el hilo.
     *
     * @param player jugador a evaluar
     * @param platforms plataformas sólidas del nivel
     * @param door puerta del nivel
     * @param pushBlocks bloques empujables presentes
     * @return {@code true} si el jugador colisiona con algún sólido
     */
    public static boolean intersectsAnySolid(Player player,
                                             Collection<PlatformTile> platforms,
                                             Door door,
                                             Collection<PushBlock> pushBlocks) {
        if (player == null) return false;
        return intersectsAnySolid(player.getX(), player.getY(), player.getWidth(), player.getHeight(),
            platforms, door, pushBlocks);
    }

    /**
     * Determina si un bloque móvil está colisionando con una plataforma.
     *
     * @param block bloque empujable a evaluar
     * @param platform plataforma candidata
     * @return {@code true} si ambos volúmenes se superponen
     */
    public static boolean intersects(PushBlock block, PlatformTile platform) {
        if (block == null || platform == null) return false;
        return intersects(block.getX(), block.getY(), block.getWidth(), block.getHeight(),
            platform.getX(), platform.getY(), platform.getWidth(), platform.getHeight());
    }

    /**
     * Determina si un bloque móvil colisiona con una puerta cerrada.
     *
     * @param block bloque empujable a evaluar
     * @param door puerta candidata
     * @return {@code true} si la puerta está cerrada y ambos volúmenes se cruzan
     */
    public static boolean intersects(PushBlock block, Door door) {
        if (block == null || door == null || door.isOpen()) return false;
        return intersects(block.getX(), block.getY(), block.getWidth(), block.getHeight(),
            door.getX(), door.getY(), door.getWidth(), door.getHeight());
    }

    /**
     * Verifica si un jugador está presionando el botón del nivel.
     *
     * @param player jugador a evaluar
     * @param button botón del nivel
     * @return {@code true} si el jugador vivo ocupa el área del botón
     */
    public static boolean isPressingButton(Player player, ButtonSwitch button) {
        if (player == null || button == null || !player.isAlive()) return false;
        return intersects(player.getX(), player.getY(), player.getWidth(), player.getHeight(),
            button.getX(), button.getY(), button.getWidth(), button.getHeight());
    }

    /**
     * Verifica si un jugador se encuentra dentro de la salida.
     *
     * @param player jugador a evaluar
     * @param exitZone zona de salida del nivel
     * @return {@code true} si el jugador vivo está dentro de la meta
     */
    public static boolean isInsideExit(Player player, ExitZone exitZone) {
        if (player == null || exitZone == null || !player.isAlive()) return false;
        return intersects(player.getX(), player.getY(), player.getWidth(), player.getHeight(),
            exitZone.getX(), exitZone.getY(), exitZone.getWidth(), exitZone.getHeight());
    }

    /**
     * Confirma si todos los jugadores conectados ya están dentro de la meta.
     *
     * @param players jugadores a comprobar
     * @return {@code true} si existe al menos un jugador conectado y todos están en la salida
     */
    public static boolean allConnectedPlayersAtExit(Collection<Player> players) {
        boolean hasConnectedPlayers = false;
        for (Player player : players) {
            if (!player.isConnected()) continue;
            hasConnectedPlayers = true;
            if (!player.isAtExit()) return false;
        }
        return hasConnectedPlayers;
    }

    /**
     * Retorna los jugadores conectados y vivos preservando el orden recibido.
     *
     * <p>Cuando la colección fuente proviene de {@code SessionService.players},
     * el resultado corresponde al orden de unión porque el mapa subyacente es
     * un {@code LinkedHashMap}.</p>
     *
     * @param players colección fuente de jugadores
     * @return jugadores conectados y vivos en orden de hilo
     */
    public static List<Player> getConnectedPlayersInThreadOrder(Collection<Player> players) {
        List<Player> ordered = new ArrayList<>();
        for (Player player : players) {
            if (player == null || !player.isConnected() || !player.isAlive()) continue;
            ordered.add(player);
        }
        return ordered;
    }

    /**
     * Retorna los vecinos adyacentes de un jugador dentro de la cadena del hilo.
     *
     * @param player jugador cuyo vecindario se consulta
     * @param players conjunto total de jugadores conectados
     * @return vecinos inmediatos dentro del orden del hilo
     */
    public static List<Player> getThreadNeighbors(Player player, Collection<Player> players) {
        if (player == null || player.getId() == null) return List.of();

        List<Player> ordered = getConnectedPlayersInThreadOrder(players);
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (player.getId().equals(ordered.get(i).getId())) {
                index = i;
                break;
            }
        }
        if (index < 0) return List.of();

        List<Player> neighbors = new ArrayList<>(2);
        if (index > 0) neighbors.add(ordered.get(index - 1));
        if (index + 1 < ordered.size()) neighbors.add(ordered.get(index + 1));
        return neighbors;
    }

    /**
     * Indica si un jugador excede el límite duro del hilo respecto a sus vecinos
     * adyacentes en la cadena fija.
     *
     * @param movingPlayer jugador que se está desplazando
     * @param players colección total de jugadores relevantes
     * @return {@code true} si la distancia a un vecino supera el límite duro
     */
    public static boolean violatesAdjacentThreadHardLimit(Player movingPlayer, Collection<Player> players) {
        for (Player neighbor : getThreadNeighbors(movingPlayer, players)) {
            if (distance(movingPlayer, neighbor) > GameConfig.THREAD_HARD_LIMIT) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calcula la distancia entre los centros de dos jugadores.
     *
     * @param a primer jugador
     * @param b segundo jugador
     * @return distancia euclídea entre sus centros
     */
    public static double distance(Player a, Player b) {
        double dx = a.getCenterX() - b.getCenterX();
        double dy = a.getCenterY() - b.getCenterY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Busca una plataforma que esté sosteniendo actualmente al jugador.
     *
     * @param player jugador a evaluar
     * @param platforms plataformas del nivel
     * @return plataforma de soporte o {@code null} si no existe una coincidencia
     */
    public static PlatformTile findSupportingPlatform(Player player, List<PlatformTile> platforms) {
        for (PlatformTile platform : platforms) {
            boolean withinX = player.getX() + player.getWidth() > platform.getX()
                && player.getX() < platform.getX() + platform.getWidth();
            boolean onTop = Math.abs((player.getY() + player.getHeight()) - platform.getY()) < 2.5;
            if (withinX && onTop) return platform;
        }
        return null;
    }

    /**
     * Verifica si un segmento 2D intersecta un rectángulo axis-aligned.
     *
     * @param x1 coordenada X inicial del segmento
     * @param y1 coordenada Y inicial del segmento
     * @param x2 coordenada X final del segmento
     * @param y2 coordenada Y final del segmento
     * @param rx coordenada X del rectángulo
     * @param ry coordenada Y del rectángulo
     * @param rw ancho del rectángulo
     * @param rh alto del rectángulo
     * @return {@code true} si el segmento toca el rectángulo expandido
     */
    public static boolean segmentIntersectsAabb(double x1, double y1, double x2, double y2,
                                                double rx, double ry, double rw, double rh) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double tMin = 0.0;
        double tMax = 1.0;

        if (Math.abs(dx) < 1e-9) {
            if (x1 < rx || x1 > rx + rw) return false;
        } else {
            double invDx = 1.0 / dx;
            double tx1 = (rx - x1) * invDx;
            double tx2 = (rx + rw - x1) * invDx;
            double txMin = Math.min(tx1, tx2);
            double txMax = Math.max(tx1, tx2);
            tMin = Math.max(tMin, txMin);
            tMax = Math.min(tMax, txMax);
            if (tMin > tMax) return false;
        }

        if (Math.abs(dy) < 1e-9) {
            return y1 >= ry && y1 <= ry + rh;
        }

        double invDy = 1.0 / dy;
        double ty1 = (ry - y1) * invDy;
        double ty2 = (ry + rh - y1) * invDy;
        double tyMin = Math.min(ty1, ty2);
        double tyMax = Math.max(ty1, ty2);
        tMin = Math.max(tMin, tyMin);
        tMax = Math.min(tMax, tyMax);
        return tMin <= tMax;
    }

    /**
     * Determina si existe un sólido entre dos jugadores a lo largo del segmento
     * que une sus centros.
     *
     * @param a primer jugador
     * @param b segundo jugador
     * @param platforms plataformas sólidas del nivel
     * @param door puerta del nivel
     * @param pushBlocks bloques empujables presentes
     * @param margin expansión aplicada al AABB de cada sólido
     * @return {@code true} si el hilo entre ambos jugadores queda obstruido
     */
    public static boolean isThreadObstructed(Player a, Player b,
                                             Collection<PlatformTile> platforms,
                                             Door door,
                                             Collection<PushBlock> pushBlocks,
                                             double margin) {
        if (a == null || b == null) return false;

        double ax = a.getCenterX();
        double ay = a.getCenterY();
        double bx = b.getCenterX();
        double by = b.getCenterY();

        if (platforms != null) {
            for (PlatformTile platform : platforms) {
                if (platform != null && segmentIntersectsAabb(ax, ay, bx, by,
                    platform.getX() - margin, platform.getY() - margin,
                    platform.getWidth() + margin * 2.0, platform.getHeight() + margin * 2.0)) {
                    return true;
                }
            }
        }

        if (door != null && !door.isOpen() && segmentIntersectsAabb(ax, ay, bx, by,
            door.getX() - margin, door.getY() - margin,
            door.getWidth() + margin * 2.0, door.getHeight() + margin * 2.0)) {
            return true;
        }

        if (pushBlocks != null) {
            for (PushBlock block : pushBlocks) {
                if (block != null && segmentIntersectsAabb(ax, ay, bx, by,
                    block.getX() - margin, block.getY() - margin,
                    block.getWidth() + margin * 2.0, block.getHeight() + margin * 2.0)) {
                    return true;
                }
            }
        }

        return false;
    }
}
