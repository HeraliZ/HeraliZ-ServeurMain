#!/usr/bin/env python3
"""Portée du son des silencieux TaCZ (gunpacks de serveur/tacz).

    python outils/silencieux.py              écrit la portée de chaque silencieux dans les gunpacks
    python outils/silencieux.py --verifier   contrôle seulement (code de sortie 1 si un fichier n'est pas à jour
                                             ou si un silencieux des gunpacks n'a pas de portée dans PORTEES)

Sur le serveur, un tir s'entend jusqu'à DefaultGunFireSoundDistance blocs (serveur/config/tacz-common.toml : 256).
Les silencieux des gunpacks retiraient seulement 12 à 32 blocs (valeurs prévues pour une portée de 64) : un tir
silencieux s'entendait encore à 230 blocs, presque aussi fort qu'un tir normal (les sons « silencieux » des gunpacks
ont le même volume que les tirs normaux, seul le timbre change).

Chaque silencieux a ici SA portée, en blocs (tableau PORTEES) : au-delà, les autres joueurs n'entendent rien (le
serveur ne leur envoie pas le son) ; en deçà, le son baisse avec la distance. Le script l'écrit dans le fichier de
données du silencieux : {"distance": {"multiplier": portée / portée d'un tir normal}, "use_silence_sound": true}.
Ces données sont envoyées par le serveur aux joueurs : rien à mettre à jour chez eux. Relancer le script si la portée
d'un tir normal change dans tacz-common.toml, ou après l'ajout d'un gunpack (--verifier signale tout silencieux
inconnu).

Le volume (deux fois moins fort qu'un tir normal pour les autres joueurs, comme TaCZ le fait déjà pour le tireur) est
réglé dans le mod tactique (mixin TaczSonSilencieuxMixin). La portée sert aussi à l'anticheat (Spartan montre le
tireur aux joueurs qui l'entendent) : rien à régler de son côté.
"""
import json
import re
import sys
from pathlib import Path

DEPOT = Path(__file__).resolve().parents[1]
TACZ = DEPOT / "serveur" / "tacz"
CONFIG_TACZ = DEPOT / "serveur" / "config" / "tacz-common.toml"

# Portée en blocs de chaque silencieux (identifiant de l'accessoire). Plus le silencieux est gros (long, épais), plus
# il étouffe ; un petit calibre s'étouffe mieux qu'un gros. Un tir sans silencieux : 256 blocs (tacz-common.toml).
PORTEES = {
    # silencieux de pistolet et de mitraillette (petits calibres) : les plus discrets
    "tacz:muzzle_silencer_ptilopsis": 16,       # PO-2 « Ptilopsis »
    "tacz:muzzle_silence_ptilopsis": 16,
    "tacz:muzzle_silencer_mirage": 16,          # Mirage
    "tacz:muzzle_silence_mirage": 16,
    "gucci_attachments:muzzle_s_fd197": 16,     # FD917 (silencieux de pistolet)
    # gros silencieux de fusil (longs ou épais)
    "tacz:muzzle_silencer_ursus": 24,           # Ursus (standard militaire)
    "tacz:muzzle_silence_ursus": 24,
    "tacz:muzzle_silencer_knight_qd": 24,       # Knight QD
    "tacz:muzzle_silence_knight_qd": 24,
    "tacz:muzzle_silencer_phantom_s1": 24,      # Phantom S1
    "tacz:muzzle_silence_phantom_s1": 24,
    "gucci_attachments:muzzle_s_gsx": 24,       # Eckland GS-X
    "gucci_attachments:muzzle_s_gogol9": 24,    # Gogol 9
    "gucci_attachments:muzzle_s_z60": 24,       # Z60
    "arkaniaz:sr25s": 24,                       # silencieux intégré du SR-25 (très long)
    # silencieux de fusil fins
    "gucci_attachments:muzzle_s_fss5": 32,      # FSS V
    "gucci_attachments:muzzle_s_osprey": 32,    # Osprey
    "gucci_attachments:muzzle_s_c784": 32,      # C784
    # court et imprimé en 3D ; calibre .50 (même étouffé, le tir reste puissant)
    "gucci_attachments:muzzle_s_widemouth": 40,  # 3DP Widemouth
    "tacz:muzzle_silencer_vulture": 40,         # Vulture .50
    # hybrides : à peine silencieux
    "maxstuff:supressed_brake": 48,             # frein de bouche silencieux
    "tacz:deagle_golden_long_barrel": 48,       # canon long du Deagle doré
}


# ------------------------------------------------------------------ lecture tolérante (commentaires, virgules finales)
def _sans_commentaires(texte):
    out, i, n, chaine = [], 0, len(texte), False
    while i < n:
        c = texte[i]
        if chaine:
            out.append(c)
            if c == "\\" and i + 1 < n:
                out.append(texte[i + 1])
                i += 2
                continue
            chaine = c != '"'
            i += 1
        elif c == '"':
            chaine = True
            out.append(c)
            i += 1
        elif texte.startswith("//", i):
            j = texte.find("\n", i)
            i = n if j < 0 else j
        elif texte.startswith("/*", i):
            j = texte.find("*/", i + 2)
            i = n if j < 0 else j + 2
        else:
            out.append(c)
            i += 1
    return re.sub(r",(\s*[}\]])", r"\1", "".join(out))


def lire(fichier):
    return json.loads(_sans_commentaires(fichier.read_text(encoding="utf-8-sig")))


def _accolades(texte, debut=0):
    """(indice, +1 / -1) de chaque accolade hors chaînes et commentaires, à partir de texte[debut]."""
    i, n = debut, len(texte)
    while i < n:
        c = texte[i]
        if c == '"':
            i += 1
            while i < n and texte[i] != '"':
                i += 2 if texte[i] == "\\" else 1
        elif texte.startswith("//", i):
            j = texte.find("\n", i)
            i = n if j < 0 else j
            continue
        elif texte.startswith("/*", i):
            j = texte.find("*/", i + 2)
            i = n if j < 0 else j + 1
        elif c in "{}":
            yield i, 1 if c == "{" else -1
        i += 1


def _objet_silence(texte):
    """(début, fin, indentation de la clé) de l'objet "silence" de premier niveau, ou None."""
    for m in re.finditer(r'"silence"\s*:\s*\{', texte):
        if sum(s for _, s in _accolades(texte[:m.start()])) != 1:
            continue  # « silence » d'un objet imbriqué
        debut = m.end() - 1
        profondeur = 0
        for i, s in _accolades(texte, debut):
            profondeur += s
            if profondeur == 0:
                ligne = texte[texte.rfind("\n", 0, m.start()) + 1:m.start()]
                return debut, i + 1, ligne if not ligne.strip() else "  "
        raise ValueError("objet « silence » non fermé")
    return None


# ------------------------------------------------------------------ gunpacks
def portee_tir_normal():
    m = re.search(r"^\s*DefaultGunFireSoundDistance\s*=\s*(\d+)", CONFIG_TACZ.read_text(encoding="utf-8"), re.M)
    if not m:
        sys.exit("[silencieux] DefaultGunFireSoundDistance absent de " + str(CONFIG_TACZ))
    return int(m.group(1))


def fichiers_donnees(ident):
    """Fichiers de données de l'accessoire (d'après son index, dans n'importe quel gunpack)."""
    ns, chemin = ident.split(":", 1)
    out = []
    for index in TACZ.glob("*/data/%s/index/attachments/%s.json" % (ns, chemin)):
        ref = lire(index).get("data")
        if ref:
            dns, dchemin = ref.split(":", 1)
            out += sorted(TACZ.glob("*/data/%s/data/attachments/%s.json" % (dns, dchemin)))
    return out


def silencieux_des_gunpacks():
    """Identifiants de tous les accessoires des gunpacks qui jouent le son « silencieux »."""
    out = set()
    for index in TACZ.glob("*/data/*/index/attachments/**/*.json"):
        partie = index.relative_to(TACZ).parts
        ns = partie[2]
        chemin = "/".join(partie[5:])[:-5]
        try:
            ref = lire(index).get("data")
        except ValueError:
            continue
        if not ref:
            continue
        dns, dchemin = ref.split(":", 1)
        for f in TACZ.glob("*/data/%s/data/attachments/%s.json" % (dns, dchemin)):
            s = lire(f).get("silence")
            if isinstance(s, dict) and s.get("use_silence_sound"):
                out.add(ns + ":" + chemin)
    return out


def bloc(multiplicateur, indentation):
    """Nouvel objet « silence », indenté comme le reste du fichier (la clé est au premier niveau : un cran)."""
    i1, i2 = indentation * 2, indentation * 3
    return "{\n%s\"distance\": {\n%s\"multiplier\": %r\n%s},\n%s\"use_silence_sound\": true\n%s}" % (
        i1, i2, multiplicateur, i1, i1, indentation)


def main():
    verifier = "--verifier" in sys.argv[1:]
    base = portee_tir_normal()
    problemes = 0
    a_jour = 0
    for ident, blocs in PORTEES.items():
        fichiers = fichiers_donnees(ident)
        if not fichiers:
            print("[silencieux] %s : accessoire introuvable dans serveur/tacz" % ident)
            problemes += 1
            continue
        mult = blocs / base
        for f in fichiers:
            texte = f.read_text(encoding="utf-8")
            actuel = lire(f).get("silence")
            voulu = {"distance": {"multiplier": mult}, "use_silence_sound": True}
            if actuel == voulu:
                a_jour += 1
                continue
            if verifier:
                print("[silencieux] %s : pas à jour (%s blocs voulus)" % (f.relative_to(DEPOT), blocs))
                problemes += 1
                continue
            pos = _objet_silence(texte)
            if pos is None:
                print("[silencieux] %s : pas de « silence » dans %s" % (ident, f.relative_to(DEPOT)))
                problemes += 1
                continue
            debut, fin, indentation = pos
            texte = texte[:debut] + bloc(mult, indentation) + texte[fin:]
            f.write_bytes(texte.encode("utf-8"))
            if lire(f).get("silence") != voulu:
                sys.exit("[silencieux] écriture incorrecte : " + str(f))
            print("[silencieux] %-40s %2d blocs  (%s)" % (ident, blocs, f.relative_to(TACZ)))
    for ident in sorted(silencieux_des_gunpacks() - set(PORTEES)):
        print("[silencieux] %s : silencieux sans portée dans PORTEES (outils/silencieux.py)" % ident)
        problemes += 1
    print("[silencieux] tir normal %d blocs ; %d silencieux, %d fichiers déjà à jour, %d problème(s)"
          % (base, len(PORTEES), a_jour, problemes))
    sys.exit(1 if problemes else 0)


if __name__ == "__main__":
    main()
