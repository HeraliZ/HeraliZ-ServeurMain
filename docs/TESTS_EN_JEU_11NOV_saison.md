# Essais en jeu — saison des clans, retours du propriétaire du 11/11

Rien n'a été essayé en jeu par Claude. Jars joueurs ET serveur à mettre à jour ensemble (canal des clans passé à la
version 11 : un jeu pas à jour est refusé à la connexion ; le launcher met les joueurs à jour tout seul).

## 1. Onglet Saison : seulement les points dans les cases (Clans 3.15.0)

Le petit chiffre coloré à côté des points était la PLACE du clan dans la catégorie, et « Gén. » la somme des trois
places. Maintenant : chaque case PVE / PVP / Events ne montre que les points ; la place est dans l'infobulle de la case
(avec les points exacts : 0,01 par zombie, invisible en points entiers) ; la colonne « Gén. » montre la place au
classement général (1er, 2e…), et son infobulle explique le calcul (somme des 3 places, plus petit = mieux).

À vérifier (touche K, onglet Saison) :
- [ ] chaque case ne montre plus qu'un nombre (plus de petit chiffre coloré) ;
- [ ] survol d'une case PVE : « PVE », « Points : 0,37 » (les centièmes des zombies se voient), « Place : 1er sur N
      clans » (ou « seul clan classé ») ; la case survolée prend un léger fond de sa couleur ;
- [ ] colonne « Gén. » : « 1er », « 2e »… ; au survol : « Classement général : 1er sur N clans », « = somme des places
      dans les 3 catégories », « 1 + 2 + 1 = 4 (plus petit = mieux) » ;
- [ ] en anglais (1st, 2nd, 3rd, 11th) et en russe (1-е) : rien ne déborde ;
- [ ] en bas, « Place 3 au général · 1 548 points » : même total qu'avant.

## 2. Gains de points de saison (Clans 3.15.0, Quêtes 1.21.1, Loot 0.16.1, Événements 1.1.5, Monde 1.1.2)

Chaque source a été suivie de bout en bout (action en jeu → appel à la saison → score → onglet).

| Source | Cause trouvée | Correction |
|---|---|---|
| Arme fabriquée | **Vrai défaut** : TaCZ (et TaCZ Addon) posent l'arme fabriquée AU SOL aux pieds du joueur ; notre contrôle anti-triche retirait toute arme ramassée au sol table ouverte, donc chaque arme fabriquée : jamais aucun point | l'arme que la table vient de poser (objet au sol tout neuf, sans délai de ramassage) compte une fois ; une arme jetée par un autre joueur (2 s avant de pouvoir la ramasser) ne compte toujours pas |
| Salle à carte rouge / bleue / verte | **Vrai défaut** : la toute première ouverture d'un conteneur de la salle « consommait » ses points, même celle d'un membre du staff en créatif (qui n'en recevait aucun, sans message) ; ensuite plus personne ne les prenait avant la recharge | les points vont au premier joueur EN SURVIE qui ouvre un conteneur de la salle depuis sa recharge ; le staff en créatif est prévenu et ne les consomme plus. Une salle non déclarée (`/salle liste`) n'a pas de points de salle : ses conteneurs sont ordinaires |
| Boîte noire d'hélico et autres objets d'événement vendus à Mila | code bon de bout en bout ; mais un objet venu d'un événement lancé par le staff (commande ou panel) sans `avec_points`, d'un hélicoptère touché en créatif, ou donné par `/event objet` est un **objet d'essai** : hérals seulement, 0 point (règle du 25/10), et rien ne le disait | Mila dit au survol « Objet d'essai (staff) : hérals seulement, aucun point de saison » ; après une vente sans points, la raison (essai, sans clan, sans cœur, créatif, plafond du jour) |
| Largage militaire | code bon : 8 points au premier joueur en survie qui ouvre la caisse d'un largage du planning (toutes les 3 h 30). Un largage du staff (bouton du panel, `/event lancer largage`) est sans points (règle), une caisse ouverte en créatif garde ses points : rien n'était dit | messages : « Largage lancé par le staff : aucun point (essai) », « Mode créatif : ce largage garde ses points… », et la raison quand il n'y en a pas (sans clan, sans cœur, plafond de 3 par jour) |
| Quêtes de Varga | code bon (fin d'une des 39 quêtes de l'histoire de Varga → points, plafond commun de 50 par joueur et par jour) ; une quête finie en créatif était refusée en silence. Les **quêtes du jour** ne donnent pas de points de saison (système à part) | message en créatif ; **6 points au lieu de 10** (`quete_principale` de `saison.json`) |
| Zombie tué | points bien donnés, mais 0,01 par zombie : l'onglet arrondit à l'unité (100 zombies pour voir 1 point) | infobulle de la case PVE avec le compte exact (point 1) ; une ligne par joueur et par minute au journal du serveur |
| ClashWar | code relu : fin du tournoi → récompenses → saison (places 100 / 60 / 40 / 20 et participation 5, × 2) : bon. Conditions : 4 clans différents au moins (`saison_clans_min` de `clashwar.json`), saison en cours, cœur posé | rien à corriger ; une ligne au journal par clan payé, et la raison d'un refus |
| Toutes | en créatif ou en spectateur, certaines sources refusaient en silence et d'autres pas (kills, objets vendus) ; impossible de savoir pourquoi un point manquait | règle unique : jamais de points en créatif ou en spectateur (message dans le chat, au plus toutes les 2 min) ; journal du serveur (ci-dessous) |

**Journal du serveur** (console du Ptero, ou workflow « Journaux du serveur » avec le filtre `[saison]`) :
- chaque point gagné ou perdu : `[saison] +40 pve [TAG] salle_rouge (Pseudo) -> clan : pve 52.37, pvp 1, events 0` ;
- zombies et conteneurs : une ligne par joueur et par minute (`[saison] +0.37 pve [TAG] Pseudo (petites actions de la
  derniere minute : zombie x37 +0.37)`) ;
- chaque point refusé : `[saison] pas de points : salle_rouge (Pseudo [TAG]) : clan sans coeur pose dans sa base` (sans
  clan, pas de saison, créatif, plafond, même adresse IP, victime sans arme…), la même raison au plus toutes les 10 min.

Pour tester (en SURVIE, dans un clan dont le cœur est posé dans la base : onglet Saison « Cœur posé ») :
- [ ] Table d'armurier : fabriquer une arme Rare → +4 PVE (console : `craft_rare`) ; fermer la table tout de suite après
      le clic : compte quand même ; un ami jette une arme à tes pieds table ouverte : rien ;
- [ ] salle rouge : `/salle liste` (la salle doit y être), `/salle recharger <nom>` ; ouvrir un conteneur en créatif :
      message « Mode créatif… », aucun point ; repasser en survie, ouvrir un conteneur de la salle : +40 PVE ; rouvrir :
      rien de plus jusqu'à la prochaine recharge ;
- [ ] Mila : un objet de `/event objet boite_noire_helico` dit « Objet d'essai » au survol, se vend sans points avec le
      message ; un objet d'un largage du planning (ou `/event lancer largage avec_points`) donne ses points ;
- [ ] hélicoptère `/event helico lancer avec_points`, abattu EN SURVIE : sa boîte noire vendue à Mila donne 12 points ;
- [ ] largage `/event lancer largage avec_points` ouvert en survie : +8 EVENTS ; ouvert d'abord en créatif : message, et
      le premier joueur en survie prend les points ; largage du bouton du panel : « Largage lancé par le staff : aucun
      point (essai) » ;
- [ ] une quête de l'histoire de Varga finie en survie : +6 PVE ;
- [ ] tuer des zombies : au bout d'une minute, la ligne `petites actions` dans la console ; survol de la case PVE de
      l'onglet Saison : les centièmes ;
- [ ] en créatif, n'importe quelle action : message gris « Mode créatif ou spectateur : aucun point de saison… » (au plus
      toutes les 2 min).
