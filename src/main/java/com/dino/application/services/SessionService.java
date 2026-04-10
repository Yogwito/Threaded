package com.dino.application.services;

import com.dino.domain.entities.ButtonSwitch;
import com.dino.domain.entities.CollectibleItem;
import com.dino.domain.entities.Door;
import com.dino.domain.entities.ExitZone;
import com.dino.domain.entities.PlatformTile;
import com.dino.domain.entities.Player;
import com.dino.domain.entities.PushBlock;
import com.dino.domain.events.EventNames;

import java.net.InetSocketAddress;
import java.util.*;

/**
 * Estado compartido de la sesión actual.
 *
 * <p>Actúa como repositorio en memoria para lobby y partida. Guarda identidad
 * local, lista de jugadores, geometría del nivel, objetos interactivos, puntaje
 * y metadatos de red. El host produce snapshots desde este estado; el cliente lo
 * reconstruye al recibir snapshots UDP.</p>
 *
 * <p>También conserva secuencia de snapshots y trazas mínimas de peers remotos,
 * pero no implementa por sí mismo una capa confiable completa sobre UDP.</p>
 */
public class SessionService {
    private final EventPublisher eventPublisher;
    private final SessionSnapshotService snapshotService;
    private final SessionPeerRegistry peerRegistry;
    private final SessionViewFactory viewFactory;
    private final SessionConnectionState connectionState;
    private final SessionMatchState matchState;
    private final SessionWorldState worldState;
    private final SessionStateMachine stateMachine;

    /**
     * Crea un contenedor de sesión asociado al bus de eventos global.
     *
     * @param eventPublisher publicador usado para notificar recepción de snapshots
     */
    public SessionService(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.snapshotService = new SessionSnapshotService();
        this.peerRegistry = new SessionPeerRegistry();
        this.viewFactory = new SessionViewFactory();
        this.connectionState = new SessionConnectionState();
        this.matchState = new SessionMatchState();
        this.worldState = new SessionWorldState();
        this.stateMachine = new SessionStateMachine();
    }

    /**
     * Registra o reemplaza un jugador en el estado actual.
     *
     * @param player jugador a almacenar
     */
    public synchronized void addPlayer(Player player) { worldState.players().put(player.getId(), player); }

    /**
     * Elimina un jugador y cualquier rastro de su red asociada.
     *
     * @param playerId identificador único del jugador
     */
    public synchronized void removePlayer(String playerId) {
        worldState.players().remove(playerId);
        peerRegistry.remove(playerId);
    }

    /**
     * Aplica un snapshot autoritativo recibido por red.
     *
     * <p>Si el snapshot trae una secuencia menor o repetida, se ignora para
     * evitar retrocesos visuales por paquetes UDP fuera de orden.</p>
     *
     * @param data snapshot ya deserializado
     */
    @SuppressWarnings("unchecked")
    synchronized void applyAuthoritativeSnapshot(Map<String, Object> data) {
        if (!snapshotService.applyAuthoritativeSnapshot(this, data)) {
            return;
        }

        // La UI y los observadores reaccionan a este evento; por eso el
        // SessionService no invoca controladores de forma directa.
        eventPublisher.publish(EventNames.SNAPSHOT_RECEIVED, data);
    }

    /**
     * Construye un snapshot completo del estado actual.
     *
     * <p>Lo usa el host para difundir el juego en vivo y también para enviar el
     * snapshot inicial cuando arranca una partida o se sincroniza el lobby.</p>
     *
     * @return mapa serializable listo para enviarse por UDP
     */
    synchronized Map<String, Object> buildAuthoritativeSnapshot() {
        return snapshotService.buildAuthoritativeSnapshot(this, matchState.nextOutgoingSequence());
    }

    /**
     * Retorna la lista mutable de monedas del host.
     */
    List<CollectibleItem> getCoins() { return worldState.coins(); }

    /**
     * Retorna una copia defensiva de las monedas para la UI.
     */
    public synchronized List<CollectibleItem> getCoinsSnapshot() {
        return viewFactory.copyCollectibles(worldState.coins());
    }

    /**
     * Limpia por completo el estado de la sesión actual.
     */
    synchronized void clearSession() {
        worldState.clearAll();
        matchState.clear();
        connectionState.clear();
        peerRegistry.clear();
        stateMachine.reset();
    }

    /**
     * Asocia un jugador remoto a una dirección UDP y actualiza su último pulso.
     */
    public synchronized void registerPeerAddress(String playerId, InetSocketAddress address) {
        peerRegistry.register(playerId, address);
    }

    /**
     * Lista únicamente peers remotos, excluyendo a la instancia local.
     */
    public synchronized List<InetSocketAddress> getRemotePeerAddresses() {
        return peerRegistry.getRemoteAddresses(connectionState.getLocalPlayerId());
    }

    /**
     * Cambia el estado de listo de un jugador.
     */
    synchronized void markPlayerReady(String playerId, boolean ready) {
        Player player = worldState.players().get(playerId);
        if (player != null) player.setReady(ready);
    }

    /**
     * Cambia el estado de conexión de un jugador y sincroniza su timestamp.
     */
    synchronized void markPlayerConnected(String playerId, boolean connected) {
        Player player = worldState.players().get(playerId);
        if (player != null) player.setConnected(connected);
        if (connected) {
            peerRegistry.markSeen(playerId);
        } else {
            peerRegistry.remove(playerId);
        }
    }

    /**
     * Elimina la dirección de red asociada a un peer.
     */
    synchronized void removePeerAddress(String playerId) {
        peerRegistry.remove(playerId);
    }

    /**
     * Registra que se recibió actividad reciente de un peer.
     */
    synchronized void markPeerSeen(String playerId) {
        peerRegistry.markSeen(playerId);
    }

    /**
     * Calcula la edad del último paquete observado de un peer.
     *
     * @return milisegundos desde el último mensaje o {@code null} si no hay dato
     */
    synchronized Long getPeerAgeMillis(String playerId) {
        return peerRegistry.getAgeMillis(playerId);
    }

    /**
     * Marca como inactivos los peers que superan el timeout indicado.
     *
     * <p>Actualmente se conserva como utilidad de soporte para futuras mejoras
     * de heartbeat/timeout. El flujo principal del proyecto no depende de este
     * mecanismo para sincronizar la partida.</p>
     *
     * @param timeoutMillis tiempo máximo sin actividad
     * @return lista de identificadores expirados
     */
    synchronized List<String> expireInactivePeers(long timeoutMillis) {
        return peerRegistry.expireInactivePlayers(
            worldState.players(),
            connectionState.getLocalPlayerId(),
            connectionState.isHost(),
            timeoutMillis
        );
    }

    /**
     * Retorna una copia defensiva de los jugadores.
     */
    public synchronized List<Player> getPlayersSnapshot() {
        return viewFactory.copyPlayers(worldState.players().values());
    }

    /**
     * Retorna una copia defensiva de las plataformas del nivel.
     */
    public synchronized List<PlatformTile> getPlatformsSnapshot() {
        return viewFactory.copyPlatformTiles(worldState.platforms());
    }

    /**
     * Retorna una copia de los puntos de aparición del nivel.
     */
    public synchronized List<double[]> getSpawnPointsSnapshot() {
        return viewFactory.copyPoints(worldState.spawnPoints());
    }

    /**
     * Retorna una copia defensiva de las plataformas especiales.
     */
    public synchronized List<PlatformTile> getSpecialPlatformsSnapshot() {
        return viewFactory.copyPlatformTiles(worldState.specialPlatforms());
    }

    /**
     * Retorna una copia defensiva de los hazards.
     */
    public synchronized List<PlatformTile> getHazardsSnapshot() {
        return viewFactory.copyPlatformTiles(worldState.hazards());
    }

    /**
     * Retorna una copia defensiva de los checkpoints.
     */
    public synchronized List<PlatformTile> getCheckpointsSnapshot() {
        return viewFactory.copyPlatformTiles(worldState.checkpoints());
    }

    /**
     * Retorna una copia defensiva del botón actual.
     */
    public synchronized ButtonSwitch getButtonSwitchSnapshot() {
        return viewFactory.copyButton(worldState.buttonSwitch());
    }

    /**
     * Retorna una copia defensiva de la puerta actual.
     */
    public synchronized Door getDoorSnapshot() {
        return viewFactory.copyDoor(worldState.door());
    }

    /**
     * Retorna una copia defensiva de la salida actual.
     */
    public synchronized ExitZone getExitZoneSnapshot() {
        return viewFactory.copyExitZone(worldState.exitZone());
    }

    /**
     * Retorna una copia defensiva de los bloques empujables.
     */
    public synchronized List<PushBlock> getPushBlocksSnapshot() {
        return viewFactory.copyPushBlocks(worldState.pushBlocks());
    }

    /**
     * Retorna el identificador único del jugador controlado localmente.
     *
     * @return id del jugador local o {@code null} si la sesión aún no fue inicializada
     */
    public synchronized String getLocalPlayerId() { return connectionState.getLocalPlayerId(); }

    /**
     * Configura la sesión local como host autoritativo.
     *
     * @param playerId id del jugador local
     * @param playerName nombre visible del jugador local
     * @param localIp IP del socket local
     * @param localPort puerto del socket local
     * @param expectedPlayers tamaño objetivo del lobby
     */
    synchronized void applyHostConfiguration(String playerId, String playerName, String localIp, int localPort, int expectedPlayers) {
        clearSession();
        connectionState.configureAsHost(playerId, playerName, localIp, localPort, expectedPlayers);
        stateMachine.enterLobby();
    }

    /**
     * Configura la sesión local como cliente de un host remoto.
     *
     * @param playerId id del jugador local
     * @param playerName nombre visible del jugador local
     * @param localIp IP del socket local
     * @param localPort puerto del socket local
     * @param hostIp IP del host autoritativo
     * @param hostPort puerto del host autoritativo
     */
    synchronized void applyClientConfiguration(String playerId, String playerName, String localIp, int localPort,
                                               String hostIp, int hostPort) {
        clearSession();
        connectionState.configureAsClient(playerId, playerName, localIp, localPort, hostIp, hostPort);
        stateMachine.enterLobby();
    }

    /**
     * Marca que la sesión salió del lobby y entró a gameplay.
     */
    synchronized void transitionToGameplay() {
        stateMachine.enterGameplay();
        matchState.enterGameplay();
    }

    /**
     * Marca que la campaña terminó y debe mostrarse la pantalla final.
     */
    synchronized void transitionToGameOver() {
        stateMachine.enterGameOver();
        matchState.enterGameOver();
    }

    /**
     * Retorna la fase macro actual de la sesión.
     */
    public synchronized SessionPhase getPhase() { return stateMachine.currentPhase(); }

    /**
     * Indica si la sesión está en la fase consultada.
     */
    public synchronized boolean isInPhase(SessionPhase phase) { return stateMachine.isIn(phase); }

    /**
     * Retorna la IP local declarada para esta instancia.
     *
     * @return IP usada para el bind local
     */
    public synchronized String getLocalIp() { return connectionState.getLocalIp(); }

    /**
     * Retorna el puerto local reservado para UDP.
     *
     * @return puerto local enlazado o esperado
     */
    public synchronized int getLocalPort() { return connectionState.getLocalPort(); }

    /**
     * Retorna la IP del host autoritativo conocida por esta instancia.
     *
     * @return IP del host de la partida
     */
    public synchronized String getHostIp() { return connectionState.getHostIp(); }

    /**
     * Retorna el puerto UDP del host autoritativo.
     *
     * @return puerto del host
     */
    public synchronized int getHostPort() { return connectionState.getHostPort(); }

    /**
     * Indica si esta instancia actúa como host autoritativo.
     *
     * @return {@code true} cuando la simulación local manda snapshots
     */
    public synchronized boolean isHost() { return connectionState.isHost(); }

    /**
     * Retorna el nombre visible configurado para el jugador local.
     *
     * @return nombre mostrado en lobby y marcador
     */
    public synchronized String getPlayerName() { return connectionState.getPlayerName(); }

    /**
     * Retorna la cantidad de jugadores que el host espera en el lobby.
     *
     * @return tamaño objetivo declarado para la sala
     */
    public synchronized int getExpectedPlayers() { return connectionState.getExpectedPlayers(); }

    /**
     * Expone el mapa mutable interno de jugadores para servicios de simulación.
     *
     * <p>Se mantiene con visibilidad de paquete para no filtrar esta estructura
     * a controladores u otras capas externas.</p>
     */
    Map<String, Player> getPlayers() { return worldState.players(); }

    /**
     * Busca un jugador existente por id dentro del estado mutable actual.
     *
     * @param playerId identificador lógico del jugador
     * @return jugador mutable o {@code null} si no existe
     */
    Player findPlayer(String playerId) { return worldState.players().get(playerId); }

    /**
     * Retorna la cantidad actual de jugadores conocidos por la sesión.
     */
    int getPlayerCount() { return worldState.players().size(); }

    /**
     * Expone las plataformas sólidas mutables del estado para la simulación.
     */
    List<PlatformTile> getPlatforms() { return worldState.platforms(); }

    /**
     * Expone las plataformas especiales mutables del estado para la simulación.
     */
    List<PlatformTile> getSpecialPlatforms() { return worldState.specialPlatforms(); }

    /**
     * Expone las zonas peligrosas mutables del nivel actual.
     */
    List<PlatformTile> getHazards() { return worldState.hazards(); }

    /**
     * Expone los checkpoints mutables del nivel actual.
     */
    List<PlatformTile> getCheckpoints() { return worldState.checkpoints(); }

    /**
     * Expone los puntos de aparición mutables del nivel actual.
     */
    List<double[]> getSpawnPoints() { return worldState.spawnPoints(); }

    /**
     * Expone el botón mutable del nivel para la simulación.
     */
    ButtonSwitch getButtonSwitch() { return worldState.buttonSwitch(); }

    /**
     * Reemplaza el botón activo del nivel.
     *
     * @param buttonSwitch nuevo botón o {@code null}
     */
    void setButtonSwitch(ButtonSwitch buttonSwitch) { worldState.setButtonSwitch(buttonSwitch); }

    /**
     * Expone la puerta mutable del nivel para la simulación.
     */
    Door getDoor() { return worldState.door(); }

    /**
     * Reemplaza la puerta activa del nivel.
     *
     * @param door nueva puerta o {@code null}
     */
    void setDoor(Door door) { worldState.setDoor(door); }

    /**
     * Expone la zona de salida mutable del nivel para la simulación.
     */
    ExitZone getExitZone() { return worldState.exitZone(); }

    /**
     * Reemplaza la salida activa del nivel.
     *
     * @param exitZone nueva salida o {@code null}
     */
    void setExitZone(ExitZone exitZone) { worldState.setExitZone(exitZone); }

    /**
     * Expone los bloques empujables mutables del nivel actual.
     */
    List<PushBlock> getPushBlocks() { return worldState.pushBlocks(); }

    /**
     * Retorna el slice interno del mundo para consumidores de la capa de aplicación.
     */
    SessionWorldState worldState() { return worldState; }

    /**
     * Retorna el slice interno de progreso para consumidores de la capa de aplicación.
     */
    SessionMatchState matchState() { return matchState; }

    /**
     * Retorna la máquina de estados interna de la sesión.
     */
    SessionStateMachine stateMachine() { return stateMachine; }

    /**
     * Retorna cuántas veces se reinició la sala actual.
     *
     * @return contador acumulado de reinicios de sala
     */
    public synchronized int getRoomResetCount() { return matchState.getRoomResetCount(); }

    /**
     * Actualiza el contador interno de reinicios de sala.
     *
     * @param roomResetCount nuevo contador absoluto
     */
    synchronized void setRoomResetCount(int roomResetCount) { matchState.setRoomResetCount(roomResetCount); }

    /**
     * Retorna la razón del último reinicio de sala.
     *
     * @return texto explicativo del último reset
     */
    public synchronized String getRoomResetReason() { return matchState.getRoomResetReason(); }

    /**
     * Actualiza la razón del último reinicio de sala.
     *
     * @param roomResetReason nuevo motivo del reset
     */
    synchronized void setRoomResetReason(String roomResetReason) { matchState.setRoomResetReason(roomResetReason); }

    /**
     * Retorna el índice base cero del nivel actual.
     *
     * @return índice interno del nivel en curso
     */
    public synchronized int getCurrentLevelIndex() { return matchState.getCurrentLevelIndex(); }

    /**
     * Actualiza el índice interno del nivel actual.
     *
     * @param currentLevelIndex nuevo índice base cero
     */
    synchronized void setCurrentLevelIndex(int currentLevelIndex) { matchState.setCurrentLevelIndex(currentLevelIndex); }

    /**
     * Retorna el total de niveles disponibles en la campaña actual.
     *
     * @return número total de niveles conocidos
     */
    public synchronized int getTotalLevels() { return matchState.getTotalLevels(); }

    /**
     * Actualiza el total de niveles disponibles en la campaña.
     *
     * @param totalLevels nuevo total conocido
     */
    synchronized void setTotalLevels(int totalLevels) { matchState.setTotalLevels(totalLevels); }

    /**
     * Retorna el tiempo total acumulado de la partida en segundos.
     *
     * @return tiempo de sesión visible para la HUD
     */
    public synchronized double getElapsedTime() { return matchState.getElapsedTime(); }

    /**
     * Actualiza el tiempo acumulado de partida.
     *
     * @param elapsedTime nuevo tiempo absoluto en segundos
     */
    synchronized void setElapsedTime(double elapsedTime) { matchState.setElapsedTime(elapsedTime); }

    /**
     * Indica si la partida está considerada activa.
     *
     * @return {@code true} mientras la campaña sigue en curso
     */
    public synchronized boolean isGameRunning() { return matchState.isGameRunning(); }

    /**
     * Cambia el estado global de ejecución de la partida.
     *
     * @param gameRunning nuevo estado global
     */
    synchronized void setGameRunning(boolean gameRunning) { matchState.setGameRunning(gameRunning); }

    /**
     * Retorna el nombre visible del nivel actual.
     *
     * @return nombre amigable del nivel en curso
     */
    public synchronized String getCurrentLevelName() { return matchState.getCurrentLevelName(); }

    /**
     * Actualiza el nombre visible del nivel actual.
     *
     * @param currentLevelName nuevo nombre del nivel
     */
    synchronized void setCurrentLevelName(String currentLevelName) { matchState.setCurrentLevelName(currentLevelName); }

    /**
     * Retorna el biome o fondo visual actualmente activo.
     *
     * @return clave del fondo actual
     */
    public synchronized String getCurrentBackground() { return matchState.getCurrentBackground(); }

    /**
     * Actualiza la clave del fondo visual del nivel actual.
     *
     * @param currentBackground nuevo identificador de fondo
     */
    synchronized void setCurrentBackground(String currentBackground) { matchState.setCurrentBackground(currentBackground); }

    /**
     * Retorna el tamaño lógico de tile del nivel actual.
     *
     * @return tamaño base de tile en unidades de mundo
     */
    public synchronized int getCurrentTileSize() { return matchState.getCurrentTileSize(); }

    /**
     * Actualiza el tamaño lógico de tile del nivel actual.
     *
     * @param currentTileSize nuevo tamaño base de tile
     */
    synchronized void setCurrentTileSize(int currentTileSize) { matchState.setCurrentTileSize(currentTileSize); }
}
