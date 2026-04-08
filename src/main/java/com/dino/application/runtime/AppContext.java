package com.dino.application.runtime;

import com.dino.application.services.EventBus;
import com.dino.application.services.EventChannel;
import com.dino.application.services.HostMatchService;
import com.dino.application.services.SessionService;
import com.dino.infrastructure.audio.SoundManager;
import com.dino.infrastructure.network.NetworkPeer;
import com.dino.infrastructure.network.UdpPeer;
import com.dino.infrastructure.serialization.MessageSerializer;
import com.dino.presentation.components.EventLogObserver;
import com.dino.presentation.components.ScoreBoardObserver;
import com.dino.presentation.navigation.SceneNavigator;
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
    private final SceneNavigator sceneNavigator;
    private final Runnable resetToStartMenuAction;

    private NetworkPeer networkPeer;
    private HostMatchService hostMatchService;

    private AppContext(Stage stage, Runnable resetToStartMenuAction) {
        this.eventBus = new EventBus();
        this.sessionService = new SessionService(eventBus);
        this.soundManager = new SoundManager(eventBus);
        this.scoreBoardObserver = new ScoreBoardObserver(eventBus);
        this.eventLogObserver = new EventLogObserver(eventBus);
        this.messageSerializer = new MessageSerializer();
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
        networkPeer = new UdpPeer();
        return networkPeer;
    }

    /**
     * Crea la simulación autoritativa del host usando las dependencias ya
     * compuestas por el runtime.
     */
    public HostMatchService createHostMatchService() {
        hostMatchService = new HostMatchService(sessionService, eventBus);
        return hostMatchService;
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
                MessageSerializer.DISCONNECT,
                "playerId", playerId
            );
            networkPeer.send(msg, InetAddress.getByName(hostIp), sessionService.getHostPort());
        } catch (Exception ignored) {
        }
    }

    public EventChannel events() { return eventBus; }
    public SessionService session() { return sessionService; }
    public SoundManager sound() { return soundManager; }
    public ScoreBoardObserver scoreBoard() { return scoreBoardObserver; }
    public EventLogObserver eventLog() { return eventLogObserver; }
    public MessageSerializer serializer() { return messageSerializer; }
    public SceneNavigator navigator() { return sceneNavigator; }
    public NetworkPeer networkPeer() { return networkPeer; }
    public HostMatchService hostMatchService() { return hostMatchService; }
}
