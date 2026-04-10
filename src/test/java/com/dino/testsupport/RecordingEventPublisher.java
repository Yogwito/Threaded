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

    @Override
    public void publish(String event, Map<String, Object> payload) {
        events.add(new PublishedEvent(event, payload));
    }

    public List<PublishedEvent> getEvents() {
        return List.copyOf(events);
    }

    public long count(String eventName) {
        return events.stream().filter(event -> event.name().equals(eventName)).count();
    }

    /**
     * Evento emitido durante una prueba.
     */
    public record PublishedEvent(String name, Map<String, Object> payload) {
    }
}
