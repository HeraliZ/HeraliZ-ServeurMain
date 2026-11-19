#!/usr/bin/env python3
"""
Grande texture des blocs (atlas « blocks » de Minecraft) : sa taille, calculée hors du jeu comme le jeu la fabrique.

Minecraft range dans une seule texture toutes les images de textures/block et textures/item de tous les mods et packs
(qu'un modèle s'en serve ou non), plus ce que déclarent les fichiers atlases/blocks.json (sacs : textures/backpack,
Curios : textures/slot, images seules de cfm) et moins ce que retirent leurs filtres (notre pack « textures »). Il les
range avec ModernFix (StbStitcher, actif par défaut : stb_rect_pack, « skyline » en bas à gauche, images triées par
hauteur) dans un carré ou un rectangle dont les côtés sont des puissances de 2. Au-delà de 8192 x 8192, il passe à
16384 x 8192 : deux fois plus de mémoire graphique (17/11 : ≈ 336 Mo de plus), et une vieille carte graphique limitée à
8192 ne démarre plus. Ce script refait ce calcul :

- sources, du bas vers le haut : Minecraft 1.20.1 (son jar s'il est là, sinon la liste outils/donnees/atlas_minecraft.txt),
  les jars des autres auteurs livrés aux joueurs (serveur/mods-tiers, client/mods-tiers, jars imbriqués compris), nos
  modules livrés aux joueurs (ressources client et communes, hors packs) et nos packs imposés (textures < herbe <
  heraliz_terrain) ; une image prise dans le pack le plus haut, son .mcmeta cherché de haut en bas jusqu'à lui ;
- une image animée ne compte qu'une fois (taille d'une image de l'animation) ; chaque côté arrondi au multiple de
  2^mipmaps au-dessus (Stitcher.Holder) ;
- rangement : copie fidèle de stb_rect_pack (ModernFix) ; le rangement de Minecraft sans ModernFix est donné en plus.

    python outils/atlas_blocs.py                rapport : taille, remplissage, plus grosses sources et images
    python outils/atlas_blocs.py --verifier     code de sortie 1 si la texture dépasse 8192 x 8192 (verifier.py)
    python outils/atlas_blocs.py --detail N     les N plus grandes images (défaut 40)
    python outils/atlas_blocs.py --mip 2        niveau de mipmaps (défaut 4, le plus haut que propose le jeu)

Sans dépendance (en-têtes PNG lus à la main) : tourne aussi sur les machines de GitHub.
"""
import argparse
import io
import json
import re
import struct
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import chemins  # noqa: E402

D = chemins.DEPOT
LIMITE = 8192
MODULES_JAMAIS = ("_gabarit", "banc", "capture", "events")       # jamais livrés aux joueurs (construire.py)
PACKS = [("textures", D / "modules/interface/src/main/resources/resourcepacks/textures"),
         ("herbe", D / "modules/interface/src/main/resources/resourcepacks/herbe"),
         ("heraliz_terrain", D / "modules/monde/src/client/resources/resourcepacks/heraliz_terrain")]
VANILLA_JAR = chemins.LANCEUR / "versions" / "1.20.1" / "1.20.1.jar"
VANILLA_LISTE = D / "outils" / "donnees" / "atlas_minecraft.txt"
# atlases/blocks.json de Minecraft 1.20.1 (dans son jar) : dossiers block/ et item/, conduit, quelques images seules ;
# les 56 contours de garnitures d'armure (paletted_permutations, 16 x 16) sont comptés à part
SOURCES_VANILLA = [{"type": "directory", "source": "block", "prefix": "block/"},
                   {"type": "directory", "source": "item", "prefix": "item/"},
                   {"type": "directory", "source": "entity/conduit", "prefix": "entity/conduit/"},
                   {"type": "single", "resource": "minecraft:entity/bell/bell_body"},
                   {"type": "single", "resource": "minecraft:entity/decorated_pot/decorated_pot_side"},
                   {"type": "single", "resource": "minecraft:entity/enchanting_table_book"}]
GARNITURES = 56
TEX = re.compile(r"^assets/([^/]+)/textures/(.+)\.png$")
MODELE = re.compile(r"^assets/[^/]+/models/.+\.json$")
ATLAS_JSON = "assets/minecraft/atlases/blocks.json"
# fichier des filtres de notre pack « textures » (le plus haut des fichiers d'atlas : il passe après ceux des mods)
ATLAS_PACK = PACKS[0][1] / ATLAS_JSON
CFM = "cfm-forge-1.20.1-7.0.0-pre36PATCH.jar"


# ------------------------------------------------------------------------------------------------ lecture des sources
def taille_png(debut):
    if debut[:8] != b"\x89PNG\r\n\x1a\n" or len(debut) < 24:
        return None
    return struct.unpack(">II", debut[16:24])


class Couche:
    """Un pack (ou un groupe de packs de même rang) : images {(espace, chemin sous textures/) : (l, h)}, .mcmeta, atlas,
    et les textures citées par ses modèles (assets/*/models/**.json)."""

    def __init__(self, nom):
        self.nom = nom
        self.images = {}
        self.meta = {}
        self.atlas = []          # listes de sources des atlases/blocks.json de la couche
        self.cites = {}          # identifiant de texture cité par un modèle -> premier modèle qui le cite

    def ajouter(self, nom_fichier, lire, origine):
        if MODELE.match(nom_fichier):
            try:
                d = json.loads(lire(nom_fichier, None).decode("utf-8-sig"))
            except ValueError:
                return
            t = d.get("textures") if isinstance(d, dict) else None
            if isinstance(t, dict):
                for v in t.values():
                    if isinstance(v, str) and v and not v.startswith("#"):
                        cle = v if ":" in v else "minecraft:" + v
                        self.cites.setdefault(cle, origine + " : " + nom_fichier)
            return
        m = TEX.match(nom_fichier)
        if m:
            t = taille_png(lire(nom_fichier, 24))
            if t:
                self.images[(m.group(1), m.group(2))] = (t[0], t[1], origine)
        elif nom_fichier.endswith(".png.mcmeta"):
            m = TEX.match(nom_fichier[:-7])
            if m:
                self.meta[(m.group(1), m.group(2))] = lire(nom_fichier, None)
        elif nom_fichier == ATLAS_JSON:
            try:
                self.atlas.append((origine, json.loads(lire(nom_fichier, None).decode("utf-8-sig")).get("sources", [])))
            except ValueError as e:
                print("[atlas] %s : %s illisible (%s)" % (origine, ATLAS_JSON, e))


def lire_jar(couche, z, origine):
    def lire(n, k):
        with z.open(n) as f:
            return f.read(k) if k else f.read()
    for n in z.namelist():
        if n.startswith("assets/"):
            couche.ajouter(n, lire, origine)
        elif n.startswith("META-INF/jarjar/") and n.endswith(".jar"):
            lire_jar(couche, zipfile.ZipFile(io.BytesIO(z.read(n))), origine + " > " + n.rsplit("/", 1)[-1])


def lire_dossier(couche, racine, origine, sauf_packs=False):
    racine = Path(racine)
    if not racine.is_dir():
        return

    def lire(n, k):
        with open(racine / n, "rb") as f:
            return f.read(k) if k else f.read()
    for f in racine.rglob("*"):
        if f.is_file():
            n = f.relative_to(racine).as_posix()
            if sauf_packs and n.startswith("resourcepacks/"):
                continue
            couche.ajouter(n, lire, origine)


def couche_vanilla():
    c = Couche("Minecraft 1.20.1")
    if VANILLA_JAR.exists():
        lire_jar(c, zipfile.ZipFile(VANILLA_JAR), "Minecraft 1.20.1")    # avec le vrai atlases/blocks.json du jar
        c.source = "jar"
    else:
        for ligne in VANILLA_LISTE.read_text(encoding="utf-8").splitlines():
            if ligne and not ligne.startswith("#"):
                chemin, l, h = ligne.split()
                c.images[("minecraft", chemin)] = (int(l), int(h), "Minecraft 1.20.1")
        c.source = "liste"
    if not c.atlas:
        c.atlas = [("Minecraft 1.20.1", SOURCES_VANILLA)]
    return c


def couches():
    """Couches du bas vers le haut, comme le jeu les empile (Minecraft, mods, nos packs imposés)."""
    out = [couche_vanilla()]
    mods = Couche("mods")
    for d in (chemins.MODS_TIERS, chemins.MODS_TIERS_CLIENT):
        for j in sorted(Path(d).glob("*.jar")):
            lire_jar(mods, zipfile.ZipFile(j), j.name)
    for m in sorted(chemins.MODS.iterdir()):
        if not m.is_dir() or m.name in MODULES_JAMAIS:
            continue
        for sous in ("src/client/resources", "src/commun/resources", "src/main/resources"):
            lire_dossier(mods, m / sous, "module " + m.name, sauf_packs=True)
    out.append(mods)
    for nom, chemin in PACKS:
        c = Couche("pack " + nom)
        lire_dossier(c, chemin, "pack " + nom)
        out.append(c)
    return out


# ------------------------------------------------------------------------------------------------ liste des images
def image_animee(l, h, meta):
    """Taille d'une image dans la texture (AnimationMetadataSection.calculateFrameSize) ; sans animation : l'image entière."""
    if meta is None:
        return l, h
    try:
        a = json.loads(meta.decode("utf-8-sig")).get("animation")
    except ValueError:
        return l, h
    if not isinstance(a, dict):
        return l, h
    fl, fh = a.get("width", -1), a.get("height", -1)
    if fl != -1:
        return (fl, fh) if fh != -1 else (fl, h)
    if fh != -1:
        return l, fh
    c = min(l, h)
    return c, c


def motif(p):
    """ResourceLocationPattern : expression cherchée DANS le nom (Pattern.asPredicate, find())."""
    return re.compile(p).search if p is not None else (lambda s: True)


def sprites(cs, retires=None, sans_nos_filtres=False):
    """{identifiant : (l, h, origine)} des images de la grande texture, sources et filtres appliqués dans l'ordre.
    retires (dict) : reçoit les images retirées par les filtres de nos packs ; sans_nos_filtres : ces filtres sautés."""
    gagnant = {}                                        # (espace, chemin) -> couche la plus haute qui a l'image
    for i, c in enumerate(cs):
        for k in c.images:
            gagnant[k] = i

    def taille(k):
        i = gagnant[k]
        meta = None
        for c in reversed(cs[i:]):                     # .mcmeta : du haut jusqu'au pack de l'image
            if k in c.meta:
                meta = c.meta[k]
                break
        l, h, origine = cs[i].images[k]
        fl, fh = image_animee(l, h, meta)
        return fl, fh, origine

    par_dossier = {}
    for (ns, chemin) in gagnant:
        par_dossier.setdefault(chemin.split("/", 1)[0], []).append((ns, chemin))
    res = {}
    for c in cs:
        for origine, sources in c.atlas:
            for s in sources:
                t = s.get("type", "").replace("minecraft:", "")
                if t == "directory":
                    src = s["source"].rstrip("/") + "/"
                    pref = s.get("prefix", "")
                    for ns, chemin in par_dossier.get(src.split("/", 1)[0], ()):
                        if chemin.startswith(src):
                            res[ns + ":" + pref + chemin[len(src):]] = taille((ns, chemin))
                elif t == "single":
                    r = s["resource"]
                    ns, chemin = r.split(":", 1) if ":" in r else ("minecraft", r)
                    if (ns, chemin) in gagnant:
                        sp = s.get("sprite", r)
                        res[sp if ":" in sp else "minecraft:" + sp] = taille((ns, chemin))
                elif t == "filter":
                    notre = c.nom.startswith("pack ")
                    if notre and sans_nos_filtres:
                        continue
                    p = s.get("pattern", {})
                    fn, fp = motif(p.get("namespace")), motif(p.get("path"))
                    for cle in [k for k in res if fn(k.split(":", 1)[0]) and fp(k.split(":", 1)[1])]:
                        if notre and retires is not None:
                            retires[cle] = res[cle]
                        del res[cle]
    for i in range(GARNITURES):
        res["minecraft:trims/items/garniture_%d" % i] = (16, 16, "Minecraft 1.20.1")
    res["minecraft:missingno"] = (16, 16, "Minecraft 1.20.1")
    return res


def arrondi(v, mip):
    """Stitcher.smallestFittingMinTexel : côté arrondi au multiple de 2^mip au-dessus."""
    p = 1 << mip
    return (v + p - 1) // p * p


def puissance2(v):
    p = 1
    while p < v:
        p <<= 1
    return p


# ------------------------------------------------------------------------------------------------ rangement ModernFix
def stb_ranger(rects, largeur, hauteur):
    """stb_rect_pack (Skyline_BL_sortHeight, align = 1) : vrai si tout tient dans largeur x hauteur."""
    rects = sorted(rects, key=lambda r: (-r[1], -r[0]))
    xs = [0, largeur]                    # ligne d'horizon : noeud i de xs[i] à xs[i+1], hauteur ys[i] (dernier : sentinelle)
    ys = [0, 1 << 30]
    for w, h in rects:
        if w > largeur or h > hauteur:
            return False
        meilleur_y, meilleur = 1 << 30, -1
        i = 0
        n = len(xs)
        while xs[i] + w <= largeur:
            x0 = xs[i]
            x1 = x0 + w
            y = 0
            j = i
            while xs[j] < x1:            # plus haut noeud sous [x0, x1)
                if ys[j] > y:
                    y = ys[j]
                j += 1
            if y < meilleur_y:
                meilleur_y, meilleur = y, i
            i += 1
            if i >= n:
                break
        if meilleur < 0 or meilleur_y + h > hauteur:
            return False
        x = xs[meilleur]
        fin = x + w
        j = meilleur
        while j + 1 < len(xs) and xs[j + 1] <= fin:
            j += 1
        # noeuds meilleur..j-1 couverts : remplacés par le nouveau ; le noeud j commence au plus tôt à fin
        if xs[j] < fin:
            xs[j] = fin
        xs[meilleur:j] = [x]
        ys[meilleur:j] = [meilleur_y + h]
    return True


def taille_modernfix(rects):
    """StbStitcher.packRects : départ aux puissances de 2 des plus grands côtés, doublé (le plus petit côté) jusqu'à la
    surface totale, puis jusqu'à 4 essais de rangement, chacun doublant encore en cas d'échec."""
    lw = puissance2(max(w for w, h in rects))
    lh = puissance2(max(h for w, h in rects))
    surface = sum(w * h for w, h in rects)
    while lw * lh < surface:
        if lw <= lh:
            lw *= 2
        else:
            lh *= 2
    for _ in range(4):
        if stb_ranger(rects, lw, lh):
            return lw, lh
        if lw <= lh:
            lw *= 2
        else:
            lh *= 2
    return None


# ------------------------------------------------------------------------------------------------ rangement Minecraft
class Region:
    __slots__ = ("x", "y", "w", "h", "sous", "plein")

    def __init__(self, x, y, w, h):
        self.x, self.y, self.w, self.h = x, y, w, h
        self.sous = None
        self.plein = False

    def ajouter(self, w, h):
        """Stitcher.Region.add."""
        if self.plein or w > self.w or h > self.h:
            return False
        if w == self.w and h == self.h and self.sous is None:
            self.plein = True
            return True
        if self.sous is None:
            self.sous = [Region(self.x, self.y, w, h)]
            k, l = self.w - w, self.h - h
            if l > 0 and k > 0:
                if max(self.h, k) >= max(self.w, l):
                    self.sous += [Region(self.x, self.y + h, w, l), Region(self.x + w, self.y, k, self.h)]
                else:
                    self.sous += [Region(self.x + w, self.y, k, h), Region(self.x, self.y + h, self.w, l)]
            elif k == 0:
                self.sous.append(Region(self.x, self.y + h, w, l))
            elif l == 0:
                self.sous.append(Region(self.x + w, self.y, k, h))
        return any(r.ajouter(w, h) for r in self.sous)


def taille_minecraft(rects, maxi=32768):
    """Stitcher de Minecraft 1.20.1 (sans ModernFix) : tri par hauteur puis largeur, régions agrandies par puissances de 2."""
    sys.setrecursionlimit(100000)
    regions = []
    sx = sy = 0
    for w, h in sorted(rects, key=lambda r: (-r[1], -r[0])):
        if any(r.ajouter(w, h) for r in regions):
            continue
        i, j = puissance2(sx), puissance2(sy)
        k, l = puissance2(sx + w), puissance2(sy + h)
        ok_l, ok_h = k <= maxi, l <= maxi
        if not ok_l and not ok_h:
            return None
        f2 = ok_l and i != k
        f3 = ok_h and j != l
        largeur = f2 if f2 ^ f3 else (ok_l and i <= j)
        if largeur:
            if sy == 0:
                sy = l
            r = Region(sx, 0, k - sx, sy)
            sx = k
        else:
            r = Region(0, sy, sx, l - sy)
            sy = l
        r.ajouter(w, h)
        regions.append(r)
    return sx, sy


# ------------------------------------------------------------------------------------------------ filtres de notre pack
def cites(cs):
    """{identifiant de texture : modèle qui le cite} de tous les modèles de toutes les couches."""
    out = {}
    for c in cs:
        for k, v in c.cites.items():
            out.setdefault(k, v)
    return out


def chaines_classe(data):
    """Chaînes CONSTANT_Utf8 d'un fichier .class."""
    out = []
    if data[:4] != b"\xca\xfe\xba\xbe":
        return out
    try:
        n, i, k = int.from_bytes(data[8:10], "big"), 10, 1
        while k < n:
            tag = data[i]
            if tag == 1:
                ln = int.from_bytes(data[i + 1:i + 3], "big")
                out.append(data[i + 3:i + 3 + ln].decode("utf-8", "replace"))
                i += 3 + ln
            elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
                i += 5
            elif tag in (5, 6):
                i += 9
                k += 1
            elif tag in (7, 8, 16, 19, 20):
                i += 3
            elif tag == 15:
                i += 4
            else:
                break
            k += 1
    except IndexError:
        pass
    return out


def cites_ailleurs():
    """Textures « espace:chemin » citées hors des modèles : chaînes des classes et fichiers texte de tous les jars, sources
    de nos modules ; plus tout chemin « block/… », « item/… » des classes de cfm (elles n'en ont aucun le 19/11 : seuls
    ses modèles citent ses textures)."""
    out = set()
    jeton = re.compile(r"[a-z0-9_.\-]+:[a-z0-9_./\-]+")

    def noter(texte, de_cfm):
        for j in jeton.findall(texte.lower()):
            out.add(j[:-4] if j.endswith(".png") else j)
        if de_cfm:
            for j in re.findall(r"(?:^|[^a-z0-9_])((?:block|item)/[a-z0-9_./\-]*)", texte.lower()):
                out.add("cfm:" + j)

    def jar(z, de_cfm):
        for n in z.namelist():
            if n.endswith(".class"):
                noter("\n".join(chaines_classe(z.read(n))), de_cfm)
            elif n.endswith((".txt", ".toml", ".properties", ".js", ".cfg")) or (n.endswith(".json") and not MODELE.match(n)):
                noter(z.read(n).decode("utf-8", "replace"), False)
            elif n.endswith(".jar"):
                jar(zipfile.ZipFile(io.BytesIO(z.read(n))), de_cfm)
    for d in (chemins.MODS_TIERS, chemins.MODS_TIERS_CLIENT):
        for j in sorted(Path(d).glob("*.jar")):
            jar(zipfile.ZipFile(j), j.name == CFM)
    for m in sorted(chemins.MODS.iterdir()):
        if m.is_dir() and m.name not in MODULES_JAMAIS:
            for f in (m / "src").rglob("*"):
                if f.suffix in (".java", ".json", ".txt", ".toml") and f.is_file() and not MODELE.search(f.as_posix().split("resources/", 1)[-1]):
                    noter(f.read_text(encoding="utf-8", errors="replace"), False)
    return out


def ecrire_filtre_cfm():
    """Réécrit les filtres de cfm de notre pack : toutes les images de cfm de la grande texture que ni un modèle (de
    n'importe quel jar, module ou pack) ni une chaîne de code ne cite. Le filtre de GeckoLib (armures) est gardé."""
    cs = couches()
    tout = sprites(cs, sans_nos_filtres=True)
    ref = cites(cs)
    ailleurs = cites_ailleurs()
    retirer = sorted(k for k in tout if k.startswith("cfm:") and k not in ref and k not in ailleurs)
    groupes = {}
    for k in retirer:
        chemin = k.split(":", 1)[1]
        dossier, nom = chemin.rsplit("/", 1)
        groupes.setdefault(dossier, []).append(nom)
    echap = lambda s: re.sub(r"([^a-z0-9_/\-])", r"\\\1", s)  # noqa: E731
    avant = json.loads(ATLAS_PACK.read_text(encoding="utf-8-sig")).get("sources", []) if ATLAS_PACK.exists() else []
    sources = [s for s in avant if not (s.get("type") == "filter" and s.get("pattern", {}).get("namespace") == "^cfm$")]
    for dossier, noms in sorted(groupes.items()):
        sources.append({"type": "filter", "pattern": {"namespace": "^cfm$",
                                                     "path": "^%s/(?:%s)$" % (echap(dossier), "|".join(echap(n) for n in noms))}})
    lignes = ",\n".join("    " + json.dumps(s, ensure_ascii=False) for s in sources)
    ATLAS_PACK.write_text('{\n  "sources": [\n%s\n  ]\n}\n' % lignes, encoding="utf-8", newline="\n")
    surface = sum(arrondi(tout[k][0], 4) * arrondi(tout[k][1], 4) for k in retirer)
    print("[atlas] %s : %d images de cfm retirées (%d dossiers, %.2f M points)" % (ATLAS_PACK.relative_to(D), len(retirer),
                                                                                 len(groupes), surface / 1e6))


# ------------------------------------------------------------------------------------------------ rapport
def calculer(mip=4, retires=None):
    cs = couches()
    sp = sprites(cs, retires)
    rects = [(arrondi(l, mip), arrondi(h, mip)) for l, h, _ in sp.values()]
    return cs, sp, rects


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--verifier", action="store_true")
    ap.add_argument("--detail", type=int, default=40)
    ap.add_argument("--mip", type=int, default=4)
    ap.add_argument("--minecraft", action="store_true", help="donner aussi le rangement de Minecraft sans ModernFix")
    ap.add_argument("--filtre-cfm", action="store_true", help="réécrire les filtres des images de cfm que rien ne cite")
    a = ap.parse_args()
    if a.filtre_cfm:
        ecrire_filtre_cfm()
    retires = {}
    cs, sp, rects = calculer(a.mip, retires)
    # une image retirée par nos filtres mais citée par un modèle serait la texture manquante (damier violet) en jeu
    ref = cites(cs)
    manquantes = sorted((k, ref[k]) for k in retires if k in ref)
    for k, m in manquantes[:20]:
        print("[atlas] RETIRÉE MAIS CITÉE : %s (%s) : relancer python outils/atlas_blocs.py --filtre-cfm" % (k, m))
    # une seule image dont un côté n'est pas un multiple de 16 coupe les mipmaps de toute la grande texture (journal du
    # jeu : « limits mip level ») : terrain qui scintille au loin (copie retaillée dans notre pack : outils/mipmaps.py)
    # (19/11 : trois images d'anciens menus de cfm le font depuis le début ; leur correction change l'aspect du terrain au
    # loin, décision du propriétaire : seulement signalé, pas encore bloquant)
    coupe = sorted((k, l, h, o) for k, (l, h, o) in sp.items() if min(l & -l, h & -h) < 16 and k != "minecraft:missingno"
                   and not k.startswith("minecraft:trims/"))
    for k, l, h, o in coupe[:20]:
        print("[atlas] attention, coupe les mipmaps : %s %dx%d (%s) : python outils/mipmaps.py --corriger" % (k, l, h, o))
    surface = sum(w * h for w, h in rects)
    t = taille_modernfix(rects)
    rempli = 100.0 * surface / (LIMITE * LIMITE)
    if cs[0].source == "liste":
        print("[atlas] Minecraft 1.20.1 : liste outils/donnees/atlas_minecraft.txt (jar du lanceur absent)")
    print("[atlas] %d images, %.2f M points (mipmaps %d) : %.1f %% de %d x %d" % (len(sp), surface / 1e6, a.mip, rempli,
                                                                                  LIMITE, LIMITE))
    if t:
        print("[atlas] rangement ModernFix : %d x %d" % t)
    else:
        print("[atlas] rangement ModernFix : impossible (plus de 4 essais)")
    if a.minecraft or not a.verifier:
        tm = taille_minecraft(rects)
        print("[atlas] rangement Minecraft sans ModernFix : %s" % ("%d x %d" % tm if tm else "impossible"))
    if not a.verifier:
        par = {}
        for l, h, o in sp.values():
            par[o.split(" > ")[0]] = par.get(o.split(" > ")[0], 0) + arrondi(l, a.mip) * arrondi(h, a.mip)
        print("\nPar source :")
        for o, v in sorted(par.items(), key=lambda x: -x[1])[:30]:
            print("   %-50s %6.2f M" % (o, v / 1e6))
        print("\nLes %d plus grandes images :" % a.detail)
        for k, (l, h, o) in sorted(sp.items(), key=lambda x: -x[1][0] * x[1][1])[:a.detail]:
            print("   %5d x %-5d %-75s %s" % (l, h, k, o))
    if a.verifier:
        ok = t is not None and t[0] <= LIMITE and t[1] <= LIMITE and not manquantes
        if coupe:
            print("[atlas] attention : %d image(s) coupent les mipmaps de toute la grande texture des blocs" % len(coupe))
        if manquantes:
            print("[atlas] %d image(s) retirée(s) par nos filtres mais citée(s) par un modèle" % len(manquantes))
        elif not ok:
            print("[atlas] TROP GRANDE : la grande texture des blocs dépasse %d x %d (mémoire graphique doublée) ; "
                  "voir python outils/atlas_blocs.py (plus grosses images) et docs/TESTS_EN_JEU_17NOV_carte-ile.md" % (LIMITE, LIMITE))
        elif rempli > 97:
            print("[atlas] attention : %.1f %% de la place, presque pleine" % rempli)
        sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
