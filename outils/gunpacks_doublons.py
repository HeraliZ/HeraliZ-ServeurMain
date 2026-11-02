"""Doublons des gunpacks dans la copie livrée aux joueurs (audit du 02/11, point 37).

    python outils/gunpacks_doublons.py              rapport sur serveur/tacz (rien n'est écrit)
    python outils/gunpacks_doublons.py <dossier>    allège ce dossier tacz (une COPIE, jamais serveur/tacz du dépôt)

Les gunpacks du dépôt (serveur/tacz, dont des packs d'autres auteurs) ne sont jamais modifiés : seule la copie mise
dans le pack du launcher est allégée (outils/pack_launcher.py). TaCZ réunit tous les gunpacks du dossier « tacz » en un
seul pack de ressources (GunPackLoader : « tacz_resources ») : une ressource est trouvée par son identifiant
(espace:chemin), quel que soit le gunpack qui la porte.

Retiré de la copie, sans rien changer à ce que joue le jeu :
  1. un fichier identique octet pour octet à un autre fichier de MÊME identifiant (même espace et même chemin sous
     assets/, dans un autre gunpack) : le même fichier reste trouvé ;
  2. un son (assets/<espace>/tacz_sounds/<chemin>.ogg, identifiant <espace>:<chemin>) identique octet pour octet à un
     autre son : chaque fois que son identifiant est écrit comme valeur entière d'un JSON des assets (sons des armes,
     « effect » des animations), il est remplacé par celui du son gardé. Un son n'est retiré que si son identifiant
     n'apparaît NULLE PART ailleurs : ni dans un autre texte des gunpacks (Lua, data, morceau d'un autre identifiant), ni
     dans nos modules, et que son chemin seul (sans espace) n'est écrit dans aucun JSON.
Le fichier gardé : celui du gunpack heraliz_1_base_tacz s'il y en a un, sinon le premier par ordre alphabétique.
Les autres doublons (modèles, animations .gltf, textures à un autre identifiant) ne sont pas touchés : leurs références
ont des formes multiples (extensions, chemins implicites), à ne faire qu'avec un essai en jeu.
"""
import collections
import hashlib
import json
import re
import sys
from pathlib import Path

DEPOT = Path(__file__).resolve().parents[1]
PREFERE = "heraliz_1_base_tacz"
TEXTES = (".json", ".lua", ".mcmeta", ".txt", ".toml", ".properties", ".md")
# « mot » de la forme d'un identifiant (espace:chemin/fichier)
JETON = re.compile(r"[\w./:-]+")


def _valeurs(doc, sortie):
    """Chaînes écrites comme valeurs (jamais les clés) d'un JSON lu."""
    if isinstance(doc, str):
        sortie.append(doc)
    elif isinstance(doc, list):
        for x in doc:
            _valeurs(x, sortie)
    elif isinstance(doc, dict):
        for x in doc.values():
            _valeurs(x, sortie)
    return sortie


def _remplacer(doc, table):
    """Copie du JSON lu dont chaque valeur chaîne présente dans table est remplacée (les clés ne changent jamais)."""
    if isinstance(doc, str):
        return table.get(doc, doc)
    if isinstance(doc, list):
        return [_remplacer(x, table) for x in doc]
    if isinstance(doc, dict):
        return {k: _remplacer(v, table) for k, v in doc.items()}
    return doc


def _empreinte(f):
    return hashlib.sha256(f.read_bytes()).hexdigest()


def _ordre(f, racine):
    rel = f.relative_to(racine).as_posix()
    return (0 if rel.startswith(PREFERE + "/") else 1, rel)


def analyser(racine):
    """(retirer, remplacer, refus) pour le dossier tacz donné (avec ses dossiers data/, que le serveur envoie aux joueurs) :
    chemins à retirer (relatifs au dossier), {ancien id: nouveau id}, raisons des sons gardés."""
    racine = Path(racine)
    assets = [f for f in sorted(racine.glob("*/assets/**/*")) if f.is_file()]
    # 1. même identifiant (chemin sous assets/), même contenu
    par_chemin = collections.defaultdict(list)
    for f in assets:
        par_chemin[Path(*f.relative_to(racine).parts[1:]).as_posix()].append(f)
    retirer, deja = [], set()
    for chemin, fs in par_chemin.items():
        if len(fs) < 2:
            continue
        fs.sort(key=lambda x: _ordre(x, racine))
        garde = _empreinte(fs[0])
        for f in fs[1:]:
            if _empreinte(f) == garde:
                retirer.append(f)
                deja.add(f)
    # 2. sons identiques à d'autres identifiants
    sons = {}
    for f in assets:
        p = f.relative_to(racine).parts
        if len(p) > 4 and p[1] == "assets" and p[3] == "tacz_sounds" and f.suffix == ".ogg" and f not in deja:
            ident = "%s:%s" % (p[2], "/".join(p[4:])[:-4])
            sons.setdefault(ident, f)
    groupes = collections.defaultdict(list)
    for ident, f in sons.items():
        groupes[_empreinte(f)].append((ident, f))
    candidats = {}
    for liste in groupes.values():
        if len(liste) < 2:
            continue
        liste.sort(key=lambda x: _ordre(x[1], racine))
        for ident, f in liste[1:]:
            candidats[ident] = (liste[0][0], f)
    refus = collections.Counter()
    if candidats:
        # toutes les chaînes des JSON des assets (valeurs entières), et tout le texte (gunpacks et nos modules)
        valeurs = collections.Counter()
        textes = []
        for f in sorted(racine.rglob("*")):
            if f.is_file() and f.suffix in TEXTES:
                t = f.read_text(encoding="utf-8", errors="replace")
                textes.append(t)
                if f.suffix == ".json" and "assets" in f.relative_to(racine).parts:
                    try:
                        valeurs.update(_valeurs(json.loads(t), []))
                    except ValueError:
                        pass  # JSON illisible ici (commentaires…) : ses identifiants ne comptent pas comme valeurs -> refusés
        modules = []
        for f in sorted((DEPOT / "modules").glob("*/src/**/*")):
            if f.is_file() and f.suffix in (".java", ".json", ".lua", ".mcmeta", ".txt") and "build" not in f.parts:
                modules.append(f.read_text(encoding="utf-8", errors="replace"))
        # identifiant entier seulement (pas un morceau d'un identifiant plus long, ex. « tir » dans « tir_3p ») : le texte est
        # coupé en « mots » faits des lettres d'un identifiant, comptés s'ils sont un identifiant candidat
        brut = collections.Counter()
        for t in textes:
            brut.update(m for m in JETON.findall(t) if m in candidats)
        cites_modules = set()
        for t in modules:
            cites_modules.update(m for m in JETON.findall(t) if m in candidats)
        chemins = {i.split(":", 1)[1] for i in candidats}
        chemins_seuls = {c for c in chemins if valeurs.get(c)}
        for ident in sorted(candidats):
            garde, f = candidats[ident]
            total = brut[ident]
            if ident in cites_modules:
                refus["cité par nos modules"] += 1
            elif ident.split(":", 1)[1] in chemins_seuls:
                refus["chemin seul écrit dans un JSON"] += 1
            elif total != valeurs.get(ident, 0):
                refus["écrit ailleurs que comme valeur d'un JSON des assets"] += 1
            else:
                retirer.append(f)
                candidats[ident] = (garde, f, True)
                continue
            candidats[ident] = (garde, f, False)
    remplacer = {i: v[0] for i, v in candidats.items() if len(v) == 3 and v[2]}
    # un son gardé ne peut pas lui-même être retiré (même empreinte : gardé = premier du groupe, jamais candidat)
    return sorted({f.relative_to(racine).as_posix() for f in retirer}), remplacer, refus


def alleger(racine, reference=None):
    """Allège le dossier tacz donné (une copie, éventuellement sans data/) d'après l'analyse du dossier de référence
    (serveur/tacz du dépôt, data/ compris : un identifiant cité dans les data envoyées par le serveur n'est jamais
    retiré). Rend (fichiers retirés, octets, JSON réécrits, refus)."""
    racine = Path(racine)
    retirer, remplacer, refus = analyser(Path(reference) if reference else DEPOT / "serveur" / "tacz")
    reecrits = 0
    if remplacer:
        for f in sorted(racine.glob("*/assets/**/*.json")):
            t = f.read_bytes().decode("utf-8")
            if not any(m in remplacer for m in JETON.findall(t)):
                continue
            doc = json.loads(t)   # lisible : sinon aucun de ses identifiants ne serait dans remplacer
            f.write_bytes(json.dumps(_remplacer(doc, remplacer), indent=2, ensure_ascii=False).encode("utf-8"))
            reecrits += 1
    octets, n = 0, 0
    for rel in retirer:
        f = racine / rel
        if f.is_file():
            octets += f.stat().st_size
            f.unlink()
            n += 1
    return n, octets, reecrits, refus


def main():
    if len(sys.argv) > 1 and not sys.argv[1].startswith("--"):
        cible = Path(sys.argv[1]).resolve()
        if cible == (DEPOT / "serveur" / "tacz").resolve():
            raise SystemExit("[gunpacks] jamais sur serveur/tacz du dépôt : seulement sur une copie")
        n, octets, reecrits, refus = alleger(cible)
        print("[gunpacks] %d doublons retirés (%.1f Mo), %d JSON réécrits ; gardés : %s" % (n, octets / 1048576, reecrits, dict(refus)))
        return
    retirer, remplacer, refus = analyser(DEPOT / "serveur" / "tacz")
    octets = sum((DEPOT / "serveur" / "tacz" / rel).stat().st_size for rel in retirer)
    print("[gunpacks] serveur/tacz : %d doublons à retirer de la copie des joueurs (%.1f Mo), %d sons renvoyés vers un autre"
          " identifiant ; gardés : %s" % (len(retirer), octets / 1048576, len(remplacer), dict(refus)))


if __name__ == "__main__":
    main()
