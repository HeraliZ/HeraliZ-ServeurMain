package fr.heraliz.crac.build.net;

import fr.heraliz.crac.heraliz.Txt;
import java.util.function.Supplier;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.model.Tier;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.item.BuildingHammerItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Traitement serveur du paquet C2SHammer (corps d'origine, docs/METHODE_CLIENT_SERVEUR.md §6) : le paquet commun ne garde
 * que son format ; ce traitement est appelé par serveur/PartieServeur.
 */
public final class C2SHammerTraitement {
    private C2SHammerTraitement() {
    }

    static final long ACTION_COOLDOWN_MS = 250L;
    public static void traiter(C2SHammer msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().enqueueWork(() -> process(player, msg));
        ctx.get().setPacketHandled(true);
    }    private static void process(@Nullable ServerPlayer player, C2SHammer msg) {
        if (!PacketGuard.canAct(player) || !(player.getMainHandItem().getItem() instanceof BuildingHammerItem)) {
            return;
        }
        if (msg.action() < C2SHammer.UPGRADE || msg.action() > C2SHammer.DEMOLISH) {
            return;
        }
        if (msg.action() == C2SHammer.UPGRADE && (msg.tier() <= Tier.TWIG.ordinal() || msg.tier() >= Tier.values().length)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!PacketGuard.near(player, level, msg.pos(), PacketGuard.reach() + 3.0)) {
            return;
        }
        boolean allowed = msg.action() == C2SHammer.REPAIR
            ? PacketGuard.allow(player, PacketGuard.Limit.REPAIR, C2SHammer.repairCooldownMs())
            : PacketGuard.allow(player, PacketGuard.Limit.HAMMER, ACTION_COOLDOWN_MS);
        if (!allowed) {
            return;
        }
        Piece p = PacketGuard.pieceAt(level, msg.pos(), msg.pieceId());
        if (p == null) {
            S2CPieceInfo.send(player, S2CPieceInfo.gone(msg.pieceId()));
            return;
        }
        if (!PacketGuard.inSight(player, level, msg.pos(), p)) {
            player.displayClientMessage(Txt.red("crac.hammer.no_sight"), true);
            return;
        }
        StructureService.Outcome out = switch (msg.action()) {
            case C2SHammer.UPGRADE -> StructureService.upgrade(level, player, p, Tier.byOrdinal(msg.tier()));
            case C2SHammer.REPAIR -> StructureService.repair(level, player, p);
            default -> StructureService.demolish(level, player, p);
        };
        if (!out.ok()) {
            player.displayClientMessage(out.message().copy().withStyle(ChatFormatting.RED), true);
        }
        Piece now = PacketGuard.pieceAt(level, msg.pos(), msg.pieceId());
        S2CPieceInfo.send(player, now == null ? S2CPieceInfo.gone(msg.pieceId()) : S2CPieceInfoFabrique.of(level, player, now));
    }}
