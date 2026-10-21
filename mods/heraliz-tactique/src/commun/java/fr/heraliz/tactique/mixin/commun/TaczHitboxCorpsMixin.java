package fr.heraliz.tactique.mixin.commun;

import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.util.EntityUtil;
import com.tacz.guns.util.HitboxHelper;
import fr.heraliz.tactique.hitbox.HitboxCorps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Balles de TaCZ contre un joueur : la hitbox « en morceaux » (fr.heraliz.tactique.hitbox.HitboxCorps) remplace la
 * boîte droite de Minecraft. La balle ne touche que là où le corps est vraiment (debout, accroupi, allongé, à terre,
 * assis) ; tir à la tête = morceau « tête ». La place du joueur reste celle que TaCZ corrige pour le ping du tireur
 * (HitboxHelper.getFixedBoundingBox, train compris : son bas au centre = les pieds).
 * <p>
 * Cible : EntityUtil.getHitResult(Projectile, Entity, Vec3, Vec3) de TaCZ 1.1.8 (null = manqué). Seul le serveur décide.
 * Noms de TaCZ : remap = false. require = 0 : si TaCZ change cette fonction, la boîte de TaCZ revient, rien ne plante.
 */
@Mixin(value = EntityUtil.class, remap = false)
public abstract class TaczHitboxCorpsMixin {
   @Inject(
      method = "getHitResult(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Lcom/tacz/guns/entity/EntityKineticBullet$EntityResult;",
      at = @At("HEAD"),
      cancellable = true,
      require = 0
   )
   private static void heraliz$hitboxCorps(Projectile balle, Entity cible, Vec3 de, Vec3 a, CallbackInfoReturnable<EntityKineticBullet.EntityResult> cir) {
      if (!HitboxCorps.actif || !(cible instanceof Player joueur) || cible.level().isClientSide || joueur.isSleeping()) {
         return;
      }
      AABB boite = HitboxHelper.getFixedBoundingBox(cible, balle.getOwner());
      Vec3 pieds = new Vec3((boite.minX + boite.maxX) / 2.0, boite.minY, (boite.minZ + boite.maxZ) / 2.0);
      HitboxCorps.Touche t = HitboxCorps.toucher(HitboxCorps.morceaux(joueur, pieds, 1.0F), de, a);
      cir.setReturnValue(t == null ? null : new EntityKineticBullet.EntityResult(cible, de.add(a.subtract(de).scale(t.t())), t.morceau().tete()));
   }
}
