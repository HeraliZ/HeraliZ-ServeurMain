package com.Spartan.check.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Une copie = un même numéro à deux endroits du même relevé ; jamais d'accusation sans copie. */
class DupeIndexTest {
    private static DupeIndex.Lieu lieu(String j, String e) {
        return new DupeIndex.Lieu(j, e);
    }

    @Test
    void oneItemPerPlaceIsNotADupe() {
        DupeIndex idx = new DupeIndex();
        idx.ajouter(new Object(), 11L, "tacz:modern_kinetic_gun", lieu("Steve", "inventaire 3"));
        idx.ajouter(new Object(), 22L, "tacz:modern_kinetic_gun", lieu("Alex", "inventaire 3"));
        idx.ajouter(new Object(), 33L, "backpacked:backpack", lieu("Steve", "Curios 0"));
        assertTrue(idx.copies().isEmpty());
        assertEquals(3, idx.taille());
    }

    @Test
    void sameNumberAtTwoPlacesIsACopy() {
        DupeIndex idx = new DupeIndex();
        idx.ajouter(new Object(), 42L, "tacz:modern_kinetic_gun", lieu("Steve", "inventaire 3"));
        idx.ajouter(new Object(), 42L, "tacz:modern_kinetic_gun", lieu("Alex", "sac (Curios 0) 7"));
        List<DupeIndex.Copie> c = idx.copies();
        assertEquals(1, c.size());
        assertEquals(42L, c.get(0).uid());
        assertEquals(2, c.get(0).lieux().size());
        assertEquals("Alex (sac (Curios 0) 7)", c.get(0).lieux().get(1).toString());
    }

    @Test
    void theSameStackSeenTwiceIsNotACopy() {
        DupeIndex idx = new DupeIndex();
        Object memePile = new Object();
        idx.ajouter(memePile, 7L, "heraliz_armory:gilet", lieu("Steve", "inventaire 1"));
        idx.ajouter(memePile, 7L, "heraliz_armory:gilet", lieu("Steve", "inventaire 1"));
        assertTrue(idx.copies().isEmpty());
    }

    @Test
    void itemsWithoutNumberAreIgnored() {
        DupeIndex idx = new DupeIndex();
        idx.ajouter(new Object(), 0L, "tacz:modern_kinetic_gun", lieu("Steve", "inventaire 0"));
        idx.ajouter(new Object(), 0L, "tacz:modern_kinetic_gun", lieu("Alex", "inventaire 0"));
        assertTrue(idx.copies().isEmpty());
    }

    @Test
    void trackedItemPatterns() {
        List<String> motifs = List.of("tacz:*", "backpacked:backpack");
        assertTrue(DupeIndex.suivi("tacz:modern_kinetic_gun", motifs));
        assertTrue(DupeIndex.suivi("backpacked:backpack", motifs));
        assertFalse(DupeIndex.suivi("backpacked:shelf", motifs));
        assertFalse(DupeIndex.suivi("minecraft:diamond", motifs));
        assertFalse(DupeIndex.suivi("taczaddon:x", motifs));
    }
}
