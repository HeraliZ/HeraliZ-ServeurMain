package fr.heralisz.matchs.zombies.jeu;

/**
 * Regles CoD du nounours de la boite mystere (ZOMBIES_V3_SPEC §2, THE_GIANT_REFERENCE §5), sans dependance au jeu
 * (verifiables hors Minecraft) :
 * <ul>
 *   <li>jamais aux 3 premiers tirages faits a l'emplacement courant ;</li>
 *   <li>15 % du 4e au 7e, 30 % du 8e au 12e, 50 % ensuite ;</li>
 *   <li>a l'emplacement de depart (premier emplacement de la partie, avant tout deplacement), le 8e tirage donne
 *   forcement le nounours.</li>
 * </ul>
 */
final class ReglesBoite {
    private ReglesBoite() {
    }

    /** Chance de nounours pour le tirage numero {@code n} (1 = premier tirage a cet emplacement). */
    static float chance(int n) {
        if (n <= 3) return 0f;
        if (n <= 7) return 0.15f;
        if (n <= 12) return 0.30f;
        return 0.50f;
    }

    /**
     * Le tirage numero {@code n} donne-t-il le nounours ?
     *
     * @param depart vrai tant que la boite n'a jamais quitte son premier emplacement
     * @param alea   tirage uniforme dans [0, 1)
     */
    static boolean nounours(int n, boolean depart, float alea) {
        if (n <= 3) return false;
        if (depart && n == 8) return true;
        return alea < chance(n);
    }
}
