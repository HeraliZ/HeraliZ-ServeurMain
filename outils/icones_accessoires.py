#!/usr/bin/env python3
"""Icônes « carte » des accessoires TaCZ : la carte prend la couleur de la rareté de l'accessoire dans le jeu.

    python outils/icones_accessoires.py                    recolore les cartes qui ne sont pas de la bonne couleur
    python outils/icones_accessoires.py --verifier         contrôle seulement (code de sortie 1 si une carte n'est pas
                                                           à jour) ; outils/verifier.py le lance
    python outils/icones_accessoires.py --apercu DOSSIER   planche avant / après dans DOSSIER, rien n'est changé

Besoin de Pillow et numpy (pip install pillow numpy).

Les chargeurs, les munitions spéciales et les crosses d'usine de TaCZ (et ceux des gunpacks W Mags et Max Stuff, faits
sur le même modèle) ont une icône en forme de carte : un fond gris foncé qui se fond vers le bas dans une couleur, un
liseré d'un pixel qui part du blanc en haut vers la même couleur en bas, et l'objet dessiné par-dessus. Cette couleur
suit les niveaux de TaCZ (gris, bleu, violet, jaune), pas nos raretés : un chargeur tambour Peu commun avait une carte
violette, comme un objet Épique. Et la carte, pleine, cache la couleur de rareté que le jeu pose derrière l'objet dans
les cases (loot, RaretesClient) : seule la carte la montrait, avec la mauvaise couleur.

Ici chaque carte prend la couleur de la rareté de son accessoire (section « accessoires » de
serveur/config/heraliz_loot/raretes.json, « defaut » sinon ; couleurs lues dans Rarete.java du module loot, les mêmes
que le fond des cases et l'étoile de l'infobulle) : le dégradé du bas du fond va jusqu'à cette couleur un peu assombrie
(FOND_BAS), le liseré va du blanc à cette couleur. Le haut gris foncé et le dessin de l'objet ne changent pas.

Une carte est reconnue à sa forme, jamais par une liste : liseré opaque tout autour, clair en haut ; première ligne du
fond d'un gris foncé uni ; dernière ligne du fond d'une autre couleur. Le dégradé des cartes de TaCZ est fixe (gris
foncé jusqu'à la ligne 15 sur 32, puis droit jusqu'à la couleur à la ligne 30) : un pixel est du fond s'il a la couleur
du dégradé d'origine à sa ligne (à TOLERANCE près), sinon il appartient au dessin. Relancer ne change rien à une carte
déjà à la bonne couleur. Une image partagée par des accessoires de raretés différentes est laissée telle quelle et
signalée.

À relancer après un changement de rareté d'un accessoire (outils/equilibrage_armes.py) ou l'ajout d'un gunpack. Une
rareté changée en jeu (/rarete : raretes_jeu.json du serveur) n'est pas suivie : reporter le choix dans raretes.json.
Les icônes sont des fichiers des joueurs : il faut leur redonner le dossier tacz.
"""
import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from silencieux import lire  # noqa: E402  (lecture tolérante : commentaires, virgules finales)

DEPOT = Path(__file__).resolve().parents[1]
TACZ = DEPOT / "serveur" / "tacz"
RARETES = DEPOT / "serveur" / "config" / "heraliz_loot" / "raretes.json"
RARETE_JAVA = DEPOT / "modules" / "loot" / "src" / "commun" / "java" / "fr" / "heraliz" / "loot" / "Rarete.java"

DEBUT, FIN = 15, 30       # dégradé du fond (lignes d'une carte de 32 px) : uni jusqu'à DEBUT, couleur pleine à FIN
TOLERANCE = 6             # écart (par canal) au dégradé d'origine encore compté comme fond
FOND_BAS = 0.8            # couleur du bas du fond = couleur de la rareté x FOND_BAS (le dessin blanc reste lisible)
GRIS_MAX, GRIS_ECART = 80, 6   # haut du fond : gris foncé (canaux sous GRIS_MAX, écart entre eux au plus GRIS_ECART)


def couleurs_raretes():
    """{rareté: (r, g, b)} lues dans Rarete.java (une seule vérité pour les couleurs du jeu)."""
    texte = RARETE_JAVA.read_text(encoding="utf-8")
    out = {}
    for ident, hexa in re.findall(r'[A-Z_]+\("(\w+)",\s*"[^"]*",\s*0x([0-9A-Fa-f]{6}),', texte):
        v = int(hexa, 16)
        out[ident] = ((v >> 16) & 255, (v >> 8) & 255, v & 255)
    if len(out) < 6:
        sys.exit("[icones] couleurs des raretés illisibles dans %s" % RARETE_JAVA.relative_to(DEPOT))
    return out


def icones_des_accessoires():
    """{image slot (Path): [identifiants des accessoires qui l'utilisent]}."""
    affichages = {}
    for f in TACZ.glob("*/assets/*/display/attachments/*.json"):
        affichages.setdefault("%s:%s" % (f.parts[-4], f.stem), f)
    out = defaultdict(list)
    for f in sorted(TACZ.glob("*/data/*/index/attachments/*.json")):
        ident = "%s:%s" % (f.parts[-4], f.stem)
        try:
            idx = lire(f)
            aff = lire(affichages[idx["display"]])
        except (KeyError, ValueError, OSError):
            continue
        slot = aff.get("slot") or ""
        if ":" not in slot:
            continue
        ns, chemin = slot.split(":", 1)
        for png in sorted(TACZ.glob("*/assets/%s/textures/%s.png" % (ns, chemin))):
            out[png].append(ident)
    return out


def mode(pixels):
    """Couleur (r, g, b) la plus fréquente d'une rangée de pixels, et sa part."""
    c = Counter(map(tuple, pixels[:, :3]))
    coul, n = c.most_common(1)[0]
    return np.array(coul, dtype=float), n / len(pixels)


def carte(img):
    """(haut du fond, bas du fond) si l'image est une carte, sinon None. img : tableau RGBA 32 x 32."""
    if img.shape[:2] != (32, 32):
        return None
    anneau = np.concatenate([img[0], img[-1], img[:, 0], img[:, -1]])
    if anneau[:, 3].min() < 250 or img[0, :, :3].mean() < 200:
        return None
    haut, part = mode(img[1, 1:-1])
    if part < 0.25 or haut.max() >= GRIS_MAX or haut.max() - haut.min() > GRIS_ECART:
        return None
    bas, part = mode(img[FIN, 1:-1])
    if part < 0.25 or np.abs(bas - haut).sum() < 40:
        return None
    return haut, bas


def degrade(haut, bas, y):
    t = min(1.0, max(0.0, (y - DEBUT) / float(FIN - DEBUT)))
    return haut + t * (bas - haut)


def recolorer(img, couleur):
    """Copie de la carte img (RGBA, uint8) à la couleur donnée, ou None si ce n'est pas une carte.

    Carte colorée (bas du fond d'une couleur franche) : chaque pixel du fond ET du dessin qui est un mélange d'un gris et
    de la couleur d'origine (gris + a x couleur, à TOLERANCE près : le dégradé, et les parties du dessin que l'auteur a
    teintées de la couleur de la carte, comme la fenêtre des chargeurs W Mags) garde son gris et sa part a, avec la
    nouvelle couleur. Carte grise : seulement le fond (couleur du dégradé d'origine à sa ligne), un gris ne disant pas
    ce qui vient de la carte. Liseré redessiné du blanc à la couleur de la rareté. Un pixel ne change que s'il bouge de
    plus d'un point : relancer ne change rien."""
    c = carte(img)
    if c is None:
        return None
    haut, bas = c
    neuf_bas = np.array(couleur, dtype=float) * FOND_BAS
    cible = img[..., :3].astype(float).copy()
    interieur = cible[1:31, 1:31]
    if np.abs(bas - np.rint(neuf_bas)).max() <= 2:
        pass                                                          # fond déjà à la couleur : dessin intact
    elif bas.max() - bas.min() > 30:
        base = np.stack([np.ones(3), bas], axis=1)                    # 3 x 2 : gris, couleur d'origine
        pix = interieur.reshape(-1, 3).T                              # 3 x n
        u, a = np.linalg.pinv(base) @ pix
        reste = np.abs(base @ np.stack([u, a]) - pix).max(axis=0)
        melange = (reste <= TOLERANCE) & (a > 0.02) & (a < 1.25)
        neuf = (np.outer(np.ones(3), u) + np.outer(neuf_bas, a)).T
        plat = interieur.reshape(-1, 3)
        plat[melange] = neuf[melange]
        interieur[:] = plat.reshape(interieur.shape)
    else:
        for y in range(1, 31):
            ligne = cible[y, 1:31]
            fond = (np.abs(ligne - degrade(haut, bas, y)) <= TOLERANCE).all(axis=1)
            ligne[fond] = degrade(haut, neuf_bas, y)
    # liseré : du blanc (ligne du haut) à la couleur de la rareté (ligne du bas)
    blanc = np.array([255.0, 255.0, 255.0])
    coul = np.array(couleur, dtype=float)
    for y in range(32):
        v = blanc + (y / 31.0) * (coul - blanc)
        if y in (0, 31):
            cible[y, :] = v
        else:
            cible[y, 0] = v
            cible[y, -1] = v
    cible = np.clip(np.rint(cible), 0, 255)
    out = img.copy()
    bouge = np.abs(cible - img[..., :3].astype(float)).max(axis=2) > 1
    out[..., :3][bouge] = cible[bouge].astype(np.uint8)
    return out


def calculer():
    """[(image, accessoires, rareté, avant, après ou None, raison)] pour chaque image d'accessoire qui est une carte."""
    raretes = json.loads(RARETES.read_text(encoding="utf-8"))
    par_acc, defaut = raretes.get("accessoires", {}), raretes.get("defaut", "commun")
    couleurs = couleurs_raretes()
    out = []
    for png, ids in sorted(icones_des_accessoires().items()):
        img = np.asarray(Image.open(png).convert("RGBA"), dtype=np.uint8)
        if carte(img) is None:
            continue
        rs = sorted({par_acc.get(i, defaut) for i in ids})
        if len(rs) != 1 or rs[0] not in couleurs:
            out.append((png, ids, "/".join(rs), img, None, "raretés différentes ou inconnues : %s" % ", ".join(rs)))
            continue
        out.append((png, ids, rs[0], img, recolorer(img, couleurs[rs[0]]), ""))
    return out


def police(taille):
    from PIL import ImageFont
    for f in ("DejaVuSans.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(f, taille)
        except OSError:
            pass
    return ImageFont.load_default()


def planche(lignes, sortie):
    """Avant / après sur une case d'inventaire (64 px et 32 px), comme dans le jeu."""
    col = 4
    L, H = 300, 150
    rangs = (len(lignes) + col - 1) // col
    fond = Image.new("RGBA", (col * L, rangs * H), (40, 42, 48, 255))
    dr = ImageDraw.Draw(fond)
    p = police(12)
    for i, (png, ids, rarete, avant, apres, _) in enumerate(lignes):
        x, y = (i % col) * L, (i // col) * H
        dr.text((x + 6, y + 4), "%s (%s)" % (ids[0].split(":")[1], rarete), fill=(220, 220, 220, 255), font=p)
        for j, im in enumerate((avant, apres)):
            if im is None:
                continue
            bx = x + 6 + j * 150
            for taille, dy in ((64, 22), (32, 92)):
                dr.rectangle([bx, y + dy, bx + taille - 1, y + dy + taille - 1], fill=(139, 139, 139, 255))
                fond.alpha_composite(Image.fromarray(im, "RGBA").resize((taille, taille), Image.NEAREST), (bx, y + dy))
        dr.text((x + 76, y + 126), "avant", fill=(150, 150, 150, 255), font=p)
        dr.text((x + 226, y + 126), "après", fill=(150, 150, 150, 255), font=p)
    fond.convert("RGB").save(sortie)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--verifier", action="store_true")
    p.add_argument("--apercu")
    a = p.parse_args()
    lignes = calculer()
    a_changer, problemes = [], 0
    for png, ids, rarete, avant, apres, raison in lignes:
        rel = png.relative_to(TACZ)
        if apres is None:
            print("[icones] %s : laissée telle quelle (%s)" % (rel, raison))
            problemes += 1
            continue
        if not np.array_equal(avant, apres):
            a_changer.append((png, apres))
            print("[icones] %s : carte à la couleur %s (%s)" % (rel, rarete, ", ".join(ids)))
    if a.apercu:
        sortie = Path(a.apercu)
        sortie.mkdir(parents=True, exist_ok=True)
        planche(lignes, sortie / "icones_accessoires.png")
        print("[icones] planche :", sortie / "icones_accessoires.png")
        return
    if a.verifier:
        print("[icones] %d carte(s) d'accessoire, %d pas à jour, %d problème(s)" % (len(lignes), len(a_changer), problemes))
        sys.exit(1 if a_changer or problemes else 0)
    for png, apres in a_changer:
        Image.fromarray(apres, "RGBA").save(png, optimize=True)
    print("[icones] %d carte(s) d'accessoire, %d recolorée(s), %d problème(s)" % (len(lignes), len(a_changer), problemes))
    sys.exit(1 if problemes else 0)


if __name__ == "__main__":
    main()
