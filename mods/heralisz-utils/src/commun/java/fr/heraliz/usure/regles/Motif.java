package fr.heraliz.usure.regles;

/** Motifs d'identifiants avec « * » (n'importe quelle suite de caracteres, eventuellement vide). Sans allocation. */
public final class Motif {
    private Motif() {
    }

    public static boolean correspond(String motif, String texte) {
        if (motif.indexOf('*') < 0) return motif.equals(texte);
        return glob(motif, 0, texte, 0);
    }

    private static boolean glob(String m, int i, String t, int j) {
        // parcours iteratif avec retour au dernier « * » (algorithme classique, lineaire en pratique)
        int etoile = -1, retour = 0;
        while (j < t.length()) {
            if (i < m.length() && m.charAt(i) != '*' && m.charAt(i) == t.charAt(j)) {
                i++;
                j++;
            } else if (i < m.length() && m.charAt(i) == '*') {
                etoile = i++;
                retour = j;
            } else if (etoile >= 0) {
                i = etoile + 1;
                j = ++retour;
            } else {
                return false;
            }
        }
        while (i < m.length() && m.charAt(i) == '*') i++;
        return i == m.length();
    }
}
