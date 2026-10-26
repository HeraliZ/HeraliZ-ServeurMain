# Audit des écrans en jeu (25/10, soir)

Demande du propriétaire : faire le tour de toutes les interfaces dans leur **rendu réel en jeu**, corriger ce qui est mal
placé et ramener toutes les interfaces à la même charte que les écrans du clan et la Table d'armurier (verre sombre, DM
Sans à sa vraie taille, texte net, centré, compréhensible).

## L'outil : captures automatiques dans un vrai client

- **HeraliZ Capture** (`modules/capture`) : petit mod du client d'essai, **jamais livré** (`MODULES_DE_COTE` de
  `outils/construire.py`). Il joue un script (`<jeu>/capture/script.json`) : commandes, touches, écrans, clics et texte
  dans un écran, interactions avec une entité ou un bloc, mort et réapparition, captures. Détail des étapes : en tête de
  `modules/capture/src/client/java/fr/heraliz/capture/client/Script.java`.
- **`outils/client_essai.py`** : copie un profil CurseForge à jour dans un dossier de travail, y ajoute HeraliZ Capture
  et le script, puis lance Minecraft (Forge du lanceur CurseForge, compte hors ligne) qui rejoint le serveur de test,
  joue le script et se ferme. Les captures arrivent dans `<dossier>/screenshots/`, le journal dans
  `<dossier>/capture/journal.txt` (classe de l'écran ouvert à chaque capture).

  ```
  python outils/client_essai.py "<profil CurseForge>" <dossier de travail> outils/capture/scripts/tour_ecrans.json
  ```

- Scripts : `outils/capture/scripts/tour_ecrans.json` (tour de ~25 écrans), `verif_hud.json` (contrôle rapide).
- Le serveur de test doit accepter un compte hors ligne sans le launcher (`tokenRequired` et `captureRequired` à false
  dans **son** `spartan-common.toml`). Jamais sur le Ptero.
- Mémoire : un client (3 Go) + le serveur de test (4 Go) : fermer le jeu et les gros programmes pendant les captures.

## Corrigé (25/10, fabriqué et contrôlé ; vu dans le client d'essai)

| Défaut vu en jeu | Correction |
|---|---|
| Le HUD (panneau de zone, Purge, suivi de quête, cœurs, barre d'objets) passait par-dessus ou à travers les écrans plein écran (quêtes, warps, emotes, exécutions, fiche des bases, créateur de personnage, mort) | Une seule règle pour tous nos écrans (`EcransPleins` de heraliz_interface) : derrière un écran plein écran, ni HUD ni main ; seuls le chat et les inventaires / coffres / fouilles gardent le HUD (style DayZ). Le panneau de zone des clans suit la même règle (`ClanHud`). |
| Quêtes, warps, kits, emotes, exécutions sans fond sombre | Fond des écrans du clan posé derrière eux (`EcransPleins.FOND`). |
| « Impossible de vérifier les messages » (avertissement de Minecraft pour un serveur hors ligne) par-dessus le créateur de personnage, à chaque connexion | Masqué chez le joueur (`ToastsMixin`). |
| Chat fermé : la dernière ligne passait sur les cœurs (échelle d'interface 4, la plus courante en 1080p) | Lignes au-dessus des cœurs et de l'armure, chat ouvert et fermé. |
| Mort : l'objet en main (soins, arme du mod tactique) restait dessiné | Mains et objets cachés dès la mort (priorité la plus haute). |

## Refait d'après maquette validée

- **Journal des quêtes** (touche J, Quêtes 1.12.0) : codé d'après `maquettes/quetes/journal_quetes.png` et
  `journal_quetes_vide.png` (script `modules/pnj/outils/apercu_journal.py`, mêmes mesures). Polices à leur vraie taille
  (`ui_titre` 14, `ui_small_bold` 9, `ui_micro` 7,5), texte centré sur le milieu des capitales et posé au point d'écran,
  personnages avec avatar et réputation, cartes de quête (difficulté en points, « Suivie », « À rendre », étapes cochées
  avec barre, récompense, Suivre / Ne plus suivre / Abandonner en deux clics), état vide, touches en bas. Vu en jeu :
  vide, trois quêtes, suivre, abandon (captures `outils/capture/scripts/journal.json`).

## Corrigé au 2e passage (25/10, nuit)

| Défaut vu en jeu | Correction |
|---|---|
| Caisses : la carte du milieu de la roue touchait sa voisine | Écart 82 et décroissance 0,35 (`CaseOpenScreen.tailleCarte`) : 9 à 10 points entre deux cartes |
| Chat ouvert (1080p) : saisie et « TAB pour changer » sous la barre d'objets | Saisie et onglets s'arrêtent avant la barre d'objets ; l'aide passe dans la case de saisie vide quand elle ne tient plus à côté des onglets |
| Éditeur du marchand : chiffres dans la police pixel de Minecraft, « item.tacz.modern_kinetic_gun », flèches ◀ ▶ et croix ✕ dessinées en police pixel (absentes de DM Sans), titre agrandi | Champs du kit (`HzField`), « Toutes les armes à feu / munitions / accessoires », ← → ↑ ×, titre en police 14 |
| Registre de l'armurier : onglets tronqués (« MUNITI… »), mêmes défauts de champs, flèches et titre | Onglets en pastilles (les dix entiers), `HzField`, ← → ×, titre en police 14 ; « Onglet › » au lieu de « ▾ » |
| Conteneurs de loot, planque, coffre VIP : titre gris foncé sur gris (illisible), « Inventaire » coupé par le bord | Titre clair, étiquette « Inventaire » retirée (comme l'inventaire) ; tous les coffres de base passent par le même écran |
| Marchand, dialogue des PNJ, note, clavier à code : nom ou code agrandi (flou) | Polices `ui_titre` (14) et `ui_code` (18) à leur vraie taille |

Relevé automatique des caractères absents de DM Sans (dessinés en police pixel) : ◀ ▶ ▲ ▼ ✕ ▾ dans des écrans → remplacés
par ← → ↑ × ›. Restent quelques symboles dans des messages du chat et des infobulles (★ ☠ ⚠ ❄ ☢ ✦ 🔒) : dessinés par la
police de secours de Minecraft, à remplacer si le propriétaire les trouve gênants (relevé : script de l'audit, à refaire).

## Texte agrandi supprimé partout (25/10, nuit)

Relevé automatique des textes dessinés sous `pose().scale` ou `Ui.gros` (flous), puis une police à la vraie taille pour
chacun : boutique (échange, kits, warps, éditeur des grades), clan (banque, niveau, tuiles de l'aperçu), menu Échap et
Informations (nom, tuiles hérals / en ligne / ping), emotes, exécutions, joueur à terre (bandeau, sous-titre), badge des
conteneurs, plongée, HUD des matchs (bandeau, annonces, compte à rebours), choix de classe, extraction, cinématique,
roue et HUD des jumelles, radio de Varga, suivi de quête, armoire de base, raid, atelier d'arme. Un agrandissement animé
qui revient exactement à 1 (apparition d'une annonce, pulsation) est gardé. Laissés exprès : la carte du monde (noms
selon le zoom, validé), les murs du ClashWar (dessinés dans des textures), les machines du casino (thème à part), les
numéros de la barre d'objets DayZ (police pixel calée sur la grille de l'écran). Reste à faire : bandeaux réduits du HUD
des matchs (compact ×0,72, spectateur ×0,8, petites annonces).

Grandes polices : Minecraft range les lettres dans des planches de 256 points ; `outils/polices_nettes.py` plafonne
l'oversample à 240 / taille (une lettre trop grande disparaîtrait).

## Recycleur refait (25/10, maquette validée : `maquettes/recycleur/`)

Écran à cases (`MenuRecycleur`, `EcranRecycleur`, partie serveur `MenuRecycleurServeur`) : « À recycler » (6 cases), la
barre de l'objet en cours (3 s par objet), « Récupéré » (6 cases, « Tout prendre »), hérals sur le compte quand rien ne
sort, aperçu de l'objet survolé (pièces possibles et chance d'en avoir au moins une, moyenne, « rien du tout », ou la
raison d'un refus). Économie dure (demande du propriétaire) : 10 % par pièce, 2 pièces au plus par objet, hérals « rien »
1 à 4 (`outils/recyclage.py`) ; un AK-47 rend 0,9 pièce en moyenne (rien 35 % du temps), un M1911 0,5, un viseur 0,4.
Vu en jeu sur le serveur d'essai (`outils/capture/scripts/tour_boutique.json`) : dépôt, barre, pièces, Tout prendre,
refus expliqués, HUD caché derrière. Canal des quêtes version 9 : jars joueurs et serveur de la même fabrication.

## Kits, boutique et écrans du staff (vus en jeu grâce au serveur d'essai local)

Le serveur d'essai (Forge) n'a pas AuthMe : boutique, kits, éditeurs et panel refusaient de s'ouvrir. Interrupteur
réservé au serveur d'essai : `-Dheraliz.essai.sansAuthme=true` ET `server-ip=127.0.0.1` (sinon rien ne change ; jamais
sur le Ptero). Kits refaits : nom entier et état sous le nom dans la liste (avant « Kit Roi de la Zo… »), contenu en liste
(case, nom, quantité ; deux colonnes et « + N autres » si besoin), durées « 1 j » / « 7 h ». Éditeur des grades : aperçu
du chat et du tab dans la police du vrai chat. Bouteille d'eau vide nommée (3 langues). Boutique, panel du staff et
éditeur des bons : propres.

## Écrans vus, sans défaut majeur

Carte (M), menu Échap, Informations, fiche des bases (`/base info`), écran de mort sans base, création de clan, clan sans
clan, exécutions, emotes (vide : aucune emote débloquée), coffre T7 (style DayZ voulu), inventaire (style DayZ voulu),
barre de soin, liste des joueurs (Tab), pings, réglages de la voix, Table d'armurier, caisse, dialogues et marchand de
Mila, warps (avec message quand la liste est vide), boutique, panel du staff, éditeur des bons.

## Pas encore capturés

Écran du clan avec un clan (le script ne sait pas encore remplir le formulaire de création), Atelier d'arme (la touche de
TaCZ ne s'ouvre pas par le script), sacs à dos, fouille d'un corps et d'un joueur à terre, écran Graphismes (constructeur
à deux arguments), `/trade` (il faut deux joueurs). Rien de tout cela n'a été joué par un vrai joueur : à tester en jeu.
