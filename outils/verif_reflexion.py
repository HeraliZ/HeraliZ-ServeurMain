"""Carte de la réflexion entre nos mods (audit du 21/11) : chaque appel « par son nom » écrit dans nos sources est cherché
dans sa cible, sans fabrication (pas de Gradle, pas de Minecraft : marche partout, aussi sur GitHub).

    python outils/verif_reflexion.py                       tous les modules livrés
    python outils/verif_reflexion.py loot bases            seulement les appels écrits dans ces modules
    python outils/verif_reflexion.py --tout                avec la liste de chaque appel vérifié (pas seulement les problèmes)
    python outils/verif_reflexion.py --json <fichier>      carte complète en JSON (appelant, cible, résultat)
    python outils/verif_reflexion.py --forge <jar>         en plus : le jar de Mohist (Release « base-serveur ») ou le
                                                           jar « universal » de Forge qu'il contient : classes Bukkit,
                                                           CraftBukkit et Forge du serveur, membres ajoutés par Bukkit

Ce qui est relevé dans nos sources (modules livrés : tous sauf events, capture, banc, _gabarit) :
  - Class.forName("…"), ClassLoader.loadClass("…"), puis getMethod / getDeclaredMethod / getField / getDeclaredField /
    getConstructor / getDeclaredConstructor("nom", Type.class…), en chaîne ou par une variable ;
  - MethodHandles : findStatic / findVirtual / findGetter / findStaticGetter / findSetter / findConstructor /
    findVarHandle (MethodType.methodType(retour, paramètres…)) ;
  - ObfuscationReflectionHelper.findMethod / findField / getPrivateValue / setPrivateValue (noms SRG de Minecraft) ;
  - les petites fonctions d'aide écrites pour ça : appel(f)("classe", "méthode", Type.class…) / new Appel(…) ;
  - toute chaîne qui nomme une de nos classes (ex. Parties.serveur(MODID, "…PartieServeur", bus)).
Chaque cible est cherchée dans nos sources (tous les modules), puis dans les jars des autres auteurs (libs/,
serveur/mods-tiers/, client/mods-tiers/, serveur/plugins/, modules/*/libs/ et leurs jars imbriqués), puis dans
Minecraft si outils/chemins.py le trouve (sinon : « non vérifiable ici »).

ERREUR (code de sortie 1) :
  - classe introuvable, méthode / champ / constructeur introuvable avec ces types exacts de paramètres ;
  - getMethod / getField / getConstructor sur un membre non public (ils ne voient que le public), ou invoke sur une
    classe non publique sans setAccessible ;
  - invoke(null, …) / get(null) / findStatic sur un membre qui n'est pas static, findVirtual sur un static ;
  - résultat converti dans un autre type que le vrai (ex. (boolean) sur une méthode qui rend un int, ou void) ;
  - mauvais côté : appel écrit côté serveur vers une classe du joueur (src/client, module interface), ou côté joueur
    vers une classe du serveur (src/serveur) ; appel écrit dans src/commun vers une classe d'un seul côté sans
    try/catch ; cible dans un module jamais livré (events, banc…) ;
  - appel non protégé (ni try/catch, ni throws) vers un autre module que le mods.toml ne déclare pas obligatoire ;
  - nom de Mojang écrit en clair (getDeclaredField("connection")…) alors que son nom SRG est connu : introuvable en
    jeu (les membres ajoutés par Forge ou Bukkit gardent leur nom : acceptés quand le Forge de Mohist les cite) ;
  - chaîne qui nomme une de nos classes qui n'existe pas (sauf constante que rien ne lit : avertissement) ;
  - import (ou nom qualifié) d'une classe d'un AUTRE module du mauvais côté : src/serveur qui cite src/client (ou le
    module interface), src/client qui cite src/serveur, src/commun qui cite l'un des deux ; classe d'un mod des joueurs
    seulement (client/mods-tiers) citée côté serveur, d'un plugin citée côté joueur ; module jamais livré.
AVERTISSEMENT : appel vers un membre dont les types ne se lisent pas dans le code (variables, classe d'exécution),
membre hérité d'une classe d'un autre auteur, nom SRG que rien ne confirme, classe de Minecraft non vérifiable ici.
INFO (--tout) : appels protégés vers un mod facultatif absent du pack (Iris, OptiFine…).
Sans Minecraft, les noms de Minecraft sont appris dans les autres jars (classe Correspondances). Sur GitHub, après le
téléchargement de Mohist du démarrage d'essai : python outils/verif_reflexion.py --forge _banc/mohist-*.jar
"""
import json
import re
import struct
import sys
import tomllib
import zipfile
import io
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import sources_java as sj  # noqa: E402

RACINE = sj.RACINE
SRG = re.compile(r"^(f|m)_\d+_$")
NOM_CLASSE = re.compile(r"^(?:[A-Za-z_][\w]*\.)+[A-Z][\w]*(?:\$[\w]+)*$")
MINECRAFT = ("net.minecraft.", "com.mojang.")
TOLERE_CLASSE = {"ClassNotFoundException", "ReflectiveOperationException", "Exception", "Throwable", "LinkageError",
                 "NoClassDefFoundError"}
TOLERE_MEMBRE = {"NoSuchMethodException", "NoSuchFieldException", "ReflectiveOperationException", "Exception",
                 "Throwable", "IllegalAccessException"}
# bibliothèques fournies des deux côtés par Minecraft, Forge ou Mixin (parfois recopiées dans un mod ou un plugin)
BIBLIOTHEQUES = ("javax.", "com.google.", "org.apache.", "com.sun.jna", "it.unimi.", "org.slf4j.", "org.joml.",
                 "io.netty.", "org.lwjgl.", "org.jetbrains.", "org.intellij.", "org.objectweb.", "org.spongepowered.",
                 "com.mojang.", "net.minecraft.", "org.checkerframework.", "oshi.", "com.ibm.icu.")
ENVELOPPES = {"Integer": "int", "Long": "long", "Double": "double", "Float": "float", "Boolean": "boolean",
              "Byte": "byte", "Short": "short", "Character": "char", "Void": "void"}


def rel(chemin):
    """Chemin lisible depuis la racine du dépôt."""
    try:
        return Path(chemin).relative_to(RACINE).as_posix()
    except ValueError:
        return Path(chemin).as_posix()


# ====================================================================== classes des jars (autres auteurs)
class ClasseBin:
    """Fichier .class : nom, accès, mère, interfaces, champs et méthodes [(accès, nom, descripteur)]."""
    __slots__ = ("nom", "acces", "mere", "interfaces", "champs", "methodes")

    def __init__(self, data):
        n = struct.unpack(">H", data[8:10])[0]
        cp, i, k = [None] * n, 10, 1
        while k < n:
            tag = data[i]
            if tag == 1:
                ln = struct.unpack(">H", data[i + 1:i + 3])[0]
                cp[k] = data[i + 3:i + 3 + ln].decode("utf-8", "replace")
                i += 3 + ln
            elif tag in (3, 4):
                i += 5
            elif tag in (5, 6):
                i += 9
                k += 1
            elif tag in (7, 8, 16, 19, 20):
                cp[k] = ("ref", struct.unpack(">H", data[i + 1:i + 3])[0])
                i += 3
            elif tag in (9, 10, 11, 12, 17, 18):
                i += 5
            elif tag == 15:
                i += 4
            else:
                raise ValueError("tag %d" % tag)
            k += 1
        cls = lambda x: cp[cp[x][1]]
        self.acces, this, sup, ni = struct.unpack(">HHHH", data[i:i + 8])
        i += 8
        self.nom = cls(this)
        self.mere = cls(sup) if sup else None
        self.interfaces = [cls(struct.unpack(">H", data[i + 2 * j:i + 2 * j + 2])[0]) for j in range(ni)]
        i += 2 * ni
        self.champs, self.methodes = [], []
        for liste in (self.champs, self.methodes):
            cnt = struct.unpack(">H", data[i:i + 2])[0]
            i += 2
            for _ in range(cnt):
                ac, nm, ds, na = struct.unpack(">HHHH", data[i:i + 8])
                i += 8
                for _ in range(na):
                    ln = struct.unpack(">I", data[i + 2:i + 6])[0]
                    i += 6 + ln
                liste.append((ac, cp[nm], cp[ds]))


class Jars:
    """Index paresseux des classes des jars : nom interne (a/b/C) -> (jar, entrée)."""

    def __init__(self):
        self.index, self.zips, self.cache, self.cote = {}, {}, {}, {}

    def ajouter(self, jar, cote, etiquette=None):
        jar = Path(jar)
        if not jar.exists():
            return
        try:
            z = zipfile.ZipFile(jar)
        except zipfile.BadZipFile:
            return
        racine = None
        if cote.startswith("serveur (plugin"):
            # un plugin embarque souvent des bibliothèques (javax.annotation, JNA…) : seul son propre paquet compte
            try:
                m = re.search(r"^main:\s*([\w.]+)", z.read("plugin.yml").decode("utf-8", "replace"), re.M)
                racine = ".".join(m.group(1).split(".")[:2]).replace(".", "/") + "/" if m else None
            except KeyError:
                racine = None
        self._indexer(etiquette or jar.name, z, cote, 3, racine)

    def _indexer(self, cle, z, cote, profondeur, racine=None):
        self.zips[cle] = z
        for n in z.namelist():
            if n.endswith(".class") and not n.startswith("META-INF/versions/"):
                if racine is not None and not n.startswith(racine):
                    continue
                if n[:-6] not in self.index:
                    self.index[n[:-6]] = (cle, n)
                    self.cote[n[:-6]] = cote
            elif profondeur > 0 and n.endswith(".jar") and n.startswith(("META-INF/jarjar/", "META-INF/jars/")):
                try:
                    zi = zipfile.ZipFile(io.BytesIO(z.read(n)))
                except zipfile.BadZipFile:
                    continue
                self._indexer(cle + "!" + n.rsplit("/", 1)[-1], zi, cote, profondeur - 1)

    def classe(self, nom):
        nom = nom.replace(".", "/")
        if nom not in self.cache:
            c = None
            if nom in self.index:
                jar, n = self.index[nom]
                try:
                    c = ClasseBin(self.zips[jar].read(n))
                except (ValueError, struct.error, IndexError):
                    c = None
            self.cache[nom] = c
        return self.cache[nom]

    def origine(self, nom):
        e = self.index.get(nom.replace(".", "/"))
        return e[0] if e else None


def jars_tiers(forge=None):
    j = Jars()
    for f in sorted((RACINE / "serveur" / "mods-tiers").glob("*.jar")):
        j.ajouter(f, "les deux")
    for f in sorted((RACINE / "client" / "mods-tiers").glob("*.jar")):
        j.ajouter(f, "joueur")
    for f in sorted((RACINE / "serveur" / "plugins").glob("*.jar")):
        j.ajouter(f, "serveur (plugin)")
    for f in sorted((RACINE / "libs").glob("*.jar")):
        j.ajouter(f, "compilation")
    for f in sorted((RACINE / "modules").glob("*/libs/*.jar")):
        # jar recopié dans le jar du module par sa fabrication (zipTree('libs/<jar>') : Emotecraft des emotes, Opus de la
        # voix) : ses classes sont en jeu des deux côtés ; sinon, seulement pour compiler
        b = f.parents[1] / "build.gradle"
        embarque = b.exists() and ("zipTree('libs/%s')" % f.name) in b.read_text(encoding="utf-8")
        j.ajouter(f, "les deux" if embarque else "compilation")
    j.cote_bukkit = bool(forge)
    if forge:
        z = jar_forge(forge)
        j._indexer("Forge/Bukkit de Mohist", z, "serveur (Mohist)", 0)
    return j


def jar_forge(chemin):
    """Le jar « universal » de Forge de Mohist : donné tel quel, ou lu DANS le jar de Mohist (data/forge-*-universal.jar,
    Release « base-serveur », déjà téléchargé par le démarrage d'essai)."""
    z = zipfile.ZipFile(chemin)
    dedans = [n for n in z.namelist() if re.fullmatch(r"data/forge-[\w.-]+-universal\.jar", n)]
    return zipfile.ZipFile(io.BytesIO(z.read(dedans[0]))) if dedans else z


class Correspondances:
    """Ce qu'on sait des noms de Minecraft SANS Minecraft, appris dans les jars des autres auteurs et dans le Forge de
    Mohist : appels SRG qu'ils font (le membre existe donc), tables de correspondance de leurs mixins (refmap : nom de
    Mojang -> nom SRG) et leurs accesstransformer.cfg (nom SRG # nom de Mojang), et noms EN CLAIR que le Forge / Bukkit
    du serveur appelle dans les classes de Minecraft (membres ajoutés par Forge ou par Bukkit, jamais renommés)."""

    def __init__(self):
        self.srg = {}        # (classe interne, nom SRG) -> {descripteurs}
        self.mojang = {}     # (classe interne, nom de Mojang) -> {(descripteur ou '', nom SRG)}
        self.ajoutes = {}    # (classe interne, nom en clair) -> {origine : 'forge' | 'bukkit'}

    def apprendre_jar(self, z, serveur_mohist=False, profondeur=2):
        import verif_split as vs
        for n in z.namelist():
            if n.endswith(".class") and not n.startswith("META-INF/versions/"):
                try:
                    c = vs.Classe(z.read(n))
                except (ValueError, struct.error, IndexError):
                    continue
                for g, cl, nm, ds in c.refs:
                    if g == "classe" or not cl.startswith(("net/minecraft/", "com/mojang/math/", "com/mojang/blaze3d/")):
                        continue
                    if SRG.match(nm):
                        self.srg.setdefault((cl, nm), set()).add(ds)
                    elif serveur_mohist and nm not in ("<init>", "<clinit>"):
                        orig = "bukkit" if n.startswith(("org/bukkit/", "com/mohistmc/", "org/spigotmc/", "io/papermc/")) else "forge"
                        self.ajoutes.setdefault((cl, nm), set()).add(orig)
            elif n.endswith("refmap.json"):
                try:
                    d = json.loads(z.read(n))
                except ValueError:
                    continue
                for table in list((d.get("mappings") or {}).values()) + [
                        x for env in (d.get("data") or {}).values() for x in env.values()]:
                    for dev, obf in table.items():
                        self._paire(dev, obf)
            elif n.endswith("accesstransformer.cfg"):
                self.apprendre_at(z.read(n).decode("utf-8", "replace"))
            elif profondeur > 0 and n.endswith(".jar") and n.startswith(("META-INF/jarjar/", "META-INF/jars/")):
                try:
                    self.apprendre_jar(zipfile.ZipFile(io.BytesIO(z.read(n))), serveur_mohist, profondeur - 1)
                except zipfile.BadZipFile:
                    pass

    def apprendre_at(self, texte):
        for l in texte.splitlines():
            m = re.match(r"\s*\S+\s+(net\.minecraft\.[\w.$]+)\s+([fm]_\d+_)(\([^\s#]*)?\s*(?:#\s*(\w+))?", l)
            if not m:
                continue
            cl = m.group(1).replace(".", "/")
            self.srg.setdefault((cl, m.group(2)), set()).add(m.group(3) or "")
            if m.group(4):
                self.mojang.setdefault((cl, m.group(4)), set()).add((m.group(3) or "", m.group(2)))

    def _paire(self, dev, obf):
        def couper(x):
            proprio = None
            if x.startswith("L") and ";" in x:
                proprio, x = x[1:x.index(";")], x[x.index(";") + 1:]
            nom, desc = re.match(r"([^(:]*)(.*)", x).groups()
            return proprio, nom, desc.lstrip(":")
        po, no, do = couper(obf)
        pd, nd, dd = couper(dev)
        proprio = pd or po
        if not proprio or not SRG.match(no or ""):
            return
        self.srg.setdefault((po or proprio, no), set()).add(do)
        if nd and not SRG.match(nd):
            self.mojang.setdefault((proprio, nd), set()).add((dd, no))

    def srg_connu(self, classe, nom):
        return (classe.replace(".", "/"), nom) in self.srg

    def srg_de(self, classe, nom):
        """{(descripteur, SRG)} connus pour ce nom de Mojang (dans la classe ; les ascendants ne sont pas connus)."""
        return self.mojang.get((classe.replace(".", "/"), nom), set())

    def ajoute(self, classe, nom):
        return self.ajoutes.get((classe.replace(".", "/"), nom), set())


def correspondances(forge=None):
    c = Correspondances()
    for f in sorted((RACINE / "serveur" / "mods-tiers").glob("*.jar")) + sorted((RACINE / "client" / "mods-tiers").glob("*.jar")):
        try:
            c.apprendre_jar(zipfile.ZipFile(f))
        except zipfile.BadZipFile:
            pass
    for at in sorted(sj.MODULES.glob("*/src/*/resources/META-INF/accesstransformer.cfg")):
        c.apprendre_at(at.read_text(encoding="utf-8", errors="replace"))
    if forge:
        c.apprendre_jar(jar_forge(forge), serveur_mohist=True)
    return c


def minecraft():
    """Minecraft + Forge en noms SRG (outils/chemins.py) s'ils sont sur cette machine, sinon None."""
    try:
        import verif_split as vs
    except ImportError:
        return None
    mondes = {}
    srv = vs.SERVEUR / "libraries" / f"net/minecraft/server/{vs.MC_SRG}/server-{vs.MC_SRG}-srg.jar"
    cli = vs.LANCEUR / "libraries" / f"net/minecraft/client/{vs.MC_SRG}/client-{vs.MC_SRG}-srg.jar"
    if srv.exists():
        mondes["serveur"] = vs.monde_serveur()
    if cli.exists():
        mondes["client"] = vs.monde_client()
    return mondes or None


# ====================================================================== descripteurs
def desc_type(fqn):
    """fqn (a.b.C$D, int, X[]) -> descripteur JVM."""
    if fqn is None:
        return None
    dims = 0
    while fqn.endswith("[]"):
        fqn, dims = fqn[:-2], dims + 1
    d = sj.PRIMITIFS.get(fqn) or ("L" + fqn.replace(".", "/") + ";")
    return "[" * dims + d


def type_desc(d):
    """descripteur JVM -> fqn lisible."""
    dims = 0
    while d.startswith("["):
        d, dims = d[1:], dims + 1
    inv = {v: k for k, v in sj.PRIMITIFS.items()}
    base = inv[d] if d in inv else d[1:-1].replace("/", ".")
    return base + "[]" * dims


def params_desc(desc):
    """'(ILjava/lang/String;)V' -> (['int', 'java.lang.String'], 'void')."""
    i, out = 1, []
    while desc[i] != ")":
        j = i
        while desc[j] == "[":
            j += 1
        if desc[j] == "L":
            j = desc.index(";", j)
        out.append(type_desc(desc[i:j + 1]))
        i = j + 1
    return out, type_desc(desc[i + 1:])


# ====================================================================== lecture des expressions
class Unite:
    """Un corps de méthode, un bloc ou un initialiseur de champ : jetons [a, b) d'un fichier."""
    __slots__ = ("fichier", "type", "membre", "a", "b", "essais")

    def __init__(self, fichier, ty, membre, a, b):
        self.fichier, self.type, self.membre, self.a, self.b = fichier, ty, membre, a, b
        self.essais = None

    def lieu(self, i):
        f = self.fichier
        return "%s:%d" % (rel(f.chemin), f.t[i].ligne)

    def blocs_try(self):
        """[(début, fin, {types attrapés})] des blocs try de l'unité."""
        if self.essais is None:
            t, out = self.fichier.t, []
            for i in range(self.a, self.b):
                if t[i].texte == "try" and t[i].genre == "id":
                    j = i + 1
                    if t[j].texte == "(":
                        j = sj.fermant(t, j) + 1
                    if t[j].texte != "{":
                        continue
                    fin = sj.fermant(t, j, "{", "}")
                    types, k = set(), fin + 1
                    while k < self.b and t[k].texte == "catch":
                        e = sj.fermant(t, k + 1)
                        types |= {t[q].texte for q in range(k + 2, e) if t[q].genre == "id"}
                        k = sj.fermant(t, e + 1, "{", "}") + 1
                    out.append((j, fin, types))
            self.essais = out
        return self.essais

    def protege(self, i, attendus):
        """'try' si le jeton i est dans un try qui attrape une des exceptions attendues, 'throws' si la méthode les
        déclare, sinon None."""
        for (a, b, types) in self.blocs_try():
            if a <= i <= b and types & attendus:
                return "try"
        if self.membre is not None and set(self.membre.lance) & attendus:
            return "throws"
        return None


class Site:
    """Un appel par réflexion relevé dans nos sources."""

    def __init__(self, unite, i, genre):
        self.unite, self.i, self.genre = unite, i, genre
        self.classe = None        # fqn visé (chaîne), ou None si dynamique
        self.classe_dyn = None    # texte de l'expression quand elle n'est pas lisible
        self.membre = None        # nom du membre (méthode / champ), None pour un constructeur ou une classe seule
        self.types = None         # [fqn] des paramètres, None si illisibles
        self.retour = None        # type de retour attendu (MethodType) ou converti ((T) invoke)
        self.statique = None      # True : appelé sans objet (invoke(null…), get(null), findStatic) ; False : findVirtual
        self.declare = False      # getDeclared* (tous les accès) plutôt que get* (public seulement)
        self.accessible = False   # setAccessible(true) vu sur le résultat
        self.protection = None    # 'try' / 'throws' / None
        self.cote = unite.fichier.cote
        self.var = None

    def lieu(self):
        return self.unite.lieu(self.i)


LECTEURS = {}   # lecteurs des autres fichiers (constantes chaînes lues ailleurs)


class Lecteur:
    """Relève les sites d'un fichier."""

    LOOKUPS = {"getMethod": "méthode", "getDeclaredMethod": "méthode", "getField": "champ",
               "getDeclaredField": "champ", "getConstructor": "constructeur", "getDeclaredConstructor": "constructeur"}
    HANDLES = {"findStatic": "méthode", "findVirtual": "méthode", "findSpecial": "méthode", "findGetter": "champ",
               "findSetter": "champ", "findStaticGetter": "champ", "findStaticSetter": "champ",
               "findVarHandle": "champ", "findStaticVarHandle": "champ", "findConstructor": "constructeur"}
    ORH = {"findMethod": "méthode", "findField": "champ", "getPrivateValue": "champ", "setPrivateValue": "champ",
           "findConstructor": "constructeur"}

    def __init__(self, index, fichier):
        self.ix, self.f, self.t = index, fichier, fichier.t
        self.sites = []
        self.vars_type = {}    # (id(type), nom) -> fqn d'une variable Class<?> (champ)
        self.vars_unite = {}   # (id(unité), nom) -> fqn
        self.resultats = {}    # (id(type), nom) -> [Site] : variables Method / Field / Constructor
        self.types_valeurs = {}  # (id(unité), nom) -> fqn d'une valeur rendue par un appel par réflexion
        self.unites = []

    # ---------------------------------------------------------------- évaluation de petites expressions
    def constante(self, nom, ty, unite=None):
        """Valeur d'une constante chaîne : champ du type (ou de ses englobants), Classe.CONSTANTE, variable locale."""
        if unite is not None:
            v = self.locales_chaines(unite).get(nom)
            if v is not None:
                return v
        if "." in nom:
            cl, _, c = nom.rpartition(".")
            fq = self.ix.resoudre(cl, self.f, ty)
            cible = self.ix.type(fq) if fq else None
            if cible is None:
                return None
            ty, nom = cible, c
        k = ty
        while k is not None:
            m = k.champ(nom)
            if m is not None and m.init:
                if k.fichier is not self.f:
                    autre = LECTEURS.get(id(k.fichier))
                    if autre is None:
                        autre = LECTEURS[id(k.fichier)] = Lecteur(self.ix, k.fichier)
                    return autre.valeur(m.init[0], m.init[1], k)
                return self.valeur(m.init[0], m.init[1], k)
            k = k.parent
        return None

    def locales_chaines(self, unite):
        cle = ("ch", id(unite))
        if cle not in self.vars_unite:
            t, out = self.t, {}
            for i in range(unite.a, unite.b - 2):
                if t[i].genre == "id" and t[i + 1].texte == "=" and t[i + 2].genre == "str" and \
                        t[i + 3].texte in (";", ",") and i > 0 and t[i - 1].texte == "String":
                    out[t[i].texte] = sj.chaine(t[i + 2].texte)
            self.vars_unite[cle] = out
        return self.vars_unite[cle]

    def valeur(self, a, b, ty, unite=None):
        """Chaîne d'une expression « "a" + CONST + "b" » entre a et b, ou None."""
        t, out, i = self.t, [], a
        while i < b:
            x = t[i]
            if x.genre == "str":
                out.append(sj.chaine(x.texte))
                i += 1
            elif x.texte == "+":
                i += 1
            elif x.genre == "id":
                j, nom = i, [x.texte]
                while j + 2 < b + 1 and t[j + 1].texte == "." and t[j + 2].genre == "id":
                    nom.append(t[j + 2].texte)
                    j += 2
                v = self.constante(".".join(nom), ty, unite)
                if v is None and b - a == 1 and unite is not None and unite.membre is not None \
                        and unite.membre.genre == "méthode":
                    # paramètre de la méthode : gabarit, remplacé plus tard par la valeur passée à chaque appel
                    for q, (tp, nm) in enumerate(unite.membre.params):
                        if nm == x.texte and tp == "String":
                            return "\0param:%d" % q
                if v is None:
                    return None
                out.append(v)
                i = j + 1
            elif x.texte == "(" and t[b - 1].texte == ")" and i == a:
                a, b, i = a + 1, b - 1, a + 1
            else:
                return None
        return "".join(out) if out else None

    def args(self, i):
        """Arguments de l'appel dont la parenthèse ouvrante est en i : [(début, fin)] et fin (parenthèse fermante)."""
        t, fin = self.t, sj.fermant(self.t, i)
        out, debut, p = [], i + 1, 0
        for k in range(i + 1, fin):
            x = t[k].texte
            if x in ("(", "[", "{"):
                p += 1
            elif x in (")", "]", "}"):
                p -= 1
            elif x == "," and p == 0:
                out.append((debut, k))
                debut = k + 1
        if debut < fin:
            out.append((debut, fin))
        return out, fin

    def expr_classe(self, a, b, ty, unite):
        """Classe désignée par l'expression [a, b) : 'X.class', Class.forName(...), variable. -> (fqn | None, texte)."""
        t = self.t
        texte = " ".join(x.texte for x in t[a:b])
        # retire une conversion (Class<...>) en tête
        while a < b and t[a].texte == "(" and sj.fermant(t, a) < b - 1:
            e = sj.fermant(t, a)
            if all(x.genre == "id" or x.texte in ("<", ">", "?", ",", ".", "extends") for x in t[a + 1:e]):
                a = e + 1
            else:
                break
        if a >= b:
            return None, texte
        # X.class (ou int.class, X[].class)
        if b - a >= 3 and t[b - 1].texte == "class" and t[b - 2].texte == ".":
            nom = "".join(x.texte for x in t[a:b - 2])
            return self.ix.resoudre(nom, self.f, ty) or nom, texte
        # Class.forName(...) / ....loadClass(...) éventuellement suivi de .asSubclass(...)
        for k in range(a, b):
            if t[k].texte in ("forName", "loadClass") and k + 1 < b and t[k + 1].texte == "(":
                ar, fin = self.args(k + 1)
                if ar:
                    v = self.valeur(ar[0][0], ar[0][1], ty, unite)
                    return v, texte
        # variable
        if b - a == 1 and t[a].genre == "id":
            v = self.vars_unite.get((id(unite), t[a].texte))
            if v is None:
                k = ty
                while k is not None and v is None:
                    v = self.vars_type.get((id(k), t[a].texte))
                    k = k.parent
            return v, texte
        if b - a == 3 and t[a].texte == "this" and t[a + 1].texte == ".":
            return self.vars_type.get((id(ty), t[a + 2].texte)), texte
        # aide sans paramètre du même fichier qui rend la classe : « Class<?> c = eco(); »
        if b - a == 3 and t[a].genre == "id" and t[a + 1].texte == "(" and t[a + 2].texte == ")":
            return self.classe_rendue(t[a].texte), texte
        # « x.getClass() » où x est le résultat d'un appel par réflexion déjà lu (type rendu par la cible)
        if b - a == 5 and t[a].genre == "id" and t[a + 2].texte == "getClass" and t[a + 3].texte == "(":
            return self.types_valeurs.get((id(unite), t[a].texte)), texte
        return None, texte

    def classe_rendue(self, nom):
        """Classe que rend une méthode sans paramètre du fichier (un seul Class.forName("…") dans son corps)."""
        for k in self.f.tous_les_types():
            for m in k.methodes(nom):
                if m.params or m.debut is None:
                    continue
                vals = []
                for q in range(m.debut, m.fin):
                    if self.t[q].texte == "forName" and self.t[q + 1].texte == "(":
                        ar, _ = self.args(q + 1)
                        if ar:
                            vals.append(self.valeur(ar[0][0], ar[0][1], k))
                vals = {v for v in vals if v}
                if len(vals) == 1:
                    return vals.pop()
        return None

    def retour_cible(self, s):
        """Type rendu par la méthode visée (nos classes seulement), pour suivre « x = m.invoke(…); x.getClass() »."""
        if s.classe is None or s.genre != "méthode" or not s.membre:
            return None
        ty = self.ix.type(s.classe)
        while ty is not None:
            for m in ty.methodes(s.membre):
                if s.types is None or len(m.params) == len(s.types):
                    return self.ix.resoudre(m.retour, ty.fichier, ty)
            fq = self.ix.resoudre(ty.mere, ty.fichier, ty.parent or ty) if ty.mere else None
            ty = self.ix.type(fq) if fq else None
        return None

    def liste_types(self, morceaux, ty, unite):
        """Types des paramètres (morceaux d'arguments) -> [fqn] ou None si l'un n'est pas lisible."""
        t, out = self.t, []
        for (a, b) in morceaux:
            # new Class<?>[]{A.class, B.class} / new Class[]{...}
            if t[a].texte == "new" and t[a + 1].texte == "Class":
                k = a
                while k < b and t[k].texte != "{":
                    k += 1
                if k >= b:
                    return None   # new Class<?>[n]
                e = sj.fermant(t, k, "{", "}")
                sous, _ = self.args_entre(k, e)
                r = self.liste_types(sous, ty, unite)
                if r is None:
                    return None
                out.extend(r)
                continue
            fq, _ = self.expr_classe(a, b, ty, unite)
            if fq is None:
                return None
            out.append(fq)
        return out

    def args_entre(self, i, fin):
        t, out, debut, p = self.t, [], i + 1, 0
        for k in range(i + 1, fin):
            x = t[k].texte
            if x in ("(", "[", "{"):
                p += 1
            elif x in (")", "]", "}"):
                p -= 1
            elif x == "," and p == 0:
                out.append((debut, k))
                debut = k + 1
        if debut < fin:
            out.append((debut, fin))
        return out, fin

    def method_type(self, a, b, ty, unite):
        """MethodType.methodType(R, P...) -> (retour, [params]) ou (None, None)."""
        t = self.t
        for k in range(a, b):
            if t[k].texte == "methodType" and t[k + 1].texte == "(":
                ar, _ = self.args(k + 1)
                if not ar:
                    return None, None
                r, _ = self.expr_classe(ar[0][0], ar[0][1], ty, unite)
                ps = self.liste_types(ar[1:], ty, unite)
                return r, ps
        return None, None

    # ---------------------------------------------------------------- relevé
    def unites_du_type(self, ty):
        out = []
        for m in ty.membres:
            if m.debut is not None:
                out.append(Unite(self.f, ty, m, m.debut, m.fin + 1))
            elif m.init is not None:
                out.append(Unite(self.f, ty, m, m.init[0], m.init[1]))
        return out

    def lire(self):
        types = self.f.tous_les_types()
        for ty in types:
            self.unites += self.unites_du_type(ty)
        # 1er passage : variables Class<?> (champs et locales)
        for u in self.unites:
            self.variables_classe(u)
        # 2e passage : appels
        for u in self.unites:
            self.appels(u)
        # utilisation des résultats rangés dans des variables (invoke(null…), conversions, setAccessible)
        for u in self.unites:
            self.usages(u)
        self.gabarits()
        return [s for s in self.sites if not (s.classe or "").startswith("\0") and not (s.membre or "").startswith("\0")]

    def gabarits(self):
        """Aide « f(String classe) { Class.forName(classe)…getMethod("x") } » : un site par appel f("a.b.C") du fichier."""
        t = self.t
        modeles = [s for s in self.sites if (s.classe or "").startswith("\0param:") or (s.membre or "").startswith("\0param:")]
        for s in modeles:
            q = int(s.classe.split(":")[1]) if (s.classe or "").startswith("\0") else None
            m = s.unite.membre
            variadique = m.params and m.params[-1][0].endswith("...")
            for u in self.unites:
                for k in range(u.a, u.b - 1):
                    if t[k].texte != m.nom or t[k + 1].texte != "(" or t[k - 1].texte in (".",) and t[k - 2].texte not in ("this",):
                        continue
                    if u.membre is m and u.a <= k <= u.a:
                        continue
                    ar, _ = self.args(k + 1)
                    if len(ar) != len(m.params) and not (variadique and len(ar) >= len(m.params) - 1):
                        continue
                    v = self.valeur(ar[q][0], ar[q][1], u.type, u) if q is not None else s.classe
                    if not v or v.startswith("\0"):
                        continue
                    membre = s.membre
                    if membre and membre.startswith("\0param:"):
                        qm = int(membre.split(":")[1])
                        membre = self.valeur(ar[qm][0], ar[qm][1], u.type, u) if qm < len(ar) else None
                        if not membre or membre.startswith("\0"):
                            continue
                    types = s.types
                    if getattr(s, "types_param", None) is not None:
                        qt = s.types_param
                        types = self.liste_types(ar[qt:] if variadique and qt == len(m.params) - 1 else ar[qt:qt + 1],
                                                 u.type, u)
                    c = Site(u, k, s.genre)
                    c.classe, c.membre, c.types, c.retour = v, membre, types, s.retour
                    c.statique, c.declare, c.accessible = s.statique, s.declare, s.accessible
                    c.protection = s.protection or u.protege(k, TOLERE_MEMBRE | TOLERE_CLASSE)
                    c.lookup = getattr(s, "lookup", None)
                    c.via = "%s (%s)" % (m.nom, s.lieu())
                    self.sites.append(c)

    def variables_classe(self, u):
        t = self.t
        if u.membre.genre == "champ" and u.membre.init:
            fq, _ = self.expr_classe(u.a, u.b, u.type, u)
            if fq and any(x.texte in ("forName", "loadClass") for x in t[u.a:u.b]):
                self.vars_type[(id(u.type), u.membre.nom)] = fq
        for i in range(u.a, u.b - 1):
            if t[i].genre == "id" and t[i + 1].texte == "=" and t[i + 2:i + 3] and t[i - 1].texte != ".":
                # fin de l'expression affectée
                j, p = i + 2, 0
                while j < u.b:
                    x = t[j].texte
                    if x in ("(", "[", "{"):
                        p += 1
                    elif x in (")", "]", "}"):
                        if p == 0:
                            break
                        p -= 1
                    elif x in (";", ",") and p == 0:
                        break
                    j += 1
                seg = t[i + 2:j]
                if len(seg) == 3 and seg[0].genre == "id" and seg[1].texte == "(" and seg[2].texte == ")":
                    fq = self.classe_rendue(seg[0].texte)   # « Class<?> c = eco(); »
                    if fq:
                        self.vars_unite[(id(u), t[i].texte)] = fq
                    continue
                if not any(x.texte in ("forName", "loadClass") for x in seg):
                    continue
                # seulement si l'expression EST la classe (pas Class.forName(..).getMethod(..))
                if any(x.texte in self.LOOKUPS or x.texte in ("invoke", "newInstance", "get") for x in seg):
                    continue
                fq, _ = self.expr_classe(i + 2, j, u.type, u)
                if fq:
                    self.vars_unite[(id(u), t[i].texte)] = fq
                    if u.type.champ(t[i].texte) is not None:
                        self.vars_type[(id(u.type), t[i].texte)] = fq

    def debut_chaine(self, k, u):
        """Début de l'expression en chaîne qui se termine juste avant le jeton k (« . » de .getMethod)."""
        t, j = self.t, k - 1
        while j >= u.a:
            x = t[j]
            if x.texte == ")":
                j2, p = j, 0
                while j2 >= u.a:
                    if t[j2].texte == ")":
                        p += 1
                    elif t[j2].texte == "(":
                        p -= 1
                        if p == 0:
                            break
                    j2 -= 1
                j = j2 - 1
                continue
            if x.genre == "id" or x.texte == "class":
                if j - 1 >= u.a and t[j - 1].texte == ".":
                    j -= 2
                    continue
                if j - 1 >= u.a and t[j - 1].texte == "]" and t[j - 2].texte == "[":
                    j -= 3
                    continue
                return j
            if x.texte == "]" and t[j - 1].texte == "[":
                j -= 2
                continue
            return j + 1
        return u.a

    def affectation(self, u, k):
        """« NOM = M.invoke(…) » (M : Method déjà lu) et « Class<?> c = NOM.getClass() » : on suit le type."""
        t = self.t
        nom = t[k - 1].texte
        if t[k + 1].genre == "id" and t[k + 2].texte == "." and t[k + 3].texte == "invoke":
            k2 = u.type
            sites = None
            while k2 is not None and sites is None:
                sites = self.resultats.get((id(k2), t[k + 1].texte))
                k2 = k2.parent
            if sites:
                r = self.retour_cible(sites[0])
                if r:
                    self.types_valeurs[(id(u), nom)] = r
        elif t[k + 1].genre == "id" and t[k + 2].texte == "." and t[k + 3].texte == "getClass" and t[k + 6].texte == ";":
            r = self.types_valeurs.get((id(u), t[k + 1].texte))
            if r:
                self.vars_unite[(id(u), nom)] = r

    def appels(self, u):
        t, ty = self.t, u.type
        for k in range(u.a, u.b - 2):
            x = t[k]
            if x.texte == "=" and x.genre == "op" and t[k - 1].genre == "id" and k + 6 < u.b:
                self.affectation(u, k)
                continue
            if x.genre != "id" or t[k + 1].texte != "(":
                continue
            nom = x.texte
            precede = t[k - 1].texte if k > 0 else ""
            if nom in ("forName", "loadClass") and precede == ".":
                # Class.forName("...") seul (la suite éventuelle .getMethod est lue par son propre jeton)
                ar, fin = self.args(k + 1)
                if not ar:
                    continue
                s = Site(u, k, "classe")
                s.classe = self.valeur(ar[0][0], ar[0][1], ty, u)
                if s.classe is None:
                    s.classe_dyn = " ".join(z.texte for z in t[ar[0][0]:ar[0][1]])
                s.protection = u.protege(k, TOLERE_CLASSE)
                self.sites.append(s)
            elif nom in self.LOOKUPS and precede == ".":
                debut = self.debut_chaine(k - 1, u)
                fq, texte = self.expr_classe(debut, k - 1, ty, u)
                ar, fin = self.args(k + 1)
                s = Site(u, k, self.LOOKUPS[nom])
                s.declare = "Declared" in nom
                s.classe = fq
                if fq is None:
                    s.classe_dyn = texte
                if s.genre == "constructeur":
                    s.types = self.liste_types(ar, ty, u)
                else:
                    if not ar:
                        continue
                    s.membre = self.valeur(ar[0][0], ar[0][1], ty, u)
                    s.types = self.liste_types(ar[1:], ty, u) if s.genre == "méthode" else []
                # types passés en paramètre de la méthode (aide « (String classe, String nom, Class<?>... types) »)
                reste = ar if s.genre == "constructeur" else ar[1:]
                if s.types is None and len(reste) == 1 and reste[0][1] - reste[0][0] == 1 and u.membre is not None:
                    for q, (tp, nm) in enumerate(u.membre.params):
                        if nm == t[reste[0][0]].texte and tp.startswith("Class"):
                            s.types_param = q
                s.protection = u.protege(k, TOLERE_MEMBRE | TOLERE_CLASSE)
                self.suite(s, debut, fin, u)
                self.sites.append(s)
            elif nom in self.HANDLES and precede == ".":
                ar, fin = self.args(k + 1)
                if len(ar) < 2:
                    continue
                s = Site(u, k, self.HANDLES[nom])
                fq, texte = self.expr_classe(ar[0][0], ar[0][1], ty, u)
                s.classe = fq
                if fq is None:
                    s.classe_dyn = texte
                if nom == "findConstructor":
                    r, ps = self.method_type(ar[1][0], ar[1][1], ty, u)
                    s.types = ps
                else:
                    s.membre = self.valeur(ar[1][0], ar[1][1], ty, u)
                    if len(ar) < 3:
                        continue
                    if s.genre == "méthode":
                        r, ps = self.method_type(ar[2][0], ar[2][1], ty, u)
                        s.retour, s.types = r, ps
                    else:
                        r, _ = self.expr_classe(ar[2][0], ar[2][1], ty, u)
                        s.retour, s.types = r, []
                s.statique = nom.startswith(("findStatic",)) or (False if nom in ("findVirtual", "findGetter", "findSetter", "findVarHandle", "findSpecial") else None)
                s.lookup = self.genre_lookup(self.debut_chaine(k - 1, u), k - 1, u) or "appelant"
                s.declare = s.lookup != "public"
                s.protection = u.protege(k, TOLERE_MEMBRE | TOLERE_CLASSE)
                self.sites.append(s)
            elif nom in self.ORH and precede == "." and t[k - 2].texte == "ObfuscationReflectionHelper":
                ar, fin = self.args(k + 1)
                if not ar:
                    continue
                s = Site(u, k, self.ORH[nom])
                fq, texte = self.expr_classe(ar[0][0], ar[0][1], ty, u)
                s.classe, s.declare, s.accessible = fq, True, True
                if fq is None:
                    s.classe_dyn = texte
                if nom == "findConstructor":
                    s.types = self.liste_types(ar[1:], ty, u)
                elif nom == "findMethod":
                    s.membre = self.valeur(ar[1][0], ar[1][1], ty, u) if len(ar) > 1 else None
                    s.types = self.liste_types(ar[2:], ty, u)
                elif nom == "findField":
                    s.membre = self.valeur(ar[1][0], ar[1][1], ty, u) if len(ar) > 1 else None
                    s.types = []
                else:
                    s.membre = self.valeur(ar[-1][0], ar[-1][1], ty, u)
                    s.types = []
                    s.statique = (t[ar[1][0]].texte == "null" and ar[1][1] - ar[1][0] == 1)
                s.protection = u.protege(k, TOLERE_MEMBRE | TOLERE_CLASSE | {"UnableToFindFieldException",
                                                                            "UnableToFindMethodException",
                                                                            "UnableToAccessFieldException",
                                                                            "RuntimeException"})
                s.orh = True
                self.suite(s, k - 2, fin, u)
                self.sites.append(s)
            else:
                self.enveloppe(u, k)

    def enveloppe(self, u, k):
        """Petite fonction d'aide : f("a.b.Classe", "methode", Type.class…) / new Appel(…)."""
        t = self.t
        ar, fin = self.args(k + 1)
        if len(ar) < 2:
            return
        a0, a1 = ar[0], ar[1]
        if t[a0[0]].genre != "str" and not (t[a0[0]].genre == "id" and a0[1] - a0[0] <= 3):
            return
        c = self.valeur(a0[0], a0[1], u.type, u)
        m = self.valeur(a1[0], a1[1], u.type, u)
        if not c or not m or not NOM_CLASSE.match(c) or not re.fullmatch(r"[A-Za-z_$][\w$]*", m):
            return
        appele = self.aide(t[k].texte, len(ar), u.type)
        if appele is None:
            return
        s = Site(u, k, "méthode")
        s.classe, s.membre = c, m
        s.types = self.liste_types(ar[2:], u.type, u) if len(ar) > 2 else []
        # le reste des arguments peut contenir les valeurs passées : on ne garde que les types lisibles en tête
        if s.types is None and len(ar) > 2:
            s.types = self.liste_types(ar[2:3], u.type, u)
        s.statique = appele[1]
        s.protection = "try" if appele[2] else u.protege(k, TOLERE_MEMBRE | TOLERE_CLASSE)
        s.via = appele[0]
        self.sites.append(s)

    def genre_lookup(self, a, b, u):
        """Lookup qui cherche : 'public' (publicLookup), 'appelant' (lookup()), 'prive' (privateLookupIn), ou None."""
        t = self.t
        seg = [x.texte for x in t[a:b]]
        if len(seg) == 1 and t[a].genre == "id":
            # variable : locale de l'unité (« Lookup l = MethodHandles.publicLookup(); »), sinon champ du type
            nom = seg[0]
            trouve = False
            for q in range(u.a, a):
                if t[q].texte == nom and t[q + 1].texte == "=":
                    e = q + 2
                    while e < u.b and t[e].texte != ";":
                        e += 1
                    seg = [x.texte for x in t[q + 2:e]]
                    trouve = True
            k = u.type
            while k is not None and not trouve:
                m = k.champ(nom)
                if m is not None and m.init:
                    seg = [x.texte for x in t[m.init[0]:m.init[1]]]
                    break
                k = k.parent
        if "privateLookupIn" in seg:
            return "prive"
        if "publicLookup" in seg:
            return "public"
        if "lookup" in seg:
            return "appelant"
        return None

    def aide(self, nom, nargs, ty):
        """Fonction d'aide « (String classe, String nom, Class<?>... types) » du fichier qui porte ce nom :
        (description, statique exigé, protégée par try) ou None."""
        for k in self.f.tous_les_types():
            for m in k.membres:
                if m.genre not in ("méthode", "constructeur"):
                    continue
                if (m.genre == "méthode" and m.nom != nom) or (m.genre == "constructeur" and k.nom != nom):
                    continue
                ps = [p[0] for p in m.params]
                if len(ps) < 2 or ps[0] != "String" or ps[1] != "String":
                    continue
                if len(ps) > 2 and not ps[2].startswith("Class"):
                    continue
                corps = self.t[k.debut:k.fin]
                if not any(x.texte in ("getMethod", "getDeclaredMethod") for x in corps):
                    continue
                statique = any(corps[q].texte == "invoke" and corps[q + 1].texte == "(" and corps[q + 2].texte == "null"
                               for q in range(len(corps) - 2))
                try_ = any(x.texte == "catch" for x in corps)
                return ("%s.%s" % (k.nom, nom), statique, try_)
        return None

    def suite(self, s, debut, fin, u):
        """Ce qui suit l'appel (.invoke(null…), .get(null)), la conversion qui le précède, la variable qui le garde."""
        t = self.t
        j = fin + 1
        if j + 2 < u.b and t[j].texte == "." and t[j + 1].texte in ("invoke", "get", "getInt", "getBoolean",
                                                                    "getLong", "getDouble", "getFloat", "set",
                                                                    "newInstance") and t[j + 2].texte == "(":
            if t[j + 1].texte != "newInstance" and t[j + 3].texte == "null":
                s.statique = True
            r = self.conversion(debut)
            if r:
                s.retour = r
            # « Object g = c.getMethod("x").invoke(s); » : g a le type rendu par x (pour g.getClass() ensuite)
            if debut - 2 >= u.a and t[debut - 1].texte == "=" and t[debut - 2].genre == "id":
                rc = self.retour_cible(s)
                if rc:
                    self.types_valeurs[(id(u), t[debut - 2].texte)] = rc
            return   # le résultat gardé est celui de l'appel, pas le Method / Field
        if j + 1 < u.b and t[j].texte == "." and t[j + 1].texte not in ("setAccessible",):
            return   # .getName(), .getReturnType()… : rien à suivre
        # variable qui reçoit le résultat : « NOM = <chaîne> » ou « Type NOM = <chaîne> »
        if debut - 2 >= u.a and t[debut - 1].texte == "=" and t[debut - 2].genre == "id":
            s.var = t[debut - 2].texte
            self.resultats.setdefault((id(u.type), s.var), []).append(s)
        elif debut - 4 >= u.a and t[debut - 1].texte == ")" and t[debut - 3].texte == "(" and t[debut - 4].texte == "=":
            s.var = t[debut - 5].texte
            self.resultats.setdefault((id(u.type), s.var), []).append(s)
        elif u.membre.genre == "champ" and u.membre.init and u.membre.init[0] == debut:
            s.var = u.membre.nom
            self.resultats.setdefault((id(u.type), s.var), []).append(s)
        # return Class.forName(...).getMethod(...) : variable = ce que rend la méthode (cas des petites aides)

    def conversion(self, debut):
        """Type de la conversion « (T) » juste avant le jeton debut, ou None."""
        t = self.t
        if debut - 1 >= 0 and t[debut - 1].texte == ")":
            j = debut - 1
            p = 0
            while j >= 0:
                if t[j].texte == ")":
                    p += 1
                elif t[j].texte == "(":
                    p -= 1
                    if p == 0:
                        break
                j -= 1
            seg = t[j + 1:debut - 1]
            if seg and all(x.genre == "id" or x.texte in (".", "<", ">", "?", ",", "[", "]") for x in seg) and \
                    seg[0].genre == "id" and seg[0].texte not in ("if", "while", "for", "switch", "return", "catch"):
                return "".join(x.texte for x in seg)
        return None

    def usages(self, u):
        """invoke(null, …) / get(null) / setAccessible(true) / (T) var.invoke(…) sur une variable Method / Field."""
        t = self.t
        for i in range(u.a, u.b - 3):
            if t[i].genre != "id" or t[i + 1].texte != ".":
                continue
            k = u.type
            sites = None
            while k is not None and sites is None:
                sites = self.resultats.get((id(k), t[i].texte))
                k = k.parent
            if not sites:
                continue
            op = t[i + 2].texte
            if op == "setAccessible" and t[i + 4].texte == "true":
                for s in sites:
                    s.accessible = True
            elif op in ("invoke", "get", "getInt", "getBoolean", "getLong", "getDouble", "getFloat", "set") and \
                    t[i + 3].texte == "(":
                r = self.conversion(i)
                for s in sites:
                    # invoke(objet, …) marche aussi sur une méthode static (l'objet est ignoré) : seul null compte
                    if t[i + 4].texte == "null" and op != "set" or (op == "set" and t[i + 4].texte == "null"):
                        if s.statique is None:
                            s.statique = True
                    if r and not s.retour and op != "set":
                        s.retour = r


# ====================================================================== contrôle
class Verif:
    def __init__(self, index, jars, mc, toml, corr):
        self.ix, self.jars, self.mc, self.toml, self.corr = index, jars, mc, toml, corr
        self.erreurs, self.avert, self.ok, self.facultatifs = [], [], [], []
        self.racines = tuple(sorted({".".join(p.split(".")[:2]) + "." for p in index.paquets if p.count(".") >= 1}))

    def controler_mc(self, s, lieu, quoi):
        """Classe de Minecraft / Forge sans Minecraft sous la main : ce que les correspondances en disent."""
        if s.genre == "classe" or s.membre is None:
            self.avert.append((lieu, "%s : classe de Minecraft / Forge, non vérifiable ici (Minecraft absent)" % quoi))
            return
        orh = getattr(s, "orh", False)
        cl = s.classe
        if cl.startswith(("com.mojang.brigadier.", "com.mojang.authlib.", "com.mojang.serialization.",
                          "com.mojang.datafixers.", "com.mojang.logging.", "net.minecraftforge.")):
            self.avert.append((lieu, "%s : bibliothèque de Mojang / Forge (noms jamais renommés), non vérifiable ici" % quoi))
            return
        if SRG.match(s.membre):
            if self.corr.srg_connu(cl, s.membre):
                self.ok.append((lieu, quoi + " (nom SRG vu dans un autre jar)"))
            else:
                self.avert.append((lieu, "%s : nom SRG non confirmé (Minecraft absent ; aucun autre jar ne l'utilise)" % quoi))
            return
        if orh:
            self.erreurs.append((lieu, "%s : ObfuscationReflectionHelper attend le nom SRG (f_…_ / m_…_), pas « %s »" % (
                quoi, s.membre)))
            return
        ajout = self.corr.ajoute(cl, s.membre)
        if ajout:
            if ajout == {"bukkit"} and s.cote != "serveur":
                self.erreurs.append((lieu, "%s : membre ajouté par Bukkit (Mohist, serveur seulement), appelé dans du code "
                                           "%s" % (quoi, s.cote)))
            else:
                self.ok.append((lieu, quoi + " (membre en clair ajouté par %s, vu dans le Forge de Mohist)" % "/".join(sorted(ajout))))
            return
        srg = self.corr.srg_de(cl, s.membre)
        if srg:
            self.erreurs.append((lieu, "%s : nom de Mojang en clair ; en jeu ce membre s'appelle %s : introuvable sur le "
                                       "serveur et chez les joueurs (passer par ObfuscationReflectionHelper avec ce nom)" % (
                                           quoi, " / ".join(sorted({x[1] for x in srg})))))
            return
        self.avert.append((lieu, "%s : nom en clair sur une classe de Minecraft, non vérifiable ici : il n'existe en jeu que "
                                 "s'il est ajouté par Forge ou Bukkit (sinon le nom est SRG)" % quoi))

    # ------------------------------------------------------------- où est la classe
    def trouver(self, fqn):
        """-> ('nous', Type) | ('jar', ClasseBin) | ('mc', None) | ('java', None) | (None, None)"""
        if fqn is None:
            return None, None
        base = fqn
        while base.endswith("[]"):
            base = base[:-2]
        if base in sj.PRIMITIFS:
            return "java", None
        ty = self.ix.type(base)
        if ty is not None:
            return "nous", ty
        c = self.jars.classe(base)
        if c is not None:
            return "jar", c
        if self.mc:
            for m in self.mc.values():
                c = m.classe(base.replace(".", "/"))
                if c is not None:
                    return "mc", c
        if base.startswith(MINECRAFT) or base.startswith("net.minecraftforge."):
            return "mc", None
        if base.startswith(("java.", "javax.", "jdk.", "sun.")):
            return "java", None
        return None, None

    # ------------------------------------------------------------- membres de nos classes
    def erasure(self, texte, ty):
        fq = self.ix.resoudre(texte, ty.fichier, ty)
        if fq is None:
            base = re.sub(r"<.*>", "", texte).replace("...", "[]")
            return base
        return fq

    def nos_membres(self, ty, site):
        """Candidats (Membre, type déclarant) du nom voulu dans le type et ses ascendants à nous ; et mère externe."""
        vus, out, externes = set(), [], []
        k = ty
        pile = [ty]
        while pile:
            k = pile.pop(0)
            if k.fqn in vus:
                continue
            vus.add(k.fqn)
            for m in k.membres:
                if site.genre == "constructeur":
                    if m.genre == "constructeur" and k is ty:
                        out.append((m, k))
                elif m.genre == site.genre and m.nom == site.membre:
                    out.append((m, k))
            if site.genre == "constructeur" or (site.declare and getattr(site, "lookup", None) is None):
                break
            for parent in ([k.mere] if k.mere else []) + k.interfaces:
                fq = self.ix.resoudre(parent, k.fichier, k.parent or k)
                pt = self.ix.type(fq) if fq else None
                if pt is not None:
                    pile.append(pt)
                elif fq:
                    externes.append(fq)
        return out, externes

    def params_de(self, m, ty):
        return [self.erasure(p[0], ty) for p in m.params]

    def statique_de(self, m, ty):
        return "static" in m.mods

    # ------------------------------------------------------------- contrôle d'un site
    def controler(self, s):
        lieu = s.lieu()
        if s.classe is None:
            if s.genre == "classe":
                self.avert.append((lieu, "Class.forName(%s) : nom de classe calculé, non vérifié" % (s.classe_dyn or "?")))
            elif s.membre and s.classe_dyn and s.classe_dyn not in ("this", "getClass ( )"):
                self.avert.append((lieu, "%s %s sur « %s » : classe non lisible dans le code, non vérifié" % (
                    s.genre, s.membre, s.classe_dyn)))
            return
        ou, cible = self.trouver(s.classe)
        quoi = s.classe + (("." + s.membre) if s.membre else "") + (
            "(" + ", ".join(x.rsplit(".", 1)[-1] for x in s.types) + ")" if s.types is not None and s.genre != "champ" and s.genre != "classe" else "")
        if ou is None:
            nous = s.classe.startswith(self.racines)
            if s.classe.startswith(("org.bukkit.", "org.spigotmc.", "com.mohistmc.", "net.md_5.")) and not self.jars.cote_bukkit:
                self.avert.append((lieu, "%s : API Bukkit / Mohist, non vérifiée (passer --forge <jar universal de Mohist>)"
                                   % s.classe))
                return
            if nous or not s.protection:
                self.erreurs.append((lieu, "classe introuvable : %s (ni dans nos modules, ni dans les jars des autres "
                                           "auteurs)%s" % (s.classe, "" if s.protection else ", appel non protégé")))
            else:
                self.facultatifs.append((lieu, "%s : mod facultatif absent du pack (appel protégé : sans effet)" % s.classe))
            return
        if ou == "java":
            self.ok.append((lieu, quoi + " (Java)"))
            return
        if ou == "mc" and cible is None:
            self.controler_mc(s, lieu, quoi)
            return
        if ou == "nous":
            self.controler_nous(s, cible, lieu, quoi)
        else:
            self.controler_jar(s, cible, lieu, quoi, ou)

    def cote_cible(self, ty):
        f = ty.fichier
        return f.cote, f.module

    def module_obligatoire(self, mod_appelant, mod_cible):
        """Le mods.toml du module appelant déclare-t-il un des modId du module cible obligatoire ?"""
        ids_cible = self.toml.get(mod_cible, {}).get("ids", set())
        deps = self.toml.get(mod_appelant, {}).get("deps", {})
        return any(deps.get(i) for i in ids_cible) or mod_appelant == mod_cible

    def controler_nous(self, s, ty, lieu, quoi):
        f_app = s.unite.fichier
        cote_c, mod_c = self.cote_cible(ty)
        # côté
        if mod_c in sj.JAMAIS_LIVRES:
            (self.avert if s.protection else self.erreurs).append((lieu, "%s : dans le module « %s », jamais livré : "
                                                                  "absent en jeu%s" % (quoi, mod_c, "" if s.protection else
                                                                                       " et l'appel n'est pas protégé")))
            return
        if ty.fichier.ensemble not in sj.ENSEMBLES:
            self.erreurs.append((lieu, "%s : classe de %s/src/%s, jamais dans un jar" % (quoi, mod_c, ty.fichier.ensemble)))
            return
        ca = s.cote
        if ca == "serveur" and cote_c == "client":
            self.erreurs.append((lieu, "%s : appel écrit côté serveur vers une classe du joueur (%s/src/%s) : absente du "
                                       "serveur" % (quoi, mod_c, ty.fichier.ensemble)))
            return
        if ca == "client" and cote_c == "serveur":
            self.erreurs.append((lieu, "%s : appel écrit côté joueur vers une classe du serveur (%s/src/serveur) : "
                                       "absente chez les joueurs" % (quoi, mod_c)))
            return
        if ca == "commun" and cote_c in ("client", "serveur") and not s.protection:
            self.erreurs.append((lieu, "%s : appel écrit dans src/commun (les deux côtés) vers une classe seulement %s "
                                       "(%s/src/%s), sans try/catch" % (quoi, "chez les joueurs" if cote_c == "client" else
                                                                       "sur le serveur", mod_c, ty.fichier.ensemble)))
            return
        if mod_c != f_app.module and not s.protection and not self.module_obligatoire(f_app.module, mod_c):
            self.erreurs.append((lieu, "%s : module « %s » pas déclaré obligatoire dans le mods.toml de « %s », et "
                                       "l'appel n'est pas protégé (try/catch)" % (quoi, mod_c, f_app.module)))
        if s.genre == "classe":
            self.ok.append((lieu, quoi))
            return
        if s.membre is None and s.genre != "constructeur":
            self.avert.append((lieu, "%s : nom du membre calculé, non vérifié" % quoi))
            return
        cands, externes = self.nos_membres(ty, s)
        if not cands:
            if externes:
                self.avert.append((lieu, "%s : absent de nos classes, peut-être hérité de %s (non vérifié)" % (
                    quoi, ", ".join(externes))))
                return
            if s.genre == "constructeur" and not any(m.genre == "constructeur" for m in ty.membres) and not s.types:
                self.ok.append((lieu, quoi + " (constructeur par défaut)"))
                return
            self.erreurs.append((lieu, "%s : %s introuvable dans %s (%s:%d)" % (
                quoi, s.genre, ty.fqn, rel(ty.fichier.chemin), ty.ligne)))
            return
        bons = cands
        if s.genre != "champ" and s.types is not None:
            bons = [(m, k) for (m, k) in cands if self.params_de(m, k) == s.types]
            if not bons:
                proposes = "; ".join("%s(%s) %s:%d" % (m.nom if m.genre != "constructeur" else k.nom,
                                                      ", ".join(self.params_de(m, k)),
                                                      rel(k.fichier.chemin), m.ligne)
                                     for (m, k) in cands)
                self.erreurs.append((lieu, "%s : pas de %s avec ces paramètres (%s). Existants : %s" % (
                    quoi, s.genre, ", ".join(s.types), proposes)))
                return
        elif s.types is None and s.genre != "champ":
            if len(cands) == 1:
                self.avert.append((lieu, "%s : types des paramètres non lisibles dans le code ; seul candidat %s(%s)" % (
                    quoi, s.membre or "<init>", ", ".join(self.params_de(*cands[0])))))
            else:
                self.avert.append((lieu, "%s : types des paramètres non lisibles, %d candidats" % (quoi, len(cands))))
            return
        m, k = bons[0]
        problemes = []
        public = "public" in m.mods or (k.genre in ("interface", "annotation") and "private" not in m.mods)
        if not s.declare and not public:
            problemes.append("%s n'est pas public (get%s ne voit que le public)" % (
                s.genre, {"méthode": "Method", "champ": "Field", "constructeur": "Constructor"}[s.genre]))
        meme_paquet = k.fichier.paquet == s.unite.fichier.paquet
        lookup = getattr(s, "lookup", None)
        if lookup == "public" and (not public or not k.publique()):
            problemes.append("MethodHandles.publicLookup() ne voit que les membres publics des classes publiques")
        elif lookup == "appelant" and "private" in m.mods and k.fqn.split("$")[0] != s.unite.type.fqn.split("$")[0]:
            problemes.append("MethodHandles.lookup() ne voit pas un membre private d'une autre classe")
        elif lookup == "appelant" and not public and not meme_paquet and "protected" not in m.mods:
            problemes.append("MethodHandles.lookup() ne voit pas un membre non public d'un autre paquet")
        if s.declare and lookup is None and not public and not s.accessible and not getattr(s, "orh", False):
            problemes.append("membre non public lu par getDeclared… sans setAccessible(true) : IllegalAccessException")
        if lookup is None and not k.publique() and not s.accessible and not meme_paquet and s.genre != "classe":
            problemes.append("la classe %s n'est pas publique : invoke / get refusés (IllegalAccessException) sans "
                             "setAccessible(true)" % k.fqn)
        if s.statique is True and "static" not in m.mods and s.genre != "constructeur":
            problemes.append("appelé sans objet (null / findStatic) mais le %s n'est pas static" % s.genre)
        if s.statique is False and "static" in m.mods and s.genre == "méthode":
            problemes.append("findVirtual sur une méthode static")
        if s.retour:
            r = self.erasure(m.retour, k) if s.genre == "méthode" else self.erasure(m.retour, k) if s.genre == "champ" else None
            attendu = self.ix.resoudre(s.retour, s.unite.fichier, s.unite.type) or s.retour
            if r and not self.compatible(r, attendu):
                problemes.append("résultat lu comme %s, mais le %s rend %s" % (attendu, s.genre, r))
        if problemes:
            self.erreurs.append((lieu, "%s (%s:%d) : %s" % (quoi, rel(k.fichier.chemin), m.ligne,
                                                            " ; ".join(problemes))))
        else:
            self.ok.append((lieu, quoi))

    def compatible(self, reel, attendu):
        """Le résultat réel peut-il être converti dans le type attendu sans ClassCastException ?"""
        a = attendu.rsplit(".", 1)[-1]
        r_court = reel.rsplit(".", 1)[-1]
        if reel == "void" or attendu == "void":
            return reel == attendu
        if attendu in ("java.lang.Object", "Object"):
            return True
        if a in ("Number", "Comparable", "Serializable"):
            return reel in sj.PRIMITIFS and reel not in ("boolean", "char") or r_court in (
                "Integer", "Long", "Double", "Float", "Short", "Byte", "BigInteger", "BigDecimal", "Number")
        prim_r = reel if reel in sj.PRIMITIFS else ENVELOPPES.get(r_court) if reel.startswith("java.lang.") else None
        prim_a = attendu if attendu in sj.PRIMITIFS else ENVELOPPES.get(a)
        if prim_r or prim_a:
            return prim_r == prim_a
        if reel == attendu or r_court == a:
            return True
        # sous-classe connue ?
        ty = self.ix.type(reel)
        vus = set()
        while ty is not None and ty.fqn not in vus:
            vus.add(ty.fqn)
            for p in ([ty.mere] if ty.mere else []) + ty.interfaces:
                fq = self.ix.resoudre(p, ty.fichier, ty.parent or ty)
                if fq == attendu or (fq or "").rsplit(".", 1)[-1] == a:
                    return True
            fq = self.ix.resoudre(ty.mere, ty.fichier, ty.parent or ty) if ty.mere else None
            ty = self.ix.type(fq) if fq else None
        # types d'autres auteurs, collections… : pas de verdict
        return not (reel.startswith(("fr.heraliz", "fr.heralisz")) and attendu.startswith(("fr.heraliz", "fr.heralisz")))

    def controler_jar(self, s, c, lieu, quoi, ou):
        origine = self.jars.origine(s.classe) if ou == "jar" else "Minecraft"
        cote_jar = self.jars.cote.get(s.classe.replace(".", "/")) if ou == "jar" else "les deux"
        if ou == "jar":
            if cote_jar == "compilation":
                (self.avert if s.protection else self.erreurs).append((lieu, "%s : classe seulement dans un jar de "
                                                                      "compilation (%s), livré nulle part%s" % (
                                                                          quoi, origine, "" if s.protection else
                                                                          " ; appel non protégé")))
                return
            if cote_jar == "joueur" and s.cote == "serveur":
                self.erreurs.append((lieu, "%s : mod seulement chez les joueurs (%s), appel écrit côté serveur" % (quoi, origine)))
                return
            if cote_jar.startswith("serveur") and s.cote == "client":
                self.erreurs.append((lieu, "%s : classe seulement sur le serveur (%s), appel écrit côté joueur" % (quoi, origine)))
                return
            if cote_jar.startswith("serveur") and s.cote == "commun" and not s.protection:
                self.erreurs.append((lieu, "%s : classe seulement sur le serveur (%s), appel écrit dans src/commun sans "
                                           "try/catch" % (quoi, origine)))
                return
            if cote_jar == "joueur" and s.cote == "commun" and not s.protection:
                self.erreurs.append((lieu, "%s : mod seulement chez les joueurs (%s), appel écrit dans src/commun sans "
                                           "try/catch" % (quoi, origine)))
                return
        if s.genre == "classe":
            self.ok.append((lieu, quoi + " [%s]" % origine))
            return
        if s.membre is None and s.genre != "constructeur":
            self.avert.append((lieu, "%s [%s] : nom du membre calculé, non vérifié" % (quoi, origine)))
            return
        # membres de la classe et de ses ascendants (dans les jars)
        nom_jvm = s.membre if s.genre != "constructeur" else "<init>"
        cands, vus, pile, hors = [], set(), [c], []
        while pile:
            k = pile.pop(0)
            if k is None or k.nom in vus:
                continue
            vus.add(k.nom)
            liste = k.champs if s.genre == "champ" else k.methodes
            for (ac, nm, ds) in liste:
                if nm == nom_jvm:
                    cands.append((ac, nm, ds, k))
            if s.genre == "constructeur" or (s.declare and getattr(s, "lookup", None) is None):
                break
            for p in [k.mere] + k.interfaces:
                if p is None or p == "java/lang/Object":
                    continue
                pk = self.jars.classe(p)
                if pk is None and self.mc:
                    for m in self.mc.values():
                        pk = m.classe(p)
                        if pk:
                            break
                if pk is None:
                    hors.append(p.replace("/", "."))
                elif isinstance(pk, ClasseBin):
                    pile.append(pk)
        if not cands:
            if hors:
                self.avert.append((lieu, "%s [%s] : absent de la classe, peut-être hérité de %s (non vérifiable ici)" % (
                    quoi, origine, ", ".join(hors[:3]))))
            else:
                self.erreurs.append((lieu, "%s [%s] : %s introuvable" % (quoi, origine, s.genre)))
            return
        bons = cands
        if s.genre != "champ" and s.types is not None:
            voulu = "(" + "".join(desc_type(x) for x in s.types) + ")"
            bons = [x for x in cands if x[2].startswith(voulu)]
            if not bons:
                self.erreurs.append((lieu, "%s [%s] : pas de %s avec ces paramètres. Existants : %s" % (
                    quoi, origine, s.genre, "; ".join("%s(%s)" % (x[1], ", ".join(params_desc(x[2])[0])) for x in cands))))
                return
        elif s.types is None and s.genre != "champ":
            self.avert.append((lieu, "%s [%s] : types des paramètres non lisibles ; existants : %s" % (
                quoi, origine, "; ".join("%s(%s)" % (x[1], ", ".join(params_desc(x[2])[0])) for x in cands))))
            return
        ac, nm, ds, k = bons[0]
        problemes = []
        if not s.declare and not (ac & 0x0001):
            problemes.append("%s non public" % s.genre)
        if s.statique is True and not (ac & 0x0008) and s.genre != "constructeur":
            problemes.append("appelé sans objet (null / findStatic) mais pas static")
        if s.statique is False and (ac & 0x0008) and s.genre == "méthode":
            problemes.append("findVirtual sur une méthode static")
        if not (k.acces & 0x0001) and not s.accessible and not s.declare:
            problemes.append("classe %s non publique" % k.nom)
        if s.retour:
            r = params_desc(ds)[1] if s.genre == "méthode" else type_desc(ds)
            attendu = self.ix.resoudre(s.retour, s.unite.fichier, s.unite.type) or s.retour
            if not self.compatible(r, attendu):
                problemes.append("résultat lu comme %s, mais rend %s" % (attendu, r))
        if problemes:
            self.erreurs.append((lieu, "%s [%s] : %s" % (quoi, origine, " ; ".join(problemes))))
        else:
            self.ok.append((lieu, quoi + " [%s]" % origine))

    # ------------------------------------------------------------- chaînes qui nomment nos classes
    def chaines(self, fichiers, racines):
        t_vus = 0
        # noms utilisés dans chaque module (pour reconnaître une constante que personne ne lit)
        utilises = {}
        for f in self.ix.fichiers:
            u = utilises.setdefault(f.module, {})
            for x in f.t:
                if x.genre == "id":
                    u[x.texte] = u.get(x.texte, 0) + 1
        for f in fichiers:
            for i, x in enumerate(f.t):
                if x.genre != "str":
                    continue
                v = sj.chaine(x.texte)
                if not NOM_CLASSE.match(v) or not v.startswith(racines):
                    continue
                # déjà vu comme Class.forName / aide ?
                t_vus += 1
                lieu = "%s:%d" % (rel(f.chemin), x.ligne)
                ty = self.ix.type(v)
                if ty is None:
                    if self.jars.classe(v) is not None:
                        continue
                    # chaîne qui ressemble à une classe mais n'en est pas une (clé de texte, nom de fichier…)
                    if v.split(".")[-1][:1].isupper() and not v.endswith((".Json", ".json")):
                        # constante « static final String X = "..." » que rien ne lit : reste mort, sans effet
                        nom = f.t[i - 2].texte if i >= 2 and f.t[i - 1].texte == "=" and f.t[i + 1].texte == ";" else None
                        if nom and utilises.get(f.module, {}).get(nom, 0) <= 1:
                            self.avert.append((lieu, "« %s » : classe inexistante, dans la constante %s que rien ne lit "
                                                     "(reste mort)" % (v, nom)))
                        else:
                            self.erreurs.append((lieu, "« %s » : nomme une classe qui n'existe dans aucun module" % v))
                    continue
                mod_c = ty.fichier.module
                if mod_c in sj.JAMAIS_LIVRES and f.module not in sj.JAMAIS_LIVRES:
                    self.avert.append((lieu, "« %s » : classe du module « %s », jamais livré" % (v, mod_c)))
                if v.endswith(".PartieServeur") and ty.fichier.ensemble != "serveur":
                    self.erreurs.append((lieu, "« %s » : partie serveur hors de src/serveur (%s)" % (v, ty.fichier.ensemble)))
                if v.endswith(".PartieClient") and ty.fichier.ensemble not in ("client", "main"):
                    self.erreurs.append((lieu, "« %s » : partie client hors de src/client (%s)" % (v, ty.fichier.ensemble)))
        return t_vus


# ====================================================================== références en dur entre modules (imports)
def references_dures(ix, jars, appelants, v):
    """Imports et noms qualifiés : un module compile contre un autre par devComplet (commun + client + serveur), le
    compilateur laisse donc src/serveur de A citer src/client de B, ou src/commun de A citer src/serveur de B ; en jeu,
    la classe manque de ce côté (NoClassDefFoundError). Même contrôle vers les mods des joueurs seulement
    (client/mods-tiers) et les plugins (serveur seulement). verif_split.py le voit sur les jars fabriqués, mais il ne
    tourne pas sans Minecraft (--sans-split de verifier.py)."""
    n = 0
    for f in appelants:
        refs = set(f.imports.values()) | {q.rsplit(".", 1)[0] for q in f.imports_statiques.values()}
        t = f.t
        i = 0
        while i < len(t):
            if t[i].genre == "id" and t[i].texte[:1].islower() and i + 2 < len(t) and t[i + 1].texte == "." and \
                    (i == 0 or t[i - 1].texte != "."):
                j, nom = i, [t[i].texte]
                while j + 2 < len(t) and t[j + 1].texte == "." and t[j + 2].genre == "id":
                    nom.append(t[j + 2].texte)
                    j += 2
                    if nom[-1][:1].isupper():
                        break
                if len(nom) >= 3 and nom[-1][:1].isupper() and all(x[:1].islower() for x in nom[:-1]):
                    refs.add(".".join(nom))
                i = j + 1
                continue
            i += 1
        lieu = rel(f.chemin)
        for r in sorted(refs):
            ty = ix.types.get(r) or ix.types.get(ix._imbrique(r))
            if ty is not None:
                mb, eb = ty.fichier.module, ty.fichier.cote
                if mb == f.module:
                    continue
                n += 1
                if mb in sj.JAMAIS_LIVRES:
                    v.erreurs.append((lieu, "cite %s du module « %s », jamais livré" % (r, mb)))
                elif f.cote == "serveur" and eb == "client":
                    v.erreurs.append((lieu, "côté serveur, cite %s (%s/src/%s : absent du serveur)" % (
                        r, mb, ty.fichier.ensemble)))
                elif f.cote == "client" and eb == "serveur":
                    v.erreurs.append((lieu, "côté joueur, cite %s (%s/src/serveur : absent chez les joueurs)" % (r, mb)))
                elif f.cote == "commun" and eb in ("client", "serveur"):
                    v.erreurs.append((lieu, "src/commun (les deux côtés) cite %s (%s/src/%s : d'un seul côté)" % (
                        r, mb, ty.fichier.ensemble)))
                continue
            cote = jars.cote.get(r.replace(".", "/"))
            if cote is None or r.startswith(BIBLIOTHEQUES):
                continue
            n += 1
            if cote == "joueur" and f.cote in ("serveur", "commun"):
                v.erreurs.append((lieu, "côté %s, cite %s (%s : seulement chez les joueurs)" % (
                    f.cote, r, jars.origine(r))))
            elif cote.startswith("serveur") and f.cote in ("client", "commun") and not r.startswith("net.minecraftforge."):
                v.erreurs.append((lieu, "côté %s, cite %s (%s : seulement sur le serveur)" % (
                    f.cote, r, jars.origine(r))))
    return n


def lire_tomls():
    """{module: {'ids': {modId}, 'deps': {modId: obligatoire}}} des mods.toml de nos modules."""
    out = {}

    def proprietes(p):
        return dict(re.findall(r"^([\w.]+)=(.*)$", p.read_text(encoding="utf-8"), re.M)) if p.exists() else {}
    racine = proprietes(RACINE / "gradle.properties")
    for mod in sorted(p for p in sj.MODULES.iterdir() if p.is_dir()):
        ids, deps = set(), {}
        props = {**racine, **proprietes(mod / "gradle.properties")}
        for t in mod.glob("src/*/resources/META-INF/mods.toml"):
            texte = re.sub(r'\$\{([^}]*)\}', lambda m: props.get(m.group(1), "0"), t.read_text(encoding="utf-8"))
            try:
                d = tomllib.loads(texte)
            except tomllib.TOMLDecodeError:
                continue
            ids |= {m.get("modId") for m in d.get("mods", [])}
            for _, liste in d.get("dependencies", {}).items():
                for dep in liste:
                    m = dep.get("modId")
                    ob = dep.get("mandatory", dep.get("type", "required") == "required")
                    deps[m] = deps.get(m, False) or bool(ob)
        out[mod.name] = {"ids": ids, "deps": deps}
    return out


def main():
    args = sys.argv[1:]
    tout = "--tout" in args
    js = None
    forge = None
    if "--json" in args:
        js = args[args.index("--json") + 1]
    if "--forge" in args:
        forge = args[args.index("--forge") + 1]
    choisis = [a for a in args if not a.startswith("--") and a not in (js, forge)]
    print("[verif_reflexion] lecture des sources…", flush=True)
    ix = sj.Index()
    jars = jars_tiers(forge)
    mc = minecraft()
    print("[verif_reflexion] %d fichiers, %d classes à nous ; %d classes dans les jars des autres auteurs ; Minecraft : %s"
          % (len(ix.fichiers), len(ix.types), len(jars.index), ", ".join(mc) if mc else "absent (non vérifié)"), flush=True)
    toml = lire_tomls()
    corr = correspondances(forge)
    print("[verif_reflexion] noms de Minecraft connus sans Minecraft : %d SRG, %d noms de Mojang, %d ajoutés par Forge/Bukkit"
          % (len(corr.srg), len(corr.mojang), len(corr.ajoutes)), flush=True)
    v = Verif(ix, jars, mc, toml, corr)
    appelants = [f for f in ix.fichiers if f.module not in sj.JAMAIS_LIVRES and f.cote
                 and (not choisis or f.module in choisis)]
    sites = []
    for f in appelants:
        sites += Lecteur(ix, f).lire()
    # un « Class.forName("X") » suivi d'un getMethod sur la même ligne est déjà contrôlé par le getMethod : la classe
    # seule n'est gardée que si rien d'autre ne la vise sur cette ligne
    membres = {(s.lieu(), s.classe) for s in sites if s.genre != "classe"}
    sites = [s for s in sites if s.genre != "classe" or (s.lieu(), s.classe) not in membres]
    for s in sites:
        v.controler(s)
    n_ch = v.chaines(appelants, v.racines)
    n_ref = references_dures(ix, jars, appelants, v)
    print("[verif_reflexion] %d appels par réflexion relevés, %d chaînes nommant une classe, %d références en dur vers "
          "un autre module ou un mod d'un seul côté" % (len(sites), n_ch, n_ref))
    for (lieu, msg) in sorted(set(v.erreurs)):
        print("   ERREUR %s : %s" % (lieu, msg))
    for (lieu, msg) in sorted(set(v.avert)):
        print("   avert. %s : %s" % (lieu, msg))
    if tout:
        for (lieu, msg) in sorted(set(v.facultatifs)):
            print("   info   %s : %s" % (lieu, msg))
        for (lieu, msg) in sorted(set(v.ok)):
            print("   ok     %s : %s" % (lieu, msg))
    else:
        print("[verif_reflexion] %d appel(s) vers des mods facultatifs absents du pack, protégés (--tout pour la liste)"
              % len(set(v.facultatifs)))
    if js:
        Path(js).write_text(json.dumps({"erreurs": sorted(set(v.erreurs)), "avertissements": sorted(set(v.avert)),
                                        "ok": sorted(set(v.ok))}, ensure_ascii=False, indent=1), encoding="utf-8")
    print("[verif_reflexion] %d vérifié(s) sans problème, %d avertissement(s), %d erreur(s)" % (
        len(set(v.ok)), len(set(v.avert)), len(set(v.erreurs))))
    sys.exit(1 if v.erreurs else 0)


if __name__ == "__main__":
    main()
