package com.dino.presentation.controllers;

import com.dino.presentation.flow.GameScreenFlow;

/**
 * Contrato de inyección para controladores que consumen el flujo de gameplay.
 *
 * <p>Permite que {@code SceneNavigator} inyecte una fachada específica de la
 * escena principal sin exponer a la UI el runtime completo ni la sesión
 * compartida.</p>
 */
public interface GameScreenFlowAware {
    /**
     * Inyecta el flujo de gameplay antes de que la escena quede activa.
     *
     * @param gameScreenFlow fachada de la partida actual
     */
    void setGameScreenFlow(GameScreenFlow gameScreenFlow);
}
