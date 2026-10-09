package com.heraliz.extraction.entity;

import com.heraliz.extraction.ModRegistry;
import com.heraliz.extraction.TerminalGeometry;
import com.heraliz.extraction.ZoneShape;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Terminal d'extraction posé au sol. Une fois posé, la zone reste ouverte pendant
 * la durée de vie de la zone (zoneLifetimeSeconds) : n'importe quel joueur qui y tient
 * le temps à tenir (durationSeconds) est extrait, chacun avec son propre chrono.
 * Sortir de la zone annule seulement le chrono du joueur concerné.
 * <p>
 * Partie commune : état synchronisé, forme de la zone, effets (particules). Chronos, barres, téléportation et sauvegarde
 * dans ExtractionTerminalEntityServeur (src/serveur) ; créer l'entité par son type, jamais par {@code new}.
 */
public class ExtractionTerminalEntity extends Entity {
    public static final byte STATE_ACTIVE = 0;
    public static final byte STATE_CLOSED = 1;

    /** Durée (ticks) pendant laquelle le terminal reste après la fermeture, le temps des effets. */
    public static final int END_LINGER = 60;
    /** Événement client : un joueur vient d'être extrait (gerbe d'étincelles). */
    protected static final byte EVENT_EXTRACTED = 60;

    protected static final EntityDataAccessor<Integer> DATA_BEST_PROGRESS =
            SynchedEntityData.defineId(ExtractionTerminalEntity.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> DATA_DURATION =
            SynchedEntityData.defineId(ExtractionTerminalEntity.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> DATA_TICKS_LEFT =
            SynchedEntityData.defineId(ExtractionTerminalEntity.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> DATA_EXTRACTING =
            SynchedEntityData.defineId(ExtractionTerminalEntity.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(ExtractionTerminalEntity.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Byte> DATA_STATE =
            SynchedEntityData.defineId(ExtractionTerminalEntity.class, EntityDataSerializers.BYTE);
    /** Zone fixe créée par un admin (/extraction create) : pas de PC, pas de fumée, permanente, toit autorisé. */
    protected static final EntityDataAccessor<Boolean> DATA_FIXED =
            SynchedEntityData.defineId(ExtractionTerminalEntity.class, EntityDataSerializers.BOOLEAN);

    /** Ticks depuis la fermeture (effets de fin ; le serveur retire le terminal après {@link #END_LINGER}). */
    protected int endTicks;

    /** Forme de la zone (suit le relief, s'arrête sur les murs), recalculée régulièrement. */
    @Nullable
    private ZoneShape zone;

    public ExtractionTerminalEntity(EntityType<? extends ExtractionTerminalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static boolean isUnderOpenSky(Level level, BlockPos pos) {
        return ZoneShape.isUnderOpenSky(level, pos);
    }

    public boolean isFixed() {
        return this.entityData.get(DATA_FIXED);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_BEST_PROGRESS, 0);
        this.entityData.define(DATA_DURATION, 1800);
        this.entityData.define(DATA_TICKS_LEFT, 6000);
        this.entityData.define(DATA_EXTRACTING, 0);
        this.entityData.define(DATA_RADIUS, 5.0F);
        this.entityData.define(DATA_STATE, STATE_ACTIVE);
        this.entityData.define(DATA_FIXED, false);
    }

    // ------------------------------------------------------------------ getters

    public byte getState() {
        return this.entityData.get(DATA_STATE);
    }

    public float getRadius() {
        return this.entityData.get(DATA_RADIUS);
    }

    public int getDuration() {
        return this.entityData.get(DATA_DURATION);
    }

    /** Temps restant avant la fermeture de la zone (ticks). */
    public int getTicksLeft() {
        return this.entityData.get(DATA_TICKS_LEFT);
    }

    /** Nombre de joueurs en train de s'extraire. */
    public int getExtractingCount() {
        return this.entityData.get(DATA_EXTRACTING);
    }

    /** Avancement du joueur le plus avancé dans la zone (0..1), visible par tous (couleur, écran). */
    public float getProgress(float partialTick) {
        return Mth.clamp((float) this.entityData.get(DATA_BEST_PROGRESS) / getDuration(), 0.0F, 1.0F);
    }

    /** Intensité des effets de la zone : monte une fois le fumigène déployé, retombe à la fermeture. */
    public float getVisualStrength(float partialTick) {
        if (isFixed()) {
            return 1.0F;
        }
        if (getState() == STATE_ACTIVE) {
            return Mth.clamp((this.tickCount + partialTick - TerminalGeometry.DEPLOY_END + 10.0F) / 10.0F, 0.0F, 1.0F);
        }
        return Mth.clamp(1.0F - (this.endTicks + partialTick) / 20.0F, 0.0F, 1.0F);
    }

    /** Couleur selon l'avancement : rouge → orange → vert. */
    public static float[] progressColor(float progress) {
        if (progress < 0.6F) {
            float t = progress / 0.6F;
            return new float[]{1.0F, Mth.lerp(t, 0.12F, 0.55F), Mth.lerp(t, 0.06F, 0.04F)};
        }
        float t = (progress - 0.6F) / 0.4F;
        return new float[]{Mth.lerp(t, 1.0F, 0.25F), Mth.lerp(t, 0.55F, 1.0F), Mth.lerp(t, 0.04F, 0.35F)};
    }

    /** Forme actuelle de la zone. */
    public ZoneShape getZone() {
        if (this.zone == null) {
            this.zone = ZoneShape.compute(this.level(), position(), getRadius(), !isFixed());
        }
        return this.zone;
    }

    protected void refreshZone() {
        if (this.zone == null || this.tickCount % 20 == 0) {
            this.zone = ZoneShape.compute(this.level(), position(), getRadius(), !isFixed());
        }
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            clientTick();
        } else {
            serverTick();
        }
    }

    /** Chronos des joueurs, barres, extraction, fermeture : dans ExtractionTerminalEntityServeur. */
    protected void serverTick() {
    }

    // ------------------------------------------------------------------ effets client

    private void clientTick() {
        if (getState() != STATE_ACTIVE) {
            if (this.endTicks == 0) {
                spawnBurst(false);
            }
            this.endTicks++;
            return;
        }

        refreshZone();
        if (isFixed()) {
            spawnOutlineParticles();
            return;
        }
        if (this.tickCount < TerminalGeometry.DEPLOY_END) {
            return;
        }

        RandomSource rand = this.random;
        Level level = this.level();
        Vec3 tip = position().add(TerminalGeometry.flareTipOffset(getYRot()));

        if (this.tickCount == (int) TerminalGeometry.DEPLOY_END) {
            for (int i = 0; i < 30; i++) {
                level.addParticle(ModRegistry.EXTRACTION_SPARK.get(), tip.x, tip.y, tip.z,
                        (rand.nextDouble() - 0.5D) * 0.4D, 0.2D + rand.nextDouble() * 0.5D, (rand.nextDouble() - 0.5D) * 0.4D);
            }
        }

        // colonne de fumée épaisse qui monte très haut
        level.addParticle(ModRegistry.EXTRACTION_SMOKE.get(),
                tip.x + (rand.nextDouble() - 0.5D) * 0.15D, tip.y, tip.z + (rand.nextDouble() - 0.5D) * 0.15D,
                (rand.nextDouble() - 0.5D) * 0.02D, 0.22D + rand.nextDouble() * 0.05D, (rand.nextDouble() - 0.5D) * 0.02D);
        // étincelles crachées par le fumigène
        for (int i = 0; i < 2; i++) {
            level.addParticle(ModRegistry.EXTRACTION_SPARK.get(), tip.x, tip.y, tip.z,
                    (rand.nextDouble() - 0.5D) * 0.14D, 0.1D + rand.nextDouble() * 0.18D, (rand.nextDouble() - 0.5D) * 0.14D);
        }
        spawnOutlineParticles();
    }

    /** Limite de la zone au sol, le long du contour (qui tourne doucement). */
    private void spawnOutlineParticles() {
        Level level = this.level();
        if (this.tickCount % 6 == 0) {
            ZoneShape shape = getZone();
            int offset = (this.tickCount / 6) % 3;
            for (int i = offset; i < ZoneShape.OUTLINE_SEGMENTS; i += 3) {
                double angle = Math.PI * 2.0D * i / ZoneShape.OUTLINE_SEGMENTS;
                double d = shape.outlineDistance(i);
                level.addParticle(ModRegistry.EXTRACTION_RING.get(),
                        getX() + Math.cos(angle) * d, shape.outlineGround(i) + 0.1D, getZ() + Math.sin(angle) * d,
                        0.0D, 0.015D, 0.0D);
            }
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_EXTRACTED) {
            spawnBurst(true);
        } else {
            super.handleEntityEvent(id);
        }
    }

    /** Gerbe d'étincelles quand un joueur est extrait, bouffée de fumée quand la zone se ferme. */
    private void spawnBurst(boolean extracted) {
        RandomSource rand = this.random;
        Level level = this.level();
        Vec3 tip = isFixed() ? position().add(0.0D, 0.5D, 0.0D) : position().add(TerminalGeometry.flareTipOffset(getYRot()));
        if (extracted) {
            for (int i = 0; i < 60; i++) {
                level.addParticle(ModRegistry.EXTRACTION_SPARK.get(), tip.x, tip.y, tip.z,
                        (rand.nextDouble() - 0.5D) * 0.5D, 0.4D + rand.nextDouble() * 0.8D, (rand.nextDouble() - 0.5D) * 0.5D);
            }
        }
        for (int i = 0; i < (extracted ? 20 : 12); i++) {
            level.addParticle(ModRegistry.EXTRACTION_SMOKE.get(),
                    tip.x + (rand.nextDouble() - 0.5D) * 0.6D, tip.y, tip.z + (rand.nextDouble() - 0.5D) * 0.6D,
                    (rand.nextDouble() - 0.5D) * 0.08D, 0.05D + rand.nextDouble() * (extracted ? 0.3D : 0.06D), (rand.nextDouble() - 0.5D) * 0.08D);
        }
    }

    // ------------------------------------------------------------------ divers

    /** Les zones de fusée ne survivent pas à un redémarrage ; les zones fixes d'admin, si. */
    @Override
    public boolean shouldBeSaved() {
        return isFixed() && super.shouldBeSaved();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 256.0D * 256.0D;
    }

    /** Le mur de la zone dépasse largement la hitbox : on l'inclut pour le culling. */
    @Override
    public AABB getBoundingBoxForCulling() {
        float r = getRadius();
        return getBoundingBox().inflate(r, 2.0D, r);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Sauvegarde des zones fixes : dans ExtractionTerminalEntityServeur (le client n'enregistre rien). */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
