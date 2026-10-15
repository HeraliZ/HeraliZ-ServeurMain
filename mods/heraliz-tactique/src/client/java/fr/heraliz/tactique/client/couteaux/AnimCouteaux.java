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
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;

/**
 * Animations des deux coups de couteau, bien différentes comme celles des couteaux de LR Tactical (15/10) :
 * <ul>
 *    <li>1re personne : « heraliz_tranche_1 » / « heraliz_tranche_2 » (coup rapide : tranche de gauche à droite, puis de
 *    droite à gauche) et « melee_stock » (coup puissant : estoc, bras armé puis poussé vers l'avant), écrites par
 *    {@code outils/couteaux_coups.py} ({@code assets/heraliz_tactique/couteaux/<modèle>.animation.json}). Données au
 *    contrôleur d'animation de TaCZ du couteau tenu, puis jouées directement sur la piste principale de sa machine à
 *    états par défaut, avec une transition courte (le signal de TaCZ imposerait 0,2 s de fondu : la tranche partirait
 *    en retard et ressemblerait à l'estoc). Bruits du joueur dans les animations (ceux des couteaux de LR). En secours
 *    (modèle sans fichier, machine à états inattendue) : signaux de TaCZ (« melee_push » du gunpack, « melee_stock ») ;</li>
 *    <li>3e personne (autres joueurs, {@link PaquetCoupVu}) : gestes du couteau de LR ({@code heralisz_skins:melee/knife} :
 *    « slash_upper » / « slash2_upper » en alternance, « stab_upper » pour l'estoc, voir {@link GesteCouteau}), sinon
 *    ceux de TaCZ ; estoc : bruit de lame qu'on arme puis coup lourd, joués chez ceux qui sont à portée d'oreille.</li>
 * </ul>
 */
final class AnimCouteaux {
   private static final Logger LOG = LogUtils.getLogger();
   static final String TRANCHE_1 = "heraliz_tranche_1";
   static final String TRANCHE_2 = "heraliz_tranche_2";
   static final String ESTOC = "melee_stock";
   private static final String[] CLIPS = {TRANCHE_1, TRANCHE_2, ESTOC};
   /** Signaux de TaCZ (secours) : « melee_push » du gunpack, « melee_stock » (l'estoc ci-dessus). */
   private static final String SIGNAL_COUP = "bayonet_push";
   private static final String SIGNAL_COUP_LOURD = "bayonet_stock";
   /** Piste principale de la machine à états par défaut de TaCZ (default_state_machine.lua : ligne 0, piste MAIN_TRACK). */
   private static final int LIGNE_STATIQUE = 0;
   private static final int PISTE_PRINCIPALE = 9;
   /** Fondu d'entrée des coups, en secondes (le signal de TaCZ en impose 0,2). */
   private static final float TRANSITION = 0.04F;
   /** Bruits de l'estoc entendus par les autres (tacz_sounds des couteaux de LR). */
   private static final ResourceLocation SON_ARMER = new ResourceLocation("heralisz_tactical", "melee/knife/melee_knife_charge_01");
   private static final ResourceLocation SON_ESTOC = new ResourceLocation("heralisz_tactical", "melee/knife/melee_attack_knife_plr_02");
   private static final int DISTANCE_SON_DEFAUT = 16;

   /** Modèle -> animations lues (absentes si le fichier manque ou est illisible). */
   private static final Map<String, Map<String, ObjectAnimation>> CACHE = new HashMap<>();
   /** Alternance des deux tranches chez ce joueur : vrai si la dernière jouée est la première. */
   private static boolean premiere;

   private AnimCouteaux() {
   }

   /**
    * Joue le coup en 1re personne. Vrai si l'animation HeraliZ est partie (elle porte ses bruits) ; faux si c'est le
    * secours de TaCZ (l'appelant joue alors le bruit du coup de TaCZ).
    */
   static boolean jouer(@Nullable AnimationStateMachine<?> etats, @Nullable ResourceLocation arme, boolean lourd) {
      if (etats == null) {
         return false;
      }
      String clip = lourd ? ESTOC : (premiere = !premiere) ? TRANCHE_1 : TRANCHE_2;
      boolean fourni = arme != null && fournir(etats.getAnimationController(), arme);
      if (fourni) {
         try {
            AnimationStateContext contexte = etats.getContext();
            int piste = contexte == null ? -1 : contexte.getTrack(LIGNE_STATIQUE, PISTE_PRINCIPALE);
            if (piste >= 0) {
               contexte.runAnimation(clip, piste, false, ObjectAnimation.PlayType.PLAY_ONCE_STOP.ordinal(), TRANSITION);
               return true;
            }
         } catch (RuntimeException e) {
            LOG.debug("[couteaux] piste principale de TaCZ introuvable : signal de secours", e);
         }
      }
      // secours : l'estoc passe par « bayonet_stock » s'il a été fourni, sinon le coup du gunpack
      etats.trigger(lourd && fourni ? SIGNAL_COUP_LOURD : SIGNAL_COUP);
      return false;
   }

   /** Vrai si la dernière tranche jouée est la première (de gauche à droite), faux pour le retour (de droite à gauche). */
   static boolean derniereTranchePremiere() {
      return premiere;
   }

   /** Donne au contrôleur les animations HeraliZ de ce couteau s'il ne les a pas ; vrai si elles y sont. */
   private static boolean fournir(@Nullable AnimationController controleur, ResourceLocation arme) {
      if (controleur == null) {
         return false;
      }
      String modele = modele(arme.getPath());
      if (modele == null) {
         return false;
      }
      Map<String, ObjectAnimation> anims = CACHE.computeIfAbsent(modele, AnimCouteaux::charger);
      for (String nom : CLIPS) {
         ObjectAnimation a = anims.get(nom);
         if (a == null) {
            return false;
         }
         controleur.providePrototypeIfAbsent(nom, () -> a);
         if (!controleur.containPrototype(nom)) {
            return false;
         }
      }
      return true;
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

   private static Map<String, ObjectAnimation> charger(String modele) {
      Map<String, ObjectAnimation> anims = new HashMap<>();
      ResourceLocation rl = new ResourceLocation(HeralizTactique.MODID, "couteaux/" + modele + ".animation.json");
      Resource res = Minecraft.getInstance().getResourceManager().getResource(rl).orElse(null);
      if (res == null) {
         LOG.warn("[couteaux] animations de {} absentes : coups de TaCZ", modele);
         return anims;
      }
      try (Reader r = res.openAsReader()) {
         JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
         BedrockAnimationFile file = ClientAssetsManager.GSON.fromJson(json, BedrockAnimationFile.class);
         for (ObjectAnimation a : Animations.createAnimationFromBedrock(file)) {
            anims.put(a.name, a);
         }
      } catch (Exception e) {
         LOG.warn("[couteaux] animations de {} illisibles : coups de TaCZ", modele, e);
         anims.clear();
      }
      return anims;
   }

   /** Alternance des tranches vues en 3e personne, par joueur (identifiant d'entité). */
   private static final Map<Integer, Boolean> ALTERNANCE_VUE = new HashMap<>();

   static void oublierJoueurs() {
      ALTERNANCE_VUE.clear();
   }

   /** Coup d'un autre joueur, vu en 3e personne (et entendu, pour l'estoc). */
   static void vu(PaquetCoupVu p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || !(mc.level.getEntity(p.entite()) instanceof AbstractClientPlayer joueur)) {
         return;
      }
      boolean second = !ALTERNANCE_VUE.getOrDefault(p.entite(), Boolean.TRUE);
      if (ALTERNANCE_VUE.size() > 256) {
         ALTERNANCE_VUE.clear();
      }
      ALTERNANCE_VUE.put(p.entite(), second);
      if (p.lourd()) {
         sonsEstoc(mc, joueur);
      }
      TimelessAPI.getGunDisplay(joueur.getMainHandItem()).ifPresent(affichage -> {
         try {
            if (!PlayerAnimatorCompat.isInstalled() || !AnimationManager.hasPlayerAnimator3rd(affichage)) {
               return;
            }
            if (!GesteCouteau.jouer(joueur, p.lourd(), second)) {
               // gestes de LR absents : ceux de TaCZ (grand coup de haut en bas pour l'estoc, deux gestes sinon)
               String geste = p.lourd() ? "melee_3_upper" : second ? "melee_2_upper" : "melee_upper";
               AnimationManager.playOnceAnimation(joueur, affichage, PlayerAnimatorCompat.ONCE_UPPER_ANIMATION, geste);
            }
         } catch (RuntimeException | LinkageError ex) {
            LOG.debug("[couteaux] geste en 3e personne impossible", ex);
         }
      });
   }

   /** Estoc d'un autre joueur : lame qu'on arme puis coup lourd, à la portée des bruits d'arme de TaCZ. */
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
