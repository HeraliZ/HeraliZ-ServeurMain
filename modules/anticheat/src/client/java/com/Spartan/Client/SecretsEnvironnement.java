package com.Spartan.Client;

import com.sun.jna.Platform;
import com.sun.jna.platform.unix.LibC;
import com.sun.jna.platform.win32.Kernel32;

/**
 * Secrets du lancement retirés de l'environnement du processus du jeu (27/10) : la clé des rapports du Gardien, le jeton
 * de lancement et l'accord des captures arrivent par des variables d'environnement (posées par le launcher seulement) ;
 * n'importe quel programme du même utilisateur pouvait les lire dans le processus du jeu (Process Explorer) et fabriquer
 * de faux rapports du Gardien. Java garde sa propre copie de l'environnement (lue au démarrage) : le jeu les lit toujours.
 * Windows : le bloc d'environnement du processus est vidé de ces noms. Linux / Mac : retirés de l'environnement de libc
 * (la copie de départ du système reste lisible par /proc sous Linux). Jamais d'erreur : sans JNA, rien n'est fait.
 */
public final class SecretsEnvironnement {
    private static final String[] NOMS = {"SPARTAN_GARDIEN_CLE", "SPARTAN_LAUNCH_TOKEN", "SPARTAN_CAPTURE_CONSENT"};

    private SecretsEnvironnement() {
    }

    public static void effacer() {
        try {
            System.getenv(); // la copie de Java est faite avant l'effacement
            for (String n : NOMS) {
                if (Platform.isWindows()) {
                    Kernel32.INSTANCE.SetEnvironmentVariable(n, null);
                } else if (Platform.isLinux() || Platform.isMac()) {
                    LibC.INSTANCE.unsetenv(n);
                }
            }
        } catch (Throwable ignore) {
            // JNA absent ou refusé : les variables restent, rien d'autre ne change
        }
    }
}
