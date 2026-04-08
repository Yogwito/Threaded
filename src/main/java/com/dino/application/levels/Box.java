package com.dino.application.levels;

/**
 * Caja rectangular declarada dentro de la matriz del nivel.
 *
 * <p>Actúa como representación intermedia de un bloque antes de convertirse en
 * la entidad jugable usada por la simulación. Sus coordenadas ya están en
 * espacio de mundo.</p>
 */
public class Box {
    private final double x;
    private final double y;
    private final double width;
    private final double height;

    /**
     * Crea una caja estática a partir de los datos cargados del nivel.
     *
     * @param x coordenada X en mundo
     * @param y coordenada Y en mundo
     * @param width ancho de la caja
     * @param height alto de la caja
     */
    public Box(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** @return coordenada X en mundo */
    public double getX() { return x; }
    /** @return coordenada Y en mundo */
    public double getY() { return y; }
    /** @return ancho de la caja */
    public double getWidth() { return width; }
    /** @return alto de la caja */
    public double getHeight() { return height; }
}
