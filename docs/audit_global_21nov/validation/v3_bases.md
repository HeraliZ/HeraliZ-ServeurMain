# Validation de l'audit du 21/11 — zone v3 : les bases (CRac)

Deuxième relecture, demandée par le propriétaire (« un audit bien profond et complet pour bien tout valider ») : un œil
neuf sur la zone du compte rendu `../a3_bases.md`, sans se fier à ses « vérifié sans problème ». Relu : `modules/bases`
(mod `crac`) en entier côté serveur (pièces, portes et sas, armoire et entretien, bases des clans, cœur de clan, Purge et
raids, échelles de raid, `/home` `/sethome` `/spawn` `/rtp`, tutoriel `crac:tuto`, zones interdites, carrières,
effacements, paquets des joueurs), les blocs et menus communs (armoire, coffres, caisse, échelle, explosifs), les
rendus et écrans des joueurs (survol), et les appels des autres mods vers CRac par réflexion.

**Rien n'a été testé en jeu** (pas de jeu ici). Fait sur ce PC : relecture du code ; `outils/verif_reflexion.py` (0 erreur,
dont les 45 appels des autres mods vers CRac : `WipeApi`, `CoeurApi`, `SpawnCracApi`, `BaseCracApi`, `TutorielApi`,
`VueApi`, `ClaimManager.getAt` / `basePres`, `PurgeHud.hauteur`, classes lues par l'anti-ESP de Spartan, toutes présentes
avec les mêmes signatures) ; deux essais hors du jeu en Java (règles des armoires de `ClaimData`, ancienne et nouvelle
version côte à côte ; cadence `allowBurst`) ; lecture des sources publiques de Forge 1.20.1 et de Mohist 1.20.1 pour
l'ordre des contrôles du clic gauche. La fabrication et les contrôles se font sur GitHub (« Contrôle de branche »).

En bref : les **4 changements du jour** de la zone font ce qu'ils disent, sans régression trouvée (le changement risqué,
les armoires gardées une par une, relit les bases déjà posées exactement comme avant) ; **1 défaut important corrigé**
(un jeu modifié pouvait geler le serveur par des clics gauches lointains) et 1 petit ; **aucune décision nouvelle** à
prendre, seulement 3 remarques sans effet en jeu. Aucune duplication d'objets trouvée. Version : **CRac 1.38.11-beta**
(le launcher donne le nouveau jar ; aucun canal réseau ne change : un jeu en 1.38.10 se connecte toujours).

---

## Changements du jour validés

| Commit | Ce qu'il fait | Verdict |
|---|---|---|
| `06449bbf5` | Objets à vie rendus et matériel de la première base : le stockage écrit sur le disque avant le joueur (`SafeSavedData.sauverMaintenant`) | **OK**. `sauverMaintenant` (`data/SafeSavedData.java` l. 194-222) relève et écrit tout de suite, sur le fil du serveur, après les écritures en file du même fichier (jamais une version plus ancienne par-dessus) ; écriture ratée : la donnée reste « à sauver ». Objets à vie (`bases/ObjetsAVieBases.java` l. 225-231) : fichier écrit, puis le joueur : bon ordre. Matériel de la première base : voir la remarque R1 (ordre presque toujours bon, une fenêtre de quelques millisecondes reste ; au pire un kit de départ en double après un plantage à ce moment précis). |
| `a47ec9766` | Armoires gardées une par une (`ClaimData`), effacements par armoire, tutoriel à deux d'un même clan, tronçons prêts, C4 de Tactique, noyau déclaré au serveur | **OK**, détail plus bas. |
| `8cb1e198f` | Échelle de raid abîmée seulement à portée de main | **OK** (`placable/EchelleServeur.java` l. 114-119). Même mesure que Forge (yeux → centre du bloc, portée + 1 bloc, Forge prend + 1,5) : un vrai coup est au plus à 5,4 blocs, la limite est 5,5. Mais le bloc visé était lu AVANT ce contrôle : corrigé, voir « Corrigé » n° 1. |
| `b37753d9e` | Cadence côté serveur : écran de mort « À la base » (2 d'un coup puis 1 / 0,5 s), onglet Accès (8 puis 8 / s) | **OK**. Essai hors du jeu de `allowBurst` (`network/PacketThrottle.java` l. 36-49) : un flot de 1 000 paquets par seconde pendant 10 s n'en laisse passer que 88 ; un joueur normal (recherche toutes les 250 ms, 3 clics d'un coup) n'est jamais arrêté ; l'horloge utilisée ne recule jamais. Oublié au départ du joueur et à l'arrêt. |

### Le changement risqué : les armoires gardées une par une (`claim/ClaimData.java`)

En simple : avant, CRac rangeait les armoires « une par clan et par monde » ; depuis ce midi, « une par armoire ». Le
fichier des armoires (`crac_claims.dat`) a exactement la même forme (une liste d'armoires) : rien à convertir.

- **Bases déjà posées sur la carte** : l'ancien fichier contient une armoire par clan ; relu par la nouvelle version, il
  donne les mêmes armoires, au même clan, et se réécrit à l'identique (`load` l. 211-228, `save` l. 232-243). Sur la carte,
  la règle « une base par clan » (`event/ClaimBlockHandler.java` l. 189, aussi pour une armoire retrouvée après un
  plantage) fait que les deux rangements tiennent toujours les mêmes armoires : protection (`getAt` l. 103-132),
  entretien, Purge, `/home`, textes « Base protégée », zones envoyées aux joueurs ne changent pas. Essai hors du jeu : 2 000
  parties de 60 poses et retraits au hasard, ancienne et nouvelle version côte à côte, et relecture de l'ancien fichier
  par la nouvelle : mêmes armoires, même armoire par clan, même fichier, à chaque pas. Rien de perdu ni en double.
- **Tous les appelants relus** (une trentaine : listes de toutes les armoires, recherches « l'armoire du clan ») : sur la carte, rien ne
  change ; au tutoriel, chaque cabane d'essai garde son armoire, sa protection, son entretien et sa sauvegarde, et la fin
  du tutoriel d'un joueur n'efface que les armoires posées sur SES pièces (`bases/EffacerBase.java` l. 115-126). Essai hors
  du jeu : deux cabanes d'un clan, fin de l'une puis de l'autre (dans les deux ordres), armoire remplacée à la même place :
  juste.
- **Effacements** (`/base effacer`, fin du tutoriel, `/wipe <joueur>`, `/wipe serveur`, clan dissous) : retirent l'armoire
  visée (`EffacerBase.java` l. 146-155, `WipeBases.java` l. 75-84, `heraliz/BasesClans.java` l. 245-270). L'ancien
  `releaseClaim` ne faisait rien de plus ici (l'armoire était déjà retirée juste avant). Clan dissous pendant le tutoriel :
  la cabane est retrouvée par son siège (`ClaimManager.buildingUnder`), chaque membre reçoit son groupe d'essai.
- **Le reste du commit** : tronçons complets pour les explosions (`raid/BlastDamage.java` l. 104), la repousse des
  carrières et les textes des bases protégées : même coût que l'ancien test, aucune case lue dans un tronçon en lecture.
  C4 de Tactique (`heraliz/HeralizC4.java` l. 44-50) : liste vide = plus rien de lu, même résultat. Tutoriel sans effet sur
  la réservation de 24 h (`heraliz/ArmoiresPerdues.java` l. 216-218 ; le retrait d'une armoire au tutoriel n'en crée pas
  non plus, l. 182) et sans message d'entretien au clan (`claim/ClaimMaintenanceManager.java` l. 340-345 ; l'entretien
  lui-même se calcule comme avant). Noyau demandé des deux côtés, 1.1.5 ou plus : le serveur et les joueurs ont la 1.1.11.

---

## Corrigé

Commit **`c814c5f42`**, CRac **1.38.11-beta**.

### 1. (Important) Clics gauches lointains d'un jeu modifié : le serveur chargeait le tronçon visé

En simple : quand un joueur commence à casser un bloc, son jeu envoie au serveur la position du bloc. Forge (et Mohist,
vérifié dans leurs sources) prévient les mods AVANT de vérifier que ce bloc est à portée de main. CRac regardait alors ce
bloc (pour refuser la casse en survie, et pour les échelles de raid). Un jeu modifié pouvait donc envoyer en rafale des
positions à des milliers de blocs : pour chacune, le serveur lisait (ou fabriquait) ce morceau de carte sur son fil
principal, en dizaines de millisecondes chacun, de quoi geler tout le serveur, et remplir le disque de carte neuve.
Maintenant, un bloc dans un morceau de carte pas complètement chargé n'est jamais lu : le clic est refusé tout de suite.
Un vrai joueur ne vise que des blocs tout près de lui, toujours chargés : rien ne change pour lui. Le refus est fait en
premier, donc les autres mods (qui ne lisent pas les clics refusés) ne voient plus ces clics non plus.

Pour les développeurs : `event/CasseBlocs.java` l. 58-65 (`clic`, priorité la plus haute, côté serveur seulement :
`Troncons.charge` faux → annulé sans lecture) ; `placable/EchelleServeur.java` l. 97-102 (`coup` reçoit aussi les clics
déjà annulés : même test avant la lecture). `StructureEvents.onLeftClick` ne reçoit pas les clics annulés. Les autres
mods du dépôt qui écoutent ce clic ne lisent pas le bloc (relu : Spartan, grades, casino, bunker, ClashWar, train,
réanimation) ; ceux des autres auteurs n'ont pas été relus.

### 2. (Petit) `/base effacer` et fin du tutoriel : seulement l'armoire

Même défaut que le point 6 du compte rendu `a3` (corrigé ce matin pour `/wipe serveur` seulement) : si l'armoire d'une
base avait disparu sans que sa zone soit libérée (carte retouchée, morceau de carte remis d'une sauvegarde), l'effacement
retirait le bloc qui était à sa place, même un bloc de la carte. Maintenant, seulement si c'est bien une armoire
(`bases/EffacerBase.java` l. 146-150). Rien ne change dans les autres cas.

---

## À décider par le propriétaire

Rien de nouveau à décider. Les propositions de `a3` non retenues restent des propositions (n° 1 à 6).

Remarques (aucun effet en jeu aujourd'hui, rien de changé) :
- **R1. Matériel de la première base** (`heraliz/BasesClans.java` l. 657 puis 702) : le coffre du clan reçoit le kit, son
  fichier part en écriture sur un autre fil, puis le fichier des bases est écrit tout de suite. Le commentaire dit « le
  fichier des bases est sur le disque avant le coffre » : presque toujours vrai, mais l'écriture du coffre, partie la
  première, peut finir avant. Un plantage du serveur dans ces quelques millisecondes redéposerait le kit de départ (plan,
  marteau, armoire, brindilles) une 2e fois. Risque négligeable ; le corriger demanderait de réordonner ce dépôt
  (« au pire perdu » au lieu de « au pire en double ») : pas conseillé sans besoin.
- **R2. Deux cabanes d'essai d'un même clan** : l'usure et la réparation de la passe d'entretien sont notées par clan
  (`claim/ClaimMaintenanceManager.java` l. 351-352), donc les deux cabanes suivent l'état de la dernière vue. Sans effet :
  les pièces de moins de 30 min ne s'usent jamais (`graceMinutes`) et un tutoriel dure moins.
- **R3. « L'armoire du clan » au tutoriel** : quand un clan y a deux cabanes, après un redémarrage la recherche
  « armoire du clan » (`getByGroup`) rend l'une ou l'autre, plus forcément la dernière posée (commentaire de `ClaimData`
  l. 24-29). Seuls usages touchés : `/base effacer <chef>` (déjà la proposition n° 1 de `a3`) et l'agrandissement par
  l'arbre du clan, qui peut agrandir une cabane d'essai (sans effet : parcelles à 256 blocs l'une de l'autre).

---

## Vérifié sans problème

- **Paquets des joueurs** (11) : tous repris sur le fil du serveur, avec distance, tronçon chargé, vue, droits du clan et
  cadence ; listes et textes bornés au décodage ; groupes et invitations refusés pour les bases des clans.
- **Duplications** : stock de l'armoire relu clic par clic (prise, dépôt, échange avec un objet marqué, touches 1 à 9,
  Maj + clic, armoire pleine : le compte tient toujours), entonnoirs, ramassage d'un objet posé (refusé s'il contient
  quelque chose), déplacement de l'armoire (stock vidé avant le retrait, un autre écran ouvert dessus ne peut rien en
  sortir), charge ratée reprise une seule fois, vitres et barreaux (posés = pris, retirés = rendus), démolition au
  tutoriel (rendu marqué, repris à la fin), cœur de clan (jamais jeté, jamais en double).
- **Droits** : portes, fenêtres, coffres (droit « Ouvrir les coffres »), casse et pose dans une base, échelles de raid,
  home (`/sethome` : droit « Gérer la base », debout sur une fondation de la base), onglet Accès (propriétaire seul).
- **Lenteurs** : rien de nouveau par tick ; recherche de base par cases (`getAt`), entretien étalé, textes des bases
  protégées une fois par minute, échelles revues toutes les 2 s, tickets de chargement rafraîchis sans fuite.
- **Fichiers** : toutes les données de CRac passent par `SafeSavedData` (écriture à côté puis renommée, fichier illisible
  mis de côté, jamais écrasé).
- **Liens** : `mods.toml` (noyau 1.1.5+ des deux côtés, Clans facultatif) ; appels par réflexion des autres mods vers CRac
  et de CRac vers eux : 0 erreur.
- **Mixins de CRac** : au démarrage d'essai « mixins stricts » lancé sur GitHub ce soir (21/11, 18 h 13 UTC, branche juste
  avant ce correctif, qui ne touche aucun mixin), aucune injection de CRac ratée sur Mohist (la seule ratée est dans le
  noyau, hors de cette zone).
- **Affichage chez les joueurs** (survol) : rendus des échelles, coffres, armoire, cœur, zones et fantômes sortis tout de
  suite quand il n'y a rien à montrer (déjà relus par les audits d'images des 19 et 20/11).

---

## À tester en jeu

Corrigé ici (CRac 1.38.11-beta) :
- [ ] En survie, frapper (clic gauche) un mur de sa base, une caisse, le sol : rien ne casse, comme avant.
- [ ] Échelle de raid : une dizaine de coups à la main la cassent, comme avant ; de loin (au-delà de la portée), rien.
- [ ] Carrière du monde des bases : ses blocs se cassent toujours ; armoire de sa base : se casse (gérer la base).
- [ ] Staff en créatif : casse toujours d'un clic, pièces comprises.
- [ ] `/base effacer <chef>` sur le serveur d'essai : la base et son armoire disparaissent, la carte autour reste.
- [ ] Journal du serveur : aucune erreur de `crac` ; `/diag etat` : rien de nouveau.

Changements du jour (rappel de `a3`, toujours à faire) : tutoriel à deux d'un même clan, échelle de raid à la main, mort
puis « À la base », onglet Accès en tapant vite, `/wipe serveur` sur le serveur d'essai.

Pas testé : tout en jeu (fabrication et contrôles sur GitHub seulement).
