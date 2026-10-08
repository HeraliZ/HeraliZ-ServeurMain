/**
 * Contrat du moteur commun (MATCHS_SPEC §1.10) entre les agents : signatures stables, seulement etendues (jamais
 * renommees ni retirees sans accord). Tout est a appeler sur le fil serveur, sauf mention contraire.
 * <ul>
 *     <li>{@link fr.heralisz.matchs.moteur.api.MatchApi} : parties actives, joueurs en match, drapeau {@code heralisz:en_match} ;</li>
 *     <li>{@link fr.heralisz.matchs.moteur.api.Partie} (abstraite), {@link fr.heralisz.matchs.moteur.api.Equipe} ;</li>
 *     <li>{@link fr.heralisz.matchs.moteur.api.Coffre} : sauvegarde / restauration du joueur ;</li>
 *     <li>{@link fr.heralisz.matchs.moteur.api.CombatTag} : tag de combat 15 s ;</li>
 *     <li>{@link fr.heralisz.matchs.moteur.api.Arenes}, {@link fr.heralisz.matchs.moteur.api.Carte},
 *     {@link fr.heralisz.matchs.moteur.api.Instance}, {@link fr.heralisz.matchs.moteur.api.Pose} ;</li>
 *     <li>{@link fr.heralisz.matchs.moteur.api.Groupes}, {@link fr.heralisz.matchs.moteur.api.Groupe} ;</li>
 *     <li>{@link fr.heralisz.matchs.moteur.api.Kits}, {@link fr.heralisz.matchs.moteur.api.Kit},
 *     {@link fr.heralisz.matchs.moteur.api.Equipement} ;</li>
 *     <li>{@link fr.heralisz.matchs.moteur.api.Hud}, {@link fr.heralisz.matchs.moteur.api.Annonces},
 *     {@link fr.heralisz.matchs.moteur.api.KillFeed}.</li>
 * </ul>
 * Les methodes marquees « Reserve au moteur » ne sont pas a appeler par les modes. Les corps marques « provisoire »
 * sont a ecrire par l'agent moteur sans changer les signatures.
 */
package fr.heralisz.matchs.moteur.api;
