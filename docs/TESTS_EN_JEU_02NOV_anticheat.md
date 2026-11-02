# Essais en jeu — chantier « anticheat » (audit du 02/11)

Spartan **2.10.0**, launcher **1.16.0**. Rien de tout cela n'a été testé en jeu ni fabriqué avec Gradle (bloqué sur la
machine de travail) : seules les parties en Java pur ont été compilées et essayées hors du jeu, et les tests du launcher
passent. La fabrication complète et les tests de Spartan seront faits par le contrôle de branche de GitHub.

## Ordre de mise en ligne (important)

1. Envoyer sur `main` : le serveur et le pack du launcher passent à Spartan 2.10.0 tout seuls (`serveur.yml`).
2. Juste après, publier le launcher : onglet Actions, « Launcher HeraliZ », « Run workflow », case **publier**. Les
   launchers Windows et Linux (AppImage) se mettent à jour seuls au démarrage ; Mac et `.deb` : le joueur est prévenu et
   retélécharge.
3. Un ou deux jours après (quand la plupart des joueurs ont relancé leur launcher), passer `ancienLauncher` de `"noter"`
   à `"alerte"` dans `serveur/config/spartan-common.toml` (section `[gardien]`).

Le launcher 1.16.0 peut être publié avant ou après le serveur sans rien casser : il ne passe au nouveau Gardien que si le
pack contient Spartan 2.10.0 ou plus récent ; sinon il garde l'ancien fonctionnement.

## Point 1 — agents Java (`allowedJavaAgents` vidé)

- [ ] Un joueur lancé normalement par le launcher : « ✅ Jeu validé » comme avant (aucun agent Java chez nous).
- [ ] (serveur d'essai) Un jeu lancé à la main avec `-javaagent:authlib-injector.jar` : refusé en mode strict
      (« jeu non identique »), raison « agent Java au lancement » pour le staff.

## Point 2 — Gardien vérifié par le serveur

Ce qui change pour le joueur : rien de visible. Le launcher garde une clé secrète pour lui et signe ses rapports ; le jeu
les transmet sans pouvoir les changer ; le serveur les vérifie.

- [ ] Joueur avec le launcher 1.16.0 : dans le fil Discord du joueur, « Jeu validé », champ « Gardien du launcher » :
      d'abord « pas encore de rapport prouvé », puis (à la connexion suivante ou après 1 min) « actif, rapports signés par
      le launcher vérifiés par le serveur ». Champ « Launcher » : « jeton valide, mods conformes, Gardien lié au jeton ».
- [ ] Rester 10 min en jeu : **aucune** alerte « Gardien » ni « Gardien muet ».
- [ ] Ouvrir Cheat Engine (ou le renommer `cheatengine.exe`) pendant la partie : alerte « Gardien » « programme de
      triche… : cheatengine » en moins de 30 s (comme avant).
- [ ] Fermer le launcher de force (gestionnaire des tâches) pendant la partie : « Gardien muet » environ 90 s après.
- [ ] Ancien launcher (1.15.3, sur un PC qui ne s'est pas mis à jour) : il joue normalement ; « Jeu validé » dit
      « ancien launcher : rapports non prouvés » ; aucune alerte tant que `ancienLauncher = "noter"`.
- [ ] Même joueur, après être venu une fois avec le 1.16.0, qui revient avec un 1.15.3 : une alerte « Gardien »
      « revient avec un ancien launcher ».
- [ ] Reconnexion (même partie, sans relancer le jeu) : pas d'alerte « rapport refusé ».
- [ ] Changement de monde (carte ↔ spawn), mort, réapparition : pas d'alerte.

Ce qui n'est pas couvert (limite de fond) : un programme qui imite tout le launcher avec le compte du joueur peut signer
ses propres rapports (le site ne sait pas reconnaître le vrai launcher). Le Gardien relève la marche, il ne la ferme pas.

## Point 36 (partie anticheat) — commandes

- [ ] `/banpc info <joueur>` (op 2) : IP / PC connus, comptes liés, historique (ce que donnait `/sbaninfo`).
- [ ] `/banpc <joueur> 1d test` (op 3) puis `/unbanpc <joueur>` : comme avant ; le panneau P (bouton « Ban PC ») aussi.
- [ ] Un modérateur op 2 voit `/banpc` dans la complétion mais ne peut taper que `info`.
- [ ] `/sbaninfo`, `/spartan selfscan`, `/spartan discordtest`, `/spartan esp bench` : n'existent plus. L'état des webhooks
      Discord reste écrit au démarrage (« Alertes Discord : actif ; sanctions publiques : … » dans le journal et le fil
      « Informations du staff »).
- Pour le chantier « boutique » : la clé `heraliz_grades.staff.cmd.sbaninfo` du panneau ne sert plus (commande retirée).
- `docs/COMMANDES_INVENTAIRE.md` est écrit par `outils/commandes.py` : à relancer une fois tous les chantiers fusionnés.

## Point 13 — ce que le jeu montre, angles morts du mode strict, limites du launcher

Ce qui change pour le joueur : rien de visible (mêmes contrôles, mêmes résultats pour un jeu honnête).

- [ ] Joueur honnête (Windows, Discord et Steam ouverts, overlay Steam ou Discord actif, OBS qui enregistre) :
      « ✅ Jeu validé », aucune alerte « affichage détourné ».
- [ ] `/spartan scan <joueur>` avec Cheat Engine ouvert (serveur d'essai) : « programme de triche… : cheatengine… »
      comme avant (le jeu ne reçoit plus que des empreintes).
- [ ] Appui sur ² ou Maj droite en jeu (sans écran de saisie) : alerte TricheMenu et capture, comme avant ; dans le
      chat, rien. Ajout d'une touche dans `touchesSurveillees` (ex. 260 = Inser) puis reconnexion : surveillée aussi.
- [ ] Mode strict : un joueur dont le jeu cesse de répondre aux contrôles en pleine partie (3 contrôles de suite, soit
      au moins ~5 min) est refusé. À vérifier sur le serveur d'essai seulement (couper le scanner demande un jeu modifié).
- [ ] Mod du launcher plus gros que 256 Mo ou illisible : un jar officiel verrouillé par un antivirus passe toujours ;
      un jar inconnu non lisible est refusé en mode strict (raison « impossible à vérifier »).

Ce qui reste impossible côté joueur (dit honnêtement) : tout ce qui tourne sur le PC du joueur peut mentir. Un Spartan
modifié avec soin peut répondre juste au défi et envoyer un rapport propre ; un programme qui imite le launcher avec le
compte du joueur obtient un jeton du site ; le jeu calcule lui-même l'empreinte de ses mods. Les bibliothèques de Forge
et de Minecraft (couches de lancement) ne sont toujours comparées qu'au client de référence (`/spartan reference
learn`), pas au jeu officiel : il faudrait que `outils/pack_launcher.py` écrive leurs empreintes dans `officiel.json`
(hors de ce chantier). Les vraies défenses sont côté serveur (point 14) et le Gardien prouvé (point 2).
