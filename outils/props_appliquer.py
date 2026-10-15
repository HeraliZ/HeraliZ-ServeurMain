"""Joue un plan de props (outils/props_carte.py plan) sur le serveur, par la console du Ptero (API « client » de
Pterodactyl), ou l'annule. Ne lit ni n'écrit aucun fichier du monde : ce sont les commandes du plan, jouées par le
serveur lui-même, qui changent les blocs, et chacune ne change un bloc que s'il est ENCORE celui relevé dans la copie.

    python outils/props_appliquer.py <dossier du plan> [--etapes a,b] [--lots 1-20] [--pause 5] [--envoyer]
                                     [--annuler] [--journal props_carte/journal.txt] [--budget-minutes 330]
    python outils/props_appliquer.py <dossier du plan> --verifier <latest.log> [--journal ...]

Sans --envoyer : essai à blanc (affiche ce qui serait envoyé, rien ne part). Variables d'environnement (secrets du
workflow, jamais dans un fichier) : PTERO_URL, PTERO_SERVER_ID, PTERO_API_KEY.

Le journal (une ligne par lot joué ou annulé) permet de reprendre un envoi coupé et de tout annuler, du dernier lot au
premier. Lignes du dossier du plan : lots/NNN_etape.txt et annulation/NNN_etape.txt ; dans un lot, « #avant cmd » est
envoyé avant les commandes (chargement des secteurs : /forceload add), « #attendre N » attend N secondes, « #apres
cmd » est envoyé à la fin (/forceload remove) ; les autres lignes en « # » sont des commentaires.
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ESPACEMENT = 0.3      # secondes entre deux commandes (l'API du panel limite le nombre d'appels par minute)


def lire_lot(f):
    avant, apres, cmds, attente = [], [], [], 0
    for ligne in Path(f).read_text(encoding="utf-8").splitlines():
        ligne = ligne.strip()
        if not ligne:
            continue
        if ligne.startswith("#avant "):
            avant.append(ligne[7:])
        elif ligne.startswith("#apres "):
            apres.append(ligne[7:])
        elif ligne.startswith("#attendre "):
            attente = max(attente, float(ligne[10:]))
        elif not ligne.startswith("#"):
            cmds.append(ligne)
    return avant, attente, cmds, apres


def journal_lire(chemin):
    """{lot: « joue » | « annule »} d'après le journal (dernier état de chaque lot)."""
    etat = {}
    p = Path(chemin)
    if p.is_file():
        for ligne in p.read_text(encoding="utf-8").splitlines():
            m = re.match(r"^\S+ \S+ (joue|annule) (\S+)", ligne)
            if m:
                etat[m.group(2)] = m.group(1)
    return etat


def journal_ecrire(chemin, action, lot, detail):
    p = Path(chemin)
    p.parent.mkdir(parents=True, exist_ok=True)
    with open(p, "a", encoding="utf-8") as f:
        f.write("%s %s %s %s\n" % (datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M"), action, lot, detail))


class Console:
    def __init__(self, envoyer):
        self.envoyer = envoyer
        url = (os.environ.get("PTERO_URL") or "").rstrip("/")
        sid = os.environ.get("PTERO_SERVER_ID") or ""
        self.cle = os.environ.get("PTERO_API_KEY") or ""
        self.api = "%s/api/client/servers/%s" % (url, sid)
        if envoyer and not (url and sid and self.cle):
            sys.exit("Secrets du Ptero absents (PTERO_URL, PTERO_SERVER_ID, PTERO_API_KEY) : rien n'est envoyé.")
        self.envoyees = 0
        self.essais = 0

    def _requete(self, chemin, donnees=None):
        req = urllib.request.Request(self.api + chemin, data=json.dumps(donnees).encode() if donnees is not None else None,
                                     method="POST" if donnees is not None else "GET")
        req.add_header("Authorization", "Bearer " + self.cle)
        req.add_header("Content-Type", "application/json")
        req.add_header("Accept", "Application/vnd.pterodactyl.v1+json")
        return urllib.request.urlopen(req, timeout=30)

    def etat(self):
        if not self.envoyer:
            return "essai"
        try:
            with self._requete("/resources") as r:
                return json.load(r)["attributes"]["current_state"]
        except Exception as ex:  # noqa: BLE001
            return "inconnu (%s)" % type(ex).__name__

    def commande(self, cmd):
        if not self.envoyer:
            self.essais += 1
            if self.essais <= 3:
                print("    [essai] " + cmd)
            return True
        for essai in range(6):
            try:
                with self._requete("/command", {"command": cmd}) as r:
                    r.read()
                self.envoyees += 1
                time.sleep(ESPACEMENT)
                return True
            except urllib.error.HTTPError as ex:
                if ex.code == 429:                      # trop d'appels : on attend
                    time.sleep(10 * (essai + 1))
                    continue
                if ex.code in (502, 504) or ex.code >= 500:
                    time.sleep(5 * (essai + 1))
                    continue
                print("::error::Le panel refuse la commande (HTTP %d) : arrêt." % ex.code)
                return False
            except (urllib.error.URLError, TimeoutError):
                time.sleep(5 * (essai + 1))
        print("::error::Le panel ne répond plus : arrêt.")
        return False


def plage(texte, n):
    """« 1-20,25 » -> ensemble de numéros (1 = premier lot)."""
    if not texte:
        return set(range(1, n + 1))
    out = set()
    for morceau in texte.split(","):
        morceau = morceau.strip()
        if "-" in morceau:
            a, b = morceau.split("-", 1)
            out |= set(range(int(a), int(b) + 1))
        elif morceau:
            out.add(int(morceau))
    return out


def verifier(dossier, log, journal):
    """Compte, pour chaque lot du journal, les blocs que le serveur dit avoir changés (journal du serveur)."""
    texte = Path(log).read_text(encoding="utf-8", errors="replace")
    changes = set(re.findall(r"Changed the block at (-?\d+), (-?\d+), (-?\d+)", texte))
    etat = journal_lire(journal)
    lignes = ["| lot | état | commandes | blocs confirmés par le serveur |", "|---|---|---|---|"]
    for lot in sorted(etat):
        sous = "lots" if etat[lot] == "joue" else "annulation"
        f = Path(dossier, sous, lot + ".txt")
        if not f.is_file():
            continue
        _a, _t, cmds, _p = lire_lot(f)
        pos = [m for c in cmds for m in re.findall(r"run setblock (-?\d+) (-?\d+) (-?\d+) ", c)]
        ok = sum(1 for p in pos if p in changes)
        lignes.append("| %s | %s | %d | %d / %d |" % (lot, etat[lot], len(cmds), ok, len(pos)))
    print("\n".join(lignes))
    print("\n(un bloc non confirmé : sa commande n'a rien changé, le bloc n'était plus celui relevé ; ou le journal du "
          "serveur a tourné entre-temps.)")


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        sys.exit(1)

    def opt(nom, defaut=None):
        return args[args.index(nom) + 1] if nom in args else defaut
    dossier = Path(args[0])
    journal = opt("--journal", str(dossier / "journal.txt"))
    if "--verifier" in args:
        verifier(dossier, opt("--verifier"), journal)
        return
    annuler = "--annuler" in args
    envoyer = "--envoyer" in args
    pause = float(opt("--pause", "5"))
    budget = time.time() + 60 * float(opt("--budget-minutes", "330"))
    tous = sorted(p.stem for p in (dossier / "lots").glob("*.txt"))
    etat = journal_lire(journal)
    console = Console(envoyer)
    print("Serveur : %s" % console.etat())
    if envoyer and console.etat() not in ("running", "starting"):
        sys.exit("::error::Le serveur n'est pas en marche : rien n'est envoyé.")
    if annuler:
        a_jouer = [l for l in reversed(tous) if etat.get(l) == "joue"]
        sous, action = "annulation", "annule"
    else:
        etapes = set((opt("--etapes") or "correction,couverture,equilibrage,cfm").split(","))
        nums = plage(opt("--lots"), len(tous))
        a_jouer = []
        for k, l in enumerate(tous, 1):
            etape = l.split("_", 1)[1].replace("_parts_avant", "").replace("_parts", "")
            if k in nums and etape in etapes and etat.get(l) != "joue":
                a_jouer.append(l)
        sous, action = "lots", "joue"
    print("%d lot(s) à %s%s." % (len(a_jouer), "annuler" if annuler else "jouer", "" if envoyer else " (ESSAI À BLANC : rien n'est envoyé)"))
    faits = 0
    for l in a_jouer:
        if time.time() > budget:
            print("::warning::Temps écoulé : arrêt propre après %d lot(s) ; relancer pour continuer (le journal reprend où il en est)." % faits)
            break
        avant, attente, cmds, apres = lire_lot(dossier / sous / (l + ".txt"))
        print("== %s %s : %d commande(s)" % ("annulation" if annuler else "lot", l, len(cmds)))
        console.essais = 0
        ok = all(console.commande(c) for c in avant)
        if ok and attente and envoyer:
            time.sleep(attente)
        ok = ok and all(console.commande(c) for c in cmds)
        # on libère les secteurs même si un envoi a échoué
        for c in apres:
            console.commande(c)
        if not ok:
            journal_ecrire(journal, "echec", l, "envoi interrompu") if envoyer else None
            sys.exit(1)
        if envoyer:
            journal_ecrire(journal, action, l, "%d commande(s)" % len(cmds))
            time.sleep(pause)
        faits += 1
    print("%d lot(s) %s, %d commande(s) envoyée(s)." % (faits, "annulé(s)" if annuler else "joué(s)", console.envoyees))


if __name__ == "__main__":
    main()
