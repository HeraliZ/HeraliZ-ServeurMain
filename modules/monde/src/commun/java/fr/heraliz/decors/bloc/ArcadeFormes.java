package fr.heraliz.decors.bloc;

/* Repris du jar Décors 1.30.3 de la nouvelle carte (décompilé le 08/11, sources d'origine absentes du dépôt). */

import java.util.Map;

public final class ArcadeFormes {
    public static final Map<String, double[][]> BOITES = Map.ofEntries(
        Map.entry("arcade_bas", new double[][]{{0.0, 0.0, 2.0, 16.0}, {14.0, 0.0, 16.0, 16.0}}),
        Map.entry(
            "arcade_haut",
            new double[][]{
                {0.0, 0.0, 1.0, 16.0},
                {1.0, 8.0, 2.0, 16.0},
                {1.0, 0.0, 2.0, 3.0},
                {2.0, 10.0, 3.0, 16.0},
                {3.0, 11.0, 4.0, 16.0},
                {4.0, 12.0, 12.0, 16.0},
                {12.0, 11.0, 13.0, 16.0},
                {13.0, 10.0, 14.0, 16.0},
                {14.0, 8.0, 15.0, 16.0},
                {14.0, 0.0, 15.0, 3.0},
                {15.0, 0.0, 16.0, 16.0}
            }
        ),
        Map.entry("arcade_milieu", new double[][]{{0.0, 0.0, 2.0, 16.0}, {14.0, 0.0, 16.0, 16.0}}),
        Map.entry("grande_arcade_bas_droite", new double[][]{{13.0, 0.0, 16.0, 16.0}}),
        Map.entry("grande_arcade_bas_gauche", new double[][]{{0.0, 0.0, 3.0, 16.0}}),
        Map.entry(
            "grande_arcade_haut_droite",
            new double[][]{
                {0.0, 13.0, 6.0, 16.0},
                {6.0, 12.0, 8.0, 16.0},
                {8.0, 11.0, 9.0, 16.0},
                {9.0, 10.0, 11.0, 16.0},
                {11.0, 8.0, 12.0, 16.0},
                {12.0, 7.0, 13.0, 16.0},
                {13.0, 5.0, 14.0, 16.0},
                {14.0, 3.0, 15.0, 16.0},
                {15.0, 0.0, 16.0, 16.0}
            }
        ),
        Map.entry(
            "grande_arcade_haut_gauche",
            new double[][]{
                {0.0, 0.0, 1.0, 16.0},
                {1.0, 3.0, 2.0, 16.0},
                {2.0, 5.0, 3.0, 16.0},
                {3.0, 7.0, 4.0, 16.0},
                {4.0, 8.0, 5.0, 16.0},
                {5.0, 10.0, 7.0, 16.0},
                {7.0, 11.0, 8.0, 16.0},
                {8.0, 12.0, 10.0, 16.0},
                {10.0, 13.0, 16.0, 16.0}
            }
        ),
        Map.entry("grande_arcade_milieu_droite", new double[][]{{13.0, 0.0, 14.0, 8.0}, {14.0, 0.0, 15.0, 10.0}, {15.0, 0.0, 16.0, 16.0}}),
        Map.entry("grande_arcade_milieu_gauche", new double[][]{{0.0, 0.0, 1.0, 16.0}, {1.0, 0.0, 2.0, 10.0}, {2.0, 0.0, 3.0, 8.0}})
    );

    private ArcadeFormes() {
    }
}
