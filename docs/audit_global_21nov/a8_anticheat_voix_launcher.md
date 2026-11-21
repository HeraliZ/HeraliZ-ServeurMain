# Audit global du 21/11 — zone a8 : anticheat, voix, launcher

Relu : `modules/anticheat` (Spartan : mouvement paquet par paquet, tirs TaCZ, anti-ESP et ses fils de calcul, contrôles
d'intégrité et défis, jeton du launcher, Gardien, règles « Injection » et leur disjoncteur « en rodage » changé ce matin,
captures d'écran, bans et registre des joueurs, alertes et fils Discord, porte des paquets, packs de textures),
`modules/voix` (CaraCrea : fil UDP de la voix, chiffrement ChaCha20-Poly1305, tri des voix, liste vocale par différences
et staff caché changés ce matin, groupes, sourdines) et `launcher/` (Gardien, options Java permises, signatures des mises à
jour et du pack, jeton, effacement après un ban). Aussi : ce que les autres modules appellent dans cette zone (noms lus par
réflexion, messages entre mods) et les cibles des Mixins sur TaCZ 1.1.8 (vérifiées dans son jar avec `javap`).

**Rien n'a été testé en jeu** (pas de jeu ici). Fait hors du jeu : relecture du code, essais Java purs (`RodageTest`,
nouveau `NetUtilTest` : 17 essais passés), essais du launcher (125 sur 126 passés ; le dernier, `emballage.test.js`, a
besoin des outils de fabrication d'Electron, absents ici), puis la fabrication et les contrôles sur GitHub.

Version : **Anticheat 2.15.4** (côté serveur seulement : le jeu des joueurs ne change pas ; le launcher donne le nouveau
jar comme d'habitude). Voix et launcher : **aucun changement** (pas de nouvelle version du launcher à publier).

En bref : 3 points corrigés (1 moyen, 2 petits), 10 propositions à décider (2 moyennes, le reste petit), aucun plantage
ni duplication trouvés, aucun paquet des joueurs accepté sans contrôle.

---

## Corrigé (commits `217d1161` et `634cbd00`)

### 1. Disjoncteur des règles en rodage : tous les joueurs pouvaient compter pour un seul PC (moyen)

En simple : depuis ce matin, une règle nouvelle (agent injecté, classe modifiée en mémoire, launcher imité, pack de
textures) se coupe toute seule quand **trois PC différents** la déclenchent en une heure : c'est le signe d'une fausse
alerte. Pour reconnaître « le même PC », le serveur regarde le compte, la connexion internet (le bloc d'adresses IP) et
l'identifiant matériel. Problème : derrière Docker / Pterodactyl (selon le réglage réseau de l'hébergeur), derrière un
proxy anti-DDoS ou en local, **tous les joueurs arrivent avec la même adresse** (celle de la passerelle, 172.x…). Le
serveur les prenait alors tous pour un seul PC : le disjoncteur ne sautait plus jamais, et une règle qui se trompe aurait
expulsé **tout le monde**, en boucle. Le reste de Spartan (bans, comptes liés) ignorait déjà ces adresses ; cette nouvelle
partie non.

Maintenant : seule une adresse **publique** compte (même règle que les bans). Une adresse de passerelle, de réseau local
ou de NAT d'opérateur n'est plus une clé du PC (il reste le compte et l'identifiant matériel). Rien ne change quand les
vraies adresses des joueurs arrivent au serveur.

Pour les développeurs :
- `modules/anticheat/src/serveur/java/com/Spartan/integrity/Injection.java` l. 229-245 (`pc`) : le bloc d'adresses
  seulement si `NetUtil.isPublicAddress(...)` ;
- `modules/anticheat/src/serveur/java/com/Spartan/util/NetUtil.java` : `isPublicAddress(InetAddress)` (même règle que
  `isPublicIp(String)`, qui l'appelle ; une IPv4 écrite en IPv6, `::ffff:a.b.c.d`, est jugée comme l'IPv4 qu'elle
  contient, commit `634cbd00`) ; test `src/test/java/com/Spartan/util/NetUtilTest.java`.

### 2. Packs de textures : le PC n'était pas encore connu quand la règle jugeait (petit)

En simple : à l'arrivée, le jeu envoie ses packs de textures **avant** ses identifiants matériels ; tout est mis en attente
jusqu'au `/login`, puis traité dans l'ordre. La règle « packs de textures » (en rodage) jugeait donc un joueur sans
connaître son PC : avec plusieurs comptes sur un même PC, chaque compte comptait à part (et pouvait couper la règle).
Maintenant, les identifiants matériels sont traités en premier quand l'attente se termine (un PC banni part aussi avant le
reste).

Pour les développeurs : `modules/anticheat/src/serveur/java/com/Spartan/serveur/ReceptionServeur.java` l. 134-142
(`tick`) : `TestPacket` d'abord, puis les autres paquets si le joueur est toujours là.

### 3. Alertes du staff en jeu : un jeu lancé sous le pseudo d'un op les recevait (petit, sécurité)

En simple : le serveur n'a pas de comptes Mojang (AuthMe). Quelqu'un qui se connecte avec le pseudo d'un op est vu comme
op par Minecraft tant qu'il n'a pas fait son `/login` ; il est refusé au bout de quelques secondes (pas de jeton du
launcher), mais pendant ce temps il recevait les alertes du staff dans son tchat (pseudos, détections, comptes liés,
captures reçues). Maintenant, les alertes en jeu ne vont qu'au staff **vérifié** (jeton du launcher et `/login` faits) ;
Discord et `alerts.log` ne changent pas.

Pour les développeurs : `AlertManager.staffEnJeu` (`alert/AlertManager.java` l. 31-38), utilisé l. 57 et 88, dans
`capture/CaptureManager.java` l. 186 et `scan/ScanManager.java` l. 854.

---

## À décider par le propriétaire

### P1. Disjoncteur (moyen) : les PC qui ont coupé une règle restent expulsés à chaque connexion

Décision de ce matin : une règle coupée se remet en service au bout d'une heure, et les PC qui l'ont coupée ne comptent
plus pour elle jusqu'au redémarrage (pour qu'un tricheur avec trois PC ne la coupe pas toutes les heures). Conséquence
pas prévue : si une règle se trompe pour **quelques joueurs honnêtes seulement** (un logiciel précis sur 3 ou 4 PC), ces
joueurs sont expulsés à **chaque** connexion après la remise en service, jusqu'au prochain redémarrage, et la règle ne
se recoupe jamais (il faudrait 3 PC nouveaux). Le texte du réglage (« une règle qui se trompe n'expulse jamais plus de
2 PC honnêtes d'affilée ») n'est donc plus tout à fait vrai.
- Option A (sans code, prudent) : `rodageRepriseMinutes = 0` dans `[injection]` (règle coupée jusqu'au redémarrage,
  comme avant ce matin).
- Option B (code) : un PC déjà compté qui redéclenche la règle après sa remise en service compte de nouveau, à condition
  qu'au moins un PC nouveau la déclenche aussi dans l'heure (un tricheur doit trouver un PC de plus à chaque fois ; des
  joueurs honnêtes ne restent pas bloqués quand la fausse alerte touche aussi d'autres PC).
- Option C : garder ainsi (le staff voit « Règle suspendue » et retire la règle de `indices` à la main si elle se trompe).
- Code : `integrity/Rodage.java` l. 148-151 (`dejaComptes`).

### P2. Disjoncteur (moyen) : la clé du Gardien comme signe d'un même PC pour « launcher imité »

La règle « clé du Gardien réutilisée » (`gardien:cle:`) est jugée à la réception du jeton, avant le `/login`, donc sans
identifiant matériel. Un faux launcher avec une clé fixe, trois comptes et trois connexions différentes (VPN) la coupe
pour une heure. Proposition : compter la clé du Gardien elle-même comme clé du PC (le vrai launcher en tire une neuve à
chaque lancement : aucun joueur honnête n'est touché) ; les comptes qui partagent une clé fixe ne compteraient qu'une fois.
Code : `integrity/Injection.java` `pc()`, clé connue dans `serveur/GardienServeur.java` (`CLE_JOUEUR`).

### P3. Disjoncteur (petit) : bloc d'adresses /24 et forfaits mobiles

Des joueurs honnêtes en 4G chez le même opérateur sortent souvent par le même bloc d'adresses : ils comptent pour un seul
PC, donc une fausse alerte qui les touche met plus longtemps à couper la règle (plus de joueurs expulsés avant). C'est le
prix du choix « plusieurs PC derrière la même connexion = un seul » ; à garder en tête, rien à changer sans décision.

### P4. Voix avant le `/login` (petit, sécurité)

La voix démarre dès l'arrivée, avant le jeton du launcher et le `/login`. Par la voie UDP (la normale), un jeu lancé sous
le pseudo d'un autre joueur peut donc parler en son nom pendant les quelques secondes avant son refus (près du point
d'arrivée). Proposition : pas de voix (ni parler ni entendre) tant que le joueur n'a pas fait son `/login` (CaraCrea
sait déjà lire AuthMe pour le créateur de personnage : `LoginCheck.loggedIn`, à relire toutes les 10 ticks comme le
staff caché). Code : `modules/voix/src/serveur/java/com/caracrea/server/VoiceServer.java` `vanish()` l. 511 et
`snapshot()` l. 446 (drapeau `TALK`).

### P5. Alertes (petit) : un membre du staff visé voit sa propre alerte

Les alertes en jeu partent à tout le staff, **y compris** le joueur visé s'il est du staff (les contrôles d'intégrité,
eux, l'excluent). Un staff qui triche sait donc qu'il est repéré. L'exclure ? (Attention : en essai sur soi-même, le
propriétaire ne verrait plus ses propres alertes en jeu, seulement sur Discord et dans `alerts.log`.) Code :
`alert/AlertManager.java` l. 56-60.

### P6. Coût réel de l'anticheat à 150 joueurs : jamais mesuré (info)

Le banc de 150 bots ne mesure presque rien de Spartan : les bots sont ignorés (ni mouvement, ni défis, ni contrôles, et
jamais « observateurs » pour l'anti-ESP : `SpartanEvents.java` l. 302, `esp/VisibilityEngine.java` l. 337). Les « 1,4 ms
par tick » du banc ne comptent donc pas le vrai travail. Le plus lourd attendu : l'anti-ESP quand tout le monde est
serré (arrivée après un redémarrage) : jusqu'à ~22 000 paires de joueurs passées en revue à chaque tick sur le fil du
serveur (le calcul des lignes de vue, lui, est à part). Proposition : un profil (`/diag profil`, « Lenteurs du serveur »,
60 s) un soir chargé, et/ou des bots « observateurs » pour le banc.

### P7. Images par seconde (petit) : le défi d'intégrité est calculé sur le fil du jeu chez le joueur

Toutes les 1 à 4 minutes, le jeu répond au défi en relisant toutes les classes de Spartan (empreinte et forme vivante) sur
le fil qui dessine les images : un à-coup de quelques millisecondes. On peut le faire à côté (comme le Gardien depuis la
2.15.1), mais il faut changer le jar des joueurs (jars joueur et serveur de la même fabrication, comme toujours). Code :
`src/client/java/com/Spartan/Client/PartieClient.java` l. 27-28.

### P8. Voix (tout petit) : un groupe vocal d'un staff caché reste visible

Un staff caché seul dans son groupe vocal : son nom n'apparaît plus, mais le groupe (son nom) reste dans la liste des
groupes des joueurs. Le retirer de la vue des joueurs quand il n'a que des cachés. Code : `VoiceServer.sendRoster` (l. 620).

### P9. Voix (tout petit) : essais de mot de passe des groupes jamais oubliés

`VoiceServer.ESSAIS_MDP` (l. 718) garde une petite entrée par joueur qui s'est trompé, même après son départ (quelques
octets par joueur, jamais vidés avant le redémarrage). À vider dans `onLogout` à la prochaine version de CaraCrea (pas
une raison d'en faire une maintenant).

### P10. Registre des joueurs écrit une fois par heure (tout petit)

`players.json` (IP et PC vus) n'est écrit qu'une fois par heure et à l'arrêt (`SpartanEvents.java` l. 270). Après un
plantage, les PC des joueurs arrivés dans l'heure sont perdus : un `/banpc` hors ligne sur l'un d'eux ne bannirait que le
compte et l'IP. Toutes les 10 minutes coûterait une copie du registre de plus (faite sur le fil du serveur, écrite à côté).

---

## Vérifié sans problème

- **Disjoncteur (changé ce matin)** : fusion des PC qui partagent une clé, fenêtre d'une heure, remise en service, PC déjà
  comptés, plafond de 64 clés, règles sans aucune clé : logique juste (13 essais `RodageTest`), fil du serveur seulement.
  Restent les points P1 à P3 (choix) et le point corrigé n° 1.
- **Staff caché dans la voix (changé ce matin)** : sa voix ne part qu'au staff (`VoiceRouting.route` et les crochets des
  autres mods `ApiView.add`), lui entend tout le monde ; deux vues de la liste vocale, jamais la ligne d'un caché hors du
  staff ; noms lus par réflexion (`GradesApi.cache`, `GradesApi.voitLesCaches`) présents. Le vanish lui-même passe par
  Bukkit : l'anti-ESP ne peut pas montrer un caché (il ne fait que retirer).
- **Fils de la voix** : un instantané par tick lu sans verrou (triple tampon, publication `volatile`), un tampon de
  chiffrement par fil, compteurs de nonces atomiques par session (jamais deux fois le même nonce), anti-rejeu vérifié après
  l'authentification, rien renvoyé à un paquet non authentifié, limites de débit par session ; voix par la connexion du
  jeu lue sur le fil réseau (jamais le fil du serveur) et bornée.
- **Paquets des joueurs** : tailles bornées partout (listes, textes, morceaux de rapports et de captures), paquets avant
  le `/login` retenus (128 au plus, 2 Mo), rapports et captures non demandés jetés, défis non sollicités ignorés, rapports
  du Gardien au plus un toutes les 5 s, touches et listes de packs limitées.
- **Fil du serveur** : Discord toujours à côté (file bornée de 300, une par seconde, 429 respecté), écritures sur disque à
  côté (`AsyncIO`, fichier temporaire puis renommage), analyse des contrôles d'intégrité à côté, date d'`officiel.json` lue
  à côté, anti-ESP calculé sur d'autres cœurs (résultat appliqué au tick suivant, montré dans le doute).
- **Tirs TaCZ** : cibles des Mixins vérifiées dans `tacz-1.20.1-1.1.8-hotfix.jar` (`LivingEntityShoot.shoot(...JFZ)`,
  `getShootCoolDown(J)`, `HitboxHelper.getFixedBoundingBox`, `SoundManager.sendSoundToNearby`, `IGunOperator.shoot`,
  `ShootResult`, `ShooterDataHolder.baseTimestamp`) ; tirs mis de côté oubliés à la déconnexion.
- **Liens avec les autres modules** : `ClansApi.clanDe`, `Matchs.partie/equipe/spectateur`, `Rechargements.ajouter`,
  `VueApi.formeVue` et les classes de CRac lues par l'anti-ESP, `com.caracrea.api.Arrivee.personnageFait` (Monde),
  `Catalog`/`Trait`/`Option` (Quêtes), `TabIcons.suffix` (Grades), message `ally_predicate` (train), clé `spartan_uid` :
  tous présents avec la bonne forme.
- **Bans et registre** : écriture atomique, fichier illisible mis de côté (jamais écrasé), IP privées jamais bannies,
  identifiants matériels génériques ignorés, un modérateur ne bannit pas un admin depuis le jeu, vidage à l'arrêt.
- **Codec Concentus** (BSD) : jar d'origine avec sa licence (`LICENSE-concentus.txt` mise dans le jar), aucun nom HeraliZ.
- **Launcher** : options Java en liste blanche, variables d'injection retirées, jeton jamais sur la ligne de commande,
  mises à jour et pack vérifiés par signature Ed25519 avant tout téléchargement (pas de retour à une version plus
  ancienne), effacement après un ban limité au dossier du launcher sans suivre les liens, programmes du Gardien en
  priorité basse, fenêtre Electron isolée (aucune page étrangère, liens externes contrôlés). Aucun changement, aucune
  publication à faire.

---

## À tester en jeu (rien de cette section n'a été testé)

- [ ] Se connecter avec le launcher (joueur et staff) : comme avant, « ✅ Jeu validé » ; aucune alerte « Règle
      suspendue ».
- [ ] Staff (op) connecté : les alertes Spartan arrivent dans le tchat **après** le `/login` (avant : rien, c'est normal).
      Discord et `config/spartan/alerts.log` comme avant.
- [ ] Journal du serveur, une connexion : `[Spartan] Packs de ressources de … :` apparaît comme avant ; un PC banni est
      refusé dès le `/login`.
- [ ] `/spartan status <joueur>` : rien de nouveau tant qu'aucune règle n'est suspendue.
- [ ] Si un jour « Règle suspendue » apparaît : la règle se remet en service une heure plus tard (alerte « Règle remise en
      service ») ; lire la proposition P1 avant de garder ce réglage.

Pas testé : tout ce qui précède, en jeu (aucun Minecraft ici) ; vrai serveur derrière Docker (les adresses vues par le
Ptero n'ont pas été relues pour cette partie).
