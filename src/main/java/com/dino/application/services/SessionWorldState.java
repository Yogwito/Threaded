package com.dino.application.services;

import com.dino.domain.entities.ButtonSwitch;
import com.dino.domain.entities.CollectibleItem;
import com.dino.domain.entities.Door;
import com.dino.domain.entities.ExitZone;
import com.dino.domain.entities.PlatformTile;
import com.dino.domain.entities.Player;
import com.dino.domain.entities.PushBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estado mutable del mundo replicado por snapshots.
 *
 * <p>Agrupa entidades, geometría e interactivos del nivel. Esta extracción
 * permite que la simulación de host dependa del estado del mundo directamente
 * sin arrastrar toda la semántica de conexión y progreso de {@link SessionService}.</p>
 *
 * <p>Su contenido coincide con lo que el host serializa en snapshots
 * autoritativos: jugadores, plataformas, hazards, spawns, salida, bloques
 * empujables y coleccionables. No contiene reglas de negocio ni lógica de
 * transición entre pantallas.</p>
 */
final class SessionWorldState {
    private final Map<String, Player> players = new LinkedHashMap<>();
    private final List<PlatformTile> platforms = new ArrayList<>();
    private final List<PlatformTile> specialPlatforms = new ArrayList<>();
    private final List<PlatformTile> hazards = new ArrayList<>();
    private final List<PlatformTile> checkpoints = new ArrayList<>();
    private final List<double[]> spawnPoints = new ArrayList<>();
    private ButtonSwitch buttonSwitch;
    private Door door;
    private ExitZone exitZone;
    private final List<PushBlock> pushBlocks = new ArrayList<>();
    private final List<CollectibleItem> coins = new ArrayList<>();

    /**
     * Limpia todo el mundo, incluidos jugadores y nivel cargado.
     */
    void clearAll() {
        players.clear();
        clearLevel();
    }

    /**
     * Limpia únicamente el estado asociado al nivel cargado.
     */
    void clearLevel() {
        platforms.clear();
        specialPlatforms.clear();
        hazards.clear();
        checkpoints.clear();
        spawnPoints.clear();
        pushBlocks.clear();
        coins.clear();
        buttonSwitch = null;
        door = null;
        exitZone = null;
    }

    /**
     * Retorna el mapa mutable de jugadores indexados por id.
     */
    Map<String, Player> players() {
        return players;
    }

    /**
     * Retorna la lista mutable de plataformas sólidas normales.
     */
    List<PlatformTile> platforms() {
        return platforms;
    }

    /**
     * Retorna la lista mutable de plataformas especiales del nivel.
     */
    List<PlatformTile> specialPlatforms() {
        return specialPlatforms;
    }

    /**
     * Retorna la lista mutable de hazards del nivel.
     */
    List<PlatformTile> hazards() {
        return hazards;
    }

    /**
     * Retorna la lista mutable de checkpoints del nivel.
     */
    List<PlatformTile> checkpoints() {
        return checkpoints;
    }

    /**
     * Retorna los puntos de spawn disponibles para la sala actual.
     */
    List<double[]> spawnPoints() {
        return spawnPoints;
    }

    /**
     * Retorna el botón de nivel actual, si existe.
     */
    ButtonSwitch buttonSwitch() {
        return buttonSwitch;
    }

    /**
     * Actualiza el botón interactivo del nivel.
     *
     * @param buttonSwitch nuevo botón o {@code null}
     */
    void setButtonSwitch(ButtonSwitch buttonSwitch) {
        this.buttonSwitch = buttonSwitch;
    }

    /**
     * Retorna la puerta asociada al botón del nivel, si existe.
     */
    Door door() {
        return door;
    }

    /**
     * Actualiza la puerta interactiva del nivel.
     *
     * @param door nueva puerta o {@code null}
     */
    void setDoor(Door door) {
        this.door = door;
    }

    /**
     * Retorna la zona de salida activa del nivel.
     */
    ExitZone exitZone() {
        return exitZone;
    }

    /**
     * Actualiza la zona de salida del nivel.
     *
     * @param exitZone nueva meta o {@code null}
     */
    void setExitZone(ExitZone exitZone) {
        this.exitZone = exitZone;
    }

    /**
     * Retorna la lista mutable de bloques empujables del nivel.
     */
    List<PushBlock> pushBlocks() {
        return pushBlocks;
    }

    /**
     * Retorna la lista mutable de monedas o coleccionables activos.
     */
    List<CollectibleItem> coins() {
        return coins;
    }
}
