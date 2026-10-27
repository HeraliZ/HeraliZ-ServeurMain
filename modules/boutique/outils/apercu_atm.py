"""Rendus du distributeur de capsules (« ATM ») hors du jeu, à valider par le propriétaire avant de le brancher (règle 6).

    python modules/boutique/outils/apercu_atm.py [DOSSIER]      (par défaut : maquettes/atm_capsules/borne)

Le modèle (outils/atm/AtmDesign.java) est dessiné avec le vrai moteur 3D du casino (Java pur, compilé ici avec JOML) et
ses vraies textures (outils/textures_atm.py, dans une copie de l'atlas : rien n'est écrit dans le mod) par
outils/ApercuMachine.java, un petit moteur logiciel. Ce n'est pas la lumière du jeu : il sert à juger formes, couleurs et
proportions. Silhouette grise : un joueur (1,8 bloc), pour l'échelle. Besoin : un JDK 17 (javac), comme la fabrication.
"""
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ICI = Path(__file__).resolve().parent
RACINE = ICI.parents[2]
sys.path.insert(0, str(RACINE / "outils"))
sys.path.insert(0, str(ICI))
import formes_caisses as fc  # noqa: E402
import textures_atm  # noqa: E402

SRC = ICI.parent / "src"
RENDU = SRC / "client/java/fr/heraliz/casino/client/render"
ASSETS = SRC / "client/resources/assets/heralizcasino"
CLASSE = "fr.heraliz.casino.client.render.scene.machines.AtmDesign"

# nom, temps (-1 = repos ; dépôt vers 10-40 ; retrait vers 90-130), yaw, pitch, distance, point visé (x, y, z), taille,
# silhouette, décor
VUES = [
    ("1_trois_quarts", -1, 34, 12, 70, (2, 16, 8), (1400, 1050), 1, 1),
    ("2_face", -1, 0, 6, 58, (7, 16.5, 8), (1100, 1100), 0, 1),
    ("3_profil_gauche", -1, -42, 10, 56, (7, 16, 8), (1100, 1100), 0, 1),
    ("4_ecran_clavier", -1, 10, 16, 24, (7, 19.5, 12), (1400, 1050), 0, 1),
    ("5_depot", 22, 30, 22, 24, (9.5, 17.5, 13), (1400, 1050), 0, 1),
    ("6_retrait", 140, 18, 20, 22, (7, 10, 13.5), (1400, 1050), 0, 1),
]


def main():
    sortie = Path(sys.argv[1]) if len(sys.argv) > 1 else RACINE / "maquettes" / "atm_capsules" / "borne"
    sortie.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ)
    env.pop("JAVA_TOOL_OPTIONS", None)
    jar = fc.joml()
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        # ressources : atlas du guichet avec les tuiles de l'ATM, feuille /recup, lueurs
        res = tmp / "res"
        (res / "textures/machine").mkdir(parents=True)
        atlas, glow, _ = textures_atm.construire()
        atlas.save(res / "textures/machine/caisse.png")
        glow.save(res / "textures/machine/caisse_glow.png")
        for n in ("recup.png", "recup_glow.png"):
            shutil.copy(ASSETS / "textures/machine" / n, res / "textures/machine" / n)
        shutil.copytree(ASSETS / "textures/fx", res / "textures/fx")
        # le moteur du casino (Java pur) et le modèle
        sources = [str(p) for p in (RENDU / "mesh").glob("*.java")]
        sources += [str(p) for p in (RENDU / "scene").glob("*.java") if p.name != "Designs.java"]
        sources += [str(RENDU / "scene/machines/BunkerKit.java"), str(SRC / "commun/java/fr/heraliz/casino/registry/Snd.java")]
        sources += [str(p) for p in (ICI / "atm").glob("*.java")] + [str(ICI / "ApercuMachine.java")]
        classes = tmp / "classes"
        subprocess.run([fc.outil("javac"), "-nowarn", "-encoding", "UTF-8", "-d", str(classes), "-cp", jar] + sources, check=True, env=env)
        cp = os.pathsep.join([str(classes), jar])
        for nom, t, yaw, pitch, dist, vise, taille, sil, decor in VUES:
            f = sortie / (nom + ".png")
            subprocess.run([fc.outil("java"), "-cp", cp, "ApercuMachine", str(res), str(f), CLASSE, str(t), str(yaw), str(pitch), str(dist),
                            str(vise[0]), str(vise[1]), str(vise[2]), str(taille[0]), str(taille[1]), str(sil), str(decor)], check=True, env=env)
            print("écrit", f)
    planche(sortie)


LEGENDES = {
    "1_trois_quarts": "Vue d'ensemble, à côté d'un joueur (1,8 bloc) : 1 bloc de large, 2 de haut",
    "4_ecran_clavier": "Écran (solde du joueur), clavier, dépôt (vert), retrait (orange)",
    "5_depot": "Dépôt : les capsules tombent dans l'entonnoir",
    "6_retrait": "Retrait : les capsules sortent dans le bac",
}


def planche(sortie):
    """Les quatre vues principales sur une image, avec une légende sous chacune."""
    from PIL import Image, ImageDraw, ImageFont
    w, h, marge, bande = 1400, 1050, 24, 70
    im = Image.new("RGB", (2 * w + 3 * marge, 2 * (h + bande) + 3 * marge), (14, 15, 18))
    d = ImageDraw.Draw(im)
    f = ImageFont.truetype(str(ASSETS / "font/dmsans_bold.ttf"), 34)
    for k, (nom, texte) in enumerate(LEGENDES.items()):
        x, y = marge + (k % 2) * (w + marge), marge + (k // 2) * (h + bande + marge)
        im.paste(Image.open(sortie / (nom + ".png")).convert("RGB").resize((w, h)), (x, y))
        d.text((x + 8, y + h + bande / 2), texte, font=f, fill=(225, 228, 232), anchor="lm")
    im = im.resize((im.width * 2 // 3, im.height * 2 // 3), Image.LANCZOS)
    im.save(sortie / "0_planche.png", optimize=True)
    print("écrit", sortie / "0_planche.png")


if __name__ == "__main__":
    main()
