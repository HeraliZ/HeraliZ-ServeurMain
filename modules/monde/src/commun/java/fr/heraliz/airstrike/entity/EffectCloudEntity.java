package fr.heraliz.airstrike.entity;

import fr.heraliz.airstrike.particle.SmokeBudget;
import fr.heraliz.airstrike.particle.SmokeParticleOptions;
import fr.heraliz.airstrike.registry.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Invisible entity carrying a lingering area effect: smoke screen, toxic gas, napalm fire or signal smoke.
 * The server applies the gameplay effects, clients emit the particles.
 */
public class EffectCloudEntity extends Entity {
    public static final int SMOKE = 0;
    public static final int GAS = 1;
    public static final int NAPALM = 2;
    public static final int MARKER = 3;

    /** Helmets protecting against the toxic gas (fill it with your gas masks). */
    public static final TagKey<Item> GAS_MASKS = TagKey.create(Registries.ITEM, new ResourceLocation("airstrike", "gas_masks"));

    static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(EffectCloudEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(EffectCloudEntity.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Integer> DURATION = SynchedEntityData.defineId(EffectCloudEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(EffectCloudEntity.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(EffectCloudEntity.class, EntityDataSerializers.INT);

    int age;

    /**
     * Particle quality of the client ({@code client.fx.FxQuality}), plugged in by PartieClient
     * (docs/METHODE_CLIENT_SERVEUR.md §6). Never null on a physical client; null elsewhere (no particles).
     */
    public interface QualiteFx {
        float gameplay(double x, double y, double z);

        float at(double x, double y, double z);

        float grow(float q);
    }

    public static volatile QualiteFx QUALITE_FX;
    /** Clouds currently loaded on this client: the fog lookup is skipped when there are none. */
    private static int clientClouds;

    public EffectCloudEntity(EntityType<? extends EffectCloudEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (level().isClientSide) clientClouds++;
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (level().isClientSide) clientClouds = Math.max(0, clientClouds - 1);
    }

    public static boolean anyOnClient() {
        return clientClouds > 0;
    }

    /** Leaving the world (the client level is dropped without removing its entities one by one). */
    public static void resetClientCount() {
        clientClouds = 0;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(KIND, SMOKE);
        this.entityData.define(RADIUS, 5.0f);
        this.entityData.define(DURATION, 600);
        this.entityData.define(COLOR, 0xDDDDDD);
        this.entityData.define(AGE, 0);
    }

    public int getKind() {
        return entityData.get(KIND);
    }

    public float getRadius() {
        return entityData.get(RADIUS);
    }

    public int getDuration() {
        return entityData.get(DURATION);
    }

    public int getColor() {
        return entityData.get(COLOR);
    }

    public int getAge() {
        return age;
    }

    /** 0..1 intensity: quick fade-in, long plateau, fade-out over the last 20%. */
    public float intensity() {
        float d = Math.max(1, getDuration());
        float t = age / d;
        float in = getKind() == GAS ? Mth.clamp(age / 60f, 0f, 1f) : Mth.clamp(age / 10f, 0f, 1f);
        float out = Mth.clamp((1f - t) / 0.2f, 0f, 1f);
        return in * out;
    }

    /** Current effective radius (gas slowly spreads). */
    public float effectiveRadius() {
        float r = getRadius();
        if (getKind() == GAS) return r * (0.45f + 0.55f * Mth.clamp(age / 120f, 0f, 1f));
        return r;
    }

    private static float smoothstep(double a, double b, double x) {
        double t = Mth.clamp((x - a) / (b - a), 0.0, 1.0);
        return (float) (t * t * (3.0 - 2.0 * t));
    }

    /** How deep a point is inside the visible cloud, 0..1 (client fog). */
    public float fogDensityAt(Vec3 p) {
        int kind = getKind();
        if (kind != SMOKE && kind != GAS) return 0f;
        double dx = p.x - getX(), dz = p.z - getZ();
        double h = Math.sqrt(dx * dx + dz * dz) / Math.max(0.5f, effectiveRadius());
        double dy = p.y - getY();
        double top = kind == GAS ? 3.4 : 6.0;
        float horizontal = 1f - smoothstep(0.55, 1.1, h);
        float vertical = (1f - smoothstep(top - 1.5, top + 1.0, dy)) * smoothstep(-2.5, -0.8, dy);
        return horizontal * vertical * intensity();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            tickServeur();
        } else {
            int synced = entityData.get(AGE);
            if (Math.abs(synced - age) > 25) age = synced;
            age++;
            clientParticles();
        }
    }

    /** Server tick (lifetime, gameplay effects, napalm fire, hiss): EffectCloudEntityServeur. Neutral here. */
    void tickServeur() {
    }

    // ------------------------------------------------------------------------------------------------------------
    // Client visuals (Level#addParticle is a no-op on the server, our particle types ignore the distance limiter)

    void clientParticles() {
        RandomSource rnd = random;
        float k = intensity();
        if (k <= 0.01f) return;
        // fewer, bigger puffs far away (client only class, never loaded on a server); smoke screens and gas hide the
        // same whatever the particle setting, the napalm fire follows it
        boolean screen = getKind() != NAPALM;
        QualiteFx fx = QUALITE_FX;
        if (fx == null) return;
        float q = screen ? fx.gameplay(getX(), getY(), getZ()) : fx.at(getX(), getY(), getZ());
        float grow = fx.grow(q);
        float budget = SmokeBudget.factor() * q;
        float r = effectiveRadius();
        int color = getColor();
        switch (getKind()) {
            case SMOKE -> {
                // few big puffs: the cloud stays opaque while costing a fraction of the fill rate
                if (rnd.nextFloat() < 0.42f * budget) {
                    Vec3 p = randomInDisc(rnd, r * 0.9f);
                    float size = (4.2f + rnd.nextFloat() * 2.6f) * grow;
                    int grey = 0xC4 + rnd.nextInt(0x24);
                    int c = (grey << 16) | (grey << 8) | Math.min(255, grey + 6);
                    level().addParticle(SmokeParticleOptions.of(c, size, 200 + rnd.nextInt(80), 0.01f, 0.95f * k),
                            p.x, getY() + 0.6 + rnd.nextFloat() * 3.6, p.z, (rnd.nextFloat() - 0.5f) * 0.03f, 0, (rnd.nextFloat() - 0.5f) * 0.03f);
                }
                if (age < 300 && age % 3 == 0) {
                    // dense jet from the canister itself
                    level().addParticle(SmokeParticleOptions.of(0xF0F0F0, 1.5f, 45, 0.12f, 0.95f),
                            getX(), getY() + 0.3, getZ(), (rnd.nextFloat() - 0.5f) * 0.12f, 0.18f, (rnd.nextFloat() - 0.5f) * 0.12f);
                }
            }
            case GAS -> {
                if (rnd.nextFloat() < 0.36f * budget) {
                    Vec3 p = randomInDisc(rnd, r);
                    float size = (3.6f + rnd.nextFloat() * 3.0f) * grow;
                    int c = lerpColor(color, 0x7C8A3A, rnd.nextFloat() * 0.6f);
                    level().addParticle(SmokeParticleOptions.of(c, size, 150 + rnd.nextInt(70), 0.003f, 0.78f * k),
                            p.x, getY() + 0.3 + rnd.nextFloat() * 1.2, p.z, (rnd.nextFloat() - 0.5f) * 0.02f, 0, (rnd.nextFloat() - 0.5f) * 0.02f);
                }
                if (age < 240 && age % 4 == 0) {
                    level().addParticle(SmokeParticleOptions.of(0xC9CF6A, 1.2f, 40, 0.08f, 0.8f),
                            getX(), getY() + 0.3, getZ(), (rnd.nextFloat() - 0.5f) * 0.1f, 0.12f, (rnd.nextFloat() - 0.5f) * 0.1f);
                }
            }
            case NAPALM -> {
                for (int i = 0, n = Math.max(1, Math.round(4 * q)); i < n; i++) {
                    Vec3 p = randomInDisc(rnd, r);
                    level().addParticle(ParticleTypes.FLAME, p.x, getY() + 0.1 + rnd.nextFloat() * 0.6, p.z,
                            (rnd.nextFloat() - 0.5f) * 0.02f, 0.05f + rnd.nextFloat() * 0.08f, (rnd.nextFloat() - 0.5f) * 0.02f);
                }
                if (rnd.nextFloat() < 0.6f * k * q) {
                    Vec3 p = randomInDisc(rnd, r * 0.8f);
                    level().addParticle(ModParticles.FIREBALL.get(), p.x, getY() + 0.5 + rnd.nextFloat(), p.z, 0, 0.04, 0);
                }
                if (rnd.nextFloat() < 0.35f * k * budget) {
                    Vec3 p = randomInDisc(rnd, r * 0.7f);
                    level().addParticle(SmokeParticleOptions.of(0x1E1B19, (3.0f + rnd.nextFloat() * 2f) * grow, 120, 0.09f, 0.85f),
                            p.x, getY() + 1.5, p.z, 0, 0.02, 0);
                }
                if (rnd.nextFloat() < 0.125f * q) {
                    Vec3 p = randomInDisc(rnd, r);
                    level().addParticle(ParticleTypes.LAVA, p.x, getY() + 0.2, p.z, 0, 0, 0);
                }
            }
            case MARKER -> {
                if (age % 2 == 0) {
                    float size = 1.2f + rnd.nextFloat() * 0.8f;
                    level().addParticle(SmokeParticleOptions.of(color, size, 90 + rnd.nextInt(40), 0.11f, 0.95f * k),
                            getX() + (rnd.nextFloat() - 0.5f) * 0.3, getY() + 0.2, getZ() + (rnd.nextFloat() - 0.5f) * 0.3,
                            0.012f + (rnd.nextFloat() - 0.5f) * 0.02f, 0.1f, 0.006f + (rnd.nextFloat() - 0.5f) * 0.02f);
                }
                if (age % 5 == 0) {
                    level().addParticle(ParticleTypes.FLAME, getX(), getY() + 0.1, getZ(), 0, 0.02, 0);
                }
            }
            default -> {
            }
        }
    }

    Vec3 randomInDisc(RandomSource rnd, float r) {
        double a = rnd.nextDouble() * Math.PI * 2;
        double d = r * Math.sqrt(rnd.nextDouble());
        return new Vec3(getX() + Math.cos(a) * d, getY(), getZ() + Math.sin(a) * d);
    }

    private static int lerpColor(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    // ------------------------------------------------------------------------------------------------------------

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSqr) {
        return distSqr < 256 * 256;
    }

    // Save data: EffectCloudEntityServeur (server part). Neutral here (abstract in Entity).
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
