package com.dino.application.levels;

/**
 * Implementación de {@link LevelCatalog} respaldada por recursos del
 * classpath.
 *
 * <p>Actúa como fachada inyectable sobre {@link LevelLoader}. De esta forma la
 * simulación no depende de utilidades estáticas y puede cambiar de fuente de
 * niveles con cambios localizados.</p>
 */
public final class ResourceLevelCatalog implements LevelCatalog {
    /**
     * {@inheritDoc}
     */
    @Override
    public LevelData loadLevel(int levelNumber) {
        return LevelLoader.loadLevel(levelNumber);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int countAvailableLevels() {
        return LevelLoader.countAvailableLevels();
    }
}
