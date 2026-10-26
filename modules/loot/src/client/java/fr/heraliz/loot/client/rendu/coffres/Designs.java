package fr.heraliz.loot.client.rendu.coffres;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Les modèles des coffres T7, par nom de bloc (construits à la première utilisation). */
public final class Designs {
    private Designs() {
    }

    private static final Map<String, Supplier<DesignCoffre>> USINES = new LinkedHashMap<>();
    private static final Map<String, DesignCoffre> FAITS = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        USINES.put("t7_coffre_fort", CoffreFort::new);
        USINES.put("t7_coffre_armes", CoffreArmes::new);
        USINES.put("t7_largage", Largage::new);
        USINES.put("t7_mallette", Mallette::new);
        USINES.put("t7_reliquaire", Reliquaire::new);
        USINES.put("t7_capsule", Capsule::new);
        USINES.put("t7_conteneur_heraliz", ConteneurHeraliz::new);
    }

    public static Iterable<String> noms() {
        return USINES.keySet();
    }

    /** Modèle prêt (maillages construits), ou null si le nom est inconnu. */
    public static DesignCoffre get(String nom) {
        DesignCoffre d = FAITS.get(nom);
        if (d == null) {
            Supplier<DesignCoffre> u = USINES.get(nom);
            if (u == null) return null;
            d = u.get();
            d.preparer();
            FAITS.put(nom, d);
        }
        return d;
    }
}
