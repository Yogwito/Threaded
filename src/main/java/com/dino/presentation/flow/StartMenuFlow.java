package com.dino.presentation.flow;

import com.dino.application.usecases.CreateSessionUseCase;
import com.dino.application.usecases.JoinSessionUseCase;
import com.dino.infrastructure.network.NetworkPeer;
import com.dino.presentation.navigation.SceneNavigation;

import java.util.function.Supplier;

/**
 * Fachada de aplicación para la pantalla inicial.
 *
 * <p>Encapsula el arranque del transporte de red, la ejecución de los casos de
 * uso de host/cliente y la navegación al lobby. Su objetivo es mantener al
 * controlador JavaFX enfocado en validación básica de formulario y feedback de
 * vista.</p>
 */
public final class StartMenuFlow {
    private final Supplier<NetworkPeer> networkPeerOpener;
    private final Supplier<CreateSessionUseCase> createSessionUseCaseFactory;
    private final Supplier<JoinSessionUseCase> joinSessionUseCaseFactory;
    private final Runnable shutdownNetworking;
    private final SceneNavigation navigation;

    /**
     * Construye el flujo con las operaciones mínimas del runtime.
     *
     * @param networkPeerOpener callback que prepara un peer de red nuevo
     * @param createSessionUseCaseFactory fabrica del caso de uso de host
     * @param joinSessionUseCaseFactory fabrica del caso de uso de cliente
     * @param shutdownNetworking callback para limpiar red si algo falla
     * @param navigation navegador de pantallas
     */
    public StartMenuFlow(Supplier<NetworkPeer> networkPeerOpener,
                         Supplier<CreateSessionUseCase> createSessionUseCaseFactory,
                         Supplier<JoinSessionUseCase> joinSessionUseCaseFactory,
                         Runnable shutdownNetworking,
                         SceneNavigation navigation) {
        this.networkPeerOpener = networkPeerOpener;
        this.createSessionUseCaseFactory = createSessionUseCaseFactory;
        this.joinSessionUseCaseFactory = joinSessionUseCaseFactory;
        this.shutdownNetworking = shutdownNetworking;
        this.navigation = navigation;
    }

    /**
     * Crea una sala nueva y abre el lobby local.
     *
     * @param playerName nombre visible del host
     * @param localIp IP local a enlazar
     * @param localPort puerto UDP local
     * @param expectedPlayers tamaño esperado del lobby
     * @throws Exception si falla el bind, la inicialización o la navegación
     */
    public void createLobby(String playerName, String localIp, int localPort, int expectedPlayers) throws Exception {
        executeAndOpenLobby(() ->
            createSessionUseCaseFactory.get().execute(playerName, localIp, localPort, expectedPlayers));
    }

    /**
     * Se une a una sala remota y abre el lobby local.
     *
     * @param playerName nombre visible del jugador local
     * @param localIp IP local a enlazar
     * @param localPort puerto UDP local
     * @param hostIp IP del host remoto
     * @param hostPort puerto UDP del host
     * @throws Exception si falla el bind, el envío inicial o la navegación
     */
    public void joinLobby(String playerName, String localIp, int localPort, String hostIp, int hostPort) throws Exception {
        executeAndOpenLobby(() ->
            joinSessionUseCaseFactory.get().execute(playerName, localIp, localPort, hostIp, hostPort));
    }

    private void executeAndOpenLobby(ThrowingAction action) throws Exception {
        networkPeerOpener.get();
        try {
            action.run();
            navigation.showLobby();
        } catch (Exception exception) {
            shutdownNetworking.run();
            throw exception;
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
