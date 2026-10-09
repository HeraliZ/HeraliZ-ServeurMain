package com.Spartan.check.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Un relevé de l'anti-duplication (Java pur, testé) : chaque objet suivi vu, avec son numéro unique et l'endroit où il
 * est. Un même numéro à DEUX endroits du même relevé est une copie. Un même objet en mémoire vu deux fois (deux chemins
 * qui mènent au même emplacement) n'est compté qu'une fois.
 */
public final class DupeIndex {
    /** Où se trouve un exemplaire : pseudo du joueur et emplacement lisible (« inventaire 4 », « sac (inventaire 2) 7 »…). */
    public record Lieu(String joueur, String endroit) {
        @Override
        public String toString() {
            return joueur + " (" + endroit + ")";
        }
    }

    /** Un numéro vu à plusieurs endroits à la fois. */
    public record Copie(long uid, String objet, List<Lieu> lieux) {
    }

    private final Map<Long, List<Lieu>> vus = new LinkedHashMap<>();
    private final Map<Long, String> objets = new LinkedHashMap<>();
    private final Set<Object> deja = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * @param identite l'objet en mémoire (pile ou données) : vu deux fois, il ne compte qu'une fois ; null = toujours compté
     */
    public void ajouter(Object identite, long uid, String objet, Lieu lieu) {
        if (uid == 0 || (identite != null && !deja.add(identite))) {
            return;
        }
        vus.computeIfAbsent(uid, k -> new ArrayList<>()).add(lieu);
        objets.putIfAbsent(uid, objet);
    }

    public int taille() {
        return vus.size();
    }

    /** Numéros vus à au moins deux endroits. */
    public List<Copie> copies() {
        List<Copie> out = new ArrayList<>();
        vus.forEach((uid, lieux) -> {
            if (lieux.size() >= 2) {
                out.add(new Copie(uid, objets.get(uid), List.copyOf(lieux)));
            }
        });
        return out;
    }

    /** Un identifiant d'objet correspond-il à la liste suivie (« mod:objet » exact ou « mod:* ») ? */
    public static boolean suivi(String id, List<String> motifs) {
        for (String m : motifs) {
            if (m.endsWith(":*") ? id.startsWith(m.substring(0, m.length() - 1)) : id.equals(m)) {
                return true;
            }
        }
        return false;
    }
}
