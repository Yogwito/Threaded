package com.dino;

import com.dino.application.runtime.AppRuntimeManager;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Punto de entrada de la aplicación JavaFX.
 *
 * <p>Su responsabilidad es bootstrapear JavaFX, crear el contexto compartido de
 * runtime y delegar la navegación de escenas. La lógica del juego y la
 * infraestructura compartida ya no cuelgan de múltiples campos estáticos
 * independientes, sino de un {@link com.dino.application.runtime.AppContext}
 * central gestionado por {@link AppRuntimeManager}.</p>
 */
public class MainApp extends Application {
    private AppRuntimeManager runtimeManager;

    /**
     * Inicializa el escenario principal, crea el runtime y muestra la primera escena.
     *
     * @param stage escenario raíz entregado por JavaFX
     * @throws Exception si falla la carga del runtime o de la escena inicial
     */
    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle("Threaded");
        stage.setResizable(false);
        runtimeManager = new AppRuntimeManager(stage);
        stage.setOnCloseRequest(event -> runtimeManager.close());

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        stage.setX((screen.getWidth() - 1280) / 2);
        stage.setY((screen.getHeight() - 780) / 2);

        runtimeManager.start();
        stage.show();
    }

    /**
     * Punto de entrada tradicional para arrancar JavaFX desde línea de comandos.
     *
     * @param args argumentos del proceso
     */
    public static void main(String[] args) {
        launch(args);
    }
}
