#!/usr/bin/env python3
"""Accessoires TaCZ tenus en main (première personne) qui sortaient de l'écran : silencieux, bipieds, crosses longues.

    python modules/armes/outils/accessoires_en_main.py                  réécrit la table du jeu
    python modules/armes/outils/accessoires_en_main.py --verifier       contrôle seulement (code de sortie 1 si la table
                                                                        n'est plus à jour) ; outils/verifier.py le lance
    python modules/armes/outils/accessoires_en_main.py --apercu DOSSIER [--ids a,b]
                                                                        vues de l'écran avant / après (rien n'est changé)

Besoin de numpy (et de Pillow pour --apercu).

Comment un accessoire tenu en main est dessiné (lu dans le code de Minecraft 1.20.1 et de TaCZ 1.1.8) :
  - ItemInHandRenderer pose l'objet à (0,56 ; -0,52 ; -0,72) bloc de l'oeil (applyItemArmTransform), puis applique la
    transformation « firstperson_righthand » du modèle d'objet tacz:item/attachment (rotation 0, -90, 25 ; translation
    1,13, 3,2, 1,13 ; échelle 0,68 : celle des objets plats de Minecraft), lue ici dans le jar de TaCZ ;
  - AttachmentItemRenderer dessine le modèle Bedrock de l'accessoire avec son origine au centre de l'objet. Cette origine
    est le point où l'accessoire se fixe sur l'arme (os « <type>_pos ») : l'arrière d'un silencieux, le haut d'une
    poignée. Un silencieux, long de 8 à 20 px vers l'avant, part donc vers la droite de l'écran et son bout sort de
    l'image ; le bipied X48 (gucci_attachments:grip_bipod), dessiné 8 à 12 px devant son point de fixation, tombait
    entièrement hors de l'écran : invisible en main ;
  - la main est dessinée avec un champ de vision de 70° (GameRenderer.getFov sans le réglage du joueur), écran de
    référence 16:9.

Règle : un accessoire (bouche, poignée, crosse, laser ; les viseurs ont leur propre dessin en main) dont le modèle, dessiné
ainsi, sort par le côté de l'écran (à droite, à gauche pour la main gauche) est tenu par le milieu de son modèle et
réduit juste assez pour tenir à MARGE de chaque bord. Les autres ne bougent pas, même coupés en bas de l'écran comme tout
objet tenu de Minecraft. Rien ne change sur l'arme, au sol, dans un cadre ni vu des autres joueurs.
Le modèle est lu comme TaCZ le construit (outils/verif_accessoires.py : mêmes os, mêmes cubes ; réticules, lentilles,
faisceaux de laser et cubes transparents ne comptent pas).

Table écrite : modules/armes/src/client/resources/assets/heraliz_tactique/accessoires_en_main.json (jamais à la main),
lue par fr.heraliz.tactique.client.accessoires.AccessoiresEnMain. À relancer après l'ajout ou la mise à jour d'un
gunpack (outils/verifier.py le réclame), puis refabriquer le module armes (jar des joueurs).
"""
import argparse
import json
import math
import sys
import zipfile
from pathlib import Path

import numpy as np

ICI = Path(__file__).resolve().parent
DEPOT = ICI.parents[2]
sys.path.insert(0, str(DEPOT / "outils"))
import verif_accessoires as va  # noqa: E402

TABLE = DEPOT / "modules" / "armes" / "src" / "client" / "resources" / "assets" / "heraliz_tactique" / "accessoires_en_main.json"
JARS_TACZ = ("serveur/mods-tiers/tacz-1.20.1-1.1.8-hotfix.jar", "libs/tacz-1.20.1-1.1.8-hotfix.jar")
MODELE_OBJET = "assets/tacz/models/item/attachment.json"
TYPES = ("muzzle", "grip", "stock", "laser")
BRAS = np.array([0.56, -0.52, -0.72])     # ItemInHandRenderer.applyItemArmTransform (main droite, au repos)
CHAMP = 70.0                              # degrés, vertical (GameRenderer.getFov(..., false))
ECRAN = 16 / 9
MARGE = 0.95                              # l'accessoire réduit tient dans 95 % de l'écran (coordonnées -1..1)
TEXTE_AIDE = ("Écrit par modules/armes/outils/accessoires_en_main.py (jamais à la main). Accessoires TaCZ qui sortaient "
              "de l'écran tenus en main (première personne) : [x, y, z, échelle], milieu du modèle en pixels dans le "
              "repère de l'objet tenu (origine au centre de l'objet ; x = -x du modèle Bedrock, y et z du modèle), et "
              "échelle qui le fait tenir à l'écran. Lu par AccessoiresEnMain (heraliz_tactique).")


def transformation_main():
    """(rotation, translation, échelle) « firstperson_righthand » du modèle d'objet des accessoires de TaCZ."""
    for rel in JARS_TACZ:
        jar = DEPOT / rel
        if jar.is_file():
            with zipfile.ZipFile(jar) as z:
                d = json.loads(z.read(MODELE_OBJET).decode("utf-8"))["display"]["firstperson_righthand"]
            return (d.get("rotation", [0, 0, 0]), d.get("translation", [0, 0, 0]), d.get("scale", [1, 1, 1]))
    sys.exit("[en_main] jar de TaCZ introuvable (%s)" % ", ".join(JARS_TACZ))


def _r(axe, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axe == 0:
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axe == 1:
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


class Ecran:
    """Repère de l'objet tenu (pixels, origine au centre de l'objet) -> coordonnées d'écran (-1..1)."""

    def __init__(self, transfo):
        rot, tr, sc = transfo
        # ItemTransform.apply : translation (/16), rotationXYZ (X puis Y puis Z appliqués à l'objet : Rx Ry Rz), échelle
        self.R = _r(0, rot[0]) @ _r(1, rot[1]) @ _r(2, rot[2]) @ np.diag(np.asarray(sc, float))
        self.T = np.asarray(tr, float) / 16.0 + BRAS
        self.t = math.tan(math.radians(CHAMP) / 2)

    def projeter(self, p_px):
        q = (self.R @ (np.asarray(p_px, float) / 16.0).T).T + self.T
        z = -q[:, 2]
        return q[:, 0] / z / (self.t * ECRAN), q[:, 1] / z / self.t, z

    def tient(self, p_px, marge):
        x, y, z = self.projeter(p_px)
        return bool((z > 0.05).all() and np.abs(x).max() <= marge and np.abs(y).max() <= marge)


def points_objet(boites):
    """Coins des cubes du modèle dans le repère de l'objet tenu (pixels) : TaCZ dessine le modèle retourné (x et y
    inversés) avec le haut du modèle (y Bedrock 24) au centre de l'objet."""
    c = boites.coins().reshape(-1, 3)
    return np.stack([-c[:, 0], 24.0 - c[:, 1], c[:, 2]], axis=1)


def echelle(ecran, pts, centre):
    """Plus grande échelle (au plus 1) qui fait tenir le modèle centré dans MARGE de l'écran."""
    rel = pts - centre
    if ecran.tient(rel, MARGE):
        return 1.0
    bas, haut = 0.0, 1.0
    for _ in range(40):
        m = (bas + haut) / 2
        if ecran.tient(rel * m, MARGE):
            bas = m
        else:
            haut = m
    return bas


def _r3(v):
    """Arrondi au millième, sans « -0.0 » (même texte de table d'une machine à l'autre)."""
    return round(float(v), 3) + 0.0


def calculer():
    """{accessoire: (type, x, y, z, échelle, (x min, x max, y min, y max) à l'écran avant)} des accessoires qui sortaient
    de l'écran."""
    ecran = Ecran(transformation_main())
    cat = va.Catalogue()
    out = {}
    for ident in sorted(cat.gp.type_acc):
        typ = cat.gp.type_acc[ident]
        if typ not in TYPES:
            continue
        _, modele, boites = cat.accessoire(ident)
        if modele is None or boites is None or not len(boites):
            continue
        pts = points_objet(boites)
        x, y, _ = ecran.projeter(pts)
        if np.abs(x).max() <= 1.0:
            continue          # tient en largeur (coupé en bas au plus, comme les objets tenus de Minecraft) : inchangé
        centre = (pts.min(axis=0) + pts.max(axis=0)) / 2
        s = math.floor(echelle(ecran, pts, centre) * 1000) / 1000      # arrondi vers le bas : tient toujours
        out[ident] = (typ, _r3(centre[0]), _r3(centre[1]), _r3(centre[2]), _r3(s),
                      (float(x.min()), float(x.max()), float(y.min()), float(y.max())))
    return out


def contenu(res):
    """Texte de la table : une ligne par accessoire."""
    lignes = ['  %s: %s' % (json.dumps(k), json.dumps([v[1], v[2], v[3], v[4]])) for k, v in sorted(res.items())]
    return ('{\n "_aide": %s,\n "accessoires": {\n%s\n }\n}\n'
            % (json.dumps(TEXTE_AIDE, ensure_ascii=False), ",\n".join(lignes)))


def apercu(dossier, res, ids):
    """Vue de l'écran (16:9) avant / après, faces de la couleur moyenne de la texture (outils/verif_accessoires.py)."""
    from PIL import Image, ImageDraw
    ecran = Ecran(transformation_main())
    cat = va.Catalogue()
    W, H = 480, 270
    noms = ids or sorted(res)
    planche = Image.new("RGB", (2 * W + 30, len(noms) * (H + 26) + 6), (34, 34, 38))
    dr = ImageDraw.Draw(planche)
    police = va.police(13)
    for i, ident in enumerate(noms):
        aff, modele, boites = cat.accessoire(ident)
        if modele is None or boites is None:
            continue
        tex = cat.texture(aff.get("texture"), modele)
        pts = points_objet(boites)
        r = res.get(ident)
        centre = np.array(r[1:4]) if r else np.zeros(3)
        s = r[4] if r else 1.0
        for j, (dec, ech) in enumerate(((np.zeros(3), 1.0), (centre, s))):
            im = Image.new("RGB", (W, H), (120, 165, 235))
            d = ImageDraw.Draw(im)
            d.rectangle([0, H * 0.55, W, H], fill=(80, 120, 55))
            d.line([(W / 2 - 5, H / 2), (W / 2 + 5, H / 2)], fill=(255, 255, 255))
            d.line([(W / 2, H / 2 - 5), (W / 2, H / 2 + 5)], fill=(255, 255, 255))
            faces = []
            coins = boites.coins()
            for k in range(len(boites)):
                c = coins[k]
                cobj = np.stack([-c[:, 0], 24.0 - c[:, 1], c[:, 2]], axis=1)
                x, y, z = ecran.projeter((cobj - dec) * ech)
                coul = tex.faces(boites.uv[k]) if tex is not None else [None] * 6
                for f, (a0, a1, a2, a3, axe, sens) in enumerate(va.FACES):
                    cc = coul[f] if f < len(coul) and coul[f] else (90, 90, 95)
                    ombre = 1.0 if (axe, sens) == (1, -1) else 0.8 if axe == 0 else 0.65
                    idx = (a0, a1, a2, a3)
                    faces.append((float(np.mean(z[list(idx)])), [((x[n] + 1) / 2 * W, (1 - y[n]) / 2 * H) for n in idx],
                                  tuple(int(v * ombre) for v in cc[:3])))
            for _, poly, cc in sorted(faces, key=lambda f: -f[0]):
                d.polygon(poly, fill=cc, outline=(30, 30, 30))
            y0 = 6 + i * (H + 26)
            planche.paste(im, (10 + j * (W + 10), y0 + 20))
            dr.text((10 + j * (W + 10), y0 + 2), "%s : %s" % (ident, "avant" if j == 0 else "après (échelle %.2f)" % s),
                    fill=(230, 230, 230), font=police)
    Path(dossier).mkdir(parents=True, exist_ok=True)
    sortie = Path(dossier) / "accessoires_en_main.png"
    planche.save(sortie)
    print("[en_main] planche :", sortie)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--verifier", action="store_true")
    p.add_argument("--apercu")
    p.add_argument("--ids", help="accessoires de l'aperçu, séparés par des virgules (défaut : ceux de la table)")
    a = p.parse_args()
    res = calculer()
    for k, v in sorted(res.items()):
        print("[en_main] %-40s %-6s sortait de l'écran (x %+.2f..%+.2f, y %+.2f..%+.2f) : tenu par son milieu, "
              "échelle %.2f" % (k, v[0], v[5][0], v[5][1], v[5][2], v[5][3], v[4]))
    if a.apercu:
        apercu(a.apercu, res, a.ids.split(",") if a.ids else None)
        return
    texte = contenu(res)
    actuel = TABLE.read_text(encoding="utf-8") if TABLE.is_file() else ""
    if a.verifier:
        if actuel != texte:
            print("[en_main] table pas à jour : relancer python modules/armes/outils/accessoires_en_main.py")
            sys.exit(1)
        print("[en_main] table à jour (%d accessoire(s))" % len(res))
        return
    if actuel != texte:
        TABLE.parent.mkdir(parents=True, exist_ok=True)
        TABLE.write_text(texte, encoding="utf-8", newline="\n")
    print("[en_main] %d accessoire(s) dans %s" % (len(res), TABLE.relative_to(DEPOT)))


if __name__ == "__main__":
    main()
