package fr.heralisz.n07.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Partie SERVEUR de {@link DecorBlock} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dès que la partie serveur est dans le jar (avec -complet, aussi côté client : la logique
 * serveur reste derrière isClientSide ou le ticker serveur, comme à l'origine) ; ne jamais faire « new DecorBlock(...) ».
 */
public class DecorBlockServeur extends DecorBlock {
   public DecorBlockServeur(DecorKind kind) {
      super(kind);
   }


   // ------------------------------------------------------------------ cases annexes : collisions et lumières
   /** Cases de collision, en coordonnées du monde. */
   public static List<BlockPos> parts(BlockPos pos, BlockState st, DecorSpec sp) {
      List<BlockPos> out = new ArrayList<>();
      Direction f = st.getValue(FACING);
      int dy = st.getValue(ON_RAIL) ? -1 : 0;
      for (int i = 0; i + 2 < sp.foot.length; i += 3) {
         int[] r = DecorSpec.rotate(sp.foot[i], sp.foot[i + 2], f);
         out.add(pos.offset(r[0], sp.foot[i + 1] + dy, r[1]));
      }
      return out;
   }

   /** Lumières (hors la case du bloc lui-même) : {position, niveau}. */
   public static List<Object[]> lights(BlockPos pos, BlockState st, DecorSpec sp) {
      List<Object[]> out = new ArrayList<>();
      Direction f = st.getValue(FACING);
      int drop = sp.drop ? st.getValue(DROP) : 0;
      int dy = st.getValue(ON_RAIL) ? -1 : 0;
      for (int i = 0; i + 3 < sp.lights.length; i += 4) {
         int ly = sp.lights[i + 1] - (sp.drop && sp.lights[i + 1] < 0 ? drop : 0) + dy;
         if (sp.lights[i] == 0 && ly == 0 && sp.lights[i + 2] == 0) continue;
         int[] r = DecorSpec.rotate(sp.lights[i], sp.lights[i + 2], f);
         out.add(new Object[] {pos.offset(r[0], ly, r[1]), sp.lights[i + 3]});
      }
      return out;
   }

   public static void build(Level level, BlockPos pos, BlockState st, DecorSpec sp) {
      for (BlockPos p : parts(pos, st, sp)) {
         BlockState cur = level.getBlockState(p);
         if (cur.isAir() || cur.canBeReplaced() && !(cur.getBlock() instanceof DecorBlock)) {
            level.setBlock(p, DecorRegistry.PART.get().defaultBlockState(), 3);
         }
      }
      for (Object[] l : lights(pos, st, sp)) {
         BlockPos p = (BlockPos) l[0];
         BlockState cur = level.getBlockState(p);
         if (cur.isAir() || cur.is(Blocks.LIGHT)) {
            level.setBlock(p, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, (Integer) l[1]), 3);
         }
      }
   }

   public static void clear(Level level, BlockPos pos, BlockState st, DecorSpec sp, boolean partsToo) {
      if (partsToo) {
         for (BlockPos p : parts(pos, st, sp)) {
            if (level.getBlockState(p).is(DecorRegistry.PART.get())) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
         }
      }
      for (Object[] l : lights(pos, st, sp)) {
         BlockPos p = (BlockPos) l[0];
         if (level.getBlockState(p).is(Blocks.LIGHT)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
      }
   }

   @Override
   @SuppressWarnings("deprecation")
   public void onPlace(BlockState st, Level level, BlockPos pos, BlockState old, boolean moving) {
      super.onPlace(st, level, pos, old, moving);
      if (level.isClientSide) return;
      if (old.is(this)) {
         if (old.getValue(DROP).equals(st.getValue(DROP)) && old.getValue(FACING) == st.getValue(FACING)
            && old.getValue(ON_RAIL).equals(st.getValue(ON_RAIL))) return;
         clear(level, pos, old, spec(), old.getValue(FACING) != st.getValue(FACING) || !old.getValue(ON_RAIL).equals(st.getValue(ON_RAIL)));
      }
      build(level, pos, st, spec());
   }

   @Override
   @SuppressWarnings("deprecation")
   public void onRemove(BlockState st, Level level, BlockPos pos, BlockState now, boolean moving) {
      if (!level.isClientSide && !now.is(this)) clear(level, pos, st, spec(), true);
      super.onRemove(st, level, pos, now, moving);
   }

   /** Clic droit à main nue sur une lanterne suspendue : rallonge la chaîne (puis revient au plus court). */
   @Override
   @SuppressWarnings("deprecation")
   public InteractionResult use(BlockState st, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (!spec().drop || !player.getItemInHand(hand).isEmpty() || !player.mayBuild()) return InteractionResult.PASS;
      if (!level.isClientSide) {
         int d = (st.getValue(DROP) + 1) % 4;
         level.setBlock(pos, st.setValue(DROP, d), 3);
         level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8F, 0.8F + d * 0.1F);
      }
      return InteractionResult.sidedSuccess(level.isClientSide);
   }
}
