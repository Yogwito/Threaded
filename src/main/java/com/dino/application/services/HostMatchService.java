package com.dino.application.services;

import com.dino.application.levels.LevelCatalog;
import com.dino.domain.entities.Player;
import com.dino.domain.rules.GameRules;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordinador de la simulación autoritativa del host.
 *
 * <p>Recibe input remoto, avanza el tick del host y delega la mayor parte del
 * trabajo en servicios especializados: física base, restricción del hilo y
 * flujo de nivel/score. Los clientes no duplican esta lógica; solo consumen
 * snapshots.</p>
 *
 * <p>El servicio conserva únicamente la orquestación del orden del frame y la
 * coordinación entre colaboradores. No serializa red, no renderiza y tampoco
 * guarda por sí mismo el mundo; opera sobre los slices de estado expuestos por
 * la sesión compartida.</p>
 */
public class HostMatchService {
    private final SessionMatchState matchState;
    private final SessionWorldState worldState;
    private final SessionLifecycleService lifecycleService;
    private final LevelCatalog levelCatalog;
    private final PlayerPhysicsService playerPhysicsService;
    private final ThreadConstraintService threadConstraintService;
    private final LevelFlowService levelFlowService;

    private boolean gameOver = false;

    /**
     * Construye la simulación del host con el estado compartido y sus
     * servicios de soporte.
     *
     * @param sessionService sesión compartida desde la que se obtienen los
     *                       slices internos de mundo y progreso
     * @param lifecycleService servicio encargado de mover la sesión hacia
     *                         gameplay o pantalla final
     * @param eventPublisher publicador de eventos internos
     * @param levelCatalog fuente de niveles de campaña
     */
    public HostMatchService(SessionService sessionService,
                            SessionLifecycleService lifecycleService,
                            EventPublisher eventPublisher,
                            LevelCatalog levelCatalog) {
        this.matchState = sessionService.matchState();
        this.worldState = sessionService.worldState();
        this.lifecycleService = lifecycleService;
        this.levelCatalog = levelCatalog;
        this.threadConstraintService = new ThreadConstraintService(worldState, eventPublisher);
        this.playerPhysicsService = new PlayerPhysicsService(worldState, eventPublisher, threadConstraintService);
        this.levelFlowService = new LevelFlowService(worldState, matchState, eventPublisher, levelCatalog);
    }

    /**
     * Inicializa la campaña desde el primer nivel y reinicia contadores.
     */
    public void initWorld() {
        matchState.beginCampaign(levelCatalog.countAvailableLevels());
        gameOver = false;
        playerPhysicsService.resetState();
        threadConstraintService.resetState();
        levelFlowService.resetState();
        levelFlowService.loadLevel(0, true);
        playerPhysicsService.syncInputsToPlayers(new ArrayList<>(worldState.players().values()));
    }

    /**
     * Registra el último objetivo de movimiento apuntado por un jugador.
     *
     * @param playerId jugador al que pertenece el input
     * @param targetX objetivo X en mundo
     * @param targetY objetivo Y en mundo
     */
    public void handleMoveTarget(String playerId, double targetX, double targetY) {
        playerPhysicsService.handleMoveTarget(playerId, targetX, targetY);
    }

    /**
     * Encola un salto discreto para el siguiente tick del host.
     *
     * @param playerId jugador que solicitó el salto
     */
    public void handleJump(String playerId) {
        playerPhysicsService.handleJump(playerId);
    }

    /**
     * Avanza una iteración completa de simulación del host.
     *
     * <p>El orden del frame se mantiene: física base, hilo, segunda resolución,
     * interacciones del nivel, fallos de sala y posible avance de campaña.</p>
     *
     * @param dt delta temporal del frame en segundos
     */
    public void tick(double dt) {
        if (gameOver || !matchState.isGameRunning()) return;
        matchState.advanceElapsedTime(dt);
        playerPhysicsService.tickCooldowns(dt);
        threadConstraintService.tickCooldowns(dt);

        List<Player> players = new ArrayList<>(worldState.players().values());

        playerPhysicsService.updatePlayers(players, dt);
        threadConstraintService.applyThreadElasticity(players, dt, playerPhysicsService::stabilizePlayer);
        playerPhysicsService.resolvePlayerInteractions(players);
        playerPhysicsService.updatePushBlocks(players, dt);

        levelFlowService.updateButtonAndDoor(players);
        levelFlowService.updateExitState(players);
        levelFlowService.updateCoins(players);

        if (levelFlowService.resolveFailures(players)) {
            playerPhysicsService.syncInputsToPlayers(new ArrayList<>(worldState.players().values()));
            return;
        }

        if (GameRules.allConnectedPlayersAtExit(players)) {
            gameOver = levelFlowService.advanceLevelOrFinish();
            if (gameOver) {
                lifecycleService.enterGameOver();
            } else {
                playerPhysicsService.syncInputsToPlayers(new ArrayList<>(worldState.players().values()));
            }
        }
    }
}
