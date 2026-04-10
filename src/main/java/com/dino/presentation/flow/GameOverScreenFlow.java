package com.dino.presentation.flow;

/**
 * Fachada de presentación para la pantalla final de resultados.
 *
 * <p>Entrega a {@code GameOverController} un resumen congelado de la campaña
 * terminada y el callback necesario para reconstruir el runtime al volver al
 * menú. La vista final no consulta directamente la sesión viva para evitar
 * depender de estado mutable después del cierre de partida.</p>
 */
public final class GameOverScreenFlow {
    private final GameOverSummary summary;
    private final Runnable resetToStartMenu;

    /**
     * Construye el flujo de la pantalla final.
     *
     * @param summary resumen congelado de resultados finales
     * @param resetToStartMenu callback que reconstruye el runtime y vuelve al menú
     */
    public GameOverScreenFlow(GameOverSummary summary, Runnable resetToStartMenu) {
        this.summary = summary;
        this.resetToStartMenu = resetToStartMenu;
    }

    /**
     * Retorna el resumen que debe mostrarse en la vista final.
     *
     * @return resumen de ranking y duración
     */
    public GameOverSummary summary() {
        return summary;
    }

    /**
     * Vuelve al menú principal reconstruyendo el runtime actual.
     */
    public void resetToStartMenu() {
        resetToStartMenu.run();
    }
}
