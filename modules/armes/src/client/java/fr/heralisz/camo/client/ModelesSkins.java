package fr.heralisz.camo.client;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.GunDisplayInstance;
import fr.heralisz.camo.Camo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Modèles 3D propres à un skin sur une arme précise (façon « skin à modèle ») : quand l'arme porte ce skin, le jeu du joueur
 * la montre avec un autre affichage TaCZ du gunpack heralisz_skins (modèle, texture ; animations, sons, icône et petit modèle
 * de loin de l'arme de base). Rien n'est enregistré sur l'arme : retirer le skin rend le modèle de base. Branché sur
 * TimelessAPI.getGunDisplay (TimelessApiAffichageMixin), avant le cache des affichages.
 * <ul>
 *    <li>AK-47 (tacz:ak47) + Faille : heralisz_skins:ak47_faille_display (05/11, rendus validés : maquettes/skins_premium/ak_faille ;
 *    modèle écrit par outils/ak47_faille.py --installer).</li>
 * </ul>
 */
public final class ModelesSkins {
   private static final Map<String, Map<Camo, ResourceLocation>> PAR_ARME = Map.of(
      "tacz:ak47", Map.of(Camo.FAILLE, new ResourceLocation("heralisz_skins", "ak47_faille_display")));
   /** Affichages résolus, gardés le temps d'un tick (TaCZ écrit dans le journal à chaque appel si l'un d'eux manque). */
   private static final Map<ResourceLocation, Optional<GunDisplayInstance>> GARDES = new HashMap<>();
   private static java.lang.ref.WeakReference<ClientLevel> monde = new java.lang.ref.WeakReference<>(null);
   private static long tick = Long.MIN_VALUE;

   private ModelesSkins() {
   }

   /** Affichage propre au skin de cette arme, null si elle n'en a pas (cas courant : sort au premier test). */
   @Nullable
   public static Optional<GunDisplayInstance> affichage(ItemStack stack) {
      CompoundTag tag = stack.getTag();
      if (tag == null) return null;
      String gunId = tag.getString("GunId");
      Map<Camo, ResourceLocation> parCamo = PAR_ARME.get(gunId);
      if (parCamo == null) return null;
      // un affichage déjà choisi pour cette arme (autre que celui par défaut) passe avant
      if (tag.contains("GunDisplayId", Tag.TAG_STRING)) {
         ResourceLocation d = ResourceLocation.tryParse(tag.getString("GunDisplayId"));
         if (d != null && !d.equals(DefaultAssets.DEFAULT_GUN_DISPLAY_ID)) return null;
      }
      Camo camo = CamoClient.camoFor(stack);
      ResourceLocation id = camo == null ? null : parCamo.get(camo);
      if (id == null) return null;
      ResourceLocation arme = ResourceLocation.tryParse(gunId);
      if (arme == null) return null;
      if (!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
         // hors du fil du jeu : pas de cache (il n'est lu et écrit que sur ce fil)
         return TimelessAPI.getGunDisplay(id, arme);
      }
      ClientLevel level = Minecraft.getInstance().level;
      long t = level == null ? Long.MIN_VALUE : level.getGameTime();
      if (level != monde.get() || t != tick) {
         monde = new java.lang.ref.WeakReference<>(level);
         tick = t;
         GARDES.clear();
      }
      Optional<GunDisplayInstance> d = GARDES.get(id);
      if (d == null) {
         d = TimelessAPI.getGunDisplay(id, arme);
         GARDES.put(id, d);
      }
      return d;
   }
}
