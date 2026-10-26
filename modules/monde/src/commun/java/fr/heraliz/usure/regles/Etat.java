package fr.heraliz.usure.regles;

/**
 * Etats d'une arme, du meilleur au pire. Les seuils (en % d'etat) sont reglables ; une arme a 0 % est ruinee : elle ne
 * tire plus avant d'etre reparee. L'usure ne change rien d'autre (ni degats, ni precision).
 */
public enum Etat {
    NEUF("neuf", 0x5FD08A),
    BON("bon", 0xB5D86A),
    USE("use", 0xE8C36A),
    ABIME("abime", 0xEE8E4A),
    RUINE("ruine", 0xFF6B6B);

    public static final Etat[] TOUS = values();

    public final String cle;
    /** Couleur (RGB) de l'etat : HUD (point et jauge), infobulle. */
    public final int couleur;

    Etat(String cle, int couleur) {
        this.cle = cle;
        this.couleur = couleur;
    }

    public String cleTraduction() {
        return "heraliz_usure.etat." + cle;
    }

    /** Etat d'une arme a {@code milliemes} d'etat (0..1000) selon les seuils des regles. */
    public static Etat de(int milliemes, Regles r) {
        if (milliemes <= 0) return RUINE;
        float pct = milliemes / 10f;
        if (pct >= r.seuilNeuf) return NEUF;
        if (pct >= r.seuilBon) return BON;
        if (pct >= r.seuilUse) return USE;
        return ABIME;
    }
}
