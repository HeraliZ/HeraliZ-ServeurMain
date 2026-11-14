# Essais en jeu — véhicules, retours du 14/11 (Monde 1.2.1)

Demande du propriétaire : « Faire en sorte pour les véhicules que la touche pour sortir soit directement la touche de
sneak ». Puis ses deux choix du 14/11 : on ne sort pas d'un véhicule lancé ; sans conducteur, le véhicule freine et
coupe son moteur.

Rien n'a été essayé en jeu par Claude. Jars **joueurs ET serveur** à mettre à jour : la touche S'accroupir est lue par le
jeu des joueurs ; la sortie sûre, la vitesse de sortie et l'arrêt sans conducteur sont décidés par le serveur.

## 1. Comment on sortait jusqu'ici (constat)

- Une seule façon, la même pour les 4 véhicules (buggy, moto, camion, fourgon), pour le conducteur comme pour les
  passagers : la touche **« Sortir du véhicule »** du moteur des véhicules (Limitless Vehicle, jar d'un autre auteur). J à
  l'origine ; HeraliZ l'avait mise sur Maj gauche le 04/10.
- **Maj gauche ne faisait pas sortir** : nos touches de véhicule sont dans un réglage « à bord » ; pour Forge, une touche
  sans modificateur d'un tel réglage est ignorée tant qu'on tient Maj, Ctrl ou Alt. Appuyer sur Maj gauche « tenait Maj »,
  donc la touche Maj gauche ne se déclenchait jamais. Même effet sur la conduite : en gardant Ctrl (sprint) enfoncé, le
  véhicule ne répondait plus.
- Le moteur **bloque la descente de Minecraft** par S'accroupir (comme sur un cheval) et efface le message de Minecraft
  « Appuyez sur Maj gauche pour descendre ».
- **Maj n'est utilisée par aucune commande de nos véhicules** : frein à main = Espace, freiner et reculer = S. La commande
  « Descendre » du moteur (hélicoptères et avions) était sur Maj gauche à l'origine, mais HeraliZ l'avait déjà mise sur 0
  du pavé numérique, et elle ne sert à aucun de nos 4 véhicules (tous à roues). Aucun véhicule n'a d'arme. Aucun
  hélicoptère ni avion ne se pilote sur le serveur (l'hélicoptère des événements n'est pas un véhicule).
- Endroit de sortie : choisi par le moteur **sans regarder les blocs** : la porte la plus proche (fourgon, camion), sinon
  1 bloc à côté d'un flanc et 1 bloc plus haut (buggy, moto). Collé à un mur : dans le mur, ou de l'autre côté d'un mur
  d'une case (dans une base).
- Sans conducteur, le véhicule continuait sur sa lancée (seul le frottement des roues le ralentissait), moteur allumé,
  et consommait l'essence sans fin, même garé.

## 2. Ce qui change

- **S'accroupir fait sortir**, à bord de n'importe quel véhicule, à n'importe quelle place : la touche que chaque joueur a
  réglée dans ses options (Maj gauche par défaut), pas une touche écrite en dur. Le jeu envoie au serveur la même demande
  que l'ancienne touche du moteur ; le serveur fait sortir le joueur (place libérée, conduite libérée, véhicule laissé
  sur place) et le replace ; rien n'est décidé chez le joueur, donc pas de retour en arrière.
- En montant dans un véhicule : **« Maj gauche : sortir du véhicule »** (nom de la touche du joueur) s'affiche quelques
  secondes au-dessus de la barre, comme le message de Minecraft pour un cheval.
- Options > Commandes > Véhicules : l'ancienne touche s'appelle **« Sortir (en plus de S'accroupir) »**, sans touche par
  défaut ; on peut lui en donner une en plus. Une touche restée sur J ou Maj gauche (anciens réglages par défaut) est
  vidée ; une touche choisie par le joueur est gardée et marche.
- **On peut conduire en gardant Ctrl, Maj ou Alt** enfoncé (toutes les touches de véhicule marchent comme celles du jeu).
- **Sortie sûre** (serveur) : l'endroit du moteur est gardé s'il est libre et qu'aucun bloc ne le sépare de la place du
  joueur ; sinon un demi-bloc ou un bloc plus haut ; sinon l'autre flanc, le même flanc au milieu, l'arrière, l'avant ;
  sans aucune place libre, sur le toit du véhicule. **Jamais dans un mur ni de l'autre côté d'un mur.** En terrain
  dégagé, rien ne change. Réglage `sortie_sure` de `heraliz_vehicules-server.toml` (`false` : endroit du moteur tel quel).
- **Pas de sortie d'un véhicule lancé** (serveur) : au-dessus de **15 km/h** au sol, la sortie volontaire est refusée,
  pour le conducteur comme pour les passagers (S'accroupir comme la touche « Sortir » du moteur). Le joueur lit **« Trop
  vite pour sortir : ralentis sous 15 km/h »** au-dessus de sa barre, au plus une fois toutes les 1,5 s s'il garde la
  touche enfoncée ; touche gardée enfoncée, il sort tout seul dès que le véhicule passe sous 15 km/h. Jamais pour une
  sortie forcée par le serveur : mise à terre, mort, téléportation, véhicule détruit, déconnexion. Réglage
  `sortie_vitesse_max_kmh` (15 ; 0 : pas de limite) ; le chiffre du message suit le réglage. Vitesse = celle du véhicule
  au sol (sans la chute), en km/h comme le moteur l'affiche pour ses hélicoptères.
- **Sans conducteur, le véhicule s'arrête et coupe son moteur** (serveur), quelle que soit la raison (sortie, mort, mise
  à terre, déconnexion, téléportation, véhicule rechargé moteur allumé) :
  - il freine tout seul, roues au sol, avec le frein du véhicule (comme un conducteur qui freine) : depuis 58 km/h, environ
    1,5 à 3 s pour le buggy, le camion et le fourgon, 3 à 6 s pour la moto (un peu moins avec le frottement) ; en l'air,
    pas de frein ;
  - le moteur se coupe comme avec la touche Moteur (`<`) : il s'arrête de tourner en 5 s au plus, puis **plus aucune
    essence consommée** ;
  - le conducteur qui remonte le redémarre comme d'habitude (le moteur se rallume tout seul à la montée).
  Réglage `arret_sans_conducteur` (`false` : le véhicule continue sur sa lancée, moteur allumé, comme avant).

## 3. Cas particuliers

- **Véhicule en mouvement** : sortie refusée au-dessus de 15 km/h (au plus 58 km/h pour nos 4 véhicules) ; en dessous, le
  joueur arrive à côté, sans élan (Minecraft remet à zéro la vitesse d'un passager), donc sans dégâts au sol. Si c'est le
  conducteur qui sort, le véhicule freine jusqu'à l'arrêt et coupe son moteur ; les passagers restés à bord peuvent
  sortir dès qu'il est sous 15 km/h.
- **Véhicule en l'air** (saut, chute d'une falaise) : la règle des 15 km/h vaut pour la vitesse au sol (une chute droite ne
  compte pas) ; si on sort, on tombe de là où on est, dégâts de chute normaux au-delà de 3 blocs (la chute ne compte qu'à
  partir de la sortie). Avec le parachute dorsal au torse, Espace l'ouvre pendant la chute. Pas d'hélicoptère sur le
  serveur.
- **Joueur à terre (Réanimation)** : déjà sorti du véhicule par Réanimation au moment où il tombe (et à chaque tick), à
  toute vitesse (sortie forcée : jamais bloquée), et il ne peut pas monter tant qu'il est à terre ; S'accroupir est de toute
  façon bloqué à terre et pendant une réanimation. L'endroit où il est posé est vérifié (pas dans un mur). Si c'était le
  conducteur, le véhicule freine et coupe son moteur.
- **Joueur en combat** : sortir est permis sous 15 km/h (le tag de combat n'interdit que de poser un véhicule). Un réflexe
  d'accroupi à bord fait sortir si le véhicule est assez lent ; à pleine vitesse, il ne fait qu'afficher le message.
- **Mort du conducteur** (tué au volant) : sortie forcée, le véhicule freine et s'arrête ; les passagers peuvent sortir
  sous 15 km/h.
- Chat, inventaire ou tout autre écran ouvert : Maj ne fait pas sortir.

## À vérifier en jeu

- [ ] Monter dans chacun des 4 véhicules, conducteur puis passager (arrière du buggy et du fourgon, place du camion) :
      le message « Maj gauche : sortir du véhicule » s'affiche ; à l'arrêt, un appui sur Maj gauche fait sortir, à côté
      du véhicule (près d'une porte pour le fourgon et le camion), sans saut ni retour en arrière.
- [ ] Options > Commandes : mettre S'accroupir sur une autre touche (par exemple C) : C fait sortir, Maj gauche ne fait
      plus rien à bord ; le message de montée dit « C : sortir du véhicule ».
- [ ] Options > Commandes > Véhicules : « Sortir (en plus de S'accroupir) » est vide ; lui donner une touche libre (par
      exemple F8) : elle fait sortir aussi, avec la même limite de vitesse.
- [ ] Conduire en gardant Ctrl (sprint) enfoncé : le véhicule avance et tourne.
- [ ] Accroupissement en mode « Basculer » (Options > Commandes) : un appui fait sortir ; on ne remonte pas tant qu'on
      reste accroupi (comme pour un cheval).
- [ ] Rouler à fond et appuyer sur Maj (conducteur, puis passager) : on reste à bord, message « Trop vite pour sortir :
      ralentis sous 15 km/h » ; en gardant Maj enfoncée : le message revient au plus toutes les 1,5 s, et on sort tout
      seul dès que le véhicule ralentit sous 15 km/h. Rouler doucement (moins de 15 km/h) : on sort.
- [ ] `sortie_vitesse_max_kmh = 30` dans le fichier de réglages du monde (serverconfig) : le message dit « 30 km/h » ;
      `0` : on sort à toute vitesse.
- [ ] Le conducteur sort : le véhicule freine et s'arrête en quelques secondes (la moto plus lentement), le bruit du
      moteur s'éteint. Noter « FUEL … % » affiché à bord, sortir, attendre 5 minutes, remonter : presque rien n'a été
      consommé (avant : environ 1 % par minute, moteur allumé). Remonter au volant : le moteur repart et on conduit
      normalement.
- [ ] Conducteur tué ou mis à terre au volant, à pleine vitesse : il est sorti tout de suite (jamais bloqué), le véhicule
      freine et s'arrête ; les passagers peuvent sortir une fois sous 15 km/h.
- [ ] Le conducteur passe sur une place passager (touche « Changer de place ») : le véhicule freine et coupe son moteur ;
      revenir au volant le redémarre.
- [ ] Déconnexion du conducteur en roulant, puis téléportation (`/tp` du staff, extraction) : il part bien (jamais
      bloqué) ; le véhicule s'arrête et coupe son moteur.
- [ ] Sortir en l'air (bosse, falaise) en roulant doucement : chute normale ; avec le parachute dorsal, Espace l'ouvre.
- [ ] Sortir avec le tag de combat, véhicule lent : ça marche.
- [ ] Joueur mis à terre dans un véhicule : il est posé à côté, jamais dans un mur ; à terre, Maj ne fait rien.
- [ ] Buggy puis moto garés collés contre un mur de base d'une case, côté sortie : en sortant on est de l'autre côté du
      véhicule, jamais dans le mur ni dans la base. Journal du serveur : « [vehicules] sortie de … : endroit du moteur pas
      sur (…), autre place (…) ».
- [ ] Fourgon dans un garage étroit (murs des deux côtés) : on sort à l'arrière ou à l'avant ; sans aucune place, sur le
      toit.
- [ ] En terrain dégagé, la sortie est au même endroit qu'avant.
- [ ] Chat ou inventaire ouvert à bord : Maj ne fait pas sortir.

## Non vérifié par Claude

- Pas compilé ici avec Minecraft et Forge (ils ne se téléchargent pas dans cet environnement) : les 7 classes qui ont du
  code nouveau ont été compilées avec javac contre le vrai jar du moteur des véhicules et de petits remplaçants des classes
  du jeu (`HeralizVehiculesServeur` : une ligne d'inscription, relue) ; la fabrication complète est faite par le contrôle
  de branche de GitHub. Textes contrôlés (`outils/verifier_langues.py` : 0 erreur).
- La cause « Maj gauche ne faisait pas sortir » vient de la lecture du traitement des touches par Forge, pas d'un essai en
  jeu ; la nouvelle sortie ne dépend pas de cette cause (elle lit S'accroupir comme le jeu).
- Un petit mixin serveur sur deux méthodes du moteur (jar d'ywzj intact) : celle qui donne l'endroit de sortie (sortie
  sûre) et celle qui reçoit la demande de sortie d'un joueur (limite de vitesse). Il ne se vérifie pas hors du jeu : s'il
  ne s'appliquait pas, rien ne plante, la sortie redevient celle du moteur (sans contrôle de l'endroit ni de la vitesse).
- L'arrêt sans conducteur passe par un événement du moteur (sans mixin) ; les temps de freinage sont calculés depuis les
  réglages du pack (force de freinage, masse), pas mesurés en jeu.
