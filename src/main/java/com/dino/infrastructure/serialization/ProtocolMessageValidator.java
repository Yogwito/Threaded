package com.dino.infrastructure.serialization;

import java.util.Map;
import java.util.Optional;

/**
 * Validador estructural de mensajes del protocolo UDP.
 *
 * <p>No intenta imponer semántica de juego, solo confirmar que un mapa
 * recibido representa un tipo conocido y contiene los campos mínimos exigidos
 * por ese tipo antes de que coordinadores o simulación lo consuman.</p>
 *
 * <p>Su papel es deliberadamente limitado: evita que un payload mal formado
 * avance hacia la lógica de lobby o gameplay, pero no reemplaza la validación
 * contextual que cada coordinador aplica según rol local y fase activa.</p>
 */
public final class ProtocolMessageValidator {
    /**
     * Resuelve el tipo lógico del mensaje si la clave {@code type} es válida.
     *
     * @param message mapa recibido por red
     * @return tipo reconocido, si existe
     */
    public Optional<MessageType> resolveType(Map<String, Object> message) {
        Object rawType = message.get("type");
        if (!(rawType instanceof String typeValue)) {
            return Optional.empty();
        }
        return MessageType.fromWireValue(typeValue);
    }

    /**
     * Indica si el tipo puede aceptarse durante el lobby según el rol local.
     */
    public boolean isAcceptedInLobby(MessageType type, boolean hostRole) {
        return switch (type) {
            case JOIN, READY, DISCONNECT -> hostRole;
            case LOBBY_SNAPSHOT, START_GAME -> !hostRole;
            default -> false;
        };
    }

    /**
     * Indica si el tipo puede aceptarse durante gameplay según el rol local.
     */
    public boolean isAcceptedInGameplay(MessageType type, boolean hostRole) {
        return switch (type) {
            case MOVE_TARGET, JUMP, DISCONNECT -> hostRole;
            case SNAPSHOT, GAME_OVER -> !hostRole;
            default -> false;
        };
    }

    /**
     * Verifica que el mensaje contenga los campos mínimos exigidos por su tipo.
     *
     * <p>La validación es estructural y conservadora. Por ejemplo, exige
     * {@code seq} y {@code players} en snapshots y mensajes de transición,
     * pero no inspecciona todavía la forma interna de cada jugador del payload.</p>
     *
     * @param type tipo ya resuelto del mensaje
     * @param message payload recibido
     * @return {@code true} si la estructura mínima es válida
     */
    public boolean hasRequiredFields(MessageType type, Map<String, Object> message) {
        return switch (type) {
            case JOIN -> hasNonBlankString(message, "playerId") && hasNonBlankString(message, "name");
            case READY, JUMP, DISCONNECT -> hasNonBlankString(message, "playerId");
            case MOVE_TARGET -> hasNonBlankString(message, "playerId")
                && hasNumber(message, "targetX")
                && hasNumber(message, "targetY");
            case LOBBY_SNAPSHOT, START_GAME, SNAPSHOT, GAME_OVER -> hasNumber(message, "seq") && hasList(message, "players");
            case WELCOME, GAME_EVENT -> true;
        };
    }

    private boolean hasNonBlankString(Map<String, Object> message, String key) {
        Object value = message.get(key);
        return value instanceof String text && !text.isBlank();
    }

    private boolean hasNumber(Map<String, Object> message, String key) {
        return message.get(key) instanceof Number;
    }

    private boolean hasList(Map<String, Object> message, String key) {
        return message.get(key) instanceof Iterable<?>;
    }
}
