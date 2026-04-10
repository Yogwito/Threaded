package com.dino.presentation.components;

import com.dino.application.services.EventChannel;
import com.dino.application.services.SessionService;
import com.dino.domain.entities.Player;
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
    private final SessionService sessionService;

    /**
     * Registra suscripciones al bus para construir el log incremental.
     *
     * @param eventBus bus de eventos interno de la aplicación
     */
    public EventLogObserver(EventChannel eventBus, SessionService sessionService) {
        this.sessionService = sessionService;
        eventBus.subscribe(EventNames.COIN_COLLECTED, e -> {
            String player = resolvePlayerName((String) e.get("playerId"));
            int pts = e.containsKey("points") ? ((Number) e.get("points")).intValue() : 0;
            add(player + " recogio moneda +" + pts);
        });
        eventBus.subscribe(EventNames.SCORE_CHANGED, e -> {
            String player = resolvePlayerName((String) e.get("playerId"));
            int delta = e.containsKey("delta") ? ((Number) e.get("delta")).intValue() : 0;
            String reason = String.valueOf(e.getOrDefault("reason", ""));
            add(player + " " + (delta >= 0 ? "+" : "") + delta + " " + reason);
        });
        eventBus.subscribe(EventNames.PLAYER_JUMPED, e -> {
            String player = resolvePlayerName((String) e.get("playerId"));
            add(player + " salto");
        });
        eventBus.subscribe(EventNames.PLAYER_REACHED_EXIT, e -> {
            String player = resolvePlayerName((String) e.get("playerId"));
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

    private String resolvePlayerName(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return "?";
        }
        for (Player player : sessionService.getPlayersSnapshot()) {
            if (playerId.equals(player.getId())) {
                return player.getName();
            }
        }
        return playerId;
    }
}
