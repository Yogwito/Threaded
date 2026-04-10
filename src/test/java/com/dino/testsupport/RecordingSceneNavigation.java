package com.dino.testsupport;

import com.dino.presentation.navigation.SceneNavigation;

/**
 * Navegador en memoria para verificar transiciones de escena en pruebas.
 */
public final class RecordingSceneNavigation implements SceneNavigation {
    private String lastScreen;

    @Override
    public void showStartMenu() {
        lastScreen = "start";
    }

    @Override
    public void showLobby() {
        lastScreen = "lobby";
    }

    @Override
    public void showGame() {
        lastScreen = "game";
    }

    @Override
    public void showGameOver() {
        lastScreen = "gameOver";
    }

    public String getLastScreen() {
        return lastScreen;
    }
}
