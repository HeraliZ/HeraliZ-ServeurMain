# À tester en jeu — lot du 25/10

Rien de cette liste n'a été essayé en jeu (Claude ne lance pas le jeu). Chaque ligne : quoi faire, ce qu'on doit voir.
Cocher au fur et à mesure ; noter ce qui ne va pas (capture d'écran + langue du jeu).

## 1. Faim
- [ ] Courir 5 minutes sur la carte en survie : la barre de faim baisse **environ deux fois moins vite** qu'avant.
- [ ] Au spawn et pendant le tutoriel : la faim reste pleine (comme avant).
- [ ] Réglage : `[survie] faim_facteur` dans `heraliz_noyau-server.toml` du monde (0,5 ; 1 = comme Minecraft).

## 2. Clans
- [ ] Créer un clan : **4 membres au plus** (le 5e invité est refusé, message « clan plein »).
- [ ] Onglet Niveau (touche K) : la branche « Membres » affiche « Niv. 10 · 1 pt · 25 000 hérals » ; bouton éteint tant que
      le clan n'est pas niveau 10 ; un clan niveau 10 avec les hérals peut l'acheter (5 membres).
- [ ] Un ancien clan plus nombreux garde ses membres mais ne peut plus inviter.
- [ ] Onglet Permissions : nouvelle ligne « Se téléporter à la base » ; décocher pour un membre → il ne peut plus faire
      `/home` ni réapparaître à la base.

## 3. Réapparition et /home
- [ ] Mourir sans base de clan : écran « Tu es mort », « Au camp » marche, « À la base » grisé (« Ton clan n'a pas de base »).
- [ ] Avec une base (armoire posée) et le droit : « À la base » → réapparition **juste devant l'armoire**, face à elle.
- [ ] Remourir tout de suite : « À la base » affiche « Disponible dans 7:5x » et se rallume seul à 0:00.
- [ ] `/home` juste après une réapparition à la base : refusé (« Prochaine téléportation vers ta base dans … »).
- [ ] `/home` depuis le spawn (délai fini) : départ après 3 s immobile, arrivée devant l'armoire.
- [ ] Armoire entourée de murs : arrivée sur la place libre la plus proche, jamais dans un bloc.
- [ ] Redémarrer le serveur pendant le délai : le délai continue (il n'est pas remis à zéro).
- [ ] « Quitter le serveur » sur l'écran de mort.

## 4. Panel du staff (touche P)
- [ ] Un joueur sans grade du staff : P ne fait rien.
- [ ] Un Helpeur : seulement ses actions (avertir, muet court, expulser, aller voir, libérer, écrire).
- [ ] Rendre muet 1 h : LiteBans applique le mute ; le journal (onglet Historique) et le salon du staff Discord le notent.
      **Vérifier dans l'historique de LiteBans que l'auteur est bien le membre du staff** (option `--sender`).
- [ ] Un Modérateur ne peut pas sanctionner un Admin (message « staff de ton rang ou au-dessus »).
- [ ] Fondateur (op 4) : onglet Permissions, cocher / décocher une case → effet immédiat chez le grade concerné.
- [ ] Donner un grade (Fondateur) : le joueur reçoit le grade, message, annonce Discord ; le grade Fondateur n'est pas
      proposé.
- [ ] « Voir inventaire » : on ne peut rien prendre ni poser ; « Modifier inventaire » (Admin) : on peut.
- [ ] Onglet Serveur : État du serveur, Analyser 60 s (résultat dans le chat), nettoyage du sol autour de soi.

## 5. Chat
- [ ] Ouvrir le chat (T) : panneau de verre, onglets Proximité / Global / Clan ; Tab change de canal (pas pendant une
      commande commencée par /).
- [ ] Proximité : un joueur à plus de 48 blocs ne lit pas ; seul, on reçoit « Personne à moins de 48 blocs ».
- [ ] Clan : seuls les membres lisent ; sans clan, l'onglet est grisé.
- [ ] Joueur muet (LiteBans) : ses messages ne partent dans aucun canal.
- [ ] Chat fermé : 4 lignes au plus, effacées en 8 s ; rien par-dessus l'inventaire ou un autre écran.
- [ ] Ouverture d'une vague d'extractions : notification en haut à gauche (« Extraction 3 · 7 · 12 · 18 — pendant 1 h »),
      plus rien dans le chat.
- [ ] Liens cliquables (boutons « accepter » de /tpa) : cliquer marche.
- [ ] F3 + D vide le chat ; « Vider le chat » du panel le vide chez tout le monde.

## 6. Recycleur et soins
- [ ] Poser le recycleur (onglet créatif HeraliZ · Quêtes) : 2 blocs de large, tourné vers soi ; clic droit sur les deux
      moitiés ouvre le recycleur ; le casser en créatif retire les deux moitiés.
- [ ] Bandage : arrête le saignement et rend 2 cœurs ; Attelle : soigne la jambe cassée seulement. Modèles en main et
      icônes corrects, animation sans pièce flottante.
- [ ] Un ancien « Pansement de terrain » / « Bandages » / « Chiffons » dans l'inventaire devient un Bandage en 5 s.
- [ ] Jacqueline vend Bandage et Attelle ; on en trouve dans les conteneurs de soins.

## 7. Commandes et interfaces
- [ ] `/home`, `/tpa accepter`, `/discord lier`, `/herals payer`, `/boss mammouth eveil` marchent ; les anciennes
      (`/sethome`, `/tpaccept`, `/lier`, `/cc`…) n'existent plus.
- [ ] Passer le jeu en anglais puis en russe : parcourir les écrans (clan, quêtes, marchands, boutique, panel, carte,
      roue de ping, voix, armurier) : aucun texte ne doit sortir de sa case.
- [ ] Touche P libre (plus d'écran « Interactions sociales »).

## 8. Corrections de l'audit du 25/10 (soir)
- [ ] **Bons** : créer un bon « grade 1 jour » (`/bon editeur`), le mettre dans une caisse, l'ouvrir avec 2 ou 3 joueurs :
      chacun peut utiliser le sien (une ligne « exemplaire » par gain dans `heraliz_grades/journal.log`). Un bon copié en
      créatif puis donné : refusé, mais l'objet reste.
- [ ] **Kits** : joueur gradé, kit Survivant puis tout de suite le kit de grade (aussi inventaire presque plein) : le bouton
      ne reste jamais figé, un message dit pourquoi en cas de refus. `/kit` sur la carte : refus clair.
- [ ] **Grades** : donner Rescapé à la console à un joueur qui a acheté Roi de la Zone ; `/boutique abonnement` montre Roi en
      réserve ; retirer Rescapé : Roi revient dans la minute.
- [ ] **`/redemarrage 1`** avec un joueur en combat et un joueur à terre : après le retour, aucun mort, aucun corps.
- [ ] **Réanimé puis déconnecté** juste après (plus de 5 min après la chute) : on revient debout.
- [ ] **Écran de mort** : se déconnecter dessus, revenir : « Disponible » ou le temps restant (plus « Vérification… »).
- [ ] **Panel** : un Modérateur ne peut pas expulser un Admin qui n'a pas encore fait /login. Un Helpeur caché à qui on
      retire le grade redevient visible en 2 s.
- [ ] **Clans** : double clic sur « Améliorer » = un seul palier ; `/clan dissoudre` demande une confirmation (2e fois en 30 s).
- [ ] **ClashWar** : un joueur qui se déconnecte en plein match puis revient attend en salle (plus de « fantôme »).
- [ ] **Bonus de loot** et **toiles de la Mère des Ruines** : plus d'alerte « IllegalEffect » de Spartan, effet stable.
- [ ] **Quête « Trousse de secours »** : les Bandages sont acceptés. Bandage et Attelle se recyclent.
- [ ] **Échange chez un marchand** « accessoire précis → X » : un autre accessoire est refusé.
- [ ] Le fusil **M82A2** ne tombe plus dans le loot (il ne se chargeait pas dans TaCZ).
- [ ] **Premier démarrage** : dans le journal, plus de lignes « [diag] blocage … ArenesService » pendant la pose des arènes.

## 9. Deuxième passe du 25/10 (soir)
- [ ] **Lits** : cliquer sur un lit, mourir, « Au camp » → réveil au spawn, pas au lit.
- [ ] **Salle à carte** : se déconnecter dedans, revenir → devant la porte côté couloir, porte fermée.
- [ ] **Revente** : acheter des brindilles (2) chez le Bâtisseur, les vendre à Mila → 1 héral chacune.
- [ ] **Clan dissous** : rien ne tombe de l'armoire ; la base devient libre.
- [ ] **Losange vert** au-dessus des membres de son clan (pas à travers les murs, pas sur soi, caché avec F1).
- [ ] **`/ping canal test`** à deux joueurs sans clan commun : ils voient les pings l'un de l'autre.
- [ ] **TaCZ** : tirer sur un joueur qui court de côté puis saute, avec 100 à 200 ms de ping : la majorité des balles touche.
      Chercher « [armes] tirs refusés par le serveur » dans le journal pendant les combats.
- [ ] **Emotes** : faire une emote puis tirer / lancer une grenade aussitôt → refusé avec « encore N s » ; se déconnecter
      pendant le délai et revenir → toujours bloqué.
- [ ] **Clés** : un joueur avec des clés physiques (inventaire, sac, planque) se connecte → clés converties, bon total dans
      `/boutique` onglet « Mes clés » ; ouvrir chaque caisse ; double clic rapide ; `/trade` d'une vieille clé refusé ;
      vote (AzLink) et achat de clés crédités.
- [ ] **Varga** sans points de quête posés : il passe aux quêtes suivantes au lieu de dire « pas encore ».
- [ ] **FPS** (profil CurseForge « HeraliZ Decors ») : F3 à Valmont puis en campagne ; `/diag client` ; armures GeckoLib
      bien affichées (textures retirées de la grande texture).

## 10. Saison et quêtes de clan (25/10, après-midi)
- [ ] **Quête « Jouer 180 min sur la carte »** : rester immobile 3 min sur la carte → la quête n'avance pas ; marcher → +1
      par minute ; au spawn, même en marchant → rien ; frapper un zombie sans bouger → la minute compte.
- [ ] **Quête « Parcourir 10 km à pied »** : marcher avance la quête ; en véhicule, dans le train ou à la nage → rien.
- [ ] Le serveur démarre sans les boss (aucune erreur liée à `heraliz_boss` dans le journal) ; les quêtes de clan
      « Vaincre un boss » et « Vaincre 5 boss » ne sont plus tirées (un clan qui en avait une reçoit une autre quête à la
      place, dans les 20 s).
- [ ] `/saison recharger` : pas d'erreur, `boss_coefficients` lu (journal « bareme charge »).
- [ ] **Onglet Saison** (touche K) : classement des clans par points, mon clan surligné, « Votre clan » (place, points,
      cœur posé ou non) ; à droite, les puces PVE / PVP / ÉVÉNEMENTS changent la liste ; chiffres = ceux de `saison.json`
      (changer un chiffre puis `/saison recharger` : l'onglet rouvert montre le nouveau) ; en anglais et en russe, rien
      ne sort des cases.
- [ ] **Capture d'un drapeau** (saison commencée, cœur posé) : zone libre → points PVE (`/saison journal <tag>` :
      « capture … zone libre valeur N ») ; la reprendre à un clan qui la tenait depuis 1 h → points PVP ; la même zone
      recapturée tout de suite → 0 (« deja recompensee ») ; au-delà de 40 points dans la journée → 0.
- [ ] **Purge** : pendant une Purge (ou `/purge forcer 5`), petite icône en haut à gauche (point rouge, « PURGE »,
      temps restant), plus aucune barre rouge en haut ; F1 la cache ; dans une zone de clan, le panneau de zone est
      dessous ; à la fin (`/purge arreter`), l'icône disparaît ; un joueur qui se connecte pendant la Purge la voit.
- [ ] **Saison automatique** : au premier démarrage, « La saison 1 commence » ; `/saison` dit « pas de date de fin » ;
      `/saison reset` puis `/saison reset confirmer` (dans les 30 s) → tous les clans à 0, saison 2 annoncée ;
      `/saison fin 31/12/2026 20:00` → annonce à tous ; l'onglet Saison affiche « fin dans … » ; `/saison fin annuler`.
- [ ] **Largage public** : `/event lancer largage avec_points` : dans le chat ET en notification en haut à gauche
      « Largage militaire dans 6 min · X …, Z … (secteur …) », rappel « dans 3 min » ; aller aux coordonnées : l'avion
      passe vers la 6e minute, caisse en parachute avec sa fumée, sur un sol dégagé (ni eau, ni arbre, ni toit) ; le
      premier qui ouvre : « +8 points de saison pour ton clan », annonce « Le largage du secteur … a été ouvert » ;
      `/saison journal <tag>` montre « largage (1e du jour) » ; le 4e largage du jour du même clan : 0 point.
- [ ] Largage lancé sans « avec_points » : aucun point. Redémarrer le serveur avec une caisse au sol non ouverte puis
      l'ouvrir : les points sont donnés une fois.
- [ ] `/event` (joueur, sur la carte) : « Prochains événements : 20:39 largage · 00:09 largage · … » (toutes les 3 h 30) ;
      au planning, avec moins de 4 joueurs sur la carte : pas de largage, et (27/10) pas de nouvel essai rapproché : le
      suivant 3 h 30 plus tard, comme s'il avait eu lieu (journal : « prochain essai a HH:MM (intervalle normal) », `/event`).
- [ ] **Caisse de largage façon T7** : à l'ouverture, l'écran des coffres T7 (sans badge « T7 »), objets en « ? »
      découverts un par un (cadre rouge qui se trace, son, fondu, couleur de la rareté), plus long pour un objet rare ;
      on ne prend un objet qu'une fois découvert ; on ne peut rien déposer ; à deux joueurs devant la même caisse, la
      découverte avance pour les deux. Les coffres T7 de la carte marchent toujours comme avant (badge « T7 » compris).
- [ ] **Objet d'événement** : dans la caisse du largage, toujours un dossier classé, une mallette scellée ou un disque dur
      militaire (nom bleu, violet ou or ; modèle 3D en main, icône dans l'inventaire ; infobulle « à revendre à Mila ») ;
      chez Mila, au survol : prix (120 / 300 / 700) et « +2 / +4 / +8 points de saison pour ton clan » ; vendu : hérals
      reçus et notification des points ; `/saison journal <tag>` : « objet_… ».
- [ ] Objet d'événement sans clan, ou clan sans cœur : chez Mila, en orange « aucun point » et « Garde-le pour plus tard »,
      « Tout vendre » ne le prend pas ; vendu quand même en le choisissant : hérals seulement.
- [ ] Copie d'un objet d'événement (créatif, pioche du milieu) : la première se vend, la seconde est refusée par Mila.
      `/event objet mallette_scellee` (staff) : se vend 300 hérals, aucun point.
- [ ] **Fusée d'extraction** dans le loot : en T5 à T7, rare, nom violet (Épique).
- [ ] Plus tard, boss remis : 1er kill d'un boss par un clan = 60 points (`/saison journal <tag>` : « kill 1 du jour,
      x1 »), 2e = 12 points, 3e = 0 ; minuit (heure de Paris) remet à zéro.

## 11. Hélicoptère militaire (25/10, rien testé en jeu)

Réglages : `serveur/config/heraliz_evenements/helico.json` (`/event recharger`). Essais du staff : `/event helico lancer ici`
(48 blocs devant soi, sans points) ou `/event helico lancer avec_points` (depuis le bord de la carte), `/event helico info`,
`/event helico prochain <minutes>`, `/event annuler helico`. Journal : `heraliz_evenements/journal.log`. Pour les points, tirer
en **survie** (un tir en créatif ou en spectateur passe l'hélicoptère en « essai »).
- [ ] **Vol** : il entre par le bord de la carte, au moins 56 blocs à l'intérieur de la bordure (ou devant le staff), sans
      à-coup au départ (son tronçon est demandé, jamais chargé d'un coup) ; patrouille au-dessus de la terre ferme (jamais
      au-dessus de la mer, du point d'apparition ni d'une base de clan), 30 à 45 blocs au-dessus du terrain, virages doux et
      penchés ; rotor qui tourne, disque flou, feux qui clignotent ; on l'**entend de loin** (jusqu'à ~250 blocs, avant de
      le voir), plus fort en approchant ; aucun bloc touché. Au bout de **5 à 10 min** sans être attaqué : « L'hélicoptère
      s'éloigne » et il disparaît au bord. Attaqué : il reste tant qu'on lui tire dessus, part 60 s après le dernier coup,
      jamais après 25 min de vol.
- [ ] **Carte M** : tous les joueurs de la carte voient l'hélicoptère bouger en direct (pictogramme vu de dessus, tourné
      dans son sens de vol, rouge orangé, halo qui pulse), au-dessus des extractions ; infobulle « Hélicoptère militaire ·
      abats-le pour récupérer sa boîte noire » ; après le crash, pictogramme ambre de l'épave tant qu'elle est là
      (« Épave d'hélicoptère · coffre et boîte noire quand le feu s'éteint ») ; tout disparaît à la fin (ou 6 s / 12 s sans
      nouvelles) ; rien dans le monde des bases ni au spawn ; rien après une déconnexion. Textes en anglais et en russe.
- [ ] **Il n'attaque que ceux qui lui tirent dessus** : passer dessous sans tirer : rien. Tirer dessus (balles TaCZ) : il
      tourne autour du tireur, alerte de verrouillage chez lui seul (~1,2 s), puis rafale de 8 coups (traçantes, éclairs aux
      miniguns, canons qui tournent) toutes les 3 à 5 s ; un autre joueur à côté n'est jamais touché ; sous un toit ou sous
      un arbre : aucune balle ne passe ; se cacher pendant une rafale l'arrête. Il tire jusqu'à 60 blocs et ne suit pas un
      tireur parti à plus de 96 blocs. 60 s sans tirer : il oublie le tireur. Joueur à terre : il ne tire plus dessus.
- [ ] **Touches** : balles sur la cabine et sur la queue (F3 + B : 5 boîtes qui suivent l'hélicoptère quand il tourne),
      étincelles et claquements ; une balle perforante qui traverse cabine et queue ne compte qu'une fois (`/event helico
      info` : dégâts du tireur = dégâts de l'arme, pas le double) ; grenade ou roquette : comptée une fois (×2) ; fumée grise
      à 50 %, noire et flammes à 25 %, alarme dans la cabine. Laisser 30 s sans tirer : il regagne 1,5 % de ses PV par
      seconde (`/event helico info`). Tirer en créatif : `/event helico info` dit « essai ».
- [ ] **Chute** : à 0 PV, spirale de 5 à 8 s, rotation de plus en plus rapide, traînée noire, rotor qui ralentit (son plus
      grave), le bruit lointain du rotor s'arrête ; il s'écrase **à côté** de l'endroit où il a été abattu, sur un sol
      naturel dégagé (jamais dans l'eau, un arbre, sur un toit ou une maison en terre, ni dans une base de clan) ; grosse
      explosion sans casse ; tout près : petits dégâts ; zombies attirés. Annonce « Un hélicoptère s'est écrasé dans le
      secteur … ». L'abattre au-dessus d'une ville ou de la mer loin de tout sol dégagé : « … a explosé en vol », la boîte
      noire tombe sur le dernier sol dégagé qu'il a survolé (sinon le plus proche ; journal : « objet d'evenement pose »).
- [ ] **Épave** : modèle brûlé, zone de feu (on y brûle) et colonne de fumée noire pendant 2 min à 3 min 30 ; ensuite un
      coffre T7 apparaît à côté, au niveau du sol (jamais sur un toit voisin ni dans un trou) ; à sa première ouverture il
      contient la **Boîte noire d'hélicoptère** ; épave et fumée parties 15 min après la fin du feu, même si plus personne
      n'est là (heures de jeu).
- [ ] **Points** : aucun à la destruction ni à l'ouverture du coffre ; la boîte noire vendue à Mila : 400 hérals et
      « +12 points de saison » (2 par clan et par jour) ; hélicoptère lancé par le staff sans « avec_points », ou touché en
      créatif ou en spectateur : boîte noire « essai » (hérals seulement).
- [ ] **Cycle** : jamais annoncé à l'avance ; après un hélicoptère (abattu, parti, annulé), le suivant 4 h à 6 h plus tard
      (`/event helico info`) ; avec moins de 10 joueurs sur la carte ou un ClashWar (27/10) : pas de nouvel essai
      rapproché, le prochain encore 4 h à 6 h plus tard (une ligne au journal par refus, « intervalle normal »). Redémarrage pendant le vol ou l'incendie : plus rien dans le monde, aucun point
      donné deux fois, prochain passage gardé (heure écrite dès le départ) ; heure dépassée pendant l'arrêt : 30 à 60 min
      après le démarrage, jamais tout de suite.
- [ ] **Perf** : `/diag mesure` pendant un combat et un crash (ticks dans les objectifs) ; petit PC : FPS près de l'épave.
