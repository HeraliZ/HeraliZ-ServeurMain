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
    // IA allégée (voir IaZombies)
    public static final ForgeConfigSpec.BooleanValue IA_ALLEGEE;
    public static final ForgeConfigSpec.IntValue IA_RYTHME;
    public static final ForgeConfigSpec.DoubleValue IA_CHEMINS;
    // budget et partage des chemins (voir CheminsZombies), niveaux d'IA selon la distance (IaZombies)
    public static final ForgeConfigSpec.IntValue CHEMINS_BUDGET, LOD_PROCHE, LOD_MOYEN, LOD_SOMMEIL;
    public static final ForgeConfigSpec.BooleanValue CHEMINS_PARTAGE;
    // variétés (voir VarietesZombies)
    public static final ForgeConfigSpec.BooleanValue VARIETES;
    public static final ForgeConfigSpec.DoubleValue PART_COUREURS, PART_COSTAUDS, VIE_MARCHEUR, VIE_COUREUR, VIE_COSTAUD,
            VITESSE_MARCHEUR, VITESSE_COUREUR, VITESSE_COSTAUD, DEGATS_COSTAUD;
    // peuplement (voir Peuplement et ReservesZombies)
    public static final ForgeConfigSpec.BooleanValue PEUPLEMENT, PEUPLEMENT_REGLE, PEUPLEMENT_BOTS, TEST_VUE;
    public static final ForgeConfigSpec.IntValue PEUPLEMENT_INTERVALLE, PEUPLEMENT_RETRAIT, PEUPLEMENT_MAX, SECTEUR, CAP_NATURE, CAP_VILLE,
            APPARITION_MIN, APPARITION_MAX, ECART_JOUEUR, ECART_INTERIEUR, VUE_PORTEE, MAX_AUTOUR, MAX_AUTOUR_RAYON, EXTRACTION_RAYON,
            EXTRACTION_VIDE;
    public static final ForgeConfigSpec.DoubleValue PEUPLEMENT_NUIT, PEUPLEMENT_HORS_VUE, PEUPLEMENT_TICK_MAX, CHANCE_ESSAI, CAP_PAR_VALEUR,
            REGEN_NATURE, REGEN_VILLE, EXTRACTION_FACTEUR;
    // bruit et sang (voir StimuliZombies)
    public static final ForgeConfigSpec.BooleanValue STIMULI;
    public static final ForgeConfigSpec.IntValue STIMULUS_TIR, STIMULUS_TIR_SILENCIEUX, STIMULUS_EXPLOSION, STIMULUS_SANG, STIMULUS_MAX;
    // combat juste (voir JetonsAttaque et AttaquesZombiesServeur)
    public static final ForgeConfigSpec.IntValue JETONS, JETONS_FENETRE;
    public static final ForgeConfigSpec.BooleanValue ESQUIVE_SURVIE;

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
        IA_ALLEGEE = b.comment("IA allegee des zombies HeraliZ (24/10) : ils ne cherchent plus de village la nuit, ne cherchent plus",
                        "d'oeufs de tortue a casser, ne visent plus villageois, golems ni tortues (seulement les joueurs, et ce qui les frappe).",
                        "false : IA du zombie de Minecraft complete.").define("ia_allegee", true);
        IA_RYTHME = b.comment("Tous les combien de ticks un zombie reconsidere ses objectifs (cible, errance). Minecraft : 3.")
                .defineInRange("ia_rythme", 6, 1, 20);
        IA_CHEMINS = b.comment("Taille maximale d'une recherche de chemin, par rapport a Minecraft (1.0). Plus petit : chemins",
                        "longs et compliques abandonnes plus tot, beaucoup moins de calcul quand un joueur est loin ou cache.")
                .defineInRange("ia_chemins", 0.5, 0.1, 1.0);
        CHEMINS_BUDGET = b.comment("Recherches de chemin des zombies HeraliZ par tick, sur tout le serveur (24/10). Au-delà, le zombie",
                        "garde son ancien chemin et réessaie un peu plus tard (Minecraft attend alors 0,75 s). 0 : pas de budget.")
                .defineInRange("chemins_budget", 40, 0, 10000);
        CHEMINS_PARTAGE = b.comment("Un zombie qui vise la même case qu'un autre, à 4 blocs de lui, dans la seconde, reprend son chemin au lieu",
                "d'en recalculer un (une horde qui suit un joueur ne calcule qu'un chemin).").define("chemins_partage", true);
        LOD_PROCHE = b.comment("Niveaux d'IA selon le joueur le plus proche : jusqu'à lod_proche blocs, IA complète (objectifs revus tous les",
                        "3 ticks comme Minecraft, chemins complets) ; jusqu'à lod_moyen, ia_rythme et ia_chemins ; jusqu'à lod_sommeil,",
                        "objectifs tous les 20 ticks et chemins quatre fois plus courts ; au-delà, endormi (objectifs toutes les 2 s, plus de",
                        "chemin sans cible). Sous Mohist, Spigot ne fait presque plus bouger les monstres au-delà de 24 blocs",
                        "(entity-activation-range de spigot.yml).").defineInRange("lod_proche", 16, 4, 128);
        LOD_MOYEN = b.defineInRange("lod_moyen", 40, 8, 256);
        LOD_SOMMEIL = b.defineInRange("lod_sommeil", 64, 16, 512);
        b.pop();
        b.comment("Variétés des zombies HeraliZ (24/10, « des coureurs, des costauds, une belle variété, assez résistants ») :",
                "tirées à leur première arrivée, gardées ensuite. Le reste des zombies est marcheur. Les rampants restent ceux de",
                "Mutants & Zombies (un zombie rampant maison demande d'abord des rendus validés).").push("varietes");
        VARIETES = b.define("actif", true);
        PART_COUREURS = b.comment("Part de coureurs : plus rapides, moins de vie.").defineInRange("part_coureurs", 0.22, 0.0, 1.0);
        PART_COSTAUDS = b.comment("Part de costauds : beaucoup de vie, lents, frappent plus fort, peu de recul.").defineInRange("part_costauds", 0.08, 0.0, 1.0);
        VIE_MARCHEUR = b.comment("Points de vie (Minecraft : 20).").defineInRange("vie_marcheur", 30.0, 1.0, 1000.0);
        VIE_COUREUR = b.defineInRange("vie_coureur", 24.0, 1.0, 1000.0);
        VIE_COSTAUD = b.defineInRange("vie_costaud", 60.0, 1.0, 1000.0);
        VITESSE_MARCHEUR = b.comment("Vitesse (Minecraft : 0,23).").defineInRange("vitesse_marcheur", 0.23, 0.05, 1.0);
        VITESSE_COUREUR = b.defineInRange("vitesse_coureur", 0.33, 0.05, 1.0);
        VITESSE_COSTAUD = b.defineInRange("vitesse_costaud", 0.20, 0.05, 1.0);
        DEGATS_COSTAUD = b.comment("Dégâts en plus d'un costaud (Minecraft : 3 par coup).").defineInRange("degats_costaud", 1.5, 0.0, 20.0);
        b.pop();
        b.comment("Peuplement de la carte (25/10, refait : « pas obligatoirement N zombies autour de chaque joueur, une regle plus",
                "aleatoire, moins de lag, jamais un zombie qui apparait sur un joueur de nulle part »). La carte est decoupee en secteurs",
                "(carres de « secteur » blocs). Chaque secteur a une capacite (nature, ville selon sa valeur, zones de /zombies zone) et",
                "une reserve : un zombie pose prend une place de la reserve ; un zombie tue ne revient qu'apres « regen » minutes ; un",
                "zombie retire (plus personne pres de lui) rend sa place. Un groupe qui reste au meme endroit vide donc les reserves",
                "autour de lui au lieu d'etre « rempli » sans fin. A chaque passage, chaque joueur en survie ou en aventure sur la carte",
                "(ni vehicule, ni train) n'a qu'une CHANCE de faire apparaitre un zombie, loin de tout joueur, jamais a la vue d'un",
                "joueur, et en interieur seulement si personne n'est proche.").push("peuplement");
        PEUPLEMENT = b.comment("false : plus de peuplement (les zombies deja poses partent quand plus personne n'est pres d'eux).")
                .define("actif", true);
        PEUPLEMENT_INTERVALLE = b.comment("Secondes entre deux passages.").defineInRange("intervalle_secondes", 3, 1, 60);
        CHANCE_ESSAI = b.comment("Chance, a chaque passage, qu'un joueur fasse apparaitre un zombie (s'il y a de la place autour).")
                .defineInRange("chance_essai", 0.35, 0.0, 1.0);
        PEUPLEMENT_NUIT = b.comment("Multiplicateur de cette chance la nuit.").defineInRange("nuit", 1.25, 0.0, 5.0);
        SECTEUR = b.comment("Taille d'un secteur (blocs).").defineInRange("secteur", 64, 16, 512);
        CAP_NATURE = b.comment("Zombies au plus dans un secteur hors des villes.").defineInRange("capacite_nature", 2, 0, 100);
        CAP_VILLE = b.comment("Zombies au plus dans un secteur de ville (cercles de config/heralisz_clans/zones.json), plus",
                "capacite_par_valeur par point de valeur de la ville (niveau de loot, 1 a 7).").defineInRange("capacite_ville", 4, 0, 100);
        CAP_PAR_VALEUR = b.defineInRange("capacite_par_valeur", 1.0, 0.0, 20.0);
        REGEN_NATURE = b.comment("Minutes pour qu'une place de la reserve d'un secteur revienne (un zombie tue), hors des villes...")
                .defineInRange("regen_nature_minutes", 8.0, 0.1, 1440.0);
        REGEN_VILLE = b.comment("... et en ville.").defineInRange("regen_ville_minutes", 5.0, 0.1, 1440.0);
        APPARITION_MIN = b.comment("Distance d'apparition au joueur qui la provoque, au moins (blocs)...").defineInRange("apparition_min", 32, 8, 128);
        APPARITION_MAX = b.comment("... et au plus (rester sous la distance de simulation).").defineInRange("apparition_max", 72, 8, 160);
        ECART_JOUEUR = b.comment("Jamais a moins de tant de blocs d'un joueur, quel qu'il soit.").defineInRange("ecart_joueur", 28, 0, 128);
        ECART_INTERIEUR = b.comment("Apparition dans un batiment (etage a l'abri) seulement si aucun joueur n'est a moins de tant de blocs.")
                .defineInRange("ecart_interieur", 40, 0, 256);
        TEST_VUE = b.comment("Jamais d'apparition a un endroit qu'un joueur voit (rayon tire depuis ses yeux vers la tete et les pieds).")
                .define("test_vue", true);
        VUE_PORTEE = b.comment("Portee du test de vue (blocs).").defineInRange("vue_portee", 96, 16, 256);
        PEUPLEMENT_HORS_VUE = b.comment("Part des essais faits derriere ou sur les cotes du joueur.").defineInRange("part_hors_vue", 0.8, 0.0, 1.0);
        MAX_AUTOUR = b.comment("Rien de neuf si deja tant de zombies HeraliZ a moins de max_autour_rayon blocs du joueur.")
                .defineInRange("max_autour", 14, 0, 200);
        MAX_AUTOUR_RAYON = b.defineInRange("max_autour_rayon", 48, 16, 160);
        EXTRACTION_RAYON = b.comment("Pres d'une zone d'extraction ouverte (a moins de tant de blocs de son centre), la chance est",
                "multipliee par extraction_facteur, et rien de neuf a moins de extraction_vide blocs du centre. Les zombies deja la",
                "et le bruit restent le danger.").defineInRange("extraction_rayon", 64, 0, 512);
        EXTRACTION_FACTEUR = b.defineInRange("extraction_facteur", 0.3, 0.0, 1.0);
        EXTRACTION_VIDE = b.defineInRange("extraction_vide", 24, 0, 256);
        PEUPLEMENT_RETRAIT = b.comment("Un zombie du peuplement a plus de tant de blocs de tout joueur disparait (sans butin) et rend",
                "sa place a la reserve de son secteur.").defineInRange("distance_retrait", 96, 32, 512);
        PEUPLEMENT_MAX = b.comment("Zombies du peuplement au plus sur toute la carte.").defineInRange("max_total", 1000, 0, 100000);
        PEUPLEMENT_TICK_MAX = b.comment("Aucune nouvelle apparition tant que le serveur met plus de tant de millisecondes par tick",
                "(moyenne ; 50 = 20 TPS tout juste). 0 : jamais coupe.").defineInRange("tick_max_ms", 45.0, 0.0, 1000.0);
        PEUPLEMENT_REGLE = b.comment("true : rien tant que la regle doMobSpawning du monde est a false. false : peuplement quand meme",
                "(la carte de construction l'avait a false).").define("suivre_doMobSpawning", false);
        PEUPLEMENT_BOTS = b.comment("true : les bots de charge (/bots) attirent aussi des zombies.").define("bots", false);
        b.pop();
        b.comment("Bruit et sang (25/10) : tirs, explosions et joueurs blesses attirent les zombies HeraliZ proches qui n'ont pas de",
                "cible (ils vont voir). Un seul calcul au moment du bruit, jamais a chaque tick ; tirs d'un meme tireur une fois par",
                "seconde au plus.").push("stimuli");
        STIMULI = b.define("actif", true);
        STIMULUS_TIR = b.comment("Rayon (blocs) d'un tir sans silencieux.").defineInRange("tir", 80, 0, 256);
        STIMULUS_TIR_SILENCIEUX = b.comment("Rayon d'un tir avec silencieux.").defineInRange("tir_silencieux", 16, 0, 256);
        STIMULUS_EXPLOSION = b.comment("Rayon d'une explosion.").defineInRange("explosion", 96, 0, 256);
        STIMULUS_SANG = b.comment("Rayon de l'odeur du sang d'un joueur blesse (toutes les 3 s au plus par joueur).")
                .defineInRange("sang", 14, 0, 64);
        STIMULUS_MAX = b.comment("Zombies au plus attires par un meme bruit.").defineInRange("max_zombies", 16, 1, 200);
        b.pop();
        b.comment("Combat juste (25/10) : jamais une mort en une seconde sous dix coups a la fois.").push("attaques");
        JETONS = b.comment("Zombies differents au plus qui peuvent toucher un meme joueur dans la fenetre ci-dessous ; les autres",
                "attendent leur tour. 0 : pas de limite.").defineInRange("attaquants_max", 3, 0, 50);
        JETONS_FENETRE = b.comment("Fenetre (ticks ; 20 = 1 s).").defineInRange("fenetre_ticks", 30, 1, 200);
        ESQUIVE_SURVIE = b.comment("Les zombies HeraliZ frappent a la fin de leur geste, seulement si la cible est encore a portee et en vue",
                "(on peut esquiver en reculant), comme ceux de Mutants & Zombies. false : coup au contact de Minecraft.")
                .define("esquive", true);
        b.pop();
        SPEC = b.build();
    }
}
