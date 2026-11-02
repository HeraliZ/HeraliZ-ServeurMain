# Audit du 02/11 — ce qu'il reste à corriger

Audit en lecture seule de `main` (65e64fad, mis en ligne le 02/11), en six volets. Rien n'a été modifié ni testé en
jeu. Chaque point a été vérifié dans le code actuel (fichier et ligne dans les rapports détaillés de `docs/audit_02nov/`).
Les numéros « A… » renvoient à l'audit du 26/10 quand le point y était déjà.

Rapports détaillés : `mods-1.md` (noyau, armes, équipement, sacs, survie, voix, emotes, interface), `mods-2.md` (loot,
bunker, monde, événements), `mods-3.md` (clans, bases, ClashWar, PNJ, boutique), `split.md` (ce qui part chez les joueurs,
mods livrés), `securite.md` (site, launcher, anticheat), `commandes.md` (commandes admin).

Le propriétaire choisit les points à faire (par numéro). Effort : P = petit, M = moyen, G = gros.

## Bloquants

| N° | Quoi | Effort | Détail |
|---|---|---|---|
| 1 | **Anticheat** : un réglage (`allowedJavaAgents`) laisse passer en silence une famille d'outils d'injection, alors que notre launcher n'en utilise aucun. Le vider. | P | securite §1 |
| 2 | **Gardien du launcher** : le serveur croit une simple phrase envoyée par le jeu (« tout va bien ») ; un jeu modifié l'envoie et n'a plus d'alerte. | M | split §1 |
| 3 | **Après le premier ClashWar, plus aucun largage ni hélicoptère** jusqu'au redémarrage (le tournoi fini n'est jamais oublié). Une ligne. | P | mods-2 §1 |
| 4 | **Quêtes de clan** : « fouiller N caches » ne peut jamais avancer (anciens blocs) ; « déposer N hérals » se fait gratuitement en retirant puis redéposant (+2 000 hérals, +1 500 XP, +5 points par semaine) ; « tuer N joueurs » se fait avec des comptes secondaires. | M | mods-3 §1 |
| 5 | **Casino** : un gain au-delà de ~23 000 capsules est jeté par terre (lag, capsules ramassées par d'autres). | P | mods-3 §2 |
| 6 | **Recycleur du spawn** : son contenu tombe par terre à la déconnexion, au plantage du jeu ou au redémarrage ; il accepte les objets à vie. | P | mods-3 §3 |
| 7 | **Armes de mêlée « à vie »** : elles s'usent et finissent par casser et disparaître. | P | mods-1 §1 |
| 8 | **Objets à vie perdus** quand on quitte avec un écran ouvert (Curios compris). | P | mods-1 §2 |
| 9 | **Tirs à la tête et coups de mêlée refaits par geckolib222** : au fusil à pompe, un plomb à la tête fait moins qu'au corps. | M | mods-1 §3 |

## Importants

| N° | Quoi | Effort | Détail |
|---|---|---|---|
| 10 | `/clan admin argent` crée des hérals sans limite, sans contrôle ni journal ; et donner une commande à un grade lui donne aussi ses sous-commandes d'admin. | P | commandes §2-3 |
| 11 | **Réglages qui fuient** : 8 fichiers de réglages serveur envoyés à chaque joueur à la connexion, 7 écrits en clair sur le PC des joueurs (dont la règle anti-boost des clans). | M | split §3-4 |
| 12 | **Nos jars se décompilent presque comme le code source** (noms gardés ; l'outil de brouillage est cassé et jamais branché). | M | split §5 |
| 13 | **Anticheat côté joueur** : il montre ce qu'il ignore et ce qu'il cherche ; le mode strict a des angles morts ; le launcher officiel peut être imité. | G | split §2, securite §2-3 |
| 14 | **Triches de tir et de déplacement** sans parade côté serveur (renvoi en arrière coupé, marge de vitesse +20 %, tirs TaCZ pas analysés). | G | securite §7 |
| 15 | **Accès sensibles** : clé des PB en clair sur le Ptero (PB sans limite si volée), un compte admin du site commande la console du serveur (double authentification non exigée), secrets GitHub lisibles depuis n'importe quelle branche. | M | securite §4-6 |
| 16 | **Largage** : le tirage du point gèle le serveur, peut créer du terrain neuf dans `dayz_map` et ignore les zones VIP. | P | mods-2 §3 |
| 17 | **Bunker** : fuite de mémoire chez les joueurs (portes), à chaque aller-retour carte ↔ spawn. | P | mods-2 §2 |
| 18 | Objet déposé dans un conteneur de loot **détruit 5 min après** (objets à vie compris). | P | mods-2 §4 |
| 19 | Nom de zone d'extraction de plus de 64 caractères : **le serveur plante** ou expulse les joueurs. | P | mods-2 §5 |
| 20 | **Hérals** : chaque grosse opération fait attendre le serveur pendant la réécriture de tous les comptes (fichier qui grossit sans fin) ; le recycleur de la carte enregistre à chaque clic. Lag provocable. | M | mods-3 §4-5 |
| 21 | **Boutique** : une erreur du site efface un achat en PB, sans trace ni remboursement. | P | mods-3 §6 |
| 22 | `/clan don` marche sur la carte : la valeur d'un Légendaire est mise à l'abri sans extraction. | P | mods-3 §7 |
| 23 | Récompenses de caisses et achats jetés par terre quand l'inventaire est plein. | P | mods-3 §8 |
| 24 | **Loot** : sacs Épiques et Légendaires ~3 fois trop fréquents ; composants Épiques des zombies par 4 en moyenne ; sans fichier de zones, un conteneur haut de gamme isolé est toujours plein. | P | mods-2 §7-9 |
| 25 | Planques : chaque fermeture relit les coffres de tous les joueurs (petit gel). | P | mods-2 §6 |
| 26 | L'écran « Informations » est figé chez les anciens joueurs (cite `/duel`, `/zombies`, `/groupe`). | P | mods-1 §4 |
| 27 | Armure : un plomb à la tête fait ignorer le gilet pour tout le tir. | P | mods-1 §5 |
| 28 | Reconnexion automatique : cache la vraie raison d'un refus pendant 10 min. | P | mods-1 §6 |
| 29 | Voix : un jeu modifié peut faire renvoyer la liste vocale sans fin (lag). | P | mods-1 §7 |
| 30 | Camos, emotes et sourdines d'un joueur remis à zéro si leur fichier est illisible. | P | mods-1 §8 |
| 31 | Soins : changer d'objet pendant le soin garde le temps du premier. | P | mods-1 §9 |
| 32 | `/logout` et les commandes des plugins passent hors du filtre « combat / à terre / carte ». | P | mods-1 §10 |
| 33 | `/ping canal` probablement inaccessible aux joueurs (Mohist a déjà un `/ping`). | P | commandes §1 |
| 34 | `/kiwi dev_env_rules` (mod Kiwi) tue toutes les entités chargées (PNJ, marchands, corps, véhicules) et active keepInventory. | P | commandes §4 |
| 35 | Le jar de TaCZ contient un 2e pack d'armes de 48,5 Mo jamais utilisé (~10 % du téléchargement). | P | split §6 |

## Nettoyage (mineurs, groupés)

| N° | Quoi | Effort | Détail |
|---|---|---|---|
| 36 | **Commandes** : de ~500 à ~280 dans le panneau du Fondateur, de ~1 000 à ~280 dans le chat. Sans toucher au code des autres : préfixes `minecraft:` cachés, autres noms de WorldEdit et LiteBans cachés, une liste « commandes retirées » dans le noyau. Dans nos mods : outils d'essai retirés, doublons fondus, 16 « recharger » en trop. | M | commandes §5-12 |
| 37 | **~76 Mo inutiles téléchargés par les joueurs** : textures OptiFine de cfm (6 Mo), 8 caisses vidées (8 Mo), doublons des gunpacks (11 Mo), polices copiées 13 fois (3 Mo), outils de tournage, bot d'essai de la réanimation, console admin de CRac jamais activée, moteur du ClashWar dans le jar joueur, `PackXZExtract.jar`. | M | split §9-11 |
| 38 | **Restes d'anciens systèmes** : camos du mode Zombies, code de PlayerRevive et de spark, tests de CRac dans le jar serveur, ancien monde `crac:bases` créé à chaque démarrage, recettes de CRac, jackpot du casino jamais alimenté, machine « Fruits d'Or », pont Vault, traces des boss, des matchs et de l'Oil Rig, réglages morts. | M | mods-1 §13-16, mods-3 §10, split §12 |
| 39 | **Petits défauts de jeu** : zombies écrits dans le monde, véhicules posables au spawn et au tutoriel, atelier qui jette l'arme à la déconnexion, staff en créatif qui prend les points d'un largage, caisses gratuites en créatif, 3 s de mouvement à la borne, fusée d'extraction hors de la carte, quêtes du jour livrables sur la carte, largage jamais visité qui fume pour toujours, ordres d'écriture (ATM, hérals des ventes) à remettre dans le bon sens. | M | mods-2 §10-18, mods-3 §9-14 |
| 40 | **Propreté du code** : kit d'interface recopié 21 fois, réflexion recopiée, réglages d'équilibrage écrits dans le code, dégâts des armes qui ne se rechargent pas sans redémarrer, écritures de fichiers en arrière-plan recopiées 5 fois, skins d'armes recalculés à chaque image. | G | mods-1 §11-22, mods-2 §19-21 |
| 41 | **Sécurité mineure** : connexions au site et au Ptero par mot de passe sans vérifier la machine, AzLink pris « en dernière version », création de compte par le launcher sans anti-robot, votes non vérifiés, launcher sans signature Windows, empreintes MD5, adresses IP jamais purgées (la page Confidentialité promet 1 an), durcissements web, ~13 Mo de restes de Helios, 3 vues mortes du site, docs de l'anticheat périmées. | M | securite §8-19 |

## Décisions à prendre (une ligne chacune)

- Quêtes de clan « Déposer N hérals » : les remplacer ou les retirer ?
- `/clan don` : seulement au spawn ?
- Sacs trouvés : quelles parts d'Épiques et de Légendaires (avec ou sans le sac d'école) ? Composants Épiques des zombies : 1 seul ?
- Un conteneur haut de gamme isolé doit-il être plein à chaque passage ?
- Brouiller nos jars (avec un essai en jeu complet avant) ?
- Alléger le jar de TaCZ (licence gardée) ?
- Les 8 caisses vidées reviendront-elles ? Sinon on allège leurs modèles.
- Supprimer la console admin de CRac ?
- Renommer notre `/ping` en `/canal` ?
- Une commande donnée à un grade doit-elle aussi donner ses sous-commandes d'admin ? (conseil : non)
- Les bâtisseurs gardent-ils tout WorldEdit (en cachant seulement ses doublons) ?
- Y a-t-il des zones VIP sur la carte elle-même ?
- Double authentification exigée pour les comptes admin du site (à régler dans l'administration du site) ?

## Pas vérifié

La version d'Azuriom et de ses extensions sur le site en ligne, les droits LuckPerms (dans la base du serveur, pas dans
le dépôt), la liste exacte des commandes de Mohist, l'usage réel des commandes au-delà de la soirée du 28-29/10, les
règles de construction et de raid de CRac et l'équité des jeux du casino (pas relus en détail).
