package com.dino.application.services;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * Política explícita de transiciones válidas para el ciclo de vida de sesión.
 *
 * <p>Separa de {@link SessionStateMachine} la definición de qué cambios de fase
 * están permitidos. Esto vuelve más testeable el flujo menú → lobby → partida
 * → game over y evita que la máquina mezcle almacenamiento de estado con reglas
 * de transición.</p>
 */
public final class SessionTransitionPolicy {
    private final Map<SessionPhase, EnumSet<SessionPhase>> allowedTransitions;

    /**
     * Construye la política por defecto usada por el runtime actual.
     */
    public SessionTransitionPolicy() {
        this.allowedTransitions = buildDefaultTransitions();
    }

    /**
     * Indica si una transición concreta está permitida.
     *
     * @param from fase actual
     * @param to fase objetivo
     * @return {@code true} si el cambio es válido según la política
     */
    public boolean canTransition(SessionPhase from, SessionPhase to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        return allowedTransitions.getOrDefault(from, EnumSet.noneOf(SessionPhase.class)).contains(to);
    }

    /**
     * Retorna las fases destino permitidas desde una fase concreta.
     *
     * @param from fase origen
     * @return conjunto inmutable de destinos válidos
     */
    public EnumSet<SessionPhase> allowedTargetsFrom(SessionPhase from) {
        EnumSet<SessionPhase> allowed = allowedTransitions.getOrDefault(from, EnumSet.noneOf(SessionPhase.class));
        return allowed.isEmpty() ? EnumSet.noneOf(SessionPhase.class) : EnumSet.copyOf(allowed);
    }

    /**
     * Verifica una transición y lanza una excepción descriptiva si no es válida.
     *
     * @param from fase actual
     * @param to fase objetivo
     * @throws IllegalStateException si la transición no está permitida
     */
    public void requireTransition(SessionPhase from, SessionPhase to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Transición de sesión inválida: " + from + " -> " + to);
        }
    }

    private Map<SessionPhase, EnumSet<SessionPhase>> buildDefaultTransitions() {
        Map<SessionPhase, EnumSet<SessionPhase>> transitions = new EnumMap<>(SessionPhase.class);
        transitions.put(SessionPhase.IDLE, EnumSet.of(SessionPhase.LOBBY));
        transitions.put(SessionPhase.LOBBY, EnumSet.of(SessionPhase.PLAYING, SessionPhase.IDLE));
        transitions.put(SessionPhase.PLAYING, EnumSet.of(SessionPhase.GAME_OVER, SessionPhase.IDLE));
        transitions.put(SessionPhase.GAME_OVER, EnumSet.of(SessionPhase.IDLE, SessionPhase.LOBBY));
        return transitions;
    }
}
