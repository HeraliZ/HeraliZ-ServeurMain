package fr.heraliz.airstrike.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.heraliz.airstrike.strike.StrikeManager;
import fr.heraliz.airstrike.strike.StrikeType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/**
 * {@code /airstrike call <type> [x y z]} and {@code /airstrike clear} (operators). Handy for events and tests.
 */
public final class AirstrikeCommand {
    private AirstrikeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("airstrike").requires(s -> s.hasPermission(2))
                .then(Commands.literal("call")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(StrikeType.values()).map(t -> t.id), b))
                                .executes(c -> call(c, null))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(c -> call(c, Vec3Argument.getVec3(c, "pos"))))))
                .then(Commands.literal("clear").executes(c -> {
                    int n = StrikeManager.clear();
                    c.getSource().sendSuccess(() -> Component.translatable("commands.airstrike.cleared", n), true);
                    return n;
                })));
    }

    private static int call(CommandContext<CommandSourceStack> ctx, Vec3 pos) {
        CommandSourceStack src = ctx.getSource();
        StrikeType type = StrikeType.byName(StringArgumentType.getString(ctx, "type"));
        if (type == null) {
            src.sendFailure(Component.translatable("commands.airstrike.unknown_type"));
            return 0;
        }
        ServerLevel level = src.getLevel();
        Vec3 from = src.getPosition();
        ServerPlayer player = src.getEntity() instanceof ServerPlayer sp ? sp : null;
        Vec3 target = pos;
        if (target == null) {
            if (player == null) {
                src.sendFailure(Component.translatable("commands.airstrike.need_pos"));
                return 0;
            }
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(player.getViewVector(1.0f).scale(400));
            BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
            target = hit.getType() == HitResult.Type.MISS ? eye.add(player.getViewVector(1.0f).scale(60)) : hit.getLocation();
        }
        if (from.distanceToSqr(target) < 4) from = target.add(-50, 0, 0);
        StrikeManager.launch(level, type, target, from, player);
        Vec3 t = target;
        src.sendSuccess(() -> Component.translatable("commands.airstrike.called", Component.translatable(type.translationKey()),
                String.format("%.0f %.0f %.0f", t.x, t.y, t.z)), true);
        return 1;
    }
}
