package fr.heraliz.loot;

import javax.annotation.Nullable;
import java.nio.file.Path;

/**
 * Écritures des fichiers de HeraliZ Loot sur un fil à part (27/10, perf : la rotation du loot, des milliers de conteneurs,
 * était compressée et écrite sur le fil serveur à chaque sauvegarde automatique, et l'état du butin des zombies toutes
 * les minutes). Le contenu est relevé sur le fil serveur dans un objet neuf (NBT ou arbre JSON, jamais partagé avec le
 * jeu) ; seuls la mise en texte ou la compression, l'écriture et le renommage partent ailleurs, dans l'ordre d'arrivée.
 * Plusieurs relevés d'un même fichier en attente : seul le plus récent est écrit. Arrêt du serveur : dès ServerStopping
 * (donc pendant la dernière sauvegarde du monde), chaque écriture est attendue, puis tout est attendu à ServerStopped (rien
 * n'est perdu avant la fin du programme).
 * <p>19/11 (audit des lenteurs, C2) : la voie « loot » de l'écrivain en fond commun du noyau
 * ({@link fr.heraliz.noyau.api.EcrituresFond}) fait le travail (avant : un fil à ce module, même méthode).
 */
final class EcrituresFond {
    private EcrituresFond() {
    }

    /** Une écriture à faire sur le fil d'écriture (ne lit jamais d'objet du jeu). */
    @FunctionalInterface
    interface Ecriture {
        void ecrire() throws Exception;
    }

    private static final fr.heraliz.noyau.api.EcrituresFond.Voie VOIE = fr.heraliz.noyau.api.EcrituresFond.voie("loot");

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

    /** Attend la fin de toutes les écritures en file (arrêt du serveur, lecture d'un fichier juste écrit), 60 s au plus. */
    static void attendre() {
        VOIE.attendre();
    }
}
