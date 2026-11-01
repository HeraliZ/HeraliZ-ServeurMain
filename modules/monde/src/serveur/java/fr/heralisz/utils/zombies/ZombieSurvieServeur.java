package fr.heralisz.utils.zombies;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/**
 * Zombie de la survie côté serveur (01/11) : créé par {@link ZombiesSurvie#ZOMBIE_USINE} dans les mondes du serveur
 * (docs/METHODE_CLIENT_SERVEUR.md §4) ; le jeu des joueurs garde {@link ZombieSurvie}. Même entité, même identifiant
 * ({@code heralisz_utils:zombie_survie}), mêmes données gardées : seule sa navigation change ({@link NavigationZombie}, chemin
 * gardé vers une cible loin ou hors d'atteinte). Toujours le créer par son type ({@code ZombiesSurvie.ZOMBIE.get().create}).
 */
public class ZombieSurvieServeur extends ZombieSurvie {
    public ZombieSurvieServeur(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new NavigationZombie(this, level);
    }
}
