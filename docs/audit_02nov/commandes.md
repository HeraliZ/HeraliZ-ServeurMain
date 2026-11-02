# Audit des commandes (02/11) — volet « commandes »

Audit en lecture seule, sur `main` (65e64fad). Rien n'a été modifié ni testé en jeu. Chemins : dépôt, relatifs à la racine.

## En bref

- **Le Fondateur voit environ 500 commandes** (onglet « Commandes du Fondateur » du panneau P, qui retire déjà les
  doublons `minecraft:…`). Dans le chat (touche Tab), c'est **environ le double** : chaque commande y apparaît aussi avec
  son préfixe (`minecraft:tp`, `worldedit://set`, `litebans:ban`…).
- **Nos mods n'en font que 80** (1 sur 6). Le gros vient des plugins : **WorldEdit à lui seul ~180 noms**, **LiteBans
  ~100** (30 commandes + 73 autres noms pour les mêmes), puis Minecraft (~72) et le reste.
- **Ce qui sert vraiment** (journaux du 28 au 29/10, la seule soirée enregistrée) : 25 commandes différentes, dont 58 fois
  `/gamemode`.
- Proposition : **de ~500 à ~280 dans le panneau, et de ~1 000 à ~280 dans le chat**, sans toucher au code des autres
  (réglages + une petite liste dans le noyau), et en retirant de nos mods les outils d'essai et les doublons.
- Deux défauts trouvés en chemin : `/ping canal` est très probablement **inaccessible** aux joueurs (Mohist a son propre
  `/ping`), et **donner une commande à un grade lui donne aussi ses sous-commandes d'admin** (argent, clés payantes…).

---

## 1. Combien de commandes aujourd'hui

« Noms » = ce qu'on peut taper en premier mot (une commande + ses autres noms). Comptés dans les sources et dans les
jars (javap, `plugin.yml`), pas sur le serveur.

| D'où | Noms (1er niveau) | Sous-commandes (mots fixes) | Qui les voit |
|---|---|---|---|
| Nos mods livrés | **80** (dont 1 côté joueur : `/diagclient`) | **~478** | 37 pour tous, le reste staff |
| Mods des autres auteurs (TaCZ, Curios, Kiwi, CoroUtil, ModernFix, ywzj, MAtmos, TCTcore) | **9** | ~36 | 7 staff, **2 pour tous** (`/matmossettings`, `/tctcoresettings`) |
| Minecraft 1.20.1 + Forge | **~72** (79 de Minecraft, dont 9 remplacés par des plugins, + `/forge`, `/config`) | plus de 200 | op |
| WorldEdit | **~182** (120 commandes + 62 autres noms) | ~42 | op et bâtisseurs |
| LiteBans | **103** (30 commandes + 73 autres noms) | – | staff |
| AuthMe | 17 (10 + 7 autres noms) | ~30 | tous |
| WorldGuard | 17 (11 + 6 autres noms) | ~38 | op |
| LuckPerms | 6 (1 + 5 autres noms) | ~40 | tous (sans droit d'exécuter) |
| KauriVPN, ConsoleSpamFix | 5 + 1 | 6 | staff / tous |
| Bukkit, Spigot, Mohist | ~11 connus (`version ver about icanhasbukkit plugins pl reload rl timings tps restart mohist ping`) | ? | op, et `/plugins` `/version` pour tous |
| **Total (sans préfixes)** | **≈ 500** | **> 900** | |

Remarques :
- `docs/COMMANDES.md` annonce 69 commandes et `docs/COMMANDES_INVENTAIRE.md` 76 : l'outil `outils/commandes.py` en rate 6
  (`/matchs`, `/heralizloot`, `/rarete`, `/caracrea`, `/reanimation`, `/unlockbackpack`) et toutes les sous-commandes
  construites à part (`/casino zone …`, `/event helico …`, `/heralizloot zones|zombies …`, `/train voie …`, `/wipe tout …`).
- `/boss` (module mis de côté) et `/bots` (banc) ne sont pas sur le serveur : non comptés.

## 2. Ce qui est vraiment utilisé

Journaux du Ptero relevés par le workflow « Lenteurs » (`lent_log.txt`, `lent2_log.txt`, mêmes données) : **une seule
soirée**, le 28/10 de 17 h 49 au 29/10 à 2 h 49, **142 commandes tapées par des joueurs** (la console n'est pas notée ;
les journaux du 30 et du 31/10 n'en ont aucune).

| Commande | Fois | D'où |
|---|---|---|
| `/gamemode` | 58 | Minecraft |
| `/tp` | 10 | Minecraft |
| `/spawn`, `/guide` | 8 chacune | nos mods |
| `/wipe`, `/clan` | 7 chacune | nos mods |
| `/time`, `/boutique` | 6 chacune | Minecraft, nos mods |
| `/hdv` | 5 | nos mods |
| `/r`, `/kill`, `/deop` | 3 chacune | nos mods, Minecraft |
| `/tuto`, `/purge`, `/msg`, `/kit`, `/heal` | 2 chacune | nos mods, Minecraft, WorldGuard (`/heal`) |
| `/suicide`, `/pnj`, `/herals`, `/event`, `/eco`, `/namehist` | 1 chacune | nos mods, LiteBans (`/namehist`, un « autre nom ») |
| `/spwn`, `/gdv` | 1 chacune | fautes de frappe |

Ce que ça dit : le staff se sert d'une **dizaine de commandes d'admin**, presque toutes de Minecraft (`/gamemode`, `/tp`,
`/time`, `/kill`) ; aucune commande de WorldEdit, une seule de LiteBans (par un autre nom : `/namehist`). C'est une soirée
de bêta : à revérifier après une ou deux semaines d'ouverture (relancer « Lenteurs du serveur », qui relève les 300
dernières commandes de chaque journal).

Commandes que nos outils tapent eux-mêmes (à garder, voir §7) : `/recharger` (12 sous-commandes « recharger »), le
panneau P (`diag client`, `diag mesure`, `cycle jour|nuit`, `extraction rotation next`, `event lancer largage`,
`airstrike call`, `redemarrage`, `banpc`, `inspecter`, et LiteBans `warn`, `mute`, `tempmute`, `kick`, `tempban`), le site
(`pb donner`, `heraliz givekey`, `eco donner`), les caisses et la boutique (`grade acheter`, `heraliz givekey`,
`eco donner`, `emote debloquer`), le banc (`diag start`, `diag mesure`, `zombies stats`, `diag etat`, `forge tps`,
`bots …`, `tuto lancer`, `gamerule`, `stop`), les scripts de capture (beaucoup de Minecraft, `quete donner`,
`journalieres essai`, `heralizloot zombies …`).

---

## 3. Points à corriger (du plus grave au moins grave)

### 1. `/ping canal` et `/ping admin` probablement inaccessibles : Mohist a son propre `/ping`
- **Gravité : important**
- Où : `modules/monde/src/serveur/java/fr/heraliz/ping/server/PingCommandes.java:56` (notre `/ping`) ;
  `serveur/mohist-config/mohist.yml:91` (`ping-command-output`, réglage de la commande `/ping` de Mohist) ; preuve dans le
  banc du 01/11 (`banc6_log.txt`, lignes 1945, 2134, 3419) : chaque `ping canal` tapé par un bot est noté
  « `/ping (inconnue de Minecraft)` », alors que `/clan info`, `/home`… sont bien reconnues.
- Ce qui se passe : sous Mohist, une commande de Bukkit passe devant une commande de mod du même nom. Le `/ping` de Mohist
  (réservé à ceux qui en ont la permission) prend la place du nôtre : le noyau ne trouve pas `ping` dans l'arbre de
  Minecraft, donc il refuse aussi la commande sur la carte (« (refusée) » dans le banc, alors que `ping` est dans
  `commandes_permises`). Le commentaire du code dit « aucun plugin n'a /ping » : vrai pour les plugins, pas pour Mohist.
- En jeu : un joueur qui tape `/ping canal ami` reçoit sans doute « I'm sorry, but you do not have permission… » ou son
  ping en ms ; les canaux de pings ne marchent pas. **À confirmer en 1 minute** avec un compte non op.
- Proposition : soit renommer la nôtre (`/pings`, l'ancien nom, ou `/canal`), soit rediriger `/ping` vers la nôtre dans
  `serveur/commands.yml`. Effort : **petit**.
- Question : « On renomme `/ping` en `/pings` (ou `/canal`) ? »

### 2. Donner une commande à un grade lui donne aussi toutes ses sous-commandes d'admin
- **Gravité : important**
- Où : `modules/boutique/src/serveur/java/fr/heraliz/grades/serveur/CommandesDonnees.java:186-198` (la commande est relue
  avec la permission 4, quelle que soit la sous-commande) ; liste des interdites :
  `modules/boutique/src/commun/java/fr/heraliz/grades/staff/CommandesStaff.java:30-37`.
- Ce qui se passe : l'onglet « Commandes du Fondateur » donne une commande **par son premier mot**. Le serveur rejoue
  alors tout ce que le membre du staff tape après ce mot avec les droits les plus hauts. Exemples vérifiés :
  - cocher `/clan` (description : « créer, inviter… ») donne `/clan admin argent <joueur> <montant>` (hérals **sans
    limite**), `/clan admin supprimer <tag>` (efface un clan), `/clan admin forcer` ;
  - `/heraliz` donne `givekey` et `keyall` (clés payantes de la boutique) ;
  - `/quete` donne `donner` puis `finir` (récompenses de quête à volonté) ;
  - `/eco` donne `donner|retirer|definir` (10 millions par opération) ; `/casino` donne `jetons donner` (1 milliard).
  Tout est noté au journal du staff, mais rien ne l'empêche.
- Proposition : relire la commande avec la permission 2 au lieu de 4 (les sous-commandes réservées au niveau 4 restent
  fermées), et une liste de sous-commandes jamais données (`clan admin`, `eco donner|retirer|definir`,
  `heraliz givekey|keyall|cles`, `casino jetons|compte`, `quete donner|finir`). Effort : **moyen**.
- Question : « Quand tu donnes une commande à un grade, doit-il avoir aussi ses parties réservées à l'admin ? »

### 3. `/clan admin argent` : un `/eco` sans aucun garde-fou
- **Gravité : important**
- Où : `modules/clans/src/serveur/java/fr/heralisz/clans/server/ClanCommands.java:329` et `:406-425`, à comparer avec
  `modules/clans/src/serveur/java/fr/heralisz/economie/server/EconomieCommands.java:101` et `:237`.
- Ce qui se passe : depuis le 28/10, `/eco donner|retirer|definir` est réservé aux op 4 connectés sur AuthMe, plafonné à
  10 millions par opération et noté dans `heralisz_economie/journal_admin.log`. `/clan admin argent` fait la même chose
  (donner ou retirer des hérals à un joueur) dès l'op 2, **sans plafond, sans contrôle AuthMe et sans ce journal**.
  Signalé le 26/10 (A292, non vérifié) : c'est vérifié.
- Proposition : retirer `/clan admin argent` (on garde `/eco`). Effort : **petit**.

### 4. `/kiwi dev_env_rules …` (mod Kiwi) : une commande qui vide le monde
- **Gravité : important** (op 2 seulement, mais effet irréversible)
- Où : `serveur/mods-tiers/Kiwi-1.20.1-Forge-11.8.30.jar`, classe `snownee/kiwi/command/KiwiCommand` (lue avec javap).
- Ce qui se passe : `/kiwi dev_env_rules do_not_run_this_if_you_do_not_know_what_it_does` (outil de développement de
  Kiwi, livré tel quel) lance d'un coup : `kill @e[type=!minecraft:player]` (tue **toutes** les entités chargées : PNJ,
  marchands, corps, véhicules, zombies…), `gamerule keepInventory true` (plus de perte du stuff à la mort),
  `difficulty peaceful`, `doMobSpawning false`, etc. Kiwi ajoute aussi `/kiwi reload` et `/kiwi eval`, inutiles ici, et
  la racine `/kiwi` est visible des joueurs.
- Proposition : retirer `/kiwi` avec la liste du noyau (point 5). Effort : **petit** une fois la liste en place.

### 5. La liste est gonflée par les autres noms, les préfixes et des plugins entiers
- **Gravité : important** (c'est la demande du propriétaire)
- Où : `serveur/spigot.yml:49` (`send-namespaced: true`) ; `serveur/commands.yml` ; plugins dans `serveur/plugins/`.
- Ce qui se passe :
  - `send-namespaced: true` envoie au jeu chaque commande deux fois (`/tp` et `/minecraft:tp`, `//set` et
    `/worldedit://set`…) : **~1 000 lignes** dans la complétion du chat ;
  - WorldEdit (~182 noms, 62 sont des doublons comme `/br`, `//re`, `//rep`, `/;`, `/!`) et LiteBans (73 autres noms comme
    `lban`, `lmute`, `tban`, `hist`…) font à eux deux **57 %** de la liste ;
  - 9 commandes de mods des autres auteurs ne servent pas ici (`/tacz`, `/curios`, `/kiwi`, `/coro`, `/modernfix`,
    `/mfsrc`, `/ywzj_vehicle`, `/matmossettings`, `/tctcoresettings`) ;
  - une trentaine de commandes de Minecraft ne serviront jamais ici (`/jfr`, `/perf`, `/debug`, `/datapack`, `/function`,
    `/trigger`, `/team`, `/teammsg`, `/fillbiome`, `/forceload`…).
- Proposition, sans toucher au code des autres (détail au §5) :
  1. `send-namespaced: false` dans `serveur/spigot.yml` : moitié moins de lignes dans le chat, rien ne change pour les
     commandes elles-mêmes (on peut toujours taper `/minecraft:tp`). Effort **petit**.
  2. Une liste « commandes retirées » dans `heraliz_noyau-server.toml`, appliquée par le noyau juste après
     l'enregistrement des commandes de Minecraft et des mods : enlève pour de bon `/kiwi`, `/tacz`… et les commandes de
     Minecraft choisies (console comprise). Effort **moyen**.
  3. Pour les plugins (Bukkit) : soit les « cacher » de la complétion (le pont Bukkit de la boutique écoute déjà des
     évènements Bukkit ; il retirerait une liste de noms de ce qui est envoyé au jeu, la commande reste utilisable si on la
     tape : utile pour `/namehist`), effort **moyen** ; soit les retirer vraiment par `serveur/commands.yml`
     (`nom: []`), effort **petit**, à essayer d'abord sur le serveur d'essai (comportement de Mohist non vérifié).
- Question : « Les bâtisseurs gardent-ils tout WorldEdit (on cache seulement ses doublons) ? »

### 6. Des outils d'essai et de mise au point sont livrés sur le vrai serveur
- **Gravité : important** pour `/zombies banc`, **mineur** pour les autres
- Où et quoi :
  - `/zombies banc <1-3000> [rayon]` (op 2) — `modules/monde/src/serveur/java/fr/heralisz/utils/zombies/CommandeZombies.java:64` :
    pose jusqu'à 3 000 zombies qui ne disparaissent jamais d'eux-mêmes (ni peuplement ni retrait) tant que personne ne
    tape `/zombies banc retirer`. Le code dit lui-même « jamais en production avec des joueurs ». Le banc de charge de
    GitHub ne s'en sert pas (il a `/bots`).
  - `/journalieres essai <joueur> forcer|avancer|minuit` (op 4) — `modules/pnj/src/serveur/java/fr/heraliz/quetes/serveur/Commandes.java:74-80` :
    fausse progression des quêtes du jour (donc des récompenses).
  - `/quete donner` — même fichier, `:136` : donne une quête, que `/quete finir` termine aussitôt avec sa récompense.
  - `/heralizloot ouvrir` et `/heralizloot zombies simuler` — `modules/loot/src/serveur/java/fr/heraliz/loot/Commandes.java:41`,
    `ButinZombies.java:434`.
  - `/spartan esp bench`, `/spartan selfscan`, `/spartan discordtest` — `modules/anticheat/src/serveur/java/com/Spartan/SpartanCommand.java:155,177,183`.
  - `/diag start` (ancien nom de `/diag profil`), `/diag profil stop` (= `/diag stop`), `/diag auto on|off` et
    `/diag seuil` (déjà dans la section `[diag]` du réglage) — `modules/noyau/src/serveur/java/fr/heraliz/noyau/serveur/diag/CommandeDiag.java:32-55`.
- Proposition : retirer `zombies banc` (ou le déplacer dans le module `banc`, jamais livré) ; déplacer
  `journalieres essai`, `quete donner`, `heralizloot zombies simuler` vers le module `banc` (les scripts de capture
  tournent sur un serveur d'essai qui peut l'avoir) ; retirer le reste. `/quete finir` reste (dépannage), en op 4.
  Effort : **petit à moyen**. Attention au banc : `diag start` est la commande par défaut de `outils/banc_charge.py:62` et
  de `.github/workflows/banc.yml:29,117` (à changer en `diag profil`).

### 7. Le panneau P propose de réveiller des boss qui ne sont pas sur le serveur
- **Gravité : mineur**
- Où : `modules/boutique/src/commun/java/fr/heraliz/grades/staff/ActionStaff.java:48-49`,
  `modules/boutique/src/serveur/java/fr/heraliz/grades/serveur/PanelStaff.java:477-479`.
- Ce qui se passe : les actions « Boss : éveil » et « Boss : remise à zéro » (5 boss au choix) lancent `/boss …`, qui
  n'existe pas (module `events` mis de côté). En jeu : le bouton répond « échec ». Elles apparaissent aussi dans l'onglet
  Permissions.
- Proposition : cacher ces deux actions quand `/boss` est absent. Effort : **petit**.

### 8. 16 sous-commandes « recharger » en plus de `/recharger`, et `/recharger` en oublie 4
- **Gravité : mineur**
- Où : `serveur/defaultconfigs/heraliz_noyau-server.toml:36` (les 12 commandes jouées par `/recharger`) ;
  `modules/noyau/src/serveur/java/fr/heraliz/noyau/serveur/maintenance/Maintenance.java:84-101`.
- Ce qui se passe : chaque module garde sa propre commande de rechargement (`quete recharger`, `heraliz reload`,
  `casino reload`, `rarete recharger`, `heralizloot tables`, `saison recharger`, `territoire recharger`,
  `purge recharger`, `zones recharger`, `clashwar recharger`, `event recharger`, `spartan officiel`) que `/recharger` tape à
  sa place. Quatre autres n'y sont pas : `coffres recharger` (`CoffresLimites.java:280`), `salle recharger`
  (`CommandesSalle.java:71`), `clan admin recharger` (`ClanCommands.java:335`), `eco recharger`
  (`EconomieCommands.java:121`) : après un changement de `config/crac/coffres.json`, `/recharger` ne le relit pas.
- Proposition : ajouter les 4 à la liste (effort **petit**) ; puis, si on veut alléger, faire appeler le code directement
  par `/recharger` et retirer les 16 sous-commandes (effort **moyen**).

### 9. Doublons sous un autre nom dans nos mods
- **Gravité : mineur**
- Où et quoi :
  - `/caracrea` = `/perso` (même nœud : `/perso` renvoie vers `/caracrea`, `modules/voix/src/serveur/java/com/caracrea/server/CharacterCommand.java`) ;
    `/caracrea mute|unmute` (sans fin) = `/voix mute|unmute` (avec durée, `VoiceCommand.java:45,54`).
  - `/staff vanish` et `/staff nametag` (`VanishServeur.java:183,191`) = boutons « Se cacher » et « Cacher son nom » du
    panneau P (et ces deux commandes ne marchent pas depuis la console, contrairement à ce que dit `docs/COMMANDES.md`).
  - `/heraliz loot add|list|remove` = l'écran `/heraliz edit` (`HeralizCommand.java:105-140`).
  - `/casino zone vip …` (`ZoneCommand.java:41-48`) et `/zonevip` (`ZonesVip.java`) : deux systèmes de « zone VIP » avec
    la même règle d'entrée (grades Rescapé, Traqueur, Roi de la Zone).
  - `/matchs` (`CommandesMoteur.java:40`) : nom de l'ancien système des matchs ; seul `coffre rendre` sert encore
    (dépannage ClashWar), `parties` et `arenes …` sont de la mise au point.
  - `/sbaninfo` : nom resté de l'ancien `/sban` ; c'est la fiche d'un ban PC.
  - `/casino rtp` et `/casino stats` : deux statistiques des machines.
- Proposition : `/caracrea` fondue dans `/perso` (staff : `open|random|reset`), mute dans `/voix` ; retirer
  `/staff vanish|nametag` ; retirer `/heraliz loot add|list|remove` (garder `loot fichier`) ; `/matchs coffre` devient
  `/clashwar coffre`, le reste retiré ; `/sbaninfo` devient `/banpc info` ; `rtp` dans `stats`. Effort : **petit** chacun.
- Question : « La zone VIP du casino devient-elle une zone `/zonevip` comme les autres ? »

### 10. Ce que les joueurs (et les curieux) voient sans en avoir besoin
- **Gravité : mineur**
- Ce qui se passe :
  - `/plugins` et `/version` de Bukkit sont ouverts à tous par défaut : n'importe quel joueur lit la liste des plugins et
    leur version exacte (LiteBans 2.16.1, AuthMe 5.6.0, WorldGuard 7.0.9…), utile pour chercher une faille connue.
    À vérifier dans LuckPerms (`bukkit.command.plugins`, `bukkit.command.version` au groupe `default`), base hors du dépôt.
  - `/matmossettings` et `/tctcoresettings` (MAtmos, TCTcore : aucune permission demandée), `/kiwi`, `/lp` et ses 5 autres
    noms, `/csf`, `/litebans` : dans la liste de tous les joueurs.
  - `/vehicules liste` (`modules/monde/src/serveur/java/fr/heraliz/vehicules/Commandes.java:39`) : ouverte à tous, sans
    intérêt pour un joueur.
  - AuthMe : `/email` (aucun serveur de mail réglé, `serveur/plugins/AuthMe/config.yml:557-565` : la récupération ne peut
    rien envoyer), `/verification`, `/unregister` (un joueur efface son propre compte), `/logout` (A279 : sortie de combat).
- Proposition : LuckPerms (`bukkit.command.plugins` et `bukkit.command.version` à faux pour `default` ; `authme.player.email`,
  `…unregister`, `…logout` à faux), le reste par la liste du noyau ou le filtre du pont Bukkit (point 5) ;
  `/vehicules liste` en op 2. Effort : **petit**.

### 11. Restes de systèmes retirés et listes qui citent des commandes mortes
- **Gravité : mineur**
- Où et quoi :
  - `report` dans `commandes_permises_combat` et `commandes_permises` (`serveur/defaultconfigs/heraliz_noyau-server.toml:13,19`) :
    aucune commande `/report` n'existe.
  - `/registerserial` dans les commandes permises avant connexion d'AuthMe (`serveur/plugins/AuthMe/config.yml:134`) :
    n'existe pas.
  - `zombies`, `duel`, `groupe` dans les commandes permises en match
    (`modules/clashwar/src/serveur/java/fr/heraliz/clashwar/moteur/arenes/Protections.java:68`) : systèmes retirés.
  - `/cracbuild clear` conseillé par 3 messages du journal (`modules/bases/src/serveur/java/fr/heraliz/crac/claim/ClaimMaintenanceManager.java:407`,
    `…/build/world/StructureStore.java:98`, `…/build/world/StructureIntegrity.java:167`) et tapé par les scripts de
    capture `outils/capture/scripts/essai_bases.json` et `essai_bases_2.json` : la commande a été retirée (A278).
  - `AliasSanctions` (`modules/anticheat/src/serveur/java/com/Spartan/ban/AliasSanctions.java:31-36` vide, `:57` jamais
    appelée) et le commentaire de `BanCommands.java:26-27` : code mort.
  - `docs/COMMANDES.md` liste `/boss` comme gardée et `/matchs` comme commande ClashWar ; `outils/commandes.py:20` ne
    voit pas les commandes écrites avec une variable ou `lit()` (point du §1).
  - Bukkit `/reload` (`/rl`) recharge les plugins (risqué sur un serveur Forge + Bukkit) et Spigot `/restart` lance
    `./start.sh` (`serveur/spigot.yml:24`) : doublons dangereux de `/recharger` et `/redemarrage`. L'alias
    `icanhasbukkit` de `serveur/commands.yml` ne sert à rien. Le message « Unknown command. Type "/help" for help. »
    (`serveur/spigot.yml:37`) est en anglais : c'est ce que verront les joueurs qui tapent une commande retirée.
- Proposition : nettoyer ces listes et messages, refaire `outils/commandes.py` pour qu'il voie toutes les racines (puis
  `docs/COMMANDES*.md`), `reload: []`, `rl: []`, `restart: []` dans `serveur/commands.yml`, message en français.
  Effort : **petit**.

### 12. `/unlockbackpack` et `/vitres` : commandes que personne n'utilise
- **Gravité : mineur**
- `/unlockbackpack` (Backpacked, dans notre module `sacs`, `UnlockBackpackCommand.java:14`) : débloque un modèle de sac,
  jamais citée ailleurs. `/vitres pourquoi` (`CommandeVitres.java`) : outil de mise au point des vitres cassables.
- Proposition : retirer `/unlockbackpack` (code LGPL modifiable dans notre module, licence et crédits gardés) ; `/vitres`
  en op 4 ou retirée. Effort : **petit**.

---

## 4. Nos mods, module par module

Légende : **G** garder · **F** fusionner (avec quoi) · **R** retirer · **C** cacher (console / op 4, hors de la liste).
« Vu » = fois dans les journaux du 28-29/10.

### Noyau
| Commande | Qui | Vu | Décision |
|---|---|---|---|
| `/combat etat|marquer|effacer` | op 2 | – | G (dépannage du tag de combat) |
| `/diag` (12 sous-commandes) | op 4 | – | G ; **F** `start` → `profil`, `profil stop` → `stop` ; **R** `auto on|off`, `seuil` (déjà dans le réglage) |
| `/diagclient` | tous (côté jeu) | – | G |
| `/r` | tous | 3 | G |
| `/recharger` | op 4 | – | G (et y ajouter les 4 oubliés, point 8) |
| `/redemarrage [annuler]` | op 4 | – | G |

### ClashWar
| Commande | Qui | Vu | Décision |
|---|---|---|---|
| `/clashwar` (11) | tous / op 2 | – | G ; `recharger` C (via `/recharger`) |
| `/matchs` (9) | op 2 | – | **F** `coffre etat|rendre` → `/clashwar coffre` ; **R** `parties`, `arenes …` |

### Monde (utils, décors, extraction, frappes, corps, usure, véhicules, carte, ping)
| Commande | Qui | Vu | Décision |
|---|---|---|---|
| `/airstrike call|clear` | op 2 | – | G (panneau) |
| `/brume` | op 2 | – | G |
| `/zones info|recharger` | op 2 | – | G ; `recharger` C |
| `/carte lieu …|nom` | op 2 | – | G (bâtisseurs) |
| `/cinematique` (+ `epave …`) | tous / op 2 | – | G |
| `/cycle` (6) | op 2 | – | G (panneau) ; `/time` de Minecraft fait la même chose et reste |
| `/depart` (14) | op 2 | – | G |
| `/inspecter` | op 4 | – | G (panneau) |
| `/suicide` | tous | 1 | G |
| `/train` (+ `voie …`, 20) | op 2 | – | G (bâtisseurs) |
| `/zombies stats|reserves|zone …` | op 2 | – | G ; **R** `banc`, `banc retirer` |
| `/extraction` (10) | op 2 | – | G (panneau) |
| `/extractions` | tous | – | G |
| `/corps` (4) | op 2 | – | G |
| `/usure` (4) | op 2 | – | G |
| `/vehicules liste|donner` | tous / op 2 | – | G ; `liste` C (op 2) |
| `/ping canal|admin` | tous | – | G, **à réparer** (point 1) |

### Bases (CRac)
| Commande | Qui | Vu | Décision |
|---|---|---|---|
| `/base info|effacer` | tous / op 4 | – | G |
| `/carriere` (5) | op 2 | – | G |
| `/spawn` (+ `definir|effacer|voir`) | tous / op 2 | 8 | G |
| `/rtp`, `/home` | tous | – | G |
| `/protection` (3) | op 2 | – | G (protection donnée à la main) |
| `/tuto rejouer|lancer|finir` | op 2 | 2 | G (le banc tape `tuto lancer`) |
| `/coeur deplacer` | tous | – | G |
| `/coffres voir|recharger` | op 4 | – | G ; `recharger` C (à mettre dans `/recharger`) |
| `/purge` (+ `forcer|arreter|recharger`) | tous / op 4 | 2 | G ; `recharger` C |

### Boutique (grades, casino, caisses)
| Commande | Qui | Vu | Décision |
|---|---|---|---|
| `/boutique [abonnement]` | tous | 6 | G |
| `/staff panel|vanish|nametag` | staff | – | G `panel` ; **F** `vanish`, `nametag` → boutons du panneau P |
| `/bon editeur` | op | – | G |
| `/grade` (7) | staff / console | – | G |
| `/pb` (+ `stats`, console : `voir|donner|retirer`) | tous | – | G (le site tape `pb donner`) |
| `/trade`, `/hdv`, `/kit`, `/discord`, `/avantages`, `/tpa` | tous | `/hdv` 5, `/kit` 2 | G |
| `/warp` (+ 6 staff) | tous / staff | – | G |
| `/zonevip` (8) | op 2 | – | G |
| `/heraliz` (16) | tous / op 2 | – | G `preview`, `givekey` (site, caisses), `keyall`, `cles`, `givecrate`, `edit`, `editor`, `interface`, `loot fichier` ; `reload` C ; **F** `loot add|list|remove` → `edit` |
| `/casino` (40, dont `zone …` 21) | tous / op 2 | – | G ; `reload` C ; **F** `rtp` → `stats` ; **R** `salle clochard|vip` (construit les deux salles en écrasant ce qui est devant : déjà posées ?) ; `zone vip` → question du point 9 |
| `/capsules deposer|retirer` | tous | – | G |

### Clans
| Commande | Qui | Vu | Décision |
|---|---|---|---|
| `/clan` (20 pour les joueurs) | tous | 7 | G (doublent les boutons de l'écran, utiles en secours) |
| `/clan admin` (11) | op 2 | – | G ; **F** `argent` → `/eco` (point 3) ; `recharger` C |
| `/saison` (8) | tous / op 4 | – | G ; `recharger` C |
| `/herals payer|classement` | tous | 1 | G |
| `/eco` (8) | op 2 / op 4 | 1 | G ; `recharger` C ; **R** `sauvegarder [--forcer]` (la sauvegarde est automatique) |
| `/territoire` (12) | op 2 | – | G ; `recharger` C |

### PNJ et quêtes
| Commande | Qui | Vu | Décision |
|---|---|---|---|
| `/wipe` (+ `tout …`) | op 4 | 7 | G |
| `/quetes` | tous | – | G |
| `/journalieres` (+ `voir|remettre`) | tous / op 2 | – | G ; **R** `essai forcer|avancer|minuit` (vers le banc) |
| `/pnj` (12) | op 2 | 1 | G |
| `/guide` (4) | op 2 | 8 | G |
| `/quete` (12) | op 2 | – | G ; `recharger` C ; **R** `donner` (vers le banc) ; `finir` en op 4 |
| `/marchand` (7) | op 4 | – | G |

### Loot, bunker, armes, emotes, événements, survie, voix, anticheat, sacs
| Commande | Qui | Vu | Décision |
|---|---|---|---|
| `/heralizloot` (21) | op 2 | – | G ; `tables` C ; **R** `ouvrir`, `zombies simuler` |
| `/rarete` (18) | op 2 | – | G ; `recharger` C |
| `/salle` (14) | op 2 | – | G ; `recharger` C (à mettre dans `/recharger`) |
| `/camos` (4) | op 2 | – | G |
| `/armurier registre|editer` | op 4 | – | G |
| `/vitres pourquoi` | op 2 | – | C (op 4) ou R |
| `/emote debloquer|retirer|voir` | op 2 | – | G (caisses et boutique tapent `emote debloquer`) |
| `/event` (16, dont `helico …`) | tous / op 4 | 1 | G ; `recharger` C |
| `/reanimation execution …|revive` | tous / op 2 | – | G |
| `/caracrea` (5) | tous / op 2 | – | **F** → `/perso` et `/voix` |
| `/perso` | tous | – | G |
| `/voix` (5) | tous / op 2 | – | G |
| `/spartan` (18) | op 2 / op 3 | – | G ; `officiel` C ; **R** `esp bench`, `selfscan`, `discordtest` |
| `/sbaninfo` | op 2 | – | **F** → `/banpc info` |
| `/banpc`, `/unbanpc` | op 3 | – | G (panneau) |
| `/unlockbackpack` | op 2 | – | **R** |

**Nos mods : de 80 à 75 noms visibles** (`/caracrea`, `/matchs`, `/sbaninfo`, `/unlockbackpack` en moins, `/vitres`
cachée), **et de ~478 à ~416 sous-commandes** (22 retirées, 23 fusionnées, 17 cachées par `/recharger` ou l'op 4).

## 5. Mods des autres auteurs, plugins, Minecraft et Bukkit

On ne touche pas leur code. Moyens possibles : **N** = liste « commandes retirées » du noyau (à créer ; marche pour
Minecraft et les mods, console comprise) ; **Y** = `serveur/commands.yml` (`nom: []`, plugins Bukkit, à essayer sur le
serveur d'essai) ; **K** = cacher de la complétion par le pont Bukkit (la commande reste utilisable) ; **L** = permission
LuckPerms (ne change rien pour un op) ; **S** = `serveur/spigot.yml`.

| D'où | Aujourd'hui | Proposé | Comment | Ce qui est retiré ou caché |
|---|---|---|---|---|
| TaCZ `/tacz` (8 : `reload`, `config`, `debug`, `dummy`, `convert`, `overwrite`…) | 1 | 0 | N | tout (aucun outil ne s'en sert ; `config` peut couper des contrôles de tir) |
| Curios `/curios` (13) | 1 | 0 | N | doublon de `/inspecter` |
| Kiwi `/kiwi` | 1 | 0 | N | point 4 |
| CoroUtil `/coro config …` | 1 | 0 | N | réglages de Zombie Awareness (mod à retirer) |
| ModernFix `/modernfix`, `/mfsrc` | 2 | 0 | N | outils de développement |
| ywzj `/ywzj_vehicle debug|reload|summon` | 1 | 0 | N | doublon de `/vehicules donner` |
| MAtmos `/matmossettings`, TCTcore `/tctcoresettings` | 2 | 0 | N | réglages du son ouverts à tous |
| Minecraft + Forge | ~72 | ~45 | N | `debug jfr perf datapack function schedule trigger return bossbar team teammsg tm advancement recipe fillbiome place forceload spreadplayers ride loot seed defaultgamemode setidletimeout experience xp enchant attribute` (27). Gardés : `tp gamemode time kill give clear effect summon gamerule weather msg tell w me say tellraw title execute fill setblock clone item data …`, `/forge` (le banc tape `forge tps`) |
| WorldEdit | ~182 | ~116 | K (ou Y) | 62 autres noms (`/br`, `//re`, `//rep`, `/;`, `/!`, `//calc`→`//eval`…) ; R : `/snapshot` `/snap` `/restore` `//restore` (aucun dossier de sauvegardes réglé) et `/cs` `/.s` (scripts, rien d'installé) |
| LiteBans | 103 | ~19 | K pour les 73 autres noms ; Y pour le reste | R : `ipmute checkmute checkwarn mutelist warnlist prunehistory staffrollback lockdown geoip lastuuid ipreport`. Gardés : `ban tempban ipban unban mute tempmute unmute kick warn unwarn warnings history checkban banlist dupeip iphistory namehistory staffhistory litebans` (le panneau tape `warn mute tempmute kick tempban`) |
| AuthMe | 17 | ~8 | L + K | R : `email verification unregister unreg logout` ; K : `changepass cp` ; gardés `login l register reg changepassword captcha totp authme` |
| WorldGuard | 17 | ~11 | K / Y | K : `regions ; stoplag haltactivity` ; R : `slay stack` (gardés `region rg worldguard wg god ungod heal locate stopfire allowfire halt-activity`) |
| LuckPerms | 6 | 2 | K | `perm perms permission permissions` |
| KauriVPN | 5 | 1 | K | `kaurivpn kvpn vpn avpn` |
| ConsoleSpamFix `/csf` | 1 | 0 | K | console seulement |
| Bukkit, Spigot, Mohist | ~11 | ~4 | Y + L | R : `icanhasbukkit` (alias de `commands.yml`), `about`, `ver`, `pl`, `reload`, `rl`, `restart`, `timings` (remplacé par `/diag profil`) ; gardés `version plugins tps mohist` (et le `/ping` de Mohist si on renomme le nôtre) |
| Préfixes `minecraft:` `worldedit:` `litebans:`… (chat seulement) | ~500 | 0 | S | `send-namespaced: false` |

## 6. À retirer : le compte

| | Aujourd'hui | Après |
|---|---|---|
| Nos mods | 80 | 75 |
| Mods des autres auteurs | 9 | 0 |
| Minecraft + Forge | ~72 | ~45 |
| WorldEdit | ~182 | ~116 |
| LiteBans | 103 | ~19 |
| AuthMe, WorldGuard, LuckPerms, KauriVPN, ConsoleSpamFix | 46 | ~22 |
| Bukkit, Spigot, Mohist | ~11 | ~4 |
| **Panneau du Fondateur** | **≈ 503** | **≈ 281** (−44 %) |
| **Complétion du chat (préfixes compris)** | **≈ 1 000** | **≈ 281** |
| Sous-commandes de nos mods | ~478 | ~416 |

Pour un joueur normal : de ~80 noms à ~45 (sans `/plugins`, `/version`, `/lp`, `/csf`, `/kiwi`, `/matmossettings`,
`/tctcoresettings`, `/teammsg`, `/tm`, `/trigger`, `/email`…).

Le plus rentable, dans l'ordre : `send-namespaced: false` (1 ligne, −500 lignes dans le chat) ; cacher les autres noms
de WorldEdit et LiteBans (−135) ; la liste du noyau (mods + Minecraft, −36) ; nos propres retraits (−5 noms, −62
sous-commandes).

## 7. Ce qui casse si on retire

| Retrait | Qui s'en sert | À faire |
|---|---|---|
| `/diag start` | `outils/banc_charge.py:62`, `.github/workflows/banc.yml:29,117`, `docs/GUIDE.md:220,353,385` | écrire `diag profil` |
| `/diag auto`, `/diag seuil` | `docs/GUIDE.md:315` | doc |
| `/journalieres essai` | `outils/capture/scripts/journalieres.json` (5 fois), `docs/TESTS_EN_JEU_*` | mettre la commande dans le module `banc` ou `capture` |
| `/quete donner` | scripts de capture (6 fois : `journal.json`, `tour_complet.json`…) | idem |
| `/heralizloot zombies simuler` | scripts de capture (2 fois) | idem |
| `/zombies banc` | rien (seulement de vieux audits) | – |
| les 16 sous-commandes « recharger » | `/recharger` (`heraliz_noyau-server.toml:36`), panneau P (action Recharger), `outils/caisses.py` (texte « /heraliz reload ») | les garder cachées, ou faire appeler le code par `/recharger` avant de les retirer |
| `/clan admin argent` | rien | – |
| `/caracrea` | rien ; `/perso` est un renvoi vers elle | refaire `/perso` comme commande principale |
| `/matchs` | `Protections.java:68` (liste des commandes permises en match), lang du panneau | changer la liste |
| `/sbaninfo` | docs | – |
| `/staff vanish|nametag` | rien (le panneau appelle le code directement) | – |
| `/casino salle` | rien | – |
| `/heraliz loot add|list|remove` | commentaire de `vehicules/Commandes.java` | – |
| commandes de Minecraft retirées | scripts de capture (`execute tp item give gamemode clear effect fill weather time kill summon setblock gamerule difficulty damage stop` : tous **gardés**), banc (`gamerule`, `stop`), Kiwi (retiré aussi), outil de tournage de l'armurerie (`gamerule`) | ne retirer aucune de ces commandes |
| `/tacz`, `/curios`… | aucun outil ni mod ne les tape | – |
| autres noms de LiteBans | le staff a tapé `/namehist` le 28/10 | les **cacher** (K) plutôt que les retirer |
| LiteBans `warn mute tempmute kick tempban`, `/banpc`, `/inspecter`, `/cycle`, `/airstrike call`, `/event lancer`, `/extraction rotation next`, `/redemarrage`, `/diag client|mesure` | panneau P | à garder |
| `/pb donner`, `/heraliz givekey`, `/eco donner`, `/grade acheter`, `/emote debloquer` | site, caisses, boutique, casino | à garder |
| toute commande retirée | `heraliz_grades/commandes_staff.json` (commandes déjà données à un grade, sur le Ptero) | l'entrée reste sans effet |

## 8. Ce qui n'a pas été vérifié

- Rien n'a été testé en jeu. La liste exacte des commandes de Mohist n'est pas connue (pas de jar de Mohist dans le
  dépôt) ; la conclusion du point 1 vient des rapports du banc, à confirmer en jeu.
- `nom: []` dans `commands.yml` retire une commande sous Bukkit ; le comportement de Mohist (commandes de mods) est à
  essayer sur le serveur d'essai avant le Ptero.
- Les droits LuckPerms des groupes (`default`, staff) sont dans la base du serveur, pas dans le dépôt : le point 10 dit
  ce que Bukkit fait par défaut.
- L'usage réel repose sur une seule soirée de journaux.

## 9. Résumé

1. Le Fondateur voit ~500 commandes (~1 000 dans le chat) ; nos mods n'en font que 80, WorldEdit et LiteBans plus de la moitié.
2. Sans toucher au code des autres : `send-namespaced: false`, cacher les autres noms, une liste « retirées » dans le noyau → ~280.
3. Nos mods : retirer les outils d'essai (`/zombies banc`, `journalieres essai`, `quete donner`…) et 9 doublons (`/caracrea`, `/clan admin argent`, `/staff vanish`…).
4. À réparer : `/ping canal` est probablement cachée par le `/ping` de Mohist ; donner une commande à un grade donne aussi ses sous-commandes d'admin.
5. `/kiwi dev_env_rules` peut tuer toutes les entités d'un coup : à retirer avec les 8 autres commandes de mods inutiles.
