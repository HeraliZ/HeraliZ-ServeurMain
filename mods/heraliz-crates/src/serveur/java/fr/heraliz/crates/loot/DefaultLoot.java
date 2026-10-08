package fr.heraliz.crates.loot;

import fr.heraliz.crates.crate.CrateType;

import java.util.ArrayList;
import java.util.List;

import static fr.heraliz.crates.loot.LootRarity.COMMON;
import static fr.heraliz.crates.loot.LootRarity.EPIC;
import static fr.heraliz.crates.loot.LootRarity.LEGENDARY;
import static fr.heraliz.crates.loot.LootRarity.MYTHIC;
import static fr.heraliz.crates.loot.LootRarity.RARE;
import static fr.heraliz.crates.loot.LootRarity.UNCOMMON;

/**
 * Butin par defaut (objets vanilla) ecrit au premier lancement.
 * A modifier ensuite dans config/heralizcrates/<caisse>.json ou avec /heraliz loot add.
 */
final class DefaultLoot {
    private DefaultLoot() {
    }

    private static final String SHARP = "{Enchantments:[{id:\"minecraft:sharpness\",lvl:%ds}]}";
    private static final String PROT = "{Enchantments:[{id:\"minecraft:protection\",lvl:%ds},{id:\"minecraft:unbreaking\",lvl:3s}]}";

    static List<LootEntry> of(CrateType type) {
        List<LootEntry> l = new ArrayList<>();
        switch (type) {
            case SUJET_N07 -> {
                l.add(e("minecraft:honey_bottle", 4, 18, COMMON));
                l.add(e("minecraft:milk_bucket", 1, 15, COMMON).name("&aAntidote N-07"));
                l.add(e("minecraft:slime_ball", 8, 14, COMMON));
                l.add(e("minecraft:splash_potion", 2, 14, UNCOMMON).nbt("{Potion:\"minecraft:strong_healing\"}"));
                l.add(e("minecraft:splash_potion", 2, 10, UNCOMMON).nbt("{Potion:\"minecraft:strong_poison\"}"));
                l.add(e("minecraft:glowstone_dust", 16, 12, UNCOMMON));
                l.add(e("minecraft:experience_bottle", 16, 10, RARE));
                l.add(e("minecraft:potion", 1, 10, RARE).nbt("{Potion:\"minecraft:strong_regeneration\"}").name("&aSérum N-07"));
                l.add(e("minecraft:golden_apple", 3, 8, RARE));
                l.add(e("minecraft:diamond", 4, 6, EPIC));
                l.add(e("minecraft:totem_of_undying", 1, 3, LEGENDARY));
                l.add(e("minecraft:enchanted_golden_apple", 1, 1, MYTHIC));
            }
            case SYLVORN -> {
                l.add(e("minecraft:oak_sapling", 8, 16, COMMON));
                l.add(e("minecraft:bone_meal", 32, 16, COMMON));
                l.add(e("minecraft:glow_berries", 16, 14, COMMON));
                l.add(e("minecraft:golden_carrot", 16, 14, UNCOMMON));
                l.add(e("minecraft:arrow", 32, 14, UNCOMMON));
                l.add(e("minecraft:emerald", 12, 12, UNCOMMON));
                l.add(e("minecraft:bow", 1, 8, RARE).nbt("{Enchantments:[{id:\"minecraft:power\",lvl:4s},{id:\"minecraft:punch\",lvl:1s},{id:\"minecraft:unbreaking\",lvl:3s}]}").name("&aArc de Sylvorn"));
                l.add(e("minecraft:golden_apple", 3, 8, RARE));
                l.add(e("minecraft:diamond", 4, 6, EPIC));
                l.add(e("minecraft:enchanted_book", 1, 3, EPIC).nbt("{StoredEnchantments:[{id:\"minecraft:mending\",lvl:1s}]}"));
                l.add(e("minecraft:enchanted_golden_apple", 1, 1, LEGENDARY));
            }
            case MERE_DES_RUINES -> {
                l.add(e("minecraft:string", 24, 16, COMMON));
                l.add(e("minecraft:spider_eye", 8, 14, COMMON));
                l.add(e("minecraft:cobweb", 4, 12, UNCOMMON));
                l.add(e("minecraft:ender_pearl", 8, 12, UNCOMMON));
                l.add(e("minecraft:fermented_spider_eye", 4, 10, UNCOMMON));
                l.add(e("minecraft:diamond", 5, 12, RARE));
                l.add(e("minecraft:golden_apple", 4, 10, RARE));
                l.add(e("minecraft:netherite_scrap", 2, 7, EPIC));
                l.add(e("minecraft:totem_of_undying", 1, 6, EPIC));
                l.add(e("minecraft:netherite_sword", 1, 3, LEGENDARY).nbt("{Enchantments:[{id:\"minecraft:sharpness\",lvl:5s},{id:\"minecraft:bane_of_arthropods\",lvl:5s},{id:\"minecraft:unbreaking\",lvl:3s}]}").name("&4&lCrochet de la Mère"));
                l.add(e("minecraft:elytra", 1, 2, LEGENDARY));
                l.add(e("minecraft:enchanted_golden_apple", 2, 1, MYTHIC));
            }
            case VULKHAR -> {
                l.add(e("minecraft:blaze_rod", 8, 16, COMMON));
                l.add(e("minecraft:fire_charge", 8, 14, COMMON));
                l.add(e("minecraft:magma_cream", 8, 12, UNCOMMON));
                l.add(e("minecraft:potion", 1, 12, UNCOMMON).nbt("{Potion:\"minecraft:long_fire_resistance\"}"));
                l.add(e("minecraft:gold_block", 3, 12, RARE));
                l.add(e("minecraft:diamond", 6, 12, RARE));
                l.add(e("minecraft:netherite_scrap", 3, 8, EPIC));
                l.add(e("minecraft:diamond_sword", 1, 6, EPIC).nbt("{Enchantments:[{id:\"minecraft:sharpness\",lvl:5s},{id:\"minecraft:fire_aspect\",lvl:2s},{id:\"minecraft:unbreaking\",lvl:3s}]}").name("&6Croc de Vulkhar"));
                l.add(e("minecraft:netherite_ingot", 2, 5, LEGENDARY));
                l.add(e("minecraft:netherite_chestplate", 1, 3, LEGENDARY).nbt("{Enchantments:[{id:\"minecraft:fire_protection\",lvl:4s},{id:\"minecraft:unbreaking\",lvl:3s}]}").name("&6&lÉcailles de Vulkhar"));
                l.add(e("minecraft:dragon_head", 1, 2, LEGENDARY));
                l.add(e("minecraft:enchanted_golden_apple", 2, 1, MYTHIC));
            }
            case PORTEUR_DE_PESTE -> {
                l.add(e("minecraft:milk_bucket", 1, 16, COMMON).name("&fRemède du Médecin"));
                l.add(e("minecraft:bread", 12, 16, COMMON));
                l.add(e("minecraft:splash_potion", 3, 14, UNCOMMON).nbt("{Potion:\"minecraft:strong_poison\"}"));
                l.add(e("minecraft:splash_potion", 2, 12, UNCOMMON).nbt("{Potion:\"minecraft:strong_healing\"}"));
                l.add(e("minecraft:iron_ingot", 16, 12, UNCOMMON));
                l.add(e("minecraft:experience_bottle", 16, 10, RARE));
                l.add(e("minecraft:golden_apple", 3, 8, RARE));
                l.add(e("minecraft:diamond", 4, 6, EPIC));
                l.add(e("minecraft:diamond_axe", 1, 5, EPIC).nbt("{Enchantments:[{id:\"minecraft:sharpness\",lvl:4s},{id:\"minecraft:unbreaking\",lvl:3s}]}").name("&eFaux de la Peste"));
                l.add(e("minecraft:totem_of_undying", 1, 3, LEGENDARY));
                l.add(e("minecraft:enchanted_golden_apple", 1, 1, MYTHIC));
            }
            case CHEVALIER_SANS_VISAGE -> {
                l.add(e("minecraft:iron_ingot", 16, 16, COMMON));
                l.add(e("minecraft:shield", 1, 14, UNCOMMON));
                l.add(e("minecraft:iron_chestplate", 1, 12, UNCOMMON).nbt(String.format(PROT, 3)));
                l.add(e("minecraft:saddle", 1, 10, UNCOMMON));
                l.add(e("minecraft:diamond_horse_armor", 1, 8, RARE));
                l.add(e("minecraft:diamond", 5, 12, RARE));
                l.add(e("minecraft:golden_apple", 4, 10, RARE));
                l.add(e("minecraft:diamond_helmet", 1, 6, EPIC).nbt(String.format(PROT, 4)).name("&5Heaume Sans Visage"));
                l.add(e("minecraft:totem_of_undying", 1, 5, EPIC));
                l.add(e("minecraft:netherite_sword", 1, 3, LEGENDARY).nbt("{Enchantments:[{id:\"minecraft:sharpness\",lvl:5s},{id:\"minecraft:sweeping\",lvl:3s},{id:\"minecraft:unbreaking\",lvl:3s}]}").name("&5&lLame du Chevalier"));
                l.add(e("minecraft:netherite_ingot", 1, 3, LEGENDARY));
                l.add(e("minecraft:enchanted_golden_apple", 2, 1, MYTHIC));
            }
            case KRAKEN -> {
                l.add(e("minecraft:prismarine_crystals", 16, 16, COMMON));
                l.add(e("minecraft:ink_sac", 16, 14, COMMON));
                l.add(e("minecraft:glow_ink_sac", 8, 12, UNCOMMON));
                l.add(e("minecraft:nautilus_shell", 2, 12, UNCOMMON));
                l.add(e("minecraft:potion", 1, 12, UNCOMMON).nbt("{Potion:\"minecraft:long_water_breathing\"}"));
                l.add(e("minecraft:turtle_helmet", 1, 8, RARE));
                l.add(e("minecraft:diamond", 6, 12, RARE));
                l.add(e("minecraft:heart_of_the_sea", 1, 5, EPIC));
                l.add(e("minecraft:totem_of_undying", 1, 5, EPIC));
                l.add(e("minecraft:trident", 1, 3, LEGENDARY).nbt("{Enchantments:[{id:\"minecraft:loyalty\",lvl:3s},{id:\"minecraft:impaling\",lvl:5s},{id:\"minecraft:unbreaking\",lvl:3s}]}").name("&3&lTrident du Kraken"));
                l.add(e("minecraft:netherite_ingot", 1, 3, LEGENDARY));
                l.add(e("minecraft:enchanted_golden_apple", 2, 1, MYTHIC));
            }
            case VOTE -> {
                l.add(e("minecraft:experience_bottle", 8, 20, COMMON));
                l.add(e("minecraft:golden_carrot", 16, 20, COMMON));
                l.add(e("minecraft:emerald", 8, 18, UNCOMMON));
                l.add(e("minecraft:iron_ingot", 16, 15, UNCOMMON));
                l.add(e("minecraft:name_tag", 1, 10, UNCOMMON));
                l.add(e("minecraft:diamond", 2, 12, RARE));
                l.add(e("minecraft:ender_pearl", 4, 10, RARE));
                l.add(e("minecraft:paper", 1, 5, RARE).name("&b+1 Clé de Vote").noItem()
                        .command("heraliz givekey {player} vote 1"));
                l.add(e("minecraft:diamond", 5, 4, EPIC));
                l.add(e("minecraft:netherite_scrap", 1, 2, LEGENDARY));
            }
            case QUETE -> {
                l.add(e("minecraft:experience_bottle", 12, 20, COMMON));
                l.add(e("minecraft:cooked_beef", 16, 18, COMMON));
                l.add(e("minecraft:compass", 1, 10, UNCOMMON));
                l.add(e("minecraft:emerald", 12, 15, UNCOMMON));
                l.add(e("minecraft:name_tag", 2, 10, UNCOMMON));
                l.add(e("minecraft:diamond", 3, 12, RARE));
                l.add(e("minecraft:golden_apple", 2, 10, RARE));
                l.add(e("minecraft:paper", 1, 5, RARE).name("&6+1 Clé de Quêtes").noItem()
                        .command("heraliz givekey {player} quete 1"));
                l.add(e("minecraft:enchanted_book", 1, 4, EPIC).nbt("{StoredEnchantments:[{id:\"minecraft:mending\",lvl:1s}]}"));
                l.add(e("minecraft:totem_of_undying", 1, 3, EPIC));
                l.add(e("minecraft:netherite_ingot", 1, 1, LEGENDARY));
            }
            case EVENT -> {
                l.add(e("minecraft:firework_rocket", 32, 20, COMMON));
                l.add(e("minecraft:cake", 1, 15, COMMON));
                l.add(e("minecraft:name_tag", 3, 12, UNCOMMON));
                l.add(e("minecraft:diamond", 6, 10, RARE));
                l.add(e("minecraft:music_disc_pigstep", 1, 6, RARE));
                l.add(e("minecraft:shulker_shell", 2, 4, EPIC));
                l.add(e("minecraft:totem_of_undying", 1, 4, EPIC));
                l.add(e("minecraft:enchanted_golden_apple", 1, 3, EPIC));
                l.add(e("minecraft:paper", 1, 4, EPIC).name("&d+1 Clé d'Événement").noItem()
                        .command("heraliz givekey {player} event 1"));
                l.add(e("minecraft:elytra", 1, 2, LEGENDARY));
            }
            // caisse Camos : tous les camos d'armes de heralisz_camo, par rarete (communs 9 % chacun ... skins X 0,1 %)
            case CAMO -> {
                for (String c : new String[]{"crimson", "navy", "arctic", "olive", "sand", "sakura"}) l.add(camo(c, 90, COMMON));
                for (String c : new String[]{"carbon", "woodland", "digital", "tiger", "rust", "damascus"}) l.add(camo(c, 45, UNCOMMON));
                for (String c : new String[]{"ice", "toxic", "plasma", "circuit"}) l.add(camo(c, 25, RARE));
                for (String c : new String[]{"gold", "honeycomb", "lava", "galaxy", "solar", "borealis", "afterlife", "bioluminescence",
                        "genesis", "dark_aether", "infestation", "atomic", "obsidian", "glitch", "amethyst", "diamond", "black_ice"}) {
                    l.add(camo(c, 4, EPIC));
                }
                for (String c : new String[]{"orion", "singularity", "nexus_horizon", "dark_matter_nuclear", "liquid_chrome", "dragon", "storm", "dark_matter"}) {
                    l.add(camo(c, 2, LEGENDARY));
                }
                for (String c : new String[]{"seraphin", "chaines_enfer", "texture_manquante", "money_gun", "surchauffe", "trophee"}) {
                    l.add(e("heralisz_camo:skinx_" + c, 1, 1, MYTHIC));
                }
            }
            // caisse Pendentifs : les repliques miniatures d'armes et de couteaux de heralisz_camo (gemmes les plus rares)
            case PENDENTIF -> {
                for (String c : new String[]{"deagle_gold", "vector_toxic", "scar_plasma", "m200_black_ice"}) l.add(charm(c, 110, COMMON));
                for (String c : new String[]{"ak47_dark_matter", "awp_dragon"}) l.add(charm(c, 80, UNCOMMON));
                for (String c : new String[]{"butterfly_fade", "butterfly_marble_fade", "karambit_crimson_web", "karambit_tiger_tooth",
                        "karambit_case_hardened", "m9_lore", "m9_autotronic", "m9_slaughter"}) {
                    l.add(charm(c, 40, EPIC));
                }
                for (String c : new String[]{"butterfly_ruby", "butterfly_sapphire", "butterfly_gamma_emerald", "butterfly_black_pearl"}) {
                    l.add(charm(c, 20, LEGENDARY));
                }
            }
            // caisse Stickers : les 32 stickers de heralisz_camo en papier, une partie en holo, metallises et en or, + le module
            // StatTrack (papier 55 %, holo 22 %, metallises 12 %, or 4 %, StatTrack 4 %)
            case STICKER -> {
                int first = l.size();
                for (String st : STICKERS) l.add(e("heralisz_camo:sticker_" + st, 1, 15, COMMON));
                for (String st : new String[]{"ace", "headshot", "clutch", "one_tap", "no_scope", "reaper", "wolf", "boom", "skull_king",
                        "biohazard", "phoenix", "oni", "kitsune", "volt", "diamond", "ggwp"}) {
                    l.add(e("heralisz_camo:sticker_" + st + "_holo", 1, 14, RARE));
                }
                for (String st : new String[]{"ace", "reaper", "wolf", "skull_king", "phoenix", "oni", "katana", "volt", "diamond", "blood_money"}) {
                    l.add(e("heralisz_camo:sticker_" + st + "_foil", 1, 12, EPIC));
                }
                for (String st : new String[]{"ace", "skull_king", "phoenix", "diamond", "ggwp", "oni"}) {
                    l.add(e("heralisz_camo:sticker_" + st + "_gold", 1, 7, LEGENDARY));
                }
                l.add(e("heralisz_camo:stattrack_module", 1, 40, EPIC));
                int rest = TOTAL - total(l);
                for (int i = 0; i < STICKERS.length; i++) l.get(first + i).weight += rest / STICKERS.length + (i < rest % STICKERS.length ? 1 : 0);
            }
            // caisse Chantier : de quoi batir sa base. Materiaux en lots (heralisz_utils, props cfm), clotures et barbeles, puis
            // les portes, cartes et lecteurs de heralisz_bunker du plus courant au plus rare (porte de silo : 0,2 %)
            case BUILD -> {
                l.add(e("heralisz_utils:wood_plate", 8, 70, COMMON));
                l.add(e("cfm:arkaniaz_red_brick", 16, 60, COMMON));
                l.add(e("cfm:arkaniaz_hollow_brick", 16, 60, COMMON));
                l.add(e("cfm:beton", 16, 55, COMMON));
                l.add(e("heralisz_utils:iron_plate", 4, 55, COMMON));
                l.add(e("heralisz_utils:glass", 4, 45, COMMON));
                l.add(e("cfm:arkaniaz_iron_rod", 8, 40, COMMON));
                l.add(e("cfm:gofro_metal", 8, 40, COMMON));
                l.add(e("cfm:wooden_pallet_1", 2, 30, COMMON));
                l.add(e("cfm:arkaniaz_steel_pipe", 4, 30, COMMON));
                l.add(e("heralisz_utils:steel_plate", 4, 35, UNCOMMON));
                l.add(e("heralisz_utils:aluminum", 4, 25, UNCOMMON));
                l.add(e("heralisz_utils:copper_coil", 2, 20, UNCOMMON));
                l.add(e("cfm:green_beton", 8, 25, UNCOMMON));
                l.add(e("cfm:dark_metal_2", 8, 25, UNCOMMON));
                l.add(e("cfm:zombie_chainlinkfence", 6, 25, UNCOMMON));
                l.add(e("cfm:prison_barbed_fence", 4, 25, UNCOMMON));
                l.add(e("cfm:city_security_fence", 4, 20, UNCOMMON));
                l.add(e("heralisz_bunker:wooden_barricade", 2, 25, UNCOMMON));
                l.add(e("cfm:arkaniaz_spotlight", 1, 15, UNCOMMON));
                l.add(e("heralisz_utils:titanium_plate", 2, 18, RARE));
                l.add(e("heralisz_utils:carbon", 2, 15, RARE));
                l.add(e("heralisz_bunker:metal_door", 1, 18, RARE));
                l.add(e("heralisz_bunker:door_bunker", 1, 15, RARE));
                l.add(e("heralisz_bunker:keycard_green", 1, 15, RARE));
                l.add(e("heralisz_bunker:card_reader_green", 1, 12, RARE));
                l.add(e("cfm:container_green", 1, 10, RARE));
                l.add(e("cfm:military_tent_1", 1, 10, RARE));
                l.add(e("cfm:storage_cabinet_metal_27", 1, 10, RARE));
                l.add(e("heralisz_bunker:fire_door", 1, 9, EPIC));
                l.add(e("heralisz_bunker:sliding_blast_door", 1, 7, EPIC));
                l.add(e("heralisz_bunker:secure_access_door", 1, 7, EPIC));
                l.add(e("heralisz_bunker:round_airlock_door", 1, 6, EPIC));
                l.add(e("heralisz_bunker:keycard_blue", 1, 7, EPIC));
                l.add(e("heralisz_bunker:card_reader_blue", 1, 6, EPIC));
                l.add(e("cfm:container_camo2", 1, 5, EPIC));
                l.add(e("heralisz_bunker:vault_door", 1, 4, LEGENDARY));
                l.add(e("heralisz_bunker:large_vehicle_door", 1, 4, LEGENDARY));
                l.add(e("heralisz_bunker:silo_hatch", 1, 3, LEGENDARY));
                l.add(e("heralisz_bunker:keycard_red", 1, 3, LEGENDARY));
                l.add(e("heralisz_bunker:card_reader_red", 1, 3, LEGENDARY));
                l.add(e("heralisz_bunker:silo_hatch_large", 1, 2, MYTHIC));
                l.add(e("heralisz_bunker:qe_containment_door", 1, 2, MYTHIC));
                l.get(0).weight += TOTAL - total(l);
            }
            // caisse Trauma : les soins de heralisz_heal, des plus courants (anti-douleurs, nourriture) aux kits chirurgicaux,
            // en lots plus gros pour les tirages rares ; le totem d'immortalite en seconde vie (0,3 %)
            case TRAUMA -> {
                l.add(e("heralisz_tactical:consumable", 2, 110, COMMON).nbt("{ConsumableId:\"heralisz_heal:ibuprofen\"}"));
                l.add(e("heralisz_tactical:consumable", 2, 100, COMMON).nbt("{ConsumableId:\"heralisz_heal:condensed_milk\"}"));
                l.add(e("heralisz_tactical:consumable", 1, 90, COMMON).nbt("{ConsumableId:\"heralisz_heal:vaseline\"}"));
                l.add(e("heralisz_tactical:consumable", 1, 90, COMMON).nbt("{ConsumableId:\"heralisz_heal:goldenstar\"}"));
                l.add(e("heralisz_tactical:consumable", 2, 80, COMMON).nbt("{ConsumableId:\"heralisz_heal:amoxycillin\"}"));
                l.add(e("heralisz_tactical:consumable", 1, 90, UNCOMMON).nbt("{ConsumableId:\"heralisz_heal:ai2\"}"));
                l.add(e("heralisz_tactical:consumable", 1, 75, UNCOMMON).nbt("{ConsumableId:\"heralisz_heal:carfak\"}"));
                l.add(e("heralisz_tactical:consumable", 1, 70, UNCOMMON).nbt("{ConsumableId:\"heralisz_heal:blood_pack\"}"));
                l.add(e("heralisz_tactical:consumable", 5, 30, UNCOMMON).nbt("{ConsumableId:\"heralisz_heal:ibuprofen\"}"));
                l.add(e("heralisz_tactical:consumable", 1, 50, RARE).nbt("{ConsumableId:\"heralisz_heal:surv12\"}"));
                l.add(e("heralisz_tactical:consumable", 1, 40, RARE).nbt("{ConsumableId:\"heralisz_heal:cms\"}"));
                l.add(e("heralisz_tactical:consumable", 3, 20, RARE).nbt("{ConsumableId:\"heralisz_heal:blood_pack\"}"));
                l.add(e("heralisz_tactical:consumable", 3, 12, EPIC).nbt("{ConsumableId:\"heralisz_heal:surv12\"}"));
                l.add(e("heralisz_tactical:consumable", 3, 10, EPIC).nbt("{ConsumableId:\"heralisz_heal:cms\"}"));
                l.add(e("minecraft:totem_of_undying", 1, 3, LEGENDARY));
                l.get(0).weight += TOTAL - total(l);
            }
            // caisse Largage : ravitaillement militaire. Munitions TaCZ et grenades (courant), armes de poing, puis
            // PM, fusils a pompe, fusils d'assaut, precision et armes lourdes (minigun et RPG-7 : 0,1 %)
            case LARGAGE -> {
                l.add(ammo("762x39", 30, 80, COMMON));
                l.add(ammo("556x45", 30, 80, COMMON));
                l.add(ammo("9mm", 40, 80, COMMON));
                l.add(ammo("12g", 16, 60, COMMON));
                l.add(ammo("45acp", 30, 50, COMMON));
                l.add(e("heralisz_tactical:throwable", 2, 50, COMMON).nbt("{ThrowableId:\"heralisz_grenade:smoke_grenade\"}"));
                l.add(ammo("308", 20, 40, UNCOMMON));
                l.add(e("heralisz_tactical:throwable", 2, 50, UNCOMMON).nbt("{ThrowableId:\"heralisz_grenade:m67\"}"));
                l.add(e("heralisz_tactical:throwable", 2, 45, UNCOMMON).nbt("{ThrowableId:\"heralisz_grenade:flash_grenade\"}"));
                l.add(e("heralisz_tactical:throwable", 1, 40, UNCOMMON).nbt("{ThrowableId:\"heralisz_grenade:molotov\"}"));
                l.add(e("heralisz_tactical:throwable", 2, 35, UNCOMMON).nbt("{ThrowableId:\"heralisz_grenade:rgn\"}"));
                l.add(gun("glock_17", "SEMI", 35, UNCOMMON));
                l.add(gun("m1911", "SEMI", 30, UNCOMMON));
                l.add(ammo("338", 10, 15, RARE));
                l.add(e("heralisz_tactical:throwable", 1, 15, RARE).nbt("{ThrowableId:\"heralisz_grenade:c4\"}"));
                l.add(gun("deagle", "SEMI", 20, RARE));
                l.add(gun("ump45", "AUTO", 22, RARE));
                l.add(gun("hk_mp5a5", "AUTO", 22, RARE));
                l.add(gun("vector45", "AUTO", 15, RARE));
                l.add(gun("p90", "AUTO", 15, RARE));
                l.add(gun("m870", "SEMI", 25, RARE));
                l.add(gun("ak47", "AUTO", 18, RARE));
                l.add(gun("m4a1", "AUTO", 18, RARE));
                l.add(ammo("50bmg", 5, 8, EPIC));
                l.add(gun("aa12", "AUTO", 8, EPIC));
                l.add(gun("scar_l", "AUTO", 10, EPIC));
                l.add(gun("hk416d", "AUTO", 10, EPIC));
                l.add(gun("g36k", "AUTO", 10, EPIC));
                l.add(gun("mk14", "SEMI", 8, EPIC));
                l.add(gun("rpk", "AUTO", 6, EPIC));
                l.add(gun("m700", "SEMI", 10, EPIC));
                l.add(gun("ai_awp", "SEMI", 4, LEGENDARY));
                l.add(gun("m107", "SEMI", 3, LEGENDARY));
                l.add(gun("barrett_m95", "SEMI", 2, LEGENDARY));
                l.add(gun("m249", "AUTO", 3, LEGENDARY));
                l.add(e("dyairdrop:flaregun0", 1, 4, LEGENDARY));
                l.add(gun("rpg7", "SEMI", 1, MYTHIC));
                l.add(gun("minigun", "AUTO", 1, MYTHIC));
                l.get(0).weight += TOTAL - total(l);
            }
            // caisse Gemme : uniquement les 4 finitions gemmes des trois couteaux (Black Pearl la plus courante, Emerald la plus rare)
            case GEMME -> {
                for (String k : new String[]{"butterfly", "karambit", "m9"}) {
                    l.add(knife(k + "__doppler_black_pearl", 120, LEGENDARY));
                    l.add(knife(k + "__doppler_ruby", 107, LEGENDARY));
                    l.add(knife(k + "__doppler_sapphire", 67, MYTHIC));
                    l.add(knife(k + "__gamma_emerald", 39, MYTHIC));
                }
                l.get(0).weight += TOTAL - total(l);
            }
        }
        return l;
    }

    /** Les 32 stickers de heralisz_camo (tools/sticker_art.py). */
    private static final String[] STICKERS = {"ace", "headshot", "clutch", "one_tap", "no_scope", "glhf", "reaper", "wolf",
            "viper", "boom", "frag", "skull_king", "biohazard", "radioactive", "survivor", "outbreak",
            "dead_zone", "lock_n_load", "elite", "phoenix", "oni", "kitsune", "katana", "volt",
            "diamond", "toxic", "rage_quit", "last_hp", "rng", "blood_money", "wallbang", "ggwp"};

    /** Somme des poids d'une caisse a couteaux (1 = 0,1 %). */
    private static final int TOTAL = 1000;

    /** Couteau TaCZ skinne du pack heralisz_skins (meme NBT que le couteau pris dans l'onglet creatif). */
    private static LootEntry knife(String skin, int weight, LootRarity rarity) {
        return e("tacz:modern_kinetic_gun", 1, weight, rarity)
                .nbt("{GunId:\"heralisz_skins:" + skin + "\",GunFireMode:\"SEMI\",GunCurrentAmmoCount:1,HasBulletInBarrel:1b}");
    }

    private static int total(List<LootEntry> l) {
        int w = 0;
        for (LootEntry x : l) w += x.weight;
        return w;
    }

    /** Arme TaCZ du pack par defaut (chargeur vide, le joueur recharge). */
    private static LootEntry gun(String id, String mode, int weight, LootRarity rarity) {
        return e("tacz:modern_kinetic_gun", 1, weight, rarity).nbt("{GunId:\"tacz:" + id + "\",GunFireMode:\"" + mode + "\",GunCurrentAmmoCount:0}");
    }

    /** Munitions TaCZ du pack par defaut. */
    private static LootEntry ammo(String id, int count, int weight, LootRarity rarity) {
        return e("tacz:ammo", count, weight, rarity).nbt("{AmmoId:\"tacz:" + id + "\"}");
    }

    private static LootEntry charm(String id, int weight, LootRarity rarity) {
        return e("heralisz_camo:charm_" + id, 1, weight, rarity);
    }

    private static LootEntry camo(String id, int weight, LootRarity rarity) {
        return e("heralisz_camo:camo_" + id, 1, weight, rarity);
    }

    private static LootEntry e(String item, int count, int weight, LootRarity rarity) {
        return new LootEntry(item, count, weight, rarity);
    }
}
