package com.dino.config;

import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;

import java.util.Map;

/**
 * Configuración central del juego.
 *
 * <p>Reúne constantes de ventana, física, red, cámara, puntaje y elementos del
 * nivel. Se usa para evitar números mágicos dispersos y facilitar el ajuste del
 * balance sin tocar la lógica de las clases principales.</p>
 */
public final class GameConfig {
    /** Tecla que el host puede presionar durante el juego para reiniciar la sala actual. */
    public static final KeyCode RESTART_KEY = KeyCode.R;

    /** Ancho fijo de la ventana principal. */
    public static final int WINDOW_WIDTH = 1280;
    /** Alto fijo de la ventana principal. */
    public static final int WINDOW_HEIGHT = 780;
    /** Frecuencia objetivo de render. */
    public static final int FPS = 60;
    /**
     * Frecuencia con la que el host difunde snapshots UDP.
     *
     * <p>Es la fuente de verdad para la sincronización periódica; el runtime no
     * debe duplicar este valor con otro intervalo hardcodeado.</p>
     */
    public static final int SNAPSHOT_RATE_HZ = 30;

    public static final int LEVEL_WIDTH = 1800;
    public static final int LEVEL_HEIGHT = 900;
    public static final double VIEWPORT_W = 980.0;
    public static final double VIEWPORT_H = 620.0;

    public static final int MAX_PLAYERS = 4;
    public static final double PLAYER_WIDTH = 42.0;
    public static final double PLAYER_HEIGHT = 52.0;
    public static final double MOVE_SPEED = 260.0;
    public static final double MOVE_ACCELERATION = 1850.0;
    public static final double AIR_MOVE_ACCELERATION = 1100.0;
    public static final double MOVE_FRICTION = 2200.0;
    public static final double JUMP_FORCE = -560.0;
    public static final double JUMP_VELOCITY = -560.0;
    public static final double GRAVITY = 1480.0;
    public static final double COYOTE_TIME_SECONDS = 0.11;
    public static final double JUMP_BUFFER_SECONDS = 0.12;
    public static final double FALL_RESET_Y = LEVEL_HEIGHT + 120.0;
    public static final double THREAD_MAX_DISTANCE = 300.0;
    public static final double THREAD_REST_DISTANCE = 200.0;
    public static final double THREAD_TENSE_DISTANCE = 245.0;
    public static final double THREAD_CRITICAL_DISTANCE = 292.0;
    public static final double THREAD_PULL_FACTOR = 6.4;
    public static final double THREAD_DAMPING = 2.4;
    public static final double THREAD_VERTICAL_PULL = 0.16;
    public static final double THREAD_HARD_LIMIT = 340.0;
    public static final double THREAD_HARD_PULL_FACTOR = 11.2;
    public static final double THREAD_HARD_DAMPING = 3.6;
    public static final double THREAD_MAX_POSITION_CORRECTION_PER_TICK = 18.0;
    public static final double THREAD_POSITION_STEP = 4.0;
    public static final double THREAD_SEPARATION_CANCEL_FACTOR = 0.9;
    public static final double THREAD_OBSTRUCTION_MARGIN = 2.0;
    public static final double THREAD_GROUNDED_VERTICAL_FACTOR = 0.05;
    public static final double THREAD_AIR_VERTICAL_FACTOR = 0.16;
    public static final double PLAYER_COLLISION_CONTACT_MARGIN = 9.0;
    public static final double PLAYER_COLLISION_VELOCITY_DAMPING = 0.72;
    public static final double PLAYER_COLLISION_CARRY_RATIO = 0.35;
    public static final double TARGET_REACHED_TOLERANCE = 8.0;
    public static final double CLIENT_INPUT_RESEND_SECONDS = 0.033;
    public static final double REMOTE_SMOOTHING = 0.18;
    public static final double VISUAL_STRETCH_SMOOTHING = 0.2;
    public static final double REMOTE_PREDICTION_SECONDS = 0.10;
    public static final double LOCAL_CLIENT_PREDICTION_SECONDS = 0.06;
    public static final double SNAPSHOT_INTERPOLATION_DELAY_SECONDS = 0.09;
    public static final int MAX_BUFFERED_SNAPSHOTS = 8;
    public static final int CRITICAL_BROADCAST_REPEATS = 3;
    public static final int CRITICAL_BROADCAST_DELAY_MS = 120;
    public static final double CAMERA_GROUP_INFLUENCE = 0.26;
    public static final double SNAPSHOT_STALE_WARNING_SECONDS = 0.28;

    public static final double BUTTON_WIDTH = 80.0;
    public static final double BUTTON_HEIGHT = 16.0;
    public static final double DOOR_WIDTH = 56.0;
    public static final double DOOR_HEIGHT = 148.0;
    public static final double EXIT_WIDTH = 120.0;
    public static final double EXIT_HEIGHT = 110.0;
    public static final double PUSH_BLOCK_WIDTH = 68.0;
    public static final double PUSH_BLOCK_HEIGHT = 68.0;
    public static final double PUSH_BLOCK_GRAVITY = 1480.0;
    public static final double PUSH_BLOCK_FRICTION = 1800.0;
    public static final double PUSH_BLOCK_MAX_SPEED = 220.0;
    public static final double PUSH_BLOCK_PUSH_IMPULSE = 0.78;
    /** Reducción de velocidad horizontal del jugador al colisionar con un bloque empujable. */
    public static final double PUSH_BLOCK_PLAYER_VX_DAMPING = 0.55;
    /** Velocidad mínima del bloque para emitir el evento de sonido de empuje (unidades/s). */
    public static final double PUSH_BLOCK_SOUND_SPEED_THRESHOLD = 18.0;
    /** Cooldown en segundos entre eventos de sonido de empuje de bloque. */
    public static final double PUSH_BLOCK_SOUND_COOLDOWN_SECONDS = 0.18;

    /** Factor de amortiguación horizontal aplicado al jugador cuando el hilo excede su límite duro. */
    public static final double THREAD_WALL_HIT_VX_DAMPING = 0.85;
    /** Factor de amortiguación vertical aplicado al jugador cuando el hilo excede su límite duro. */
    public static final double THREAD_WALL_HIT_VY_DAMPING = 0.60;
    /** Movilidad relativa de un jugador en suelo para repartir impulsos del hilo (menor = se mueve menos). */
    public static final double THREAD_MOBILITY_GROUNDED = 0.35;
    /** Movilidad relativa de un jugador en aire para repartir impulsos del hilo. */
    public static final double THREAD_MOBILITY_AIR = 0.65;
    /** Cooldown en segundos entre eventos de sonido de tensión del hilo. */
    public static final double THREAD_SOUND_COOLDOWN_SECONDS = 0.16;
    /** Estiramiento mínimo en unidades de mundo para activar el sonido del hilo. */
    public static final double THREAD_STRETCH_SOUND_THRESHOLD = 8.0;

    public static final double COIN_SIZE = 16.0;
    public static final int SCORE_COIN_SMALL = 10;
    public static final int SCORE_COIN_LARGE = 25;

    public static final int SCORE_BUTTON_PRESS = 25;
    public static final int SCORE_FIRST_EXIT = 100;
    public static final int SCORE_SECOND_EXIT = 70;
    public static final int SCORE_LATE_EXIT = 50;
    public static final int SCORE_FALL_PENALTY = 15;

    public static final double CAMERA_SMOOTHING = 0.16;
    public static final double BASE_ZOOM = 1.0;
    public static final double MIN_ZOOM = 0.92;
    public static final double MAX_ZOOM = 1.05;

    /** Margen de píxeles extra alrededor del viewport para evitar pop-in de objetos en los bordes. */
    public static final double RENDERER_VISIBILITY_MARGIN = 96.0;
    /** Duración en milisegundos del flash rojo que aparece al morir. */
    public static final long DEATH_FLASH_DURATION_MS = 500;
    /** Opacidad máxima (0–1) del overlay rojo de muerte al inicio del flash. */
    public static final double DEATH_FLASH_MAX_ALPHA = 0.60;
    /** Duración en frames de una partícula de moneda antes de desaparecer. */
    public static final int COIN_PARTICLE_FRAMES = 25;
    /** Aceleración gravitatoria aplicada por frame a las partículas de moneda (unidades de mundo). */
    public static final double COIN_PARTICLE_GRAVITY = 0.30;
    /** Semi-rango de la velocidad horizontal aleatoria de partículas de moneda (±VX_RANGE). */
    public static final double COIN_PARTICLE_VX_RANGE = 3.0;
    /** Velocidad mínima de ascenso de partículas de moneda (unidades de mundo/frame). */
    public static final double COIN_PARTICLE_VY_MIN = 1.0;
    /** Rango adicional de velocidad de ascenso aleatoria de partículas de moneda. */
    public static final double COIN_PARTICLE_VY_RANGE = 4.0;

    /** Colores base disponibles para identificar a cada jugador. */
    public static final Map<String, Color> COLORS = Map.of(
        "red", Color.web("#ef476f"),
        "blue", Color.web("#4cc9f0"),
        "green", Color.web("#80ed99"),
        "yellow", Color.web("#ffd166")
    );

    /**
     * Evita instanciación accidental; la clase actúa como contenedor estático.
     */
    private GameConfig() {}
}
