package fr.heraliz.tactique.client.couteaux;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.Animations;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.animation.bedrock.BedrockAnimationFile;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import com.tacz.guns.compat.playeranimator.animation.AnimationManager;
import fr.heraliz.tactique.couteaux.PaquetCoupVu;
import fr.heraliz.tactique.HeralizTactique;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;

/**
 * Animation du coup puissant des couteaux (clic droit), différente du coup rapide (14/10) : « melee_stock », tirée du
 * « melee_push » de chaque modèle par {@code outils/couteaux_coup_lourd.py}
 * ({@code assets/heraliz_tactique/couteaux/<modèle>.animation.json}). Donnée au contrôleur d'animation de TaCZ du couteau
 * tenu s'il ne l'a pas, puis jouée par le signal « bayonet_stock » de la machine à états par défaut. Modèle sans fichier
 * ou illisible : le coup puissant garde l'animation du coup rapide.
 */
final class CoupLourdClient {
   private static final Logger LOG = LogUtils.getLogger();
   static final String CLIP = "melee_stock";
   private static final Map<String, Optional<ObjectAnimation>> CACHE = new HashMap<>();

   private CoupLourdClient() {
   }

   /** Vrai si le contrôleur a (ou vient de recevoir) le coup puissant pour ce couteau. */
   static boolean preparer(@Nullable AnimationController controleur, ResourceLocation arme) {
      if (controleur == null || arme == null) return false;
      if (controleur.containPrototype(CLIP)) return true;
      String modele = modele(arme.getPath());
      if (modele == null) return false;
      ObjectAnimation anim = CACHE.computeIfAbsent(modele, CoupLourdClient::charger).orElse(null);
      if (anim == null) return false;
      controleur.providePrototypeIfAbsent(CLIP, () -> anim);
      return controleur.containPrototype(CLIP);
   }

   /** Alternance des deux gestes du coup rapide vu en 3e personne. */
   private static boolean alterne;

   /**
    * Coup d'un autre joueur, vu en 3e personne : grand coup de haut en bas de TaCZ (« melee_3_upper ») pour le coup puissant,
    * « melee_upper » / « melee_2_upper » en alternance pour le coup rapide. Rien si Player Animator manque.
    */
   static void vu(PaquetCoupVu p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || !(mc.level.getEntity(p.entite()) instanceof AbstractClientPlayer joueur)) return;
      String geste = p.lourd() ? "melee_3_upper" : (alterne = !alterne) ? "melee_upper" : "melee_2_upper";
      TimelessAPI.getGunDisplay(joueur.getMainHandItem()).ifPresent(affichage -> {
         try {
            if (PlayerAnimatorCompat.isInstalled() && AnimationManager.hasPlayerAnimator3rd(affichage)) {
               AnimationManager.playOnceAnimation(joueur, affichage, PlayerAnimatorCompat.ONCE_UPPER_ANIMATION, geste);
            }
         } catch (RuntimeException | LinkageError ex) {
            LOG.debug("[couteaux] geste en 3e personne impossible", ex);
         }
      });
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
      Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(rl);
      if (res.isEmpty()) return Optional.empty();
      try (Reader r = res.get().openAsReader()) {
         JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
         BedrockAnimationFile file = ClientAssetsManager.GSON.fromJson(json, BedrockAnimationFile.class);
         return Animations.createAnimationFromBedrock(file).stream().filter(a -> CLIP.equals(a.name)).findFirst();
      } catch (Exception e) {
         LOG.warn("[couteaux] coup puissant de {} illisible : animation du coup rapide", modele, e);
         return Optional.empty();
      }
   }
}
