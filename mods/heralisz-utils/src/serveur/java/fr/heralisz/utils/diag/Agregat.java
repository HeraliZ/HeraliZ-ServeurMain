package fr.heralisz.utils.diag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Échantillons de pile du fil du serveur regroupés : par propriétaire (mod, plugin, Minecraft), par partie de
 * Minecraft, par méthode (en haut de la pile = temps propre ; n'importe où = temps total), par pile entière, et par
 * entité en cours de tick. Rempli par UN fil d'échantillonnage à la fois, puis figé et lu par le fil d'écriture.
 * <p>Les méthodes sont gardées une seule fois (dictionnaire texte -> numéro) : une pile = un tableau de numéros.
 */
final class Agregat {
    static final int PROFONDEUR = 64;
    private static final int MAX_PILES = 3000;

    private final Map<String, Integer> ids = new HashMap<>();
    private final List<String> noms = new ArrayList<>();

    int echantillons;
    private final Map<String, int[]> etats = new HashMap<>();
    private final Map<String, int[]> proprietaires = new HashMap<>();
    private final Map<String, int[]> familles = new HashMap<>();
    private final Map<String, int[]> propFam = new HashMap<>();
    private final Map<String, int[]> entites = new HashMap<>();
    private final Map<String, Map<Integer, int[]>> entrees = new HashMap<>();
    private final Map<Integer, int[]> propre = new HashMap<>();
    private final Map<Integer, int[]> total = new HashMap<>();
    private final Map<Pile, int[]> piles = new HashMap<>();
    private int pilesHors;

    /** Pile tronquée (numéros des méthodes, du haut vers le bas). */
    private static final class Pile {
        final int[] f;
        final int h;

        Pile(int[] f) {
            this.f = f;
            this.h = Arrays.hashCode(f);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Pile p && p.h == h && Arrays.equals(p.f, f);
        }

        @Override
        public int hashCode() {
            return h;
        }
    }

    static String texte(StackTraceElement f) {
        int l = f.getLineNumber();
        return f.getClassName() + "." + f.getMethodName() + (l >= 0 ? ":" + l : l == -2 ? " (natif)" : "");
    }

    int id(String t) {
        Integer i = ids.get(t);
        if (i == null) {
            i = noms.size();
            noms.add(t);
            ids.put(t, i);
        }
        return i;
    }

    String nom(int id) {
        return id >= 0 && id < noms.size() ? noms.get(id) : "?";
    }

    private static void inc(Map<String, int[]> m, String k, int n) {
        m.computeIfAbsent(k, x -> new int[1])[0] += n;
    }

    private static void inc(Map<Integer, int[]> m, int k, int n) {
        m.computeIfAbsent(k, x -> new int[1])[0] += n;
    }

    /** Famille affichée pour un échantillon, selon l'état du fil. */
    static String famille(StackTraceElement[] st, Thread.State etat) {
        if (etat == Thread.State.WAITING || etat == Thread.State.TIMED_WAITING) {
            return Proprietaires.attendTroncon(st) ? "Attente d'un tronçon (lu ou généré par un autre fil)"
                    : "Attente (sommeil, verrou ou tâche d'un autre fil)";
        }
        if (etat == Thread.State.BLOCKED) return "Bloqué sur un verrou (synchronized)";
        String f = Proprietaires.famille(st);
        return f == null ? "(aucune méthode de Minecraft)" : f;
    }

    /**
     * Ajoute un échantillon ; rend les numéros de toute la pile (pour la liste des échantillons d'un blocage).
     *
     * @param entite entité en cours de tick à cet instant, ou null
     */
    int[] ajouter(StackTraceElement[] st, Thread.State etat, String entite) {
        echantillons++;
        inc(etats, etat == null ? "?" : etat.name(), 1);
        String proprio = Proprietaires.proprietaire(st);
        String fam = famille(st, etat);
        inc(proprietaires, proprio, 1);
        inc(familles, fam, 1);
        inc(propFam, proprio + "  ›  " + fam, 1);
        int[] tout = new int[st.length];
        for (int i = 0; i < st.length; i++) tout[i] = id(texte(st[i]));
        int ip = Proprietaires.indiceProprietaire(st);
        if (ip >= 0) inc(entrees.computeIfAbsent(proprio, x -> new HashMap<>()), tout[ip], 1);
        if (tout.length > 0) inc(propre, tout[0], 1);
        // temps total : chaque méthode une fois par échantillon (récursion comprise)
        int[] tri = tout.clone();
        Arrays.sort(tri);
        for (int i = 0; i < tri.length; i++) {
            if (i == 0 || tri[i] != tri[i - 1]) inc(total, tri[i], 1);
        }
        Pile p = new Pile(Arrays.copyOf(tout, Math.min(tout.length, PROFONDEUR)));
        int[] c = piles.get(p);
        if (c != null) c[0]++;
        else if (piles.size() < MAX_PILES) piles.put(p, new int[]{1});
        else pilesHors++;
        if (entite != null) inc(entites, entite, 1);
        return tout;
    }

    /** Ajoute un autre regroupement (ticks longs réunis dans un même rapport). */
    void fusionner(Agregat o) {
        echantillons += o.echantillons;
        o.etats.forEach((k, v) -> inc(etats, k, v[0]));
        o.proprietaires.forEach((k, v) -> inc(proprietaires, k, v[0]));
        o.familles.forEach((k, v) -> inc(familles, k, v[0]));
        o.propFam.forEach((k, v) -> inc(propFam, k, v[0]));
        o.entites.forEach((k, v) -> inc(entites, k, v[0]));
        o.entrees.forEach((k, m) -> {
            Map<Integer, int[]> d = entrees.computeIfAbsent(k, x -> new HashMap<>());
            m.forEach((f, v) -> inc(d, id(o.nom(f)), v[0]));
        });
        o.propre.forEach((f, v) -> inc(propre, id(o.nom(f)), v[0]));
        o.total.forEach((f, v) -> inc(total, id(o.nom(f)), v[0]));
        o.piles.forEach((p, v) -> {
            int[] f = new int[p.f.length];
            for (int i = 0; i < f.length; i++) f[i] = id(o.nom(p.f[i]));
            Pile q = new Pile(f);
            int[] c = piles.get(q);
            if (c != null) c[0] += v[0];
            else if (piles.size() < MAX_PILES) piles.put(q, new int[]{v[0]});
            else pilesHors += v[0];
        });
        pilesHors += o.pilesHors;
    }

    // ------------------------------------------------------------------ lecture

    private static <K> List<Map.Entry<K, int[]>> tries(Map<K, int[]> m) {
        List<Map.Entry<K, int[]>> l = new ArrayList<>(m.entrySet());
        l.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
        return l;
    }

    /** Les {@code n} premiers propriétaires : « Zombie Awareness 41 %, Minecraft 30 %… ». */
    String tete(int n) {
        StringBuilder b = new StringBuilder();
        int k = 0;
        for (Map.Entry<String, int[]> e : tries(proprietaires)) {
            if (k++ >= n) break;
            if (b.length() > 0) b.append(", ");
            b.append(e.getKey()).append(' ').append(Math.round(100.0 * e.getValue()[0] / Math.max(1, echantillons))).append(" %");
        }
        return b.length() == 0 ? "-" : b.toString();
    }

    /** Premier propriétaire hors Minecraft et Java (pour le titre d'un rapport), sinon le premier tout court. */
    String premierMod() {
        String premier = null;
        for (Map.Entry<String, int[]> e : tries(proprietaires)) {
            if (premier == null) premier = e.getKey();
            if (!e.getKey().equals(Proprietaires.MINECRAFT) && !e.getKey().equals(Proprietaires.JVM)) return e.getKey();
        }
        return premier == null ? "-" : premier;
    }

    private String ligne(int n, double msParEchantillon) {
        double pct = 100.0 * n / Math.max(1, echantillons);
        return String.format(Locale.ROOT, "%6.1f %%  %9.1f ms  %6d éch.  ", pct, n * msParEchantillon, n);
    }

    /**
     * Écrit les tableaux du regroupement.
     *
     * @param msParEchantillon temps représenté par un échantillon (pour l'estimation en ms)
     * @param nbPiles          piles entières à montrer
     */
    void ecrire(StringBuilder b, double msParEchantillon, int nbPiles) {
        b.append(String.format(Locale.ROOT, "%d échantillon(s) ; un échantillon ≈ %.2f ms. « éch. » = nombre d'échantillons.%n%n",
                echantillons, msParEchantillon));
        b.append("== État du fil du serveur ==\n");
        for (Map.Entry<String, int[]> e : tries(etats)) b.append(ligne(e.getValue()[0], msParEchantillon)).append(e.getKey()).append('\n');
        b.append("  (RUNNABLE = il calcule ; TIMED_WAITING / WAITING = il attend ; BLOCKED = il attend un verrou)\n\n");

        b.append("== Par propriétaire (mod, plugin ou Minecraft, en partant de la méthode qui tournait) ==\n");
        for (Map.Entry<String, int[]> e : tries(proprietaires)) b.append(ligne(e.getValue()[0], msParEchantillon)).append(e.getKey()).append('\n');
        b.append('\n');

        b.append("== Par partie de Minecraft (la plus précise trouvée dans la pile) ==\n");
        for (Map.Entry<String, int[]> e : tries(familles)) b.append(ligne(e.getValue()[0], msParEchantillon)).append(e.getKey()).append('\n');
        b.append('\n');

        b.append("== Propriétaire  ›  partie de Minecraft ==\n");
        int k = 0;
        for (Map.Entry<String, int[]> e : tries(propFam)) {
            if (k++ >= 40) break;
            b.append(ligne(e.getValue()[0], msParEchantillon)).append(e.getKey()).append('\n');
        }
        b.append('\n');

        b.append("== Méthodes des mods et plugins qui ont lancé ce travail (par propriétaire) ==\n");
        k = 0;
        for (Map.Entry<String, int[]> p : tries(proprietaires)) {
            Map<Integer, int[]> m = entrees.get(p.getKey());
            if (m == null) continue;
            if (k++ >= 12) break;
            b.append(p.getKey()).append(" :\n");
            int j = 0;
            for (Map.Entry<Integer, int[]> e : tries(m)) {
                if (j++ >= 8) break;
                b.append("  ").append(ligne(e.getValue()[0], msParEchantillon)).append(nom(e.getKey())).append('\n');
            }
        }
        b.append('\n');

        if (!entites.isEmpty()) {
            b.append("== Entité dont le tick était en cours à l'instant de l'échantillon ==\n");
            k = 0;
            for (Map.Entry<String, int[]> e : tries(entites)) {
                if (k++ >= 25) break;
                b.append(ligne(e.getValue()[0], msParEchantillon)).append(e.getKey()).append('\n');
            }
            b.append('\n');
        }

        b.append("== Méthodes en haut de la pile (temps propre : ce qui calculait vraiment) ==\n");
        k = 0;
        for (Map.Entry<Integer, int[]> e : tries(propre)) {
            if (k++ >= 40) break;
            b.append(ligne(e.getValue()[0], msParEchantillon)).append(nom(e.getKey())).append('\n');
        }
        b.append('\n');

        b.append("== Méthodes présentes dans la pile (temps total : elles et tout ce qu'elles appellent) ==\n");
        k = 0;
        for (Map.Entry<Integer, int[]> e : tries(total)) {
            if (k >= 60) break;
            String n = nom(e.getKey());
            // les méthodes de la boucle du serveur et des fils sont dans toutes les piles : rien à apprendre
            if (n.startsWith("java.lang.Thread.") || n.startsWith("net.minecraft.server.MinecraftServer.")
                    || n.startsWith("net.minecraft.server.dedicated.") || n.startsWith("net.minecraft.util.thread.")) continue;
            k++;
            b.append(ligne(e.getValue()[0], msParEchantillon)).append(n).append('\n');
        }
        b.append('\n');

        if (nbPiles > 0) {
            b.append("== Piles les plus fréquentes (du haut vers le bas, ").append(PROFONDEUR).append(" méthodes au plus) ==\n");
            k = 0;
            for (Map.Entry<Pile, int[]> e : tries(piles)) {
                if (k++ >= nbPiles) break;
                b.append("-- pile ").append(k).append(" : ").append(ligne(e.getValue()[0], msParEchantillon).trim()).append('\n');
                for (int f : e.getKey().f) b.append("    ").append(nom(f)).append('\n');
            }
            if (pilesHors > 0) b.append("(").append(pilesHors).append(" échantillon(s) de piles trop variées, non listées)\n");
            b.append('\n');
        }
    }

    /** Pile entière en texte (liste des échantillons d'un blocage). */
    void pile(StringBuilder b, int[] tout) {
        for (int f : tout) b.append("    ").append(nom(f)).append('\n');
    }
}
