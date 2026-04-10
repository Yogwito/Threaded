package com.dino.presentation.navigation;

/**
 * Contrato mínimo para transiciones entre pantallas principales del juego.
 *
 * <p>La interfaz desacopla coordinadores y flujos de presentación del
 * {@link SceneNavigator} concreto, facilitando pruebas unitarias y evitando
 * que los controladores JavaFX dependan de toda la infraestructura de
 * navegación.</p>
 */
public interface SceneNavigation {
    /**
     * Abre la pantalla inicial de creación/unión de sesión.
     *
     * @throws Exception si falla la carga de la escena
     */
    void showStartMenu() throws Exception;

    /**
     * Abre la pantalla de lobby.
     *
     * @throws Exception si falla la carga de la escena
     */
    void showLobby() throws Exception;

    /**
     * Abre la escena principal de gameplay.
     *
     * @throws Exception si falla la carga de la escena
     */
    void showGame() throws Exception;

    /**
     * Abre la pantalla final de resultados.
     *
     * @throws Exception si falla la carga de la escena
     */
    void showGameOver() throws Exception;
}
