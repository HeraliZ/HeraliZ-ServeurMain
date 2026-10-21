#!/usr/bin/env python3
"""
Couteaux du pack « CS2 Knifes Pack » (gunpack cs2_wt de LR Tactical, 21/10, choix du propriétaire : WT Skeleton, WT Push,
WT Stiletto) : recopiés dans heralisz_melee sous les noms cs2_skeleton, cs2_push, cs2_stiletto (modèle, texture, icône,
animations, sons, machine d'états du Skeleton), fiche réglée comme nos armes de mêlée (1,2 s entre deux coups, clic gauche
comme clic droit ; portée ramenée à 3 blocs par le serveur ; +5 % de vitesse en main), affichage en 3e personne et sons de
coup de la Dague TAC. Usage : python couteaux_cs2.py <dossier dézippé du pack> (relance sans danger).
"""
import json
import re
import shutil
import sys
from collections import OrderedDict
from pathlib import Path

OUTILS = Path(__file__).resolve().parent
MOD = OUTILS.parent
ASSETS = MOD / "src" / "client" / "resources" / "assets" / "heralisz_melee"
DATA = MOD / "src" / "serveur" / "resources" / "data" / "heralisz_melee" / "index" / "melee"
COUTEAUX = {
    # id : (nom fr, en, ru)
    "skeleton": ("Couteau Skeleton", "Skeleton Knife", "Нож Skeleton"),
    "push": ("Couteau Push", "Push Dagger", "Тычковый нож Push"),
    "stiletto": ("Stiletto", "Stiletto Knife", "Стилет"),
}
DESC = ("§8Exclusivité HeraliZ", "§8HeraliZ Exclusive", "§8Эксклюзив HeraliZ")
CADENCE = 24  # ticks : 1,2 s, comme toute la mêlée (18/10)


def lire(p):
    s = Path(p).read_text(encoding="utf-8-sig")
    s = re.sub(r"^\s*//[^\n]*$", "", s, flags=re.M)
    return json.loads(s, object_pairs_hook=OrderedDict)


def ecrire(p, d):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def renommer(texte, k):
    texte = texte.replace("cs2_wt:melee/knife/", "heralisz_melee:melee/cs2/")
    return texte


def main():
    src = Path(sys.argv[1]) / "assets" / "cs2_wt"
    data_src = Path(sys.argv[1]) / "data" / "cs2_wt" / "index" / "melee"
    modele_aff = lire(ASSETS / "display" / "melee" / "tac_dagger.json")
    langues = {l: lire(ASSETS / "lang" / (l + ".json")) for l in ("fr_fr", "en_us", "ru_ru")}
    sons = set()
    for k, noms in COUTEAUX.items():
        cid = "cs2_" + k
        aff = lire(src / "display" / "melee" / (k + ".json"))
        shutil.copyfile(src / "geo_models" / "melee" / (k + "_geo.json"), ASSETS / "geo_models" / "melee" / (cid + "_geo.json"))
        shutil.copyfile(src / "textures" / "melee" / (k + "_uv.png"), ASSETS / "textures" / "melee" / (cid + "_uv.png"))
        shutil.copyfile(src / "textures" / "melee" / "slot" / (k + ".png"), ASSETS / "textures" / "melee" / "slot" / (cid + ".png"))
        anim = (src / "animations" / "melee" / (k + ".animation.json")).read_text(encoding="utf-8-sig")
        sons.update(re.findall(r"cs2_wt:melee/knife/([\w.]+)", anim))
        (ASSETS / "animations" / "melee" / (cid + ".animation.json")).write_text(renommer(anim, k), encoding="utf-8")
        machine = "heralisz_melee:default_melee_state_machine"
        if aff.get("state_machine") == "cs2_wt:skeleton_melee_state_machine":
            lua = (src / "scripts" / "skeleton_melee_state_machine.lua").read_text(encoding="utf-8-sig").replace("\r", "")
            # même compteur de coups que notre machine par défaut (alternance gauche / gauche)
            lua = lua.replace('        local counter = this.main_track_states.attack_index\n'
                              '        local animationName = "melee_" .. tostring(counter + 1)\n'
                              '        this.main_track_states.attack_index = (counter + 1) % 2\n',
                              '        local counter = context:getActionCount("attack_left")\n'
                              '        local animationName = "melee_" .. tostring(counter % 2 + 1)\n')
            (ASSETS / "scripts" / "cs2_skeleton_state_machine.lua").write_text(lua, encoding="utf-8")
            machine = "heralisz_melee:cs2_skeleton_state_machine"
        nouvel = OrderedDict([
            ("model", "heralisz_melee:melee/%s_geo" % cid),
            ("animation", "heralisz_melee:melee/%s" % cid),
            ("state_machine", machine),
            ("texture", "heralisz_melee:melee/%s_uv" % cid),
            ("slot_texture", "heralisz_melee:melee/slot/%s" % cid),
            ("sounds", modele_aff["sounds"]),
            ("transforms", aff["transforms"]),
            ("third_person_animation", modele_aff["third_person_animation"]),
        ])
        ecrire(ASSETS / "display" / "melee" / (cid + ".json"), nouvel)
        fiche = lire(data_src / (k + ".json"))
        fiche["name"] = "item.heralisz_melee.%s" % cid
        fiche = OrderedDict([("name", fiche["name"]), ("tooltip", "item.heralisz_melee.%s.desc" % cid)]
                            + [(a, b) for a, b in fiche.items() if a not in ("name", "tooltip")])
        fiche["type"] = "heralisz_tactical:normal"
        fiche["base_item"] = "heralisz_tactical:melee"
        for cote in ("attack_left", "attack_right"):
            fiche["data"]["attack"][cote]["cooldown"] = CADENCE
        ecrire(DATA / (cid + ".json"), fiche)
        for i, l in enumerate(("fr_fr", "en_us", "ru_ru")):
            langues[l]["item.heralisz_melee.%s" % cid] = noms[i]
            langues[l]["item.heralisz_melee.%s.desc" % cid] = DESC[i]
    (ASSETS / "tacz_sounds" / "melee" / "cs2").mkdir(parents=True, exist_ok=True)
    for s in sorted(sons):
        shutil.copyfile(src / "tacz_sounds" / "melee" / "knife" / (s + ".ogg"), ASSETS / "tacz_sounds" / "melee" / "cs2" / (s + ".ogg"))
    for l, d in langues.items():
        ecrire(ASSETS / "lang" / (l + ".json"), d)
    print("3 couteaux, %d sons" % len(sons))


if __name__ == "__main__":
    main()
