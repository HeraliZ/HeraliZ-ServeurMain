package fr.heralisz.utils;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages serveur de l'objet « Bonus de loot » (fichier {@code heralisz_utils-server.toml}, dans {@code serverconfig/}
 * du monde). L'effet (objets plus rares, quantités) est appliqué par HeraliZ Loot à l'ouverture des conteneurs.
 */
public final class BonusLootConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue POURCENT;
    public static final ForgeConfigSpec.IntValue MINUTES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Objet « Bonus de loot » (boutique) : clic droit = bonus de loot de HeraliZ Loot pendant un temps de jeu.").push("bonus_loot");
        POURCENT = b.comment("Bonus (%) : chances d'objets plus rares et quantités (munitions, soins...) à l'ouverture des conteneurs.")
                .defineInRange("pourcent", 10, 1, 100);
        MINUTES = b.comment("Durée (minutes de jeu : décomptées seulement quand le joueur est connecté). Un deuxième objet ajoute sa durée.")
                .defineInRange("minutes", 120, 1, 10080);
        b.pop();
        SPEC = b.build();
    }

    private BonusLootConfig() {
    }

    public static int pourcent() {
        return SPEC.isLoaded() ? POURCENT.get() : 10;
    }

    public static int minutes() {
        return SPEC.isLoaded() ? MINUTES.get() : 120;
    }
}
