# Refonte des HUD en jeu (25/10)

Suite de l'audit `docs/AUDIT_VISUEL_ZOMBIES_25OCT.md` (§2.1, §3). Quatre HUD refaits avec un **kit HUD commun dans le
noyau** : joueur à terre, réanimateur (et carte « viser un joueur à terre »), soins / grenades, usure de l'arme.
**Rien n'a été compilé ni essayé en jeu** (travail fait dans la copie `HeraliZ-chantier`, à appliquer puis fabriquer).
**Aucune règle du serveur ne change** : durées, distances, interruptions, anti-abus, usure, soins restent ceux d'avant ;
seul l'affichage chez le joueur change.

Maquettes : `maquettes/hud_refonte/`, faites par `python modules/noyau/outils/apercu_hud.py` (français, anglais, russe ;
chaque image a sa version agrandie `_zoom` ; `--refaites` : seulement à terre, soins et usure). Le script lit les mesures
**dans le code Java** et les tailles **dans les fichiers de police** : maquettes et jeu ont les mêmes cotes. Il refuse
tout texte qui dépasse.

**Retours du propriétaire (25/10)** : réanimation (anneau, carte de la cible) **validée telle quelle** ; soins : plus
d'anneau, « une petite barre bien propre, un peu en italique » ; à terre : « trop gros, faut un truc bien plus
smooth » (plus de bloc) ; usure : « sur une ligne, un truc plus moderne ». La deuxième version ci-dessous en tient compte
(nouvelles maquettes `a_terre_*`, `soins_*`, `usure_*`).

---

## 1. Direction artistique

Style « DayZ militaire / post-apo » : sobre, lisible sur le monde, peu de chrome. Formes **penchées de 12°** comme les
cases de la barre d'objets DayZ (HotbarOverlay) pour les barres ; verre des écrans de clan en version HUD (fond sombre,
bord et liseré d'un pixel d'écran) ; textes posés sur le monde avec un voile sombre aux bords fondus plutôt qu'un cadre.

### Palette (une seule valeur par rôle, `fr.heraliz.noyau.client.kit.Palette`)

| Rôle | Couleur | Usage |
|---|---|---|
| danger | `#E3201B` (rouge du logo) | à terre, interruption, arme ruinée, mèche d'une grenade |
| alerte | `#E8B34A` | fouille en cours, arme usée |
| ok | `#5FD08A` | soin, réanimation, réussite, arme en bon état |
| info | `#6FA8FF` | information (réservé) |
| texte | `#ECEEF2` | texte principal |
| atténué | `#8F95A0` | aides, légendes |
| très atténué | `#5A5F69` | « (maintenir) » |
| verre | `#0D0F14` à 80 % | fond des panneaux (usure : 59 %) |

Teintes claires : `Palette.clair(couleur, t)` (mélange vers le blanc). Usure : bon = ok→alerte 45 %, abîmé =
alerte→danger 50 %.

### Polices (`PoliceHud`, fichiers `assets/heraliz_noyau/font/hud_*.json`)

Toujours à leur **vraie taille** : plus aucun `pose().scale` sur un texte. Tailles de la charte (em, en unités
d'interface) : **6,5 / 8 / 11 / 16**.

| Police | Fichier | Usage |
|---|---|---|
| `TITRE` / `CHIFFRE` | Oswald Bold 11 / 16 | réservées aux grands titres (plus utilisées par ces quatre HUD) |
| `CHIFFRE_PETIT` | Oswald Bold 8 | temps du joueur à terre, temps au centre de l'anneau, pourcentage d'usure |
| `TEXTE` / `TEXTE_GRAS` | DM Sans 8 | ligne du réanimateur, pseudo de la carte |
| `MINI` / `MINI_GRAS` | DM Sans 6,5 | « À TERRE » (capitales espacées), état, aides, touches, nom de l'objet, état de l'arme |

- Oswald (`oswald_bold.ttf`, sous-ensemble latin, licence OFL `oswald_ofl.txt` à côté) n'a **pas** le cyrillique :
  Onest Bold prend le relais, réglé pour la **même ligne de base** (taille du .json × 1,0581).
- Taille du .json = em × (ascendante − descendante) / unités par em (Oswald 1,482 ; DM Sans 1,302 ; Onest 1,275).
- Placement vertical (Minecraft 1.20.1, TTF sans décalage) : ligne de base = point de dessin + em × ascendante / 1000 − 3.
  Les HUD posent les textes sur le **milieu des capitales**.
- Chaque texte est posé sur un **pixel entier** de l'écran (net), avec une ombre douce d'un pixel d'écran.
- Les lettres de `heraliz_noyau` sont lissées par l'interface (`PolicesNettes`, déjà dans la liste).

### Ancrage et cotes (unités d'interface)

| HUD | Ancrage | Cotes |
|---|---|---|
| À terre | bas-centre, sans cadre | ligne principale à 43 du bas, barre de 150 × 1 à 35,5, état à 27,5, aide à 16 ; voile de 260 de large aux bords fondus (80 sur les côtés, 14 en haut et en bas) |
| Réanimateur | réticule | anneau rayon 12 (24 de diamètre), épaisseur 1,5 ; lignes à +22 et +32 |
| Carte de la cible | réticule +16, +10 | 152 de large, marge 8 |
| Soins / grenades | réticule | barre penchée 46 × 2,5 à +11 sous le centre, temps à droite (+4), nom à +19,5, aide à +28,5, « lancer doux » à +38 |
| Usure | bas à droite, 8 du bord, milieu des cases de la barre DayZ (12 du bas) | une ligne de 11 de haut : 10 segments de 3,2 × 5 (écart 1), pourcentage, état ; marge 6, écarts 5 ; jamais à moins de 6 du bord droit réel de la barre DayZ |

Touches : cadre de 9 de haut, 3 de marge.

### Animations (`Anim`)

- Fondus : apparition 180 ms et disparition 250 ms (sortie cubique) ; **à terre : fondu doux** (smoothstep) de 350 ms
  et 450 ms, la ligne glisse de 3 unités. Chaque HUD **garde ce qu'il montrait** pendant sa sortie, puis ne dessine
  plus rien.
- Jauges **interpolées entre deux ticks** (sang, réanimation, soin, mèche) ; transitions lissées (à terre : barre et
  couleur rouge → vert ; usure : couleur et segments, 10 / s).
- Anneau du réanimateur : normal, **attente** (fini chez le joueur, serveur pas encore répondu : arc clair qui tourne),
  **interrompu** (rouge qui se vide en 300 ms), **réussi** (coche verte 600 ms).
- Barre des soins (`BarreProgression`) : normale, **interrompue** (rouge qui se vide en 300 ms).
- Battement du cœur (point de la ligne à terre). Jamais de clignotement.

### Performances

- Formes envoyées au tampon puis **un seul vidage**, textes ensuite, **un seul vidage**.
- Textes en cache (`TexteHud`, `ToucheHud`) : recalculés seulement si la langue, la touche ou la valeur affichée
  change (une fois par seconde pour le chrono, par dixième pour les temps courts).
- Usure : un texte par pourcentage (101 au plus), aucune allocation par tir ; bord de la barre lu par `MethodHandle`.
- Aucune allocation par image dans les chemins chauds (pas de `I18n.get` ni de `String.format` à chaque image).

---

## 2. Les quatre HUD

| HUD | Classe | Ce qui change |
|---|---|---|
| À terre | `survie/.../client/hud/HudATerre` | plus de bloc : une ligne fine « ● À TERRE · 0:24 » au bas-centre (point qui bat), barre de 1 unité du sang qui se vide en douceur, état en petit (« Tu te vides de ton sang · un allié peut te relever », « X te fouille », « X te réanime, tiens bon »), aide d'abandon en tout petit (la touche se remplit) ; réanimé : tout passe au vert en douceur ; voile sombre aux bords fondus, vignette très légère ; fondu doux |
| Réanimateur | `.../hud/HudReanimateur` | **validé** : anneau, temps au centre jusqu'à la réponse du serveur (27/10 : avant, caché sous 2 s), « Réanimation de PSEUDO », « Bouger annule » ; 100 % avant la réponse du serveur = attente ; réussite = coche 600 ms ; interruption = rouge 300 ms ; réticule caché pendant ces fins |
| Carte de la cible | `.../hud/CarteCible` | **validée** : pseudo + pastille « À TERRE », sang + « Mort dans 24 s », actions avec les vraies touches (exécuter : la touche se remplit) ou ligne d'état |
| Soins / grenades | `armes/.../tactique/client/soins/HudSoins` (code neuf, hors du paquet GPL) | petite barre penchée sous le réticule (le réticule reste), remplie en continu, temps restant en petit à droite, nom de l'objet en petit, aide discrète « [Clic droit] arrêter » **pendant** l'action, « Interrompu » (barre rouge qui se vide) ; grenade : dégoupillage blanc puis mèche rouge, « explose dans 2,1 s », « Lancer doux » ; l'ancien `UsingProgressOverlay` (GPL) reste dans le dépôt, n'est plus enregistré |
| Usure | `monde/.../usure/client/HudUsure` | une ligne : barre segmentée penchée + pourcentage + état, sur un verre penché ; largeur fixe (par langue) ; bord droit réel de la barre DayZ (`HotbarOverlay.bordDroit`) ; version courte (segments seuls) s'il manque de la place (écran étroit) ; couleur selon l'état, transitions douces ; arme ruinée : contour rouge avivé au clic à vide |

## 3. Fichiers

Nouveaux :
- `modules/noyau/src/client/java/fr/heraliz/noyau/client/kit/` : `Palette`, `Anim`, `PoliceHud`, `TexteHud`,
  `ToucheHud`, `Formes` (dont formes penchées : `parallelogramme`, `bordPenche`, `verrePenche`, `segments`, et `voile`),
  `AnneauProgression`, `BarreProgression` ;
- `modules/noyau/src/client/resources/assets/heraliz_noyau/font/` : `oswald_bold.ttf`, `oswald_ofl.txt`, `hud_titre`,
  `hud_chiffre`, `hud_chiffre_petit`, `hud_texte`, `hud_texte_gras`, `hud_mini`, `hud_mini_gras` (.json) ;
- `outils/polices/oswald_ofl.txt` (licence de la police déjà présente là) ;
- `modules/noyau/outils/apercu_hud.py` (maquettes) ;
- `modules/survie/src/client/java/fr/heraliz/reanimation/client/hud/` : `HudATerre`, `HudReanimateur`, `CarteCible` ;
- `modules/armes/src/client/java/fr/heraliz/tactique/client/soins/HudSoins.java` ;
- ce fichier.

Modifiés :
- `modules/survie` : `DownedHud` (aiguillage vers les nouveaux HUD ; exécutions inchangées), `ClientDowned` (réponse du
  serveur transmise à `HudReanimateur`), `ClientEvents` (réticule caché aussi pendant la coche / l'anneau rouge),
  langues fr / en / ru (5 clés), `build.gradle` et `mods.toml` (dépendance au noyau) ;
- `modules/armes` : `ClientTactique` (branche `HudSoins`), `ClientSetupHandler` (code GPL : seule la ligne qui
  enregistrait l'ancien anneau est retirée), langues (4 clés), `build.gradle` et `mods.toml` (dépendance au noyau) ;
- `modules/monde` : `HudUsure` (réécrit), `mods.toml` (heraliz_usure dépend du noyau) ; `FormesHud` et `Maillage` de
  l'usure ne servent plus (à supprimer plus tard) ;
- `modules/interface` : `HotbarOverlay.bordDroit(int)` (lecture du bord droit réel de la barre, par réflexion depuis
  Monde : garder son nom et sa signature).

## 4. Reste à faire / à vérifier

- Fabriquer (`construire.py noyau survie armes monde interface`) et `verifier.py` : rien n'a été compilé.
- Essais en jeu : lisibilité en 1080p et 1440p, échelles 2, 3, 4 ; anglais et russe ; touche d'abandon remplie ;
  réanimation réussie et interrompue (bouger, coup) ; soin interrompu (changer d'objet) et fini ; grenade dégoupillée ;
  usure sur une barre DayZ réglée (espacement, inclinaison, gaucher) et sur un écran étroit ; F1, F3, spectateur.
- Usure : sous 334 unités de large (fenêtre très petite), même la version courte touche la barre DayZ.
- Les autres HUD (quêtes, radio, ClashWar, extraction, jumelles...) passeront au même kit ensuite.
