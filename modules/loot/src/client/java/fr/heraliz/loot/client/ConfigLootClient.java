package fr.heraliz.loot.client;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages du joueur pour HeraliZ Loot (fichier {@code config/heraliz_loot-client.toml} de son jeu).
 */
public final class ConfigLootClient {
    private ConfigLootClient() {
    }

    public static final ForgeConfigSpec SPEC;
    /** Conteneurs T1-T6 allégés (faces cachées retirées, maillage simplifié : environ un cinquième de faces en moins). */
    public static final ForgeConfigSpec.BooleanValue ALLEGES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("rendu");
        ALLEGES = b.comment("Conteneurs de loot allégés (faces cachées retirées, maillage simplifié) : plus d'images par seconde.",
                        "false : modèles complets (en cas de souci d'affichage). Pris en compte au rechargement des ressources (F3 + T).")
                .define("conteneurs_alleges", true);
        b.pop();
        SPEC = b.build();
    }

    /** Valeur du réglage, vrai si la config n'est pas encore lue (jamais d'exception dans le rendu du terrain). */
    public static boolean alleges() {
        try {
            return ALLEGES.get();
        } catch (RuntimeException e) {
            return true;
        }
    }
}
