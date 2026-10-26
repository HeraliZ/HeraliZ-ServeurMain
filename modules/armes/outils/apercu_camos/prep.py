#!/usr/bin/env python3
"""
Prépare une arme TaCZ (modèle Bedrock + texture) pour l'aperçu WebGL des camos : reproduit le chargeur de TaCZ
(BedrockModel / BedrockCubeBox / BedrockCubePerFace) et la carte de positions du mod (PosMap) : position 3D normalisée de
chaque sommet dans le repère de l'arme, partie (corps / chargeur / canon / jamais camouflé).

usage : python3 prep.py modele_geo.json texture.png dossier nom
  -> dossier/nom.json (géométrie) et dossier/nom.png (copie de la texture)
exemple (gunpack du serveur) :
  python3 prep.py serveur/tacz/heraliz_6_armes_maxstuff/assets/maxstuff/geo_models/gun/xm177_geo.json \
                  serveur/tacz/heraliz_6_armes_maxstuff/assets/maxstuff/textures/gun/uv/xm177.png /tmp/armes xm177
"""
import json, math, os, re, shutil, sys

EXCLUDE = re.compile(r'^(left|right|lefthand|righthand|lefthand_pos|righthand_pos)$|^bullet|762x|shell|muzzle_flash|glass|lens', re.I)
ILLUM = re.compile(r'illuminated', re.I)
MAG = re.compile(r'(^|_)mag|magazine|clip|drum', re.I)
BARREL = re.compile(r'barrel|muzzle|suppress|silenc|canon', re.I)
# pièces cachées par défaut dans TaCZ (variantes d'accessoires, positions, main) : comptées pour le repère, pas dessinées
HIDDEN = re.compile(r'mag_extended|extd_mag|additional_magazine|^bullet|_pos$|^constraint$|^camera$|^shell$|muzzle_flash|^(left|right)hand$|^scope_|_illuminated$|^lod', re.I)
BODY, MAGAZINE, BARREL_PART, NONE = 1.0, 0.75, 0.5, 0.0
# carcasse (Diamant : pavé de diamants), comme PosMap.RECEIVER : pièces du corps nommées comme la carcasse et leurs
# sous-pièces sans nom propre ; à défaut de noms, la zone autour du haut du chargeur, entre la crosse et le garde-main
RECEIVER = 0.94
RECEIVER_NAME = re.compile(r'^(upper|lower|receiver|upper_?receiver|lower_?receiver|body|gun_?body|upper_?body|lower_?body|frame|slide|slide_?base)\d*$', re.I)
# sous-pièces de la carcasse qui restent en or (petites pièces rapportées, poignée, organes de visée...)
NOT_RECEIVER = re.compile(r'grip|stock|butt|cheek|sight|scope|rail|mount|carry|handle|trigger|safety|selector|switch|hammer|bolt|charg|release|catch|latch|lever|pin|ping|screw|button|guard|gaurd|gas|barrel|muzzle', re.I)
# petites pièces rapportées et poignée (Diamant façon Call of Duty : elles restent en or, le reste du corps prend le pavé),
# comme PosMap.ACCENT : code 0.97, « corps » pour tous les autres camos
ACCENT = 0.97
ACCENT_NAME = re.compile(r'grip|trigger|safety|selector|switch|hammer|bolt|charg|release|catch|latch|lever|(^|_)pin|ping|screw|button|buffer|deco|pull|ring', re.I)
# poignées (relief d'usinage : grain antidérapant ; Diamond : en or comme les petites pièces), comme PosMap.GRIP
GRIP = 0.985
GRIP_NAME = re.compile(r'grip', re.I)
STOCK_NAME = re.compile(r'stock|butt', re.I)
FRONT_NAME = re.compile(r'handguard|hand_guard|^hg$|foregrip|fore_grip|forend|gas|barrel|muzzle|suppress|silenc', re.I)
OTHER_NAME = re.compile(r'grip|sight|scope|carry|handle|rail|mount', re.I)
R_NONE, R_STOCK, R_FRONT, R_OTHER = 0, 1, 2, 3


def role_de(parent, name):
    """Rôle d'une pièce pour placer le pavé quand la carcasse n'est pas nommée (hérité des parents)."""
    if STOCK_NAME.search(name):
        return R_STOCK
    if FRONT_NAME.search(name):
        return R_FRONT
    if OTHER_NAME.search(name):
        return R_OTHER
    return parent


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def translate(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def rot(axis, a):
    c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]
    if axis == 'y':
        return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def apply(m, p):
    return [m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3)]


def part_matrix(parent, pos, r):
    """PoseStack de TaCZ : translation (pixels, ici sans /16), puis Z, Y, X."""
    m = mat_mul(parent, translate(*pos))
    if r[2]:
        m = mat_mul(m, rot('z', r[2]))
    if r[1]:
        m = mat_mul(m, rot('y', r[1]))
    if r[0]:
        m = mat_mul(m, rot('x', r[0]))
    return m


def box_quads(u, v, x, y, z, w, h, d, delta, mirror, tw, th):
    xe, ye, ze = x + w, y + h, z + d
    x, y, z = x - delta, y - delta, z - delta
    xe, ye, ze = xe + delta, ye + delta, ze + delta
    if mirror:
        x, xe = xe, x
    v1, v2, v3, v4 = (x, y, z), (xe, y, z), (xe, ye, z), (x, ye, z)
    v5, v6, v7, v8 = (x, y, ze), (xe, y, ze), (xe, ye, ze), (x, ye, ze)
    dx, dy, dz = int(w), int(h), int(d)
    p1, p2, p3 = u + dz, u + dz + dx, u + dz + dx + dx
    p4, p5 = u + dz + dx + dz, u + dz + dx + dz + dx
    p6, p7, p8, p9 = v + dz, v + dz + dy, v, u
    faces = [
        ([v6, v5, v1, v2], p1, p8, p2, p6),
        ([v3, v4, v8, v7], p2, p6, p3, p8),
        ([v1, v5, v8, v4], p9, p6, p1, p7),
        ([v2, v1, v4, v3], p1, p6, p2, p7),
        ([v6, v2, v3, v7], p2, p6, p4, p7),
        ([v5, v6, v7, v8], p4, p6, p5, p7),
    ]
    out = []
    for verts, u1, vv1, u2, vv2 in faces:
        uv = [(u2 / tw, vv1 / th), (u1 / tw, vv1 / th), (u1 / tw, vv2 / th), (u2 / tw, vv2 / th)]
        q = [list(verts[i]) + list(uv[i]) for i in range(4)]
        if mirror:
            q = q[::-1]
        out.append(q)
    return out


def face_quads(x, y, z, w, h, d, delta, tw, th, faces):
    xe, ye, ze = x + w, y + h, z + d
    x, y, z = x - delta, y - delta, z - delta
    xe, ye, ze = xe + delta, ye + delta, ze + delta
    v1, v2, v3, v4 = (x, y, z), (xe, y, z), (xe, ye, z), (x, ye, z)
    v5, v6, v7, v8 = (x, y, ze), (xe, y, ze), (xe, ye, ze), (x, ye, ze)
    # TaCZ échange est/ouest et haut/bas (axe y retourné)
    order = [([v6, v5, v1, v2], 'up'), ([v3, v4, v8, v7], 'down'), ([v1, v5, v8, v4], 'east'),
             ([v2, v1, v4, v3], 'north'), ([v6, v2, v3, v7], 'west'), ([v5, v6, v7, v8], 'south')]
    out = []
    for verts, key in order:
        f = faces.get(key)
        if not f or 'uv' not in f:
            continue
        u1, vv1 = f['uv']
        size = f.get('uv_size', [0, 0])
        u2, vv2 = u1 + size[0], vv1 + size[1]
        uv = [(u2 / tw, vv1 / th), (u1 / tw, vv1 / th), (u1 / tw, vv2 / th), (u2 / tw, vv2 / th)]
        out.append([list(verts[i]) + list(uv[i]) for i in range(4)])
    return out


def classe(cls, name):
    if cls in (BODY, RECEIVER) and MAG.search(name):
        return MAGAZINE
    if cls in (BODY, RECEIVER) and BARREL.search(name) and 'muzzle_flash' not in name.lower():
        return BARREL_PART
    if cls in (BODY, RECEIVER) and GRIP_NAME.search(name):
        return GRIP
    if cls in (BODY, RECEIVER) and ACCENT_NAME.search(name):
        return ACCENT
    if cls == BODY and RECEIVER_NAME.match(name):
        return RECEIVER
    if cls == RECEIVER and not RECEIVER_NAME.match(name) and NOT_RECEIVER.search(name):
        return BODY      # petite pièce rapportée sur la carcasse (détente, sûreté, vis, rail...) : pas de diamants
    return cls


def main():
    geo_path, tex_path, dossier, nom = sys.argv[1:5]
    os.makedirs(dossier, exist_ok=True)
    out_path = os.path.join(dossier, nom + '.json')
    shutil.copyfile(tex_path, os.path.join(dossier, nom + '.png'))
    data = json.load(open(geo_path))
    g = data['minecraft:geometry'][0]
    desc = g['description']
    tw, th = desc['texture_width'], desc['texture_height']
    bones = g.get('bones', [])
    by_name = {b['name']: b for b in bones}
    children = {}
    roots = []
    for b in bones:
        if b.get('parent'):
            children.setdefault(b['parent'], []).append(b)
        else:
            roots.append(b)
    quads = []      # (4 sommets x,y,z,u,v), partie, visible, rôle
    muzzles = []

    def walk(b, parent_m, cls, hidden, role):
        name = b['name']
        piv = b.get('pivot', [0, 0, 0])
        if b.get('parent'):
            pp = by_name[b['parent']].get('pivot', [0, 0, 0])
            pos = (piv[0] - pp[0], pp[1] - piv[1], piv[2] - pp[2])
        else:
            pos = (piv[0], 24 - piv[1], piv[2])
        r = [math.radians(a) for a in b.get('rotation', [0, 0, 0])]
        m = part_matrix(parent_m, pos, r)
        if cls != NONE and (EXCLUDE.search(name) or ILLUM.search(name)):
            cls = NONE
        else:
            cls = classe(cls, name)
        hidden = hidden or bool(HIDDEN.search(name))
        role = role_de(role, name)
        if 'muzzle' in name.lower():
            muzzles.append(apply(m, [0, 0, 0]))
        for c in b.get('cubes', []) or []:
            mirror = c.get('mirror', b.get('mirror', False))
            size = c['size']
            origin = c['origin']
            inflate = c.get('inflate', 0)
            uv = c.get('uv', [0, 0])
            if c.get('rotation'):
                cp = c.get('pivot', [0, 0, 0])
                cpos = (cp[0] - piv[0], piv[1] - cp[1], cp[2] - piv[2])
                cm = part_matrix(m, cpos, [math.radians(a) for a in c['rotation']])
                o = (origin[0] - cp[0], cp[1] - origin[1] - size[1], origin[2] - cp[2])
            else:
                cm = m
                o = (origin[0] - piv[0], piv[1] - origin[1] - size[1], origin[2] - piv[2])
            if isinstance(uv, dict):
                qs = face_quads(o[0], o[1], o[2], size[0], size[1], size[2], inflate, tw, th, uv)
            else:
                qs = box_quads(uv[0], uv[1], o[0], o[1], o[2], size[0], size[1], size[2], inflate, mirror, tw, th)
            for q in qs:
                pts = [apply(cm, v[:3]) + v[3:] for v in q]
                quads.append((pts, cls, not hidden, role))
        for ch in children.get(name, []):
            walk(ch, m, cls, hidden, role)

    ident = [[1, 0, 0, 0], [0, 1, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]
    for r in roots:
        walk(r, ident, BODY, False, R_NONE)
    # repère de l'arme (PosMap.build) : axe le plus long = longueur, orienté vers le canon
    mn = [1e9] * 3
    mx = [-1e9] * 3
    for pts, cls, vis, role in quads:
        if cls == NONE:
            continue
        for p in pts:
            for k in range(3):
                mn[k] = min(mn[k], p[k])
                mx[k] = max(mx[k], p[k])
    c = [(mn[k] + mx[k]) / 2 for k in range(3)]
    ext = [max(mx[k] - mn[k], 1e-4) for k in range(3)]
    a = max(range(3), key=lambda k: ext[k])
    b = 0 if a == 1 else 1
    for k in range(3):
        if k != a and ext[k] > ext[b]:
            b = k
    d = 3 - a - b
    ln = ext[a]
    sa = 1
    if muzzles:
        mm = sum(m[a] for m in muzzles) / len(muzzles)
        sa = 1 if mm >= c[a] else -1
    sb = -1 if b == 1 else 1

    def norm(p):
        return (sa * (p[a] - c[a]) / ln, sb * (p[b] - c[b]) / ln)

    # « carcasse » qui couvre presque toute une arme longue (pièce « body » qui contient tout) : ce n'en est pas une
    def aire(pts):
        e1 = [pts[1][k] - pts[0][k] for k in range(3)]
        e2 = [pts[3][k] - pts[0][k] for k in range(3)]
        cr = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]]
        return math.sqrt(sum(x * x for x in cr))
    a_rec = sum(aire(pts) for pts, cls, vis, role in quads if cls == RECEIVER)
    a_tout = sum(aire(pts) for pts, cls, vis, role in quads if cls != NONE)
    if a_rec > 0.45 * a_tout and ext[b] / ext[a] < 0.5:
        quads = [(pts, BODY if cls == RECEIVER else cls, vis, role) for pts, cls, vis, role in quads]
    # carcasse sans nom : zone entre l'avant de la crosse et l'arrière du garde-main, autour du haut du chargeur
    if not any(q[1] == RECEIVER for q in quads):
        mag = [norm(p) for pts, cls, vis, role in quads if cls == MAGAZINE for p in pts]
        if mag:
            mx0, mx1 = min(x for x, y in mag), max(x for x, y in mag)
            my1 = max(y for x, y in mag)
            w, mxc = mx1 - mx0, (mx0 + mx1) / 2
            arr = [max(norm(p)[0] for p in pts) for pts, cls, vis, role in quads if role == R_STOCK and cls != NONE]
            arr = [x for x in arr if x < mxc]
            rear = max(arr) if arr else mx0 - 1.3 * w - 0.03
            av = [min(norm(p)[0] for p in pts) for pts, cls, vis, role in quads if role == R_FRONT and cls != NONE]
            av = [x for x in av if x > mxc]
            front = min(min(av), mx1 + 0.03 + 0.5 * w) if av else mx1 + 0.03 + 0.25 * w
            y0, y1 = my1 - 0.075, my1 + 0.075
        else:
            rear, front, y0, y1 = -0.12, 0.08, -0.05, 0.08
        for i, (pts, cls, vis, role) in enumerate(quads):
            if cls != BODY or role != R_NONE:
                continue
            xs = [norm(p)[0] for p in pts]
            cy = sum(norm(p)[1] for p in pts) / 4
            qx0, qx1 = min(xs), max(xs)
            dedans = (min(qx1, front) - max(qx0, rear)) / max(qx1 - qx0, 1e-6)
            if dedans >= 0.6 and y0 <= cy <= y1:
                quads[i] = (pts, RECEIVER, vis, role)
    pos, nrm, uvs, cam, parts = [], [], [], [], []
    for pts, cls, vis, role in quads:
        if not vis:
            continue
        # deux triangles ; rendu : (-x, -y, z) comme le rendu des entités (y vers le haut)
        P = [[-p[0], -p[1], p[2]] for p in pts]
        e1 = [P[1][k] - P[0][k] for k in range(3)]
        e2 = [P[2][k] - P[0][k] for k in range(3)]
        n = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]]
        l = math.sqrt(sum(x * x for x in n))
        if l < 1e-9:
            e2 = [P[3][k] - P[0][k] for k in range(3)]
            n = [e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]]
            l = math.sqrt(sum(x * x for x in n))
            if l < 1e-9:
                continue
        n = [x / l for x in n]
        for tri in ((0, 1, 2), (0, 2, 3)):
            for i in tri:
                p = pts[i]
                pos += P[i]
                nrm += n
                uvs += [p[3], p[4]]
                cam += [sa * (p[a] - c[a]) / ln, sb * (p[b] - c[b]) / ln, (p[d] - c[d]) / ln]
                parts.append(cls)
    # centre et taille du rendu
    xs, ys, zs = pos[0::3], pos[1::3], pos[2::3]
    center = [(min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2, (min(zs) + max(zs)) / 2]
    size = max(max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs))
    json.dump({'texture': nom + '.png', 'pos': pos, 'nrm': nrm, 'uv': uvs, 'cam': cam, 'part': parts,
               'center': center, 'size': size, 'axis': a}, open(out_path, 'w'))
    print('%d triangles, longueur %.1f, axe %d, bouche %s' % (len(parts) // 3, ln, a, 'oui' if muzzles else 'non'))


if __name__ == '__main__':
    main()
