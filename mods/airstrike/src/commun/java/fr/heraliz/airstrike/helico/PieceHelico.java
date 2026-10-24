package fr.heraliz.airstrike.helico;

import net.minecraft.util.RandomSource;

/**
 * Pièces récupérables sur l'épave de l'hélicoptère (3 morceaux fouillables par épave, une pièce chacun, tirée au sort
 * avec ces poids : les pièces de peu de valeur sortent le plus souvent). Prix de rachat chez Mila : outils/marchands.py
 * (35 à 250 hérals) ; raretés : outils/loot_armes.py (RARETES_IMPOSEES) ; icônes : outils/modele/helico.py. Les
 * identifiants des objets (airstrike:helico_&lt;id&gt;) ne changent jamais.
 */
public enum PieceHelico {
    TOLE("tole", 30),
    CABLES("cables", 24),
    PALE("pale", 18),
    RESERVOIR("reservoir", 12),
    MITRAILLEUSE("mitrailleuse", 8),
    AVIONIQUE("avionique", 5),
    TURBINE("turbine", 3);

    public final String id;
    public final int poids;

    PieceHelico(String id, int poids) {
        this.id = id;
        this.poids = poids;
    }

    /** Nom de l'objet dans le registre (sans le modId). */
    public String objet() {
        return "helico_" + id;
    }

    public static PieceHelico tirer(RandomSource r) {
        int total = 0;
        for (PieceHelico p : values()) total += p.poids;
        int k = r.nextInt(total);
        for (PieceHelico p : values()) {
            k -= p.poids;
            if (k < 0) return p;
        }
        return TOLE;
    }
}
