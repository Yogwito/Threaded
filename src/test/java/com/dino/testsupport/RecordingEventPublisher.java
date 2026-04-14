package com.dino.testsupport;

import com.dino.application.services.EventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Publicador de eventos en memoria para verificar emisiones en tests.
 */
public final class RecordingEventPublisher implements EventPublisher {
    private final List<PublishedEvent> events = new ArrayList<>();

    /**
     * Registra una emisión sin despachar listeners reales.
     *
     * @param event nombre lógico publicado por la prueba
     * @param payload datos asociados a la emisión
     */
    @Override
    public void publish(String event, Map<String, Object> payload) {
        events.add(new PublishedEvent(event, payload));
    }

    /**
     * Devuelve una copia inmutable de todos los eventos publicados.
     *
     * @return eventos registrados durante la prueba
     */
    public List<PublishedEvent> getEvents() {
        return List.copyOf(events);
    }

    /**
     * Cuenta cuántas veces se publicó un nombre de evento concreto.
     *
     * @param eventName nombre lógico del evento
     * @return cantidad de emisiones con ese nombre
     */
    public long count(String eventName) {
        return events.stream().filter(event -> event.name().equals(eventName)).count();
    }

    /**
     * Evento emitido durante una prueba.
     *
     * @param name nombre lógico del evento
     * @param payload payload asociado a la emisión
     */
    public record PublishedEvent(String name, Map<String, Object> payload) {
    }
}
