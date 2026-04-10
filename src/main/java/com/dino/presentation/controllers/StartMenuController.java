package com.dino.presentation.controllers;

import com.dino.presentation.flow.StartMenuFlow;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controlador de la pantalla inicial.
 *
 * <p>Recoge los datos básicos de red y del jugador, decide si la instancia
 * actuará como host o cliente y delega la creación o unión de la sesión a los
 * flujo de aplicación específico del menú inicial. Después abre la vista de
 * lobby con el runtime ya inicializado.</p>
 */
public class StartMenuController implements Initializable, StartMenuFlowAware {
    @FXML private TextField playerNameField;
    @FXML private RadioButton createRadio;
    @FXML private RadioButton joinRadio;
    @FXML private ToggleGroup modeToggle;
    @FXML private TextField localIpField;
    @FXML private TextField localPortField;
    @FXML private TextField hostIpField;
    @FXML private TextField hostPortField;
    @FXML private HBox expectedPlayersBox;
    @FXML private ChoiceBox<Integer> expectedPlayersChoice;
    @FXML private Label errorLabel;

    private StartMenuFlow startMenuFlow;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStartMenuFlow(StartMenuFlow startMenuFlow) {
        this.startMenuFlow = startMenuFlow;
    }

    /**
     * Inicializa el formulario base y adapta los campos al modo elegido.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        expectedPlayersChoice.getItems().addAll(2, 3, 4);
        expectedPlayersChoice.setValue(2);

        modeToggle.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            boolean isCreate = newT == createRadio;
            expectedPlayersBox.setVisible(isCreate);
            hostIpField.setDisable(isCreate);
            hostPortField.setDisable(isCreate);
        });

        hostIpField.setDisable(true);
        hostPortField.setDisable(true);
    }

    /**
     * Valida el formulario inicial y abre el lobby de la sesión creada/unida.
     */
    @FXML
    public void onAbrirLobby() {
        errorLabel.setVisible(false);
        String name = playerNameField.getText().trim();
        if (name.isEmpty()) {
            showError("Ingresa tu nombre de jugador.");
            return;
        }

        String localIp = localIpField.getText().trim();
        int localPort;
        try {
            localPort = Integer.parseInt(localPortField.getText().trim());
        } catch (NumberFormatException e) {
            showError("Puerto local inválido.");
            return;
        }

        try {
            if (createRadio.isSelected()) {
                int expected = expectedPlayersChoice.getValue();
                startMenuFlow.createLobby(name, localIp, localPort, expected);
            } else {
                String hostIp = hostIpField.getText().trim();
                if (hostIp.isEmpty()) {
                    showError("Ingresa la IP del host.");
                    return;
                }
                int hostPort;
                try {
                    hostPort = Integer.parseInt(hostPortField.getText().trim());
                } catch (NumberFormatException e) {
                    showError("Puerto host inválido.");
                    return;
                }
                startMenuFlow.joinLobby(name, localIp, localPort, hostIp, hostPort);
            }
        } catch (Exception e) {
            showError("Error al conectar: " + e.getMessage());
        }
    }

    /**
     * Muestra un error breve de validación o conexión en la misma vista.
     */
    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }
}
