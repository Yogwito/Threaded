package com.dino.application.services;

import com.dino.domain.entities.ButtonSwitch;
import com.dino.domain.entities.CollectibleItem;
import com.dino.domain.entities.Door;
import com.dino.domain.entities.ExitZone;
import com.dino.domain.entities.PlatformTile;
import com.dino.domain.entities.Player;
import com.dino.domain.entities.PushBlock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Fabrica de copias defensivas del estado de sesión.
 *
 * <p>La UI y el render no deben manipular directamente las colecciones mutables
 * que usa la simulación autoritativa. Este componente concentra la construcción
 * de vistas temporales para presentación y evita duplicar lógica de clonado en
 * {@link SessionService}.</p>
 */
public final class SessionViewFactory {
    /**
     * Crea copias defensivas de una colección de jugadores.
     *
     * @param players jugadores vivos del estado de sesión
     * @return lista independiente apta para UI y render
     */
    public List<Player> copyPlayers(Collection<Player> players) {
        List<Player> snapshot = new ArrayList<>();
        for (Player player : players) {
            Player copy = new Player();
            copy.setId(player.getId());
            copy.setName(player.getName());
            copy.setColor(player.getColor());
            copy.setX(player.getX());
            copy.setY(player.getY());
            copy.setVx(player.getVx());
            copy.setVy(player.getVy());
            copy.setCoyoteTimer(player.getCoyoteTimer());
            copy.setGrounded(player.isGrounded());
            copy.setAlive(player.isAlive());
            copy.setAtExit(player.isAtExit());
            copy.setTargetX(player.getTargetX());
            copy.setTargetY(player.getTargetY());
            copy.setScore(player.getScore());
            copy.setDeaths(player.getDeaths());
            copy.setFinishOrder(player.getFinishOrder());
            copy.setConnected(player.isConnected());
            copy.setReady(player.isReady());
            snapshot.add(copy);
        }
        return snapshot;
    }

    /**
     * Crea copias de plataformas rectangulares.
     *
     * @param platforms plataformas a copiar
     * @return nueva lista desligada del estado mutable
     */
    public List<PlatformTile> copyPlatformTiles(Collection<PlatformTile> platforms) {
        List<PlatformTile> snapshot = new ArrayList<>();
        for (PlatformTile platform : platforms) {
            snapshot.add(new PlatformTile(platform.getId(), platform.getX(), platform.getY(),
                platform.getWidth(), platform.getHeight()));
        }
        return snapshot;
    }

    /**
     * Copia puntos de aparición representados como arreglos `{x, y}`.
     *
     * @param spawnPoints puntos a copiar
     * @return lista nueva con arreglos independientes
     */
    public List<double[]> copyPoints(Collection<double[]> spawnPoints) {
        List<double[]> snapshot = new ArrayList<>();
        for (double[] spawn : spawnPoints) {
            snapshot.add(new double[]{spawn[0], spawn[1]});
        }
        return snapshot;
    }

    /**
     * Copia el botón del nivel si existe.
     *
     * @param buttonSwitch botón actual del estado
     * @return copia del botón o {@code null}
     */
    public ButtonSwitch copyButton(ButtonSwitch buttonSwitch) {
        if (buttonSwitch == null) return null;
        ButtonSwitch copy = new ButtonSwitch(buttonSwitch.getId(), buttonSwitch.getX(), buttonSwitch.getY(),
            buttonSwitch.getWidth(), buttonSwitch.getHeight());
        copy.setPressed(buttonSwitch.isPressed());
        return copy;
    }

    /**
     * Copia la puerta del nivel si existe.
     *
     * @param door puerta actual del estado
     * @return copia de la puerta o {@code null}
     */
    public Door copyDoor(Door door) {
        if (door == null) return null;
        Door copy = new Door(door.getId(), door.getX(), door.getY(), door.getWidth(), door.getHeight());
        copy.setOpen(door.isOpen());
        return copy;
    }

    /**
     * Copia la zona de salida del nivel si existe.
     *
     * @param exitZone salida actual del estado
     * @return copia de la salida o {@code null}
     */
    public ExitZone copyExitZone(ExitZone exitZone) {
        if (exitZone == null) return null;
        return new ExitZone(exitZone.getX(), exitZone.getY(), exitZone.getWidth(), exitZone.getHeight());
    }

    /**
     * Crea copias defensivas de bloques empujables.
     *
     * @param pushBlocks bloques actuales del estado
     * @return lista desligada del estado mutable
     */
    public List<PushBlock> copyPushBlocks(Collection<PushBlock> pushBlocks) {
        List<PushBlock> snapshot = new ArrayList<>();
        for (PushBlock block : pushBlocks) {
            PushBlock copy = new PushBlock(block.getId(), block.getX(), block.getY(), block.getWidth(), block.getHeight());
            copy.setVx(block.getVx());
            copy.setVy(block.getVy());
            copy.setHomeX(block.getHomeX());
            copy.setHomeY(block.getHomeY());
            snapshot.add(copy);
        }
        return snapshot;
    }

    /**
     * Crea copias defensivas de coleccionables.
     *
     * @param items ítems actuales del estado
     * @return lista nueva desligada del estado mutable
     */
    public List<CollectibleItem> copyCollectibles(Collection<CollectibleItem> items) {
        List<CollectibleItem> snapshot = new ArrayList<>();
        for (CollectibleItem item : items) {
            CollectibleItem copy = new CollectibleItem(item.getId(), item.getX(), item.getY(), item.getPoints());
            copy.setActive(item.isActive());
            snapshot.add(copy);
        }
        return snapshot;
    }
}
