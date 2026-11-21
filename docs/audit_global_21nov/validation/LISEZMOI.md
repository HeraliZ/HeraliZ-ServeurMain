# Audit de validation du 21/11 (soir) : synthèse

Demandé par le propriétaire après la mise en ligne de 17 h 43 (heure UTC) : « un audit bien profond et complet pour bien
tout valider ». Dix nouveaux relecteurs (aucun n'avait participé au premier audit) ont relu chacun une partie du serveur
en deux temps : chaque changement du jour, un par un, puis un passage complet de leur partie. **Rien n'a été testé en
jeu** ; fabrication, contrôles et démarrage d'essai sur GitHub. Essais en jeu : section 51 de
`docs/TESTS_EN_JEU_17NOV_carte-ile.md`.

## En bref

**Tous les changements du jour sont validés** (une soixantaine de commits relus) : ils font ce qu'ils annoncent. Trois
avaient un effet de bord, corrigé : la porte d'une salle à carte au retour d'un joueur (loot), le marquage du Coffre du
ClashWar pendant l'écriture (ClashWar), et la dispense des actions du panneau du staff qui s'appliquait aussi aux
panneaux en bois portant une commande (noyau).

| Partie | Compte rendu | Corrigé | Versions |
|---|---|---|---|
| Boutique | `v1_boutique.md` | 3 (`/tpa` deux fois pour une recharge ; refus du site des PB pris pour un site muet dans `/boutique` et `/trade` ; caisse du casino à chaque image) | Boutique 1.7.4 |
| Monde | `v2_monde.md` | 1 (point de chute de l'hélicoptère abattu : tronçons tous prêts) | Monde 1.3.12 |
| Bases | `v3_bases.md` | 2, dont 1 **important** (clic gauche sur un bloc lointain : un jeu modifié pouvait faire charger de la carte lointaine au serveur ; `/base effacer` ne retire qu'une vraie armoire) | CRac 1.38.11-beta |
| Armes, équipement, sacs | `v4_armes_equipement_sacs.md` | doc seulement (chiffres de l'essai `/camos dessins` : 167 fichiers, 698 Ko, 88 Ko) | — |
| Clans, PNJ, événements | `v5_clans_pnj_evenements.md` | 1 (coffre du clan et autres actions des clans au départ d'un ClashWar) | Clans 3.19.4 |
| Loot, bunker | `v6_loot_bunker.md` | 3 (porte de salle à carte au retour d'un joueur ; mémoire du jeu des joueurs qui gardait chaque joueur vu passer une carte ; conteneurs T6 de l'épave) | Loot 0.20.9, Bunker 0.14.9 |
| Réanimation, ClashWar | `v7_survie_clashwar.md` | 4 cas rares du Coffre | ClashWar 1.3.9 |
| Anticheat, voix, launcher | `v8_anticheat_voix_launcher.md` | 1 (file d'attente d'un joueur parti reçue par une nouvelle connexion du même compte) | Spartan 2.15.6 |
| Noyau, interface, site, outils | `v9_noyau_interface_site_outils.md` | 3 (panneaux en bois à commande ; secrets de production à portée d'une bibliothèque d'un autre auteur pendant les contrôles de la mise en ligne ; `/redemarrage annuler`) | Noyau 1.1.12 |
| Liens entre les mods | `v10_dependances_mixins.md` | outil du démarrage « mixins stricts » réparé | — |

Corrigé aussi par le coordinateur : les journaux **publics** des tâches « Journaux du serveur » et « Lenteurs du
serveur » ne montrent plus jamais le chat, les messages privés ni les commandes tapées (une commande n'apparaît dans
« Lenteurs » que si elle a été vue au moins 3 fois : un mot de passe tapé une fois par erreur comme une commande n'y
apparaît plus).

## Démarrage « mixins stricts » : enfin jusqu'au bout

L'option qui fait noter chaque injection ratée sans arrêter le serveur s'appelle `mixin.env.ignoreRequired` (et pas
`mixin.ignoreRequired`, d'où les trois essais arrêtés plus tôt). Résultat : côté serveur, les 111 mixins de nos 19
configurations ont été essayés ; **une seule injection rate, et c'est voulu** (deuxième forme d'un même appel dans
`ReglagesGenerationMixin` du noyau, prévue facultative) : rien n'est cassé en ligne. Mods des autres auteurs : aucune
injection ratée en dehors des deux de ModernFix connues (coupées dans l'essai seulement). Pas vérifié : les mixins côté
joueur (il faut un vrai jeu : `outils/client_essai.py` sur un PC).

## Kills de saison et adresses IP

Les kills ne comptent pas entre deux joueurs de la même adresse IP (anti-triche). Si le serveur voyait tous les joueurs
avec une seule adresse (celle d'une passerelle), aucun kill ne compterait. Les journaux du jour ne montrent aucun kill
compté ni refusé, donc rien ne permet de trancher par là ; mais AuthMe refuse plus de 5 joueurs connectés depuis la même
adresse (`maxJoinPerIp: 5`) : **si plus de 5 joueurs ont déjà été connectés en même temps, les adresses vues sont les
bonnes**. À confirmer par le propriétaire.

## À décider (nouveau ; détails, risques et fichiers dans chaque compte rendu)

- **Sécurité, moyen** : `/camos ajouter` et `/camos retirer` sont à l'op 2 (un staff op 2 peut se donner tous les skins
  vendus en boutique) : proposé op 4 (v4).
- **Sécurité, moyen** : contrôles de la mise en ligne dans un job sans aucun secret, empreinte de la bibliothèque JOML
  vérifiée (v9 n° 1) ; `/quete reset` (op 2) permet à un staff de refaire pour lui les quêtes de Varga et leurs
  récompenses (v5 P2).
- Petits : solde des PB gardé 5 s dans `/boutique` (v1) ; borne de départ qui charge elle-même un tronçon sous forte
  charge (v2) ; deux fichiers de cases Curios en trop (les 2 erreurs « face / ear_protection » de chaque démarrage, v4) ;
  limites de cadence restantes (écran des quêtes du jour v5 P3, choix des exécutions v7 P4) ; voix d'avant le `/login`
  gardée dans la file de Spartan quand l'UDP est bloqué (v8 P1) ; version minimale du noyau dans 8 `mods.toml` (v10).

## À faire par le propriétaire

- Créer le secret GitHub `NOMS_INTERDITS` (toujours absent).
- Secrets de l'environnement « production » vides : `SPARTAN_DISCORD_SANCTIONS`, `LITEBANS_DB_*`, `HERALIBOT_LIEN_*`
  (rien à faire si c'est voulu).
- Confirmer qu'on a déjà eu plus de 5 joueurs connectés en même temps (voir plus haut).
