package fr.heralisz.camo.mixin;

import com.tacz.guns.client.gui.GunRefitScreen;
import fr.heralisz.camo.client.RefitSkinTab;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ajoute l'onglet « Skin » au menu de modification TaCZ. Noms de méthodes : dev (Mojang) et jeu (SRG). */
@Mixin(value = GunRefitScreen.class, remap = false)
public abstract class GunRefitScreenMixin extends Screen {
   protected GunRefitScreenMixin(Component title) {
      super(title);
   }

   @Inject(method = {"init()V", "m_7856_()V"}, at = @At("TAIL"), require = 1)
   private void heralisz$addSkinTab(CallbackInfo ci) {
      RefitSkinTab.addWidgets((GunRefitScreen) (Object) this, w -> this.addRenderableWidget(w));
      fr.heralisz.camo.decor.client.RefitDecorTab.addWidgets((GunRefitScreen) (Object) this, w -> this.addRenderableWidget(w));
   }

   @Inject(method = {"onClose()V", "m_7379_()V"}, at = @At("HEAD"), require = 1)
   private void heralisz$closeSkinTab(CallbackInfo ci) {
      RefitSkinTab.open = false;
      RefitSkinTab.preview = null;
   }
}
