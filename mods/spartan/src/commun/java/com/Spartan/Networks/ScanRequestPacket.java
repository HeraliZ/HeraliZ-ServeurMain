package com.Spartan.Networks;

import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * S->C : demande de contrôle d'intégrité. Contient les signatures à chercher
 * (classes, programmes, DLL) : elles restent dans la config du serveur et ne
 * sont jamais écrites dans le jar distribué aux joueurs.
 */
public class ScanRequestPacket {
    public static final int MAX_KNOWN_MIXINS = 8192;
    public static final int MAX_PROBES = 256;
    public static final int MAX_PATTERNS = 128;
    public static final int MAX_SAMPLES = 200;

    private final int scanId;
    private final int samples;
    private final boolean referenceKnown;
    private final long[] knownMixins;
    private final List<String> watchClasses;
    private final List<String> classProbes;
    private final List<String> processPatterns;
    private final List<String> nativePatterns;

    public ScanRequestPacket(int scanId, int samples, boolean referenceKnown, long[] knownMixins,
                             List<String> watchClasses, List<String> classProbes,
                             List<String> processPatterns, List<String> nativePatterns) {
        this.scanId = scanId;
        this.samples = samples;
        this.referenceKnown = referenceKnown;
        this.knownMixins = knownMixins;
        this.watchClasses = watchClasses;
        this.classProbes = classProbes;
        this.processPatterns = processPatterns;
        this.nativePatterns = nativePatterns;
    }

    public int scanId() {
        return scanId;
    }

    public int samples() {
        return samples;
    }

    /** Le serveur a un client de référence : les configs Mixin absentes de knownMixins sont inconnues. */
    public boolean referenceKnown() {
        return referenceKnown;
    }

    public long[] knownMixins() {
        return knownMixins;
    }

    /** Classes sensibles dont on veut connaître les Mixins appliqués (rendu, mouvement…). */
    public List<String> watchClasses() {
        return watchClasses;
    }

    public List<String> classProbes() {
        return classProbes;
    }

    public List<String> processPatterns() {
        return processPatterns;
    }

    public List<String> nativePatterns() {
        return nativePatterns;
    }

    public static ScanRequestPacket decode(FriendlyByteBuf buf) {
        int scanId = buf.readInt();
        int samples = buf.readVarInt();
        if (samples < 0 || samples > MAX_SAMPLES) {
            throw new DecoderException("Spartan: échantillons invalides (" + samples + ")");
        }
        boolean referenceKnown = buf.readBoolean();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_KNOWN_MIXINS) {
            throw new DecoderException("Spartan: liste Mixin invalide (" + n + ")");
        }
        long[] known = new long[n];
        for (int i = 0; i < n; i++) {
            known[i] = buf.readLong();
        }
        return new ScanRequestPacket(scanId, samples, referenceKnown, known, readList(buf, MAX_PROBES, 200),
                readList(buf, MAX_PROBES, 200), readList(buf, MAX_PATTERNS, 64), readList(buf, MAX_PATTERNS, 64));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.scanId);
        buf.writeVarInt(Math.max(0, Math.min(MAX_SAMPLES, this.samples)));
        buf.writeBoolean(this.referenceKnown);
        int n = Math.min(this.knownMixins.length, MAX_KNOWN_MIXINS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeLong(this.knownMixins[i]);
        }
        writeList(buf, this.watchClasses, MAX_PROBES, 200);
        writeList(buf, this.classProbes, MAX_PROBES, 200);
        writeList(buf, this.processPatterns, MAX_PATTERNS, 64);
        writeList(buf, this.nativePatterns, MAX_PATTERNS, 64);
    }

    private static List<String> readList(FriendlyByteBuf buf, int maxItems, int maxLen) {
        int n = buf.readVarInt();
        if (n < 0 || n > maxItems) {
            throw new DecoderException("Spartan: liste invalide (" + n + ")");
        }
        String[] out = new String[n];
        for (int i = 0; i < n; i++) {
            out[i] = buf.readUtf(maxLen);
        }
        return List.of(out);
    }

    private static void writeList(FriendlyByteBuf buf, List<String> list, int maxItems, int maxLen) {
        int n = Math.min(list.size(), maxItems);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            String s = list.get(i);
            buf.writeUtf(s.length() > maxLen ? s.substring(0, maxLen) : s, maxLen);
        }
    }

    /** Traité par la partie client (PartieClient), sur le thread du jeu. */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.RECEPTION_CLIENT.accept(this));
        ctx.get().setPacketHandled(true);
    }
}
