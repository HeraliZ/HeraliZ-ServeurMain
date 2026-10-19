package fr.heraliz.tactique.client.allonge;

import com.tacz.guns.api.item.IGun;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import fr.heralisz.tactical.util.IPAAssetManager;
import fr.heraliz.tactique.HeralizTactique;
import fr.heraliz.tactique.allonge.CompatReanimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Allongé SANS arme TaCZ en main (19/10), vu en 3e personne par soi et par les autres : la même pose à plat que TaCZ avec
 * une arme. Avant, Minecraft dessinait sa nage (brasse des bras, battement des jambes) : « on a l'impression que je
 * nage ». Ici, les gestes « allongé » de TaCZ (« lie » immobile, « lie_move » en rampant, ceux du fusil
 * {@code tacz:rifle_default}, sur lesquels les bras rampent) sont joués sur une couche Player Animator à nous, que TaCZ
 * n'arrête pas quand le joueur n'a pas d'arme (il arrête seulement ses propres couches). Mêmes règles que l'allongé de
 * TaCZ ({@code TaczAllongeAnimationMixin}) : corps couché à plus de moitié, jamais dans l'eau ; ni à terre (Réanimation a
 * sa pose), ni en véhicule, en spectateur, endormi ou en vol plané. Décidé chez chaque joueur pour chaque joueur visible
 * (la pose vient du serveur) : rien côté serveur. Classe chargée seulement si Player Animator est là.
 */
public final class RamperSansArme {
   private static final ResourceLocation COUCHE = new ResourceLocation(HeralizTactique.MODID, "ramper_sans_arme");
   private static final ResourceLocation GESTES = new ResourceLocation("tacz", "rifle_default.player_animation");
   /** Au-dessus des couches de TaCZ et de la mêlée de LR (40 à 42) : de toute façon arrêtées sans arme et allongé. */
   private static final int PRIORITE = 45;
   /** Fondu entre deux gestes et à l'entrée / la sortie, en ticks. */
   private static final int FONDU = 4;
   /** Vitesse des jambes (walkAnimation) pour passer à « lie_move », et pour revenir à « lie » (écart : pas de va-et-vient). */
   private static final float BOUGE = 0.08F, ARRETE = 0.03F;

   private RamperSansArme() {
   }

   public static void brancher(IEventBus busMod) {
      busMod.addListener(EventPriority.NORMAL, false, FMLClientSetupEvent.class, e -> e.enqueueWork(RamperSansArme::enregistrer));
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, RamperSansArme::tick);
   }

   private static void enregistrer() {
      PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(COUCHE, PRIORITE, joueur -> new ModifierLayer<>());
   }

   private static void tick(TickEvent.ClientTickEvent e) {
      if (e.phase != TickEvent.Phase.END) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.isPaused()) return;
      for (AbstractClientPlayer joueur : mc.level.players()) {
         maj(joueur);
      }
   }

   @SuppressWarnings("unchecked")
   private static void maj(AbstractClientPlayer joueur) {
      IAnimation brute = PlayerAnimationAccess.getPlayerAssociatedData(joueur).get(COUCHE);
      if (!(brute instanceof ModifierLayer<?> modifiable)) return;
      ModifierLayer<IAnimation> couche = (ModifierLayer<IAnimation>) modifiable;
      KeyframeAnimation enCours = couche.getAnimation() instanceof KeyframeAnimationPlayer lecteur ? lecteur.getData() : null;
      KeyframeAnimation voulu = null;
      if (rampe(joueur)) {
         KeyframeAnimation immobile = IPAAssetManager.getAnimations(GESTES, "lie").orElse(null);
         KeyframeAnimation bouge = IPAAssetManager.getAnimations(GESTES, "lie_move").orElse(immobile);
         float vitesse = joueur.walkAnimation.speed();
         boolean enMouvement = enCours != null && enCours == bouge ? vitesse > ARRETE : vitesse > BOUGE;
         voulu = enMouvement ? bouge : immobile;
      }
      if (voulu == enCours) return;
      KeyframeAnimationPlayer lecteur = null;
      if (voulu != null) {
         lecteur = new KeyframeAnimationPlayer(voulu);
         lecteur.setFirstPersonMode(FirstPersonMode.DISABLED);
      }
      couche.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FONDU, Ease.INOUTSINE), lecteur);
   }

   /** À plat comme l'allongé de TaCZ, mais sans arme TaCZ en main (avec une arme, TaCZ joue lui-même ces gestes). */
   private static boolean rampe(AbstractClientPlayer joueur) {
      if (joueur.isSwimming() || joueur.isInWater() || joueur.isPassenger() || joueur.isSpectator() || joueur.isSleeping()
            || joueur.isFallFlying()) {
         return false;
      }
      if (joueur.getSwimAmount(1.0F) <= 0.5F) return false;
      if (joueur.getMainHandItem().getItem() instanceof IGun) return false;
      return !CompatReanimation.aTerre(joueur);
   }
}
