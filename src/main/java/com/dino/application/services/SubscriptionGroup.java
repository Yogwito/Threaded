package com.dino.application.services;

import java.util.ArrayList;
import java.util.List;

/**
 * Agrupa varias suscripciones temporales para cerrarlas juntas.
 *
 * <p>Se usa principalmente en controladores JavaFX, donde una escena suele
 * registrar varios listeners al activarse y debe liberarlos todos al salir de
 * pantalla.</p>
 */
public final class SubscriptionGroup implements AutoCloseable {
    private final List<EventSubscription> subscriptions = new ArrayList<>();

    /**
     * Registra una suscripción dentro del grupo y la retorna para uso fluido.
     *
     * @param subscription handle a conservar
     * @return la misma suscripción recibida
     */
    public EventSubscription add(EventSubscription subscription) {
        if (subscription != null) {
            subscriptions.add(subscription);
        }
        return subscription;
    }

    /**
     * Cancela todas las suscripciones acumuladas y deja el grupo reutilizable.
     */
    public void clear() {
        for (EventSubscription subscription : List.copyOf(subscriptions)) {
            try {
                subscription.unsubscribe();
            } catch (Exception ignored) {
            }
        }
        subscriptions.clear();
    }

    /**
     * Alias de {@link #clear()} para uso con {@link AutoCloseable}.
     */
    @Override
    public void close() {
        clear();
    }
}
