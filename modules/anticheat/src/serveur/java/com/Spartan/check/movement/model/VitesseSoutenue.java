package com.Spartan.check.movement.model;

/**
 * Vitesse soutenue (audit du 02/11, point 14) : distance réellement parcourue contre le maximum physique, sur plusieurs
 * secondes de déplacement suivi sans interruption (Java pur, testé).
 *
 * <p>La détection Speed tolère une petite marge par tick (0,06 bloc : un pas de 0,35 là où le maximum est 0,29) pour
 * les imprécisions du modèle ; un speed hack réglé juste en dessous (+10 à +20 %) n'y dépassait jamais. Sur 5 s, les
 * imprécisions d'un joueur honnête se compensent (le maximum est celui d'un sprint parfait) ; un tricheur qui va
 * toujours un peu trop vite accumule. Toute interruption du suivi (véhicule, train, eau, échelle, téléportation,
 * élytre…) vide la fenêtre : on ne compare que des déplacements à pied entièrement modélisés.
 *
 * <p>Tableaux fixes (aucun objet créé par déplacement).
 */
public final class VitesseSoutenue {
    private static final int CAPACITE = 512;
    private final double[] reels = new double[CAPACITE];
    private final double[] permis = new double[CAPACITE];
    private final int[] ticks = new int[CAPACITE];
    private int debut;
    private int taille;
    private double sommeReel;
    private double sommePermis;
    private int sommeTicks;

    public void clear() {
        debut = 0;
        taille = 0;
        sommeReel = 0;
        sommePermis = 0;
        sommeTicks = 0;
    }

    /**
     * @param reel        distance horizontale parcourue par ce déplacement
     * @param max         maximum physique de ce déplacement (sans la marge par tick)
     * @param t           ticks couverts (≥ 1)
     * @param fenetre     ticks comparés (100 = 5 s)
     * @param distanceMin distance minimale parcourue sur la fenêtre pour juger (un joueur presque immobile : rien)
     * @param ratioMax    dépassement toléré (0,10 = 10 % au-dessus du maximum physique)
     * @return le dépassement mesuré (0,15 = 15 %) s'il franchit {@code ratioMax} sur une fenêtre pleine (la fenêtre est
     * alors vidée), sinon -1
     */
    public double add(double reel, double max, int t, int fenetre, double distanceMin, double ratioMax) {
        int span = Math.max(1, t);
        if (taille == CAPACITE) {
            retirer();
        }
        int i = (debut + taille) % CAPACITE;
        reels[i] = Math.max(0, reel);
        permis[i] = Math.max(0, max);
        ticks[i] = span;
        taille++;
        sommeReel += reels[i];
        sommePermis += permis[i];
        sommeTicks += span;
        while (taille > 1 && sommeTicks - ticks[debut] >= fenetre) {
            retirer();
        }
        if (sommeTicks < fenetre || sommeReel < distanceMin || sommePermis <= 1e-6) {
            return -1;
        }
        double depassement = sommeReel / sommePermis - 1;
        if (depassement > ratioMax) {
            constatReel = sommeReel;
            constatPermis = sommePermis;
            constatTicks = sommeTicks;
            clear();
            return depassement;
        }
        return -1;
    }

    /** Ce qui a été mesuré lors du dernier dépassement (pour l'alerte). */
    private double constatReel;
    private double constatPermis;
    private int constatTicks;

    public double constatReel() {
        return constatReel;
    }

    public double constatPermis() {
        return constatPermis;
    }

    public int constatTicks() {
        return constatTicks;
    }

    private void retirer() {
        sommeReel -= reels[debut];
        sommePermis -= permis[debut];
        sommeTicks -= ticks[debut];
        debut = (debut + 1) % CAPACITE;
        taille--;
    }

    public double sommeReel() {
        return sommeReel;
    }

    public double sommePermis() {
        return sommePermis;
    }

    public int sommeTicks() {
        return sommeTicks;
    }
}
