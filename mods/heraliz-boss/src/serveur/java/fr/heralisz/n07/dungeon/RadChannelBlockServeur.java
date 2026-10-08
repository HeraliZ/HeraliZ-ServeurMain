package fr.heralisz.n07.dungeon;

import fr.heralisz.n07.HeraliszN07;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Partie SERVEUR de {@link RadChannelBlock} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dès que la partie serveur est dans le jar (avec -complet, aussi côté client : la logique
 * serveur reste derrière isClientSide ou le ticker serveur, comme à l'origine) ; ne jamais faire « new RadChannelBlock(...) ».
 */
public class RadChannelBlockServeur extends RadChannelBlock {
   public RadChannelBlockServeur(Params a) {
      super(a);
   }

   public RadChannelBlockServeur(Properties p, boolean sludge) {
      super(p, sludge);
   }

   public RadChannelBlockServeur(Properties p, boolean sludge, boolean lava) {
      super(p, sludge, lava);
   }


   @Override
   @SuppressWarnings("deprecation")
   public void entityInside(BlockState s, Level level, BlockPos pos, Entity e) {
      if (s.getValue(COVERED) || s.getValue(DRY) || !(e instanceof LivingEntity le) || e.getY() >= pos.getY() + LEVEL / 16.0 + 0.05) return;
      if (lava) {
         le.makeStuckInBlock(s, new net.minecraft.world.phys.Vec3(0.6, 0.8, 0.6));
         if (!level.isClientSide && !le.fireImmune()) {
            le.setSecondsOnFire(6);
            if (level.getGameTime() % 10 == 0) le.hurt(level.damageSources().lava(), 3.0F);
         }
         return;
      }
      // la boue colle aux bottes (des deux côtés : le client déplace lui-même le joueur)
      if (sludge) le.makeStuckInBlock(s, new net.minecraft.world.phys.Vec3(0.75, 0.9, 0.75));
      if (!level.isClientSide && level.getGameTime() % 20 == 0) {
         le.addEffect(sludge ? new MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 60, 0)
            : new MobEffectInstance(HeraliszN07.IRRADIATED.get(), 60, 0));
      }
   }
}
