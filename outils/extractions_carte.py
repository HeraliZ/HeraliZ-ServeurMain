"""Extractions de la carte : liste des zones fixes posées en jeu (/extraction create), carte de leur répartition,
couverture de la carte et points proposés pour combler les trous. LECTURE SEULE : rien n'est écrit dans le monde ni
sur le serveur ; on lit une COPIE des fichiers (le workflow « Extractions de la carte » les télécharge du Ptero).

    python outils/extractions_carte.py <heraliz_extraction_zones.dat> [options]

Le fichier des zones est data/heraliz_extraction_zones.dat du monde en service (registre du mod HeraliZ Extraction,
NBT compressé : Zones = [{Name, Dimension, X, Y, Z, Radius, Duration, Mode, Label, OpenUntil, ClosedAt}], et depuis la
1.5.0 VagueFin et Vague).

Options :
  --sortie <dossier>      où écrire rapport.md, zones.json et les images (défaut : livraison/extractions)
  --monde <dossier>       copie du monde (dossier region/ du monde principal) : hauteurs, eau, toits, pente ; sans elle,
                          les points proposés sont tirés sur l'image du fond (hauteur inconnue, « à vérifier en jeu »)
  --level <level.dat>     heure de jeu du monde (zones ouvertes au moment de la sauvegarde) ; défaut : <monde>/level.dat
  --config <toml>         heraliz_extraction-common.toml du serveur (réglages de la rotation affichés et simulés)
  --fond <dossier>        fond de la carte du monde (carte.json et tuiles) ; défaut : celui du mod HeraliZ Utils
  --dimension <id>        dimension dessinée (défaut : celle du fond, minecraft:overworld)
  --trou <blocs>          un endroit de la carte est « mal couvert » au-delà de tant de blocs de toute zone (défaut 600)
  --propositions <n>      nombre de points proposés au plus (défaut 10)
  --marge <blocs>         pas de point proposé à moins de tant de blocs du bord de la carte (défaut 200)
  --bordure <x0,z0,x1,z1> bordure du monde : la couverture et les points proposés ne comptent que ce qui est dedans, et
                          aucun point à moins de --marge blocs d'elle ; défaut : « bordure » de carte.json du fond
  --simulations <n>       vagues simulées pour juger le tirage du serveur (défaut 2000)

Images : carte_extractions.png (fond satellite, grille de 500 m comme l'écran de la carte, zones, distance entre
voisines, points proposés) et couverture.png (distance à l'extraction la plus proche, rouge au-delà de --trou).
Besoin : numpy et Pillow.
"""
import gzip
import json
import math
import re
import struct
import sys
import time
import tomllib
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

DEPOT = Path(__file__).resolve().parent.parent
FOND_MOD = DEPOT / "mods/heralisz-utils/src/client/resources/assets/heraliz_carte/carte"
POLICE = DEPOT / "mods/heraliz-extraction/src/client/resources/assets/heraliz_extraction/font/dmsans_bold.ttf"
POLICE_FINE = DEPOT / "mods/heraliz-extraction/src/client/resources/assets/heraliz_extraction/font/dmsans_medium.ttf"
GRILLE = 500
CELLULE = 8            # blocs par case pour la couverture et la recherche de points
IMAGE_MAX = 1400       # côté des images (pixels)
VIDE = (16, 19, 23)    # hors des chunks lus, sur le fond de carte_monde.py

# réglages de la rotation par défaut (mod 1.5.0) si le fichier du serveur ne les a pas
REGLAGES_DEFAUT = {"enabled": True, "openAtOnce": 4, "openAtOnceMax": 4, "minMinutes": 60, "maxMinutes": 60,
                   "waves": True, "minDistance": 800, "spread": True}
COULEURS_MODE = {"rotation": (229, 72, 77), "permanent": (70, 200, 120), "closed": (150, 150, 150)}
JAUNE = (255, 205, 60)


# ================================================================== NBT (lecture seule)
def _nbt(d, i, t):
    if t == 1: return struct.unpack_from(">b", d, i)[0], i + 1
    if t == 2: return struct.unpack_from(">h", d, i)[0], i + 2
    if t == 3: return struct.unpack_from(">i", d, i)[0], i + 4
    if t == 4: return struct.unpack_from(">q", d, i)[0], i + 8
    if t == 5: return struct.unpack_from(">f", d, i)[0], i + 4
    if t == 6: return struct.unpack_from(">d", d, i)[0], i + 8
    if t == 7:
        n = struct.unpack_from(">i", d, i)[0]
        return bytes(d[i + 4:i + 4 + n]), i + 4 + n
    if t == 8:
        n = struct.unpack_from(">H", d, i)[0]
        return bytes(d[i + 2:i + 2 + n]).decode("utf-8", "replace"), i + 2 + n
    if t == 9:
        et, n = d[i], struct.unpack_from(">i", d, i + 1)[0]
        i += 5
        out = []
        for _ in range(max(0, n)):
            v, i = _nbt(d, i, et)
            out.append(v)
        return out, i
    if t == 10:
        o = {}
        while True:
            tt = d[i]
            i += 1
            if tt == 0:
                return o, i
            n = struct.unpack_from(">H", d, i)[0]
            k = bytes(d[i + 2:i + 2 + n]).decode("utf-8", "replace")
            o[k], i = _nbt(d, i + 2 + n, tt)
    if t == 11:
        n = struct.unpack_from(">i", d, i)[0]
        return list(struct.unpack_from(">%di" % n, d, i + 4)), i + 4 + 4 * n
    if t == 12:
        n = struct.unpack_from(">i", d, i)[0]
        return list(struct.unpack_from(">%dq" % n, d, i + 4)), i + 4 + 8 * n
    raise ValueError("type NBT inconnu %d" % t)


def lire_nbt(chemin):
    brut = Path(chemin).read_bytes()
    if brut[:2] == b"\x1f\x8b":
        brut = gzip.decompress(brut)
    if not brut or brut[0] != 10:
        raise ValueError("%s : pas un fichier NBT" % chemin)
    n = struct.unpack_from(">H", brut, 1)[0]
    v, _ = _nbt(brut, 3 + n, 10)
    return v


# ================================================================== zones et réglages
def lire_zones(chemin):
    """Zones du registre et état de la vague : (zones, vague_fin, vague)."""
    if str(chemin).endswith(".json"):
        d = json.loads(Path(chemin).read_text(encoding="utf-8"))
        return d["zones"], d.get("vagueFin", 0), d.get("vague", [])
    racine = lire_nbt(chemin)
    data = racine.get("data", racine)
    zones = []
    for z in data.get("Zones", []):
        zones.append({
            "nom": z.get("Name", "?"),
            "libelle": z.get("Label", ""),
            "dimension": z.get("Dimension", "minecraft:overworld"),
            "x": float(z.get("X", 0.0)), "y": float(z.get("Y", 0.0)), "z": float(z.get("Z", 0.0)),
            "rayon": float(z.get("Radius", 8.0)),
            "secondes": int(z.get("Duration", 0)),
            "mode": z.get("Mode", "") or "rotation",
            "ouverteJusqua": int(z.get("OpenUntil", 0)),
            "fermeeA": int(z.get("ClosedAt", 0)),
        })
    return zones, int(data.get("VagueFin", 0)), list(data.get("Vague", []))


def affiche(z):
    if z["libelle"]:
        return z["libelle"]
    s = z["nom"].replace("_", " ").replace("-", " ").strip()
    return s[:1].upper() + s[1:] if s else z["nom"]


def lire_reglages(chemin):
    r = dict(REGLAGES_DEFAUT)
    absents = list(r)
    temps = 30
    if chemin and Path(chemin).is_file():
        d = tomllib.loads(Path(chemin).read_text(encoding="utf-8"))
        rot = d.get("rotation", {})
        absents = [k for k in r if k not in rot]
        for k in r:
            if k in rot:
                r[k] = rot[k]
        temps = d.get("zones", {}).get("durationSeconds", 30)
    return r, absents, temps


def heure_jeu(level):
    if not level or not Path(level).is_file():
        return None
    try:
        d = lire_nbt(level).get("Data", {})
        return int(d.get("Time", 0)) or None
    except Exception as e:
        print("[extractions] level.dat illisible : %s" % e)
        return None


# ================================================================== fond de la carte
class Fond:
    """Emprise et images du fond de la carte du monde (carte.json + tuiles de carte_monde.py)."""

    def __init__(self, dossier):
        self.ok = False
        self.vues = {}
        f = Path(dossier) / "carte.json"
        if not f.is_file():
            return
        self.info = json.loads(f.read_text(encoding="utf-8"))
        i = self.info
        self.x0, self.z0, self.largeur, self.hauteur = i["x0"], i["z0"], i["largeur"], i["hauteur"]
        self.bpp, self.dimension = i["blocsParPixel"], i.get("dimension", "minecraft:overworld")
        for vue in i.get("vues", []):
            img = Image.new("RGB", (i["pixelsLargeur"], i["pixelsHauteur"]), VIDE)
            manque = False
            for c in range(i["colonnes"]):
                for l in range(i["lignes"]):
                    t = Path(dossier) / ("%s_%d_%d.png" % (vue, c, l))
                    if not t.is_file():
                        manque = True
                        continue
                    img.paste(Image.open(t).convert("RGB"), (c * i["tuile"], l * i["tuile"]))
            if not manque:
                self.vues[vue] = img
        self.ok = bool(self.vues)


# ================================================================== terrain (copie du monde, facultatif)
class Terrain:
    """Hauteurs du sol et cases praticables, lues dans les régions avec la méthode de carte_monde.py (lecture seule)."""

    def __init__(self, monde, ignores=("crac",)):
        sys.path.insert(0, str(DEPOT / "outils"))
        import carte_monde as cm  # même lecture des régions que le fond de la carte
        reg = Path(monde) / "region"
        fichiers = sorted(p for p in reg.glob("r.*.mca") if re.match(r"r\.-?\d+\.-?\d+\.mca$", p.name))
        if not fichiers:
            raise SystemExit("[extractions] aucun fichier de région dans %s" % reg)
        rx = [int(p.name.split(".")[1]) for p in fichiers]
        rz = [int(p.name.split(".")[2]) for p in fichiers]
        self.x0, self.z0 = min(rx) * 512, min(rz) * 512
        W, H = (max(rx) - min(rx) + 1) * 512, (max(rz) - min(rz) + 1) * 512
        self.sol = np.full((H, W), -32768, np.int16)
        self.ok = np.zeros((H, W), bool)       # colonne praticable : à sec, à ciel ouvert, sans lave
        self.present = np.zeros((H, W), bool)
        self.eau = np.zeros((H, W), bool)
        t0 = time.time()
        from multiprocessing import Pool
        with Pool() as pool:
            for n, res in enumerate(pool.imap_unordered(cm.lire_region, [(str(f), set(ignores)) for f in fichiers]), 1):
                x, z, sy, sid, fy, so, _bio, pres, noms, _b, _l, _e = res
                m = np.repeat(np.repeat(pres, 16, axis=0), 16, axis=1)
                lave = np.array(["lava" in nm or "magma" in nm for nm in noms] or [False])[np.clip(sid, 0, max(0, len(noms) - 1))]
                eau = sy > fy
                ok = m & ~eau & ~lave & (fy - so <= 2)
                ox, oz = x * 512 - self.x0, z * 512 - self.z0
                sl = (slice(oz, oz + 512), slice(ox, ox + 512))
                self.sol[sl] = np.where(m, so, -32768)
                self.ok[sl] = ok
                self.present[sl] = m
                self.eau[sl] = m & eau
                if n % 16 == 0 or n == len(fichiers):
                    print("[extractions] %d/%d régions lues (%.0f s)" % (n, len(fichiers), time.time() - t0))

    def fenetre(self, a, x0, z0, w, h, vide):
        """Morceau de a (indexé depuis x0, z0 du monde, w x h blocs), complété par vide hors des régions lues."""
        out = np.full((h, w), vide, a.dtype)
        H, W = a.shape
        wx0, wx1 = max(x0, self.x0), min(x0 + w, self.x0 + W)
        wz0, wz1 = max(z0, self.z0), min(z0 + h, self.z0 + H)
        if wx1 > wx0 and wz1 > wz0:
            out[wz0 - z0:wz1 - z0, wx0 - x0:wx1 - x0] = a[wz0 - self.z0:wz1 - self.z0, wx0 - self.x0:wx1 - self.x0]
        return out

    def cases(self, x0, z0, nx, nz):
        """Grille de cases de CELLULE blocs à partir de (x0, z0) : (praticable, jouable, eau, hauteur du sol)."""
        w, h = nx * CELLULE, nz * CELLULE

        def bloc(a, vide):
            return self.fenetre(a, x0, z0, w, h, vide).reshape(nz, CELLULE, nx, CELLULE)

        ok, pres, ea = bloc(self.ok, False), bloc(self.present, False), bloc(self.eau, False)
        so = bloc(self.sol, -32768).astype(np.int32)
        hmax, hmin = so.max(axis=(1, 3)), so.min(axis=(1, 3))
        prat = ok.all(axis=(1, 3)) & (hmax - hmin <= 2)
        joue = pres.mean(axis=(1, 3)) > 0.5
        eau = ea.mean(axis=(1, 3)) > 0.5
        hy = np.where(prat, hmax, -32768).astype(np.int32)
        # une zone (rayon 8) doit tenir : la case et ses 8 voisines praticables, pente faible sur l'ensemble
        p = prat.copy()
        haut = np.where(prat, hy, -32768)
        bas_ = np.where(prat, hy, 32767)
        mx, mn = haut.copy(), bas_.copy()
        for dz in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if dx == 0 and dz == 0:
                    continue
                p &= np.roll(np.roll(prat, dz, axis=0), dx, axis=1)
                mx = np.maximum(mx, np.roll(np.roll(haut, dz, axis=0), dx, axis=1))
                mn = np.minimum(mn, np.roll(np.roll(bas_, dz, axis=0), dx, axis=1))
        p &= (mx - mn) <= 3
        p[0, :] = p[-1, :] = False
        p[:, 0] = p[:, -1] = False
        return p, joue, eau, hy


# ================================================================== distances et tirage (comme le serveur)
def dist(a, b):
    return math.hypot(a["x"] - b["x"], a["z"] - b["z"])


def mediane(v):
    v = sorted(v)
    m = len(v) // 2
    return v[m] if len(v) % 2 else (v[m - 1] + v[m]) / 2.0


def centre(zones):
    return (mediane([z["x"] for z in zones]), mediane([z["z"] for z in zones])) if zones else (0.0, 0.0)


def quart(z, c):
    return (1 if z["x"] >= c[0] else 0) + (2 if z["z"] >= c[1] else 0)


NOMS_QUARTS = ["nord-ouest", "nord-est", "sud-ouest", "sud-est"]


def choisir(pool, ouvertes, candidates, eviter, n, distance_min, repartir, rnd, fermee=None):
    """Même tirage que com.heraliz.extraction.serveur.Repartition (1.5.0) : (zones, distance, quarts, évitées)."""
    c = centre(pool)
    par_quart = max(1, (len(ouvertes) + n + 3) // 4)
    sans = [z for z in candidates if z["nom"] not in eviter]
    distances, d = [], max(0, distance_min)
    while d > 0 and d >= 50:
        distances.append(d)
        d = d * 85 // 100
    distances.append(0)
    meilleur = None
    for evite in ([True, False] if len(sans) < len(candidates) else [False]):
        liste = sans if evite else candidates
        tenu_evite = evite or len(sans) == len(candidates)
        for d in distances:
            for q in ([True, False] if repartir else [False]):
                t = essayer(liste, ouvertes, n, d, q, par_quart, c, rnd, fermee or {})
                if len(t) >= n:
                    return t, d, q or not repartir, tenu_evite
                if meilleur is None or len(t) > len(meilleur[0]):
                    meilleur = (t, d, q or not repartir, tenu_evite)
    return meilleur or ([], 0, False, False)


def essayer(liste, ouvertes, n, d, q, par_quart, c, rnd, fermee):
    meilleur = []
    par_attente = sorted(liste, key=lambda z: fermee.get(z["nom"], 0))
    ecart = max(2, len(par_attente) // 2)
    for _ in range(64):
        # comme le serveur : rang d'attente + écart au hasard (les zones qui attendent depuis longtemps passent devant)
        cles = [i + rnd.randrange(ecart) + rnd.random() for i in range(len(par_attente))]
        ordre = [z for _, z in sorted(zip(cles, par_attente), key=lambda kz: kz[0])]
        tirees, placees = [], list(ouvertes)
        for z in ordre:
            if len(tirees) >= n:
                break
            ok = all(dist(z, o) >= d for o in placees) if d > 0 else True
            if ok and q:
                ok = sum(1 for o in placees if quart(o, c) == quart(z, c)) < par_quart
            if ok:
                tirees.append(z)
                placees.append(z)
        if len(tirees) >= n:
            return tirees
        if len(tirees) > len(meilleur):
            meilleur = tirees
        if d == 0 and not q:
            break
    return meilleur


def simuler(pool, permanentes, reglages, vagues, graine=7):
    import random
    rnd = random.Random(graine)
    stats = {"distances": [], "quarts": 0, "evitees": 0, "completes": 0, "fois": {z["nom"]: 0 for z in pool},
             "deux_de_suite": 0}
    precedente = set()
    fermee = {}
    lo, hi = int(reglages["openAtOnce"]), max(int(reglages["openAtOnce"]), int(reglages["openAtOnceMax"]))
    for v in range(vagues):
        n = min(rnd.randint(lo, hi), len(pool))
        t, d, q, e = choisir(pool, permanentes, pool, precedente, n, int(reglages["minDistance"]),
                             bool(reglages["spread"]), rnd, fermee)
        noms = {z["nom"] for z in t}
        stats["distances"].append(d)
        stats["quarts"] += q
        stats["evitees"] += e
        stats["completes"] += len(t) >= n
        stats["deux_de_suite"] += len(noms & precedente)
        for nm in noms:
            stats["fois"][nm] += 1
        for nm in precedente:
            fermee[nm] = v + 1
        precedente = noms
    return stats


def meilleure_distance(pool, n):
    """Plus grande distance minimale possible entre n zones ouvertes ensemble (recherche exacte)."""
    if len(pool) < n or n < 2:
        return None, []
    D = [[dist(a, b) for b in pool] for a in pool]
    seuils = sorted({round(D[i][j], 1) for i in range(len(pool)) for j in range(i + 1, len(pool))}, reverse=True)

    def clique(t):
        ordre = sorted(range(len(pool)), key=lambda i: -sum(1 for j in range(len(pool)) if j != i and D[i][j] >= t))

        def rec(choisis, reste):
            if len(choisis) == n:
                return choisis
            for k, i in enumerate(reste):
                if len(choisis) + len(reste) - k < n:
                    return None
                r = rec(choisis + [i], [j for j in reste[k + 1:] if D[i][j] >= t])
                if r:
                    return r
            return None
        return rec([], ordre)

    # recherche par dichotomie sur les distances triées (une clique existe pour un seuil plus bas si elle existe plus haut)
    lo, hi, trouve = 0, len(seuils) - 1, None
    while lo <= hi:
        m = (lo + hi) // 2
        r = clique(seuils[m])
        if r:
            trouve = (seuils[m], r)
            hi = m - 1
        else:
            lo = m + 1
    if not trouve:
        return None, []
    return trouve[0], [pool[i] for i in trouve[1]]


# ================================================================== grille de 500 m (comme l'écran de la carte)
def colonne(i):
    if i < 0:
        return "?"
    s, n = "", i
    while True:
        s = chr(ord("A") + n % 26) + s
        n = n // 26 - 1
        if n < 0:
            return s


def carre(x, z, gx0, gz0):
    return "%s%d" % (colonne(int(math.floor((x - gx0) / GRILLE))), int(math.floor((z - gz0) / GRILLE)) + 1)


# ================================================================== images
def police(taille, fine=False):
    p = POLICE_FINE if fine else POLICE
    try:
        return ImageFont.truetype(str(p), taille)
    except Exception:
        try:
            return ImageFont.load_default(size=taille)
        except TypeError:
            return ImageFont.load_default()


def texte(dr, xy, s, f, couleur=(255, 255, 255), centre_=False):
    x, y = xy
    if centre_:
        b = dr.textbbox((0, 0), s, font=f)
        x -= (b[2] - b[0]) / 2
        y -= (b[3] - b[1]) / 2
    dr.text((x, y), s, font=f, fill=couleur, stroke_width=2, stroke_fill=(0, 0, 0))


class Vue:
    """Passage monde -> image."""

    def __init__(self, x0, z0, largeur, hauteur):
        self.x0, self.z0, self.largeur, self.hauteur = x0, z0, largeur, hauteur
        self.k = max(largeur, hauteur) / IMAGE_MAX
        self.w, self.h = int(round(largeur / self.k)), int(round(hauteur / self.k))

    def p(self, x, z):
        return (x - self.x0) / self.k, (z - self.z0) / self.k


def grille(img, v, gx0, gz0):
    dr = ImageDraw.Draw(img, "RGBA")
    f = police(13, fine=True)
    x = gx0
    while x <= v.x0 + v.largeur:
        X, _ = v.p(x, 0)
        if 0 <= X <= v.w:
            dr.line([(X, 0), (X, v.h)], fill=(255, 255, 255, 60), width=1)
        if x + GRILLE / 2 >= v.x0 and x + GRILLE / 2 <= v.x0 + v.largeur:
            texte(dr, (v.p(x + GRILLE / 2, 0)[0], 9), colonne(int((x - gx0) // GRILLE)), f, (230, 230, 230), True)
        x += GRILLE
    z = gz0
    while z <= v.z0 + v.hauteur:
        _, Y = v.p(0, z)
        if 0 <= Y <= v.h:
            dr.line([(0, Y), (v.w, Y)], fill=(255, 255, 255, 60), width=1)
        if z + GRILLE / 2 >= v.z0 and z + GRILLE / 2 <= v.z0 + v.hauteur:
            texte(dr, (10, v.p(0, z + GRILLE / 2)[1]), str(int((z - gz0) // GRILLE) + 1), f, (230, 230, 230), True)
        z += GRILLE


def dessiner_zones(img, v, zones, ouvertes, propositions, voisins=True):
    dr = ImageDraw.Draw(img, "RGBA")
    f, fp = police(14), police(12, fine=True)
    if voisins:
        faits = set()
        for a in zones:
            proches = sorted((b for b in zones if b is not a), key=lambda b: dist(a, b))[:2]
            for b in proches:
                cle = tuple(sorted((a["nom"], b["nom"])))
                if cle in faits:
                    continue
                faits.add(cle)
                pa, pb = v.p(a["x"], a["z"]), v.p(b["x"], b["z"])
                dr.line([pa, pb], fill=(255, 255, 255, 110), width=1)
                texte(dr, ((pa[0] + pb[0]) / 2, (pa[1] + pb[1]) / 2), "%d m" % round(dist(a, b)), fp, (220, 220, 220), True)
    for z in zones:
        X, Y = v.p(z["x"], z["z"])
        c = COULEURS_MODE.get(z["mode"], COULEURS_MODE["rotation"])
        r = 8
        if z["nom"] in ouvertes:
            dr.ellipse([X - r - 4, Y - r - 4, X + r + 4, Y + r + 4], outline=(255, 255, 255, 230), width=2)
        dr.ellipse([X - r, Y - r, X + r, Y + r], fill=c + (235,), outline=(0, 0, 0, 255), width=2)
        texte(dr, (X + r + 4, Y - 9), affiche(z), f)
    for p in propositions:
        X, Y = v.p(p["x"], p["z"])
        r = 9
        dr.polygon([(X, Y - r), (X + r, Y), (X, Y + r), (X - r, Y)], fill=JAUNE + (240,), outline=(0, 0, 0, 255))
        texte(dr, (X + r + 4, Y - 9), p["nom"], f, JAUNE)


def legende(img, lignes):
    dr = ImageDraw.Draw(img, "RGBA")
    f = police(13, fine=True)
    h = 10 + 18 * len(lignes)
    w = 10 + max(dr.textbbox((0, 0), s, font=f)[2] for s, _ in lignes) + 26
    x0, y0 = img.width - w - 12, img.height - h - 12
    dr.rounded_rectangle([x0, y0, x0 + w, y0 + h], 8, fill=(10, 12, 16, 200), outline=(255, 255, 255, 70))
    for i, (s, c) in enumerate(lignes):
        y = y0 + 8 + 18 * i
        if c:
            dr.ellipse([x0 + 8, y + 3, x0 + 18, y + 13], fill=c + (255,), outline=(0, 0, 0, 255))
        dr.text((x0 + 26, y), s, font=f, fill=(235, 235, 235))


def enregistrer(img, chemin):
    # palette de 256 couleurs (fichier léger) ; les couleurs des repères y sont mises d'office pour rester vives
    rgb = img.convert("RGB")
    reperes = list(COULEURS_MODE.values()) + [JAUNE, (255, 255, 255), (0, 0, 0), (60, 190, 90), (230, 210, 60),
                                              (240, 140, 40), (225, 50, 50)]
    fond = rgb.quantize(colors=256 - len(reperes), method=Image.Quantize.MEDIANCUT)
    pal = [c for rgb_ in reperes for c in rgb_] + fond.getpalette()[:3 * (256 - len(reperes))]
    palette = Image.new("P", (1, 1))
    palette.putpalette(pal)
    rgb.quantize(palette=palette, dither=Image.Dither.NONE).save(chemin, optimize=True)


def grandes_terres(terre, part=0.03):
    """Garde les terres d'un seul tenant qui font au moins part de toute la terre (pas les îlots ni les rochers)."""
    from collections import deque
    nz, nx = terre.shape
    etiq = np.zeros(terre.shape, np.int32)
    tailles = [0]
    for j0, i0 in zip(*np.nonzero(terre)):
        if etiq[j0, i0]:
            continue
        n = len(tailles)
        etiq[j0, i0] = n
        file, t = deque([(j0, i0)]), 0
        while file:
            j, i = file.popleft()
            t += 1
            for jj, ii in ((j - 1, i), (j + 1, i), (j, i - 1), (j, i + 1)):
                if 0 <= jj < nz and 0 <= ii < nx and terre[jj, ii] and not etiq[jj, ii]:
                    etiq[jj, ii] = n
                    file.append((jj, ii))
        tailles.append(t)
    total = max(1, int(terre.sum()))
    gardees = np.array([False] + [t >= part * total for t in tailles[1:]])
    return gardees[etiq]


def eroder(m, r=2):
    """Case gardée seulement si toutes les cases à r autour le sont (le point a de la place autour de lui)."""
    out = m.copy()
    for dz in range(-r, r + 1):
        for dx in range(-r, r + 1):
            out &= np.roll(np.roll(m, dz, axis=0), dx, axis=1)
    return out


# ================================================================== programme
def options(argv):
    o = {"zones": None, "sortie": str(DEPOT / "livraison/extractions"), "monde": None, "level": None, "config": None,
         "fond": str(FOND_MOD), "dimension": None, "trou": 600, "propositions": 10, "marge": 200, "simulations": 2000, "bordure": None}
    i = 0
    while i < len(argv):
        a = argv[i]
        if a.startswith("--"):
            k = a[2:]
            if k not in o or i + 1 >= len(argv):
                raise SystemExit(__doc__)
            v = argv[i + 1]
            o[k] = int(v) if k in ("trou", "propositions", "marge", "simulations") else v
            i += 2
        else:
            o["zones"] = a
            i += 1
    if not o["zones"]:
        raise SystemExit(__doc__)
    return o


def main():
    o = options(sys.argv[1:])
    sortie = Path(o["sortie"])
    if o["monde"] and Path(o["monde"]).resolve() in [sortie.resolve(), *sortie.resolve().parents]:
        raise SystemExit("[extractions] la sortie ne peut pas être dans le dossier du monde (lecture seule)")
    sortie.mkdir(parents=True, exist_ok=True)
    zones, vague_fin, vague = lire_zones(o["zones"])
    reglages, absents, temps_defaut = lire_reglages(o["config"])
    level = o["level"] or (str(Path(o["monde"]) / "level.dat") if o["monde"] else None)
    maintenant = heure_jeu(level)
    fond = Fond(o["fond"])
    dimension = o["dimension"] or (fond.dimension if fond.ok else "minecraft:overworld")
    print("[extractions] %d zone(s) lue(s) ; dimension dessinée %s ; fond %s" % (len(zones), dimension,
                                                                                   "trouvé" if fond.ok else "absent"))

    def ouverte(z):
        if z["mode"] == "permanent":
            return True
        return maintenant is not None and z["ouverteJusqua"] > maintenant

    ici = [z for z in zones if z["dimension"] == dimension]
    ailleurs = [z for z in zones if z["dimension"] != dimension]
    pool = [z for z in ici if z["mode"] == "rotation"]
    permanentes = [z for z in ici if z["mode"] == "permanent"]
    comptees = [z for z in ici if z["mode"] in ("rotation", "permanent")]   # celles qui servent un jour
    ouvertes_noms = {z["nom"] for z in zones if ouverte(z)}

    # emprise : celle du fond, sinon celle des zones avec une marge
    if fond.ok:
        X0, Z0, LARG, HAUT = fond.x0, fond.z0, fond.largeur, fond.hauteur
    else:
        xs = [z["x"] for z in ici] or [0.0]
        zs = [z["z"] for z in ici] or [0.0]
        X0, Z0 = int(min(xs)) - 500, int(min(zs)) - 500
        LARG, HAUT = max(1000, int(max(xs) - min(xs)) + 1000), max(1000, int(max(zs) - min(zs)) + 1000)
    gx0, gz0 = (X0 // GRILLE) * GRILLE, (Z0 // GRILLE) * GRILLE
    v = Vue(X0, Z0, LARG, HAUT)

    # ------------------------------------------------ cases de la carte : jouables, praticables, distance aux zones
    nx, nz = LARG // CELLULE, HAUT // CELLULE
    cx = X0 + (np.arange(nx) + 0.5) * CELLULE
    cz = Z0 + (np.arange(nz) + 0.5) * CELLULE
    terrain = None
    if o["monde"]:
        terrain = Terrain(o["monde"])
        prat, joue, eau, hy = terrain.cases(X0, Z0, nx, nz)
        source_points = "hauteurs lues dans la copie du monde (sol à sec, à ciel ouvert, pente faible sur 24 blocs)"
    else:
        hy = None
        if fond.ok:
            img = np.asarray(fond.vues.get("satellite", next(iter(fond.vues.values()))).resize((nx, nz), Image.BOX)).astype(int)
            r, g, b = img[..., 0], img[..., 1], img[..., 2]
            vide = (abs(r - VIDE[0]) < 6) & (abs(g - VIDE[1]) < 6) & (abs(b - VIDE[2]) < 6)
            eau = (b > r + 15) & (b >= g - 5)
            joue = ~vide
        else:
            joue = np.ones((nz, nx), bool)
            eau = np.zeros((nz, nx), bool)
        prat = eroder(joue & ~eau)
        source_points = "fond de la carte seulement (couleurs : ni vide ni eau) : hauteur, toits et pente inconnus"
    # bordure du monde : rien de ce qui est dehors ne compte (les joueurs n'y vont pas)
    bordure = o["bordure"] or (fond.info.get("bordure") if fond.ok else None)
    if isinstance(bordure, str):
        bordure = [int(float(t)) for t in bordure.split(",")]
    dedans = np.ones((nz, nx), bool)
    loin_bord = np.ones((nz, nx), bool)
    if bordure:
        bx0, bz0, bx1, bz1 = bordure
        dedans = ((cx[None, :] >= bx0) & (cx[None, :] <= bx1)) & ((cz[:, None] >= bz0) & (cz[:, None] <= bz1))
        m = o["marge"]
        loin_bord = ((cx[None, :] >= bx0 + m) & (cx[None, :] <= bx1 - m)) & ((cz[:, None] >= bz0 + m) & (cz[:, None] <= bz1 - m))
        joue = joue & dedans
    terre = grandes_terres(joue & ~eau)    # ce qu'on juge pour la couverture : la carte, sans les îlots

    def champ(points):
        d = np.full((nz, nx), np.inf)
        for p in points:
            d = np.minimum(d, np.hypot(cx[None, :] - p["x"], cz[:, None] - p["z"]))
        return d

    d_toutes = champ(comptees)
    d_rotation = champ(pool)
    n_terre = max(1, int(terre.sum()))
    trou = o["trou"]
    part_trou = float((terre & (d_toutes > trou)).sum()) / n_terre
    part_trou_rot = float((terre & (d_rotation > trou)).sum()) / n_terre

    # ------------------------------------------------ points proposés : le plus loin de toute zone, tant qu'il y a un trou
    marge = o["marge"] // CELLULE
    permis = prat & terre & loin_bord
    if marge > 0:
        permis[:marge, :] = permis[-marge:, :] = False
        permis[:, :marge] = permis[:, -marge:] = False
    propositions = []
    d = d_toutes.copy()
    noms_pris = {z["nom"] for z in zones}
    for _ in range(max(0, o["propositions"])):
        if not permis.any():
            break
        if np.isinf(d).all():
            # aucune zone encore : on part du milieu de la terre de la carte, puis toujours le plus loin
            jj, ii = np.nonzero(permis)
            k = int(np.argmin((jj - jj.mean()) ** 2 + (ii - ii.mean()) ** 2))
            j, i = int(jj[k]), int(ii[k])
            dd = np.where(permis, d, -1.0)
        else:
            dd = np.where(permis, d, -1.0)
            k = int(np.argmax(dd))
            j, i = divmod(k, nx)
            if dd[j, i] < trou:
                break
        x, z = int(round(cx[i])), int(round(cz[j]))
        nom = "nouvelle_" + carre(x, z, gx0, gz0).lower()
        s = 2
        while nom in noms_pris:
            nom = "nouvelle_%s_%d" % (carre(x, z, gx0, gz0).lower(), s)
            s += 1
        noms_pris.add(nom)
        y = int(hy[j, i]) + 1 if hy is not None and hy[j, i] > -32768 else None
        proche = min(comptees, key=lambda q: math.hypot(q["x"] - x, q["z"] - z)) if comptees else None
        propositions.append({"nom": nom, "x": x, "y": y, "z": z, "carre": carre(x, z, gx0, gz0),
                             "loin": float(dd[j, i]), "proche": proche,
                             "mode": "rotation", "libelle": "", "dimension": dimension})
        d = np.minimum(d, np.hypot(cx[None, :] - x, cz[:, None] - z))
    d_apres = d
    part_trou_apres = float((terre & (d_apres > trou)).sum()) / n_terre

    # ------------------------------------------------ tirage du serveur : simulation, meilleure distance possible
    n_ouv = max(int(reglages["openAtOnce"]), int(reglages["openAtOnceMax"]))
    sim = simuler(pool, permanentes, reglages, o["simulations"]) if pool else None
    meilleure, lot = meilleure_distance(pool, min(n_ouv, len(pool))) if len(pool) >= 2 else (None, [])
    sim_apres = simuler(pool + propositions, permanentes, reglages, o["simulations"]) if pool and propositions else None

    # ------------------------------------------------ images
    if fond.ok:
        base = fond.vues.get("satellite", next(iter(fond.vues.values()))).resize((v.w, v.h), Image.LANCZOS)
    else:
        base = Image.new("RGB", (v.w, v.h), VIDE)
    carte = Image.blend(base, Image.new("RGB", base.size, (0, 0, 0)), 0.25).convert("RGBA")
    grille(carte, v, gx0, gz0)
    dessiner_zones(carte, v, ici, ouvertes_noms, propositions)
    legende(carte, [("zone en rotation", COULEURS_MODE["rotation"]), ("zone permanente", COULEURS_MODE["permanent"]),
                    ("fermée hors ouverture à la main", COULEURS_MODE["closed"]),
                    ("point proposé (à vérifier en jeu)", JAUNE),
                    ("cercle blanc : ouverte à la sauvegarde", None), ("traits : distance aux 2 plus proches", None)])
    enregistrer(carte, sortie / "carte_extractions.png")

    rel = fond.vues.get("relief", base) if fond.ok else base
    rel = rel.resize((v.w, v.h), Image.LANCZOS).convert("L").convert("RGB")
    couleur = np.zeros((nz, nx, 4), np.uint8)
    paliers = [(trou * 0.5, (60, 190, 90, 110)), (trou * 0.75, (230, 210, 60, 120)), (trou, (240, 140, 40, 140)),
               (np.inf, (225, 50, 50, 170))]
    bas = 0.0
    for haut, c in paliers:
        m = terre & (d_toutes > bas) & (d_toutes <= haut)
        couleur[m] = c
        bas = haut
    couv = Image.alpha_composite(rel.convert("RGBA"), Image.fromarray(couleur, "RGBA").resize((v.w, v.h), Image.NEAREST))
    grille(couv, v, gx0, gz0)
    dessiner_zones(couv, v, comptees, ouvertes_noms, propositions, voisins=False)
    legende(couv, [("à moins de %d blocs d'une extraction" % (trou * 0.5), (60, 190, 90)),
                   ("%d à %d blocs" % (trou * 0.5, trou * 0.75), (230, 210, 60)),
                   ("%d à %d blocs" % (trou * 0.75, trou), (240, 140, 40)),
                   ("plus de %d blocs : mal couvert" % trou, (225, 50, 50)),
                   ("point proposé", JAUNE)])
    enregistrer(couv, sortie / "couverture.png")

    # ------------------------------------------------ zones.json (pour comparer d'une fois sur l'autre)
    (sortie / "zones.json").write_text(json.dumps({"zones": zones, "vagueFin": vague_fin, "vague": vague,
                                                   "heureJeu": maintenant}, indent=2, ensure_ascii=False) + "\n",
                                       encoding="utf-8")

    # ------------------------------------------------ rapport
    L = []
    L.append("# Extractions de la carte")
    L.append("")
    L.append("Fait le %s depuis le monde en service (lecture seule ; rien n'a été écrit sur le serveur)." % time.strftime("%d/%m/%Y"))
    L.append("")
    modes = {m: sum(1 for z in zones if z["mode"] == m) for m in ("rotation", "permanent", "closed")}
    L.append("- **%d zone(s)** posées : %d en rotation, %d permanente(s), %d fermée(s) hors ouverture à la main%s."
             % (len(zones), modes["rotation"], modes["permanent"], modes["closed"],
                (" ; %d dans une autre dimension que %s (pas sur la carte)" % (len(ailleurs), dimension)) if ailleurs else ""))
    regl = "%s zone(s) ouvertes à la fois, %s à %s min, %s, distance minimale %s blocs, %s" % (
        reglages["openAtOnce"] if reglages["openAtOnce"] == reglages["openAtOnceMax"]
        else "%s à %s" % (reglages["openAtOnce"], reglages["openAtOnceMax"]),
        reglages["minMinutes"], reglages["maxMinutes"], "par vagues" if reglages["waves"] else "chaque zone à son tour",
        reglages["minDistance"], "une par quart de la carte" if reglages["spread"] else "sans quarts")
    L.append("- Réglages de la rotation : %s%s." % (regl, ("" if not o["config"] or not Path(o["config"]).is_file() else
                                                     (" (absents du fichier du serveur, valeurs du mod 1.5.0 : %s)" % ", ".join(absents)) if absents else " (fichier du serveur)")))
    if maintenant is not None:
        L.append("- Zones ouvertes à la dernière sauvegarde du monde : %s." % (", ".join(sorted(ouvertes_noms)) or "aucune"))
    if vague:
        L.append("- Dernière vague : %s%s." % (", ".join(vague), (" (fin dans %d min)" % ((vague_fin - maintenant) // 1200))
                                               if maintenant and vague_fin > maintenant else ""))
    L.append("")
    L.append("![Carte des extractions](carte_extractions.png)")
    L.append("")
    L.append("## Les zones")
    L.append("")
    L.append("| # | Nom | Affiché | Mode | X | Y | Z | Rayon | À tenir | Carré | Plus proche (distance) | Ouverte |")
    L.append("|---|---|---|---|---:|---:|---:|---:|---:|---|---|---|")
    for n, z in enumerate(sorted(zones, key=lambda q: (q["dimension"], q["nom"])), 1):
        voisines = [q for q in zones if q is not z and q["dimension"] == z["dimension"]]
        pr = min(voisines, key=lambda q: dist(z, q)) if voisines else None
        L.append("| %d | `%s` | %s | %s | %d | %d | %d | %.0f | %d s | %s | %s | %s |" % (
            n, z["nom"], affiche(z), z["mode"] + ("" if z["dimension"] == dimension else " (" + z["dimension"] + ")"),
            math.floor(z["x"]), math.floor(z["y"]), math.floor(z["z"]), z["rayon"], z["secondes"] or temps_defaut,
            carre(z["x"], z["z"], gx0, gz0) if z["dimension"] == dimension else "-",
            ("%s (%d m)" % (pr["nom"], round(dist(z, pr)))) if pr else "-",
            "oui" if z["nom"] in ouvertes_noms else ("?" if maintenant is None else "non")))
    if not zones:
        L.append("| | aucune zone posée | | | | | | | | | | |")
    L.append("")
    L.append("## Répartition")
    L.append("")
    if comptees:
        proches = [min(dist(a, b) for b in comptees if b is not a) for a in comptees if len(comptees) > 1]
        if proches:
            L.append("- Distance à la voisine la plus proche : %d m au plus près, %d m en médiane, %d m au plus loin."
                     % (min(proches), mediane(proches), max(proches)))
        doublons = [(a["nom"], b["nom"]) for n, a in enumerate(comptees) for b in comptees[n + 1:] if dist(a, b) < 16]
        if doublons:
            L.append("- **Zones au même endroit** (une seule suffit, retirer l'autre avec `/extraction remove <nom>`) : %s."
                     % ", ".join("`%s` et `%s`" % d for d in doublons))
        if bordure:
            hors = [z["nom"] for z in comptees if not (bordure[0] <= z["x"] <= bordure[2] and bordure[1] <= z["z"] <= bordure[3])]
            L.append("- Bordure du monde : x %d à %d, z %d à %d ; %s." % (bordure[0], bordure[2], bordure[1], bordure[3],
                     ("**hors de la bordure** : " + ", ".join("`%s`" % n for n in hors)) if hors else "toutes les zones sont dedans"))
        c = centre(pool or comptees)
        par_q = [sum(1 for z in (pool or comptees) if quart(z, c) == q) for q in range(4)]
        L.append("- Quarts de la carte (autour du centre des zones en rotation, x %d, z %d, comme le serveur) : %s."
                 % (c[0], c[1], ", ".join("%s %d" % (NOMS_QUARTS[q], par_q[q]) for q in range(4))))
    L.append("- Couverture : **%.0f %%** de la terre de la carte est à plus de %d blocs de toute extraction (rotation et "
             "permanentes) ; %.0f %% en ne comptant que la rotation. Avec les points proposés : %.0f %%. Voir la "
             "[carte de couverture](couverture.png)." % (100 * part_trou, trou, 100 * part_trou_rot, 100 * part_trou_apres))
    L.append("")
    L.append("![Couverture](couverture.png)")
    L.append("")
    L.append("## Le tirage du serveur (simulé)")
    L.append("")
    if sim:
        nv = o["simulations"]
        ds = sim["distances"]
        L.append("%d vagues de %s zones tirées comme le fait le serveur (HeraliZ Extraction 1.5.0, `Repartition`) avec "
                 "les zones en rotation d'aujourd'hui :" % (nv, n_ouv))
        L.append("")
        L.append("- distance minimale de %d blocs tenue : %.0f %% des vagues ; distance tenue en médiane %d blocs, au "
                 "pire %d blocs ;" % (int(reglages["minDistance"]), 100 * sum(1 for x in ds if x >= int(reglages["minDistance"])) / nv,
                                      mediane(ds), min(ds)))
        L.append("- une zone par quart tenue : %.0f %% ; aucune zone de la vague précédente reprise : %.0f %% ; "
                 "vagues complètes : %.0f %% ;" % (100 * sim["quarts"] / nv, 100 * sim["evitees"] / nv, 100 * sim["completes"] / nv))
        if meilleure is not None:
            L.append("- meilleure distance possible entre %d zones ouvertes ensemble : **%d blocs** (%s)."
                     % (len(lot), meilleure, ", ".join(z["nom"] for z in lot)))
        fois = sim["fois"]
        if fois:
            jamais = [k for k, x in fois.items() if x == 0]
            tri = sorted(((k, x) for k, x in fois.items() if x > 0), key=lambda kv: -kv[1])
            moyenne = sum(fois.values()) / max(1, len(fois))
            if tri:
                L.append("- ouvertures par zone (sur %d vagues, %.0f en moyenne) : la plus tirée `%s` %d fois, la moins "
                         "tirée `%s` %d fois ;" % (nv, moyenne, tri[0][0], tri[0][1], tri[-1][0], tri[-1][1]))
            for k in jamais:
                z = next(q for q in pool if q["nom"] == k)
                gene = [q for q in permanentes if dist(z, q) < int(reglages["minDistance"])]
                L.append("- **jamais tirée : `%s`**%s." % (k, (" : trop près de la zone permanente %s (toujours ouverte, "
                                                               "moins de %s blocs)" % (", ".join("`%s`" % q["nom"] for q in gene),
                                                                                    reglages["minDistance"])) if gene else ""))
        if sim_apres:
            ds2 = sim_apres["distances"]
            L.append("- avec les points proposés en plus : distance tenue %.0f %% des vagues (médiane %d blocs), une par "
                     "quart %.0f %%." % (100 * sum(1 for x in ds2 if x >= int(reglages["minDistance"])) / nv, mediane(ds2),
                                         100 * sim_apres["quarts"] / nv))
    else:
        L.append("Aucune zone en rotation dans %s : rien à tirer." % dimension)
    L.append("")
    L.append("## Points proposés pour combler les trous")
    L.append("")
    L.append("Choisis un par un là où la carte est le plus loin de toute extraction (au moins %d blocs), à %d blocs au "
             "moins du bord ; méthode : %s. **Tous à vérifier en jeu** avant de les créer (accès, pas dans une zone "
             "dangereuse ni un bâtiment, endroit intéressant). Un point qui ne va pas : en choisir un autre près de lui "
             "et taper `/extraction create <nom>` debout à cet endroit." % (trou, o["marge"], source_points))
    L.append("")
    if propositions:
        L.append("| Nom | X | Y | Z | Carré | Loin de tout (m) | Zone la plus proche | Aller voir | Créer |")
        L.append("|---|---:|---:|---:|---|---:|---|---|---|")
        for p in propositions:
            y = str(p["y"]) if p["y"] is not None else "?"
            voir = "`/execute in %s run tp @s %d %s %d`" % (dimension, p["x"], p["y"] + 1 if p["y"] is not None else 200, p["z"])
            if p["y"] is not None:
                creer = "`/execute in %s positioned %d %d %d run extraction create %s`" % (dimension, p["x"], p["y"], p["z"], p["nom"])
            else:
                creer = ("`/execute in %s positioned %d 0 %d positioned over motion_blocking_no_leaves run extraction create %s`"
                         " (terrain chargé : être sur place)" % (dimension, p["x"], p["z"], p["nom"]))
            L.append("| `%s` | %d | %s | %d | %s | %s | %s | %s | %s |" % (
                p["nom"], p["x"], y, p["z"], p["carre"], "-" if math.isinf(p["loin"]) else "%d" % p["loin"],
                ("%s (%d m)" % (p["proche"]["nom"], round(math.hypot(p["proche"]["x"] - p["x"], p["proche"]["z"] - p["z"]))))
                if p["proche"] else "-", voir, creer))
        L.append("")
        L.append("Ensuite : `/extraction label <nom> <nom affiché>` pour lui donner un nom de lieu (« Ferme du col »).")
    else:
        L.append("Aucun : partout sur la carte, une extraction est à moins de %d blocs." % trou)
    L.append("")
    L.append("Rien n'a été testé en jeu.")
    (sortie / "rapport.md").write_text("\n".join(L) + "\n", encoding="utf-8")
    print("[extractions] %d zone(s), %d point(s) proposé(s), %.0f %% de la carte à plus de %d blocs -> %s"
          % (len(zones), len(propositions), 100 * part_trou, trou, sortie))


if __name__ == "__main__":
    main()
