package com.dino.presentation.controllers;

import com.dino.domain.entities.Player;
import com.dino.presentation.flow.GameOverScreenFlow;
import com.dino.presentation.flow.GameOverSummary;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
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
 * <p>Consume un resumen final ya calculado y lo proyecta en la tabla de
 * resultados. Desde esta vista también reinicia el runtime compartido para
 * volver al menú principal.</p>
 */
public class GameOverController implements Initializable, GameOverScreenFlowAware {
    @FXML private TableView<Player> resultsTable;
    @FXML private TableColumn<Player, String> posColumn;
    @FXML private TableColumn<Player, String> nameColumn;
    @FXML private TableColumn<Player, Integer> scoreColumn;
    @FXML private Label winnerLabel;
    @FXML private Label totalTimeLabel;

    private GameOverScreenFlow gameOverScreenFlow;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setGameOverScreenFlow(GameOverScreenFlow gameOverScreenFlow) {
        this.gameOverScreenFlow = gameOverScreenFlow;
    }

    /**
     * Construye la tabla final de resultados a partir del snapshot de sesión.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        GameOverSummary summary = gameOverScreenFlow.summary();
        List<Player> sorted = new ArrayList<>(summary.standings());

        posColumn.setCellValueFactory(data -> {
            int pos = sorted.indexOf(data.getValue()) + 1;
            return new SimpleStringProperty("#" + pos);
        });
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        scoreColumn.setCellValueFactory(data ->
            new SimpleIntegerProperty(data.getValue().getScore()).asObject());

        resultsTable.getItems().addAll(sorted);
        winnerLabel.setText(summary.winnerText());
        totalTimeLabel.setText(summary.totalTimeText());
    }

    /**
     * Limpia el runtime compartido y regresa al menú inicial.
     */
    @FXML
    public void onVolverAlMenu() {
        gameOverScreenFlow.resetToStartMenu();
    }
}
