package fr.heraliz.airstrike.entity;

import fr.heraliz.airstrike.AirstrikeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import fr.heraliz.airstrike.registry.ModEntities;
import fr.heraliz.airstrike.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link EffectCloudEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new EffectCloudEntity(...) » côté serveur.
 */
public class EffectCloudEntityServeur extends EffectCloudEntity {
   public EffectCloudEntityServeur(EntityType<? extends EffectCloudEntity> type, Level level) {
      super(type, level);
   }


    public static EffectCloudEntity create(Level level, int kind, Vec3 pos, float radius, int durationTicks, int color) {
        // created by the type (factory): the server version
        EffectCloudEntityServeur e = (EffectCloudEntityServeur) ModEntities.EFFECT_CLOUD.get().create(level);
        e.setPos(pos.x, pos.y, pos.z);
        e.entityData.set(KIND, kind);
        e.entityData.set(RADIUS, radius);
        e.entityData.set(DURATION, durationTicks);
        e.entityData.set(COLOR, color);
        return e;
    }

    /** Original server branch of tick() (the common tick() calls it on the server, after Entity.tick()). */
    @Override
    void tickServeur() {
        age++;
        if (age % 20 == 0) entityData.set(AGE, age);
        if (age >= getDuration()) {
            discard();
            return;
        }
        if (age % 10 == 0) applyEffects();
        if (getKind() == NAPALM && age % 20 == 5 && age < getDuration() - 60) rekindle();
        if (getKind() == SMOKE && age % 40 == 1 && age < 400) {
            level().playSound(null, getX(), getY(), getZ(), ModSounds.SMOKE_HISS.get(), SoundSource.NEUTRAL, 1.2f,
                    0.9f + random.nextFloat() * 0.2f);
        }
    }

    private boolean isInside(Entity e) {
        float r = effectiveRadius();
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        double dy = e.getY() - getY();
        double height = getKind() == GAS ? 3.5 : getKind() == NAPALM ? 2.5 : 6.0;
        return dx * dx + dz * dz <= r * r && dy > -2.0 && dy < height;
    }

    private void applyEffects() {
        int kind = getKind();
        if (kind == MARKER) return;
        float r = effectiveRadius();
        AABB box = new AABB(getX() - r, getY() - 2, getZ() - r, getX() + r, getY() + 7, getZ() + r);
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, this::isInside)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            switch (kind) {
                case SMOKE -> {
                    if (AirstrikeConfig.SMOKE_BLINDS.get() && e instanceof Player && intensity() > 0.4f) {
                        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, true, false, false));
                    }
                }
                case GAS -> {
                    if (e.getItemBySlot(EquipmentSlot.HEAD).is(GAS_MASKS)) continue;
                    e.addEffect(new MobEffectInstance(MobEffects.POISON, 70, 1, true, true, true));
                    e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0, true, false, true));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0, true, false, true));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, true, false, true));
                }
                case NAPALM -> {
                    if (!e.fireImmune()) {
                        e.setSecondsOnFire(8);
                        e.hurt(level().damageSources().inFire(), 2.0f);
                    }
                }
                default -> {
                }
            }
        }
        if (kind == SMOKE && AirstrikeConfig.SMOKE_BREAKS_AGGRO.get()) {
            AABB wide = box.inflate(24);
            for (Mob m : level().getEntitiesOfClass(Mob.class, wide)) {
                LivingEntity target = m.getTarget();
                if (target != null && isInside(target)) m.setTarget(null);
            }
        }
    }

    /** Napalm keeps setting the ground on fire for its whole duration. */
    private void rekindle() {
        if (!AirstrikeConfig.DESTROY_BLOCKS.get() || !AirstrikeConfig.NAPALM_PLACES_FIRE.get()) return;
        float r = getRadius() * 0.9f;
        for (int i = 0; i < 5; i++) {
            Vec3 p = randomInDisc(random, r);
            int x = Mth.floor(p.x), z = Mth.floor(p.z);
            int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (Math.abs(y - getY()) > 4) continue;
            BlockPos pos = new BlockPos(x, y, z);
            if (level().getBlockState(pos).isAir() && BaseFireBlock.canBePlacedAt(level(), pos, Direction.UP)) {
                level().setBlockAndUpdate(pos, BaseFireBlock.getState(level(), pos));
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(KIND, tag.getInt("Kind"));
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(DURATION, tag.getInt("Duration"));
        entityData.set(COLOR, tag.getInt("Color"));
        age = tag.getInt("Age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Kind", getKind());
        tag.putFloat("Radius", getRadius());
        tag.putInt("Duration", getDuration());
        tag.putInt("Color", getColor());
        tag.putInt("Age", age);
    }
}
