package com.dino.application.services;

/**
 * Estado de progreso de lobby/partida y secuenciación de snapshots.
 *
 * <p>Contiene el metadato temporal y de campaña que antes vivía mezclado con
 * la geometría del mundo. También encapsula la numeración de snapshots para que
 * la lógica de orden no quede dispersa en {@link SessionService}.</p>
 *
 * <p>El tipo actúa como slice mutable interno de la sesión. Mantiene tiempo,
 * nivel actual, razón del último reinicio de sala, información de presentación
 * del nivel y secuencias de snapshots entrantes/salientes.</p>
 */
final class SessionMatchState {
    private static final String DEFAULT_BACKGROUND = "default";
    private static final int DEFAULT_TILE_SIZE = 64;

    private int roomResetCount;
    private String roomResetReason = "";
    private int currentLevelIndex;
    private int totalLevels;
    private double elapsedTime;
    private boolean gameRunning;
    private long lastSnapshotSeq = -1;
    private long nextSnapshotSeq;
    private String currentLevelName = "";
    private String currentBackground = DEFAULT_BACKGROUND;
    private int currentTileSize = DEFAULT_TILE_SIZE;

    /**
     * Reinicia el progreso y los contadores transitorios de la partida.
     */
    void clear() {
        roomResetCount = 0;
        roomResetReason = "";
        currentLevelIndex = 0;
        totalLevels = 0;
        elapsedTime = 0;
        gameRunning = false;
        lastSnapshotSeq = -1;
        nextSnapshotSeq = 0;
        currentLevelName = "";
        currentBackground = DEFAULT_BACKGROUND;
        currentTileSize = DEFAULT_TILE_SIZE;
    }

    /**
     * Inicializa el progreso de campaña desde el primer nivel.
     *
     * <p>Además de fijar la cantidad total de niveles, reinicia tiempo
     * acumulado y contadores de reinicio de sala para arrancar una campaña
     * limpia.</p>
     */
    void beginCampaign(int totalLevels) {
        currentLevelIndex = 0;
        this.totalLevels = Math.max(1, totalLevels);
        elapsedTime = 0;
        gameRunning = true;
        roomResetCount = 0;
        roomResetReason = "";
    }

    /**
     * Marca que la sesión entró a gameplay.
     *
     * <p>Se conserva el estado previo del resto de metadatos y solo se asegura
     * que la partida quede considerada como activa.</p>
     */
    void enterGameplay() {
        gameRunning = true;
    }

    /**
     * Marca que la sesión terminó la campaña visible.
     */
    void enterGameOver() {
        gameRunning = false;
    }

    /**
     * Acumula tiempo de partida en segundos.
     */
    void advanceElapsedTime(double dt) {
        elapsedTime += dt;
    }

    /**
     * Registra un reinicio de sala con la razón visible asociada.
     */
    void recordRoomReset(String reason) {
        roomResetCount++;
        roomResetReason = reason;
    }

    /**
     * Actualiza el metadato visible del nivel cargado.
     */
    void updateLevelPresentation(int levelIndex, String levelName, String background, int tileSize) {
        currentLevelIndex = levelIndex;
        currentLevelName = levelName;
        currentBackground = background;
        currentTileSize = tileSize;
    }

    /**
     * Intenta aceptar una secuencia recibida para evitar retrocesos por UDP.
     *
     * <p>Una secuencia repetida o menor indica un snapshot fuera de orden o
     * retrasado, por lo que debe descartarse.</p>
     *
     * @return {@code true} si el snapshot debe procesarse
     */
    boolean acceptIncomingSequence(long sequence) {
        if (sequence <= lastSnapshotSeq) {
            return false;
        }
        lastSnapshotSeq = sequence;
        return true;
    }

    /**
     * Retorna la siguiente secuencia a emitir por el host.
     */
    long nextOutgoingSequence() {
        return ++nextSnapshotSeq;
    }

    int getRoomResetCount() {
        return roomResetCount;
    }

    void setRoomResetCount(int roomResetCount) {
        this.roomResetCount = roomResetCount;
    }

    String getRoomResetReason() {
        return roomResetReason;
    }

    void setRoomResetReason(String roomResetReason) {
        this.roomResetReason = roomResetReason;
    }

    int getCurrentLevelIndex() {
        return currentLevelIndex;
    }

    void setCurrentLevelIndex(int currentLevelIndex) {
        this.currentLevelIndex = currentLevelIndex;
    }

    int getTotalLevels() {
        return totalLevels;
    }

    void setTotalLevels(int totalLevels) {
        this.totalLevels = totalLevels;
    }

    double getElapsedTime() {
        return elapsedTime;
    }

    void setElapsedTime(double elapsedTime) {
        this.elapsedTime = elapsedTime;
    }

    boolean isGameRunning() {
        return gameRunning;
    }

    void setGameRunning(boolean gameRunning) {
        this.gameRunning = gameRunning;
    }

    String getCurrentLevelName() {
        return currentLevelName;
    }

    void setCurrentLevelName(String currentLevelName) {
        this.currentLevelName = currentLevelName;
    }

    String getCurrentBackground() {
        return currentBackground;
    }

    void setCurrentBackground(String currentBackground) {
        this.currentBackground = currentBackground;
    }

    int getCurrentTileSize() {
        return currentTileSize;
    }

    void setCurrentTileSize(int currentTileSize) {
        this.currentTileSize = currentTileSize;
    }
}
