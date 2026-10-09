# Aperçu des jumelles

Images des jumelles de l'Airstrike **sans lancer le jeu**, pour les faire valider par le propriétaire avant de toucher au
mod (règle des rendus d'abord). Le chemin de dessin du jeu est reproduit : main à la 1re personne (`ItemInHandRenderer`,
`applyForgeHandTransform`, transformation d'affichage du modèle d'objet), joueur à la 3e personne (`LivingEntityRenderer`,
`HumanoidModel.setupAnim`, `ItemInHandLayer`), et le rendu des jumelles (`DesignatorRenderer` : modèle `.geo`, trappe,
voyant, écran, cartouches, trajet de la main pendant le rechargement).

Ce qui diffère du jeu : pas de lumière du monde (plein jour), pas de brouillard, un décor simple (ciel, herbe). Toujours
dire au propriétaire que ce n'est pas testé en jeu.

## 1. Préparer (une fois, et après chaque changement de modèle)

```
python3 prep.py <dossier>                                         modèle du mod, cartouches, joueur (skin de Steve)
python3 prep.py <dossier> --apres <designator.geo> <designator.png>   + un modèle proposé (voir ../modele/jumelles.py)
python3 prep_tacz.py ... <dossier> m16a1                           une arme TaCZ de référence (détail en tête du fichier)
```

Le dossier préparé ne va pas dans Git (refait à la demande).

## 2. Rendre

Node.js et Playwright (avec son Chromium) :

```
NODE_PATH=$(npm root -g) node rendre.js travaux.json
```

`travaux.json` : `{"donnees": "<dossier>", "armes": ["m16a1"], "rendus": [{"sortie": "image.png", ...options}]}`.
Options d'un rendu (voir `rendre()` dans `apercu.html`) :

- `vue` : `fps` (1re personne), `tps` (3e personne : `camYaw`, `camPitch`, `camDist`, `tete` [lacet, tangage]),
  `objet` (l'objet seul en orbite), `gui` (case d'inventaire) ;
- `version` : `avant` (le mod tel qu'il est) ou `apres` (la proposition d'`apres.js`) ;
- `etat` : `repos`, `vise` (`tVise` : ticks depuis le clic droit), `recharge` (`p` : avancement 0..1, `charge`,
  `ancienne` : cartouches), et pour la référence `tacz` (`arme`) ou `main_vide` (la main vide du jeu) ;
- `modele: "origine"` : la version `apres` avec le modèle d'origine ; `R` : réglages d'`apres.js` remplacés le temps
  d'un rendu (essais de placement) ; `w`, `h` : taille de l'image.

`apres.js` contient la proposition (placements, prises des mains, trajet du rechargement, poses à la 3e personne) :
ce sont les valeurs reprises dans le mod une fois validées.
