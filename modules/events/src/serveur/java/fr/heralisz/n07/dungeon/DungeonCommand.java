package fr.heralisz.n07.dungeon;

import com.mojang.brigadier.arguments.StringArgumentType;
import fr.heralisz.n07.HeraliszN07;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * /heralisz donjon &lt;n07|peste|chevalier|sylvorn|mere&gt; : construit le donjon du boss devant soi (l'entrée à deux pas, le
 * donjon s'étend dans la direction du regard) et lie son sanctuaire à l'emplacement du boss.
 * /heralisz sanctuaire reset : remet à zéro le sanctuaire le plus proche (délai de réapparition, boss rendormi).
 */
@Mod.EventBusSubscriber(modid = HeraliszN07.MODID)
public final class DungeonCommand {
   private DungeonCommand() {}

   @SubscribeEvent
   public static void register(RegisterCommandsEvent e) {
      // 25/10 (tri des commandes) : toutes les commandes des boss sous /boss
      e.getDispatcher().register(Commands.literal("boss").requires(s -> s.hasPermission(2)).then(Commands.literal("n07").requires(s -> s.hasPermission(2))
         .then(Commands.literal("labo").executes(c -> place(c.getSource(), DungeonKind.N07)))
         .then(Commands.literal("reset").executes(c -> reset(c.getSource())))
         .then(Commands.literal("eveil").executes(c -> wake(c.getSource())))));
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

   public static int place(CommandSourceStack src, DungeonKind kind) {
      ServerLevel level = src.getLevel();
      Optional<StructureTemplate> opt = level.getStructureManager().get(new ResourceLocation(HeraliszN07.MODID, kind.structure()));
      if (opt.isEmpty()) {
         src.sendFailure(Component.translatable("command.heralisz_n07.dungeon.missing", kind.structure()));
         return 0;
      }
      StructureTemplate t = opt.get();
      Direction face = Direction.fromYRot(src.getRotation().y);
      Rotation rot = rotationFor(face);
      StructurePlaceSettings set = new StructurePlaceSettings().setRotation(rot).setMirror(Mirror.NONE).setIgnoreEntities(true);
      // l'entrée (marqueur « entrance ») tombe deux blocs devant soi, au niveau des pieds
      BlockPos entrance = BlockPos.ZERO;
      for (StructureTemplate.StructureBlockInfo info : t.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.STRUCTURE_BLOCK)) {
         if (info.nbt() != null && info.nbt().getString("metadata").startsWith("entrance")) entrance = info.pos();
      }
      BlockPos target = BlockPos.containing(src.getPosition()).relative(face, 2);
      BlockPos origin = target.subtract(StructureTemplate.calculateRelativePosition(set, entrance));
      src.sendSuccess(() -> Component.translatable("command.heralisz_n07.dungeon.building", kind.id), true);
      t.placeInWorld(level, origin, origin, set, level.random, Block.UPDATE_CLIENTS);
      // marqueurs : emplacement du boss, puis on les efface
      BlockPos bossPos = null;
      float bossYaw = Float.NaN;
      List<StructureTemplate.StructureBlockInfo> markers = t.filterBlocks(origin, set, Blocks.STRUCTURE_BLOCK);
      for (StructureTemplate.StructureBlockInfo info : markers) {
         CompoundTag nbt = info.nbt();
         String meta = nbt == null ? "" : nbt.getString("metadata");
         if (meta.startsWith("boss")) {
            bossPos = info.pos();
            String[] parts = meta.split(" ");
            if (parts.length > 1) {
               try {
                  bossYaw = Float.parseFloat(parts[1]) + yawOffset(rot);
               } catch (NumberFormatException ignored) {
                  // pas d'orientation : il regardera le sanctuaire
               }
            }
         }
         level.setBlock(info.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
      }
      int shrines = 0;
      for (StructureTemplate.StructureBlockInfo info : t.filterBlocks(origin, set, shrineBlock(kind))) {
         if (level.getBlockEntity(info.pos()) instanceof ShrineBlockEntityServeur be) {
            if (bossPos != null) be.setBoss(bossPos, bossYaw);
            shrines++;
         }
      }
      Vec3 c = Vec3.atCenterOf(target);
      int n = shrines;
      src.sendSuccess(() -> Component.translatable("command.heralisz_n07.dungeon.done", kind.id, (int) c.x, (int) c.y, (int) c.z, n), true);
      return 1;
   }

   private static Block shrineBlock(DungeonKind k) {
      return switch (k) {
         case N07 -> DungeonRegistry.N07_TERMINAL.get();
      };
   }

   private static ShrineBlockEntityServeur nearest(CommandSourceStack src) {
      ServerLevel level = src.getLevel();
      BlockPos at = BlockPos.containing(src.getPosition());
      ShrineBlockEntityServeur best = null;
      double bd = 96 * 96;
      ChunkPos cp = new ChunkPos(at);
      for (int dx = -6; dx <= 6; dx++) {
         for (int dz = -6; dz <= 6; dz++) {
            if (!level.hasChunk(cp.x + dx, cp.z + dz)) continue;
            for (BlockEntity be : level.getChunk(cp.x + dx, cp.z + dz).getBlockEntities().values()) {
               if (be instanceof ShrineBlockEntityServeur s && s.getBlockPos().distSqr(at) < bd) {
                  bd = s.getBlockPos().distSqr(at);
                  best = s;
               }
            }
         }
      }
      if (best == null) src.sendFailure(Component.translatable("command.heralisz_n07.shrine.none"));
      return best;
   }

   private static int reset(CommandSourceStack src) {
      ShrineBlockEntityServeur best = nearest(src);
      if (best == null) return 0;
      best.reset(src.getLevel());
      src.sendSuccess(() -> Component.translatable("command.heralisz_n07.shrine.reset"), true);
      return 1;
   }

   private static int wake(CommandSourceStack src) {
      ShrineBlockEntityServeur best = nearest(src);
      if (best == null) return 0;
      if (!best.forceAwaken(src.getLevel())) {
         src.sendFailure(Component.translatable("command.heralisz_n07.shrine.busy"));
         return 0;
      }
      src.sendSuccess(() -> Component.translatable("command.heralisz_n07.shrine.woken"), true);
      return 1;
   }
}
