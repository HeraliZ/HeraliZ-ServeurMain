# Validation de l'audit du 21/11 — zone v5 : clans, PNJ, événements, récolte

Deuxième relecture, demandée par le propriétaire (« un audit bien profond et complet pour bien tout valider ») : un œil
neuf sur la même zone que le compte rendu `../a5_clans_pnj_evenements.md`, sans se fier à ses « vérifié sans problème ».
Modules relus : `modules/clans` (hérals et comptes, clans, banque, coffre, arbre, territoires et drapeaux, saison, profils,
classements, tags de clan par différences, staff caché), `modules/pnj` (Varga, quêtes du jour, journal, marchands, Mila,
échanges, recycleurs, bourses, `/wipe`), `modules/evenements` (Directeur, largage, objets d'événement) et `modules/recolte`.

**Rien n'a été testé en jeu** (pas de jeu ici) : relecture du code, et sur ce PC les contrôles qui marchent sans
fabrication : `outils/verif_reflexion.py` (0 erreur, 498 appels vérifiés), `outils/verifier_langues.py` (0 erreur),
`outils/verif_synchro.py` (0 erreur). La fabrication et les contrôles se font sur GitHub (« Contrôle de branche »).

En bref : les **13 changements du jour** de la zone font ce qu'ils disent, sans régression trouvée ; **1 petit défaut
corrigé** (Clans 3.19.4) ; **6 propositions** à décider, toutes petites ; **1 point à vérifier sur le serveur en ligne**
(adresse IP des joueurs vue par le serveur). Aucune duplication d'objets ni d'argent trouvée.

---

## Changements du jour validés

Chaque commit du 21/11 touchant la zone a été relu ligne à ligne (ce qu'il dit faire, cas limites, fils, lenteurs,
textes).

| Commit | Ce qu'il fait | Verdict |
|---|---|---|
| `06449bbf5` | Progression des quêtes jamais remplacée par un fichier à moitié écrit | **OK**. Le fichier d'avant reste si l'écriture rate ; le joueur est réécrit 30 s après (`Donnees.java` l. 151-157, 206-237). Les écritures d'une même voie se font une à la fois, dans l'ordre (noyau `EcrituresFond`) : pas de mélange. Reste connu : disque plein + plantage juste après une quête payée = quête rendable une 2e fois (avant ce commit : progression perdue en entier, pire). |
| `0379b11fc`, `7e18032ce` | Marchands, Mila, échanges, recycleur du spawn, quêtes du jour et étape « donner » refusés quand l'inventaire est mis de côté par le ClashWar | **OK** (`Marchands.java` l. 84-89, `Moteur.java` l. 473-479 ; règle du noyau `Matchs.inventaireRetenu`). |
| `a1490b67b` | Staff caché : tags de clan en deux vues (staff / joueurs), profil vu hors ligne | **OK**. Les deux vues du diffuseur (`Sync.java` l. 276-389) : un joueur qui change de vue reçoit la liste complète, les autres seulement la différence ; personne de caché = une seule vue, aucun appel aux grades. Table relue toutes les 2 s (150 joueurs : quelques centaines d'objets, rien qui part si rien ne change). |
| `e12d469ff` | Staff caché hors ligne pour son clan (écran du clan, carte, `/clan info`) | **OK** (`Sync.pour` l. 445-478, `Territoires.java` l. 212, `ClanCommands.java` l. 295-300 ; console = voit tout). |
| `1a03ce3b4` | `/bourse donner` op 4 + `/login` ; économie « indisponible » si `comptes.json` illisible ; « vu le » d'avant la partie ; erreurs du Directeur notées | **OK**. Commande : refus clair, texte dans les 3 langues. Économie : tous les appelants qui demandent `disponible()` refusent ou attendent proprement (Mila, banque, création de clan, hôtel des ventes, bons, casino, quêtes « récompense en attente ») ; drapeau remis à faux par `/recharger herals` ou `--forcer` (`ComptesStore` l. 435, 505). Directeur : `Directeur.java` l. 114-125 et 293-305. Deux petites suites, voir P5 et P6 plus bas. |
| `f112b1d95` | Marchands : 4 achats, ventes ou échanges par seconde | **OK** (`Marchands.java` l. 53-73, oubli à la déconnexion et à l'arrêt). Achat : le panneau n'en envoie jamais plus de 2 par seconde. Seul effet possible pour un joueur normal : sur l'onglet Échanges, plus de 4 clics par seconde sur « Échanger » → les clics en trop ne font rien (rien de pris ni de donné), il suffit de recliquer. |
| `5ce631488` | Panneau de zone : textes refaits seulement quand l'état change | **OK**. Tout ce qui est écrit vient du paquet du serveur (pourcentage, « Protégée 1 h 52 », distance au drapeau), refait à chaque nouveau paquet, changement de clan ou de langue (`ClanHud.java` l. 112-140). |
| `f324509e4` | Noyau déclaré des deux côtés (clans), Événements → noyau | **OK**. Pas de boucle de dépendances (le noyau ne dépend d'aucun de nos mods) ; versions demandées ≤ 1.1.11 en ligne. |
| `24397a2d3`, `247ca990e` | Recycleur de la carte : jamais en double après une écriture ratée ; journal des quêtes à 4 demandes par seconde | **OK**. Écriture ratée : ancien fichier effacé, machine fermée (les écrans ouverts se ferment, chaque clic revérifie `utilisable()`), nouvel essai toutes les 5 s, au déchargement du tronçon et à l'arrêt ; une écriture qui passe rouvre la machine. Pas de boucle : `ecrire()` → `reprendre()` ne réécrit pas une machine fermée ; aucun bloc changé pendant le déchargement (`RecycleurCarteEntiteServeur.java` l. 150-160, 230-326, 497-536). Journal des quêtes : la touche J, les boutons et Espace (visite du Guide) n'en envoient jamais 4 par seconde en usage normal ; un appui en trop est ignoré sans dégât (`ServeurQuetes.java` l. 56-62, 125-143). |
| `b93e323b6` | `profils.json` relevé sur le fil du serveur, écrit en fond ; écran de la saison 1 fois par seconde | **OK**. Mêmes clés, même ordre qu'avant ; relevé = copie de nombres (rien de partagé avec l'autre fil) ; le plus récent relevé gagne ; écriture ratée → refaite la minute suivante ; à l'arrêt, l'écriture est attendue (le noyau passe en « arrêt » avant les clans) (`Profils.java` l. 225-280). Saison : l'onglet ne redemande qu'après 2 s, jamais bloqué en usage normal (`ServerHandlers.java` l. 47-61, 197-199). |

Textes du jour : une seule clé nouvelle (`heraliz_quetes.bourse.cmd.login`), présente en français, anglais et russe.

---

## Corrigé

### 1. Coffre du clan ouvert pendant que l'inventaire est mis de côté par le ClashWar (petit, perte d'objets) — commit `69ef80a85`

En simple : quand un joueur part au ClashWar, son vrai inventaire est mis de côté, puis lui est rendu à la fin (et
l'inventaire du match est effacé à ce moment-là). Le noyau a une seule règle pour savoir si un joueur est dans cet état,
et les marchands la suivent depuis ce matin. Les clans avaient leur propre copie, qui oubliait un cas : la marque du dépôt
(par exemple à la reconnexion après un plantage, avant que l'inventaire soit rendu). Dans ce moment-là, un objet pris dans
le coffre du clan, ou le coffre rendu au chef à la dissolution, serait effacé avec l'inventaire du match. Maintenant le
coffre du clan, la création d'un clan, `/clan don` et la dissolution répondent « pendant un match », comme les marchands.
Rien ne change le reste du temps. **Clans 3.19.4** (le launcher la donne ; aucun canal réseau ne change).

Pour les développeurs : `modules/clans/src/serveur/java/fr/heralisz/clans/server/EtatJoueur.java` l. 58-67 (`enMatch` :
`fr.heraliz.noyau.api.Matchs.inventaireRetenu(p)` en plus) ; utilisé par `CoffreClan.empechement`, `ClanService.creer`,
`ClanService.dissoudre`, `SaisonCommandes.don`.

---

## À décider par le propriétaire

Rien de grave ; à choisir ou à laisser tel quel.

**P1. Deux commandes d'op 4 créent des hérals sans demander le `/login` (très petit, sécurité en plus).**
`/event objet <type>` donne un objet d'événement d'essai (Mila le rachète 120 à 700 hérals, sans points) et
`/quete finir <joueur> <quête>` finit une quête avec sa récompense. Votre règle du 28/10 pour créer de l'argent est
« op 4 connecté sur AuthMe, ou la console » (déjà appliquée à `/eco donner` et, depuis ce matin, à `/bourse donner`).
Aujourd'hui, sur Mohist, toute commande tapée passe d'abord par l'événement de Bukkit qu'AuthMe bloque avant le `/login`
(voir `CommandesBukkit` du noyau) : ce serait seulement une deuxième serrure. Proposition : même contrôle que `/bourse donner`
(un texte nouveau dans les 3 langues). Code : `modules/evenements/.../serveur/Commandes.java` l. 53 et 142 ;
`modules/pnj/.../serveur/Commandes.java` l. 177 et 476.

**P2. `/quete reset <joueur>` (op 2) permet de refaire les quêtes de Varga payées (petit, règle du jeu).**
La commande efface toute la progression, y compris la liste des quêtes principales finies : un staff op 2 qui se la
fait à lui-même peut refaire ces quêtes et toucher de nouveau leurs hérals et leurs points de saison (2 par quête
principale). Propositions possibles : la réserver à l'op 4 (comme `/quete finir`), ou garder la liste des quêtes
principales finies (plus de points ni d'argent la 2e fois). Code : `modules/pnj/.../serveur/Commandes.java` l. 180 et
663-680.

**P3. Écran des quêtes du jour sans cadence côté serveur (très petit, lenteurs).**
Comme le journal des quêtes corrigé ce matin : chaque demande refait l'écran et le renvoie (sans disque : les choix ne
s'enregistrent qu'une fois). Un jeu modifié pourrait le demander en boucle. Proposition : 4 par seconde, comme le
journal. Code : `ServeurQuetes.java` l. 70-73, `Journalieres.action` l. 248.

**P4. Coffre du clan : chaque fermeture enregistre le joueur, même si rien n'a bougé (très petit, lenteurs).**
Ouvrir et refermer en boucle (5 fois par seconde au plus) écrit son fichier de joueur à chaque fois sur le fil du serveur.
Proposition : n'enregistrer qu'après un vrai dépôt ou retrait. Code : `CoffreClan.java` l. 192-198.

**P5. Fichier des comptes illisible : les recycleurs ne versent pas leurs hérals (très petit, suite d'un changement du jour).**
Depuis ce matin, l'économie se dit « indisponible » dans ce cas rare. Les recycleurs (spawn et carte) recyclent quand
même l'objet : les composants sont donnés, la part en hérals ne l'est pas (avant : versée sur des comptes de toute façon
effacés à la relecture du fichier réparé). Proposition : laisser tel quel, ou arrêter les recycleurs pendant ce temps.
Code : `MenuRecycleurServeur.java` l. 205, `RecycleurCarteEntiteServeur.java` l. 497.

**P6. Événement dont l'annulation elle-même tombe en erreur (très petit, journal).**
Depuis ce matin, le serveur ne s'arrête plus : l'erreur est notée. Mais si l'annulation échoue à chaque fois, l'erreur
revient chaque seconde dans le journal jusqu'au redémarrage (aucun cas connu ne la provoque aujourd'hui). Proposition :
après une annulation ratée, ne plus faire tourner cet événement et le dire une seule fois. Code : `Directeur.java`
l. 114-125, `Largage.annuler` l. 268-283.

Restes déjà connus, pas reproposés : P5 et P6 du premier compte rendu (staff caché : total du temps de jeu du clan ; et,
vu ici, ses compteurs de zombies et de morts du `/profil` bougent s'il joue caché ; dépôt au-delà du plafond des
comptes), coffre du clan et planque après un plantage (sections 24 et 42 de la fiche d'essais).

---

## À vérifier sur le serveur en ligne

**Adresse IP des joueurs.** Les kills ne donnent ni point de saison, ni progrès des quêtes du jour « tuer des joueurs », ni
kill au profil quand le tueur et la victime ont la même adresse (`Saison.java` l. 483, `Journalieres.java` l. 418,
`ClanEvents.java` l. 257). La relecture de l'anticheat (a8) a noté que, selon le réseau de l'hébergeur, tous les joueurs
peuvent arriver avec la même adresse (celle de la passerelle de Pterodactyl). Si c'est le cas sur le Ptero, **aucun kill
ne compterait jamais**. À vérifier : après un kill entre deux joueurs de maisons différentes, le journal du serveur doit
montrer `[saison] +1 pvp …` et non `pas de points : kill … meme adresse IP que la victime`. Si c'est la deuxième ligne, le
dire : il faudra alors que l'hébergeur transmette la vraie adresse des joueurs.

---

## Vérifié sans problème

- **Paquets reçus des joueurs** : clans (tout passe par la cadence de 200 ms ; banque 1 par seconde ; saison 1 par
  seconde), quêtes (dialogues : seulement un numéro d'option de la page en cours ; code d'une étape : 1 essai toutes les
  2 s ; éditeur des marchands et ajout d'échange : op 4 ; marchands et échanges : 4 par seconde ; journal : 4 par
  seconde), événements et récolte : aucun paquet venu des joueurs.
- **Argent et objets** : achat (prix confirmé, place vérifiée avant de payer, compte écrit puis joueur), vente à Mila
  (cases dédoublonnées, bourse effacée du registre avant le paiement, objet d'événement noté vendu avant le paiement,
  signature qui couvre aussi la marque « essai »), échanges (calcul sur une copie, sac à dos porté jamais touché),
  recycleur du spawn (copie gardée avec le joueur à chaque changement, joueur écrit avant les hérals), recycleur de la
  carte (voir plus haut), bourses (registre écrit avant l'objet, une copie ne paie jamais), quêtes du jour (tirage noté
  avant la remise, clé par `crediterUneFois`), quêtes de Varga (progression et joueur écrits avant les hérals), banque du
  clan, création et dissolution, `/clan don` (joueur écrit sans l'objet avant l'XP, numéro anti-copie).
- **Commandes** : `/wipe` op 4 ; `/eco donner|retirer|definir` op 4 + `/login` ; `/clan admin argent` op 4 + `/login` ;
  `/saison reset|fin|points|journal` op 4 ; `/territoire` op 2 (captures du staff : décision du propriétaire, gardée) ;
  `/event lancer|annuler|point|objet` op 4 ; outils d'essai des quêtes seulement avec le banc.
- **Lenteurs** : territoires (un passage par seconde, joueurs × zones), Sync (rien à faire sans changement, tags relus
  toutes les 2 s), hologrammes (rien sans hologramme chargé), Directeur (une comparaison par seconde hors événement),
  recycleurs (nouvel essai d'écriture seulement s'il y en a), écritures des fichiers des clans, de la saison, des kills,
  de l'historique et des progressions sur les fils d'écriture du noyau.
- **Images par seconde** : panneau de zone (corrigé ce matin), suivi de quête (mis en page une fois par changement),
  solde.
- **Liens avec les autres modules** : `outils/verif_reflexion.py`, 0 erreur sur tout le dépôt (cibles des appels par
  réflexion vers `fr.heralisz.clans`, `fr.heralisz.economie`, `SaisonApi`, `ClansApi`, `fr.heraliz.quetes`,
  `QuetesApi`, `heraliz_evenements` présentes avec les mêmes paramètres) ; `GradesApi.cache`, `voitLesCaches`,
  `authentifie` présents (Boutique) ; `Matchs.inventaireRetenu` présent dans le noyau en ligne.
- **Textes** : `outils/verifier_langues.py` 0 erreur ; listes par différences `outils/verif_synchro.py` 0 erreur.

---

## À tester en jeu

- [ ] **Coffre du clan** (Clans 3.19.4) : déposer, prendre, Maj + clic, fermer, rouvrir : comme avant. Pendant un ClashWar
      (joueur inscrit, ou `/clashwar regarder`) : `/clan coffre` répond « fermé pendant un match » ; après le retour,
      il s'ouvre de nouveau. `/clan don`, créer un clan, dissoudre : comme avant hors match.
- [ ] **Kills** (voir « À vérifier ») : kill entre deux joueurs de maisons différentes : `[saison] +1 pvp` dans le journal
      du serveur, et +1 au `/profil` du tueur.
- [ ] Les essais du premier compte rendu (sections « À tester en jeu » de `../a5_clans_pnj_evenements.md`) restent
      valables : marchands et échanges en cliquant vite, recycleur de la carte, `/profil` après un redémarrage, journal
      des quêtes et onglet Saison, staff caché, `/bourse donner`.

Pas testé : tout en jeu (aucune compilation ici : fabrication et contrôles sur GitHub seulement).

---

## Fin du 21/11 (choix du propriétaire : « tout lançable et terminé à 100 % »)

En ligne avant ce lot : `main` = `1607d9655` (Clans 3.19.4 ; Quêtes 1.24.6, où une autre relecture a déjà mis
`/quete reset` à l'op 4 + `/login`, ma proposition P2). Fait ici, commit `5ce3a6ded` : **Clans 3.19.5, Quêtes 1.24.7,
Événements 1.3.4** (le launcher donne les nouveaux jars ; aucun canal réseau ne change ; chaque texte nouveau est en
français, anglais et russe). Les captures du staff **gardent** leurs points de saison (décision du propriétaire : rien
changé). **Rien n'a été testé en jeu.**

### Ce qui a changé

1. **`/event objet` et `/quete finir` demandent le `/login` (P1).** Ces deux commandes d'op 4 créent des hérals (objet
   d'essai revendu à Mila, récompense d'une quête) : même règle que `/eco donner`, `/bourse donner` et `/quete reset`,
   op 4 connecté sur AuthMe (ou la console pour `/quete finir`). Refus : « Connecte-toi d'abord avec /login. ».
   Code : `modules/evenements/.../serveur/Commandes.java` l. 145, `Outils.authentifie` l. 258 ; `modules/pnj/.../serveur/
   Commandes.java` l. 478. `docs/COMMANDES.md` mis à jour.
2. **Écran des quêtes du jour : 4 demandes par seconde au plus (P3)**, comme le journal des quêtes ; compté à part du
   journal ; la fermeture de l'écran passe toujours (la remise d'une récompense en attente se fait).
   Code : `ServeurQuetes.java` l. 71-75 et 136-151.
3. **Coffre du clan : la fermeture n'enregistre le joueur que s'il a bougé quelque chose (P4).** Ouvrir puis refermer
   sans rien déposer ni prendre n'écrit plus son fichier sur le fil du serveur ; un dépôt ou un retrait est enregistré
   comme avant (joueur puis coffre). Code : `CoffreClan.java` l. 192-199.
4. **Économie « indisponible » (fichier des comptes illisible) : les recycleurs ne recyclent plus (P5).** Celui du spawn
   (bloc et PNJ) ne s'ouvre pas : « Recycleur à l'arrêt : les hérals ne peuvent pas être versés pour l'instant. Reviens
   un peu plus tard. » ; s'il est déjà ouvert, il attend au bout de la barre sans rien prendre. Celui de la carte attend
   aussi, mais s'ouvre pour qu'on reprenne ses objets, avec « Recyclage en pause… ». Code : `Recyclage.java` l. 416 et
   428 ; `MenuRecycleurServeur.java` l. 117-150 ; `RecycleurCarteEntiteServeur.java` l. 424 et 461.
5. **Annulation d'un événement qui rate (P6)** : la pile d'appels est écrite une fois, puis une ligne au plus par minute
   (nouvel essai d'annulation chaque minute) ; l'événement cassé ne tourne plus. Avant : deux erreurs par seconde.
   Code : `Directeur.java` l. 112-130 et 147-173.
6. **Staff caché qui rejoint un clan pendant sa partie (a5 P5).** Son arrivée n'est annoncée dans le chat qu'aux membres
   qui voient les cachés (staff connecté) ; pour les autres il reste « hors ligne » (vu pour la dernière fois à son arrivée,
   temps de jeu 0) et la ligne « X a rejoint » est retirée du journal du clan qu'ils reçoivent (elle revient quand il se
   montre ou se déconnecte). À noter : `/clan info` ne montre aucun temps de jeu (kills, K/D, territoires, âge du clan) ;
   le seul temps de jeu visible est celui de chaque membre dans l'écran du clan, déjà figé à sa valeur d'avant la partie
   depuis ce matin, et maintenant aussi pour un caché arrivé pendant sa partie. Code : `ClanService.repondre` l. 511-528 ;
   `Sync.java` l. 103 (`debutSession`, heure de début gardée) et l. 502 (`sansArrivees`) ; `ClanEvents.java` l. 149.
7. **Plafond de 10 000 000 hérals (a5 P6) : plus jamais d'objet pris sans tout payer.**
   - Mila : le total de la vente est compté d'abord, sans rien changer ; s'il ne tient pas sur le compte, rien n'est
     pris : « Vente refusée : ton compte dépasserait son plafond (10 000 000 hérals). Vends moins d'objets. Rien n'a été
     pris. » (`Marchands.java` l. 270 et 341).
   - Quête de Varga : elle attend, comme quand l'économie est indisponible (« … dépense un peu, puis reviens la rendre »,
     `Moteur.java` l. 222-233).
   - Recycleurs : ils attendent quand la part en hérals ne tiendrait pas sur le compte (spawn : compte du joueur ; carte :
     seulement si les bourses sont bloquées, sinon les hérals vont dans la bourse de la machine).
   - Bourses : une bourse pleine ne reçoit plus rien (une neuve est faite) et deux bourses dont la somme dépasserait le
     plafond ne se fusionnent plus ; avant, le surplus disparaissait (`Bourses.java` l. 281 et 382).
   - Tout dépôt coupé au plafond est maintenant écrit dans le journal du serveur (`EconomieApiServeur.java` l. 99) : le
     staff peut rendre la différence. Les autres modules (hôtel des ventes, casino, ClashWar…) gardent leur façon de
     faire (hors de cette zone) ; la banque du clan, `/herals payer`, `/eco` et la dissolution vérifiaient déjà la place.
   - Outils communs : `Ponts.plafond` et `Ponts.place` (l. 295 et 318, par réflexion sur `EconomieApi.maxSolde`).
8. **`mods.toml`** : noyau 1.1.5 au moins pour les clans (l. 91), les événements (l. 30) et les quêtes (l. 30) (relevé de
   la relecture v10 : tronçons prêts, inventaire tenu par un match).

Contrôles ici : `outils/verif_reflexion.py` (clans, quêtes, événements : 0 erreur ; `EconomieApi.maxSolde()` et
`GradesApi.authentifie(ServerPlayer)` trouvés), `outils/verifier_langues.py` (0 erreur), textes des quêtes refaits par
`modules/pnj/outils/langues.py`, inventaire des commandes inchangé (`outils/commandes.py`).

### À tester en jeu (Fin du 21/11)

- [ ] `/event objet dossier_classe` et `/quete finir <joueur> <quête>` : op 4 après `/login`, ça marche ; avant le
      `/login` (si AuthMe laisse passer la commande), refus « Connecte-toi d'abord avec /login. ».
- [ ] Quêtes du jour : ouvrir, choisir, accepter, livrer, tirer, fermer en cliquant vite : tout répond ; la récompense
      tirée arrive bien à la fermeture.
- [ ] Coffre du clan : ouvrir et refermer sans rien bouger, puis déposer et prendre : contenu juste après un redémarrage.
- [ ] Recycleurs, marchands et quêtes comme avant quand l'économie va bien. (Serveur d'essai seulement : avec une copie
      abîmée de `comptes.json`, le recycleur du spawn refuse de s'ouvrir avec le message, celui de la carte s'ouvre avec
      « Recyclage en pause » et ne recycle pas ; après `/recharger herals` réussi, tout repart.)
- [ ] Plafond (serveur d'essai, `/eco definir <joueur> 9999990`) : vendre à Mila pour plus de 10 hérals → refusé, rien
      n'est pris ; vendre pour 10 hérals ou moins → passe ; rendre une quête qui paie plus → elle attend avec le message.
- [ ] Staff caché (S, staff caché ; C, joueur du clan hors du staff) : S rejoint le clan de C par une invitation : C ne lit
      aucun « S a rejoint le clan » ; dans l'écran du clan, S est hors ligne et la ligne « S a rejoint » n'est pas dans
      l'activité ; un autre staff du clan voit tout. S se montre : la ligne revient (au plus quelques secondes).
- [ ] Démarrage du serveur : aucune ligne « dépendance manquante » pour le noyau.
