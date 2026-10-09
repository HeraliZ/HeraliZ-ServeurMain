#!/usr/bin/env python3
"""
Arme TaCZ de référence pour l'aperçu des jumelles (même rendu des bras que les armes) : os du modèle Bedrock (hiérarchie,
positions, rotations, comme BedrockModel de TaCZ), cubes de chaque os, pose « static_idle » de l'animation (mains).

usage : python3 prep_tacz.py <modèle *_geo.json> <texture .png> <animation .json> <dossier> <nom>
exemple (gunpack du serveur, M16A1) :
  python3 prep_tacz.py serveur/tacz/arkaniaz_pack_0_converted/assets/tacz/geo_models/gun/m16a1_geo.json \
      serveur/tacz/arkaniaz_pack_0_converted/assets/tacz/textures/gun/uv/m16a1.png \
      serveur/tacz/arkaniaz_pack_0_converted/assets/tacz/animations/m16a1.animation.json <dossier> m16a1
"""
import json, math, re, shutil, sys
from pathlib import Path

# pièces cachées par TaCZ (variantes de chargeur, balles, flamme) et cubes des mains (remplacés par les bras)
HIDDEN = re.compile(r'mag_extended|extd_mag|additional_magazine|^bullet|muzzle_flash|^lod|_pos$', re.I)


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
    faces = [([v6, v5, v1, v2], p1, p8, p2, p6), ([v3, v4, v8, v7], p2, p6, p3, p8), ([v1, v5, v8, v4], p9, p6, p1, p7),
             ([v2, v1, v4, v3], p1, p6, p2, p7), ([v6, v2, v3, v7], p2, p6, p4, p7), ([v5, v6, v7, v8], p4, p6, p5, p7)]
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


def rotm(r):
    # BedrockPart : Z, puis Y, puis X (matrice Rz·Ry·Rx)
    x, y, z = r
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    Rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    Ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    Rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    mm = lambda a, b: [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    return mm(mm(Rz, Ry), Rx)


def main():
    geo, tex, anim, dossier, nom = sys.argv[1:6]
    Path(dossier).mkdir(parents=True, exist_ok=True)
    shutil.copyfile(tex, Path(dossier) / (nom + '.png'))
    g = json.load(open(geo))['minecraft:geometry'][0]
    tw, th = g['description']['texture_width'], g['description']['texture_height']
    by = {b['name']: b for b in g['bones']}
    os_ = []
    for b in g['bones']:
        piv = b.get('pivot', [0, 0, 0])
        if b.get('parent'):
            pp = by[b['parent']].get('pivot', [0, 0, 0])
            pos = [piv[0] - pp[0], pp[1] - piv[1], piv[2] - pp[2]]
        else:
            pos = [piv[0], 24 - piv[1], piv[2]]
        rot = [math.radians(a) for a in b.get('rotation', [0, 0, 0])]
        pts, uvs = [], []
        if not HIDDEN.search(b['name']):
            for c in b.get('cubes', []) or []:
                mirror = c.get('mirror', b.get('mirror', False))
                size, origin, inflate, uv = c['size'], c['origin'], c.get('inflate', 0), c.get('uv', [0, 0])
                if c.get('rotation'):
                    cp = c.get('pivot', [0, 0, 0])
                    cpos = (cp[0] - piv[0], piv[1] - cp[1], cp[2] - piv[2])
                    R = rotm([math.radians(a) for a in c['rotation']])
                    o = (origin[0] - cp[0], cp[1] - origin[1] - size[1], origin[2] - cp[2])
                else:
                    cpos, R = (0, 0, 0), None
                    o = (origin[0] - piv[0], piv[1] - origin[1] - size[1], origin[2] - piv[2])
                qs = face_quads(*o, *size, inflate, tw, th, uv) if isinstance(uv, dict) else box_quads(uv[0], uv[1], *o, *size, inflate, mirror, tw, th)
                for q in qs:
                    P = []
                    for v in q:
                        p = v[:3]
                        if R:
                            p = [sum(R[i][k] * p[k] for k in range(3)) + cpos[i] for i in range(3)]
                        P.append(p + v[3:])
                    for tri in ((0, 1, 2), (0, 2, 3)):
                        for i in tri:
                            pts += P[i][:3]
                            uvs += P[i][3:5]
        os_.append({'nom': b['name'], 'parent': b.get('parent'), 'pos': pos, 'rot': rot, 'pts': pts, 'uv': uvs})
    a = json.load(open(anim))['animations']['static_idle']['bones']
    pose = {}
    for n, v in a.items():
        def val(k):
            x = v.get(k)
            if isinstance(x, dict):
                x = x[sorted(x, key=float)[0]]
                if isinstance(x, dict):
                    x = x.get('post') or x.get('pre') or x.get('vector')
            return x
        pose[n] = {'rotation': val('rotation'), 'position': val('position'), 'scale': val('scale')}
    json.dump({'texture': nom + '.png', 'os': os_, 'pose': pose}, open(Path(dossier) / (nom + '.json'), 'w'))
    print(nom, len(os_), 'os', sum(len(o['pts']) // 9 for o in os_), 'triangles', pose.get('lefthand'), pose.get('righthand'))


main()
