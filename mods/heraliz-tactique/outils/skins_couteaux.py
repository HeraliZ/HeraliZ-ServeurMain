#!/usr/bin/env python3
"""
Skins de couteaux par paliers (Classique, Raffiné, Prestige, Ultime) : catalogue et fichiers qui en découlent, écrits par
ce script, jamais à la main.

    python mods/heraliz-tactique/outils/skins_couteaux.py

Chaque skin est un programme de shader camo_<id>.fsh (ids 120 à 199 ; les finitions exclusives 100 à 111 déjà écrites
dans le gabarit sont reprises telles quelles) qui appelle un style du gabarit (kSatin, kDamascus, kFade… : section
« skins de couteaux par paliers » de outils/camo_gabarit.fsh) avec sa palette.

Écrit :
  - le bloc <skins_couteaux> du gabarit (programme commun camo_120 : couleurs de chaque skin, style, manche, insert) ;
  - camo_120.json et ceux des finitions exclusives reprises, puis relance generer_camos.py ;
  - les finitions dans KnifeFinish (bloc <skins_couteaux>) ;
  - les objets : couteaux TaCZ (M9, Karambit, Butterfly, CT, T) dans le gunpack serveur/tacz/heralisz_skins (fiche,
    affichage, noms) ; armes de mêlée de LR dans heralisz_melee (fiche copiée de l'arme de base, affichage sur le modèle
    propre écrit par skins_melee.py, noms) ; noms en français, anglais et russe.
Les icônes (textures/.../slot/<couteau>__<finition>.png) sont rendues par l'outil d'aperçu (outils/apercu_camos, vue
« icone »), sur les modèles tels que le jeu les dessine.
"""
import json
from collections import OrderedDict
import re
import subprocess
import sys
from pathlib import Path

MOD = Path(__file__).resolve().parent.parent
OUTILS = MOD / "outils"
GABARIT = OUTILS / "camo_gabarit.fsh"
CORE = MOD / "src" / "client" / "resources" / "assets" / "heralisz_camo" / "shaders" / "core"

# paliers de beauté, du plus courant au plus rare : (id, nom affiché, code couleur du nom)
PALIERS = {
    "classique": ("Classique", "§7"),
    "raffine": ("Raffiné", "§3"),
    "prestige": ("Prestige", "§5"),
    "ultime": ("Ultime", "§e"),
}

RUBBER = ("rubber",)


def wrap(dark, light, accent, glow=0.0):
    return ("wrap", dark, light, accent, glow)


# (shader, couteau, finition, nom, palier, style, arguments, manche, insert du Butterfly)
# style None : finition exclusive déjà écrite dans le gabarit (uniqueFinish / uniqueHandle, ids 100 à 111)
SKINS = [
    # --- M9 Bayonet (manche caoutchouc)
    (120, "m9", "night_ops", "Night Ops", "classique", "kNightOps", [0x383a41, 0xe02a36], RUBBER, None),
    (121, "m9", "forest_ddpat", "Forest DDPAT", "classique", "kCamo", [0x6a7450, 0x3e4830, 0x9a9a72, 0x22281a, 7.0, 1.0], RUBBER, None),
    (122, "m9", "satin", "Satin", "classique", "kSatin", [0xb9bec6, 1.0], RUBBER, None),
    (123, "m9", "damascus_steel", "Damascus Steel", "raffine", "kDamascus", [0xd4d8de, 0x3a3e46, 90.0], RUBBER, None),
    (124, "m9", "blue_steel", "Blue Steel", "raffine", "kTempered", [0xc8a865, 0x9a5a3a, 0x5a3aa0, 0x2a5ad8], RUBBER, None),
    (125, "m9", "ocean_fade", "Ocean Fade", "prestige", "kFade", [0x2c2ab8, 0x1aa0d8, 0x4ef0d0], RUBBER, None),
    # --- Karambit (manche caoutchouc)
    (126, "karambit", "urban_masked", "Urban Masked", "classique", "kCamo", [0x8a8e94, 0x4a4e55, 0xc8ccd2, 0x24272b, 6.0, 0.0], RUBBER, None),
    (127, "karambit", "stonewash", "Stonewash", "classique", "kStonewash", [0xb8bcc2, 0x5a5e64], RUBBER, None),
    (128, "karambit", "coyote", "Coyote", "classique", "kCerakote", [0x9a7a52], RUBBER, None),
    (129, "karambit", "night_stripe", "Night Stripe", "raffine", "kStripes", [0x1a1b1f, 0x4a4e58, 9.0], RUBBER, None),
    (130, "karambit", "copper_patina", "Copper Patina", "raffine", "kPatina", [0xc8743a, 0x3aa088], RUBBER, None),
    (131, "karambit", "amber_fade", "Amber Fade", "prestige", "kFade", [0x8a1a10, 0xe85a18, 0xffd040], RUBBER, None),
    # --- Butterfly (manches en métal noir, insert teinté)
    (132, "butterfly", "safari_mesh", "Safari Mesh", "classique", "kMesh", [0xb8a47a, 0x8a8a5a, 0x3a3a2a], None, 0x6a5a3a),
    (133, "butterfly", "scorched", "Scorched", "classique", "kScorched", [0xc0c4ca, 0x2a2420], None, 0x2a2420),
    (134, "butterfly", "gunmetal", "Gunmetal", "classique", "kSatin", [0x6a707a, 1.2], None, 0x2a2e34),
    (135, "butterfly", "contour", "Contour", "raffine", "kTopo", [0x1e3a2a, 0x6a8a4a, 0xd8e0a8], None, 0x1e3a2a),
    (136, "butterfly", "blue_carbon", "Blue Carbon", "raffine", "kCarbon", [(0.75, 0.9, 1.25)], None, 0x1a2a4a),
    (137, "butterfly", "kintsugi", "Kintsugi", "prestige", "kKintsugi", [0x26272b, 0xf0bc50], None, 0x6a5020),
    # --- Dague tactique (TAC Dagger)
    (138, "tac_dagger", "olive_drab", "Olive Drab", "classique", "kCerakote", [0x5a6038], wrap(0x1c1e12, 0x4a5030, 0x8a9058), None),
    (139, "tac_dagger", "tiger_stripe", "Tiger Stripe", "classique", "kTigerStripe", [0x8a8a5a, 0x4a5a30, 0x16180e], wrap(0x14160e, 0x3a4228, 0x8a8a5a), None),
    (140, "tac_dagger", "satin_bronze", "Satin Bronze", "classique", "kSatin", [0xb07a40, 1.0], wrap(0x1a120a, 0x5a3e22, 0xc89050), None),
    (141, "tac_dagger", "hex_armor", "Hex Armor", "raffine", "kHex", [0x2a2e34, 0x4a5260, 0x30d0c0], wrap(0x101214, 0x2a2e34, 0x30d0c0, 0.5), None),
    (142, "tac_dagger", "sunset", "Sunset", "raffine", "kAnodFade", [0xe0306a, 0xffa030], wrap(0x1a0a10, 0x5a1a30, 0xffa030), None),
    (143, "tac_dagger", "emerald_web", "Emerald Web", "prestige", "kWeb", [0x0a4a24, 0x16903c, 0x020604], wrap(0x020604, 0x0a3a1c, 0x16c04c), None),
    (104, "tac_dagger", "lava_core", "Lava Core", "ultime", None, [], None, None),
    # --- Credo (Creed)
    (144, "creed", "arctic_digital", "Arctic Digital", "classique", "kCamo", [0xdfe6ec, 0x9fb4c8, 0x5a7090, 0x2a3448, 8.0, 1.0], wrap(0x1a2230, 0x5a7090, 0xdfe6ec), None),
    (145, "creed", "champagne", "Champagne", "classique", "kSatin", [0xd8c49a, 0.9], wrap(0x1e1810, 0x6a5a3a, 0xd8c49a), None),
    (146, "creed", "blackout", "Blackout", "classique", "kNightOps", [0x34353a, 0xe8b850], wrap(0x0a0a0c, 0x1e1e22, 0xd8a840), None),
    (147, "creed", "freehand", "Freehand", "raffine", "kFreehand", [0xe8e4dc, 0x1a1a1a, 0xe02a2a, 0x2a6ae0], wrap(0x141414, 0x3a3a3a, 0xe02a2a), None),
    (148, "creed", "twin_tone", "Twin Tone", "raffine", "kTwoTone", [0xe8e8e4, 0x16171a, 0xe02030], wrap(0x0e0e10, 0x2a2a2e, 0xe02030), None),
    (149, "creed", "prism", "Prism", "prestige", "kPrism", [0xc0c4cc], wrap(0x101014, 0x3a3a48, 0x9ad8ff, 0.4), None),
    (105, "creed", "frostbite", "Frostbite", "ultime", None, [], None, None),
    # --- Lame de Desmoulins (manche orné : motifs dans la finition de la lame)
    (150, "desmoulins_edge", "desert_storm", "Desert Storm", "classique", "kCamo", [0xc8b088, 0xa08058, 0x7a6040, 0xe0d0a8, 6.0, 0.0], None, None),
    (151, "desmoulins_edge", "ivory", "Ivory", "classique", "kCerakote", [0xe6dcc6], None, None),
    (152, "desmoulins_edge", "ash", "Ash", "classique", "kScorched", [0xa8aab0, 0x3a3a3e], None, None),
    (153, "desmoulins_edge", "rose_gold", "Rose Gold", "raffine", "kAnodFade", [0xd88a8a, 0xf2c8a0], None, None),
    (154, "desmoulins_edge", "crimson_carbon", "Crimson Carbon", "raffine", "kCarbon", [(1.35, 0.55, 0.55)], None, None),
    (155, "desmoulins_edge", "silver_lore", "Silver Lore", "prestige", "kEngraved", [0xd0d6de, 0x1a3a8a], None, None),
    (178, "desmoulins_edge", "garnet", "Garnet", "ultime", "gemDoppler", [0x14000a, 0x5a0628, 0xa0103a, 0xff90b0, 0.12, (19.3, 4.1, 33.7)], None, None),
    # --- Croc du dragon (Dragon's Fang)
    (156, "dragons_fang", "jungle", "Jungle", "classique", "kCamo", [0x3a5a2a, 0x1e3a18, 0x6a7a3a, 0x0e1a0c, 5.0, 0.0], wrap(0x0e1a0c, 0x2a4a20, 0x6a7a3a), None),
    (157, "dragons_fang", "cobalt", "Cobalt", "classique", "kSatin", [0x5a78b8, 1.0], wrap(0x0e1628, 0x2a3e6a, 0x8aa8e8), None),
    (158, "dragons_fang", "charcoal", "Charcoal", "classique", "kCerakote", [0x2e3034], wrap(0x0e0e10, 0x2a2c30, 0x6a6e74), None),
    (159, "dragons_fang", "forged_damascus", "Forged Damascus", "raffine", "kDamascus", [0xe0d0b8, 0x2e221a, 120.0], wrap(0x140e0a, 0x4a3628, 0xd8c8b0), None),
    (160, "dragons_fang", "ember_temper", "Ember Temper", "raffine", "kTempered", [0xe0c070, 0xd07030, 0xa02818, 0x3a0e2a], wrap(0x1a0808, 0x5a1a10, 0xe0a050), None),
    (107, "dragons_fang", "dragon_gold", "Dragon Gold", "prestige", None, [], None, None),
    (109, "dragons_fang", "phoenix", "Phoenix", "ultime", None, [], None, None),
    # --- Exécuteur (Executor)
    (161, "executor", "rust_coat", "Rust Coat", "classique", "kRustCoat", [0x5a5e64, 0xb05a20], wrap(0x140e0a, 0x4a2a14, 0xb05a20), None),
    (162, "executor", "arctic_white", "Arctic White", "classique", "kCerakote", [0xe4e8ec], wrap(0x1a1c20, 0x8a8e94, 0xe4e8ec), None),
    (163, "executor", "urban_digital", "Urban Digital", "classique", "kCamo", [0x6a6e74, 0x3a3d42, 0xa8acb2, 0x1c1e22, 7.0, 1.0], wrap(0x141518, 0x3a3d42, 0xa8acb2), None),
    (164, "executor", "blood_stripe", "Blood Stripe", "raffine", "kStripes", [0x121214, 0x9a1018, 7.0], wrap(0x0c0c0e, 0x2a0a0e, 0xc01822), None),
    (165, "executor", "hazard", "Hazard", "raffine", "kTwoTone", [0xf0c020, 0x141416, 0xffffff], wrap(0x0e0e10, 0x2a2a2c, 0xf0c020), None),
    (166, "executor", "toxic_fade", "Toxic Fade", "prestige", "kFade", [0x1a6a20, 0x7ad020, 0xf0f040], wrap(0x0a1a0a, 0x1a4a1a, 0x9af030, 0.5), None),
    (179, "executor", "amethyst", "Amethyst", "ultime", "gemDoppler", [0x12021e, 0x5a1490, 0xa040e8, 0xe8b0ff, 0.12, (27.9, 15.2, 8.6)], wrap(0x0a0410, 0x2a0e44, 0xb060ff, 0.5), None),
    # --- Miséricorde (Mercy)
    (167, "mercy", "pearl", "Pearl", "classique", "kSatin", [0xe4e6ea, 0.7], wrap(0x2a2c30, 0x9a9ea6, 0xf4f6f8), None),
    (168, "mercy", "sage", "Sage", "classique", "kCerakote", [0x8a9a7a], wrap(0x1a1e16, 0x4a5640, 0xb8c8a8), None),
    (169, "mercy", "snow_camo", "Snow Camo", "classique", "kCamo", [0xf0f2f4, 0xb8bec6, 0x7a828c, 0xd8dce2, 6.0, 0.0], wrap(0x3a4048, 0xb8bec6, 0xf0f2f4), None),
    (170, "mercy", "azure_temper", "Azure Temper", "raffine", "kTempered", [0xd8d0b0, 0x9ac0d8, 0x3a7ad8, 0x1a2a8a], wrap(0x0e1428, 0x2a4a8a, 0x9ac0d8), None),
    (171, "mercy", "celeste", "Celeste", "raffine", "kTwoTone", [0xeef2f4, 0x2aa8a8, 0xd9a640], wrap(0x0e2a2a, 0x2a8a8a, 0xd9a640), None),
    (172, "mercy", "jade_marble", "Jade Marble", "prestige", "kMarble", [0x0e7a4a, 0xe8c860, 0x9ae0c0, 0x041a10], wrap(0x041a10, 0x0e5a36, 0xe8c860), None),
    (180, "mercy", "opal", "Opal", "ultime", "kOpal", [], wrap(0x3a4048, 0xd8e0e8, 0xf0c070, 0.3), None),
    # --- Lueur des mers (Sea Gleam)
    (173, "sea_gleam", "navy", "Navy", "classique", "kCerakote", [0x1e2e4a], wrap(0x0a1020, 0x1e2e4a, 0x6a8ab8), None),
    (174, "sea_gleam", "marine_camo", "Marine Camo", "classique", "kCamo", [0x2a4a6a, 0x1a2e48, 0x4a7a9a, 0x0e1828, 6.0, 0.0], wrap(0x0e1828, 0x2a4a6a, 0x8ab8d8), None),
    (175, "sea_gleam", "tidewash", "Tidewash", "classique", "kStonewash", [0x9ab4c8, 0x2e4a5e], wrap(0x0e1a24, 0x2e4a5e, 0x9ab4c8), None),
    (102, "sea_gleam", "bright_water", "Bright Water", "raffine", None, [], None, None),
    (176, "sea_gleam", "tide_stripe", "Tide Stripe", "raffine", "kStripes", [0x0e2a3a, 0x1a8a9a, 8.0], wrap(0x081a24, 0x0e2a3a, 0x1a8a9a), None),
    (177, "sea_gleam", "gold_hardened", "Gold Hardened", "prestige", "kCaseHard", [0xc89a30, 0xf0d070, 0x8a4a20, 0x3a6ab8], wrap(0x1a1006, 0x5a3a12, 0xf0d070), None),
    (111, "sea_gleam", "abyssal_pearl", "Abyssal Pearl", "ultime", None, [], None, None),
]


# deuxième série (mêmes paliers) et armes ajoutées : batte, marteau, couteaux CT et T (deux séries chacune).
# AUTO : manche enroulé aux couleurs du skin (voir manche_auto)
AUTO = ("auto",)
_LOT2 = [
    # --- M9 Bayonet
    ("m9", "battle_worn", "Battle Worn", "classique", "kBattleWorn", [0x4a5a3a, 0xb8bcc2], RUBBER),
    ("m9", "hammered", "Hammered", "classique", "kHammered", [0xa8adb5], RUBBER),
    ("m9", "desert_spray", "Desert Spray", "classique", "kSpray", [0xc8a878, 0x8a6a48], RUBBER),
    ("m9", "leopard", "Leopard", "raffine", "kLeopard", [0xd8b070, 0xa86a2a, 0x1a1208], RUBBER),
    ("m9", "shard", "Shard", "raffine", "kFacets", [0x1a3a6a, 0x2a6ab8, 0x8ab8e8], RUBBER),
    ("m9", "sakura", "Sakura", "prestige", "kSakura", [0x120c10, 0xf0a0c0, 0x3a2418], RUBBER),
    # --- Karambit
    ("karambit", "arid_digital", "Arid Digital", "classique", "kCamo", [0xc8b490, 0x9a7a52, 0x6a5a3a, 0xe0d4b4, 6.0, 1.0], RUBBER),
    ("karambit", "black_satin", "Black Satin", "classique", "kSatin", [0x3a3d44, 1.0], RUBBER),
    ("karambit", "ember_worn", "Ember Worn", "classique", "kBattleWorn", [0xa83a1a, 0xb0b4ba], RUBBER),
    ("karambit", "splatter", "Splatter", "raffine", "kSplatter", [0x16171a, 0xe02a6a, 0x30c0e0], RUBBER),
    ("karambit", "gold_hex", "Gold Hex", "raffine", "kHex", [0x1a1a1c, 0x2a2a2e, 0xe0b040], RUBBER),
    ("karambit", "titanium", "Titanium", "prestige", "kTitanium", [], RUBBER),
    # --- Butterfly
    ("butterfly", "forest_camo", "Forest Camo", "classique", "kCamo", [0x4a5a3a, 0x2a3a22, 0x6a7a4a, 0x1a2214, 5.0, 0.0], None, 0x2a3a22),
    ("butterfly", "hammered_bronze", "Hammered Bronze", "classique", "kHammered", [0xb08048], None, 0x5a3a1e),
    ("butterfly", "polar_spray", "Polar Spray", "classique", "kSpray", [0xe8ecf0, 0x8aa0b8], None, 0x5a6a80),
    ("butterfly", "midnight_damascus", "Midnight Damascus", "raffine", "kDamascus", [0x6a8ad8, 0x0e1630, 100.0], None, 0x1a2a5a),
    ("butterfly", "neon_stripe", "Neon Stripe", "raffine", "kStripes", [0x101014, 0x30e0a0, 8.0], None, 0x0e4a36),
    ("butterfly", "circuit", "Circuit", "prestige", "kCircuit", [0x0a1a12, 0x2a8a5a, 0x40ffa0], None, 0x0a2a1a),
    # --- Dague tactique
    ("tac_dagger", "urban_worn", "Urban Worn", "classique", "kBattleWorn", [0x3a3e44, 0xb8bcc2], AUTO),
    ("tac_dagger", "sandstone", "Sandstone", "classique", "kStonewash", [0xd8c0a0, 0x8a6a4a], AUTO),
    ("tac_dagger", "mirror", "Mirror", "classique", "kSatin", [0xd8dce2, 0.3], AUTO),
    ("tac_dagger", "ember_shard", "Ember Shard", "raffine", "kFacets", [0x6a1a0a, 0xc04018, 0xf0a040], AUTO),
    ("tac_dagger", "snow_leopard", "Snow Leopard", "raffine", "kLeopard", [0xe8e8ea, 0xb8bcc4, 0x2a2c30], AUTO),
    ("tac_dagger", "silver_kintsugi", "Silver Kintsugi", "prestige", "kKintsugi", [0xe8e4dc, 0xc8ccd4], AUTO),
    ("tac_dagger", "aurora", "Aurora", "ultime", "kAurora", [0x05081a, 0x30f0a0, 0xa040f0], AUTO),
    # --- Credo
    ("creed", "hammered_steel", "Hammered Steel", "classique", "kHammered", [0x8a9098], AUTO),
    ("creed", "midnight_camo", "Midnight Camo", "classique", "kCamo", [0x2a3048, 0x1a1e30, 0x3a4460, 0x0e1018, 6.0, 0.0], AUTO),
    ("creed", "brass", "Brass", "classique", "kSatin", [0xc8a050, 0.9], AUTO),
    ("creed", "ink_splatter", "Ink Splatter", "raffine", "kSplatter", [0xe8e4dc, 0x101012, 0xc01820], AUTO),
    ("creed", "copper_hex", "Copper Hex", "raffine", "kHex", [0x2a1a12, 0x5a3a24, 0xe08040], AUTO),
    ("creed", "imperial_marble", "Imperial Marble", "prestige", "kMarble", [0x8a0a1a, 0xf0d080, 0x2a0a0a, 0x0a0204], AUTO),
    ("creed", "tanzanite", "Tanzanite", "ultime", "gemDoppler", [0x0a0628, 0x3a2aa0, 0x7a6af0, 0xd0c8ff, 0.12, (2.7, 17.9, 40.3)], AUTO),
    # --- Lame de Desmoulins (manche orné)
    ("desmoulins_edge", "night_digital", "Night Digital", "classique", "kCamo", [0x5a5a64, 0x3a3a44, 0x7a7a86, 0x24242c, 5.0, 1.0], None),
    ("desmoulins_edge", "bone_worn", "Bone Worn", "classique", "kBattleWorn", [0xd8ccb0, 0x8a8e94], None),
    ("desmoulins_edge", "gilded", "Gilded", "classique", "kSatin", [0xd8b060, 0.8], None),
    ("desmoulins_edge", "royal_damascus", "Royal Damascus", "raffine", "kDamascus", [0xc8a8e8, 0x2a1440, 110.0], None),
    ("desmoulins_edge", "crimson_shard", "Crimson Shard", "raffine", "kFacets", [0x3a0610, 0x8a1020, 0xd02040], None),
    ("desmoulins_edge", "obsidian_sakura", "Obsidian Sakura", "prestige", "kSakura", [0x08080a, 0xe02040, 0x2a2a2e], None),
    ("desmoulins_edge", "molten_gold", "Molten Gold", "ultime", "kMolten", [0xe0b040, 0xff8020], None),
    # --- Croc du dragon
    ("dragons_fang", "lava_spray", "Lava Spray", "classique", "kSpray", [0x2a0e08, 0xa0300e], AUTO),
    ("dragons_fang", "volcanic", "Volcanic", "classique", "kStonewash", [0x5a4a44, 0x1a1210], AUTO),
    ("dragons_fang", "olive_satin", "Olive Satin", "classique", "kSatin", [0x7a7a4a, 1.0], AUTO),
    ("dragons_fang", "bengal", "Bengal", "raffine", "kTigerStripe", [0xe08a2a, 0xb05a14, 0x140a04], AUTO),
    ("dragons_fang", "jade_hex", "Jade Hex", "raffine", "kHex", [0x0a2a1a, 0x145a34, 0x40e090], AUTO),
    ("dragons_fang", "infernal_fade", "Infernal Fade", "prestige", "kFade", [0x1a0404, 0xa01810, 0xffb040], AUTO),
    ("dragons_fang", "citrine", "Citrine", "ultime", "gemDoppler", [0x2a1400, 0xa05a00, 0xf0b020, 0xfff0a0, 0.12, (5.5, 21.2, 9.9)], AUTO),
    # --- Exécuteur
    ("executor", "charcoal_worn", "Charcoal Worn", "classique", "kBattleWorn", [0x26282c, 0x9aa0a8], AUTO),
    ("executor", "glacier_digital", "Glacier Digital", "classique", "kCamo", [0xe8eef4, 0xb0c0d0, 0x6a8098, 0xd0dae4, 5.0, 1.0], AUTO),
    ("executor", "dark_hammered", "Dark Hammered", "classique", "kHammered", [0x4a4e56], AUTO),
    ("executor", "toxic_splatter", "Toxic Splatter", "raffine", "kSplatter", [0x141614, 0x9af030, 0xf0e040], AUTO),
    ("executor", "violet_shard", "Violet Shard", "raffine", "kFacets", [0x2a1048, 0x5a2a90, 0xa060e0], AUTO),
    ("executor", "neon_circuit", "Neon Circuit", "prestige", "kCircuit", [0x0c0a14, 0x6a2aa0, 0xff40e0], AUTO),
    ("executor", "onyx", "Onyx", "ultime", "gemDoppler", [0x020203, 0x1a1a20, 0x50545e, 0xc0c8d8, 0.05, (9.1, 33.3, 2.2)], AUTO),
    # --- Miséricorde
    ("mercy", "ivory_hammered", "Ivory Hammered", "classique", "kHammered", [0xe0dccc], AUTO),
    ("mercy", "seafoam_spray", "Seafoam Spray", "classique", "kSpray", [0xd8f0e8, 0x7ac0b0], AUTO),
    ("mercy", "steel_worn", "Steel Worn", "classique", "kBattleWorn", [0x5a6a7a, 0xd0d4da], AUTO),
    ("mercy", "celestial_shard", "Celestial Shard", "raffine", "kFacets", [0xd8e4f0, 0x8ab0d8, 0xf0d8a0], AUTO),
    ("mercy", "pearl_damascus", "Pearl Damascus", "raffine", "kDamascus", [0xf0f2f4, 0x8a96a8, 100.0], AUTO),
    ("mercy", "cherry_blossom", "Cherry Blossom", "prestige", "kSakura", [0xf2ece4, 0xe86a9a, 0x5a3a2a], AUTO),
    ("mercy", "rose_quartz", "Rose Quartz", "ultime", "gemDoppler", [0x2a0614, 0xa03a6a, 0xf08ab8, 0xffd8ea, 0.1, (31.7, 8.8, 15.5)], AUTO),
    # --- Lueur des mers
    ("sea_gleam", "coral_worn", "Coral Worn", "classique", "kBattleWorn", [0xd86a50, 0xb8c0c8], AUTO),
    ("sea_gleam", "driftwood", "Driftwood", "classique", "kWood", [0xb8a080, 0x6a5440], AUTO),
    ("sea_gleam", "deep_hammered", "Deep Hammered", "classique", "kHammered", [0x3a5a7a], AUTO),
    ("sea_gleam", "reef_splatter", "Reef Splatter", "raffine", "kSplatter", [0x0e2a3a, 0xf08a50, 0x40e0d0], AUTO),
    ("sea_gleam", "kraken_hex", "Kraken Hex", "raffine", "kHex", [0x0a1a24, 0x123a4a, 0x40c0e0], AUTO),
    ("sea_gleam", "tidal_marble", "Tidal Marble", "prestige", "kMarble", [0x0a4a8a, 0xf0f4f8, 0x30b0c0, 0x02101c], AUTO),
    ("sea_gleam", "aquamarine", "Aquamarine", "ultime", "gemDoppler", [0x002a2e, 0x008a90, 0x30e0d8, 0xc0fff8, 0.12, (14.4, 6.6, 28.1)], AUTO),
    # --- Batte de baseball (deux séries)
    ("baseball_bat", "ash_wood", "Ash Wood", "classique", "kWood", [0xe0c8a0, 0xa07a50], AUTO),
    ("baseball_bat", "walnut", "Walnut", "classique", "kWood", [0x8a5a34, 0x3a2214], AUTO),
    ("baseball_bat", "blackwood", "Blackwood", "classique", "kWood", [0x4a4044, 0x161214], AUTO),
    ("baseball_bat", "aluminium", "Aluminium", "classique", "kSatin", [0xc8ccd2, 1.2], AUTO),
    ("baseball_bat", "graphite", "Graphite", "classique", "kCerakote", [0x34363a], AUTO),
    ("baseball_bat", "street_camo", "Street Camo", "classique", "kCamo", [0x7a7e84, 0x4a4e55, 0xb8bcc2, 0x2a2c30, 6.0, 1.0], AUTO),
    ("baseball_bat", "slugger_stripes", "Slugger Stripes", "raffine", "kStripes", [0xe8e8e4, 0xc01820, 6.0], AUTO),
    ("baseball_bat", "graffiti", "Graffiti", "raffine", "kSplatter", [0x1a1a1c, 0xf0e020, 0x20c0f0], AUTO),
    ("baseball_bat", "bruiser_hex", "Bruiser Hex", "raffine", "kHex", [0x1a1a1c, 0x30343a, 0xf06020], AUTO),
    ("baseball_bat", "tempered_alloy", "Tempered Alloy", "raffine", "kTempered", [0xd8c890, 0xc07a40, 0x7a3ab0, 0x2a60e0], AUTO),
    ("baseball_bat", "midnight_fade", "Midnight Fade", "prestige", "kFade", [0x10103a, 0x6a2ab8, 0xe060c0], AUTO),
    ("baseball_bat", "red_crackle", "Red Crackle", "prestige", "kKintsugi", [0x8a1018, 0xf0c050], AUTO),
    ("baseball_bat", "molten_steel", "Molten Steel", "ultime", "kMolten", [0xc8ccd4, 0x80c0ff], AUTO),
    ("baseball_bat", "plasma", "Plasma", "ultime", "kElectric", [0x120818, 0xff40c0], AUTO),
    # --- Marteau du châtiment (deux séries)
    ("smite_hammer", "forged_iron", "Forged Iron", "classique", "kHammered", [0x5a5e66], AUTO),
    ("smite_hammer", "blacksmith", "Blacksmith", "classique", "kBattleWorn", [0x2a2a2e, 0xa8a49c], AUTO),
    ("smite_hammer", "granite", "Granite", "classique", "kStonewash", [0xa8a8aa, 0x4a4a4e], AUTO),
    ("smite_hammer", "antique_bronze", "Antique Bronze", "classique", "kSatin", [0x9a6a38, 1.2], AUTO),
    ("smite_hammer", "highland_camo", "Highland Camo", "classique", "kCamo", [0x6a6a4a, 0x4a4a32, 0x8a8a6a, 0x2a2a1a, 5.0, 0.0], AUTO),
    ("smite_hammer", "slate", "Slate", "classique", "kCerakote", [0x4a5462], AUTO),
    ("smite_hammer", "thunder_stripe", "Thunder Stripe", "raffine", "kStripes", [0x14161c, 0x3a7ae0, 7.0], AUTO),
    ("smite_hammer", "verdigris", "Verdigris", "raffine", "kPatina", [0xb07a3a, 0x40b0a0], AUTO),
    ("smite_hammer", "rune_hex", "Rune Hex", "raffine", "kHex", [0x14141a, 0x24242e, 0x80a0ff], AUTO),
    ("smite_hammer", "aurum_temper", "Aurum Temper", "raffine", "kTempered", [0xf0e0a0, 0xd0a040, 0x8a5a20, 0x3a2a10], AUTO),
    ("smite_hammer", "storm_marble", "Storm Marble", "prestige", "kMarble", [0x1a2a6a, 0xc8d0e0, 0x5a7ad8, 0x02040e], AUTO),
    ("smite_hammer", "runic_gold", "Runic Gold", "prestige", "kEngraved", [0xe0b860, 0x3a1a08], AUTO),
    ("smite_hammer", "thunderstrike", "Thunderstrike", "ultime", "kElectric", [0x10121a, 0x80b0ff], AUTO),
    ("smite_hammer", "nebula", "Nebula", "ultime", None, [], None),
    # --- Couteau CT (deux séries)
    ("ct_knife", "ranger_digital", "Ranger Digital", "classique", "kCamo", [0x5a6a4a, 0x3a4a30, 0x8a9a6a, 0x1e2618, 7.0, 1.0], AUTO),
    ("ct_knife", "blue_satin", "Blue Satin", "classique", "kSatin", [0x4a6aa8, 1.0], AUTO),
    ("ct_knife", "steel_hammered", "Steel Hammered", "classique", "kHammered", [0x9aa0aa], AUTO),
    ("ct_knife", "navy_worn", "Navy Worn", "classique", "kBattleWorn", [0x1e2a44, 0xb0b6be], AUTO),
    ("ct_knife", "concrete", "Concrete", "classique", "kStonewash", [0xa0a4a8, 0x5a5e64], AUTO),
    ("ct_knife", "steel_blue", "Steel Blue", "classique", "kCerakote", [0x3a5070], AUTO),
    ("ct_knife", "cobalt_damascus", "Cobalt Damascus", "raffine", "kDamascus", [0x8ab0f0, 0x0e1a3a, 100.0], AUTO),
    ("ct_knife", "ice_shard", "Ice Shard", "raffine", "kFacets", [0x8ab8e8, 0xd8ecff, 0x3a6ab8], AUTO),
    ("ct_knife", "ultraviolet", "Ultraviolet", "raffine", None, [], None),
    ("ct_knife", "blue_stripe", "Blue Stripe", "raffine", "kStripes", [0x0e1428, 0x2a5ad8, 8.0], AUTO),
    ("ct_knife", "blue_circuit", "Blue Circuit", "prestige", "kCircuit", [0x080e1a, 0x2a5a9a, 0x40c0ff], AUTO),
    ("ct_knife", "glacier_marble", "Glacier Marble", "prestige", "kMarble", [0x2a6ad8, 0xf0f6ff, 0x8ac8f0, 0x04102a], AUTO),
    ("ct_knife", "sapphire_aurora", "Sapphire Aurora", "ultime", "kAurora", [0x04061a, 0x40a0ff, 0x80f0ff], AUTO),
    ("ct_knife", "molten_silver", "Molten Silver", "ultime", "kMolten", [0xd8dee8, 0x80c0ff], AUTO),
    # --- Couteau T (deux séries)
    ("t_knife", "desert_digital", "Desert Digital", "classique", "kCamo", [0xc8a878, 0x9a7a4a, 0x7a5a34, 0xe0cca0, 6.0, 1.0], AUTO),
    ("t_knife", "copper_satin", "Copper Satin", "classique", "kSatin", [0xb86a3a, 1.0], AUTO),
    ("t_knife", "red_worn", "Red Worn", "classique", "kBattleWorn", [0x8a1a14, 0xb8bcc2], AUTO),
    ("t_knife", "sand_hammered", "Sand Hammered", "classique", "kHammered", [0xc8b08a], AUTO),
    ("t_knife", "sunbaked", "Sunbaked", "classique", "kStonewash", [0xd8b890, 0x8a6440], AUTO),
    ("t_knife", "brick", "Brick", "classique", "kCerakote", [0x8a3a24], AUTO),
    ("t_knife", "sunburst", "Sunburst", "raffine", "kFacets", [0xf0a030, 0xe05a20, 0xffd060], AUTO),
    ("t_knife", "rust_damascus", "Rust Damascus", "raffine", "kDamascus", [0xe0a070, 0x3a1a0a, 100.0], AUTO),
    ("t_knife", "red_splatter", "Red Splatter", "raffine", "kSplatter", [0xd8d0c0, 0xc01818, 0x101010], AUTO),
    ("t_knife", "orange_stripe", "Orange Stripe", "raffine", "kStripes", [0x18100a, 0xe06a18, 8.0], AUTO),
    ("t_knife", "inferno_circuit", "Inferno Circuit", "prestige", "kCircuit", [0x140806, 0x8a2a10, 0xff6020], AUTO),
    ("t_knife", "desert_sakura", "Desert Sakura", "prestige", "kSakura", [0x2a1a10, 0xf0b080, 0x0e0806], AUTO),
    ("t_knife", "solar_flare", "Solar Flare", "ultime", "kAurora", [0x140404, 0xff6020, 0xffd040], AUTO),
    ("t_knife", "ruby_storm", "Ruby Storm", "ultime", "kElectric", [0x140608, 0xff3040], AUTO),
]
# finitions exclusives déjà écrites dans le gabarit, reprises par la deuxième série (programme à elles)
_EXCLUSIVES = {"nebula": 103, "ultraviolet": 101}


def manche_auto(args):
    """Manche enroulé tiré des couleurs du skin : fond très sombre, bandes de la teinte principale, filet de la plus vive."""
    cols = [a for a in args if isinstance(a, int)]
    def rgb(c):
        return ((c >> 16) & 255, (c >> 8) & 255, c & 255)
    def hexa(r, g, b):
        return (int(r) << 16) | (int(g) << 8) | int(b)
    r, g, b = rgb(cols[0])
    vive = max(cols, key=lambda c: max(rgb(c)) - min(rgb(c)) + 0.3 * sum(rgb(c)) / 3)
    return ("wrap", hexa(r * 0.18, g * 0.18, b * 0.18), hexa(r * 0.55, g * 0.55, b * 0.55), vive, 0.0)


_id = 300
for _x in _LOT2:
    couteau, fin, nom, pal, style, args, manche = _x[:7]
    insert = _x[7] if len(_x) > 7 else None
    if manche == AUTO:
        manche = manche_auto(args)
    sid = _EXCLUSIVES[fin] if style is None else _id
    if style is not None:
        _id += 1
    SKINS.append((sid, couteau, fin, nom, pal, style, args, manche, insert))


def glsl_arg(a):
    if isinstance(a, int):
        return "hexc(0x%06x)" % a
    if isinstance(a, float):
        return repr(a)
    if isinstance(a, tuple):
        return "vec3(%s)" % ", ".join(repr(float(x)) for x in a)
    raise ValueError(a)


def motif(args):
    """Forme d'appel d'un style : c = couleur ou vec3 (rangés dans K_C), f = nombre (rangés dans K_F)."""
    return "".join("f" if isinstance(a, float) else "c" for a in args)


def bloc_shader():
    """Programme commun (camo_120) : skinParams() lit les couleurs du skin dessiné (CamoId), skinCouteau() appelle son
    style. Chaque style n'est appelé qu'une fois dans le programme (petit programme, compilé une seule fois)."""
    palier = [x for x in SKINS if x[5] is not None]
    styles = {}
    for sid, couteau, fin, nom, pal, style, args, manche, insert in palier:
        m = motif(args)
        if styles.setdefault(style, m) != m:
            sys.exit("[skins_couteaux] %s appelé de deux façons (%s, %s)" % (style, styles[style], m))
    nc = max([motif(x[6]).count("c") for x in palier] + [1])
    nf = max([motif(x[6]).count("f") for x in palier] + [1])
    if nf > 4:
        sys.exit("[skins_couteaux] plus de 4 nombres pour un style")
    noms = sorted(styles)
    L = ["#if CAMO_ID == 120",
         "#define SKIN_PALIERS",
         "int K_STYLE = 0;",
         "vec3 K_C[%d];" % nc,
         "vec4 K_F = vec4(0.0);",
         "int K_GRIP = 0;",
         "vec3 K_GD = vec3(0.0), K_GL = vec3(0.0), K_GA = vec3(0.0);",
         "float K_GG = 0.0;",
         "vec3 K_INLAY = vec3(0.1);",
         "void skinParams() {",
         "    switch (CamoId) {"]
    for sid, couteau, fin, nom, pal, style, args, manche, insert in palier:
        L.append("    case %d:   // %s | %s (%s)" % (sid, couteau, nom, PALIERS[pal][0]))
        L.append("        K_STYLE = %d;" % noms.index(style))
        ic = jf = 0
        for a in args:
            if isinstance(a, float):
                L.append("        K_F.%s = %r;" % ("xyzw"[jf], a)); jf += 1
            else:
                L.append("        K_C[%d] = %s;" % (ic, glsl_arg(a))); ic += 1
        if manche is not None and manche[0] == "wrap":
            _, d, l, a, g = manche
            L.append("        K_GRIP = 1; K_GD = hexc(0x%06x); K_GL = hexc(0x%06x); K_GA = hexc(0x%06x); K_GG = %r;" % (d, l, a, float(g)))
        if insert is not None:
            L.append("        K_INLAY = hexc(0x%06x);" % insert)
        L.append("        break;")
    L += ["    }", "}", "vec3 skinCouteau(vec3 p, out vec3 em) {", "    em = vec3(0.0);"]
    for n, style in enumerate(noms):
        ic = jf = 0
        params = ["p"]
        for t in styles[style]:
            if t == "f":
                params.append("K_F.%s" % "xyzw"[jf]); jf += 1
            else:
                params.append("K_C[%d]" % ic); ic += 1
        L.append("    if (K_STYLE == %d) return %s(%s, em);" % (n, style, ", ".join(params)))
    L += ["    return vec3(0.5);", "}",
          "vec3 skinGrip(vec3 p, out vec3 em) {",
          "    if (K_GRIP == 1) return gripWrap(p, K_GD, K_GL, K_GA, K_GG, em);",
          "    em = vec3(0.0);",
          "    return rubberGrip(p);",
          "}",
          "#endif"]
    return "\n".join(L)


def ecrire_gabarit():
    texte = GABARIT.read_text(encoding="utf-8")
    debut = "// <skins_couteaux> : écrit par outils/skins_couteaux.py (catalogue des skins), ne pas modifier à la main\n"
    fin = "// </skins_couteaux>"
    i, j = texte.index(debut) + len(debut), texte.index(fin)
    nouveau = texte[:i] + bloc_shader() + "\n" + texte[j:]
    if nouveau != texte:
        GABARIT.write_bytes(nouveau.encode("utf-8"))


def ecrire_programmes():
    """camo_120.json (programme commun) et ceux des finitions exclusives reprises ; retire les programmes d'un skin par
    paliers qui n'existent plus (anciens programmes séparés)."""
    modele = (CORE / "camo_65.json").read_text(encoding="utf-8")
    voulus = {120} | {x[0] for x in SKINS if x[5] is None}
    n = 0
    for sid in sorted(voulus):
        cible = CORE / ("camo_%d.json" % sid)
        texte = modele.replace('"heralisz_camo:camo_65"', '"heralisz_camo:camo_%d"' % sid)
        if not cible.exists() or cible.read_text(encoding="utf-8") != texte:
            cible.write_bytes(texte.encode("utf-8"))
            n += 1
    for x in SKINS:
        if x[0] not in voulus:
            for ext in (".json", ".fsh"):
                (CORE / ("camo_%d%s" % (x[0], ext))).unlink(missing_ok=True)
    return n


def verifier_catalogue():
    vus, ids = set(), set()
    for sid, couteau, fin, *_ in SKINS:
        if sid in ids:
            sys.exit("[skins_couteaux] shader %d en double" % sid)
        if fin in vus:
            sys.exit("[skins_couteaux] finition %s en double (une finition = un skin)" % fin)
        ids.add(sid)
        vus.add(fin)


# --- objets
ROOT = MOD.parent.parent
GUNPACK = ROOT / "serveur" / "tacz" / "heralisz_skins"
MELEE_DATA = MOD / "src" / "serveur" / "resources" / "data" / "heralisz_melee" / "index" / "melee"
MELEE_ASSETS = MOD / "src" / "client" / "resources" / "assets" / "heralisz_melee"
JAVA_FINITIONS = MOD / "src" / "commun" / "java" / "fr" / "heralisz" / "camo" / "knife" / "KnifeFinish.java"
ARKANIAZ = ROOT / "serveur" / "tacz" / "arkaniaz_pack_4_converted" / "assets" / "arkaniaz" / "display" / "guns"

# couteaux TaCZ (gunpack heralisz_skins) : affichage pris pour modèle
TACZ = {
    "m9": GUNPACK / "assets" / "heralisz_skins" / "display" / "guns" / "m9__lore_display.json",
    "karambit": GUNPACK / "assets" / "heralisz_skins" / "display" / "guns" / "karambit__fade_display.json",
    "butterfly": GUNPACK / "assets" / "heralisz_skins" / "display" / "guns" / "butterfly__fade_display.json",
    "ct_knife": ARKANIAZ / "ct_knife_display.json",
    "t_knife": ARKANIAZ / "t_knife_display.json",
}
NOMS_TACZ = {
    "m9": ("M9 Bayonet", "M9 Bayonet", "M9 Bayonet"),
    "karambit": ("Karambit", "Karambit", "Karambit"),
    "butterfly": ("Butterfly Knife", "Butterfly Knife", "Butterfly Knife"),
    "ct_knife": ("Couteau CT", "CT Knife", "Нож CT"),
    "t_knife": ("Couteau T", "T Knife", "Нож T"),
}
# armes de mêlée de LR : modèle et texture propres (outils/skins_melee.py)
MELEE = {
    "tac_dagger": "silencefd_skin",
    "creed": "treasure_kn_skin",
    "desmoulins_edge": ("polaris_lore_geo", "polaris_lore_uv"),
    "dragons_fang": "quenching_skin",
    "executor": "executor_skin",
    "mercy": "mercy_skin",
    "sea_gleam": "thieve_sea_skin",
    "baseball_bat": ("baseball_bat_skin_geo", "baseball_bat_skin_uv"),
    "smite_hammer": "thunder_hammer_skin",
}
LANGUES = ("fr_fr", "en_us", "ru_ru")
PALIERS_LANGUES = {
    "classique": ("Classique", "Classic", "Классика"),
    "raffine": ("Raffiné", "Refined", "Изысканный"),
    "prestige": ("Prestige", "Prestige", "Престиж"),
    "ultime": ("Ultime", "Ultimate", "Ультимативный"),
}
EXCLUSIF = ("Exclusivité HeraliZ", "HeraliZ Exclusive", "Эксклюзив HeraliZ")


def ecrire_json(chemin, donnees):
    texte = json.dumps(donnees, ensure_ascii=False, indent=2) + "\n"
    if not chemin.exists() or chemin.read_text(encoding="utf-8") != texte:
        chemin.parent.mkdir(parents=True, exist_ok=True)
        chemin.write_bytes(texte.encode("utf-8"))


def sans_commentaires(texte):
    """JSON avec commentaires « // » (fiches de LR) : commentaires et virgules finales retirés."""
    lignes = []
    for ligne in texte.splitlines():
        i = ligne.find("//")
        while i >= 0 and ligne[:i].count('"') % 2:
            i = ligne.find("//", i + 2)
        lignes.append(ligne if i < 0 else ligne[:i])
    return re.sub(r",(\s*[}\]])", r"\1", "\n".join(lignes))


def lire_json(chemin):
    if not chemin.exists():
        return OrderedDict()
    return json.loads(sans_commentaires(chemin.read_text(encoding="utf-8")), object_pairs_hook=OrderedDict)


def sans_codes(t):
    return re.sub("§.", "", t).strip()


def ecrire_java():
    texte = JAVA_FINITIONS.read_text(encoding="utf-8")
    debut = "   // <skins_couteaux> : écrit par outils/skins_couteaux.py (catalogue des skins), ne pas modifier à la main\n"
    fin = "   // </skins_couteaux>"
    lignes = []
    for sid, couteau, f, nom, pal, style, *_ in SKINS:
        lignes.append('   %s("%s", %d, %s),' % (re.sub(r"[^A-Z0-9]", "_", f.upper()), f, sid, "true" if style else "false"))
    i, j = texte.index(debut) + len(debut), texte.index(fin)
    nouveau = texte[:i] + "\n".join(lignes) + "\n" + texte[j:]
    if nouveau != texte:
        JAVA_FINITIONS.write_bytes(nouveau.encode("utf-8"))


def textes(couteau, nom, pal, arme):
    """(nom, description) dans les trois langues ; arme : nom de l'arme de base (fr, en, ru)."""
    code = PALIERS[pal][1]
    noms = ["%s★ %s §7| %s§l%s" % (code, arme[i], code, nom) for i in range(3)]
    descs = ["%s%s\n§8%s" % (code, PALIERS_LANGUES[pal][i], EXCLUSIF[i]) for i in range(3)]
    return noms, descs


def ecrire_objets():
    langues_pack = {l: lire_json(GUNPACK / "assets" / "heralisz_skins" / "lang" / (l + ".json")) for l in LANGUES}
    if not langues_pack["ru_ru"]:
        langues_pack["ru_ru"] = OrderedDict(langues_pack["en_us"])          # le gunpack n'avait pas de russe
    langues_melee = {l: lire_json(MELEE_ASSETS / "lang" / (l + ".json")) for l in LANGUES}
    ordre = 300
    for sid, couteau, f, nom, pal, style, *_ in SKINS:
        cle = "%s__%s" % (couteau, f)
        if couteau in TACZ:
            ordre += 1
            noms, descs = textes(couteau, nom, pal, NOMS_TACZ[couteau])
            ecrire_json(GUNPACK / "data" / "heralisz_skins" / "index" / "guns" / (cle + ".json"), OrderedDict([
                ("name", "heralisz_skins.knife.%s.name" % cle), ("display", "heralisz_skins:%s_display" % cle),
                ("data", "arkaniaz:knife_data"), ("tooltip", "heralisz_skins.tooltip.%s.desc" % cle),
                ("type", "rpg"), ("item_type", "modern_kinetic"), ("sort", ordre)]))
            affichage = lire_json(TACZ[couteau])
            affichage["slot"] = "heralisz_skins:gun/slot/%s" % cle
            ecrire_json(GUNPACK / "assets" / "heralisz_skins" / "display" / "guns" / (cle + "_display.json"), affichage)
            for i, l in enumerate(LANGUES):
                langues_pack[l]["heralisz_skins.knife.%s.name" % cle] = noms[i]
                langues_pack[l]["heralisz_skins.tooltip.%s.desc" % cle] = descs[i]
        elif couteau in MELEE:
            arme = [sans_codes(langues_melee[l]["item.heralisz_melee.%s" % couteau]) for l in LANGUES]
            noms, descs = textes(couteau, nom, pal, arme)
            fiche = lire_json(MELEE_DATA / (couteau + ".json"))
            fiche["name"] = "item.heralisz_melee.%s" % cle
            fiche = OrderedDict([("name", fiche["name"]), ("tooltip", "item.heralisz_melee.%s.desc" % cle)]
                                + [(a, b) for a, b in fiche.items() if a not in ("name", "tooltip")])
            ecrire_json(MELEE_DATA / (cle + ".json"), fiche)
            m = MELEE[couteau]
            geo, tex = m if isinstance(m, tuple) else (m + "_geo", m + "_uv")
            affichage = lire_json(MELEE_ASSETS / "display" / "melee" / (couteau + ".json"))
            affichage["model"] = "heralisz_melee:melee/%s" % geo
            affichage["texture"] = "heralisz_melee:melee/%s" % tex
            affichage["slot_texture"] = "heralisz_melee:melee/slot/%s" % cle
            ecrire_json(MELEE_ASSETS / "display" / "melee" / (cle + ".json"), affichage)
            for i, l in enumerate(LANGUES):
                langues_melee[l]["item.heralisz_melee.%s" % cle] = noms[i]
                langues_melee[l]["item.heralisz_melee.%s.desc" % cle] = descs[i]
        else:
            sys.exit("[skins_couteaux] arme inconnue : " + couteau)
    for l in LANGUES:
        ecrire_json(GUNPACK / "assets" / "heralisz_skins" / "lang" / (l + ".json"), langues_pack[l])
        ecrire_json(MELEE_ASSETS / "lang" / (l + ".json"), langues_melee[l])


def main():
    verifier_catalogue()
    ecrire_gabarit()
    n = ecrire_programmes()
    ecrire_java()
    ecrire_objets()
    subprocess.run([sys.executable, str(OUTILS / "generer_camos.py")], check=True)
    print("[skins_couteaux] %d skins, %d programmes ajoutés" % (len(SKINS), n))


if __name__ == "__main__":
    main()
