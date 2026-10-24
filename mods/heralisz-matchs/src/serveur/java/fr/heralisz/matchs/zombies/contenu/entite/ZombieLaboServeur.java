package fr.heralisz.matchs.zombies.contenu.entite;

import fr.heralisz.matchs.zombies.contenu.ZbSons;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/**
 * Version serveur du {@link ZombieLabo} (docs/METHODE_CLIENT_SERVEUR.md §5.1) : poursuite et coup de griffe de la cible
 * choisie par le jeu ({@link GriffeGoal}), intouchable au corps a corps pendant une sortie (terre, conduit, egout), son
 * du coup. Creee par {@code ZbRegistres.ZOMBIE_LABO_USINE}, branchee par la partie serveur ; le jar des joueurs n'a que
 * la classe commune.
 */
public class ZombieLaboServeur extends ZombieLabo {
    public ZombieLaboServeur(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new GriffeGoal(this, 1.0, 20));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // sortie de terre : on ne peut pas encore le toucher au corps a corps (les balles, si)
        if (sortieFin > this.tickCount && source.getDirectEntity() == source.getEntity() && source.getEntity() != null) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(Entity cible) {
        boolean ok = super.doHurtTarget(cible);
        if (ok) this.playSound(ZbSons.ZOMBIE_ATTAQUE.get(), 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
        return ok;
    }
}
