package fr.heralisz.matchs.duels;

import java.util.List;

/**
 * Reglages des duels (MATCHS_SPEC §3). Valeurs du cahier des charges ; les durees sont en ticks (20 par seconde) sauf
 * mention. Constantes : un changement passe par une nouvelle version du mod.
 */
public final class DuelsConfig {
    private DuelsConfig() {
    }

    /** Validite d'un defi (ms). */
    public static final long DEFI_MS = 30_000L;
    /** Delai minimal entre deux defis lances par le meme joueur (ms). */
    public static final long DEFI_CADENCE_MS = 3_000L;
    /**
     * Commandes de reponse a un defi (les memes dans toutes les langues), suivies au besoin de l'id du defi : boutons du
     * chat (serveur) et rappel de la notification (client).
     */
    public static final String COMMANDE_ACCEPTER = "/duel accepter", COMMANDE_REFUSER = "/duel refuser";
    /** Carte par defaut (v1 : Nuketown). */
    public static final String CARTE_DEFAUT = "duel_nuketown";

    /** Choix de la classe (debut de manche en R&D, reapparition en match a mort). */
    public static final int CHOIX_TICKS = 5 * 20;
    /** En match a mort : apres une reapparition, un changement de classe recu dans ce delai re-equipe tout de suite. */
    public static final int GRACE_CLASSE_TICKS = 5 * 20;
    /** Attente de la pose de l'instance avant d'annuler le duel. */
    public static final int ATTENTE_MAX_TICKS = 60 * 20;
    /** Duree de l'ecran des scores avant le retour au Coffre. */
    public static final int FIN_TICKS = 12 * 20;

    // ------------------------------------------------------------------ recherche et destruction
    /** Duree d'une manche (2 min). */
    public static final int RD_MANCHE_TICKS = 120 * 20;
    /** Amorcage d'une bombe : saisie des 3 codes sur l'ordinateur, accroupi (5 s). */
    public static final int RD_POSE_TICKS = 5 * 20;
    /** Desamorcage = piratage de l'ordinateur, accroupi, l'ordinateur en main (6 s). */
    public static final int RD_DESAMORCAGE_TICKS = 6 * 20;
    /** Minuterie de la bombe amorcee (40 s). */
    public static final int RD_MINUTERIE_TICKS = 40 * 20;
    /** Distance a plat (blocs) du centre de la bombe jusqu'a laquelle le porteur peut l'amorcer. */
    public static final double RD_AMORCAGE_PORTEE = 2.8;
    /** Pendant l'amorcage, l'attaquant reste a cette distance (blocs, a plat) de l'ordinateur qu'il a ouvert. */
    public static final double RD_AMORCAGE_ECART = 1.6;
    /** Distance a plat (blocs) de l'ordinateur pose jusqu'a laquelle un defenseur accroupi le ramasse pour le pirater. */
    public static final double RD_PIRATAGE_PORTEE = 1.6;
    /** Longueur du cable (blocs, a plat depuis le centre de la bombe) : plus loin, le pirate lache l'ordinateur. */
    public static final double RD_CABLE = 4.8;
    /** Ecart de hauteur maximal (blocs) pour amorcer ou ramasser l'ordinateur. */
    public static final double RD_HAUTEUR = 1.5;
    /** Manches gagnantes (premier a 4). */
    public static final int RD_MANCHES_GAGNANTES = 4;
    /** Changement de cote apres ce nombre de manches jouees. */
    public static final int RD_CHANGEMENT_COTE = 3;
    /** Pause entre deux manches (killcam de la derniere elimination, annonces). */
    public static final int RD_INTERMANCHE_TICKS = 7 * 20;
    /** Distance de ramassage de l'ordinateur ferme au sol par un attaquant (blocs). */
    public static final double RD_RAMASSAGE = 1.6;

    // ------------------------------------------------------------------ match a mort
    /** Reapparition apres 3 s. */
    public static final int MAM_REAPPARITION_TICKS = 3 * 20;
    /** Eliminations pour gagner en 1v1. */
    public static final int MAM_OBJECTIF_1V1 = 20;
    /** Eliminations pour gagner en 2v2. */
    public static final int MAM_OBJECTIF_2V2 = 30;
    /** Temps limite (10 min). */
    public static final int MAM_DUREE_TICKS = 10 * 60 * 20;

    // ------------------------------------------------------------------ equipement
    /** Chargeurs de reserve virtuelle donnes avec chaque arme a feu. */
    public static final int CHARGEURS_RESERVE = 4;
    /**
     * Cles NBT retirees de tout objet donne en duel (camo et marques d'un camo a vie, decor, StatTrak, skins TaCZ et
     * heralisz_tactical).
     */
    public static final List<String> NBT_INTERDITS = List.of("HeraliszCamo", "HeraliszCamoProprio", "HeraliszCamoJeton", "HeraliszDecor",
            "HeraliszKills", "Skin", "GunDisplayId", "DisplayId");
    /** Espaces de noms exclus du catalogue (armes et armes de melee « skin », Wonder Weapons reservees aux zombies). */
    public static final List<String> ESPACES_EXCLUS = List.of("heralisz_skins", "heraliz_wonder");
    /** Types TaCZ exclus (lanceurs) ; les balles explosives sont exclues a part. */
    public static final List<String> TYPES_EXCLUS = List.of("rpg", "launcher", "grenade_launcher", "rocket");

    /** Couleurs des equipes (maison jaune, maison verte de Nuketown). */
    public static final int COULEUR_A = 0xE8C547, COULEUR_B = 0x6FBF73;

    /** Vrai dans le client de dev du banc d'essai (meme JVM que le serveur integre). */
    public static boolean banc() {
        return System.getProperty("heralisz.matchs.harness") != null;
    }
}
