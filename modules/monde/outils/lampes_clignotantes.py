#!/usr/bin/env python3
"""
Lampes rouges qui clignotent (heraliz_decors : lampe_rouge_clignotante, lampe_rouge_fixe, gyrophare) : le clignotement
devient une texture animée, la lumière du bloc ne change plus (audit du 19/11, point 2).

Avant : le serveur basculait l'état « allume » de chaque lampe (5 s allumée, 1 s éteinte, toutes en même temps) ; à
chaque bascule, chaque joueur proche refaisait l'éclairage autour et reconstruisait les morceaux de carte touchés (jusqu'à
27 par lampe), conteneurs HD compris : un à-coup toutes les 5 s, puis 1 s après, dans les bunkers et les labos.
Maintenant : la lumière du bloc reste à 15 ; le verre de la lampe passe de l'image allumée à l'éteinte par sa texture
animée (mêmes dessins, même rythme : 100 ticks allumée, 20 éteinte, comme Clignotement.CYCLE et NOIR) ; AlarmeRouge refait
la seconde noire sur la teinte de la lumière, près du joueur, comme avant.

Ce que ce script écrit (jamais à la main), à partir des textures et modèles d'origine (jamais modifiés) :
  - textures/block/lampe_rouge_clignote, neon_rouge_clignote, gyrophare_clignote (.png et .mcmeta) : l'image allumée
    puis l'image éteinte (gyrophare : ses 16 images qui tournent, puis l'éteinte), images de 32 x 32 en colonne ;
  - models/block/lampe_rouge_clignotante_animee, lampe_rouge_fixe_animee, gyrophare_anime : le modèle allumé d'origine
    (parent), verre remplacé par la texture animée ;
  - blockstates/<bloc>.json : les deux états (allume=true et false) vers ce modèle, mêmes rotations.
Les modèles d'objet (inventaire) gardent le modèle allumé : l'icône ne clignote pas.

    python modules/monde/outils/lampes_clignotantes.py              écrit les fichiers
    python modules/monde/outils/lampes_clignotantes.py --verifier   contrôle seulement (code 1 si un fichier diffère)
"""
import argparse
import io
import json
import sys
from pathlib import Path

from PIL import Image

RACINE = Path(__file__).resolve().parents[1]
A = RACINE / "src" / "client" / "resources" / "assets" / "heraliz_decors"
CYCLE, NOIR = 120, 20                    # Clignotement.CYCLE, Clignotement.NOIR
# bloc -> (modèle allumé, variable du verre, texture allumée, texture éteinte, images de l'animation allumée,
#          texture animée écrite, modèle écrit)
LAMPES = {
    "lampe_rouge_clignotante": ("lampe_rouge_clignotante", "v", "lampe_rouge_allumee", "lampe_rouge_eteinte", 1,
                                "lampe_rouge_clignote", "lampe_rouge_clignotante_animee"),
    "lampe_rouge_fixe": ("lampe_rouge_fixe", "n", "neon_rouge", "neon_rouge_eteint", 1,
                         "neon_rouge_clignote", "lampe_rouge_fixe_animee"),
    "gyrophare": ("gyrophare", "v", "gyrophare_anim", "gyrophare_eteint", 16, "gyrophare_clignote", "gyrophare_anime"),
}


def png(im):
    b = io.BytesIO()
    im.save(b, "PNG", optimize=True)
    return b.getvalue()


def texte_json(d):
    return json.dumps(d, indent=1, ensure_ascii=False)


def fichiers():
    """{chemin relatif à assets/heraliz_decors : octets}."""
    out = {}
    for bloc, (modele, var, allumee, eteinte, n, nom, anime) in LAMPES.items():
        img_a = Image.open(A / "textures/block" / (allumee + ".png")).convert("RGBA")
        img_e = Image.open(A / "textures/block" / (eteinte + ".png")).convert("RGBA")
        c = img_e.size[0]
        assert img_e.size == (c, c) and img_a.size == (c, n * c), (bloc, img_a.size, img_e.size)
        # images les unes sous les autres (comme gyrophare_anim) : les n images allumées, puis l'éteinte
        planche = Image.new("RGBA", (c, (n + 1) * c))
        planche.paste(img_a, (0, 0))
        planche.paste(img_e, (0, n * c))
        out["textures/block/%s.png" % nom] = png(planche)
        if n == 1:
            anim = {"frames": [{"index": 0, "time": CYCLE - NOIR}, {"index": 1, "time": NOIR}]}
        else:
            # une image par tick, comme gyrophare_anim, pendant les 100 ticks allumés
            anim = {"frametime": 1, "frames": [k % n for k in range(CYCLE - NOIR)] + [{"index": n, "time": NOIR}]}
        out["textures/block/%s.png.mcmeta" % nom] = (json.dumps({"animation": anim}, ensure_ascii=False) + "\n").encode()
        out["models/block/%s.json" % anime] = (texte_json({
            "parent": "heraliz_decors:block/" + modele,
            "textures": {var: "heraliz_decors:block/" + nom},
        }) + "\n").encode()
        bs = json.loads((A / "blockstates" / (bloc + ".json")).read_text(encoding="utf-8"))
        variantes = {}
        for cle, v in bs["variants"].items():
            v = dict(v)
            if v["model"] in ("heraliz_decors:block/" + modele, "heraliz_decors:block/" + anime) or \
                    v["model"].startswith("heraliz_decors:block/%s_etein" % modele):
                v["model"] = "heraliz_decors:block/" + anime
            variantes[cle] = v
        out["blockstates/%s.json" % bloc] = texte_json({"variants": variantes}).encode()
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--verifier", action="store_true")
    a = ap.parse_args()
    diff = []
    for rel, octets in fichiers().items():
        p = A / rel
        avant = p.read_bytes() if p.exists() else None
        if rel.endswith(".png") and avant is not None:
            a1 = Image.open(io.BytesIO(avant)).convert("RGBA")
            a2 = Image.open(io.BytesIO(octets)).convert("RGBA")
            meme = a1.size == a2.size and a1.tobytes() == a2.tobytes()
        else:
            meme = avant == octets
        if meme:
            continue
        diff.append(rel)
        if not a.verifier:
            p.write_bytes(octets)
            print("écrit :", rel)
    if a.verifier and diff:
        for rel in diff:
            print("[lampes] pas à jour :", rel)
        print("[lampes] relancer : python modules/monde/outils/lampes_clignotantes.py")
        sys.exit(1)
    print("[lampes] %d fichier(s) %s" % (len(diff), "à écrire" if a.verifier else "écrit(s)") if diff else "[lampes] à jour")


if __name__ == "__main__":
    main()
