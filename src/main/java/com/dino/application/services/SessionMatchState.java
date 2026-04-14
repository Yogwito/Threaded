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

    /**
     * Retorna cuántas veces se reinició la sala actual.
     */
    int getRoomResetCount() {
        return roomResetCount;
    }

    /**
     * Sobrescribe el contador de reinicios de sala.
     *
     * @param roomResetCount nuevo contador persistido
     */
    void setRoomResetCount(int roomResetCount) {
        this.roomResetCount = roomResetCount;
    }

    /**
     * Retorna la razón visible del último reinicio de sala.
     */
    String getRoomResetReason() {
        return roomResetReason;
    }

    /**
     * Actualiza el motivo visible del último reinicio.
     *
     * @param roomResetReason nuevo motivo textual
     */
    void setRoomResetReason(String roomResetReason) {
        this.roomResetReason = roomResetReason;
    }

    /**
     * Retorna el índice del nivel actualmente cargado.
     */
    int getCurrentLevelIndex() {
        return currentLevelIndex;
    }

    /**
     * Fija el índice del nivel actualmente cargado.
     *
     * @param currentLevelIndex nuevo índice de nivel
     */
    void setCurrentLevelIndex(int currentLevelIndex) {
        this.currentLevelIndex = currentLevelIndex;
    }

    /**
     * Retorna el total de niveles de la campaña actual.
     */
    int getTotalLevels() {
        return totalLevels;
    }

    /**
     * Actualiza cuántos niveles contiene la campaña.
     *
     * @param totalLevels cantidad total de niveles
     */
    void setTotalLevels(int totalLevels) {
        this.totalLevels = totalLevels;
    }

    /**
     * Retorna el tiempo acumulado de juego en segundos.
     */
    double getElapsedTime() {
        return elapsedTime;
    }

    /**
     * Sobrescribe el tiempo acumulado de juego.
     *
     * @param elapsedTime nuevo tiempo total en segundos
     */
    void setElapsedTime(double elapsedTime) {
        this.elapsedTime = elapsedTime;
    }

    /**
     * Indica si la campaña sigue marcada como activa.
     */
    boolean isGameRunning() {
        return gameRunning;
    }

    /**
     * Marca si la campaña debe considerarse activa.
     *
     * @param gameRunning nuevo estado de ejecución
     */
    void setGameRunning(boolean gameRunning) {
        this.gameRunning = gameRunning;
    }

    /**
     * Retorna el nombre visible del nivel actual.
     */
    String getCurrentLevelName() {
        return currentLevelName;
    }

    /**
     * Actualiza el nombre visible del nivel actual.
     *
     * @param currentLevelName nuevo nombre de presentación
     */
    void setCurrentLevelName(String currentLevelName) {
        this.currentLevelName = currentLevelName;
    }

    /**
     * Retorna el biome o fondo visual asociado al nivel actual.
     */
    String getCurrentBackground() {
        return currentBackground;
    }

    /**
     * Actualiza el biome o fondo visual del nivel actual.
     *
     * @param currentBackground nuevo identificador de fondo
     */
    void setCurrentBackground(String currentBackground) {
        this.currentBackground = currentBackground;
    }

    /**
     * Retorna el tamaño base de tile del nivel actual.
     */
    int getCurrentTileSize() {
        return currentTileSize;
    }

    /**
     * Actualiza el tamaño base de tile del nivel actual.
     *
     * @param currentTileSize nuevo tamaño de tile
     */
    void setCurrentTileSize(int currentTileSize) {
        this.currentTileSize = currentTileSize;
    }
}
