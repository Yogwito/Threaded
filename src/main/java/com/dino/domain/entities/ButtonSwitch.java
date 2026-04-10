package com.dino.domain.entities;

/**
 * Botón del nivel.
 *
 * <p>Cuando al menos un jugador se superpone con él, el host lo marca como
 * presionado y abre la puerta asociada.</p>
 */
public class ButtonSwitch {
    private String id;
    private double x;
    private double y;
    private double width;
    private double height;
    private boolean pressed;

    /**
     * Constructor vacío requerido para snapshots y copias defensivas.
     */
    public ButtonSwitch() {}

    /**
     * Crea un botón rectangular con geometría fija.
     *
     * @param id identificador lógico del botón
     * @param x coordenada X
     * @param y coordenada Y
     * @param width ancho del botón
     * @param height alto del botón
     */
    public ButtonSwitch(String id, double x, double y, double width, double height) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Retorna el identificador del botón.
     *
     * @return id lógico del botón
     */
    public String getId() { return id; }

    /**
     * Actualiza el identificador lógico del botón.
     *
     * @param id nuevo identificador
     */
    public void setId(String id) { this.id = id; }

    /**
     * Retorna la coordenada X del botón.
     *
     * @return posición horizontal en mundo
     */
    public double getX() { return x; }

    /**
     * Actualiza la coordenada X del botón.
     *
     * @param x nueva posición horizontal
     */
    public void setX(double x) { this.x = x; }

    /**
     * Retorna la coordenada Y del botón.
     *
     * @return posición vertical en mundo
     */
    public double getY() { return y; }

    /**
     * Actualiza la coordenada Y del botón.
     *
     * @param y nueva posición vertical
     */
    public void setY(double y) { this.y = y; }

    /**
     * Retorna el ancho del botón.
     *
     * @return ancho en unidades de mundo
     */
    public double getWidth() { return width; }

    /**
     * Actualiza el ancho del botón.
     *
     * @param width nuevo ancho
     */
    public void setWidth(double width) { this.width = width; }

    /**
     * Retorna el alto del botón.
     *
     * @return alto en unidades de mundo
     */
    public double getHeight() { return height; }

    /**
     * Actualiza el alto del botón.
     *
     * @param height nuevo alto
     */
    public void setHeight(double height) { this.height = height; }

    /**
     * Indica si el botón está siendo presionado en este instante.
     *
     * @return {@code true} si el host lo considera activado
     */
    public boolean isPressed() { return pressed; }

    /**
     * Actualiza el estado presionado del botón.
     *
     * @param pressed nuevo estado de activación
     */
    public void setPressed(boolean pressed) { this.pressed = pressed; }
}
