package com.Spartan.integrity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Jeton de lancement émis par le launcher officiel, et empreinte du dossier mods.
 *
 * Java pur (aucune dépendance Minecraft) : ce fichier peut être copié tel quel
 * dans un launcher Java, et il est testé unitairement. Le format est décrit
 * dans docs/LAUNCHER.md (avec une implémentation JavaScript équivalente).
 *
 * Jeton v1 : base64url(payload) + "." + base64url(HMAC-SHA256(secret, payload))
 * payload  : "v1|<base64url(pseudo)>|<expiration epoch secondes>|<empreinte mods>|<aléa>"
 * Le pseudo est encodé : en crack, un pseudo peut contenir « | ».
 */
public final class LaunchToken {
    public static final String VERSION = "v1";
    /** Durée de vie maximale acceptée d'un jeton (anti-jeton quasi éternel). */
    public static final long MAX_LIFETIME_S = 30L * 24 * 3600;
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private LaunchToken() {
    }

    /** Résultat d'une vérification. */
    public record Result(boolean valid, String reason, String pseudo, long expires, String modsHash) {
        static Result fail(String reason) {
            return new Result(false, reason, null, 0, null);
        }
    }

    /** Création d'un jeton (côté launcher ou serveur d'authentification). */
    public static String create(String secret, String pseudo, long expiresEpochSeconds, String modsHash) {
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        String payload = String.join("|", VERSION,
                B64.encodeToString(pseudo.getBytes(StandardCharsets.UTF_8)),
                Long.toString(expiresEpochSeconds), modsHash, HexFormat.of().formatHex(nonce));
        byte[] p = payload.getBytes(StandardCharsets.UTF_8);
        return B64.encodeToString(p) + "." + B64.encodeToString(hmac(secret, p));
    }

    /** Vérification d'un jeton (côté serveur). */
    public static Result verify(String secret, String token, String expectedPseudo, long nowEpochSeconds) {
        if (token == null || token.isBlank()) {
            return Result.fail("jeton absent");
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1 || token.indexOf('.', dot + 1) >= 0) {
            return Result.fail("jeton mal formé");
        }
        byte[] payload;
        byte[] sig;
        try {
            payload = B64D.decode(token.substring(0, dot));
            sig = B64D.decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            return Result.fail("jeton mal formé");
        }
        if (!MessageDigest.isEqual(hmac(secret, payload), sig)) {
            return Result.fail("signature invalide");
        }
        String[] parts = new String(payload, StandardCharsets.UTF_8).split("\\|", -1);
        if (parts.length != 5 || !VERSION.equals(parts[0])) {
            return Result.fail("version de jeton inconnue");
        }
        String pseudo;
        try {
            pseudo = new String(B64D.decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Result.fail("jeton mal formé");
        }
        if (!parts[2].matches("\\d{1,12}")) {
            return Result.fail("expiration illisible");
        }
        long expires = Long.parseLong(parts[2]);
        // Sensible à la casse : en crack, « Steve » et « steve » sont deux comptes différents.
        if (!pseudo.equals(expectedPseudo)) {
            return Result.fail("jeton émis pour un autre pseudo");
        }
        if (nowEpochSeconds >= expires) {
            return Result.fail("jeton expiré (relancez le jeu depuis le launcher)");
        }
        if (expires - nowEpochSeconds > MAX_LIFETIME_S) {
            return Result.fail("durée de validité du jeton trop longue");
        }
        return new Result(true, "ok", pseudo, expires, parts[3]);
    }

    /** Une entrée « .jar » du dossier mods : fichier, ou dossier nommé .jar (mod « éclaté »). */
    public record ModFile(String name, boolean directory, long size, String sha256) {
        String line() {
            return directory ? name + ":dir:" + sha256 : name + ":" + sha256;
        }
    }

    /**
     * Empreinte du dossier mods : pour chaque entrée « .jar » du dossier (non récursif),
     * la ligne "nom-en-minuscules:sha256hex" (fichier) ou "nom:dir:sha256hex" (dossier,
     * voir sha256Dir) ; lignes triées, jointes par "\n", puis SHA-256 hexadécimal (UTF-8).
     */
    public static String modsHash(Path modsDir) throws IOException {
        return hashOf(modsInventory(modsDir));
    }

    /** Empreinte d'un inventaire déjà calculé (même résultat que modsHash). */
    public static String hashOf(List<ModFile> inventory) {
        List<String> lines = new ArrayList<>();
        for (ModFile f : inventory) {
            lines.add(f.line());
        }
        lines.sort(null);
        return sha256Hex(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    /** Entrées « .jar » du dossier mods avec l'empreinte de chacune (ordre du disque). */
    public static List<ModFile> modsInventory(Path modsDir) throws IOException {
        List<ModFile> out = new ArrayList<>();
        if (Files.isDirectory(modsDir)) {
            try (Stream<Path> files = Files.list(modsDir)) {
                for (Path f : files.toList()) {
                    String name = f.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!name.endsWith(".jar")) {
                        continue;
                    }
                    if (Files.isRegularFile(f)) {
                        out.add(new ModFile(name, false, Files.size(f), sha256File(f)));
                    } else if (Files.isDirectory(f)) {
                        // Mod « éclaté » (dossier nommé .jar) : Forge peut aussi le charger.
                        out.add(new ModFile(name, true, 0, sha256Dir(f)));
                    }
                }
            }
        }
        return out;
    }

    /**
     * Empreinte d'un dossier : lignes « chemin-relatif:sha256 » de tous ses fichiers
     * (récursif, liens vers des dossiers non suivis), triées, jointes par « \n ».
     */
    private static String sha256Dir(Path dir) throws IOException {
        List<String> lines = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path f : walk.toList()) {
                if (!f.equals(dir) && Files.isRegularFile(f)) {
                    String rel = dir.relativize(f).toString().replace('\\', '/');
                    lines.add(rel + ":" + sha256File(f));
                }
            }
        }
        lines.sort(null);
        return sha256Hex(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256File(Path f) throws IOException {
        MessageDigest sha = sha256();
        byte[] buf = new byte[1 << 16];
        try (InputStream in = Files.newInputStream(f)) {
            int r;
            while ((r = in.read(buf)) > 0) {
                sha.update(buf, 0, r);
            }
        }
        return HexFormat.of().formatHex(sha.digest());
    }

    private static String sha256Hex(byte[] data) {
        return HexFormat.of().formatHex(sha256().digest(data));
    }

    private static byte[] hmac(String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
