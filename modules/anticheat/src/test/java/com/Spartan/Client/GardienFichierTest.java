package com.Spartan.Client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Lecture du rapport du Gardien du launcher (20/11 : faite hors du fil du jeu, même résultat qu'avant). */
class GardienFichierTest {
    @TempDir
    Path dossier;

    @Test
    void rapportLuTelQuel() throws Exception {
        Path f = dossier.resolve("gardien.txt");
        Files.writeString(f, "v2|3|1000|4242|0123456789abcdef0123456789abcdef|ok|xenos|0\nSIGNATURE_base64url\n", StandardCharsets.UTF_8);
        assertArrayEquals(new String[]{"ok", "v2|3|1000|4242|0123456789abcdef0123456789abcdef|ok|xenos|0", "SIGNATURE_base64url"},
                Gardien.lire(f));
        // fins de ligne Windows, signature absente : la ligne seule
        Files.writeString(f, "v1|1|5|7|ok||0\r\n", StandardCharsets.UTF_8);
        assertArrayEquals(new String[]{"ok", "v1|1|5|7|ok||0", ""}, Gardien.lire(f));
    }

    @Test
    void fichierAbsentOuIllisible() throws Exception {
        assertArrayEquals(new String[]{"fichier", "", ""}, Gardien.lire(dossier.resolve("absent.txt")));
        Path d = Files.createDirectory(dossier.resolve("gardien.txt"));
        assertArrayEquals(new String[]{"lecture", "", ""}, Gardien.lire(d));
    }
}
