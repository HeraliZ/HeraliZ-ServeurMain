# Essais en jeu du 01/11 — chantier « lenteurs »

Demande du propriétaire : « grosse montée de ms quand je faisais des commandes, tout répertorier et corriger ».
Données lues : tâche GitHub « Lenteurs du serveur » (après correction du 01/11 : rapports `diag/`, journaux du 28 au
01/11, `debug.log` depuis le démarrage du 28/10 à 17:12). Le serveur tournait encore avec les mods du 28/10 (`a03fafbd`) :
la mise à jour du 31/10 a été envoyée sans redémarrage.

**Rien de ceci n'a été essayé en jeu, ni compilé avec Gradle** (seulement la syntaxe Java vérifiée) : la fabrication
complète se fait par le contrôle de branche de GitHub.

## Ce que montraient les rapports (28/10 17:12 → 01/11)

62 lignes « blocage » dans le journal (seuil 100 ms), 40 rapports détaillés encore sur le serveur (les plus anciens sont
effacés au-delà de 60 fichiers).
Presque tout est la même cause : **un joueur posé dans un endroit pas encore chargé** ; le serveur s'arrête le temps de
lire et décoder ces morceaux de carte.

| Montée | Fois | Cause | Corrigé |
|---|---|---|---|
| 1,2 à 1,5 s | 3 | `/spawn` du staff (28/10 19:18, 19:40, 19:49) : arrivée au point /spawn pas chargé | oui (point /spawn gardé chargé) |
| 130 ms à 1,5 s | 4 | retour d'extraction au point /spawn (`Deplacements.allerAuPointDefini`, 29/10 01:54 à 02:43) | oui (même correctif) |
| 220 à 330 ms | 6 | départ du spawn vers la carte par la borne (`DepartServeur.partir`) | oui (chargé pendant le compte à rebours) |
| 0,6 et 1,3 s | 2 | `/tuto` : murs invisibles d'une nouvelle parcelle (17 000 blocs d'un coup, `PlotsTuto.poserMurs`) | oui (posés en quelques ticks, préparés avant l'envoi) |
| 120 à 260 ms | 5 | première connexion ou `/wipe` : point de réveil de la cinématique pas chargé | oui (point de réveil gardé chargé) |
| 150 à 750 ms | ~20 | joueur qui se déplace vite (vol du staff, `/tp` de Minecraft) dans des morceaux pas chargés | non : Minecraft lui-même |
| 115 ms | 1 | connexion : table des raretés du loot refaite pour chaque joueur | oui (faite une fois) |
| 117 ms | 1 | boutique : simple coup d'œil au disque toutes les 10 s, disque occupé | oui (fait à côté) |
| 110 à 220 ms | 2 | sauvegarde automatique (level.dat) | déjà allégée le 27/10, reste la part de Minecraft |
| 1,7 s | 1 | Spartan (anti-ESP) prépare sa table au premier tick après le démarrage | non (une fois par démarrage, personne en ligne) |
| 0,9 s | 1 | Mohist traduit des noms de classes à la première utilisation d'un plugin | non (Mohist, une fois) |
| 105 à 190 ms | 6 | divers une seule fois (connexion : liste des commandes, boutons du casino, spark, filtre du journal) | non |

Ces blocages passaient tous **dans le tick**. Les commandes elles-mêmes passent **entre deux ticks** : le diagnostic ne les
voyait pas. Il les voit maintenant (voir plus bas).

## À vérifier en jeu (serveur redémarré avec les nouveaux jars)

1. **Borne de départ** (spawn → carte) : le départ se fait comme avant à la fin du compte à rebours ; pas d'alerte
   `[Diag] Blocage` au moment du départ. Rarement, le départ peut attendre jusqu'à 3 s de plus (seulement si la carte
   n'est pas prête).
2. **`/spawn`** depuis sa base, et en staff (sans délai) : arrivée normale au point /spawn, pas d'alerte. Pareil pour un
   **retour d'extraction**.
3. **Première connexion** (ou `/wipe` d'un compte d'essai) : arrivée au point de réveil et cinématique comme avant, pas
   d'alerte à la connexion.
4. **Tutoriel des bases** avec un compte qui n'a jamais eu de parcelle : arrivée dans sa parcelle, **murs invisibles bien
   là** (essayer de sortir par les 4 côtés et par-dessus), pas d'alerte. Les murs peuvent mettre une ou deux secondes à
   apparaître si le monde du tutoriel n'était pas chargé : on est quand même ramené dans la parcelle.
5. **Diagnostic** : faire une commande lourde exprès (par exemple `/recharger`) ; l'alerte dit « entre deux ticks » et le
   rapport `diag/blocage-…` a une section « Dernières commandes » (mots de la commande seulement, sans pseudo). Plus de
   rapports « blocage de 2 ms ».
6. **Fuites** : plus d'alerte « tronçons chargés heralisz:spawn passe de 2209 à 4234 » quand un joueur reste au spawn.
7. **Boutique et hôtel des ventes** : changer `config/heraliz_grades/boutique.json` ou `hdv.json` ; la boutique / l'hôtel
   des ventes prennent le changement en 15 s au plus, comme avant.
8. **`/saison journal <tag>`** (op 4) : la liste arrive dans le chat (une fraction de seconde après la commande).
9. **Connexion** : rareté des objets (couleurs du loot) toujours affichée chez le joueur.

## Pas corrigé (à savoir)

- `/home` (vers l'armoire de la base) et `/rtp` ont la même cause possible (arrivée dans un endroit pas chargé) : pas vus
  dans les rapports, pas changés.
- Les bruits (tirs) qui envoient les zombies : 49 lignes « bruit … en N ms » de plus de 10 ms, jamais au-delà du seuil de
  100 ms. À étaler sur plusieurs ticks si ça grossit (étape 4 de `ARCHITECTURE.md`).
- Journal : avertissements « Invalid statistic … heralisz_n07 / heralisz_mammoth / heralisz_mere / heralisz_plague /
  heralisz_matchs » à chaque connexion (statistiques des joueurs qui citent les objets des boss mis de côté et de l'ancien
  Matchs) : sans effet. 13 refus de connexion « Channels [...] » : jeux avec des mods pas à jour.
- spark : profileur de fond coupé, puis plugin retiré le 01/11 (choix du propriétaire) : `/diag profil` du noyau fait le
  même travail.
