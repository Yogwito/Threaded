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
    private final SessionSnapshotMapper snapshotMapper;

    private String localPlayerId;
    private String localIp;
    private int localPort;
    private String hostIp;
    private int hostPort;
    private boolean isHost;
    private String playerName;
    private int expectedPlayers;

    private final Map<String, Player> players = new LinkedHashMap<>();
    private final Map<String, InetSocketAddress> peerAddresses = new LinkedHashMap<>();
    private final List<PlatformTile> platforms = new ArrayList<>();
    private final List<PlatformTile> specialPlatforms = new ArrayList<>();
    private final List<PlatformTile> hazards = new ArrayList<>();
    private final List<PlatformTile> checkpoints = new ArrayList<>();
    private final List<double[]> spawnPoints = new ArrayList<>();
    private ButtonSwitch buttonSwitch;
    private Door door;
    private ExitZone exitZone;
    private final List<PushBlock> pushBlocks = new ArrayList<>();
    private int roomResetCount;
    private String roomResetReason = "";
    private int currentLevelIndex;
    private int totalLevels;
    private double elapsedTime;
    private boolean gameRunning;
    private volatile long lastSnapshotSeq = -1;
    private long nextSnapshotSeq = 0;
    private final List<CollectibleItem> coins = new ArrayList<>();
    private final Map<String, Long> peerLastSeenMillis = new LinkedHashMap<>();
    private String currentLevelName = "";
    private String currentBackground = "default";
    private int currentTileSize = 64;

    /**
     * Crea un contenedor de sesión asociado al bus de eventos global.
     *
     * @param eventPublisher publicador usado para notificar recepción de snapshots
     */
    public SessionService(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.snapshotMapper = new SessionSnapshotMapper();
    }

    /**
     * Registra o reemplaza un jugador en el estado actual.
     *
     * @param player jugador a almacenar
     */
    public synchronized void addPlayer(Player player) { players.put(player.getId(), player); }

    /**
     * Elimina un jugador y cualquier rastro de su red asociada.
     *
     * @param playerId identificador único del jugador
     */
    public synchronized void removePlayer(String playerId) {
        players.remove(playerId);
        peerAddresses.remove(playerId);
        peerLastSeenMillis.remove(playerId);
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
    public synchronized void updateFromSnapshot(Map<String, Object> data) {
        if (data.containsKey("seq")) {
            long seq = ((Number) data.get("seq")).longValue();
            if (seq <= lastSnapshotSeq) return;
            lastSnapshotSeq = seq;
        }
        snapshotMapper.applySnapshot(this, data);

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
    public synchronized Map<String, Object> getSnapshotData() {
        return snapshotMapper.buildSnapshot(this, ++nextSnapshotSeq);
    }

    /**
     * Retorna la lista mutable de monedas del host.
     */
    public List<CollectibleItem> getCoins() { return coins; }

    /**
     * Retorna una copia defensiva de las monedas para la UI.
     */
    public synchronized List<CollectibleItem> getCoinsSnapshot() {
        List<CollectibleItem> snapshot = new ArrayList<>();
        for (CollectibleItem c : coins) {
            CollectibleItem copy = new CollectibleItem(c.getId(), c.getX(), c.getY(), c.getPoints());
            copy.setActive(c.isActive());
            snapshot.add(copy);
        }
        return snapshot;
    }

    /**
     * Limpia por completo el estado de la sesión actual.
     */
    public synchronized void reset() {
        players.clear();
        peerAddresses.clear();
        platforms.clear();
        specialPlatforms.clear();
        hazards.clear();
        checkpoints.clear();
        spawnPoints.clear();
        pushBlocks.clear();
        coins.clear();
        buttonSwitch = null;
        door = null;
        exitZone = null;
        roomResetCount = 0;
        roomResetReason = "";
        currentLevelIndex = 0;
        totalLevels = 0;
        elapsedTime = 0;
        gameRunning = false;
        currentLevelName = "";
        currentBackground = "default";
        currentTileSize = 64;
        lastSnapshotSeq = -1;
        nextSnapshotSeq = 0;
        localPlayerId = null;
        isHost = false;
        peerLastSeenMillis.clear();
    }

    /**
     * Asocia un jugador remoto a una dirección UDP y actualiza su último pulso.
     */
    public synchronized void registerPeerAddress(String playerId, InetSocketAddress address) {
        if (playerId != null && address != null) {
            peerAddresses.put(playerId, address);
            peerLastSeenMillis.put(playerId, System.currentTimeMillis());
        }
    }

    /**
     * Lista únicamente peers remotos, excluyendo a la instancia local.
     */
    public synchronized List<InetSocketAddress> getRemotePeerAddresses() {
        List<InetSocketAddress> remotes = new ArrayList<>();
        for (Map.Entry<String, InetSocketAddress> entry : peerAddresses.entrySet()) {
            if (!Objects.equals(entry.getKey(), localPlayerId)) remotes.add(entry.getValue());
        }
        return remotes;
    }

    /**
     * Cambia el estado de listo de un jugador.
     */
    public synchronized void markPlayerReady(String playerId, boolean ready) {
        Player player = players.get(playerId);
        if (player != null) player.setReady(ready);
    }

    /**
     * Cambia el estado de conexión de un jugador y sincroniza su timestamp.
     */
    public synchronized void markPlayerConnected(String playerId, boolean connected) {
        Player player = players.get(playerId);
        if (player != null) player.setConnected(connected);
        if (connected) {
            peerLastSeenMillis.put(playerId, System.currentTimeMillis());
        } else {
            peerLastSeenMillis.remove(playerId);
        }
    }

    /**
     * Elimina la dirección de red asociada a un peer.
     */
    public synchronized void removePeerAddress(String playerId) {
        if (playerId != null) {
            peerAddresses.remove(playerId);
            peerLastSeenMillis.remove(playerId);
        }
    }

    /**
     * Registra que se recibió actividad reciente de un peer.
     */
    public synchronized void markPeerSeen(String playerId) {
        if (playerId != null) peerLastSeenMillis.put(playerId, System.currentTimeMillis());
    }

    /**
     * Calcula la edad del último paquete observado de un peer.
     *
     * @return milisegundos desde el último mensaje o {@code null} si no hay dato
     */
    public synchronized Long getPeerAgeMillis(String playerId) {
        Long lastSeen = peerLastSeenMillis.get(playerId);
        if (lastSeen == null) return null;
        return Math.max(0L, System.currentTimeMillis() - lastSeen);
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
    public synchronized List<String> expireInactivePeers(long timeoutMillis) {
        long now = System.currentTimeMillis();
        List<String> expired = new ArrayList<>();
        for (Player player : players.values()) {
            if (!player.isConnected()) continue;
            if (Objects.equals(player.getId(), localPlayerId) && isHost) continue;
            Long lastSeen = peerLastSeenMillis.get(player.getId());
            if (lastSeen == null) continue;
            if (now - lastSeen > timeoutMillis) {
                player.setConnected(false);
                peerAddresses.remove(player.getId());
                expired.add(player.getId());
            }
        }
        expired.forEach(peerLastSeenMillis::remove);
        return expired;
    }

    /**
     * Retorna una copia defensiva de los jugadores.
     */
    public synchronized List<Player> getPlayersSnapshot() {
        List<Player> snapshot = new ArrayList<>();
        for (Player player : players.values()) {
            Player copy = new Player();
            copy.setId(player.getId());
            copy.setName(player.getName());
            copy.setColor(player.getColor());
            copy.setX(player.getX());
            copy.setY(player.getY());
            copy.setVx(player.getVx());
            copy.setVy(player.getVy());
            copy.setCoyoteTimer(player.getCoyoteTimer());
            copy.setGrounded(player.isGrounded());
            copy.setAlive(player.isAlive());
            copy.setAtExit(player.isAtExit());
            copy.setTargetX(player.getTargetX());
            copy.setTargetY(player.getTargetY());
            copy.setScore(player.getScore());
            copy.setDeaths(player.getDeaths());
            copy.setFinishOrder(player.getFinishOrder());
            copy.setConnected(player.isConnected());
            copy.setReady(player.isReady());
            snapshot.add(copy);
        }
        return snapshot;
    }

    /**
     * Retorna una copia defensiva de las plataformas del nivel.
     */
    public synchronized List<PlatformTile> getPlatformsSnapshot() {
        List<PlatformTile> snapshot = new ArrayList<>();
        for (PlatformTile platform : platforms) {
            snapshot.add(new PlatformTile(platform.getId(), platform.getX(), platform.getY(), platform.getWidth(), platform.getHeight()));
        }
        return snapshot;
    }

    /**
     * Retorna una copia de los puntos de aparición del nivel.
     */
    public synchronized List<double[]> getSpawnPointsSnapshot() {
        List<double[]> snapshot = new ArrayList<>();
        for (double[] spawn : spawnPoints) snapshot.add(new double[]{spawn[0], spawn[1]});
        return snapshot;
    }

    /**
     * Retorna una copia defensiva de las plataformas especiales.
     */
    public synchronized List<PlatformTile> getSpecialPlatformsSnapshot() {
        List<PlatformTile> snapshot = new ArrayList<>();
        for (PlatformTile platform : specialPlatforms) {
            snapshot.add(new PlatformTile(platform.getId(), platform.getX(), platform.getY(), platform.getWidth(), platform.getHeight()));
        }
        return snapshot;
    }

    /**
     * Retorna una copia defensiva de los hazards.
     */
    public synchronized List<PlatformTile> getHazardsSnapshot() {
        List<PlatformTile> snapshot = new ArrayList<>();
        for (PlatformTile hazard : hazards) {
            snapshot.add(new PlatformTile(hazard.getId(), hazard.getX(), hazard.getY(), hazard.getWidth(), hazard.getHeight()));
        }
        return snapshot;
    }

    /**
     * Retorna una copia defensiva de los checkpoints.
     */
    public synchronized List<PlatformTile> getCheckpointsSnapshot() {
        List<PlatformTile> snapshot = new ArrayList<>();
        for (PlatformTile checkpoint : checkpoints) {
            snapshot.add(new PlatformTile(checkpoint.getId(), checkpoint.getX(), checkpoint.getY(), checkpoint.getWidth(), checkpoint.getHeight()));
        }
        return snapshot;
    }

    /**
     * Retorna una copia defensiva del botón actual.
     */
    public synchronized ButtonSwitch getButtonSwitchSnapshot() {
        if (buttonSwitch == null) return null;
        ButtonSwitch copy = new ButtonSwitch(buttonSwitch.getId(), buttonSwitch.getX(), buttonSwitch.getY(), buttonSwitch.getWidth(), buttonSwitch.getHeight());
        copy.setPressed(buttonSwitch.isPressed());
        return copy;
    }

    /**
     * Retorna una copia defensiva de la puerta actual.
     */
    public synchronized Door getDoorSnapshot() {
        if (door == null) return null;
        Door copy = new Door(door.getId(), door.getX(), door.getY(), door.getWidth(), door.getHeight());
        copy.setOpen(door.isOpen());
        return copy;
    }

    /**
     * Retorna una copia defensiva de la salida actual.
     */
    public synchronized ExitZone getExitZoneSnapshot() {
        if (exitZone == null) return null;
        return new ExitZone(exitZone.getX(), exitZone.getY(), exitZone.getWidth(), exitZone.getHeight());
    }

    /**
     * Retorna una copia defensiva de los bloques empujables.
     */
    public synchronized List<PushBlock> getPushBlocksSnapshot() {
        List<PushBlock> snapshot = new ArrayList<>();
        for (PushBlock block : pushBlocks) {
            PushBlock copy = new PushBlock(block.getId(), block.getX(), block.getY(), block.getWidth(), block.getHeight());
            copy.setVx(block.getVx());
            copy.setVy(block.getVy());
            copy.setHomeX(block.getHomeX());
            copy.setHomeY(block.getHomeY());
            snapshot.add(copy);
        }
        return snapshot;
    }

    public String getLocalPlayerId() { return localPlayerId; }
    public void setLocalPlayerId(String v) { this.localPlayerId = v; }
    public String getLocalIp() { return localIp; }
    public void setLocalIp(String v) { this.localIp = v; }
    public int getLocalPort() { return localPort; }
    public void setLocalPort(int v) { this.localPort = v; }
    public String getHostIp() { return hostIp; }
    public void setHostIp(String v) { this.hostIp = v; }
    public int getHostPort() { return hostPort; }
    public void setHostPort(int v) { this.hostPort = v; }
    public boolean isHost() { return isHost; }
    public void setHost(boolean v) { this.isHost = v; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String v) { this.playerName = v; }
    public int getExpectedPlayers() { return expectedPlayers; }
    public void setExpectedPlayers(int v) { this.expectedPlayers = v; }
    public Map<String, Player> getPlayers() { return players; }
    public List<PlatformTile> getPlatforms() { return platforms; }
    public List<PlatformTile> getSpecialPlatforms() { return specialPlatforms; }
    public List<PlatformTile> getHazards() { return hazards; }
    public List<PlatformTile> getCheckpoints() { return checkpoints; }
    public List<double[]> getSpawnPoints() { return spawnPoints; }
    public ButtonSwitch getButtonSwitch() { return buttonSwitch; }
    public void setButtonSwitch(ButtonSwitch buttonSwitch) { this.buttonSwitch = buttonSwitch; }
    public Door getDoor() { return door; }
    public void setDoor(Door door) { this.door = door; }
    public ExitZone getExitZone() { return exitZone; }
    public void setExitZone(ExitZone exitZone) { this.exitZone = exitZone; }
    public List<PushBlock> getPushBlocks() { return pushBlocks; }
    public synchronized int getRoomResetCount() { return roomResetCount; }
    public synchronized void setRoomResetCount(int roomResetCount) { this.roomResetCount = roomResetCount; }
    public synchronized String getRoomResetReason() { return roomResetReason; }
    public synchronized void setRoomResetReason(String roomResetReason) { this.roomResetReason = roomResetReason; }
    public synchronized int getCurrentLevelIndex() { return currentLevelIndex; }
    public synchronized void setCurrentLevelIndex(int currentLevelIndex) { this.currentLevelIndex = currentLevelIndex; }
    public synchronized int getTotalLevels() { return totalLevels; }
    public synchronized void setTotalLevels(int totalLevels) { this.totalLevels = totalLevels; }
    public synchronized double getElapsedTime() { return elapsedTime; }
    public synchronized void setElapsedTime(double elapsedTime) { this.elapsedTime = elapsedTime; }
    public synchronized boolean isGameRunning() { return gameRunning; }
    public synchronized void setGameRunning(boolean gameRunning) { this.gameRunning = gameRunning; }
    public synchronized String getCurrentLevelName() { return currentLevelName; }
    public synchronized void setCurrentLevelName(String currentLevelName) { this.currentLevelName = currentLevelName; }
    public synchronized String getCurrentBackground() { return currentBackground; }
    public synchronized void setCurrentBackground(String currentBackground) { this.currentBackground = currentBackground; }
    public synchronized int getCurrentTileSize() { return currentTileSize; }
    public synchronized void setCurrentTileSize(int currentTileSize) { this.currentTileSize = currentTileSize; }
}
