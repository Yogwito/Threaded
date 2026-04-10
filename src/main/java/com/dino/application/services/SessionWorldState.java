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

    Map<String, Player> players() {
        return players;
    }

    List<PlatformTile> platforms() {
        return platforms;
    }

    List<PlatformTile> specialPlatforms() {
        return specialPlatforms;
    }

    List<PlatformTile> hazards() {
        return hazards;
    }

    List<PlatformTile> checkpoints() {
        return checkpoints;
    }

    List<double[]> spawnPoints() {
        return spawnPoints;
    }

    ButtonSwitch buttonSwitch() {
        return buttonSwitch;
    }

    void setButtonSwitch(ButtonSwitch buttonSwitch) {
        this.buttonSwitch = buttonSwitch;
    }

    Door door() {
        return door;
    }

    void setDoor(Door door) {
        this.door = door;
    }

    ExitZone exitZone() {
        return exitZone;
    }

    void setExitZone(ExitZone exitZone) {
        this.exitZone = exitZone;
    }

    List<PushBlock> pushBlocks() {
        return pushBlocks;
    }

    List<CollectibleItem> coins() {
        return coins;
    }
}
