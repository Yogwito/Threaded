package com.dino.presentation.controllers;

import com.dino.application.runtime.AppContext;
import com.dino.config.GameConfig;
import com.dino.domain.entities.Player;
import com.dino.domain.events.EventNames;
import com.dino.infrastructure.serialization.MessageSerializer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Controlador de la sala de espera previa a la partida.
 *
 * <p>Sincroniza la lista de jugadores, procesa mensajes básicos de lobby y
 * permite marcar estado de listo o arrancar la simulación cuando la instancia
 * local es host. También actúa como puente temporal entre la capa de red UDP y
 * la transición hacia la escena principal del juego.</p>
 */
public class LobbyController implements Initializable, AppContextAware {
    @FXML private ListView<String> playersList;
    @FXML private Button startBtn;
    @FXML private Label statusLabel;

    private AppContext appContext;
    private Timer networkTimer;
    private long lastLobbyBroadcastAt = 0;
    private boolean startTransitionTriggered = false;

    @Override
    public void setAppContext(AppContext appContext) {
        this.appContext = appContext;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        startBtn.setVisible(appContext.session().isHost());

        appContext.events().subscribe(EventNames.PLAYER_JOINED, e -> refreshPlayerList());
        appContext.events().subscribe(EventNames.PLAYER_READY, e -> refreshPlayerList());
        appContext.events().subscribe(EventNames.SNAPSHOT_RECEIVED, e -> refreshPlayerList());

        refreshPlayerList();
        startNetworkLoop();
    }

    private void refreshPlayerList() {
        Platform.runLater(() -> {
            playersList.getItems().clear();
            for (Player p : appContext.session().getPlayersSnapshot()) {
                String state = p.isReady() ? " listo" : (p.isConnected() ? " conectado" : " desconectado");
                playersList.getItems().add(p.getName() + " [" + state + "]");
            }
        });
    }

    /**
     * Marca al jugador local como listo y propaga el cambio al resto de peers.
     */
    @FXML
    public void onListo() {
        if (appContext.session().isHost()) {
            appContext.session().markPlayerReady(appContext.session().getLocalPlayerId(), true);
            appContext.events().publish(EventNames.PLAYER_READY, Map.of("playerId", appContext.session().getLocalPlayerId()));
            broadcastLobbySnapshot();
            statusLabel.setText("Host listo. Esperando más jugadores...");
        } else {
            try {
                Map<String, Object> msg = appContext.serializer().build(
                    MessageSerializer.READY,
                    "playerId", appContext.session().getLocalPlayerId()
                );
                appContext.networkPeer().send(msg,
                    InetAddress.getByName(appContext.session().getHostIp()),
                    appContext.session().getHostPort());
                statusLabel.setText("Listo! Esperando al host...");
            } catch (Exception e) {
                statusLabel.setText("Error: " + e.getMessage());
            }
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
        if (!appContext.session().isHost()) return;
        try {
            var hostMatchService = appContext.createHostMatchService();
            hostMatchService.initWorld();
            Map<String, Object> startMessage = appContext.session().getSnapshotData();
            startMessage.put("type", MessageSerializer.START_GAME);
            appContext.networkPeer().broadcastBurst(startMessage, appContext.session().getRemotePeerAddresses(),
                GameConfig.CRITICAL_BROADCAST_REPEATS, GameConfig.CRITICAL_BROADCAST_DELAY_MS);
            appContext.events().publish(EventNames.GAME_STARTED, Map.of());
            if (networkTimer != null) networkTimer.cancel();
            appContext.navigator().showGame();
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
                if (appContext.networkPeer() == null || !appContext.networkPeer().isBound()) return;
                if (appContext.session().isHost()) broadcastLobbySnapshotIfDue();
                var received = appContext.networkPeer().receive();
                received.ifPresent(entry -> handleMessage(entry.getKey(), entry.getValue()));
            }
        }, 0, 100);
    }

    private void handleMessage(Map<String, Object> msg, InetSocketAddress sender) {
        String type = (String) msg.get("type");
        if (type == null) return;

        if (appContext.session().isHost()) {
            handleHostMessage(type, msg, sender);
            return;
        }

        if (MessageSerializer.START_GAME.equals(type)) {
            if (startTransitionTriggered) return;
            startTransitionTriggered = true;
            appContext.session().updateFromSnapshot(msg);
            Platform.runLater(() -> {
                try {
                    if (networkTimer != null) networkTimer.cancel();
                    appContext.events().publish(EventNames.GAME_STARTED, Map.of());
                    appContext.navigator().showGame();
                } catch (Exception e) {
                    statusLabel.setText("Error: " + e.getMessage());
                }
            });
        } else if (MessageSerializer.LOBBY_SNAPSHOT.equals(type)) {
            appContext.session().updateFromSnapshot(msg);
        }
    }

    private void handleHostMessage(String type, Map<String, Object> msg, InetSocketAddress sender) {
        if (MessageSerializer.JOIN.equals(type)) {
            String playerId = (String) msg.get("playerId");
            String name = (String) msg.getOrDefault("name", "Jugador");
            if (playerId == null || playerId.isBlank()) return;

            Player player = appContext.session().getPlayers().get(playerId);
            if (player == null) {
                player = new Player(playerId, name, nextPlayerColor());
                appContext.session().addPlayer(player);
            } else {
                player.setName(name);
                player.setConnected(true);
            }
            appContext.session().registerPeerAddress(playerId, sender);
            appContext.events().publish(EventNames.PLAYER_JOINED, Map.of("playerId", playerId, "name", name));
            broadcastLobbySnapshot();
        } else if (MessageSerializer.READY.equals(type)) {
            String playerId = (String) msg.get("playerId");
            appContext.session().markPlayerReady(playerId, true);
            appContext.events().publish(EventNames.PLAYER_READY, msg);
            Platform.runLater(() -> statusLabel.setText("Jugador listo: " + msg.getOrDefault("playerId", "?")));
            broadcastLobbySnapshot();
        } else if (MessageSerializer.DISCONNECT.equals(type)) {
            String playerId = (String) msg.get("playerId");
            appContext.session().removePlayer(playerId);
            Platform.runLater(() -> statusLabel.setText("Jugador desconectado: " + msg.getOrDefault("playerId", "?")));
            broadcastLobbySnapshot();
        }
    }

    private void broadcastLobbySnapshotIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastLobbyBroadcastAt < 250) return;
        broadcastLobbySnapshot();
        lastLobbyBroadcastAt = now;
    }

    private void broadcastLobbySnapshot() {
        List<InetSocketAddress> remotes = appContext.session().getRemotePeerAddresses();
        if (remotes.isEmpty()) return;
        Map<String, Object> snapshot = appContext.session().getSnapshotData();
        snapshot.put("type", MessageSerializer.LOBBY_SNAPSHOT);
        appContext.networkPeer().broadcast(snapshot, remotes);
    }

    private String nextPlayerColor() {
        String[] colors = {"red", "blue", "green", "yellow"};
        int index = Math.max(0, appContext.session().getPlayers().size()) % colors.length;
        return colors[index];
    }
}
