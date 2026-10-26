package fr.heralisz.n07.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Sirène : rien côté serveur ; le rendu lit l'état (LIT) pour faire tourner les faisceaux. */
public class SirenBlockEntity extends BlockEntity {
   public final float phase;

   public SirenBlockEntity(BlockPos pos, BlockState st) {
      super(DungeonRegistry.SIREN_BE.get(), pos, st);
      this.phase = (float) Math.floorMod(pos.asLong() * 2654435761L, 1000) / 1000F;
   }

   @Override
   public AABB getRenderBoundingBox() {
      return new AABB(worldPosition).inflate(9);
   }
}
