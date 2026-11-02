# Essais en jeu — chantier « bases et ClashWar » (audit du 02/11)

Bases (CRac) **1.30.0-beta**, ClashWar **1.1.0**. Rien n'a été testé en jeu ni fabriqué : Gradle est bloqué sur cette
machine (maven.minecraftforge.net inaccessible). Tous les fichiers Java touchés passent la lecture de javac (syntaxe) ;
la compilation complète et `verif_split` se feront au contrôle de branche de GitHub. `python outils/verifier_langues.py` :
0 erreur.

**Joueurs ET serveur doivent avoir les nouveaux jars** : le canal `crac:main` passe au protocole 31 (console admin
retirée). Le canal du ClashWar ne change pas.

## Ce qui change

| Audit | Quoi | Pour le joueur |
|---|---|---|
| 38 | Essais automatiques de CRac (GameTests, 4 739 lignes, 3 structures) sortis des jars (`src/essais`, compilés, jamais livrés) | rien |
| 38 | 11 anciennes recettes d'établi de CRac (C4 à la netherite, clous en 2 × 2…) et leurs 11 « succès » retirés | plus de recette Minecraft pour les objets de CRac (ils s'obtiennent comme avant : Table d'armurier, Bâtisseur, loot) |
| 37, 11 | Console admin de CRac supprimée (4 écrans, 5 paquets, `crac-admin.toml` écrit sur le PC des joueurs) | la touche « Console admin CRac » disparaît des Commandes |
| 38 | Ancien monde `crac:bases` plus créé au démarrage (voir plus bas) | rien, sauf un joueur resté dans ce monde : ramené au spawn |
| 38 | Restes : sas du tutoriel, `zone`/`duel` dans les commandes bloquées du tutoriel, monde `heralisz_bases:bunkers` dans `blockedDimensions`, usines du gabarit | rien |
| 37 | ClashWar : moteur et règles du tournoi (2 291 lignes) côté serveur seulement | jar joueur plus léger, rien d'autre |
| 3 | `Tournois.enCours()` : vrai seulement pendant un vrai tournoi (le module `evenements` doit s'en servir, autre chantier) | — |
| 36 | `/matchs` fondue dans `/clashwar` : `/clashwar coffre etat\|rendre <joueur>`, `/clashwar parties`, `/clashwar arenes …` (op 2) | staff : une commande de moins |
| 38, 40 | Commandes permises en match nettoyées ; seuil « 4 clans » des points de saison dans `clashwar.json` (`saison_clans_min`) | rien |
| 8 | Objets à vie jamais perdus par le Coffre des matchs ni par les bases (coffre détruit, base effacée) | voir les essais 6 à 9 |

### L'ancien monde `crac:bases`
- Sa déclaration n'est plus livrée ; un petit mixin serveur (facultatif) empêche Minecraft de le recréer d'après le
  `level.dat`. **Rien n'est effacé** : le dossier `dayz_map/dimensions/crac/bases` reste tel quel ; ses fichiers de
  génération restent dans CRac (sans eux, le `level.dat` actuel ne se lirait plus).
- Pour le rouvrir : `python modules/bases/outils/monde_bases.py --remettre` (remet la déclaration à l'identique,
  vérifié octet par octet), refabriquer CRac, redémarrer.
- Plus rien ne s'en sert : bases des clans (carte), tutoriel (`crac:tuto`), carrières (carte), aucun ticket de tronçon.
  Il reste cité (sans effet) dans `mondes_sans_spawn` du noyau, `MONDES_SANS_POINTS` des clans, `protectedDimensions`
  de `crac-server.toml` (gardé pour une réouverture), un texte du module `banc`, et l'option « Monde des bases : mettre
  de côté dimensions/crac/bases » du workflow de mise en ligne (devenue inutile).

## Essais à faire (serveur d'essai, puis Ptero)

1. **Démarrage** : le serveur démarre ; journal : « [CRac] ancien monde des bases (crac:bases) fermé : pas créé » ;
   `/execute in crac:bases run tp ~ ~ ~` répond que le monde n'existe pas ; `dayz_map/dimensions/crac/bases` est toujours
   là. Redémarrer une 2e fois : même chose (le `level.dat` ne le cite plus).
2. **Joueur resté dans l'ancien monde** (s'il en reste : un tutoriel commencé avant le 28/10) : à sa connexion, il arrive
   au spawn avec son inventaire, message jaune ; si son tutoriel était en cours, il repart du début dans `crac:tuto`.
3. **Tutoriel** complet d'un nouveau joueur dans `crac:tuto` : pas de créature, pas de seau, fin au spawn (inchangé).
4. **Console admin** : plus de touche « Console admin CRac » dans Options › Commandes ; `/base effacer`, `/protection`,
   `/coffres`, `/tuto` marchent comme avant.
5. **Recettes** : le livre de recettes ne propose plus de C4, clous, panneaux… de CRac.
6. **Objet à vie dans un coffre de base détruit** : poser un couteau à vie dans une malle de chantier d'une base, faire
   sauter la malle au C4 : le reste tombe, le couteau arrive dans l'inventaire du chef du clan (connecté) avec un message
   doré, ou à sa prochaine connexion. Inventaire plein : message jaune, nouvel essai toutes les 30 s.
7. **/base effacer** sur un chef dont un coffre contient un objet à vie : l'objet lui est rendu (pas détruit).
8. **ClashWar, entrée en match** avec un couteau à vie au curseur (inventaire ouvert) et l'inventaire plein : rien ne
   tombe au sol ; à la fin du match, le couteau revient (inventaire, grille 2 × 2 ou curseur).
9. **ClashWar, fin de match** : tout l'inventaire d'avant revient ; les objets du kit du match disparaissent comme avant.
10. **/clashwar coffre etat <joueur>**, **/clashwar arenes liste**, **/clashwar parties** (op 2) : mêmes réponses que
    l'ancien `/matchs` ; un joueur sans droit ne voit pas ces sous-commandes ; `/matchs` n'existe plus.
11. **ClashWar complet** (inscriptions, tableau, matchs, podium, mur du tableau, classement) : rien ne doit avoir changé ;
    le jeu du joueur ne doit afficher aucune erreur (HUD, choix de classe, tableau).
12. **En match** : `/msg`, `/r`, `/clashwar quitter` permis ; le reste refusé (inchangé).

## Pas fait, ou pour un autre chantier
- `SaisonVue` (module clans) garde sa copie du seuil « 4 clans » : à lire dans `clashwar.json` par le chantier clans.
- Le Directeur d'événements (module evenements) doit lire `Tournois.enCours()` (point 3).
- Textes laissés dans d'autres modules : `heraliz_grades.staff.cmd.matchs` (panneau du staff, boutique), touche
  `key.crac.admin_console` cachée par `interface` (sans effet), commande `matchs` peut-être donnée à un grade dans
  `heraliz_grades/commandes_staff.json` sur le Ptero (sans effet).
- `python outils/commandes.py` à relancer après la fusion des chantiers (inventaire des commandes).
- `/cracbuild` (outils de construction, op 2) existe toujours, contrairement à ce que dit l'audit « commandes » §11.
- Un pilleur qui OUVRE un coffre de base peut toujours y prendre un objet à vie (seule la destruction est couverte).
