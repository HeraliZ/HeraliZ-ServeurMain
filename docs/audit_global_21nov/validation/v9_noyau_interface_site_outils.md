# Validation du 21/11 — v9 : noyau, interface, emotes, site, tâches GitHub et outils

Deuxième relecture (validation) de la zone a9 de l'audit du 21/11 : `modules/noyau`, `modules/interface`,
`modules/emotes`, le site (`site/`), les tâches GitHub (`.github/workflows/*.yml`) et les outils (`outils/*.py`).
Point de départ : la branche `carte-ile-reglages` au commit `6638bb00a` (celui qui est en ligne depuis 17 h 43, heure UTC).

**Rien n'a été testé en jeu** (pas de jeu ni de serveur ici) : relecture du code, du journal public de la dernière mise
en ligne (17 h 37), contrôle de syntaxe (Python, YAML, Java), puis fabrication et contrôles sur GitHub (« Contrôle de
branche »).

En bref : les changements du jour sont bons, **sauf un** (le correctif du panneau du staff laissait passer les panneaux à
commande, corrigé). Une faille de la mise en ligne trouvée en plus (secrets visibles par du code d'un autre auteur pendant
les contrôles, corrigée) et un petit défaut voisin du correctif du redémarrage (corrigé). Aucun plantage, aucune perte de
données, aucune duplication trouvée. **Site : rien n'a changé, aucune mise en ligne du site à faire.**

Versions : **Noyau 1.1.12** (seul le jar du serveur change vraiment ; le launcher donnera le nouveau jar aux joueurs,
aucun canal réseau ne change). La correction de `serveur.yml` ne sert qu'à la prochaine mise en ligne sur `main`.
Sur GitHub : « Contrôle de branche » vert sur `7fc16a9a3` (qui contient ces corrections : fabrication, contrôles, images) ;
démarrage d'essai réussi avec le Noyau 1.1.12 (`f02b58599` : « Done » en 54 s, arrêt propre).

---

## Changements du jour validés

| Commit | Ce qu'il fait | Verdict |
|---|---|---|
| `f70922da5` | `verif_synchro.py` : essai sauté avec un javac trop ancien ; `verifier.py` montre sa ligne de résultat | OK |
| `05728f613`, `b54684d27`, `46024c380` | Démarrage d'essai : avertissements comptés, réglages corrigés par Forge clé par clé, fichiers « commentaires seulement » à part | OK (serveur d'essai sans aucun secret : rien de privé dans le résumé public) |
| `3c2be9e72` | `lod_armes.py --verifier` branché dans `verifier.py` | OK (Python seul, sans dépendance) |
| `cc10fec2c` | `verifier_langues.py` : clés de traduction de tous les modules | OK |
| `24c9a4aa9` | Noyau 1.1.10 : `sethome` dans la liste par défaut des commandes permises sur la carte ; `commandes.py` | OK (le fichier du dépôt l'avait déjà) |
| `86af842a4` | Commentaire de `heraliz_distances-server.toml` | OK |
| `e34f7ed3e` | Interface 2.0.17 : `tall_grass.json` du pack « herbe » | OK (JSON relu : valide) |
| `3fdb8b9dc` | Inventaire des commandes (documentation) | OK |
| `c31d7c240` | Adresses IPv6 masquées dans « Journaux » et « Lenteurs » | OK, une petite limite : une adresse écrite avec un seul groupe avant « :: » (`2001::abcd`) n'est pas masquée. Java écrit les adresses des joueurs en entier (`2a01:cb00:0:0:…`), qui, elles, sont masquées |
| `544cb75e4` | `poi_sans_relecture` aussi pour `heralisz:spawn` et `crac:tuto` ; job « images » (numpy et Pillow figés) | OK (aucun de nos mods ne lit les points d'intérêt ; le job « images » n'a aucun secret) |
| `5d056668c` | Outil de la vidéo « vitrine des skins » | OK (outil du PC seulement, pas dans GitHub ; ni nom, ni chemin de PC) |
| `e11e98677` | Contrôle de branche en trois jobs (fabrication sans secret) ; version Modrinth nettoyée ; `/redemarrage` attend les écritures en fond ; IPv6 dans « Commandes du serveur » | OK. Deux remarques : un défaut voisin dans `/redemarrage annuler` (corrigé, plus bas) ; JOML (code d'un autre auteur) tourne pendant les contrôles (corrigé pour la mise en ligne, proposition pour les branches) |
| `0d6f9ce5c` + `66233ddf8` | Noyau 1.1.11 : actions du panneau du staff plus jugées par les règles du combat, à terre et de la carte | **Problème** : les panneaux (blocs) à commande passaient aussi. Corrigé (`69a02729a`) |
| `13a16e896` | `verif_reflexion.py`, `verif_mixins.py`, `sources_java.py` | OK (lecture seule ; pas branchés sur GitHub, comme vous l'avez choisi) |
| `f324509e4`, `84bad746d`, `f545589a0` | Démarrage d'essai « mixins stricts » ; `remove_spawn_chunks=false` de ModernFix | OK (case de « Run workflow » seulement ; l'expression ne lit qu'un booléen ; la ligne ajoutée à ModernFix ne touche que le serveur d'essai) |
| `6638bb00a` | Contrôle « dessins » des skins dans les jars -serveur et -complet d'armes | OK (vu dans le journal de 17 h 37 : 161 fichiers, comme les sources) |

Vérifié aussi : les écritures en fond servent maintenant à plus de modules (collection des camos à vie, profils des clans) ;
la collection des camos s'en sert correctement (relevé neuf sur le fil du serveur, fichier écrit à côté puis remplacé,
attente au chargement et à l'arrêt : `modules/armes/.../camo/serveur/CollectionCamos.java` l. 81, 156, 204).

---

## Corrigé

### 1. Panneaux à commande : de nouveau jugés en combat, à terre et sur la carte (moyen) — commit `69a02729a`

En simple : depuis le correctif du panneau du staff de ce midi, toute commande partie « avec plus de droits que le
joueur » n'était plus jugée par les règles du combat et de la carte. Or Minecraft donne lui-même plus de droits (niveau 2)
à un **panneau en bois qui porte une commande au clic** : c'est le joueur qui clique qui la joue. Un panneau « /spawn »,
« /home » ou « /warp » posé par le staff (sur la carte, au spawn, ou collé par WorldEdit) aurait donc permis de quitter la
carte ou de fuir un combat. Un joueur ne peut pas fabriquer un tel panneau lui-même ; c'était donc un trou seulement si le
staff en a posé un (impossible à vérifier d'ici : on ne touche pas au monde). Maintenant, seul le niveau 4, celui des
actions du panneau du staff (ban PC, jour, largage…), en est dispensé ; les panneaux sont jugés comme avant le 21/11.
Les commandes tapées et celles données au staff par le Fondateur restent jugées, comme ce midi.

Pour les développeurs : `modules/noyau/src/serveur/java/fr/heraliz/noyau/serveur/combat/CombatService.java` l. 326-334
(`elevee` : `source.hasPermission(4) && !p.hasPermissions(4)` ; avant : n'importe quel niveau au-dessus de celui du
joueur). Source du niveau 2 : `SignBlockEntity.createCommandSourceStack` de Minecraft 1.20.1 (même chose chez
CraftBukkit/Mohist). Seule source de niveau 4 au nom d'un joueur dans nos mods : `PanelStaff.commandeStaff`
(`modules/boutique/.../PanelStaff.java` l. 610). Relu aussi : `CommandesDonnees` (boutique) relit la commande à
`HIGH` (l. 84), après la lecture de la source à `HIGHEST` (`CombatService` l. 98) : une commande tapée n'est jamais vue
comme « partie avec plus de droits ». Commandes des blocs de commande, de la console et de `/execute` : jugées comme avant
(la source n'est pas un joueur, ou c'est l'op lui-même).

### 2. Mise en ligne : secrets visibles par du code d'un autre auteur pendant les contrôles (moyen, sécurité) — commit `8bd56d9a5`

En simple : la règle du projet est qu'aucun mot de passe ni clé ne doit être là où tourne du code écrit par d'autres.
Dans la mise en ligne (`serveur.yml`, job « serveur »), l'étape « Contrôles » lance `outils/verifier.py` avec tous les
secrets de la mise en ligne dans son environnement (mot de passe SFTP et clé API du Ptero, webhook de Spartan…). Or
`verifier.py` lance `modules/loot/outils/lod_props.py`, qui **télécharge JOML** (une bibliothèque d'un autre auteur, depuis
Maven Central) et le fait tourner avec Java. Le journal de la mise en ligne de 17 h 37 le confirme (contrôle fait, secrets
présents dans l'étape). Le risque réel est faible (Maven Central ne change jamais un fichier publié), mais la règle
n'était pas tenue. Maintenant, les secrets sont vidés pour cette seule étape ; la liste des noms perso est lue dans son
fichier, comme avant ; rien d'autre ne change. C'est la protection simple (un code qui lit les variables de son
environnement, le cas le plus courant, ne trouve plus rien) ; la séparation complète est une proposition plus bas.

Pour les développeurs : `.github/workflows/serveur.yml` l. 198-218 (`env:` de l'étape, chaque secret du job à `""` : la
valeur de l'étape remplace celle du job, donc ni le shell de l'étape ni ses enfants ne les ont dans leur environnement).
Limite : sur une machine de GitHub, un code malveillant a `sudo` et pourrait lire la mémoire du programme de GitHub qui
garde les secrets du job ; seul un job à part (proposition 1) l'empêche. JOML : `modules/loot/outils/lod_props.py`
l. 60-71 (`joml()`, sans empreinte contrôlée), lancé par `outils/verifier.py` l. 354-361.

### 3. `/redemarrage annuler` gardait le code de sortie du redémarrage annulé (petit) — commit `69a02729a`

En simple : avec un `[redemarrage] code_sortie` différent de 0 (0 aujourd'hui : rien ne change pour vous), un redémarrage
programmé puis annulé laissait son code : un `/stop` plus tard aurait coupé Java net avec ce code, et le panel aurait
relancé le serveur sans qu'on le veuille. Le code est maintenant remis à 0 à l'annulation.

Pour les développeurs : `modules/noyau/src/serveur/java/fr/heraliz/noyau/serveur/maintenance/Maintenance.java` l. 154.
Le correctif du jour (attente des écritures avant `Runtime.halt`, l. 69) est bon : attente de 60 s au plus par voie, sur un
fil à part, après la sauvegarde du monde.

---

## À décider par le propriétaire (rien n'est changé)

1. **Contrôles de la mise en ligne dans un job à part, et empreinte de JOML** (petit, sécurité ; **fait ensuite, voir
   « Suite »**). (a) Mettre l'étape
   « Contrôles » de `serveur.yml` dans un job sans aucun secret (comme le job « controles » de `branches.yml`), dont le
   job « serveur » dépendrait : séparation complète, sans minute de plus (il tourne pendant le démarrage d'essai). Pas
   fait ici : c'est la structure de la mise en ligne, à changer avec une mise en ligne surveillée. (b) Dans le « Contrôle
   de branche », JOML tourne pendant que le fichier des noms perso est sur le disque (dès que le secret `NOMS_INTERDITS`
   sera réglé) : contrôler l'empreinte du fichier téléchargé dans `lod_props.py` (SHA-256 de `joml-1.10.5.jar` :
   `cac9f22f83a7aa33eebda73c16ff5261e3cb4911b6bafcf4c79ea486099d0c9a`, vérifiée ici contre l'empreinte publiée par Maven
   Central), comme numpy et Pillow dans `outils/requis_images.txt`. Pas fait ici : ce fichier est dans le module loot, et
   le changer refait le jar de loot (version à monter).
2. **Journal public de « Lenteurs » : mot de passe tapé par erreur, messages privés** (petit, à moyen pour la vie privée).
   Si un joueur tape son mot de passe
   précédé d'un « / » (par exemple `/monmotdepasse` au lieu de `/login monmotdepasse`), ce premier mot est gardé tel quel :
   dans le journal du serveur (« issued server command », écrit par Mohist avant qu'AuthMe voie la commande ; AuthMe ne
   cache que les lignes de ses propres commandes comme `/login`), que la tâche « Lenteurs du serveur » recopie dans son
   journal public (parties « commandes jouées » et « 30 lignes avant chaque retard », `.github/workflows/lenteurs.yml`
   l. 230 et 232), et dans les rapports du diagnostic (`DernieresCommandes.debut`,
   `modules/noyau/.../diag/DernieresCommandes.java` l. 103), recopiés aussi. Peu probable, mais le dépôt est public. Même
   partie « 30 lignes avant chaque retard » : les lignes de commandes y sont entières, donc le texte des messages privés
   (`/msg <pseudo> <message>` : pseudos masqués, message lisible ; `log: true` de `serveur/spigot.yml` écrit chaque
   commande en entier), et le texte du chat s'il est écrit dans le journal. Proposition : dans ces parties, ne
   garder que les commandes connues du serveur, sans leurs arguments (les autres : « /? »), et pas le texte du chat ; ou
   retirer ces parties du journal public.
3. **Staff op 4 en combat** (petit, règle). Le choix de ce midi garde les règles du combat pour un op 4 : un Admin ou le
   Fondateur op 4 qui vient de prendre un coup ne peut pas lancer « Ban PC », « Jour » ou « Largage » depuis le panneau
   pendant 15 s (« Impossible en combat »), alors qu'un Guide sans op le peut. Proposition : dispenser toutes les actions
   du panneau, quel que soit le niveau d'op (repère posé par le panneau au lieu des droits). À décider.
4. **Téléportations en attente du chargement** (petit, règle). Depuis le 19/11, une téléportation de nos mods attend
   jusqu'à 3 s que l'arrivée soit chargée (souvent 0 à 0,5 s). Si le joueur prend un coup pendant ce temps, il part quand
   même. Le noyau ne sait pas quelles téléportations doivent être annulées en combat (extraction, ClashWar et staff ne le
   doivent pas) : c'est à chaque mod (`/home`, `/tpa`) de revérifier le combat dans son action (`api/Arrivees.java`). À voir
   avec les relectures de ces mods.

## À faire par vous

- **Secret `NOMS_INTERDITS`** : toujours absent (journal de la mise en ligne de 17 h 37 : « contrôle des noms perso
  sauté »). GitHub → Settings → Secrets and variables → Actions → New repository secret (`docs/MISE_EN_LIGNE_GITHUB.md`,
  section 7).
- À savoir : dans l'environnement « production », les secrets `SPARTAN_DISCORD_SANCTIONS`, `LITEBANS_DB_USER`,
  `LITEBANS_DB_PASSWORD`, `HERALIBOT_LIEN_URL` et `HERALIBOT_LIEN_CLE` sont vides (même journal). Si c'est voulu (LiteBans
  en base locale, lien Discord avec HeraliBot coupé, pas de salon des sanctions), rien à faire.

---

## Vérifié sans problème

- **Écrivain en fond** (`modules/noyau/.../api/EcrituresFond.java`) relu de nouveau : une écriture à la fois par voie et
  dans l'ordre, le relevé le plus récent d'un fichier remplace l'ancien sans trou (« occupé » de la mise en file jusqu'à la
  fin), attente à l'arrêt (dès ServerStopping, puis tout à ServerStopped), aucune attente bloquante d'un fil d'écriture
  sur lui-même, échec noté et donnée remise « à sauver ». Fichiers `data/` (`perf/DonneesFond.java`) : copie du relevé,
  fichier à côté puis remplacement, lecture qui attend l'écriture en file.
- **Points d'intérêt** (`allege/PoiMesureMixin.java` et `[perf] poi_sans_relecture`) : les deux mondes ajoutés sont lus
  par leur nom exact ; rien d'autre ne change.
- **Règles du combat** : `CombatTag`, `Matchs`, `ATerre` (API inchangées, clés `heralisz:combat_jusqua`,
  `heralisz:en_match`, `HeraliszMatch` intactes) ; commandes des plugins jugées par `CommandesBukkit` (refus = liste
  permise, un alias inconnu est refusé) ; `/r` (`chat/Reponse.java`) rejoue `/msg` avec les droits du joueur.
- **Ponts et paquets** : `ClicObjetBukkit` (objet toujours rendu), `ObjetsNeutres` ; paquets du noyau bornés
  (`net/NoyauNet.java`) ; rapport du jeu d'un joueur accepté seulement avec le jeton d'une demande du staff
  (`diag/DiagClients.java`) ; `/diag` op 4.
- **Perf** : `RegenLente` (une boucle par seconde), `MouvementsPretsMixin`, `Troncons` (jamais d'attente ni de
  chargement), `SauvegardesEtalees` (pas de changement aujourd'hui).
- **Interface** (jeu des joueurs) : barre d'objets sans création d'objets à chaque image ; reconnexion automatique vers
  le même serveur seulement après le message du redémarrage ; les « lettres nettes » restent non chargées (votre choix) et
  leur code n'est jamais appelé sans leur configuration (aucun risque de plantage).
- **Emotes** : le serveur décide seul si une emote est « forcée » (suivi du serveur, jamais une valeur envoyée par le jeu
  du joueur) ; la copie officielle de l'animation est toujours transmise.
- **Site** (aucun changement depuis le 19/11) : Points Boutique signés (HMAC, horodatage de 2 min, signature à usage
  unique, référence unique, verrous de la base), jetons de jeu et liens du pack signés, comptes du launcher, boutons
  Discord signés (Ed25519), pas de requête SQL avec du texte venu de l'extérieur, pas de HTML non échappé venu des
  joueurs. Rien à corriger, **pas de mise en ligne du site**.
- **Tâches GitHub** : `branches.yml` (fabrication sans secret, contrôles sur une machine neuve, images sans secret) ;
  `mods_tiers.yml` (nom de fichier et version réduits à des caractères sûrs) ; `demarrage.yml` (case « mixins_stricts »
  sans injection possible) ; `journaux.yml`, `lenteurs.yml` (pseudos et adresses masqués ; la liste des pseudos n'est
  jamais affichée) ; `commandes.yml` (adresses masquées, seules les réponses aux commandes du lot sont recopiées) ;
  `carte_loot.yml` et `commandes.yml` (Python standard et lftp seulement, à côté des secrets) ; `banc.yml` (aucun secret ;
  le Mohist d'essai n'entre pas dans le cache).
- **Outils** : `verifier.py` fait ce qu'il annonce (les contrôles sautés sont dits : séparation client/serveur, noms de
  Minecraft, images hors du job « images », noms perso sans le secret) ; les trois outils de l'audit a10 ne sont branchés
  nulle part et n'écrivent rien sans qu'on le demande ; `pseudos_a_masquer.py` n'affiche jamais un pseudo.

---

## À tester en jeu

- [ ] Staff **sans op** en survie sur la carte, puis juste après un coup reçu (tag de combat) : panneau (P) → « Ban PC »
      d'un compte d'essai, « Jour », « Largage » : l'action se fait (comme ce midi).
- [ ] Joueur normal sur la carte : `/spawn` refusé ; en combat : `/home` refusé, `/msg` permis (comme avant).
- [ ] S'il existe un panneau (bloc) qui joue une commande au clic (au spawn par exemple) : le cliquer en combat →
      « Impossible en combat », comme avant le 21/11 ; hors combat, il marche comme avant.
- [ ] `/redemarrage 5` puis `/redemarrage annuler` : message « annulé » ; un arrêt normal plus tard reste un arrêt normal.
- [ ] GitHub, prochaine mise en ligne sur `main` (voir « Suite » plus bas) : un job « controles » de plus, vert, en même
      temps que le démarrage d'essai ; son journal ne montre aucun secret du Ptero (seulement `NOMS_INTERDITS`, et un
      avertissement tant qu'il manque) ; son étape « JOML (empreinte vérifiée) » dit « OK » ; le job « serveur » part
      ensuite et marche comme avant (SFTP, redémarrage, pack du launcher).

Pas testé : tout ce qui précède (aucun jeu ni serveur ici).

---

## Suite (21/11, choix du propriétaire : proposition n° 1 « Oui »)

Fait : les contrôles de la mise en ligne sont dans un job à part, `controles`, sans aucun secret de production (machine
neuve, jars repris du cache `jars-<commit>`, seul le secret du dépôt `NOMS_INTERDITS`, avertissement s'il manque) ; le job
`serveur` en dépend (`needs` et `if`) : un contrôle raté, rien n'est envoyé au Ptero, comme avant. JOML est téléchargé
par le workflow et refusé si son empreinte SHA-256 change (`cac9f22f…0c9a`), dans `serveur.yml` et dans `branches.yml` ;
`lod_props.py` le prend par `JOML_JAR` (module loot inchangé, pas de version à monter). Rien d'autre ne change dans la
mise en ligne. `serveur.yml` ne tourne que sur `main` : il ne sera essayé qu'à la prochaine mise en ligne. Le secret
`NOMS_INTERDITS` doit être un secret du DÉPÔT (le job `controles` n'a pas d'environnement), comme le dit
`docs/MISE_EN_LIGNE_GITHUB.md` (sections 3 et 7, mises à jour).
