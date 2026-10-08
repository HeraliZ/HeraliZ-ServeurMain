package fr.heraliz.crac.build.client.render;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.LPoint;
import fr.heraliz.crac.build.model.Tier;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Development preview of {@link GhostMesh}, registered only outside production:
 * {@code /cracghost <x> <y> <z> <kind> [tier=..] [anchor=xa,xb,za,zb] [h=..] [orient=..] [rot=..] [state=..]
 * [variant=..] [red]} adds a ghost of the piece with building origin (x, y, z) (lattice anchor, height h in 24 px
 * units, orientation in 30° steps, see {@code PieceKey}); {@code /cracghost off} clears them. Walls draw every post
 * unless a variant is given.
 */
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GhostDebug {
    private static final List<Entry> GHOSTS = new CopyOnWriteArrayList<>();

    private record Entry(PieceKey key, Tier tier, int state, long variant, int argb) {
    }

    private GhostDebug() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        if (FMLEnvironment.production) {
            return;
        }
        event.getDispatcher().register(Commands.literal("cracghost")
            .then(Commands.literal("off").executes(c -> {
                GHOSTS.clear();
                return 1;
            }))
            .then(Commands.argument("args", StringArgumentType.greedyString())
                .executes(c -> add(c.getSource(), StringArgumentType.getString(c, "args")))));
    }

    private static int add(CommandSourceStack src, String args) {
        String[] a = args.trim().split("\\s+");
        try {
            BlockPos origin = new BlockPos(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]));
            PieceKind kind = PieceKind.byId(a[3]);
            if (kind == null) {
                throw new IllegalArgumentException("unknown kind " + a[3]);
            }
            Tier tier = Tier.WOOD;
            LPoint anchor = LPoint.ORIGIN;
            int h = 0;
            int orient = 0;
            int rot = 0;
            int state = 0;
            long variant = kind.isWall() ? 0xF : 0;
            int argb = 0x8040A0FF;
            for (int i = 4; i < a.length; i++) {
                String[] kv = a[i].split("=", 2);
                switch (kv[0]) {
                    case "tier" -> tier = Tier.byId(kv[1]);
                    case "anchor" -> {
                        String[] l = kv[1].split(",");
                        if (l.length != 4) {
                            throw new IllegalArgumentException("anchor=xa,xb,za,zb");
                        }
                        anchor = new LPoint(Integer.parseInt(l[0]), Integer.parseInt(l[1]), Integer.parseInt(l[2]), Integer.parseInt(l[3]));
                    }
                    case "h" -> h = Integer.parseInt(kv[1]);
                    case "orient" -> orient = Integer.parseInt(kv[1]);
                    case "rot" -> rot = Integer.parseInt(kv[1]);
                    case "state" -> state = Integer.parseInt(kv[1]);
                    case "variant" -> variant = Long.parseLong(kv[1]);
                    case "red" -> argb = 0x80FF4040;
                    default -> throw new IllegalArgumentException("unknown option " + a[i]);
                }
            }
            if (tier == null) {
                throw new IllegalArgumentException("unknown tier");
            }
            PieceKey key = new PieceKey(kind, origin, anchor, h, orient, rot);
            if (!key.isWellFormed()) {
                throw new IllegalArgumentException("key out of range: " + key);
            }
            GHOSTS.add(new Entry(key, tier, state, variant, argb));
            return 1;
        } catch (RuntimeException e) {
            src.sendFailure(Component.literal("cracghost: " + e.getMessage()));
            return 0;
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (GHOSTS.isEmpty() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        for (Entry e : GHOSTS) {
            GhostMesh.render(event.getPoseStack(), buffers, event.getCamera().getPosition(), e.key, e.tier, e.state, e.variant, e.argb);
        }
    }
}
