package fr.heralisz.matchs.zombies.contenu.entite;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/**
 * Poursuite et coup de griffe d'une cible choisie par le jeu ({@code setTarget}). Differences avec
 * {@code MeleeAttackGoal} : ne choisit jamais de cible, et n'arrete <b>pas</b> la navigation en s'arretant (le jeu
 * reprend la main sur le deplacement des qu'il retire la cible, pour conduire le zombie a une fenetre) ; chemin
 * recalcule au plus toutes les 8 ticks (plus souvent de pres) ; coup quand la cible est a portee de bras, avec un
 * vrai {@code swing} (animation de griffe) et {@code doHurtTarget}.
 */
public class GriffeGoal extends Goal {
    private final PathfinderMob mob;
    private final double vitesse;
    private final int cadence;
    private int recalcul;
    private int recharge;
    private double cx, cy, cz;

    /**
     * @param vitesse multiplicateur de l'attribut de vitesse
     * @param cadence ticks entre deux coups
     */
    public GriffeGoal(PathfinderMob mob, double vitesse, int cadence) {
        this.mob = mob;
        this.vitesse = vitesse;
        this.cadence = cadence;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean cibleValide(LivingEntity c) {
        if (c == null || !c.isAlive() || c.level() != mob.level()) return false;
        return !(c instanceof Player p) || !p.isSpectator();
    }

    @Override
    public boolean canUse() {
        return cibleValide(mob.getTarget());
    }

    @Override
    public boolean canContinueToUse() {
        return cibleValide(mob.getTarget());
    }

    @Override
    public void start() {
        recalcul = 0;
        mob.setAggressive(true);
    }

    @Override
    public void stop() {
        mob.setAggressive(false);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity c = mob.getTarget();
        if (c == null) return;
        mob.getLookControl().setLookAt(c, 30.0F, 30.0F);
        double d2 = mob.distanceToSqr(c.getX(), c.getY(), c.getZ());
        recalcul = Math.max(recalcul - 1, 0);
        boolean bouge = c.distanceToSqr(cx, cy, cz) >= 1.0;
        if (recalcul <= 0 && (bouge || mob.getNavigation().isDone())) {
            cx = c.getX();
            cy = c.getY();
            cz = c.getZ();
            recalcul = d2 > 1024 ? 15 : d2 > 256 ? 10 : 4 + mob.getRandom().nextInt(4);
            if (!mob.getNavigation().moveTo(c, vitesse)) recalcul += 10;
        }
        recharge = Math.max(recharge - 1, 0);
        double portee = mob.getBbWidth() * 2.0F * mob.getBbWidth() * 2.0F + c.getBbWidth() + 0.6;
        if (recharge <= 0 && d2 <= portee && mob.getSensing().hasLineOfSight(c)) {
            recharge = cadence;
            mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(c);
        }
    }
}
