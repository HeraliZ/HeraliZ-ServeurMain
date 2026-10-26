package fr.heralisz.n07.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Case invisible et solide d'un grand décor (le train, la tente, l'épave...) : on s'y cogne, on s'y abrite, mais elle ne se voit
 * pas et laisse passer la lumière. En créatif on peut la viser : la casser casse tout le décor.
 */
public class DecorPartBlock extends Block {
   public DecorPartBlock(Properties p) {
      super(p);
   }

   @Override
   @SuppressWarnings("deprecation")
   public RenderShape getRenderShape(BlockState state) {
      return RenderShape.INVISIBLE;
   }

   /** Visée : seulement en créatif (en survie, pas de cadre qui trahit la grille). */
   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return ctx instanceof EntityCollisionContext ec && ec.getEntity() instanceof Player p && p.isCreative() ? Shapes.block() : Shapes.empty();
   }

   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return Shapes.block();
   }

   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return Shapes.empty();
   }

   @Override
   @SuppressWarnings("deprecation")
   public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
      return 1.0F;
   }

   @Override
   public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
      return true;
   }
}
