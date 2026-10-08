package fr.heraliz.crac.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import fr.heraliz.crac.placable.PlacableSet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.registries.ForgeRegistries;

public record S2CPlacableSet(List<ResourceLocation> ids) {
    public static S2CPlacableSet of(Collection<Block> blocks) {
        List<ResourceLocation> ids = new ArrayList<>(blocks.size());

        for (Block b : blocks) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
            if (id != null) {
                ids.add(id);
            }
        }

        return new S2CPlacableSet(ids);
    }

    public static void encode(S2CPlacableSet m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.ids.size());

        for (ResourceLocation id : m.ids) {
            buf.writeResourceLocation(id);
        }
    }

    private static final int MAX_IDS = 65536;

    public static S2CPlacableSet decode(FriendlyByteBuf buf) {
        int n = PacketLimits.readCount(buf, MAX_IDS, 2);
        List<ResourceLocation> ids = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            ids.add(buf.readResourceLocation());
        }

        return new S2CPlacableSet(ids);
    }

    public static void handle(S2CPlacableSet msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist != Dist.CLIENT) {
                return;
            }
            List<Block> blocks = new ArrayList<>(msg.ids.size());

            for (ResourceLocation id : msg.ids) {
                Block b = ForgeRegistries.BLOCKS.getValue(id);
                if (b != null) {
                    blocks.add(b);
                }
            }

            PlacableSet.setClientSide(blocks);
        });
        ctx.get().setPacketHandled(true);
    }
}
