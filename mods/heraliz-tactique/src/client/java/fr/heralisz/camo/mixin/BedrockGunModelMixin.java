package fr.heralisz.camo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.client.CamoClient;
import fr.heralisz.camo.client.CamoRenderType;
import fr.heralisz.camo.client.CamoShaders;
import fr.heralisz.camo.client.PosMap;
import fr.heralisz.camo.client.PosMapCache;
import fr.heralisz.camo.knife.KnifeSkins;
import fr.heralisz.camo.knife.KnifeType;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.tuple.Pair;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remplace le rendu de l'arme par le rendu camo quand l'arme porte un camo (modèle principal et modèle LOD). */
@Mixin(value = BedrockGunModel.class, remap = false)
public abstract class BedrockGunModelMixin {
   @ModifyVariable(
      method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V",
      at = @At("HEAD"), argsOnly = true)
   private RenderType heralisz$camo(RenderType original, PoseStack poseStack, ItemStack gun, ItemDisplayContext ctx, RenderType renderType, int light, int overlay) {
      int shaderId, parts;
      KnifeType knife = null;
      Camo camo = CamoClient.camoFor(gun);
      fr.heralisz.camo.decor.client.DecorRender.beginGun(this, gun, ctx, light);
      KnifeSkins.Skin skin = camo == null ? KnifeSkins.of(gun) : null;
      if (camo == null && skin == null) {
         // arme sans skin (la plupart) : rien d'autre à lire pour elle, à chaque image et pour chaque joueur visible
         fr.heralisz.camo.client.CamoFx.pendingLocal = false;
         fr.heralisz.camo.client.CamoFx.pendingHeat = 0;
         fr.heralisz.camo.client.CamoFx.pendingAges = null;
         fr.heralisz.camo.client.CamoFx.pendingSeq = 0;
         fr.heralisz.camo.client.CamoFx.pendingKills = 0;
         fr.heralisz.camo.client.CamoFx.pendingReveal = -1;
         return original;
      }
      if (camo != null) {
         shaderId = camo.shaderId;
         parts = camo.parts;
      } else {
         shaderId = skin.finish().shaderId;
         // bits 0-3 : lame, manche caoutchouc, métal et manche métal sombre ; bits 4+ : type de couteau (réglages propres
         // à chaque modèle dans le shader : 1 Butterfly, 2 Karambit, 3 M9)
         parts = 15 | ((skin.knife().ordinal() + 1) << 4);
         knife = skin.knife();
      }
      // apparition (sortie de l'arme, 1re personne) : lue par le rendu camo (front de matérialisation) et par les effets
      net.minecraft.client.Minecraft mcl = net.minecraft.client.Minecraft.getInstance();
      fr.heralisz.camo.client.CamoFx.pendingLocal = mcl.player != null && gun == mcl.player.getMainHandItem();
      fr.heralisz.camo.client.CamoFx.pendingHeat = fr.heralisz.camo.client.ShotTracker.heat(gun);
      // derniers tirs : seulement pour les skins à effet 3D (seuls lecteurs) ; un tableau neuf à chaque rendu sinon
      fr.heralisz.camo.client.CamoFx.pendingAges = fr.heralisz.camo.fx.FxEffects.has(shaderId) ? fr.heralisz.camo.client.ShotTracker.ages(gun) : null;
      fr.heralisz.camo.client.CamoFx.pendingSeq = fr.heralisz.camo.client.ShotTracker.seq(gun);
      fr.heralisz.camo.client.CamoFx.pendingKills = fr.heralisz.camo.CamoData.kills(gun);
      fr.heralisz.camo.client.CamoFx.pendingReveal = camo != null && ctx.firstPerson() && fr.heralisz.camo.client.SkinReveal.activeFor(gun)
         ? fr.heralisz.camo.client.SkinReveal.front(gun) : -1;
      if (CamoShaders.get(shaderId) == null) {
         return original;
      }
      GunDisplayInstance display = TimelessAPI.getGunDisplay(gun).orElse(null);
      if (display == null) {
         return original;
      }
      // modèle principal, ou modèle simplifié (LOD) que TaCZ utilise de loin : 3e personne, arme au sol, cadre
      BedrockGunModel self = (BedrockGunModel) (Object) this;
      ResourceLocation texture = null;
      if (display.getGunModel() == self) {
         texture = display.getModelTexture();
      } else {
         Pair<BedrockGunModel, ResourceLocation> lod = display.getLodModel();
         if (lod != null && lod.getLeft() == self) {
            texture = lod.getRight();
         }
      }
      if (texture == null) {
         return original;
      }
      PosMap map = PosMapCache.get(self, knife, texture);
      return map == null ? original : CamoRenderType.get(texture, map.glId, shaderId, parts);
   }

   /**
    * Personnalisation (stickers, StatTrack) : dessinée quand TaCZ a entièrement fini l'arme.
    */
   @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;II)V",
      at = @At("RETURN"))
   private void heralisz$decorDraw(CallbackInfo ci) {
      fr.heralisz.camo.decor.client.DecorRender.endModel(this);
   }
}
