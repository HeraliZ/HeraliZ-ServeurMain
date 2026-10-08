package fr.heralisz.n07.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link SludgeBlock} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dès que la partie serveur est dans le jar (avec -complet, aussi côté client : la logique
 * serveur reste derrière isClientSide ou le ticker serveur, comme à l'origine) ; ne jamais faire « new SludgeBlock(...) ».
 */
public class SludgeBlockServeur extends SludgeBlock {
   public SludgeBlockServeur(Properties props) {
      super(props);
   }


   @Override
   @SuppressWarnings("deprecation")
   public void entityInside(BlockState state, Level level, BlockPos pos, Entity e) {
      e.makeStuckInBlock(state, new Vec3(0.6, 0.5, 0.6));
      if (!level.isClientSide && e instanceof LivingEntity le && level.getGameTime() % 20 == 0) {
         le.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
      }
   }
}
