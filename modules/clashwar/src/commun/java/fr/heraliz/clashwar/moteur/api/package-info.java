/**
 * Contrat du moteur commun (MATCHS_SPEC §1.10) entre les agents : signatures stables, seulement etendues (jamais
 * renommees ni retirees sans accord). Tout est a appeler sur le fil serveur, sauf mention contraire.
 * <ul>
 *     <li>{@link fr.heraliz.clashwar.moteur.api.MatchApi} : parties actives, joueurs en match, drapeau {@code heralisz:en_match} ;</li>
 *     <li>{@link fr.heraliz.clashwar.moteur.api.Partie} (abstraite), {@link fr.heraliz.clashwar.moteur.api.Equipe} ;</li>
 *     <li>{@link fr.heraliz.clashwar.moteur.api.Coffre} : sauvegarde / restauration du joueur ;</li>
 *     <li>{@link fr.heraliz.noyau.api.CombatTag} : tag de combat 15 s ;</li>
 *     <li>{@link fr.heraliz.clashwar.moteur.api.Arenes}, {@link fr.heraliz.clashwar.moteur.api.Carte},
 *     {@link fr.heraliz.clashwar.moteur.api.Instance}, {@link fr.heraliz.clashwar.moteur.api.Pose} ;</li>
 *     <li>{@link fr.heraliz.clashwar.moteur.api.Groupes}, {@link fr.heraliz.clashwar.moteur.api.Groupe} ;</li>
 *     <li>{@link fr.heraliz.clashwar.moteur.api.Kits}, {@link fr.heraliz.clashwar.moteur.api.Kit},
 *     {@link fr.heraliz.clashwar.moteur.api.Equipement} ;</li>
 *     <li>{@link fr.heraliz.clashwar.moteur.api.Hud}, {@link fr.heraliz.clashwar.moteur.api.Annonces},
 *     {@link fr.heraliz.clashwar.moteur.api.KillFeed}.</li>
 * </ul>
 * Les methodes marquees « Reserve au moteur » ne sont pas a appeler par les modes. Les corps marques « provisoire »
 * sont a ecrire par l'agent moteur sans changer les signatures.
 */
package fr.heraliz.clashwar.moteur.api;
