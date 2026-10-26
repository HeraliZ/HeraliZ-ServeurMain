package fr.heralisz.utils.ambiance;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages client de l'ambiance (config/heralisz_utils-ambiance.toml). Les nuits sombres, le ciel et le brouillard ne
 * se désactivent pas (même nuit pour tous les joueurs) ; seul le filtre d'image, purement esthétique, peut être coupé
 * sur un PC très faible.
 */
public final class AmbianceConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue FILTRE;
    private static final ForgeConfigSpec.BooleanValue FILANTES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Ambiance de la map HeraliZ (côté client)").push("ambiance");
        FILTRE = b.comment("Filtre d'image : étalonnage des couleurs, vignette et grain de pellicule (coût très faible).",
                        "Le désactiver ne rend pas les nuits plus claires : seul l'aspect « film » disparaît.")
                .define("filtre_image", true);
        FILANTES = b.comment("Étoiles filantes les nuits dégagées.").define("etoiles_filantes", true);
        b.pop();
        SPEC = b.build();
    }

    private AmbianceConfig() {}

    public static boolean filtre() {
        try {
            return FILTRE.get();
        } catch (IllegalStateException e) {                         // configuration pas encore chargée
            return true;
        }
    }

    public static boolean filantes() {
        try {
            return FILANTES.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
