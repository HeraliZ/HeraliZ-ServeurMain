"""Écrit src/commun/resources/data/heraliz_emote/liste.json et l'objet « icône d'emote » (modèles et textures de
heraliz_emote:icone, une par emote, CustomModelData = rang + 1, pour la roue de la caisse des emotes). Liste : : les emotes HeraliZ (id, uuid, nom, avec accessoires ou non) lues par le serveur
(déblocages, caisse des emotes). Source : le catalogue et les fichiers des emotes du jar joueur. Jamais à la main :
relancer après l'ajout ou le retrait d'une emote.

    python modules/emotes/outils/liste_emotes.py
"""
import json
from pathlib import Path

from PIL import Image

RACINE = Path(__file__).resolve().parent.parent
EMOTES = RACINE / "src/client/resources/assets/heraliz_emote/emotes"
# animations (.json) et musiques (.nbs) : dans les ressources communes, le serveur s'en sert (EmotesOfficielles)
ANIMATIONS = RACINE / "src/commun/resources/assets/heraliz_emote/emotes"
SORTIE = RACINE / "src/commun/resources/data/heraliz_emote/liste.json"
ASSETS = RACINE / "src/client/resources/assets/heraliz_emote"


def main():
    catalogue = json.loads((EMOTES / "catalog.json").read_text(encoding="utf-8"))
    liste = []
    for e in catalogue["emotes"]:
        fichier = ANIMATIONS / (e["id"] + ".json")
        nom = e["id"]
        if fichier.exists():
            nom = json.loads(fichier.read_text(encoding="utf-8")).get("name", nom)
        accessoires = bool(e.get("props") or e.get("particles"))
        liste.append({"id": e["id"], "uuid": e["uuid"], "nom": nom, "accessoires": accessoires})
    icones(liste)
    SORTIE.write_text(json.dumps({"emotes": liste}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{len(liste)} emotes -> {SORTIE.relative_to(RACINE)}")


def icones(liste):
    """Objet heraliz_emote:icone : un modèle par emote (icône du menu des emotes réduite à 64 px)."""
    modeles = ASSETS / "models/item/icone"
    textures = ASSETS / "textures/item/icone"
    modeles.mkdir(parents=True, exist_ok=True)
    textures.mkdir(parents=True, exist_ok=True)
    for f in list(modeles.glob("*.json")) + list(textures.glob("*.png")):
        f.unlink()
    overrides = []
    for i, e in enumerate(liste):
        png = EMOTES / (e["id"] + ".png")
        if png.exists():
            Image.open(png).convert("RGBA").resize((64, 64), Image.LANCZOS).save(textures / (e["id"] + ".png"))
            tex = "heraliz_emote:item/icone/" + e["id"]
        else:
            tex = "minecraft:item/name_tag"
        (modeles / (e["id"] + ".json")).write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": tex}}, indent=2) + "\n", encoding="utf-8")
        overrides.append({"predicate": {"custom_model_data": i + 1}, "model": "heraliz_emote:item/icone/" + e["id"]})
        e["modele"] = i + 1
    (ASSETS / "models/item/icone.json").write_text(json.dumps({"parent": "minecraft:item/generated",
        "textures": {"layer0": "minecraft:item/name_tag"}, "overrides": overrides}, indent=1) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
