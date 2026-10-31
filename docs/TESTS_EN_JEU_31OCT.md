# Essais en jeu du 31/10 (rien testé par Claude)

Module `armes` (HeraliZ Tactique 2.40.0). Les joueurs doivent avoir le nouveau jar ; redémarrer le serveur.

## Soins plus rapides
Durées écrites par `python modules/armes/outils/soins_durees.py` (tableau `DUREES`, `--controle` : ce qui changerait).

| Soin | Avant | Après |
|---|---|---|
| Ibuprofène, vaseline, Goldenstar, amoxicilline | 2,5 à 7,25 s | 1,5 s |
| AI-2, Carfak, bandage | 3,5 à 6 s | 2,5 s |
| Poche de sang | 4,65 s | 3 s |
| Attelle, attelle et bandage | 3,5 s | 3,5 s (inchangé) |
| Surv12, CMS | 8 s, 13 s | 3,5 s |
| Lait concentré | 5,45 s | 1,6 s |

Sortie de l'objet en main : 0,5 s au plus. Animations et sons resserrés à la même durée.

À vérifier :
- chaque soin va au bout de son animation, sans geste coupé ni sons qui se chevauchent trop (surtout CMS et Surv12) ;
- le rond de soin autour du viseur suit la nouvelle durée ;
- l'effet du soin (vie, saignement, jambe cassée) arrive bien à la fin.

## F3+B seulement en créatif et en spectateur
En survie et en aventure : F3+B ne fait rien, sans message (le menu F3 ne s'ouvre pas non plus en relâchant F3), et
aucune boîte de collision ne s'affiche, même si elles étaient déjà allumées avant de passer en survie.

À vérifier :
- en survie : F3+B ne montre rien ; F3 seul ouvre toujours le menu de débogage ;
- en créatif ou en spectateur : F3+B marche comme avant (hitbox « en morceaux » des joueurs) ;
- boîtes allumées en créatif, puis passage en survie : elles disparaissent.

## Conteneurs de loot allégés (HeraliZ Loot 0.14.0)
Module `loot` (jar joueur seulement : le serveur ne change pas). Les conteneurs T1 à T6 posés dans le monde sont dessinés
avec moins de faces (version « C ») ; dans l'inventaire et en main, ils restent complets. Les coffres T7 ne changent pas.

| | Faces (246 modèles) |
|---|---|
| Avant | 231 587 |
| Faces cachées retirées (sans perte) | 210 539 (−9 %) |
| + simplification « C » | 189 099 (−18 %) |

Plus 6 045 faces du dessous que Minecraft ne dessine pas quand le bloc d'en dessous est plein (presque toujours).
Moins que les −50 % des rendus du 24/10 : la simplification d'alors cassait, vus de près, les anneaux, les rebords et les
tiges ; elle est maintenant arrêtée partout où elle abîmerait le modèle (rien de percé, retourné, tordu, aplati, collé ni
effacé). Ce qui reste visible de très près : cylindres et arrondis un peu moins ronds (bords des trous de la caisse à
grenades, tiges du conteneur sécurisé), arêtes biseautées devenues vives.

Interrupteur du joueur : `conteneurs_alleges = false` dans `config/heraliz_loot-client.toml` (section `[rendu]`), puis
F3 + T : modèles complets.

À vérifier :
- dans une ville pleine de conteneurs : images par seconde avant / après (même endroit, même distance de vue) ;
- de près (1 à 2 blocs), sur une dizaine de conteneurs différents : pas de trou, pas de face qui clignote, pas de pièce
  qui flotte ou qui disparaît (poignées, anneaux, étiquettes, vis) ; couleurs et textures comme avant ;
- tourner autour, regarder aussi par-dessous un conteneur posé sur une dalle, une vitre ou au bord d'un vide : le
  dessous doit être là ;
- conteneur posé contre un mur ou dans un coin : rien ne manque ;
- inventaire et main : modèle complet, inchangé ;
- avec `conteneurs_alleges = false` + F3 + T : retour aux modèles complets.
