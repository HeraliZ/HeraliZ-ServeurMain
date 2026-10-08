package fr.heralisz.mere.dungeon;

import fr.heralisz.mere.HeraliszMere;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * /mere salle : construit le Nid devant soi (la bouche de la grotte à deux pas, la galerie dans la direction du regard) et lie le
 * cœur du nid à l'arène. /mere reset : elle regagne son cocon, le nid est remis à zéro. /mere eveil : rituel immédiat.
 */
@Mod.EventBusSubscriber(modid = HeraliszMere.MODID)
public final class NestCommand {
   private NestCommand() {}

   @SubscribeEvent
   public static void register(RegisterCommandsEvent e) {
      e.getDispatcher().register(Commands.literal("mere").requires(s -> s.hasPermission(2))
         .then(Commands.literal("salle").executes(c -> place(c.getSource())))
         .then(Commands.literal("reset").executes(c -> reset(c.getSource())))
         .then(Commands.literal("eveil").executes(c -> wake(c.getSource()))));
   }

   private static Rotation rotationFor(Direction face) {
      return switch (face) {
         case WEST -> Rotation.CLOCKWISE_90;
         case NORTH -> Rotation.CLOCKWISE_180;
         case EAST -> Rotation.COUNTERCLOCKWISE_90;
         default -> Rotation.NONE;
      };
   }

   private static float yawOffset(Rotation r) {
      return switch (r) {
         case CLOCKWISE_90 -> 90F;
         case CLOCKWISE_180 -> 180F;
         case COUNTERCLOCKWISE_90 -> 270F;
         default -> 0F;
      };
   }

   private static int place(CommandSourceStack src) {
      ServerLevel level = src.getLevel();
      Optional<StructureTemplate> opt = level.getStructureManager().get(new ResourceLocation(HeraliszMere.MODID, "ruin_nest"));
      if (opt.isEmpty()) {
         src.sendFailure(Component.translatable("command.heralisz_mere.missing"));
         return 0;
      }
      StructureTemplate t = opt.get();
      Direction face = Direction.fromYRot(src.getRotation().y);
      Rotation rot = rotationFor(face);
      StructurePlaceSettings set = new StructurePlaceSettings().setRotation(rot).setMirror(Mirror.NONE).setIgnoreEntities(true);
      BlockPos entrance = BlockPos.ZERO;
      for (StructureTemplate.StructureBlockInfo info : t.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.STRUCTURE_BLOCK)) {
         if (info.nbt() != null && info.nbt().getString("metadata").startsWith("entrance")) entrance = info.pos();
      }
      BlockPos target = BlockPos.containing(src.getPosition()).relative(face, 2);
      BlockPos origin = target.subtract(StructureTemplate.calculateRelativePosition(set, entrance));
      src.sendSuccess(() -> Component.translatable("command.heralisz_mere.building"), true);
      t.placeInWorld(level, origin, origin, set, level.random, Block.UPDATE_CLIENTS);
      BlockPos bossPos = null, door = null, gate = null;
      float bossYaw = Float.NaN, doorYaw = Float.NaN;
      for (StructureTemplate.StructureBlockInfo info : t.filterBlocks(origin, set, Blocks.STRUCTURE_BLOCK)) {
         CompoundTag nbt = info.nbt();
         String meta = nbt == null ? "" : nbt.getString("metadata");
         String[] parts = meta.split(" ");
         float yaw = Float.NaN;
         if (parts.length > 1) {
            try {
               yaw = Float.parseFloat(parts[1]) + yawOffset(rot);
            } catch (NumberFormatException ignored) {
               // sans orientation
            }
         }
         if (meta.startsWith("boss")) {
            bossPos = info.pos();
            bossYaw = yaw;
         } else if (meta.startsWith("door")) {
            door = info.pos();
            doorYaw = yaw;
         } else if (meta.startsWith("gate")) {
            gate = info.pos();
         }
         level.setBlock(info.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
      }
      int n = 0;
      for (StructureTemplate.StructureBlockInfo info : t.filterBlocks(origin, set, ModBlocks.NEST_HEART.get())) {
         if (level.getBlockEntity(info.pos()) instanceof NestHallBEServeur be) {
            if (bossPos != null) be.setMarkers(bossPos, bossYaw, door, doorYaw, gate);
            n++;
         }
      }
      int seals = n;
      src.sendSuccess(() -> Component.translatable("command.heralisz_mere.done", target.getX(), target.getY(), target.getZ(), seals), true);
      return 1;
   }

   private static NestHallBEServeur nearest(CommandSourceStack src) {
      NestHallBEServeur best = NestHallBEServeur.nearestServeur(src.getLevel(), BlockPos.containing(src.getPosition()), 160);
      if (best == null) src.sendFailure(Component.translatable("command.heralisz_mere.none"));
      return best;
   }

   private static int reset(CommandSourceStack src) {
      NestHallBEServeur a = nearest(src);
      if (a == null) return 0;
      a.reset(src.getLevel());
      src.sendSuccess(() -> Component.translatable("command.heralisz_mere.reset"), true);
      return 1;
   }

   private static int wake(CommandSourceStack src) {
      NestHallBEServeur a = nearest(src);
      if (a == null) return 0;
      if (!a.forceAwaken(src.getLevel())) {
         src.sendFailure(Component.translatable("command.heralisz_mere.busy"));
         return 0;
      }
      src.sendSuccess(() -> Component.translatable("command.heralisz_mere.woken"), true);
      return 1;
   }
}
