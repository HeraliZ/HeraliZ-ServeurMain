#!/usr/bin/env python3
"""
Variantes de couleur du Sac de sport en bandoulière, du Sac à dos tactique et du Sac de randonnée (21/10, demande du
propriétaire : « autant de variantes que les autres sacs », rouge, bleu, vert, noir, orange, rose, jaune, blanc, neige).

Pour chaque couleur manquante, le script écrit à partir d'un sac existant du même modèle (le « modèle source ») :
  - la texture du sac porté (textures/backpack/tgr_<modele>_<couleur>.png) et l'icône (textures/item/<id>.png),
    recolorées : le tissu prend la couleur, les détails (ombres, coutures) gardent leur relief ; boucles et sangles
    neutres du sac vert restent telles quelles ; « neige » = blanc avec des taches de camouflage gris clair ;
  - les modèles (models/backpacked/<id>.json et <id>_straps.json, même .obj, autre texture), le .backpack, la donnée
    du serveur, le modèle d'objet ;
  - le bloc « variantes de couleur » de ArkaniaZModItems.java, le tag Curios back.json et les noms en français,
    anglais et russe.
Jamais à la main : on modifie ce script et on le relance. --apercu DOSSIER : planche des icônes et des textures.
"""
import argparse
import colorsys
import json
import os
import re
import sys

import numpy as np
from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(os.path.dirname(ICI), "src")
A = os.path.join(SRC, "client", "resources", "assets", "backpacked")
DATA = os.path.join(SRC, "serveur", "resources", "data", "backpacked", "backpacked")
ITEMS = os.path.join(SRC, "commun", "java", "com", "mrcrayfish", "backpacked", "core", "ArkaniaZModItems.java")
CURIOS = os.path.join(SRC, "serveur", "resources", "data", "curios", "tags", "items", "back.json")
LANG = os.path.join(A, "lang")

# couleur : (RVB du tissu, fr, en, ru masculin, ru féminin)
COULEURS = {
    "red": ((142, 46, 44), "Rouge", "Red", "Красный", "Красная"),
    "blue": ((54, 86, 132), "Bleu", "Blue", "Синий", "Синяя"),
    "green": ((92, 102, 60), "Vert", "Green", "Зелёный", "Зелёная"),
    "black": ((36, 36, 38), "Noir", "Black", "Чёрный", "Чёрная"),
    "orange": ((176, 94, 38), "Orange", "Orange", "Оранжевый", "Оранжевая"),
    "pink": ((226, 148, 186), "Rose", "Pink", "Розовый", "Розовая"),
    "yellow": ((196, 164, 58), "Jaune", "Yellow", "Жёлтый", "Жёлтая"),
    "white": ((236, 236, 232), "Blanc", "White", "Белый", "Белая"),
    "snow": ((240, 242, 244), "Neige", "Snow", "Снежный", "Снежная"),
}

# modèle : id de l'objet pour une couleur, sac source, texture du sac porté, taille (colonnes, lignes), noms
MODELES = {
    "sports_bag": {
        "id": "backpack_sports_bag_{c}", "source": "backpack_sports_bag", "texture": "tgr_sports_bag",
        "taille": (4, 4), "neutre": False,
        "fr": "Sac de Sport en Bandoulière {C}", "en": "{C} Shoulder Sports Bag", "ru": "{Cf} спортивная сумка через плечо",
        # le sac d'origine (backpack_sports_bag) est le noir
        "existants": {"black": "backpack_sports_bag"},
    },
    "tactical": {
        "id": "backpack_tactical_{c}", "source": "backpack_tactical_green", "texture": "tgr_tactical",
        "taille": (5, 4), "neutre": True,
        "fr": "Sac à dos Tactique {C}", "en": "{C} Tactical Backpack", "ru": "{Cm} тактический рюкзак",
        "existants": {"black": "backpack_tactical_black", "green": "backpack_tactical_green"},
    },
    "hiking": {
        "id": "backpack_hiking_{c}", "source": "backpack_hiking_green", "texture": "tgr_hiking",
        "taille": (5, 5), "neutre": True,
        "fr": "Sac de Randonnée {C}", "en": "{C} Hiking Backpack", "ru": "{Cm} походный рюкзак",
        "existants": {"black": "backpack_hiking_black", "green": "backpack_hiking_green"},
    },
}

DEBUT = "   // >>> HeralisZ : variantes de couleur (outils/sacs_couleurs.py)\n"
FIN = "   // <<< HeralisZ : variantes de couleur\n"


def variantes():
    """(modele, couleur, id) de chaque variante à écrire."""
    for m, d in MODELES.items():
        for c in COULEURS:
            if c not in d["existants"]:
                yield m, c, d["id"].format(c=c)


# ---------------------------------------------------------------------------------------------- recoloration
def luminance(rgb):
    return rgb[..., 0] * 0.299 + rgb[..., 1] * 0.587 + rgb[..., 2] * 0.114


def taches_neige(h, w):
    """Taches de camouflage (0..1) pour la neige : bruit à basse fréquence, le même à chaque fois."""
    rnd = np.random.default_rng(7)
    acc = np.zeros((h, w))
    for k, poids in ((max(2, w // 16), 1.0), (max(2, w // 8), 0.5)):
        g = rnd.random((k + 1, k + 1))
        petit = Image.fromarray((g * 255).astype(np.uint8)).resize((w, h), Image.BILINEAR)
        acc += np.asarray(petit, float) / 255 * poids
    acc /= 1.5
    return (acc > 0.58).astype(float)


def recolorer(chemin, couleur, neutre, sortie):
    im = Image.open(chemin).convert("RGBA")
    a = np.asarray(im).astype(float)
    rgb, alpha = a[..., :3] / 255, a[..., 3]
    vis = alpha > 0
    hsv = np.zeros_like(rgb)
    flat = rgb.reshape(-1, 3)
    hsv.reshape(-1, 3)[:] = [colorsys.rgb_to_hsv(*p) for p in flat]
    lum = luminance(rgb)
    if neutre:
        # tissu = pixels colorés (vert olive) ; les gris (boucles, sangles, métal) restent tels quels
        tissu = vis & (hsv[..., 1] > 0.12)
    else:
        tissu = vis.copy()
    if not tissu.any():
        raise SystemExit(f"aucun pixel de tissu dans {chemin}")
    ref = np.median(lum[tissu])
    f = lum / max(ref, 1e-3)
    f = 1 + (f - 1) * 0.85  # garde le relief sans brûler les clairs
    cible = np.array(COULEURS[couleur][0], float) / 255
    out = np.clip(cible[None, None, :] * f[..., None], 0, 1)
    if couleur == "snow":
        t = taches_neige(*lum.shape)
        gris = np.clip(np.array([0.70, 0.72, 0.75])[None, None, :] * f[..., None], 0, 1)
        out = out * (1 - t[..., None]) + gris * t[..., None]
    res = rgb.copy()
    res[tissu] = out[tissu]
    img = np.dstack([np.round(res * 255), alpha]).astype(np.uint8)
    Image.fromarray(img, "RGBA").save(sortie, optimize=True)


# ---------------------------------------------------------------------------------------------- fichiers
def ecrire_texte(chemin, texte):
    with open(chemin, "w", encoding="utf-8", newline="\n") as f:
        f.write(texte)


def copier_remplacer(src, dst, avant, apres):
    s = open(src, encoding="utf-8").read()
    if avant not in s:
        raise SystemExit(f"{avant} absent de {src}")
    ecrire_texte(dst, s.replace(avant, apres))


def fichiers_variante(m, c, ident):
    d = MODELES[m]
    src = d["source"]
    tex_src = d["texture"] + ("" if m == "sports_bag" else "_green")
    tex = f"{d['texture']}_{c}"
    recolorer(os.path.join(A, "textures", "backpack", tex_src + ".png"), c, d["neutre"],
              os.path.join(A, "textures", "backpack", tex + ".png"))
    recolorer(os.path.join(A, "textures", "item", src + ".png"), c, d["neutre"],
              os.path.join(A, "textures", "item", ident + ".png"))
    for suffixe in ("", "_straps"):
        copier_remplacer(os.path.join(A, "models", "backpacked", src + suffixe + ".json"),
                         os.path.join(A, "models", "backpacked", ident + suffixe + ".json"),
                         f'"backpacked:backpack/{tex_src}"', f'"backpacked:backpack/{tex}"')
    copier_remplacer(os.path.join(A, "models", "item", src + ".json"), os.path.join(A, "models", "item", ident + ".json"),
                     f'"backpacked:item/{src}"', f'"backpacked:item/{ident}"')
    ecrire_texte(os.path.join(A, "backpacked", ident + ".backpack"),
                 open(os.path.join(A, "backpacked", src + ".backpack"), encoding="utf-8").read())
    ecrire_texte(os.path.join(DATA, ident + ".json"), open(os.path.join(DATA, src + ".json"), encoding="utf-8").read())


def ecrire_java():
    s = open(ITEMS, encoding="utf-8").read()
    bloc = [DEBUT]
    for m, c, ident in variantes():
        col, lig = MODELES[m]["taille"]
        bloc.append(
            f"   public static final RegistryEntry<Item> {ident.upper()} = RegistryEntry.item(\n"
            f"      new ResourceLocation(\"backpacked\", \"{ident}\"), () -> Services.BACKPACK.createBackpackItem("
            f"new Properties().stacksTo(1), {col}, {lig})\n"
            f"   );\n")
    bloc.append(FIN)
    bloc = "".join(bloc)
    if DEBUT in s:
        i, j = s.index(DEBUT), s.index(FIN) + len(FIN)
        s = s[:i] + bloc + s[j:]
    else:
        k = s.rindex("}")
        s = s[:k] + bloc + s[k:]
    ecrire_texte(ITEMS, s)


def ecrire_curios():
    d = json.load(open(CURIOS, encoding="utf-8"))
    for _, _, ident in variantes():
        v = "backpacked:" + ident
        if v not in d["values"]:
            d["values"].append(v)
    ecrire_texte(CURIOS, json.dumps(d, ensure_ascii=False, indent=2) + "\n")


def ecrire_langues():
    for code, cle_nom in (("fr_fr", "fr"), ("en_us", "en"), ("ru_ru", "ru")):
        p = os.path.join(LANG, code + ".json")
        d = json.load(open(p, encoding="utf-8"))
        for m, c, ident in variantes():
            _, fr, en, ru_m, ru_f = COULEURS[c]
            gabarit = MODELES[m][cle_nom]
            d["item.backpacked." + ident] = gabarit.format(C=fr if code == "fr_fr" else en, Cm=ru_m, Cf=ru_f)
        ecrire_texte(p, json.dumps(d, ensure_ascii=False, indent=2) + "\n")


def apercu(dossier):
    os.makedirs(dossier, exist_ok=True)
    for m, d in MODELES.items():
        ids = []
        for c in COULEURS:
            ids.append(d["existants"].get(c) or d["id"].format(c=c))
        W = 96
        planche = Image.new("RGBA", (W * len(ids), W), (44, 46, 52, 255))
        for i, ident in enumerate(ids):
            ic = Image.open(os.path.join(A, "textures", "item", ident + ".png")).convert("RGBA").resize((W, W), Image.NEAREST)
            planche.alpha_composite(ic, (i * W, 0))
        planche.save(os.path.join(dossier, f"icones_{m}.png"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apercu", help="dossier des planches d'aperçu")
    a = ap.parse_args()
    n = 0
    for m, c, ident in variantes():
        fichiers_variante(m, c, ident)
        n += 1
    ecrire_java()
    ecrire_curios()
    ecrire_langues()
    print(f"{n} variantes écrites")
    if a.apercu:
        apercu(a.apercu)


if __name__ == "__main__":
    sys.exit(main())
