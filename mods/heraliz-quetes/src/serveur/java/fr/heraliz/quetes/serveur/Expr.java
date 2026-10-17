package fr.heraliz.quetes.serveur;

/**
 * Petit calcul pour les récompenses des contrats (« {n}*6+50 » une fois {n} remplacé) : nombres, + - * / et
 * parenthèses. Une expression illisible vaut 0.
 */
final class Expr {
    private final String s;
    private int i;

    private Expr(String s) {
        this.s = s.replace(" ", "");
    }

    static long calculer(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            Expr e = new Expr(s);
            double v = e.somme();
            return e.i == e.s.length() ? Math.round(v) : 0;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private double somme() {
        double v = produit();
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '+') {
                i++;
                v += produit();
            } else if (c == '-') {
                i++;
                v -= produit();
            } else break;
        }
        return v;
    }

    private double produit() {
        double v = facteur();
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '*') {
                i++;
                v *= facteur();
            } else if (c == '/') {
                i++;
                double d = facteur();
                v = d == 0 ? 0 : v / d;
            } else break;
        }
        return v;
    }

    private double facteur() {
        if (i < s.length() && s.charAt(i) == '(') {
            i++;
            double v = somme();
            if (i < s.length() && s.charAt(i) == ')') i++;
            return v;
        }
        if (i < s.length() && s.charAt(i) == '-') {
            i++;
            return -facteur();
        }
        int d = i;
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
        if (d == i) throw new IllegalArgumentException(s);
        return Double.parseDouble(s.substring(d, i));
    }
}
