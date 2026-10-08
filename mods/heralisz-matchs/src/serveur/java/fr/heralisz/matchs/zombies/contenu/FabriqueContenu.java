package fr.heralisz.matchs.zombies.contenu;

import fr.heralisz.matchs.zombies.contenu.entite.Rodeur;
import fr.heralisz.matchs.zombies.contenu.entite.ZombieLabo;
import fr.heralisz.matchs.zombies.jeu.api.Allure;
import fr.heralisz.matchs.zombies.jeu.api.TypeZombie;
import fr.heralisz.matchs.zombies.jeu.api.ZombieFabrique;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import javax.annotation.Nullable;

/**
 * Fabrique des creatures (implementation de {@link ZombieFabrique}) : zombie « labo » (variante 0..3 : blouse, hazmat,
 * militaire, technicien ; demarche selon l'allure) ou rodeur. L'entite rendue n'est ni positionnee ni ajoutee au monde.
 */
public class FabriqueContenu implements ZombieFabrique {
    @Nullable
    @Override
    public Mob creer(ServerLevel niveau, TypeZombie type, int manche, Allure allure, int variante) {
        if (type == TypeZombie.RODEUR) return Rodeur.creer(niveau);
        return ZombieLabo.creer(niveau, allure == Allure.RODEUR ? Allure.SPRINTEUR : allure, variante, manche);
    }
}
