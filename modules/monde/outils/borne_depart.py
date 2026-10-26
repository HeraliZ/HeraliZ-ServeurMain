"""Borne de départ du spawn (HeraliZ Utils, bloc heralisz_utils:borne_depart) : modèle, textures et rendus de contrôle.

    python modules/monde/outils/borne_depart.py [--rendus DOSSIER]

Maquette validée par le propriétaire (19/10) : « table tactique holographique ». Écrit dans
src/client/resources/assets/heralisz_utils/ :
- textures/block/borne_depart_{raye,sombre,olive,carte,holo}.png (64 × 64 : socle rayé jaune et noir, métal sombre,
  tôle vert armée, carte allumée de la table avec villes cerclées et cible rouge, lentille du projecteur) ;
- models/block/borne_depart.json (table d'un bloc : socle rayé, quatre pieds, tablette et radio, plateau, carte allumée,
  projecteur), models/item/borne_depart.json, blockstates/borne_depart.json (quatre directions).
L'hologramme au-dessus (globe de la carte qui tourne, épingle rouge qui saute) n'est pas dans le modèle : il est dessiné
chez le joueur par fr.heralisz.utils.spawn.BorneHologramme ; --rendus le montre fixe, pour l'aperçu seulement.
--rendus : vues de trois quarts, de face et de dos (CLAUDE.md, règle 9). On modifie ce fichier, jamais les fichiers produits.
"""
import argparse
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ICI = Path(__file__).resolve().parent.parent
ASSETS = ICI / "src/client/resources/assets/heralisz_utils"

# ------------------------------------------------------------------ textures (64 × 64)
S = 64                                                              # texels par bloc

def bruit(im, amp, seed):
    r = random.Random(seed); px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            c = px[x, y]; b = r.randint(-amp, amp)
            px[x, y] = tuple(max(0, min(255, v + b)) for v in c[:3]) + (255,)
    return im

def metal(base, seed, panneaux=True, rivets=True, rouille=0.0):
    im = Image.new("RGBA", (S, S), base + (255,)); d = ImageDraw.Draw(im)
    for y in range(S):  # léger dégradé vertical
        k = 1.0 + 0.10 * (0.5 - y / S)
        d.line([(0, y), (S, y)], fill=tuple(int(c * k) for c in base) + (255,))
    bruit(im, 6, seed)
    if panneaux:
        for v in (0, 31, 63):
            d.line([(v, 0), (v, S)], fill=tuple(int(c * .55) for c in base)); d.line([(0, v), (S, v)], fill=tuple(int(c * .55) for c in base))
            if v < 63:
                d.line([(v + 1, 0), (v + 1, S)], fill=tuple(min(255, int(c * 1.25)) for c in base))
    if rivets:
        for x in (4, 27, 36, 59):
            for y in (4, 27, 36, 59):
                d.ellipse([x - 1, y - 1, x + 1, y + 1], fill=tuple(min(255, int(c * 1.5)) for c in base))
    r = random.Random(seed + 1)
    for _ in range(int(rouille * 60)):
        x, y = r.randrange(S), r.randrange(S); l = r.randint(2, 7)
        d.line([(x, y), (x, y + l)], fill=(110 + r.randint(-15, 15), 62, 34))
    return im

def rayures():
    im = Image.new("RGBA", (S, S)); px = im.load(); r = random.Random(3)
    for y in range(S):
        for x in range(S):
            c = (226, 178, 32) if ((x + y) // 8) % 2 == 0 else (28, 28, 26)
            if r.random() < 0.06: c = (96, 88, 70)
            px[x, y] = c + (255,)
    return im

def carte_ecran(teinte=(30, 210, 120), fond=(6, 26, 18)):
    im = Image.new("RGBA", (S, S), fond + (255,)); d = ImageDraw.Draw(im)
    r = random.Random(5)
    for k in range(7):  # courbes de niveau
        pts = [(x, 10 + k * 7 + 3 * math.sin(x * 0.12 + k)) for x in range(0, S + 1, 2)]
        d.line(pts, fill=tuple(int(c * .45) for c in teinte), width=1)
    for v in range(0, S, 16):
        d.line([(v, 0), (v, S)], fill=tuple(int(c * .25) for c in teinte)); d.line([(0, v), (S, v)], fill=tuple(int(c * .25) for c in teinte))
    # villes (zones interdites) en hachures, points d'arrivée possibles
    for (x, y, rr) in ((16, 20, 7), (44, 14, 6), (38, 46, 8)):
        d.ellipse([x - rr, y - rr, x + rr, y + rr], outline=(200, 70, 60), width=1)
    for _ in range(9):
        x, y = r.randrange(6, 58), r.randrange(6, 58)
        d.point((x, y), fill=tuple(int(c * .9) for c in teinte))
    # cible tirée au hasard : réticule rouge
    cx, cy = 24, 40
    d.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], outline=(255, 70, 50), width=1)
    d.line([(cx - 9, cy), (cx - 3, cy)], fill=(255, 70, 50)); d.line([(cx + 3, cy), (cx + 9, cy)], fill=(255, 70, 50))
    d.line([(cx, cy - 9), (cx, cy - 3)], fill=(255, 70, 50)); d.line([(cx, cy + 3), (cx, cy + 9)], fill=(255, 70, 50))
    d.point((cx, cy), fill=(255, 220, 200))
    for y in range(0, S, 2): d.line([(0, y), (S, y)], fill=tuple(int(c * .8) for c in fond))
    d.rectangle([0, 0, S - 1, S - 1], outline=(20, 22, 20), width=2)
    return im

def lumiere(c, seed=0):
    im = Image.new("RGBA", (S, S)); px = im.load()
    for y in range(S):
        for x in range(S):
            dd = math.hypot(x - 31.5, y - 31.5) / 45
            k = 1.15 - 0.5 * dd
            px[x, y] = tuple(max(0, min(255, int(v * k))) for v in c) + (255,)
    return im

def peinture(c, seed, bande=None):
    im = metal(c, seed, panneaux=True, rivets=True, rouille=0.15)
    if bande:
        d = ImageDraw.Draw(im); d.rectangle([0, 40, S, 50], fill=bande + (255,)); bruit(im, 3, seed + 9)
    return im

def pochoir(base, texte):
    im = metal(base, 21, rivets=False); d = ImageDraw.Draw(im)
    try: f = ImageFont.truetype("DejaVuSans-Bold.ttf", 11)
    except Exception: f = None
    d.text((4, 24), texte, fill=(225, 220, 200), font=f)
    return im

# ------------------------------------------------------------------ rendu (faces du modèle, painter)
NORMALES = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def coins(de, a, f):
    x0, y0, z0 = de
    x1, y1, z1 = a
    # ordre : haut gauche, haut droite, bas droite, bas gauche (vu de l'extérieur), comme les uv de Minecraft
    return {"north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
            "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
            "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
            "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
            "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
            "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]}[f]


def tourner(p, rot):
    if not rot:
        return p
    o, a = rot["origin"], math.radians(rot["angle"])
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    c, s = math.cos(a), math.sin(a)
    if rot["axis"] == "x":
        y, z = y * c - z * s, y * s + z * c
    elif rot["axis"] == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


# ------------------------------------------------------------------ rendu HD (uv 0-16 -> texels)
def rendu(m, textures, lacet, tangage, taille=900, fond=(24, 26, 30), centre_y=16, champ=46):
    k = taille / champ
    cy, sy_ = math.cos(math.radians(lacet)), math.sin(math.radians(lacet))
    cp, sp = math.cos(math.radians(tangage)), math.sin(math.radians(tangage))
    def vue(p):
        x, y, z = p[0] - 8, p[1] - centre_y, p[2] - 8
        x, z = x * cy - z * sy_, x * sy_ + z * cy
        y, z = y * cp + z * sp, -y * sp + z * cp
        return (taille / 2 + x * k, taille * 0.52 - y * k, z)
    lum = (-0.45, 0.75, -0.5)
    polys = []
    for e in m["elements"]:
        rot = e.get("rotation")
        for f, fd in e["faces"].items():
            pts = [tourner(p, rot) for p in coins(e["from"], e["to"], f)]
            n = tourner(NORMALES[f], dict(rot, origin=[0, 0, 0]) if rot else None)
            nx, nz = n[0] * cy - n[2] * sy_, n[0] * sy_ + n[2] * cy
            ny, nz = n[1] * cp + nz * sp, -n[1] * sp + nz * cp
            if nz > 0.01: continue
            v = [vue(p) for p in pts]
            prof = sum(p[2] for p in v) / 4
            ombre = 0.5 + 0.5 * max(0.0, n[0] * lum[0] + n[1] * lum[1] + n[2] * lum[2] + 0.35)
            ombre = min(1.0, ombre)
            if "forge_data" in fd: ombre = 1.0
            polys.append((prof, v, fd, ombre))
    polys.sort(key=lambda t: -t[0])
    im = Image.new("RGB", (taille, taille), fond)
    # sol
    dr = ImageDraw.Draw(im)
    sol = [vue((x, 0, z)) for x, z in ((-16, -16), (32, -16), (32, 32), (-16, 32))]
    dr.polygon([(p[0], p[1]) for p in sol], fill=(48, 52, 50))
    glows = []
    for prof, v, fd, ombre in polys:
        tex = textures[fd["texture"][1:]]
        sc = tex.width / 16.0
        u0, v0, u1, v1 = fd["uv"]
        miroir = u0 > u1
        if miroir: u0, u1 = u1, u0
        src = tex.crop((int(u0 * sc), int(v0 * sc), max(int(u0 * sc) + 1, int(math.ceil(u1 * sc))), max(int(v0 * sc) + 1, int(math.ceil(v1 * sc)))))
        if miroir: src = src.transpose(Image.FLIP_LEFT_RIGHT)
        sw, sh = src.size
        f = max(1, int(600 / max(sw, sh)))
        src = src.resize((sw * f, sh * f), Image.NEAREST)
        (ax, ay, _), (bx, by, _), _, (dx, dy, _) = v
        W, H = src.size
        det = (bx - ax) * (dy - ay) - (dx - ax) * (by - ay)
        if abs(det) < 1e-6: continue
        ia = ((dy - ay) * W / det, -(dx - ax) * W / det)
        ib = (-(by - ay) * H / det, (bx - ax) * H / det)
        coef = (ia[0], ia[1], -(ia[0] * ax + ia[1] * ay), ib[0], ib[1], -(ib[0] * ax + ib[1] * ay))
        couche = src.convert("RGB").transform((taille, taille), Image.AFFINE, coef, resample=Image.NEAREST)
        couche = Image.eval(couche, lambda c, o=ombre: int(c * o))
        masque = Image.new("L", (taille, taille), 0)
        ImageDraw.Draw(masque).polygon([(p[0], p[1]) for p in v], fill=255)
        im.paste(couche, (0, 0), masque)
        if "forge_data" in fd and fd.get("lueur"):
            glows.append(([(p[0], p[1]) for p in v], fd["lueur"]))
    if glows:
        g = Image.new("RGB", (taille, taille), (0, 0, 0)); gd = ImageDraw.Draw(g)
        for poly, c in glows: gd.polygon(poly, fill=c)
        g = g.filter(ImageFilter.GaussianBlur(18))
        im = Image.blend(im, Image.eval(Image.merge("RGB", [Image.eval(a, lambda x: x) for a in im.split()]), lambda x: x), 0)
        from PIL import ImageChops
        im = ImageChops.add(im, g)
    return im

def F(uv, t, lit=False, lueur=None):
    f = {"uv": uv, "texture": "#" + t}
    if lit: f["forge_data"] = {"block_light": 15, "sky_light": 15}
    if lueur: f["lueur"] = lueur
    return f

def B(de, a, t, faces=None, rot=None, uv=None, lit=False, lueur=None, sauf=()):
    faces = faces or {}
    tous = {}
    for d in ("north", "south", "east", "west", "up", "down"):
        if d in sauf: continue
        if d in faces: tous[d] = faces[d]; continue
        x0, y0, z0 = de; x1, y1, z1 = a
        u = uv or {"north": [16 - x1, 16 - (y1 % 16 or 16), 16 - x0, 16 - (y0 % 16)], }.get(d)
        if u is None or u[2] <= u[0] or u[3] <= u[1]:
            # uv proportionnel à la face
            w = {"north": x1 - x0, "south": x1 - x0, "east": z1 - z0, "west": z1 - z0, "up": x1 - x0, "down": x1 - x0}[d]
            h = {"north": y1 - y0, "south": y1 - y0, "east": y1 - y0, "west": y1 - y0, "up": z1 - z0, "down": z1 - z0}[d]
            u = [0, 0, min(16, w), min(16, h)]
        tous[d] = F(u, t, lit, lueur)
    e = {"from": de, "to": a, "faces": tous}
    if rot: e["rotation"] = rot
    return e

def cylindre(cx, cz, y0, y1, r, t, n=3, lit=False, lueur=None):
    """Approximation d'un cylindre : n boîtes tournées (0, 22.5, -22.5 ou 45°)."""
    els = []
    angles = [0, 45] if n == 2 else [0, 22.5, -22.5]
    for a in angles:
        e = B([cx - r, y0, cz - r * 0.41], [cx + r, y1, cz + r * 0.41], t, lit=lit, lueur=lueur)
        e2 = B([cx - r * 0.41, y0, cz - r], [cx + r * 0.41, y1, cz + r], t, lit=lit, lueur=lueur)
        for x in (e, e2):
            if a: x["rotation"] = {"origin": [cx, y0, cz], "axis": "y", "angle": a}
            els.append(x)
    return els


def F(uv, t, lit=False, lueur=None):
    f = {"uv": uv, "texture": "#" + t}
    if lit:
        f["forge_data"] = {"block_light": 15, "sky_light": 15}     # dessiné à pleine lumière (Forge)
    if lueur:
        f["lueur"] = lueur                                          # halo de l'aperçu seulement (retiré du modèle)
    return f


def B(de, a, t, faces=None, rot=None, lit=False, lueur=None, sauf=()):
    faces = faces or {}
    tous = {}
    x0, y0, z0 = de
    x1, y1, z1 = a
    for d in ("north", "south", "east", "west", "up", "down"):
        if d in sauf:
            continue
        if d in faces:
            tous[d] = faces[d]
            continue
        w = {"north": x1 - x0, "south": x1 - x0, "east": z1 - z0, "west": z1 - z0, "up": x1 - x0, "down": x1 - x0}[d]
        h = {"north": y1 - y0, "south": y1 - y0, "east": y1 - y0, "west": y1 - y0, "up": z1 - z0, "down": z1 - z0}[d]
        tous[d] = F([0, 0, min(16, w), min(16, h)], t, lit, lueur)
    e = {"from": de, "to": a, "faces": tous}
    if rot:
        e["rotation"] = rot
    return e


TEXTURES = ("raye", "sombre", "olive", "carte", "holo")


def textures():
    return {"raye": rayures(), "sombre": metal((40, 42, 44), 2), "olive": metal((78, 88, 62), 1, rouille=0.3),
            "carte": carte_ecran((60, 200, 230), (8, 20, 30)), "holo": lumiere((70, 210, 255))}


def elements():
    """La table (le modèle du bloc)."""
    return [
        B([0, 0, 0], [16, 2, 16], "raye"),
        B([1, 2, 1], [4, 12, 4], "sombre"), B([12, 2, 1], [15, 12, 4], "sombre"),
        B([1, 2, 12], [4, 12, 15], "sombre"), B([12, 2, 12], [15, 12, 15], "sombre"),
        B([2, 5, 2], [14, 7, 14], "olive"),                          # tablette basse
        B([4, 7, 4], [10, 10, 9], "sombre"),                          # radio
        B([10.5, 7, 6], [11.5, 11.5, 7], "sombre"),                  # antenne de la radio
        B([0, 12, 0], [16, 14, 16], "olive"),                        # plateau
        B([0.5, 14, 0.5], [15.5, 14.3, 15.5], "carte", faces={"up": F([0, 0, 16, 16], "carte", True, (20, 90, 120))}),
        B([5.5, 14.3, 5.5], [10.5, 15.3, 10.5], "sombre"),           # projecteur
        B([7, 15.3, 7], [9, 16, 9], "holo", lit=True, lueur=(40, 140, 200)),
    ]


def hologramme_apercu():
    """L'hologramme fixe, pour l'aperçu (en jeu : BorneHologramme, animé)."""
    els = []
    # hologramme : globe en fil de fer (anneaux et méridiens fins) et épingle rouge au-dessus du point tiré
    L, ep = (30, 110, 160), 0.3
    for y, rr in ((18.5, 3.5), (21, 5.5), (23.5, 6.2), (26, 5.5), (28.5, 3.5)):
        for a in (0, 45):
            r0 = {"origin": [8, y, 8], "axis": "y", "angle": a} if a else None
            for (x0, z0, x1, z1) in ((8 - rr, 8 - rr, 8 + rr, 8 - rr + ep), (8 - rr, 8 + rr - ep, 8 + rr, 8 + rr),
                                     (8 - rr, 8 - rr, 8 - rr + ep, 8 + rr), (8 + rr - ep, 8 - rr, 8 + rr, 8 + rr)):
                els.append(B([x0, y, z0], [x1, y + ep, z1], "holo", lit=True, lueur=L, rot=r0))
    for a in (0, 45, -45, 22.5, -22.5):
        els.append(B([7.85, 16.5, 7.85], [8.15, 30.5, 8.15], "holo", lit=True, lueur=L))
        break
    for a in (0, 45, -45):
        r0 = {"origin": [8, 23.5, 8], "axis": "y", "angle": a} if a else None
        els.append(B([1.8, 23.35, 7.85], [14.2, 23.65, 8.15], "holo", lit=True, lueur=L, rot=r0))
    els.append(B([10.35, 16.5, 6.85], [10.65, 30, 7.15], "rouge", lit=True, lueur=(120, 20, 10)))   # rayon de l'épingle
    els += [B([10, 30, 6.5], [11, 33, 7.5], "rouge", lit=True, lueur=(180, 30, 20)),
            B([9.25, 33, 5.75], [11.75, 35.5, 8.25], "rouge", lit=True, lueur=(200, 40, 25))]
    return els


def modele():
    els = json.loads(json.dumps(elements()))
    for e in els:
        for f in e["faces"].values():
            f.pop("lueur", None)
    tex = {"particle": "heralisz_utils:block/borne_depart_olive"}
    tex.update({n: "heralisz_utils:block/borne_depart_" + n for n in TEXTURES})
    return {"parent": "block/block", "ambientocclusion": False, "textures": tex,
            "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
                        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
                        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
                        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
                        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}},
            "elements": els}


def blockstate():
    v = {}
    for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v["facing=" + d] = {"model": "heralisz_utils:block/borne_depart"} | ({"y": y} if y else {})
    return {"variants": v}



def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--rendus")
    a = ap.parse_args()
    T = textures()
    (ASSETS / "textures/block").mkdir(parents=True, exist_ok=True)
    for vieux in ("corps", "ecran", "lampe"):                     # textures du pupitre d'avant
        (ASSETS / "textures/block" / ("borne_depart_%s.png" % vieux)).unlink(missing_ok=True)
    for n in TEXTURES:
        T[n].save(ASSETS / "textures/block" / ("borne_depart_%s.png" % n))
    for chemin, contenu in ((ASSETS / "models/block/borne_depart.json", modele()),
                            (ASSETS / "models/item/borne_depart.json", {"parent": "heralisz_utils:block/borne_depart"}),
                            (ASSETS / "blockstates/borne_depart.json", blockstate())):
        chemin.parent.mkdir(parents=True, exist_ok=True)
        chemin.write_text(json.dumps(contenu, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print("borne de départ : modèle, %d textures et états écrits dans %s" % (len(TEXTURES), ASSETS))
    if a.rendus:
        d = Path(a.rendus)
        d.mkdir(parents=True, exist_ok=True)
        T["rouge"] = lumiere((255, 60, 45))
        m = {"elements": elements() + hologramme_apercu()}
        vues = [rendu(m, T, -38, 22), rendu(m, T, 0, 12), rendu(m, T, 180 + 40, 22)]
        pl = Image.new("RGB", (900 * 3, 960), (24, 26, 30))
        for i, v in enumerate(vues):
            pl.paste(v, (900 * i, 60))
        dr = ImageDraw.Draw(pl)
        try:
            f = ImageFont.truetype("DejaVuSans-Bold.ttf", 34)
            f2 = ImageFont.truetype("DejaVuSans.ttf", 24)
        except Exception:
            f = f2 = None
        dr.text((24, 12), "Borne de départ : table tactique holographique (hologramme animé en jeu)", fill=(240, 240, 240), font=f)
        for i, t in enumerate(("Trois quarts avant", "Face", "Trois quarts arrière")):
            dr.text((900 * i + 24, 925), t, fill=(190, 190, 190), font=f2)
        pl.save(d / "borne_depart.png")
        print("rendus :", d / "borne_depart.png")


if __name__ == "__main__":
    main()
