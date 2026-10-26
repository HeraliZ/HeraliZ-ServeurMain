package fr.heraliz.decors.client;

import fr.heraliz.decors.HeralizDecors;
import fr.heraliz.decors.Particules;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Rendu des particules de HeraliZ Décors. Peu de particules, longues et douces : la brume vit 8 à 13 secondes, la
 * brume basse glisse sur l'eau 12 à 18 secondes, la poussière flotte lentement ; les particules lumineuses (poussière,
 * poussière dorée, éclats, lucioles) ignorent l'obscurité.
 */
@Mod.EventBusSubscriber(modid = HeralizDecors.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ParticulesClient {
    private ParticulesClient() {}

    @SubscribeEvent
    public static void enregistrer(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(Particules.BRUME.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Douce(l, x, y, z, vx, vy, vz, s, Genre.BRUME));
        e.registerSpriteSet(Particules.POUSSIERE.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Douce(l, x, y, z, vx, vy, vz, s, Genre.POUSSIERE));
        e.registerSpriteSet(Particules.ECLAT.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Douce(l, x, y, z, vx, vy, vz, s, Genre.ECLAT));
        e.registerSpriteSet(Particules.EMBRUN.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Douce(l, x, y, z, vx, vy, vz, s, Genre.EMBRUN));
        e.registerSpriteSet(Particules.BRUME_BASSE.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Douce(l, x, y, z, vx, vy, vz, s, Genre.BRUME_BASSE));
        e.registerSpriteSet(Particules.LUCIOLE.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Douce(l, x, y, z, vx, vy, vz, s, Genre.LUCIOLE));
        e.registerSpriteSet(Particules.POUSSIERE_DOREE.get(), s -> (t, l, x, y, z, vx, vy, vz) -> new Douce(l, x, y, z, vx, vy, vz, s,
                Genre.POUSSIERE_DOREE));
    }

    enum Genre { BRUME, BRUME_BASSE, POUSSIERE, POUSSIERE_DOREE, ECLAT, EMBRUN, LUCIOLE }

    static final class Douce extends TextureSheetParticle {
        private final Genre genre;
        private final float alphaMax;
        private final float tailleBase;
        private final float phase;

        Douce(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, Genre genre) {
            super(l, x, y, z, vx, vy, vz);
            this.genre = genre;
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.pickSprite(sprites);
            this.hasPhysics = genre == Genre.EMBRUN;
            this.phase = random.nextFloat() * 6.28f;
            switch (genre) {
                case BRUME -> {
                    lifetime = 160 + random.nextInt(100);
                    tailleBase = 1.4f + random.nextFloat() * 1.4f;
                    alphaMax = 0.10f + random.nextFloat() * 0.10f;
                    gravity = 0;
                    friction = 0.995f;
                    float g = 0.80f + random.nextFloat() * 0.1f;
                    setColor(g, g + 0.05f, g + 0.12f);
                    roll = random.nextFloat() * 6.28f;
                    oRoll = roll;
                }
                case POUSSIERE -> {
                    lifetime = 120 + random.nextInt(140);
                    tailleBase = 0.025f + random.nextFloat() * 0.035f;
                    alphaMax = 0.65f + random.nextFloat() * 0.3f;
                    gravity = 0;
                    friction = 0.98f;
                    setColor(1.0f, 0.93f, 0.78f);
                }
                case BRUME_BASSE -> {
                    lifetime = 240 + random.nextInt(120);
                    tailleBase = 2.4f + random.nextFloat() * 2.2f;
                    alphaMax = 0.05f + random.nextFloat() * 0.06f;
                    gravity = 0;
                    friction = 0.998f;
                    float g = 0.82f + random.nextFloat() * 0.06f;
                    setColor(g, g + 0.04f, g + 0.07f);
                    roll = random.nextFloat() * 6.28f;
                    oRoll = roll;
                }
                case POUSSIERE_DOREE -> {
                    lifetime = 140 + random.nextInt(120);
                    tailleBase = 0.03f + random.nextFloat() * 0.035f;
                    alphaMax = 0.7f + random.nextFloat() * 0.3f;
                    gravity = 0;
                    friction = 0.985f;
                    setColor(1.0f, 0.82f, 0.46f);
                }
                case LUCIOLE -> {
                    lifetime = 110 + random.nextInt(90);
                    tailleBase = 0.045f + random.nextFloat() * 0.035f;
                    alphaMax = 0.95f;
                    gravity = 0;
                    friction = 0.93f;
                    setColor(0.78f, 1.0f, 0.36f);
                }
                case ECLAT -> {
                    lifetime = 18 + random.nextInt(22);
                    tailleBase = 0.05f + random.nextFloat() * 0.07f;
                    alphaMax = 0.95f;
                    gravity = 0;
                    friction = 0.9f;
                    setColor(0.75f, 0.95f, 1.0f);
                }
                default -> {
                    lifetime = 14 + random.nextInt(14);
                    tailleBase = 0.06f + random.nextFloat() * 0.08f;
                    alphaMax = 0.7f;
                    gravity = 0.45f;
                    friction = 0.96f;
                    setColor(0.85f, 0.93f, 1.0f);
                }
            }
            quadSize = tailleBase;
            alpha = 0;
        }

        @Override
        public void tick() {
            super.tick();
            float t = (float) age / lifetime;
            switch (genre) {
                case BRUME -> {
                    alpha = alphaMax * Mth.sin((float) Math.PI * t);
                    quadSize = tailleBase * (0.85f + 0.4f * t);
                    oRoll = roll;
                    roll += 0.002f;
                }
                case BRUME_BASSE -> {
                    alpha = alphaMax * Mth.sin((float) Math.PI * t);
                    quadSize = tailleBase * (0.9f + 0.3f * t);
                    oRoll = roll;
                    roll += 0.001f;
                }
                case POUSSIERE, POUSSIERE_DOREE -> alpha = alphaMax * Mth.sin((float) Math.PI * t) * (0.7f + 0.3f * Mth.sin(age * 0.2f + phase));
                case LUCIOLE -> {                                  // vole au hasard et clignote doucement
                    xd += (random.nextFloat() - 0.5f) * 0.006f;
                    yd += (random.nextFloat() - 0.5f) * 0.004f;
                    zd += (random.nextFloat() - 0.5f) * 0.006f;
                    float c = Math.max(0, Mth.sin(age * 0.12f + phase));
                    alpha = alphaMax * c * c * Mth.sin((float) Math.PI * t);
                }
                case ECLAT -> {
                    alpha = alphaMax * (1 - t);
                    quadSize = tailleBase * (1 - 0.5f * t);
                }
                default -> alpha = alphaMax * (1 - t);
            }
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partial) {
            if (genre == Genre.POUSSIERE || genre == Genre.ECLAT || genre == Genre.POUSSIERE_DOREE || genre == Genre.LUCIOLE) {
                return 0xF000F0;
            }
            return super.getLightColor(partial);
        }
    }

    @SuppressWarnings("unused")
    private static ParticleProvider<SimpleParticleType> rien() {
        return (t, l, x, y, z, vx, vy, vz) -> (Particle) null;
    }
}
