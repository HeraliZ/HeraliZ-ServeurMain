"""Prépare un serveur de test complet sur un PC (25/10), à partir de la DERNIÈRE fabrication (livraison/) et des réglages
du dépôt (serveur/), pour essayer le nouveau serveur avant toute mise en ligne.

    python outils/serveur_local.py <dossier vide ou à mettre à jour> [--banc]

Ce que fait l'outil (rien n'est supprimé hors de `mods/` du dossier cible) :
- `mods/` = exactement `livraison/mods serveur/` (anciens jars retirés), plus le module `banc` (bots de charge) avec --banc ;
- `plugins/` (jars + réglages, sans données), `config/`, `defaultconfigs/`, `tacz/`, `mohist-config/`, `heraliz_clashwar/` ;
- `server.properties`, `bukkit.yml`, `spigot.yml`, `commands.yml`, `server-icon.png` (seulement s'ils n'existent pas encore :
  un réglage changé à la main dans le dossier cible est gardé) ;
- `LISEZ-MOI.txt` : la suite (jar Mohist, EULA, monde, démarrage).
Le monde n'est jamais touché : copiez celui que vous voulez (par exemple `dayz_map` du Ptero) dans le dossier cible.
"""
import shutil
import sys
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
LIV = RACINE / "livraison"
SRV = RACINE / "serveur"


def copier_dossier(src, dst):
    if not src.exists():
        return 0
    n = 0
    for f in src.rglob("*"):
        if f.is_file():
            cible = dst / f.relative_to(src)
            cible.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(f, cible)
            n += 1
    return n


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    cible = Path(sys.argv[1]).resolve()
    cible.mkdir(parents=True, exist_ok=True)
    jars = sorted((LIV / "mods serveur").glob("*.jar"))
    if not jars:
        sys.exit("livraison/mods serveur est vide : lancer d'abord python outils/construire.py")
    mods = cible / "mods"
    if mods.exists():
        for f in mods.glob("*.jar"):
            f.unlink()  # jamais deux versions du même mod (les anciens jars ont les mêmes identifiants)
    mods.mkdir(exist_ok=True)
    for j in jars:
        shutil.copy2(j, mods / j.name)
    if "--banc" in sys.argv:
        for j in (LIV / "banc").glob("*.jar"):
            shutil.copy2(j, mods / j.name)
    n = sum(copier_dossier(SRV / d, cible / d) for d in ("config", "defaultconfigs", "tacz", "mohist-config", "heraliz_clashwar"))
    n += copier_dossier(LIV / "plugins", cible / "plugins")
    officiel = LIV / "spartan" / "officiel.json"
    if officiel.exists():
        (cible / "config" / "spartan").mkdir(parents=True, exist_ok=True)
        shutil.copy2(officiel, cible / "config" / "spartan" / "officiel.json")
    for f in ("server.properties", "bukkit.yml", "spigot.yml", "commands.yml", "server-icon.png"):
        if (SRV / f).exists() and not (cible / f).exists():
            shutil.copy2(SRV / f, cible / f)
    (cible / "LISEZ-MOI.txt").write_text(LISEZ_MOI, encoding="utf-8")
    print("[serveur_local] %d mods, %d fichiers de réglages et de plugins dans %s" % (len(list(mods.glob("*.jar"))), n, cible))


LISEZ_MOI = """Serveur de test HeraliZ (préparé par outils/serveur_local.py)

1. Mettre le jar Mohist du Ptero (mohist-1.20.1-916-server.jar, même version que la prod) dans ce dossier.
   Sans Mohist (Forge seul), le serveur démarre, mais sans les plugins (AuthMe, LiteBans...) : en mode hors ligne,
   tous les droits du staff (panel P, /staff, /grade, éditeurs, /bon) sont alors refusés, même aux op (AuthMe
   obligatoire). Pour tester le staff : Mohist + AuthMe (/register puis /login).
2. Lire et accepter l'EULA de Minecraft : créer eula.txt avec la ligne eula=true.
3. Monde : copier ici le dossier du monde à tester (par exemple dayz_map du Ptero, serveur arrêté) ;
   le nom doit être celui de level-name dans server.properties (dayz_map).
4. Démarrer :  java -Xms8G -Xmx8G -XX:+UseG1GC -jar mohist-1.20.1-916-server.jar nogui
   Les réglages du monde (serverconfig) viennent de defaultconfigs/ au premier démarrage d'un monde neuf ;
   pour un monde déjà existant, recopier defaultconfigs/*.toml dans <monde>/serverconfig/ avant de démarrer.
5. Joueurs : le jeu doit avoir les jars de livraison/mods client/ de la MÊME fabrication, et le dossier tacz/.
6. Pour un test sans le launcher : dans config/spartan-common.toml, mettre tokenRequired = false et
   captureRequired = false (sinon Spartan refuse les connexions qui ne viennent pas du launcher HeraliZ).
   Ne jamais envoyer ce fichier modifié sur le serveur en ligne.
7. Bots de charge (avec --banc) : /bots scenario 10 5   (10 bots, 5 minutes, rapport dans diag/).
   Liste des essais : docs/TESTS_EN_JEU_25OCT.md du dépôt.
"""

if __name__ == "__main__":
    main()
