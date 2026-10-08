package fr.heralisz.n07.dungeon;

import fr.heralisz.n07.sound.BossSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Teleporteur du Groupe 935 (salle du N-07 en mode Zombies) : plateforme 3 x 3 dessinee depuis un seul bloc (modele de bloc
 * cuit dans les chunks : aucun cout par image). Eteint jusqu'a la victoire ; allume, il crepite (particules client) et ramene
 * les joueurs a la carte : rester dessus ~1,5 s ou clic droit (le jeu fait la teleportation, voir N07Events.onReturnRequested).
 */
public class Teleporter935Block extends Block {
   public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
   private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

   public Teleporter935Block() {
      super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable().sound(SoundType.METAL)
         .lightLevel(s -> s.getValue(ACTIVE) ? 12 : 3).isValidSpawn((s, l, p, e) -> false));
      this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(ACTIVE);
   }

   @Override
   @SuppressWarnings("deprecation")
   public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
      return SHAPE;
   }

   /** Allumé : le clic est accepté (bras levé côté client) ; la demande de retour au jeu est dans Teleporter935BlockServeur. */
   @Override
   @SuppressWarnings("deprecation")
   public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (!s.getValue(ACTIVE)) return InteractionResult.PASS;
      return InteractionResult.sidedSuccess(level.isClientSide);
   }

   @Override
   public void animateTick(BlockState s, Level level, BlockPos pos, RandomSource r) {
      if (!s.getValue(ACTIVE)) return;
      double cx = pos.getX() + 0.5, cy = pos.getY() + 0.12, cz = pos.getZ() + 0.5;
      for (int i = 0; i < 3; i++) {
         double a = r.nextDouble() * Math.PI * 2, rr = 1.1 + r.nextDouble() * 0.6;
         level.addParticle(ParticleTypes.ELECTRIC_SPARK, cx + Math.cos(a) * rr, cy + r.nextDouble() * 0.2, cz + Math.sin(a) * rr, 0, 0.05, 0);
      }
      // calottes des bobines
      if (r.nextInt(3) == 0) {
         int k = r.nextInt(4);
         double ox = (k & 1) == 0 ? -0.6 : 1.6, oz = (k & 2) == 0 ? -0.6 : 1.6;
         level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + ox, pos.getY() + 1.4, pos.getZ() + oz, (r.nextDouble() - 0.5) * 0.2, 0.05,
            (r.nextDouble() - 0.5) * 0.2);
      }
      if (r.nextInt(4) == 0) level.addParticle(ParticleTypes.END_ROD, cx + (r.nextDouble() - 0.5) * 1.5, cy + 0.2, cz + (r.nextDouble() - 0.5) * 1.5, 0,
         0.04 + r.nextDouble() * 0.04, 0);
      if (r.nextInt(40) == 0) level.playLocalSound(cx, cy + 1, cz, BossSounds.DG_ARC_ZAP.get(), SoundSource.BLOCKS, 0.5F, 1.3F + r.nextFloat() * 0.3F, false);
   }
}
