package com.dino.domain.entities;

/**
 * Entidad residual del proyecto anterior.
 *
 * <p>Ya no forma parte del loop principal de {@code Threaded}, pero se conserva
 * para no romper compatibilidad con etapas previas del repositorio.</p>
 */
public class PenaltyZone {
    private String id;
    private double x;
    private double y;
    private double radius;
    private double triggerMass;
    private double burstRatio;

    /**
     * Constructor vacío conservado por compatibilidad histórica.
     */
    public PenaltyZone() {}

    /**
     * Crea una zona de penalización heredada de iteraciones previas.
     *
     * @param id identificador lógico de la zona
     * @param x coordenada X del centro o referencia
     * @param y coordenada Y del centro o referencia
     * @param radius radio configurado
     * @param triggerMass umbral de activación legado
     * @param burstRatio ratio de penalización legado
     */
    public PenaltyZone(String id, double x, double y, double radius, double triggerMass, double burstRatio) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.triggerMass = triggerMass;
        this.burstRatio = burstRatio;
    }

    /**
     * Retorna el identificador lógico de la zona.
     *
     * @return id legado de la zona
     */
    public String getId() { return id; }

    /**
     * Actualiza el identificador lógico de la zona.
     *
     * @param id nuevo identificador
     */
    public void setId(String id) { this.id = id; }

    /**
     * Retorna la coordenada X almacenada.
     *
     * @return posición horizontal configurada
     */
    public double getX() { return x; }

    /**
     * Actualiza la coordenada X almacenada.
     *
     * @param x nueva posición horizontal
     */
    public void setX(double x) { this.x = x; }

    /**
     * Retorna la coordenada Y almacenada.
     *
     * @return posición vertical configurada
     */
    public double getY() { return y; }

    /**
     * Actualiza la coordenada Y almacenada.
     *
     * @param y nueva posición vertical
     */
    public void setY(double y) { this.y = y; }

    /**
     * Retorna el radio legado de la zona.
     *
     * @return radio configurado
     */
    public double getRadius() { return radius; }

    /**
     * Actualiza el radio legado de la zona.
     *
     * @param radius nuevo radio configurado
     */
    public void setRadius(double radius) { this.radius = radius; }

    /**
     * Retorna la masa umbral histórica asociada a la zona.
     *
     * @return valor legado de activación
     */
    public double getTriggerMass() { return triggerMass; }

    /**
     * Actualiza la masa umbral histórica de la zona.
     *
     * @param triggerMass nuevo umbral legado
     */
    public void setTriggerMass(double triggerMass) { this.triggerMass = triggerMass; }

    /**
     * Retorna el ratio de ráfaga/pérdida legado.
     *
     * @return ratio histórico configurado
     */
    public double getBurstRatio() { return burstRatio; }

    /**
     * Actualiza el ratio legado de la zona.
     *
     * @param burstRatio nuevo ratio configurado
     */
    public void setBurstRatio(double burstRatio) { this.burstRatio = burstRatio; }
}
