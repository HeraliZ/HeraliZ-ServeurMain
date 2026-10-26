# Audit des commandes (25/10) — validé et appliqué le 25/10 (résultat : `docs/COMMANDES.md`)

Inventaire complet, lu dans les sources : `docs/COMMANDES_INVENTAIRE.md` (écrit par `python outils/commandes.py`).
**92 commandes de premier niveau et 316 sous-commandes** dans nos mods. À cela s'ajoutent celles des plugins (AuthMe,
LiteBans, LuckPerms, WorldEdit, WorldGuard, spark) et de Minecraft.

Idée générale :
- **les joueurs** gardent peu de commandes, courtes, une par sujet ;
- **le staff** passe par le **panel** (touche P) : chaque grade n'y voit que ce qu'il a le droit de faire. Les commandes
  du staff restent pour la console et pour les cas rares, regroupées.

Validé par le propriétaire le 25/10 et appliqué : 68 commandes de premier niveau. Donner un grade est aussi possible depuis le
panel, pour le Fondateur seulement (op 4, connecté), jamais le grade Fondateur.

## 1. Joueurs : de 32 à 18 commandes

| Aujourd'hui | Proposé | Pourquoi |
|---|---|---|
| `/home`, `/homes`, `/sethome`, `/delhome` | `/home [nom]`, `/home liste`, `/home poser [nom]`, `/home retirer <nom>` | une seule commande pour les homes |
| `/tpa`, `/tpaccept`, `/tpdeny` | `/tpa <joueur>`, `/tpa accepter`, `/tpa refuser` (+ boutons cliquables, comme aujourd'hui) | même chose, une seule racine |
| `/rtp`, `/zone base` | `/rtp` | doublon |
| `/solde`, `/payer`, `/classement` | `/herals`, `/herals payer <joueur> <montant>`, `/herals classement` | tout l'argent au même endroit |
| `/lier`, `/delier` | `/discord lier`, `/discord delier` | plus clair |
| `/ping`, `/pings` | `/ping` | doublon |
| `/boutique`, `/abonnement`, `/pb` | `/boutique` (onglets : abonnement, PB) ; `/pb` gardé (solde rapide) | moins de commandes à retenir |
| `/nick`, `/chatcouleur` | `/avantages nick …`, `/avantages couleur …` | avantages des grades au même endroit |
| `/clan`, `/saison`, `/quetes`, `/kit`, `/warp`, `/trade`, `/r`, `/suicide`, `/spawn`, `/extractions`, `/base info`, `/coeur`, `/purge`, `/clashwar`, `/diagclient` | gardés | déjà une seule commande par sujet |

Les anciens noms peuvent rester comme raccourcis invisibles quelques semaines (habitudes de la bêta) : à toi de voir.

## 2. Staff : regroupements

| Aujourd'hui | Proposé |
|---|---|
| `/mammouth`, `/mere`, `/n07`, `/peste`, `/sylvorn` | `/boss <nom> eveil\|reset\|salle…` |
| `/cc` (clans, staff) | `/clan admin …` |
| `/eco`, `/solde <joueur>` | `/eco …` |
| `/grade`, `/grades` | `/grade …` (éditeur, registre, voir, liste) |
| `/vanish`, `/nametag`, `/panel` | panel (touche P) ; `/staff vanish`, `/staff nametag` pour la console |
| `/sban`, `/sunban`, `/unbanplayer`, `/untempban` | supprimés : doublons de `/banpc`, `/unbanpc`, `/unban` (LiteBans) |
| sous-commandes `recharger` / `reload` de 11 modules | une seule commande `/recharger` (les autres restent pour la console, cachées du panel) |
| `/clashwar factices\|sim\|banc`, `/casino bot\|clochard\|roll` | sortis du serveur normal : outils d'essai, vers le module `banc` |
| `/brume`, `/zones`, `/depart`, `/carte`, `/cinematique`, `/train`, `/corps`, `/usure`, `/vehicules`, `/vitres`, `/airstrike`, `/extraction`, `/salle`, `/carriere` | gardés (outils des bâtisseurs), rangés dans l'onglet « Monde » du panel |
| `/diag`, `/redemarrage`, `/recharger`, `/wipe`, `/inspecter`, `/combat` | gardés, onglet « Serveur » du panel |

## 3. Panel du staff (maquettes : `maquettes/staff_chat/1_` à `3_`)

- **Une touche (P) pour tout le staff**, du Guide au Fondateur. Le panel n'affiche que les actions permises à son grade.
- **Le Fondateur** coche, dans l'onglet « Permissions », ce que chaque grade peut faire. C'est gardé à vie, sans
  redémarrer (dans `heraliz_grades/definitions.json`, comme les autres réglages des grades).
- **Le serveur revérifie** chaque action : un jeu modifié ne peut rien faire de plus que son grade.
- **Chaque action est notée** (qui, quand, quoi, pourquoi) dans un journal du serveur, dans l'onglet « Historique », et
  sur le salon du staff Discord.
- Les sanctions passent par LiteBans (mute, kick, ban) et Spartan (ban PC), joués par la console au nom du staff : leur
  historique ne change pas.
- **Grades du staff prêts** (HeraliZ Grades) : Fondateur, Admin, Modérateur, Helpeur, Guide (rangs 5 à 1). Admin et
  Guide sont nouveaux ; Helper s'affiche « Helpeur ».

Répartition proposée des droits (modifiable ensuite dans le panel) :

| Action | Guide | Helpeur | Modérateur | Admin | Fondateur |
|---|---|---|---|---|---|
| Répondre aux questions | ✔ | ✔ | ✔ | ✔ | ✔ |
| Avertir, rendre muet (1 h au plus), expulser, aller voir, libérer un joueur coincé | | ✔ | ✔ | ✔ | ✔ |
| Mute toute durée, ban temporaire, voir un inventaire | | | ✔ | ✔ | ✔ |
| Ban compte + PC, modifier un inventaire, événements et boss, redémarrage doux, recharger | | | | ✔ | ✔ |
| Permissions du staff | | | | | ✔ |
| Donner un grade | | | | | console seulement (ton choix du 22/10) : à garder ainsi ? |
