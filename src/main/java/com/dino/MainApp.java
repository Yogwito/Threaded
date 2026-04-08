package com.dino;

import com.dino.application.runtime.AppContext;
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
 * independientes, sino de un {@link AppContext} central.</p>
 */
public class MainApp extends Application {
    private static Stage primaryStage;
    private static AppContext appContext;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        resetRuntimeState();

        stage.setTitle("Threaded");
        stage.setResizable(false);
        stage.setOnCloseRequest(event -> {
            if (appContext != null) appContext.close();
        });

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        stage.setX((screen.getWidth() - 1280) / 2);
        stage.setY((screen.getHeight() - 780) / 2);

        appContext.navigator().showStartMenu();
        stage.show();
    }

    /**
     * Retorna el escenario principal del runtime actual.
     */
    public static Stage getStage() {
        return primaryStage;
    }

    /**
     * Retorna el contexto compartido actual.
     */
    public static AppContext context() {
        return appContext;
    }

    /**
     * Reconstruye el contexto completo de runtime desde cero.
     *
     * <p>Se usa al iniciar la app y al volver al menú tras terminar una sesión.
     * Primero libera recursos del contexto anterior y luego crea uno nuevo.</p>
     */
    public static void resetRuntimeState() {
        if (appContext != null) appContext.close();
        appContext = AppContext.create(primaryStage, MainApp::resetToStartMenu);
    }

    /**
     * Reconstruye el runtime y vuelve a mostrar el menú principal.
     */
    private static void resetToStartMenu() {
        try {
            resetRuntimeState();
            appContext.navigator().showStartMenu();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo volver al menú principal", e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
