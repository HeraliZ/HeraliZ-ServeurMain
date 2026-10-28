# Commandes du serveur (tri du 25/10)

**69 commandes de premier niveau** (`/coffres` ajoutée le 27/10) depuis le tri du 25/10 (environ 90 avant). Une commande = un système : les
variantes sont des sous-commandes (`/tpa accepter`, `/herals payer`…). Inventaire complet lu dans les sources :
`python outils/commandes.py` (écrit `docs/COMMANDES_INVENTAIRE.md`).

## 1. Joueurs — survie DayZ (à garder)

| Commande | Module | À quoi elle sert |
|---|---|---|
| `/clan` | Clans | créer, inviter, banque, coffre, dons, améliorations, quitter |
| `/saison` | Clans | points et classement de la saison des clans |
| `/herals` (`payer <joueur> <montant>`, `classement`) | Clans | son argent, payer un joueur, les plus riches |
| `/home` | CRac | du spawn, devant l'armoire de la base de son clan (droit de clan, délai de 8 min partagé avec la réapparition à la base) |
| `/rtp` | CRac | aller du spawn vers un point libre pour sa base |
| `/spawn` | CRac | retour au spawn depuis sa base (`definir`, `effacer`, `voir` : staff) |
| `/base info` | CRac | fiche des bases (coûts, raids) ; `/base effacer` : op |
| `/coeur deplacer` | CRac | déplacer le cœur du clan |
| `/purge` | CRac | créneaux de la Purge ; `forcer` : op |
| `/tpa <joueur>` (`accepter [joueur]`, `refuser [joueur]`) | Grades | téléportation vers un joueur |
| `/warp` | Grades | menu des warps du spawn (`set`, `del`… : staff) |
| `/kit` | Grades | kit du grade |
| `/boutique`, `/pb`, `/trade` | Grades | boutique en jeu, solde de PB, échange entre joueurs |
| `/hdv` | Grades | hôtel des ventes (aussi le PNJ commissaire-priseur `commissaire`) : acheter, vendre (hérals sans frais, PB avec 10 % de frais payés par l'acheteur), mes ventes ; au spawn seulement, jamais en combat, à terre, en match ni au tutoriel ; réglages `config/heraliz_grades/hdv.json` (5 ventes, 12 / 24 / 48 h, plafonds PB par rareté), données et journal dans `heraliz_grades/hdv/` à la racine du serveur |
| `/boutique abonnement` | Grades | grade de la boutique et temps restant |
| `/avantages` (`nick`, `couleur`) | Grades | avantages des grades de la boutique |
| `/discord` (`lier`, `delier`) | Grades | lien avec Discord |
| `/r` | Noyau | répondre au dernier message privé |
| `/diagclient` | Noyau (côté joueur) | analyse les performances de son propre jeu |
| `/extractions` | Extraction | extractions ouvertes |
| `/suicide` | Utils | mort normale (refusée en combat et au spawn) |
| `/ping` | Utils | aide des pings (`admin` : staff) |
| `/ping canal` (`<nom>`, `clan`) | Utils | canal des pings : ton clan par défaut ; `/ping canal <nom>` (2 à 16 lettres ou chiffres) rejoint un canal à part, partagé avec tous ceux qui tapent le même nom (16 connectés au plus) ; `/ping canal` montre le tien et qui y est ; `/ping canal clan` ramène à ton clan. Un ping ne va qu'aux joueurs du même canal et du même monde (en match : ton équipe). Les membres du canal sont prévenus des arrivées et des départs. Gardé après déconnexion et redémarrage (`heraliz_ping/canaux.json` à la racine du serveur). Pas sur la carte tant que `ping` n'est pas dans `commandes_permises` du noyau |
| `/perso` | CaraCrea | créateur de personnage (au spawn seulement) |
| `/quetes` | Quêtes | journal des quêtes (touche J) |
| `/journalieres` | Quêtes | quêtes du jour (aussi le bouton « Quêtes du jour » du journal, touche libre dans les options) : choisir une difficulté par jour, une quête parmi trois, puis un tirage de récompense par réussite (26/10) |
| `/clashwar` | ClashWar | inscription, tableau, regarder un match, revenir en salle |
| `/heraliz` | Caisses | aperçu des caisses ; staff : `givekey` (clés ajoutées au compte du joueur, plus d'objet depuis le 25/10), `cles voir <joueur>`, `cles retirer <joueurs> <caisse> <nombre>` |

## 2. Staff — gestion du serveur (à garder)

| Commande | Module | À quoi elle sert |
|---|---|---|
| `/combat` | Noyau | voir, poser ou retirer le tag de combat d'un joueur |
| `/diag` | Noyau | diagnostic façon spark : `etat`, `start [s]`, `stop`, `mesure [s]`, `fuites`, `client <joueur> [s]`, `auto on\|off`, `seuil <ms>` (op 4, console) |
| `/recharger` | Noyau | relit les données de tous les modules sans redémarrer (op 4, console) |
| `/redemarrage` | Noyau | `<minutes> [raison]` ou `annuler` : redémarrage doux, les joueurs se reconnectent seuls (op 4, console) |
| `/matchs` | ClashWar | coffres, parties en cours, arènes |
| `/territoire` | Clans | zones des clans, drapeaux, difficulté |
| `/eco` | Clans | `donner`, `retirer`, `definir <joueur> <montant> [motif]` : op 4 connecté sur AuthMe ou console (28/10), un joueur connecté ou non (compte connu), 1 à 10 000 000 par opération, jamais de solde négatif ni au-dessus du plafond, journal `heralisz_economie/journal_admin.log` ; `journal`, `info` : op 2 |
| `/grade` (`editeur`, `registre` : op 4 ; `donner`, `retirer`, `acheter` : console) | Grades | éditeur des grades, grades des joueurs |
| `/bon` | Grades | éditeur des bons (op) |
| `/staff` (`panel`, `vanish`, `nametag`) | Grades | modération (panel : touche P, actions selon le grade du staff) ; onglet « Commandes du Fondateur » (28/10) : le Fondateur donne une commande précise à un grade du staff (`heraliz_grades/commandes_staff.json`, appliqué par le serveur) |
| `/zonevip` | Grades | zones VIP |
| `/marchand`, `/pnj`, `/quete` | Quêtes | marchands, PNJ, points de quête |
| `/wipe` | Quêtes | remise à zéro d'un joueur ou du serveur |
| `/tuto`, `/protection`, `/carriere` | CRac | tutoriel des bases, protection des nouvelles bases, carrières |
| `/coffres` (`voir`, `recharger`) | CRac | coffres de la base où l'on se tient et leurs limites ; relit `config/crac/coffres.json` (op) |
| `/depart`, `/carte`, `/train`, `/cinematique` | Utils | borne de départ, lieux de la carte, train, cinématique |
| `/corps`, `/usure`, `/vehicules`, `/inspecter` | Utils | cadavres, usure des armes, véhicules, inventaire d'un joueur |
| `/vitres`, `/camos`, `/armurier` | Tactique | vitres cassables, camos à vie, recettes de la Table d'armurier |
| `/heralizloot`, `/rarete` | Loot | conteneurs, zones de loot, raretés ; `/heralizloot zombies stats` (butin des zombies : kills, objets, fragments, cases chaudes), `zombies simuler <n> [militaire]` (tire n fois la table, rien n'est donné), `zombies joueur <joueur>` (ses kills de l'heure, sa chance de butin, ses fragments du jour) (26/10) |
| `/journalieres voir <joueur>`, `remettre <joueur>` (op 2) ; `essai <joueur> forcer\|avancer\|minuit` (op 4, serveur d'essai) | Quêtes | état des quêtes du jour d'un joueur, remise d'une récompense restée en attente ; essais : quête forcée, progression ajoutée, minuit simulé (noté au journal `heraliz_quetes/journalieres.log`) |
| `/salle` | Bunker | salles à carte |
| `/brume`, `/zones` | Décors | brouillard, zones gaz / radiation / froid |
| `/extraction` | Extraction | poser et régler les zones d'extraction |
| `/airstrike` | Airstrike | frappes et largages |
| `/voix`, `/caracrea` | CaraCrea | voix de proximité, personnages |
| `/emote` | Emote | débloquer ou retirer une emote |
| `/casino` | Casino | zones du casino, capsules, `compte voir\|donner\|retirer` (compte de capsules d'un joueur) |
| `/capsules` | Casino (joueurs) | compte de capsules : solde, `deposer [nombre]`, `retirer <nombre>` à côté d'un distributeur de capsules (ATM) ou d'une caisse du casino (au distributeur : clic droit, son écran) |
| `/boss <mammouth\|mere\|n07\|peste\|sylvorn> …` | Boss | arènes et réveil des boss (events) |
| `/spartan`, `/banpc`, `/unbanpc`, `/sbaninfo` | Spartan | anticheat, ban compte + IP + PC (les autres sanctions : LiteBans) |

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
| `/sethome`, `/delhome`, `/homes`, `/home poser` | `/home` seul : le point est toujours devant l'armoire de la base du clan |
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
| `/pings` | `/ping` |
| `/clashwar banc`, `/casino roll`, `/casino bot` | retirées (outils d'essai) |
| `/cc` (chat de clan) | canal « Clan » du chat (Tab dans le chat) |
