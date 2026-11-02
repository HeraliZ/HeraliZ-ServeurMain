package fr.heraliz.clashwar.moteur.api;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kits fixes des matchs, lus dans {@code data/heraliz_clashwar/kits/*.json} au chargement des donnees (et a chaque
 * {@code /reload}) par le moteur. Les classes personnalisees des duels ne sont pas ici (donnees des duels).
 * Fil serveur uniquement.
 */
public final class Kits {
    private Kits() {
    }

    private static Map<String, Kit> kits = Map.of();

    /** Kit par identifiant, ou null. */
    @Nullable
    public static Kit get(String id) {
        return kits.get(id);
    }

    /** Tous les kits (ordre de chargement), non modifiable. */
    public static Collection<Kit> tous() {
        return kits.values();
    }

    /** Kits proposes dans ce mode ("clashwar", "duel", "zombies"), nouvelle liste. */
    public static List<Kit> pourMode(String mode) {
        List<Kit> l = new ArrayList<>();
        for (Kit k : kits.values()) if (k.pourMode(mode)) l.add(k);
        return l;
    }

    /** Kits d'une classe ({@code Kit.CLASSE_*}), nouvelle liste. */
    public static List<Kit> deClasse(String classe) {
        List<Kit> l = new ArrayList<>();
        for (Kit k : kits.values()) if (k.classe().equals(classe)) l.add(k);
        return l;
    }

    /** Reserve au moteur : remplace les kits (rechargement des donnees). */
    public static void definir(Map<String, Kit> nouveaux) {
        kits = Collections.unmodifiableMap(new LinkedHashMap<>(nouveaux));
    }
}
