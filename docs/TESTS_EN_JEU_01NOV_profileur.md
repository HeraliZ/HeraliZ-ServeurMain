# Essais en jeu du 01/11 — chantier « profileur »

Demande du propriétaire : « si on a un truc qui fait exactement comme spark, on peut mettre spark de côté, mais il faut un
truc qui nous permette de voir ce qui fait lag, quelle partie du code, quel fichier, etc., un truc ultra précis pour régler
les problèmes simplement ».

**Rien de ceci n'a été essayé en jeu, ni compilé avec Gradle.** Vérifié hors du jeu :
- la partie sans Minecraft (arbre des appels, attribution aux mods, rapport, noms SRG) compilée et essayée sur de fausses
  piles (zombies, balles TaCZ, écouteur, mixin, attente d'un tronçon, commande, plugin AuthMe) : tout est bon ;
- la partie Minecraft (commandes, mesures, paquets, mixins) compilée seulement contre des imitations des classes de
  Minecraft, Forge et Netty : la vraie compilation se fait par le contrôle de branche de GitHub ;
- `outils/diag_noms.py` essayé sur la vraie table de MCPConfig 1.20.1 (les correspondances de Mojang ne sont pas
  téléchargeables d'ici : remplacées par des fausses, bâties sur la vraie table).

## Ce qui change

- `/diag profil [secondes] [intervalle]` (op 4 ou console) : profil du serveur façon spark, pendant ET entre les ticks,
  rapport `diag/profil-<date>.txt` et résumé dans le chat. `/diag start` en devient l'ancien nom (même chose, intervalle
  5 ms). `/diag stop` arrête et écrit le rapport. `/diag commandes` : les plus lentes depuis le démarrage.
- Chaque commande jouée est chronométrée (deux lectures d'horloge) : durée dans les rapports de blocage et le profil.
- Tâche GitHub « Lenteurs du serveur » : case « profil » (jamais cochée par défaut), noms de Minecraft et chemins de nos
  fichiers rendus lisibles, dernier profil en entier dans le journal et son résumé en tête de la page.
- spark : profileur de fond coupé (pris au prochain redémarrage), plugin gardé.
- Noyau 1.0.10 (la branche était déjà en 1.0.9 pour le chantier « perf-noyau »).

## À vérifier en jeu (serveur redémarré avec le nouveau noyau)

1. **Démarrage** : pas d'erreur Mixin dans le journal pour `heraliz_noyau_profil.mixins.json` ni
   `heraliz_noyau_serveur_forge.mixins.json` (les deux sont facultatives : au pire, un avertissement).
2. **Profil court** : `/diag profil 30` en jeu (op 4).
   - Message « Profil du serveur lancé pour 30 s… », puis toutes les 10 s une ligne « [Diag] xx ms/tick (max xx), 20.0 TPS
     — mod xx %, … ».
   - Au bout de 30 s (plus 1 à 2 s) : « [Diag] Profil terminé : … — rapport diag/profil-<date>.txt ».
   - Pendant le profil, le serveur ne doit pas ramer davantage (au plus 1 à 2 % de plus, voir l'en-tête « Coût des
     relevés » du rapport).
3. **Le rapport** (`diag/profil-<date>.txt`, ou tâche GitHub) :
   - en-tête : nombre de relevés proche de 30 s / 5 ms = 6 000 ; « Coût des relevés » de l'ordre de 20 à 150 µs ;
   - ligne « Noms des méthodes de Minecraft » : « traduits » si le `libraries/` du Ptero a les correspondances, sinon
     « noms SRG » (alors la tâche GitHub les traduit) ;
   - section 6 : tableaux **remplis** (mondes, entités par type, entités de bloc par type, écouteurs par événement,
     tronçons, paquets). Si « Entités et entités de bloc : rien mesuré… repli » apparaît, le mixin du chronomètre de
     Forge n'est pas posé : le dire (le profil marche quand même, avec les chiffres de Forge seul) ;
   - section « Paquets » : « connexions suivies » = nombre de joueurs connectés (sauf les bots du banc).
4. **Entre deux ticks** : pendant un profil, taper plusieurs commandes (`/clan info`, `/home`…) : elles apparaissent dans
   « Commandes jouées pendant le profil » (section 6) avec leur durée, et dans la section 1 bis si elles coûtent.
5. **Console du panel** : `diag profil 20` dans la console : le résumé arrive dans la console.
6. **Depuis GitHub** : Actions → « Lenteurs du serveur » → Run workflow, cocher « profil », 60 s. La tâche dure environ
   2 min de plus ; le journal de l'étape « Contenu » commence par « DERNIER PROFIL » et la page de la tâche montre le
   résumé. Sans la case : rien n'est lancé sur le serveur.
7. **`/diag stop`** pendant un profil : « Profil arrêté : rapport en cours d'écriture… », puis le résumé final.
8. **`/diag commandes`** après quelques commandes : liste des plus lentes (mots de la commande seulement, jamais les
   arguments ni le pseudo).
9. **Banc de charge** (`/bots scenario`, monde `crac:bases`) : le rapport `charge-<date>.txt` cite toujours son profil
   (`diag/profil-…`), qui est maintenant le nouveau rapport complet.
10. **spark** : après le redémarrage, `/spark` répond toujours ; plus de « Starting background profiler » dans le journal.

## Questions pour le propriétaire

- Le profil par défaut dure 60 s avec un relevé toutes les 5 ms (environ 1 % du serveur pendant ce temps). Plus long ou
  plus fin si besoin : `/diag profil 300 4`.
- Quand `/diag profil` aura fait ses preuves, on retire spark (une ligne : supprimer son jar de `serveur/plugins/`).
