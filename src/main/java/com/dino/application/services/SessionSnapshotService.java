package com.dino.application.services;

import java.util.Map;

/**
 * Servicio especializado para construir y aplicar snapshots autoritativos.
 *
 * <p>Extrae de {@link SessionService} la coordinación del pipeline de
 * snapshots: secuenciación entrante, proyección a payload serializable y
 * reconstrucción del estado vivo. Esto deja al store principal centrado en
 * conservar estado y delega la traducción de snapshots a un colaborador
 * cohesivo.</p>
 *
 * <p>No implementa transporte UDP ni validación estructural del protocolo.
 * Asume que el mensaje ya fue aceptado por la capa de coordinadores y se
 * concentra solo en convertir entre estado vivo y payload de snapshot.</p>
 */
final class SessionSnapshotService {
    private final SessionSnapshotBuilder snapshotBuilder;
    private final SessionSnapshotApplier snapshotApplier;

    /**
     * Crea el servicio con sus colaboradores de construcción y aplicación.
     */
    SessionSnapshotService() {
        this.snapshotBuilder = new SessionSnapshotBuilder();
        this.snapshotApplier = new SessionSnapshotApplier();
    }

    /**
     * Construye un snapshot completo listo para serializarse y enviarse.
     *
     * @param session sesión fuente del estado
     * @param sequence secuencia monotónica del snapshot
     * @return payload serializable del estado actual
     */
    Map<String, Object> buildAuthoritativeSnapshot(SessionService session, long sequence) {
        return snapshotBuilder.buildSnapshot(session, sequence);
    }

    /**
     * Aplica un snapshot recibido si su secuencia aún no fue procesada.
     *
     * @param session sesión destino
     * @param data payload deserializado desde red
     * @return {@code true} si el snapshot fue aplicado
     */
    boolean applyAuthoritativeSnapshot(SessionService session, Map<String, Object> data) {
        if (data.containsKey("seq")) {
            long seq = ((Number) data.get("seq")).longValue();
            if (!session.matchState().acceptIncomingSequence(seq)) {
                return false;
            }
        }
        snapshotApplier.applySnapshot(session, data);
        return true;
    }
}
