package com.dino.domain.entities;

/**
 * Puerta del nivel controlada por el botón.
 *
 * <p>Mientras está cerrada participa en la colisión; cuando se abre deja de
 * bloquear el paso de jugadores y bloques.</p>
 */
public class Door {
    private String id;
    private double x;
    private double y;
    private double width;
    private double height;
    private boolean open;

    /**
     * Constructor vacío requerido para snapshots y copias defensivas.
     */
    public Door() {}

    /**
     * Crea una puerta rectangular con geometría fija.
     *
     * @param id identificador lógico de la puerta
     * @param x coordenada X
     * @param y coordenada Y
     * @param width ancho de la puerta
     * @param height alto de la puerta
     */
    public Door(String id, double x, double y, double width, double height) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Retorna el identificador lógico de la puerta.
     *
     * @return id único dentro del nivel
     */
    public String getId() { return id; }

    /**
     * Actualiza el identificador lógico de la puerta.
     *
     * @param id nuevo identificador
     */
    public void setId(String id) { this.id = id; }

    /**
     * Retorna la coordenada X actual de la puerta.
     *
     * @return posición horizontal en mundo
     */
    public double getX() { return x; }

    /**
     * Actualiza la coordenada X de la puerta.
     *
     * @param x nueva posición horizontal
     */
    public void setX(double x) { this.x = x; }

    /**
     * Retorna la coordenada Y actual de la puerta.
     *
     * @return posición vertical en mundo
     */
    public double getY() { return y; }

    /**
     * Actualiza la coordenada Y de la puerta.
     *
     * @param y nueva posición vertical
     */
    public void setY(double y) { this.y = y; }

    /**
     * Retorna el ancho de la puerta.
     *
     * @return ancho en unidades de mundo
     */
    public double getWidth() { return width; }

    /**
     * Actualiza el ancho de la puerta.
     *
     * @param width nuevo ancho
     */
    public void setWidth(double width) { this.width = width; }

    /**
     * Retorna el alto de la puerta.
     *
     * @return alto en unidades de mundo
     */
    public double getHeight() { return height; }

    /**
     * Actualiza el alto de la puerta.
     *
     * @param height nuevo alto
     */
    public void setHeight(double height) { this.height = height; }

    /**
     * Indica si la puerta está abierta.
     *
     * @return {@code true} si ya no bloquea colisiones
     */
    public boolean isOpen() { return open; }

    /**
     * Actualiza el estado abierto de la puerta.
     *
     * @param open nuevo estado de apertura
     */
    public void setOpen(boolean open) { this.open = open; }
}
