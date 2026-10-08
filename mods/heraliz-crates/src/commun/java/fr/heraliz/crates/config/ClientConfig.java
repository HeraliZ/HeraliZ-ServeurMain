package fr.heraliz.crates.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Reglages visuels cote client (config/heralizcrates-client.toml).
 */
public final class ClientConfig {
    private ClientConfig() {
    }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue BEAMS;
    public static final ForgeConfigSpec.BooleanValue RUNE_CIRCLE;
    public static final ForgeConfigSpec.BooleanValue HOLOGRAM_TEXT;
    public static final ForgeConfigSpec.DoubleValue PARTICLES;
    public static final ForgeConfigSpec.BooleanValue STATIC_VBO;
    public static final ForgeConfigSpec.BooleanValue CAMERA_SHAKE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Effets visuels des coffres Heraliz").push("visuals");
        BEAMS = b.comment("Faisceau de lumiere vertical pendant une ouverture").define("beams", true);
        RUNE_CIRCLE = b.comment("Cercle runique lumineux au sol pendant une ouverture").define("runeCircle", true);
        HOLOGRAM_TEXT = b.comment("Nom du coffre affiche au-dessus (de pres)").define("hologramText", true);
        PARTICLES = b.comment("Densite des particules (0 = aucune, 1 = normal, 2 = beaucoup)").defineInRange("particles", 1.0, 0.0, 2.0);
        CAMERA_SHAKE = b.comment("La camera tremble aux impacts quand une caisse s'ouvre pres de vous").define("cameraShake", true);
        b.pop();
        b.comment("Performances").push("performance");
        STATIC_VBO = b.comment("Caisses (au repos et pendant l'ouverture) envoyees une seule fois a la carte graphique (tres rapide).",
                "Desactive automatiquement avec un pack de shaders. Mettre false en cas de souci d'affichage.").define("staticGpuBuffers", true);
        b.pop();
        SPEC = b.build();
    }
}
