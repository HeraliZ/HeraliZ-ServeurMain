# Essais en jeu — chiffres des dégâts du 08/11 (Équipement 1.2.0)

Rien n'a été essayé en jeu par Claude.

## 1. Cible d'essai : les chiffres ne s'affichaient jamais

Cause trouvée dans le journal du serveur (lignes `[cible]`) : chaque coup arrivait bien à la cible, mais était noté
« aucun dégât mesuré » (tirs ET couteau), donc aucun chiffre n'était envoyé au tireur. Le chemin des dégâts de Minecraft
version serveur hybride (Mohist) rejetait le coup avant les évènements qui mesurent les dégâts. La cible refait
maintenant elle-même ce que fait le jeu pour un joueur (évènement d'attaque, 0,5 s d'invincibilité comme un joueur,
dégâts → armures de l'Armurerie → armure et effets de Minecraft → absorption) sans passer par ce chemin. Elle ne perd
toujours jamais de vie. Chaque refus a maintenant sa propre ligne dans le journal (`attaque annulée par une règle du
serveur`, `temps d'invincibilité`).

À vérifier :
- [ ] `/give @s heraliz_armory:cible_essai`, la poser, tirer dessus : un chiffre blanc (doré à la tête) au-dessus de
      sa tête, qui monte et s'efface en 1 s ; la ligne détaillée au-dessus de la barre ; le récapitulatif dans le chat
      après 4 s sans tir ;
- [ ] au couteau / à la mêlée : un chiffre à chaque coup ;
- [ ] fusil à pompe : un seul chiffre par tir (plombs additionnés) ;
- [ ] avec une armure choisie (clic droit main vide) : chiffre plus petit qu'avec « sans armure » ;
- [ ] si rien ne s'affiche encore : lancer « Journaux du serveur » avec le filtre `[cible]` et me donner la ligne.

## 2. Nouveau : petits chiffres quand on touche un joueur

Quand un joueur en touche un autre (balle, couteau, mêlée, explosion), il voit — lui seul — la vie que l'autre a
VRAIMENT perdue (après armures et protections), en petit (70 % de la taille de la cible) juste au-dessus de sa tête ;
doré pour un tir à la tête ; un seul chiffre par tir de fusil à pompe. Décidé par le serveur : un coup refusé (spawn,
protection, joueur à terre…) n'affiche rien. Rien sur un joueur spectateur ou invisible (vanish). Réglage du serveur :
`[chiffres] joueurs = true` dans `config/heraliz_armory-common.toml` (false = coupé). Pas de réglage côté joueur.

À vérifier (à deux) :
- [ ] A tire sur B : A voit le chiffre, B ne voit rien, un troisième joueur ne voit rien ;
- [ ] tir à la tête : chiffre doré ;
- [ ] au spawn (dégâts coupés) : aucun chiffre ;
- [ ] FPS normaux pendant une fusillade (au plus 16 chiffres à la fois, 1 s chacun).

## Non vérifié par Claude

- Pas compilé ici (Minecraft et Forge ne se téléchargent pas dans cet environnement) : la fabrication complète est
  faite par le contrôle de branche de GitHub. Code relu.
- Protocole du canal de l'Armurerie passé de 5 à 6 : un jeu pas à jour est refusé à la connexion ; le launcher met à
  jour les joueurs tout seul.
