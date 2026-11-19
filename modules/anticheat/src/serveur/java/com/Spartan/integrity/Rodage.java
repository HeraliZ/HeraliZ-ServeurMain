package com.Spartan.integrity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Règles d'expulsion « en rodage » (19/11) : les règles ajoutées ce jour-là (bibliothèque d'agent Java ou de débogueur
 * chargée dans le jeu, classe modifiée en mémoire après son chargement, la même vue par le launcher, clé du Gardien
 * réutilisée) n'ont jamais tourné sur de vrais PC de joueurs. Si l'une d'elles se trompait, elle expulserait tout le monde.
 *
 * <p>Disjoncteur : quand {@code seuil} joueurs DIFFÉRENTS la déclenchent en moins de {@code fenetreMs}, la règle est
 * suspendue (alerte au staff seulement, plus d'expulsion) jusqu'au prochain redémarrage du serveur, et le staff est
 * prévenu. Un tricheur est seul ; une règle qui se trompe touche tous les joueurs en quelques minutes : au plus
 * {@code seuil - 1} joueurs honnêtes expulsés à tort, puis plus aucun. Le même joueur ne compte qu'une fois.
 *
 * <p>Java pur (testé hors du jeu). Appelé sur le fil du serveur ; synchronisé par prudence.
 */
public final class Rodage {
    /** Ce que la règle doit faire pour ce joueur. */
    public enum Decision {
        /** Expulser comme d'habitude. */
        APPLIQUER,
        /** Ce joueur est le {@code seuil}-ième différent : règle suspendue maintenant (ne pas l'expulser, prévenir le staff). */
        SUSPENDRE,
        /** Règle déjà suspendue : alerte seulement. */
        SUSPENDUE
    }

    private record Touche(UUID joueur, long quand) {
    }

    private final Map<String, Deque<Touche>> touches = new HashMap<>();
    private final Set<String> suspendues = new LinkedHashSet<>();

    /**
     * Motif de la liste {@code rodage} par lequel commence la clé d'un indice (minuscules), ou null : règle établie, jamais
     * suspendue.
     */
    public static String motif(String cle, List<? extends String> rodage) {
        if (cle == null || rodage == null) {
            return null;
        }
        String c = cle.toLowerCase(Locale.ROOT);
        for (String m : rodage) {
            String t = m == null ? "" : m.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty() && c.startsWith(t)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Un joueur déclenche la règle (motif de {@link #motif}).
     *
     * @param seuil     joueurs différents qui suspendent la règle (0 ou moins : disjoncteur coupé, toujours APPLIQUER)
     * @param fenetreMs durée pendant laquelle on les compte
     */
    public synchronized Decision noter(String regle, UUID joueur, long maintenant, int seuil, long fenetreMs) {
        if (suspendues.contains(regle)) {
            return Decision.SUSPENDUE;
        }
        if (seuil <= 0) {
            return Decision.APPLIQUER;
        }
        Deque<Touche> d = touches.computeIfAbsent(regle, k -> new ArrayDeque<>());
        while (!d.isEmpty() && maintenant - d.peekFirst().quand() > fenetreMs) {
            d.pollFirst();
        }
        d.removeIf(t -> t.joueur().equals(joueur));
        d.addLast(new Touche(joueur, maintenant));
        if (d.size() >= seuil) {
            suspendues.add(regle);
            touches.remove(regle);
            return Decision.SUSPENDRE;
        }
        return Decision.APPLIQUER;
    }

    /** Règle suspendue depuis le démarrage ? */
    public synchronized boolean suspendue(String regle) {
        return suspendues.contains(regle);
    }

    /** Règles suspendues (pour /spartan status et le staff). */
    public synchronized List<String> suspendues() {
        return new ArrayList<>(suspendues);
    }
}
