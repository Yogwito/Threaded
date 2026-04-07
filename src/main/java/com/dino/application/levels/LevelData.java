package com.dino.application.levels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resultado inmutable de parsear un archivo de nivel.
 *
 * <p>Agrupa metadata visual y toda la geometría ya traducida a coordenadas de
 * mundo. El host usa esta estructura para inicializar plataformas, cajas,
 * spawns, hazards, checkpoints y metas del nivel cargado.</p>
 */
public class LevelData {
    private final String name;
    private final String background;
    private final int tileSize;
    private final List<Platform> platforms;
    private final List<Platform> specialPlatforms;
    private final List<Box> boxes;
    private final List<double[]> spawnPoints;
    private final List<Platform> hazards;
    private final List<Platform> checkpoints;
    private final List<Platform> goals;

    /**
     * Construye una vista inmutable del nivel parseado.
     *
     * @param name nombre visible del nivel
     * @param background biome o fondo visual del nivel
     * @param tileSize tamaño base de cada tile
     * @param platforms plataformas sólidas normales
     * @param specialPlatforms plataformas especiales
     * @param boxes cajas empujables definidas en el mapa
     * @param spawnPoints puntos de aparición disponibles
     * @param hazards zonas peligrosas
     * @param checkpoints plataformas de checkpoint
     * @param goals zonas meta del nivel
     */
    public LevelData(String name,
                     String background,
                     int tileSize,
                     List<Platform> platforms,
                     List<Platform> specialPlatforms,
                     List<Box> boxes,
                     List<double[]> spawnPoints,
                     List<Platform> hazards,
                     List<Platform> checkpoints,
                     List<Platform> goals) {
        this.name = name;
        this.background = background;
        this.tileSize = tileSize;
        this.platforms = Collections.unmodifiableList(new ArrayList<>(platforms));
        this.specialPlatforms = Collections.unmodifiableList(new ArrayList<>(specialPlatforms));
        this.boxes = Collections.unmodifiableList(new ArrayList<>(boxes));
        this.spawnPoints = Collections.unmodifiableList(copyPoints(spawnPoints));
        this.hazards = Collections.unmodifiableList(new ArrayList<>(hazards));
        this.checkpoints = Collections.unmodifiableList(new ArrayList<>(checkpoints));
        this.goals = Collections.unmodifiableList(new ArrayList<>(goals));
    }

    /** @return nombre visible del nivel */
    public String getName() { return name; }
    /** @return identificador de fondo o biome */
    public String getBackground() { return background; }
    /** @return tamaño base de tile usado en el archivo */
    public int getTileSize() { return tileSize; }
    /** @return plataformas sólidas normales */
    public List<Platform> getPlatforms() { return platforms; }
    /** @return plataformas especiales */
    public List<Platform> getSpecialPlatforms() { return specialPlatforms; }
    /** @return cajas declaradas en el nivel */
    public List<Box> getBoxes() { return boxes; }
    /** @return copia defensiva de los puntos de spawn */
    public List<double[]> getSpawnPoints() { return copyPoints(spawnPoints); }
    /** @return hazards del nivel */
    public List<Platform> getHazards() { return hazards; }
    /** @return checkpoints del nivel */
    public List<Platform> getCheckpoints() { return checkpoints; }
    /** @return metas de salida del nivel */
    public List<Platform> getGoals() { return goals; }

    private static List<double[]> copyPoints(List<double[]> points) {
        List<double[]> copy = new ArrayList<>();
        for (double[] point : points) {
            copy.add(new double[]{point[0], point[1]});
        }
        return copy;
    }
}
