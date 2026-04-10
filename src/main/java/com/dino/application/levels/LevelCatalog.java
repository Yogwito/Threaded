package com.dino.application.levels;

/**
 * Contrato para obtener niveles de campaña desde una fuente concreta.
 *
 * <p>La implementación actual lee recursos embebidos en el classpath, pero el
 * resto del sistema solo necesita saber que puede cargar un nivel numerado y
 * consultar cuántos niveles hay disponibles.</p>
 */
public interface LevelCatalog {
    /**
     * Carga un nivel numerado de la campaña.
     *
     * @param levelNumber índice base 1 del nivel a obtener
     * @return definición parseada del nivel
     */
    LevelData loadLevel(int levelNumber);

    /**
     * Cuenta cuántos niveles consecutivos hay disponibles en la campaña.
     *
     * @return total de niveles detectados
     */
    int countAvailableLevels();
}
