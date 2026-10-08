package fr.heralisz.matchs.zombies.jeu.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import javax.annotation.Nullable;

/**
 * Fabrique des creatures du mode zombies, fournie par le module contenu ({@code zombies/contenu}) via
 * {@link ZombiesApi#fabrique(ZombieFabrique)} dans son {@code construire}. Sans fabrique enregistree, le jeu cree un
 * zombie vanilla de secours (developpement seulement, journalise).
 * <p><b>Contrat</b> :
 * <ul>
 *     <li>{@link #creer} construit l'entite <b>sans</b> l'ajouter au monde ni la positionner : le jeu la place, pose
 *     les attributs ({@code MAX_HEALTH}, {@code MOVEMENT_SPEED} = {@link Allure#vitesse}, {@code ATTACK_DAMAGE},
 *     {@code FOLLOW_RANGE}), la sante, le tag {@link ZombiesApi#TAG_PARTIE}, puis l'ajoute ({@code addFreshEntity}) ;</li>
 *     <li>l'entite ne choisit <b>pas</b> sa cible seule (aucun {@code NearestAttackableTargetGoal}) : le jeu appelle
 *     {@code setTarget(joueur)} (joueur debout le plus proche) ou {@code setTarget(null)} tant que le zombie est dehors,
 *     et la deplace lui-meme vers la fenetre ({@code getNavigation().moveTo}) ; l'entite doit poursuivre et frapper sa
 *     cible avec un vrai coup de griffe ({@code doHurtTarget}) quand elle en a une ;</li>
 *     <li>pas de combustion au soleil, aucun butin, aucune experience, pas de ramassage d'objets, pas de despawn
 *     ({@code removeWhenFarAway} faux / {@code setPersistenceRequired}) ; sons maison ;</li>
 *     <li>{@code variante} : 0..3 (texture « labo »), au choix de la fabrique si hors bornes ;
 *     {@code manche} : pour d'eventuels details visuels (yeux, sang) ;</li>
 *     <li>rendre null en cas d'echec (le jeu reessaie plus tard).</li>
 * </ul>
 * Fil serveur.
 */
@FunctionalInterface
public interface ZombieFabrique {
    @Nullable
    Mob creer(ServerLevel niveau, TypeZombie type, int manche, Allure allure, int variante);
}
