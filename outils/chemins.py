"""Chemins de la machine (différents sur chaque PC). Lus dans outils/chemins_local.json, qui n'est PAS dans Git ;
sinon valeurs par défaut. Exemple de chemins_local.json :

    {
      "lanceur_curseforge": "C:/Users/<vous>/curseforge/minecraft/Install",
      "serveur_local": "C:/Users/<vous>/HeraliZ-serveur-local",
      "java_home": "C:/Program Files/Java/jdk-17"
    }

- lanceur_curseforge : dossier Install du lanceur CurseForge (son dossier libraries/ contient Minecraft 1.20.1 en noms
  SRG et Forge 47.4.0 côté client) : il existe dès qu'on a lancé une fois un profil Forge 1.20.1.
- serveur_local : un dossier serveur Mohist 1.20.1 démarré une fois (on n'utilise que son dossier libraries/ : Minecraft
  serveur en noms SRG et Forge 47.3.12). Le plus simple : copier une fois le dossier libraries/ du Ptero par SFTP.
- java_home : JDK 17.
Les variables d'environnement HERALIZ_LANCEUR, HERALIZ_SERVEUR et JAVA_HOME passent avant le fichier.
"""
import json
import os
import shutil
from pathlib import Path

DEPOT = Path(__file__).resolve().parents[1]
_FICHIER = DEPOT / "outils" / "chemins_local.json"
_conf = json.loads(_FICHIER.read_text(encoding="utf-8")) if _FICHIER.exists() else {}

LANCEUR = Path(os.environ.get("HERALIZ_LANCEUR") or _conf.get("lanceur_curseforge")
               or Path.home() / "curseforge" / "minecraft" / "Install")
SERVEUR = Path(os.environ.get("HERALIZ_SERVEUR") or _conf.get("serveur_local") or DEPOT / "_serveur_local")
JAVA_HOME = Path(os.environ.get("JAVA_HOME") or _conf.get("java_home") or r"C:\Program Files\Java\jdk-17")

MODS = DEPOT / "modules"
MODS_TIERS = DEPOT / "serveur" / "mods-tiers"
MODS_TIERS_CLIENT = DEPOT / "client" / "mods-tiers"
GECKOLIB = MODS_TIERS / "geckolib222-forge-1.20.1-4.4.7.jar"
DIST = DEPOT / "dist"
LIVRAISON = DEPOT / "livraison"


def javap():
    """Chemin de javap (JDK 17)."""
    for c in (JAVA_HOME / "bin" / "javap.exe", JAVA_HOME / "bin" / "javap"):
        if c.exists():
            return str(c)
    return shutil.which("javap") or "javap"
