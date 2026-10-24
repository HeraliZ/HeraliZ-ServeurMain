"""Rend lisibles les rapports du diagnostic du serveur (HeraliZ Utils, dossier heraliz_diag/ du Ptero) : en jeu, les
méthodes de Minecraft portent leur nom SRG (« net.minecraft.world.level.Level.m_46653_ ») ; ce script les remplace par
le nom officiel de Mojang (« ...Level.guardEntityTick »). Les noms de classes sont déjà les noms officiels.

    python outils/diag_noms.py <rapports...> [--sortie DOSSIER] [--cache DOSSIER]

Il télécharge (une fois, gardés dans --cache, par défaut ~/.cache/heraliz_diag) :
  - les correspondances de Mojang de Minecraft 1.20.1 (client_mappings, nom officiel -> nom obfusqué) ;
  - la table MCPConfig 1.20.1 de Forge (joined.tsrg : nom obfusqué -> nom SRG).
Sans --sortie, chaque fichier est réécrit à sa place. Lecture seule sur tout le reste. Si un téléchargement échoue, les
rapports restent tels quels (message, code de sortie 0) : c'est un confort, pas un contrôle.
"""
import json
import os
import re
import sys
import urllib.request
from pathlib import Path

VERSION = "1.20.1"
MANIFESTE = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
TSRG = "https://raw.githubusercontent.com/MinecraftForge/MCPConfig/master/versions/release/1.20.1/joined.tsrg"
PRIMITIFS = {"void": "V", "boolean": "Z", "byte": "B", "char": "C", "short": "S", "int": "I", "long": "J",
             "float": "F", "double": "D"}
SRG = re.compile(r"\bm_\d+_\b")


def telecharger(url, fichier):
    if fichier.exists() and fichier.stat().st_size > 0:
        return fichier
    fichier.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url, timeout=60) as r:
        donnees = r.read()
    tmp = fichier.with_suffix(".part")
    tmp.write_bytes(donnees)
    tmp.replace(fichier)
    return fichier


def mojang(cache):
    f = cache / f"client-{VERSION}.txt"
    if not f.exists():
        with urllib.request.urlopen(MANIFESTE, timeout=60) as r:
            man = json.load(r)
        url = next(v["url"] for v in man["versions"] if v["id"] == VERSION)
        with urllib.request.urlopen(url, timeout=60) as r:
            ver = json.load(r)
        telecharger(ver["downloads"]["client_mappings"]["url"], f)
    return f


def type_obf(t, classes):
    """Type Java officiel (« net.minecraft.world.entity.Entity[] ») -> descripteur obfusqué (« [Lbfj; »)."""
    dims = 0
    while t.endswith("[]"):
        dims += 1
        t = t[:-2]
    if t in PRIMITIFS:
        d = PRIMITIFS[t]
    else:
        d = "L" + classes.get(t, t).replace(".", "/") + ";"
    return "[" * dims + d


def lire_mojang(f):
    """{classe obfusquée: {(nom obfusqué, descripteur obfusqué): nom officiel}}."""
    classes = {}
    lignes = f.read_text(encoding="utf-8").splitlines()
    for l in lignes:
        if l and not l.startswith((" ", "#")) and l.endswith(":"):
            off, obf = l[:-1].split(" -> ")
            classes[off] = obf
    methodes = {}
    courante = None
    meth = re.compile(r"^\s+(?:\d+:\d+:)?(\S+) ([^\s(]+)\(([^)]*)\)(?::\d+(?::\d+)?)? -> (\S+)$")
    for l in lignes:
        if not l or l.startswith("#"):
            continue
        if not l.startswith(" "):
            courante = l[:-1].split(" -> ")[1] if l.endswith(":") else None
            continue
        m = meth.match(l)
        if not m or courante is None:
            continue
        ret, nom, params, obf = m.groups()
        desc = "(" + "".join(type_obf(p, classes) for p in params.split(",") if p) + ")" + type_obf(ret, classes)
        methodes.setdefault(courante, {})[(obf, desc)] = nom
    return methodes


def lire_tsrg(f, methodes):
    """{nom SRG: nom officiel} (tsrg2 de MCPConfig : « obf srg id », méthodes « \\tobf (desc) srg id »)."""
    noms = {}
    classe = None
    for l in f.read_text(encoding="utf-8").splitlines():
        if not l or l.startswith("tsrg2"):
            continue
        if not l.startswith("\t"):
            classe = l.split(" ")[0]
            continue
        if l.startswith("\t\t"):
            continue
        p = l[1:].split(" ")
        if len(p) >= 3 and p[1].startswith("(") and p[2].startswith("m_"):
            off = methodes.get(classe, {}).get((p[0], p[1]))
            if off and p[2] not in noms:
                noms[p[2]] = off
    return noms


def main():
    args = sys.argv[1:]
    sortie = cache = None
    fichiers = []
    i = 0
    while i < len(args):
        if args[i] == "--sortie":
            sortie = Path(args[i + 1])
            i += 2
        elif args[i] == "--cache":
            cache = Path(args[i + 1])
            i += 2
        else:
            fichiers.append(Path(args[i]))
            i += 1
    if not fichiers:
        print(__doc__)
        return 1
    cache = cache or Path(os.path.expanduser("~/.cache/heraliz_diag"))
    try:
        noms = lire_tsrg(telecharger(TSRG, cache / f"joined-{VERSION}.tsrg"), lire_mojang(mojang(cache)))
    except Exception as e:  # noqa: BLE001 : confort seulement
        print(f"[diag_noms] correspondances indisponibles ({e}) : rapports laissés tels quels")
        noms = {}
    print(f"[diag_noms] {len(noms)} noms de méthodes connus")
    if sortie:
        sortie.mkdir(parents=True, exist_ok=True)
    for f in fichiers:
        texte = f.read_text(encoding="utf-8", errors="replace")
        n = [0]

        def remplacer(m):
            r = noms.get(m.group(0))
            if r:
                n[0] += 1
                return r
            return m.group(0)

        texte = SRG.sub(remplacer, texte)
        cible = (sortie / f.name) if sortie else f
        cible.write_text(texte, encoding="utf-8")
        print(f"[diag_noms] {f.name} : {n[0]} nom(s) traduit(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
