package fr.heraliz.loot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Un coffre T7 : son nom de bloc, la durée de son animation d'ouverture (l'écran s'ouvre quand elle est finie) et sa
 * forme (seizièmes, repère du modèle : avant vers +Z). Les modèles 3D animés sont côté client (client/rendu/coffres) et
 * utilisent les mêmes durées.
 */
public final class TypeCoffre {
    public static final Map<String, TypeCoffre> TOUS = new LinkedHashMap<>();

    public final String nom;
    public final int ouverture;
    public final List<double[]> collision = new ArrayList<>();

    private TypeCoffre(String nom, int ouverture, double[]... boites) {
        this.nom = nom;
        this.ouverture = ouverture;
        for (double[] b : boites) collision.add(b);
    }

    /** Durée de la fermeture (plus rapide que l'ouverture). */
    public int fermeture() {
        return Math.max(12, ouverture / 2);
    }

    private static void ajouter(TypeCoffre t) {
        TOUS.put(t.nom, t);
    }

    /**
     * Boîtes en seizièmes, repère du modèle (avant vers +Z, +X à droite, origine au coin du bloc principal) : elles
     * peuvent dépasser le bloc (coffre d'armes sur deux blocs vers la droite, largage de 2 x 2 vers l'arrière).
     */
    static void charger() {
        if (!TOUS.isEmpty()) return;
        ajouter(new TypeCoffre("t7_coffre_fort", 46, new double[]{1, 0, 1.5, 15, 15.6, 15.0}));
        ajouter(new TypeCoffre("t7_coffre_armes", 44, new double[]{0.6, 0, 3.6, 31.4, 8.6, 12.6}));
        ajouter(new TypeCoffre("t7_largage", 40, new double[]{0.4, 0, -15.6, 31.6, 29.6, 15.6}));
        ajouter(new TypeCoffre("t7_mallette", 46, new double[]{1.2, 0, 3.6, 14.8, 4.3, 12.4}));
        ajouter(new TypeCoffre("t7_reliquaire", 60, new double[]{1.2, 0, 1.2, 14.8, 3.8, 14.8}, new double[]{3.3, 3.8, 3.3, 12.7, 14.2, 12.7}));
        ajouter(new TypeCoffre("t7_capsule", 45, new double[]{0.3, 0, 2.9, 15.7, 11.2, 13.1}));
        ajouter(new TypeCoffre("t7_conteneur_heraliz", 54, new double[]{0.4, 0, 0.4, 15.6, 15.6, 15.6}));
    }
}
