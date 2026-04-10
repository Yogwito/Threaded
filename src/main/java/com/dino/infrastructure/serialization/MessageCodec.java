package com.dino.infrastructure.serialization;

import java.util.Map;

/**
 * Contrato mínimo para serializar y deserializar mensajes de red.
 *
 * <p>Separa el transporte UDP del formato concreto de codificación para que
 * {@code UdpPeer} no dependa directamente de Jackson ni de un esquema rígido.
 * El proyecto actual usa mapas JSON, pero esta interfaz permite sustituir la
 * estrategia sin tocar la capa de socket.</p>
 */
public interface MessageCodec {
    /**
     * Serializa un mensaje listo para enviarse por red.
     *
     * @param message mapa lógico del mensaje
     * @return representación binaria lista para el socket
     */
    byte[] serialize(Map<String, Object> message);

    /**
     * Reconstruye un mensaje lógico a partir de bytes recibidos.
     *
     * @param data payload crudo recibido por el socket
     * @return mapa ya decodificado
     */
    Map<String, Object> deserialize(byte[] data);
}
