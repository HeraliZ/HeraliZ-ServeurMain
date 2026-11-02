# Devlog et essais — du 25/10 au 02/11/2026

- **Période** : du 25/10 au 02/11/2026, environ 345 changements (nouveau projet propre le 26/10).
- **En ligne** : `main` au changement `740bccff`, mis en ligne le 02/11 (Noyau 1.0.13, Spartan 2.10.1, ClashWar 1.1.1,
  Bases 1.30.1-beta, launcher 1.16.0 dans le pack).
- **Prêt, pas encore en ligne** (branche de travail, à envoyer à la prochaine mise en ligne) :
  - « démarrage propre » : ClashWar 1.1.2, Spartan 2.10.2, Noyau 1.0.14 (console propre au démarrage, moins de carte
    chargée pour rien) ;
  - `server.properties` du dépôt aligné sur celui du Ptero (160 places, vue 6, simulation 4), utile seulement pour une
    installation complète ; le message de la liste des serveurs et la liste blanche attendent ton choix ;
  - empreinte du jar AzLink figée (la mise en ligne refusera tout autre jar) ;
  - tâche GitHub « Lenteurs du serveur » : affiche aussi les fichiers de réglage du Ptero (lecture seule, secrets masqués) ;
  - launcher 1.16.1 : onglet Boutique simplifié, un seul bouton au centre qui ouvre la boutique du site.
- **Rien n'a été testé en jeu** : la partie 2 est la liste de ce qu'il faut vérifier. Les choses retirées en cours de
  route (mode Zombies, matchs autres que ClashWar, spark, boss, console admin de CRac…) n'y sont pas.

---

## 1. Devlog (pour les joueurs)

### Armes et combat
- Les fusils à pompe font enfin leurs vrais dégâts : deux décharges bien placées à bout portant tuent un joueur sans
  armure (jamais une seule) ; il en faut trois contre une armure Rare.
- Les tirs à la tête comptent vraiment : la tête fait plus mal que le corps. Un seul plomb dans la tête ne fait plus
  ignorer le gilet aux autres plombs.
- Plus aucun tir ni coup à travers une porte fermée ou un mur ; à l'écran, la balle s'arrête net sur le mur.
- Les silencieux servent enfin : plus de trait lumineux derrière la balle, et le tir ne s'entend clairement que de près
  (10 blocs) ; plus loin, on devine seulement la direction.
- Tirs à longue distance fiables, même quand le serveur est chargé ; fini les « balles fantômes » qui touchaient
  quelqu'un des minutes après.
- Une arme trouvée a maintenant la moitié de son chargeur (au lieu d'un dixième).
- Icônes des armes refaites à partir de leur vrai modèle 3D ; les armes sans crosse (Glock 18, TEC-9, 9A-91, Vector,
  Scorpion EVO 3, PP-19, fusils à canon scié) n'en ont plus sur leur icône.
- Les armes de mêlée à vie ne s'usent plus et ne cassent plus jamais ; battes, haches et couteaux animés font deux fois
  moins de dégâts (l'infobulle le montre).
- Retirés : les armes et les camos « Pack-a-Punch » de l'ancien mode Zombies, l'accessoire « Glock 54 Integrated Laser
  Battery » ; le M82A2, le Vector Red Moon et le Vector Enigma ne sortent plus du loot.
- Plus de main gauche (la case a disparu de l'inventaire) ; F3 + B ne montre plus rien en survie ; étoiles des couteaux
  CS2 revues.

### Soins et survie
- Soins beaucoup plus rapides : 1,5 s pour les petits soins, 2,5 s pour le bandage et l'AI-2, 3 s pour la poche de
  sang, 3,5 s au plus pour l'attelle, la Surv12 et la CMS ; lait concentré 1,6 s.
- Nouveaux Bandage et Attelle, chacun avec son animation. Le kit de départ contient une attelle.
- La faim descend environ trois fois moins vite qu'en Minecraft normal, et reste pleine au spawn et au tutoriel.
- Jambe cassée : on frappe de nouveau normalement (la lenteur reste).
- Manger, se soigner ou ouvrir une porte en visant un mur, le sol ou la porte, objet ou arme en main : ça marche enfin.
- À la mort : « Au camp » (spawn) ou « À la base » (devant l'armoire de ton clan, une fois toutes les 8 min), écran plus
  grand et plus net. Un cadavre vidé disparaît aussitôt. `/suicide` : une fois toutes les 5 min.
- Les objets à vie ne se perdent plus jamais : déconnexion avec un écran ouvert, mort avec l'objet dans le sac,
  inventaire plein… Jeté au sol et oublié, il revient à son propriétaire au bout de 5 min.
- Un sac qui contient quelque chose ne s'enlève plus du dos : il faut le vider d'abord.
- Réanimation : le compte à rebours reste visible jusqu'au bout.

### Loot et zombies
- Les zombies lâchent du butin (environ 8 sur 10), un peu meilleur sur les zombies militaires ; rien sur un zombie
  créé exprès (anti-farm). Leur nourriture est bonne, pourrie ou périmée, un tiers chacune.
- Les zombies ne ramassent plus les armes ni les armures laissées au sol.
- Crâne de cristal, sceptre et calice (objets Uniques) dans les coffres T7 : environ un coffre sur 50.
- Sacs Épiques et Légendaires bien plus rares ; un conteneur haut de gamme isolé n'est plus plein à chaque passage.
- Les conteneurs pleins scintillent (étoiles blanches, dorées en T7) ; on ne peut plus rien y déposer.
- Conteneurs dessinés plus légèrement (18 % de faces en moins) : plus d'images par seconde en ville.
- La fusée d'extraction se trouve, rarement, dans les T5 à T7.
- Zombies bien plus légers pour le serveur : ils se massent sous un joueur hors d'atteinte sans faire ramer, et
  partent vers un tir une fraction de seconde après.

### Carte, extractions et événements
- 6 extractions ouvertes en même temps, changées toutes les 30 min ; le menu Échap montre les 3 plus proches.
- **Largage militaire public** toutes les 3 h 30 (au moins 4 joueurs sur la carte) : annonce, avion, caisse en
  parachute avec fumée ; objets révélés un par un comme un coffre T7 ; +8 points de saison au clan qui l'ouvre en
  premier. Une caisse où personne ne va disparaît 20 min après. Jamais dans une zone VIP.
- **Hélicoptère militaire** : passe toutes les 4 à 6 h (au moins 10 joueurs sur la carte), n'attaque que ceux qui lui
  tirent dessus. Abattu, il s'écrase, brûle, puis laisse un coffre T7 avec sa boîte noire (400 hérals chez Mila et
  +12 points de saison).
- **Objets d'événement** (dossier classé, mallette scellée, disque dur militaire) : à revendre à Mila 120 / 300 /
  700 hérals, +2 / +4 / +8 points de saison.
- Pendant un ClashWar, ni largage ni hélicoptère ; ils reprennent dès la fin du tournoi.
- Frappes aériennes : jumelles Légendaires, munitions à 2 frappes, aucun bloc cassé ni feu, personne touché sous un
  toit ; avion, bombes et caisse de largage se déplacent sans à-coups.
- La carte est figée : herbe, feuilles, neige, glace et cultures ne changent plus toutes seules.
- Pas de véhicule au spawn ni au tutoriel ; la fusée d'extraction est refusée hors de la carte (sans être consommée).
- Le temps du Bonus de loot ne s'use plus que sur la carte, et s'arrête après 3 min sans bouger.

### Clans et saison
- La saison démarre toute seule ; classement des clans dans l'onglet Saison (touche K).
- Niveaux de clan beaucoup plus durs (3 600 XP pour le niveau 2), XP plafonnée par jour et par source.
- Membres : 4 au départ, puis 5 au niveau 2, 6 au 5, 7 au 10, 8 au 15, sans rien acheter. L'ancienne amélioration
  « Membres » est remboursée.
- Capture des zones : à partir du niveau 5 avec l'amélioration Territoires, 2 zones au plus ; une zone est tenue 2 h,
  puis redevient libre.
- Dons au clan (`/clan don`, au spawn seulement) : Épique 150 XP, Légendaire 1 000, Unique 2 500 ; une copie est refusée.
- Points de saison : kills plafonnés à 40 points par clan et par jour ; les quêtes de Varga entrent dans le plafond des
  petites actions (50 points par joueur et par jour).
- Quêtes de clan réparées : « Fouiller des caches » avance enfin ; retirer puis redéposer des hérals ne compte plus ;
  tuer ses propres comptes ne compte plus.
- Nouveau droit « Se téléporter à la base » ; losange vert au-dessus des membres de ton clan ; petite icône pendant la
  Purge.

### Bases
- Coffres par rareté : malle de chantier, casier, caisse d'armes, coffre-fort (45 à 78 cases) ; la caisse en métal
  passe à 18 cases.
- 9 caisses en métal dans une fondation fermée (1 seule avant) ; caisses et coffres se collent aux murs.
- Table d'armurier à fabriquer et à poser dans sa base (une par base).
- Marteau : ramasse un objet vide, retire une vitre, déplace l'armoire dans les 15 min après sa pose ; on améliore à
  4 blocs au plus.
- Fenêtres plus larges, vitres transparentes, meurtrière sur la porte blindée ; demi-mur de 1,25 bloc, montées plus
  faciles, on passe accroupi sous un plafond bas ; rampes ouvertes, sans poteaux.
- Toits et rampe triangle retirés du plan (ceux déjà posés restent). Les pièces carrées se posent toujours droites.
- Zones où l'on ne construit pas affichées en rouge, plan en main. Dans une base, seuls les objets de construction se
  posent.
- Plus de protection automatique des nouvelles bases : pendant la Purge, toute base se raide.
- Un pilleur ne peut plus prendre un objet à vie dans un coffre de base ; coffre détruit : l'objet va au chef du clan.
- Tutoriel des bases dans un monde à part, une parcelle de 64 × 64 par joueur.

### Économie : hérals, hôtel des ventes, recycleurs, casino, boutique
- **Hôtel des ventes** (`/hdv`, ou le commissaire-priseur au spawn) : prix en hérals obligatoire, prix en PB en plus si
  tu veux ; taxe de mise en vente de 12,5 % ; un achat en PB coûte 10 % de plus (100 PB → 110).
- Recycleur du spawn : on recycle à l'unité, les caisses de stockage vides se recyclent, plus rien n'est perdu à la
  déconnexion.
- **Recycleur de terrain** sur la carte : machine partagée comme un coffre, 10 % de chances en plus, utilisable en combat.
- Mila rachète une arme selon son état (10 % du prix au moins).
- Prix : cœur de clan 5 000 hérals chez le Bâtisseur, caisse en métal 300, armoire 250 ; Jack ×4 ; lait concentré et
  vaseline 90 hérals chez Jacqueline.
- Table d'armurier : munitions avec un bocal de poudre plus fort (jusqu'à 60 balles de 9 mm d'un coup), onglet Divers
  (cartes d'accès, jumelles, munitions de frappe, C4, barricade) ; les cartes d'accès ne se font plus qu'à la table.
- **Quêtes du jour** (touche J) : une difficulté, une quête parmi trois, une récompense tirée ; à rendre au spawn.
- `/home` et `/tpa` : une fois toutes les 8 min (7 min 30, 7 min, 6 min 30 selon le grade).
- **Casino** : compte de capsules et borne ATM ; les gains vont directement sur le compte ; grades à la journée (10 000 /
  18 000 / 30 000 capsules) ; jackpot nourri par 1 % des mises des machines à sous ; Main du Joker seulement au casino.
- Caisses : les clés sont sur un compte de clés ; une récompense qui ne tient pas dans l'inventaire t'attend au lieu de
  tomber par terre. Seul « Coup de grâce » est un finisher gratuit.
- Kit de réparation : clic droit en main pour ouvrir l'atelier ; l'arme posée dedans n'est plus perdue.

### Interface
- Chat en verre avec onglets Proximité (48 blocs), Global et Clan.
- Munitions en bas à droite (« 27 / 120 · AUTO ») ; notifications en haut à gauche (extractions, largages).
- Menu Échap : extractions proches et bouton Boutique.
- Écran « Informations » à jour ; après un redémarrage, la reconnexion automatique montre la vraie raison d'un refus.
- Tutoriel du spawn : visite guidée (le Guide, puis Varga), traînée verte au sol, 7 questions à Varga.
- `/canal` remplace `/ping canal` ; la liste des commandes (touche Tab) est bien plus courte ; textes en français,
  anglais et russe, annonce de redémarrage comprise.
- Plus de sons d'ambiance en double ; le menu de MAtmos est retiré (le volume se règle dans les options du jeu).

### Anticheat, launcher et site
- Anticheat : seul le jeu officiel entre ; message « Jeu validé » ; le Gardien du launcher est vérifié par le serveur ;
  nouvelles alertes pour le staff (vitesse, faux lag, tirs suspects), sans gêner un joueur honnête.
- Launcher (1.14 → 1.16) : vérifie le jeu, Java et chaque fichier avant chaque partie, se répare seul, efface les
  anciennes versions des mods, onglet Anticheat, anti-robot à la création de compte ; à venir : onglet Boutique simplifié.
- Téléchargement plus léger : environ 72 Mo de moins (−15 %).
- Site : votes protégés contre les robots (compte connecté, Discord lié), plus besoin d'avoir joué pour voter ; adresses
  IP effacées au bout d'un an ; page Confidentialité à jour.

### Performances
- Au banc de charge à **150 joueurs**, le serveur met en moyenne **44,5 ms** par tour de calcul au lieu de **135 ms**
  (sous la barre des 50 ms, il tourne à pleine vitesse).
- Plus de gel au départ du spawn, à `/spawn`, `/home`, `/rtp`, au retour d'extraction, au tutoriel et à la connexion.
  Zombies, balles, bases, hérals, quêtes, anticheat : tout est allégé ; côté joueurs, conteneurs, stickers et skins aussi.

---

## 2. À regarder en jeu (pour l'équipe)

Serveur et joueurs à jour en même temps (relancer le launcher). Cocher au fur et à mesure ; noter ce qui ne va pas
(capture d'écran + langue du jeu). Fiches détaillées : `docs/TESTS_EN_JEU_*.md`.

### 2.0 En priorité avant d'ouvrir
- [ ] **Démarrage** : le serveur démarre sans erreur rouge dans la console ; un joueur lance le jeu par le launcher sans
      plantage.
- [ ] **Connexion** : plusieurs joueurs se connectent à la suite (une erreur bloquait toute connexion au banc du 02/11,
      corrigée dans `740bccff`).
- [ ] **Connexion d'un joueur resté sur la carte** : `/login` (et un `/captcha` ou un code de double authentification)
      passe ; sinon il reste bloqué dehors.
- [ ] **Launcher 1.16** : « Vérification des fichiers du jeu… » puis partie normale ; « Jeu validé » dans le fil Discord du
      joueur ; aucune alerte « Gardien » en 10 min de jeu.
- [ ] **Mort et réapparition** : « Au camp » → spawn ; « À la base » → devant l'armoire, puis « Disponible dans 7:5x » ;
      se déconnecter sur l'écran de mort et revenir : le bon temps s'affiche.
- [ ] **Objets à vie jamais perdus** (couteau ou skin à vie, en survie) : quitter (Alt+F4) avec l'objet au curseur,
      inventaire, écran Curios, coffre, sac ou recycleur ouvert → il est là au retour ; mourir avec l'objet dans le sac
      porté → rendu à la réapparition ; inventaire plein → « gardé », puis rendu dès qu'une case se libère ; jeté au sol
      → revient au bout de 5 min, ne brûle pas dans la lave.
- [ ] **Armes de mêlée à vie** : frapper zombies et joueurs avec une batte, une machette : plus de barre d'usure, ne casse
      jamais.
- [ ] **Tirs à la tête** : sur un joueur sans armure, la tête fait plus que le corps (fusil à pompe aussi) ; avec un bon
      gilet, un plomb à la tête n'annule plus le gilet pour les autres plombs.
- [ ] **Casino → ATM** : gagner à une machine à sous, au crash, à la roulette, au blackjack : rien dans l'inventaire, le
      compte de capsules monte ; miser avec 0 capsule sur soi mais un compte rempli : ça marche ; se déconnecter en pleine
      partie gagnante : gain sur le compte au retour.
- [ ] **Recycleur du spawn** : 6 armes dedans, Alt+F4 (puis pareil avec `/redemarrage`) → tout est dans l'inventaire au
      retour ; inventaire plein → « ils t'attendent dans le recycleur » ; un objet à vie ou de quête n'entre pas.
- [ ] **Largage et hélicoptère après un ClashWar** : finir ou annuler un ClashWar, puis `/event lancer largage
      avec_points` → il part ; pendant les inscriptions ou les matchs, refusé.
- [ ] **Quêtes de clan** : « Fouiller N caches » avance à chaque premier conteneur fouillé ; « Déposer N hérals » : retirer
      puis redéposer ne fait pas avancer ; tuer un compte de la même connexion internet ne compte pas.
- [ ] **Atelier de réparation** : poser une arme, se déconnecter → elle est dans l'inventaire au retour.
- [ ] **Conteneurs de loot** : on prend tout, mais rien n'entre (clic, glisser, Maj + clic, touches 1 à 9).
- [ ] **Balles** : après un long échange de tirs, plus aucune traçante qui part de nulle part ; tirs à 60, 90, 120 blocs
      qui touchent tout de suite, même quand le serveur rame.
- [ ] **Commandes** : en combat, `/logout` refusé, `/msg` et `/r` passent ; sur la carte, `/boutique` répond « seulement
      au spawn ».
- [ ] **Hérals** : dépôt et retrait à la banque du clan, arrêt brutal juste après (serveur d'essai) → soldes justes au
      redémarrage, rien en double.
- [ ] **Démarrage propre** (prochaine mise en ligne) : plus de lignes « bloc(s) inconnu(s) » des arènes ni de rapport de
      blocage de Spartan ; une fois : « 13 tronçon(s) forcé(s) retiré(s) » ; puis, spawn vide quelques minutes :
      première connexion, « Au camp », extraction et `/spawn` arrivent sans gel ; PNJ, casino et borne se chargent en
      moins d'une seconde, rien en double (un seul Varga, deux videurs par entrée).

### 2.1 Armes et combat
- [ ] Fusil à pompe sans armure : deux décharges de près tuent (jamais une) ; armure Rare : trois ; à 15-20 blocs, moins
      de dégâts ; infobulle du M870 : 36 ou « 4 × 9 ».
- [ ] Porte fermée ou mur entre deux joueurs collés : ni balle ni coup ne passe ; porte ouverte ou fenêtre : ça passe.
      Un zombie collé à une porte fermée ne frappe plus derrière.
- [ ] Silencieux : plus de trait lumineux (tireur et autres) ; son net à moins de 10 blocs, flou au-delà.
- [ ] Arme trouvée (conteneur, largage, zombie militaire) : chargeur à moitié plein ; double canon : 1 balle.
- [ ] Icônes : Glock 18, TEC-9, 9A-91, Vector 9 mm, EVO 3, PP-19 et les deux canons sciés sans crosse ; les autres
      inchangées.
- [ ] Glock 54 Integrated Laser Battery absent de la table, de Jack et du loot ; Vector Red Moon et Enigma jamais dans
      un T7 ; aucune arme du mode Zombies dans le menu des armes.
- [ ] Battes, haches, couteaux animés : dégâts divisés par deux (infobulle comprise) ; armes à feu inchangées.
- [ ] Plus de case de main gauche (sauf en créatif) ; F3 + B ne montre rien en survie, marche en créatif.
- [ ] Tous les skins s'affichent (Gold, Dark Matter, Séraphin, un couteau Doppler) ; stickers bien collés pendant un
      rechargement ; aucun camo « Pack-a-Punch ».

### 2.2 Soins et survie
- [ ] Chaque soin va au bout de son animation (surtout CMS et Surv12), le rond autour du viseur suit la nouvelle durée,
      l'effet arrive à la fin ; changer de soin dans la même case pendant le soin : il s'arrête.
- [ ] Manger une conserve ou se soigner en regardant un mur ou le sol : l'objet est consommé, l'effet arrive.
- [ ] Porte en bois, trappe, portillon : s'ouvrent avec nourriture, soin, batte ou pistolet en main ; porte d'un autre
      clan toujours verrouillée ; porte du bunker avec un pistolet en main : s'ouvre.
- [ ] Kit de départ : une attelle en plus, qui soigne la jambe cassée ; faim lente sur la carte, pleine au spawn.
- [ ] Jambe cassée : on frappe normalement, lenteur toujours là.
- [ ] Cadavre vidé : disparaît tout de suite. `/suicide` : 5 min entre deux, même après une déconnexion.
- [ ] Sac non vide : ne s'enlève pas du dos (infobulle « Vide le sac… ») ; un sac plein sur un corps se ramasse ;
      à la fouille d'un joueur à terre, un couteau à vie rangé dans son sac reste à lui.
- [ ] Réanimation : le temps descend jusqu'à 0, puis la coche ; interrompue : le temps disparaît.

### 2.3 Loot et zombies
- [ ] Zombies de la carte : environ 8 sur 10 lâchent quelque chose ; un zombie créé par commande : rien ; nourriture
      bonne, pourrie ou périmée, un tiers chacune ; composant Épique toujours seul.
- [ ] Jeter une arme près d'un zombie : il ne la prend plus.
- [ ] Coffres T7 : de temps en temps un crâne de cristal, un sceptre ou un calice (révélé en rouge, le plus long) ;
      Mila le rachète, le recycleur le refuse, `/clan don` donne 2 500 XP.
- [ ] Sacs trouvés : beaucoup moins d'Épiques et de Légendaires, aucun sac Commun.
- [ ] Conteneur T5 ou T6 isolé : vidé, il ne se remplit plus à chaque passage.
- [ ] Conteneurs pleins : étoiles visibles même avec « Particules : minimales », parties en moins de 2 s une fois vidé.
- [ ] Conteneurs allégés : de près, pas de trou, rien qui clignote ni qui flotte ; plus d'images par seconde en ville.
- [ ] Zombies : un joueur sur un toit ou derrière une clôture → ils se massent dessous sans se figer ; tirs en ville :
      ils arrivent ; le sang attire toujours ; partir loin puis revenir : pas d'ancienne horde en plus.

### 2.4 Carte, extractions, événements
- [ ] Extractions : 6 zones ouvertes, changées toutes les 30 min, loin les unes des autres ; notification complète en
      haut à gauche (3 langues) ; menu Échap : les 3 plus proches ; `/extractions` : les 6.
- [ ] Largage (`/event lancer largage avec_points`) : annonce quelques secondes après, sans à-coup ; avion, caisse en
      parachute sur un sol dégagé ; ouverture façon T7 ; +8 points au premier clan ; objet d'événement dedans ; un staff
      en créatif qui l'ouvre ne prend pas les points.
- [ ] Largage où personne ne va : plus de caisse ni de fumée au bout de 20 min ; jamais dans une zone VIP ; pas de terrain
      neuf autour de la carte.
- [ ] Hélicoptère (`/event helico lancer ici`) : vol, son de loin, sur la carte M ; n'attaque que celui qui lui tire
      dessus ; abattu : crash, feu, coffre T7 avec la boîte noire (400 hérals, +12 points s'il est lancé avec points).
- [ ] Objets d'événement chez Mila : 120 / 300 / 700 hérals et +2 / +4 / +8 points ; sans clan ou sans cœur : « aucun
      point » ; une copie est refusée.
- [ ] Frappe aérienne : aucun bloc cassé ni feu, personne touché sous un toit ; avion et bombes fluides, explosion au
      contact du sol ; refusée au spawn, en zone VIP et en match.
- [ ] Borne de départ : arrivée sur un sol sûr ; si « Départ imminent : ne bouge pas. » s'affiche, bouger annule.
- [ ] Au spawn et au tutoriel : fusée d'extraction refusée (« ERR: hors de la carte », pas consommée), véhicule refusé.
- [ ] Bonus de loot : rien ne s'use au spawn ni après 3 min immobile ; sur la carte, le temps baisse normalement.
- [ ] Carte figée : herbe, feuilles et neige ne bougent plus après quelques minutes au même endroit.
- [ ] Portes du bunker : aller-retour carte ↔ spawn une dizaine de fois, la mémoire du jeu (F3) ne monte pas sans fin.

### 2.5 Clans et saison
- [ ] Onglet Saison (K) : classement, mon clan surligné, barème avec « 40 points par jour au plus » (kills) et « plafond
      commun de 50 points par jour » (quêtes de Varga) ; rien ne déborde en anglais et en russe.
- [ ] Clan neuf : « encore 3 600 XP » pour le niveau 2 ; zombies plafonnés à 400 XP par jour avec un message ; infobulles
      des plafonds dans l'onglet Niveau.
- [ ] Membres : 4 au niveau 1 (le 5e est refusé), puis 5, 6, 7, 8 aux niveaux 2, 5, 10, 15 ; ligne « Membres » absente
      de l'arbre ; remboursement des clans qui l'avaient achetée (points et hérals, une seule fois).
- [ ] Capture : clan niveau 1 à 4 → « Capture verrouillée » ; niveau 5 + Territoires → 1 zone, niveau 10 → 2 ; zone tenue
      2 h puis libre (message, HUD, carte M).
- [ ] `/clan don` : refusé sur la carte ; au spawn, Épique 150 / Légendaire 1 000 / Unique 2 500 XP ; au-delà de 3 000 XP
      dans la journée, confirmation ; copie refusée.
- [ ] Kills : au-delà de 40 points dans la journée, plus rien pour le clan du tueur (la victime perd toujours) ; droit
      « Se téléporter à la base » décoché : plus de `/home` ni de réapparition à la base pour ce membre.
- [ ] Losange vert au-dessus des membres de son clan (pas à travers les murs, caché avec F1) ; icône de la Purge.

### 2.6 Bases
- [ ] Fondation fermée : 9 caisses en métal (angles, milieux des murs, centre), rien ne dépasse dehors, rien ne
      clignote ; ramasser ou faire sauter une caisse : le mur se rebouche.
- [ ] Coffres par rareté : posés contre un mur, s'ouvrent au bon endroit, gardent leur contenu ; limites par base
      (`/coffres voir`).
- [ ] Table d'armurier de base : se pose dans sa base (une seule), même écran que celle du spawn, reste après un
      redémarrage, prend les dégâts d'un raid.
- [ ] Marteau : ramasse un objet vide (refuse s'il est plein), retire une vitre, reprend l'armoire posée depuis moins de
      15 min ; amélioration seulement à 4 blocs.
- [ ] Pièce carrée contre un côté en biais d'une fondation triangle : refusée (seul un triangle s'y pose) ; ailleurs,
      toujours droite.
- [ ] Demi-mur, plancher, montées : on saute dessus en courant ; accroupi, on passe sous un plafond bas ; pas d'alerte
      de l'anticheat.
- [ ] Fenêtres et vitres : on voit à travers, balles et joueurs arrêtés ; un joueur derrière une fenêtre reste visible.
- [ ] Plan en main près d'une ville ou des rails : cases interdites en rouge ; refus dans une zone interdite.
- [ ] Purge (`/purge forcer 30`) : un C4 abîme une base toute neuve ; hors Purge, les explosifs ne font rien.
- [ ] Pilleur devant un coffre avec un couteau à vie : il voit le couteau mais ne peut pas le prendre ; coffre détruit au
      C4 : le couteau va au chef du clan.
- [ ] Sauvegarde : abîmer, améliorer, ouvrir des portes, poser une vitre, `/save-all`, redémarrer : tout est comme avant.
- [ ] Tutoriel des bases : seul sur sa parcelle, murs invisibles sur les 4 côtés, fin au spawn ; Varga parle de `/home`, plus
      de « /home poser ».
- [ ] `/rtp` et `/home` : arrivée normale ; si « Préparation de l'arrivée… » s'affiche, l'arrivée suit (3 s au plus).

### 2.7 Économie
- [ ] Hôtel des ventes : vente à 10 000 hérals → taxe de 1 250 hérals, confirmée en deux clics, jamais rendue ; achat
      à 100 PB → 110 PB payés, 100 au vendeur ; refus hors du spawn, en combat, à terre ; double clic : une seule vente.
- [ ] Recycleur à l'unité : 1 balle (« 1 sur 15 »), 15 balles (« Paquet de 15 ») ; 6 roquettes se recyclent ; une
      caisse de stockage vide se recycle.
- [ ] Recycleur de terrain : posé par le staff, partagé entre deux joueurs, +10 % affiché, ouvert en combat ; le
      déposant reçoit les hérals « rien » même déconnecté ; 4 clics par seconde au plus.
- [ ] Mila : arme neuve au même prix qu'avant, arme à 50 % à moitié prix, à 0 % à 10 %.
- [ ] Bâtisseur : cœur de clan 5 000, caisse en métal 300 ; Jacqueline : lait concentré et vaseline 90 ; Jack :
      240 / 600 / 1 400.
- [ ] Table d'armurier : munitions avec bocal II, III ou IV (bonne quantité), lait et vaseline à 12 tissus, cartes
      d'accès dans l'onglet Divers (plus de clic droit avec les 4 fragments).
- [ ] Quêtes du jour (J) : trois quêtes, tirage de la récompense ; livrer ou tirer est refusé sur la carte et en
      combat ; « Journée terminée » jusqu'à minuit.
- [ ] `/home`, `/tpa` : 8 min sans grade, moins avec un grade ; un délai ne bloque pas l'autre ; survit au redémarrage.
- [ ] Casino : borne ATM (déposer, retirer, derniers mouvements), grades à la journée aux nouveaux prix, jackpot qui
      monte de 1 % des mises (`/casino jackpot`), les 15 machines s'affichent.
- [ ] Caisses : un staff en créatif paie la clé ; inventaire plein → « t'attend », l'objet arrive dès qu'une case se
      libère (pareil pour un kit, un bon, un `/trade` annulé, une quête de Varga).
- [ ] Achat en PB quand le site répond une erreur (site de test) : l'achat attend puis est remboursé, jamais perdu.

### 2.8 Interface, textes, voix
- [ ] Chat (T) : onglets Proximité / Global / Clan, Tab change de canal ; seul à plus de 48 blocs : « Personne à moins
      de 48 blocs » ; un joueur muet n'écrit nulle part.
- [ ] Munitions en bas à droite, chiffre rouge sous un quart du chargeur ; rien avec un couteau.
- [ ] Menu Échap : « Boutique » ouvre la boutique au spawn, refus « seulement au spawn » sur la carte ; extractions
      proches affichées.
- [ ] Écran « Informations » à jour (plus de `/duel` ni `/zombies`), même chez un ancien joueur.
- [ ] `/redemarrage 1` sur un jeu en anglais et en russe : unités traduites, « relance le launcher » ; reconnexion
      automatique ; pendant 10 min, un vrai refus (serveur plein, banni) s'affiche tel quel.
- [ ] `/canal test` à deux joueurs sans clan commun : ils voient leurs pings ; `/canal clan` ramène au clan.
- [ ] `/perso` au spawn ouvre le créateur ; groupes vocaux : la liste se met à jour chez les autres.
- [ ] Polices : tous les écrans en DM Sans, lettres russes comprises ; rien ne déborde en anglais et en russe.
- [ ] Tutoriel du spawn : traînée verte vers le Guide puis Varga, visite (Espace pour passer), 7 questions.

### 2.9 Anticheat, launcher, site
- [ ] Ancien launcher 1.15.3 : joue normalement, « rapports non prouvés » au staff, aucune alerte tant que le réglage
      reste sur « noter ».
- [ ] Gardien : Cheat Engine ouvert → alerte en moins de 30 s ; launcher fermé de force → « Gardien muet » vers 90 s.
- [ ] Sprint, véhicules, train, allongé, à terre, téléportations, mauvais Wi-Fi : aucune alerte de vitesse ni de latence ;
      tir normal : aucune alerte « Tirs ».
- [ ] Anti-ESP : un joueur derrière un mur reste caché, réapparaît dès qu'il est visible ; juste après le démarrage,
      `/spartan esp` dit « en préparation » quelques secondes (prochaine mise en ligne).
- [ ] Launcher : un mod remplacé à la main est retiré et retéléchargé ; création de compte : petite fenêtre anti-robot.
- [ ] Téléchargement : sons des armes, meubles, zombies et joueurs comme avant (fichiers en double et textures OptiFine
      retirés).
- [ ] Site : vote récompensé une seule fois et seulement si le site de vote l'a vu ; un article avec une commande
      interdite (`op …`) est refusé ; la page Confidentialité porte la date du 2 novembre 2026.
- [ ] Staff : `/diag profil 30` donne un résumé toutes les 10 s puis un rapport ; `/diag etat` après une soirée :
      quelques dizaines de balles au plus, aucune ligne « COUPÉ ».

---

## 3. Côté admin / staff

**Commandes**
- `/recharger [module]` relit les fichiers sans redémarrer (op 4 ou console, aussi le bouton « Recharger » du panneau P)
  : `quetes`, `caisses`, `casino`, `raretes`, `loot`, `saison`, `territoires`, `clans`, `herals`, `purge`, `coffres`,
  `zones`, `clashwar`, `evenements`, `armes`, `anticheat`. Toutes les anciennes « recharger » de chaque mod
  (`/quete recharger`, `/casino reload`, `/eco recharger`, `/armurier recharger`…) n'existent plus.
- `/diag` remplace spark (retiré) : `profil [secondes]`, `stop`, `commandes` (les plus lentes), `mesure`, `etat`,
  `fuites`, `client <joueur>`. Rapports dans `diag/` ; la tâche GitHub « Lenteurs du serveur » peut lancer un profil et
  rend les noms lisibles. Le bouton « Analyser 60 s » du panneau fait `/diag mesure 60`.
- Panneau P : onglet « Commandes du Fondateur » (environ 280 commandes au lieu de 500 ; donner `/eco` à un grade ne
  donne pas `/eco donner`) ; boutons « Se cacher » et « Cacher son nom » (ex-`/staff vanish` et `/staff nametag`) ;
  bouton « Ban PC ».
- Argent : `/eco donner|retirer|definir <joueur> <montant> [motif]` (op 4 connecté, ou console ; 10 millions au plus ;
  journal `journal_admin.log`). `/clan admin argent` est fondu dedans.
- Renommées ou fondues : `/canal` (ex-`/ping canal`), `/perso` et `/voix mute|unmute` (ex-`/caracrea`), `/banpc info`
  (ex-`/sbaninfo`), `/clashwar coffre|parties|arenes` (ex-`/matchs`), `/casino stats` (ex-`/casino rtp`),
  `/heraliz edit <caisse>` (ex-`/heraliz loot add|list|remove`).
- Réservées : `/quete finir`, `/vitres`, `/cracbuild` (op 4) ; `/vehicules` (staff). Seulement sur un serveur d'essai
  avec le banc : `/quete donner`, `/journalieres essai`, `/heralizloot zombies simuler`.
- Retirées pour de bon : `/kiwi`, `/tacz`, `/seed`, `/xp`, `/team`, `/forceload`, `/matmossettings`, `/reload`, `/rl`,
  `/restart`… ; les noms en double (`minecraft:…`, autres noms de WorldEdit et LiteBans) sont cachés mais marchent si on
  les tape en entier.
- Les commandes des plugins (`/logout`, LiteBans…) passent maintenant aussi par la règle « combat, à terre, carte ». Le
  staff sur la carte garde LiteBans, mais un staff **tagué en combat** est bloqué comme un joueur. Sanctions LiteBans :
  plus rien dans le chat public, une ligne « [Staff] » pour l'équipe.
- Événements et saison : `/event lancer largage [avec_points]`, `/event helico lancer|info`, `/purge forcer <minutes>`,
  `/protection donner <joueur>` (seule protection de base qui reste), `/saison journal <tag>`, `/saison fin <date>`.

**Réglages rechargeables** (changés dans le fichier puis `/recharger`, ou relus seuls)
- Dégâts des armes (`armes.json`), plafond des armures (60 %), attente des camos à vie (15 min), angle de mêlée,
  10 s sans arme après une emote, balles (`[balles]`), silencieux.
- Saison (`saison.json` : plafond des kills 40, petites actions 50), niveaux et membres des clans (`niveaux.json`),
  tenue des zones, Purge (`protection_nouvelle_base_heures` à 0).
- Hôtel des ventes (`hdv.json` : taxes 12,5 % et 10 %), jackpot (`jackpot_part_mises` 1 %), Mila (`selon_etat_min` 10),
  coffres des bases (`coffres.json`), objets à vie dans les coffres (`lifetimeItemsOwnerOnly`).
- Bonus de loot (carte seulement, 3 min d'inactivité), zombies qui ramassent (coupé), protections gaz / radiation /
  froid, `/suicide`, distance de l'avion du largage, munitions par case.
- Noyau : tics aléatoires (0 = carte figée, 3 = normal), allègements de Minecraft (chacun se coupe dans `[perf]`), vue
  de 4 dans le tutoriel, commandes retirées et cachées. Les réglages du serveur ne partent plus chez les joueurs.
- Marchands réglés en jeu (Registre) : ils passent avant le fichier ; si un prix reste à l'ancien, « Revenir au fichier ».

**Outils GitHub**
- « Banc de charge » (vrai serveur, 100 à 150 joueurs simulés, sans toucher au Ptero) et « Lenteurs du serveur »
  (rapports du diagnostic et journaux du Ptero, en lecture seule).
- « Remplacer la carte de loot » : essai à blanc par défaut, garde le spawn et les données des joueurs.
- Mise en ligne : secrets réservés à `main`, Ptero et site reconnus à leur empreinte, AzLink figé, case « Monde des
  bases » retirée. Clé des PB plafonnée (20 000 par opération, 50 000 par 24 h, alerte Discord) ; depuis le site, seules
  quelques commandes partent vers la console (clés, hérals, PB, grades, emotes).

**À faire en jeu par le staff, si ce n'est pas déjà fait**
- Poser le commissaire-priseur (`/pnj creer commissaire`) et les points de la visite du Guide (`/guide point …`).
- Un ou deux jours après la publication du launcher 1.16.0 (02/11) : passer `ancienLauncher` de « noter » à « alerte ».

---

## 4. Pas encore fait / en attente

**Mise en ligne**
- Envoyer la branche « démarrage propre » (ClashWar 1.1.2, Spartan 2.10.2, Noyau 1.0.14, AzLink) avec un redémarrage ;
  puis publier le launcher 1.16.1 (le 1.16.0 est déjà publié depuis le 02/11).
- `server.properties` : choisir le message de la liste des serveurs (aujourd'hui « serveur de test ») et la liste
  blanche, avant une installation complète.

**À faire par le propriétaire (GitHub, panel, site)**
- Déjà fait le 02/11 : empreintes du site et du Ptero réglées et vérifiées, environnements GitHub créés, site durci
  (tâches `installer` et `theme`). Secrets laissés où ils sont (choix du propriétaire : lui seul a accès au GitHub).
- Site : exiger la double authentification pour les comptes admin (administration du site) ; vérifier que l'anti-robot
  de la création de compte est bien actif.
- LuckPerms : `bukkit.command.plugins` et `bukkit.command.version` à faux pour les joueurs (et `authme.player.unregister`,
  `authme.player.email`).

**Décisions à prendre**
- Cœur de clan au casino (toujours 20 000 capsules) ; jackpot gagné seulement au gain maximal d'une machine (très rare).
- Sac rangé dans un coffre de base avec un objet à vie dedans : il se prend toujours, d'accord ?
- Portée des traçantes (64, 48 ou 32 blocs) ; roquette tirée vers une zone vide : elle disparaît sans exploser.
- Composants de niveau II des zombies (encore 1 à 7 d'un coup) ; portée de poursuite des zombies.
- Boutique depuis la carte (bouton Échap refusé) ; pistolet en main à moins de 5 blocs d'une porte : ouvre la porte au
  lieu de viser.
- `/recharger` pour le staff de niveau 2 ? LiteBans au staff en combat ? `/matmossettings` rendu ? `/casino salle`,
  `/vitres pourquoi` gardées ? Uniques en T7 (1 coffre sur 50) ? Zones VIP sur la carte ? Les 8 caisses vidées ?
- Nouvelle carte de loot : dossier `/carte_nouvelle` du Ptero ou Release GitHub, avec ou sans wipe.

**Reporté exprès**
- Boss mis de côté pour l'ouverture (fabriqués, pas livrés) ; signature Windows du launcher (certificat payant) ;
  launcher « scellé » (essai sur un vrai Windows et Mac d'abord) ; clé SSH pour le Ptero ; clé des jetons de l'anticheat.
- Sauvegarde des clans et de l'hôtel des ventes encore faites d'un bloc (sûr mais un peu lourd) ; doublons des packs
  d'armes autres que les sons (environ 3 Mo) ; coffre d'un clan dissous quand le chef est hors ligne : toujours perdu.
- Renvoi en arrière de l'anticheat toujours coupé ; seuils des nouvelles alertes à régler après une semaine de relevés.
