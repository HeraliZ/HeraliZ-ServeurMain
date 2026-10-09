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

## Zones fixes (admins, OP 2)

```
/extraction create <nom> [rayon] [secondes]   crée une zone permanente à ta position
/extraction remove <nom>                      supprime une zone
/extraction list                              liste les zones
```

Zone rouge seule (sans PC ni fumée), permanente (survit aux redémarrages), utilisable avec ou sans toit.

## Réglages

`config/heraliz_extraction-common.toml`

| Clé | Défaut | Rôle |
|---|---|---|
| `durationSeconds` | 90 | temps à tenir dans la zone |
| `radius` | 8.0 | rayon de la zone (blocs) |
| `zoneLifetimeSeconds` | 300 | durée d'ouverture de la zone du terminal |
| `useWorldSpawn` | true | TP au spawn du monde, sinon `dimension` / `x` `y` `z` |

## Où toucher

- `src/commun` : enregistrements (objet, entité, particules), réglages, état synchronisé du terminal, forme de la zone,
  particules de la fumée et de la zone ;
- `src/serveur` : pose du terminal, chronos et barres des joueurs, téléportation, zones fixes et commandes ;
- `src/client` : PC en main et à genoux, écran du PC, rendu du terminal et de la zone, textures.

## Outils

- `outils/generate_textures.ps1` : refait toutes les textures (pixel art procédural, Windows PowerShell).
- `gradlew renderPreview` : aperçus PNG du modèle (au sol, inventaire, 3e/1re personne) dans `build/preview/`, avec le
  même code que le jeu (sources dans `outils/apercu`, jamais dans les jars).
