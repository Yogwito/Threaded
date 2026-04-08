package com.dino.presentation.navigation;

import com.dino.application.runtime.AppContext;
import com.dino.presentation.controllers.AppContextAware;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Navegador mínimo para escenas JavaFX del proyecto.
 *
 * <p>Centraliza la carga de FXML y la inyección del {@link AppContext} en los
 * controladores para evitar duplicar esta lógica en cada pantalla.</p>
 */
public final class SceneNavigator {
    private final Stage stage;
    private final AppContext appContext;

    public SceneNavigator(Stage stage, AppContext appContext) {
        this.stage = stage;
        this.appContext = appContext;
    }

    public void showStartMenu() throws Exception {
        show("/com.dino.views/start_menu.fxml");
    }

    public void showLobby() throws Exception {
        show("/com.dino.views/lobby.fxml");
    }

    public void showGame() throws Exception {
        show("/com.dino.views/game.fxml");
    }

    public void showGameOver() throws Exception {
        show("/com.dino.views/game_over.fxml");
    }

    /**
     * Carga una vista FXML y, si el controlador lo soporta, le inyecta el
     * contexto compartido de la aplicación.
     *
     * @param resourcePath ruta del recurso FXML
     * @throws Exception si falla la carga de la escena
     */
    public void show(String resourcePath) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(resourcePath));
        loader.setControllerFactory(type -> {
            try {
                Object controller = type.getDeclaredConstructor().newInstance();
                if (controller instanceof AppContextAware aware) {
                    aware.setAppContext(appContext);
                }
                return controller;
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo crear el controlador " + type.getName(), e);
            }
        });
        Scene scene = new Scene(loader.load(), 1280, 780);
        stage.setScene(scene);
    }
}
