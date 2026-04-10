package com.dino.domain.entities;

/**
 * Plataforma estática rectangular del nivel.
 *
 * <p>Se usa para suelo, escalones y superficies de apoyo. Es inmutable a nivel
 * conceptual, aunque se serializa como DTO simple para snapshots.</p>
 */
public class PlatformTile {
    private String id;
    private double x;
    private double y;
    private double width;
    private double height;

    /**
     * Constructor vacío requerido para snapshots y copias defensivas.
     */
    public PlatformTile() {}

    /**
     * Crea un rectángulo estático del mundo.
     *
     * @param id identificador lógico del tile
     * @param x coordenada X
     * @param y coordenada Y
     * @param width ancho del tile
     * @param height alto del tile
     */
    public PlatformTile(String id, double x, double y, double width, double height) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Retorna el identificador lógico del tile.
     *
     * @return id único dentro del snapshot o nivel
     */
    public String getId() { return id; }

    /**
     * Actualiza el identificador lógico del tile.
     *
     * @param id nuevo identificador
     */
    public void setId(String id) { this.id = id; }

    /**
     * Retorna la coordenada X del tile.
     *
     * @return posición horizontal en mundo
     */
    public double getX() { return x; }

    /**
     * Actualiza la coordenada X del tile.
     *
     * @param x nueva posición horizontal
     */
    public void setX(double x) { this.x = x; }

    /**
     * Retorna la coordenada Y del tile.
     *
     * @return posición vertical en mundo
     */
    public double getY() { return y; }

    /**
     * Actualiza la coordenada Y del tile.
     *
     * @param y nueva posición vertical
     */
    public void setY(double y) { this.y = y; }

    /**
     * Retorna el ancho del tile.
     *
     * @return ancho en unidades de mundo
     */
    public double getWidth() { return width; }

    /**
     * Actualiza el ancho del tile.
     *
     * @param width nuevo ancho
     */
    public void setWidth(double width) { this.width = width; }

    /**
     * Retorna el alto del tile.
     *
     * @return alto en unidades de mundo
     */
    public double getHeight() { return height; }

    /**
     * Actualiza el alto del tile.
     *
     * @param height nuevo alto
     */
    public void setHeight(double height) { this.height = height; }
}
