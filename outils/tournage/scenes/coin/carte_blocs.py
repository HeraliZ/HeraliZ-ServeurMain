import sys, numpy as np
sys.path.insert(0, '/home/user/HeraliZ-ServeurMain/outils')
import monde_spawn as ms
MONDE = '/home/user/HeraliZ-ServeurMain/monde/HeraliZ'
cache = {}
def chunk(cx, cz):
    rx, rz = cx >> 5, cz >> 5
    if (rx, rz) not in cache:
        cache[(rx, rz)] = ms.region_lire('%s/region/r.%d.%d.mca' % (MONDE, rx, rz))
    return cache[(rx, rz)].get((cx & 31, cz & 31))
def bloc(x, y, z):
    c = chunk(x >> 4, z >> 4)
    if c is None: return '?'
    for s in c['sections'].v[1]:
        if s['Y'].v == (y >> 4):
            pal, v = ms.decoder_section(s)
            if pal is None: return 'minecraft:air'
            i = ((y & 15) * 16 + (z & 15)) * 16 + (x & 15)
            return pal[v[i]]['Name'].v
    return 'minecraft:air'
PASS = ('air', 'grass', 'fern', 'flower', 'poppy', 'dandelion', 'snow', 'dead_bush', 'torch', 'button', 'carpet', 'rail', 'sign', 'pressure', 'vine', 'tulip', 'orchid', 'allium', 'bluet', 'daisy', 'cornflower', 'lily', 'sapling', 'decors:sang', 'tache')
def car(n):
    s = n.split(':')[-1]
    if any(p in n for p in PASS) and 'block' not in s: return '.'
    if 'glass' in s or 'pane' in s: return 'v'
    if 'leaves' in s: return 'f'
    if 'door' in s: return 'D'
    if 'slab' in s or 'stairs' in s: return 's'
    if 'fence' in s or 'wall' in s and 'wall_' not in s: return '+'
    return '#'
x0, x1, z0, z1, ys = int(sys.argv[1]), int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]), [int(a) for a in sys.argv[5].split(',')]
for y in ys:
    print('y=%d   x de %d (gauche) a %d (droite)' % (y, x0, x1))
    for z in range(z0, z1 + 1):
        print('%5d ' % z + ''.join(car(bloc(x, y, z)) for x in range(x0, x1 + 1)))
