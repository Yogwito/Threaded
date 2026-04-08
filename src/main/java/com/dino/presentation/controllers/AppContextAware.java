package com.dino.presentation.controllers;

import com.dino.application.runtime.AppContext;

/**
 * Contrato para controladores JavaFX que requieren acceso al contexto
 * compartido de la aplicación.
 */
public interface AppContextAware {
    /**
     * Inyecta el contexto compartido antes de que el controlador empiece a
     * interactuar con la escena.
     *
     * @param appContext runtime compartido del juego
     */
    void setAppContext(AppContext appContext);
}
