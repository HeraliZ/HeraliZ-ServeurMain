package fr.heralisz.matchs.zombies.jeu.api;

/**
 * Raison d'un gain ou d'une depense de points (MATCHS_SPEC §4) : +10 par touche, +60 kill, +100 kill a la tete,
 * +130 au couteau, +10 par planche reparee, bonus Atomique (+400) / Charpentier (+200), achats (negatif).
 * Le HUD s'en sert pour la couleur ou le style du « +60 » flottant.
 */
public enum MotifPoints {
    TOUCHE, KILL, TETE, COUTEAU, PLANCHE, BONUS, ACHAT, AUTRE;

    public static MotifPoints de(int ordinal) {
        MotifPoints[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : AUTRE;
    }
}
