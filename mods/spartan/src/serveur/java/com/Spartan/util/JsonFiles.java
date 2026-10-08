package com.Spartan.util;

import com.Spartan.Spartan;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Écriture atomique (fichier temporaire puis renommage) : jamais de JSON à moitié écrit. */
public final class JsonFiles {
    private JsonFiles() {
    }

    /** Écrit en asynchrone. Le contenu doit être déjà sérialisé (sur le thread serveur). */
    public static void writeAsync(Path target, String content) {
        AsyncIO.submit(() -> write(target, content));
    }

    public static void write(Path target, String content) {
        try {
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Spartan.LOGGER.error("[Spartan] Impossible d'écrire " + target, e);
        }
    }

    /**
     * Lit un fichier texte. Renvoie null s'il est ABSENT ; lève IOException s'il
     * existe mais est illisible (à ne surtout pas confondre avec « absent » :
     * on écraserait des données). Lecture tolérante sur l'encodage.
     */
    public static String readOrNull(Path file) throws IOException {
        if (!Files.exists(file)) {
            return null;
        }
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /**
     * Met de côté un fichier illisible ou corrompu (renommé en .corrupt-…) pour
     * qu'il ne soit jamais écrasé et puisse être réparé à la main.
     */
    public static void quarantine(Path file, Exception cause) {
        Path dest = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(file, dest);
            Spartan.LOGGER.error("[Spartan] {} est illisible ({}). Fichier mis de côté : {}. "
                    + "Spartan repart d'une liste vide : réparez le fichier puis redémarrez.", file, cause.getMessage(), dest);
        } catch (IOException e) {
            Spartan.LOGGER.error("[Spartan] {} est illisible et n'a pas pu être mis de côté", file, e);
        }
    }

    /** Renomme un fichier source après une migration réussie (pour ne pas la refaire). */
    public static void markMigrated(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".migre"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Spartan.LOGGER.warn("[Spartan] Impossible de renommer {} après migration : {}", file, e.getMessage());
        }
    }
}
