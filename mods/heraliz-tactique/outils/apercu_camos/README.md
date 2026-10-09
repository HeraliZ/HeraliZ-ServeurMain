# Aperçu des camos

Images des camos sur de vraies armes du serveur, **sans lancer le jeu**, pour les faire valider par le propriétaire
avant de toucher au mod (règle des rendus d'abord). Le code dessiné est le vrai : la partie `CAMO LIB` d'un
`camo_<id>.fsh` (ou de `outils/camo_gabarit.fsh`), avec le même `main` que le jeu.

Ce qui diffère du jeu : la position sur l'arme vient des sommets (pas de la carte de positions), pas de brouillard,
pas de lumière du monde (plein jour), éclairage des entités de Minecraft. Les couleurs sont donc très proches, pas
identiques : toujours dire au propriétaire que ce n'est pas testé en jeu.

## 1. Préparer des armes (une fois)

```
python3 prep.py <modèle *_geo.json> <texture uv .png> <dossier> <nom>
```

Exemples pris dans les gunpacks du dépôt (`serveur/tacz/`) :

| nom | modèle | texture |
|---|---|---|
| xm177 | `arkaniaz_pack_5_converted/assets/maxstuff/geo_models/gun/xm177_geo.json` | `.../maxstuff/textures/gun/uv/xm177.png` |
| an94 | `arkaniaz_pack_1_converted/assets/arkaniaz/geo_models/gun/an94_geo.json` | `.../arkaniaz/textures/gun/uv/an94.png` |
| glock_18c | `arkaniaz_pack_5_converted/assets/maxstuff/geo_models/gun/glock_18c_geo.json` | `.../maxstuff/textures/gun/uv/glock_18c.png` |

Le dossier des armes préparées ne va pas dans Git (gros fichiers refaits à la demande).

`prep.py` reproduit aussi le repérage de la **carcasse** du mod (`PosMap.RECEIVER`, partie 0.94 : pavé de diamants du
Diamond) : pièces nommées upper / lower / receiver / body / frame / slide…, sinon la zone entre la crosse et le
garde-main autour du haut du chargeur. Si l'une des deux règles change, changer l'autre (elles sont comparées pièce par
pièce sur les 147 modèles des gunpacks : 0 différence le 08/10).

## 2. Rendre

Node.js et Playwright (avec son Chromium) :

```
NODE_PATH=$(npm root -g) node rendre.js travaux.json
```

`travaux.json` (détail en tête de `rendre.js`) :

```json
{"donnees": "/tmp/armes", "armes": ["xm177"],
 "passes": [{"shader": "../../src/client/resources/assets/heralisz_camo/shaders/core/camo_1.fsh",
             "rendus": [{"arme": "xm177", "camo": 1, "vue": "orbite", "yaw": 80, "pitch": 10, "zoom": 1.3,
                         "w": 1280, "h": 720, "temps": 2.0, "sortie": "gold.png"}]}]}
```

- `vue: "orbite"` : l'arme posée, la caméra autour (`yaw`, `pitch`, `zoom`) ;
- `vue: "fps"` : l'arme tenue en vue du joueur, qui regarde vers `envYaw` / `envPitch` (degrés) : faire varier `envYaw`
  de 0 à 360 donne l'animation « le joueur se retourne » (les reflets liés au monde glissent sur l'arme) ;
- `vue: "icone"` : icône d'inventaire (profil droit penché de `roule` degrés, -34 par défaut, sans perspective, cadrée
  sur l'arme, fond transparent ; `camo: 0` = sans camo) : rendre en 512 px puis réduire (moyenne) pour une icône lisse ;
- `temps` : `CamoTime` (camos animés) ; `relief: true` : affiche la carte de relief tirée de la texture ;
- `"ancien": true` sur une passe : `main` d'avant la refonte 2026-10 (comparaisons avant / après avec l'ancien shader).

Pour une animation : une image par pas, puis `ffmpeg -framerate 12 -i %03d.png ... anim.gif`.

## 3. Vérifier que les shaders compilent

```
pip install moderngl
xvfb-run -a python3 verif_glsl.py
```

Compile les 62 programmes avec un vrai compilateur OpenGL de bureau (Mesa) en `#version 150`, comme le jeu.
À lancer après chaque `python3 ../generer_camos.py`.
