package com.dino.presentation.render;

import javafx.scene.paint.Color;

/**
 * Paletas y estilos base para el render pixel art del juego.
 *
 * <p>Concentra colores compartidos de HUD y mundo, así como la resolución de
 * paletas por biome y skins de jugador. Mantener esta información en un único
 * punto evita que el renderer disperse constantes visuales por toda la capa de
 * presentación.</p>
 */
public final class PixelArtTheme {
    public static final String PRIDE = "pride";
    public static final int BASE_PIXEL = 4;
    public static final Color HUD_BG = Color.web("#1c1a22");
    public static final Color HUD_PANEL = Color.web("#26232d");
    public static final Color HUD_BORDER = Color.web("#4d4659");
    public static final Color HUD_TEXT = Color.web("#e8dfcf");
    public static final Color HUD_MUTED = Color.web("#b8ad9a");
    public static final Color HUD_ACCENT = Color.web("#d1a35b");
    public static final Color GRID = Color.web("#201d28");
    public static final Color WORLD_BORDER = Color.web("#5b5368");
    public static final Color INK = Color.web("#17141d");

    private static final Color[] PLAYER_PRIMARY = {
        Color.web("#c96b5c"),
        Color.web("#5d8f9b"),
        Color.web("#7c9c63"),
        Color.web("#b78d57")
    };

    private static final Color[] PLAYER_SECONDARY = {
        Color.web("#f0d2b6"),
        Color.web("#d7e3d0"),
        Color.web("#d9cdb2"),
        Color.web("#f0ddad")
    };

    private static final Color[] PRIDE_STRIPES = {
        Color.web("#e40303"),
        Color.web("#ff8c00"),
        Color.web("#ffed00"),
        Color.web("#008026"),
        Color.web("#004dff"),
        Color.web("#750787")
    };

    /** Clase utilitaria: no debe instanciarse. */
    private PixelArtTheme() {}

    /**
     * Retorna la paleta de fondo y tiles asociada a un biome.
     *
     * @param biomeName nombre lógico del biome o fondo
     * @return paleta visual correspondiente; por defecto usa la estándar
     */
    public static Palette paletteFor(String biomeName) {
        if (biomeName == null) return defaultPalette();
        return switch (biomeName.toLowerCase()) {
            case "forest" -> new Palette(
                Color.web("#1d2b27"),
                Color.web("#28382f"),
                Color.web("#324737"),
                Color.web("#516142"),
                Color.web("#647653"),
                Color.web("#7b8c65"),
                Color.web("#4a3c2e"),
                Color.web("#70583b"),
                Color.web("#8b3f32"),
                Color.web("#d7b86a"),
                Color.web("#9dbb7a"),
                Color.web("#6b8f7a")
            );
            case "desert" -> new Palette(
                Color.web("#2d241b"),
                Color.web("#3a2d20"),
                Color.web("#4b3928"),
                Color.web("#79603d"),
                Color.web("#93754c"),
                Color.web("#b59665"),
                Color.web("#5e4330"),
                Color.web("#876345"),
                Color.web("#9a4b36"),
                Color.web("#d8be7a"),
                Color.web("#cbb48a"),
                Color.web("#8e8f74")
            );
            case "cave" -> new Palette(
                Color.web("#181821"),
                Color.web("#212131"),
                Color.web("#2d2d40"),
                Color.web("#4a4863"),
                Color.web("#625f7b"),
                Color.web("#7a7592"),
                Color.web("#3a3340"),
                Color.web("#5a4d54"),
                Color.web("#8c4f58"),
                Color.web("#c9b46d"),
                Color.web("#9aa1c1"),
                Color.web("#6e7698")
            );
            case "snow" -> new Palette(
                Color.web("#1d2733"),
                Color.web("#263545"),
                Color.web("#355063"),
                Color.web("#6a8192"),
                Color.web("#91a7b4"),
                Color.web("#bccbd3"),
                Color.web("#556370"),
                Color.web("#7d8d97"),
                Color.web("#8d5454"),
                Color.web("#d8c788"),
                Color.web("#d7e2e8"),
                Color.web("#7f9fb2")
            );
            default -> defaultPalette();
        };
    }

    /**
     * Retorna un color primario rotado por índice para jugadores sin skin explícita.
     *
     * @param index índice del jugador dentro del orden de render
     * @return color base recomendado
     */
    public static Color playerPrimary(int index) {
        return PLAYER_PRIMARY[Math.floorMod(index, PLAYER_PRIMARY.length)];
    }

    /**
     * Retorna un color secundario rotado por índice para jugadores sin skin explícita.
     *
     * @param index índice del jugador dentro del orden de render
     * @return color secundario recomendado
     */
    public static Color playerSecondary(int index) {
        return PLAYER_SECONDARY[Math.floorMod(index, PLAYER_SECONDARY.length)];
    }

    /**
     * Indica si una skin textual debe renderizarse con la bandera pride.
     *
     * @param colorName nombre lógico de color o skin
     * @return {@code true} cuando corresponde la skin multicolor
     */
    public static boolean usesPrideFlag(String colorName) {
        return PRIDE.equalsIgnoreCase(colorName);
    }

    /**
     * Resuelve el color primario visible de un jugador.
     *
     * @param colorName skin textual declarada por la sesión
     * @param index índice de fallback para rotación de paleta
     * @return color primario final
     */
    public static Color resolvePlayerPrimary(String colorName, int index) {
        if (colorName == null) return playerPrimary(index);
        return switch (colorName.toLowerCase()) {
            case "red" -> Color.web("#c96b5c");
            case "blue" -> Color.web("#5d8f9b");
            case "green" -> Color.web("#7c9c63");
            case "yellow" -> Color.web("#b78d57");
            case PRIDE -> PRIDE_STRIPES[0];
            default -> playerPrimary(index);
        };
    }

    /**
     * Resuelve el color secundario visible de un jugador.
     *
     * @param colorName skin textual declarada por la sesión
     * @param index índice de fallback para rotación de paleta
     * @return color secundario final
     */
    public static Color resolvePlayerSecondary(String colorName, int index) {
        if (colorName == null) return playerSecondary(index);
        return switch (colorName.toLowerCase()) {
            case "red" -> Color.web("#f0d2b6");
            case "blue" -> Color.web("#d7e3d0");
            case "green" -> Color.web("#d9cdb2");
            case "yellow" -> Color.web("#f0ddad");
            case PRIDE -> Color.web("#fff4d6");
            default -> playerSecondary(index);
        };
    }

    /**
     * Retorna una franja de la bandera pride según índice circular.
     *
     * @param index índice de la franja solicitada
     * @return color de la franja correspondiente
     */
    public static Color prideStripe(int index) {
        return PRIDE_STRIPES[Math.floorMod(index, PRIDE_STRIPES.length)];
    }

    private static Palette defaultPalette() {
        return new Palette(
            Color.web("#1e2029"),
            Color.web("#262935"),
            Color.web("#313646"),
            Color.web("#4b5364"),
            Color.web("#677186"),
            Color.web("#818aa1"),
            Color.web("#4d4036"),
            Color.web("#6f5947"),
            Color.web("#8a4d46"),
            Color.web("#ccb56c"),
            Color.web("#adb8a0"),
            Color.web("#7f8aa2")
        );
    }

    /**
     * Paleta completa usada por el renderer para un biome concreto.
     *
     * @param backgroundFar color del plano de fondo más lejano
     * @param backgroundMid color del plano intermedio de fondo
     * @param backgroundNear color del plano cercano de fondo
     * @param platformTop color del borde superior de plataformas normales
     * @param platformFace color principal del cuerpo de plataformas normales
     * @param platformDetail color auxiliar de textura para plataformas
     * @param crateDark tono oscuro para cajas y bloques
     * @param crateLight tono claro para cajas y bloques
     * @param hazard color principal de hazards
     * @param goal color principal de la meta
     * @param checkpoint color principal de checkpoints
     * @param special color principal de plataformas especiales
     */
    public record Palette(
        Color backgroundFar,
        Color backgroundMid,
        Color backgroundNear,
        Color platformTop,
        Color platformFace,
        Color platformDetail,
        Color crateDark,
        Color crateLight,
        Color hazard,
        Color goal,
        Color checkpoint,
        Color special
    ) {}
}
