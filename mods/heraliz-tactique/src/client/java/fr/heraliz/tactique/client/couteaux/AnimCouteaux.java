package fr.heraliz.tactique.client.couteaux;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.Animations;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateContext;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import com.tacz.guns.compat.playeranimator.animation.AnimationManager;
import com.tacz.guns.config.common.GunConfig;
import fr.heraliz.tactique.HeralizTactique;
import fr.heraliz.tactique.couteaux.CoupsCouteau;
import fr.heraliz.tactique.couteaux.PaquetCoupVu;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Animations des coups de couteau (16/10) :
 * <ul>
 *    <li>coup rapide (clic gauche) : comme avant la 2.16.0, le coup « melee_push » du gunpack, joué par le signal
 *    « bayonet_push » de TaCZ avec son bruit (voir {@link CouteauxClient}) ; vu des autres : gestes de TaCZ
 *    « melee_upper » / « melee_2_upper » en alternance ;</li>
 *    <li>coup puissant (clic droit), 1re personne : « melee_stock », écrite pour chaque modèle par
 *    {@code outils/couteaux_coups.py} ({@code assets/heraliz_tactique/couteaux/<modèle>.animation.json}) : le couteau
 *    lui-même tourne avec le geste du bras. Knife CT et T : geste écrit comme le coup lourd de la Dague tactique de LR
 *    (lame dressée en armant, pointe sur le réticule à 0,24 s). M9, papillon et karambit (Tactique 2.18.0) : coup lourd
 *    de la Miséricorde, de la Lame de Desmoulins et du Credo de LR, repris de leurs fichiers d'animation (bras, lame qui
 *    tourne, rythme, bruits ; leur mouvement de caméra est rejoué par {@link SecousseCouteau}). Donnée au contrôleur
 *    d'animation de TaCZ du couteau tenu, puis jouée directement sur la piste principale de sa machine à états par
 *    défaut, avec le fondu d'entrée écrit dans le fichier (0,05 s pour les gestes écrits, 0,2 s comme les armes de LR
 *    pour les reprises ; le signal de TaCZ imposerait toujours 0,2 s). Bruits du joueur dans l'animation. En secours
 *    (piste introuvable) : signal « bayonet_stock » ; modèle sans fichier : coup du gunpack ;</li>
 *    <li>coup puissant vu des autres ({@link PaquetCoupVu}) : estoc des couteaux de LR ({@code heralisz_skins:melee/knife}
 *    « stab_upper », voir {@link GesteCouteau} ; c'est aussi le geste du clic droit de la Miséricorde, de la Lame de
 *    Desmoulins et du Credo), sinon grand coup « melee_3_upper » de TaCZ ; bruits du coup, joués chez ceux qui sont à
 *    portée d'oreille : ceux de l'arme de LR reprise, aux mêmes instants que dans son animation (entrée
 *    « heraliz.sons_vus » du fichier), sinon lame qu'on arme puis coup lourd des couteaux de LR.</li>
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
   /** Fondu d'entrée du coup puissant si le fichier n'en donne pas, en secondes. */
   private static final float TRANSITION = 0.05F;
   /** Bruits de l'estoc entendus par les autres (tacz_sounds des couteaux de LR), pour les gestes écrits. */
   private static final ResourceLocation SON_ARMER = new ResourceLocation("heralisz_tactical", "melee/knife/melee_knife_charge_01");
   private static final ResourceLocation SON_ESTOC = new ResourceLocation("heralisz_tactical", "melee/knife/melee_attack_knife_plr_02");
   private static final int DISTANCE_SON_DEFAUT = 16;
   /** Bruits en attente au plus (coups vus des autres) : au-delà, les plus anciens sont oubliés. */
   private static final int ATTENTE_MAX = 64;

   /** Coup puissant d'un modèle : animation et données HeraliZ du fichier. */
   private record Coup(ObjectAnimation animation, float transition, @Nullable float[][] camera, List<SonVu> sons) {
   }

   /** Bruit du coup puissant entendu par les autres, à {@code temps} secondes du début. */
   private record SonVu(float temps, ResourceLocation son) {
   }

   /** Bruit d'un coup vu, à jouer à l'heure {@code quandNs} (System.nanoTime). */
   private record SonEnAttente(int entite, ResourceLocation son, long quandNs) {
   }

   /** Modèle -> coup puissant lu (vide si le fichier manque ou est illisible). */
   private static final Map<String, Optional<Coup>> CACHE = new HashMap<>();
   private static final List<SonEnAttente> ATTENTE = new ArrayList<>();

   private AnimCouteaux() {
   }

   /**
    * Joue le coup puissant en 1re personne, avec son mouvement de caméra. Vrai s'il est parti (animation HeraliZ, qui
    * porte ses bruits, ou son secours « bayonet_stock ») ; faux si ce couteau n'a pas d'animation HeraliZ (l'appelant joue
    * alors le coup du gunpack).
    */
   static boolean jouerLourd(@Nullable AnimationStateMachine<?> etats, @Nullable ResourceLocation arme) {
      Coup coup = coup(arme);
      if (etats == null || coup == null || !fournir(etats.getAnimationController(), coup)) {
         return false;
      }
      SecousseCouteau.lancer(coup.camera());
      try {
         AnimationStateContext contexte = etats.getContext();
         int piste = contexte == null ? -1 : contexte.getTrack(LIGNE_STATIQUE, PISTE_PRINCIPALE);
         if (piste >= 0) {
            contexte.runAnimation(ESTOC, piste, false, ObjectAnimation.PlayType.PLAY_ONCE_STOP.ordinal(), coup.transition());
            return true;
         }
      } catch (RuntimeException e) {
         LOG.debug("[couteaux] piste principale de TaCZ introuvable : signal de secours", e);
      }
      etats.trigger(SIGNAL_COUP_LOURD);
      return true;
   }

   /** Donne au contrôleur le coup puissant HeraliZ de ce couteau s'il ne l'a pas ; vrai s'il y est. */
   private static boolean fournir(@Nullable AnimationController controleur, Coup coup) {
      if (controleur == null) {
         return false;
      }
      controleur.providePrototypeIfAbsent(ESTOC, coup::animation);
      return controleur.containPrototype(ESTOC);
   }

   /** Coup puissant HeraliZ d'un couteau d'après son identifiant d'arme (skins compris), null s'il n'en a pas. */
   @Nullable
   private static Coup coup(@Nullable ResourceLocation arme) {
      String modele = CoupsCouteau.modele(arme);
      return modele == null ? null : CACHE.computeIfAbsent(modele, AnimCouteaux::charger).orElse(null);
   }

   private static Optional<Coup> charger(String modele) {
      ResourceLocation rl = new ResourceLocation(HeralizTactique.MODID, "couteaux/" + modele + ".animation.json");
      Resource res = Minecraft.getInstance().getResourceManager().getResource(rl).orElse(null);
      if (res == null) {
         LOG.warn("[couteaux] coup puissant de {} absent : coup du gunpack", modele);
         return Optional.empty();
      }
      try (Reader r = res.openAsReader()) {
         JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
         // données HeraliZ (outils/couteaux_coups.py), retirées avant la lecture par TaCZ
         JsonElement meta = json.remove("heraliz");
         BedrockAnimationFile file = ClientAssetsManager.GSON.fromJson(json, BedrockAnimationFile.class);
         ObjectAnimation anim = Animations.createAnimationFromBedrock(file).stream().filter(a -> ESTOC.equals(a.name)).findFirst()
            .orElse(null);
         if (anim == null) {
            LOG.warn("[couteaux] coup puissant de {} sans « {} » : coup du gunpack", modele, ESTOC);
            return Optional.empty();
         }
         float transition = TRANSITION;
         float[][] camera = null;
         List<SonVu> sons = new ArrayList<>();
         if (meta != null && meta.isJsonObject()) {
            JsonObject m = meta.getAsJsonObject();
            if (m.has("transition") && m.get("transition").isJsonPrimitive()) {
               transition = Math.max(0.0F, m.get("transition").getAsFloat());
            }
            camera = courbe(m.get("camera"));
            if (m.has("sons_vus") && m.get("sons_vus").isJsonArray()) {
               for (JsonElement e : m.getAsJsonArray("sons_vus")) {
                  JsonArray s = e.getAsJsonArray();
                  ResourceLocation son = ResourceLocation.tryParse(s.get(1).getAsString());
                  if (son != null) {
                     sons.add(new SonVu(s.get(0).getAsFloat(), son));
                  }
               }
            }
         }
         return Optional.of(new Coup(anim, transition, camera, List.copyOf(sons)));
      } catch (Exception e) {
         LOG.warn("[couteaux] coup puissant de {} illisible : coup du gunpack", modele, e);
         return Optional.empty();
      }
   }

   /** Courbe de caméra du fichier ([temps, tangage, lacet, roulis] en degrés), null si absente ou vide. */
   @Nullable
   private static float[][] courbe(@Nullable JsonElement e) {
      if (e == null || !e.isJsonArray() || e.getAsJsonArray().isEmpty()) {
         return null;
      }
      JsonArray a = e.getAsJsonArray();
      float[][] c = new float[a.size()][];
      for (int i = 0; i < a.size(); i++) {
         JsonArray cle = a.get(i).getAsJsonArray();
         c[i] = new float[]{cle.get(0).getAsFloat(), cle.get(1).getAsFloat(), cle.get(2).getAsFloat(), cle.get(3).getAsFloat(), 0.0F};
      }
      return c;
   }

   /** Alternance des deux gestes du coup rapide vu en 3e personne, par joueur (identifiant d'entité). */
   private static final Map<Integer, Boolean> ALTERNANCE_VUE = new HashMap<>();

   static void oublierJoueurs() {
      ALTERNANCE_VUE.clear();
      ATTENTE.clear();
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

   /**
    * Coup puissant d'un autre joueur, à la portée des bruits d'arme de TaCZ : bruits de l'arme de LR reprise par son
    * couteau, aux instants de son animation ; sinon lame qu'on arme puis coup lourd.
    */
   private static void sonsEstoc(Minecraft mc, AbstractClientPlayer joueur) {
      int distance = distanceSon();
      if (distance <= 0 || mc.player == null || mc.player.distanceToSqr(joueur) > (double) distance * distance) {
         return;
      }
      ItemStack main = joueur.getMainHandItem();
      IGun arme = IGun.getIGunOrNull(main);
      Coup coup = arme == null ? null : coup(arme.getGunId(main));
      if (coup != null && !coup.sons().isEmpty()) {
         long maintenant = System.nanoTime();
         for (SonVu s : coup.sons()) {
            if (s.temps() <= 0.0F) {
               jouer(joueur, s.son(), 1.0F, 1.0F, distance);
            } else {
               if (ATTENTE.size() >= ATTENTE_MAX) {
                  ATTENTE.remove(0);
               }
               ATTENTE.add(new SonEnAttente(joueur.getId(), s.son(), maintenant + (long) (s.temps() * 1.0E9F)));
            }
         }
         return;
      }
      RandomSource alea = joueur.getRandom();
      jouer(joueur, SON_ARMER, 0.8F, 0.95F + alea.nextFloat() * 0.1F, distance);
      jouer(joueur, SON_ESTOC, 1.0F, 0.9F + alea.nextFloat() * 0.1F, distance);
   }

   /** À chaque tick du jeu : joue les bruits des coups vus dont l'heure est venue (joueur toujours là et à portée). */
   static void sonsEnAttente() {
      if (ATTENTE.isEmpty()) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         ATTENTE.clear();
         return;
      }
      long maintenant = System.nanoTime();
      int distance = distanceSon();
      Iterator<SonEnAttente> it = ATTENTE.iterator();
      while (it.hasNext()) {
         SonEnAttente s = it.next();
         if (maintenant - s.quandNs() < 0L) {
            continue;
         }
         it.remove();
         if (distance > 0 && mc.level.getEntity(s.entite()) instanceof AbstractClientPlayer joueur
               && mc.player.distanceToSqr(joueur) <= (double) distance * distance) {
            jouer(joueur, s.son(), 1.0F, 1.0F, distance);
         }
      }
   }

   private static void jouer(AbstractClientPlayer joueur, ResourceLocation son, float volume, float hauteur, int distance) {
      try {
         SoundPlayManager.playClientSound(joueur, son, volume, hauteur, distance);
      } catch (RuntimeException | LinkageError ex) {
         LOG.debug("[couteaux] bruit du coup puissant impossible", ex);
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
