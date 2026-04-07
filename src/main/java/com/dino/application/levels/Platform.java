package com.dino.application.levels;

/**
 * Plataforma rectangular cargada desde la definición textual del nivel.
 *
 * <p>Se usa como DTO geométrico durante el parseo para describir suelo,
 * hazards, checkpoints y metas antes de traducirlos a entidades del dominio o
 * al snapshot compartido.</p>
 */
public class Platform {
    private final double x;
    private final double y;
    private final double width;
    private final double height;

    /**
     * Construye una plataforma rectangular en coordenadas de mundo.
     *
     * @param x coordenada X
     * @param y coordenada Y
     * @param width ancho
     * @param height alto
     */
    public Platform(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** @return coordenada X de la plataforma */
    public double getX() { return x; }
    /** @return coordenada Y de la plataforma */
    public double getY() { return y; }
    /** @return ancho de la plataforma */
    public double getWidth() { return width; }
    /** @return alto de la plataforma */
    public double getHeight() { return height; }
}
