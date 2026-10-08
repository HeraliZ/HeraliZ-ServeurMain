package fr.heraliz.casino.client.render.scene.decor;

import fr.heraliz.casino.decor.DecorNames;
import fr.heraliz.casino.client.render.scene.MachineDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * Modeles 3D des elements de decor, par identifiant (une variante de forme = un modele : fleche vers la gauche,
 * motifs du neon, affiches, bottes de paille, pancartes, graffitis).
 */
public final class DecorDesigns {
    private DecorDesigns() {
    }

    /** Tous les modeles (prepares en arriere-plan au lancement du jeu). */
    public static final String[] IDS;

    static {
        List<String> ids = new ArrayList<>(List.of("enseigne_casino", "panneau_ouvert", "ecran_jackpot", "fleche_neon", "fleche_neon_g",
                "tele_crt", "baril_feu", "poteau_chaine", "lampe_grillagee", "ampoule_fil", "matelas", "touret_table", "potelet_velours",
                "lustre", "fauteuil_club", "porte_coffre",
                // 2.0.0
                "neon_plafond", "caddie", "canape_defonce", "sacs_poubelle", "rechaud", "pneus", "radio_cassette", "chaise_jardin",
                "cabane_carton", "canape_chesterfield", "tabouret_bar", "lampe_banquier", "table_whisky", "casiers_coffres", "lingots",
                "valise_billets", "applique_laiton", "juke_box", "palmier_pot"));
        for (int i = 0; i < DecorNames.NEON.length; i++) ids.add("neon_mural_" + i);
        for (int i = 0; i < DecorNames.POSTERS.length; i++) ids.add("affiche_" + i);
        for (int i = 0; i < 3; i++) ids.add("botte_paille_" + i);
        for (int i = 0; i < DecorNames.SIGNS.length; i++) ids.add("pancarte_carton_" + i);
        for (int i = 0; i < 8; i++) {
            ids.add("graffiti_caisse_" + i);
            ids.add("graffiti_vip_" + i);
        }
        IDS = ids.toArray(new String[0]);
    }

    /** Modele d'un element de decor, ou null si l'identifiant n'en est pas un. */
    public static MachineDesign<?> create(String id) {
        if (id.startsWith("neon_mural_")) return new NeonDesign(index(id, DecorNames.NEON.length));
        if (id.startsWith("affiche_")) return new AfficheDesign(index(id, DecorNames.POSTERS.length));
        if (id.startsWith("botte_paille_")) return new BotteDesign(index(id, 3));
        if (id.startsWith("pancarte_carton_")) return new PancarteDesign(index(id, DecorNames.SIGNS.length));
        if (id.startsWith("graffiti_caisse_")) return new GraffitiDesign(false, index(id, 8));
        if (id.startsWith("graffiti_vip_")) return new GraffitiDesign(true, index(id, 8));
        return switch (id) {
            case "enseigne_casino" -> new EnseigneDesign();
            case "panneau_ouvert" -> new PanneauDesign();
            case "ecran_jackpot" -> new JackpotDesign();
            case "fleche_neon" -> new FlecheDesign(false);
            case "fleche_neon_g" -> new FlecheDesign(true);
            case "tele_crt" -> new TeleDesign();
            case "baril_feu" -> new BarilDesign();
            case "poteau_chaine" -> new PoteauDesign();
            case "lampe_grillagee" -> new LampeDesign();
            case "ampoule_fil" -> new AmpouleDesign();
            case "matelas" -> new MatelasDesign();
            case "touret_table" -> new TouretDesign();
            case "potelet_velours" -> new PoteletDesign();
            case "lustre" -> new LustreDesign();
            case "fauteuil_club" -> new FauteuilDesign();
            case "porte_coffre" -> new CoffreDesign();
            // ---- 2.0.0 : le Trou
            case "neon_plafond" -> new NeonPlafondDesign();
            case "caddie" -> new CaddieDesign();
            case "canape_defonce" -> new CanapeDesign();
            case "sacs_poubelle" -> new SacsDesign();
            case "rechaud" -> new RechaudDesign();
            case "pneus" -> new PneusDesign();
            case "radio_cassette" -> new RadioDesign();
            case "chaise_jardin" -> new ChaiseDesign();
            case "cabane_carton" -> new CabaneDesign();
            // ---- 2.0.0 : le Coffre
            case "canape_chesterfield" -> new ChesterfieldDesign();
            case "tabouret_bar" -> new TabouretDesign();
            case "lampe_banquier" -> new LampeBanquierDesign();
            case "table_whisky" -> new GueridonDesign();
            case "casiers_coffres" -> new CasiersDesign();
            case "lingots" -> new LingotsDesign();
            case "valise_billets" -> new ValiseDesign();
            case "applique_laiton" -> new AppliqueDesign();
            case "juke_box" -> new JukeboxDesign();
            case "palmier_pot" -> new PalmierDesign();
            default -> null;
        };
    }

    private static int index(String id, int n) {
        try {
            return Math.floorMod(Integer.parseInt(id.substring(id.lastIndexOf('_') + 1)), n);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
