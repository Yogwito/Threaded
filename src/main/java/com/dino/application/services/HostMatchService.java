package com.dino.application.services;

import com.dino.application.levels.LevelLoader;
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
 */
public class HostMatchService {
    private final SessionService sessionService;
    private final PlayerPhysicsService playerPhysicsService;
    private final ThreadConstraintService threadConstraintService;
    private final LevelFlowService levelFlowService;

    private boolean gameOver = false;

    /**
     * Construye la simulación del host con el estado compartido y el publicador
     * de eventos interno.
     */
    public HostMatchService(SessionService sessionService, EventPublisher eventPublisher) {
        this.sessionService = sessionService;
        this.threadConstraintService = new ThreadConstraintService(sessionService, eventPublisher);
        this.playerPhysicsService = new PlayerPhysicsService(sessionService, eventPublisher, threadConstraintService);
        this.levelFlowService = new LevelFlowService(sessionService, eventPublisher);
    }

    /**
     * Inicializa la campaña desde el primer nivel y reinicia contadores.
     */
    public void initWorld() {
        sessionService.setCurrentLevelIndex(0);
        sessionService.setTotalLevels(Math.max(1, LevelLoader.countAvailableLevels()));
        sessionService.setElapsedTime(0);
        sessionService.setGameRunning(true);
        sessionService.setRoomResetCount(0);
        sessionService.setRoomResetReason("");
        gameOver = false;
        playerPhysicsService.resetState();
        threadConstraintService.resetState();
        levelFlowService.resetState();
        levelFlowService.loadLevel(0, true);
    }

    /**
     * Registra el último objetivo de movimiento apuntado por un jugador.
     */
    public void handleMoveTarget(String playerId, double targetX, double targetY) {
        playerPhysicsService.handleMoveTarget(playerId, targetX, targetY);
    }

    /**
     * Encola un salto discreto para el siguiente tick del host.
     */
    public void handleJump(String playerId) {
        playerPhysicsService.handleJump(playerId);
    }

    /**
     * Avanza una iteración completa de simulación del host.
     *
     * <p>El orden del frame se mantiene: física base, hilo, segunda resolución,
     * interacciones del nivel, fallos de sala y posible avance de campaña.</p>
     */
    public void tick(double dt) {
        if (gameOver || !sessionService.isGameRunning()) return;
        sessionService.setElapsedTime(sessionService.getElapsedTime() + dt);
        playerPhysicsService.tickCooldowns(dt);
        threadConstraintService.tickCooldowns(dt);

        List<Player> players = new ArrayList<>(sessionService.getPlayers().values());

        playerPhysicsService.updatePlayers(players, dt);
        threadConstraintService.applyThreadElasticity(players, dt, playerPhysicsService::stabilizePlayer);
        playerPhysicsService.resolvePlayerInteractions(players);
        playerPhysicsService.updatePushBlocks(players, dt);

        levelFlowService.updateButtonAndDoor(players);
        levelFlowService.updateExitState(players);
        levelFlowService.updateCoins(players);

        if (levelFlowService.resolveFailures(players)) {
            return;
        }

        if (GameRules.allConnectedPlayersAtExit(players)) {
            gameOver = levelFlowService.advanceLevelOrFinish();
        }
    }
}
