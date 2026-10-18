#!/usr/bin/env python3
"""
Modèles des skins de mêlée (finitions des couteaux, shader camo_<id>) : écrits par ce script, jamais à la main.

    python mods/heraliz-tactique/outils/skins_melee.py

Les finitions des couteaux (Lore, Doppler…) sont dessinées par le shader d'après une carte de positions : pour chaque
point de la texture, la position 3D de la face qui l'utilise (PosMap). Ça demande que chaque face ait SON morceau de
texture. Les modèles des armes de mêlée de LR ne le respectent pas :
  - Lame de Desmoulins (polaris) : beaucoup de faces de la lame partagent un même morceau de texture ou en ont un de
    taille nulle -> elles prenaient en jeu le motif d'une autre face (coupures nettes sur la lame) ;
  - batte (baseball_bat) : un cœur qui court d'un bout à l'autre et des lattes penchées de 1,5 à 3° qui le traversent ->
    faces superposées qui clignotent dès qu'elles n'ont plus la même couleur.
Le modèle de chaque skin est donc une copie propre, avec les mêmes os (mêmes animations, même tenue en main) :
  - polaris_lore_geo : géométrie d'origine, chaque face de la lame (os blade2) reçoit un morceau de texture à elle,
    dans une moitié ajoutée à droite de la texture (polaris_lore_uv.png, deux fois plus large) ; le manche garde ses
    morceaux d'origine (les motifs de la texture servent au manche orné) ;
  - baseball_bat_skin_geo : batte refaite en tronçons hexagonaux (3 pavés tournés de 60° autour de l'axe, seules leurs
    faces extérieures sont dessinées), même silhouette que celle de LR (pommeau, manche, évasement en 8 marches,
    tonneau, bout arrondi), le manche (pommeau compris) dans l'os « manche » (rôle grip de KnifeType), aucune face qui
    en traverse une autre, chaque face avec son morceau de texture.
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image

MOD = Path(__file__).resolve().parent.parent
MELEE = MOD / "src" / "client" / "resources" / "assets" / "heralisz_melee"
GEO = MELEE / "geo_models" / "melee"
TEX = MELEE / "textures" / "melee"
FACES = ("north", "south", "east", "west", "up", "down")
# repères des mains (jamais dessinés) : leur morceau de texture chevauchait celui de l'arme
REPERES = ("lefthand_pos", "righthand_pos")
# plus petit côté d'un morceau de texture (unités du modèle) : la carte des couteaux fait 1024 texels, il en faut au
# moins 6 par côté de face pour que le filtrage ne mélange pas les faces voisines
MIN_UV = 0.75


def dims(face, size):
    x, y, z = size
    return {"north": (x, y), "south": (x, y), "east": (z, y), "west": (z, y), "up": (x, z), "down": (x, z)}[face]


class Etageres:
    """Rangement des morceaux de texture en étagères, dans un rectangle (unités du modèle)."""

    def __init__(self, x0, y0, largeur, hauteur):
        self.x0, self.y0, self.l, self.h = x0, y0, largeur, hauteur
        self.x, self.y, self.ligne = 0.0, 0.0, 0.0

    def place(self, w, h):
        w, h = math.ceil(w * 4) / 4 + 0.25, math.ceil(h * 4) / 4 + 0.25   # bords de 1/4 d'unité entre morceaux
        if self.x + w > self.l:
            self.x, self.y, self.ligne = 0.0, self.y + self.ligne, 0.0
        if self.y + h > self.h:
            sys.exit("[skins_melee] plus de place dans la texture")
        u, v = self.x0 + self.x, self.y0 + self.y
        self.x += w
        self.ligne = max(self.ligne, h)
        return u, v


def ranger_reperes(g, rang):
    """Les cubes des repères des mains partagent un petit morceau de texture à eux, hors de celui de l'arme."""
    u, v = rang.place(1, 1)
    for b in g["bones"]:
        if b["name"] in REPERES:
            for c in b.get("cubes", []):
                c["uv"] = {f: {"uv": [u, v], "uv_size": [1, 1]} for f in FACES}


def desmoulins():
    src = json.loads((GEO / "polaris_geo.json").read_text(encoding="utf-8"))
    g = src["minecraft:geometry"][0]
    tw, th = g["description"]["texture_width"], g["description"]["texture_height"]
    g["description"]["identifier"] = "geometry.polaris_lore"
    g["description"]["texture_width"] = tw * 2
    rang = Etageres(tw, 0, tw, th)
    n = 0
    for b in g["bones"]:
        if b["name"] != "blade2":
            continue
        for c in b.get("cubes", []):
            uv = {}
            for f in FACES:
                a, bb = dims(f, c["size"])
                if a < 1e-4 or bb < 1e-4:
                    continue   # face sans épaisseur : jamais dessinée
                w, h = max(a, MIN_UV), max(bb, MIN_UV)
                u, v = rang.place(w, h)
                uv[f] = {"uv": [u, v], "uv_size": [w, h]}
                n += 1
            c["uv"] = uv
    ranger_reperes(g, rang)
    (GEO / "polaris_lore_geo.json").write_text(json.dumps(src, separators=(",", ":")), encoding="utf-8")
    img = Image.open(TEX / "polaris_uv.png").convert("RGBA")
    px = img.width // tw
    out = Image.new("RGBA", (img.width * 2, img.height), (150, 154, 160, 255))   # acier : moitié des faces de la lame
    out.paste(img, (0, 0))
    out.save(TEX / "polaris_lore_uv.png")
    print("[skins_melee] Desmoulins : %d faces de lame avec leur morceau de texture (%d px par unité)" % (n, px))


# batte : (bas, haut, apothème) des tronçons, en unités du modèle, d'après le modèle de LR ; manche = pommeau + poignée
POMMEAU = (2.72, 3.72, 0.758)
POIGNEE = (3.72, 11.74, 0.541)
EVASEMENT = (11.74, 23.72, 0.541, 0.866, 8)
TONNEAU = (23.72, 32.9, 0.866)
BOUT = [(32.9, 33.35, 0.76), (33.35, 33.7, 0.52)]


def troncons():
    t = [("manche",) + POMMEAU, ("manche",) + POIGNEE]
    y0, y1, a0, a1, n = EVASEMENT
    for i in range(n):
        ya, yb = y0 + (y1 - y0) * i / n, y0 + (y1 - y0) * (i + 1) / n
        t.append(("weapon", ya, yb, a0 + (a1 - a0) * (i + 0.5) / n))
    t.append(("weapon",) + TONNEAU)
    t += [("weapon",) + b for b in BOUT]
    return t


def batte():
    src = json.loads((GEO / "baseball_bat_geo.json").read_text(encoding="utf-8"))
    g = src["minecraft:geometry"][0]
    g["description"]["identifier"] = "geometry.baseball_bat_skin"
    tw = th = 64
    g["description"]["texture_width"], g["description"]["texture_height"] = tw, th
    rang = Etageres(0, 0, tw, th)
    weapon = next(b for b in g["bones"] if b["name"] == "weapon")
    t = troncons()
    cubes = {"weapon": [], "manche": []}
    for i, (os_, y0, y1, a) in enumerate(t):
        s = 2 * a / math.sqrt(3)   # hexagone régulier : côté = 2 x apothème / racine de 3
        h = y1 - y0
        # chapeaux : seulement là où ce tronçon dépasse son voisin (sinon caché dedans)
        bas = i == 0 or t[i - 1][3] < a - 1e-6
        haut = i == len(t) - 1 or t[i + 1][3] < a - 1e-6
        for rot in (0, 60, -60):
            uv = {}
            for f in ("north", "south"):
                w, hh = max(s, MIN_UV), max(h, MIN_UV)
                u, v = rang.place(w, hh)
                uv[f] = {"uv": [u, v], "uv_size": [w, hh]}
            for f, garde in (("down", bas), ("up", haut)):
                if garde:
                    w, hh = max(s, MIN_UV), max(2 * a, MIN_UV)
                    u, v = rang.place(w, hh)
                    uv[f] = {"uv": [u, v], "uv_size": [w, hh]}
            c = {"origin": [-s / 2, y0, -a], "size": [s, h, 2 * a], "uv": uv}
            if rot:
                c["pivot"] = [0, y0, 0]
                c["rotation"] = [0, rot, 0]
            cubes[os_].append(c)
    weapon["cubes"] = cubes["weapon"]
    i = g["bones"].index(weapon)
    g["bones"].insert(i + 1, {"name": "manche", "parent": "weapon", "pivot": weapon["pivot"], "cubes": cubes["manche"]})
    ranger_reperes(g, rang)
    (GEO / "baseball_bat_skin_geo.json").write_text(json.dumps(src, separators=(",", ":")), encoding="utf-8")
    # texture de secours (si le shader ne marche pas) : manche noir, reste acier bleuté
    px = 4
    # toute la texture opaque : un point transparent est jeté au rendu (trou dans la batte au bord d'une face)
    img = Image.new("RGBA", (tw * px, th * px), (70, 96, 160, 255))
    from PIL import ImageDraw
    d = ImageDraw.Draw(img)
    for b in g["bones"]:
        if b["name"] not in ("weapon", "manche"):
            continue
        if b["name"] != "manche":
            continue
        col = (24, 25, 28, 255)
        for c in b["cubes"]:
            for f in c["uv"].values():
                u, v = f["uv"]
                w, h = f["uv_size"]
                d.rectangle([u * px - 1, v * px - 1, (u + w) * px, (v + h) * px], fill=col)
    img.save(TEX / "baseball_bat_skin_uv.png")
    print("[skins_melee] batte : %d tronçons, %d pavés" % (len(t), len(cubes["weapon"]) + len(cubes["manche"])))


if __name__ == "__main__":
    desmoulins()
    batte()
