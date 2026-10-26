package fr.heralisz.n07.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;

/**
 * Grille de l'arène : taille de l'ouverture (écrite par la structure du donjon, synchronisée pour le rendu). La fermeture /
 * ouverture est une action serveur : ArenaGates.setClosed (docs/METHODE_CLIENT_SERVEUR.md §5.2b).
 */
public class ArenaGateBlockEntity extends BlockEntity {
   /** Largeur (impaire de préférence, centrée sur le bloc) et hauteur de l'ouverture, en blocs. */
   public int width = 5, height = 4;
   /** Côté client : instant du dernier changement d'état vu (animation). */
   public long clientChange = Long.MIN_VALUE;
   public boolean clientClosed;

   public ArenaGateBlockEntity(BlockPos pos, BlockState st) {
      super(DungeonRegistry.GATE_BE.get(), pos, st);
   }

   public Direction facing() {
      BlockState s = getBlockState();
      return s.hasProperty(ArenaGateBlock.FACING) ? s.getValue(ArenaGateBlock.FACING) : Direction.NORTH;
   }

   /** Cases de l'ouverture (hors le bloc de la grille lui-même). */
   public List<BlockPos> cells() {
      List<BlockPos> out = new ArrayList<>();
      Direction side = facing().getClockWise();
      int half = width / 2;
      for (int dx = -half; dx <= width - 1 - half; dx++) {
         for (int dy = 0; dy < height; dy++) {
            if (dx == 0 && dy == 0) continue;
            out.add(worldPosition.relative(side, dx).above(dy));
         }
      }
      return out;
   }

   @Override
   protected void saveAdditional(CompoundTag tag) {
      super.saveAdditional(tag);
      tag.putInt("Width", width);
      tag.putInt("Height", height);
   }

   @Override
   public void load(CompoundTag tag) {
      super.load(tag);
      if (tag.contains("Width")) width = tag.getInt("Width");
      if (tag.contains("Height")) height = tag.getInt("Height");
   }

   @Override
   public CompoundTag getUpdateTag() {
      CompoundTag t = new CompoundTag();
      saveAdditional(t);
      return t;
   }

   @Override
   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   @Override
   public AABB getRenderBoundingBox() {
      return new AABB(worldPosition).inflate(Math.max(width, height) + 1);
   }
}
