# HeraliZ v2 — règles du projet

Serveur Minecraft **DayZ** (Forge 1.20.1 / Mohist) : survie, loot, armes TaCZ, zombies, clans, bases, events, ClashWar.
Le plan d'ensemble et l'ordre des travaux sont dans `ARCHITECTURE.md` : le lire avant de commencer.
L'ancien dépôt reste dans l'historique Git (avant le 26/10) : on y pioche, on n'y revient pas.

Les propriétaires ne sont pas développeurs : leur répondre en français simple, sans jargon, avec des questions courtes
quand un choix leur revient.

## Règles d'or

1. **On modifie les sources** (`modules/<module>/src/...`) puis on refabrique. Jamais de retouche dans un jar.
2. **Fabriquer et vérifier avant de livrer** : `python outils/construire.py [modules]` puis
   `python outils/verifier.py [modules]` (0 problème exigé).
3. **Aucun prénom, pseudo, mail, chemin de PC, mot de passe, clé ou adresse de serveur** dans le code ou les fichiers.
   L'auteur de nos mods est **HeraliZ**. Seule adresse permise : `play.heraliz.fr`.
4. **Jamais notre nom sur le code des autres** (TaCZ, Curios, cfm, GeckoLib, Backpacked, HBM, LR Tactical, Emotecraft…),
   jamais retirer leurs licences ni leurs crédits.
5. **Pas de test en jeu par Claude** : les propriétaires testent. Chaque compte rendu dit ce qui n'a PAS été testé.
6. **Contenu visuel nouveau** (modèle, arme, interface importante) : rendus validés par le propriétaire d'abord.
7. **La carte se construit sur le serveur** (monde `dayz_map`) : on ne touche jamais aux fichiers du monde.
8. **Identifiants figés** : les identifiants des blocs, objets et entités posés sur la carte ou gardés dans les
   inventaires (`heraliz_decors:…`, `heralisz_utils:…`, `heraliz_loot:…`, `crac:…`, `cfm:…`…) ne changent jamais, même si
   leur code change de module. Même règle pour les clés de données lues par plusieurs mods (`heralisz:combat_jusqua`,
   `heralisz:en_match`, `HeraliszMatch`).
9. **Mise en ligne par GitHub** (depuis le 26/10) : un envoi sur `main` fabrique, contrôle et met à jour le Ptero
   (`.github/workflows/serveur.yml`, secrets GitHub injectés à l'envoi : jamais de secret dans un fichier). Une
   branche de travail est seulement fabriquée et contrôlée (`branches.yml`). Guide : `docs/MISE_EN_LIGNE_GITHUB.md`.

## Organisation

```
modules/<module>/      un jar (qui peut regrouper plusieurs mods), sources coupées en trois :
    src/commun         enregistrements, paquets, état synchronisé, API lues des deux côtés
    src/serveur        ce qui décide : IA, règles, dégâts, butin, commandes, sauvegardes, data/
    src/client         l'affichage : rendus, HUD, écrans, sons, assets/
settings.gradle        la fabrication unique (un include par module)
gradle/module-coupe.gradle   fabrication commune des modules (sources, mixins, trois jars)
libs/                  jars des autres auteurs utiles à la compilation (une seule copie de chaque)
serveur/               configs, plugins, mods des autres auteurs, gunpacks (ce qui va sur le Ptero)
outils/                fabrication, contrôles, générateurs
```

Les modules (dossier → mods qu'il contient) :

| Module | Contenu |
|---|---|
| `noyau` | heraliz_noyau : état du joueur, règles du combat, kit d'interface |
| `armes` | heraliz_tactique (+ LR Tactical, GPL) : TaCZ, mêlée, soins, grenades, camos |
| `equipement` | heraliz_armory, heraliz_composants : armures, combinaisons, composants |
| `sacs` | backpacked (Backpacked de MrCrayfish, LGPL) |
| `loot` | heraliz_loot : conteneurs, rotation, raretés |
| `bunker` | heralisz_bunker (+ HBM Modernized, GPL) : portes, cartes d'accès, salles à carte |
| `monde` | heralisz_utils (+ carte, corps, ping, usure, véhicules), heraliz_decors, heraliz_extraction, airstrike |
| `survie` | reanimation : joueurs à terre, réanimation, exécutions |
| `clans` | heraliz_clansetbases (clans, hérals, saison, territoires) |
| `bases` | crac : bases des clans façon Rust |
| `events` | heraliz_boss : les 5 boss — **mis de côté pour l'ouverture** (fabriqué, livré nulle part : `MODULES_DE_COTE` de `outils/construire.py`) |
| `clashwar` | heraliz_clashwar : tournoi des clans |
| `evenements` | heraliz_evenements : Directeur d'événements (planning, annonces, journal), événements de la carte (largage public toutes les 3 h 30…) et objets d'événement à revendre à Mila (identifiants figés `heraliz_evenements:dossier_classe`, `mallette_scellee`, `disque_dur_militaire`) ; contrat : `docs/EVENEMENTS.md` |
| `pnj` | heraliz_quetes : Varga, quêtes, marchands, recycleur (écran à barre de progression et aperçu ; règles écrites par `outils/recyclage.py`) |
| `boutique` | heraliz_grades, heralizcasino, heralizcrates |
| `emotes` | heraliz_emote (+ Emotecraft, Player Animator) |
| `voix` | caracrea : créateur de personnage, voix de proximité |
| `interface` | heraliz_interface, dayzhotbar (joueurs seulement) |
| `anticheat` | spartan (toujours un jar à part) |
| `banc` | heraliz_banc : bots de charge, **jamais livré avec le serveur normal** (`livraison/banc/`) |

Les mods qui contiennent du code d'autres auteurs (sacs, bunker, armes, emotes, voix, anticheat) restent des jars à part :
leurs licences ne doivent pas s'appliquer à notre code.

- Un module dépend d'un autre par `compileOnly project(path: ':<module>', configuration: 'devComplet')`.
- Le compilateur interdit à `commun` de voir `client` ou `serveur` (méthode : `docs/METHODE_CLIENT_SERVEUR.md`).
- Une classe avec de la logique est coupée en `X` (commun) + `XServeur` ; on la crée par son type ou son usine, jamais
  par `new X(...)`.

## Le noyau (`modules/heraliz-noyau`)

Ce que tout le serveur partage :
- **`fr.heraliz.noyau.api.CombatTag`** : tag de combat de 15 s en PvP ; clé `heralisz:combat_jusqua` du joueur ;
- **`Matchs`** : en match, partie, équipe, spectateur (branché par ClashWar) ; clé `heralisz:en_match` ;
- **`ATerre`** : joueur à terre (Réanimation) ;
- **`EtatClient`** : état côté joueur (combat, en match).

Le noyau porte aussi les règles du combat : déconnexion en combat = mort, commandes refusées en combat, à terre et sur
la carte (`serveur/defaultconfigs/heraliz_noyau-server.toml`), pas de tir à terre, et le kit d'interface « style
clan » (`fr.heraliz.noyau.client.kit`). Ces classes sont lues par réflexion par d'autres mods : **ne jamais les renommer**.

## Événements et points de saison

Le but du serveur est le **classement des clans** : tout événement, toute nouvelle activité rapporte des points de saison.
Avant d'ajouter un événement, suivre la liste de branchement de **`docs/EVENEMENTS.md`** (points via `SaisonApi`, ligne
dans `saison/SaisonVue.bareme()` pour l'onglet Saison, annonces, loot, objet d'événement, textes, perfs, tests).

## Textes et interfaces

- Jamais de texte en dur : clés dans `assets/<modid>/lang/` en **français** (référence), **anglais** et **russe**.
  Côté serveur : `Component.translatableWithFallback(clé, texte français, args...)`.
- Style de toutes les interfaces : celui des écrans de clan (verre, DM Sans, texte net à sa vraie taille). Exceptions :
  inventaire, fouille du loot et sacs (style DayZ de base), machines du casino.
- Texte net : une police par taille (copie de `ui_bold.json` en changeant `size`), jamais `pose().scale` ni `Ui.gros` sur
  du texte ; `outils/polices_nettes.py` règle l'oversample (plafonné pour les grandes tailles) ; outils de dessin net :
  `fr.heraliz.quetes.client.kit.Net`. Caractères absents de DM Sans (◀ ▶ ▲ ✕ ▾…) : dessinés en police pixel, utiliser
  ← → ↑ × ›.
- Contrôle en jeu : captures automatiques dans un vrai client (`docs/AUDIT_UI.md` : `outils/client_essai.py`, mod
  `modules/capture` jamais livré, scripts `outils/capture/scripts/`). Serveur d'essai local seulement : sans AuthMe, lancer
  avec `-Dheraliz.essai.sansAuthme=true` ET `server-ip=127.0.0.1` (jamais sur le Ptero).

## Performances (priorité)

- Rien ne tourne pour rien : un système sans travail sort tout de suite de son tick (exemple : ClashWar hors tournoi).
- Pas d'allocation par tick dans les boucles chaudes ; pas de parcours de tous les joueurs ou entités à chaque tick.
- Ce qui peut être une donnée (loot, prix, zones, réglages) va dans un fichier rechargeable plutôt que dans le code.
- Conteneurs de loot allégés : `modules/loot/src/client/resources/assets/heraliz_loot/lod/conteneurs_lod.txt` est écrit par
  `python modules/loot/outils/lod_props.py --installer`, jamais à la main ; à relancer après tout changement d'un modèle
  de conteneur (`python outils/verifier.py loot` le signale ; sinon le jeu garde ce modèle moins allégé).
