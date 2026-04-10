package com.dino.domain.events;

/**
 * Catálogo de eventos internos publicados en el {@code EventBus}.
 *
 * <p>Se usa como contrato informal entre la lógica del host, la interfaz y el
 * audio. Mantener todos los nombres en un solo lugar evita errores por cadenas
 * duplicadas o mal escritas.</p>
 */
public final class EventNames {
    public static final String PLAYER_JOINED = "PLAYER_JOINED";
    public static final String PLAYER_READY = "PLAYER_READY";
    public static final String GAME_STARTED = "GAME_STARTED";
    public static final String SNAPSHOT_RECEIVED = "SNAPSHOT_RECEIVED";
    public static final String BUTTON_STATE_CHANGED = "BUTTON_STATE_CHANGED";
    public static final String PLAYER_DIED = "PLAYER_DIED";
    public static final String PLAYER_JUMPED = "PLAYER_JUMPED";
    public static final String PLAYER_COLLIDED = "PLAYER_COLLIDED";
    public static final String THREAD_STRETCHED = "THREAD_STRETCHED";
    public static final String PLAYER_REACHED_EXIT = "PLAYER_REACHED_EXIT";
    public static final String SCORE_CHANGED = "SCORE_CHANGED";
    public static final String ROOM_RESET = "ROOM_RESET";
    public static final String LEVEL_ADVANCED = "LEVEL_ADVANCED";
    public static final String LEVEL_COMPLETED = "LEVEL_COMPLETED";
    public static final String GAME_OVER = "GAME_OVER";
    public static final String PLAYER_DISCONNECTED = "PLAYER_DISCONNECTED";
    public static final String COIN_COLLECTED = "COIN_COLLECTED";
    public static final String PUSH_BLOCK_MOVED = "PUSH_BLOCK_MOVED";

    private EventNames() {
    }
}
