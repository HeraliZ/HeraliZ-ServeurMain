package fr.heraliz.crac.bases;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;

import java.util.List;

/**
 * Réglages du monde des bases (config/serverconfig {@code crac-bases.toml}, type SERVER : lus par le serveur, recopiés
 * chez le joueur). Sections {@code [bases]}, {@code [home]}, {@code [tuto]}, {@code [protection]}, {@code [carrieres]}.
 */
public final class BasesReglages {
    public static final ForgeConfigSpec SPEC;

    // [bases]
    public static final IntValue BORDURE;
    public static final BooleanValue CONSTRUCTION_ICI_SEULEMENT;
    public static final BooleanValue CREATURES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CREATURES_PERMISES;
    public static final BooleanValue SEAUX;
    public static final IntValue RAYON_DEPART;
    public static final IntValue ECART_BASES;
    // [home]
    public static final IntValue HOME_DELAI_S;
    public static final IntValue IMMOBILE_S;
    public static final IntValue ZONE_DELAI_S;
    public static final IntValue HOMES_BASE;
    public static final IntValue HOMES_PAR_GRADE;
    public static final IntValue SPAWN_RAYON;
    // [tuto]
    public static final BooleanValue TUTO_ACTIF;
    public static final IntValue ATTENTE_MIN_S;
    public static final IntValue ATTENTE_MAX_S;
    public static final BooleanValue AMELIORATION_BOIS;
    public static final IntValue MARGE_POURCENT;
    public static final IntValue ENTRETIEN_HEURES;
    public static final IntValue PHASE_MAX_MIN;
    public static final IntValue FONDATIONS_MIN;
    // [protection]
    public static final DoubleValue PROTECTION_JOURS;
    public static final IntValue MEMBRES_MAX;
    public static final BooleanValue PAUSE_USURE;
    public static final BooleanValue PANNEAU;
    public static final BooleanValue PROTECTION_AUTO;
    // [carrieres]
    public static final IntValue REPOUSSE_MIN;
    public static final IntValue RAYON_MAX;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Monde des bases (dimension crac:bases) : reliefs et grands endroits plats, ou les joueurs construisent.").push("bases");
        BORDURE = b.comment("Taille de la bordure du monde des bases (blocs de cote, centree sur 0 0). 0 = laisser celle du jeu.")
            .defineInRange("bordure", 2500, 0, 60_000_000);
        CONSTRUCTION_ICI_SEULEMENT = b.comment(
                "Vrai : en survie, les pieces de construction et le Claim Block ne se posent QUE dans le monde des bases",
                "(carte DayZ, spawn et autres mondes refuses). Le staff (creatif) n'est pas concerne.")
            .define("construction_ici_seulement", true);
        CREATURES = b.comment("Faux : aucune creature dans le monde des bases (toute entite « Mob » : animaux, monstres, villageois,",
                "marchands, golems, PNJ et zombies des mods ; apparitions, oeufs, cages, mods) ; celles deja la sont retirees.",
                "Les autres entites restent (textes flottants, objets poses, vehicules, cadavres, porte-armures).")
            .define("creatures", false);
        CREATURES_PERMISES = b.comment("Creatures gardees malgre tout dans le monde des bases (identifiants, ex. \"minecraft:villager\").")
            .defineListAllowEmpty(List.of("creatures_permises"), () -> List.<String>of(), o -> o instanceof String);
        SEAUX = b.comment("Faux : dans le monde des bases, aucun seau ne se vide (eau, lave, poissons), meme dans une base.",
                "Le staff en creatif n'est pas concerne.")
            .define("seaux", false);
        RAYON_DEPART = b.comment("Rayon (blocs, autour de 0 0) ou l'on tire le point d'arrivee d'un joueur sans base ;",
                "ramene a l'interieur de la bordure (100 blocs de marge).")
            .defineInRange("rayon_depart", 1150, 64, 1_000_000);
        ECART_BASES = b.comment("Distance minimale (blocs) entre ce point d'arrivee et le Claim Block d'une autre base.")
            .defineInRange("ecart_bases", 120, 0, 4096);
        b.pop();

        b.comment("/home (du spawn vers l'armoire de la base de son clan), /rtp (du spawn vers un point au hasard du monde des bases) et /spawn (de sa base vers le spawn).").push("home");
        HOME_DELAI_S = b.comment("Secondes entre deux teleportations vers la base, /home ET reapparition a la base partagees (25/10 : 480 = 8 min ;",
                "l'arbre du clan retire 1 min par palier de la branche « tp »). Gardees au redemarrage, jamais remises a zero par la mort.")
            .defineInRange("delai_s", 480, 0, 86_400);
        IMMOBILE_S = b.comment("Secondes sans bouger ni etre blesse avant le depart (/home, /rtp et /spawn).")
            .defineInRange("immobile_s", 3, 0, 60);
        ZONE_DELAI_S = b.comment("Secondes entre deux /rtp (alias /rtp) d'un meme joueur (gardees au redemarrage).")
            .defineInRange("zone_delai_s", 300, 0, 86_400);
        HOMES_BASE = b.comment("Plus lu depuis le 25/10 (/home va toujours devant l'armoire de la base du clan).")
            .defineInRange("homes_base", 1, 0, 100);
        HOMES_PAR_GRADE = b.comment("Plus lu depuis le 25/10 (/home va toujours devant l'armoire de la base du clan).")
            .defineInRange("homes_par_grade", 1, 0, 100);
        SPAWN_RAYON = b.comment("Blocs autour du point /spawn defini par le staff (/spawn definir) qui comptent comme « au spawn »",
                "pour /home et /rtp (le monde du spawn heralisz:spawn compte toujours en entier).")
            .defineInRange("spawn_rayon", 48, 0, 4096);
        b.pop();

        b.comment("Tutoriel apres la cinematique d'arrivee (joueur qui n'a jamais eu de base).").push("tuto");
        TUTO_ACTIF = b.comment("Faux : pas de tutoriel ni de kit.").define("actif", true);
        ATTENTE_MIN_S = b.comment("Secondes au moins apres le debut de la cinematique ; le tuto part au premier pas du joueur ensuite.")
            .defineInRange("attente_min_s", 15, 0, 3600);
        ATTENTE_MAX_S = b.comment("Secondes au plus apres le debut de la cinematique (la cinematique dure environ 122 s).")
            .defineInRange("attente_max_s", 130, 1, 3600);
        AMELIORATION_BOIS = b.comment("Vrai : le kit contient aussi de quoi passer toute la cabane en bois au marteau.")
            .define("amelioration_bois", true);
        MARGE_POURCENT = b.comment("Matieres en plus dans le kit (pourcentage), pour une piece ratee.")
            .defineInRange("marge_pourcent", 10, 0, 200);
        ENTRETIEN_HEURES = b.comment("Heures d'entretien du Claim Block donnees dans le kit (0 = rien).")
            .defineInRange("entretien_heures", 24, 0, 720);
        PHASE_MAX_MIN = b.comment("Minutes de jeu au plus de la protection « nouveau joueur » (contour vert) si la base n'est pas posee.")
            .defineInRange("phase_max_min", 120, 1, 100_000);
        FONDATIONS_MIN = b.comment("Fondations au moins sous le Claim Block pour que la base compte comme posee.")
            .defineInRange("fondations_min", 2, 1, 64);
        b.pop();

        b.comment("Protection de la base d'un nouveau joueur, une fois posee (pas de raid).").push("protection");
        PROTECTION_AUTO = b.comment("Faux (HeraliZ, 27/10, choix du proprietaire) : plus aucune protection donnee toute seule a la base",
                "d'un nouveau joueur ; celles deja donnees sont retirees une fois. /protection donner (staff) marche toujours.")
            .define("automatique", false);
        PROTECTION_JOURS = b.comment("Jours (temps reel) de protection de la base.").defineInRange("jours", 4.0, 0.0, 365.0);
        MEMBRES_MAX = b.comment("La protection tombe si le groupe de la base compte plus de membres que ca (anti-abus).")
            .defineInRange("membres_max", 2, 1, 64);
        PAUSE_USURE = b.comment("Vrai : la base protegee ne s'use pas non plus (entretien impaye).").define("pause_usure", true);
        PANNEAU = b.comment("Vrai : texte « Base protegee » flottant au-dessus de la base, visible de tous.").define("panneau", true);
        b.pop();

        b.comment("Carrieres : zones du monde des bases ou les joueurs cassent les blocs naturels de leur type.").push("carrieres");
        REPOUSSE_MIN = b.comment("Minutes avant qu'un bloc casse dans une carriere repousse (jamais sous un joueur).")
            .defineInRange("repousse_min", 10, 1, 10_080);
        RAYON_MAX = b.comment("Rayon maximal d'une carriere (blocs).").defineInRange("rayon_max", 64, 2, 256);
        b.pop();
        SPEC = b.build();
    }

    private BasesReglages() {
    }

    /** Valeur d'un réglage, ou {@code defaut} tant que le fichier n'est pas chargé (avant l'arrivée dans un monde). */
    public static int i(IntValue v, int defaut) {
        return SPEC.isLoaded() ? v.get() : defaut;
    }

    public static boolean b(BooleanValue v, boolean defaut) {
        return SPEC.isLoaded() ? v.get() : defaut;
    }

    public static double d(DoubleValue v, double defaut) {
        return SPEC.isLoaded() ? v.get() : defaut;
    }
}
