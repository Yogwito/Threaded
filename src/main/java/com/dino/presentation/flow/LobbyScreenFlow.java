package com.dino.presentation.flow;

import com.dino.application.services.EventChannel;
import com.dino.application.services.EventSubscription;
import com.dino.application.services.LobbySessionCoordinator;
import com.dino.application.services.LobbySignal;
import com.dino.application.services.SessionService;
import com.dino.application.services.SubscriptionGroup;
import com.dino.domain.events.EventNames;
import com.dino.presentation.navigation.SceneNavigation;
import com.dino.presentation.viewmodel.LobbyPlayerListFormatter;
import com.dino.domain.entities.Player;

import java.util.List;
import java.util.function.Supplier;

/**
 * Fachada de presentación para la escena de lobby.
 *
 * <p>Coordina navegación, protocolo de lobby y proyección de jugadores hacia la
 * lista visible. El controlador conserva únicamente el timer de escena y la
 * actualización de widgets JavaFX.</p>
 */
public final class LobbyScreenFlow {
    private final SessionService sessionService;
    private final EventChannel eventChannel;
    private final Supplier<LobbySessionCoordinator> coordinatorFactory;
    private final SceneNavigation navigation;
    private final LobbyPlayerListFormatter playerListFormatter;

    private LobbySessionCoordinator coordinator;

    /**
     * Construye el flujo del lobby activo.
     *
     * @param sessionService sesión compartida actual
     * @param eventChannel canal de eventos interno
     * @param coordinatorFactory fábrica del coordinador UDP del lobby
     * @param navigation navegador de pantallas
     * @param playerListFormatter formateador de la lista visible de jugadores
     */
    public LobbyScreenFlow(SessionService sessionService,
                           EventChannel eventChannel,
                           Supplier<LobbySessionCoordinator> coordinatorFactory,
                           SceneNavigation navigation,
                           LobbyPlayerListFormatter playerListFormatter) {
        this.sessionService = sessionService;
        this.eventChannel = eventChannel;
        this.coordinatorFactory = coordinatorFactory;
        this.navigation = navigation;
        this.playerListFormatter = playerListFormatter;
    }

    /**
     * Activa el flujo del lobby para la escena mostrada.
     */
    public void activate() {
        coordinator = coordinatorFactory.get();
    }

    /**
     * Indica si el peer local actúa como host del lobby.
     *
     * @return {@code true} cuando la instancia local es host
     */
    public boolean isHost() {
        return sessionService.isHost();
    }

    /**
     * Retorna la lista visible de jugadores del lobby.
     *
     * @return nombres y estado listos para {@code ListView}
     */
    public List<String> playerEntries() {
        return playerListFormatter.format(sessionService.getPlayersSnapshot());
    }

    /**
     * Retorna el snapshot actual de jugadores del lobby.
     *
     * @return jugadores visibles para componentes gráficos del lobby
     */
    public List<Player> playerSnapshots() {
        return sessionService.getPlayersSnapshot();
    }

    /**
     * Retorna cuántos jugadores espera la sala creada por el host.
     *
     * @return cupo objetivo del lobby
     */
    public int expectedPlayers() {
        return Math.max(2, sessionService.getExpectedPlayers());
    }

    /**
     * Cuenta cuántos jugadores siguen conectados en el lobby actual.
     *
     * @return número de jugadores conectados
     */
    public int connectedPlayersCount() {
        int connected = 0;
        for (Player player : sessionService.getPlayersSnapshot()) {
            if (player.isConnected()) {
                connected++;
            }
        }
        return connected;
    }

    /**
     * Cuenta cuántos jugadores están marcados como listos.
     *
     * @return cantidad de jugadores listos
     */
    public int readyPlayersCount() {
        int ready = 0;
        for (Player player : sessionService.getPlayersSnapshot()) {
            if (player.isReady()) {
                ready++;
            }
        }
        return ready;
    }

    /**
     * Suscribe una acción de refresco a los cambios relevantes del lobby.
     *
     * @param refreshAction acción a ejecutar ante cambios visibles
     * @return handle para liberar todas las suscripciones juntas
     */
    public EventSubscription bindLobbyUpdates(Runnable refreshAction) {
        SubscriptionGroup group = new SubscriptionGroup();
        group.add(eventChannel.subscribe(EventNames.PLAYER_JOINED, ignored -> refreshAction.run()));
        group.add(eventChannel.subscribe(EventNames.PLAYER_READY, ignored -> refreshAction.run()));
        group.add(eventChannel.subscribe(EventNames.SNAPSHOT_RECEIVED, ignored -> refreshAction.run()));
        return group::clear;
    }

    /**
     * Marca al jugador local como listo y propaga el cambio por UDP.
     *
     * @throws Exception si falla el envío del mensaje
     */
    public void markLocalReady() throws Exception {
        requireCoordinator().markLocalReady();
    }

    /**
     * Inicia la partida desde el host y navega a gameplay.
     *
     * @throws Exception si falla el broadcast inicial o la transición de escena
     */
    public void startGame() throws Exception {
        requireCoordinator().startGame();
        navigation.showGame();
    }

    /**
     * Ejecuta un tick de polling de red del lobby.
     *
     * @return señal derivada del protocolo del lobby
     */
    public LobbySignal pollNetworkTick() {
        return requireCoordinator().pollNetworkTick();
    }

    /**
     * Resuelve las transiciones de pantalla derivadas del protocolo del lobby.
     *
     * @param signal señal emitida por el coordinador
     * @throws Exception si falla la transición a gameplay
     */
    public void handleSignal(LobbySignal signal) throws Exception {
        if (signal == LobbySignal.START_GAME) {
            navigation.showGame();
        }
    }

    /**
     * Retorna el mensaje de estado asociado a dejar listo al jugador local.
     *
     * @return texto breve para la etiqueta de estado
     */
    public String readyStatusMessage() {
        return sessionService.isHost()
            ? "Host listo. Esperando más jugadores..."
            : "Listo! Esperando al host...";
    }

    private LobbySessionCoordinator requireCoordinator() {
        if (coordinator == null) {
            throw new IllegalStateException("El flujo del lobby aún no fue activado");
        }
        return coordinator;
    }
}
