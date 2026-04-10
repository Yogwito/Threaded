package com.dino.presentation.flow;

import com.dino.domain.entities.Player;

import java.util.List;

/**
 * Resumen derivado de la pantalla final.
 *
 * <p>Encapsula el ranking ya ordenado y los textos principales mostrados en la
 * vista de game over, evitando que el controlador mezcle cálculo de resultados
 * con configuración de widgets JavaFX.</p>
 *
 * @param standings ranking ordenado por puntaje descendente
 * @param winnerText texto principal del ganador o empate
 * @param totalTimeText texto de duración total de la partida
 */
public record GameOverSummary(List<Player> standings, String winnerText, String totalTimeText) {
}
