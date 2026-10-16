"""Animation 1re personne du coup puissant (clic droit) des couteaux TaCZ (choix du propriétaire, 16/10).

Le coup rapide (clic gauche) est celui du gunpack, « melee_push », joué par TaCZ comme avant la 2.16.0 : ce script n'y
touche pas. Il écrit pour chacun des 5 modèles (ct_knife, t_knife, m9, butterfly_knife, karambit ; les skins de
heralisz_skins s'en servent) le coup puissant « melee_stock », fait comme le coup lourd des couteaux de LR Tactical
(« melee_3 » de la Dague tactique et du karambit de LR) : LE COUTEAU LUI-MÊME TOURNE avec le geste du bras.

- poignard (CT, T, M9, papillon) : en armant (0,16 s), le bras monte et recule, le poignet se casse et la lame se
  dresse, pointe vers le haut ; puis la poussée : le poignet et la lame tournent pour amener la pointe droit sur le
  réticule (impact à 0,24 s), tenue avec une torsion du tranchant, dégagement, retour lent ;
- karambit (tenu lame vers le bas) : bras armé à droite, lame relevée, puis crochet vers le bas et l'avant, la courbe
  de la lame menant le coup ; au retour, un tour complet du karambit autour de son anneau (comme le karambit de LR).

Comment : chaque geste est écrit dans le repère vu à l'écran (x à droite, y en haut, z vers soi ; degrés, tangage + =
vers le haut, lacet + = vers la gauche, roulis + = sens inverse des aiguilles d'une montre). La direction voulue de la
lame (« visée ») est atteinte par une rotation dont une part (PART_POIGNET) va au poignet (main et couteau ensemble) et
le reste au couteau seul, qui tourne dans la main autour du point de prise ; axe de la lame, épaisseur, prise et anneau
sont calculés depuis le modèle 3D. Le tout est converti en valeurs Bedrock pour trois os de chaque modèle (bras,
poignet, couteau) et écrit toutes les 1/60 s (angles déroulés pour rester continus) : TaCZ interpole les angles un par
un, des clés espacées tordraient les rotations. Le reste du modèle garde sa pose de repos (« static_idle » au temps 0).

Le mod Tactique donne l'animation à TaCZ chez le joueur (client/couteaux/AnimCouteaux) et la joue sur la piste
principale (transition courte) ; secours : signal « bayonet_stock ». L'impact (IMPACT ci-dessous), la secousse de
caméra (client/couteaux/SecousseCouteau) et le délai du coup puissant côté serveur (CoupsCouteau.DELAI_COUP_LOURD, en
ticks) vont ensemble : à changer ensemble. Les gunpacks ne sont pas modifiés.

Écrit src/client/resources/assets/heraliz_tactique/couteaux/<modèle>.animation.json.
Usage : python mods/heraliz-tactique/outils/couteaux_coups.py [--apercu DOSSIER]
        (--apercu : planches d'images clés, vue 1re personne et vue de côté, lame dessinée d'après le modèle 3D avec sa
        pointe en rouge, et le coup rapide du gunpack pour comparer ; Pillow). Le script demande numpy.
"""
import argparse
import json
import math
import os

import numpy as np

ICI = os.path.dirname(os.path.abspath(__file__))
PROJET = os.path.normpath(os.path.join(ICI, ".."))
DEPOT = os.path.normpath(os.path.join(PROJET, "..", ".."))
PACK = os.path.join(DEPOT, "serveur", "tacz", "arkaniaz_pack_4_converted", "assets", "arkaniaz")
SOURCE = os.path.join(PACK, "animations")
SORTIE = os.path.join(PROJET, "src", "client", "resources", "assets", "heraliz_tactique", "couteaux")

CLIP = "melee_stock"
DUREE = 0.85  # cadence du coup puissant : 0,9 s
IMPACT = 0.24  # la pointe arrive au centre : CoupsCouteau.DELAI_COUP_LOURD = 5 ticks (0,25 s)
PAS = 1.0 / 60.0

# modèle -> os du bras, os qui oriente la lame (poignet : main et couteau ensemble), os du couteau seul, modèle 3D
# (aperçu, axes de la lame), geste
MODELES = {
    "ct_knife": ("right_elbow", "right_wrist", "knife", "ct_knife_geo", "poignard"),
    "t_knife": ("right_elbow", "right_wrist", "knife", "t_knife_geo", "poignard"),
    "m9": ("right_elbow", "right_wrist", "knife", "m9_rusted_geo", "poignard"),
    "butterfly_knife": ("right_elbow", "righthand_and_knife", "butterfly_knife", "butterfly_knife_asiimov_geo", "poignard"),
    # le karambit n'a pas d'os de poignet : la main et la lame (hand_and_knife) s'orientent ensemble
    "karambit": ("hand_and_knife", "hand_and_knife", "karambit", "karambit_fade_geo", "karambit"),
}

# Gestes. Clés : (temps, bras_rot, bras_pos, visée, roulis, tour, mouvement jusqu'à cette clé)
#  bras_rot : [tangage, lacet, roulis] vus de l'avant-bras autour du coude (petits : il est long et proche des yeux) ;
#  bras_pos : [x, y, z] en px vus ;
#  visée : direction de la lame (du poing vers la pointe) [tangage, lacet] vus en degrés, « repos » (celle de la pose
#    de repos) ou « cible » (vers le réticule, à 40 px devant les yeux) ; le poignet tourne pour l'y mettre ;
#  roulis : rotation de la lame autour d'elle-même (tranchant), degrés ;
#  tour : tour du couteau dans la main (autour de l'épaisseur de la lame), degrés ;
#  mouvement : « lisse » (part et arrive en douceur), « frappe » (accélère jusqu'à la clé), « sortie » (part vite).
GESTES = {
    "poignard": [
        (0.00, (0, 0, 0), (0, 0, 0), "repos", 0, 0, "lisse"),
        (0.16, (7, -4, -6), (3.0, 2.5, 1.5), (74, 22), -30, 0, "lisse"),
        (IMPACT, (-6, 4, 2), (-3.0, -1.5, -6.5), "cible", 10, 0, "frappe"),
        (0.31, (-7, 4, 2), (-3.4, -1.8, -7.4), "cible", 45, 0, "sortie"),
        (0.44, (-3, 2, 1), (-1.8, -0.8, -4.0), (24, 40), 20, 0, "lisse"),
        (0.85, (0, 0, 0), (0, 0, 0), "repos", 0, 0, "lisse"),
    ],
    "karambit": [
        (0.00, (0, 0, 0), (0, 0, 0), "repos", 0, 0, "lisse"),
        (0.16, (0, 0, 0), (2.0, 1.5, -1.5), (22, -100), -25, 0, "lisse"),
        (IMPACT, (0, 0, 0), (-4.5, -2.0, -5.0), (-22, -70), 25, 0, "frappe"),
        (0.31, (0, 0, 0), (-5.0, -2.4, -5.5), (-26, -66), 28, 0, "sortie"),
        (0.46, (0, 0, 0), (-2.0, -1.0, -2.5), (-6, -90), 10, 0, "lisse"),
        (0.74, (0, 0, 0), (0, 0, 0), "repos", 0, 360, "lisse"),
        (0.85, (0, 0, 0), (0, 0, 0), "repos", 0, 360, "lisse"),
    ],
}
# centre du tour du couteau : « prise » (milieu de la poignée) ou « anneau » (bout de la poignée : anneau du karambit)
CENTRE_TOUR = {"poignard": "prise", "karambit": "anneau"}
CIBLE = 40.0
# part de l'orientation donnée au poignet (main et bras avec), le reste au couteau qui tourne dans la main
PART_POIGNET = 0.35

SON_LR = "heralisz_tactical:melee/knife/"
# bruits du joueur (ceux des couteaux de LR) : lame qu'on arme, coup, lame dégagée (poignard) ou tour (karambit)
SONS = {
    "poignard": {"0.0": SON_LR + "melee_knife_charge_01", "0.12": SON_LR + "melee_attack_knife_plr_02",
                 "0.5": SON_LR + "wfoly_me_knife_inspect_01"},
    "karambit": {"0.0": SON_LR + "melee_knife_charge_01", "0.12": SON_LR + "melee_attack_knife_plr_02",
                 "0.5": SON_LR + "iw9_weap_knife_inspect_01b"},
}

# temps des images de l'aperçu
APERCU = [0.0, 0.07, 0.12, 0.16, 0.2, 0.24, 0.31, 0.44, 0.55, 0.66, 0.85]


# ---------------------------------------------------------------------------------------------------------------------
# Repères. On calcule dans le repère « vu » de Blockbench (celui de l'écran, x vers la droite) ; un fichier Bedrock a
# l'axe x inversé : position (-x, y, z), rotation (-tangage, -lacet, roulis), ordre Z * Y * X.


def rot_xyz(deg):
    """Matrice Rz * Ry * Rx (degrés, repère vu)."""
    x, y, z = [math.radians(v) for v in deg]
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def rot_axe(axe, deg):
    a = np.asarray(axe, float) / np.linalg.norm(axe)
    t = math.radians(deg)
    k = np.array([[0, -a[2], a[1]], [a[2], 0, -a[0]], [-a[1], a[0], 0]])
    return np.eye(3) + math.sin(t) * k + (1 - math.cos(t)) * (k @ k)


def euler_zyx(r, avant=None):
    """Angles (degrés) tels que Rz * Ry * Rx = r ; le plus proche de « avant » (angles déroulés)."""
    b = math.asin(max(-1.0, min(1.0, -r[2, 0])))
    if abs(math.cos(b)) > 1e-6:
        a = math.atan2(r[2, 1], r[2, 2])
        c = math.atan2(r[1, 0], r[0, 0])
    else:  # blocage de cardan : tout dans a
        a = math.atan2(-r[1, 2], r[1, 1])
        c = 0.0
    sols = [(a, b, c), (a + math.pi, math.pi - b, c + math.pi)]
    sols = [[math.degrees(v) for v in s] for s in sols]
    if avant is None:
        return sols[0]
    meilleur = None
    for s in sols:
        s = [v + 360.0 * round((p - v) / 360.0) for v, p in zip(s, avant)]
        d = sum((v - p) ** 2 for v, p in zip(s, avant))
        if meilleur is None or d < meilleur[0]:
            meilleur = (d, s)
    return meilleur[1]


def vu(v):
    return np.array([-v[0], v[1], v[2]], float)


def rot_bedrock(r_b):
    return rot_xyz([-r_b[0], -r_b[1], r_b[2]])


# ---------------------------------------------------------------------------------------------------------------------
# Animations Bedrock


def valeur(k):
    if isinstance(k, dict):
        return k.get("post", k.get("pre"))
    return k


def vec(v):
    if isinstance(v, (int, float)):
        return [float(v)] * 3
    return [float(x) for x in v]


def echantillon(canal, t):
    """Valeur d'un canal Bedrock au temps t : composante par composante, comme TaCZ (ligne droite ou catmullrom)."""
    if not isinstance(canal, dict):
        return vec(canal)
    cles = sorted((float(x), vec(valeur(v)), (v.get("lerp_mode") if isinstance(v, dict) else None)) for x, v in canal.items())
    if t <= cles[0][0]:
        return list(cles[0][1])
    for i in range(len(cles) - 1):
        (t0, v0, _), (t1, v1, mode) = cles[i], cles[i + 1]
        if t0 <= t <= t1:
            f = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            if cles[i][2] == "catmullrom" or mode == "catmullrom":
                vm = cles[i - 1][1] if i > 0 else v0
                vp = cles[i + 2][1] if i + 2 < len(cles) else v1
                f2, f3 = f * f, f * f * f
                return [0.5 * (2 * b + (-a + c) * f + (2 * a - 5 * b + 4 * c - d) * f2 + (-a + 3 * b - 3 * c + d) * f3)
                        for a, b, c, d in zip(vm, v0, v1, vp)]
            return [a + (b - a) * f for a, b in zip(v0, v1)]
    return list(cles[-1][1])


def repos(src):
    """Pose de repos du modèle : chaque canal de « static_idle » au temps 0."""
    return {nom: {c: echantillon(v, 0.0) for c, v in canaux.items()}
            for nom, canaux in src["animations"]["static_idle"].get("bones", {}).items()}


# ---------------------------------------------------------------------------------------------------------------------
# Modèle 3D : transformations des os (comme TaCZ : décalage animé, pivot, rotation du modèle, rotation animée, échelle)


class Modele:
    def __init__(self, geo):
        self.os = {b["name"]: b for b in geo["bones"]}

    def parent(self, nom):
        p = self.os[nom].get("parent")
        return p if p in self.os else None

    def locale(self, nom, pose):
        """Transformation locale 4x4 de l'os (repère vu) pour une pose {os: {rotation, position, scale}}."""
        b = self.os[nom]
        pv = vu(b.get("pivot", [0, 0, 0]))
        a = pose.get(nom, {})
        m = np.eye(4)
        m[:3, 3] = vu(a.get("position", [0, 0, 0]))
        r = rot_bedrock(b.get("rotation") or [0, 0, 0]) @ rot_bedrock(a.get("rotation", [0, 0, 0]))
        rs = r @ np.diag(a.get("scale", [1, 1, 1]))
        loc = np.eye(4)
        loc[:3, :3] = rs
        loc[:3, 3] = pv - rs @ pv
        return m @ loc

    def monde(self, nom, pose, cache=None):
        cache = {} if cache is None else cache
        if nom not in cache:
            p = self.parent(nom)
            cache[nom] = (self.monde(p, pose, cache) if p else np.eye(4)) @ self.locale(nom, pose)
        return cache[nom]

    def descendants(self, nom):
        res = [nom]
        for n, b in self.os.items():
            if b.get("parent") == nom:
                res += self.descendants(n)
        return res

    def coins(self, nom):
        """Coins des cubes de l'os, dans le repère de l'os (vu)."""
        out = []
        for c in self.os[nom].get("cubes", []):
            o, s = np.array(c["origin"], float), np.array(c["size"], float)
            lo = np.array([-(o[0] + s[0]), o[1], o[2]])
            hi = np.array([-o[0], o[1] + s[1], o[2] + s[2]])
            mc = np.eye(4)
            if c.get("rotation"):
                cp = vu(c.get("pivot", [0, 0, 0]))
                r = rot_bedrock(c["rotation"])
                mc[:3, :3] = r
                mc[:3, 3] = cp - r @ cp
            pts = np.array([[x, y, z, 1] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
            out.append((mc @ pts.T).T)
        return out


def points(modele, racine, pose, repere=None):
    """Coins des cubes de l'os et de ses descendants, dans le repère « repere » (os) ou celui du modèle."""
    cache = {}
    inv = np.linalg.inv(modele.monde(repere, pose, cache)) if repere else np.eye(4)
    pts = []
    for n in modele.descendants(racine):
        m = inv @ modele.monde(n, pose, cache)
        for c in modele.coins(n):
            pts.append((m @ c.T).T[:, :3])
    return np.concatenate(pts) if pts else np.zeros((0, 3))


def axes_lame(modele, lame, pose):
    """Dans le repère du parent du couteau : point de prise, axe de la lame (vers la pointe), axe de l'épaisseur."""
    parent = modele.parent(lame)
    pts = points(modele, lame, pose, parent)
    centre = pts.mean(axis=0)
    _, _, vt = np.linalg.svd(pts - centre)
    long_, epais = vt[0], vt[2]
    # la main : bas de la boîte du bras (là où est le poing), dans le même repère
    cache = {}
    inv = np.linalg.inv(modele.monde(parent, pose, cache))
    m = inv @ modele.monde("righthand_pos", pose, cache)
    bras = np.concatenate([(m @ c.T).T[:, :3] for c in modele.coins("righthand_pos")])
    poing = bras.mean(axis=0)
    proj = (pts - centre) @ long_
    if abs(proj.max() - (poing - centre) @ long_) < abs(proj.min() - (poing - centre) @ long_):
        long_ = -long_  # pointe à l'opposé du poing
        proj = -proj
    prise = centre + long_ * (proj.min() + 0.25 * (proj.max() - proj.min()))
    anneau = centre + long_ * proj.min()
    return prise, long_, epais, anneau


# ---------------------------------------------------------------------------------------------------------------------
# Geste -> canaux Bedrock


def doux(f, mode):
    if mode == "frappe":
        return f * f
    if mode == "sortie":
        return 1 - (1 - f) * (1 - f)
    return f * f * (3 - 2 * f)


def vue_rot(r):
    """[tangage, lacet, roulis] vus (main droite, degrés) -> matrice : lacet puis tangage puis roulis."""
    return rot_axe((0, 1, 0), r[1]) @ rot_axe((1, 0, 0), r[0]) @ rot_axe((0, 0, 1), r[2])


def direction(angles):
    """[tangage, lacet] vus -> vecteur unité (lacet 0 = droit devant, + = vers la gauche ; tangage + = vers le haut)."""
    p, y = [math.radians(v) for v in angles]
    return np.array([-math.cos(p) * math.sin(y), math.sin(p), -math.cos(p) * math.cos(y)])


def angles(d):
    d = d / np.linalg.norm(d)
    return np.array([math.degrees(math.asin(max(-1.0, min(1.0, d[1])))), math.degrees(math.atan2(-d[0], -d[2]))])


def axe_angle(r):
    """Axe et angle (degrés) d'une rotation."""
    angle = math.degrees(math.acos(max(-1.0, min(1.0, (np.trace(r) - 1) / 2))))
    axe = np.array([r[2, 1] - r[1, 2], r[0, 2] - r[2, 0], r[1, 0] - r[0, 1]])
    n = np.linalg.norm(axe)
    if n < 1e-9:
        if angle < 90:
            return np.array([1.0, 0, 0]), 0.0
        w, v = np.linalg.eigh(r + np.eye(3))  # demi-tour : axe = vecteur propre de valeur 1
        return v[:, -1], angle
    return axe / n, angle


def rot_entre(a, b):
    """Plus petite rotation qui amène la direction a sur la direction b."""
    a, b = a / np.linalg.norm(a), b / np.linalg.norm(b)
    v, c = np.cross(a, b), float(a @ b)
    s = np.linalg.norm(v)
    if s < 1e-9:
        return np.eye(3)
    return rot_axe(v, math.degrees(math.atan2(s, c)))


def delta(modele, nom, pose_repos, r_delta, centre, deplacement, avant):
    """Os « nom » tourné de r_delta autour de « centre » et déplacé (repère de son parent) : (rotation, position) Bedrock."""
    a = pose_repos.get(nom, {})
    b = modele.os[nom]
    pv = vu(b.get("pivot", [0, 0, 0]))
    r_geo = rot_bedrock(b.get("rotation") or [0, 0, 0])
    r0 = rot_bedrock(a.get("rotation", [0, 0, 0]))
    off0 = vu(a.get("position", [0, 0, 0]))
    # repos : x -> off0 + pv + r_geo r0 (x - pv) ; voulu : centre + r_delta (repos(x) - centre) + déplacement
    r_anim = np.linalg.inv(r_geo) @ r_delta @ r_geo @ r0
    off = centre + r_delta @ (off0 + pv - centre) + deplacement - pv
    e = euler_zyx(r_anim, None if avant is None else [-avant[0], -avant[1], avant[2]])
    return [-e[0], -e[1], e[2]], [-off[0], off[1], off[2]]


def pivot_pose(modele, nom, pose):
    return vu(modele.os[nom].get("pivot", [0, 0, 0])) + vu(pose.get(nom, {}).get("position", [0, 0, 0]))


class Coup:
    """Calcule la pose d'un modèle à chaque instant du geste, et les canaux Bedrock qui la donnent."""

    def __init__(self, modele, pose_repos, bras, orient, lame, geste):
        self.m, self.repos, self.bras, self.orient, self.lame = modele, pose_repos, bras, orient, lame
        self.cles = GESTES[geste]
        self.prise, self.long, self.epais, self.anneau = axes_lame(modele, lame, pose_repos)
        self.centre_tour = self.anneau if CENTRE_TOUR[geste] == "anneau" else self.prise
        self.oeil = vu((modele.os.get("idle_view") or {"pivot": [0, 12, 14]})["pivot"])
        self.avant = {}
        # visée de chaque clé en angles (« repos » et « cible » calculés à la pose du bras de cette clé)
        self.visees = []
        for k in self.cles:
            pose = self.pose_bras(np.array(k[1], float), np.array(k[2], float))
            prise, dir_ = self.lame_monde(pose)
            if k[3] == "repos":
                v = angles(dir_)
            elif k[3] == "cible":
                v = angles(self.oeil + np.array([0, 0, -CIBLE]) - prise)
            else:
                v = np.array(k[3], float)
            self.visees.append(v)
        # lacets déroulés (pas de demi-tour par le mauvais côté)
        for i in range(1, len(self.visees)):
            self.visees[i][1] += 360.0 * round((self.visees[i - 1][1] - self.visees[i][1]) / 360.0)

    def pose_bras(self, r_bras, p_bras):
        pose = {n: dict(c) for n, c in self.repos.items()}
        if self.bras != self.orient:
            r, p = delta(self.m, self.bras, self.repos, vue_rot(r_bras), pivot_pose(self.m, self.bras, self.repos), p_bras, None)
        else:
            r, p = delta(self.m, self.bras, self.repos, np.eye(3), pivot_pose(self.m, self.bras, self.repos), p_bras, None)
        pose.setdefault(self.bras, {}).update(rotation=r, position=p)
        return pose

    def lame_monde(self, pose):
        """Point de prise et direction de la lame dans le repère du modèle."""
        mp = self.m.monde(self.m.parent(self.lame), pose)
        return mp[:3, :3] @ self.prise + mp[:3, 3], mp[:3, :3] @ self.long

    def parametres(self, t):
        cles = self.cles
        if t <= cles[0][0]:
            i, f = 0, 0.0
        elif t >= cles[-1][0]:
            i, f = len(cles) - 2, 1.0
        else:
            i = max(j for j in range(len(cles) - 1) if cles[j][0] <= t)
            f = doux((t - cles[i][0]) / (cles[i + 1][0] - cles[i][0]), cles[i + 1][6])
        k0, k1 = cles[i], cles[i + 1]
        mix = lambda a, b: np.array(a, float) + (np.array(b, float) - np.array(a, float)) * f
        return (mix(k0[1], k1[1]), mix(k0[2], k1[2]), mix(self.visees[i], self.visees[i + 1]),
                k0[4] + (k1[4] - k0[4]) * f, k0[5] + (k1[5] - k0[5]) * f)

    def valeurs(self, t):
        """{os: (rotation, position)} Bedrock au temps t."""
        r_bras, p_bras, visee, roulis, tour = self.parametres(t)
        pose = self.pose_bras(r_bras, p_bras)
        _, dir0 = self.lame_monde(pose)
        dir1 = direction(visee)
        r_monde = rot_axe(dir1, roulis) @ rot_entre(dir0, dir1)
        # une part de l'orientation au poignet (main et couteau), le reste au couteau qui tourne dans la main
        axe, angle = axe_angle(r_monde)
        r_poignet = rot_axe(axe, angle * PART_POIGNET) if angle > 1e-6 else np.eye(3)
        r_reste = r_monde @ r_poignet.T
        out = {}
        if self.orient == self.bras:
            r, p = delta(self.m, self.bras, self.repos, r_poignet, pivot_pose(self.m, self.bras, self.repos), p_bras,
                         self.avant.get(self.bras))
            out[self.bras] = (r, p)
        else:
            out[self.bras] = (pose[self.bras]["rotation"], pose[self.bras]["position"])
            rp = self.m.monde(self.m.parent(self.orient), pose)[:3, :3]
            out[self.orient] = delta(self.m, self.orient, self.repos, rp.T @ r_poignet @ rp,
                                     pivot_pose(self.m, self.orient, self.repos), np.zeros(3), self.avant.get(self.orient))
        for n, (r, p) in out.items():
            pose.setdefault(n, {}).update(rotation=r, position=p)
        # couteau : tour dans la main (autour de l'épaisseur), puis le reste de l'orientation autour du point de prise,
        # dans le repère de son parent (déjà tourné)
        rp = self.m.monde(self.m.parent(self.lame), pose)[:3, :3]
        r2 = rp.T @ r_reste @ rp
        r1 = rot_axe(self.epais, tour)
        c1, c2 = self.centre_tour, self.prise
        depl = c2 - r2 @ c2 + r2 @ (c1 - r1 @ c1)
        out[self.lame] = delta(self.m, self.lame, self.repos, r2 @ r1, np.zeros(3), depl, self.avant.get(self.lame))
        for n, (r, _) in out.items():
            self.avant[n] = r
        return out


def clip(modele, pose_repos, bras, orient, lame, geste):
    coup = Coup(modele, pose_repos, bras, orient, lame, geste)
    os_ = {nom: {c: [round(x, 4) for x in v] for c, v in canaux.items()} for nom, canaux in pose_repos.items()}
    canaux = {}
    n = int(round(DUREE / PAS))
    for i in range(n + 1):
        t = min(DUREE, i * PAS)
        cle = str(round(t, 4))
        for nom, (r, p) in coup.valeurs(t).items():
            c = canaux.setdefault(nom, {"rotation": {}, "position": {}})
            c["rotation"][cle] = [round(x, 3) for x in r]
            c["position"][cle] = [round(x, 4) for x in p]
    for nom, c in canaux.items():
        os_.setdefault(nom, {}).update(c)
    return {"loop": False, "animation_length": DUREE, "bones": os_,
            "sound_effects": {t: {"effect": s} for t, s in SONS[geste].items()}}


def charger_geo(nom_geo):
    with open(os.path.join(PACK, "geo_models", "gun", nom_geo + ".json"), encoding="utf-8") as f:
        return json.load(f)["minecraft:geometry"][0]


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--apercu", metavar="DOSSIER", help="planches d'images clés (numpy, Pillow)")
    args = ap.parse_args()
    os.makedirs(SORTIE, exist_ok=True)
    rendus = []
    for nom, (bras, poignet, lame, nom_geo, geste) in MODELES.items():
        with open(os.path.join(SOURCE, nom + ".animation.json"), encoding="utf-8") as f:
            src = json.load(f)
        geo = charger_geo(nom_geo)
        modele = Modele(geo)
        pose_repos = repos(src)
        anim = clip(modele, pose_repos, bras, poignet, lame, geste)
        sortie = {"format_version": src.get("format_version", "1.8.0"), "animations": {CLIP: anim}}
        chemin = os.path.join(SORTIE, nom + ".animation.json")
        with open(chemin, "w", encoding="utf-8", newline="\n") as f:
            json.dump(sortie, f, ensure_ascii=False, separators=(",", ":"))
            f.write("\n")
        print("écrit", os.path.relpath(chemin, DEPOT).replace(os.sep, "/"), CLIP, DUREE, "s")
        rendus.append((nom, geo, modele, lame, anim, src["animations"].get("melee_push")))
    if args.apercu:
        apercu(args.apercu, rendus)


# ---------------------------------------------------------------------------------------------------------------------
# Aperçu : cubes du modèle en aplat (lame en acier, poignée sombre, bras couleur peau, pointe en rouge), caméra à
# « idle_view » (1re personne) et vue de côté (depuis la droite) ; l'animation est relue comme TaCZ la lit.


def apercu(dossier, rendus):
    from PIL import Image, ImageDraw

    larg, haut = 300, 170

    def pose_a(anim, t):
        return {nom: {c: echantillon(v, t) for c, v in canaux.items()} for nom, canaux in anim["bones"].items()}

    def rendu(modele, geo, lame, pose, titre, cote):
        img = Image.new("RGB", (larg, haut), (30, 34, 42))
        d = ImageDraw.Draw(img)
        cache = {}
        lames = set(modele.descendants(lame))
        prise, long_, _, _ = axes_lame(modele, lame, pose)
        parent = modele.parent(lame)
        mp = modele.monde(parent, pose, cache)
        pts_l = points(modele, lame, pose)
        proj = (np.c_[pts_l, np.ones(len(pts_l))] @ np.linalg.inv(mp).T)[:, :3] @ long_
        pointe = pts_l[int(np.argmax(proj))]
        if cote:
            def cam(p):  # vue de côté : depuis la droite, regard vers -x ; profondeur = x
                return np.stack([-p[:, 2], p[:, 1], 60 - p[:, 0]], axis=1)
            focale, centre = 3.2, (larg * 0.5, haut * 0.62)
        else:
            oeil = vu((modele.os.get("idle_view") or {"pivot": [0, 12, 14]})["pivot"])

            def cam(p):
                r = p - oeil
                return np.stack([r[:, 0], r[:, 1], -r[:, 2]], axis=1)
            focale, centre = None, (larg / 2, haut / 2)
        polys = []
        lum = np.array([0.3, 0.8, 0.5]) / np.linalg.norm([0.3, 0.8, 0.5])
        for nom in modele.os:
            if nom == "lefthand_pos" or not modele.os[nom].get("cubes"):
                continue
            if nom != "righthand_pos" and nom not in lames:
                continue
            m = modele.monde(nom, pose, cache)
            if nom == "righthand_pos":
                coul = (206, 160, 126)
            else:
                coul = (196, 206, 220)
            for c in modele.coins(nom):
                pts = (m @ c.T).T[:, :3]
                for f in ((0, 1, 3, 2), (4, 5, 7, 6), (0, 1, 5, 4), (2, 3, 7, 6), (0, 2, 6, 4), (1, 3, 7, 5)):
                    q = cam(pts[list(f)])
                    if (q[:, 2] < 0.5).any():
                        continue
                    nn = np.cross(pts[f[1]] - pts[f[0]], pts[f[2]] - pts[f[0]])
                    no = np.linalg.norm(nn)
                    ombre = 0.5 + 0.5 * abs(float(nn @ lum / no)) if no > 1e-9 else 0.7
                    if focale is None:
                        fz = (haut / 2) / math.tan(math.radians(35))
                        xy = [(centre[0] + x / z * fz, centre[1] - y / z * fz) for x, y, z in q]
                    else:
                        xy = [(centre[0] + x * focale, centre[1] - y * focale) for x, y, z in q]
                    polys.append((float(q[:, 2].mean()), xy, tuple(int(k * ombre) for k in coul)))
        for _, xy, coul in sorted(polys, key=lambda p: -p[0]):
            d.polygon(xy, fill=coul)
        q = cam(np.array([pointe]))[0]
        if q[2] > 0.5:
            if focale is None:
                fz = (haut / 2) / math.tan(math.radians(35))
                x, y = centre[0] + q[0] / q[2] * fz, centre[1] - q[1] / q[2] * fz
            else:
                x, y = centre[0] + q[0] * focale, centre[1] - q[1] * focale
            d.ellipse([x - 4, y - 4, x + 4, y + 4], fill=(235, 40, 40))
        if not cote:
            d.line([(larg / 2 - 5, haut / 2), (larg / 2 + 5, haut / 2)], fill=(255, 255, 255))
            d.line([(larg / 2, haut / 2 - 5), (larg / 2, haut / 2 + 5)], fill=(255, 255, 255))
        else:
            d.text((larg - 70, haut - 14), "avant ->", fill=(150, 150, 150))
        d.text((5, 4), titre, fill=(255, 255, 255))
        return img

    os.makedirs(dossier, exist_ok=True)
    for nom, geo, modele, lame, anim, push in rendus:
        lignes = []
        for cote in (False, True):
            lignes.append([rendu(modele, geo, lame, pose_a(anim, t), "%s coup puissant %.2f s%s" % (nom, t, " (côté)" if cote else ""), cote)
                           for t in APERCU])
        if push:
            temps = [0.0, 0.02, 0.06, 0.1, 0.15, 0.2, 0.3, 0.45, 0.55, 0.7, 0.9]
            base = {n: {c: v for c, v in canaux.items()} for n, canaux in anim["bones"].items()}
            # coup rapide du gunpack : ses os par-dessus la pose de repos
            for cote in (False,):
                ligne = []
                for t in temps:
                    pose = pose_a({"bones": base}, 0.0)
                    pose.update(pose_a(push, t))
                    ligne.append(rendu(modele, geo, lame, pose, "%s coup rapide (gunpack) %.2f s" % (nom, t), cote))
                lignes.append(ligne)
        planche = Image.new("RGB", (larg * len(APERCU), haut * len(lignes)))
        for j, ligne in enumerate(lignes):
            for i, im in enumerate(ligne):
                planche.paste(im, (larg * i, haut * j))
        chemin = os.path.join(dossier, nom + ".png")
        planche.save(chemin)
        print("aperçu", chemin)


if __name__ == "__main__":
    main()
