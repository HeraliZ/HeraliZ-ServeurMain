# Essais en jeu du 31/10 : chantier « monde » (frappes, largages, extractions)

Rien de tout cela n'a été testé en jeu ni compilé ici (Forge et Minecraft ne se téléchargent pas sur la machine de
travail) : la fabrication complète sera faite par le contrôle de branche de GitHub. Jars à mettre à jour : **Monde
1.0.4** (serveur ET joueurs) et **Interface 2.0.2** (joueurs). Réglages : `serveur/config/heraliz_extraction-common.toml`.

## Frappes aériennes et largages (avion, bombes, caisse)

Le mieux : deux joueurs, l'un qui appelle la frappe, l'autre qui regarde de 100 à 300 blocs. Si possible pendant que le
serveur est chargé (beaucoup de joueurs, ou juste après une sauvegarde).

- [ ] Frappe de l'avion de chasse (bombes, bombe guidée, mitraillage) : l'avion avance d'un mouvement continu, sans recul
      ni bond une fois par seconde ; les bombes partent des ailes et suivent une courbe régulière jusqu'au sol.
- [ ] L'explosion arrive quand la bombe touche le sol (ni avant, ni une seconde après).
- [ ] Largage (jumelles avec munition de largage, et `/event lancer largage`) : l'avion cargo avance sans à-coups, la
      caisse glisse de la soute puis tombe sous son parachute **sans sautiller**, et se pose au même endroit chez les deux
      joueurs.
- [ ] Un joueur qui arrive à moins de 700 blocs pendant le vol (il approche ou se téléporte) voit l'avion dans la
      seconde (avant : rien).
- [ ] Un joueur qui change de monde pendant une frappe (spawn, tutoriel) ne voit plus l'avion dans l'autre monde.
- [ ] Une frappe vue, puis le joueur meurt et réapparaît au spawn : plus d'avion au spawn.
- [ ] Le son du moteur suit l'avion, le sifflement suit la bombe.

## Extractions : 6 zones, changées toutes les 30 minutes

- [ ] Au redémarrage : la vague en cours est complétée à 6 zones jusqu'à sa fin (annonce des 2 ou 3 nouvelles), puis les
      vagues suivantes ouvrent 6 zones pour 30 min. `/extraction rotation` le montre.
- [ ] Notification en haut à gauche : « Extraction 3 · 7 · 12 · 18 · 22 · 31 — pendant 30 min », en entier, en français,
      anglais et russe.
- [ ] Carte M : les 6 extractions ouvertes ; elles changent à la vague suivante.
- [ ] Menu Échap : les 3 extractions les plus proches du joueur, titre « Extractions ouvertes · 6 ».
- [ ] `/extractions` : les 6 zones avec leur direction et leur distance.
- [ ] Aucune zone de la vague précédente ne revient à la vague suivante ; les 6 zones sont loin les unes des autres
      (800 blocs au moins). Journal du serveur : pas de ligne « distance minimale ramenée » avec la carte actuelle.
- [ ] Rappel « ferme dans 2 min » aux joueurs proches, 2 minutes avant la fin de chaque vague.

## Nouvelle carte

Rien à tester aujourd'hui : `docs/NOUVELLE_CARTE.md` est une étude (aucun fichier du monde touché).
