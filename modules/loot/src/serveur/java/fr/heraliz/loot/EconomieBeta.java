package fr.heraliz.loot;

/**
 * Économie de la bêta (03/11, suite de la demande du propriétaire : « les prix des PNJ réduits, le recycleur donne plus,
 * les améliorations pareil, pour que tout soit un peu plus simple ») : mêmes réglages que le loot de la bêta, section
 * [beta] de {@code heraliz_loot-server.toml} (relue toute seule), coupés avec lui par {@code actif = false}.
 * <p>API lue par réflexion par HeraliZ Quêtes (prix des marchands, recycleur, hérals des quêtes) et HeraliZ Clans (arbre
 * d'améliorations, hérals des quêtes de clan) : ne jamais renommer cette classe ni changer cette signature. Côté serveur.
 * Pas d'effet sur la boutique, le casino, l'hôtel des ventes, les caisses ni les objets d'événement.
 */
public final class EconomieBeta {
    private EconomieBeta() {
    }

    /**
     * Facteur en cours (1 : rien ne change, bêta coupée ou réglage pas encore chargé) :
     * <ul>
     * <li>{@code achat} : prix d'achat chez les marchands (0,25 à 1) ;</li>
     * <li>{@code rachat} : prix de rachat (1 à 2, plafond des 50 % du prix d'achat gardé par Quêtes) ;</li>
     * <li>{@code recyclage} : rendement du recycleur (1 à 3) ;</li>
     * <li>{@code ameliorations} : hérals des améliorations de l'arbre du clan (0,25 à 1) ;</li>
     * <li>{@code quetes} : hérals des quêtes (1 à 3).</li>
     * </ul>
     * Clé inconnue : 1.
     */
    public static double facteur(String cle) {
        if (!ConfigLoot.betaActif()) return 1.0;
        return switch (cle) {
            case "achat" -> ConfigLoot.BETA_ACHAT.get();
            case "rachat" -> ConfigLoot.BETA_RACHAT.get();
            case "recyclage" -> ConfigLoot.BETA_RECYCLAGE.get();
            case "ameliorations" -> ConfigLoot.BETA_AMELIORATIONS.get();
            case "quetes" -> ConfigLoot.BETA_QUETES.get();
            default -> 1.0;
        };
    }
}
