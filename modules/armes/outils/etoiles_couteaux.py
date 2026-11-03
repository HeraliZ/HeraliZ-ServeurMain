#!/usr/bin/env python3
"""Étoiles du rang des skins de couteaux dans l'infobulle (03/11, proposition A validée par le propriétaire) : étoile
pleine en or (dégradé, liseré sombre), étoile vide en contour gris. Remplacent les « ★ » en points de la police de
Minecraft ; dessinées par fr.heraliz.tactique.client.couteaux.EtoilesRang (9 x 9 points d'interface chacune, 1 point
d'écart), texture lue lissée (« blur » dans son .mcmeta) pour rester nette aux échelles 2, 3 et 4.

Écrit assets/heraliz_tactique/textures/gui/etoiles_rang.png (deux cases de TAILLE x TAILLE : pleine, vide) et son
.mcmeta. Jamais à la main : changer ce script, puis le relancer.

    python modules/armes/outils/etoiles_couteaux.py --installer
    python modules/armes/outils/etoiles_couteaux.py --apercu DOSSIER
"""
import argparse
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

RACINE = Path(__file__).resolve().parents[1]
SORTIE = RACINE / "src/client/resources/assets/heraliz_tactique/textures/gui/etoiles_rang.png"
# 36 points par étoile : 1 point de texture par point d'écran à l'échelle 4 (9 x 4), 2 pour 1 à l'échelle 2
TAILLE = 36
SUR = 8  # dessin 8 fois plus grand, puis réduit (Lanczos)
OR_HAUT, OR_BAS, OR_BORD = (255, 222, 120, 255), (232, 160, 34, 255), (110, 62, 0, 230)
VIDE = (150, 156, 168, 255)


def etoile(cx, cy, R, r, rot=-90):
    pts = []
    for n in range(10):
        a = math.radians(rot + n * 36)
        rr = R if n % 2 == 0 else r
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return pts


def dessiner():
    k = SUR
    t = TAILLE * k
    grand = Image.new("RGBA", (2 * t, t), (0, 0, 0, 0))
    d = ImageDraw.Draw(grand, "RGBA")
    pt = TAILLE / 9.0  # points de texture par point d'interface
    R = t / 2 - 0.6 * k * pt  # marge pour le liseré
    cx, cy = t / 2, t / 2 + 0.3 * pt * k
    # pleine : or foncé, moitié haute plus claire (reflet), liseré sombre
    pleine = etoile(cx, cy, R, R * 0.47)
    d.polygon(pleine, fill=OR_BAS)
    d.polygon(etoile(cx, cy - R * 0.08, R * 0.82, R * 0.82 * 0.47), fill=OR_HAUT)
    d.line(pleine + [pleine[0]], fill=OR_BORD, width=max(1, round(0.55 * pt * k / 2)), joint="curve")
    # vide : contour gris
    vide = etoile(t + cx, cy, R, R * 0.47)
    d.line(vide + [vide[0]], fill=VIDE, width=max(1, round(0.9 * pt * k / 2)), joint="curve")
    return grand.resize((2 * TAILLE, TAILLE), Image.LANCZOS)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--installer", action="store_true")
    p.add_argument("--apercu")
    a = p.parse_args()
    img = dessiner()
    if a.apercu:
        dossier = Path(a.apercu)
        dossier.mkdir(parents=True, exist_ok=True)
        fond = Image.new("RGBA", (img.width * 4, img.height * 4), (16, 0, 16, 255))
        fond.alpha_composite(img.resize((img.width * 4, img.height * 4), Image.NEAREST))
        fond.save(dossier / "etoiles_rang_x4.png")
        print(dossier / "etoiles_rang_x4.png")
    if a.installer:
        SORTIE.parent.mkdir(parents=True, exist_ok=True)
        img.save(SORTIE, optimize=True)
        meta = SORTIE.with_name(SORTIE.name + ".mcmeta")
        meta.write_text(json.dumps({"texture": {"blur": True, "clamp": True}}, indent=2) + "\n", encoding="utf-8")
        print("[etoiles_couteaux] %s (%d x %d)" % (SORTIE.relative_to(RACINE.parents[1]), img.width, img.height))
    if not (a.apercu or a.installer):
        p.error("--installer ou --apercu DOSSIER")


if __name__ == "__main__":
    main()
