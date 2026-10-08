package com.Spartan.integrity;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Calcul de l'empreinte du dossier mods côté client, en arrière-plan : avec
 * ~100 mods, lire tous les jars prend une à deux secondes, ce qui ne doit
 * jamais figer le jeu. Lancé dès l'écran titre, prêt bien avant la connexion.
 *
 * L'inventaire (empreinte de chaque jar) est gardé : le contrôle d'intégrité
 * l'envoie au serveur sans relire les fichiers.
 */
public final class ModsHasher {
    private record Result(List<LaunchToken.ModFile> inventory, String hash) {
    }

    private static CompletableFuture<Result> future;

    private ModsHasher() {
    }

    public static synchronized void startIfNeeded(Path modsDir) {
        if (future != null) {
            return;
        }
        future = CompletableFuture.supplyAsync(() -> {
            try {
                List<LaunchToken.ModFile> inventory = LaunchToken.modsInventory(modsDir);
                return new Result(inventory, LaunchToken.hashOf(inventory));
            } catch (Exception e) {
                return new Result(null, "error");
            }
        });
    }

    /** L'empreinte si elle est prête, sinon null. */
    public static synchronized String getIfReady() {
        return future != null && future.isDone() ? future.join().hash() : null;
    }

    /** L'inventaire si prêt et lisible, sinon null. */
    public static synchronized List<LaunchToken.ModFile> inventoryIfReady() {
        return future != null && future.isDone() ? future.join().inventory() : null;
    }
}
