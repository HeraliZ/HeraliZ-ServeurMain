"""Plan de diversification des props de la carte et rendus avant / après (appelés par outils/props_carte.py).

Rien n'est écrit dans le monde : le plan produit des lots de commandes que le serveur jouera lui-même (workflow
« Props de la carte »), chacune conditionnelle (« le bloc est-il encore celui relevé ? ») et annulable.

Étapes du plan (chaque changement porte son étape ; le workflow choisit lesquelles appliquer) :
  restauration  anciens meubles cfm retirés du jar (devenus de l'air au chargement) : le conteneur de HeraliZ Loot qui
                reprend le même modèle est reposé à leur place, s'il y a toujours de l'air ;
  correction    incohérences : doublons dans une pièce (2 frigos, 2 cuisinières...), prop d'extérieur dans une pièce
                fermée, prop d'intérieur à ciel ouvert, prop qui ne va pas avec sa pièce, même conteneur répété ;
  couverture    chaque conteneur (et chaque palier) au moins N fois sur la carte, à des places cohérentes ;
  equilibrage   les conteneurs trop répétés (range-chaussures, poubelle en tôle...) cèdent une partie de leurs places
                aux autres modèles de leur palier ;
  cfm           variantes des meubles cfm déjà posés (chaises, palettes, poubelles...) ;
  t7            points d'apparition des coffres T7 (un par modèle) dans des lieux T6 ;
  ajouts        (option --ajouts) props posés sur des cases vides : salle de bain dans les petites pièces vides des
                maisons, meubles cfm jamais posés dans les pièces de leur type.

Règles de chaque remplacement : même palier de loot (le loot d'une zone ne change pas de niveau), même gabarit (un
meuble par un meuble, un objet par un objet, un cadavre par un cadavre), même orientation, pièce ou extérieur
compatibles (outils/props_catalogue.py), groupes uniques respectés (une cuisinière, un frigo... par pièce), place
libre pour les cases en plus (de l'air, un sol dessous, dans la même pièce, ni devant une porte, ni dans un passage
d'un bloc, ni devant un autre meuble, sans couper la pièce en deux).
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
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
YAW = {"south": 0, "west": 90, "north": 180, "east": -90}
# minimum de places sur la carte pour chaque modèle (s'il a des places cohérentes)
QUOTA = {1: 6, 2: 6, 3: 4, 4: 4, 5: 3, 6: 2}
QUOTA_FAMILLE, QUOTA_CADAVRE, QUOTA_CFM = 3, 3, 2
SEUIL = 2.0        # un modèle posé plus de SEUIL fois sa part de son palier est « trop répété »
# lieux (dossiers des zones de loot) -> extérieurs
LIEU_EXT = {"aerodrome": "base", "silo": "base", "arctique": "base", "radio": "base", "bunker": "base", "prison": "base",
            "petrole": "chantier", "port": "quai rue", "jetee": "quai", "campagne": "ferme route jardin",
            "routes": "route", "gaz": "chantier rue", "faille": "camp route", "halte_aerodrome": "route base"}
ETAPES = ["restauration", "correction", "couverture", "equilibrage", "cfm", "t7", "ajouts"]


def cle_palier(pr):
    m = re.match(r"([a-z]+)(\d)_", pr.id.split(":", 1)[1])
    return (m.group(1), int(m.group(2))) if m else ("?", 0)


class Emplacement:
    """Un prop remplaçable de la carte."""
    __slots__ = ("p", "pr", "facing", "cells", "env", "rtype", "rsur", "piece", "ctx", "cle", "gab", "porteur", "v",
                 "change", "lieu")

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
        self.non_corriges = Counter()
        self.max = max_changements
        # cases devant la face avant des meubles (on n'y pose rien)
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
        """Extérieurs autour de chaque prop du dehors : lieu + votes des props d'extérieur voisins (12 blocs)."""
        grille = defaultdict(list)
        for p in self.a.props:
            pr = self.cat.get(p["id"])
            if pr and pr.ext and not p["piece"] and pr.mod == "heraliz_loot":
                grille[(p["x"] >> 4, p["z"] >> 4)].append((p, pr))
        self.ctx_de = {}
        for p in self.a.props:
            if p["piece"] and self.a.env(p) == "int":
                continue
            votes = Counter()
            for gx in range((p["x"] >> 4) - 1, (p["x"] >> 4) + 2):
                for gz in range((p["z"] >> 4) - 1, (p["z"] >> 4) + 2):
                    for q, pr in grille.get((gx, gz), ()):
                        if (q["x"] - p["x"]) ** 2 + (q["z"] - p["z"]) ** 2 <= 144 and len(pr.ext) <= 4:
                            for e in pr.ext:
                                votes[e] += 1.0 / len(pr.ext)
            top = votes.most_common(3)
            ctx = {e for e, n in top if n >= 0.5 * top[0][1]} if top else set()
            lieu = p.get("lieu") or ""
            ctx |= set(LIEU_EXT.get(lieu, "rue jardin" if lieu else "route").split())
            self.ctx_de[p["i"]] = ctx

    def _emplacements(self):
        self.emps = []
        self.restaurations = []
        cat = self.cat
        for p in self.a.props:
            pr = cat.get(p["id"])
            if pr is None:
                if p["id"].startswith("cfm:"):
                    eq = pc.equivalent_loot(p["id"].split(":", 1)[1], cat)
                    if eq:
                        self.restaurations.append((p, cat[eq]))
                continue
            if p["be"]:
                continue
            if pr.mod == "heraliz_loot":
                if not pr.loot or pr.gab not in ("meuble", "objet", "cadavre"):
                    continue
            elif pr.mod == "cfm":
                if not pr.dayz or pr.structure:
                    continue
            else:
                continue
            f = (p["etat"] or {}).get("facing")
            if f not in DIRS:
                continue
            v = pk.voisinage(p)
            dessus = v[2][R][R] & 7
            cells = pr.cellules_monde(f)
            porteur = dessus == pm.PROP and (0, 1, 0) not in cells
            info = self.a.pieces.get(p["piece"]) if p["piece"] else None
            e = Emplacement(p=p, pr=pr, facing=f, cells=cells, env=self.a.env(p), piece=p["piece"],
                            rtype=info.get("type") if info else None, rsur=info.get("sur", 0) if info else 0,
                            ctx=self.ctx_de.get(p["i"], set()), cle=cle_palier(pr) if pr.mod == "heraliz_loot" else ("cfm", pr.famille),
                            gab=pr.gab, porteur=porteur, v=v, change=None, lieu=p.get("lieu"))
            self.emps.append(e)
        self.par_i = {e.p["i"]: e for e in self.emps}

    def _pools(self):
        """Modèles interchangeables : même palier et même gabarit (loot) ; même famille de variantes (cfm)."""
        self.pools = defaultdict(list)
        for i, pr in self.cat.items():
            if pr.mod == "heraliz_loot" and pr.loot and pr.gab in ("meuble", "objet", "cadavre") and not pr.be:
                self.pools[(cle_palier(pr), pr.gab)].append(pr)
            elif pr.mod == "cfm" and pr.dayz and not pr.structure:
                self.pools[(("cfm", pr.famille), "decor")].append(pr)
        # part de chaque modèle dans son palier : places du palier / modèles du palier
        n_pool = Counter((e.cle, e.gab) for e in self.emps)
        self.cible = {}
        for k, prs in self.pools.items():
            poids = sum(pc.frequence(pr.id.split(":")[1]) for pr in prs)
            for pr in prs:
                part = n_pool.get(k, 0) * pc.frequence(pr.id.split(":")[1]) / max(1e-6, poids)
                self.cible[pr.id] = max(self.quota(pr), part)

    def quota(self, pr):
        if pr.mod == "cfm":
            return QUOTA_CFM
        if pr.gab == "cadavre":
            return QUOTA_CADAVRE
        fam, niv = cle_palier(pr)
        return QUOTA.get(niv, 2) if fam == "t" else QUOTA_FAMILLE

    # ------------------------------------------------------------------ règles
    def compatible(self, e, q, raison=None):
        """q peut-il prendre la place de e ? (pièce, extérieur, groupes, place)"""
        if q.id == e.pr.id:
            return False
        if e.pr.mod == "heraliz_loot":
            if e.env == "int" or (e.env == "abri" and e.piece):
                if not q.pieces:
                    return False
                sur = e.rtype and e.rsur >= 0.35 and len(self.a.par_piece.get(e.piece, ())) >= 2
                if sur:
                    if e.rtype not in q.pieces:
                        return False
                elif not (q.pieces & (e.pr.pieces or set())):
                    return False
                # un prop propre à une ou deux sortes de pièces (matelas, lavabo...) : seulement dans une pièce
                # dont le type est sûr
                if len(q.pieces) <= 2 and not (e.rtype in q.pieces and e.rsur >= 0.5):
                    return False
            else:
                if not (q.ext & e.ctx):
                    return False
        if e.piece:
            ids = self.r_ids[e.piece]
            if q.groupe:
                n = self.r_grp[e.piece][q.groupe] - (1 if e.pr.groupe == q.groupe else 0)
                maxi = pc.maxi_groupe(q.groupe, self.a.pieces.get(e.piece, {}))
                if n >= maxi:
                    return False
            if ids[q.id] >= (1 if q.gab == "meuble" else 2):
                return False
        if e.porteur and (q.gab != "meuble" or (q.haut or 16) < 14):
            return False
        if self.garder_groupes and e.piece and e.pr.groupe in pc.ESSENTIELS and q.groupe != e.pr.groupe \
                and self.r_grp[e.piece][e.pr.groupe] <= 1:
            return False
        return self.place(e, q)

    def place(self, e, q):
        v = e.v
        x, y, z = e.p["x"], e.p["y"], e.p["z"]
        old = set(e.cells)
        new = q.cellules_monde(e.facing)
        extra = [c for c in new if c not in old]
        bas = []
        for (dx, dy, dz) in extra:
            if abs(dx) > R or abs(dz) > R or not (0 <= dy <= 3):
                return False
            code = v[dy + 1][dz + R][dx + R]
            if code & 7 != pm.VIDE:
                return False
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
        if q.mod == "cfm":
            vis_old = set(e.pr.visuel_monde(e.facing))
            for (dx, dy, dz) in q.visuel_monde(e.facing):
                if (dx, dy, dz) in vis_old or (dx, dy, dz) == (0, 0, 0):
                    continue
                if abs(dx) > R or abs(dz) > R or not (-1 <= dy <= 3):
                    return False
                if v[dy + 1][dz + R][dx + R] & 7 not in (pm.VIDE, pm.FIN):
                    return False
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
        out = []
        for q in self.pools.get((e.cle, e.gab), ()):
            if filtre and not filtre(q):
                continue
            if self.compatible(e, q):
                out.append(q)
        return out

    def score(self, e, q):
        s = (self.compte[q.id] + 1) / max(1.0, self.cible.get(q.id, 1.0))
        if e.rtype and e.rtype in q.pieces and len(q.pieces) <= 3:
            s -= 0.15
        if len(q.cellules) != len(e.pr.cellules):
            s += 0.2
        return s + self.rnd.random() * 0.05

    def appliquer(self, e, q, etape, raison):
        if self.max is not None and len(self.changes) >= self.max:
            return False
        old = e.pr
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
        # cases en plus : elles deviennent occupées (et le devant du nouveau meuble est réservé)
        x, y, z = e.p["x"], e.p["y"], e.p["z"]
        old_cells = set(e.cells)
        extra = [c for c in q.cellules_monde(e.facing) if c not in old_cells]
        for c in extra:
            self.occupe[(x + c[0], y + c[1], z + c[2])] = e.p["i"]
            if 0 <= c[1] + 1 <= 4 and abs(c[0]) <= R and abs(c[2]) <= R:
                e.v[c[1] + 1][c[2] + R][c[0] + R] = (e.v[c[1] + 1][c[2] + R][c[0] + R] & 0xF8) | pm.PROP
        self.changes.append({"etape": etape, "raison": raison, "x": x, "y": y, "z": z, "avant": old.id,
                             "etat_avant": dict(e.p["etat"] or {}), "be": e.p["be"], "apres": q.id,
                             "etat_apres": {"facing": e.facing} if q.mod == "heraliz_loot" or "facing" in (e.p["etat"] or {}) else {},
                             "cases_libres": [[x + c[0], y + c[1], z + c[2]] for c in extra],
                             "cases_avant": [[x + c[0], y + c[1], z + c[2]] for c in e.cells if c != (0, 0, 0)],
                             "piece": e.piece, "lieu": e.lieu})
        if q.mod == "cfm":
            # garder les autres propriétés du bloc (waterlogged...) si le nouveau les a aussi
            et = dict(e.p["etat"] or {})
            et["facing"] = e.facing
            self.changes[-1]["etat_apres"] = et
        e.change = q.id
        e.pr = q
        e.cells = q.cellules_monde(e.facing)
        return True

    def meilleur(self, e, etape, raison, filtre=None):
        cands = self.candidats(e, filtre)
        if not cands:
            return False
        q = min(cands, key=lambda q: self.score(e, q))
        return self.appliquer(e, q, etape, raison)

    # ------------------------------------------------------------------ étapes
    def etape_restauration(self):
        n = 0
        for p, q in self.restaurations:
            ancien = p["id"].split(":", 1)[1]
            f_old = (p["etat"] or {}).get("facing", "north")
            # cfm : facing=north -> rotation 0, east 90, south 180, west 270 ; même rotation dans HeraliZ Loot
            y_old = {"north": 0, "east": 90, "south": 180, "west": 270}.get(f_old, 0)
            f_new = next((f for f, yy in (q.rot or {}).items() if yy == y_old), OPP.get(f_old, "north"))
            x, y, z = p["x"], p["y"], p["z"]
            self.changes.append({"etape": "restauration", "raison": "cfm:%s retiré du jar (même modèle)" % ancien,
                                 "x": x, "y": y, "z": z, "avant": "minecraft:air", "etat_avant": {}, "be": None,
                                 "apres": q.id, "etat_apres": {"facing": f_new}, "cases_libres": [], "cases_avant": [],
                                 "piece": p["piece"], "lieu": p.get("lieu"), "ancien": p["id"]})
            self.compte[q.id] += 1
            n += 1
        return n

    def etape_correction(self):
        self.garder_groupes = False
        try:
            return self._correction()
        finally:
            self.garder_groupes = True

    def _correction(self):
        inc = self.a.inc
        n = 0
        a_corriger = []
        for pid, g, lst in inc["doublon"]:
            garde = pc.maxi_groupe(g, self.a.pieces.get(pid, {}))
            for p in sorted(lst, key=lambda p: (p["x"], p["z"]))[garde:]:
                a_corriger.append((p, "doublon : %s en trop dans la pièce" % g))
        for pid, i, nb in inc["repetition"]:
            ps = [p for p in self.a.par_piece[pid] if p["id"] == i][1:]
            for p in ps:
                a_corriger.append((p, "même conteneur répété dans une petite pièce"))
        for p in inc["exterieur_dedans"]:
            a_corriger.append((p, "prop d'extérieur dans une pièce fermée"))
        for p in inc["interieur_dehors"]:
            a_corriger.append((p, "prop d'intérieur à ciel ouvert"))
        for p in inc["mauvaise_piece"]:
            a_corriger.append((p, "ne va pas avec la pièce"))
        vus = set()
        for p, raison in a_corriger:
            e = self.par_i.get(p["i"])
            if e is None or p["i"] in vus or e.change:
                if e is None:
                    self.non_corriges[raison] += 1
                continue
            vus.add(p["i"])
            if raison.startswith("prop d'extérieur"):
                # forcer un prop d'intérieur
                ok = self.meilleur(e, "correction", raison, lambda q: bool(q.pieces))
            elif raison.startswith("prop d'intérieur"):
                e.env = "ext"
                ok = self.meilleur(e, "correction", raison, lambda q: bool(q.ext))
            else:
                ok = self.meilleur(e, "correction", raison)
            if ok:
                n += 1
            else:
                self.non_corriges[raison] += 1
        return n

    def etape_couverture(self):
        n = 0
        manque = []
        for k, prs in self.pools.items():
            if k[1] == "decor":
                continue
            for q in prs:
                if self.compte[q.id] < self.quota(q):
                    manque.append(q)
        self.rnd.shuffle(manque)
        par_cle = defaultdict(list)
        for e in self.emps:
            if not e.change and e.gab != "decor":
                par_cle[(e.cle[1], e.gab)].append(e)
                if e.gab == "cadavre":
                    par_cle[("*", "cadavre")].append(e)
        self.sans_place = []
        # les modèles qui ont le moins de places possibles d'abord
        essais = []
        for q in manque:
            cle = (cle_palier(q)[1], q.gab)
            if q.gab == "cadavre" and not any(x.pr.niveau == q.niveau for x in par_cle[cle]):
                cle = ("*", "cadavre")
            places = [e for e in par_cle[cle] if not e.change
                      and self.compte[e.pr.id] > self.quota(e.pr) and self.compatible(e, q)]
            essais.append((len(places), q, places))
        essais.sort(key=lambda t: t[0])
        for _n, q, places in essais:
            besoin = self.quota(q) - self.compte[q.id]
            if not places:
                self.sans_place.append(q.id)
                continue
            self.rnd.shuffle(places)
            places.sort(key=lambda e: (0 if e.rtype in q.pieces and e.rsur >= 0.5 else 1, self.r_chg[e.piece] if e.piece else 0))
            # étaler sur plusieurs lieux : au plus 2 par lieu tant que d'autres lieux sont possibles
            par_lieu = Counter()
            for e in places:
                if besoin <= 0:
                    break
                if e.change or not self.compatible(e, q) or self.compte[e.pr.id] <= self.quota(e.pr):
                    continue
                if par_lieu[e.lieu] >= 2 and len({x.lieu for x in places}) > len(par_lieu):
                    continue
                if self.appliquer(e, q, "couverture", "%s jamais ou trop peu posé" % q.id.split(":")[1]):
                    par_lieu[e.lieu] += 1
                    besoin -= 1
                    n += 1
            if besoin > 0 and self.compte[q.id] == 0:
                self.sans_place.append(q.id)
        return n

    def etape_equilibrage(self):
        n = 0
        ordre = [e for e in self.emps if not e.change and e.gab != "decor"]
        self.rnd.shuffle(ordre)
        for e in ordre:
            p = e.pr
            if self.compte[p.id] <= SEUIL * self.cible.get(p.id, 1):
                continue
            if e.piece and self.r_chg[e.piece] >= max(1, math.ceil(len(self.a.par_piece.get(e.piece, ())) / 2)):
                continue
            if self.meilleur(e, "equilibrage", "%s trop répété" % p.id.split(":")[1],
                             lambda q: self.compte[q.id] < self.cible.get(q.id, 1)):
                n += 1
        return n

    def etape_cfm(self):
        n = 0
        ordre = [e for e in self.emps if not e.change and e.gab == "decor"]
        self.rnd.shuffle(ordre)
        for e in ordre:
            prs = self.pools.get((e.cle, e.gab), ())
            if len(prs) < 2:
                continue
            moy = sum(self.compte[q.id] for q in prs) / len(prs)
            if self.compte[e.pr.id] <= max(1.5 * moy, QUOTA_CFM):
                continue
            if self.meilleur(e, "cfm", "variante de %s" % e.pr.famille, lambda q: self.compte[q.id] < moy):
                n += 1
        return n

    def etape_t7(self):
        """Un point d'apparition par modèle de coffre T7, dans des pièces (ou cours) des lieux T6."""
        self.points = []
        types = [i for i in self.cat if i.startswith("heraliz_loot:t7_")]
        voulu = {"t7_coffre_fort": ("banque", "int"), "t7_coffre_armes": ("militaire", "int"),
                 "t7_largage": ("base", "ext"), "t7_mallette": ("labo", "int"), "t7_reliquaire": ("militaire", "int"),
                 "t7_capsule": ("labo", "int"), "t7_conteneur_heraliz": ("militaire", "int")}
        existants = {pt.get("type") for pt in self.a.loot.get("points") or []}
        noms_pris = {pt.get("nom") for pt in self.a.loot.get("points") or []}
        types = [t for t in types if t.split(":")[1] not in existants]
        hotes = [p for p in self.a.props if self.cat.get(p["id"]) and self.cat[p["id"]].niveau >= 5
                 and self.cat[p["id"]].mod == "heraliz_loot"]
        self.rnd.shuffle(hotes)
        lieux_pris = Counter()
        for t in sorted(types):
            court = t.split(":")[1]
            ou, env = voulu.get(court, ("militaire", "int"))
            q = self.cat[t]
            meilleur = None
            for p in sorted(hotes, key=lambda p: (lieux_pris[p.get("lieu")], -self.cat[p["id"]].niveau)):
                pr = self.cat[p["id"]]
                env_p = self.a.env(p)
                if env == "ext" and not (env_p == "ext" and p["ciel"]):
                    continue
                if env == "int" and env_p != "int":
                    continue
                info = self.a.pieces.get(p["piece"], {}) if p["piece"] else {}
                if env == "int" and ou not in pr.pieces and info.get("type") != ou and ou != "militaire":
                    continue
                spot = self._place_libre(p, q)
                if spot:
                    meilleur = (p, spot)
                    break
            if meilleur is None:
                continue
            p, (dx, dz, f) = meilleur
            lieux_pris[p.get("lieu")] += 1
            nom = "heraliz_%s_%d" % (court[3:], len(self.points) + 1)
            while nom in noms_pris:
                nom += "b"
            self.points.append({"etape": "t7", "nom": nom, "type": court, "x": p["x"] + dx, "y": p["y"], "z": p["z"] + dz,
                                "facing": f, "lieu": p.get("lieu"), "pres_de": p["id"]})
        return len(self.points)

    def _place_libre(self, p, q):
        """(dx, dz, facing) d'une place libre près du prop p pour le coffre q (toutes ses cases), ou None."""
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
                        if code & 7 != pm.VIDE or (cy == 0 and (code & (pk.F_PASSAGE | pk.F_PORTE) or v[0][Z + R][X + R] & 7 != pm.MUR)):
                            ok = False
                            break
                        if (p["x"] + X, p["y"] + cy, p["z"] + Z) in self.devant or (p["x"] + X, p["y"] + cy, p["z"] + Z) in self.occupe:
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

    def etape_ajouts(self):
        """Salles de bain dans les petites pièces vides des maisons, meubles cfm jamais posés dans les pièces de leur type."""
        self.ajouts_faits = []
        maisons = set()
        for pid, ps in self.a.par_piece.items():
            info = self.a.pieces.get(pid, {})
            if info.get("type") in ("cuisine", "chambre", "salon") and info.get("batiment"):
                maisons.add(info["batiment"])
        sdb = [self.cat[i] for i in ("heraliz_loot:t1_meuble_vasque", "heraliz_loot:t1_panier_linge",
                                     "heraliz_loot:t1_loot_civil_19", "heraliz_loot:t1_lave_linge") if i in self.cat]
        vides = [(pid, info) for pid, info in self.a.pieces.items() if info.get("vide") and 4 <= info["cases"] <= 12
                 and info.get("batiment") in maisons and info.get("plan")]
        lieu_bat = defaultdict(Counter)
        for p in self.a.props:
            if p.get("batiment") and p.get("lieu"):
                lieu_bat[p["batiment"]][p["lieu"]] += 1
        self.rnd.shuffle(vides)
        par_bat = Counter()
        for pid, info in vides:
            if par_bat[info["batiment"]] >= 1:
                continue
            plan = info["plan"]
            v = np.frombuffer(__import__("zlib").decompress(__import__("base64").b64decode(plan["v"])),
                              dtype=np.uint8).reshape(5, plan["p"], plan["l"])
            q = min(sdb, key=lambda q: self.compte[q.id] + self.rnd.random())
            spot = self._place_mur(v, q)
            if spot is None:
                continue
            lx, lz, f = spot
            x, z = plan["x"] + lx, plan["z"] + lz
            y = info["y"]
            if (x, y, z) in self.occupe:
                continue
            cells = q.cellules_monde(f)
            self.ajouts_faits.append({"etape": "ajouts", "raison": "salle de bain (petite pièce vide d'une maison)",
                                      "x": x, "y": y, "z": z, "avant": "minecraft:air", "etat_avant": {}, "be": None,
                                      "apres": q.id, "etat_apres": {"facing": f},
                                      "cases_libres": [[x + c[0], y + c[1], z + c[2]] for c in cells if c != (0, 0, 0)],
                                      "cases_avant": [], "piece": pid,
                                      "lieu": (lieu_bat[info["batiment"]].most_common(1) or [(None, 0)])[0][0]})
            for c in cells:
                self.occupe[(x + c[0], y + c[1], z + c[2])] = -1
            self.compte[q.id] += 1
            par_bat[info["batiment"]] += 1
        # conteneurs de loot encore absents et meubles cfm jamais posés : dans une pièce de leur type (sûre), case libre
        # le long d'un mur, hors passage et porte, sans couper la pièce
        loot_absents = [pr for k, prs in self.pools.items() if k[1] != "decor" for pr in prs
                        if self.compte[pr.id] == 0 and pr.pieces]
        jamais = loot_absents + [pr for pr in self.cat.values() if pr.mod == "cfm" and pr.dayz and not pr.structure
                                 and self.compte[pr.id] == 0 and pr.pieces and len(pr.pieces) <= 4]
        self.rnd.shuffle(jamais)
        pieces_par_type = defaultdict(list)
        for e in self.emps:
            if e.piece and e.env == "int" and e.rtype and e.rsur >= 0.5:
                pieces_par_type[e.rtype].append(e)
        deja = Counter()
        for q in jamais:
            hotes = [e for t in q.pieces for e in pieces_par_type.get(t, ())]
            self.rnd.shuffle(hotes)
            voulu = QUOTA_CFM if q.mod == "cfm" else min(2, self.quota(q))
            for e in hotes:
                if self.compte[q.id] >= voulu:
                    break
                if q.mod == "heraliz_loot" and (e.pr.mod != "heraliz_loot" or e.pr.niveau != q.niveau and q.gab != "cadavre"):
                    continue    # un conteneur ajouté va près des conteneurs de son palier
                if deja[e.piece] >= 1:
                    continue
                spot = self._place_mur(e.v, q, centre=True)
                if spot is None:
                    continue
                lx, lz, f = spot
                x, y, z = e.p["x"] + lx - R, e.p["y"], e.p["z"] + lz - R
                if (x, y, z) in self.occupe:
                    continue
                cells = q.cellules_monde(f) if q.mod == "heraliz_loot" else [(0, 0, 0)]
                if any((x + c[0], y + c[1], z + c[2]) in self.occupe or (x + c[0], y, z + c[2]) in self.devant
                       for c in cells):
                    continue
                self.ajouts_faits.append({"etape": "ajouts", "raison": "%s jamais posé (%s)" % (
                    "meuble cfm" if q.mod == "cfm" else "conteneur", e.rtype),
                                          "x": x, "y": y, "z": z, "avant": "minecraft:air", "etat_avant": {}, "be": None,
                                          "apres": q.id, "etat_apres": {"facing": f},
                                          "cases_libres": [[x + c[0], y + c[1], z + c[2]] for c in cells if c != (0, 0, 0)],
                                          "cases_avant": [], "piece": e.piece, "lieu": e.lieu})
                for c in cells:
                    self.occupe[(x + c[0], y + c[1], z + c[2])] = -1
                    if c[1] == 0 and 0 <= lz + c[2] < e.v.shape[1] and 0 <= lx + c[0] < e.v.shape[2]:
                        e.v[1][lz + c[2]][lx + c[0]] = (e.v[1][lz + c[2]][lx + c[0]] & 0xF8) | pm.PROP
                self.compte[q.id] += 1
                deja[e.piece] += 1
        return len(self.ajouts_faits)

    def _place_mur(self, v, q, centre=False):
        """(lx, lz, facing) : case du plan v (couche 1 = étage du prop) libre, sol dessous, dans la pièce, dos à un
        mur et face à une case libre, hors passage et porte ; toutes les cases de q libres."""
        P, L_ = v.shape[1], v.shape[2]
        options = []
        for lz in range(1, P - 1):
            for lx in range(1, L_ - 1):
                for f in ("north", "south", "east", "west"):
                    fx, fz = DIRS[f]
                    if q.mod == "heraliz_loot":
                        avant = f
                    else:
                        avant = q.avant(f)
                    ax, az = DIRS[avant]
                    bx, bz = lx - ax, lz - az
                    if not (0 <= bx < L_ and 0 <= bz < P) or v[1][bz][bx] & 7 != pm.MUR:
                        continue
                    ok = True
                    cells = q.cellules_monde(f) if q.mod == "heraliz_loot" else [(0, 0, 0)]
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
                    if q.mod == "cfm":
                        for (cx, cy, cz) in q.visuel_monde(f):
                            X, Z = lx + cx, lz + cz
                            if not (0 <= X < L_ and 0 <= Z < P and -1 <= cy <= 3) or v[cy + 1][Z][X] & 7 not in (pm.VIDE, pm.FIN):
                                if (cx, cy, cz) != (0, 0, 0):
                                    ok = False
                                    break
                        if not ok:
                            continue
                    dx_, dz_ = lx + ax, lz + az
                    if not (0 <= dx_ < L_ and 0 <= dz_ < P) or v[1][dz_][dx_] & 7 not in (pm.VIDE, pm.FIN):
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
        """Commandes qui gardent le même nombre de conteneurs remplis par zone malgré les conteneurs AJOUTÉS par une
        étape (restauration, ajouts) : HeraliZ Loot range un conteneur posé dans la zone de son niveau la plus proche
        (64 blocs), sinon il crée une zone « ajouts/tN_x_z » ; la part d'actifs de la zone est baissée d'autant, et une
        zone créée par ces props reçoit la part 0 (props de décor, sans loot). -> [(commande, annulation)]"""
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
        for et in ETAPES:
            if et not in etapes:
                continue
            if et == "ajouts" and not self.ajouts:
                continue
            n = getattr(self, "etape_" + et)()
            self.resume[et] = n
            print("[plan] %-12s %d" % (et, n), flush=True)
        if "ajouts" in self.resume:
            self.changes += self.ajouts_faits
        self.parts = {}
        if self.compenser:
            for et in ("restauration", "ajouts"):
                self.parts[et] = self.compensation(et)
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


def lots(changes, points, taille, forces, parts=None):
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
    # parts d'actifs : juste après les lots de blocs de leur étape
    parts = parts or {}
    ordre = []
    for et in ETAPES:
        # zones qui existent déjà : part baissée AVANT la pose (jamais plus de conteneurs remplis qu'avant) ;
        # zones créées par la pose : part 0 juste APRÈS (elles n'existent pas avant)
        av = [(c, d) for c, d, cree in parts.get(et) or [] if not cree]
        ap = [(c, d) for c, d, cree in parts.get(et) or [] if cree]
        if av:
            ordre.append([et + "_parts_avant", None, [c for c, _d in av], [d for _c, d in av]])
        ordre += [l for l in out if l[0] == et]
        if ap:
            ordre.append([et + "_parts", None, [c for c, _d in ap], [d for _c, d in ap]])
    out = ordre
    if points:
        cmds = ["execute positioned %d.5 %d %d.5 rotated %d 0 run heralizloot point ajouter %s %s auto" % (
            p["x"], p["y"], p["z"], YAW[p["facing"]], p["nom"], p["type"]) for p in points]
        out.append(["t7", None, cmds, ["heralizloot point retirer %s" % p["nom"] for p in points]])
    res = []
    for k, (etape, zone, cmds, ann) in enumerate(out, 1):
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
    etapes = (opt("--etapes") or ",".join(ETAPES)).split(",")
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
    les_lots = lots(changes, getattr(pl, "points", []), int(opt("--lot", "40")), a.forces, pl.parts)
    for nom, etape, zone, cmds, ann, chunks in les_lots:
        ecrire_lot(sortie / "lots" / (nom + ".txt"), nom, etape, zone, cmds, chunks)
        ecrire_lot(sortie / "annulation" / (nom + ".txt"), nom, etape, zone, list(reversed(ann)), chunks, annulation=True)
    json.dump({"changements": changes, "points_t7": getattr(pl, "points", []), "resume": pl.resume,
               "lots": [(n, e, len(c)) for n, e, _z, c, _a, _ch in les_lots]},
              open(sortie / "plan.json", "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    md = rapport_plan(pl, avant, les_lots)
    (sortie / "plan.md").write_text(md, encoding="utf-8")
    print(md)


def rapport_plan(pl, avant, les_lots):
    cat = pl.cat
    L = ["# Plan de diversification des props", ""]
    tot = sum(pl.resume.values())
    L.append("%d changements en %d lots (%s)." % (tot, len(les_lots), ", ".join("%s %d" % kv for kv in pl.resume.items())))
    L.append("")
    if pl.non_corriges:
        L.append("Incohérences sans remplaçant possible (laissées, à voir à la main) : " +
                 ", ".join("%s ×%d" % kv for kv in pl.non_corriges.most_common()))
        L.append("")
    # présence des modèles
    loot = [pr for pr in cat.values() if pr.mod == "heraliz_loot" and pr.loot and pr.gab in ("meuble", "objet", "cadavre")]
    av0 = sum(1 for pr in loot if avant[pr.id] > 0)
    ap0 = sum(1 for pr in loot if pl.compte[pr.id] > 0)
    L.append("Conteneurs de loot présents sur la carte : %d / %d avant, %d / %d après (T7 : %d points d'apparition)." % (
        av0, len(loot), ap0, len(loot), len(getattr(pl, "points", []))))
    reste = sorted(pr.id.split(":")[1] for pr in loot if pl.compte[pr.id] == 0)
    if reste:
        L.append("Toujours absents (aucune place cohérente dans la carte actuelle : à poser à la main, ou option --ajouts) : " +
                 ", ".join("`%s`" % r for r in reste))
    L.append("")
    cfm = [pr for pr in cat.values() if pr.mod == "cfm" and pr.dayz and not pr.structure]
    L.append("Meubles cfm (thème DayZ) présents : %d / %d avant, %d / %d après." % (
        sum(1 for pr in cfm if avant[pr.id] > 0), len(cfm), sum(1 for pr in cfm if pl.compte[pr.id] > 0), len(cfm)))
    L.append("")
    # par palier : modèles différents
    L += ["| palier | modèles posés avant | après | le plus répété avant | après |", "|---|---|---|---|---|"]
    par = defaultdict(list)
    for pr in loot:
        par[cle_palier(pr)].append(pr)
    for k in sorted(par, key=lambda k: (k[0] != "t", k)):
        prs = par[k]
        top_av = max(prs, key=lambda pr: avant[pr.id])
        top_ap = max(prs, key=lambda pr: pl.compte[pr.id])
        L.append("| %s | %d / %d | %d / %d | %s ×%d | %s ×%d |" % (
            ("T%d" % k[1]) if k[0] == "t" else "%s %d" % k, sum(1 for pr in prs if avant[pr.id] > 0), len(prs),
            sum(1 for pr in prs if pl.compte[pr.id] > 0), len(prs), top_av.id.split(":")[1], avant[top_av.id],
            top_ap.id.split(":")[1], pl.compte[top_ap.id]))
    L.append("")
    # paliers par lieu : changement du nombre de conteneurs (restauration et ajouts seulement : les autres étapes
    # gardent le palier)
    plus = Counter()
    for ch in pl.changes:
        if ch["etape"] in ("restauration", "ajouts") and ch["apres"].startswith("heraliz_loot:"):
            plus[(ch.get("lieu") or "?", "T%d" % cat[ch["apres"]].niveau if cle_palier(cat[ch["apres"]])[0] == "t"
                  else cle_palier(cat[ch["apres"]])[0])] += 1
    if plus:
        L.append("Conteneurs AJOUTÉS (restauration, ajouts) par lieu et palier : " +
                 ", ".join("%s %s +%d" % (l, t, n) for (l, t), n in sorted(plus.items())))
        L.append("")
    if getattr(pl, "points", None):
        L.append("Points T7 proposés : " + ", ".join("%s (%s, %s %d %d %d)" % (p["nom"], p["type"], p["lieu"], p["x"], p["y"], p["z"])
                                                   for p in pl.points))
        L.append("")
    raisons = Counter(ch["raison"] for ch in pl.changes if ch["etape"] == "correction")
    if raisons:
        L.append("Corrections : " + ", ".join("%s ×%d" % kv for kv in raisons.most_common()))
        L.append("")
    ex = Counter((ch["avant"].split(":")[-1], ch["apres"].split(":")[-1]) for ch in pl.changes)
    L.append("Remplacements les plus fréquents : " + ", ".join("%s → %s ×%d" % (a, b, n) for (a, b), n in ex.most_common(15)))
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
        # une pièce avec un doublon corrigé (deux cuisinières, deux frigos...), une pièce corrigée pour son type,
        # une pièce diversifiée : assez petites pour rester lisibles
        def bon(pid, test, types=None):
            info = a.pieces.get(pid, {})
            return (not info.get("couloir") and 16 <= info.get("cases", 0) <= 160 and len(a.par_piece.get(pid, ())) >= 3
                    and any(test(c) for c in par_piece[pid]) and (types is None or info.get("type") in types))
        choix = []
        for test, types in ((lambda c: c["raison"].startswith("doublon"), None),
                            (lambda c: c["etape"] == "correction", ("cuisine", "salon", "chambre", "garage")),
                            (lambda c: c["etape"] in ("equilibrage", "couverture"), ("cuisine", "chambre", "salon")),
                            (lambda c: c["etape"] == "restauration", None)):
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
