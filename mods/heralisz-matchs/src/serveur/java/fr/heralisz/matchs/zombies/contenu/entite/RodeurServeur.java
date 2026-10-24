package fr.heralisz.matchs.zombies.contenu.entite;

import fr.heralisz.matchs.zombies.contenu.ZbSons;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Version serveur du {@link Rodeur} (docs/METHODE_CLIENT_SERVEUR.md §5.1) : poursuite et coup de griffe
 * ({@link GriffeGoal}), bond sur la cible a mi-distance, son du coup. Creee par {@code ZbRegistres.RODEUR_USINE},
 * branchee par la partie serveur ; le jar des joueurs n'a que la classe commune.
 */
public class RodeurServeur extends Rodeur {
    private int bondRecharge;

    public RodeurServeur(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new GriffeGoal(this, 1.0, 16));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;
        if (bondRecharge > 0) bondRecharge--;
        LivingEntity c = getTarget();
        if (c != null && bondRecharge <= 0 && onGround()) {
            double d2 = distanceToSqr(c);
            if (d2 > 9 && d2 < 36 && getSensing().hasLineOfSight(c)) {
                // bond : elan vers la cible
                Vec3 dir = new Vec3(c.getX() - getX(), 0, c.getZ() - getZ()).normalize();
                setDeltaMovement(dir.x * 0.9, 0.42, dir.z * 0.9);
                bondRecharge = 50 + random.nextInt(30);
                playSound(ZbSons.RODEUR_ATTAQUE.get(), 1.0F, 1.1F);
            }
        }
    }

    @Override
    public boolean doHurtTarget(Entity cible) {
        boolean ok = super.doHurtTarget(cible);
        if (ok) this.playSound(ZbSons.RODEUR_ATTAQUE.get(), 1.0F, 0.95F + this.random.nextFloat() * 0.1F);
        return ok;
    }
}
