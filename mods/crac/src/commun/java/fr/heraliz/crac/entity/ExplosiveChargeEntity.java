package fr.heraliz.crac.entity;

import fr.heraliz.crac.pont.Serveur;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.registry.ModEntities;
import fr.heraliz.crac.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Satchel-style charge: the piece it is stuck to and the targets next to it that the blast can see, so nothing behind
 * the panel it is stuck to.
 */
public class ExplosiveChargeEntity extends StickyExplosiveEntity {
    public ExplosiveChargeEntity(EntityType<? extends ExplosiveChargeEntity> type, Level level) {
        super(type, level);
    }

    public ExplosiveChargeEntity(Level level, LivingEntity owner) {
        super(ModEntities.EXPLOSIVE_CHARGE.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.EXPLOSIVE_CHARGE.get();
    }

    @Override
    protected double configuredFuseSeconds() {
        return Config.explosiveChargeFuseSeconds.get();
    }

    /** Like Rust's satchel: a fuse drawn between chargeFuseSeconds and chargeFuseMaxSeconds, and a chance to go out. */
    @Override
    protected int drawFuseTicks() {
        double min = Config.explosiveChargeFuseSeconds.get();
        double max = Math.max(min, Config.explosiveChargeFuseMaxSeconds.get());
        return Fuse.ticks(min + this.random.nextDouble() * (max - min));
    }

    @Override
    protected boolean drawDud() {
        return this.random.nextDouble() < Config.explosiveChargeDudChance.get();
    }

    @Override
    protected void playDudSound() {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.6F, 1.2F);
    }

    @Override
    protected String blinkAnimation() {
        return "explosive_charge.model.blinking";
    }

    @Override
    protected void playArmSound() {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TNT_PRIMED, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    @Override
    protected void explode(ServerLevel level, BlockPos pos, Vec3 hit, @Nullable Direction face) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 1.8F, 1.05F);
        // pieces first: the sight lines are traced while the blocks the charge breaks still stand
        Serveur.Moteur m = Serveur.MOTEUR;   // dégâts puis destruction : partie serveur (raid/BlastDamage, RaidBlocks)
        if (m != null) m.charge(level, pos, hit, this.position(), face);
        level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.15, this.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.2, this.getZ(), 80, 1.2, 0.7, 1.2, 0.03);
    }
}
