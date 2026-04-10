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

    String getLocalPlayerId() {
        return localPlayerId;
    }

    String getLocalIp() {
        return localIp;
    }

    int getLocalPort() {
        return localPort;
    }

    String getHostIp() {
        return hostIp;
    }

    int getHostPort() {
        return hostPort;
    }

    boolean isHost() {
        return host;
    }

    String getPlayerName() {
        return playerName;
    }

    int getExpectedPlayers() {
        return expectedPlayers;
    }
}
