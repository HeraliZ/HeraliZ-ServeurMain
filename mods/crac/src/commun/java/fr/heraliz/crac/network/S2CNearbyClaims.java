package fr.heraliz.crac.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import fr.heraliz.crac.claim.Claim;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;
import org.jetbrains.annotations.Nullable;

public record S2CNearbyClaims(List<S2CNearbyClaims.ClaimEntry> claims) {
    public static void encode(S2CNearbyClaims msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.claims.size());

        for (S2CNearbyClaims.ClaimEntry e : msg.claims) {
            buf.writeBlockPos(e.center());
            buf.writeVarInt(e.radius());
            buf.writeVarInt(e.yRadius());
            buf.writeVarInt(e.tier());
            buf.writeInt(e.color());
            buf.writeBoolean(e.ownedByMe());
        }
    }

    /** Claims in view are few; the cap only bounds what a broken or hostile stream can make the client allocate. */
    static final int MAX_CLAIMS = 65536;

    public static S2CNearbyClaims decode(FriendlyByteBuf buf) {
        int n = PacketLimits.readCount(buf, MAX_CLAIMS, 16);
        List<S2CNearbyClaims.ClaimEntry> list = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            list.add(new S2CNearbyClaims.ClaimEntry(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readBoolean()));
        }

        return new S2CNearbyClaims(list);
    }

    public static void handle(S2CNearbyClaims msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                NetworkHandler.RECEPTION_CLIENT.accept(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }


    /**
     * The claim whose zone holds the block, resolved as the server does where zones overlap ({@code ClaimData.getAt}):
     * the nearest Claim Block, ties by position. Null outside every zone.
     */
    @Nullable
    public static ClaimEntry claimAt(List<ClaimEntry> claims, BlockPos pos) {
        ClaimEntry best = null;
        for (ClaimEntry e : claims) {
            if (e.contains(pos) && (best == null || Claim.nearer(e.center(), best.center(), pos))) {
                best = e;
            }
        }
        return best;
    }

    public record ClaimEntry(BlockPos center, int radius, int yRadius, int tier, int color, boolean ownedByMe) {
        public boolean contains(BlockPos pos) {
            return Math.abs(pos.getX() - this.center.getX()) <= this.radius
                && Math.abs(pos.getZ() - this.center.getZ()) <= this.radius
                && Math.abs(pos.getY() - this.center.getY()) <= this.yRadius;
        }
    }
}
