#!/usr/bin/env python3
"""
Faces confondues dans des modèles de bloc (HeraliZ, 03/11) : deux faces dans le même plan, tournées du même côté, qui se
recouvrent. La carte graphique les départage au hasard, d'une image à l'autre et d'un pixel à l'autre : la texture
scintille (« z-fighting », vu en jeu sur le cadran et le volant du coffre-fort). Une face cachée (le point juste devant
elle est dans une autre pièce) ne compte pas.

    python modules/bases/outils/faces_confondues.py <modèle.json>...      liste les recouvrements visibles (code 1 s'il y en a)

Appelé par les outils qui écrivent nos modèles (coffres_modeles.py) : ils refusent d'installer un modèle qui en a.
Pièces tournées comprises (même calcul que FaceBakery : coins tournés autour de l'origine de la pièce).
"""
import json
import math
import sys
from pathlib import Path

import numpy as np

COINS = {
    "down": [(0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)],
    "up": [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)],
    "north": [(1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)],
    "south": [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)],
    "west": [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)],
    "east": [(1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)],
}
NORMALES = {"down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0),
            "east": (1, 0, 0)}
AXES = {"x": np.array([1.0, 0, 0]), "y": np.array([0, 1.0, 0]), "z": np.array([0, 0, 1.0])}
ECART = 0.02          # px : en dessous, deux plans comptent comme le même (nos gradins voulus font 0,05 px au moins)
AIRE_MIN = 1e-3       # px² : recouvrement plus petit ignoré (arêtes qui se touchent)


def _rot(axe, deg):
    k = AXES[axe]
    t = math.radians(deg)
    K = np.array([[0, -k[2], k[1]], [k[2], 0, -k[0]], [-k[1], k[0], 0]])
    return np.eye(3) + math.sin(t) * K + (1 - math.cos(t)) * K @ K


def _faces(elements):
    out = []
    for i, el in enumerate(elements):
        a, b = np.array(el["from"], float), np.array(el["to"], float)
        R, o = np.eye(3), np.zeros(3)
        if "rotation" in el:
            R = _rot(el["rotation"]["axis"], el["rotation"]["angle"])
            o = np.array(el["rotation"]["origin"], float)
        for f in el["faces"]:
            c = np.array([[b[k] if s else a[k] for k, s in enumerate(sel)] for sel in COINS[f]], float)
            out.append((i, f, (c - o) @ R.T + o, R @ np.array(NORMALES[f], float)))
    return out


def _aire_signee(p):
    return 0.5 * (np.dot(p[:, 0], np.roll(p[:, 1], -1)) - np.dot(p[:, 1], np.roll(p[:, 0], -1)))


def _couper(sujet, clip):
    """Intersection de deux polygones convexes tournant dans le sens trigonométrique (Sutherland-Hodgman)."""
    out = list(sujet)
    for k in range(len(clip)):
        a, b = clip[k], clip[(k + 1) % len(clip)]
        entree, out = out, []
        if not entree:
            break
        cote = [(b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]) for p in entree]
        for j in range(len(entree)):
            p, q, dp, dq = entree[j], entree[(j + 1) % len(entree)], cote[j], cote[(j + 1) % len(entree)]
            if dq >= -1e-9:
                if dp < -1e-9:
                    out.append(p + (q - p) * (dp / (dp - dq)))
                out.append(q)
            elif dp >= -1e-9:
                out.append(p + (q - p) * (dp / (dp - dq)))
    return np.array(out) if len(out) >= 3 else None


def _dans_une_piece(p, elements, sauf):
    for j, el in enumerate(elements):
        if j in sauf:
            continue
        a, b = np.array(el["from"], float), np.array(el["to"], float)
        q = p
        if "rotation" in el:
            o = np.array(el["rotation"]["origin"], float)
            q = (p - o) @ _rot(el["rotation"]["axis"], el["rotation"]["angle"]) + o     # rotation inverse
        if np.all(q > a + 1e-3) and np.all(q < b - 1e-3):
            return True
    return False


def recouvrements(elements):
    """[(pièce i, face, pièce j, face, écart en px, aire en px²)] des faces confondues visibles."""
    fs = _faces(elements)
    res = []
    for x in range(len(fs)):
        i, f, c, n = fs[x]
        u = np.cross(n, [0, 1, 0]) if abs(n[1]) < 0.9 else np.cross(n, [1, 0, 0])
        u = u / np.linalg.norm(u)
        v = np.cross(n, u)
        d = float(np.dot(c[0], n))
        p1 = np.stack([c @ u, c @ v], 1)
        p1 = p1 if _aire_signee(p1) > 0 else p1[::-1]
        for y in range(x + 1, len(fs)):
            j, g, c2, n2 = fs[y]
            if i == j or np.dot(n, n2) < 0.9999:
                continue
            ecart = float(np.dot(c2[0], n)) - d
            if abs(ecart) >= ECART:
                continue
            p2 = np.stack([c2 @ u, c2 @ v], 1)
            p2 = p2 if _aire_signee(p2) > 0 else p2[::-1]
            inter = _couper(p1, p2)
            if inter is None or abs(_aire_signee(inter)) < AIRE_MIN:
                continue
            # visible si un point de la zone commune (centre, sommets ramenés vers lui) n'a pas une pièce juste devant
            m = inter.mean(0)
            points = [m] + [m + (q - m) * t for q in inter for t in (0.5, 0.9)]
            if any(not _dans_une_piece(q[0] * u + q[1] * v + n * (d + 0.01), elements, {i, j}) for q in points):
                res.append((i, f, j, g, round(ecart, 4), round(abs(_aire_signee(inter)), 3)))
    return res


def _boite(el):
    return "[%s]..[%s]" % (", ".join("%g" % round(float(x), 4) for x in el["from"]),
                           ", ".join("%g" % round(float(x), 4) for x in el["to"]))


def texte(nom, elements, res):
    lignes = [f"{nom} : {len(res)} recouvrement(s) de faces confondues (scintillement en jeu)"]
    for i, f, j, g, e, s in res[:12]:
        lignes.append(f"    pièce {i} {_boite(elements[i])} face {f}  /  pièce {j} {_boite(elements[j])} face {g}"
                      f"  (écart {e:g} px, {s:g} px²)")
    if len(res) > 12:
        lignes.append(f"    ... et {len(res) - 12} autres")
    return "\n".join(lignes)


def exiger_aucun(nom, elements):
    """Arrête l'outil si le modèle a des faces confondues visibles."""
    res = recouvrements(elements)
    if res:
        raise SystemExit(texte(nom, elements, res))


def main():
    total = 0
    for chemin in sys.argv[1:]:
        els = json.loads(Path(chemin).read_text(encoding="utf-8")).get("elements", [])
        res = recouvrements(els)
        total += len(res)
        print(texte(chemin, els, res) if res else f"{chemin} : aucune face confondue")
    sys.exit(1 if total else 0)


if __name__ == "__main__":
    main()
