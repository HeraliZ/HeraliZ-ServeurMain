package com.Spartan.scan;

import java.nio.file.Path;
import java.util.List;

/**
 * Contrôle d'intégrité exécuté DANS ce jeu (commande de diagnostic /spartan selfscan). Le code de contrôle n'existe que
 * dans la partie client (jar -client et -complet) : elle branche ici son collecteur. Sur le serveur dédié (jar -serveur),
 * il n'y en a pas et la commande le dit.
 */
public final class CollecteLocale {
    /** Collecteur du jeu courant (null sur le serveur dédié). */
    public static volatile Collecteur COLLECTEUR;

    private CollecteLocale() {
    }

    @FunctionalInterface
    public interface Collecteur {
        ScanReport collecter(Path modsDir, Path gameDir, Thread watched, List<String> watchClasses,
                             List<String> classProbes, List<String> processPatterns, List<String> nativePatterns);
    }
}
