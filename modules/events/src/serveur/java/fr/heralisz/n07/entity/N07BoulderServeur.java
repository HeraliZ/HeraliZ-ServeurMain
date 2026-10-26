package fr.heralisz.n07.entity;

import fr.heralisz.n07.HeraliszN07;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Partie SERVEUR de {@link N07Boulder} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par SujetN07Serveur (new N07BoulderServeur, comme l'ancien constructeur) ou par l'usine du code commun (mondes serveur) ;
 * ne jamais faire « new N07Boulder(...) » côté serveur.
 */
public class N07BoulderServeur extends N07Boulder {
   public N07BoulderServeur(EntityType<? extends N07Boulder> type, Level level) {
      super(type, level);
   }

   /** Ancien constructeur N07Boulder(level, owner) : bloc arraché par le boss (le propriétaire est une donnée serveur). */
   public N07BoulderServeur(Level level, SujetN07Serveur owner) {
      this(HeraliszN07.N07_BOULDER.get(), level);
      this.owner = owner;
   }

   private SujetN07Serveur owner;

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         if (thrown() && this.tickCount % 2 == 0) {
            this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAY_CONCRETE.defaultBlockState()), getX(), getY() + 0.6, getZ(), 0, 0, 0);
         }
         return;
      }
      ServerLevel level = (ServerLevel) this.level();
      if (owner == null || !owner.isAlive() && isHeld() || this.tickCount > 160) {
         this.discard();
         return;
      }
      if (!thrown()) return;
      Vec3 v = this.getDeltaMovement();
      Vec3 from = this.position().add(0, 0.6, 0), to = from.add(v);
      BlockHitResult r = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
      Player hitP = null;
      for (Player p : level.getEntitiesOfClass(Player.class, new AABB(from, to).inflate(1.1))) {
         if (!p.isCreative() && !p.isSpectator()) {
            hitP = p;
            break;
         }
      }
      if (r.getType() != HitResult.Type.MISS || hitP != null || this.tickCount - thrownAt > 60) {
         Vec3 at = hitP != null ? hitP.position() : r.getType() != HitResult.Type.MISS ? r.getLocation() : to;
         double gy = Ground.floorY(level, at.x, at.y + 0.6, at.z);
         owner.boulderImpact(level, new Vec3(at.x, Math.min(Math.max(at.y, gy), at.y + 1), at.z));
         this.discard();
         return;
      }
      this.setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
      this.setDeltaMovement(v.x, v.y - G, v.z);
   }
}
