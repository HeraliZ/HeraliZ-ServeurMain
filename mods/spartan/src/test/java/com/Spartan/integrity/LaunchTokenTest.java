package com.Spartan.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LaunchTokenTest {
    private static final String SECRET = "un-secret-de-test-assez-long-0123456789";
    private static final long NOW = 1_800_000_000L;
    private static final String HASH = "ab".repeat(32);

    @Test
    void validTokenIsAccepted() {
        String t = LaunchToken.create(SECRET, "Steve", NOW + 3600, HASH);
        LaunchToken.Result r = LaunchToken.verify(SECRET, t, "Steve", NOW);
        assertTrue(r.valid(), r.reason());
        assertEquals("Steve", r.pseudo());
        assertEquals(HASH, r.modsHash());
    }

    @Test
    void pseudoIsCaseSensitive() {
        // En crack, « Steve » et « steve » sont deux comptes (UUID différents).
        String t = LaunchToken.create(SECRET, "Steve", NOW + 3600, HASH);
        assertFalse(LaunchToken.verify(SECRET, t, "steve", NOW).valid());
    }

    @Test
    void pseudoWithSeparatorWorks() {
        String t = LaunchToken.create(SECRET, "a|b", NOW + 3600, HASH);
        LaunchToken.Result r = LaunchToken.verify(SECRET, t, "a|b", NOW);
        assertTrue(r.valid(), r.reason());
        assertEquals("a|b", r.pseudo());
    }

    @Test
    void tooLongLifetimeIsRejected() {
        String t = LaunchToken.create(SECRET, "Steve", NOW + LaunchToken.MAX_LIFETIME_S + 10, HASH);
        assertFalse(LaunchToken.verify(SECRET, t, "Steve", NOW).valid());
    }

    @Test
    void wrongSecretIsRejected() {
        String t = LaunchToken.create("autre-secret", "Steve", NOW + 3600, HASH);
        assertEquals("signature invalide", LaunchToken.verify(SECRET, t, "Steve", NOW).reason());
    }

    @Test
    void tokenForAnotherPlayerIsRejected() {
        String t = LaunchToken.create(SECRET, "Steve", NOW + 3600, HASH);
        assertFalse(LaunchToken.verify(SECRET, t, "Alex", NOW).valid());
    }

    @Test
    void expiredTokenIsRejected() {
        String t = LaunchToken.create(SECRET, "Steve", NOW - 1, HASH);
        LaunchToken.Result r = LaunchToken.verify(SECRET, t, "Steve", NOW);
        assertFalse(r.valid());
        assertTrue(r.reason().startsWith("jeton expiré"), r.reason());
    }

    @Test
    void tamperedPayloadIsRejected() {
        String t = LaunchToken.create(SECRET, "Steve", NOW + 3600, HASH);
        String sig = t.substring(t.indexOf('.') + 1);
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("v1|Steve|" + (NOW + 999_999) + "|" + HASH + "|00").getBytes(StandardCharsets.UTF_8));
        assertEquals("signature invalide", LaunchToken.verify(SECRET, forged + "." + sig, "Steve", NOW).reason());
    }

    @Test
    void malformedTokensAreRejected() {
        assertFalse(LaunchToken.verify(SECRET, "", "Steve", NOW).valid());
        assertFalse(LaunchToken.verify(SECRET, "pas-de-point", "Steve", NOW).valid());
        assertFalse(LaunchToken.verify(SECRET, "a.b.c", "Steve", NOW).valid());
        assertFalse(LaunchToken.verify(SECRET, "!!!.???", "Steve", NOW).valid());
    }

    @Test
    void modsHashIsDeterministicAndDetectsChanges(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("B.jar"), "contenu b");
        Files.writeString(dir.resolve("a.jar"), "contenu a");
        Files.writeString(dir.resolve("notes.txt"), "ignoré");
        String h1 = LaunchToken.modsHash(dir);
        assertEquals(h1, LaunchToken.modsHash(dir));
        assertEquals(64, h1.length());

        Files.writeString(dir.resolve("autre.txt"), "toujours ignoré");
        assertEquals(h1, LaunchToken.modsHash(dir), "les fichiers non-jar sont ignorés");

        Files.writeString(dir.resolve("cheat.jar"), "triche");
        String h2 = LaunchToken.modsHash(dir);
        assertNotEquals(h1, h2, "un jar ajouté change l'empreinte");

        Files.delete(dir.resolve("cheat.jar"));
        Files.writeString(dir.resolve("a.jar"), "contenu a modifié");
        assertNotEquals(h1, LaunchToken.modsHash(dir), "un jar modifié change l'empreinte");
    }

    @Test
    void directoryJarIsIncluded(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.jar"), "contenu a");
        String h1 = LaunchToken.modsHash(dir);
        Path exploded = Files.createDirectories(dir.resolve("eclate.jar").resolve("pkg"));
        Files.writeString(exploded.resolve("Cheat.class"), "octets");
        String h2 = LaunchToken.modsHash(dir);
        assertNotEquals(h1, h2, "un mod « éclaté » (dossier .jar) change l'empreinte");
        Files.writeString(exploded.resolve("Cheat.class"), "autres octets");
        assertNotEquals(h2, LaunchToken.modsHash(dir), "modifier son contenu change l'empreinte");
    }

    @Test
    void emptyOrMissingModsDirHasStableHash(@TempDir Path dir) throws Exception {
        assertEquals(LaunchToken.modsHash(dir), LaunchToken.modsHash(dir.resolve("absent")));
    }
}
