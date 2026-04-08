package com.dino.application.levels;

/**
 * Moneda coleccionable declarada dentro de la matriz del nivel.
 *
 * <p>Actúa como representación intermedia antes de convertirse en la entidad
 * {@link com.dino.domain.entities.CollectibleItem} usada por la simulación.
 * Sus coordenadas son la esquina superior-izquierda del tile en espacio de
 * mundo; el host las centra al construir la entidad real.</p>
 */
public class Coin {
    private final double x;
    private final double y;
    private final int points;

    /**
     * Crea una moneda con posición y valor dados.
     *
     * @param x coordenada X del tile en mundo
     * @param y coordenada Y del tile en mundo
     * @param points valor en puntos de la moneda
     */
    public Coin(double x, double y, int points) {
        this.x = x;
        this.y = y;
        this.points = points;
    }

    /** @return coordenada X del tile en mundo */
    public double getX() { return x; }
    /** @return coordenada Y del tile en mundo */
    public double getY() { return y; }
    /** @return puntos que otorga al ser recogida */
    public int getPoints() { return points; }
}
