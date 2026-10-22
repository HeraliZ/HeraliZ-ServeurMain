package com.Spartan.Networks;

import com.Spartan.scan.ScanTransfer;
import io.netty.handler.codec.DecoderException;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** C->S : un morceau d'une capture d'écran (image JPEG) demandée par le serveur. */
public class CaptureChunkPacket {
    private final int captureId;
    private final int index;
    private final int total;
    private final byte[] data;

    public CaptureChunkPacket(int captureId, int index, int total, byte[] data) {
        this.captureId = captureId;
        this.index = index;
        this.total = total;
        this.data = data;
    }

    public static CaptureChunkPacket decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        int index = buf.readVarInt();
        int total = buf.readVarInt();
        if (total < 1 || total > ScanTransfer.MAX_CHUNKS || index < 0 || index >= total) {
            throw new DecoderException("Spartan: morceau de capture invalide");
        }
        return new CaptureChunkPacket(id, index, total, buf.readByteArray(ScanTransfer.CHUNK_BYTES));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.captureId);
        buf.writeVarInt(this.index);
        buf.writeVarInt(this.total);
        buf.writeByteArray(this.data);
    }

    public int captureId() {
        return this.captureId;
    }

    public int index() {
        return this.index;
    }

    public int total() {
        return this.total;
    }

    public byte[] data() {
        return this.data;
    }

    /** Traité par la partie serveur (ReceptionServeur), sur le thread du serveur. */
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ModMessages.RECEPTION_SERVEUR.accept(new ModMessages.Recu(ctx.get().getSender(), this)));
        ctx.get().setPacketHandled(true);
    }
}
