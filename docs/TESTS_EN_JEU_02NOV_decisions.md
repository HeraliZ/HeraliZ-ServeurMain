# Essais en jeu — chantier « décisions » (choix du propriétaire du 02/11)

Ce chantier applique les réponses du propriétaire aux questions laissées par les chantiers de l'audit du 02/11. Chaque
choix est un réglage (valeur par défaut = le choix) : on revient en arrière en changeant une valeur, sans code.

Rien n'a été testé en jeu ni fabriqué par Gradle (dépôt de Forge bloqué sur la machine de travail) : seule la syntaxe
des fichiers Java changés a été contrôlée (javac, sans Minecraft). La vraie compilation (et `verif_split`) sera faite
par le contrôle de branche de GitHub. `python outils/verifier_langues.py` : 0 erreur. `python outils/marchands.py
--controle` : aucune revente trop rentable. Outils relancés deux fois : aucun écart.

Modules touchés, tous déjà montés depuis `main` (65e64fad) par les chantiers de l'audit (pas de nouvelle version) :
**Clans 3.11.0**, **Quêtes 1.17.0** (`pnj`), **Boutique 1.4.0**, **Loot 0.14.6**, **Monde 1.0.10**, **Bases
1.30.0-beta**. Les textes nouveaux sont dans les jars des joueurs : serveur ET joueurs à jour ensemble.

## Ce qui change

| N° | Choix | Réglage (fichier) | Pour le joueur |
|---|---|---|---|
| 1 | Points PvP de saison : 40 par clan et par jour | `pvp.plafond_jour_clan` = 40 (`serveur/config/heralisz_clans/saison.json`, `/recharger saison`) | au-delà de 40 points de kills dans la journée, un kill ne rapporte plus rien au clan du tueur (la victime perd toujours) ; onglet Saison : « 40 points par jour au plus » |
| 2 | Quêtes de Varga dans les petites actions | `quete_principale` ajoutée à `plafonnees` (même fichier) | les 10 points d'une quête de Varga comptent dans les 50 points par joueur et par jour (avec zombies, conteneurs, ventes, fabrication) ; onglet Saison : « plafond commun de 50 points par jour » |
| 3 | Mila rachète une arme selon son état | `"selon_etat_min": 10` sur la règle des armes de `serveur/config/heraliz_quetes/pnj/mila.json` (écrit par `outils/marchands.py`, `RACHAT_ARMES_ETAT_MIN`) | une arme à 50 % se revend moitié prix, à 0 % 10 % du prix ; le prix montré dans l'écran de Mila suit |
| 4 | Cœur de clan à 5 000 hérals | `outils/marchands.py` (`MATERIAUX`) → `batisseur.json` | le Bâtisseur vend le cœur 5 000 au lieu de 1 000 ; le casino garde 20 000 capsules (fichier écrit à la main, voir les questions) |
| 5 | Jackpot nourri par 1 % des mises des machines à sous | `general.jackpot_part_mises` = 0.01 (`serveur/config/heralizcasino/casino.json`, `/casino reload`) | l'afficheur du jackpot et la télé (chaîne 2) montent avec les mises de Rotten, Olympe, Souffle du Loup et La Niche ; la partie qui atteint le gain maximal de sa machine gagne aussi le jackpot (annonce à tous), qui repart de 5 000 |
| 7 | Nourriture des zombies : bonne une fois sur trois | `NOURRITURE_TOUJOURS_BONNE = False` (`outils/loot_zombies.py`, tables relancées) | comme dans les conteneurs : bonne, pourrie ou périmée, un tiers chacune |
| 8 | Bonus de loot : temps compté sur la carte, hors AFK | `carte_seulement = true`, `afk_minutes = 3` (`[bonus_loot]` de `serverconfig/heralisz_utils-server.toml` du monde) | le temps ne s'use plus au spawn, au tutoriel, en match, ni après 3 min sans bouger ni tourner la tête ; messages « de jeu sur la carte » |
| 13 | Latence de TaCZ | `ServerHitboxLatencyMaxSaveMs = 350.0` (`serveur/config/tacz-common.toml`) | rien pour un joueur français ; un joueur à plus de 350 ms de ping voit quelques tirs ne pas compter |
| 15 | Objets à vie dans les coffres d'une base | `lifetimeItemsOwnerOnly = true` (`[heraliz]` de `crac-server.toml`, aussi dans `serveur/defaultconfigs/`) | un pilleur voit le couteau à vie dans le coffre mais ne peut pas le prendre (message dans la barre) ; le clan de la base le reprend comme avant |

## Déjà faits (vérifiés, rien changé)
- **6. Plafonds des PB créés à la main** : `site/extension/heraliz/src/Controllers/PbController.php`, `CREATION_MAX` =
  20 000 par opération, `CREATION_JOUR_MAX` = 50 000 sur 24 h glissantes ; au-delà, refus « plafond » et alerte du staff.
- **9. Sac plein bloqué sur le dos** : `lockIntoSlot = true` dans `serveur/config/backpacked.server.toml`.
- **10. Objet à vie jeté au sol** : rendu à celui qui l'a jeté au bout de 5 min (`ObjetsAVieServeur.finAuSol`,
  Tactique 2.43.0) ; sans lanceur connu, il reste au sol.

## Pas faits (choix du propriétaire : plus tard)
- **11. Ancien launcher en alerte** : le moment venu, passer `ancienLauncher` de `"noter"` à `"alerte"` dans
  `serveur/config/spartan-common.toml`, section `[gardien]` (un ou deux jours après la publication du launcher 1.16.0).
- **12. Renvoi en arrière de l'anticheat** : `movementSetback = false` reste tel quel (`spartan-common.toml`).
- **14. Nouvelles touches surveillées** : rien.

## Essais à faire (serveur d'essai, puis Ptero)

### 1. Plafond des kills (saison)
- [ ] Pour aller vite : `pvp.plafond_jour_clan` à 2 dans `saison.json`, `/recharger saison`. Un joueur d'un clan avec
      cœur tue 3 joueurs armés de clans différents (comptes différents, IP différentes) : +1, +1, puis 0 ;
      `/saison journal <tag>` montre « plafond du jour atteint ». Les clans des victimes perdent 1 point chacun.
- [ ] Remettre 40 : onglet Saison (K) › « Gagner des points » : « Tuer un joueur · victime armée ou en armure ;
      40 points par jour au plus » (fr, en, ru).
- [ ] Redémarrer le serveur en cours de journée : le compteur du jour est gardé ; à minuit (heure de Paris), il repart.

### 2. Quêtes de Varga plafonnées
- [ ] Un joueur qui a déjà fait 45 points de petites actions dans la journée (`/saison journal <tag>`) finit une quête
      de Varga : son clan ne reçoit que 5 points ; une quête de plus le même jour : 0. Le lendemain : 10.
- [ ] Onglet Saison : « Quête de Varga · une fois chacune ; plafond commun de 50 points par jour ».

### 3. Mila et l'état des armes
- [ ] Vendre à Mila une arme neuve : même prix qu'avant (ex. Rare 60).
- [ ] Une arme usée à ~50 % (état dans son infobulle) : l'écran de Mila propose la moitié (Rare ~30) ; à 0 % : 10 %
      (Rare 6, Commun 2). Le total versé correspond au prix montré.
- [ ] Armures, sacs, composants, objets de valeur : prix inchangés.
- [ ] Au Registre (staff), changer le prix d'une rareté des armes de Mila : la clé `selon_etat_min` reste dans
      `mila.json` sur le serveur.

### 4. Cœur de clan
- [ ] `/recharger quetes` (ou redémarrage) : le Bâtisseur vend le cœur de clan 5 000 hérals.

### 5. Jackpot du casino
- [ ] Jouer aux machines à sous : `/casino jackpot` monte de 1 % des mises (100 capsules misées = +1) ; l'afficheur du
      jackpot et la télé (chaîne 2) suivent dans la seconde.
- [ ] Pour voir un gain (serveur d'essai seulement) : `"max_win": 10.0` dans la section `rotten` de `casino.json`,
      `/casino reload`, jouer à Rotten avec une mise d'au moins 100 jusqu'à un gain plafonné à x10 : le gain arrive sur le
      compte de capsules avec le jackpot en plus, annonce dorée « remporte le JACKPOT » à tout le serveur, l'afficheur
      repart à 5 000. Remettre 5000.0 ensuite.
- [ ] Mise sous 100 : seulement une part du jackpot (mise / 100), le reste reste dans le jackpot.
- [ ] `jackpot_part_mises` à 0 : plus rien ne bouge, comme avant.

### 7. Nourriture des zombies
- [ ] Tuer des zombies jusqu'à trouver de la nourriture (sardines, thon, haricots en boîte ; ration MRE pour les militaires) : environ
      un tiers bonne, un tiers pourrie, un tiers périmée (infobulle), et l'état ne change plus une fois ramassée.

### 8. Bonus de loot
- [ ] Activer un Bonus de loot au spawn : message « … de jeu sur la carte (le temps s'arrête au spawn et quand tu ne
      bouges plus) ».
- [ ] Rester 10 min au spawn, se reconnecter : le temps restant n'a pas baissé.
- [ ] Sur la carte, jouer 10 min : il baisse de 10 min. Rester immobile sans toucher la souris 10 min : il ne baisse que
      des 3 premières minutes ; il repart dès qu'on bouge.
- [ ] Le bonus s'applique toujours aux conteneurs ouverts (effet inchangé).

### 13. Latence de TaCZ
- [ ] Après redémarrage : tirs normaux (pistolet, fusil, sniper) sur un joueur qui court, avec un ping normal : les
      touches comptent comme avant.

### 15. Objets à vie dans les coffres des bases (avec un compte SANS droits de staff)
- [ ] Le chef d'une base range un couteau à vie dans un coffre de sa base ; il le reprend (clic, Maj + clic).
- [ ] Un membre du clan avec le droit « Ouvrir les coffres » le reprend aussi.
- [ ] Un pilleur d'un autre clan ouvre ce coffre (base raidée, ou protection des premiers jours finie) : il voit le
      couteau ; clic, Maj + clic, touches 1 à 9, double clic sur une pile du même objet, touche de jet : rien ne sort,
      message jaune dans la barre ; il prend tout le reste du coffre comme avant.
- [ ] Le pilleur fait sauter le coffre au C4 : le couteau va au chef de la base (comme depuis d7c8d2ef), pas au sol.
- [ ] `lifetimeItemsOwnerOnly = false` dans `crac-server.toml` du monde : le pilleur le prend, comme avant.

## Questions pour le propriétaire
1. **Cœur de clan au casino** : il coûte toujours 20 000 capsules à la caisse du casino (fichier
   `serveur/config/heralizcasino/casino.json`, écrit à la main, aucun outil). Le monter aussi (par exemple 100 000,
   même rapport que 1 000 → 5 000), ou le laisser ?
2. **Jackpot** : le jackpot n'est gagné que par une partie qui atteint le gain maximal de sa machine (x5 000 à Rotten et
   Olympe, x6 750 à La Niche, x10 000 au Souffle du Loup) : c'est très rare, il peut monter longtemps. Garder cette
   règle, ou le faire tomber plus souvent (un gain à partir de x1 000, par exemple) ?
3. **Sac dans un coffre de base** : un sac rangé dans le coffre qui contient un objet à vie se prend toujours (le bloquer
   protégerait tout un sac avec un couteau dedans). D'accord ?
