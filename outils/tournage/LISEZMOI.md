# Tournage des vidéos (TikTok)

Outils pour filmer de vraies scènes du jeu **sans écran ni carte graphique** (machine de tournage : conteneur Linux),
puis les monter en vidéos verticales. Rien de tout ça ne va sur le Ptero ni chez les joueurs.

## Ce qu'il faut sur la machine

- JDK 17, Python 3 (numpy, Pillow), ffmpeg, Xvfb, tmux, `pip install portablemc` ;
- accès réseau à Mojang et Forge (`piston-meta.mojang.com`, `piston-data.mojang.com`, `launcher.mojang.com`,
  `libraries.minecraft.net`, `resources.download.minecraft.net`, `maven.minecraftforge.net`, `files.minecraftforge.net`) ;
- nos mods fabriqués (`python outils/construire.py`), sans Spartan ;
- le mod de tournage : `cd outils/tournage/mod && ./gradlew build` (jar dans `build/libs/`).

Mise en place (une fois) :
- serveur Forge 1.20.1 dans `/root/srv` (installateur Forge `--installServer`), avec `serveur/config`,
  `serveur/defaultconfigs`, `serveur/tacz`, les jars de `livraison/mods serveur` (sauf Spartan) + le jar de tournage,
  une **copie** de `monde/HeraliZ` en `world` (jamais le monde du Ptero), `online-mode=false`. Dans
  `world/serverconfig` : `caracrea-server.toml` `openOnFirstJoin = false`, `crac-bases.toml` `[tuto] actif = false` ;
- un jeu par acteur dans `/root/mc/<pseudo>` (`mods` = `livraison/mods client` sans Spartan + jar de tournage, dossier
  `tacz`, `config/heraliz_interface-client.toml` `[ecran_titre] actif = false`, `options.txt` avec
  `pauseOnLostFocus:false`, `chatVisibility:2`, `mipmapLevels:0`) ; `lancer.sh` dans `/root/mc`.

## Tourner

```
LENT=6 python3 outils/tournage/directeur.py demarrer
LENT=6 python3 outils/tournage/directeur.py prise outils/tournage/scenes/prise1.json
python3 outils/tournage/montage.py outils/tournage/scenes/montage_execution.json
```

- **Ralenti** : la machine n'affiche que ~6 images par seconde ; le serveur et les jeux tournent `LENT` fois moins vite
  (`-Dtournage.lent`), la prise est remise à vitesse normale : ~30 images par seconde.
- **Scène** (`scenes/*.json`) : commandes console « avant » (téléportations, objets, camos), puis pour chaque acteur
  une liste d'événements au tick près : `touche` (nom d'un réglage de touche : `key.forward`, `key.tacz.shoot.desc`,
  `key.reanimation.execute`... ; `etat` bas / haut / tape), `regard` (angles ou `vers` un pseudo), `suivre` (garder la
  visée sur un pseudo), `marcher` (aller vers un pseudo jusqu'à `arret` blocs), `slot`, `cmd`, `hud`, `ips`,
  `camera` (caméra libre : clés `t`/`pos`/`yaw`/`pitch`, `viser` un pseudo ; le joueur continue d'agir et son corps est
  filmé de l'extérieur), `debug` (écrit dans le journal du jeu ce que vise le joueur).
- Chaque prise filme l'écran des acteurs listés dans `filmer` : un même jeu donne soit la vue de son joueur, soit une
  caméra libre. Plusieurs prises de la même scène = plusieurs angles.
- **Montage** (`montage.py`) : plans (source, début, durée, vitesse, zoom, secousse, flash, à-coups), gros
  sous-titres mot par mot (`*mot*` en jaune, `!mot!` en rouge), écran de fin (logo, « Discord en description »).
  Pas de son : la musique se met dans TikTok.

À savoir : l'exécution de Réanimation demande le viseur sur le joueur à terre (des herbes ou feuillages entre les deux
la bloquent) ; un acteur mort est remis en jeu au début de chaque prise.
