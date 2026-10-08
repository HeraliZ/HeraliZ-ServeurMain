package fr.heralisz.clans.territory.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Flamme de la fusee de signalisation : point lumineux rouge-orange additif, pleine lumiere, monte vite puis
 * ralentit, scintille, retrecit et s'eteint. Deux par tick pendant 20 s = la colonne du signal.
 */
@OnlyIn(Dist.CLIENT)
public class FlammeParticle extends TextureSheetParticle {
    /** Melange additif : la flamme brille vraiment. */
    public static final ParticleRenderType ADDITIF = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "heralisz_clans:additif";
        }
    };

    private final float taille0, scintille;

    protected FlammeParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.lifetime = 50 + random.nextInt(25);
        this.gravity = 0.02f;
        this.friction = 0.992f;
        this.hasPhysics = false;
        float n = random.nextFloat();
        this.rCol = 1f;
        this.gCol = 0.25f + 0.25f * n;
        this.bCol = 0.08f + 0.1f * n;
        this.taille0 = 0.45f + random.nextFloat() * 0.25f;
        this.quadSize = taille0;
        this.scintille = random.nextFloat() * 6.2831f;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float v = (float) age / lifetime;
        alpha = v < 0.08f ? v / 0.08f : 1 - Math.max(0, (v - 0.5f) / 0.5f);
    }

    @Override
    public float getQuadSize(float partialTick) {
        float a = age + partialTick, v = a / lifetime;
        return taille0 * (1f - 0.5f * v) * (0.85f + 0.15f * (float) Math.sin(a * 0.9f + scintille));
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ADDITIF;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new FlammeParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
