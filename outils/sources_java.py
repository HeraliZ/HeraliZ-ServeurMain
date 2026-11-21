"""Lecture légère des sources Java de nos modules (sans compilateur) : paquets, imports, classes, méthodes, champs,
constructeurs, corps des méthodes en jetons. Sert aux contrôles qui doivent tourner sans fabrication (pas de Gradle,
pas de Minecraft) : outils/verif_reflexion.py.

Ce n'est PAS un compilateur : il suffit pour retrouver une méthode par son nom et ses types de paramètres, savoir si
elle est publique et statique, et lire les chaînes et les blocs try/catch d'un corps de méthode. Les types sont
résolus par les imports du fichier (import simple, import « .* », même paquet, classes imbriquées, java.lang).
"""
import re
from pathlib import Path

RACINE = Path(__file__).resolve().parents[1]
MODULES = RACINE / "modules"

# modules jamais livrés (outils/construire.py : MODULES_DE_COTE, MODULES_A_PART, gabarit)
JAMAIS_LIVRES = ("events", "capture", "banc", "_gabarit")
# modules qui ne vont que chez les joueurs (un seul jar, sans partie serveur)
CLIENT_SEUL = ("interface",)
# dossiers de sources compilés et livrés -> côté
ENSEMBLES = {"commun": "commun", "client": "client", "serveur": "serveur", "main": "client"}

_JETON = re.compile(r'''
  (?P<ws>\s+)
| (?P<lc>//[^\n]*)
| (?P<bc>/\*.*?\*/)
| (?P<tb>"""(?:\\.|[^\\])*?""")
| (?P<str>"(?:\\.|[^"\\\n])*")
| (?P<chr>'(?:\\.|[^'\\\n])+')
| (?P<num>(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|\d[\d_]*(?:\.[\d_]*)?(?:[eE][+-]?\d+)?|\.\d[\d_]*(?:[eE][+-]?\d+)?)[lLfFdD]?)
| (?P<id>[A-Za-z_$][\w$]*)
| (?P<op>>>>=|<<=|>>=|\.\.\.|->|::|\+\+|--|&&|\|\||[=!<>+\-*/&|^%]=|[{}()\[\];,.@=<>!~?:+\-*/&|^%])
''', re.S | re.X)

PRIMITIFS = {"boolean": "Z", "byte": "B", "char": "C", "short": "S", "int": "I", "long": "J", "float": "F",
             "double": "D", "void": "V"}
MODIFICATEURS = {"public", "protected", "private", "static", "final", "abstract", "synchronized", "native",
                 "transient", "volatile", "strictfp", "default", "sealed", "non"}
JAVA_LANG = {"Object", "String", "Class", "Integer", "Long", "Double", "Float", "Boolean", "Byte", "Short",
             "Character", "Number", "Void", "Math", "System", "Thread", "Runnable", "Iterable", "Comparable",
             "Enum", "Record", "Throwable", "Exception", "RuntimeException", "Error", "CharSequence",
             "StringBuilder", "Override", "Deprecated", "SuppressWarnings", "FunctionalInterface", "SafeVarargs",
             "AutoCloseable", "Cloneable", "ClassLoader", "Module", "Process", "StackTraceElement", "ThreadLocal",
             "IllegalStateException", "IllegalArgumentException", "NullPointerException", "ClassCastException",
             "ReflectiveOperationException", "ClassNotFoundException", "NoSuchMethodException",
             "NoSuchFieldException", "IllegalAccessException", "InstantiationException", "LinkageError",
             "NoClassDefFoundError", "ExceptionInInitializerError", "UnsupportedOperationException",
             "IndexOutOfBoundsException", "ArrayIndexOutOfBoundsException", "SecurityException",
             "InterruptedException", "CloneNotSupportedException", "OutOfMemoryError", "StackOverflowError",
             "AssertionError", "ArithmeticException", "NumberFormatException", "Iterable", "Appendable",
             "Readable", "Package", "Runtime", "SecurityManager", "StrictMath", "InheritableThreadLocal",
             "VirtualMachineError", "IncompatibleClassChangeError", "AbstractMethodError", "NoSuchFieldError",
             "NoSuchMethodError", "BootstrapMethodError", "TypeNotPresentException", "ArrayStoreException",
             "NegativeArraySizeException", "EnumConstantNotPresentException", "IllegalMonitorStateException",
             "StringIndexOutOfBoundsException", "ProcessBuilder", "ClassValue", "StackWalker", "ScopedValue"}


class Jeton:
    __slots__ = ("genre", "texte", "ligne")

    def __init__(self, genre, texte, ligne):
        self.genre, self.texte, self.ligne = genre, texte, ligne

    def __repr__(self):
        return "%s:%r@%d" % (self.genre, self.texte, self.ligne)


def jetons(texte):
    out, ligne = [], 1
    for m in _JETON.finditer(texte):
        g = m.lastgroup
        s = m.group(g)
        if g not in ("ws", "lc", "bc"):
            out.append(Jeton(g, s, ligne))
        ligne += s.count("\n")
    return out


def chaine(texte):
    """Valeur d'un littéral "..." (échappements simples)."""
    s = texte[1:-1]
    return re.sub(r"\\(u[0-9a-fA-F]{4}|.)", lambda m: chr(int(m.group(1)[1:], 16)) if m.group(1)[0] == "u" else
                  {"n": "\n", "t": "\t", "r": "\r", "0": "\0", "b": "\b", "f": "\f"}.get(m.group(1), m.group(1)), s)


def fermant(t, i, ouvre="(", ferme=")"):
    """Indice du jeton qui ferme celui en i (même profondeur)."""
    p = 0
    for k in range(i, len(t)):
        x = t[k].texte
        if t[k].genre == "op":
            if x == ouvre:
                p += 1
            elif x == ferme:
                p -= 1
                if p == 0:
                    return k
    return len(t) - 1


def fermant_chevron(t, i):
    """Fin d'une liste de paramètres de type <...> commençant en i (jeton « < »)."""
    p = 0
    for k in range(i, len(t)):
        x = t[k].texte
        if x == "<":
            p += 1
        elif x == ">":
            p -= 1
            if p == 0:
                return k
        elif x in (";", "{", "}", "(", ")") and t[k].genre == "op":
            return k - 1
    return len(t) - 1


class Membre:
    """Méthode, constructeur ou champ d'une classe."""
    __slots__ = ("genre", "nom", "params", "retour", "mods", "ligne", "debut", "fin", "init", "annotations",
                 "type_params", "lance", "variadique")

    def __init__(self, genre, nom, ligne):
        self.genre, self.nom, self.ligne = genre, nom, ligne
        self.params = []        # [(texte du type, nom)]
        self.retour = None      # texte du type (méthode) ou du champ
        self.mods = set()
        self.debut = self.fin = None   # jetons du corps (méthode) : indices dans Fichier.t
        self.init = None        # (début, fin) de l'initialiseur d'un champ
        self.annotations = []   # [(nom, début, fin)] indices des jetons de l'annotation (@ compris)
        self.type_params = set()
        self.lance = []         # types de la clause throws
        self.variadique = False


class Type:
    """Classe, interface, enum, record ou annotation déclarée dans un fichier."""
    __slots__ = ("nom", "fqn", "genre", "mods", "mere", "interfaces", "membres", "types", "parent", "fichier",
                 "ligne", "annotations", "type_params", "debut", "fin", "constantes")

    def __init__(self, nom, genre, fichier, parent, ligne):
        self.nom, self.genre, self.fichier, self.parent, self.ligne = nom, genre, fichier, parent, ligne
        self.fqn = (parent.fqn + "$" + nom) if parent else ((fichier.paquet + "." + nom) if fichier.paquet else nom)
        self.mods = set()
        self.mere = None
        self.interfaces = []
        self.membres = []
        self.types = []
        self.annotations = []
        self.type_params = set()
        self.debut = self.fin = None
        self.constantes = []    # constantes d'enum

    def publique(self):
        if self.genre in ("interface", "annotation") and self.parent and self.parent.genre in ("interface", "annotation"):
            return True
        return "public" in self.mods

    def methodes(self, nom):
        return [m for m in self.membres if m.genre == "méthode" and m.nom == nom]

    def champ(self, nom):
        for m in self.membres:
            if m.genre == "champ" and m.nom == nom:
                return m
        return None


class Fichier:
    def __init__(self, chemin, module, ensemble):
        self.chemin, self.module, self.ensemble = chemin, module, ensemble
        self.cote = ENSEMBLES.get(ensemble)
        if module in CLIENT_SEUL and self.cote:
            self.cote = "client"
        self.paquet = ""
        self.imports = {}        # nom simple -> fqn
        self.imports_etoile = []  # paquets (ou classes) importés par « .* »
        self.imports_statiques = {}
        self.types = []
        self.t = jetons(chemin.read_text(encoding="utf-8", errors="replace"))
        self._lire()

    # ------------------------------------------------------------------ lecture
    def _lire(self):
        t, i, n = self.t, 0, len(self.t)
        mods, annots = set(), []
        while i < n:
            x = t[i].texte
            if x == "package":
                j = i + 1
                nom = []
                while j < n and t[j].texte != ";":
                    nom.append(t[j].texte)
                    j += 1
                self.paquet = "".join(nom)
                i = j + 1
            elif x == "import":
                j = i + 1
                statique = t[j].texte == "static"
                if statique:
                    j += 1
                nom = []
                while j < n and t[j].texte != ";":
                    nom.append(t[j].texte)
                    j += 1
                q = "".join(nom)
                if statique:
                    self.imports_statiques[q.rsplit(".", 1)[-1]] = q
                elif q.endswith(".*"):
                    self.imports_etoile.append(q[:-2])
                else:
                    self.imports[q.rsplit(".", 1)[-1]] = q
                i = j + 1
            elif x == "@" and i + 1 < n and t[i + 1].texte != "interface":
                k = self._annotation(i)
                annots.append((self._nom_annot(i), i, k))
                i = k + 1
            elif x in MODIFICATEURS:
                mods.add(x)
                i += 1
            elif x in ("class", "interface", "enum") or (x == "record" and i + 2 < n and t[i + 2].texte in ("(", "<")) \
                    or (x == "@" and t[i + 1].texte == "interface"):
                i = self._type(i, None, mods, annots)
                mods, annots = set(), []
            else:
                i += 1

    def _nom_annot(self, i):
        t, j, nom = self.t, i + 1, []
        while j < len(t) and t[j].genre == "id":
            nom.append(t[j].texte)
            if j + 1 < len(t) and t[j + 1].texte == ".":
                j += 2
            else:
                break
        return ".".join(nom)

    def _annotation(self, i):
        """Fin (incluse) de l'annotation qui commence en i (@)."""
        t, j = self.t, i + 1
        while j < len(t) and (t[j].genre == "id" or t[j].texte == "."):
            if t[j].genre == "id" and j + 1 < len(t) and t[j + 1].texte == ".":
                j += 2
                continue
            j += 1
            break
        if j < len(t) and t[j].texte == "(":
            return fermant(t, j)
        return j - 1

    def _type(self, i, parent, mods, annots):
        t, n = self.t, len(self.t)
        x = t[i].texte
        genre = {"class": "classe", "interface": "interface", "enum": "enum", "record": "record"}.get(x, "annotation")
        if x == "@":
            i += 1
        nom = t[i + 1].texte
        ty = Type(nom, genre, self, parent, t[i + 1].ligne)
        ty.mods, ty.annotations = set(mods), list(annots)
        (parent.types if parent else self.types).append(ty)
        j = i + 2
        if j < n and t[j].texte == "<":
            k = fermant_chevron(t, j)
            ty.type_params = {t[a].texte for a in range(j + 1, k) if t[a].genre == "id" and t[a - 1].texte in ("<", ",")}
            j = k + 1
        composants = []
        if genre == "record" and t[j].texte == "(":
            k = fermant(t, j)
            composants = self._params(j + 1, k)
            j = k + 1
        etat = None
        while j < n and t[j].texte != "{":
            if t[j].texte == "extends":
                etat = "extends"
            elif t[j].texte == "implements":
                etat = "implements"
            elif t[j].texte == "permits":
                etat = None
            elif t[j].genre == "id" and etat:
                q, j = self._nom_qualifie(j)
                if t[j].texte == "<":
                    j = fermant_chevron(t, j) + 1
                if etat == "extends" and genre == "classe":
                    ty.mere = q
                else:
                    ty.interfaces.append(q)
                continue
            j += 1
        fin = fermant(t, j, "{", "}")
        ty.debut, ty.fin = j, fin
        for (tp, nm) in composants:
            c = Membre("champ", nm, ty.ligne)
            c.retour, c.mods = tp, {"private", "final"}
            ty.membres.append(c)
            a = Membre("méthode", nm, ty.ligne)
            a.retour, a.mods = tp, {"public"}
            ty.membres.append(a)
        if composants:
            k = Membre("constructeur", "<init>", ty.ligne)
            k.params, k.mods = composants, {"public"}
            ty.membres.append(k)
        self._corps(ty, j + 1, fin)
        return fin + 1

    def _nom_qualifie(self, j):
        t, nom = self.t, [self.t[j].texte]
        j += 1
        while j + 1 < len(t) and t[j].texte == "." and t[j + 1].genre == "id":
            nom.append(t[j + 1].texte)
            j += 2
        return ".".join(nom), j

    def _params(self, a, b):
        """[(type, nom)] des paramètres entre a et b (exclu)."""
        t, out, debut, p = self.t, [], a, 0
        morceaux = []
        for k in range(a, b + 1):
            x = t[k].texte if k < b else ","
            if k < b and x in ("<", "(", "["):
                p += 1
            elif k < b and x in (">", ")", "]"):
                p -= 1
            if x == "," and p == 0:
                morceaux.append((debut, k))
                debut = k + 1
        for (u, v) in morceaux:
            toks = [t[k] for k in range(u, v)]
            # retire annotations et final
            propres, k = [], 0
            while k < len(toks):
                if toks[k].texte == "@":
                    k += 1
                    while k + 1 < len(toks) and toks[k + 1].texte == ".":
                        k += 2
                    k += 1
                    if k < len(toks) and toks[k].texte == "(":
                        p2 = 0
                        while k < len(toks):
                            if toks[k].texte == "(":
                                p2 += 1
                            elif toks[k].texte == ")":
                                p2 -= 1
                                if p2 == 0:
                                    break
                            k += 1
                        k += 1
                    continue
                if toks[k].texte == "final":
                    k += 1
                    continue
                propres.append(toks[k])
                k += 1
            if len(propres) < 2:
                continue
            nom = propres[-1].texte
            dims = ""
            while len(propres) > 2 and propres[-1].texte == "]":
                propres = propres[:-2]
                dims += "[]"
                nom = propres[-1].texte
            tp = "".join(x.texte for x in propres[:-1]) + dims
            out.append((tp, nom))
        return out

    def _corps(self, ty, a, b):
        """Membres du corps de type entre a et b (exclu)."""
        t = self.t
        j = a
        if ty.genre == "enum":
            # constantes jusqu'au premier ; de profondeur 0 (ou la fin)
            k = j
            while k < b:
                x = t[k].texte
                if x == ";":
                    break
                if x == "@":
                    k = self._annotation(k) + 1
                    continue
                if t[k].genre == "id":
                    ty.constantes.append(t[k].texte)
                    c = Membre("champ", t[k].texte, t[k].ligne)
                    c.retour, c.mods = ty.nom, {"public", "static", "final"}
                    ty.membres.append(c)
                    k += 1
                    if k < b and t[k].texte == "(":
                        k = fermant(t, k) + 1
                    if k < b and t[k].texte == "{":
                        k = fermant(t, k, "{", "}") + 1
                    continue
                k += 1
            j = k + 1
        mods, annots, type_params = set(), [], set()
        while j < b:
            x = t[j].texte
            if x == ";":
                j += 1
                mods, annots, type_params = set(), [], set()
                continue
            if x == "{":
                k = fermant(t, j, "{", "}")
                m = Membre("bloc", "<clinit>" if "static" in mods else "<bloc>", t[j].ligne)
                m.debut, m.fin, m.mods = j, k, mods
                ty.membres.append(m)
                j = k + 1
                mods, annots, type_params = set(), [], set()
                continue
            if x == "@" and t[j + 1].texte != "interface":
                k = self._annotation(j)
                annots.append((self._nom_annot(j), j, k))
                j = k + 1
                continue
            if x in MODIFICATEURS:
                mods.add(x)
                j += 1
                if x == "non" and t[j].texte == "-":
                    j += 2
                continue
            if x in ("class", "interface", "enum") or (x == "record" and t[j + 2].texte in ("(", "<")) \
                    or (x == "@" and t[j + 1].texte == "interface"):
                j = self._type(j, ty, mods, annots)
                mods, annots, type_params = set(), [], set()
                continue
            if x == "<":
                k = fermant_chevron(t, j)
                type_params = {t[q].texte for q in range(j + 1, k) if t[q].genre == "id" and t[q - 1].texte in ("<", ",")}
                j = k + 1
                continue
            if t[j].genre == "id" and t[j].texte == ty.nom and t[j + 1].texte in ("(", "{"):
                # constructeur (ou constructeur compact d'un record)
                m = Membre("constructeur", "<init>", t[j].ligne)
                m.mods, m.annotations, m.type_params = mods, annots, type_params
                if t[j + 1].texte == "(":
                    k = fermant(t, j + 1)
                    m.params = self._params(j + 2, k)
                    m.variadique = any(p[0].endswith("...") for p in m.params)
                    j = k + 1
                else:
                    m.genre = "bloc"
                    m.nom = "<compact>"
                while j < b and t[j].texte not in ("{", ";"):
                    if t[j].texte == "throws":
                        pass
                    elif t[j].genre == "id" and t[j - 1].texte in ("throws", ","):
                        m.lance.append(t[j].texte)
                    j += 1
                if t[j].texte == "{":
                    k = fermant(t, j, "{", "}")
                    m.debut, m.fin = j, k
                    j = k + 1
                else:
                    j += 1
                ty.membres.append(m)
                mods, annots, type_params = set(), [], set()
                continue
            if t[j].genre == "id":
                # type puis nom
                k = j
                tp = []
                while k < b:
                    y = t[k]
                    if y.texte == "@":
                        k = self._annotation(k) + 1
                        continue
                    if y.genre == "id":
                        tp.append(y.texte)
                        k += 1
                        if k < b and t[k].texte == "<":
                            e = fermant_chevron(t, k)
                            tp.extend(z.texte for z in t[k:e + 1])
                            k = e + 1
                        if k < b and t[k].texte == ".":
                            tp.append(".")
                            k += 1
                            continue
                        while k + 1 < b and t[k].texte == "[" and t[k + 1].texte == "]":
                            tp.append("[]")
                            k += 2
                        if k < b and t[k].texte == "...":
                            tp.append("...")
                            k += 1
                        break
                    break
                if k >= b or t[k].genre != "id":
                    j = k + 1
                    mods, annots, type_params = set(), [], set()
                    continue
                nom = t[k].texte
                texte_type = "".join(tp)
                if k + 1 < b and t[k + 1].texte == "(":
                    m = Membre("méthode", nom, t[k].ligne)
                    m.retour, m.mods, m.annotations, m.type_params = texte_type, mods, annots, type_params
                    e = fermant(t, k + 1)
                    m.params = self._params(k + 2, e)
                    m.variadique = any(p[0].endswith("...") for p in m.params)
                    j = e + 1
                    while j < b and t[j].texte not in ("{", ";"):
                        if t[j].genre == "id" and t[j - 1].texte in ("throws", ",") and t[j].texte != "throws":
                            m.lance.append(t[j].texte)
                        if t[j].texte == "default":
                            # valeur par défaut d'un membre d'annotation
                            while j < b and t[j].texte != ";":
                                if t[j].texte in ("(", "{"):
                                    j = fermant(t, j, t[j].texte, ")" if t[j].texte == "(" else "}")
                                j += 1
                            break
                        j += 1
                    if j < b and t[j].texte == "{":
                        e2 = fermant(t, j, "{", "}")
                        m.debut, m.fin = j, e2
                        j = e2 + 1
                    else:
                        j += 1
                    if ty.genre in ("interface", "annotation"):
                        if "private" not in m.mods:
                            m.mods = set(m.mods) | {"public"}
                        if m.debut is None and "static" not in m.mods:
                            m.mods.add("abstract")
                    ty.membres.append(m)
                else:
                    # champ(s)
                    k2 = k
                    while k2 < b:
                        nm = t[k2].texte
                        dims = ""
                        k2 += 1
                        while k2 + 1 < b and t[k2].texte == "[" and t[k2 + 1].texte == "]":
                            dims += "[]"
                            k2 += 2
                        c = Membre("champ", nm, t[k2 - 1].ligne)
                        c.retour, c.mods, c.annotations = texte_type + dims, mods, annots
                        if ty.genre in ("interface", "annotation"):
                            c.mods = set(mods) | {"public", "static", "final"}
                        if k2 < b and t[k2].texte == "=":
                            d = k2 + 1
                            p = 0
                            while k2 < b:
                                y = t[k2].texte
                                if y in ("(", "[", "{"):
                                    p += 1
                                elif y in (")", "]", "}"):
                                    p -= 1
                                elif y in (",", ";") and p == 0:
                                    break
                                k2 += 1
                            c.init = (d, k2)
                        ty.membres.append(c)
                        if k2 < b and t[k2].texte == ",":
                            k2 += 1
                            continue
                        break
                    j = k2 + 1
                mods, annots, type_params = set(), [], set()
                continue
            j += 1

    # ------------------------------------------------------------------ outils
    def tous_les_types(self):
        out = []

        def visiter(ty):
            out.append(ty)
            for s in ty.types:
                visiter(s)
        for ty in self.types:
            visiter(ty)
        return out


class Index:
    """Toutes nos classes : fqn (avec $ pour les imbriquées) -> Type ; paquets connus."""

    def __init__(self, modules=None):
        self.types, self.fichiers, self.paquets = {}, [], set()
        for mod in sorted(p for p in MODULES.iterdir() if p.is_dir()):
            if modules and mod.name not in modules:
                continue
            src = mod / "src"
            if not src.is_dir():
                continue
            for ens in sorted(p for p in src.iterdir() if p.is_dir()):
                java = ens / "java"
                if not java.is_dir():
                    continue
                for f in sorted(java.rglob("*.java")):
                    fi = Fichier(f, mod.name, ens.name)
                    self.fichiers.append(fi)
                    self.paquets.add(fi.paquet)
                    for ty in fi.tous_les_types():
                        # même classe dans deux ensembles (ne devrait pas arriver) : on garde la première
                        self.types.setdefault(ty.fqn, ty)

    def type(self, fqn):
        return self.types.get(fqn.replace("/", "."))

    def resoudre(self, nom, fichier, dans=None):
        """Nom de type écrit dans le fichier (simple ou qualifié, sans <>) -> fqn (avec $), ou None si inconnu.
        `dans` : type englobant (classes imbriquées, paramètres de type)."""
        nom = re.sub(r"<.*>", "", nom).replace("...", "[]")
        dims = ""
        while nom.endswith("[]"):
            nom, dims = nom[:-2], dims + "[]"
        if nom in PRIMITIFS:
            return nom + dims
        tete, _, reste = nom.partition(".")
        # paramètres de type
        ty = dans
        while ty is not None:
            if tete in ty.type_params:
                return "java.lang.Object" + dims
            ty = ty.parent
        base = None
        # imbriquées du type englobant et de ses parents
        ty = dans
        while ty is not None and base is None:
            for s in ty.types:
                if s.nom == tete:
                    base = s.fqn
            if base is None and ty.nom == tete:
                base = ty.fqn
            ty = ty.parent
        if base is None:
            for s in fichier.types:
                if s.nom == tete:
                    base = s.fqn
        if base is None and tete in fichier.imports:
            base = fichier.imports[tete]
            if base not in self.types:
                # import d'une classe imbriquée : a.b.C.D -> a.b.C$D si connu
                base = self._imbrique(base)
        if base is None:
            q = (fichier.paquet + "." + tete) if fichier.paquet else tete
            if q in self.types:
                base = q
        if base is None:
            for p in fichier.imports_etoile:
                q = p + "." + tete
                if q in self.types or self._imbrique(q) in self.types:
                    base = self._imbrique(q)
                    break
                if p.rsplit(".", 1)[-1][:1].isupper():
                    q2 = self._imbrique(p) + "$" + tete
                    if q2 in self.types:
                        base = q2
                        break
        if base is None and tete in JAVA_LANG:
            base = "java.lang." + tete
        if base is None:
            if "." in nom and nom[:1].islower():
                # nom déjà qualifié (a.b.C.D)
                return self._imbrique(nom) + dims
            return None
        if reste:
            base = base + "$" + reste.replace(".", "$")
        return base + dims

    def _imbrique(self, q):
        """a.b.C.D écrit avec des points -> a.b.C$D quand a.b.C est connu."""
        if q in self.types:
            return q
        morceaux = q.split(".")
        for k in range(len(morceaux) - 1, 0, -1):
            tete = ".".join(morceaux[:k])
            if tete in self.types:
                return tete + "$" + "$".join(morceaux[k:])
        return q
