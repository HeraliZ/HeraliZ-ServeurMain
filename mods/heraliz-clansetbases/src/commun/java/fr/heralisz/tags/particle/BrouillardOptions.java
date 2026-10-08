package fr.heralisz.tags.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.heralisz.tags.registry.Particules;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/**
 * Options de la particule « brouillard de peinture » : la case du tag dans l'atlas (le client en tire la couleur
 * dominante, calculee une fois au chargement de l'atlas) et une couleur de repli RGB choisie par le serveur (couleur
 * de la rarete) quand l'atlas n'est pas lisible cote client.
 *
 * @param cas    case du tag dans l'atlas (0..63), -1 = pas de tag (couleur de repli seule)
 * @param repli  couleur RGB de repli
 */
public record BrouillardOptions(int cas, int repli) implements ParticleOptions {

    public static final Codec<BrouillardOptions> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("cas").forGetter(BrouillardOptions::cas),
            Codec.INT.fieldOf("repli").forGetter(BrouillardOptions::repli)
    ).apply(i, BrouillardOptions::new));

    @SuppressWarnings("deprecation")
    public static final ParticleOptions.Deserializer<BrouillardOptions> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public BrouillardOptions fromCommand(ParticleType<BrouillardOptions> type, StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            int cas = reader.readInt();
            reader.expect(' ');
            int repli = reader.readInt();
            return new BrouillardOptions(cas, repli);
        }

        @Override
        public BrouillardOptions fromNetwork(ParticleType<BrouillardOptions> type, FriendlyByteBuf buf) {
            return new BrouillardOptions(buf.readVarInt(), buf.readInt());
        }
    };

    @Override
    public ParticleType<?> getType() {
        return Particules.BROUILLARD.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buf) {
        buf.writeVarInt(cas);
        buf.writeInt(repli);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %d %d", ForgeRegistries.PARTICLE_TYPES.getKey(getType()), cas, repli);
    }
}
