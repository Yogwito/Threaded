package com.dino.application.services;

/**
 * Máquina de estados pequeña para el ciclo de vida de una sesión.
 *
 * <p>Evita que lobby, gameplay y cierre final se modelen como banderas sueltas
 * dispersas. El objetivo no es convertir el proyecto en un motor formal, sino
 * dejar explícitas las transiciones válidas y dar un punto único de decisión
 * para coordinadores y runtime.</p>
 *
 * <p>La máquina solo guarda la fase actual y delega la validación de
 * transiciones en {@link SessionTransitionPolicy}. Así se separa el estado de
 * la regla que define qué cambios son válidos.</p>
 */
final class SessionStateMachine {
    private final SessionTransitionPolicy transitionPolicy;
    private SessionPhase currentPhase = SessionPhase.IDLE;

    /**
     * Construye la máquina usando la política de transición por defecto.
     */
    SessionStateMachine() {
        this(new SessionTransitionPolicy());
    }

    /**
     * Construye la máquina con una política explícita.
     *
     * @param transitionPolicy política que valida cambios de fase
     */
    SessionStateMachine(SessionTransitionPolicy transitionPolicy) {
        this.transitionPolicy = transitionPolicy;
    }

    /**
     * Reinicia la sesión al estado inactivo.
     */
    void reset() {
        currentPhase = SessionPhase.IDLE;
    }

    /**
     * Marca que la sesión ya fue creada/unida y está en lobby.
     */
    void enterLobby() {
        transitionTo(SessionPhase.LOBBY);
    }

    /**
     * Marca que la partida principal ya comenzó.
     */
    void enterGameplay() {
        transitionTo(SessionPhase.PLAYING);
    }

    /**
     * Marca que la campaña terminó y se deben mostrar resultados.
     */
    void enterGameOver() {
        transitionTo(SessionPhase.GAME_OVER);
    }

    /**
     * Retorna la fase actual de la sesión.
     */
    SessionPhase currentPhase() {
        return currentPhase;
    }

    /**
     * Indica si la sesión está exactamente en la fase consultada.
     */
    boolean isIn(SessionPhase phase) {
        return currentPhase == phase;
    }

    private void transitionTo(SessionPhase target) {
        if (target == currentPhase) {
            return;
        }
        transitionPolicy.requireTransition(currentPhase, target);
        currentPhase = target;
    }
}
