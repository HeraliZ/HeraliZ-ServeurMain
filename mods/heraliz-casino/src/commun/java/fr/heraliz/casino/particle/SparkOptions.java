package fr.heraliz.casino.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/**
 * Particule lumineuse HD coloree.
 * kind : 0 etincelle, 1 point lumineux, 2 confetti, 3 bulle, 4 braise, 5 fumee, 6 flamme, 7 flocon, 8 feuille,
 * 9 goutte, 10 volute, 11 papier, 12 mouche, 13 eclat (memes valeurs que client.render.scene.FxSink).
 */
public record SparkOptions(float red, float green, float blue, float size, int kind, int life) implements ParticleOptions {
    public static final int SPARK = 0;
    public static final int DOT = 1;
    public static final int CONFETTI = 2;
    public static final int BUBBLE = 3;
    public static final int EMBER = 4;
    public static final int SMOKE = 5;
    public static final int FLAME = 6;
    public static final int SNOW = 7;
    public static final int LEAF = 8;
    public static final int DROP = 9;
    public static final int WISP = 10;
    public static final int PAPER = 11;
    public static final int FLY = 12;
    public static final int SHARD = 13;

    public static final Codec<SparkOptions> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("r").forGetter(SparkOptions::red),
            Codec.FLOAT.fieldOf("g").forGetter(SparkOptions::green),
            Codec.FLOAT.fieldOf("b").forGetter(SparkOptions::blue),
            Codec.FLOAT.fieldOf("size").forGetter(SparkOptions::size),
            Codec.INT.fieldOf("kind").forGetter(SparkOptions::kind),
            Codec.INT.fieldOf("life").forGetter(SparkOptions::life)
    ).apply(i, SparkOptions::new));

    @SuppressWarnings("deprecation")
    public static final ParticleOptions.Deserializer<SparkOptions> DESERIALIZER = new ParticleOptions.Deserializer<SparkOptions>() {
        @Override
        public SparkOptions fromCommand(ParticleType<SparkOptions> type, StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            float r = reader.readFloat();
            reader.expect(' ');
            float g = reader.readFloat();
            reader.expect(' ');
            float b = reader.readFloat();
            reader.expect(' ');
            float size = reader.readFloat();
            reader.expect(' ');
            int kind = reader.readInt();
            reader.expect(' ');
            int life = reader.readInt();
            return new SparkOptions(r, g, b, size, kind, life);
        }

        @Override
        public SparkOptions fromNetwork(ParticleType<SparkOptions> type, FriendlyByteBuf buf) {
            return new SparkOptions(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt());
        }
    };

    public static SparkOptions of(int rgb, float size, int kind, int life) {
        return new SparkOptions(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, size, kind, life);
    }

    @Override
    public ParticleType<?> getType() {
        return ModRegistry.SPARK.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeFloat(red);
        buf.writeFloat(green);
        buf.writeFloat(blue);
        buf.writeFloat(size);
        buf.writeVarInt(kind);
        buf.writeVarInt(life);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %.3f %.3f %.3f %.3f %d %d",
                ForgeRegistries.PARTICLE_TYPES.getKey(getType()), red, green, blue, size, kind, life);
    }
}
