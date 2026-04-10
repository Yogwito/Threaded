package com.dino.presentation.controllers;

import com.dino.presentation.flow.LobbyScreenFlow;

/**
 * Contrato de inyección para controladores que consumen el flujo del lobby.
 *
 * <p>Permite que la vista de pre-partida dependa solo de una fachada de lobby
 * y no del runtime, del socket o de la sesión completa.</p>
 */
public interface LobbyScreenFlowAware {
    /**
     * Inyecta el flujo del lobby antes de mostrar la escena.
     *
     * @param lobbyScreenFlow fachada del lobby activo
     */
    void setLobbyScreenFlow(LobbyScreenFlow lobbyScreenFlow);
}
