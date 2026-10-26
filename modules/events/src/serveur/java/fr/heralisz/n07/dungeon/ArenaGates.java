package fr.heralisz.n07.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Partie SERVEUR de la grille d'arène (docs/METHODE_CLIENT_SERVEUR.md §5.2b) : l'entité de bloc {@link ArenaGateBlockEntity}
 * reste commune (taille de l'ouverture, synchronisée pour le rendu) ; seule l'action « fermer / ouvrir » est ici.
 */
public final class ArenaGates {
   private ArenaGates() {}

   /** Ferme (bouche l'entrée ; ceux qui sont dans l'embrasure passent côté arène) ou ouvre la grille. */
   public static void setClosed(ArenaGateBlockEntity be, ServerLevel level, boolean closed) {
      BlockState st = be.getBlockState();
      if (!(st.getBlock() instanceof ArenaGateBlock) || st.getValue(ArenaGateBlock.CLOSED) == closed) return;
      List<BlockPos> cells = be.cells();
      if (closed) {
         AABB box = new AABB(be.getBlockPos());
         for (BlockPos p : cells) box = box.minmax(new AABB(p));
         Vec3 in = Vec3.atLowerCornerOf(be.facing().getNormal());
         for (Player p : level.getEntitiesOfClass(Player.class, box.inflate(0.3))) {
            p.teleportTo(p.getX() + in.x * 1.6, p.getY(), p.getZ() + in.z * 1.6);
         }
         for (BlockPos p : cells) if (level.getBlockState(p).isAir()) level.setBlock(p, DungeonRegistry.PROP_SOLID.get().defaultBlockState(), 3);
      } else {
         for (BlockPos p : cells) if (level.getBlockState(p).is(DungeonRegistry.PROP_SOLID.get())) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
      }
      level.setBlock(be.getBlockPos(), st.setValue(ArenaGateBlock.CLOSED, closed), 3);
   }
}
