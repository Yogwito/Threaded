package com.dino.domain.entities;

/**
 * Zona de salida de la sala.
 *
 * <p>Cuando todos los jugadores conectados están dentro de esta región, el host
 * avanza al siguiente nivel o finaliza la campaña.</p>
 */
public class ExitZone {
    private double x;
    private double y;
    private double width;
    private double height;

    /**
     * Constructor vacío requerido para snapshots y copias defensivas.
     */
    public ExitZone() {}

    /**
     * Crea una zona rectangular de meta.
     *
     * @param x coordenada X
     * @param y coordenada Y
     * @param width ancho de la zona
     * @param height alto de la zona
     */
    public ExitZone(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Retorna la coordenada X de la salida.
     *
     * @return posición horizontal en mundo
     */
    public double getX() { return x; }

    /**
     * Actualiza la coordenada X de la salida.
     *
     * @param x nueva posición horizontal
     */
    public void setX(double x) { this.x = x; }

    /**
     * Retorna la coordenada Y de la salida.
     *
     * @return posición vertical en mundo
     */
    public double getY() { return y; }

    /**
     * Actualiza la coordenada Y de la salida.
     *
     * @param y nueva posición vertical
     */
    public void setY(double y) { this.y = y; }

    /**
     * Retorna el ancho de la salida.
     *
     * @return ancho en unidades de mundo
     */
    public double getWidth() { return width; }

    /**
     * Actualiza el ancho de la salida.
     *
     * @param width nuevo ancho
     */
    public void setWidth(double width) { this.width = width; }

    /**
     * Retorna el alto de la salida.
     *
     * @return alto en unidades de mundo
     */
    public double getHeight() { return height; }

    /**
     * Actualiza el alto de la salida.
     *
     * @param height nuevo alto
     */
    public void setHeight(double height) { this.height = height; }
}
