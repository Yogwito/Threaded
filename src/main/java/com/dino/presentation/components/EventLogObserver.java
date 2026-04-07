package com.dino.presentation.components;

import com.dino.application.services.EventBus;
import com.dino.domain.events.EventNames;

import java.util.*;

/**
 * Observador de UI que mantiene un log corto con los eventos más recientes.
 *
 * <p>Escucha eventos del bus global y los transforma en mensajes de texto listos
 * para renderizar en la HUD de la partida. No conoce controles JavaFX ni
 * modifica el estado del juego; solo resume actividad relevante para el
 * jugador.</p>
 */
public class EventLogObserver {
    private final Deque<String> entries = new ArrayDeque<>();
    private static final int MAX = 5;

    /**
     * Registra suscripciones al bus para construir el log incremental.
     *
     * @param eventBus bus de eventos interno de la aplicación
     */
    public EventLogObserver(EventBus eventBus) {
        eventBus.subscribe(EventNames.COIN_COLLECTED, e -> {
            String player = (String) e.getOrDefault("playerId", "?");
            int pts = e.containsKey("points") ? ((Number) e.get("points")).intValue() : 0;
            add(player + " recogio moneda +" + pts);
        });
        eventBus.subscribe(EventNames.SCORE_CHANGED, e -> {
            String player = (String) e.getOrDefault("playerId", "?");
            int delta = e.containsKey("delta") ? ((Number) e.get("delta")).intValue() : 0;
            String reason = String.valueOf(e.getOrDefault("reason", ""));
            add(player + " " + (delta >= 0 ? "+" : "") + delta + " " + reason);
        });
        eventBus.subscribe(EventNames.PLAYER_JUMPED, e -> {
            String player = (String) e.getOrDefault("playerId", "?");
            add(player + " salto");
        });
        eventBus.subscribe(EventNames.PLAYER_REACHED_EXIT, e -> {
            String player = (String) e.getOrDefault("playerId", "?");
            int order = e.containsKey("finishOrder") ? ((Number) e.get("finishOrder")).intValue() : 0;
            add(player + " llego a la salida #" + order);
        });
        eventBus.subscribe(EventNames.ROOM_RESET, e -> {
            add("Reinicio: " + e.getOrDefault("reason", "sin motivo"));
        });
    }

    private void add(String msg) {
        entries.addFirst(msg);
        while (entries.size() > MAX) entries.removeLast();
    }

    /**
     * Devuelve una copia del log actual en orden de más reciente a más antiguo.
     *
     * @return entradas visibles para la interfaz
     */
    public List<String> getEntries() {
        return new ArrayList<>(entries);
    }
}
