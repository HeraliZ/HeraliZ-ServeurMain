#!/usr/bin/env python3
"""
Prépare les modèles de l'aperçu des jumelles (rendus hors jeu, à faire valider avant de toucher au mod) :
les jumelles (geo/designator.geo, chargé comme GeoLoader / GeoPart du mod), les cartouches (modèles d'objet
payload_*.json) et le joueur (PlayerModel du jeu : tête, corps, bras, jambes et leurs calques, bras larges et fins).

usage : python3 prep.py <dossier de sortie> [skin 64x64 .png] [--apres <designator.geo> <designator.png>]
        [--cartouches <dossier des cartridge_*.json et .png proposés>]
  -> scene.json + les textures copiées (designator.png, designator_display.png, payload_*.png, skin.png)
Le skin par défaut est celui de Steve du pack de textures du serveur (heraliz-interface).
"""
import json
import math
import shutil
import sys
from pathlib import Path

ICI = Path(__file__).resolve().parent
MOD = ICI.parents[1]
DEPOT = MOD.parents[1]
ASSETS = MOD / "src" / "client" / "resources" / "assets" / "airstrike"
SKIN = DEPOT / "mods" / "heraliz-interface" / "src" / "main" / "resources" / "resourcepacks" / "textures" / "assets" \
    / "minecraft" / "textures" / "entity" / "player" / "wide" / "steve.png"


# ------------------------------------------------------------------ matrices (lignes, 4 x 4)
def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def ident():
    return [[1.0 if i == j else 0.0 for j in range(4)] for i in range(4)]


def tra(x, y, z):
    m = ident()
    m[0][3], m[1][3], m[2][3] = x, y, z
    return m


def rot(axe, a):
    c, s = math.cos(a), math.sin(a)
    m = ident()
    if axe == "x":
        m[1][1], m[1][2], m[2][1], m[2][2] = c, -s, s, c
    elif axe == "y":
        m[0][0], m[0][2], m[2][0], m[2][2] = c, s, -s, c
    else:
        m[0][0], m[0][1], m[1][0], m[1][1] = c, -s, s, c
    return m


def app(m, p):
    return [m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3)]


def app_dir(m, p):
    return [m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] for i in range(3)]


# ------------------------------------------------------------------ boîtes (même découpe que ModelPart.Cube)
def boite(x0, y0, z0, w, h, d, grow, uvs, tw, th, inset=0.0, miroir=False):
    """6 faces (bas, haut, ouest, nord, est, sud) ; uvs : (u1, v1, u2, v2) en texels pour chaque face."""
    x1, y1, z1 = x0 + w + grow, y0 + h + grow, z0 + d + grow
    x0, y0, z0 = x0 - grow, y0 - grow, z0 - grow
    if miroir:
        x0, x1 = x1, x0
    v7, vA, v1, v2 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    v3, v4, v5, v6 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
    faces = [(v4, v3, v7, vA), (v1, v2, v6, v5), (v7, v3, v6, v2), (vA, v7, v2, v1), (v4, vA, v1, v5), (v3, v4, v5, v6)]
    normales = [(0, -1, 0), (0, 1, 0), (-1, 0, 0), (0, 0, -1), (1, 0, 0), (0, 0, 1)]
    out = []
    for f in range(6):
        u1, vv1, u2, vv2 = uvs[f * 4:f * 4 + 4]
        if inset:
            def ins(a, b, premier):
                e = min(inset, abs(b - a) * 0.25)
                if a <= b:
                    return a + e if premier else b - e
                return a - e if premier else b + e
            u1, u2 = ins(uvs[f * 4], uvs[f * 4 + 2], True), ins(uvs[f * 4], uvs[f * 4 + 2], False)
            vv1, vv2 = ins(uvs[f * 4 + 1], uvs[f * 4 + 3], True), ins(uvs[f * 4 + 1], uvs[f * 4 + 3], False)
        uv = [(u2 / tw, vv1 / th), (u1 / tw, vv1 / th), (u1 / tw, vv2 / th), (u2 / tw, vv2 / th)]
        sommets = [list(faces[f][i]) + list(uv[i]) for i in range(4)]
        n = list(normales[f])
        if miroir:
            sommets = sommets[::-1]
            n[0] = -n[0]
        out.append((sommets, n))
    return out


def uv_vanilla(u, v, w, h, d):
    """UV d'une boîte de ModelPart (texOffs u, v) : bas, haut, ouest, nord, est, sud."""
    f4, f5, f6, f7 = u, u + d, u + d + w, u + d + w + w
    f8, f9 = u + d + w + d, u + d + w + d + w
    f10, f11, f12 = v, v + d, v + d + h
    return [f5, f10, f6, f11, f6, f11, f7, f10, f4, f11, f5, f12, f5, f11, f6, f12, f6, f11, f8, f12, f8, f11, f9, f12]


class Maillage:
    def __init__(self):
        self.pos, self.nrm, self.uv = [], [], []

    def quad(self, m, sommets, n):
        nm = app_dir(m, n)
        l = math.sqrt(sum(c * c for c in nm)) or 1.0
        nm = [c / l for c in nm]
        for tri in ((0, 1, 2), (0, 2, 3)):
            for i in tri:
                self.pos += app(m, sommets[i][:3])
                self.nrm += nm
                self.uv += sommets[i][3:5]

    def quad_lisse(self, m, sommets, tw, th):
        """Quad à normales par sommet (x, y, z, u, v en texels, nx, ny, nz)."""
        for tri in ((0, 1, 2), (0, 2, 3)):
            for i in tri:
                s = sommets[i]
                n = app_dir(m, s[5:8])
                l = math.sqrt(sum(c * c for c in n)) or 1.0
                self.pos += app(m, s[0:3])
                self.nrm += [c / l for c in n]
                self.uv += [s[3] / tw, s[4] / th]

    def json(self):
        r = lambda l: [round(x, 5) for x in l]
        return {"pos": r(self.pos), "nrm": r(self.nrm), "uv": r(self.uv)}


# ------------------------------------------------------------------ jumelles (.geo)
def jumelles(geo=None):
    tw = th = 64
    passes = {}
    pieces = {}           # chemin -> (parent, x, y, z, rx, ry, rz)
    quads = {}            # chemin -> [quads lisses : 4 x (x, y, z, u, v, nx, ny, nz)]
    boites = {}           # chemin -> [boîtes]
    courant = None
    lignes = Path(geo or ASSETS / "geo" / "designator.geo").read_text(encoding="utf-8").splitlines()
    for l in lignes:
        t = l.split()
        if len(t) >= 3 and t[0] == "texture":
            tw, th = int(t[1]), int(t[2])
    for l in lignes:
        t = l.strip().split()
        if not t or t[0].startswith("#"):
            continue
        if t[0] == "pass":
            passes[t[1]] = t[2]
        elif t[0] == "part":
            courant = t[1]
            pieces[courant] = [float(x) for x in t[2:8]]
            boites[courant] = []
        elif t[0] == "box" and courant:
            boites[courant].append([float(x) for x in t[1:32]])
        elif t[0] == "quad" and courant:
            quads.setdefault(courant, []).append([float(x) for x in t[1:33]])

    def matrice(chemin):
        """Repère d'une pièce dans celui de sa pièce de premier niveau (GeoPart.translateAndRotate)."""
        m = ident()
        morceaux = chemin.split("/")
        for k in range(2, len(morceaux) + 1):
            x, y, z, rx, ry, rz = pieces["/".join(morceaux[:k])]
            m = mul(m, tra(x, y, z))
            m = mul(m, mul(rot("z", rz), mul(rot("y", ry), rot("x", rx))))
        return m

    groupes = {}
    for chemin, liste in boites.items():
        haut = chemin.split("/")[0]
        g = groupes.setdefault(haut, Maillage())
        m = matrice(chemin)
        for b in liste:
            for sommets, n in boite(*b[0:7], b[7:31], tw, th, inset=0.035):
                g.quad(m, sommets, n)
    for chemin, liste in quads.items():
        haut = chemin.split("/")[0]
        g = groupes.setdefault(haut, Maillage())
        m = matrice(chemin)
        for q in liste:
            g.quad_lisse(m, [q[i * 8:i * 8 + 8] for i in range(4)], tw, th)
    out = {}
    for haut, g in groupes.items():
        x, y, z, rx, ry, rz = pieces[haut]
        d = g.json()
        d.update({"pivot": [x, y, z], "rot": [rx, ry, rz], "passe": passes.get(haut, "solid")})
        out[haut] = d
    return out


# ------------------------------------------------------------------ cartouches (modèle d'objet à éléments)
FACES = {
    "down": ((0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0), (0, -1, 0)),
    "up": ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1), (0, 1, 0)),
    "north": ((1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0), (0, 0, -1)),
    "south": ((0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1), (0, 0, 1)),
    "west": ((0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0), (-1, 0, 0)),
    "east": ((1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1), (1, 0, 0)),
}


def cartouche(nom, dossier=None):
    """Modèle d'objet à éléments ; les éléments lumineux (forge_data) à part, dans « <nom>_lumineux »."""
    d = json.loads(((dossier or ASSETS / "models" / "item") / (nom + ".json")).read_text(encoding="utf-8"))
    g, lum = Maillage(), Maillage()
    for e in d["elements"]:
        cible = lum if "forge_data" in e else g
        a, b = e["from"], e["to"]
        m = ident()
        r = e.get("rotation")
        if r:
            o = r["origin"]
            m = mul(tra(*o), mul(rot(r["axis"], math.radians(r["angle"])), tra(-o[0], -o[1], -o[2])))
        for face, f in e["faces"].items():
            c0, c1, c2, c3, n = FACES[face]
            u1, v1, u2, v2 = f["uv"]
            uv = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
            k = (f.get("rotation", 0) // 90) % 4
            uv = uv[k:] + uv[:k]
            sommets = []
            for i, c in enumerate((c0, c1, c2, c3)):
                p = [a[j] + (b[j] - a[j]) * c[j] for j in range(3)]
                sommets.append(p + [uv[i][0] / 16.0, uv[i][1] / 16.0])
            # coins listés dans le sens des aiguilles vu de dehors : inversés pour que la face soit vue de dehors
            cible.quad(m, sommets[::-1], n)
    return g.json(), (lum.json() if lum.pos else None)


# ------------------------------------------------------------------ joueur (PlayerModel)
def joueur(fin):
    """Pièces du joueur : (pivot, boîte, texOffs, gonflement). Bras fins : 3 de large."""
    lb = 3 if fin else 4
    pieces = {
        "head": ((0, 0, 0), (-4, -8, -4, 8, 8, 8), (0, 0), 0.0),
        "hat": ((0, 0, 0), (-4, -8, -4, 8, 8, 8), (32, 0), 0.5),
        "body": ((0, 0, 0), (-4, 0, -2, 8, 12, 4), (16, 16), 0.0),
        "jacket": ((0, 0, 0), (-4, 0, -2, 8, 12, 4), (16, 32), 0.25),
        "right_arm": ((-5, 2.5 if fin else 2, 0), (1 - lb, -2, -2, lb, 12, 4), (40, 16), 0.0),
        "right_sleeve": ((-5, 2.5 if fin else 2, 0), (1 - lb, -2, -2, lb, 12, 4), (40, 32), 0.25),
        "left_arm": ((5, 2.5 if fin else 2, 0), (-1, -2, -2, lb, 12, 4), (32, 48), 0.0),
        "left_sleeve": ((5, 2.5 if fin else 2, 0), (-1, -2, -2, lb, 12, 4), (48, 48), 0.25),
        "right_leg": ((-1.9, 12, 0), (-2, 0, -2, 4, 12, 4), (0, 16), 0.0),
        "right_pants": ((-1.9, 12, 0), (-2, 0, -2, 4, 12, 4), (0, 32), 0.25),
        "left_leg": ((1.9, 12, 0), (-2, 0, -2, 4, 12, 4), (16, 48), 0.0),
        "left_pants": ((1.9, 12, 0), (-2, 0, -2, 4, 12, 4), (0, 48), 0.25),
    }
    out = {}
    for nom, (pivot, (x, y, z, w, h, d), (u, v), grow) in pieces.items():
        g = Maillage()
        for sommets, n in boite(x, y, z, w, h, d, grow, uv_vanilla(u, v, w, h, d), 64, 64):
            g.quad(ident(), sommets, n)
        js = g.json()
        js["pivot"] = list(pivot)
        js["boite"] = [x, y, z, w, h, d]
        out[nom] = js
    return out


def main():
    args = sys.argv[1:]
    apres = None
    if "--apres" in args:
        i = args.index("--apres")
        apres = args[i + 1:i + 3]
        args = args[:i] + args[i + 3:]
    neuves = None
    if "--cartouches" in args:
        i = args.index("--cartouches")
        neuves = Path(args[i + 1])
        args = args[:i] + args[i + 2:]
    dossier = Path(args[0])
    skin = Path(args[1]) if len(args) > 1 else SKIN
    dossier.mkdir(parents=True, exist_ok=True)
    tex = ASSETS / "textures"
    shutil.copyfile(tex / "entity" / "designator.png", dossier / "designator.png")
    shutil.copyfile(tex / "entity" / "designator_display.png", dossier / "designator_display.png")
    cartouches = {}

    def ajouter(f, textures):
        maillage, lumineux = cartouche(f.stem, f.parent)
        shutil.copyfile(textures / (f.stem + ".png"), dossier / (f.stem + ".png"))
        cartouches[f.stem] = maillage
        if lumineux:
            cartouches[f.stem + "_lumineux"] = lumineux

    for f in sorted((ASSETS / "models" / "item").glob("payload_*.json")):
        ajouter(f, tex / "item")
    # cartouches par niveau (cartridge_light, _heavy, _special) : celles du mod, ou celles proposées (--cartouches)
    for f in sorted((neuves or ASSETS / "models" / "item").glob("cartridge_*.json")):
        ajouter(f, neuves or tex / "item")
    shutil.copyfile(skin, dossier / "skin.png")
    scene = {"jumelles": jumelles(), "cartouches": cartouches, "joueur": joueur(False), "joueur_fin": joueur(True)}
    if apres:
        # modèle proposé (jumelles.py écrit ailleurs que dans le mod) : dessiné par la version « apres »
        scene["apres"] = jumelles(apres[0])
        shutil.copyfile(apres[1], dossier / "apres.png")
    (dossier / "scene.json").write_text(json.dumps(scene), encoding="utf-8")
    n = sum(len(g["pos"]) // 9 for g in scene["jumelles"].values())
    print("jumelles : %d triangles ; %d cartouches ; joueur prêt -> %s" % (n, len(cartouches), dossier))


if __name__ == "__main__":
    main()
