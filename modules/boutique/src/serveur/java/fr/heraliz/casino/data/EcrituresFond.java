package fr.heraliz.casino.data;

import javax.annotation.Nullable;
import java.nio.file.Path;

/**
 * Écritures des données du casino sur un fil à part (27/10, perf : la comptabilité du casino, 120 jours de mises et de
 * joueurs par machine, était compressée et écrite sur le fil serveur à chaque sauvegarde automatique). Le contenu est
 * relevé sur le fil serveur dans un NBT neuf (jamais partagé avec le jeu) ; seuls la compression, l'écriture et le
 * renommage partent ailleurs, dans l'ordre d'arrivée. Plusieurs relevés d'un même fichier en attente : seul le plus récent
 * est écrit. Arrêt du serveur : dès ServerStopping (donc pendant la dernière sauvegarde du monde), chaque écriture est
 * attendue, puis tout est attendu à ServerStopped (rien n'est perdu avant la fin du programme).
 * <p>19/11 (audit des lenteurs, C2) : la voie « casino » de l'écrivain en fond commun du noyau
 * ({@link fr.heraliz.noyau.api.EcrituresFond}) fait le travail (avant : un fil à ce module, même méthode).
 */
public final class EcrituresFond {
    private EcrituresFond() {
    }

    /** Une écriture à faire sur le fil d'écriture (ne lit jamais d'objet du jeu). */
    @FunctionalInterface
    public interface Ecriture {
        void ecrire() throws Exception;
    }

    private static final fr.heraliz.noyau.api.EcrituresFond.Voie VOIE = fr.heraliz.noyau.api.EcrituresFond.voie("casino");

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

    /**
     * Met en file une écriture qui ne remplace aucune autre (27/10 : lignes du journal du compte de capsules, toutes
     * gardées, dans l'ordre d'arrivée). À l'arrêt du serveur, attend la fin de l'écriture.
     */
    public static void ajouter(Path f, Ecriture ecriture) {
        VOIE.ajouter(f, ecriture::ecrire);
    }

    /** Attend la fin de toutes les écritures en file (arrêt du serveur), 60 s au plus. */
    public static void attendre() {
        VOIE.attendre();
    }
}
