#!/usr/bin/env python3
"""
Modèle « casier militaire » de l'armoire à outils (Claim Block façon TC de Rust), proposition C du 18/10.

Écrit le modèle de bloc (éléments Minecraft) et sa texture peinte au calcul, au style de la caisse en métal de CRac
(vert armée, tôles nervurées, bords usés, rivets, rouille). Rien n'est dessiné à la main : on modifie ce script.

    python mods/crac/outils/tc_modele.py --apercu DOSSIER     écrit tc_casier.json + tc_casier.png dans DOSSIER
    python mods/crac/outils/tc_modele.py --installer          écrit le modèle dans le mod (CRac 1.10.0) : modèle et
                                                              texture du Claim Block, ses états (sens et tier), sa case
                                                              du haut invisible et le modèle de l'objet

Le casier fait 1 bloc de large et 2 de haut (y de 0 à 32), face avant au nord (-z), dos contre le mur (z = 16).
2 pixels de texture par pixel de modèle (32 px par bloc, comme la texture de la caisse).
"""
import argparse
import json
import random
from pathlib import Path

from PIL import Image

DENSITE = 2           # pixels de texture par unité de modèle
TEXTURE = "crac:block/claim_block"
ASSETS = Path(__file__).resolve().parents[1] / "src" / "client" / "resources" / "assets" / "crac"
# en main et dans l'inventaire : le casier fait 2 blocs de haut, réduit et redescendu au centre
AFFICHAGE = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -3.2, 0], "scale": [0.4, 0.4, 0.4]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [0.2, 0.2, 0.2]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, -3.2, 0], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 0.5, 0], "scale": [0.25, 0.25, 0.25]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 0.5, 0], "scale": [0.25, 0.25, 0.25]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -2, 0], "scale": [0.25, 0.25, 0.25]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, -2, 0], "scale": [0.25, 0.25, 0.25]},
}
ROTATION = {"north": 0, "east": 90, "south": 180, "west": 270}
# anciens fichiers du Claim Block (piquet et drapeau, avant la 1.10.0), retirés par --installer
ANCIENS = ["models/block/claim_block_tier1.json", "models/block/claim_block_tier2.json", "models/block/claim_block_tier3.json",
           "textures/block/claim_block_crate.png", "textures/block/claim_block_flag.png", "textures/item/claim_block.png"]
TAILLE = 512          # texture carrée (faces rangées avec leur gouttière)
GRAINE = 7

# palette relevée sur la texture de la caisse en métal (chest_one.png)
VERT = [(0x7a, 0x8e, 0x76), (0x6a, 0x7b, 0x65), (0x5a, 0x68, 0x55), (0x4a, 0x54, 0x45), (0x3c, 0x43, 0x37), (0x2d, 0x32, 0x29)]
SOMBRE = [(0x1f, 0x22, 0x1c), (0x0f, 0x11, 0x0e)]
METAL = [(0x9a, 0xa3, 0xa6), (0x7d, 0x88, 0x8d), (0x5c, 0x63, 0x66), (0x21, 0x23, 0x26)]
ROUILLE = [(0x8a, 0x55, 0x2e), (0x6e, 0x40, 0x22), (0x4f, 0x2e, 0x19)]
BOIS = [(0x8a, 0x66, 0x3c), (0x6e, 0x50, 0x2e), (0x52, 0x3a, 0x20)]
PAPIER = [(0xe6, 0xdf, 0xc6), (0xc9, 0xc1, 0xa4), (0x4a, 0x47, 0x40)]
LAMPE = [(0xb8, 0xff, 0xcc), (0x5f, 0xd0, 0x8a), (0x2f, 0x7a, 0x48)]

rnd = random.Random(GRAINE)


def bruit(c, f=10):
    d = rnd.randint(-f, f)
    return tuple(max(0, min(255, v + d)) for v in c)


# ------------------------------------------------------------------------------------------------- peintures

def peint(img, x, y, w, h, matiere):
    """Peint une face (rectangle de la texture) selon sa matière ; bords et usure compris."""
    px = img.load()
    for j in range(h):
        for i in range(w):
            bord = i == 0 or j == 0 or i == w - 1 or j == h - 1
            c = couleur(matiere, i, j, w, h, bord)
            px[x + i, y + j] = c + (255,)


def couleur(m, i, j, w, h, bord):
    if m == "tole":                       # tôle verte lisse, bords usés plus clairs, rouille en bas
        if bord:
            return bruit(VERT[0] if rnd.random() < .35 else VERT[2], 6)
        if j > h - 4 and rnd.random() < .35:
            return bruit(ROUILLE[rnd.randint(0, 2)], 6)
        if rnd.random() < .012:
            return bruit(VERT[1], 3)          # éraflure
        if j % 9 == 4 and rnd.random() < .5:
            return bruit(VERT[3], 2)          # coulure, trace d'usure horizontale
        return bruit(VERT[2], 3)
    if m == "nervure":                    # tôle nervurée : une nervure toutes les 6 lignes (lumière dessus, ombre dessous)
        if bord:
            return bruit(VERT[3], 5)
        k = j % 6
        base = VERT[1] if k == 0 else VERT[4] if k == 1 else VERT[2]
        if j > h - 5 and rnd.random() < .3:
            return bruit(ROUILLE[rnd.randint(0, 2)], 6)
        return bruit(base, 3)
    if m == "porte":                      # porte : cadre en relief, fentes d'aération, pochoir « TC-07 »
        if bord or i == 1 or j == 1 or i == w - 2 or j == h - 2:
            return bruit(VERT[1] if (i + j) % 2 else VERT[2], 4)
        if 4 <= j <= 12 and 4 <= i < w - 4 and j % 3 == 0:
            return bruit(SOMBRE[1], 3)        # fentes
        if 4 <= j <= 12 and 4 <= i < w - 4 and j % 3 == 1:
            return bruit(VERT[0], 4)
        if j > h - 4 and rnd.random() < .3:
            return bruit(ROUILLE[1], 6)
        return bruit(VERT[3], 3)
    if m == "dedans":
        return bruit(SOMBRE[0] if (i + j) % 5 else SOMBRE[1], 3)
    if m == "panneau":                    # panneau perforé du fond de la niche
        if i % 4 == 2 and j % 4 == 2:
            return SOMBRE[1]
        return bruit(BOIS[1] if not bord else BOIS[2], 6)
    if m == "metal":
        if bord:
            return bruit(METAL[2], 5)
        return bruit(METAL[1] if (i + j) % 3 else METAL[0], 6)
    if m == "metal_sombre":
        return bruit(METAL[3] if not bord else METAL[2], 4)
    if m == "rivet":
        return bruit(METAL[0] if not bord else METAL[2], 4)
    if m == "bois":
        return bruit(BOIS[0] if j % 4 else BOIS[1], 6)
    if m == "papier":                     # liste des accès punaisée : lignes d'écriture
        if bord:
            return PAPIER[1]
        if j % 3 == 2 and 2 <= i < w - 2 and rnd.random() < .8:
            return PAPIER[2]
        return bruit(PAPIER[0], 4)
    if m == "lampe":
        return LAMPE[0] if (1 <= i < w - 1 and 1 <= j < h - 1) else LAMPE[1]
    if m == "rouille":
        return bruit(ROUILLE[rnd.randint(0, 2)], 8)
    if m == "cable":
        return bruit((0x2a, 0x2a, 0x26) if (i + j) % 3 else (0x44, 0x44, 0x3c), 4)
    raise ValueError(m)


POCHOIR = {  # petites lettres 3 × 5 pour « TC-07 »
    "T": ["111", "010", "010", "010", "010"], "C": ["111", "100", "100", "100", "111"], "-": ["000", "000", "111", "000", "000"],
    "0": ["111", "101", "101", "101", "111"], "7": ["111", "001", "010", "010", "010"],
}


def pochoir(img, x, y, texte):
    px = img.load()
    for n, ch in enumerate(texte):
        for j, ligne in enumerate(POCHOIR[ch]):
            for i, b in enumerate(ligne):
                if b == "1" and rnd.random() < .85:          # pochoir un peu effacé
                    px[x + n * 4 + i, y + j] = bruit((0xc9, 0xc4, 0xa8), 8) + (255,)


# ------------------------------------------------------------------------------------------------- modèle

def elements():
    """Les pièces du casier : (nom, de, à, matière par défaut, matières par face)."""
    E = []

    def e(nom, a, b, mat, **faces):
        E.append((nom, a, b, mat, faces))

    # pieds et socle
    for x in (1, 13):
        for z in (4, 13):
            e("pied", (x, 0, z), (x + 2, 1, z + 2), "metal_sombre")
    e("socle", (1, 1, 4), (15, 2, 15.5), "tole")
    # caisson : flancs nervurés, dos
    e("flanc_g", (1, 2, 4), (2, 30, 15.5), "nervure", north="tole")
    e("flanc_d", (14, 2, 4), (15, 30, 15.5), "nervure", north="tole")
    e("dos", (2, 2, 14.5), (14, 30, 15.5), "tole")
    # bas : porte à fentes, poignée, moraillon et cadenas
    e("porte", (2, 2, 4), (14, 16, 5), "tole", north="porte", south="dedans")
    e("charniere_h", (1.5, 13, 3.5), (2.5, 15, 4.5), "metal")
    e("charniere_b", (1.5, 3, 3.5), (2.5, 5, 4.5), "metal")
    e("poignee", (11, 7, 3), (12, 11, 4), "metal")
    e("moraillon", (12, 9, 3.5), (14.5, 10, 4), "metal_sombre")
    e("cadenas", (12.5, 6.5, 2.5), (14, 8.5, 3.5), "metal")
    e("anse", (12.75, 8.5, 2.75), (13.75, 9.5, 3.25), "metal_sombre")
    # tablette entre les deux parties
    e("tablette", (1, 16, 3.5), (15, 17, 15), "tole")
    # niche du haut : fond perforé, montants, outils
    e("fond_niche", (2, 17, 13.5), (14, 28, 14.5), "panneau")
    e("plafond_niche", (2, 27, 4.5), (14, 28, 14.5), "dedans")
    e("montant_g", (1, 17, 3.5), (2.5, 30, 4.5), "tole")
    e("montant_d", (13.5, 17, 3.5), (15, 30, 4.5), "tole")
    e("linteau", (1, 28, 3.5), (15, 30, 4.5), "tole")
    e("marteau_manche", (4, 18.5, 12.5), (5, 25.5, 13.5), "bois")
    e("marteau_tete", (2.75, 24.5, 12.25), (6.25, 26.5, 13.5), "metal")
    e("cle", (7.5, 19, 12.75), (8.5, 25, 13.5), "metal")
    e("cle_tete", (7, 24, 12.75), (9, 25.5, 13.5), "metal")
    e("cable", (10, 20, 12), (13, 23, 13.5), "cable")
    e("liste", (9.5, 23.5, 13.25), (12.5, 27, 13.5), "papier")
    e("boite", (3, 17, 9), (8, 19.5, 13), "rouille", up="tole")
    # dessus : rebord, voyant, poignées de transport sur les flancs
    e("dessus", (0.5, 30, 3), (15.5, 31, 15.75), "tole")
    e("voyant_socle", (11.5, 31, 4), (14, 31.5, 6.5), "metal_sombre")
    e("voyant", (12, 31.5, 4.5), (13.5, 32, 6), "lampe")
    for x0, x1 in ((0.25, 1), (15, 15.75)):
        e("anse_flanc", (x0, 22, 8), (x1, 23, 11), "metal_sombre")
    # renforts d'angle (silhouette) et rivets
    for x0, x1 in ((0.75, 1.25), (14.75, 15.25)):
        e("renfort", (x0, 2, 3.75), (x1, 30, 4.25), "metal")
    for y in (4, 12, 20, 26):
        for x in (1.25, 14.25):
            e("rivet", (x, y, 3.25), (x + .5, y + .5, 3.75), "rivet")
    return E


FACES = {  # largeur et hauteur (en unités) de chaque face d'une boîte
    "north": lambda a, b: (b[0] - a[0], b[1] - a[1]), "south": lambda a, b: (b[0] - a[0], b[1] - a[1]),
    "east": lambda a, b: (b[2] - a[2], b[1] - a[1]), "west": lambda a, b: (b[2] - a[2], b[1] - a[1]),
    "up": lambda a, b: (b[0] - a[0], b[2] - a[2]), "down": lambda a, b: (b[0] - a[0], b[2] - a[2]),
}


# plan de chaque face : (axe, côté de la boîte, sens) ; deux faces du même plan et du même sens qui se recouvrent
# scintillent en jeu (« z-fighting ») : la seconde est rentrée de RETRAIT dans sa pièce
PLANS = {"north": (2, 0), "south": (2, 1), "west": (0, 0), "east": (0, 1), "down": (1, 0), "up": (1, 1)}
RETRAIT = 0.1
GOUTTIERE = 1         # pixels recopiés autour de chaque face dans la texture (pas de bavure au loin, mipmaps)


def recouvre(a1, b1, a2, b2, axe):
    """Les deux rectangles (boîtes vues le long de l'axe) se recouvrent avec une aire non nulle."""
    autres = [i for i in range(3) if i != axe]
    return all(min(b1[i], b2[i]) - max(a1[i], a2[i]) > 1e-6 for i in autres)


def desenchevetrer(liste):
    """Rentre les faces qui en doublent une autre (même plan, même sens) ; rend le nombre de faces déplacées."""
    n = 0
    for _ in range(20):
        change = False
        for i, (nom, a, b, mat, faces) in enumerate(liste):
            for f, (axe, cote) in PLANS.items():
                plan = (b if cote else a)[axe]
                for j in range(i):
                    a2, b2 = liste[j][1], liste[j][2]
                    if abs((b2 if cote else a2)[axe] - plan) < 1e-6 and recouvre(a, b, a2, b2, axe):
                        if cote:
                            b[axe] -= RETRAIT
                        else:
                            a[axe] += RETRAIT
                        n += 1
                        change = True
                        break
        if not change:
            return n
    raise SystemExit("faces en double impossibles à séparer")


def doublons(liste):
    """Faces encore en double (contrôle)."""
    out = []
    for i, (nom, a, b, _, _) in enumerate(liste):
        for f, (axe, cote) in PLANS.items():
            for j in range(i):
                a2, b2 = liste[j][1], liste[j][2]
                if abs((b2 if cote else a2)[axe] - (b if cote else a)[axe]) < 1e-6 and recouvre(a, b, a2, b2, axe):
                    out.append((nom, liste[j][0], f))
    return out


def construire():
    img = Image.new("RGBA", (TAILLE, TAILLE), (0, 0, 0, 0))
    # rangement des faces dans la texture, en lignes
    x = y = haut_ligne = 0
    modele = {"credit": "HeraliZ, mods/crac/outils/tc_modele.py", "texture_size": [TAILLE, TAILLE],
              "textures": {"0": TEXTURE, "particle": TEXTURE}, "elements": [], "display": AFFICHAGE}
    porte = None
    liste = [(nom, list(a), list(b), mat, faces) for nom, a, b, mat, faces in elements()]
    desenchevetrer(liste)
    if doublons(liste):
        raise SystemExit(f"faces en double : {doublons(liste)}")
    g = GOUTTIERE
    for nom, a, b, mat, faces in liste:
        a = [round(v, 4) for v in a]
        b = [round(v, 4) for v in b]
        el = {"name": nom, "from": a, "to": b, "faces": {}}
        for f, taille in FACES.items():
            w = max(1, round(taille(a, b)[0] * DENSITE))
            h = max(1, round(taille(a, b)[1] * DENSITE))
            if x + w + 2 * g > TAILLE:
                x, y, haut_ligne = 0, y + haut_ligne, 0
            if y + h + 2 * g > TAILLE:
                raise SystemExit("texture trop petite")
            m = faces.get(f, mat)
            peint(img, x + g, y + g, w, h, m)
            gouttiere(img, x + g, y + g, w, h, g)
            if nom == "porte" and f == "north":
                porte = (x + g, y + g, w, h)
            k = 16 / TAILLE
            u0, v0 = x + g, y + g
            el["faces"][f] = {"uv": [round(u0 * k, 4), round(v0 * k, 4), round((u0 + w) * k, 4), round((v0 + h) * k, 4)], "texture": "#0"}
            x += w + 2 * g
            haut_ligne = max(haut_ligne, h + 2 * g)
        modele["elements"].append(el)
    if porte:
        pochoir(img, porte[0] + 4, porte[1] + porte[3] - 10, "TC-07")
        gouttiere(img, porte[0], porte[1], porte[2], porte[3], GOUTTIERE)
    return modele, img


def gouttiere(img, x, y, w, h, g):
    """Recopie les pixels du bord d'une face tout autour (g pixels) : au loin, les mipmaps ne mélangent plus deux faces."""
    px = img.load()
    for k in range(1, g + 1):
        for i in range(w):
            px[x + i, y - k] = px[x + i, y]
            px[x + i, y + h - 1 + k] = px[x + i, y + h - 1]
        for j in range(-k, h + k):
            jj = min(max(j, 0), h - 1)
            px[x - k, y + j] = px[x, y + jj]
            px[x + w - 1 + k, y + j] = px[x + w - 1, y + jj]


def ecrire(chemin, donnees):
    chemin.parent.mkdir(parents=True, exist_ok=True)
    chemin.write_text(json.dumps(donnees, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def installer():
    modele, img = construire()
    ecrire(ASSETS / "models/block/claim_block.json", modele)
    img.save(ASSETS / "textures/block/claim_block.png")
    ecrire(ASSETS / "models/block/claim_block_haut.json", {"credit": modele["credit"], "textures": {"particle": TEXTURE}})
    ecrire(ASSETS / "models/item/claim_block.json", {"parent": "crac:block/claim_block"})
    # états (sens, tier, adossement) et modèles poussés contre les murs : outils/adossement.py
    import adossement
    adossement.armoire(modele)
    for a in ANCIENS:
        (ASSETS / a).unlink(missing_ok=True)
    print(f"[tc_modele] {len(modele['elements'])} éléments installés dans {ASSETS}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--apercu", help="dossier où écrire tc_casier.json et tc_casier.png")
    g.add_argument("--installer", action="store_true", help="écrit le modèle dans le mod")
    args = ap.parse_args()
    if args.installer:
        installer()
        return
    out = Path(args.apercu)
    out.mkdir(parents=True, exist_ok=True)
    modele, img = construire()
    (out / "tc_casier.json").write_text(json.dumps(modele, indent=1), encoding="utf-8")
    img.save(out / "tc_casier.png")
    print(f"[tc_modele] {len(modele['elements'])} éléments, texture {TAILLE}×{TAILLE} -> {out}")


if __name__ == "__main__":
    main()
