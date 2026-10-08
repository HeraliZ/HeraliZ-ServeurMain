package fr.heraliz.airstrike.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.heraliz.airstrike.registry.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Locale;

/**
 * Parameters of the big smoke puffs: colour, size (blocks), lifetime (ticks), vertical drift and opacity.
 */
public record SmokeParticleOptions(float r, float g, float b, float size, int lifetime, float rise, float alpha)
        implements ParticleOptions {

    public static Codec<SmokeParticleOptions> codec(ParticleType<SmokeParticleOptions> type) {
        return RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("r").forGetter(SmokeParticleOptions::r),
                Codec.FLOAT.fieldOf("g").forGetter(SmokeParticleOptions::g),
                Codec.FLOAT.fieldOf("b").forGetter(SmokeParticleOptions::b),
                Codec.FLOAT.fieldOf("size").forGetter(SmokeParticleOptions::size),
                Codec.INT.fieldOf("lifetime").forGetter(SmokeParticleOptions::lifetime),
                Codec.FLOAT.fieldOf("rise").forGetter(SmokeParticleOptions::rise),
                Codec.FLOAT.fieldOf("alpha").forGetter(SmokeParticleOptions::alpha)
        ).apply(i, SmokeParticleOptions::new));
    }

    @SuppressWarnings("deprecation")
    public static final ParticleOptions.Deserializer<SmokeParticleOptions> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public SmokeParticleOptions fromCommand(ParticleType<SmokeParticleOptions> type, StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            float r = reader.readFloat();
            reader.expect(' ');
            float g = reader.readFloat();
            reader.expect(' ');
            float b = reader.readFloat();
            reader.expect(' ');
            float size = reader.readFloat();
            reader.expect(' ');
            int life = reader.readInt();
            return new SmokeParticleOptions(r, g, b, size, life, 0.02f, 0.9f);
        }

        @Override
        public SmokeParticleOptions fromNetwork(ParticleType<SmokeParticleOptions> type, FriendlyByteBuf buf) {
            return new SmokeParticleOptions(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readVarInt(), buf.readFloat(), buf.readFloat());
        }
    };

    public static SmokeParticleOptions of(int rgb, float size, int lifetime, float rise, float alpha) {
        return new SmokeParticleOptions(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f,
                size, lifetime, rise, alpha);
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticles.SMOKE.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeFloat(r);
        buf.writeFloat(g);
        buf.writeFloat(b);
        buf.writeFloat(size);
        buf.writeVarInt(lifetime);
        buf.writeFloat(rise);
        buf.writeFloat(alpha);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "airstrike:smoke %.2f %.2f %.2f %.2f %d", r, g, b, size, lifetime);
    }
}
