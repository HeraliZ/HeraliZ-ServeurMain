package fr.heralisz.n07.decor;

import fr.heralisz.n07.HeraliszN07;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * /heralisz decors vitrine : pose tous les décors du spawn devant soi, en rangées, tournés vers soi, pour les voir d'un coup
 * (les lanternes sous une poutre, les enseignes contre un mur, les wagons sur une voie). À faire dans un endroit plat et dégagé.
 */
@Mod.EventBusSubscriber(modid = HeraliszN07.MODID)
public final class DecorCommand {
   private DecorCommand() {}

   private static final int ROW_WIDTH = 60;

   @SubscribeEvent
   public static void register(RegisterCommandsEvent e) {
      // 25/10 (tri des commandes) : toutes les commandes des boss sous /boss
      e.getDispatcher().register(Commands.literal("boss").requires(s -> s.hasPermission(2)).then(Commands.literal("n07").requires(s -> s.hasPermission(2))
         .then(Commands.literal("decors").then(Commands.literal("vitrine").executes(c -> showroom(c.getSource()))))));
   }

   private static int showroom(CommandSourceStack src) {
      ServerLevel level = src.getLevel();
      Direction look = src.getEntity() != null ? src.getEntity().getDirection() : Direction.NORTH;
      Direction right = look.getClockWise();
      Direction face = look.getOpposite();
      BlockPos origin = BlockPos.containing(src.getPosition()).relative(look, 5).relative(right, -ROW_WIDTH / 2);
      int col = 0, row = 0, depth = 0, placed = 0;
      for (DecorKind k : DecorKind.values()) {
         DecorSpec s = k.spec;
         int[] e = extent(s, face, look, right);
         int w = e[1] - e[0] + 1;
         if (col > 0 && col + w > ROW_WIDTH) {
            row += depth + 4;
            col = 0;
            depth = 0;
         }
         depth = Math.max(depth, e[3] - e[2] + 1);
         BlockPos p = origin.relative(right, col - e[0]).relative(look, row - e[2]);
         BlockState st = DecorRegistry.BLOCKS.get(k).get().defaultBlockState().setValue(DecorBlock.FACING, face);
         switch (s.mount) {
            case CEILING -> {
               // poutre sur un poteau ; le décor pend dessous
               int top = Math.max(4, 2 - s.bounds[1]);
               for (int i = e[0] - 1; i <= e[1] + 1; i++) level.setBlock(p.relative(right, i).above(top), Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState(), 3);
               for (int y = 0; y < top; y++) level.setBlock(p.relative(right, e[0] - 1).above(y), Blocks.DARK_OAK_FENCE.defaultBlockState(), 3);
               p = p.above(top - 1);
            }
            case WALL -> {
               // pan de mur derrière le décor ; le bas du décor à un bloc du sol
               int lift = Math.max(1, 1 - s.bounds[1]);
               BlockPos wall = p.relative(look);
               for (int i = e[0] - 1; i <= e[1] + 1; i++) {
                  for (int y = 0; y <= lift + e[5] + 1; y++) level.setBlock(wall.relative(right, i).above(y), Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
               }
               p = p.above(lift);
            }
            default -> {
               if (s.rail) {
                  RailShape sh = look.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
                  for (int i = e[2] - 2; i <= e[3] + 2; i++) level.setBlock(p.relative(look, i), Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, sh), 3);
                  p = p.above();
                  st = st.setValue(DecorBlock.ON_RAIL, true);
               }
            }
         }
         level.setBlock(p, st, 3);
         col += w + 2;
         placed++;
      }
      int n = placed;
      src.sendSuccess(() -> Component.translatable("command.heralisz_n07.decor.showroom", n), true);
      return n;
   }

   /**
    * Encombrement du décor vu du joueur, relatif à sa case d'ancrage : {gauche, droite, près, loin, bas, haut} — le long de la
    * droite du joueur puis de son regard.
    */
   private static int[] extent(DecorSpec s, Direction face, Direction look, Direction right) {
      int[] b = s.bounds;
      int[] e = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, b[1], b[4]};
      for (int x : new int[] {b[0], b[3]}) {
         for (int z : new int[] {b[2], b[5]}) {
            int[] r = DecorSpec.rotate(x, z, face);
            int side = r[0] * right.getStepX() + r[1] * right.getStepZ();
            int ahead = r[0] * look.getStepX() + r[1] * look.getStepZ();
            e[0] = Math.min(e[0], side);
            e[1] = Math.max(e[1], side);
            e[2] = Math.min(e[2], ahead);
            e[3] = Math.max(e[3], ahead);
         }
      }
      return e;
   }
}
