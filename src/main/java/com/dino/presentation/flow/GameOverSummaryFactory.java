package com.dino.presentation.flow;

import com.dino.application.services.SessionService;
import com.dino.domain.entities.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabrica de resumenes para la pantalla de resultados finales.
 */
public final class GameOverSummaryFactory {
    /**
     * Construye el resumen final visible a partir del estado congelado de la
     * sesión al terminar la campaña.
     *
     * @param sessionService sesión finalizada de la que se leen resultados
     * @return resumen ordenado y listo para la vista
     */
    public GameOverSummary build(SessionService sessionService) {
        List<Player> sorted = new ArrayList<>(sessionService.getPlayersSnapshot());
        sorted.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

        String winnerText = "";
        if (!sorted.isEmpty()) {
            boolean tie = sorted.size() > 1 && sorted.get(0).getScore() == sorted.get(1).getScore();
            winnerText = tie
                ? "¡Empate!"
                : "Ganador: " + sorted.get(0).getName() + " (" + sorted.get(0).getScore() + " masa)";
        }

        return new GameOverSummary(
            List.copyOf(sorted),
            winnerText,
            String.format("Duración: %.1fs", sessionService.getElapsedTime())
        );
    }
}
