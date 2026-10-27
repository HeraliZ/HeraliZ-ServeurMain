package fr.heralisz.camo.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.CamoData;
import fr.heralisz.camo.HeraliszCamo;
import fr.heralisz.camo.knife.KnifeSkins;
import fr.heralisz.camo.knife.KnifeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

/**
 * Préchauffage des cartes de positions des skins (PosMapCache) : les armes skinnées du joueur ont leur carte calculée avant
 * d'être sorties, d'abord la barre d'objets, puis la main gauche, puis le reste de l'inventaire. Une demande toutes les
 * 10 ticks au plus, seulement quand aucun calcul n'est en cours : rien ne s'accumule. Armes TaCZ (camo, skins de couteaux :
 * modèle en main puis modèle simplifié de loin) et armes de mêlée skinnées. Joueur seulement, rien n'est envoyé au serveur.
 */
@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID, value = Dist.CLIENT)
public final class PrechauffageCamo {
   /** Ticks entre deux passages (0,5 s). */
   private static final int INTERVALLE = 10;
   /** Cases de la barre d'objets. */
   private static final int BARRE = 9;
   private static int attente;

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END || ++attente < INTERVALLE) {
         return;
      }
      attente = 0;
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer joueur = mc.player;
      if (joueur == null || mc.level == null || mc.getOverlay() != null || PosMapCache.occupe()) {
         return;
      }
      Inventory inv = joueur.getInventory();
      int n = Math.min(BARRE, inv.items.size());
      for (int i = 0; i < n; i++) {
         if (demander(inv.items.get(i))) return;
      }
      for (ItemStack stack : inv.offhand) {
         if (demander(stack)) return;
      }
      for (int i = n; i < inv.items.size(); i++) {
         if (demander(inv.items.get(i))) return;
      }
   }

   /** Vrai si une carte a été demandée pour cet objet (une seule par passage). */
   private static boolean demander(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      }
      return IGun.getIGunOrNull(stack) != null ? arme(stack) : MeleeCamo.prechauffer(stack);
   }

   /** Arme TaCZ : même choix que le rendu (BedrockGunModelMixin) : le camo d'abord, sinon le skin de couteau. */
   private static boolean arme(ItemStack stack) {
      Camo camo = CamoData.get(stack);
      KnifeSkins.Skin lame = camo == null ? KnifeSkins.of(stack) : null;
      if (camo == null && lame == null) {
         return false;
      }
      if (CamoShaders.get(camo != null ? camo.shaderId : lame.finish().shaderId) == null) {
         return false;
      }
      GunDisplayInstance display = TimelessAPI.getGunDisplay(stack).orElse(null);
      if (display == null) {
         return false;
      }
      KnifeType couteau = camo != null ? null : lame.knife();
      // TaCZ charge ses modèles à la demande : getGunModel() / getLodModel() d'un modèle pas encore chargé le chargerait
      // ici même, sur le fil de rendu (le gel qu'on évite) ; seuls les modèles déjà chargés sont préchauffés
      if (charge(MODELE_CHARGE, display) && modele(display.getGunModel(), couteau, display.getModelTexture())) {
         return true;
      }
      if (!charge(LOD_CHARGE, display)) {
         return false;
      }
      Pair<BedrockGunModel, ResourceLocation> lod = display.getLodModel();
      return lod != null && modele(lod.getLeft(), couteau, lod.getRight());
   }

   /** Champs « modèle chargé » de TaCZ (pas de méthode publique) ; illisibles : rien n'est préchauffé. */
   @Nullable
   private static final Field MODELE_CHARGE = champ("modelLoaded"), LOD_CHARGE = champ("lodLoaded");

   @Nullable
   private static Field champ(String nom) {
      try {
         Field f = GunDisplayInstance.class.getDeclaredField(nom);
         f.setAccessible(true);
         return f;
      } catch (ReflectiveOperationException | RuntimeException e) {
         return null;
      }
   }

   private static boolean charge(@Nullable Field champ, GunDisplayInstance display) {
      try {
         return champ != null && champ.getBoolean(display);
      } catch (ReflectiveOperationException | RuntimeException e) {
         return false;
      }
   }

   private static boolean modele(@Nullable BedrockGunModel modele, @Nullable KnifeType couteau, @Nullable ResourceLocation texture) {
      // sans texture, le rendu ne demande pas de carte non plus
      return modele != null && texture != null && PosMapCache.prechauffer(modele, couteau, texture);
   }

   private PrechauffageCamo() {}
}
