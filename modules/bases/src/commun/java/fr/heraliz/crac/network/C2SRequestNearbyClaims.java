package fr.heraliz.crac.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.group.Group;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent.Context;

public record C2SRequestNearbyClaims() {



    public static void encode(C2SRequestNearbyClaims msg, FriendlyByteBuf buf) {
    }

    public static C2SRequestNearbyClaims decode(FriendlyByteBuf buf) {
        return new C2SRequestNearbyClaims();
    }



    /** How far the player may walk from where the claims were sent before the client asks for them again. */
    public static int refreshDistance() {
        return Math.max(16, Config.claimViewRadius.get() / 4);
    }

    public static void handle(C2SRequestNearbyClaims msg, Supplier<Context> ctx) {
        // traitement : C2SRequestNearbyClaimsTraitement.traiter (src/serveur), branché par la partie serveur
        fr.heraliz.crac.network.NetworkHandler.recevoirServeur(msg, ctx);
    }
}
