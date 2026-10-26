package fr.heraliz.ping;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Reglages. SERVEUR (par monde : serverconfig/heraliz_ping-server.toml, modifiables en jeu par /ping admin) : regles
 * des pings, toutes verifiees par le serveur. CLIENT (config/heraliz_ping-client.toml, ecran Reglages) : affichage et
 * son, propres a chaque joueur.
 */
public final class PingConfig {
    private PingConfig() {
    }

    // ------------------------------------------------------------------ serveur
    public static final ForgeConfigSpec SERVEUR_SPEC;
    /** Interrupteur general (faux : les pings sont refuses). */
    public static final ForgeConfigSpec.BooleanValue ACTIF;
    /** Duree de vie d'un ping (secondes). */
    public static final ForgeConfigSpec.IntValue DUREE_S;
    /** Portee maximale depuis les yeux (blocs). */
    public static final ForgeConfigSpec.IntValue PORTEE;
    /** Le serveur verifie que la cible est bien en vue (pas de ping a travers les murs). */
    public static final ForgeConfigSpec.BooleanValue VERIFIER_VUE;
    /** Pings d'affilee permis avant d'attendre (anti-spam : seau a jetons). */
    public static final ForgeConfigSpec.IntValue RAFALE;
    /** Temps pour regagner un ping (millisecondes). */
    public static final ForgeConfigSpec.IntValue RECHARGE_MS;
    /** Pings actifs au plus par joueur (le plus ancien s'efface). */
    public static final ForgeConfigSpec.IntValue MAX_PAR_JOUEUR;
    /** Les spectateurs (mode spectateur, elimines en match) recoivent les pings de leur cercle. */
    public static final ForgeConfigSpec.BooleanValue SPECTATEURS_VOIENT;
    /** Hors match, un joueur sans clan pingue son groupe (/groupe de HeraliZ Matchs) s'il en a un. */
    public static final ForgeConfigSpec.BooleanValue GROUPE_HORS_MATCH;
    /** Pings poses depuis la carte du monde (heraliz_carte) : sans portee ni ligne de vue, dans le monde du joueur. */
    public static final ForgeConfigSpec.BooleanValue CARTE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("HeraliZ Ping : regles du serveur (modifiables en jeu : /ping admin)").push("pings");
        ACTIF = b.comment("Interrupteur general des pings").define("actif", true);
        DUREE_S = b.comment("Duree d'un ping a l'ecran, en secondes").defineInRange("duree_s", 10, 3, 60);
        PORTEE = b.comment("Portee maximale d'un ping depuis les yeux, en blocs").defineInRange("portee", 300, 16, 1024);
        VERIFIER_VUE = b.comment("Refuser un ping dont la cible n'est pas en vue (a travers un mur)").define("verifier_vue", true);
        RAFALE = b.comment("Pings d'affilee permis avant de devoir attendre (anti-spam)").defineInRange("rafale", 3, 1, 20);
        RECHARGE_MS = b.comment("Temps pour regagner un ping, en millisecondes").defineInRange("recharge_ms", 900, 100, 10000);
        MAX_PAR_JOUEUR = b.comment("Pings actifs au plus par joueur (le plus ancien s'efface)").defineInRange("max_par_joueur", 3, 1, 10);
        SPECTATEURS_VOIENT = b.comment("Les spectateurs (mode spectateur, elimines en match) voient les pings de leur equipe ou clan")
                .define("spectateurs_voient", false);
        GROUPE_HORS_MATCH = b.comment("Hors match, un joueur sans clan envoie ses pings a son groupe (/groupe) s'il en a un")
                .define("groupe_hors_match", true);
        CARTE = b.comment("Marquer un point depuis la carte du monde (clic droit), sans portee ni ligne de vue, dans le monde du joueur")
                .define("carte", true);
        b.pop();
        SERVEUR_SPEC = b.build();
    }

    // ------------------------------------------------------------------ client
    public static final ForgeConfigSpec CLIENT_SPEC;
    /** Volume du son d'un ping (0 a 100 %). */
    public static final ForgeConfigSpec.IntValue VOLUME;
    /** Taille des marqueurs (50 a 160 %). */
    public static final ForgeConfigSpec.IntValue TAILLE;
    /** Toujours afficher le nom de l'auteur (sinon : les 3 premieres secondes et sous le viseur). */
    public static final ForgeConfigSpec.BooleanValue NOMS_TOUJOURS;
    /** Fleches au bord de l'ecran pour les pings hors champ. */
    public static final ForgeConfigSpec.BooleanValue FLECHES;
    /** Pings affiches au plus (les plus proches). */
    public static final ForgeConfigSpec.IntValue MAX_VISIBLES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("HeraliZ Ping : affichage (ecran Reglages : Mods > HeraliZ Ping > Config, ou la touche Reglages des pings)").push("affichage");
        VOLUME = b.comment("Volume du son d'un ping, en %").defineInRange("volume", 60, 0, 100);
        TAILLE = b.comment("Taille des marqueurs, en %").defineInRange("taille", 100, 50, 160);
        NOMS_TOUJOURS = b.comment("Toujours afficher le nom de l'auteur (sinon au debut et sous le viseur)").define("noms_toujours", false);
        FLECHES = b.comment("Fleches au bord de l'ecran pour les pings hors champ").define("fleches_bord", true);
        MAX_VISIBLES = b.comment("Pings affiches au plus (les plus proches)").defineInRange("max_visibles", 12, 4, 32);
        b.pop();
        CLIENT_SPEC = b.build();
    }
}
