package fr.heraliz.crac.build.net;

import java.util.UUID;
import java.util.function.Supplier;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Contenu du paquet S2CPieceInfo pour un joueur et une pièce (corps d'origine de S2CPieceInfo.of, côté serveur). */
public final class S2CPieceInfoFabrique {
    private S2CPieceInfoFabrique() {
    }

    public static S2CPieceInfo of(ServerLevel level, ServerPlayer player, Piece p) {
        boolean creative = player.getAbilities().instabuild;
        int window = BuildConfig.demolishWindowSeconds.get();
        int left = window < 0 ? -1 : (int) Math.max(0, window - (level.getGameTime() - p.modifiedAt) / 20L);
        UUID group = StructureService.controllingGroup(level, StructureService.buildingOf(level, p));
        boolean mine = group != null ? StructureService.isMember(level, player, group, fr.heraliz.crac.heraliz.ClanAccess.Action.BUILD) : p.owner.equals(player.getUUID());
        boolean canDemolish = creative || (mine && left != 0);
        return new S2CPieceInfo(p.id, true, p.hp, p.maxHp, p.stability, p.tier.ordinal(), left, canDemolish,
            StructureService.hasPrivilege(level, player, p));
    }
}
