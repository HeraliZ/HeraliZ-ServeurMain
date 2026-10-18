"""Mobilier de prison de HeraliZ Décors (18/10, demande du propriétaire : « je fais une prison ») : quatre blocs simples,
façon modèles de la carte.

- `lavabo_sale` : lavabo en porcelaine sur colonne, vasque crasseuse, robinet rouillé (1 bloc) ;
- `toilettes_sales` : cuvette et réservoir en porcelaine sale, abattant relevé, eau trouble, traces de merde (1 bloc) ;
- `lit_superpose` : lit superposé en tubes de métal, deux matelas tachés, échelle (2 blocs de long, 2 de haut au rendu) ;
- `lit_prison` : planche de métal accrochée au mur par deux chaînes, sans matelas (2 blocs le long du mur).

Textures 32 px faites ici (porcelaine sale, eau trouble, métal rouillé, matelas taché) ; chaînes : texture de la chaîne
de Minecraft. Modèles dessinés dos au nord (contre le mur nord), tournés par l'état `facing`.

    python mods/heraliz-decors/outils/prison.py --rendus DOSSIER     (aperçus à valider, rien n'est écrit dans le mod)
    python mods/heraliz-decors/outils/prison.py --installer          (textures, modèles, états dans le mod)
On modifie ce fichier, jamais les fichiers produits.
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ICI = Path(__file__).resolve().parent
A = ICI.parent / "src/client/resources/assets/heraliz_decors"
NS = "heraliz_decors"
T = 32

NOMS = {"lavabo_sale": ("Lavabo sale", "Dirty sink"),
        "toilettes_sales": ("Toilettes sales", "Dirty toilet"),
        "lit_superpose": ("Lit superposé", "Bunk bed"),
        "lit_prison": ("Lit de prison (chaînes)", "Prison bed (chains)")}


# ---------------------------------------------------------------------------------------------- textures
def _rng(g):
    return np.random.default_rng(g)


def _flou(a, n=1):
    for _ in range(n):
        a = (a + np.roll(a, 1, 0) + np.roll(a, -1, 0) + np.roll(a, 1, 1) + np.roll(a, -1, 1)) / 5
    return a


def _img(rgb):
    return Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def porcelaine(g=1, sale=1.0):
    r = _rng(g)
    base = np.ones((T, T, 3)) * np.array([226, 222, 210])
    grain = _flou(r.normal(0, 1, (T, T)), 1)[..., None] * 6
    crasse = np.clip(_flou(r.normal(0, 1, (T, T)), 3) * 3.0, 0, 1)[..., None]       # voile jaunâtre
    rgb = base + grain - crasse * sale * np.array([38, 44, 70])
    for _ in range(int(5 * sale)):                                                   # coulures brunes
        x, y = r.integers(0, T), r.integers(0, T // 2)
        for k in range(r.integers(4, 12)):
            yy = min(T - 1, y + k)
            rgb[yy, x] = rgb[yy, x] * 0.75 + np.array([120, 92, 52]) * 0.25
    for _ in range(3):                                                               # fêlures
        x, y = r.integers(0, T), r.integers(0, T)
        for k in range(r.integers(4, 9)):
            x = int(np.clip(x + r.integers(-1, 2), 0, T - 1))
            y = int(np.clip(y + 1, 0, T - 1))
            rgb[y, x] = np.array([150, 146, 132])
    return _img(rgb)


def eau_sale():
    r = _rng(5)
    v = _flou(r.normal(0, 1, (T, T)), 2)[..., None]
    rgb = np.array([150, 132, 70]) + v * 18
    return _img(rgb)


def merde():
    r = _rng(6)
    v = _flou(r.normal(0, 1, (T, T)), 1)[..., None]
    rgb = np.array([92, 58, 28]) + v * 16
    return _img(rgb)


def cuvette():
    """Dessus de la cuvette : rebord de porcelaine sale, eau trouble au fond, une merde qui flotte."""
    p = np.array(porcelaine(2, 1.2).convert("RGB"), float)
    e = np.array(eau_sale().convert("RGB"), float)
    m = np.array(merde().convert("RGB"), float)
    yy, xx = np.mgrid[0:T, 0:T]
    cx, cy = (T - 1) / 2, T * 0.56
    ov = ((xx - cx) / (T * 0.36)) ** 2 + ((yy - cy) / (T * 0.42)) ** 2
    out = p.copy()
    out[ov < 1.0] = p[ov < 1.0] * 0.55 + np.array([110, 96, 70]) * 0.45                # pente sale
    out[ov < 0.55] = e[ov < 0.55]
    tas = ((xx - cx - 2) / 4.2) ** 2 + ((yy - cy - 1) / 2.6) ** 2 < 1
    out[tas] = m[tas]
    return _img(out)


def vasque():
    """Dessus du lavabo : rebord, cuvette grise de crasse, bonde rouillée, traînées."""
    p = np.array(porcelaine(3, 1.0).convert("RGB"), float)
    yy, xx = np.mgrid[0:T, 0:T]
    cx, cy = (T - 1) / 2, T * 0.58
    ov = ((xx - cx) / (T * 0.38)) ** 2 + ((yy - cy) / (T * 0.32)) ** 2
    out = p.copy()
    out[ov < 1.0] = p[ov < 1.0] * 0.62 + np.array([96, 92, 80]) * 0.38
    bonde = ((xx - cx) ** 2 + (yy - cy) ** 2) < 3.2
    out[bonde] = np.array([70, 52, 36])
    r = _rng(9)
    for _ in range(6):
        x = int(cx + r.integers(-6, 7))
        for k in range(r.integers(3, 8)):
            y = int(cy - 7 + k)
            if 0 <= y < T:
                out[y, x] = out[y, x] * 0.7 + np.array([130, 100, 60]) * 0.3
    return _img(out)


def metal(g=7, teinte=(86, 92, 88), rouille=0.35):
    r = _rng(g)
    rgb = np.ones((T, T, 3)) * np.array(teinte, float)
    rgb += _flou(r.normal(0, 1, (T, T)), 1)[..., None] * 7
    rgb += (np.arange(T)[None, :, None] % 8 == 0) * -6                               # traits de tôle
    taches = np.clip(_flou(r.normal(0, 1, (T, T)), 3) * 3.2 - 0.6, 0, 1)[..., None] * rouille
    rgb = rgb * (1 - taches) + np.array([128, 70, 38]) * taches
    return _img(rgb)


def matelas():
    r = _rng(11)
    yy, xx = np.mgrid[0:T, 0:T]
    rayure = ((xx // 4) % 2)[..., None]
    rgb = np.array([150, 150, 140]) - rayure * np.array([22, 24, 16])
    rgb = rgb + _flou(r.normal(0, 1, (T, T)), 1)[..., None] * 6
    for _ in range(3):                                                               # taches jaunes
        cx, cy, rr = r.integers(4, T - 4), r.integers(4, T - 4), r.integers(3, 7)
        m = ((xx - cx) ** 2 + (yy - cy) ** 2) < rr * rr
        rgb[m] = rgb[m] * 0.7 + np.array([170, 140, 70]) * 0.3
    return _img(rgb)


def couverture():
    r = _rng(12)
    rgb = np.array([88, 92, 84]) + _flou(r.normal(0, 1, (T, T)), 1)[..., None] * 8
    rgb[(np.arange(T) % 16 < 2)[:, None].repeat(T, 1)] -= 14
    return _img(rgb)


def textures():
    return {"porcelaine_sale": porcelaine(1, 1.0), "toilettes_cuvette": cuvette(), "lavabo_vasque": vasque(),
            "merde": merde(), "metal_rouille": metal(7), "tube_peint": metal(8, (70, 86, 72), 0.25),
            "matelas_sale": matelas(), "couverture_grise": couverture()}


# ---------------------------------------------------------------------------------------------- modèles
TOUTES = ("north", "south", "east", "west", "up", "down")


def _uv(de, a, f, echelle=1.0):
    """UV automatiques (projection de la face), en px de texture 16 de Minecraft."""
    x0, y0, z0 = de
    x1, y1, z1 = a
    if f in ("north", "south"):
        u0, u1, v0, v1 = x0, x1, 16 - y1, 16 - y0
    elif f in ("east", "west"):
        u0, u1, v0, v1 = z0, z1, 16 - y1, 16 - y0
    else:
        u0, u1, v0, v1 = x0, x1, z0, z1
    c = lambda t: max(0.0, min(16.0, t % 16 if t > 16 or t < 0 else t))
    u0, u1, v0, v1 = c(u0), c(u1), c(v0), c(v1)
    if u1 <= u0:
        u1 = min(16, u0 + 1)
    if v1 <= v0:
        v1 = min(16, v0 + 1)
    return [round(u0, 3), round(v0, 3), round(u1, 3), round(v1, 3)]


def boite(de, a, tex, faces=TOUTES, rot=None, uvs=None):
    fs = {}
    for f in faces:
        t = tex[f] if isinstance(tex, dict) else tex
        fs[f] = {"uv": (uvs or {}).get(f) or _uv(de, a, f), "texture": "#" + t}
    e = {"from": list(de), "to": list(a), "faces": fs}
    if rot:
        e["rotation"] = rot
    return e


def chaine(x, y0, z0, y1, z1):
    """Chaîne droite de (x, y0, z0) à (x, y1, z1), dans le plan vertical x = cste, à 45° au plus (règle des modèles)."""
    cy, cz = (y0 + y1) / 2, (z0 + z1) / 2
    long = math.hypot(y1 - y0, z1 - z0)
    ang = math.degrees(math.atan2(z1 - z0, y1 - y0))                                  # depuis la verticale
    ang = max(-45, min(45, round(ang / 22.5) * 22.5))
    rot = {"origin": [x, cy, cz], "axis": "x", "angle": -ang}
    h = long / 2
    a = {"from": [x - 1.5, cy - h, cz], "to": [x + 1.5, cy + h, cz],
         "faces": {"north": {"uv": [0, 0, 3, 16], "texture": "#chaine"}, "south": {"uv": [0, 0, 3, 16], "texture": "#chaine"}},
         "rotation": rot, "shade": False}
    b = {"from": [x, cy - h, cz - 1.5], "to": [x, cy + h, cz + 1.5],
         "faces": {"east": {"uv": [3, 0, 6, 16], "texture": "#chaine"}, "west": {"uv": [3, 0, 6, 16], "texture": "#chaine"}},
         "rotation": rot, "shade": False}
    return [a, b]


def m_toilettes():
    P, C, M = "porcelaine", "cuvette", "merde"
    return [
        boite((5, 0, 5), (11, 6, 12), P),                                            # pied
        boite((3.5, 6, 3.5), (12.5, 10, 14), {"north": P, "south": P, "east": P, "west": P, "down": P, "up": C},
              uvs={"up": [0, 0, 16, 16]}),                                            # cuvette
        boite((3, 8, 0), (13, 20, 4), P),                                            # réservoir
        boite((2.5, 20, -0.25), (13.5, 21, 4.5), P),                                 # couvercle
        boite((7, 21, 1.5), (9, 21.5, 3), "metal"),                                  # bouton
        boite((3.75, 10, 4), (12.25, 20.5, 5), P),                                   # abattant relevé
        boite((6, 11, 4.95), (9, 14, 5.05), M, faces=("south",), uvs={"south": [0, 0, 6, 6]}),   # trace sur l'abattant
        boite((10.5, 3, 11.95), (12, 6.5, 12.05), M, faces=("south",), uvs={"south": [0, 0, 3, 7]}),  # coulure du pied
    ]


def m_lavabo():
    P, V = "porcelaine", "vasque"
    return [
        boite((6, 0, 2.5), (10, 11, 6.5), P),                                        # colonne
        boite((2, 11, 0.5), (14, 15, 11), {"north": P, "south": P, "east": P, "west": P, "down": P, "up": V},
              uvs={"up": [0, 0, 16, 16]}),                                            # vasque
        boite((7.25, 15, 1), (8.75, 18.5, 2.5), "metal"),                            # robinet
        boite((7.4, 17.4, 2.5), (8.6, 18.5, 5.5), "metal"),                          # bec
        boite((4.5, 15, 1.2), (6, 16, 2.7), "metal"),                                # poignée froid
        boite((10, 15, 1.2), (11.5, 16, 2.7), "metal"),                              # poignée chaud
        boite((1.5, 15, 0), (14.5, 19, 0.6), P),                                     # dosseret contre le mur
    ]


def m_lit_prison(cote):
    """cote 0 : moitié gauche (vue de face), 1 : moitié droite. Planche le long du mur (axe x), profondeur 12 px."""
    M = "metal"
    els = [
        boite((0, 9, 0.5), (16, 10.5, 12), M),                                       # planche
        boite((0, 10.5, 11), (16, 11.5, 12), M),                                     # rebord avant
        boite((0, 8, 0), (16, 9, 1.5), M),                                           # cornière contre le mur
    ]
    x = 2.5 if cote == 0 else 13.5
    els += [boite((x - 1.5, 21, 0), (x + 1.5, 24, 1), M)]                            # patte au mur
    els += chaine(x, 22.5, 0.8, 10.5, 11.2)                                          # chaîne à 45°
    els += [boite((x - 1, 10.5, 10.2), (x + 1, 11.8, 11.8), M)]                      # anneau d'accroche
    return els


def m_lit_superpose(tete):
    """tete : moitié avec l'oreiller. Bloc de 16 (x) sur 16 (z) ; l'autre moitié est au sud. Hauteur 32 px (2 blocs)."""
    Tb, Ma, Co = "tube", "matelas", "couverture"
    z_mont = 1 if tete else 14                                                       # montants au bout
    els = [
        boite((1, 0, z_mont), (3, 31, z_mont + 1.5), Tb), boite((13, 0, z_mont), (15, 31, z_mont + 1.5), Tb),
        boite((1, 5, 0 if tete else 0), (15, 6.5, 16), Tb, faces=("up", "down", "east", "west")),   # cadre bas
        boite((1, 21, 0), (15, 22.5, 16), Tb, faces=("up", "down", "east", "west")),                # cadre haut
        boite((1.5, 6.5, 0), (14.5, 9.5, 16), Ma),                                   # matelas bas
        boite((1.5, 22.5, 0), (14.5, 25.5, 16), Ma),                                 # matelas haut
        boite((1, 25.5, 0), (2, 28.5, 16), Tb), boite((14, 25.5, 0), (15, 28.5, 16), Tb),          # garde-fous haut
    ]
    if tete:
        els += [boite((1, 5, 1), (15, 15, 2.5), Tb, faces=("north", "south", "up")),                # tête de lit
                boite((3.5, 9.5, 2.5), (12.5, 11.5, 7), Co), boite((3.5, 25.5, 2.5), (12.5, 27.5, 7), Co)]   # oreillers
    else:
        els += [boite((2, 9.5, 6), (14, 10.2, 16), Co), boite((2, 25.5, 6), (14, 26.2, 16), Co)]   # couvertures
        for y in (9, 13, 17, 21):                                                     # échelle au pied
            els.append(boite((3, y, 15.5), (13, y + 1, 16), Tb))
    for e in els:                                                                     # bornes 0..16 en z
        e["from"][2], e["to"][2] = max(0, e["from"][2]), min(16, e["to"][2])
    return els


def modele(nom, elements, textures_map):
    tx = {k: (v if ":" in v else f"{NS}:block/{v}") for k, v in textures_map.items()}
    tx["particle"] = tx[next(iter(textures_map))]
    return {"parent": "block/block", "textures": tx, "elements": elements}


def modeles():
    tp = {"porcelaine": "porcelaine_sale", "cuvette": "toilettes_cuvette", "merde": "merde", "metal": "metal_rouille"}
    lv = {"porcelaine": "porcelaine_sale", "vasque": "lavabo_vasque", "metal": "metal_rouille"}
    lp = {"metal": "metal_rouille", "chaine": "minecraft:block/chain"}
    ls = {"tube": "tube_peint", "matelas": "matelas_sale", "couverture": "couverture_grise"}
    return {
        "toilettes_sales": modele("toilettes_sales", m_toilettes(), tp),
        "lavabo_sale": modele("lavabo_sale", m_lavabo(), lv),
        "lit_prison_gauche": modele("lit_prison_gauche", m_lit_prison(0), lp),
        "lit_prison_droite": modele("lit_prison_droite", m_lit_prison(1), lp),
        "lit_superpose_tete": modele("lit_superpose_tete", m_lit_superpose(True), ls),
        "lit_superpose_pied": modele("lit_superpose_pied", m_lit_superpose(False), ls),
    }


# ---------------------------------------------------------------------------------------------- rendus (aperçus)
sys.path.insert(0, str(ICI.parents[1] / "heralisz-utils" / "outils"))


def _decaler(els, dx=0, dz=0):
    out = json.loads(json.dumps(els))
    for e in out:
        e["from"][0] += dx; e["to"][0] += dx; e["from"][2] += dz; e["to"][2] += dz
        if "rotation" in e:
            e["rotation"]["origin"][0] += dx; e["rotation"]["origin"][2] += dz
    return out


def _murs(dx0, dx1, dz0=-1, dz1=0, h=26):
    return [{"from": [dx0, 0, dz0], "to": [dx1, h, dz1], "faces": {"south": {"uv": [0, 0, 16, 16], "texture": "#mur"},
                                                               "up": {"uv": [0, 0, 16, 1], "texture": "#mur"}}},
            {"from": [dx0, -1, dz0], "to": [dx1, 0, 18], "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#sol"}}}]


def rendus(dossier):
    import borne_depart as BD
    d = Path(dossier)
    d.mkdir(parents=True, exist_ok=True)
    tx = {k: v for k, v in textures().items()}
    texs = {"porcelaine": tx["porcelaine_sale"], "cuvette": tx["toilettes_cuvette"], "merde": tx["merde"],
            "metal": tx["metal_rouille"], "vasque": tx["lavabo_vasque"], "tube": tx["tube_peint"],
            "matelas": tx["matelas_sale"], "couverture": tx["couverture_grise"],
            "chaine": _chaine_vanilla(), "mur": _uni((120, 116, 108)), "sol": _uni((80, 78, 74))}
    # l'aperçu lit les UV en pixels d'une texture de 16 (le jeu, lui, les étire sur toute la texture de 32)
    texs = {k: (v.resize((16, 16), Image.BOX) if v.size[0] > 16 else v) for k, v in texs.items()}
    m = modeles()
    scenes = [
        ("Toilettes sales", m["toilettes_sales"]["elements"] + _murs(-2, 18), 1.0),
        ("Lavabo sale", m["lavabo_sale"]["elements"] + _murs(-2, 18), 1.0),
        ("Lit de prison (2 blocs, chaînes au mur)",
         _decaler(m["lit_prison_gauche"]["elements"], -8) + _decaler(m["lit_prison_droite"]["elements"], 8) + _murs(-10, 26), 0.62),
        ("Lit superposé (2 blocs de long, 2 de haut)",
         _decaler(m["lit_superpose_tete"]["elements"], 0, -8) + _decaler(m["lit_superpose_pied"]["elements"], 0, 8), 0.55),
    ]
    vues = []
    for titre, els, z in scenes:
        mm = {"elements": els}
        a = _rendu_zoom(BD, mm, texs, 180 + 35, 24, z)
        b = _rendu_zoom(BD, mm, texs, 180 - 30, 16, z)
        vues.append((titre, a, b))
    W = 700
    planche = Image.new("RGB", (W * 2, (W + 50) * len(vues)), (24, 26, 30))
    dr = ImageDraw.Draw(planche)
    try:
        from PIL import ImageFont
        f = ImageFont.truetype("DejaVuSans.ttf", 28)
    except Exception:
        f = None
    for i, (titre, a, b) in enumerate(vues):
        y = i * (W + 50)
        dr.text((20, y + 10), titre, fill=(235, 235, 235), font=f)
        planche.paste(a.resize((W, W)), (0, y + 50))
        planche.paste(b.resize((W, W)), (W, y + 50))
    planche.save(d / "prison_apercus.png")
    for k, im in tx.items():
        im.resize((192, 192), Image.NEAREST).save(d / f"texture_{k}.png")
    print("aperçus :", d / "prison_apercus.png")


def _rendu_zoom(BD, m, texs, lacet, tangage, zoom):
    els = json.loads(json.dumps(m["elements"]))
    for e in els:                                                                     # recentrer et réduire pour le cadre
        for k in ("from", "to"):
            e[k] = [8 + (e[k][0] - 8) * zoom, 10 + (e[k][1] - 10) * zoom, 8 + (e[k][2] - 8) * zoom]
        if "rotation" in e:
            o = e["rotation"]["origin"]
            e["rotation"]["origin"] = [8 + (o[0] - 8) * zoom, 10 + (o[1] - 10) * zoom, 8 + (o[2] - 8) * zoom]
    return BD.rendu({"elements": els}, texs, lacet, tangage, taille=900)


def _uni(c):
    r = _rng(sum(c))
    v = _flou(r.normal(0, 1, (T, T)), 1)[..., None] * 6
    return _img(np.array(c, float) + v)


def _chaine_vanilla():
    """Chaîne (pour l'aperçu seulement : en jeu, la texture de Minecraft) : maillons gris sombre."""
    im = Image.new("RGBA", (16, 16), (24, 26, 30, 255))
    dr = ImageDraw.Draw(im)
    for y in range(0, 16, 4):
        dr.rectangle((0, y, 2, y + 2), outline=(70, 74, 82, 255))
        dr.line((4, y + 2, 4, y + 4), fill=(60, 64, 72, 255))
    return im


# ---------------------------------------------------------------------------------------------- installation
ETATS = {"toilettes_sales": None, "lavabo_sale": None, "lit_prison": "cote", "lit_superpose": "partie"}


def installer():
    (A / "textures/block").mkdir(parents=True, exist_ok=True)
    for k, im in textures().items():
        im.save(A / "textures/block" / f"{k}.png")
    for n, m in modeles().items():
        (A / "models/block").mkdir(parents=True, exist_ok=True)
        (A / "models/block" / f"{n}.json").write_text(json.dumps(m, indent=1), encoding="utf-8")
    # facing = côté vers le joueur qui pose (Deco.Oriente) : dos du modèle (dessiné au nord) à l'opposé
    rot = (("south", 0), ("west", 90), ("north", 180), ("east", 270))
    for bloc, prop in ETATS.items():
        v = {}
        for d, y in rot:
            r = {"y": y} if y else {}
            if prop is None:
                v[f"facing={d}"] = {"model": f"{NS}:block/{bloc}"} | r
            elif prop == "cote":
                v[f"cote=gauche,facing={d}"] = {"model": f"{NS}:block/lit_prison_gauche"} | r
                v[f"cote=droite,facing={d}"] = {"model": f"{NS}:block/lit_prison_droite"} | r
            else:
                v[f"facing={d},partie=tete"] = {"model": f"{NS}:block/lit_superpose_tete"} | r
                v[f"facing={d},partie=pied"] = {"model": f"{NS}:block/lit_superpose_pied"} | r
        (A / "blockstates").mkdir(parents=True, exist_ok=True)
        (A / "blockstates" / f"{bloc}.json").write_text(json.dumps({"variants": v}, indent=1), encoding="utf-8")
        item = {"lit_prison": "lit_prison_gauche", "lit_superpose": "lit_superpose_tete"}.get(bloc, bloc)
        (A / "models/item").mkdir(parents=True, exist_ok=True)
        (A / "models/item" / f"{bloc}.json").write_text(json.dumps({"parent": f"{NS}:block/{item}"}, indent=1), encoding="utf-8")
    print("mobilier de prison installé dans", A)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--rendus")
    ap.add_argument("--installer", action="store_true")
    a = ap.parse_args()
    if a.rendus:
        rendus(a.rendus)
    if a.installer:
        installer()
