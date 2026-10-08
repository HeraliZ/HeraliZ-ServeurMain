package fr.heralisz.mammoth.dungeon;

import fr.heralisz.mammoth.HeraliszMammoth;
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
 * /mammouth arene : construit le Cimetière des Mammouths devant soi (l'entrée à deux pas, l'arène s'étend dans la direction
 * du regard) et lie l'autel à la niche d'Ymirak. /mammouth reset : rendort Ymirak, remet l'arène à zéro. /mammouth eveil :
 * réveil immédiat (sans cor).
 */
@Mod.EventBusSubscriber(modid = HeraliszMammoth.MODID)
public final class MammothCommand {
   private MammothCommand() {}

   @SubscribeEvent
   public static void register(RegisterCommandsEvent e) {
      e.getDispatcher().register(Commands.literal("mammouth").requires(s -> s.hasPermission(2))
         .then(Commands.literal("arene").executes(c -> place(c.getSource())))
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
      Optional<StructureTemplate> opt = level.getStructureManager().get(new ResourceLocation(HeraliszMammoth.MODID, "mammoth_graveyard"));
      if (opt.isEmpty()) {
         src.sendFailure(Component.translatable("command.heralisz_mammoth.missing"));
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
      src.sendSuccess(() -> Component.translatable("command.heralisz_mammoth.building"), true);
      t.placeInWorld(level, origin, origin, set, level.random, Block.UPDATE_CLIENTS);
      BlockPos bossPos = null;
      float bossYaw = Float.NaN;
      for (StructureTemplate.StructureBlockInfo info : t.filterBlocks(origin, set, Blocks.STRUCTURE_BLOCK)) {
         CompoundTag nbt = info.nbt();
         String meta = nbt == null ? "" : nbt.getString("metadata");
         if (meta.startsWith("boss")) {
            bossPos = info.pos();
            String[] parts = meta.split(" ");
            if (parts.length > 1) {
               try {
                  bossYaw = Float.parseFloat(parts[1]) + yawOffset(rot);
               } catch (NumberFormatException ignored) {
                  // il regardera l'autel
               }
            }
         }
         level.setBlock(info.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
      }
      int n = 0;
      for (StructureTemplate.StructureBlockInfo info : t.filterBlocks(origin, set, ModBlocks.HORN_ALTAR.get())) {
         if (level.getBlockEntity(info.pos()) instanceof HornAltarBlockEntityServeur be) {
            if (bossPos != null) be.setBoss(bossPos, bossYaw);
            n++;
         }
      }
      int altars = n;
      src.sendSuccess(() -> Component.translatable("command.heralisz_mammoth.done", target.getX(), target.getY(), target.getZ(), altars), true);
      return 1;
   }

   private static HornAltarBlockEntityServeur nearest(CommandSourceStack src) {
      HornAltarBlockEntityServeur best = HornAltarBlockEntityServeur.nearestServeur(src.getLevel(), BlockPos.containing(src.getPosition()), 160);
      if (best == null) src.sendFailure(Component.translatable("command.heralisz_mammoth.none"));
      return best;
   }

   private static int reset(CommandSourceStack src) {
      HornAltarBlockEntityServeur a = nearest(src);
      if (a == null) return 0;
      a.reset(src.getLevel());
      src.sendSuccess(() -> Component.translatable("command.heralisz_mammoth.reset"), true);
      return 1;
   }

   private static int wake(CommandSourceStack src) {
      HornAltarBlockEntityServeur a = nearest(src);
      if (a == null) return 0;
      if (!a.forceAwaken(src.getLevel())) {
         src.sendFailure(Component.translatable("command.heralisz_mammoth.busy"));
         return 0;
      }
      src.sendSuccess(() -> Component.translatable("command.heralisz_mammoth.woken"), true);
      return 1;
   }
}
