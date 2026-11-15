#!/usr/bin/env python3
"""HeraliZ Récolte : modèles 3D des gisements, des arbres à abattre et des outils, et leurs rendus.

Proposition (règle 6 du CLAUDE.md : rendus validés par le propriétaire AVANT tout code du mod) :
  - 3 gisements : pierre (pierres taillées), métal (fragments de métal), métal rare (métal renforcé) ; chacun en
    4 étapes (il rétrécit, des morceaux cassés en clair) ;
  - 2 arbres à abattre : bouleau et épicéa (tronc qui tombe, souche qui reste) ;
  - 2 outils : hache et pioche.

    python modules/recolte/outils/recolte_modeles.py --apercu DOSSIER     rendus seulement (rien n'est installé)

Rien n'est dessiné à la main : formes et matières sont calculées ici (moteur : recolte_moteur.py). Mesures en mètres,
1 bloc = 1 m, joueur 1,8 m. Les rendus utilisent l'éclairage des entités de Minecraft 1.20.1 et le sol du pack du serveur.
"""
from __future__ import annotations

import argparse
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import recolte_moteur as mo  # noqa: E402
from recolte_moteur import Morceau, fbm3, lisse, melange, perlin3, rgb, ride3, voronoi3  # noqa: E402

RACINE_MODULE = Path(__file__).resolve().parents[1]
DEPOT = RACINE_MODULE.parents[1]
POLICE = DEPOT / "launcher" / "app" / "assets" / "fonts" / "DMSans-Bold.ttf"
POLICE_MOY = DEPOT / "launcher" / "app" / "assets" / "fonts" / "DMSans-Medium.ttf"


# ============================================================================================================ gisements

ETAPES = [1.0, 0.84, 0.68, 0.52]       # taille du gisement à chaque étape (il rétrécit par étapes)


def _coupe(r, d, n, dist):
    """Coupe plane, en gardant la forme « étoilée » : un sommet au-delà du plan revient sur le plan, le long de son rayon."""
    proj = d @ n
    lim = np.where(proj > 1e-6, dist / np.maximum(proj, 1e-6), np.inf)
    dedans = r > lim
    return np.where(dedans, lim, r), dedans


def rocher(graine, echelle, coupes=11, etape=0, sub=4, enfoui=0.12):
    """Rocher en facettes (granite cassé), centré en x/z, posé sur y = 0 (enfoui de quelques centimètres).
    Rend (positions, triangles, directions, fraîcheur par sommet)."""
    rng = np.random.default_rng(graine)
    d, tri = mo.icosphere(sub)
    r = 1.0 + 0.16 * fbm3(d * 1.2, 1.0, 3, graine)
    frais = np.zeros(len(d))
    # grandes facettes d'origine (vieilles cassures, usées)
    for k in range(coupes):
        n = rng.normal(size=3)
        n[1] = abs(n[1]) * 0.8 + 0.1 if rng.random() < 0.6 else n[1] * 0.4
        n /= np.linalg.norm(n)
        r, _ = _coupe(r, d, n, rng.uniform(0.68, 0.9))
    # dessous plat, posé au sol
    r, _ = _coupe(r, d, np.array([0, -1.0, 0]), 0.42)
    # étapes : des morceaux arrachés (cassures fraîches), toujours les mêmes d'une étape à la suivante
    rng2 = np.random.default_rng(graine + 991)
    eclats = []
    for k in range(9):
        n = rng2.normal(size=3)
        n[1] = abs(n[1]) * 0.9 + 0.25
        n /= np.linalg.norm(n)
        eclats.append((n, rng2.uniform(0.56, 0.78)))
    for k in range(min(etape * 2, len(eclats))):
        n, dist = eclats[k]
        r, dedans = _coupe(r, d, n, dist)
        frais = np.maximum(frais, dedans.astype(float))
    # rugosité fine (les facettes ne sont pas des plans parfaits)
    r = r * (1 + 0.022 * fbm3(d * 5.0, 1.0, 3, graine + 5) + 0.008 * perlin3(d * 17.0, graine + 9))
    p = d * r[:, None]
    s = np.array(echelle) * ETAPES[etape]
    p = p * s
    p[:, 1] -= p[:, 1].min() + enfoui * ETAPES[etape]
    return p, tri, d, frais


def morceaux_rocher(p, tri, d, frais, matiere, cote_px, extra=None):
    """Découpe le rocher en 6 cartes (projection sur un cube depuis son centre : aucune carte ne se recouvre)."""
    nor = mo.normales_sommets(p, tri)
    tmp = Morceau(p, tri, np.zeros((len(p), 2)), matiere, (1, 1), nor=nor)
    cent = d[tri].mean(1)
    face, _ = mo.uv_cube(cent)
    morceaux = []
    for f in range(6):
        sel = tri[face == f]
        if len(sel) == 0:
            continue
        idx, inv = np.unique(sel, return_inverse=True)
        t = inv.reshape(-1, 3)
        uv = mo.projete_face(d[idx], np.full(len(idx), f))
        uv = (uv - 0.5) * 0.86 + 0.5          # marge pour les triangles qui débordent un peu de la face
        ex = {"frais": frais[idx][:, None]}
        if extra:
            for k, v in extra.items():
                ex[k] = v[idx]
        m = Morceau(p[idx], t, uv, matiere, (cote_px, cote_px), nor=nor[idx], extra=ex)
        m.groupe = "rocher"
        m._source = (tmp, idx)
        morceaux.append(m)
    return tmp, morceaux


# ------------------------------------------------------------------------------------------------ matières des roches

def _h(x):
    return (np.sin(x * 12.9898 * 1000) * 43758.5453) % 1.0


def _attr(ctxm, cle, n):
    v = ctxm.get(cle)
    return np.zeros(n) if v is None else v[:, 0]


def _roche(p, n, ctxm, base, grain, fissures=1.0, contraste=1.0):
    """Roche : teinte en grandes taches, marbrures, grain fin discret, arêtes usées plus claires, creux plus sombres,
    quelques vraies fissures (pas un réseau), traînées de pluie sur les flancs."""
    N = len(p)
    courb = _attr(ctxm, "courbure", N)
    c = melange(base[0], base[1], lisse(-0.55, 0.55, fbm3(p, 1.3, 4, 11)))
    c *= (0.93 + 0.14 * lisse(-0.6, 0.6, fbm3(p, 7.0, 3, 12)))[:, None] ** contraste
    f1, f2, ident = voronoi3(p, 48.0, 3)
    pal = np.array(grain)
    g = pal[(ident * len(pal)).astype(int) % len(pal)]
    c = melange(c, g, 0.22 * lisse(0.2, 0.8, _h(ident + 0.17)))
    # arêtes (convexes) éclaircies, creux assombris
    c *= (1 + 0.20 * lisse(0.0, 1.0, -courb) - 0.28 * lisse(0.0, 1.0, courb))[:, None]
    # fissures : quelques lignes, seulement là où un masque le veut
    w = p + 0.05 * np.stack([fbm3(p, 4.0, 2, 31), fbm3(p, 4.0, 2, 32), fbm3(p, 4.0, 2, 33)], 1)
    ligne = 1 - lisse(0.006, 0.026, np.abs(perlin3(w * 1.4, 34)))
    masque = lisse(0.15, 0.45, fbm3(p, 0.9, 2, 35))
    fis = ligne * masque
    c *= (1 - 0.55 * fis * fissures)[:, None]
    # traînées de pluie (sombres) sur les flancs
    flanc = 1 - np.abs(n[:, 1])
    trainee = lisse(0.1, 0.7, fbm3(p * np.array([7.0, 0.7, 7.0]), 1.0, 3, 38) * 0.5 + 0.5)
    c *= (1 - 0.12 * trainee * flanc)[:, None]
    return c


def _usure(p, n, ctxm, c, mousse=1.0, lichen=1.0):
    """Mousse en plaques sur le dessus, lichens épars, terre au pied ; rien sur les cassures fraîches."""
    N = len(p)
    vieux = 1 - _attr(ctxm, "frais", N)
    haut = lisse(0.45, 0.9, n[:, 1])
    m = lisse(0.18, 0.42, fbm3(p, 1.5, 4, 41) + 0.18 * haut) * haut * vieux * mousse
    mc = melange(rgb("#3F4A26"), rgb("#66703A"), lisse(-0.5, 0.6, fbm3(p, 11.0, 3, 42)))
    mc *= (0.78 + 0.3 * lisse(-0.3, 0.6, fbm3(p, 38.0, 2, 43)))[:, None]
    bord = lisse(0.0, 0.25, m)
    c = melange(c, mc, bord * 0.9)
    f1, f2, ident = voronoi3(p, 11.0, 47)
    tache = (1 - lisse(0.10, 0.22, f1 + 0.05 * fbm3(p, 40.0, 2, 48))) * lisse(0.72, 0.9, _h(ident + 0.3)) * vieux * lichen
    lc = np.where((_h(ident + 0.7) > 0.75)[:, None], rgb("#9C7B45"), rgb("#9EA290"))
    c = melange(c, lc, tache * 0.75 * (1 - m))
    y = p[:, 1]
    pied = (1 - lisse(0.0, 0.22, y + 0.05 * fbm3(p, 6.0, 2, 49))) * vieux
    c = melange(c, rgb("#46392A") * (0.85 + 0.3 * (fbm3(p, 20.0, 2, 50) * 0.5 + 0.5))[:, None], pied * 0.8)
    return c


def _cassure(p, n, ctxm, c, teinte):
    """Cassure fraîche : plus claire, nette, petites marques d'impact de la pioche."""
    frais = _attr(ctxm, "frais", len(p))
    cc = np.array(teinte) * (0.9 + 0.14 * lisse(-0.5, 0.5, fbm3(p, 18.0, 3, 62)))[:, None]
    f1, f2, ident = voronoi3(p, 60.0, 61)
    cc = melange(cc, cc * 0.82, 0.5 * lisse(0.4, 0.9, _h(ident)))
    impact = 1 - lisse(0.0, 0.035, voronoi3(p, 5.0, 63)[0])
    cc *= (1 - 0.3 * impact)[:, None]
    return melange(c, cc, frais)


def mat_pierre(p, n, ctxm):
    c = _roche(p, n, ctxm, (rgb("#66645F"), rgb("#8A867E")),
               [rgb("#4F4D4A"), rgb("#A19D95"), rgb("#8C7E76"), rgb("#3A3937")])
    c = _cassure(p, n, ctxm, c, rgb("#928E86"))
    return _usure(p, n, ctxm, c)


def _veines(p, freq, largeur, graine, deform=0.18):
    w = p + deform * np.stack([fbm3(p, 1.6, 3, graine + 1), fbm3(p, 1.6, 3, graine + 2), fbm3(p, 1.6, 3, graine + 3)], 1)
    v = np.abs(perlin3(w * freq, graine))
    return 1 - lisse(largeur * 0.35, largeur, v), v


def mat_metal(p, n, ctxm):
    N = len(p)
    frais = _attr(ctxm, "frais", N)
    c = _roche(p, n, ctxm, (rgb("#47423D"), rgb("#5E5750")),
               [rgb("#34302D"), rgb("#6A625A"), rgb("#4F433B"), rgb("#2A2725")], fissures=0.8)
    # minerai en plaques et en filons de largeur variable (pas des traits réguliers)
    w = p + 0.22 * np.stack([fbm3(p, 1.4, 3, 101), fbm3(p, 1.4, 3, 102), fbm3(p, 1.4, 3, 103)], 1)
    large = 0.06 + 0.12 * lisse(-0.4, 0.6, fbm3(p, 1.8, 3, 104))
    dist = np.abs(perlin3(w * 1.1, 71))
    filon = 1 - lisse(large * 0.45, large, dist)
    plaque = lisse(0.28, 0.42, fbm3(w, 1.5, 4, 105))
    v = np.maximum(filon, plaque)
    halo = np.maximum(1 - lisse(large, large + 0.12, dist), lisse(0.18, 0.3, fbm3(w, 1.5, 4, 105)))
    # couleur du minerai : hématite brun-rouge sombre, limonite plus claire, fer gris à reflets
    m1 = melange(rgb("#3A2219"), rgb("#62351F"), lisse(-0.5, 0.5, fbm3(p, 10.0, 3, 72)))
    m1 = melange(m1, rgb("#7E5530"), 0.4 * lisse(0.25, 0.7, fbm3(p, 4.0, 2, 76)))
    fer_m = lisse(0.15, 0.45, fbm3(w, 6.0, 3, 77))
    lustre = 0.5 + 0.5 * np.clip(n[:, 1], 0, 1)
    fer = melange(rgb("#3E4247"), rgb("#6E747B"), lustre * lisse(0.0, 0.6, fbm3(p, 25.0, 2, 78) * 0.5 + 0.5))
    f1, f2, ident = voronoi3(p, 40.0, 79)
    eclat = (1 - lisse(0.08, 0.16, f1)) * lisse(0.8, 0.95, _h(ident)) * lustre
    fer = melange(fer, rgb("#B4BAC0"), eclat * 0.8)
    vein_c = melange(m1, fer, fer_m * 0.8)
    c = melange(c, c * np.array([1.06, 0.86, 0.72]), halo * 0.45)   # roche tachée de rouille autour
    c = melange(c, vein_c, v)
    # coulures de rouille vers le bas
    vb = 1 - lisse(large * 0.6, large * 1.8, np.abs(perlin3((w + np.array([0, 0.14, 0])) * 1.1, 71)))
    coul = vb * lisse(0.0, 0.6, fbm3(p * np.array([9.0, 0.6, 9.0]), 1.0, 3, 74) * 0.5 + 0.5) * (1 - np.abs(n[:, 1]))
    c = melange(c, rgb("#5C3119"), coul * 0.35 * (1 - v) * (1 - frais))
    c = _cassure(p, n, ctxm, c, rgb("#6A645D"))
    # sur une cassure fraîche, le fer est à vif
    c = melange(c, melange(rgb("#6A3A20"), rgb("#9CA2A8"), np.maximum(fer_m, 0.4)), v * frais * 0.9)
    return _usure(p, n, ctxm, c, mousse=0.45, lichen=0.6)


def mat_rare(p, n, ctxm):
    N = len(p)
    frais = _attr(ctxm, "frais", N)
    c = _roche(p, n, ctxm, (rgb("#30343B"), rgb("#454A53")),
               [rgb("#25272C"), rgb("#55585F"), rgb("#383B43"), rgb("#4A4D55")], fissures=0.6)
    v, dist = _veines(p, 0.8, 0.2, 81, deform=0.3)
    # veine : bords sombres, coeur bleu acier, paillettes allongées (pas de cellules visibles)
    coeur = 1 - lisse(0.0, 0.1, dist)
    acier = melange(rgb("#33435A"), rgb("#6584A6"), coeur)
    q = np.abs(perlin3(p * np.array([3.0, 22.0, 3.0]) + 1.7, 84))
    paillette = (1 - lisse(0.02, 0.06, q)) * lisse(0.2, 0.6, fbm3(p, 9.0, 2, 85))
    acier = melange(acier, rgb("#A8BBCE"), paillette * 0.7 * (0.5 + 0.5 * np.clip(n[:, 1], 0, 1)))
    halo = 1 - lisse(0.03, 0.18, dist)
    c = melange(c, c * np.array([0.88, 0.94, 1.1]), halo * 0.6)
    c = melange(c, acier, v)
    c = _cassure(p, n, ctxm, c, rgb("#4A4E56"))
    c = melange(c, acier * 1.12, v * frais * 0.7)
    return _usure(p, n, ctxm, c, mousse=0.3, lichen=0.35)


def mat_cristal(p, n, ctxm):
    """Cristaux de métal rare : prismes bleu acier, arêtes claires (u autour du prisme : 6 pans)."""
    uv = ctxm["uv_local"] if "uv_local" in ctxm else None
    N = len(p)
    tour = _attr(ctxm, "tour", N)
    long_ = _attr(ctxm, "long", N)
    pan = (tour * 6.0) % 1.0
    arete = 1 - lisse(0.0, 0.12, np.minimum(pan, 1 - pan))
    c = melange(rgb("#2E3C50"), rgb("#58708E"), lisse(0.0, 1.0, long_))
    c = melange(c, rgb("#A9BCD0"), arete * 0.75)
    c *= (0.9 + 0.2 * lisse(-0.5, 0.5, fbm3(p, 60.0, 2, 91)))[:, None]
    pied = 1 - lisse(0.0, 0.25, long_)
    return melange(c, rgb("#1D1F24"), pied * 0.7)


MATIERES = {"pierre": mat_pierre, "metal": mat_metal, "rare": mat_rare, "cristal": mat_cristal}

GISEMENTS = {
    # nom : (graine, échelle en m (largeur, hauteur, profondeur) à la première étape, matière, coupes)
    "pierre": (12, (1.05, 0.78, 0.92), "pierre", 11),
    "metal": (31, (0.98, 0.82, 0.86), "metal", 12),
    "rare": (57, (0.82, 0.92, 0.74), "rare", 14),
}


def prisme(base, direction, longueur, rayon, cotes=6):
    """Cristal : prisme à 6 pans, pointe au bout ; rend un Morceau (u autour, v le long, de 0 à 1)."""
    direction = direction / np.linalg.norm(direction)
    k = np.array([0.0, 0.12, 0.62, 0.86, 1.0])
    spine = base[None, :] + direction[None, :] * (k * longueur)[:, None]
    rayons = rayon * np.array([1.05, 1.0, 0.96, 0.62, 0.02])
    pos, tri, uv, lg = mo.tube(spine, rayons, cotes=cotes)
    uv[:, 1] = uv[:, 1] / max(lg[-1], 1e-6)
    # pans plats : normales par pan (pas lissées autour du prisme)
    pos_f, tri_f = pos[tri].reshape(-1, 3), np.arange(len(tri) * 3).reshape(-1, 3)
    uv_f = uv[tri].reshape(-1, 2)
    v0, v1, v2 = pos_f[tri_f[:, 0]], pos_f[tri_f[:, 1]], pos_f[tri_f[:, 2]]
    fn = np.cross(v1 - v0, v2 - v0)
    fn /= np.maximum(np.linalg.norm(fn, axis=1, keepdims=True), 1e-12)
    nor = np.repeat(fn, 3, 0)
    m = Morceau(pos_f, tri_f, uv_f, "cristal", (28, 56), nor=nor,
                extra={"tour": uv_f[:, :1].copy(), "long": uv_f[:, 1:2].copy()})
    return m


def cristaux(p0, n0, etape_p, etape_n, garde, graine, echelle):
    """Grappes de cristaux posées sur le rocher (mêmes sommets d'ancrage à chaque étape)."""
    rng = np.random.default_rng(graine)
    out = []
    for ia in garde:
        base = etape_p[ia]
        nn = etape_n[ia]
        for k in range(rng.integers(3, 6)):
            dirc = nn + rng.normal(size=3) * 0.42 + np.array([0, 0.3, 0])
            L = rng.uniform(0.26, 0.5) * echelle * (1.0 if k == 0 else rng.uniform(0.45, 0.8))
            R = rng.uniform(0.045, 0.07) * echelle * (1.0 if k == 0 else 0.75)
            dec = rng.normal(size=3) * 0.05
            out.append(prisme(base - nn * 0.05 + dec, dirc, L, R))
    return out


def gisement(nom, cote_px=168, largeur_tex=1024):
    """Les 4 étapes d'un gisement, une seule texture (4 x 6 cartes, plus les cristaux du métal rare)."""
    graine, ech, mat, coupes = GISEMENTS[nom]
    etapes = []
    tous = []
    ancres = None
    for e in range(4):
        p, tri, d, frais = rocher(graine, ech, coupes, e)
        nor = mo.normales_sommets(p, tri)
        courb = mo.courbure(p, tri, nor)
        tmp, ms = morceaux_rocher(p, tri, d, frais, mat, cote_px, extra={"courbure": courb[:, None]})
        extras = []
        if nom == "rare":
            if ancres is None:
                rng = np.random.default_rng(graine + 5)
                cand = np.nonzero((nor[:, 1] > 0.25) & (p[:, 1] > 0.35 * p[:, 1].max()))[0]
                rng.shuffle(cand)
                ancres = []
                for c in cand:
                    if all(np.linalg.norm(p[c] - p[a]) > 0.55 for a in ancres):
                        ancres.append(c)
                    if len(ancres) == 3:
                        break
            garde = [a for a in ancres if frais[a] < 0.5][: [3, 2, 1, 1][e]]
            extras = cristaux(None, None, p, nor, garde, graine + 7, ETAPES[e])
        mo.ombre_cuite([tmp] + extras, sol_y=0.0, directions=128, plancher=0.30)
        for m in ms:
            src, idx = m._source
            m.ao = src.ao[idx]
        for m in ms + extras:
            m.groupe = "etape%d" % e
        etapes.append(ms + extras)
        tous += ms + extras
    tex, h = mo.cuire_texture(tous, MATIERES, largeur_tex)
    return etapes, tex


# ============================================================================================================ arbres

HAUTEUR_COUPE = 0.5          # le tronc est coupé à 50 cm : la souche reste, le reste tombe


def _spine_tronc(hauteur, graine, penche=(0.18, 0.1), k=48):
    rng = np.random.default_rng(graine)
    y = np.linspace(-0.25, hauteur, k)
    t = np.clip(y / hauteur, 0, 1)
    x = penche[0] * np.sin(t * 1.4) + 0.05 * np.sin(t * 7.0 + rng.uniform(0, 6))
    z = penche[1] * np.sin(t * 2.3 + 1.0) + 0.04 * np.sin(t * 9.0 + rng.uniform(0, 6))
    return np.stack([x, y, z], 1)


def _point_sur(spine, y):
    i = np.clip(np.searchsorted(spine[:, 1], y), 1, len(spine) - 1)
    a, b = spine[i - 1], spine[i]
    t = (y - a[1]) / max(b[1] - a[1], 1e-9)
    return a + (b - a) * t


def _bout_de_tube(pos, tri, uv, matiere, taille, groupe, extra=None):
    m = Morceau(pos, tri, uv / np.maximum(uv.max(0), 1e-9), matiere, taille, extra=extra or {})
    m.groupe = groupe
    return m


def tronc_morceaux(spine, rayon_de, matiere, hauteur, cotes=28, entaille=0.0, entaille_dir=-math.pi / 2, densite=150):
    """Tronc coupé en deux tubes à HAUTEUR_COUPE : souche (fixe) et tronc (qui tombe), plus les deux disques de la coupe
    (bois de bout : cernes). entaille : profondeur de l'entaille de la hache (0 à 0,7 du rayon), côté entaille_dir."""
    out = []
    hc = HAUTEUR_COUPE

    def rayon_bruit(spi, i, a, s_ref):
        y = s_ref[i, 1]
        r = rayon_de(y)
        r *= 1 + 0.035 * perlin3(np.array([[math.cos(a) * 2.0, y * 1.5, math.sin(a) * 2.0]]), 5)[0]
        if entaille > 0 and spi is not None and y >= hc - 1e-6:
            da = math.atan2(math.sin(a - entaille_dir), math.cos(a - entaille_dir))
            prof = max(0.0, 1 - abs(da) / 1.2) ** 0.5
            v = max(0.0, 1 - (y - hc) / 0.24)
            r *= 1 - entaille * prof * v
        return r

    for nom, y0, y1 in (("souche", -0.25, hc), ("tronc", hc, hauteur)):
        sel = (spine[:, 1] > y0) & (spine[:, 1] < y1)
        spi = np.vstack([_point_sur(spine, y0)[None], spine[sel], _point_sur(spine, y1)[None]])
        if nom == "souche":
            spi = np.vstack([spi[:1], _point_sur(spine, hc - 0.2)[None], _point_sur(spine, hc - 0.12)[None],
                             _point_sur(spine, hc - 0.06)[None], spi[-1:]])
        else:
            extra_pts = [_point_sur(spine, hc + d)[None] for d in (0.02, 0.05, 0.09, 0.13, 0.17, 0.2, 0.24)]
            spi = np.vstack([spi[:1]] + extra_pts + [spi[1:]])
            spi = spi[np.argsort(spi[:, 1])]
            spi = spi[np.concatenate([[True], np.diff(spi[:, 1]) > 1e-4])]
        rayons = np.array([rayon_de(p[1]) for p in spi])
        souche = nom == "souche"
        pos, tri, uv, lg = mo.tube(spi, rayons, cotes=cotes,
                                   bruit_r=lambda a, i, s=spi, so=souche: rayon_bruit(None if so else s, i, a, s) / max(rayon_de(s[i, 1]), 1e-9))
        circ = 2 * math.pi * rayons.mean()
        tw = max(32, min(1024, int(circ * densite)))
        th = max(32, min(2048, int(lg[-1] * densite)))
        uvn = np.stack([uv[:, 0], uv[:, 1] / max(lg[-1], 1e-9)], 1)
        m = Morceau(pos, tri, uvn, matiere, (tw, th))
        m.groupe = nom
        out.append(m)
        # disque de la coupe (bois de bout)
        y = hc
        c = _point_sur(spine, y)
        r = rayon_de(y) * 0.985
        n_ = 32
        ang = np.linspace(0, 2 * math.pi, n_, endpoint=False)
        anneau = np.stack([c[0] + np.cos(ang) * r, np.full(n_, y), c[2] + np.sin(ang) * r], 1)
        if nom == "souche":
            pts = np.vstack([c[None], anneau])
            tri_d = np.array([[0, 1 + (i + 1) % n_, 1 + i] for i in range(n_)])
            nor = np.tile([0, 1.0, 0], (len(pts), 1))
        else:
            pts = np.vstack([c[None], anneau]) + np.array([0, 0.002, 0])
            tri_d = np.array([[0, 1 + i, 1 + (i + 1) % n_] for i in range(n_)])
            nor = np.tile([0, -1.0, 0], (len(pts), 1))
        uvd = np.stack([(pts[:, 0] - c[0]) / (2 * r) + 0.5, (pts[:, 2] - c[2]) / (2 * r) + 0.5], 1)
        d = Morceau(pts, tri_d, uvd, "bout_" + matiere.split("_")[-1], (int(2 * r * 220) + 8, int(2 * r * 220) + 8),
                    nor=nor, extra={"centre": np.tile(c, (len(pts), 1)), "rayon": np.full((len(pts), 1), r)})
        d.groupe = nom
        out.append(d)
    return out


def racines(spine, rayon_de, n, graine, matiere, longueur=0.9):
    rng = np.random.default_rng(graine)
    out = []
    a0 = rng.uniform(0, 2 * math.pi)
    for k in range(n):
        a = a0 + 2 * math.pi * k / n + rng.uniform(-0.3, 0.3)
        r0 = rayon_de(0.2)
        c = _point_sur(spine, 0.2)
        dirh = np.array([math.cos(a), 0, math.sin(a)])
        L = longueur * rng.uniform(0.75, 1.15)
        t = np.linspace(0, 1, 10)
        pts = c[None] + dirh[None] * (r0 * 0.55 + L * t[:, None]) + np.array([0, 1, 0]) * (-0.2 - 0.1 * t - 0.4 * t ** 3)[:, None]
        rr = r0 * 0.3 * (1 - t) ** 0.8 + 0.01
        pos, tri, uv, lg = mo.tube(pts, rr, cotes=12)
        m = Morceau(pos, tri, np.stack([uv[:, 0], uv[:, 1] / lg[-1]], 1), matiere, (48, 96))
        m.groupe = "souche"
        out.append(m)
    return out


def branche(base, direction, longueur, r0, montee, tombe, graine, cotes=8, k=10):
    """Branche courbe : part de base, monte puis retombe au bout ; rend (spine, rayons)."""
    rng = np.random.default_rng(graine)
    d = np.array(direction, float)
    d[1] = 0
    d /= np.linalg.norm(d)
    t = np.linspace(0, 1, k)
    lat = np.cross(d, [0, 1, 0])
    spine = base[None] + d[None] * (longueur * t)[:, None] \
        + np.array([0, 1.0, 0])[None] * (longueur * (montee * t - tombe * t ** 2))[:, None] \
        + lat[None] * (0.08 * longueur * np.sin(t * 3.1 + rng.uniform(0, 6)))[:, None]
    rayons = r0 * (1 - 0.85 * t) + 0.006
    return spine, rayons


def carte_feuilles(centre, taille, normale, rot, variante, nb_variantes, groupe="tronc", centre_houppe=None,
                   rayon_houppe=1.0, penche=0.0):
    """Une grappe de feuilles : un carré texturé (découpé), deux faces ; normales tournées vers l'extérieur de la
    couronne (la couronne paraît en volume)."""
    n = np.asarray(normale, float)
    n /= np.linalg.norm(n)
    a = np.cross(n, [0, 1, 0])
    if np.linalg.norm(a) < 1e-3:
        a = np.array([1.0, 0, 0])
    a /= np.linalg.norm(a)
    b = np.cross(n, a)
    c, s = math.cos(rot), math.sin(rot)
    a, b = a * c + b * s, -a * s + b * c
    h = taille / 2
    pts = np.array([centre - a * h - b * h, centre + a * h - b * h, centre + a * h + b * h, centre - a * h + b * h])
    pts[:, 1] -= np.array([0, 0, 1, 1]) * penche * taille
    tri = np.array([[0, 1, 2], [0, 2, 3]])
    uv = np.array([[0, 1], [1, 1], [1, 0], [0, 0]], float)
    if centre_houppe is not None:
        dirs = pts - centre_houppe[None]
        dirs /= np.linalg.norm(dirs, axis=1, keepdims=True)
        nor = dirs * 0.75 + n[None] * 0.25
        nor /= np.linalg.norm(nor, axis=1, keepdims=True)
        dist = np.linalg.norm((pts - centre_houppe[None]) / np.array([1, 1.3, 1]), axis=1) / rayon_houppe
        ao = np.clip(0.42 + 0.62 * dist ** 1.1, 0.42, 1.0) * np.clip(0.75 + 0.25 * (pts[:, 1] - centre_houppe[1]) / rayon_houppe + 0.25, 0.6, 1.0)
    else:
        nor = np.tile(n, (4, 1))
        ao = np.ones(4)
    m = Morceau(pts, tri, uv, "feuilles", (1, 1), nor=nor, decoupe=True)
    m.ao = ao
    m.groupe = groupe
    m.variante = variante
    m.occulte = False
    return m


# --------------------------------------------------------------------------------------------- textures peintes (2D)

def _seuil_alpha(im, taille, seuil=0.5):
    im = im.resize(taille, Image.LANCZOS)
    a = np.array(im).astype(np.float64) / 255.0
    rgb_ = a[..., :3] / np.maximum(a[..., 3:4], 1e-6)
    alpha = (a[..., 3:4] > seuil).astype(np.float64)
    return np.concatenate([np.clip(rgb_, 0, 1), alpha], -1)


def _peindre_grappe_bouleau(graine, taille=256, sur=3):
    """Grappe de bouleau : rameaux qui retombent, petites feuilles serrées, plus sombres au coeur de la grappe."""
    rng = np.random.default_rng(graine)
    S = taille * sur
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    pal = [(112, 146, 60), (92, 128, 50), (134, 162, 72), (78, 110, 44), (150, 172, 86), (104, 138, 56), (122, 150, 64)]
    feuilles = []
    for k in range(rng.integers(5, 8)):
        x0 = rng.uniform(0.12, 0.88) * S
        y0 = rng.uniform(0.02, 0.3) * S
        pts = [(x0, y0)]
        ang = math.pi / 2 + rng.uniform(-0.7, 0.7)
        for j in range(16):
            ang += rng.uniform(-0.1, 0.14)
            x, y = pts[-1]
            pts.append((x + math.cos(ang) * S * 0.045, y + math.sin(ang) * S * 0.045))
        d.line(pts, fill=(64, 50, 38, 255), width=max(1, int(1.2 * sur)))
        for j in range(1, len(pts)):
            for s in range(3):
                x, y = pts[j]
                feuilles.append((x + rng.normal() * 9 * sur, y + rng.normal() * 7 * sur))
    # amas autour du centre (forme irrégulière)
    for k in range(160):
        a = rng.uniform(0, 2 * math.pi)
        r = rng.uniform(0, 1) ** 0.6 * 0.42 * S
        x = S / 2 + math.cos(a) * r * 1.05
        y = S * 0.55 + math.sin(a) * r * 0.9
        if 0.05 * S < x < 0.95 * S and 0.05 * S < y < 0.97 * S:
            feuilles.append((x, y))
    rng.shuffle(feuilles)
    for (x, y) in feuilles:
        L = rng.uniform(8.5, 13.0) * sur
        W = L * rng.uniform(0.6, 0.75)
        a = math.pi / 2 + rng.uniform(-1.0, 1.0)
        base = np.array(pal[rng.integers(len(pal))], float)
        cx, cy = x / S - 0.5, y / S - 0.55
        ombre = 0.62 + 0.38 * min(1.0, (cx * cx + cy * cy) ** 0.5 * 2.4)
        col = np.clip(base * ombre * rng.uniform(0.9, 1.1), 0, 255)
        ca, sa = math.cos(a), math.sin(a)
        contour = []
        for t in np.linspace(0, 2 * math.pi, 16, endpoint=False):
            u, v = math.cos(t), math.sin(t)
            lv = 0.5 + 0.5 * v
            px = u * W * 0.5 * (1 - 0.6 * lv ** 2)
            py = v * L * 0.5
            contour.append((x + px * ca - py * sa, y + px * sa + py * ca))
        d.polygon(contour, fill=tuple(int(c) for c in col) + (255,))
        # moitié éclairée / moitié ombrée de la feuille
        demi = contour[: len(contour) // 2] + [(x, y)]
        d.polygon(demi, fill=tuple(int(min(255, c * 1.1)) for c in col) + (255,))
    return _seuil_alpha(im, (taille, taille))


def _peindre_rameau_epicea(graine, w=256, h=96, sur=3):
    """Rameau d'épicéa vu de dessus : tige, ramilles serrées, aiguilles sombres, bouts plus clairs ; s'affine vers le
    bout (à droite)."""
    rng = np.random.default_rng(graine)
    W, H = w * sur, h * sur
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    tige = (70, 52, 38, 255)
    pal = [(40, 64, 46), (33, 56, 41), (48, 74, 52), (28, 48, 37), (55, 82, 56)]
    cy = H / 2

    def aiguilles(x0, y0, x1, y1, pas, longueur, t_global):
        n = max(2, int(math.hypot(x1 - x0, y1 - y0) / pas))
        ang = math.atan2(y1 - y0, x1 - x0)
        for i in range(n + 1):
            t = i / n
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            for s in (-1, 1):
                a = ang + s * rng.uniform(0.5, 1.1)
                L = longueur * rng.uniform(0.75, 1.2)
                clair = 1.0 + 0.55 * max(0.0, t - 0.6) + 0.25 * t_global
                base = np.array(pal[rng.integers(len(pal))], float) * clair
                col = tuple(int(min(255, c)) for c in base) + (255,)
                d.line([(x, y), (x + math.cos(a) * L, y + math.sin(a) * L)], fill=col, width=max(1, int(1.15 * sur)))

    d.line([(0, cy), (W * 0.97, cy)], fill=tige, width=int(2.2 * sur))
    nb = 26
    for i in range(nb):
        tg = i / nb
        x = (0.03 + 0.92 * tg) * W
        demi = (0.47 * (1 - tg) ** 0.7 + 0.07) * H
        for s in (-1, 1):
            a = s * rng.uniform(0.6, 0.9)
            lon = demi / max(math.sin(abs(a)), 0.3)
            x1, y1 = x + math.cos(a) * lon, cy + math.sin(a) * lon
            x1 = min(x1, W - 2)
            y1 = min(max(y1, 2), H - 2)
            d.line([(x, cy), (x1, y1)], fill=tige, width=max(1, int(1.1 * sur)))
            aiguilles(x, cy, x1, y1, 1.6 * sur, 6.0 * sur, tg)
    aiguilles(0, cy, W * 0.98, cy, 1.5 * sur, 7.0 * sur, 0.5)
    return _seuil_alpha(im, (w, h), 0.42)


# --------------------------------------------------------------------------------------------- matières du bois

ENTAILLE = {}     # réglé par modele_arbre : hauteur, direction, profondeur, ligne du tronc, rayon


def _dans_entaille(p):
    if not ENTAILLE or ENTAILLE["prof"] <= 0:
        return np.zeros(len(p))
    hc, d = ENTAILLE["hc"], ENTAILLE["dir"]
    y = p[:, 1]
    c = np.array([_point_sur(ENTAILLE["spine"], yy) for yy in np.clip(y, hc, hc + 0.3)])
    rel = p - c
    rel[:, 1] = 0
    r = np.linalg.norm(rel, axis=1)
    cosang = (rel @ d) / np.maximum(r, 1e-9)
    rmax = np.array([ENTAILLE["rayon"](yy) for yy in np.clip(y, hc, hc + 0.3)])
    dedans = (y >= hc - 0.002) & (y <= hc + 0.25) & (cosang > math.cos(1.15)) & (r < rmax * 0.95)
    return dedans.astype(float)


def _bois_vif(p, c, clair):
    e = _dans_entaille(p)
    if not e.any():
        return c
    v = melange(clair, clair * np.array([0.86, 0.8, 0.72]), lisse(-0.4, 0.6, fbm3(p * np.array([30, 4, 30]), 1.0, 3, 251)))
    marques = np.abs(perlin3(p * np.array([3.0, 90.0, 3.0]), 252))
    v *= (0.85 + 0.2 * lisse(0.0, 0.3, marques))[:, None]
    return melange(c, v, e)


def mat_ecorce_bouleau(p, n, ctxm):
    return _bois_vif(p, _ecorce_bouleau(p, n, ctxm), rgb("#E8D6B2"))


def mat_ecorce_epicea(p, n, ctxm):
    return _bois_vif(p, _ecorce_epicea(p, n, ctxm), rgb("#E6C690"))


def _ecorce_bouleau(p, n, ctxm):
    y = p[:, 1]
    c = melange(rgb("#E9E5DA"), rgb("#D3CFC4"), lisse(-0.5, 0.6, fbm3(p, 3.0, 4, 201)))
    c = melange(c, rgb("#F3F0E8"), 0.4 * lisse(0.2, 0.7, fbm3(p, 9.0, 2, 202)))
    # lenticelles : traits sombres horizontaux
    q = np.abs(perlin3(p * np.array([5.0, 70.0, 5.0]), 203))
    trait = (1 - lisse(0.03, 0.09, q)) * lisse(0.1, 0.5, fbm3(p * np.array([2.0, 1.0, 2.0]), 8.0, 2, 204))
    c = melange(c, rgb("#3B3632"), trait * 0.85)
    # papier qui pèle (orangé)
    pele = lisse(0.42, 0.55, fbm3(p * np.array([3.0, 12.0, 3.0]), 2.5, 3, 205))
    c = melange(c, rgb("#C9A88E"), pele * 0.55)
    # taches noires en losange (cicatrices de branches) : grandes cellules sombres éparses
    f1, f2, ident = voronoi3(p * np.array([1.0, 0.45, 1.0]), 3.5, 206)
    tache = (1 - lisse(0.12, 0.2, f1 + 0.03 * fbm3(p, 20.0, 2, 207))) * lisse(0.6, 0.85, (ident * 7.3) % 1.0)
    c = melange(c, rgb("#24201E"), tache * 0.9)
    # pied du tronc : écorce sombre et crevassée
    pied = 1 - lisse(0.5, 1.6, y + 0.4 * fbm3(p, 2.0, 2, 208))
    crev = np.abs(perlin3(p * np.array([26.0, 2.2, 26.0]), 209))
    sombre = melange(rgb("#2E2A27"), rgb("#5C5650"), lisse(0.05, 0.35, crev))
    c = melange(c, sombre, pied)
    # mousse au ras du sol
    c = melange(c, rgb("#4F5A2C"), (1 - lisse(0.0, 0.35, y)) * lisse(0.0, 0.4, fbm3(p, 6.0, 2, 210)) * 0.8)
    c *= (0.95 + 0.08 * lisse(-0.5, 0.5, fbm3(p, 30.0, 2, 211)))[:, None]
    return c


def _ecorce_epicea(p, n, ctxm):
    y = p[:, 1]
    c = melange(rgb("#4F3E31"), rgb("#6E5A48"), lisse(-0.5, 0.6, fbm3(p, 2.0, 4, 221)))
    # plaques d'écorce : cellules allongées vers le haut, joints sombres
    f1, f2, ident = voronoi3(p * np.array([1.0, 0.35, 1.0]), 16.0, 222)
    joint = 1 - lisse(0.0, 0.07, f2 - f1)
    c *= (0.85 + 0.3 * ((ident * 5.1) % 1.0))[:, None]
    c = melange(c, rgb("#2A2019"), joint * 0.75)
    # reflets gris (écorce sèche) et lichen
    c = melange(c, rgb("#8A8178"), 0.35 * lisse(0.25, 0.6, fbm3(p, 5.0, 3, 223)))
    lich = lisse(0.35, 0.55, fbm3(p, 4.0, 3, 224)) * lisse(0.6, 2.5, y)
    c = melange(c, rgb("#9EA48C"), lich * 0.5)
    # résine
    res = (1 - lisse(0.0, 0.03, voronoi3(p, 3.0, 225)[0])) * 0.8
    c = melange(c, rgb("#B07A2A"), res * 0.6)
    c = melange(c, rgb("#4A5428"), (1 - lisse(0.0, 0.3, y)) * 0.6)
    return c


def mat_bout(p, n, ctxm, aubier, coeur, cerne):
    c0 = ctxm["centre"]
    r = ctxm["rayon"][:, 0]
    dx = p - c0
    dist = np.sqrt(dx[:, 0] ** 2 + dx[:, 2] ** 2) / np.maximum(r, 1e-6)
    ang = np.arctan2(dx[:, 2], dx[:, 0])
    dd = dist + 0.02 * perlin3(np.stack([np.cos(ang) * 3, np.sin(ang) * 3, dist * 2], 1), 231)
    anneaux = 0.5 + 0.5 * np.cos(dd * 2 * math.pi * 14)
    c = melange(coeur, aubier, lisse(0.3, 0.85, dd))
    c = melange(c, cerne, lisse(0.55, 0.95, anneaux) * 0.45)
    # coups de hache : stries obliques, fentes radiales
    stries = np.abs(perlin3(np.stack([dx[:, 0] * 30 + dx[:, 2] * 30, dx[:, 2] * 4, np.zeros(len(p))], 1), 232))
    c *= (0.88 + 0.16 * lisse(0.0, 0.4, stries))[:, None]
    fente = (1 - lisse(0.0, 0.05, np.abs(np.sin(ang * 3 + 0.7)))) * lisse(0.25, 0.9, dist) * 0.6
    c = melange(c, cerne * 0.6, fente)
    ecorce = lisse(0.9, 0.97, dist)
    return melange(c, rgb("#3A2E26"), ecorce)


def mat_bout_bouleau(p, n, ctxm):
    return mat_bout(p, n, ctxm, rgb("#EBD9B8"), rgb("#D9BE92"), rgb("#B89770"))


def mat_bout_epicea(p, n, ctxm):
    return mat_bout(p, n, ctxm, rgb("#E8C995"), rgb("#C99A62"), rgb("#A97B4C"))


def mat_branche_bouleau(p, n, ctxm):
    c = melange(rgb("#6A5A4E"), rgb("#8E8076"), lisse(-0.5, 0.6, fbm3(p, 8.0, 3, 241)))
    return c


MATIERES.update({"ecorce_bouleau": mat_ecorce_bouleau, "ecorce_epicea": mat_ecorce_epicea,
                 "bout_ecorce": mat_bout_bouleau, "bout_bouleau": mat_bout_bouleau,
                 "bout_epicea": mat_bout_epicea, "branche_bouleau": mat_branche_bouleau})


def _atlas_avec_peintures(cuits, peintes, largeur=1024):
    """Texture : en haut les morceaux cuits (3D), en dessous les images peintes (feuilles), côte à côte."""
    h_cuit = mo.ranger(cuits, largeur)
    tex_cuit, _ = mo.cuire_texture(cuits, MATIERES, largeur, h_cuit)
    x = 0
    rangee_h = max(im.shape[0] for im in peintes)
    bas = np.zeros((rangee_h, largeur, 4))
    places = []
    for im in peintes:
        h, w = im.shape[:2]
        bas[:h, x:x + w] = im
        places.append((x, w, h))
        x += w
    H = h_cuit + rangee_h
    for m in cuits:
        m.uv_atlas = np.stack([m.uv_atlas[:, 0], m.uv_atlas[:, 1] * h_cuit / H], 1)
    tex = np.concatenate([tex_cuit, bas], 0)
    regions = [(px / largeur, h_cuit / H, (px + w) / largeur, (h_cuit + h) / H) for (px, w, h) in places]
    return tex, regions


def _placer_feuilles(cartes, regions):
    for m in cartes:
        u0, v0, u1, v1 = regions[m.variante]
        m.uv_atlas = np.stack([u0 + m.uv[:, 0] * (u1 - u0), v0 + m.uv[:, 1] * (v1 - v0)], 1)
        # un liseré d'un point de texture : jamais l'image voisine
        du, dv = 0.5 / 1024, 0.5 / 1024
        m.uv_atlas = np.clip(m.uv_atlas, [u0 + du, v0 + dv], [u1 - du, v1 - dv])


def bouleau(graine=4, entaille=0.0):
    rng = np.random.default_rng(graine)
    H = 9.2
    spine = _spine_tronc(H, graine, (0.22, 0.12))

    def rayon(y):
        base = 0.165 * (1 - 0.82 * np.clip(y / H, 0, 1)) + 0.03
        evase = 0.07 * math.exp(-max(y, 0) / 0.35)
        return base + evase

    morceaux = tronc_morceaux(spine, rayon, "ecorce_bouleau", H, entaille=entaille, densite=110)
    morceaux += racines(spine, rayon, 6, graine + 1, "ecorce_bouleau", 0.8)
    # branches
    cartes = []
    centre_h = np.array([0.2, 6.2, 0.1])
    R_h = 2.6
    nb = 15
    for k in range(nb):
        y = 2.9 + (H - 3.4) * (k / nb) ** 0.85 + rng.uniform(-0.15, 0.15)
        a = k * 2.399 + rng.uniform(-0.3, 0.3)
        base = _point_sur(spine, y)
        L = (2.3 - 1.5 * (k / nb)) * rng.uniform(0.85, 1.1)
        spi, rr = branche(base, (math.cos(a), 0, math.sin(a)), L, rayon(y) * 0.38, 0.75, 0.55, graine + 10 + k)
        pos, tri, uv, lg = mo.tube(spi, rr, cotes=8)
        m = Morceau(pos, tri, np.stack([uv[:, 0], uv[:, 1] / lg[-1]], 1), "ecorce_bouleau", (40, int(lg[-1] * 120) + 8))
        m.groupe = "tronc"
        morceaux.append(m)
        # rameaux et grappes
        for t in np.linspace(0.25, 1.0, 9):
            i = int(t * (len(spi) - 1))
            pt = spi[i] + rng.normal(size=3) * 0.22
            out = pt - _point_sur(spine, pt[1])
            out[1] = 0.25
            cartes.append(carte_feuilles(pt + np.array([0, -0.2, 0]), rng.uniform(1.1, 1.6), out + rng.normal(size=3) * 0.45,
                                         rng.uniform(0, 6.28), int(rng.integers(0, 4)), 4, "tronc", centre_h, R_h, penche=0.15))
    pointe_t = _point_sur(spine, H - 0.5)
    for k in range(7):
        cartes.append(carte_feuilles(pointe_t + rng.normal(size=3) * np.array([0.35, 0.3, 0.35]), rng.uniform(0.9, 1.2),
                                     rng.normal(size=3) + np.array([0, 0.6, 0]), rng.uniform(0, 6.28),
                                     int(rng.integers(0, 4)), 4, "tronc", centre_h, R_h, penche=0.08))
    # haut de la couronne, et remplissage intérieur
    for k in range(85):
        u = rng.normal(size=3)
        u /= np.linalg.norm(u)
        u[1] = abs(u[1]) * 0.9 - 0.15
        r = R_h * rng.uniform(0.3, 0.95) ** 0.7
        pt = centre_h + u * np.array([r, r * 1.3, r])
        cartes.append(carte_feuilles(pt, rng.uniform(1.1, 1.6), u + rng.normal(size=3) * 0.3, rng.uniform(0, 6.28),
                                     int(rng.integers(0, 4)), 4, "tronc", centre_h, R_h, penche=0.1))
    return morceaux, cartes, spine


def epicea(graine=8, entaille=0.0):
    rng = np.random.default_rng(graine)
    H = 12.0
    spine = _spine_tronc(H, graine, (0.06, 0.04))

    def rayon(y):
        base = 0.24 * (1 - 0.9 * np.clip(y / H, 0, 1)) + 0.02
        evase = 0.09 * math.exp(-max(y, 0) / 0.4)
        return base + evase

    morceaux = tronc_morceaux(spine, rayon, "ecorce_epicea", H, entaille=entaille, densite=80)
    morceaux += racines(spine, rayon, 6, graine + 1, "ecorce_epicea", 0.95)
    cartes = []
    y = 1.25
    k = 0
    while y < H - 0.3:
        t_h = (y - 1.25) / (H - 1.55)
        nbv = 6 if t_h < 0.55 else (5 if t_h < 0.8 else 4)
        decal = rng.uniform(0, 6.28)
        for j in range(nbv + 2):
            interne = j >= nbv
            a = decal + 2 * math.pi * (j if not interne else j + 0.5) / nbv + rng.uniform(-0.3, 0.3)
            L = (2.7 * (1 - t_h) ** 1.1 + 0.32) * rng.uniform(0.85, 1.12) * (0.55 if interne else 1.0)
            yy = y + (0.14 if interne else 0.0)
            base = _point_sur(spine, yy)
            montee = -0.2 * (1 - t_h) + 0.38 * t_h
            tombe = -0.13 * (1 - t_h) + 0.1 * t_h
            spi, rr = branche(base, (math.cos(a), 0, math.sin(a)), L, rayon(yy) * 0.2, montee, tombe, graine + 100 + k)
            k += 1
            cartes += rameau(spi, L, rng, graine + k, H)
        y += rng.uniform(0.27, 0.34) * (1 if t_h < 0.75 else 0.85)
    tip = _point_sur(spine, H - 0.35)
    for j in range(4):
        cartes.append(carte_feuilles(tip + np.array([0, 0.25, 0]), 0.85, (math.cos(j * 1.6), 0.15, math.sin(j * 1.6)),
                                     1.57, 1, 4, "tronc", None))
    # brindilles mortes au bas du tronc
    for j in range(10):
        yy = rng.uniform(0.75, 1.3)
        a = rng.uniform(0, 6.28)
        spi, rr = branche(_point_sur(spine, yy), (math.cos(a), 0, math.sin(a)), rng.uniform(0.3, 0.65), 0.02, -0.15, 0.05,
                          graine + 900 + j, k=5)
        pos, tri, uv, lg = mo.tube(spi, rr, cotes=5)
        m = Morceau(pos, tri, np.stack([uv[:, 0], uv[:, 1] / lg[-1]], 1), "ecorce_epicea", (16, 24))
        m.groupe = "tronc" if yy > HAUTEUR_COUPE else "souche"
        morceaux.append(m)
    centre = np.array([0, H * 0.4, 0])
    for m in cartes:
        if m.ao is None or np.all(m.ao == 1):
            rel = m.pos - centre
            rad = np.sqrt(rel[:, 0] ** 2 + rel[:, 2] ** 2)
            lim = np.maximum(2.9 * (1 - (m.pos[:, 1] - 1.2) / (H - 1.2)) + 0.3, 0.3)
            m.ao = np.clip(0.38 + 0.7 * (rad / lim) ** 1.2, 0.38, 1.0) * np.clip(0.8 + 0.25 * m.pos[:, 1] / H, 0.8, 1.0)
    return morceaux, cartes, spine


def rameau(spi, L, rng, graine, H):
    """Branche d'épicéa : trois bandes texturées qui suivent la branche (une à plat, une qui retombe, une qui monte)."""
    out = []
    segs = len(spi)
    for (incl, largeur) in ((0.0, 0.62), (-0.75, 0.46), (0.85, 0.34)):
        pts, uvs = [], []
        for i in range(segs):
            t = i / (segs - 1)
            avant = spi[min(i + 1, segs - 1)] - spi[max(i - 1, 0)]
            avant /= np.linalg.norm(avant)
            cote = np.cross(avant, [0, 1, 0])
            cote /= np.linalg.norm(cote)
            haut = np.cross(cote, avant)
            dirw = cote * math.cos(incl) + haut * math.sin(incl)
            w = L * largeur * (0.3 + 0.7 * math.sin(math.pi * (0.12 + 0.88 * t)) ** 0.5) * 0.5
            centre = spi[i] - haut * (0.06 * L * abs(math.sin(incl)))
            pts += [centre - dirw * w, centre + dirw * w]
            uvs += [(t, 1.0), (t, 0.0)]
        pts = np.array(pts)
        uvs = np.array(uvs)
        tri = []
        for i in range(segs - 1):
            a, b, c, d = 2 * i, 2 * i + 1, 2 * i + 2, 2 * i + 3
            tri += [(a, c, b), (b, c, d)]
        tri = np.array(tri)
        nor = mo.normales_sommets(pts, tri)
        nor[:, 1] = np.abs(nor[:, 1])
        outw = pts - np.array([0, pts[:, 1].mean(), 0])
        outw[:, 1] = 0
        outw /= np.maximum(np.linalg.norm(outw, axis=1, keepdims=True), 1e-6)
        nor = nor * 0.45 + outw * 0.4 + np.array([0, 0.35, 0])
        nor /= np.linalg.norm(nor, axis=1, keepdims=True)
        m = Morceau(pts, tri, uvs, "feuilles", (1, 1), nor=nor, decoupe=True)
        m.variante = int(rng.integers(0, 4))
        m.groupe = "tronc"
        m.occulte = False
        m.ao = None
        out.append(m)
    return out


def modele_arbre(espece, entaille=0.0, largeur=1024):
    ENTAILLE.clear()
    if espece == "bouleau":
        morceaux, cartes, spine = bouleau(entaille=entaille)
        peintes = [_peindre_grappe_bouleau(300 + i) for i in range(4)]
    else:
        morceaux, cartes, spine = epicea(entaille=entaille)
        peintes = [_peindre_rameau_epicea(400 + i, 256, 96) for i in range(4)]
    cuits = [m for m in morceaux]
    if entaille > 0:
        H = spine[-1, 1]
        if espece == "bouleau":
            ray = lambda y: 0.165 * (1 - 0.82 * min(max(y / H, 0), 1)) + 0.03 + 0.07 * math.exp(-max(y, 0) / 0.35)
        else:
            ray = lambda y: 0.24 * (1 - 0.9 * min(max(y / H, 0), 1)) + 0.02 + 0.09 * math.exp(-max(y, 0) / 0.4)
        ENTAILLE.update({"hc": HAUTEUR_COUPE, "dir": np.array([1.0, 0, 0]), "prof": entaille, "spine": spine, "rayon": ray})
    mo.ombre_cuite(cuits, sol_y=0.0, directions=96, plancher=0.35)
    tex, regions = _atlas_avec_peintures(cuits, peintes, largeur)
    if espece == "epicea":
        regions = regions + [regions[1]]
    _placer_feuilles(cartes, regions)
    return morceaux + cartes, tex, spine


# ============================================================================================================ scènes

PACK_CARTE = DEPOT   # dépôt dont on lit l'herbe et les arbres en blocs du pack (--pack : une autre copie du dépôt)


def _tex(chemin, defaut=None):
    try:
        return np.array(Image.open(chemin).convert("RGBA")).astype(np.float64) / 255.0
    except OSError:
        return defaut


def sol_herbe(cx=0, cz=0, rayon=7, relief=None):
    """Blocs d'herbe du pack (dessus teinté comme la plaine de la carte en île), autour de (cx, cz), dessus en y = 0."""
    base = PACK_CARTE / "modules/monde/src/client/resources/resourcepacks/heraliz_terrain/assets/minecraft/textures/block"
    top = _tex(base / "grass_block_top.png")
    side = _tex(base / "grass_block_side.png")
    over = _tex(base / "grass_block_side_overlay.png")
    if top is None:
        top = np.ones((16, 16, 4)) * 0.6
        side = np.ones((16, 16, 4)) * 0.35
        over = np.zeros((16, 16, 4))
    teinte = np.array([145, 180, 114]) / 255.0
    t = top.copy()
    t[..., :3] *= teinte
    s = side.copy()
    a = over[..., 3:4] if over.shape[-1] == 4 else 0
    s[..., :3] = s[..., :3] * (1 - a) + over[..., :3] * teinte * a
    W = t.shape[1]
    atlas = np.concatenate([t, s], 1)
    sommets = []
    for x in range(cx - rayon, cx + rayon):
        for z in range(cz - rayon, cz + rayon):
            if (x + 0.5 - cx) ** 2 + (z + 0.5 - cz) ** 2 > rayon * rayon:
                continue
            y = -1 if relief is None else relief(x, z) - 1
            sommets += mo.cube_sommets(x, y, z, (0, 0, W, W), (W, 0, 2 * W, W), (W, 0, 2 * W, W), 2 * W, W,
                                       faces=("haut", "n", "s", "e", "o"))
    ob = mo.Objet(np.array(sommets, "f4"), atlas, mode=1)
    return ob


def objets_gisement(etapes, tex, etape, x=0.0, z=0.0, rot=0.0):
    M = np.eye(4)
    c, s = math.cos(rot), math.sin(rot)
    M[:3, :3] = np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    M[0, 3], M[2, 3] = x, z
    return mo.objet_de(etapes[etape], tex, model=M)


def titre(im, texte, sous=None):
    d = ImageDraw.Draw(im)
    try:
        f = ImageFont.truetype(str(POLICE), 30)
        f2 = ImageFont.truetype(str(POLICE_MOY), 20)
    except OSError:
        f = f2 = ImageFont.load_default()
    d.rectangle([0, 0, im.width, 64 if sous else 50], fill=(18, 20, 24))
    d.text((24, 10), texte, font=f, fill=(236, 238, 240))
    if sous:
        d.text((24, 40), sous, font=f2, fill=(160, 168, 178))
    return im


def apercu_gisements(dossier: Path):
    dossier.mkdir(parents=True, exist_ok=True)
    noms = {"pierre": ("Gisement de pierre", "donne de la pierre brute -> pierres taillées"),
            "metal": ("Gisement de métal", "donne du minerai de fer -> fragments de métal"),
            "rare": ("Gisement de métal rare", "donne du minerai rare -> métal renforcé")}
    resultats = {}
    for nom in ("pierre", "metal", "rare"):
        etapes, tex = gisement(nom)
        mo.enregistre_png(tex, dossier / f"texture_{nom}.png")
        resultats[nom] = (etapes, tex)
        sol = sol_herbe(0, 0, 22)
        # vue principale
        im = mo.rendre([sol, objets_gisement(etapes, tex, 0, rot=0.6 if nom != "rare" else 2.4),
                        mannequin(-1.9, 0.9, rot=0.6)], oeil=(2.2, 1.7, 4.1), cible=(-0.5, 0.75, 0.2),
                       taille=(1400, 900), fov=42, ombres=[(0, 0, 0, 0.9, 0.35), (-1.9, 0, 0.9, 0.4, 0.3)], brume=(14, 45))
        titre(im, noms[nom][0], noms[nom][1]).save(dossier / f"gisement_{nom}.png")
        # les 4 étapes côte à côte
        obs = [sol_herbe(2, 0, 22)]
        for e in range(4):
            obs.append(objets_gisement(etapes, tex, e, x=e * 2.2 - 1.2, rot=0.6 if nom != "rare" else 2.4))
        im = mo.rendre(obs, oeil=(2.2, 2.6, 6.4), cible=(2.2, 0.3, 0), taille=(1600, 760), fov=44,
                       ombres=[(e * 2.2 - 1.2, 0, 0, 0.9 * mo_et(e), 0.35) for e in range(4)], brume=(14, 45))
        titre(im, noms[nom][0] + " : les 4 étapes", "chaque série de coups casse un morceau ; à la fin il disparaît "
                                                       "et réapparaît ailleurs").save(dossier / f"etapes_{nom}.png")
        print("rendu", nom)
    return resultats


def mo_et(e):
    return ETAPES[e]


# ============================================================================================================ outils

def modele_outil(nom, largeur=512):
    """Outil en cubes (recolte_cubes : comme les armes, modèle Blockbench) : morceaux et texture."""
    import recolte_cubes as rc
    ms, tex, n = rc.modele(nom, largeur)
    print(f"  {nom} : {n} cubes, {len(ms) * 2} triangles, texture {tex.shape[1]} x {tex.shape[0]}")
    return ms, tex


NOMS_OUTILS = {
    "hache": ("Hache de bûcheron", "Rare · abat les arbres ; manche en hickory, ruban noir, tête forgée, tranchant affûté ; "
                                   "72 cm, modèle en cubes comme les armes"),
    "pioche": ("Pioche", "Rare · casse les gisements ; manche en frêne, tissu kaki, tête forgée (pointe et tranchant plat) ; "
                         "86 cm, modèle en cubes"),
    "legendaire": ("Hache-pioche de survie", "Légendaire · le bois ET la pierre, plus vite ; composite noir, bagues en laiton, "
                                             "paracorde noir et or, tête noircie ; 80 cm, modèle en cubes")}


def apercu_outils(dossier: Path):
    dossier.mkdir(parents=True, exist_ok=True)
    for nom in ("hache", "pioche", "legendaire"):
        ms, tex = modele_outil(nom)
        mo.enregistre_png(tex, dossier / f"texture_{nom}.png")
        vues = []
        for (rot_y, oeil) in ((0.0, (0.42, 0.62, 0.95)), (0.0, (0.0, 0.45, 1.25)), (math.pi * 0.9, (0.55, 0.75, 0.7))):
            R = mo.rotation((0, 0, 1), -0.6) @ mo.rotation((0, 1, 0), rot_y)
            M = mo.pivot(R, (0, 0.4, 0))
            ob = mo.objet_de(ms, tex, model=M)
            centre = (M @ np.array([0.05, 0.55, 0, 1]))[:3]
            vues.append(mo.rendre([ob], oeil=np.array(oeil) + centre - np.array([0, 0.15, 0]), cible=centre,
                                  taille=(900, 760), fov=38, ciel=("#2A2E35", "#4A4F58"), brume=(50, 90)))
        R = mo.rotation((0, 0, 1), -0.6)
        M = mo.pivot(R, (0, 0.4, 0))
        ob = mo.objet_de(ms, tex, model=M)
        yt = {"hache": 0.67, "pioche": 0.83, "legendaire": 0.74}[nom]
        tete = (M @ np.array([0.0, yt, 0, 1]))[:3]
        gp = mo.rendre([ob], oeil=tete + np.array([0.13, 0.09, 0.3]), cible=tete, taille=(900, 760), fov=40,
                       ciel=("#2A2E35", "#4A4F58"), brume=(50, 90))
        planche = Image.new("RGB", (1800, 1520), (18, 20, 24))
        for i, im in enumerate([vues[0], vues[1], gp, vues[2]]):
            planche.paste(im, ((i % 2) * 900, (i // 2) * 760))
        titre(planche, NOMS_OUTILS[nom][0], NOMS_OUTILS[nom][1]).save(dossier / f"outil_{nom}.png")
        print("rendu", nom)


def texture_bras():
    """Bras du personnage (comme un skin de Minecraft, deux fois plus fin) : manche de veste kaki, poignet, main.
    En jeu, ce sera le skin du personnage du joueur (créateur /perso) ; ici une tenue de survivant neutre."""
    rng = np.random.default_rng(12)
    W, H = 16, 24
    t = np.ones((H, W, 4))
    veste = rgb("#5A6446")
    for y in range(H):
        for x in range(8):
            if y < 17:
                c = veste * (0.9 + 0.12 * rng.random())
                if x in (0, 7):
                    c = c * 0.86
                if y in (5, 11):
                    c = c * 0.85                       # plis de la manche
            elif y < 19:
                c = rgb("#3B4230") * (0.95 + 0.08 * rng.random())   # poignet
            else:
                c = rgb("#DDB394") * (0.94 + 0.08 * rng.random())   # main
                if y >= 21 and x in (2, 4, 6):
                    c = c * 0.8                        # doigts
                if y == 19:
                    c = c * 0.9
            t[y, x, :3] = c
    t[0:8, 8:16, :3] = rgb("#DDB394")                   # bout de la main
    t[8:16, 8:16, :3] = veste * 0.9                      # épaule
    return t


def bras(depuis, main, largeur=0.19, longueur=0.75, au_dela=0.07):
    """Bras (boîte de 4 x 12 x 4 px, comme Minecraft) qui va de l'épaule vers la main ; la main serre le manche
    (le manche passe dans le bout de la boîte). Rend un Objet dans le repère de la caméra."""
    d = np.asarray(main, float) - np.asarray(depuis, float)
    d /= np.linalg.norm(d)
    bout = np.asarray(main, float) + d * au_dela
    debut = bout - d * longueur
    haut = np.array([0, 1.0, 0])
    a = np.cross(d, haut)
    if np.linalg.norm(a) < 1e-3:
        a = np.array([1.0, 0, 0])
    a /= np.linalg.norm(a)
    b = np.cross(a, d)
    h = largeur / 2
    coins = lambda p: [p + (-a - b) * h, p + (a - b) * h, p + (a + b) * h, p + (-a + b) * h]
    D, F = coins(debut), coins(bout)
    S, T = 16.0, 24.0
    sommets = []

    def quad(p0, p1, p2, p3, n, uvs):
        for k in (0, 1, 2, 0, 2, 3):
            p = (p0, p1, p2, p3)[k]
            sommets.append([*p, *n, *uvs[k], 1, 1, 1])

    for i in range(4):
        j = (i + 1) % 4
        n = (D[i] + D[j]) / 2 - debut
        n /= np.linalg.norm(n)
        quad(D[i], D[j], F[j], F[i], n, [(0, 0), (8 / S, 0), (8 / S, 1), (0, 1)])
    quad(F[0], F[1], F[2], F[3], d, [(8 / S, 0), (1, 0), (1, 8 / T), (8 / S, 8 / T)])
    quad(D[3], D[2], D[1], D[0], -d, [(8 / S, 8 / T), (1, 8 / T), (1, 16 / T), (8 / S, 16 / T)])
    return mo.Objet(np.array(sommets, "f4"), texture_bras())


def _repere(U, F):
    U = np.asarray(U, float)
    U /= np.linalg.norm(U)
    F = np.asarray(F, float)
    F = F - np.dot(F, U) * U
    F /= np.linalg.norm(F)
    Z = np.cross(F, U)
    return np.stack([F, U, Z], 1)


PRISES = {"hache": (0.06, 0.34), "pioche": (0.06, 0.34), "legendaire": (0.06, 0.34)}
POSES_HACHE = [("Au repos", (0.2, -0.48, -0.5), (-0.45, 0.85, -0.25), (-0.9, 0.1, -0.3)),
               ("Élan", (0.25, -0.5, -0.6), (0.45, 0.8, 0.2), (-0.6, 0.25, -0.75)),
               ("Frappe", (0.22, -0.42, -0.58), (-0.6, 0.45, -0.66), (-0.7, -0.2, 0.6)),
               ("Impact", None, (-0.1, 0.2, -1.0), (-1.0, -0.05, -0.2))]
POSES_PIOCHE = [("Au repos", (0.22, -0.5, -0.52), (-0.35, 0.92, -0.15), (-0.9, -0.3, -0.3)),
                ("Élan", (0.2, -0.48, -0.62), (0.15, 0.85, 0.35), (-0.2, 0.3, -1.0)),
                ("Frappe", (0.16, -0.36, -0.6), (-0.05, 0.62, -0.78), (0.0, -0.8, -0.6)),
                ("Impact", None, (0.02, 0.15, -1.0), (0.0, -1.0, -0.15))]


def apercu_vue_joueur(dossier: Path):
    """Vue du joueur (première personne, champ de vision 70 comme Minecraft) : les deux mains tiennent le manche ;
    les moments du coup. L'outil légendaire : coup sur le bois (côté hache) puis sur la pierre (côté pic)."""
    import recolte_cubes as rc
    dossier.mkdir(parents=True, exist_ok=True)
    sp = sprites()
    epaule_d, epaule_g = np.array([0.7, -1.0, -0.25]), np.array([-0.6, -1.0, -0.25])
    sol = sol_herbe(0, 0, 22)
    ms_a, tex_a, _ = modele_arbre("bouleau", entaille=0.45)
    etapes, tex_g = gisement("pierre")
    scenes = {
        "arbre": (objets_arbre(ms_a, tex_a), np.array([1.75, 1.62, 0.25]), np.array([0.0, 0.8, 0.0]),
                  np.array([0.2, 0.62, 0.05]), "copeau"),
        "roche": ([objets_gisement(etapes, tex_g, 1, rot=0.9)], np.array([1.95, 1.62, 0.45]), np.array([0.1, 0.45, 0.0]),
                  np.array([0.62, 0.62, 0.2]), "eclat"),
    }
    plans = {
        "hache": [(t, "arbre", B, U, F, "hache", False) for (t, B, U, F) in POSES_HACHE],
        "pioche": [(t, "roche", B, U, F, "pioche", True) for (t, B, U, F) in POSES_PIOCHE],
        "legendaire": [("Au repos", "arbre") + POSES_HACHE[0][1:] + ("hache", False),
                       ("Impact sur le bois", "arbre") + POSES_HACHE[3][1:] + ("hache", False),
                       ("Élan, côté pic", "roche") + POSES_PIOCHE[1][1:] + ("pic", True),
                       ("Impact sur la pierre", "roche") + POSES_PIOCHE[3][1:] + ("pic", True)],
    }
    for nom in ("hache", "pioche", "legendaire"):
        ms, tex = modele_outil(nom)
        images = []
        for (titre_, scene, B, U, F, pointe, cote_pic) in plans[nom]:
            decor, oeil, regard_, impact, debris = scenes[scene]
            V = mo.regard(oeil, regard_)
            Vi = np.linalg.inv(V)
            cible_cam = (V @ np.append(impact, 1))[:3]
            R = rc_repere(U, -np.asarray(F, float) if cote_pic else F)
            if B is None:
                if pointe == "pic":
                    pt = rc.POINTES_PIC[nom]
                else:
                    pt = rc.POINTES[nom]
                B = cible_cam - R @ pt
            Mc = np.eye(4)
            Mc[:3, :3] = R
            Mc[:3, 3] = np.asarray(B, float)
            outil = mo.objet_de(ms, tex, model=Vi @ Mc)
            g1 = (Mc @ np.array([0, PRISES[nom][0], 0, 1]))[:3]
            g2 = (Mc @ np.array([0, PRISES[nom][1], 0, 1]))[:3]
            bd, bg = bras(epaule_d, g1), bras(epaule_g, g2)
            bd.model, bg.model = Vi.copy(), Vi.copy()
            parts = []
            if titre_.startswith("Impact"):
                rng = np.random.default_rng(3)
                for k in range(16):
                    p = impact + np.array([rng.uniform(0.05, 0.6), rng.uniform(-0.05, 0.45), rng.normal() * 0.35])
                    nm = debris if rng.random() < 0.75 else ("ecorce" if debris == "copeau" else "poussiere")
                    parts.append((*p, rng.uniform(0.05, 0.1), nm, 0.5 if nm == "poussiere" else 1.0, rng.uniform(0, 6.28),
                                  (1, 1, 1) if debris == "copeau" else (0.8, 0.78, 0.74)))
            im = mo.rendre([sol] + decor, oeil=oeil, cible=regard_, taille=(1000, 640), fov=70, brume=(20, 55),
                           particules=parts, sprites=sp, premier_plan=[outil, bd, bg])
            images.append(_cadre(im.convert("RGBA"), titre_).convert("RGB"))
        planche = Image.new("RGB", (1000 * 2 + 30, 640 * 2 + 120), (18, 20, 24))
        d = ImageDraw.Draw(planche)
        try:
            f = ImageFont.truetype(str(POLICE), 34)
            f2 = ImageFont.truetype(str(POLICE_MOY), 20)
        except OSError:
            f = f2 = ImageFont.load_default()
        d.text((24, 16), f"{NOMS_OUTILS[nom][0]} en main : le coup", font=f, fill=(238, 240, 242))
        d.text((24, 62), "vue du joueur ; les deux mains du personnage tiennent le manche (en jeu : le skin du joueur) ; "
                         "coup fluide d'environ 0,6 s", font=f2, fill=(160, 168, 178))
        for i, im in enumerate(images):
            planche.paste(im, (10 + (i % 2) * 1010, 110 + (i // 2) * 650))
        planche.save(dossier / f"vue_joueur_{nom}.png")
        print("rendu vue joueur", nom)


def rc_repere(U, F):
    return _repere(U, F)


def apercu_en_main(dossier: Path):
    apercu_vue_joueur(dossier)


def noms_outil(nom):
    return {"hache": "Hache", "pioche": "Pioche"}[nom]


# ============================================================================================================ décor des rendus

def sprites():
    """Images des particules (64 x 64) : copeau, écorce, feuille, poussière, éclat de pierre, étincelle."""
    rng = np.random.default_rng(77)
    out = {}

    def poly(points, col, taille=64, sur=4):
        im = Image.new("RGBA", (taille * sur, taille * sur), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        d.polygon([(x * sur, y * sur) for x, y in points], fill=col)
        return im

    im = poly([(8, 30), (50, 18), (58, 26), (20, 44), (6, 38)], (222, 196, 148, 255))
    ImageDraw.Draw(im).line([(32, 120), (200, 80)], fill=(176, 140, 96, 255), width=10)
    out["copeau"] = _seuil_alpha(im, (64, 64))
    im = poly([(10, 26), (52, 20), (56, 40), (14, 46)], (232, 228, 218, 255))
    ImageDraw.Draw(im).line([(60, 120), (180, 110)], fill=(40, 36, 32, 255), width=14)
    out["ecorce"] = _seuil_alpha(im, (64, 64))
    im = poly([(32, 6), (46, 24), (40, 50), (32, 58), (24, 50), (18, 24)], (118, 150, 62, 255))
    ImageDraw.Draw(im).line([(128, 30), (128, 230)], fill=(84, 112, 44, 255), width=6)
    out["feuille"] = _seuil_alpha(im, (64, 64))
    yy, xx = np.mgrid[0:64, 0:64]
    r = np.sqrt((xx - 31.5) ** 2 + (yy - 31.5) ** 2) / 32
    bruit = fbm3(np.stack([xx / 9.0, yy / 9.0, np.zeros_like(xx, float)], -1), 1.0, 4, 5)
    a = np.clip((1 - r ** 1.6) * (0.75 + 0.5 * bruit), 0, 1) ** 1.2
    out["poussiere"] = np.dstack([np.ones((64, 64, 3)), a])
    im = poly([(12, 34), (28, 12), (52, 22), (48, 46), (22, 52)], (150, 146, 138, 255))
    ImageDraw.Draw(im).polygon([(28 * 4, 12 * 4), (52 * 4, 22 * 4), (34 * 4, 30 * 4)], fill=(186, 182, 172, 255))
    out["eclat"] = _seuil_alpha(im, (64, 64))
    a = np.clip(1 - np.sqrt(((xx - 31.5) / 30) ** 2 + ((yy - 31.5) / 5) ** 2), 0, 1) ** 1.5
    out["etincelle"] = np.dstack([np.full((64, 64), 1.0), np.full((64, 64), 0.78), np.full((64, 64), 0.42), a])
    return out


def mannequin(x, z, rot=0.0):
    """Repère d'échelle : silhouette d'un joueur de Minecraft (1,875 m), tenue de survivant neutre (pas de skin)."""
    px = 0.9375 / 16
    couleurs = {"pantalon": "#3A4048", "veste": "#5A6446", "peau": "#C79C7E", "cheveux": "#3B2B20", "chaussure": "#2A2622"}
    noms = list(couleurs)
    tex = np.ones((1, len(noms), 4))
    for i, k in enumerate(noms):
        tex[0, i, :3] = rgb(couleurs[k])
    boites = [((-4, 0, -2), (0, 2, 2), "chaussure"), ((0, 0, -2), (4, 2, 2), "chaussure"),
              ((-4, 2, -2), (0, 12, 2), "pantalon"), ((0, 2, -2), (4, 12, 2), "pantalon"),
              ((-4, 12, -2), (4, 24, 2), "veste"),
              ((-8, 13, -2), (-4, 24, 2), "veste"), ((4, 13, -2), (8, 24, 2), "veste"),
              ((-8, 12, -2), (-4, 13, 2), "peau"), ((4, 12, -2), (8, 13, 2), "peau"),
              ((-4, 24, -4), (4, 31, 4), "peau"), ((-4.2, 30, -4.2), (4.2, 32.2, 4.2), "cheveux")]
    sommets = []
    for (a, b, coul) in boites:
        x0, y0, z0 = (np.array(a) * px)
        x1, y1, z1 = (np.array(b) * px)
        u = (noms.index(coul) + 0.5) / len(noms)
        faces = [((x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0), (0, 1, 0)),
                 ((x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1), (0, -1, 0)),
                 ((x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0), (0, 0, -1)),
                 ((x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1), (0, 0, 1)),
                 ((x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (1, 0, 0)),
                 ((x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0), (-1, 0, 0))]
        for (p0, p1, p2, p3, n) in faces:
            for k in (p0, p1, p2, p0, p2, p3):
                sommets.append([k[0], k[1], k[2], n[0], n[1], n[2], u, 0.5, 1, 1, 1])
    M = np.eye(4)
    c, s = math.cos(rot), math.sin(rot)
    M[:3, :3] = np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    M[0, 3], M[2, 3] = x, z
    return mo.Objet(np.array(sommets, "f4"), tex, model=M)


def arbre_vanille(x0, z0, espece="birch", hauteur=6):
    """Arbre en blocs de Minecraft (textures du pack du serveur) pour comparer."""
    base = PACK_CARTE / "modules/interface/src/main/resources/resourcepacks/textures/assets/minecraft/textures/block"
    log = _tex(base / f"{espece}_log.png", np.ones((16, 16, 4)) * 0.8)
    top = _tex(base / f"{espece}_log_top.png", np.ones((16, 16, 4)) * 0.7)
    feu = _tex(base / f"{espece}_leaves.png", np.ones((16, 16, 4)) * 0.5)
    teinte = {"birch": (0x80, 0xA7, 0x55), "spruce": (0x61, 0x99, 0x61), "oak": (122, 158, 81)}[espece]
    feu = feu.copy()
    feu[..., :3] *= np.array(teinte) / 255.0
    W = log.shape[1]
    tw = [log, top, feu]
    tw = [np.array(Image.fromarray((t * 255).astype(np.uint8), "RGBA").resize((W, W), Image.NEAREST)).astype(float) / 255 for t in tw]
    atlas = np.concatenate(tw, 1)
    sommets = []
    for y in range(hauteur):
        sommets += mo.cube_sommets(x0, y, z0, (W, 0, 2 * W, W), (0, 0, W, W), (W, 0, 2 * W, W), 3 * W, W,
                                   faces=("haut", "n", "s", "e", "o"))
    rng = np.random.default_rng(3)
    feuilles = []
    for y in range(hauteur - 3, hauteur + 1):
        R = 2 if y < hauteur - 1 else 1
        for dx in range(-R, R + 1):
            for dz in range(-R, R + 1):
                if (dx, dz) == (0, 0) and y < hauteur:
                    continue
                if abs(dx) == R and abs(dz) == R and (y >= hauteur - 1 or rng.random() < 0.5):
                    continue
                if y == hauteur and (abs(dx) + abs(dz)) > 1:
                    continue
                feuilles.append((x0 + dx, y, z0 + dz))
    for (x, y, z) in feuilles:
        sommets += mo.cube_sommets(x, y, z, (2 * W, 0, 3 * W, W), (2 * W, 0, 3 * W, W), (2 * W, 0, 3 * W, W), 3 * W, W,
                                   faces=("haut", "n", "s", "e", "o"))
    return mo.Objet(np.array(sommets, "f4"), atlas, mode=1, alpha_min=0.5)


def objets_arbre(ms, tex, angle=0.0, axe=(0, 0, 1), charniere=(0, HAUTEUR_COUPE, 0), sans_tronc=False, x=0.0, z=0.0,
                 sans_feuilles=False):
    obs = []
    T = np.eye(4)
    T[0, 3], T[2, 3] = x, z
    souche = mo.objet_de([m for m in ms if m.groupe == "souche"], tex, model=T)
    obs.append(souche)
    if not sans_tronc:
        M = T @ mo.pivot(mo.rotation(axe, angle), charniere)
        tronc = [m for m in ms if m.groupe == "tronc" and not (sans_feuilles and m.decoupe)]
        ob = mo.objet_de(tronc, tex, model=M)
        ob.alpha_min = 0.5
        obs.append(ob)
    return obs


def _cadre(im, texte, sous=None):
    d = ImageDraw.Draw(im)
    try:
        f = ImageFont.truetype(str(POLICE), 26)
        f2 = ImageFont.truetype(str(POLICE_MOY), 18)
    except OSError:
        f = f2 = ImageFont.load_default()
    h = 66 if sous else 44
    bande = Image.new("RGBA", (im.width, h), (16, 18, 22, 210))
    im.paste(bande, (0, im.height - h), bande)
    d.text((20, im.height - h + 8), texte, font=f, fill=(238, 240, 242))
    if sous:
        d.text((20, im.height - h + 40), sous, font=f2, fill=(170, 176, 186))
    return im


def apercu_chute(dossier: Path, espece="bouleau"):
    """La chute de l'arbre, image par image (vue de côté) : coups de hache, craquement, chute, impact, souche."""
    dossier.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(5)
    ms, tex, spine = modele_arbre(espece, entaille=0.62)
    sp = sprites()
    r_coupe = 0.2
    charn = (-r_coupe, HAUTEUR_COUPE, 0.0)          # l'arbre bascule sur le bord opposé à l'entaille
    sol = sol_herbe(-3, 0, 26)
    joueur = mannequin(1.25, 0.15, rot=-math.pi / 2)
    oeil, cible = (-3.4, 2.6, 11.8), (-3.4, 3.9, 0)
    H = spine[-1, 1]

    def pointe(angle):
        R = mo.rotation((0, 0, 1), angle)
        return np.array(charn) + R @ (np.array([0, H * 0.72, 0]) - np.array(charn))

    def copeaux(n, force=1.0):
        out = []
        for k in range(n):
            t = rng.uniform(0.15, 1.0)
            p = np.array([0.25 + 0.9 * t * force, HAUTEUR_COUPE + 0.05 + 0.5 * t - 0.6 * t * t, rng.normal() * 0.35])
            nm = "copeau" if rng.random() < 0.7 else "ecorce"
            out.append((*p, rng.uniform(0.12, 0.2), nm, 1.0, rng.uniform(0, 6.28), (1, 1, 1)))
        return out

    def feuilles_volantes(n, centre, dispersion, alpha=1.0):
        out = []
        for k in range(n):
            p = np.array(centre) + rng.normal(size=3) * np.array(dispersion)
            p[1] = max(p[1], 0.05)
            out.append((*p, rng.uniform(0.11, 0.17), "feuille", alpha, rng.uniform(0, 6.28),
                        tuple(rng.uniform(0.85, 1.15) * np.array([1, 1, 1]))))
        return out

    def poussiere(n, centre, dispersion, taille, alpha, teinte=(0.62, 0.56, 0.47)):
        out = []
        for k in range(n):
            p = np.array(centre) + rng.normal(size=3) * np.array(dispersion)
            p[1] = max(p[1], 0.25)
            out.append((*p, taille * rng.uniform(0.7, 1.3), "poussiere", alpha * rng.uniform(0.6, 1.0),
                        rng.uniform(0, 6.28), teinte))
        return out

    etapes = [
        ("1. Coups de hache", "copeaux et morceaux d'écorce à chaque coup, l'entaille se creuse", 0.0,
         copeaux(14) + feuilles_volantes(5, (0.3, 6.0, 0), (1.5, 1.5, 1.5))),
        ("2. Craquement", "dernier coup : le tronc craque et commence à pencher", math.radians(5),
         copeaux(6, 0.6) + poussiere(4, (-0.1, 0.65, 0), (0.25, 0.1, 0.25), 0.55, 0.35, (0.84, 0.74, 0.58))
         + feuilles_volantes(10, (0.0, 6.0, 0), (1.8, 1.8, 1.8))),
        ("3. Il bascule", "lent au début, comme un vrai arbre", math.radians(28),
         feuilles_volantes(16, pointe(math.radians(24)), (1.4, 1.6, 1.6))),
        ("4. Il accélère", "le houppier fouette l'air, des feuilles se détachent", math.radians(63),
         feuilles_volantes(26, pointe(math.radians(55)), (1.8, 1.5, 1.8))),
        ("5. Impact", "rebond, nuage de poussière et de feuilles au sol", math.radians(84),
         poussiere(22, (-6.0, 0.7, 0.6), (2.0, 0.35, 1.0), 2.3, 0.7, (0.82, 0.76, 0.66))
         + poussiere(10, (-2.6, 0.45, 0.5), (1.6, 0.15, 0.6), 1.4, 0.55, (0.8, 0.74, 0.64))
         + feuilles_volantes(55, (-6.0, 1.4, 0), (2.4, 1.0, 1.8))),
        ("6. La souche reste", "le tronc s'efface en 2 s (bois donné au joueur) ; la souche disparaît plus tard", None,
         [(*np.array([rng.uniform(-0.4, 1.4), 0.06, rng.normal() * 0.6]), rng.uniform(0.12, 0.18), "copeau", 1.0,
           rng.uniform(0, 6.28), (1, 1, 1)) for _ in range(16)]
         + feuilles_volantes(10, (-5.0, 0.08, 0), (2.5, 0.0, 1.4))),
    ]
    images = []
    for (titre_, sous, angle, parts) in etapes:
        obs = [sol, joueur]
        if angle is None:
            obs += objets_arbre(ms, tex, sans_tronc=True)
        else:
            obs += objets_arbre(ms, tex, angle=angle, charniere=charn)
        im = mo.rendre(obs, oeil=oeil, cible=cible, taille=(960, 640), fov=56, ombres=[(0, 0, 0, 0.7, 0.3), (1.25, 0, 0.15, 0.4, 0.3)],
                       brume=(22, 60), particules=parts, sprites=sp)
        images.append(_cadre(im.convert("RGBA"), titre_, sous).convert("RGB"))
    planche = Image.new("RGB", (960 * 3 + 40, 640 * 2 + 110), (18, 20, 24))
    d = ImageDraw.Draw(planche)
    try:
        f = ImageFont.truetype(str(POLICE), 34)
        f2 = ImageFont.truetype(str(POLICE_MOY), 20)
    except OSError:
        f = f2 = ImageFont.load_default()
    nom = "bouleau" if espece == "bouleau" else "épicéa"
    d.text((24, 16), f"Chute de l'arbre ({nom}) : environ 3 secondes", font=f, fill=(238, 240, 242))
    d.text((24, 60), "vue de côté ; le mannequin gris est un joueur (1,8 m) qui vient de donner les coups de hache",
           font=f2, fill=(160, 168, 178))
    for i, im in enumerate(images):
        planche.paste(im, (10 + (i % 3) * 970, 100 + (i // 3) * 650))
        im.save(dossier / f"chute_{espece}_{i + 1}.png")
    planche.save(dossier / f"chute_{espece}.png")
    print("rendu chute", espece)


def apercu_arbres(dossier: Path):
    dossier.mkdir(parents=True, exist_ok=True)
    for espece, nom, vanille in (("bouleau", "Bouleau à abattre", "birch"), ("epicea", "Épicéa à abattre", "spruce")):
        ms, tex, spine = modele_arbre(espece)
        mo.enregistre_png(tex, dossier / f"texture_{espece}.png")
        sol = sol_herbe(0, 0, 26)
        obs = [sol] + objets_arbre(ms, tex) + [mannequin(1.6, 1.4, rot=-2.3), arbre_vanille(-6, -2, vanille, 7 if espece == "epicea" else 6)]
        im = mo.rendre(obs, oeil=(5.8, 2.4, 10.0), cible=(-1.8, 4.4, 0), taille=(1300, 1000), fov=58,
                       ombres=[(0, 0, 0, 1.7, 0.3), (1.6, 0, 1.4, 0.4, 0.3)], brume=(20, 55))
        titre(im, nom, "modèle 3D, rare comme un gisement ; à gauche un arbre en blocs de la carte, devant un joueur "
                       "(1,8 m)").save(dossier / f"arbre_{espece}.png")
        # gros plan sur le pied : entaille et souche
        ms2, tex2, _ = modele_arbre(espece, entaille=0.62)
        sp = sprites()
        sf = espece == "epicea"
        im = mo.rendre([sol] + objets_arbre(ms2, tex2, sans_feuilles=sf), oeil=(1.75, 0.95, 1.05), cible=(0, 0.62, 0),
                       taille=(900, 700), fov=50, ombres=[(0, 0, 0, 0.8, 0.3)], brume=(20, 55),
                       particules=[(0.35 + 0.1 * k, 0.55 + 0.05 * math.sin(k * 2.0), 0.12 * math.cos(k * 3.0), 0.09,
                                    "copeau" if k % 3 else "ecorce", 1.0, k * 1.3, (1, 1, 1)) for k in range(6)], sprites=sp)
        titre(im, nom + " : l'entaille", "chaque coup de hache creuse le tronc" + (" (branches cachées pour voir le pied)"
                                                                                   if sf else "")).save(dossier / f"entaille_{espece}.png")
        im = mo.rendre([sol] + objets_arbre(ms2, tex2, sans_tronc=True), oeil=(1.6, 1.3, 1.8), cible=(0, 0.3, 0),
                       taille=(900, 700), fov=50, ombres=[(0, 0, 0, 0.8, 0.3)], brume=(20, 55),
                       particules=[(*np.array([np.cos(k) * 0.6, 0.05, np.sin(k) * 0.6]), 0.1, "copeau", 1.0, k, (1, 1, 1))
                                   for k in np.linspace(0, 6, 12)], sprites=sp)
        titre(im, nom + " : la souche", "elle reste après la chute, puis s'efface ; l'arbre repousse ailleurs").save(
            dossier / f"souche_{espece}.png")
        print("rendu", espece)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--apercu", type=Path, help="dossier des rendus (rien n'est installé)")
    ap.add_argument("--seulement", default="tout", help="gisements | arbres | chute | outils | vue_joueur | tout")
    ap.add_argument("--pack", type=Path, help="autre copie du dépôt dont on lit l'herbe du pack (ex. la branche de la carte)")
    a = ap.parse_args()
    global PACK_CARTE
    if a.pack:
        PACK_CARTE = a.pack
    if not a.apercu:
        ap.error("--apercu DOSSIER (l'installation dans le mod viendra après la validation des rendus)")
    if a.seulement in ("gisements", "tout"):
        apercu_gisements(a.apercu)
    if a.seulement in ("arbres", "tout"):
        apercu_arbres(a.apercu)
    if a.seulement in ("chute", "arbres", "tout"):
        apercu_chute(a.apercu, "bouleau")
    if a.seulement in ("outils", "tout"):
        apercu_outils(a.apercu)
    if a.seulement in ("vue_joueur", "outils", "tout"):
        apercu_vue_joueur(a.apercu)


if __name__ == "__main__":
    main()
