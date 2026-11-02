# Essais en jeu — chantier « commandes et noyau » (audit du 02/11, vague 2)

Points de l'audit : 32, 33, 34, 36 (commandes : `docs/audit_02nov/commandes.md`), restes du noyau (mods-1 §16 et §21,
point 38) et suite du point 3 (Événements). Versions : Noyau 1.0.13, Monde 1.0.11, Boutique 1.4.1, Quêtes 1.17.1,
Loot 0.14.7, CaraCrea 1.6.6, Clans 3.11.1, Bases 1.30.1-beta, ClashWar 1.1.1, Événements 1.1.4, Tactique 2.43.1,
Spartan 2.10.1, Sacs 3.0.1-beta.4-heralisz23.

**Rien n'a été testé en jeu ni compilé avec Gradle** (le dépôt de Forge est bloqué sur la machine de travail). Contrôlé :
la syntaxe Java de tous les fichiers changés (javac, sans Minecraft) ; les types des nouvelles classes du noyau
(`CommandesRetirees`, `CommandesBukkit`, `Rechargements`, `Maintenance`, `NoyauConfig`) avec javac et des « bouchons » de
Minecraft, Forge et Brigadier ; `python outils/verifier_langues.py` : 0 erreur. La vraie compilation se fera au contrôle
de branche de GitHub. **Le comportement de Mohist n'a pas pu être lu** (pas de jar de Mohist dans le dépôt) : retrait de
commandes de l'arbre et de la table de Bukkit, `PlayerCommandSendEvent`, `commands.yml` avec `[]` sont ceux de
CraftBukkit, à confirmer sur le serveur d'essai.

Mise en ligne : complète (jars + `serveur/spigot.yml`, `serveur/commands.yml`, `serveur/defaultconfigs/` recopié dans
`dayz_map/serverconfig/`), puis redémarrage. Un joueur doit relancer le launcher (nouvelles versions des jars).

## Ce qui change pour le joueur

- Le canal des pings se règle par **`/canal`** (`/canal <nom>`, `/canal clan`, `/canal aide`) : `/ping canal` ne marchait
  pas (caché par le `/ping` de Mohist). `/canal` est permis sur la carte.
- La liste des commandes (touche Tab, complétion) est **beaucoup plus courte** : plus de doublons `minecraft:…`, plus de
  `/matmossettings`, `/tctcoresettings`, `/kiwi`, `/teammsg`, `/trigger`, `/vehicules`…
- `/logout` (et toute commande de plugin) est refusée **en combat, à terre et sur la carte**, comme les autres.
- `/matmossettings` n'existe plus : c'était le seul moyen d'ouvrir le menu de MAtmos (couper ses sons de musique, grotte,
  Nether, eau, vent, inventaire, joueur par joueur). Le volume de ces sons se règle toujours dans les options du jeu
  (Sons › Ambiance et Joueurs). Pour le rendre : enlever `"matmossettings"` de `[commandes] retirees`.
- `/redemarrage` : « dans 5 min » / « 30 s » dans la langue du joueur, et « si une mise à jour est sortie, relance le
  launcher ».

## À vérifier (dans l'ordre)

### Liste des commandes (points 36 et 34)
- [ ] Journal du démarrage : « [noyau] commandes retirées (démarrage) : … » avec /kiwi, /tacz, /seed, /xp… (35 noms au
      plus) ; « [noyau] liste des commandes : 178 nom(s) caché(s) … ; commandes des plugins soumises aux règles … ».
- [ ] Op 4 : `/kiwi`, `/tacz`, `/seed`, `/xp`, `/team` → « Commande inconnue. » ; même chose dans la console du panel
      (`kiwi dev_env_rules …` n'existe plus). `/tp`, `/gamemode`, `/give`, `/time`, `/kill`, `/effect`, `/forge tps`,
      `/curios clear <joueur>` marchent toujours.
- [ ] Chat, touche Tab après « / » : plus aucun nom `minecraft:…`, `worldedit:…`, `litebans:…` ; plus de `/lban`, `/tban`,
      `/hist`, `/namehist`, `//re`, `/br`, `/perms`, `/csf`, `/pl`, `/ver`… **mais** `/namehist <joueur>`, `/lban`, `//re`
      tapés en entier marchent toujours.
- [ ] Panneau P, onglet « Commandes du Fondateur » : environ 280 commandes (avant ~500) ; les commandes déjà données à
      un grade restent dans la liste.
- [ ] Joueur normal : Tab après « / » : ni `/plugins`… (voir la question LuckPerms), ni `/vehicules`, ni
      `/matmossettings`.
- [ ] `/reload`, `/rl`, `/restart` → « Commande inconnue. » (op 4). `/minecraft:reload` (datapacks) existe encore.
- [ ] Réglage : retirer `"seed"` de `[commandes] retirees` du serveur d'essai : le journal le dit au prochain démarrage
      (une commande enlevée de la liste ne revient qu'au redémarrage). Ajouter un nom à `cachees` : pris à la
      reconnexion suivante, sans redémarrer.

### `/canal` (point 33)
- [ ] Deux joueurs sans clan commun, compte non op : `/canal test` chacun → « Canal de pings : « test » » ; ils voient
      les pings l'un de l'autre. `/canal` → le canal et qui y est ; `/canal clan` → retour au clan.
- [ ] Sur la carte (survie) : `/canal test` passe (permis), `/canal aide` donne l'aide des pings.
- [ ] Staff : `/canal admin` (réglages, compteurs), `/canal admin duree 10`. Un joueur : `/canal admin` → erreur, il ne
      rejoint pas un canal « admin ».
- [ ] `/ping` : c'est celui de Mohist (latence ; refusé à qui n'a pas la permission).

### Commandes des plugins dans le filtre (point 32)
- [ ] Joueur en combat (tag) : `/logout` → « Impossible en combat · N s » ; `/msg`, `/r`, `/l <mdp>` passent.
- [ ] Joueur à terre : `/logout` → « Impossible à terre. »
- [ ] Joueur sur la carte (survie, non op) : `/logout`, `/changepassword` → « /logout : seulement au spawn, pas sur la
      carte. » ; au spawn : marchent.
- [ ] **Connexion** d'un joueur dont le personnage est sur la carte : `/login`, `/l`, un `/captcha <code>` demandé
      après des erreurs et un code `/2fa` ou `/totp` passent (sinon : bloqué dehors).
- [ ] Staff (op 2) sur la carte : LiteBans `/warn`, `/kick` passent (staff exempté de la règle de la carte, comme avant).
      Attention : un membre du staff **tagué en combat** voit maintenant aussi ses commandes de plugins refusées
      (comme ses commandes de mods ; `operateurs_exemptes = true` dans `[combat]` pour l'éviter).

### Outils d'essai et doublons retirés (point 36)
- [ ] `/zombies banc`, `/heralizloot ouvrir`, `/unlockbackpack`, `/caracrea`, `/staff vanish`, `/staff nametag`,
      `/heraliz loot add|list|remove`, `/casino rtp`, `/diag start`, `/diag auto`, `/diag seuil`, `/diag profil stop`
      → inconnues. Des zombies d'un ancien banc : `/kill @e[tag=heraliz_banc_zombie]`.
- [ ] `/perso` (au spawn) ouvre le créateur ; staff : `/perso open|random|reset <joueur>`. `/voix mute <joueur>` (sans
      durée : sans fin), `/voix unmute <joueur>`.
- [ ] `/wipe <joueur> confirmer` : son personnage CaraCrea est bien remis à zéro (tape maintenant « perso reset »).
- [ ] Panneau P : « Se cacher » et « Cacher son nom » marchent comme avant ; `/staff panel` aussi.
- [ ] `/heraliz edit <caisse>` : ajouter un objet depuis l'inventaire, retirer une ligne, enregistrer.
- [ ] `/casino stats` : chiffres réels puis redistribution prévue machine par machine (l'ancien `/casino rtp`).
- [ ] `/quete finir` : refusée à un op 2, marche en op 4. `/vitres pourquoi` et `/cracbuild` : op 4 seulement.
- [ ] Sans le banc : `/quete donner`, `/journalieres essai`, `/heralizloot zombies simuler` → inconnues. Serveur d'essai
      préparé avec `python outils/serveur_local.py <dossier> --banc` : elles marchent comme avant (scripts de capture
      `journal.json`, `tour_complet.json`, `journalieres.json`).
- [ ] `/vehicules liste` : refusée à un joueur, marche pour le staff.

### `/recharger` (point 36)
- [ ] `/recharger` (op 4 ou console) : chaque module répond (« N PNJ relus », « Barème de la saison relu », « purge.json
      relu », « coffres.json relu », « Comptes… », « Modpack officiel relu »…), puis « [Recharger] 16 rechargement(s)
      faits ». La complétion de `/recharger ` propose les 16 noms.
- [ ] `/recharger casino` seul ; `/recharger inconnu` → « Sans effet ou refusés : inconnu ».
- [ ] Changer `config/crac/coffres.json` puis `/recharger` : relu (avant, /recharger l'oubliait).
- [ ] Bouton « Recharger » du panneau P : même résultat.
- [ ] Les anciennes (`/quete recharger`, `/heraliz reload`, `/eco recharger`, `/spartan officiel`…) → inconnues ou erreur
      de syntaxe ; `/salle recharger <nom>` (remettre le loot d'une salle) existe toujours.
- À savoir : `/recharger` demande le niveau 4 (comme avant). Plusieurs anciennes relectures étaient ouvertes au niveau 2
  (quêtes, caisses, casino, raretés, tables de loot, territoires, clans, hérals, zones, ClashWar) ou 3 (modpack officiel
  de l'anticheat) : un membre du staff de niveau 2 ou 3 ne peut plus relire les fichiers (le bouton « Recharger » du
  panneau était déjà au niveau 4).

### Noyau
- [ ] `/redemarrage 1` sur un client anglais et un russe : « in 1 min » / « через 1 мин », titres « in 30 s » / « через
      30 с » ; annonce avec « restart the launcher » ; `/redemarrage annuler`.
- [ ] `/diag profil 30`, `/diag stop`, `/diag mesure 15` comme avant. Banc de GitHub : il lance `diag profil {s}`.
- [ ] Planque, coffre VIP, hélicoptère : un joueur debout n'est jamais vu « à terre » (plus de lecture de PlayerRevive).
- [ ] Largages et hélicoptère : pendant un ClashWar, aucun ; après la fin du tournoi, ils reprennent (Événements lit
      `Tournois.enCours()`).

## Pas fait, ou pour un autre chantier
- `/casino salle clochard|vip` (construit les salles en écrasant ce qui est devant) gardée : question ci-dessous.
- Zone VIP du casino (`/casino zone vip`) et `/zonevip` : deux systèmes gardés (question de l'audit).
- `/eco sauvegarder` gardée (le chantier « économie » s'en sert pour lever un blocage).
- `/vitres` gardée en op 4 (le réglage des vitres y renvoie) plutôt que retirée.
- `ARCHITECTURE.md` parle encore de « `/hz recharger [module]` » (plan du 24/10) : c'est `/recharger [module]`.
- Le noyau ne sait pas, sous Forge seul (sans Mohist), cacher des noms de la liste : `cachees` ne marche que sur le
  serveur hybride (sans effet sur le serveur d'essai Forge).

## Questions pour le propriétaire
1. **LuckPerms** (dans la base du serveur, pas dans le dépôt) : mettre `bukkit.command.plugins` et
   `bukkit.command.version` à faux pour le groupe `default` ? (aujourd'hui tout joueur lit la liste des plugins et leurs
   versions). Et `authme.player.unregister`, `authme.player.email` à faux ?
2. `/casino salle clochard|vip` : les salles sont-elles déjà posées ? Si oui, on retire la commande.
3. `/vitres pourquoi` : on la garde (op 4) ou on la retire ?
4. Le staff doit-il garder ses commandes de plugins (LiteBans) **en combat** ? (aujourd'hui non, comme pour les autres
   commandes : `operateurs_exemptes`).
5. `/recharger` reste au niveau 4 : faut-il qu'un membre du staff de niveau 2 puisse relire les fichiers ? (si oui, on
   ouvre `/recharger <module>` au niveau 2 pour les modules qui l'étaient).
6. `/matmossettings` (menu des sons d'ambiance de MAtmos, ouvert à tous) : retirée comme demandé (point 34) ; la
   rendre aux joueurs ? (une ligne du réglage)
