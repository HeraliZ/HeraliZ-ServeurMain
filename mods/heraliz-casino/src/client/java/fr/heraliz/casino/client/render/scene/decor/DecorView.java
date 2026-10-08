package fr.heraliz.casino.client.render.scene.decor;

/**
 * Etat d'un element de decor lu par son modele 3D : allume ou non, variante, et quelques donnees du monde.
 */
public final class DecorView {
    /** Allume (ecran, neon, feu, lampe) ; poteau : chaine accrochee ; porte du coffre : ouverte. */
    public boolean on = true;
    /** Variante choisie par le personnel (couleur, texte, motif, chaine...). */
    public int mode;
    /** Jackpot progressif du casino (jetons), pour l'afficheur et la tele. */
    public long jackpot = 25_000;
    /** Poteau : decalages (px, repere du modele) vers les poteaux voisins relies par une chaine. */
    public float[][] links = new float[0][];
    /** Ticks depuis le dernier basculement de "on" (porte qui s'ouvre ou se ferme) ; tres grand si jamais vu basculer. */
    public float since = 1e6f;
    /** Lumiere qui gresille : etat du bloc (0 pleine, 1 faible, 2 coupee) et ticks depuis la derniere saute. */
    public int flicker;
    public float flickerSince = 1e6f;

    public DecorView set(boolean on, int mode) {
        this.on = on;
        this.mode = mode;
        this.since = 1e6f;
        this.flicker = 0;
        this.flickerSince = 1e6f;
        return this;
    }
}
