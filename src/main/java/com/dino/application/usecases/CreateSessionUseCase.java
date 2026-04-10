package com.dino.application.usecases;

import com.dino.application.services.EventPublisher;
import com.dino.application.services.SessionLifecycleService;
import com.dino.application.services.SessionService;
import com.dino.domain.entities.Player;
import com.dino.domain.events.EventNames;
import com.dino.infrastructure.network.NetworkPeer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;

/**
 * Caso de uso encargado de crear una sala nueva.
 *
 * <p>Configura la instancia local como host, registra al jugador creador en el
 * estado compartido y deja el socket UDP listo para recibir conexiones.</p>
 */
public class CreateSessionUseCase {
    private final SessionService sessionService;
    private final SessionLifecycleService lifecycleService;
    private final NetworkPeer udpPeer;
    private final EventPublisher eventBus;

    /**
     * Construye el caso de uso con las dependencias mínimas necesarias.
     *
     * @param sessionService estado de sesión que quedará inicializado como host
     * @param udpPeer socket UDP local
     * @param eventBus bus de eventos para notificar la creación del jugador local
     */
    public CreateSessionUseCase(SessionService sessionService,
                                SessionLifecycleService lifecycleService,
                                NetworkPeer udpPeer,
                                EventPublisher eventBus) {
        this.sessionService = sessionService;
        this.lifecycleService = lifecycleService;
        this.udpPeer = udpPeer;
        this.eventBus = eventBus;
    }

    /**
     * Crea una sala nueva y deja enlazada la instancia anfitriona.
     *
     * @param playerName nombre del jugador que actuará como host
     * @param localIp IP local a enlazar
     * @param localPort puerto local UDP
     * @param expectedPlayers cantidad esperada de jugadores en la sala
     * @throws Exception si falla el bind del socket o la inicialización base
     */
    public void execute(String playerName, String localIp, int localPort, int expectedPlayers) throws Exception {
        String playerId = UUID.randomUUID().toString();
        lifecycleService.configureAsHost(playerId, playerName, localIp, localPort, expectedPlayers);

        Player host = new Player(playerId, playerName, "pride");
        host.setX(200); host.setY(200);
        sessionService.addPlayer(host);
        sessionService.registerPeerAddress(playerId, new InetSocketAddress(localIp, localPort));

        udpPeer.bind(localIp, localPort);
        eventBus.publish(EventNames.PLAYER_JOINED, Map.of("playerId", playerId, "name", playerName));
    }
}
