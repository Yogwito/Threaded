package com.dino.presentation.controllers;

import com.dino.MainApp;
import com.dino.config.GameConfig;
import com.dino.domain.entities.ButtonSwitch;
import com.dino.domain.entities.CollectibleItem;
import com.dino.domain.entities.Door;
import com.dino.domain.entities.ExitZone;
import com.dino.domain.entities.PlatformTile;
import com.dino.domain.entities.Player;
import com.dino.domain.entities.PushBlock;
import com.dino.domain.events.EventNames;
import com.dino.infrastructure.serialization.MessageSerializer;
import com.dino.presentation.render.PixelArtTheme;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Controlador principal de la partida en curso.
 *
 * <p>Coordina el loop visual, el polling de red, el envío de input local, el
 * render del mundo y la actualización de la HUD. Cuando la instancia es host,
 * además dispara la simulación autoritativa y la emisión periódica de
 * snapshots; cuando es cliente, consume snapshots y refleja el estado
 * replicado.</p>
 */
public class GameController implements Initializable {
    @FXML private Canvas arenaCanvas;
    @FXML private Label timerLabel;
    @FXML private Label levelLabel;
    @FXML private Label roomStatusLabel;
    @FXML private Label threadLabel;
    @FXML private Label networkLabel;
    @FXML private ListView<String> playersList;
    @FXML private ListView<String> eventLog;
    @FXML private Label feedbackLabel;

    private static final double SNAPSHOT_INTERVAL = 1.0 / 20.0;
    private static final double CAMERA_SMOOTHING = 0.16;
    private static final double FEEDBACK_DURATION_SECONDS = 1.4;

    private final MessageSerializer serializer = new MessageSerializer();
    private AnimationTimer gameLoop;
    private long lastNano = 0;
    private double snapshotTimer = 0;
    private double feedbackTimer = 0;
    private double cameraX = 0;
    private double cameraY = 0;
    private double currentZoom = GameConfig.BASE_ZOOM;
    private double lastWorldMouseX = 0;
    private double lastWorldMouseY = 0;
    private InetAddress hostAddress;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        MainApp.eventBus.subscribe(EventNames.SNAPSHOT_RECEIVED, e -> Platform.runLater(this::refreshUI));
        MainApp.eventBus.subscribe(EventNames.GAME_OVER, e -> {
            if (MainApp.sessionService.isHost()) broadcastGameOver();
            Platform.runLater(this::onGameOver);
        });
        MainApp.eventBus.subscribe(EventNames.COIN_COLLECTED, e -> onGameplayEvent(e, "Moneda recogida", "#ffd166"));
        MainApp.eventBus.subscribe(EventNames.PLAYER_JUMPED, e -> onGameplayEvent(e, "Salto", "#8be9fd"));
        MainApp.eventBus.subscribe(EventNames.ROOM_RESET, e -> Platform.runLater(() ->
            showFeedback(String.valueOf(e.getOrDefault("reason", "Sala reiniciada")), "#ff9f68")));
        MainApp.eventBus.subscribe(EventNames.LEVEL_ADVANCED, e -> Platform.runLater(() ->
            showFeedback("Nivel completado", "#80ed99")));

        feedbackLabel.setVisible(false);
        applyHudStyles();

        arenaCanvas.setOnMouseMoved(ev -> {
            lastWorldMouseX = canvasToWorldX(ev.getX());
            lastWorldMouseY = canvasToWorldY(ev.getY());
            sendAim(lastWorldMouseX, lastWorldMouseY);
        });
        arenaCanvas.setOnMouseDragged(ev -> {
            lastWorldMouseX = canvasToWorldX(ev.getX());
            lastWorldMouseY = canvasToWorldY(ev.getY());
            sendAim(lastWorldMouseX, lastWorldMouseY);
        });
        arenaCanvas.setOnMousePressed(ev -> {
            if (ev.getButton() == MouseButton.PRIMARY) sendJump();
        });

        try {
            hostAddress = InetAddress.getByName(MainApp.sessionService.getHostIp());
        } catch (Exception e) {
            System.err.println("[GameController] Cannot resolve host IP: " + e.getMessage());
        }

        refreshUI();
        startGameLoop();
    }

    private void startGameLoop() {
        gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNano == 0) {
                    lastNano = now;
                    return;
                }

                double dt = (now - lastNano) / 1_000_000_000.0;
                lastNano = now;

                if (MainApp.udpPeer != null && MainApp.udpPeer.isBound()) {
                    pollIncomingMessages();
                }

                if (MainApp.sessionService.isHost() && MainApp.hostMatchService != null) {
                    MainApp.hostMatchService.tick(dt);
                    snapshotTimer += dt;
                    if (snapshotTimer >= SNAPSHOT_INTERVAL) {
                        snapshotTimer = 0;
                        Map<String, Object> snapshot = MainApp.sessionService.getSnapshotData();
                        snapshot.put("type", MessageSerializer.SNAPSHOT);
                        MainApp.sessionService.updateFromSnapshot(snapshot);
                        MainApp.udpPeer.broadcast(snapshot, MainApp.sessionService.getRemotePeerAddresses());
                    }
                }

                if (!MainApp.sessionService.isHost() && lastWorldMouseX != 0) {
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

    private void sendAim(double worldX, double worldY) {
        String localPlayerId = MainApp.sessionService.getLocalPlayerId();
        if (localPlayerId == null) return;

        if (MainApp.sessionService.isHost()) {
            if (MainApp.hostMatchService != null) {
                MainApp.hostMatchService.handleMoveTarget(localPlayerId, worldX, worldY);
            }
            return;
        }

        try {
            Map<String, Object> msg = serializer.build(
                MessageSerializer.MOVE_TARGET,
                "playerId", localPlayerId,
                "targetX", worldX,
                "targetY", worldY
            );
            MainApp.udpPeer.send(msg,
                hostAddress,
                MainApp.sessionService.getHostPort());
        } catch (Exception e) {
            System.err.println("[GameController] Aim error: " + e.getMessage());
        }
    }

    private void sendJump() {
        String localPlayerId = MainApp.sessionService.getLocalPlayerId();
        if (localPlayerId == null) return;

        if (MainApp.sessionService.isHost()) {
            if (MainApp.hostMatchService != null) {
                MainApp.hostMatchService.handleJump(localPlayerId);
            }
            return;
        }

        try {
            Map<String, Object> msg = serializer.build(
                MessageSerializer.JUMP,
                "playerId", localPlayerId
            );
            MainApp.udpPeer.send(msg,
                hostAddress,
                MainApp.sessionService.getHostPort());
        } catch (Exception e) {
            System.err.println("[GameController] Jump error: " + e.getMessage());
        }
    }

    private void pollIncomingMessages() {
        while (true) {
            var received = MainApp.udpPeer.receive();
            if (received.isEmpty()) return;
            handleIncomingMessage(received.get().getKey(), received.get().getValue());
        }
    }

    private void handleIncomingMessage(Map<String, Object> msg, InetSocketAddress sender) {
        Object type = msg.get("type");
        if (!(type instanceof String messageType)) return;

        if (MainApp.sessionService.isHost()) {
            handleHostNetworkMessage(messageType, msg, sender);
            return;
        }

        if (MessageSerializer.SNAPSHOT.equals(messageType)) {
            MainApp.sessionService.updateFromSnapshot(msg);
            MainApp.eventBus.publish(com.dino.domain.events.EventNames.SNAPSHOT_RECEIVED, msg);
        } else if (MessageSerializer.GAME_OVER.equals(messageType)) {
            MainApp.sessionService.updateFromSnapshot(msg);
            Platform.runLater(this::onGameOver);
        }
    }

    private void handleHostNetworkMessage(String type, Map<String, Object> msg, InetSocketAddress sender) {
        if (MainApp.hostMatchService == null) return;

        if (MessageSerializer.MOVE_TARGET.equals(type)) {
            String playerId = (String) msg.get("playerId");
            Number targetX = (Number) msg.get("targetX");
            Number targetY = (Number) msg.get("targetY");
            if (playerId != null && targetX != null && targetY != null) {
                MainApp.sessionService.registerPeerAddress(playerId, sender);
                MainApp.hostMatchService.handleMoveTarget(playerId, targetX.doubleValue(), targetY.doubleValue());
            }
            return;
        }

        if (MessageSerializer.JUMP.equals(type)) {
            String playerId = (String) msg.get("playerId");
            if (playerId != null) {
                MainApp.sessionService.registerPeerAddress(playerId, sender);
                MainApp.hostMatchService.handleJump(playerId);
            }
            return;
        }

        if (MessageSerializer.DISCONNECT.equals(type)) {
            String playerId = (String) msg.get("playerId");
            MainApp.sessionService.markPlayerConnected(playerId, false);
            MainApp.sessionService.removePeerAddress(playerId);
            Map<String, Object> snapshot = MainApp.sessionService.getSnapshotData();
            snapshot.put("type", MessageSerializer.SNAPSHOT);
            MainApp.sessionService.updateFromSnapshot(snapshot);
            MainApp.udpPeer.broadcast(snapshot, MainApp.sessionService.getRemotePeerAddresses());
        }
    }

    private void broadcastGameOver() {
        if (MainApp.udpPeer == null || !MainApp.udpPeer.isBound()) return;
        Map<String, Object> payload = MainApp.sessionService.getSnapshotData();
        payload.put("type", MessageSerializer.GAME_OVER);
        MainApp.udpPeer.broadcast(payload, MainApp.sessionService.getRemotePeerAddresses());
    }

    private double canvasToWorldX(double canvasX) {
        double scaleX = arenaCanvas.getWidth() / getViewportWorldWidth();
        return cameraX + (canvasX / scaleX);
    }

    private double canvasToWorldY(double canvasY) {
        double scaleY = arenaCanvas.getHeight() / getViewportWorldHeight();
        return cameraY + (canvasY / scaleY);
    }

    private void render() {
        double canvasWidth = arenaCanvas.getWidth();
        double canvasHeight = arenaCanvas.getHeight();
        double viewportWidth = getViewportWorldWidth();
        double viewportHeight = getViewportWorldHeight();
        double scaleX = canvasWidth / viewportWidth;
        double scaleY = canvasHeight / viewportHeight;
        int tileSize = Math.max(16, MainApp.sessionService.getCurrentTileSize());
        PixelArtTheme.Palette palette = PixelArtTheme.paletteFor(MainApp.sessionService.getCurrentBackground());

        GraphicsContext gc = arenaCanvas.getGraphicsContext2D();
        drawBackground(gc, palette, canvasWidth, canvasHeight, scaleX, scaleY, tileSize);

        gc.setStroke(PixelArtTheme.GRID);
        gc.setLineWidth(1);
        for (double x = 0; x <= GameConfig.LEVEL_WIDTH; x += tileSize) {
            double sx = worldToScreenX(x, scaleX);
            if (sx >= 0 && sx <= canvasWidth) gc.strokeLine(sx, 0, sx, canvasHeight);
        }
        for (double y = 0; y <= GameConfig.LEVEL_HEIGHT; y += tileSize) {
            double sy = worldToScreenY(y, scaleY);
            if (sy >= 0 && sy <= canvasHeight) gc.strokeLine(0, sy, canvasWidth, sy);
        }

        gc.setStroke(PixelArtTheme.WORLD_BORDER);
        gc.setLineWidth(2);
        gc.strokeRect(worldToScreenX(0, scaleX), worldToScreenY(0, scaleY),
            GameConfig.LEVEL_WIDTH * scaleX, GameConfig.LEVEL_HEIGHT * scaleY);

        for (PlatformTile platform : MainApp.sessionService.getPlatformsSnapshot()) {
            drawPlatform(gc, platform, scaleX, scaleY, palette, false);
        }

        for (PlatformTile platform : MainApp.sessionService.getSpecialPlatformsSnapshot()) {
            drawPlatform(gc, platform, scaleX, scaleY, palette, true);
        }

        for (PlatformTile hazard : MainApp.sessionService.getHazardsSnapshot()) {
            drawHazard(gc, hazard, scaleX, scaleY, palette);
        }

        for (PlatformTile checkpoint : MainApp.sessionService.getCheckpointsSnapshot()) {
            drawCheckpoint(gc, checkpoint, scaleX, scaleY, palette);
        }

        for (PushBlock block : MainApp.sessionService.getPushBlocksSnapshot()) {
            drawPushBlock(gc, block, scaleX, scaleY, palette);
        }

        ButtonSwitch button = MainApp.sessionService.getButtonSwitchSnapshot();
        if (button != null) drawButton(gc, button, scaleX, scaleY, palette);

        Door door = MainApp.sessionService.getDoorSnapshot();
        if (door != null) drawDoor(gc, door, scaleX, scaleY, palette);

        ExitZone exitZone = MainApp.sessionService.getExitZoneSnapshot();
        if (exitZone != null) drawExit(gc, exitZone, scaleX, scaleY, palette);

        for (CollectibleItem coin : MainApp.sessionService.getCoinsSnapshot()) {
            if (coin.isActive()) drawCoin(gc, coin, scaleX, scaleY, palette);
        }

        List<Player> allPlayers = MainApp.sessionService.getPlayersSnapshot();
        drawThread(gc, allPlayers, scaleX, scaleY);

        Player localPlayer = getLocalPlayerSnapshot();
        int playerIndex = 0;
        for (Player player : allPlayers) {
            if (!player.isConnected()) continue;
            drawPlayer(gc, player, localPlayer, playerIndex++, scaleX, scaleY, palette);
        }
    }

    private void drawThread(GraphicsContext gc, List<Player> players, double scaleX, double scaleY) {
        List<Player> connected = players.stream()
            .filter(p -> p.isConnected() && p.isAlive())
            .sorted((a, b) -> Double.compare(a.getX(), b.getX()))
            .toList();

        for (int i = 0; i < connected.size() - 1; i++) {
            Player a = connected.get(i);
            Player b = connected.get(i + 1);

            double ax = worldToScreenX(a.getCenterX(), scaleX);
            double ay = worldToScreenY(a.getCenterY(), scaleY);
            double bx = worldToScreenX(b.getCenterX(), scaleX);
            double by = worldToScreenY(b.getCenterY(), scaleY);

            double dx = b.getCenterX() - a.getCenterX();
            double dy = b.getCenterY() - a.getCenterY();
            double dist = Math.sqrt(dx * dx + dy * dy);

            Color threadColor;
            double lineWidth;
            if (dist >= GameConfig.THREAD_CRITICAL_DISTANCE) {
                threadColor = Color.web("#e05555");
                lineWidth = 3.0;
            } else if (dist >= GameConfig.THREAD_TENSE_DISTANCE) {
                threadColor = Color.web("#e0a030");
                lineWidth = 2.5;
            } else if (dist >= GameConfig.THREAD_REST_DISTANCE) {
                threadColor = Color.web("#d4c87a");
                lineWidth = 2.0;
            } else {
                threadColor = Color.web("#8ab06e", 0.7);
                lineWidth = 1.5;
            }

            gc.setStroke(PixelArtTheme.INK.deriveColor(0, 1, 1, 0.5));
            gc.setLineWidth(lineWidth + 2);
            gc.strokeLine(ax, ay, bx, by);

            gc.setStroke(threadColor);
            gc.setLineWidth(lineWidth);
            gc.strokeLine(ax, ay, bx, by);
        }
    }

    private void drawBackground(GraphicsContext gc, PixelArtTheme.Palette palette,
                                double canvasWidth, double canvasHeight, double scaleX, double scaleY,
                                int tileSize) {
        String biome = MainApp.sessionService.getCurrentBackground();
        if (biome == null) biome = "default";

        gc.setFill(palette.backgroundFar());
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        gc.setFill(palette.backgroundMid());
        switch (biome) {
            case "forest" -> {
                drawParallaxBand(gc, scaleX, scaleY, tileSize, new double[][]{
                    {0, 7, 2, 7}, {3, 6, 3, 8}, {8, 7, 2, 7}, {12, 5, 3, 9}, {17, 7, 2, 7}, {21, 6, 3, 8}
                });
                gc.setFill(palette.backgroundNear());
                drawParallaxBand(gc, scaleX, scaleY, tileSize, new double[][]{
                    {1, 8, 2, 6}, {5, 7, 2, 7}, {10, 8, 2, 6}, {14, 6, 2, 8}, {18, 8, 2, 6}, {23, 7, 2, 7}
                });
                drawBackgroundMotif(gc, scaleX, scaleY, tileSize, palette.platformDetail(), new double[][]{
                    {2, 8, 1, 3}, {6, 7, 1, 4}, {11, 8, 1, 3}, {15, 7, 1, 4}, {19, 8, 1, 3}, {24, 7, 1, 4}
                });
            }
            case "desert" -> {
                drawParallaxBand(gc, scaleX, scaleY, tileSize, new double[][]{
                    {0, 9, 5, 5}, {6, 8, 4, 6}, {12, 9, 5, 5}, {18, 7, 5, 7}, {24, 9, 4, 5}
                });
                gc.setFill(palette.backgroundNear());
                drawParallaxBand(gc, scaleX, scaleY, tileSize, new double[][]{
                    {2, 10, 4, 4}, {9, 9, 4, 5}, {15, 10, 4, 4}, {22, 8, 4, 6}
                });
                drawBackgroundMotif(gc, scaleX, scaleY, tileSize, palette.platformDetail(), new double[][]{
                    {3, 10, 1, 2}, {10, 9, 1, 3}, {16, 10, 1, 2}, {23, 9, 1, 3}
                });
            }
            case "cave" -> {
                drawParallaxBand(gc, scaleX, scaleY, tileSize, new double[][]{
                    {0, 8, 4, 6}, {5, 9, 3, 5}, {10, 7, 4, 7}, {15, 8, 3, 6}, {20, 6, 4, 8}, {25, 8, 3, 6}
                });
                gc.setFill(palette.backgroundNear());
                drawParallaxBand(gc, scaleX, scaleY, tileSize, new double[][]{
                    {2, 10, 2, 4}, {7, 9, 2, 5}, {12, 10, 2, 4}, {17, 9, 2, 5}, {22, 10, 2, 4}
                });
                drawBackgroundMotif(gc, scaleX, scaleY, tileSize, palette.platformDetail(), new double[][]{
                    {3, 9, 1, 1}, {8, 10, 1, 1}, {13, 9, 1, 1}, {18, 10, 1, 1}, {23, 9, 1, 1}
                });
            }
            case "snow" -> {
                drawParallaxBand(gc, scaleX, scaleY, tileSize, new double[][]{
                    {0, 9, 4, 5}, {5, 8, 3, 6}, {10, 9, 4, 5}, {15, 7, 3, 7}, {20, 9, 4, 5}, {25, 8, 3, 6}
                });
                gc.setFill(palette.backgroundNear());
                drawParallaxBand(gc, scaleX, scaleY, tileSize, new double[][]{
                    {1, 10, 3, 4}, {7, 9, 3, 5}, {13, 10, 3, 4}, {19, 9, 3, 5}, {24, 10, 3, 4}
                });
                drawBackgroundMotif(gc, scaleX, scaleY, tileSize, palette.platformDetail(), new double[][]{
                    {2, 10, 1, 1}, {8, 9, 1, 1}, {14, 10, 1, 1}, {20, 9, 1, 1}, {25, 10, 1, 1}
                });
            }
            default -> {
                drawParallaxBand(gc, scaleX, scaleY, tileSize, new double[][]{
                    {0, 8, 4, 6}, {5, 7, 4, 7}, {11, 9, 4, 5}, {17, 7, 4, 7}, {23, 8, 4, 6}
                });
                gc.setFill(palette.backgroundNear());
                drawParallaxBand(gc, scaleX, scaleY, tileSize, new double[][]{
                    {2, 10, 3, 4}, {8, 9, 3, 5}, {14, 10, 3, 4}, {20, 9, 3, 5}
                });
            }
        }
    }

    private void drawParallaxBand(GraphicsContext gc, double scaleX, double scaleY, int tileSize,
                                  double[][] segments) {
        for (double[] segment : segments) {
            double sx = snap(worldToScreenX(segment[0] * tileSize, scaleX));
            double sy = snap(worldToScreenY(segment[1] * tileSize, scaleY));
            double sw = snap(segment[2] * tileSize * scaleX);
            double sh = snap(segment[3] * tileSize * scaleY);
            gc.fillRect(sx, sy, sw, sh);
        }
    }

    private void drawBackgroundMotif(GraphicsContext gc, double scaleX, double scaleY, int tileSize,
                                     Color color, double[][] motifs) {
        gc.setFill(color);
        for (double[] motif : motifs) {
            double sx = snap(worldToScreenX(motif[0] * tileSize, scaleX));
            double sy = snap(worldToScreenY(motif[1] * tileSize, scaleY));
            double sw = snap(motif[2] * tileSize * scaleX);
            double sh = snap(motif[3] * tileSize * scaleY);
            gc.fillRect(sx, sy, sw, sh);
        }
    }

    private void drawPlatform(GraphicsContext gc, PlatformTile platform, double scaleX, double scaleY,
                              PixelArtTheme.Palette palette, boolean special) {
        double sx = snap(worldToScreenX(platform.getX(), scaleX));
        double sy = snap(worldToScreenY(platform.getY(), scaleY));
        double sw = snap(platform.getWidth() * scaleX);
        double sh = snap(platform.getHeight() * scaleY);
        Color top = special ? palette.special() : palette.platformTop();
        Color face = special ? palette.special().darker() : palette.platformFace();
        gc.setFill(face);
        gc.fillRect(sx, sy, sw, sh);
        gc.setFill(top);
        double topBand = snap(Math.max(PixelArtTheme.BASE_PIXEL * 2, sh * 0.25));
        gc.fillRect(sx, sy, sw, topBand);
        fillTilePattern(gc, sx, sy + topBand, sw, Math.max(PixelArtTheme.BASE_PIXEL, sh - topBand),
            palette.platformDetail(), PixelArtTheme.BASE_PIXEL * 3);
        gc.setStroke(PixelArtTheme.WORLD_BORDER);
        gc.setLineWidth(1);
        gc.strokeRect(sx + 0.5, sy + 0.5, sw - 1, sh - 1);
    }

    private void drawHazard(GraphicsContext gc, PlatformTile hazard, double scaleX, double scaleY,
                            PixelArtTheme.Palette palette) {
        double sx = snap(worldToScreenX(hazard.getX(), scaleX));
        double sy = snap(worldToScreenY(hazard.getY(), scaleY));
        double sw = snap(hazard.getWidth() * scaleX);
        double sh = snap(hazard.getHeight() * scaleY);
        gc.setFill(PixelArtTheme.INK);
        gc.fillRect(sx, sy, sw, sh);
        gc.setFill(palette.hazard());
        double spikeW = PixelArtTheme.BASE_PIXEL * 2;
        for (double px = sx; px < sx + sw; px += spikeW * 2) {
            gc.fillRect(px, sy + sh - 12, spikeW, 12);
            gc.fillRect(px + PixelArtTheme.BASE_PIXEL, sy + sh - 18, PixelArtTheme.BASE_PIXEL * 2, 6);
        }
        gc.setStroke(PixelArtTheme.WORLD_BORDER);
        gc.setLineWidth(1);
        gc.strokeRect(sx + 0.5, sy + 0.5, sw - 1, sh - 1);
    }

    private void drawCheckpoint(GraphicsContext gc, PlatformTile checkpoint, double scaleX, double scaleY,
                                PixelArtTheme.Palette palette) {
        double sx = snap(worldToScreenX(checkpoint.getX(), scaleX));
        double sy = snap(worldToScreenY(checkpoint.getY(), scaleY));
        double sw = snap(checkpoint.getWidth() * scaleX);
        double sh = snap(checkpoint.getHeight() * scaleY);
        gc.setFill(PixelArtTheme.INK);
        gc.fillRect(sx, sy, sw, sh);
        gc.setFill(palette.checkpoint());
        double poleX = snap(sx + sw * 0.5 - PixelArtTheme.BASE_PIXEL);
        gc.fillRect(poleX, sy + 4, PixelArtTheme.BASE_PIXEL * 2, sh - 8);
        gc.fillRect(poleX + PixelArtTheme.BASE_PIXEL * 2, sy + 8, PixelArtTheme.BASE_PIXEL * 3, PixelArtTheme.BASE_PIXEL * 2);
        gc.setStroke(PixelArtTheme.WORLD_BORDER);
        gc.setLineWidth(1);
        gc.strokeRect(sx + 0.5, sy + 0.5, sw - 1, sh - 1);
    }

    private void drawPushBlock(GraphicsContext gc, PushBlock block, double scaleX, double scaleY,
                               PixelArtTheme.Palette palette) {
        double sx = snap(worldToScreenX(block.getX(), scaleX));
        double sy = snap(worldToScreenY(block.getY(), scaleY));
        double sw = snap(block.getWidth() * scaleX);
        double sh = snap(block.getHeight() * scaleY);
        gc.setFill(palette.crateDark());
        gc.fillRect(sx, sy, sw, sh);
        gc.setFill(palette.crateLight());
        gc.fillRect(sx + 4, sy + 4, sw - 8, PixelArtTheme.BASE_PIXEL);
        gc.fillRect(sx + 4, sy + sh - 8, sw - 8, PixelArtTheme.BASE_PIXEL);
        gc.fillRect(sx + 4, sy + 4, PixelArtTheme.BASE_PIXEL, sh - 8);
        gc.fillRect(sx + sw - 8, sy + 4, PixelArtTheme.BASE_PIXEL, sh - 8);
        gc.fillRect(sx + sw * 0.5 - 2, sy + 4, PixelArtTheme.BASE_PIXEL, sh - 8);
        gc.fillRect(sx + 4, sy + sh * 0.5 - 2, sw - 8, PixelArtTheme.BASE_PIXEL);
        gc.setStroke(PixelArtTheme.WORLD_BORDER);
        gc.setLineWidth(1);
        gc.strokeRect(sx + 0.5, sy + 0.5, sw - 1, sh - 1);
    }

    private void drawButton(GraphicsContext gc, ButtonSwitch button, double scaleX, double scaleY,
                            PixelArtTheme.Palette palette) {
        double sx = snap(worldToScreenX(button.getX(), scaleX));
        double sy = snap(worldToScreenY(button.getY(), scaleY));
        double sw = snap(button.getWidth() * scaleX);
        double sh = snap(button.getHeight() * scaleY);
        Color buttonColor = button.isPressed() ? palette.checkpoint() : palette.hazard();
        gc.setFill(PixelArtTheme.INK);
        gc.fillRect(sx, sy, sw, sh);
        gc.setFill(buttonColor);
        gc.fillRect(sx + 4, sy + 4, sw - 8, Math.max(PixelArtTheme.BASE_PIXEL, sh - 8));
        gc.setStroke(PixelArtTheme.WORLD_BORDER);
        gc.setLineWidth(1);
        gc.strokeRect(sx + 0.5, sy + 0.5, sw - 1, sh - 1);
    }

    private void drawDoor(GraphicsContext gc, Door door, double scaleX, double scaleY,
                          PixelArtTheme.Palette palette) {
        double sx = snap(worldToScreenX(door.getX(), scaleX));
        double sy = snap(worldToScreenY(door.getY(), scaleY));
        double sw = snap(door.getWidth() * scaleX);
        double sh = snap(door.getHeight() * scaleY);
        gc.setFill(door.isOpen() ? palette.backgroundMid() : palette.crateDark());
        gc.fillRect(sx, sy, sw, sh);
        if (!door.isOpen()) {
            gc.setFill(palette.crateLight());
            for (double py = sy + 6; py < sy + sh - 6; py += PixelArtTheme.BASE_PIXEL * 4) {
                gc.fillRect(sx + 6, py, sw - 12, PixelArtTheme.BASE_PIXEL);
            }
        }
        gc.setStroke(PixelArtTheme.WORLD_BORDER);
        gc.setLineWidth(1);
        gc.strokeRect(sx + 0.5, sy + 0.5, sw - 1, sh - 1);
    }

    private void drawExit(GraphicsContext gc, ExitZone exitZone, double scaleX, double scaleY,
                          PixelArtTheme.Palette palette) {
        double sx = snap(worldToScreenX(exitZone.getX(), scaleX));
        double sy = snap(worldToScreenY(exitZone.getY(), scaleY));
        double sw = snap(exitZone.getWidth() * scaleX);
        double sh = snap(exitZone.getHeight() * scaleY);
        gc.setFill(PixelArtTheme.INK);
        gc.fillRect(sx, sy, sw, sh);
        gc.setFill(palette.goal());
        for (double px = sx + 8; px < sx + sw - 8; px += PixelArtTheme.BASE_PIXEL * 3) {
            gc.fillRect(px, sy + 8, PixelArtTheme.BASE_PIXEL, sh - 16);
        }
        gc.setStroke(PixelArtTheme.WORLD_BORDER);
        gc.setLineWidth(1);
        gc.strokeRect(sx + 0.5, sy + 0.5, sw - 1, sh - 1);
    }

    private void drawCoin(GraphicsContext gc, CollectibleItem coin, double scaleX, double scaleY,
                          PixelArtTheme.Palette palette) {
        double size = snap(GameConfig.COIN_SIZE * Math.min(scaleX, scaleY));
        double sx = snap(worldToScreenX(coin.getX(), scaleX));
        double sy = snap(worldToScreenY(coin.getY(), scaleY));
        gc.setFill(PixelArtTheme.INK);
        gc.fillRect(sx, sy, size, size);
        gc.setFill(palette.goal());
        gc.fillRect(sx + 4, sy + 4, size - 8, size - 8);
        gc.setFill(palette.platformDetail());
        gc.fillRect(sx + 4, sy + 4, size - 8, PixelArtTheme.BASE_PIXEL);
        gc.setStroke(PixelArtTheme.WORLD_BORDER);
        gc.setLineWidth(1);
        gc.strokeRect(sx + 0.5, sy + 0.5, size - 1, size - 1);
    }

    private void drawPlayer(GraphicsContext gc, Player player, Player localPlayer, int playerIndex,
                            double scaleX, double scaleY, PixelArtTheme.Palette palette) {
        double sx = snap(worldToScreenX(player.getX(), scaleX));
        double sy = snap(worldToScreenY(player.getY(), scaleY));
        double sw = snap(player.getWidth() * scaleX);
        double sh = snap(player.getHeight() * scaleY);

        double squash = player.isGrounded() ? Math.min(4, Math.abs(player.getVx()) * 0.01) : 0;
        double stretch = !player.isGrounded() ? Math.min(6, Math.abs(player.getVy()) * 0.008) : 0;
        double bodyW = snap(Math.max(24, sw + squash - stretch * 0.35));
        double bodyH = snap(Math.max(36, sh - squash + stretch));
        double bodyX = snap(sx - (bodyW - sw) / 2.0);
        double bodyY = snap(sy - (bodyH - sh) / 2.0);

        String colorName = player.getColor();
        Color primary = PixelArtTheme.resolvePlayerPrimary(colorName, playerIndex);
        Color accent = PixelArtTheme.resolvePlayerSecondary(colorName, playerIndex);
        if (PixelArtTheme.usesPrideFlag(colorName)) {
            double stripeHeight = Math.max(PixelArtTheme.BASE_PIXEL, bodyH / 6.0);
            for (int i = 0; i < 6; i++) {
                double stripeY = bodyY + (i * stripeHeight);
                double remaining = bodyY + bodyH - stripeY;
                gc.setFill(PixelArtTheme.prideStripe(i));
                gc.fillRect(bodyX, stripeY, bodyW, i == 5 ? remaining : stripeHeight);
            }
        } else {
            gc.setFill(primary);
            gc.fillRect(bodyX, bodyY, bodyW, bodyH);
        }
        gc.setFill(accent);
        gc.fillRect(bodyX + 4, bodyY + 4, bodyW - 8, PixelArtTheme.BASE_PIXEL * 2);
        gc.fillRect(bodyX + 4, bodyY + bodyH - 8, bodyW - 8, PixelArtTheme.BASE_PIXEL * 2);

        double eyeDirection = Math.signum(player.getTargetX() - player.getCenterX());
        if (eyeDirection == 0) eyeDirection = 1;
        double eyeOffset = PixelArtTheme.BASE_PIXEL * eyeDirection;
        double eyeY = bodyY + 12;

        gc.setFill(Color.WHITE);
        gc.fillRect(bodyX + 8 + eyeOffset, eyeY, 6, 6);
        gc.fillRect(bodyX + bodyW - 14 + eyeOffset, eyeY, 6, 6);
        gc.setFill(PixelArtTheme.INK);
        gc.fillRect(bodyX + 10 + eyeOffset, eyeY + 2, 2, 2);
        gc.fillRect(bodyX + bodyW - 12 + eyeOffset, eyeY + 2, 2, 2);

        gc.setFill(PixelArtTheme.INK);
        gc.fillRect(bodyX + 10, bodyY + bodyH - 14, bodyW - 20, PixelArtTheme.BASE_PIXEL / 2.0);
        gc.fillRect(bodyX + 6, bodyY + bodyH - 4, PixelArtTheme.BASE_PIXEL * 2, PixelArtTheme.BASE_PIXEL);
        gc.fillRect(bodyX + bodyW - 14, bodyY + bodyH - 4, PixelArtTheme.BASE_PIXEL * 2, PixelArtTheme.BASE_PIXEL);

        gc.setStroke(PixelArtTheme.WORLD_BORDER);
        gc.setLineWidth(localPlayer != null && player.getId().equals(localPlayer.getId()) ? 2 : 1);
        gc.strokeRect(bodyX + 0.5, bodyY + 0.5, bodyW - 1, bodyH - 1);

        if (localPlayer != null && player.getId().equals(localPlayer.getId())) {
            gc.setStroke(palette.goal());
            gc.setLineWidth(1);
            gc.strokeRect(bodyX - 4.5, bodyY - 4.5, bodyW + 8, bodyH + 8);
        }

        gc.setFill(PixelArtTheme.HUD_TEXT);
        gc.setFont(Font.font("Monospaced", 12));
        gc.fillText(player.getName(), bodyX, bodyY - 6);
    }

    private void fillTilePattern(GraphicsContext gc, double x, double y, double width, double height,
                                 Color color, double spacing) {
        gc.setFill(color);
        for (double py = y; py < y + height; py += spacing) {
            for (double px = x + ((int) ((py - y) / spacing) % 2 == 0 ? 0 : spacing / 2.0);
                 px < x + width; px += spacing) {
                gc.fillRect(px, py, PixelArtTheme.BASE_PIXEL, PixelArtTheme.BASE_PIXEL);
            }
        }
    }

    private double snap(double value) {
        return Math.rint(value / PixelArtTheme.BASE_PIXEL) * PixelArtTheme.BASE_PIXEL;
    }

    private void applyHudStyles() {
        String listStyle =
            "-fx-control-inner-background: #1f1c26;"
                + "-fx-background-color: #1f1c26;"
                + "-fx-border-color: #4d4659;"
                + "-fx-border-width: 2;"
                + "-fx-font-family: 'Monospaced';"
                + "-fx-text-fill: #e8dfcf;"
                + "-fx-highlight-fill: #4d4659;"
                + "-fx-highlight-text-fill: #e8dfcf;";
        playersList.setStyle(listStyle);
        eventLog.setStyle(listStyle);
    }

    private double worldToScreenX(double worldX, double scaleX) {
        return (worldX - cameraX) * scaleX;
    }

    private double worldToScreenY(double worldY, double scaleY) {
        return (worldY - cameraY) * scaleY;
    }

    private void updateTimer() {
        timerLabel.setText(String.format("Tiempo: %.1fs", MainApp.sessionService.getElapsedTime()));
    }

    private void refreshUI() {
        playersList.getItems().clear();
        MainApp.scoreBoardObserver.getEntries().forEach(player ->
            playersList.getItems().add(player.getName() + "  " + player.getScore() + " pts"));

        eventLog.getItems().setAll(MainApp.eventLogObserver.getEntries());

        int currentLevel = MainApp.sessionService.getCurrentLevelIndex() + 1;
        int totalLevels = Math.max(1, MainApp.sessionService.getTotalLevels());
        String levelName = MainApp.sessionService.getCurrentLevelName();
        levelLabel.setText((levelName == null || levelName.isBlank() ? "Nivel " + currentLevel : levelName)
            + "  " + currentLevel + "/" + totalLevels);

        String roomReason = MainApp.sessionService.getRoomResetReason();
        roomStatusLabel.setText(roomReason == null || roomReason.isBlank()
            ? "Llega a la meta evitando hazards"
            : roomReason);

        threadLabel.setText(String.format("Hilo maximo: %.0f px", GameConfig.THREAD_HARD_LIMIT));
        networkLabel.setText(MainApp.sessionService.isHost() ? "Host autoritativo" : "Cliente sincronizado");
    }

    private void onGameOver() {
        if (gameLoop != null) gameLoop.stop();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com.dino.views/game_over.fxml"));
            Scene scene = new Scene(loader.load(), 1280, 780);
            MainApp.getStage().setScene(scene);
        } catch (Exception e) {
            System.err.println("[GameController] Game over: " + e.getMessage());
        }
    }

    private void updateCamera(double dt) {
        Player localPlayer = getLocalPlayerSnapshot();
        if (localPlayer == null) return;

        double targetX = clampCameraX(localPlayer.getCenterX() - getViewportWorldWidth() / 2.0);
        double targetY = clampCameraY(localPlayer.getCenterY() - getViewportWorldHeight() / 2.0);
        cameraX += (targetX - cameraX) * Math.min(1.0, CAMERA_SMOOTHING * (dt * 60.0));
        cameraY += (targetY - cameraY) * Math.min(1.0, CAMERA_SMOOTHING * (dt * 60.0));
        cameraX = clampCameraX(cameraX);
        cameraY = clampCameraY(cameraY);
    }

    private void updateFeedback(double dt) {
        if (feedbackTimer <= 0) return;
        feedbackTimer = Math.max(0, feedbackTimer - dt);
        double alpha = Math.min(1.0, feedbackTimer / FEEDBACK_DURATION_SECONDS);
        feedbackLabel.setVisible(alpha > 0);
        feedbackLabel.setOpacity(alpha);
        if (alpha == 0) feedbackLabel.setText("");
    }

    private void showFeedback(String text, String colorHex) {
        feedbackTimer = FEEDBACK_DURATION_SECONDS;
        feedbackLabel.setText(text);
        feedbackLabel.setStyle(
            "-fx-text-fill: " + colorHex + "; -fx-font-size: 18px; -fx-font-weight: bold;"
                + " -fx-font-family: 'Monospaced';"
                + " -fx-background-color: #26232d; -fx-padding: 6 10;"
                + " -fx-border-color: #4d4659; -fx-border-width: 2;"
        );
        feedbackLabel.setVisible(true);
        feedbackLabel.setOpacity(1.0);
    }

    private void onGameplayEvent(Map<String, Object> payload, String text, String color) {
        if (isLocalPlayerEvent(payload, "playerId")) {
            Platform.runLater(() -> showFeedback(text, color));
        }
    }

    private boolean isLocalPlayerEvent(Map<String, Object> payload, String key) {
        String localId = MainApp.sessionService.getLocalPlayerId();
        return localId != null && localId.equals(payload.get(key));
    }

    private Player getLocalPlayerSnapshot() {
        String localId = MainApp.sessionService.getLocalPlayerId();
        if (localId == null) return null;
        List<Player> players = MainApp.sessionService.getPlayersSnapshot();
        for (Player player : players) {
            if (localId.equals(player.getId())) return player;
        }
        return null;
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
