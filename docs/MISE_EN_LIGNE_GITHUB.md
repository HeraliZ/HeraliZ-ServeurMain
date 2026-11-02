# Mettre le nouveau serveur sur GitHub (à la place de l'ancien) — 26/10

Ce projet remplace **tout le contenu** de l'ancien dépôt GitHub du serveur. Après l'envoi sur la branche `main`, GitHub
fabrique les mods, fait les contrôles et met à jour le Ptero tout seul (workflow « Serveur HeraliZ »), comme avant.

## 1. Avant l'envoi

1. **Sauvegarde du Ptero** depuis le panel (onglet Backups).
2. Personne ne doit envoyer sur l'ancien dépôt pendant ce temps (l'autre personne aussi).

## 2. Remplacer le contenu du dépôt

Dans le dossier du dépôt GitHub sur le PC (celui qui contient le dossier caché `.git`) :

1. Supprimer **tout** sauf le dossier `.git`.
2. Copier dedans **tout** le contenu du dossier préparé (y compris le dossier caché `.github` et le fichier
   `.gitignore`).
3. **Conseillé : d'abord sur une branche d'essai.** GitHub Desktop : « Current branch » → « New branch »
   `nouveau-serveur`, « Commit », « Publish branch ». GitHub fabrique et contrôle tout (workflow « Contrôle de branche »)
   **sans rien envoyer au Ptero**. C'est la première fabrication de ce projet sur une machine de GitHub : si elle échoue,
   rien n'est cassé, il suffit de demander à Claude de lire l'erreur.
4. Quand c'est vert : passer la branche sur `main` (sur GitHub : « Pull request » → « Merge »). La mise en ligne au Ptero
   part à ce moment-là. (Sans branche d'essai : « Commit to main » puis « Push origin », avec le message
   « Nouveau serveur HeraliZ (projet propre) ».)

L'historique Git est gardé : l'ancien serveur reste lisible dans les anciens commits. La Release « base-serveur » (le jar
de Mohist) ne change pas.

## 3. Ce que GitHub fait tout seul (onglet Actions, « Serveur HeraliZ »)

- Fabrique tous les modules (la première fois : environ 30 à 60 min).
- Contrôles (GeckoLib, langues, noms interdits).
- Met à jour le Ptero par SFTP :
  - `mods/` devient la copie exacte des nouveaux jars : **les anciens jars sont retirés** (dont `heraliz_boss`,
    `heralisz_matchs`, `heraliz_wonder`, `dayzhotbar`…) ;
  - `config/`, `defaultconfigs/` (recopié dans `dayz_map/serverconfig/`), `tacz/`, `heraliz_clashwar/`, plugins ;
  - les **anciens gunpacks** (`arkaniaz_pack_0_converted` à `_5`, `arkaniaz_custom_converted`, `Cislumen_Armory`) sont
    supprimés du Ptero (liste en fin de `serveur/tacz_retires.txt`) ;
  - `config/heralisz_clans/zones.json` (territoires) n'est **jamais écrasé** s'il existe déjà sur le Ptero : les
    drapeaux posés en jeu sont gardés ;
  - les secrets (Spartan, Discord, LiteBans, lien Discord) sont écrits à l'envoi, comme avant.
- Refait le pack du launcher avec les jars joueurs de la même fabrication.
- Le monde, les données des joueurs (clans, hérals, grades, quêtes…), `ops.json` et `server.properties` ne sont jamais
  touchés.

Secrets GitHub : les mêmes qu'avant (Settings → Secrets and variables → Actions), plus `TOPSERVEURS_TOKEN` (facultatif)
pour les votes du site (section 6).

## 4. Après l'envoi

1. Attendre que « Serveur HeraliZ » soit vert.
2. « Run workflow » → cocher **redemarrer** : le serveur redémarre avec les nouveaux mods.
3. Lire le résumé : la ligne « clé des jetons du launcher » doit être réglée. **Spartan refuse maintenant toutes les
   connexions si ce secret manque** (avant, il laissait tout le monde entrer sans rien dire).
4. Les joueurs reçoivent les nouveaux mods au prochain lancement du launcher. Monter la version du launcher et le
   publier (workflow « Launcher HeraliZ », case « publier ») pour qu'il retire lui-même les anciens gunpacks.

À faire une fois à la main sur le Ptero (pas fait par GitHub) :
- `server.properties` : `sync-chunk-writes=false`, et changer la description du serveur (`motd`).
- Réglages Java à demander à l'hébergeur (`-Xms` égal à `-Xmx`, voir `docs/DEPLOIEMENT.md`).
- En jeu : `/gamerule doMobSpawning false`, `/gamerule keepInventory false`, `/forceload query` dans chaque monde.

## 5. Les workflows gardés

| Workflow | Rôle |
|---|---|
| `serveur.yml` « Serveur HeraliZ » | à chaque envoi sur `main` : fabrication, contrôles, mise à jour du Ptero, pack du launcher ; « Run workflow » : redémarrage, installation complète, monde des bases à neuf |
| `branches.yml` | une autre branche : fabrication et contrôles seulement, rien n'est envoyé |
| `launcher.yml` | installateurs du launcher, « publier » |
| `site.yml` | site HeraliZ (Azuriom) |
| `mods_tiers.yml` | ajouter un mod d'un autre auteur depuis Modrinth |
| `enquete.yml` | enquête Spartan sur un joueur (lecture seule) |
| `journaux.yml` | lire les journaux du serveur (lecture seule) |
| `banc.yml` « Banc de charge » | « Run workflow » (ou envoi sur une branche de travail qui change ce fichier) : vrai serveur avec nos mods monté sur la machine de GitHub, monde neuf, 100 bots (jusqu'à 150) qui jouent, mesure et diagnostic, résumé et rapports ; **sans le Ptero ni aucun secret** (01/11, `docs/GUIDE.md` §12) |
| `carte_loot.yml` « Remplacer la carte de loot » | **seulement par « Run workflow »** : remplacer la carte de loot en gardant le spawn (31/10). Par défaut, essai à blanc qui ne change rien et liste ce qui serait remplacé ou gardé ; vraie exécution seulement avec la case « executer » ET la phrase `REMPLACER LA CARTE DE LOOT`, depuis `main`. Nouvelle carte dans `/carte_nouvelle/` du Ptero (ou archive d'une Release). Mode d'emploi : `docs/NOUVELLE_CARTE.md` §8 |

Retirés (outils de l'ancienne carte qui ne sont plus dans le projet) : props de la carte, inventaire de la carte, monde
du spawn, grotte, schematic, extractions, gunpacks, diagnostic CRac, ancien test de charge (sur le Ptero ; remplacé le 01/11 par
`banc.yml`, sur une machine de GitHub). Ils restent dans l'historique Git.

## 6. Votes du site protégés contre les bots (27/10)

Chaque vote donne une clé, 10 hérals et 2 PB : une ferme de votes casse l'économie. L'extension HeraliZ du site
(`site/extension/heraliz/src/Votes/`) ne laisse l'extension Vote d'Azuriom donner la récompense que :
- à un **compte du site connecté** (plus de vote par simple pseudo : réglage remis à chaque envoi par `reglages.php`) ;
- dont le **Discord est lié, membre et validé**, comme pour jouer, et qui a **déjà joué 10 minutes** (temps compté sur
  les relevés d'AzLink, `TempsDeJeu` ; tant qu'AzLink n'a rien envoyé, cette règle ne s'applique pas) ;
- avec le jeton Top-Serveurs : quand **Top-Serveurs a vu le vote** depuis la même connexion internet (vérification
  stricte : une panne de Top-Serveurs ou un jeton refusé ne donnent rien, contrairement à l'extension Vote seule) ;
- sans jeton (cas actuel : identifiants Top-Serveurs perdus) : seulement après un **clic « Voter » sur notre page**,
  anti-robot **Turnstile** passé, depuis la même connexion, **20 s au moins** avant (`Votes\Clic`) ; un programme qui
  appelle directement l'adresse de la récompense n'obtient plus rien ;
- **une fois par connexion internet et par site** sur la durée du vote, tous comptes confondus (même bloc /64 en IPv6).

Une connexion qui vote pour 3 comptes ou plus en 24 h est signalée dans le salon privé des comptes (Discord). La page de
vote dit au joueur ce qui bloque AVANT qu'il clique (il ne gâche pas son vote sur Top-Serveurs).

Sans jeton, le site ne peut pas savoir si le joueur a vraiment voté sur Top-Serveurs : un joueur en règle qui clique
« Voter » puis ferme Top-Serveurs est quand même récompensé (comme avant). Les bots et les fermes de comptes, eux, sont
bloqués.

À régler par le propriétaire :
- **Turnstile** (gratuit) : compte Cloudflare → Turnstile → ajouter le site `heraliz.fr` (mode « Managed ») → copier la
  clé du site et la clé secrète dans l'administration du site → Paramètres → Sécurité → captcha « Turnstile ». Il
  protège aussi l'inscription sur le site et, depuis le launcher 1.16.0, la création de compte dans le launcher (petite
  fenêtre « Vérification anti-robot ») ; la connexion du launcher n'est pas touchée.
- Facultatif, si le compte Top-Serveurs est un jour retrouvé : secret GitHub **`TOPSERVEURS_TOKEN`** = le jeton du
  serveur dans le panel Top-Serveurs. Il passe par `site.yml` → `theme.sh` (environnement) → `reglages.php`, jamais
  dans un fichier du dépôt ; chaque vote est alors vérifié auprès de Top-Serveurs.

## 7. Secrets réservés à `main` : environnements GitHub (02/11, audit point 15)

Avant : les secrets étaient ceux du dépôt entier. Toute personne ou tout outil qui peut envoyer une **branche** pouvait
y glisser un workflow qui les lit (clé de signature du launcher, mots de passe du Ptero et du site). Les workflows
demandent maintenant un **environnement** GitHub :

| Environnement | Qui s'en sert | Branches permises |
|---|---|---|
| `production` | « Serveur HeraliZ » (mise à jour du Ptero, pack du launcher), « Site HeraliZ », publication du launcher, « Remplacer la carte de loot », et les outils de lecture lancés depuis `main` | `main` seulement |
| `lecture` | « Journaux », « Lenteurs », « Enquête Spartan » lancés depuis une branche de travail | toutes |

Tant que le propriétaire n'a rien réglé, GitHub crée ces environnements vides et tout marche comme avant (secrets du
dépôt). Le réglage, à faire une fois (Settings du dépôt, compte propriétaire) :

1. **Settings → Environments → `production`** (le créer s'il n'existe pas) :
   - « Deployment branches and tags » → **Selected branches and tags** → **Add deployment branch or tag rule** → `main` ;
   - ne PAS cocher « Required reviewers » (sinon chaque envoi sur `main` attendrait un clic) ;
   - **Environment secrets → Add secret** : recréer ici, avec les mêmes noms et valeurs, `PTERO_SFTP_HOST`,
     `PTERO_SFTP_PORT`, `PTERO_SFTP_USER`, `PTERO_SFTP_PASSWORD`, `PTERO_URL`, `PTERO_SERVER_ID`, `PTERO_API_KEY`,
     `SITE_SSH_HOTE`, `SITE_SSH_PORT`, `SITE_SSH_UTILISATEUR`, `SITE_SSH_MDP`, `SITE_BDD_MDP`, `LAUNCHER_SIGN_KEY`,
     `SPARTAN_DISCORD_ALERTES`, `SPARTAN_DISCORD_SANCTIONS`, `LITEBANS_DB_USER`, `LITEBANS_DB_PASSWORD`,
     `HERALIBOT_LIEN_URL`, `HERALIBOT_LIEN_CLE`, les `DISCORD_*`, `TOPSERVEURS_TOKEN`, `SITE_ADRESSE_LEGALE`,
     `SITE_MAIL_CONTACT` (ceux qui existent).
2. **Settings → Environments → `lecture`** : « Deployment branches » laissé à **No restriction**. Secrets :
   `PTERO_SFTP_HOST` et `PTERO_SFTP_PORT` (mêmes valeurs), et `PTERO_SFTP_USER` / `PTERO_SFTP_PASSWORD` d'un
   **sous-utilisateur du Ptero en lecture seule** (panel → serveur → Users → New user, cocher seulement
   « file.sftp », « file.read » et « file.read-content »). Pas de `PTERO_API_KEY` ici : depuis une branche,
   « Enquête Spartan » lit les rapports déjà là sans lancer de contrôle, et « Lenteurs » ne lance pas de profil.
3. **Settings → Secrets and variables → Actions → Repository secrets** : **supprimer** les secrets recopiés aux étapes
   1 et 2 (sinon ils restent lisibles depuis n'importe quelle branche). **Garder** au niveau du dépôt seulement
   `NOMS_INTERDITS` (contrôle des branches de travail).
4. Vérifier : envoyer quelque chose sur `main` → « Serveur HeraliZ » vert ; « Site HeraliZ » → Run workflow →
   `verifier` → vert.

À garder en tête : un envoi sur `main` lui-même peut toujours lire ces secrets. Seuls les propriétaires doivent pouvoir
écrire sur `main` (Settings → Collaborators : rôle « Write » seulement pour qui en a besoin ; double authentification
GitHub sur ces comptes).

## 8. Machines distantes reconnues à leur empreinte (02/11, audit point 41)

La mise en ligne envoie le mot de passe de la VM du site (SSH) et celui du Ptero (SFTP). Elle vérifie maintenant que
la machine qui répond est bien la bonne, par sa **clé d'hôte** (publique, rien de secret), avant d'envoyer le mot de
passe (`outils/hote_connu.sh`, et `launcher/ci/publier.py` pour la publication du launcher). Tant que les empreintes ne
sont pas réglées, tout marche comme avant et le résumé de la tâche affiche les clés à recopier.

À faire une fois : lancer « Site HeraliZ » → `verifier`, et une mise en ligne « Serveur HeraliZ » ; dans leur résumé,
la partie « Machine distante non vérifiée » donne les lignes à recopier dans **Settings → Secrets and variables →
Actions → onglet Variables → New repository variable** :
- `SITE_SSH_EMPREINTE` : les lignes de la VM du site ;
- `PTERO_SFTP_EMPREINTE` : les lignes du SFTP du Ptero.

Pour être sûr de recopier les bonnes : comparer avec ce que montre la machine elle-même (VM : `ssh-keygen -lf
/etc/ssh/ssh_host_ed25519_key.pub` dans la console de l'hébergeur). Si la machine change (nouvelle VM, Ptero
déménagé), la mise en ligne s'arrête avec une erreur de clé d'hôte : remplacer la variable par les nouvelles lignes.
