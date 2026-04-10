package com.dino.domain.entities;

import com.dino.config.GameConfig;

/**
 * Entidad principal del dominio para representar a un jugador en lobby y partida.
 *
 * <p>Es un objeto mutable y serializable por snapshot. Acumula estado de
 * movimiento, puntaje, conexión y progreso dentro de la campaña.</p>
 */
public class Player {
    private String id;
    private String name;
    private String color;
    private double x;
    private double y;
    private double vx;
    private double vy;
    private double coyoteTimer;
    private boolean grounded;
    private boolean alive;
    private boolean atExit;
    private double targetX;
    private double targetY;
    private int score;
    private int deaths;
    private int finishOrder;
    private boolean connected;
    private boolean ready;

    /**
     * Constructor vacío requerido para reconstrucción por snapshot.
     */
    public Player() {}

    /**
     * Crea un jugador listo para registrarse en la sesión.
     *
     * @param id identificador único
     * @param name nombre visible
     * @param color color asignado dentro del juego
     */
    public Player(String id, String name, String color) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.alive = true;
        this.connected = true;
        this.ready = false;
        this.targetX = 0;
        this.targetY = 0;
    }

    /** @return ancho jugable del personaje según la configuración global */
    public double getWidth() {
        return GameConfig.PLAYER_WIDTH;
    }

    /** @return alto jugable del personaje según la configuración global */
    public double getHeight() {
        return GameConfig.PLAYER_HEIGHT;
    }

    /** @return coordenada X del centro del jugador */
    public double getCenterX() {
        return x + getWidth() / 2.0;
    }

    /** @return coordenada Y del centro del jugador */
    public double getCenterY() {
        return y + getHeight() / 2.0;
    }

    /**
     * Retorna el puntaje acumulado del jugador.
     *
     * @return puntaje actual, siempre no negativo
     */
    public int getScore() {
        return score;
    }

    /**
     * Reemplaza el puntaje acumulado del jugador.
     *
     * @param score nuevo puntaje absoluto
     */
    public void setScore(int score) {
        this.score = score;
    }

    /**
     * Suma o resta puntaje sin permitir valores negativos.
     *
     * @param delta cambio de puntaje a aplicar
     */
    public void addScore(int delta) {
        this.score = Math.max(0, this.score + delta);
    }

    /**
     * Retorna el identificador único del jugador dentro de la sesión.
     *
     * @return id lógico del jugador
     */
    public String getId() { return id; }

    /**
     * Actualiza el identificador del jugador.
     *
     * @param id nuevo id lógico
     */
    public void setId(String id) { this.id = id; }

    /**
     * Retorna el nombre visible del jugador.
     *
     * @return nombre mostrado en UI y ranking
     */
    public String getName() { return name; }

    /**
     * Actualiza el nombre visible del jugador.
     *
     * @param name nuevo nombre mostrado en UI
     */
    public void setName(String name) { this.name = name; }

    /**
     * Retorna la clave de color o skin asignada.
     *
     * @return identificador textual del color del jugador
     */
    public String getColor() { return color; }

    /**
     * Actualiza la clave de color o skin del jugador.
     *
     * @param color nuevo identificador visual
     */
    public void setColor(String color) { this.color = color; }

    /**
     * Retorna la coordenada X del jugador en espacio de mundo.
     *
     * @return posición horizontal actual
     */
    public double getX() { return x; }

    /**
     * Actualiza la coordenada X del jugador.
     *
     * @param x nueva posición horizontal
     */
    public void setX(double x) { this.x = x; }

    /**
     * Retorna la coordenada Y del jugador en espacio de mundo.
     *
     * @return posición vertical actual
     */
    public double getY() { return y; }

    /**
     * Actualiza la coordenada Y del jugador.
     *
     * @param y nueva posición vertical
     */
    public void setY(double y) { this.y = y; }

    /**
     * Retorna la velocidad horizontal actual.
     *
     * @return velocidad horizontal en unidades de mundo por segundo
     */
    public double getVx() { return vx; }

    /**
     * Actualiza la velocidad horizontal actual.
     *
     * @param vx nueva velocidad horizontal
     */
    public void setVx(double vx) { this.vx = vx; }

    /**
     * Retorna la velocidad vertical actual.
     *
     * @return velocidad vertical en unidades de mundo por segundo
     */
    public double getVy() { return vy; }

    /**
     * Actualiza la velocidad vertical del jugador.
     *
     * @param vy nueva velocidad vertical
     */
    public void setVy(double vy) { this.vy = vy; }

    /**
     * Retorna el tiempo restante de coyote jump.
     *
     * @return segundos restantes de tolerancia para saltar
     */
    public double getCoyoteTimer() { return coyoteTimer; }

    /**
     * Actualiza el tiempo restante de coyote jump.
     *
     * @param coyoteTimer nuevos segundos restantes
     */
    public void setCoyoteTimer(double coyoteTimer) { this.coyoteTimer = coyoteTimer; }

    /**
     * Indica si el jugador está apoyado sobre una superficie válida.
     *
     * @return {@code true} si puede considerarse grounded
     */
    public boolean isGrounded() { return grounded; }

    /**
     * Actualiza el estado grounded del jugador.
     *
     * @param grounded nuevo estado de apoyo
     */
    public void setGrounded(boolean grounded) { this.grounded = grounded; }

    /**
     * Indica si el jugador sigue vivo en la sala actual.
     *
     * @return {@code true} si no ha muerto por caída o hazard
     */
    public boolean isAlive() { return alive; }

    /**
     * Actualiza el estado de vida del jugador.
     *
     * @param alive nuevo estado de vida
     */
    public void setAlive(boolean alive) { this.alive = alive; }

    /**
     * Indica si el jugador se encuentra dentro de la salida.
     *
     * @return {@code true} si está ocupando la zona de meta
     */
    public boolean isAtExit() { return atExit; }

    /**
     * Actualiza si el jugador está dentro de la salida.
     *
     * @param atExit nuevo estado de salida
     */
    public void setAtExit(boolean atExit) { this.atExit = atExit; }

    /**
     * Retorna la X del objetivo de movimiento más reciente.
     *
     * @return coordenada X objetivo en mundo
     */
    public double getTargetX() { return targetX; }

    /**
     * Actualiza la X del objetivo de movimiento.
     *
     * @param targetX nueva coordenada objetivo
     */
    public void setTargetX(double targetX) { this.targetX = targetX; }

    /**
     * Retorna la Y del objetivo de movimiento más reciente.
     *
     * @return coordenada Y objetivo en mundo
     */
    public double getTargetY() { return targetY; }

    /**
     * Actualiza la Y del objetivo de movimiento.
     *
     * @param targetY nueva coordenada objetivo
     */
    public void setTargetY(double targetY) { this.targetY = targetY; }

    /**
     * Retorna la cantidad acumulada de muertes en la campaña actual.
     *
     * @return contador de muertes del jugador
     */
    public int getDeaths() { return deaths; }

    /**
     * Reemplaza el contador de muertes.
     *
     * @param deaths nuevo valor absoluto del contador
     */
    public void setDeaths(int deaths) { this.deaths = deaths; }

    /**
     * Retorna el orden en que llegó a la meta del nivel.
     *
     * @return orden de llegada o 0 si aún no llegó
     */
    public int getFinishOrder() { return finishOrder; }

    /**
     * Actualiza el orden de llegada del jugador.
     *
     * @param finishOrder nuevo orden de llegada
     */
    public void setFinishOrder(int finishOrder) { this.finishOrder = finishOrder; }

    /**
     * Indica si el jugador sigue conectado a la sesión.
     *
     * @return {@code true} si el peer se considera activo
     */
    public boolean isConnected() { return connected; }

    /**
     * Actualiza el estado de conectividad del jugador.
     *
     * @param connected nuevo estado de conectividad
     */
    public void setConnected(boolean connected) { this.connected = connected; }

    /**
     * Indica si el jugador ya marcó listo en el lobby.
     *
     * @return {@code true} si está listo para iniciar
     */
    public boolean isReady() { return ready; }

    /**
     * Actualiza el estado listo del jugador en el lobby.
     *
     * @param ready nuevo estado listo
     */
    public void setReady(boolean ready) { this.ready = ready; }
}
