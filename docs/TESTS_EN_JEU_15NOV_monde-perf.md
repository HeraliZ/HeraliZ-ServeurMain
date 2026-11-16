# Essais en jeu — audit du 15/11, monde, quêtes et performances

Corrections des rapports d'audit du 15/11 « monde et PvE » (M1, B1, B2, B3) et « performances » (n° 1, n° 2, n° 3 pour
les recharges des kits et du /tpa, n° 5, n° 6). Modules : Monde 1.2.2, Quêtes 1.21.3, CRac 1.37.2-beta, Interface
2.0.8, Boutique 1.5.8, Noyau 1.0.20.

Rien n'a été essayé en jeu par Claude, et rien n'a été fabriqué avec Gradle (téléchargements de Forge bloqués ici) :
seuls de petits morceaux ont été compilés avec javac contre des classes de remplacement (voir chaque partie). Jars
**joueurs ET serveur** à mettre à jour (brouillard, flou, barre, liste Tab : jeu des joueurs ; le reste : serveur).

## 1. Brouillard seulement sur la carte (monde M1)

Ce qui change : le brouillard tiré au hasard (clair, léger, moyen, fort) ne s'affiche plus que sur la carte. Les arènes
du ClashWar, le tutoriel des bases et le spawn gardent leur ciel ; la teinte grise ne change plus l'horizon du spawn.

À vérifier :
- `/brume fort 10` (staff) sur la carte : brouillard fort, comme avant.
- Avec le brouillard fort : aller au spawn, faire le tutoriel des bases, entrer dans une arène du ClashWar : pas de
  brouillard (seulement celui de Minecraft au bout de la vue). Revenir sur la carte : le brouillard fort est là tout de
  suite, le même que celui des autres joueurs (pas de nouveau fondu).
- Zones de gaz, de radiation, de froid : brouillard local et teinte comme avant ; sortir de la carte par la mort ou une
  extraction en plein gaz : rien de vert au spawn.

## 2. Une mort = un seul changement de monde (perf n° 2)

Ce qui change :
- **Au camp** (et toute mort sans choix) : le joueur réapparaît directement au point de réveil du spawn (avant : d'abord
  sur la carte, puis téléporté au spawn). Il arrive à sa place habituelle, regard réglé, écarté des autres s'ils sont
  plusieurs.
- **À la base** : il réapparaît sur la carte puis va à sa base dans le même monde (avant : carte, camp, puis base). Si la
  base refuse au dernier moment (home bouché…), il est envoyé au camp avec le message habituel.
- Quêtes : mourir sur la carte ne compte plus comme une extraction (Varga, quêtes du jour).

À vérifier (les plus importants de cette fiche) :
- Mort sur la carte, « Au camp » : arrivée au point de réveil, un seul écran de chargement, rien d'anormal dans le chat
  (surtout PAS « Votre lit est absent ou bloqué »). Deux ou trois joueurs morts en même temps : pas les uns dans les autres.
- Mort sur la carte avec une base et le droit, délai fini, « À la base » : arrivée devant le home de la base, message
  « Te voici à ta base… », un seul écran de chargement. Avec le home bouché : arrivée au camp avec le message.
- « À la base » quand le délai court encore (le bouton ne doit pas le permettre ; sinon) : camp et message de refus.
- Se déconnecter sur l'écran de mort, revenir, choisir « Au camp » puis, une autre fois, « À la base ».
- Mort dans le tutoriel des bases : comme avant (le tutoriel renvoie à la cabane).
- Une quête du jour ou de Varga « extraction » en cours : mourir sur la carte ne la fait PAS avancer ; une vraie
  extraction, si.
- Staff : `/spawnpoint` posé sur un joueur : il réapparaît à ce point, comme avant.
- `/diag profil 60` pendant plusieurs morts : la liste des commandes (`Commands.sendCommands`) n'est plus renvoyée
  qu'une fois par mort.

Pas vérifié : la règle « Bukkit » de réapparition de Mohist avec nos plugins (aucun ne la change d'après la liste, mais
pas essayé) ; le banc (bots) suit encore l'ancien chemin et ne mesure pas ce gain.

## 3. Flou derrière les écrans (perf n° 1)

Ce qui change : même aspect, beaucoup moins de travail pour la carte graphique (image réduite au quart, rendus avant /
après et chiffres dans le dossier des rendus du 15/11, `flou/`). Nouvel interrupteur **« Flou derrière les écrans »**
dans l'écran Graphismes (Échap > Qualité graphique, ou Options > Vidéo), au-dessus de « Réglages détaillés ».

À vérifier :
- Inventaire (E), sac, fouille d'un conteneur, coffre d'une base, menu Échap : le monde est flou derrière, comme avant
  (en 1080p, et si possible en 1440p ou 4K). Rien de « pixelisé » ni de décalé, surtout en tournant la tête ou quand
  quelque chose bouge derrière (zombie, joueur).
- Ouverture du menu Échap : le flou monte doucement, sans à-coup.
- Changer la taille de la fenêtre avec l'inventaire ouvert : le flou suit.
- Graphismes : couper « Flou derrière les écrans » : voile sombre seul, tout de suite ; le remettre ; relancer le jeu :
  le choix est gardé (fichier `config/heraliz_interface-client.toml`, `flou`).
- Images par seconde (F3) sur un PC modeste, inventaire ouvert : meilleures qu'avant (avant : 60 → 40 environ).

Pas vérifié : le programme de la carte graphique (`flou.fsh`) n'a pu être compilé ici (aucun outil) : s'il ne compile
pas, le jeu coupe le flou pour la session (voile sombre seul) et l'écrit dans le journal (« flou indisponible »).

## 4. Recharges des kits et du /tpa (perf n° 3)

Ce qui change : plus de réécriture du fichier entier à chaque kit ou /tpa ; une petite ligne dans un journal, le fichier
complet réécrit en fond au plus une fois toutes les 30 s, et les recharges finies sont oubliées.

À vérifier :
- Prendre un kit : il est donné ; le reprendre : refusé avec le temps restant. Redémarrer le serveur : toujours refusé.
- Même chose avec /tpa (recharge gardée après un redémarrage).
- `kit reset <joueur>` (console du serveur) : le joueur peut reprendre son kit, même après un redémarrage.
- Dossier `heraliz_grades/` : pendant le jeu, des fichiers `recharges.json.journal.N` peuvent apparaître ; après un arrêt
  propre, il n'en reste plus. Arrêt brutal (kill) juste après un kit : au redémarrage, la recharge est toujours là.
- `recharges.json` ne garde plus les kits pris il y a plus que la plus longue recharge + 1 jour, ni les /tpa finis.

## 5. Petits correctifs

- **Quêtes de Varga « fouiller des conteneurs T3 / T4 / T6 »** (monde B1) : une caisse « [MUNITIONS 5] », de soins ou de
  nourriture du bon niveau fait avancer le compteur (comme les quêtes du jour).
- **Étape « capturer »** (monde B2) : branchée sur les prises de zones des clans (membres au pied du drapeau). La quête
  « Prendre le terrain » reste désactivée : pour l'essayer, la réactiver sur un serveur d'essai, prendre une zone avec
  son clan, la quête avance ; une prise forcée par le staff ne compte pas.
- **Protections des zones** (monde B3) : masques et combinaisons protègent comme avant (gaz, radiation, froid) ; un motif
  avec un point dans `[protections]` de `heraliz_decors-server.toml` marche maintenant.
- **Train** (perf n° 5) : horaire calculé en une passe, même résultat exact (vérifié sur les deux réseaux) ; le train roule
  comme avant (gares, arrêts, portes) ; plus de petit gel à l'arrivée d'un joueur quand il roule.
- **level.dat** (perf n° 5) : après une sauvegarde automatique en marche, puis un arrêt et un redémarrage, le monde se
  recharge normalement (comme l'essai du 27/10).
- **Borne de départ** (perf n° 6) : l'attente entre deux départs marche comme avant, même après une reconnexion.
- **Barre DayZ et liste Tab** (perf n° 6) : affichage identique (chiffres des cases et des piles, liste par grade, ping,
  icône de voix) ; la liste suit les arrivées et départs.

## 6. Pas changé, à mesurer (monde, PLAUSIBLE : barricades et épave)

Chaque barricade ajoute 9 morceaux de collision, l'épave de l'hélicoptère 8. Rien n'a été changé sans preuve. Pour
mesurer, en soirée sur le Ptero :
1. compter les barricades : `/execute if entity @e[type=heralisz_bunker:barricade]` (le nombre s'affiche) ; et par
   endroit : `/execute as @a at @s if entity @e[type=heralisz_bunker:barricade,distance=..128]` ;
2. `/diag profil 60` pendant le pic de joueurs, puis lire la part de `Level.getEntities` /
   `EntityGetter.getEntityCollisions` (et `getPartEntities`) dans le rapport (`outils/diag_noms.py` pour les noms) ;
3. si cette part dépasse quelques pour cent du tick et grandit avec le nombre de barricades : moins de morceaux par
   barricade (3 au lieu de 9) ou un plafond par zone, à décider.
