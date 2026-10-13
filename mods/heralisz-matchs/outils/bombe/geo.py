"""
Modèles par cubes au format GeckoLib (geo.json « 1.12.0 », UV par face) + atlas de texture HD peint par programme.

On décrit un modèle dans l'espace de Blockbench (pixels, 16 par bloc, avant du modèle vers le nord = -Z) :
    m = Modele("bombe_ogive", densite=8)          # 8 texels par pixel de modèle (128 par bloc)
    m.os("corps", pivot=(0, 0, 0))
    m.cube("corps", (-4, 0, -3), (4, 6, 3), metal(...), deco={"north": [...]})
    m.construire(dossier)  ->  <nom>.geo.json, <nom>.png, <nom>_glowmask.png (si des parties brillent)

Correspondance avec GeckoLib 4.4.7 (BakedModelFactory / GeoQuad, relus dans le jar geckolib222) : le fichier
contient origin = [-(x1), y0, z0], pivot = [-px, py, pz], rotation = [-rx, -ry, rz] ; GeckoLib les remet dans
l'espace de Blockbench au chargement. Sens des images sur chaque face (vue de l'extérieur) :
    north : gauche = +X ; south : gauche = -X ; east : gauche = +Z ; west : gauche = -Z (haut = +Y)
    up    : gauche = +X, haut = +Z (lisible depuis l'avant) ; down : gauche = +X, haut = -Z
Les décorations (deco) sont données en pixels de modèle dans le repère de l'image de la face (u à droite, v en bas).

Polices (dossier polices/, licences à côté) : Oswald 500 et 700 (OFL), DejaVu Sans Mono Bold réduite à l'ASCII.
Bibliothèques : numpy et Pillow. Outil de HeraliZ Matchs (bombe des duels), voir modeles.py.
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

POLICES = Path(__file__).parent / "polices"
FACES = ("north", "south", "east", "west", "up", "down")
NORMALES = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
SUR = 4  # suréchantillonnage des décorations (anticrénelage)


def police(nom, taille):
    chemins = {
        "oswald": POLICES / "oswald-700.ttf", "oswald5": POLICES / "oswald-500.ttf",
        "mono": POLICES / "dejavu_mono_bold.ttf",
    }
    return ImageFont.truetype(str(chemins[nom]), max(1, int(round(taille))))


# ------------------------------------------------------------------ bruit (continu d'une face à l'autre)
def _hash(ix, iy, iz, graine):
    h = (ix * 374761393 + iy * 668265263 + iz * 2147483647 + graine * 144665) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def bruit3(P, echelle, graine=0):
    """Bruit de valeur 3D (trilinéaire lissé) ; P : (..., 3) en pixels de modèle ; échelle en pixels."""
    q = P / echelle
    i = np.floor(q).astype(np.int64)
    f = q - i
    f = f * f * (3 - 2 * f)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                h = _hash(i[..., 0] + dx, i[..., 1] + dy, i[..., 2] + dz, graine)
                w = (f[..., 0] if dx else 1 - f[..., 0]) * (f[..., 1] if dy else 1 - f[..., 1]) * (f[..., 2] if dz else 1 - f[..., 2])
                out = out + h * w
    return out


def fbm(P, echelle, octaves=4, graine=0):
    tot, amp, s = 0, 0.5, echelle
    for o in range(octaves):
        tot = tot + amp * bruit3(P, s, graine + o * 17)
        amp *= 0.5
        s *= 0.5
    return tot / (1 - 0.5 ** octaves)


def hexa(c):
    c = c.lstrip("#")
    return np.array([int(c[i:i + 2], 16) / 255 for i in (0, 2, 4)])


# ------------------------------------------------------------------ contexte d'une face
class Face:
    """Une face à peindre : taille en texels, position 3D (pixels de modèle) de chaque texel, bords."""

    def __init__(self, cube, nom, densite):
        self.cube, self.nom, self.densite = cube, nom, densite
        (x0, y0, z0), (x1, y1, z1) = cube.de, cube.a
        sx, sy, sz = x1 - x0, y1 - y0, z1 - z0
        if nom in ("north", "south"):
            W, H = sx, sy
        elif nom in ("east", "west"):
            W, H = sz, sy
        else:
            W, H = sx, sz
        self.Wpx, self.Hpx = W, H
        self.w = max(1, int(round(W * densite)))
        self.h = max(1, int(round(H * densite)))
        u = (np.arange(self.w) + 0.5) / self.w * W   # pixels de modèle depuis la gauche de l'image
        v = (np.arange(self.h) + 0.5) / self.h * H   # depuis le haut
        U, Vv = np.meshgrid(u, v)
        self.U, self.V = U, Vv
        P = np.zeros((self.h, self.w, 3))
        if nom == "north":
            P[..., 0] = x1 - U; P[..., 1] = y1 - Vv; P[..., 2] = z0
        elif nom == "south":
            P[..., 0] = x0 + U; P[..., 1] = y1 - Vv; P[..., 2] = z1
        elif nom == "east":
            P[..., 0] = x1; P[..., 1] = y1 - Vv; P[..., 2] = z1 - U
        elif nom == "west":
            P[..., 0] = x0; P[..., 1] = y1 - Vv; P[..., 2] = z0 + U
        elif nom == "up":
            P[..., 0] = x1 - U; P[..., 1] = y1; P[..., 2] = z1 - Vv
        else:
            P[..., 0] = x1 - U; P[..., 1] = y0; P[..., 2] = z0 + Vv
        self.P = P + np.array(cube.decalage_bruit)
        self.N = np.array(NORMALES[nom])
        # distance au bord de la face (pixels de modèle)
        self.bord = np.minimum(np.minimum(U, W - U), np.minimum(Vv, H - Vv))


# ------------------------------------------------------------------ cubes et modèle
class Cube:
    def __init__(self, os, de, a, mat, pivot=None, rot=None, faces=None, deco=None, densite=None, inflate=0.0,
                 decalage_bruit=(0, 0, 0), deco_rot=None):
        self.os = os
        self.deco_rot = deco_rot or {}
        self.de = tuple(float(v) for v in de)
        self.a = tuple(float(v) for v in a)
        assert all(self.a[i] >= self.de[i] for i in range(3)), (de, a)
        self.mat = mat
        self.pivot = pivot
        self.rot = rot
        self.faces = faces  # None = toutes ; liste de faces gardées
        self.deco = deco or {}
        self.densite = densite
        self.inflate = inflate
        self.decalage_bruit = decalage_bruit
        self.uv = {}

    def faces_gardees(self):
        if self.faces is None:
            return list(FACES)
        return [f for f in FACES if f in self.faces]


class Modele:
    def __init__(self, nom, densite=8, largeur_atlas=1024):
        self.nom = nom
        self.densite = densite
        self.largeur_atlas = largeur_atlas
        self.os_ = {}
        self.ordre = []
        self.cubes = []
        self.info = {}

    def os(self, nom, pivot=(0, 0, 0), rotation=(0, 0, 0), parent=None):
        self.os_[nom] = {"pivot": pivot, "rotation": rotation, "parent": parent}
        self.ordre.append(nom)
        return nom

    def cube(self, os, de, a, mat, **k):
        c = Cube(os, de, a, mat, **k)
        self.cubes.append(c)
        return c

    def boite(self, os, centre, taille, mat, **k):
        cx, cy, cz = centre
        sx, sy, sz = taille
        return self.cube(os, (cx - sx / 2, cy - sy / 2, cz - sz / 2), (cx + sx / 2, cy + sy / 2, cz + sz / 2), mat, **k)

    def prisme8(self, os, centre, rayon, longueur, axe, mat, deco_cotes=None, faces_bouts=("a", "b"), densite=None,
                decalage=0.0, couleur_bouts="#3a3d3a", bouts_lumineux=False):
        """Prisme à 8 pans (cylindre) le long de l'axe 'x', 'y' ou 'z' : 4 boîtes tournées de 0, 45, 90 et 135 degrés
        (les faces internes sont omises ; les bouts se superposent : même couleur unie conseillée)."""
        l = 2 * rayon * math.tan(math.pi / 8)
        cx, cy, cz = centre
        res = []
        for k, ang in enumerate((0, 45, 90, 135)):
            if axe == "x":
                de = (cx - longueur / 2 - decalage * k, cy - rayon, cz - l / 2)
                a = (cx + longueur / 2 + decalage * k, cy + rayon, cz + l / 2)
                rot = (ang, 0, 0)
                cotes = ["up", "down"]
                bouts = {"a": "west", "b": "east"}
            elif axe == "z":
                de = (cx - l / 2, cy - rayon, cz - longueur / 2 - decalage * k)
                a = (cx + l / 2, cy + rayon, cz + longueur / 2 + decalage * k)
                rot = (0, 0, ang)
                cotes = ["up", "down"]
                bouts = {"a": "north", "b": "south"}
            else:
                de = (cx - l / 2, cy - longueur / 2 - decalage * k, cz - rayon)
                a = (cx + l / 2, cy + longueur / 2 + decalage * k, cz + rayon)
                rot = (0, ang, 0)
                cotes = ["north", "south"]
                bouts = {"a": "down", "b": "up"}
            faces = list(cotes) + [bouts[b] for b in faces_bouts]
            deco = {}
            if deco_cotes:
                for f in cotes:
                    d = deco_cotes(k, f)
                    if d:
                        deco[f] = d
            # bouts : 4 faces superposées -> couleur unie identique (pas de scintillement visible)
            mb = plat(couleur_bouts, bouts_lumineux)
            m = {f: (mb if f in bouts.values() else mat) for f in FACES}
            res.append(self.cube(os, de, a, m, pivot=centre, rot=rot, faces=faces, deco=deco, densite=densite,
                                 decalage_bruit=(k * 31.7, k * 7.3, k * 13.1)))
        return res

    # -------------------------------------------------------------- construction
    def construire(self, dossier):
        dossier = Path(dossier)
        dossier.mkdir(parents=True, exist_ok=True)
        rects = []
        for ci, c in enumerate(self.cubes):
            d = c.densite or self.densite
            for f in c.faces_gardees():
                fc = Face(c, f, d)
                rects.append((fc.h, fc.w, ci, f, fc))
        # rangement en étagères (plus hautes d'abord), 2 texels de marge
        rects.sort(key=lambda r: (-r[0], -r[1]))
        W = self.largeur_atlas
        x = y = hauteur_etagere = 0
        places = []
        for h, w, ci, f, fc in rects:
            if w + 2 > W:
                raise ValueError("face trop large pour l'atlas : %s %s" % (w, self.nom))
            if x + w + 2 > W:
                x = 0
                y += hauteur_etagere
                hauteur_etagere = 0
            places.append((x + 1, y + 1, w, h, ci, f, fc))
            x += w + 2
            hauteur_etagere = max(hauteur_etagere, h + 2)
        H = y + hauteur_etagere
        H = 1 << max(4, (H - 1).bit_length())  # puissance de deux
        atlas = np.zeros((H, W, 4))
        glow = np.zeros((H, W, 4))
        a_du_glow = False
        for px, py, w, h, ci, f, fc in places:
            c = self.cubes[ci]
            mat = c.mat[f] if isinstance(c.mat, dict) else c.mat
            rgb, alpha, gl = peindre_face(fc, mat, c.deco.get(f, []))
            atlas[py:py + h, px:px + w, :3] = rgb
            atlas[py:py + h, px:px + w, 3] = alpha
            # marge : on recopie le bord (pas de liseré au filtrage)
            atlas[py - 1, px:px + w] = atlas[py, px:px + w]
            atlas[py + h, px:px + w] = atlas[py + h - 1, px:px + w]
            atlas[py - 1:py + h + 1, px - 1] = atlas[py - 1:py + h + 1, px]
            atlas[py - 1:py + h + 1, px + w] = atlas[py - 1:py + h + 1, px + w - 1]
            if gl is not None:
                a_du_glow = True
                glow[py:py + h, px:px + w] = gl
            c.uv[f] = {"uv": [px, py], "uv_size": [w, h]}
        Image.fromarray((np.clip(atlas, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA").save(dossier / (self.nom + ".png"))
        if a_du_glow:
            Image.fromarray((np.clip(glow, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA").save(dossier / (self.nom + "_glowmask.png"))
        geo = self.geo(W, H)
        (dossier / (self.nom + ".geo.json")).write_text(json.dumps(geo, indent=1))
        return geo

    def geo(self, W, H):
        os_json = []
        for nom in self.ordre:
            o = self.os_[nom]
            b = {"name": nom, "pivot": [-o["pivot"][0], o["pivot"][1], o["pivot"][2]]}
            if o["parent"]:
                b["parent"] = o["parent"]
            r = o["rotation"]
            if any(r):
                b["rotation"] = [-r[0], -r[1], r[2]]
            cubes = []
            for c in self.cubes:
                if c.os != nom:
                    continue
                size = [round(c.a[i] - c.de[i], 4) for i in range(3)]
                cj = {"origin": [round(-c.a[0], 4), round(c.de[1], 4), round(c.de[2], 4)], "size": size}
                if c.inflate:
                    cj["inflate"] = c.inflate
                if c.rot is not None and any(c.rot):
                    p = c.pivot
                    cj["pivot"] = [round(-p[0], 4), round(p[1], 4), round(p[2], 4)]
                    cj["rotation"] = [-c.rot[0], -c.rot[1], c.rot[2]]
                cj["uv"] = {f: c.uv[f] for f in FACES if f in c.uv}
                cubes.append(cj)
            if cubes:
                b["cubes"] = cubes
            os_json.append(b)
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {"identifier": "geometry." + self.nom, "texture_width": W, "texture_height": H,
                                "visible_bounds_width": 4, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1, 0]},
                "bones": os_json,
            }],
        }


# ------------------------------------------------------------------ peinture d'une face
def peindre_face(fc, mat, deco):
    res = mat(fc)
    rgb = res["rgb"]
    alpha = res.get("alpha", np.ones((fc.h, fc.w)))
    glow = res.get("glow")
    if deco:
        calque, calque_glow, a_glow = dessiner_deco(fc, deco)
        a = calque[..., 3:4]
        rgb = rgb * (1 - a) + calque[..., :3] * a
        alpha = alpha + calque[..., 3] * (1 - alpha)
        if a_glow:
            if glow is None:
                glow = np.zeros((fc.h, fc.w, 4))
            ga = calque_glow[..., 3:4]
            glow[..., :3] = glow[..., :3] * (1 - ga) + calque_glow[..., :3] * ga
            glow[..., 3] = np.maximum(glow[..., 3], calque_glow[..., 3])
        # usure par-dessus les marquages (sauf ce qui brille)
        if res.get("usure_deco") is not None:
            u = res["usure_deco"][..., None] * a
            rgb = rgb * (1 - u) + res["rgb_usure"] * u
    return np.clip(rgb, 0, 1), alpha, glow


def dessiner_deco(fc, deco):
    """Décorations vectorielles (pixels de modèle, repère de l'image de la face), suréchantillonnées puis réduites."""
    s = fc.densite * SUR
    Wt, Ht = fc.w * SUR, fc.h * SUR
    im = Image.new("RGBA", (Wt, Ht), (0, 0, 0, 0))
    img = Image.new("RGBA", (Wt, Ht), (0, 0, 0, 0))
    a_glow = False

    def P(u, v):
        return (u * s, v * s)

    for d in deco:
        t = d[0]
        cible = img if (len(d) > 0 and isinstance(d[-1], dict) and d[-1].get("glow")) else im
        if cible is img:
            a_glow = True
        dr = ImageDraw.Draw(cible)
        opts = d[-1] if isinstance(d[-1], dict) else {}
        if t == "rect":
            _, u0, v0, u1, v1, coul = d[:6]
            dr.rectangle([P(u0, v0), (u1 * s - 1, v1 * s - 1)], fill=coul)
        elif t == "rrect":
            _, u0, v0, u1, v1, r, coul = d[:7]
            dr.rounded_rectangle([P(u0, v0), (u1 * s - 1, v1 * s - 1)], radius=r * s, fill=coul,
                                 outline=opts.get("contour"), width=int(opts.get("ep", 0) * s))
        elif t == "cadre":
            _, u0, v0, u1, v1, ep, coul = d[:7]
            dr.rectangle([P(u0, v0), (u1 * s - 1, v1 * s - 1)], outline=coul, width=max(1, int(ep * s)))
        elif t == "ligne":
            _, u0, v0, u1, v1, ep, coul = d[:7]
            dr.line([P(u0, v0), P(u1, v1)], fill=coul, width=max(1, int(ep * s)))
        elif t == "rainure":  # ligne de panneau : creux sombre + reflet clair dessous
            _, u0, v0, u1, v1, ep = d[:6]
            dr.line([P(u0, v0), P(u1, v1)], fill=(10, 10, 10, 150), width=max(1, int(ep * s)))
            dx, dy = (0, ep * 0.9) if abs(v1 - v0) < abs(u1 - u0) else (ep * 0.9, 0)
            dr.line([P(u0 + dx, v0 + dy), P(u1 + dx, v1 + dy)], fill=(255, 255, 255, 38), width=max(1, int(ep * 0.6 * s)))
        elif t == "vis":
            _, u, v, r = d[:4]
            coul = opts.get("coul", (60, 62, 60, 255))
            dr.ellipse([P(u - r, v - r), P(u + r, v + r)], fill=(20, 20, 20, 200))
            dr.ellipse([P(u - r * 0.8, v - r * 0.8), P(u + r * 0.8, v + r * 0.8)], fill=coul)
            dr.ellipse([P(u - r * 0.8, v - r * 0.8), P(u + r * 0.2, v + r * 0.2)], fill=tuple(min(255, int(c * 1.35)) for c in coul[:3]) + (255,))
            dr.line([P(u - r * 0.55, v), P(u + r * 0.55, v)], fill=(15, 15, 15, 230), width=max(1, int(r * 0.35 * s)))
        elif t == "rond":
            _, u, v, r, coul = d[:5]
            dr.ellipse([P(u - r, v - r), P(u + r, v + r)], fill=coul, outline=opts.get("contour"), width=int(opts.get("ep", 0) * s))
        elif t == "anneau":
            _, u, v, r, ep, coul = d[:6]
            dr.ellipse([P(u - r, v - r), P(u + r, v + r)], outline=coul, width=max(1, int(ep * s)))
        elif t == "poly":
            _, pts, coul = d[:3]
            dr.polygon([P(a, b) for a, b in pts], fill=coul)
        elif t == "texte":
            _, u, v, txt, nom_police, taille, coul = d[:7]
            f = police(nom_police, taille * s)
            ancre = opts.get("ancre", "la")
            esp = opts.get("espacement", 0)
            if esp:
                # lettres espacées
                larg = sum(dr.textlength(ch, font=f) for ch in txt) + esp * s * (len(txt) - 1)
                x = u * s - (larg / 2 if ancre[0] == "m" else larg if ancre[0] == "r" else 0)
                for ch in txt:
                    dr.text((x, v * s), ch, font=f, fill=coul, anchor="l" + ancre[1])
                    x += dr.textlength(ch, font=f) + esp * s
            else:
                dr.text(P(u, v), txt, font=f, fill=coul, anchor=ancre)
        elif t == "rayures":  # bandes de danger diagonales
            _, u0, v0, u1, v1, larg, c1, c2 = d[:8]
            sous = Image.new("RGBA", (int((u1 - u0) * s), int((v1 - v0) * s)), c1)
            ds = ImageDraw.Draw(sous)
            L = larg * s
            hh = sous.size[1]
            k = -hh
            while k < sous.size[0] + hh:
                ds.polygon([(k, hh), (k + L, hh), (k + L + hh, 0), (k + hh, 0)], fill=c2)
                k += 2 * L
            cible.alpha_composite(sous, (int(u0 * s), int(v0 * s)))
        elif t == "fentes":  # grille d'aération
            _, u0, v0, u1, v1, n, coul = d[:7]
            hpas = (v1 - v0) / n
            for i in range(n):
                y = v0 + i * hpas + hpas * 0.25
                dr.rounded_rectangle([P(u0, y), P(u1, y + hpas * 0.5)], radius=hpas * 0.25 * s, fill=coul)
                dr.line([P(u0 + hpas * 0.2, y + hpas * 0.55), P(u1 - hpas * 0.2, y + hpas * 0.55)], fill=(255, 255, 255, 30),
                        width=max(1, int(hpas * 0.12 * s)))
        elif t == "image":
            _, u0, v0, u1, v1, pil = d[:6]
            r = pil.convert("RGBA").resize((max(1, int((u1 - u0) * s)), max(1, int((v1 - v0) * s))), Image.LANCZOS)
            cible.alpha_composite(r, (int(u0 * s), int(v0 * s)))
        elif t == "image_px":  # image collée texel pour texel (écrans) : u0, v0 en pixels de modèle, pas de réduction
            pass
        else:
            raise ValueError(t)
    if fc.cube.deco_rot.get(fc.nom) == 180:
        im = im.rotate(180)
        img = img.rotate(180)
    im = im.resize((fc.w, fc.h), Image.LANCZOS)
    img = img.resize((fc.w, fc.h), Image.LANCZOS)
    calque = np.asarray(im).astype(float) / 255
    calque_glow = np.asarray(img).astype(float) / 255
    # ce qui brille est aussi dans la texture de base (visible si le masque n'est pas chargé)
    if a_glow:
        ga = calque_glow[..., 3:4]
        calque[..., :3] = calque[..., :3] * (1 - ga) + calque_glow[..., :3] * ga
        calque[..., 3] = np.maximum(calque[..., 3], calque_glow[..., 3])
    return calque, calque_glow, a_glow


# ------------------------------------------------------------------ matériaux
def metal(couleur, usure=0.5, crasse=0.35, grain=0.05, couleur_usure="#9a9c96", rugueux=0.5, graine=0, bords=0.22,
          motif=None):
    """Métal peint, mat : grain fin, variations larges, crasse vers le bas, bords usés (peinture écaillée)."""
    base = hexa(couleur)
    cu = hexa(couleur_usure)

    def m(fc):
        P = fc.P
        n1 = fbm(P, 6.0, 4, graine)
        n2 = bruit3(P, 0.35, graine + 5)
        n3 = fbm(P, 1.6, 3, graine + 9)
        rgb = base[None, None, :] * (0.9 + 0.2 * n1[..., None]) * (1 - grain + 2 * grain * n2[..., None])
        if motif is not None:
            rgb = motif(fc, rgb)
        # crasse : plus sombre et un peu brune vers le bas des faces verticales, et dans les creux
        if fc.nom in ("north", "south", "east", "west"):
            vb = np.clip(fc.V / max(fc.Hpx, 1e-6), 0, 1)
            k = crasse * (vb ** 2.2) * (0.6 + 0.8 * n3)
            rgb = rgb * (1 - 0.45 * k[..., None]) + np.array([0.18, 0.15, 0.11]) * 0.45 * k[..., None]
        elif fc.nom == "down":
            rgb = rgb * (1 - 0.35 * crasse)
        # bords usés : éclats de peinture en taches irrégulières (pas de liseré continu)
        n4 = bruit3(P, 0.9, graine + 41)
        n5 = bruit3(P, 0.22, graine + 43)
        seuil = bords * (0.15 + 1.1 * n3 * n3)
        u = (fc.bord < seuil) & (n4 * 0.7 + n5 * 0.3 > 1 - usure * 0.55)
        u = u.astype(float)
        u_doux = np.clip((seuil - fc.bord) / np.maximum(seuil, 1e-6), 0, 1) * usure * 0.18 * n4
        rgb = rgb * (1 - u_doux[..., None]) + cu * u_doux[..., None]
        rgb = rgb * (1 - u[..., None]) + cu * (0.8 + 0.3 * n5[..., None]) * u[..., None]
        # arête qui accroche la lumière (très léger)
        lis = np.clip(1 - fc.bord / 0.10, 0, 1) * 0.035
        rgb = rgb + lis[..., None]
        # rayures fines
        ray = (bruit3(P * np.array([1, 7, 1]), 0.5, graine + 21) > 0.95) & (n3 > 0.6)
        rgb = rgb * (1 - 0.2 * ray[..., None]) + cu * 0.2 * ray[..., None]
        usure_deco = ((fc.bord < seuil * 1.3) & (n4 * 0.7 + n5 * 0.3 > 1 - usure * 0.7)).astype(float) * 0.9 + \
                     (bruit3(P, 0.3, graine + 33) > 0.9) * 0.5 * usure
        return {"rgb": rgb, "usure_deco": np.clip(usure_deco, 0, 1), "rgb_usure": rgb}

    return m


def caoutchouc(couleur="#1c1d1f", graine=3):
    base = hexa(couleur)

    def m(fc):
        n1 = fbm(fc.P, 3.0, 3, graine)
        n2 = bruit3(fc.P, 0.3, graine + 4)
        rgb = base * (0.85 + 0.25 * n1[..., None]) * (0.95 + 0.1 * n2[..., None])
        lis = np.clip(1 - fc.bord / 0.12, 0, 1) * 0.035
        rgb = rgb + lis[..., None]
        if fc.nom in ("north", "south", "east", "west"):
            vb = np.clip(fc.V / max(fc.Hpx, 1e-6), 0, 1)
            rgb = rgb + (np.array([0.20, 0.17, 0.13]) * 0.25 * vb ** 3 * n1)[..., None] if False else rgb
        return {"rgb": rgb}

    return m


def uni(couleur, bruit=0.03, graine=7):
    base = hexa(couleur)

    def m(fc):
        n = bruit3(fc.P, 0.4, graine)
        return {"rgb": base * (1 - bruit + 2 * bruit * n[..., None])}

    return m


def lumineux(couleur, intensite=1.0, coeur=None):
    """Matériau qui brille (dans la texture et dans le masque lumineux)."""
    base = hexa(couleur)
    c2 = hexa(coeur) if coeur else np.minimum(1, base * 1.25 + 0.15)

    def m(fc):
        d = np.clip(fc.bord / (min(fc.Wpx, fc.Hpx) / 2 + 1e-6), 0, 1)
        rgb = base * (1 - d[..., None]) + c2 * d[..., None]
        rgb = rgb * intensite
        glow = np.concatenate([rgb, np.ones((fc.h, fc.w, 1))], axis=-1)
        return {"rgb": rgb, "glow": glow}

    return m


def ecran(image_pil, lueur=1.0, verre=0.10):
    """Écran : l'image (déjà à la bonne taille en texels, ou réduite) brille ; un léger reflet de verre."""

    def m(fc):
        im = image_pil.convert("RGB").resize((fc.w, fc.h), Image.LANCZOS)
        rgb = np.asarray(im).astype(float) / 255
        # lignes de balayage très légères + reflet diagonal
        lignes = 1 - 0.06 * ((np.arange(fc.h) % 2) == 1)[:, None, None]
        rgb = rgb * lignes
        diag = np.clip(1 - np.abs((fc.U / max(fc.Wpx, 1e-6)) + (fc.V / max(fc.Hpx, 1e-6)) * 0.6 - 0.35) / 0.18, 0, 1)
        rgb = rgb + verre * diag[..., None]
        rgb = np.clip(rgb * lueur, 0, 1)
        glow = np.concatenate([rgb, np.ones((fc.h, fc.w, 1))], axis=-1)
        return {"rgb": rgb, "glow": glow}

    return m


def avec_motif(mat_base, motif):
    """Applique une fonction motif(fc, rgb) -> rgb sur un matériau existant (texture de surface)."""

    def m(fc):
        r = mat_base(fc)
        r["rgb"] = motif(fc, r["rgb"])
        return r

    return m


def plat(couleur, lumineux_=False):
    """Couleur unie sans bruit (bouts superposés des cylindres)."""
    base = hexa(couleur)

    def m(fc):
        rgb = np.ones((fc.h, fc.w, 3)) * base
        r = {"rgb": rgb}
        if lumineux_:
            r["glow"] = np.concatenate([rgb, np.ones((fc.h, fc.w, 1))], axis=-1)
        return r

    return m
