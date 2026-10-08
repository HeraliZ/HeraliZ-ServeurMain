package fr.heraliz.crac.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Bounds for list sizes read from the network. Decoders run on the Netty thread before any permission check, so a
 * count is never trusted: it must fit a fixed cap and the bytes actually left in the payload.
 */
public final class PacketLimits {
    private PacketLimits() {
    }

    /**
     * Reads a VarInt list size and checks it: {@code 0 <= n <= max} and {@code n * minEntryBytes <= readable bytes}.
     * Throws a {@link DecoderException} otherwise, which drops the packet and disconnects the sender.
     */
    public static int readCount(FriendlyByteBuf buf, int max, int minEntryBytes) {
        int n = buf.readVarInt();
        if (n < 0 || n > max) {
            throw new DecoderException("List size " + n + " outside 0.." + max);
        }
        if ((long) n * Math.max(1, minEntryBytes) > buf.readableBytes()) {
            throw new DecoderException("List size " + n + " larger than the payload (" + buf.readableBytes() + " bytes left)");
        }
        return n;
    }

    /** Reads an enum written by {@link FriendlyByteBuf#writeEnum}; null when the ordinal is out of range. */
    public static <E extends Enum<E>> E readEnumOrNull(FriendlyByteBuf buf, Class<E> type) {
        int ordinal = buf.readVarInt();
        E[] values = type.getEnumConstants();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }
}
