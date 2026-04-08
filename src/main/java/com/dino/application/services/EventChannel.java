package com.dino.application.services;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Canal de eventos completo para componentes que además de publicar también
 * necesitan observar cambios.
 */
public interface EventChannel extends EventPublisher {
    /**
     * Registra un observador para un tipo de evento.
     *
     * @param event nombre lógico del evento
     * @param callback acción a ejecutar cuando el evento sea publicado
     */
    void subscribe(String event, Consumer<Map<String, Object>> callback);

    /**
     * Elimina un observador previamente registrado.
     *
     * @param event nombre lógico del evento
     * @param callback observador a remover
     */
    void unsubscribe(String event, Consumer<Map<String, Object>> callback);
}
