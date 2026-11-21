"""Mixins de nos modules contre leurs vraies cibles, sans fabrication (audit du 21/11).

    python outils/verif_mixins.py [modules...] [--tout] [--forge <jar de Mohist ou son jar universal de Forge>]

Pour chaque config *.mixins.json livrée (tous les modules sauf events, capture, banc, _gabarit) :
  - la config : sa table de correspondance (refmap) est-elle celle que la fabrication met dans le même jar, est-elle
    listée dans le manifeste MixinConfigs du jar (sinon elle n'est jamais chargée), chaque classe listée existe-t-elle
    dans le même ensemble de sources, une classe @Mixin est-elle oubliée dans toutes les configs ;
  - « require » : defaultRequire absent ou 0, require = 0 : une injection qui ne trouve plus sa cible ne dit rien ;
  - chaque cible d'un AUTRE AUTEUR (TaCZ, Curios, GeckoLib, véhicules, Backpacked…, jars de serveur/mods-tiers,
    client/mods-tiers et libs/) : la classe existe, chaque méthode visée existe (nom et descripteur), les appels visés
    par @At(INVOKE / FIELD) sont bien dans le code de la méthode, et les noms SRG / de Mojang sont ceux qu'il faut selon
    remap (un jar d'un autre auteur garde ses noms mais appelle Minecraft en noms SRG) ;
  - chaque cible de MINECRAFT : sans Minecraft sous la main, seulement ce que les autres jars en disent (appels SRG,
    refmap, accesstransformer : outils/verif_reflexion.py, classe Correspondances) ; un nom de Mojang écrit avec
    remap = false est une erreur (introuvable en jeu) ;
  - conflits : même appel redirigé par un @Redirect d'un autre mod du pack (ou de deux de nos mixins), méthode
    remplacée par un @Overwrite ; les mixins de ModernFix dont l'option est coupée (serveur/config/
    modernfix-mixins.properties) ne comptent pas.
Ce que l'outil ne peut pas faire sans Minecraft : dire qu'une méthode de Minecraft visée existe encore (seulement ce que
les autres jars en disent), ni voir les changements de Mohist dans le corps des méthodes ; le démarrage d'essai
(outils/demarrage_essai.py) le voit pour les injections obligatoires des classes chargées au démarrage.
Code de sortie 1 si une erreur est trouvée.
"""
import io
import json
import re
import struct
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import sources_java as sj  # noqa: E402
import verif_reflexion as vr  # noqa: E402

RACINE = sj.RACINE
SRG = re.compile(r"^(f|m)_\d+_$")
INJECTEURS = {"Inject", "Redirect", "ModifyArg", "ModifyArgs", "ModifyVariable", "ModifyConstant", "Overwrite",
              "WrapOperation", "WrapWithCondition", "ModifyExpressionValue", "ModifyReturnValue", "ModifyReceiver"}
MC = ("net/minecraft/", "com/mojang/math/", "com/mojang/blaze3d/")
COUPE = re.compile(r"^\s*apply\s+from:.*module-coupe\.gradle", re.M)


# ====================================================================== bytecode (méthodes et leurs appels)
class ClasseCode:
    """Classe d'un jar : méthodes [(accès, nom, descripteur, [(op, classe, nom, desc)])], champs, mère, interfaces,
    annotations invisibles (mixins des autres mods)."""

    OPS = {0xB2: "GETSTATIC", 0xB3: "PUTSTATIC", 0xB4: "GETFIELD", 0xB5: "PUTFIELD", 0xB6: "INVOKEVIRTUAL",
           0xB7: "INVOKESPECIAL", 0xB8: "INVOKESTATIC", 0xB9: "INVOKEINTERFACE", 0xBB: "NEW"}

    def __init__(self, data):
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
                raise ValueError(tag)
            k += 1
        self.cp = cp
        utf = lambda x: cp[x][1]
        cls = lambda x: utf(cp[x][1])
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
                appels = []
                for _ in range(na):
                    an, ln = struct.unpack(">HI", data[i:i + 6])
                    if liste is self.methodes and utf(an) == "Code":
                        appels = self._code(data, i + 6)
                    i += 6 + ln
                liste.append((ac, utf(nm), utf(ds), appels))
        self.annotations = []
        cnt = struct.unpack(">H", data[i:i + 2])[0]
        i += 2
        for _ in range(cnt):
            an, ln = struct.unpack(">HI", data[i:i + 6])
            if utf(an) in ("RuntimeInvisibleAnnotations", "RuntimeVisibleAnnotations"):
                import verif_split as vs
                self.annotations.append(vs._annotations(data, i + 6, utf))
            i += 6 + ln

    def _ref(self, idx):
        e = self.cp[idx]
        cl = self.cp[self.cp[e[1]][1]][1]
        nt = self.cp[e[2]]
        return cl, self.cp[nt[1]][1], self.cp[nt[2]][1]

    def _code(self, data, i):
        lg = struct.unpack(">I", data[i + 4:i + 8])[0]
        code = data[i + 8:i + 8 + lg]
        pc, out = 0, []
        L = vr_long()
        while pc < lg:
            op = code[pc]
            if op in self.OPS and op != 0xBB:
                idx = struct.unpack(">H", code[pc + 1:pc + 3])[0]
                try:
                    out.append((self.OPS[op],) + self._ref(idx))
                except (TypeError, IndexError):
                    pass
            if op == 0xAA:
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
                pc += L.get(op, 1)
        return out


def vr_long():
    import verif_split as vs
    return vs._LONG


class JarsCode:
    def __init__(self):
        self.index, self.zips, self.cache = {}, {}, {}

    def ajouter(self, jar, etiquette=None):
        jar = Path(jar)
        if jar.exists():
            self._indexer(etiquette or jar.name, zipfile.ZipFile(jar), 3)

    def _indexer(self, cle, z, prof):
        self.zips[cle] = z
        for n in z.namelist():
            if n.endswith(".class") and not n.startswith("META-INF/versions/"):
                self.index.setdefault(n[:-6], (cle, n))
            elif prof > 0 and n.endswith(".jar") and n.startswith(("META-INF/jarjar/", "META-INF/jars/")):
                try:
                    self._indexer(cle + "!" + n.rsplit("/", 1)[-1], zipfile.ZipFile(io.BytesIO(z.read(n))), prof - 1)
                except zipfile.BadZipFile:
                    pass

    def classe(self, nom):
        nom = nom.replace(".", "/")
        if nom not in self.cache:
            c = None
            if nom in self.index:
                jar, n = self.index[nom]
                try:
                    c = ClasseCode(self.zips[jar].read(n))
                except (ValueError, struct.error, IndexError, KeyError):
                    c = None
            self.cache[nom] = c
        return self.cache[nom]

    def origine(self, nom):
        e = self.index.get(nom.replace(".", "/"))
        return e[0] if e else None


# ====================================================================== annotations des sources
def lire_annotation(lec, f, a, b, ty):
    """(nom, {clé: valeur}) de l'annotation entre les jetons a (@) et b (inclus)."""
    t = f.t
    nom = f._nom_annot(a)
    j = a + 1
    while j <= b and t[j].texte != "(":
        j += 1
    if j > b:
        return nom, {}
    morceaux, _ = lec.args(j)
    out = {}
    for (u, v) in morceaux:
        if v - u >= 2 and t[u].genre == "id" and t[u + 1].texte == "=":
            out[t[u].texte] = valeur(lec, f, u + 2, v, ty)
        else:
            out["value"] = valeur(lec, f, u, v, ty)
    return nom, out


def valeur(lec, f, a, b, ty):
    t = f.t
    if a >= b:
        return None
    x = t[a]
    if x.texte == "{":
        e = sj.fermant(t, a, "{", "}")
        sous, _ = lec.args_entre(a, e)
        return [valeur(lec, f, u, v, ty) for (u, v) in sous]
    if x.texte == "@":
        return lire_annotation(lec, f, a, b - 1, ty)
    if b - a >= 3 and t[b - 1].texte == "class" and t[b - 2].texte == ".":
        nom = "".join(z.texte for z in t[a:b - 2])
        return ("classe", lec.ix.resoudre(nom, f, ty) or nom)
    if x.texte in ("true", "false") and b - a == 1:
        return x.texte == "true"
    if x.genre == "num" and b - a == 1:
        try:
            return int(x.texte.rstrip("lL"), 0)
        except ValueError:
            return x.texte
    if x.texte == "-" and b - a == 2 and t[a + 1].genre == "num":
        return -int(t[a + 1].texte.rstrip("lL"), 0)
    s = lec.valeur(a, b, ty)
    if s is not None:
        return s
    return ("ident", "".join(z.texte for z in t[a:b]))


def liste(v):
    if v is None:
        return []
    return v if isinstance(v, list) else [v]


# ====================================================================== sélecteurs de Mixin
def selecteur(s):
    """'nom(desc)ret' / 'nom' / 'Lowner;nom(desc)' / '<init>' -> (owner|None, nom, desc|None). '*' : tout."""
    proprio = None
    if s.startswith("L") and ";" in s and "(" in s[s.index(";"):] + "(":
        proprio, s = s[1:s.index(";")], s[s.index(";") + 1:]
    m = re.match(r"([^(:]*)(\(.*|:.*)?$", s)
    nom, desc = m.group(1), m.group(2)
    if desc and desc.startswith(":"):
        desc = desc[1:]
    return proprio, nom, desc


class Rapport:
    def __init__(self):
        self.erreurs, self.avert, self.ok, self.infos = [], [], [], []


def configs_livrees():
    """[(module, ensemble, chemin)] des configs de mixins des modules livrés."""
    out = []
    for mod in sorted(p for p in sj.MODULES.iterdir() if p.is_dir()):
        if mod.name in sj.JAMAIS_LIVRES:
            continue
        for c in sorted(mod.glob("src/*/resources/*.mixins.json")):
            out.append((mod.name, c.parts[-3], c))
    return out


def manifeste_attendu(mod, ensemble, cfg):
    """Configs que la fabrication met dans le manifeste MixinConfigs (lu dans le build.gradle du module) :
    {jar: bool} ; None si la fabrication n'est pas reconnue."""
    b = (sj.MODULES / mod / "build.gradle").read_text(encoding="utf-8")
    if COUPE.search(b):
        return True   # module-coupe.gradle : toutes les configs de src/<ensemble>/resources (voir avecMixins)
    explicite = re.findall(r"'MixinConfigs'\s*:\s*'([^']+)'", b)
    if explicite:
        noms = {x.strip() for e in explicite for x in e.split(",")}
        return cfg.name in noms
    return None


def main():
    args = sys.argv[1:]
    tout = "--tout" in args
    forge = args[args.index("--forge") + 1] if "--forge" in args else None
    choisis = [a for a in args if not a.startswith("--") and a != forge]
    ix = sj.Index()
    corr = vr.correspondances(forge)
    jars = JarsCode()
    for f in sorted((RACINE / "serveur" / "mods-tiers").glob("*.jar")) + sorted((RACINE / "client" / "mods-tiers").glob("*.jar")):
        jars.ajouter(f)
    for f in sorted((RACINE / "libs").glob("*.jar")) + sorted((RACINE / "modules").glob("*/libs/*.jar")):
        jars.ajouter(f)
    if forge:
        jars._indexer("Forge/Bukkit de Mohist", vr.jar_forge(forge), 0)
    r = Rapport()
    toutes_listees = set()
    lecteurs = {}
    n_mixins = n_inj = 0
    for (mod, ens, chemin) in configs_livrees():
        if choisis and mod not in choisis:
            continue
        lieu_cfg = vr.rel(chemin)
        d = json.loads(chemin.read_text(encoding="utf-8"))
        paquet = d.get("package", "")
        inj = d.get("injectors") or {}
        defreq = inj.get("defaultRequire", 0)
        refmap = d.get("refmap")
        # 1. manifeste
        m = manifeste_attendu(mod, ens, chemin)
        if m is False:
            r.erreurs.append((lieu_cfg, "config absente du manifeste MixinConfigs écrit par %s/build.gradle : jamais chargée "
                                        "par Forge, aucun de ses mixins ne s'applique" % mod))
        # 2. table de correspondance attendue dans ce jar
        props = vr_props(mod)
        jar_name = props.get("jar_name") or props.get("mod_id")
        attendu = refmap_attendue(mod, ens, jar_name)
        if refmap and attendu and refmap not in attendu:
            r.erreurs.append((lieu_cfg, "refmap « %s » : la fabrication produit %s pour src/%s : noms non réobfusqués en jeu"
                              % (refmap, " ou ".join(sorted(attendu)), ens)))
        if not refmap:
            r.infos.append((lieu_cfg, "sans refmap : chaque nom doit être celui du jeu (SRG pour Minecraft) ou remap = false"))
        # 3. sections (+ mixins ajoutées par le plugin de la config : getMixins())
        sections = {k: d.get(k) or [] for k in ("mixins", "client", "server")}
        if d.get("plugin"):
            pl = ix.types.get(d["plugin"])
            if pl is None:
                r.erreurs.append((lieu_cfg, "plugin %s introuvable : la config ne se charge pas" % d["plugin"]))
            else:
                ajoutes = []
                for mb in pl.methodes("getMixins"):
                    if mb.debut is not None:
                        tt = pl.fichier.t
                        ajoutes += [sj.chaine(tt[q + 2].texte) for q in range(mb.debut, mb.fin - 2)
                                    if tt[q].texte == "add" and tt[q + 1].texte == "(" and tt[q + 2].genre == "str"]
                sections["plugin"] = ajoutes
        # refmap écrite à la main (pas d'annotation processor) : chaque nom SRG doit exister
        if refmap and attendu is None:
            rm = chemin.parent / refmap
            if not rm.exists():
                r.erreurs.append((lieu_cfg, "refmap %s absente des ressources" % refmap))
            else:
                for mx, table in (json.loads(rm.read_text(encoding="utf-8")).get("mappings") or {}).items():
                    for dev, obf in table.items():
                        po, no, do = selecteur(obf)
                        if po and SRG.match(no or ""):
                            if corr.srg_connu(po, no):
                                r.ok.append((vr.rel(rm), "%s -> %s (SRG vu dans un autre jar)" % (dev, obf)))
                            else:
                                r.infos.append((vr.rel(rm), "%s -> %s : SRG non confirmé (Minecraft absent)" % (dev, obf)))
                        else:
                            r.erreurs.append((vr.rel(rm), "%s -> %s : pas un nom SRG" % (dev, obf)))
        if ens == "serveur" and sections["client"]:
            r.erreurs.append((lieu_cfg, "section « client » dans une config du serveur : jamais appliquée (jar -serveur)"))
        if ens == "client" and sections["server"]:
            r.erreurs.append((lieu_cfg, "section « server » dans une config du client : jamais appliquée"))
        if defreq < 1:
            r.avert.append((lieu_cfg, "defaultRequire = %s : une injection qui ne trouve plus sa cible ne dit rien "
                                      "(échec silencieux)" % defreq))
        for sec, noms in sections.items():
            for nom in noms:
                fqn = paquet + "." + nom
                toutes_listees.add(fqn)
                ty = ix.type(fqn.replace(".", "$", 0))
                ty = ix.types.get(fqn) or ix.types.get(re.sub(r"\.(\w+)$", r"$\1", fqn))
                if ty is None:
                    r.erreurs.append((lieu_cfg, "mixin %s listée mais introuvable dans les sources" % fqn))
                    continue
                if ty.fichier.module != mod or (ty.fichier.ensemble != ens):
                    r.erreurs.append((lieu_cfg, "mixin %s dans %s/src/%s, config dans %s/src/%s : la classe n'est pas dans le "
                                                "même jar ou pas vue par la bonne table de correspondance" % (
                                                    fqn, ty.fichier.module, ty.fichier.ensemble, mod, ens)))
                n_mixins += 1
                lec = lecteurs.get(id(ty.fichier))
                if lec is None:
                    lec = lecteurs[id(ty.fichier)] = vr.Lecteur(ix, ty.fichier)
                n_inj += controler_mixin(r, ix, lec, ty, d, ens, defreq, corr, jars, refmap)
    # mixins oubliées
    for fqn, ty in ix.types.items():
        if ty.fichier.module in sj.JAMAIS_LIVRES or (choisis and ty.fichier.module not in choisis):
            continue
        if any(a[0] == "Mixin" for a in ty.annotations) and fqn not in toutes_listees and ty.parent is None:
            r.avert.append(("%s:%d" % (vr.rel(ty.fichier.chemin), ty.ligne), "classe @Mixin %s listée dans aucune config : "
                                                                           "jamais appliquée" % fqn))
    n_conf, n_tiers = conflits(r, corr)
    print("[verif_mixins] %d injections des autres mods du pack lues, %d méthodes visées aussi par l'un d'eux" % (n_tiers, n_conf))
    for (lieu, msg) in sorted(set(r.erreurs)):
        print("   ERREUR %s : %s" % (lieu, msg))
    for (lieu, msg) in sorted(set(r.avert)):
        print("   avert. %s : %s" % (lieu, msg))
    if tout:
        for (lieu, msg) in sorted(set(r.infos)):
            print("   info   %s : %s" % (lieu, msg))
        for (lieu, msg) in sorted(set(r.ok)):
            print("   ok     %s : %s" % (lieu, msg))
    print("[verif_mixins] %d mixins, %d injections ou membres lus ; %d vérifié(s), %d avertissement(s), %d erreur(s)" % (
        n_mixins, n_inj, len(set(r.ok)), len(set(r.avert)), len(set(r.erreurs))))
    sys.exit(1 if r.erreurs else 0)


def vr_props(mod):
    p = sj.MODULES / mod / "gradle.properties"
    return dict(re.findall(r"^([\w.]+)=(.*)$", p.read_text(encoding="utf-8"), re.M)) if p.exists() else {}


def refmap_attendue(mod, ens, jar_name):
    """Noms de refmap que la fabrication produit pour cet ensemble de sources (lu dans build.gradle)."""
    b = (sj.MODULES / mod / "build.gradle").read_text(encoding="utf-8")
    ss = {"commun": "main", "client": "client", "serveur": "serveur", "main": "main"}[ens]
    if COUPE.search(b):
        return {{"main": "%s-commun.refmap.json", "client": "%s.refmap.json", "serveur": "%s-serveur.refmap.json"}[ss] % jar_name}
    out = set()
    for m in re.finditer(r"add\s+sourceSets\.(\w+)\s*,\s*\"([^\"]+)\"", b):
        if m.group(1) == ss:
            out.add(m.group(2).replace("${jar_name}", jar_name or ""))
    if not out and "spongepowered.mixin" not in b:
        return None    # pas d'annotation processor : refmap écrite à la main (Spartan)
    return out


def controler_mixin(r, ix, lec, ty, cfg, ens, defreq, corr, jars, refmap):
    f = ty.fichier
    lieu = "%s:%d" % (vr.rel(f.chemin), ty.ligne)
    ann = [lire_annotation(lec, f, a, b, ty) for (n, a, b) in ty.annotations if n in ("Mixin", "org.spongepowered.asm.mixin.Mixin")]
    if not ann:
        r.erreurs.append((lieu, "%s listée dans une config mais sans @Mixin" % ty.fqn))
        return 0
    _, a = ann[0]
    cibles = [v[1] for v in liste(a.get("value")) if isinstance(v, tuple) and v[0] == "classe"]
    cibles += [s for s in liste(a.get("targets")) if isinstance(s, str)]
    remap_classe = a.get("remap", True)
    pseudo = any(n == "Pseudo" for (n, _, _) in ty.annotations)
    n = 0
    for c in cibles:
        cint = c.replace(".", "/")
        if c.startswith(("net.minecraft.", "com.mojang.")):
            genre = "mc"
        elif c.startswith("net.minecraftforge."):
            genre = "forge"
        elif ix.types.get(c) or ix.types.get(re.sub(r"\.(\w+)$", r"$\1", c)):
            genre = "nous"
        else:
            genre = "tiers"
        cc = jars.classe(cint) if genre in ("tiers", "forge") else None
        if genre == "tiers" and cc is None:
            (r.avert if pseudo or not cfg.get("required") else r.erreurs).append((lieu, "cible %s introuvable dans les jars "
                                                                                       "du pack%s" % (c, " (@Pseudo)" if pseudo else "")))
        if c.startswith("net.minecraft.client.") and ens in ("serveur",):
            r.erreurs.append((lieu, "cible de Minecraft client %s dans une config du serveur" % c))
        for m in ty.membres:
            for (na, aa, ab) in m.annotations:
                court = na.rsplit(".", 1)[-1]
                if court not in INJECTEURS and court not in ("Shadow", "Accessor", "Invoker"):
                    continue
                n += 1
                _, av = lire_annotation(lec, f, aa, ab, ty)
                lm = "%s:%d" % (vr.rel(f.chemin), m.ligne)
                controler_membre(r, lm, court, av, m, c, cint, genre, cc, remap_classe, defreq, corr, jars, ix, ty, lec)
    return n


def methodes_de(cc, jars, nom, desc, profondeur=6):
    """Méthodes (accès, nom, desc, appels, classe) de la classe du jar et de ses ascendants dans les jars."""
    out, k, vus = [], cc, set()
    while k is not None and profondeur > 0 and k.nom not in vus:
        vus.add(k.nom)
        for (ac, nm, ds, ap) in k.methodes:
            if (nom == "*" or nm == nom) and (desc is None or ds == desc or ds.startswith(desc)):
                out.append((ac, nm, ds, ap, k))
        k = jars.classe(k.mere) if k.mere else None
        profondeur -= 1
    return out


NOS_CIBLES = []   # (lieu, injecteur, classe interne, [méthodes visées], [cibles @At])


def controler_membre(r, lieu, genre_ann, av, m, cible, cint, genre_cible, cc, remap_classe, defreq, corr, jars, ix, ty, lec):
    remap = av.get("remap", remap_classe)
    if genre_ann in INJECTEURS:
        meth = [x for x in liste(av.get("method")) if isinstance(x, str)] or ([m.nom] if genre_ann == "Overwrite" else [])
        ats = [a[1].get("target") for a in liste(av.get("at")) if isinstance(a, tuple) and len(a) == 2 and isinstance(a[1], dict)
               and isinstance(a[1].get("target"), str)]
        NOS_CIBLES.append((lieu, genre_ann, cint, meth, ats))
    require = av.get("require", None)
    if genre_ann in INJECTEURS and genre_ann != "Overwrite":
        req = defreq if require is None else require
        if req == 0:
            r.infos.append((lieu, "@%s sur %s : require 0 (échec silencieux possible)" % (genre_ann, cible)))
    if genre_ann == "Overwrite":
        r.avert.append((lieu, "@Overwrite de %s.%s : remplace la méthode entière (tout autre mod qui la vise est perdu)"
                        % (cible, m.nom)))
    # méthodes visées
    if genre_ann in ("Shadow", "Accessor", "Invoker", "Overwrite"):
        if genre_ann == "Shadow":
            noms = [m.nom]
            alias = [x for x in liste(av.get("aliases")) if isinstance(x, str)]
            noms += alias
        elif genre_ann in ("Accessor", "Invoker"):
            v = av.get("value")
            if isinstance(v, str) and v:
                noms = [v]
            else:
                base = re.sub(r"^(get|set|is|call|invoke|create|new)", "", m.nom)
                noms = [base[:1].lower() + base[1:]] if base else [m.nom]
        else:
            noms = [m.nom]
        champ = m.genre == "champ" or (genre_ann == "Accessor")
        for nom in noms:
            controler_nom(r, lieu, genre_ann, cible, cint, genre_cible, cc, nom, None, champ, remap, corr, jars)
        return
    methodes = [x for x in liste(av.get("method")) if isinstance(x, str)]
    if methodes:
        # plusieurs noms (nom de Mojang ET nom SRG avec remap = false) : il suffit que l'un existe
        essais = []
        for sel in methodes:
            po, nom, desc = selecteur(sel)
            sous = Rapport()
            controler_nom(sous, lieu, genre_ann, cible, cint, genre_cible, cc, nom, desc, False, remap, corr, jars)
            essais.append(sous)
        if any(x.ok for x in essais):
            for x in essais:
                r.ok += x.ok
        else:
            for x in essais:
                r.erreurs += x.erreurs
                r.avert += x.avert
                r.infos += x.infos
    # points d'injection
    ats = liste(av.get("at"))
    for at in ats:
        if not (isinstance(at, tuple) and len(at) == 2 and isinstance(at[1], dict)):
            continue
        atv = at[1]
        quoi = atv.get("value")
        tgt = atv.get("target")
        at_remap = atv.get("remap", remap)
        if quoi in ("INVOKE", "INVOKE_ASSIGN", "FIELD", "NEW", "INVOKE_STRING") and isinstance(tgt, str) and tgt:
            controler_at(r, lieu, genre_ann, cible, genre_cible, cc, methodes, quoi, tgt, at_remap, corr, jars)
        elif quoi in ("INVOKE", "FIELD") and isinstance(tgt, tuple):
            r.avert.append((lieu, "@At(%s) : cible non lisible (%s)" % (quoi, tgt[1])))


def controler_nom(r, lieu, genre_ann, cible, cint, genre_cible, cc, nom, desc, champ, remap, corr, jars):
    """Une méthode / un champ visé dans la classe cible."""
    if nom in ("<init>", "<clinit>", "*") or not nom:
        return
    if genre_cible in ("tiers", "forge"):
        if cc is None:
            return
        if champ:
            noms = {nm for (ac, nm, ds, ap) in cc.champs}
            k = cc
            while k is not None and nom not in noms:
                k = jars.classe(k.mere) if k.mere else None
                if k is not None:
                    noms |= {nm for (ac, nm, ds, ap) in k.champs}
            if nom in noms:
                r.ok.append((lieu, "@%s %s.%s [%s]" % (genre_ann, cible, nom, jars.origine(cint))))
            elif SRG.match(nom) or not remap:
                r.erreurs.append((lieu, "@%s %s.%s : champ introuvable dans %s" % (genre_ann, cible, nom, jars.origine(cint))))
            else:
                r.avert.append((lieu, "@%s %s.%s : champ introuvable dans %s (nom de Mojang hérité de Minecraft ?)" % (
                    genre_ann, cible, nom, jars.origine(cint))))
            return
        trouves = methodes_de(cc, jars, nom, desc)
        if trouves:
            r.ok.append((lieu, "@%s %s.%s%s [%s]" % (genre_ann, cible, nom, desc or "", jars.origine(cint))))
            return
        # pas trouvée par ce nom : héritée de Minecraft (nom SRG dans le jar) ?
        if not SRG.match(nom) and remap is False:
            srg = corr.srg_de("net/minecraft/x", nom)
            r.erreurs.append((lieu, "@%s %s.%s%s : introuvable dans %s avec remap = false (si c'est une méthode de "
                                    "Minecraft redéfinie, le jar l'appelle par son nom SRG)" % (
                                        genre_ann, cible, nom, desc or "", jars.origine(cint))))
        elif SRG.match(nom):
            # SRG : redéfinition d'une méthode de Minecraft, présente si la classe la redéfinit
            r.erreurs.append((lieu, "@%s %s.%s%s : pas dans %s (la classe ne redéfinit pas cette méthode)" % (
                genre_ann, cible, nom, desc or "", jars.origine(cint))))
        else:
            r.avert.append((lieu, "@%s %s.%s%s : absent de %s sous ce nom ; remappé par la refmap s'il redéfinit une "
                                  "méthode de Minecraft (à vérifier sur le jar fabriqué)" % (
                                      genre_ann, cible, nom, desc or "", jars.origine(cint))))
        return
    if genre_cible == "mc":
        if SRG.match(nom):
            if corr.srg_connu(cint, nom):
                r.ok.append((lieu, "@%s %s.%s (SRG vu dans un autre jar)" % (genre_ann, cible, nom)))
            else:
                r.infos.append((lieu, "@%s %s.%s : SRG non confirmé (Minecraft absent)" % (genre_ann, cible, nom)))
            return
        if remap is False:
            ajout = corr.ajoute(cint, nom)
            srg = corr.srg_de(cint, nom)
            if ajout:
                r.ok.append((lieu, "@%s %s.%s : nom en clair ajouté par %s" % (genre_ann, cible, nom, "/".join(sorted(ajout)))))
            elif srg:
                r.erreurs.append((lieu, "@%s %s.%s%s avec remap = false : nom de Mojang, en jeu %s : introuvable" % (
                    genre_ann, cible, nom, desc or "", "/".join(sorted({x[1] for x in srg})))))
            else:
                r.infos.append((lieu, "@%s %s.%s avec remap = false : nom en clair, à confirmer (Forge / Bukkit ?)" % (
                    genre_ann, cible, nom)))
            return
        srg = corr.srg_de(cint, nom)
        if srg:
            r.ok.append((lieu, "@%s %s.%s%s (nom de Mojang connu : %s)" % (genre_ann, cible, nom, desc or "",
                                                                        "/".join(sorted({x[1] for x in srg})))))
        else:
            r.infos.append((lieu, "@%s %s.%s%s : non vérifiable ici (Minecraft absent)" % (genre_ann, cible, nom, desc or "")))


def controler_at(r, lieu, genre_ann, cible, genre_cible, cc, methodes, quoi, tgt, at_remap, corr, jars):
    po, nom, desc = selecteur(tgt)
    if po is None:
        r.avert.append((lieu, "@At(%s, target = \"%s\") sans classe propriétaire" % (quoi, tgt)))
        return
    proprio_mc = po.startswith(MC)
    if proprio_mc and not SRG.match(nom) and at_remap is False:
        ajout = corr.ajoute(po, nom)
        srg = corr.srg_de(po, nom)
        if srg and not ajout:
            r.erreurs.append((lieu, "@At(%s) %s avec remap = false : nom de Mojang, en jeu %s : l'injection ne trouve rien" % (
                quoi, tgt, "/".join(sorted({x[1] for x in srg})))))
        elif not ajout:
            r.avert.append((lieu, "@At(%s) %s avec remap = false : nom en clair d'un membre de Minecraft, à confirmer "
                                  "(seuls les membres ajoutés par Forge / Bukkit gardent leur nom)" % (quoi, tgt)))
    # l'appel est-il dans le code de la méthode visée (cible d'un autre auteur) ?
    if genre_cible in ("tiers", "forge") and cc is not None and methodes:
        nom_jeu = nom
        if proprio_mc and not SRG.match(nom):
            srg = {x[1] for x in corr.srg_de(po, nom)}
            if at_remap is not False and srg:
                nom_jeu = sorted(srg)[0]
        for sel in methodes:
            _, mn, md = selecteur(sel)
            cand = methodes_de(cc, jars, mn, md, 1)
            if not cand:
                continue
            vus = [ap for (_, _, _, aps, _) in cand for ap in aps]
            op_ok = ("GET", "PUT") if quoi == "FIELD" else ("INVOKE",)
            vus = [x for x in vus if x[0].startswith(op_ok)]
            bons = [x for x in vus if x[1] == po and x[2] in (nom, nom_jeu) and (desc is None or x[3] == desc)]
            if bons:
                r.ok.append((lieu, "@At(%s) %s dans %s.%s" % (quoi, tgt, cible, mn)))
            else:
                meme = [x for x in vus if x[1] == po and (desc is None or x[3] == desc)]
                if meme and proprio_mc and not SRG.match(nom):
                    r.avert.append((lieu, "@At(%s) %s dans %s.%s : appel trouvé sous un autre nom (%s), à confirmer" % (
                        quoi, tgt, cible, mn, ", ".join(sorted({x[2] for x in meme})))))
                else:
                    r.erreurs.append((lieu, "@At(%s) %s : aucun appel de ce membre dans %s.%s [%s]" % (
                        quoi, tgt, cible, mn, jars.origine(cible))))


# ====================================================================== conflits avec les mixins des autres mods
ANN_INJ = {"Lorg/spongepowered/asm/mixin/injection/%s;" % n: n for n in
           ("Inject", "Redirect", "ModifyArg", "ModifyArgs", "ModifyVariable", "ModifyConstant")}
ANN_INJ["Lorg/spongepowered/asm/mixin/Overwrite;"] = "Overwrite"
for _n, _p in (("WrapOperation", "wrapoperation"), ("WrapWithCondition", "wrapwithcondition"),
               ("ModifyExpressionValue", "modifyexpressionvalue"), ("ModifyReturnValue", "modifyreturnvalue"),
               ("ModifyReceiver", "modifyreceiver")):
    ANN_INJ["Lcom/llamalad7/mixinextras/injector/%s/%s;" % (_p, _n)] = _n
    ANN_INJ["Lcom/llamalad7/mixinextras/injector/%s;" % _n] = _n


def mixins_tiers():
    """[(jar, classe mixin, injecteur, cible interne, {noms de méthode}, {cibles @At})] des mixins des autres mods du
    pack (classes annotées @Mixin, noms passés par leur refmap)."""
    import verif_split as vs
    out = []

    def visiter(z, nom, prof):
        refmaps = {}
        for n in z.namelist():
            if n.endswith("refmap.json") or n.endswith("refmap.mixins.json") or re.search(r"refmap.*\.json$", n):
                try:
                    refmaps.update((json.loads(z.read(n)).get("mappings") or {}))
                except ValueError:
                    pass
        for n in z.namelist():
            if not n.endswith(".class") or n.startswith("META-INF/versions/"):
                continue
            data = z.read(n)
            if b"spongepowered/asm/mixin/Mixin" not in data:
                continue
            try:
                c = vs.Classe(data)
            except (ValueError, struct.error, IndexError):
                continue
            a = c.annotations.get("Lorg/spongepowered/asm/mixin/Mixin;")
            if a is None:
                continue
            cibles = [v[1][1:-1] for v in (a.get("value") or []) if isinstance(v, tuple) and v[0] == "classe"]
            cibles += [x.replace(".", "/") for x in (a.get("targets") or []) if isinstance(x, str)]
            rm = refmaps.get(c.nom, {})
            for (mn, md), ann in c.membres.items():
                for cle, genre in ANN_INJ.items():
                    if cle not in ann:
                        continue
                    v = ann[cle]
                    meth = [x for x in (v.get("method") or []) if isinstance(x, str)] if genre != "Overwrite" else [mn]
                    noms = set()
                    for x in meth:
                        noms.add(selecteur(x)[1])
                        if x in rm:
                            noms.add(selecteur(rm[x])[1])
                    ats = set()
                    at = v.get("at")
                    for e in (at if isinstance(at, list) else [at] if at else []):
                        if isinstance(e, tuple) and len(e) == 2 and isinstance(e[1], dict):
                            tg = e[1].get("target")
                            if isinstance(tg, str):
                                ats.add(tg)
                                if tg in rm:
                                    ats.add(rm[tg])
                    for cl in cibles:
                        out.append((nom, c.nom, genre, cl, noms, ats))
        if prof > 0:
            for n in z.namelist():
                if n.endswith(".jar") and n.startswith(("META-INF/jarjar/", "META-INF/jars/")):
                    try:
                        visiter(zipfile.ZipFile(io.BytesIO(z.read(n))), nom + "!" + n.rsplit("/", 1)[-1], prof - 1)
                    except zipfile.BadZipFile:
                        pass
    for f in sorted((RACINE / "serveur" / "mods-tiers").glob("*.jar")) + sorted((RACINE / "client" / "mods-tiers").glob("*.jar")):
        visiter(zipfile.ZipFile(f), f.name, 2)
    return out


def noms_jeu(cint, nom, corr):
    """Nom écrit dans une de nos annotations -> noms possibles en jeu (lui-même et son SRG s'il est connu)."""
    out = {nom}
    if not SRG.match(nom):
        out |= {x[1] for x in corr.srg_de(cint, nom)}
    return out


def at_jeu(tg, corr):
    po, nom, desc = selecteur(tg)
    if po is None:
        return {tg}
    out = {"%s.%s%s" % (po, nom, desc or "")}
    if not SRG.match(nom):
        out |= {"%s.%s%s" % (po, x[1], desc or "") for x in corr.srg_de(po, nom)}
    return out


def conflits_internes(r, corr):
    """Deux de NOS mixins qui modifient le même appel de la même méthode (deux @Redirect : le second est ignoré)."""
    vus = {}
    for (lieu, genre, cint, meth, ats) in NOS_CIBLES:
        noms = set()
        for x in meth:
            noms |= noms_jeu(cint, selecteur(x)[1], corr)
        cibles = set()
        for x in ats:
            cibles |= at_jeu(x, corr)
        for nm in noms:
            for c in cibles or {None}:
                vus.setdefault((cint, nm, c), []).append((lieu, genre))
    for (cint, nm, c), l in sorted(vus.items(), key=lambda x: str(x[0])):
        lieux = sorted({x for x in l})
        if len({x[0] for x in lieux}) < 2:
            continue
        genres = [g for (_, g) in lieux]
        if c is not None and genres.count("Redirect") >= 2:
            r.erreurs.append((lieux[0][0], "conflit entre nos mixins : %s.%s, appel %s redirigé par %s" % (
                cint.replace("/", "."), nm, c, ", ".join(x[0] for x in lieux))))
        elif "Overwrite" in genres:
            r.erreurs.append((lieux[0][0], "conflit entre nos mixins : %s.%s remplacée (@Overwrite) et visée par %s" % (
                cint.replace("/", "."), nm, ", ".join(x[0] for x in lieux))))


def options_modernfix():
    """Options de ModernFix (serveur/config/modernfix-mixins.properties : valeurs par défaut en commentaire, puis
    changements) : {"mixin.perf.remove_spawn_chunks": False, …}. Un mixin de ModernFix d'une option coupée ne compte pas."""
    f = RACINE / "serveur" / "config" / "modernfix-mixins.properties"
    out = {}
    if f.exists():
        for l in f.read_text(encoding="utf-8").splitlines():
            m = re.match(r"^#?\s*(mixin\.[\w.]+)\s*=\s*(true|false)", l)
            if m:
                out[m.group(1)] = m.group(2) == "true"
    return out


def actif_modernfix(classe, options):
    """org/embeddedt/modernfix/common/mixin/perf/remove_spawn_chunks/X -> option mixin.perf.remove_spawn_chunks."""
    m = re.match(r"org/embeddedt/modernfix/[\w]+/mixin/(.+)/[\w$]+$", classe)
    if not m:
        return True
    morceaux = m.group(1).split("/")
    for k in range(len(morceaux), 0, -1):
        cle = "mixin." + ".".join(morceaux[:k])
        if cle in options:
            return options[cle]
    return True


def conflits(r, corr):
    conflits_internes(r, corr)
    options = options_modernfix()
    tiers = [e for e in mixins_tiers() if actif_modernfix(e[1], options)]
    par_cible = {}
    for e in tiers:
        par_cible.setdefault(e[3], []).append(e)
    n = 0
    for (lieu, genre, cint, meth, ats) in NOS_CIBLES:
        autres = par_cible.get(cint)
        if not autres:
            continue
        nos_noms = set()
        for x in meth:
            nos_noms |= noms_jeu(cint, selecteur(x)[1], corr)
        nos_ats = set()
        for x in ats:
            nos_ats |= at_jeu(x, corr)
        for (jar, mx, g2, cl, noms2, ats2) in autres:
            if not (nos_noms & noms2):
                continue
            n += 1
            ats2n = set()
            for x in ats2:
                ats2n |= at_jeu(x, corr)
            meme_appel = bool(nos_ats & ats2n)
            texte = "%s de %s.%s aussi visé par %s (%s, @%s%s)" % (
                "@" + genre, cint.replace("/", "."), "/".join(sorted(nos_noms & noms2)), mx.replace("/", "."), jar, g2,
                ", même appel" if meme_appel else "")
            if "Overwrite" in (genre, g2) or (genre == "Redirect" and g2 == "Redirect" and meme_appel):
                r.erreurs.append((lieu, "conflit : " + texte))
            elif meme_appel and "Redirect" in (genre, g2):
                r.avert.append((lieu, "même appel modifié par deux mods : " + texte))
            else:
                r.infos.append((lieu, "méthode partagée : " + texte))
    return n, len(tiers)


if __name__ == "__main__":
    main()
