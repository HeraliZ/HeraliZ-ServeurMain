# Validation de l'audit du 21/11 — zone v8 : anticheat, voix, launcher

Deuxième relecture, demandée par le propriétaire (« un audit bien profond et complet pour bien tout valider ») : un œil
neuf sur la même zone que le compte rendu `../a8_anticheat_voix_launcher.md`, sans se fier à ses « vérifié sans
problème ». Relu : `modules/anticheat` (Spartan : disjoncteur des règles en rodage, porte des paquets avant le `/login`,
alertes, bans et adresses IP, jeton du launcher, Gardien, contrôles d'intégrité, défis, captures, tirs TaCZ, anti-ESP,
anti-duplication), `modules/voix` (CaraCrea : voix avant le `/login`, staff caché, liste vocale par différences, fil UDP,
groupes, sourdines) et `launcher/` (Gardien, options Java permises, jeton, signatures du pack, effacement après un ban).
Aussi : ce que les autres mods appellent dans cette zone (réflexion, messages entre mods) et les cibles des Mixins sur TaCZ.

**Rien n'a été testé en jeu** (pas de jeu ici). Fait hors du jeu, sur ce PC :
- essais Java purs de l'anticheat (javac + JUnit 1.10.2, empreinte SHA-1 du jar vérifiée) : **148 essais, tous passés**
  (23 classes d'essais, dont `RodageTest` 16, `NetUtilTest` 4, `JvmDurcieTest` 2). Les 16 autres classes d'essais ont
  besoin de Minecraft, Netty ou JNA : pas lançables ici, et la fabrication sur GitHub ne lance aucun essai ;
- essais du launcher (`npm test`, Node 22, dans une copie à part) : **129 sur 129 passés** ;
- `outils/verif_synchro.py` (liste vocale par différences) : 0 erreur ; `outils/verif_reflexion.py` : 0 erreur ;
- cibles des Mixins de Spartan sur `tacz-1.20.1-1.1.8-hotfix.jar` (le même que sur le serveur) relues avec `javap` : toutes
  présentes.

Sur GitHub, avec la correction (`7fc16a9a3`) : « Contrôle de branche » réussi (fabrication, contrôles, images) et
démarrage d'essai normal réussi. Dans son journal, Forge ne remet que les commentaires de `spartan-common.toml` (valeurs
inchangées) : déjà le cas avant le 21/11, sans effet.

En bref : les **9 changements du jour** de la zone font ce qu'ils disent, sans régression trouvée ; **1 défaut corrigé**
(Anticheat 2.15.6, sécurité, cas rare) ; **4 propositions** nouvelles, petites ; les propositions de la première relecture
que le propriétaire n'a pas choisies restent des propositions. Voix : aucun changement. Launcher : aucun changement,
**aucune publication du launcher à faire**.

---

## Changements du jour validés

Chaque commit du 21/11 touchant la zone a été relu ligne à ligne (ce qu'il dit faire, cas limites, fils, lenteurs,
textes). La liste vocale par différences est datée du 20/11 à 23 h 58 (heure UTC), donc du 21/11 à Paris : relue aussi.

| Commit | Ce qu'il fait | Verdict |
|---|---|---|
| `264894b1a` (Voix 1.7.0) | Liste vocale par différences numérotées | **OK**. Liste complète seulement à l'arrivée, au changement de vue ou sur demande ; numéro par vue ; un joueur à qui une différence manque redemande la liste, une fois toutes les 5 s au plus, et le serveur n'en accepte pas plus (`Synchro.java`, `VoiceClient.java` l. 183-247, `VoiceServer.java` l. 615-681). Tailles bornées au décodage (1 024 groupes, 4 096 joueurs). Essai hors du jeu : 16 000 ticks, 0 erreur, aussi avec 2 % et 20 % de pertes. |
| `5de420dbc` (Spartan 2.15.2) | Plus d'alerte « JvmHardening » pour chaque joueur du launcher | **OK**. Seule l'attache ouverte est signalée (`JvmDurcie.java` l. 22-25, `ReceptionServeur.java` l. 235-246) ; les textes envoyés par le jeu sont bien « attach activé » et « agents dynamiques autorisés » (`JvmProbe.java` l. 31-37). Le mode strict exigeait déjà la même chose. |
| `6eb1ad756` | Commentaire de `JvmDurcie` précisé | **OK** (aucun code changé). |
| `03e6801a2` (Spartan 2.15.3) | Disjoncteur compté en PC, remise en service au bout d'une heure | **OK**. Logique relue cas par cas (`Rodage.java` l. 135-201) : PC fusionnés quand ils partagent une clé, fenêtre d'une heure, plafond de 64 clés, tout sur le fil du serveur. Les deux défauts de ce commit ont été corrigés le jour même (adresse publique seulement, PC qui comptent de nouveau). Réglage `rodageRepriseMinutes = 60` présent dans `serveur/config/spartan-common.toml`. |
| `a1490b67b` (Voix 1.7.1, partie voix) | Staff caché : voix réservée au staff, absent de la liste vocale des joueurs | **OK**. Un caché n'est entendu que du staff, crochets des autres mods compris (`VoiceRouting.java` l. 151, `VoiceServer.java` l. 1009-1013) ; jamais sa ligne dans la vue des joueurs. « Voit les cachés » exige le `/login` (`GradesApi.voitLesCaches` → `VanishServeur.java` l. 108-111 de la boutique) : un jeu lancé sous le pseudo d'un staff ne voit rien. |
| `217d1161c` (Spartan 2.15.4) | Disjoncteur sur les adresses publiques ; identifiants matériels avant les packs ; alertes au staff vérifié | **OK** (`Injection.java` l. 229-247, `ReceptionServeur.java` l. 127-142, `AlertManager.java` l. 36-38, utilisé l. 57 et 88, `CaptureManager.java` l. 186, `ScanManager.java` l. 854). En relisant la file d'attente de ces paquets, un défaut plus ancien a été trouvé et corrigé (voir « Corrigé »). |
| `634cbd00e` | IPv4 écrite en IPv6 jugée comme l'IPv4 | **OK** (`NetUtil.java` l. 32-71, 4 essais). Sans effet aujourd'hui : le serveur n'écoute qu'en IPv4 (`server-ip=0.0.0.0`), Java donne déjà des adresses IPv4 ; c'est une sécurité en plus. |
| `62fbb244a` (Spartan 2.15.5) | Après une remise en service, les PC qui ont coupé la règle comptent de nouveau avec au moins un PC nouveau | **OK** (`Rodage.java` l. 153-201, 4 essais de plus, 16 passés). |
| `0dc735cb7` (CaraCrea 1.7.2) | Pas de voix avant le `/login`, ni parler, ni entendre, ni groupes | **OK**. Sans `/login` : ni drapeau « parle » ni « entend » (`VoiceServer.java` l. 478-493) ; la voix qui arrive est jetée avant tout envoi et avant tout événement « parle » (l. 842) ; groupes refusés (l. 744). `/login` relu tous les 2 ticks tant qu'il manque (la voix revient au plus 1/10 de seconde après), puis une fois par seconde (`/logout`). AuthMe absent, désactivé ou illisible : la voix marche comme avant (`LoginCheck.java` l. 26-48) ; elle ne peut donc jamais rester coupée à cause de cette règle. Coût : quelques appels courts par tick, rien de plus. Deux petites suites possibles : P1 et P2 plus bas. |

---

## Corrigé

### 1. File d'arrivée d'une connexion non vérifiée gardée trop longtemps (moyen, sécurité, cas rare) — commit `94647411c`, Anticheat 2.15.6

En simple : à son arrivée, le jeu envoie à Spartan quelques paquets (identifiants du PC, liste des mods, packs de textures,
numéro de version de Spartan). Tant que le joueur n'a pas son jeton du launcher et fait son `/login`, ils attendent dans
une file, puis sont traités. La file d'une connexion qui partait avant d'être vérifiée n'était vidée qu'à la fin du tick :
dans un cas rare (nouvelle connexion du même compte acceptée dans ce même tick), elle pouvait passer à la nouvelle
connexion et être traitée comme la sienne. Maintenant la file est oubliée dès la déconnexion. Rien ne change pour une
connexion normale (le jeu envoie ses propres paquets d'arrivée).

Côté serveur seulement : le jar des joueurs ne change que par son numéro de version (même canal, même fabrication que le
jar du serveur : la mise en ligne donne les deux, comme d'habitude).

Pour les développeurs : `modules/anticheat/src/serveur/java/com/Spartan/serveur/ReceptionServeur.java` l. 282-293
(`oublierTouches` : `ATTENTE.remove(id)`), appelé par `SpartanEvents.onLogout` (l. 325). La file elle-même : l. 124-142.

---

## À décider par le propriétaire

### P1. Voix par la connexion du jeu avant le `/login` : gardée dans la file de Spartan (petit) — fait, voir « Fin du 21/11 »

Un joueur dont la voix ne passe pas par l'UDP (réseau qui le bloque : il passe tout seul par la connexion du jeu) et qui
parle pendant qu'il tape son `/login` : ses morceaux de voix sont retenus par la porte de Spartan avec les autres paquets
des mods (128 au plus), puis relâchés après le `/login`. Deux effets possibles : la file se remplit en 2 à 3 secondes et
les paquets des autres mods envoyés ensuite avant le `/login` sont jetés ; et un petit bout de voix d'avant le `/login`
peut être rejoué juste après. Depuis aujourd'hui, la voix d'avant le `/login` est de toute façon jetée par CaraCrea : la
garder ne sert à rien.
Proposition : que CaraCrea lise sa voix avant la porte de Spartan (elle serait jetée tout de suite avant le `/login`, comme
par l'UDP), ou que la porte jette la voix au lieu de la garder. Côté serveur seulement. Les joueurs en UDP (la normale) ne
sont pas concernés.
Code : `modules/voix/src/serveur/java/com/caracrea/server/VoiceInbound.java` l. 34-48 (posé devant `packet_handler`, donc
après la porte), `modules/anticheat/src/serveur/java/com/Spartan/integrity/PorteJoueurs.java` l. 37, 181-217.

### P2. Voix : après une seule erreur de lecture d'AuthMe, plus de contrôle du `/login` jusqu'au redémarrage (tout petit) — fait, voir « Fin du 21/11 »

C'est voulu « ouvert dans le doute » (le créateur de personnage ne doit jamais rester bloqué) ; mais une seule erreur,
même passagère, coupe la règle « pas de voix avant le `/login` » jusqu'au redémarrage, sans le dire au staff. Spartan, lui,
se rebranche à AuthMe au tour suivant. Proposition : réessayer de lire AuthMe au plus une fois par minute après une
erreur (toujours ouvert pendant ce temps). Code : `modules/voix/src/serveur/java/com/caracrea/server/LoginCheck.java`
l. 41-47.

### P3. Anti-duplication : un relevé de tout le serveur dans un seul tick, toutes les 20 s (info, à mesurer)

Le relevé relit d'un coup les inventaires, coffres de l'Ender, Curios et sacs de tous les joueurs (pour voir un même objet
à deux endroits au même instant). Avant son allègement (2.9.5), le code l'estimait à « plusieurs dizaines de ms » à 150
joueurs ; depuis, le réglage dit « ~1 ms », mais ce n'a jamais été mesuré avec de vrais joueurs (les bots du banc sont
ignorés par ce relevé). À mesurer un soir chargé avec `/diag profil`, en même temps que l'anti-ESP (proposition P6 de la
première relecture). S'il se voit : relevé moins souvent (`scanSeconds` de `[antidupe]`, réglage seulement). Code :
`modules/anticheat/src/serveur/java/com/Spartan/check/item/AntiDupe.java` l. 63-73, 120-133.

### P4. Launcher : deux PowerShell à la fois dans un cas de secours (tout petit, à la prochaine version du launcher)

Le launcher 1.18.1 ne lance plus qu'un PowerShell à la fois, sauf quand `tasklist` échoue : la liste des programmes passe
alors par un second PowerShell pendant que celui des titres de fenêtres tourne encore. Rare ; à régler seulement si le
launcher est republié pour autre chose. Code : `launcher/app/assets/js/gardien.js` l. 208-227.

### Restent ouvertes (première relecture, non choisies par le propriétaire)

P2 (clé du Gardien comme signe d'un même PC), P3 (bloc d'adresses et forfaits mobiles), P5 (un staff visé voit sa propre
alerte), P6 (coût réel de l'anticheat à 150 joueurs jamais mesuré), P7 (défi d'intégrité calculé sur le fil de l'image
chez le joueur), P8 (groupe vocal d'un staff caché visible), P9 (essais de mot de passe des groupes jamais oubliés), P10
(registre des joueurs écrit une fois par heure) : détail dans `../a8_anticheat_voix_launcher.md`. **P2, P7, P8 et P9 faits
ensuite** (« Fin du 21/11 » ci-dessous).

---

## Vérifié sans problème

- **Disjoncteur des règles en rodage** : relu cas par cas (même PC sous plusieurs comptes ou derrière la même connexion,
  PC qui relie deux PC notés à part, sortie de la fenêtre, remise en service, PC qui l'ont déjà coupée, règles sans clé,
  seuil 0) ; appelé seulement sur le fil du serveur (`Injection.juger`, `rodageAutorise`) ; textes des alertes justes.
- **Porte des paquets avant le `/login`** : chaque connexion a sa propre file (fil réseau), 128 paquets et 2 Mo au plus,
  tampons libérés, rien gardé d'une connexion à l'autre (`PorteJoueurs.java`). AuthMe injoignable : la porte reste fermée
  (choix d'origine, écrit dans le journal).
- **Alertes au staff vérifié** : toutes les alertes en jeu de Spartan passent par `AlertManager.staffEnJeu` ; Discord et
  `alerts.log` inchangés ; le staff qui voit tout à travers les murs reste coupé (`staffSeesAll = false`).
- **Bans et adresses** : bans du compte, de l'IP publique et du PC ; un fichier illisible est mis de côté, jamais écrasé ;
  écriture « fichier temporaire puis renommage » sur un fil à part, attendue à l'arrêt (`BanManager.java`, `JsonFiles.java`,
  `AsyncIO.java`).
- **Jeton du launcher** : signature vérifiée en temps constant, lié au pseudo (casse comprise), durée bornée
  (`LaunchToken.java`) ; jamais écrit dans le journal ; jamais sur la ligne de commande du jeu (`launcher/app/assets/js/spartan.js`).
- **Discord** : file bornée (300), un envoi à la fois par webhook, jamais d'attente sur le fil du serveur
  (`DiscordWebhook.java`, `FilsDiscord.java`).
- **Captures d'écran** : seulement pour un joueur vérifié qui les a autorisées, demandées par le serveur, bornées (une
  toutes les 10 s, 30 par heure), vérifiées comme JPEG, écrites sur un fil à part, nom de fichier nettoyé (`CaptureManager.java`).
- **Défis d'intégrité** : calcul de la réponse attendue court (moins d'une milliseconde), réponse vérifiée en temps
  constant, une seule prolongation silencieuse avant de compter un retard (`ChallengeManager.java`).
- **Voix** : instantané par tick lu sans verrou par les fils de la voix ; rien n'est renvoyé à un paquet UDP non
  authentifié ; anti-rejeu après authentification ; limites de débit par session ; sourdines écrites rarement (commande du
  staff), fichier illisible jamais réécrit.
- **Liens avec les autres mods** : `com.caracrea.api.Arrivee.personnageFait` (monde), `Trait`/`Catalog`/`Option`
  (quêtes), `TabIcons.suffix` (grades), `VoiceEvent$TalkStart/TalkStop` et `CaraCreaConfig.SERVER` (anti-ESP de Spartan),
  `fr.heraliz.reanimation.core.Downed.isDowned`, `fr.heraliz.noyau.api.Matchs.partie/equipe/spectateur`,
  `GradesApi.cache/voitLesCaches`, message `ally_predicate` (train), clé `spartan_uid` : tous présents, même forme.
- **Mixins de Spartan sur TaCZ 1.1.8** : `LivingEntityShoot.shoot(…JFZ)`, `getShootCoolDown(J)`,
  `HitboxHelper.getFixedBoundingBox` (lecture de la latence du joueur), `SoundManager.sendSoundToNearby` : présents.
- **Launcher** (aucun changement aujourd'hui) : options Java en liste blanche, variables d'injection retirées, pack et mises
  à jour vérifiés par signature Ed25519 avant tout téléchargement, effacement après un ban limité au dossier du launcher
  sans suivre les liens, programmes du Gardien en priorité basse. 129 essais passés.

---

## À tester en jeu (rien de cette section n'a été testé)

- [ ] Connexion normale par le launcher : « ✅ Jeu validé » comme avant ; aucune alerte « Règle suspendue » ni « Conformité ».
- [ ] Se connecter avec un compte A, fermer le jeu de force (sans quitter) et revenir tout de suite avec le même compte :
      on entre, aucune alerte, aucun refus ; journal du serveur : `[Spartan] Packs de ressources de A :` une fois par
      connexion.
- [ ] Staff (op) : les alertes Spartan arrivent dans le tchat seulement après le `/login`.
- [ ] Voix : avant le `/login`, le nouveau venu n'est pas entendu et n'entend rien ; juste après, tout marche dans les deux
      sens, groupes compris. Un joueur dont l'UDP est bloqué (réglage « connexion du jeu » dans les réglages de la voix) :
      pareil.
- [ ] Journal du serveur au démarrage et après une heure : aucune erreur de `spartan` ni de `caracrea`.

Pas testé : tout ce qui précède, en jeu (aucun Minecraft ici) ; rien de mesuré avec 150 vrais joueurs.

---

## Fin du 21/11 (demande du propriétaire : « tout lançable et terminé à 100 % »)

En ligne avant cette suite : `main` = `1607d9655` (Spartan 2.15.6, CaraCrea 1.7.2). Versions de cette suite :
**Spartan 2.15.7** (commit `4dcf8e70b`) et **CaraCrea 1.7.3** (commit `f9620e309`). **Rien n'a été testé en jeu.**

**Jar des joueurs** : celui de **Spartan change** (point 6 : le calcul du défi a changé de fil chez le joueur). Comme à
chaque version de Spartan, le jar des joueurs et celui du serveur viennent de la même fabrication et le launcher donne le
nouveau ; un jeu resté sur l'ancien est refusé avec la consigne de relancer le launcher. Celui de **CaraCrea ne change que
par son numéro** (tout le reste est côté serveur, même canal de la voix).

### Fait

1. **Voix d'avant le `/login` (P1 ci-dessus)** — CaraCrea 1.7.3. En simple : pour un joueur dont la voix passe par la
   connexion du jeu (UDP bloqué), la voix est maintenant lue avant la porte de Spartan et jetée tant qu'il n'a pas fait son
   `/login`, exactement comme par l'UDP. Elle ne prend plus de place dans la file de la porte (les paquets des autres mods ne
   sont plus jetés à cause d'elle) et rien n'est rejoué après le `/login`. Les autres paquets de la voix (réglages, groupes)
   passent toujours par la porte. Pour les développeurs : `VoiceInbound.java` l. 22-30 et 50 (posé devant `spartan_porte`
   quand il est là, sinon devant `packet_handler` comme avant) ; `VoiceServer.java` l. 877-887 (`relayTcp` : jetée sans
   `/login`).
2. **AuthMe illisible dans la voix (P2 ci-dessus)** — CaraCrea 1.7.3. Une erreur de lecture d'AuthMe ne coupe plus la
   règle « pas de voix avant le `/login` » jusqu'au redémarrage : AuthMe est relu une minute plus tard (ouvert dans le doute
   entre-temps, comme avant, pour que le créateur de personnage ne bloque jamais personne) ; pareil si son API n'est pas
   encore prête ou s'il est coupé. Une ligne dans le journal à la première panne et quand il redevient lisible, jamais une
   par minute. `LoginCheck.java` l. 15-60 et 110-118.
3. **Groupe vocal du staff caché (a8 P8, staff caché partout)** — CaraCrea 1.7.3. Un groupe où il n'y a que du staff caché
   n'est plus dans la liste des groupes des joueurs hors du staff (le staff le voit toujours) ; pour eux il n'existe pas :
   le rejoindre ne fait rien. Dès qu'un membre visible y est, ou que le staff se montre, le groupe réapparaît (au plus une
   seconde). `VoiceServer.java` l. 614-645 (`HiddenGroup`, `hiddenGroup`), 668-672, 714-725 et 820-824.
4. **Essais de mot de passe des groupes (a8 P9)** — CaraCrea 1.7.3. Une fois par minute, les entrées de plus d'une minute
   sont retirées (elles repartaient déjà de zéro au prochain essai) : la table ne grossit plus jusqu'au redémarrage. Une
   attente en cours (5 erreurs, une minute) est gardée, même si le joueur se reconnecte. `VoiceServer.java` l. 427-429 et
   773-788.
5. **Clé du Gardien comme signe d'un même PC (a8 P2)** — Spartan 2.15.7. Pour le disjoncteur des règles en rodage, un PC =
   son compte, son bloc d'adresses, ses identifiants matériels **et la clé du Gardien de son lancement** (prouvée par le
   jeton du site). Sûr : le vrai launcher en tire une neuve à chaque lancement, deux PC honnêtes ne la partagent jamais (rien
   ne change pour eux, une fausse alerte se coupe toujours au 3e PC) ; des comptes qui partagent une clé fixe ne comptent
   qu'une fois, même avant le `/login` (règle « clé réutilisée », jugée sans identifiant matériel). Valable pour toutes les
   règles en rodage (même raison). `Injection.java` l. 226-241, `GardienServeur.java` l. 90-98 (`clePc`), texte du réglage
   `[injection] rodage` (`SpartanConfig.java` et `serveur/config/spartan-common.toml`), README ; essai
   `RodageTest.desComptesQuiPartagentUneCleDuGardienNeComptentQuUneFois` (17 essais).
6. **Défi d'intégrité hors du fil de l'image chez le joueur (a8 P7)** — Spartan 2.15.7. La réponse au défi (empreinte et
   forme de toutes les classes de Spartan, quelques millisecondes toutes les 1 à 4 minutes) est calculée sur un fil à part
   au lieu du fil qui dessine les images ; la réponse part ensuite du fil du jeu, comme avant. Sûr côté fils : mêmes
   opérations que le contrôle d'intégrité, qui les fait déjà sur son propre fil (classes lues sans être initialisées,
   lecture des membres par réflexion) ; les tables de l'empreinte sont protégées (`synchronized`) ; un défi après l'autre,
   dans l'ordre ; même réponse (le serveur ne voit aucune différence). `PartieClient.java` l. 23-71.

### Pas fait (choix du propriétaire)

a8 P3 (bloc d'adresses /24), a8 P5 (un staff visé voit sa propre alerte), P4 ci-dessus (deux PowerShell du launcher) :
**pas de publication du launcher**. Restent des mesures ou de petites propositions : a8 P6 et P3 ci-dessus (à mesurer avec
`/diag profil` un soir chargé), a8 P10 (registre écrit une fois par heure).

### Contrôles hors du jeu

Essais Java purs de l'anticheat : **149 passés** (dont `RodageTest` 17) ; launcher : **129 sur 129** (inchangé, identique à
`main`) ; `outils/verif_synchro.py` et `outils/verif_reflexion.py` : 0 erreur.

Sur GitHub, avec ces deux versions (`19008fffa`) : « Contrôle de branche » réussi (fabrication, contrôles, images) et
démarrage d'essai réussi (serveur prêt en 24 s ; Forge ne remet toujours que les commentaires de `spartan-common.toml`,
valeurs inchangées, comme avant le 21/11).

### À tester en jeu (rien de cette suite n'a été testé)

- [ ] Connexion normale par le launcher : « ✅ Jeu validé » comme avant (le launcher donne le nouveau Spartan) ; après
      10 minutes de jeu, aucune alerte « Integrity » ni « Conformité » ; aucune saccade nouvelle toutes les 1 à 4 minutes.
- [ ] Voix d'un joueur dont l'UDP est bloqué (réglages de la voix : « connexion du jeu ») : il parle pendant son `/login` ;
      personne ne l'entend avant, rien d'ancien n'est rejoué juste après ; après le `/login`, la voix marche tout de suite et
      les autres mods aussi (tab, chat, interface, armes).
- [ ] Staff caché S seul dans un groupe vocal : un joueur A hors du staff ne voit pas ce groupe dans la liste des groupes ;
      un autre staff B le voit. S se montre : le groupe apparaît chez A (au plus une seconde).
- [ ] Groupe avec mot de passe : 5 erreurs, attente d'une minute, puis le bon mot de passe marche.
- [ ] Journal du serveur au premier joueur : « AuthMe found: the first-join creator and the voice wait for /login » ;
      aucune erreur de `spartan` ni de `caracrea`.
- [ ] `/spartan status <joueur>` : rien de nouveau tant qu'aucune règle n'est suspendue.

Pas testé : tout ce qui précède, en jeu (aucun Minecraft ici).
