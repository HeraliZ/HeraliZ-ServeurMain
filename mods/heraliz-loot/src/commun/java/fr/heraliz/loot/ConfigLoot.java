package fr.heraliz.loot;

import java.util.List;
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
    /** Conteneurs de Minecraft scellés (src/serveur : ConteneursVanilla). */
    public static final ForgeConfigSpec.BooleanValue VANILLA_ACTIF, VANILLA_AUTO, VANILLA_ENDER, VANILLA_ENTITES, VANILLA_CLAIMS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> VANILLA_BLOQUES, VANILLA_EXCEPTIONS;

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
        b.comment("Conteneurs de Minecraft (coffre, baril, four, alambic, entonnoir...) : un joueur en survie ou en aventure ne",
                "peut pas les ouvrir (« Ce conteneur est vide et scellé. »). Créatif, spectateur et staff (permission 2) : libres.",
                "Seulement les blocs et entités de Minecraft (namespace minecraft) : jamais ceux de nos mods ni de cfm.",
                "Postes de travail sans stockage (établi, enclume, meule, métier, tailleur de pierre...) et pupitre : permis.")
                .push("conteneurs_vanilla");
        VANILLA_ACTIF = b.comment("Sceller les conteneurs de Minecraft.").define("actif", true);
        VANILLA_BLOQUES = b.comment("Blocs scellés : identifiants (minecraft:chest) ou tags (#minecraft:shulker_boxes).")
                .defineListAllowEmpty(List.of("bloques"), () -> List.of("minecraft:chest", "minecraft:trapped_chest",
                        "minecraft:barrel", "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker",
                        "minecraft:brewing_stand", "minecraft:hopper", "minecraft:dispenser", "minecraft:dropper",
                        "#minecraft:shulker_boxes", "minecraft:jukebox", "minecraft:chiseled_bookshelf"), ConfigLoot::idValide);
        VANILLA_AUTO = b.comment("Sceller aussi tout autre bloc de Minecraft dont l'entité de bloc garde des objets (inventaire).")
                .define("inventaires_auto", true);
        VANILLA_EXCEPTIONS = b.comment("Blocs toujours permis, même s'ils sont dans la liste ou ont un inventaire (identifiants ou tags).")
                .defineListAllowEmpty(List.of("exceptions"), () -> List.of("minecraft:lectern", "minecraft:campfire",
                        "minecraft:soul_campfire"), ConfigLoot::idValide);
        VANILLA_ENDER = b.comment("Sceller aussi le coffre de l'Ender (false : il reste permis, c'est l'inventaire personnel du joueur).")
                .define("coffre_ender", false);
        VANILLA_CLAIMS = b.comment("Laisser ouvrir les blocs posés dans un claim de CRac (rangements des bases des joueurs ;",
                "CRac décide ensuite qui peut les ouvrir). false : scellés là aussi.").define("permis_claims_crac", true);
        VANILLA_ENTITES = b.comment("Sceller aussi les wagonnets à coffre et à entonnoir, et le coffre des bateaux à coffre",
                "(on peut toujours monter dans le bateau).").define("entites", true);
        b.pop();
        SPEC = b.build();
    }

    private static boolean idValide(Object o) {
        return o instanceof String t && !t.isBlank();
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
