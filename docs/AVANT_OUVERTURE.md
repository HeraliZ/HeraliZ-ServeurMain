# Avant d'ouvrir le serveur (audit final du 23/11)

Tiré des 440 essais de `TESTS_EN_JEU_17NOV_carte-ile.md` (sections 1 à 57) et des 6 relectures du code du 23/11.
Rien de cette liste n'a encore été testé en jeu. Le numéro (§) renvoie à la section d'origine de la fiche.

## Le jour J

- Sur le Ptero : `/whitelist off` (le fichier du dépôt la remet si on fait une « installation complète »).
- Pendant l'ouverture : toute mise en ligne se fait **avec** la case « redémarrer » (sinon le serveur garde les anciens
  mods alors que le launcher donne déjà les nouveaux : joueurs refusés par l'anticheat).
- Après chaque redémarrage : « Journaux du serveur » sans erreur nouvelle.

## Essais à faire avant d'ouvrir

### Connexion et arrivée
1. Lancer par le launcher, jouer 10 min avec Discord ou OBS ouvert, se déconnecter, revenir : « Jeu validé », aucune
   alerte de l'anticheat chez le staff. *1 joueur + 1 staff.* (§33, 46, 47, 51)
2. Un pack de textures dans `resourcepacks` : la partie démarre et la connexion est acceptée. (§33)
3. Se connecter avec le plus gros inventaire possible (sac plein d'armes, Curios pleins), ouvrir coffre du clan, coffre
   VIP, recycleur plein : aucune expulsion « Packet too big ». (§28)
4. Nouveau compte : Guide, Varga, tutoriel des bases ; un 2e compte du même clan clique « Quitter le tuto » : inventaire
   rendu exact, départ sur l'île. *2 comptes.* (§6, 11, 24, 49)
5. Voix et Tab : personne ne s'entend avant `/login`, tout le monde après ; Tab montre grade et tag de clan. *3 joueurs.*
   (§40, 50, 52)
6. **Arrivée en masse** : 12 comptes jamais inscrits se connectent en 30 s : tous entrent (voir la question sur
   l'anti-bot d'AuthMe). (§57)

### Bases et raids
7. Raid d'un clan sur un autre (C4, charges, échelle) : les murs cassent comme prévu. *2 clans.* (§27, 49, 51)
8. Créer un clan : 30 s après, le matériel de la première base est dans le coffre du clan, une seule fois ; déposer et
   prendre pendant une sauvegarde, redémarrer : rien en double ni perdu. (§30, 31, 41)
9. Objet à vie dans un coffre de base, `/base effacer` inventaire plein, libérer une case : l'objet revient une seule
   fois. (§41)
10. `/sethome`, mourir, « À la base » ; `/home` ou `/rtp` sous les tirs : refusé. *2 joueurs.* (§45, 50, 52)
11. Recrue sans le droit « Ouvrir les coffres » : `/clan quitter` dans la base, les coffres restent refusés 10 min. (§57)

### Loot et conteneurs
12. Ouvrir des conteneurs partout (villes, bunkers, salle à carte, T7, largage, corps, joueur à terre), en courant,
    accroupi, arme en main : tout s'ouvre, jamais « Tu ne vois pas ça d'ici » à tort. (§34, 49)
13. Vider des conteneurs et attendre : le loot revient (`/heralizloot zone <zone> info`). (§31)
14. Salles à carte : bonne carte ouvre, mauvaise refusée, carte usée, déconnexion dedans = devant la porte fermée. (§2, 3, 51)
15. Bunkers : carte des Bois au lecteur du dehors ouvre (annonce), carte du Pic et rouge refusées, lecteur du dedans
    rouvre. (§1, 27)

### Argent, boutique, caisses, échanges
16. Bâtisseur : 500 brindilles, « Max » inventaire presque plein, Échap, double clic : solde exact, rien sans
    « Confirmer ». (§25)
17. Arme achetée au Passeur, revendue à Mila, une autre recyclée : jamais plus que prévu. (§25, 29)
18. Bourse de hérals : recycleur de terrain, fusion, rachat par Mila, copie en créatif refusée, hôtel des ventes refusé.
    (§8, 52)
19. Recycleurs du spawn et de la carte : recycler, reprendre, redémarrer : chaque objet une seule fois. (§24, 29, 50)
20. `/trade`, achat en PB avec solde trop bas (objets rendus), hôtel des ventes vente + achat. *2 joueurs.* (§8, 51)
21. Caisses : ouvrir Camo, Événement, Gemme, Vote, Quête ; aucun objet de Noël dedans ; clé de Noël donnée par le staff,
    caisse Noël ouverte (spammer le clic, fermer pendant la roue, se déconnecter pendant la roue : 1 lot par clé). (§54, 55)

### Combat, mort, réanimation, inventaire
22. Déconnexion en combat sur la carte = mort ; au spawn = vivant ; `/spawn` en combat refusé ; deux `/tpa` = une seule
    téléportation. (§27, 51)
23. Extraction, aussi sous le feu : arrivée sans « EN COMBAT », quitter aussitôt et revenir : rien perdu. (§7, 27)
24. Mort avec sac plein et bourse : un autre joueur récupère tout sur le corps. (§8, 37)
25. À terre puis relevé ; à terre puis achevé : une seule mort, pas de tir à terre. (§35, 49)
26. Tirer avec toutes les armes (aussi en 4G) : aucune fausse alerte de l'anticheat. (§34)
27. Après une sauvegarde automatique, déconnexion/reconnexion : inventaire, sac, armes, position intacts. (§30, 49)
28. Première connexion du jour avec des armes à skins : les skins apparaissent (journal du jeu : « 92 programmes
    prêts, 0 en échec »). (§50, 57)

### Clans, saison, quêtes
29. Capturer le drapeau d'une ville : points dans l'onglet Saison. (§7)
30. Quête de Varga : étape finie, déconnexion, progression gardée. (§4, 41)
31. Après un `/stop` propre : banque, membres, territoires, comptes, quêtes, `/profil` intacts. (§30, 31, 35, 50)

### ClashWar (serveur d'essai, `/clashwar lancer`)
32. Partir avec inventaire plein, fusil équipé, sac, Curios, objet à la souris : tout revient une seule fois (même mort
    ou déconnexion à la clôture). *4 comptes.* (§42, 44, 49, 50, 51)
33. `/clashwar regarder` / `quitter` plusieurs fois, recycleur juste après : rien en double. (§24, 42)
34. Moins de 3 clans : pas de primes ; 3 clans ou plus : primes et clés une seule fois. *6 comptes.* (§24, 31)

### Événements
35. `/event liste`, un largage tombe sur l'île, sa caisse donne du loot. (§6, 49)
36. Hélicoptère abattu : coffre T7 + 2 T6 fermés pendant le feu, puis ouverts. (§13, 31, 44, 51)

### Staff
37. `/depart info` « prêt », `/heralizloot point liste`, `/salle liste` (12), `/pnj liste`, `/zones info`. (§1, 3, 5, 6, 25, 27)
38. Journal après 1 h de jeu : aucune erreur nouvelle ; `/diag etat` sans échec d'écriture. (§27, 30, 45, 49, 52)
39. Droits : un op 2 ne peut pas `/camos ajouter`, `/quete reset`, `/bourse donner` ; `/redemarrage 5` puis
    `annuler` : rien ne redémarre. (§49, 51, 52)

## À prévoir à part

- **Soir chargé** : « Lenteurs du serveur » (profil 60 s) avec de vrais joueurs ; départs groupés à la borne ;
  `/diagclient 30` au spawn avec 20 joueurs armés.
- **Tournoi** (mercredi et dimanche 20 h 30) : essais 32 à 34 en vrai, arènes prêtes à temps en 4v4.
- **Redémarrages** : pendant le feu de l'hélicoptère, pendant une porte de bunker ouverte ; coffre du clan, profils,
  recycleur de la carte intacts après.

## Peut attendre après l'ouverture

Visuels des camos et skins, écrans du ClashWar, bandeaux et textes anglais/russe, images par seconde, équilibrage
(zombies, récolte, tenues), confort (`/money`, `/profil`, staff caché), décor, outils du staff.
