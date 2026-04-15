package com.dino.presentation.controllers;

import com.dino.application.services.LobbySignal;
import com.dino.application.services.SubscriptionGroup;
import com.dino.presentation.flow.LobbyScreenFlow;
import com.dino.presentation.render.LobbyPreviewRenderer;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Controlador de la sala de espera previa a la partida.
 *
 * <p>Sincroniza la lista de jugadores y delega el protocolo UDP del lobby en
 * {@link LobbyScreenFlow}. El controlador conserva solo la gestión de la
 * vista y el timer JavaFX-friendly.</p>
 */
public class LobbyController implements Initializable, LobbyScreenFlowAware, SceneLifecycleAware {
    @FXML private Canvas lobbyPreviewCanvas;
    @FXML private StackPane lobbyCanvasPane;
    @FXML private ListView<String> playersList;
    @FXML private Button readyBtn;
    @FXML private Button startBtn;
    @FXML private Label hostInfoLabel;
    @FXML private Label statusLabel;
    @FXML private Label connectedSummaryLabel;
    @FXML private Label readySummaryLabel;
    @FXML private Label missingSummaryLabel;

    private LobbyScreenFlow lobbyScreenFlow;
    private final SubscriptionGroup subscriptions = new SubscriptionGroup();
    private final LobbyPreviewRenderer previewRenderer = new LobbyPreviewRenderer();
    private Timer networkTimer;
    private AnimationTimer previewLoop;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLobbyScreenFlow(LobbyScreenFlow lobbyScreenFlow) {
        this.lobbyScreenFlow = lobbyScreenFlow;
    }

    /**
     * Configura los widgets base de la escena del lobby.
     *
     * <p>Las suscripciones y el polling de red arrancan después, cuando la
     * escena queda activa, para poder liberarlos limpiamente al abandonarla.</p>
     *
     * @param url ubicación del recurso FXML, si JavaFX la proporciona
     * @param rb bundle de recursos asociado a la vista, si existe
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        startBtn.setVisible(lobbyScreenFlow.isHost());
        if (lobbyScreenFlow.isHost()) {
            hostInfoLabel.setText("Tu IP: " + lobbyScreenFlow.localIp()
                + "   Puerto: " + lobbyScreenFlow.localPort());
            hostInfoLabel.setVisible(true);
        }
        lobbyPreviewCanvas.setManaged(false);
        lobbyCanvasPane.widthProperty().addListener((obs, old, w) -> syncPreviewCanvasSize());
        lobbyCanvasPane.heightProperty().addListener((obs, old, h) -> syncPreviewCanvasSize());
        Platform.runLater(this::syncPreviewCanvasSize);
        refreshLobbyView();
    }

    /**
     * Arranca el ciclo de vida activo del lobby actual.
     */
    @Override
    public void onSceneShown() {
        lobbyScreenFlow.activate();
        subscriptions.add(lobbyScreenFlow.bindLobbyUpdates(this::refreshLobbyView));
        refreshLobbyView();
        startPreviewLoop();
        startNetworkLoop();
    }

    /**
     * Libera listeners y timers temporales asociados al lobby.
     */
    @Override
    public void onSceneHidden() {
        subscriptions.clear();
        if (networkTimer != null) {
            networkTimer.cancel();
            networkTimer = null;
        }
        if (previewLoop != null) {
            previewLoop.stop();
            previewLoop = null;
        }
    }

    /**
     * Reconstituye la lista visible de jugadores a partir del snapshot actual.
     */
    private void refreshLobbyView() {
        Platform.runLater(() -> {
            playersList.getItems().setAll(lobbyScreenFlow.playerEntries());
            updateSummaryLabels();
            updateReadyButton();
            renderLobbyPreview(0);
        });
    }

    /**
     * Alterna el estado READY del jugador local y actualiza el botón.
     */
    @FXML
    public void onListo() {
        try {
            boolean nextReadyState = !lobbyScreenFlow.isLocalPlayerReady();
            lobbyScreenFlow.setLocalReady(nextReadyState);
            statusLabel.setText(lobbyScreenFlow.readyStatusMessage());
            updateSummaryLabels();
            updateReadyButton();
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    /**
     * Inicia la simulación autoritativa del host y abre la escena de juego.
     *
     * <p>El mensaje de arranque se reenvía en ráfaga corta por UDP para reducir
     * el riesgo de que un único paquete perdido deje a un cliente atrapado en el
     * lobby.</p>
     */
    @FXML
    public void onIniciarPartida() {
        if (!lobbyScreenFlow.isHost()) return;
        try {
            lobbyScreenFlow.startGame();
            if (networkTimer != null) {
                networkTimer.cancel();
                networkTimer = null;
            }
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    /**
     * Atiende el loop de red del lobby con polling liviano.
     */
    private void startNetworkLoop() {
        networkTimer = new Timer(true);
        networkTimer.scheduleAtFixedRate(new TimerTask() {
            /**
             * Ejecuta un tick de red del lobby y despacha la señal resultante.
             */
            @Override
            public void run() {
                LobbySignal signal = lobbyScreenFlow.pollNetworkTick();
                handleSignal(signal);
            }
        }, 0, 100);
    }

    /**
     * Resuelve las transiciones de UI derivadas del protocolo de lobby.
     *
     * @param signal señal producida por el coordinador
     */
    private void handleSignal(LobbySignal signal) {
        if (signal == LobbySignal.START_GAME) {
            Platform.runLater(() -> {
                try {
                    if (networkTimer != null) {
                        networkTimer.cancel();
                        networkTimer = null;
                    }
                    lobbyScreenFlow.handleSignal(signal);
                } catch (Exception e) {
                    statusLabel.setText("Error: " + e.getMessage());
                }
            });
        }
    }

    /**
     * Arranca la animación del preview del lobby con un reloj JavaFX ligero.
     */
    private void startPreviewLoop() {
        previewLoop = new AnimationTimer() {
            /**
             * Redibuja el preview con el tiempo continuo del reloj de JavaFX.
             *
             * @param now timestamp actual en nanosegundos
             */
            @Override
            public void handle(long now) {
                renderLobbyPreview(now / 1_000_000_000.0);
            }
        };
        previewLoop.start();
    }

    /**
     * Redibuja el canvas de preview del lobby con el estado actual.
     *
     * @param timeSeconds tiempo continuo usado para animaciones suaves del preview
     */
    private void renderLobbyPreview(double timeSeconds) {
        previewRenderer.render(
            lobbyPreviewCanvas,
            lobbyScreenFlow.playerSnapshots(),
            lobbyScreenFlow.expectedPlayers(),
            timeSeconds
        );
    }

    /**
     * Sincroniza el tamaño real del canvas con el área útil del contenedor.
     *
     * <p>El canvas es unmanaged para que su tamaño no altere el pref-size del
     * StackPane y no se forme un bucle de layout al redimensionarlo.</p>
     */
    private void syncPreviewCanvasSize() {
        double targetWidth = Math.max(0, lobbyCanvasPane.getWidth()
            - lobbyCanvasPane.getInsets().getLeft() - lobbyCanvasPane.getInsets().getRight());
        double targetHeight = Math.max(0, lobbyCanvasPane.getHeight()
            - lobbyCanvasPane.getInsets().getTop() - lobbyCanvasPane.getInsets().getBottom());

        if (Double.compare(lobbyPreviewCanvas.getWidth(), targetWidth) != 0) {
            lobbyPreviewCanvas.setWidth(targetWidth);
        }
        if (Double.compare(lobbyPreviewCanvas.getHeight(), targetHeight) != 0) {
            lobbyPreviewCanvas.setHeight(targetHeight);
        }
    }

    /**
     * Sincroniza el texto y estilo del botón READY según el estado actual del jugador local.
     *
     * <p>Muestra "READY" en azul cuando el jugador aún no confirmó, y
     * "CANCELAR" en gris cuando ya está listo, para indicar la acción disponible.</p>
     */
    private void updateReadyButton() {
        boolean ready = lobbyScreenFlow.isLocalPlayerReady();
        if (ready) {
            readyBtn.setText("CANCELAR");
            readyBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #6a7d8e, #4e5f6d); "
                + "-fx-text-fill: #d8e6f0; -fx-font-weight: bold; -fx-font-size: 14px; "
                + "-fx-font-family: 'Monospaced'; -fx-padding: 12 26; -fx-background-radius: 14; -fx-cursor: hand;");
        } else {
            readyBtn.setText("READY");
            readyBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #8ae2ff, #5fc8f4); "
                + "-fx-text-fill: #0d1826; -fx-font-weight: bold; -fx-font-size: 14px; "
                + "-fx-font-family: 'Monospaced'; -fx-padding: 12 26; -fx-background-radius: 14; -fx-cursor: hand;");
        }
    }

    /**
     * Actualiza los contadores de conectados, listos y faltantes del lobby.
     */
    private void updateSummaryLabels() {
        int expectedPlayers = lobbyScreenFlow.expectedPlayers();
        int connectedPlayers = lobbyScreenFlow.connectedPlayersCount();
        int readyPlayers = lobbyScreenFlow.readyPlayersCount();
        int missingPlayers = Math.max(0, expectedPlayers - connectedPlayers);

        connectedSummaryLabel.setText("Conectados: " + connectedPlayers + "/" + expectedPlayers);
        readySummaryLabel.setText("Listos: " + readyPlayers + "/" + connectedPlayers);
        missingSummaryLabel.setText(missingPlayers == 0
            ? "Lobby completo"
            : "Faltan " + missingPlayers + " jugador(es)");
    }
}
