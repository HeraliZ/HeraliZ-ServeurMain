# Vitrine des skins (vidéo)

Vidéo de 30 s demandée par le propriétaire le 21/11 pour TikTok et Discord : 9 armes qui tournent comme à l'inspection,
avec les skins Diamant, Dark Matter, Orion et Chrome liquide, sur un coucher de soleil vu d'un toit, puis le logo,
`play.heraliz.fr` et `discord.gg/heraliz`.

**Ce n'est pas une capture du jeu** : les armes sont dessinées par l'outil d'aperçu des camos
(`modules/armes/outils/apercu_camos`), avec les mêmes modèles, textures et code des skins que le jeu, mais une lumière et
un décor à part (ni mains, ni animation d'inspection, ni accessoires). La musique est synthétisée par `musique.py`
(92 BPM, une arme par mesure) : aucun échantillon, aucun droit d'auteur.

```
python3 outils/video_vitrine/video.py tout        # tout refaire (environ 45 min)
python3 outils/video_vitrine/video.py image 330   # une image de contrôle (16:9 et 9:16)
```

Étapes séparées : `preparer`, `rendre`, `composer [16_9] [9_16]`, `finir`. Tout est écrit dans `build/video_vitrine/`
(hors de Git) ; les vidéos dans `build/video_vitrine/sortie/` :

| Fichier | Pour |
|---|---|
| `heraliz_skins_tiktok.mp4` | TikTok (1080 x 1920, avec musique) |
| `heraliz_skins_tiktok_sans_musique.mp4` | TikTok avec un son de l'application |
| `heraliz_skins_discord.mp4` | Discord (1920 x 1080) |
| `heraliz_skins_discord_moins_de_10Mo.mp4` | Discord sans Nitro (10 Mo au plus) |
| `couverture_tiktok.jpg` | image de couverture |

Changer d'armes ou de skins : tableau `SKINS` de `video.py` (identifiant TaCZ de l'arme, nom affiché, zoom ; programme
du skin = `shaderId` de `modules/armes/src/commun/java/fr/heralisz/camo/Camo.java`). Les armes sont cadrées d'après leur
vraie place à l'écran (`boites.json`, à effacer après un changement d'arme). En 9:16, rien d'important dans le haut ni
dans le bas de l'image (onglets, légende et boutons de TikTok).

Besoin : numpy, Pillow, Node.js et Playwright (son Chromium, rendu logiciel), ffmpeg avec libx264.
