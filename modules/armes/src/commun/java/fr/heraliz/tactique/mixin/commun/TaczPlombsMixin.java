package fr.heraliz.tactique.mixin.commun;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.item.ModernKineticGunItem;
import fr.heraliz.tactique.armes.PlombsArmes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Armes à plombs (retour du propriétaire du 02/11) : TOUS les fusils à pompe dispersent leurs plombs. Réglages de
 * {@code armes.json} ({@link PlombsArmes}, remplis par le serveur) appliqués aux propriétés que TaCZ demande au moment du
 * tir (ModernKineticGunScriptAPI.shootOnce et EntityKineticBullet, par {@code modifyProperty}) :
 * <ul>
 *   <li>« bullet_amount » : nombre de plombs imposé (TaCZ partage ensuite les dégâts entre eux :
 *   EntityKineticBullet.applyShotgunDamageSpread) ;</li>
 *   <li>« inaccuracy » (dispersion du tir, en degrés) : jamais sous « dispersion_min », même en visant ;</li>
 *   <li>« pierce » : un plomb imposé ne traverse pas un joueur pour en toucher un autre (l'ancienne balle unique du DB-4
 *   Ursus en traversait 5).</li>
 * </ul>
 * Seul le serveur décide (la table reste vide chez le joueur). Cible : ModernKineticGunItem.modifyProperty de TaCZ 1.1.8
 * (fin de la méthode, après les accessoires et le script de l'arme) ; noms de TaCZ : remap = false. require = 0 : si
 * TaCZ change, les plombs du gunpack reviennent, rien ne plante.
 */
@Mixin(value = ModernKineticGunItem.class, remap = false)
public abstract class TaczPlombsMixin {
   @Inject(
      method = "modifyProperty(Lcom/tacz/guns/entity/shooter/ShooterDataHolder;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/Object;)Ljava/lang/Object;",
      at = @At("RETURN"),
      cancellable = true,
      require = 0
   )
   private void heraliz$plombs(ShooterDataHolder donnees, ItemStack arme, LivingEntity tireur, String methodeLua, String id,
                               Class<?> type, Object origine, CallbackInfoReturnable<Object> cir) {
      if (PlombsArmes.vide() || tireur == null || id == null || tireur.level().isClientSide || !"modify_property".equals(methodeLua)) {
         return;
      }
      Object valeur = cir.getReturnValue();
      switch (id) {
         case "bullet_amount" -> {
            PlombsArmes.Reglage r = this.heraliz$reglage(arme);
            if (r != null && r.plombs() > 1 && valeur instanceof Integer) cir.setReturnValue(r.plombs());
         }
         case "inaccuracy" -> {
            if (valeur instanceof Float f) {
               PlombsArmes.Reglage r = this.heraliz$reglage(arme);
               if (r != null && r.dispersionMin() > f) cir.setReturnValue(r.dispersionMin());
            }
         }
         case "pierce" -> {
            if (valeur instanceof Integer n && n > 1) {
               PlombsArmes.Reglage r = this.heraliz$reglage(arme);
               if (r != null && r.plombs() > 1) cir.setReturnValue(1);
            }
         }
         default -> {
         }
      }
   }

   @Unique
   private PlombsArmes.Reglage heraliz$reglage(ItemStack arme) {
      return arme == null || arme.isEmpty() ? null : PlombsArmes.de(((IGun) (Object) this).getGunId(arme));
   }
}
