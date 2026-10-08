package fr.heraliz.casino.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Reglages visuels cote client (config/heralizcasino-client.toml).
 */
public final class ClientConfig {
    private ClientConfig() {
    }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue PARTICLES;
    public static final ForgeConfigSpec.BooleanValue STATIC_VBO;
    public static final ForgeConfigSpec.BooleanValue WIN_OVERLAY;
    public static final ForgeConfigSpec.DoubleValue MACHINE_VOLUME;
    public static final ForgeConfigSpec.DoubleValue MUSIC_VOLUME;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Effets visuels du casino Heraliz").push("visuals");
        PARTICLES = b.comment("Densite des particules (0 = aucune, 1 = normal, 2 = beaucoup)").defineInRange("particles", 1.0, 0.0, 2.0);
        WIN_OVERLAY = b.comment("Grand bandeau anime a l'ecran quand vous gagnez").define("winOverlay", true);
        MACHINE_VOLUME = b.comment("Volume des sons des machines (multiplicateur)").defineInRange("machineVolume", 1.0, 0.0, 2.0);
        MUSIC_VOLUME = b.comment("Volume de la musique d'ambiance des machines a sous (0 = coupee)").defineInRange("slotMusicVolume", 1.0, 0.0, 2.0);
        b.pop();
        b.comment("Performances").push("performance");
        STATIC_VBO = b.comment("Carrosserie des machines envoyee une seule fois a la carte graphique (tres rapide).",
                "Desactive automatiquement avec un pack de shaders. Mettre false en cas de souci d'affichage.").define("staticGpuBuffers", true);
        b.pop();
        SPEC = b.build();
    }
}
