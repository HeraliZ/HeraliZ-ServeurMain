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

   /**
    * Un lecteur de la bonne couleur, activé, à portée d'une des pièces de la porte ?
    * <p>24/10 (optimisation) : la boîte des pièces agrandie de la portée est lue une seule fois, puis chaque lecteur trouvé
    * est comparé aux pièces (même résultat qu'avant). Avant : un cube de 5 x 5 x 5 par pièce, rangé dans un ensemble
    * (plusieurs milliers de lectures pour une grande porte, toutes les 5 s par porte de salle à carte).</p>
    */
   public static boolean readerGranted(Level level, List<BlockPos> parts, AccessLevel needed) {
      if (parts.isEmpty()) return false;
      int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
      int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
      for (BlockPos p : parts) {
         x0 = Math.min(x0, p.getX());
         y0 = Math.min(y0, p.getY());
         z0 = Math.min(z0, p.getZ());
         x1 = Math.max(x1, p.getX());
         y1 = Math.max(y1, p.getY());
         z1 = Math.max(z1, p.getZ());
      }
      BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
      for (int x = x0 - READER_RANGE; x <= x1 + READER_RANGE; x++) {
         for (int y = y0 - READER_RANGE; y <= y1 + READER_RANGE; y++) {
            for (int z = z0 - READER_RANGE; z <= z1 + READER_RANGE; z++) {
               BlockState s = level.getBlockState(mp.set(x, y, z));
               if (s.getBlock() instanceof CardReaderBlock reader && reader.level == needed && s.getValue(CardReaderBlock.POWERED)
                  && aPortee(parts, x, y, z)) {
                  return true;
               }
            }
         }
      }
      return false;
   }

   /** Ce point est-il à READER_RANGE au plus (sur chaque axe) d'une des pièces ? */
   private static boolean aPortee(List<BlockPos> parts, int x, int y, int z) {
      for (BlockPos p : parts) {
         if (Math.abs(p.getX() - x) <= READER_RANGE && Math.abs(p.getY() - y) <= READER_RANGE && Math.abs(p.getZ() - z) <= READER_RANGE) {
            return true;
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
