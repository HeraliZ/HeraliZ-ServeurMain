package fr.heraliz.tactique.client.couteaux;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.sound.SoundPlayManager;
import fr.heraliz.tactique.allonge.CompatReanimation;
import fr.heraliz.tactique.couteaux.CoupsCouteau;
import fr.heraliz.tactique.couteaux.PaquetCoupCouteau;
import fr.heraliz.tactique.couteaux.ReseauCouteaux;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.lwjgl.glfw.GLFW;

/**
 * Coups de couteau TaCZ chez le joueur (voir {@link CoupsCouteau}) : les touches, la cadence vue par le joueur,
 * l'animation et le bruit du coup. Le coup lui-même (cibles, dégâts) est décidé par le serveur.
 * <ul>
 *    <li>touche Tirer de TaCZ (clic gauche) : le tir de TaCZ est remplacé par le coup rapide
 *    ({@code mixin.client.LocalPlayerShootCouteauMixin}) ; TaCZ redemande un tir à chaque tick tant que la touche est
 *    tenue : un coup dès que la cadence le permet ;</li>
 *    <li>touche Viser de TaCZ (clic droit) : coup puissant à l'appui ; la visée est refusée avec un couteau
 *    ({@code mixin.client.LocalPlayerAimCouteauMixin}). Si le viseur est sur un joueur à terre, pas de coup : le clic
 *    réanime ou fouille (Réanimation) ;</li>
 *    <li>touche « Mêlée » de TaCZ (V) : son coup est annulé (GunMeleeEvent du joueur) et remplacé par le coup rapide ;</li>
 *    <li>pas de coup pendant que le couteau sort (temps de sortie de son arme TaCZ, compté depuis qu'il est en main, puis
 *    tant que le serveur le voit sortir, comme pour les tirs de TaCZ), ni à terre ou en train de fouiller ou de réanimer,
 *    ni avec un écran ouvert ;</li>
 *    <li>coup accepté : paquet au serveur, puis l'animation. Coup rapide : comme avant la 2.16.0 et comme la touche
 *    « Mêlée » de TaCZ (LocalPlayerMelee), bruit « melee_push » du couteau et animation « melee_push » du gunpack (signal
 *    « bayonet_push » de sa machine à états). Coup puissant (16/10) : animation « melee_stock » du modèle où le couteau
 *    tourne avec le geste du bras (AnimCouteaux : geste écrit pour Knife CT et T, coup lourd de la Miséricorde, de la
 *    Lame de Desmoulins et du Credo de LR pour M9, papillon et karambit), ses bruits et son mouvement de caméra
 *    (SecousseCouteau) ; cadence du coup puissant propre au modèle (CoupsCouteau) ; en secours, le coup du gunpack.</li>
 * </ul>
 */
public final class CouteauxClient {
   /** Signal de la machine à états de TaCZ qui joue l'animation « melee_push » (coup sans accessoire de mêlée). */
   private static final String SIGNAL_COUP = "bayonet_push";

   /** Horloge du joueur, en ticks (avance à chaque tick du jeu). */
   private static long horloge;
   /** Tick à partir duquel un nouveau coup est permis (cadence du dernier coup, ou couteau qui sort). */
   private static long pretA;
   /** Même chose en temps réel (ms) : un coup ne repart jamais avant, même si les ticks du jeu s'emballent. */
   private static long pretMs;
   /** Durée totale de la recharge en cours, en ticks (anneau autour du réticule). */
   private static int rechargeTotale = 1;
   /** Couteau en main au dernier passage (case de la barre, identifiant TaCZ) : s'il change, le nouveau sort. */
   private static int caseVue = -1;
   @Nullable
   private static ResourceLocation armeVue;

   private CouteauxClient() {
   }

   public static void brancher() {
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, CouteauxClient::tick);
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, InputEvent.MouseButton.Post.class, e -> {
         if (e.getAction() == GLFW.GLFW_PRESS) {
            appui(InputConstants.Type.MOUSE.getOrCreate(e.getButton()));
         }
      });
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, InputEvent.Key.class, e -> {
         if (e.getAction() == GLFW.GLFW_PRESS) {
            appui(InputConstants.getKey(e.getKey(), e.getScanCode()));
         }
      });
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, GunMeleeEvent.class, CouteauxClient::coupDeTacz);
      ReseauCouteaux.COUP_VU_CLIENT.brancher(AnimCouteaux::vu);
      SecousseCouteau.brancher();
      MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.LoggingIn.class, e -> {
         pretA = 0L;
         pretMs = 0L;
         caseVue = -1;
         armeVue = null;
         AnimCouteaux.oublierJoueurs();
      });
   }

   /** Vrai si ce joueur tient un couteau TaCZ frappé par HeraliZ (lu par les mixins de tir et de visée). */
   public static boolean couteauEnMain(@Nullable Player joueur) {
      return CoupsCouteau.couteauEnMain(joueur);
   }

   /**
    * Coup demandé par une touche : vrai s'il part (paquet envoyé, animation et bruit joués), faux s'il est refusé ici
    * (pas de couteau, cadence, couteau qui sort, à terre, écran ouvert).
    */
   public static boolean attaquer(boolean lourd) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer joueur = mc.player;
      if (joueur == null || joueur.isSpectator() || !joueur.isAlive() || mc.screen != null || mc.getOverlay() != null
            || !couteauEnMain(joueur)) {
         return false;
      }
      suivre(joueur);
      // couteau qui sort : compté ici depuis la prise en main, puis tel que le serveur le voit (TaCZ y ajoute le temps de
      // rangement de l'objet d'avant ; c'est aussi ce qui retient les tirs des armes TaCZ)
      long maintenant = System.currentTimeMillis();
      if (horloge < pretA || maintenant < pretMs || IGunOperator.fromLivingEntity(joueur).getSynDrawCoolDown() > 0L
            || CompatReanimation.empeche(joueur)) {
         return false;
      }
      ItemStack main = joueur.getMainHandItem();
      IGun ig = IGun.getIGunOrNull(main);
      ResourceLocation id = ig == null ? null : ig.getGunId(main);
      int cadence = CoupsCouteau.cadence(lourd, CoupsCouteau.modele(id));
      pretA = horloge + cadence;
      pretMs = maintenant + cadence * 50L - 20L;
      rechargeTotale = Math.max(1, cadence);
      ReseauCouteaux.envoyerAuServeur(new PaquetCoupCouteau(lourd));
      TimelessAPI.getGunDisplay(main).ifPresent(affichage -> {
         AnimationStateMachine<?> etats = affichage.getAnimationStateMachine();
         // coup puissant : animation HeraliZ du modèle (bruits et mouvement de caméra compris, AnimCouteaux)
         if (lourd && AnimCouteaux.jouerLourd(etats, id)) {
            return;
         }
         // coup rapide (et secours du coup puissant) : comme la touche « Mêlée » de TaCZ, bruit et animation du gunpack
         SoundPlayManager.playMeleePushSound(joueur, affichage);
         if (etats != null) {
            etats.trigger(SIGNAL_COUP);
         }
      });
      return true;
   }

   /**
    * Recharge du coup en cours, pour l'anneau autour du réticule (comme les armes de mêlée) : part déjà rechargée, de 0 à
    * 1 ; -1 s'il n'y a rien à montrer (pas de couteau en main, ou coup prêt).
    */
   public static float recharge(float partiel) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || !couteauEnMain(mc.player)) {
         return -1.0F;
      }
      float reste = Math.max(pretA - horloge - partiel, (pretMs - System.currentTimeMillis()) / 50.0F);
      if (reste <= 0.0F) {
         return -1.0F;
      }
      return 1.0F - Math.min(1.0F, reste / rechargeTotale);
   }

   private static void tick(TickEvent.ClientTickEvent e) {
      if (e.phase != TickEvent.Phase.END) {
         return;
      }
      horloge++;
      AnimCouteaux.sonsEnAttente();
      LocalPlayer joueur = Minecraft.getInstance().player;
      if (joueur == null) {
         caseVue = -1;
         armeVue = null;
      } else {
         suivre(joueur);
      }
   }

   /** Couteau tout juste pris en main (autre case ou autre couteau) : pas de coup avant la fin de sa sortie. */
   private static void suivre(LocalPlayer joueur) {
      ItemStack main = joueur.getMainHandItem();
      IGun arme = couteauEnMain(joueur) ? IGun.getIGunOrNull(main) : null;
      if (arme == null) {
         caseVue = -1;
         armeVue = null;
         return;
      }
      ResourceLocation id = arme.getGunId(main);
      int c = joueur.getInventory().selected;
      if (c != caseVue || !id.equals(armeVue)) {
         pretA = Math.max(pretA, horloge + ticksSortie(id));
      }
      caseVue = c;
      armeVue = id;
   }

   /** Temps de sortie de cette arme TaCZ ({@code draw_time} de sa fiche), en ticks. */
   private static long ticksSortie(ResourceLocation id) {
      return TimelessAPI.getCommonGunIndex(id).map(i -> (long) Math.round(i.getGunData().getDrawTime() * 20.0F)).orElse(0L);
   }

   /** Appui d'une touche ou d'un bouton : la touche Viser de TaCZ donne le coup puissant. */
   private static void appui(InputConstants.Key touche) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.screen != null || mc.getOverlay() != null || mc.player == null || !AimKey.AIM_KEY.isActiveAndMatches(touche)
            || !couteauEnMain(mc.player)) {
         return;
      }
      // viseur sur un joueur à terre : le clic droit réanime ou fouille (Réanimation), pas de coup
      if (mc.hitResult instanceof EntityHitResult visee && CompatReanimation.aTerre(visee.getEntity())) {
         return;
      }
      attaquer(true);
   }

   /** Touche « Mêlée » de TaCZ avec un couteau, chez ce joueur : le coup de TaCZ est remplacé par le coup rapide. */
   private static void coupDeTacz(GunMeleeEvent e) {
      if (!e.getLogicalSide().isClient()) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || e.getShooter() != mc.player || !couteauEnMain(mc.player)) {
         return; // un autre joueur (coup vu en 3e personne, envoyé par le serveur) : rien à changer
      }
      e.setCanceled(true);
      attaquer(false);
   }
}
