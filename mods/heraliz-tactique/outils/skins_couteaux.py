#!/usr/bin/env python3
"""
Skins de couteaux par paliers (Classique, Raffiné, Prestige, Ultime) : catalogue et fichiers qui en découlent, écrits par
ce script, jamais à la main.

    python mods/heraliz-tactique/outils/skins_couteaux.py

Chaque skin est un programme de shader camo_<id>.fsh (ids 120 à 199 ; les finitions exclusives 100 à 111 déjà écrites
dans le gabarit sont reprises telles quelles) qui appelle un style du gabarit (kSatin, kDamascus, kFade… : section
« skins de couteaux par paliers » de outils/camo_gabarit.fsh) avec sa palette.

Écrit :
  - le bloc <skins_couteaux> du gabarit (fonctions skinCouteau / skinGrip et teinte de l'insert du Butterfly, par skin) ;
  - les camo_<id>.json manquants (copies de camo_65.json), puis relance generer_camos.py.
"""
import json
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


def glsl_arg(a):
    if isinstance(a, int):
        return "hexc(0x%06x)" % a
    if isinstance(a, float):
        return repr(a)
    if isinstance(a, tuple):
        return "vec3(%s)" % ", ".join(repr(float(x)) for x in a)
    raise ValueError(a)


def bloc_shader():
    lignes = []
    premier = True
    for sid, couteau, fin, nom, palier, style, args, manche, insert in SKINS:
        if style is None:
            continue
        lignes.append(("#if" if premier else "#elif") + " CAMO_ID == %d   // %s | %s (%s)" % (sid, couteau, nom, PALIERS[palier][0]))
        premier = False
        lignes.append("#define SKIN_INLAY 0x%06x" % (insert if insert is not None else 0x1a1b1e))
        lignes.append("vec3 skinCouteau(vec3 p, out vec3 em) {")
        lignes.append("    return %s(%s);" % (style, ", ".join(["p"] + [glsl_arg(a) for a in args] + ["em"])))
        lignes.append("}")
        lignes.append("vec3 skinGrip(vec3 p, out vec3 em) {")
        if manche is not None and manche[0] == "wrap":
            _, d, l, a, g = manche
            lignes.append("    return gripWrap(p, hexc(0x%06x), hexc(0x%06x), hexc(0x%06x), %r, em);" % (d, l, a, float(g)))
        else:
            lignes.append("    em = vec3(0.0);")
            lignes.append("    return rubberGrip(p);")
        lignes.append("}")
    lignes.append("#else")
    lignes.append("#define SKIN_INLAY 0x1a1b1e")
    lignes.append("vec3 skinCouteau(vec3 p, out vec3 em) { em = vec3(0.0); return vec3(0.5); }")
    lignes.append("vec3 skinGrip(vec3 p, out vec3 em) { em = vec3(0.0); return rubberGrip(p); }")
    lignes.append("#endif")
    return "\n".join(lignes)


def ecrire_gabarit():
    texte = GABARIT.read_text(encoding="utf-8")
    debut = "// <skins_couteaux> : écrit par outils/skins_couteaux.py (catalogue des skins), ne pas modifier à la main\n"
    fin = "// </skins_couteaux>"
    i, j = texte.index(debut) + len(debut), texte.index(fin)
    nouveau = texte[:i] + bloc_shader() + "\n" + texte[j:]
    if nouveau != texte:
        GABARIT.write_bytes(nouveau.encode("utf-8"))


def ecrire_programmes():
    modele = (CORE / "camo_65.json").read_text(encoding="utf-8")
    n = 0
    for sid, *_ in SKINS:
        cible = CORE / ("camo_%d.json" % sid)
        texte = modele.replace('"heralisz_camo:camo_65"', '"heralisz_camo:camo_%d"' % sid)
        if not cible.exists() or cible.read_text(encoding="utf-8") != texte:
            cible.write_bytes(texte.encode("utf-8"))
            n += 1
    return n


def verifier_catalogue():
    vus, ids = set(), set()
    for sid, couteau, fin, *_ in SKINS:
        if sid in ids:
            sys.exit("[skins_couteaux] shader %d en double" % sid)
        if (couteau, fin) in vus:
            sys.exit("[skins_couteaux] %s__%s en double" % (couteau, fin))
        ids.add(sid)
        vus.add((couteau, fin))


def main():
    verifier_catalogue()
    ecrire_gabarit()
    n = ecrire_programmes()
    subprocess.run([sys.executable, str(OUTILS / "generer_camos.py")], check=True)
    print("[skins_couteaux] %d skins, %d programmes ajoutés" % (len(SKINS), n))


if __name__ == "__main__":
    main()
