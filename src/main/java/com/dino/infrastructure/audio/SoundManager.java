package com.dino.infrastructure.audio;

import com.dino.application.services.EventChannel;
import com.dino.application.services.SubscriptionGroup;
import com.dino.domain.events.EventNames;

/**
 * Gestor de audio procedural del juego.
 *
 * <p>No depende de archivos externos. Escucha eventos de un {@link EventChannel} y
 * genera tonos y acordes sencillos con Java Sound para maximizar portabilidad
 * en distintos equipos de laboratorio.</p>
 *
 * <p>En términos arquitectónicos actúa como adaptador entre el bus de eventos
 * del juego y el motor de síntesis procedural. La política de qué sonido
 * corresponde a cada evento vive aquí; la generación de audio crudo se delega
 * a {@link ProceduralAudioEngine}.</p>
 */
public class SoundManager {
    private final ProceduralAudioEngine audioEngine;
    private final SubscriptionGroup subscriptions = new SubscriptionGroup();

    /**
     * Registra las suscripciones del sistema de audio a los eventos del juego.
     *
     * @param eventBus canal de eventos compartido por gameplay, HUD y audio
     */
    public SoundManager(EventChannel eventBus) {
        this.audioEngine = new ProceduralAudioEngine();
        subscriptions.add(eventBus.subscribe(EventNames.GAME_STARTED, e -> {
            audioEngine.startBackgroundMusic();
            audioEngine.playCartoonChord(new int[]{392, 523, 659}, 220, 0.34, 0.10);
        }));
        subscriptions.add(eventBus.subscribe(EventNames.PLAYER_JUMPED, e ->
            audioEngine.playCartoonBlip(620, 920, 100, 0.32)));
        subscriptions.add(eventBus.subscribe(EventNames.PLAYER_COLLIDED, e ->
            audioEngine.playCartoonBlip(220, 170, 75, 0.30)));
        subscriptions.add(eventBus.subscribe(EventNames.THREAD_STRETCHED, e ->
            audioEngine.playCartoonBlip(340, 470, 90, 0.22)));
        subscriptions.add(eventBus.subscribe(EventNames.PUSH_BLOCK_MOVED, e ->
            audioEngine.playCartoonBlip(170, 130, 95, 0.24)));
        subscriptions.add(eventBus.subscribe(EventNames.BUTTON_STATE_CHANGED, e ->
            audioEngine.playCartoonChord(new int[]{660, 880}, 110, 0.24, 0.05)));
        subscriptions.add(eventBus.subscribe(EventNames.SCORE_CHANGED, e -> {
            int delta = e.containsKey("delta") ? ((Number) e.get("delta")).intValue() : 0;
            String reason = String.valueOf(e.getOrDefault("reason", ""));
            if (reason.contains("recogió moneda") || reason.contains("llego a la salida") || reason.contains("activo el boton")) {
                return;
            }
            if (delta < 0) {
                audioEngine.playCartoonBlip(250, 150, 130, 0.24);
            } else {
                audioEngine.playCartoonBlip(660, 840, 90, 0.22);
            }
        }));
        subscriptions.add(eventBus.subscribe(EventNames.PLAYER_REACHED_EXIT, e ->
            audioEngine.playCartoonChord(new int[]{784, 988, 1175}, 220, 0.30, 0.14)));
        subscriptions.add(eventBus.subscribe(EventNames.PLAYER_DIED, e ->
            audioEngine.playCartoonBlip(240, 110, 180, 0.26)));
        subscriptions.add(eventBus.subscribe(EventNames.ROOM_RESET, e ->
            audioEngine.playCartoonChord(new int[]{260, 190}, 200, 0.24, 0.04)));
        subscriptions.add(eventBus.subscribe(EventNames.LEVEL_ADVANCED, e ->
            audioEngine.playCartoonChord(new int[]{440, 587, 740}, 240, 0.30, 0.16)));
        subscriptions.add(eventBus.subscribe(EventNames.LEVEL_COMPLETED, e ->
            audioEngine.playCartoonChord(new int[]{523, 659, 784, 988}, 320, 0.34, 0.18)));
        subscriptions.add(eventBus.subscribe(EventNames.GAME_OVER, e -> {
            audioEngine.stopBackgroundMusic();
            audioEngine.playCartoonChord(new int[]{392, 330}, 420, 0.28, 0.08);
        }));
        subscriptions.add(eventBus.subscribe(EventNames.COIN_COLLECTED, e ->
            audioEngine.playCartoonBlip(900, 1380, 80, 0.26)));
    }

    /**
     * Libera el audio continuo al cerrar la aplicación o volver al menú.
     */
    public void close() {
        subscriptions.clear();
        audioEngine.close();
    }
}
