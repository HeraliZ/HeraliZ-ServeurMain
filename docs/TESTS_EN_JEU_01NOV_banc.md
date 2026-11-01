# Essais du 01/11 — chantier « banc » (banc de charge sur une machine de GitHub)

Demande du propriétaire : mesurer le serveur avec beaucoup de joueurs **sans toucher au Ptero**.
Fait : workflow « Banc de charge » (`.github/workflows/banc.yml`), outil `outils/banc_charge.py`, module `banc` 1.1.0
(`/bots mesure`, `/bots commandes`, bots qui vivent dans le monde où on les envoie). Mode d'emploi : `docs/GUIDE.md` §12.

**Rien de ceci n'a tourné sur une machine de GitHub, ni avec un vrai Mohist, ni compilé avec Gradle** (ici : pas d'accès à
Forge ni à Mojang). Vérifié ici : syntaxe Java du module banc, syntaxe du workflow (actionlint), préparation du serveur
d'essai (`preparer`, sur les jars des autres auteurs seulement), et tout le déroulé de `lancer` contre un faux serveur qui
imite le journal de Mohist (démarrage, échec avant « Done » puis nouvel essai, annulation en cours de route, rapport).

## Lancer le premier banc

1. Pousser la branche (le propriétaire) : le contrôle de branche fabrique `banc` 1.1.0 ; si `banc.yml` est dans l'envoi, le
   banc part tout seul avec les réglages par défaut (100 bots, 4 min de chauffe, 5 min de mesure, monde normal).
2. Après le passage sur `main` : onglet Actions → « Banc de charge » → « Run workflow », choisir la branche et les réglages.
3. Durée attendue : 25 à 35 min (le premier banc un peu plus : Mohist télécharge ses bibliothèques, gardées ensuite).

## À vérifier au premier banc (résumé de la tâche et journal)

1. **Fabrication** : `banc` 1.1.0 compile (nouvelles classes `Mesure` et `Commandes`). Sinon, l'erreur est dans le job
   « fabriquer » : me la donner.
2. **Démarrage** : la ligne « serveur démarré en … s » apparaît (moins de 20 min). Sinon le résumé dit « Done jamais vu » et
   montre la fin du journal : me le donner (bibliothèques de Mohist, mod qui plante dans un monde neuf…).
3. **Bots** : lignes « chauffe : 100 bots en vie … » ; les bots du tutoriel apparaissent dans la ligne `crac:tuto` du
   tableau des mondes (environ 10 joueurs).
4. **Zombies** : « zombies HeraliZ » au-dessus de 0 dans la chauffe et le tableau (peuplement autour des bots). S'il reste à
   0 : le peuplement ne marche pas dans ce monde (à me signaler avec le bloc « État à la fin », ligne `zombies stats`).
5. **Commandes des bots** : « Commandes tapées » au-dessus de 0, tableau par commande (les plus lentes en haut).
6. **Mesure** : tableau des ms par tick rempli, TPS réel, mémoire, tronçons ; « créés » proche de 0 (sinon le terrain se
   créait encore : relancer avec plus de chauffe, ou le monde plat).
7. **Diagnostic** : `profil-…txt` et `mesure-…txt` dans « Rapports du diagnostic » (et en entier dans le journal).
8. **Arrêt** : « Stopping server » dans le journal, artefact `banc-rapport` présent (sauf quota plein).

## En jeu (serveur d'essai sur un PC, `python outils/serveur_local.py <dossier> --banc`)

- `/bots lancer 10`, puis `/bots mesure 60` : une minute plus tard, message « Scénario terminé : mesure de 60 s, 10 bots … —
  rapport diag/banc-… » et le fichier dans `diag/`. `/bots mesure stop` arrête avant (rapport quand même).
- `/bots commandes 30` : les bots tapent des commandes (réponse « Réglage des bots : commandes = 30 s (N) ») ;
  `/bots commandes 0` arrête. Rien ne doit apparaître dans le chat des vrais joueurs.
- `/tuto lancer HzBot001` : le bot part au tutoriel et y bouge autour de son point d'arrivée ; tué, il revient à son lieu
  de départ sur la carte.

## Questions pour le propriétaire

- Le dépôt est-il public ? Sinon, la machine de GitHub n'a que 2 cœurs et 7 Go : le banc marche, mais avec moins de bots
  (40 au plus conseillé) ; le résumé affiche la machine utilisée.
- Faut-il aussi des bots qui restent au spawn (le monde du spawn construit n'est pas dans le dépôt : il faudrait une
  copie du spawn dans une Release, comme Mohist) ?
