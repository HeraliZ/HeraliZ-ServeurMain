package fr.heralisz.n07.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Feu d'alerte d'un donjon : éteint au repos, il s'allume quand le boss s'éveille et s'éteint à sa mort (le sanctuaire le
 * commande, comme les sirènes). Simple modèle de bloc (aucun rendu par image) ; les particules viennent de animateTick côté
 * client, seulement quand il est allumé. Brasero (chapelle), feu follet (tertre), sac d'œufs (nid).
 */
public class AlertLightBlock extends Block {
   public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
   public static final BooleanProperty LIT = BlockStateProperties.LIT;

   public enum Style {
      BRAZIER(15, Block.box(1, 0, 1, 15, 14, 15), true),
      WISP(12, Block.box(3, 0, 3, 13, 12, 13), true),
      EGGSAC(10, Block.box(1, 0, 1, 15, 15, 15), false);

      final int light;
      final VoxelShape shape;
      final boolean solid;

      Style(int light, VoxelShape shape, boolean solid) {
         this.light = light;
         this.shape = shape;
         this.solid = solid;
      }
   }

   public final Style style;

   public AlertLightBlock(Style style) {
      super(BlockBehaviour.Properties.of().mapColor(style == Style.EGGSAC ? MapColor.COLOR_ORANGE : MapColor.METAL).strength(-1.0F, 3600000.0F)
         .noOcclusion().noLootTable().sound(style == Style.EGGSAC ? SoundType.SLIME_BLOCK : SoundType.METAL)
         .lightLevel(s -> s.getValue(LIT) ? style.light : 0));
      this.style = style;
      this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(FACING, LIT);
   }

   @Override
   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
   }

   @Override
   @SuppressWarnings("deprecation")
   public BlockState rotate(BlockState s, Rotation r) {
      return s.setValue(FACING, r.rotate(s.getValue(FACING)));
   }

   @Override
   @SuppressWarnings("deprecation")
   public BlockState mirror(BlockState s, Mirror m) {
      return s.rotate(m.getRotation(s.getValue(FACING)));
   }

   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return style.shape;
   }

   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return style.solid ? style.shape : net.minecraft.world.phys.shapes.Shapes.empty();
   }

   @Override
   public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
      if (!state.getValue(LIT)) return;
      double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
      switch (style) {
         case BRAZIER -> {
            for (int i = 0; i < 3; i++) {
               level.addParticle(ParticleTypes.FLAME, x + (r.nextDouble() - 0.5) * 0.5, y + 0.9 + r.nextDouble() * 0.3, z + (r.nextDouble() - 0.5) * 0.5,
                  0, 0.03 + r.nextDouble() * 0.03, 0);
            }
            if (r.nextInt(3) == 0) level.addParticle(ParticleTypes.LARGE_SMOKE, x, y + 1.3, z, 0, 0.04, 0);
            if (r.nextInt(4) == 0) level.addParticle(ParticleTypes.LAVA, x, y + 1.0, z, 0, 0, 0);
            if (r.nextInt(20) == 0) {
               level.playLocalSound(x, y + 1, z, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.8F, 0.8F + r.nextFloat() * 0.3F, false);
            }
         }
         case WISP -> {
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + (r.nextDouble() - 0.5) * 0.25, y + 0.95, z + (r.nextDouble() - 0.5) * 0.25, 0, 0.02, 0);
            if (r.nextInt(2) == 0) {
               level.addParticle(ParticleTypes.GLOW, x + (r.nextDouble() - 0.5) * 1.6, y + 0.6 + r.nextDouble() * 1.4, z + (r.nextDouble() - 0.5) * 1.6,
                  (r.nextDouble() - 0.5) * 0.04, 0.02, (r.nextDouble() - 0.5) * 0.04);
            }
         }
         case EGGSAC -> {
            if (r.nextInt(2) == 0) {
               level.addParticle(ParticleTypes.DRIPPING_HONEY, x + (r.nextDouble() - 0.5) * 0.7, y + 0.2 + r.nextDouble() * 0.6, z + (r.nextDouble() - 0.5) * 0.7,
                  0, 0, 0);
            }
            if (r.nextInt(12) == 0) {
               level.playLocalSound(x, y + 0.5, z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.BLOCKS, 0.6F, 0.5F + r.nextFloat() * 0.3F, false);
            }
         }
      }
   }
}
