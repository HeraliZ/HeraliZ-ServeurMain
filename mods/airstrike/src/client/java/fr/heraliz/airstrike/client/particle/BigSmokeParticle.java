package fr.heraliz.airstrike.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.airstrike.client.CloudFog;
import fr.heraliz.airstrike.particle.SmokeBudget;
import fr.heraliz.airstrike.particle.SmokeParticleOptions;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Large soft puff used by smoke screens, gas, explosions and trails. */
public class BigSmokeParticle extends TextureSheetParticle {
    private final float baseSize;
    private final float rise;
    private final float maxAlpha;
    private final float spin;
    /** Light at the puff, refreshed a few times per second instead of every frame (puffs drift slowly). */
    private int light = -1;

    protected BigSmokeParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                               SmokeParticleOptions o, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.rCol = o.r();
        this.gCol = o.g();
        this.bCol = o.b();
        this.baseSize = o.size();
        this.quadSize = o.size() * 0.5f;
        this.lifetime = Math.max(4, (int) (o.lifetime() * (0.85f + random.nextFloat() * 0.3f)));
        this.rise = o.rise();
        this.maxAlpha = o.alpha();
        this.alpha = 0f;
        this.gravity = 0f;
        this.friction = 0.96f;
        this.hasPhysics = false;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        this.spin = (random.nextFloat() - 0.5f) * 0.02f;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        SmokeBudget.count();
        yd += rise * 0.05;
        xd *= friction;
        yd *= friction;
        zd *= friction;
        move(xd, yd, zd);
        roll += spin;
        float t = age / (float) lifetime;
        float grow = 1f - (1f - t) * (1f - t);
        quadSize = baseSize * (0.45f + 0.75f * grow);
        float in = Mth.clamp(t / 0.08f, 0f, 1f);
        float out = Mth.clamp((1f - t) / 0.55f, 0f, 1f);
        alpha = maxAlpha * in * out;
        if (light < 0 || (age & 7) == 0) light = super.getLightColor(0f);
    }

    @Override
    protected int getLightColor(float partialTicks) {
        return light < 0 ? super.getLightColor(partialTicks) : light;
    }

    /**
     * Big puffs cost a lot of fill rate once the camera is among them: the ones wrapping the camera fade out (the
     * cloud fog takes over, see CloudFog) and the ones lost in that fog are skipped.
     */
    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        double dx = Mth.lerp(partialTicks, xo, x) - cam.x;
        double dy = Mth.lerp(partialTicks, yo, y) - cam.y;
        double dz = Mth.lerp(partialTicks, zo, z) - cam.z;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float vis = CloudFog.puffVisibility;
        if (vis <= 0.02f) return;
        float q = getQuadSize(partialTicks);
        if (dist - q * 0.5f > CloudFog.cullDistance) return;
        float near = Mth.clamp((dist - q * (0.35f + CloudFog.nearFade)) / (q * 0.8f), 0f, 1f);
        if (near <= 0.02f) return;
        float a = alpha;
        alpha = a * vis * near * near * (3f - 2f * near);
        if (alpha > 0.004f) super.render(buffer, camera, partialTicks);
        alpha = a;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ModParticleRenderTypes.SOFT;
    }

    public static class Provider implements ParticleProvider<SmokeParticleOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SmokeParticleOptions o, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new BigSmokeParticle(level, x, y, z, vx, vy, vz, o, sprites);
        }
    }
}
