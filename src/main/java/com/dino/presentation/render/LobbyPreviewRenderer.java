package com.dino.presentation.render;

import com.dino.domain.entities.Player;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;

import java.util.List;

/**
 * Renderer ligero de la vista previa del lobby.
 *
 * <p>Dibuja un panel compacto con slots de jugadores conectados o faltantes,
 * usando el color y nombre visibles de cada peer. Su objetivo es dar una
 * lectura rápida del estado de la pre-partida sin convertir el lobby en una
 * escena de simulación separada.</p>
 */
public final class LobbyPreviewRenderer {
    private static final Font TITLE_FONT = Font.font("Monospaced", 14);
    private static final Font BODY_FONT = Font.font("Monospaced", 12);

    /**
     * Redibuja el canvas del lobby con los jugadores actuales.
     *
     * @param canvas canvas JavaFX del panel de preview
     * @param players jugadores visibles en el lobby
     * @param expectedPlayers tamaño objetivo de la sala
     * @param timeSeconds tiempo acumulado usado para una animación leve
     */
    public void render(Canvas canvas, List<Player> players, int expectedPlayers, double timeSeconds) {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(new LinearGradient(
            0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.web("#111821")),
            new Stop(1.0, Color.web("#0b1018"))
        ));
        gc.fillRoundRect(0, 0, width, height, 24, 24);
        gc.setFill(new RadialGradient(
            0, 0, width * 0.18, height * 0.18, width * 0.34,
            false, CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.web("#8fc8ff", 0.12)),
            new Stop(1.0, Color.TRANSPARENT)
        ));
        gc.fillOval(-24, -18, width * 0.6, height * 0.7);
        gc.setFill(Color.web("#0f151f"));
        gc.fillRoundRect(12, height - 52, width - 24, 28, 18, 18);
        gc.setFill(Color.web("#172434"));
        gc.fillRect(22, height - 40, width - 44, 4);
        gc.setStroke(Color.web("#374255"));
        gc.setLineWidth(2);
        gc.strokeRoundRect(1, 1, width - 2, height - 2, 24, 24);

        gc.setFill(Color.web("#dfe7f2"));
        gc.setFont(TITLE_FONT);
        gc.fillText("Vista previa del lobby", 18, 24);
        gc.setFill(Color.web("#8ea0b8"));
        gc.setFont(BODY_FONT);
        gc.fillText("Jugadores conectados sin cuerda", 18, 42);

        int slots = Math.max(2, expectedPlayers);
        int columns = slots <= 2 ? slots : 2;
        int rows = (int) Math.ceil(slots / (double) columns);
        double padding = 18;
        double topOffset = 58;
        double gap = 14;
        double cardWidth = (width - (padding * 2) - gap * (columns - 1)) / columns;
        double cardHeight = (height - topOffset - padding - gap * (rows - 1)) / rows;

        for (int i = 0; i < slots; i++) {
            int row = i / columns;
            int column = i % columns;
            double x = padding + column * (cardWidth + gap);
            double y = topOffset + row * (cardHeight + gap);
            Player player = i < players.size() ? players.get(i) : null;
            drawSlot(gc, player, i, x, y, cardWidth, cardHeight, timeSeconds + i * 0.35);
        }
    }

    private void drawSlot(GraphicsContext gc, Player player, int index, double x, double y,
                          double width, double height, double timeSeconds) {
        boolean occupied = player != null;
        Color border = occupied ? Color.web("#4e617d") : Color.web("#283241");
        Color bg = occupied ? Color.web("#161d27") : Color.web("#10151d");

        gc.setFill(bg);
        gc.fillRoundRect(x, y, width, height, 18, 18);
        gc.setFill(Color.web("#ffffff", occupied ? 0.04 : 0.02));
        gc.fillRoundRect(x + 1, y + 1, width - 2, Math.max(12, height * 0.22), 18, 18);
        gc.setStroke(border);
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(x + 0.5, y + 0.5, width - 1, height - 1, 18, 18);

        double bobOffset = occupied ? Math.sin(timeSeconds * 2.4) * 2.4 : 0;
        double avatarSize = Math.min(58, width * 0.34);
        double avatarX = x + 18;
        double avatarY = y + 18 + bobOffset;

        if (occupied) {
            Color primary = PixelArtTheme.resolvePlayerPrimary(player.getColor(), index);
            Color accent = PixelArtTheme.resolvePlayerSecondary(player.getColor(), index);
            gc.setFill(Color.web("#090b10", 0.34));
            gc.fillOval(avatarX + 8, avatarY + avatarSize - 8, avatarSize - 16, 10);
            gc.setFill(Color.web("#ffffff", 0.08));
            gc.fillOval(avatarX - 6, avatarY - 6, avatarSize + 12, avatarSize + 12);
            gc.setFill(primary);
            gc.fillRoundRect(avatarX, avatarY, avatarSize, avatarSize, 14, 14);
            gc.setFill(Color.web("#ffffff", 0.08));
            gc.fillRoundRect(avatarX + 3, avatarY + 3, avatarSize * 0.32, avatarSize - 6, 10, 10);
            gc.setFill(accent);
            gc.fillRect(avatarX + 8, avatarY + 8, avatarSize - 16, 10);
            gc.fillPolygon(
                new double[]{avatarX + 8, avatarX + avatarSize * 0.52, avatarX + avatarSize * 0.46, avatarX + 8},
                new double[]{avatarY + avatarSize * 0.40, avatarY + avatarSize * 0.25, avatarY + avatarSize * 0.38, avatarY + avatarSize * 0.52},
                4
            );
            gc.setFill(Color.WHITE);
            gc.fillRect(avatarX + 12, avatarY + 22, 8, 8);
            gc.fillRect(avatarX + avatarSize - 20, avatarY + 22, 8, 8);
            gc.setFill(PixelArtTheme.INK);
            gc.fillRect(avatarX + 15, avatarY + 25, 2, 2);
            gc.fillRect(avatarX + avatarSize - 17, avatarY + 25, 2, 2);
            gc.setFill(player.isReady() ? Color.web("#8ff1a3") : Color.web("#7bcfff"));
            gc.fillOval(x + width - 24, y + 12, 10, 10);

            gc.setFont(TITLE_FONT);
            gc.setFill(Color.web("#edf3fa"));
            gc.fillText(player.getName(), avatarX + avatarSize + 16, y + 30);
            gc.setFont(BODY_FONT);
            gc.setFill(player.isReady() ? Color.web("#92e6a7") : Color.web("#87c6f5"));
            gc.fillText(player.isReady() ? "Listo" : "Conectado", avatarX + avatarSize + 16, y + 52);
            gc.setFill(Color.web("#93a2b8"));
            gc.fillText("Color: " + player.getColor(), avatarX + avatarSize + 16, y + 72);
        } else {
            gc.setStroke(Color.web("#2c3645"));
            gc.setLineDashes(8, 6);
            gc.strokeRoundRect(avatarX, avatarY, avatarSize, avatarSize, 14, 14);
            gc.setLineDashes(null);
            gc.setFont(TITLE_FONT);
            gc.setFill(Color.web("#9dadbf"));
            gc.fillText("Esperando", avatarX + avatarSize + 16, y + 34);
            gc.setFont(BODY_FONT);
            gc.setFill(Color.web("#6f7d91"));
            gc.fillText("Slot libre", avatarX + avatarSize + 16, y + 56);
        }
    }
}
