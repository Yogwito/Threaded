package com.dino.presentation.render;

import com.dino.domain.entities.ButtonSwitch;
import com.dino.domain.entities.CollectibleItem;
import com.dino.domain.entities.Door;
import com.dino.domain.entities.ExitZone;
import com.dino.domain.entities.PlatformTile;
import com.dino.domain.entities.Player;
import com.dino.domain.entities.PushBlock;

import java.util.List;

/**
 * Snapshot de presentación necesario para dibujar un frame completo del juego.
 *
 * <p>El controlador construye esta estructura a partir del estado de sesión y
 * se la entrega al renderer. Así el render queda desacoplado del runtime, del
 * polling de red y de la lógica de navegación.</p>
 *
 * @param background nombre del biome o fondo visual activo
 * @param tileSize tamaño lógico del tile actual
 * @param cameraX desplazamiento X de cámara en mundo
 * @param cameraY desplazamiento Y de cámara en mundo
 * @param viewportWidth ancho visible en coordenadas de mundo
 * @param viewportHeight alto visible en coordenadas de mundo
 * @param platforms plataformas sólidas normales
 * @param specialPlatforms plataformas especiales
 * @param hazards hazards rectangulares del nivel
 * @param checkpoints checkpoints visibles
 * @param pushBlocks bloques empujables
 * @param button botón del nivel si existe
 * @param door puerta del nivel si existe
 * @param exitZone zona de salida si existe
 * @param coins coleccionables del nivel
 * @param players jugadores conectados conocidos
 * @param localPlayer copia del jugador local para resaltar selección
 * @param visualTimeSeconds tiempo visual continuo usado para animaciones de
 *                          presentación que no alteran la simulación
 */
public record GameRenderState(
    String background,
    int tileSize,
    double cameraX,
    double cameraY,
    double viewportWidth,
    double viewportHeight,
    List<PlatformTile> platforms,
    List<PlatformTile> specialPlatforms,
    List<PlatformTile> hazards,
    List<PlatformTile> checkpoints,
    List<PushBlock> pushBlocks,
    ButtonSwitch button,
    Door door,
    ExitZone exitZone,
    List<CollectibleItem> coins,
    List<Player> players,
    Player localPlayer,
    double visualTimeSeconds
) {
}
