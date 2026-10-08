package fr.heralisz.bunker.keycard;

import com.hbm_m.block.UniversalMachinePartBlock;
import com.hbm_m.block.decorations.DoorBlock;
import com.hbm_m.block.entity.doors.DoorBlockEntity;
import com.hbm_m.interfaces.IMultiblockPart;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Portes verrouillées par carte : elles ne s'ouvrent ni au clic droit ni avec un redstone ordinaire, seulement quand un
 * lecteur de leur couleur, posé à côté (2 blocs max d'une de leurs pièces), accorde l'accès.
 */
public final class KeycardDoors {
   public static final Map<String, AccessLevel> LEVELS = Map.of(
      "silo_hatch", AccessLevel.GREEN, "cargo_door", AccessLevel.GREEN, "qe_sliding_door", AccessLevel.GREEN, "fire_door", AccessLevel.GREEN,
      "secure_access_door", AccessLevel.BLUE, "qe_containment_door", AccessLevel.BLUE, "water_door", AccessLevel.BLUE,
      "vault_door", AccessLevel.RED, "large_vehicle_door", AccessLevel.RED);
   private static final int READER_RANGE = 2, SEARCH_RADIUS = 5;

   @Nullable
   public static AccessLevel levelOf(Block block) {
      var id = ForgeRegistries.BLOCKS.getKey(block);
      return id == null ? null : LEVELS.get(id.getPath());
   }

   public static List<BlockPos> parts(DoorBlockEntity door) {
      List<BlockPos> list = new ArrayList<>();
      list.add(door.getBlockPos());
      if (door.getBlockState().getBlock() instanceof DoorBlock block) {
         list.addAll(block.getStructureHelper().getAllPartPositions(door.getBlockPos(), door.getFacing()));
      }
      return list;
   }

   /** Un lecteur de la bonne couleur, activé, à portée d'une des pièces de la porte ? */
   public static boolean readerGranted(Level level, List<BlockPos> parts, AccessLevel needed) {
      Set<BlockPos> seen = new LinkedHashSet<>();
      for (BlockPos part : parts) {
         for (BlockPos p : BlockPos.betweenClosed(part.offset(-READER_RANGE, -READER_RANGE, -READER_RANGE), part.offset(READER_RANGE, READER_RANGE, READER_RANGE))) {
            if (!seen.add(p.immutable())) continue;
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof CardReaderBlock reader && reader.level == needed && s.getValue(CardReaderBlock.POWERED)) {
               return true;
            }
         }
      }
      return false;
   }

   /** Portes (contrôleurs) de la couleur donnée proches d'un lecteur, la plus proche en premier. */
   public static List<DoorBlockEntity> doorsNear(Level level, BlockPos reader, AccessLevel needed) {
      Set<BlockPos> controllers = new LinkedHashSet<>();
      for (BlockPos p : BlockPos.betweenClosed(reader.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS), reader.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS))) {
         BlockState s = level.getBlockState(p);
         if (s.getBlock() instanceof DoorBlock) {
            controllers.add(p.immutable());
         } else if (s.getBlock() instanceof UniversalMachinePartBlock && level.getBlockEntity(p) instanceof IMultiblockPart part && part.getControllerPos() != null) {
            controllers.add(part.getControllerPos().immutable());
         }
      }
      List<DoorBlockEntity> out = new ArrayList<>();
      for (BlockPos c : controllers) {
         if (level.getBlockEntity(c) instanceof DoorBlockEntity door && levelOf(door.getBlockState().getBlock()) == needed) {
            out.add(door);
         }
      }
      out.sort((a, b) -> Double.compare(a.getBlockPos().distSqr(reader), b.getBlockPos().distSqr(reader)));
      return out;
   }

   /** Le lecteur a changé d'état : les portes de sa couleur à proximité réévaluent leur « alimentation ». */
   public static void notifyDoors(Level level, BlockPos reader, AccessLevel lvl) {
      for (DoorBlockEntity door : doorsNear(level, reader, lvl)) {
         door.checkRedstonePower();
      }
   }

   private KeycardDoors() {}
}
