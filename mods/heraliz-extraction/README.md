# HeraliZ Extraction

Système d'extraction du serveur (Forge 1.20.1), coupé en trois jars comme nos autres mods :
`-serveur` (Ptero), `-client` (joueurs), `-complet` (solo). Le jar va **sur le serveur et chez les joueurs**.

## Terminal d'extraction (objet)

PC portable bricolé avec une caisse à fumigène fixée sur le côté.

1. Maintenir **clic droit** : le joueur se met à genoux, ne peut plus bouger et tape la séquence de lancement (3 s).
   L'écran du PC affiche ce qu'il tape.
2. À ciel ouvert, le PC est posé au sol : la caisse s'ouvre, le fumigène se redresse et une **colonne de fumée rouge**
   monte dans le ciel (visible de loin).
3. Sous un toit, la séquence échoue : l'écran affiche `ERR: toit détecté` / `LANCEMENT INTERROMPU`, puis
   `nouvel essai...` / `ACCÈS REFUSÉ` tant que le joueur insiste. Rien n'est posé ni consommé.

La zone reste ouverte **5 minutes** pour **tous** les joueurs : chacun qui y tient **1:30** est téléporté au spawn.
Sortir de la zone remet le chrono du joueur à zéro. Seule une barre en haut de l'écran est affichée.
La zone (rayon 8 par défaut) suit le relief, s'arrête sur les murs et n'entre pas dans les bâtiments.

```
/give @p heraliz_extraction:extraction_terminal
```

## Zones fixes de la carte (admins, OP 2)

Les extractions de la carte : zone rouge (sans PC), qui survit aux redémarrages, utilisable avec ou sans toit, retour
au spawn au bout du temps à tenir (30 s par défaut). Elles **tournent par vagues** (1.5.0) : 4 zones ouvertes
ensemble pendant 1 heure, puis toutes ferment et 4 autres ouvrent (aucune de la vague précédente si la carte en a
assez). Le tirage est au hasard, mais les zones ouvertes sont à 800 blocs au moins les unes des autres et au plus une
par quart de la carte (quarts autour du centre des zones) ; si les zones posées ne le permettent pas, la distance est
baissée peu à peu, puis les quarts relâchés, et c'est noté dans le journal du serveur (`[extraction] ...`). Une zone
fermée ou supprimée à la main pendant une vague est remplacée jusqu'à la fin de la vague. `waves = false` : l'ancienne
rotation (chaque zone sa durée, une autre prend le relais), avec la même règle de distance. Une zone ouverte a sa
colonne de fumée (repère de loin).

Répartition des zones sur la carte : workflow « Extractions de la carte » (`.github/workflows/extractions.yml`, lecture
seule) et `outils/extractions_carte.py` : liste, carte des zones, couverture et points proposés pour combler les trous
(dans `monde/extractions/` de la branche de travail).

- Ouverture : annoncée dans le chat à tous les joueurs de la dimension (« [Extraction] Aérodrome nord ouverte pendant
  45 min · 850 m au nord-est », clic = `/extractions`).
- 2 min avant la fermeture : rappel aux joueurs à moins de 200 blocs ; puis « est fermée ». Ceux qui étaient en train
  de s'extraire perdent leur chrono.
- Un joueur à terre (Réanimation) ne peut pas s'extraire.
- Les joueurs reçoivent la liste des extractions ouvertes (connexion, chaque ouverture / fermeture) : la carte du monde
  la lit dans `com.heraliz.extraction.api.ExtractionApi` (API stable).

```
/extraction create <nom> [rayon] [secondes]   crée une zone à ta position (elle entre dans la rotation)
/extraction label <nom> <nom affiché>         nom montré aux joueurs (« Aérodrome nord »)
/extraction remove <nom>                      supprime une zone
/extraction list                              liste les zones (mode, ouverte ou non, temps restant)
/extraction open <nom> [minutes]              ouvre une zone tout de suite (sans minutes : 30 à 60 min)
/extraction close <nom>                       ferme une zone (la rotation en ouvre une autre)
/extraction mode <nom> rotation|permanent|closed
                                              rotation (défaut), toujours ouverte, ou seulement à la main
/extraction tp <nom>                          va voir une zone
/extraction rotation                          état de la rotation et zones ouvertes
/extraction rotation next                     ferme les zones ouvertes, d'autres ouvrent
```

Pour tous les joueurs : `/extractions`, les extractions ouvertes avec leur distance, leur direction et le temps avant
leur fermeture.

## Réglages

`config/heraliz_extraction-common.toml`

| Clé | Défaut | Rôle |
|---|---|---|
| `durationSeconds` | 90 | temps à tenir dans la zone du terminal |
| `radius` | 8.0 | rayon de la zone (blocs) |
| `zoneLifetimeSeconds` | 300 | durée d'ouverture de la zone du terminal |
| `zones.durationSeconds` | 30 | temps à tenir dans une zone fixe créée sans temps |
| `zones.smoke` | true | colonne de fumée au-dessus des zones fixes ouvertes |
| `rotation.enabled` | true | rotation des zones fixes (false : ouvertures à la main seulement) |
| `rotation.openAtOnce` / `openAtOnceMax` | 4 / 4 | zones ouvertes en même temps (au hasard entre les deux) |
| `rotation.minMinutes` / `maxMinutes` | 60 / 60 | durée d'ouverture d'une zone ou d'une vague (au hasard entre les deux) |
| `rotation.warnMinutes` | 2 | rappel aux joueurs proches avant la fermeture (0 = jamais) |
| `rotation.announce` | true | annonce de chaque ouverture dans le chat |
| `rotation.waves` | true | par vagues : toutes les zones changent ensemble |
| `rotation.minDistance` | 800 | distance minimale (blocs, à plat) entre deux zones ouvertes (baissée si impossible) |
| `rotation.spread` | true | au plus une zone ouverte par quart de la carte (relâché si impossible) |
| `useWorldSpawn` | true | TP au spawn du monde, sinon `dimension` / `x` `y` `z` |

## Où toucher

- `src/commun` : enregistrements (objet, entité, particules), réglages, état synchronisé du terminal (zone ouverte,
  fumée), forme de la zone, particules de la fumée et de la zone, paquet des extractions ouvertes, `api/ExtractionApi` ;
- `src/serveur` : pose du terminal, chronos et barres des joueurs, téléportation, zones fixes (`ExtractionZones`),
  rotation et annonces (`Rotation`), commandes ;
- `src/client` : PC en main et à genoux, écran du PC, rendu du terminal et de la zone, textures.

## Outils

- `outils/generate_textures.ps1` : refait toutes les textures (pixel art procédural, Windows PowerShell).
- `gradlew renderPreview` : aperçus PNG du modèle (au sol, inventaire, 3e/1re personne) dans `build/preview/`, avec le
  même code que le jeu (sources dans `outils/apercu`, jamais dans les jars).
