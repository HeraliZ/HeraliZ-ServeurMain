#!/usr/bin/env python3
"""
Herbe légère HeraliZ : états de bloc et modèles de l'herbe (« grass ») et des hautes herbes (« tall_grass ») de NOTRE pack
heraliz_terrain (posé tout en haut des packs : il passe devant le pack « herbe » d'un autre auteur, qui n'est pas touché).

Pourquoi : les modèles du pack « herbe » font 36 faces par touffe (18 plans, dont 12 qui débordent de 6 pixels chez les
voisins) et jusqu'à 88 pour une haute herbe fleurie : 32,8 faces en moyenne par touffe et 85 par haute herbe, contre 4 et
8 pour Minecraft. Sur une prairie, c'est des centaines de milliers de faces à dessiner.

Ce que fait ce script (jamais de retouche à la main des fichiers écrits) :
- herbe : 3 formes, toutes DANS le bloc (0 à 16 en x et z, 0 à 16 en hauteur, même hauteur qu'avant) :
    « touffe »     6 plans (12 faces) : la croix en diagonale + 4 plans qui partent du milieu et s'ouvrent vers les bords
                   (inclinés de 22,5°, comme une vraie touffe) ;
    « clairsemée » 4 plans (8 faces) : la croix + 2 plans inclinés d'un seul côté (la rotation de l'état de bloc la
                   tourne dans les 4 sens) ;
    « dense »      8 plans (16 faces) : la touffe + 2 plans bas (13 pixels) pour le dessous ;
  avec les 4 textures du pack « herbe » (grass, grass2, grass3, grass4), lues par leur nom, jamais copiées ; 16 tirages
  au hasard (forme, texture, rotation) ; poids touffe 2, clairsemée 1, dense 1 -> 12 faces en moyenne (avant 32,8) ;
- hautes herbes : les deux moitiés ont les mêmes 6 plans (la croix + 4 plans droits en carré, à 3 pixels des bords) pour
  que les tiges se suivent ; haut tiré parmi les 4 textures du pack « herbe » (la 3e avec ses fleurs jaunes : 6 plans de
  plus, sans teinte) -> 12 faces en bas, 15 en haut en moyenne, 27 par plante (avant 85) ;
- type de rendu « cutout_mipped » (Forge, clé render_type) : l'herbe utilise les petites versions de sa texture au loin
  (comme les feuilles de Minecraft) au lieu de scintiller ; avec Embeddium, cutout et cutout_mipped sont dessinés dans
  la même passe : aucun coût en plus. Les textures d'herbe sont tout ou rien (transparence 0 ou 255) : le seuil de
  découpe (0,1 -> 0,5) ne change rien de près ; au loin, Minecraft calcule les petites versions en gardant l'opacité
  (moyenne « gamma ») : les brins s'épaississent un peu au lieu de clignoter.

    python modules/monde/outils/herbe_legere.py                 écrit les fichiers du pack et contrôle (faces, bornes)
    python modules/monde/outils/herbe_legere.py --verifier      contrôle seulement (code 1 si un fichier diffère)
    python modules/monde/outils/herbe_legere.py --apercu F.png  image avant / après (rendu logiciel approché)

Variable HERALIZ_DEPOT : dépôt où lire les textures et le pack « herbe » s'ils manquent à côté du script (copie de travail).
"""
import argparse
import json
import math
import os
import sys
from pathlib import Path

ICI = Path(__file__).resolve().parent
DEPOT = ICI.parents[2]
LECTURE = [DEPOT] + ([Path(os.environ["HERALIZ_DEPOT"])] if os.environ.get("HERALIZ_DEPOT") else [])
TERRAIN = "modules/monde/src/client/resources/resourcepacks/heraliz_terrain"
HERBE = "modules/interface/src/main/resources/resourcepacks/herbe"
TEXTURES = "modules/interface/src/main/resources/resourcepacks/textures"
NS = "heralisz_utils"
DOSSIER_MODELES = "block/herbe"
RENDU = "minecraft:cutout_mipped"

HERBES = ["grass", "grass2", "grass3", "grass4"]                   # textures du pack « herbe » (minecraft:block/…)
HAUTS = ["tall_grass_top", "tall_grass_top2", "tall_grass_top3", "tall_grass_top4"]
BAS = "tall_grass_bottom"
FLEURS = "tall_grass_top3_overlay"


def trouver(rel):
    for d in LECTURE:
        if (d / rel).exists():
            return d / rel
    return None


# ------------------------------------------------------------------ plans
def face(tex, teinte=True):
    f = {"uv": [0, 0, 16, 16], "texture": tex}
    if teinte:
        f["tintindex"] = 0
    return f


def plan_x(x, z0, z1, y0, y1, rot=None, tex="#cross", teinte=True):
    """Plan vertical à x constant (faces est / ouest)."""
    e = {"from": [x, y0, z0], "to": [x, y1, z1], "shade": False,
         "faces": {"east": face(tex, teinte), "west": face(tex, teinte)}}
    if rot:
        e["rotation"] = rot
    return e


def plan_z(z, x0, x1, y0, y1, rot=None, tex="#cross", teinte=True):
    """Plan vertical à z constant (faces nord / sud)."""
    e = {"from": [x0, y0, z], "to": [x1, y1, z], "shade": False,
         "faces": {"north": face(tex, teinte), "south": face(tex, teinte)}}
    if rot:
        e["rotation"] = rot
    return e


def rot(origine, axe, angle):
    return {"origin": origine, "axis": axe, "angle": angle, "rescale": True}


def croix(tex="#cross", teinte=True):
    """La croix de Minecraft : deux plans en diagonale, d'un coin à l'autre, 16 de haut."""
    r = rot([8, 8, 8], "y", 45)
    return [plan_z(8, 0.8, 15.2, 0, 16, r, tex, teinte), plan_x(8, 0.8, 15.2, 0, 16, r, tex, teinte)]


# Plans inclinés de 22,5° qui partent près du milieu et s'ouvrent vers un bord. Avec « rescale », Minecraft les étire de
# 1/cos(22,5°) : le haut arrive à 16 de haut et à 6,6 pixels du pied, donc à 1,9 pixel du bord.
def penche_ouest():
    return plan_x(8.5, 0.8, 15.2, 0, 16, rot([8.5, 0, 8], "z", 22.5))


def penche_est():
    return plan_x(7.5, 0.8, 15.2, 0, 16, rot([7.5, 0, 8], "z", -22.5))


def penche_sud():
    return plan_z(7.5, 0.8, 15.2, 0, 16, rot([8, 0, 7.5], "x", 22.5))


def penche_nord():
    return plan_z(8.5, 0.8, 15.2, 0, 16, rot([8, 0, 8.5], "x", -22.5))


def carre(tex="#cross", teinte=True):
    """4 plans droits en carré, à 3 pixels des bords (hautes herbes : les deux moitiés se suivent)."""
    return [plan_x(3, 1, 15, 0, 16, None, tex, teinte), plan_x(13, 1, 15, 0, 16, None, tex, teinte),
            plan_z(3, 1, 15, 0, 16, None, tex, teinte), plan_z(13, 1, 15, 0, 16, None, tex, teinte)]


def forme(elements, textures):
    m = {"ambientocclusion": False, "render_type": RENDU, "textures": dict(textures, particle="#cross"),
         "elements": elements}
    return m


FORMES = {
    "touffe": forme(croix() + [penche_ouest(), penche_est(), penche_sud(), penche_nord()], {}),
    "clairsemee": forme(croix() + [penche_ouest(), penche_sud()], {}),
    "dense": forme(croix() + [penche_ouest(), penche_est(), penche_sud(), penche_nord(),
                              plan_x(4.5, 0.8, 15.2, 0, 13), plan_z(11.5, 0.8, 15.2, 0, 13)], {}),
    "haute": forme(croix() + carre(), {}),
    "haute_fleurs": forme(croix() + carre() + croix("#overlay", False) + carre("#overlay", False), {}),
}
# Herbe : (forme, poids) ; chaque texture a sa rotation pour la forme penchée d'un côté et la dense.
HERBE_TIRAGES = [("touffe", 0, 1), ("touffe", 180, 1), ("clairsemee", None, 1), ("dense", None, 1)]


def modele(nom):
    return "%s:%s/%s" % (NS, DOSSIER_MODELES, nom)


def fichiers():
    """{chemin relatif au dépôt : contenu json}."""
    out = {}
    base = TERRAIN + "/assets/" + NS + "/models/" + DOSSIER_MODELES + "/"
    for nom, m in FORMES.items():
        out[base + nom + ".json"] = m
    variantes = []
    for i, tex in enumerate(HERBES, 1):
        for f in ("touffe", "clairsemee", "dense"):
            out[base + "%s_%d.json" % (f, i)] = {"parent": modele(f), "textures": {"cross": "minecraft:block/" + tex}}
        for f, y, poids in HERBE_TIRAGES:
            if y is None:
                y = (90 * i) % 360 if f == "clairsemee" else (270 - 90 * i) % 360
            v = {"model": modele("%s_%d" % (f, i)), "weight": poids}
            if y:
                v["y"] = y
            variantes.append(v)
    out[TERRAIN + "/assets/minecraft/blockstates/grass.json"] = {"variants": {"": variantes}}
    out[base + "haute_bas.json"] = {"parent": modele("haute"), "textures": {"cross": "minecraft:block/" + BAS}}
    hauts = []
    for i, tex in enumerate(HAUTS, 1):
        t = {"cross": "minecraft:block/" + tex}
        parent = "haute"
        if tex == "tall_grass_top3":
            t["overlay"] = "minecraft:block/" + FLEURS
            parent = "haute_fleurs"
        out[base + "haute_haut_%d.json" % i] = {"parent": modele(parent), "textures": t}
        v = {"model": modele("haute_haut_%d" % i)}
        if i > 1:
            v["y"] = 90 * (i - 1)
        hauts.append(v)
    out[TERRAIN + "/assets/minecraft/blockstates/tall_grass.json"] = {"variants": {
        "half=lower": [{"model": modele("haute_bas")}, {"model": modele("haute_bas"), "y": 180}],
        "half=upper": hauts}}
    return out


def texte(d):
    return json.dumps(d, indent=1, ensure_ascii=False) + "\n"


# ------------------------------------------------------------------ contrôle : même calcul que FaceBakery (1.20.1)
K = {22.5: 1 / math.cos(math.pi / 8), 45: 1 / math.cos(math.pi / 4)}


def sommets(e):
    a, b = e["from"], e["to"]
    pts = [(x, y, z) for x in (a[0], b[0]) for y in (a[1], b[1]) for z in (a[2], b[2])]
    r = e.get("rotation")
    if not r:
        return pts
    ang = math.radians(r["angle"])
    c, s = math.cos(ang), math.sin(ang)
    o = r["origin"]
    k = K[22.5 if abs(r["angle"]) == 22.5 else 45] if r.get("rescale") else 1.0
    out = []
    for x, y, z in pts:
        x, y, z = x - o[0], y - o[1], z - o[2]
        if r["axis"] == "x":
            y, z = y * c - z * s, y * s + z * c
            sc = (1, k, k)
        elif r["axis"] == "y":
            x, z = x * c + z * s, -x * s + z * c
            sc = (k, 1, k)
        else:
            x, y = x * c - y * s, x * s + y * c
            sc = (k, k, 1)
        out.append((x * sc[0] + o[0], y * sc[1] + o[1], z * sc[2] + o[2]))
    return out


def lire_json(p):
    # quelques fichiers du pack « herbe » ont une accolade de trop à la fin : Minecraft lit le premier objet
    return json.JSONDecoder().raw_decode(p.read_text(encoding="utf-8").lstrip())[0]


def charger_modele(loc, nos):
    ns, p = loc.split(":", 1) if ":" in loc else ("minecraft", loc)
    for rel in [TERRAIN, HERBE, TEXTURES]:
        chemin = rel + "/assets/" + ns + "/models/" + p + ".json"
        if chemin in nos:
            return nos[chemin]
        f = trouver(chemin)
        if f:
            return lire_json(f)
    raise SystemExit("modèle introuvable : " + loc)


def elements_de(loc, nos):
    m = charger_modele(loc, nos)
    while "elements" not in m:
        m = charger_modele(m["parent"], nos)
    return m["elements"]


def moyenne(etat, nos):
    """Faces moyennes par variante tirée, boîte englobante de toutes les variantes."""
    res = {}
    for clef, v in etat["variants"].items():
        lst = v if isinstance(v, list) else [v]
        tot = sum(x.get("weight", 1) for x in lst)
        n, mn, mx = 0.0, [99] * 3, [-99] * 3
        for x in lst:
            els = elements_de(x["model"], nos)
            n += sum(len(e["faces"]) for e in els) * x.get("weight", 1) / tot
            for e in els:
                for p in sommets(e):
                    for i in range(3):
                        mn[i] = min(mn[i], p[i])
                        mx[i] = max(mx[i], p[i])
        res[clef] = (n, mn, mx)
    return res


def controler(nos):
    ok = True
    print("faces par touffe (moyenne des tirages) et boîte englobante (pixels, bloc = 0..16) :")
    for bloc in ("grass", "tall_grass"):
        av = trouver(HERBE + "/assets/minecraft/blockstates/" + bloc + ".json")
        apres = nos[TERRAIN + "/assets/minecraft/blockstates/" + bloc + ".json"]
        ra = moyenne(lire_json(av), {}) if av else {}
        rb = moyenne(apres, nos)
        for clef in rb:
            n0 = ra.get(clef, (float("nan"),))[0]
            n1, mn, mx = rb[clef]
            dans = all(-0.01 <= mn[i] and mx[i] <= 16.01 for i in (0, 2)) and mn[1] >= -0.01 and mx[1] <= 16.05
            ok &= dans
            print("  %-10s %-11s avant %5.1f  après %5.1f   x %5.2f..%5.2f  y %5.2f..%5.2f  z %5.2f..%5.2f  %s"
                  % (bloc, clef or "(seul)", n0, n1, mn[0], mx[0], mn[1], mx[1], mn[2], mx[2],
                     "dans le bloc" if dans else "DÉBORDE"))
            if av and clef in ra:
                _, mn0, mx0 = ra[clef]
                print("  %-10s %-11s avant : x %5.2f..%5.2f  y %5.2f..%5.2f" % ("", "", mn0[0], mx0[0], mn0[1], mx0[1]))
    for f in HERBES + HAUTS + [BAS, FLEURS]:
        if not trouver(HERBE + "/assets/minecraft/textures/block/" + f + ".png"):
            print("  texture absente du pack « herbe » :", f)
            ok = False
    return ok


# ------------------------------------------------------------------ aperçu (rendu logiciel approché, peintre)
def apercu(nos, sortie):
    import numpy as np
    from PIL import Image, ImageDraw, ImageFont
    try:
        police = ImageFont.truetype("arial.ttf", 16)
    except OSError:
        police = ImageFont.load_default()

    def tex(nom):
        for rel in [TERRAIN, HERBE, TEXTURES]:
            f = trouver(rel + "/assets/minecraft/textures/block/" + nom + ".png")
            if f:
                im = Image.open(f).convert("RGBA")
                return im.crop((0, 0, im.width, im.width))           # 1re image d'une animation
        raise SystemExit("texture introuvable : " + nom)

    TEINTE = (145, 169, 145)                                         # colormap du pack, plaines

    def teinter(im):
        a = np.asarray(im).astype(np.float32)
        a[..., :3] *= np.array(TEINTE, np.float32) / 255
        return Image.fromarray(a.clip(0, 255).astype(np.uint8))

    W, H = 900, 520
    N = 12                                                           # champ de N x N blocs
    cam = np.array([-1.5, 1.62, -1.5])                               # yeux d'un joueur debout, au coin du champ
    cible = np.array([N * 0.62, 0.15, N * 0.62])
    av = cible - cam
    av /= np.linalg.norm(av)
    dr = np.cross(av, [0, 1, 0])
    dr /= np.linalg.norm(dr)
    ht = np.cross(dr, av)
    foc = 640.0

    def proj(p):
        v = np.asarray(p) - cam
        z = v @ av
        return (W / 2 + foc * (v @ dr) / z, H / 2 - foc * (v @ ht) / z, z)

    def quads_modele(loc, y_rot, ox, oy, oz, textures_vues):
        m = charger_modele(loc, nos)
        texmap = dict(m.get("textures", {}))
        mm = m
        while "elements" not in mm:
            mm = charger_modele(mm["parent"], nos)
            for k, v in mm.get("textures", {}).items():
                texmap.setdefault(k, v)
        out = []
        for e in mm["elements"]:
            for nomf, f in e["faces"].items():
                t = f["texture"]
                while t.startswith("#"):
                    t = texmap[t[1:]]
                nom = t.split("/")[-1]
                a, b = e["from"], e["to"]
                # coins du plan (sans épaisseur) : haut-gauche, haut-droit, bas-droit, bas-gauche, puis la rotation
                ordre = []
                for c in [(a[0], b[1], a[2]), (b[0], b[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], a[2])]:
                    ordre.append(sommets({"from": list(c), "to": list(c), "rotation": e.get("rotation")})[0])
                q = []
                for x, y, z in ordre:
                    x, z = x - 8, z - 8
                    ang = math.radians(-y_rot)
                    x, z = x * math.cos(ang) - z * math.sin(ang), x * math.sin(ang) + z * math.cos(ang)
                    q.append((ox + (x + 8) / 16, oy + y / 16, oz + (z + 8) / 16))
                im = textures_vues.setdefault((nom, f.get("tintindex") is not None),
                                              teinter(tex(nom)) if f.get("tintindex") is not None else tex(nom))
                out.append((q, im))
        return out

    def liste(v):
        return v if isinstance(v, list) else [v]

    def scene(etats, decal):
        textures_vues = {}
        quads = []
        import random
        rnd = random.Random(7)
        for gx in range(N):
            for gz in range(N):
                ox, oz = gx + decal, gz
                quads.append(([(ox, 0, oz), (ox + 1, 0, oz), (ox + 1, 0, oz + 1), (ox, 0, oz + 1)], "sol"))
                if (gx + gz) % 5 == 4:
                    lo = rnd.choice(liste(etats["tall_grass"]["variants"]["half=lower"]))
                    hi = rnd.choice(liste(etats["tall_grass"]["variants"]["half=upper"]))
                    quads += quads_modele(lo["model"], lo.get("y", 0), ox, 0, oz, textures_vues)
                    quads += quads_modele(hi["model"], hi.get("y", 0), ox, 1, oz, textures_vues)
                else:
                    v = etats["grass"]["variants"][""]
                    v = v if isinstance(v, list) else [v]
                    tot = sum(x.get("weight", 1) for x in v)
                    r = rnd.uniform(0, tot)
                    for x in v:
                        r -= x.get("weight", 1)
                        if r <= 0:
                            break
                    quads += quads_modele(x["model"], x.get("y", 0), ox, 0, oz, textures_vues)
        return quads

    def charger_etat(bloc, depuis_herbe):
        if depuis_herbe:
            return lire_json(trouver(HERBE + "/assets/minecraft/blockstates/" + bloc + ".json"))
        return nos[TERRAIN + "/assets/minecraft/blockstates/" + bloc + ".json"]

    sol = teinter(tex("grass_block_top")).convert("RGBA")
    img = Image.new("RGBA", (W * 2 + 10, H + 30), (126, 150, 172, 255))
    d = ImageDraw.Draw(img)
    for k, (titre, herbe) in enumerate([("AVANT : pack herbe (32,8 / 85 faces)", True),
                                        ("APRÈS : herbe légère HeraliZ (12 / 27 faces)", False)]):
        etats = {b: charger_etat(b, herbe) for b in ("grass", "tall_grass")}
        quads = scene(etats, 0)
        cadre = Image.new("RGBA", (W, H), (126, 150, 172, 255))
        quads.sort(key=lambda q: -sum(proj(p)[2] for p in q[0]) / 4)
        for q, im in quads:
            if im == "sol":
                im = sol
            pts = [proj(p) for p in q]
            if min(p[2] for p in pts) <= 0.1:
                continue
            x0 = max(0, int(min(p[0] for p in pts)))
            y0 = max(0, int(min(p[1] for p in pts)))
            x1 = min(W, int(max(p[0] for p in pts)) + 2)
            y1 = min(H, int(max(p[1] for p in pts)) + 2)
            if x1 <= x0 or y1 <= y0:
                continue
            dst = [(p[0] - x0, p[1] - y0) for p in pts]                 # repère du petit cadre autour de la face
            tw, th = im.size
            src = [(0, 0), (tw, 0), (tw, th), (0, th)]
            # homographie : point du cadre -> point de la texture
            A, B = [], []
            for (x, y), (u, v) in zip(dst, src):
                A.append([x, y, 1, 0, 0, 0, -u * x, -u * y]); B.append(u)
                A.append([0, 0, 0, x, y, 1, -v * x, -v * y]); B.append(v)
            try:
                co = np.linalg.solve(np.array(A, float), np.array(B, float))
            except np.linalg.LinAlgError:
                continue
            w, h = x1 - x0, y1 - y0
            calque = im.transform((w, h), Image.PERSPECTIVE, tuple(co), Image.NEAREST)
            masque = Image.new("L", (w, h), 0)
            ImageDraw.Draw(masque).polygon(dst, fill=255)
            alpha = np.minimum(np.asarray(calque)[..., 3], np.asarray(masque))
            alpha = np.where(alpha >= 128, 255, 0).astype(np.uint8)      # découpe nette, comme le jeu
            calque.putalpha(Image.fromarray(alpha))
            cadre.alpha_composite(calque, (x0, y0))
        img.paste(cadre, (k * (W + 10), 30))
        d.text((k * (W + 10) + 8, 6), titre, fill=(20, 20, 20, 255), font=police)
    img.convert("RGB").save(sortie)
    print("aperçu :", sortie)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--verifier", action="store_true")
    ap.add_argument("--apercu", type=Path)
    a = ap.parse_args()
    nos = fichiers()
    ok = controler(nos)
    if a.apercu:
        apercu(nos, a.apercu)
    differents = []
    for rel, d in nos.items():
        f = DEPOT / rel
        if not f.exists() or f.read_text(encoding="utf-8") != texte(d):
            differents.append(rel)
    if a.verifier:
        for rel in differents:
            print("à refaire :", rel)
        sys.exit(0 if ok and not differents else 1)
    if not ok:
        sys.exit("contrôle raté : rien n'est écrit")
    for rel in differents:
        f = DEPOT / rel
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(texte(nos[rel]), encoding="utf-8")
    print("%d fichiers écrits (%d déjà à jour)" % (len(differents), len(nos) - len(differents)))


if __name__ == "__main__":
    main()
