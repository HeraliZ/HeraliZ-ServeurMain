package fr.heraliz.crac.heraliz;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Command replies. The HeraliZ world runs with {@code sendCommandFeedback = false}, which hides
 * {@link CommandSourceStack#sendSuccess} from players: a player gets the reply as a system message, the console as
 * usual.
 */
public final class Reply {
    private Reply() {
    }

    public static void ok(CommandSourceStack src, Component msg) {
        if (src.getEntity() instanceof ServerPlayer p) {
            p.sendSystemMessage(msg);
        } else {
            src.sendSuccess(() -> msg, false);
        }
    }

    public static void fail(CommandSourceStack src, Component msg) {
        if (src.getEntity() instanceof ServerPlayer p) {
            p.sendSystemMessage(msg.copy().withStyle(ChatFormatting.RED));
        } else {
            src.sendFailure(msg);
        }
    }
}
