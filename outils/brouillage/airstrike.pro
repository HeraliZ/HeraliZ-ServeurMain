# ------------------------------------------------------------------------------------------------------------------
# Réglage ProGuard propre à airstrike (mod d'ESSAI du brouillage) — s'ajoute à commun.pro.
# airstrike est 100 % à nous (fr.heraliz.airstrike), petit, une seule entrée par réflexion.
# commun.pro garde déjà toutes les classes par leur nom et tous les membres publics ; ces règles le redisent
# explicitement pour les points sensibles, au cas où on durcirait commun.pro plus tard.
# ------------------------------------------------------------------------------------------------------------------

# Appelée par réflexion depuis heraliz-quetes (Ponts.java : StrikeManager.countActive(UUID)) — à garder telle quelle.
-keep class fr.heraliz.airstrike.strike.StrikeManager {
    public static int countActive(java.util.UUID);
}

# Pont (Parties / Crochet / Usines, chargés par Class.forName) et paquets réseau : gardés en entier par sécurité.
-keep class fr.heraliz.airstrike.pont.**    { *; }
-keep class fr.heraliz.airstrike.network.** { *; }
