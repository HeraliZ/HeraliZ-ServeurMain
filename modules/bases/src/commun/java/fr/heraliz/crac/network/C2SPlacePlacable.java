package fr.heraliz.crac.network;

import fr.heraliz.crac.heraliz.Txt;
import java.util.function.Supplier;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.placable.PlacableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.network.NetworkEvent.Context;
import org.jetbrains.annotations.Nullable;

public record C2SPlacePlacable(int hand) {
    public static void encode(C2SPlacePlacable m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.hand);
    }

    public static C2SPlacePlacable decode(FriendlyByteBuf buf) {
        return new C2SPlacePlacable(buf.readVarInt());
    }

    public static void handle(C2SPlacePlacable msg, Supplier<Context> ctx) {
        // traitement : C2SPlacePlacableTraitement.traiter (src/serveur), branché par la partie serveur
        fr.heraliz.crac.network.NetworkHandler.recevoirServeur(msg, ctx);
    }
}
