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
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;

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

    private final GameRenderer renderer = new GameRenderer();
    private final SubscriptionGroup subscriptions = new SubscriptionGroup();

    private AnimationTimer gameLoop;
    private long lastNano = 0;
    private double feedbackTimer = 0;
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
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        feedbackLabel.setVisible(false);
        applyHudStyles();
        bindCanvasInput();
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

        subscriptions.add(gameScreenFlow.bindSceneEvents(
            () -> Platform.runLater(this::refreshUI),
            () -> Platform.runLater(this::onGameOver),
            feedback -> Platform.runLater(() -> showFeedback(feedback.text(), feedback.colorHex()))
        ));

        refreshUI();
        startGameLoop();
    }

    /**
     * Libera recursos temporales de la escena de juego.
     */
    @Override
    public void onSceneHidden() {
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

                if (!gameScreenFlow.isHost() && hasAimTarget) {
                    sendAim(lastWorldMouseX, lastWorldMouseY);
                }

                updateCamera(dt);
                updateFeedback(dt);
                render();
                updateTimer();
            }
        };
        gameLoop.start();
    }

    private void stopGameLoop() {
        if (gameLoop != null) {
            gameLoop.stop();
            gameLoop = null;
        }
        lastNano = 0;
    }

    private void sendAim(double worldX, double worldY) {
        gameScreenFlow.sendAim(worldX, worldY);
    }

    private void sendJump() {
        gameScreenFlow.sendJump();
    }

    private double canvasToWorldX(double canvasX) {
        double scaleX = arenaCanvas.getWidth() / getViewportWorldWidth();
        return cameraX + (canvasX / scaleX);
    }

    private double canvasToWorldY(double canvasY) {
        double scaleY = arenaCanvas.getHeight() / getViewportWorldHeight();
        return cameraY + (canvasY / scaleY);
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

    private void updateTimer() {
        timerLabel.setText(String.format("%.1fs", gameScreenFlow.elapsedTime()));
    }

    /**
     * Sincroniza HUD, ranking y bitácora con el snapshot visible del juego.
     */
    private void refreshUI() {
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
     */
    private void showFeedback(String text, String colorHex) {
        feedbackTimer = FEEDBACK_DURATION_SECONDS;
        feedbackLabel.setText(text);
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

    private double getViewportWorldWidth() {
        return GameConfig.VIEWPORT_W / currentZoom;
    }

    private double getViewportWorldHeight() {
        return GameConfig.VIEWPORT_H / currentZoom;
    }

    private double clampCameraX(double x) {
        return Math.max(0, Math.min(GameConfig.LEVEL_WIDTH - getViewportWorldWidth(), x));
    }

    private double clampCameraY(double y) {
        return Math.max(0, Math.min(GameConfig.LEVEL_HEIGHT - getViewportWorldHeight(), y));
    }
}
