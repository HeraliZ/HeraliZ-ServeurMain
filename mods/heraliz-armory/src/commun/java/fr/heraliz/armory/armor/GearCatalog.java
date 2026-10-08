package fr.heraliz.armory.armor;

import java.util.List;

/**
 * GENERE par tools/fusion_090.py : objets gardes de HeralisZ Armor (heralisz_armor_catalog.json), memes stats.
 * Ordre de l'ancien catalogue (ordre des onglets).
 */
public final class GearCatalog {
    private GearCatalog() {}

    public static final List<GearDef> GEAR = List.of(
            new GearDef("gas_mask_black", "equipement", "gas_mask", "curio", "face", "mask", "uncommon", 3, 3, 3, 50, 180, "gasmask_black", "gasmask_black", null, null, List.of("head"), "poison", false),
            new GearDef("gas_mask_white", "equipement", "gas_mask", "curio", "face", "mask", "uncommon", 3, 3, 3, 50, 180, "gasmask_white", "gasmask_white", null, null, List.of("head"), "poison", false),
            new GearDef("gas_mask_military_black", "equipement", "gas_mask", "curio", "face", "mask", "rare", 6, 6, 6, 60, 260, "special_gas_mask_military_black", "special_gas_mask_military_black", null, null, List.of("head"), "poison", false),
            new GearDef("gas_mask_military_white", "equipement", "gas_mask", "curio", "face", "mask", "rare", 6, 6, 6, 60, 260, "special_gas_mask_military_white", "special_gas_mask_military_white", null, null, List.of("head"), "poison", false),
            new GearDef("gas_mask_military_green", "equipement", "gas_mask", "curio", "face", "mask", "rare", 6, 6, 6, 60, 260, "special_gas_mask_military_green", "special_gas_mask_military_green", null, null, List.of("head"), "poison", false),
            new GearDef("gas_mask_military_red", "equipement", "gas_mask", "curio", "face", "mask", "rare", 6, 6, 6, 60, 260, "special_gas_mask_military_red", "special_gas_mask_military_red", null, null, List.of("head"), "poison", false),
            new GearDef("gas_mask_military_purple", "equipement", "gas_mask", "curio", "face", "mask", "rare", 6, 6, 6, 60, 260, "special_gas_mask_military_purple", "special_gas_mask_military_purple", null, null, List.of("head"), "poison", false),
            new GearDef("gas_mask_military_gold", "equipement", "gas_mask", "curio", "face", "mask", "rare", 6, 6, 6, 60, 260, "special_gas_mask_military_gold", "special_gas_mask_military_gold", null, null, List.of("head"), "poison", false),
            new GearDef("ghillie_leafy_forest", "equipement", "ghillie", "curio", "body", "body", "epic", 4, 4, 6, 5, 350, "ghillie_leafy", "ghillie_leafy_forest", "ghillie_leafy", "leafy", List.of("head", "chest", "legs"), "camouflage", false),
            new GearDef("ghillie_leafy_dark_forest", "equipement", "ghillie", "curio", "body", "body", "epic", 4, 4, 6, 5, 350, "ghillie_leafy", "ghillie_leafy_dark_forest", "ghillie_leafy", "leafy", List.of("head", "chest", "legs"), "camouflage", false),
            new GearDef("ghillie_leafy_snow", "equipement", "ghillie", "curio", "body", "body", "epic", 4, 4, 6, 5, 350, "ghillie_leafy", "ghillie_leafy_snow", "ghillie_leafy", "leafy", List.of("head", "chest", "legs"), "camouflage", false),
            new GearDef("ghillie_leafy_cave", "equipement", "ghillie", "curio", "body", "body", "epic", 4, 4, 6, 5, 350, "ghillie_leafy", "ghillie_leafy_cave", "ghillie_leafy", "leafy", List.of("head", "chest", "legs"), "camouflage", false),
            new GearDef("ghillie_leafy_cherry", "equipement", "ghillie", "curio", "body", "body", "epic", 4, 4, 6, 5, 350, "ghillie_leafy", "ghillie_leafy_cherry", "ghillie_leafy", "leafy", List.of("head", "chest", "legs"), "camouflage", false),
            new GearDef("ghillie_leafy_desert", "equipement", "ghillie", "curio", "body", "body", "epic", 4, 4, 6, 5, 350, "ghillie_leafy", "ghillie_leafy_desert", "ghillie_leafy", "leafy", List.of("head", "chest", "legs"), "camouflage", false),
            new GearDef("ghillie_leafy_swamp", "equipement", "ghillie", "curio", "body", "body", "epic", 4, 4, 6, 5, 350, "ghillie_leafy", "ghillie_leafy_swamp", "ghillie_leafy", "leafy", List.of("head", "chest", "legs"), "camouflage", false),
            new GearDef("ghillie_leafy_badlands", "equipement", "ghillie", "curio", "body", "body", "epic", 4, 4, 6, 5, 350, "ghillie_leafy", "ghillie_leafy_badlands", "ghillie_leafy", "leafy", List.of("head", "chest", "legs"), "camouflage", false),
            new GearDef("ghillie_leafy_black", "equipement", "ghillie", "curio", "body", "body", "epic", 4, 4, 6, 5, 350, "ghillie_leafy", "ghillie_leafy_black", "ghillie_leafy", "leafy", List.of("head", "chest", "legs"), "camouflage", false),
            new GearDef("ghillie_full_forest", "equipement", "ghillie", "curio", "body", "body", "legendary", 5, 5, 8, 5, 450, "ghillie_full", "ghillie_full_forest", "ghillie_full", "full", List.of("head", "chest", "legs"), "camouflage", false),
            new GearDef("ghillie_full_dark_forest", "equipement", "ghillie", "curio", "body", "body", "legendary", 5, 5, 8, 5, 450, "ghillie_full", "ghillie_full_dark_forest", "ghillie_full", "full", List.of("head", "chest", "legs"), "camouflage", false),
            new GearDef("ghillie_full_snow", "equipement", "ghillie", "curio", "body", "body", "legendary", 5, 5, 8, 5, 450, "ghillie_full", "ghillie_full_snow", "ghillie_full", "full", List.of("head", "chest", "legs"), "camouflage", false),
            new GearDef("ghillie_full_cave", "equipement", "ghillie", "curio", "body", "body", "legendary", 5, 5, 8, 5, 450, "ghillie_full", "ghillie_full_cave", "ghillie_full", "full", List.of("head", "chest", "legs"), "camouflage", false),
            new GearDef("ghillie_full_cherry", "equipement", "ghillie", "curio", "body", "body", "legendary", 5, 5, 8, 5, 450, "ghillie_full", "ghillie_full_cherry", "ghillie_full", "full", List.of("head", "chest", "legs"), "camouflage", false),
            new GearDef("ghillie_full_desert", "equipement", "ghillie", "curio", "body", "body", "legendary", 5, 5, 8, 5, 450, "ghillie_full", "ghillie_full_desert", "ghillie_full", "full", List.of("head", "chest", "legs"), "camouflage", false),
            new GearDef("ghillie_full_swamp", "equipement", "ghillie", "curio", "body", "body", "legendary", 5, 5, 8, 5, 450, "ghillie_full", "ghillie_full_swamp", "ghillie_full", "full", List.of("head", "chest", "legs"), "camouflage", false),
            new GearDef("ghillie_full_badlands", "equipement", "ghillie", "curio", "body", "body", "legendary", 5, 5, 8, 5, 450, "ghillie_full", "ghillie_full_badlands", "ghillie_full", "full", List.of("head", "chest", "legs"), "camouflage", false),
            new GearDef("ghillie_full_black", "equipement", "ghillie", "curio", "body", "body", "legendary", 5, 5, 8, 5, 450, "ghillie_full", "ghillie_full_black", "ghillie_full", "full", List.of("head", "chest", "legs"), "camouflage", false),
            new GearDef("armband_black", "equipement", "armband", "curio", "bracelet", "bracelet", "common", 1, 1, 1, 1, 120, "armband", "armband_black", null, null, List.of("chest"), null, false),
            new GearDef("armband_blue", "equipement", "armband", "curio", "bracelet", "bracelet", "common", 1, 1, 1, 1, 120, "armband", "armband_blue", null, null, List.of("chest"), null, false),
            new GearDef("armband_gray", "equipement", "armband", "curio", "bracelet", "bracelet", "common", 1, 1, 1, 1, 120, "armband", "armband_gray", null, null, List.of("chest"), null, false),
            new GearDef("armband_orange", "equipement", "armband", "curio", "bracelet", "bracelet", "common", 1, 1, 1, 1, 120, "armband", "armband_orange", null, null, List.of("chest"), null, false),
            new GearDef("armband_pink", "equipement", "armband", "curio", "bracelet", "bracelet", "common", 1, 1, 1, 1, 120, "armband", "armband_pink", null, null, List.of("chest"), null, false),
            new GearDef("armband_white", "equipement", "armband", "curio", "bracelet", "bracelet", "common", 1, 1, 1, 1, 120, "armband", "armband_white", null, null, List.of("chest"), null, false),
            new GearDef("armband_yellow", "equipement", "armband", "curio", "bracelet", "bracelet", "common", 1, 1, 1, 1, 120, "armband", "armband_yellow", null, null, List.of("chest"), null, false),
            new GearDef("pumpkin_head", "event", "halloween", "armor", "head", null, "event", 1, 1, 1, 1, 80, "halloween_pack/pumpkin_head", "halloween_pack/pumpkin_head", null, null, List.of("head"), null, false),
            new GearDef("skeleton_head", "event", "halloween", "armor", "head", null, "event", 1, 1, 1, 1, 80, "halloween_pack/skeleton_head", "halloween_pack/skeleton_head", null, null, List.of("head"), null, false),
            new GearDef("ghost_head", "event", "halloween", "armor", "head", null, "event", 1, 1, 1, 1, 80, "halloween_pack/ghost_head", "halloween_pack/ghost_head", null, null, List.of("head"), null, false),
            new GearDef("mummy_head", "event", "halloween", "armor", "head", null, "event", 1, 1, 1, 1, 80, "halloween_pack/mummy", "halloween_pack/mummy", null, null, List.of("head"), null, false),
            new GearDef("frankenstein_head", "event", "halloween", "armor", "head", null, "event", 1, 1, 1, 1, 80, "halloween_pack/franck", "halloween_pack/franck", null, null, List.of("head"), null, false),
            new GearDef("witch_hat", "event", "halloween", "armor", "head", null, "event", 1, 1, 1, 1, 80, "halloween_pack/witch_hat", "halloween_pack/witch_hat", null, null, List.of("head"), null, false),
            new GearDef("cauldron_hat", "event", "halloween", "armor", "head", null, "event", 1, 1, 1, 1, 80, "halloween_pack/cauldron_hat", "halloween_pack/cauldron_hat", null, null, List.of("head"), null, false),
            new GearDef("cemetery_hat", "event", "halloween", "armor", "head", null, "event", 1, 1, 1, 1, 80, "halloween_pack/cimetery_hat", "halloween_pack/cimetery_hat", null, null, List.of("head"), null, false),
            new GearDef("knife_hat", "event", "halloween", "armor", "head", null, "event", 1, 1, 1, 1, 80, "halloween_pack/knife_hat", "halloween_pack/knife_hat", null, null, List.of("head"), null, false),
            new GearDef("big_eye", "event", "halloween", "armor", "head", null, "event", 1, 1, 1, 1, 80, "halloween_pack/big_eye", "halloween_pack/big_eye", null, null, List.of("head"), null, false),
            new GearDef("clown_mask", "event", "halloween", "armor", "head", null, "event", 1, 1, 1, 1, 80, "clown", "clown", null, null, List.of("head"), null, false),
            new GearDef("spider_legs", "event", "halloween", "curio", "body", "body", "event", 1, 1, 1, 1, 80, "halloween_pack/spider_legs", "halloween_pack/spider_legs", null, null, List.of("chest"), null, false),
            new GearDef("head_bag_black", "event", "head_bag", "armor", "head", null, "event", 1, 1, 1, 3, 80, "bag_black", "bag_black", null, null, List.of("head"), null, false),
            new GearDef("head_bag_blue", "event", "head_bag", "armor", "head", null, "event", 1, 1, 1, 3, 80, "bag_blue", "bag_blue", null, null, List.of("head"), null, false),
            new GearDef("head_bag_red", "event", "head_bag", "armor", "head", null, "event", 1, 1, 1, 3, 80, "bag_red", "bag_red", null, null, List.of("head"), null, false),
            new GearDef("head_bag_white", "event", "head_bag", "armor", "head", null, "event", 1, 1, 1, 3, 80, "bag_white", "bag_white", null, null, List.of("head"), null, false),
            new GearDef("head_bag_yellow", "event", "head_bag", "armor", "head", null, "event", 1, 1, 1, 3, 80, "bag_yellow", "bag_yellow", null, null, List.of("head"), null, false)
    );
}
