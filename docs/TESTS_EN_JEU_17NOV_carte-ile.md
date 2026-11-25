# Carte en île : à faire et à tester en jeu (17/11)

Ce qui est en ligne avec le redémarrage du 17/11 : eau du labo sous-marin (sans tenue de plongée, 2,5 s d'air puis
2,5 cœurs par seconde, noir en profondeur), vitres du labo incassables, zones (capture, extraction, construction) plus
jamais vues à travers les blocs, éponge mouillée = éponge, portes des bunkers, cartes et lecteurs des deux bunkers.
**Rien de tout cela n'a été testé en jeu** : tout est à vérifier ci-dessous.

Fait depuis la console juste après le redémarrage (lot `serveur/commandes/bunkers_17nov.txt`) : « Bunker des Bois » et
« Bunker du Pic » retirés de la carte M ; six zones d'extraction renommées (Plage du Pic, Plage est du Pic, Forêt du
Grand Bois, Clairière du Grand Bois, Grande Prairie, Forêt de Val-Fleuri).

## 1. Portes des deux bunkers

| Bunker | Porte (écluse à air ronde) | Lecteur à poser | Carte |
|---|---|---|---|
| Bunker des Bois | -281 85 161 | `heralisz_bunker:card_reader_bunker_bois` (orange) | `heralisz_bunker:keycard_bunker_bois` |
| Bunker du Pic | 838 120 -961 | `heralisz_bunker:card_reader_bunker_pic` (violet) | `heralisz_bunker:keycard_bunker_pic` |

Pour chaque bunker, en créatif :
1. Retirer les lecteurs déjà posés à côté de la porte (en créatif ils se cassent) : **la porte de bunker s'ouvre avec
   n'importe quel lecteur posé à 2 blocs**, un lecteur rouge oublié l'ouvrirait avec une carte rouge.
2. `/give @s heralisz_bunker:card_reader_bunker_bois 2` (ou `_pic`), puis poser un lecteur **dehors** et un **dedans**,
   à 2 blocs au plus de la porte.
3. Regarder la porte (ou un de ses lecteurs), à 8 blocs au plus, et taper :
   - `/salle bunker ajouter` → annonce « La porte du bunker a été ouverte ! Elle reste ouverte 20 min. » ;
   - ou `/salle bunker ajouter Bunker des Bois` → annonce « La porte du Bunker des Bois a été ouverte ! … ».
   La réponse dit combien de lecteurs sont à côté (il en faut 2).
4. `/salle bunker` en regardant la porte : « porte de bunker, fermée, 2 lecteur(s) à côté ».

À tester (de préférence en **survie** : en créatif, la carte ne s'use pas) :
- [ ] Clic droit sur la porte : elle ne s'ouvre pas (message « passe une carte dans le lecteur à côté »).
- [ ] `/give @s heralisz_bunker:keycard_bunker_bois`, glisser la carte dans le lecteur du dehors : cinématique, la porte
      s'ouvre, **tout le serveur** lit l'annonce dans le tchat.
- [ ] La carte du Pic dans le lecteur des Bois : refusée (et l'inverse) ; une carte rouge : refusée.
- [ ] `/salle bunker` : « ouverte, se ferme dans 19 min … ».
- [ ] 5 minutes avant la fin, tout le serveur lit dans le tchat « La porte du bunker se ferme dans 5 min : sans carte,
      plus de sortie ! » ; au bout de 20 min, la porte se ferme ; qui est dans le passage est repoussé.
- [ ] Enfermé dedans : la carte dans le lecteur du dedans rouvre la porte (20 min de plus, nouvelle annonce).
- [ ] Infobulle de la carte : « Ouvre les lecteurs du Bunker des Bois », « Passages restants : 2 » ; au 2e passage,
      elle disparaît de l'inventaire et se casse dans le lecteur (bruit et éclats, comme un outil de Minecraft).
- [ ] Un redémarrage pendant que la porte est ouverte : elle garde son heure de fermeture.

Réglages (`config/heralisz_bunker-common.toml`) : `[bunkers] ouvertureMinutes = 20`, `avertirSecondes = 300` ;
`[keycards] usesBunker = 2`. Les cartes sortent des coffres T7 et des salles rouges (environ 1 coffre T7 sur 100 donne
l'une des deux) ; elles sont Légendaires et suivies par l'anti-duplication.

## 2. Usure des cartes (18/11)

Passages avant que la carte casse : **verte 4, bleue 3, rouge 2, cartes des bunkers 2** (`[keycards]` de
`config/heralisz_bunker-common.toml`). En créatif, la carte ne s'use pas : essayer en survie.
- [ ] Infobulle : « Passages restants : N » ; barre d'usure sous la carte après le premier passage.
- [ ] Au dernier passage, la porte s'ouvre quand même ; la carte disparaît de l'inventaire et se casse dans la fente du
      lecteur à la fin de la lecture (bruit d'objet cassé, éclats de la carte).
- [ ] Aucune réparation : enclume, meule, Raccommodage, recycleur (refusée), table d'armurier.

## 3. Salles à carte

Coordonnées données le 17/11 (lecteur posé devant la porte). La porte doit aller avec la couleur du lecteur :
- verte : Trappe de silo, Porte cargo, Porte coulissante QE, Porte coup-de-feu ;
- bleue : Porte d'accès sécurisé, Porte de confinement QE, Trappe étanche ;
- rouge : Porte blindée Vault-Tec, Grande porte de véhicule.

| # | Lieu | Coordonnées | Couleur | Commande de création |
|---|---|---|---|---|
| 1 | Arctique | -160 79 -1824 | rouge | `/salle creer arctique` |
| 2 | Plateforme pétrolière | -2146 85 259 | rouge | `/salle creer oilrig` |
| 3 | Aérodrome | -51 67 -698 | rouge | `/salle creer aerodrome` |
| 4 | Base Sentinelle | 1072 68 -294 | bleue | `/salle creer sentinelle` |
| 5 | Labo sous-marin | 1956 21 2067 | bleue | `/salle creer labo` |
| 6 | Zone radioactive (Radio) | 1388 83 1407 | bleue | `/salle creer radio` |
| 7 | Silo | -1596 103 -783 | bleue | `/salle creer silo` |
| 8 | Port-Margot | -1775 72 923 | verte | `/salle creer portmargot` |
| 9 | Bellerive | -481 106 1704 | verte | `/salle creer bellerive` |
| 10 | Clairval | 121 72 532 | verte | `/salle creer clairval` |
| 11 | Belcourt | -960 100 -783 | verte | `/salle creer belcourt` |
| 12 | Prison | 1605 68 -1143 | verte | `/salle creer prison` |

Il en manque pour le plan 8 vertes / 5 bleues / 3 rouges : **1 bleue** (proposée : Mirage) et **3 vertes** (proposées :
Valmont, Marbois, Gaz).

Pour chaque salle :
1. `/tp @s <coordonnées>` puis `/salle baguette` (une seule fois suffit).
2. Clic gauche sur un bloc d'un coin de la salle, clic droit sur le coin opposé : la boîte doit prendre **toute la salle
   et ses conteneurs de loot** ; la porte doit être à 3 blocs au plus de la boîte. `/salle selection` donne la taille.
3. `/salle creer <nom>` (tableau ci-dessus) : la couleur est prise de la porte. Si la réponse dit « Aucune porte à carte
   autour » : vérifier la porte, ou donner la couleur (`/salle creer silo bleue`).
4. Si la porte n'est pas liée : la regarder et `/salle lier <nom>`.
5. `/salle voir <nom>` : contour montré 30 s. `/salle liste` : toutes les salles, avec leur état.
6. Les conteneurs de loot dans la boîte sont remplis par la salle, tous à la fois, chacun à SON niveau (18/11 : plus de
   niveau imposé par la couleur ; avant, tout passait en T5 / T6 / T7). Pas de conteneur dedans = pas de loot.
   Après la mise à jour : `/salle recharger <nom>` pour remplir tout de suite une salle à son vrai niveau.

Quand c'est fait, le dire : la liste complète est vérifiée depuis la console (`salle liste`).

À tester :
- [ ] Carte de la bonne couleur : la porte s'ouvre, 20 s pour entrer (18/11), elle reste ouverte tant que quelqu'un est dedans,
      puis se ferme 4 s après la sortie du dernier (18/11, avant 2 s) ; mauvaise couleur : refusée.
- [ ] Loot de la salle au bon niveau ; il revient 30 min (verte), 2 h (bleue), 4 h (rouge) après la sortie du dernier.
- [ ] Déconnexion dans une salle : à la reconnexion, on est devant sa porte.

## 4. Quêtes de Varga : plus rien à poser (18/11)

Décision du propriétaire du 18/11 : les insignes, les carnets et la sacoche des déserteurs et la boîte noire se trouvent
dans les caisses pendant que l'étape est en cours ; les salles à carte sont reconnues toutes seules. Aucun point de quête.
- [ ] Quête « Le 7e régiment » : en ouvrant des caisses, « Tu trouves : Insigne du 7e régiment » à la fermeture de la
      caisse (bruit de ramassage tout de suite) ; jamais plus de 20 caisses d'affilée sans insigne ; 5 puis les rapporter.
- [ ] « Les déserteurs » : 3 carnets (le carnet s'ouvre à la fermeture de la caisse), puis la sacoche dans une caisse T4 ou
      mieux.
- [ ] « La boîte noire » : seulement dans les caisses de la zone de radiation de Radio (tenue hazmat).
- [ ] « Fin de classe » et « La salle rouge » : entrer dans n'importe quelle salle à carte de la bonne couleur.
- [ ] Mourir avec un insigne avant de le rapporter : il se retrouve dans les caisses (« Tu retrouves : … »).

## 5. PNJ du spawn

`/pnj liste` doit montrer : varga, mila, jack, jacqueline, doyen, commissaire, batisseur, recycleur, guide,
contremaitre. Pour un PNJ absent : se placer à l'endroit voulu, regard dans la bonne direction, `/pnj creer <id>`
(ex. `/pnj creer contremaitre`). Le déplacer : `/pnj deplacer <id>` (il vient là où on se tient). Le retirer :
`/pnj retirer <id>`.

## 6. Arrivée sur la carte, coffres T7, événements

- [ ] `/depart info` doit dire « prêt » ; `/depart liste` : les points et zones d'arrivée doivent être **sur l'île**.
      Sinon : `/depart zone ajouter <nom> <rayon>` (au centre d'un endroit dégagé) ou `/depart point ajouter <nom>`, puis
      `/depart essai tp` pour essayer.
- [ ] `/heralizloot point liste` : points des coffres T7 (anciens points de l'autre carte à retirer :
      `/heralizloot point retirer <nom>`).
- [ ] Événements (largage public toutes les 3 h 30, hélicoptère) : lieux tirés au hasard sur la carte, rien à poser ;
      `/event liste` montre le planning. Vérifier qu'un largage tombe bien sur l'île.

## 7. Le reste à vérifier

- [ ] Carte M : plus de « Bunker des Bois » ni de « Bunker du Pic » ; noms des extractions à jour (`/extraction list`).
- [ ] Labo sous-marin, **sans** tenue de plongée : 5,5 s d'air (18/11, avant 2,5), puis 2 cœurs par seconde (avant 2,5) ;
      sous y 52, tout noir.
      **Avec** la tenue : on respire et on voit correctement.
- [ ] Vitres du labo : un tir ne les casse pas (`/vitres pourquoi` en regardant une vitre dit pourquoi). 18/11 : elles
      cassaient encore ; maintenant une vitre qui touche l'eau ne casse jamais, partout sur la carte, et le labo est protégé
      même si le réglage n'est pas lu. Si une vitre du labo casse encore : `/vitres pourquoi` en la regardant, et m'envoyer
      ce qu'il répond.
- [ ] Téléportation (18/11), avec un compte **qui n'est pas du staff** (le staff passe outre) : dans une ville à drapeau,
      l'aérodrome, la prison, la radio…, et dans les zones de loot (`/tpa zone liste` : plateforme pétrolière, labo
      sous-marin), `/tpa <joueur>` est refusé ; une demande reçue ne s'accepte pas (elle attend : sorti de la zone, on
      l'accepte encore) ; on ne peut pas se téléporter vers quelqu'un qui y est. Régler une zone de loot : debout au centre,
      `/tpa zone ajouter <rayon> <nom>` (même nom : remplacée) ; `/tpa zone retirer <nom>`.
- [ ] Récolte (18/11) : 120 arbres, 90 gisements de pierre, 40 de métal, 14 de titane sur la carte (avant 30, 24, 10, 4),
      qui reviennent 2 fois plus vite ; 7 sur 10 apparaissent autour d'un joueur de la carte, entre 100 et 450 blocs de
      lui (jamais sous ses yeux), les autres n'importe où. `/recolte info` : combien sont posées et combien attendent. Au
      démarrage, elles sont tirées partout : elles se rapprochent des joueurs au fil des minutes.
- [ ] Tenues (18/11) : plongée, arctique et hazmat sortent des conteneurs de T3 à T7 (environ 2 de chaque pour 100
      conteneurs T5, 3 pour 100 en T6) ; les versions renforcées en T6 et T7, plus rares.
- [ ] Zones de capture et d'extraction : plus d'effet « rayons X », plus de traits vus à travers les blocs.
- [ ] Éponge mouillée : même texture que l'éponge.
- [ ] Drapeaux des villes : `/territoire info` ; une capture.
- [ ] Une extraction ouverte : la tenir, retour au spawn.

## 8. Bourse de hérals (18/11)

Les hérals gagnés sur la carte ne vont plus sur le compte : ils vont dans une **bourse** (un objet), qu'on revend à Mila.
Aujourd'hui, sur la carte, seul le recycleur de terrain donne des hérals (les zombies n'en donnent pas).
- [ ] Recycleur de terrain : un objet qui ne rend aucune pièce met ses hérals dans une bourse, dans les cases
      « Récupéré » ; les suivants s'ajoutent à la même bourse (3 puis 60 : une bourse de 63). Le montant est écrit en or
      dans le coin de la case ; au survol : « 63 hérals » et « À revendre à Mila, au spawn » (pas de « tombe au sol »).
- [ ] Parti plus loin pendant que la machine tourne : message « +N hérals · dans la bourse du recycleur » (un toutes les
      20 s au plus) ; un autre joueur peut prendre la bourse.
- [ ] Poser une bourse (au curseur) sur une autre dans l'inventaire : il n'en reste qu'une, avec la somme.
- [ ] Mila : la bourse se sélectionne comme un objet, au prix de ce qu'elle contient ; vendue, le compte monte d'autant.
      Pas de points de saison ni de quête du jour « vendre » pour une bourse.
- [ ] Hôtel des ventes : une bourse est refusée (« bourse : se revend à Mila, au spawn »). Le recycleur ne la prend pas.
- [ ] Mourir avec une bourse : elle tombe avec le reste.
- [ ] Anti-copie, avec le staff en créatif : copier une bourse (clic molette), vendre l'originale à Mila, puis la copie :
      la copie est refusée (« Mila refuse cette bourse : elle ne vaut plus rien »). `/bourse info` : nombre de bourses
      valables et total ; `/bourse donner <joueur> <hérals>` en crée une (essais, dédommagement).

## 9. Messages à l'écran : un seul style, plus petit (18/11)

Maquette validée (`maquettes/notifications/`) : verre sombre, trait de couleur à gauche, titre en gras et texte dessous,
sans icône ni capitales espacées.
- [ ] À droite de l'écran, empilés (le plus récent en haut, 4 au plus) : quêtes (« Étape terminée », « Tu trouves… »),
      hérals gagnés ou dépensés (« +250 hérals », « Quête · solde … »), hôtel des ventes (« AK-47 vendu à … · 1 200
      hérals », « … acheté »), clans (invitation, capture, niveau…), extractions ouvertes (une vague = un seul message qui
      se met à jour), fermetures d'extraction, événements (largage…), bourse (« +60 hérals · dans la bourse… »).
      Sous un inventaire ou un menu, ils attendent : ils s'affichent en le fermant.
- [ ] Au-dessus de la barre d'objets : les petits messages qui étaient en texte nu (« Impossible en combat »…) sont dans
      le même bandeau, trait de la couleur du message.
- [ ] Extraction : le chrono en haut au milieu est plus petit (« Extraction » et le temps, barre fine, « Reste dans la
      zone » / « Reviens dans la zone ! » et le temps de la zone).
- [ ] Ville à drapeau : panneau en haut à gauche dans le même style (nom de la ville, « Zone libre · drapeau à 25 m »,
      capture en cours et sa barre).
- [ ] Rien ne se chevauche avec les effets en haut à droite, la Purge en haut à gauche, les cœurs et la faim.

## 10. Retours du 18/11 au soir (zombies, récolte, explosifs, écrans, tchat, chiffres)

Réglages des zombies : section `[zombies]` de `serveur/defaultconfigs/heralisz_tactical-server.toml` (multiplicateur des
balles, tête, sniper, armure). Récolte : `serveur/config/heraliz_recolte/recolte.json` (relu tout seul).
- [ ] Zombies : une balle dans la tête au fusil de précision (Dragunov, M82A3, M95, AWP, M200…) tue n'importe quel zombie,
      même un Zombie Brute. Les .50 (M82A3, M95) font enfin des dégâts aux zombies (avant : 0).
- [ ] Zombies : fusil 5,56 ou pistolet 9 mm, une balle dans la tête tue un marcheur ou un coureur ; au corps, environ
      3 balles pour un marcheur, 5 pour un costaud. Au corps à corps (couteau, hache…), rien ne change : ils encaissent.
- [ ] Récolte : bien plus d'arbres et de gisements partout (3 fois plus), mais chacun donne moins : arbre 10 bûches,
      pierre 10 à 14, métal 7 à 12, titane 4 à 8 (tiré au premier coup). Avec la hache-pioche de survie : plus vite, et
      plus souvent le haut de la fourchette.
- [ ] Zone de neige (île arctique, au nord) : beaucoup plus de ressources qu'ailleurs (4 sur 10 de la carte y sont),
      mais pas plus de matière chacune. Elles apparaissent quand un joueur y va.
- [ ] Explosifs dans le loot : C4 ou 1 à 2 charges explosives, environ 1 conteneur T7 sur 12, T6 1 sur 50, T5 1 sur 200
      (jamais avant T5).
- [ ] Écrans en fenêtre (hôtel des ventes, quêtes, clans, kits…) : plus grands qu'avant dans une fenêtre un peu plus
      petite que l'écran (par quarts : 2,5 ou 2,75 au lieu de 2) ; en plein écran 1080p, rien ne change. Textes nets.
      Le casino et les caisses gardent leur taille d'avant.
- [ ] Tchat : le grade (« FONDATEUR »…) n'est plus écrit deux fois l'un sur l'autre ; il reste en gras.
- [ ] Chiffres de dégâts (joueur touché, cible d'essai) : écrits net, en gras, avec un contour sombre, même taille de
      loin ; doré pour la tête.
- [ ] Recycleur : « Chance de chaque pièce » (chaque pièce est tirée à part, on peut en avoir plusieurs à la fois) et
      « Aucune pièce : 23 % » ; les chiffres ne s'additionnent donc pas à 100 %.

## 11. Tutoriel de Varga et heure du serveur (18/11, dans la nuit)

- [ ] Pendant la visite du Guide ou le tutoriel des bases, touche T : la fenêtre « Appeler le staff » a un bouton rouge
      « Quitter le tuto ». Premier clic : il devient « Sûr ? Quitter » ; second clic : retour au spawn, kit repris,
      inventaire rendu, chat revenu, bandeau « Tutoriel quitté », Varga donne ses missions. Le tutoriel ne revient pas
      (le staff peut le relancer avec `/tuto rejouer <joueur>`).
- [ ] Tutoriel : démolir une fondation qui porte des murs : la fondation ET les murs tombés sont rendus (avant : la
      fondation seule, on restait bloqué).
- [ ] Fin du tutoriel (normale ou `/tuto finir`) : Varga ne dit plus que 2 messages courts, à 8 s d'écart (la cabane
      disparaît, le matériel est dans le coffre du clan ; puis `/sethome <nom>` dans la vraie base, `/home` du spawn).
- [ ] ClashWar : les heures des messages sont à l'heure française (avant : une heure de retard). Le tournoi part
      maintenant à 20 h 30 heure française (avant : 21 h 30 en hiver). Quêtes de clan : le tirage du jour aussi à
      l'heure française.

## 12. ClashWar (18/11, dans la nuit)

- [ ] Fil des éliminations (en haut à gauche) : le nom de l'arme est celui de la barre d'objets (« M200 »), plus
      « kinetic… ».
- [ ] `/clashwar regarder` par un joueur qui n'est pas du tournoi (ni inscrit, ni éliminé) : depuis le spawn, il est mis
      en salle (inventaire mis de côté comme un inscrit), puis regarde le match en direct ; `/clashwar quitter` le
      ramène au spawn avec son inventaire. Hors du spawn : « Pour regarder le ClashWar, viens au spawn. »
- [ ] Arènes : murs invisibles tout autour (on ne tombe plus dans le vide). Les arènes déjà posées sont reposées toutes
      seules au premier match (leur empreinte change).

## 13. Hélicoptère (19/11)

Pour les essais : `/event helico lancer ici` (staff, hélicoptère à 48 blocs devant soi). Pour le toucher et qu'il attaque,
il faut être en **survie** (en créatif, il ne riposte pas). Réglages : `serveur/config/heraliz_evenements/helico.json`
(relus par `/recharger evenements`). **Rien de tout cela n'a été testé en jeu.**
- [ ] **De loin** : en s'éloignant (ou en le laissant s'éloigner), on le voit toujours à 100, 200, 300 blocs (petit dans le
      ciel, pales qui tournent), sans à-coups. Il ne disparaît plus vers 60-70 blocs. Quand il se rapproche, pas de saut
      ni de double hélicoptère au moment où il redevient « normal » (vers 64 blocs).
- [ ] De loin aussi : on le voit tourner dans ses virages, et tomber quand quelqu'un l'abat ; le bruit du rotor baisse
      avec la distance.
- [ ] De loin, les FPS restent les mêmes qu'avant (comparer avec et sans hélicoptère en vue).
- [ ] **Plus d'alerte** : aucun bip avant qu'il tire. La rafale part d'un coup, avec seulement le bruit du minigun.
- [ ] **Roquettes** : quand on lui tire dessus, il envoie de temps en temps 4 roquettes (fumée, flamme, « whoosh ») qui
      explosent au sol près de soi. À découvert, ça fait très mal (bien plus qu'une rafale) ; caché dans un bâtiment ou
      derrière un mur épais, presque rien. Aucun bloc cassé.
- [ ] Les roquettes ne blessent que ceux qui lui ont tiré dessus : un ami à côté qui n'a pas tiré ne prend rien.
- [ ] **Rage** : plus il est abîmé (fumée, puis feu), plus ses rafales sont longues et rapprochées, et plus les roquettes
      reviennent vite. Presque mort : environ deux fois plus de coups par rafale, et presque plus de pause.
- [ ] **Butin** : à l'épave, en plus du coffre T7, **deux conteneurs T6** (deux modèles différents, posés au sol, pas dans
      l'épave ni collés à elle). Pendant le feu, ils ne s'ouvrent pas.
- [ ] En essayant d'ouvrir pendant le feu (coffre T7 ou T6) : **aucun bruit de loquet**, seulement un petit message
      au-dessus de la barre d'objets : « Caisse en feu · s'ouvre dans 1 min 20 s » (le temps baisse).
- [ ] Après le feu : les deux T6 s'ouvrent (écran de fouille normal, loot T6, étoiles blanches tant qu'il reste quelque
      chose) ; ils disparaissent en même temps que le coffre T7 (environ 15 min après le feu), ou 30 s après avoir été
      vidés.
- [ ] Annuler pendant le feu (`/event annuler helico`) : coffre et T6 encore fermés disparaissent.
- [ ] Les deux T6 ne rejoignent pas la rotation du loot : après leur départ, aucun conteneur « fantôme » ne réapparaît au
      même endroit.

## 14. Écrans du ClashWar (19/11)

Maquettes validées : `maquettes/clashwar/` (1a : choix de classe, 2a et 2b : tableau de score). ClashWar 1.2.0 : tout le
monde doit avoir la nouvelle version (le launcher la donne ; une ancienne version ne peut plus se connecter).

Choix de classe, avant chaque manche :
- [ ] Panneau au centre : « Choisis ta classe », dessous le tour et la manche (« Quarts de finale · manche 1 sur 5 »), à
      droite le score du match (ton équipe d'abord) et un anneau avec les secondes (rouge à 3 s).
- [ ] 5 cartes : nom, touche 1 à 5, rôle (« Polyvalent »…), image de l'arme, son nom (« Pistolet Mitrailleur Vector »
      sur deux lignes), l'arme secondaire avec son vrai nom (« Glock 17 », « Deagle 50 », « HK-USP », plus en capitales),
      les grenades en petit sur la carte Tactique, les jauges Portée / Cadence / Mobilité.
- [ ] Sous les cartes, une seule fois, avec leurs images : « Toutes les classes ont aussi » tenue, dague, soins.
- [ ] Clic ou touche : la carte prend la couleur de l'équipe, « Ton choix ». Un coéquipier prend le sniper : chez les
      autres la carte Sniper est grisée, « Prise par … » en rouge, on ne peut plus la prendre. Une classe sans limite
      prise par un coéquipier : « Choisie par … ».
- [ ] En bas : « Encore à choisir : … » (coéquipiers qui n'ont pas choisi), « Sans choix à la fin : Assaut ». En 1 contre
      1 : toutes les cartes « Libre ».
- [ ] Écrit net, rien ne dépasse, en français, anglais et russe ; en fenêtre aussi.

Tableau de score en haut, pendant les manches :
- [ ] En haut au milieu : ton équipe à gauche (tag, une barre par joueur : pleine = en vie), le score en gros dans une
      case de la couleur de l'équipe, 3 petits traits dessous (manches gagnées), le temps au milieu, une barre fine qui se
      vide.
- [ ] Préparation : « Préparation » et le temps en ambre. Combat : « Manche 2 »…, le temps passe au rouge les
      10 dernières secondes.
- [ ] Mort subite : « Mort subite » en rouge avec un vrai temps (plus jamais « BOMBE ») ; hors de la zone, un bandeau
      rouge « Hors de la zone » sous le score avec la distance, qui part quand on revient dans la zone.
- [ ] Entre deux manches : « Manche à … » de la couleur de l'équipe gagnante, score déjà à jour.
- [ ] Une élimination : la barre du joueur se vide tout de suite, chez les deux équipes.
- [ ] Un joueur de la salle qui regarde le match (`/clashwar regarder`) voit le même tableau, sans « BOMBE » ni temps qui
      saute.
- [ ] Le fil des éliminations reste en haut à droite ; s'il venait sur le tableau (longue ligne, grande échelle
      d'interface), il passe dessous.

## 15. Symboles des messages (19/11)

Le ☢ de la zone irradiée était posé trop bas : DM Sans n'a pas ce symbole, Minecraft le prenait dans sa propre police.
Les symboles des messages (☢ ⚠ ❄ ✔ ✓ ✖ ✕ ☠ ★ ✦ ▲ ▼ ▶ ◀ ■ ● ◆) sont maintenant dessinés par une petite police à nous, aux
mesures de DM Sans, dans toutes nos polices.
- [ ] Zone irradiée : « ☢ Zone irradiée : combinaison Hazmat obligatoire » : le ☢ est centré sur les lettres, net, jaune.
- [ ] Zone de gaz (⚠) et froid (❄) : même chose.
- [ ] Un message de clan ou de base avec ⚠ ou ☠ (Purge, prise du cœur, vague d'assaut) : symbole à la bonne hauteur.
- [ ] En fenêtré et à une autre échelle d'interface : toujours net, rien ne bouge dans les écrans.

## 16. Camo Diamant refait (19/11, rendus validés par le propriétaire)

Repris de l'ancien (petites pierres serrées en relief), seuls les endroits bizarres sont corrigés.
- [ ] XM177 en main : la crosse n'est plus rayée ni « plate », elle est couverte de pierres comme le reste.
- [ ] Faces en biais (dessus du garde-main, chanfreins) : pierres rondes, plus de moucheté.
- [ ] En bougeant la vue : ça brille sans grésiller ; de loin, pierres bombées qui scintillent pierre par pierre.
- [ ] Les parties en or ne changent pas ; FPS pareils qu'avant avec le camo.

Tactique 2.59.8 (19/11, 2e correction : plus de « neige », toujours du diamant, sans or ajouté ; **pas testé en jeu**,
rendus hors jeu seulement) :
- [ ] SSG 08 en main (lunette, culasse, hausse, pontet) : des pierres nettes partout, plus de grisaille de vieille télé ;
      en marchant, en visant, au recul : les pierres restent les mêmes d'une image à l'autre, ça brille sans neige.
- [ ] AK-47 en visée : face arrière et bords nets ; les pierres collées à l'œil sont deux fois plus petites qu'avant.
- [ ] Glock 18C, XM177, M200, AN-94 : les petites pièces sont couvertes de pierres nettes (sur une petite face, les
      pierres sont posées pour elle, centrées : coupées aux arêtes, c'est normal).
- [ ] Parties en or : les mêmes qu'avant (sur la M200, un fin trait d'or près du chargeur, pris à la pièce voisine sur la
      texture, devient du diamant).
- [ ] FPS pareils qu'avant : Diamant en main, et plusieurs joueurs avec le Diamant autour.

## 17. Sept nouveaux camos (19/11, rendus validés par le propriétaire)

Opale (Épique), Marbre noir (Légendaire), Patine (Rare), Néon (Épique, animé), Rubis (Légendaire) et deux skins X
(Unique, avec effet 3D) : Phénix et Abysses. Tactique 2.58.0 : tout le monde doit avoir la nouvelle version (le launcher la
donne). Pour les essais (staff) : onglet HeraliZ · Tactique, groupe Camos, ou `/give @s heralisz_camo:camo_opale` (puis
`camo_marbre_noir`, `camo_patine`, `camo_neon`, `camo_rubis`, `skinx_phenix`, `skinx_abysses`). **Rien de tout cela n'a
été testé en jeu** (rendus hors jeu seulement).
- [ ] Les 7 objets : nom de la couleur de leur rareté (Patine bleu, Opale et Néon violet, Marbre noir et Rubis or, Phénix et
      Abysses rouge), icône (losange pour les camos, cadre rouge pour les skins X), textes en français, anglais et russe.
- [ ] Clic droit : le camo entre dans la collection à vie ; il se pose depuis l'onglet Skin de l'atelier d'arme.
- [ ] En main et vu par un autre joueur, chaque camo ressemble aux planches validées : Opale (nacre, éclats de couleur qui
      changent quand on tourne la tête), Marbre noir (veines blanches et veines d'or), Patine (cuivre, rivets,
      vert-de-gris, coulures), Néon (pistes rose et cyan qui respirent), Rubis (pavé de rubis, or noir sur le reste).
- [ ] Rubis comme le Diamant : facettes de près, rouge profond de loin, pas de grésillement en bougeant la vue, pas de
      faces plates ni de traînées (crosse de la XM177).
- [ ] Néon, Phénix et Abysses brillent la nuit (comme Lave ou Circuit).
- [ ] Phénix : or en fusion qui coule vers la crosse ; deux ailes de flammes qui battent lentement, braises qui montent.
      Abysses : verre noir avec méduses et filaments dans la masse ; filaments lumineux qui sortent du garde-main et du
      canon et ondulent vers l'arrière, plancton autour de l'arme.
- [ ] Vu par le porteur, ces effets ne sont qu'un soupçon (petites ailes, filaments discrets), comme le Séraphin : ils
      disparaissent en visée et juste après un tir, le centre de l'écran reste dégagé.
- [ ] Sur un sac (skin X posé depuis la collection) : Phénix = ailes de flammes en V derrière le dos, braises ; Abysses =
      filaments qui pendent du bas du sac comme des tentacules, plancton. **Pas de rendu validé pour les sacs** : à
      regarder en premier.
- [ ] Phénix et Abysses sont des objets à vie (gardés à la mort, « Jeter cet objet ? » avant de les sortir de
      l'inventaire), comme le Séraphin.
- [ ] Caisse Camo, aperçu : Patine 3,25 %, Opale et Néon 0,71 %, Marbre noir et Rubis 0,50 %, Phénix et Abysses 0,75 % ;
      les autres camos gardent leurs chances ; stickers et module StatTrak plus rares (13,3 % en tout au lieu de 20,5 %).
- [ ] Caisse Événement, aperçu : Opale et Néon 0,59 %, Marbre noir et Rubis 1,13 %, Phénix et Abysses 1 % ; les autres
      camos gardent leurs chances ; hérals 32,6 % en tout au lieu de 38 %.
- [ ] FPS pareils qu'avec le Diamant ou le Séraphin : camo en main, et plusieurs joueurs avec Phénix ou Abysses autour.

## 18. Six nouveaux camos, 2e lot (19/11, demandés par le propriétaire)

Forge (Rare), Or craquelé (Épique), Dichroïque (Épique), Vipère (Légendaire), Rouages (Légendaire, animé) et un skin X
(Unique, avec effet 3D) : Prisme. Tactique 2.59.0 : tout le monde doit avoir la nouvelle version (le launcher la donne).
Pour les essais (staff) : onglet HeraliZ · Tactique, groupe Camos, ou `/give @s heralisz_camo:camo_forge` (puis
`camo_or_craquele`, `camo_dichroique`, `camo_vipere`, `camo_rouages`, `skinx_prisme`). **Rien de tout cela n'a été testé
en jeu** (rendus hors jeu seulement).
- [ ] Les 6 objets : nom de la couleur de leur rareté (Forge bleu, Or craquelé et Dichroïque violet, Vipère et Rouages or,
      Prisme rouge), icône (losange pour les camos, cadre rouge pour le Prisme), textes en français, anglais et russe.
- [ ] En main et vu par un autre joueur, chaque camo ressemble aux planches du concept : Forge (acier martelé, auréoles de
      chauffe paille, pourpre et bleu), Or craquelé (or brisé puis recollé, joints noirs, reflets qui s'allument éclat par
      éclat), Dichroïque (verre vert d'eau qui vire au bleu et au violet de biais, cadre d'argent brossé), Vipère (écailles
      d'or en relief, grandes taches sombres), Rouages (roues de laiton qui s'engrènent et tournent, lueur vert d'eau).
- [ ] Dichroïque, Vipère et Rouages sont posés face par face (comme le Diamant) : coupés aux arêtes, c'est normal ; pas de
      grésillement en bougeant la vue. Rouages sur un pistolet : le motif n'est-il pas trop serré ?
- [ ] Rouages, Prisme et un peu le Dichroïque brillent la nuit.
- [ ] Prisme : cristal taillé violet, magenta, bleu et cyan (quelques éclats d'ambre), une onde de lumière qui court de la
      crosse vers le canon ; quinze éclats de cristal tournent autour de l'arme sur trois orbites, comme un atome, avec
      leurs traînées de couleur, des étincelles et une lueur au centre.
- [ ] Vu par le porteur, l'effet n'est qu'un soupçon (orbites plus petites), comme le Séraphin : il disparaît en visée et
      juste après un tir, le centre de l'écran reste dégagé.
- [ ] Sur un sac (skin X posé depuis la collection) : le même atome, en grand, dans le dos (trois orbites, éclats,
      traînées, étincelles). **Pas de rendu validé pour le sac** : à regarder en premier.
- [ ] Le Prisme est un objet à vie (gardé à la mort, « Jeter cet objet ? » avant de le sortir de l'inventaire).
- [ ] Caisse Camo, aperçu : stickers et module StatTrak à 10 % en tout (leur minimum, au lieu de 13,3 %) ; pour garder
      ce minimum, tous les camos baissent un peu ensemble (environ 3 %) : communs 4,67 %, peu communs 3,22 %, rares (dont
      Forge) 3,14 %, épiques (dont Or craquelé et Dichroïque) 0,68 %, légendaires (dont Vipère et Rouages) 0,48 %, skins X
      (dont Prisme) 0,72 %.
- [ ] Caisse Événement, aperçu : Or craquelé et Dichroïque 0,59 %, Vipère et Rouages 1,13 %, Prisme 1 % ; les autres
      camos gardent leurs chances ; hérals 28,2 % en tout au lieu de 32,6 % (jamais moins de 25 %).
- [ ] FPS pareils qu'avec le Diamant ou le Séraphin : camo en main, et plusieurs joueurs avec le Prisme autour.

## 19. Nouvelles cartes du ClashWar (19/11)

Une carte par format, tirée à chaque match : 1v1 « Dépôt des locomotives » (90 × 90), 2v2 « Cité du Kombinat »
(130 × 130, la carte d'essai validée par le propriétaire le 19/11), 3v3 « Port à sec » (170 × 170), 4v4 « Garnison »
(220 × 220). Les anciennes (Check-point, Dépôt de fret, Carrière abandonnée, Berezka) et les six grandes cartes de
recherche et destruction sont retirées. ClashWar 1.3.0 : tout le monde doit avoir la nouvelle version (le launcher la
donne). Outils (fabrication des cartes, contrôles, images) : `modules/clashwar/outils/cartes_clashwar/`. **Rien n'a été
testé en jeu** : les cartes ont été contrôlées par calcul (blocs qui existent, rien qui flotte, lignes de vue, abris,
endroits atteignables) et en images seulement.
- [ ] Chaque format joue bien sa nouvelle carte ; les textures sont celles du pack imposé, aucun bloc violet et noir,
      aucun trou.
- [ ] Entrée dans l'arène : l'annonce dit « [A] contre [B] · Garnison » (nom de la carte) ; écran du choix de classe :
      « Quarts de finale · Garnison · manche 1 sur 5 » ; en français, anglais et russe.
- [ ] Départ : chaque équipe dans son abri (vestiaire, hangar, douane, hangar des véhicules), retenue par la barrière
      pendant le choix et la préparation, puis libre ; les deux équipes ne se voient pas au départ.
- [ ] Les deux moitiés de chaque carte sont les mêmes (tournées d'un demi-tour) avec un autre aspect : changer de côté
      aux manches 3 et 5 ne donne aucun avantage. Trois chemins vers le centre sur chaque carte.
- [ ] On ne sort pas de la carte : enceinte de béton tout autour (plus haute là où l'on grimpe près du bord, par
      exemple les buttes du dépôt de munitions de la Garnison), murs invisibles derrière. Essayer de sauter par-dessus
      depuis les endroits hauts près du bord.
- [ ] Les hauteurs s'atteignent à pied : passerelle et château d'eau (dépôt) ; étages 1 et 2 des barres (Kombinat) ;
      grue, cargo échoué, mezzanine de l'entrepôt (port) ; étages des casernes, tours de guet, buttes, galerie et balcon
      du club, clocher de l'église (Garnison). Les toits fermés (barres, hangars, château d'eau…) ne s'atteignent pas.
- [ ] Mort subite : la zone se resserre jusqu'au centre (plaque tournante, patinoire, pont central, monument aux
      morts) ; depuis un coin on a le temps d'y arriver.
- [ ] Premier démarrage après la mise à jour : les 4 cartes et la salle se posent d'avance en tâche de fond (moins
      d'une minute ; les six grandes cartes ne se posent plus), sans gel du serveur (pas de « Can't keep up »).
- [ ] Pendant les inscriptions, les arènes du premier tour se posent d'avance, une par match possible (environ 15 s
      chacune pour la Garnison) ; journal : « [matchs] arenes : … posee(s) d'avance ». Au lancement du tour, aucun match
      « arène indisponible ». À essayer avec beaucoup d'équipes en 4v4 (16 matchs : environ 4 minutes de pose pendant les
      10 minutes d'inscriptions).
- [ ] FPS et TPS sur la Garnison à 8 joueurs : vue lointaine depuis le clocher et les étages, arbres, décors.

## 20. L'eau ne gèle plus, la neige ne s'entasse plus (19/11)

Minecraft gelait l'eau des zones froides (arctique) petit à petit, et posait de la neige au sol quand il neigeait. C'est
coupé par le noyau (`[monde] gel_eau` et `neige_au_sol` de `heraliz_noyau-server.toml`, à `false`).
- [ ] Après la mise à jour, remettre la glace déjà formée en eau : WorldEdit, sélection autour des plans d'eau gelés de
      l'arctique, `//replace ice water` (`//undo` si une glace posée exprès est touchée).
- [ ] Un moment plus tard (rester près de l'eau quelques minutes) : l'eau de l'arctique reste de l'eau.
- [ ] Quand il neige : la neige tombe à l'écran mais aucune nouvelle couche n'apparaît au sol.
- [ ] `/diag etat`, ligne « Minecraft allégé, étape 2 » : « contrôles de gel sautés » et « de neige au sol » montent
      (preuve que le blocage est en place).

## 21. Recycleurs sur la carte M (19/11)

Demande du propriétaire : un petit point sur la carte pour chaque recycleur de terrain, posé tout seul. Monde 1.3.1 et
Quêtes 1.22.4 : tout le monde doit avoir les nouvelles versions (le launcher les donne). Le pictogramme (deux flèches
vertes en cercle, sur la petite pastille ronde des lieux) est nouveau et **pas encore validé par le propriétaire** :
aperçu dans `maquettes/carte_recycleurs/repere_recycleur.jpg`.
Les positions sont gardées dans `dayz_map/data/heraliz_carte_recycleurs.dat` (mis de côté avec l'ancienne carte si on
change de carte : la recherche repart alors toute seule). **Rien de tout cela n'a été testé en jeu.**
- [ ] Premier démarrage après la mise à jour : une minute après, la recherche des recycleurs déjà posés part toute
      seule. Journal du serveur : « [carte] recherche des recycleurs : … région(s) … », l'avancement toutes les 30 s,
      chaque recycleur trouvé, puis « [carte] recherche des recycleurs finie en … : N recycleur(s) sur la carte ». Compter
      5 à 12 minutes pour la carte. Pendant ce temps : pas de « Can't keep up », TPS normal, les joueurs qui courent ou
      roulent voient le terrain arriver comme d'habitude.
- [ ] Carte (M) : chaque recycleur de terrain a sa pastille, visible même en vue d'ensemble ; de près, « Recycleur »
      écrit dessous ; au survol : « Recycleur », « machine partagée : recycle tes objets en composants » et la distance.
      Le calque « Points d'intérêt » les cache et les montre avec les autres lieux, son compteur les compte. En anglais et
      en russe aussi.
- [ ] Un recycleur posé dans un autre monde n'apparaît que sur la carte de ce monde.
- [ ] Poser un nouveau recycleur de terrain (créatif) : sa pastille apparaît tout de suite chez tous les joueurs, même
      carte ouverte. Le casser : elle part tout de suite.
- [ ] `/carte recycleurs` (staff) : la liste (coordonnées, monde), puis « Recherche … faite le … ». Cliquer une ligne met
      la téléportation dans le tchat (Entrée pour y aller).
- [ ] `/carte recycleurs chercher` : « Recherche des recycleurs lancée … » ; `/carte recycleurs` pendant ce temps :
      « Recherche en cours : x % … » ; à la fin, dans le tchat de celui qui l'a lancée : « Recherche des recycleurs finie
      (… min … s) : N recycleur(s) sur la carte (… ajouté(s), … retiré(s)) ».
- [ ] `/carte lieu ajouter recycleur Essai` est refusé (« Les repères « Recycleur » se posent tout seuls… ») et
      « recycleur » n'est pas proposé dans les types ; `/carte lieu retirer` ne propose aucun recycleur ; `/carte lieu
      liste` finit par « Et N recycleur(s) de la carte, repérés tout seuls ».
- [ ] Recycleur enlevé avec WorldEdit (`//set air` dessus, son voisin invisible compris) : la pastille part tout de
      suite ; si elle reste, elle part au prochain chargement de son tronçon (y revenir plus tard), journal « recycleur de
      la carte disparu (bloc relu au chargement du tronçon) », ou avec `/carte recycleurs chercher`.
- [ ] Redémarrage suivant : la recherche ne repart pas (journal : « N recycleur(s) de la carte sur la carte du monde
      (recherche automatique déjà faite) »).

## 22. Fouille des caisses : seulement le palier (19/11)

- [ ] Ouvrir une caisse de loot (T1 à T6, salle à carte) : en haut à gauche, plus de nom, seulement le badge « T3 »… de
      la couleur du palier.
- [ ] Coffre T7 : seulement le badge « T7 » ; à droite, le compteur « à identifier » ne change pas.
- [ ] Caisse de largage, planque, coffre VIP, coffre du clan : leur nom reste (ils n'ont pas de palier).

## 23. Fragments de cartes et capsules du casino (19/11)

Fragments : la couleur suit enfin leur vraie rareté (un fragment vert sort moins souvent qu'un objet Légendaire en T5).
Vert : Épique (violet) ; bleu : Légendaire (or) ; rouge : Unique (rouge) ; les cartes entières aussi. Et toutes les
chances x 1,18 (« 1 sur 1 000 -> 1 sur 850 ») : conteneurs et zombies ; le rouge garde le bonus du réglage bêta.
- [ ] Infobulle et nom d'un fragment vert : violet ; bleu : or ; rouge : rouge ; pareil pour les cartes entières.
- [ ] Coffre T7 : un fragment rouge s'identifie avec l'animation des Uniques.

Capsules du casino : beaucoup plus de capsules dans les conteneurs, surtout en piles plus grosses (la 10, nom en bleu, en
piles de 3 à 10 dès le T3). En moyenne par conteneur, avant le réglage bêta : T1 0,3 (avant 0,07), T3 1,6 (0,2), T5 5,5
(0,45), T6 10,7 (0,9), T7 21 (1,3). Les autres objets perdent 1 à 2,5 % de leur place.
- [ ] Ouvrir une dizaine de conteneurs T4-T5 : des capsules dans 1 sur 8 environ, parfois une pile de 4 à 7 capsules de 10.

## 24. Duplications : correctifs de l'audit du 19/11

Noyau 1.1.3, Quêtes 1.22.5, Boutique 1.5.16, Monde 1.3.2, CRac 1.38.5-beta, Tactique 2.59.1, ClashWar 1.3.1. Rien à
voir de nouveau pour les joueurs. **Rien n'a été essayé en jeu.** Il faut le serveur d'essai, deux comptes et un op pour
lancer un ClashWar. Nouvelle règle commune : rien de ce qui attend (objets gardés, récompenses, clés) n'arrive pendant un
match, dans le monde des matchs, ni tant que l'inventaire est mis de côté par le ClashWar ; tout arrive après le retour.

Recycleur du spawn (le plus grave : objets doublés à volonté pendant un tournoi) :
- [ ] Poser des objets dans le recycleur, remplir son inventaire, fermer : le reste est « gardé ». Pendant un tournoi :
      `/clashwar regarder`, se déconnecter, se reconnecter, `/clashwar quitter`, rouvrir tout de suite le recycleur
      (moins de 5 s), attendre 10 s, le fermer : chaque objet une seule fois (avant : tout en double).
- [ ] Même chose sans rouvrir le recycleur : les objets gardés reviennent seuls dans les 5 s après le retour.
- [ ] Hors ClashWar : reconnexion et réapparition avec des objets gardés : rendus comme avant.

Objets en attente pendant un ClashWar :
- [ ] Atelier de réparation : arme posée, inventaire plein, fermer (« t'attend »). Partir au ClashWar : l'arme n'arrive
      pas dans l'inventaire du match ; elle revient dans les 5 s après le retour. En match, un kit de réparation n'ouvre
      pas l'atelier.
- [ ] Objets du tutoriel qui attendaient une case, objets à vie gardés (inventaire plein), objets à vie d'une base
      effacée : rien pendant le match ni à une reconnexion pendant le tournoi ; tout arrive après le retour.

Tutoriel des bases :
- [ ] Étape de l'entretien : mettre les planches du kit dans l'armoire, puis les reprendre (clic, Maj + clic, touches 1
      à 9) ; « Quitter le tuto » : ces planches sont reprises avec le reste du kit (rien en plus dans l'inventaire rendu).
- [ ] Étape de la caisse : poser la caisse en métal, la ramasser, finir ou quitter : la caisse est reprise aussi.
- [ ] L'armoire d'une vraie base sur la carte : rien ne change (les planches reprises s'empilent avec les autres).

Recycleur de la carte :
- [ ] Machine restée vide plus de 20 min : y déposer 10 objets (5 s chacun), `/tp` à 1 000 blocs tout de suite (le
      coin se décharge), attendre 10 s, revenir : environ 20 s après le dépôt, 4 à 6 objets recyclés, pas les 10 d'un
      coup (avant : la machine rattrapait aussi le temps passé vide).

Plantages au mauvais moment (non provoqués : vérifier seulement que tout marche comme avant) :
- [ ] Recycler une pile au spawn : les hérals arrivent comme avant. `/diag profil` pendant que deux joueurs recyclent :
      l'enregistrement des joueurs (`PlayerList.save`) reste petit.
- [ ] Achat chez un marchand ; quête de Varga avec une étape « donner » (donner en deux fois) et une étape « payer » ;
      clés du ClashWar en fin de tournoi : comme avant, une seule fois.
- Pas encore corrigés (il faudrait changer la façon dont ces fichiers s'écrivent) : planque et coffre VIP, coffre du
  clan. Il faudrait un plantage du serveur dans la seconde qui suit la fermeture du coffre.

## 25. Acheter avec la quantité, et le Passeur : armes et munitions (19/11)

Demandes du propriétaire : un panneau de confirmation avant chaque achat, avec la quantité (« 500 brindilles, c'est 500
clics ») ; et un PNJ qui vend des armes et des munitions, cher. Quêtes 1.23.0 : tout le monde doit avoir la nouvelle
version (le launcher la donne ; un jeu plus ancien est refusé à la connexion). Maquette du panneau, **pas encore validée
par le propriétaire** : `maquettes/achat_quantite/`. Prix et calculs : en tête de `outils/marchands.py`.
**Rien de tout cela n'a été testé en jeu.**

À faire une fois : au spawn, se placer là où le Passeur doit se tenir, regarder dans la direction voulue, puis
`/pnj creer passeur` (`/pnj deplacer passeur` pour le bouger). `/pnj liste` doit le montrer.

Le panneau d'achat (chez tous les vendeurs : le Bâtisseur, Jacqueline, le Doyen, Jack, le Passeur) :
- [ ] Clic sur « Acheter » : le panneau s'ouvre au milieu de l'écran (l'objet, son nom à la couleur de sa rareté, le prix à
      l'unité), quantité 1. Rien n'est acheté tant qu'on n'a pas confirmé.
- [ ] Taper 500 au clavier (le premier chiffre remplace le 1) : le total, le solde et « Après l'achat » suivent. Chez le
      Bâtisseur, 500 brindilles = 1 000 hérals (2 l'unité pendant la bêta).
- [ ] − et + (avec Maj : de 10 en 10), la molette, les flèches haut et bas ; pastilles ×1, ×10, une pile (×64 pour les
      brindilles, ×60 pour les balles de 9 mm, ×36 en calibre 12) ou ×5 (une arme), et Max.
- [ ] « Max » = ce que tes hérals paient, ou ce qui tient dans ton inventaire : la ligne sous la quantité dit lequel des
      deux. Une quantité plus grande : ligne rouge, total en rouge, « Confirmer » éteint.
- [ ] Inventaire presque plein : Max = ce qui tient vraiment ; acheter Max : tout rentre, rien au sol.
- [ ] Échap ou « Annuler » : le panneau se ferme, rien n'est acheté. Entrée ou « Confirmer » : l'achat se fait. Double
      clic sur « Acheter » : le panneau s'ouvre mais rien n'est acheté (« Confirmer » reste éteint une demi-seconde).
- [ ] Après l'achat : le bandeau « Acheté : 500 × Brindilles (1 000 hérals) » ; le solde de l'écran baisse d'autant.
- [ ] Un op change le prix de l'article au Registre pendant qu'un joueur a le panneau ouvert, puis le joueur confirme :
      « Le prix de … vient de changer : rien n'a été acheté », aucun hérals retiré ; l'écran montre le nouveau prix et le
      panneau, rouvert, aussi.
- [ ] En anglais et en russe : aucun texte ne dépasse du panneau.

Le Passeur :
- [ ] Lui parler : « Le Passeur », « Armes et munitions », ses phrases en français, anglais et russe ; son apparence
      (marinière bleu marine, collier de barbe, bottes de pluie).
- [ ] « Voir ce que tu vends » : 24 armes (les 20 grises et vertes, puis 4 bleues : Mk 23, UMP-45, M870, SKS tactique) et
      8 munitions (.45 ACP, 9 mm, calibre 12, .50 AE, 5,56, 7,62 × 39, .308, .338), chaque nom à la couleur de sa rareté.
- [ ] Prix pendant la bêta (prix des fichiers x 0,75) : pistolet gris 1 125, pistolet vert 2 250, pistolet-mitrailleur ou
      fusil à pompe vert 3 000, Mk 23 5 250, UMP-45 et M870 6 750, SKS 7 500 ; balles (19/11, « moins on doit tirer de
      balles, plus c'est cher ») : 9 mm et .45 ACP 6 ; .50 AE, 5,56 et 7,62 × 39 8 ; calibre 12 30 ; .308 (précision et
      fusils semi-automatiques) et .338 (AWP, M200) 45.
- [ ] Une arme achetée est neuve (100 %) et vide : la charger avec les balles achetées.
- [ ] Revendre à Mila une arme achetée chez lui : environ 15 (grise), 30 (verte), 60 (bleue) ; la recycler : quelques
      composants ou quelques hérals. Jamais plus d'un dixième de son prix.
- [ ] Une arme grise ou verte trouvée sur la carte, revendue à Mila : payée comme avant, mais plus de points de saison ni
      d'avancée de la quête du jour « Vendre » (un marchand la vend maintenant : règle du 25/10, acheter puis revendre ne
      rapporte rien).
- [ ] Sur la carte, en combat, à terre ou en match : il refuse, comme les autres marchands.

## 26. Sacs : plus aucun défi ni apparence à débloquer (19/11)

Choix du propriétaire : les sacs se trouvent seulement sur la carte, avec leur apparence ; rien à débloquer.
`serveur/config/backpacked.server.toml` : `unlockAllCosmetics = true` (aucun défi suivi, aucun déblocage annoncé) et
`disableCustomisation = true` (plus de bouton de personnalisation).
- [ ] Ouvrir son sac : plus de bouton « Personnaliser » ; aucun message « sac débloqué » en jouant.
- [ ] Un sac trouvé garde l'apparence avec laquelle il a été trouvé (tactique, randonnée, sport…), même sur le dos.
- [ ] Les camos sur les sacs (table d'armurier) marchent comme avant.

## 27. Corrections de l'audit du 19/11 (bugs de jeu)

Noyau 1.1.4, Monde 1.3.3, Quêtes 1.23.1, CRac 1.38.6-beta, Tactique 2.59.3, Sacs 3.0.1-beta.4-heralisz27.
**Rien de tout cela n'a été testé en jeu.**

À faire une fois en jeu (staff) :
- [ ] **Plus de `/tpa` dans les deux bunkers** (on en sortait sans carte et on y faisait entrer son clan). Les zones sans
      téléportation ne sont pas dans le dépôt : elles se règlent en jeu. Taper, de n'importe où :
      - `/execute in minecraft:overworld positioned -254 64 147 run tpa zone ajouter 96 Grand Bois`
      - `/execute in minecraft:overworld positioned 853 64 -998 run tpa zone ajouter 93 Pic`
      - puis `/tpa zone liste` : les deux zones sont dans la liste.
      Centre et rayon : ceux des deux bunkers dans `serveur/config/crac/zones_construction.json` (tout le bunker, plus
      40 blocs autour). Le joueur qui tente un `/tpa` dedans lit le nom de la zone : un nom neutre, pour ne pas montrer
      où sont les bunkers (ils ne sont plus sur la carte M). Pour le nom « Bunker des Bois » à la place, c'est la même
      commande avec ce nom.
- [ ] **Quête « La boîte noire »** : elle ne se finit que dans les caisses d'une zone de radiation. Au démarrage, le
      journal du serveur ne doit avoir aucune ligne « QUÊTE IMPOSSIBLE » ; `/zones info` doit montrer « Zone irradiée de
      Radio (radiation) ». Sinon : remettre cette zone dans `dayz_map/data/heraliz_zones.json` (la carte en île l'apporte
      dans son propre fichier du même nom), puis `/recharger zones`.

À tester :
- [ ] **Extraction sous le feu** : un autre joueur tire sur celui qui s'extrait (ou le staff tape
      `/combat marquer <joueur>` pendant le compte à rebours). À l'arrivée au spawn : plus d'« EN COMBAT »,
      `/combat etat <joueur>` dit « libre », `/hdv` marche tout de suite. Quitter le jeu aussitôt et revenir : vivant, au
      spawn, rien de perdu. Celui qui a tiré reste en combat, lui.
- [ ] **Spawn sans mort** : au spawn, `/combat marquer <joueur>` (staff), puis le joueur quitte le jeu : à son retour,
      vivant, rien de perdu, pas de message « déconnecté en plein combat ». Journal du serveur : « sans sanction (monde
      du spawn) ».
- [ ] **`/spawn` depuis sa base** : pendant le compte à rebours, tirer sur un autre joueur (ou `/combat marquer
      <joueur>`) : pas de départ, message « Tu es en combat : /spawn dans … s. ». Hors combat : départ comme avant.
- [ ] Quêtes : trois textes s'affichaient en clé brute ; ils sont écrits en français, anglais et russe (quête finie
      mais hérals en attente ; vente refusée quand les hérals ne peuvent pas être versés ; pour le staff, dans
      l'édition d'un marchand). Le dernier se voit facilement : ajouter un rachat plus cher que la moitié du prix de
      vente d'un objet → « … se vend … hérals : il ne sera jamais racheté plus de … hérals (la moitié). »
- [ ] Journal du démarrage : plus de « Couldn't load tag curios:ear_protection ».
- [ ] Rien ne doit changer pour : les masques à gaz et tenues Hazmat dans les zones de gaz (motifs qui ne servaient à
      rien retirés), le C4 de CRac sur les bases (`c4ThrowableIds` vidé : il visait un C4 qui n'existe plus), le verre
      du spawn et des arènes (monde des anciens bunkers de clan retiré de la liste).

Sans essai possible : après une erreur, la détection « à terre » des quêtes (marchands, recycleurs, étapes
« exécuter ») réessaie toute seule (1 s, 2 s, 4 s… jusqu'à 5 min) au lieu de rester coupée jusqu'au redémarrage.

## 28. Réglages du serveur, plugins et mise en ligne (audit du 19/11)

Rien de tout cela n'a été essayé en jeu ni sur le Ptero. Vérifié le 19/11 sur les machines de GitHub : fabrication et
contrôles de la branche (0 problème) ; démarrage d'essai réussi avec ces réglages (« Done » en 94 s, aucune ligne
fatale, arrêt propre), cloth-config retiré du serveur compris. Tout compte au **redémarrage** qui suit la mise en
ligne. Détail et raisons : `docs/DEPLOIEMENT.md`, sections 6 à 9.

Mise en ligne :
- [ ] Résumé de « Serveur HeraliZ » : partie « Démarrage d'essai » à « Réussi », puis le job « serveur » a tourné (envoi
      au Ptero). Si « Refusé » : rien n'est parti, la raison et les lignes en cause sont dans ce résumé.
- [ ] Sur le Ptero (gestionnaire de fichiers du panel) : `bukkit.yml` dit `autosave: 12000` et `warn-on-overload: true` ;
      `spigot.yml` dit `user-cache-size: 1000` et `ignore-spectators: true` ; `server.properties` n'a pas changé.
- [ ] `plugins/AuthMe/config.yml` du Ptero : `UnrestrictedName: []` (plus aucun pseudo `hzbot` dedans).

Jeu (après le redémarrage) :
- [ ] Sauvegarde automatique toutes les 10 min : dans `logs/debug.log`, les lignes « Autosave » sont à 10 min d'écart
      (5 avant).
- [ ] WorldEdit : `//size` donne le nombre de blocs ; un `//set` de plus de 250 000 blocs s'arrête (« Max blocks changed
      in an operation reached ») et `//undo` le défait ; `//limit 2000000` permet plus ; `//sphere` de rayon 65 refusé
      (64 au plus) ; au 7e `//undo` de suite, « Nothing left to undo » (6 retours au plus).
- [ ] Connexion avec le plus gros inventaire possible : sac plein d'armes équipées (accessoires), Curios pleins ; ouvrir
      un coffre de clan, le coffre VIP, un recycleur plein et un coffre du ClashWar : aucune expulsion (« Badly
      compressed packet », « Payload may not be larger », « Packet too big »).
- [ ] Après `rate-limit=1000` mis à la main dans `server.properties` (propriétaire) : 10 min de jeu normal (tir en
      automatique, voix, inventaire en cliquant vite) sans expulsion « paquets ».
- [ ] Zombies : un joueur blessé n'attire plus les zombies par l'odeur du sang, mais les tirs et les blocs cassés les
      attirent toujours ; pendant les combats, moins d'entités `zombieawareness:scent` dans les rapports `/diag` (celles
      des sons restent : pour les réduire aussi, l'audit propose `blockBreakEvent_PlayersOnly = true` et
      `blockHittingEvent_Active = false` dans `General.toml`, choix de jeu à décider).
- [ ] Véhicules : un conducteur voit toujours les véhicules lointains (mis à jour une fois par seconde au lieu de quatre).
- [ ] Menu des réglages de TaCZ (touche du jeu) toujours là chez les joueurs (cloth-config retiré du serveur seulement).
- [ ] Changer le grade d'un joueur connecté (LuckPerms) : ses nouvelles commandes arrivent dans la complétion du tchat au
      prochain changement de monde ou à la reconnexion (plus tout de suite : voulu).
- [ ] Démarrage du serveur : plus de « Downloading GeoIP » de LiteBans dans le journal. Un gros `//set` du staff fait de
      nouveau apparaître « Can't keep up » dans la console.

À faire par le propriétaire ou l'hébergeur :
- [ ] `server.properties` du Ptero : `rate-limit=1000` ; décider `enable-query` (false sauf site de classement) et
      `player-idle-timeout`.
- [ ] Hébergeur : ligne Java de `docs/DEPLOIEMENT.md` §8 (`-Xms16G -Xmx16G` + réglages Aikar + `gc.log`), conteneur de
      20 Go au moins ; puis `/diag` : 16 384 Mo et `logs/gc.log` présent.
- [ ] Hébergeur, pour viser 150 joueurs : processeur récent à haute fréquence, cœurs réservés, disque NVMe.
- [ ] Mise à jour de Mohist (build après le 20/07/2025) : seulement après un banc de charge et un essai.
- [ ] WorldGuard : `/rg list` dans chaque monde ; si aucune région n'utilise de drapeau de mouvement (entry, exit,
      greeting, farewell, ceux de WorldGuardExtraFlags), demander à passer `use-player-move-event` à `false`.

## 29. Économie : récolte, recycleur, cœur de clan, caisses (19/11)

Récolte 1.1.2 et Quêtes 1.23.2. Les réglages sont relus sans redémarrer (`recolte.json` tout seul, `/recharger` pour le
reste), mais la limite de 2 000 ressources par sorte demande la nouvelle Récolte (avec l'ancienne, 500 arbres au plus).
**Rien de tout cela n'a été testé en jeu.**

Récolte (choix du propriétaire : 2 fois plus d'arbres et de gisements, chacun donne environ 40 % de moins) :
- [ ] `/recharger recolte` : « 720 arbres, 540 gisements de pierre, 240 de métal et 84 de titane à la fois » (avant :
      360, 270, 120, 42). Les nouvelles places se remplissent petit à petit là où des joueurs passent ; pas de lag au
      moment où le fichier est relu.
- [ ] Un arbre abattu à la hache : 6 bûches en tout (5 pendant les coups, 1 quand il tombe), toujours 12 coups.
- [ ] Gisements : pierre 6 à 9 pierres brutes, métal 4 à 8 minerais, titane 2 à 5 ; toujours 16, 20 et 24 coups. Avec
      la hache-pioche de survie : moins de coups, et plus souvent le haut de la fourchette.
- [ ] L'usure de la hache et de la pioche par coup ne change pas (elles s'usent donc un peu plus par bûche ou minerai).
- [ ] Île arctique (zone de neige) : bien plus d'arbres et de gisements qu'ailleurs, plus serrés qu'avant (deux arbres
      peuvent être à une vingtaine de blocs, deux gisements de métal à une quarantaine).
- [ ] Le Contremaître ne dit plus que « le métal est compté chaque jour » (il n'y a pas de plafond par jour).

Recycleur (armes et accessoires : en moyenne un peu moins que Mila, avec de la chance nettement plus) :
- [ ] Aperçu d'une arme Commune (pistolet gris) : Canon II, Ressort II, Ferraille II, Ferraille, Kit de pièces ; d'une
      Légendaire : Canon IV, Ressort IV, Ferraille IV, Kit de pièces IV. Jamais plus de 5 lignes.
- [ ] Un viseur rend des batteries, une poignée du caoutchouc, un chargeur un ressort ; plus jamais de scotch ni de
      circuit imprimé sur une arme ou un accessoire (ce sont des pièces des explosifs).
- [ ] Recycler une dizaine d'armes Rares neuves : environ un tiers ne rend que 15 hérals, un tiers une pièce (20 ou 50
      chez Mila), un tiers deux pièces (jusqu'à 100) ; Mila en donne 60 chacune.
- [ ] Une conserve geckolib (haricots, pêches…) rend une ferraille ; une boisson ou des chips du plastique ; une batte ou
      une machette 2 ferrailles, comme avant.
- [ ] Un sac Légendaire : son aperçu montre Tissu IV, Scotch IV, Caoutchouc IV et Tissu (avant deux Tissu IV) ; en
      moyenne un peu moins que Mila (120 hérals), avec de la chance bien plus. Les autres sacs ne changent pas.
- [ ] Un article acheté chez Jack ou le Passeur se recycle comme avant (aucun refus nouveau) ; le saucisson du Doyen
      reste refusé (« rapporterait trop »).

Cœur de clan, caisses, Mila :
- [ ] Cœur de clan chez le Bâtisseur : nom et badge Légendaire (or) ; prix inchangé (3 750 hérals pendant la bêta).
- [ ] Caisse Gemme : un couteau 1 étoile annoncé « Peu commun » arrive Peu commun (vert) dans l'inventaire ; 2 étoiles
      Épique, 3 étoiles Légendaire, 4 étoiles Unique. Pareil dans la caisse Événement. Un couteau trouvé dans un coffre
      T7 reste Unique.
- [ ] Caisse des quêtes : fragment vert annoncé Épique, bleu Légendaire (annoncé à tout le serveur), kit de fortune Rare,
      kit standard Épique. Caisse de vote : le cœur de clan est annoncé Légendaire (annoncé à tout le serveur).
- [ ] Mila rachète maintenant les combinaisons intégrales trouvées (plongée, arctique, hazmat : 25 hérals ; renforcées :
      60) ; avant, elle les refusait.

## 30. Lenteurs du serveur : téléportations, sauvegardes, démarrage, diagnostic (19/11)

Audit des lenteurs du 19/11 (points C1, C2, C4, C5, C7, C9, C15, F2, F5, F7). Presque tout est dans le noyau ; chaque
changement a son réglage dans `heraliz_noyau-server.toml` (à `true` = nouveau, à `false` = comme avant) et son compteur
dans `/diag etat` (ligne « Lenteurs (19/11) », puis « Écritures en fond » et « Préchauffage du démarrage »). **Rien n'a été
testé en jeu** (pas de serveur ici) : tout est à vérifier ci-dessous. Après la mise à jour, garder un œil sur le journal
les premiers jours : une ligne « [noyau] … impossible » ou une erreur de mixin au démarrage est à signaler.
Versions : Noyau 1.1.5, Anticheat 2.12.1, Tactique 2.59.5, CRac 1.38.7-beta, Boutique 1.5.17, Bunker 0.14.6, Clans 3.17.3,
ClashWar 1.3.2, Événements 1.3.1, Loot 0.20.4, Monde 1.3.4, Quêtes 1.23.3 (le launcher donne les nouvelles aux joueurs).

Téléportations (plus de gel de 0,4 à 2,5 s quand l'arrivée n'est pas chargée)
- [ ] `/tp` (staff) vers un endroit lointain de la carte, jamais visité depuis le démarrage : le message de Minecraft
      arrive tout de suite, un bandeau « Téléportation — l'arrivée se charge… » aussi, et le départ se fait une fraction de
      seconde plus tard (3 s au plus). Pas de « [Diag] Blocage » juste après. Même chose avec `/tp <joueur> x y z` et un
      `/tp` vers un joueur d'un autre monde.
- [ ] `/tp` tout près (même zone) : départ tout de suite, sans bandeau, comme avant.
- [ ] `/warp` (au spawn) vers un warp lointain : toujours 3 s sans bouger, puis départ ; `/tpa` accepté : départ comme
      avant ; panneau du staff « Aller voir » / « Faire venir » ; `/zonevip tp`, `/carriere tp`, `/quete point tp`,
      `/corps tp`, `/extraction tp` : départ normal, sans gel.
- [ ] `/diag etat`, ligne « Lenteurs (19/11) » : « arrivées … attendues » et « /tp retardés » montent après ces essais ;
      « délais de 3 s dépassés » reste à 0 ou presque (sinon le dire : le disque est lent).
- [ ] Vol rapide en créatif ou en spectateur vers des zones jamais chargées : on peut être « retenu » une fraction de
      seconde au bord d'une zone qui se charge (comme un petit élastique), mais le serveur ne gèle plus. Compteur
      « mouvements refusés ». À pied ou en véhicule, en survie : jamais d'élastique.

Sauvegarde automatique (toutes les 5 min)
- [ ] Avec quelques joueurs, attendre une sauvegarde automatique : pas de « [Diag] Blocage » à ce moment-là. Compteurs :
      « partie fml resservie », « réglages de génération », « fichiers data/ écrits en fond », « sauvegarde automatique
      étalée : … tours, … joueurs par tranches » montent.
- [ ] Après une sauvegarde : se déconnecter, se reconnecter : inventaire, sac, armes et position intacts. Ouvrir le coffre
      du clan pendant une sauvegarde, y prendre un objet : un seul exemplaire (coffre ou inventaire), même après un
      redémarrage.
- [ ] Clans, banque, saison, quêtes, butin des zombies : rien ne change pour les joueurs (leurs écritures tombent
      seulement à d'autres moments que la sauvegarde de Minecraft). Après un `/stop` propre : clans, comptes, quêtes,
      territoires, corps, recycleurs, casino, coffres personnels et bases (CRac) sont tous là au redémarrage.
- [ ] `/diag etat`, ligne « Écritures en fond » : des fichiers écrits, 0 échec ; les voies « loot », « clans », « quetes »,
      « evenements », « casino », « crac », « coffres », « donnees », « level-dat » apparaissent au fil du temps.

Grands déchargements et tronçons « pas prêts »
- [ ] Après un gros `//replace` ou `//set` (serveur fermé, si possible), partir loin : plus de gel de 1 à 2 s une seconde
      après. Compteur « grands déchargements bornés ».
- [ ] Territoires des clans (drapeaux), cœurs de clan (scores, prise), salles à carte (portes), coffres T7 automatiques,
      conteneurs des événements, étoiles du loot, pings, vitres du labo : tout marche comme avant. Compteur « attentes de
      tronçon évitées ».
- [ ] Anticheat (Spartan) : courir, sauter, tomber près du bord d'une zone qui se charge (arrivée de `/tp`, `/rtp`) : pas
      de fausse alerte de mouvement, pas de gel. Les renvois de l'anticheat (sol sous les pieds) marchent toujours.

Démarrage et premières fois
- [ ] Pendant les 3 minutes qui suivent un redémarrage : pas de message « [Diag] Blocage » aux opérateurs ; les longs
      ticks sont dans `diag/demarrage-<date>.txt` et le journal dit « [diag] démarrage : tick de … ms ».
- [ ] Journal du démarrage : « [noyau] préchauffage : N classes des mods lues à l'avance … » (une vingtaine de secondes
      après « Done »), puis « … classes de M plugins lues à l'avance … ». Ensuite : première connexion et premier
      WorldEdit sans gel d'une seconde.
- [ ] `/diag etat` : ligne « Préchauffage du démarrage » à « fait » pour les deux.

Diagnostic
- [ ] `/diag etat` : deux lignes « Java : … » et « Arguments de Java : … » (la vraie ligne de lancement de Java, à
      comparer aux réglages demandés à l'hébergeur ; un argument qui ressemble à un mot de passe est masqué).
- [ ] Un rapport `diag/blocage-*.txt` commence par la ligne « Relevés de la pile dès 50 ms du tick » et en a une
      douzaine pour un tick de 120 ms ; les rapports d'un blocage de 300 ms et plus sont toujours écrits ; le dossier
      `diag/` garde jusqu'à 60 rapports de blocage (les plus longs restent) et 20 de chaque autre sorte.
- [ ] Ligne « points d'intérêt relus » : à relever après une soirée avec des joueurs (temps passé à relire les lits,
      postes de villageois… à chaque tronçon lu) : décidera si on le coupe sur la carte.

Réglages (section `[perf]`, à `false` pour revenir comme avant) : `arrivees_prechargees`, `tp_precharge`,
`mouvements_troncons_prets`, `dechargements_forces_par_tick` (50 ; 0 = comme Minecraft), `registres_en_memoire`,
`donnees_en_fond`, `joueurs_etales`, `entites_etalees`, `classes_prechargees`, `plugins_prechauffes` (liste vide = rien) ;
section `[diag]` : `releves_des_ms` (50), `calme_demarrage_s` (180 ; 0 = alertes dès le démarrage).

Pas fait (pour plus tard) : véhicules rapides qui entrent dans une zone pas chargée (seuls les joueurs à pied ou en vol
sont retenus) ; écriture des fichiers des joueurs en fond (seulement étalée sur 5 s, pour ne jamais lire un fichier pas
encore écrit à la reconnexion) ; points d'intérêt (seulement mesurés) ; préchargement des classes des mods des autres
auteurs.

## 31. Lenteurs du serveur : ClashWar, loot, hélicoptère, zombies, clans (19/11)

Suite de l'audit des lenteurs du 19/11 (points C3, C6, C7, C8, C10, C11, C13, C14) et deux bugs (épave de l'hélicoptère
au redémarrage, heure du tableau du ClashWar). Le but : plus aucune alerte de blocage pendant un ClashWar, un collage
WorldEdit de conteneurs ou une sauvegarde des clans. **Rien de tout cela n'a été testé en jeu** (pas de serveur ici) :
pendant les essais, garder un œil sur les alertes de blocage du staff et sur `/diag etat`.
Versions : Anticheat 2.12.2, Clans 3.17.4, ClashWar 1.3.3, Événements 1.3.2, Loot 0.20.5, Monde 1.3.5 (le launcher les
donne aux joueurs ; le canal réseau du ClashWar change de version : un jeu qui n'a pas le nouveau ClashWar est refusé à
la connexion).

ClashWar : dépôts et retours du Coffre étalés, écrits en tâche de fond, arrivées préchargées
- [ ] ClashWar à 2 équipes (clans différents) : à la clôture, les joueurs entrent en salle quelques-uns par tick (avant :
      tous dans le même tick), l'inventaire n'est vidé qu'à ce moment. Journal du serveur : une ligne « coffre de …
      depose » par joueur, puis « [matchs] coffres : N depot(s) fait(s), 0 rate(s) … ».
- [ ] Pendant le dépôt (quelques ticks) : jeter un objet (Q) ne le fait pas tomber (il est dans le Coffre et revient à la
      fin) ; pas de ramassage, de clic sur un bloc ni de commande. Le joueur ne prend aucun coup.
- [ ] Fin du tournoi : chacun retrouve son inventaire et sa position d'avant, quelques joueurs par tick ; la salle ne se
      ferme qu'après le dernier retour (30 s au plus). Clés de caisse données après le retour.
- [ ] Se déconnecter pendant son dépôt (tout de suite après la clôture) : à la reconnexion, inventaire complet, aucun
      objet en double, pas de dépôt qui traîne (`/clashwar coffre etat <joueur>` : rien).
- [ ] Se déconnecter dans la salle puis revenir après la fin du tournoi : le Coffre est rendu (un instant après la
      connexion), à la position d'avant, rien en double.
- [ ] Arrêt brutal du serveur (kill) pendant un match : à la reconnexion, le Coffre est rendu, rien en double, rien de
      perdu ; le kit du match a disparu.
- [ ] Entrée en salle, appel d'un match, retour en salle, retour à la fin : plus de gel d'une seconde à la téléportation
      (alertes de blocage du staff) ; `/diag etat`, ligne « Lenteurs (19/11) » : les « arrivées … attendues » montent
      (les téléportations du ClashWar passent maintenant par le noyau, comme `/tp`). Le match peut démarrer jusqu'à 3 s
      plus tard qu'avant.
- [ ] `/clashwar regarder` par un joueur qui n'est pas du tournoi (au spawn) : son Coffre est déposé, il arrive en salle
      puis regarde le match, comme avant.
- [ ] Réglages de `serveur/heraliz_clashwar/clashwar.json` : `coffres_en_fond` (false = écriture sur le fil du serveur,
      comme avant), `coffres_par_tick` (4) et `coffres_budget_ms` (6), `precharger_arrivees` (false = téléportations
      immédiates, comme avant), `arenes_au_demarrage` (false). `/recharger clashwar` les relit.

ClashWar : arènes posées à l'ouverture des inscriptions
- [ ] Redémarrage : plus aucune arène posée au démarrage (journal : « poses interrompues (reprises a l'ouverture des
      inscriptions) ») ; `/diag etat` : presque plus de tronçons chargés dans `heraliz_clashwar:arenes`.
- [ ] Ouverture des inscriptions (ou `/clashwar lancer`) : journal « ouverture des inscriptions, … pose(s) reprise(s), …
      carte(s) posee(s) d'avance » ; pendant la pose, peu de tronçons chargés dans le monde des arènes (avant : 2 746), et
      plus de grand déchargement 30 s après.
- [ ] Une carte posée : ses décors (cadres, supports) sont bien là, la salle et les arènes se jouent comme avant.

ClashWar : récompenses (décision du propriétaire) et heure
- [ ] Deux équipes d'un même clan seules : le tournoi se joue, mais ni primes ni clés ; le chat dit « Pas de primes ni de
      clés pour ce ClashWar : 1 clan(s) différent(s) seulement, il en faut 3. » ; le podium affiche « Sans prime ».
- [ ] Trois clans différents ou plus : primes versées ; un clan avec deux équipes sur le podium ne touche que la prime de
      la meilleure (l'autre marche affiche « Une prime par clan »). Réglage `recompenses_clans_min` (3).
- [ ] Les points de saison gardent leur règle (`saison_clans_min`).
- [ ] Mettre l'heure du PC dans un autre fuseau (ou jouer depuis un PC russe) : le tableau du ClashWar et le panneau du
      classement montrent la même heure que le chat (heure de Paris) ; le panneau du classement n'écrit plus l'heure deux
      fois (« Prochain ClashWar : mercredi 20:30 · /clashwar »).

Loot : collages WorldEdit de conteneurs et démarrage
- [ ] Coller (`//paste`) un bâtiment avec des dizaines de conteneurs : beaucoup moins long qu'avant ; les conteneurs
      rejoignent la zone de leur niveau la plus proche (ou en créent une), comme avant (`/heralizloot proche`).
- [ ] Démarrage : plus d'alerte de blocage « RotationLoot.salleDe » ; les salles à carte se rechargent comme avant.
- [ ] Vider des conteneurs, les reposer, casser et reposer un conteneur : le nombre d'actifs de la zone reste juste
      (`/heralizloot zone <zone> info` : actifs + en attente = cible).

Hélicoptère
- [ ] Hélicoptère en vol loin de tout joueur (`/event helico lancer`, puis s'éloigner) : il continue de voler et de
      tourner (il ne s'arrête jamais net au bord d'un tronçon) ; `/diag etat` : moins de tronçons chargés sur la carte
      pendant son vol. Réglage `rayon_ticket` de `serveur/config/heraliz_evenements/helico.json` (2 ; 3 = comme avant).
- [ ] Abattre l'hélicoptère, puis `/redemarrage` pendant le feu de l'épave : après le redémarrage, le coffre T7 (avec la
      boîte noire) est toujours là, encore fermé (« Caisse en feu · s'ouvre dans … ») jusqu'à l'heure de fin du feu,
      puis il s'ouvre ; les deux caisses T6 aussi. Avant : retirés au redémarrage, rien n'était donné.
- [ ] Même essai avec un arrêt brutal (kill) : le coffre ne s'ouvre pas tout de suite au redémarrage.
- [ ] `/event annuler helico` par le staff pendant le feu : coffre et caisses fermés retirés, comme avant.

Zombies
- [ ] `/zombies stats` : nouvelle ligne « Plafond : N monstres suivis a leur arrivee ; 0 oublie(s) au dernier recensement
      complet … ». Le plafond des monstres de la carte et le peuplement marchent comme avant. Réglage
      `plafond_par_arrivees` de `heraliz_zombies-server.toml` (false = toutes les entités parcourues chaque seconde, comme
      avant).

Clans
- [ ] Sauvegarde des clans (chaque minute) : plus d'alerte de blocage « ClanStore.sauvegarder » ; après un redémarrage,
      rien de perdu (banque, XP, statistiques, membres, journal).
- [ ] Coffre du clan : déposer et prendre, fermer, redémarrer : le contenu est le bon, rien en double.

Anticheat
- [ ] Anti-ESP (Spartan) : marche comme avant ; plus d'alerte de blocage « VisibilityEngine.configureWorkers ».

Pas fait (pour plus tard) : hélicoptère qui vole « sans tronçon » loin des joueurs (il garde un petit ticket) ; commandes
du staff du Coffre (`/clashwar coffre …`), toujours tout de suite sur le fil du serveur ; bordure des arènes inchangée.

## 32. Images par seconde chez les joueurs : corrections de l'audit du 19/11

Suite de l'audit des images par seconde du 19/11 (points 1, 2, 4, 6, 7, 9, 11, 12). Le but : moins de mémoire graphique,
plus d'à-coups réguliers près des lampes et au casino, plus de gel à la première vue d'une arme ou d'une ville.
**Rien de tout cela n'a été testé en jeu, aucune image par seconde n'a été mesurée** : tous les gains sont calculés.
Versions : Armes 2.59.7, Boutique 1.5.18, Bunker 0.14.7, Interface 2.0.14, Loot 0.20.6, Monde 1.3.7, Noyau 1.1.6 (le
launcher les donne aux joueurs ; aucun changement de canal réseau) ; gunpack `heraliz_5_couteaux_cs2` (texture du M9).

Grande texture des blocs (point 1 : ≈ 268 Mo de mémoire graphique en moins)
- [ ] Journal d'un joueur (`logs/latest.log`), chercher `blocks.png-atlas` : `8192x8192x0` attendu (avant :
      `16384x8192x0` ; le dernier chiffre est le niveau de mipmaps, 0 aujourd'hui : voir « À décider » plus bas).
      Mémoire graphique de cette texture : ≈ 268 Mo de moins (537 → 268 Mo sans mipmaps).
- [ ] Les 5 cartes d'accès (rouge, verte, bleue, Bunker des Bois, Bunker du Pic) : en main, au sol, dans un cadre, dans
      l'inventaire : face et dos comme avant (images recadrées sur la partie lue, rien d'autre ne change).
- [ ] Les 12 fragments (vert, bleu, rouge, 1 à 4) : face, dos et numéro comme avant, de près et dans l'inventaire.
- [ ] Quelques blocs de meubles cfm (bunkers, villes, maisons) : aucun damier violet. 386 images de cfm que rien ne cite
      ne sont plus rangées (filtre de notre pack « textures ») ; 262 images orphelines du pack « textures » retirées.
- [ ] `python outils/atlas_blocs.py` : taille, remplissage (94 %) et plus grosses images ; `verifier.py` refuse
      maintenant une texture qui ferait déborder la grande texture des blocs.

Lampes rouges, gyrophares, lampe rouge fixe (point 2 : plus d'à-coups toutes les 5 s)
- [ ] Salle d'un bunker avec des lampes rouges : le verre clignote comme avant (5 s allumé, 1 s éteint), la pièce
      s'assombrit pendant la seconde noire près des lampes (à moins de 6 blocs, comme avant ; plus rien au-delà de 20
      blocs : une pièce vue de loin ne s'éteint plus), la teinte rouge reste.
- [ ] F3 près des lampes : la ligne des tronçons en construction ne monte plus toutes les 5 s.
- [ ] Gyrophare : la lumière tourne puis s'éteint 1 s, comme avant ; lampe rouge fixe (néon) : clignote comme avant.
- [ ] Icônes des trois lampes dans l'inventaire : allumées, ne clignotent pas.
- [ ] Une lampe posée avant cette mise à jour et restée dans la seconde noire : la pièce est éclairée au chargement.

Réglette qui grésille du casino (point 2)
- [ ] Au Trou : la réglette grésille comme avant (rafales, coupures, starter), selon sa variante (Grésille, Agonise,
      Stable) ; la salle s'assombrit près d'elle pendant les coupures (toute la lumière de bloc à moins de 6 blocs, plus
      rien à 16) ; plus de saccade pendant les rafales (F3 : tronçons en construction). Deux joueurs voient chacun leurs
      propres rafales (plus synchronisées).

Armes des autres joueurs (point 4)
- [ ] Un autre joueur sort une arme jamais vue de la session (ou arrive avec) : plus de gel à la première vue.
      `/diagclient 30` pendant un combat à plusieurs : moins d'images lentes au premier contact.
- [ ] M9 et ses 18 skins : même aspect en main, à l'inspection et sur les autres (texture 4096 → 2048 ; comparaison des
      images, gauche avant, droite après : `maquettes/fps_19nov/m9_2048_avant_apres_rouille.png` et `_bords.png` ;
      seule différence visible à l'agrandissement : les filets d'un point au bord des faces, moitié moins nets).

Conteneurs HD (point 6)
- [ ] Journal du jeu au démarrage : « [loot] 246 maillages de conteneurs prêts en fond (… fils, … ms) » ; entrée dans la
      première ville de la session : le terrain apparaît sans retard ; conteneurs identiques.

MAtmos (point 7)
- [ ] Sa propre ambiance comme avant (oiseaux, vent, eau, grottes) ; plus de sons d'ambiance joués à la place des autres
      joueurs proches ; musiques de biome et ambiance du Nether jouées comme avant (lues en continu).

Stickers et StatTrack (point 9)
- [ ] Arme stickée et StatTrack : même dessin, en main et chez les autres ; placement des stickers au menu de l'arme
      (viser à la souris) comme avant.

Shaders des camos (point 11)
- [ ] F3+T (ou changer le profil graphique, qui change les mipmaps) : le rechargement est plus court ; journal « Shaders
      des camos inchangés : … programmes gardés » ; les camos et les effets 3D s'affichent toujours ensuite.

Images par seconde plafonnées à 144 (point 12, choix du propriétaire)
- [ ] Nouvelle installation, et une fois chez les joueurs déjà là : plafond 144 images par seconde (réglages HeraliZ
      version 5). L'écran Graphismes montre 144 ; le curseur de Minecraft et d'Embeddium montre 140 (il ne garde que des
      multiples de 10 : le cran 140 vaut 144 chez HeraliZ). Remonter le curseur ou « Illimité » marche tout de suite.
- [ ] `/diagclient` : « FPS max : 144 » (plafond réel de la fenêtre).

À décider (propriétaire) : **la grande texture des blocs n'a aucune mipmap aujourd'hui**, quel que soit le profil
graphique. Trois images d'anciens menus du jar de cfm (`clans_gui/gui/cakdhrb` 236 x 245, `required/recipe_showcase`
174 x 164, `required/recipe_stonecutter` 164 x 174), ajoutées par son atlases/blocks.json, ne sont pas des multiples de
16 : Minecraft coupe alors les mipmaps de tout le terrain (journal : « limits mip level from 2 to 0 »). Terrain qui
scintille au loin, et plus de travail pour la carte graphique de loin. `python outils/mipmaps.py --corriger` pose des
copies retaillées (240 x 240, 176 x 160, 160 x 176) dans notre pack « textures » (jar intact) et rend les mipmaps des
profils (2 ou 4) ; **pas fait** : le terrain au loin change d'aspect (plus doux, plus de scintillement), à voir en jeu
avant. `outils/atlas_blocs.py --verifier` le signale (sans bloquer).

Pas fait (décision du propriétaire, rendus à valider d'abord) : vraies versions allégées des 62 armes sans LOD,
allègement plus fort des conteneurs HD, camos « cuits » dans une texture ; textures des armures, de la roue du Souffle du
loup et de l'Olympia gardées à leur taille (vrai détail).

## 33. Anticheat et launcher : injection en cours de partie, launcher imité (19/11)

Demande du propriétaire : « empêcher les injections, les modifications de client ». Ce qui change (le détail est dans
`modules/anticheat/README.md`, « Audit du 19/11 », et `launcher/docs/HERALIZ-SECURITE.md`) :
- dans le jeu, Spartan voit maintenant une classe du jeu ou de l'anticheat **modifiée en mémoire** après son chargement
  (c'est ce que fait un client de triche injecté), et les **bibliothèques d'un agent Java ou d'un débogueur** chargées
  dans le jeu (`instrument`, `jdwp`…) : expulsion, avec capture d'écran ;
- le launcher regarde **depuis l'extérieur du jeu** ces mêmes bibliothèques (un jeu modifié ne peut pas les lui cacher)
  et compare chaque jar du pack à son empreinte signée juste après le lancement ;
- le serveur refuse un **launcher imité** qui réutilise sa clé (même clé pour deux comptes ou deux parties) ;
- le launcher retire de nouvelles options et variables qui ouvrent une porte d'injection (contrôle à distance de Java,
  `CLASSPATH`, dossiers de bibliothèques sur Mac) ; Electron passe en 44.11.3 (correctifs de sécurité).

Ces règles sont nouvelles : elles sont **« en rodage »**. Si trois joueurs différents en déclenchent une en moins d'une
heure, elle se coupe toute seule (alerte « Règle suspendue » au staff) : une erreur ne peut jamais expulser tout le monde.
Versions : Anticheat **2.13.0** (avec la mise en ligne du serveur), launcher **1.17.0** (à publier ensuite : onglet
Actions, « Launcher HeraliZ », « Run workflow », case **publier** ; pas fait). Les deux marchent avec l'ancienne version de
l'autre. Mis à jour le 19/11 (décisions du propriétaire, en bas de cette section) : anticheat **2.15.0**, launcher
**1.18.0**. **Rien de tout cela n'a été testé en jeu** (aucun Minecraft ici) : seulement des tests automatiques.

À tester (joueurs honnêtes : rien ne doit changer pour eux)
- [ ] Windows, launcher 1.17.0 : se connecter, jouer 10 min avec Discord, Steam, OBS ou NVIDIA ouverts. « ✅ Jeu validé »
      comme avant ; **aucune** alerte « Injection », « Règle suspendue », « Launcher … launcher imité » ni « Gardien :
      bibliothèque(s) de Java chargée(s) DANS le jeu ».
- [ ] Jouer normalement (inventaire, réglages vidéo, capture d'écran F2, capture demandée par le staff) : toujours aucune
      alerte (Java charge alors des bibliothèques à lui, qui ne comptent pas).
- [ ] Se déconnecter puis se reconnecter **sans fermer le jeu** : aucune alerte « launcher imité » (même partie = même clé,
      c'est normal). Fermer le jeu, relancer depuis le launcher, se reconnecter : aucune alerte non plus.
- [ ] Deux joueurs sur deux PC en même temps : aucune alerte.
- [ ] Mac et Linux (AppImage, .deb), si quelqu'un en a un : connexion normale, « Jeu validé ».
- [ ] `/spartan status <joueur>` : pas de ligne « règles en rodage suspendues ».
- [ ] Ancien launcher (1.16.2) avec le nouveau serveur : connexion normale (rien ne change pour lui).

Si quelque chose se passe mal
- « Règle suspendue » : regarder les dernières alertes « Injection » des joueurs cités. Fausse alerte : retirer le motif
  cité de `[injection] indices` dans `serveur/config/spartan-common.toml` (relu sans redémarrer).
- Des joueurs honnêtes lisent « ce jeu n'a pas été lancé par le launcher HeraliZ officiel » : passer `preuveExigee` à
  `"alerte"` dans `[gardien]` (sans redémarrer) et le signaler, pour correction.
- Après une semaine sans « Règle suspendue » ni fausse alerte : on peut vider `rodage = []` (les règles restent, sans
  disjoncteur), ou le laisser.

Décidé par le propriétaire le 19/11 (détail dans `launcher/docs/HERALIZ-SECURITE.md`)
1. Le dépôt GitHub **reste public** (choix délibéré, la doc le dit).
2. `requireHardenedJvm = true` : fait (une alerte de plus, sans risque).
3. **Packs de textures ajoutés par le joueur interdits** : fait (anticheat **2.15.0**, launcher **1.18.0**, à publier ensuite
   comme le 1.17.0 ; pas fait). Le launcher met de côté, avant la partie, les packs du dossier `resourcepacks` (et
   `kiwipacks`) du jeu, dans `spartan/ecartes/<date>/` (jamais effacés). Le serveur refuse un joueur qui a encore un pack
   ajouté actif, avec un message qui dit lequel retirer (jamais de ban). Nos packs (textures et herbe HeraliZ, terrain), le
   pack de base de Minecraft et les ressources des mods ne comptent jamais.
4. Verrou d'Electron « intégrité de l'archive » : reste coupé (pas encore essayé sur un vrai PC Windows).

À tester en plus (packs de textures ; **rien n'a été testé en jeu**)
- [ ] Mettre un pack de textures quelconque (.zip) dans le dossier `resourcepacks` du jeu HeraliZ, puis lancer depuis le
      launcher 1.18.0 : la partie démarre normalement, le pack a été déplacé dans `spartan/ecartes/<date>/resourcepacks`, et
      la connexion au serveur se passe sans refus ni alerte « ResourcePack ».
- [ ] Jeu normal (sans pack ajouté) : textures HeraliZ et herbe comme avant, aucun refus, aucune alerte « ResourcePack ».
- [ ] Avec l'ancien launcher (1.17.0 ou 1.16.2) et un pack dans `resourcepacks` : le mod d'interface ne l'active pas, donc
      connexion normale. Si quelqu'un est refusé : il lit le nom du pack à retirer ; dans le fil Discord, alerte
      « ResourcePack … connexion refusée ».
- [ ] `/spartan status <joueur>` : toujours pas de ligne « règles en rodage suspendues ». Refus à tort : passer
      `refuseCustomResourcePacks = false` dans `[resources]` (relu sans redémarrer ; il ne reste alors qu'une alerte).

## 34. Anticheat côté serveur : rafales de tirs, ouvertures à travers les murs, faux lag (19/11)

Demande du propriétaire : « le plus possible côté serveur ». Ces protections marchent même contre un jeu entièrement
modifié : le serveur ne croit que ce qu'il mesure lui-même (heure d'arrivée des paquets, murs entre le joueur et l'objet).
**Rien de tout cela n'a été testé en jeu** (pas de serveur ici) : seuls les calculs ont été essayés hors du jeu (tests
automatiques). Version : Anticheat 2.14.0 (le launcher la donne aux joueurs ; 2.13.0 : section 33). Réglages dans
`serveur/config/spartan-common.toml`, sections `[tirs]` et `[interactions]`, relus sans redémarrer.

Ce qu'un tricheur pouvait faire avant, et ce qui se passe maintenant :
- **Rafale de tirs** : un jeu modifié envoyait d'un coup 8 tirs d'un fusil (15 au minigun), datés comme s'ils étaient
  partis à la bonne cadence : TaCZ les acceptait tous dans le même instant. Maintenant ces tirs partent quand même, mais
  un par un, à la cadence de l'arme : la rafale ne sert plus à rien.
- **Ouvrir à travers un mur** : Minecraft ne vérifie que la distance d'un clic droit (6 blocs). On pouvait vider un
  conteneur d'une salle à carte depuis le couloir, sans carte, ou fouiller un joueur à terre derrière un mur. Maintenant
  le clic ne fait rien et le joueur lit « Tu ne vois pas ça d'ici. ».
- **Faux lag** : un jeu qui fait croire à un gros ping faisait reculer les cibles de TaCZ (jusqu'à 350 ms) et touchait un
  joueur déjà caché. Maintenant TaCZ ne recule les cibles que du vrai ping mesuré par l'anticheat.
- **Frapper un joueur caché** : un joueur que l'anticheat cache (derrière un mur) n'existe pas dans le jeu du tricheur ;
  un coup ou un clic sur lui est donc fabriqué : refusé.
- **Visée forcée** (« silent aim ») : un aimbot discret tourne la visée sur un joueur seulement le temps du paquet de tir,
  puis rend la vraie visée au paquet suivant, sans que l'écran du tricheur bouge. Le serveur voit cet aller-retour (plus
  de 15° aller, retour au degré près, en un tick) : alerte au 4e cas en 10 minutes, chaque cas écrit dans
  `config/spartan/tirs.log`.

Ce qu'une alerte donne au staff (salon Discord de l'anticheat, staff en jeu, `config/spartan/alerts.log`) :
- « **Tirs** (VL 1) : 8 tirs arrivés d'un coup alors que leurs heures suivent la cadence de l'arme, étalés de plus de
  300 ms (impossible pour un jeu honnête) ou refusés, en 60 s (…) : rafale « rapid fire » par heures de tir
  trafiquées ? ». Un étalement plus court (réseau qui se vide d'un coup) ne compte jamais pour l'alerte ;
- « **Interaction** (VL 1) : 3 interaction(s) impossible(s) refusée(s) en 10 min ; la dernière : ouvre heraliz_loot:…
  (le conteneur) à travers un mur (3.2 blocs, aucune ligne de vue) ». Chaque refus, même seul, est aussi écrit dans
  `config/spartan/interactions.log`.
- `/spartan status <joueur>` : deux lignes de plus, « tirs : … cadence réelle : N tir(s) étalé(s) » et « interactions :
  N refus dans les 10 dernières minutes ». Aucune sanction automatique : le staff regarde et décide.

À vérifier (joueur honnête : rien ne doit changer) :
- [ ] Tir en automatique, en rafale, au coup par coup, fusil à pompe, sniper, minigun, sur des cibles et sur un autre
      joueur (serveur d'essai, deux comptes) : les armes tirent comme avant, aucune alerte « Tirs ».
- [ ] Même chose en Wi-Fi chargé ou en partage de connexion 4G, et pendant une petite coupure (couper le Wi-Fi 1 s en
      plein tir automatique) : aucune alerte « Tirs ». Au pire, juste après la coupure, deux ou trois balles partent une
      fraction de seconde plus tard.
- [ ] Journal du serveur au démarrage : « Cadence réelle des tirs TaCZ active ». Jamais « Cadence réelle des tirs
      inactive : l'arrivée des tirs de TaCZ n'est jamais reconnue » (sinon : prévenir, TaCZ aurait changé).
- [ ] Ouvrir des conteneurs partout (villes, bunkers, salles à carte avec la carte, coffres T7, caisse d'un largage, corps,
      joueur à terre) : tout s'ouvre comme avant, y compris en courant, accroupi, allongé, en sautant, de biais, par une
      porte ouverte, au-dessus d'une table, un conteneur de deux blocs (frigo) par son haut ou son bas.
- [ ] Ouvrir un conteneur juste derrière une fenêtre en verre (sans l'ouvrir) : impossible, comme dans le jeu normal (le
      jeu vise la vitre).
- [ ] Salle à carte, porte fermée : se coller au mur ou à la porte depuis le couloir et cliquer droit vers un conteneur
      qu'on ne voit pas (un jeu honnête vise le mur, rien ne se passe, comme avant). Avec un client de triche d'essai qui
      ouvre à travers les murs : « Tu ne vois pas ça d'ici. », rien ne s'ouvre, alerte au 3e essai.
- [ ] Monter dans un véhicule, en descendre, rouler, puis ouvrir un coffre : rien de bloqué.
- [ ] Mode créatif ou spectateur (staff) : rien n'est jamais refusé.
- [ ] `config/spartan/interactions.log` après une soirée de jeu normal : vide ou presque (une ligne isolée de temps en temps
      peut venir d'un décalage du réseau ; plusieurs lignes du même joueur honnête : prévenir).
- [ ] Combat au corps à corps et à l'arme près d'un mur et d'un coin de rue : coups et tirs comme avant.
- [ ] Combats à l'arme entre deux comptes, en visant vite d'une cible à l'autre, en « flick » (coup de souris sec puis
      tir), au sniper : aucune alerte « Tirs … visée tournée sur un joueur seulement le temps du tir ». Dans
      `config/spartan/tirs.log`, aucune ligne « visée forcée ? » après une soirée de jeu normal (une ligne isolée : à
      signaler, avec l'arme et le geste).

Si un souci apparaît, à couper tout de suite dans `config/spartan-common.toml` du Ptero (pris à chaud) :
`[tirs] cadenceReelle = false` (TaCZ juge seul les tirs, comme avant), `[tirs] reculPingMesure = false`,
`[tirs] viseeForcee = false`, `[interactions] enabled = false` (ou seulement `ligneDeVue = false`, `portee = false`,
`entites = false`, `cibleCachee = false`).

Pas fait, volontairement :
- **Renvoi en arrière des tricheurs qui volent ou courent trop vite** (`[mitigation] movementSetback`) : toujours coupé
  depuis le 11/10 (fausses alertes chez des joueurs honnêtes). Il ne faut le rallumer qu'après une semaine d'alertes de
  mouvement relues sans aucune fausse alerte ; ces alertes ne sont pas visibles d'ici (elles sont sur le Ptero).
- **Cacher les zombies et les objets derrière les murs** comme les joueurs : les zombies se font entendre de toute façon
  (sons envoyés à leur place) et ça multiplierait le coût de l'anticheat. Le contenu des conteneurs, lui, ne part jamais
  avant l'ouverture (T7 : « ? » tant que l'objet n'est pas identifié), seule leur place se voit (c'est la carte).
- **Tir annulé pour une visée forcée** : seulement une alerte (le staff juge) ; seule la rafale est corrigée d'office.
- **Clics trop rapides dans l'inventaire** (prendre tout un coffre d'un coup) : Minecraft le fait déjà lui-même avec
  Maj + double-clic ; une règle sur la vitesse des clics donnerait des alertes à tort.

## 35. /money et /profil : son argent et ses statistiques (19/11)

Demande du propriétaire : « des commandes basiques : voir son argent avec /money, voir un panel avec son profil (kills,
morts…) avec /stats ou /profil ». Maquettes validées le 19/11 (écran du profil, le sien et celui d'un autre joueur ;
bandeau de /money). Vos réponses : `/profil pseudo` gardé, les deux commandes marchent partout (carte et combat
compris : elles ne font que lire), les chiffres comptent depuis toujours (seul `/wipe` les remet à 0).
**Rien de tout cela n'a été testé en jeu** (pas de serveur ici) : seuls la fabrication et les contrôles sont passés.
Versions : Clans 3.18.0, Boutique 1.6.0, Quêtes 1.24.0, Noyau 1.1.7, Interface 2.0.15. Le canal des clans passe en
version 14 : les joueurs doivent avoir les nouveaux jars (le launcher les donne).

Ce qui est fait :
- **`/money`** (ou **`/argent`**) : un bandeau en haut à droite pendant 6 s, rien dans le chat. En titre, les hérals du
  compte ; dessous, seulement ce qui n'est pas à 0 : les bourses qu'on a sur soi (inventaire et sac à dos porté, à
  revendre à Mila), les capsules du casino (compte et inventaire), les Points Boutique. Les PB viennent du site : le
  bandeau les attend 2 s au plus (sinon il part sans la ligne des PB) ; le solde lu est gardé 1 minute. Tout à 0 : « Les
  quêtes et Mila en rapportent, au spawn ». Un bandeau toutes les 2 s au plus (le précédent est encore là).
- **`/profil`** (ou **`/stats`**) : l'écran du profil, dans le style des écrans du clan. En haut : tête, pseudo, grade de
  la boutique, « Sur HeraliZ depuis le … · … h de jeu », et son solde (seulement le sien). À gauche : kills, morts, K/D,
  zombies tués, extractions, conteneurs fouillés ; quêtes de Varga et dernier titre, meilleure série de kills sans
  mourir, plus longue vie sur la carte, morts contre un joueur. À droite : son clan (grade, depuis quand, place dans la
  saison), ses classements (kills, hérals, zombies : « 12e sur 1 240 »), et ce qu'il a sur lui (bourses, capsules).
  Échap ou la croix ferment.
- **`/profil pseudo`** : le profil d'un autre joueur, connecté ou non. Jamais son argent, ni ce qu'il porte, ni où il est,
  ni son rang en hérals. Les suggestions de la commande ne montrent jamais le staff caché. Pseudo inconnu : « Aucun
  joueur nommé « … » » au-dessus de la barre d'objets. Un profil par seconde au plus.
- **Ce qui est compté** (pour tous les joueurs, avec ou sans clan) : les kills sont ceux du classement « Meilleurs
  tueurs » (mêmes règles anti-triche : pas un camarade de clan, pas la même adresse, pas une victime tout juste
  réapparue, pas le même joueur deux fois en 10 min) ; les morts, zombies et conteneurs comme les statistiques des clans
  (une mise à terre n'est pas une mort) ; une extraction = arriver au spawn depuis la carte sans être mort (comme les
  quêtes du jour) ; la « vie sur la carte » dure tant qu'on reste sur la carte sans mourir (une déconnexion la met en
  pause, une mort ou un départ de la carte la termine). Jamais compté : ClashWar, tutoriel des bases, bots de charge,
  zombies tués en créatif.
- **Au premier démarrage** (fichier `heralisz_clans/profils.json` créé à la racine du serveur) : les kills de tout le monde
  sont déjà là (depuis le 03/11) ; pour les membres d'un clan, les morts, zombies, conteneurs et temps de jeu de leur clan
  sont repris (comptés depuis leur arrivée dans ce clan), une seule fois. Partent de 0 pour tout le monde : extractions,
  séries, plus longue vie, morts contre un joueur ; et pour un joueur sans clan, aussi les morts, zombies, conteneurs et
  le temps de jeu. « Sur HeraliZ depuis » : la date gardée par le serveur avec le joueur, sinon sa première connexion
  après la mise à jour.
- `/wipe joueur` et `/wipe tout` remettent son profil (ou tous) à 0 ; le pseudo et la date d'arrivée restent.
- Les deux commandes sont dans l'écran Informations du menu Échap.

À vérifier :
- [ ] Au spawn, puis sur la carte, puis en combat (après avoir tapé un autre joueur), puis à terre : `/money`, `/argent`,
      `/profil`, `/stats` marchent partout (jamais « seulement au spawn » ni « Impossible en combat »).
- [ ] `/money` avec une bourse dans l'inventaire et une autre dans le sac à dos porté : la ligne « Bourses sur toi » fait
      le total des deux. Sans bourse ni capsule ni PB : seulement « sur ton compte ». Nouveau compte à 0 : « 0 héral » et
      « Les quêtes et Mila en rapportent, au spawn ». Taper `/money` 5 fois d'affilée : un seul bandeau.
- [ ] Ligne des PB : un joueur qui a des PB les voit (ils doivent être les mêmes que `/pb`). Site des PB injoignable :
      le bandeau arrive au bout de 2 s, sans la ligne des PB.
- [ ] Le bandeau garde ses lignes (une par sorte d'argent) : aucune ligne coupée au milieu d'un nombre. En anglais et en
      russe aussi.
- [ ] `/profil` : l'écran s'ouvre, nets les textes, rien ne dépasse (fenêtre et plein écran, en français, en anglais, en
      russe). Échap et la croix ferment ; le jeu ne se met pas en pause.
- [ ] Tuer un zombie, fouiller un conteneur plein, s'extraire, mourir, tuer un joueur (deux comptes, pas du même clan,
      pas la même adresse) : chaque chiffre monte de 1 au `/profil` suivant ; la meilleure série monte avec les kills et la
      série repart de 0 à la mort.
- [ ] Rester 5 min sur la carte puis rouvrir `/profil` : « Plus longue vie sur la carte » au moins 5 min ; se déconnecter
      1 min sur la carte, revenir : le temps continue (sans la minute hors ligne) ; mourir : le record reste, la vie suivante
      repart de 0.
- [ ] Mourir à terre puis être achevé : une seule mort. Être relevé : pas de mort.
- [ ] Match de ClashWar et tutoriel des bases : ni morts, ni kills, ni zombies comptés.
- [ ] `/profil pseudo` d'un joueur connecté puis d'un joueur déconnecté : son profil, sans solde, sans « Sur toi », sans
      rang en hérals, avec « Visible par tous ». Un pseudo qui n'existe pas : le message rouge au-dessus de la barre.
      Un membre du staff caché n'apparaît pas dans les suggestions de `/profil` (Tab).
- [ ] Un joueur sans clan : « Pas de clan » (et, sur son profil, l'aide `/clan`) ; un membre : son clan, son grade, sa place
      dans la saison (« pas classé » sans cœur posé).
- [ ] Après un redémarrage : tous les chiffres sont toujours là (fichier `heralisz_clans/profils.json`).
- [ ] `/wipe` d'un compte d'essai : son profil repart de 0 (sauf le pseudo et la date d'arrivée).
- [ ] Si une de ces commandes répond autre chose que nos écrans (une commande de Mohist du même nom) : prévenir.

Si un souci apparaît : `/money`, `/argent`, `/profil` et `/stats` peuvent être retirées des listes du noyau
(`commandes_permises` et `commandes_permises_combat` de `serveur/defaultconfigs/heraliz_noyau-server.toml`) pour ne plus
marcher que hors de la carte et hors combat ; rien d'autre ne dépend d'elles.

Pas fait, volontairement :
- **Les statistiques de Minecraft** (temps de jeu, distance…) : elles ne sont plus enregistrées (perf, 25/10) ; le profil a
  ses propres compteurs.
- **Le profil depuis la carte du menu Échap** (clic sur sa carte) : seulement les commandes pour l'instant ; à ajouter si
  vous le voulez.
- **Le ClashWar et les points de saison d'un joueur** : le ClashWar et la saison sont comptés par clan, pas par joueur.

## 36. Images par seconde chez les joueurs : audit du 20/11, corrections et deux décisions

Demande du propriétaire : « relancer un audit complet : vérifier que tout soit ultra optimisé ». Cette partie couvre le
**PC des joueurs** : images par seconde, à-coups, mémoire, processeur ; tout ce que nos mods dessinent, les mods des
autres auteurs, la partie joueur de l'anticheat et le launcher pendant la partie. **Rien de tout cela n'a été testé en
jeu ni sur une vraie carte graphique** : les chiffres viennent de mesures faites ici sans jeu (petits programmes qui
refont le même calcul) et du code. Versions : Voix **1.6.9**, Anticheat **2.15.1** (le launcher les donne aux joueurs ;
jar joueur et jar serveur partent ensemble), launcher **1.18.1** (fabriqué, **pas publié**).

Ce qui pèse le plus chez un joueur, du plus lourd au plus léger :
1. **Les armes des autres joueurs** : 123 fiches d'armes (66 modèles) n'ont pas de version allégée ; TaCZ dessine alors
   l'arme complète, cube par cube, à chaque image (≈ 480 cubes en moyenne, jusqu'à 1 640 ; une version allégée : 40 à
   80). Mesuré ici : 0,35 ms par arme complète et par image (0,8 ms pour la plus lourde), 0,07 à 0,1 ms allégée ; au
   spawn avec 20 joueurs armés à l'écran, ≈ 4 ms par image ici, 9 à 12 ms sur un petit processeur. **Décision A**
   plus bas.
2. **La liste Tab peignait le corps 3D de tous les joueurs en ligne** : corrigé (Voix 1.6.9), ≈ 120 Mo de mémoire
   graphique en moins avec 150 joueurs en ligne.
3. **Les cartes de position des camos** : jusqu'à 256 Mo de mémoire graphique dans une longue partie (une par modèle
   d'arme vu avec un camo, gardées jusqu'au rechargement des textures). Pas changé (voir « Pas fait »).
4. **Le launcher pendant la partie** : son anticheat (le Gardien) lance un PowerShell toutes les 10 à 20 s, et deux à
   la fois une fois sur trois : corrigé (launcher 1.18.1, priorité basse, jamais deux à la fois).
5. **Le sol au loin sans mipmaps** : il fourmille quand on bouge. **Décision B** plus bas.
6. **L'anticheat dans le jeu** : un fichier lu sur le fil du rendu toutes les 15 s, et une pause de tout le jeu de trop
   à chaque contrôle : corrigé (Anticheat 2.15.1).
7. **Mémoire Java** : le préchauffage des armes du 19/11 garde ≈ 82 Mo de modèles (et non 40 comme écrit le 19/11 ;
   26 Mo sont des copies du même couteau, une par skin) ; ≈ 30 Mo avec la décision A. Les conteneurs HD : ≈ 41 Mo.
   Rien de gênant avec 4 à 6 Go donnés au jeu.
8. Le reste a été relu et va bien : écrans neufs (/profil, marchands), HUD, lampes et réglette du casino, camo Diamant
   (+7 à 10 % du coût de son camo seul), préchauffage des armes et des conteneurs, veille de l'anticheat (toutes les 3 s,
   fil de basse priorité), mods des autres auteurs (réglages de TaCZ, Embeddium, interface des véhicules).

Corps des joueurs (Voix 1.6.9)
- [ ] Serveur plein (ou deux comptes) : appuyer sur Tab : les têtes de tous les joueurs s'affichent comme avant.
- [ ] Un joueur qu'on n'a vu que dans la liste Tab arrive près de soi : son corps est gris une image ou deux, puis
      peint (comme aujourd'hui pour un joueur jamais vu) ; ensuite il cligne des yeux et parle comme avant.
- [ ] Créateur de personnage : l'aperçu apparaît directement peint (jamais gris), et change à chaque réglage.
- [ ] HUD de la voix (groupe) et écran des volumes : têtes comme avant.

Anticheat 2.15.1 (rien ne doit changer pour un joueur honnête)
- [ ] Partie de 15 min lancée depuis le launcher : « ✅ Jeu validé », aucune alerte « Gardien muet » ni « lecture » ;
      `/spartan status <joueur>` comme avant.
- [ ] Contrôles complets (toutes les 1 min 30 à 4 min) : toujours reçus (rapport du contrôle d'intégrité dans
      `/spartan status`), aucune alerte nouvelle.

Launcher 1.18.1 (à publier quand vous voulez : onglet Actions, « Launcher HeraliZ », « Run workflow », case **publier**)
- [ ] Windows : pendant une partie, Gestionnaire des tâches, onglet Détails : les `powershell.exe` qui apparaissent un
      instant toutes les 10 à 20 s sont en priorité « Inférieure à la normale », et jamais deux en même temps.
- [ ] La partie est validée comme avant (« ✅ Jeu validé »), aucune alerte « Gardien » au staff.

**Comment mesurer les images par seconde en jeu** (pour tout ce qui précède et les deux décisions)
1. En jeu, taper `/diagclient 30` : le jeu enregistre pendant 30 secondes (jouer normalement pendant ce temps).
2. Le rapport est écrit dans le dossier `diag/` du jeu (`client-<date>.txt`). Le staff peut aussi le demander à
   distance : `/diag client <joueur> 30` (le joueur est prévenu, le rapport arrive sur le serveur).
3. À lire dans le rapport : la 1re ligne « … FPS en moyenne, 1 % le plus lent : … FPS » (le 2e chiffre, c'est ce qu'on
   ressent comme des saccades) ; « images de plus de 50 ms » (les à-coups) ; puis « temps de dessin par type
   d'entité » : la ligne `minecraft:player` donne le coût des joueurs (et de leurs armes) par image.
4. Toujours comparer au même endroit, à la même heure de jeu, avec le même profil graphique : par exemple au spawn
   avec du monde, au centre d'une ville, et en forêt en marchant. Faire la mesure avant puis après un changement.

**Décision A : versions allégées des armes vues chez les autres joueurs** (prêt, pas appliqué)
- Images : `maquettes/fps_20nov/armes_lod_resume.png` (8 armes à 10, 30 et 60 blocs), `armes_lod_stakeout.png` et
  `armes_lod_mod2.png` (de près aussi). Gauche : l'arme d'aujourd'hui ; droite : la version allégée. Chaque carré de
  l'image agrandie est un point de l'écran (écran 1920 x 1080).
- Ce qui change : l'outil retire les petits cubes (vis, rivets, rainures, lettrages) ; la forme, les couleurs et la
  texture restent. À 30 et 60 blocs : aucune différence visible. À 10 blocs : quelques points diffèrent. De près
  (3 blocs, dans la main d'un autre joueur ou la sienne en vue F5) : on voit qu'il manque des détails.
- Gain : 59 300 cubes -> 19 400 pour ces armes (-67 %), soit 3 à 4 fois moins de calcul par arme d'un autre joueur ;
  ≈ 53 Mo de mémoire Java en moins. Les armes en main (vue normale) ne changent jamais.
- Option (à côté) : TaCZ peut garder le modèle complet à moins de N blocs (`GunLodRenderDistance`, réglage du jeu de
  chaque joueur, aujourd'hui 0 = version allégée à toute distance en vue de dehors, déjà le cas des 60 armes qui en ont
  une). Avec N = 8 : plus beau de près, mais plus cher au spawn où tout le monde est serré.
- Pour l'appliquer : `python outils/lod_armes.py --installer` (écrit les versions allégées dans les gunpacks et les relie
  aux fiches), puis `python outils/verifier.py`, fabriquer et mettre en ligne comme d'habitude. `python outils/lod_armes.py`
  liste ce qui changerait, `--apercu <dossier>` refait les images. À relancer après l'ajout d'une arme ou d'un skin.

**Décision B : mipmaps du sol** (prêt, pas appliqué)
- Aujourd'hui, la grande texture des blocs n'a aucune mipmap (trois vieilles images de menus de cfm les coupent pour
  tout le terrain, voir section 32). Les mipmaps, ce sont des versions réduites de chaque texture que la carte graphique
  prend pour ce qui est loin.
- Images : `maquettes/fps_20nov/mipmaps_sans.png` (aujourd'hui), `mipmaps_mip2.png` (avec, profil Performance),
  `mipmaps_mip4.png` (avec, profils Équilibré et Qualité), `mipmaps_loin_zoom.png` (le sol de 10 à 90 blocs agrandi), et
  `mipmaps_fourmillement.gif` (en marchant). Rendus hors du jeu avec nos textures (herbe de la carte et ses variantes,
  gravier) et la même façon de lire les textures que la carte graphique.
- Ce qui change : le sol au loin ne fourmille plus en marchant : d'une image à l'autre, 45 % des points du sol entre 10
  et 90 blocs changent de couleur aujourd'hui, 8 % avec 2 niveaux, 3 % avec 4 (`mipmaps_mesures.txt`). En échange, il
  est plus flou et plus « lissé » au-delà de 15 à 20 blocs, et les petits détails peints sur l'herbe (fleurs, cailloux)
  s'effacent au loin. Avec 4 niveaux (Équilibré, Qualité), plus lisse encore qu'avec 2 (Performance).
- Coût : ≈ 84 Mo de mémoire graphique en plus (Performance), ≈ 89 Mo (Équilibré, Qualité) ; la grande texture reste
  en 8192 x 8192 (vérifié avec `outils/atlas_blocs.py --mip 4`). Démarrage un peu plus long (fabrication des mipmaps).
- Gain : surtout l'image plus calme ; petit gain de vitesse possible sur les petites cartes graphiques en ville (les
  grandes textures des conteneurs et des meubles lues de loin), pas mesurable ici.
- À surveiller si vous dites oui : des traits entre les blocs vus de loin (comme avec le filtrage anisotrope le 18/11).
- Pour l'appliquer : `python outils/mipmaps.py --corriger` (pose 3 copies retaillées des images de cfm dans notre pack ;
  jar de cfm intact), puis fabriquer et mettre en ligne. Les profils gardent leurs réglages (2 ou 4 niveaux).
  `python outils/apercu_mipmaps.py <dossier>` refait les images.

Pas fait, volontairement
- **Cartes de position des camos** (jusqu'à 256 Mo de mémoire graphique) : on pourrait libérer celles d'armes pas vues
  depuis 10 minutes, mais le camo disparaîtrait quelques images quand l'arme revient. À décider si des joueurs avec une
  petite carte graphique se plaignent de saccades après une longue partie.
- **Les relevés de la pile du rendu par l'anticheat** pendant un contrôle (40 par contrôle, chacun une toute petite pause
  avec Java 17) et la **veille de l'anticheat** sous Windows (liste de tous les fils du PC toutes les 3 s, fil de basse
  priorité) : gardés tels quels, ils servent à attraper les tricheurs et ne se voient pas.
- **Propositions du 19/11** (ImmediatelyFast, Dynamic FPS, MoreCulling) et **camos « cuits »** : toujours à votre
  décision, rien de neuf.

## 37. Réseau, mémoire et disque : audit du 20/11

Demande du propriétaire : « relancer un audit complet : vérifier que tout soit ultra optimisé ». Cette partie : le
réseau (ce qui part vers chaque joueur), la mémoire, le disque et tous les réglages du dossier `serveur/`. Les deux autres
parties de l'audit (ticks du serveur ; images par seconde chez les joueurs, section 36) ont leurs propres sections.
Versions : Équipement **1.4.1** (Armurerie 0.16.2), Noyau **1.1.8**, Banc 1.2.2 (jamais livré). **Rien n'a été testé en
jeu** (pas de serveur ici) : la fabrication, les contrôles et un démarrage d'essai sur GitHub seulement. Liste pour
changer de machine : `docs/DEPLOIEMENT.md`, section 10.

Ce qui a été mesuré (le 20/11, serveur en ligne et banc de charge) :
- Java du Ptero : 16 Go, réglages de la section 8 de `docs/DEPLOIEMENT.md` bien en place (sauf `-Dfile.encoding=UTF-8`),
  16 cœurs vus, machine de 62,8 Go. Mémoire vivante : 1 à 3 Go à vide, 3,5 Go au plus avec 100 bots (banc) : environ 5 Go
  attendus à 150 joueurs. 16 Go suffisent largement.
- Réseau (banc, 100 bots) : 19,5 Mbit/s en sortie (24 Ko/s par joueur, 24 000 paquets par seconde). À 150 joueurs : 35 à
  50 Mbit/s en moyenne, le double dans les pics, plus la voix.

Sacs : le contenu ne part plus chez les autres joueurs (Équipement 1.4.1)
Avant, le serveur envoyait le sac porté d'un joueur avec **tout son contenu** à tous ceux qui le voient : à chaque objet
rangé ou sorti, et à chaque fois qu'un joueur le voyait arriver (ou réapparaître derrière un mur). Un sac plein pèse
plusieurs Ko : du réseau pour rien, et un jeu modifié pouvait lire ce que chacun transporte. Maintenant, le porteur
reçoit toujours tout ; les autres seulement l'apparence du sac.
- [ ] Deux joueurs A et B. A porte un sac bien rempli. B s'approche : il voit le sac sur le dos de A, avec le bon modèle,
      la bonne couleur et le bon camo. A range et sort des objets : chez B, le sac ne change pas d'aspect et rien ne
      clignote.
- [ ] A survole son sac (case du dos, inventaire) : l'infobulle compte toujours ses objets ; sac non vide : le message
      « vider le sac avant de l'enlever » est toujours là. A ouvre son sac : tout son contenu est là.
- [ ] A meurt : son corps montre son sac ; à la fouille du corps, le survol du sac compte ses objets ; le contenu se
      récupère comme avant.
- [ ] Journal du démarrage du serveur : « [armurerie] contenu des sacs : envoyé seulement au porteur (actif) » ; puis,
      une fois des joueurs connectés avec un sac, une seule ligne « premier envoi allégé pour les autres joueurs ». Si on
      lit « paquets de Curios inattendus » : le dire (rien ne casse, mais l'allègement est coupé).
- [ ] En cas de souci : `contenu_porteur_seul = false` dans la section `[reseau]` de `config/heraliz_armory-common.toml`
      (relu tout seul en quelques secondes, sans redémarrer) : tout redevient comme avant.

Diagnostic (Noyau 1.1.8)
- [ ] `/diag etat` : la ligne « Java : … » finit par « ; processeur : <nom du processeur> ; encodage par défaut … ». Si
      l'encodage n'est pas « UTF-8 », la ligne le dit : demander à l'hébergeur d'ajouter `-Dfile.encoding=UTF-8`.
- [ ] Nouvelle ligne « Disque du serveur : … Go libres sur … Go » (avec « à surveiller » sous 10 %).
- [ ] Un rapport de blocage pendant la sauvegarde automatique dit « toutes les 10 min » (plus « 5 min »).

GitHub
- [ ] « Lenteurs du serveur » (Run workflow) : nouvelle partie « Mémoire et disque du serveur » dans le résumé : pauses
      du ramasse-miettes (nombre, moyenne, pire), mémoire vivante, place prise par dossier (seuls les noms de dossiers
      connus ; les autres : « autre dossier n° … »), nombre de fichiers de joueurs. Toujours en lecture seule.
- [ ] **Pseudos** (dépôt public : les journaux des tâches se lisent par tous) : avant ce correctif, « Lenteurs du serveur »
      et « Journaux du serveur » laissaient passer des pseudos dans certaines lignes (retour d'une commande du staff
      « [GRADE pseudo: …] », « Rejecting … from pseudo », lignes de la saison et de l'anticheat). Maintenant, tous les
      pseudos connus du serveur sont remplacés par *** partout (165 le 20/11 ; la liste n'est jamais affichée). Vérifier
      dans le journal d'une tâche : la ligne « pseudos à masquer : … », et aucun pseudo lisible.
- [ ] À faire par le propriétaire : supprimer les anciennes tâches « Lenteurs du serveur » et « Journaux du serveur »
      d'avant le 20/11 16 h 18 (onglet Actions, chaque tâche, « … » puis « Delete workflow run ») : leurs journaux
      peuvent contenir des pseudos. Celles de cet audit sont déjà supprimées.
- [ ] Prochain banc de charge : son rapport a une section « Réseau vers les bots, par sorte de paquet » (ce qui remplit
      la bande passante : tronçons, mouvements, sons et balles de TaCZ, paquets de nos mods).

Pas changé, à décider par le propriétaire (chiffres dans le compte rendu de l'audit) :
- distance de vue 6 au spawn (`vues_par_monde`) : environ 120 tronçons de moins envoyés à chaque mort et chaque retour au
  spawn (environ 40 % du réseau d'une arrivée) ;
- `entity-broadcast-range-percentage` à 80 (server.properties) : zombies vus à 48 blocs au lieu de 60, balles, véhicules
  et corps à 51 au lieu de 64 (les joueurs restent à 128) ; environ un tiers de paquets de mouvement en moins pour eux ;
- `rate-limit=1000` dans le `server.properties` du Ptero (toujours à 0 le 20/11 ; proposé le 19/11) ;
- `enable-query=false` (le launcher et le site ne s'en servent pas) ;
- `connection-throttle` de `bukkit.yml` à 1000 (une seule connexion par seconde et par adresse ; aujourd'hui sans
  limite) ;
- mondes qui ne sont plus chargés, dans `dayz_map/dimensions` : environ 600 Mo sur 1,2 Go de monde (`crac/bases` et deux
  vieilles copies `bases_ancien_…` des 16 et 21/10, `heralisz_matchs`, `heralisz_bases`) ; et plus de 8 Go de vieilles
  copies à la racine du Ptero, que le serveur ne lit pas (`dayz_map_ancien_2026-10-11_00h46` : 5,9 Go, `dayz_map_echec_…`,
  `tacz_backup`, `limitless_vehicle_backup`, `dynmap`, un dossier de 1,3 Go qui ne vient pas du serveur). Au
  déménagement : les garder dans une sauvegarde à part et ne pas les copier, ou tout copier (rien ne change en jeu). On
  ne touche jamais aux fichiers du monde sans vous ;
- `.bash_history` à la racine du Ptero (historique de commandes de 2025, peut contenir des mots de passe) : à supprimer.

## 38. Calcul du serveur à chaque tick : audit du 20/11 pour 150 joueurs

Demande du propriétaire : « relancer un audit complet : vérifier que tout soit ultra optimisé », avant le passage sur une
machine plus puissante et 150 joueurs. Cette partie : le **temps de calcul de chaque tick** du serveur (20 ticks par
seconde, 50 ms au plus chacun), mod par mod (images par seconde des joueurs : section 36 ; réseau, mémoire et disque :
section 37). **Rien de tout cela n'a été testé en jeu** : mesures sur le banc de charge (bots sur les machines de
GitHub, dans un monde généré : ni la vraie carte ni ses milliers de conteneurs) et lecture des rapports du serveur en
ligne. Versions : Noyau **1.1.9**, Quêtes **1.24.1**, Monde **1.3.8** (le launcher les donne aux joueurs) ; Banc 1.2.3
(jamais livré).

### Ce que disent les mesures

- **Serveur en ligne** (depuis la mise à jour du 20/11 à 12 h 22, heure française ; 0 ou 1 joueur) : trois rapports de
  blocage seulement : la première sauvegarde automatique après le démarrage (101 ms, attendu), la première connexion de
  la journée (692 ms : classes lues, LuckPerms, succès et liste des commandes) et ce même joueur qui avance sur la carte
  (342 ms : tronçons lus en attendant le disque, dont la relecture des « points d'intérêt », corrigée ci-dessous). Profil
  d'une minute à 16 h 44 : 0,8 ms par tick, rien de lourd dans nos mods sur la vraie carte. Depuis les correctifs du
  19/11, plus aucun rapport de `/tp` ni de ClashWar, mais presque personne n'a joué : ça ne prouve rien pour 150 joueurs,
  seul le banc le dit.
- **Banc à 150 bots, dans le même serveur** (essai A/B : les nouveaux réglages, puis coupés, comme dans la version en
  ligne) :

  | ms par tick (150 bots) | moyenne | 95 % | 99 % | ticks de plus de 50 ms en 5 min |
  |---|---|---|---|---|
  | comme la version en ligne | 33,7 | 43,6 | 69,6 | 158 |
  | avec les corrections | **27,8** | **37,4** | **59,0** | **114** |

  Soit **−5,9 ms par tick (−17 %)**, venus de Minecraft lui-même (le passage sur les tronçons autour des joueurs :
  5,9 → 1 ms ; la revue des tickets et le relevé des apparitions : 1,2 → 0 ms). Le gain est plutôt sous-estimé : dans
  ces essais, la mesure faite en second (ici « comme la version en ligne ») est un peu avantagée. À 100 bots, avec tout :
  **22,4 ms** en moyenne, 95 % à 29,3 ms, 99 % à 45 ms, aucun tick de plus de 100 ms : les trois objectifs (30 / 40 /
  50 ms) sont atteints. À 150 bots, la moyenne et le 95 % sont atteints, pas encore le 99 % (pointes des morts, des
  connexions et des écritures de tronçons).
- **Changer de monde** (mort, `/spawn`, extraction) coûte environ **moitié moins** au banc, grâce au point 4 ci-dessous
  (6 extractions d'un coup : 69 → 28 ms ; 10 `/spawn` d'un coup : 102 → 55 ms).
- **Attention aux chiffres d'un banc à l'autre** : la machine de GitHub change à chaque banc ; le même code a donné 27,8
  et 35,1 ms à 150 bots sur deux machines du même modèle. Seuls les essais A/B (deux mesures dans le même serveur)
  comparent vraiment. Pour mémoire (100 bots) : 20,8 ms le 12/11 (bots deux fois moins actifs), 33,7 ms le 19/11, 31,1 ms
  le 20/11 avant ces corrections, 22,4 ms après. Code final de la branche (avec les corrections des trois audits du 20/11),
  100 bots : 23,7 ms en moyenne (95 % à 35,8 ms) sur une EPYC 9V74, 27,3 ms (95 % à 38,0 ms) sur une machine plus lente
  (EPYC 7763) ; les compteurs du noyau montrent chaque correction en marche.

### Ce qui change

1. **Tronçons autour des joueurs « au repos »** (Noyau) : à chaque tick, Minecraft passait sur chacun des milliers de
   tronçons autour des joueurs (temps qu'y passent les joueurs, tics de blocs, foudre, glace, neige). Sur nos mondes,
   rien de tout cela ne peut arriver tant qu'il ne pleut pas (tics aléatoires à 0, pas d'apparitions de Minecraft, eau
   qui ne gèle pas) : chaque tronçon n'est alors vu qu'une fois par seconde, avec une seconde de temps d'un coup (même
   total : il ne sert qu'à la difficulté de l'endroit). Les blocs qui changent partent toujours aux joueurs **à chaque
   tick**. Dès qu'il pleut : comme avant. Réglage `troncons_tranches` (20 ; 1 = comme avant).
2. **Relevé des apparitions** (Noyau) : à chaque tick, Minecraft comptait toutes les créatures du monde et cherchait les
   joueurs proches de chaque monstre, pour des apparitions naturelles coupées ici (les zombies viennent du peuplement) :
   sauté. Réglage `apparitions_sans_releve`.
3. **Revue des tickets de chargement** (Noyau) : à chaque tick, Minecraft passait en revue les ~6 000 tickets de la carte
   (un par tronçon vu par un joueur) pour retirer ceux qui ont fini leur temps ; presque aucun n'en a. La revue ne se fait
   plus qu'au tick où un ticket peut finir (mêmes tickets retirés, au même tick : 94 % des revues sautées au banc).
   Réglage `tickets_a_echeance`.
4. **Permissions refusées sous Mohist** (Noyau, jar de Mohist intact) : à chaque changement de monde, la liste des
   commandes est renvoyée au joueur, et Mohist 916 relevait toute la pile d'appels pour chaque commande refusée (des
   centaines), pour un journal de débogage coupé. Plus relevée tant que ce journal est coupé (les builds de Mohist
   d'après juillet 2025 font pareil). Réglage `permissions_sans_pile`.
5. **Points d'intérêt sur la carte** (Noyau) : à la lecture de chaque tronçon, Minecraft relisait sur le disque, en
   attendant, les « points d'intérêt » (lits et postes des villageois, ruches, portails) : jusqu'à 26 ms pour un seul
   tronçon sur le Ptero le 20/11, 84 ms au banc. Aucun de nos mods ne s'en sert et la carte n'en a pas : sauté sur la
   carte (76 % des relectures au banc). Réglage `poi_sans_relecture` (liste des mondes ; vide = comme avant).
6. **PNJ des quêtes** (Quêtes) : les repères des nouveaux joueurs (lueur vers le guide puis Varga), le rappel « tape
   /spawn » et la présentation du Bâtisseur cherchaient leur PNJ parmi toutes les entités de la carte (1 500 à 150
   joueurs), plusieurs fois par seconde ; ils le cherchent maintenant parmi la dizaine de PNJ.
7. **Banc de charge** : essais A/B de la distance de vue et des réglages du noyau dans le même serveur, modèle du
   processeur dans le résumé, essai d'un autre build de Mohist (champ « mohist » : `ci` = le dernier).

Réglage ajouté **sans rien changer** tant que vous ne décidez pas : `xp` dans `heraliz_zombies-server.toml` (décision 3).

### À vérifier en jeu (rien de cela n'a été essayé)

- [ ] `/diag etat`, ligne « Tick (20/11) » : « tronçons au repos » monte vite, « comme Minecraft » reste petit (sauf
      pendant la pluie) ; « relevés des apparitions évités » et « revues des tickets sautées » montent ; « piles des
      permissions refusées évitées (Mohist) » monte après une mort ou un `/spawn`. Ligne « Lenteurs (19/11) » :
      « étages sans relecture » monte quand on se promène sur la carte.
- [ ] Les autres joueurs voient **tout de suite** ce qui change : blocs cassés et posés, portes et trappes, explosions de
      grenades, pose d'une base (CRac), lampes allumées et éteintes, conteneurs du loot qui apparaissent.
- [ ] Pendant la pluie (`/weather rain`, puis `/weather clear`) : rien ne change pour les joueurs ; « comme Minecraft »
      monte pendant la pluie, puis s'arrête.
- [ ] Zombies du peuplement, renforts et vagues des drapeaux : comme avant.
- [ ] Après une mort, un `/spawn`, une extraction : la complétion des commandes dans le chat montre les mêmes commandes
      qu'avant (seulement celles permises au joueur).
- [ ] Nouveau joueur : lueur vers le guide puis vers Varga, rappel « tape /spawn » et présentation du Bâtisseur comme
      avant.
- [ ] Se promener loin sur la carte : plus de rapport de blocage « SectionStorage.readColumn » ni
      « checkConsistencyWithBlocks ».

Si un souci apparaît : dans `serveur/defaultconfigs/heraliz_noyau-server.toml`, section `[perf]` (pris au redémarrage),
`troncons_tranches = 1`, `apparitions_sans_releve = false`, `tickets_a_echeance = false`, `permissions_sans_pile = false`,
`poi_sans_relecture = []` : chacun revient exactement à Minecraft (ou à Mohist) pour ce point seulement.

### Décisions pour vous

1. **Distance de vue** (aujourd'hui 8 dans `heraliz_distances-server.toml` ; pas changée). Mesuré à 150 bots, avec les
   corrections, chaque essai dans un même serveur :

   | essai (150 bots) | avant | après | écart |
   |---|---|---|---|
   | 8 → 7 | 35,1 ms | 30,0 ms | −5,1 ms (−15 %) |
   | 8 → 6 | 36,7 ms | 32,7 ms | −4,0 ms (−11 %) |
   | 7 → 6 | 38,9 ms | 34,2 ms | −4,7 ms (−12 %) |

   Ces trois essais se recoupent mal (la mesure faite en second est un peu avantagée) ; d'après le détail des profils,
   compter **environ 3 ms par cran** à 150 joueurs (envoi des tronçons, suivi des entités, ticks de l'eau), et moins de
   travail sur les autres cœurs (−6 points de processeur). Les tronçons gardés en mémoire baissent peu (ceux du bord de la
   vue restent chargés à moitié) ; ce qui part sur le réseau à chaque arrivée baisse beaucoup (section 37). Avec les
   corrections, 150 bots à 8 font 28 à 37 ms en moyenne selon la machine de GitHub (objectif : 30) : **7 donne de la
   marge** pour les soirs chargés (les joueurs voient à 112 blocs au lieu de 128) ; 6 en donne encore un peu (96 blocs).
   Une machine plus rapide par cœur (section 10 de `docs/DEPLOIEMENT.md`) en donne aussi, sans rien changer pour les
   joueurs.
2. **Mise à jour de Mohist** (916 aujourd'hui ; le dernier est le **995**, du 12/11/2026). Son seul gain connu pour le
   tick (la pile relevée aux permissions refusées) est maintenant obtenu sur le 916 (point 4). Au banc (100 bots, même
   code, champ « mohist »), le 995 démarre et fait tourner tous nos mods et plugins **sans aucune erreur** (mêmes
   avertissements au démarrage que le 916, même mémoire) : 28,9 ms en moyenne, 95 % à 39,6 ms, 99 % à 55,9 ms, aucun
   tick de plus de 100 ms ; le 916 avec le même code : 27,3 ms, 38,0 et 56,2 ms. Les deux bancs ont tourné sur des
   machines différentes : pas de gain visible, pas de perte non plus. Conseil : **pas avant le déménagement** (une seule
   chose à la fois, section 10 de `docs/DEPLOIEMENT.md`) ; plus tard, si vous le voulez, après un démarrage d'essai
   (nouveau jar dans la Release « base-serveur », son empreinte, donnée par le site de Mohist, dans
   `serveur/mohist.sha256`), puis en surveillant WorldEdit, les plugins et la voix les premiers jours.
3. **Expérience des zombies** : un zombie tué lâche 2 ou 3 orbes d'expérience, qui vivent jusqu'à 5 minutes : ~100 orbes
   en même temps au banc à 150 joueurs, **0,8 ms par tick** (3 %). L'expérience ne sert qu'à la barre d'expérience.
   `xp = false` dans `heraliz_zombies-server.toml` les coupe (aujourd'hui `true`, comme avant).

### Ce qui reste, le plus lourd d'abord (banc à 150 bots, après les corrections)

1. Le tick des joueurs eux-mêmes (déplacements, collisions, eau, armes TaCZ, Curios) : ~7,5 ms, à peu près fixe par
   joueur : c'est la vitesse d'**un** cœur du processeur qui compte (section 10 de `docs/DEPLOIEMENT.md`).
2. Suivi des entités par les joueurs et envoi de leurs mouvements : ~3 à 4 ms (allégé le 01/11).
3. Écriture des tronçons quittés ou changés, sur le fil du serveur : ~2,5 ms. Au banc, des tronçons sont réécrits sans
   cesse (dans le tutoriel des bases, ~60 par seconde pour 15 joueurs) : la cause reste à trouver (un compteur à ajouter).
4. Entités : orbes d'expérience 0,8 ms (décision 3), balles de TaCZ 0,7 ms (55 µs chacune), zombies 0,5 ms.
5. Ticks programmés des blocs (eau, lave) : ~1 ms au banc (monde généré ; inconnu sur la vraie carte).
6. Anticheat : 0,8 ms au banc, mais les bots n'y sont pas observateurs : avec 150 vrais joueurs l'anti-ESP aura plus de
   paires à suivre (estimation : 1 à 2 ms ; ses lignes de vue sont déjà calculées sur d'autres cœurs). Ses contrôles du
   19/11 (cadence des tirs, ouvertures à travers les murs, visée forcée) ne coûtent qu'au tir ou au clic : quelques
   microsecondes chacun, rien à chaque tick.
7. Pointes : un groupe qui change de monde d'un coup (6 extractions, 10 `/spawn`) coûte encore 75 à 380 ms au banc
   (tronçons de l'arrivée, sac et Curios renvoyés) ; une mort ou une connexion seule, 50 à 130 ms.

Pas fait, et pourquoi : la boucle des tronçons « au repos » regarde encore chaque tronçon une fois par tick (~0,5 ms à
150 bots ; une liste par tranche ferait mieux, mais un oubli retarderait l'envoi d'un bloc changé) ; les ticks programmés
des blocs (gain incertain sur la vraie carte) ; le tick des joueurs et les balles de TaCZ (code d'autres auteurs) ; **aucun
profil avec des joueurs sur le serveur en ligne** (personne pendant l'audit) : à lancer un soir chargé (« Lenteurs du
serveur », case profil, 60 s).

## 39. Décisions du propriétaire après l'audit du 20/11 (appliquées)

Choix du 20/11 : vue 6 au spawn (la carte reste à 8), armes des autres joueurs allégées de loin, terrain adouci au loin,
zombies sans boules d'expérience, zombies et petits objets suivis d'un peu moins loin. Interface 2.0.16 (le launcher la
donne). **Rien n'a été testé en jeu.**

- [ ] Spawn : on voit 6 tronçons autour de soi (96 blocs) ; sur la carte, toujours 8 (128 blocs). Mort, `/spawn` et
      retour de la carte plus rapides à charger.
- [ ] Armes des autres joueurs (`outils/lod_armes.py --installer`, 66 versions allégées, 123 fiches) : à 30 et 60 blocs,
      aucune différence ; à 10 blocs, quelques points en moins ; de près en 3e personne (F5), les petits détails (vis,
      rainures) disparaissent : c'est voulu. Sa propre arme en 1re personne ne change pas. Images :
      `maquettes/fps_20nov/armes_lod_*.png`.
- [ ] Terrain au loin (`outils/mipmaps.py --corriger` : 3 images du mod de meubles retaillées dans notre pack) : le sol ne
      scintille plus en marchant ; il est plus doux et un peu plus flou au loin (profil Performance : 2 niveaux, Équilibré
      et Qualité : 4). À surveiller : des traits entre les blocs au loin (comme le 18/11 avec le filtrage anisotrope) ;
      dans le journal du jeu, plus de ligne « limits mip level ». Images : `maquettes/fps_20nov/mipmaps_*`.
- [ ] Zombies tués : plus aucune boule d'expérience (`[zombies] xp = false` de heraliz_zombies-server.toml).
- [ ] Portées de suivi de `spigot.yml` x 0,8 (même effet que `entity-broadcast-range-percentage=80`, que la mise en ligne
      ne peut pas poser) : zombies vus à 48 blocs au lieu de 60, balles, véhicules et corps à 51 au lieu de 64, objets au
      sol à 13 au lieu de 16, animaux à 40, hologrammes et affichages à 48 ; les joueurs ne changent pas (128, limité par la
      vue). Un zombie apparaît donc un peu plus près : à vérifier qu'il ne « surgit » pas trop près en forêt.

## 40. Liste Tab, tags de clan et liste vocale : seulement ce qui change (21/11)

Décision du propriétaire après l'audit réseau du 20/11 (section 37). Trois listes partaient **complètes, à tous les
joueurs**, à chaque arrivée, départ, changement de grade, de tag de clan ou de surnom : la liste Tab (grade, tag de clan
et surnom de chaque joueur connecté), la table des tags de clan et la liste vocale (micro coupé, voix coupée, groupes de
voix). Maintenant, celui qui arrive reçoit les listes complètes (pour lui seul) et les autres seulement ce qui change
(« ce joueur ajouté », « celui-là retiré », « son grade a changé »). Versions : Boutique **1.7.0** (Grades 2.11.0), Clans
**3.19.0**, Voix **1.7.0**, données par le launcher ; **un jeu pas à jour est refusé à la connexion** (normal : le
launcher met les jars à jour). Banc 1.2.4 (jamais livré). **Rien n'a été testé en jeu** : la fabrication, les contrôles,
un essai hors du jeu, le banc de charge et un démarrage d'essai sur GitHub seulement.

Ce que ça change, octets réellement envoyés (compression comprise) :

| à 150 joueurs | avant | après |
|---|---|---|
| une arrivée | 1,35 Mo | 33 Ko |
| un départ | 1,33 Mo | 19 Ko |
| un grade changé | 0,56 Mo | 10 Ko |
| un staff qui se cache | 0,56 Mo | 7 Ko |
| tag d'un clan changé (14 membres en ligne) | 0,92 Mo | 117 Ko |
| vague de 150 reconnexions après un redémarrage | 70,5 Mo | 2,3 Mo |

À 50 joueurs : une arrivée 170 Ko → 11 Ko, la vague 3 Mo → 0,3 Mo ; à 100 joueurs : 625 Ko → 22 Ko, la vague 22 Mo
→ 1 Mo. Calcul fait sur les paquets eux-mêmes, avec des joueurs réalistes (75 % dans un clan, quelques surnoms et
grades de la boutique) : `python outils/verif_synchro.py --tailles`.

Banc de charge (150 bots, une arrivée ou un départ toutes les 5 s environ pendant 5 min ; les bots n'ont ni clan, ni
grade, ni surnom : leurs lignes sont plus petites que celles de vrais joueurs) :

| pour une arrivée ou un départ | avant | après |
|---|---|---|
| liste Tab | 213 listes complètes, 565 Ko | 0,5 liste complète (celui qui arrive) + 148 différences de 50 octets, 9 Ko |
| liste vocale | 150 listes complètes, 411 Ko | 0,5 liste complète + 148 différences de 39 octets, 7 Ko |
| total | **976 Ko** | **16 Ko** (60 fois moins) |

Avant, chaque mort renvoyait aussi la liste Tab complète à tout le monde (le joueur qui réapparaît change de place dans
la liste de Minecraft) : c'est fini, rien ne part si rien n'a changé. Le second banc est tombé sur une machine de GitHub
plus lente (EPYC 7763 au lieu d'un Xeon 6975P-C) : tous ses temps de tick sont environ 1,8 fois plus longs, mais la part
de chaque mod est la même, et nos trois mods restent sous 0,2 ms par tick.

Comment ça marche (pour comprendre ce qu'on voit) :
- chaque liste a un numéro ; chaque changement envoyé porte le numéro suivant. Un jeu qui voit un numéro sauté (un
  paquet perdu, ce qui ne doit jamais arriver) ignore la suite et redemande la liste complète au serveur dès le
  changement suivant, au plus une fois toutes les 5 s : une liste fausse ne reste pas ;
- les changements d'un même tick partent ensemble, à la fin du tick ; le contrôle du tab toutes les 2 s relit tout
  (grade qui finit, tag de clan…) mais n'envoie que ce qui a changé ; rien ne part si rien ne change ;
- joueur caché (vanish) : rien ne change pour le joueur, le staff le voit toujours « (caché) », les autres ne le
  reçoivent jamais (ni à son arrivée, ni quand il se cache : il leur est seulement « retiré ») ;
- à l'arrivée d'un membre d'un clan, la liste Tab des autres membres n'est plus renvoyée (elle ne change pas).

À vérifier en jeu (au moins 3 joueurs A, B et C, et un membre du staff S) :
- [ ] Tab tenu, chez chacun : tout le monde est là, avec le bon grade, le bon tag de clan (bonne couleur) et le bon
      surnom ; l'icône de voix de chacun est la bonne.
- [ ] C se connecte : il apparaît chez A et B avec son grade et son tag en moins d'une seconde ; chez C, la liste est
      complète dès l'arrivée.
- [ ] C se déconnecte : il disparaît chez A et B ; il revient : il réapparaît, avec son grade et son tag.
- [ ] Le grade de B change (panel du staff, `/grade`, grade de la boutique qui commence ou finit) : chez A et C, son grade
      suit dans les 2 s.
- [ ] B prend un surnom (`/avantages nick`), puis le retire : le tab de A suit.
- [ ] Clans : B rejoint un clan, le quitte, est exclu ; le clan change de tag ou de bannière ; le clan est dissous : le
      tag de B dans le tab de A suit dans les 2 s.
- [ ] Vanish : S se cache (« Se cacher » du panel) : il disparaît du tab de A et B, un autre staff le voit « (caché) » ;
      S se montre : il revient chez tous. S caché se déconnecte puis revient : A et B ne le voient pas dans le tab
      (comme avant). Un staff qui se connecte voit les cachés seulement après son `/login` (comme avant).
- [ ] Voix : A parle, B l'entend ; C arrive et parle aussitôt : A l'entend, sous le bon nom ; micro coupé de B : l'icône
      suit chez A ; groupes de voix (créer, rejoindre, quitter) : la liste des groupes suit chez les autres.
- [ ] Après un redémarrage, quand tout le monde revient : tab, tags et voix justes chez tous.
- [ ] Journal du serveur : aucune erreur de `heraliz_grades`, `heralisz_clans` ou `caracrea` à l'arrivée des joueurs.
- [ ] Un jeu pas à jour (ancien jar) est refusé à la connexion : normal, comme à chaque changement de protocole.

En cas de souci (une ligne fausse dans le tab) : se déconnecter et revenir répare tout (liste complète à l'arrivée). Le
dire avec l'heure et ce qui venait de se passer (arrivée, grade, vanish…).

Contrôle à chaque fabrication (GitHub compris) : `outils/verifier.py` lance `outils/verif_synchro.py`, qui vérifie que
les trois copies de la classe `Synchro` (Grades, Clans, CaraCrea) sont identiques et fait tourner un essai hors du jeu :
16 000 ticks d'arrivées, départs, grades, tags, joueurs cachés et staff, sans perte puis avec 2 % et 20 % de paquets
perdus. Sans perte, chaque liste est toujours juste ; jamais une ligne cachée hors du staff ; avec des pertes, tout se
répare.

Pas changé (à décider par le propriétaire) : la table des tags de clan et la liste vocale contiennent toujours un staff
caché, comme avant (rien à l'écran, mais un jeu modifié pourrait voir qu'il est connecté). Les retirer pour les joueurs
hors du staff est possible ensuite (même méthode que le tab).
Fait le 21/11 (décision du propriétaire) : section 47.

## 41. Audit du 21/11 : clans, boutique, PNJ, interface, voix, bases, emotes (relecture du code)

Demande du propriétaire : « refais un audit complet […] que tout soit clean, sans aucun bug ». Cette partie : relecture
du code des clans (profil, hérals, saison, territoires), de la boutique (grades, casino, caisses, `/money`), des PNJ
(quêtes, marchands, achat avec quantité, Passeur, recycleurs, bourses), de l'interface, de la voix, des bases et des
emotes, surtout ce qui a changé depuis le 17/11. Versions : Quêtes **1.24.2**, CRac **1.38.8-beta** (le launcher les
donne ; rien ne change entre le jeu et le serveur). **Rien n'a été testé en jeu** : la fabrication et les contrôles sur
GitHub seulement. Les deux corrections ne se voient pas en jouant : elles servent en cas de plantage ou de disque plein.

Corrigé :
- **Progression des quêtes de Varga** : si le disque du serveur était plein au moment d'enregistrer la progression d'un
  joueur, un fichier à moitié écrit prenait la place du bon. À la connexion suivante, le fichier illisible était mis de
  côté et le joueur repartait de zéro (quêtes à refaire, et leurs récompenses à toucher une deuxième fois). Maintenant
  le bon fichier reste, l'erreur est notée dans le journal du serveur et la progression est réenregistrée 30 s plus tard
  (et à l'arrêt).
- **Objets à vie rendus par une base** (coffre détruit, base effacée, inventaire plein) : le fichier qui les garde doit
  être enregistré AVANT le joueur. Depuis le 19/11 (écritures en fond du noyau), le joueur était enregistré le premier :
  un plantage juste à ce moment pouvait rendre l'objet à vie une deuxième fois. Même chose pour le matériel de la
  première base rangé dans le coffre du clan : il pouvait être rangé deux fois. Les deux fichiers sont de nouveau écrits
  tout de suite, dans le bon ordre (au pire un plantage perd le matériel, il ne le copie jamais).

À vérifier (comme avant, rien de nouveau à l'écran) :
- [ ] Quêtes : finir une étape, se déconnecter, se reconnecter : la progression est là. `/diag etat` : aucune ligne
      « écriture … impossible » de la voie « quetes ».
- [ ] Objets à vie : un objet à vie dans un coffre d'une base, `/base effacer` (ou casser le coffre en créatif), inventaire
      plein puis une case libre : l'objet revient une seule fois ; après un redémarrage, il n'est pas rendu de nouveau.
- [ ] Nouveau clan : après 30 s, le matériel de la première base est dans le coffre du clan, une seule fois (aussi après
      un redémarrage).

Relu sans rien à corriger (rien n'a changé) : profil et `/money` (compteurs, droits, ce qu'on voit d'un autre joueur),
bourses de hérals (registre, revente à Mila, recycleur de la carte : aucune copie possible), panneau d'achat avec la
quantité (prix et place revérifiés par le serveur), quitter le tutoriel, banque du clan, `/herals payer`, sauvegarde
des clans par morceaux, paquets reçus des joueurs (tous traités par le serveur, bornés), textes français, anglais et
russe (tous présents), listes gardées en mémoire (vidées au départ des joueurs).

Remarques, à décider par le propriétaire (rien n'est changé) :
- Panneau d'achat : si le staff retire un article d'un marchand au Registre pendant qu'un joueur a le panneau ouvert,
  l'article suivant prend sa place ; s'il a le même prix (deux armes à 1 125 chez le Passeur), la confirmation achète
  l'autre article. Très rare (le staff modifie pendant un achat). Correctif possible : le serveur vérifie aussi l'objet.
- `/tpa` : depuis le 19/11, le départ attend que l'arrivée soit chargée (presque toujours tout de suite, 3 s au plus). Ce
  qui est revu au départ : seulement que la cible est toujours connectée ; un joueur touché pendant cette attente part
  quand même. Très rare (l'arrivée autour d'un joueur est déjà chargée).
- Conversion des anciennes clés de la planque (une seule fois par joueur) : la planque et le compte de clés sont écrits
  sur deux fils différents ; un plantage juste à ce moment peut doubler ces clés. Très rare, une fois par joueur.

## 42. Audit du 21/11 : duplications et argent

Demande du propriétaire : « que tout soit clean, sans aucun bug ». Cette partie : tout ce qui crée, déplace ou détruit
des objets ou de l'argent (hérals, bourses, capsules, clés), surtout ce qui a changé depuis le 19/11 (achat avec la
quantité, Passeur, recycleurs, sacs, bourses, `/money`, dépôts du ClashWar écrits en fond, sauvegardes étalées).
Versions : ClashWar **1.3.4**, Quêtes **1.24.3** (rien ne change entre le jeu et le serveur ; rien de nouveau à l'écran).
**Rien n'a été testé en jeu** : la fabrication et les contrôles sur GitHub seulement.

Corrigé :
- **Inventaire mis de côté par le ClashWar** (inscrits au départ d'un tournoi, et `/clashwar regarder`) : au moment de
  vider l'inventaire, le serveur vérifie qu'il correspond exactement à ce qui a été mis de côté ; sinon rien n'est vidé
  et le dépôt est refait (au plus 5 fois, puis refusé avec le message habituel, inventaire intact). La marque du dépôt
  n'est plus posée qu'à ce moment-là.
- **Marchands, Mila, échanges, recycleur du spawn, quêtes du jour et étape « donner » de Varga** : refusés tant que
  l'inventaire est mis de côté par le ClashWar (même message qu'en match).

À vérifier en jeu (un op pour lancer un ClashWar, 4 comptes) :
- [ ] Départ d'un tournoi, inventaires bien remplis (armes, sac porté, objet tenu à la souris au moment du départ) :
      chacun entre en salle ; au retour, l'inventaire est exactement celui d'avant, ni en double ni en moins.
- [ ] `/clashwar regarder`, puis `/clashwar quitter`, plusieurs fois de suite : même chose à chaque retour.
- [ ] Journal du serveur au départ : normalement aucune ligne « inventaire change pendant l'ecriture ». S'il y en a pour
      des joueurs qui ne touchaient à rien, le dire (un objet qui change tout seul dans l'inventaire ferait attendre).
- [ ] Hors ClashWar : Mila, les marchands, le Passeur, les échanges, le recycleur du spawn, Varga et les quêtes du jour
      marchent comme avant.

Contrôles de l'économie (passés le 21/11, sans et avec la bêta) : `python outils/marchands.py --controle` (aucune revente
ni aucun recyclage au-dessus de 50 % du prix, Passeur sous 10 % pour une arme et 20 % pour des balles),
`python outils/recyclage.py --verifier` (recycleur un peu sous les prix de Mila).

Relu sans rien à corriger : achat avec la quantité (quantité 1 à 2 304, prix revérifié, place vérifiée avant de payer,
prix 0 refusé), revente à Mila (cases dédoublonnées), échanges (calcul sur une copie), bourses de hérals (registre :
une copie ne paie jamais), recycleur de la carte (contenu partagé, chaque clic écrit dans l'ordre), hôtel des ventes,
compte de capsules, caisses et clés, sacs pleins refusés partout où ils seraient détruits, contenu des sacs caché aux
autres joueurs (copie, jamais l'objet du serveur), refus de l'anticheat, `/money` et `/profil` (lecture seule).

Pas corrigé (seulement en cas de plantage du serveur à un instant précis ; à décider) :
- objets et capsules en attente (caisses, casino) : leur fichier n'est pas écrit au moment même de la remise (en fond
  depuis le 19/11 pour les caisses, à la sauvegarde automatique pour le casino) ; un plantage juste après une remise
  pourrait la redonner ;
- sauvegarde automatique étalée sur 5 s (joueurs, entités) : un objet sorti d'un corps ou d'un conteneur de loot pendant
  ces 5 s pourrait revenir après un plantage avant la sauvegarde suivante ;
- coffre du clan et planque (déjà noté le 19/11) : un objet sorti juste avant un plantage peut revenir.

## 43. Audit du 21/11 : armes, équipement, sacs, anticheat, launcher, outils et tâches GitHub (relecture du code)

Corrigé :
- **Armes vues de loin (versions allégées du 20/11)** : un contrôle vérifie maintenant, à chaque fabrication (GitHub
  compris), que chaque arme a sa version allégée et qu'elle suit le vrai modèle de l'arme. Avant, une arme mise à jour
  dans un gunpack gardait pour toujours la version allégée de l'ancien modèle (pièces mal placées chez les autres
  joueurs), sans aucune alerte, et `--installer` ne la refaisait jamais. Si le contrôle se plaint un jour :
  `python outils/lod_armes.py --installer`. Rien ne change dans le jeu aujourd'hui : tout était déjà à jour.

Vérifié le 21/11, sans rien à corriger :
- les 123 armes reliées à une version allégée : chaque fichier existe, avec sa texture ; les accessoires (viseurs,
  silencieux) restent dessinés sur la version allégée (d'après le code de TaCZ : mêmes repères) ;
- les 79 programmes des camos et effets (Diamant compris) se compilent avec un vrai compilateur OpenGL ;
- les outils qui écrivent des fichiers (loot des armes et des zombies, caisses, marchands, recycleur, Table d'armurier,
  mipmaps, grande texture des blocs, versions allégées) : relancés, leurs fichiers étaient déjà à jour ; tous leurs
  contrôles passent, y compris ceux que GitHub saute (icônes, chargeurs 3D, accessoires en main, placement des
  accessoires) ;
- sacs portés : leur contenu ne part plus qu'au porteur (vérifié dans le jar de Curios : les bons envois sont visés) ;
- anticheat : les nouvelles règles visent bien les méthodes de TaCZ 1.1.8 (vérifié dans son jar) ; relu sans trouver de
  cas où un joueur honnête serait expulsé (packs de textures, bibliothèques d'agent, clés du Gardien, ouvertures à
  travers les murs, tirs) ;
- launcher 1.18.1 : 63 tests passés (Gardien, intégrité, Spartan, effacement, signature, sécurité).

À vérifier en jeu (2 joueurs A et B) :
- [ ] A regarde B tenir plusieurs armes (fusil avec viseur et silencieux, pistolet, couteau), de près puis à 20 blocs :
      l'arme est bien dans la main, les accessoires sont à leur place, rien ne flotte.

À décider (propositions, rien n'a été changé) :
1. **Règles « en rodage » de l'anticheat** : si 3 comptes différents déclenchent la même règle en une heure, elle se
   coupe pour tout le serveur jusqu'au redémarrage. Un tricheur avec 3 comptes pourrait la couper exprès. Proposition :
   compter des PC différents plutôt que des comptes, et vider la liste « rodage » après une semaine sans « Règle
   suspendue » (comme prévu).
2. **Tirs étalés par l'anticheat** (très rare chez un joueur honnête) : le tir mis de côté part dans la direction où le
   joueur vise au moment où il est rejoué, et le tir normal qui suit peut être refusé une fois par TaCZ. Effet minime ;
   à revoir si des joueurs parlent de « balles perdues ».
3. **Contrôles sautés sur GitHub** (icônes, chargeurs 3D, accessoires en main, placement des accessoires) : il leur
   manque deux bibliothèques (numpy et Pillow). Les installer dans les tâches GitHub ajouterait environ une minute par
   fabrication.
4. **Dépôt public** : les tâches « Lenteurs » et « Journaux » ne masquent pas les adresses IPv6, « Lenteurs » écrit les
   noms des fichiers de la racine du serveur, et « Enquête Spartan » écrit le pseudo du joueur contrôlé dans son journal.
   Proposition : les masquer aussi.

Pas testé en jeu : rien de cette section (relecture du code et outils seulement).

## 44. Audit du 21/11 : noyau, monde, loot, événements, ClashWar, réanimation, bunker (relecture du code)

Dernière relecture demandée par le propriétaire, sur ce qui a changé depuis le 17/11 dans ces modules : écritures en fond
du noyau, arrivées préchargées et `/tp`, tronçons « au repos », tickets, sauvegardes étalées, fichiers `data/` en fond,
mixins du 19 et du 20/11, Coffre du ClashWar, loot (C6, C7, conteneurs d'événement), hélicoptère et épave, plafond des
zombies, repères des recycleurs, portes de bunker et salles à carte. Versions : Monde **1.3.9**, ClashWar **1.3.6** (le
launcher les donne).
**Rien n'a été testé en jeu.**

Contrôle fait hors du jeu : chaque mixin du noyau, du monde, du loot, de la réanimation et du bunker a été comparé aux
vraies classes de Mohist 916 (celles que le serveur charge) : toutes les méthodes visées et tous les appels visés
existent. Seules exceptions, attendues : la 2e forme des réglages de génération (prévue facultative) et les mixins qui
visent des mods d'autres auteurs (TaCZ, véhicules, Curios…, vérifiés au démarrage d'essai). Aucun réglage `[perf]` ne
« rate » donc en silence sur le serveur en ligne.

Corrigé :
- **Roquettes de l'hélicoptère** (Monde 1.3.9) : une roquette qui entrait dans un morceau de carte encore en lecture
  (bord de la vue d'un joueur qui avance) faisait attendre cette lecture par tout le serveur (petit gel possible pendant
  un combat contre l'hélicoptère). Elle s'arrête maintenant là, comme au bord de la carte chargée.
- **Coffre du ClashWar** (ClashWar 1.3.6, complète la section 42) : la vérification au moment du vidage compte aussi les
  objets rangés **dans le sac** (porté ou dans l'inventaire). Avant, un jeu modifié qui, pendant les quelques ticks de
  l'écriture, sortait des objets du sac puis les rangeait ailleurs (coffre du clan, marchand) laissait chaque case et
  chaque Curios identiques : ces objets revenaient en double avec le dépôt. Maintenant le dépôt est refait, comme pour
  le reste de l'inventaire.

À vérifier en jeu :
- [ ] Hélicoptère : ses salves de roquettes touchent et explosent comme avant ; aucune alerte « [Diag] Blocage » pendant
      un combat contre lui.
- [ ] ClashWar avec un sac bien rempli sur le dos : départ et retour, le sac revient avec exactement son contenu ; dans le
      journal, pas de « inventaire change pendant l'ecriture » pour un joueur qui ne touchait à rien.

Déjà corrigé par l'audit des duplications (section 42), confirmé par cette relecture : le dépôt du ClashWar pendant son
écriture en fond (écran déjà ouvert : marchand, coffre du clan, distributeur de capsules).

Proposé (pas fait, à décider) :
- Écrivain en fond du noyau : 2 fils pour tous les modules. Pendant la sauvegarde automatique, une opération qui doit
  attendre son fichier (argent des clans, quête payée, numéro d'événement) peut attendre derrière les gros fichiers des
  autres (loot, bases, `data/`) : quelques dixièmes de seconde au plus, rarement. À décider : 3 ou 4 fils, ou chaque
  module à son tour, un fichier à la fois.
- Petits : `[perf] arrivees_prechargees = false` laisse encore les tickets de préchargement (sans effet gênant) ; la
  borne de départ ne voit les conteneurs de loot posés en jeu qu'après un redémarrage.

## 45. Audit du 21/11 : textes, données, réglages, commandes et démarrage du serveur

Dernière vérification demandée par le propriétaire (« que tout soit clean, sans aucun bug ») sur ce qui n'est pas du
code mais casse le jeu si c'est faux : textes en trois langues, images et sons cités, objets cités par le loot, les
marchands, les quêtes et les caisses, raretés, commandes, réglages, démarrage du serveur. Versions : Loot **0.20.7**,
Noyau **1.1.10**, Interface **2.0.17** (avec ClashWar, voir plus bas ; le launcher les donne). **Rien n'a été testé en
jeu** : contrôles hors du jeu, fabrication et démarrage d'essai sur GitHub seulement.

Vérifié, rien à corriger :
- **Textes** : les trois langues ont les mêmes textes et les mêmes paramètres ; aucun texte anglais resté en français,
  aucun russe resté en anglais (seuls des noms propres : ClashWar, Discord, Pepsi…). Les textes russes plus longs (onglets,
  pastilles de l'Atelier) sont coupés proprement par les écrans (« +2 », infobulle).
- **Objets cités par les données** (loot, marchands, quêtes, caisses, recycleur, raretés, recettes, établis) : tous
  existent, armes, accessoires et munitions des gunpacks compris. Tous les générateurs (`loot_armes.py`,
  `marchands.py`, `caisses.py`, `recyclage.py`, `quetes_journalieres.py`, `loot_zombies.py`, `crafts.py`,
  `equilibrage_armes.py`, langues des PNJ…) relancés : ils réécrivent exactement les fichiers du dépôt (personne n'a
  retouché un fichier à la main). Tous les contrôles d'économie passent.
- **Images et modèles** : tous les modèles et images cités par nos blocs et objets existent ; mipmaps et grande texture
  des blocs bons (94 % de sa taille maximale : une grosse image de bloc en plus peut la faire déborder, le contrôle le
  dirait) ; armes allégées de loin à jour.
- **Réglages** : au démarrage d'essai, Forge n'a refusé **aucune** valeur (pas de valeur hors bornes ni du mauvais type)
  et aucun réglage du dépôt n'est un réglage disparu. Les fichiers « corrigés » au démarrage le sont seulement parce
  qu'ils ont nos longs commentaires (Forge remet les siens) ou parce que des réglages y manquent exprès (le code garde
  alors sa valeur par défaut : loot, spawn, véhicules, armures).
- **Commandes permises sur la carte et en combat** : chacune existe. **Écran Informations** : les commandes et touches
  qu'il montre existent (K pour le clan, J pour les quêtes).

Corrigé :
- **Couteaux** : une mise à mort au couteau (M9, Karambit, Butterfly, couteaux CT et T) ne faisait aucun son : leurs 61
  fiches citaient un son qui n'existe pas. Elles citent maintenant le son de coup de couteau du pack. Le launcher
  redonne les gunpacks à la prochaine mise en ligne.
- **`/recharger loot`** : son message n'avait de texte dans aucune langue (le staff anglais ou russe le lisait en
  français). Textes ajoutés (Loot 0.20.7).
- **Écran d'inscription du ClashWar** : son titre lu par le narrateur était « ClashWar #%s » (texte cassé) ; il a son
  numéro d'édition (livré avec ClashWar 1.3.6).
- **`/sethome`** : la liste des commandes permises sur la carte écrite dans le code ne l'avait pas (seul le fichier de
  réglages l'avait) : sans ce fichier, `/sethome` aurait été refusée sur la carte. Ajoutée (Noyau 1.1.10). L'inventaire
  des commandes la compte enfin (87 commandes) et ne dit plus « tous » pour celles que le mod réserve au staff par sa
  propre règle (`/zonevip`, `/grade`, `/bon`, `/staff`, `/warp set`…) : vérifié, un joueur sans droit y est bien refusé ;
  `docs/COMMANDES.md` a aussi `/aide`, `/bourse`, `/guide`, `/cycle` et `/recolte`, qui y manquaient.
- **Commentaires faux** : distance de simulation (le réglage disait « 5 », c'est 4 sur le Ptero) et `ARCHITECTURE.md`.
- **Hautes herbes du pack « herbe »** : sur 7 701 fichiers de données relus, un seul était mal écrit (une accolade de
  trop). Le jeu le lisait quand même ; corrigé, rien ne change à l'écran (Interface 2.0.17).
- **Contrôles ajoutés** (ils tournent sur GitHub à chaque envoi) : chaque texte cité par le code de TOUS les modules doit
  exister (avant : seulement CRac et les Quêtes) et recevoir assez de valeurs ; le démarrage d'essai montre maintenant
  aussi les avertissements du serveur et, clé par clé, chaque réglage que Forge corrige (un réglage ignoré se voit).

Lignes d'erreur du démarrage d'essai, toutes connues et sans danger :
- 8 « Not found model file: ywzj_vehicle:… » : le mod des véhicules (autre auteur) cherche ses modèles côté serveur ;
  ils sont bien dans le jar et s'affichent chez les joueurs.
- 4 « face / ear_protection is not a registered slot type » : fichiers de cases Curios en trop (GeckoLib d'un autre
  auteur et nos sacs) pour des cases qui n'existent pas ; ignorés. Les cases des joueurs restent celles de HeraliZ.
- 1 « No key layers » : vient du monde plat de l'essai, pas du serveur en ligne.
- Avertissements : mixins d'autres mods pour des mods absents (Create) ou pour le jeu des joueurs, mode hors ligne
  (AuthMe), plugins ; et deux qui ne viennent que du monde neuf de l'essai (quête « La boîte noire » sans zone de
  radiation, bordure du monde pour la récolte) : le journal du serveur en ligne ne les a pas.

À vérifier en jeu :
- [ ] Tuer un zombie ou un joueur avec un couteau (M9, Karambit, Butterfly, CT, T) : on entend le coup de couteau.
- [ ] `/recharger loot` (staff) : le message « N tables de loot relues. » s'affiche dans la langue du jeu.
- [ ] `/sethome` sur la carte (droit « Gérer la base ») : acceptée comme avant.

Proposé (pas fait, à décider) :
- **Couleurs des raretés des caisses** : l'écran des caisses a ses propres teintes (gris, vert, bleu, violet et or un peu
  différents de ceux des objets) ; seul « Unique » a été aligné le 14/11. Les aligner sur celles du loot : rendus à
  valider d'abord (règle des visuels).
- **MAtmos et TCTcore sur le serveur** : ce sont des sons d'ambiance joués chez les joueurs ; le serveur les charge pour
  rien (22 Mo de jar). Les retirer du serveur seulement : à essayer d'abord sur le serveur d'essai (un joueur qui les a
  doit toujours pouvoir entrer).
- **Fichiers de cases Curios en trop** : les retirer ferait disparaître 4 lignes d'erreur, mais l'ordre de lecture de
  Curios changerait ; aujourd'hui le bon fichier de HeraliZ passe en dernier (vérifié). À ne faire qu'avec un essai en
  jeu des cases d'équipement.

## 46. Serveur en ligne et banc final : audit du 21/11

Demande du propriétaire : « que tout soit clean, sans aucun bug, fonctionnel à 100 % et opti ». Cette partie : le
serveur en ligne depuis la mise à jour du 21/11 à 0 h 41 (et la tendance depuis le 19/11), lu en lecture seule par
« Journaux du serveur » et « Lenteurs du serveur », puis un dernier banc de charge à 150 bots. Version : Anticheat
**2.15.2** (le launcher la donne). **Rien n'a été testé en jeu.**

### Le serveur en ligne (lu le 21/11 vers 1 h 30, heure française)

| quoi | constat | à faire ? |
|---|---|---|
| Joueurs | aucun connecté depuis le redémarrage de 0 h 41 | pas de profil lancé (il en faut avec des joueurs) : à lancer un soir chargé (« Lenteurs du serveur », case profil, 60 s) |
| Erreurs Java | aucune depuis le redémarrage ; le 20/11 : seulement des jeux sans Forge refusés (normal) | non |
| Plantages | aucun depuis le 28/10 (4 vieux rapports d'octobre et de 2025) | non |
| Blocages | 1 seul : la première sauvegarde automatique, 10 min après le démarrage (174 ms : Forge écrit la liste de tous les objets dans level.dat, une fois par démarrage, ensuite resservie) ; 4 ticks lents pendant le démarrage (pas d'alerte, normal) | non |
| Tendance | rapports de blocage : 38 le 19/11 avant les correctifs de l'après-midi (`/spawn`, `/rtp` jusqu'à 1,5 s), 2 du 19/11 au soir au 20/11 midi, 3 le 20/11, 1 depuis le 21/11 ; mais presque personne n'a joué : ça ne dit rien pour 150 joueurs | non |
| Avertissements | LiteBans (dépendance pas déclarée, sans effet), ModernFix (temps de démarrage), « Invalid statistic » (statistiques d'objets d'anciens mods dans le fichier d'un joueur, sans effet) | non |
| Anticheat | aucune règle « en rodage » suspendue (le disjoncteur n'a jamais sauté), aucune expulsion, aucun pack refusé, aucune alerte de tirs ou d'ouverture à travers un mur depuis le 19/11 (peu de parties) ; **une fausse alerte « JvmHardening »**, corrigée ci-dessous | corrigé (2.15.2) |
| Mémoire | 16 Go ; 2,7 Go vraiment utilisés après le démarrage (comme le 20/11) ; aucune pause complète ; les 3 pauses de plus de 200 ms sont dans les 2 minutes du démarrage | non |
| Disque | 27,1 Go libres sur 147,4 Go ; rien n'a grossi depuis le 20/11 ; les vieilles copies de plus de 8 Go sont toujours là (section 37) | décision du propriétaire (section 37) |

Décisions du 20/11, bien en place sur le serveur : vue 6 au spawn (journal : « distance de vue de heralisz:spawn : 6
tronçons »), carte à 8 ; portées de suivi x 0,8 (`spigot.yml`) ; sauvegarde automatique toutes les 10 min (`bukkit.yml`) ;
réglages `[perf]` du noyau en marche (compteurs de `/diag etat` : tronçons au repos, relevés des apparitions et revues des
tickets sautés, 15 000 étages de la carte sans relecture des points d'intérêt). Zombies sans boules d'expérience :
réglage copié à la mise en ligne, et au banc il n'en reste presque plus (12 au lieu d'environ 100).

### Anticheat 2.15.2 : plus de fausse alerte « JvmHardening »

Dans le journal du 20/11, un joueur normal, venu par le launcher, est signalé « JvmHardening (VL 1) : protections
anti-injection manquantes (agents dynamiques autorisés) : lancez le jeu via le launcher officiel ». Ce n'était pas un
tricheur : depuis la 2.15.0 (`requireHardenedJvm = true`), **chaque joueur du launcher** recevait cette alerte à chaque
connexion (message au staff en jeu, ligne dans `alerts.log`, message Discord). Le jeu croyait qu'un réglage de Java
(`EnableDynamicAgentLoading`) n'existait qu'à partir de Java 21 ; il existe aussi en Java 17, celui du launcher. À
l'ouverture, les vraies alertes seraient passées derrière des centaines de fausses (Discord : un message par seconde au
plus), et le staff aurait pu croire à de la triche. Aucune expulsion tant que `alertOnly = true`.

Maintenant, seul un jeu lancé sans la protection du launcher (« attach activé ») est signalé, comme le mode strict.
Correction côté serveur seulement.

- [ ] Se connecter avec le launcher : aucune alerte « JvmHardening » chez le staff, ni sur Discord, ni dans `alerts.log`.
- [ ] `/spartan status <joueur>` : rien de nouveau ; les autres alertes (Launcher, ResourcePack, Injection…) comme avant.

### Banc final (150 bots, 5 min de mesure, monde généré)

La vitesse des machines de GitHub change d'un banc à l'autre (jusqu'à 1,8 fois entre deux modèles de processeur) :
chaque banc du 21/11 est comparé à celui du 20/11 tombé sur le même processeur.

| ms par tick (150 bots) | moyenne | 95 % | 99 % | pire | ticks de plus de 50 ms |
|---|---|---|---|---|---|
| 20/11, processeur EPYC 9V74, vue 8 | 36,7 | 49,3 | 75,0 | 169 | 280 |
| **21/11, branche (avant les derniers envois des audits), EPYC 9V74, vue 8 (spawn 6)** | **31,4** | **44,2** | **60,6** | 166 | **169** |
| 20/11, processeur EPYC 7763, vue 7 | 38,9 | 50,7 | 66,4 | 194 | 341 |
| 21/11, version en ligne (`main`), EPYC 7763 (le plus lent), vue 8 (spawn 6) | 38,6 | 53,2 | 73,9 | 256 | 426 |
| 21/11, branche finale (avec cette section), Xeon Platinum 8573C, vue 8 (spawn 6) | 37,2 | 49,2 | 69,9 | 131 | 271 |

- Même processeur, même vue : **−5,3 ms par tick (−14 %)** et 40 % de ticks lents en moins. La branche finale est tombée
  sur un processeur sans banc à 150 bots le 20/11 (à 100 bots : 28,9 ms, contre 27,3 sur l'EPYC 7763) : aucune perte
  visible après les derniers correctifs des audits, chaque mod garde sa part. La version en ligne, sur la
  machine la plus lente et à la vue 8, fait comme le 20/11 à la vue 7 (un cran plus court) : mieux, à vue égale. Prudence :
  deux bancs sur le même processeur peuvent encore différer de 20 % (section 38).
- Part de chaque mod (branche, EPYC 9V74 ; entre parenthèses le 20/11, même processeur) : Minecraft 9,5 ms (10,8), bots
  du banc 8,8 (9,5), Noyau 3,7 (4,4), TaCZ 2,3 (2,8), anticheat 1,4 (1,9), ModernFix 0,9 (1,2), Curios 0,7 (0,8), Monde 0,6
  (0,8), CRac 0,4 (0,5) ; tous les autres sous 0,35 ms.
- Boules d'expérience : 12 au lieu d'une centaine (décision du 20/11, 0,2 ms au lieu de 0,8). Mémoire gardée : 3,7 Go
  (comme le 20/11). 20 TPS, aucun « Can't keep up ». Les pires ticks viennent des bots eux-mêmes (connexion d'un bot,
  relevés du banc) : rien de nouveau dans nos mods.
- Objectifs (30 / 40 / 50 ms) : pas encore atteints à 150 bots sur ces machines de 4 cœurs, mais plus près que le 20/11.
  Le plus gros gain restant : une machine plus rapide par cœur (section 10 de `docs/DEPLOIEMENT.md`).

### Remarques (rien n'est changé, à décider)

- Points d'intérêt : la relecture n'est sautée que sur la carte. Au banc, les autres mondes (tutoriel des bases, spawn)
  en relisent encore : 1,7 s en tout pendant le banc, jusqu'à 105 ms pour un seul étage. Ajouter `"crac:tuto"` et
  `"heralisz:spawn"` à `poi_sans_relecture` (section `[perf]` de `heraliz_noyau-server.toml`) les supprimerait (aucun de
  nos mods ne lit ces points d'intérêt). Gain petit, surtout à l'ouverture (nouveaux joueurs dans le tutoriel).
- Launcher : il pourrait couper aussi `EnableDynamicAgentLoading` (accepté par Java 17, vérifié) : une protection de
  plus, à la prochaine version du launcher. Son commentaire qui dit le contraire est faux.
- La première sauvegarde automatique après chaque démarrage coûte 100 à 175 ms (une seule fois) : sans gravité.

Pas testé : tout en jeu ; aucun profil du serveur en ligne avec des joueurs (personne cette nuit) ; le banc n'est pas la
vraie carte (ni ses conteneurs, ni ses villes).

## 47. Décisions du 21/11 : anticheat renforcé, staff caché partout

### Règles en rodage de l'anticheat (Spartan 2.15.3)

Les règles nouvelles du 19/11 (agent ou débogueur injecté, classe modifiée en mémoire, launcher imité, packs de textures
ajoutés) ont un « disjoncteur » : si trois PC différents les déclenchent en moins d'une heure, c'est sans doute une
fausse alerte, la règle se suspend (alerte seulement) et le staff est prévenu.
- **On compte des PC, plus des comptes** : un PC = son compte, sa connexion internet (bloc d'adresses /24, IPv6 /48) et
  les identifiants matériels envoyés par son jeu. Un tricheur avec dix comptes sur son PC, ou plusieurs PC derrière la
  même box, ne compte qu'une fois : il ne peut plus couper la règle tout seul.
- **Remise en service toute seule au bout d'une heure** (`rodageRepriseMinutes = 60`, section `[injection]` de
  `serveur/config/spartan-common.toml` ; 0 = suspendue jusqu'au redémarrage, comme avant) : alerte « Règle remise en
  service » au staff ; `/spartan status <joueur>` donne le temps restant.
- Un PC qui a déjà fait suspendre une règle ne compte plus pour elle jusqu'au redémarrage (il reste expulsé quand elle
  est en service) : les mêmes PC ne peuvent pas la couper à chaque remise en service. **Changé par l'audit du 21/11
  (section 49, Spartan 2.15.5)** : ils comptent encore mais ne la coupent plus seuls (il faut au moins un PC nouveau) ;
  avant, ils restaient expulsés à chaque connexion même quand la fausse alerte touchait d'autres PC.
- Essais hors du jeu : `RodageTest`, 13 essais, tous passés (16 depuis la section 49).

À tester en jeu :
- [ ] Rien ne change pour un joueur normal : aucune expulsion, aucune alerte « Règle suspendue ».
- [ ] `/spartan status <joueur>` : pas de ligne « règles en rodage suspendues » tant que rien n'est suspendu.

### Staff caché (vanish) : tags de clan, liste vocale, profil (Boutique 1.7.1, Clans 3.19.1, Voix 1.7.1)

Le tab ne montrait déjà plus un membre du staff caché aux joueurs. Le reste suit maintenant la même règle :
- **Tags de clan** : deux listes, comme le tab. Les joueurs hors du staff ne reçoivent plus jamais le tag d'un caché (un
  jeu modifié ne peut plus y lire qu'il est connecté). Relu toutes les 2 s (se cacher, se montrer, staff qui se
  connecte) ; rien ne part si rien ne change.
- **Liste vocale** : pareil. En plus, la voix d'un caché n'est envoyée qu'au staff (avant : les joueurs proches
  l'entendaient). Lui entend toujours tout le monde.
- **`/profil <pseudo>`** d'un caché, vu par un joueur hors du staff : comme un joueur hors ligne (temps de jeu et plus
  longue vie sans la partie en cours : ils ne montent plus pendant qu'il est connecté).
- **Son clan** : pour les membres de son clan hors du staff, il est « hors ligne » dans l'écran du clan, absent de la
  carte (touche M : ni sa position ni le compte des membres en ligne), et `/clan info` ne le compte pas en ligne (pour
  tous les joueurs, aussi la fiche d'un autre clan).
- Personne de caché : rien ne change, une seule liste pour tous.
- Essai hors du jeu du tri des voix (`VoiceRouting`) : un caché qui parle n'est entendu que par le staff ; un joueur qui
  parle est entendu de tous, caché compris.

À tester en jeu (S = staff dans un clan, A = joueur hors du staff, B = autre staff) :
- [ ] S se cache (« Se cacher » du panel) : rien ne change à l'écran de A (S n'était déjà plus dans son tab) ; S parle à
      côté de A : A n'entend rien, B l'entend.
- [ ] S se montre : sa voix revient chez A (2 s au plus), son tag de clan aussi (tab, au-dessus de sa tête).
- [ ] S caché : A tape `/profil S` deux fois à une minute d'écart : le temps de jeu ne bouge pas. B le voit bouger.
- [ ] S caché, C = joueur du clan de S : dans l'écran du clan, S est « hors ligne » ; sur la carte (M), S n'apparaît
      pas et le nombre de membres en ligne ne le compte pas ; `/clan info` (C ou un autre joueur) ne le compte pas.
      S se montre : tout revient (au plus quelques secondes).
- [ ] Personne de caché : tags, liste vocale, voix, écran et carte du clan comme avant.

Pas testé : tout en jeu (aucune compilation ici : contrôle de la branche sur GitHub).

## 48. Améliorations choisies le 21/11 : spawn et tutoriel allégés, contrôles d'images sur GitHub

- **Spawn et tutoriel allégés** : la relecture des « points d'intérêt » (lits et postes de villageois, ruches, portails)
  est aussi sautée dans le monde du spawn et celui du tutoriel des bases (`poi_sans_relecture` de
  `serveur/defaultconfigs/heraliz_noyau-server.toml`, la carte l'avait déjà depuis le 20/11). Aucun de nos mods ne s'en
  sert. Au banc : jusqu'à 105 ms pour un seul étage, 1,7 s en tout. Réglage seulement (rien à télécharger pour les
  joueurs) ; retour en arrière : retirer les deux mondes de la liste.
- **Contrôles d'images sur GitHub** : les 5 contrôles qui lisent les images (logo « M » des icônes, icônes des
  accessoires, chargeurs 3D, accessoires tenus en main, placement des accessoires sur les armes) tournent maintenant à
  chaque envoi d'une branche (job « images » du « Contrôle de branche », en même temps que la fabrication). numpy et
  Pillow y sont installés à des versions et empreintes figées (`outils/requis_images.txt`), dans un job sans aucun secret.
  Sur un PC : `python outils/verifier.py --images` (seulement ces contrôles, sans fabriquer). La mise en ligne (`main`) ne
  les refait pas : la branche les a déjà passés.

À vérifier en jeu :
- [ ] Spawn et tutoriel des bases : rien ne change (PNJ, lits, portes, construction du tutoriel).

Pas testé : tout en jeu.

## 49. Audit global profond du 21/11 : tout le serveur relu (10 relectures)

Demandé par le propriétaire : relire tous les mods et tout le code, les liens entre mods, les lenteurs possibles. Dix
relectures en parallèle, une par partie. Synthèse : `docs/audit_global_21nov/LISEZMOI.md` ; un compte rendu par partie
dans le même dossier (avec la liste complète des essais). Aucun canal réseau changé ; launcher et site inchangés.

Corrigé (le détail, les fichiers et les lignes sont dans chaque compte rendu) :
- **ClashWar 1.3.7** : écran ouvert pendant l'écriture d'un dépôt refermé (duplication d'une arme équipée) ; souris et
  grille 2 x 2 rendues à la déconnexion ; Coffre jamais rendu à un mort (attend la réapparition) ni à un joueur à terre
  (relevé d'abord) ; spectateur bloqué dans le monde des arènes ; ce qui ne tient pas tombe à l'arrivée ; `/wipe` garde
  l'objet tenu à la souris ; plus d'« équipe retirée » à tort. **Réanimation 1.16.14** : exécutions limitées à une toutes
  les 400 ms ; garde hors match et bordure laissent un mort tranquille.
- **Boutique 1.7.2** : encaissements de la caisse du casino toujours ceux du serveur ; tablette et éditeur des caisses :
  op ET `/login` ; `/boutique abonnement`, `/avantages nick qui`, `/grade voir`, `/grade liste` répondent aux non-op ;
  hôtel des ventes : solde demandé au site 1 fois / 5 s ; `pb retirer` refusé affiché comme refusé ; objet donné à un mort
  rendu après la réapparition ; pastilles du chat sans calcul à chaque image.
- **CRac 1.38.9-beta** : armoires gardées une par une (tutoriel à deux d'un même clan) ; armoire d'essai gardée si le clan
  est dissous ; tutoriel sans effet sur la réservation d'une base raidée ni sur les messages d'entretien du clan ; échelle
  de raid frappée seulement à portée de main ; trois tests de tronçon qui faisaient attendre le serveur ; dépendance au
  noyau déclarée au serveur.
- **Monde 1.3.10** : hélicoptère, frappes, ping, jumelles, quai du train, lampes : plus d'attente d'un tronçon en lecture.
- **Armes 2.59.9** : vitres qui repoussent sans attendre un tronçon ; objet à vie refusé dans la caisse de largage posée
  en bloc (aussi côté serveur) ; petites économies d'images. **Sacs heralisz28** : défis de déblocage coupés (tout est
  débloqué) ; reliquats d'un ancien serveur retirés.
- **Clans 3.19.2, Quêtes 1.24.4, Événements 1.3.3** : `/bourse donner` en op 4 connecté ; économie « indisponible » si le
  fichier des comptes est illisible ; staff caché sans « vu à l'instant » pour son clan ; erreur du Directeur
  d'événements notée au lieu d'arrêter le serveur ; marchands à 4 opérations / s ; panneau de zone des territoires.
- **Loot 0.20.8, Bunker 0.14.8** : fouille arme en main 1 / tick ; `/heralizloot reinitialiser 128` sans gel ; portes des
  salles à carte et coffres T7 sans attente d'un tronçon ; couleurs de rareté sans calcul à chaque case.
- **Spartan 2.15.5** : disjoncteur par adresse publique seulement ; identifiants matériels lus avant les packs ; alertes en
  jeu au staff vérifié ; IPv4 écrite en IPv6 ; les PC qui ont coupé une règle comptent de nouveau avec un PC nouveau.
- **Noyau 1.1.11** : actions du panneau du staff jugées comme des actions du serveur (pas les commandes tapées) ; arrêt
  avec code de sortie qui attend les écritures.
- **Liens entre les mods** (relecture a10) : noyau déclaré des deux côtés (clans, extraction, armory 1.4.2, loot) ;
  Événements → noyau et Grades → Économie et Clans (serveur) déclarés ; sous-mods du monde moins stricts sur le numéro
  de heralisz_utils. **Pas changé (décision du propriétaire)** : les lettres nettes de l'interface du 03/11 n'ont jamais
  été chargées chez les joueurs (oubli dans le manifeste du jar). **GitHub** : fabrication sans aucun secret (contrôles dans un job à
  part) ; version Modrinth nettoyée ; IPv6 masquées dans les résumés publics.

À tester en jeu (les plus importants ; le reste dans chaque compte rendu) :
- [ ] **ClashWar** : départ avec un fusil équipé (lunette, silencieux, skin), un sac plein et un objet à la souris ; à la
      clôture, tout revient exactement une fois. Quitter le jeu juste à la clôture avec un objet à la souris : il revient.
- [ ] **ClashWar, spectateur** éliminé : touches 1 à 9, « se téléporter » vers un joueur de la carte : rien ne doit se
      passer (si ça marche, le dire). Un op peut toujours.
- [ ] **Tutoriel des bases** à deux comptes du même clan, à quelques secondes d'écart : chacun finit, cabanes effacées ;
      un troisième arrive sur une parcelle vide ; le reste du clan ne reçoit aucun message d'entretien.
- [ ] **Casino** (op connecté) : tablette, page de la caisse, achat d'un encaissement à la caisse : il est donné.
- [ ] **Joueur sans op** : `/boutique abonnement` affiche son grade et le temps restant ; Helpeur : `/grade voir`.
- [ ] **`/bourse donner`** : op 4 connecté et console seulement.
- [ ] **Staff sans op** en survie sur la carte, en combat : panneau → « Ban PC » d'un compte d'essai, « Jour »,
      « Largage » : fait. Un joueur normal : `/spawn`, `/warp` toujours refusés sur la carte, `/home` refusé en combat.
- [ ] **Hélicoptère** (`/event helico lancer`), frappe aérienne, largage public : vols à la bonne hauteur, aucune alerte
      « [Diag] Blocage » ; ping posé loin depuis la carte M ; déconnexion à bord du train puis reconnexion : sur le quai.
- [ ] **Vitres** cassées : elles repoussent au bout de 5 min ; revenir en courant dans un coin plein de vitres cassées :
      aucune alerte « [Diag] Blocage ». **Salle à carte** approchée depuis 200 blocs : pas d'alerte non plus.
- [ ] **Fouille** arme en main en cliquant vite : s'ouvre comme avant ; **marchands** : acheter, vendre vite : tout passe.
- [ ] **Sacs** : ouvrir, ranger, Maj + clic, sac dans un coffre : comme avant ; démarrage du serveur sans erreur
      « backpacked ».
- [ ] **Connexion** par le launcher : « Jeu validé », aucune alerte « Règle suspendue » ; un op reçoit les alertes Spartan
      après son `/login`.
- [ ] **Journal du serveur** au démarrage et après une heure : aucune erreur nouvelle, aucune « dépendance manquante ».

Pas testé : tout en jeu ; rien de mesuré avec 150 vrais joueurs (aucune compilation ici : fabrication, contrôles et
démarrage d'essai sur GitHub).

## 50. Suite de l'audit du 21/11 : choix du propriétaire (sécurités en plus, allègements, skins d'armes)

L'audit de la section 49 est en ligne depuis le 21/11 à 14 h 22 (heure UTC ; serveur prêt, aucune erreur Java dans le
journal). Décisions du propriétaire : lettres nettes de l'interface **non** (« les lettres sont déjà bien plus nettes ») ;
captures de territoire du staff : **gardent** leurs points de saison ; contrôles GitHub en plus : non. Choisis : sécurités
en plus, allègements du serveur, et une demande nouvelle : les skins d'armes impossibles à voler, comme ceux des couteaux.
Détail dans chaque compte rendu de `docs/audit_global_21nov/`, section « Suite du 21/11 ». **Mis en ligne le 21/11 à
15 h 34 (heure UTC)** : serveur prêt à 15 h 36, aucune erreur Java dans le journal.

Fait :
- **Armes 2.59.10, skins d'armes impossibles à voler** : un objet camo posé sur une arme entrait d'abord dans la
  collection à vie du joueur, puis se posait depuis elle (lié au joueur). **Retiré le 21/11 au soir (Armes 2.59.11) :
  malentendu, le propriétaire demandait si les skins d'armes pouvaient être protégés contre le vol de nos fichiers jar,
  comme ceux des couteaux, pas un changement en jeu. Les skins marchent de nouveau en jeu exactement comme avant** (un
  objet posé reste définitif sur l'arme). Gardé : collection écrite en fond, une action de camo ou de sticker toutes les
  0,15 s au plus (rien ne change pour les joueurs).
- **ClashWar 1.3.8** : au départ, chaque objet est comparé en entier (accessoires, skin, usure, contenu du sac) ; une
  erreur d'un autre mod pendant un dépôt laisse le joueur intact, le serveur ne plante plus.
- **Voix (CaraCrea 1.7.2)** : avant le `/login`, un joueur ne parle pas, n'entend personne et ne touche pas aux groupes.
- **Quêtes 1.24.5** : recycleur de la carte jamais en double après une écriture ratée (il se ferme et réessaie) ;
  journal des quêtes à 4 demandes par seconde au plus. **Clans 3.19.3** : `profils.json` écrit en fond ; écran de la
  saison une fois par seconde au plus.
- **CRac 1.38.10-beta** : limite de cadence pour « À la base » à la mort et pour l'onglet Accès de l'armoire.
- **Boutique 1.7.3** : `/kit` s'ouvre sans attendre le bot Discord ; `/avantages nick` ne relit plus `usercache.json` à
  chaque essai.
- **Monde 1.3.11** : l'usure ne regarde plus son fichier de réglages depuis le fil du serveur (prise en compte d'un
  changement à la main en moins de 10 s) ; le train ne refait plus l'horaire des rames pour chaque balle lointaine.

À tester en jeu :
- [ ] **Skins (Armes 2.59.11, retour comme avant)** : un objet camo posé depuis l'atelier est définitif sur l'arme et
      l'objet est utilisé (comme avant le 21/11) ; un camo de la collection se pose et se retire comme avant ; couteaux,
      skins à vie, sacs, stickers et StatTrak comme avant ; après un redémarrage, la collection est toujours là.
- [ ] **ClashWar** : départ avec un fusil équipé, un sac plein et des Curios : chacun entre en salle du premier coup, tout
      revient exactement une fois ; journal sans « inventaire change pendant l'ecriture » ni « erreur d'un autre mod ».
- [ ] **Voix** : avant le `/login`, le nouveau venu n'est pas entendu et n'entend rien ; juste après, tout marche ; les
      joueurs déjà connectés ne voient aucune différence.
- [ ] **Recycleur de la carte** : déposer, recycler, reprendre, redémarrer : comme avant.
- [ ] **`/profil`** : les chiffres restent justes après un redémarrage ; journal des quêtes (J) et onglet Saison : comme
      avant, même en cliquant vite.
- [ ] **Bases** : mourir puis « À la base » : retour du premier coup ; onglet Accès : taper vite un nom, ajouter et retirer
      plusieurs joueurs : tout passe.
- [ ] **`/kit`** avec le bot Discord en marche, puis arrêté : le menu s'ouvre tout de suite. **`/avantages nick`** avec
      le pseudo d'un autre joueur : refusé.
- [ ] **Usure** : changer une valeur à la main : « [usure] reglages relus » dans la console en moins de 10 s. **Train** :
      tirs depuis le quai et de loin : comme avant.

Pas testé : tout en jeu (fabrication, contrôles et démarrage d'essai sur GitHub seulement).

## 51. Audit de validation du 21/11 au soir (10 nouveaux relecteurs)

Après la mise en ligne de 17 h 43 (heure UTC), demande du propriétaire : « refaire un audit bien profond et complet pour
bien tout valider ». Tous les changements du jour sont validés ; corrections et propositions : synthèse
`docs/audit_global_21nov/validation/LISEZMOI.md` (un compte rendu par partie dans le même dossier). Versions : Boutique
1.7.4, Monde 1.3.12, CRac 1.38.11-beta, Clans 3.19.4, Loot 0.20.9, Bunker 0.14.9, ClashWar 1.3.9, Spartan 2.15.6,
Noyau 1.1.12 ; aucun canal réseau changé par cette relecture. Journaux publics de GitHub sans chat, messages privés ni
commandes tapées. **Mis en ligne à 19 h 09 (heure UTC)**, puis à 19 h 29 les choix du propriétaire : `/camos ajouter` et
`/camos retirer` à l'op 4 (Armes 2.59.13), `/quete reset` à l'op 4 avec `/login` (Quêtes 1.24.6), contrôles de la mise en
ligne dans un job sans aucun secret (empreinte de JOML vérifiée). Les deux fois : aucune erreur Java au démarrage.

À tester en jeu :
- [ ] **`/tpa`** : une demande normale marche ; demander un joueur puis un autre pendant l'attente : une seule
      téléportation, la deuxième attend la recharge.
- [ ] **Boutique et `/trade` en PB** avec un solde trop bas : « solde insuffisant » tout de suite (plus « site
      injoignable ») ; au `/trade`, les objets reviennent aussitôt.
- [ ] **Clic gauche et casse de blocs** en survie, échelle de raid, carrière : comme avant. `/base effacer` et fin du
      tutoriel : seule l'armoire disparaît, jamais un bloc de la carte.
- [ ] **Hélicoptère abattu** près des joueurs puis loin : l'épave tombe au bon endroit, ses 2 conteneurs T6 s'ouvrent et
      disparaissent à leur heure, aucune alerte « [Diag] Blocage ».
- [ ] **Salle à carte** : se reconnecter dedans : on ressort devant la porte et elle est déjà fermée. Passage d'une carte :
      même geste qu'avant, vu par un autre joueur.
- [ ] **ClashWar** : départ avec un inventaire plein et un sac plein ; mourir puis réapparaître pendant l'attente du
      retour : tout revient. Coffre du clan pris juste au départ : rien ne disparaît au retour.
- [ ] **Connexion** : se déconnecter et revenir aussitôt avec le même compte : « Jeu validé » comme d'habitude.
- [ ] **Noyau** : actions du panneau du staff (ban PC, jour, largage) comme ce midi ; un panneau en bois « /spawn » posé
      par le staff : refusé en combat et sur la carte (comme avant le 21/11) ; `/redemarrage 5` puis `/redemarrage
      annuler` : rien ne redémarre.
- [ ] **Skins, étape 1** : `/camos dessins` (op) : bandeau « 79 programmes prêts » ; dans le journal du serveur « 167
      fichiers (698 Ko) compressés en 88 Ko » ; skins identiques en main, dans l'atelier et dans l'inventaire.
- [ ] **Op 2 (pas op 4)** : `/camos ajouter`, `/camos retirer` et `/quete reset` ne sont plus proposées ; `/camos voir`
      et `/camos dessins` marchent. Op 4 connecté : les trois marchent (`/quete reset` demande d'abord le `/login`).

Pas testé : tout en jeu (fabrication, contrôles et démarrage d'essai sur GitHub seulement ; démarrage « mixins
stricts » allé jusqu'au bout côté serveur).

## 52. Fin du 21/11 : tout ce qui restait ouvert, terminé (demande du propriétaire)

Demande du propriétaire : « je ne créerai pas NOMS_INTERDITS, fais en sorte que tout soit lançable et terminé à 100 % ».
Les propositions encore ouvertes des deux audits ont été faites, sauf ce que le propriétaire a tranché (captures du staff
avec leurs points, pas de lettres nettes, règles de jeu de /trade et du ClashWar inchangées) et ce qui demande un essai en
jeu ou un regard sur la carte (ligne de vue des échelles, rendu des portes HBM, porte « transition seal »). Détail : section
« Fin du 21/11 » de chaque compte rendu de `docs/audit_global_21nov/validation/`. Le contrôle des noms perso est sauté par
choix du propriétaire (simple note dans les tâches GitHub). Démarrage « mixins stricts » : 0 injection ratée (111 mixins
essayés sur 111 côté serveur). Versions : Noyau 1.1.13, Armes 2.59.14, Sacs heralisz29, Boutique 1.7.5, Monde 1.3.13, CRac
1.38.12-beta, Clans 3.19.5, Quêtes 1.24.7, Événements 1.3.4, Loot 0.20.10, ClashWar 1.3.10, Réanimation 1.16.15, Spartan
2.15.7 (jar des joueurs changé : défi d'intégrité hors du fil de l'image), CaraCrea 1.7.3.

À savoir : le dépôt GitHub est public. Les dessins des skins ne sont plus dans le jar des joueurs (étape 2), mais leurs
sources restent lisibles dans le dépôt (comme tout le code et les fiches des couteaux) : la protection ne vaut contre la
copie que pour un jar pris chez un joueur, pas pour qui lit le dépôt.

À tester en jeu (les plus importants ; le reste dans chaque compte rendu) :
- [ ] **Skins (étape 2)** : premier lancement après la mise à jour, connexion : les skins (camos animés, effets 3D,
      couteaux Doppler/Fade, sac camouflé) apparaissent pendant ou juste après l'écran de chargement, identiques en main,
      en 3e personne, dans l'atelier et sur les icônes ; journal du jeu « 79 programmes prêts, 0 en échec ».
      Déconnexion, reconnexion, F3+T : rien n'est refait.
- [ ] **Staff caché** : un joueur hors du staff qui fait `/msg <caché>` : « Aucun joueur n'a été trouvé » ; le caché écrit à
      un joueur : ce joueur peut lui répondre pendant 5 min (`/r`), pas les autres. Groupe vocal d'un caché : invisible.
- [ ] **Casino** : un op 2 ne peut plus enregistrer la page de la caisse ni les gains de la tablette ; un op 4 connecté oui.
- [ ] **Hérals** : vendre à Mila avec un compte presque au plafond (10 millions) : vente refusée, rien n'est pris.
- [ ] **Bases** : `/home` et `/rtp` refusés si on entre en combat pendant l'attente, sans perdre la recharge ;
      `/base effacer <chef>` laisse la cabane d'essai d'un autre membre en plein tutoriel.
- [ ] **Borne de départ** : départs normaux, et beaucoup de départs en même temps (`/depart info`).
- [ ] **ClashWar** : retour du Coffre avec un écran ouvert : rien ne se perd ; menu des exécutions comme avant.
- [ ] **Staff op 4 en combat** : panneau → « Ban PC », « Jour » : se fait ; `/spawn` tapé en combat : toujours refusé.
- [ ] **Voix** : un joueur qui arrive n'est entendu qu'après son `/login`, même avec l'UDP bloqué.
- [ ] **Démarrage du serveur** : une seule erreur « face / ear_protection » de chaque (elle vient d'un mod d'un autre
      auteur), aucune autre erreur nouvelle.
- [ ] **Launcher** (à la prochaine publication seulement) : l'alerte de sécurité GitHub n° 22 est réglée dans l'outil qui
      le fabrique (`minimatch` 11.0.9, rien ne change pour les joueurs) ; après « publier », installer et lancer le launcher
      sur Windows (et Mac si possible) : mêmes fichiers, connexion et lancement du jeu comme avant.

Pas testé : tout en jeu (fabrication, contrôles et démarrages d'essai, normal et « mixins stricts », sur GitHub). Launcher :
tests, relecture et fabrication d'essai sur GitHub seulement, jamais installé ni lancé.

## 53. Collection de Noël du 22/11 : 8 skins d'armes et 5 skins de couteaux

Demande du propriétaire : des skins « WOW » pour Noël ; trois tours de rendus (galerie « Collection Noël HeraliZ »), puis
« je valide, tu peux tout mettre sur le serveur ». Écartés à sa demande : Sucre d'Orge (arme), Houx Royal (arme et
couteau), Paquet Royal, Blizzard, Krampus, Charbon.

Ce qui change :
- **8 camos** (Armes 2.60.0), dessinés entièrement par le skin, objets `heralisz_camo:camo_<id>` (identifiants figés) :
  Guirlande (Légendaire, animé), Flocon Royal (Épique, animé), Nuit d'Hiver (Légendaire, animé : neige qui tombe dans
  l'arme, neige posée sur le dessus), Pain d'Épices (Rare), Boule de Noël (Légendaire), Joyau Impérial (Légendaire), Pull
  de Noël (Épique), Givre Vivant (Légendaire). Icônes d'inventaire tirées des vrais dessins ; noms en français, anglais et
  russe.
- **Givre Vivant, deux skins en un** : inspecter l'arme (touche d'inspection) la couvre de givre en 2,5 s ; elle reste
  givrée ; une nouvelle inspection fait fondre le givre (quelques gouttes d'eau) et rend l'acier poli. L'état est gardé
  sur l'arme : il la suit (sol, coffre, échange) et tous les joueurs voient le même. Inspecter pendant le gel le retourne
  en dégel sans saut. Le serveur décide (arme tenue, camo vérifié, une bascule par seconde au plus par joueur). Une ligne
  bleue de l'infobulle de l'objet le dit au joueur. Sur un sac, ce camo reste en acier nu (pas d'inspection).
- **5 couteaux** (`heralisz_skins:<couteau>__<finition>`, identifiants figés) : Karambit | Sucre d'Orge (Prestige,
  3 étoiles, manche en caoutchouc noir), Butterfly | Saphir Givré (Ultime, 4 étoiles), M9 | Guirlande (Prestige,
  3 étoiles, guirlande en relief), Karambit | Or Givré (Ultime, 4 étoiles), Karambit | Glace Sculptée (Ultime, 4 étoiles).
  Icônes rendues comme celles du catalogue.
- **Caisses** (`outils/caisses.py`, règle du 19/11 : chaque nouveau camo a la chance des camos de sa rareté) : caisse
  Camo, stickers et StatTrak restent à leur minimum de 10 % et tous les camos baissent ensemble d'environ 7 % (mêmes
  écarts entre raretés) ; caisse Événement, les nouveaux camos Épiques et Légendaires à leur chance fixe, hérals 36,5 % au
  lieu de 39,7 % ; caisse Gemme (154 lots au lieu de 149) et Événement : les 5 couteaux selon leurs étoiles. Boutique
  1.7.6 : butin par défaut de la caisse Camo (seulement si son fichier manquait).
- **Loot de la carte** (Loot 0.20.11) : comme tous les couteaux de 1 à 3 étoiles, Sucre d'Orge et M9 Guirlande peuvent
  sortir des coffres T7 (Unique) ; jamais les 4 étoiles. Rareté des 5 couteaux : Unique (`raretes.json`).
- **Dessins des skins** : 92 programmes (86 camos et couteaux, 6 effets 3D) au lieu de 79, tous compilés avec un vrai
  compilateur OpenGL ; copie envoyée aux joueurs à la connexion : 26 fichiers de plus, 103 Ko compressés au lieu de 87
  (journal du Ptero, `docs/SKINS_PROTEGES.md`).

À tester en jeu :
- [ ] **Connexion** après la mise à jour : journal du jeu « 92 programmes prêts, 0 en échec » ; journal du serveur
      « 193 fichiers ».
- [ ] **Les 8 camos** sur 2 ou 3 armes chacun (pistolet, fusil d'assaut, précision) : comme sur la galerie, en main, en 3e
      personne, dans l'atelier et sur l'icône de l'arme ; Guirlande et Nuit d'Hiver aussi de nuit ; Nuit d'Hiver : neige
      posée sur le dessus de l'arme.
- [ ] **Givre Vivant** : inspecter → givre en 2,5 s, l'arme reste givrée ; changer d'arme et revenir, se déconnecter et
      revenir : toujours givrée ; inspecter de nouveau → dégel avec gouttes ; un autre joueur voit les mêmes états ; deux
      appuis très rapprochés ne font pas clignoter l'arme ; l'animation d'inspection n'est pas coupée ; l'icône de l'arme
      dans l'inventaire suit l'état (une icône givrée, une non givrée, sans le passage ; la première fois, l'icône dessinée
      du pack peut s'afficher une image le temps de la photo).
- [ ] **Infobulle** de l'objet Givre Vivant : la ligne bleue « Inspectez l'arme pour la givrer, puis pour la dégivrer ».
- [ ] **Les 5 couteaux** : en main, en 3e personne, inspection, icône (Butterfly : manche noir et incrustations bleues ;
      Sucre d'Orge : manche caoutchouc noir, seule la lame et l'anneau ont le sucre d'orge), étoiles dans l'infobulle.
- [ ] **Caisses** Camo, Événement et Gemme : la roue montre les nouveaux lots ; en ouvrir quelques-unes.

Pas testé : tout en jeu. Fait hors du jeu : rendus des vrais programmes (identiques à la galerie validée), compilation
des 92 programmes, contrôles des textes (aucune erreur, avertissements d'avant), outils des caisses (0 problème) ; la
fabrication et les contrôles des jars se font sur GitHub (la fabrication n'est pas possible sur la machine de Claude) :
contrôle de branche vert (0 problème), démarrage d'essai réussi (mêmes erreurs et avertissements connus que la veille).
Mis en ligne le 22/11 avec redémarrage : serveur démarré à 15 h 33 (heure UTC), aucune erreur Java après le démarrage,
copie des dessins prête (193 fichiers, 800 Ko, 103 Ko compressés) ; pack du launcher fabriqué et envoyé.

## 54. Objets de Noël réservés à la future caisse Noël (22/11)

Choix du propriétaire, juste après la mise en ligne de la section 53 : « une caisse Noël avec tous les trucs de Noël
dedans, retirés du reste ; ça nous fait notre première caisse d'événement préparée en avance ». Liste des objets de Noël :
`outils/donnees/noel.json` (8 camos, 5 couteaux, 3 finishers : Cadeau surprise, Sucre d'orge, Bonhomme de neige), lue par
`outils/caisses.py` et `outils/loot_armes.py`.

Ce qui change maintenant (mise en ligne avec redémarrage, choix du propriétaire) :
- caisses Camo, Événement, Gemme et coffres T7 : exactement comme avant la collection de Noël (fichiers identiques) ;
- caisse Vote : plus de Sucre d'orge ni de Bonhomme de neige ; caisse Quête : plus de Cadeau surprise (la part de leur
  groupe revient aux autres finishers du même groupe, les autres finishers restent dans leur caisse) ;
- Boutique 1.7.7 (liste de secours de la caisse Camo comme avant), Loot 0.20.12 (table T7 du jar comme avant).
Les objets déjà gagnés depuis 16 h 33 restent à leurs joueurs. La caisse Noël elle-même (sapin fixe, objets de Noël et
hérals, clés données par le staff le moment venu) viendra après validation de ses rendus.

À tester en jeu :
- [ ] aperçu des caisses Camo, Événement, Gemme : aucun objet de Noël ; Vote et Quête : plus les 3 finishers de Noël.

Pas testé : tout en jeu (outils des caisses et du loot : 0 problème ; fabrication et contrôles sur GitHub). Mis en ligne
le 22/11 avec redémarrage : serveur redémarré à 16 h 08 (heure UTC), aucune erreur Java après le démarrage.

## 55. Caisse de Noël : première caisse d'événement préparée en avance (22/11)

Choix du propriétaire : une caisse Noël avec tous les objets de Noël et des hérals ; modèle fixe (« pas de modèle animé,
les autres on a retiré les animations ») : un sapin avec étoile, guirlande, cadeaux au sol et neige. Rendus du sapin et
chances validés par le propriétaire (« oui good »), puis mise en ligne avec redémarrage.

- Caisse `heralizcrates:noel_crate`, clé `heralizcrates:noel_key` (identifiants figés dès la mise en ligne), Boutique 1.8.0.
  Modèle : `client/render/scene/crates/Sapin.java` (sapin à cinq étages enneigés, guirlande d'ampoules de cinq couleurs en
  festons, boules, étoile d'or en croix, six cadeaux enrubannés sur un tapis de neige, cadenas sur le grand cadeau rouge),
  textures `modules/boutique/outils/textures_noel.py`, formes de collision `outils/formes_caisses.py` (2,75 blocs de haut).
- Contenu (`outils/caisses.py`, `serveur/config/heralizcrates/noel.json`) : hérals 40 % (1 500 : 22 %, 3 000 : 12 %,
  6 000 : 6 %), finishers 20 % (Cadeau surprise 8 %, Sucre d'orge 8 %, Bonhomme de neige 4 %), camos 35 % (Pain d'Épices
  10 %, Flocon Royal et Pull de Noël 5 % chacun, les 5 légendaires 3 % chacun), couteaux 5 % (Sucre d'Orge et M9
  Guirlande 1,5 % chacun, Saphir Givré, Or Givré et Glace Sculptée 0,67 % chacun).
- Clés : seulement données par le staff (panneau du staff, onglet Économie, clés « Noël » ; ou
  `/heraliz givekey <joueur> noel <nombre>`) ; dans « Mes clés » de la boutique : « Donnée par le staff, lors
  d'événements ». La caisse se pose en jeu par le staff (bloc `heralizcrates:noel_crate`), au moment de l'événement.

À tester en jeu (après la mise en ligne) :
- [ ] caisse posée au spawn : le sapin comme sur les rendus, de jour et de nuit (ampoules et étoile lumineuses), sans
      saccade en arrivant ; on ne passe pas au travers ; icône de la caisse et de la clé dans l'inventaire ;
- [ ] panneau du staff : donner 1 clé de Noël à un joueur ; ouvrir (accroupi + clic droit) : la roue, un lot donné ;
      aperçu du contenu de la caisse : les 19 lots et leurs chances.

Pas testé : tout en jeu ; rendus hors jeu seulement (outil d'aperçu des caisses, sans la lumière du jeu). Sur GitHub :
contrôle de branche vert, démarrage d'essai réussi (mêmes erreurs connues que la veille). Mise en ligne le 22/11 avec
redémarrage : serveur redémarré à 16 h 34 (heure UTC), aucune erreur Java après le démarrage ; pack du launcher envoyé.

## 56. ClashWar : retouche des cartes en jeu (23/11)

Demande du propriétaire : pouvoir faire apparaître une carte du ClashWar, la corriger et la ré-enregistrer
(`/place template` n'existe pas sur le serveur). Plutôt qu'un fichier .schem de WorldEdit (qui peut perdre les blocs de
nos mods), trois commandes du ClashWar (op 2), ClashWar 1.4.0 (jar resté nommé 1.3.10 par erreur ; 1.4.1 ensuite) :

- `/clashwar carte poser <carte>` (cartes : `clashwar_1v1_depot`, `clashwar_2v2_kombinat`, `clashwar_3v3_port`,
  `clashwar_4v4_garnison`) : la carte apparaît dans le monde des arènes, réservée à la retouche (jamais prise par un
  match), et on y est emmené dès qu'elle est posée ;
- `/clashwar carte sauver`, debout dans la carte : enregistre la carte telle qu'elle est dans
  `heraliz_clashwar/cartes_retouchees/<carte>.nbt` du serveur (l'air et les murs invisibles ne sont pas gardés) ;
  cette version passe devant celle du mod. Les matchs la prennent **après le prochain redémarrage** (les arènes déjà
  posées de cette carte sont reposées au démarrage). Pour revenir à l'ancienne carte : supprimer ce fichier sur le Ptero ;
- `/clashwar carte quitter` : retour au spawn.

À tester en jeu (après la mise en ligne, en créatif) :
- [ ] `/clashwar carte poser clashwar_1v1_depot` : on arrive dans la carte ; on casse/pose quelques blocs (dont des
      blocs de nos mods : décors, cfm) ; `/clashwar carte sauver` : message « Carte … sauvée (… blocs) » ;
- [ ] `/clashwar carte quitter` : retour au spawn ; après un redémarrage, un match sur cette carte montre bien les
      retouches (et les murs invisibles autour sont toujours là).

Pas testé : tout en jeu. Les retouches sauvées ne sont que sur le Ptero (pas dans GitHub) : les envoyer à Claude si on
veut les garder dans le mod pour de bon. Sur GitHub : contrôle de branche vert, démarrage d'essai réussi. Mise en
ligne le 23/11 avec redémarrage (accord du propriétaire) : serveur reparti à 21 h 02 (heure UTC), aucune erreur Java
dans le journal.

## 57. Audit final avant l'ouverture (23/11, 6 relectures du code + tri de cette fiche)

Demande du propriétaire : « quel bug ferait qu'on doive fermer le serveur ? ». Six relectures (bases et clans ; loot,
bunker et événements ; économie ; armes, sacs, combat, réanimation ; monde, anticheat, réglages et mise en ligne ;
ClashWar et liens entre mods). Aucune duplication, perte d'inventaire ou plantage trouvé dans le code de la semaine.

Corrigé (en ligne le 23/11 avec redémarrage, serveur reparti à 23 h 41 heure UTC, aucune erreur Java dans le journal) :
- ClashWar 1.4.1, retouche des cartes : re-poser une carte déjà ouverte en retouche y ramène (avant : une copie neuve
  d'après l'ancienne carte, et la sauver effaçait les retouches) ; `sauver` ne lit plus l'air (la 4v4 aurait figé le
  serveur plusieurs secondes) ; fichier écrit sur le disque puis relu avant de remplacer l'ancien ; une carte retouchée
  illisible, d'une autre taille ou faite d'après une carte du mod qui a changé depuis est ignorée (erreur au journal,
  carte du mod utilisée) au lieu de rendre la carte injouable ; `poser`/`quitter` refusés en match ou en regardant un
  match, `poser` refusé pendant des matchs.
- Bases (CRac 1.38.13-beta) : un membre sans le droit « Ouvrir les coffres » qui quittait son clan dans la base pouvait
  vider les coffres ; pendant 10 min après son départ (ou son exclusion), il garde les refus d'un membre sans droit.

Choix du propriétaire (23/11) :
- anti-bot d'AuthMe coupé (`serveur/plugins/AuthMe/config.yml`, `enableAntiBot: false`) : 11 nouveaux joueurs à moins
  de 5 s d'écart fermaient le serveur aux nouveaux pendant 10 min ;
- réseau du serveur sur 4 fils (`serveur/spigot.yml`, `netty-threads: 4` ; -1 n'en donnait qu'un) ;
- « ne pas casser les blocs de la map avec les explosifs, uniquement les blocs de base, la table TaCZ, les coffres » :
  `raidBreaksBlocks = false` (`crac-server.toml`) ; les objets à points de vie (coffres, table TaCZ, fours…) ne prennent
  des dégâts que s'ils ont été posés par un joueur dans une base ; le wipe et `/base effacer` ne retirent plus que
  ceux-là (avant : aussi les tonneaux, fours, feux de camp de la carte recouverts par la zone d'une base).

Connu, laissé : hérals d'une caisse perdus si le compte est près du plafond de 10 millions.

À tester en jeu :
- [ ] `/clashwar carte poser clashwar_1v1_depot`, retouche, `sauver`, `quitter`, puis `poser` de nouveau : les
      retouches sont là ;
- [ ] une recrue sans le droit « Ouvrir les coffres » tape `/clan quitter` dans la base : les coffres restent refusés
      10 min.
- [ ] raid au C4 : les murs et portes de base cassent, les coffres et la table TaCZ posés dans la base prennent des
      dégâts ; un C4 collé à un arbre, un mur ou un tonneau de la carte dans la zone d'une base ne casse rien ;
- [ ] 12 comptes jamais inscrits se connectent en 30 s : tous entrent ; journal : « Using 4 threads for Netty ».

Pas testé : tout en jeu.

## 58. Inventaire : sac à dos en haut (25/11)

Demande du propriétaire : « inverser le sac et l'inventaire : mettre le sac en haut et l'inventaire plus bas ». Dans
l'écran d'inventaire (touche E), à droite : la carte du sac à dos (avec « Ouvrir ») est maintenant en haut, puis « Sur
toi » (27 cases) et « Accès rapide » en dessous ; mêmes écarts qu'avant, seul l'ordre change (Équipement 1.4.3,
`armory/client/curios/EcranInventaire`). Aperçu fait à partir de la capture du propriétaire.

À tester en jeu :
- [ ] touche E : sac en haut, « Sur toi » puis « Accès rapide » en dessous, rien ne se chevauche ;
- [ ] avec un sac porté : bouton « Ouvrir » au bon endroit et qui marche ; Maj + clic, glisser, touches 1 à 9 entre
      « Sur toi » et la barre comme avant.

Pas testé : tout en jeu.

## 59. Planque à 2 lignes, table de craft (25/11)

Demandes du propriétaire (à mettre en ligne avec les autres petites modifs, un seul redémarrage) :
- **Planque** (coffre personnel du spawn, « Safe Planque », comme le coffre de l'Ender) : **2 lignes (18 cases) au moins
  pour tout le monde** (avant : 1). Réglage « Planque » des grades : minimum et valeur de base passés à 2
  (`PermsGrades.PLANQUE`, Boutique 1.8.1) ; un grade réglé plus haut garde son nombre ; un grade réglé à 1 ou 2 ne
  donne plus d'avantage (à revoir dans l'éditeur des grades si un grade payant avait 2). Sans le mod des grades : 2 aussi
  (`CoffresPersonnels`, Monde 1.3.14).
- **Table de craft** : déjà bloquée depuis le 14/11 (`CraftReserve`, réglage `craft_fondateurs_seulement`) : seul le
  grade Fondateur l'ouvre ; tous les autres, staff et créatif compris, lisent « Ce poste est hors service. », et
  aucune fabrication (grille 2 × 2 de l'inventaire comprise). Rien changé.

À tester en jeu :
- [ ] un joueur sans grade ouvre la planque : 2 lignes ; ce qu'il y avait dans la 1re ligne est toujours là ;
- [ ] un compte sans le grade Fondateur fait clic droit sur une table de craft : « Ce poste est hors service. ».

Pas testé : tout en jeu.

## 60. ClashWar lancé par un admin seulement (25/11)

Demande du propriétaire : « le ClashWar ne doit plus être automatique, c'est un admin qui le lance ; même lancé par un
admin, il faut donner les récompenses comme si c'était automatique ».
- Nouveau réglage `automatique` de `serveur/heraliz_clashwar/clashwar.json`, mis à **false** : plus d'annonce 15 min
  avant ni d'ouverture tout seul le mercredi et le dimanche (`Tournois.planifier`) ; aucune date de prochaine édition
  montrée : le tableau du spawn dit « Annoncé par le staff » (ClashWar 1.4.2). Remettre `true` (puis
  `/recharger clashwar`) rend le planning d'avant.
- Lancer : `/clashwar lancer [format] [minutes]` (op 2) : inscriptions ouvertes tout de suite (10 min par défaut ; format
  au choix, sinon le suivant de la rotation), annonce à tous les joueurs comme avant.
- Récompenses : une édition lancée par un admin était déjà une vraie édition (seules les simulations du banc n'en
  donnent pas) : mêmes primes, clés, points de saison, avec les mêmes règles (3 clans au moins pour les primes, 4 pour les
  points de saison). Rien changé de ce côté.

À tester en jeu :
- [ ] mercredi ou dimanche à 20 h 15 / 20 h 30 : rien ne se lance tout seul ; tableau du spawn : « Annoncé par le staff » ;
- [ ] `/clashwar lancer 2v2 5` : annonce, inscriptions 5 min, matchs ; avec 3 clans ou plus : primes et clés données
      une seule fois ; points dans l'onglet Saison (4 clans ou plus).

Pas testé : tout en jeu.
