# Essais en jeu — événements et monde, retours du 11/11 (Événements 1.2.0, Monde 1.2.0, Loot 0.16.1)

Rien n'a été essayé en jeu par Claude. Jars serveur ET joueurs à mettre à jour (le jeu des joueurs doit connaître les
boîtes de collision de l'épave, sinon ils « rebondissent » dessus).

Pour lancer un hélicoptère d'essai qui donne une vraie boîte noire : `/event helico lancer ici avec_points` (op 4 ; sans
`avec_points`, ou touché en créatif, la boîte noire est « essai »). Pour l'abattre vite pendant les essais : baisser
`"pv"` dans `config/heraliz_evenements/helico.json` puis `/recharger evenements` (remettre 3600 après !).

## 1. Hélicoptère : 6 fois plus de PV, coffre dès le crash, collision de l'épave

Ce qui change :
- **3 600 PV au lieu de 600** (`"pv"` de `config/heraliz_evenements/helico.json`, relu par `/recharger evenements`) ; il
  regagne toujours 1,5 % de ses PV par seconde après 30 s sans coup (donc 54 PV par seconde au lieu de 9).
- **Coffre posé dès le crash**, à 5 à 8 blocs de l'épave (jamais dans la carcasse). Tant que le feu brûle (2 min à
  3 min 30), clic droit = refus : « Coffre encore dans les flammes : il s'ouvrira quand le feu sera éteint (encore
  1 min 20 s). » au-dessus de la barre, avec le bruit d'un coffre verrouillé. À la fin du feu, il s'ouvre comme avant
  (boîte noire dedans, contenu tiré à la première ouverture) et reste 15 min. Rien ne sort du coffre avant : pas
  d'entonnoir possible, il est incassable, et la carte ne laisse casser aucun bloc.
- Staff qui annule (`/event annuler helico`) ou redémarrage pendant le feu : le coffre encore fermé disparaît, rien n'est
  donné (comme avant). Après le feu, il reste.
- **Collision de l'épave** : on ne traverse plus la cabine, le bout de la poutre ni la queue arrachée couchée à côté
  (les pales tordues, fines, n'en ont pas). Les balles s'arrêtent sur l'épave avec des étincelles : on peut s'abriter
  derrière.
- Infobulle de l'épave sur la carte M : « coffre fermé tant que le feu brûle, boîte noire dedans ».

À vérifier :
- [ ] Abattre l'hélicoptère : il encaisse nettement plus (environ 6 fois plus de chargeurs qu'avant) ; `/event helico
      info` affiche « … / 3600 PV ».
- [ ] Au crash : le coffre apparaît tout de suite près de l'épave en feu (pas dans la carcasse, pas sous la queue).
- [ ] Clic droit sur le coffre pendant le feu (sur ses deux moitiés) : refus, message avec le temps qui reste, bruit
      de coffre fermé ; le temps affiché baisse. Accroupi avec une arme en main : pareil.
- [ ] À la fin du feu (les flammes s'arrêtent) : le coffre s'ouvre, la boîte noire est dedans, une seule fois ; il
      disparaît 15 min plus tard (ou 30 s après avoir été vidé).
- [ ] Journal `heraliz_evenements/journal.log` : « coffre … pose en …, ferme pendant le feu (encore N s) », puis « feu
      eteint, le coffre en … s'ouvre ».
- [ ] `/event annuler helico` pendant le feu : épave, feu et coffre disparaissent ; journal « coffre encore ferme
      retire ». Même essai en arrêtant le serveur pendant le feu : au redémarrage, pas de coffre.
- [ ] Marcher contre l'épave sous plusieurs angles (nez, flancs, queue couchée) : on est arrêté, sans saccade ni retour en
      arrière ; en créatif, F3+B montre les boîtes de l'épave.
- [ ] Tirer sur l'épave : étincelles et claquement, un joueur caché derrière n'est pas touché ; une grenade lancée dessus
      rebondit ou s'arrête.
- [ ] Zombies autour de l'épave : ils la contournent ou se bloquent contre, sans passer au travers.
- [ ] `/diag mesure` pendant que l'épave est là : rien d'anormal (8 boîtes, seulement tant que l'épave existe).

## Non vérifié par Claude

- Pas compilé ici (Minecraft et Forge ne se téléchargent pas dans cet environnement) : la fabrication complète est
  faite par le contrôle de branche de GitHub. Code relu ; textes contrôlés (`outils/verifier_langues.py` : 0 erreur).
- Boîtes de collision relevées sur le modèle de l'épave par un script (vue de dessus et de côté contrôlées en image),
  pas vues en jeu : leur placement exact se voit avec F3+B.
