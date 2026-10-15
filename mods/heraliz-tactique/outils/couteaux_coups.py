"""Animations 1re personne des deux coups des couteaux TaCZ (choix du propriétaire, 14/10 puis 15/10).

Les couteaux des gunpacks n'ont qu'un coup, « melee_push ». Comme les couteaux de LR Tactical (Dague tactique, karambit
de LR : « melee_1 » / « melee_2 » en alternance pour le clic gauche, « melee_3 » pour le clic droit), ce script écrit pour
chacun des 5 modèles (ct_knife, t_knife, karambit, m9, butterfly_knife ; les skins de heralisz_skins s'en servent) trois
animations bien différentes :

- « heraliz_tranche_1 » et « heraliz_tranche_2 » (coup rapide, clic gauche, en alternance) : tranche latérale vive,
  de gauche à droite puis de droite à gauche, sans préparation (lame au milieu de l'écran vers 0,11 s), 0,45 s ;
- « melee_stock » (coup puissant, clic droit) : estoc. Préparation visible (bras armé, levé et ramené vers soi pendant
  0,22 s, bruit de lame qu'on arme), puis poussée vers l'avant (lame au fond vers 0,27 s), tenue appuyée, retour lent ;
  0,85 s (cadence du coup puissant : 0,9 s).

Chaque animation part de la pose de repos du modèle (« static_idle » au temps 0, figée pendant le coup) et déplace l'os
du bras (BRAS) ; les gestes (COUPS) sont écrits dans le repère vu à l'écran : rotation [tangage (+ = pointe qui monte),
lacet (+ = vers la gauche), roulis (+ = sens inverse des aiguilles d'une montre)], position en px [x (+ = à droite),
y (+ = en haut), z (+ = vers soi)]. Le fichier Bedrock inverse x et y des rotations et x des positions (convention de
Blockbench) : le script s'en charge. Bruits du joueur : ceux des couteaux de LR (tacz_sounds de heralisz_tactical).

Le mod Tactique les donne à TaCZ chez le joueur (client/couteaux/AnimCouteaux) et les joue lui-même sur la piste
principale (transition courte) ; « melee_stock » sert aussi de secours par le signal « bayonet_stock ». La secousse de
caméra (client/couteaux/SecousseCouteau) et le délai du coup puissant côté serveur (CoupsCouteau.DELAI_COUP_LOURD)
sont réglés sur les temps ci-dessous : à changer ensemble. Les gunpacks ne sont pas modifiés.

Écrit src/client/resources/assets/heraliz_tactique/couteaux/<modèle>.animation.json.
Usage : python mods/heraliz-tactique/outils/couteaux_coups.py [--apercu DOSSIER]
        (--apercu : planches d'images clés des coups, vue 1re personne simplifiée ; numpy et Pillow)
"""
import argparse
import json
import math
import os

ICI = os.path.dirname(os.path.abspath(__file__))
PROJET = os.path.normpath(os.path.join(ICI, ".."))
DEPOT = os.path.normpath(os.path.join(PROJET, "..", ".."))
PACK = os.path.join(DEPOT, "serveur", "tacz", "arkaniaz_pack_4_converted", "assets", "arkaniaz")
SOURCE = os.path.join(PACK, "animations")
SORTIE = os.path.join(PROJET, "src", "client", "resources", "assets", "heraliz_tactique", "couteaux")

# modèle -> (os qui porte le bras et la lame, modèle 3D pour l'aperçu)
MODELES = {
    "ct_knife": ("right_elbow", "ct_knife_geo"),
    "t_knife": ("right_elbow", "t_knife_geo"),
    "m9": ("right_elbow", "m9_rusted_geo"),
    "butterfly_knife": ("right_elbow", "butterfly_knife_asiimov_geo"),
    "karambit": ("hand_and_knife", "karambit_fade_geo"),
}

# amplitude du geste par modèle (rotation, position) : le karambit, tenu lame en bas, passerait derrière le bras
AMPLITUDE = {"karambit": (0.5, 0.8)}

SON_LR = "heralisz_tactical:melee/knife/"
# nom -> durée (s), clés (temps, rotation vue [tangage, lacet, roulis], position vue [x, y, z]), bruits (temps -> son)
COUPS = {
    # coup rapide 1 : tranche de gauche à droite (lame lancée de la gauche, traverse l'écran, sort à droite)
    "heraliz_tranche_1": (0.45, [
        (0.00, (0, 0, 0), (0, 0, 0)),
        (0.05, (8, 24, 32), (-6, 3, -3)),
        (0.11, (-2, 0, -5), (0, 0.5, -4)),
        (0.18, (-10, -38, -40), (6, -3, -2)),
        (0.24, (-8, -42, -44), (7, -4, -1)),
        (0.45, (0, 0, 0), (0, 0, 0)),
    ], {"0.0": SON_LR + "melee_attack_knife_plr_05"}),
    # coup rapide 2 : tranche de droite à gauche (le retour du premier)
    "heraliz_tranche_2": (0.45, [
        (0.00, (0, 0, 0), (0, 0, 0)),
        (0.05, (10, -22, -30), (5, 4, -1)),
        (0.11, (0, 5, 10), (-1, 1, -4)),
        (0.18, (-12, 35, 40), (-7, -2, -2)),
        (0.24, (-10, 38, 42), (-8, -3, -1)),
        (0.45, (0, 0, 0), (0, 0, 0)),
    ], {"0.0": SON_LR + "melee_attack_knife_plr_04"}),
    # coup puissant : estoc. Préparation (armé, levé, ramené vers soi), poussée au fond, tenue, retour lent
    "melee_stock": (0.85, [
        (0.00, (0, 0, 0), (0, 0, 0)),
        (0.12, (14, -10, 12), (3, 3, 1.5)),
        (0.22, (20, -14, 16), (4, 4, 2)),
        (0.27, (-22, 14, -4), (-4, 2.5, -10)),
        (0.32, (-24, 15, -6), (-4.5, 2.5, -11)),
        (0.45, (-20, 13, -6), (-4, 2, -9)),
        (0.85, (0, 0, 0), (0, 0, 0)),
    ], {"0.0": SON_LR + "melee_knife_charge_01", "0.12": SON_LR + "melee_attack_knife_plr_02"}),
}
# temps des images de l'aperçu, par coup
APERCU = {
    "heraliz_tranche_1": [0.0, 0.05, 0.08, 0.11, 0.15, 0.2, 0.3],
    "heraliz_tranche_2": [0.0, 0.05, 0.08, 0.11, 0.15, 0.2, 0.3],
    "melee_stock": [0.0, 0.12, 0.22, 0.25, 0.3, 0.45, 0.65],
}


def valeur(k):
    if isinstance(k, dict):
        return k.get("post", k.get("pre"))
    return k


def vec(v):
    if isinstance(v, (int, float)):
        return [float(v)] * 3
    return [float(x) for x in v]


def echantillon(canal, t):
    """Valeur d'un canal Bedrock (valeur fixe, ou clés « temps » -> valeur) au temps t, en ligne droite entre deux clés."""
    if not isinstance(canal, dict):
        return vec(canal)
    cles = sorted((float(x), vec(valeur(v))) for x, v in canal.items())
    if t <= cles[0][0]:
        return list(cles[0][1])
    for (t0, v0), (t1, v1) in zip(cles, cles[1:]):
        if t0 <= t <= t1:
            f = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return [a + (b - a) * f for a, b in zip(v0, v1)]
    return list(cles[-1][1])


def repos(src):
    """Pose de repos du modèle : chaque canal de « static_idle » au temps 0."""
    return {nom: {c: echantillon(v, 0.0) for c, v in canaux.items()}
            for nom, canaux in src["animations"]["static_idle"].get("bones", {}).items()}


def r4(v):
    return [round(x, 4) for x in v]


def clip(pose_repos, os_bras, duree, cles, sons, amplitude=(1.0, 1.0)):
    """Animation Bedrock : pose de repos figée + geste du bras (repère vu converti en repère Bedrock)."""
    ar, ap = amplitude
    os_ = {nom: {c: r4(v) for c, v in canaux.items()} for nom, canaux in pose_repos.items()}
    base = pose_repos.get(os_bras, {})
    r0 = base.get("rotation", [0.0, 0.0, 0.0])
    p0 = base.get("position", [0.0, 0.0, 0.0])
    rot, pos = {}, {}
    for t, r, p in cles:
        tx, ty, tz = [v * ar for v in r]
        px, py, pz = [v * ap for v in p]
        cle = str(round(t, 4))
        rot[cle] = {"post": r4([r0[0] - tx, r0[1] - ty, r0[2] + tz]), "lerp_mode": "catmullrom"}
        pos[cle] = {"post": r4([p0[0] - px, p0[1] + py, p0[2] + pz]), "lerp_mode": "catmullrom"}
    bras = os_.setdefault(os_bras, {})
    bras["rotation"] = rot
    bras["position"] = pos
    return {"loop": False, "animation_length": duree, "bones": os_,
            "sound_effects": {t: {"effect": s} for t, s in sons.items()}}


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--apercu", metavar="DOSSIER", help="planches d'images clés des coups (numpy, Pillow)")
    args = ap.parse_args()
    os.makedirs(SORTIE, exist_ok=True)
    poses = {}
    for modele, (os_bras, _) in MODELES.items():
        with open(os.path.join(SOURCE, modele + ".animation.json"), encoding="utf-8") as f:
            src = json.load(f)
        pose_repos = repos(src)
        poses[modele] = pose_repos
        anims = {nom: clip(pose_repos, os_bras, *coup, AMPLITUDE.get(modele, (1.0, 1.0))) for nom, coup in COUPS.items()}
        sortie = {"format_version": src.get("format_version", "1.8.0"), "animations": anims}
        chemin = os.path.join(SORTIE, modele + ".animation.json")
        with open(chemin, "w", encoding="utf-8", newline="\n") as f:
            json.dump(sortie, f, ensure_ascii=False, indent=1)
            f.write("\n")
        print("écrit", os.path.relpath(chemin, DEPOT).replace(os.sep, "/"), ", ".join(anims))
    if args.apercu:
        apercu(args.apercu, poses)


# ---------------------------------------------------------------------------------------------------------------------
# Aperçu : rendu simplifié (cubes en aplat, bras = repère « righthand_pos » du modèle), caméra à « idle_view »


def apercu(dossier, poses):
    import numpy as np
    from PIL import Image, ImageDraw

    def rot(deg):
        x, y, z = [math.radians(v) for v in deg]
        cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
        rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
        ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
        rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
        return rz @ ry @ rx

    def mat(r=None, t=None, s=None):
        m = np.eye(4)
        if r is not None:
            m[:3, :3] = r
        if s is not None:
            m[:3, :3] = m[:3, :3] @ np.diag(s)
        if t is not None:
            m[:3, 3] = t
        return m

    def vu(v):  # repère Bedrock -> repère vu (x inversé)
        return np.array([-v[0], v[1], v[2]], float)

    def rendu(geo, pose, titre, taille=(240, 135), champ=70.0):
        os_ = {b["name"]: b for b in geo["bones"]}
        monde = {}

        def m(nom):
            if nom in monde:
                return monde[nom]
            b = os_[nom]
            parent = m(b["parent"]) if b.get("parent") in os_ else np.eye(4)
            pv = vu(b.get("pivot", [0, 0, 0]))
            r0 = b.get("rotation") or [0, 0, 0]
            a = pose.get(nom, {})
            ar = a.get("rotation", [0, 0, 0])
            ap_ = a.get("position", [0, 0, 0])
            ech = a.get("scale", [1, 1, 1])
            rr = [-(r0[0] + ar[0]), -(r0[1] + ar[1]), r0[2] + ar[2]]
            loc = mat(t=vu(ap_)) @ mat(t=pv) @ mat(r=rot(rr), s=ech) @ mat(t=-pv)
            monde[nom] = parent @ loc
            return monde[nom]

        cam = vu((os_.get("idle_view") or {"pivot": [0, 12, 14]})["pivot"])
        faces = []
        for nom, b in os_.items():
            if not b.get("cubes") or nom == "lefthand_pos":
                continue
            mb = m(nom)
            couleur = (214, 168, 132) if nom.endswith("hand_pos") else (150, 160, 172)
            for c in b["cubes"]:
                o, s = np.array(c["origin"], float), np.array(c["size"], float)
                lo = np.array([-(o[0] + s[0]), o[1], o[2]])
                hi = np.array([-o[0], o[1] + s[1], o[2] + s[2]])
                mc = np.eye(4)
                if c.get("rotation"):
                    cr, cp = c["rotation"], vu(c.get("pivot", [0, 0, 0]))
                    mc = mat(t=cp) @ mat(r=rot([-cr[0], -cr[1], cr[2]])) @ mat(t=-cp)
                coins = np.array([[x, y, z, 1] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
                pts = (mb @ mc @ coins.T).T[:, :3]
                for f in ((0, 1, 3, 2), (4, 5, 7, 6), (0, 1, 5, 4), (2, 3, 7, 6), (0, 2, 6, 4), (1, 3, 7, 5)):
                    faces.append((pts[list(f)], couleur))
        larg, haut = taille
        focale = (haut / 2) / math.tan(math.radians(champ / 2))
        img = Image.new("RGB", taille, (34, 38, 46))
        dessin = ImageDraw.Draw(img)
        lumiere = np.array([0.3, 0.8, 0.5]) / np.linalg.norm([0.3, 0.8, 0.5])
        polys = []
        for pts, couleur in faces:
            rel = pts - cam
            z = -rel[:, 2]
            if (z < 0.5).any():
                continue
            n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
            nn = np.linalg.norm(n)
            ombre = 0.55 + 0.45 * abs(float(n @ lumiere / nn)) if nn > 1e-9 else 0.7
            poly = list(zip(larg / 2 + rel[:, 0] / z * focale, haut / 2 - rel[:, 1] / z * focale))
            polys.append((float(z.mean()), poly, tuple(int(k * ombre) for k in couleur)))
        for _, poly, couleur in sorted(polys, key=lambda q: -q[0]):
            dessin.polygon(poly, fill=couleur)
        dessin.line([(larg / 2 - 4, haut / 2), (larg / 2 + 4, haut / 2)], fill=(255, 255, 255))
        dessin.line([(larg / 2, haut / 2 - 4), (larg / 2, haut / 2 + 4)], fill=(255, 255, 255))
        dessin.text((4, 3), titre, fill=(255, 255, 255))
        return img

    os.makedirs(dossier, exist_ok=True)
    for nom_coup, (duree, cles, sons) in COUPS.items():
        lignes = []
        for modele, (os_bras, nom_geo) in MODELES.items():
            with open(os.path.join(PACK, "geo_models", "gun", nom_geo + ".json"), encoding="utf-8") as f:
                geo = json.load(f)["minecraft:geometry"][0]
            anim = clip(poses[modele], os_bras, duree, cles, sons, AMPLITUDE.get(modele, (1.0, 1.0)))
            images = []
            for t in APERCU[nom_coup]:
                pose = {nom: {c: echantillon(v, t) for c, v in canaux.items()} for nom, canaux in anim["bones"].items()}
                images.append(rendu(geo, pose, "%s  %.2f s" % (modele, t)))
            lignes.append(images)
        planche = Image.new("RGB", (240 * len(lignes[0]), 135 * len(lignes)))
        for j, ligne in enumerate(lignes):
            for i, im in enumerate(ligne):
                planche.paste(im, (240 * i, 135 * j))
        chemin = os.path.join(dossier, nom_coup + ".png")
        planche.save(chemin)
        print("aperçu", chemin)


if __name__ == "__main__":
    main()
