package com.dino.application.usecases;

import com.dino.application.services.EventPublisher;
import com.dino.application.services.SessionLifecycleService;
import com.dino.application.services.SessionService;
import com.dino.domain.entities.Player;
import com.dino.domain.events.EventNames;
import com.dino.infrastructure.network.NetworkPeer;
import com.dino.infrastructure.serialization.MessageType;
import com.dino.infrastructure.serialization.MessageSerializer;

import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso encargado de unirse a una sala ya creada.
 *
 * <p>Configura la instancia local como cliente, enlaza su socket UDP y envía el
 * primer mensaje {@code JOIN} al host para anunciar su presencia.</p>
 */
public class JoinSessionUseCase {
    private final SessionService sessionService;
    private final SessionLifecycleService lifecycleService;
    private final NetworkPeer udpPeer;
    private final MessageSerializer serializer;
    private final EventPublisher eventBus;

    /**
     * Construye el caso de uso con acceso al estado, red y serialización.
     *
     * @param sessionService estado compartido de la sesión
     * @param udpPeer socket UDP del cliente
     * @param serializer serializador de mensajes de red
     * @param eventBus bus de eventos interno
     */
    public JoinSessionUseCase(SessionService sessionService,
                               SessionLifecycleService lifecycleService,
                               NetworkPeer udpPeer,
                               MessageSerializer serializer, EventPublisher eventBus) {
        this.sessionService = sessionService;
        this.lifecycleService = lifecycleService;
        this.udpPeer = udpPeer;
        this.serializer = serializer;
        this.eventBus = eventBus;
    }

    /**
     * Inicializa la instancia local como cliente y envía el mensaje de unión.
     *
     * @param playerName nombre visible del jugador
     * @param localIp IP local a enlazar
     * @param localPort puerto local UDP
     * @param hostIp IP del host
     * @param hostPort puerto UDP del host
     * @throws Exception si falla el bind local o el primer envío de unión
     */
    public void execute(String playerName, String localIp, int localPort,
                        String hostIp, int hostPort) throws Exception {
        String playerId = UUID.randomUUID().toString();
        lifecycleService.configureAsClient(playerId, playerName, localIp, localPort, hostIp, hostPort);

        Player self = new Player(playerId, playerName, "blue");
        sessionService.addPlayer(self);

        udpPeer.bind(localIp, localPort);
        Map<String, Object> joinMsg = serializer.build(MessageType.JOIN, "playerId", playerId, "name", playerName);
        udpPeer.send(joinMsg, InetAddress.getByName(hostIp), hostPort);

        eventBus.publish(EventNames.PLAYER_JOINED, Map.of("playerId", playerId, "name", playerName));
    }
}
