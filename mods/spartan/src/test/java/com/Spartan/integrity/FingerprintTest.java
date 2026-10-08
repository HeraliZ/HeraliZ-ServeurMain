package com.Spartan.integrity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/**
 * Le défi d'intégrité couvre toutes les classes du jar joueur : la moindre modification, sur le disque OU en mémoire,
 * change la réponse ; et un joueur honnête répond toujours juste.
 */
class FingerprintTest {
    private static List<byte[]> fakeClasses(int n) {
        Random r = new Random(1);
        List<byte[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            byte[] b = new byte[200 + r.nextInt(3000)];
            r.nextBytes(b);
            out.add(b);
        }
        return out;
    }

    private static byte[][] fakeShapes(int n) {
        Random r = new Random(2);
        byte[][] out = new byte[n][32];
        for (byte[] b : out) {
            r.nextBytes(b);
        }
        return out;
    }

    @Test
    void everySingleClassIsCoveredByEachChallenge() {
        List<byte[]> clean = fakeClasses(173);
        byte[][] shapes = fakeShapes(clean.size());
        for (int variant = 0; variant < 8; variant++) {
            int[] idx = ChallengeManager.coverAll(clean.size());
            assertEquals(clean.size(), idx.length);
            assertEquals(clean.size(), Arrays.stream(idx).distinct().count(), "chaque classe une fois");
            long nonce = 0x5EED0000L + variant;
            byte[] expected = Fingerprint.answer(clean, shapes, nonce, variant, idx);
            for (int victim = 0; victim < clean.size(); victim++) {
                List<byte[]> patched = new ArrayList<>(clean);
                byte[] b = clean.get(victim).clone();
                b[b.length / 3] ^= 1; // un seul bit changé dans une seule classe
                patched.set(victim, b);
                assertFalse(MessageDigest.isEqual(expected, Fingerprint.answer(patched, shapes, nonce, variant, idx)),
                        "classe " + victim + " modifiée non vue (variante " + variant + ")");
            }
        }
    }

    @Test
    void aClassChangedOnlyInMemoryIsSeen() {
        List<byte[]> clean = fakeClasses(60);
        byte[][] shapes = fakeShapes(clean.size());
        int[] idx = ChallengeManager.coverAll(clean.size());
        for (int variant = 0; variant < 8; variant++) {
            byte[] expected = Fingerprint.answer(clean, shapes, 77L, variant, idx);
            for (int victim = 0; victim < clean.size(); victim++) {
                // jar intact sur le disque, mais une méthode ajoutée en mémoire (Mixin, ASM, agent)
                byte[][] live = shapes.clone();
                live[victim] = live[victim].clone();
                live[victim][5] ^= 1;
                assertFalse(MessageDigest.isEqual(expected, Fingerprint.answer(clean, live, 77L, variant, idx)),
                        "classe " + victim + " modifiée en mémoire non vue (variante " + variant + ")");
            }
        }
    }

    @Test
    void answersAreDeterministicButNeverReusable() {
        List<byte[]> clean = fakeClasses(50);
        byte[][] shapes = fakeShapes(clean.size());
        int[] idx = ChallengeManager.coverAll(clean.size());
        // Même défi : client et serveur calculent la même chose.
        assertArrayEquals(Fingerprint.answer(clean, shapes, 42L, 3, idx), Fingerprint.answer(clean, shapes, 42L, 3, idx));
        // Autre graine ou autre variante : une ancienne réponse ne sert à rien.
        assertFalse(Arrays.equals(Fingerprint.answer(clean, shapes, 42L, 3, idx), Fingerprint.answer(clean, shapes, 43L, 3, idx)));
        assertFalse(Arrays.equals(Fingerprint.answer(clean, shapes, 42L, 3, idx), Fingerprint.answer(clean, shapes, 42L, 5, idx)));
    }

    /**
     * Joueur honnête : la forme de chaque classe du jar joueur lue en mémoire (réflexion) est EXACTEMENT celle lue dans
     * son fichier par le serveur. Sinon tout le monde échouerait au défi.
     */
    @Test
    void liveShapeOfEveryPlayerClassMatchesItsFile() throws IOException {
        int checked = 0;
        for (String set : List.of("main", "client")) {
            Path root = Path.of("build/classes/java", set);
            assertTrue(Files.isDirectory(root), "classes compilées introuvables : " + root.toAbsolutePath());
            List<Path> files;
            try (Stream<Path> walk = Files.walk(root)) {
                files = walk.filter(p -> p.toString().endsWith(".class")).toList();
            }
            for (Path f : files) {
                String name = root.relativize(f).toString().replace('\\', '/').replace(".class", "").replace('/', '.');
                byte[] file = Fingerprint.fileShape(Files.readAllBytes(f));
                byte[] live = Fingerprint.liveShape(name);
                assertArrayEquals(file, live, "forme différente en mémoire et dans le fichier : " + name);
                checked++;
            }
        }
        assertTrue(checked > 20, "trop peu de classes vérifiées : " + checked);
    }

    @Test
    void embeddedCopyIsReadInTheSameOrderAsThePlayerJar() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            for (String n : List.of("com/Spartan/b/Z.class", "com/Spartan/A.class", "META-INF/mods.toml",
                    "com/Spartan/b/", "com/Spartan/a/Y.class", "autre/X.class")) {
                z.putNextEntry(new ZipEntry(n));
                if (!n.endsWith("/")) {
                    z.write(n.getBytes());
                }
                z.closeEntry();
            }
        }
        TreeMap<String, byte[]> got = Fingerprint.classesOf(new ByteArrayInputStream(b.toByteArray()));
        assertEquals(List.of("A.class", "a/Y.class", "b/Z.class"), new ArrayList<>(got.keySet()));
    }
}
