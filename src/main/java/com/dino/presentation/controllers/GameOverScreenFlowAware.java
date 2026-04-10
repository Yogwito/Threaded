package com.dino.presentation.controllers;

import com.dino.presentation.flow.GameOverScreenFlow;

/**
 * Contrato de inyección para controladores que consumen el flujo de resultados
 * finales.
 *
 * <p>Se usa para entregar a la vista final un resumen ya calculado de la
 * campaña, sin exponer la sesión viva ni obligar al controlador a consultar el
 * runtime directamente.</p>
 */
public interface GameOverScreenFlowAware {
    /**
     * Inyecta el flujo de resultados antes de inicializar la escena.
     *
     * @param gameOverScreenFlow fachada de la pantalla final
     */
    void setGameOverScreenFlow(GameOverScreenFlow gameOverScreenFlow);
}
