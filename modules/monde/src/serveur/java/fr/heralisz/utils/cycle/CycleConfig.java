package fr.heralisz.utils.cycle;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages du cycle jour / nuit de la carte : heraliz_cycle-server.toml (config du serveur, relue seule quand le fichier
 * change ; voir {@link CycleServeur}). La pause (/cycle pause) n'est pas ici : elle est gardée avec le monde.
 */
public final class CycleConfig {
    private CycleConfig() {
    }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ACTIF;
    public static final ForgeConfigSpec.IntValue JOUR_MINUTES, NUIT_MINUTES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("cycle");
        ACTIF = b.comment("Faire avancer l'heure de la carte (minecraft:overworld) : jour puis nuit, en boucle.",
                "La règle doDaylightCycle est gardée à false (remise à false au démarrage et chaque minute) : seul ce cycle",
                "écrit l'heure. false : l'heure reste comme elle est (ce mod n'y touche plus).").define("actif", true);
        JOUR_MINUTES = b.comment("Durée du jour, en minutes réelles (du lever au coucher du soleil).")
                .defineInRange("jour_minutes", 20, 1, 600);
        NUIT_MINUTES = b.comment("Durée de la nuit, en minutes réelles (du coucher au lever du soleil).")
                .defineInRange("nuit_minutes", 4, 1, 600);
        b.pop();
        SPEC = b.build();
    }
}
