package com.Spartan.Networks;

import com.Spartan.scan.Sondes;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * S->C : demande de contrôle d'intégrité. Les signatures cherchées (classes, programmes) ne partent plus qu'en empreintes
 * salées ({@link Sondes}, audit du 02/11, point 13) : ni le jar ni le paquet ne disent ce qui est cherché. Les DLL ne
 * partent plus du tout (le serveur juge les noms reçus). Seules les classes du jeu surveillées pour les Mixins
 * (rendu, mouvement…) restent en clair : ce sont des classes de Minecraft.
 */
public class ScanRequestPacket {
    public static final int MAX_KNOWN_MIXINS = 8192;
    public static final int MAX_PROBES = 256;
    public static final int MAX_SAMPLES = 200;

    private final int scanId;
    private final int samples;
    private final boolean referenceKnown;
    private final long[] knownMixins;
    private final List<String> watchClasses;
    private final Sondes sondes;

    public ScanRequestPacket(int scanId, int samples, boolean referenceKnown, long[] knownMixins,
                             List<String> watchClasses, Sondes sondes) {
        this.scanId = scanId;
        this.samples = samples;
        this.referenceKnown = referenceKnown;
        this.knownMixins = knownMixins;
        this.watchClasses = watchClasses;
        this.sondes = sondes;
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

    /** Programmes et paquets cherchés, en empreintes salées. */
    public Sondes sondes() {
        return sondes;
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
        List<String> watch = readList(buf, MAX_PROBES, 200);
        long sel = buf.readLong();
        long[] programmes = readLongs(buf, Sondes.MAX_EMPREINTES);
        int nl = buf.readVarInt();
        if (nl < 0 || nl > Sondes.MAX_LONGUEURS) {
            throw new DecoderException("Spartan: longueurs invalides (" + nl + ")");
        }
        int[] longueurs = new int[nl];
        for (int i = 0; i < nl; i++) {
            longueurs[i] = buf.readVarInt();
        }
        long[] paquets = readLongs(buf, Sondes.MAX_EMPREINTES);
        return new ScanRequestPacket(scanId, samples, referenceKnown, known, watch, new Sondes(sel, programmes, longueurs, paquets));
    }

    private static long[] readLongs(FriendlyByteBuf buf, int max) {
        int n = buf.readVarInt();
        if (n < 0 || n > max) {
            throw new DecoderException("Spartan: empreintes invalides (" + n + ")");
        }
        long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = buf.readLong();
        }
        return out;
    }

    private static void writeLongs(FriendlyByteBuf buf, long[] v, int max) {
        int n = Math.min(v.length, max);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeLong(v[i]);
        }
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
        buf.writeLong(this.sondes.sel);
        writeLongs(buf, this.sondes.programmes, Sondes.MAX_EMPREINTES);
        int nl = Math.min(this.sondes.longueurs.length, Sondes.MAX_LONGUEURS);
        buf.writeVarInt(nl);
        for (int i = 0; i < nl; i++) {
            buf.writeVarInt(this.sondes.longueurs[i]);
        }
        writeLongs(buf, this.sondes.paquets, Sondes.MAX_EMPREINTES);
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
