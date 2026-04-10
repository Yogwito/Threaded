package com.dino.presentation.flow;

import com.dino.application.services.EventChannel;
import com.dino.application.services.EventSubscription;
import com.dino.application.services.GameplaySessionCoordinator;
import com.dino.application.services.GameplaySignal;
import com.dino.application.services.SessionService;
import com.dino.application.services.SubscriptionGroup;
import com.dino.domain.entities.Player;
import com.dino.domain.events.EventNames;
import com.dino.presentation.components.EventLogObserver;
import com.dino.presentation.components.ScoreBoardObserver;
import com.dino.presentation.navigation.SceneNavigation;
import com.dino.presentation.render.GameRenderState;
import com.dino.presentation.render.GameRenderStateFactory;
import com.dino.presentation.viewmodel.GameHudViewModel;
import com.dino.presentation.viewmodel.GameHudViewModelFactory;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Fachada de presentación para la escena principal de gameplay.
 *
 * <p>Reúne el coordinador de red/gameplay, la proyección de HUD y el armado de
 * snapshots de render. El controlador JavaFX se limita a temporización visual,
 * cámara y widgets.</p>
 */
public final class GameScreenFlow {
    private final SessionService sessionService;
    private final EventChannel eventChannel;
    private final ScoreBoardObserver scoreBoardObserver;
    private final EventLogObserver eventLogObserver;
    private final Supplier<GameplaySessionCoordinator> coordinatorFactory;
    private final SceneNavigation navigation;
    private final Runnable resetToStartMenu;
    private final GameRenderStateFactory renderStateFactory;
    private final GameHudViewModelFactory hudViewModelFactory;

    private GameplaySessionCoordinator coordinator;

    /**
     * Construye el flujo de gameplay con las dependencias necesarias para UI.
     *
     * @param sessionService sesión compartida actual
     * @param eventChannel canal de eventos interno
     * @param scoreBoardObserver ranking derivado para HUD
     * @param eventLogObserver bitácora derivada para HUD
     * @param coordinatorFactory fábrica del coordinador de gameplay
     * @param navigation navegador de escenas
     * @param resetToStartMenu callback para reconstruir el runtime desde menú
     * @param renderStateFactory fábrica de snapshots de render
     * @param hudViewModelFactory fábrica de modelos de HUD
     */
    public GameScreenFlow(SessionService sessionService,
                          EventChannel eventChannel,
                          ScoreBoardObserver scoreBoardObserver,
                          EventLogObserver eventLogObserver,
                          Supplier<GameplaySessionCoordinator> coordinatorFactory,
                          SceneNavigation navigation,
                          Runnable resetToStartMenu,
                          GameRenderStateFactory renderStateFactory,
                          GameHudViewModelFactory hudViewModelFactory) {
        this.sessionService = sessionService;
        this.eventChannel = eventChannel;
        this.scoreBoardObserver = scoreBoardObserver;
        this.eventLogObserver = eventLogObserver;
        this.coordinatorFactory = coordinatorFactory;
        this.navigation = navigation;
        this.resetToStartMenu = resetToStartMenu;
        this.renderStateFactory = renderStateFactory;
        this.hudViewModelFactory = hudViewModelFactory;
    }

    /**
     * Activa el flujo y crea el coordinador de gameplay asociado a la escena.
     */
    public void activate() {
        coordinator = coordinatorFactory.get();
    }

    /**
     * Registra callbacks de UI para los eventos relevantes de gameplay.
     *
     * @param refreshUi callback para refrescar HUD/listas
     * @param onGameOver callback para abrir la pantalla final
     * @param feedbackConsumer callback para feedback efímero del jugador local
     * @return handle para liberar todas las suscripciones de la escena
     */
    public EventSubscription bindSceneEvents(Runnable refreshUi,
                                             Runnable onGameOver,
                                             Consumer<GameplayFeedback> feedbackConsumer) {
        SubscriptionGroup group = new SubscriptionGroup();
        group.add(eventChannel.subscribe(EventNames.SNAPSHOT_RECEIVED, ignored -> refreshUi.run()));
        group.add(eventChannel.subscribe(EventNames.GAME_OVER, ignored -> {
            if (sessionService.isHost()) {
                requireCoordinator().broadcastGameOver();
            }
            onGameOver.run();
        }));
        group.add(eventChannel.subscribe(EventNames.COIN_COLLECTED, payload ->
            emitLocalPlayerFeedback(payload, "playerId", new GameplayFeedback("Moneda recogida", "#ffd166"), feedbackConsumer)));
        group.add(eventChannel.subscribe(EventNames.PLAYER_JUMPED, payload ->
            emitLocalPlayerFeedback(payload, "playerId", new GameplayFeedback("Salto", "#8be9fd"), feedbackConsumer)));
        group.add(eventChannel.subscribe(EventNames.ROOM_RESET, payload ->
            feedbackConsumer.accept(new GameplayFeedback(
                String.valueOf(payload.getOrDefault("reason", "Sala reiniciada")),
                "#ff9f68"
            ))));
        group.add(eventChannel.subscribe(EventNames.LEVEL_ADVANCED, ignored ->
            feedbackConsumer.accept(new GameplayFeedback("Nivel completado", "#80ed99"))));
        return group::clear;
    }

    /**
     * Ejecuta el polling de mensajes de gameplay.
     *
     * @return señal resultante del coordinador
     */
    public GameplaySignal pollIncomingMessages() {
        return requireCoordinator().pollIncomingMessages();
    }

    /**
     * Avanza un frame de gameplay o simulación local.
     *
     * @param dt delta temporal del frame en segundos
     */
    public void advanceFrame(double dt) {
        requireCoordinator().advanceFrame(dt);
    }

    /**
     * Envía el objetivo de puntería del jugador local.
     *
     * @param worldX coordenada X en mundo
     * @param worldY coordenada Y en mundo
     */
    public void sendAim(double worldX, double worldY) {
        requireCoordinator().sendAim(sessionService.getLocalPlayerId(), worldX, worldY);
    }

    /**
     * Envía una solicitud de salto del jugador local.
     */
    public void sendJump() {
        requireCoordinator().sendJump(sessionService.getLocalPlayerId());
    }

    /**
     * Construye el snapshot de render del frame actual.
     *
     * @param cameraX desplazamiento X de la cámara
     * @param cameraY desplazamiento Y de la cámara
     * @param viewportWidth ancho visible en mundo
     * @param viewportHeight alto visible en mundo
     * @param visualTimeSeconds tiempo visual continuo para animaciones
     * @return snapshot listo para el renderer
     */
    public GameRenderState buildRenderState(double cameraX,
                                            double cameraY,
                                            double viewportWidth,
                                            double viewportHeight,
                                            double visualTimeSeconds) {
        return renderStateFactory.build(
            sessionService,
            cameraX,
            cameraY,
            viewportWidth,
            viewportHeight,
            sessionService.getLocalPlayerId(),
            visualTimeSeconds
        );
    }

    /**
     * Construye el modelo textual de la HUD visible.
     *
     * @return modelo derivado del estado actual de la partida
     */
    public GameHudViewModel buildHudViewModel() {
        return hudViewModelFactory.build(sessionService, scoreBoardObserver, eventLogObserver);
    }

    /**
     * Retorna el snapshot del jugador local si existe.
     *
     * @return copia defensiva del jugador local o {@code null}
     */
    public Player localPlayerSnapshot() {
        return renderStateFactory.findLocalPlayer(sessionService.getPlayersSnapshot(), sessionService.getLocalPlayerId());
    }

    /**
     * Retorna el tiempo total visible de la partida en segundos.
     *
     * @return tiempo acumulado actual
     */
    public double elapsedTime() {
        return sessionService.getElapsedTime();
    }

    /**
     * Indica si la instancia local es el host autoritativo.
     *
     * @return {@code true} cuando la vista corre en el host
     */
    public boolean isHost() {
        return sessionService.isHost();
    }

    /**
     * Navega a la pantalla final de resultados.
     *
     * @throws Exception si falla la carga de la escena final
     */
    public void showGameOver() throws Exception {
        navigation.showGameOver();
    }

    /**
     * Cierra la sesión actual y reconstruye el runtime desde el menú.
     */
    public void resetToStartMenu() {
        resetToStartMenu.run();
    }

    private void emitLocalPlayerFeedback(Map<String, Object> payload,
                                         String key,
                                         GameplayFeedback feedback,
                                         Consumer<GameplayFeedback> feedbackConsumer) {
        String localPlayerId = sessionService.getLocalPlayerId();
        if (localPlayerId != null && localPlayerId.equals(payload.get(key))) {
            feedbackConsumer.accept(feedback);
        }
    }

    private GameplaySessionCoordinator requireCoordinator() {
        if (coordinator == null) {
            throw new IllegalStateException("El flujo de gameplay aún no fue activado");
        }
        return coordinator;
    }
}
