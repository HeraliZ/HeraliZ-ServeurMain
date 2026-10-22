"""Écrit src/commun/resources/data/heraliz_emote/liste.json : les emotes HeraliZ (id, uuid, nom) lues par le serveur
(déblocages, caisse des emotes). Source : le catalogue et les fichiers des emotes du jar joueur. Jamais à la main :
relancer après l'ajout ou le retrait d'une emote.

    python mods/heraliz-emote/outils/liste_emotes.py
"""
import json
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
EMOTES = RACINE / "src/client/resources/assets/heraliz_emote/emotes"
SORTIE = RACINE / "src/commun/resources/data/heraliz_emote/liste.json"


def main():
    catalogue = json.loads((EMOTES / "catalog.json").read_text(encoding="utf-8"))
    liste = []
    for e in catalogue["emotes"]:
        fichier = EMOTES / (e["id"] + ".json")
        nom = e["id"]
        if fichier.exists():
            nom = json.loads(fichier.read_text(encoding="utf-8")).get("name", nom)
        liste.append({"id": e["id"], "uuid": e["uuid"], "nom": nom})
    SORTIE.write_text(json.dumps({"emotes": liste}, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{len(liste)} emotes -> {SORTIE.relative_to(RACINE)}")


if __name__ == "__main__":
    main()
