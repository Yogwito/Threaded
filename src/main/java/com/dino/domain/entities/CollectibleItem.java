package com.dino.domain.entities;

/**
 * Moneda o ítem coleccionable del nivel.
 *
 * <p>Entrega puntaje individual cuando un jugador la recoge y luego queda
 * inactiva hasta el siguiente reinicio de sala.</p>
 */
public class CollectibleItem {
    private String id;
    private double x;
    private double y;
    private int points;
    private boolean active;

    /**
     * Constructor vacío requerido para snapshots y copias defensivas.
     */
    public CollectibleItem() {}

    /**
     * Crea un ítem coleccionable activo por defecto.
     *
     * @param id identificador lógico del ítem
     * @param x coordenada X del ítem
     * @param y coordenada Y del ítem
     * @param points puntaje otorgado al recogerlo
     */
    public CollectibleItem(String id, double x, double y, int points) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.points = points;
        this.active = true;
    }

    /**
     * Retorna el identificador lógico del ítem.
     *
     * @return id único dentro del nivel
     */
    public String getId() { return id; }

    /**
     * Actualiza el identificador lógico del ítem.
     *
     * @param id nuevo identificador
     */
    public void setId(String id) { this.id = id; }

    /**
     * Retorna la coordenada X actual del ítem.
     *
     * @return posición horizontal en mundo
     */
    public double getX() { return x; }

    /**
     * Actualiza la coordenada X del ítem.
     *
     * @param x nueva posición horizontal
     */
    public void setX(double x) { this.x = x; }

    /**
     * Retorna la coordenada Y actual del ítem.
     *
     * @return posición vertical en mundo
     */
    public double getY() { return y; }

    /**
     * Actualiza la coordenada Y del ítem.
     *
     * @param y nueva posición vertical
     */
    public void setY(double y) { this.y = y; }

    /**
     * Retorna el puntaje otorgado por el ítem.
     *
     * @return puntos concedidos al recogerlo
     */
    public int getPoints() { return points; }

    /**
     * Actualiza el puntaje otorgado por el ítem.
     *
     * @param points nuevo valor del ítem
     */
    public void setPoints(int points) { this.points = points; }

    /**
     * Indica si el ítem sigue disponible para ser recogido.
     *
     * @return {@code true} si aún está activo
     */
    public boolean isActive() { return active; }

    /**
     * Actualiza si el ítem está disponible.
     *
     * @param active nuevo estado de disponibilidad
     */
    public void setActive(boolean active) { this.active = active; }
}
