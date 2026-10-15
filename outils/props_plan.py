"""Plan de diversification des conteneurs de loot de la carte et rendus avant / après (appelés par
outils/props_carte.py).

Consigne du propriétaire (15/10) : utiliser les modèles de HeraliZ Loot, T1 à T6 seulement (T7 = événements), et que
100 % des modèles T1 à T6 soient posés sur la carte, en nombre équilibré (plus « toujours les mêmes »). C'est tout :
ni meubles cfm, ni anciens meubles cfm à reposer, ni points T7, ni familles (nourriture, soins, munitions).

Rien n'est écrit dans le monde : le plan produit des lots de commandes que le serveur jouera lui-même (workflow
« Props de la carte »), chacune conditionnelle (« le bloc est-il encore celui relevé ? ») et annulable.

Étapes du plan (chaque changement porte son étape ; le workflow choisit lesquelles jouer) :
  diversification  un conteneur T1 à T6 déjà posé est remplacé par un autre modèle du MÊME palier, dans cet ordre :
                   cohérence (deux cuisinières ou deux frigos dans la même pièce, prop d'extérieur dans une pièce
                   fermée...), couverture (chaque modèle au moins N fois, N selon le palier), équilibrage (les modèles
                   trop répétés cèdent des places aux modèles sous leur part) ;
  ajouts           (option --ajouts, signalé à part) seulement pour un modèle qui n'a AUCUNE place de remplacement
                   cohérente : posé sur une case libre près d'un conteneur de son palier ; la part d'actifs de la zone
                   est baissée d'autant (le loot de la zone ne change pas).

Règles de chaque remplacement : même palier de loot (le loot d'une zone ne change pas de niveau), même gabarit (un
meuble par un meuble, un objet par un objet, un cadavre par un cadavre, un véhicule par un véhicule), même
orientation, pièce ou extérieur compatibles (outils/props_catalogue.py : pas de prop d'extérieur dans une maison ni
l'inverse), groupes uniques respectés (une cuisinière, un frigo... par pièce), place libre exacte pour les cases en
plus d'un gros conteneur (de l'air, un sol dessous, dans la même pièce, ni devant une porte, ni dans un passage d'un
bloc, ni devant un autre meuble, sans couper la pièce en deux). Un conteneur qui garde des données (inventaire) n'est
jamais touché.
"""
import json
import math
import random
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import props_catalogue as pc  # noqa: E402
import props_carte as pk  # noqa: E402
import props_monde as pm  # noqa: E402

R = pk.R
DIRS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
PALIERS = (1, 2, 3, 4, 5, 6)
GABARITS = ("meuble", "objet", "cadavre", "véhicule")
# minimum de places sur la carte pour chaque modèle, selon son palier (plafonné par les places du palier : T6 n'a que
# quelques dizaines de conteneurs pour presque autant de modèles)
QUOTA = {1: 8, 2: 6, 3: 4, 4: 4, 5: 3, 6: 2}
QUOTA_AJOUT = 2    # places au plus par modèle posé par l'étape « ajouts »
SEUIL = 1.5        # un modèle posé plus de SEUIL fois sa part de son groupe (palier + gabarit) est « trop répété »
PASSES = 3         # passes d'équilibrage
# lieux (dossiers des zones de loot) -> extérieurs
LIEU_EXT = {"aerodrome": "base", "silo": "base", "arctique": "base", "radio": "base", "bunker": "base", "prison": "base",
            "petrole": "chantier", "port": "quai rue", "jetee": "quai", "campagne": "ferme route jardin",
            "routes": "route", "gaz": "chantier rue", "faille": "camp route", "halte_aerodrome": "route base"}
ETAPES = ["diversification", "ajouts"]
MOTIFS = ["cohérence", "couverture", "équilibrage"]


def cle_palier(pr):
    m = re.match(r"([a-z]+)(\d)_", pr.id.split(":", 1)[1])
    return (m.group(1), int(m.group(2))) if m else ("?", 0)


def vise(pr):
    """Modèle concerné par le plan : conteneur de HeraliZ Loot T1 à T6 (pas T7, pas les familles), avec du loot."""
    if pr is None or pr.mod != "heraliz_loot" or not pr.loot or pr.be or pr.gab not in GABARITS:
        return False
    f, n = cle_palier(pr)
    return f == "t" and n in PALIERS


class Emplacement:
    """Un conteneur remplaçable de la carte."""
    __slots__ = ("p", "pr", "pr0", "facing", "cells", "env", "rtype", "rsur", "piece", "ctx", "cle", "gab", "porteur",
                 "v", "change", "lieu")

    def __init__(self, **k):
        for s in self.__slots__:
            setattr(self, s, k.get(s))


class Planif:
    def __init__(self, a, graine=1, max_changements=None, ajouts=False, compenser=True):
        self.a, self.cat = a, a.cat
        self.compenser = compenser
        self.garder_groupes = True
        self.rnd = random.Random(graine)
        self.ajouts = ajouts
        self.compte = Counter(p["id"] for p in a.props)
        self.changes = []
        self.ajouts_proposes = []
        self.non_corriges = Counter()
        self.motifs = Counter()
        self.max = max_changements
        # cases devant la face avant des meubles (on n'y pose rien) et cases occupées
        self.devant = set()
        self.occupe = {}
        for p in a.props:
            pr = self.cat.get(p["id"])
            f = (p["etat"] or {}).get("facing", "north")
            cells = pr.cellules_monde(f) if pr else [(0, 0, 0)]
            for c in cells:
                self.occupe[(p["x"] + c[0], p["y"] + c[1], p["z"] + c[2])] = p["i"]
            if pr and (pr.gab == "meuble" or pr.mod == "cfm"):
                dx, dz = DIRS.get(pr.avant(f), (0, 0))
                for c in cells:
                    if c[1] == 0:
                        self.devant.add((p["x"] + c[0] + dx, p["y"], p["z"] + c[2] + dz))
        # état des pièces
        self.r_ids = defaultdict(Counter)
        self.r_grp = defaultdict(Counter)
        self.r_chg = Counter()
        for p in a.props:
            if p["piece"]:
                self.r_ids[p["piece"]][p["id"]] += 1
                pr = self.cat.get(p["id"])
                if pr and pr.groupe:
                    self.r_grp[p["piece"]][pr.groupe] += 1
        self._contextes()
        self._emplacements()
        self._pools()

    # ------------------------------------------------------------------ préparation
    def _contextes(self):
        """Extérieurs autour de chaque prop du dehors : lieu + votes des props d'extérieur voisins (12 blocs) :
        conteneurs de HeraliZ Loot, et à demi-voix les décors (cfm, Utils, Décors) propres à un ou deux extérieurs
        (tombes d'un cimetière, épaves d'une route...)."""
        grille = defaultdict(list)
        for p in self.a.props:
            pr = self.cat.get(p["id"])
            if not pr or not pr.ext or p["piece"]:
                continue
            if pr.mod == "heraliz_loot":
                grille[(p["x"] >> 4, p["z"] >> 4)].append((p, pr, 1.0))
            elif pr.dayz and not pr.structure and len(pr.ext) <= 2:
                grille[(p["x"] >> 4, p["z"] >> 4)].append((p, pr, 0.5))
        self.ctx_de = {}
        for p in self.a.props:
            if p["piece"] and self.a.env(p) == "int":
                continue
            votes = Counter()
            for gx in range((p["x"] >> 4) - 1, (p["x"] >> 4) + 2):
                for gz in range((p["z"] >> 4) - 1, (p["z"] >> 4) + 2):
                    for q, pr, poids in grille.get((gx, gz), ()):
                        if (q["x"] - p["x"]) ** 2 + (q["z"] - p["z"]) ** 2 <= 144 and len(pr.ext) <= 4:
                            for e in sorted(pr.ext):
                                votes[e] += poids / len(pr.ext)
            top = votes.most_common(3)
            ctx = {e for e, n in top if n >= 0.5 * top[0][1]} if top else set()
            lieu = p.get("lieu") or ""
            ctx |= set(LIEU_EXT.get(lieu, "rue jardin" if lieu else "route").split())
            self.ctx_de[p["i"]] = ctx

    def _emplacements(self):
        self.emps = []
        self.avec_donnees = Counter()
        for p in self.a.props:
            pr = self.cat.get(p["id"])
            if not vise(pr):
                continue
            if p["be"]:
                self.avec_donnees[p["id"]] += 1      # inventaire ou données : jamais touché
                continue
            f = (p["etat"] or {}).get("facing")
            if f not in DIRS:
                continue
            v = pk.voisinage(p)
            dessus = v[2][R][R] & 7
            cells = pr.cellules_monde(f)
            porteur = dessus == pm.PROP and (0, 1, 0) not in cells
            info = self.a.pieces.get(p["piece"]) if p["piece"] else None
            e = Emplacement(p=p, pr=pr, pr0=pr, facing=f, cells=cells, env=self.a.env(p), piece=p["piece"],
                            rtype=info.get("type") if info else None, rsur=info.get("sur", 0) if info else 0,
                            ctx=self.ctx_de.get(p["i"], set()), cle=cle_palier(pr), gab=pr.gab, porteur=porteur, v=v,
                            change=None, lieu=p.get("lieu"))
            self.emps.append(e)
        self.par_i = {e.p["i"]: e for e in self.emps}

    def _pools(self):
        """Modèles interchangeables : même palier et même gabarit ; part et minimum de chaque modèle."""
        self.pools = defaultdict(list)
        self.modeles = []
        for i, pr in sorted(self.cat.items()):
            if vise(pr):
                self.pools[(cle_palier(pr), pr.gab)].append(pr)
                self.modeles.append(pr)
        n_pool = Counter((e.cle, e.gab) for e in self.emps)
        self.n_pool = n_pool
        self.cible, self.mini = {}, {}
        for k, prs in self.pools.items():
            poids = sum(pc.frequence(pr.id.split(":")[1]) for pr in prs)
            mini = max(1, min(QUOTA.get(k[0][1], 2), n_pool.get(k, 0) // max(1, len(prs))))
            for pr in prs:
                part = n_pool.get(k, 0) * pc.frequence(pr.id.split(":")[1]) / max(1e-6, poids)
                self.mini[pr.id] = mini
                self.cible[pr.id] = max(mini, part)

    def quota(self, pr):
        return self.mini.get(pr.id, 1)

    def ratio(self, pr):
        return self.compte[pr.id] / max(1.0, self.cible.get(pr.id, 1.0))

    # ------------------------------------------------------------------ règles
    def compatible(self, e, q):
        """q peut-il prendre la place de e ? (même groupe palier + gabarit, pièce, extérieur, groupes, place)"""
        if q.id == e.pr.id or cle_palier(q) != e.cle or q.gab != e.gab:
            return False
        if e.env == "int" or (e.env == "abri" and e.piece):
            if not q.pieces:
                return False
            sur = e.rtype and e.rsur >= 0.35 and len(self.a.par_piece.get(e.piece, ())) >= 2
            if sur:
                if e.rtype not in q.pieces:
                    return False
            elif not (q.pieces & (e.pr0.pieces or set())):
                return False
            # un prop propre à une ou deux sortes de pièces (matelas, lavabo...) : seulement dans une pièce dont le
            # type est sûr (la moitié des votes, ou un bon tiers dans une pièce d'au moins 3 props)
            if len(q.pieces) <= 2 and not (e.rtype in q.pieces and self.type_sur(e)):
                return False
        else:
            if not (q.ext & e.ctx):
                return False
        if e.piece:
            ids = self.r_ids[e.piece]
            if q.groupe:
                n = self.r_grp[e.piece][q.groupe] - (1 if e.pr.groupe == q.groupe else 0)
                if n >= pc.maxi_groupe(q.groupe, self.a.pieces.get(e.piece, {})):
                    return False
            if ids[q.id] >= (1 if q.gab == "meuble" else 2):
                return False
        if e.porteur and (q.gab != "meuble" or (q.haut or 16) < 14):
            return False
        if self.garder_groupes and e.piece and e.pr.groupe in pc.ESSENTIELS and q.groupe != e.pr.groupe \
                and self.r_grp[e.piece][e.pr.groupe] <= 1:
            return False
        return self.place(e, q)

    def type_sur(self, e):
        return e.rsur >= 0.5 or (e.rsur >= 0.35 and len(self.a.par_piece.get(e.piece, ())) >= 3)

    def place(self, e, q):
        """Les cases en plus de q (gros conteneur) sont-elles libres, au sol, dans la pièce, hors passage ?"""
        v = e.v
        x, y, z = e.p["x"], e.p["y"], e.p["z"]
        old = set(e.cells)
        extra = [c for c in q.cellules_monde(e.facing) if c not in old]
        bas = []
        for (dx, dy, dz) in extra:
            if abs(dx) > R or abs(dz) > R or not (0 <= dy <= 3):
                return False
            code = v[dy + 1][dz + R][dx + R]
            if code & 7 != pm.VIDE:
                return False
            if self.occupe.get((x + dx, y + dy, z + dz), e.p["i"]) != e.p["i"]:
                return False        # case déjà prise par un autre changement du plan
            if dy == 0:
                if code & (pk.F_PASSAGE | pk.F_PORTE):
                    return False
                if e.piece and e.env == "int" and not code & pk.F_PIECE:
                    return False
                if v[0][dz + R][dx + R] & 7 != pm.MUR:
                    return False
                if (x + dx, y, z + dz) in self.devant:
                    return False
                bas.append((dx, dz))
        if bas:
            from scipy import ndimage
            libre = np.isin(v[1] & 7, (pm.VIDE, pm.FIN))
            _l, n0 = ndimage.label(libre)
            for dx, dz in bas:
                libre[dz + R, dx + R] = False
            _l, n1 = ndimage.label(libre)
            if n1 > n0:
                return False
            # le meuble plus large ne doit pas se retrouver face à un mur
            if q.gab == "meuble":
                fx, fz = DIRS[q.avant(e.facing)]
                for dx, dz in bas:
                    if abs(dx + fx) <= R and abs(dz + fz) <= R and v[1][dz + fz + R][dx + fx + R] & 7 == pm.MUR:
                        return False
        return True

    def candidats(self, e, filtre=None):
        return [q for q in self.pools.get((e.cle, e.gab), ()) if (not filtre or filtre(q)) and self.compatible(e, q)]

    def score(self, e, q):
        s = (self.compte[q.id] + 1) / max(1.0, self.cible.get(q.id, 1.0))
        if e.rtype and e.rtype in q.pieces and len(q.pieces) <= 3:
            s -= 0.15
        if len(q.cellules) != len(e.pr.cellules):
            s += 0.2
        return s + self.rnd.random() * 0.05

    def appliquer(self, e, q, motif, raison):
        if self.max is not None and len(self.changes) >= self.max:
            return False
        old = e.pr
        if self.compte[old.id] <= 1:
            return False            # un modèle posé ne disparaît jamais de la carte
        self.compte[old.id] -= 1
        self.compte[q.id] += 1
        if e.piece:
            self.r_ids[e.piece][old.id] -= 1
            self.r_ids[e.piece][q.id] += 1
            if old.groupe:
                self.r_grp[e.piece][old.groupe] -= 1
            if q.groupe:
                self.r_grp[e.piece][q.groupe] += 1
            self.r_chg[e.piece] += 1
        x, y, z = e.p["x"], e.p["y"], e.p["z"]
        old_cells = set(e.cells)
        new_cells = q.cellules_monde(e.facing)
        for c in old_cells - set(new_cells):
            self.occupe.pop((x + c[0], y + c[1], z + c[2]), None)
        extra = [c for c in new_cells if c not in old_cells]
        for c in extra:
            self.occupe[(x + c[0], y + c[1], z + c[2])] = e.p["i"]
            if 0 <= c[1] + 1 <= 4 and abs(c[0]) <= R and abs(c[2]) <= R:
                e.v[c[1] + 1][c[2] + R][c[0] + R] = (e.v[c[1] + 1][c[2] + R][c[0] + R] & 0xF8) | pm.PROP
        if q.gab == "meuble":
            fx, fz = DIRS[q.avant(e.facing)]
            for c in new_cells:
                if c[1] == 0:
                    self.devant.add((x + c[0] + fx, y, z + c[2] + fz))
        # un même emplacement changé deux fois (cohérence puis équilibrage) : un seul changement, depuis l'état relevé
        precedent = next((c for c in self.changes if c["i"] == e.p["i"]), None) if e.change else None
        if precedent is not None:
            self.changes.remove(precedent)
        avant0 = e.pr0
        extra0 = [c for c in new_cells if c not in set(avant0.cellules_monde(e.facing))]
        self.changes.append({"etape": "diversification", "motif": motif, "raison": raison, "i": e.p["i"],
                             "x": x, "y": y, "z": z, "avant": avant0.id, "etat_avant": dict(e.p["etat"] or {}),
                             "be": e.p["be"], "apres": q.id, "etat_apres": {"facing": e.facing},
                             "cases_libres": [[x + c[0], y + c[1], z + c[2]] for c in extra0],
                             "cases_avant": [[x + c[0], y + c[1], z + c[2]] for c in avant0.cellules_monde(e.facing)
                                             if c != (0, 0, 0)],
                             "piece": e.piece, "lieu": e.lieu})
        if q.id == avant0.id:       # revenu au modèle d'origine : plus rien à faire
            self.changes.pop()
            e.change = None
        else:
            e.change = q.id
        e.pr = q
        e.cells = new_cells
        self.motifs[motif] += 1
        return True

    def meilleur(self, e, motif, raison, filtre=None):
        cands = self.candidats(e, filtre)
        if not cands:
            return False
        q = min(cands, key=lambda q: self.score(e, q))
        return self.appliquer(e, q, motif, raison)

    # ------------------------------------------------------------------ diversification
    def etape_diversification(self):
        n0 = len(self.changes)
        self.garder_groupes = False
        try:
            self._coherence()
        finally:
            self.garder_groupes = True
        self._couverture()
        for _k in range(PASSES):
            if not self._equilibrage():
                break
        self._couverture()          # un modèle encore sous son minimum après l'équilibrage
        return len(self.changes) - n0

    def _coherence(self):
        """Incohérences de la carte : la place est rendue à un autre modèle du même palier (le moins posé)."""
        inc = self.a.inc
        a_corriger = []
        for pid, g, lst in inc["doublon"]:
            garde = pc.maxi_groupe(g, self.a.pieces.get(pid, {}))
            for p in sorted(lst, key=lambda p: (p["x"], p["z"]))[garde:]:
                a_corriger.append((p, "doublon : %s en trop dans la pièce" % g))
        for pid, i, nb in inc["repetition"]:
            for p in [p for p in self.a.par_piece[pid] if p["id"] == i][1:]:
                a_corriger.append((p, "même conteneur répété dans une petite pièce"))
        for p in inc["exterieur_dedans"]:
            a_corriger.append((p, "prop d'extérieur dans une pièce fermée"))
        for p in inc["interieur_dehors"]:
            a_corriger.append((p, "prop d'intérieur à ciel ouvert"))
        for p in inc["mauvaise_piece"]:
            a_corriger.append((p, "ne va pas avec la pièce"))
        vus = set()
        for p, raison in a_corriger:
            if not vise(self.cat.get(p["id"])):
                continue            # familles, véhicules de décor... : hors du plan
            e = self.par_i.get(p["i"])
            if e is None or p["i"] in vus or e.change:
                if e is None:
                    self.non_corriges[raison] += 1
                continue
            vus.add(p["i"])
            if raison.startswith("prop d'extérieur"):
                ok = self.meilleur(e, "cohérence", raison, lambda q: bool(q.pieces))
            elif raison.startswith("prop d'intérieur"):
                e.env = "ext"
                ok = self.meilleur(e, "cohérence", raison, lambda q: bool(q.ext))
            else:
                ok = self.meilleur(e, "cohérence", raison)
            if not ok:
                self.non_corriges[raison] += 1

    def _places(self, q):
        """Emplacements où q peut aller sans faire passer leur modèle sous son minimum."""
        return [e for e in self.emps if e.cle == cle_palier(q) and e.gab == q.gab and e.pr.id != q.id
                and self.compte[e.pr.id] > self.quota(e.pr) and self.compatible(e, q)]

    def _couverture(self):
        """Chaque modèle au moins à son minimum : places prises d'abord aux modèles les plus répétés, étalées sur
        plusieurs lieux."""
        manque = [q for q in self.modeles if self.compte[q.id] < self.quota(q)]
        self.rnd.shuffle(manque)
        essais = sorted(((len(pl), q, pl) for q in manque for pl in [self._places(q)]), key=lambda t: t[0])
        for _n, q, places in essais:
            besoin = self.quota(q) - self.compte[q.id]
            if besoin <= 0 or not places:
                continue
            self.rnd.shuffle(places)
            places.sort(key=lambda e: (0 if e.rtype in q.pieces and e.rsur >= 0.5 else 1, -self.ratio(e.pr),
                                       self.r_chg[e.piece] if e.piece else 0))
            par_lieu = Counter()
            nb_lieux = len({x.lieu for x in places})
            for tour in range(2):
                for e in places:
                    if besoin <= 0:
                        break
                    if e.pr.id == q.id or self.compte[e.pr.id] <= self.quota(e.pr) or not self.compatible(e, q):
                        continue
                    # au plus 2 par lieu tant que d'autres lieux sont possibles (second tour : sans limite)
                    if tour == 0 and par_lieu[e.lieu] >= 2 and nb_lieux > len(par_lieu):
                        continue
                    if self.appliquer(e, q, "couverture", "%s absent ou trop peu posé" % q.id.split(":")[1]):
                        par_lieu[e.lieu] += 1
                        besoin -= 1

    def _equilibrage(self):
        n = 0
        ordre = list(self.emps)
        self.rnd.shuffle(ordre)
        for e in ordre:
            p = e.pr
            if self.compte[p.id] <= SEUIL * self.cible.get(p.id, 1):
                continue
            # au plus la moitié des conteneurs d'une pièce changent
            if e.piece and self.r_chg[e.piece] >= max(1, math.ceil(len(self.a.par_piece.get(e.piece, ())) / 2)):
                continue
            if self.meilleur(e, "équilibrage", "%s trop répété" % p.id.split(":")[1],
                             lambda q: self.compte[q.id] + 1 <= self.cible.get(q.id, 1)):
                n += 1
        return n

    # ------------------------------------------------------------------ absents et ajouts
    def raison_absent(self, q):
        """Pourquoi q n'a aucune place de remplacement."""
        k = (cle_palier(q), q.gab)
        if not self.n_pool.get(k):
            return "aucun conteneur T%d de gabarit « %s » sur la carte à remplacer (même palier, même gabarit)" % (
                k[0][1], q.gab)
        dedans = sorted(q.pieces)
        dehors = sorted(q.ext)
        if not dedans and not any(q.ext & e.ctx for e in self.emps if e.env != "int"):
            return "aucun extérieur « %s » repéré sur la carte (ni conteneur ni décor de ce genre autour des lieux)" % (
                " / ".join(dehors))
        quoi = []
        if dedans:
            quoi.append("pièces : " + " ".join(dedans))
        if dehors:
            quoi.append("dehors : " + " ".join(dehors))
        xs = [c[0] for c in q.cellules]; zs = [c[2] for c in q.cellules]
        taille = "%d×%d" % (max(xs) - min(xs) + 1, max(zs) - min(zs) + 1)
        return "aucun des %d conteneurs T%d « %s » n'est dans une place qui lui va (%s ; %s cases au sol)" % (
            self.n_pool[k], k[0][1], q.gab, " ; ".join(quoi) or "nulle part", taille)

    def _proche_palier(self, x, y, z, n, d=48):
        """Un conteneur T<n> déjà sur la carte à moins de d blocs ? (le conteneur ajouté rejoint alors la zone de son
        palier : HeraliZ Loot la cherche à 64 blocs)"""
        if not hasattr(self, "_grille_paliers"):
            self._grille_paliers = defaultdict(list)
            for p in self.a.props:
                pr = self.cat.get(p["id"])
                if vise(pr) or (pr and pr.mod == "heraliz_loot" and cle_palier(pr)[0] == "t"):
                    self._grille_paliers[(cle_palier(pr)[1], p["x"] >> 6, p["z"] >> 6)].append((p["x"], p["y"], p["z"]))
        for gx in range((x >> 6) - 1, (x >> 6) + 2):
            for gz in range((z >> 6) - 1, (z >> 6) + 2):
                for (a, b, c) in self._grille_paliers.get((n, gx, gz), ()):
                    if (a - x) ** 2 + (c - z) ** 2 <= d * d and abs(b - y) <= 16:
                        return True
        return False

    def etape_ajouts(self):
        """Modèles sans aucune place de remplacement : posés sur une case libre, dans une pièce de leur type (dos au
        mur, face à une case libre), dans une petite pièce vide d'une maison (salle de bain) ou dehors dans un
        extérieur qui leur va, toujours à moins de 48 blocs d'un conteneur de leur palier (le loot de la zone ne change
        pas de niveau). Signalés à part : joués seulement avec l'option --ajouts."""
        self.ajouts_proposes = []
        absents = [q for q in self.modeles if self.compte[q.id] == 0]
        self.raisons = {q.id: self.raison_absent(q) for q in absents}
        self.rnd.shuffle(absents)
        hotes_int, hotes_ext = defaultdict(list), []
        for e in self.emps:
            # pièce de type sûr, ou type en tête des votes d'une pièce d'au moins 3 props (un quart des votes)
            if e.env == "int" and e.piece and e.rtype and (self.type_sur(e) or (
                    e.rsur >= 0.25 and len(self.a.par_piece.get(e.piece, ())) >= 3)):
                hotes_int[e.rtype].append(e)
            elif e.env == "ext" and e.p["ciel"]:
                hotes_ext.append(e)
        # petites pièces fermées et vides des maisons (bâtiments avec cuisine, chambre ou salon) : salles de bain
        maisons = {self.a.pieces[pid].get("batiment") for pid in self.a.par_piece
                   if self.a.pieces.get(pid, {}).get("type") in ("cuisine", "chambre", "salon")}
        maisons.discard(None)
        lieu_bat = defaultdict(Counter)
        for p in self.a.props:
            if p.get("batiment") and p.get("lieu"):
                lieu_bat[p["batiment"]][p["lieu"]] += 1
        petites = [(pid, info) for pid, info in self.a.pieces.items() if info.get("vide") and 4 <= info.get("cases", 0) <= 12
                   and info.get("batiment") in maisons and info.get("plan")]
        par_piece, hotes_pris = Counter(), set()

        def hotes(q):
            cands = []
            for t in sorted(q.pieces):
                cands += [("int", e) for e in hotes_int.get(t, ())]
            if q.ext:
                cands += [("ext", e) for e in hotes_ext if q.ext & e.ctx]
            if q.pieces and q.pieces <= {"sdb", "buanderie"}:
                cands += [("vide", (pid, info)) for pid, info in petites]
            return cands
        # les modèles qui ont le moins d'hôtes possibles d'abord
        for _n, _k, q, cands in sorted((len(c), k, q, c) for k, q in enumerate(absents) for c in [hotes(q)]):
            n = q.niveau
            self.rnd.shuffle(cands)
            # d'abord près des conteneurs de son palier, en étalant sur les lieux
            cands.sort(key=lambda t: 0 if t[0] != "vide" and t[1].cle[1] == n else 1)
            voulu = min(QUOTA_AJOUT, self.quota(q))
            faits, par_lieu = 0, Counter()
            for tour in range(2):
                for env, h in cands:
                    if faits >= voulu:
                        break
                    if env == "vide":
                        pid, info = h
                        lieu = (lieu_bat[info["batiment"]].most_common(1) or [(None, 0)])[0][0]
                        piece, cases = pid, info["cases"]
                        cle_hote = ("vide", pid)
                    else:
                        lieu, piece = h.lieu, (h.piece if env == "int" else None)
                        cases = self.a.pieces.get(piece, {}).get("cases", 0) if piece else 0
                        cle_hote = h.p["i"]
                    if cle_hote in hotes_pris or (tour == 0 and par_lieu[lieu] >= 1):
                        continue
                    if piece and par_piece[piece] >= (2 if cases >= 24 else 1):
                        continue
                    if env == "vide":
                        plan = info["plan"]
                        v = np.frombuffer(__import__("zlib").decompress(__import__("base64").b64decode(plan["v"])),
                                          dtype=np.uint8).reshape(5, plan["p"], plan["l"]).copy()
                        spot = self._place_mur(v, q)
                        if spot is None:
                            continue
                        lx, lz, f = spot
                        x, y, z = plan["x"] + lx, info["y"], plan["z"] + lz
                    elif env == "int":
                        spot = self._place_mur(h.v, q, centre=True)
                        if spot is None:
                            continue
                        lx, lz, f = spot
                        x, y, z = h.p["x"] + lx - R, h.p["y"], h.p["z"] + lz - R
                    else:
                        spot = self._place_libre(h.p, q)
                        if spot is None:
                            continue
                        dx, dz, f = spot
                        x, y, z = h.p["x"] + dx, h.p["y"], h.p["z"] + dz
                    if not self._proche_palier(x, y, z, n):
                        continue
                    cells = q.cellules_monde(f)
                    if any((x + c[0], y + c[1], z + c[2]) in self.occupe or (c[1] == 0 and (x + c[0], y, z + c[2]) in self.devant)
                           for c in cells):
                        continue
                    self.ajouts_proposes.append({
                        "etape": "ajouts", "motif": "ajout",
                        "raison": "%s : aucune place de remplacement cohérente (%s)" % (
                            q.id.split(":")[1], {"vide": "petite pièce vide d'une maison", "int": "pièce %s" % (h.rtype if env == "int" else ""),
                                                 "ext": "dehors"}[env]),
                        "x": x, "y": y, "z": z, "avant": "minecraft:air", "etat_avant": {}, "be": None, "apres": q.id,
                        "etat_apres": {"facing": f}, "cases_libres": [[x + c[0], y + c[1], z + c[2]] for c in cells if c != (0, 0, 0)],
                        "cases_avant": [], "piece": piece, "lieu": lieu})
                    for c in cells:
                        self.occupe[(x + c[0], y + c[1], z + c[2])] = -1
                        if env == "int" and 0 <= c[1] + 1 <= 4 and 0 <= lz + c[2] < h.v.shape[1] and 0 <= lx + c[0] < h.v.shape[2]:
                            h.v[c[1] + 1][lz + c[2]][lx + c[0]] = (h.v[c[1] + 1][lz + c[2]][lx + c[0]] & 0xF8) | pm.PROP
                    if q.gab == "meuble":
                        fx, fz = DIRS[f]
                        for c in cells:
                            if c[1] == 0:
                                self.devant.add((x + c[0] + fx, y, z + c[2] + fz))
                    hotes_pris.add(cle_hote)
                    if piece:
                        par_piece[piece] += 1
                    par_lieu[lieu] += 1
                    faits += 1
        if self.ajouts:
            for ch in self.ajouts_proposes:
                self.compte[ch["apres"]] += 1
        return len(self.ajouts_proposes) if self.ajouts else 0

    def _place_libre(self, p, q):
        """(dx, dz, facing) d'une place libre dehors près du prop p pour q (toutes ses cases), ou None."""
        v = pk.voisinage(p)
        options = []
        for f in ("north", "south", "east", "west"):
            cells = q.cellules_monde(f)
            for dz in range(-R + 1, R):
                for dx in range(-R + 1, R):
                    if (dx, dz) == (0, 0):
                        continue
                    ok = True
                    for (cx, cy, cz) in cells:
                        X, Z = dx + cx, dz + cz
                        if abs(X) > R or abs(Z) > R or not (0 <= cy <= 3):
                            ok = False
                            break
                        code = v[cy + 1][Z + R][X + R]
                        if code & 7 != pm.VIDE or (cy == 0 and (code & (pk.F_PASSAGE | pk.F_PORTE | pk.F_COUVERT)
                                                                 or v[0][Z + R][X + R] & 7 != pm.MUR)):
                            ok = False
                            break
                        pos = (p["x"] + X, p["y"] + cy, p["z"] + Z)
                        if pos in self.devant or pos in self.occupe:
                            ok = False
                            break
                    if ok:
                        fx, fz = DIRS[f]
                        devant = (dx + fx, dz + fz)
                        if abs(devant[0]) <= R and abs(devant[1]) <= R and v[1][devant[1] + R][devant[0] + R] & 7 in (pm.VIDE, pm.FIN):
                            options.append((abs(dx) + abs(dz), dx, dz, f))
        if not options:
            return None
        options.sort()
        _d, dx, dz, f = options[0]
        return dx, dz, f

    def _place_mur(self, v, q, centre=False):
        """(lx, lz, facing) : case du plan v (couche 1 = étage du prop) libre, sol dessous, dans la pièce, dos à un
        mur et face à une case libre, hors passage et porte ; toutes les cases de q libres. Pour un gros conteneur :
        tout son dos contre le mur, tout son devant libre."""
        P, L_ = v.shape[1], v.shape[2]
        options = []
        for lz in range(1, P - 1):
            for lx in range(1, L_ - 1):
                for f in ("north", "south", "east", "west"):
                    ax, az = DIRS[f]
                    cells = q.cellules_monde(f)
                    sol = {(cx, cz) for (cx, cy, cz) in cells if cy == 0}
                    dos = [(lx + cx - ax, lz + cz - az) for (cx, cz) in sol if (cx - ax, cz - az) not in sol]
                    face = [(lx + cx + ax, lz + cz + az) for (cx, cz) in sol if (cx + ax, cz + az) not in sol]
                    if not all(0 <= bx < L_ and 0 <= bz < P and v[1][bz][bx] & 7 == pm.MUR for bx, bz in dos):
                        continue
                    if not all(0 <= fx < L_ and 0 <= fz < P and v[1][fz][fx] & 7 in (pm.VIDE, pm.FIN) for fx, fz in face):
                        continue
                    ok = True
                    for (cx, cy, cz) in cells:
                        X, Z = lx + cx, lz + cz
                        if not (0 <= X < L_ and 0 <= Z < P and 0 <= cy <= 3):
                            ok = False
                            break
                        code = v[cy + 1][Z][X]
                        if code & 7 != pm.VIDE:
                            ok = False
                            break
                        if cy == 0 and (code & (pk.F_PASSAGE | pk.F_PORTE) or not code & pk.F_PIECE or v[0][Z][X] & 7 != pm.MUR):
                            ok = False
                            break
                    if not ok:
                        continue
                    from scipy import ndimage
                    libre = np.isin(v[1] & 7, (pm.VIDE, pm.FIN)) & ((v[1] & pk.F_PIECE) > 0)
                    _l, n0 = ndimage.label(libre)
                    for (cx, cy, cz) in cells:
                        if cy == 0:
                            libre[lz + cz][lx + cx] = False
                    _l, n1 = ndimage.label(libre)
                    if n1 > n0:
                        continue
                    d = abs(lx - L_ // 2) + abs(lz - P // 2) if centre else 0
                    options.append((d, lx, lz, f))
        if not options:
            return None
        options.sort()
        _d, lx, lz, f = options[0]
        return lx, lz, f

    # ------------------------------------------------------------------ parts d'actifs
    def compensation(self, etape):
        """Commandes qui gardent le même nombre de conteneurs remplis par zone malgré les conteneurs AJOUTÉS (étape
        « ajouts ») : HeraliZ Loot range un conteneur posé dans la zone de son niveau la plus proche (64 blocs), sinon
        il crée une zone « ajouts/tN_x_z » ; la part d'actifs de la zone est baissée d'autant, et une zone créée par ces
        props reçoit la part 0 (props de décor, sans loot). -> [(commande, annulation, zone créée)]"""
        ajoutes = [c for c in self.changes if c["etape"] == etape and c["apres"].startswith("heraliz_loot:")
                   and self.cat[c["apres"]].loot]
        if not ajoutes:
            return []
        if not hasattr(self, "_zones"):
            zones = defaultdict(list)
            for (x, y, z), (zid, _lieu, nom) in self.a.zpos.items():
                zones[zid].append((x, y, z, _niv(nom)))
            for x, y, z, zid, nom in self.a.loot.get("places") or []:
                zones[zid].append((x, y, z, _niv(nom)))
            self._zones = {zid: lst for zid, lst in zones.items()}
            self._n0 = {zid: len(lst) for zid, lst in zones.items()}
            self._crees = set()
        niv_zone = {}
        for zid, lst in self._zones.items():
            c = Counter(n for _x, _y, _z, n in lst)
            niv_zone[zid] = c.most_common(1)[0][0] if c else 0
        touchees = Counter()
        for ch in sorted(ajoutes, key=lambda c: (c["x"] >> 6, c["z"] >> 6, c["x"], c["z"], c["y"])):
            niv = _niv(ch["apres"].split(":")[1])
            x, y, z = ch["x"], ch["y"], ch["z"]
            mieux, dm = None, 64 * 64
            for zid, lst in self._zones.items():
                if niv_zone.get(zid) != niv:
                    continue
                for (a, b, c, _n) in lst:
                    d = (a - x) ** 2 + (b - y) ** 2 + (c - z) ** 2
                    if d <= dm:
                        dm, mieux = d, zid
            if mieux is None:
                mieux = "ajouts/t%d_%d_%d" % (niv, x, z)
                self._zones[mieux] = []
                niv_zone[mieux] = niv
                self._crees.add(mieux)
            self._zones[mieux].append((x, y, z, niv))
            touchees[mieux] += 1
        out = []
        for zid, n in sorted(touchees.items()):
            if zid in self._crees:
                out.append(('heralizloot zone "%s" part 0' % zid, 'heralizloot zone "%s" part auto' % zid, True))
                continue
            avant = self.a.loot.get("parts", {}).get(zid)
            info = self.a.zinfos.get(zid) or {}
            p0 = avant if avant is not None else (info.get("actifs") if info.get("actifs") is not None else 0.2)
            n0 = self._n0.get(zid, 0)
            n1 = len(self._zones[zid])
            nouvelle = p0 * n0 / max(1, n1)
            defaire = ('heralizloot zone "%s" part %s' % (zid, _pct(avant))) if avant is not None else \
                ('heralizloot zone "%s" part auto' % zid)
            out.append(('heralizloot zone "%s" part %s' % (zid, _pct(nouvelle)), defaire, False))
        return out

    # ------------------------------------------------------------------ tout le plan
    def calculer(self, etapes):
        self.resume = {}
        self.avant_ajouts = None
        if "diversification" in etapes:
            n = self.etape_diversification()
            self.resume["diversification"] = n
            print("[plan] diversification %d (%s)" % (n, ", ".join("%s %d" % (m, self.motifs[m]) for m in MOTIFS)),
                  flush=True)
        self.avant_ajouts = Counter(self.compte)
        n = self.etape_ajouts()       # toujours calculée (pour le rapport) ; jouée seulement avec --ajouts
        if self.ajouts and "ajouts" in etapes:
            self.changes += self.ajouts_proposes
            self.resume["ajouts"] = n
            print("[plan] ajouts          %d" % n, flush=True)
        else:
            print("[plan] ajouts possibles %d (option --ajouts)" % len(self.ajouts_proposes), flush=True)
        for ch in self.changes:
            ch.pop("i", None)
        self.parts = {}
        if self.compenser:
            self.parts["ajouts"] = self.compensation("ajouts")
        return self.changes


# =================================================================================================== commandes
def etat_texte(i, etat):
    return pm.etat_texte(i, etat or {})


def commandes(ch):
    """(commande, commande d'annulation) d'un changement."""
    x, y, z = ch["x"], ch["y"], ch["z"]
    pos = "%d %d %d" % (x, y, z)
    avant = etat_texte(ch["avant"], ch["etat_avant"])
    apres = etat_texte(ch["apres"], ch["etat_apres"])
    conds = ["if block %s %s" % (pos, avant)]
    for c in ch.get("cases_libres") or []:
        conds.append("if block %d %d %d minecraft:air" % tuple(c))
    faire = "execute %s run setblock %s %s" % (" ".join(conds), pos, apres)
    defaire = "execute if block %s %s run setblock %s %s%s" % (pos, apres, pos, avant, ch.get("be") or "")
    return faire, defaire


def _niv(nom):
    m = re.match(r"[a-z]+(\d+)_", nom)
    return int(m.group(1)) if m else 0


def _pct(p):
    return ("%.2f" % (100.0 * p)).rstrip("0").rstrip(".")


def lots(changes, taille, forces, parts=None):
    """Lots par étape et par carré de 64 blocs : [(nom, étape, zone, commandes, annulations, chunks)]."""
    out = []
    par = defaultdict(list)
    for ch in changes:
        par[(ETAPES.index(ch["etape"]), ch["etape"], ch["x"] >> 6, ch["z"] >> 6)].append(ch)
    for (o, etape, gx, gz) in sorted(par):
        lst = sorted(par[(o, etape, gx, gz)], key=lambda c: (c["x"], c["z"], c["y"]))
        for i in range(0, len(lst), taille):
            morceau = lst[i:i + taille]
            xs = [c["x"] for c in morceau] + [cc[0] for c in morceau for cc in c.get("cases_libres") or []]
            zs = [c["z"] for c in morceau] + [cc[2] for c in morceau for cc in c.get("cases_libres") or []]
            zone = (min(xs) - 4, min(zs) - 4, max(xs) + 4, max(zs) + 4)
            cmds, ann = [], []
            for c in morceau:
                f, d = commandes(c)
                cmds.append(f)
                ann.append(d)
            out.append([etape, zone, cmds, ann])
    # parts d'actifs : zones qui existent déjà, part baissée AVANT la pose (jamais plus de conteneurs remplis
    # qu'avant) ; zones créées par la pose, part 0 juste APRÈS (elles n'existent pas avant)
    parts = parts or {}
    ordre = []
    for et in ETAPES:
        av = [(c, d) for c, d, cree in parts.get(et) or [] if not cree]
        ap = [(c, d) for c, d, cree in parts.get(et) or [] if cree]
        if av and any(l[0] == et for l in out):
            ordre.append([et + "_parts_avant", None, [c for c, _d in av], [d for _c, d in av]])
        ordre += [l for l in out if l[0] == et]
        if ap and any(l[0] == et for l in out):
            ordre.append([et + "_parts", None, [c for c, _d in ap], [d for _c, d in ap]])
    res = []
    for k, (etape, zone, cmds, ann) in enumerate(ordre, 1):
        nom = "%03d_%s" % (k, etape)
        res.append((nom, etape, zone, cmds, ann, _chunks(zone, forces)))
    return res


def _chunks(zone, forces):
    if zone is None:
        return [], []
    x0, z0, x1, z1 = zone
    tous = [(cx, cz) for cx in range(x0 >> 4, (x1 >> 4) + 1) for cz in range(z0 >> 4, (z1 >> 4) + 1)]
    return [c for c in tous if c not in forces], [c for c in tous if c in forces]


def ecrire_lot(chemin, nom, etape, zone, cmds, chunks, annulation=False):
    libres, deja = chunks
    L = ["# lot %s · étape %s · %d commande(s) · %s%s" % (
        nom, etape, len(cmds), ("zone %d %d -> %d %d" % zone) if zone else "sans bloc",
        " · ANNULATION" if annulation else ""),
        "# Chaque commande ne change un bloc que s'il est encore celui relevé dans la copie du monde."]
    if deja:
        L.append("# chunks déjà forcés sur le serveur (laissés tels quels) : " + " ".join("%d,%d" % c for c in deja))
    for cx, cz in libres:
        L.append("#avant forceload add %d %d" % (cx * 16, cz * 16))
    if libres:
        L.append("#attendre 3")
    L += cmds
    for cx, cz in libres:
        L.append("#apres forceload remove %d %d" % (cx * 16, cz * 16))
    Path(chemin).write_text("\n".join(L) + "\n", encoding="utf-8")


def main_plan(dossier, args):
    def opt(nom, defaut=None):
        return args[args.index(nom) + 1] if nom in args else defaut
    a = pk.charger(dossier)
    etapes = [e for e in (opt("--etapes") or ",".join(ETAPES)).split(",") if e in ETAPES]
    mx = opt("--max-changements")
    pl = Planif(a, int(opt("--graine", "1")), int(mx) if mx else None, "--ajouts" in args,
                compenser="--sans-compensation" not in args)
    avant = Counter(pl.compte)
    changes = pl.calculer(etapes)
    sortie = Path(opt("--sortie") or Path(dossier, "plan"))
    if sortie.exists():
        for f in list(sortie.glob("lots/*.txt")) + list(sortie.glob("annulation/*.txt")):
            f.unlink()
    (sortie / "lots").mkdir(parents=True, exist_ok=True)
    (sortie / "annulation").mkdir(parents=True, exist_ok=True)
    les_lots = lots(changes, int(opt("--lot", "40")), a.forces, pl.parts)
    for nom, etape, zone, cmds, ann, chunks in les_lots:
        ecrire_lot(sortie / "lots" / (nom + ".txt"), nom, etape, zone, cmds, chunks)
        ecrire_lot(sortie / "annulation" / (nom + ".txt"), nom, etape, zone, list(reversed(ann)), chunks, annulation=True)
    modeles = {}
    for q in pl.modeles:
        modeles[q.id.split(":")[1]] = {"palier": q.niveau, "gabarit": q.gab, "avant": avant[q.id],
                                       "apres": pl.avant_ajouts[q.id], "minimum": pl.quota(q),
                                       "part": round(pl.cible.get(q.id, 0), 1),
                                       "ajouts_possibles": sum(1 for c in pl.ajouts_proposes if c["apres"] == q.id)}
    json.dump({"consigne": "modèles HeraliZ Loot T1 à T6, tous posés, même palier", "changements": changes,
               "ajouts_proposes": pl.ajouts_proposes, "resume": pl.resume, "motifs": dict(pl.motifs),
               "modeles": modeles, "absents": getattr(pl, "raisons", {}),
               "lots": [(n, e, len(c)) for n, e, _z, c, _a, _ch in les_lots]},
              open(sortie / "plan.json", "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    md = rapport_plan(pl, avant, les_lots)
    (sortie / "plan.md").write_text(md, encoding="utf-8")
    print(md)


def rapport_plan(pl, avant, les_lots):
    apres = pl.avant_ajouts              # après la diversification seule (sans les ajouts)
    final = pl.compte                    # avec les ajouts, s'ils sont joués
    mods = pl.modeles
    av0 = sum(1 for q in mods if avant[q.id] > 0)
    ap0 = sum(1 for q in mods if apres[q.id] > 0)
    fi0 = sum(1 for q in mods if final[q.id] > 0)
    aj = Counter(c["apres"] for c in pl.ajouts_proposes)
    po0 = sum(1 for q in mods if apres[q.id] > 0 or aj[q.id])
    L = ["# Plan des conteneurs de loot : modèles HeraliZ Loot T1 à T6", ""]
    L.append("Consigne : chaque modèle T1 à T6 de HeraliZ Loot posé sur la carte, en remplaçant des conteneurs déjà "
             "posés par d'autres modèles du même palier (même gabarit, même orientation, pièce cohérente). T7 réservé "
             "aux événements : rien n'est posé. Ni meubles cfm, ni familles (nourriture, soins, munitions).")
    L.append("")
    n_div = pl.resume.get("diversification", 0)
    def pct(n):
        return ("%.1f" % (100.0 * n / max(1, len(mods)))).replace(".0", "").replace(".", ",")
    L.append("**Modèles T1 à T6 : %d au total. Posés avant le plan : %d (%s %%) ; après la diversification : %d (%s %%)%s.**" % (
        len(mods), av0, pct(av0), ap0, pct(ap0),
        (" ; avec les ajouts : %d (%s %%)" % (fi0, pct(fi0))) if "ajouts" in pl.resume else
        (" ; avec les %d ajouts proposés à part (option --ajouts, non joués) : %d (%s %%)" % (
            len(pl.ajouts_proposes), po0, pct(po0)) if pl.ajouts_proposes else "")))
    L.append("")
    L.append("%d changements en %d lots : diversification %d (%s)%s." % (
        sum(pl.resume.values()), len(les_lots), n_div, ", ".join("%s %d" % (m, pl.motifs[m]) for m in MOTIFS),
        (", ajouts %d" % pl.resume["ajouts"]) if "ajouts" in pl.resume else ""))
    tot = sum(avant[q.id] for q in mods)
    L.append("Conteneurs T1 à T6 sur la carte : %d (%d changent de modèle, soit %s %%). Gardés tels quels : %d "
             "conteneurs avec des données (inventaire)." % (tot, n_div, ("%.1f" % (100.0 * n_div / max(1, tot))).replace(".", ","),
                                                           sum(pl.avec_donnees.values())))
    L.append("")
    L += ["| palier | modèles | posés avant | après | conteneurs | le plus répété avant | après | 3 plus posés avant → après | minimum par modèle |",
          "|---|---|---|---|---|---|---|---|---|"]
    for n in PALIERS:
        prs = [q for q in mods if q.niveau == n]
        if not prs:
            continue
        top_av = max(prs, key=lambda q: avant[q.id])
        top_ap = max(prs, key=lambda q: apres[q.id])
        minis = sorted({pl.quota(q) for q in prs})
        tot_n = max(1, sum(avant[q.id] for q in prs))
        top3 = lambda c: sum(sorted((c[q.id] for q in prs), reverse=True)[:3]) * 100 // max(1, sum(c[q.id] for q in prs))  # noqa: E731
        L.append("| T%d | %d | %d | %d | %d | %s ×%d | %s ×%d | %d %% → %d %% | %s |" % (
            n, len(prs), sum(1 for q in prs if avant[q.id] > 0), sum(1 for q in prs if apres[q.id] > 0),
            tot_n, top_av.id.split(":")[1], avant[top_av.id], top_ap.id.split(":")[1],
            apres[top_ap.id], top3(avant), top3(apres),
            "-".join(str(m) for m in (minis[0], minis[-1])) if len(minis) > 1 else minis[0]))
    L.append("")
    sous = [q for q in mods if 0 < apres[q.id] < pl.quota(q)]
    if sous:
        L.append("Posés mais sous leur minimum (places cohérentes trop rares) : " +
                 ", ".join("`%s` ×%d / %d" % (q.id.split(":")[1], apres[q.id], pl.quota(q)) for q in sous))
        L.append("")
    reste = [q for q in mods if apres[q.id] == 0]
    if reste:
        L.append("### Modèles encore absents après la diversification (%d)" % len(reste))
        L.append("")
        for q in sorted(reste, key=lambda q: q.id):
            L.append("- `%s` (T%d, %s) : %s.%s" % (
                q.id.split(":")[1], q.niveau, q.gab, pl.raisons.get(q.id, "?"),
                (" Ajout possible ×%d sur une case libre (%s)." % (aj[q.id], "joué" if "ajouts" in pl.resume else
                                                                  "seulement avec l'option --ajouts"))
                if aj[q.id] else " Aucune case libre cohérente non plus : à poser à la main."))
        L.append("")
    if pl.non_corriges:
        L.append("Incohérences sans remplaçant du même palier (laissées) : " +
                 ", ".join("%s ×%d" % kv for kv in pl.non_corriges.most_common()))
        L.append("")
    raisons = Counter(ch["raison"] for ch in pl.changes if ch.get("motif") == "cohérence")
    if raisons:
        L.append("Cohérence : " + ", ".join("%s ×%d" % kv for kv in raisons.most_common()))
        L.append("")
    if "ajouts" in pl.resume:
        plus = Counter((ch.get("lieu") or "?", "T%d" % pl.cat[ch["apres"]].niveau) for ch in pl.changes
                       if ch["etape"] == "ajouts")
        if plus:
            L.append("Conteneurs AJOUTÉS par lieu et palier (part d'actifs de la zone baissée d'autant) : " +
                     ", ".join("%s %s +%d" % (l, t, n) for (l, t), n in sorted(plus.items())))
            L.append("")
    ex = Counter((ch["avant"].split(":")[-1], ch["apres"].split(":")[-1]) for ch in pl.changes if ch["etape"] == "diversification")
    L.append("Remplacements les plus fréquents : " + ", ".join("%s → %s ×%d" % (a, b, n) for (a, b), n in ex.most_common(12)))
    L.append("")
    return "\n".join(L) + "\n"


# =================================================================================================== rendus
COULEURS = {
    "cuisine": (230, 160, 60), "sdb": (90, 190, 220), "chambre": (190, 120, 200), "salon": (220, 110, 110),
    "entree": (160, 140, 110), "bureau": (120, 150, 230), "ecole": (120, 200, 160), "buanderie": (140, 210, 230),
    "cave": (150, 110, 80), "garage": (150, 150, 150), "grange": (200, 180, 90), "commerce": (240, 210, 90),
    "entrepot": (170, 160, 130), "police": (60, 90, 200), "secours": (230, 70, 50), "hopital": (240, 240, 240),
    "militaire": (110, 140, 70), "onu": (90, 150, 230), "banque": (230, 200, 40), "labo": (120, 230, 200),
    "chasse": (120, 100, 50), "cadavre": (150, 40, 40), "extérieur": (100, 160, 100), "stockage": (180, 170, 150),
}


def main_rendus(monde, dossier, args):
    from PIL import Image, ImageDraw, ImageFont

    def opt(nom, defaut=None):
        return args[args.index(nom) + 1] if nom in args else defaut
    a = pk.charger(dossier)
    plan = json.load(open(Path(dossier, "plan", "plan.json"), encoding="utf-8"))
    sortie = Path(opt("--sortie") or Path(dossier, "rendus"))
    sortie.mkdir(parents=True, exist_ok=True)
    cat = a.cat
    par_piece = defaultdict(list)
    for ch in plan["changements"]:
        if ch.get("piece"):
            par_piece[ch["piece"]].append(ch)
    if opt("--pieces"):
        choix = opt("--pieces").split(",")
    else:
        # une pièce avec un doublon corrigé (deux cuisinières, deux frigos...), une pièce où arrive un modèle absent
        # jusque-là, une pièce de maison diversifiée : assez petites pour rester lisibles
        def bon(pid, test, types=None):
            info = a.pieces.get(pid, {})
            return (not info.get("couloir") and 16 <= info.get("cases", 0) <= 160 and len(a.par_piece.get(pid, ())) >= 3
                    and any(test(c) for c in par_piece[pid]) and (types is None or info.get("type") in types))
        absents_avant = {"heraliz_loot:" + m for m, d in (plan.get("modeles") or {}).items() if not d.get("avant")}
        choix = []
        for test, types in ((lambda c: c["raison"].startswith("doublon"), None),
                            (lambda c: c.get("motif") == "couverture" and c["apres"] in absents_avant, None),
                            (lambda c: c.get("motif") == "équilibrage", ("cuisine", "chambre", "salon"))):
            cands = sorted((pid for pid in par_piece if pid not in choix and bon(pid, test, types)),
                           key=lambda pid: (-len(par_piece[pid]), pid))
            if cands:
                choix.append(cands[0])
    classeur = pm.Classeur({i for i in cat if i.split(":")[0] in ("heraliz_loot", "heralisz_utils", "heraliz_decors")},
                           {i for i, p in cat.items() if p.structure and p.mod == "cfm"})
    try:
        police = ImageFont.truetype("DejaVuSans.ttf", 11)
        police_t = ImageFont.truetype("DejaVuSans-Bold.ttf", 14)
    except OSError:
        police = police_t = ImageFont.load_default()
    faits = []
    for k, pid in enumerate(choix, 1):
        info = a.pieces[pid]
        y = info["y"]
        x0, z0, x1, z1 = info["x0"] - 2, info["z0"] - 2, info["x1"] + 2, info["z1"] + 2
        cls, noms = pm.lire_zone(pm.dossier_region(monde), x0, z0, x1, z1, y - 1, y + pk.PLAFOND + 1, classeur)
        couvert = (cls[2:] == pm.MUR).any(axis=0)
        props = [p for p in a.props if x0 <= p["x"] <= x1 and z0 <= p["z"] <= z1 and p["y"] == y]
        chs = {(c["x"], c["y"], c["z"]): c for c in plan["changements"]}
        S = 30
        Wd, Pd = (x1 - x0 + 1) * S, (z1 - z0 + 1) * S
        images = []
        legende = {}
        for apres in (False, True):
            im = Image.new("RGB", (Wd, Pd + 24), (24, 26, 30))
            dr = ImageDraw.Draw(im)
            for zz in range(z1 - z0 + 1):
                for xx in range(x1 - x0 + 1):
                    c0, c1 = cls[0, zz, xx], cls[1, zz, xx]
                    n1 = str(noms[1, zz, xx])
                    if c1 == pm.MUR:
                        col = (150, 190, 220) if ("glass" in n1 or "vitre" in n1 or "pane" in n1 or "vb_" in n1) else (45, 45, 50)
                    elif c1 == pm.PORTE:
                        col = (150, 90, 40)
                    elif c0 == pm.MUR and couvert[zz, xx]:
                        col = (205, 200, 190)
                    elif c0 == pm.MUR:
                        col = (96, 112, 88)
                    else:
                        col = (60, 80, 55)
                    dr.rectangle([xx * S, zz * S + 24, xx * S + S - 1, zz * S + S - 1 + 24], fill=col,
                                 outline=(175, 170, 160) if col == (205, 200, 190) else None)
            for p in props:
                c = chs.get((p["x"], p["y"], p["z"]))
                pid_ = c["apres"] if (apres and c) else p["id"]
                et = (c["etat_apres"] if (apres and c) else p["etat"]) or {}
                _dessiner(dr, cat, pid_, et, p["x"] - x0, p["z"] - z0, S, police, rouge=bool(apres and c), legende=legende)
            if apres:
                for c in plan["changements"]:
                    if c["apres"] and c["avant"] == "minecraft:air" and x0 <= c["x"] <= x1 and z0 <= c["z"] <= z1 and c["y"] == y:
                        _dessiner(dr, cat, c["apres"], c["etat_apres"], c["x"] - x0, c["z"] - z0, S, police, rouge=True, legende=legende)
            dr.text((6, 4), "APRÈS" if apres else "AVANT", fill=(255, 255, 255), font=police_t)
            images.append(im)
        lignes = ["Pièce « %s » vers %s (%d cases, étage y=%d) · vue de dessus" % (
            info.get("type") or "?", pk._coord(info), info.get("cases", 0), y)]
        lignes += ["%s = %s" % (abr, nom) for abr, nom in sorted(legende.items())]
        lignes.append("cadre rouge = prop changé ; trait = face avant ; point rouge = tache de sang (décor)")
        largeur = max(Wd * 2 + 20, 760)
        par_col = max(1, (largeur - 12) // 380)
        hl = 16 * (math.ceil(len(lignes) / par_col) + 3)
        tout = Image.new("RGB", (largeur, Pd + 24 + hl), (24, 26, 30))
        tout.paste(images[0], (0, 0))
        tout.paste(images[1], (Wd + 20, 0))
        dr = ImageDraw.Draw(tout)
        dr.text((6, Pd + 32), lignes[0], fill=(255, 255, 255), font=police)
        for j, l in enumerate(lignes[1:]):
            dr.text((6 + (j % par_col) * 380, Pd + 50 + (j // par_col) * 16), l, fill=(230, 230, 230), font=police)
        f = sortie / ("piece_%d_%s.png" % (k, (info.get("type") or "piece")))
        tout.save(f)
        faits.append(str(f))
        print("[rendu] %s" % f)
    return faits


_ABRS = {}


def _abr(nom):
    """Abréviation courte et unique d'un prop (pour les rendus)."""
    if nom in _ABRS:
        return _ABRS[nom]
    court = re.sub(r"^(t\d|soins\d|nourriture\d|munitions\d)_", "", nom)
    mots = [m for m in court.split("_") if m not in ("loot", "de", "a")]
    base = "".join(m[:2] for m in mots[:2]).upper()[:4]
    num = re.search(r"(\d+)$", court)
    if num and not base.endswith(num.group(1)):
        base = base[:3] + num.group(1)
    abr, k = base, 2
    while abr in _ABRS.values():
        abr = base[:3] + str(k)
        k += 1
    _ABRS[nom] = abr
    return abr


def _dessiner(dr, cat, pid, etat, lx, lz, S, police, rouge, legende):
    pr = cat.get(pid)
    f = (etat or {}).get("facing", "north")
    if pr is not None and pr.mod in ("heraliz_decors", "heralisz_utils"):
        X, Z = lx * S, lz * S + 24
        dr.ellipse([X + S // 2 - 3, Z + S // 2 - 3, X + S // 2 + 3, Z + S // 2 + 3], fill=(170, 30, 30))
        return
    if pr is None:
        cells = [(0, 0, 0)]
        col = (90, 90, 90)
        nom = pid.split(":")[1] + " (retiré)"
    else:
        cells = pr.cellules_monde(f) if pr.mod == "heraliz_loot" else [(0, 0, 0)]
        col = COULEURS.get(pr.categorie, (180, 170, 150))
        nom = re.sub(r"^\[[^\]]*\] ", "", pr.nom)
        if pr.decrit and pr.mod == "heraliz_loot":
            nom = "%s (%s)" % (pr.decrit, nom)
        elif pr.categorie == "cadavre" and "zombie" not in pr.id:
            nom = "Cadavre (%s)" % nom
        nom = "%s [%s]" % (nom, ("T%d" % pr.niveau) if pr.id.split(":")[1].startswith("t") else pr.id.split(":")[1].split("_")[0])
    for (cx, cy, cz) in cells:
        if cy != 0:
            continue
        X, Z = (lx + cx) * S, (lz + cz) * S + 24
        dr.rectangle([X + 2, Z + 2, X + S - 3, Z + S - 3], fill=col, outline=(255, 40, 40) if rouge else (20, 20, 20), width=3 if rouge else 1)
    abr = _abr(pid.split(":")[1])
    legende[abr] = nom
    X, Z = lx * S, lz * S + 24
    dr.text((X + 3, Z + 9), abr, fill=(0, 0, 0), font=police)
    if pr is not None and f in DIRS:
        ax, az = DIRS[pr.avant(f)]
        cx_, cz_ = X + S // 2, Z + S // 2
        dr.line([cx_, cz_, cx_ + ax * (S // 2 - 2), cz_ + az * (S // 2 - 2)], fill=(20, 20, 20), width=2)
