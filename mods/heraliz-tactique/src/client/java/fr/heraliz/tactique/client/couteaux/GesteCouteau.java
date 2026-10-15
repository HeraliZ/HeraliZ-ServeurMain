package fr.heraliz.tactique.client.couteaux;

import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import fr.heralisz.tactical.util.IPAAssetManager;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Coup de couteau d'un autre joueur, vu en 3e personne, avec les gestes des couteaux de LR Tactical (ceux de la Dague
 * tactique et du karambit de LR, {@code heralisz_skins:melee/knife} du gunpack heralisz_skins) : tranche « slash_upper »
 * puis « slash2_upper » en alternance pour le coup rapide, estoc « stab_upper » (bras ramené puis poussé, buste qui
 * tourne) pour le coup puissant. Joués sur la couche « une fois » du haut du corps de TaCZ, en remplaçant le geste en
 * cours (coups enchaînés). Classe chargée seulement si Player Animator est là (appelée après
 * {@code PlayerAnimatorCompat.isInstalled()}).
 */
final class GesteCouteau {
   private static final ResourceLocation GESTES = new ResourceLocation("heralisz_skins", "melee/knife");
   /** Fondu d'entrée du geste, en ticks. */
   private static final int FONDU = 2;

   private GesteCouteau() {
   }

   /** Vrai si le geste de LR est parti ; faux s'il manque (l'appelant joue alors celui de TaCZ). */
   @SuppressWarnings("unchecked")
   static boolean jouer(AbstractClientPlayer joueur, boolean lourd, boolean second) {
      String nom = lourd ? "stab_upper" : second ? "slash2_upper" : "slash_upper";
      KeyframeAnimation geste = IPAAssetManager.getAnimations(GESTES, nom).orElse(null);
      if (geste == null) {
         return false;
      }
      IAnimation couche = PlayerAnimationAccess.getPlayerAssociatedData(joueur).get(PlayerAnimatorCompat.ONCE_UPPER_ANIMATION);
      if (!(couche instanceof ModifierLayer<?> modifiable)) {
         return false;
      }
      ((ModifierLayer<IAnimation>) modifiable).replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FONDU, Ease.INOUTSINE),
         new KeyframeAnimationPlayer(geste));
      return true;
   }
}
