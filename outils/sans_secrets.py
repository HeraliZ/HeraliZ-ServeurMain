"""Lance une commande sans les secrets de la mise en ligne dans son environnement.

Sert aux étapes des workflows qui exécutent du code d'autres auteurs (Gradle et ses dépendances, Nebula et ses paquets
npm, installeur de Forge) : ce code ne doit jamais pouvoir lire un mot de passe, une clé ou un webhook.

    python outils/sans_secrets.py <commande> [arguments...]

Depuis Python : ``env_propre()`` rend une copie de l'environnement sans ces variables.
"""
import os
import sys

# Noms exacts et débuts de noms des variables qui portent des secrets (section 8 de CLAUDE.md).
NOMS = {"NOMS_INTERDITS", "LAUNCHER_SIGN_KEY", "SSHPASS", "LFTP_PASSWORD", "GITHUB_TOKEN", "GH_TOKEN",
        "ACTIONS_RUNTIME_TOKEN", "ACTIONS_ID_TOKEN_REQUEST_TOKEN"}
DEBUTS = ("PTERO_", "SITE_SSH", "SITE_BDD", "SPARTAN_", "LITEBANS_", "HERALIBOT_", "DISCORD_")


def secret(nom: str) -> bool:
    return nom in NOMS or nom.startswith(DEBUTS)


def env_propre() -> dict:
    return {k: v for k, v in os.environ.items() if not secret(k)}


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("usage : python outils/sans_secrets.py <commande> [arguments...]")
    os.execvpe(sys.argv[1], sys.argv[1:], env_propre())
