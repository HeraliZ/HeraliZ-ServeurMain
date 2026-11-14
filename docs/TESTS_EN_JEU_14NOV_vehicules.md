# Essais en jeu — véhicules, retours du 14/11 (Monde 1.2.1)

Demande du propriétaire : « Faire en sorte pour les véhicules que la touche pour sortir soit directement la touche de
sneak ».

Rien n'a été essayé en jeu par Claude. Jars **joueurs ET serveur** à mettre à jour : la touche S'accroupir est lue par le
jeu des joueurs, la sortie sûre est décidée par le serveur.

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

## 3. Cas particuliers

- **Véhicule en mouvement** : on sort à toute vitesse (au plus 58 km/h, 0,8 bloc par tick, les 4 véhicules), comme avec
  l'ancienne touche et comme d'un cheval. Le joueur arrive à côté, sans élan (Minecraft remet à zéro la vitesse d'un
  passager), donc sans dégâts au sol. Le véhicule continue sur sa lancée sans conducteur : plus d'accélérateur ni de
  frein, le volant revient droit, il ralentit seulement par frottement et peut encore heurter quelqu'un (dégâts de choc
  du moteur, inchangés). Le moteur reste allumé et consomme l'essence jusqu'à ce qu'on le coupe (touche `<`).
- **Véhicule en l'air** (saut, chute d'une falaise) : on sort aussi ; on tombe de là où on est, dégâts de chute normaux
  au-delà de 3 blocs (la chute ne compte qu'à partir de la sortie). Avec le parachute dorsal au torse, Espace l'ouvre
  pendant la chute. Pas d'hélicoptère sur le serveur.
- **Joueur à terre (Réanimation)** : déjà sorti du véhicule par Réanimation au moment où il tombe (et à chaque tick), et
  il ne peut pas monter tant qu'il est à terre ; S'accroupir est de toute façon bloqué à terre et pendant une
  réanimation. Nouveau : l'endroit où il est posé en tombant est lui aussi vérifié (pas dans un mur).
- **Joueur en combat** : sortir est permis (comme avant ; le tag de combat n'interdit que de poser un véhicule). Attention
  au réflexe de s'accroupir en plein combat à bord : on sort.
- Chat, inventaire ou tout autre écran ouvert : Maj ne fait pas sortir.

## À vérifier en jeu

- [ ] Monter dans chacun des 4 véhicules, conducteur puis passager (arrière du buggy et du fourgon, place du camion) :
      le message « Maj gauche : sortir du véhicule » s'affiche ; un appui sur Maj gauche fait sortir, à côté du véhicule
      (près d'une porte pour le fourgon et le camion), sans saut ni retour en arrière.
- [ ] Options > Commandes : mettre S'accroupir sur une autre touche (par exemple C) : C fait sortir, Maj gauche ne fait
      plus rien à bord ; le message de montée dit « C : sortir du véhicule ».
- [ ] Options > Commandes > Véhicules : « Sortir (en plus de S'accroupir) » est vide ; lui donner une touche libre (par
      exemple F8) : elle fait sortir aussi.
- [ ] Conduire en gardant Ctrl (sprint) enfoncé : le véhicule avance et tourne.
- [ ] Accroupissement en mode « Basculer » (Options > Commandes) : un appui fait sortir ; on ne remonte pas tant qu'on
      reste accroupi (comme pour un cheval).
- [ ] Sortir en roulant vite : aucun dégât ; le véhicule continue un peu puis s'arrête ; le moteur tourne encore.
- [ ] Sortir en l'air (bosse, falaise) : chute normale ; avec le parachute dorsal, Espace l'ouvre.
- [ ] Sortir avec le tag de combat : ça marche.
- [ ] Joueur mis à terre dans un véhicule : il est posé à côté, jamais dans un mur ; à terre, Maj ne fait rien.
- [ ] Buggy puis moto garés collés contre un mur de base d'une case, côté sortie : en sortant on est de l'autre côté du
      véhicule, jamais dans le mur ni dans la base. Journal du serveur : « [vehicules] sortie de … : endroit du moteur pas
      sur (…), autre place (…) ».
- [ ] Fourgon dans un garage étroit (murs des deux côtés) : on sort à l'arrière ou à l'avant ; sans aucune place, sur le
      toit.
- [ ] En terrain dégagé, la sortie est au même endroit qu'avant.
- [ ] Chat ou inventaire ouvert à bord : Maj ne fait pas sortir.

## Questions pour le propriétaire

- Sortir d'un véhicule lancé à pleine vitesse reste permis (comme d'un cheval) : veux-tu l'interdire au-dessus d'une
  vitesse ?
- Quand le conducteur sort, le véhicule continue sur sa lancée, moteur allumé : veux-tu qu'il freine et coupe son moteur
  tout seul ?

## Non vérifié par Claude

- Pas compilé ici avec Minecraft et Forge (ils ne se téléchargent pas dans cet environnement) : les 5 classes touchées
  ont été compilées avec javac contre le vrai jar du moteur des véhicules et de petits remplaçants des classes du jeu ; la
  fabrication complète est faite par le contrôle de branche de GitHub. Textes contrôlés (`outils/verifier_langues.py` :
  0 erreur).
- La cause « Maj gauche ne faisait pas sortir » vient de la lecture du traitement des touches par Forge, pas d'un essai en
  jeu ; la nouvelle sortie ne dépend pas de cette cause (elle lit S'accroupir comme le jeu).
- La sortie sûre passe par un petit mixin serveur sur la méthode du moteur qui donne l'endroit de sortie (jar d'ywzj
  intact) ; il ne se vérifie pas hors du jeu : s'il ne s'appliquait pas, rien ne plante et la sortie redevient celle du
  moteur.
