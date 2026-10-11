"""Inventaire des blocs posés dans un monde Minecraft 1.20.1 (LECTURE SEULE : rien n'est jamais écrit dans le monde).

    python outils/inventaire_carte.py <dossier du monde> [<autre dossier>...] [--sortie rapport.md]

Lit les fichiers de régions (*.mca) de chaque dossier donné (le monde et ses dimensions : region/, DIM-1/, DIM1/,
dimensions/<ns>/<nom>/region/) et relève, par dimension, chaque bloc présent (palette des sections de chunk). Puis,
pour les blocs de nos décors et des meubles de l'onglet « HeraliZ » (cfm), compare avec la liste des blocs qui
existent (fichiers blockstates des sources et des jars) : ce qui n'est posé nulle part apparaît dans « jamais posés ».

Sert à savoir quels props retirer sans rien faire disparaître de la carte. Le monde du serveur est copié par le
workflow « Inventaire de la carte » (lecture SFTP), jamais modifié.
"""
import glob
import gzip
import io
import json
import os
import re
import struct
import sys
import zipfile
import zlib
from collections import defaultdict
from pathlib import Path

DEPOT = Path(__file__).resolve().parent.parent

# namespaces comparés à la liste de leurs blocs : nom -> fonction qui rend les blocs existants
SUIVIS = ["heraliz_decors", "cfm", "heralisz_bases", "heralisz_clans", "heralisz_tags", "heralisz_utils", "crac",
          "heralisz_bunker", "heraliz_loot"]


# ------------------------------------------------------------------ NBT (lecture partielle, rapide)

class Lecteur:
    def __init__(self, data):
        self.d = data
        self.i = 0

    def u8(self):
        v = self.d[self.i]
        self.i += 1
        return v

    def i32(self):
        v = struct.unpack_from(">i", self.d, self.i)[0]
        self.i += 4
        return v

    def chaine(self):
        n = struct.unpack_from(">H", self.d, self.i)[0]
        self.i += 2
        s = self.d[self.i:self.i + n].decode("utf-8", "replace")
        self.i += n
        return s

    def valeur(self, t, garder):
        """Lit une valeur de type t ; si garder est faux, la saute (sans rien construire)."""
        if t == 1:
            self.i += 1
            return None
        if t == 2:
            self.i += 2
            return None
        if t == 3:
            return self.i32() if garder else self._saute(4)
        if t == 4:
            self.i += 8
            return None
        if t == 5:
            self.i += 4
            return None
        if t == 6:
            self.i += 8
            return None
        if t == 7:
            n = self.i32()
            self.i += n
            return None
        if t == 8:
            return self.chaine() if garder else self._saute_chaine()
        if t == 9:
            et = self.u8()
            n = self.i32()
            out = [] if garder else None
            for _ in range(n):
                v = self.valeur(et, garder)
                if garder:
                    out.append(v)
            return out
        if t == 10:
            out = {} if garder else None
            while True:
                tt = self.u8()
                if tt == 0:
                    return out
                nom = self.chaine()
                v = self.valeur(tt, garder and nom in GARDES)
                if garder and nom in GARDES:
                    out[nom] = v
        if t == 11:
            n = self.i32()
            self.i += 4 * n
            return None
        if t == 12:
            n = self.i32()
            self.i += 8 * n
            return None
        raise ValueError("type NBT inconnu %d" % t)

    def _saute(self, n):
        self.i += n
        return None

    def _saute_chaine(self):
        n = struct.unpack_from(">H", self.d, self.i)[0]
        self.i += 2 + n
        return None


# seules ces clés sont gardées (le reste est sauté) : sections > block_states > palette > Name
GARDES = {"sections", "block_states", "palette", "Name", "block_entities", "id"}


def chunk_blocs(data):
    """(noms des blocs présents, ids des entités de bloc) d'un chunk."""
    r = Lecteur(data)
    if r.u8() != 10:
        return set(), set()
    r.chaine()
    racine = r.valeur(10, True) or {}
    blocs = set()
    for sec in racine.get("sections") or []:
        bs = (sec or {}).get("block_states") or {}
        for p in bs.get("palette") or []:
            n = (p or {}).get("Name")
            if n:
                blocs.add(n)
    be = set()
    for e in racine.get("block_entities") or []:
        if e and e.get("id"):
            be.add(e["id"])
    return blocs, be


def lire_region(chemin):
    """Pour chaque chunk du fichier : ses blocs. Les chunks illisibles (écriture en cours) sont comptés à part."""
    with open(chemin, "rb") as f:
        tout = f.read()
    if len(tout) < 8192:
        return [], 0
    out = []
    erreurs = 0
    for k in range(1024):
        loc = struct.unpack_from(">I", tout, k * 4)[0]
        off = (loc >> 8) * 4096
        if off == 0 or off + 5 > len(tout):
            continue
        n = struct.unpack_from(">I", tout, off)[0]
        comp = tout[off + 4]
        brut = tout[off + 5:off + 4 + n]
        try:
            if comp == 2:
                data = zlib.decompress(brut)
            elif comp == 1:
                data = gzip.decompress(brut)
            elif comp == 3:
                data = brut
            else:
                erreurs += 1  # chunk externe (.mcc) ou compression inconnue
                continue
            out.append(chunk_blocs(data))
        except Exception:
            erreurs += 1
    return out, erreurs


def dimensions(monde):
    """(nom de dimension, dossier region) d'un dossier de monde."""
    m = Path(monde)
    out = []
    if (m / "region").is_dir():
        out.append((m.name, m / "region"))
    for d, nom in (("DIM-1", "nether"), ("DIM1", "the_end")):
        if (m / d / "region").is_dir():
            out.append(("%s/%s" % (m.name, nom), m / d / "region"))
    for reg in sorted(m.glob("dimensions/*/*/region")):
        out.append(("%s:%s" % (reg.parent.parent.name, reg.parent.name), reg))
    return out


# ------------------------------------------------------------------ blocs existants

def blocs_existants():
    """namespace -> ensemble des blocs qui existent (fichiers blockstates des sources et des jars)."""
    out = defaultdict(set)
    for f in glob.glob(str(DEPOT / "mods/*/src/*/resources/assets/*/blockstates/*.json")):
        p = Path(f)
        out[p.parent.parent.name].add(p.stem)
    for jar in list((DEPOT / "serveur/mods-tiers").glob("*.jar")) + list((DEPOT / "client/mods-tiers").glob("*.jar")):
        try:
            with zipfile.ZipFile(jar) as z:
                for n in z.namelist():
                    mm = re.match(r"assets/([^/]+)/blockstates/([^/]+)\.json$", n)
                    if mm and mm.group(1) in SUIVIS:
                        out[mm.group(1)].add(mm.group(2))
        except zipfile.BadZipFile:
            pass
    return out


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    sortie = None
    if "--sortie" in sys.argv:
        sortie = sys.argv[sys.argv.index("--sortie") + 1]
        args = [a for a in args if a != sortie]
    if not args:
        print(__doc__)
        sys.exit(1)
    poses = defaultdict(lambda: defaultdict(int))     # dimension -> bloc -> nombre de chunks
    entites = defaultdict(lambda: defaultdict(int))
    total_chunks = 0
    total_err = 0
    for monde in args:
        for dim, reg in dimensions(monde):
            fichiers = sorted(reg.glob("*.mca"))
            for f in fichiers:
                chunks, err = lire_region(f)
                total_err += err
                for blocs, be in chunks:
                    total_chunks += 1
                    for b in blocs:
                        poses[dim][b] += 1
                    for e in be:
                        entites[dim][e] += 1
            print("[inventaire] %-40s %4d régions lues" % (dim, len(fichiers)))
    lignes = ["# Inventaire des blocs posés", "",
              "%d chunks lus, %d illisibles (en cours d'écriture pendant la copie, ou externes)." % (total_chunks, total_err), ""]
    tous = defaultdict(int)
    for dim, d in poses.items():
        for b, n in d.items():
            tous[b] += n
    par_ns = defaultdict(dict)
    for b, n in tous.items():
        ns, _, nom = b.partition(":")
        par_ns[ns][nom] = n
    existants = blocs_existants()
    for ns in SUIVIS:
        utilises = par_ns.get(ns, {})
        exist = existants.get(ns, set())
        jamais = sorted(exist - set(utilises))
        lignes.append("## %s : %d bloc(s) posé(s) sur %d existant(s)" % (ns, len(utilises), len(exist)))
        lignes.append("")
        for nom in sorted(utilises, key=lambda x: -utilises[x]):
            ou = [dim for dim in poses if poses[dim].get(ns + ":" + nom)]
            lignes.append("- posé : `%s:%s` — %d chunk(s) (%s)" % (ns, nom, utilises[nom], ", ".join(ou)))
        if jamais:
            lignes.append("")
            lignes.append("Jamais posés (%d) : %s" % (len(jamais), ", ".join("`%s`" % j for j in jamais)))
        lignes.append("")
    autres = sorted(ns for ns in par_ns if ns not in SUIVIS and ns != "minecraft")
    lignes.append("## Autres mods présents dans la carte")
    lignes.append("")
    for ns in autres:
        lignes.append("- %s : %d bloc(s) différent(s)" % (ns, len(par_ns[ns])))
    texte = "\n".join(lignes) + "\n"
    if sortie:
        Path(sortie).write_text(texte, encoding="utf-8")
        Path(sortie).with_suffix(".json").write_text(json.dumps(
            {dim: dict(d) for dim, d in poses.items()}, ensure_ascii=False, indent=1, sort_keys=True), encoding="utf-8")
    print(texte)


if __name__ == "__main__":
    main()
