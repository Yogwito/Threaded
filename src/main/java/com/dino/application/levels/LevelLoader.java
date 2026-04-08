package com.dino.application.levels;

import com.dino.config.GameConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Cargador de niveles basados en texto dentro de {@code resources}.
 *
 * <p>Lee archivos `.txt` con metadata opcional y una matriz de enteros,
 * valida su forma y los transforma en una instancia de {@link LevelData}. El
 * formato está pensado para que el contenido de campaña sea editable sin tocar
 * la lógica Java.</p>
 */
public final class LevelLoader {
    public static final String DEFAULT_LEVEL_PATTERN = "/com/dino/levels/level%d.txt";
    private static final int DEFAULT_TILE_SIZE = 64;

    /**
     * Evita instanciación; la clase expone solo utilidades estáticas.
     */
    private LevelLoader() {}

    /**
     * Carga un nivel numerado usando el patrón de recurso por defecto.
     *
     * @param levelNumber índice base 1 del nivel a cargar
     * @return estructura parseada del nivel
     */
    public static LevelData loadLevel(int levelNumber) {
        return loadResource(DEFAULT_LEVEL_PATTERN.formatted(levelNumber));
    }

    /**
     * Indica si existe un archivo de nivel con el número indicado.
     *
     * @param levelNumber índice base 1 del nivel
     * @return {@code true} si el recurso existe
     */
    public static boolean levelExists(int levelNumber) {
        return LevelLoader.class.getResourceAsStream(DEFAULT_LEVEL_PATTERN.formatted(levelNumber)) != null;
    }

    /**
     * Cuenta niveles consecutivos disponibles siguiendo el patrón por defecto.
     *
     * @return cantidad de niveles encontrados desde {@code level1.txt}
     */
    public static int countAvailableLevels() {
        int count = 0;
        while (levelExists(count + 1)) count++;
        return count;
    }

    /**
     * Carga un nivel a partir de una ruta explícita dentro de recursos.
     *
     * @param resourcePath ruta absoluta del recurso
     * @return nivel parseado
     * @throws IllegalArgumentException si el recurso no existe
     * @throws IllegalStateException si ocurre un error de lectura
     */
    public static LevelData loadResource(String resourcePath) {
        try (InputStream stream = LevelLoader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalArgumentException("No se encontro el nivel: " + resourcePath);
            }
            return parse(stream, resourcePath);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el nivel " + resourcePath, e);
        }
    }

    /**
     * Parsea metadata y matriz de tiles desde el flujo del recurso.
     */
    private static LevelData parse(InputStream stream, String resourcePath) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String name = defaultName(resourcePath);
        String background = "default";
        int tileSize = DEFAULT_TILE_SIZE;
        List<int[]> rows = new ArrayList<>();
        boolean readingMatrix = false;

        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            if (!readingMatrix && trimmed.contains("=")) {
                String[] parts = trimmed.split("=", 2);
                String key = parts[0].trim();
                String value = parts[1].trim();
                switch (key) {
                    case "name" -> name = value;
                    case "background" -> background = value;
                    case "tileSize" -> tileSize = Integer.parseInt(value);
                    default -> throw new IllegalArgumentException("Metadata no soportada: " + key);
                }
                continue;
            }

            readingMatrix = true;
            String[] cells = trimmed.split(",");
            int[] row = new int[cells.length];
            for (int i = 0; i < cells.length; i++) {
                row[i] = Integer.parseInt(cells[i].trim());
            }
            rows.add(row);
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("El nivel no contiene matriz: " + resourcePath);
        }

        int width = rows.get(0).length;
        for (int[] row : rows) {
            if (row.length != width) {
                throw new IllegalArgumentException("La matriz del nivel debe ser rectangular: " + resourcePath);
            }
        }

        List<Platform> platforms = new ArrayList<>();
        List<Platform> specialPlatforms = new ArrayList<>();
        List<Box> boxes = new ArrayList<>();
        List<double[]> spawnPoints = new ArrayList<>();
        List<Platform> hazards = new ArrayList<>();
        List<Platform> checkpoints = new ArrayList<>();
        List<Platform> goals = new ArrayList<>();
        List<Coin> coins = new ArrayList<>();

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            int[] row = rows.get(rowIndex);
            for (int colIndex = 0; colIndex < row.length; colIndex++) {
                TileType type = TileType.fromCode(row[colIndex]);
                double worldX = colIndex * (double) tileSize;
                double worldY = rowIndex * (double) tileSize;

                switch (type) {
                    case EMPTY -> {
                    }
                    case SOLID_PLATFORM -> platforms.add(new Platform(worldX, worldY, tileSize, tileSize));
                    case SPECIAL_PLATFORM -> specialPlatforms.add(new Platform(worldX, worldY, tileSize, tileSize));
                    case BOX -> boxes.add(new Box(worldX, worldY, tileSize, tileSize));
                    case PLAYER_SPAWN -> {
                        double spawnX = worldX + (tileSize - GameConfig.PLAYER_WIDTH) / 2.0;
                        double spawnY = worldY + tileSize - GameConfig.PLAYER_HEIGHT;
                        spawnPoints.add(new double[]{spawnX, spawnY});
                    }
                    case HAZARD -> hazards.add(new Platform(worldX, worldY, tileSize, tileSize));
                    case CHECKPOINT -> checkpoints.add(new Platform(worldX, worldY, tileSize, tileSize));
                    case GOAL -> goals.add(new Platform(worldX, worldY, tileSize, tileSize));
                    case COIN_SMALL -> coins.add(new Coin(worldX, worldY, GameConfig.SCORE_COIN_SMALL));
                    case COIN_LARGE -> coins.add(new Coin(worldX, worldY, GameConfig.SCORE_COIN_LARGE));
                }
            }
        }

        if (spawnPoints.isEmpty()) {
            throw new IllegalArgumentException("El nivel debe tener al menos un spawn: " + resourcePath);
        }
        if (goals.isEmpty()) {
            throw new IllegalArgumentException("El nivel debe tener al menos una meta: " + resourcePath);
        }

        return new LevelData(
            name,
            background,
            tileSize,
            platforms,
            specialPlatforms,
            boxes,
            spawnPoints,
            hazards,
            checkpoints,
            goals,
            coins
        );
    }

    /**
     * Deriva un nombre legible del archivo cuando el nivel no trae metadata.
     */
    private static String defaultName(String resourcePath) {
        String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }
}
