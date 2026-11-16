# Essais en jeu — HeraliZ Récolte 1.0.0 (16/11)

Nouveau mod `heraliz_recolte` (module `recolte`), branche `recolte` : arbres à abattre et gisements de la carte, hache,
pioche et hache-pioche de survie, le Contremaître du spawn. Cahier des charges : `modules/recolte/A_FAIRE.md` ;
barème et décisions : `maquettes/recolte/PROPOSITION.md`. **Rien n'a été testé en jeu.** Fabriqué et vérifié par le
contrôle de branche de GitHub (`outils/construire.py`, `outils/verifier.py`) ; Gradle ne marche pas sur la machine de
Claude.

Décisions du propriétaire du 16/11 (après le cahier) :
- noms des minerais alignés sur les matériaux des bases : « Minerai de métal » (fragments de métal) et « Minerai de
  titane » (métal renforcé) ; identifiants inchangés (`minerai_fer`, `minerai_rare`) ;
- **pas de plafond par jour** sur les échanges ;
- **aucun point de saison** pour la récolte ;
- le recycleur prend la hache, la pioche et la hache-pioche (un peu de loot).

## Avant l'essai

- Serveur et joueurs avec les jars de la MÊME fabrication (nouveaux objets et entités) ; le launcher les donne.
- Poser le Contremaître au spawn : se placer à l'endroit voulu, tourné vers où il doit regarder, puis
  `/pnj creer contremaitre` (`/pnj deplacer contremaitre` pour le bouger ; apparence dans son fichier,
  `/pnj apparence contremaitre moi` pour la changer). Voir `serveur/config/heraliz_quetes/LISEZMOI.md`.
- Réglages : `serveur/config/heraliz_recolte/recolte.json` (relu tout seul quand il change, et par
  `/recharger recolte`).

## 1. Apparition (serveur)

1. Démarrage : le journal dit « reglages lus… » et « 68 place(s) » ; `/recolte info` les montre en attente.
2. En se promenant : les ressources apparaissent hors de vue (au moins 70 blocs de tout joueur), sur un sol naturel à
   plat, à ciel ouvert ; jamais en ville, près d'une base, de l'eau, d'une route ou d'un bâtiment, au spawn, dans une zone
   VIP. `/recolte info` montre les refus par raison.
3. `/kill` d'une ressource, puis `/recolte info` : sa place est de nouveau en attente. `/recolte retirer` (la plus proche)
   et `/recolte vider` marchent. Une copie d'une ressource est retirée quand son coin de carte se charge.
4. `/diag profil` : la récolte ne coûte presque rien quand rien n'est prêt.

## 2. Gisements

1. `/recolte placer pierre`, `metal`, `rare` : modèle de l'étape, ombre ; rien au-delà de 64 blocs.
2. Pioche : un coup compté par 1,2 s même en cliquant vite ; clic maintenu = les coups s'enchaînent ; tremblement à
   chaque coup, éclats (étincelles pour le métal), son de pierre, « +N » au-dessus de la barre ; un « pop » à chaque
   morceau arraché (4 étapes), puis il disparaît.
3. Totaux : pierre 24 pierres brutes en 16 coups ; métal 30 minerais de métal en 20 coups ; titane 16 minerais de titane
   en 24 coups. Avec la hache-pioche : mêmes totaux en 10, 12 et 14 coups.
4. Refus : hache sur un gisement = message, rien ne se passe ; trop loin = rien ; inventaire plein = matière au sol à ses
   pieds.
5. Usure : -1 par coup compté, rien en créatif, l'outil casse à 0.

## 3. Arbres

1. Bouleau et épicéa : hache, 12 coups (7 avec la hache-pioche), 30 bûches au total (2 par coup + 6 à la chute).
2. L'entaille se creuse du côté du joueur ; frapper de 4 côtés différents : elle se met chaque fois de son côté.
3. Au dernier coup il tombe loin du joueur, lent puis de plus en plus vite, petit rebond ; poussière, feuilles et son
   à l'impact (2,4 s), au bon endroit ; +6 bûches ; le tronc s'enfonce et s'efface ; la souche reste 10 min (même si on
   s'éloigne), puis disparaît. L'arbre qui tombe ne blesse personne. Rien au-delà de 64 blocs.
4. À deux sur la même ressource : le travail est partagé, le total ne change pas.
5. Redémarrage au milieu d'une récolte : l'avancement est gardé.

## 4. Outils en main (client)

1. Première personne avec les 3 outils, skin fin et normal, manches d'armure : les deux mains sur le manche (main
   droite en bas, main gauche plus haut), l'outil « sort » quand on le prend en main ; joueur gaucher : tout en miroir.
2. Le coup (≈ 0,6 s) : élan, frappe, impact (petite secousse de la vue, réglage « effets de distorsion »), retour ; le
   bruit et les éclats tombent avec l'impact ; coup dans le vide : même geste, sans secousse. La hache-pioche frappe
   côté pic sur un gisement, côté hache sinon.
3. Clic maintenu sur un arbre puis sur un joueur : ne doit pas enchaîner sur le joueur ; joueur à terre : pas de coup.
4. Vue de dos (F5) et vue d'un autre joueur, debout et accroupi : les deux bras suivent le geste.
5. Icônes : inventaire, au sol, dans un cadre.
6. Images par seconde près de plusieurs épicéas : pas mesuré.
7. À régler à l'œil (nombres en tête de fichier dans `modules/recolte/src/client/java/fr/heraliz/recolte/client/enmain/`
   et `.../client/rendu/`) : poses, épaisseur des bras, vitesse du coup, force de la secousse.

## 5. Contremaître, Bâtisseur, recycleur, loot

1. Le Contremaître : nom, rôle, phrases, bouton « Faire un échange » ; chaque échange (5 bûches → 1 planche ; 1 bûche →
   5 brindilles ; 8 pierres brutes → 1 pierre taillée ; 10 minerais de métal → 1 fragment de métal ; 8 minerais de titane
   → 1 métal renforcé), avec Maj + clic, inventaire plein, pas assez d'objets ; aucune limite par jour.
2. Bâtisseur : hache et pioche à 1 200 hérals ; la hache-pioche n'est jamais vendue.
3. Mila et les recycleurs (spawn et carte) refusent les matières brutes. Le recycleur prend la hache, la pioche
   (planche, ferraille II, kit de pièces II) et la hache-pioche (niveau IV) : 10 % par pièce (11 % sur la carte), 2
   pièces au plus.
4. Couleur des noms : bûche et pierre brute Commun, minerai de métal Peu commun, minerai de titane Rare, hache et pioche
   Rare, hache-pioche Légendaire. La hache-pioche reçoit un numéro de Spartan (anti-duplication).
5. Loot : hache et pioche dans les conteneurs T2 à T5, hache-pioche en T6 et T7 et, rarement (0,4 %), dans la caisse du
   largage public ; un largage public s'ouvre toujours et a du contenu.

## Pas vérifié / limites

- Rien essayé en jeu, aucun serveur d'essai avec de vrais joueurs ; aucune mesure d'images par seconde.
- Le serveur (Mohist, `spigot.yml`) montre ces entités jusqu'à 64 blocs (pas 96 pour les arbres comme dans le cahier),
  et ne les fait pas tiquer au-delà de 16 blocs d'un joueur : la chute et la souche sont suivies à l'heure de jeu.
- Bases repérées par les entités de bloc `crac:` dans un rayon de 2 tronçons ; zones d'extraction, drapeaux et salles à
  carte pas exclus de l'apparition.
- Une place est tirée au hasard dans la bordure du monde : la carte des places possibles calculée depuis la carte finie
  (comme `outils/zones_construction.py`) reste à faire avec la nouvelle carte.
- Deux appels de Forge utilisés pour la première fois dans le dépôt (`ForgeHooksClient.onClickInput`,
  `Options.screenEffectScale`) : à surveiller (fabrication de GitHub, puis en jeu).
