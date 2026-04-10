package com.dino.application.runtime;

import javafx.stage.Stage;

/**
 * Orquestador del ciclo de vida global de la aplicación.
 *
 * <p>Centraliza la creación, reconstrucción y cierre del {@link AppContext}
 * para mantener a {@code MainApp} como bootstrap delgado. También concentra la
 * lógica de volver al menú principal sin exponer estado global estático.</p>
 */
public final class AppRuntimeManager {
    private final Stage stage;
    private AppContext appContext;

    /**
     * Crea un gestor asociado al escenario principal de JavaFX.
     *
     * @param stage escenario sobre el que se montarán las escenas
     */
    public AppRuntimeManager(Stage stage) {
        this.stage = stage;
    }

    /**
     * Inicializa un runtime limpio y muestra el menú principal.
     *
     * @throws Exception si falla la carga de la escena inicial
     */
    public void start() throws Exception {
        rebuildContext();
        appContext.navigator().showStartMenu();
    }

    /**
     * Cierra el runtime activo si existe.
     */
    public void close() {
        if (appContext != null) {
            appContext.close();
        }
    }

    /**
     * Reconstruye todo el runtime y vuelve al menú principal.
     */
    public void resetToStartMenu() {
        try {
            rebuildContext();
            appContext.navigator().showStartMenu();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo volver al menú principal", e);
        }
    }

    /**
     * Retorna el contexto actualmente activo.
     *
     * @return runtime compartido vigente
     */
    public AppContext context() {
        return appContext;
    }

    private void rebuildContext() {
        close();
        appContext = AppContext.create(stage, this::resetToStartMenu);
    }
}
