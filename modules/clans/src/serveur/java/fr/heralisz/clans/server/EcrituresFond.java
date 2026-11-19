package fr.heralisz.clans.server;

import javax.annotation.Nullable;
import java.nio.file.Path;

/**
 * Écritures des fichiers des clans et de la saison sur un fil à part (27/10, perf : clans.json, joueurs.json et la saison
 * écrits sur le fil serveur gelaient le jeu toutes les minutes, sans personne en ligne). Le contenu est relevé sur le fil
 * serveur dans un objet neuf (arbre JSON jamais partagé avec le jeu) ; seuls la mise en texte, l'écriture et le
 * renommage partent ailleurs, dans l'ordre d'arrivée. Plusieurs relevés d'un même fichier en attente : seul le plus récent
 * est écrit. Arrêt du serveur : dès ServerStopping, chaque écriture est attendue, puis tout est attendu à ServerStopped
 * (rien n'est perdu avant la fin du programme).
 * <p>19/11 (audit des lenteurs, C2) : la voie « clans » de l'écrivain en fond commun du noyau
 * ({@link fr.heraliz.noyau.api.EcrituresFond}) fait le travail (avant : un fil à ce module, même méthode). Attendre cette
 * voie n'attend jamais les écritures des autres modules (rotation du loot...).
 */
public final class EcrituresFond {
    private EcrituresFond() {
    }

    /** Une écriture à faire sur le fil d'écriture (ne lit jamais d'objet du jeu). */
    @FunctionalInterface
    public interface Ecriture {
        void ecrire() throws Exception;
    }

    private static final fr.heraliz.noyau.api.EcrituresFond.Voie VOIE = fr.heraliz.noyau.api.EcrituresFond.voie("clans");

    /** Une fois, au chargement de la partie serveur. */
    public static void init() {
        fr.heraliz.noyau.api.EcrituresFond.brancher();
    }

    /**
     * Met en file l'écriture du fichier {@code f} ; {@code siEchec} (appelé sur le fil d'écriture : seulement poser un
     * drapeau) redonne la donnée « à sauver ». À l'arrêt du serveur, attend la fin de l'écriture.
     */
    public static void ecrire(Path f, Ecriture ecriture, @Nullable Runnable siEchec) {
        VOIE.ecrire(f, ecriture::ecrire, siEchec);
    }

    /** Met en file une écriture qui ne se remplace pas (ajout à un journal), dans l'ordre d'arrivée. */
    public static void ajouter(Path f, Ecriture ecriture) {
        VOIE.ajouter(f, ecriture::ecrire);
    }

    /**
     * Fait {@code lecture} sur le fil d'écriture, après les écritures déjà en file (01/11 : lire un fichier juste écrit
     * sans faire attendre le fil du serveur). Ne touche à aucun objet du jeu ; la suite revient au serveur par
     * {@code server.execute}.
     */
    public static void puis(Runnable lecture) {
        VOIE.puis(lecture);
    }

    /** Attend la fin de toutes les écritures en file (arrêt du serveur, opération d'argent), 60 s au plus. */
    public static void attendre() {
        VOIE.attendre();
    }
}
