package fr.heraliz.tactique.mixin.client;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.block.BlockRayTrace;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Balles qui « traversent » les murs à l'écran (27/10, demande du propriétaire : surtout les murs des bases). Seul le serveur
 * calcule les impacts de TaCZ : il arrête la balle sur le mur (le mur ne prend aucun dégât), mais le jeu du joueur continue
 * de la faire avancer, traînée lumineuse comprise, jusqu'à ce que le serveur lui dise de la retirer (un tick et le ping plus
 * tard : à 15 blocs par tick, la traînée passait de l'autre côté du mur). Chez le joueur, la balle suit maintenant le même
 * trajet bloc par bloc que sur le serveur (BlockRayTrace de TaCZ, mêmes blocs traversés : verre, feuilles...) : le tick où
 * elle touche un bloc, elle s'arrête au point d'impact, puis disparaît au tick suivant. Affichage seulement : dégâts, trous de
 * balle et sons restent décidés par le serveur.
 * <p>
 * Cible : {@code onBulletTick} de EntityKineticBullet (TaCZ 1.1.8), appelée à chaque tick avant que la balle avance de sa
 * vitesse (côté joueur, TaCZ n'y fait rien). Méthode propre à TaCZ : remap = false ; sans effet si TaCZ change (require = 0).
 */
@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class TaczBalleArretMixin {
   /** La balle a touché un bloc au tick d'avant (chez le joueur) : elle disparaît. */
   @Unique
   private boolean heraliz$arretee;

   @Inject(method = "onBulletTick", at = @At("HEAD"), require = 0)
   private void heraliz$arretSurLeMur(CallbackInfo ci) {
      EntityKineticBullet balle = (EntityKineticBullet) (Object) this;
      Level level = balle.level();
      if (!level.isClientSide || balle.isRemoved()) {
         return;
      }
      if (this.heraliz$arretee) {
         balle.discard();
         return;
      }
      Vec3 de = balle.position();
      Vec3 vitesse = balle.getDeltaMovement();
      if (vitesse.lengthSqr() < 1.0E-6D) {
         return;
      }
      BlockHitResult r = BlockRayTrace.rayTraceBlocks(level, new ClipContext(de, de.add(vitesse), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, balle));
      if (r.getType() == HitResult.Type.MISS) {
         return;
      }
      // ce tick, la balle avance jusqu'au point d'impact (même sens : traînée et rotation inchangées), puis s'arrête
      balle.setDeltaMovement(r.getLocation().subtract(de));
      this.heraliz$arretee = true;
   }
}
