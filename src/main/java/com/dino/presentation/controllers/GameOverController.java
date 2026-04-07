package com.dino.presentation.controllers;

import com.dino.MainApp;
import com.dino.domain.entities.Player;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controlador de la pantalla final de resultados.
 *
 * <p>Toma el estado final replicado de la sesión, construye una tabla ordenada
 * por puntaje y muestra el ganador junto al tiempo total de campaña. Desde esta
 * vista también se reinicia el runtime compartido para volver al menú
 * principal.</p>
 */
public class GameOverController implements Initializable {
    @FXML private TableView<Player> resultsTable;
    @FXML private TableColumn<Player, String> posColumn;
    @FXML private TableColumn<Player, String> nameColumn;
    @FXML private TableColumn<Player, Integer> scoreColumn;
    @FXML private Label winnerLabel;
    @FXML private Label totalTimeLabel;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        List<Player> sorted = new ArrayList<>(MainApp.sessionService.getPlayersSnapshot());
        sorted.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

        posColumn.setCellValueFactory(data -> {
            int pos = sorted.indexOf(data.getValue()) + 1;
            return new SimpleStringProperty("#" + pos);
        });
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        scoreColumn.setCellValueFactory(data ->
            new SimpleIntegerProperty(data.getValue().getScore()).asObject());

        resultsTable.getItems().addAll(sorted);

        if (!sorted.isEmpty()) {
            boolean tie = sorted.size() > 1 && sorted.get(0).getScore() == sorted.get(1).getScore();
            winnerLabel.setText(tie
                ? "¡Empate!"
                : "Ganador: " + sorted.get(0).getName() + " (" + sorted.get(0).getScore() + " masa)");
        }

        totalTimeLabel.setText(String.format("Duración: %.1fs", MainApp.sessionService.getElapsedTime()));
    }

    @FXML
    /**
     * Limpia el estado global de la aplicación y regresa al menú inicial.
     */
    public void onVolverAlMenu() {
        MainApp.resetRuntimeState();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com.dino.views/start_menu.fxml"));
            Scene scene = new Scene(loader.load(), 1280, 780);
            MainApp.getStage().setScene(scene);
        } catch (Exception e) {
            System.err.println("[GameOverController] Error: " + e.getMessage());
        }
    }
}
