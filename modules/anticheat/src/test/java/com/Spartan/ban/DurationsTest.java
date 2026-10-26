package com.Spartan.ban;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DurationsTest {
    @Test
    void parsesUnits() {
        assertEquals(30 * 60_000L, Durations.parse("30m"));
        assertEquals(12 * 3_600_000L, Durations.parse("12h"));
        assertEquals(7 * 86_400_000L, Durations.parse("7d"));
        assertEquals(7 * 86_400_000L, Durations.parse("7j"));
        assertEquals(14 * 86_400_000L, Durations.parse("2w"));
        assertEquals(14 * 86_400_000L, Durations.parse("2W"));
    }

    @Test
    void permanentAliases() {
        assertEquals(0, Durations.parse("def"));
        assertEquals(0, Durations.parse("perm"));
        assertEquals(0, Durations.parse("PERMANENT"));
        assertEquals(0, Durations.parse("definitif"));
    }

    @Test
    void rejectsInvalid() {
        assertEquals(Durations.INVALID, Durations.parse("5x"));
        assertEquals(Durations.INVALID, Durations.parse("h"));
        assertEquals(Durations.INVALID, Durations.parse("-3d"));
        assertEquals(Durations.INVALID, Durations.parse("0d"));
        assertEquals(Durations.INVALID, Durations.parse("abc"));
        assertEquals(Durations.INVALID, Durations.parse("999999d"));
        assertEquals(Durations.INVALID, Durations.parse("30s"), "s est ambigu : refusé");
    }
}
