package com.dino.infrastructure.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializador y fábrica básica de mensajes de red.
 *
 * <p>Convierte mapas a JSON y viceversa usando Jackson. También concentra los
 * tipos de mensaje del protocolo UDP realmente usados por host y clientes para
 * que ambos hablen el mismo lenguaje.</p>
 *
 * <p>La estructura del wire format sigue siendo deliberadamente dinámica
 * ({@code Map<String, Object>}) para no rehacer el protocolo actual. El
 * contrato tipado vive en {@link MessageType} y en
 * {@link ProtocolMessageValidator}, mientras esta clase se limita a JSON y a
 * helpers de armado.</p>
 */
public class MessageSerializer implements MessageCodec {
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Serializa un mensaje listo para enviarse por UDP.
     *
     * @param msg mapa con el contenido del datagrama
     * @return arreglo de bytes JSON; vacío si ocurre un error de serialización
     */
    @Override
    public byte[] serialize(Map<String, Object> msg) {
        try {
            return mapper.writeValueAsBytes(msg);
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /**
     * Reconstruye un mapa a partir de bytes JSON recibidos por red.
     *
     * @param data bytes crudos del datagrama
     * @return mapa deserializado o un mapa vacío si el contenido era inválido
     */
    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> deserialize(byte[] data) {
        try {
            return mapper.readValue(data, Map.class);
        } catch (IOException e) {
            return Map.of();
        }
    }

    /**
     * Construye un mensaje agregando automáticamente la clave {@code type}.
     *
     * @param type tipo lógico del mensaje
     * @param keyValuePairs lista alternada clave/valor
     * @return mapa listo para serializar o enviar
     */
    public Map<String, Object> build(String type, Object... keyValuePairs) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", type);
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            msg.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return msg;
    }

    /**
     * Construye un mensaje a partir de un tipo formal del protocolo.
     *
     * @param type tipo lógico del mensaje
     * @param keyValuePairs lista alternada clave/valor
     * @return mapa listo para serializar o enviar
     */
    public Map<String, Object> build(MessageType type, Object... keyValuePairs) {
        return build(type.wireValue(), keyValuePairs);
    }
}
