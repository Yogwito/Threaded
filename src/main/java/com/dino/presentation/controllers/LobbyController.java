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
    @FXML private ListView<String> playersList;
    @FXML private Button startBtn;
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
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        startBtn.setVisible(lobbyScreenFlow.isHost());
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
            renderLobbyPreview(0);
        });
    }

    /**
     * Marca al jugador local como listo y propaga el cambio al resto de peers.
     */
    @FXML
    public void onListo() {
        try {
            lobbyScreenFlow.markLocalReady();
            statusLabel.setText(lobbyScreenFlow.readyStatusMessage());
            updateSummaryLabels();
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

    private void startPreviewLoop() {
        previewLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                renderLobbyPreview(now / 1_000_000_000.0);
            }
        };
        previewLoop.start();
    }

    private void renderLobbyPreview(double timeSeconds) {
        previewRenderer.render(
            lobbyPreviewCanvas,
            lobbyScreenFlow.playerSnapshots(),
            lobbyScreenFlow.expectedPlayers(),
            timeSeconds
        );
    }

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
