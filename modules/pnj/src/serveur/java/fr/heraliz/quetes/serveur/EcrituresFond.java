package fr.heraliz.quetes.serveur;

import java.nio.file.Path;

/**
 * Écritures des progressions des joueurs (heraliz_quetes/joueurs/<uuid>.json) sur un fil à part (01/11, perf : toutes les
 * 30 s, chaque progression changée était écrite et renommée sur le fil du serveur, soit des dizaines de fichiers dans le
 * même tick avec 150 joueurs, plus à chaque déconnexion). Même méthode que HeraliZ Loot, Clans et Événements : le texte
 * est fait sur le fil du serveur (copie, jamais l'objet du jeu) ; seuls l'écriture et le renommage partent ailleurs, dans
 * l'ordre d'arrivée. Plusieurs textes d'un même fichier en attente : seul le plus récent est écrit. Ce qui doit être sur le
 * disque avant de continuer (quête payée) attend la fin ({@link #attendre}) ; la relecture d'un fichier attend aussi qu'il
 * soit écrit ({@link #occupe}). Arrêt du serveur : dès ServerStopping, chaque écriture est attendue, puis tout est attendu
 * à ServerStopped.
 * <p>19/11 (audit des lenteurs, C2) : la voie « quetes » de l'écrivain en fond commun du noyau
 * ({@link fr.heraliz.noyau.api.EcrituresFond}) fait le travail (avant : un fil à ce module, même méthode). Attendre cette
 * voie (quête payée) n'attend jamais les écritures des autres modules.
 */
final class EcrituresFond {
    private EcrituresFond() {
    }

    /** Une écriture à faire sur le fil d'écriture (ne lit jamais d'objet du jeu). */
    @FunctionalInterface
    interface Ecriture {
        void ecrire() throws Exception;
    }

    private static final fr.heraliz.noyau.api.EcrituresFond.Voie VOIE = fr.heraliz.noyau.api.EcrituresFond.voie("quetes");

    /** Une fois, au chargement de la partie serveur. */
    static void init() {
        fr.heraliz.noyau.api.EcrituresFond.brancher();
    }

    /** Met en file l'écriture du fichier {@code f} (remplace celle encore en attente). À l'arrêt du serveur, l'attend. */
    static void ecrire(Path f, Ecriture ecriture) {
        VOIE.ecrire(f, ecriture::ecrire);
    }

    /** Une écriture de {@code f} attend ou se fait : le fichier sur le disque n'est peut-être pas encore le dernier. */
    static boolean occupe(Path f) {
        return VOIE.occupe(f);
    }

    /** Attend la fin de toutes les écritures en file (quête payée, relecture, arrêt du serveur), 60 s au plus. */
    static void attendre() {
        VOIE.attendre();
    }
}
