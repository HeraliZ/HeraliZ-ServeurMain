"""Recherche de la grotte du spawn (celle de la cinématique, avec la voie et l'épave du train) dans une COPIE d'un monde.

LECTURE SEULE : les fichiers du monde copié ne sont jamais modifiés. Sert au workflow « Grotte du spawn »
(.github/workflows/grotte_spawn.yml), qui copie depuis le Ptero l'ancienne grande carte mise de côté, puis recopie la
grotte trouvée dans le monde du spawn (outils/monde_spawn.py copier).

    python outils/recherche_grotte.py indices <monde> [<monde>...] --sortie DOSSIER [--prefixe CHEMIN_PTERO]
        Point d'apparition (level.dat), dimensions déclarées, réglages de la cinématique gardés dans le monde
        (data/heraliz_cinematique.dat : réveil, regard, épave, monde), réseau de train du monde, et le réveil par défaut
        du mod (heralisz_utils_cinematique.json). Écrit DOSSIER/candidats.json et DOSSIER/cibles.lftp (commandes lftp
        qui copient seulement les régions autour de chaque point).
    python outils/recherche_grotte.py analyser --candidats FICHIER --sortie DOSSIER
        Pour chaque point : charge une boîte de ±256 blocs sur toute la hauteur, repère les cavités (air couvert par un
        plafond, d'un seul tenant : portes, rails, lanternes, chaînes… laissent passer), les classe (voie, lanternes,
        barrières de l'épave, volume, ouvertures vers le ciel), fait des rendus (vue de dessus de la boîte, vue de
        dessus « plafond retiré » et coupes de chaque cavité), et choisit la grotte : résultat dans DOSSIER/resultat.json
        et DOSSIER/copie.env (zone, dimension, décalage pour monde_spawn.py copier).
    python outils/recherche_grotte.py balayer <dossier de régions> --etat FICHIER.jsonl
        Balayage rapide de fichiers de régions (palettes seulement) : sections qui contiennent des rails, des lanternes,
        des chaînes, des barrières, des blocs de nos mods. Ajoute une ligne par chunk intéressant à FICHIER.jsonl.
    python outils/recherche_grotte.py synthese --etat FICHIER.jsonl --monde <copie> --prefixe CHEMIN_PTERO --sortie DOSSIER
        Regroupe les chunks balayés en amas, dessine la carte des indices, écrit les meilleurs amas en nouveaux points
        (DOSSIER/candidats_balayage.json) et les régions à copier (DOSSIER/cibles_balayage.lftp).

Tout le compte rendu s'ajoute à DOSSIER/rapport.md (en français, pour le résumé du workflow). Besoin : numpy, scipy,
Pillow.
"""
import argparse
import gzip
import json
import math
import os
import sys
import time
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import monde_spawn as ms  # noqa: E402

DEPOT = Path(__file__).resolve().parent.parent
Y_MIN, Y_MAX = -64, 319
RAYON = 256                    # demi-côté de la boîte analysée autour d'un point (blocs)
CIBLE = None                   # réveil attendu par la cinématique (heralisz_utils_cinematique.json)
MARGE = 8                      # roche gardée autour de la grotte dans la zone copiée


def cine_json():
    return json.loads(ms.CINE_JSON.read_text(encoding="utf-8"))


def rapport(sortie, texte):
    os.makedirs(sortie, exist_ok=True)
    with open(os.path.join(sortie, "rapport.md"), "a", encoding="utf-8") as f:
        f.write(texte.rstrip("\n") + "\n\n")


def lire_nbt_fichier(f):
    d = open(f, "rb").read()
    try:
        d = gzip.decompress(d)
    except OSError:
        pass
    return ms.nbt_lire(d)


def en_python(t, prof=0):
    """Étiquette NBT -> objets Python (listes longues abrégées)."""
    if prof > 12:
        return "…"
    if t.t == 10:
        return {k: en_python(v, prof + 1) for k, v in t.v.items()}
    if t.t == 9:
        l = t.v[1]
        return [en_python(x, prof + 1) for x in l[:64]] + (["… %d de plus" % (len(l) - 64)] if len(l) > 64 else [])
    if t.t in (7, 11, 12):
        return "[%d valeurs]" % len(t.v)
    return t.v


def dossier_dim(monde, dim):
    return ms.dossier_dimension(monde, dim)


def rel_dim(dim):
    """Chemin du dossier de la dimension, relatif au monde."""
    if dim in ("minecraft:overworld", "overworld"):
        return ""
    if dim in ("minecraft:the_nether", "the_nether"):
        return "DIM-1/"
    if dim in ("minecraft:the_end", "the_end"):
        return "DIM1/"
    ns, _, nom = dim.partition(":")
    return "dimensions/%s/%s/" % (ns, nom)


def regions_autour(x, z, rayon=RAYON):
    out = []
    for rx in range((x - rayon) >> 9, ((x + rayon) >> 9) + 1):
        for rz in range((z - rayon) >> 9, ((z + rayon) >> 9) + 1):
            out.append((rx, rz))
    return out


def cmd_lftp(prefixe, monde_local, dim, regions, avec_entites=True):
    """Commandes lftp (mirror avec un filtre par fichier) qui copient ces régions d'un monde du Ptero."""
    lignes = []
    noms = sorted(set("r.%d.%d.mca" % r for r in regions))
    for sous in (["region", "entities"] if avec_entites else ["region"]):
        src = "%s/%s%s" % (prefixe.rstrip("/"), rel_dim(dim), sous)
        dst = "%s/%s%s" % (monde_local.rstrip("/"), rel_dim(dim), sous)
        for i in range(0, len(noms), 40):
            filtres = " ".join("-I '%s'" % n for n in noms[i:i + 40])
            lignes.append("mirror -P 2 --no-empty-dirs %s '%s' '%s'" % (filtres, src, dst))
    return lignes


# ================================================================== indices
def indices(args):
    global CIBLE
    sortie = args.sortie
    cj = cine_json()
    CIBLE = cj["reveil"]
    candidats = []
    lftp = []
    texte = ["## Indices des mondes copiés", ""]
    texte.append("Réveil attendu par la cinématique du mod (heralisz_utils_cinematique.json) : **%s %s %s**, regard %s°, "
                 "monde `%s`." % (CIBLE[0], CIBLE[1], CIBLE[2], cj.get("cap", 0), cj.get("monde", "minecraft:overworld")))
    for i, monde in enumerate(args.mondes):
        nom = Path(monde).name
        prefixe = (args.prefixe or "/") .rstrip("/") + "/" + nom
        texte.append("")
        texte.append("### Monde `%s`" % nom)
        dims = ["minecraft:overworld"]
        lv = Path(monde, "level.dat")
        spawn = None
        if lv.exists():
            try:
                D = lire_nbt_fichier(lv)["Data"]
                spawn = (D["SpawnX"].v, D["SpawnY"].v, D["SpawnZ"].v)
                texte.append("- point d'apparition (level.dat) : **%d %d %d**, nom « %s », dernière partie %s" % (
                    spawn + (D.get("LevelName").v if D.get("LevelName") else "?",
                             time.strftime("%Y-%m-%d %H:%M", time.gmtime(D["LastPlayed"].v / 1000)) if D.get("LastPlayed") else "?")))
                wg = D.get("WorldGenSettings")
                if wg is not None and wg.get("dimensions") is not None:
                    dd = wg["dimensions"].v
                    texte.append("- dimensions déclarées : %s" % ", ".join("`%s`" % k for k in dd))
                    for k, v in dd.items():
                        gen = v.get("generator")
                        if gen is not None and gen.get("type") is not None:
                            texte.append("  - `%s` : générateur `%s`" % (k, gen["type"].v))
                dp = D.get("DataPacks")
                if dp is not None and dp.get("Enabled") is not None:
                    texte.append("- packs de données : %s" % ", ".join("`%s`" % x.v for x in dp["Enabled"].v[1]))
            except Exception as e:
                texte.append("- level.dat illisible : %s" % e)
        else:
            texte.append("- pas de level.dat")
        # dimensions présentes sur le Ptero (liste écrite par le workflow) ou dans la copie
        ld = Path(monde, "_liste_dimensions.txt")
        noms_dims = set()
        if ld.exists():
            for l in ld.read_text(encoding="utf-8", errors="replace").splitlines():
                p = [x for x in l.strip().split("/") if x]
                if len(p) >= 2:
                    noms_dims.add("%s:%s" % (p[-2], p[-1]))
        for f in Path(monde).glob("dimensions/*/*"):
            noms_dims.add("%s:%s" % (f.parent.name, f.name))
        dims += sorted(noms_dims)
        if noms_dims:
            texte.append("- dimensions sur le disque : %s" % ", ".join("`%s`" % d for d in sorted(noms_dims)))
        data = Path(monde, "data")
        if data.is_dir():
            noms = sorted(p.name for p in data.iterdir())
            texte.append("- fichiers de données copiés (data/) : %s" % (", ".join("`%s`" % n for n in noms) or "aucun"))
        liste = Path(monde, "_liste_data.txt")
        if liste.exists():
            tous = [l.strip() for l in liste.read_text(encoding="utf-8", errors="replace").splitlines() if l.strip()]
            texte.append("- tous les fichiers de data/ sur le Ptero (%d) : %s" % (
                len(tous), ", ".join("`%s`" % n for n in tous[:80]) + (" …" if len(tous) > 80 else "")))
        lr = Path(monde, "_liste_regions.txt")
        if lr.exists():
            rs = []
            taille = 0
            for l in lr.read_text(encoding="utf-8", errors="replace").splitlines():
                p = l.split()
                if p and p[-1].endswith(".mca"):
                    try:
                        _, x, z, _ = p[-1].split("/")[-1].split(".")
                        rs.append((int(x), int(z)))
                        nombres = [int(v) for v in p[:-1] if v.isdigit()]
                        taille += max(nombres) if nombres else 0
                    except ValueError:
                        pass
            if rs:
                xs, zs = [r[0] for r in rs], [r[1] for r in rs]
                texte.append("- monde principal : %d régions, de x %d à %d et z %d à %d (blocs), %.1f Go" % (
                    len(rs), min(xs) * 512, max(xs) * 512 + 511, min(zs) * 512, max(zs) * 512 + 511, taille / 1e9))
        # la cinématique telle que réglée en jeu sur ce monde
        cdat = Path(monde, "data", "heraliz_cinematique.dat")
        if cdat.exists():
            try:
                t = lire_nbt_fichier(cdat)["data"]
                if t.get("reveil") is not None:
                    r = t["reveil"]
                    mon = t["monde"].v if t.get("monde") is not None else "minecraft:overworld"
                    rv = (math.floor(r["x"].v), math.floor(r["y"].v), math.floor(r["z"].v))
                    texte.append("- **cinématique réglée en jeu** (data/heraliz_cinematique.dat) : réveil **%d %d %d**, regard "
                                 "%.0f°, monde `%s`" % (rv + (r["cap"].v, mon)))
                    ep = [(p["modele"].v, p["x"].v, p["y"].v, p["z"].v, p["lacet"].v, p["roulis"].v) for p in t["epave"].v[1]]
                    for p in ep:
                        texte.append("  - épave : %s en %.1f %.1f %.1f, lacet %.0f°, roulis %.0f°" % p)
                    nb = len(t["barrieres"].v) if t.get("barrieres") is not None else 0
                    texte.append("  - barrières posées par /cinematique epave : %d" % nb)
                    candidats.append({"nom": "cinematique_%s" % nom, "source": "réveil réglé en jeu (heraliz_cinematique.dat)",
                                      "reveil": True, "monde": monde, "prefixe": prefixe, "dim": mon,
                                      "x": rv[0], "y": rv[1], "z": rv[2], "cap": r["cap"].v, "epave": ep})
                else:
                    texte.append("- data/heraliz_cinematique.dat : rien de réglé (décor par défaut du mod)")
            except Exception as e:
                texte.append("- data/heraliz_cinematique.dat illisible : %s" % e)
        tdat = Path(monde, "data", "heralisz_train.dat")
        if tdat.exists():
            try:
                texte.append("- train (data/heralisz_train.dat) : `%s`" % json.dumps(en_python(lire_nbt_fichier(tdat)), ensure_ascii=False)[:600])
            except Exception as e:
                texte.append("- data/heralisz_train.dat illisible : %s" % e)
        for f in sorted(data.glob("heral*")) if data.is_dir() else []:
            if f.name in ("heraliz_cinematique.dat", "heralisz_train.dat") or f.suffix != ".dat":
                continue
            # seulement la forme (clés, tailles) : ces fichiers peuvent contenir des pseudos de joueurs
            try:
                t = lire_nbt_fichier(f)
                d = t.get("data") if t.get("data") is not None else t
                cles = ["%s%s" % (k, "[%d]" % len(v.v[1]) if v.t == 9 else "{%d}" % len(v.v) if v.t == 10 else "")
                        for k, v in list(d.v.items())[:20]] if d.t == 10 else []
                texte.append("- `%s` (%d octets) : %s" % (f.name, f.stat().st_size, ", ".join(cles) or "?"))
            except Exception as e:
                texte.append("- `%s` illisible : %s" % (f.name, e))
        candidats.append({"nom": "reveil_mod_%s" % nom, "source": "réveil par défaut du mod (-61 265 2), monde principal",
                          "reveil": True, "monde": monde, "prefixe": prefixe, "dim": "minecraft:overworld",
                          "x": CIBLE[0], "y": CIBLE[1], "z": CIBLE[2], "cap": cj.get("cap", 0)})
        for d in dims:
            if "spawn" in d and d != "minecraft:overworld":
                candidats.append({"nom": "reveil_%s_%s" % (d.replace(":", "_"), nom), "source": "réveil par défaut dans la dimension %s" % d,
                                  "reveil": True, "monde": monde, "prefixe": prefixe, "dim": d,
                                  "x": CIBLE[0], "y": CIBLE[1], "z": CIBLE[2], "cap": cj.get("cap", 0)})
        if spawn:
            candidats.append({"nom": "apparition_%s" % nom, "source": "point d'apparition du level.dat", "reveil": False,
                              "apparition": True, "monde": monde, "prefixe": prefixe, "dim": "minecraft:overworld",
                              "x": spawn[0], "y": spawn[1], "z": spawn[2]})
    # régions à copier (sans doublon)
    vus = set()
    for c in candidats:
        regs = [r for r in regions_autour(c["x"], c["z"]) if (c["monde"], c["dim"], r) not in vus]
        vus.update((c["monde"], c["dim"], r) for r in regs)
        if regs:
            lftp += cmd_lftp(c["prefixe"], c["monde"], c["dim"], regs)
    texte.append("")
    texte.append("Points étudiés : " + "; ".join("`%s` (%s) en %d %d %d" % (c["nom"], c["source"], c["x"], c["y"], c["z"]) for c in candidats))
    os.makedirs(sortie, exist_ok=True)
    Path(sortie, "candidats.json").write_text(json.dumps(candidats, ensure_ascii=False, indent=1), encoding="utf-8")
    Path(sortie, "cibles.lftp").write_text("\n".join(lftp) + "\n", encoding="utf-8")
    rapport(sortie, "\n".join(texte))
    print("\n".join(texte))


# ================================================================== classement des blocs
FLEURS = {"dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip",
          "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "wither_rose", "sunflower", "lilac", "rose_bush",
          "peony", "torchflower", "pink_petals", "spore_blossom"}
PASSE_NOMS = {"air", "cave_air", "void_air", "light", "water", "bubble_column", "chain", "ladder", "vine", "cobweb", "snow",
              "grass", "tall_grass", "fern", "large_fern", "dead_bush", "redstone_wire", "tripwire", "tripwire_hook", "lever",
              "scaffolding", "pointed_dripstone", "hanging_roots", "glow_lichen", "sculk_vein", "moss_carpet", "lantern",
              "soul_lantern", "torch", "wall_torch", "soul_torch", "soul_wall_torch", "redstone_torch", "redstone_wall_torch",
              "end_rod", "lightning_rod", "flower_pot", "candle", "sugar_cane", "structure_void", "rail", "seagrass",
              "tall_seagrass", "kelp", "kelp_plant", "weeping_vines", "weeping_vines_plant", "twisting_vines",
              "twisting_vines_plant", "cave_vines", "cave_vines_plant", "small_dripleaf", "brown_mushroom", "red_mushroom",
              "string", "item_frame", "nether_portal", "fire", "soul_fire", "comparator", "repeater"} | FLEURS
PASSE_FINS = ("_rail", "_door", "_trapdoor", "_fence_gate", "_fence", "_button", "_pressure_plate", "_sign", "_banner",
              "_carpet", "_candle", "_sapling", "_head", "_skull", "_coral_fan", "_wall_fan", "_tulip", "_torch", "_pane_x")
MOT_MOD = ("lamp", "lampe", "lantern", "lanterne", "torch", "torche", "rail", "sign", "panneau", "cable", "wire", "chain",
           "chaine", "door", "porte", "grillage", "fence", "guirlande", "sang", "blood", "tache", "light", "lumiere", "carpet",
           "tapis", "rideau", "curtain", "plant", "plante", "herbe", "grass", "flower", "fleur")
VIDE_NOMS = {"air", "cave_air", "void_air", "light"}

# catégories (pour compter ce qu'il y a dans une cavité)
K_AUTRE, K_RAIL, K_LANTERNE, K_CHAINE, K_BARRIERE, K_PORTE, K_NOTRE, K_ASC = range(8)
NOMS_K = {K_RAIL: "rails", K_LANTERNE: "lanternes", K_CHAINE: "chaînes", K_BARRIERE: "barrières", K_PORTE: "portes",
          K_NOTRE: "blocs HeraliZ", K_ASC: "ascenseur"}


def passe(nom):
    ns, _, b = nom.partition(":")
    if ns == "minecraft":
        return b in PASSE_NOMS or b.endswith(PASSE_FINS) or b.endswith("_wall_sign") or b.endswith("hanging_sign")
    return any(m in b for m in MOT_MOD)


def categorie(nom):
    ns, _, b = nom.partition(":")
    if b.endswith("rail") and ("rail" in b):
        return K_RAIL
    if b in ("lantern", "soul_lantern") or (ns != "minecraft" and any(m in b for m in ("lamp", "lantern", "lanterne", "lampe"))):
        return K_LANTERNE
    if b == "chain" or b.startswith("chaine"):
        return K_CHAINE
    if b == "barrier":
        return K_BARRIERE
    if ns.startswith("heralisz_utils") and ("ascenseur" in b or "palier" in b or "machine" in b):
        return K_ASC
    if ns.startswith("heralisz") or ns.startswith("heraliz"):
        return K_NOTRE
    if b.endswith("_door"):
        return K_PORTE
    return K_AUTRE


class Boite:
    """Blocs d'une boîte (x, y, z) d'une dimension copiée : identifiants dans une palette de noms (sans propriétés)."""

    def __init__(self, base, x0, x1, z0, z1, y0=Y_MIN, y1=Y_MAX):
        self.x0, self.x1, self.y0, self.y1, self.z0, self.z1 = x0, x1, y0, y1, z0, z1
        self.noms = ["minecraft:air"]
        idx = {"minecraft:air": 0}
        self.A = np.zeros((x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1), dtype=np.uint16)
        self.chunks = 0
        self.regions_manquantes = []
        for rx in range(x0 >> 9, (x1 >> 9) + 1):
            for rz in range(z0 >> 9, (z1 >> 9) + 1):
                f = Path(base, "region", "r.%d.%d.mca" % (rx, rz))
                if not f.exists():
                    self.regions_manquantes.append(f.name)
                    continue
                for (lx, lz), t in ms.region_lire(str(f)).items():
                    cx, cz = rx * 32 + lx, rz * 32 + lz
                    bx, bz = cx * 16, cz * 16
                    if bx + 15 < x0 or bx > x1 or bz + 15 < z0 or bz > z1 or t.get("sections") is None:
                        continue
                    self.chunks += 1
                    for sec in t["sections"].v[1]:
                        if sec.get("Y") is None:
                            continue
                        sy = sec["Y"].v
                        by = sy * 16
                        if by + 15 < y0 or by > y1:
                            continue
                        pal, ind = ms.decoder_section(sec)
                        if pal is None:
                            continue
                        loc = []
                        for p in pal:
                            n = p["Name"].v
                            if n not in idx:
                                idx[n] = len(self.noms)
                                self.noms.append(n)
                            loc.append(idx[n])
                        ids = np.array(loc, dtype=np.uint16)[np.clip(ind, 0, len(loc) - 1)].reshape(16, 16, 16).transpose(2, 0, 1)
                        ax0, ax1 = max(x0, bx), min(x1, bx + 15)
                        ay0, ay1 = max(y0, by), min(y1, by + 15)
                        az0, az1 = max(z0, bz), min(z1, bz + 15)
                        self.A[ax0 - x0:ax1 - x0 + 1, ay0 - y0:ay1 - y0 + 1, az0 - z0:az1 - z0 + 1] = \
                            ids[ax0 - bx:ax1 - bx + 1, ay0 - by:ay1 - by + 1, az0 - bz:az1 - bz + 1]
        self.passe_lut = np.array([passe(n) for n in self.noms], dtype=bool)
        self.vide_lut = np.array([n.startswith("minecraft:") and n.split(":")[1] in VIDE_NOMS for n in self.noms], dtype=bool)
        self.cat_lut = np.array([categorie(n) for n in self.noms], dtype=np.uint8)

    def local(self, x, y, z):
        return x - self.x0, y - self.y0, z - self.z0

    def dedans(self, i, j, k):
        nx, ny, nz = self.A.shape
        return 0 <= i < nx and 0 <= j < ny and 0 <= k < nz

    def nom(self, x, y, z):
        i, j, k = self.local(x, y, z)
        return self.noms[int(self.A[i, j, k])] if self.dedans(i, j, k) else "(hors de la boîte)"


def cavites(b):
    """Repère les cavités de la boîte : air (et blocs sans collision) couvert par un plafond, d'un seul tenant."""
    from scipy import ndimage
    A = b.A
    P = b.passe_lut[A]
    S = ~P
    ny = A.shape[1]
    has = S.any(axis=1)
    top = ny - 1 - S[:, ::-1, :].argmax(axis=1)
    top[~has] = -1
    couvert = np.arange(ny, dtype=np.int32)[None, :, None] < top[:, None, :]
    C = P & couvert
    ciel = P & ~couvert
    del couvert
    lab, n = ndimage.label(C)
    tailles = np.bincount(lab.ravel(), minlength=n + 1)
    # ouvertures : cellules de cavité voisines du ciel
    ouv = ndimage.binary_dilation(ciel) & C
    ouvertures = np.bincount(lab[ouv], minlength=n + 1)
    del ouv
    cat = b.cat_lut[A]
    comptes = {}
    for k in NOMS_K:
        if k == K_BARRIERE:
            continue
        comptes[k] = np.bincount(lab[cat == k], minlength=n + 1)
    # barrières (solides) : rattachées à la cavité voisine
    bar = np.zeros(n + 1, dtype=np.int64)
    ii, jj, kk = np.nonzero(cat == K_BARRIERE)
    if len(ii):
        voisins = np.zeros(len(ii), dtype=np.int64)
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            a = np.clip(ii + d[0], 0, A.shape[0] - 1)
            c = np.clip(jj + d[1], 0, A.shape[1] - 1)
            e = np.clip(kk + d[2], 0, A.shape[2] - 1)
            voisins = np.maximum(voisins, lab[a, c, e])
        # barrière sans cavité voisine directe (au milieu de l'épave) : rattachée par proximité plus bas
        bar += np.bincount(voisins, minlength=n + 1)
        bar[0] = 0
    comptes[K_BARRIERE] = bar
    objets = ndimage.find_objects(lab)
    return lab, n, tailles, ouvertures, comptes, objets, C, ciel


def score_cavite(c):
    return (3 * c["rails"] + 8 * c["lanternes"] + 2 * c["chaînes"] + 1 * c["barrières"] + 6 * c["blocs HeraliZ"]
            + 10 * c["ascenseur"] + min(c["volume"], 200000) / 2000.0 - (30 if c["bord"] else 0)
            - 40 * min(1.0, c["ouvertures"] / max(c["volume"], 1) * 20))


def decrire(b, lab, tailles, ouvertures, comptes, objets, num):
    sl = objets[num - 1]
    nx, ny, nz = b.A.shape
    # coupée par la boîte (ou trop près de son bord pour garder la marge de roche autour)
    bord = sl[0].start < MARGE or sl[0].stop > nx - MARGE or sl[2].start < MARGE or sl[2].stop > nz - MARGE
    c = {"num": int(num), "volume": int(tailles[num]), "ouvertures": int(ouvertures[num]), "bord": bool(bord),
         "x": [b.x0 + sl[0].start, b.x0 + sl[0].stop - 1], "y": [b.y0 + sl[1].start, b.y0 + sl[1].stop - 1],
         "z": [b.z0 + sl[2].start, b.z0 + sl[2].stop - 1]}
    for k, nom in NOMS_K.items():
        c[nom] = int(comptes[k][num])
    c["score"] = round(score_cavite(c), 1)
    return c


def point_sol(b, lab, num, centre=None):
    """Un point praticable (2 cellules libres, sol dessous) de la cavité, près du centre (ou du point donné)."""
    sl = lab == num
    P = b.passe_lut[b.A]
    pieds = sl & np.roll(sl, -1, axis=1) & np.roll(~P, 1, axis=1)
    pieds[:, 0, :] = False
    pieds[:, -1, :] = False
    ii, jj, kk = np.nonzero(pieds)
    if not len(ii):
        return None
    if centre is None:
        ci, ck = ii.mean(), kk.mean()
        cj = jj.min()
    else:
        ci, cj, ck = b.local(*centre)
    d = (ii - ci) ** 2 + (kk - ck) ** 2 + 4 * (jj - cj) ** 2
    m = int(d.argmin())
    return [int(b.x0 + ii[m]), int(b.y0 + jj[m]), int(b.z0 + kk[m])]


# ================================================================== rendus
def couleurs(noms):
    tx = ms.textures_blocs(noms)
    pal = np.array([c or (0, 0, 0) for c, t in tx], dtype=np.float64)
    for i, n in enumerate(noms):
        if tuple(pal[i]) == (180.0, 60.0, 180.0):     # pas de texture connue (blocs des mods) : teinte neutre
            h = sum(n.encode()) % 40
            pal[i] = (105 + h, 100 + h // 2, 92 + h // 3)
        k = categorie(n)
        if k == K_RAIL:
            pal[i] = (250, 215, 60)
        elif k == K_LANTERNE:
            pal[i] = (255, 150, 40)
        elif k == K_BARRIERE:
            pal[i] = (120, 200, 255)
        elif k == K_ASC:
            pal[i] = (255, 60, 200)
        elif n.endswith(":water"):
            pal[i] = (50, 90, 200)
    return pal


def sauver(im, chemin, taille_max=1400):
    from PIL import Image
    if max(im.size) > taille_max:
        k = taille_max / max(im.size)
        im = im.resize((max(1, int(im.width * k)), max(1, int(im.height * k))), Image.NEAREST)
    im = im.convert("RGB").quantize(colors=200, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    im.save(chemin, optimize=True)
    return chemin


def toile(img, e, titre):
    from PIL import Image
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    im = im.resize((im.width * e, im.height * e), Image.NEAREST)
    try:
        largeur_titre = int(ms.police(18).getlength(titre)) + 24
    except Exception:
        largeur_titre = 10 * len(titre)
    canvas = Image.new("RGB", (max(im.width, largeur_titre), im.height + 34), (10, 12, 16))
    canvas.paste(im, (0, 34))
    d = ms.legende(canvas, titre)
    return canvas, d


def rendu_boite(b, pal, cav_list, seed, chemin, titre):
    """Vue de dessus de toute la boîte (premier bloc visible depuis le ciel), cavités retenues encadrées."""
    A = b.A
    vis = ~b.vide_lut[A]
    nx, ny, nz = A.shape
    has = vis.any(axis=1)
    top = ny - 1 - vis[:, ::-1, :].argmax(axis=1)
    ids = A[np.arange(nx)[:, None], np.clip(top, 0, ny - 1), np.arange(nz)[None, :]]
    h = (top - top[has].mean()) / 40.0 if has.any() else top * 0
    img = pal[ids] * np.clip(0.8 + 0.4 * h, 0.4, 1.4)[..., None]
    img[~has] = (14, 16, 22)
    img = img.transpose(1, 0, 2)                         # lignes = z (nord en haut), colonnes = x
    e = 2
    canvas, d = toile(img, e, titre)
    f = ms.police(14)
    for c in cav_list:
        x0, x1 = (c["x"][0] - b.x0) * e, (c["x"][1] - b.x0 + 1) * e
        z0, z1 = (c["z"][0] - b.z0) * e + 34, (c["z"][1] - b.z0 + 1) * e + 34
        col = (255, 80, 80) if c.get("graine") else (80, 255, 140)
        d.rectangle((x0, z0, x1, z1), outline=col, width=2)
        d.text((x0 + 3, max(36, z0 - 16)), "#%d" % c["num"], fill=col, font=f)
    if seed is not None:
        sx, sz = (seed[0] - b.x0) * e, (seed[2] - b.z0) * e + 34
        d.line((sx - 8, sz, sx + 8, sz), fill=(255, 40, 40), width=2)
        d.line((sx, sz - 8, sx, sz + 8), fill=(255, 40, 40), width=2)
    return sauver(canvas, chemin)


def rendus_cavite(b, pal, lab, c, prefixe, reveil=None):
    """Vue de dessus « plafond retiré » de la cavité et deux coupes (le long et en travers de sa plus grande longueur)."""
    out = []
    A = b.A
    m = MARGE
    i0, i1 = max(0, c["x"][0] - b.x0 - m), min(A.shape[0], c["x"][1] - b.x0 + m + 1)
    j0, j1 = max(0, c["y"][0] - b.y0 - m), min(A.shape[1], c["y"][1] - b.y0 + m + 1)
    k0, k1 = max(0, c["z"][0] - b.z0 - m), min(A.shape[2], c["z"][1] - b.z0 + m + 1)
    sub = A[i0:i1, j0:j1, k0:k1]
    L = lab[i0:i1, j0:j1, k0:k1] == c["num"]
    vide = b.vide_lut[sub]
    nx, ny, nz = sub.shape
    # 1. dessus : sous le plus haut point de la cavité de chaque colonne, premier bloc visible
    img = np.zeros((nx, nz, 3))
    col = L.any(axis=1)
    haut = ny - 1 - L[:, ::-1, :].argmax(axis=1)
    for i in range(nx):
        for k in range(nz):
            if not col[i, k]:
                # roche autour : plafond vu du dessus, sombre
                v = np.nonzero(~vide[i, :, k])[0]
                img[i, k] = pal[sub[i, v.max(), k]] * 0.25 if len(v) else (8, 9, 12)
                continue
            j = haut[i, k]
            while j >= 0 and vide[i, j, k]:
                j -= 1
            if j < 0:
                img[i, k] = (0, 0, 40)
                continue
            prof = (haut[i, k] - j) / 12.0
            img[i, k] = pal[sub[i, j, k]] * np.clip(1.05 - 0.35 * prof, 0.45, 1.1)
    e = max(2, min(8, 1200 // max(nx, nz, 1)))
    canvas, d = toile(img.transpose(1, 0, 2), e, "Cavité #%d vue de dessus, plafond retiré (nord en haut) — x %d à %d, z %d à %d"
                      % (c["num"], b.x0 + i0, b.x0 + i1 - 1, b.z0 + k0, b.z0 + k1 - 1))
    if reveil is not None:
        rx, rz = (reveil[0] - b.x0 - i0) * e, (reveil[2] - b.z0 - k0) * e + 34
        d.ellipse((rx - 6, rz - 6, rx + e + 6, rz + e + 6), outline=(255, 70, 70), width=3)
        d.text((rx + e + 8, rz - 6), "réveil", fill=(255, 110, 110), font=ms.police(14))
    out.append(sauver(canvas, prefixe + "_dessus.png"))
    # 2. coupes : plan vertical au milieu de la cavité
    long_x = (c["x"][1] - c["x"][0]) >= (c["z"][1] - c["z"][0])
    for nomc, le_long in (("long", long_x), ("travers", not long_x)):
        if le_long:          # plan x-y à z médian de la cavité ; on regarde vers le nord (x vers la droite)
            kk = int(np.median(np.nonzero(L.any(axis=(0, 1)))[0]))
            pl, pv, pL = sub[:, :, kk], vide[:, :, kk], L[:, :, kk]
            axe = "z = %d (x de %d à %d de gauche à droite)" % (b.z0 + k0 + kk, b.x0 + i0, b.x0 + i1 - 1)
        else:                # plan z-y à x médian ; on regarde vers l'ouest (z vers la droite)
            ii = int(np.median(np.nonzero(L.any(axis=(1, 2)))[0]))
            pl, pv, pL = sub[ii, :, :].T, vide[ii, :, :].T, L[ii, :, :].T
            axe = "x = %d (z de %d à %d de gauche à droite)" % (b.x0 + i0 + ii, b.z0 + k0, b.z0 + k1 - 1)
        img = pal[pl].copy()
        img[pv] = (6, 7, 10)
        img[pL & pv] = (30, 46, 80)
        img = img[:, ::-1, :].transpose(1, 0, 2)       # lignes = y (haut en haut)
        e2 = max(2, min(8, 1200 // max(pl.shape[0], 1)))
        canvas, d = toile(img, e2, "Cavité #%d, coupe %s — %s, y %d à %d" % (c["num"], nomc, axe, b.y0 + j0, b.y0 + j1 - 1))
        out.append(sauver(canvas, prefixe + "_coupe_%s.png" % nomc))
    return out


# ================================================================== analyse
def analyser(args):
    sortie = args.sortie
    cj = cine_json()
    cible = cj["reveil"]
    candidats = json.loads(Path(args.candidats).read_text(encoding="utf-8"))
    res_f = Path(sortie, "resultat.json")
    resultats = json.loads(res_f.read_text(encoding="utf-8")) if res_f.exists() else {"analyses": []}
    deja = {a["nom"] for a in resultats["analyses"]}
    os.makedirs(Path(sortie, "rendus"), exist_ok=True)

    def un(c):
        t0 = time.time()
        base = dossier_dim(c["monde"], c["dim"])
        texte = ["### Point `%s`" % c["nom"], "", "%s — `%s` en **%d %d %d**." % (c["source"], c["dim"], c["x"], c["y"], c["z"])]
        an = {"nom": c["nom"], "candidat": c}
        if not Path(base, "region").is_dir():
            texte.append("Pas de régions copiées pour cette dimension (elle n'existe pas sur le Ptero).")
            an["etat"] = "absent"
            resultats["analyses"].append(an)
            rapport(sortie, "\n".join(texte))
            return
        b = Boite(base, c["x"] - RAYON, c["x"] + RAYON, c["z"] - RAYON, c["z"] + RAYON)
        if b.chunks == 0:
            texte.append("Aucun bloc à cet endroit (régions absentes : %s)." % ", ".join(b.regions_manquantes))
            an["etat"] = "vide"
            resultats["analyses"].append(an)
            rapport(sortie, "\n".join(texte))
            return
        lab, n, tailles, ouvertures, comptes, objets, C, ciel = cavites(b)
        cavs = []
        for num in np.nonzero(tailles >= 150)[0]:
            if num == 0:
                continue
            cavs.append(decrire(b, lab, tailles, ouvertures, comptes, objets, num))
        cavs.sort(key=lambda d: -d["score"])
        # le point lui-même
        i, j, k = b.local(c["x"], c["y"], c["z"])
        graine = None
        if b.dedans(i, j, k):
            texte.append("Au point : `%s` (pieds), `%s` (tête), `%s` (dessous)." % (
                b.nom(c["x"], c["y"], c["z"]), b.nom(c["x"], c["y"] + 1, c["z"]), b.nom(c["x"], c["y"] - 1, c["z"])))
            if lab[i, j, k]:
                graine = int(lab[i, j, k])
            elif ciel[i, j, k]:
                texte.append("Le point est **à ciel ouvert** (aucun plafond au-dessus).")
            elif not c.get("reveil"):
                texte.append("Le point est dans un bloc (point approché d'un amas : seules les cavités du tableau comptent).")
            else:
                # cavité la plus proche (12 blocs au plus)
                r = 12
                sl = lab[max(0, i - r):i + r + 1, max(0, j - r):j + r + 1, max(0, k - r):k + r + 1]
                ii, jj, kk = np.nonzero(sl)
                if len(ii):
                    d = (ii + max(0, i - r) - i) ** 2 + (jj + max(0, j - r) - j) ** 2 + (kk + max(0, k - r) - k) ** 2
                    m = int(d.argmin())
                    graine = int(sl[ii[m], jj[m], kk[m]])
                    texte.append("Le point est dans un bloc ; cavité la plus proche à %.1f blocs." % math.sqrt(d[m]))
        if graine is not None and tailles[graine] < 150:
            texte.append("Cavité du point : minuscule (%d blocs d'air)." % tailles[graine])
            graine = None
        cg = None
        if graine is not None:
            cg = next((x for x in cavs if x["num"] == graine), None)
            if cg is not None:
                cg["graine"] = True
        retenues = cavs[:6]
        if cg is not None and cg not in retenues:
            retenues = [cg] + retenues[:5]
        an["cavites"] = retenues
        an["cavite_du_point"] = cg
        an["chunks"] = b.chunks
        an["regions_manquantes"] = b.regions_manquantes
        texte.append("Boîte analysée : x %d à %d, z %d à %d, y %d à %d (%d chunks%s) ; %d cavités de 150 blocs ou plus."
                     % (b.x0, b.x1, b.z0, b.z1, b.y0, b.y1, b.chunks,
                        ", régions absentes : " + ", ".join(b.regions_manquantes) if b.regions_manquantes else "", len(cavs)))
        if retenues:
            texte.append("")
            texte.append("| # | volume | x | y | z | rails | lanternes | chaînes | barrières | blocs HeraliZ | ascenseur | ouvertures | coupée par la boîte | score |")
            texte.append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
            for x in retenues:
                texte.append("| %s%d | %d | %d à %d | %d à %d | %d à %d | %d | %d | %d | %d | %d | %d | %d | %s | %.0f |" % (
                    "**point** " if x.get("graine") else "", x["num"], x["volume"], x["x"][0], x["x"][1], x["y"][0], x["y"][1],
                    x["z"][0], x["z"][1], x["rails"], x["lanternes"], x["chaînes"], x["barrières"], x["blocs HeraliZ"],
                    x["ascenseur"], x["ouvertures"], "oui" if x["bord"] else "non", x["score"]))
        # blocs les plus présents dans la cavité du point (ou la meilleure)
        cref = cg or (retenues[0] if retenues else None)
        if cref is not None:
            m = MARGE
            i0, i1 = max(0, cref["x"][0] - b.x0 - m), cref["x"][1] - b.x0 + m + 1
            j0, j1 = max(0, cref["y"][0] - b.y0 - m), cref["y"][1] - b.y0 + m + 1
            k0, k1 = max(0, cref["z"][0] - b.z0 - m), cref["z"][1] - b.z0 + m + 1
            sub = b.A[i0:i1, j0:j1, k0:k1]
            cnt = np.bincount(sub.ravel(), minlength=len(b.noms))
            top = [(b.noms[q], int(cnt[q])) for q in np.argsort(-cnt)[:30] if cnt[q] and not b.vide_lut[q]]
            texte.append("")
            texte.append("Blocs autour de la cavité #%d (avec %d blocs de marge) : %s" % (
                cref["num"], MARGE, ", ".join("`%s` %d" % t for t in top[:24])))
            mods = sorted({b.noms[q] for q in np.nonzero(cnt)[0] if not b.noms[q].startswith("minecraft:")})
            if mods:
                texte.append("Blocs des mods : %s" % ", ".join("`%s`" % x for x in mods[:40]))
        # rendus
        pal = couleurs(b.noms)
        imgs = [rendu_boite(b, pal, retenues[:6], (c["x"], c["y"], c["z"]), str(Path(sortie, "rendus", "%s_boite.png" % c["nom"])),
                            "%s — boîte vue de dessus (nord en haut), cavités encadrées, point en rouge" % c["nom"])]
        for x in retenues[:3]:
            rv = [c["x"], c["y"], c["z"]] if x.get("graine") else None
            imgs += rendus_cavite(b, pal, lab, x, str(Path(sortie, "rendus", "%s_cavite%d" % (c["nom"], x["num"]))), rv)
        an["rendus"] = [os.path.relpath(p, sortie) for p in imgs]
        texte.append("")
        texte.append("Rendus : " + ", ".join("`%s`" % p for p in an["rendus"]))
        # zone à copier et décalage, pour la cavité du point (réveil) ou la meilleure
        an["etat"] = "analyse"
        choix = None

        def signee(x):
            return x["rails"] >= 5 or x["lanternes"] >= 3 or x["barrières"] >= 20 or x["ascenseur"] > 0

        fermee = cg is not None and not cg["bord"] and cg["volume"] >= 500 and cg["ouvertures"] <= 0.2 * cg["volume"]
        if fermee and (c.get("source", "").startswith("réveil réglé") or (c.get("reveil") and (signee(cg) or cg["volume"] >= 3000))
                       or signee(cg)):
            choix = (cg, "point")
        elif cavs and not cavs[0]["bord"] and cavs[0]["volume"] >= 1500 and cavs[0]["rails"] >= 8 \
                and (cavs[0]["lanternes"] >= 2 or cavs[0]["barrières"] >= 20):
            choix = (cavs[0], "signature")
        if choix is not None:
            cav, pourquoi = choix
            zone, incluses = zone_grotte(b, lab, cav, cavs)
            if pourquoi == "point" and c.get("reveil"):
                reveil_src = [c["x"], c["y"], c["z"]]
                confiance = "forte"
            else:
                reveil_src = point_sol(b, lab, cav["num"], (c["x"], c["y"], c["z"]) if pourquoi == "point" else None)
                confiance = "moyenne" if pourquoi == "point" else "faible"
                if reveil_src is None:          # aucun sol praticable trouvé : milieu de la cavité, à régler en jeu
                    reveil_src = [(cav["x"][0] + cav["x"][1]) // 2, cav["y"][0], (cav["z"][0] + cav["z"][1]) // 2]
            dec =[cible[0] - reveil_src[0], cible[1] - reveil_src[1], cible[2] - reveil_src[2]]
            note = None
            if zone[1] + dec[1] < Y_MIN or zone[4] + dec[1] > Y_MAX:
                # la grotte déplacée sortirait de la hauteur du monde : on garde l'altitude d'origine (y), le réveil change
                dec[1] = 0
                note = ("décalage vertical impossible (la grotte sortirait de la hauteur du monde) : réveil à mettre en y = %d "
                        "dans heralisz_utils_cinematique.json" % reveil_src[1])
            an["grotte"] = {"cavite": cav["num"], "pourquoi": pourquoi, "confiance": confiance, "zone": zone,
                            "cavites_incluses": incluses, "reveil_source": reveil_src, "decalage": dec, "note": note,
                            "dim": c["dim"], "monde": c["monde"], "score": cav["score"], "volume": cav["volume"]}
            texte.append("")
            texte.append("**Grotte retenue ici** (cavité #%d, confiance %s) : zone x %d à %d, y %d à %d, z %d à %d ; "
                         "réveil d'origine %s ; décalage %s pour que le réveil tombe en %s.%s" % (
                             cav["num"], confiance, zone[0], zone[3], zone[1], zone[4], zone[2], zone[5],
                             " ".join(map(str, reveil_src)), " ".join(map(str, dec)), " ".join(map(str, cible)),
                             (" " + note) if note else ""))
            # épave et regard réglés en jeu sur l'ancienne carte : comparés à ceux du mod, une fois décalés
            if c.get("epave") is not None:
                dep = [[p[0], round(p[1] + dec[0], 2), round(p[2] + dec[1], 2), round(p[3] + dec[2], 2), round(p[4], 1), round(p[5], 1)]
                       for p in c["epave"]]
                mod = [[p[0]] + [round(float(v), 2) for v in p[1:]] for p in cj.get("epave", [])]
                pareil = len(dep) == len(mod) and all(a[0] == b[0] and all(abs(x - y) < 0.05 for x, y in zip(a[1:], b[1:]))
                                                      for a, b in zip(dep, mod))
                an["grotte"]["epave_decalee"] = dep
                if not pareil or abs(float(c.get("cap", 0)) - float(cj.get("cap", 0))) > 0.5:
                    texte.append("")
                    texte.append("L'épave ou le regard réglés en jeu sur l'ancienne carte ne sont pas ceux du mod : pour que le "
                                 "train couché retombe à sa place dans la copie, mettre dans heralisz_utils_cinematique.json "
                                 "(HeraliZ Utils) `\"cap\": %.1f` et `\"epave\": %s`." % (
                                     float(c.get("cap", 0)), json.dumps(dep, ensure_ascii=False)))
                else:
                    texte.append("Épave et regard réglés en jeu : les mêmes que ceux du mod (rien à changer).")
        texte.append("")
        texte.append("_(analyse : %.0f s)_" % (time.time() - t0))
        resultats["analyses"].append(an)
        rapport(sortie, "\n".join(texte))
        print("\n".join(texte))
        del b, lab, C, ciel

    for c in candidats:
        if c["nom"] in deja:
            continue
        try:
            un(c)
        except Exception as e:      # un point illisible ne doit pas arrêter les autres
            import traceback
            traceback.print_exc()
            resultats["analyses"].append({"nom": c["nom"], "candidat": c, "etat": "erreur", "erreur": repr(e)})
            rapport(sortie, "### Point `%s`\n\nAnalyse impossible : %r" % (c["nom"], e))
    # choix final : réveil réglé en jeu, puis réveil du mod, puis dimension spawn, puis point d'apparition, puis amas
    ordre = {"forte": 0, "moyenne": 1, "faible": 2}
    meilleurs = [a for a in resultats["analyses"] if a.get("grotte")]
    meilleurs.sort(key=lambda a: (ordre[a["grotte"]["confiance"]],
                                  0 if a["candidat"]["source"].startswith("réveil réglé") else 1, -a["grotte"]["score"]))
    final = meilleurs[0] if meilleurs else None
    resultats["choix"] = final["nom"] if final else None
    res_f.write_text(json.dumps(resultats, ensure_ascii=False, indent=1), encoding="utf-8")
    env = ["TROUVEE=non"]
    if final:
        g = final["grotte"]
        z = g["zone"]
        env = ["TROUVEE=%s" % ("oui" if g["confiance"] in ("forte", "moyenne") else "faible"),
               "CONFIANCE=%s" % g["confiance"],
               "MONDE_SOURCE=%s" % g["monde"], "DIM_SOURCE=%s" % g["dim"],
               "ZONE='%d %d %d %d %d %d'" % tuple(z), "DECALAGE='%d %d %d'" % tuple(g["decalage"]),
               "REVEIL='%d %d %d'" % tuple(r + d for r, d in zip(g["reveil_source"], g["decalage"])),
               "CHOIX=%s" % final["nom"]]
    Path(sortie, "copie.env").write_text("\n".join(env) + "\n", encoding="utf-8")
    print("\n".join(env))


def zone_grotte(b, lab, cav, cavs):
    """Boîte de la grotte : la cavité, plus les salles fermées qui la touchent (à 12 blocs, derrière un mur, une vitre…),
    plus MARGE blocs de roche ; bornée à la hauteur du monde."""
    x0, x1, y0, y1, z0, z1 = cav["x"][0], cav["x"][1], cav["y"][0], cav["y"][1], cav["z"][0], cav["z"][1]
    incluses = [cav["num"]]
    for _ in range(2):
        for c in cavs:
            if c["num"] in incluses or c["bord"] or c["ouvertures"] > 0.2 * c["volume"] or c["volume"] < 100:
                continue
            if c["x"][0] <= x1 + 12 and c["x"][1] >= x0 - 12 and c["z"][0] <= z1 + 12 and c["z"][1] >= z0 - 12 \
                    and c["y"][0] <= y1 + 12 and c["y"][1] >= y0 - 12:
                nx0, nx1 = min(x0, c["x"][0]), max(x1, c["x"][1])
                nz0, nz1 = min(z0, c["z"][0]), max(z1, c["z"][1])
                if nx1 - nx0 > 480 or nz1 - nz0 > 480:
                    continue
                x0, x1, z0, z1 = nx0, nx1, nz0, nz1
                y0, y1 = min(y0, c["y"][0]), max(y1, c["y"][1])
                incluses.append(c["num"])
    return [max(b.x0, x0 - MARGE), max(Y_MIN, y0 - MARGE), max(b.z0, z0 - MARGE),
            min(b.x1, x1 + MARGE), min(Y_MAX, y1 + MARGE), min(b.z1, z1 + MARGE)], incluses


# ================================================================== balayage rapide (palettes)
DRAPEAUX = {K_RAIL: 1, K_LANTERNE: 2, K_CHAINE: 4, K_BARRIERE: 8, K_NOTRE: 16, K_ASC: 32, K_PORTE: 64}


def balayer_fichier(f):
    out = []
    mods = {}
    hauts = []
    try:
        chunks = ms.region_lire(str(f))
    except Exception as e:
        return f.name, [], {}, [], str(e)
    for (lx, lz), t in chunks.items():
        if t.get("sections") is None or t.get("xPos") is None:
            continue
        cx, cz = t["xPos"].v, t["zPos"].v
        secs = []
        haut = None
        for sec in t["sections"].v[1]:
            bs = sec.get("block_states")
            if bs is None or sec.get("Y") is None:
                continue
            noms = [p["Name"].v for p in bs["palette"].v[1]]
            sy = sec["Y"].v
            if any(not (n.startswith("minecraft:") and n[10:] in VIDE_NOMS) for n in noms):
                haut = sy if haut is None else max(haut, sy)
            fl = 0
            for n in noms:
                k = categorie(n)
                if k in DRAPEAUX:
                    fl |= DRAPEAUX[k]
                if not n.startswith("minecraft:"):
                    m = mods.setdefault(n, [0, []])
                    m[0] += 1
                    if len(m[1]) < 6:
                        m[1].append([cx * 16 + 8, sy * 16 + 8, cz * 16 + 8])
            if fl:
                secs.append([sy, fl])
        if haut is not None:
            hauts.append([cx, cz, haut])
        if secs:
            out.append([cx, cz, secs])
    return f.name, out, mods, hauts, None


def balayer(args):
    from multiprocessing import Pool
    fs = sorted(Path(args.dossier).glob("*.mca"))
    if not fs:
        return
    t0 = time.time()
    with Pool(max(1, os.cpu_count() or 1)) as p, open(args.etat, "a", encoding="utf-8") as f:
        for nom, out, mods, hauts, err in p.imap_unordered(balayer_fichier, fs):
            f.write(json.dumps({"region": nom, "chunks": out, "mods": mods, "hauts": hauts, "erreur": err}) + "\n")
    print("[balayage] %d fichiers en %.0f s" % (len(fs), time.time() - t0))


def synthese(args):
    from scipy import ndimage
    sortie = args.sortie
    chunks = {}
    hauts = {}
    mods = {}
    nreg = 0
    erreurs = []
    for l in open(args.etat, encoding="utf-8"):
        d = json.loads(l)
        nreg += 1
        if d.get("erreur"):
            erreurs.append(d["region"])
        for cx, cz, secs in d["chunks"]:
            chunks[(cx, cz)] = secs
        for cx, cz, h in d["hauts"]:
            hauts[(cx, cz)] = h
        for n, (k, ex) in d["mods"].items():
            m = mods.setdefault(n, [0, []])
            m[0] += k
            m[1] = (m[1] + ex)[:6]
    texte = ["## Balayage de tout le monde principal", ""]
    if not hauts:
        texte.append("Aucun chunk lu.")
        rapport(sortie, "\n".join(texte))
        Path(sortie, "candidats_balayage.json").write_text("[]", encoding="utf-8")
        Path(sortie, "cibles_balayage.lftp").write_text("", encoding="utf-8")
        return
    hmed = float(np.median(list(hauts.values())))
    texte.append("%d fichiers de régions, %d chunks avec des blocs (sommet médian : section %d, y ≈ %d)%s." % (
        nreg, len(hauts), hmed, hmed * 16 + 8, (" ; illisibles : " + ", ".join(erreurs[:10])) if erreurs else ""))

    def score_chunk(cx, cz, secs):
        h = hauts.get((cx, cz), hmed)
        s = 0.0
        fl_tot = 0
        for sy, fl in secs:
            # rails (rares : le train et sa voie ne sont pas faits de blocs) et barrières (épave) comptent le plus ; les
            # lanternes et nos blocs (conteneurs, décors) sont partout dans les villes : peu ou pas
            v = 6 * bool(fl & 1) + 1 * bool(fl & 2) + 1 * bool(fl & 4) + 4 * bool(fl & 8) + 3 * bool(fl & 32)
            if sy <= h - 2:          # enfoui : au moins 32 blocs sous le sommet du chunk
                v *= 3
            elif sy >= hmed + 3:     # bien plus haut que le reste de la carte
                v *= 2
            s += v
            fl_tot |= fl
        if fl_tot & 1 and fl_tot & (2 | 8):      # voie avec lanternes ou barrières : la grotte de la cinématique
            s *= 3
        return s

    sc = {k: score_chunk(k[0], k[1], v) for k, v in chunks.items()}
    xs = [k[0] for k in hauts]
    zs = [k[1] for k in hauts]
    cx0, cx1, cz0, cz1 = min(xs), max(xs), min(zs), max(zs)
    W, H = cx1 - cx0 + 1, cz1 - cz0 + 1
    G = np.zeros((W, H))
    for (cx, cz), s in sc.items():
        G[cx - cx0, cz - cz0] = s
    lab, n = ndimage.label(G >= 8, structure=np.ones((3, 3)))
    amas = []
    for num in range(1, n + 1):
        ii, kk = np.nonzero(lab == num)
        tot = float(G[ii, kk].sum())
        best = int(G[ii, kk].argmax())
        bx, bz = int(ii[best] + cx0), int(kk[best] + cz0)
        secs = chunks.get((bx, bz), [])
        ys = [sy for sy, fl in secs]
        y = int(np.median(ys) * 16 + 8) if ys else int(hmed * 16)
        fl = 0
        for k in zip(ii, kk):
            for sy, f in chunks.get((int(k[0] + cx0), int(k[1] + cz0)), []):
                fl |= f
        amas.append({"score": round(tot, 1), "chunks": len(ii), "x": bx * 16 + 8, "y": y, "z": bz * 16 + 8,
                     "x_min": int(ii.min() + cx0) * 16, "x_max": int(ii.max() + cx0) * 16 + 15,
                     "z_min": int(kk.min() + cz0) * 16, "z_max": int(kk.max() + cz0) * 16 + 15,
                     "contenu": ", ".join(v for b, v in ((1, "rails"), (2, "lanternes"), (4, "chaînes"), (8, "barrières"),
                                                          (16, "blocs HeraliZ"), (32, "ascenseur"), (64, "portes")) if fl & b)})
    amas.sort(key=lambda a: -a["score"])
    texte.append("")
    texte.append("Amas d'indices (surtout rails et barrières, un peu lanternes, chaînes et ascenseur ; triple pour ce qui est "
                 "enfoui à plus de 32 blocs sous le sommet du chunk, double pour ce qui est bien plus haut que la carte, "
                 "triple pour une voie avec lanternes ou barrières) :")
    texte.append("")
    texte.append("| rang | score | chunks | point fort (x y z) | étendue x | étendue z | contenu |")
    texte.append("|---|---|---|---|---|---|---|")
    for r, a in enumerate(amas[:15], 1):
        texte.append("| %d | %.0f | %d | %d %d %d | %d à %d | %d à %d | %s |" % (
            r, a["score"], a["chunks"], a["x"], a["y"], a["z"], a["x_min"], a["x_max"], a["z_min"], a["z_max"], a["contenu"]))
    nos = sorted(((n, v) for n, v in mods.items() if n.split(":")[0].startswith(("heralisz", "heraliz", "cfm", "crac"))),
                 key=lambda e: e[1][0])
    if nos:
        texte.append("")
        texte.append("Blocs de nos mods (et meubles) les plus RARES sur la carte (sections, exemples de positions) :")
        texte.append("")
        for nm, (k, ex) in nos[:40]:
            texte.append("- `%s` : %d sections — %s" % (nm, k, "; ".join("%d %d %d" % tuple(e) for e in ex[:4])))
    # carte des indices
    img = np.zeros((H, W, 3))
    hm = np.full((W, H), np.nan)
    for (cx, cz), h in hauts.items():
        hm[cx - cx0, cz - cz0] = h
    base = np.where(np.isnan(hm), 0, 40 + 8 * (np.nan_to_num(hm) - hmed))
    img[..., 0] = img[..., 1] = img[..., 2] = np.clip(base, 0, 120).T
    img[np.isnan(hm).T] = (8, 9, 12)
    v = np.clip(G / 30.0, 0, 1).T
    m = v > 0
    img[m] = np.stack([255 * v[m], 200 * v[m] ** 2, 40 + 0 * v[m]], axis=-1)
    e = max(1, min(4, 1400 // max(W, H)))
    canvas, d = toile(img, e, "Indices sur tout le monde (1 pixel = 1 chunk, nord en haut) — x %d à %d, z %d à %d"
                      % (cx0 * 16, cx1 * 16 + 15, cz0 * 16, cz1 * 16 + 15))
    f = ms.police(14)
    for r, a in enumerate(amas[:8], 1):
        px, pz = (a["x"] // 16 - cx0) * e, (a["z"] // 16 - cz0) * e + 34
        d.ellipse((px - 7, pz - 7, px + 7, pz + 7), outline=(80, 255, 140), width=2)
        d.text((px + 9, pz - 8), str(r), fill=(80, 255, 140), font=f)
    os.makedirs(Path(sortie, "rendus"), exist_ok=True)
    sauver(canvas, str(Path(sortie, "rendus", "balayage_carte.png")))
    texte.append("")
    texte.append("Carte des indices : `rendus/balayage_carte.png` (numéros = rangs du tableau).")
    rapport(sortie, "\n".join(texte))
    print("\n".join(texte))
    # les meilleurs amas deviennent des points à analyser
    cands, lftp, vus = [], [], set()
    for r, a in enumerate(amas[:args.nombre], 1):
        cands.append({"nom": "amas%d" % r, "source": "amas d'indices n° %d du balayage (score %.0f : %s)" % (r, a["score"], a["contenu"]),
                      "reveil": False, "monde": args.monde, "prefixe": args.prefixe, "dim": "minecraft:overworld",
                      "x": a["x"], "y": a["y"], "z": a["z"]})
        regs = [x for x in regions_autour(a["x"], a["z"]) if x not in vus]
        vus.update(regs)
        if regs:
            lftp += cmd_lftp(args.prefixe, args.monde, "minecraft:overworld", regs)
    Path(sortie, "candidats_balayage.json").write_text(json.dumps(cands, ensure_ascii=False, indent=1), encoding="utf-8")
    Path(sortie, "cibles_balayage.lftp").write_text("\n".join(lftp) + "\n", encoding="utf-8")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sp = ap.add_subparsers(dest="mode", required=True)
    a = sp.add_parser("indices")
    a.add_argument("mondes", nargs="+")
    a.add_argument("--sortie", required=True)
    a.add_argument("--prefixe", default="/")
    a = sp.add_parser("analyser")
    a.add_argument("--candidats", required=True)
    a.add_argument("--sortie", required=True)
    a = sp.add_parser("balayer")
    a.add_argument("dossier")
    a.add_argument("--etat", required=True)
    a = sp.add_parser("synthese")
    a.add_argument("--etat", required=True)
    a.add_argument("--monde", required=True)
    a.add_argument("--prefixe", required=True)
    a.add_argument("--sortie", required=True)
    a.add_argument("--nombre", type=int, default=4)
    a = sp.add_parser("cibles")
    a.add_argument("--monde", required=True)
    a.add_argument("--prefixe", required=True)
    a.add_argument("--dimension", default="minecraft:overworld")
    a.add_argument("--zone", nargs=6, type=int, required=True)
    a.add_argument("--sortie", required=True)
    args = ap.parse_args()
    {"indices": indices, "analyser": analyser, "balayer": balayer, "synthese": synthese, "cibles": cibles}[args.mode](args)


def cibles(args):
    """Commandes lftp qui copient les régions (et entités) d'une zone donnée à la main."""
    x0, _, z0, x1, _, z1 = args.zone
    regs = [(rx, rz) for rx in range(min(x0, x1) >> 9, (max(x0, x1) >> 9) + 1)
            for rz in range(min(z0, z1) >> 9, (max(z0, z1) >> 9) + 1)]
    Path(args.sortie).write_text("\n".join(cmd_lftp(args.prefixe, args.monde, args.dimension, regs)) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
