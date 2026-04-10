package com.dino.application.runtime;

import com.dino.application.levels.LevelCatalog;
import com.dino.application.levels.ResourceLevelCatalog;
import com.dino.application.services.EventBus;
import com.dino.application.services.EventChannel;
import com.dino.application.services.GameplaySessionCoordinator;
import com.dino.application.services.HostMatchService;
import com.dino.application.services.LobbySessionCoordinator;
import com.dino.application.services.SessionLifecycleService;
import com.dino.application.services.SessionService;
import com.dino.application.usecases.CreateSessionUseCase;
import com.dino.application.usecases.JoinSessionUseCase;
import com.dino.infrastructure.audio.SoundManager;
import com.dino.infrastructure.network.NetworkPeer;
import com.dino.infrastructure.network.UdpPeer;
import com.dino.infrastructure.serialization.MessageType;
import com.dino.infrastructure.serialization.MessageSerializer;
import com.dino.infrastructure.serialization.ProtocolMessageValidator;
import com.dino.presentation.components.EventLogObserver;
import com.dino.presentation.components.ScoreBoardObserver;
import com.dino.presentation.flow.GameOverScreenFlow;
import com.dino.presentation.flow.GameOverSummaryFactory;
import com.dino.presentation.flow.GameScreenFlow;
import com.dino.presentation.flow.LobbyScreenFlow;
import com.dino.presentation.flow.StartMenuFlow;
import com.dino.presentation.navigation.SceneNavigator;
import com.dino.presentation.render.GameRenderStateFactory;
import com.dino.presentation.viewmodel.GameHudViewModelFactory;
import com.dino.presentation.viewmodel.LobbyPlayerListFormatter;
import javafx.stage.Stage;

import java.net.InetAddress;
import java.util.Map;

/**
 * Contexto compartido de runtime para una instancia de la aplicación.
 *
 * <p>Agrupa servicios y componentes de infraestructura que viven mientras la
 * aplicación está abierta: eventos, sesión, audio, networking, observadores y
 * navegación entre escenas. Su objetivo es reducir el uso de campos estáticos
 * dispersos y ofrecer un único punto de composición.</p>
 */
public final class AppContext {
    private final EventBus eventBus;
    private final SessionService sessionService;
    private final SoundManager soundManager;
    private final ScoreBoardObserver scoreBoardObserver;
    private final EventLogObserver eventLogObserver;
    private final MessageSerializer messageSerializer;
    private final ProtocolMessageValidator protocolValidator;
    private final SessionLifecycleService sessionLifecycleService;
    private final SceneNavigator sceneNavigator;
    private final Runnable resetToStartMenuAction;
    private final LevelCatalog levelCatalog;
    private final GameOverSummaryFactory gameOverSummaryFactory;

    private NetworkPeer networkPeer;
    private HostMatchService hostMatchService;

    private AppContext(Stage stage, Runnable resetToStartMenuAction) {
        this.eventBus = new EventBus();
        this.sessionService = new SessionService(eventBus);
        this.soundManager = new SoundManager(eventBus);
        this.scoreBoardObserver = new ScoreBoardObserver(eventBus);
        this.eventLogObserver = new EventLogObserver(eventBus, sessionService);
        this.messageSerializer = new MessageSerializer();
        this.protocolValidator = new ProtocolMessageValidator();
        this.sessionLifecycleService = new SessionLifecycleService(sessionService);
        this.levelCatalog = new ResourceLevelCatalog();
        this.gameOverSummaryFactory = new GameOverSummaryFactory();
        this.sceneNavigator = new SceneNavigator(stage, this);
        this.resetToStartMenuAction = resetToStartMenuAction;
    }

    /**
     * Construye un contexto completamente nuevo para una sesión fresca.
     *
     * @param stage escenario principal de JavaFX
     * @param resetToStartMenuAction callback que reconstruye el runtime y abre
     *                               de nuevo el menú principal
     * @return contexto listo para inyectarse en controladores
     */
    public static AppContext create(Stage stage, Runnable resetToStartMenuAction) {
        return new AppContext(stage, resetToStartMenuAction);
    }

    /**
     * Cierra el transporte activo si existe e intenta avisar al host cuando la
     * instancia local es un cliente.
     */
    public void shutdownNetworking() {
        sendDisconnectIfNeeded();
        if (networkPeer != null) {
            networkPeer.close();
        }
        networkPeer = null;
        hostMatchService = null;
    }

    /**
     * Libera recursos de larga vida del runtime actual.
     */
    public void close() {
        shutdownNetworking();
        soundManager.close();
    }

    /**
     * Crea un transporte UDP fresco y lo registra como peer activo del runtime.
     *
     * <p>Centraliza en el contexto la construcción del adaptador de red para
     * que los controladores no dependan directamente de {@link UdpPeer}.</p>
     */
    public NetworkPeer openNetworkPeer() {
        shutdownNetworking();
        networkPeer = new UdpPeer(messageSerializer);
        return networkPeer;
    }

    /**
     * Crea la simulación autoritativa del host usando las dependencias ya
     * compuestas por el runtime.
     */
    public HostMatchService createHostMatchService() {
        hostMatchService = new HostMatchService(sessionService, sessionLifecycleService, eventBus, levelCatalog);
        return hostMatchService;
    }

    /**
     * Construye un coordinador enfocado en el protocolo del lobby actual.
     *
     * @return coordinador listo para la escena de lobby
     */
    public LobbySessionCoordinator createLobbyCoordinator() {
        ensureNetworkPeer();
        return new LobbySessionCoordinator(sessionService, sessionLifecycleService, eventBus, networkPeer, messageSerializer, protocolValidator, this::createHostMatchService);
    }

    /**
     * Construye un coordinador enfocado en el protocolo de gameplay.
     *
     * @return coordinador listo para la escena principal del juego
     */
    public GameplaySessionCoordinator createGameplayCoordinator() {
        ensureNetworkPeer();
        return new GameplaySessionCoordinator(sessionService, sessionLifecycleService, networkPeer, messageSerializer, protocolValidator, hostMatchService);
    }

    /**
     * Construye el caso de uso para crear una sala como host.
     *
     * @return caso de uso configurado con el runtime actual
     */
    public CreateSessionUseCase createCreateSessionUseCase() {
        ensureNetworkPeer();
        return new CreateSessionUseCase(sessionService, sessionLifecycleService, networkPeer, eventBus);
    }

    /**
     * Construye el caso de uso para unirse a una sala existente.
     *
     * @return caso de uso configurado con el runtime actual
     */
    public JoinSessionUseCase createJoinSessionUseCase() {
        ensureNetworkPeer();
        return new JoinSessionUseCase(sessionService, sessionLifecycleService, networkPeer, messageSerializer, eventBus);
    }

    /**
     * Construye el flujo de presentación específico de la pantalla inicial.
     *
     * @return fachada de acciones del menú principal
     */
    public StartMenuFlow createStartMenuFlow() {
        return new StartMenuFlow(
            this::openNetworkPeer,
            this::createCreateSessionUseCase,
            this::createJoinSessionUseCase,
            this::shutdownNetworking,
            sceneNavigator
        );
    }

    /**
     * Construye el flujo de presentación específico del lobby actual.
     *
     * @return fachada del lobby en memoria
     */
    public LobbyScreenFlow createLobbyScreenFlow() {
        return new LobbyScreenFlow(
            sessionService,
            eventBus,
            this::createLobbyCoordinator,
            sceneNavigator,
            new LobbyPlayerListFormatter()
        );
    }

    /**
     * Construye el flujo de presentación específico de gameplay.
     *
     * @return fachada de la partida en curso
     */
    public GameScreenFlow createGameScreenFlow() {
        return new GameScreenFlow(
            sessionService,
            eventBus,
            scoreBoardObserver,
            eventLogObserver,
            this::createGameplayCoordinator,
            sceneNavigator,
            this::resetToStartMenu,
            new GameRenderStateFactory(),
            new GameHudViewModelFactory()
        );
    }

    /**
     * Construye el flujo de presentación específico de la pantalla final.
     *
     * @return fachada de resultados finales
     */
    public GameOverScreenFlow createGameOverScreenFlow() {
        return new GameOverScreenFlow(gameOverSummaryFactory.build(sessionService), this::resetToStartMenu);
    }

    /**
     * Reconstruye el runtime y regresa al menú principal.
     */
    public void resetToStartMenu() {
        if (resetToStartMenuAction != null) {
            resetToStartMenuAction.run();
        }
    }

    private void sendDisconnectIfNeeded() {
        if (networkPeer == null || !networkPeer.isBound()) return;
        if (sessionService.isHost()) return;
        String playerId = sessionService.getLocalPlayerId();
        String hostIp = sessionService.getHostIp();
        if (playerId == null || hostIp == null || hostIp.isBlank()) return;

        try {
            Map<String, Object> msg = messageSerializer.build(
                MessageType.DISCONNECT,
                "playerId", playerId
            );
            networkPeer.send(msg, InetAddress.getByName(hostIp), sessionService.getHostPort());
        } catch (Exception ignored) {
        }
    }

    private void ensureNetworkPeer() {
        if (networkPeer == null) {
            throw new IllegalStateException("No hay un transporte de red activo en el runtime actual");
        }
    }

    /**
     * Retorna el canal de eventos compartido del runtime.
     *
     * @return canal de publicación y suscripción usado por la aplicación
     */
    public EventChannel events() { return eventBus; }

    /**
     * Retorna el estado compartido de la sesión actual.
     *
     * @return sesión en memoria del lobby o de la partida
     */
    public SessionService session() { return sessionService; }

    /**
     * Retorna el observador que proyecta puntajes a la HUD.
     *
     * @return vista derivada del ranking de jugadores
     */
    public ScoreBoardObserver scoreBoard() { return scoreBoardObserver; }

    /**
     * Retorna el observador que proyecta eventos a la bitácora de UI.
     *
     * @return vista derivada de eventos significativos de gameplay
     */
    public EventLogObserver eventLog() { return eventLogObserver; }

    /**
     * Retorna el navegador encargado de cambiar escenas JavaFX.
     *
     * @return navegador del runtime actual
     */
    public SceneNavigator navigator() { return sceneNavigator; }

    /**
     * Retorna el transporte de red activo, si ya fue abierto.
     *
     * @return peer UDP actual o {@code null} si aún no existe
     */
    public NetworkPeer networkPeer() { return networkPeer; }
}
