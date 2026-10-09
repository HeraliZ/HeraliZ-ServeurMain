package fr.heraliz.loot;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages serveur de la rotation du loot (fichier {@code heraliz_loot-server.toml}, dans {@code serverconfig/} du
 * monde). Relus à chaud quand le fichier change : les zones sont alors recalculées.
 */
public final class ConfigLoot {
    private ConfigLoot() {}

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue PROPORTION;
    public static final ForgeConfigSpec.IntValue DELAI_MIN, DELAI_MAX, RAYON_ETOILES, ETOILES_PAR_SECONDE, BONUS_MAX;
    public static final ForgeConfigSpec.BooleanValue ETOILES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Rotation du loot HeraliZ : dans chaque zone, une partie des conteneurs seulement contient du loot.").push("rotation");
        PROPORTION = b.comment("Part des conteneurs d'une zone qui sont actifs (0.2 = 1 sur 5), si la zone ne précise pas \"actifs\".")
                .defineInRange("proportion_actifs", 0.2, 0.0, 1.0);
        DELAI_MIN = b.comment("Délai minimal (secondes) avant qu'un autre conteneur de la zone se remplisse quand un conteneur a été vidé.")
                .defineInRange("delai_min_secondes", 600, 0, 86400);
        DELAI_MAX = b.comment("Délai maximal (secondes). Le délai est tiré au hasard entre le minimum et le maximum.")
                .defineInRange("delai_max_secondes", 1200, 0, 86400);
        b.pop();
        b.comment("Étoiles blanches qui scintillent autour des conteneurs actifs.").push("etoiles");
        ETOILES = b.comment("Montrer les étoiles autour des conteneurs actifs.").define("actives", true);
        RAYON_ETOILES = b.comment("Distance (blocs) jusqu'à laquelle un joueur voit les étoiles.").defineInRange("rayon", 24, 4, 64);
        ETOILES_PAR_SECONDE = b.comment("Étoiles envoyées par seconde et par conteneur.").defineInRange("par_seconde", 2, 1, 8);
        b.pop();
        b.comment("Bonus de loot (zone tenue par son clan...) : au premier tirage d'un conteneur, chaque objet a ce pourcentage",
                "de chances d'être tiré une seconde fois (le plus rare des deux est gardé) et les quantités montent d'autant.").push("bonus");
        BONUS_MAX = b.comment("Bonus maximal (%) quand plusieurs bonus s'additionnent.").defineInRange("max", 25, 0, 100);
        b.pop();
        SPEC = b.build();
    }

    /** Proportion par défaut (repli sur 0.2 si la configuration n'est pas encore chargée). */
    static double proportion() {
        return SPEC.isLoaded() ? PROPORTION.get() : 0.2;
    }

    /** Délai de réapparition tiré entre min et max, en ticks. */
    static long delaiTicks(net.minecraft.util.RandomSource r) {
        int min = SPEC.isLoaded() ? DELAI_MIN.get() : 600, max = SPEC.isLoaded() ? DELAI_MAX.get() : 1200;
        if (max < min) max = min;
        return 20L * (min + (max > min ? r.nextInt(max - min + 1) : 0));
    }

    static boolean etoiles() {
        return !SPEC.isLoaded() || ETOILES.get();
    }

    static int rayonEtoiles() {
        return SPEC.isLoaded() ? RAYON_ETOILES.get() : 24;
    }

    static int bonusMax() {
        return SPEC.isLoaded() ? BONUS_MAX.get() : 25;
    }

    static int etoilesParSeconde() {
        return SPEC.isLoaded() ? ETOILES_PAR_SECONDE.get() : 2;
    }
}
