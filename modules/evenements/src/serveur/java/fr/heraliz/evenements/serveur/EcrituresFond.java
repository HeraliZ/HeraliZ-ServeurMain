package fr.heraliz.evenements.serveur;

import javax.annotation.Nullable;
import java.nio.file.Path;

/**
 * Écritures des fichiers des événements sur un fil à part (27/10, perf : etat.json, l'état de l'hélico et le journal
 * écrits sur le fil serveur gelaient le jeu à chaque passage du planning). Le contenu est relevé sur le fil serveur dans
 * un objet neuf (arbre JSON ou texte, jamais partagé avec le jeu) ; seuls la mise en texte, l'écriture et le renommage
 * partent ailleurs, dans l'ordre d'arrivée. Plusieurs relevés d'un même fichier en attente : seul le plus récent est écrit.
 * Ce qui doit être sur le disque AVANT de continuer (événement récompensé, numéro d'événement) attend sa fin
 * ({@link #attendre}). Arrêt du serveur : dès ServerStopping, chaque écriture est attendue, puis tout est attendu à
 * ServerStopped (rien n'est perdu avant la fin du programme).
 * <p>19/11 (audit des lenteurs, C2) : la voie « evenements » de l'écrivain en fond commun du noyau
 * ({@link fr.heraliz.noyau.api.EcrituresFond}) fait le travail (avant : un fil à ce module, même méthode). Attendre cette
 * voie n'attend jamais les écritures des autres modules.
 */
final class EcrituresFond {
    private EcrituresFond() {
    }

    /** Une écriture à faire sur le fil d'écriture (ne lit jamais d'objet du jeu). */
    @FunctionalInterface
    interface Ecriture {
        void ecrire() throws Exception;
    }

    private static final fr.heraliz.noyau.api.EcrituresFond.Voie VOIE = fr.heraliz.noyau.api.EcrituresFond.voie("evenements");

    /** Une fois, au chargement de la partie serveur. */
    static void init() {
        fr.heraliz.noyau.api.EcrituresFond.brancher();
    }

    /**
     * Met en file l'écriture du fichier {@code f} ; {@code siEchec} (appelé sur le fil d'écriture : seulement poser un
     * drapeau) redonne la donnée « à sauver ». À l'arrêt du serveur, attend la fin de l'écriture.
     */
    static void ecrire(Path f, Ecriture ecriture, @Nullable Runnable siEchec) {
        VOIE.ecrire(f, ecriture::ecrire, siEchec);
    }

    /** Met en file une écriture qui ne se remplace pas (ajout à un journal), dans l'ordre d'arrivée. */
    static void ajouter(Path f, Ecriture ecriture) {
        VOIE.ajouter(f, ecriture::ecrire);
    }

    /** Attend la fin de toutes les écritures en file (arrêt du serveur, fichier qui doit être sur le disque), 60 s au plus. */
    static void attendre() {
        VOIE.attendre();
    }
}
