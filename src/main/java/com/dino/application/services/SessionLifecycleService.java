package com.dino.application.services;

/**
 * Servicio encargado del ciclo de vida macro de una sesión.
 *
 * <p>Extrae de {@link SessionService} la responsabilidad de configurar una
 * instancia como host o cliente, limpiar la sesión actual y ejecutar las
 * transiciones visibles entre lobby, gameplay y pantalla final. De esta forma
 * el store principal conserva el estado, mientras este servicio concentra la
 * política de cambio de fase.</p>
 */
public final class SessionLifecycleService {
    private final SessionService sessionService;

    /**
     * Crea un gestor de ciclo de vida asociado a una sesión concreta.
     *
     * @param sessionService sesión cuyo estado y fases serán administrados
     */
    public SessionLifecycleService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * Configura la sesión local como host autoritativo y entra al lobby.
     *
     * @param playerId id lógico del jugador local
     * @param playerName nombre visible del jugador local
     * @param localIp IP local usada por el socket
     * @param localPort puerto local UDP
     * @param expectedPlayers tamaño objetivo del lobby
     */
    public void configureAsHost(String playerId, String playerName, String localIp, int localPort, int expectedPlayers) {
        sessionService.applyHostConfiguration(playerId, playerName, localIp, localPort, expectedPlayers);
    }

    /**
     * Configura la sesión local como cliente de un host remoto y entra al lobby.
     *
     * @param playerId id lógico del jugador local
     * @param playerName nombre visible del jugador local
     * @param localIp IP local usada por el socket
     * @param localPort puerto local UDP
     * @param hostIp IP del host autoritativo
     * @param hostPort puerto UDP del host
     */
    public void configureAsClient(String playerId, String playerName, String localIp, int localPort,
                                  String hostIp, int hostPort) {
        sessionService.applyClientConfiguration(playerId, playerName, localIp, localPort, hostIp, hostPort);
    }

    /**
     * Limpia por completo la sesión actual y vuelve a fase inactiva.
     */
    public void reset() {
        sessionService.clearSession();
    }

    /**
     * Ejecuta la transición desde lobby hacia gameplay.
     */
    public void enterGameplay() {
        sessionService.transitionToGameplay();
    }

    /**
     * Ejecuta la transición hacia la fase final de resultados.
     */
    public void enterGameOver() {
        sessionService.transitionToGameOver();
    }

    /**
     * Retorna la fase macro visible actual.
     *
     * @return fase vigente de la sesión
     */
    public SessionPhase currentPhase() {
        return sessionService.getPhase();
    }

    /**
     * Indica si la sesión está en una fase concreta.
     *
     * @param phase fase a consultar
     * @return {@code true} cuando la sesión está exactamente en esa fase
     */
    public boolean isInPhase(SessionPhase phase) {
        return sessionService.isInPhase(phase);
    }
}
