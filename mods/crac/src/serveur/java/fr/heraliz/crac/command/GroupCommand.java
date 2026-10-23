package fr.heraliz.crac.command;

import fr.heraliz.crac.heraliz.ClanAccess;
import fr.heraliz.crac.heraliz.Reply;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.group.GroupSync;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2COpenGroupCreate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;

@EventBusSubscriber(
    modid = "crac"
)
public class GroupCommand {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // une base par clan (1.21.0) : plus de /group du tout quand HeraliZ Clans est là (les groupes suivent les clans)
        if (fr.heraliz.crac.heraliz.BasesClans.actif()) {
            return;
        }
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("group").then(Commands.literal("create").executes(ctx -> create(ctx.getSource()))).executes(ctx -> manage(ctx.getSource()))
        );
    }

    private static int create(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        // une base par clan (1.16.0) : plus de groupes faits à la main
        if (fr.heraliz.crac.heraliz.BasesClans.actif()) {
            Reply.fail(src, Component.translatableWithFallback("crac.group.clans_seulement",
                "Les bases sont celles des clans : crée ou rejoins un clan (touche K) ; les membres et leurs droits se règlent dans le clan."));
            return 0;
        }
        GroupData data = GroupData.get(player.getServer());
        if (data.getByMember(player.getUUID()) != null) {
            Reply.fail(src, Component.translatable("crac.group.already_cmd"));
            return 0;
        } else {
            if (ClanAccess.clansLoaded()) {
                player.sendSystemMessage(Component.translatable("crac.group.clans_hint").withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2COpenGroupCreate());
            return 1;
        }
    }

    private static int manage(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        GroupData data = GroupData.get(player.getServer());
        Group g = data.getByMember(player.getUUID());
        if (g != null) {
            GroupSync.sendManage(player, g);
            return 1;
        } else if (data.hasAnyInvite(player.getUUID())) {
            GroupSync.sendInvites(player);
            return 1;
        } else {
            Reply.fail(src, Component.translatable("crac.group.none_cmd"));
            return 0;
        }
    }
}
