"""Shapes of the ClashWar maps (first made for the housing estate of the 2v2): local frames, apartment blocks (stairwells, flats, balconies, arch), garages, kiosks,
market stalls, cars, trees, playground, hockey box, heating pipes, small buildings. All writes go through a Sym writer
(xl_lib.Sym), so whatever is built in one half is mirrored in the other.
"""
import math

from xl_lib import avec, dalle, etat, feuilles, marche, nom, oriente, trappe, tronc

G = 4   # ground surface (top block) y
S = 5   # standing level y (feet)
H_ETAGE = 4

# ------------------------------------------------------------------------------------------------ materials
M = "minecraft:"
D = "heraliz_decors:"
U = "heralisz_utils:"
C = "cfm:"

AIR = None
LUMIERE = etat("light", level="13", waterlogged="false")
LUMIERE_F = etat("light", level="10", waterlogged="false")


def garde_corps(*cotes):
    p = {c: "false" for c in ("north", "east", "south", "west")}
    for c in cotes:
        p[c] = "true"
    return etat(D + "garde_corps", **p)


class Repere:
    """Local frame of a building: u along its length, v across (v = 0 street side, v = prof - 1 courtyard side)."""
    AXES = {"+x": (1, 0), "-x": (-1, 0), "+z": (0, 1), "-z": (0, -1)}
    NOMS = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}

    def __init__(self, ox, oz, axe_u, axe_v):
        self.ox, self.oz = ox, oz
        self.du = self.AXES[axe_u]
        self.dv = self.AXES[axe_v]

    def xz(self, u, v):
        return self.ox + self.du[0] * u + self.dv[0] * v, self.oz + self.du[1] * u + self.dv[1] * v

    def dir(self, d):
        s = -1 if d[0] == "-" else 1
        a = self.du if d[1] == "u" else self.dv
        return self.NOMS[(a[0] * s, a[1] * s)]


class Local:
    """Writer in a local frame (u, y, v)."""

    def __init__(self, w, rep):
        self.w, self.r = w, rep

    def set(self, u, y, v, b, b_b="meme"):
        x, z = self.r.xz(u, v)
        self.w.set(x, y, z, b, b_b)

    def get(self, u, y, v):
        x, z = self.r.xz(u, v)
        return self.w.get(x, y, z)

    def fill(self, u0, y0, v0, u1, y1, v1, b, b_b="meme"):
        for u in range(min(u0, u1), max(u0, u1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for v in range(min(v0, v1), max(v0, v1) + 1):
                    self.set(u, y, v, b, b_b)

    def clear(self, u0, y0, v0, u1, y1, v1):
        self.fill(u0, y0, v0, u1, y1, v1, None, None)

    def d(self, x):
        return self.r.dir(x)


# ------------------------------------------------------------------------------------------------ apartment block
FURN_LIT = [C + "arkaniaz_bed"]
MEUBLES = [  # (block, needs facing) small furniture for rooms, all single-cell
    (C + "century_table", True), (C + "century_chair", True), (C + "arkaniaz_old_tv_1", True), (C + "arkaniaz_old_tv_2", True),
    (C + "century_cupboard", True), (C + "century_nightstand", True), (C + "arkaniaz_office_drawers", True),
    (D + "radiateur_mural", True), (C + "century_green_leather_sofa", True), (C + "arkaniaz_microwave_oven", True),
    (D + "chaise_metal", True), (D + "table_metal", True), (C + "century_book_shelf", True), (C + "arkaniaz_radio", True),
]


class Barre:
    """Soviet apartment block ('barre'): 4 storeys, flat roof, stairwells ('podyezds') with scissor stairs going up to the
    2nd floor (the 3rd floor and the roof are closed), flats with rooms and furniture, balconies on the courtyard side,
    entrance canopies, barred ground-floor windows, optional arch through the ground floor.

    style keys: mur, mur2 (panel joints / bands), socle, linteau (slab id), dalle_balcon, garde (balcony parapet),
    toit, acrotere, sol_hall, sol_appart, peinture_bas / peinture_haut (stairwell walls), marche (stairs material).
    """

    def __init__(self, w, rep, long, prof=11, etages=4, style=None, podyezds=(), arche=None, balcons=True,
                 fenetres_rue=True, pignon_fenetres=False, graine=0, acces_toit=False, sans_balcon=()):
        self.l = Local(w, rep)
        self.w = w
        self.L, self.P, self.E = long, prof, etages
        self.st = style
        self.pod = list(podyezds)
        self.arche = arche
        self.balcons = balcons
        self.fenetres_rue = fenetres_rue
        self.pignon_fenetres = pignon_fenetres
        self.rnd = w.rnd
        self.graine = graine
        self.sans_balcon = sans_balcon

    # y helpers
    @staticmethod
    def y_sol(k):
        return G + H_ETAGE * k   # floor slab of storey k (0 = ground)

    def construire(self):
        l, st, L, P, E = self.l, self.st, self.L, self.P, self.E
        y_toit = self.y_sol(E)
        # ground floor floor, slabs, walls
        l.fill(0, G, 0, L - 1, G, P - 1, st["sol_hall"])
        for k in range(1, E):
            l.fill(1, self.y_sol(k), 1, L - 2, self.y_sol(k), P - 2, st["sol_appart"])
        l.fill(0, y_toit, 0, L - 1, y_toit, P - 1, st["toit"])
        for y in range(S, y_toit):
            for u in range(L):
                for v in (0, P - 1):
                    l.set(u, y, v, self.mat_mur(u, y, v))
            for v in range(1, P - 1):
                for u in (0, L - 1):
                    l.set(u, y, v, self.mat_mur(u, y, v))
        # floor band marks on the facades (slab edges)
        for k in range(1, E + 1):
            y = self.y_sol(k)
            for u in range(L):
                for v in (0, P - 1):
                    l.set(u, y, v, st["bandeau"])
            for v in range(P):
                for u in (0, L - 1):
                    l.set(u, y, v, st["bandeau"])
        # plinth
        for u in range(L):
            for v in (0, P - 1):
                l.set(u, S, v, st["socle"])
        for v in range(P):
            for u in (0, L - 1):
                l.set(u, S, v, st["socle"])
        # parapet and roof details
        for u in range(L):
            for v in (0, P - 1):
                l.set(u, y_toit + 1, v, st["acrotere"])
        for v in range(P):
            for u in (0, L - 1):
                l.set(u, y_toit + 1, v, st["acrotere"])
        # interior: flats between stairwells
        self.cloisons()
        for p in self.pod:
            self.cage(p)
        if self.arche:
            self.passage(*self.arche)
        self.fenetres()
        if self.balcons:
            self.balcons_cour()
        for p in self.pod:
            self.auvent(p)
        self.toit()
        self.lumieres()

    def mat_mur(self, u, y, v):
        st = self.st
        # panel joints: every 3 blocks along u a darker column, every storey a band (set later)
        if st.get("joint") and u % 6 == 0 and 0 < u < self.L - 1:
            return st["joint"]
        if st.get("mur_var") and ((u * 7 + y * 3 + v * 5) % 11 == 0):
            return st["mur_var"]
        return st["mur"]

    # ---------------------------------------------------------------- interior walls
    def segments(self):
        """u-ranges of flats between stairwells / ends / arch (interior u ranges)."""
        bornes = [0]
        for p in sorted(self.pod):
            bornes += [p, p + 5]
        if self.arche:
            bornes += [self.arche[0] - 1, self.arche[1] + 1]
        bornes += [self.L - 1]
        bornes = sorted(bornes)
        segs = []
        for a, b in zip(bornes[0::2], bornes[1::2]):
            if b - a > 2:
                segs.append((a + 1, b - 1))
        return segs

    def cloisons(self):
        l, st, P, E = self.l, self.st, self.P, self.E
        mid = P // 2
        for k in range(0, E):
            y0 = self.y_sol(k) + 1
            y1 = self.y_sol(k + 1) - 1
            for (a, b) in self.segments():
                # central wall along u (street rooms / courtyard rooms) with doorways
                for u in range(a, b + 1):
                    for y in range(y0, y1 + 1):
                        l.set(u, y, mid, st["cloison"])
                n = b - a + 1
                # cross walls every ~5 blocks
                coupes = list(range(a + 5, b - 2, 5))
                for c in coupes:
                    for v in range(1, P - 1):
                        for y in range(y0, y1 + 1):
                            l.set(c, y, v, st["cloison"])
                    # doorways in the cross wall (one per side, staggered)
                    l.clear(c, y0, 2 + (c + k) % 2, c, y0 + 1, 2 + (c + k) % 2)
                    l.clear(c, y0, P - 3 - (c + k) % 2, c, y0 + 1, P - 3 - (c + k) % 2)
                # doorways in the central wall
                for u in range(a + 2, b - 1, 5):
                    du = u + (k % 2)
                    if du <= b:
                        l.clear(du, y0, mid, du, y0 + 1, mid)
                # room floors and wallpaper
                for u in range(a, b + 1):
                    for v in range(1, P - 1):
                        if k > 0 and l.get(u, self.y_sol(k), v) is not None:
                            l.set(u, self.y_sol(k), v, st["sol_appart"] if (u // 5 + v // 5 + k) % 3 else st["sol_appart2"])
                self.meubler(a, b, k)
            # walls between flats and the stairwells/arch already come from cage()/passage()

    def meubler(self, a, b, k):
        l, P = self.l, self.P
        y = self.y_sol(k) + 1
        if k >= 3:
            return
        mid = P // 2
        for u in range(a, b + 1):
            for v in (1, P - 2, mid - 1, mid + 1):
                if l.get(u, y, v) is not None:
                    continue
                h = (u * 31 + v * 17 + k * 7 + self.graine * 13) % 23
                if v in (1, P - 2) and h in (0, 1, 2):
                    blk, fac = MEUBLES[(u + v + k * 3 + self.graine) % len(MEUBLES)]
                    face = self.l.d("+v") if v == 1 else self.l.d("-v")
                    l.set(u, y, v, oriente(blk, face) if fac else blk)
                elif h == 5:
                    l.set(u, y, v, etat(D + "gravats", taille="petit"))
                elif h == 7 and v in (mid - 1, mid + 1):
                    l.set(u, y, v, M + ["red_carpet", "brown_carpet", "gray_carpet"][(u + k) % 3])

    # ---------------------------------------------------------------- stairwell
    def cage(self, p):
        """Stairwell with interior u p+1..p+4, v 1..P-2. Scissor flights: 0 -> 1 (u p+1..p+2, going to -v),
        1 -> 2 (u p+3..p+4, going to +v). Through-entrance at ground level on both facades."""
        l, st, P, E = self.l, self.st, self.P, self.E
        y_toit = self.y_sol(E)
        vb, vh = 1, P - 2  # interior v
        # side walls of the well, from ground to roof
        for y in range(S, y_toit):
            for v in range(vb, vh + 1):
                for u in (p, p + 5):
                    l.set(u, y, v, st["mur_cage"])
        # clear the well (remove floor slabs inside)
        for k in range(1, E):
            l.clear(p + 1, self.y_sol(k), vb, p + 4, self.y_sol(k), vh)
        l.clear(p + 1, S, vb, p + 4, y_toit - 1, vh)
        # painted walls inside the well (lower half green, upper whitewash)
        for y in range(S, y_toit):
            for v in range(vb, vh + 1):
                for u in (p, p + 5):
                    pass
        # flight 1: ground -> storey 1 (4 steps, going to -v) at u p+1, p+2
        f1 = l.d("-v")
        f2 = l.d("+v")
        for i in range(4):
            v = vh - 2 - i  # P-4 .. P-7
            for u in (p + 1, p + 2):
                for yy in range(S, S + i):
                    l.set(u, yy, v, st["mur_cage_bas"])
                l.set(u, S + i, v, marche(st["marche"], f1))
        # landing storey 1 (street side), v vb..vb+2
        l.fill(p + 1, self.y_sol(1), vb, p + 4, self.y_sol(1), vb + 2, st["palier"])
        # flight 2: storey 1 -> storey 2 at u p+3, p+4 going to +v
        y1 = self.y_sol(1) + 1
        for i in range(4):
            v = vb + 3 + i
            for u in (p + 3, p + 4):
                l.set(u, y1 + i, v, marche(st["marche"], f2))
                l.set(u, y1 + i - 1, v, st["sous_marche"]) if i > 0 else None
        # landing storey 2 (courtyard side)
        l.fill(p + 1, self.y_sol(2), vh - 1, p + 4, self.y_sol(2), vh, st["palier"])
        # closed above storey 2: slab ceiling at storey 3 + grille on the would-be flight
        l.fill(p + 1, self.y_sol(3), vb, p + 4, self.y_sol(3), vh, st["palier"])
        # doors from landings into flats (both side walls)
        for u in (p, p + 5):
            l.clear(u, S, vh - 1, u, S + 1, vh - 1)            # ground floor, hall
            l.clear(u, S, vb + 1, u, S + 1, vb + 1)            # ground floor, under landing
            l.clear(u, self.y_sol(1) + 1, vb + 1, u, self.y_sol(1) + 2, vb + 1)   # storey 1
            l.clear(u, self.y_sol(2) + 1, vh - 1, u, self.y_sol(2) + 2, vh - 1)   # storey 2
        # entrances: courtyard side (u p+1..p+2, into the hall) and street side (u p+3..p+4, under flight 2):
        # offset, so that no straight line goes through the building
        l.clear(p + 1, S, P - 1, p + 2, S + 2, P - 1)
        l.clear(p + 3, S, 0, p + 4, S + 2, 0)
        # stairwell windows (vertical strips) on both facades
        for y in (self.y_sol(1) + 2, self.y_sol(1) + 3, self.y_sol(2) + 2, self.y_sol(2) + 3, self.y_sol(3) + 2):
            l.set(p + 2, y, 0, st["vitre_cage"])
            l.set(p + 2, y, P - 1, st["vitre_cage"])
        # wall paint inside
        for v in range(vb, vh + 1):
            for u in (p, p + 5):
                for y in range(S, y_toit):
                    b = l.get(u, y, v)
                    if b is None:
                        continue
                    ky = (y - G) % H_ETAGE
                    l.set(u, y, v, st["peinture_bas"] if ky in (1, 2) else st["peinture_haut"])
        # mailboxes / radiator in the hall
        l.set(p + 1, S, vh, oriente(D + "radiateur_mural", l.d("-v")))
        l.set(p + 1, self.y_sol(1) - 1, vh, etat(M + "lantern", hanging="true", waterlogged="false"))
        l.set(p + 1, self.y_sol(1), vh, st["palier"])

    def auvent(self, p):
        """Entrance canopy on the courtyard side, wide enough to reach the flat window next to the stairwell."""
        l, st, P = self.l, self.st, self.P
        y = self.y_sol(1)
        for u in range(p + 1, p + 8):
            for v in (P, P + 1):
                l.set(u, y, v, dalle(st["auvent"], "top"))
        # side posts
        for yy in range(S, y):
            l.set(p + 1, yy, P + 1, st["poteau_auvent"])
        # a pile of crates just past the canopy end: 1, 2, 3 high toward the wall, then a step onto the canopy
        u0 = p + 8
        for dv, n in ((P, 3), (P + 1, 2), (P + 2, 1)):
            for k in range(n):
                l.set(u0, S + k, dv, [M + "iron_block", M + "orange_wool", M + "terracotta"][(k + dv) % 3])
        # bench next to the door (babushka bench)
        l.set(p, S, P, oriente(D + "banc_parc", l.d("+v")))
        l.set(p - 1, S, P, oriente(D + "banc_parc", l.d("+v")))

    # ---------------------------------------------------------------- arch
    def passage(self, a, b):
        l, st, P = self.l, self.st, self.P
        l.clear(a, S, 0, b, self.y_sol(1) - 1, P - 1)
        l.fill(a, G, 0, b, G, P - 1, st["sol_arche"])
        for v in range(P):
            for y in range(S, self.y_sol(1)):
                l.set(a - 1, y, v, st["mur"])
                l.set(b + 1, y, v, st["mur"])
        l.fill(a - 1, self.y_sol(1), 0, b + 1, self.y_sol(1), P - 1, st["bandeau"])
        # ceiling pipes and a lamp
        for v in range(P):
            l.set(a, self.y_sol(1) - 1, v, D + "gaine_calorifugee")
        l.set((a + b) // 2, self.y_sol(1) - 1, P // 2, etat(M + "lantern", hanging="true", waterlogged="false"))
        # graffiti on the arch walls
        l.set(a, S + 1, 2, oriente(D + "tag_mur", l.d("+u")))

    # ---------------------------------------------------------------- windows
    def fenetres(self):
        l, st, L, P, E = self.l, self.st, self.L, self.P, self.E
        cages = set()
        for p in self.pod:
            cages.update(range(p, p + 6))
        arche = set(range(self.arche[0] - 1, self.arche[1] + 2)) if self.arche else set()
        for k in range(E):
            y0 = self.y_sol(k) + 2
            for v in (0, P - 1):
                if v == 0 and not self.fenetres_rue:
                    continue
                for u in range(2, L - 2):
                    if u in cages or (k == 0 and u in arche):
                        continue
                    m = (u - (2 if v == P - 1 else 0)) % 4
                    if m not in (1, 2):
                        continue
                    for y in (y0, y0 + 1):
                        l.set(u, y, v, self.vitre(u, y, v, k))
        # gable windows
        if self.pignon_fenetres:
            for k in range(E):
                y0 = self.y_sol(k) + 2
                for v in (3, P - 4):
                    for u in (0, L - 1):
                        for y in (y0, y0 + 1):
                            l.set(u, y, v, self.vitre(u, y, v, k))

    def vitre(self, u, y, v, k):
        st = self.st
        if k == 1 and v == self.P - 1 and any(u in (p + 6, p + 7) for p in self.pod):
            return None
        h = (u * 13 + v * 7 + k * 5 + self.graine) % 10
        if k == 0:
            return st["barreaux"] if h < 7 else None           # ground floor: bars, some broken
        if k >= 3:
            return st["vitre"] if h < 7 else st["vitre_cassee"]
        return None if h < 6 else (st["vitre"] if h < 8 else st["vitre_cassee"])

    # ---------------------------------------------------------------- balconies
    def balcons_cour(self):
        """Vertical stacks of balconies (every 8 blocks) on the courtyard facade, storeys 1 to 3. Types: concrete
        parapet, iron railing, glazed (parapet + glass + frame), wooden boards, corrugated sheet."""
        l, st, L, P, E = self.l, self.st, self.L, self.P, self.E
        cage = set()      # the stairwell and its window strip: no balcony at any storey
        auvent = set()    # the entrance canopy (storey 1 only)
        for p in self.pod:
            cage.update(range(p - 1, p + 7))
            auvent.update(range(p - 1, p + 10))
        # balcony stacks on courtyard window pairs (u, u+1) with u % 4 == 3, at least 8 apart
        choisis = []
        for u in range(2, L - 3):
            if u % 4 != 3 or u in cage or (u + 1) in cage or u in self.sans_balcon or (u + 1) in self.sans_balcon:
                continue
            if (u - 1) < 1 or (u + 2) > L - 2:
                continue
            if choisis and u - choisis[-1] < 8:
                continue
            choisis.append(u)
        for u in choisis:
            for k in range(1, E):
                if k == 1 and (u in auvent or (u + 1) in auvent or (u - 1) in auvent or (u + 2) in auvent):
                    continue
                y = self.y_sol(k)
                typ = (u // 8 * 3 + k * 2 + self.graine) % 5
                for du in range(-1, 3):
                    for dv in (P, P + 1):
                        l.set(u + du, y, dv, st["dalle_balcon"])
                if k <= 2:
                    l.clear(u, y + 1, P - 1, u + 1, y + 3, P - 1)
                parapet = {0: st["garde_plein"], 1: None, 2: st["garde_plein"], 3: st["garde_bois"], 4: st["garde_tole"]}[typ]
                for du in range(-1, 3):
                    uu = u + du
                    if typ == 1:
                        cotes = [self.l.d("+v")]
                        if du == -1:
                            cotes.append(self.l.d("-u"))
                        if du == 2:
                            cotes.append(self.l.d("+u"))
                        l.set(uu, y + 1, P + 1, garde_corps(*cotes))
                    else:
                        l.set(uu, y + 1, P + 1, parapet)
                for dv in (P,):
                    for uu in (u - 1, u + 2):
                        if typ == 1:
                            l.set(uu, y + 1, dv, garde_corps(self.l.d("-u") if uu == u - 1 else self.l.d("+u")))
                        else:
                            l.set(uu, y + 1, dv, parapet)
                if typ == 2:
                    # glazed loggia: glass above the parapet, frame under the slab above
                    for du in range(-1, 3):
                        l.set(u + du, y + 2, P + 1, st["vitre_balcon"])
                        l.set(u + du, y + 3, P + 1, st["cadre_balcon"])
                    for uu in (u - 1, u + 2):
                        l.set(uu, y + 2, P, st["vitre_balcon"])
                        l.set(uu, y + 3, P, st["cadre_balcon"])
                if k == E - 1:
                    for du in range(-1, 3):
                        for dv in (P, P + 1):
                            l.set(u + du, self.y_sol(E), dv, dalle(st["auvent"], "bottom"))
                # junk
                h = (u * 3 + k * 5 + self.graine) % 6
                if h == 0:
                    l.set(u + 1, y + 1, P, M + "barrel" if (u + k) % 2 else M + "orange_wool")
                elif h == 2:
                    l.set(u, y + 1, P, M + "terracotta")

    # ---------------------------------------------------------------- roof
    def toit(self):
        l, st, L, P, E = self.l, self.st, self.L, self.P, self.E
        y = self.y_sol(E) + 1
        for p in self.pod:
            # roof hut over the stairwell
            l.fill(p, y, 2, p + 5, y + 2, 6, st["edicule"])
            l.fill(p, y + 3, 2, p + 5, y + 3, 6, st["acrotere"])
            l.clear(p + 2, y, 7, p + 3, y, 7)
            l.set(p + 2, y, 6, st["porte_toit"])
            l.set(p + 3, y, 6, st["porte_toit"])
            l.set(p + 2, y + 1, 6, st["porte_toit"])
            l.set(p + 3, y + 1, 6, st["porte_toit"])
            # TV antennas
            l.set(p + 1, y + 4, 4, etat(M + "lightning_rod", facing="up", powered="false", waterlogged="false"))
            l.set(p + 4, y + 4, 3, M + "iron_bars")
            l.set(p + 4, y + 5, 3, etat(M + "lightning_rod", facing="up", powered="false", waterlogged="false"))
        # vent stacks along the ridge line
        for u in range(3, L - 3, 7):
            if any(p - 1 <= u <= p + 6 for p in self.pod):
                continue
            l.set(u, y, P // 2, st["ventil"])
            l.set(u, y + 1, P // 2, dalle(M + "smooth_stone", "bottom"))
        # roof surface variety
        for u in range(1, L - 1):
            for v in range(1, P - 1):
                h = (u * 7 + v * 3) % 9
                if h == 0:
                    l.set(u, y - 1, v, st["toit2"])

    def lumieres(self):
        l, L, P, E = self.l, self.L, self.P, self.E
        for k in range(0, 3):
            yl = self.y_sol(k + 1) - 1
            for u in range(2, L - 2, 4):
                for v in (2, P - 3):
                    if l.get(u, yl, v) is None:
                        l.set(u, yl, v, LUMIERE_F)


# ------------------------------------------------------------------------------------------------ styles
STYLE_BRIQUE = {
    "mur": M + "bricks", "mur_var": M + "bricks", "joint": None, "bandeau": M + "light_gray_concrete", "socle": M + "stone",
    "sol_hall": M + "smooth_stone", "sol_appart": M + "spruce_planks", "sol_appart2": M + "brown_terracotta",
    "toit": M + "black_concrete", "toit2": M + "gray_concrete", "acrotere": M + "stone_bricks",
    "cloison": M + "white_wool", "mur_cage": M + "white_wool", "mur_cage_bas": M + "stone",
    "peinture_bas": M + "green_concrete", "peinture_haut": M + "white_wool",
    "marche": M + "stone_brick", "sous_marche": M + "stone", "palier": M + "polished_andesite", "rampe": D + "balustrade_fer",
    "vitre_cage": M + "gray_stained_glass_pane", "vitre": M + "gray_stained_glass_pane", "vitre_cassee": M + "iron_bars",
    "barreaux": M + "iron_bars", "dalle_balcon": M + "smooth_stone", "garde_plein": M + "light_gray_concrete",
    "garde_bois": M + "gold_block", "garde_tole": M + "pink_wool", "vitre_balcon": M + "glass_pane", "cadre_balcon": M + "coal_block",
    "auvent": M + "smooth_stone", "poteau_auvent": M + "stone_brick_wall", "sol_arche": M + "black_concrete",
    "edicule": M + "bricks", "porte_toit": M + "lapis_block", "ventil": M + "stone_bricks",
}
STYLE_PANNEAU = dict(STYLE_BRIQUE)
STYLE_PANNEAU.update({
    "mur": M + "blue_concrete", "mur_var": M + "smooth_stone", "joint": M + "light_gray_concrete", "bandeau": M + "orange_terracotta",
    "socle": M + "andesite", "cloison": M + "yellow_wool", "peinture_bas": M + "cyan_terracotta", "edicule": M + "light_gray_concrete",
    "sol_appart": M + "oak_planks",
})
