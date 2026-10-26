package com.Spartan.Networks;

import com.Spartan.scan.ScanTransfer;
import io.netty.handler.codec.DecoderException;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** C->S : un morceau (compressé) d'un rapport de contrôle d'intégrité. */
public class ScanChunkPacket {
    private final int scanId;
    private final int index;
    private final int total;
    private final byte[] data;

    public ScanChunkPacket(int scanId, int index, int total, byte[] data) {
        this.scanId = scanId;
        this.index = index;
        this.total = total;
        this.data = data;
    }

    public static ScanChunkPacket decode(FriendlyByteBuf buf) {
        int scanId = buf.readInt();
        int index = buf.readVarInt();
        int total = buf.readVarInt();
        if (total < 1 || total > ScanTransfer.MAX_CHUNKS || index < 0 || index >= total) {
            throw new DecoderException("Spartan: morceau de rapport invalide");
        }
        return new ScanChunkPacket(scanId, index, total, buf.readByteArray(ScanTransfer.CHUNK_BYTES));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.scanId);
        buf.writeVarInt(this.index);
        buf.writeVarInt(this.total);
        buf.writeByteArray(this.data);
    }

    public int scanId() {
        return this.scanId;
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
