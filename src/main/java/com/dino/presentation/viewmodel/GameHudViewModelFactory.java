package com.dino.presentation.viewmodel;

import com.dino.application.services.SessionService;
import com.dino.config.GameConfig;
import com.dino.domain.entities.Player;
import com.dino.presentation.components.EventLogObserver;
import com.dino.presentation.components.ScoreBoardObserver;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabrica de modelos de HUD para la escena principal.
 *
 * <p>Concentra el formateo textual del ranking, el progreso de campaña y el
 * estado de red. Así, {@code GameController} delega la traducción de estado a
 * texto y conserva la coordinación de escena.</p>
 */
public final class GameHudViewModelFactory {
    /**
     * Construye el modelo textual de la HUD a partir del estado actual.
     *
     * @param sessionService sesión compartida de la partida
     * @param scoreBoardObserver ranking derivado de snapshots
     * @param eventLogObserver log visible de eventos
     * @return modelo listo para aplicarse a controles JavaFX
     */
    public GameHudViewModel build(SessionService sessionService,
                                  ScoreBoardObserver scoreBoardObserver,
                                  EventLogObserver eventLogObserver) {
        List<String> playerEntries = new ArrayList<>();
        for (Player player : scoreBoardObserver.getEntries()) {
            playerEntries.add(player.getName() + "  " + player.getScore() + " pts");
        }

        int currentLevel = sessionService.getCurrentLevelIndex() + 1;
        int totalLevels = Math.max(1, sessionService.getTotalLevels());
        String levelName = sessionService.getCurrentLevelName();
        String levelText = (levelName == null || levelName.isBlank() ? "Nivel " + currentLevel : levelName)
            + " / " + totalLevels;

        String roomReason = sessionService.getRoomResetReason();
        String roomStatusText = roomReason == null || roomReason.isBlank()
            ? "Objetivo: todos a la salida"
            : "Reinicio: " + roomReason + " (#" + sessionService.getRoomResetCount() + ")";

        return new GameHudViewModel(
            String.format("%.1fs", sessionService.getElapsedTime()),
            levelText,
            roomStatusText,
            String.format("%.0f px", GameConfig.THREAD_HARD_LIMIT),
            sessionService.isHost() ? "Host | 30 Hz" : "Cliente | 30 Hz",
            playerEntries,
            eventLogObserver.getEntries()
        );
    }
}
