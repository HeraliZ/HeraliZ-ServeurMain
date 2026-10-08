package fr.heraliz.loot.client.rendu.conteneurs;

/**
 * Lot T2b — maisons de campagne, fermes, jardins, caves et ateliers français abandonnés depuis des mois : bois ancien
 * grisé, fonte, émail, tôle, touches de vert. Échelle réelle (1 px = 6,25 cm, le joueur fait 1,8 m), dos au mur
 * (z = 0), objets larges vers +X. Modèles : {@link LotT2bMeubles}, {@link LotT2bMeubles2} (meubles),
 * {@link LotT2bFerme} (ferme et jardin), {@link LotT2bAtelier} (atelier, cave, cuisine), {@link LotT2bLoisirs}
 * (randonnée, camping, pêche, secours) ; pièces communes {@link LotT2bKit}, page {@link LotT2bAtlas}.
 */
public final class LotT2b {
    private LotT2b() {
    }

    public static void enregistrer() {
        // meubles
        Conteneurs.ajouter("t2_armoire", "t2", "bois", "Armoire ancienne", "Old wardrobe", LotT2bMeubles::armoire);
        Conteneurs.ajouter("t2_buffet", "t2", "bois", "Buffet de cuisine", "Kitchen sideboard", LotT2bMeubles2::buffet);
        Conteneurs.ajouter("t2_vaisselier", "t2", "bois", "Vaisselier", "Dresser", LotT2bMeubles2::vaisselier);
        Conteneurs.ajouter("t2_coffre_bois", "t2", "bois", "Coffre de bout de lit", "Blanket chest", LotT2bMeubles2::coffreBois);
        // atelier, ferme, jardin
        Conteneurs.ajouter("t2_etabli", "t2", "bois", "Établi", "Workbench", LotT2bAtelier::etabli);
        Conteneurs.ajouter("t2_servante", "t2", "metal", "Servante d'atelier", "Tool cart", LotT2bAtelier::servante);
        Conteneurs.ajouter("t2_bidons_lait", "t2", "metal", "Bidons à lait", "Milk churns", LotT2bFerme::bidonsLait);
        Conteneurs.ajouter("t2_brouette", "t2", "metal", "Brouette", "Wheelbarrow", LotT2bFerme::brouette);
        Conteneurs.ajouter("t2_bottes_paille", "t2", "tissu", "Bottes de paille", "Straw bales", LotT2bFerme::bottesPaille);
        Conteneurs.collision("t2_bottes_paille", new float[]{0.2f, 0, 0.2f, 13.9f, 11.3f, 9.1f});
        Conteneurs.ajouter("t2_sacs_engrais", "t2", "tissu", "Sacs d'engrais", "Fertilizer sacks", LotT2bFerme::sacsEngrais);
        Conteneurs.ajouter("t2_ruche", "t2", "bois", "Ruche", "Beehive", LotT2bFerme::ruche);
        Conteneurs.ajouter("t2_clapier", "t2", "bois", "Clapier", "Rabbit hutch", LotT2bFerme::clapier);
        // cave, cuisine
        Conteneurs.ajouter("t2_tonneau", "t2", "bois", "Tonneau", "Wine barrel", LotT2bAtelier::tonneau);
        Conteneurs.ajouter("t2_casier_vin", "t2", "bois", "Casier à bouteilles", "Wine rack", LotT2bAtelier::casierVin);
        Conteneurs.ajouter("t2_etagere_conserves", "t2", "metal", "Étagère de cave", "Cellar shelf", LotT2bAtelier::etagereConserves);
        Conteneurs.ajouter("t2_congelateur_coffre", "t2", "metal", "Congélateur coffre", "Chest freezer", LotT2bAtelier::congelateur);
        Conteneurs.ajouter("t2_cuisiniere_bois", "t2", "metal", "Cuisinière à bois", "Wood stove", LotT2bAtelier::cuisiniere);
        Conteneurs.ajouter("t2_machine_coudre", "t2", "metal", "Machine à coudre", "Sewing machine", LotT2bMeubles2::machineCoudre);
        Conteneurs.ajouter("t2_valises_anciennes", "t2", "tissu", "Valises anciennes", "Old suitcases", LotT2bMeubles2::valisesAnciennes);
        // loisirs, secours, jardin
        Conteneurs.ajouter("t2_sac_randonnee", "t2", "tissu", "Sac de randonnée", "Hiking backpack", LotT2bLoisirs::sacRandonnee);
        Conteneurs.ajouter("t2_materiel_camping", "t2", "tissu", "Matériel de camping", "Camping gear", LotT2bLoisirs::materielCamping);
        Conteneurs.ajouter("t2_caisse_peche", "t2", "plastique", "Matériel de pêche", "Fishing gear", LotT2bLoisirs::caissePeche);
        Conteneurs.collision("t2_caisse_peche", new float[]{0.6f, 0, 5.4f, 10.2f, 5.8f, 10.6f});
        Conteneurs.ajouter("t2_trousse_secours", "t2", "metal", "Trousse de premiers secours", "First aid kit", LotT2bLoisirs::trousseSecours);
        Conteneurs.ajouter("t2_tondeuse", "t2", "metal", "Tondeuse", "Lawn mower", LotT2bFerme::tondeuse);
        Conteneurs.ajouter("t2_outils_jardin", "t2", "bois", "Outils de jardin", "Garden tools", LotT2bFerme::outilsJardin);
        Conteneurs.collision("t2_outils_jardin", new float[]{1.2f, 0, 0.5f, 15.9f, 7.4f, 12.6f});
    }
}
