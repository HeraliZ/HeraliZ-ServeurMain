"""Reachability of the key places of each ClashWar map (A half; the B half is the mirror), bomb sites (free 3 x 3
slab, reachable) and the path from spawn A to spawn B. Usage: python zones_xl.py [map id ...] (all four by default)"""
import sys

import acces_xl

ZONES = {
    "clashwar_2v2_kombinat": [
        ("toits des garages (nord-ouest)", 2, 30, 2, 24, 9, True),
        ("barre A : étage 1 (par l'escalier)", 31, 98, 31, 39, 9, True),
        ("barre A : étage 2", 31, 98, 31, 39, 13, True),
        ("barre A : étage 3 (fermé)", 31, 98, 31, 39, 17, False),
        ("barre A : toit (fermé)", 30, 99, 30, 40, 21, False),
        ("balcons, cour (z 41..42)", 31, 88, 41, 42, 13, True),
        ("auvents des entrées", 31, 88, 41, 42, 9, True),
        ("petite aile : étage 2", 90, 98, 42, 71, 13, True),
        ("sous-station : toit", 68, 78, 47, 54, 10, True),
        ("toboggan (plateforme)", 51, 53, 48, 50, 8, True),
        ("pigeonnier", 118, 121, 33, 36, 10, True),
        ("baraque : étage", 3, 12, 48, 59, 9, True),
        ("baraque : toit (fermé)", 2, 13, 46, 61, 15, False),
        ("chaufferie : passerelle", 78, 91, 4, 4, 9, True),
        ("chaufferie : toit (fermé)", 77, 92, 3, 12, 14, False),
        ("cheminée (fermée)", 94, 96, 4, 6, 12, False),
        ("magasin : toit", 105, 124, 3, 13, 10, True),
        ("butte du chantier", 2, 4, 30, 34, 8, True),
        ("fond de la fosse", 5, 14, 31, 39, 2, True),
        ("bus couché (dessus)", 39, 49, 15, 17, 8, True),
        ("hangar du spawn : toit (fermé)", 55, 74, 2, 12, 11, False),
    ],
    "clashwar_1v1_depot": [
        ("passerelle (tablier)", 26, 63, 24, 25, 11, True),
        ("château d'eau : salle haute", 8, 15, 31, 37, 11, True),
        ("château d'eau : cuve (fermée)", 6, 17, 29, 40, 16, False),
        ("estacade (tablier)", 17, 20, 8, 30, 9, True),
        ("remise : galerie", 67, 67, 13, 39, 11, True),
        ("remise : fosses de visite", 69, 81, 15, 37, 3, True),
        ("remise : toit (fermé)", 66, 87, 12, 40, 14, False),
        ("poste d'aiguillage : salle haute", 26, 30, 31, 35, 10, True),
        ("plaque tournante : fosse", 38, 51, 38, 43, 3, True),
        ("vestiaire du spawn : toit (fermé)", 37, 52, 2, 9, 10, False),
    ],
    "clashwar_3v3_port": [
        ("grue : plateforme (9 de haut)", 41, 48, 56, 62, 14, True),
        ("navire : pont", 21, 61, 70, 80, 8, True),
        ("navire : passerelle de commande", 21, 28, 72, 78, 12, True),
        ("navire : cales", 31, 49, 72, 78, 2, True),
        ("fond du bassin", 14, 155, 66, 84, 1, True),
        ("pont central (tablier)", 82, 87, 66, 84, 5, True),
        ("jetée et dessous", 96, 99, 66, 78, 1, True),
        ("entrepôt : mezzanine", 105, 122, 18, 37, 10, True),
        ("capitainerie : étage 2", 61, 84, 17, 25, 13, True),
        ("capitainerie : étage 3 (fermé)", 61, 84, 17, 25, 17, False),
        ("silo : toit (fermé)", 146, 158, 24, 36, 24, False),
        ("douane du spawn : toit (fermé)", 76, 93, 2, 11, 10, False),
        ("réservoirs : toit (fermé)", 147, 166, 4, 20, 14, False),
    ],
    "clashwar_4v4_garnison": [
        ("caserne : étage 1 (par l'escalier)", 31, 84, 23, 31, 9, True),
        ("caserne : étage 2", 31, 84, 23, 31, 13, True),
        ("caserne : étage 3 (fermé)", 31, 84, 23, 31, 17, False),
        ("caserne : toit (fermé)", 30, 85, 22, 32, 21, False),
        ("état-major : étage 2", 139, 166, 23, 31, 13, True),
        ("état-major : étage 3 (fermé)", 139, 166, 23, 31, 17, False),
        ("tour de guet du dépôt", 22, 25, 18, 21, 11, True),
        ("buttes du dépôt (dessus)", 6, 14, 5, 11, 10, True),
        ("tribune", 62, 73, 37, 38, 7, True),
        ("tranchée du polygone", 66, 94, 9, 17, 4, True),
        ("rampe d'inspection (pistes)", 160, 173, 4, 7, 7, True),
        ("fosse d'inspection", 161, 172, 5, 6, 4, True),
        ("club : galerie", 97, 122, 84, 88, 10, True),
        ("club : balcon du portique", 101, 118, 90, 92, 10, True),
        ("club : toit (fermé)", 96, 123, 72, 89, 16, False),
        ("clocher : beffroi", 169, 172, 81, 86, 14, True),
        ("clocher : toit (fermé)", 168, 173, 80, 87, 20, False),
        ("isba : grenier", 48, 50, 73, 79, 10, True),
        ("hangar du spawn : toit (fermé)", 98, 121, 2, 21, 14, False),
        ("boxes du parc auto : toits (fermés)", 178, 217, 2, 12, 11, False),
        ("chaufferie : toit (fermé)", 6, 19, 50, 59, 12, False),
        ("cheminée (fermée)", 2, 4, 53, 55, 27, False),
        ("château d'eau (fermé)", 14, 21, 100, 107, 21, False),
        ("citernes (fermées)", 182, 189, 42, 49, 12, False),
    ],
}


def main(ident):
    m, st, vu = acces_xl.accessibles(ident)
    pb = 0
    for nom, x0, x1, z0, z1, y, voulu in ZONES.get(ident, []):
        n = int(vu[x0:x1 + 1, y, z0:z1 + 1].sum())
        ok = (n > 0) == voulu
        pb += 0 if ok else 1
        print(f"{'ok ' if ok else 'PB '} {nom:40s} pieds y={y:2d} : {n:4d} cases atteignables ({'voulu' if voulu else 'interdit'})")
    for k in ("bombe_a", "bombe_b"):
        x, y, z = (int(v) for v in m["meta"]["points"][k][0][:3])
        ok = all(st[x + dx, y, z + dz] and vu[x + dx, y, z + dz] for dx in (-1, 0, 1) for dz in (-1, 0, 1))
        pb += 0 if ok else 1
        print(f"{'ok ' if ok else 'PB '} site {k[-1].upper()} ({x}, {z}) : dalle 3 x 3 libre et atteignable")
    # the two teams reach each other (flood fill from spawn A alone reaches every spawn B)
    _m, _st, vu_a = acces_xl.accessibles(ident, departs=("spawn_a",))
    sb = [tuple(int(v) for v in (q[0], round(q[1]), q[2])) for q in m["meta"]["points"]["spawn_b"]]
    ok = all(vu_a[p] for p in sb)
    pb += 0 if ok else 1
    print(f"{'ok ' if ok else 'PB '} depuis le spawn A on atteint le spawn B à pied")
    print(pb, "problème(s)")
    return pb


if __name__ == "__main__":
    sys.exit(1 if sum(main(c) for c in (sys.argv[1:] or list(ZONES))) else 0)
