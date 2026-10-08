package fr.heraliz.crates.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heraliz.crates.block.CrateBlock;
import fr.heraliz.crates.block.CratePartBlock;
import fr.heraliz.crates.particle.SparkOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Particule HD coloree des caisses : etincelle, point lumineux, confetti, bulle, braise, fumee, flamme, flocon,
 * feuille, goutte, volute, papier, mouche, eclat. Les particules lumineuses sont additives, les autres
 * (fumee, confettis, feuilles...) sont melangees et eclairees par le monde.
 * La physique reproduit celle de l'apercu hors-jeu (tools/preview) : gravite, frottement 0.96, balancement.
 */
public class SparkParticle extends TextureSheetParticle {
    /** Melange additif : les particules brillent vraiment. */
    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
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
            return "heralizcrates:additive";
        }
    };

    /** Nombre de textures dans particles/spark.json. */
    private static final int SPRITE_COUNT = 16;

    private final SpriteSet sprites;
    private final int kind;
    private final float baseSize;
    private float spin;
    private final float twinkle;
    /** Retombe et se pose sur les blocs (hors blocs des caisses). */
    private final boolean lands;
    private boolean landed;

    protected SparkParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                            SparkOptions o, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.kind = o.kind();
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.rCol = o.red();
        this.gCol = o.green();
        this.bCol = o.blue();
        this.baseSize = o.size();
        this.quadSize = o.size();
        this.lifetime = o.life() > 0 ? o.life() : 20 + this.random.nextInt(20);
        this.friction = 0.96F;
        this.hasPhysics = kind == SparkOptions.CONFETTI;
        this.lands = switch (kind) {
            case SparkOptions.DROP, SparkOptions.LEAF, SparkOptions.PAPER, SparkOptions.SNOW, SparkOptions.SHARD -> true;
            default -> false;
        };
        this.gravity = gravity(kind);
        this.roll = this.random.nextFloat() * 6.2831F;
        this.oRoll = this.roll;
        this.spin = (this.random.nextFloat() - 0.5F) * switch (kind) {
            case SparkOptions.CONFETTI -> 0.5F;
            case SparkOptions.LEAF, SparkOptions.PAPER, SparkOptions.SHARD -> 0.3F;
            case SparkOptions.SMOKE -> 0.06F;
            default -> 0.12F;
        };
        this.twinkle = this.random.nextFloat() * 6.2831F;
        pickSprite();
    }

    /** Gravite par type (meme table que l'apercu hors-jeu). */
    public static float gravity(int kind) {
        return switch (kind) {
            case SparkOptions.CONFETTI -> 0.25F;
            case SparkOptions.BUBBLE -> -0.03F;
            case SparkOptions.EMBER -> -0.015F;
            case SparkOptions.SMOKE -> -0.02F;
            case SparkOptions.FLAME -> -0.05F;
            case SparkOptions.SNOW -> 0.02F;
            case SparkOptions.LEAF -> 0.03F;
            case SparkOptions.DROP -> 0.5F;
            case SparkOptions.PAPER -> 0.06F;
            case SparkOptions.SHARD -> 0.3F;
            default -> 0F;
        };
    }

    private void pickSprite() {
        int index = switch (kind) {
            case SparkOptions.DOT, SparkOptions.WISP -> 4;
            case SparkOptions.CONFETTI -> 5;
            case SparkOptions.BUBBLE -> 6;
            case SparkOptions.EMBER -> 7;
            case SparkOptions.SMOKE -> 8;
            case SparkOptions.FLAME -> 9;
            case SparkOptions.SNOW -> 10;
            case SparkOptions.LEAF -> 11;
            case SparkOptions.DROP -> 12;
            case SparkOptions.PAPER -> 13;
            case SparkOptions.FLY -> 14;
            case SparkOptions.SHARD -> 15;
            default -> Math.min(3, (int) (4f * this.age / Math.max(1, this.lifetime)));
        };
        this.setSprite(this.sprites.get(index, SPRITE_COUNT - 1));
    }

    @Override
    public void tick() {
        if (!landed) {
            // balancement (flocons, feuilles, papiers) et vol erratique des mouches, en blocs/tick
            if (kind == SparkOptions.SNOW || kind == SparkOptions.LEAF || kind == SparkOptions.PAPER) {
                this.xd += Math.sin(this.age * 0.2F + twinkle) * 0.03 / 16.0;
                this.zd += Math.cos(this.age * 0.17F + twinkle) * 0.03 / 16.0;
            } else if (kind == SparkOptions.FLY) {
                this.xd += (this.random.nextFloat() - 0.5F) * 0.5F / 16F;
                this.yd += (this.random.nextFloat() - 0.5F) * 0.4F / 16F;
                this.zd += (this.random.nextFloat() - 0.5F) * 0.5F / 16F;
            }
        }
        super.tick();
        if (lands && !landed && this.yd < 0) land();
        if (landed) {
            this.xd = 0;
            this.yd = 0;
            this.zd = 0;
            this.spin = 0;
        }
        this.oRoll = this.roll;
        this.roll += this.spin;
        if (kind == SparkOptions.SPARK) {
            pickSprite();
        }
        if (kind == SparkOptions.CONFETTI && this.onGround) {
            this.spin = 0; // pose au sol
        }
        float life = (float) this.age / (float) this.lifetime;
        this.alpha = (life < 0.1F ? life / 0.1F : 1.0F - Math.max(0F, (life - 0.6F) / 0.4F)) * (kind == SparkOptions.SMOKE ? 0.55F : 1F);
    }

    /** Se pose sur le dessus d'un bloc solide (les blocs des caisses sont ignores : leur volume est invisible). */
    private void land() {
        BlockPos bp = BlockPos.containing(this.x, this.y, this.z);
        BlockState bs = this.level.getBlockState(bp);
        if (bs.getBlock() instanceof CrateBlock || bs.getBlock() instanceof CratePartBlock) return;
        VoxelShape shape = bs.getCollisionShape(this.level, bp);
        if (shape.isEmpty()) return;
        double top = bp.getY() + shape.max(Direction.Axis.Y);
        if (this.y < top) {
            this.setPos(this.x, top + 0.01, this.z);
            this.landed = true;
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        float life = ((float) this.age + partialTick) / (float) this.lifetime;
        float grow = life < 0.15F ? life / 0.15F : 1F;
        float k = switch (kind) {
            case SparkOptions.SPARK -> 0.75F + 0.25F * (float) Math.sin((this.age + partialTick) * 0.9F + twinkle);
            case SparkOptions.SMOKE -> 1F + life * 1.5F;
            case SparkOptions.FLAME -> 1F - life * 0.6F;
            default -> 1F;
        };
        return baseSize * grow * k;
    }

    private boolean blended() {
        return switch (kind) {
            case SparkOptions.CONFETTI, SparkOptions.SMOKE, SparkOptions.SNOW, SparkOptions.LEAF, SparkOptions.DROP,
                    SparkOptions.PAPER, SparkOptions.FLY -> true;
            default -> false;
        };
    }

    @Override
    public ParticleRenderType getRenderType() {
        return blended() ? ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT : ADDITIVE;
    }

    @Override
    public int getLightColor(float partialTick) {
        return blended() ? super.getLightColor(partialTick) : LightTexture.FULL_BRIGHT;
    }

    public static class Provider implements ParticleProvider<SparkOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SparkOptions options, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new SparkParticle(level, x, y, z, vx, vy, vz, options, sprites);
        }
    }
}
