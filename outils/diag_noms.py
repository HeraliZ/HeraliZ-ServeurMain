"""Rend lisibles les rapports du diagnostic du serveur (dossier diag/ du Ptero : profils, blocages, mesures).

1. Méthodes de Minecraft : dans le serveur, elles portent leur nom SRG (« MinecraftServer.m_5705_ », aussi dans
   « lambda$m_5705_$0 ») ; elles sont remplacées par le nom officiel de Mojang (« MinecraftServer.tickServer »). Les noms
   de classes sont déjà les noms officiels. Le serveur le fait lui-même quand il trouve les correspondances dans son
   dossier libraries/ (voir NomsSrg du noyau) : alors il n'y a plus rien à traduire.
2. Fichiers de NOS modules : « fr/heralisz/utils/zombies/IaZombies.java:212 » devient le vrai chemin du dépôt,
   « modules/monde/src/serveur/java/fr/heralisz/utils/zombies/IaZombies.java:212 » (si le fichier est trouvé).

    python outils/diag_noms.py <rapports...> [--sortie DOSSIER] [--cache DOSSIER] [--mojang FICHIER] [--tsrg FICHIER]

Correspondances, dans cet ordre :
  - --mojang (correspondances de Mojang, officiel -> obfusqué) et --tsrg (table de MCPConfig, obfusqué -> SRG, ou le zip
    mcp_config-*.zip) ;
  - le dossier libraries/ du serveur local (outils/chemins.py : serveur_local, ou HERALIZ_SERVEUR), copié du Ptero ;
  - sinon téléchargées une fois et gardées dans --cache (par défaut ~/.cache/heraliz_diag) : correspondances de Mojang de
    Minecraft 1.20.1 (client_mappings) et table MCPConfig 1.20.1 de Forge (joined.tsrg).
Sans --sortie, chaque fichier est réécrit à sa place. Lecture seule sur tout le reste. Si les correspondances manquent,
seuls les chemins sont réécrits (message, code de sortie 0) : c'est un confort, pas un contrôle.
"""
import json
import os
import re
import sys
import urllib.request
import zipfile
from pathlib import Path

VERSION = "1.20.1"
MANIFESTE = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
TSRG = "https://raw.githubusercontent.com/MinecraftForge/MCPConfig/master/versions/release/1.20.1/joined.tsrg"
PRIMITIFS = {"void": "V", "boolean": "Z", "byte": "B", "char": "C", "short": "S", "int": "I", "long": "J",
             "float": "F", "double": "D"}
SRG = re.compile(r"\bm_\d+_\b")
# chemin de source d'un de nos modules dans un rapport : « fr/heraliz/loot/serveur/Rotation.java:88 »
CHEMIN = re.compile(r"(?<![\w/.-])((?:fr|com)/[\w/$]+/[A-Za-z_$][\w$]*\.java)(?=[:)\s,]|$)")
DEPOT = Path(__file__).resolve().parents[1]


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


def mojang_telecharge(cache):
    f = cache / f"client-{VERSION}.txt"
    if not f.exists():
        with urllib.request.urlopen(MANIFESTE, timeout=60) as r:
            man = json.load(r)
        url = next(v["url"] for v in man["versions"] if v["id"] == VERSION)
        with urllib.request.urlopen(url, timeout=60) as r:
            ver = json.load(r)
        telecharger(ver["downloads"]["client_mappings"]["url"], f)
    return f


def lire_texte(f):
    """Texte d'un fichier, ou de config/joined.tsrg si c'est un zip de MCPConfig."""
    f = Path(f)
    if f.suffix == ".zip":
        with zipfile.ZipFile(f) as z:
            return z.read("config/joined.tsrg").decode("utf-8")
    return f.read_text(encoding="utf-8")


def genre(texte):
    """« mojang », « tsrg » ou « » d'après les premières lignes."""
    for l in texte.splitlines()[:50]:
        if not l or l.startswith("#"):
            continue
        if l.startswith("tsrg2 "):
            return "tsrg"
        if not l.startswith((" ", "\t")) and " -> " in l and l.endswith(":"):
            return "mojang"
        if not l.startswith("\t") and len(l.split(" ")) >= 2 and "->" not in l:
            return "tsrg"
        return ""
    return ""


def correspondances_locales():
    """(mojang, tsrg) trouvés dans le dossier libraries/ du serveur local, sinon (None, None)."""
    try:
        sys.path.insert(0, str(DEPOT / "outils"))
        import chemins  # noqa: E402
        lib = Path(chemins.SERVEUR) / "libraries"
    except Exception:  # noqa: BLE001 : pas de serveur local, rien de grave
        return None, None
    mojang = tsrg = None
    for d in (lib / "net/minecraft/server", lib / "de/oceanlabs/mcp/mcp_config"):
        if not d.is_dir():
            continue
        for f in sorted(d.rglob("*")):
            if not f.is_file() or f.name.endswith(("-merged.txt", ".sha1")) or f.suffix not in (".txt", ".tsrg", ".zip"):
                continue
            try:
                g = genre(lire_texte(f)[:20000])
            except Exception:  # noqa: BLE001
                continue
            if g == "mojang" and mojang is None:
                mojang = f
            if g == "tsrg" and tsrg is None:
                tsrg = f
    return mojang, tsrg


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


def lire_mojang(texte):
    """{(classe obfusquée, nom obfusqué, descripteur obfusqué): nom officiel}."""
    lignes = texte.splitlines()
    classes = {}
    for l in lignes:
        if l and not l.startswith((" ", "#")) and l.endswith(":") and " -> " in l:
            off, obf = l[:-1].split(" -> ")
            classes[off] = obf
    methodes = {}
    courante = None
    # « 12:34:void tick(int) -> a », et aussi « void tick(int):12:34 -> a » (versions récentes)
    meth = re.compile(r"^\s+(?:\d+:\d+:)?(\S+) ([^\s(]+)\(([^)]*)\)(?::\d+(?::\d+)?)? -> (\S+)$")
    for l in lignes:
        if not l or l.startswith("#"):
            continue
        if not l.startswith(" "):
            courante = l[:-1].split(" -> ")[1].replace(".", "/") if l.endswith(":") and " -> " in l else None
            continue
        m = meth.match(l)
        if not m or courante is None:
            continue
        ret, nom, params, obf = m.groups()
        desc = "(" + "".join(type_obf(p.strip(), classes) for p in params.split(",") if p.strip()) + ")" + type_obf(ret, classes)
        methodes[(courante, obf, desc)] = nom
    return methodes


def lire_tsrg(texte, methodes):
    """{nom SRG: nom officiel} (tsrg ou tsrg2 de MCPConfig : classes « obf srg [id] », méthodes « \\tobf (desc) srg [id] »)."""
    noms = {}
    classe = None
    for l in texte.splitlines():
        if not l or l.startswith(("tsrg2", "#")):
            continue
        if not l.startswith("\t"):
            classe = l.split(" ")[0]
            continue
        if l.startswith("\t\t") or classe is None:
            continue
        p = l[1:].split(" ")
        if len(p) >= 3 and p[1].startswith("(") and p[2].startswith("m_"):
            off = methodes.get((classe, p[0], p[1]))
            if off and p[2] not in noms:
                noms[p[2]] = off
    return noms


def table_noms(args_mojang, args_tsrg, cache):
    mojang, tsrg = (Path(args_mojang) if args_mojang else None), (Path(args_tsrg) if args_tsrg else None)
    if mojang is None or tsrg is None:
        lm, lt = correspondances_locales()
        mojang, tsrg = mojang or lm, tsrg or lt
    if mojang is None:
        mojang = mojang_telecharge(cache)
    if tsrg is None:
        tsrg = telecharger(TSRG, cache / f"joined-{VERSION}.tsrg")
    sources = [str(mojang), str(tsrg)]
    return lire_tsrg(lire_texte(tsrg), lire_mojang(lire_texte(mojang))), sources


def index_sources():
    """« fr/heraliz/loot/serveur/Rotation.java » -> [« modules/loot/src/serveur/java/fr/heraliz/loot/serveur/Rotation.java »]."""
    idx = {}
    mods = DEPOT / "modules"
    if not mods.is_dir():
        return idx
    for f in mods.glob("*/src/*/java/**/*.java"):
        rel = f.relative_to(mods)
        parties = rel.parts   # module, src, partie, java, paquet..., Fichier.java
        cle = "/".join(parties[4:])
        idx.setdefault(cle, []).append("modules/" + "/".join(parties))
    return idx


def main():
    args = sys.argv[1:]
    sortie = cache = arg_mojang = arg_tsrg = None
    fichiers = []
    i = 0
    while i < len(args):
        if args[i] in ("--sortie", "--cache", "--mojang", "--tsrg") and i + 1 < len(args):
            v = args[i + 1]
            if args[i] == "--sortie":
                sortie = Path(v)
            elif args[i] == "--cache":
                cache = Path(v)
            elif args[i] == "--mojang":
                arg_mojang = v
            else:
                arg_tsrg = v
            i += 2
        else:
            fichiers.append(Path(args[i]))
            i += 1
    fichiers = [f for f in fichiers if f.is_file()]
    if not fichiers:
        print(__doc__)
        return 1
    cache = cache or Path(os.path.expanduser("~/.cache/heraliz_diag"))
    try:
        noms, sources = table_noms(arg_mojang, arg_tsrg, cache)
        print(f"[diag_noms] {len(noms)} noms de méthodes connus ({', '.join(Path(s).name for s in sources)})")
    except Exception as e:  # noqa: BLE001 : confort seulement
        print(f"[diag_noms] correspondances indisponibles ({e}) : noms SRG laissés tels quels")
        noms = {}
    idx = index_sources()
    if sortie:
        sortie.mkdir(parents=True, exist_ok=True)
    for f in fichiers:
        texte = f.read_text(encoding="utf-8", errors="replace")
        n = [0, 0]

        def nom(m):
            r = noms.get(m.group(0))
            if r:
                n[0] += 1
                return r
            return m.group(0)

        def chemin(m):
            trouves = idx.get(m.group(1).split("$")[0] if "$" in m.group(1) else m.group(1))
            if trouves and len(trouves) == 1:
                n[1] += 1
                return trouves[0]
            return m.group(1)

        texte = SRG.sub(nom, texte)
        texte = CHEMIN.sub(chemin, texte)
        cible = (sortie / f.name) if sortie else f
        cible.write_text(texte, encoding="utf-8")
        print(f"[diag_noms] {f.name} : {n[0]} nom(s) traduit(s), {n[1]} chemin(s) du dépôt")
    return 0


if __name__ == "__main__":
    sys.exit(main())
