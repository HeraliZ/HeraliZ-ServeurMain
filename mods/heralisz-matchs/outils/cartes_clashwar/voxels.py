"""Briques de construction des cartes du ClashWar : grille de blocs, formes de base, raccords, écriture .nbt.

Tout est en coordonnées de la structure (x vers l'est, y vers le haut, z vers le sud), bornes incluses. Un bloc est une
chaîne « minecraft:stone » ou « minecraft:oak_stairs[facing=north,half=bottom] ». L'air n'est jamais écrit (la pose des
arènes omet l'air : les cases de la grille sont vides).
"""
import gzip
import io
import random
import struct

DATA_VERSION = 3465  # Minecraft 1.20.1

# blocs qui se raccordent à leurs voisins (vitres, barreaux, murets, barrières) : propriétés calculées à l'écriture
RACCORDS = ("_pane", "iron_bars", "_wall", "_fence")
# blocs « pleins » pour les raccords et les contrôles de place
NON_PLEINS = ("air", "_pane", "iron_bars", "_wall", "_fence", "_slab", "_stairs", "lantern", "torch", "carpet", "snow",
              "rail", "_trapdoor", "chain", "_button", "flower_pot", "ladder", "_sign", "light", "grass", "fern", "bush",
              "dead_bush", "_door", "cobweb", "scaffolding", "_head", "candle")


def nom(bloc):
    return bloc.split("[", 1)[0]


def props(bloc):
    if "[" not in bloc:
        return {}
    corps = bloc.split("[", 1)[1].rstrip("]")
    return dict(kv.split("=", 1) for kv in corps.split(",") if kv)


def plein(bloc):
    if bloc is None:
        return False
    n = nom(bloc)
    return not any(m in n for m in NON_PLEINS)


class Grille:
    def __init__(self, sx, sy, sz, graine=1):
        self.sx, self.sy, self.sz = sx, sy, sz
        self.b = {}
        self.rnd = random.Random(graine)

    # ------------------------------------------------------------------ accès
    def dedans(self, x, y, z):
        return 0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz

    def get(self, x, y, z):
        return self.b.get((x, y, z))

    def set(self, x, y, z, bloc):
        if not self.dedans(x, y, z):
            return
        if bloc is None or nom(bloc) == "minecraft:air":
            self.b.pop((x, y, z), None)
        else:
            self.b[(x, y, z)] = bloc

    def remplir(self, x0, y0, z0, x1, y1, z1, bloc):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, bloc)

    def vider(self, x0, y0, z0, x1, y1, z1):
        self.remplir(x0, y0, z0, x1, y1, z1, None)

    def creux(self, x0, y0, z0, x1, y1, z1, bloc):
        """Parois verticales d'une boîte (sans sol ni plafond)."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, z0, bloc)
                self.set(x, y, z1, bloc)
            for z in range(z0, z1 + 1):
                self.set(x0, y, z, bloc)
                self.set(x1, y, z, bloc)

    def haut(self, x, z):
        """Plus haut bloc plein de la colonne (ou -1)."""
        for y in range(self.sy - 1, -1, -1):
            if plein(self.get(x, y, z)):
                return y
        return -1

    # ------------------------------------------------------------------ écriture
    def raccorder(self):
        """Propriétés north/east/south/west des vitres, barreaux, murets et barrières d'après leurs voisins."""
        for (x, y, z), bloc in list(self.b.items()):
            n = nom(bloc)
            if not any(m in n for m in RACCORDS):
                continue
            mur = n.endswith("_wall")
            p = {}
            for cote, (dx, dz) in (("north", (0, -1)), ("east", (1, 0)), ("south", (0, 1)), ("west", (-1, 0))):
                v = self.get(x + dx, y, z + dz)
                lie = v is not None and (plein(v) or any(m in nom(v) for m in RACCORDS))
                p[cote] = ("low" if lie else "none") if mur else ("true" if lie else "false")
            if mur:
                p["up"] = "true" if not any(p[c] == "low" for c in ("north", "south")) or not any(p[c] == "low" for c in ("east", "west")) else "false"
            p["waterlogged"] = "false"
            self.b[(x, y, z)] = n + "[" + ",".join(k + "=" + v for k, v in sorted(p.items())) + "]"

    def ecrire(self, chemin):
        self.raccorder()
        palette, index, blocs = [], {}, []
        for (x, y, z), bloc in sorted(self.b.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            if bloc not in index:
                index[bloc] = len(palette)
                palette.append(bloc)
            blocs.append((index[bloc], x, y, z))
        racine = {
            "DataVersion": ("int", DATA_VERSION),
            "size": ("list_int", [self.sx, self.sy, self.sz]),
            "palette": ("list_comp", [entree_palette(p) for p in palette]),
            "blocks": ("list_comp", [{"state": ("int", s), "pos": ("list_int", [x, y, z])} for s, x, y, z in blocs]),
            "entities": ("list_comp", []),
        }
        tampon = io.BytesIO()
        tampon.write(b"\x0a")
        _chaine(tampon, "")
        _compose(tampon, racine)
        with open(chemin, "wb") as brut, gzip.GzipFile(fileobj=brut, mode="wb", mtime=0) as f:
            f.write(tampon.getvalue())
        return len(blocs)


def entree_palette(bloc):
    e = {"Name": ("str", nom(bloc))}
    p = props(bloc)
    if p:
        e["Properties"] = ("comp", {k: ("str", v) for k, v in p.items()})
    return e


# ---------------------------------------------------------------------- NBT (écriture minimale)
def _chaine(f, s):
    d = s.encode("utf-8")
    f.write(struct.pack(">H", len(d)))
    f.write(d)


def _compose(f, d):
    for k, (t, v) in d.items():
        if t == "int":
            f.write(b"\x03"); _chaine(f, k); f.write(struct.pack(">i", v))
        elif t == "str":
            f.write(b"\x08"); _chaine(f, k); _chaine(f, v)
        elif t == "comp":
            f.write(b"\x0a"); _chaine(f, k); _compose(f, v)
        elif t == "list_int":
            f.write(b"\x09"); _chaine(f, k); f.write(b"\x03"); f.write(struct.pack(">i", len(v)))
            for i in v:
                f.write(struct.pack(">i", i))
        elif t == "list_comp":
            f.write(b"\x09"); _chaine(f, k); f.write(b"\x0a" if v else b"\x00"); f.write(struct.pack(">i", len(v)))
            for c in v:
                _compose(f, c)
        else:
            raise ValueError(t)
    f.write(b"\x00")


# ---------------------------------------------------------------------- formes réutilisables
def escalier(g, x, y, z, sens, hauteur, bloc_marche, largeur=2):
    """Escalier droit qui monte de `hauteur` blocs vers `sens` (north/south/east/west), marches pleines dessous."""
    dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[sens]
    px, pz = (1, 0) if dx == 0 else (0, 1)
    for i in range(hauteur):
        for l in range(largeur):
            cx, cz = x + dx * i + px * l, z + dz * i + pz * l
            for yy in range(y, y + i):
                g.set(cx, yy, cz, "minecraft:stone_bricks")
            g.set(cx, y + i, cz, f"{bloc_marche}[facing={sens},half=bottom,shape=straight,waterlogged=false]")
            g.vider(cx, y + i + 1, cz, cx, y + i + 3, cz)


def batiment(g, x0, z0, x1, z1, y, etages, h, mur, sol, toit, ouvertures=(), fenetres=True, escaliers=True,
             lampe="minecraft:lantern[hanging=true,waterlogged=false]", rebord=None):
    """Bâtiment rectangulaire : murs, fenêtres ouvertes, planchers, escalier intérieur, toit plat à parapet.

    ouvertures : liste de (côté, décalage, largeur) pour les portes du rez-de-chaussée (côté = n, s, e, o).
    """
    haut_total = y + etages * h
    g.creux(x0, y, z0, x1, haut_total, z1, mur)
    for e in range(etages + 1):
        ye = y + e * h - 1 if e > 0 else y - 1
        g.remplir(x0, ye, z0, x1, ye, z1, sol if e < etages else toit)
    g.vider(x0 + 1, y, z0 + 1, x1 - 1, haut_total - 2, z1 - 1)
    for e in range(1, etages):
        g.remplir(x0 + 1, y + e * h - 1, z0 + 1, x1 - 1, y + e * h - 1, z1 - 1, sol)
    # parapet du toit
    g.creux(x0, haut_total, z0, x1, haut_total, z1, rebord or mur)
    g.vider(x0 + 1, haut_total, z0 + 1, x1 - 1, haut_total, z1 - 1)
    # fenêtres ouvertes de 1 x 2 tous les 3 blocs, à chaque étage
    if fenetres:
        for e in range(etages):
            yf = y + e * h + 1
            for x in range(x0 + 2, x1 - 1, 3):
                g.vider(x, yf, z0, x, yf + 1, z0)
                g.vider(x, yf, z1, x, yf + 1, z1)
            for z in range(z0 + 2, z1 - 1, 3):
                g.vider(x0, yf, z, x0, yf + 1, z)
                g.vider(x1, yf, z, x1, yf + 1, z)
    for cote, dec, larg in ouvertures:
        for i in range(larg):
            if cote == "n":
                g.vider(x0 + dec + i, y, z0, x0 + dec + i, y + 2, z0)
            elif cote == "s":
                g.vider(x0 + dec + i, y, z1, x0 + dec + i, y + 2, z1)
            elif cote == "o":
                g.vider(x0, y, z0 + dec + i, x0, y + 2, z0 + dec + i)
            else:
                g.vider(x1, y, z0 + dec + i, x1, y + 2, z0 + dec + i)
    # escalier intérieur le long du mur ouest, d'un étage à l'autre, et trémie
    if escaliers and etages > 1 and (z1 - z0) >= h + 3:
        for e in range(etages - 1):
            ye = y + e * h
            escalier(g, x0 + 1, ye, z0 + 2, "south", h, "minecraft:spruce_stairs", largeur=2)
            g.vider(x0 + 1, ye + h - 1, z0 + 2, x0 + 2, ye + h - 1, z0 + 2 + h - 1)
    # accès au toit : échelle contre le mur est
    if escaliers and (z1 - z0) >= 4:
        lx, lz = x1 - 1, z0 + 2
        for yy in range(y, haut_total):
            g.set(lx, yy, lz, "minecraft:ladder[facing=west,waterlogged=false]")
        g.vider(lx, haut_total - 1, lz, lx, haut_total - 1, lz)
        g.set(lx, haut_total - 1, lz, "minecraft:ladder[facing=west,waterlogged=false]")
    # éclairage
    if lampe:
        for e in range(etages):
            yl = y + (e + 1) * h - 2
            for x in range(x0 + 3, x1 - 2, 5):
                for z in range(z0 + 3, z1 - 2, 5):
                    if g.get(x, yl, z) is None:
                        g.set(x, yl, z, lampe)


def ruiner(g, x0, y0, z0, x1, y1, z1, part, debris, rnd):
    """Trous au hasard dans les murs et le haut d'un bâtiment, gravats au sol."""
    for (x, y, z) in list(g.b.keys()):
        if x0 <= x <= x1 and y0 + 2 <= y <= y1 and z0 <= z <= z1:
            haut = (y - y0) / max(1, (y1 - y0))
            if rnd.random() < part * (0.4 + haut):
                g.set(x, y, z, None)
    for _ in range(int((x1 - x0) * (z1 - z0) * part * 0.6)):
        x, z = rnd.randint(x0 - 2, x1 + 2), rnd.randint(z0 - 2, z1 + 2)
        if g.get(x, y0, z) is None and plein(g.get(x, y0 - 1, z)):
            g.set(x, y0, z, rnd.choice(debris))


def conteneur(g, x, y, z, long, axe, couleur, ouvert=True):
    """Conteneur maritime 3 de large x 3 de haut, ouvert aux deux bouts (on traverse)."""
    if axe == "x":
        g.remplir(x, y, z, x + long - 1, y + 2, z + 2, couleur)
        g.remplir(x, y + 2, z, x + long - 1, y + 2, z + 2, couleur)
        if ouvert:
            g.vider(x, y, z + 1, x + long - 1, y + 1, z + 1)
        for i in range(0, long, 2):
            g.set(x + i, y + 3, z + 1, None)
    else:
        g.remplir(x, y, z, x + 2, y + 2, z + long - 1, couleur)
        if ouvert:
            g.vider(x + 1, y, z, x + 1, y + 1, z + long - 1)


def citerne(g, cx, cz, y, r, h, bloc, cerclage=None):
    for yy in range(y, y + h):
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                d = ((x - cx) ** 2 + (z - cz) ** 2) ** 0.5
                if d <= r + 0.3:
                    g.set(x, yy, z, cerclage if cerclage and (yy - y) % 4 == 3 and d > r - 0.8 else bloc)


def sapin(g, x, y, z, h, tronc="minecraft:spruce_log[axis=y]", feuilles="minecraft:spruce_leaves[distance=1,persistent=true,waterlogged=false]"):
    for yy in range(y, y + h):
        g.set(x, yy, z, tronc)
    for i, yy in enumerate(range(y + 2, y + h + 1)):
        r = max(0, (h - i) // 2 - 1)
        if (yy - y) % 2 == 1:
            r = max(0, r - 1)
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) + abs(dz) <= r + 1 and not (dx == 0 and dz == 0 and yy < y + h):
                    if g.get(x + dx, yy, z + dz) is None:
                        g.set(x + dx, yy, z + dz, feuilles)
    g.set(x, y + h, z, feuilles)


def sacs(g, x0, z0, x1, z1, y, bloc="minecraft:mud_bricks", haut=2):
    """Muret de sacs de sable (hauteur 1 ou 2) : on s'abrite accroupi."""
    g.remplir(x0, y, z0, x1, y + haut - 1, z1, bloc)


def caisses(g, x, y, z, rnd, n=3, blocs=("minecraft:barrel[facing=up,open=false]", "minecraft:spruce_planks", "minecraft:oak_planks")):
    for i in range(n):
        dx, dz = rnd.randint(0, 1), rnd.randint(0, 1)
        hy = 0
        while g.get(x + dx, y + hy, z + dz) is not None and hy < 2:
            hy += 1
        g.set(x + dx, y + hy, z + dz, rnd.choice(blocs))


def site_bombe(g, cx, cz, y, lettre, couleur):
    """Dalle du site (9 x 9) à bandes jaunes et noires, grande lettre au sol ; le centre (5 x 5) reste libre."""
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            bord = abs(x - cx) == 4 or abs(z - cz) == 4
            if bord:
                g.set(x, y - 1, z, "minecraft:yellow_concrete" if (x + z) % 2 == 0 else "minecraft:black_concrete")
            else:
                g.set(x, y - 1, z, "minecraft:gray_concrete")
            g.vider(x, y, z, x, y + 3, z)
    motifs = {
        "A": [".###.", "#...#", "#####", "#...#", "#...#"],
        "B": ["####.", "#...#", "####.", "#...#", "####."],
    }
    for i, ligne in enumerate(motifs[lettre]):
        for j, c in enumerate(ligne):
            if c == "#":
                g.set(cx - 2 + j, y - 1, cz - 2 + i, couleur)
