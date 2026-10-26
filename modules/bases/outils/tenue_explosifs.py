#!/usr/bin/env python3
"""
Tenue à deux mains de la C4 et de la charge explosive de CRac (1.12.0).

En première personne, les deux mains du joueur tiennent l'objet devant lui, au milieu ; vu des autres joueurs, les deux
bras sont tendus devant le ventre et l'objet est posé entre les deux mains. Ce script refait par le calcul les mêmes
étapes que Minecraft 1.20.1 (bras du modèle du joueur, ItemInHandLayer, transformations « display » des modèles d'objet,
rendu GeckoLib), place l'objet exactement au milieu des deux mains et écrit :

  - la transformation « thirdperson_righthand » des modèles d'objet (models/item/c4.json, explosive_charge.json) ;
  - les nombres de la première personne dans client/render/TenueExplosifsDonnees.java.

Jamais à la main : on change les réglages en tête du script, puis on relance.

    python modules/bases/outils/tenue_explosifs.py --installer          écrit les deux modèles et la classe Java
    python modules/bases/outils/tenue_explosifs.py --apercu DOSSIER     images de contrôle (1re personne, de face, de côté)
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np

RACINE = Path(__file__).resolve().parents[1]
ASSETS = RACINE / "src" / "client" / "resources" / "assets" / "crac"
JAVA = RACINE / "src" / "client" / "java" / "fr" / "heraliz" / "crac" / "client" / "render" / "TenueExplosifsDonnees.java"

# --------------------------------------------------------------------------------------------- réglages
# Bras vus des autres (radians, comme HumanoidModel) : tendus devant le ventre, rentrés vers le milieu.
BRAS_X = -0.78
BRAS_Y = 0.22
# Où les mains tiennent l'objet, dans le repère du bras (px, cube du bras droit de -3 à 1 en x, -2 à 10 en y) :
# le bas de la paume, côté intérieur.
PAUME = (-0.2, 9.6, -0.6)

# Première personne (repère de la caméra, en blocs ; x à droite, y en haut, -z devant).
FP_CENTRE = (0.0, -0.40, -0.92)       # centre de l'objet
FP_INCLINE = 38.0                    # l'objet basculé vers le joueur (degrés) : on voit le dessus (minuteur de la C4)
FP_LACET = 0.0
FP_TAILLE = 0.50                     # échelle de l'objet
FP_SERRE = 0.6                       # la main entre un peu dans le côté de l'objet (px de main, 4 = toute la main)
FP_MAINS_BAS = 0.35                  # point d'appui : 0 = bas de l'objet, 1 = haut
FP_MAINS_RECUL = 0.15                # point d'appui : 0 = avant de l'objet, 1 = arrière (côté joueur)
# Direction de l'épaule vue de la main, repère de la caméra (pour la main droite ; la gauche en miroir).
FP_VERS_EPAULE = (0.32, -0.8, 0.5)
# Rotations que Minecraft laisse sur le bras en le dessinant (PlayerRenderer.renderHand : setupAnim avec notre pose, puis
# xRot remis à 0) : yRot de la pose et zRot du balancement au repos (AnimationUtils.bobModelPart, âge 0). La main est
# dessinée avec la rotation inverse devant, pour qu'il ne reste que la nôtre.
BOB_Z = 0.1

OBJETS = {
    # nom : (fichier geo, boîte tenue en px du modèle : x0, y0, z0, x1, y1, z1, échelle vue des autres)
    "c4": ("c4", (-5.68, -0.14, -4.16, 5.68, 4.36, 4.2), 0.62),
    "explosive_charge": ("explosive_charge", (-5.15, -0.15, -4.55, 5.15, 4.55, 1.6), 0.6),
}


# --------------------------------------------------------------------------------------------- maths
def rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]])


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]])


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]])


def tr(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def sc(x, y=None, z=None):
    return np.diag([x, x if y is None else y, x if z is None else z, 1.0])


def deg(a):
    return math.radians(a)


def partie(px, py, pz, xr=0.0, yr=0.0, zr=0.0):
    """ModelPart.translateAndRotate : pivot en px, puis rotationZYX(z, y, x)."""
    return tr(px / 16, py / 16, pz / 16) @ rz(zr) @ ry(yr) @ rx(xr)


def euler_xyz(r):
    """Angles (degrés) de Quaternionf.rotationXYZ(x, y, z) = Rx·Ry·Rz donnant la matrice r (3×3)."""
    y = math.asin(max(-1.0, min(1.0, r[0, 2])))
    if abs(math.cos(y)) > 1e-6:
        x = math.atan2(-r[1, 2], r[2, 2])
        z = math.atan2(-r[0, 1], r[0, 0])
    else:
        x = math.atan2(r[2, 1], r[1, 1])
        z = 0.0
    return [round(math.degrees(v), 3) for v in (x, y, z)]


# --------------------------------------------------------------------------------------------- vu des autres
CORPS = tr(0, 0, 0) @ ry(math.pi) @ sc(-1, -1, 1) @ sc(0.9375) @ tr(0, -1.501, 0)


def bras(droit):
    """Repère du bras (vu des autres), avec la pose de tenue (les deux bras symétriques)."""
    if droit:
        return CORPS @ partie(-5, 2, 0, BRAS_X, -BRAS_Y, 0)
    return CORPS @ partie(5, 2, 0, BRAS_X, BRAS_Y, 0)


def paume(droit):
    x, y, z = PAUME
    if not droit:
        x = -x
    return (bras(droit) @ np.array([x / 16, y / 16, z / 16, 1]))[:3]


def avant_display(droit=True):
    """ItemInHandLayer : du bras à la transformation « display » de l'objet."""
    f = -1 if not droit else 1
    return bras(droit) @ rx(deg(-90)) @ ry(math.pi) @ tr(f / 16, 0.125, -0.625)


def troisieme_personne(nom):
    """Transformation « thirdperson_righthand » qui pose le centre de la boîte tenue au milieu des deux mains, à plat,
    dessus vers le haut, longueur en travers du corps."""
    _, boite, echelle = OBJETS[nom]
    cible = (paume(True) + paume(False)) / 2
    pre = avant_display(True)
    # orientation voulue dans le monde : modèle (x, y, z) -> monde (x, y, z), le dessus en haut
    voulu = np.eye(3)
    r = np.linalg.inv(pre[:3, :3] / np.linalg.norm(pre[:3, 0])) @ voulu
    # pre contient l'échelle 0,9375 et des miroirs : on garde la vraie rotation propre
    u, _, vt = np.linalg.svd(r)
    r = u @ vt
    if np.linalg.det(r) < 0:
        r = -r
    centre_px = np.array([-(boite[0] + boite[3]) / 2, (boite[1] + boite[4]) / 2, (boite[2] + boite[5]) / 2])
    centre = centre_px / 16 + np.array([0, 0.01, 0])
    # point = pre · (t + R·s·centre)
    local = np.linalg.inv(pre) @ np.append(cible, 1)
    t = local[:3] - r @ (echelle * centre)
    rot = euler_xyz(r)
    return {"rotation": rot, "translation": [round(v * 16, 3) for v in t], "scale": [echelle] * 3}


def matrice_display(d, gauche=False):
    t = np.array(d["translation"]) / 16
    x, y, z = (deg(v) for v in d["rotation"])
    i = -1 if gauche else 1
    if gauche:
        y, z = -y, -z
    return tr(i * t[0], t[1], t[2]) @ rx(x) @ ry(y) @ rz(z) @ sc(*d["scale"])


# --------------------------------------------------------------------------------------------- première personne
def fp_objet():
    """Repère de l'objet en première personne (avant le -0,5 de ItemRenderer et le +0,5 de GeckoLib, qui s'annulent)."""
    return tr(*FP_CENTRE) @ ry(deg(FP_LACET)) @ rx(deg(FP_INCLINE)) @ sc(FP_TAILLE)


def norm(v):
    v = np.asarray(v, dtype=float)
    return v / np.linalg.norm(v)


def fp_main(droite, boite):
    """Repère de la main en première personne, tel que PlayerRenderer.renderRightHand / renderLeftHand le reçoit : le bout
    du bras (centre de la main) sur le côté de l'objet, paume (face intérieure du bras) contre lui, avant-bras vers
    l'épaule. Rend (point d'appui, angles XYZ en degrés, décalage du pivot en blocs)."""
    f = 1 if droite else -1
    base = fp_objet()
    # côté de l'objet dans le repère du modèle (x du modèle GeckoLib inversé : le côté droit à l'écran est x0 du modèle)
    bord = (-boite[0] if droite else -boite[3]) / 16
    y = (boite[1] + (boite[4] - boite[1]) * FP_MAINS_BAS) / 16
    z = (boite[5] - (boite[5] - boite[2]) * (1 - FP_MAINS_RECUL)) / 16
    cote = (base @ np.array([bord, y, z, 1]))[:3]
    dehors = norm(base[:3, 0] * f)                 # vers l'extérieur de l'objet, dans la caméra
    centre_main = cote + dehors * (2 - FP_SERRE) / 16
    epaule = norm((FP_VERS_EPAULE[0] * f, FP_VERS_EPAULE[1], FP_VERS_EPAULE[2]))
    ay = -epaule                                   # +y du bras : de l'épaule vers la main
    # face intérieure du bras (+x pour le bras droit, -x pour le gauche) tournée vers l'objet
    interieur = -dehors
    ax = interieur * f
    ax = norm(ax - ay * np.dot(ax, ay))
    az = np.cross(ax, ay)
    r = np.column_stack([ax, ay, az])
    # bout du bras dans le repère du modèle : pivot (∓5, 2, 0) + (∓1, 10, 0) px
    pivot = np.array([-6 * f, 12, 0]) / 16
    return centre_main, euler_xyz(r), -pivot


def fp_matrice_main(droite, boite):
    """Matrice posée avant PlayerRenderer.renderRightHand / renderLeftHand (même suite d'opérations que le Java)."""
    p, ang, decal = fp_main(droite, boite)
    f = 1 if droite else -1
    piv = np.array([-5 * f, 2, 0]) / 16
    return (tr(*p) @ rx(deg(ang[0])) @ ry(deg(ang[1])) @ rz(deg(ang[2])) @ tr(*decal) @ tr(*piv)
            @ ry(BRAS_Y * f) @ rz(-BOB_Z * f) @ tr(*-piv))


# --------------------------------------------------------------------------------------------- géométrie pour l'aperçu
def cubes_geo(nom):
    geo = json.loads((ASSETS / "geo" / f"{OBJETS[nom][0]}.geo.json").read_text(encoding="utf-8"))
    out = []
    for b in geo["minecraft:geometry"][0]["bones"]:
        if b["name"].startswith(("tens_", "ones_")) and not b["name"].endswith("_7"):
            continue
        for c in b.get("cubes", []):
            o, s = c["origin"], c["size"]
            x0, x1 = -(o[0] + s[0]) / 16, -o[0] / 16
            out.append(((x0, o[1] / 16, o[2] / 16), (x1, (o[1] + s[1]) / 16, (o[2] + s[2]) / 16)))
    return out


def faces(boite, m, couleur):
    (x0, y0, z0), (x1, y1, z1) = boite
    p = [np.array([x, y, z, 1.0]) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    q = [(m @ v)[:3] for v in p]
    idx = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]
    return [([q[i] for i in f], couleur) for f in idx]


def bras_boite(droit):
    if droit:
        return ((-3 / 16, -2 / 16, -2 / 16), (1 / 16, 10 / 16, 2 / 16))
    return ((-1 / 16, -2 / 16, -2 / 16), (3 / 16, 10 / 16, 2 / 16))


def dessiner(polys, projeter, taille, fichier, titre):
    from PIL import Image, ImageDraw
    img = Image.new("RGB", taille, (34, 38, 44))
    d = ImageDraw.Draw(img)
    rendu = []
    for pts, coul in polys:
        a, b, c = pts[0], pts[1], pts[2]
        n = np.cross(b - a, c - a)
        nn = np.linalg.norm(n)
        lum = 0.55 + 0.45 * abs(n[1] / nn if nn else 0) if nn else 0.6
        proj = [projeter(p) for p in pts]
        if any(p is None for p in proj):
            continue
        prof = sum(p[2] for p in proj) / len(proj)
        rendu.append((prof, [(p[0], p[1]) for p in proj], tuple(int(v * lum) for v in coul)))
    for _, pts, coul in sorted(rendu, key=lambda r: -r[0]):
        d.polygon(pts, fill=coul, outline=(0, 0, 0))
    d.text((8, 8), titre, fill=(230, 230, 230))
    img.save(fichier)


def apercu(dossier):
    dossier = Path(dossier)
    dossier.mkdir(parents=True, exist_ok=True)
    peau, manche, objet = (214, 170, 140), (60, 80, 60), (150, 140, 110)
    for nom, (_, boite, _) in OBJETS.items():
        d3 = troisieme_personne(nom)
        polys = []
        polys += faces(((-4 / 16, 0, -2 / 16), (4 / 16, 12 / 16, 2 / 16)), CORPS, (70, 90, 70))
        polys += faces(((-4 / 16, -8 / 16, -4 / 16), (4 / 16, 0, 4 / 16)), CORPS, peau)
        for dx in (-1.9, 1.9):
            polys += faces(((-2 / 16, 0, -2 / 16), (2 / 16, 12 / 16, 2 / 16)), CORPS @ partie(dx, 12, 0), (60, 60, 70))
        for droit in (True, False):
            polys += faces(bras_boite(droit), bras(droit), manche)
        m = avant_display(True) @ matrice_display(d3) @ tr(0, 0.01, 0)
        for cb in cubes_geo(nom):
            polys += faces(cb, m, objet)
        for vue, f in (("face", lambda p: (300 - p[0] * 220, 520 - p[1] * 220, -p[2])),
                       ("cote", lambda p: (300 + p[2] * 220, 520 - p[1] * 220, -p[0]))):
            dessiner(polys, f, (600, 600), dossier / f"{nom}_{vue}.png", f"{nom} vu des autres ({vue})")
        # première personne
        polys = []
        base = fp_objet() @ tr(0, 0.01, 0)
        for cb in cubes_geo(nom):
            polys += faces(cb, base, objet)
        for droite in (True, False):
            m = fp_matrice_main(droite, boite)
            f = 1 if droite else -1
            mc = partie(-5 * f, 2, 0, 0, -BRAS_Y * f, BOB_Z * f)   # ce que Minecraft applique au bras
            polys += faces(bras_boite(droite), m @ mc, peau)

        def proj(p):
            if p[2] >= -0.05:
                return None
            k = 1 / math.tan(deg(35)) / -p[2]
            return (480 + p[0] * k * 270 * 16 / 9, 270 - p[1] * k * 270, -p[2])
        dessiner(polys, proj, (960, 540), dossier / f"{nom}_1re_personne.png", f"{nom} en 1re personne")
        print(f"[tenue] {nom} : thirdperson_righthand {d3}")
    print(f"[tenue] aperçus dans {dossier}")


# --------------------------------------------------------------------------------------------- écriture
def installer():
    for nom in OBJETS:
        f = ASSETS / "models" / "item" / f"{nom}.json"
        m = json.loads(f.read_text(encoding="utf-8"))
        m["display"]["thirdperson_righthand"] = troisieme_personne(nom)
        m["display"].pop("thirdperson_lefthand", None)
        f.write_text(json.dumps(m, indent=2) + "\n", encoding="utf-8")
        print(f"[tenue] {f.relative_to(RACINE)} : {m['display']['thirdperson_righthand']}")
    lignes = []
    for nom, (_, boite, _) in OBJETS.items():
        for droite in (True, False):
            p, ang, decal = fp_main(droite, boite)
            cote = "DROITE" if droite else "GAUCHE"
            vals = [*p, *ang, *decal]
            lignes.append(f"    /** {nom}, main {cote.lower()} : position (blocs), angles XYZ (degrés), décalage du bout du bras (blocs). */\n"
                          f"    static final float[] {nom.upper()}_{cote} = {{{', '.join(f'{v:.5f}F' for v in vals)}}};")
    java = f"""package fr.heraliz.crac.client.render;

/**
 * Nombres de la tenue à deux mains de la C4 et de la charge explosive en première personne. ÉCRIT par
 * modules/bases/outils/tenue_explosifs.py : ne pas modifier à la main.
 */
final class TenueExplosifsDonnees {{
    /** Bras vus des autres joueurs (radians). */
    static final float BRAS_X = {BRAS_X}F, BRAS_Y = {BRAS_Y}F;
    /** Centre de l'objet devant la caméra (blocs), inclinaison vers le joueur et lacet (degrés), échelle. */
    static final float FP_X = {FP_CENTRE[0]}F, FP_Y = {FP_CENTRE[1]}F, FP_Z = {FP_CENTRE[2]}F;
    static final float FP_INCLINE = {FP_INCLINE}F, FP_LACET = {FP_LACET}F, FP_TAILLE = {FP_TAILLE}F;
    /** Balancement du bras au repos laissé par Minecraft (radians), annulé devant chaque main. */
    static final float BOB_Z = {BOB_Z}F;
{chr(10).join(lignes)}

    private TenueExplosifsDonnees() {{
    }}
}}
"""
    JAVA.write_text(java, encoding="utf-8")
    print(f"[tenue] {JAVA.relative_to(RACINE)}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--installer", action="store_true")
    ap.add_argument("--apercu")
    a = ap.parse_args()
    if a.apercu:
        apercu(a.apercu)
    if a.installer:
        installer()
    if not a.apercu and not a.installer:
        ap.print_help()
