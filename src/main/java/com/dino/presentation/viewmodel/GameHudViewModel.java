package com.dino.presentation.viewmodel;

import java.util.List;

/**
 * Proyección textual de la HUD de gameplay.
 *
 * <p>Agrupa las cadenas y listas que la vista JavaFX necesita para actualizar
 * la interfaz sin exponer a {@code GameController} a demasiados detalles del
 * estado de sesión.</p>
 *
 * @param timerText texto del tiempo transcurrido
 * @param levelText texto del nivel actual
 * @param roomStatusText texto del estado de sala
 * @param threadText texto informativo del límite del hilo
 * @param networkText texto del rol de red local
 * @param playerEntries entradas visibles del ranking
 * @param eventEntries entradas visibles del log de eventos
 */
public record GameHudViewModel(
    String timerText,
    String levelText,
    String roomStatusText,
    String threadText,
    String networkText,
    List<String> playerEntries,
    List<String> eventEntries
) {
}
