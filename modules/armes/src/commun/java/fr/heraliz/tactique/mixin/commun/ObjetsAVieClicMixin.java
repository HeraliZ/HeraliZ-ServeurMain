package fr.heraliz.tactique.mixin.commun;

import fr.heraliz.tactique.avie.ObjetsAVie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Objets à vie rangés hors de l'inventaire (03/11, règle du propriétaire, voir {@link ObjetsAVie}) : tout clic d'un
 * joueur dans un écran (clic, glisser, Maj + clic, touches 1 à 9, double clic, Q et Ctrl + Q) passe par
 * {@code AbstractContainerMenu.clicked}, côté serveur comme côté joueur, y compris quand un écran de mod le réécrit et
 * appelle celui de Minecraft. Avant et après chaque clic, la partie serveur de Tactique regarde si un objet à vie vient
 * de quitter l'inventaire du joueur pour une case d'un contenant : il en est ressorti et le joueur reçoit la question
 * (ObjetsAVieServeur). Chez le joueur (et sans la partie serveur), rien n'est fait : seulement un test de classe.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ObjetsAVieClicMixin {
   @Inject(method = "clicked", at = @At("HEAD"))
   private void heraliz$avantClicAVie(int caseClic, int bouton, ClickType type, Player joueur, CallbackInfo ci) {
      ObjetsAVie.avantClic((AbstractContainerMenu) (Object) this, joueur);
   }

   @Inject(method = "clicked", at = @At("RETURN"))
   private void heraliz$apresClicAVie(int caseClic, int bouton, ClickType type, Player joueur, CallbackInfo ci) {
      ObjetsAVie.apresClic((AbstractContainerMenu) (Object) this, joueur);
   }
}
