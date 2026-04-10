package com.dino.presentation.viewmodel;

import com.dino.domain.entities.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Formateador de la lista visible de jugadores del lobby.
 *
 * <p>Extrae de {@code LobbyController} la responsabilidad de convertir
 * snapshots de jugadores en cadenas legibles por la interfaz.</p>
 */
public final class LobbyPlayerListFormatter {
    /**
     * Convierte la lista de jugadores en entradas visibles para el lobby.
     *
     * @param players jugadores actuales del snapshot
     * @return lista de textos aptos para el {@code ListView}
     */
    public List<String> format(List<Player> players) {
        List<String> entries = new ArrayList<>();
        for (Player player : players) {
            String state = player.isReady() ? "READY" : (player.isConnected() ? "Conectado" : "Desconectado");
            entries.add(player.getName() + "  -  " + state);
        }
        return entries;
    }
}
