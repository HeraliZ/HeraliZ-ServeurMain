#!/usr/bin/env python3
"""Rendus hors du jeu d'un modèle .geo des frappes aériennes (images à faire valider avant de toucher au mod).

    python modules/monde/outils/modele/rendu_geo.py <modele.geo> <texture.png> <sortie.png> [options]

Options : --lacet D (degrés, 0 = nez vers le nord), --tangage D, --roulis D, --cam AZIMUT HAUTEUR DISTANCE (degrés,
degrés, blocs, autour du centre du modèle), --cible X Y Z (blocs, repère du modèle), --focale D (champ vertical),
--taille L H, --rotor D (angle des pales), --flou (disques flous des rotors au lieu des pales), --sol (sol herbeux
sous le modèle), --altitude A (hauteur du modèle au-dessus du sol), --nuit, --cache partie,partie.

Le chemin de dessin du jeu est reproduit : parties et rotations comme GeoPart (rotationZYX), repère du modèle retourné
(scale(-1, -1, 1) du rendu), éclairage des entités de Minecraft (deux lumières fixes, 0,4 d'ambiance, par sommet),
textures en « plus proche voisin », passes solid (découpe à alpha 0,1), glass (transparence triée), emissive (sans
éclairage). Ce qui diffère du jeu : pas de lumière du monde ni de brouillard de Minecraft, décor simple. Toujours dire
au propriétaire que ce n'est pas testé en jeu.
"""
import argparse
import math

import numpy as np
from PIL import Image

L0 = np.array([0.2, 1.0, -0.7])
L1 = np.array([-0.2, 1.0, 0.7])
L0 /= np.linalg.norm(L0)
L1 /= np.linalg.norm(L1)


def rot_zyx(rz, ry, rx):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    mx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    my = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    mz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return mz @ my @ mx


def lire(geo):
    parts, ordre, passes, points = {}, [], {}, {}
    tex = (64, 64)
    courant = None
    for l in open(geo, encoding="utf-8"):
        t = l.split()
        if not t or t[0].startswith("#"):
            continue
        if t[0] == "texture":
            tex = (float(t[1]), float(t[2]))
        elif t[0] == "pass":
            passes[t[1]] = t[2]
        elif t[0] == "point":
            points[t[1]] = np.array([float(a) for a in t[2:5]]) / 16
        elif t[0] == "part":
            courant = t[1]
            parts[courant] = dict(pos=np.array([float(a) for a in t[2:5]]), rot=[float(a) for a in t[5:8]], quads=[])
            ordre.append(courant)
        elif t[0] == "quad" and courant:
            v = np.array([float(a) for a in t[1:33]]).reshape(4, 8)
            parts[courant]["quads"].append(v)
    return parts, ordre, passes, points, tex


def assembler(parts, ordre, passes, tex, anim, cache):
    """Sommets du modèle dans le repère naturel (blocs) : liste (passe, P 4x3, UV 4x2, N 4x3)."""
    W, H = tex
    mondes = {}
    sortie = []
    for p in ordre:
        haut = p.split("/")[0]
        if haut in cache:
            continue
        parent = p.rsplit("/", 1)[0] if "/" in p else None
        Mp, tp = mondes.get(parent, (np.eye(3), np.zeros(3)))
        rx, ry, rz = parts[p]["rot"]
        if p in anim:
            ax, val = anim[p]
            if ax == "x":
                rx += val
            elif ax == "y":
                ry += val
            else:
                rz += val
        R = rot_zyx(rz, ry, rx)
        t = tp + Mp @ (parts[p]["pos"] / 16)
        M = Mp @ R
        mondes[p] = (M, t)
        passe = passes.get(haut, "solid")
        for q in parts[p]["quads"]:
            P = (q[:, :3] / 16) @ M.T + t
            N = q[:, 5:8] @ M.T
            # repère du jeu -> repère naturel (scale(-1, -1, 1) du rendu)
            P = P * [-1, -1, 1]
            N = N * [-1, -1, 1]
            UV = q[:, 3:5] / [W, H]
            sortie.append((passe, P, UV, N))
    return sortie


def matrice_entite(lacet, tangage, roulis):
    # comme StrikeRenderer : Y(180 - lacet), X(-tangage), Z(-roulis), puis le retournement (déjà fait)
    a = math.radians(180 - lacet)
    ry = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
    b = math.radians(-tangage)
    rx = np.array([[1, 0, 0], [0, math.cos(b), -math.sin(b)], [0, math.sin(b), math.cos(b)]])
    c = math.radians(-roulis)
    rz = np.array([[math.cos(c), -math.sin(c), 0], [math.sin(c), math.cos(c), 0], [0, 0, 1]])
    # le retournement est fait dans assembler : on ramène le modèle naturel (nez -z) dans le repère du rendu du jeu
    flip = np.diag([-1, -1, 1])
    return ry @ rx @ rz @ flip @ flip


def echantillon(texture, uv):
    h, w = texture.shape[:2]
    x = np.clip((uv[..., 0] * w).astype(int), 0, w - 1)
    y = np.clip((uv[..., 1] * h).astype(int), 0, h - 1)
    return texture[y, x]


def rendre(tris, texture, cam_pos, cible, focale, L, H, fond, sol=None, nuit=False):
    """tris : (passe, P monde 4x3, UV 4x2, N monde 4x3)."""
    texture = texture.astype(np.float32)
    avant = cible - cam_pos
    avant /= np.linalg.norm(avant)
    droite = np.cross(avant, [0, 1, 0])
    droite /= np.linalg.norm(droite)
    haut = np.cross(droite, avant)
    f = 0.5 * H / math.tan(math.radians(focale) / 2)
    img = fond.astype(np.float32).copy()
    zbuf = np.full((H, W_ := L), np.inf, np.float32)
    lum_amb = 0.55 if nuit else 1.0

    def projeter(P):
        d = P - cam_pos
        x, y, z = d @ droite, d @ haut, d @ avant
        return np.stack([L / 2 + f * x / np.maximum(z, 1e-4), H / 2 - f * y / np.maximum(z, 1e-4), z], axis=-1)

    def tri(p, uv, col, alpha_mode, emis=False):
        # p : 3 x 3 (x écran, y écran, z caméra) ; uv 3 x 2 ; col 3 x 3 (éclairage par sommet)
        if np.any(p[:, 2] < 0.05):
            return
        x0, x1 = int(max(0, math.floor(p[:, 0].min()))), int(min(L - 1, math.ceil(p[:, 0].max())))
        y0, y1 = int(max(0, math.floor(p[:, 1].min()))), int(min(H - 1, math.ceil(p[:, 1].max())))
        if x1 < x0 or y1 < y0:
            return
        (ax, ay, _), (bx, by, _), (cx, cy, _) = p
        aire = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        if abs(aire) < 1e-9:
            return
        if aire > 0 and alpha_mode != "double":   # face vue de dos (le jeu cache les faces arrière)
            return
        ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1].astype(np.float32) + 0.5
        w0 = ((bx - xs) * (cy - ys) - (by - ys) * (cx - xs)) / aire
        w1 = ((cx - xs) * (ay - ys) - (cy - ys) * (ax - xs)) / aire
        w2 = 1 - w0 - w1
        dedans = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
        if not dedans.any():
            return
        iz = w0 / p[0, 2] + w1 / p[1, 2] + w2 / p[2, 2]
        z = 1 / iz
        zone = zbuf[y0:y1 + 1, x0:x1 + 1]
        dedans &= z < zone
        if not dedans.any():
            return
        k0, k1, k2 = (w0 / p[0, 2]) * z, (w1 / p[1, 2]) * z, (w2 / p[2, 2]) * z
        u = k0 * uv[0, 0] + k1 * uv[1, 0] + k2 * uv[2, 0]
        v = k0 * uv[0, 1] + k1 * uv[1, 1] + k2 * uv[2, 1]
        texel = echantillon(texture, np.stack([u, v], -1))
        a = texel[..., 3] / 255.0
        lum = (k0[..., None] * col[0] + k1[..., None] * col[1] + k2[..., None] * col[2])
        rgb = texel[..., :3] * (1.0 if emis else lum)
        cible_img = img[y0:y1 + 1, x0:x1 + 1]
        if alpha_mode == "glass":
            m = dedans & (a > 0.004)
            cible_img[m] = cible_img[m] * (1 - a[m, None]) + rgb[m] * a[m, None]
        else:
            m = dedans & (a >= 0.1)
            cible_img[m] = rgb[m]
            zone[m] = z[m]

    def eclairage(N):
        n = N / np.maximum(np.linalg.norm(N, axis=1, keepdims=True), 1e-9)
        l = np.minimum(1.0, 0.4 + 0.6 * np.maximum(0, n @ L0) + 0.6 * np.maximum(0, n @ L1)) * lum_amb
        return np.repeat(l[:, None], 3, axis=1)

    if sol is not None:
        sol(tri, projeter)
    verres = []
    for (passe, P, UV, N) in tris:
        sp = projeter(P)
        if passe == "glass":
            verres.append((sp[:, 2].mean(), sp, UV, eclairage(N)))
            continue
        if passe == "sol":
            # sol du décor : la longueur de la « normale » porte l'ombre du modèle
            col = np.repeat(np.linalg.norm(N, axis=1)[:, None] * 0.95 * lum_amb, 3, axis=1)
            mode = "double"
        else:
            col = eclairage(N)
            mode = "solid"
        emis = passe == "emissive"
        for idx in ((0, 1, 2), (0, 2, 3)):
            tri(sp[list(idx)], UV[list(idx)], col[list(idx)], mode, emis)
    for (_, sp, UV, col) in sorted(verres, key=lambda e: -e[0]):
        for idx in ((0, 1, 2), (0, 2, 3)):
            tri(sp[list(idx)], UV[list(idx)], col[list(idx)], "glass")
    return np.clip(img, 0, 255).astype(np.uint8)


def ciel(L, H, nuit=False):
    y = np.linspace(0, 1, H)[:, None, None]
    haut, bas = (np.array([40, 52, 78]), np.array([96, 104, 116])) if nuit else (np.array([108, 146, 196]), np.array([206, 214, 220]))
    return np.broadcast_to(haut * (1 - y) + bas * y, (H, L, 3)).copy()


def sol_herbe(altitude_modele, ombre_centre, ombre_rayon):
    rng = np.random.default_rng(3)
    T = 256
    tex = np.ones((T, T, 4), np.float32) * 255
    base = np.array([92, 112, 58], np.float32)
    n = rng.normal(0, 1, (T, T)).astype(np.float32)
    tex[..., :3] = base * (1 + 0.10 * n)[..., None]
    terre = rng.random((T, T)) < 0.06
    tex[terre, :3] = np.array([110, 92, 66])

    def dessiner(tri, projeter):
        S = 60.0
        pas = 6.0
        for i in np.arange(-S, S, pas):
            for j in np.arange(-S, S, pas):
                P = np.array([[i, -altitude_modele, j], [i + pas, -altitude_modele, j], [i + pas, -altitude_modele, j + pas],
                              [i, -altitude_modele, j + pas]])
                UV = (P[:, [0, 2]] / 12.0) % 1.0
                UV[UV == 0] = 0.0
                sp = projeter(P)
                d = np.linalg.norm(P[:, [0, 2]] - ombre_centre, axis=1)
                ombre = 1 - 0.45 * np.exp(-(d / ombre_rayon) ** 2)
                col = np.repeat(ombre[:, None], 3, axis=1) * 0.92
                for idx in ((0, 2, 1), (0, 3, 2)):
                    tri(sp[list(idx)], UV[list(idx)], col[list(idx)], "double")
    return dessiner, tex


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("geo")
    ap.add_argument("png")
    ap.add_argument("sortie")
    ap.add_argument("--lacet", type=float, default=0)
    ap.add_argument("--tangage", type=float, default=0)
    ap.add_argument("--roulis", type=float, default=0)
    ap.add_argument("--cam", type=float, nargs=3, default=[35, 18, 22])
    ap.add_argument("--cible", type=float, nargs=3, default=[0, 2.2, 1.5])
    ap.add_argument("--focale", type=float, default=50)
    ap.add_argument("--taille", type=int, nargs=2, default=[1280, 800])
    ap.add_argument("--rotor", type=float, default=20)
    ap.add_argument("--flou", action="store_true")
    ap.add_argument("--sol", action="store_true")
    ap.add_argument("--altitude", type=float, default=0)
    ap.add_argument("--nuit", action="store_true")
    ap.add_argument("--cache", default="")
    ap.add_argument("--sur", type=int, default=2)
    a = ap.parse_args()
    parts, ordre, passes, points, tex = lire(a.geo)
    texture = np.array(Image.open(a.png).convert("RGBA"))
    cache = set(c for c in a.cache.split(",") if c)
    if a.flou:
        cache |= {"rotor", "tail_rotor"}
    else:
        cache |= {"rotor_blur", "tail_blur"}
    anim = {"rotor": ("y", math.radians(a.rotor)), "tail_rotor": ("x", math.radians(a.rotor * 3))}
    tris = assembler(parts, ordre, passes, tex, anim, cache)
    M = matrice_entite(a.lacet, a.tangage, a.roulis)
    monde = [(p, P @ M.T, UV, N @ M.T) for (p, P, UV, N) in tris]
    cible = M @ np.array(a.cible)
    az, el, dist = math.radians(a.cam[0]), math.radians(a.cam[1]), a.cam[2]
    cam = cible + dist * np.array([math.sin(az) * math.cos(el), math.sin(el), math.cos(az) * math.cos(el)])
    L, H = a.taille[0] * a.sur, a.taille[1] * a.sur
    fond = ciel(L, H, a.nuit)
    if a.sol:
        _, tex_sol = sol_herbe(a.altitude, np.array([cible[0], cible[2]]), 4.5)
        img = rendre_avec_sol(monde, texture, cam, cible, a.focale, L, H, fond, a.altitude, tex_sol, a.nuit)
    else:
        img = rendre(monde, texture, cam, cible, a.focale, L, H, fond, None, a.nuit)
    Image.fromarray(img).resize((a.taille[0], a.taille[1]), Image.LANCZOS).save(a.sortie)
    print("rendu :", a.sortie)


def rendre_avec_sol(monde, texture, cam, cible, focale, L, H, fond, altitude, tex_sol, nuit):
    # 1) sol seul (sa texture), 2) le modèle par-dessus avec le tampon de profondeur du sol
    sol_tris = []
    S, pas = 70.0, 2.5
    ombre_c = np.array([cible[0], cible[2]])
    for i in np.arange(-S, S, pas):
        for j in np.arange(-S, S, pas):
            P = np.array([[i, -altitude, j], [i, -altitude, j + pas], [i + pas, -altitude, j + pas], [i + pas, -altitude, j]])
            UV = np.array([[0, 0], [0, 1], [1, 1], [1, 0]], float) * 0.999
            N = np.array([[0, 1.0, 0]] * 4)
            sol_tris.append(("sol", P, UV, N))
    # rendu combiné : on fusionne les deux listes, le sol avec sa texture dans un atlas côte à côte
    h, w = texture.shape[:2]
    hs = tex_sol.shape[0]
    grand = np.zeros((max(h, hs), w + hs, 4), np.uint8)
    grand[:h, :w] = texture
    grand[:hs, w:w + hs] = np.clip(tex_sol, 0, 255).astype(np.uint8)
    tris = []
    for (p, P, UV, N) in monde:
        tris.append((p, P, UV * [w / (w + hs), h / grand.shape[0]], N))
    for (p, P, UV, N) in sol_tris:
        d = np.linalg.norm(P[:, [0, 2]] - ombre_c, axis=1)
        tris.append(("sol", P, np.column_stack([(w + UV[:, 0] * (hs - 1)) / (w + hs), UV[:, 1] * (hs - 1) / grand.shape[0]]),
                     N * (1 - 0.5 * np.exp(-(d / 5.5) ** 2))[:, None]))
    return rendre(tris, grand, cam, cible, focale, L, H, fond, None, nuit)


if __name__ == "__main__":
    main()
