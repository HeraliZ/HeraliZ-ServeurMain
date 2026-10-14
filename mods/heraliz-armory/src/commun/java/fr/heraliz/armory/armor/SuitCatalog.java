package fr.heraliz.armory.armor;

import java.util.List;

/**
 * Combinaisons integrales (0.9.0), faites par le proprietaire : un seul objet (heraliz_armory:<ensemble>_suit) porte au
 * torse, qui couvre tout le corps et se porte SEUL (voir suit.SuitRules). Modele armor/<ensemble>/suit.json, texture
 * textures/models/armor/<ensemble>.png.
 * <p>La combinaison etant seule, ses valeurs sont le total porte (meme echelle que les pieces de l'Armurerie) ; elle
 * compte en entier sur la tete comme sur le corps. Aucun malus de vitesse, sauf les palmes de la plongee a terre.
 */
public final class SuitCatalog {
    private SuitCatalog() {}

    // valeurs ecrites par outils/equilibrage_armures.py (palier = rang de rarete, une seule protection partout)
    public static final List<PieceDef> SUITS = List.of(
            new PieceDef("hazmat", "suit", 2, 300, 0, 12, 12, 12, 12.0f, 100, true, false, null),
            new PieceDef("hazmat_renforce", "suit", 4, 450, 0, 36, 36, 36, 36.0f, 100, true, false, null),
            new PieceDef("arctique", "suit", 2, 300, 0, 12, 12, 12, 12.0f, 0, false, false, "cold"),
            new PieceDef("arctique_renforcee", "suit", 4, 450, 0, 36, 36, 36, 36.0f, 0, false, false, "cold"),
            new PieceDef("plongee", "suit", 3, 400, -0.06, 6, 6, 6, 6.0f, 0, false, false, "diving")
    );
}
