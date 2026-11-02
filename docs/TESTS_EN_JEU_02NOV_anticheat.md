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
