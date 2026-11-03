# Essais en jeu — chantier « anticheat-esp » (retours du 03/11)

Spartan **2.11.0** (`anticheat`), launcher **1.16.2** (pas publié), extension du site **1.4.1**. Rien de tout cela n'a été
testé en jeu, sur le site en ligne ni sur un PC Windows. Gradle ne marche pas sur la machine de travail : seule la partie
« mesure » du suivi à travers les murs (Java pur) a été compilée et essayée hors du jeu ; les tests du launcher passent ;
les fichiers PHP passent `php -l`. Le reste sera compilé par le contrôle de branche de GitHub.

## Ordre de mise en ligne

1. Envoyer sur `main` : serveur (Spartan 2.11.0 et `serveur/config/spartan-common.toml`) et site (extension 1.4.1).
2. Publier le launcher 1.16.2 (onglet Actions, « Launcher HeraliZ », case **publier**).
3. Le serveur refuse maintenant les anciens launchers (`ancienLauncher = "refuser"`, `required = true`) avec le message
   « ton launcher n'est pas à jour, ferme-le et rouvre-le » : les launchers Windows et AppImage se mettent à jour seuls
   au démarrage. Si beaucoup de joueurs Mac ou `.deb` restent bloqués, repasser `ancienLauncher` à `"alerte"` (sans
   redémarrer).

Réglages à couper tout de suite en cas de souci (fichier `config/spartan-common.toml` du Ptero, pris à chaud) :
`[suiviMurs] enabled = false`, `[gardien] preuveExigee = "alerte"`, `[injection] expulser = false`.

## Point 1 — suivi à travers les murs (alerte + capture, jamais de sanction)

Pour essayer sans tricher : deux comptes **qui ne sont pas du même clan** (sinon « alliés », jamais comptés), en appel
Discord. Le « faux tricheur » (A) reste derrière un mur ; l'autre (B) se déplace derrière le mur **accroupi** (ou à plus de
16 blocs) et annonce sa position en vocal ; A garde son viseur sur B à travers le mur. Sur le serveur d'essai, baisser
d'abord les seuils de l'alerte : `[suiviMurs] episodesMin = 1`, `dureeTotaleMinSecondes = 3`.

- [ ] B fait des allers-retours (zigzags, arrêts, demi-tours) derrière un mur plein (pierre, béton, mur de base CRac),
      A le suit au viseur 5 s : une ligne dans `config/spartan/suivi.log` (durée, angle moyen, corrélation, virages,
      distance). Avec les seuils baissés : alerte « Suivi à travers les murs » dans le salon Discord de Spartan et
      capture d'écran de A (s'il a autorisé les captures dans le launcher).
- [ ] Avec les seuils normaux (3 épisodes, 12 s en 15 min) : une seule alerte, puis plus rien pendant 30 min.
- [ ] `/spartan esp A` : dernière ligne « suivi à travers les murs : N épisode(s) suspect(s) récent(s) ».

Ne doit JAMAIS donner de ligne dans `suivi.log` (A suit B pareil au viseur) :
- [ ] B derrière une **vitre**, une fenêtre de base CRac, des **barreaux**, une clôture, une **haie de feuilles**.
- [ ] B derrière une **porte fermée** (vitrée ou non), une **trappe**, une porte **ouverte** ; derrière des **barrières**
      ou des blocs invisibles.
- [ ] B qui **tire** (ou recharge) derrière le mur, et jusqu'à 4 s après.
- [ ] B qui **parle** en vocal de proximité derrière le mur, et jusqu'à 4 s après.
- [ ] B qui **marche debout** (pas accroupi) à moins de 16 blocs (on entend ses pas).
- [ ] B **en véhicule** derrière le mur.
- [ ] B vu il y a moins de 4 s (il passe derrière le mur, A continue de le suivre).
- [ ] A et B du **même clan** ; ou un coéquipier de A (même clan) qui voit B pendant que A le suit (annonces, ping).
- [ ] A qui **tient un angle** sur une porte pendant que B va et vient juste derrière ; A qui **tourne autour** de B
      immobile en gardant le viseur sur lui ; B qui court **tout droit** derrière un long mur et A qui prolonge sa course.
- [ ] B à moins de 4 blocs ; A en spectateur ou en créatif ; B en surbrillance.
- [ ] Partie normale à 2 ou plus pendant 30 min (combats en ville, bâtiments, forêts) : `suivi.log` vide.

Coût : `/spartan esp` pendant une soirée chargée : le temps du thread serveur ne doit pas bouger de façon visible.

## Point 2 — launcher modifié, imité ou trop ancien : connexion refusée

Ce qui change pour le joueur honnête : rien (launcher à jour). Ancien launcher : message « Ton launcher HeraliZ n'est pas à
jour. Ferme-le complètement puis rouvre-le… ».

- [ ] Launcher 1.16.2 : connexion normale ; « Jeu validé » ; champ « Gardien du launcher » : « actif, rapports signés… ».
      Aucune alerte « Launcher … connexion refusée ».
- [ ] Rester 3 min au /login avant de taper son mot de passe, puis jouer : pas de refus (le délai de 120 s court après le
      /login).
- [ ] Fermer le launcher de force (gestionnaire des tâches) JUSTE après le lancement du jeu, puis se connecter : refus
      « le launcher HeraliZ ne répond pas… » environ 2 min après le /login ; alerte « Launcher » pour le staff.
- [ ] Fermer le launcher de force EN PLEINE PARTIE (après la première minute) : pas d'expulsion, alerte « Gardien muet »
      comme avant.
- [ ] Ancien launcher (1.15.x) : refus avec le message de mise à jour ; alerte « Gardien … connexion refusée : ancien
      launcher ».
- [ ] Jeu lancé autrement (CurseForge, Prism…) : refusé comme avant (« lancez le jeu depuis le launcher HeraliZ »).

Pourquoi un refus propre et pas « faire crasher le jeu » : un plantage volontaire se contourne (le tricheur retire le code
qui plante) et fait plus de dégâts chez les honnêtes (un faux positif = jeu qui plante sans explication, rapports de
plantage, joueur perdu). Le serveur, lui, décide seul : il ferme la connexion avec un message clair.

## Point 3 — injection : alerte Discord + expulsion

Message au joueur : « Connexion fermée : un programme modifie le jeu ou s'y injecte… ». Capture d'écran demandée, expulsion
5 s plus tard. Alerte « Injection — EXPULSÉ : … [source] » au staff.

- [ ] (serveur d'essai) Cheat Engine ouvert pendant la partie : en moins de 30 s, alerte « Gardien » puis « Injection »,
      capture, expulsion. Relancer le jeu avec Cheat Engine ouvert : le launcher refuse (« Ferme Cheat Engine pour
      jouer »).
- [ ] (serveur d'essai) Jeu lancé à la main avec `-javaagent:quelquechose.jar` : expulsion « agent Java au lancement ».
- [ ] Joueur honnête avec Discord (overlay actif), Steam (overlay), OBS qui enregistre, MSI Afterburner / RivaTuner,
      GeForce Experience, un antivirus : aucune expulsion, aucune alerte « Injection ».
- [ ] x64dbg, Process Hacker, Recaf ouverts : alerte « Gardien » seulement, pas d'expulsion.
- [ ] Un fichier texte nommé « Cheat Engine.txt » ouvert dans Notepad++ : alerte « Gardien » « cheat engine (titre d'une
      fenêtre seulement) », pas d'expulsion ; le launcher lance le jeu.

Déclenchent l'expulsion : programme attaché au jeu en cours de partie, anticheat modifié en mémoire, client de triche
connu, agent Java / débogueur / options d'injection au lancement, réponse fausse au défi d'intégrité, code natif caché
(thread hors module, affichage détourné vers du code sans module) **revu au contrôle suivant**, et ces programmes ouverts
sur le PC : Cheat Engine, ArtMoney, Squalr, Extreme Injector, Xenos, GH Injector, DLL Injector, kdmapper.
Restent des alertes : DLL au nom ou à l'emplacement douteux, overlay inconnu, threads sans nom, DLL chargée en cours de
partie, débogueurs, décompilateurs, Process Hacker / System Informer, Gardien muet, jar changé pendant la partie.

## Point 4 — lancement bloqué par le launcher : info sur Discord

Message posté dans le salon privé du staff (celui de la validation des comptes) : « Launcher : <pseudo> — <raison> : détail
(launcher 1.16.2) ».

- [ ] Ouvrir Cheat Engine puis cliquer « Jouer » : le launcher refuse ; message sur Discord « programme d'injection ou
      d'édition de la mémoire ouvert (lancement refusé) : Cheat Engine ».
- [ ] Poser un jar dans le dossier `mods` du jeu, puis « Jouer » : le jeu se lance (jar mis de côté) ; message « jar(s)
      en trop dans le dossier mods (mis de côté) : 1 jar(s) : nom.jar ».
- [ ] Modifier un jar de mod du dossier du launcher, puis « Jouer » : le launcher le retélécharge tout seul (contrôle de
      Helios) et lance le jeu, sans message (rien de bloqué). Le message « fichier du jeu différent du pack signé » ne
      part que si un fichier passe ce premier contrôle mais pas l'empreinte SHA-256 signée (fichier trafiqué avec soin) :
      pas facile à reproduire, pas à tester.
- [ ] Ouvrir Cheat Engine et cliquer « Jouer » 3 fois de suite : un seul message par compte et par raison en 10 min.

## Retours du propriétaire (02-03/11)

### « Le Gardien n'arrive jamais à lister les programmes »
Cause : la commande `tasklist /V` (titres ET comptes Windows de tous les programmes) est très lente (souvent plus de 12 s au
lancement du jeu), coupée, liste perdue. Maintenant : liste rapide, titres à part.
- [ ] Se connecter avec le launcher 1.16.2 : plus d'alerte « Gardien : le launcher n'arrive pas à lister les programmes ».
- [ ] Sur un PC chargé (navigateur, Discord, OBS ouverts) : idem.

### « La validation n'est jamais reçue »
Deux trous corrigés : une validation arrivée après l'alerte (60 s) n'était plus jamais annoncée ; une erreur du serveur
dans l'analyse était présentée comme « aucun rapport reçu ».
- [ ] Connexion normale : « ✅ Jeu validé » dans le fil Discord du joueur et dans la console.
- [ ] Si « ⚠ Validation jamais reçue » apparaît encore : noter le champ « Ce qui manque » (launcher, Spartan, défi ou
      contrôle d'intégrité) ; si le jeu est validé ensuite, « ✅ Jeu validé (en retard) » suit.
- [ ] `/spartan status <joueur>` : ligne « validation : … ».

## À régler plus tard

- Seuils du suivi à travers les murs : regarder `config/spartan/suivi.log` après une semaine ; baisser `episodesMin` ou
  `correlationMin` seulement si des tricheurs avérés y apparaissent sans alerte, jamais l'inverse.
- `[gardien] preuveSecondes` (120 s) : à garder tant qu'aucun joueur honnête n'est refusé.
