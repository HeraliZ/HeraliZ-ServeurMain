# À tester en jeu — cahier des charges du 28/10

Rien de cette liste n'a été testé en jeu par Claude. Tout est fabriqué (`construire.py`) et contrôlé (`verifier.py` :
0 problème). Canaux réseau changés (armes, bases, pnj, clans, grades, loot) : joueurs et serveur doivent avoir les jars de
la **même** fabrication, puis redémarrer le serveur.

Versions : Tactique 2.39.0, CRac 1.28.0-beta, Quêtes 1.14.0, Grades 2.8.0, Clans 3.8.0, Loot 0.13.2, Utils 2.26.1,
Bunker 0.13.2, Noyau 1.0.5, Spartan 2.9.4.

## À faire en jeu par le staff d'abord
- Poser les points de la visite du Guide (debout là où la caméra doit être, en regardant la chose) :
  `/guide point casino|armurier|coffre|caisses|borne|recycleur`, puis `/guide visite` pour regarder.
- Si Jack a été modifié au Registre du marchand : « Revenir au fichier » dans son éditeur (sinon ses nouveaux prix ×4 ne
  s'appliquent pas).

## 1. Couteaux
- [ ] Étoiles des Push, Skeleton et Stiletto comme dans le cahier (le « Marble » du Stiletto est le Nero Marble, 1★).

## 2. Tutoriel du spawn et Varga
- [ ] Nouveau joueur : traînée verte au sol et contour vert vers le Guide, puis vers Varga après la visite ; plus rien une
      fois qu'on a parlé à Varga.
- [ ] Visite : PNJ puis casino, Table d'armurier, coffre (VIP dit réservé aux grades), caisses, borne, recycleur (~2 min 30,
      Espace pour passer). Point non posé : phrase seule.
- [ ] Varga, bouton « Questions » : 7 questions, réponses lisibles en fr / en / ru.

## 3. Loot
- [ ] Étoiles blanches (dorées en T7) visibles sur les conteneurs pleins, même avec « Particules : minimales ».
- [ ] Conteneur vidé : plus d'étoiles en moins de 2 s.

## 4. Clans
- [ ] XP par niveau ×3, prix de l'arbre ×2 (`serveur/config/heralisz_clans/niveaux.json`, `facteur_xp`,
      `facteur_prix_arbre`). Prix affiché = prix payé.

## 5. Bases
- [ ] Plus de rampe triangle dans la roue du plan ; celles déjà posées restent et marchent.
- [ ] Fenêtre à barreaux ou à vitre (vitre transparente comme avant) : on voit à travers ; la lumière passe ; balles et
      joueurs toujours arrêtés.
- [ ] Anti-ESP : joueur derrière une fenêtre ouverte, à barreaux ou à vitre, même de biais : il reste visible, il ne
      « disparaît » jamais. Derrière un mur plein ou une porte fermée : caché. Le journal dit « cases de construction de
      CRac lues pièce par pièce ».
- [ ] Bâtisseur : caisse en métal à 300 hérals (si le Bâtisseur a été modifié au Registre du marchand : « Revenir au
      fichier »).
- [ ] Coffre visé au milieu d'une fondation : il reste au milieu (plus décalé d'un bloc). Près d'un mur : collé au mur.
- [ ] Plancher sur demi-murs : coffres et caisse en métal posés à plat sur le plancher (plus rien qui flotte) ; armoire et
      four restent refusés à cet endroit.
- [ ] R / Maj + R, objet en main : tourne coffre, caisse, armoire d'un quart de tour ; plus de coffre de travers près
      d'un triangle.
- [ ] Tutoriel des bases dans le monde `crac:tuto` : parcelle plate de 64 × 64 par joueur, murs invisibles, deux joueurs
      en même temps ne se voient pas ; fin : cabane effacée, retour au spawn ; aucune alerte Spartan au bord.
- [ ] Coffres de base à la Table d'armurier (onglet Rangement) : coût doublé.

## 6. Administration
- [ ] `/eco donner|retirer|definir <joueur> <montant> [motif]` : op 4 connecté (ou console), joueur hors ligne permis.
- [ ] Panel (P) : pseudos des joueurs en ligne bien affichés, à toutes les tailles de fenêtre.
- [ ] Onglet « Commandes du Fondateur » : toutes les commandes, description, case par grade ; un grade coché peut taper
      la commande, les autres non. Vérifier aussi une commande de plugin (permission Bukkit).

## 8-9. Crafts et prix
- [ ] Cartes d'accès : plus de clic droit avec les 4 fragments ; seulement à la Table d'armurier, onglet Divers.
- [ ] Jack : prix ×4 (240 / 600 / 1 400 hérals).
- [ ] Munitions : poudre I pour 9 mm, .45, 12 g ; poudre II pour les autres.

## 10. Sanctions (LiteBans)
- [ ] Ban / warn / mute : plus rien dans le chat public ; le staff le voit en une ligne « [Staff] ». Staff non-op : s'il ne
      le voit pas, vérifier la permission `litebans.notify.silent`.
- [ ] Message reçu par le sanctionné : une ligne (mute, warn), 3 lignes à l'écran de ban.

## 11. Cadavres et réapparition
- [ ] Cadavre vidé : disparaît tout de suite, même loin de tout joueur.
- [ ] Écran de réapparition 20 % plus grand, textes nets, rien ne déborde (aussi en russe).
- [ ] `/suicide` : 5 min entre deux (survit à la déconnexion), staff exempté.

## 12-14. Soins, faim, combat
- [ ] Trousse AI-2 (et autres soins) : soin appliqué, objet consommé ; si le serveur refuse, la barre repart à zéro.
- [ ] Faim environ 3 fois plus lente que Minecraft (`faim_facteur` 0,35 dans `heraliz_noyau-server.toml`).
- [ ] Battes, haches, couteaux GeckoLib : dégâts divisés par 2 (infobulle comprise) ; armes TaCZ et mêlée LR inchangées.
- [ ] Jambe cassée : on frappe de nouveau normalement (zombies et joueurs) ; lenteur et saut limité toujours là.

## Hôtel des ventes (`/hdv`, Grades 2.9.0, Quêtes 1.15.0)
Nouveau canal réseau `heraliz_grades:hdv` : jars joueurs et serveur de la même fabrication. Rien testé en jeu.

À faire d'abord par le staff : poser le PNJ au spawn, `/pnj creer commissaire` (fichier
`serveur/config/heraliz_quetes/pnj/commissaire.json`, apparence à changer avec `/pnj apparence commissaire moi`).

- [ ] `/hdv` au spawn : l'écran s'ouvre (soldes hérals et PB en haut à droite) ; sur la carte, en combat, à terre, en
      match, au tutoriel : refusé avec un message. Le PNJ commissaire : bouton « Ouvrir l'hôtel des ventes ».
- [ ] Écran fidèle aux maquettes (`maquettes/hotel_ventes/`) en fr / en / ru, à plusieurs tailles de fenêtre : rien ne
      déborde, textes nets.
- [ ] Vendre : clic sur un objet, hérals ou PB, prix, durée, « Mettre en vente » (ou Entrée) : l'objet quitte
      l'inventaire. Objets grisés (couteaux à vie, arme à camo à vie, objet de match, de quête, sac plein, clé de caisse) :
      raison au survol, refusés.
- [ ] Arme chargée mise en vente : les balles reviennent dans l'inventaire, l'acheteur la reçoit déchargée.
- [ ] 6e vente : refusée (5 au plus). Prix PB au-dessus du plafond de la rareté : refusé (texte rouge sous la durée).
- [ ] Acheter en hérals (deux clics) : hérals retirés, objet dans l'inventaire (ou dans « Mes ventes » si plein) ; le
      vendeur voit « Vendu » et récupère ses hérals au spawn.
- [ ] Acheter en PB : l'acheteur paie prix + 10 % (arrondi au supérieur), le vendeur reçoit le prix affiché sur son compte
      du site (« PB versés sur ton compte »). Solde insuffisant : refusé, rien ne bouge.
- [ ] Sa propre offre : pas de bouton d'achat. Deux joueurs qui achètent la même offre en même temps : un seul l'a.
- [ ] Retirer une vente : l'objet revient (cases libres) ; vente expirée (essayer avec une durée courte dans `hdv.json`) :
      l'objet attend dans « Mes ventes ».
- [ ] « Tout récupérer » avec l'inventaire plein : rien n'est perdu ni jeté par terre ; après avoir fait de la place, le reste.
- [ ] Recherche (nom en français, en anglais, en russe), catégories, Hérals / PB, tri, pages (molette).
- [ ] Redémarrage du serveur avec des ventes en cours : tout est toujours là ; déconnexion pendant un achat en PB : l'achat
      se termine, l'objet est remis à la connexion au spawn.
- [ ] `/wipe <joueur>` : ses ventes et ce qui l'attendait disparaissent.
- [ ] Journal : `heraliz_grades/hdv/journal.log` (chaque mise en vente, achat, versement, remise).
