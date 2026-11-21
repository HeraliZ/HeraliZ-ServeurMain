# Audit global du 21/11 — zone a9 : noyau, interface, emotes, site, tâches GitHub et outils

Relu : `modules/noyau` (le socle partagé par tous les mods : tag de combat, matchs, à terre, portes, bandeaux,
écritures en fond, sauvegardes étalées, réglages `[perf]` et leurs mixins, ponts vers Bukkit, `/diag` et son veilleur,
vie qui remonte, kit d'interface), `modules/interface` (écran titre, menu Échap, lettres nettes, pack imposé, barre
DayZ), `modules/emotes` (règles des emotes, Emotecraft et Player Animator embarqués, licences gardées), le site
(`site/` : extension HeraliZ d'Azuriom, thème, scripts de la VM), les tâches GitHub (`.github/workflows/*.yml`) et les
outils (`outils/*.py`, surtout `verifier.py` et `construire.py`).

**Rien n'a été testé en jeu** (pas de jeu ici) : relecture du code, contrôle de syntaxe des fichiers changés (Python,
YAML), lecture des journaux publics des tâches GitHub, puis la fabrication et les contrôles sur GitHub. Version :
**Noyau 1.1.11** (seul le jar du serveur change vraiment ; le launcher donnera le nouveau jar aux joueurs, aucun canal
réseau ne change). **Site : rien n'a changé, aucune mise en ligne du site à faire.**

En bref : 5 points corrigés (3 moyens, 2 petits), 1 réglage à faire par vous (important), 5 propositions à décider.
Aucun bug grave trouvé : pas de plantage, pas de duplication, pas de fuite de mot de passe, pas de faille sur le site.

---

## À faire par vous (important, 2 minutes)

### Le contrôle des « noms perso » ne tourne jamais sur GitHub

En simple : la règle n° 3 du projet (aucun prénom, pseudo, mail… dans le code) est contrôlée par `outils/verifier.py`
avec une liste de mots interdits, donnée à GitHub par le secret **`NOMS_INTERDITS`**. Ce secret n'existe pas : dans les
journaux des tâches (branche et `main`), l'étape « Noms interdits » est sautée et le contrôle écrit
« outils/noms_interdits.txt absent : contrôle des noms perso sauté ». Depuis toujours, ce contrôle ne s'est donc fait
que sur un PC qui a ce fichier.

À faire : GitHub → Settings → Secrets and variables → Actions → **New repository secret** → nom `NOMS_INTERDITS`,
valeur : les mots interdits (prénoms, anciens pseudos, mails…), un par ligne. Au niveau du **dépôt** (comme le dit
`docs/MISE_EN_LIGNE_GITHUB.md`, section 7, étape 3), pour que les branches de travail le voient aussi. Depuis ce
correctif, la tâche « Contrôle de branche » affiche un avertissement tant qu'il manque. Le secret n'est jamais affiché
dans les journaux, et il ne va plus chez Gradle (point 1 ci-dessous).

---

## Corrigé

### 1. Tâche « Contrôle de branche » : le secret des noms était visible par Gradle (moyen, sécurité) — commit `e11e9867`

En simple : la fabrication des mods fait tourner Gradle et ses extensions, téléchargées sur internet (du code d'autres
auteurs). La règle du projet : un secret ne doit jamais être là où tourne ce code. La mise en ligne (`serveur.yml`) la
respectait déjà ; le contrôle des branches non : le secret `NOMS_INTERDITS` était dans l'environnement de toute la
tâche, donc de Gradle, et son fichier était écrit pendant que Gradle tournait encore en arrière-plan. Aujourd'hui sans
effet (le secret est vide, voir plus haut), mais il l'aurait été dès que vous le réglez.

Maintenant : trois jobs. « fabrication » (sans aucun secret), « controles » (une machine neuve où Gradle n'a jamais
tourné : elle reprend les jars fabriqués, et le secret n'arrive que dans l'étape qui écrit son fichier) et « images »
(sans changement). Même ordre que la mise en ligne ; une minute de plus environ.

Pour les développeurs : `.github/workflows/branches.yml` l. 19-125 (job `controles`, cache `jars-<commit>` repris par
`actions/cache/restore`, `fail-on-cache-miss`, comme le job `serveur` de `serveur.yml`) ; la fabrication passe par
`outils/sans_secrets.py` (l. 59).

### 2. Tâche « Mod d'un autre auteur » (Modrinth) : variable injectable par l'auteur du mod (moyen, sécurité) — commit `e11e9867`

En simple : la tâche télécharge un mod depuis Modrinth, puis l'enregistre sur la branche (elle a le droit d'écrire
dans le dépôt). Le numéro de version, écrit par l'auteur du mod, était recopié tel quel pour l'étape suivante : avec un
retour à la ligne dedans, un auteur mal intentionné pouvait y glisser une variable de son choix et faire exécuter du
code par l'étape qui écrit dans le dépôt. Maintenant, ce numéro est réduit à des lettres, chiffres et `. _ + -`.

Pour les développeurs : `.github/workflows/mods_tiers.yml` l. 75-83. Note : changer ce fichier la lance sur la branche
avec ses valeurs par défaut (Embeddium, joueurs) ; elle a tourné le 21/11 : déjà à jour, rien d'enregistré.

### 3. Actions du panneau du staff refusées sur la carte et en combat (moyen) — commit `0d6f9ce5`

En simple : certaines actions du panneau du staff (touche P) passent par une commande jouée au nom du membre du staff,
avec les droits d'un admin : **ban PC**, jour et nuit, largage, frappe aérienne, rotation des extractions, redémarrage,
recharger, diagnostic. Le noyau les prenait pour une commande tapée : un membre du staff **sans op**, en survie **sur la
carte**, ou n'importe quel staff **en combat**, voyait « /banpc : seulement au spawn, pas sur la carte » et l'action
ratait (un tricheur pas banni tout de suite). Maintenant, une commande jouée par le code avec plus de droits que le joueur
n'est plus jugée par les règles du combat, à terre et de la carte. Les commandes tapées par les joueurs (plugins compris)
gardent exactement les mêmes règles : elles ont toujours les droits du joueur lui-même.

Pour les développeurs : `modules/noyau/.../serveur/combat/CombatService.java` l. 297-318 (`elevee` : la source a un
niveau que le joueur n'a pas ; hors de `canUse`, `CommandSourceStack.hasPermission` ne lit que son niveau, Mohist
compris). Seule source concernée aujourd'hui : `PanelStaff.commandeStaff` (boutique, `withPermission(4)`). Un op 4 reste
soumis à la règle du combat (`operateurs_exemptes = false`, votre choix), comme pour ce qu'il tape.

### 4. `/redemarrage` avec un code de sortie : arrêt net possible avant la fin des sauvegardes (petit) — commit `e11e9867`

En simple : avec le réglage `[redemarrage] code_sortie` différent de 0 (0 aujourd'hui : rien ne change pour vous), le
serveur coupait Java net 3 s après l'arrêt, même si les dernières sauvegardes en fond (clans, quêtes, bases, level.dat…)
n'étaient pas finies. Maintenant il les attend d'abord (60 s au plus par module).

Pour les développeurs : `modules/noyau/.../serveur/maintenance/Maintenance.java` l. 66-76 (`EcrituresFond.attendreTout()`
avant `Runtime.halt`).

### 5. « Commandes du serveur » : adresses IPv6 lisibles dans le résumé public (petit) — commit `e11e9867`

En simple : le dépôt est public ; le résumé de cette tâche masquait les adresses IPv4, pas les IPv6 (même oubli que
celui corrigé le 21/11 dans « Journaux » et « Lenteurs »). Maintenant masquées aussi, avec les mêmes motifs.

Pour les développeurs : `outils/commandes_serveur.py` l. 70-74.

---

## À décider par vous (rien n'est changé)

1. **Fabrication des branches : un mod qui utilise un mod changé n'est pas refait** (moyen). `construire.py
   --si-change` ne refait que les mods dont les fichiers ont changé (`empreinte()`, `outils/construire.py` l. 72). Si
   le noyau (ou les clans, la voix, le monde…) change une fonction utilisée par un autre mod, cet autre mod garde son jar
   fabriqué avec l'ancienne version : une fonction retirée ou renommée ne se voit qu'en jeu (ou au démarrage d'essai si
   elle sert au démarrage). La règle « ne jamais renommer » rend ce cas rare, pas impossible (12 modules dépendent du
   noyau à la fabrication). Choix : (a) refaire aussi les mods qui en dépendent (sûr ; une fabrication plus longue à chaque changement
   du noyau, presque tout est refait) ; (b) un contrôle rapide qui vérifie que chaque appel des jars gardés existe encore
   dans les jars refaits (l'outil `outils/verif_dependants.py` existe déjà, pas branché) ; (c) ne rien changer.
2. **Publication du launcher : paquet Python sans empreintes là où sont les clés** (petit). Les trois jobs de mise en
   ligne de `launcher.yml` (l. 241, 309, 376) installent `paramiko` (et, sans version figée, ses bibliothèques) dans le
   job qui a la clé de signature du launcher et le mot de passe de la VM. Proposition : versions et empreintes figées
   (comme `outils/requis_images.txt`), ou `sshpass` et `rsync` du système comme `serveur.yml`. À faire avec un essai de
   publication.
3. **Contrôle des noms perso : seulement dans les modules.** `verifier.py` cherche les mots interdits dans les sources et
   les jars des modules, pas dans `docs/`, `outils/`, `serveur/`, `site/` ni `launcher/`, publics eux aussi.
   Proposition : les ajouter (d'abord en simple avertissement), une fois le secret réglé.
4. **« Lenteurs du serveur » écrit encore les noms des fichiers de la racine du Ptero** (partie « DISQUE : fichiers de la
   racine ») dans son journal public : déjà proposé le 21/11 (section 43, point 4), seule la partie IPv6 a été faite.
   Un fichier peut porter un nom de personne ; proposition : n'y écrire que leur nombre et leur taille.
5. **Écritures en fond, pour les développeurs** (rien à décider, à savoir) : dans une même voie
   (`EcrituresFond.Voie.ecrire`), un fichier relevé deux fois n'est écrit qu'une fois, à la place de son PREMIER relevé :
   il peut donc passer avant un autre fichier mis en file entre les deux. Un module qui a besoin d'un ordre entre deux
   fichiers (objets à vie avant le joueur, section 41) doit continuer d'écrire tout de suite, comme c'est fait.

---

## Vérifié sans problème

- **Écrivain en fond du noyau** (`api/EcrituresFond`) : une écriture à la fois par voie, dans l'ordre ; le relevé le plus
  récent remplace l'ancien sans trou (fichier toujours « occupé » pendant l'écriture) ; attente à l'arrêt dès
  ServerStopping puis de tout à ServerStopped ; pas de blocage entre deux fils ; échec noté et donnée remise « à sauver »
  (`DonneesFond`) ; fichier temporaire puis remplacement (data/, level.dat avec level.dat_old).
- **Sauvegardes étalées** (`SauvegardesEtalees`, `JoueursEtalesMixin`, `EntitesEtaleesMixin`) : seule la sauvegarde
  automatique est étalée (`/save-all`, l'arrêt et Bukkit écrivent tout de suite) ; joueur parti ou revenu entre-temps,
  monde sans tronçon : bien gérés. Le risque connu en cas de plantage (section 42) reste le même.
- **Mixins du tick** (tronçons « au repos », tickets à échéance, relevé des apparitions, mélange, tronçons sans ticket,
  points d'intérêt, mouvements vers un tronçon pas prêt, déchargements bornés, comparaisons de menus) : relus contre le
  code de Minecraft 1.20.1 ; chacun fait ce que Minecraft aurait fait (mêmes tickets retirés au même tick, mêmes envois
  de blocs à chaque tick, même joueur visé) et se coupe par son réglage `[perf]`.
- **Staff caché** : le raccourci du test de Bukkit (`VoirBukkit`) suit la vraie règle de Bukkit (un joueur à qui un
  staff est caché passe toujours par le vrai test) ; les bruits de pas d'un staff caché ne partent pas.
- **Ponts vers Bukkit** (`CommandesBukkit`, `ClicObjetBukkit`) : commandes des plugins jugées sur le fil du serveur, après
  AuthMe ; l'objet caché à WorldGuard est toujours rendu (même si un plugin annule le clic).
- **Diagnostic** : le veilleur ne relève la pile que pendant un blocage ou un profil ; rapports écrits sur un fil à part ;
  le rapport du jeu d'un joueur n'est accepté que s'il répond à une demande du staff (jeton, délai, taille bornée) ;
  arguments de Java qui ressemblent à un mot de passe masqués.
- **Règles du combat** : tag, mort à la déconnexion (jamais à l'arrêt, au spawn, en match, en créatif), refus des
  commandes (Forge et plugins), effacement à la mort ; listes de `heraliz_noyau-server.toml` relues sans redémarrer, sur
  le bon fil.
- **APIs lues par d'autres mods** (CombatTag, Matchs, ATerre, EtatClient, Portes, Notifs, Arrivees, Troncons, Etalement,
  Fuites, Rechargements, EcrituresFond) : noms et signatures inchangés ; clés `heralisz:combat_jusqua`,
  `heralisz:en_match`, `HeraliszMatch` inchangées.
- **Interface et emotes** (joueurs) : pas de travail lourd à chaque image relevé ; reconnexion automatique seulement sur
  le message du redémarrage ; emotes : le serveur transmet toujours sa copie officielle de l'animation, refuse une emote
  verrouillée, en combat, à terre ou en match ; le canal HeraliZ des emotes ne reçoit rien des joueurs (celui
  d'Emotecraft passe par ce filtre du serveur, `ServerEmotePlayMixin`, dont le branchement est vérifié au démarrage).
- **Site** (extension HeraliZ) : Points Boutique signés (HMAC, horodatage à 2 min, une signature ne sert qu'une fois,
  référence unique, plafonds de création), jetons de jeu et liens du pack signés, comptes du launcher (jeton d'Azuriom,
  limites par adresse, adresse IP jamais lue dans un en-tête), Discord (état de connexion dans la session, lien à usage
  unique, boutons du staff signés Ed25519), votes, commandes de la console limitées à une liste, requêtes SQL toujours
  avec paramètres, pages sans injection de HTML. Rien à corriger.
- **Tâches GitHub** : les choix des formulaires passent toujours par l'environnement (jamais collés dans le script) ;
  secrets de mise en ligne réservés à `main` (environnement `production`) ; code des autres auteurs (Gradle, npm, Nebula,
  installeur de Forge, serveur d'essai) sans secret ; machines distantes vérifiées par leur empreinte. `enquete.yml`
  laissé tel quel (votre choix).
- **Outils** : `verifier.py` fait bien ses contrôles sur GitHub ; sont sautés seulement ceux qui sont annoncés (séparation
  client / serveur et noms de Minecraft : pas de Minecraft ici ; images : job à part), plus les noms perso faute de
  secret (voir plus haut).

---

## À tester en jeu

- [ ] Un membre du staff **sans op**, en survie sur la carte : panneau (P) → « Ban PC » d'un compte d'essai, « Jour »,
      « Largage » : l'action se fait (avant : « seulement au spawn, pas sur la carte »).
- [ ] Le même, juste après avoir pris un coup d'un autre joueur (tag de combat) : mêmes actions, elles se font.
- [ ] Un joueur normal sur la carte : `/spawn`, `/warp` toujours refusés ; en combat : `/home` refusé, `/msg` permis
      (rien ne change pour les joueurs).
- [ ] `/redemarrage 1` : compte à rebours, sauvegarde, reconnexion automatique, comme avant (code de sortie à 0).
- [ ] GitHub, onglet Actions, « Contrôle de branche » : trois jobs (« fabrication », « controles », « images »), tous verts ;
      tant que le secret `NOMS_INTERDITS` manque, un avertissement jaune le dit. Une fois réglé : l'étape « Noms
      interdits » n'est plus sautée et le contrôle écrit son résultat.

Pas testé : tout ce qui précède (aucun jeu ni serveur ici).
