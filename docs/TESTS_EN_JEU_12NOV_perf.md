# Vérification des performances du 11 et 12/11 (Noyau 1.0.19)

Demande du propriétaire : « le serveur est clean ? on a full opti ? … être environ 50 sur le serveur sans aucun bug ».
Rien n'a été essayé en jeu par Claude. Les mesures viennent du vrai serveur (lecture seule) et du banc de charge de
GitHub (`docs/GUIDE.md` §12 : autre machine, monde neuf, bots sans réseau).

## 1. Ce que disent les rapports du vrai serveur (07 au 11/11, 1 ou 2 joueurs)

- Aucun plantage depuis le 28/10 ; aucun « Can't keep up » ; ticks de 2 à 7 ms ; mémoire 3 à 6 Go sur 16 ; la dernière
  « fuite » signalée (05/11) est une fausse alerte (mémoire stable puis un joueur qui se connecte).
- 38 blocages d'un tick (seuil 100 ms), de 0,1 à 1,6 s :
  - environ 25 : tronçons lus par le fil du serveur après une téléportation du STAFF (`/spawn`, `/home`, `/tp` sans compte
    à rebours, vol en créatif). Pour un joueur normal, l'arrivée est préchargée pendant le compte à rebours (01/11) ;
  - environ 12 : premières fois après un redémarrage (première connexion, premier tir, premier chemin d'un zombie, première
    sauvegarde : classes chargées, code pas encore compilé par Java). Une fois par redémarrage ;
  - 1 : sauvegarde de `level.dat` 5 min après chaque démarrage (190 ms, la première seulement).
- Chaque changement de monde ou réapparition coûte 35 à 60 ms sur le fil du serveur (liste des commandes renvoyée au
  joueur, permissions des plugins) : petit à-coup, pas corrigé ici (moins de 0,5 % du temps au banc).

## 2. Banc de charge

| essai | version | bots | moyenne | 95 % | 99 % | pire | TPS | erreurs Java |
|---|---|---|---|---|---|---|---|---|
| 02/11 (référence) | `740bccff` | 100 | 39,3 ms | 48,1 | 65,6 | 171 | 20,0 | 0 |
| 11/11, en ligne | `166acc4d` | 100 | 37,8 ms | 47,6 | 72,3 | 234 | 19,99 | 1 (AuthMe, sans effet) |
| 11/11, en ligne | `166acc4d` | 50 | 16,7 ms | 23,0 | 31,9 | 147 | 20,0 | 0 |
| 12/11, branche, relevés de hauteur seuls | `9befd318` | 100 | 25,7 ms | 34,2 | 52,2 | 128 | 20,0 | 0 |
| 12/11, branche, les trois changements | `cd948c2e` | 100 | 20,8 ms | 28,4 | 44,8 | 187 | 20,0 | 0 |

**Attention à la lecture** : les machines de GitHub ne vont pas toutes à la même vitesse. Les deux essais de la branche ont
tourné sur des machines environ 1,5 fois plus rapides que l'essai « en ligne » à 100 bots (coût propre du banc : 0,35 et
0,37 ms par tick contre 0,55). Les ms ne se comparent donc qu'entre les deux essais de la branche (25,7 → 20,8 ms, −19 %),
et par les PARTS du profil : `ChunkMap.processUnloads` pèse 21 à 26 % du tick avant, 7 % après (dont les vraies écritures).

Conclusions :
- les changements du 02 au 11/11 (retours du propriétaire, anticheat, saison, bases…) n'ont pas ralenti le serveur ;
- à 50 bots, les trois objectifs du projet sont tenus sur la version en ligne (moyenne ≤ 30 ms, 95 % ≤ 40, 99 % < 50) ;
- avec les trois changements, ils le sont aussi à 100 bots sur une machine rapide.

## 3. Ce qui change (Noyau 1.0.19, serveur seulement, chacun se coupe dans `[perf]` de `heraliz_noyau-server.toml`)

1. **`sauvegardes_etalees`** : à chaque tick, Minecraft passait en revue TOUS les tronçons chargés pour écrire ceux qui ont
   changé (8,1 ms par tick à 100 bots, 3,5 ms à 50). Maintenant un vingtième par tick, en reprenant où le tick d'avant
   s'est arrêté : chaque tronçon est regardé une fois par seconde. Mêmes écritures (20 par tick au plus, rien quand le
   serveur n'a plus de temps) ; sauvegarde automatique, déchargements et arrêt inchangés.
2. **`melange_si_apparitions`** : le mélange au hasard de la liste des tronçons qui tournent (1,1 ms par tick à 100 bots)
   n'est plus fait quand `doMobSpawning` est à faux (aucune apparition naturelle : l'ordre ne sert à rien).
3. **`hauteurs_sans_balayage`** : à la lecture d'un tronçon, plus de parcours des 256 colonnes quand aucun relevé de
   hauteur ne manque (10 à 25 % du temps de lecture d'un tronçon dans les rapports du vrai serveur).
4. **Rapports de blocage** : nouvelle section « Tronçons attendus par le fil du serveur : qui les a demandés ».

Le premier banc de la branche a PLANTÉ (point 1 : monde sans aucun tronçon chargé) ; corrigé, essai hors du jeu refait plus
dur (cartes vidées et remplies, 40 graines × 6000 ticks), second banc sans erreur. Rien de cela n'a été en ligne.

## 4. À vérifier sur le vrai serveur après la mise en ligne

- [ ] `/diag etat` : la ligne « Minecraft allégé, étape 2 » se termine par « relevés de hauteur : N sans parcours, M
      refaits ; sauvegardes étalées : N tranches (… tronçons regardés par tranche) ; mélanges évités N ». Les trois
      nombres montent (« mélanges évités » reste à 0 si `doMobSpawning` est à vrai : me le dire) ;
- [ ] si « refaits » monte beaucoup plus vite que « sans parcours » sur la vraie carte : les tronçons de la carte n'ont pas
      leurs relevés de hauteur enregistrés (carte construite avec un outil) ; me le dire, il y a un gain de plus à prendre ;
- [ ] poser un bloc dans une base, attendre 10 s, redémarrer : le bloc est toujours là (comme avant) ;
- [ ] aucun nouveau message d'erreur au démarrage (journal : chercher « heraliz_noyau_allege ») ;
- [ ] en cas de doute sur un des trois : le passer à `false` dans `[perf]` (relu sans redémarrer) et me le dire.

## 5. Ce qui n'est PAS mesuré

- **Les images par seconde chez les joueurs** : jamais mesurées par un outil dans ce projet. À faire pendant une vraie
  session à plusieurs : `/diag client <pseudo> 60` (ou `/diagclient 60` par le joueur), puis « Lenteurs du serveur »
  (Actions) rapatrie le rapport `client-<pseudo>-<date>.txt` (images lentes, mod responsable, temps par type d'entité).
- **La vraie carte et la vraie machine** : le banc tourne sur un monde neuf et une machine de GitHub. À faire avec 15 à
  30 vrais joueurs : « Lenteurs du serveur » avec la case « profil » (2 min), ou `/diag profil 120` en jeu.
- **Le réseau** (bots sans connexion), l'anticheat avec de vrais jeux, les pics de connexion juste après un redémarrage
  (attendre quelques minutes après un redémarrage avant d'ouvrir à beaucoup de joueurs).
