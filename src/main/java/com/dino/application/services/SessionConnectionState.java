package com.dino.application.services;

/**
 * Estado de identidad y topología local de una sesión.
 *
 * <p>Agrupa los datos que describen quién es el jugador local, en qué endpoint
 * está enlazada la instancia y a qué host debe hablar cuando actúa como
 * cliente. Extraer este bloque evita que {@link SessionService} mezcle metadatos
 * de conexión con progreso de partida y geometría del mundo.</p>
 *
 * <p>Este tipo no implementa validación de red ni resuelve transiciones de
 * sesión. Su rol es estrictamente almacenar la identidad local y la topología
 * host/cliente que luego consumen coordinadores, casos de uso y helpers de
 * presentación.</p>
 */
final class SessionConnectionState {
    private String localPlayerId;
    private String localIp;
    private int localPort;
    private String hostIp;
    private int hostPort;
    private boolean host;
    private String playerName;
    private int expectedPlayers;

    /**
     * Configura la sesión local como host autoritativo.
     *
     * <p>En este modo la instancia se anuncia a sí misma como host remoto y
     * conserva también la cantidad esperada de jugadores del lobby.</p>
     */
    void configureAsHost(String playerId, String playerName, String localIp, int localPort, int expectedPlayers) {
        this.localPlayerId = playerId;
        this.localIp = localIp;
        this.localPort = localPort;
        this.hostIp = localIp;
        this.hostPort = localPort;
        this.host = true;
        this.playerName = playerName;
        this.expectedPlayers = expectedPlayers;
    }

    /**
     * Configura la sesión local como cliente conectado a un host remoto.
     *
     * <p>En modo cliente el cupo esperado del lobby se deja en {@code 0}
     * porque ese dato lo controla el host.</p>
     */
    void configureAsClient(String playerId, String playerName, String localIp, int localPort, String hostIp, int hostPort) {
        this.localPlayerId = playerId;
        this.localIp = localIp;
        this.localPort = localPort;
        this.hostIp = hostIp;
        this.hostPort = hostPort;
        this.host = false;
        this.playerName = playerName;
        this.expectedPlayers = 0;
    }

    /**
     * Limpia la configuración de conexión de la sesión actual.
     */
    void clear() {
        localPlayerId = null;
        localIp = null;
        localPort = 0;
        hostIp = null;
        hostPort = 0;
        host = false;
        playerName = null;
        expectedPlayers = 0;
    }

    /**
     * Retorna el identificador del jugador local.
     *
     * @return id local configurado o {@code null} si no existe sesión activa
     */
    String getLocalPlayerId() {
        return localPlayerId;
    }

    /**
     * Retorna la IP local enlazada por la instancia actual.
     *
     * @return IP local configurada
     */
    String getLocalIp() {
        return localIp;
    }

    /**
     * Retorna el puerto local enlazado por la instancia actual.
     *
     * @return puerto local configurado
     */
    int getLocalPort() {
        return localPort;
    }

    /**
     * Retorna la IP del host conocida por esta sesión.
     *
     * @return IP del host remoto o local según el rol
     */
    String getHostIp() {
        return hostIp;
    }

    /**
     * Retorna el puerto del host conocido por esta sesión.
     *
     * @return puerto del host remoto o local según el rol
     */
    int getHostPort() {
        return hostPort;
    }

    /**
     * Indica si la instancia actual actúa como host autoritativo.
     *
     * @return {@code true} si la sesión local es host
     */
    boolean isHost() {
        return host;
    }

    /**
     * Retorna el nombre visible del jugador local.
     *
     * @return nombre configurado para el jugador local
     */
    String getPlayerName() {
        return playerName;
    }

    /**
     * Retorna el tamaño esperado del lobby.
     *
     * @return número esperado de jugadores cuando la sesión es host
     */
    int getExpectedPlayers() {
        return expectedPlayers;
    }
}
