"""
Bombe des duels (recherche et destruction, HeraliZ Matchs) : modèles GeckoLib et textures HD, refaits par programme.
Rendus validés par le propriétaire : bombe « ogive sur berceau » (proposition 1) et ordinateur « terminal noir VX-9 »
(proposition 2).

    python mods/heralisz-matchs/outils/bombe/modeles.py              -> assets du mod (geo/ et textures/entity/)
    python mods/heralisz-matchs/outils/bombe/modeles.py --sortie D    -> mêmes fichiers dans le dossier D (essais)

Fichiers écrits dans src/client/resources/assets/heralisz_matchs/ :
    geo/duel_bombe.geo.json                       bombe (os : racine, bombe, gyrophare)
    textures/entity/duel_bombe_<a|b>[_arme].png   une texture par site, et la variante amorcée (écran ARMED, rouge)
    geo/duel_ordinateur.geo.json                  ordinateur ouvert (os : racine, base, ecran, antenne)
    geo/duel_ordinateur_ferme.geo.json            ordinateur fermé (porté en main, ou au sol après la mort du porteur)
    textures/entity/duel_ordinateur_<etat>_<a|b>.png   écran de chaque étape : amorcage, amorcee, piratage, desamorcee
    textures/entity/duel_ordinateur_ferme.png     ordinateur fermé
Chaque texture a son masque lumineux <nom>_glowmask.png (lettre, écrans, liserés orange), dessiné en plein éclat par le
jeu (duels/client/bombe/CalqueLumineux). Les parties de l'écran qui bougent (chiffres, barres, anneau, compte à
rebours) ne sont pas peintes : le jeu les dessine en direct (voir ecrans.py).

Unités : pixels de modèle (16 par bloc), espace de Blockbench, avant du modèle vers le nord (-Z), sol en y = 0.
Bibliothèques : numpy et Pillow.
"""
import argparse
import json
import shutil
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import ecrans  # noqa: E402
from geo import Modele, metal, caoutchouc, uni, lumineux, avec_motif  # noqa: E402

ICI = Path(__file__).parent
ASSETS = ICI.parents[1] / "src" / "client" / "resources" / "assets" / "heralisz_matchs"

# ------------------------------------------------------------------ teintes
OLIVE = "#4c5631"
JAUNE = "#d2a21c"
ACIER = "#5d6266"
ACIER_FONCE = "#2f3337"
GUNMETAL = "#272b30"
ORANGE = "#ff7a1a"
JAUNE_STENCIL = (226, 178, 40, 240)
NOIR_STENCIL = (16, 16, 16, 230)


def m_olive(g=0):
    return metal(OLIVE, usure=0.55, crasse=0.45, couleur_usure="#8c8f80", graine=g)


def m_acier(g=10):
    return metal(ACIER, usure=0.3, crasse=0.35, couleur_usure="#a8adb0", graine=g)


def m_acier_fonce(g=20):
    return metal(ACIER_FONCE, usure=0.45, crasse=0.3, couleur_usure="#8a9094", graine=g)


def m_jaune(g=30):
    return metal(JAUNE, usure=0.6, crasse=0.5, couleur_usure="#6d675a", graine=g)


def m_gun(g=40):
    return metal(GUNMETAL, usure=0.35, crasse=0.25, couleur_usure="#7b8288", graine=g, bords=0.16)


import numpy as np  # noqa: E402  (utilisé par les motifs)


def carbone(fc, rgb):
    """Tissage de carbone (2 x 2 texels de modèle par motif)."""
    s = 0.5
    a = (np.floor(fc.U / s) + np.floor(fc.V / s)) % 2
    t = (fc.U / s) % 1.0
    t2 = (fc.V / s) % 1.0
    reflet = np.where(a > 0, 0.85 + 0.3 * np.abs(t - 0.5), 0.85 + 0.3 * np.abs(t2 - 0.5))
    return rgb * reflet[..., None]


# ================================================================== BOMBE : OGIVE SUR BERCEAU
def bombe_ogive(site, arme=False):
    m = Modele("bombe_ogive_" + site.lower() + ("_arme" if arme else ""), densite=8, largeur_atlas=1024)
    etat_fond, etat_txt, etat_coul = ((70, 12, 8, 255), "ARMED", (255, 90, 70, 255)) if arme else \
        ((20, 60, 24, 255), "READY", (140, 255, 120, 255))
    m.os("racine")
    m.os("bombe", parent="racine")
    m.os("gyrophare", pivot=(-2.5, 14, 0), parent="bombe")
    cy, R = 9.0, 4.6

    # ---- berceau : rails, traverses, selles
    rail = m_acier_fonce(21)
    for zs in (-1, 1):
        z0, z1 = (3.4, 5.0) if zs > 0 else (-5.0, -3.4)
        deco_rail = {"north" if zs < 0 else "south": [("rayures", 1.0, 0.25, 21.0, 1.15, 0.9, (205, 160, 30, 255), (22, 22, 22, 255))]}
        m.cube("bombe", (-11, 0, z0), (11, 1.4, z1), rail, deco=deco_rail)
        # anneaux de levage aux bouts
        for xs in (-1, 1):
            x = 11.3 * xs
            m.boite("bombe", (x, 1.6, (z0 + z1) / 2), (0.6, 1.6, 1.0), m_acier(22))
    for x in (-9.5, 9.5):
        m.cube("bombe", (x - 0.7, 0.2, -3.4), (x + 0.7, 1.2, 3.4), rail)
    for x in (-4.6, 4.6):
        sel = m_jaune(31)
        m.cube("bombe", (x - 1.2, 1.4, -4.4), (x + 1.2, 4.6, 4.4), sel,
               deco={"north": [("texte", 1.2, 1.6, "HZ", "oswald", 1.3, NOIR_STENCIL, {"ancre": "mm"})],
                     "south": [("texte", 1.2, 1.6, "HZ", "oswald", 1.3, NOIR_STENCIL, {"ancre": "mm"})]})
        for zs in (-1, 1):
            zz = (2.6, 4.4) if zs > 0 else (-4.4, -2.6)
            m.cube("bombe", (x - 1.2, 4.6, zz[0]), (x + 1.2, 6.4, zz[1]), sel)
        # sangle (anneau sombre) + boucle
        m.prisme8("bombe", (x, cy, 0), R + 0.22, 1.0, "x", metal("#2a2c2a", usure=0.2, crasse=0.1, graine=33), couleur_bouts="#2a2c2a")
        m.boite("bombe", (x, cy + 1.5, -R - 0.45), (1.5, 1.6, 0.5), m_acier(34))

    # ---- corps (cylindre à 8 pans)
    def deco_corps(k, f):
        # k = 2 : face « down » tournée vers le nord, « up » vers le sud ; k = 0 « up » : dessus
        # côté sud (face « up » de k = 2, retournée) : marquages entre les deux sangles ; côté nord : caché par le module
        if k == 2 and f == "up":
            return [("texte", 4.9, 1.75, "HZ-M7", "oswald", 1.15, JAUNE_STENCIL, {"ancre": "mm"}),
                    ("texte", 4.9, 2.85, "500 KG", "oswald5", 0.55, (230, 220, 190, 200), {"ancre": "mm"}),
                    ("rect", 7.1, 0.3, 10.6, 3.5, (210, 160, 26, 245)),
                    ("cadre", 7.3, 0.5, 10.4, 3.3, 0.16, (18, 18, 18, 255)),
                    ("texte", 8.85, 1.95, site, "oswald", 3.2, (18, 18, 18, 255), {"ancre": "mm"})]
        if k == 0 and f == "up":
            return [("texte", 5.55, 1.95, "SITE " + site, "oswald", 1.55, JAUNE_STENCIL, {"ancre": "mm", "espacement": 0.12})]
        if k == 1 and f == "down" or k == 3 and f == "down":
            return [("rainure", 0.3, 0.2, 0.3, 3.6, 0.12), ("rainure", 13.7, 0.2, 13.7, 3.6, 0.12)]
        return None

    corps = m.prisme8("bombe", (0, cy, 0), R, 14, "x", m_olive(1), deco_cotes=deco_corps, faces_bouts=("a", "b"), couleur_bouts="#454e2c")
    for c in corps:
        if c.rot == (90, 0, 0) and "up" in c.deco:
            c.deco_rot = {"up": 180}
    # bande jaune + nez en paliers + fusée
    m.prisme8("bombe", (7.8, cy, 0), 4.25, 1.6, "x", m_jaune(35), faces_bouts=("b",), couleur_bouts="#b88f1a")
    m.prisme8("bombe", (9.3, cy, 0), 3.55, 1.4, "x", m_olive(2), faces_bouts=("b",), couleur_bouts="#454e2c")
    m.prisme8("bombe", (10.6, cy, 0), 2.7, 1.2, "x", m_olive(3), faces_bouts=("b",), couleur_bouts="#454e2c")
    m.prisme8("bombe", (11.6, cy, 0), 1.8, 0.8, "x", m_olive(4), faces_bouts=("b",), couleur_bouts="#454e2c")
    m.prisme8("bombe", (12.5, cy, 0), 1.0, 1.0, "x", m_acier(5), faces_bouts=("b",), couleur_bouts="#7d8286")
    m.prisme8("bombe", (13.3, cy, 0), 0.55, 0.6, "x", m_acier(6), faces_bouts=("b",), couleur_bouts="#8d9296")
    # queue
    m.prisme8("bombe", (-7.7, cy, 0), 4.2, 1.4, "x", m_olive(7), faces_bouts=("a",), couleur_bouts="#454e2c")
    m.prisme8("bombe", (-9.0, cy, 0), 3.4, 1.2, "x", m_olive(8), faces_bouts=("a",), couleur_bouts="#454e2c")
    for ang in (45, -45):
        m.cube("bombe", (-12.6, cy - 6.0, -0.25), (-8.2, cy + 6.0, 0.25), m_olive(9), pivot=(0, cy, 0), rot=(ang, 0, 0),
               deco={"north": [("rayures", 0.0, 0.0, 4.4, 1.0, 0.5, (205, 160, 30, 255), (22, 22, 22, 255)),
                               ("rayures", 0.0, 11.0, 4.4, 12.0, 0.5, (205, 160, 30, 255), (22, 22, 22, 255))],
                     "south": [("rayures", 0.0, 0.0, 4.4, 1.0, 0.5, (205, 160, 30, 255), (22, 22, 22, 255)),
                               ("rayures", 0.0, 11.0, 4.4, 12.0, 0.5, (205, 160, 30, 255), (22, 22, 22, 255))]})
    m.prisme8("bombe", (-12.1, cy, 0), 3.2, 0.7, "x", m_olive(11), faces_bouts=("a", "b"), couleur_bouts="#454e2c")

    # ---- module de commande (face nord, là où l'on branche l'ordinateur)
    face_module = [
        ("rect", 0, 0, 7, 0.7, (200, 155, 28, 255)),
        ("rayures", 0, 0, 7, 0.7, 0.45, (205, 160, 30, 255), (20, 20, 20, 255)),
        # lettre lumineuse
        ("rrect", 0.45, 1.05, 3.05, 4.55, 0.25, (14, 14, 14, 255)),
        ("texte", 1.75, 2.85, site, "oswald", 3.2, (255, 60, 40, 255) if arme else (255, 150, 40, 255), {"ancre": "mm", "glow": True}),
        # petit écran d'état
        ("rrect", 3.45, 1.05, 6.55, 2.25, 0.15, (10, 10, 10, 255)),
        ("rect", 3.6, 1.2, 6.4, 2.1, etat_fond, {"glow": True}),
        ("texte", 3.75, 1.65, etat_txt, "mono", 0.62, etat_coul, {"ancre": "lm", "glow": True}),
        ("rond", 6.05, 1.65, 0.18, (255, 70, 50, 255), {"glow": True}),
        # clavier 3 x 3
    ]
    for i in range(3):
        for j in range(3):
            u, v = 3.65 + i * 0.62, 2.55 + j * 0.62
            face_module.append(("rrect", u, v, u + 0.5, v + 0.5, 0.08, (52, 54, 52, 255)))
            face_module.append(("rect", u + 0.06, v + 0.06, u + 0.44, v + 0.2, (95, 98, 92, 255)))
    face_module += [
        # prise de données
        ("rrect", 5.65, 2.55, 6.55, 3.55, 0.12, (10, 10, 10, 255)),
        ("rrect", 5.85, 2.8, 6.35, 3.3, 0.08, (40, 40, 40, 255)),
        ("rond", 6.1, 3.05, 0.12, (255, 160, 40, 255), {"glow": True}),
        ("texte", 6.1, 4.05, "DATA", "mono", 0.4, (220, 215, 200, 220), {"ancre": "mm"}),
        ("vis", 0.25, 4.75, 0.14), ("vis", 6.75, 4.75, 0.14), ("vis", 6.75, 1.0, 0.14), ("vis", 0.25, 1.0, 0.14),
    ]
    m.cube("bombe", (-3.5, 6.4, -6.7), (3.5, 11.4, -4.0), m_gun(41), densite=16, deco={"north": face_module})
    m.cube("bombe", (-3.9, 6.0, -6.9), (3.9, 6.5, -3.9), m_acier_fonce(42))
    # poignées du module
    for x in (-4.2, 3.8):
        m.cube("bombe", (x, 7.4, -6.3), (x + 0.4, 10.4, -5.9), m_acier(43))
    # câbles du module vers le corps
    for i, (x, coul) in enumerate(((-2.6, "#b3261e"), (-1.9, "#d8b21e"), (2.2, "#1e4fb3"))):
        m.cube("bombe", (x, 11.4, -5.6), (x + 0.45, 12.6, -5.15), uni(coul, 0.08, 60 + i))
        m.cube("bombe", (x, 12.15, -5.15), (x + 0.45, 12.6, -3.6), uni(coul, 0.08, 63 + i))

    # ---- gyrophare (repère du site, ambre)
    m.boite("bombe", (-2.5, R + cy + 0.25, 0), (2.4, 0.6, 2.4), m_acier_fonce(44))
    m.prisme8("gyrophare", (-2.5, cy + R + 1.3, 0), 0.95, 1.5, "y", lumineux("#ff3020" if arme else "#ffae1a", coeur="#ffc0b0" if arme else "#fff1b0"), faces_bouts=("b",),
               couleur_bouts="#ff6050" if arme else "#ffcf60", bouts_lumineux=True)
    m.boite("gyrophare", (-2.5, cy + R + 2.2, 0), (1.3, 0.35, 1.3), m_acier_fonce(45))
    m.info = {"hauteur_px": cy + R + 2.4, "longueur_px": 26}
    return m


# ================================================================== ORDINATEUR : TERMINAL NOIR « VX-9 »
def ordi_noir(etat_ecran="amorcage", site="A", ouvert=True):
    nom = "ordi_noir"
    m = Modele(nom, densite=16, largeur_atlas=1024)
    m.os("racine")
    m.os("base", parent="racine")
    m.os("ecran", pivot=(0, 1.0, 3.6), rotation=(12 if ouvert else -90, 0, 0), parent="base")
    m.os("antenne", pivot=(5.3, 8.0, 4.0), rotation=(0, 0, -18), parent="ecran")
    coque = avec_motif(metal("#1a1c20", usure=0.25, crasse=0.15, couleur_usure="#5b6068", graine=110, bords=0.12), carbone)
    lisse = metal("#202328", usure=0.25, crasse=0.1, couleur_usure="#6b7078", graine=111, bords=0.12)
    orange = lumineux(ORANGE, coeur="#ffd8b0")

    clav = [("rect", 0.0, 0.0, 11.0, 7.2, (24, 26, 29, 255)),
            ("rrect", 0.6, 0.4, 10.4, 4.3, 0.15, (10, 10, 11, 255))]
    for r, n in enumerate((13, 13, 12, 11)):
        pas = 9.5 / 13
        dec = (13 - n) * pas / 2
        for i in range(n):
            u = 0.75 + dec + i * pas
            v = 0.55 + r * 0.74
            clav.append(("rrect", u, v, u + pas - 0.1, v + 0.62, 0.06, (20, 21, 23, 255)))
            clav.append(("rect", u + 0.06, v + 0.56, u + pas - 0.16, v + 0.62, (255, 122, 26, 200), {"glow": True}))
            clav.append(("rect", u + 0.2, v + 0.2, u + 0.42, v + 0.28, (255, 190, 140, 160), {"glow": True}))
    clav += [("rrect", 3.3, 3.55, 7.7, 4.15, 0.06, (20, 21, 23, 255)),
             ("rrect", 4.0, 4.7, 7.0, 6.6, 0.2, (30, 32, 36, 255), {"contour": (255, 122, 26, 180), "ep": 0.06}),
             ("poly", [(0.6, 6.9), (1.6, 5.4), (2.0, 5.4), (1.0, 6.9)], (255, 122, 26, 255), {"glow": True}),
             ("poly", [(10.4, 6.9), (9.4, 5.4), (9.0, 5.4), (10.0, 6.9)], (255, 122, 26, 255), {"glow": True}),
             ("texte", 5.5, 6.95, "VX-9  BREACH", "oswald", 0.35, (190, 190, 195, 200), {"ancre": "mm", "espacement": 0.06})]
    m.cube("base", (-5.5, 0.15, -3.6), (5.5, 1.0, 3.6), coque, deco={
        "up": clav,
        "north": [("rect", 0.4, 0.3, 10.6, 0.45, (255, 122, 26, 255), {"glow": True})],
        "east": [("rrect", 1.0, 0.25, 2.6, 0.7, 0.1, (8, 8, 8, 255)), ("rect", 3.2, 0.4, 6.4, 0.55, (255, 122, 26, 255), {"glow": True})],
        "west": [("rect", 0.8, 0.4, 4.0, 0.55, (255, 122, 26, 255), {"glow": True}), ("rrect", 4.6, 0.25, 6.2, 0.7, 0.1, (8, 8, 8, 255))]})
    m.cube("base", (-5.0, 0, -3.1), (5.0, 0.15, 3.1), caoutchouc("#0e0f10", 112))
    # chanfrein avant (arête biseautée)
    m.cube("base", (-5.5, 0.35, -3.95), (5.5, 0.85, -3.45), lisse, pivot=(0, 0.6, -3.6), rot=(45, 0, 0))
    # module « pointe de piratage » sur le côté droit
    m.prisme8("base", (6.2, 0.6, 1.4), 0.55, 1.4, "x", lisse, faces_bouts=("b",), couleur_bouts="#202328")
    m.prisme8("base", (6.0, 0.6, 1.4), 0.62, 0.3, "x", orange, faces_bouts=("a", "b"), couleur_bouts="#ff7a1a", bouts_lumineux=True)
    m.cube("base", (6.9, 0.45, 1.25), (7.5, 0.75, 1.55), metal("#8a8f96", usure=0.1, graine=113))

    # écran du jeu : parties mobiles laissées au rendu en direct (ecrans.py)
    img = ecrans.tous(site, jeu=True)[etat_ecran]
    lunette = [("rect", 0, 0, 11, 7.4, (14, 15, 17, 255)),
               ("poly", [(0.4, 0.4), (10.6, 0.4), (10.6, 6.5), (9.9, 7.0), (1.1, 7.0), (0.4, 6.5)], (4, 4, 5, 255)),
               ("image", 0.75, 0.65, 10.25, 6.55, img, {"glow": True}),
               ("rect", 4.0, 7.05, 7.0, 7.2, (255, 122, 26, 255), {"glow": True})]
    dos = [("poly", [(5.5, 1.4), (7.6, 5.4), (6.9, 5.4), (5.5, 2.7), (4.1, 5.4), (3.4, 5.4)], (255, 122, 26, 255), {"glow": True}),
           ("texte", 5.5, 6.4, "VX-9", "oswald", 0.6, (170, 172, 178, 200), {"ancre": "mm", "espacement": 0.2})]
    m.cube("ecran", (-5.5, 1.0, 3.6), (5.5, 8.4, 4.3), coque, deco={"north": lunette, "south": dos})
    m.info = {"ecran": etat_ecran}
    # « ailes » angulaires en haut de l'écran
    for xs in (-1, 1):
        m.cube("ecran", (5.5 * xs - 0.5, 7.4, 3.55), (5.5 * xs + 0.5, 8.6, 4.35), lisse, pivot=(5.5 * xs, 8.0, 3.95), rot=(0, 0, 35 * xs))
    # antenne repliable
    m.cube("antenne", (5.15, 8.0, 3.85), (5.45, 12.5, 4.15), lisse)
    m.prisme8("antenne", (5.3, 12.7, 4.0), 0.22, 0.4, "y", orange, faces_bouts=("b",), couleur_bouts="#ffb070", bouts_lumineux=True)
    return m


# ------------------------------------------------------------------ fabrication
def _geo(dossier, nom, identifiant):
    d = json.loads((dossier / (nom + ".geo.json")).read_text(encoding="utf-8"))
    d["minecraft:geometry"][0]["description"]["identifier"] = "geometry." + identifiant
    return d


def _meme_geometrie(a, b):
    a = json.loads(json.dumps(a))
    b = json.loads(json.dumps(b))
    for x in (a, b):
        g = x["minecraft:geometry"][0]
        g["description"]["identifier"] = ""
        for o in g["bones"]:
            if o["name"] == "ecran":
                o.pop("rotation", None)
    return a == b


def tout(sortie=None):
    """Construit tout dans un dossier temporaire, vérifie que les variantes partagent la même géométrie (mêmes UV),
    puis range les fichiers dans les assets du mod (ou dans le dossier sortie)."""
    geo_dir = (sortie or ASSETS) / "geo"
    tex_dir = (sortie or ASSETS) / "textures" / "entity"
    geo_dir.mkdir(parents=True, exist_ok=True)
    tex_dir.mkdir(parents=True, exist_ok=True)
    faits = []
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        textures = {}
        # bombes : une texture par site et par état (même géométrie)
        ref_bombe = None
        for site in ("A", "B"):
            for arme in (False, True):
                m = bombe_ogive(site, arme)
                m.construire(tmp)
                g = _geo(tmp, m.nom, "duel_bombe")
                if ref_bombe is None:
                    ref_bombe = g
                elif not _meme_geometrie(g, ref_bombe):
                    raise SystemExit("géométrie différente pour " + m.nom)
                textures[m.nom] = "duel_bombe_%s%s" % (site.lower(), "_arme" if arme else "")
        # ordinateur ouvert : une texture par étape et par site ; fermé : une seule texture (écran caché)
        ref_ordi = None
        ferme = None
        for etat in ("amorcage", "amorcee", "piratage", "desamorcee"):
            for site in ("A", "B"):
                m = ordi_noir(etat, site, True)
                m.nom = "ordi_%s_%s" % (etat, site.lower())
                m.construire(tmp)
                g = _geo(tmp, m.nom, "duel_ordinateur")
                if ref_ordi is None:
                    ref_ordi = g
                elif not _meme_geometrie(g, ref_ordi):
                    raise SystemExit("géométrie différente pour " + m.nom)
                textures[m.nom] = "duel_ordinateur_%s_%s" % (etat, site.lower())
        m = ordi_noir("veille", "A", False)
        m.nom = "ordi_ferme"
        m.construire(tmp)
        ferme = _geo(tmp, m.nom, "duel_ordinateur_ferme")
        if not _meme_geometrie(ferme, ref_ordi):
            raise SystemExit("géométrie différente pour l'ordinateur fermé")
        textures[m.nom] = "duel_ordinateur_ferme"
        # écriture
        for nom, g in (("duel_bombe", ref_bombe), ("duel_ordinateur", ref_ordi), ("duel_ordinateur_ferme", ferme)):
            (geo_dir / (nom + ".geo.json")).write_text(json.dumps(g, indent=1) + "\n", encoding="utf-8")
            faits.append("geo/%s.geo.json" % nom)
        for src, dst in textures.items():
            for suffixe in ("", "_glowmask"):
                f = tmp / (src + suffixe + ".png")
                if f.exists():
                    shutil.copyfile(f, tex_dir / (dst + suffixe + ".png"))
                    faits.append("textures/entity/%s%s.png" % (dst, suffixe))
    return faits


if __name__ == "__main__":
    import time
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--sortie", type=Path, default=None, help="dossier de sortie (par défaut : assets du mod)")
    a = ap.parse_args()
    t = time.time()
    for f in tout(a.sortie):
        print(f)
    print("%.1f s" % (time.time() - t))
