package com.dino.infrastructure.serialization;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tipos de mensaje del protocolo UDP del proyecto.
 *
 * <p>Formaliza el contrato wire sin alterar el payload observable: el valor
 * serializado sigue siendo la cadena clásica usada por host y clientes, pero
 * el código deja de depender de literales dispersos.</p>
 */
public enum MessageType {
    JOIN("JOIN"),
    WELCOME("WELCOME"),
    READY("READY"),
    LOBBY_SNAPSHOT("LOBBY_SNAPSHOT"),
    START_GAME("START_GAME"),
    MOVE_TARGET("MOVE_TARGET"),
    JUMP("JUMP"),
    SNAPSHOT("SNAPSHOT"),
    GAME_EVENT("GAME_EVENT"),
    DISCONNECT("DISCONNECT"),
    GAME_OVER("GAME_OVER");

    private static final Map<String, MessageType> BY_WIRE_VALUE = buildIndex();

    private final String wireValue;

    MessageType(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Retorna el valor textual que viaja por UDP.
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Resuelve un tipo de mensaje desde su valor textual de red.
     *
     * @param rawType valor de la clave {@code type}
     * @return tipo reconocido, si existe
     */
    public static Optional<MessageType> fromWireValue(String rawType) {
        if (rawType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_WIRE_VALUE.get(rawType));
    }

    private static Map<String, MessageType> buildIndex() {
        Map<String, MessageType> index = new HashMap<>();
        for (MessageType type : values()) {
            index.put(type.wireValue, type);
        }
        return index;
    }
}
