package com.dino.presentation.navigation;

import com.dino.application.runtime.AppContext;
import com.dino.presentation.controllers.AppContextAware;
import com.dino.presentation.controllers.GameOverScreenFlowAware;
import com.dino.presentation.controllers.GameScreenFlowAware;
import com.dino.presentation.controllers.LobbyScreenFlowAware;
import com.dino.presentation.controllers.SceneLifecycleAware;
import com.dino.presentation.controllers.StartMenuFlowAware;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Navegador mínimo para escenas JavaFX del proyecto.
 *
 * <p>Centraliza la carga de FXML y la inyección del {@link AppContext} en los
 * controladores para evitar duplicar esta lógica en cada pantalla. También
 * notifica el ciclo de vida de escena cuando un controlador implementa
 * {@link SceneLifecycleAware}, de modo que timers y listeners temporales puedan
 * arrancarse y liberarse con claridad.</p>
 */
public final class SceneNavigator implements SceneNavigation {
    private final Stage stage;
    private final AppContext appContext;
    private Object currentController;

    /**
     * Crea un navegador ligado al escenario principal y al runtime actual.
     *
     * @param stage escenario donde se montarán las escenas
     * @param appContext contexto compartido a inyectar en controladores
     */
    public SceneNavigator(Stage stage, AppContext appContext) {
        this.stage = stage;
        this.appContext = appContext;
    }

    /**
     * Muestra la pantalla inicial de conexión/creación de sesión.
     *
     * @throws Exception si falla la carga del FXML
     */
    public void showStartMenu() throws Exception {
        show("/com.dino.views/start_menu.fxml");
    }

    /**
     * Muestra la pantalla de lobby previa a la partida.
     *
     * @throws Exception si falla la carga del FXML
     */
    public void showLobby() throws Exception {
        show("/com.dino.views/lobby.fxml");
    }

    /**
     * Muestra la escena principal de gameplay.
     *
     * @throws Exception si falla la carga del FXML
     */
    public void showGame() throws Exception {
        show("/com.dino.views/game.fxml");
    }

    /**
     * Muestra la pantalla final de resultados.
     *
     * @throws Exception si falla la carga del FXML
     */
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
        notifySceneHidden(currentController);

        FXMLLoader loader = new FXMLLoader(getClass().getResource(resourcePath));
        loader.setControllerFactory(type -> {
            try {
                Object controller = type.getDeclaredConstructor().newInstance();
                if (controller instanceof AppContextAware aware) {
                    aware.setAppContext(appContext);
                }
                if (controller instanceof StartMenuFlowAware aware) {
                    aware.setStartMenuFlow(appContext.createStartMenuFlow());
                }
                if (controller instanceof LobbyScreenFlowAware aware) {
                    aware.setLobbyScreenFlow(appContext.createLobbyScreenFlow());
                }
                if (controller instanceof GameScreenFlowAware aware) {
                    aware.setGameScreenFlow(appContext.createGameScreenFlow());
                }
                if (controller instanceof GameOverScreenFlowAware aware) {
                    aware.setGameOverScreenFlow(appContext.createGameOverScreenFlow());
                }
                return controller;
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo crear el controlador " + type.getName(), e);
            }
        });
        Scene scene = new Scene(loader.load(), 1280, 780);
        currentController = loader.getController();
        stage.setScene(scene);
        notifySceneShown(currentController);
    }

    private void notifySceneShown(Object controller) {
        if (controller instanceof SceneLifecycleAware aware) {
            aware.onSceneShown();
        }
    }

    private void notifySceneHidden(Object controller) {
        if (controller instanceof SceneLifecycleAware aware) {
            aware.onSceneHidden();
        }
    }
}
