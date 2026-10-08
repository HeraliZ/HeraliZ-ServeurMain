package fr.heraliz.ping.server;

import fr.heraliz.ping.PingType;

/**
 * Les regles de decision des pings, en pur calcul (aucune classe du jeu : testees hors du jeu par ReglesTest).
 * {@link Cercles} et {@link Contexte} rassemblent les faits (equipe, clan, monde, spectateur...) et appliquent ces regles.
 */
public final class Regles {
    private Regles() {
    }

    /** D'ou vient le cercle d'un auteur : equipe (en match), clan, groupe, ou lui seul. Dans cet ordre de priorite. */
    public enum Origine { EQUIPE, CLAN, GROUPE, SEUL }

    /**
     * Cercle d'un auteur. {@code enMatch} l'emporte sur tout (en match, seule l'equipe compte) ; puis le clan ; puis le
     * groupe s'il est permis hors match ; sinon lui seul.
     */
    public static Origine origine(boolean enMatch, boolean aUnClan, boolean aUnGroupe, boolean groupeHorsMatch) {
        if (enMatch) return Origine.EQUIPE;
        if (aUnClan) return Origine.CLAN;
        if (aUnGroupe && groupeHorsMatch) return Origine.GROUPE;
        return Origine.SEUL;
    }

    /**
     * Hors match : un membre du cercle (clan ou groupe) recoit-il le ping ? L'auteur, toujours. Les autres : connectes,
     * dans le meme monde, pas en match eux-memes (ils jouent ailleurs), et pas spectateurs sauf reglage.
     */
    public static boolean recoitHorsMatch(boolean estAuteur, boolean deconnecte, boolean memeMonde, boolean spectateur, boolean enMatch,
                                          boolean spectateursVoient) {
        if (estAuteur) return true;
        return !deconnecte && memeMonde && !enMatch && (spectateursVoient || !spectateur);
    }

    /**
     * En match : un joueur de l'equipe de l'auteur recoit-il le ping ? Connecte, meme monde, meme partie, et pas
     * spectateur (elimine) sauf reglage. Un joueur d'une autre equipe n'est jamais dans la liste.
     */
    public static boolean recoitEnMatch(boolean estAuteur, boolean deconnecte, boolean memeMonde, boolean memePartie, boolean spectateur,
                                        boolean spectateursVoient) {
        if (estAuteur) return true;
        return !deconnecte && memeMonde && memePartie && (spectateursVoient || !spectateur);
    }

    /** Un spectateur (mode spectateur, ou elimine de sa partie) ne pingue jamais. */
    public static boolean peutPinguer(boolean vivant, boolean spectateur) {
        return vivant && !spectateur;
    }

    /** Nature de la cible d'un appui bref (ping automatique). */
    public enum Cible { OBJET_AU_SOL, JOUEUR, MONSTRE, AUTRE_ENTITE, CONTENANT, BLOC }

    /**
     * Type d'un appui bref : objet au sol ou contenant = Loot ; monstre = Ennemi ; joueur = Ennemi, sauf s'il est dans le
     * cercle de l'auteur (alors On y va) ; le reste = On y va.
     */
    public static PingType typeAuto(Cible cible, boolean allie) {
        return switch (cible) {
            case OBJET_AU_SOL, CONTENANT -> PingType.LOOT;
            case MONSTRE -> PingType.ENNEMI;
            case JOUEUR -> allie ? PingType.ALLER : PingType.ENNEMI;
            case AUTRE_ENTITE, BLOC -> PingType.ALLER;
        };
    }

    /** Distance au carre dans la portee permise (tolerance de 2 blocs : le joueur bouge entre la visee et l'arrivee). */
    public static boolean aPortee(double distanceCarre, int portee) {
        double p = portee + 2.0;
        return Double.isFinite(distanceCarre) && distanceCarre <= p * p;
    }
}
