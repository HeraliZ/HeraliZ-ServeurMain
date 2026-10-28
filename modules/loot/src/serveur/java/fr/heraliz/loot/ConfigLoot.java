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
    /** Facteur de la part d'actifs par niveau de zone (T1 à T7), écrit par outils/loot_armes.py. */
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> FACTEURS;
    public static final ForgeConfigSpec.IntValue DELAI_MIN, DELAI_MAX, RESTES, RAYON_ETOILES, ETOILES_PAR_SECONDE, BONUS_MAX;
    public static final ForgeConfigSpec.BooleanValue ETOILES;
    /** Conteneurs de Minecraft scellés (src/serveur : ConteneursVanilla). */
    public static final ForgeConfigSpec.BooleanValue VANILLA_ACTIF, VANILLA_AUTO, VANILLA_ENTITES, VANILLA_CLAIMS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> VANILLA_BLOQUES, VANILLA_EXCEPTIONS;
    /** Postes de travail et blocs à usage de Minecraft hors service (src/serveur : ConteneursVanilla). */
    public static final ForgeConfigSpec.BooleanValue POSTES_ACTIF, POSTES_MENUS_AUTO, POSTES_CLAIMS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> POSTES_BLOQUES, POSTES_EXCEPTIONS;
    /** Présentoirs protégés : cadres, porte-armures, mannequins, peintures (src/serveur : Presentoirs). */
    public static final ForgeConfigSpec.BooleanValue PRES_ACTIF, PRES_AUTO, PRES_POSE, PRES_CLAIMS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PRES_ENTITES, PRES_EXCEPTIONS, PRES_OBJETS;
    /** Autres façons d'abîmer la carte : seaux, feu, outils, engrais, panneaux (src/serveur : Nuisances). */
    public static final ForgeConfigSpec.BooleanValue NUIS_ACTIF, NUIS_SEAUX, NUIS_FEU, NUIS_OUTILS, NUIS_ENGRAIS, NUIS_PANNEAUX,
            NUIS_CLAIMS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Rotation du loot HeraliZ : dans chaque zone, une partie des conteneurs seulement contient du loot.").push("rotation");
        PROPORTION = b.comment("Part des conteneurs d'une zone qui sont actifs (0.2 = 1 sur 5), si la zone ne précise pas \"actifs\".")
                .defineInRange("proportion_actifs", 0.2, 0.0, 1.0);
        FACTEURS = b.comment("Facteur de la part d'actifs par niveau de zone, T1 à T7 (23/10 : plusieurs objets par conteneur,",
                        "donc moins de conteneurs remplis à la fois ; une part réglée en jeu avec /heralizloot zone <id> part n'est",
                        "pas touchée). Valeurs par défaut écrites par outils/loot_armes.py.")
                .defineList("facteurs_actifs",
                        // facteurs_actifs (outils/loot_armes.py)
                        List.of(0.932, 0.746, 0.802, 0.834, 0.714, 0.546, 0.129),
                        o -> o instanceof Number n && n.doubleValue() >= 0 && n.doubleValue() <= 1);
        DELAI_MIN = b.comment("Délai minimal (secondes) avant qu'un autre conteneur de la zone se remplisse quand un conteneur a été vidé.")
                .defineInRange("delai_min_secondes", 240, 0, 86400);
        DELAI_MAX = b.comment("Délai maximal (secondes). Le délai est tiré au hasard entre le minimum et le maximum.")
                .defineInRange("delai_max_secondes", 360, 0, 86400);
        RESTES = b.comment("Conteneur fouillé puis laissé avec des objets (le joueur n'a pris que ce qui l'intéressait) : au bout",
                        "de ce délai (secondes) sans que personne n'y regarde, il est vidé comme un conteneur vide et un autre",
                        "conteneur de la zone se remplit après le délai ci-dessus. 0 : jamais (il garde ses restes).")
                .defineInRange("restes_secondes", 300, 0, 86400);
        b.pop();
        b.comment("Étoiles blanches qui scintillent autour des conteneurs actifs.").push("etoiles");
        ETOILES = b.comment("Montrer les étoiles autour des conteneurs actifs.").define("actives", true);
        RAYON_ETOILES = b.comment("Distance (blocs) jusqu'à laquelle un joueur voit les étoiles.").defineInRange("rayon", 24, 4, 64);
        ETOILES_PAR_SECONDE = b.comment("Étoiles envoyées par seconde et par conteneur (28/10 : étoiles d'une seconde environ, 4 pour garder le même éclat).")
                .defineInRange("par_seconde", 4, 1, 8);
        b.pop();
        b.comment("Bonus de loot (zone tenue par son clan...) : au premier tirage d'un conteneur, chaque objet a ce pourcentage",
                "de chances d'être tiré une seconde fois (le plus rare des deux est gardé) et les quantités montent d'autant.").push("bonus");
        BONUS_MAX = b.comment("Bonus maximal (%) quand plusieurs bonus s'additionnent.").defineInRange("max", 25, 0, 100);
        b.pop();
        b.comment("Conteneurs de Minecraft (coffre, baril, four, alambic, entonnoir...) : un joueur en survie ou en aventure ne",
                "peut pas les ouvrir (« Ce conteneur est vide et scellé. »). Créatif, spectateur et staff (permission 2) : libres.",
                "Seulement les blocs et entités de Minecraft (namespace minecraft) : jamais ceux de nos mods ni de cfm.",
                "Postes de travail (établi, enclume, table d'enchantement...), coffre de l'Ender, pupitre : section [postes_vanilla].")
                .push("conteneurs_vanilla");
        VANILLA_ACTIF = b.comment("Sceller les conteneurs de Minecraft.").define("actif", true);
        VANILLA_BLOQUES = b.comment("Blocs scellés : identifiants (minecraft:chest) ou tags (#minecraft:shulker_boxes).")
                .defineListAllowEmpty(List.of("bloques"), () -> List.of("minecraft:chest", "minecraft:trapped_chest",
                        "minecraft:barrel", "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker",
                        "minecraft:brewing_stand", "minecraft:hopper", "minecraft:dispenser", "minecraft:dropper",
                        "#minecraft:shulker_boxes", "minecraft:jukebox", "minecraft:chiseled_bookshelf"), ConfigLoot::idValide);
        VANILLA_AUTO = b.comment("Sceller aussi tout autre bloc de Minecraft dont l'entité de bloc garde des objets (inventaire).")
                .define("inventaires_auto", true);
        VANILLA_EXCEPTIONS = b.comment("Conteneurs toujours permis, même s'ils sont dans la liste ou ont un inventaire (identifiants ou",
                        "tags). Un bloc mis ici passe ensuite par [postes_vanilla] : un pupitre ou un feu de camp y reste hors service.")
                .defineListAllowEmpty(List.of("exceptions"), () -> List.<String>of(), ConfigLoot::idValide);
        VANILLA_CLAIMS = b.comment("Laisser ouvrir les conteneurs posés dans un claim de CRac (rangements des bases des joueurs ;",
                "CRac décide ensuite qui peut les ouvrir). false : scellés là aussi.").define("permis_claims_crac", true);
        VANILLA_ENTITES = b.comment("Sceller aussi les wagonnets à coffre et à entonnoir, et le coffre des bateaux à coffre",
                "(on peut toujours monter dans le bateau).").define("entites", true);
        b.pop();
        b.comment("Postes de travail et blocs à usage de Minecraft (établi, enclumes, table d'enchantement, métier à tisser,",
                "coffre de l'Ender, cartographie, tailleur de pierre, table de forge, balise, meule, pupitre, composteur, chaudron,",
                "pots de fleurs, feux de camp, bougies, gâteau, bloc musical, répéteur, comparateur, blocs de commande...) : un joueur",
                "en survie ou en aventure ne peut pas s'en servir (« Ce poste est hors service. »). Créatif, spectateur et staff",
                "(permission 2) : libres. Restent permis (jamais dans la liste) : portes, trappes, portillons, boutons, leviers,",
                "plaques de pression, lits, échelles, cloches. Seulement les blocs de Minecraft (namespace minecraft).")
                .push("postes_vanilla");
        POSTES_ACTIF = b.comment("Mettre hors service les postes de travail de Minecraft.").define("actif", true);
        POSTES_BLOQUES = b.comment("Blocs hors service : identifiants (minecraft:loom) ou tags (#minecraft:cauldrons).")
                .defineListAllowEmpty(List.of("bloques"), () -> List.of("minecraft:crafting_table", "#minecraft:anvil",
                        "minecraft:enchanting_table", "minecraft:loom", "minecraft:ender_chest", "minecraft:cartography_table",
                        "minecraft:stonecutter", "minecraft:smithing_table", "minecraft:beacon", "minecraft:grindstone",
                        "minecraft:fletching_table", "minecraft:lectern", "minecraft:composter", "#minecraft:cauldrons",
                        "#minecraft:flower_pots", "minecraft:decorated_pot", "#minecraft:campfires", "#minecraft:candles",
                        "#minecraft:candle_cakes", "minecraft:cake", "minecraft:respawn_anchor", "minecraft:note_block",
                        "minecraft:repeater", "minecraft:comparator", "minecraft:daylight_detector", "minecraft:redstone_wire",
                        "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
                        "minecraft:structure_block", "minecraft:jigsaw", "minecraft:end_portal_frame", "minecraft:dragon_egg",
                        "minecraft:spawner", "minecraft:pumpkin", "#minecraft:beehives"), ConfigLoot::idValide);
        POSTES_MENUS_AUTO = b.comment("Mettre hors service aussi tout autre bloc de Minecraft qui ouvre un menu (les conteneurs restent",
                "réglés par [conteneurs_vanilla]).").define("menus_auto", true);
        POSTES_EXCEPTIONS = b.comment("Blocs toujours permis, même s'ils sont dans la liste ou ouvrent un menu (identifiants ou tags).")
                .defineListAllowEmpty(List.of("exceptions"), () -> List.<String>of(), ConfigLoot::idValide);
        POSTES_CLAIMS = b.comment("Laisser servir les postes posés dans un claim de CRac (bases des joueurs). false (choix du",
                "propriétaire : bloquer en trop) : hors service là aussi. Les coffres et barils des bases suivent",
                "permis_claims_crac de [conteneurs_vanilla].").define("permis_claims_crac", false);
        b.pop();
        b.comment("Présentoirs (cadres, cadres lumineux, porte-armures, mannequins de l'Armurerie, peintures, noeuds de laisse) : un",
                "joueur en survie ou en aventure ne peut ni les casser, ni les frapper, ni tourner ou prendre l'objet d'un cadre, ni",
                "prendre ou poser un objet sur un porte-armure ou un mannequin (« Ce présentoir est protégé. »). Ni ses projectiles",
                "ni les balles de TaCZ ne les touchent, et aucune explosion ne les abîme ni ne casse le bloc qui les porte.",
                "Créatif, spectateur et staff (permission 2) : libres.")
                .push("presentoirs");
        PRES_ACTIF = b.comment("Protéger les présentoirs.").define("actif", true);
        PRES_ENTITES = b.comment("Entités protégées (identifiants).")
                .defineListAllowEmpty(List.of("entites"), () -> List.of("minecraft:item_frame", "minecraft:glow_item_frame",
                        "minecraft:armor_stand", "minecraft:painting", "minecraft:leash_knot", "heraliz_armory:mannequin"),
                        ConfigLoot::idValide);
        PRES_AUTO = b.comment("Protéger aussi toute autre entité accrochée à un bloc (cadres, peintures d'autres mods) et tout",
                "porte-armure d'un autre mod (sauf les marqueurs invisibles).").define("auto", true);
        PRES_EXCEPTIONS = b.comment("Entités jamais protégées, même si elles sont dans la liste ou reconnues d'office (identifiants).")
                .defineListAllowEmpty(List.of("exceptions"), () -> List.<String>of(), ConfigLoot::idValide);
        PRES_POSE = b.comment("Interdire aussi de poser un cadre, une peinture, un porte-armure ou un mannequin (objets de la liste",
                "objets, plus tout objet qui pose une entité accrochée ou un porte-armure).").define("poser", true);
        PRES_OBJETS = b.comment("Objets qui posent un présentoir, en plus de ceux reconnus d'office (identifiants).")
                .defineListAllowEmpty(List.of("objets"), () -> List.of("heraliz_armory:mannequin"), ConfigLoot::idValide);
        PRES_CLAIMS = b.comment("Dans un claim de CRac (bases des joueurs) : présentoirs libres (poser, décorer, retirer ; CRac",
                "décide ensuite). false : protégés là aussi.").define("permis_claims_crac", true);
        b.pop();
        b.comment("Autres façons d'abîmer la carte, refusées à un joueur en survie ou en aventure (« Impossible ici. ») ; créatif,",
                "spectateur et staff (permission 2) : libres. Permis dans un claim de CRac si permis_claims_crac (CRac décide ensuite).")
                .push("nuisances");
        NUIS_ACTIF = b.comment("Refuser les nuisances ci-dessous.").define("actif", true);
        NUIS_SEAUX = b.comment("Seaux : poser ou ramasser de l'eau, de la lave, de la neige poudreuse, un poisson en seau.")
                .define("seaux", true);
        NUIS_FEU = b.comment("Feu : briquet et boule de feu sur un bloc (allumer un feu, une bougie, un feu de camp, de la TNT).")
                .define("feu", true);
        NUIS_OUTILS = b.comment("Outils sur un bloc : écorcer à la hache, gratter ou décirer le cuivre, cirer au rayon de miel,",
                "faire un chemin à la pelle, labourer à la houe.").define("outils", true);
        NUIS_ENGRAIS = b.comment("Poudre d'os sur un bloc (arbres, herbes et fleurs qui poussent).").define("engrais", true);
        NUIS_PANNEAUX = b.comment("Panneaux : écrire sur un panneau posé, le teindre, le faire briller, le cirer, et poser un panneau.",
                "Un panneau ciré (par le staff) reste utilisable : ses commandes au clic marchent.").define("panneaux", true);
        NUIS_CLAIMS = b.comment("Tout cela reste permis dans un claim de CRac (bases des joueurs). false : refusé là aussi.")
                .define("permis_claims_crac", true);
        b.pop();
        SPEC = b.build();
    }

    private static boolean idValide(Object o) {
        return o instanceof String t && !t.isBlank();
    }

    /** Proportion par défaut (repli sur 0.2 si la configuration n'est pas encore chargée). */
    /** Facteur de la part d'actifs d'une zone de ce niveau (1 si inconnu). */
    static double facteur(int niveau) {
        if (!SPEC.isLoaded() || niveau < 1) return 1.0;
        List<? extends Double> l = FACTEURS.get();
        if (niveau > l.size()) return 1.0;
        Object v = l.get(niveau - 1);
        return v instanceof Number n ? Math.max(0, Math.min(1, n.doubleValue())) : 1.0;
    }

    static double proportion() {
        return SPEC.isLoaded() ? PROPORTION.get() : 0.2;
    }

    /** Délai de réapparition tiré entre min et max, en ticks. */
    static long delaiTicks(net.minecraft.util.RandomSource r) {
        int min = SPEC.isLoaded() ? DELAI_MIN.get() : 240, max = SPEC.isLoaded() ? DELAI_MAX.get() : 360;
        if (max < min) max = min;
        return 20L * (min + (max > min ? r.nextInt(max - min + 1) : 0));
    }

    /** Délai avant de recycler un conteneur entamé, en ticks (0 : jamais). */
    static long restesTicks() {
        return 20L * (SPEC.isLoaded() ? RESTES.get() : 300);
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
        return SPEC.isLoaded() ? ETOILES_PAR_SECONDE.get() : 4;
    }
}
