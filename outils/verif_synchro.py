"""Listes envoyées par différences (21/11) : liste Tab (Grades), tags des joueurs (Clans), liste vocale (CaraCrea).

    python outils/verif_synchro.py             contrôle : les trois copies de Synchro.java identiques (au paquet près), puis
                                               l'essai hors du jeu outils/synchro/EssaiSynchro.java (javac et java 17 ou
                                               plus ; sautés sans eux, comme sur une machine sans Java)
    python outils/verif_synchro.py --tailles   ce que coûtent une arrivée, un départ, un changement de grade ou de tag et
                                               la vague de reconnexions après un redémarrage, avant et après, à 50, 100 et
                                               150 joueurs : calcul d'après les encodeurs des paquets (mêmes octets que le
                                               jeu), compression du serveur comprise (seuil 256, comme le banc de charge)

Avant le 21/11, chacune de ces listes partait COMPLÈTE à tous les joueurs à chaque arrivée ou départ : 150 listes de 150
lignes à 150 joueurs. Maintenant : complète pour le joueur qui arrive, puis seulement ce qui change, numéroté (un joueur
à qui une différence manque redemande la liste complète). Code de sortie 1 au moindre problème.
"""
import os
import random
import re
import shutil
import subprocess
import sys
import tempfile
import zlib
from pathlib import Path

D = Path(__file__).resolve().parent.parent
COPIES = [D / "modules/boutique/src/commun/java/fr/heraliz/grades/reseau/Synchro.java",
          D / "modules/clans/src/commun/java/fr/heralisz/clans/network/Synchro.java",
          D / "modules/voix/src/commun/java/com/caracrea/network/Synchro.java"]
ESSAI = D / "outils" / "synchro" / "EssaiSynchro.java"


def sans_paquet(f):
    texte = f.read_text(encoding="utf-8").replace("\r\n", "\n")
    return re.sub(r"^package [\w.]+;\n", "", texte, count=1)


def controle():
    erreurs = 0
    for f in COPIES:
        if not f.exists():
            print("[synchro] ERREUR : %s absent" % f.relative_to(D))
            return 1
    ref = sans_paquet(COPIES[0])
    for f in COPIES[1:]:
        if sans_paquet(f) != ref:
            print("[synchro] ERREUR : %s n'est plus la copie de %s (à garder identiques, au paquet près)"
                  % (f.relative_to(D), COPIES[0].relative_to(D)))
            erreurs += 1
    javac, java = shutil.which("javac"), shutil.which("java")
    home = os.environ.get("JAVA_HOME")
    if home and (Path(home) / "bin").is_dir():
        javac = shutil.which("javac", path=str(Path(home) / "bin")) or javac
        java = shutil.which("java", path=str(Path(home) / "bin")) or java
    if not javac or not java:
        print("[synchro] javac ou java absent : essai hors du jeu sauté (copies contrôlées : %d problème(s))" % erreurs)
        return erreurs
    tmp = Path(tempfile.mkdtemp(prefix="hz_synchro_"))
    try:
        r = subprocess.run([javac, "-encoding", "UTF-8", "--release", "17", "-nowarn", "-d", str(tmp)]
                           + [str(f) for f in COPIES] + [str(ESSAI)], capture_output=True, text=True, encoding="utf-8",
                           errors="replace")
        if r.returncode != 0:
            print("[synchro] ERREUR : compilation de l'essai impossible :")
            for l in (r.stdout + r.stderr).splitlines():
                if "Picked up" not in l:
                    print("      " + l)
            return erreurs + 1
        r = subprocess.run([java, "-Dstdout.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-cp", str(tmp), "EssaiSynchro"],
                           capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=300)
        for l in (r.stdout + r.stderr).splitlines():
            if l.startswith("[synchro]"):
                print(l)
        if r.returncode != 0:
            erreurs += 1
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    return erreurs


# ---------------------------------------------------------------------------------------------------- coût réseau

def varint(v):
    n = 1
    while v >= 0x80:
        v >>= 7
        n += 1
    return n


def b_varint(v):
    o = bytearray()
    while True:
        b = v & 0x7F
        v >>= 7
        if v:
            o.append(b | 0x80)
        else:
            o.append(b)
            return bytes(o)


def b_utf(s):
    e = s.encode("utf-8")
    return b_varint(len(e)) + e


def trame(canal, message):
    """Paquet d'un canal de Forge tel qu'il part : numéro du paquet, nom du canal, numéro du message, données ; compressé
    au-delà de 256 octets (seuil du serveur), longueur de la trame en tête. Rend (octets bruts, octets sortants)."""
    p = b_varint(0x17) + b_utf(canal) + message
    n = len(p)
    corps = b_varint(n) + zlib.compress(p, 6) if n >= 256 else b_varint(0) + p
    return n, len(corps) + varint(len(corps))


class Joueurs:
    """Joueurs réalistes (tirés au sort, graine fixe) : grades, clans, surnoms, groupes de voix."""

    def __init__(self, n, graine=150):
        r = random.Random(graine)
        self.r = r
        grades = ["survivant"] * 70 + ["rescape"] * 12 + ["traqueur"] * 8 + ["roi_de_la_zone"] * 4 + ["streamer"] + \
                 ["guide", "helper", "moderateur", "admin", "fondateur"]
        tags = ["ZK", "NOVA", "WOLF", "ABC", "KRAK", "LYNX", "DUST", "OMEGA", "RAT", "VIPER"]
        self.l = []
        for i in range(n):
            self.l.append({
                "uuid": r.getrandbits(128).to_bytes(16, "big"),
                "grade": r.choice(grades),
                "clan": r.choice(tags) if r.random() < 0.75 else "",
                "couleur": r.getrandbits(24),
                "surnom": ("Surnom%d" % r.randint(1, 99)) if r.random() < 0.05 else "",
                "voix": i + 1,
                "groupe": r.choice([0] * 9 + [1, 2, 3]),
            })

    def nouveau(self, i):
        return Joueurs(1, graine=10_000 + i).l[0]


# --- liste Tab (heraliz_grades:main : InfosTab #2, DeltaTab #8)
def ligne_tab(j):
    return j["uuid"] + b_utf(j["grade"]) + b_utf(j["clan"]) + j["couleur"].to_bytes(4, "big") + b_utf(j["surnom"]) + b"\x00"


def tab_complet(js, version=None):
    corps = (b"" if version is None else b_varint(version)) + b_varint(len(js)) + b"".join(ligne_tab(j) for j in js)
    return trame("heraliz_grades:main", b"\x02" + corps)


def tab_delta(changes, retires):
    corps = b_varint(1234) + b_varint(len(retires)) + b"".join(j["uuid"] for j in retires) + b_varint(len(changes)) + \
            b"".join(ligne_tab(j) for j in changes)
    return trame("heraliz_grades:main", b"\x08" + corps)


# --- tags des joueurs (heralisz_clans:main : TagsS2C #2, TagsDeltaS2C #27)
def tag(j):
    return j["uuid"] + b_utf(j["clan"][:8]) + j["couleur"].to_bytes(4, "big")


def tags_complet(js, version=None):
    avec = [j for j in js if j["clan"]]
    corps = (b"" if version is None else b_varint(version)) + b_varint(len(avec)) + b"".join(tag(j) for j in avec)
    return trame("heralisz_clans:main", b"\x02" + corps)


def tags_delta(changes, retires):
    corps = b_varint(1234) + b_varint(len(retires)) + b"".join(j["uuid"] for j in retires) + b_varint(len(changes)) + \
            b"".join(tag(j) for j in changes)
    return trame("heralisz_clans:main", bytes([27]) + corps)


# --- liste vocale (caracrea:main : VoiceRoster #8, VoiceRosterDelta #12)
GROUPES = [(1, "Escouade A"), (2, "Les potes"), (3, "Raid")]


def voix(j):
    return j["uuid"] + b_varint(j["voix"]) + b"\x00" + b_varint(j["groupe"])


def groupes():
    return b_varint(len(GROUPES)) + b"".join(b_varint(i) + b_utf(n) + b"\x00\x00" for i, n in GROUPES)


def voix_complet(js, version=None):
    corps = (b"" if version is None else b_varint(version)) + groupes() + b_varint(len(js)) + b"".join(voix(j) for j in js)
    return trame("caracrea:main", b"\x08" + corps)


def voix_delta(changes, retires):
    corps = b_varint(1234) + b_varint(0) + b_varint(0) + b_varint(len(retires)) + b"".join(j["uuid"] for j in retires) + \
            b_varint(len(changes)) + b"".join(voix(j) for j in changes)
    return trame("caracrea:main", bytes([12]) + corps)


def somme(*envois):
    """envois : (nombre de paquets, (octets bruts, octets sortants)) -> (paquets, bruts, sortants)."""
    p = b = s = 0
    for n, (br, so) in envois:
        p += n
        b += n * br
        s += n * so
    return p, b, s


def couts(n):
    """Coût de chaque événement dans un serveur de n joueurs (après une arrivée : n joueurs), avant et après."""
    js = Joueurs(n).l
    arrive = js[-1]                      # le dernier arrivé (dans un clan, comme 75 % des joueurs)
    if not arrive["clan"]:
        arrive["clan"] = "ZK"
    avant_depart = js
    apres_depart = js[:-1]
    clan = "ZK"
    membres = [j for j in js if j["clan"] == clan]
    o = {}
    # arrivée : avant, liste Tab complète, table des tags complète et liste vocale complète à TOUS (n joueurs)
    o["arrivée"] = (somme((n, tab_complet(js)), (n, tags_complet(js)), (n, voix_complet(js))),
                    somme((1, tab_complet(js, 1)), (n - 1, tab_delta([arrive], [])),
                          (1, tags_complet(js, 1)), (n - 1, tags_delta([arrive], [])),
                          (1, voix_complet(js, 1)), (n - 1, voix_delta([arrive], []))))
    # départ : avant, les trois listes complètes (sans lui) aux n - 1 qui restent
    o["départ"] = (somme((n - 1, tab_complet(apres_depart)), (n - 1, tags_complet(apres_depart)), (n - 1, voix_complet(apres_depart))),
                   somme((n - 1, tab_delta([], [arrive])), (n - 1, tags_delta([], [arrive])), (n - 1, voix_delta([], [arrive]))))
    # changement de grade d'un joueur : liste Tab
    o["grade changé"] = (somme((n, tab_complet(js))), somme((n, tab_delta([js[0]], []))))
    # tag ou bannière d'un clan changé : liste Tab (ses membres) et table des tags
    o["tag de clan changé (%d membres en ligne)" % len(membres)] = (
        somme((n, tab_complet(js)), (n, tags_complet(js))),
        somme((n, tab_delta(membres, [])), (n, tags_delta(membres, []))))
    # joueur du staff qui se cache : liste Tab (les autres le perdent, le staff le voit « caché »)
    o["vanish d'un staff"] = (somme((n, tab_complet(js))), somme((n, tab_delta([], [js[0]]))))
    # vague de reconnexions après un redémarrage : les n joueurs arrivent un par un (pire cas : un par tick)
    av = [0, 0, 0]
    ap = [0, 0, 0]
    for k in range(1, n + 1):
        sous = js[:k]
        a = somme((k, tab_complet(sous)), (k, tags_complet(sous)), (k, voix_complet(sous)))
        b = somme((1, tab_complet(sous, k)), (k - 1, tab_delta([sous[-1]], [])), (1, tags_complet(sous, k)),
                  (k - 1 if sous[-1]["clan"] else 0, tags_delta([sous[-1]], [])), (1, voix_complet(sous, k)),
                  (k - 1, voix_delta([sous[-1]], [])))
        for i in range(3):
            av[i] += a[i]
            ap[i] += b[i]
    o["vague de %d reconnexions" % n] = (tuple(av), tuple(ap))
    return o


def mo(o):
    return "%.2f Mo" % (o / 1e6) if o >= 1e6 else "%.1f Ko" % (o / 1e3)


def tailles():
    print("Coût réseau des trois listes (liste Tab, tags de clan, liste vocale) : paquets, octets bruts, octets sortants")
    print("(compression du serveur comprise). Avant = jusqu'au 20/11 ; après = différences du 21/11.")
    for n in (50, 100, 150):
        print("\n== %d joueurs ==" % n)
        print("%-40s %26s %26s %8s" % ("événement", "avant (paquets, sortants)", "après (paquets, sortants)", "gain"))
        for quoi, (a, b) in couts(n).items():
            print("%-40s %10d %15s %10d %15s %7.1fx" % (quoi, a[0], mo(a[2]), b[0], mo(b[2]), a[2] / max(1, b[2])))
            print("%-40s %10s %15s %10s %15s" % ("", "bruts :", mo(a[1]), "bruts :", mo(b[1])))


def main():
    if "--tailles" in sys.argv:
        tailles()
        return 0
    e = controle()
    print("[synchro] %d problème(s)" % e)
    return 1 if e else 0


if __name__ == "__main__":
    sys.exit(main())
