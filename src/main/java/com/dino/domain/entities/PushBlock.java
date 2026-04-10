package com.dino.domain.entities;

/**
 * Bloque empujable del nivel.
 *
 * <p>Su función principal es cubrir el requisito de mover objetos dentro del
 * juego. El host simula su movimiento y luego lo replica a los clientes por
 * snapshot.</p>
 */
public class PushBlock {
    private String id;
    private double x;
    private double y;
    private double width;
    private double height;
    private double vx;
    private double vy;
    private double homeX;
    private double homeY;

    /**
     * Constructor vacío requerido para snapshots y reconstrucción defensiva.
     */
    public PushBlock() {}

    /**
     * Crea un bloque empujable con su geometría inicial.
     *
     * @param id identificador lógico del bloque
     * @param x coordenada X inicial
     * @param y coordenada Y inicial
     * @param width ancho del bloque
     * @param height alto del bloque
     */
    public PushBlock(String id, double x, double y, double width, double height) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.homeX = x;
        this.homeY = y;
    }

    /**
     * Retorna el identificador lógico del bloque.
     *
     * @return id único dentro del nivel
     */
    public String getId() { return id; }

    /**
     * Actualiza el identificador lógico del bloque.
     *
     * @param id nuevo identificador
     */
    public void setId(String id) { this.id = id; }

    /**
     * Retorna la coordenada X actual del bloque.
     *
     * @return posición horizontal en mundo
     */
    public double getX() { return x; }

    /**
     * Actualiza la coordenada X actual del bloque.
     *
     * @param x nueva posición horizontal
     */
    public void setX(double x) { this.x = x; }

    /**
     * Retorna la coordenada Y actual del bloque.
     *
     * @return posición vertical en mundo
     */
    public double getY() { return y; }

    /**
     * Actualiza la coordenada Y actual del bloque.
     *
     * @param y nueva posición vertical
     */
    public void setY(double y) { this.y = y; }

    /**
     * Retorna el ancho del bloque.
     *
     * @return ancho en unidades de mundo
     */
    public double getWidth() { return width; }

    /**
     * Actualiza el ancho del bloque.
     *
     * @param width nuevo ancho
     */
    public void setWidth(double width) { this.width = width; }

    /**
     * Retorna el alto del bloque.
     *
     * @return alto en unidades de mundo
     */
    public double getHeight() { return height; }

    /**
     * Actualiza el alto del bloque.
     *
     * @param height nuevo alto
     */
    public void setHeight(double height) { this.height = height; }

    /**
     * Retorna la velocidad horizontal actual del bloque.
     *
     * @return velocidad horizontal en mundo por segundo
     */
    public double getVx() { return vx; }

    /**
     * Actualiza la velocidad horizontal del bloque.
     *
     * @param vx nueva velocidad horizontal
     */
    public void setVx(double vx) { this.vx = vx; }

    /**
     * Retorna la velocidad vertical actual del bloque.
     *
     * @return velocidad vertical en mundo por segundo
     */
    public double getVy() { return vy; }

    /**
     * Actualiza la velocidad vertical del bloque.
     *
     * @param vy nueva velocidad vertical
     */
    public void setVy(double vy) { this.vy = vy; }

    /**
     * Retorna la posición X de reinicio del bloque.
     *
     * @return coordenada X de origen
     */
    public double getHomeX() { return homeX; }

    /**
     * Actualiza la posición X de reinicio del bloque.
     *
     * @param homeX nueva coordenada X de origen
     */
    public void setHomeX(double homeX) { this.homeX = homeX; }

    /**
     * Retorna la posición Y de reinicio del bloque.
     *
     * @return coordenada Y de origen
     */
    public double getHomeY() { return homeY; }

    /**
     * Actualiza la posición Y de reinicio del bloque.
     *
     * @param homeY nueva coordenada Y de origen
     */
    public void setHomeY(double homeY) { this.homeY = homeY; }
}
