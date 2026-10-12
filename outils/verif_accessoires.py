#!/usr/bin/env python3
"""Contrôle des accessoires TaCZ posés sur les armes : chaque arme avec chacun des accessoires qu'elle accepte
(listes de outils/accessoires.py et des gunpacks), assemblés par le calcul exactement comme TaCZ 1.1.8 les dessine,
puis mesurés.

    python outils/verif_accessoires.py                  contrôle (code de sortie 1 s'il reste un défaut ni corrigé ni
                                                        accepté) ; outils/verifier.py le lance
    python outils/verif_accessoires.py --rapport        détail classé par arme (--tout : aussi les combinaisons bonnes)
    python outils/verif_accessoires.py --images [arme…] planches de contrôle (une par arme et par type : l'arme seule,
                                                        puis chaque accessoire en gros plan de côté, l'arme entière et
                                                        une vue de face) dans --sortie (work/verif_accessoires par
                                                        défaut) ; sans nom d'arme : les armes qui ont un défaut
    options : --arme <id> (plusieurs fois), --type scope|muzzle|stock|grip|laser (images), --jobs N (processus)

Comment TaCZ place un accessoire (lu dans son jar : BedrockGunModel, AttachmentRender, BedrockModel, BedrockPart) :
  - les modèles sont au format Bedrock (assets/<ns>/geo_models/...) : chaque os est placé par son « pivot » (racine :
    x, 24 - y, z ; enfant : écart au pivot du parent, y inversé), tourné Z puis Y puis X (degrés du fichier), cubes en
    pixels (1/16 de bloc) ; un cube tourné devient une petite pièce autour de son propre pivot ;
  - un accessoire de type T est dessiné dans le repère de l'os « T_pos » de l'arme, abaissé de 1,5 bloc : en tout, le
    point (x, y, z) du modèle de l'accessoire tombe sur le pivot de « T_pos » + (x, y, z) (os non tournés). Un viseur
    suit le chemin des os jusqu'à « scope_pos » (même sous un os caché) ; les autres types ne sont dessinés que si tous
    les parents de « T_pos » sont visibles. Sans os « T_pos », l'accessoire posé est invisible ;
  - quand un accessoire est posé, TaCZ cache « T_default » (pièce d'origine ; une bouche « show_muzzle » la laisse),
    « sight » et « carry » (viseur posé ; « sight_folded » apparaît), montre « mount » avec un viseur (sauf
    « show_mount »: false), remplace « handguard_default » par « handguard_tactical » (poignée ou laser posé) ; sous
    « attachment_adapter », seuls les nœuds nommés par l'« adapter » d'un accessoire posé sont dessinés (tube de crosse
    « ar_stock_adapter » des crosses AR, crosses OEM qui ne sont qu'un nœud de l'arme) ;
  - ne comptent pas : les réticules (« division »), les lentilles (« ocular… ») et les textes (dessinés seulement à la
    visée), les cubes dont la texture est transparente ou presque (décalques, effets), les cubes de plus de 40 px
    (faisceaux de laser dessinés en cubes). Les crosses OEM sont dessinées par l'auteur de l'arme : seule la présence de
    leur nœud est contrôlée.

Mesures (en pixels du modèle, 1 px = 1/16 de bloc ; seuils en tête de fichier) :
  - écart : plus petite distance entre l'accessoire et l'arme (0 s'ils se touchent). Au-delà de SEUIL_ECART,
    l'accessoire « flotte » (viseur au-dessus du capot, crosse décollée de la carcasse, bipied devant le canon…) ;
  - enfoncement : le plus court chemin pour le sortir des pièces qu'il traverse, dans le sens du montage (viseur vers
    le haut, poignée vers le bas, crosse vers l'arrière, bouche vers l'avant) ou de côté, jamais en le poussant vers
    l'arme. Pour une bouche ou une crosse, seules comptent les pièces tout entières de l'autre côté du point de
    montage (le canon entre dans la bouche, le tube dans la crosse). Au-delà de SEUIL_ENFONCE, il « traverse » une
    pièce (poignée longue dans le chargeur, viseur noyé dans la carcasse…) ;
  - décalage : la plupart des accessoires d'un type flottent un peu sur la même arme (écart médian > SEUIL_DECALAGE) :
    l'os « T_pos » est mal placé ;
  - invisible : pas d'os « T_pos », ou il est sous un os caché quand l'accessoire est posé, ou nœud OEM absent.
  Le contrôle se fait sur la pose de repos (sans animation), chargeur standard, sans regarder les modèles LOD (vus de
  loin). Une arme dessinée par deux gunpacks (le pack W Mags redessine des armes de TaCZ) est contrôlée deux fois :
  TaCZ garde l'une ou l'autre selon l'ordre des dossiers.

Corriger un défaut (la plus sûre d'abord) :
  1. déplacer l'os « T_pos » de l'arme dans son modèle, si ça arrange tous les accessoires de ce type sans abîmer
     ceux faits pour elle (ou en décalant d'autant le modèle d'un accessoire fait pour cette seule arme), ou ajouter le
     nœud d'adaptateur que TaCZ prévoit (« ar_stock_adapter » sous « attachment_adapter ») ;
  2. sinon, retirer l'accessoire de cette arme : EXCLUS de outils/accessoires.py (puis relancer outils/accessoires.py).
     Si c'est la liste d'origine de l'arme qui le donne, l'en retirer dans son gunpack (fichier
     allow_attachments/<arme>.json, ou liste partagée #tacz:… de chaque gunpack qui l'y met : TaCZ les additionne ;
     la liste commune le redonne ensuite aux autres armes) ;
  3. sinon, l'accepter dans ACCEPTES ci-dessous, avec sa raison.
À relancer après l'ajout ou la mise à jour d'un gunpack, après outils/accessoires.py ; les planches (--images) aident à
voir chaque cas. Rien n'est testé en jeu : c'est le calcul du dessin de TaCZ, à confirmer à l'œil sur le serveur.
"""
import argparse
import math
import re
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import accessoires as acc  # noqa: E402

DEPOT = acc.DEPOT
TACZ = acc.TACZ
TYPES = ("scope", "muzzle", "stock", "grip", "laser")
NOMS_TYPES = {"scope": "viseur", "muzzle": "bouche", "stock": "crosse", "grip": "poignée", "laser": "laser"}

# Seuils en pixels du modèle (1/16 de bloc), par type : écart au-delà duquel l'accessoire flotte (0,35 px se voit déjà
# en première personne), enfoncement au-delà duquel il traverse une pièce de l'arme (les crosses enveloppent souvent
# la poignée pistolet : seuil plus large).
SEUIL_ECART = {"scope": 0.35, "muzzle": 0.35, "stock": 0.35, "grip": 0.35, "laser": 0.35}
SEUIL_ENFONCE = {"scope": 1.3, "muzzle": 1.3, "stock": 2.5, "grip": 1.3, "laser": 1.3}
# Décalage d'ensemble : quand la plupart des accessoires d'un type flottent un peu sur une arme (écart médian au-delà
# de ce seuil, 5 accessoires au moins), c'est l'os « T_pos » qui est mal placé, même si aucun ne dépasse SEUIL_ECART.
SEUIL_DECALAGE = 0.2

# Défauts laissés en connaissance de cause : (arme, accessoire, défaut) -> raison ; « * » pour toute arme, tout
# accessoire ou tout défaut (« flotte », « traverse », « invisible »).
ACCEPTES = {
    ("*", "tacz:bayonet_6h3", "traverse"): "baïonnette : la lame et sa monture longent le canon",
    ("*", "tacz:bayonet_m9", "traverse"): "baïonnette : la lame et sa monture longent le canon",
    ("*", "gucci_attachments:muzzle_s_fd197", "traverse"):
        "silencieux à manchon : sa partie arrière enveloppe l'avant de l'arme, comme sur le pistolet pour lequel il "
        "est fait",
    ("tacz:springfield1873", "tacz:scope_1873_6x", "traverse"):
        "lunette d'époque : sa monture latérale entre dans le fût, tel que dessiné par l'auteur du pack",
}


# ====================================================================== modèles Bedrock, lus comme TaCZ
def _rot(axe, a):
    c, s = math.cos(a), math.sin(a)
    if axe == 0:
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axe == 1:
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def matrice(pos, rot):
    """Repère d'un os (BedrockPart.translateAndRotateAndScale) : translation puis Z, Y, X."""
    m = np.eye(4)
    m[:3, 3] = pos
    r = np.eye(3)
    for axe in (2, 1, 0):
        if rot[axe]:
            r = r @ _rot(axe, rot[axe])
    m[:3, :3] = r
    return m


def translation(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


class Os:
    __slots__ = ("nom", "parent", "pos", "rot", "enfants", "cubes", "pieces", "pivot")

    def __init__(self, nom):
        self.nom, self.parent, self.enfants = nom, None, []
        self.pos, self.rot, self.pivot = (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), (0.0, 0.0, 0.0)
        self.cubes = []          # (min, max, uv) dans le repère de l'os, en pixels, axe y vers le bas (Java)
        self.pieces = []         # cubes tournés : (repère 4x4 relatif à l'os, min, max, uv)


class Modele:
    """Modèle Bedrock tel que TaCZ le construit (BedrockModel.loadNewModel / loadLegacyModel)."""

    def __init__(self, fichier):
        self.fichier = fichier
        self.erreur = None
        self.os = {}
        self.racines = []
        self.ancien = False
        self.texture = (16, 16)
        d = acc.charger(fichier)
        fv = str(d.get("format_version", ""))
        p = fv.split(".")
        nouveau = len(p) == 3 and p[1].isdigit() and int(p[1]) >= 12 and d.get("minecraft:geometry")
        self.ancien = fv == "1.10.0" and bool(d.get("geometry.model"))
        if nouveau:
            geo = d["minecraft:geometry"][0]
        elif self.ancien:
            geo = d["geometry.model"]
        else:
            self.erreur = "format %r non lu par TaCZ" % fv
            return
        desc = geo.get("description") or geo
        self.texture = (float(desc.get("texture_width") or desc.get("texturewidth") or 16),
                        float(desc.get("texture_height") or desc.get("textureheight") or 16))
        bones = [b for b in geo.get("bones") or [] if b.get("name")]
        index = {}
        for b in bones:
            index.setdefault(b["name"], b)
            self.os.setdefault(b["name"], Os(b["name"]))

        def piv(b):
            v = b.get("pivot") or [0, 0, 0]
            return [float(x) for x in v]

        for nom, b in index.items():
            o = self.os[nom]
            pv = piv(b)
            o.pivot = tuple(pv)
            par = b.get("parent")
            if par and par in index:
                pp = piv(index[par])
                o.pos = (pv[0] - pp[0], pp[1] - pv[1], pv[2] - pp[2])
                self.os[par].enfants.append(o)
                if not self.ancien:
                    o.parent = self.os[par]
            else:
                o.pos = (pv[0], 24.0 - pv[1], pv[2])
                self.racines.append(o)
            r = b.get("rotation")
            if r:
                o.rot = tuple(math.radians(float(x)) for x in r)
            for c in b.get("cubes") or []:
                org = [float(x) for x in c.get("origin") or [0, 0, 0]]
                taille = [float(x) for x in c.get("size") or [0, 0, 0]]
                gonfle = float(c.get("inflate") or 0)
                crot = c.get("rotation")
                if crot and not self.ancien:
                    cp = [float(x) for x in c.get("pivot") or [0, 0, 0]]
                    rep = matrice((cp[0] - pv[0], pv[1] - cp[1], cp[2] - pv[2]),
                                  tuple(math.radians(float(x)) for x in crot))
                    ref = cp
                else:
                    rep, ref = None, pv
                lo = np.array([org[0] - ref[0], ref[1] - org[1] - taille[1], org[2] - ref[2]])
                hi = lo + np.array(taille)
                lo, hi = np.minimum(lo, hi) - gonfle, np.maximum(lo, hi) + gonfle
                uv = c.get("uv")
                if isinstance(uv, dict):
                    uv = ("faces", uv)
                else:
                    uv = ("boite", [float(x) for x in (uv or [0, 0])[:2]], [int(x) for x in taille],
                          bool(c.get("mirror", b.get("mirror", False))))
                if rep is None:
                    o.cubes.append((lo, hi, uv))
                else:
                    o.pieces.append((rep, lo, hi, uv))

    def chemin(self, nom):
        """BedrockModel.getPath : les os de la racine jusqu'à « nom » (format ancien : l'os seul, défaut de TaCZ)."""
        o = self.os.get(nom)
        if o is None:
            return None
        out = []
        while o is not None:
            out.append(o)
            o = o.parent
        return out[::-1]

    def repere(self, nom):
        m = np.eye(4)
        for o in self.chemin(nom):
            m = m @ matrice(o.pos, o.rot)
        return m

    def sous_arbre(self, nom):
        """Noms des os sous « nom » (lui compris)."""
        out, pile = set(), [self.os[nom]] if nom in self.os else []
        while pile:
            o = pile.pop()
            out.add(o.nom)
            pile += o.enfants
        return out

    def ancetres(self, nom):
        """Os parents (hiérarchie réelle, même au format ancien)."""
        par = {}
        for o in self.os.values():
            for e in o.enfants:
                par[e.nom] = o.nom
        out, n = [], par.get(nom)
        while n is not None:
            out.append(n)
            n = par.get(n)
        return out

    def boites(self, visible=None, repere0=None, sauter=None, depart=None):
        """Boîtes (repère 4x4, min, max, nom de l'os) des cubes dessinés, comme BedrockModel.render.
        visible(nom) -> True / False / "seul" (l'os est dessiné par une fonction de TaCZ : ni ses cubes ni ses
        enfants) ; sauter : os dont tout le sous-arbre est ignoré ; depart : un seul sous-arbre (repère des parents
        compris)."""
        out = []

        def visite(o, m):
            if sauter and o.nom in sauter:
                return
            v = visible(o.nom) if visible else True
            if v is False or v == "seul":
                return
            m = m @ matrice(o.pos, o.rot)
            for lo, hi, uv in o.cubes:
                out.append((m, lo, hi, o.nom, uv))
            for rep, lo, hi, uv in o.pieces:
                out.append((m @ rep, lo, hi, o.nom, uv))
            for e in o.enfants:
                visite(e, m)

        m0 = np.eye(4) if repere0 is None else repere0
        if depart:
            anc = self.ancetres(depart)[::-1]
            for n in anc:
                m0 = m0 @ matrice(self.os[n].pos, self.os[n].rot)
            visite(self.os[depart], m0)
        else:
            for o in self.racines:
                visite(o, m0)
        return out


# ====================================================================== assemblage arme + accessoire, comme TaCZ
OCULAIRE = re.compile(r"^(ocular|ocular_sight|ocular_scope)(_(\d+))?$")
TYPES_POS = ("muzzle", "stock", "grip", "laser", "extended_mag")   # os « T_pos » remplacés par AttachmentRender


def suite(modele, base):
    """« division », « division_2 »… ; « shell », « shell_1 »… : tant qu'ils existent (comme TaCZ)."""
    out, i = [], 2 if base == "division" else 1
    if base in modele.os:
        out.append(base)
        while "%s_%d" % (base, i) in modele.os:
            out.append("%s_%d" % (base, i))
            i += 1
    return out


class Config:
    """Accessoires posés sur l'arme pour un dessin : {type: accessoire}, plus ce qu'en lit TaCZ."""

    def __init__(self, poses, adaptateurs=(), montre_bouche=False, montre_montage=True, niveau_chargeur=0):
        self.poses = dict(poses)
        self.adaptateurs = set(adaptateurs)
        self.montre_bouche = montre_bouche
        self.montre_montage = montre_montage
        self.niveau_chargeur = niveau_chargeur


def regle_arme(modele, cfg, textes=()):
    """visible(nom) pour le modèle de l'arme (BedrockGunModel : fonctions posées sur les os par leur nom)."""
    fonctions = {"lefthand_pos", "righthand_pos", "muzzle_flash"} | set(suite(modele, "shell")) | set(textes)
    fonctions |= {t + "_pos" for t in TYPES_POS}
    adapt = {e.nom for e in modele.os["attachment_adapter"].enfants} if "attachment_adapter" in modele.os else set()
    p = cfg.poses
    lunette = "scope" in p

    def visible(n):
        if n in fonctions:
            return "seul"
        if n in adapt:
            return n in cfg.adaptateurs
        if n == "sight" or n == "carry":
            return not lunette
        if n == "sight_folded":
            return lunette
        if n == "mount":
            return lunette and cfg.montre_montage      # « show_mount » du viseur, vrai par défaut
        if n == "muzzle_default":
            return "muzzle" not in p or cfg.montre_bouche
        if n.endswith("_default") and n[:-8] in TYPES_POS:
            return n[:-8] not in p
        if n == "mag_standard":
            return cfg.niveau_chargeur == 0
        if n.startswith("mag_extended_") and n[13:].isdigit():
            return cfg.niveau_chargeur == int(n[13:])
        if n == "handguard_default":
            return "grip" not in p and "laser" not in p
        if n == "handguard_tactical":
            return "grip" in p or "laser" in p
        if n == "additional_magazine":
            return False
        return True
    return visible


def pos_dessine(modele, type_, visible):
    """None si l'accessoire de ce type est bien dessiné, sinon la raison."""
    nom = type_ + "_pos"
    if nom not in modele.os:
        return "pas d'os %s dans le modèle de l'arme" % nom
    if type_ == "scope":
        return None                        # dessiné par son chemin, même sous un os caché
    for a in modele.ancetres(nom):
        v = visible(a)
        if v is False:
            return "os %s sous « %s », caché quand l'accessoire est posé" % (nom, a)
        if v == "seul":
            return "os %s sous « %s », que TaCZ ne dessine pas" % (nom, a)
    return None


def sauter_accessoire(modele, textes=()):
    """Os du modèle d'accessoire qui ne comptent pas : réticules, lentilles, textes."""
    return set(suite(modele, "division")) | {n for n in modele.os if OCULAIRE.match(n)} | set(textes)


def repere_accessoire(modele_arme, type_):
    """Repère du modèle de l'accessoire dans celui de l'arme (AttachmentRender.renderAttachment : -1,5 bloc)."""
    return modele_arme.repere(type_ + "_pos") @ translation(0, -24, 0)


# ====================================================================== mesures sur les boîtes
class Boites:
    """Boîtes orientées : centres C (n,3), axes R (n,3,3) en colonnes, demi-tailles H (n,3), enveloppes lo/hi."""

    def __init__(self, boites=()):
        self.adaptes = set()
        self.noms = [b[3] for b in boites]
        self.uv = [b[4] for b in boites]
        n = len(boites)
        self.C = np.zeros((n, 3))
        self.R = np.zeros((n, 3, 3))
        self.H = np.zeros((n, 3))
        for i, (m, lo, hi, _, _) in enumerate(boites):
            self.R[i] = m[:3, :3]
            self.C[i] = m[:3, :3] @ ((lo + hi) / 2) + m[:3, 3]
            self.H[i] = (hi - lo) / 2
        self._enveloppes()

    def _enveloppes(self):
        ext = np.einsum("nij,nj->ni", np.abs(self.R), self.H) if len(self.C) else np.zeros((0, 3))
        self.lo, self.hi = self.C - ext, self.C + ext

    def __len__(self):
        return len(self.C)

    def pres(self, lo, hi):
        """Les boîtes dont l'enveloppe touche le pavé [lo, hi]."""
        return self.filtre(np.all((self.lo <= hi) & (self.hi >= lo), axis=1))

    def sans(self, noms):
        """Les boîtes qui n'appartiennent pas aux os « noms »."""
        return self.filtre(np.array([n not in noms for n in self.noms], bool)) if noms else self

    def visibles(self, tex):
        """Sans les cubes transparents ou presque dans la texture (invisibles, ou simples décalques d'effet)."""
        if tex is None or tex.img is None or not len(self):
            return self
        return self.filtre(np.array([any(c is not None for c in tex.faces(u)) for u in self.uv], bool))

    def filtre(self, garde):
        b = Boites()
        b.noms = [n for n, g in zip(self.noms, garde) if g]
        b.uv = [n for n, g in zip(self.uv, garde) if g]
        b.C, b.R, b.H, b.lo, b.hi = self.C[garde], self.R[garde], self.H[garde], self.lo[garde], self.hi[garde]
        return b

    def transforme(self, m):
        """Les mêmes boîtes placées par le repère 4x4 m."""
        b = Boites()
        b.noms, b.uv = list(self.noms), list(self.uv)
        b.C = self.C @ m[:3, :3].T + m[:3, 3]
        b.R = np.einsum("ij,njk->nik", m[:3, :3], self.R)
        b.H = self.H.copy()
        b._enveloppes()
        return b

    def coins(self):
        """Les 8 coins de chaque boîte (n, 8, 3)."""
        s = np.array([[x, y, z] for x in (-1, 1) for y in (-1, 1) for z in (-1, 1)], float)
        return self.C[:, None, :] + np.einsum("nij,knj->nki", self.R, s[:, None, :] * self.H[None])


def _axes(Ra, Rb):
    """Les 15 axes de séparation (paires, 15, 3), normés ; axes nuls (arêtes parallèles) remplacés par Ra[:, 0]."""
    a = np.transpose(Ra, (0, 2, 1))
    b = np.transpose(Rb, (0, 2, 1))
    cr = np.cross(a[:, :, None, :], b[:, None, :, :]).reshape(len(Ra), 9, 3)
    L = np.concatenate([a, b, cr], axis=1)
    nrm = np.linalg.norm(L, axis=2, keepdims=True)
    nul = nrm[..., 0] < 1e-6
    return np.where(nul[..., None], a[:, :1, :], L / np.maximum(nrm, 1e-12))


def _rayons(L, R, H):
    """Demi-largeur des boîtes projetées sur chaque axe (paires, 15) et somme des |L·axes| (pour le gonflement)."""
    p = np.abs(np.matmul(L, R))                         # |L · axe_i| (paires, 15, 3)
    return (p * H[:, None, :]).sum(axis=2), p.sum(axis=2)


def ecart(A, G, limite=12.0):
    """Plus petite distance entre les boîtes A et G (borne basse, exacte face contre face), plafonnée à limite."""
    if not len(A) or not len(G):
        return limite
    G = G.pres(A.lo.min(axis=0) - limite, A.hi.max(axis=0) + limite)
    if not len(G):
        return limite
    d = np.maximum(0, np.maximum(A.lo[:, None, :] - G.hi[None], G.lo[None] - A.hi[:, None, :]))
    d = np.sqrt((d ** 2).sum(axis=2))                  # distance des enveloppes : borne basse de la vraie distance
    ia, ig = np.nonzero(d < limite)
    if not len(ia):
        return limite
    ordre = np.argsort(d[ia, ig])
    ia, ig = ia[ordre], ig[ordre]
    meilleur, debut, pas = limite, 0, 64
    while debut < len(ia) and meilleur > 0:
        sa, sg = ia[debut:debut + pas], ig[debut:debut + pas]
        debut, pas = debut + pas, min(pas * 4, 4096)
        if d[sa[0], sg[0]] >= meilleur:
            break
        L = _axes(A.R[sa], G.R[sg])
        t = np.abs(np.matmul(L, (G.C[sg] - A.C[sa])[:, :, None])[..., 0])
        ra, wa = _rayons(L, A.R[sa], A.H[sa])
        rg, wg = _rayons(L, G.R[sg], G.H[sg])
        sep = t - ra - rg
        r = np.maximum(np.maximum(np.max(sep / wa, axis=1), np.max(sep / wg, axis=1)), 0)
        meilleur = min(meilleur, float(r.min()))
    return meilleur


def intervalles(A, G, d, jeu=0.02, portee=40.0):
    """A déplacé de s·d : intervalles ]s0, s1[ où une boîte de A traverse une boîte de G (boîtes rétrécies de « jeu » :
    un simple contact ne compte pas). Renvoie (s0, s1, ia, ig, G réduit aux boîtes en jeu)."""
    vide = (np.zeros(0), np.zeros(0), np.zeros(0, int), np.zeros(0, int), G)
    if not len(A) or not len(G):
        return vide
    d = np.asarray(d, float)
    lo_b = np.minimum(A.lo, A.lo + portee * d)
    hi_b = np.maximum(A.hi, A.hi + portee * d)
    G = G.pres(lo_b.min(axis=0), hi_b.max(axis=0))
    vide = vide[:4] + (G,)
    if not len(G):
        return vide
    rec = np.all((lo_b[:, None, :] < G.hi[None]) & (G.lo[None] < hi_b[:, None, :]), axis=2)
    ia, ig = np.nonzero(rec)
    if not len(ia):
        return vide
    S0, S1 = [], []
    for debut in range(0, len(ia), 4000):
        sa, sg = ia[debut:debut + 4000], ig[debut:debut + 4000]
        L = _axes(A.R[sa], G.R[sg])
        t = np.matmul(L, (G.C[sg] - A.C[sa])[:, :, None])[..., 0]
        ra, _ = _rayons(L, A.R[sa], np.maximum(A.H[sa] - jeu, 0))
        rg, _ = _rayons(L, G.R[sg], np.maximum(G.H[sg] - jeu, 0))
        R = ra + rg
        k = L @ d
        plat = np.abs(k) < 1e-9
        k = np.where(plat, 1.0, k)
        a, b = (t - R) / k, (t + R) / k
        dedans = np.abs(t) < R
        s0 = np.where(plat, np.where(dedans, -np.inf, np.inf), np.minimum(a, b))
        s1 = np.where(plat, np.where(dedans, np.inf, -np.inf), np.maximum(a, b))
        S0.append(s0.max(axis=1))
        S1.append(s1.min(axis=1))
    s0, s1 = np.concatenate(S0), np.concatenate(S1)
    ok = s0 < s1
    return s0[ok], s1[ok], ia[ok], ig[ok], G


def enfoncement(A, G, d, portee=20.0):
    """De combien sortir A de G dans le sens d pour qu'il ne la traverse plus (0 : ne la traverse pas), et la paire
    des os (accessoire, arme) qui retient le plus loin."""
    s0, s1, ia, ig, G = intervalles(A, G, d, portee=portee)
    garde = s1 > 0
    s0, s1, ia, ig = s0[garde], s1[garde], ia[garde], ig[garde]
    s, cause = 0.0, None
    for i in np.argsort(s0, kind="stable"):
        if s0[i] < s + 1e-9 and s1[i] > s:
            s, cause = float(s1[i]), (A.noms[ia[i]], G.noms[ig[i]])
    return min(s, portee), cause


def decollement(A, G, d, portee=20.0):
    """De combien rapprocher A de G (sens -d) pour qu'ils se touchent : 0 s'ils se touchent, None si jamais."""
    s0, s1, ia, ig, _ = intervalles(A, G, -np.asarray(d, float), jeu=-0.001, portee=portee)
    garde = s1 > 0
    if not garde.any():
        return None
    return float(np.maximum(s0[garde], 0).min())


# ====================================================================== gunpacks : armes, accessoires, listes
def vrai(v, defaut=False):
    """Booléen lu comme Gson (« "false" » entre guillemets vaut faux) ; absent : defaut."""
    if v is None:
        return defaut
    return v is True or str(v).strip().lower() == "true"


class Catalogue:
    """Armes, accessoires et listes de tous les gunpacks, listes communes de outils/accessoires.py comprises."""

    def __init__(self):
        gp = self.gp = acc.Gunpacks()
        self.communes, self.ajouts, _ = acc.calculer(gp)
        self.listes_apres = dict(gp.listes)
        for t, nom in acc.LISTES.items():
            self.listes_apres["%s:%s" % (acc.PACK, nom)] = self.communes[t]
        self._modeles, self._acc, self._tex = {}, {}, {}

    def modele(self, ident):
        fs = self.gp.modeles.get(ident or "", [])
        if not fs:
            return None
        if fs[0] not in self._modeles:
            self._modeles[fs[0]] = Modele(fs[0])
        return self._modeles[fs[0]]

    def armes(self):
        return sorted(a for a in self.gp.armes if not acc.a_part(a))

    def texture(self, ident, modele):
        """Texture (images de contrôle) : assets/<ns>/textures/<chemin>.png d'un des gunpacks."""
        if not ident or ":" not in ident or modele is None:
            return None
        ns, chemin = ident.split(":", 1)
        fs = sorted(TACZ.glob("*/assets/%s/textures/%s.png" % (ns, chemin)))
        cle = (fs[0] if fs else None, modele.texture)
        if cle not in self._tex:
            self._tex[cle] = Texture(cle[0], modele.texture) if fs else None
        return self._tex[cle]

    def possibles(self, arme):
        """{accessoire: True s'il était déjà permis par les listes d'origine des gunpacks} : ce que l'atelier d'arme
        propose (liste de l'arme et type ouvert), comme AllowAttachmentTagMatcher."""
        gp = self.gp
        types = gp.types_arme(arme)
        propres = gp.listes_armes.get(arme, [])
        origine = acc.resoudre(propres, gp.listes)
        apres = acc.resoudre(propres + self.ajouts.get(arme, []), self.listes_apres)
        return {a: a in origine for a in apres if gp.type_acc.get(a) in TYPES and gp.type_acc[a] in types}

    def copies(self, arme):
        """[(gunpack, affichage, modèle)] : chaque affichage de l'arme (deux gunpacks peuvent donner le même ; TaCZ
        garde l'un ou l'autre selon l'ordre des dossiers sur le disque : les deux sont contrôlés)."""
        idx = acc.charger(self.gp.armes[arme][0])
        out = []
        for f in self.gp.affichages_armes.get(idx.get("display") or "", []):
            aff = acc.charger(f)
            out.append((f.relative_to(TACZ).parts[0], aff, self.modele(aff.get("model"))))
        return out

    def integres(self, arme):
        """Accessoires intégrés (« builtin_attachments ») : dessinés quand rien n'est posé à leur place."""
        idx = acc.charger(self.gp.armes[arme][0])
        for f in self.gp.donnees_armes.get(idx.get("data") or "", []):
            b = acc.charger(f).get("builtin_attachments") or {}
            if b:
                return {t: a for t, a in b.items() if isinstance(a, str) and a}
        return {}

    def accessoire(self, a):
        """(affichage, modèle ou None, boîtes visibles du modèle dans son propre repère ou None)."""
        if a not in self._acc:
            aff = self.gp.affichage_acc.get(a, {})
            m = self.modele(aff.get("model")) if aff.get("model") else None
            b = None
            if m is not None and not m.erreur:
                b = Boites(m.boites(None, None, sauter_accessoire(m, (aff.get("text_show") or {}).keys())))
                b = b.filtre((b.hi - b.lo).max(axis=1) <= LONGUEUR_MAX)   # faisceaux dessinés en cubes
                b = b.visibles(self.texture(aff.get("texture"), m))
            self._acc[a] = (aff, m, b)
        return self._acc[a]


# ====================================================================== contrôle d'une arme
LONGUEUR_MAX = 40.0   # px : un cube d'accessoire plus long est un faisceau de laser dessiné, pas une pièce
SORTIE = {"scope": (0, -1, 0), "grip": (0, 1, 0), "stock": (0, 0, 1), "muzzle": (0, 0, -1)}   # repère Java (y bas)


class Mesure:
    __slots__ = ("arme", "copie", "acc", "type", "origine", "ecart", "enfonce", "decolle", "cause", "defauts",
                 "accepte", "rien")

    def __init__(self, arme, copie, a, type_, origine):
        self.arme, self.copie, self.acc, self.type, self.origine = arme, copie, a, type_, origine
        self.ecart = self.enfonce = self.decolle = None
        self.cause, self.defauts, self.accepte, self.rien = "", [], None, False


def sortie_laser(A, origine):
    """Sens de montage d'un laser (repère de l'arme) : du point de montage vers le centre du laser, sur le côté,
    dessous ou dessus."""
    if not len(A):
        return np.array([0.0, 1.0, 0.0])
    poids = np.prod(2 * A.H + 0.05, axis=1)
    c = (A.C * poids[:, None]).sum(axis=0) / poids.sum() - origine
    if abs(c[0]) > abs(c[1]):
        return np.array([math.copysign(1.0, c[0]), 0.0, 0.0])
    return np.array([0.0, math.copysign(1.0, c[1]) if abs(c[1]) > 1e-6 else 1.0, 0.0])


def accepte(m):
    """Raison si tous les défauts de la mesure sont acceptés dans ACCEPTES, sinon None."""
    raisons = []
    for d in m.defauts:
        genre = d.split(" ")[0].rstrip(":")
        r = next((ACCEPTES[c] for c in ((m.arme, m.acc, genre), (m.arme, m.acc, "*"), ("*", m.acc, genre),
                                        ("*", m.acc, "*"), (m.arme, "*", genre)) if c in ACCEPTES), None)
        if r is None:
            return None
        raisons.append(r)
    return " ; ".join(dict.fromkeys(raisons)) or None


def scene(cat, M, aff, poses, t, cache=None):
    """(boîtes de l'arme, boîtes de l'accessoire, sens de montage, raison s'il n'est pas dessiné) pour l'accessoire
    de type t parmi poses {type: accessoire} ; cache : boîtes de l'arme par état des os (même modèle). Les boîtes de
    l'arme portent dans « adaptes » les os de l'adaptateur de cet accessoire (tube de crosse…), faits pour entrer
    dedans."""
    a = poses[t]
    affa, ma, Aloc = cat.accessoire(a)
    adapt = {cat.accessoire(x)[0].get("adapter") for x in poses.values()} - {None, ""}
    bouche = poses.get("muzzle")
    montre = vrai(cat.accessoire(bouche)[0].get("show_muzzle")) if bouche else False
    lunette = poses.get("scope")
    montage = vrai(cat.accessoire(lunette)[0].get("show_mount"), True) if lunette else True
    textes = (aff.get("text_show") or {}).keys()
    par_noeud = Aloc is None and affa.get("adapter")
    if par_noeud:
        adapt.discard(affa.get("adapter"))       # le nœud de l'arme est l'accessoire : mesuré contre le reste
    vis = regle_arme(M, Config(poses, adapt, montre, montage), textes)
    cle = (frozenset(poses), frozenset(adapt), montre, montage)
    if cache is None or cle not in cache:
        G = Boites(M.boites(vis)).visibles(cat.texture(aff.get("texture"), M))
        if cache is not None:
            cache[cle] = G
    G = cache[cle] if cache is not None else G
    G.adaptes = M.sous_arbre(affa.get("adapter")) if affa.get("adapter") and not par_noeud else set()
    if par_noeud:
        if affa["adapter"] not in M.os:
            return G, Boites(), None, "pas de nœud %s sous attachment_adapter" % affa["adapter"]
        A = Boites(M.boites(None, depart=affa["adapter"])).visibles(cat.texture(aff.get("texture"), M))
        return G, A, None, None             # pièce du modèle de l'arme : seule son existence est contrôlée
    raison = pos_dessine(M, t, vis)
    if Aloc is None or raison:
        return G, Boites(), None, raison
    F = repere_accessoire(M, t)
    A = Aloc.transforme(F)
    origine = M.repere(t + "_pos")[:3, 3]
    d = sortie_laser(A, origine) if t == "laser" else F[:3, :3] @ np.array(SORTIE[t], float)
    return G, A, d, None


def controler_arme(cat, arme, seulement=None):
    """Mesures de chaque accessoire permis sur chaque copie de l'arme."""
    out = []
    possibles = cat.possibles(arme)
    integres = cat.integres(arme)
    for copie, aff, M in cat.copies(arme):
        if M is None or M.erreur:
            m = Mesure(arme, copie, "-", "-", True)
            m.defauts = ["modèle de l'arme %s : %s" % (aff.get("model"), M.erreur if M else "introuvable")]
            out.append(m)
            continue
        cache = {}
        for a in sorted(possibles):
            if seulement and a not in seulement:
                continue
            t = cat.gp.type_acc[a]
            m = Mesure(arme, copie, a, t, possibles[a])
            poses = {k: v for k, v in integres.items() if k != t and cat.gp.type_acc.get(v) == k}
            poses[t] = a
            G, A, d, raison = scene(cat, M, aff, poses, t, cache)
            if raison:
                m.defauts.append("invisible : " + raison)
            elif d is None or not len(A):
                m.rien = True       # nœud du modèle de l'arme (crosse OEM, dessinée par son auteur) ou rien de solide
            else:
                mesurer(m, M, G, A, d)
            m.accepte = accepte(m) if m.defauts else None
            out.append(m)
        for t in TYPES:
            ecarts = sorted(m.ecart for m in out if m.copie == copie and m.type == t and m.ecart is not None)
            if len(ecarts) >= 5 and ecarts[len(ecarts) // 2] > SEUIL_DECALAGE:
                m = Mesure(arme, copie, "*", t, True)
                m.defauts = ["décalage : la plupart des %s de cette arme flottent (écart médian %.2f px sur %d) : "
                             "déplacer l'os %s_pos" % (acc.LISTES[t], ecarts[len(ecarts) // 2], len(ecarts), t)]
                m.accepte = accepte(m)
                out.append(m)
    return out


NOMS_SENS = {(1, -1): "vers le haut", (1, 1): "vers le bas", (2, -1): "vers l'avant", (2, 1): "vers l'arrière",
             (0, -1): "sur le côté", (0, 1): "sur le côté"}


def sens_de(v):
    i = int(np.argmax(np.abs(v)))
    return NOMS_SENS[(i, 1 if v[i] > 0 else -1)]


def mesurer(m, M, G, A, d):
    """Écart, enfoncement et défauts d'un accessoire A posé sur l'arme G (sens de montage d)."""
    t = m.type
    m.ecart = ecart(A, G)
    obstacles = G.sans(G.adaptes)
    if t in ("muzzle", "stock") and t + "_pos" in M.os:
        # le canon entre dans la bouche, le tube de crosse dans la crosse : ne comptent que les pièces tout entières
        # de l'autre côté du point de montage (garde-main, carcasse, poignée…)
        o = M.repere(t + "_pos")[:3, 3]
        proj = obstacles.C @ d + (np.abs(np.einsum("j,nji->ni", d, obstacles.R)) * obstacles.H).sum(axis=1)
        obstacles = obstacles.filtre(proj <= o @ d + 0.1)
    # enfoncement : le plus court chemin pour sortir de l'arme, dans le sens du montage ou de travers (une poignée
    # qui touche à peine le chargeur en sort par le côté : peu enfoncée), jamais en poussant vers l'arme
    axe = int(np.argmax(np.abs(d)))
    sens = [d] + [s * np.eye(3)[i] for i in range(3) if i != axe for s in (1.0, -1.0)]
    m.enfonce, cause, par = None, None, None
    for v in sens:
        e, c = enfoncement(A, obstacles, v)
        if m.enfonce is None or e < m.enfonce - 1e-6:
            m.enfonce, cause, par = e, c, v
        if m.enfonce == 0:
            break
    if cause:
        m.cause = "%s / %s" % cause
    if m.ecart > SEUIL_ECART[t]:
        m.decolle = decollement(A, G, d)
        loin = m.decolle is not None and m.decolle > m.ecart + 0.2
        m.defauts.append("flotte à %.1f px de l'arme%s" % (
            m.ecart, (" (%.1f px dans le sens du montage)" % m.decolle) if loin else ""))
    elif m.enfonce > SEUIL_ENFONCE[t]:
        m.defauts.append("traverse « %s » de l'arme (%.1f px à sortir %s)" % (cause[1], m.enfonce, sens_de(par)))


# ====================================================================== images de contrôle (Pillow)
FACES = ((0, 1, 3, 2, 0, -1), (4, 5, 7, 6, 0, 1), (0, 1, 5, 4, 1, -1), (2, 3, 7, 6, 1, 1), (0, 2, 6, 4, 2, -1),
         (1, 3, 7, 5, 2, 1))          # coins de chaque face, axe et sens de sa normale (repère Java de la boîte)
CLES_FACES = ("east", "west", "up", "down", "north", "south")   # FaceUVsItem.getFace, dans l'ordre de FACES
GRIS, ORANGE, ROUGE = (150, 156, 168), (238, 146, 40), (226, 64, 52)
LUMIERE = np.array([-0.55, -0.7, -0.45]) / np.linalg.norm([-0.55, -0.7, -0.45])
VUES = {   # (axe horizontal, signe, axe de profondeur, sens de la caméra) ; vertical : y Java (vers le bas)
    "cote": (2, -1, 0, -1),           # de côté (caméra du côté -x), bouche à droite
    "face": (0, 1, 2, -1),            # de face (caméra devant la bouche)
}


def police(taille):
    from PIL import ImageFont
    for f in ("DejaVuSans.ttf", "arial.ttf", "Arial.ttf", "LiberationSans-Regular.ttf"):
        try:
            return ImageFont.truetype(f, taille)
        except OSError:
            continue
    try:
        return ImageFont.load_default(taille)
    except TypeError:
        return ImageFont.load_default()


COUVERTURE_MIN = 0.3   # face dont moins de 30 % des pixels sont opaques : décalque ou effet, pas une pièce


class Texture:
    """Couleur moyenne de chaque face des cubes, lue dans la texture du modèle (UV « boîte » ou par face)."""

    def __init__(self, fichier, taille_uv):
        from PIL import Image
        self.img = np.asarray(Image.open(fichier).convert("RGBA"), dtype=np.float32) if fichier else None
        self.taille_uv = taille_uv
        self.cache = {}

    def moyenne(self, u, v, w, h):
        img = self.img
        sx, sy = img.shape[1] / self.taille_uv[0], img.shape[0] / self.taille_uv[1]
        u0, u1 = sorted((u * sx, (u + w) * sx))
        v0, v1 = sorted((v * sy, (v + h) * sy))
        x0, y0 = int(math.floor(u0)), int(math.floor(v0))
        x1, y1 = max(int(math.ceil(u1)), x0 + 1), max(int(math.ceil(v1)), y0 + 1)
        zone = img[max(y0, 0):min(y1, img.shape[0]), max(x0, 0):min(x1, img.shape[1])].reshape(-1, 4)
        opaque = zone[zone[:, 3] > 25]
        if len(opaque) < COUVERTURE_MIN * max(len(zone), 1):
            return None                    # transparente ou presque (décalque, effet) : pas une pièce solide
        return tuple(int(c) for c in opaque[:, :3].mean(axis=0))

    def faces(self, uv):
        """Les 6 couleurs (ou None) d'un cube, dans l'ordre de FACES."""
        cle = id(uv)
        if cle in self.cache:
            return self.cache[cle]
        if self.img is None:
            out = [()] * 6
        elif uv[0] == "faces":
            out = []
            for k in CLES_FACES:
                f = uv[1].get(k)
                if not isinstance(f, dict) or "uv" not in f:
                    out.append(None)
                    continue
                t = f.get("uv_size") or [1, 1]
                out.append(self.moyenne(float(f["uv"][0]), float(f["uv"][1]), float(t[0]), float(t[1])))
        else:
            (u, v), (dx, dy, dz), miroir = uv[1], uv[2], uv[3]
            out = [self.moyenne(u, v + dz, dz, dy), self.moyenne(u + dz + dx, v + dz, dz, dy),
                   self.moyenne(u + dz, v, dx, dz), self.moyenne(u + dz + dx, v, dx, dz),
                   self.moyenne(u + dz, v + dz, dx, dy), self.moyenne(u + 2 * dz + dx, v + dz, dx, dy)]
            if miroir:
                out[0], out[1] = out[1], out[0]
        self.cache[cle] = out
        return out


def projeter(vue, pts):
    ah, sg, _, _ = VUES[vue]
    return np.stack([sg * pts[..., ah], pts[..., 1]], axis=-1)


def cadre_de(boites_list, vue, marge=1.5):
    pts = [projeter(vue, b.coins()).reshape(-1, 2) for b in boites_list if len(b)]
    if not pts:
        return (-1, -1, 1, 1)
    p = np.concatenate(pts)
    lo, hi = p.min(axis=0) - marge, p.max(axis=0) + marge
    return (lo[0], lo[1], hi[0], hi[1])


def dessiner(img, groupes, vue, cadre, boite_px, sur=2):
    """Dessine les groupes [(Boites, Texture ou None, teinte, force de la teinte)] dans le rectangle boite_px
    (x0, y0, x1, y1) de l'image, cadre en pixels du modèle ; peintre (faces du fond d'abord), ombrage plat, couleur
    moyenne de la texture sur chaque face."""
    from PIL import Image, ImageDraw
    x0, y0, x1, y1 = boite_px
    w, h = (x1 - x0) * sur, (y1 - y0) * sur
    ech = min(w / max(cadre[2] - cadre[0], 1e-3), h / max(cadre[3] - cadre[1], 1e-3))
    ox = (w - ech * (cadre[2] - cadre[0])) / 2 - ech * cadre[0]
    oy = (h - ech * (cadre[3] - cadre[1])) / 2 - ech * cadre[1]
    _, _, ap, sc = VUES[vue]
    polys = []
    for b, tex, teinte, force in groupes:
        if not len(b):
            continue
        co = b.coins()
        uv = projeter(vue, co) * ech + (ox, oy)
        cols = [tex.faces(x) if tex else [()] * 6 for x in b.uv]
        for f, (i0, i1, i2, i3, axe, sgn) in enumerate(FACES):
            n = b.R[:, :, axe] * sgn
            vu = n[:, ap] * sc < -1e-6                     # face tournée vers la caméra
            if not vu.any():
                continue
            prof = (co[:, [i0, i1, i2, i3], ap]).mean(axis=1) * sc
            lum = 0.5 + 0.5 * np.clip(n @ LUMIERE, 0, 1)
            for k in np.nonzero(vu)[0]:
                c = cols[k][f]
                if c is None:
                    continue
                c = c or GRIS
                c = tuple(int((c[i] * (1 - force) + teinte[i] * force) * lum[k]) for i in range(3))
                polys.append((-prof[k], uv[k, [i0, i1, i2, i3]], c, teinte if force else None))
    polys.sort(key=lambda p: p[0])
    calque = Image.new("RGB", (int(w), int(h)), (236, 238, 242))
    dr = ImageDraw.Draw(calque)
    for _, q, coul, trait in polys:
        dr.polygon([tuple(p) for p in q], fill=coul,
                   outline=tuple(int(c * 0.8) for c in trait) if trait else tuple(int(c * 0.85) for c in coul))
    calque = calque.resize((x1 - x0, y1 - y0), Image.LANCZOS)
    img.paste(calque, (x0, y0))
    return ech / sur, ox / sur + x0, oy / sur + y0


def cellule(img, x, y, w, h, G, A, texG, texA, titre, lignes, couleur, police_t, police_l, defaut=False):
    """Une case de planche : à gauche, l'accessoire en gros plan de côté ; à droite, l'arme entière de côté et
    l'accessoire de face ; en haut, le nom et les mesures."""
    from PIL import ImageDraw
    dr = ImageDraw.Draw(img)
    dr.rectangle((x, y, x + w - 1, y + h - 1), outline=couleur, width=3)
    haut = 24 + 15 * len(lignes)
    dr.text((x + 8, y + 5), titre, fill=(20, 20, 24), font=police_t)
    for i, l in enumerate(lignes):
        dr.text((x + 8, y + 26 + 15 * i), l, fill=couleur if i == 0 else (60, 60, 70), font=police_l)
    groupes = [(G, texG, GRIS, 0.0), (A, texA, ROUGE if defaut else ORANGE, 0.35)]
    y0, y1 = y + haut + 4, y + h - 5
    if not len(A):
        dessiner(img, groupes, "cote", cadre_de([G], "cote"), (x + 5, y0, x + w - 5, y1))
        return
    xm = x + int(w * 0.64)
    c = cadre_de([A], "cote", marge=0)
    m = max(4.0, 0.3 * max(c[2] - c[0], c[3] - c[1]))
    dessiner(img, groupes, "cote", (c[0] - m, c[1] - m, c[2] + m, c[3] + m), (x + 5, y0, xm - 3, y1))
    ym = (y0 + y1) // 2
    dessiner(img, groupes, "cote", cadre_de([G, A], "cote"), (xm + 3, y0, x + w - 5, ym - 3))
    dessiner(img, groupes, "face", cadre_de([A], "face", marge=4), (xm + 3, ym + 3, x + w - 5, y1))
    for r in ((x + 5, y0, xm - 3, y1), (xm + 3, y0, x + w - 5, ym - 3), (xm + 3, ym + 3, x + w - 5, y1)):
        dr.rectangle(r, outline=(205, 206, 214))
    dr.text((x + 9, y1 - 16), "gros plan, de côté", fill=(120, 120, 130), font=police_l)
    dr.text((xm + 7, ym - 19), "arme entière", fill=(120, 120, 130), font=police_l)
    dr.text((xm + 7, y1 - 16), "de face", fill=(120, 120, 130), font=police_l)


def nom_fichier(arme, copie, type_, plusieurs):
    return "%s%s_%s.png" % (arme.replace(":", "__"), ("__" + copie) if plusieurs else "", acc.LISTES[type_])


def planche(cat, arme, copie, type_, mesures, dossier, plusieurs=False):
    """Planche d'une arme pour un type d'accessoire : l'arme seule, puis une case par accessoire."""
    from PIL import Image, ImageDraw
    aff, M = next((a, m) for c, a, m in cat.copies(arme) if c == copie)
    integres = cat.integres(arme)
    cols = 4 if len(mesures) >= 6 else max(2, len(mesures) + 1)
    w, h, tete = 560, 340, 54
    rangs = (len(mesures) + 1 + cols - 1) // cols
    img = Image.new("RGB", (cols * w, tete + rangs * h), (255, 255, 255))
    dr = ImageDraw.Draw(img)
    pt, pl = police(15), police(12)
    n_def = sum(1 for m in mesures if m.defauts and not m.accepte)
    dr.text((10, 8), "%s  ·  %s  ·  %ss" % (arme, copie, NOMS_TYPES[type_]), fill=(10, 10, 14), font=police(20))
    dr.text((10, 32), "%d accessoire(s), %d défaut(s) ; l'accessoire est teinté en orange (en rouge : défaut) ; "
            "couleurs moyennes des textures, vue de côté sans perspective" % (len(mesures), n_def),
            fill=(70, 70, 80), font=pl)
    poses = {k: v for k, v in integres.items() if k != type_ and cat.gp.type_acc.get(v) == k}
    adapt = {cat.accessoire(x)[0].get("adapter") for x in poses.values()} - {None, ""}
    vis = regle_arme(M, Config(poses, adapt), (aff.get("text_show") or {}).keys())
    texG = cat.texture(aff.get("texture"), M)
    cellule(img, 0, tete, w, h, Boites(M.boites(vis)), Boites(), texG, None, "sans %s" % NOMS_TYPES[type_],
            ["arme seule (pièces d'origine)"], (90, 90, 100), pt, pl)
    cache = {}
    for i, m in enumerate(mesures, start=1):
        p = dict(poses)
        p[type_] = m.acc
        G, A, _, _ = scene(cat, M, aff, p, type_, cache)
        affa, ma, _ = cat.accessoire(m.acc)
        texA = cat.texture(affa.get("texture"), ma) if ma is not None else texG
        if m.defauts and not m.accepte:
            coul, etat = (200, 40, 36), " ; ".join(m.defauts)
        elif m.defauts:
            coul, etat = (176, 120, 0), "accepté : " + m.accepte
        else:
            coul, etat = (34, 140, 60), "bon"
        mes = ("écart %.2f px · enfoncement %.2f px" % (m.ecart, m.enfonce)) if m.ecart is not None else ""
        lignes = [etat[:90]] + ([etat[90:180]] if len(etat) > 90 else []) + [
            mes + (" · " if mes else "") + ("liste d'origine" if m.origine else "ajouté par heraliz_accessoires")]
        cellule(img, (i % cols) * w, tete + (i // cols) * h, w, h, G, A, texG, texA, m.acc, lignes, coul, pt, pl,
                bool(m.defauts and not m.accepte))
    dossier.mkdir(parents=True, exist_ok=True)
    f = dossier / nom_fichier(arme, copie, type_, plusieurs)
    img.quantize(256, dither=0).save(f, optimize=True)
    return f


# ====================================================================== principal
_CAT = None


def _init():
    global _CAT
    _CAT = Catalogue()


def _travail(arme):
    return arme, controler_arme(_CAT, arme)


def tout_controler(armes, jobs):
    """{arme: [Mesure]} ; en parallèle sur plusieurs processus si jobs > 1."""
    global _CAT
    if jobs <= 1 or len(armes) < 4:
        if _CAT is None:
            _init()
        return {a: controler_arme(_CAT, a) for a in armes}
    import multiprocessing as mp
    with mp.Pool(jobs, initializer=_init) as pool:
        return dict(pool.imap_unordered(_travail, armes, chunksize=1))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--rapport", action="store_true", help="détail classé par arme")
    ap.add_argument("--tout", action="store_true", help="avec --rapport : toutes les combinaisons")
    ap.add_argument("--images", nargs="*", metavar="ARME", help="planches de contrôle")
    ap.add_argument("--sortie", default=str(DEPOT / "work" / "verif_accessoires"), help="dossier des planches")
    ap.add_argument("--arme", action="append", default=[], help="seulement cette arme")
    ap.add_argument("--type", action="append", default=[], choices=TYPES, help="images : seulement ce type")
    ap.add_argument("--jobs", type=int, default=0, help="processus en parallèle (défaut : nombre de cœurs)")
    ap.add_argument("--verifier", action="store_true", help="contrôle seul (comportement par défaut)")
    args = ap.parse_args()
    import os
    jobs = args.jobs or min(8, os.cpu_count() or 1)
    _init()
    cat = _CAT
    armes = args.arme or cat.armes()
    inconnues = [a for a in armes if a not in cat.gp.armes]
    if inconnues:
        print("[verif_accessoires] arme(s) inconnue(s) : " + ", ".join(inconnues))
        sys.exit(2)
    res = tout_controler(armes, jobs)
    toutes = [m for a in sorted(res) for m in res[a]]
    combos = [m for m in toutes if m.acc not in ("-", "*")]
    defauts = [m for m in toutes if m.defauts and not m.accepte]
    acceptes = [m for m in toutes if m.defauts and m.accepte]
    if args.rapport:
        for a in sorted(res):
            ms = res[a]
            mauvais = [m for m in ms if m.defauts]
            if not mauvais and not args.tout:
                continue
            copies = sorted({m.copie for m in ms})
            print("%s  (%s)  %d accessoire(s), %d défaut(s)" % (a, ", ".join(copies), len({m.acc for m in ms}),
                                                                len([m for m in mauvais if not m.accepte])))
            for m in sorted(ms if args.tout else mauvais, key=lambda m: (m.type, m.acc, m.copie)):
                etat = "; ".join(m.defauts) if m.defauts else ("rien à mesurer" if m.rien else "bon")
                if m.accepte:
                    etat = "accepté (%s) : %s" % (m.accepte, etat)
                mes = "écart %5.2f  enf. %5.2f" % (m.ecart, m.enfonce) if m.ecart is not None else " " * 24
                print("   %-8s %-40s %s  %s%s%s" % (NOMS_TYPES.get(m.type, m.type), m.acc, mes, etat,
                                                     "" if m.origine else "  [ajouté]",
                                                     ("  [" + m.copie + "]") if len(copies) > 1 else ""))
        par_type = defaultdict(lambda: [0, 0])
        for m in combos:
            par_type[m.type][0] += 1
            par_type[m.type][1] += bool(m.defauts and not m.accepte)
        print("[verif_accessoires] par type : " + ", ".join(
            "%s %d/%d" % (NOMS_TYPES[t], par_type[t][1], par_type[t][0]) for t in TYPES if t in par_type))
    if args.images is not None:
        dossier = Path(args.sortie)
        voulues = args.images or sorted({m.arme for m in defauts})
        n = 0
        for a in voulues:
            ms = res.get(a) or controler_arme(cat, a)
            copies = sorted({m.copie for m in ms})
            for copie in copies:
                for t in args.type or TYPES:
                    sel = [m for m in ms if m.copie == copie and m.type == t]
                    if sel:
                        planche(cat, a, copie, t, sel, dossier, len(copies) > 1)
                        n += 1
        print("[verif_accessoires] %d planche(s) dans %s" % (n, dossier))
    for m in defauts if not args.rapport else []:
        print("[verif_accessoires] %s + %s%s : %s" % (m.arme, m.acc, "" if len({x.copie for x in res[m.arme]}) == 1
                                                     else " [%s]" % m.copie, "; ".join(m.defauts)))
    print("[verif_accessoires] %d armes, %d accessoires : %d combinaisons contrôlées ; %d défaut(s) accepté(s) ; "
          "%d problème(s)" % (len(res), len({m.acc for m in combos}), len(combos), len(acceptes), len(defauts)))
    sys.exit(1 if defauts else 0)


if __name__ == "__main__":
    main()
