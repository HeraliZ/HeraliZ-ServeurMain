"""Props et conteneurs de loot de la carte : analyse, plan de diversification des conteneurs de HeraliZ Loot (T1 à T6),
rendus. LECTURE SEULE du monde : rien n'est jamais écrit dans ses fichiers. Les changements passent par des lots de
commandes que le SERVEUR joue lui-même (workflow « Props de la carte », console du Ptero), chacune conditionnelle et
annulable.

    python outils/props_carte.py analyser <monde> [--sortie DOSSIER] [--zones DOSSIER] [--processus N]
    python outils/props_carte.py plan <DOSSIER d'analyse> [--graine N] [--lot N] [--ajouts] [--familles]
                                      [--familles-compenser] [--max-changements N]
    python outils/props_carte.py rendus <monde> <DOSSIER d'analyse> [--pieces id,id] [--sortie DOSSIER]

analyser : relève chaque prop (cfm, HeraliZ Utils, HeraliZ Décors) et chaque conteneur de HeraliZ Loot (T1 à T7,
  familles), avec son orientation et ses données ; découpe l'intérieur des bâtiments en pièces (volumes couverts
  fermés par des murs, des portes ou un passage d'un bloc de large) et regroupe les pièces par bâtiment ; puis compte
  et repère : props jamais posés, anciens meubles cfm retirés du jar (ils disparaissent au chargement du secteur),
  doublons dans une pièce (deux frigos, deux cuisinières...), props d'extérieur à l'intérieur (ruche...) et
  inversement, paliers de loot absents ou mal répartis par lieu, écarts avec les zones de loot du pack de données.
  Sortie : rapport.md (court, pour le résumé du workflow), rapport_complet.md, analyse.json.gz (détails, pour le plan).
  --zones : dossier des zones de loot (défaut : <monde>/datapacks/heraliz_carte/data/heraliz_loot/zones).

plan : consigne du propriétaire (15/10) : 100 % des modèles T1 à T6 de HeraliZ Loot posés sur la carte, en nombre
  équilibré ; T7 réservé aux événements (rien posé), ni meubles cfm (familles : option --familles, plus bas). À
  partir de l'analyse, le plan remplace des conteneurs T1 à T6 déjà posés par d'autres modèles du MÊME palier (règles
  de outils/props_catalogue.py : même gabarit, même orientation, pièce ou extérieur compatibles, une cuisinière / un
  frigo par pièce, place libre exacte pour un gros conteneur, sans boucher de porte ni de passage). Sortie dans
  <analyse>/plan/ : lots/NNN_etape.txt (commandes), annulation/NNN_etape.txt (commandes inverses, état exact d'avant,
  données de bloc comprises), plan.json, plan.md (modèles posés avant / après, absents et pourquoi). Les commandes ne
  changent un bloc que s'il est ENCORE celui relevé (« execute if block ... run setblock ... ») : un prop déplacé ou
  un mur posé entre-temps par les constructeurs n'est pas touché. --ajouts : un modèle qui n'a AUCUNE place de
  remplacement cohérente est aussi posé sur une case libre (près d'un conteneur de son palier, dos au mur, jamais
  devant une porte ni dans un passage ; étape « ajouts », signalée à part) ; sans cette option, rien n'est posé dans
  le vide. --familles (demande du propriétaire du 24/10) : conteneurs de vivres, de soins et de munitions posés en plus
  sur des cases libres, un peu partout (nombre pour 100 conteneurs du lieu, au niveau du lieu, l'aérodrome au maximum ;
  tableaux en tête de outils/props_plan.py), toujours à portée d'une zone de loot de leur niveau, où HeraliZ Loot les
  range : ils tournent avec elle (étape « familles », signalée à part ; parts d'actifs inchangées, sauf avec
  --familles-compenser).

rendus : vues de dessus (avant / après) de quelques pièces, en PNG (Pillow).
"""
import base64
import gzip
import json
import os
import re
import sys
import time
import zlib
from collections import Counter, defaultdict
from multiprocessing import Pool
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import props_catalogue as pc  # noqa: E402
import props_monde as pm  # noqa: E402

DEPOT = Path(__file__).resolve().parent.parent
HALO = 32          # blocs lus autour de chaque région (pièces à cheval)
PLAFOND = 20       # un plafond à moins de 20 blocs au-dessus = intérieur couvert
R = 4              # rayon des voisinages gardés autour de chaque prop (pour le plan)
NS_PROPS = ("heraliz_loot", "cfm")

# drapeaux des voisinages (octet : 3 bits de classe + drapeaux de la couche du prop)
F_COUVERT, F_PASSAGE, F_PORTE, F_PIECE = 8, 16, 32, 64


# =================================================================================================== analyse d'une région
def _tuile(args):
    dossier, rx, rz, ids, structure = args
    t0 = time.time()
    regs = pm.Regions(dossier)
    cl = pm.Classeur(ids, structure)
    core = {}
    trouves = []        # (x, y, z, nom, props)
    parties = 0
    for cx in range(rx * 32, rx * 32 + 32):
        for cz in range(rz * 32, rz * 32 + 32):
            ch = regs.chunk(cx, cz, garder_be=True)
            if ch is None:
                continue
            core[(cx, cz)] = ch
            for sy, pal, donnees in ch["sections"]:
                interessants = [k for k, (n, _p) in enumerate(pal) if (n.partition(":")[0] in NS_PROPS or n in ids)
                                and n not in structure]
                if not interessants:
                    continue
                idx = pm.indices(donnees, len(pal)).reshape(16, 16, 16)
                for k in interessants:
                    n, p = pal[k]
                    pos = np.argwhere(idx == k)
                    if n == "heraliz_loot:partie":
                        parties += len(pos)
                        continue
                    for (yy, zz, xx) in pos:
                        trouves.append((cx * 16 + int(xx), sy * 16 + int(yy), cz * 16 + int(zz), n, p))
    if not trouves:
        return {"props": [], "pieces": {}, "batiments": {}, "parties": parties, "chunks": len(core), "rx": rx, "rz": rz}
    ys = [t[1] for t in trouves]
    y0 = max(-64, min(ys) - 2)
    y1 = min(320, max(ys) + PLAFOND + 3)
    X0, Z0 = rx * 512 - HALO, rz * 512 - HALO
    W = 512 + 2 * HALO
    cls = np.zeros((y1 - y0, W, W), dtype=np.uint8)
    for cx in range(rx * 32 - HALO // 16, rx * 32 + 32 + HALO // 16):
        for cz in range(rz * 32 - HALO // 16, rz * 32 + 32 + HALO // 16):
            ch = core.get((cx, cz)) or regs.chunk(cx, cz)
            if ch is None:
                continue
            ox, oz = cx * 16 - X0, cz * 16 - Z0
            for sy, pal, donnees in ch["sections"]:
                b0 = sy * 16
                if b0 + 16 <= y0 or b0 >= y1:
                    continue
                lut = np.array([cl.classe(n) for n, _p in pal], dtype=np.uint8)
                if len(pal) == 1:
                    bloc = np.full((16, 16, 16), lut[0], dtype=np.uint8)
                else:
                    bloc = lut[pm.indices(donnees, len(pal))].reshape(16, 16, 16)
                a, b = max(b0, y0), min(b0 + 16, y1)
                cls[a - y0:b - y0, oz:oz + 16, ox:ox + 16] = bloc[a - b0:b - b0]
    mur = (cls == pm.MUR)
    cs = np.zeros((cls.shape[0] + 1, W, W), dtype=np.int16)
    cs[:-1] = np.cumsum(mur[::-1].astype(np.int16), axis=0)[::-1]

    def couvert(y):
        i = y - y0
        j = min(i + 1 + PLAFOND, cls.shape[0])
        return (cs[i + 1] - cs[j]) > 0

    from scipy import ndimage
    couches = {}

    def couche(y):
        if y in couches:
            return couches[y]
        i = y - y0
        c = cls[i]
        pas = c <= pm.PROP
        sep = (c == pm.MUR) | (c == pm.PORTE)
        cov = couvert(y)
        n_ = np.zeros_like(sep); s_ = np.zeros_like(sep); e_ = np.zeros_like(sep); w_ = np.zeros_like(sep)
        n_[1:, :] = sep[:-1, :]; s_[:-1, :] = sep[1:, :]; w_[:, 1:] = sep[:, :-1]; e_[:, :-1] = sep[:, 1:]
        passage = pas & ((n_ & s_) | (e_ & w_))
        po = (c == pm.PORTE)
        pn = np.zeros_like(po); ps = np.zeros_like(po); pe = np.zeros_like(po); pw = np.zeros_like(po)
        pn[1:, :] = po[:-1, :]; ps[:-1, :] = po[1:, :]; pw[:, 1:] = po[:, :-1]; pe[:, :-1] = po[:, 1:]
        porte = pas & (pn | ps | pe | pw)
        lab, nl = ndimage.label(pas & cov & ~passage)
        clab, ncl = ndimage.label(pas & cov & passage)
        couches[y] = (lab, nl, clab, ncl, cov, passage, porte, pas, sep)
        return couches[y]

    for y in sorted(set(ys)):
        couche(y)
    # bâtiments : colonnes couvertes au niveau d'un prop, reliées en diagonale
    emprise = np.zeros((W, W), dtype=bool)
    for y, v in couches.items():
        emprise |= v[4] & (v[7] | v[8])
    blab, _nb = ndimage.label(emprise, structure=np.ones((3, 3), dtype=bool))
    bobj = ndimage.find_objects(blab)

    def bat_id(lbl):
        if lbl <= 0:
            return None
        sl = bobj[lbl - 1]
        return "b%d,%d" % (sl[1].start + X0, sl[0].start + Z0)

    pieces = {}

    def piece_de(y, lx, lz):
        """(id de pièce, id de bâtiment) de la case (lx, lz) de la couche y, ou (None, bâtiment)."""
        lab, nl, clab, ncl, cov, passage, porte, pas, sep = couche(y)
        for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            x, z = lx + dx, lz + dz
            if not (0 <= x < W and 0 <= z < W):
                continue
            for arr, pre in ((lab, "p"), (clab, "c")):
                L_ = int(arr[z, x])
                if L_ > 0:
                    return _piece(y, arr, L_, pre), bat_id(int(blab[z, x]))
        return None, bat_id(int(blab[lz, lx]))

    stats = {}

    def _piece(y, arr, L_, pre):
        key = (y, pre, L_)
        if key in stats:
            return stats[key]
        lab, nl, clab, ncl, cov, passage, porte, pas, sep = couche(y)
        objs = stats.setdefault(("obj", y, pre), ndimage.find_objects(arr))
        sl = objs[L_ - 1]
        sub = arr[sl] == L_
        n = int(sub.sum())
        z0_, x0_ = sl[0].start, sl[1].start
        pid = "%s%d,%d,%d" % (pre, y, x0_ + X0, z0_ + Z0)
        # bord : murs/portes, extérieur (libre et sans plafond), autre (passage, autre pièce)
        zz0, zz1 = max(0, sl[0].start - 1), min(W, sl[0].stop + 1)
        xx0, xx1 = max(0, sl[1].start - 1), min(W, sl[1].stop + 1)
        m = np.zeros((zz1 - zz0, xx1 - xx0), dtype=bool)
        m[sl[0].start - zz0:sl[0].stop - zz0, sl[1].start - xx0:sl[1].stop - xx0] = sub
        d = ndimage.binary_dilation(m) & ~m
        ouv = pas[zz0:zz1, xx0:xx1] & ~cov[zz0:zz1, xx0:xx1]
        po = cls[y - y0][zz0:zz1, xx0:xx1] == pm.PORTE
        bord = int(d.sum())
        pieces[pid] = {"y": y, "cases": n, "x0": int(x0_ + X0), "z0": int(z0_ + Z0), "x1": int(sl[1].stop - 1 + X0),
                       "z1": int(sl[0].stop - 1 + Z0), "ouvert": round(float((d & ouv).sum()) / max(1, bord), 3),
                       "portes": int((d & po).sum()), "couloir": pre == "c"}
        stats[key] = pid
        return pid

    props = []
    for (x, y, z, n, p) in trouves:
        lx, lz = x - X0, z - Z0
        pid, bid = piece_de(y, lx, lz)
        if pid is None and y - 1 >= y0 and cls[y - 1 - y0][lz, lx] == pm.PROP:
            pid, bid2 = piece_de(y - 1, lx, lz)
            bid = bid or bid2
        if pid is not None:
            pieces[pid]["batiment"] = bid
        i = y - y0
        col = mur[i + 1:, lz, lx]
        ciel = not bool(col.any())
        # voisinage : couches y-1 à y+3, ±R ; drapeaux de la couche y
        lab, nl, clab, ncl, cov, passage, porte, pas, sep = couche(y)
        vz0, vz1, vx0, vx1 = lz - R, lz + R + 1, lx - R, lx + R + 1
        vois = np.full((5, 2 * R + 1, 2 * R + 1), pm.MUR, dtype=np.uint8)
        if vz0 >= 0 and vx0 >= 0 and vz1 <= W and vx1 <= W:
            for k, yy in enumerate(range(y - 1, y + 4)):
                if y0 <= yy < y1:
                    vois[k] = cls[yy - y0][vz0:vz1, vx0:vx1]
            fl = (cov[vz0:vz1, vx0:vx1] * F_COUVERT) | (passage[vz0:vz1, vx0:vx1] * F_PASSAGE) | \
                 (porte[vz0:vz1, vx0:vx1] * F_PORTE)
            if pid is not None:
                arr = clab if pid.startswith("c") else lab
                L_ = int(arr[lz, lx]) or 0
                if L_:
                    fl = fl | ((arr[vz0:vz1, vx0:vx1] == L_) * F_PIECE)
            vois[1] = vois[1] | fl.astype(np.uint8)
        be = None
        ch = core.get((x >> 4, z >> 4))
        if ch is not None:
            for e in ch["be"]:
                if e.get("x", (0, None))[1] == x and e.get("y", (0, None))[1] == y and e.get("z", (0, None))[1] == z:
                    be = pm.nbt_entite_bloc(e)
                    break
        props.append({"x": x, "y": y, "z": z, "id": n, "etat": p, "be": be or None, "piece": pid, "batiment": bid,
                      "couvert": bool(cov[lz, lx]), "ciel": ciel,
                      "v": base64.b64encode(zlib.compress(vois.tobytes(), 6)).decode("ascii")})
    bats = {}
    for pr in props:
        if pr["batiment"]:
            bats.setdefault(pr["batiment"], 0)
            bats[pr["batiment"]] += 1
    # pièces vides et fermées des bâtiments qui ont des props (petites pièces : salles de bain, réserves...) : gardées
    # avec leur plan pour l'option --ajouts du plan
    avec_props = {int(blab[p["z"] - Z0, p["x"] - X0]) for p in props}
    avec_props.discard(0)
    for y in sorted(couches):
        lab, nl, clab, ncl, cov, passage, porte, pas, sep = couche(y)
        objs = ndimage.find_objects(lab)
        tailles = np.bincount(lab.ravel(), minlength=nl + 1)
        for L_ in range(1, nl + 1):
            sl = objs[L_ - 1]
            if sl is None or not (4 <= tailles[L_] <= 400):
                continue
            if not (HALO <= sl[0].start < HALO + 512 and HALO <= sl[1].start < HALO + 512):
                continue
            zc, xc = sl[0].start, sl[1].start
            pid = "p%d,%d,%d" % (y, xc + X0, zc + Z0)
            if pid in pieces:
                continue
            m = lab[sl] == L_
            bl = set(np.unique(blab[sl][m]).tolist()) & avec_props
            if not bl:
                continue
            _piece(y, lab, L_, "p")
            info = pieces[pid]
            if info["ouvert"] > 0.1:
                del pieces[pid]
                continue
            info["vide"] = True
            info["batiment"] = bat_id(next(iter(bl)))
            z0_, z1_ = max(0, sl[0].start - 1), min(W, sl[0].stop + 1)
            x0_, x1_ = max(0, sl[1].start - 1), min(W, sl[1].stop + 1)
            plan = np.zeros((5, z1_ - z0_, x1_ - x0_), dtype=np.uint8)
            for k, yy in enumerate(range(y - 1, y + 4)):
                if y0 <= yy < y1:
                    plan[k] = cls[yy - y0][z0_:z1_, x0_:x1_]
            fl = (cov[z0_:z1_, x0_:x1_] * F_COUVERT) | (passage[z0_:z1_, x0_:x1_] * F_PASSAGE) | \
                 (porte[z0_:z1_, x0_:x1_] * F_PORTE) | ((lab[z0_:z1_, x0_:x1_] == L_) * F_PIECE)
            plan[1] = plan[1] | fl.astype(np.uint8)
            info["plan"] = {"x": int(x0_ + X0), "z": int(z0_ + Z0), "l": int(x1_ - x0_), "p": int(z1_ - z0_),
                            "v": base64.b64encode(zlib.compress(plan.tobytes(), 6)).decode("ascii")}
    return {"props": props, "pieces": pieces, "batiments": bats, "parties": parties, "chunks": len(core),
            "rx": rx, "rz": rz, "duree": round(time.time() - t0, 1)}


def voisinage(pr):
    return np.frombuffer(zlib.decompress(base64.b64decode(pr["v"])), dtype=np.uint8).reshape(5, 2 * R + 1, 2 * R + 1).copy()


# =================================================================================================== zones de loot
def lire_zones(dossier):
    """(x, y, z) -> (zone, lieu, nom du conteneur attendu) d'après les fichiers de zones du pack de données."""
    out = {}
    infos = {}
    d = Path(dossier)
    if not d.is_dir():
        return out, infos
    for f in sorted(d.rglob("*.json")):
        try:
            z = json.loads(f.read_text(encoding="utf-8"))
        except ValueError:
            continue
        if (z.get("dimension") or "minecraft:overworld") != "minecraft:overworld":
            continue
        zid = f.relative_to(d).with_suffix("").as_posix()
        lieu = zid.split("/")[0]
        infos[zid] = {"nom": z.get("nom", zid), "actifs": z.get("actifs"), "lieu": lieu, "n": len(z.get("conteneurs") or [])}
        for c in z.get("conteneurs") or []:
            out[(int(c[0]), int(c[1]), int(c[2]))] = (zid, lieu, c[3])
    return out, infos


# =================================================================================================== analyse
def analyser(monde, sortie, zones=None, processus=None):
    t0 = time.time()
    cat = pc.catalogue()
    ids = {i for i, p in cat.items() if p.mod in ("heralisz_utils", "heraliz_decors")}
    ids |= {i for i in cat if i.startswith("heraliz_loot:")}
    structure = {i for i, p in cat.items() if p.structure and p.mod == "cfm"}
    dossier = pm.dossier_region(monde)
    regions = pm.Regions(dossier).regions()
    taches = [(str(dossier), rx, rz, ids, structure) for rx, rz in regions]
    print("[props] %d régions à lire dans %s" % (len(taches), dossier), flush=True)
    resultats = []
    with Pool(processus or max(1, min(os.cpu_count() or 2, 4))) as pool:
        for r in pool.imap_unordered(_tuile, taches):
            resultats.append(r)
            if r["props"]:
                print("[props] région %d,%d : %d props, %d pièces (%.1f s)" % (
                    r["rx"], r["rz"], len(r["props"]), len(r["pieces"]), r.get("duree", 0)), flush=True)
    props, pieces, parties, chunks = [], {}, 0, 0
    for r in resultats:
        props += r["props"]
        for k, v in r["pieces"].items():
            pieces.setdefault(k, v)
        parties += r["parties"]
        chunks += r["chunks"]
    props.sort(key=lambda p: (p["x"], p["z"], p["y"]))
    zdir = zones or (Path(monde) / "datapacks/heraliz_carte/data/heraliz_loot/zones")
    zpos, zinfos = lire_zones(zdir)
    a = Analyse(cat, props, pieces, zpos, zinfos, parties, chunks)
    a.calculer()
    Path(sortie).mkdir(parents=True, exist_ok=True)
    Path(sortie, "rapport.md").write_text(a.rapport(court=True), encoding="utf-8")
    Path(sortie, "rapport_complet.md").write_text(a.rapport(court=False), encoding="utf-8")
    with gzip.open(Path(sortie, "analyse.json.gz"), "wt", encoding="utf-8") as f:
        json.dump({"monde": str(monde), "forces": sorted(pm.chunks_forces(monde)), "loot": pm.donnees_loot(monde), "props": props, "pieces": pieces, "zones": {"%d,%d,%d" % k: v for k, v in zpos.items()},
                   "zinfos": zinfos, "parties": parties, "chunks": chunks}, f, ensure_ascii=False)
    print("[props] analyse en %.0f s : %d props, %d pièces ; rapport dans %s" % (time.time() - t0, len(props), len(pieces), sortie))
    return a


def charger(dossier):
    with gzip.open(Path(dossier, "analyse.json.gz"), "rt", encoding="utf-8") as f:
        d = json.load(f)
    zpos = {tuple(int(v) for v in k.split(",")): tuple(v) for k, v in d["zones"].items()}
    a = Analyse(pc.catalogue(), d["props"], d["pieces"], zpos, d["zinfos"], d["parties"], d["chunks"])
    a.monde = d.get("monde")
    a.forces = {tuple(c) for c in d.get("forces") or []}
    a.loot = d.get("loot") or {"parts": {}, "places": [], "points": []}
    a.calculer()
    return a


class Analyse:
    def __init__(self, cat, props, pieces, zpos, zinfos, parties, chunks):
        self.cat, self.props, self.pieces = cat, props, pieces
        self.zpos, self.zinfos, self.parties, self.chunks = zpos, zinfos, parties, chunks
        self.monde = None
        self.forces = set()
        self.loot = {"parts": {}, "places": [], "points": []}

    # ------------------------------------------------------------------ calculs
    def calculer(self):
        cat = self.cat
        self.compte = Counter(p["id"] for p in self.props)
        self.par_piece = defaultdict(list)
        for i, p in enumerate(self.props):
            p["i"] = i
            if p["piece"]:
                self.par_piece[p["piece"]].append(p)
        # lieu de chaque prop : zone de loot à sa place, sinon celle du conteneur le plus proche (48 blocs)
        zxy = [(k, v) for k, v in self.zpos.items()]
        grille = defaultdict(list)
        for (x, y, z), v in zxy:
            grille[(x >> 6, z >> 6)].append((x, y, z, v))
        for p in self.props:
            v = self.zpos.get((p["x"], p["y"], p["z"]))
            if v is None:
                best, bd = None, 48 * 48
                for gx in range((p["x"] >> 6) - 1, (p["x"] >> 6) + 2):
                    for gz in range((p["z"] >> 6) - 1, (p["z"] >> 6) + 2):
                        for (x, y, z, vv) in grille.get((gx, gz), ()):
                            d = (x - p["x"]) ** 2 + (z - p["z"]) ** 2
                            if d < bd:
                                best, bd = vv, d
                p["zone"], p["lieu"] = (best[0], best[1]) if best else (None, None)
                p["dans_zone"] = False
            else:
                p["zone"], p["lieu"] = v[0], v[1]
                p["dans_zone"] = v[2] == p["id"].split(":", 1)[1]
        # type de chaque pièce : vote des props (1 / nombre de pièces possibles), conteneurs de loot surtout
        for pid, ps in self.par_piece.items():
            votes = Counter()
            for p in ps:
                pr = cat.get(p["id"])
                if pr is None or not pr.pieces or len(pr.pieces) > 8:
                    continue
                poids = (1.0 if pr.mod == "heraliz_loot" else 0.5) / len(pr.pieces)
                for t in sorted(pr.pieces):
                    votes[t] += poids
            info = self.pieces.get(pid, {})
            if votes:
                (t1, v1), = votes.most_common(1)
                info["type"] = t1
                info["sur"] = round(v1 / max(1e-6, sum(votes.values())), 2)
            else:
                info["type"] = None
                info["sur"] = 0
            info["n"] = len(ps)
        self._incoherences()

    def env(self, p):
        """« int » (pièce fermée), « abri » (couvert mais ouvert), « ext » (dehors)."""
        if p["piece"]:
            info = self.pieces.get(p["piece"], {})
            if info.get("ouvert", 1) <= 0.25:
                return "int"
            return "abri"
        return "abri" if p["couvert"] else "ext"

    def _incoherences(self):
        cat = self.cat
        self.inc = defaultdict(list)
        for pid, ps in self.par_piece.items():
            info = self.pieces[pid]
            groupes = defaultdict(list)
            for p in ps:
                pr = cat.get(p["id"])
                if pr and pr.groupe:
                    groupes[pr.groupe].append(p)
            for g, lst in groupes.items():
                maxi = pc.maxi_groupe(g, info)
                if len(lst) > maxi:
                    self.inc["doublon"].append((pid, g, lst))
            meme = Counter(p["id"] for p in ps if cat.get(p["id"]) and cat[p["id"]].mod == "heraliz_loot"
                           and not cat[p["id"]].groupe and cat[p["id"]].categorie not in ("cadavre",))
            for i, n in meme.items():
                if n >= 3 and info.get("cases", 0) < 60:
                    self.inc["repetition"].append((pid, i, n))
        for p in self.props:
            pr = cat.get(p["id"])
            if pr is None:
                continue
            e = self.env(p)
            if e == "int" and pr.ext and not pr.pieces and pr.mod == "heraliz_loot" and pr.gab != "véhicule":
                self.inc["exterieur_dedans"].append(p)
            elif e == "ext" and p["ciel"] and pr.pieces and not pr.ext:
                self.inc["interieur_dehors"].append(p)
            if p["piece"] and pr.pieces and len(pr.pieces) <= 6:
                info = self.pieces[p["piece"]]
                if info.get("type") and info.get("sur", 0) >= 0.5 and info["type"] not in pr.pieces and info.get("n", 0) >= 3:
                    self.inc["mauvaise_piece"].append(p)

    # ------------------------------------------------------------------ rapport
    def rapport(self, court=True):
        cat = self.cat
        L = []
        loot = [p for p in self.props if p["id"].startswith("heraliz_loot:")]
        conts = [p for p in loot if cat.get(p["id"]) and cat[p["id"]].loot]
        L += ["# Props et conteneurs de loot de la carte", "",
              "%d chunks lus. %d props relevés (%d conteneurs de loot, %d véhicules de décor, %d meubles cfm, %d décors), "
              "%d blocs « partie » (cases des gros conteneurs). %d pièces fermées avec des props, %d couloirs." % (
                  self.chunks, len(self.props), len(conts), len(loot) - len(conts),
                  sum(1 for p in self.props if p["id"].startswith("cfm:")),
                  sum(1 for p in self.props if p["id"].split(":")[0] in ("heralisz_utils", "heraliz_decors")),
                  self.parties, sum(1 for v in self.pieces.values() if not v.get("couloir") and not v.get("vide")),
                  sum(1 for v in self.pieces.values() if v.get("couloir"))),
              "%d petites pièces fermées et vides dans ces bâtiments (salles de bain, réserves... : places possibles "
              "de l'option « ajouts » du plan, pour les modèles de salle de bain sans autre place)."
              % sum(1 for v in self.pieces.values() if v.get("vide")), ""]
        # ---- paliers
        par_niv = Counter()
        types_niv = defaultdict(set)
        exist_niv = defaultdict(set)
        for i, pr in cat.items():
            if pr.mod == "heraliz_loot" and pr.loot:
                exist_niv[(pr.niveau, _fam(i))].add(i)
        for p in conts:
            pr = cat[p["id"]]
            par_niv[(pr.niveau, _fam(p["id"]))] += 1
            types_niv[(pr.niveau, _fam(p["id"]))].add(p["id"])
        L += ["## Paliers de loot", "", "| palier | conteneurs posés | modèles posés / existants | jamais posés |", "|---|---|---|---|"]
        for k in sorted(exist_niv, key=lambda k: (k[1] != "t", k[1], k[0])):
            jam = sorted(exist_niv[k] - types_niv[k])
            nom = ("T%d" % k[0]) if k[1] == "t" else "%s %d" % (k[1], k[0])
            L.append("| %s | %d | %d / %d | %s |" % (nom, par_niv[k], len(types_niv[k]), len(exist_niv[k]),
                                                    ", ".join(j.split(":")[1] for j in jam[:12]) + (" …" if len(jam) > 12 else "")))
        L.append("")
        if not any(cat[p["id"]].niveau == 7 for p in conts):
            L.append("Aucun coffre T7 posé : c'est normal, les T7 sont réservés aux événements (point d'apparition, "
                     "`/heralizloot point ajouter`) ; le plan n'en pose pas.")
            L.append("")
        # ---- par lieu
        lieux = defaultdict(Counter)
        for p in conts:
            if p["lieu"]:
                lieux[p["lieu"]][_palier(cat[p["id"]])] += 1
        L += ["## Paliers par lieu", "", "| lieu | conteneurs | " + " | ".join("T%d" % i for i in range(1, 7)) + " | familles |",
              "|---|---|" + "---|" * 7]
        for lieu in sorted(lieux, key=lambda l: -sum(lieux[l].values())):
            c = lieux[lieu]
            L.append("| %s | %d | %s | %d |" % (lieu, sum(c.values()), " | ".join(str(c.get("T%d" % i, 0) or "") for i in range(1, 7)),
                                              sum(v for k, v in c.items() if not k.startswith("T"))))
        L.append("")
        # ---- props jamais posés
        poses = set(self.compte)
        L += ["## Props jamais posés", ""]
        for mod in ("heraliz_loot", "cfm", "heralisz_utils", "heraliz_decors"):
            ex = [i for i, pr in cat.items() if pr.mod == mod and pr.dayz and not pr.structure]
            jam = sorted(set(ex) - poses)
            hors = sum(1 for i, pr in cat.items() if pr.mod == mod and (not pr.dayz or pr.structure))
            L.append("- **%s** : %d posés sur %d (thème DayZ%s) ; jamais posés : %d%s" % (
                mod, len(set(ex) & poses), len(ex), (", %d hors thème ou structure non comptés" % hors) if hors else "",
                len(jam), "" if court else " : " + ", ".join("`%s`" % j.split(":")[1] for j in jam)))
        L.append("")
        # ---- anciens meubles cfm retirés
        retires = Counter(p["id"] for p in self.props if p["id"].startswith("cfm:") and p["id"] not in cat)
        if retires:
            eq = sum(n for i, n in retires.items() if pc.equivalent_loot(i.split(":")[1], cat))
            L += ["## Anciens meubles cfm retirés du jar", "",
                  "%d blocs de %d sortes de meubles cfm de la carte n'existent plus dans cfm « PATCH » : au chargement de "
                  "leur secteur, le jeu les remplace par de l'air (ils sont devenus des conteneurs de HeraliZ Loot). "
                  "%d ont un équivalent exact dans HeraliZ Loot (même modèle). Pour information : le plan ne les "
                  "repose pas (consigne du 15/10 : seulement les conteneurs T1 à T6 déjà posés)." % (
                      sum(retires.values()), len(retires), eq), ""]
            if not court:
                L.append(", ".join("`%s` ×%d" % (i.split(":")[1], n) for i, n in retires.most_common()))
                L.append("")
        # ---- les plus fréquents
        L += ["## Les plus répétés", ""]
        top = [(i, n) for i, n in self.compte.most_common() if i in cat and cat[i].mod == "heraliz_loot" and cat[i].loot][:15]
        L.append(", ".join("`%s` ×%d" % (i.split(":")[1], n) for i, n in top))
        L.append("")
        # ---- incohérences
        inc = self.inc
        L += ["## Incohérences", "",
              "- doublons dans une même pièce (groupes : %s) : **%d pièces**" % (
                  ", ".join("%s ×%d" % (g, n) for g, n in Counter(g for _p, g, _l in inc["doublon"]).most_common(8)), len(inc["doublon"])),
              "- même conteneur répété 3 fois ou plus dans une petite pièce : %d" % len(inc["repetition"]),
              "- props d'extérieur dans une pièce fermée : **%d** (%s)" % (len(inc["exterieur_dedans"]), ", ".join(
                  "%s ×%d" % (i.split(":")[1], n) for i, n in Counter(p["id"] for p in inc["exterieur_dedans"]).most_common(8))),
              "- props d'intérieur à ciel ouvert : **%d** (%s)" % (len(inc["interieur_dehors"]), ", ".join(
                  "%s ×%d" % (i.split(":")[1], n) for i, n in Counter(p["id"] for p in inc["interieur_dehors"]).most_common(8))),
              "- props qui ne vont pas avec le type de leur pièce : %d" % len(inc["mauvaise_piece"]), ""]
        exemples = inc["doublon"][:5 if court else 60]
        if exemples:
            L.append("Exemples de doublons :")
            for pid, g, lst in exemples:
                info = self.pieces[pid]
                L.append("- pièce %s (%s, %d cases) : %d × %s — %s" % (
                    _coord(info), info.get("type") or "?", info["cases"], len(lst), g,
                    ", ".join("%s %d %d %d" % (p["id"].split(":")[1], p["x"], p["y"], p["z"]) for p in lst)))
            L.append("")
        if not court:
            for cle in ("exterieur_dedans", "interieur_dehors", "mauvaise_piece"):
                if inc[cle]:
                    L.append("### %s" % cle)
                    for p in inc[cle][:200]:
                        L.append("- %s %d %d %d (pièce %s)" % (p["id"].split(":")[1], p["x"], p["y"], p["z"],
                                                             self.pieces.get(p["piece"], {}).get("type") if p["piece"] else "-"))
                    L.append("")
        # ---- pièces
        types = Counter(v.get("type") for k, v in self.pieces.items() if not v.get("couloir") and v.get("n"))
        L += ["## Pièces", "", "Type de chaque pièce d'après ses props (vote) : " +
              ", ".join("%s ×%d" % (t or "inconnue", n) for t, n in types.most_common()), ""]
        # ---- zones de loot
        if self.zpos:
            pos_monde = {(p["x"], p["y"], p["z"]): p["id"].split(":", 1)[1] for p in loot}
            manquants = sum(1 for k in self.zpos if k not in pos_monde)
            autres = sum(1 for k, v in self.zpos.items() if k in pos_monde and pos_monde[k] != v[2])
            hors = sum(1 for p in conts if (p["x"], p["y"], p["z"]) not in self.zpos)
            L += ["## Zones de loot (pack de données)", "",
                  "%d zones, %d conteneurs listés. Dans le monde : %d absents de leur place, %d remplacés par un autre "
                  "modèle, %d conteneurs posés hors des fichiers (ils rejoignent la zone de leur palier la plus proche, "
                  "comme prévu par HeraliZ Loot)." % (len(self.zinfos), len(self.zpos), manquants, autres, hors), ""]
        return "\n".join(L) + "\n"


def _fam(i):
    m = re.match(r"heraliz_loot:([a-z]+)\d", i)
    return "t" if m and m.group(1) == "t" else (m.group(1) if m else "?")


def _palier(pr):
    f = _fam(pr.id)
    return "T%d" % pr.niveau if f == "t" else f


def _coord(info):
    return "%d %d %d" % ((info["x0"] + info["x1"]) // 2, info["y"], (info["z0"] + info["z1"]) // 2)


# =================================================================================================== ligne de commande
def main():
    args = sys.argv[1:]
    if not args or args[0] not in ("analyser", "plan", "rendus"):
        print(__doc__)
        sys.exit(1)

    def opt(nom, defaut=None):
        if nom in args:
            v = args[args.index(nom) + 1]
            return v
        return defaut

    libres = [a for i, a in enumerate(args[1:], 1) if not a.startswith("--") and not args[i - 1].startswith("--")]
    if args[0] == "analyser":
        sortie = opt("--sortie", str(DEPOT / "livraison/props_carte"))
        analyser(libres[0], sortie, opt("--zones"), int(opt("--processus", "0")) or None)
        print(Path(sortie, "rapport.md").read_text(encoding="utf-8"))
    elif args[0] == "plan":
        import props_plan
        props_plan.main_plan(libres[0], args)
    elif args[0] == "rendus":
        import props_plan
        props_plan.main_rendus(libres[0], libres[1], args)


if __name__ == "__main__":
    main()
