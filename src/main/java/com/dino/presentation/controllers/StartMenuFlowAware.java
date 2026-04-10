package com.dino.presentation.controllers;

import com.dino.presentation.flow.StartMenuFlow;

/**
 * Contrato de inyección para controladores que consumen el flujo de la
 * pantalla inicial.
 *
 * <p>Su objetivo es desacoplar el formulario de arranque del runtime y de los
 * casos de uso concretos de creación/unión de sesión.</p>
 */
public interface StartMenuFlowAware {
    /**
     * Inyecta el flujo de la pantalla inicial antes de cargar la escena.
     *
     * @param startMenuFlow fachada de acciones del menú inicial
     */
    void setStartMenuFlow(StartMenuFlow startMenuFlow);
}
