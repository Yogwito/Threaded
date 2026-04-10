package com.dino.presentation.controllers;

import com.dino.application.runtime.AppContext;

/**
 * Contrato para controladores JavaFX que requieren acceso al contexto
 * compartido de la aplicación.
 *
 * <p>Se conserva como mecanismo de compatibilidad para vistas que todavía
 * necesitan el runtime completo. La dirección actual del proyecto es preferir
 * contratos más pequeños por pantalla, como los flujos de presentación, pero
 * este punto de inyección sigue existiendo para escenarios puntuales de
 * wiring.</p>
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
