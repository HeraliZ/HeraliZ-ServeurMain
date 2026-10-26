package fr.heraliz.crac.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Admin console sessions and login throttling, free of any game state so it can be unit tested. A session remembers
 * a fingerprint of the password it was opened with: changing the password, losing the operator level or disabling the
 * console ends it on the next check.
 */
public final class AdminAuth {
    public enum Result { OK, DISABLED, NO_PERMISSION, LOCKED, WRONG_PASSWORD }

    private static final String SHA256_PREFIX = "sha256:";

    private final Map<UUID, String> sessions = new HashMap<>();
    private final Map<UUID, Failures> failures = new HashMap<>();

    private static final class Failures {
        int count;
        long last;
        long lockedUntil;
    }

    /**
     * One login attempt. {@code permitted}: the player has the required operator level. After {@code maxFailures}
     * wrong passwords in a row the player is locked out for {@code lockoutMs}; failures older than that are forgotten.
     */
    public Result login(UUID id, boolean permitted, String configured, String attempt, long now, int maxFailures, long lockoutMs) {
        if (configured == null || configured.isBlank()) {
            return Result.DISABLED;
        }
        if (!permitted) {
            return Result.NO_PERMISSION;
        }
        Failures f = this.failures.get(id);
        if (f != null && now < f.lockedUntil) {
            return Result.LOCKED;
        }
        if (matches(configured, attempt)) {
            this.failures.remove(id);
            this.sessions.put(id, fingerprint(configured));
            return Result.OK;
        }
        if (f == null) {
            f = new Failures();
            this.failures.put(id, f);
        } else if (now - f.last > lockoutMs) {
            f.count = 0;
        }
        f.last = now;
        if (++f.count >= Math.max(1, maxFailures)) {
            f.count = 0;
            f.lockedUntil = now + lockoutMs;
        }
        return Result.WRONG_PASSWORD;
    }

    /** Milliseconds left on the player's lockout, 0 when none. */
    public long lockoutLeft(UUID id, long now) {
        Failures f = this.failures.get(id);
        return f == null ? 0L : Math.max(0L, f.lockedUntil - now);
    }

    /** Whether the player's session is still valid; an invalid session is dropped. */
    public boolean isAuthed(UUID id, boolean permitted, String configured) {
        String fp = this.sessions.get(id);
        if (fp == null) {
            return false;
        }
        if (!permitted || configured == null || configured.isBlank() || !fp.equals(fingerprint(configured))) {
            this.sessions.remove(id);
            return false;
        }
        return true;
    }

    public boolean hasSession(UUID id) {
        return this.sessions.containsKey(id);
    }

    public void logout(UUID id) {
        this.sessions.remove(id);
    }

    public void clear() {
        this.sessions.clear();
        this.failures.clear();
    }

    /** Constant-time check of {@code attempt} against the configured password or {@code sha256:<hex>} digest. */
    public static boolean matches(String configured, String attempt) {
        if (configured == null || configured.isBlank() || attempt == null) {
            return false;
        }
        byte[] given = attempt.getBytes(StandardCharsets.UTF_8);
        if (configured.regionMatches(true, 0, SHA256_PREFIX, 0, SHA256_PREFIX.length())) {
            byte[] expected;
            try {
                expected = HexFormat.of().parseHex(configured.substring(SHA256_PREFIX.length()).trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return false;
            }
            return expected.length == 32 && MessageDigest.isEqual(expected, sha256(given));
        }
        return MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8), given);
    }

    /** Identifies a password setting without keeping it in memory as plain text. */
    static String fingerprint(String configured) {
        return HexFormat.of().formatHex(sha256(configured.getBytes(StandardCharsets.UTF_8)));
    }

    static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
