package com.dino.application.services;

import java.util.Map;

/**
 * Contrato mínimo para publicar eventos internos del juego.
 *
 * <p>Lo usan los servicios de dominio y aplicación que solo necesitan emitir
 * eventos, sin conocer cómo se gestionan las suscripciones.</p>
 */
public interface EventPublisher {
    /**
     * Publica un evento con su payload asociado.
     *
     * @param event nombre lógico del evento
     * @param payload datos serializables del evento
     */
    void publish(String event, Map<String, Object> payload);
}
