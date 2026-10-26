package fr.heraliz.crac.entity;

import fr.heraliz.crac.pont.Serveur;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.registry.ModEntities;
import fr.heraliz.crac.registry.ModItems;
import fr.heraliz.crac.registry.ModSounds;
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

/** Timed explosive charge: hits only the piece (or placable) it is stuck to; on an ordinary block in a claim it breaches. */
public class C4Entity extends StickyExplosiveEntity {
    public C4Entity(EntityType<? extends C4Entity> type, Level level) {
        super(type, level);
    }

    public C4Entity(Level level, LivingEntity owner) {
        super(ModEntities.C4.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.C4_ITEM.get();
    }

    @Override
    protected double configuredFuseSeconds() {
        return Config.c4FuseSeconds.get();
    }

    @Override
    protected String blinkAnimation() {
        return "animation.model.blinking";
    }

    @Override
    protected void playArmSound() {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.C4_BEEP.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    @Override
    protected void explode(ServerLevel level, BlockPos pos, Vec3 hit, @Nullable Direction face) {
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 1.6F, 0.9F);
        Serveur.Moteur m = Serveur.MOTEUR;   // dégâts et destruction : partie serveur (raid/BlastDamage, RaidBlocks)
        if (m != null) m.c4(level, pos, hit, face);
        level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.15, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.2, this.getZ(), 60, 1.0, 0.5, 1.0, 0.02);
    }
}
