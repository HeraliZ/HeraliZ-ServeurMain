package fr.heralisz.utils;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages serveur de HeraliZ Utils (fichier {@code heralisz_utils-server.toml}, dans {@code serverconfig/} du monde) :
 * objet « Bonus de loot » (l'effet, objets plus rares et quantités, est appliqué par HeraliZ Loot à l'ouverture des
 * conteneurs) et, depuis le 02/11 (audit mods-2 §21), délai de {@code /suicide} (avant écrit dans le code).
 */
public final class BonusLootConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue POURCENT;
    public static final ForgeConfigSpec.IntValue MINUTES;
    public static final ForgeConfigSpec.BooleanValue CARTE_SEULEMENT;
    public static final ForgeConfigSpec.IntValue AFK_MINUTES;
    public static final ForgeConfigSpec.IntValue SUICIDE_MINUTES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Objet « Bonus de loot » (boutique) : clic droit = bonus de loot de HeraliZ Loot pendant un temps de jeu.").push("bonus_loot");
        POURCENT = b.comment("Bonus (%) : chances d'objets plus rares et quantités (munitions, soins...) à l'ouverture des conteneurs.")
                .defineInRange("pourcent", 10, 1, 100);
        MINUTES = b.comment("Durée (minutes de jeu : décomptées seulement quand le joueur est connecté, et selon les deux réglages",
                        "ci-dessous). Un deuxième objet ajoute sa durée.")
                .defineInRange("minutes", 120, 1, 10080);
        CARTE_SEULEMENT = b.comment("02/11 (choix du propriétaire) : true = le temps ne s'use que sur la carte (ni au spawn, ni dans le",
                        "tutoriel, ni en match) ; false = partout où le joueur est connecté (avant).")
                .define("carte_seulement", true);
        AFK_MINUTES = b.comment("02/11 (choix du propriétaire) : le temps ne s'use plus pour un joueur qui n'a ni bougé ni tourné la tête",
                        "depuis ce nombre de minutes (AFK) ; il repart dès qu'il bouge. 0 : pas de contrôle (avant).")
                .defineInRange("afk_minutes", 3, 0, 60);
        b.pop();
        b.comment("/suicide : délai entre deux suicides d'un même joueur (temps réel ; le staff niveau 2 n'en a pas).").push("suicide");
        SUICIDE_MINUTES = b.comment("Minutes (0 : aucun délai).").defineInRange("delai_minutes", 5, 0, 1440);
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

    /** Le temps du bonus ne s'use que sur la carte. */
    public static boolean carteSeulement() {
        return !SPEC.isLoaded() || CARTE_SEULEMENT.get();
    }

    /** Minutes sans bouger après lesquelles le temps du bonus ne s'use plus (0 : jamais). */
    public static int afkMinutes() {
        return SPEC.isLoaded() ? AFK_MINUTES.get() : 3;
    }

    /** Délai entre deux /suicide, en minutes. */
    public static int suicideMinutes() {
        return SPEC.isLoaded() ? SUICIDE_MINUTES.get() : 5;
    }
}
