# Commandes du serveur (tri du 25/10, allégé le 02/11)

**Nos mods : 76 commandes de premier niveau sur le serveur** (02/11 ; plus `/diagclient` côté joueur et `/cracghost`, outil
de développement jamais en production). Une commande = un système : les variantes sont des sous-commandes
(`/tpa accepter`, `/herals payer`…). Inventaire complet lu dans les sources : `python outils/commandes.py` (écrit
`docs/COMMANDES_INVENTAIRE.md`).

**Liste complète vue par le Fondateur** (panneau P, onglet « Commandes du Fondateur », et complétion du chat) : environ 280
noms depuis le 02/11 (environ 500 avant, et le double dans le chat avec les préfixes `minecraft:`…). Ce qui l'allège,
sans toucher au code des autres (section 6) : `send-namespaced: false` (`serveur/spigot.yml`), la liste `[commandes]
retirees` du noyau (mods et Minecraft, retirées pour de bon) et `[commandes] cachees` (autres noms des plugins, cachés
mais toujours utilisables si on les tape), `serveur/commands.yml`.

## 1. Joueurs — survie DayZ (à garder)

| Commande | Module | À quoi elle sert |
|---|---|---|
| `/clan` | Clans | créer, inviter, banque, coffre, dons, améliorations, quitter |
| `/saison` | Clans | points et classement de la saison des clans |
| `/herals` (`payer <joueur> <montant>`, `classement`) | Clans | son argent, payer un joueur, les plus riches |
| `/home` | CRac | du spawn, vers le home de la base de son clan (droit de clan, délai de 8 min partagé avec la réapparition à la base) ; base posée avant le 03/11 sans home posé : devant l'armoire |
| `/sethome [nom]` | CRac | (03/11) poser le home de la base de son clan, là où l'on est : dans la zone de l'armoire, à un endroit sûr, droit « Gérer la base » ; un seul par base, le nouveau remplace l'ancien (nom de la commande : `Homes.COMMANDE_POSER`) |
| `/rtp` | CRac | aller du spawn vers un point libre pour sa base |
| `/spawn` | CRac | retour au spawn depuis sa base (`definir`, `effacer`, `voir` : staff) |
| `/base info` | CRac | fiche des bases (coûts, raids) ; `/base effacer` : op |
| `/coeur deplacer` | CRac | déplacer le cœur du clan |
| `/purge` | CRac | créneaux de la Purge ; `forcer` : op |
| `/tpa <joueur>` (`accepter [joueur]`, `refuser [joueur]`) | Grades | téléportation vers un joueur |
| `/warp` | Grades | menu des warps du spawn (`set`, `del`… : staff) |
| `/kit` | Grades | kit du grade |
| `/boutique`, `/pb`, `/trade` | Grades | boutique en jeu, solde de PB, échange entre joueurs |
| `/hdv` | Grades | hôtel des ventes (aussi le PNJ commissaire-priseur `commissaire`) : acheter, vendre (toujours un prix en hérals, un prix en PB en plus si le vendeur veut : l'acheteur choisit ; achat en hérals au prix affiché, en PB avec une taxe de 10 % payée par l'acheteur en plus du prix (100 -> 110, le vendeur reçoit 100, 10 brûlés), `frais_pb_pourcent` ; taxe de mise en vente de 12,5 % du prix en hérals payée à la publication, jamais rendue, `taxe_mise_en_vente_pourcent`), mes ventes ; au spawn seulement, jamais en combat, à terre, en match ni au tutoriel ; réglages `config/heraliz_grades/hdv.json` (5 ventes, 12 / 24 / 48 h, plafonds PB par rareté), données et journal dans `heraliz_grades/hdv/` à la racine du serveur |
| `/boutique abonnement` | Grades | grade de la boutique et temps restant |
| `/avantages` (`nick`, `couleur`) | Grades | avantages des grades de la boutique |
| `/discord` (`lier`, `delier`) | Grades | lien avec Discord |
| `/r` | Noyau | répondre au dernier message privé |
| `/diagclient` | Noyau (côté joueur) | analyse les performances de son propre jeu |
| `/extractions` | Extraction | extractions ouvertes |
| `/suicide` | Utils | mort normale (refusée en combat et au spawn) |
| `/canal` (`<nom>`, `clan`, `aide` ; `admin` : staff) | Utils | canal des pings (02/11 : ancien `/ping canal`, caché par le `/ping` de Mohist) : ton clan par défaut ; `/canal <nom>` (2 à 16 lettres ou chiffres) rejoint un canal à part, partagé avec tous ceux qui tapent le même nom (16 connectés au plus) ; `/canal` montre le tien et qui y est ; `/canal clan` ramène à ton clan ; `/canal aide` : l'aide des pings. Un ping ne va qu'aux joueurs du même canal et du même monde (en match : ton équipe). Les membres du canal sont prévenus des arrivées et des départs. Gardé après déconnexion et redémarrage (`heraliz_ping/canaux.json` à la racine du serveur). Permis sur la carte (`canal` dans `commandes_permises` du noyau) |
| `/perso` | CaraCrea | créateur de personnage (au spawn seulement) ; staff : `open`, `random`, `reset <joueurs>` (ancien `/caracrea`) |
| `/quetes` | Quêtes | journal des quêtes (touche J) |
| `/journalieres` | Quêtes | quêtes du jour (aussi le bouton « Quêtes du jour » du journal, touche libre dans les options) : choisir une difficulté par jour, une quête parmi trois, puis un tirage de récompense par réussite (26/10) |
| `/clashwar` | ClashWar | inscription, tableau, regarder un match, revenir en salle |
| `/heraliz` | Caisses | aperçu des caisses ; staff : `givekey` (clés ajoutées au compte du joueur, plus d'objet depuis le 25/10), `cles voir <joueur>`, `cles retirer <joueurs> <caisse> <nombre>` |

## 2. Staff — gestion du serveur (à garder)

| Commande | Module | À quoi elle sert |
|---|---|---|
| `/combat` | Noyau | voir, poser ou retirer le tag de combat d'un joueur |
| `/diag` | Noyau | diagnostic façon spark : `etat`, `profil [s] [intervalle]`, `stop`, `commandes`, `mesure [s]`, `fuites`, `client <joueur> [s]` (op 4, console) ; réglages des rapports automatiques : section `[diag]` du noyau |
| `/recharger [module]` | Noyau | relit les données de tous les modules (ou d'un seul) sans redémarrer (op 4, console) : `quetes`, `caisses`, `casino`, `raretes`, `loot`, `saison`, `territoires`, `clans`, `herals`, `purge`, `coffres`, `zones`, `clashwar`, `evenements`, `armes`, `anticheat` (02/11 : les sous-commandes « recharger » de chaque module n'existent plus) |
| `/redemarrage` | Noyau | `<minutes> [raison]` ou `annuler` : redémarrage doux, les joueurs se reconnectent seuls (op 4, console) |
| `/clashwar coffre\|parties\|arenes` | ClashWar | coffres des matchs (`etat`, `rendre <joueur>`), parties en cours, arènes (op 2) ; anciennement `/matchs`, fondue dans `/clashwar` le 02/11 |
| `/territoire` | Clans | zones des clans, drapeaux, difficulté |
| `/eco` | Clans | `donner`, `retirer`, `definir <joueur> <montant> [motif]` : op 4 connecté sur AuthMe ou console (28/10), un joueur connecté ou non (compte connu), 1 à 10 000 000 par opération, jamais de solde négatif ni au-dessus du plafond, journal `heralisz_economie/journal_admin.log` ; `journal`, `info` : op 2 |
| `/grade` (`editeur`, `registre` : op 4 ; `donner`, `retirer`, `acheter` : console) | Grades | éditeur des grades, grades des joueurs |
| `/bon` | Grades | éditeur des bons (op) |
| `/staff panel` | Grades | modération (panel : touche P, actions selon le grade du staff ; « Se cacher » et « Cacher son nom » y sont des boutons, ex-`/staff vanish` et `/staff nametag`) ; onglet « Commandes du Fondateur » (28/10) : le Fondateur donne une commande précise à un grade du staff (`heraliz_grades/commandes_staff.json`, appliqué par le serveur) |
| `/zonevip` | Grades | zones VIP |
| `/marchand`, `/pnj`, `/quete` | Quêtes | marchands, PNJ, points de quête ; `/quete finir` : op 4 ; `/quete donner` : seulement avec le banc d'essai |
| `/wipe` | Quêtes | remise à zéro d'un joueur ou du serveur |
| `/tuto`, `/protection`, `/carriere` | CRac | tutoriel des bases, protection d'une base donnée à la main par le staff (plus aucune protection automatique depuis le 27/10 et le 31/10), carrières |
| `/cracbuild` | CRac | outils de construction (`showcase`, `essai`, `clear`, `tier`, `damage`, `doors`) : op 4 (02/11, `clear` efface des bases) |
| `/coffres voir` | CRac | coffres de la base où l'on se tient et leurs limites (op 4) ; `config/crac/coffres.json` : `/recharger coffres` |
| `/depart`, `/carte`, `/train`, `/cinematique` | Utils | borne de départ, lieux de la carte, train, cinématique |
| `/corps`, `/usure`, `/vehicules` (op 2 depuis le 02/11), `/inspecter` | Utils | cadavres, usure des armes, véhicules, inventaire d'un joueur |
| `/zombies` (`stats`, `reserves`, `zone`) | Utils | zombies HeraliZ (02/11 : `banc` retiré) |
| `/vitres` (op 4), `/camos`, `/armurier` | Tactique | vitres cassables (diagnostic), camos à vie, recettes de la Table d'armurier |
| `/heralizloot`, `/rarete` | Loot | conteneurs, zones de loot, raretés ; `/heralizloot zombies stats` (butin des zombies : kills, objets, fragments, cases chaudes), `zombies joueur <joueur>` (ses kills de l'heure, sa chance de butin, ses fragments du jour) (26/10) ; `zombies simuler <n> [militaire]` (tire n fois la table) : seulement avec le banc d'essai (02/11) |
| `/journalieres voir <joueur>`, `remettre <joueur>` (op 2) ; `essai <joueur> forcer\|avancer\|minuit` (op 4, seulement avec le banc d'essai) | Quêtes | état des quêtes du jour d'un joueur, remise d'une récompense restée en attente ; essais : quête forcée, progression ajoutée, minuit simulé (noté au journal `heraliz_quetes/journalieres.log`) |
| `/salle` | Bunker | salles à carte |
| `/brume`, `/zones` | Décors | brouillard, zones gaz / radiation / froid |
| `/extraction` | Extraction | poser et régler les zones d'extraction |
| `/airstrike` | Airstrike | frappes et largages |
| `/voix` | CaraCrea | voix de proximité : réglages ; staff : `mute <joueurs> [durée] [raison]` (sans durée : sans fin), `unmute`, `mutes`, `info`, `stats` |
| `/emote` | Emote | débloquer ou retirer une emote |
| `/casino` | Casino | zones du casino, capsules, `compte voir\|donner\|retirer` (compte de capsules d'un joueur), `stats` (chiffres réels puis redistribution prévue : ex-`/casino rtp`) |
| `/capsules` | Casino (joueurs) | compte de capsules : solde, `deposer [nombre]`, `retirer <nombre>` à côté d'un distributeur de capsules (ATM) ou d'une caisse du casino (au distributeur : clic droit, son écran) |
| `/boss <mammouth\|mere\|n07\|peste\|sylvorn> …` | Boss | arènes et réveil des boss (events) : module mis de côté, pas sur le serveur |
| `/spartan`, `/banpc` (`info` : ancien `/sbaninfo`), `/unbanpc` | Spartan | anticheat, ban compte + IP + PC (les autres sanctions : LiteBans) |

## 3. Retirées (validé le 24/10)

| Commande | Pourquoi |
|---|---|
| `/emotetest` | banc d'essai des emotes, jamais utile en jeu |
| `/group` | ancien système de groupes de CRac, déjà inactif depuis les bases de clan |
| `/n07 zombies` (salle du boss du mode Zombies) | mode Zombies supprimé |
| `/n07` sous-commandes d'essai (`hp`, `phase`, `passive`) | réglages de test du boss |
| `/reanimation down` et `/reanimation bot` | outils d'essai de la mise à terre (`execution` et `revive` restent) |
| `/armory` (`equip`, `wear`, `give`, `strip`, `nvg`, `mannequin`) | outils d'essai des armures (le staff a `/give`) |
| `/roll` | doublon de `/casino roll` (staff) |
| `/bots` | gardé pour les tests de charge, mais dans le module `banc`, **livré à part** (`livraison/banc/`) |

## 4. Regroupées ou retirées (validé le 25/10)

| Avant | Maintenant |
|---|---|
| `/sethome`, `/delhome`, `/homes`, `/home poser` | `/home` seul : le point est toujours devant l'armoire de la base du clan (03/11 : `/sethome [nom]` revient, un home par base posé par le joueur, plus aucun home créé tout seul) |
| `/zone base` | `/rtp` (c'était le même) |
| `/tpaccept`, `/tpdeny` | `/tpa accepter`, `/tpa refuser` (boutons cliquables dans le chat) |
| `/lier`, `/delier` | `/discord lier`, `/discord delier` |
| `/nick`, `/chatcouleur` | `/avantages nick`, `/avantages couleur` |
| `/abonnement` | `/boutique abonnement` |
| `/vanish`, `/nametag`, `/panel` | `/staff vanish`, `/staff nametag`, `/staff panel` |
| `/grades editeur`, `/grades registre` | `/grade editeur`, `/grade registre` |
| `/solde`, `/payer`, `/classement` | `/herals`, `/herals payer`, `/herals classement` |
| `/mammouth`, `/mere`, `/n07`, `/peste`, `/sylvorn` | `/boss mammouth` … |
| `/sban`, `/sunban`, `/unbanplayer` | `/banpc`, `/unbanpc` |
| `/untempban` | `/unban` de LiteBans (il lève aussi les bans temporaires) |
| `/pings` | `/ping` (devenu `/canal` le 02/11) |
| `/clashwar banc`, `/casino roll`, `/casino bot` | retirées (outils d'essai) |
| `/cc` (chat de clan) | canal « Clan » du chat (Tab dans le chat) |

## 5. Allégées le 02/11 (audit des commandes, `docs/audit_02nov/commandes.md`)

| Avant | Maintenant |
|---|---|
| `/ping canal …`, `/ping admin …`, `/ping` | `/canal …`, `/canal admin …`, `/canal aide` (`/ping` est celui de Mohist : latence) |
| `/caracrea [open\|random\|reset]`, `/caracrea mute\|unmute` | `/perso [open\|random\|reset]`, `/voix mute\|unmute` |
| `/staff vanish`, `/staff nametag` | boutons « Se cacher » et « Cacher son nom » du panneau du staff |
| `/heraliz loot add\|list\|remove` | éditeur de butin `/heraliz edit <caisse>` (`/heraliz loot fichier` reste) |
| `/casino rtp` | `/casino stats` (donne les deux) |
| `/quete recharger`, `/heraliz reload`, `/casino reload`, `/rarete recharger`, `/heralizloot tables`, `/saison recharger`, `/territoire recharger`, `/clan admin recharger`, `/eco recharger`, `/purge recharger`, `/coffres recharger`, `/zones recharger`, `/clashwar recharger`, `/event recharger`, `/armurier recharger`, `/spartan officiel` | `/recharger [module]` |
| `/diag start`, `/diag profil stop`, `/diag auto`, `/diag seuil` | `/diag profil`, `/diag stop`, section `[diag]` du noyau |
| `/zombies banc`, `/heralizloot ouvrir`, `/unlockbackpack` | retirées (outils d'essai, aucun usage) |
| `/journalieres essai`, `/quete donner`, `/heralizloot zombies simuler` | seulement sur un serveur d'essai qui a le banc (`outils/serveur_local.py --banc`) |
| `/quete finir`, `/vitres`, `/cracbuild` | op 4 (avant : op 2) |
| `/vehicules liste` (tous) | `/vehicules` : staff (op 2) |
| `/matchs` (autre chantier) | `/clashwar coffre\|parties\|arenes` |
| `/sbaninfo`, `/spartan esp bench\|selfscan\|discordtest` (autre chantier) | `/banpc info` ; retirées |
| `/clan admin argent` (autre chantier) | `/eco` |

## 6. Commandes des autres (mods, Minecraft, plugins) : réglages, sans toucher à leur code

- **Préfixes** : `send-namespaced: false` (`serveur/spigot.yml`) : `/minecraft:tp`, `/worldedit://set`, `/litebans:ban`… ne sont
  plus envoyés au jeu (ils marchent toujours si on les tape).
- **Retirées pour de bon** (`[commandes] retirees` de `heraliz_noyau-server.toml`, 35 noms, console comprise ; une commande de
  plugin n'y est jamais touchée) : `/tacz`, `/kiwi` (dont `/kiwi dev_env_rules`, qui tuait toutes les entités chargées),
  `/coro`, `/modernfix`, `/mfsrc`, `/ywzj_vehicle`, `/matmossettings`, `/tctcoresettings` ; Minecraft : `debug`, `jfr`, `perf`,
  `datapack`, `function`, `schedule`, `trigger`, `return`, `bossbar`, `team`, `teammsg`, `tm`, `advancement`, `recipe`,
  `fillbiome`, `place`, `forceload`, `spreadplayers`, `ride`, `loot`, `seed`, `defaultgamemode`, `setidletimeout`,
  `experience`, `xp`, `enchant`, `attribute`. Gardées (nos outils, le banc, les scripts de capture et le site s'en servent) :
  `tp`, `gamemode`, `time`, `kill`, `give`, `clear`, `effect`, `summon`, `gamerule`, `weather`, `difficulty`, `execute`, `fill`,
  `setblock`, `item`, `damage`, `stop`, `msg`, `tell`, `w`, `me`, `say`, `forge`, `config`…
- **Cachées mais utilisables** (`[commandes] cachees`, 178 noms, serveur Bukkit seulement) : les 62 autres noms de WorldEdit
  (`/br`, `//re`, `//rep`, `/;`, `/!`, `//calc`…) et `snapshot`, `restore`, `cs`, `.s` ; les 73 autres noms de LiteBans (`lban`,
  `tban`, `hist`, `namehist`…) et ses commandes sans usage (`ipmute`, `lockdown`, `geoip`…) ; AuthMe (`log`, `2fa`,
  `changepass`, `cp`, `email`, `verification`, `unregister`, `unreg`, `logout`) ; WorldGuard (`regions`, `;`, `stoplag`,
  `haltactivity`, `slay`, `stack`) ; LuckPerms (`perm`, `perms`, `permission`, `permissions`) ; KauriVPN (`kaurivpn`, `kvpn`,
  `vpn`, `avpn`) ; `/csf` ; Bukkit (`about`, `ver`, `pl`, `timings`) ; `/curios` (`/wipe` tape `curios clear`).
- **`serveur/commands.yml`** : `/reload`, `/rl` (recharge les plugins, risqué sur Forge + Bukkit) et `/restart` retirés ;
  l'alias `icanhasbukkit` vidé.
- **Filtre « combat, à terre, carte »** : les commandes des plugins (AuthMe `/logout`, LiteBans, WorldGuard…) y passent aussi
  depuis le 02/11 (événement Bukkit, `CommandesBukkit` du noyau), avec les mêmes listes `commandes_permises_combat` et
  `commandes_permises`.
- **À régler dans LuckPerms** (base du serveur, pas dans le dépôt) : `bukkit.command.plugins` et `bukkit.command.version` à
  faux pour le groupe `default` (sinon tout joueur lit la liste des plugins et leurs versions).
