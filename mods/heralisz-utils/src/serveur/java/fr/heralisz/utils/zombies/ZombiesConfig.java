package fr.heralisz.utils.zombies;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Reglages des zombies de la survie : heraliz_zombies-server.toml (voir {@link SpawnZombiesServeur}). */
public final class ZombiesConfig {
    private ZombiesConfig() {
    }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ACTIF;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ARCTIQUE, PRISON, MILITAIRE, CONTAMINEES, REMPLACES, MUTANTS, INTERDITS;
    public static final ForgeConfigSpec.DoubleValue PART_HAZMAT;
    public static final ForgeConfigSpec.IntValue PLAFOND_CARTE, PLAFOND_LOCAL;
    // peuplement (voir Peuplement)
    public static final ForgeConfigSpec.BooleanValue PEUPLEMENT, PEUPLEMENT_REGLE, PEUPLEMENT_BOTS;
    public static final ForgeConfigSpec.IntValue PEUPLEMENT_INTERVALLE, PEUPLEMENT_NATURE, PEUPLEMENT_VILLE, PEUPLEMENT_MAX_JOUEUR,
            PEUPLEMENT_RAYON, PEUPLEMENT_DIST_MIN, PEUPLEMENT_DIST_MAX, PEUPLEMENT_DIST_JOUEUR, PEUPLEMENT_RETRAIT, PEUPLEMENT_MAX;
    public static final ForgeConfigSpec.DoubleValue PEUPLEMENT_PAR_VALEUR, PEUPLEMENT_NUIT, PEUPLEMENT_BONUS_JOUEUR, PEUPLEMENT_BONUS_MAX,
            PEUPLEMENT_HORS_VUE, PEUPLEMENT_TICK_MAX;
    // intelligence des zombies HeraliZ (voir ZombieSurvieServeur, NavigationZombie)
    public static final ForgeConfigSpec.BooleanValue IA_ACTIVE, IA_AWARENESS, PLAFOND_AVANT;
    public static final ForgeConfigSpec.IntValue IA_DISTANCE, IA_CHEMIN_PRES, IA_ATTENTE_MOYEN, IA_ATTENTE_LOIN, IA_ATTENTE_BLOQUE;
    public static final ForgeConfigSpec.DoubleValue IA_SUIVI_MAX;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("zombies");
        ACTIF = b.comment("Remplacer les zombies qui apparaissent sur la carte par les zombies HeraliZ (tenue selon l'endroit).",
                "false : zombies de Minecraft comme avant.").define("actif", true);
        REMPLACES = b.comment("Zombies de Minecraft remplaces partout sur la carte.")
                .defineListAllowEmpty("remplaces", List.of("minecraft:zombie", "minecraft:husk", "minecraft:zombie_villager"), o -> o instanceof String);
        INTERDITS = b.comment("Jamais sur la carte, d'ou qu'ils viennent (apparition, commande, oeuf, deja dans le monde) :",
                        "retires des leur arrivee. Les zombies remplaces par les zombies HeraliZ ne passent pas par la.")
                .defineListAllowEmpty("interdits", List.of("minecraft:zombie", "minecraft:husk", "minecraft:zombie_villager",
                        "minecraft:drowned"), o -> o instanceof String);
        MUTANTS = b.comment("Remplaces en plus dans l'arctique, la prison et les zones militaires (\"que des zombies arctiques\").",
                        "Un nom qui finit par :* vaut pour tout le mod.")
                .defineListAllowEmpty("remplaces_zones_fermees", List.of("mutantszombies:*"), o -> o instanceof String);
        b.comment("Villes (identifiants de config/heralisz_clans/zones.json : centre et rayon de la ville).");
        ARCTIQUE = b.comment("Seulement des zombies arctiques (aussi dans les zones « froid » de HeraliZ Decors).")
                .defineListAllowEmpty("villes_arctique", List.of("arctique"), o -> o instanceof String);
        PRISON = b.comment("Seulement des prisonniers.").defineListAllowEmpty("villes_prison", List.of("prison"), o -> o instanceof String);
        MILITAIRE = b.comment("Seulement des militaires.")
                .defineListAllowEmpty("villes_militaire", List.of("aerodrome", "silo"), o -> o instanceof String);
        CONTAMINEES = b.comment("Surtout des hazmat (aussi dans les zones « gaz » et « radiation » de HeraliZ Decors).")
                .defineListAllowEmpty("villes_contaminees", List.of("gaz", "radio"), o -> o instanceof String);
        PART_HAZMAT = b.comment("Part de hazmat dans une zone contaminee (le reste : zombies de ville).")
                .defineInRange("part_hazmat", 0.75, 0.0, 1.0);
        PLAFOND_CARTE = b.comment("Monstres au plus sur toute la carte (24/10, pour 50 joueurs : le serveur ralentit au-dela) ;",
                        "au-dela, plus aucune apparition naturelle de monstre sur la carte. 0 : pas de plafond.",
                        "Vagues des drapeaux, /summon et oeufs : jamais concernes.")
                .defineInRange("plafond_carte", 1500, 0, 100000);
        PLAFOND_LOCAL = b.comment("Monstres au plus dans un carre de 5 x 5 troncons (80 blocs) autour du point d'apparition ;",
                        "au-dela, pas d'apparition naturelle ici (plusieurs joueurs au meme endroit n'empilent plus les hordes). 0 : pas de plafond.")
                .defineInRange("plafond_local", 0, 0, 10000);
        PLAFOND_AVANT = b.comment("Plafonds ci-dessus verifies AVANT que Minecraft cree le monstre (regles d'apparition) : au plafond, les",
                        "essais d'apparition naturelle ne coutent presque plus rien (avant : monstre cree, puis refuse). false : comme avant.")
                .define("plafond_avant_creation", true);
        b.pop();
        b.comment("Peuplement de la carte (24/10) : des zombies HeraliZ apparaissent autour de chaque joueur en survie ou en aventure",
                "sur la carte (ni spectateur, ni creatif, ni a bord d'un vehicule ou du train), de jour comme de nuit, hors de sa vue",
                "de preference, et disparaissent (sans butin) loin de tout joueur. Cible = zombies HeraliZ a moins de rayon_compte blocs",
                "du joueur, partagee entre joueurs proches. Le plafond_carte ci-dessus est toujours respecte.").push("peuplement");
        PEUPLEMENT = b.comment("false : plus de peuplement (les zombies deja poses partent quand plus personne n'est pres d'eux).")
                .define("actif", true);
        PEUPLEMENT_INTERVALLE = b.comment("Secondes entre deux passages ; a chaque passage, au plus un zombie par joueur.")
                .defineInRange("intervalle_secondes", 2, 1, 60);
        PEUPLEMENT_NATURE = b.comment("Zombies vises autour d'un joueur hors des villes.").defineInRange("cible_nature", 6, 0, 100);
        PEUPLEMENT_VILLE = b.comment("Zombies vises autour d'un joueur dans une ville (cercles de config/heralisz_clans/zones.json),",
                "plus cible_par_valeur par point de valeur de la ville (niveau de loot, 1 a 7).").defineInRange("cible_ville", 10, 0, 100);
        PEUPLEMENT_PAR_VALEUR = b.comment("En plus par point de valeur de la ville (valeur 2 : 10 + 3 = 13 ; valeur 5 : 10 + 7,5 = 17).")
                .defineInRange("cible_par_valeur", 1.5, 0.0, 20.0);
        PEUPLEMENT_NUIT = b.comment("Multiplicateur de la cible la nuit.").defineInRange("nuit", 1.25, 0.0, 5.0);
        PEUPLEMENT_BONUS_JOUEUR = b.comment("Cible partagee : un groupe n'attire pas un zombie de plus par joueur. Chaque autre joueur",
                "a moins de rayon_compte blocs ajoute seulement cette part a la cible (0,1 = +10 %)...").defineInRange("bonus_par_joueur", 0.10, 0.0, 1.0);
        PEUPLEMENT_BONUS_MAX = b.comment("... jusqu'a cette part au plus (0,4 = +40 %).").defineInRange("bonus_groupe_max", 0.40, 0.0, 5.0);
        PEUPLEMENT_MAX_JOUEUR = b.comment("Cible au plus, ville, nuit et groupe compris.").defineInRange("cible_max", 24, 0, 200);
        PEUPLEMENT_RAYON = b.comment("Rayon (blocs) dans lequel les zombies HeraliZ sont comptes autour d'un joueur.")
                .defineInRange("rayon_compte", 64, 16, 160);
        PEUPLEMENT_DIST_MIN = b.comment("Distance d'apparition au joueur, au moins (blocs)...").defineInRange("distance_min", 24, 8, 128);
        PEUPLEMENT_DIST_MAX = b.comment("... et au plus (rester sous la distance de simulation : 5 tronçons = 80 blocs).")
                .defineInRange("distance_max", 56, 8, 128);
        PEUPLEMENT_DIST_JOUEUR = b.comment("Jamais a moins de tant de blocs d'un joueur, quel qu'il soit.")
                .defineInRange("distance_joueur_min", 20, 0, 128);
        PEUPLEMENT_HORS_VUE = b.comment("Part des essais faits hors du champ de vision du joueur (derriere et sur les cotes).")
                .defineInRange("part_hors_vue", 0.8, 0.0, 1.0);
        PEUPLEMENT_RETRAIT = b.comment("Un zombie du peuplement a plus de tant de blocs de tout joueur disparait (sans butin).")
                .defineInRange("distance_retrait", 96, 32, 512);
        PEUPLEMENT_MAX = b.comment("Zombies du peuplement au plus sur toute la carte.").defineInRange("max_total", 1000, 0, 100000);
        PEUPLEMENT_TICK_MAX = b.comment("Aucune nouvelle apparition tant que le serveur met plus de tant de millisecondes par tick",
                "(moyenne ; 50 = 20 TPS tout juste). 0 : jamais coupe.").defineInRange("tick_max_ms", 60.0, 0.0, 1000.0);
        PEUPLEMENT_REGLE = b.comment("true : rien tant que la regle doMobSpawning du monde est a false. false : peuplement quand meme",
                "(la carte de construction l'avait a false).").define("suivre_doMobSpawning", false);
        PEUPLEMENT_BOTS = b.comment("true : les bots de charge (/bots) attirent aussi des zombies.").define("bots", false);
        b.pop();
        b.comment("Intelligence des zombies HeraliZ (24/10, performances du serveur ; ne change rien de pres) :",
                "- plus d'objectifs inutiles sur la carte (villageois, golems, tortues et oeufs de tortue, « traverser le village » la nuit,",
                "  qui cherchait des lits et des postes de travail et calculait un chemin toutes les 2 ticks par zombie inoccupe) ;",
                "- chemin vers la cible recalcule moins souvent quand elle est loin (de pres : comme Minecraft) ;",
                "- loin de tout joueur et sans cible : ni promenade ni Zombie Awareness (rien ne les voit) ;",
                "- portee de suivi bornee (le bonus de difficulte de Minecraft pouvait la porter a 87 blocs).").push("ia");
        IA_ACTIVE = b.comment("false : zombies HeraliZ comme le zombie de Minecraft (objectifs, chemins, portee de suivi).")
                .define("actif", true);
        IA_DISTANCE = b.comment("Au-dela de tant de blocs de tout joueur (et sans cible), le zombie ne se promene plus et Zombie Awareness",
                "le laisse de cote ; il se reveille des qu'un joueur approche ou qu'on le touche.").defineInRange("distance_reduite", 48, 16, 512);
        IA_CHEMIN_PRES = b.comment("Sous tant de blocs de sa cible, le chemin est recalcule comme dans Minecraft (toutes les 4 a 10 ticks).")
                .defineInRange("chemin_pres", 12, 0, 128);
        IA_ATTENTE_MOYEN = b.comment("De chemin_pres a 24 blocs : ticks au moins entre deux calculs de chemin vers la meme cible.")
                .defineInRange("attente_moyen", 15, 0, 200);
        IA_ATTENTE_LOIN = b.comment("Au-dela de 24 blocs : ticks au moins entre deux calculs de chemin vers la meme cible.")
                .defineInRange("attente_loin", 30, 0, 400);
        IA_ATTENTE_BLOQUE = b.comment("Cible hors d'atteinte (sur un toit, derriere un mur...) et toujours au meme endroit : ticks au moins",
                "entre deux calculs (chaque calcul rate parcourait les 560 cases permises).").defineInRange("attente_bloque", 10, 0, 200);
        IA_SUIVI_MAX = b.comment("Portee de suivi (blocs) au plus : distance a laquelle un zombie repere et poursuit un joueur.",
                "Minecraft : 35, jusqu'a 87 avec le bonus de difficulte. 0 : pas de limite.").defineInRange("suivi_max", 40.0, 0.0, 128.0);
        IA_AWARENESS = b.comment("Zombie Awareness : recherche des sons et odeurs faite seulement quand son tirage la demande (10 % des fois)",
                "et parmi ses seules entites de sens, au lieu de lister toutes les entites a 64 blocs a chaque fois. Meme resultat.")
                .define("awareness_rapide", true);
        b.pop();
        SPEC = b.build();
    }
}
