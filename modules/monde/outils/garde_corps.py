"""Garde-corps de HeraliZ Décors (18/10, demande du propriétaire : « poser deux model 240 sur le même bloc pour faire un
angle ») : le garde-corps en métal du « Model 240 » du mod de meubles (même forme, même texture, lue dans le mod de
meubles : rien n'est copié), mais un même bloc peut en porter un sur chacun de ses 4 côtés (nord, est, sud, ouest). Le
garde-corps est posé à l'intérieur du bloc, contre le bord (le Model 240 dépasse de 1 px chez le voisin) : deux côtés se
rejoignent en angle sans trou.

    import garde_corps ; garde_corps.ecrire(A)        (appelé par gen_assets.py)
"""
import json
import os

NS = "heraliz_decors"
BLOC = "garde_corps"
NOMS = {BLOC: ("Garde-corps (métal)", "Railing (metal)")}
TEX = "cfm:block/blocks/iron_block"

# boîtes du Model 240 (côté nord), décalées de 1 px vers l'intérieur du bloc (z de 0 à 1) ; mêmes UV
BOITES = (
    ((0, 0, 0), (0.5, 16, 1), {"north": [3.125, 0, 3.625, 16], "east": [4.25, 0, 5.25, 16], "south": [3.5, 0, 4, 16],
                               "west": [4.25, 0, 5.25, 16], "up": [4.25, 14.75, 5.25, 16], "down": [4.25, 0, 5.25, 16]}),
    ((15.5, 0, 0), (16, 16, 1), {"north": [15, 0, 15.5, 16], "east": [15, 0, 16, 16], "south": [15, 0, 15.5, 16],
                                 "west": [15, 0, 16, 16], "up": [15, 14.5, 16, 16], "down": [15, 0, 16, 16]}),
    ((0.5, 15, 0), (15.5, 16, 1), {"north": [1, 2.625, 16, 3.625], "east": [1.5, 2.625, 15.5, 3.625],
                                   "south": [1, 2.625, 16, 3.625], "west": [1.5, 2.625, 15.5, 3.625],
                                   "up": [1.5, 2.625, 15.5, 3.625], "down": [1.5, 2.625, 15.5, 3.625]}),
    ((0.5, 12, 0), (15.5, 12.8, 0.8), {"north": [1, 2.625, 16, 3.425], "east": [1.5, 2.625, 15.5, 3.625],
                                       "south": [1, 2.625, 16, 3.425], "west": [1.5, 2.625, 15.5, 3.625],
                                       "up": [1.5, 2.625, 15.5, 3.625], "down": [1.5, 2.625, 15.5, 3.625]}),
    ((0.5, 5, 0), (15.5, 6, 1), {"north": [1.5, 2.625, 15.5, 3.625], "east": [1.5, 2.625, 15.5, 3.625],
                                 "south": [1, 2.625, 16, 3.625], "west": [1.5, 2.625, 15.5, 3.625],
                                 "up": [1.5, 2.625, 15.5, 3.625], "down": [1.5, 2.625, 15.5, 3.625]}),
)
DIRS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def modele():
    el = [{"from": list(a), "to": list(b), "faces": {f: {"uv": uv, "texture": "#0"} for f, uv in faces.items()}}
          for a, b, faces in BOITES]
    return {"textures": {"0": TEX, "particle": TEX}, "elements": el}


def objet():
    return {"parent": f"{NS}:block/{BLOC}", "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}}}


def etat():
    return {"multipart": [{"when": {d: "true"}, "apply": {"model": f"{NS}:block/{BLOC}", "y": y}} for d, y in DIRS]}


def ecrire(A):
    for sous, obj in (("models/block", modele()), ("models/item", objet()), ("blockstates", etat())):
        os.makedirs(os.path.join(A, sous), exist_ok=True)
        with open(os.path.join(A, sous, BLOC + ".json"), "w", encoding="utf-8") as f:
            json.dump(obj, f, indent=1)


if __name__ == "__main__":
    ecrire(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "client", "resources", "assets", NS))
