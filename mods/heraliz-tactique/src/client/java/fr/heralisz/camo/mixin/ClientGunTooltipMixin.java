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
 * Depuis le 21/10 (choix du propriétaire) : plus de description des armes à feu (celle des couteaux garde le rang en
 * étoiles du skin) ni de ligne « Niveau 0 (MAX) » : le type prend sa place et l'infobulle perd une ligne.
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
   @Shadow
   private net.minecraft.network.chat.MutableComponent levelInfo;
   @Shadow
   private net.minecraft.network.chat.MutableComponent gunType;
   @Shadow
   private net.minecraft.network.chat.MutableComponent damage;
   private boolean heraliz$sansNiveau;

   @Inject(method = "shouldShow", at = @At("HEAD"), cancellable = true, require = 0)
   private void heraliz$masquer(GunTooltipPart part, CallbackInfoReturnable<Boolean> cir) {
      if (part == GunTooltipPart.EXTRA_DAMAGE_INFO || part == GunTooltipPart.UPGRADES_TIP || part == GunTooltipPart.PACK_INFO) {
         cir.setReturnValue(false);
      } else if (part == GunTooltipPart.DESCRIPTION && !fr.heraliz.tactique.onglets.Couteaux.estCouteau(this.gun)) {
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

   /** Ligne « Niveau » retirée : le type (ou, sans type, les dégâts) monte à sa place. */
   @Inject(method = "getText", at = @At("TAIL"), require = 0)
   private void heraliz$sansNiveau(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
      if (this.levelInfo == null || this.damage == null) return;
      if (this.gunType != null) {
         this.levelInfo = this.gunType;
         this.gunType = null;
      } else {
         this.levelInfo = this.damage;
         this.damage = net.minecraft.network.chat.Component.empty();
      }
      this.heraliz$sansNiveau = true;
   }

   @Inject(method = {"getHeight()I", "m_142103_()I"}, at = @At("RETURN"), cancellable = true, require = 0)
   private void heraliz$hauteur(CallbackInfoReturnable<Integer> cir) {
      if (this.heraliz$sansNiveau && !fr.heraliz.tactique.onglets.Couteaux.estCouteau(this.gun)) {
         cir.setReturnValue(cir.getReturnValue() - 10);
      }
   }
}
