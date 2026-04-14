package com.dino.testsupport;

import com.dino.presentation.navigation.SceneNavigation;

/**
 * Navegador en memoria para verificar transiciones de escena en pruebas.
 */
public final class RecordingSceneNavigation implements SceneNavigation {
    private String lastScreen;

    /**
     * Registra la apertura de la pantalla inicial.
     */
    @Override
    public void showStartMenu() {
        lastScreen = "start";
    }

    /**
     * Registra la apertura del lobby.
     */
    @Override
    public void showLobby() {
        lastScreen = "lobby";
    }

    /**
     * Registra la apertura de la escena de gameplay.
     */
    @Override
    public void showGame() {
        lastScreen = "game";
    }

    /**
     * Registra la apertura de la pantalla final.
     */
    @Override
    public void showGameOver() {
        lastScreen = "gameOver";
    }

    /**
     * Retorna la última pantalla solicitada por el flujo bajo prueba.
     *
     * @return identificador corto de la pantalla mostrada más recientemente
     */
    public String getLastScreen() {
        return lastScreen;
    }
}
