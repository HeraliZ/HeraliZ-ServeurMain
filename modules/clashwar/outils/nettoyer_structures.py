#!/usr/bin/env python3
"""Structures du ClashWar : un bloc de meuble (cfm:…) cité par un fichier de structure mais absent de notre jar de
meubles devient de l'air DANS LE FICHIER.

    python modules/clashwar/outils/nettoyer_structures.py              montre ce qui serait changé (rien n'est écrit)
    python modules/clashwar/outils/nettoyer_structures.py --installer  nettoie les fichiers, les relit et les vérifie
    python modules/clashwar/outils/nettoyer_structures.py --verifier   code de sortie 1 si un fichier cite encore un
                                                                       bloc absent (outils/verifier.py clashwar)

En jeu, rien ne change : Structure.lire (module clashwar) posait déjà ces blocs en air, mais l'écrivait au journal à
chaque démarrage (« structure lobby_clashwar : 14 bloc(s) inconnu(s) poses en air (cfm:wires, … ) »). Les fichiers
lobby_clashwar et clashwar_1v1 à clashwar_4v4 ont été faits en jeu par le propriétaire avec une autre version des
meubles ; les six grandes cartes sont écrites par cartes_clashwar/generer.py (blocs de Minecraft seulement).

Ce que l'outil change, et rien d'autre :
- chaque bloc dont l'état est un bloc absent pointe vers l'air de la palette (`minecraft:air`, ajouté à la fin de la
  palette s'il n'y en a pas) et perd ses données de bloc (`nbt`) s'il en avait ;
- les états absents sortent de la palette ; les autres gardent leur ordre (numéros des blocs recalés) ;
- tout le reste (DataVersion, taille, autres blocs, entités) est réécrit à l'identique : avant tout changement, l'outil
  contrôle qu'il relit et réécrit le fichier octet pour octet, sinon il n'y touche pas. Compression gzip comme avant
  (nom du fichier dans l'en-tête, date 0, compression maximale).
Après l'écriture, le fichier est relu depuis le disque et comparé bloc par bloc à l'original.

Blocs connus : un nom est gardé s'il est une chaîne des classes du jar (serveur/mods-tiers/cfm-*.jar, celui livré au
Ptero) ou s'il a un fichier d'état (assets/cfm/blockstates). Dans le doute, le bloc reste. Seuls les blocs de cfm sont
contrôlés (Minecraft et nos mods ne sont pas lus ici).

Effet sur les arènes : l'empreinte d'une structure suit son fichier (Structure.empreinte), donc une arène déjà posée avec
l'ancien fichier est marquée obsolète au démarrage suivant et une nouvelle est posée d'avance, une fois, en tâche de
fond (ArenesService.prechauffer, avec son budget de blocs par tick). Relancer l'outil après tout nouveau fichier de
structure fait en jeu, ou après un changement du jar des meubles.
"""
import argparse
import gzip
import io
import os
import struct
import sys
import zipfile
from pathlib import Path

DEPOT = Path(__file__).resolve().parents[3]
STRUCTURES = DEPOT / "modules" / "clashwar" / "src" / "serveur" / "resources" / "data" / "heraliz_clashwar" / "structures"
MODS_TIERS = DEPOT / "serveur" / "mods-tiers"
ESPACE = "cfm"
AIR = b"minecraft:air"


# ---------------------------------------------------------------------- NBT (lecture et écriture à l'identique)
# Un tag est (type, valeur). Compound : dict {nom en octets: tag}, dans l'ordre du fichier. Liste : (type des éléments,
# [valeurs]). Les chaînes restent en octets (UTF-8 modifié de Java, jamais retraduit), les flottants en octets bruts :
# rien ne peut changer en route.
def _lire(d, i, t):
    if t == 1:
        return struct.unpack_from(">b", d, i)[0], i + 1
    if t == 2:
        return struct.unpack_from(">h", d, i)[0], i + 2
    if t == 3:
        return struct.unpack_from(">i", d, i)[0], i + 4
    if t == 4:
        return struct.unpack_from(">q", d, i)[0], i + 8
    if t == 5:
        return d[i:i + 4], i + 4
    if t == 6:
        return d[i:i + 8], i + 8
    if t == 7:
        n = struct.unpack_from(">i", d, i)[0]
        return d[i + 4:i + 4 + n], i + 4 + n
    if t == 8:
        n = struct.unpack_from(">H", d, i)[0]
        return d[i + 2:i + 2 + n], i + 2 + n
    if t == 9:
        et = d[i]
        n = struct.unpack_from(">i", d, i + 1)[0]
        i += 5
        v = []
        for _ in range(n):
            x, i = _lire(d, i, et)
            v.append(x)
        return (et, v), i
    if t == 10:
        r = {}
        while True:
            tt = d[i]
            i += 1
            if tt == 0:
                return r, i
            n = struct.unpack_from(">H", d, i)[0]
            cle = d[i + 2:i + 2 + n]
            i += 2 + n
            if cle in r:
                raise ValueError("clé en double : %r" % cle)
            x, i = _lire(d, i, tt)
            r[cle] = (tt, x)
    if t == 11:
        n = struct.unpack_from(">i", d, i)[0]
        return list(struct.unpack_from(">%di" % n, d, i + 4)), i + 4 + 4 * n
    if t == 12:
        n = struct.unpack_from(">i", d, i)[0]
        return list(struct.unpack_from(">%dq" % n, d, i + 4)), i + 4 + 8 * n
    raise ValueError("type de tag inconnu : %d" % t)


def _ecrire(f, t, v):
    if t == 1:
        f.write(struct.pack(">b", v))
    elif t == 2:
        f.write(struct.pack(">h", v))
    elif t == 3:
        f.write(struct.pack(">i", v))
    elif t == 4:
        f.write(struct.pack(">q", v))
    elif t in (5, 6):
        f.write(v)
    elif t == 7:
        f.write(struct.pack(">i", len(v)))
        f.write(v)
    elif t == 8:
        f.write(struct.pack(">H", len(v)))
        f.write(v)
    elif t == 9:
        et, liste = v
        f.write(struct.pack(">bi", et, len(liste)))
        for x in liste:
            _ecrire(f, et, x)
    elif t == 10:
        for cle, (tt, x) in v.items():
            f.write(struct.pack(">bH", tt, len(cle)))
            f.write(cle)
            _ecrire(f, tt, x)
        f.write(b"\x00")
    elif t == 11:
        f.write(struct.pack(">i%di" % len(v), len(v), *v))
    elif t == 12:
        f.write(struct.pack(">i%dq" % len(v), len(v), *v))
    else:
        raise ValueError("type de tag inconnu : %d" % t)


def lire_nbt(brut):
    """Octets non compressés -> (nom de la racine, compound racine)."""
    if brut[0] != 10:
        raise ValueError("la racine n'est pas un compound")
    n = struct.unpack_from(">H", brut, 1)[0]
    nom = brut[3:3 + n]
    racine, fin = _lire(brut, 3 + n, 10)
    if fin != len(brut):
        raise ValueError("%d octet(s) en trop après la racine" % (len(brut) - fin))
    return nom, racine


def ecrire_nbt(nom, racine):
    f = io.BytesIO()
    f.write(struct.pack(">bH", 10, len(nom)))
    f.write(nom)
    _ecrire(f, 10, racine)
    return f.getvalue()


def octets_tag(t, v):
    f = io.BytesIO()
    _ecrire(f, t, v)
    return f.getvalue()


def compresser(brut, nom_fichier):
    """gzip comme les fichiers d'origine : nom du fichier dans l'en-tête, date 0, compression maximale."""
    f = io.BytesIO()
    with gzip.GzipFile(filename=nom_fichier, mode="wb", fileobj=f, mtime=0, compresslevel=9) as g:
        g.write(brut)
    return f.getvalue()


# ---------------------------------------------------------------------- blocs du jar de meubles
def _chaines_classe(d):
    """Chaînes (CONSTANT_String) du pool de constantes d'une classe Java."""
    i = 10
    n = struct.unpack_from(">H", d, 8)[0]
    utf, refs, k = {}, [], 1
    while k < n:
        t = d[i]
        i += 1
        if t == 1:
            m = struct.unpack_from(">H", d, i)[0]
            utf[k] = d[i + 2:i + 2 + m]
            i += 2 + m
        elif t in (3, 4):
            i += 4
        elif t in (5, 6):
            i += 8
            k += 1
        elif t in (7, 8, 16, 19, 20):
            if t == 8:
                refs.append(struct.unpack_from(">H", d, i)[0])
            i += 2
        elif t in (9, 10, 11, 12, 17, 18):
            i += 4
        elif t == 15:
            i += 3
        else:
            raise ValueError("pool de constantes illisible (tag %d)" % t)
        k += 1
    return {utf[r].decode("utf-8", "replace") for r in refs if r in utf}


def jar_meubles():
    jars = sorted(MODS_TIERS.glob(ESPACE + "-*.jar"))
    if len(jars) != 1:
        raise SystemExit("[structures] il faut exactement un jar %s-*.jar dans %s (trouvé : %d)" % (ESPACE, MODS_TIERS, len(jars)))
    return jars[0]


def blocs_connus(jar):
    """Noms de blocs que le jar peut enregistrer : chaînes de ses classes, plus les noms des fichiers d'état."""
    connus = set()
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            if n.endswith(".class"):
                connus |= _chaines_classe(z.read(n))
            elif n.startswith("assets/%s/blockstates/" % ESPACE) and n.endswith(".json"):
                connus.add(n.rsplit("/", 1)[1][:-5])
    return connus


# ---------------------------------------------------------------------- nettoyage
def _nom(entree):
    return entree[b"Name"][1]


def _absent(nom, connus):
    ns, _, chemin = nom.decode("utf-8", "replace").partition(":")
    return ns == ESPACE and chemin not in connus


def _vue(racine):
    """Chaque bloc tel que le jeu le lit : (position, nom, propriétés, données de bloc), dans l'ordre du fichier."""
    palette = racine[b"palette"][1][1]
    vue = []
    for b in racine[b"blocks"][1][1]:
        e = palette[b[b"state"][1]]
        prop = octets_tag(*e[b"Properties"]) if b"Properties" in e else None
        nbt = octets_tag(*b[b"nbt"]) if b"nbt" in b else None
        vue.append((tuple(b[b"pos"][1][1]), _nom(e), prop, nbt))
    return vue


def nettoyer(racine, connus):
    """Change la racine sur place. Rend (noms retirés, nombre d'états retirés, nombre de blocs changés)."""
    if b"palettes" in racine or b"palette" not in racine:
        raise ValueError("palettes multiples ou palette absente : non géré")
    palette = racine[b"palette"][1][1]
    blocs = racine[b"blocks"][1][1]
    for b in blocs:
        s = b[b"state"][1]
        if not 0 <= s < len(palette):
            raise ValueError("bloc avec un état hors de la palette (%d)" % s)
    morts = {i for i, e in enumerate(palette) if _absent(_nom(e), connus)}
    if not morts:
        return [], 0, 0
    noms = sorted({_nom(palette[i]).decode() for i in morts})
    air = next((i for i, e in enumerate(palette) if i not in morts and list(e) == [b"Name"] and _nom(e) == AIR), None)
    nouvelle, renum = [], {}
    for i, e in enumerate(palette):
        if i not in morts:
            renum[i] = len(nouvelle)
            nouvelle.append(e)
    if air is None:
        air_n = len(nouvelle)
        nouvelle.append({b"Name": (8, AIR)})
    else:
        air_n = renum[air]
    changes = 0
    for b in blocs:
        s = b[b"state"][1]
        if s in morts:
            b[b"state"] = (3, air_n)
            b.pop(b"nbt", None)
            changes += 1
        else:
            b[b"state"] = (3, renum[s])
    racine[b"palette"] = (9, (10, nouvelle))
    return noms, len(morts), changes


def controler(avant, apres, connus):
    """Vrai si apres = avant avec les blocs absents passés en air, et rien d'autre de changé. Sinon : le problème."""
    if list(avant) != list(apres):
        return "clés de la racine changées"
    for cle in avant:
        if cle not in (b"palette", b"blocks") and octets_tag(*avant[cle]) != octets_tag(*apres[cle]):
            return "%s changé" % cle.decode()
    va, vb = _vue(avant), _vue(apres)
    if len(va) != len(vb):
        return "nombre de blocs changé (%d -> %d)" % (len(va), len(vb))
    for a, b in zip(va, vb):
        attendu = (a[0], AIR, None, None) if _absent(a[1], connus) else a
        if b != attendu:
            return "bloc %s changé : %s -> %s" % (a[0], a[1:], b[1:])
    restants = [_nom(e) for e in apres[b"palette"][1][1] if _absent(_nom(e), connus)]
    if restants:
        return "blocs absents encore dans la palette : %s" % restants
    return None


def traiter(chemin, connus, installer):
    """Rend (noms retirés, nombre d'états retirés, nombre de blocs changés). Lève une erreur au moindre doute."""
    compresse = chemin.read_bytes()
    brut = gzip.decompress(compresse)
    nom, racine = lire_nbt(brut)
    if ecrire_nbt(nom, racine) != brut:
        raise ValueError("relu puis réécrit, le fichier ne serait pas identique : non touché")
    _, avant = lire_nbt(brut)
    noms, etats, changes = nettoyer(racine, connus)
    if not etats or not installer:
        return noms, etats, changes
    nouveau = ecrire_nbt(nom, racine)
    temp = chemin.with_name(chemin.name + ".tmp")
    temp.write_bytes(compresser(nouveau, chemin.name))
    try:
        relu = gzip.decompress(temp.read_bytes())
        if relu != nouveau:
            raise ValueError("fichier relu différent de celui écrit")
        _, apres = lire_nbt(relu)
        probleme = controler(avant, apres, connus)
        if probleme:
            raise ValueError("contrôle après écriture : " + probleme)
        os.replace(temp, chemin)
    finally:
        if temp.exists():
            temp.unlink()
    # relu une dernière fois depuis sa vraie place
    _, final = lire_nbt(gzip.decompress(chemin.read_bytes()))
    probleme = controler(avant, final, connus)
    if probleme:
        raise ValueError("contrôle final : " + probleme)
    return noms, etats, changes


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    g = p.add_mutually_exclusive_group()
    g.add_argument("--installer", action="store_true", help="nettoie les fichiers de structure")
    g.add_argument("--verifier", action="store_true", help="code de sortie 1 si un fichier cite un bloc absent")
    a = p.parse_args()
    jar = jar_meubles()
    connus = blocs_connus(jar)
    fichiers = sorted(STRUCTURES.glob("*.nbt"))
    if not fichiers:
        raise SystemExit("[structures] aucun fichier dans %s" % STRUCTURES)
    print("[structures] %s : %d noms de blocs connus ; %d fichiers" % (jar.name, len(connus), len(fichiers)))
    a_faire = erreurs = 0
    for f in fichiers:
        try:
            noms, etats, changes = traiter(f, connus, a.installer)
        except Exception as ex:  # noqa: BLE001 (un fichier illisible n'arrête pas les autres)
            print("[structures] %s : ERREUR %s" % (f.name, ex))
            erreurs += 1
            continue
        if not etats:
            continue
        a_faire += 1
        quoi = "nettoyé" if a.installer else "à nettoyer"
        print("[structures] %s : %s, %d état(s) de bloc absent(s), %d bloc(s) en air : %s"
              % (f.name, quoi, etats, changes, ", ".join(noms)))
    if a.installer:
        print("[structures] %d fichier(s) nettoyé(s), %d erreur(s)" % (a_faire, erreurs))
    elif a_faire:
        print("[structures] %d fichier(s) à nettoyer : python modules/clashwar/outils/nettoyer_structures.py --installer"
              % a_faire)
    else:
        print("[structures] aucun bloc absent")
    sys.exit(1 if erreurs or (a.verifier and a_faire) else 0)


if __name__ == "__main__":
    main()
