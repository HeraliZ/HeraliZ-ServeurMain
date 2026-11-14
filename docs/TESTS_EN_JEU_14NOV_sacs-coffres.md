# Essais en jeu — chantier « sacs-coffres » (retours du propriétaire du 14/11)

Bases (CRac) **1.37.1-beta**, Sacs (HeraliZ Backpack) **3.0.1-beta.4-heralisz24**, réglage `crac-server.toml` (commentaire
seulement). Rien n'a été essayé en jeu par Claude, ni fabriqué ici (Minecraft et Forge ne se téléchargent pas sur la
machine de travail) : la compilation complète se fera au contrôle de branche de GitHub. Essais faits hors du jeu : voir
« Essais hors du jeu ». `python outils/verifier_langues.py` : 0 erreur (aucun nouveau texte).

Jars : serveur obligatoire (CRac et Sacs) ; joueurs : nouveaux jars livrés comme d'habitude, mais un ancien jar joueur
marche aussi (aucun canal réseau ni texte ne change).

Demande du propriétaire : « Faire en sorte qu'on puisse poser les sacs qui n'ont rien dedans, et même ceux pleins, dans
les coffres de base (actuellement aucun sac ne peut être dans une base) — mais bien faire attention au possible bug de
DUPLICATION !!!!!! »

## Ce qui change

| Quoi | Pour le joueur |
|---|---|
| Coffres des bases (caisse en métal, malle de chantier, casier métallique, caisse d'armes renforcée, coffre-fort) | un sac, **vide ou plein**, s'y range et s'y reprend par tous les gestes (clic, Maj + clic, glisser, touches 1 à 9, touche F), contenu gardé. Refusé du 11/11 au 14/11 (règle `SansSac` et mixin `SlotCoffreSansSacMixin` retirés) |
| Écran d'un sac (touche B) | ne vaut que tant que **le même objet sac** est sur le dos de son porteur vivant ; sinon plus aucun clic n'est accepté par le serveur (déjà vrai par Minecraft et Mohist, maintenant aussi par le sac lui-même) et l'écran se ferme au tick suivant. En jeu normal, rien ne se voit |
| Objets à vie (couteaux, skins à vie) rangés dans un sac, lui-même dans un coffre de base | mêmes règles que s'ils étaient dans le coffre : base effacée (`/base effacer`, `/wipe`) : rendus au chef (avant : perdus avec le sac) ; réglage `lifetimeItemsOwnerOnly` (coupé par défaut, choix du 03/11) : s'il est mis à `true`, un pilleur ne prend pas non plus un sac qui en contient |

À savoir : la place d'une base est multipliée. Le plus grand sac a 20 cases ; un coffre-fort (78 cases) rempli de sacs
pleins garde 1 560 cases d'objets, une base au maximum de coffres (14 + 12 + 10 + 8 + 5) environ 45 000 au lieu de 2 250.

## Audit anti-duplication (fait AVANT d'ouvrir les coffres)

Comment marche un sac (Backpacked, code de MrCrayfish, LGPL) : son contenu est écrit **dans l'objet sac** à chaque
changement (correctif HeraliZ du 23/10). Il ne s'ouvre que **porté sur le dos** (touche B) : jamais au sol, jamais dans un
coffre, jamais dans l'inventaire. Le seul vrai danger est donc « un écran de sac encore ouvert alors que l'objet sac est
parti ailleurs » : ce qu'on sortirait de cet écran resterait aussi dans le sac parti (copie). L'écran exige le **même
objet** (pas une copie) sur le dos d'un porteur vivant, sinon il ne vaut plus ; Minecraft 1.20.1 et Mohist refusent tout
clic dans un écran qui ne vaut plus (lu dans leur code) et le ferment au tick suivant. Le sac lui-même refuse maintenant
aussi ces clics (garde-fou qui ne dépend plus d'eux), et note une ligne au journal du serveur.

| Chemin | Copie possible ? | Ce qui le ferme |
|---|---|---|
| Écran du sac ouvert, le porteur enlève son sac | non | Pour enlever le sac il faut l'écran de Curios, qui ferme d'abord celui du sac. Un client modifié peut, écran ouvert, échanger le sac porté par clic droit (Curios) : le sac porté n'est plus le même objet, l'écran ne vaut plus, clics refusés (Minecraft, Mohist et garde-fou), fermé au tick suivant |
| Un autre joueur ouvre le sac porté par quelqu'un | non (coupé) | `enabledPickpocketing = false` (`serveur/config/backpacked.server.toml`, copié sur le Ptero). Si on le rallume un jour : même règle (sac encore sur le dos, les deux vivants, voleur à portée), et l'écran ne vaut plus si on recoupe le réglage |
| Un sac ouvert dans un coffre | impossible | Backpacked n'ouvre qu'un sac porté ; l'étagère à sacs n'est pas enregistrée ; le marchand ambulant à sac est coupé |
| Deux joueurs sur le même coffre | non | Les deux écrans montrent le même coffre (comme Minecraft) : le sac pris par l'un n'est plus là pour l'autre |
| Coffre cassé, raidé (C4, chute), usé par l'entretien, pendant la Purge | non | Le contenu tombe au sol une seule fois (Minecraft vide chaque case en la jetant) ; les écrans du coffre ne valent plus aussitôt. Le marteau ne ramasse pas un coffre qui contient quelque chose |
| Base effacée, `/wipe` | non | Coffres vidés avant d'être retirés ; objets à vie des sacs rendus au chef (14/11) |
| Entonnoirs, distributeurs | non | Interdits à la pose depuis le 11/11. Ceux déjà sur la carte (je n'y ai pas accès : au staff de les compter s'il veut) déplacent un sac comme tout objet (retiré puis posé), et personne ne peut ouvrir un sac qui n'est pas porté |
| Mort (corps) | non | Mort : l'écran du sac ne vaut plus ; le corps reçoit le sac lui-même (contenu à jour) et ce qui était au curseur |
| Déconnexion en combat (= mort), relog, redémarrage, écran ouvert | non | Écran fermé avant l'enregistrement du joueur (Tactique, en dernier écouteur) ; contenu déjà écrit dans l'objet ; en combat : mort, corps, rien dans les données du joueur |
| Plantage du serveur / retour en arrière d'une sauvegarde | oui, comme pour **tout** objet de Minecraft | Un coffre est écrit sur le disque quelques secondes après un changement (10 s au plus), un joueur à sa déconnexion et toutes les 5 min : un plantage juste après un échange peut laisser un objet aux deux endroits. Pas propre aux sacs (même chose avec un sac posé au sol), mais un sac plein en contient plus. Pas de parade simple ; Spartan signale un même sac vu deux fois en même temps |
| Créatif (clic du milieu) | oui (staff) | Copie normale du créatif de Minecraft, staff seulement ; Spartan ignore le créatif |
| `/trade` | non | Inchangé : rien ne quitte l'inventaire avant la fin, cases relues à chaque tick, joueurs enregistrés après |
| Hôtel des ventes, recycleur, marchands | non | Inchangé : sac plein refusé |
| Planque, coffre VIP, coffre du clan | non | Inchangé : sac plein refusé dans la planque et le coffre VIP (sac vide accepté), aucun sac dans le coffre du clan |
| Sac dans un sac | non | Toujours refusé par Backpacked (clic, Maj + clic, touches) |
| Paquets réseau forgés | non | Le serveur décide de tout : case inexistante ignorée, écran fermé ou d'un autre numéro ignoré, écran qui ne vaut plus refusé ; paquets de Backpacked : ouvrir SON sac porté seulement (personnalisation coupée, sac d'autrui coupé) |
| Mémoire du contenu du sac porté | non | Le joueur garde en mémoire le contenu de son sac porté ; chaque changement est écrit dans l'objet, et la mémoire est oubliée dès que le sac n'est plus porté. Règle pour la suite : ne jamais modifier à la main la liste `Items` d'un sac porté, passer par `backpacked$GetBackpackInventory` |

Trou trouvé et fermé : aucun chemin de copie ouvert aujourd'hui ; le seul point fragile (un clic qui arriverait à l'écran
du sac sans passer par le contrôle de Minecraft) est fermé par le garde-fou du sac (`BackpackContainerMenu.clicked`).

## Essais hors du jeu

Les vraies classes `BackpackContainerMenu`, `BackpackInventory`, `BackpackSlot`, `InventoryHelper` (sacs) et `CoffreMenu`
(CRac), compilées contre de petites copies des classes de Minecraft 1.20.1 (clics recopiés du code décompilé), et les
fonctions des objets à vie (`contientAVie`, `objetsAVieDedans` de CRac, `retirerContenuAVie` de Tactique) recopiées
telles quelles. Règle vérifiée à chaque pas : le total des munitions et des couteaux (inventaires, curseurs, dos, coffres,
sol, et dans les sacs) ne change jamais. **59 vérifications, toutes bonnes** :
1. sac porté, écran ouvert : clic, Maj + clic, touches, tout est écrit dans l'objet sac ;
2. sac déplacé tel quel (touches dans l'écran de Curios), écran ouvert : clics refusés, une seule ligne au journal ;
3. sac posé dans un coffre de base (copie de Minecraft), écran ouvert : clics refusés ; l'écran d'avant le 14/11, appelé
   sans le contrôle de Minecraft, donnait 128 munitions au lieu de 64 : c'est ce que le garde-fou empêche ;
4. porteur mort : refusé ; 5. sac d'autrui : refusé quand c'est coupé, refusé dès que le sac part ou que le voleur meurt ;
6. coffre de base 11 × 6 : sac plein rangé et repris par Maj + clic, clic, touches 1 à 9, touche F, glisser ; deux
   joueurs sur le même coffre ; jet (Q) ; coffre cassé écran ouvert : le sac tombe une fois avec son contenu ;
7. sac dans un sac refusé ; 8. objets à vie trouvés dans un sac (et dans un sac rangé dans un sac), sac d'origine intact ;
9. sac aux données trop grandes : le surplus tombe une seule fois.

Pas essayé hors du jeu : le vrai Minecraft, Forge, Mohist et Curios (les copies imitent leurs règles), les vrais paquets
réseau, la casse d'un vrai coffre, `EffacerBase` et `WipeBases` (deux petites boucles, relues), les mixins.

## À vérifier en jeu

Avec un sac PLEIN (noter son contenu exact) et un sac VIDE, dans chacun des cinq coffres de base :
- [ ] rangé puis repris par clic, Maj + clic, glisser sur plusieurs cases, touches 1 à 9, touche F (main gauche) ;
- [ ] remis sur le dos et ouvert (B) : exactement le même contenu ; rien en plus dans l'inventaire ;
- [ ] à deux joueurs sur le même coffre : A et B cliquent le sac en même temps : un seul l'a ;
- [ ] coffre avec un sac plein détruit (C4 pendant la Purge, ou démoli) : le sac tombe une fois, avec son contenu ;
- [ ] marteau (clic droit) sur un coffre qui contient un sac : refusé (« Il contient encore des objets. ») ;
- [ ] écran du sac ouvert puis Échap, inventaire, enlever le sac du dos : l'écran du sac s'est bien fermé, contenu juste ;
- [ ] mort avec l'écran du sac ouvert : le corps a le sac et son contenu, rien en double au sol ni à la réapparition ;
- [ ] déconnexion en combat avec l'écran du sac ouvert, puis reconnexion : le sac est sur le corps, rien en double ;
- [ ] inchangé : planque et coffre VIP (sac vide oui, sac plein non), coffre du clan (aucun sac), sac dans un sac (non),
      hôtel des ventes, recycleur et marchands (sac plein refusé) ;
- [ ] journal du serveur : la ligne « [sacs] clic de … refusé » n'apparaît pas en jeu normal (si elle apparaît : client
      modifié, ou cas à remonter) ; Spartan : aucune alerte de copie sur les sacs pendant ces essais ;
- [ ] (staff, base d'essai) un couteau dans un sac, le sac dans un coffre, puis `/base effacer` : le couteau revient au
      chef (« 1 objet(s) à vie de ta base (coffre détruit ou base effacée) rendu(s) dans ton inventaire. »).
