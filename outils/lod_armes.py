#!/usr/bin/env python3
"""
Versions allégées (« LOD ») des armes TaCZ vues chez les autres joueurs : décision du propriétaire, sur images
(docs/TESTS_EN_JEU_17NOV_carte-ile.md, section 36, décision A), APPLIQUÉE le 20/11 (section 39 : 66 versions allégées,
123 fiches reliées).

En 3e personne (les armes des autres joueurs, la sienne en vue F5, une arme posée), TaCZ dessine la version allégée de
l'arme quand sa fiche (display/guns/*.json, champ « lod ») en donne une, et sinon le modèle complet. Il dessine cube par
cube, chaque image, sur le processeur (24 sommets par cube) : une arme complète, c'est 140 à 1 640 cubes (≈ 480 en
moyenne pour celles qui n'ont pas de version allégée), une version allégée d'usine 40 à 80. Beaucoup de nos armes n'en
ont pas, ou ont un « LOD » qui est le modèle complet recopié : chaque joueur qui en tient une coûte 5 à 30 fois plus.
Mesuré le 20/11 (hors du jeu, même calcul par sommet) : ≈ 0,35 ms par arme de 650 cubes et par image.

Ce script fabrique la version allégée qui manque en retirant les PETITS cubes (vis, rivets, rainures, lettrages : rien
qui se voie à quelques mètres), même texture, mêmes os (positions des accessoires, de la flamme, des mains) :
un cube reste s'il a au moins un côté de 1,5 point du modèle et une face d'au moins 0,6 point² (TaCZ : 16 points = 1 bloc
à l'échelle 1). Garde-fous : si la surface gardée tombe sous 85 % de celle du modèle, les plus grands cubes retirés
reviennent jusqu'à 85 % ; si la version allégée garde plus de 70 % des cubes (modèle fait de petits cubes, comme des
couteaux), l'arme reste sans version allégée. Les fiches qui ont déjà un vrai LOD ne changent pas.

    python outils/lod_armes.py                    liste : fiches sans vrai LOD, cubes avant / après (rien n'est écrit)
    python outils/lod_armes.py --apercu DOSSIER   planches avant / après à 3, 10, 30 et 60 blocs (rien n'est écrit
                                                  dans le dépôt ; il faut numpy et Pillow)
    python outils/lod_armes.py --installer        écrit les versions allégées (geo_models/gun/lod_hz/) et les relie aux
                                                  fiches (champ « lod », même texture) ; relancer après l'ajout ou la mise
                                                  à jour d'une arme ou d'un gunpack (les versions allégées déjà écrites
                                                  sont refaites sur le modèle du moment, celles de plus aucune fiche
                                                  retirées)
    python outils/lod_armes.py --verifier         contrôle seul (lancé par outils/verifier.py, sans dépendance) : chaque
                                                  version allégée citée existe, celles de lod_hz/ sont à jour avec leur
                                                  modèle, aucune arme qui pourrait en avoir une n'en manque ; code 1 sinon

21/11 (audit) : avant --verifier, une arme mise à jour dans un gunpack gardait pour toujours sa version allégée d'avant
(l'outil la prenait pour un « vrai LOD » et ne la refaisait plus) : os renommés ou déplacés chez les autres joueurs, sans
aucune alerte.

Réglage lié (choix du propriétaire, à côté) : GunLodRenderDistance de TaCZ (tacz-client.toml des joueurs), aujourd'hui 0 =
version allégée à toute distance en 3e personne ; N > 0 = modèle complet à moins de N blocs (plus beau de près, plus cher
au spawn où les joueurs sont serrés).
"""
import argparse
import glob
import json
import math
import re
import sys
from pathlib import Path

RACINE = Path(__file__).resolve().parents[1]
GUNPACKS = RACINE / "serveur" / "tacz"
# un cube reste s'il a un côté d'au moins COTE_MIN points et une face d'au moins FACE_MIN points²
COTE_MIN = 1.5
FACE_MIN = 0.6
# un « LOD » qui garde au moins 90 % des cubes du modèle n'en est pas un
LOD_FAUX = 0.9
# surface minimale gardée (part de la surface des cubes du modèle) ; au-delà de GAIN_MIN des cubes gardés : pas de LOD
SURFACE_MIN = 0.85
GAIN_MIN = 0.70
DOSSIER_LOD = "lod_hz"
# octets de mémoire Java par cube chargé par TaCZ 1.1.8 (cube, ses 6 faces, 24 sommets, 8 positions ; JVM 64 bits)
OCTETS_CUBE = 1328


def json_lache(texte):
    """JSON de TaCZ : commentaires // et /* */ permis (hors des chaînes), virgules finales tolérées."""
    out, i, n, chaine = [], 0, len(texte), False
    while i < n:
        c = texte[i]
        if chaine:
            out.append(c)
            if c == "\\" and i + 1 < n:
                out.append(texte[i + 1])
                i += 2
                continue
            if c == '"':
                chaine = False
            i += 1
            continue
        if c == '"':
            chaine = True
            out.append(c)
            i += 1
            continue
        if texte.startswith("//", i):
            j = texte.find("\n", i)
            i = n if j < 0 else j
            continue
        if texte.startswith("/*", i):
            j = texte.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        out.append(c)
        i += 1
    return json.loads(re.sub(r",(\s*[}\]])", r"\1", "".join(out)))


def ressource(rl, dossier, ext):
    """Fichier d'une ressource « ns:chemin » dans les gunpacks (le dernier pack, dans l'ordre des dossiers, gagne)."""
    if not rl or ":" not in rl:
        return None
    ns, p = rl.split(":", 1)
    trouves = sorted(glob.glob(str(GUNPACKS / "*" / "assets" / ns / dossier / (p + ext))))
    return Path(trouves[-1]) if trouves else None


def lire_geo(fichier):
    return json_lache(fichier.read_text(encoding="utf-8"))


def cubes_de(geo):
    return [c for b in geo["minecraft:geometry"][0].get("bones", []) for c in (b.get("cubes") or [])]


def cotes(cube):
    """Côtés réels du cube (gonflement compris), du plus petit au plus grand."""
    gonfle = float(cube.get("inflate", 0) or 0)
    return sorted(max(0.0, float(x) + 2 * gonfle) for x in cube.get("size", [0, 0, 0]))


def garde(cube):
    """Le cube reste-t-il dans la version allégée (règle des petits cubes) ?"""
    s = cotes(cube)
    return s[2] >= COTE_MIN and s[1] * s[2] >= FACE_MIN


def aire(cube):
    a, b, c = cotes(cube)
    return 2 * (a * b + b * c + a * c)


def selection(cubes):
    """Cubes gardés (même ordre) : règle des petits cubes, puis les plus grands retirés reviennent tant que la surface
    gardée est sous SURFACE_MIN de celle du modèle."""
    gardes = [garde(c) for c in cubes]
    total = sum(aire(c) for c in cubes)
    gardee = sum(aire(c) for c, g in zip(cubes, gardes) if g)
    for i in sorted((i for i, g in enumerate(gardes) if not g), key=lambda i: -aire(cubes[i])):
        if gardee >= SURFACE_MIN * total:
            break
        gardes[i] = True
        gardee += aire(cubes[i])
    return gardes


def allege(geo):
    """Copie du modèle sans les petits cubes : mêmes os (et leur ordre), même texture, même description. None si la
    version allégée garderait plus de GAIN_MIN des cubes (rien à gagner)."""
    g = json.loads(json.dumps(geo))
    gardes = iter(selection(cubes_de(g)))
    n = gardes_n = 0
    for b in g["minecraft:geometry"][0].get("bones", []):
        if b.get("cubes"):
            reste = []
            for c in b["cubes"]:
                n += 1
                if next(gardes):
                    reste.append(c)
            gardes_n += len(reste)
            b["cubes"] = reste
            if not reste:
                del b["cubes"]
    return g if n and gardes_n <= GAIN_MIN * n else None


def fiches():
    """{id de la fiche : (fichier, contenu)} ; le dernier pack (ordre des dossiers) gagne, comme pour les ressources."""
    out = {}
    for f in sorted(glob.glob(str(GUNPACKS / "*" / "assets" / "*" / "display" / "guns" / "*.json"))):
        f = Path(f)
        ns = f.parts[f.parts.index("assets") + 1]
        try:
            out[ns + ":" + f.stem] = (f, json_lache(f.read_text(encoding="utf-8")))
        except (ValueError, OSError) as e:
            print("[lod] fiche illisible %s : %s" % (f.relative_to(RACINE), e), file=sys.stderr)
    return out


def de_l_outil(rl):
    """Version allégée écrite par ce script (dossier geo_models/gun/lod_hz/) ?"""
    return isinstance(rl, str) and ("/%s/" % DOSSIER_LOD) in rl


def a_alleger(nos_lod=False):
    """Fiches sans vrai LOD : [(id, fichier, fiche, fichier du modèle, cubes du modèle, cubes du LOD actuel ou None)].
    nos_lod : aussi les fiches dont le LOD a été écrit par ce script (--installer les refait sur le modèle du moment ;
    sans ça, un modèle mis à jour gardait pour toujours la version allégée de l'ancien)."""
    res = []
    for cle, (f, d) in sorted(fiches().items()):
        modele = ressource(d.get("model"), "geo_models", ".json")
        if modele is None:
            continue
        nm = len(cubes_de(lire_geo(modele)))
        lod = d.get("lod") if isinstance(d.get("lod"), dict) else {}
        flod = ressource(lod.get("model"), "geo_models", ".json")
        nl = len(cubes_de(lire_geo(flod))) if flod else None
        if nl is not None and nl < nm * LOD_FAUX and not (nos_lod and de_l_outil(lod.get("model"))):
            continue          # vrai LOD : rien à faire
        res.append((cle, f, d, modele, nm, nl))
    return res


def id_lod(modele_rl):
    ns, p = modele_rl.split(":", 1)
    return "%s:gun/%s/%s" % (ns, DOSSIER_LOD, p.rsplit("/", 1)[-1])


def lister():
    liste = a_alleger()
    avant = apres = 0
    vus = {}
    sans_gain = 0
    for cle, f, d, modele, nm, nl in liste:
        if modele not in vus:
            leger = allege(lire_geo(modele))
            vus[modele] = len(cubes_de(leger)) if leger else None
        na = vus[modele]
        n = nl if nl is not None else nm
        avant += n
        apres += n if na is None else na
        if na is None:
            sans_gain += 1
        print("%-48s %5d cubes -> %s (%s)" % (cle, n, "%4d" % na if na is not None else "gardé (petits cubes)",
                                              "LOD = modèle complet" if nl is not None else "sans LOD"))
    print("[lod] %d fiches sans vrai LOD (%d modèles, dont %d fiches gardées telles quelles) : %d cubes -> %d (-%.0f %%)" % (
        len(liste), len(vus), sans_gain, avant, apres, 100.0 * (avant - apres) / max(1, avant)))
    print("[lod] mémoire Java des armes préchauffées pour les autres joueurs (PrechauffageArmes) : ≈ %.0f Mo de moins"
          % ((avant - apres) * OCTETS_CUBE / 1e6))
    return liste


def remplacer_lod(texte, rl_modele, rl_texture):
    """Fiche avec le champ « lod » pointé sur la version allégée, commentaires de la fiche gardés (édition du texte)."""
    bloc = '"lod": {\n    "model": "%s",\n    "texture": "%s"\n  }' % (rl_modele, rl_texture)
    m = re.search(r'"lod"\s*:\s*\{[^{}]*\}', texte)
    if m:
        return texte[:m.start()] + bloc + texte[m.end():]
    m = re.search(r'("texture"\s*:\s*"[^"]*")\s*,', texte)
    if not m:
        raise ValueError("champ texture introuvable")
    return texte[:m.end()] + "\n  " + bloc + "," + texte[m.end():]


def ecrit(leger):
    """Texte exact d'une version allégée écrite (une ligne de JSON compact)."""
    return json.dumps(leger, ensure_ascii=False, separators=(",", ":")) + "\n"


def fichiers_lod():
    """Versions allégées écrites par ce script, dans tous les gunpacks."""
    return sorted(Path(p) for p in glob.glob(str(GUNPACKS / "*" / "assets" / "*" / "geo_models" / "gun" / DOSSIER_LOD / "*.json")))


def citees():
    """Fichiers des versions allégées citées par une fiche (résolus comme TaCZ : le dernier pack gagne)."""
    out = set()
    for f, d in fiches().values():
        lod = d.get("lod")
        if isinstance(lod, dict):
            fl = ressource(lod.get("model"), "geo_models", ".json")
            if fl is not None:
                out.add(fl.resolve())
    return out


def installer():
    liste = a_alleger(nos_lod=True)
    faits = set()
    sautes = 0
    for cle, f, d, modele, nm, nl in liste:
        leger = allege(lire_geo(modele))
        if leger is None:
            sautes += 1
            lod = d.get("lod") if isinstance(d.get("lod"), dict) else {}
            if de_l_outil(lod.get("model")):
                print("[lod] %s : son modèle n'a plus rien à alléger, mais la fiche cite encore %s : retirer son champ "
                      "« lod » à la main" % (cle, lod.get("model")))
            continue
        rl = id_lod(d["model"])
        ns, p = rl.split(":", 1)
        pack = modele.parents[len(modele.relative_to(GUNPACKS).parts) - 2]
        sortie = pack / "assets" / ns / "geo_models" / (p + ".json")
        if sortie not in faits:
            sortie.parent.mkdir(parents=True, exist_ok=True)
            # octets : fin de ligne LF sur tous les systèmes (write_text écrivait CRLF sous Windows)
            sortie.write_bytes(ecrit(leger).encode("utf-8"))
            faits.add(sortie)
        texte = f.read_bytes().decode("utf-8")
        crlf = "\r\n" in texte
        nouveau = remplacer_lod(texte.replace("\r\n", "\n"), rl, d["texture"])
        f.write_bytes((nouveau.replace("\n", "\r\n") if crlf else nouveau).encode("utf-8"))
        # contrôle : la fiche se relit et pointe bien sur la version allégée
        relu = json_lache(f.read_text(encoding="utf-8"))
        assert relu["lod"]["model"] == rl and ressource(rl, "geo_models", ".json") is not None, cle
    # versions allégées que plus aucune fiche ne cite (arme retirée d'un gunpack) : retirées, elles partiraient chez les
    # joueurs pour rien (seulement dans notre dossier lod_hz/, jamais un fichier des auteurs des gunpacks)
    gardees = citees()
    retirees = [f for f in fichiers_lod() if f.resolve() not in gardees]
    for f in retirees:
        f.unlink()
        print("[lod] %s retirée (plus citée)" % f.relative_to(RACINE))
    print("[lod] %d versions allégées écrites, %d fiches reliées, %d gardées sans (petits cubes), %d retirée(s)" % (
        len(faits), len(liste) - sautes, sautes, len(retirees)))


def verifier():
    """Contrôle seul (outils/verifier.py) : rend le nombre de problèmes, rien n'est écrit."""
    problemes = 0
    attendus = {}             # modèle -> texte attendu de sa version allégée (plusieurs fiches partagent un modèle)
    for cle, (f, d) in sorted(fiches().items()):
        lod = d.get("lod")
        if not isinstance(lod, dict):
            continue
        flod = ressource(lod.get("model"), "geo_models", ".json")
        if flod is None or ressource(lod.get("texture"), "textures", ".png") is None:
            # TaCZ n'aurait rien à dessiner de loin
            print("[lod] %s : version allégée introuvable (modèle %s, texture %s)" % (cle, lod.get("model"), lod.get("texture")))
            problemes += 1
            continue
        if not de_l_outil(lod.get("model")):
            continue          # LOD de l'auteur du gunpack : pas à nous de le refaire
        modele = ressource(d.get("model"), "geo_models", ".json")
        if modele not in attendus:
            leger = allege(lire_geo(modele)) if modele is not None else None
            attendus[modele] = ecrit(leger) if leger is not None else None
        attendu = attendus[modele]
        if attendu is None or lod.get("model") != id_lod(d["model"]) or lod.get("texture") != d.get("texture") \
                or flod.read_text(encoding="utf-8") != attendu:
            print("[lod] %s : version allégée PAS à jour avec le modèle %s" % (cle, d.get("model")))
            problemes += 1
    for cle, f, d, modele, nm, nl in a_alleger():
        if allege(lire_geo(modele)) is not None:
            print("[lod] %s : sans version allégée (%d cubes dessinés chez les autres joueurs)" % (cle, nl if nl is not None else nm))
            problemes += 1
    gardees = citees()
    for f in fichiers_lod():
        if f.resolve() not in gardees:
            print("[lod] %s : plus citée par aucune fiche" % f.relative_to(RACINE))
            problemes += 1
    if problemes:
        print("[lod] relancer : python outils/lod_armes.py --installer")
    print("[lod] %d problème(s)" % problemes)
    return problemes


# ------------------------------------------------------------------ aperçu (images avant / après)

def apercu(dossier, noms):
    import importlib.util
    import numpy as np
    from PIL import Image, ImageDraw, ImageFont
    spec = importlib.util.spec_from_file_location("fragments", RACINE / "modules" / "bunker" / "outils" / "fragments.py")
    rendu = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(rendu)
    sortie = Path(dossier)
    sortie.mkdir(parents=True, exist_ok=True)
    try:
        police = ImageFont.truetype(str(RACINE / "launcher" / "app" / "assets" / "fonts" / "DMSans-Bold.ttf"), 20)
        petite = ImageFont.truetype(str(RACINE / "launcher" / "app" / "assets" / "fonts" / "DMSans-Bold.ttf"), 15)
    except OSError:
        police = petite = None
    liste = {cle: (f, d, modele, nm, nl) for cle, f, d, modele, nm, nl in a_alleger()}
    # écran 1920 x 1080, champ de vision vertical 70° (réglage de Minecraft) : points de l'écran par bloc à d blocs
    par_bloc = lambda dist: 1080 / (2 * dist * math.tan(math.radians(35)))
    resume = []
    for cle in noms:
        if cle not in liste:
            print("[lod] %s : pas dans la liste (vrai LOD, ou fiche inconnue)" % cle)
            continue
        f, d, modele, nm, nl = liste[cle]
        tex = ressource(d["texture"], "textures", ".png")
        img = np.asarray(Image.open(tex).convert("RGBA")).astype(np.float32) / 255.0
        complet = lire_geo(modele)
        leger = allege(complet)
        if leger is None:
            print("[lod] %s : gardée sans version allégée (faite de petits cubes)" % cle)
            continue
        qa, qb = quads(complet, img), quads(leger, img)
        p = np.concatenate([q[0] for q in qa])
        lo, hi = p.min(0), p.max(0)
        centre = tuple((lo + hi) / 2)
        longueur = float(max(hi - lo))
        # taille dans le monde : la longueur du modèle de l'AK-47 de TaCZ (43 points) fait ≈ 0,9 bloc en 3e personne
        blocs = longueur * 0.9 / 43.0
        lignes = []
        for dist, zoom_vue in ((3, 2), (10, 4), (30, 8), (60, 8)):
            px = max(4, int(round(blocs * par_bloc(dist))))
            z = px * 0.92 / longueur
            cote = px + 6
            a = rendu.rendre(qa, (cote, cote), yaw=-62, pitch=12, zoom=z, centre=centre, fond=(88, 96, 84, 255), ss=1)
            b = rendu.rendre(qb, (cote, cote), yaw=-62, pitch=12, zoom=z, centre=centre, fond=(88, 96, 84, 255), ss=1)
            ecart = float(np.abs(np.asarray(a, np.float32) - np.asarray(b, np.float32))[..., :3].mean())
            a = a.resize((cote * zoom_vue, cote * zoom_vue), Image.NEAREST)
            b = b.resize((cote * zoom_vue, cote * zoom_vue), Image.NEAREST)
            lignes.append((dist, px, zoom_vue, a, b, ecart))
        na = len(cubes_de(leger))
        resume.append((cle.split(":", 1)[1].replace("_display", ""), nl if nl is not None else nm, na, lignes[1:]))
        larg = max(a.width for *_, a, _b, _e in lignes) * 2 + 40
        haut = sum(a.height + 44 for *_, a, _b, _e in lignes) + 70
        pl = Image.new("RGBA", (max(larg, 760), haut), (24, 26, 30, 255))
        dr = ImageDraw.Draw(pl)
        nom = cle.split(":", 1)[1].replace("_display", "")
        dr.text((12, 10), "%s : %d cubes -> %d cubes (-%.0f %%)" % (nom, nl if nl is not None else nm, na,
                                                                    100.0 * (1 - na / float(nl if nl is not None else nm))),
                fill=(236, 238, 242, 255), font=police)
        dr.text((12, 38), "gauche : modèle complet (aujourd'hui) — droite : version allégée (proposée)",
                fill=(170, 176, 186, 255), font=petite)
        y = 70
        for dist, px, zv, a, b, ecart in lignes:
            dr.text((12, y), "à %d blocs : l'arme fait %d points de long sur l'écran (agrandie %d fois : un carré = un point)"
                    % (dist, px, zv), fill=(200, 204, 212, 255), font=petite)
            y += 24
            pl.alpha_composite(a, (12, y))
            pl.alpha_composite(b, (a.width + 28, y))
            y += a.height + 20
        pl.save(sortie / ("lod_%s.png" % nom))
        print("[lod] %s : %s" % (cle, sortie / ("lod_%s.png" % nom)))
    if resume:
        planche_resume(resume, sortie / "lod_resume.png", police, petite)


def planche_resume(resume, fichier, police, petite):
    """Une page : chaque arme à 10, 30 et 60 blocs, complète puis allégée (agrandies, un carré = un point de l'écran)."""
    from PIL import Image, ImageDraw
    colonnes = [(dist, zv) for dist, _px, zv, _a, _b, _e in resume[0][3]]
    largeurs = [max(l[3][i][3].width for l in resume) for i in range(len(colonnes))]
    hauteurs = [max(l[3][i][3].height for i in range(len(colonnes))) for l in resume]
    W = 190 + sum(2 * w + 30 for w in largeurs) + 10
    H = 96 + sum(h + 16 for h in hauteurs) + 10
    pl = Image.new("RGBA", (W, H), (24, 26, 30, 255))
    dr = ImageDraw.Draw(pl)
    dr.text((12, 10), "Armes des autres joueurs : modèle complet (gauche) et version allégée (droite)",
            fill=(236, 238, 242, 255), font=police)
    x = 190
    for (dist, zv), w in zip(colonnes, largeurs):
        dr.text((x, 44), "à %d blocs (agrandi %d fois)" % (dist, zv), fill=(200, 204, 212, 255), font=petite)
        dr.text((x, 64), "complet", fill=(150, 156, 166, 255), font=petite)
        dr.text((x + w + 10, 64), "allégé", fill=(150, 156, 166, 255), font=petite)
        x += 2 * w + 30
    y = 96
    for (nom, avant, apres, lignes), h in zip(resume, hauteurs):
        dr.text((12, y + 4), nom, fill=(236, 238, 242, 255), font=petite)
        dr.text((12, y + 24), "%d -> %d cubes" % (avant, apres), fill=(170, 176, 186, 255), font=petite)
        x = 190
        for (dist, px, zv, a, b, e), w in zip(lignes, largeurs):
            pl.alpha_composite(a, (x, y))
            pl.alpha_composite(b, (x + w + 10, y))
            x += 2 * w + 30
        y += h + 16
    pl.save(fichier)
    print("[lod] page résumé : %s" % fichier)


def quads(geo, img):
    """Quads (positions monde, UV en 1/16 de texture, image) du modèle, comme TaCZ 1.1.8 (BedrockCubeBox / PerFace),
    sans les os que TaCZ ne dessine pas en 3e personne (mains et repères *_pos)."""
    import numpy as np
    g = geo["minecraft:geometry"][0]
    tw, th = g["description"]["texture_width"], g["description"]["texture_height"]
    os_ = {b["name"]: b for b in g["bones"]}
    monde = {}

    def rot(rx, ry, rz):
        cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
        return (np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
                @ np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]))

    def mat(pos, r):
        m = np.eye(4)
        m[:3, :3] = rot(*r)
        m[:3, 3] = pos
        return m

    def rad(v):
        return [math.radians(x) for x in v] if v else [0, 0, 0]

    def pivot_os(b, i):
        p = b["pivot"]
        if b.get("parent"):
            q = os_[b["parent"]]["pivot"]
            return q[i] - p[i] if i == 1 else p[i] - q[i]
        return 24 - p[i] if i == 1 else p[i]

    def matrice(nom):
        if nom not in monde:
            b = os_[nom]
            m = mat([pivot_os(b, i) for i in range(3)], rad(b.get("rotation")))
            monde[nom] = matrice(b["parent"]) @ m if b.get("parent") else m
        return monde[nom]

    def cache(nom):
        while nom:
            n = nom.lower()
            if n.endswith("_pos") or n in ("lefthand", "righthand"):
                return True
            nom = os_[nom].get("parent")
        return False

    ordre = {"down": (5, 4, 0, 1), "up": (2, 3, 7, 6), "west": (0, 4, 7, 3), "north": (1, 0, 3, 2), "east": (5, 1, 2, 6),
             "south": (4, 5, 6, 7)}
    out = []
    for b in g["bones"]:
        if cache(b["name"]):
            continue
        for c in b.get("cubes") or []:
            o, s, d = c["origin"], c["size"], float(c.get("inflate", 0) or 0)
            if c.get("rotation"):
                p = c["pivot"]
                m = matrice(b["name"]) @ mat([p[i] - b["pivot"][i] if i != 1 else b["pivot"][i] - p[i] for i in range(3)],
                                             rad(c["rotation"]))
                org = [o[i] - p[i] if i != 1 else p[i] - o[i] - s[i] for i in range(3)]
            else:
                m = matrice(b["name"])
                org = [o[i] - b["pivot"][i] if i != 1 else b["pivot"][i] - o[i] - s[i] for i in range(3)]
            x, y, z = org
            w, h, e = s
            x0, y0, z0, x1, y1, z1 = x - d, y - d, z - d, x + w + d, y + h + d, z + e + d
            uv = c.get("uv", [0, 0])
            if c.get("mirror") and not isinstance(uv, dict):
                x0, x1 = x1, x0
            v = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1),
                 (x0, y1, z1)]
            faces = []
            if isinstance(uv, dict):
                for nomf, idx in ordre.items():
                    fu = uv.get(nomf)
                    if fu:
                        u1, v1 = fu["uv"]
                        du, dv = fu.get("uv_size", [0, 0])
                        faces.append((idx, (u1, v1, u1 + du, v1 + dv)))
            else:
                tu, tv = uv
                dx, dy, dz = int(w), int(h), int(e)
                p1, p2, p3 = tu + dz, tu + dz + dx, tu + dz + dx + dx
                p4, p5, p6, p7 = tu + dz + dx + dz, tu + dz + dx + dz + dx, tv + dz, tv + dz + dy
                boites = {"down": (p1, tv, p2, p6), "up": (p2, p6, p3, tv), "west": (tu, p6, p1, p7),
                          "north": (p1, p6, p2, p7), "east": (p2, p6, p4, p7), "south": (p4, p6, p5, p7)}
                faces = [(idx, boites[nomf]) for nomf, idx in ordre.items()]
            for (i0, i1, i2, i3), (u1, v1, u2, v2) in faces:
                pts = np.array([list(v[i]) + [1] for i in (i0, i1, i2, i3)]) @ m.T
                pts = pts[:, :3] * np.array([-1, -1, 1])           # TaCZ : scale(-1, -1, 1) au rendu
                uvs = np.array([[u2, v1], [u1, v1], [u1, v2], [u2, v2]], np.float64)
                uvs[:, 0] *= 16.0 / tw
                uvs[:, 1] *= 16.0 / th
                out.append((pts, uvs, img))
    return out


def main():
    a = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    a.add_argument("--apercu", metavar="DOSSIER", help="planches avant / après (rien n'est écrit dans le dépôt)")
    a.add_argument("--armes", nargs="*", default=["arkaniaz:mod2_display", "heraliz_fuse:stakeout_display",
                                                  "arkaniaz:mg3_display", "heraliz_fuse:ro635v3_display",
                                                  "maxstuff:rpk16_display", "arkaniaz:butterfly_knife_asiimov_display"],
                   help="fiches à dessiner avec --apercu")
    a.add_argument("--installer", action="store_true", help="écrit les versions allégées (accord du propriétaire)")
    a.add_argument("--verifier", action="store_true", help="contrôle seul (outils/verifier.py) : code 1 au moindre problème")
    o = a.parse_args()
    if o.verifier:
        sys.exit(1 if verifier() else 0)
    if o.installer:
        installer()
    elif o.apercu:
        apercu(o.apercu, o.armes)
    else:
        lister()


if __name__ == "__main__":
    main()
