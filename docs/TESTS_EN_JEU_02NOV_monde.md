# Essais en jeu du 02/11 : chantier « monde » (audit du 02/11 : loot, bunker, monde, événements)

Rien de tout cela n'a été testé en jeu ni compilé ici : Gradle ne trouve pas Forge sur la machine de travail
(maven.minecraftforge.net bloqué). Seule la syntaxe Java a été contrôlée (javac, sans Minecraft) ; la vraie compilation
sera faite par le contrôle de branche de GitHub. Les tables de loot ont été réécrites par leurs outils
(`outils/loot_armes.py`, `outils/loot_zombies.py`), relancés deux fois sans écart ; `outils/verifier_langues.py` : 0 erreur.

Jars à mettre à jour ensemble (serveur ET joueurs) : **Monde 1.0.10**, **Loot 0.14.6**, **Bunker 0.13.5**,
**Événements 1.1.3**. Réglages du dépôt changés : `serveur/defaultconfigs/heraliz_vehicules-server.toml`,
`heraliz_decors-server.toml`, `heraliz_zombies-server.toml`, `heraliz_usure-server.toml`,
`serveur/config/heraliz_evenements/largage.json`, tables `serveur/config/heraliz_loot/coffres/t1…t7.json`,
`zombies.json`, `zombies_militaire.json`.

## 1. Largages et hélicoptère après un ClashWar (n° 3)

- [ ] Laisser un ClashWar se terminer (ou en annuler un), puis `/event lancer largage avec_points` : le largage part
      (avant : « pas lancé (ClashWar en cours) » jusqu'au redémarrage). Journal `heraliz_evenements/journal.log`.
- [ ] Pendant les inscriptions ou les matchs d'un vrai ClashWar : `/event lancer largage` est toujours refusé.

## 2. Tirage du point de largage (n° 16)

- [ ] `/event lancer largage` : l'annonce arrive quelques secondes après (pas tout de suite), avec des coordonnées sur la
      carte construite. Aucun à-coup du serveur au lancement (`/diag` : pas de rapport de blocage).
- [ ] Journal : « recherche du point en tache de fond », puis « lance par … en x y z ». Si aucun point : la ligne donne le
      nombre de refus par raison (hors de la carte, zone VIP, base, tronçon, sol).
- [ ] Avec une zone VIP sur la carte (`heraliz_grades/zones_vip.json`) : aucun largage n'y tombe (en lancer plusieurs).
- [ ] Après plusieurs largages, aucun terrain neuf autour de la carte construite (vue de la carte M, bords du monde).

## 3. Portes du bunker (n° 17)

- [ ] Ouvrir et fermer plusieurs portes HBM, aller-retour carte ↔ spawn une dizaine de fois : la mémoire du jeu (F3)
      redescend après chaque nettoyage, pas de montée continue.
- [ ] Une porte qui finit de bouger s'affiche bien fermée ou ouverte (pas de porte invisible ou en double), choix du skin
      d'une porte : le modèle change tout de suite.

## 4. Conteneurs de loot : rien ne s'y dépose (n° 18, n° 8)

- [ ] Ouvrir un conteneur T1 à T6 : on prend tout normalement (clic, Maj + clic, double clic).
- [ ] Essayer d'y poser un objet (clic, glisser, Maj + clic depuis l'inventaire, touches 1 à 9) : l'objet revient à sa
      place, rien n'entre dans le conteneur.
- [ ] Même chose dans un conteneur d'une salle à carte.

## 5. Zone d'extraction au nom trop long (n° 19)

- [ ] `/extraction create` avec un nom de 33 caractères ou plus : refusé (« Nom trop long : 32 caractères au plus. ») en
      français, anglais et russe.

## 6. Économie du loot (n° 24)

- [ ] Sacs trouvés : beaucoup moins de sacs Épiques et Légendaires qu'avant (T6 : environ un sac Épique pour 150
      conteneurs au lieu de 60), plus aucun sac Commun.
- [ ] Zombies : un composant Épique (niveau III) sort toujours par 1 ; niveau II inchangé.
- [ ] Un conteneur T5 ou T6 posé seul (loin des autres de son palier) : vidé, il ne se remplit plus à chaque passage
      (environ une fois sur 7 à 9, après 4 à 6 min d'attente chacune). `/heralizloot zones` pour voir sa zone.

## 7. Planques (n° 25)

- [ ] Ouvrir et fermer sa planque et son coffre VIP, déposer et prendre : tout est gardé après un redémarrage.
- [ ] Beaucoup de joueurs qui ferment leur planque en même temps au spawn : pas d'à-coup.

## 8. Largage jamais visité (§10)

- [ ] `/event lancer largage`, ne pas y aller : 20 min après l'atterrissage, la caisse a disparu et ne fume plus (avant :
      elle fumait pour toujours). Arriver 30 min plus tard : plus de caisse.
- [ ] Aller à la caisse dans les 20 min : elle est là, elle donne ses points (si lancée avec points).
- [ ] Redémarrer le serveur pendant qu'une caisse est au sol : elle garde son temps restant.

## 9. Zombies du peuplement (§11)

- [ ] Courir dans une ville (zombies autour), se téléporter loin (`/spawn` ou mort), revenir : pas d'ancienne horde en
      plus des nouveaux zombies, le nombre est normal.

## 10. Véhicules (§12)

- [ ] Au spawn et dans le tutoriel des bases : poser un véhicule est refusé (« Les véhicules sont interdits ici :
      seulement sur la carte. »). Les véhicules posés en décor par le staff au spawn sont toujours là.
- [ ] Sur la carte : poser un véhicule marche comme avant.

## 11. Atelier de réparation (§13, n° 8)

- [ ] Poser une arme dans l'atelier, se déconnecter (ou Alt+F4) : au retour, l'arme est dans l'inventaire (avant : au sol).
- [ ] Poser une arme, mourir : l'arme est sur le corps avec le reste (une arme à vie : gardée comme les autres objets à vie).
- [ ] Poser une arme, inventaire plein, se déconnecter : au retour, message « Ton objet de l'atelier t'attend… », l'arme
      revient dès qu'une case se libère ; l'atelier ne s'ouvre pas tant qu'elle attend.
- [ ] Réparer, fermer avec Échap : l'arme réparée revient dans l'inventaire.

## 12. Largage ouvert par le staff en créatif (§14)

- [ ] Staff en créatif qui ouvre un largage public lancé avec points : aucun point à son clan, pas d'annonce « ouvert ».
      Un joueur en survie qui l'ouvre ensuite prend les points et trouve l'objet d'événement.

## 13. Borne de départ (§15)

- [ ] Départ normal : rien ne change.
- [ ] À la fin du compte à rebours, si « Départ imminent : ne bouge pas. » s'affiche : bouger annule le départ.
- [ ] Ouvrir l'inventaire ou sa planque juste au moment du départ : l'écran se ferme, on arrive sur la carte sans écran.

## 14. Fusée d'extraction (§16)

- [ ] Au spawn et dans le tutoriel : le terminal affiche « ERR: hors de la carte », la fusée n'est pas consommée.
- [ ] Sur la carte : comme avant.

## 15. Salles à carte (§18)

- [ ] Ouvrir une salle verte, bleue et rouge après sa recharge : les points de saison de la salle et la quête du jour
      « salle » avancent comme avant (journal de la saison).

## 16. Réglages et nettoyage (§17, §20, §21)

- [ ] `serverconfig/heraliz_decors-server.toml` : section `[protections]` ; masque à gaz, Hazmat et combinaison arctique
      protègent comme avant. Ajouter un objet à une liste : pris en compte sans redémarrer.
- [ ] `/suicide` : délai de 5 min comme avant (`[suicide]` de `heralisz_utils-server.toml`).
- [ ] Largage : l'avion arrive de 400 blocs comme avant (`distance_avion` de `largage.json`).
- [ ] Munitions dans les conteneurs : jamais plus de 40 par case (`[quantites] balles_max`).
- [ ] Plus de ligne `ia_rythme` dans `heraliz_zombies-server.toml` ; zombies comme avant.
- [ ] Corps des joueurs : comme avant (le convertisseur de l'ancien mod « corpse » est retiré).

## Questions pour le propriétaire

- Nourriture : celle des zombies est toujours bonne, celle des conteneurs une fois sur trois. Voulu ?
- Bonus de loot de la boutique : son temps s'use dès que le joueur est connecté, même au spawn ou AFK. Ne le décompter que
  sur la carte ?
- Composants de niveau II des zombies : toujours 1 à 7 d'un coup (seul le niveau III passe à 1). Les limiter aussi (1 ou 2) ?
  Avec le niveau III à 1, le rapport zombies / conteneurs passe de 0,43 à 0,40 (visé 0,5).
