package com.dino.presentation.render;

import com.dino.application.services.SessionService;
import com.dino.domain.entities.ExitZone;
import com.dino.domain.entities.Player;
import com.dino.domain.entities.PlatformTile;

import java.util.List;

/**
 * Fabrica de snapshots de render a partir del estado de sesión.
 *
 * <p>Extrae de {@code GameController} la construcción de {@link GameRenderState}
 * y la búsqueda del jugador local. El renderer sigue desacoplado del runtime y
 * el controlador reduce acceso directo al modelo.</p>
 */
public final class GameRenderStateFactory {
    private int cachedLevelIndex = Integer.MIN_VALUE;
    private String cachedBackground = "";
    private int cachedTileSize = -1;
    private List<PlatformTile> cachedPlatforms = List.of();
    private List<PlatformTile> cachedSpecialPlatforms = List.of();
    private List<PlatformTile> cachedHazards = List.of();
    private List<PlatformTile> cachedCheckpoints = List.of();
    private ExitZone cachedExitZone;

    /**
     * Construye el snapshot de render de un frame.
     *
     * @param sessionService sesión compartida de gameplay
     * @param cameraX desplazamiento X de la cámara
     * @param cameraY desplazamiento Y de la cámara
     * @param viewportWidth ancho visible en mundo
     * @param viewportHeight alto visible en mundo
     * @param localPlayerId identificador del jugador local
     * @param visualTimeSeconds tiempo visual continuo usado por el renderer
     * @return snapshot de render listo para {@link GameRenderer}
     */
    public GameRenderState build(SessionService sessionService,
                                 double cameraX,
                                 double cameraY,
                                 double viewportWidth,
                                 double viewportHeight,
                                 String localPlayerId,
                                 double visualTimeSeconds) {
        refreshStaticLevelCacheIfNeeded(sessionService);
        List<Player> players = sessionService.getPlayersSnapshot();
        Player localPlayer = findLocalPlayer(players, localPlayerId);
        return new GameRenderState(
            cachedBackground,
            cachedTileSize,
            cameraX,
            cameraY,
            viewportWidth,
            viewportHeight,
            cachedPlatforms,
            cachedSpecialPlatforms,
            cachedHazards,
            cachedCheckpoints,
            sessionService.getPushBlocksSnapshot(),
            sessionService.getButtonSwitchSnapshot(),
            sessionService.getDoorSnapshot(),
            cachedExitZone,
            sessionService.getCoinsSnapshot(),
            players,
            localPlayer,
            visualTimeSeconds
        );
    }

    /**
     * Refresca el cache de geometría estática solo cuando cambia el nivel
     * visible.
     *
     * <p>Las plataformas, hazards, checkpoints y la zona de salida permanecen
     * estables durante un nivel completo. Cachearlas evita recrear copias
     * defensivas idénticas en cada frame del render loop.</p>
     *
     * @param sessionService sesión compartida actual
     */
    private void refreshStaticLevelCacheIfNeeded(SessionService sessionService) {
        int levelIndex = sessionService.getCurrentLevelIndex();
        String background = sessionService.getCurrentBackground();
        int tileSize = sessionService.getCurrentTileSize();
        if (levelIndex == cachedLevelIndex
            && tileSize == cachedTileSize
            && background.equals(cachedBackground)) {
            return;
        }

        cachedLevelIndex = levelIndex;
        cachedBackground = background;
        cachedTileSize = tileSize;
        cachedPlatforms = sessionService.getPlatformsSnapshot();
        cachedSpecialPlatforms = sessionService.getSpecialPlatformsSnapshot();
        cachedHazards = sessionService.getHazardsSnapshot();
        cachedCheckpoints = sessionService.getCheckpointsSnapshot();
        cachedExitZone = sessionService.getExitZoneSnapshot();
    }

    /**
     * Busca al jugador local dentro de la lista a renderizar.
     */
    public Player findLocalPlayer(List<Player> players, String localPlayerId) {
        if (localPlayerId == null) {
            return null;
        }
        for (Player player : players) {
            if (localPlayerId.equals(player.getId())) {
                return player;
            }
        }
        return null;
    }
}
