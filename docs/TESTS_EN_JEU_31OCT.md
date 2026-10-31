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
