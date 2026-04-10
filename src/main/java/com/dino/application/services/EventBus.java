package com.dino.application.services;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Consumer;

/**
 * Implementación sencilla del patrón Observer para eventos internos.
 *
 * <p>Permite que capas distintas del sistema se comuniquen sin acoplarse de
 * forma directa. Por ejemplo, el host publica eventos de gameplay, la UI los
 * refleja y el audio reproduce sonidos sin que unas clases conozcan a las
 * otras.</p>
 *
 * <p>El despacho es síncrono y ocurre en el mismo hilo que invoca
 * {@link #publish(String, Map)}. Por eso los consumidores de UI siguen
 * encapsulando el salto a {@code Platform.runLater(...)} cuando necesitan
 * actualizar controles JavaFX.</p>
 */
public class EventBus implements EventChannel {
    private static final Logger LOGGER = Logger.getLogger(EventBus.class.getName());
    private final Map<String, List<Consumer<Map<String, Object>>>> subscribers = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Registra un observador para un tipo de evento.
     *
     * @param event nombre lógico del evento
     * @param callback acción a ejecutar cuando el evento sea publicado
     */
    public EventSubscription subscribe(String event, Consumer<Map<String, Object>> callback) {
        lock.lock();
        try {
            subscribers.computeIfAbsent(event, k -> new ArrayList<>()).add(callback);
            return () -> unsubscribe(event, callback);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Elimina un observador previamente registrado.
     *
     * @param event nombre lógico del evento
     * @param callback observador a remover
     */
    public void unsubscribe(String event, Consumer<Map<String, Object>> callback) {
        lock.lock();
        try {
            List<Consumer<Map<String, Object>>> list = subscribers.get(event);
            if (list != null) list.remove(callback);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Publica un evento a todos los observadores actuales.
     *
     * <p>El método toma una copia de los suscriptores antes de iterar para no
     * fallar si algún observador modifica suscripciones durante la notificación.
     * Si un observador falla, el bus registra el error y continúa con el resto
     * para no romper en cascada la difusión del evento.</p>
     *
     * @param event nombre lógico del evento
     * @param payload datos asociados al evento
     */
    public void publish(String event, Map<String, Object> payload) {
        List<Consumer<Map<String, Object>>> snapshot;
        lock.lock();
        try {
            List<Consumer<Map<String, Object>>> list = subscribers.get(event);
            snapshot = list != null ? new ArrayList<>(list) : Collections.emptyList();
        } finally {
            lock.unlock();
        }
        for (Consumer<Map<String, Object>> cb : snapshot) {
            try { cb.accept(payload); }
            catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error al despachar evento {0}", event);
                LOGGER.log(Level.FINE, "Detalle del error de evento " + event, e);
            }
        }
    }
}
