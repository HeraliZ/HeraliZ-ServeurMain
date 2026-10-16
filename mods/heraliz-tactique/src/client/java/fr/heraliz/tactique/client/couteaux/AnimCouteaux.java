package fr.heraliz.tactique.client.couteaux;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.Animations;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateContext;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import com.tacz.guns.compat.playeranimator.animation.AnimationManager;
import com.tacz.guns.config.common.GunConfig;
import fr.heraliz.tactique.HeralizTactique;
import fr.heraliz.tactique.couteaux.PaquetCoupVu;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;

/**
 * Animations des coups de couteau (16/10) :
 * <ul>
 *    <li>coup rapide (clic gauche) : comme avant la 2.16.0, le coup « melee_push » du gunpack, joué par le signal
 *    « bayonet_push » de TaCZ avec son bruit (voir {@link CouteauxClient}) ; vu des autres : gestes de TaCZ
 *    « melee_upper » / « melee_2_upper » en alternance ;</li>
 *    <li>coup puissant (clic droit), 1re personne : « melee_stock », écrite pour chaque modèle par
 *    {@code outils/couteaux_coups.py} ({@code assets/heraliz_tactique/couteaux/<modèle>.animation.json}) comme le coup
 *    lourd des couteaux de LR : le couteau lui-même tourne avec le geste du bras (lame dressée en armant, pointe amenée
 *    sur le réticule à l'impact, 0,24 s). Donnée au contrôleur d'animation de TaCZ du couteau tenu, puis jouée
 *    directement sur la piste principale de sa machine à états par défaut avec une transition courte (le signal de TaCZ
 *    imposerait 0,2 s de fondu, qui mangerait la préparation). Bruits du joueur dans l'animation (ceux des couteaux de
 *    LR). En secours (piste introuvable) : signal « bayonet_stock » ; modèle sans fichier : coup du gunpack ;</li>
 *    <li>coup puissant vu des autres ({@link PaquetCoupVu}) : estoc du couteau de LR ({@code heralisz_skins:melee/knife}
 *    « stab_upper », voir {@link GesteCouteau}), sinon grand coup « melee_3_upper » de TaCZ ; bruit de lame qu'on arme
 *    puis coup lourd, joués chez ceux qui sont à portée d'oreille.</li>
 * </ul>
 */
final class AnimCouteaux {
   private static final Logger LOG = LogUtils.getLogger();
   static final String ESTOC = "melee_stock";
   /** Signal de TaCZ qui joue « melee_stock » (secours du coup puissant). */
   private static final String SIGNAL_COUP_LOURD = "bayonet_stock";
   /** Piste principale de la machine à états par défaut de TaCZ (default_state_machine.lua : ligne 0, piste MAIN_TRACK). */
   private static final int LIGNE_STATIQUE = 0;
   private static final int PISTE_PRINCIPALE = 9;
   /** Fondu d'entrée du coup puissant, en secondes (le signal de TaCZ en impose 0,2). */
   private static final float TRANSITION = 0.05F;
   /** Bruits de l'estoc entendus par les autres (tacz_sounds des couteaux de LR). */
   private static final ResourceLocation SON_ARMER = new ResourceLocation("heralisz_tactical", "melee/knife/melee_knife_charge_01");
   private static final ResourceLocation SON_ESTOC = new ResourceLocation("heralisz_tactical", "melee/knife/melee_attack_knife_plr_02");
   private static final int DISTANCE_SON_DEFAUT = 16;

   /** Modèle -> coup puissant lu (vide si le fichier manque ou est illisible). */
   private static final Map<String, Optional<ObjectAnimation>> CACHE = new HashMap<>();

   private AnimCouteaux() {
   }

   /**
    * Joue le coup puissant en 1re personne. Vrai s'il est parti (animation HeraliZ, qui porte ses bruits, ou son secours
    * « bayonet_stock ») ; faux si ce couteau n'a pas d'animation HeraliZ (l'appelant joue alors le coup du gunpack).
    */
   static boolean jouerLourd(@Nullable AnimationStateMachine<?> etats, @Nullable ResourceLocation arme) {
      if (etats == null || arme == null || !fournir(etats.getAnimationController(), arme)) {
         return false;
      }
      try {
         AnimationStateContext contexte = etats.getContext();
         int piste = contexte == null ? -1 : contexte.getTrack(LIGNE_STATIQUE, PISTE_PRINCIPALE);
         if (piste >= 0) {
            contexte.runAnimation(ESTOC, piste, false, ObjectAnimation.PlayType.PLAY_ONCE_STOP.ordinal(), TRANSITION);
            return true;
         }
      } catch (RuntimeException e) {
         LOG.debug("[couteaux] piste principale de TaCZ introuvable : signal de secours", e);
      }
      etats.trigger(SIGNAL_COUP_LOURD);
      return true;
   }

   /** Donne au contrôleur le coup puissant HeraliZ de ce couteau s'il ne l'a pas ; vrai s'il y est. */
   private static boolean fournir(@Nullable AnimationController controleur, ResourceLocation arme) {
      if (controleur == null) {
         return false;
      }
      String modele = modele(arme.getPath());
      if (modele == null) {
         return false;
      }
      ObjectAnimation anim = CACHE.computeIfAbsent(modele, AnimCouteaux::charger).orElse(null);
      if (anim == null) {
         return false;
      }
      controleur.providePrototypeIfAbsent(ESTOC, () -> anim);
      return controleur.containPrototype(ESTOC);
   }

   /** Modèle d'animation d'un couteau d'après son identifiant d'arme (skins compris : karambit__..., m9__...). */
   @Nullable
   static String modele(String chemin) {
      if (chemin.contains("karambit")) return "karambit";
      if (chemin.contains("butterfly")) return "butterfly_knife";
      if (chemin.startsWith("m9")) return "m9";
      if (chemin.contains("ct_knife")) return "ct_knife";
      if (chemin.contains("t_knife")) return "t_knife";
      return null;
   }

   private static Optional<ObjectAnimation> charger(String modele) {
      ResourceLocation rl = new ResourceLocation(HeralizTactique.MODID, "couteaux/" + modele + ".animation.json");
      Resource res = Minecraft.getInstance().getResourceManager().getResource(rl).orElse(null);
      if (res == null) {
         LOG.warn("[couteaux] coup puissant de {} absent : coup du gunpack", modele);
         return Optional.empty();
      }
      try (Reader r = res.openAsReader()) {
         JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
         BedrockAnimationFile file = ClientAssetsManager.GSON.fromJson(json, BedrockAnimationFile.class);
         return Animations.createAnimationFromBedrock(file).stream().filter(a -> ESTOC.equals(a.name)).findFirst();
      } catch (Exception e) {
         LOG.warn("[couteaux] coup puissant de {} illisible : coup du gunpack", modele, e);
         return Optional.empty();
      }
   }

   /** Alternance des deux gestes du coup rapide vu en 3e personne, par joueur (identifiant d'entité). */
   private static final Map<Integer, Boolean> ALTERNANCE_VUE = new HashMap<>();

   static void oublierJoueurs() {
      ALTERNANCE_VUE.clear();
   }

   /** Coup d'un autre joueur, vu en 3e personne (et entendu, pour le coup puissant). */
   static void vu(PaquetCoupVu p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || !(mc.level.getEntity(p.entite()) instanceof AbstractClientPlayer joueur)) {
         return;
      }
      boolean second = false;
      if (!p.lourd()) {
         second = !ALTERNANCE_VUE.getOrDefault(p.entite(), Boolean.TRUE);
         if (ALTERNANCE_VUE.size() > 256) {
            ALTERNANCE_VUE.clear();
         }
         ALTERNANCE_VUE.put(p.entite(), second);
      } else {
         sonsEstoc(mc, joueur);
      }
      boolean geste2 = second;
      TimelessAPI.getGunDisplay(joueur.getMainHandItem()).ifPresent(affichage -> {
         try {
            if (!PlayerAnimatorCompat.isInstalled() || !AnimationManager.hasPlayerAnimator3rd(affichage)) {
               return;
            }
            // coup puissant : estoc du couteau de LR ; sinon (ou geste de LR absent) gestes de TaCZ, comme avant la 2.16.0
            if (!p.lourd() || !GesteCouteau.estoc(joueur)) {
               String geste = p.lourd() ? "melee_3_upper" : geste2 ? "melee_2_upper" : "melee_upper";
               AnimationManager.playOnceAnimation(joueur, affichage, PlayerAnimatorCompat.ONCE_UPPER_ANIMATION, geste);
            }
         } catch (RuntimeException | LinkageError ex) {
            LOG.debug("[couteaux] geste en 3e personne impossible", ex);
         }
      });
   }

   /** Coup puissant d'un autre joueur : lame qu'on arme puis coup lourd, à la portée des bruits d'arme de TaCZ. */
   private static void sonsEstoc(Minecraft mc, AbstractClientPlayer joueur) {
      int distance = distanceSon();
      if (distance <= 0 || mc.player == null || mc.player.distanceToSqr(joueur) > (double) distance * distance) {
         return;
      }
      RandomSource alea = joueur.getRandom();
      try {
         SoundPlayManager.playClientSound(joueur, SON_ARMER, 0.8F, 0.95F + alea.nextFloat() * 0.1F, distance);
         SoundPlayManager.playClientSound(joueur, SON_ESTOC, 1.0F, 0.9F + alea.nextFloat() * 0.1F, distance);
      } catch (RuntimeException | LinkageError ex) {
         LOG.debug("[couteaux] bruit de l'estoc impossible", ex);
      }
   }

   private static int distanceSon() {
      try {
         return GunConfig.DEFAULT_GUN_OTHER_SOUND_DISTANCE.get();
      } catch (RuntimeException illisible) {
         return DISTANCE_SON_DEFAUT;
      }
   }
}
