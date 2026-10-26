package fr.heraliz.crac.network;

import fr.heraliz.crac.heraliz.Txt;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.build.world.StructureBlock;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.placable.MultiBlocks;
import fr.heraliz.crac.placable.PlacableSet;
import fr.heraliz.crac.placable.PlacableSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent.Context;
import org.jetbrains.annotations.Nullable;

/** Adventure-mode sneak-click on a placable: it goes back to the inventory as exactly one item. */
public record C2SPickup(BlockPos pos) {


    public static void encode(C2SPickup m, FriendlyByteBuf buf) {
        buf.writeBlockPos(m.pos);
    }

    public static C2SPickup decode(FriendlyByteBuf buf) {
        return new C2SPickup(buf.readBlockPos());
    }

    public static void handle(C2SPickup msg, Supplier<Context> ctx) {
        // traitement : C2SPickupTraitement.traiter (src/serveur), branché par la partie serveur
        fr.heraliz.crac.network.NetworkHandler.recevoirServeur(msg, ctx);
    }
}
