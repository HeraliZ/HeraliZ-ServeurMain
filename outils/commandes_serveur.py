"""Envoyer un lot de commandes à la console du serveur (Ptero) : lancé par le workflow « Commandes du serveur »
(.github/workflows/commandes.yml), jamais tout seul.

Un lot = un fichier du dépôt, serveur/commandes/<lot>.txt : une commande de console par ligne (sans « / »), lignes vides
et lignes commençant par # ignorées. Le fichier est relu dans Git avant l'envoi : pas de commande tapée dans le
formulaire du workflow.

Par défaut, ESSAI À BLANC : il lit le lot, contrôle chaque ligne et l'état du serveur, et écrit dans le résumé ce qui
serait envoyé. Rien n'est envoyé.
Vraie exécution seulement si la case « executer » est cochée ET que la phrase PHRASE est tapée exactement, depuis main,
serveur en marche. Les commandes partent une à une (une petite pause entre deux) ; ensuite le journal du serveur est relu
et les réponses à ces commandes sont recopiées dans le résumé (adresses IP masquées, rien d'autre du journal).
"""
import os
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import carte_loot as cl  # noqa: E402  (accès SFTP et API du Ptero, résumé du workflow)

PHRASE = "ENVOYER LES COMMANDES"
DOSSIER = Path("serveur/commandes")
PAUSE = 0.6          # secondes entre deux commandes
MAX_COMMANDES = 400
# premiers mots permis (les commandes d'administration du jeu et de nos mods ; jamais stop, op, whitelist, ban...)
PERMIS = {"execute", "extraction", "territoire", "quete", "heralizloot", "carte", "depart", "train", "recolte",
          "worldborder", "pnj", "guide", "say", "recharger", "zonevip", "warp", "salle", "zombies", "setblock"}
# setblock : seulement pour retirer un bloc (remplacé par de l'air), à des coordonnées écrites en entier
SETBLOCK_AIR = re.compile(r"^setblock -?\d+ -?\d+ -?\d+ (minecraft:)?air$")
ENV = os.environ


def lire_lot(nom):
    if not re.fullmatch(r"[a-z0-9_-]{1,64}", nom or ""):
        cl.arreter("nom de lot refusé : « %s » (lettres minuscules, chiffres, _ et - seulement)" % nom)
    f = DOSSIER / (nom + ".txt")
    if not f.is_file():
        cl.arreter("lot introuvable : %s (lots du dépôt : %s)" % (f, ", ".join(sorted(p.stem for p in DOSSIER.glob("*.txt"))) or "aucun"))
    cmds = []
    for n, ligne in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
        c = ligne.strip()
        if not c or c.startswith("#"):
            continue
        c = c[1:] if c.startswith("/") else c
        mot = c.split(" ", 1)[0]
        if mot not in PERMIS:
            cl.arreter("ligne %d : commande « %s » pas permise dans un lot (permises : %s)" % (n, mot, ", ".join(sorted(PERMIS))))
        if mot == "setblock" and not SETBLOCK_AIR.match(c):
            cl.arreter("ligne %d : setblock seulement pour retirer un bloc : « setblock x y z air »" % n)
        if mot == "execute":
            m = re.search(r"\brun\s+(\S+)", c)
            if not m or m.group(1).lstrip("/") not in PERMIS - {"execute", "setblock"}:
                cl.arreter("ligne %d : « execute … run » sans commande permise derrière" % n)
        if len(c) > 256:
            cl.arreter("ligne %d : commande trop longue (256 caractères au plus)" % n)
        cmds.append(c)
    if not cmds:
        cl.arreter("lot vide : %s" % f)
    if len(cmds) > MAX_COMMANDES:
        cl.arreter("lot trop long : %d commandes (%d au plus)" % (len(cmds), MAX_COMMANDES))
    return f, cmds


def masque(t):
    return re.sub(r"\b\d{1,3}(\.\d{1,3}){3}\b", "x.x.x.x", t)


def reponses(texte, nb):
    """Lignes du journal écrites par la console en réponse (messages [Server] et erreurs de commande)."""
    garde = []
    for l in texte.splitlines():
        if re.search(r"password|login|register|logged in|joined the game|left the game|lost connection|<[^>]+>", l, re.I):
            continue
        if re.search(r"\[Server thread/(INFO|WARN|ERROR)\]", l) and re.search(
                r"Unknown or incomplete command|Incorrect argument|<--\[HERE\]|Expected|Invalid|\[Server\]|"
                r"Territoires|Drapeau|Zone |extraction|point\(s\) de quête|Impossible|Attention|[Ss]alle| — |Changed the block|"
                r"Could not set the block|Lieu|lieu", l):
            garde.append(masque(l.strip()))
    return garde[-(nb * 4 + 20):]


def main():
    lot = ENV.get("LOT", "").strip()
    f, cmds = lire_lot(lot)
    vrai = ENV.get("EXECUTER", "false").lower() == "true"
    cl.dire("# Commandes du serveur : lot « %s »" % lot)
    cl.dire("%d commande(s), lues dans `%s` (commit %s)." % (len(cmds), f, ENV.get("GITHUB_SHA", "?")[:8]))
    e = cl.etat()
    cl.dire("État du serveur : **%s**." % e)
    cl.dire("")
    cl.dire("```")
    for c in cmds:
        cl.dire(c)
    cl.dire("```")
    if not vrai:
        cl.dire("")
        cl.dire("**Essai à blanc : rien n'a été envoyé.** Pour envoyer : case « executer » + phrase « %s », depuis main." % PHRASE)
        cl.ecrire_resume()
        return
    if ENV.get("CONFIRMATION", "").strip() != PHRASE:
        cl.arreter("phrase de confirmation absente ou différente de « %s »" % PHRASE)
    if ENV.get("GITHUB_REF", "") != "refs/heads/main":
        cl.arreter("vraie exécution seulement depuis main (ici : %s)" % ENV.get("GITHUB_REF", "?"))
    if e != "running":
        cl.arreter("le serveur n'est pas en marche (état : %s)" % e)
    depuis = len(cl.journal())
    envoyees = 0
    for c in cmds:
        try:
            cl.api("POST", "/command", {"command": c})
            envoyees += 1
        except Exception as ex:
            cl.erreur("commande pas envoyée : %s (%s)" % (c, type(ex).__name__))
        time.sleep(PAUSE)
    time.sleep(5)
    texte = cl.journal()
    cl.dire("")
    cl.dire("**%d / %d commande(s) envoyée(s).** Réponses du serveur (journal, IP masquées) :" % (envoyees, len(cmds)))
    cl.dire("")
    cl.dire("```")
    lignes = reponses(texte[depuis:] if len(texte) >= depuis else texte, len(cmds))
    for l in lignes or ["(aucune réponse trouvée dans le journal : le vérifier dans la console du panel)"]:
        cl.dire(l)
    cl.dire("```")
    erreurs = [l for l in lignes if re.search(r"Unknown or incomplete command|Incorrect argument|<--\[HERE\]|Impossible", l)]
    if erreurs:
        cl.dire("")
        cl.dire("**%d réponse(s) en erreur** : voir ci-dessus." % len(erreurs))
    cl.ecrire_resume()
    if envoyees < len(cmds):
        sys.exit(1)


if __name__ == "__main__":
    main()
