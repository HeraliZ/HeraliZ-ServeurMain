package fr.heraliz.carte;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages de la carte propres à chaque joueur (config/heraliz_carte-client.toml) : la vue choisie et les calques
 * affichés, retenus d'une partie à l'autre. Écrits à la fermeture de la carte.
 */
public final class CarteConfig {
    private CarteConfig() {
    }

    public static final ForgeConfigSpec CLIENT_SPEC;
    /** Vue topographique (relief) au lieu de la vue satellite. */
    public static final ForgeConfigSpec.BooleanValue RELIEF;
    public static final ForgeConfigSpec.BooleanValue TERRITOIRES, EXTRACTIONS, LIEUX, CLAN, PINGS, GRILLE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("HeraliZ Carte : vue et calques (changés depuis l'écran de la carte)").push("carte");
        RELIEF = b.comment("Vue topographique (relief) au lieu de la vue satellite").define("relief", false);
        TERRITOIRES = b.comment("Territoires des clans").define("territoires", true);
        EXTRACTIONS = b.comment("Zones d'extraction ouvertes").define("extractions", true);
        LIEUX = b.comment("Villes, villages et points d'intérêt").define("lieux", true);
        CLAN = b.comment("Membres de son clan").define("clan", true);
        PINGS = b.comment("Pings du clan").define("pings", true);
        GRILLE = b.comment("Grille de 500 m").define("grille", true);
        b.pop();
        CLIENT_SPEC = b.build();
    }
}
