package fr.heraliz.tactique.client.accessoires;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IAttachment;
import fr.heraliz.tactique.HeralizTactique;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Accessoires TaCZ tenus en main (première personne) qui sortaient de l'écran : silencieux, bipieds, crosses longues
 * (retour du propriétaire du 11/11 : « un bout du silencieux est hors de l'écran », « X48 BIPOD invisible dans la main »).
 * <p>
 * TaCZ dessine un accessoire tenu comme un objet de Minecraft (transformation « firstperson_righthand » de son modèle
 * tacz:item/attachment, celle des objets plats) en posant l'origine de son modèle au centre de l'objet. Cette origine est
 * le point où l'accessoire se fixe sur l'arme : l'arrière d'un silencieux, le haut d'une poignée. Un silencieux, long de
 * 8 à 20 px vers l'avant, partait vers la droite de l'écran et son bout sortait de l'image ; le bipied X48, dessiné 8 à
 * 12 px devant son point de fixation, tombait tout entier hors de l'écran.
 * <p>
 * La table assets/heraliz_tactique/accessoires_en_main.json, ÉCRITE par modules/armes/outils/accessoires_en_main.py (calcul
 * de l'écran : main droite, champ de vision de la main 70°, 16:9), donne pour chaque accessoire concerné le milieu de son
 * modèle et l'échelle qui le fait tenir à l'écran : il est tenu par son milieu, réduit juste assez. Les autres ne bougent
 * pas ; rien ne change sur l'arme, au sol, dans un cadre, dans l'inventaire ni vu des autres joueurs (troisième personne).
 * Table lue une fois (thread de rendu), sans allocation ensuite.
 */
public final class AccessoiresEnMain {
   private static final Logger LOG = LogUtils.getLogger();
   private static final ResourceLocation TABLE = new ResourceLocation(HeralizTactique.MODID, "accessoires_en_main.json");
   /** Accessoire -> {x, y, z du milieu (blocs, repère de l'objet tenu, origine au centre de l'objet), échelle}. */
   @Nullable
   private static Map<ResourceLocation, float[]> table;

   private AccessoiresEnMain() {
   }

   /**
    * Au début du dessin d'un accessoire tenu (AttachmentItemRenderer.renderByItem, repère du cube de l'objet après le
    * -0,5 de Minecraft : centre de l'objet en 0,5 ; 0,5 ; 0,5). TaCZ y pose ensuite le modèle, origine au centre : le
    * milieu du modèle est ramené au centre de l'objet, puis le tout est réduit autour de lui. Le dessin de l'objet est
    * encadré par un pushPose / popPose de Minecraft (ItemRenderer.render) : rien ne déborde sur la suite.
    */
   public static void placer(ItemStack stack, ItemDisplayContext ctx, PoseStack pose) {
      if (!ctx.firstPerson() || !(stack.getItem() instanceof IAttachment accessoire)) {
         return;
      }
      ResourceLocation id = accessoire.getAttachmentId(stack);
      float[] r = id == null ? null : table().get(id);
      if (r == null) {
         return;
      }
      pose.translate(0.5F, 0.5F, 0.5F);
      pose.scale(r[3], r[3], r[3]);
      pose.translate(-0.5F - r[0], -0.5F - r[1], -0.5F - r[2]);
   }

   private static Map<ResourceLocation, float[]> table() {
      Map<ResourceLocation, float[]> t = table;
      if (t == null) {
         table = t = lire();
      }
      return t;
   }

   /** Table du jar ; absente ou illisible : table vide (TaCZ dessine comme avant) et un avertissement. */
   private static Map<ResourceLocation, float[]> lire() {
      Map<ResourceLocation, float[]> t = new HashMap<>();
      Resource res = Minecraft.getInstance().getResourceManager().getResource(TABLE).orElse(null);
      if (res == null) {
         LOG.warn("[accessoires en main] {} absente : accessoires tenus dessinés par TaCZ", TABLE);
         return t;
      }
      try (Reader lecteur = res.openAsReader()) {
         JsonObject accessoires = JsonParser.parseReader(lecteur).getAsJsonObject().getAsJsonObject("accessoires");
         for (Map.Entry<String, JsonElement> e : accessoires.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey());
            if (id == null || !e.getValue().isJsonArray()) {
               continue;
            }
            JsonArray a = e.getValue().getAsJsonArray();
            if (a.size() != 4) {
               continue;
            }
            float echelle = a.get(3).getAsFloat();
            if (!(echelle > 0.0F) || echelle > 1.0F) {
               continue;
            }
            // pixels du modèle -> blocs
            t.put(id, new float[]{a.get(0).getAsFloat() / 16.0F, a.get(1).getAsFloat() / 16.0F, a.get(2).getAsFloat() / 16.0F, echelle});
         }
      } catch (Exception ex) {
         LOG.warn("[accessoires en main] {} illisible : accessoires tenus dessinés par TaCZ", TABLE, ex);
         t.clear();
      }
      return t;
   }
}
