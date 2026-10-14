package fr.heralisz.camo.client.atelier;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import fr.heralisz.camo.client.kit.Ui;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Effets d'un accessoire en textes courts (« + Dégâts » en vert, « - Bruit »…), lus dans les données de TaCZ (les mêmes
 * lignes que son infobulle) : zoom des viseurs, capacité des chargeurs, modificateurs. Atelier d'arme et table d'armurier.
 */
final class Effets {
   private static final String PREFIXE = "tooltip.tacz.attachment.";
   /** Effets déjà lus, par accessoire (vidé à chaque ouverture d'écran : les données arrivent du serveur à la connexion). */
   private static final Map<ResourceLocation, List<Effet>> CACHE = new HashMap<>();

   /**
    * Un effet : texte court et couleur (vert : mieux, rouge : moins bien, or : spécial, gris : neutre) ; {@code cle} : la
    * caractéristique de TaCZ (damage, ads, pitch…) ou null (zoom, capacité).
    */
   record Effet(String texte, int couleur, @Nullable String cle) {
      Effet(String texte, int couleur) {
         this(texte, couleur, null);
      }
   }

   /** Caractéristiques chiffrées par {@link Stats#effets} (les lignes de TaCZ correspondantes ne sont pas répétées). */
   private static final java.util.Set<String> CHIFFREES = java.util.Set.of("damage", "rpm", "ads", "pitch", "yaw", "effective_range",
      "aim_inaccuracy", "inaccuracy", "hipfire_inaccuracy", "weight", "ammo_speed");
   /** Effets chiffrés sur l'arme, par accessoire (vidé avec {@link #CACHE}). */
   private static final Map<ResourceLocation, List<Effet>> SUR_ARME = new HashMap<>();

   private Effets() {
   }

   static void vider() {
      CACHE.clear();
      SUR_ARME.clear();
   }

   /**
    * Effets de l'accessoire {@code s} sur l'arme {@code gun}, chiffrés (« -20 % recul vert. », « -0,03 s visée ») : ce que
    * l'arme gagne ou perd en le posant à la place de ce qui occupe son emplacement ; puis les effets sans chiffre de TaCZ
    * (zoom, silencieux, capacité…). Sans arme : {@link #de}.
    */
   static List<Effet> sur(ItemStack gun, ItemStack s) {
      IAttachment ia = IAttachment.getIAttachmentOrNull(s);
      if (ia == null) return List.of();
      if (gun.isEmpty()) return de(s);
      return SUR_ARME.computeIfAbsent(ia.getAttachmentId(s), id -> {
         List<Effet> out = new ArrayList<>();
         List<Stats.Effet> chiffres = List.of();
         try {
            chiffres = Stats.effets(gun, s);
         } catch (RuntimeException ignored) {
            // arme ou accessoire dont TaCZ ne calcule pas les chiffres : effets sans chiffre seulement
         }
         for (Effet e : de(s)) {
            if (e.cle() == null || chiffres.isEmpty() || !CHIFFREES.contains(e.cle())) out.add(e);
         }
         List<Effet> num = new ArrayList<>();
         for (Stats.Effet c : chiffres) {
            num.add(new Effet(c.texte() + " " + I18n.get("gui.heralisz_camo.atelier.court." + c.cle()), c.mieux() ? Ui.OK : Ui.DANGER, c.cle()));
         }
         num.addAll(out);
         return num;
      });
   }

   /** Effets de l'accessoire (liste vide pour un autre objet). */
   static List<Effet> de(ItemStack s) {
      IAttachment ia = IAttachment.getIAttachmentOrNull(s);
      if (ia == null) return List.of();
      AttachmentType type = ia.getType(s);
      return CACHE.computeIfAbsent(ia.getAttachmentId(s), id -> calculer(id, type));
   }

   private static List<Effet> calculer(ResourceLocation id, AttachmentType type) {
      ClientAttachmentIndex idx = TimelessAPI.getClientAttachmentIndex(id).orElse(null);
      if (idx == null) return List.of();
      List<Effet> out = new ArrayList<>();
      float[] zoom = idx.getZoom();
      if (type == AttachmentType.SCOPE && zoom != null && zoom.length > 0) {
         StringBuilder sb = new StringBuilder();
         for (int i = 0; i < zoom.length; i++) {
            if (i > 0) sb.append(" / ");
            sb.append('×').append(nombre(zoom[i]));
         }
         out.add(new Effet(sb.toString(), Ui.ACCENT_NEUTRE));
      }
      AttachmentData data = idx.getData();
      if (data != null) {
         int niveau = data.getExtendedMagLevel();
         if (niveau > 0) out.add(new Effet(AtelierArme.tr("effet.capacite", "I".repeat(Math.min(3, niveau))), Ui.OK));
         for (JsonProperty<?> prop : data.getModifier().values()) {
            for (Component c : prop.getComponents()) {
               Effet e = effet(c);
               if (e != null) out.add(e);
            }
         }
      }
      return out;
   }

   /** Une ligne d'effet de TaCZ (« + Dégâts » en vert…) en texte court, avec sa couleur. */
   @Nullable
   private static Effet effet(Component c) {
      int couleur = Ui.MUTED;
      TextColor tc = c.getStyle().getColor();
      if (tc != null) {
         int v = tc.getValue() & 0xFFFFFF;
         if (v == 0x55FF55) couleur = Ui.OK;
         else if (v == 0xFF5555) couleur = Ui.DANGER;
         else if (v == 0xFFAA00) couleur = Ui.OR;
      }
      if (c.getContents() instanceof TranslatableContents t && t.getKey().startsWith(PREFIXE)) {
         String reste = t.getKey().substring(PREFIXE.length());
         String signe = "";
         if (reste.endsWith(".increase")) {
            signe = "+ ";
            reste = reste.substring(0, reste.length() - ".increase".length());
         } else if (reste.endsWith(".decrease")) {
            signe = "- ";
            reste = reste.substring(0, reste.length() - ".decrease".length());
         }
         String cle = "gui.heralisz_camo.atelier.effet." + reste.replace('.', '_');
         if (I18n.exists(cle)) return new Effet(signe + I18n.get(cle), couleur, reste.replace('.', '_'));
      }
      String s = c.getString().trim();
      return s.isEmpty() ? null : new Effet(s, couleur);
   }

   private static String nombre(float v) {
      String s = String.format(Locale.ROOT, "%.1f", v);
      if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
      return Ui.french() ? s.replace('.', ',') : s;
   }
}
