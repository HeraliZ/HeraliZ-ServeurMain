# Skins mythiques « premium animés » : Faille et Accès Rouge (rendus à valider, 04/11)

Demande du propriétaire (04/11) : essayer 2 skins haut de gamme avec un **rechargement propre au skin**. Designs
**originaux HeraliZ** (aucun fichier, nom, motif ni forme repris d'un autre jeu). Rien n'est codé dans le mod tant que
ces rendus ne sont pas validés ; si le résultat en jeu ne plaît pas, on les retire et seuls Séraphin et Chaînes de
l'Enfer restent mythiques.

| Fichier | Contenu |
|---|---|
| `planche_faille.png`, `planche_acces.png` | finition sur 4 armes (AK-47, XM177, Vector, Glock), de trois quarts, en main ; rechargement en 6 étapes |
| `rechargement_faille.gif`, `rechargement_acces.gif` | le rechargement en mouvement (2,4 s) |
| `prototype/` | code d'essai des rendus (pas dans le mod) |

## Les deux skins

- **Faille** (anomalies de la Zone) : verre volcanique noir poli, grandes fractures lumineuses (bord magenta, lumière
  cyan, fil blanc), espace de l'anomalie visible au fond. Rechargement : une faille s'ouvre sous l'arme et avale l'ancien
  chargeur (éclats de lumière), recrache le nouveau chauffé à blanc, qui se refroidit en obsidienne ; la faille se
  referme au clic.
- **Accès Rouge** (laboratoires des bunkers, carte rouge) : panneaux de blindage en angles vifs (céramique noire,
  graphite à micro-hexagones, panneaux rouges au pochoir), lumière rouge de sécurité dans les joints, balayage. Rechargement :
  l'ancien chargeur se coupe comme un hologramme et part en cubes ; le nouveau s'imprime (grille rouge, ligne de balayage
  de bas en haut) ; au clic, onde dans les joints qui passent au vert une demi-seconde (« accès autorisé »).

## Ce qui serait fait en jeu après validation (module `armes`)

- Deux camos mythiques (`Camo` 210 et 211, objets `heralisz_camo:skinx_faille` et `skinx_acces_rouge`, textes fr / en /
  ru), finitions écrites dans `outils/camo_gabarit.fsh` (fonctions du prototype).
- Phase du rechargement lue chez chaque joueur dans TaCZ (`IGunOperator.getSynReloadState()` : type et compte à rebours,
  temps de rechargement de la fiche de l'arme), passée au dessin du skin (uniforme `CamoReload`) ; hauteur du chargeur
  tirée de la carte de positions (comme `CamoAnchor`).
- Effets autour du chargeur (faille, éclats, cubes, cadre d'hologramme, éclair) en 3D par le système d'effets des skins
  (`fx/`, comme les ailes de Séraphin), sur le chargeur repéré par la carte de positions : marche sur toutes les armes.
- Limite : le geste des mains et le trajet du chargeur restent ceux de l'animation de chaque arme (TaCZ) ; le skin change
  ce que devient le chargeur et ce qui se passe autour, pas le geste.
- À vérifier en jeu : que la phase du rechargement arrive aussi pour les autres joueurs (3e personne), et le coût en
  images par seconde avec plusieurs joueurs qui rechargent.

## Refaire les images

Prototype d'aperçu (copie de `modules/armes/outils/apercu_camos`, Chrome sans écran par Playwright) :
`prototype/monter.py <dossier> <dépôt>` (code commun du mod + prototype, copie de l'aperçu avec chargeur déplacé),
`prototype/rechargement.py <dossier> travaux` puis `node rendre.js recharge.json`, puis `rechargement.py <dossier> effets`
et `planche.py <dossier> <dépôt> <sortie>`. Armes préparées par `apercu_camos/prep.py` (`--sans-adaptateurs` pour le Glock
et le Vector).

Rendus faits hors du jeu (vrai modèle des armes, vrai code de dessin d'essai, effets autour du chargeur posés en 2D) :
**rien n'est testé en jeu**.

## AK-47 Faille : modèle 3D propre au skin (premier jet, à valider, 05/11)

Demande du propriétaire (« comme sur Valorant, un vrai modèle ») : avec le skin Faille, l'AK-47 change de forme.
`ak_faille/planche_ak_faille.png` (avant / après, trois quarts, en main) ; modèle écrit par
`modules/armes/outils/ak47_faille.py` (depuis le 05/11, `--installer` : dans le gunpack) depuis l'AK de `serveur/tacz/heraliz_1_base_tacz`
(même squelette : animations, rechargement, accessoires ; pièces lumineuses dans des os « *_illuminated »).
En jeu (après validation) : TaCZ sait déjà donner à une arme un autre affichage (`IGun.setGunDisplayId`) ; le modèle irait
dans le gunpack `heralisz_skins` comme nouvel affichage, posé sur l'AK quand le skin Faille y est appliqué et retiré avec lui.
2e jet (05/11, demande du propriétaire : chargeur transparent, plus propre et plus fin) : chargeur à fenêtre (montants, liseré
lumineux, balles en laiton visibles, qui partent et reviennent avec lui) ; les balles portent une marque de texture (alpha 200)
que le skin laisse en métal : `ak_faille/essai_laiton.py` (retouche d'essai du code commun des camos, reportée dans
`outils/camo_gabarit.fsh` : `BASE_A`, `marqueLaiton`, `laitonPoli`).
**Validé et mis en jeu le 05/11 (Tactique 2.53.0)** : affichage `heralisz_skins:ak47_faille_display` du gunpack
`serveur/tacz/heralisz_skins`, montré par le jeu du joueur sur tout AK-47 qui porte le skin Faille (`camo/client/ModelesSkins`,
branché sur `TimelessAPI.getGunDisplay` ; rien n'est enregistré sur l'arme).
