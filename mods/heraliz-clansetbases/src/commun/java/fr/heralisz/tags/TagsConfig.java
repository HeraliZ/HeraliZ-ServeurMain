package fr.heralisz.tags;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Reglages communs (config/heralisz_tags-common.toml) : duree des tags, cadence, portee, plafonds.
 * Toutes les regles sont verifiees cote serveur ; le client ne fait que proposer.
 */
public final class TagsConfig {
    private TagsConfig() {
    }

    public static final ForgeConfigSpec SPEC;
    /** Duree de vie d'un tag pose (secondes). */
    public static final ForgeConfigSpec.IntValue DUREE_S;
    /** Delai minimal entre deux tags d'un meme joueur (secondes). */
    public static final ForgeConfigSpec.IntValue CADENCE_S;
    /** Portee de pose depuis les yeux (blocs) ; le serveur tolere +1. */
    public static final ForgeConfigSpec.DoubleValue PORTEE;
    /** Nombre maximal de tags actifs sur le serveur (le plus ancien est retire). */
    public static final ForgeConfigSpec.IntValue MAX_ACTIFS;
    /** Nombre maximal de tags actifs par joueur. */
    public static final ForgeConfigSpec.IntValue MAX_PAR_JOUEUR;
    /** Distance de diffusion d'un tag pose aux autres joueurs (blocs). */
    public static final ForgeConfigSpec.DoubleValue DISTANCE_DIFFUSION;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Tags (sprays) HeraliZ").push("tags");
        DUREE_S = b.comment("Duree d'un tag pose, en secondes").defineInRange("duree_s", 30, 5, 600);
        CADENCE_S = b.comment("Delai minimal entre deux tags d'un meme joueur, en secondes").defineInRange("cadence_s", 3, 0, 120);
        PORTEE = b.comment("Portee de pose depuis les yeux, en blocs").defineInRange("portee", 6.0, 1.0, 16.0);
        MAX_ACTIFS = b.comment("Nombre maximal de tags actifs sur le serveur (le plus ancien est remplace)").defineInRange("max_actifs", 300, 1, 2000);
        MAX_PAR_JOUEUR = b.comment("Nombre maximal de tags actifs par joueur").defineInRange("max_par_joueur", 3, 1, 50);
        DISTANCE_DIFFUSION = b.comment("Distance a laquelle un tag pose est envoye aux autres joueurs, en blocs").defineInRange("distance_diffusion", 96.0, 16.0, 512.0);
        b.pop();
        SPEC = b.build();
    }
}
