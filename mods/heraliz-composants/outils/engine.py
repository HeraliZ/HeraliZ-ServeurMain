"""Mini moteur : modeles a base de cubes (format Minecraft), textures de materiaux,
et rendu pixel-art net (lancer de rayons orthographique, sans anti-aliasing)."""
import math
import numpy as np
from PIL import Image

# ----------------------------------------------------------------- materiaux

def hexc(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)


def _lowfreq(r, cells=4):
    small = r.uniform(-1, 1, (cells, cells)).astype(np.float32)
    img = Image.fromarray(((small + 1) * 127.5).astype(np.uint8), 'L').resize((16, 16), Image.BICUBIC)
    return (np.asarray(img, float) / 127.5 - 1)


def make_texture(base, amp, pattern, alt, seed):
    r = np.random.default_rng(seed)
    b = hexc(base)
    a = hexc(alt) if alt else b * 0.7
    t = np.tile(b, (16, 16, 1)).astype(float)
    n = r.normal(0, 1, (16, 16, 1))
    y = np.arange(16)[:, None]
    x = np.arange(16)[None, :]
    if pattern == 'flat':
        t += n * amp * 0.5
    elif pattern == 'brushed':
        t += r.normal(0, 1, (16, 1, 1)) * amp * 0.8 + n * amp * 0.3
    elif pattern == 'grain':
        w = np.sin((y + 1.6 * np.sin(x / 2.7 + r.uniform(0, 6))) * 1.25)
        t += w[..., None] * amp + n * amp * 0.25
        knot = (np.abs(w) > 0.92) & (r.uniform(0, 1, (16, 16)) > 0.6)
        t[knot] = t[knot] * 0.85
    elif pattern == 'mottle':
        m = np.clip(_lowfreq(r, 5) * 1.4 + 0.1, 0, 1)[..., None]
        t = t * (1 - m) + a * m + n * amp * 0.4
    elif pattern == 'knurl':
        t += (((x + y) % 2) * 2 - 1)[..., None] * amp * 0.6 + n * amp * 0.2
    elif pattern == 'stripes':  # filetage / fil bobine : rayures selon u
        t += ((x % 2) * 2 - 1)[..., None] * amp + n * amp * 0.2
    elif pattern == 'weave':
        t += ((((x // 1) % 2) ^ ((y // 1) % 2)) * 2 - 1)[..., None] * amp * 0.35 + n * amp * 0.35
        t += r.normal(0, 1, (16, 1, 1)) * amp * 0.3
    elif pattern == 'fibers':
        t += n * amp * 0.4 + r.normal(0, 1, (1, 16, 1)) * amp * 0.3
    elif pattern == 'glass':
        t += (((15 - x) + (15 - y)) / 30.0 - 0.5)[..., None] * amp
        hl = (np.abs((x - y) - 3) <= 1) & (x > 2) & (x < 13)
        t[hl] = t[hl] * 0.5 + 255 * 0.5
    elif pattern == 'label':  # bandeau colore + "texte"
        t += n * amp * 0.3
        band = np.broadcast_to((y >= 6) & (y <= 10), (16, 16))
        t[band] = a
        txt = band & (y >= 7) & (y <= 9) & (r.uniform(0, 1, (16, 16)) > 0.55) & (x % 4 != 0)
        t[txt] = a * 0.35
    elif pattern == 'pcb':
        t += n * amp * 0.3
        tr = np.zeros((16, 16), bool)
        for _ in range(5):
            rr = r.integers(0, 16)
            c0, c1 = sorted(r.integers(0, 16, 2))
            tr[rr, c0:c1 + 1] = True
            cc = r.integers(0, 16)
            r0, r1 = sorted(r.integers(0, 16, 2))
            tr[r0:r1 + 1, cc] = True
        t[tr] = t[tr] * 1.35
        pads = r.uniform(0, 1, (16, 16)) > 0.94
        t[pads] = hexc('#d4b04a')
    elif pattern == 'grains':  # poudre : grains sombres avec reflets
        t += n * amp
        sp = r.uniform(0, 1, (16, 16)) > 0.86
        t[sp] = t[sp] + 45
    elif pattern == 'crack':  # ecran fissure
        t += (((15 - x) + (15 - y)) / 30.0 - 0.5)[..., None] * amp
        for _ in range(4):
            px, py = r.uniform(4, 12), r.uniform(4, 12)
            a0 = r.uniform(0, 2 * np.pi)
            for _s in range(9):
                a0 += r.uniform(-0.6, 0.6)
                px, py = px + np.cos(a0), py + np.sin(a0)
                if 0 <= px < 16 and 0 <= py < 16:
                    t[int(py), int(px)] = (190, 200, 210)
    elif pattern == 'text':  # papier journal : lignes de texte
        t += n * amp * 0.4
        lines = np.broadcast_to((y % 3 == 1) & (y > 3), (16, 16)) & (r.uniform(0, 1, (16, 16)) > 0.3) & (x != 7) & (x != 8)
        t[lines] = t[lines] * 0.45
        head = np.broadcast_to((y == 1) | (y == 2), (16, 16)) & (x > 1) & (x < 14)
        t[head] = t[head] * 0.25
        m = (_lowfreq(r, 4) > 0.5)
        t[m] = t[m] * 0.82 + a * 0.18
    elif pattern == 'scuffed':
        t += r.normal(0, 1, (16, 1, 1)) * amp * 0.5 + n * amp * 0.3
        for _ in range(4):
            yy = r.integers(0, 16)
            x0 = r.integers(0, 10)
            t[yy, x0:x0 + r.integers(3, 7)] += 28
        m = (_lowfreq(r, 4) > 0.55)
        t[m] = t[m] * 0.6 + a * 0.4
    return np.clip(t, 0, 255)


# nom: (couleur, amplitude, motif, metal (0-1), couleur secondaire)
MATERIALS = {
    'steel':       ('#a9aeb5', 9, 'brushed', 0.85, None),
    'steel_dark':  ('#6c7179', 8, 'brushed', 0.7, None),
    'spring':      ('#5d636b', 7, 'brushed', 0.8, None),
    'gunmetal':    ('#4f555d', 6, 'brushed', 0.6, None),
    'black':       ('#35383c', 5, 'flat', 0.3, None),
    'polymer':     ('#3b3d41', 4, 'flat', 0.15, None),
    'polymer_grip': ('#37393c', 7, 'knurl', 0.1, None),
    'olive':       ('#5a6443', 6, 'flat', 0.1, None),
    'tan':         ('#a88d63', 6, 'flat', 0.1, None),
    'brass':       ('#d0a640', 10, 'brushed', 0.95, None),
    'copper':      ('#c46a3a', 9, 'brushed', 0.9, None),
    'copper_wire': ('#c8703c', 16, 'stripes', 0.9, None),
    'aluminum':    ('#c8ccd2', 7, 'brushed', 0.75, None),
    'zinc':        ('#b3b8ad', 10, 'mottle', 0.6, '#949a8e'),
    'rust':        ('#8b4b27', 12, 'mottle', 0.15, '#5c3017'),
    'rusty_steel': ('#7c736c', 10, 'mottle', 0.4, '#8b4b27'),
    'plate':       ('#8d939a', 8, 'scuffed', 0.6, '#7a4a2c'),
    'lead':        ('#6f7683', 6, 'flat', 0.45, None),
    'rubber':      ('#2a2a2d', 3, 'flat', 0.05, None),
    'chip':        ('#18191b', 2, 'flat', 0.2, None),
    'wood':        ('#6e4428', 13, 'grain', 0.05, None),
    'pcb':         ('#2c6a38', 8, 'pcb', 0.2, None),
    'glass':       ('#8fcadb', 40, 'glass', 1.0, None),
    'bottle':      ('#7fb39a', 30, 'glass', 0.9, None),
    'cardboard':   ('#b38a5a', 9, 'fibers', 0.0, None),
    'tape':        ('#8e9298', 10, 'weave', 0.35, None),
    'red':         ('#a3312b', 5, 'flat', 0.2, None),
    'yellow':      ('#d6ad2b', 5, 'flat', 0.2, None),
    'white':       ('#d8d8d2', 4, 'flat', 0.15, None),
    'blue':        ('#2f5d9e', 5, 'flat', 0.3, None),
    'thread':      ('#a3a8af', 22, 'stripes', 0.85, None),
    'oil':         ('#c48b1e', 30, 'glass', 0.6, None),
    'label_green': ('#3d5a36', 5, 'label', 0.1, '#d8c99a'),
    'label_red':   ('#9e2b25', 5, 'label', 0.15, '#e8dfcf'),
    'label_yellow': ('#d6ad2b', 5, 'label', 0.1, '#1f1f1f'),
    'label_black': ('#26272a', 4, 'label', 0.1, '#d6ad2b'),
    'wood_raw':    ('#9a7a4e', 12, 'grain', 0.0, None),
    'gunmetal_worn': ('#7a828b', 8, 'scuffed', 0.6, '#a3a9b0'),
    'tan_grip':    ('#a3895f', 8, 'knurl', 0.1, None),
    'gold':        ('#e0b64a', 12, 'brushed', 1.0, None),
    'cell_blue':   ('#2f5fa8', 5, 'label', 0.35, '#d9d9d2'),
    'burlap':      ('#8f7852', 12, 'weave', 0.0, None),
    'string':      ('#4a3a26', 6, 'stripes', 0.0, None),
    'powder':      ('#2c2c2f', 10, 'grains', 0.3, None),
    'can_red':     ('#b22a2c', 6, 'label', 0.7, '#e6e6e6'),
    'tin':         ('#b9bdb4', 10, 'mottle', 0.75, '#8a6a3c'),
    'screen':      ('#2c4460', 30, 'crack', 0.9, None),
    'newspaper':   ('#cdc7b2', 8, 'text', 0.0, '#9a8c5e'),
    'cloth':       ('#f6f4ee', 6, 'weave', 0.0, None),
    'alcohol':     ('#b9d8e4', 34, 'glass', 0.9, None),
    'label_med':   ('#efeee8', 4, 'label', 0.1, '#c42b2b'),
    'rope':        ('#a88c5a', 18, 'stripes', 0.0, None),
    'tire':        ('#28282b', 9, 'knurl', 0.05, None),
    'plastic':     ('#76abd0', 30, 'glass', 0.7, None),
    'bone':        ('#dccba2', 9, 'grain', 0.1, None),
    'hazard':      ('#e5822a', 4, 'flat', 0.1, None),
    'jar_glass':   ('#c9e2ea', 40, 'glass', 1.0, None),
    'kraft':       ('#e4d4ac', 8, 'fibers', 0.0, None),
    'weld':        ('#6b625a', 14, 'knurl', 0.5, None),
    'cordura':     ('#3a3d42', 8, 'weave', 0.15, None),
    'plate_clean': ('#b4bac1', 8, 'brushed', 0.85, None),
    'leather':     ('#6e4529', 9, 'flat', 0.1, None),
    'grille':      ('#2a2b2e', 10, 'knurl', 0.3, None),
    'smoke':       ('#55595e', 22, 'glass', 0.8, None),
    # objets de valeur
    'silver':      ('#c4c9d0', 9, 'brushed', 0.9, None),
    'silver_worn': ('#b9bdc3', 10, 'mottle', 0.8, '#8e9298'),
    'gold_dark':   ('#a8812c', 8, 'brushed', 0.9, None),
    'jade':        ('#4f9a6e', 12, 'mottle', 0.55, '#2f6e4a'),
    'pearl':       ('#eee8dc', 5, 'flat', 0.8, None),
    'diamond':     ('#c8f0ff', 40, 'glass', 1.0, None),
    'ruby':        ('#c21f35', 34, 'glass', 0.9, None),
    'sapphire':    ('#2f58c8', 34, 'glass', 0.9, None),
    'enamel':      ('#8e1d33', 6, 'flat', 0.7, None),
    'velvet':      ('#6e1424', 10, 'weave', 0.0, None),
    'filter':      ('#c98a4b', 8, 'fibers', 0.0, None),
    'diamond_shade': ('#6fb2de', 26, 'glass', 1.0, None),
    'brass_worn':  ('#a8843a', 14, 'mottle', 0.7, '#5f5a34'),
    'brass_patina': ('#5e6a4a', 10, 'mottle', 0.3, '#3f4a34'),
    'red_worn':    ('#8c3029', 12, 'mottle', 0.2, '#5c3a2a'),
    'crystal':     ('#c8e2ff', 34, 'glass', 1.0, None),
    'crystal_dark': ('#3f6694', 22, 'glass', 1.0, None),
    'emerald':     ('#1f9a5a', 34, 'glass', 0.9, None),
    'filament':    ('#2a2522', 4, 'flat', 0.4, None),
    # construction
    'stone_cut':  ('#8e8c86', 16, 'mottle', 0.05, '#6f6d68'),
    'stone_dark': ('#6f6d68', 14, 'mottle', 0.05, '#55534f'),
    'paper_bag':  ('#b9a27a', 9, 'fibers', 0.0, None),
    'cement':     ('#9b9d9f', 12, 'grains', 0.0, None),
    'flux':       ('#7f8a92', 10, 'mottle', 0.1, '#5f6b74'),
    'hq_metal':   ('#5b6670', 9, 'brushed', 0.8, None),
    'hq_band':    ('#3c434a', 7, 'brushed', 0.7, None),
    'mortar':     ('#a4a29c', 14, 'grains', 0.0, None),
    'bucket':     ('#d9792a', 6, 'scuffed', 0.15, '#9a5a28'),
    'gas_blue':   ('#2b5ea8', 6, 'scuffed', 0.6, '#c8ccd2'),
    'tool_yellow': ('#e3b021', 6, 'scuffed', 0.2, '#8a6a20'),
    'disc':       ('#3a3b3e', 12, 'grains', 0.1, None),
    'rebar':      ('#7a4a30', 12, 'knurl', 0.3, '#5c3017'),
    'wire_tie':   ('#5d636b', 8, 'stripes', 0.6, None),
    'kevlar':     ('#d8b23a', 14, 'weave', 0.1, None),
    'kevlar_edge': ('#a8862a', 10, 'weave', 0.1, None),
    'titanium':   ('#8f98a3', 8, 'brushed', 0.9, None),
    'sack':       ('#d9d6cc', 9, 'weave', 0.0, None),
    'sack_print': ('#b8322b', 4, 'flat', 0.1, None),
    'sack_seam':  ('#a9a59a', 10, 'stripes', 0.0, None),
    'ceramic':    ('#e4e2dc', 6, 'mottle', 0.2, '#c9c6bd'),
    'ceramic_back': ('#4a5a3c', 6, 'weave', 0.0, None),
    'cable':      ('#9aa1a8', 22, 'stripes', 0.85, None),
    'chain':      ('#5f646b', 8, 'brushed', 0.7, None),
    'cem_bag':    ('#8f9194', 9, 'fibers', 0.0, None),
    'cem_bag_dk': ('#77797c', 9, 'fibers', 0.0, None),
    'cem_powder': ('#b9bbbd', 16, 'grains', 0.0, None),
    'ink':        ('#1d1e20', 2, 'flat', 0.0, None),
    'ink_red':    ('#a32a24', 3, 'flat', 0.0, None),
    'barbed':     ('#7d8086', 10, 'stripes', 0.7, None),
    'ingot_steel': ('#6f7882', 9, 'brushed', 0.85, None),
    'armor_olive': ('#55603f', 10, 'scuffed', 0.3, '#8a8f86'),
    'armor_edge':  ('#5d6168', 9, 'brushed', 0.7, None),
    'stencil':     ('#d9d4b8', 3, 'flat', 0.0, None),
}


def material_textures():
    out = {}
    for i, (name, (base, amp, pat, metal, alt)) in enumerate(MATERIALS.items()):
        out[name] = make_texture(base, amp, pat, alt, 1000 + i * 17)
    return out


# ----------------------------------------------------------------- modele

ALLOWED = (-45.0, -22.5, 0.0, 22.5, 45.0)


def rot_matrix(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


AX = {'x': 0, 'y': 1, 'z': 2}


class Model:
    def __init__(self):
        self.boxes = []

    # cube aligne (avec rotation Minecraft optionnelle : (axe, angle, origine))
    def box(self, f, t, m, rot=None):
        f, t = np.minimum(f, t).astype(float), np.maximum(f, t).astype(float)
        if rot is not None:
            axis, ang, org = rot
            assert ang in ALLOWED, ang
            if ang == 0:
                rot = None
            else:
                rot = (axis, float(ang), np.array(org, float))
        self.boxes.append({'f': f, 't': t, 'm': m, 'rot': rot})
        return self

    # cube centre, tourne autour de son centre (angle quelconque multiple de 22.5)
    def obox(self, c, size, m, axis='z', ang=0.0, origin=None):
        c = np.array(c, float)
        size = list(size)
        a = ((ang + 90) % 180) - 90  # (-90, 90]
        if a == -90:
            a = 90
        if abs(a) > 45:
            i, j = [k for k in range(3) if k != AX[axis]]
            size[i], size[j] = size[j], size[i]
            a = a - 90 if a > 0 else a + 90
        h = np.array(size, float) / 2
        org = c if origin is None else np.array(origin, float)
        return self.box(c - h, c + h, m, (axis, a, org) if a else None)

    # cylindre octogonal le long d'un axe (4 barres : 0, 90, +45, -45)
    def cyl(self, axis, a1, a2, c1, c2, r, m, sides=8):
        i = AX[axis]
        others = [k for k in range(3) if k != i]
        if r < 1.0 or sides == 4:
            sides = 'plus'
        lo, hi = min(a1, a2), max(a1, a2)

        def mk(h1, h2, ang, inset):
            f = np.zeros(3)
            t = np.zeros(3)
            f[i], t[i] = lo + inset, hi - inset
            f[others[0]], t[others[0]] = c1 - h1, c1 + h1
            f[others[1]], t[others[1]] = c2 - h2, c2 + h2
            center = [0, 0, 0]
            center[i] = (lo + hi) / 2
            center[others[0]] = c1
            center[others[1]] = c2
            self.box(f, t, m, (axis, ang, center) if ang else None)

        # decalages en bout de cylindre : les 4 barres ne doivent pas avoir leurs faces
        # d'extremite au meme endroit, sinon elles scintillent en jeu (z-fighting)
        e = min(0.03, (hi - lo) / 10)
        if sides == 'plus':
            k = 0.58
            mk(r, r * k, 0, 0)
            mk(r * k, r, 0, e)
        else:
            w = r * math.tan(math.radians(22.5))
            mk(r, w, 0, 0)
            mk(w, r, 0, e)
            mk(r, w, 45, e * 2)
            mk(r, w, -45, e * 3)
        return self

    # anneau polygonal (16 ou 8 segments) autour d'un axe
    def ring(self, axis, a1, a2, c1, c2, R, th, m, n=16):
        i = AX[axis]
        others = [k for k in range(3) if k != i]
        step = 360.0 / n
        L = 2 * R * math.tan(math.radians(step / 2)) + th * 0.45
        e = min(0.04, abs(a2 - a1) / 8)
        for k in range(n):
            phi = k * step
            ap = R - th / 2
            c = [0, 0, 0]
            c[i] = (a1 + a2) / 2
            c[others[0]] = c1 + ap * math.cos(math.radians(phi))
            c[others[1]] = c2 + ap * math.sin(math.radians(phi))
            size = [0, 0, 0]
            # un segment sur deux legerement en retrait : les recouvrements entre
            # segments voisins ne sont plus coplanaires (sinon scintillement en jeu)
            size[i] = abs(a2 - a1) - (2 * e if k % 2 else 0)
            size[others[0]] = th - (e if k % 2 else 0)
            size[others[1]] = L
            self.obox(c, size, m, axis, self._sgn(axis) * phi)
        return self

    @staticmethod
    def _sgn(axis):
        # sens de rotation pour que "others[0] -> others[1]" corresponde a l'angle positif
        return -1 if axis == 'y' else 1

    # ressort helicoidal le long de X
    def helix(self, x1, x2, cy, cz, R, w, turns, m, n=8):
        step = 360.0 / n
        total = int(round(turns * n))
        L = 2 * R * math.tan(math.radians(step / 2)) + w * 0.55
        dx = (x2 - x1 - w) / total
        for k in range(total + 1):
            phi = k * step
            ap = R - w / 2
            x = x1 + w / 2 + k * dx
            c = (x, cy + ap * math.cos(math.radians(phi)), cz + ap * math.sin(math.radians(phi)))
            self.obox(c, (max(w, dx * 1.6), w, L), m, 'x', phi)
        return self

    def bounds(self):
        pts = []
        for b in self.boxes:
            pts.append(corners(b))
        pts = np.concatenate(pts)
        return pts.min(0), pts.max(0)

    def recenter(self, target=8.0):
        lo, hi = self.bounds()
        off = target - (lo + hi) / 2
        for b in self.boxes:
            b['f'] = b['f'] + off
            b['t'] = b['t'] + off
            if b['rot'] is not None:
                b['rot'] = (b['rot'][0], b['rot'][1], b['rot'][2] + off)
        return self


def corners(b):
    f, t = b['f'], b['t']
    c = np.array([[x, y, z] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])], float)
    if b['rot'] is not None:
        axis, ang, org = b['rot']
        R = rot_matrix(axis, ang)
        c = (R @ (c - org).T).T + org
    return c


# ----------------------------------------------------------------- rendu

def view_matrix(roll, yaw, pitch):
    return rot_matrix('x', pitch) @ rot_matrix('y', yaw) @ rot_matrix('z', roll)


def fp_matrix(rot):
    """Approximation de la vue premiere personne (main droite) : rotation 'display'
    de Minecraft (X*Y*Z) puis camera regardant l'objet depuis l'epaule."""
    D = rot_matrix('x', rot[0]) @ rot_matrix('y', rot[1]) @ rot_matrix('z', rot[2])
    f = np.array([0.56, -0.52, -0.72])
    f /= np.linalg.norm(f)
    right = np.cross(f, [0, 1.0, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, f)
    V = np.stack([right, up, -f])
    return V @ D


def render(model, mats, size=32, roll=0.0, yaw=-30.0, pitch=28.0, detail=0.45,
           outline=True, pad=1, bg=None, M=None):
    if M is None:
        M = view_matrix(roll, yaw, pitch)
    center = np.array([8.0, 8.0, 8.0])
    pts = np.concatenate([corners(b) for b in model.boxes])
    pv = (M @ (pts - center).T).T
    umin, vmin = pv[:, 0].min(), pv[:, 1].min()
    umax, vmax = pv[:, 0].max(), pv[:, 1].max()
    span = max(umax - umin, vmax - vmin)
    inner = size - 2 * pad - (2 if outline else 0)
    s = inner / span
    uc, vc = (umin + umax) / 2, (vmin + vmax) / 2
    px = np.arange(size) + 0.5 - size / 2
    U, V = np.meshgrid(uc + px / s, vc - px / s)
    N = size * size
    O = np.stack([U.ravel(), V.ravel(), np.full(N, 500.0)], 1)
    O = (M.T @ O.T).T + center
    D = M.T @ np.array([0, 0, -1.0])

    best = np.full(N, np.inf)
    mat_idx = np.full(N, -1)
    nrm = np.zeros((N, 3))
    uv = np.zeros((N, 2))
    names = list(mats.keys())
    for b in model.boxes:
        if b['rot'] is not None:
            axis, ang, org = b['rot']
            R = rot_matrix(axis, ang)
            Ol = (R.T @ (O - org).T).T + org
            Dl = R.T @ D
        else:
            R = np.eye(3)
            Ol, Dl = O, D
        Dl = np.where(np.abs(Dl) < 1e-9, 1e-9, Dl)
        inv = 1.0 / Dl
        t1 = (b['f'] - Ol) * inv
        t2 = (b['t'] - Ol) * inv
        tn = np.minimum(t1, t2)
        tf = np.maximum(t1, t2)
        tmin = tn.max(1)
        tmax = tf.min(1)
        hit = (tmax >= tmin) & (tmin > 0) & (tmin < best - 1e-6)
        if not hit.any():
            continue
        ax = tn.argmax(1)
        best[hit] = tmin[hit]
        mat_idx[hit] = names.index(b['m'])
        for a in range(3):
            sel = hit & (ax == a)
            if not sel.any():
                continue
            n_local = np.zeros(3)
            n_local[a] = -np.sign(Dl[a])
            nrm[sel] = R @ n_local
            P = Ol[sel] + tmin[sel, None] * Dl
            if a == 0:
                uv[sel] = P[:, [2, 1]]
            elif a == 1:
                uv[sel] = P[:, [0, 2]]
            else:
                uv[sel] = P[:, [0, 1]]

    # ombrage
    L = np.array([-0.45, 0.78, 0.55])
    L /= np.linalg.norm(L)
    H = L + np.array([0, 0, 1.0])
    H /= np.linalg.norm(H)
    nv = (M @ nrm.T).T
    diff = np.clip(nv @ L, 0, 1)
    spec = np.clip(nv @ H, 0, 1) ** 14
    img = np.zeros((N, 3))
    obj = mat_idx >= 0
    for mi, name in enumerate(names):
        sel = mat_idx == mi
        if not sel.any():
            continue
        tex = mats[name]
        mean = tex.reshape(-1, 3).mean(0)
        tu = np.floor(np.mod(uv[sel, 0], 16)).astype(int)
        tv = np.floor(np.mod(16 - uv[sel, 1], 16)).astype(int)
        texel = tex[np.clip(tv, 0, 15), np.clip(tu, 0, 15)]
        col = mean + (texel - mean) * detail
        metal = MATERIALS[name][3]
        shade = 0.40 + 0.62 * diff[sel]
        col = col * shade[:, None] + 255 * (spec[sel] * metal * 0.45)[:, None]
        img[sel] = col
    img = img.reshape(size, size, 3)
    obj = obj.reshape(size, size)
    depth = best.reshape(size, size)
    key = (mat_idx * 1000 + np.round((nrm @ np.array([1, 7, 49])) * 10)).reshape(size, size)

    # contours internes (lumiere haut-gauche / ombre bas-droite)
    out = img.copy()
    for (dy, dx, f) in ((-1, 0, 1.13), (0, -1, 1.08), (1, 0, 0.82), (0, 1, 0.88)):
        nb_key = np.roll(np.roll(key, dy, 0), dx, 1)
        nb_obj = np.roll(np.roll(obj, dy, 0), dx, 1)
        nb_d = np.roll(np.roll(depth, dy, 0), dx, 1)
        edge = obj & nb_obj & ((nb_key != key) | (np.abs(nb_d - depth) > 0.8))
        if f < 1:  # ombre seulement si le voisin est devant
            edge &= (nb_d < depth - 0.05) | (nb_key != key)
        out[edge] *= f
    img = np.clip(out, 0, 255)

    rgba = np.zeros((size, size, 4))
    rgba[..., :3] = img
    rgba[..., 3] = obj * 255
    if outline:
        o = obj
        nb = np.zeros_like(o)
        colsum = np.zeros((size, size, 3))
        cnt = np.zeros((size, size))
        for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            sh = np.zeros_like(o)
            shc = np.zeros((size, size, 3))
            ys = slice(max(dy, 0), size + min(dy, 0))
            yd = slice(max(-dy, 0), size + min(-dy, 0))
            xs = slice(max(dx, 0), size + min(dx, 0))
            xd = slice(max(-dx, 0), size + min(-dx, 0))
            sh[yd, xd] = o[ys, xs]
            shc[yd, xd] = img[ys, xs]
            nb |= sh
            colsum += shc * sh[..., None]
            cnt += sh
        ol = nb & ~o
        avg = colsum / np.maximum(cnt, 1)[..., None]
        rgba[ol, :3] = avg[ol] * 0.28 + 6
        rgba[ol, 3] = 255
    if bg is not None:
        a = rgba[..., 3:4] / 255
        rgba[..., :3] = rgba[..., :3] * a + np.array(bg, float) * (1 - a)
        rgba[..., 3] = 255
    return Image.fromarray(np.clip(rgba, 0, 255).astype(np.uint8), 'RGBA')
