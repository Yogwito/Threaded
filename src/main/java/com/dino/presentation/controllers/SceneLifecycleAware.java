package com.dino.presentation.controllers;

/**
 * Contrato opcional para controladores que necesitan gestionar su ciclo de
 * vida al entrar o salir de escena.
 *
 * <p>JavaFX invoca {@code initialize()} una sola vez por instancia, pero no
 * ofrece un hook directo cuando una escena deja de estar activa. Este contrato
 * permite arrancar timers, loops o suscripciones al mostrarse la vista y
 * liberarlos de forma explícita al abandonarla.</p>
 */
public interface SceneLifecycleAware {
    /**
     * Se invoca después de que la escena queda montada en el escenario.
     */
    default void onSceneShown() {
    }

    /**
     * Se invoca justo antes de reemplazar la escena actual.
     */
    default void onSceneHidden() {
    }
}
