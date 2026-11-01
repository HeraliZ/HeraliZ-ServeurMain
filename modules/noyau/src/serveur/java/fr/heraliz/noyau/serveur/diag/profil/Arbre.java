package fr.heraliz.noyau.serveur.diag.profil;

import java.util.Arrays;

/**
 * Arbre des appels d'un profil (01/11) : chaque relevé de la pile du fil serveur est rangé du bas de la pile
 * ({@code Thread.run}) vers le haut (ce qui tournait). Un nœud = une méthode À UNE LIGNE donnée (la ligne de l'appel
 * suivant, ou la ligne où le fil était) : on retrouve ainsi la ligne exacte de chaque appel, et l'affichage regroupe ensuite
 * par méthode. {@code total} = relevés passés par ce nœud ; {@code propre} = relevés arrêtés dessus (temps propre).
 * <p>
 * Ajout sans allocation hors des nouveaux nœuds (les chaînes des cadres sont celles de la machine Java, partagées d'un
 * relevé à l'autre). Taille bornée : au-delà de {@code maxNoeuds}, la fin d'une pile nouvelle est rangée sur son dernier
 * nœud connu ({@link #tronques}). Un seul fil écrit à la fois (verrou de l'appelant).
 */
public final class Arbre {
    public static final class Noeud {
        public final String classe, methode, fichier, module;
        public final int ligne, profondeur;
        public final Noeud parent;
        public int total, propre;
        private Noeud[] enfants = VIDE;
        private int nb;
        /** index des enfants par empreinte (au-delà de 12 enfants) */
        private Noeud[] index;

        Noeud(Noeud parent, String classe, String methode, String fichier, String module, int ligne) {
            this.parent = parent;
            this.classe = classe;
            this.methode = methode;
            this.fichier = fichier;
            this.module = module;
            this.ligne = ligne;
            this.profondeur = parent == null ? 0 : parent.profondeur + 1;
        }

        public int nbEnfants() {
            return nb;
        }

        public Noeud enfant(int i) {
            return enfants[i];
        }

        boolean est(String c, String m, int l) {
            return ligne == l && methode.equals(m) && classe.equals(c);
        }

        private static int empreinte(String c, String m, int l) {
            int h = (c.hashCode() * 31 + m.hashCode()) * 31 + l;
            return h ^ (h >>> 16);
        }

        Noeud trouver(String c, String m, int l) {
            if (index != null) {
                int masque = index.length - 1;
                for (int i = empreinte(c, m, l) & masque; ; i = (i + 1) & masque) {
                    Noeud n = index[i];
                    if (n == null) return null;
                    if (n.est(c, m, l)) return n;
                }
            }
            for (int i = 0; i < nb; i++) if (enfants[i].est(c, m, l)) return enfants[i];
            return null;
        }

        void ajouter(Noeud n) {
            if (nb == enfants.length) enfants = Arrays.copyOf(enfants, Math.max(2, nb * 2));
            enfants[nb++] = n;
            if (nb > 12) {
                if (index == null || nb * 2 > index.length) {
                    index = new Noeud[Integer.highestOneBit(nb * 4)];
                    for (int i = 0; i < nb; i++) placer(enfants[i]);
                } else {
                    placer(n);
                }
            }
        }

        private void placer(Noeud n) {
            int masque = index.length - 1;
            int i = empreinte(n.classe, n.methode, n.ligne) & masque;
            while (index[i] != null) i = (i + 1) & masque;
            index[i] = n;
        }
    }

    private static final Noeud[] VIDE = new Noeud[0];

    public final Noeud racine = new Noeud(null, "", "", null, null, -1);
    private final int maxNoeuds;
    private int noeuds = 1;
    /** relevés dont la pile a été coupée faute de place */
    public int tronques;

    public Arbre(int maxNoeuds) {
        this.maxNoeuds = maxNoeuds;
    }

    public int noeuds() {
        return noeuds;
    }

    /** Range une pile (cadre 0 = le haut de la pile, comme {@link Thread#getStackTrace()}) ; rend le nœud du haut. */
    public Noeud ajouter(StackTraceElement[] pile) {
        Noeud n = racine;
        n.total++;
        for (int i = pile.length - 1; i >= 0; i--) {
            StackTraceElement e = pile[i];
            String c = e.getClassName(), m = e.getMethodName();
            int l = e.getLineNumber();
            Noeud f = n.trouver(c, m, l);
            if (f == null) {
                if (noeuds >= maxNoeuds) {
                    tronques++;
                    break;
                }
                f = new Noeud(n, c, m, e.getFileName(), e.getModuleName(), l);
                n.ajouter(f);
                noeuds++;
            }
            n = f;
            n.total++;
        }
        n.propre++;
        return n;
    }
}
