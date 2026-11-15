"""Moteur des modèles de la récolte (HeraliZ Récolte) : maillages, bruits, cuisson des textures et de l'ombre, rendus.

Outil hors du jeu, utilisé par recolte_modeles.py (rien n'est dessiné à la main : on modifie les scripts).

- Maillage : triangles, en mètres (1 bloc = 1 m), avec normales, coordonnées de texture et ombre cuite par sommet
  (couleur du sommet, comme le moteur du casino : fr.heraliz.casino.client.render.mesh.Mesh).
- Cuisson : chaque morceau a sa carte (« chart ») dans la texture du modèle ; la carte graphique dessine le morceau à
  plat dans la texture et rend, pour chaque point de texture, la position 3D et la normale ; la matière (pierre,
  écorce, acier...) est alors calculée en Python depuis la position 3D : pas de raccord visible entre deux cartes.
- Ombre cuite : visibilité du ciel de chaque sommet (128 directions, sol compris), calculée par la carte graphique.
- Rendus : éclairage des entités de Minecraft 1.20.1 (shader rendertype_entity_cutout : minecraft_mix_light, deux
  lumières fixes, 0,4 d'ambiance et 0,6 par lumière), textures sans lissage (comme le jeu), sol en blocs d'herbe du
  pack du serveur. Ce que montrent les rendus est ce que le jeu dessinera, à l'anticrénelage près (rendu 2x réduit).
"""
from __future__ import annotations

import math
from dataclasses import dataclass, field
from pathlib import Path

import moderngl
import numpy as np
from PIL import Image

# ============================================================================================================ bruits

_PERM_CACHE: dict[int, np.ndarray] = {}


def _perm(graine: int) -> np.ndarray:
    p = _PERM_CACHE.get(graine)
    if p is None:
        rng = np.random.default_rng(graine * 7919 + 17)
        p = rng.permutation(256).astype(np.int64)
        p = np.concatenate([p, p])
        _PERM_CACHE[graine] = p
    return p


_GRAD3 = np.array([[1, 1, 0], [-1, 1, 0], [1, -1, 0], [-1, -1, 0], [1, 0, 1], [-1, 0, 1], [1, 0, -1], [-1, 0, -1],
                   [0, 1, 1], [0, -1, 1], [0, 1, -1], [0, -1, -1], [1, 1, 0], [0, -1, 1], [-1, 1, 0], [0, -1, -1]],
                  np.float64)


def perlin3(p: np.ndarray, graine: int = 0) -> np.ndarray:
    """Bruit de gradient 3D (Perlin amélioré), p : (..., 3). Valeurs dans [-1, 1] environ."""
    p = np.asarray(p, np.float64)
    perm = _perm(graine)
    pi = np.floor(p).astype(np.int64)
    pf = p - pi
    pi &= 255
    u = pf * pf * pf * (pf * (pf * 6 - 15) + 10)
    x0, y0, z0 = pi[..., 0], pi[..., 1], pi[..., 2]
    res = 0.0
    out = np.zeros(p.shape[:-1])
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                h = perm[perm[perm[x0 + dx] + y0 + dy] + z0 + dz] & 15
                g = _GRAD3[h]
                d = pf - np.array([dx, dy, dz], np.float64)
                dot = (g * d).sum(-1)
                w = (u[..., 0] if dx else 1 - u[..., 0]) * (u[..., 1] if dy else 1 - u[..., 1]) * \
                    (u[..., 2] if dz else 1 - u[..., 2])
                out += w * dot
    return out * 1.0 + res


def fbm3(p, freq=1.0, octaves=4, graine=0, lacune=2.0, gain=0.5):
    p = np.asarray(p, np.float64)
    tot = np.zeros(p.shape[:-1])
    amp, norme = 1.0, 0.0
    f = freq
    for o in range(octaves):
        tot += amp * perlin3(p * f + o * 17.31, graine + o * 101)
        norme += amp
        amp *= gain
        f *= lacune
    return tot / norme


def ride3(p, freq=1.0, octaves=4, graine=0):
    """Bruit « en crêtes » (1 - |bruit|), dans [0, 1]."""
    p = np.asarray(p, np.float64)
    tot = np.zeros(p.shape[:-1])
    amp, norme, f = 1.0, 0.0, freq
    for o in range(octaves):
        tot += amp * (1 - np.abs(perlin3(p * f + o * 9.7, graine + o * 31)))
        norme += amp
        amp *= 0.5
        f *= 2.0
    return tot / norme


def voronoi3(p, freq=1.0, graine=0):
    """Cellules : (F1, F2, identifiant de cellule dans [0, 1)). p : (N, 3)."""
    p = np.asarray(p, np.float64) * freq
    c = np.floor(p).astype(np.int64)
    f1 = np.full(p.shape[0], 9.0)
    f2 = np.full(p.shape[0], 9.0)
    ident = np.zeros(p.shape[0])
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                cc = c + np.array([dx, dy, dz])
                h = _hash3(cc[:, 0], cc[:, 1], cc[:, 2], graine)
                jit = np.stack([_hash3(cc[:, 0], cc[:, 1], cc[:, 2], graine + k + 1) for k in range(3)], -1)
                pt = cc + 0.1 + 0.8 * jit
                d = np.sqrt(((pt - p) ** 2).sum(-1))
                plus_pres = d < f1
                f2 = np.where(plus_pres, f1, np.minimum(f2, d))
                ident = np.where(plus_pres, h, ident)
                f1 = np.where(plus_pres, d, f1)
    return f1, f2, ident


def _hash3(ix, iy, iz, graine):
    h = (np.asarray(ix, np.int64) * 374761393 + np.asarray(iy, np.int64) * 668265263
         + np.asarray(iz, np.int64) * 1274126177 + graine * 2654435761) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h = (h ^ (h >> 16)) & 0xFFFFFF
    return h / 16777216.0


def lisse(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def melange(a, b, t):
    a = np.asarray(a, np.float64)
    b = np.asarray(b, np.float64)
    t = np.asarray(t, np.float64)[..., None]
    return a * (1 - t) + b * t


def rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)])


# ============================================================================================================ maillage

@dataclass
class Morceau:
    """Morceau d'un modèle : triangles, une carte dans la texture, une matière."""
    pos: np.ndarray            # (N, 3) mètres
    tri: np.ndarray            # (M, 3)
    uv: np.ndarray             # (N, 2) coordonnées dans la carte, [0, 1]
    matiere: str               # nom de la fonction de matière
    taille_px: tuple           # taille de la carte dans la texture (largeur, hauteur) en points
    nor: np.ndarray | None = None
    extra: dict = field(default_factory=dict)   # attributs par sommet utiles à la matière (N, k)
    decoupe: bool = False      # matière avec transparence (feuilles) : rendu « cutout », deux faces
    ao: np.ndarray | None = None
    uv_atlas: np.ndarray | None = None
    groupe: str = "fixe"       # pour l'animation (ex. « tronc » qui tombe, « souche »)
    occulte: bool = True       # compte dans l'ombre cuite

    def normales(self):
        if self.nor is None:
            self.nor = normales_sommets(self.pos, self.tri)
        return self.nor


def normales_sommets(pos, tri):
    v0, v1, v2 = pos[tri[:, 0]], pos[tri[:, 1]], pos[tri[:, 2]]
    fn = np.cross(v1 - v0, v2 - v0)
    n = np.zeros_like(pos)
    for k in range(3):
        np.add.at(n, tri[:, k], fn)
    l = np.linalg.norm(n, axis=1, keepdims=True)
    return n / np.maximum(l, 1e-12)


def courbure(pos, tri, nor):
    """Courbure de chaque sommet : < 0 arête (bosse), > 0 creux ; ramenée dans [-1, 1] (percentiles)."""
    acc = np.zeros_like(pos)
    cnt = np.zeros(len(pos))
    for a, b in ((0, 1), (1, 2), (2, 0)):
        np.add.at(acc, tri[:, a], pos[tri[:, b]])
        np.add.at(acc, tri[:, b], pos[tri[:, a]])
        np.add.at(cnt, tri[:, a], 1)
        np.add.at(cnt, tri[:, b], 1)
    lap = acc / np.maximum(cnt, 1)[:, None] - pos
    k = (lap * nor).sum(1)
    e = np.percentile(np.abs(k), 92) + 1e-9
    return np.clip(k / e, -1, 1)


def icosphere(sub=4):
    t = (1 + 5 ** 0.5) / 2
    v = [(-1, t, 0), (1, t, 0), (-1, -t, 0), (1, -t, 0), (0, -1, t), (0, 1, t), (0, -1, -t), (0, 1, -t),
         (t, 0, -1), (t, 0, 1), (-t, 0, -1), (-t, 0, 1)]
    f = [(0, 11, 5), (0, 5, 1), (0, 1, 7), (0, 7, 10), (0, 10, 11), (1, 5, 9), (5, 11, 4), (11, 10, 2), (10, 7, 6),
         (7, 1, 8), (3, 9, 4), (3, 4, 2), (3, 2, 6), (3, 6, 8), (3, 8, 9), (4, 9, 5), (2, 4, 11), (6, 2, 10),
         (8, 6, 7), (9, 8, 1)]
    v = [np.array(x, float) / np.linalg.norm(x) for x in v]
    for _ in range(sub):
        cache = {}
        nf = []

        def mil(a, b):
            k = (min(a, b), max(a, b))
            if k not in cache:
                m = v[a] + v[b]
                v.append(m / np.linalg.norm(m))
                cache[k] = len(v) - 1
            return cache[k]

        for a, b, c in f:
            ab, bc, ca = mil(a, b), mil(b, c), mil(c, a)
            nf += [(a, ab, ca), (b, bc, ab), (c, ca, bc), (ab, bc, ca)]
        f = nf
    return np.array(v), np.array(f, np.int64)


def uv_cube(direc):
    """Projection d'une direction sur un cube : (face 0..5, u, v dans [0, 1])."""
    d = direc / np.linalg.norm(direc, axis=1, keepdims=True)
    ax = np.abs(d)
    face = np.argmax(ax, 1) * 2 + (d[np.arange(len(d)), np.argmax(ax, 1)] < 0)
    return face, d


def projete_face(d, face):
    """Coordonnées (u, v) gnomoniques d'une direction sur une face du cube, dans [0, 1] (un peu plus aux bords)."""
    x, y, z = d[:, 0], d[:, 1], d[:, 2]
    a = face // 2
    s = np.where(face % 2 == 0, 1.0, -1.0)
    with np.errstate(divide="ignore", invalid="ignore"):
        u = np.where(a == 0, -z * s / np.abs(x), np.where(a == 1, x / np.abs(y), x * s / np.abs(z)))
        v = np.where(a == 0, -y / np.abs(x), np.where(a == 1, -z * s / np.abs(y), -y / np.abs(z)))
    return np.stack([u * 0.5 + 0.5, v * 0.5 + 0.5], 1)


def tube(spine, rayons, cotes=16, ferme_bas=False, ferme_haut=False, ovale=None, torsion=0.0, bruit_r=None):
    """Tube le long d'une ligne (K, 3), rayon par point (K,), u autour, v le long (longueur réelle).
    ovale : (K,) rapport de la section (1 = rond). bruit_r(angle, i) -> facteur de rayon."""
    spine = np.asarray(spine, float)
    K = len(spine)
    t = np.gradient(spine, axis=0)
    t /= np.linalg.norm(t, axis=1, keepdims=True)
    # repère qui ne tourne pas (transport parallèle)
    ref = np.array([1.0, 0, 0]) if abs(t[0][1]) > 0.9 or True else np.array([0, 1.0, 0])
    if abs(np.dot(ref, t[0])) > 0.9:
        ref = np.array([0, 0, 1.0])
    n = np.cross(t[0], ref)
    n /= np.linalg.norm(n)
    ns = [n]
    for i in range(1, K):
        n = ns[-1] - np.dot(ns[-1], t[i]) * t[i]
        n /= np.linalg.norm(n)
        ns.append(n)
    ns = np.array(ns)
    bs = np.cross(t, ns)
    longueur = np.concatenate([[0], np.cumsum(np.linalg.norm(np.diff(spine, axis=0), axis=1))])
    pos, uv = [], []
    for i in range(K):
        for j in range(cotes + 1):
            a = 2 * math.pi * j / cotes + torsion * longueur[i]
            r = rayons[i]
            f = 1.0 if bruit_r is None else bruit_r(a, i)
            ov = 1.0 if ovale is None else ovale[i]
            p = spine[i] + (ns[i] * math.cos(a) * r * f + bs[i] * math.sin(a) * r * f * ov)
            pos.append(p)
            uv.append((j / cotes, longueur[i]))
    pos = np.array(pos)
    uv = np.array(uv)
    tri = []
    w = cotes + 1
    for i in range(K - 1):
        for j in range(cotes):
            a, b, c, d = i * w + j, i * w + j + 1, (i + 1) * w + j, (i + 1) * w + j + 1
            tri += [(a, c, b), (b, c, d)]
    tri = np.array(tri, np.int64)
    # faces tournées vers l'extérieur du tube
    v0, v1, v2 = pos[tri[:, 0]], pos[tri[:, 1]], pos[tri[:, 2]]
    fn = np.cross(v1 - v0, v2 - v0)
    ligne = tri[:, 0] // w
    dehors = (v0 + v1 + v2) / 3 - spine[np.clip(ligne, 0, K - 1)]
    if ((fn * dehors).sum(1) < 0).sum() > len(tri) / 2:
        tri = tri[:, [0, 2, 1]]
    return pos, tri, uv, longueur


def soude(pos, tri, tol=1e-6):
    """Fusionne les sommets confondus (pour des normales lisses sur un maillage fermé)."""
    cle = np.round(pos / tol).astype(np.int64)
    _, idx, inv = np.unique(cle, axis=0, return_index=True, return_inverse=True)
    return pos[idx], inv.reshape(-1)[tri]


# ============================================================================================================ GPU

_CTX = None


def ctx():
    global _CTX
    if _CTX is None:
        _CTX = moderngl.create_standalone_context(require=330)
    return _CTX


_VS_CUISSON = """
#version 330
in vec2 in_uv; in vec3 in_pos; in vec3 in_nor;
out vec3 v_pos; out vec3 v_nor;
void main(){ v_pos = in_pos; v_nor = in_nor; gl_Position = vec4(in_uv * 2.0 - 1.0, 0.0, 1.0); }
"""
_FS_CUISSON = """
#version 330
in vec3 v_pos; in vec3 v_nor;
layout(location=0) out vec4 o_pos; layout(location=1) out vec4 o_nor;
void main(){ o_pos = vec4(v_pos, 1.0); o_nor = vec4(normalize(v_nor), 1.0); }
"""


def cuire_geometrie(morceaux, largeur, hauteur):
    """Dessine chaque morceau à plat dans la texture : position 3D et normale de chaque point (nan : vide)."""
    c = ctx()
    prog = c.program(vertex_shader=_VS_CUISSON, fragment_shader=_FS_CUISSON)
    tp = c.texture((largeur, hauteur), 4, dtype="f4")
    tn = c.texture((largeur, hauteur), 4, dtype="f4")
    fbo = c.framebuffer([tp, tn])
    fbo.use()
    fbo.clear(0, 0, 0, 0)
    c.disable(moderngl.DEPTH_TEST | moderngl.CULL_FACE)
    for m in morceaux:
        uv = m.uv_atlas[m.tri].reshape(-1, 2)
        p = m.pos[m.tri].reshape(-1, 3)
        n = m.normales()[m.tri].reshape(-1, 3)
        data = np.hstack([uv, p, n]).astype("f4")
        vbo = c.buffer(data.tobytes())
        vao = c.vertex_array(prog, [(vbo, "2f 3f 3f", "in_uv", "in_pos", "in_nor")])
        vao.render(moderngl.TRIANGLES)
        vao.release()
        vbo.release()
    P = np.frombuffer(tp.read(), "f4").reshape(hauteur, largeur, 4).copy()
    N = np.frombuffer(tn.read(), "f4").reshape(hauteur, largeur, 4).copy()
    for o in (fbo, tp, tn, prog):
        o.release()
    return P, N


def cuire_morceau_id(morceaux, largeur, hauteur):
    """Index du morceau dessiné en chaque point de texture (-1 : vide) ; même méthode que cuire_geometrie."""
    c = ctx()
    prog = c.program(vertex_shader="""
#version 330
in vec2 in_uv; void main(){ gl_Position = vec4(in_uv*2.0-1.0, 0.0, 1.0); }""", fragment_shader="""
#version 330
uniform float idm; out vec4 o; void main(){ o = vec4(idm, 0, 0, 1); }""")
    t = c.texture((largeur, hauteur), 4, dtype="f4")
    fbo = c.framebuffer([t])
    fbo.use()
    fbo.clear(-1, 0, 0, 0)
    for i, m in enumerate(morceaux):
        uv = m.uv_atlas[m.tri].reshape(-1, 2).astype("f4")
        vbo = c.buffer(uv.tobytes())
        vao = c.vertex_array(prog, [(vbo, "2f", "in_uv")])
        prog["idm"].value = float(i)
        vao.render(moderngl.TRIANGLES)
        vao.release()
        vbo.release()
    I = np.frombuffer(t.read(), "f4").reshape(hauteur, largeur, 4)[..., 0].copy()
    for o in (fbo, t, prog):
        o.release()
    return np.round(I).astype(int)


def dilater(img, masque, pas=6):
    """Étend les bords des cartes dans le vide (pas de liseré sombre au bord des morceaux)."""
    img = img.copy()
    m = masque.copy()
    for _ in range(pas):
        acc = np.zeros_like(img)
        cnt = np.zeros(m.shape)
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0), (1, 1), (1, -1), (-1, 1), (-1, -1)):
            sm = np.roll(np.roll(m, dy, 0), dx, 1)
            si = np.roll(np.roll(img, dy, 0), dx, 1)
            acc += si * sm[..., None]
            cnt += sm
        nouveau = (~m) & (cnt > 0)
        img[nouveau] = acc[nouveau] / cnt[nouveau][:, None]
        m = m | nouveau
    return img


_VS_PROF = """
#version 330
uniform mat4 mvp; in vec3 in_pos;
void main(){ gl_Position = mvp * vec4(in_pos, 1.0); }
"""
_FS_PROF = """
#version 330
out vec4 o; void main(){ o = vec4(1.0); }
"""


def regard(oeil, cible, haut=(0, 1, 0)):
    oeil, cible, haut = (np.asarray(a, float) for a in (oeil, cible, haut))
    f = cible - oeil
    f /= np.linalg.norm(f)
    s = np.cross(f, haut)
    if np.linalg.norm(s) < 1e-6:
        s = np.cross(f, np.array([0, 0, 1.0]))
    s /= np.linalg.norm(s)
    u = np.cross(s, f)
    m = np.eye(4)
    m[0, :3], m[1, :3], m[2, :3] = s, u, -f
    m[:3, 3] = -m[:3, :3] @ oeil
    return m


def ortho(l, r, b, t, n, f):
    m = np.eye(4)
    m[0, 0] = 2 / (r - l)
    m[1, 1] = 2 / (t - b)
    m[2, 2] = -2 / (f - n)
    m[0, 3] = -(r + l) / (r - l)
    m[1, 3] = -(t + b) / (t - b)
    m[2, 3] = -(f + n) / (f - n)
    return m


def perspective(fov_deg, aspect, n, f):
    t = 1 / math.tan(math.radians(fov_deg) / 2)
    m = np.zeros((4, 4))
    m[0, 0] = t / aspect
    m[1, 1] = t
    m[2, 2] = (f + n) / (n - f)
    m[2, 3] = 2 * f * n / (n - f)
    m[3, 2] = -1
    return m


def ombre_cuite(morceaux, sol_y=None, directions=128, res=768, puissance=1.0, plancher=0.28):
    """Visibilité du ciel de chaque sommet de chaque morceau (sol plan compris si sol_y).
    Rend la valeur dans m.ao (0 = fermé, 1 = à l'air libre), déjà remappée [plancher, 1]."""
    c = ctx()
    prog = c.program(vertex_shader=_VS_PROF, fragment_shader=_FS_PROF)
    occl = [m for m in morceaux if m.occulte]
    tous = np.concatenate([m.pos for m in morceaux])
    centre = (tous.max(0) + tous.min(0)) / 2
    rayon = np.linalg.norm(tous - centre, axis=1).max() * 1.05 + 0.05
    tris = [m.pos[m.tri].reshape(-1, 3) for m in occl]
    if sol_y is not None:
        R = rayon * 6
        q = np.array([[-R, sol_y, -R], [R, sol_y, -R], [R, sol_y, R], [-R, sol_y, -R], [R, sol_y, R], [-R, sol_y, R]])
        tris.append(q + np.array([centre[0], 0, centre[2]]))
    data = np.concatenate(tris).astype("f4")
    vbo = c.buffer(data.tobytes())
    vao = c.vertex_array(prog, [(vbo, "3f", "in_pos")])
    dt = c.depth_texture((res, res))
    fbo = c.framebuffer(depth_attachment=dt)
    # directions : sphère de Fibonacci
    k = np.arange(directions) + 0.5
    phi = np.arccos(1 - 2 * k / directions)
    th = math.pi * (1 + 5 ** 0.5) * k
    dirs = np.stack([np.cos(th) * np.sin(phi), np.cos(phi), np.sin(th) * np.sin(phi)], 1)
    vis = [np.zeros(len(m.pos)) for m in morceaux]
    poids = [np.zeros(len(m.pos)) for m in morceaux]
    c.enable(moderngl.DEPTH_TEST)
    c.disable(moderngl.CULL_FACE)
    for d in dirs:
        oeil = centre + d * rayon * 3
        V = regard(oeil, centre, (0, 1, 0) if abs(d[1]) < 0.95 else (1, 0, 0))
        P = ortho(-rayon, rayon, -rayon, rayon, rayon * 0.5, rayon * 6)
        mvp = (P @ V).astype("f4")
        prog["mvp"].write(mvp.T.tobytes())
        fbo.use()
        fbo.clear(depth=1.0)
        vao.render(moderngl.TRIANGLES)
        prof = np.frombuffer(dt.read(), "f4").reshape(res, res)
        for i, m in enumerate(morceaux):
            n = m.normales()
            cosw = n @ d
            ok = cosw > 0.02
            if m.decoupe:
                cosw = np.abs(cosw) * 0.5 + 0.5
                ok = np.ones_like(ok)
            if not ok.any():
                continue
            p = m.pos + n * 0.012
            ph = np.hstack([p, np.ones((len(p), 1))]) @ mvp.T.astype(float)
            ndc = ph[:, :3] / ph[:, 3:4]
            px = np.clip(((ndc[:, 0] * 0.5 + 0.5) * res).astype(int), 0, res - 1)
            py = np.clip(((ndc[:, 1] * 0.5 + 0.5) * res).astype(int), 0, res - 1)
            z = ndc[:, 2] * 0.5 + 0.5
            libre = z <= prof[py, px] + 0.002
            w = np.where(ok, cosw, 0)
            vis[i] += libre * w
            poids[i] += w
    for i, m in enumerate(morceaux):
        a = vis[i] / np.maximum(poids[i], 1e-9)
        a = np.clip(a, 0, 1) ** puissance
        m.ao = plancher + (1 - plancher) * a
    for o in (vao, vbo, fbo, dt, prog):
        o.release()


# ============================================================================================================ atlas


def ranger(morceaux, largeur, marge=4):
    """Range les cartes des morceaux dans la texture (ligne d'horizon : chaque carte au plus haut possible) ; rend la
    hauteur utilisée, arrondie au multiple de 64."""
    ordre = sorted(range(len(morceaux)), key=lambda i: -(morceaux[i].taille_px[1] * 4 + morceaux[i].taille_px[0]))
    ciel = np.zeros(largeur, int)
    places = {}
    for i in ordre:
        w, h = morceaux[i].taille_px
        w, h = int(math.ceil(w)) + marge, int(math.ceil(h)) + marge
        w = min(w, largeur)
        if w >= largeur:
            meilleur_x, meilleur_y = 0, ciel.max()
        else:
            fen = np.lib.stride_tricks.sliding_window_view(ciel, w).max(1)
            meilleur_x = int(np.argmin(fen))
            meilleur_y = int(fen[meilleur_x])
        places[i] = (meilleur_x + marge // 2, meilleur_y + marge // 2, w - marge, h - marge)
        ciel[meilleur_x:meilleur_x + w] = meilleur_y + h
    total = int(ciel.max())
    H = ((total + 63) // 64) * 64
    for i, m in enumerate(morceaux):
        px, py, w, h = places[i]
        m.uv_atlas = np.stack([(px + m.uv[:, 0] * w) / largeur, (py + m.uv[:, 1] * h) / H], 1)
        m.rect = (px, py, w, h)
    return H


def cuire_texture(morceaux, matieres, largeur, hauteur=None, dilatation=8):
    """Texture du modèle : chaque point reçoit la couleur (et la transparence) de la matière de son morceau."""
    if hauteur is None:
        hauteur = ranger(morceaux, largeur)
    P, N = cuire_geometrie(morceaux, largeur, hauteur)
    I = cuire_morceau_id(morceaux, largeur, hauteur)
    img = np.zeros((hauteur, largeur, 4))
    plein = I >= 0
    for i, m in enumerate(morceaux):
        sel = I == i
        if not sel.any():
            continue
        p = P[sel][:, :3].astype(np.float64)
        n = N[sel][:, :3].astype(np.float64)
        # attributs supplémentaires interpolés : on prend le plus proche sommet (suffisant pour des marques douces)
        uvs = np.stack([(np.nonzero(sel)[1] + 0.5) / largeur, (np.nonzero(sel)[0] + 0.5) / hauteur], 1)
        ctxm = {"morceau": m, "uv": uvs}
        rect = getattr(m, "rect", None)
        if rect is not None:
            ys, xs = np.nonzero(sel)
            ctxm["uv_local"] = np.stack([(xs + 0.5 - rect[0]) / rect[2], (ys + 0.5 - rect[1]) / rect[3]], 1)
            ctxm["taille_carte"] = (rect[2], rect[3])
        if m.extra:
            from scipy.spatial import cKDTree
            arbre = cKDTree(m.pos)
            _, j = arbre.query(p)
            for k, val in m.extra.items():
                ctxm[k] = val[j]
        couleur = matieres[m.matiere](p, n, ctxm)
        if couleur.shape[-1] == 3:
            couleur = np.concatenate([couleur, np.ones((len(couleur), 1))], 1)
        img[sel] = couleur
    img = dilater(img, plein, dilatation)   # couleur ET transparence : aucun bord vide entre deux cartes
    return np.clip(img, 0, 1), hauteur


def enregistre_png(img, chemin):
    Path(chemin).parent.mkdir(parents=True, exist_ok=True)
    a = (np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8)
    Image.fromarray(a, "RGBA" if a.shape[-1] == 4 else "RGB").save(chemin)


# ============================================================================================================ rendus

_VS = """
#version 330
uniform mat4 vp; uniform mat4 model; uniform mat3 nmat;
in vec3 in_pos; in vec3 in_nor; in vec2 in_uv; in vec3 in_col;
out vec3 v_nor; out vec2 v_uv; out vec3 v_col; out vec3 v_mpos;
void main(){
  vec4 w = model * vec4(in_pos, 1.0);
  v_mpos = w.xyz; v_nor = normalize(nmat * in_nor); v_uv = in_uv; v_col = in_col;
  gl_Position = vp * w;
}
"""
# éclairage de Minecraft 1.20.1 (include/light.glsl, minecraft_mix_light) : MINECRAFT_LIGHT_POWER 0.6, AMBIENT 0.4
_FS = """
#version 330
uniform sampler2D tex; uniform int mode; uniform vec3 teinte; uniform float alpha_min; uniform float lumiere;
uniform vec3 brume_col; uniform float brume_debut; uniform float brume_fin; uniform vec3 oeil;
in vec3 v_nor; in vec2 v_uv; in vec3 v_col; in vec3 v_mpos;
out vec4 o;
const vec3 L0 = normalize(vec3(0.2, 1.0, -0.7));
const vec3 L1 = normalize(vec3(-0.2, 1.0, 0.7));
void main(){
  vec4 c = texture(tex, v_uv);
  if (c.a < alpha_min) discard;
  vec3 n = normalize(v_nor);
  if (!gl_FrontFacing && mode == 0) n = -n;
  float l;
  if (mode == 1) {
    // bloc de Minecraft : ombrage par face
    vec3 a = abs(n);
    l = a.y > 0.5 ? (n.y > 0.0 ? 1.0 : 0.5) : (a.z > a.x ? 0.8 : 0.6);
  } else {
    l = min(1.0, 0.4 + 0.6 * (max(0.0, dot(L0, n)) + max(0.0, dot(L1, n))));
  }
  vec3 col = c.rgb * teinte * v_col * l * lumiere;
  float d = length(v_mpos - oeil);
  float f = clamp((d - brume_debut) / (brume_fin - brume_debut), 0.0, 1.0);
  o = vec4(mix(col, brume_col, f), 1.0);
}
"""


@dataclass
class Objet:
    """Un modèle prêt à dessiner : sommets (position, normale, uv, couleur), texture."""
    sommets: np.ndarray       # (T*3, 11)
    texture: np.ndarray       # (H, W, 4)
    mode: int = 0             # 0 entité, 1 bloc
    teinte: tuple = (1, 1, 1)
    alpha_min: float = 0.1
    model: np.ndarray = field(default_factory=lambda: np.eye(4))


def objet_de(morceaux, texture, model=None, groupes=None):
    lignes = []
    for m in morceaux:
        if groupes is not None and m.groupe not in groupes:
            continue
        n = m.normales()
        ao = m.ao if m.ao is not None else np.ones(len(m.pos))
        col = np.repeat(ao[:, None], 3, 1)
        if "teinte_sommet" in m.extra:
            col = col * m.extra["teinte_sommet"]
        v = np.hstack([m.pos, n, m.uv_atlas, col])[m.tri].reshape(-1, 11)
        lignes.append(v)
    return Objet(np.concatenate(lignes).astype("f4"), texture, model=np.eye(4) if model is None else model)


def rendre(objets, oeil, cible, taille=(1600, 1000), fov=40.0, ciel=("#79A7E8", "#C9DDF2"), sur=2,
           brume=(60.0, 140.0), lumiere=1.0, ombres=(), particules=(), sprites=None, premier_plan=(), transparent=False,
           orthographique=None):
    """Image d'une scène : objets (Objet), caméra en perspective. ombres : [(x, y, z, rayon, force)] (ombre ronde
    des entités de Minecraft, posée sur le sol)."""
    c = ctx()
    W, H = taille[0] * sur, taille[1] * sur
    col = c.texture((W, H), 4)
    dep = c.depth_renderbuffer((W, H))
    fbo = c.framebuffer([col], dep)
    fbo.use()
    haut, bas = rgb(ciel[0]), rgb(ciel[1])
    fbo.clear(*bas, 1.0)
    prog = c.program(vertex_shader=_VS, fragment_shader=_FS)
    V = regard(oeil, cible)
    if orthographique:
        h_ = orthographique / 2
        P = ortho(-h_ * W / H, h_ * W / H, -h_, h_, 0.05, 400)
    else:
        P = perspective(fov, W / H, 0.05, 400)
    VP = (P @ V).astype("f4")
    prog["vp"].write(VP.T.tobytes())
    prog["lumiere"].value = lumiere
    prog["brume_col"].value = tuple(bas)
    prog["brume_debut"].value = brume[0]
    prog["brume_fin"].value = brume[1]
    prog["oeil"].value = tuple(float(x) for x in oeil)
    # ciel en dégradé
    ciel_prog = c.program(vertex_shader="""
#version 330
in vec2 p; out float h; void main(){ h = p.y*0.5+0.5; gl_Position = vec4(p, 0.999, 1.0); }""",
                          fragment_shader="""
#version 330
uniform vec3 a; uniform vec3 b; in float h; out vec4 o; void main(){ o = vec4(mix(b, a, smoothstep(0.35, 1.0, h)), 1.0); }""")
    ciel_prog["a"].value = tuple(haut)
    ciel_prog["b"].value = tuple(bas)
    q = c.buffer(np.array([[-1, -1], [1, -1], [1, 1], [-1, -1], [1, 1], [-1, 1]], "f4").tobytes())
    vq = c.vertex_array(ciel_prog, [(q, "2f", "p")])
    c.disable(moderngl.DEPTH_TEST)
    if transparent:
        fbo.clear(0, 0, 0, 0)
    else:
        vq.render(moderngl.TRIANGLES)
    c.enable(moderngl.DEPTH_TEST)
    c.disable(moderngl.CULL_FACE)
    textures = []
    for ob in objets:
        t = c.texture((ob.texture.shape[1], ob.texture.shape[0]), 4,
                      (np.clip(ob.texture, 0, 1) * 255 + 0.5).astype(np.uint8).tobytes())  # v = 0 : haut de l'image (Minecraft)
        t.filter = (moderngl.NEAREST, moderngl.NEAREST)
        t.repeat_x = t.repeat_y = True
        textures.append(t)
        vbo = c.buffer(ob.sommets.tobytes())
        vao = c.vertex_array(prog, [(vbo, "3f 3f 2f 3f", "in_pos", "in_nor", "in_uv", "in_col")])
        prog["model"].write(ob.model.astype("f4").T.tobytes())
        nm = np.linalg.inv(ob.model[:3, :3]).T
        prog["nmat"].write(nm.astype("f4").T.tobytes())
        prog["mode"].value = ob.mode
        prog["teinte"].value = tuple(float(x) for x in ob.teinte)
        prog["alpha_min"].value = ob.alpha_min
        t.use(0)
        prog["tex"].value = 0
        vao.render(moderngl.TRIANGLES)
        vao.release()
        vbo.release()
    # ombres rondes des entités (comme EntityRenderDispatcher.renderShadow : disque sombre posé au sol)
    if ombres:
        op = c.program(vertex_shader="""
#version 330
uniform mat4 vp; in vec3 p; in vec2 l; out vec2 v; void main(){ v = l; gl_Position = vp*vec4(p,1.0); }""",
                       fragment_shader="""
#version 330
uniform float force; in vec2 v; out vec4 o;
void main(){ float d = length(v); float a = force * (1.0 - smoothstep(0.55, 1.0, d)); o = vec4(0.0, 0.0, 0.0, a); }""")
        op["vp"].write(VP.T.tobytes())
        c.enable(moderngl.BLEND)
        c.blend_func = moderngl.SRC_ALPHA, moderngl.ONE_MINUS_SRC_ALPHA
        c.depth_mask = False
        for (x, y, z, r, f) in ombres:
            pts = np.array([[x - r, y + 0.005, z - r, -1, -1], [x + r, y + 0.005, z - r, 1, -1],
                            [x + r, y + 0.005, z + r, 1, 1], [x - r, y + 0.005, z - r, -1, -1],
                            [x + r, y + 0.005, z + r, 1, 1], [x - r, y + 0.005, z + r, -1, 1]], "f4")
            b = c.buffer(pts.tobytes())
            va = c.vertex_array(op, [(b, "3f 2f", "p", "l")])
            op["force"].value = f
            va.render(moderngl.TRIANGLES)
            va.release()
            b.release()
        c.depth_mask = True
        c.disable(moderngl.BLEND)
        op.release()
    if particules:
        _dessiner_particules(c, VP, V, particules, sprites)
    if premier_plan:
        # comme les mains de Minecraft : dessinées après le monde, sans être cachées par lui
        _effacer_profondeur(c, fbo)
        for ob in premier_plan:
            t = c.texture((ob.texture.shape[1], ob.texture.shape[0]), 4,
                          (np.clip(ob.texture, 0, 1) * 255 + 0.5).astype(np.uint8).tobytes())
            t.filter = (moderngl.NEAREST, moderngl.NEAREST)
            textures.append(t)
            vbo = c.buffer(ob.sommets.tobytes())
            vao = c.vertex_array(prog, [(vbo, "3f 3f 2f 3f", "in_pos", "in_nor", "in_uv", "in_col")])
            prog["model"].write(ob.model.astype("f4").T.tobytes())
            prog["nmat"].write(np.linalg.inv(ob.model[:3, :3]).T.astype("f4").T.tobytes())
            prog["mode"].value = ob.mode
            prog["teinte"].value = tuple(float(x) for x in ob.teinte)
            prog["alpha_min"].value = ob.alpha_min
            prog["brume_debut"].value = 1000.0
            prog["brume_fin"].value = 2000.0
            t.use(0)
            vao.render(moderngl.TRIANGLES)
            vao.release()
            vbo.release()
    if transparent:
        img = np.frombuffer(col.read(), np.uint8).reshape(H, W, 4)[::-1].copy()
        im = Image.fromarray(img, "RGBA")
    else:
        img = np.frombuffer(col.read(), np.uint8).reshape(H, W, 4)[::-1, :, :3]
        im = Image.fromarray(img.copy(), "RGB")
    if sur > 1:
        im = im.resize((taille[0], taille[1]), Image.LANCZOS)
    for o in textures + [fbo, col, dep, prog, ciel_prog, q, vq]:
        o.release()
    return im


# ============================================================================================================ sol de blocs

def cube_sommets(x, y, z, uv_haut, uv_cote, uv_bas, tex_w, tex_h, faces=("haut", "n", "s", "e", "o")):
    """Sommets (11 flottants) d'un bloc de 1 m posé en (x, y, z) (coin), uv : (u0, v0, u1, v1) en points de texture."""
    out = []

    def quad(p0, p1, p2, p3, n, uvr):
        u0, v0, u1, v1 = uvr[0] / tex_w, uvr[1] / tex_h, uvr[2] / tex_w, uvr[3] / tex_h
        uvs = [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]
        for a, b, c in ((0, 1, 2), (0, 2, 3)):
            for k in (a, b, c):
                p = (p0, p1, p2, p3)[k]
                out.append([p[0], p[1], p[2], n[0], n[1], n[2], uvs[k][0], uvs[k][1], 1, 1, 1])

    X, Y, Z = x + 1, y + 1, z + 1
    if "haut" in faces:
        quad((x, Y, Z), (X, Y, Z), (X, Y, z), (x, Y, z), (0, 1, 0), uv_haut)
    if "n" in faces:
        quad((X, y, z), (x, y, z), (x, Y, z), (X, Y, z), (0, 0, -1), uv_cote)
    if "s" in faces:
        quad((x, y, Z), (X, y, Z), (X, Y, Z), (x, Y, Z), (0, 0, 1), uv_cote)
    if "e" in faces:
        quad((X, y, Z), (X, y, z), (X, Y, z), (X, Y, Z), (1, 0, 0), uv_cote)
    if "o" in faces:
        quad((x, y, z), (x, y, Z), (x, Y, Z), (x, Y, z), (-1, 0, 0), uv_cote)
    return out


_VS_PART = """
#version 330
uniform mat4 vp; uniform vec3 droite; uniform vec3 haut;
in vec3 p; in vec2 coin; in vec4 uvr; in vec4 col; in float taille; in float rot;
out vec2 v_uv; out vec4 v_col;
void main(){
  float c = cos(rot), s = sin(rot);
  vec2 k = vec2(coin.x * c - coin.y * s, coin.x * s + coin.y * c);
  vec3 w = p + (droite * k.x + haut * k.y) * taille * 0.5;
  v_uv = vec2(mix(uvr.x, uvr.z, coin.x * 0.5 + 0.5), mix(uvr.w, uvr.y, coin.y * 0.5 + 0.5));
  v_col = col;
  gl_Position = vp * vec4(w, 1.0);
}
"""
_FS_PART = """
#version 330
uniform sampler2D tex; in vec2 v_uv; in vec4 v_col; out vec4 o;
void main(){ vec4 t = texture(tex, v_uv); float a = t.a * v_col.a; if (a < 0.01) discard; o = vec4(t.rgb * v_col.rgb, a); }
"""


def _effacer_profondeur(c, fbo):
    """Remet la profondeur à zéro sans toucher aux couleurs (quad plein écran qui n'écrit que la profondeur)."""
    p = c.program(vertex_shader="""
#version 330
in vec2 q; void main(){ gl_Position = vec4(q, 1.0, 1.0); }""", fragment_shader="""
#version 330
out vec4 o; void main(){ o = vec4(0.0); }""")
    b = c.buffer(np.array([[-1, -1], [1, -1], [1, 1], [-1, -1], [1, 1], [-1, 1]], "f4").tobytes())
    va = c.vertex_array(p, [(b, "2f", "q")])
    c.enable(moderngl.BLEND)
    c.blend_func = moderngl.ZERO, moderngl.ONE          # la couleur ne change pas, seule la profondeur est écrite
    c.depth_func = "1"
    va.render(moderngl.TRIANGLES)
    c.depth_func = "<"
    c.disable(moderngl.BLEND)
    for o in (va, b, p):
        o.release()


def _dessiner_particules(c, VP, V, particules, sprites):
    """particules : [(x, y, z, taille, nom_sprite, alpha, rotation, (r, g, b))] ; sprites : {nom: image RGBA}."""
    noms = list(sprites.keys())
    T = 64
    atlas = np.zeros((T, T * len(noms), 4))
    regions = {}
    for i, nm in enumerate(noms):
        im = sprites[nm]
        atlas[:, i * T:(i + 1) * T] = np.array(Image.fromarray((np.clip(im, 0, 1) * 255).astype(np.uint8), "RGBA")
                                               .resize((T, T), Image.LANCZOS)).astype(float) / 255
        regions[nm] = (i / len(noms), 0.0, (i + 1) / len(noms), 1.0)
    tex = c.texture((atlas.shape[1], T), 4, (atlas * 255 + 0.5).astype(np.uint8).tobytes())
    tex.filter = (moderngl.LINEAR, moderngl.LINEAR)
    prog = c.program(vertex_shader=_VS_PART, fragment_shader=_FS_PART)
    prog["vp"].write(VP.T.tobytes())
    Vi = np.linalg.inv(V)
    prog["droite"].value = tuple(Vi[:3, 0])
    prog["haut"].value = tuple(Vi[:3, 1])
    oeil = Vi[:3, 3]
    ordre = sorted(particules, key=lambda q: -np.linalg.norm(np.array(q[:3]) - oeil))
    lignes = []
    for (x, y, z, s, nm, a, rot, teinte) in ordre:
        u0, v0, u1, v1 = regions[nm]
        for (cx, cy) in ((-1, -1), (1, -1), (1, 1), (-1, -1), (1, 1), (-1, 1)):
            lignes.append([x, y, z, cx, cy, u0, v0, u1, v1, teinte[0], teinte[1], teinte[2], a, s, rot])
    data = np.array(lignes, "f4")
    vbo = c.buffer(data.tobytes())
    vao = c.vertex_array(prog, [(vbo, "3f 2f 4f 4f 1f 1f", "p", "coin", "uvr", "col", "taille", "rot")])
    c.enable(moderngl.BLEND)
    c.blend_func = moderngl.SRC_ALPHA, moderngl.ONE_MINUS_SRC_ALPHA
    c.depth_mask = False
    tex.use(0)
    prog["tex"].value = 0
    vao.render(moderngl.TRIANGLES)
    c.depth_mask = True
    c.disable(moderngl.BLEND)
    for o in (vao, vbo, prog, tex):
        o.release()


def rotation(axe, angle):
    axe = np.asarray(axe, float)
    axe /= np.linalg.norm(axe)
    x, y, z = axe
    c, s = math.cos(angle), math.sin(angle)
    C = 1 - c
    return np.array([[c + x * x * C, x * y * C - z * s, x * z * C + y * s],
                     [y * x * C + z * s, c + y * y * C, y * z * C - x * s],
                     [z * x * C - y * s, z * y * C + x * s, c + z * z * C]])


def pivot(R, point, translation=(0, 0, 0)):
    """Matrice 4x4 : rotation R autour de point, puis translation."""
    M = np.eye(4)
    M[:3, :3] = R
    p = np.asarray(point, float)
    M[:3, 3] = p - R @ p + np.asarray(translation, float)
    return M
