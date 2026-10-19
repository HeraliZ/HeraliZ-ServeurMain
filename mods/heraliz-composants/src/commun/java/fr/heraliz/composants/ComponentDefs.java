package fr.heraliz.composants;

import net.minecraft.world.item.Rarity;

import java.util.List;

// Genere par tools/build.py, ne pas modifier a la main.
public final class ComponentDefs {
    /** categories : liste separee par des virgules (armes, soins, viseurs, munitions, divers, vente). */
    public record Def(String id, String categories, Rarity rarity, int stack) {}

    public static final List<Def> DEFS = List.of(
            new Def("canon", "armes", HeralizRarity.UNCOMMON, 16),
            new Def("ressort", "armes", HeralizRarity.COMMON, 32),
            new Def("kit_pieces", "armes", HeralizRarity.UNCOMMON, 16),
            new Def("tissu", "soins", HeralizRarity.COMMON, 32),
            new Def("alcool", "soins", HeralizRarity.COMMON, 16),
            new Def("batterie", "viseurs", HeralizRarity.COMMON, 16),
            new Def("circuit_imprime", "viseurs", HeralizRarity.UNCOMMON, 16),
            new Def("ferraille", "viseurs,munitions", HeralizRarity.COMMON, 64),
            new Def("bocal_poudre", "munitions", HeralizRarity.COMMON, 32),
            new Def("tonnelet_poudre", "munitions", HeralizRarity.COMMON, 16),
            new Def("plaque_recyclee", "protection", HeralizRarity.COMMON, 8),
            new Def("plaque_soudee", "protection", HeralizRarity.UNCOMMON, 8),
            new Def("plaque_metal", "protection", HeralizRarity.RARE, 8),
            new Def("plaque_renforcee", "protection", HeralizRarity.EPIC, 8),
            new Def("scotch", "divers", HeralizRarity.COMMON, 32),
            new Def("caoutchouc", "vente", HeralizRarity.COMMON, 32),
            new Def("corde", "vente", HeralizRarity.COMMON, 32),
            new Def("clou", "construction", HeralizRarity.COMMON, 64),
            new Def("plastique", "vente", HeralizRarity.COMMON, 64),
            new Def("planche", "vente", HeralizRarity.COMMON, 32),
            new Def("collier_casse", "vente", HeralizRarity.COMMON, 16),
            new Def("telephone_casse", "vente", HeralizRarity.COMMON, 16),
            new Def("montre_cassee", "vente", HeralizRarity.COMMON, 16),
            new Def("lunettes_cassees", "vente", HeralizRarity.COMMON, 16),
            new Def("radio_cassee", "vente", HeralizRarity.UNCOMMON, 8),
            new Def("ampoule_grillee", "vente", HeralizRarity.COMMON, 16),
            new Def("briquet", "vente", HeralizRarity.UNCOMMON, 16),
            new Def("plaque_identite", "vente", HeralizRarity.UNCOMMON, 16),
            new Def("cigarettes", "vente", HeralizRarity.UNCOMMON, 16),
            new Def("bague_argent", "vente", HeralizRarity.UNCOMMON, 16),
            new Def("amulette", "vente", HeralizRarity.RARE, 8),
            new Def("montre_gousset", "vente", HeralizRarity.RARE, 8),
            new Def("bague_fiancailles", "vente", HeralizRarity.RARE, 8),
            new Def("appareil_photo", "vente", HeralizRarity.RARE, 8),
            new Def("crucifix", "vente", HeralizRarity.RARE, 8),
            new Def("lingot_argent", "vente", HeralizRarity.EPIC, 4),
            new Def("chronographe", "vente", HeralizRarity.EPIC, 4),
            new Def("collier_perles", "vente", HeralizRarity.EPIC, 4),
            new Def("statuette_jade", "vente", HeralizRarity.EPIC, 4),
            new Def("diamant", "vente", HeralizRarity.LEGENDARY, 1),
            new Def("lingot_or", "vente", HeralizRarity.LEGENDARY, 1),
            new Def("couronne", "vente", HeralizRarity.LEGENDARY, 1),
            new Def("idole_doree", "vente", HeralizRarity.LEGENDARY, 1),
            new Def("oeuf_orne", "vente", HeralizRarity.LEGENDARY, 1),
            new Def("crane_cristal", "vente", HeralizRarity.MYTHIC, 1),
            new Def("sceptre", "vente", HeralizRarity.MYTHIC, 1),
            new Def("calice", "vente", HeralizRarity.MYTHIC, 1),
            new Def("planches", "construction", HeralizRarity.COMMON, 64),
            new Def("brindilles", "construction", HeralizRarity.COMMON, 64),
            new Def("pierres_taillees", "construction", HeralizRarity.COMMON, 64),
            new Def("fragments_metal", "construction", HeralizRarity.UNCOMMON, 64),
            new Def("metal_renforce", "construction", HeralizRarity.RARE, 64),
            new Def("sac_ciment", "construction", HeralizRarity.COMMON, 64),
            new Def("equerres_acier", "construction", HeralizRarity.UNCOMMON, 64),
            new Def("fil_souder", "construction", HeralizRarity.RARE, 64)
    );

    private ComponentDefs() {}
}
