package com.Spartan.esp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SightTest {
    private static final double EYE = 1.62;

    /** Joueur debout, pieds en (x, 0, z), immobile. */
    private static Sight.Body standing(double x, double z) {
        return new Sight.Body(x - 0.3, 0, z - 0.3, x + 0.3, 1.8, z + 0.3, 0, 0, 0);
    }

    private static Sight.Eye eye(double x, double z) {
        return new Sight.Eye(x, EYE, z, 0, 0, 0, true);
    }

    @Test
    void openFieldIsVisible() {
        Sight s = new Sight(new Raycaster(new Grid()));
        assertTrue(s.canSee(eye(0.5, 0.5), standing(40.5, 12.5), 4, 0.6, 0.6));
    }

    @Test
    void thickWallHides() {
        Grid g = new Grid().fill(5, 0, -20, 6, 12, 20);
        Sight s = new Sight(new Raycaster(g));
        assertFalse(s.canSee(eye(0.5, 0.5), standing(10.5, 0.5), 4, 0.6, 0.6));
        assertFalse(s.canSee(eye(0.5, 0.5), standing(30.5, -9.5), 4, 0.6, 0.6));
    }

    @Test
    void lowWallShowsTheHeadOfAStandingPlayer() {
        // Muret d'un bloc : la tête de la cible dépasse.
        Grid g = new Grid().fill(5, 0, -20, 5, 0, 20);
        Sight s = new Sight(new Raycaster(g));
        assertTrue(s.canSee(eye(0.5, 0.5), standing(7.5, 0.5), 4, 0, 0));
    }

    @Test
    void cornerPeekIsAnticipated() {
        // Mur en x ∈ [5 ; 6), z < 0. Observateur juste derrière l'angle, cible de l'autre côté.
        Grid g = new Grid().fill(5, 0, -20, 5, 12, -1);
        Sight s = new Sight(new Raycaster(g));
        Sight.Eye e = new Sight.Eye(4.0, EYE, -0.3, 0, 0, 0, true);
        Sight.Body target = standing(9.5, 0.5);
        assertFalse(s.canSee(e, target, 1, 0, 0), "sans anticipation, l'angle cache la cible");
        assertTrue(s.canSee(e, target, 1, 0.6, 0), "un pas de côté la montre : on l'envoie d'avance");
    }

    @Test
    void huggingAWallDoesNotSeeThroughIt() {
        Grid g = new Grid().fill(5, 0, -20, 5, 12, 20);
        Sight s = new Sight(new Raycaster(g));
        // Collé au mur, et même en marchant dedans (anticipation à travers le mur refusée).
        Sight.Eye e = new Sight.Eye(4.7, EYE, 0.5, 0.25, 0, 0, true);
        assertFalse(s.canSee(e, standing(8.5, 0.5), 6, 0.6, 0.6));
    }

    @Test
    void movingObserverSeesWhereHeIsGoing() {
        Grid g = new Grid().fill(5, 0, -20, 5, 12, -1);
        Sight s = new Sight(new Raycaster(g));
        // Il court vers +z à 0,28 bloc/tick : dans 4 ticks il sera sorti de derrière le mur.
        Sight.Eye e = new Sight.Eye(4.0, EYE, -1.5, 0, 0, 0.28, true);
        assertFalse(s.canSee(e, standing(9.5, 0.5), 0, 0, 0));
        assertTrue(s.canSee(e, standing(9.5, 0.5), 6, 0, 0));
    }

    @Test
    void jumpPeekOverAWallIsAnticipated() {
        // Mur jusqu'à y = 2 (plein de 0 à 2 inclus) : l'œil à 1,62 ne voit pas par-dessus, sauté oui.
        Grid g = new Grid().fill(5, 0, -20, 5, 1, 20).boxes(5, 2, 0, 0, 0, 0, 1, 0.1, 1);
        for (int z = -20; z <= 20; z++) {
            g.boxes(5, 2, z, 0, 0, 0, 1, 0.1, 1);
        }
        Sight s = new Sight(new Raycaster(g));
        Sight.Body crouched = new Sight.Body(8.2, 0, 0.2, 8.8, 1.5, 0.8, 0, 0, 0);
        assertFalse(s.canSee(eye(4.2, 0.5), crouched, 2, 0, 0));
        assertTrue(s.canSee(eye(4.2, 0.5), crouched, 2, 0, 1.0));
    }

    @Test
    void glassIsSeeThroughButADoorIsNot() {
        Grid glass = new Grid().fill(5, 0, -20, 5, 12, 20);
        glass.clear(5, 1, 0); // fenêtre (verre = transparent, absent de la grille)
        assertTrue(new Sight(new Raycaster(glass)).canSee(eye(0.5, 0.5), standing(9.5, 0.5), 2, 0, 0));
        Grid door = new Grid().fill(5, 0, -20, 5, 12, 20);
        door.boxes(5, 1, 0, 0, 0, 0, 0.1875, 1, 1);
        door.boxes(5, 2, 0, 0, 0, 0, 0.1875, 1, 1);
        assertFalse(new Sight(new Raycaster(door)).canSee(eye(0.5, 0.5), standing(9.5, 0.5), 2, 0, 0));
    }
}
