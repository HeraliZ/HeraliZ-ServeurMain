package com.Spartan.ban;

import java.util.Locale;

/**
 * Analyse des durées de ban : 30m, 12h, 7d (ou 7j), 2w, ou def / perm.
 * « s » est volontairement refusé : « 30s » serait lu « 30 secondes » par certains
 * et « 30 semaines » par d'autres.
 */
public final class Durations {
    public static final long INVALID = -1;

    private Durations() {
    }

    /** @return durée en ms, 0 pour définitif, INVALID si illisible. */
    public static long parse(String input) {
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.equals("def") || s.equals("perm") || s.equals("permanent") || s.equals("definitif")) {
            return 0;
        }
        if (s.length() < 2) {
            return INVALID;
        }
        char unit = s.charAt(s.length() - 1);
        long n;
        try {
            n = Long.parseLong(s.substring(0, s.length() - 1));
        } catch (NumberFormatException e) {
            return INVALID;
        }
        if (n <= 0 || n > 100_000) {
            return INVALID;
        }
        return switch (unit) {
            case 'm' -> n * 60_000L;
            case 'h' -> n * 3_600_000L;
            case 'd', 'j' -> n * 86_400_000L;
            case 'w' -> n * 7 * 86_400_000L;
            default -> INVALID;
        };
    }
}
