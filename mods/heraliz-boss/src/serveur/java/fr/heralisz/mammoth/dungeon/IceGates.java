package fr.heralisz.mammoth.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Partie SERVEUR du mur de glace (docs/METHODE_CLIENT_SERVEUR.md §5.2b) : l'entité de bloc {@link IceGateBlockEntity} reste
 * commune (taille de l'ouverture, synchronisée pour le rendu) ; seule l'action « fermer / ouvrir » est ici.
 */
public final class IceGates {
   private IceGates() {}

   /** Ferme (ceux qui sont dans l'embrasure passent côté arène) ou ouvre. */
   public static void setClosed(IceGateBlockEntity be, ServerLevel level, boolean closed) {
      BlockState st = be.getBlockState();
      if (!(st.getBlock() instanceof IceGateBlock) || st.getValue(IceGateBlock.CLOSED) == closed) return;
      List<BlockPos> cells = be.cells();
      if (closed) {
         AABB box = new AABB(be.getBlockPos());
         for (BlockPos p : cells) box = box.minmax(new AABB(p));
         Vec3 in = Vec3.atLowerCornerOf(be.facing().getNormal());
         for (Player p : level.getEntitiesOfClass(Player.class, box.inflate(0.3))) p.teleportTo(p.getX() + in.x * 1.6, p.getY(), p.getZ() + in.z * 1.6);
         for (BlockPos p : cells) if (level.getBlockState(p).isAir()) level.setBlock(p, ModBlocks.PROP_SOLID.get().defaultBlockState(), 3);
      } else {
         for (BlockPos p : cells) if (level.getBlockState(p).is(ModBlocks.PROP_SOLID.get())) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
      }
      level.setBlock(be.getBlockPos(), st.setValue(IceGateBlock.CLOSED, closed), 3);
   }
}
