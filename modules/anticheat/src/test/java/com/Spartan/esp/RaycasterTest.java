package com.Spartan.esp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RaycasterTest {
    @Test
    void emptyWorldNeverBlocks() {
        Raycaster rc = new Raycaster(new Grid());
        assertFalse(rc.blocked(0.5, 64.5, 0.5, 100.3, 70.1, -40.7));
        assertFalse(rc.blocked(-3.2, 1.1, 7.9, -3.2, 1.1, 7.9));
    }

    @Test
    void wallBlocksOnlyRaysThatCrossIt() {
        Grid g = new Grid().fill(5, 0, -10, 5, 10, 10);
        Raycaster rc = new Raycaster(g);
        assertTrue(rc.blocked(0.5, 1.6, 0.5, 9.5, 1.6, 0.5));
        assertTrue(rc.blocked(9.5, 1.6, 0.5, 0.5, 1.6, 0.5)); // sens inverse
        assertTrue(rc.blocked(0.5, 1.6, -3.5, 9.5, 2.6, 4.5)); // en diagonale
        assertFalse(rc.blocked(0.5, 1.6, 0.5, 4.9, 1.6, 0.5)); // s'arrête avant le mur
        assertFalse(rc.blocked(0.5, 12.5, 0.5, 9.5, 12.5, 0.5)); // passe au-dessus
    }

    @Test
    void startingInsideAFullBlockDoesNotBlock() {
        Grid g = new Grid().full(0, 1, 0);
        Raycaster rc = new Raycaster(g);
        assertFalse(rc.blocked(0.5, 1.6, 0.5, 6.5, 1.6, 0.5));
        assertTrue(rc.blocked(6.5, 1.6, 0.5, 0.5, 1.6, 0.5)); // mais il bloque qui le traverse en venant d'ailleurs
    }

    @Test
    void partialBlocksBlockOnlyWhereTheyAre() {
        // Dalle du bas en (5, 0, 0) : boîte locale y ∈ [0 ; 0,5].
        Grid g = new Grid().boxes(5, 0, 0, 0, 0, 0, 1, 0.5, 1);
        Raycaster rc = new Raycaster(g);
        assertTrue(rc.blocked(0.5, 0.25, 0.5, 9.5, 0.25, 0.5));
        assertFalse(rc.blocked(0.5, 0.75, 0.5, 9.5, 0.75, 0.5));
        assertFalse(rc.blocked(0.5, 0.5, 0.5, 9.5, 0.5, 0.5)); // frôle la face du dessus : on montre
    }

    @Test
    void closedDoorBlocksButOpenDoorletsTheRayThrough() {
        // Porte fermée : plaque de 3/16 au nord du bloc (z ∈ [0 ; 0,1875]).
        Grid closed = new Grid().boxes(5, 1, 0, 0, 0, 0, 1, 1, 0.1875).boxes(5, 2, 0, 0, 0, 0, 1, 1, 0.1875);
        assertTrue(new Raycaster(closed).blocked(5.5, 1.6, -4.5, 5.5, 1.6, 4.5));
        // Porte ouverte : plaque contre le côté ouest (x ∈ [0 ; 0,1875]).
        Grid open = new Grid().boxes(5, 1, 0, 0, 0, 0, 0.1875, 1, 1).boxes(5, 2, 0, 0, 0, 0, 0.1875, 1, 1);
        assertFalse(new Raycaster(open).blocked(5.5, 1.6, -4.5, 5.5, 1.6, 4.5));
    }

    @Test
    void endBlockCounts() {
        Grid g = new Grid().full(5, 1, 0);
        assertTrue(new Raycaster(g).blocked(0.5, 1.5, 0.5, 5.5, 1.5, 0.5));
    }

    @Test
    void axisAlignedAndVerticalRays() {
        Grid g = new Grid().full(0, 5, 0);
        Raycaster rc = new Raycaster(g);
        assertTrue(rc.blocked(0.5, 0.5, 0.5, 0.5, 9.5, 0.5));
        assertFalse(rc.blocked(1.5, 0.5, 0.5, 1.5, 9.5, 0.5));
        assertTrue(rc.blocked(0.5, 9.5, 0.5, 0.5, 0.5, 0.5));
    }

    @Test
    void negativeCoordinates() {
        Grid g = new Grid().fill(-6, 0, -6, -6, 5, 6);
        Raycaster rc = new Raycaster(g);
        assertTrue(rc.blocked(-0.5, 1.6, -0.5, -10.5, 1.6, -0.5));
        assertFalse(rc.blocked(-0.5, 1.6, -0.5, -4.9, 1.6, -0.5)); // le mur occupe x ∈ [-6 ; -5)
    }
}
