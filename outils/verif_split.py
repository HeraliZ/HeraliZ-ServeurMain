"""Contrôle d'un jar coupé client / serveur (docs/METHODE_CLIENT_SERVEUR.md §8). À lancer sur CHAQUE jar livré.

    python outils/verif_split.py <jar>... [--cote client|serveur|complet] [--mods <dossier mods>] [--projet <dossier projet>]
                                [--ref <jar>[,<jar>...]] [--embarque <jar ou fichier>[,...]]
    --ref : jars de plus pour trouver les références (API d'un mod facultatif absent du modpack, ex. l'API de Simple
            Voice Chat pour CaraCrea : ses classes ne sont chargées que si ce mod est installé).
    --embarque : contenu recopié exprès dans le jar par le build (bibliothèque relogée, sa licence) : toléré par le
            contrôle 7 (pas « en trop »). Pour un .jar : toutes ses entrées (sauf son manifeste), ex. META-INF/jarjar/ ; sinon : le fichier à la racine.
    ex. python outils/verif_split.py dist/fusion/heraliz_boss-2.0.1-*.jar --mods "livraison/mods client" --projet mods/heraliz-boss
    (le plus simple : python outils/verifier.py, qui lance tous les contrôles avec les bonnes options)

Le côté est deviné par le nom du jar (-client / -serveur / -complet) si --cote est absent.

ERREURS (code de sortie 1) :
 1. Références introuvables : chaque classe, méthode et champ cité par le jar (tables de constantes, comme
    check_geckolib_api.py) doit exister dans le jar lui-même, dans Minecraft + Forge du bon côté, dans leurs bibliothèques,
    ou dans un mod dont le mods.toml du jar déclare la dépendance (cherché dans --mods). Java (java/, javax/, jdk/, sun/)
    est supposé présent.
      -client / -complet : contre Minecraft CLIENT 1.20.1 (SRG) + Forge 47.4.0 du lanceur CurseForge ;
      -serveur : contre Minecraft SERVEUR 1.20.1 (SRG) + Forge 47.3.12 du serveur local (outils/chemins.py) : rien de net/minecraft/client,
       com/mojang/blaze3d, org/lwjgl, ni les membres Forge marqués @OnlyIn(Dist.CLIENT).
 2. -serveur : classe client restée dans le jar (paquet « client », ou classe qui hérite de code client).
 3. Ressources : -client sans data/ ; -serveur sans assets/ (sauf, dans les deux cas, les fichiers placés exprès dans
    src/commun/resources, lus des deux côtés par getResourceAsStream, avec --projet), sans configs de mixins client ni refmap (une config
    COMMUNE, listée dans le MixinConfigs du jar et sans section « client », est permise avec sa refmap : groupe Tactique).
 4. Jar à plusieurs mods : chaque [[mods]] du mods.toml a sa classe @Mod et inversement ; chaque @Mod.EventBusSubscriber
    précise modid (sinon FML l'inscrit une fois PAR mod du jar : écouteurs appelés plusieurs fois).
 5. Mixins (manifeste MixinConfigs) : chaque classe listée existe, la refmap est dans le jar, les @Shadow ont des noms SRG
    (seulement pour les mixins qui visent une classe de Minecraft : les membres d'un autre mod gardent leur nom).
 Bibliothèques embarquées (Jar-in-Jar, META-INF/jarjar/*.jar) des mods déclarés en dépendance : lues aussi (ex. LuaJ et
 SimpleBedrockModel, fournis par TaCZ).
 6. Usines : « new X(...) » d'une classe commune X qui a une version serveur (sous-classe XServeur, ou --projet) hors du
    code client : on obtiendrait la version neutre (sans IA, sans logique). Créer par le type (TYPE.get().create(...)).
 7. --projet : le jar contient exactement les classes et ressources attendues (commun + client / commun + serveur / tout),
    lues dans <projet>/build/classes/java/{main,client,serveur} et build/resources/{...}.
INFO : crochets de chargement (chaînes « ...PartieServeur » / « ...PartieClient ») présents ou absents dans ce jar.
"""
import io
import json
import re
import struct
import subprocess
import sys
import tomllib
import zipfile
from pathlib import Path
import sys as _sys
_sys.path.insert(0, str(Path(__file__).resolve().parent))
import chemins  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
LANCEUR = chemins.LANCEUR   # outils/chemins_local.json
SERVEUR = chemins.SERVEUR
MC_SRG = "1.20.1-20230612.114412"
FORGE_CLIENT, FORGE_SERVEUR = "1.20.1-47.4.0", "1.20.1-47.3.12"
JAVA = ("java/", "javax/", "jdk/", "sun/", "com/sun/", "org/w3c/dom/", "org/xml/sax/")   # + module java.xml du JDK
CLIENT_SEUL = ("net/minecraft/client/", "com/mojang/blaze3d/", "org/lwjgl/", "com/mojang/realmsclient/")
ONLYIN = "Lnet/minecraftforge/api/distmarker/OnlyIn;"
MOD = "Lnet/minecraftforge/fml/common/Mod;"
EBS = "Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;"
SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;"
MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;"
SRG = re.compile(r"^(f|m)_\d+_$")


# ------------------------------------------------------------------ lecture d'un .class
class Classe:
    """Ce qu'il faut d'un fichier .class : nom, mère, interfaces, membres (+ annotations), références, « new », chaînes."""
    __slots__ = ("nom", "mere", "interfaces", "membres", "annotations", "refs", "news", "chaines")

    def __init__(self, data, profond=True):
        n = struct.unpack(">H", data[8:10])[0]
        cp, i, k = [None] * n, 10, 1
        while k < n:
            tag = data[i]
            if tag == 1:
                ln = struct.unpack(">H", data[i + 1:i + 3])[0]
                cp[k] = (1, data[i + 3:i + 3 + ln].decode("utf-8", "replace"))
                i += 3 + ln
            elif tag in (3, 4):
                i += 5
            elif tag in (5, 6):
                i += 9
                k += 1
            elif tag in (7, 8, 16, 19, 20):
                cp[k] = (tag, struct.unpack(">H", data[i + 1:i + 3])[0])
                i += 3
            elif tag in (9, 10, 11, 12, 17, 18):
                cp[k] = (tag,) + struct.unpack(">HH", data[i + 1:i + 5])
                i += 5
            elif tag == 15:
                i += 4
            else:
                raise ValueError(f"tag {tag}")
            k += 1
        utf = lambda x: cp[x][1]
        cls = lambda x: utf(cp[x][1])
        _, this, sup, ni = struct.unpack(">HHHH", data[i:i + 8])
        i += 8
        self.nom = cls(this)
        self.mere = cls(sup) if sup else None
        self.interfaces = [cls(struct.unpack(">H", data[i + 2 * j:i + 2 * j + 2])[0]) for j in range(ni)]
        i += 2 * ni
        self.refs, self.news, self.chaines = [], [], []
        if profond:
            for e in cp:
                if e and e[0] in (9, 10, 11):
                    nt = cp[e[2]]
                    self.refs.append(("champ" if e[0] == 9 else "méthode", cls(e[1]), utf(nt[1]), utf(nt[2])))
                elif e and e[0] == 7:
                    self.refs.append(("classe", utf(e[1]), "", ""))
                elif e and e[0] == 8:
                    self.chaines.append(utf(e[1]))
        self.membres = {}  # (nom, descripteur) -> annotations
        for genre in ("champ", "méthode"):
            cnt = struct.unpack(">H", data[i:i + 2])[0]
            i += 2
            for _ in range(cnt):
                _, nm, ds, ac = struct.unpack(">HHHH", data[i:i + 8])
                i += 8
                ann = {}
                for _ in range(ac):
                    an, ln = struct.unpack(">HI", data[i:i + 6])
                    nom_attr = utf(an)
                    if nom_attr in ("RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations"):
                        ann.update(_annotations(data, i + 6, utf))
                    elif nom_attr == "Code" and profond:
                        self.news += [(utf(nm), cls(c)) for c in _news(data, i + 6, cp)]
                    i += 6 + ln
                self.membres[(utf(nm), utf(ds))] = ann
        self.annotations = {}
        cnt = struct.unpack(">H", data[i:i + 2])[0]
        i += 2
        for _ in range(cnt):
            an, ln = struct.unpack(">HI", data[i:i + 6])
            if utf(an) in ("RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations"):
                self.annotations.update(_annotations(data, i + 6, utf))
            i += 6 + ln


def _valeur(data, i, utf):
    tag = chr(data[i])
    i += 1
    if tag == "e":
        t, c = struct.unpack(">HH", data[i:i + 4])
        return ("enum", utf(t), utf(c)), i + 4
    if tag == "c":
        return ("classe", utf(struct.unpack(">H", data[i:i + 2])[0])), i + 2
    if tag == "@":
        sous, i = _une_annotation(data, i, utf)
        return sous, i
    if tag == "[":
        n = struct.unpack(">H", data[i:i + 2])[0]
        i += 2
        vals = []
        for _ in range(n):
            v, i = _valeur(data, i, utf)
            vals.append(v)
        return vals, i
    idx = struct.unpack(">H", data[i:i + 2])[0]
    return (utf(idx) if tag == "s" else idx), i + 2


def _une_annotation(data, i, utf):
    t, n = struct.unpack(">HH", data[i:i + 4])
    i += 4
    paires = {}
    for _ in range(n):
        nom = utf(struct.unpack(">H", data[i:i + 2])[0])
        v, i = _valeur(data, i + 2, utf)
        paires[nom] = v
    return (utf(t), paires), i


def _annotations(data, i, utf):
    n = struct.unpack(">H", data[i:i + 2])[0]
    i += 2
    out = {}
    for _ in range(n):
        (t, paires), i = _une_annotation(data, i, utf)
        out[t] = paires
    return out


_LONG = {**{o: 1 for o in range(0x00, 0x10)}, 0x10: 2, 0x11: 3, 0x12: 2, 0x13: 3, 0x14: 3, **{o: 2 for o in range(0x15, 0x1A)},
         **{o: 1 for o in range(0x1A, 0x36)}, **{o: 2 for o in range(0x36, 0x3B)}, **{o: 1 for o in range(0x3B, 0x84)}, 0x84: 3,
         **{o: 1 for o in range(0x85, 0x99)}, **{o: 3 for o in range(0x99, 0xA9)}, 0xA9: 2, **{o: 1 for o in range(0xAC, 0xB2)},
         **{o: 3 for o in range(0xB2, 0xB9)}, 0xB9: 5, 0xBA: 5, 0xBB: 3, 0xBC: 2, 0xBD: 3, 0xBE: 1, 0xBF: 1, 0xC0: 3, 0xC1: 3,
         0xC2: 1, 0xC3: 1, 0xC5: 4, 0xC6: 3, 0xC7: 3, 0xC8: 5, 0xC9: 5, 0xCA: 1, 0xFE: 1, 0xFF: 1}


def _news(data, i, cp):
    """Indices de classes des instructions « new » d'un attribut Code."""
    lg = struct.unpack(">I", data[i + 4:i + 8])[0]
    code = data[i + 8:i + 8 + lg]
    pc, out = 0, []
    while pc < lg:
        op = code[pc]
        if op == 0xBB:
            out.append(struct.unpack(">H", code[pc + 1:pc + 3])[0])
            pc += 3
        elif op == 0xAA:
            p = pc + 1 + (3 - pc % 4)
            bas, haut = struct.unpack(">ii", code[p + 4:p + 12])
            pc = p + 12 + (haut - bas + 1) * 4
        elif op == 0xAB:
            p = pc + 1 + (3 - pc % 4)
            npaires = struct.unpack(">i", code[p + 4:p + 8])[0]
            pc = p + 8 + npaires * 8
        elif op == 0xC4:
            pc += 6 if code[pc + 1] == 0x84 else 4
        else:
            pc += _LONG.get(op, 1)
    return out


def _dist_client(ann):
    v = ann.get(ONLYIN)
    return v is not None and v.get("value", ("enum", "", ""))[2] == "CLIENT"


_JAVA_CLASSES = {}


def classe_java(cls):
    """(noms des membres, ascendants) d'une classe du JDK, lus par javap. Sert aux membres hérités de Java par une classe
    de Minecraft ou d'un mod (NonNullList.iterator, Direction.ordinal...). Classe inconnue : (None, [])."""
    if cls not in _JAVA_CLASSES:
        r = subprocess.run([chemins.javap(), "-p", cls.replace("/", ".")], capture_output=True, text=True,
                           encoding="utf-8", errors="replace")
        membres, parents = set(), []
        for l in r.stdout.splitlines():
            l = l.strip()
            while re.search(r"<[^<>]*>", l):
                l = re.sub(r"<[^<>]*>", "", l)
            if l.endswith("{"):
                for mot in ("extends", "implements"):
                    m = re.search(r"\b%s\s+([\w.$, ]+?)(?=\s+implements|\s*\{)" % mot, l)
                    if m:
                        parents += [x.strip().replace(".", "/") for x in m.group(1).split(",") if x.strip()]
            elif l.endswith(";"):
                m = re.search(r"([\w$]+)\(", l) or re.search(r"([\w$]+)\s*(=.*)?;$", l)
                if m:
                    membres.add(m.group(1))
        _JAVA_CLASSES[cls] = (membres if r.returncode == 0 else None, parents)
    return _JAVA_CLASSES[cls]


# ------------------------------------------------------------------ le « monde » : Minecraft + Forge + bibliothèques
class Monde:
    """Index paresseux : nom de classe -> (jar, entrée) ; une classe n'est lue que si on en a besoin."""

    def __init__(self, cote):
        self.cote = cote
        self.index, self.cache, self.zips = {}, {}, {}

    def ajouter(self, jar, ecraser=True, imbriques=False):
        jar = Path(jar)
        if not jar.exists():
            print(f"   (absent : {jar})")
            return
        z = zipfile.ZipFile(jar)
        self.zips[str(jar)] = z
        self._indexer(str(jar), z, ecraser)
        if imbriques:
            # bibliothèques embarquées par un mod (Jar-in-Jar de Forge), chargées en jeu avec lui (ex. LuaJ dans TaCZ)
            for n in z.namelist():
                if n.startswith("META-INF/jarjar/") and n.endswith(".jar"):
                    cle = f"{jar}!{n}"
                    zi = zipfile.ZipFile(io.BytesIO(z.read(n)))
                    self.zips[cle] = zi
                    self._indexer(cle, zi, False)

    def _indexer(self, cle, z, ecraser):
        for n in z.namelist():
            if n.endswith(".class") and not n.startswith("META-INF/"):
                nom = n[:-6]
                if ecraser or nom not in self.index:
                    self.index[nom] = (cle, n)

    def classe(self, nom):
        if nom in self.cache:
            return self.cache[nom]
        c = None
        if nom in self.index:
            jar, n = self.index[nom]
            c = Classe(self.zips[jar].read(n), profond=False)
            if self.cote == "serveur" and _dist_client(c.annotations):
                c = None
        self.cache[nom] = c
        return c


def monde_client():
    m = Monde("client")
    lib = LANCEUR / "libraries"
    m.ajouter(lib / f"net/minecraft/client/{MC_SRG}/client-{MC_SRG}-srg.jar")
    m.ajouter(lib / f"net/minecraftforge/forge/{FORGE_CLIENT}/forge-{FORGE_CLIENT}-client.jar")
    m.ajouter(lib / f"net/minecraftforge/forge/{FORGE_CLIENT}/forge-{FORGE_CLIENT}-universal.jar")
    for d in (lib / "net/minecraftforge").glob(f"*/{FORGE_CLIENT}/*.jar"):
        if "forge-" not in d.name:
            m.ajouter(d, ecraser=False)
    for v in (LANCEUR / f"versions/forge-{FORGE_CLIENT.split('-')[1]}/forge-{FORGE_CLIENT.split('-')[1]}.json", LANCEUR / "versions/1.20.1/1.20.1.json"):
        if not v.exists():
            continue
        for l in json.loads(v.read_text(encoding="utf-8")).get("libraries", []):
            art = l.get("downloads", {}).get("artifact", {}).get("path")
            if not art:
                g, a, ver = l["name"].split(":")[:3]
                art = f"{g.replace('.', '/')}/{a}/{ver}/{a}-{ver}.jar"
            if (lib / art).exists():
                m.ajouter(lib / art, ecraser=False)
    return m


def monde_serveur():
    m = Monde("serveur")
    lib = SERVEUR / "libraries"
    m.ajouter(lib / f"net/minecraft/server/{MC_SRG}/server-{MC_SRG}-srg.jar")
    m.ajouter(lib / f"net/minecraftforge/forge/{FORGE_SERVEUR}/forge-{FORGE_SERVEUR}-server.jar")
    m.ajouter(lib / f"net/minecraftforge/forge/{FORGE_SERVEUR}/forge-{FORGE_SERVEUR}-universal.jar")
    for j in lib.rglob("*.jar"):
        if "net/minecraft/server" in j.as_posix() or "net/minecraftforge/forge/" in j.as_posix():
            continue
        m.ajouter(j, ecraser=False)
    return m


# ------------------------------------------------------------------ le jar à contrôler
def _configs_mixins(z):
    """Configs de mixins listées dans l'attribut MixinConfigs du manifeste du jar."""
    try:
        manif = z.read("META-INF/MANIFEST.MF").decode("utf-8").replace(chr(13) + chr(10), chr(10))
    except KeyError:
        return []
    m = re.search(r"MixinConfigs: ((?:.|\n )+)", manif)
    return [c.strip() for c in re.sub(r"\n ", "", m.group(1)).strip().split(",")] if m else []


def _vise_minecraft(c):
    """Vrai si le mixin vise une classe de Minecraft (ou si sa cible est inconnue) : ses @Shadow doivent être en SRG."""
    a = c.annotations.get(MIXIN, {})
    cibles = [v[1][1:-1] for v in a.get("value", []) or [] if isinstance(v, tuple) and v[0] == "classe"]
    cibles += [s.replace(".", "/") for s in a.get("targets", []) or [] if isinstance(s, str)]
    return not cibles or any(x.startswith(("net/minecraft/", "com/mojang/")) for x in cibles)


def lire_mods_toml(z):
    try:
        return tomllib.loads(z.read("META-INF/mods.toml").decode("utf-8"))
    except KeyError:
        return {}


def jars_dependances(toml, dossier_mods):
    """Jars du dossier mods qui fournissent un modId déclaré en dépendance (hors forge / minecraft / mods du jar)."""
    propres = {m["modId"] for m in toml.get("mods", [])}
    voulus = set()
    for deps in toml.get("dependencies", {}).values():
        for d in deps:
            if d["modId"] not in ("forge", "minecraft") and d["modId"] not in propres:
                voulus.add(d["modId"])
    trouves = {}
    if dossier_mods and voulus:
        for j in Path(dossier_mods).glob("*.jar"):
            try:
                with zipfile.ZipFile(j) as z:
                    for m in lire_mods_toml(z).get("mods", []):
                        if m["modId"] in voulus:
                            trouves[m["modId"]] = j
            except (zipfile.BadZipFile, tomllib.TOMLDecodeError, KeyError):
                continue
    return trouves, voulus - set(trouves)


def classes_projet(projet):
    """{ensemble: (classes, ressources)} lus dans build/classes et build/resources du projet."""
    out = {}
    for ens in ("main", "client", "serveur"):
        cl = projet / "build/classes/java" / ens
        rs = projet / "build/resources" / ens
        classes = {p.relative_to(cl).as_posix() for p in cl.rglob("*.class")} if cl.exists() else set()
        res = {p.relative_to(rs).as_posix() for p in rs.rglob("*") if p.is_file()} if rs.exists() else set()
        out[ens] = (classes, res)
    return out


def verifier(jar, cote, mondes, dossier_mods, projet, refs=(), embarques=frozenset()):
    print(f"\n=== {Path(jar).name} (côté {cote})")
    erreurs, infos = [], []
    z = zipfile.ZipFile(jar)
    noms = z.namelist()
    propres = {}
    for n in noms:
        if n.endswith(".class") and not n.startswith("META-INF/"):
            propres[n[:-6]] = Classe(z.read(n))
    toml = lire_mods_toml(z)

    # monde du bon côté + dépendances déclarées
    monde = mondes["serveur" if cote == "serveur" else "client"]
    deps, manquants = jars_dependances(toml, dossier_mods)
    for mid in sorted(manquants):
        infos.append(f"dépendance {mid} introuvable dans --mods : ses références ne sont pas vérifiées")
    deps_monde = Monde(monde.cote)
    for j in deps.values():
        deps_monde.ajouter(j, imbriques=True)
    for j in refs:
        deps_monde.ajouter(j, imbriques=True)

    def classe(nom):
        if nom in propres:
            return propres[nom]
        return monde.classe(nom) or deps_monde.classe(nom)

    def existe_classe(nom):
        nom = nom.lstrip("[")
        if nom.startswith("L") and nom.endswith(";"):
            nom = nom[1:-1]
        if len(nom) <= 1 or nom.startswith(JAVA):
            return True
        return classe(nom) is not None

    def a_membre(nom, membre, desc, genre, vus=None):
        racine = vus is None
        vus = set() if racine else vus
        if nom.startswith("["):
            return True
        if nom.startswith(JAVA):
            # référence directe à Java : supposée juste ; membre HÉRITÉ de Java (ascendant d'une classe de Minecraft ou
            # d'un mod) : cherché pour de vrai, par son nom (sinon tout membre absent passait pour hérité d'Object)
            if racine:
                return True
            if nom in vus:
                return False
            vus.add(nom)
            membres, parents = classe_java(nom)
            if membres is None:
                return True
            return membre in membres or any(a_membre(x, membre, desc, genre, vus) for x in parents + ["java/lang/Object"])
        if nom in vus:
            return False
        vus.add(nom)
        c = classe(nom)
        if c is None:
            return False
        for (m, d), ann in c.membres.items():
            if m == membre and d == desc:
                return not (cote == "serveur" and _dist_client(ann))
        return any(x and a_membre(x, membre, desc, genre, vus) for x in [c.mere] + c.interfaces)

    # 1. références (une classe du jar dont un ascendant manque n'est signalée qu'une fois, pas membre par membre)
    def ascendance_cassee(nom, vus=None):
        vus = vus or set()
        if nom is None or nom.startswith(JAVA) or nom in vus:
            return None
        vus.add(nom)
        c = classe(nom)
        if c is None:
            return nom
        for x in [c.mere] + c.interfaces:
            r = ascendance_cassee(x, vus)
            if r:
                return r
        return None

    manque, cassees = {}, {}
    for nom in propres:
        r = ascendance_cassee(nom)
        if r:
            cassees[nom] = r
    for nom, r in sorted(cassees.items()):
        erreurs.append(f"introuvable côté {cote} : {r}, ascendant de {nom}")
    for c in propres.values():
        for genre, cl, nm, ds in c.refs:
            base = cl.lstrip("[")
            base = base[1:-1] if base.startswith("L") and base.endswith(";") else base
            if base in cassees:
                continue
            if genre == "classe":
                if not existe_classe(cl):
                    manque.setdefault(f"classe {base}", set()).add(c.nom)
            elif not existe_classe(cl):
                manque.setdefault(f"classe {cl}", set()).add(c.nom)
            elif not a_membre(cl, nm, ds, genre):
                manque.setdefault(f"{genre} {cl}.{nm}{ds}", set()).add(c.nom)
    for k in sorted(manque):
        qui = sorted(manque[k])
        erreurs.append(f"introuvable côté {cote} : {k}  <- {qui[0]}{f' (+{len(qui) - 1})' if len(qui) > 1 else ''}")

    # 2. classes client restées dans le jar serveur
    if cote == "serveur":
        for nom, c in propres.items():
            if "/client/" in nom + "/" or any(x and x.startswith(CLIENT_SEUL) for x in [c.mere] + c.interfaces):
                erreurs.append(f"classe client dans le jar serveur : {nom}")
            elif _dist_client(c.annotations):
                erreurs.append(f"classe @OnlyIn(Dist.CLIENT) dans le jar serveur (refusée au chargement) : {nom}")

    # 3. ressources
    if cote == "client":
        # exception (groupe Utils) : un fichier data/ placé exprès dans src/commun/resources, lu par getResourceAsStream
        # des deux côtés (ex. data/heralisz_utils/corps_formes.txt : formes de collision ET nombre d'états du bloc
        # heralisz_utils:corps, identiques des deux côtés) ; seulement avec --projet
        communs = classes_projet(projet)["main"][1] if projet else set()
        donnees = [n for n in noms if n.startswith("data/") and not n.endswith("/")]
        hors_commun = [n for n in donnees if n not in communs]
        if hors_commun:
            erreurs.append(f"data/ dans le jar client ({len(hors_commun)} fichiers, ex. {hors_commun[0]})")
        if len(donnees) > len(hors_commun):
            infos.append(f"data/ communs (src/commun/resources, lus des deux côtés) : {len(donnees) - len(hors_commun)} fichier(s)")
    if cote == "serveur":
        # exception : un fichier assets/ placé exprès dans src/commun/resources (ressource lue par getResourceAsStream
        # des deux côtés, ex. catalogue des tags, formes des bannières : piège n°16) ; seulement avec --projet
        communs = classes_projet(projet)["main"][1] if projet else set()
        assets = [n for n in noms if n.startswith("assets/") and not n.endswith("/")]
        hors_commun = [n for n in assets if n not in communs]
        if hors_commun:
            erreurs.append(f"assets/ dans le jar serveur ({len(hors_commun)} fichiers, ex. {hors_commun[0]})")
        if len(assets) > len(hors_commun):
            infos.append(f"assets/ communs (src/commun/resources, lus des deux côtés) : {len(assets) - len(hors_commun)} fichier(s)")
        # exception : config de mixins COMMUNE (listée dans le MixinConfigs du jar, sans section « client » : mixins qui
        # doivent agir des deux côtés, ex. enclume / bouclier du groupe Tactique) et sa refmap
        mixins_communs = set()
        for cfg in _configs_mixins(z):
            if cfg in noms:
                d = json.loads(z.read(cfg))
                if not d.get("client"):
                    mixins_communs.add(cfg)
                    if d.get("refmap"):
                        mixins_communs.add(d["refmap"])
        for n in noms:
            if (n.endswith(".mixins.json") or n.endswith("refmap.json")) and n not in mixins_communs:
                erreurs.append(f"fichier de mixins client dans le jar serveur : {n}")
        if mixins_communs:
            infos.append(f"mixins communs (des deux côtés) : {', '.join(sorted(mixins_communs))}")

    # 4. plusieurs mods par jar
    ids_toml = [m["modId"] for m in toml.get("mods", [])]
    ids_mod = {}
    for nom, c in propres.items():
        if MOD in c.annotations:
            ids_mod.setdefault(c.annotations[MOD].get("value"), []).append(nom)
    for mid in ids_toml:
        if mid not in ids_mod:
            erreurs.append(f"[[mods]] {mid} sans classe @Mod (FML : fml.modloading.missingclasses)")
    for mid, cls in ids_mod.items():
        if mid not in ids_toml:
            erreurs.append(f"@Mod(\"{mid}\") ({cls[0]}) absent du mods.toml (FML : fml.modloading.missingmetadata)")
        if len(cls) > 1:
            erreurs.append(f"plusieurs classes @Mod(\"{mid}\") : {cls}")
    if len(ids_toml) > 1:
        for nom, c in propres.items():
            if EBS in c.annotations and "modid" not in c.annotations[EBS] and MOD not in c.annotations:
                erreurs.append(f"@Mod.EventBusSubscriber sans modid (inscrit {len(ids_toml)} fois) : {nom}")
    infos.append(f"mods du jar : {', '.join(ids_toml)}")

    # 5. mixins
    try:
        manif = z.read("META-INF/MANIFEST.MF").decode("utf-8").replace(chr(13) + chr(10), chr(10))
    except KeyError:
        manif = ""
    configs = re.search(r"MixinConfigs: ((?:.|\n )+)", manif)
    if configs:
        for cfg in re.sub(r"\n ", "", configs.group(1)).strip().split(","):
            cfg = cfg.strip()
            if cfg not in noms:
                erreurs.append(f"config de mixins absente : {cfg}")
                continue
            d = json.loads(z.read(cfg))
            if d.get("refmap") and d["refmap"] not in noms:
                erreurs.append(f"{cfg} : refmap {d['refmap']} absente du jar")
            for cle in ("mixins", "client", "server"):
                for m in d.get(cle, []) or []:
                    nom = (d["package"] + "." + m).replace(".", "/")
                    if nom not in propres:
                        if not (cle == "client" and cote == "serveur"):
                            erreurs.append(f"{cfg} : mixin {nom} absente")
                        continue
                    for (mb, ds), ann in propres[nom].membres.items():
                        if SHADOW in ann and not SRG.match(mb) and _vise_minecraft(propres[nom]):
                            erreurs.append(f"mixin {nom} : @Shadow {mb} pas réobfusqué (attendu f_..._/m_..._)")

    # 6. « new X » d'une classe qui a une version serveur
    ens = classes_projet(projet) if projet else None
    serveur_cls = {n[:-6] for n in ens["serveur"][0]} if ens else set()
    client_cls = {n[:-6] for n in ens["client"][0]} if ens else set()
    bases = set()
    for nom, c in propres.items():
        if c.mere in propres and (nom in serveur_cls or nom.endswith("Serveur")):
            bases.add(c.mere)
    for nom, c in propres.items():
        if nom in client_cls or "/client/" in nom:
            continue
        for meth, cible in c.news:
            if cible in bases and not (nom == cible and meth == "<init>"):
                erreurs.append(f"new {cible} dans {nom}.{meth} : version neutre créée (passer par le type / l'usine)")

    # 7. contenu attendu (projet)
    if ens:
        attendu = {"client": ("main", "client"), "serveur": ("main", "serveur"), "complet": ("main", "client", "serveur")}[cote]
        cl_att = set().union(*(ens[e][0] for e in attendu))
        rs_att = set().union(*(ens[e][1] for e in attendu))
        cl_jar = {n for n in noms if n.endswith(".class")}
        rs_jar = {n for n in noms if not n.endswith(".class") and not n.endswith("/") and not n.startswith("META-INF/MANIFEST")}
        rs_jar -= {n for n in rs_jar if n.endswith("refmap.json")}  # refmap : ajoutée par MixinGradle
        for n in sorted(cl_att - cl_jar)[:20]:
            erreurs.append(f"classe attendue absente du jar : {n}")
        n_emb = len((cl_jar | rs_jar) & embarques)
        if n_emb:
            infos.append(f"{n_emb} entrée(s) embarquée(s) exprès (--embarque)")
        cl_jar -= embarques
        rs_jar -= embarques
        for n in sorted(cl_jar - cl_att)[:20]:
            erreurs.append(f"classe en trop dans le jar : {n}")
        for n in sorted(rs_att - rs_jar)[:20]:
            erreurs.append(f"ressource attendue absente du jar : {n}")
        for n in sorted(rs_jar - rs_att)[:20]:
            erreurs.append(f"ressource en trop dans le jar : {n}")
        infos.append(f"contenu comparé au projet : {len(cl_jar)} classes, {len(rs_jar)} ressources")

    # INFO : crochets de chargement des parties
    parties = sorted({s for c in propres.values() for s in c.chaines if re.fullmatch(r"[\w.]+\.Partie(Serveur|Client)", s)})
    for s in parties:
        infos.append(f"partie {'présente' if s.replace('.', '/') in propres else 'absente '} : {s}")

    infos.append(f"{len(propres)} classes, {sum(len(c.refs) for c in propres.values())} références lues")
    for i in infos:
        print("   info :", i)
    for e in erreurs[:200]:
        print("   ERREUR :", e)
    if len(erreurs) > 200:
        print(f"   ... et {len(erreurs) - 200} autres")
    print(f"   => {len(erreurs)} erreur(s)")
    return len(erreurs)


def main():
    args = sys.argv[1:]
    if not args:
        raise SystemExit(__doc__)

    def option(nom):
        if nom in args:
            i = args.index(nom)
            v = args[i + 1]
            del args[i:i + 2]
            return v
        return None

    cote_force, dossier_mods, projet = option("--cote"), option("--mods"), option("--projet")
    refs = [Path(x) for x in (option("--ref") or "").split(",") if x]
    embarques = set()
    for x in (option("--embarque") or "").split(","):
        if not x:
            continue
        if x.endswith(".jar"):
            with zipfile.ZipFile(x) as zz:
                embarques |= {n for n in zz.namelist() if not n.endswith("/") and n != "META-INF/MANIFEST.MF"}
        else:
            embarques.add(Path(x).name)
    embarques = frozenset(embarques)
    projet = (Path(projet) if Path(projet).is_absolute() else ROOT / projet) if projet else None
    jars = [Path(a) if Path(a).is_absolute() else Path.cwd() / a for a in args]
    mondes = {}
    total = 0
    for j in jars:
        cote = cote_force or next((c for c in ("client", "serveur", "complet") if j.stem.endswith("-" + c)), None)
        if cote is None:
            raise SystemExit(f"{j.name} : côté inconnu (nom en -client/-serveur/-complet, ou --cote)")
        cle = "serveur" if cote == "serveur" else "client"
        if cle not in mondes:
            print(f"[verif_split] chargement du monde {cle}…")
            mondes[cle] = monde_serveur() if cle == "serveur" else monde_client()
            print(f"[verif_split] {len(mondes[cle].index)} classes indexées")
        total += verifier(j, cote, mondes, dossier_mods, projet, refs, embarques)
    print(f"\n[verif_split] {total} erreur(s) au total")
    sys.exit(1 if total else 0)


if __name__ == "__main__":
    main()
