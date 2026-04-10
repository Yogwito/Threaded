package com.dino.presentation.flow;

/**
 * Mensaje breve de feedback contextual para la HUD de gameplay.
 *
 * <p>Representa solo la información de presentación necesaria para resaltar un
 * evento local del jugador sin acoplar la vista al payload completo del bus de
 * eventos.</p>
 *
 * @param text texto visible para el jugador
 * @param colorHex color destacado en formato hexadecimal CSS
 */
public record GameplayFeedback(String text, String colorHex) {
}
