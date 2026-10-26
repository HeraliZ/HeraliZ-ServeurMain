package fr.heraliz.airstrike.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Flat ring expanding along the ground. */
public class ShockwaveParticle extends TextureSheetParticle {
    private float maxSize;

    protected ShockwaveParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.lifetime = 11;
        this.maxSize = 12f;
        this.hasPhysics = false;
        this.gravity = 0f;
        pickSprite(sprites);
    }

    @Override
    public Particle scale(float f) {
        maxSize *= f;
        return this;
    }

    @Override
    public void render(VertexConsumer vc, Camera camera, float pt) {
        Vec3 cam = camera.getPosition();
        float x = (float) (Mth.lerp(pt, xo, this.x) - cam.x);
        float y = (float) (Mth.lerp(pt, yo, this.y) - cam.y);
        float z = (float) (Mth.lerp(pt, zo, this.z) - cam.z);
        float t = Mth.clamp((age + pt) / lifetime, 0f, 1f);
        float r = maxSize * (1f - (1f - t) * (1f - t) * (1f - t));
        float a = 0.8f * (1f - t);
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = 0xF000F0;
        // top face
        vc.vertex(x - r, y, z - r).uv(u0, v0).color(1f, 0.93f, 0.85f, a).uv2(light).endVertex();
        vc.vertex(x - r, y, z + r).uv(u0, v1).color(1f, 0.93f, 0.85f, a).uv2(light).endVertex();
        vc.vertex(x + r, y, z + r).uv(u1, v1).color(1f, 0.93f, 0.85f, a).uv2(light).endVertex();
        vc.vertex(x + r, y, z - r).uv(u1, v0).color(1f, 0.93f, 0.85f, a).uv2(light).endVertex();
        // bottom face
        vc.vertex(x + r, y, z - r).uv(u1, v0).color(1f, 0.93f, 0.85f, a).uv2(light).endVertex();
        vc.vertex(x + r, y, z + r).uv(u1, v1).color(1f, 0.93f, 0.85f, a).uv2(light).endVertex();
        vc.vertex(x - r, y, z + r).uv(u0, v1).color(1f, 0.93f, 0.85f, a).uv2(light).endVertex();
        vc.vertex(x - r, y, z - r).uv(u0, v0).color(1f, 0.93f, 0.85f, a).uv2(light).endVertex();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ModParticleRenderTypes.SOFT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new ShockwaveParticle(level, x, y, z, sprites);
        }
    }
}
