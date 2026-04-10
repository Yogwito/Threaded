package com.dino.application.services;

/**
 * Fases macro del ciclo de vida de una sesión local.
 *
 * <p>No modela cada detalle del protocolo, sino los estados visibles que usa
 * la aplicación para decidir qué coordinador debe estar activo y qué mensajes
 * tienen sentido aceptar en cada momento.</p>
 */
public enum SessionPhase {
    /**
     * No existe una sesión activa inicializada.
     */
    IDLE,

    /**
     * La sesión existe y se encuentra en el lobby previo a la partida.
     */
    LOBBY,

    /**
     * La partida está en curso y la escena principal debe estar activa.
     */
    PLAYING,

    /**
     * La campaña terminó y la aplicación muestra resultados finales.
     */
    GAME_OVER
}
