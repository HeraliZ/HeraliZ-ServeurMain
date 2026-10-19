package fr.heralisz.camo.mixin;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.tooltip.ClientGunTooltip;
import com.tacz.guns.item.GunTooltipPart;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import fr.heraliz.tactique.armes.DegatsJoueurs;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Infobulle des armes TaCZ (choix du propriétaire, 14/10) : plus de lignes « perforation de l'armure Minecraft », « dégâts
 * de tir à la tête », « vitesse de déplacement » (partie EXTRA_DAMAGE_INFO de TaCZ), plus de « Appuyez sur … pour les
 * accessoires » (UPGRADES_TIP) ni du nom du gunpack (PACK_INFO) ; la hauteur et la largeur de l'infobulle suivent (même
 * test de TaCZ). Couteaux : plus non plus de munitions ni d'informations de base (niveau, type, dégâts). « Dégâts » : ceux contre un joueur (réglage du serveur, armes.DegatsJoueurs), pas ceux du gunpack.
 * Méthodes propres à TaCZ : pas de nom SRG. Sans effet si TaCZ change (require = 0).
 */
@Mixin(value = ClientGunTooltip.class, remap = false)
public abstract class ClientGunTooltipMixin {
   @Shadow
   @Final
   private ItemStack gun;
   @Shadow
   @Final
   private IGun iGun;

   @Inject(method = "shouldShow", at = @At("HEAD"), cancellable = true, require = 0)
   private void heraliz$masquer(GunTooltipPart part, CallbackInfoReturnable<Boolean> cir) {
      if (part == GunTooltipPart.EXTRA_DAMAGE_INFO || part == GunTooltipPart.UPGRADES_TIP || part == GunTooltipPart.PACK_INFO) {
         cir.setReturnValue(false);
      } else if ((part == GunTooltipPart.AMMO_INFO || part == GunTooltipPart.BASE_INFO)
         && fr.heraliz.tactique.onglets.Couteaux.estCouteau(this.gun)) {
         // couteaux (19/10) : ni munitions (« melee weapon 1/1 »), ni niveau, type « arme lourde » ou dégâts : seulement
         // le nom et la description
         cir.setReturnValue(false);
      }
   }

   @Redirect(method = "getText", at = @At(value = "INVOKE",
      target = "Lcom/tacz/guns/util/AttachmentDataUtils;getDamageWithAttachment(Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/resource/pojo/data/gun/GunData;)D"),
      require = 0)
   private double heraliz$degatsJoueur(ItemStack arme, GunData data) {
      double d = AttachmentDataUtils.getDamageWithAttachment(arme, data);
      if (this.iGun == null || data == null || data.getBulletData() == null) return d;
      return DegatsJoueurs.ajuster(this.iGun.getGunId(this.gun), d, data.getBulletData().getDamageAmount());
   }
}
