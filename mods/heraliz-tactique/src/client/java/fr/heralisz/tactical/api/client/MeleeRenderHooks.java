package fr.heralisz.tactical.api.client;

import com.tacz.guns.client.model.bedrock.BedrockModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Point d'accroche du rendu des armes de mêlée (HeralisZ) : un autre mod peut remplacer le type de rendu d'une arme,
 * par exemple heralisz_camo pour dessiner ses skins animés (shader) sur les lames.
 */
public final class MeleeRenderHooks {
   @FunctionalInterface
   public interface Hook {
      /** @return le type de rendu à utiliser (original si l'arme n'est pas concernée) */
      RenderType apply(ItemStack stack, BedrockModel model, ResourceLocation texture, RenderType original);
   }

   private static volatile Hook hook;

   private MeleeRenderHooks() {
   }

   public static void set(Hook h) {
      hook = h;
   }

   public static RenderType apply(ItemStack stack, BedrockModel model, ResourceLocation texture, RenderType original) {
      Hook h = hook;
      if (h == null || model == null || texture == null) {
         return original;
      }
      try {
         return h.apply(stack, model, texture, original);
      } catch (RuntimeException e) {
         return original;
      }
   }
}
