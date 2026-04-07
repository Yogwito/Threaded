package com.dino.application.levels;

/**
 * Catálogo de tiles soportados por los archivos de nivel.
 *
 * <p>Cada valor mantiene el código entero que aparece en la matriz textual del
 * recurso. El cargador usa este enum para traducir números crudos a semántica
 * de juego.</p>
 */
public enum TileType {
    EMPTY(0),
    SOLID_PLATFORM(1),
    BOX(2),
    PLAYER_SPAWN(3),
    SPECIAL_PLATFORM(4),
    HAZARD(5),
    CHECKPOINT(6),
    GOAL(7);

    private final int code;

    /**
     * Crea el tipo de tile asociado a un código entero.
     *
     * @param code valor leído desde la matriz del nivel
     */
    TileType(int code) {
        this.code = code;
    }

    /**
     * Retorna el código entero persistido en los archivos de nivel.
     *
     * @return valor numérico del tile
     */
    public int getCode() {
        return code;
    }

    /**
     * Resuelve el tipo de tile correspondiente a un código textual.
     *
     * @param code código leído desde la matriz del nivel
     * @return tipo de tile equivalente
     * @throws IllegalArgumentException si el código no pertenece al catálogo
     */
    public static TileType fromCode(int code) {
        for (TileType type : values()) {
            if (type.code == code) return type;
        }
        throw new IllegalArgumentException("Tile desconocido: " + code);
    }
}
