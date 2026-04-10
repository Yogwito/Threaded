package com.dino.application.services;

/**
 * Handle liviano para cancelar una suscripción al bus de eventos.
 *
 * <p>La interfaz permite que controladores y componentes temporales limpien sus
 * listeners al abandonar una escena, evitando fugas de memoria y reacciones
 * duplicadas cuando JavaFX reconstruye vistas.</p>
 */
@FunctionalInterface
public interface EventSubscription extends AutoCloseable {
    /**
     * Cancela la suscripción asociada.
     */
    void unsubscribe();

    /**
     * Alias de {@link #unsubscribe()} para uso con patrones de cierre.
     */
    @Override
    default void close() {
        unsubscribe();
    }
}
