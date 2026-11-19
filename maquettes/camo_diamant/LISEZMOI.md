# Camo Diamant refait (19/11) : en attente de validation du propriétaire

Retour du propriétaire (18/11) : le Diamant a des endroits « plats » qui font du bruit sur le skin ; la première reprise
(grosses pierres, fond argenté lisse) a été jugée moins bonne que l'ancien. Cette version repart de l'ancien (pierres
petites et serrées, vrai relief 3D, facettes et éclats) et ne corrige que les endroits bizarres :

- faces abîmées du modèle (zone de texture écrasée en un point, partagée, ou plus fine qu'un point) : mêmes pierres 3D
  sur une grille de secours prise dans la face elle-même (plus de crosse rayée sur le XM177, plus d'argent plat) ;
- faces à 45° : le plan de projection ne saute plus d'un point à l'autre (fin du moucheté) ;
- moins de scintillement : une pierre trop petite à l'écran devient une pierre bombée (relief, reflet et éclat gardés),
  éclats au moins d'un point, relief limité en vue rasante (xm177 4,6 % → 2,9 % de points qui sautent sur 2°, m249
  5,2 % → 1,4 %) ;
- même coût qu'avant (ou un peu moins), aucune lecture de texture en plus ; l'or ne change pas.

Rendus envoyés au propriétaire le 19/11 (ancien / nouveau, vue joueur, présentoir, de loin). Pas testé en jeu.

Le changement n'est pas encore dans le mod : il est gardé ici, `diamant_19nov.patch` (camo_gabarit.fsh et
heralisz_camo.glsl du module armes, écrit depuis le gabarit). Une fois validé :

    git apply maquettes/camo_diamant/diamant_19nov.patch

puis version du module armes, `python outils/construire.py armes` et `python outils/verifier.py armes`, et retirer ce
dossier.
