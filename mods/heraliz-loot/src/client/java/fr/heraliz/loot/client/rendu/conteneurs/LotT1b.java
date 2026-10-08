package fr.heraliz.loot.client.rendu.conteneurs;

/**
 * Lot T1b (tiers T1, loot le plus commun) : logements abandonnés depuis des mois — cuisine, chambre, salon, salle de
 * bain, bureau. Intérieurs des années 80-2000 aux teintes passées, poussière, moisissure, portes arrachées.
 * Matières : page {@link LotT1bAtlas} + usure commune {@link AtlasUsure} + atlas civil.
 */
public final class LotT1b {
    private LotT1b() {
    }

    public static void enregistrer() {
        Conteneurs.ajouter("t1_table_chevet", "t1", "bois", "Table de chevet", "Bedside table", LotT1bChambre::tableChevet);
        Conteneurs.verre("t1_table_chevet", LotT1bChambre::tableChevetVerre);
        Conteneurs.ajouter("t1_micro_ondes", "t1", "metal", "Micro-ondes", "Microwave oven", LotT1bCuisine::microOndes);
        Conteneurs.verre("t1_micro_ondes", LotT1bCuisine::microOndesVerre);
        Conteneurs.ajouter("t1_cuisiniere", "t1", "metal", "Cuisinière", "Cooker", LotT1bCuisine::cuisiniere);
        Conteneurs.ajouter("t1_lave_linge", "t1", "metal", "Lave-linge", "Washing machine", LotT1bCuisine::laveLinge);
        Conteneurs.verre("t1_lave_linge", LotT1bCuisine::laveLingeVerre);
        Conteneurs.ajouter("t1_element_cuisine", "t1", "bois", "Meuble de cuisine", "Kitchen cabinet", LotT1bCuisine2::elementCuisine);
        Conteneurs.ajouter("t1_poubelle_cuisine", "t1", "metal", "Poubelle à pédale", "Pedal bin", LotT1bCuisine2::poubelleCuisine);
        Conteneurs.ajouter("t1_panier_linge", "t1", "plastique", "Panier à linge", "Laundry basket", LotT1bChambre2::panierLinge);
        Conteneurs.ajouter("t1_matelas", "t1", "tissu", "Matelas", "Mattress", LotT1bChambre2::matelas);
        Conteneurs.ajouter("t1_cartons_demenagement", "t1", "carton", "Cartons de déménagement", "Moving boxes", LotT1bChambre2::cartons);
        Conteneurs.ajouter("t1_bureau_ecolier", "t1", "bois", "Bureau d'écolier", "School desk", LotT1bBureau::bureauEcolier);
        Conteneurs.ajouter("t1_classeur_metal", "t1", "metal", "Armoire à dossiers", "Filing cabinet", LotT1bBureau::classeurMetal);
        Conteneurs.ajouter("t1_vestiaire", "t1", "metal", "Vestiaire métallique", "Metal locker", LotT1bBureau::vestiaire);
        Conteneurs.ajouter("t1_etagere_garage", "t1", "metal", "Étagère de garage", "Garage shelving", LotT1bBureau::etagereGarage);
        Conteneurs.ajouter("t1_bibliotheque", "t1", "bois", "Petite bibliothèque", "Small bookcase", LotT1bBureau::bibliotheque);
        Conteneurs.ajouter("t1_coffre_jouets", "t1", "bois", "Coffre à jouets", "Toy chest", LotT1bChambre2::coffreJouets);
        Conteneurs.ajouter("t1_poussette", "t1", "tissu", "Poussette abandonnée", "Abandoned stroller", LotT1bSalon::poussette);
        Conteneurs.ajouter("t1_canape", "t1", "tissu", "Canapé éventré", "Gutted sofa", LotT1bSalon::canape);
        Conteneurs.ajouter("t1_fauteuil", "t1", "tissu", "Fauteuil", "Armchair", LotT1bSalon::fauteuil);
        Conteneurs.ajouter("t1_meuble_vasque", "t1", "bois", "Meuble de salle de bain", "Bathroom vanity", LotT1bBureau::meubleVasque);
        Conteneurs.verre("t1_meuble_vasque", LotT1bBureau::meubleVasqueVerre);
        Conteneurs.ajouter("t1_range_chaussures", "t1", "bois", "Meuble à chaussures", "Shoe rack", LotT1bChambre2::rangeChaussures);
        Conteneurs.ajouter("t1_grand_cabas", "t1", "plastique", "Grand cabas", "Big tote bag", LotT1bChambre2::grandCabas);
        Conteneurs.ajouter("t1_tiroir_tombe", "t1", "bois", "Tiroir renversé", "Fallen drawer", LotT1bCuisine2::tiroirTombe);
        Conteneurs.ajouter("t1_mini_frigo", "t1", "metal", "Mini-frigo", "Mini fridge", LotT1bCuisine2::miniFrigo);
        Conteneurs.ajouter("t1_chariot_menage", "t1", "plastique", "Chariot de ménage", "Cleaning cart", LotT1bCuisine2::chariotMenage);
        Conteneurs.ajouter("t1_meuble_tv", "t1", "bois", "Meuble télé", "TV stand", LotT1bSalon::meubleTv);
        Conteneurs.verre("t1_meuble_tv", LotT1bSalon::meubleTvVerre);
        Conteneurs.collision("t1_meuble_tv", new float[]{1.4f, 0, 0.4f, 20.6f, 15.2f, 7.8f});
    }
}
