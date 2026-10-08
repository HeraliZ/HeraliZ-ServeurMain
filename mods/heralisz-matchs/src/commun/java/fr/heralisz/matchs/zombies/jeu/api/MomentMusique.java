package fr.heralisz.matchs.zombies.jeu.api;

/**
 * Moments musicaux d'une partie de zombies, envoyes au client ({@link EvenementsHud#musique}) et gardes comme etat
 * courant ({@link HudZombies#ambiance()}) : le jeu ne joue <b>aucune</b> musique lui-meme, c'est le module client
 * (agent contenu) qui choisit et lit les pistes (fond d'ambiance, musique de manche, « 115 » integree
 * {@code heralisz_matchs:zombies.musique_115}, emplacements {@code heralisz_matchs:zombies.musique_1..8} du dossier
 * {@code config/heralisz_matchs/musiques/}).
 * Les jingles courts du jeu ({@link ZombiesApi.Sons}) continuent d'etre joues par le serveur.
 */
public enum MomentMusique {
    /** Arrivee dans l'instance, intro et premiers pas (avant la manche 1) : fond d'ambiance calme. */
    PARTIE_DEBUT,
    /** Debut d'une manche normale (musique de manche / tension). */
    MANCHE_DEBUT,
    /** Fin d'une manche normale (pause : retour au fond d'ambiance). */
    MANCHE_FIN,
    /** Debut d'une manche speciale des Rodeurs. */
    SPECIALE_DEBUT,
    /** Fin d'une manche speciale. */
    SPECIALE_FIN,
    /** Courant retabli (accent musical). */
    COURANT,
    /** Une arme sort du Pack-a-Punch. */
    PACK_A_PUNCH,
    /** Easter egg termine : morceau cache ({@code easter_egg.ogg} de l'utilisateur, sinon « 115 » integree). */
    EASTER_EGG,
    /** Garde-fou : la manche est passee faute de kill (fin de manche anticipee). */
    MANCHE_PASSEE,
    /** Fin de partie (tous a terre) : musique de fin. */
    PARTIE_FIN,
    /** Retour au monde (Coffre rendu) : couper toute musique de zombies. */
    SILENCE,
    /**
     * The Giant : les 3 bocaux a moelle sont actives (easter egg musical) : {@code beauty_of_annihilation.ogg} de
     * l'utilisateur, sinon « 115 » integree. Ajoute a la fin (l'ordinal circule sur le reseau).
     */
    BEAUTY_OF_ANNIHILATION,
    /**
     * The Giant, acte IV : l'intro du boss N-07 commence (porte de confinement) : la playlist et le fond d'ambiance se
     * taisent, la scene a ses propres sons.
     */
    BOSS_INTRO,
    /**
     * Combat contre N-07 : musique du boss en boucle ({@code boss_n07.ogg} de l'utilisateur, sinon la musique du mod du
     * boss {@code heralisz_n07:music.sujet_n07}, sinon « 115 » integree).
     */
    BOSS_N07,
    /** Fin du combat (victoire, abandon) : la musique du boss s'arrete, la partie reprend sa musique. */
    BOSS_FIN;

    public static MomentMusique de(int ordinal) {
        MomentMusique[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : SILENCE;
    }
}
