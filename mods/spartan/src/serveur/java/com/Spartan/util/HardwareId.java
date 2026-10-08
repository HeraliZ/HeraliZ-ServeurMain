package com.Spartan.util;

import java.util.Locale;
import java.util.Set;

/** Reconnaissance des identifiants matériels « de remplissage » (génériques). */
public final class HardwareId {
    /**
     * UUID renvoyés par beaucoup de cartes mères bas de gamme : identiques sur des
     * milliers de PC, ils ne doivent jamais servir à bannir (sinon un ban toucherait
     * tous ces joueurs).
     */
    private static final Set<String> PLACEHOLDERS = Set.of(
            "03000200-0400-0500-0006-000700080009",
            "00000000-0000-0000-0000-000000000000",
            "ffffffff-ffff-ffff-ffff-ffffffffffff",
            "00020003-0004-0005-0006-000700080009",
            "12345678-1234-5678-90ab-cddeefaabbcc",
            "4c4c4544-0000-0000-0000-000000000000");

    private HardwareId() {
    }

    /** Valeur générique connue, ou suite trop répétitive (0000…, 1111…, etc.). */
    public static boolean isPlaceholder(String serial) {
        if (serial == null) {
            return true;
        }
        String lower = serial.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty() || lower.equals("unknown") || lower.equals("error") || PLACEHOLDERS.contains(lower)) {
            return true;
        }
        return lower.replace("-", "").chars().distinct().count() <= 2;
    }
}
