package fr.heralisz.matchs.zombies.jeu.api;

/**
 * Allure d'un zombie (MATCHS_SPEC §4 : marcheurs, coureurs a partir de la manche 4, sprinteurs a partir de la 8).
 * Le jeu pose l'attribut de vitesse lui-meme ({@link #vitesse}) ; la fabrique s'en sert pour choisir l'animation
 * (bras tendus en marche, course penchee, sprint).
 */
public enum Allure {
    MARCHEUR(0.16),
    COUREUR(0.24),
    SPRINTEUR(0.30),
    /** Rodeurs (manche speciale). */
    RODEUR(0.34);

    /** Valeur de base de l'attribut {@code MOVEMENT_SPEED} posee par le jeu. */
    public final double vitesse;

    Allure(double vitesse) {
        this.vitesse = vitesse;
    }

    /** Lecture tolerante depuis l'ordinal (paquets), MARCHEUR par defaut. */
    public static Allure de(int ordinal) {
        Allure[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : MARCHEUR;
    }
}
