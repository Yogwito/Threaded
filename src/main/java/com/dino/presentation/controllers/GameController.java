package com.dino.presentation.controllers;

import com.dino.application.services.GameplaySignal;
import com.dino.application.services.SubscriptionGroup;
import com.dino.config.GameConfig;
import com.dino.domain.entities.Player;
import com.dino.presentation.flow.GameScreenFlow;
import com.dino.presentation.flow.GameplayFeedback;
import com.dino.presentation.render.GameRenderState;
import com.dino.presentation.render.GameRenderer;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controlador principal de la partida en curso.
 *
 * <p>Actúa como adaptador entre la vista JavaFX y el flujo de gameplay
 * especializado. De esta forma conserva coordinación visual, cámara y widgets,
 * pero delega protocolo, snapshots y proyección de HUD en colaboradores
 * dedicados.</p>
 */
public class GameController implements Initializable, GameScreenFlowAware, SceneLifecycleAware {
    private static final double MAX_FRAME_DELTA_SECONDS = 0.05;
    @FXML private Canvas arenaCanvas;
    @FXML private StackPane arenaPane;
    @FXML private Pane levelTransitionOverlay;
    @FXML private Label timerLabel;
    @FXML private Label levelLabel;
    @FXML private Label roomStatusLabel;
    @FXML private Label threadLabel;
    @FXML private Label networkLabel;
    @FXML private ListView<String> playersList;
    @FXML private ListView<String> eventLog;
    @FXML private Label feedbackLabel;

    private static final double CAMERA_SMOOTHING = 0.16;
    private static final double FEEDBACK_DURATION_SECONDS = 1.4;
    /** Segundos sin snapshot antes de avisar al cliente que el host puede haberse caído. */
    private static final double HOST_TIMEOUT_SECONDS = GameConfig.SNAPSHOT_STALE_WARNING_SECONDS * 5;

    private final GameRenderer renderer = new GameRenderer();
    private final SubscriptionGroup subscriptions = new SubscriptionGroup();

    private AnimationTimer gameLoop;
    private long lastNano = 0;
    private double feedbackTimer = 0;
    private double timeSinceLastSnapshot = 0;
    private double cameraX = 0;
    private double cameraY = 0;
    private double currentZoom = GameConfig.BASE_ZOOM;
    private double lastWorldMouseX = 0;
    private double lastWorldMouseY = 0;
    private boolean hasAimTarget = false;
    private boolean gameOverSceneOpened = false;
    private GameScreenFlow gameScreenFlow;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setGameScreenFlow(GameScreenFlow gameScreenFlow) {
        this.gameScreenFlow = gameScreenFlow;
    }

    /**
     * Configura únicamente elementos propios de la vista JavaFX.
     *
     * <p>Las suscripciones al bus, el loop y el coordinador de gameplay se
     * activan en {@link #onSceneShown()} para que puedan liberarse de forma
     * explícita al abandonar la escena.</p>
     *
     * @param url ubicación del recurso FXML, si JavaFX la proporciona
     * @param rb bundle de recursos asociado a la vista, si existe
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        feedbackLabel.setVisible(false);
        applyHudStyles();
        bindCanvasInput();
        arenaCanvas.setManaged(false);
        arenaPane.widthProperty().addListener((obs, old, w) -> syncArenaCanvasSize());
        arenaPane.heightProperty().addListener((obs, old, h) -> syncArenaCanvasSize());
        Platform.runLater(this::syncArenaCanvasSize);
    }

    /**
     * Arranca el ciclo de vida activo de la escena de juego.
     */
    @Override
    public void onSceneShown() {
        gameScreenFlow.activate();
        gameOverSceneOpened = false;
        lastNano = 0;
        feedbackTimer = 0;
        cameraX = 0;
        cameraY = 0;
        lastWorldMouseX = 0;
        lastWorldMouseY = 0;
        hasAimTarget = false;
        timeSinceLastSnapshot = 0;

        subscriptions.add(gameScreenFlow.bindSceneEvents(
            () -> Platform.runLater(this::refreshUI),
            () -> Platform.runLater(this::onGameOver),
            feedback -> Platform.runLater(() -> showFeedback(feedback.text(), feedback.colorHex()))
        ));

        arenaCanvas.getScene().setOnKeyPressed(ev -> {
            if (ev.getCode() == GameConfig.RESTART_KEY && gameScreenFlow.isHost()) {
                gameScreenFlow.resetCurrentRoom();
            }
        });

        refreshUI();
        startGameLoop();
    }

    /**
     * Libera recursos temporales de la escena de juego.
     */
    @Override
    public void onSceneHidden() {
        if (arenaCanvas.getScene() != null) {
            arenaCanvas.getScene().setOnKeyPressed(null);
        }
        subscriptions.clear();
        stopGameLoop();
    }

    /**
     * Cierra la sesión actual y reconstruye el runtime desde el menú inicial.
     */
    @FXML
    public void onVolverAlMenu() {
        gameScreenFlow.resetToStartMenu();
    }

    /**
     * Conecta el canvas principal con los gestos de apuntado y salto.
     *
     * <p>La escena traduce coordenadas de pantalla a coordenadas del mundo para
     * mantener el input consistente con la cámara actual. El click primario
     * reutiliza el último punto apuntado y además dispara un salto.</p>
     */
    private void bindCanvasInput() {
        arenaCanvas.setOnMouseMoved(ev -> {
            lastWorldMouseX = canvasToWorldX(ev.getX());
            lastWorldMouseY = canvasToWorldY(ev.getY());
            hasAimTarget = true;
            sendAim(lastWorldMouseX, lastWorldMouseY);
        });
        arenaCanvas.setOnMouseDragged(ev -> {
            lastWorldMouseX = canvasToWorldX(ev.getX());
            lastWorldMouseY = canvasToWorldY(ev.getY());
            hasAimTarget = true;
            sendAim(lastWorldMouseX, lastWorldMouseY);
        });
        arenaCanvas.setOnMousePressed(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY) {
                lastWorldMouseX = canvasToWorldX(ev.getX());
                lastWorldMouseY = canvasToWorldY(ev.getY());
                hasAimTarget = true;
                sendAim(lastWorldMouseX, lastWorldMouseY);
                sendJump();
            }
        });
    }

    /**
     * Arranca el loop visual y delega la parte de protocolo al coordinador.
     */
    private void startGameLoop() {
        stopGameLoop();
        gameLoop = new AnimationTimer() {
            /**
             * Ejecuta un frame visual completo: red, simulación, cámara, render y HUD.
             *
             * @param now timestamp de JavaFX en nanosegundos
             */
            @Override
            public void handle(long now) {
                if (lastNano == 0) {
                    lastNano = now;
                    return;
                }

                double dt = Math.min(MAX_FRAME_DELTA_SECONDS, (now - lastNano) / 1_000_000_000.0);
                lastNano = now;
                if (dt <= 0) {
                    return;
                }

                GameplaySignal signal = gameScreenFlow.pollIncomingMessages();
                if (signal == GameplaySignal.GAME_OVER) {
                    Platform.runLater(GameController.this::onGameOver);
                }

                gameScreenFlow.advanceFrame(dt);

                if (!gameScreenFlow.isHost()) {
                    timeSinceLastSnapshot += dt;
                    if (timeSinceLastSnapshot > HOST_TIMEOUT_SECONDS) {
                        showHostDisconnectedWarning();
                    }
                    if (hasAimTarget) {
                        sendAim(lastWorldMouseX, lastWorldMouseY);
                    }
                }

                updateCamera(dt);
                updateFeedback(dt);
                render();
                updateTimer();
            }
        };
        gameLoop.start();
    }

    /**
     * Detiene el loop visual activo y reinicia su reloj interno.
     */
    private void stopGameLoop() {
        if (gameLoop != null) {
            gameLoop.stop();
            gameLoop = null;
        }
        lastNano = 0;
    }

    /**
     * Reenvía al flujo el último objetivo de apuntado en coordenadas de mundo.
     *
     * @param worldX coordenada X destino en el mundo
     * @param worldY coordenada Y destino en el mundo
     */
    private void sendAim(double worldX, double worldY) {
        gameScreenFlow.sendAim(worldX, worldY);
    }

    /**
     * Solicita un salto para el jugador local a través del flujo activo.
     */
    private void sendJump() {
        gameScreenFlow.sendJump();
    }

    /**
     * Convierte una coordenada X del canvas a una coordenada X del mundo.
     *
     * @param canvasX posición horizontal en píxeles del canvas
     * @return coordenada horizontal equivalente en el mundo visible
     */
    private double canvasToWorldX(double canvasX) {
        double scale = renderer.getRenderScale();
        return cameraX + (canvasX - renderer.getRenderOffsetX()) / scale;
    }

    /**
     * Convierte una coordenada Y del canvas a una coordenada Y del mundo.
     *
     * @param canvasY posición vertical en píxeles del canvas
     * @return coordenada vertical equivalente en el mundo visible
     */
    private double canvasToWorldY(double canvasY) {
        double scale = renderer.getRenderScale();
        return cameraY + (canvasY - renderer.getRenderOffsetY()) / scale;
    }

    /**
     * Redibuja el mundo usando el renderer dedicado.
     */
    private void render() {
        if (arenaCanvas.getWidth() <= 0 || arenaCanvas.getHeight() <= 0) {
            return;
        }
        GameRenderState state = gameScreenFlow.buildRenderState(
            cameraX,
            cameraY,
            getViewportWorldWidth(),
            getViewportWorldHeight(),
            gameScreenFlow.elapsedTime()
        );
        renderer.render(arenaCanvas, state);
    }

    /**
     * Sincroniza el tamaño de render del canvas con el área útil del contenedor.
     *
     * <p>El canvas queda fuera del layout para que su tamaño no realimente el
     * pref-size del {@code arenaPane} y la escena permanezca estable.</p>
     */
    private void syncArenaCanvasSize() {
        double targetWidth = Math.max(0, arenaPane.getWidth()
            - arenaPane.getInsets().getLeft() - arenaPane.getInsets().getRight());
        double targetHeight = Math.max(0, arenaPane.getHeight()
            - arenaPane.getInsets().getTop() - arenaPane.getInsets().getBottom());

        if (Double.compare(arenaCanvas.getWidth(), targetWidth) != 0) {
            arenaCanvas.setWidth(targetWidth);
        }
        if (Double.compare(arenaCanvas.getHeight(), targetHeight) != 0) {
            arenaCanvas.setHeight(targetHeight);
        }
    }

    /**
     * Aplica el estilo base de valores, listas y feedback de la HUD.
     *
     * <p>Centralizar estos estilos en código evita duplicar cadenas CSS en el
     * FXML para widgets cuyo aspecto depende del estado del gameplay.</p>
     */
    private void applyHudStyles() {
        String valueStyle = "-fx-text-fill: #eef6ff; -fx-font-family: 'Monospaced'; "
            + "-fx-font-size: 15px; -fx-font-weight: bold;";
        String listStyle = "-fx-control-inner-background: #121822; -fx-background-color: #121822; "
            + "-fx-border-color: #314356; -fx-border-width: 1.5; -fx-border-radius: 14; "
            + "-fx-background-radius: 14; -fx-text-fill: #eef6ff; -fx-font-family: 'Monospaced'; "
            + "-fx-focus-color: transparent; -fx-faint-focus-color: transparent;";

        timerLabel.setStyle(valueStyle);
        levelLabel.setStyle(valueStyle);
        roomStatusLabel.setStyle("-fx-text-fill: #ffdcaa; -fx-font-family: 'Monospaced'; "
            + "-fx-font-size: 14px; -fx-font-weight: bold;");
        threadLabel.setStyle(valueStyle);
        networkLabel.setStyle("-fx-text-fill: #98d8ff; -fx-font-family: 'Monospaced'; "
            + "-fx-font-size: 14px; -fx-font-weight: bold;");
        feedbackLabel.setStyle("-fx-background-color: rgba(10,15,24,0.92); "
            + "-fx-background-radius: 999; -fx-border-color: rgba(126,167,212,0.55); "
            + "-fx-border-width: 1.8; -fx-border-radius: 999; -fx-padding: 10 22 10 22; "
            + "-fx-text-fill: #eaf4ff; -fx-font-family: 'Monospaced'; -fx-font-size: 17px; "
            + "-fx-font-weight: bold;");
        playersList.setStyle(listStyle);
        eventLog.setStyle(listStyle);
    }

    /**
     * Refresca el cronómetro visible de la HUD durante el loop de render.
     */
    private void updateTimer() {
        timerLabel.setText(String.format("%.1fs", gameScreenFlow.elapsedTime()));
    }

    /**
     * Sincroniza HUD, ranking y bitácora con el snapshot visible del juego.
     */
    private void refreshUI() {
        timeSinceLastSnapshot = 0;
        var viewModel = gameScreenFlow.buildHudViewModel();
        playersList.getItems().setAll(viewModel.playerEntries());
        eventLog.getItems().setAll(viewModel.eventEntries());
        timerLabel.setText(viewModel.timerText());
        levelLabel.setText(viewModel.levelText());
        roomStatusLabel.setText(viewModel.roomStatusText());
        threadLabel.setText(viewModel.threadText());
        networkLabel.setText(viewModel.networkText());
    }

    /**
     * Abre la escena final una sola vez cuando la partida termina.
     */
    private void onGameOver() {
        if (gameOverSceneOpened) return;
        gameOverSceneOpened = true;
        stopGameLoop();
        try {
            gameScreenFlow.showGameOver();
        } catch (Exception e) {
            System.err.println("[GameController] Game over: " + e.getMessage());
        }
    }

    /**
     * Interpola la cámara hacia el jugador local manteniéndola dentro del mapa.
     */
    private void updateCamera(double dt) {
        Player localPlayer = gameScreenFlow.localPlayerSnapshot();
        if (localPlayer == null) return;

        double targetX = clampCameraX(localPlayer.getCenterX() - getViewportWorldWidth() / 2.0);
        double targetY = clampCameraY(localPlayer.getCenterY() - getViewportWorldHeight() / 2.0);
        cameraX += (targetX - cameraX) * Math.min(1.0, CAMERA_SMOOTHING * (dt * 60.0));
        cameraY += (targetY - cameraY) * Math.min(1.0, CAMERA_SMOOTHING * (dt * 60.0));
        cameraX = clampCameraX(cameraX);
        cameraY = clampCameraY(cameraY);
    }

    /**
     * Gestiona la desaparición progresiva del feedback contextual de la HUD.
     */
    private void updateFeedback(double dt) {
        if (feedbackTimer <= 0) return;
        feedbackTimer = Math.max(0, feedbackTimer - dt);
        double alpha = Math.min(1.0, feedbackTimer / FEEDBACK_DURATION_SECONDS);
        feedbackLabel.setVisible(alpha > 0);
        feedbackLabel.setOpacity(alpha);
        feedbackLabel.setTranslateY((1.0 - alpha) * -10.0);
        if (alpha == 0) feedbackLabel.setText("");
    }

    /**
     * Muestra un mensaje temporal resaltado para eventos relevantes.
     *
     * @param text texto visible en la píldora de feedback
     * @param colorHex color CSS usado para resaltar el mensaje
     */
    /**
     * Muestra un aviso persistente cuando el cliente no recibe snapshots del host.
     *
     * <p>Se activa cuando {@code timeSinceLastSnapshot} supera {@code HOST_TIMEOUT_SECONDS}.
     * El aviso desaparece automáticamente en cuanto llega el siguiente snapshot y se
     * resetea el contador.</p>
     */
    private void showHostDisconnectedWarning() {
        feedbackLabel.setText("Sin señal del host — reconectando...");
        feedbackLabel.setStyle(
            "-fx-background-color: rgba(60,10,10,0.92); "
                + "-fx-background-radius: 999; "
                + "-fx-border-color: rgba(220,60,60,0.6); -fx-border-width: 1.8; -fx-border-radius: 999; "
                + "-fx-padding: 10 22 10 22; "
                + "-fx-text-fill: #ff8888; "
                + "-fx-font-family: 'Monospaced'; -fx-font-size: 15px; -fx-font-weight: bold;"
        );
        feedbackLabel.setVisible(true);
        feedbackLabel.setOpacity(1.0);
        feedbackLabel.setTranslateY(0);
        feedbackTimer = FEEDBACK_DURATION_SECONDS;
    }

    /**
     * Sacude el área de juego con un micro-desplazamiento para reforzar el impacto de muerte.
     *
     * <p>Anima la traslación X del {@code arenaPane} en cuatro ciclos alternativos de ±5 px
     * durante 180 ms en total, volviendo a 0 al terminar.</p>
     */
    private void triggerScreenShake() {
        double amp = 5.0;
        double step = 180.0 / 8;
        Timeline shake = new Timeline(
            new KeyFrame(Duration.millis(step * 1), new KeyValue(arenaPane.translateXProperty(),  amp)),
            new KeyFrame(Duration.millis(step * 2), new KeyValue(arenaPane.translateXProperty(), -amp)),
            new KeyFrame(Duration.millis(step * 3), new KeyValue(arenaPane.translateXProperty(),  amp)),
            new KeyFrame(Duration.millis(step * 4), new KeyValue(arenaPane.translateXProperty(), -amp)),
            new KeyFrame(Duration.millis(step * 5), new KeyValue(arenaPane.translateXProperty(),  amp * 0.5)),
            new KeyFrame(Duration.millis(step * 6), new KeyValue(arenaPane.translateXProperty(), -amp * 0.5)),
            new KeyFrame(Duration.millis(step * 7), new KeyValue(arenaPane.translateXProperty(),  amp * 0.25)),
            new KeyFrame(Duration.millis(step * 8), new KeyValue(arenaPane.translateXProperty(),  0.0))
        );
        shake.play();
    }

    /**
     * Ejecuta el flash negro de transición entre niveles: fade-in 250 ms → fade-out 250 ms.
     */
    private void triggerLevelTransition() {
        levelTransitionOverlay.setOpacity(0.0);
        levelTransitionOverlay.setVisible(true);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), levelTransitionOverlay);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        fadeIn.setOnFinished(e -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(250), levelTransitionOverlay);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(ev -> levelTransitionOverlay.setVisible(false));
            fadeOut.play();
        });
        fadeIn.play();
    }

    /**
     * Muestra un mensaje temporal resaltado para eventos relevantes del gameplay.
     *
     * <p>Además de actualizar la píldora de HUD, dispara efectos secundarios
     * visuales según el texto recibido: flash rojo al morir y fade de pantalla
     * al completar un nivel.</p>
     *
     * @param text texto visible en la píldora de feedback
     * @param colorHex color CSS del texto del mensaje
     */
    private void showFeedback(String text, String colorHex) {
        feedbackTimer = FEEDBACK_DURATION_SECONDS;
        feedbackLabel.setText(text);
        if ("Nivel completado".equals(text)) {
            triggerLevelTransition();
        }
        if ("Caida al vacio".equals(text) || "Hazard".equals(text)) {
            renderer.markDeath();
            triggerScreenShake();
        }
        feedbackLabel.setStyle(
            "-fx-background-color: rgba(9,14,24,0.92); "
                + "-fx-background-radius: 999; "
                + "-fx-border-color: rgba(120,160,210,0.55); -fx-border-width: 1.8; -fx-border-radius: 999; "
                + "-fx-padding: 10 22 10 22; "
                + "-fx-text-fill: " + colorHex + "; "
                + "-fx-font-family: 'Monospaced'; -fx-font-size: 16px; -fx-font-weight: bold;"
        );
        feedbackLabel.setVisible(true);
        feedbackLabel.setOpacity(1.0);
        feedbackLabel.setTranslateY(0);
    }

    /**
     * Calcula el ancho del viewport visible en coordenadas de mundo.
     *
     * @return ancho lógico visible tras aplicar el zoom actual
     */
    private double getViewportWorldWidth() {
        return GameConfig.VIEWPORT_W / currentZoom;
    }

    /**
     * Calcula el alto del viewport visible en coordenadas de mundo.
     *
     * @return alto lógico visible tras aplicar el zoom actual
     */
    private double getViewportWorldHeight() {
        return GameConfig.VIEWPORT_H / currentZoom;
    }

    /**
     * Restringe la cámara horizontal a los límites del nivel.
     *
     * @param x coordenada X candidata para la cámara
     * @return coordenada horizontal válida dentro del mapa
     */
    private double clampCameraX(double x) {
        return Math.max(0, Math.min(GameConfig.LEVEL_WIDTH - getViewportWorldWidth(), x));
    }

    /**
     * Restringe la cámara vertical a los límites del nivel.
     *
     * @param y coordenada Y candidata para la cámara
     * @return coordenada vertical válida dentro del mapa
     */
    private double clampCameraY(double y) {
        return Math.max(0, Math.min(GameConfig.LEVEL_HEIGHT - getViewportWorldHeight(), y));
    }
}
