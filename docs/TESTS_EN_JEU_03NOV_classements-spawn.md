# Essais en jeu — chantier « classements-spawn » (retours du 03/11)

Clans **3.14.0** (`clans`). Demande du propriétaire : « les commandes pour mettre au spawn le leaderboard monnaie, kill
et clan », puis : « un truc simple qui ne fait pas laguer, genre un hologramme qui affiche les 8 premiers ». Avant, il
n'y avait que `/herals classement`, `/clan top` et `/saison classement` dans le chat.

Chaque hologramme est **une seule entité d'affichage de texte de Minecraft** (`minecraft:text_display`) : un titre et
8 lignes « rang. pseudo [TAG] — valeur », sans fond, toujours tourné vers le joueur. Le jeu des joueurs le dessine
lui-même : pas de bloc, pas de dessin à nous, pas de nouvel objet. **Rien n'a été testé en jeu ni compilé** (Gradle ne
marche pas sur la machine de travail) : la compilation sera faite par le contrôle de branche de GitHub. Rendus :
`python modules/clans/outils/apercu_hologrammes.py DOSSIER` (approximatifs : le jeu peut décaler le texte d'un pixel).

## Commandes du staff (op niveau 2)

| Commande | Effet |
|---|---|
| `/classement poser argent` | les 8 joueurs les plus riches (hérals du compte) |
| `/classement poser kills` | les 8 joueurs qui ont tué le plus de joueurs |
| `/classement poser clans` | les 8 premiers clans de la saison (comme l'onglet Saison) |
| `/classement retirer` | retire l'hologramme le plus proche (10 blocs au plus) |
| `/classement maj` | met tout de suite à jour les hologrammes chargés |
| `/classement liste` | hologrammes chargés : genre et position |

Pose : sur le bloc visé (16 blocs au plus ; un mur visé : dans la case devant lui), sinon aux pieds du staff. Le bas du
texte est 0,3 bloc au-dessus du sol ; l'hologramme fait environ 3,5 blocs de large et 2,5 de haut. Deux hologrammes ne
peuvent pas être posés à moins de 1,5 bloc l'un de l'autre.

## À vérifier

### Pose et retrait
- [ ] `/classement poser argent` en visant le sol du spawn : texte qui flotte au-dessus du bloc, tourné vers soi quand
      on tourne autour, lisible de nuit (plein éclairage) ; message « Hologramme « les plus riches » posé ».
- [ ] Sans rien viser (ciel) : posé à ses pieds. Deuxième pose au même endroit : refus « Impossible ici… ».
- [ ] On ne peut ni le frapper, ni cliquer dessus, ni le pousser ; une balle passe au travers.
- [ ] `/classement retirer` à côté : il disparaît ; plus loin que 10 blocs : « Aucun hologramme… ».
- [ ] Redémarrer le serveur : les hologrammes sont toujours là, **une seule fois chacun** (`/classement liste`), et se
      remettent à jour dans la seconde où leur tronçon est chargé.
- [ ] Si un plugin de protection du spawn refuse l'entité : le message de refus s'affiche (le dire, on adaptera).

### Contenu
- [ ] **Argent** : même ordre que `/herals classement` (solde du compte de hérals ; la banque d'un clan n'est à personne
      et ne compte pas) ; montants avec l'espace des milliers et le jeton doré ; tag du clan en couleur.
- [ ] **Kills** : tuer un joueur (deux comptes de clans différents, ou sans clan) : +1 au tueur au plus tard 2 min après
      (ou tout de suite avec `/classement maj`). Ne comptent PAS : un zombie, un camarade de clan, un joueur de la même
      adresse IP, un joueur tué moins de 60 s après sa réapparition, le même joueur avant 10 min, un kill en match du
      ClashWar, un bot `/bots`. Un joueur sans clan compte aussi.
- [ ] Au premier démarrage avec cette version, les kills partent des statistiques des clans (journal : « kills de depart
      repris des clans : N joueur(s) ») ; fichier `heralisz_clans/kills_joueurs.json` créé.
- [ ] **Clans** : même ordre que l'onglet Saison (touche K, somme des places) ; titre « Saison N · meilleurs clans » ;
      valeur = points PVE + PVP + événements ; un clan sans cœur posé n'apparaît pas.
- [ ] Un joueur déconnecté apparaît avec son pseudo (jamais un identifiant).
- [ ] Un staff « caché » (panel P, Se cacher) n'apparaît ni dans « Les plus riches » ni dans « Meilleurs tueurs » (au
      plus 2 min après).
- [ ] Serveur neuf : « Personne au classement pour l'instant ».
- [ ] `/wipe <joueur>` : ses kills repartent de 0 ; `/wipe tout` : tous les kills repartent de 0.
- [ ] Jeu en anglais et en russe : titres, « kills », « pts », pied traduits.

### Performances
- [ ] `/diag profil` au spawn avec les trois hologrammes : rien de visible pour les classements (calcul toutes les
      2 min, seulement si un joueur est à 64 blocs ; rien par tick sinon ; texte renvoyé aux joueurs proches seulement
      s'il a changé). Réglages : section `[classements]` de `config/heralisz_clans-common.toml` (`maj_minutes` = 2,
      `distance_joueurs` = 64).
- [ ] Côté joueur : aucune baisse d'images par seconde devant les hologrammes (texte dessiné par le jeu).

## Pas fait / questions
- Les kills ne repartent pas de 0 à une nouvelle saison (`/saison reset`) : seulement avec `/wipe`. À trancher.
- Le staff non caché apparaît dans les classements (seul le vanish est retiré).
