package fr.heraliz.crates.client.render.scene;

/**
 * Textures d'effets lumineux (additifs) : assets/heralizcrates/textures/fx/&lt;nom&gt;.png
 */
public enum Sprite {
    HALO("halo"),
    FLARE("flare"),
    BEAM("beam"),
    RAY("ray"),
    RING("ring"),
    RUNE("rune_circle"),
    CHEVRON("chevron"),
    SMOKE("smoke"),
    FLAME("flame"),
    SWIRL("swirl"),
    SHOCK("shock"),
    GLINT("glint");

    public final String file;

    Sprite(String file) {
        this.file = file;
    }

    public String path() {
        return "textures/fx/" + file + ".png";
    }
}
