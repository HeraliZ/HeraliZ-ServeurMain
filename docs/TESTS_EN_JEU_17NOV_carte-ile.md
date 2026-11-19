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
      4 munitions (.45 ACP, 9 mm, calibre 12, 7,62 × 39), chaque nom à la couleur de sa rareté.
- [ ] Prix pendant la bêta (prix des fichiers x 0,75) : pistolet gris 1 125, pistolet vert 2 250, pistolet-mitrailleur ou
      fusil à pompe vert 3 000, Mk 23 5 250, UMP-45 et M870 6 750, SKS 7 500 ; balles : 9 mm et .45 ACP 8, calibre 12 11,
      7,62 × 39 15.
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

Rien de tout cela n'a été essayé en jeu ni sur le Ptero : seulement contrôlé dans le dépôt, et le démarrage d'essai a
tourné sur une machine de GitHub. Tout compte au **redémarrage** qui suit la mise en ligne. Détail et raisons :
`docs/DEPLOIEMENT.md`, sections 6 à 9.

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
      attirent toujours ; dans les rapports `/diag`, beaucoup moins d'entités `zombieawareness:scent`.
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
