# Essais en jeu — anticheat du 08/11 (Spartan 2.12.0)

Ce qui a changé, et comment le vérifier. Rien n'a été essayé en jeu par Claude.

## 1. Overlay de Discord pris pour un cheat (expulsion à chaque connexion)

Cause : le nouvel overlay de Discord se branche sur l'affichage du jeu (SwapBuffers) par un petit RELAIS posé en mémoire,
qui saute ensuite dans `discordhook64.dll`. L'anticheat ne regardait que le premier saut (vers le relais, « sans
module ») et concluait « code injecté » : indice fort, expulsion. Le jeu suit maintenant le relais (3 sauts au plus,
lecture sûre qui ne peut pas faire planter le jeu) et rapporte la DLL qui reçoit vraiment l'affichage ; le serveur la
juge comme un overlay direct (liste `scan.overlays`, à sa place normale).

À vérifier :
- [ ] un joueur avec l'overlay de Discord ACTIVÉ se connecte et joue plusieurs minutes : plus d'expulsion
      « affichage détourné … sans module » ; au plus un contrôle de plus quand l'overlay s'accroche (voir 3) ;
- [ ] même chose avec l'overlay de Steam ou OBS si quelqu'un l'a ;
- [ ] `/spartan scan <joueur>` sur ce joueur : pas d'indice « inject:hook » ;
- [ ] un vrai crochet de triche reste attrapé (relais vers de la mémoire sans DLL = indice fort, expulsion ; relais
      vers une DLL inconnue = soupçon) : couvert par les tests automatiques, à confirmer si l'équipe a un client de
      triche d'essai.

## 2. Contrôle immédiat sur les touches des menus de triche

Avant : une touche « Maj droite », « Inser »… pressée sans action donnait une alerte TricheMenu, mais le contrôle complet
n'arrivait qu'au hasard (90 à 240 s sur le Ptero). Maintenant, la première alerte lance TOUT DE SUITE un contrôle
complet (au plus un par minute et par joueur : réglage `immediateScanGapSeconds = 60` dans
`config/spartan-common.toml`, section `[scan]` ; 0 = coupé).

À vérifier :
- [ ] un joueur propre appuie sur Maj droite : alerte TricheMenu + un contrôle dans les secondes qui suivent, rien
      d'autre (pas d'expulsion) ;
- [ ] un client injecté puis Maj droite : expulsé en quelques secondes (le temps du contrôle, ~2–3 s).

## 3. Veille du jeu (toutes les 3 s)

Un fil à part dans le jeu du joueur (basse priorité, jamais sur le rendu) regarde toutes les 3 s : les nouveaux threads
Java sans nom ou exécutant du code hors des mods connus ; sous Windows, les nouveaux threads natifs hors de toute DLL,
les nouvelles DLL chargées hors de Windows et du Java du jeu (relu toutes les 30 s, ou tout de suite si un thread
inconnu apparaît), et le début de SwapBuffers qui change. Elle ne juge rien : elle dit seulement au serveur
« quelque chose a changé » (un mot), au plus toutes les 20 s, et le serveur lance un contrôle complet (même limite
d'un par minute). Ce qui existait déjà à la connexion n'est jamais signalé (le contrôle de connexion le juge).

À vérifier :
- [ ] FPS identiques avec et sans la mise à jour (la veille ne coûte presque rien) ;
- [ ] un joueur propre qui joue 15 min : au plus quelques contrôles de plus (overlay qui s'accroche, capture d'écran),
      jamais d'alerte ni d'expulsion ;
- [ ] client injecté EN COURS de partie (sans toucher Maj droite) : expulsé en moins de ~10 s (3 s de veille + le
      contrôle) au lieu d'une à huit minutes.

## Non vérifié par Claude

- Fabrication complète (Forge) : faite par le contrôle de branche de GitHub. Compilé ici : les classes du contrôle
  (InjectionCollector, Veille, ScanAnalyzer…) avec de petites copies vides des classes de Minecraft ; tests
  automatiques ScanAnalyzerTest et InjectionCollectorTest : 33 réussis, 3 sautés (Windows seulement : relais lu dans
  la vraie mémoire, page illisible sans plantage, JVM propre sans trace).
- ScanManager, ReceptionServeur, SpartanConfig et ClientEvents (qui ont besoin de Forge) : relus, pas compilés ici.
