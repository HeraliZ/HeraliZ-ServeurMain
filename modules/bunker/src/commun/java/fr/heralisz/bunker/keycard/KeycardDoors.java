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
import java.util.function.Predicate;

/**
 * Portes verrouillées par carte : elles ne s'ouvrent ni au clic droit ni avec un redstone ordinaire, seulement quand un
 * lecteur de leur couleur, posé à côté (2 blocs max d'une de leurs pièces), accorde l'accès.
 * <p>Portes de bunker (17/11, demande du propriétaire : l'écluse à air ronde ferme les deux bunkers de la carte) : une
 * porte ordinaire désignée par le staff ({@code /salle bunker ajouter}, gardée dans la porte : {@link DoorBlockEntity#estBunker()},
 * jamais toutes les portes d'un type, les joueurs en posent dans leurs bases) ne s'ouvre plus qu'avec un lecteur posé à
 * côté, de n'importe quelle couleur (le lecteur posé choisit la carte qu'il faut) ; ouverte, elle le reste un long moment,
 * annoncé à tout le serveur (BunkerPont).</p>
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
    * Un lecteur de la bonne couleur (null : n'importe laquelle, portes de bunker), activé, à portée d'une des pièces de la porte ?
    * <p>24/10 (optimisation) : la boîte des pièces agrandie de la portée est lue une seule fois, puis chaque lecteur trouvé
    * est comparé aux pièces (même résultat qu'avant). Avant : un cube de 5 x 5 x 5 par pièce, rangé dans un ensemble
    * (plusieurs milliers de lectures pour une grande porte, toutes les 5 s par porte de salle à carte).</p>
    */
   public static boolean readerGranted(Level level, List<BlockPos> parts, @Nullable AccessLevel needed) {
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
               if (s.getBlock() instanceof CardReaderBlock reader && (needed == null || reader.level == needed) && s.getValue(CardReaderBlock.POWERED)
                  && aPortee(parts, x, y, z)) {
                  return true;
               }
            }
         }
      }
      return false;
   }

   /**
    * 21/11 (audit) : les tronçons de la porte et de ses lecteurs ({@link #readerGranted} : ses pièces et READER_RANGE
    * autour) sont-ils tous complets ? Sinon, lire un de leurs blocs ferait attendre au fil du serveur la fin de leur
    * chargement (et tout le travail de chargement en file pendant ce temps) : porte d'une salle à carte au bord de la vue
    * d'un joueur, son lecteur juste à côté dans un tronçon encore en lecture. Ne charge rien, n'attend rien.
    */
   public static boolean lecteursPrets(Level level, List<BlockPos> parts) {
      if (parts.isEmpty()) return false;
      int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
      for (BlockPos p : parts) {
         x0 = Math.min(x0, p.getX());
         z0 = Math.min(z0, p.getZ());
         x1 = Math.max(x1, p.getX());
         z1 = Math.max(z1, p.getZ());
      }
      for (int cx = (x0 - READER_RANGE) >> 4; cx <= (x1 + READER_RANGE) >> 4; cx++) {
         for (int cz = (z0 - READER_RANGE) >> 4; cz <= (z1 + READER_RANGE) >> 4; cz++) {
            if (level.getChunkSource().getChunkNow(cx, cz) == null) return false;
         }
      }
      return true;
   }

   /** Lecteurs (toutes couleurs, actifs ou non) à portée d'une des pièces de la porte : pour le staff (/salle bunker). */
   public static int lecteursAPortee(Level level, List<BlockPos> parts) {
      if (parts.isEmpty()) return 0;
      BlockPos min = parts.get(0), max = parts.get(0);
      for (BlockPos p : parts) {
         min = new BlockPos(Math.min(min.getX(), p.getX()), Math.min(min.getY(), p.getY()), Math.min(min.getZ(), p.getZ()));
         max = new BlockPos(Math.max(max.getX(), p.getX()), Math.max(max.getY(), p.getY()), Math.max(max.getZ(), p.getZ()));
      }
      int n = 0;
      for (BlockPos p : BlockPos.betweenClosed(min.offset(-READER_RANGE, -READER_RANGE, -READER_RANGE), max.offset(READER_RANGE, READER_RANGE, READER_RANGE))) {
         if (level.getBlockState(p).getBlock() instanceof CardReaderBlock && aPortee(parts, p.getX(), p.getY(), p.getZ())) n++;
      }
      return n;
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
      return portesPres(level, reader, d -> levelOf(d.getBlockState().getBlock()) == needed);
   }

   /**
    * Portes qu'un lecteur de cette couleur ouvre : ses portes à carte et les portes de bunker proches, la plus proche en
    * premier (les salles à carte ne voient que les premières : {@link #doorsNear}).
    */
   public static List<DoorBlockEntity> portesDuLecteur(Level level, BlockPos reader, AccessLevel lvl) {
      return portesPres(level, reader, d -> d.estBunker() || levelOf(d.getBlockState().getBlock()) == lvl);
   }

   /** Toutes les portes (contrôleurs) proches de ce point, la plus proche en premier : pour le staff (/salle bunker). */
   public static List<DoorBlockEntity> toutesPortesPres(Level level, BlockPos pos) {
      return portesPres(level, pos, d -> true);
   }

   private static List<DoorBlockEntity> portesPres(Level level, BlockPos reader, Predicate<DoorBlockEntity> garder) {
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
         if (level.getBlockEntity(c) instanceof DoorBlockEntity door && garder.test(door)) {
            out.add(door);
         }
      }
      out.sort((a, b) -> Double.compare(a.getBlockPos().distSqr(reader), b.getBlockPos().distSqr(reader)));
      return out;
   }

   /** Le lecteur a changé d'état : les portes qu'il ouvre (sa couleur, bunker) réévaluent leur « alimentation ». */
   public static void notifyDoors(Level level, BlockPos reader, AccessLevel lvl) {
      for (DoorBlockEntity door : portesDuLecteur(level, reader, lvl)) {
         door.checkRedstonePower();
      }
   }

   private KeycardDoors() {}
}
