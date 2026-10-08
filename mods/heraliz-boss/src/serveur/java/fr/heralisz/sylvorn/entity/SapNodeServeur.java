package fr.heralisz.sylvorn.entity;

import fr.heralisz.sylvorn.particle.ModParticles;
import fr.heralisz.sylvorn.sound.Sounds;
import fr.heralisz.sylvorn.fvfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link SapNode} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new SapNode(...) » côté serveur.
 */
public class SapNodeServeur extends SapNode {
   public SapNodeServeur(EntityType<? extends SapNode> type, Level level) {
      super(type, level);
   }

   /** Ce que faisait le constructeur d'origine SapNode(level, owner, pos) : le nœud est créé par son type (usine). */
   public void setup(Sylvorn owner, Vec3 pos) {
      this.ownerId = owner.getId();
      this.setPos(pos.x, pos.y, pos.z);
   }

   @Override
   public boolean hurt(DamageSource s, float amount) {
      if (s.getEntity() instanceof Sylvorn) return false;
      boolean ok = super.hurt(s, amount);
      if (ok && this.level() instanceof ServerLevel sl) {
         sl.sendParticles(ModParticles.SPORE.get(), getX(), getY() + 1.5, getZ(), 4, 0.3, 0.3, 0.3, 0.08);
         sl.playSound(null, getX(), getY(), getZ(), Sounds.FX_GOO.get(), SoundSource.HOSTILE, 1.2F, 0.6F);
      }
      return ok;
   }

   /** Éclat du nœud abattu (flash, éclats, sons) : appelé par SapNode.die dans le monde serveur. */
   @Override
   void effetsMort(ServerLevel sl) {
      Vfx.flash(sl, this.position().add(0, 1.5, 0), 3F, 6, 0xFFC060);
      Vfx.chips(sl, this.position().add(0, 1, 0), 0.8F, 5F, 12, ModParticles.SPORE.get());
      sl.playSound(null, getX(), getY(), getZ(), Sounds.FX_WOOD.get(), SoundSource.HOSTILE, 2F, 0.6F);
      sl.playSound(null, getX(), getY(), getZ(), Sounds.FX_GOO.get(), SoundSource.HOSTILE, 2F, 0.4F);
   }
}
