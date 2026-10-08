package fr.heralisz.bases.pillage;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Reglages du module pillage et blindage : {@code config/heralisz_bases-pillage.toml} (COMMON). Les heures de la fenetre,
 * l'heure de remise a zero et le nombre de pillards max sont dans la config du coeur ({@code heralisz_bases-common.toml}).
 * La table des niveaux et les coups par source d'explosion sont dans {@code protection.json} (voir blindage.Protection).
 */
public final class PillageConfig {
    private PillageConfig() {
    }

    public static final String FICHIER = "heralisz_bases-pillage.toml";
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue RETOUR_APRES_SORTIE;
    public static final ForgeConfigSpec.IntValue AVERTISSEMENT_S;
    public static final ForgeConfigSpec.BooleanValue BLINDAGE_PARTOUT;
    public static final ForgeConfigSpec.IntValue C4_MAINTIEN_MIN;
    public static final ForgeConfigSpec.IntValue FISSURES_RAFRAICHIR_S;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("pillage");
        RETOUR_APRES_SORTIE = b.comment("Un pillard sorti VIVANT peut revenir le meme jour dans la MEME base (jamais dans une autre ; "
                + "jamais apres une mort)").define("retour_apres_sortie", true);
        AVERTISSEMENT_S = b.comment("Secondes avant la fin de la fenetre ou les pillards sont prevenus qu'ils vont etre renvoyes")
                .defineInRange("avertissement_s", 120, 10, 1800);
        b.pop();
        b.push("blindage");
        BLINDAGE_PARTOUT = b.comment("Les blocs heralisz_bases:blindage_1..8 valent PARTOUT (N C4 pour les casser, insensibles aux "
                + "autres explosions). false : les AUTRES blocs de la table (cfm, portes HBM) ne sont proteges que dans la dimension des "
                + "bases (la carte n'est jamais touchee). true : toute la table dans toutes les dimensions").define("blindage_partout", false);
        C4_MAINTIEN_MIN = b.comment("Un C4 pose dans une base garde son chunk charge (il ne disparait pas au dechargement) tant que la "
                + "fenetre de pillage est ouverte ou qu'un joueur est dans la base, au plus ce nombre de minutes")
                .defineInRange("c4_maintien_min", 120, 1, 1440);
        FISSURES_RAFRAICHIR_S = b.comment("Periode de renvoi des fissures aux joueurs proches (le client les oublie apres 20 s)")
                .defineInRange("fissures_rafraichir_s", 10, 2, 19);
        b.pop();
        SPEC = b.build();
    }
}
