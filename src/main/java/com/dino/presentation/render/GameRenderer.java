package com.dino.presentation.render;

import com.dino.config.GameConfig;
import com.dino.domain.entities.ButtonSwitch;
import com.dino.domain.entities.CollectibleItem;
import com.dino.domain.entities.Door;
import com.dino.domain.entities.ExitZone;
import com.dino.domain.entities.PlatformTile;
import com.dino.domain.entities.Player;
import com.dino.domain.entities.PushBlock;
import com.dino.domain.rules.GameRules;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;

import java.util.List;

/**
 * Renderer pixel art de la escena principal del juego.
 *
 * <p>Concentra toda la lógica de dibujo del mundo para que el controlador no
 * mezcle orquestación de gameplay con detalles de rasterización JavaFX.</p>
 */
public final class GameRenderer {
    private static final Font PLAYER_NAME_FONT = Font.font("Monospaced", 12);
    private static final double VISIBILITY_MARGIN = 96.0;

    /**
     * Dibuja un frame completo del juego sobre el canvas indicado.
     *
     * @param canvas superficie de dibujo JavaFX
     * @param state snapshot de presentación del frame actual
     */
    public void render(Canvas canvas, GameRenderState state) {
        double canvasWidth = canvas.getWidth();
        double canvasHeight = canvas.getHeight();
        double scaleX = canvasWidth / state.viewportWidth();
        double scaleY = canvasHeight / state.viewportHeight();
        int tileSize = Math.max(16, state.tileSize());
        PixelArtTheme.Palette palette = PixelArtTheme.paletteFor(state.background());

        GraphicsContext gc = canvas.getGraphicsContext2D();
        drawBackground(gc, state, palette, canvasWidth, canvasHeight, scaleX, scaleY, tileSize);

        drawGrid(gc, state, canvasWidth, canvasHeight, scaleX, scaleY, tileSize);

        gc.setStroke(PixelArtTheme.WORLD_BORDER);
        gc.setLineWidth(2);
        gc.strokeRect(worldToScreenX(state, 0, scaleX), worldToScreenY(state, 0, scaleY),
            GameConfig.LEVEL_WIDTH * scaleX, GameConfig.LEVEL_HEIGHT * scaleY);

        for (PlatformTile platform : state.platforms()) {
            if (!isVisible(state, platform.getX(), platform.getY(), platform.getWidth(), platform.getHeight())) continue;
            drawPlatform(gc, state, platform, scaleX, scaleY, palette, false);
        }
        for (PlatformTile platform : state.specialPlatforms()) {
            if (!isVisible(state, platform.getX(), platform.getY(), platform.getWidth(), platform.getHeight())) continue;
            drawPlatform(gc, state, platform, scaleX, scaleY, palette, true);
        }
        for (PlatformTile hazard : state.hazards()) {
            if (!isVisible(state, hazard.getX(), hazard.getY(), hazard.getWidth(), hazard.getHeight())) continue;
            drawHazard(gc, state, hazard, scaleX, scaleY, palette);
        }
        for (PlatformTile checkpoint : state.checkpoints()) {
            if (!isVisible(state, checkpoint.getX(), checkpoint.getY(), checkpoint.getWidth(), checkpoint.getHeight())) continue;
            drawCheckpoint(gc, state, checkpoint, scaleX, scaleY, palette);
        }
        for (PushBlock block : state.pushBlocks()) {
            if (!isVisible(state, block.getX(), block.getY(), block.getWidth(), block.getHeight())) continue;
            drawPushBlock(gc, state, block, scaleX, scaleY, palette);
        }
        if (state.button() != null) {
            drawButton(gc, state, state.button(), scaleX, scaleY, palette);
        }
        if (state.door() != null) {
            drawDoor(gc, state, state.door(), scaleX, scaleY, palette);
        }
        if (state.exitZone() != null) {
            drawExit(gc, state, state.exitZone(), scaleX, scaleY, palette);
        }
        for (CollectibleItem coin : state.coins()) {
            if (coin.isActive() && isVisible(state, coin.getX(), coin.getY(), GameConfig.COIN_SIZE, GameConfig.COIN_SIZE)) {
                drawCoin(gc, state, coin, scaleX, scaleY, palette);
            }
        }

        drawThread(gc, state, state.players(), scaleX, scaleY);
        int playerIndex = 0;
        for (Player player : state.players()) {
            if (!player.isConnected()) continue;
            if (!isVisible(state, player.getX(), player.getY(), player.getWidth(), player.getHeight())) continue;
            drawPlayer(gc, state, player, state.localPlayer(), playerIndex++, scaleX, scaleY, palette);
        }
    }

    private void drawGrid(GraphicsContext gc, GameRenderState state, double canvasWidth, double canvasHeight,
                          double scaleX, double scaleY, int tileSize) {
        gc.setStroke(PixelArtTheme.GRID.deriveColor(0, 1, 1, 0.24));
        gc.setLineWidth(1);
        int startColumn = Math.max(0, (int) Math.floor(state.cameraX() / tileSize) - 1);
        int endColumn = Math.min((int) Math.ceil(GameConfig.LEVEL_WIDTH / (double) tileSize),
            (int) Math.ceil((state.cameraX() + state.viewportWidth()) / tileSize) + 1);
        for (int column = startColumn; column <= endColumn; column++) {
            double sx = worldToScreenX(state, column * tileSize, scaleX);
            if (sx >= -tileSize && sx <= canvasWidth + tileSize) {
                gc.strokeLine(sx, 0, sx, canvasHeight);
            }
        }

        int startRow = Math.max(0, (int) Math.floor(state.cameraY() / tileSize) - 1);
        int endRow = Math.min((int) Math.ceil(GameConfig.LEVEL_HEIGHT / (double) tileSize),
            (int) Math.ceil((state.cameraY() + state.viewportHeight()) / tileSize) + 1);
        for (int row = startRow; row <= endRow; row++) {
            double sy = worldToScreenY(state, row * tileSize, scaleY);
            if (sy >= -tileSize && sy <= canvasHeight + tileSize) {
                gc.strokeLine(0, sy, canvasWidth, sy);
            }
        }
    }

    /**
     * Dibuja el hilo virtual entre jugadores conectados según su tensión actual.
     */
    private void drawThread(GraphicsContext gc, GameRenderState state, List<Player> players, double scaleX, double scaleY) {
        List<Player> connected = GameRules.getConnectedPlayersInThreadOrder(players);
        for (int i = 0; i < connected.size() - 1; i++) {
            Player a = connected.get(i);
            Player b = connected.get(i + 1);

            double ax = worldToScreenX(state, a.getCenterX(), scaleX);
            double ay = worldToScreenY(state, a.getCenterY(), scaleY);
            double bx = worldToScreenX(state, b.getCenterX(), scaleX);
            double by = worldToScreenY(state, b.getCenterY(), scaleY);

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

            double nx = dist <= 0 ? 0 : -dy / dist;
            double ny = dist <= 0 ? 0 : dx / dist;

            gc.save();
            gc.setLineCap(StrokeLineCap.ROUND);
            gc.setStroke(Color.web("#05070c", 0.42));
            gc.setLineWidth(lineWidth + 4.5);
            gc.strokeLine(ax, ay, bx, by);

            gc.setStroke(threadColor.darker().deriveColor(0, 0.95, 0.82, 0.95));
            gc.setLineWidth(lineWidth + 1.4);
            gc.strokeLine(ax, ay, bx, by);

            gc.setStroke(threadColor);
            gc.setLineWidth(lineWidth);
            gc.strokeLine(ax, ay, bx, by);

            gc.setStroke(threadColor.interpolate(Color.WHITE, 0.45).deriveColor(0, 1, 1.03, 0.85));
            gc.setLineWidth(Math.max(1.1, lineWidth * 0.36));
            gc.strokeLine(ax + nx * 0.9, ay + ny * 0.9, bx + nx * 0.9, by + ny * 0.9);

            int knots = Math.max(1, (int) Math.floor(dist / 28.0));
            for (int knot = 1; knot <= knots; knot++) {
                double t = knot / (double) (knots + 1);
                double knotX = lerp(ax, bx, t);
                double knotY = lerp(ay, by, t);
                double radius = knot % 2 == 0 ? 1.9 : 2.4;
                gc.setFill(Color.web("#0b0d12", 0.68));
                gc.fillOval(knotX - radius - 1, knotY - radius - 1, radius * 2 + 2, radius * 2 + 2);
                gc.setFill(threadColor.interpolate(Color.WHITE, 0.18));
                gc.fillOval(knotX - radius, knotY - radius, radius * 2, radius * 2);
            }

            drawThreadAnchor(gc, ax, ay, lineWidth, threadColor);
            drawThreadAnchor(gc, bx, by, lineWidth, threadColor);
            gc.restore();

            double midX = (ax + bx) * 0.5;
            double midY = (ay + by) * 0.5;
            gc.setFill(threadColor.interpolate(Color.WHITE, 0.25));
            gc.fillOval(midX - 3.5, midY - 3.5, 7, 7);
        }
    }

    /**
     * Dibuja capas de fondo y motivos decorativos según el biome activo.
     */
    private void drawBackground(GraphicsContext gc, GameRenderState state, PixelArtTheme.Palette palette,
                                double canvasWidth, double canvasHeight, double scaleX, double scaleY,
                                int tileSize) {
        String biome = state.background();
        if (biome == null) biome = "default";

        gc.setFill(palette.backgroundFar());
        gc.fillRect(0, 0, canvasWidth, canvasHeight);
        drawAtmosphere(gc, state, palette, biome, canvasWidth, canvasHeight, tileSize);

        gc.setFill(palette.backgroundMid());
        switch (biome) {
            case "forest" -> {
                drawParallaxBand(gc, state, scaleX, scaleY, tileSize, new double[][]{
                    {0, 7, 2, 7}, {3, 6, 3, 8}, {8, 7, 2, 7}, {12, 5, 3, 9}, {17, 7, 2, 7}, {21, 6, 3, 8}
                });
                gc.setFill(palette.backgroundNear());
                drawParallaxBand(gc, state, scaleX, scaleY, tileSize, new double[][]{
                    {1, 8, 2, 6}, {5, 7, 2, 7}, {10, 8, 2, 6}, {14, 6, 2, 8}, {18, 8, 2, 6}, {23, 7, 2, 7}
                });
                drawBackgroundMotif(gc, state, scaleX, scaleY, tileSize, palette.platformDetail(), new double[][]{
                    {2, 8, 1, 3}, {6, 7, 1, 4}, {11, 8, 1, 3}, {15, 7, 1, 4}, {19, 8, 1, 3}, {24, 7, 1, 4}
                });
            }
            case "desert" -> {
                drawParallaxBand(gc, state, scaleX, scaleY, tileSize, new double[][]{
                    {0, 9, 5, 5}, {6, 8, 4, 6}, {12, 9, 5, 5}, {18, 7, 5, 7}, {24, 9, 4, 5}
                });
                gc.setFill(palette.backgroundNear());
                drawParallaxBand(gc, state, scaleX, scaleY, tileSize, new double[][]{
                    {2, 10, 4, 4}, {9, 9, 4, 5}, {15, 10, 4, 4}, {22, 8, 4, 6}
                });
                drawBackgroundMotif(gc, state, scaleX, scaleY, tileSize, palette.platformDetail(), new double[][]{
                    {3, 10, 1, 2}, {10, 9, 1, 3}, {16, 10, 1, 2}, {23, 9, 1, 3}
                });
            }
            case "cave" -> {
                drawParallaxBand(gc, state, scaleX, scaleY, tileSize, new double[][]{
                    {0, 8, 4, 6}, {5, 9, 3, 5}, {10, 7, 4, 7}, {15, 8, 3, 6}, {20, 6, 4, 8}, {25, 8, 3, 6}
                });
                gc.setFill(palette.backgroundNear());
                drawParallaxBand(gc, state, scaleX, scaleY, tileSize, new double[][]{
                    {2, 10, 2, 4}, {7, 9, 2, 5}, {12, 10, 2, 4}, {17, 9, 2, 5}, {22, 10, 2, 4}
                });
                drawBackgroundMotif(gc, state, scaleX, scaleY, tileSize, palette.platformDetail(), new double[][]{
                    {3, 9, 1, 1}, {8, 10, 1, 1}, {13, 9, 1, 1}, {18, 10, 1, 1}, {23, 9, 1, 1}
                });
            }
            case "snow" -> {
                drawParallaxBand(gc, state, scaleX, scaleY, tileSize, new double[][]{
                    {0, 9, 4, 5}, {5, 8, 3, 6}, {10, 9, 4, 5}, {15, 7, 3, 7}, {20, 9, 4, 5}, {25, 8, 3, 6}
                });
                gc.setFill(palette.backgroundNear());
                drawParallaxBand(gc, state, scaleX, scaleY, tileSize, new double[][]{
                    {1, 10, 3, 4}, {7, 9, 3, 5}, {13, 10, 3, 4}, {19, 9, 3, 5}, {24, 10, 3, 4}
                });
                drawBackgroundMotif(gc, state, scaleX, scaleY, tileSize, palette.platformDetail(), new double[][]{
                    {2, 10, 1, 1}, {8, 9, 1, 1}, {14, 10, 1, 1}, {20, 9, 1, 1}, {25, 10, 1, 1}
                });
            }
            default -> {
                drawParallaxBand(gc, state, scaleX, scaleY, tileSize, new double[][]{
                    {0, 8, 4, 6}, {5, 7, 4, 7}, {11, 9, 4, 5}, {17, 7, 4, 7}, {23, 8, 4, 6}
                });
                gc.setFill(palette.backgroundNear());
                drawParallaxBand(gc, state, scaleX, scaleY, tileSize, new double[][]{
                    {2, 10, 3, 4}, {8, 9, 3, 5}, {14, 10, 3, 4}, {20, 9, 3, 5}
                });
            }
        }

        gc.setFill(new LinearGradient(
            0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.web("#ffffff", 0.04)),
            new Stop(0.55, Color.TRANSPARENT),
            new Stop(1.0, Color.web("#04070d", 0.28))
        ));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);
    }

    private void drawParallaxBand(GraphicsContext gc, GameRenderState state, double scaleX, double scaleY,
                                  int tileSize, double[][] segments) {
        for (double[] segment : segments) {
            double sx = snap(worldToScreenX(state, segment[0] * tileSize, scaleX));
            double sy = snap(worldToScreenY(state, segment[1] * tileSize, scaleY));
            double sw = snap(segment[2] * tileSize * scaleX);
            double sh = snap(segment[3] * tileSize * scaleY);
            gc.fillRect(sx, sy, sw, sh);
        }
    }

    private void drawBackgroundMotif(GraphicsContext gc, GameRenderState state, double scaleX, double scaleY,
                                     int tileSize, Color color, double[][] motifs) {
        gc.setFill(color);
        for (double[] motif : motifs) {
            double sx = snap(worldToScreenX(state, motif[0] * tileSize, scaleX));
            double sy = snap(worldToScreenY(state, motif[1] * tileSize, scaleY));
            double sw = snap(motif[2] * tileSize * scaleX);
            double sh = snap(motif[3] * tileSize * scaleY);
            gc.fillRect(sx, sy, sw, sh);
        }
    }

    private void drawAtmosphere(GraphicsContext gc, GameRenderState state, PixelArtTheme.Palette palette, String biome,
                                double canvasWidth, double canvasHeight, int tileSize) {
        double drift = Math.sin(state.visualTimeSeconds() * 0.32) * 18.0;
        switch (biome) {
            case "forest" -> {
                gc.setFill(new RadialGradient(
                    0, 0, canvasWidth * 0.82, canvasHeight * 0.22, canvasWidth * 0.28,
                    false, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.web("#f0f5c8", 0.16)),
                    new Stop(1.0, Color.TRANSPARENT)
                ));
                gc.fillOval(canvasWidth * 0.56, -canvasHeight * 0.02, canvasWidth * 0.52, canvasHeight * 0.52);
                gc.setFill(new LinearGradient(
                    0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.TRANSPARENT),
                    new Stop(0.5, Color.web("#dfecc7", 0.05)),
                    new Stop(1.0, Color.TRANSPARENT)
                ));
                gc.fillRect(-40 + drift, canvasHeight * 0.60, canvasWidth + 80, canvasHeight * 0.14);
            }
            case "desert" -> {
                gc.setFill(new RadialGradient(
                    0, 0, canvasWidth * 0.18, canvasHeight * 0.22, canvasWidth * 0.24,
                    false, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.web("#ffe0a1", 0.18)),
                    new Stop(1.0, Color.TRANSPARENT)
                ));
                gc.fillOval(-canvasWidth * 0.04, -canvasHeight * 0.02, canvasWidth * 0.44, canvasHeight * 0.44);
                gc.setFill(new LinearGradient(
                    0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.TRANSPARENT),
                    new Stop(0.75, Color.web("#ffd094", 0.08)),
                    new Stop(1.0, Color.web("#2d1b0f", 0.06))
                ));
                gc.fillRect(0, canvasHeight * 0.45, canvasWidth, canvasHeight * 0.35);
            }
            case "cave" -> {
                gc.setFill(new RadialGradient(
                    0, 0, canvasWidth * 0.52, canvasHeight * 0.28, canvasWidth * 0.26,
                    false, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.web("#88a1ff", 0.08)),
                    new Stop(1.0, Color.TRANSPARENT)
                ));
                gc.fillOval(canvasWidth * 0.22, canvasHeight * 0.05, canvasWidth * 0.6, canvasHeight * 0.5);
                gc.setFill(Color.web("#c7d3ff", 0.07));
                gc.fillPolygon(
                    new double[]{canvasWidth * 0.18, canvasWidth * 0.22, canvasWidth * 0.26},
                    new double[]{canvasHeight * 0.18, canvasHeight * 0.36, canvasHeight * 0.18},
                    3
                );
                gc.fillPolygon(
                    new double[]{canvasWidth * 0.74, canvasWidth * 0.79, canvasWidth * 0.84},
                    new double[]{canvasHeight * 0.24, canvasHeight * 0.44, canvasHeight * 0.24},
                    3
                );
            }
            case "snow" -> {
                gc.setFill(new RadialGradient(
                    0, 0, canvasWidth * 0.72, canvasHeight * 0.16, canvasWidth * 0.24,
                    false, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.web("#e8f5ff", 0.14)),
                    new Stop(1.0, Color.TRANSPARENT)
                ));
                gc.fillOval(canvasWidth * 0.48, -canvasHeight * 0.04, canvasWidth * 0.44, canvasHeight * 0.44);
                gc.setFill(new LinearGradient(
                    0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.TRANSPARENT),
                    new Stop(0.5, Color.web("#eaf8ff", 0.08)),
                    new Stop(1.0, Color.TRANSPARENT)
                ));
                gc.fillRect(-30 - drift, canvasHeight * 0.56, canvasWidth + 60, canvasHeight * 0.12);
            }
            default -> {
                gc.setFill(new LinearGradient(
                    0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.web("#ffffff", 0.06)),
                    new Stop(1.0, Color.TRANSPARENT)
                ));
                gc.fillRect(0, 0, canvasWidth, canvasHeight * 0.38);
            }
        }

        gc.setFill(new LinearGradient(
            0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.TRANSPARENT),
            new Stop(1.0, palette.backgroundNear().deriveColor(0, 1, 0.92, 0.18))
        ));
        gc.fillRect(0, canvasHeight * 0.52, canvasWidth, canvasHeight * 0.48);
    }

    private void drawPlatform(GraphicsContext gc, GameRenderState state, PlatformTile platform, double scaleX, double scaleY,
                              PixelArtTheme.Palette palette, boolean special) {
        double sx = snap(worldToScreenX(state, platform.getX(), scaleX));
        double sy = snap(worldToScreenY(state, platform.getY(), scaleY));
        double sw = snap(platform.getWidth() * scaleX);
        double sh = snap(platform.getHeight() * scaleY);
        Color top = special ? palette.special() : palette.platformTop();
        Color face = special ? palette.special().darker() : palette.platformFace();
        gc.setFill(Color.web("#05070d", 0.28));
        gc.fillRect(sx + 4, sy + sh - 2, Math.max(8, sw - 8), 6);
        gc.setFill(face);
        gc.fillRect(sx, sy, sw, sh);
        gc.setFill(top);
        double topBand = snap(Math.max(PixelArtTheme.BASE_PIXEL * 2, sh * 0.25));
        gc.fillRect(sx, sy, sw, topBand);
        gc.setFill(top.interpolate(Color.WHITE, 0.22));
        gc.fillRect(sx + 4, sy + 4, Math.max(8, sw - 8), PixelArtTheme.BASE_PIXEL);
        fillTilePattern(gc, sx, sy + topBand, sw, Math.max(PixelArtTheme.BASE_PIXEL, sh - topBand),
            palette.platformDetail(), PixelArtTheme.BASE_PIXEL * 3);
        gc.setFill(face.darker().deriveColor(0, 1, 0.88, 1));
        gc.fillRect(sx, sy + sh - 6, sw, 6);
        if (special) {
            gc.setFill(Color.web("#f6fbff", 0.15));
            gc.fillPolygon(
                new double[]{sx + 8, sx + 18, sx + 26},
                new double[]{sy + sh - 10, sy + sh - 22, sy + sh - 10},
                3
            );
            gc.fillPolygon(
                new double[]{sx + sw - 28, sx + sw - 18, sx + sw - 10},
                new double[]{sy + sh - 10, sy + sh - 24, sy + sh - 10},
                3
            );
        }
        gc.setStroke(PixelArtTheme.WORLD_BORDER);
        gc.setLineWidth(1);
        gc.strokeRect(sx + 0.5, sy + 0.5, sw - 1, sh - 1);
    }

    private void drawHazard(GraphicsContext gc, GameRenderState state, PlatformTile hazard, double scaleX, double scaleY,
                            PixelArtTheme.Palette palette) {
        double sx = snap(worldToScreenX(state, hazard.getX(), scaleX));
        double sy = snap(worldToScreenY(state, hazard.getY(), scaleY));
        double sw = snap(hazard.getWidth() * scaleX);
        double sh = snap(hazard.getHeight() * scaleY);
        double pulse = 0.82 + Math.sin(state.visualTimeSeconds() * 7.0 + hazard.getX() * 0.025) * 0.12;
        gc.setFill(PixelArtTheme.INK);
        gc.fillRect(sx, sy, sw, sh);
        gc.setFill(palette.hazard().deriveColor(0, 1, pulse, 1));
        double spikeW = PixelArtTheme.BASE_PIXEL * 3;
        for (double px = sx; px < sx + sw; px += spikeW * 2) {
            gc.fillPolygon(
                new double[]{px, px + spikeW, Math.min(sx + sw, px + spikeW * 2)},
                new double[]{sy + sh, sy + sh - 18, sy + sh},
                3
            );
        }
        double stripeOffset = (state.visualTimeSeconds() * 38.0) % 22.0;
        gc.setFill(Color.web("#fff0c0", 0.18));
        for (double px = sx - 18 + stripeOffset; px < sx + sw + 18; px += 22) {
            gc.fillPolygon(
                new double[]{px, px + 10, px + 18, px + 8},
                new double[]{sy + 4, sy + 4, sy + sh - 6, sy + sh - 6},
                4
            );
        }
        gc.setFill(Color.web("#ffd38a", 0.22));
        gc.fillRect(sx + 2, sy + 2, sw - 4, PixelArtTheme.BASE_PIXEL + 1);
        gc.setStroke(PixelArtTheme.WORLD_BORDER);
        gc.setLineWidth(1);
        gc.strokeRect(sx + 0.5, sy + 0.5, sw - 1, sh - 1);
    }

    private void drawCheckpoint(GraphicsContext gc, GameRenderState state, PlatformTile checkpoint, double scaleX, double scaleY,
                                PixelArtTheme.Palette palette) {
        double sx = snap(worldToScreenX(state, checkpoint.getX(), scaleX));
        double sy = snap(worldToScreenY(state, checkpoint.getY(), scaleY));
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

    private void drawPushBlock(GraphicsContext gc, GameRenderState state, PushBlock block, double scaleX, double scaleY,
                               PixelArtTheme.Palette palette) {
        double sx = snap(worldToScreenX(state, block.getX(), scaleX));
        double sy = snap(worldToScreenY(state, block.getY(), scaleY));
        double sw = snap(block.getWidth() * scaleX);
        double sh = snap(block.getHeight() * scaleY);
        gc.setFill(Color.web("#09070d", 0.30));
        gc.fillOval(sx + 8, sy + sh - 8, Math.max(20, sw - 16), 10);
        gc.setFill(palette.crateDark());
        gc.fillRect(sx, sy, sw, sh);
        gc.setFill(palette.crateLight());
        gc.fillRect(sx + 4, sy + 4, sw - 8, PixelArtTheme.BASE_PIXEL + 1);
        gc.fillRect(sx + 4, sy + sh - 8, sw - 8, PixelArtTheme.BASE_PIXEL + 1);
        gc.fillRect(sx + 4, sy + 4, PixelArtTheme.BASE_PIXEL + 1, sh - 8);
        gc.fillRect(sx + sw - 8, sy + 4, PixelArtTheme.BASE_PIXEL + 1, sh - 8);
        gc.fillRect(sx + sw * 0.5 - 2, sy + 4, PixelArtTheme.BASE_PIXEL, sh - 8);
        gc.fillRect(sx + 4, sy + sh * 0.5 - 2, sw - 8, PixelArtTheme.BASE_PIXEL);
        gc.setFill(Color.web("#f3e8c8", 0.18));
        gc.fillRect(sx + 10, sy + 10, sw - 20, sh - 20);
        gc.setStroke(Color.web("#f3e8c8", 0.35));
        gc.setLineWidth(2);
        gc.strokeLine(sx + 10, sy + 10, sx + sw - 10, sy + sh - 10);
        gc.strokeLine(sx + sw - 10, sy + 10, sx + 10, sy + sh - 10);
        gc.setFill(Color.web("#121013", 0.45));
        gc.fillRect(sx + sw - 12, sy + 6, 4, sh - 12);
        gc.setFill(Color.web("#e5c285", 0.85));
        gc.fillOval(sx + 8, sy + 8, 4, 4);
        gc.fillOval(sx + sw - 12, sy + 8, 4, 4);
        gc.fillOval(sx + 8, sy + sh - 12, 4, 4);
        gc.fillOval(sx + sw - 12, sy + sh - 12, 4, 4);
        gc.setStroke(PixelArtTheme.WORLD_BORDER);
        gc.setLineWidth(1);
        gc.strokeRect(sx + 0.5, sy + 0.5, sw - 1, sh - 1);
    }

    private void drawButton(GraphicsContext gc, GameRenderState state, ButtonSwitch button, double scaleX, double scaleY,
                            PixelArtTheme.Palette palette) {
        double sx = snap(worldToScreenX(state, button.getX(), scaleX));
        double sy = snap(worldToScreenY(state, button.getY(), scaleY));
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

    private void drawDoor(GraphicsContext gc, GameRenderState state, Door door, double scaleX, double scaleY,
                          PixelArtTheme.Palette palette) {
        double sx = snap(worldToScreenX(state, door.getX(), scaleX));
        double sy = snap(worldToScreenY(state, door.getY(), scaleY));
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

    private void drawExit(GraphicsContext gc, GameRenderState state, ExitZone exitZone, double scaleX, double scaleY,
                          PixelArtTheme.Palette palette) {
        double sx = snap(worldToScreenX(state, exitZone.getX(), scaleX));
        double sy = snap(worldToScreenY(state, exitZone.getY(), scaleY));
        double sw = snap(exitZone.getWidth() * scaleX);
        double sh = snap(exitZone.getHeight() * scaleY);
        double pulse = 0.84 + Math.sin(state.visualTimeSeconds() * 4.5) * 0.08;
        gc.setFill(Color.web("#fff0a8", 0.08 + (pulse - 0.76) * 0.14));
        gc.fillRoundRect(sx - 10, sy - 10, sw + 20, sh + 20, 16, 16);
        gc.setFill(PixelArtTheme.INK);
        gc.fillRoundRect(sx, sy, sw, sh, 10, 10);
        gc.setFill(palette.goal().deriveColor(0, 1, pulse, 1));
        gc.fillRoundRect(sx + 4, sy + 4, sw - 8, sh - 8, 8, 8);
        gc.setFill(new LinearGradient(
            0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.web("#fff4bf", 0.55)),
            new Stop(0.45, Color.web("#f3d876", 0.25)),
            new Stop(1.0, Color.web("#b88d25", 0.15))
        ));
        gc.fillRoundRect(sx + 8, sy + 8, sw - 16, sh - 16, 6, 6);
        gc.setFill(Color.web("#fff8d6", 0.30));
        for (double px = sx + 10; px < sx + sw - 10; px += PixelArtTheme.BASE_PIXEL * 3) {
            gc.fillRect(px, sy + 10, PixelArtTheme.BASE_PIXEL, sh - 20);
        }
        gc.setFill(Color.web("#fff7d2", 0.24));
        gc.fillRoundRect(sx + 10, sy + 10, sw - 20, PixelArtTheme.BASE_PIXEL * 2, 6, 6);
        gc.setStroke(PixelArtTheme.WORLD_BORDER);
        gc.setLineWidth(1);
        gc.strokeRoundRect(sx + 0.5, sy + 0.5, sw - 1, sh - 1, 10, 10);
    }

    private void drawCoin(GraphicsContext gc, GameRenderState state, CollectibleItem coin, double scaleX, double scaleY,
                          PixelArtTheme.Palette palette) {
        double size = snap(GameConfig.COIN_SIZE * Math.min(scaleX, scaleY));
        double sx = snap(worldToScreenX(state, coin.getX(), scaleX));
        double bob = Math.sin(state.visualTimeSeconds() * 5.2 + coin.getX() * 0.03) * 3.0;
        double sy = snap(worldToScreenY(state, coin.getY(), scaleY) + bob);
        double pulse = 0.88 + Math.sin(state.visualTimeSeconds() * 7.5 + coin.getY() * 0.04) * 0.10;
        gc.setFill(Color.web("#ffdf7a", 0.14 + (pulse - 0.78) * 0.16));
        gc.fillOval(sx - 7, sy - 6, size + 14, size + 14);
        gc.setFill(PixelArtTheme.INK);
        gc.fillRoundRect(sx, sy, size, size, 8, 8);
        gc.setFill(palette.goal().deriveColor(0, 1, pulse, 1));
        gc.fillRoundRect(sx + 3, sy + 3, size - 6, size - 6, 8, 8);
        gc.setFill(Color.web("#fff6cf", 0.38));
        gc.fillRect(sx + 5, sy + 5, size - 10, PixelArtTheme.BASE_PIXEL + 1);
        gc.setFill(Color.web("#d49d22", 0.62));
        gc.fillOval(sx + size * 0.28, sy + size * 0.28, size * 0.44, size * 0.44);
        gc.setStroke(PixelArtTheme.WORLD_BORDER);
        gc.setLineWidth(1);
        gc.strokeRoundRect(sx + 0.5, sy + 0.5, size - 1, size - 1, 8, 8);
    }

    private void drawPlayer(GraphicsContext gc, GameRenderState state, Player player, Player localPlayer, int playerIndex,
                            double scaleX, double scaleY, PixelArtTheme.Palette palette) {
        double sx = snap(worldToScreenX(state, player.getX(), scaleX));
        double sy = snap(worldToScreenY(state, player.getY(), scaleY));
        double sw = snap(player.getWidth() * scaleX);
        double sh = snap(player.getHeight() * scaleY);

        double squash = player.isGrounded() ? Math.min(4, Math.abs(player.getVx()) * 0.01) : 0;
        double stretch = !player.isGrounded() ? Math.min(6, Math.abs(player.getVy()) * 0.008) : 0;
        double motion = Math.min(1.0, Math.abs(player.getVx()) / 120.0);
        double idleLift = player.isGrounded()
            ? Math.abs(Math.sin(state.visualTimeSeconds() * (4.0 + motion * 5.0) + playerIndex * 0.6)) * (1.2 + motion * 1.8)
            : 0;
        double bodyW = snap(Math.max(24, sw + squash - stretch * 0.35));
        double bodyH = snap(Math.max(36, sh - squash + stretch));
        double bodyX = snap(sx - (bodyW - sw) / 2.0);
        double bodyY = snap(sy - (bodyH - sh) / 2.0 - idleLift);
        gc.setFill(Color.web("#08060b", 0.34));
        gc.fillOval(bodyX + 6, sy + sh - 8, Math.max(18, bodyW - 12), 10);

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
        gc.setFill(Color.web("#ffffff", 0.10));
        gc.fillRect(bodyX + 5, bodyY + 6, Math.max(8, bodyW * 0.3), bodyH - 12);
        gc.setFill(Color.web("#06080d", 0.16));
        gc.fillRect(bodyX + bodyW - 9, bodyY + 4, 5, bodyH - 8);
        gc.setFill(accent.deriveColor(0, 1, 0.92, 1));
        gc.fillPolygon(
            new double[]{bodyX + 6, bodyX + bodyW * 0.48, bodyX + bodyW * 0.42, bodyX + 6},
            new double[]{bodyY + bodyH * 0.38, bodyY + bodyH * 0.22, bodyY + bodyH * 0.34, bodyY + bodyH * 0.50},
            4
        );

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
        gc.setFill(Color.web("#ffd7c7", 0.30));
        gc.fillRect(bodyX + bodyW * 0.5 - 4, bodyY + bodyH * 0.55, 8, 2);

        gc.setStroke(PixelArtTheme.WORLD_BORDER);
        gc.setLineWidth(localPlayer != null && player.getId().equals(localPlayer.getId()) ? 2 : 1);
        gc.strokeRect(bodyX + 0.5, bodyY + 0.5, bodyW - 1, bodyH - 1);

        if (localPlayer != null && player.getId().equals(localPlayer.getId())) {
            gc.setStroke(palette.goal().deriveColor(0, 1, 1.08, 0.95));
            gc.setLineWidth(1.5);
            gc.strokeRoundRect(bodyX - 5, bodyY - 5, bodyW + 10, bodyH + 10, 12, 12);
        }

        gc.setFont(PLAYER_NAME_FONT);
        double nameWidth = Math.max(34, player.getName().length() * 7.4 + 10);
        double namePlateX = bodyX - 4;
        double namePlateY = bodyY - 20;
        gc.setFill(Color.web("#060911", 0.66));
        gc.fillRoundRect(namePlateX, namePlateY, nameWidth, 14, 8, 8);
        gc.setStroke(Color.web("#3c5068", 0.72));
        gc.setLineWidth(1);
        gc.strokeRoundRect(namePlateX + 0.5, namePlateY + 0.5, nameWidth - 1, 13, 8, 8);
        gc.setFill(Color.web("#0d1118", 0.52));
        gc.fillText(player.getName(), bodyX + 1, bodyY - 5);
        gc.setFill(PixelArtTheme.HUD_TEXT);
        gc.fillText(player.getName(), bodyX, bodyY - 6);
    }

    private void drawThreadAnchor(GraphicsContext gc, double x, double y, double lineWidth, Color color) {
        double outer = Math.max(6.0, lineWidth * 2.7);
        double inner = Math.max(3.0, lineWidth * 1.5);
        gc.setFill(Color.web("#090b10", 0.58));
        gc.fillOval(x - outer, y - outer, outer * 2, outer * 2);
        gc.setFill(color.darker().deriveColor(0, 1, 0.88, 1));
        gc.fillOval(x - inner, y - inner, inner * 2, inner * 2);
        gc.setFill(color.interpolate(Color.WHITE, 0.28));
        gc.fillOval(x - inner * 0.55, y - inner * 0.55, inner * 1.1, inner * 1.1);
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

    private double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private double worldToScreenX(GameRenderState state, double worldX, double scaleX) {
        return (worldX - state.cameraX()) * scaleX;
    }

    private double worldToScreenY(GameRenderState state, double worldY, double scaleY) {
        return (worldY - state.cameraY()) * scaleY;
    }

    private boolean isVisible(GameRenderState state, double x, double y, double width, double height) {
        double minX = state.cameraX() - VISIBILITY_MARGIN;
        double maxX = state.cameraX() + state.viewportWidth() + VISIBILITY_MARGIN;
        double minY = state.cameraY() - VISIBILITY_MARGIN;
        double maxY = state.cameraY() + state.viewportHeight() + VISIBILITY_MARGIN;
        return x + width >= minX && x <= maxX && y + height >= minY && y <= maxY;
    }
}
