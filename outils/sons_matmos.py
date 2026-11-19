#!/usr/bin/env python3
"""
Sons de MAtmos (sons d'ambiance, mod d'un autre auteur, jar intact) lus en continu au lieu d'être décodés en entier
(audit des images par seconde du 19/11, point 7).

Minecraft décode en entier, à leur premier son, les sons qui ne sont pas « stream » et les garde en mémoire jusqu'au
rechargement des ressources : les 134 sons de MAtmos (tous « stream »: false) font 44 min, ≈ 244 Mo une fois décodés.
Un son « stream » est lu morceau par morceau, presque sans mémoire, MAIS Minecraft n'en joue que 8 à la fois (tous mods
confondus : nos hélicoptères, train, alarmes en ont 20) ; au-delà, le son ne part pas. Or MAtmos superpose ses ambiances :
oiseaux de la forêt toutes les 1,5 à 6,5 s (jusqu'à 78 s chacun : ≈ 4 à la fois), jungle (≈ 7 à la fois), eau dormante
(toutes les 5 s, 30 s chacune : ≈ 6), grand vent (≈ 5), orage, désert (≈ 2) : en continu, ils videraient les 8 places.
Seuls passent en continu les sons longs qui ne jouent qu'un à la fois (calcul fait sur les procédures de MAtmos 7.1) :
  - les musiques de biome (désert, village du désert, jungle, badlands, End, Nether : une fois, au saut dans le biome) ;
  - l'ambiance du Nether (toutes les 33 s, 35,5 s) ;
  - le vent d'intérieur (202 s ; aucune procédure de MAtmos 7.1 ne le joue aujourd'hui).
Les autres restent tels quels.

Ce script écrit, dans notre pack imposé (heraliz_interface, resourcepacks/textures), assets/matmos_tct/sounds.json :
ces événements seulement, « replace »: true, mêmes sons, volumes et réglages que MAtmos, « stream »: true.

    python outils/sons_matmos.py              écrit le fichier (et dit la mémoire gagnée)
    python outils/sons_matmos.py --verifier   code 1 s'il n'est plus à jour avec le jar de MAtmos
"""
import argparse
import json
import struct
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import chemins  # noqa: E402

PACK = chemins.MODS / "interface" / "src" / "main" / "resources" / "resourcepacks" / "textures"
SORTIE = PACK / "assets" / "matmos_tct" / "sounds.json"
EN_CONTINU = ("music.desert", "music.desertvillage", "music.jungle", "music.badlands", "music.end", "music.nether",
              "biome.nether.ambiant", "weather.interiorwind")


def jar():
    js = sorted(chemins.MODS_TIERS.glob("MAtmos-*.jar"))
    if not js:
        raise SystemExit("[matmos] jar de MAtmos absent de serveur/mods-tiers")
    return zipfile.ZipFile(js[-1])


def duree_memoire(z, nom):
    """(secondes, octets une fois décodé en 16 bits) d'un son de MAtmos."""
    ns, p = nom.split(":", 1)
    b = z.read("assets/%s/sounds/%s.ogg" % (ns, p))          # Minecraft : sounds/<nom>.ogg, points compris
    i = b.find(b"\x01vorbis")
    canaux, frequence = b[i + 11], struct.unpack("<I", b[i + 12:i + 16])[0]
    j = b.rfind(b"OggS")
    s = struct.unpack("<q", b[j + 6:j + 14])[0] / frequence
    return s, s * frequence * canaux * 2


def contenu(z):
    d = json.loads(z.read("assets/matmos_tct/sounds.json").decode("utf-8"))
    out, gain = {}, 0.0
    for ev in EN_CONTINU:
        if ev not in d:
            raise SystemExit("[matmos] événement %s absent du sounds.json de MAtmos (jar changé ?) : revoir EN_CONTINU" % ev)
        e = dict(d[ev])
        sons = []
        for s in e["sounds"]:
            s = {"name": s} if isinstance(s, str) else dict(s)
            if s.get("type", "file") == "file":
                s["stream"] = True
                gain += duree_memoire(z, s["name"])[1]
            sons.append(s)
        e["sounds"] = sons
        e["replace"] = True
        out[ev] = e
    return json.dumps(out, indent=2, ensure_ascii=False) + "\n", gain


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--verifier", action="store_true")
    a = ap.parse_args()
    texte, gain = contenu(jar())
    avant = SORTIE.read_text(encoding="utf-8") if SORTIE.exists() else None
    if a.verifier:
        if avant != texte:
            print("[matmos] %s pas à jour : relancer python outils/sons_matmos.py" % SORTIE.relative_to(chemins.DEPOT))
            sys.exit(1)
        print("[matmos] à jour")
        return
    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    with open(SORTIE, "w", encoding="utf-8", newline="\n") as f:
        f.write(texte)
    print("[matmos] écrit %s : %d événements en continu, ≈ %.0f Mo de moins une fois joués" % (
        SORTIE.relative_to(chemins.DEPOT), len(EN_CONTINU), gain / 1e6))


if __name__ == "__main__":
    main()
