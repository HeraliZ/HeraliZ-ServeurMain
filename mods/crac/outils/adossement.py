#!/usr/bin/env python3
"""
Objets « adossés » de CRac (1.11.0) : l'armoire à outils (Claim Block) et la caisse en métal se collent aux murs.

Les murs de CRac occupent toute la rangée de cases qui longe le bord d'une fondation (4 px d'épaisseur, centrés sur le
bord) : un bloc ne peut pas être posé contre un mur. Un objet adossé reste posé dans la case libre voisine, mais il est
dessiné (et sa forme est) poussé d'une case vers le mur ou vers l'angle, à 0,25 px de la face du mur.

Ce script écrit, depuis le modèle de base de chaque objet, ses 8 modèles décalés et son fichier d'états (sens × adossement
× autres propriétés). Jamais à la main : on modifie le script ou le modèle de base, puis on relance.

    python mods/crac/outils/adossement.py           écrit les modèles et états de la caisse en métal
    (l'armoire est écrite par mods/crac/outils/tc_modele.py --installer, qui appelle ce module)

Mêmes nombres que fr.heraliz.crac.block.Adosse (Java) : à changer des deux côtés.
"""
import copy
import json
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[1] / "src" / "client" / "resources" / "assets" / "crac"
# bord de la boîte poussée : face intérieure du mur de la case voisine (murs de 2 px de demi-épaisseur), moins 0,25 px
LIMITE_HAUT = 29.75
LIMITE_BAS = -13.75
AUCUN = 4
ROTATION = {"north": 0, "east": 90, "south": 180, "west": 270}
# boîtes de référence, objet tourné vers le nord (x0, z0, x1, z1 en px) : les mêmes que dans Adosse.java
BOITE_ARMOIRE = (0.25, 2.5, 15.75, 15.75)
BOITE_CAISSE = (0.0, 1.5, 16.0, 14.5)


def sens(code):
    """Code d'adossement (0 à 8) -> (sx, sz) en -1, 0, 1 (4 : aucun)."""
    return code // 3 - 1, code % 3 - 1


def code(sx, sz):
    return (sx + 1) * 3 + (sz + 1)


def vers_modele(facing, wx, wz):
    """Sens dans le monde -> sens dans le modèle (tourné vers le nord) pour l'état « facing »."""
    r = ROTATION[facing]
    if r == 90:
        return wz, -wx
    if r == 180:
        return -wx, -wz
    if r == 270:
        return -wz, wx
    return wx, wz


def decalage_modele(boite, mx, mz):
    """Décalage (px) dans le modèle pour pousser la boîte dans le sens (mx, mz)."""
    x0, z0, x1, z1 = boite
    ox = LIMITE_HAUT - x1 if mx > 0 else LIMITE_BAS - x0 if mx < 0 else 0.0
    oz = LIMITE_HAUT - z1 if mz > 0 else LIMITE_BAS - z0 if mz < 0 else 0.0
    return round(ox, 4), round(oz, 4)


def decaler(modele, ox, oz):
    """Copie du modèle, chaque pièce (et l'origine de sa rotation) déplacée de (ox, 0, oz)."""
    m = copy.deepcopy(modele)
    for e in m.get("elements", []):
        e["from"] = [round(e["from"][0] + ox, 4), e["from"][1], round(e["from"][2] + oz, 4)]
        e["to"] = [round(e["to"][0] + ox, 4), e["to"][1], round(e["to"][2] + oz, 4)]
        if "rotation" in e and "origin" in e["rotation"]:
            o = e["rotation"]["origin"]
            e["rotation"]["origin"] = [round(o[0] + ox, 4), o[1], round(o[2] + oz, 4)]
        for v in e["from"] + e["to"]:
            if v < -16 or v > 32:
                raise SystemExit(f"pièce hors des limites de Minecraft (-16 à 32) : {e.get('name')} {e['from']} {e['to']}")
    m.pop("display", None)   # en main : le modèle de base
    return m


def ecrire(chemin, donnees):
    chemin.parent.mkdir(parents=True, exist_ok=True)
    chemin.write_text(json.dumps(donnees, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def modeles_decales(nom, modele, boite):
    """Écrit les 8 modèles décalés « <nom>_adosse_<sens du modèle> » ; rend le nom du modèle pour chaque sens du modèle."""
    noms = {}
    for c in range(9):
        mx, mz = sens(c)
        if c == AUCUN:
            noms[(mx, mz)] = f"crac:block/{nom}"
            continue
        ox, oz = decalage_modele(boite, mx, mz)
        ecrire(ASSETS / f"models/block/{nom}_adosse_{c}.json", decaler(modele, ox, oz))
        noms[(mx, mz)] = f"crac:block/{nom}_adosse_{c}"
    return noms


def variantes(noms_par_modele, autres):
    """
    États « adosse=…,facing=…[,autre=…] » -> modèle décalé et rotation. {@code noms_par_modele} : valeur des autres
    propriétés (chaîne « tier=1 » ou « » ) -> {(mx, mz): modèle}.
    """
    out = {}
    for f, r in ROTATION.items():
        for c in range(9):
            wx, wz = sens(c)
            mx, mz = vers_modele(f, wx, wz)
            for autre in autres:
                cle = f"adosse={c},facing={f}" + (f",{autre}" if autre else "")
                v = {"model": noms_par_modele[autre][(mx, mz)]}
                if r:
                    v["y"] = r
                out[cle] = v
    return out


def caisse():
    noms = {}
    for ouvert in ("false", "true"):
        nom = "metal_crate_open" if ouvert == "true" else "metal_crate_closed"
        base = json.loads((ASSETS / f"models/block/{nom}.json").read_text(encoding="utf-8"))
        noms[f"open={ouvert}"] = modeles_decales(nom, base, BOITE_CAISSE)
    ecrire(ASSETS / "blockstates/metal_crate.json", {"variants": variantes(noms, ["open=false", "open=true"])})
    print("[adossement] caisse en métal : 16 modèles décalés, 72 états")


def armoire(modele):
    """Appelé par tc_modele.py --installer, une fois le modèle de base écrit."""
    noms = modeles_decales("claim_block", modele, BOITE_ARMOIRE)
    tiers = [f"tier={t}" for t in (1, 2, 3)]
    ecrire(ASSETS / "blockstates/claim_block.json", {"variants": variantes({t: noms for t in tiers}, tiers)})
    haut = {(mx, mz): "crac:block/claim_block_haut" for mx in (-1, 0, 1) for mz in (-1, 0, 1)}
    ecrire(ASSETS / "blockstates/claim_block_haut.json", {"variants": variantes({"": haut}, [""])})
    print("[adossement] armoire : 8 modèles décalés, 108 états (+ 36 pour sa case du haut)")


if __name__ == "__main__":
    caisse()
