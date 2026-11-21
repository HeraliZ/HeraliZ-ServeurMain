package fr.heralisz.camo.client;

import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.HeraliszCamo;
import fr.heralisz.camo.fx.FxEffects;
import fr.heralisz.camo.knife.KnifeFinish;
import fr.heralisz.camo.network.CamoNetwork;
import fr.heralisz.camo.network.DessinsBilanPacket;
import com.mojang.logging.LogUtils;
import fr.heraliz.noyau.api.Notifs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CamoShaders {
   private static final Logger LOG = LogUtils.getLogger();
   /** Un petit programme par camo (généré par tools/gen_camo_shaders.py), indexé par shaderId. */
   private static final Map<Integer, ShaderInstance> SHADERS = new HashMap<>();
   /** Shaders des effets 3D (fx_<id>), même numéro que le camo. */
   private static final Map<Integer, ShaderInstance> FX = new HashMap<>();
   /** Empreinte des sources des programmes compilés (null : rien de compilé). */
   @Nullable
   private static String empreinte;
   /**
    * Programmes faits depuis la copie des dessins reçue du serveur ({@code /camos dessins}, docs/SKINS_PROTEGES.md) :
    * gardés pour la session, refaits depuis la copie (jamais depuis le jar) si leurs sources changent.
    */
   private static boolean depuisServeur;

   @Nullable
   public static ShaderInstance get(int shaderId) {
      return SHADERS.get(shaderId);
   }

   @Nullable
   public static ShaderInstance fx(int shaderId) {
      return FX.get(shaderId);
   }

   /**
    * Si un shader ne compile pas sur une machine, ce camo s'affiche juste sans effet (jamais de crash).
    * <p>
    * 19/11 (audit des images par seconde, point 11) : les ≈ 80 programmes (chacun avec l'include des camos, 261 Ko) étaient
    * recompilés à chaque rechargement des ressources (F3+T, et chaque changement des mipmaps par le profil graphique) :
    * plusieurs secondes de gel. Ils ne le sont plus que si leurs sources ont changé (empreinte des fichiers shaders/ de
    * heralisz_camo et des includes). Ils ne sont donc plus confiés à Minecraft (qui ferme à chaque rechargement tous les
    * programmes qu'on lui a donnés) : gardés ici, fermés ici quand on les refait. Les morceaux de programme partagés que
    * Minecraft libère au rechargement suivant ne gênent pas : un programme déjà lié reste valable.
    * <p>
    * 21/11 (docs/SKINS_PROTEGES.md, étape 1) : programmes déjà refaits depuis la copie reçue du serveur
    * ({@code /camos dessins}) : refaits depuis cette copie seulement (jamais depuis le jar), et seulement si leurs sources
    * ont changé (empreinte de la copie et des includes de Minecraft) ; c'est le mécanisme prévu pour l'étape 2.
    */
   @SubscribeEvent
   public static void onRegisterShaders(RegisterShadersEvent event) {
      Map<ResourceLocation, byte[]> copie = depuisServeur ? DessinsRecus.fichiers() : null;
      depuisServeur = copie != null;
      String emp = copie != null ? empreinte(copie) : empreinte();
      if (emp != null && emp.equals(empreinte) && !SHADERS.isEmpty()) {
         LOG.info("Shaders des camos inchangés : {} programmes gardés, rien recompilé", distincts().size());
         return;
      }
      fermer();
      empreinte = emp;
      ResourceProvider sources = copie != null ? DessinsRecus.fournisseur(copie, event.getResourceProvider()) : event.getResourceProvider();
      Bilan b = construire(sources);
      if (copie != null) {
         LOG.info("[Camos] dessins des skins refaits depuis la copie du serveur (rechargement) : {} programmes prêts, {} en échec",
            b.prets(), b.echecs());
      }
   }

   /** Programmes prêts et en échec d'une construction. */
   private record Bilan(int prets, int echecs) {
   }

   /** Compile tous les programmes des skins (camos, finitions des couteaux, effets 3D) avec ces sources ; fil du rendu. */
   private static Bilan construire(ResourceProvider sources) {
      SHADERS.clear();
      FX.clear();
      int prets = 0, echecs = 0;
      // programme -> skins qu'il dessine (les skins de couteaux par paliers partagent un seul programme)
      Map<Integer, Set<Integer>> programmes = new TreeMap<>();
      for (Camo c : Camo.values()) programmes.computeIfAbsent(c.shaderId, k -> new TreeSet<>()).add(c.shaderId);
      for (KnifeFinish f : KnifeFinish.values()) programmes.computeIfAbsent(f.programme(), k -> new TreeSet<>()).add(f.shaderId);
      for (Map.Entry<Integer, Set<Integer>> e : programmes.entrySet()) {
         int id = e.getKey();
         Set<Integer> skins = e.getValue();
         try {
            ShaderInstance s = new ShaderInstance(sources, new ResourceLocation(HeraliszCamo.MODID, "camo_" + id), DefaultVertexFormat.NEW_ENTITY);
            for (int skin : skins) SHADERS.put(skin, s);
            prets++;
         } catch (IOException | RuntimeException ex) {
            echecs++;
            LOG.error("Shader camo_{} indisponible, ce skin s'affichera sans effet", id, ex);
         }
      }
      for (int id : FxEffects.ids()) {
         try {
            FX.put(id, new ShaderInstance(sources, new ResourceLocation(HeraliszCamo.MODID, "fx_" + id), DefaultVertexFormat.NEW_ENTITY));
            prets++;
         } catch (IOException | RuntimeException ex) {
            echecs++;
            LOG.error("Shader fx_{} indisponible, l'effet 3D de ce skin ne s'affichera pas", id, ex);
         }
      }
      return new Bilan(prets, echecs);
   }

   /**
    * {@code /camos dessins} (staff ; 21/11, docs/SKINS_PROTEGES.md, étape 1) : refait tous les programmes des skins (camos,
    * finitions des couteaux, effets 3D) depuis la copie reçue du serveur, sur le fil du rendu, puis un bandeau donne le
    * nombre de programmes prêts et le temps pris. Ces programmes servent ensuite pour toute la session (rendu identique
    * attendu) ; copie absente ou illisible : rien n'est touché.
    */
   public static void refaireDepuisServeur() {
      if (!RenderSystem.isOnRenderThread()) {
         RenderSystem.recordRenderCall(CamoShaders::refaireDepuisServeur);
         return;
      }
      long debut = System.nanoTime();
      Map<ResourceLocation, byte[]> copie = DessinsRecus.fichiers();
      if (copie == null) {
         boolean rien = !DessinsRecus.recue();
         LOG.warn("[Camos] /camos dessins : {}, les dessins du jeu restent en place", rien ? "rien reçu du serveur" : "copie du serveur illisible");
         bandeau(Component.translatable(rien ? "gui.heralisz_camo.dessins.rien" : "gui.heralisz_camo.dessins.illisible").getString(), Notifs.DANGER);
         bilan(new DessinsBilanPacket(rien ? DessinsBilanPacket.RIEN : DessinsBilanPacket.ILLISIBLE, 0, 0, 0));
         return;
      }
      // les morceaux de programme déjà compilés depuis le jar sont oubliés : tout est compilé depuis la copie
      oublierEtagesEnCache();
      fermer();
      Bilan b = construire(DessinsRecus.fournisseur(copie, Minecraft.getInstance().getResourceManager()));
      empreinte = empreinte(copie);
      depuisServeur = true;
      // icônes 3D de l'inventaire : refaites avec les nouveaux programmes (l'essai les couvre aussi)
      IconesArmes.vider();
      long ms = (System.nanoTime() - debut) / 1_000_000L;
      LOG.info("[Camos] dessins des skins refaits depuis la copie du serveur : {} programmes prêts, {} en échec, {} ms", b.prets(), b.echecs(), ms);
      bilan(new DessinsBilanPacket(DessinsBilanPacket.FAIT, b.prets(), b.echecs(), (int) Math.min(ms, Integer.MAX_VALUE)));
      bandeau(b.echecs() == 0
            ? Component.translatable("gui.heralisz_camo.dessins.prets", String.valueOf(b.prets()), String.valueOf(ms)).getString()
            : Component.translatable("gui.heralisz_camo.dessins.echecs", String.valueOf(b.prets()), String.valueOf(b.echecs()), String.valueOf(ms)).getString(),
         b.echecs() == 0 ? Notifs.OK : Notifs.ALERTE);
   }

   private static void bandeau(String texte, int couleur) {
      fr.heraliz.noyau.client.Bandeaux.ajouter(Component.translatable("gui.heralisz_camo.dessins.titre").getString(), texte, couleur, 8);
   }

   /** Le résultat de l'essai, aussi pour le journal du serveur (s'il est encore là). */
   private static void bilan(DessinsBilanPacket b) {
      if (Minecraft.getInstance().getConnection() != null) CamoNetwork.CHANNEL.sendToServer(b);
   }

   /**
    * Ferme les morceaux de programme (vsh, fsh) des skins que Minecraft garde en réserve sous leur nom
    * (« heralisz_camo:camo », « heralisz_camo:camo_12 »…) : sinon un nouveau programme reprendrait ceux compilés depuis le
    * jar. Un programme déjà lié reste valable ; le sticker n'est pas touché.
    */
   private static void oublierEtagesEnCache() {
      for (Program.Type type : Program.Type.values()) {
         List<Program> anciens = new ArrayList<>();
         for (Map.Entry<String, Program> e : type.getPrograms().entrySet()) {
            String nom = e.getKey();
            if (nom.startsWith(HeraliszCamo.MODID + ":camo") || nom.startsWith(HeraliszCamo.MODID + ":fx")) anciens.add(e.getValue());
         }
         for (Program p : anciens) p.close();
      }
   }

   /** Programmes gardés (un même programme sert plusieurs skins de couteaux). */
   private static Set<ShaderInstance> distincts() {
      Set<ShaderInstance> s = Collections.newSetFromMap(new IdentityHashMap<>());
      s.addAll(SHADERS.values());
      s.addAll(FX.values());
      return s;
   }

   /** Ferme les programmes d'avant (refaits parce que leurs sources ont changé). */
   private static void fermer() {
      for (ShaderInstance s : distincts()) {
         try {
            s.close();
         } catch (RuntimeException ex) {
            LOG.warn("Shader {} : fermeture impossible ({})", s.getName(), ex.toString());
         }
      }
      SHADERS.clear();
      FX.clear();
   }

   /**
    * Empreinte des sources : fichiers shaders/ de heralisz_camo (programmes, vsh, fsh) et tous les includes (le nôtre et
    * ceux de Minecraft qu'il importe), tels que les packs en cours les donnent. Null si on ne peut pas la faire : on
    * recompile alors, comme avant.
    */
   @Nullable
   private static String empreinte() {
      try {
         MessageDigest md = MessageDigest.getInstance("SHA-256");
         Map<ResourceLocation, Resource> res = Minecraft.getInstance().getResourceManager().listResources("shaders",
            rl -> rl.getNamespace().equals(HeraliszCamo.MODID) || rl.getPath().startsWith("shaders/include/"));
         for (Map.Entry<ResourceLocation, Resource> en : new TreeMap<>(res).entrySet()) {
            md.update(en.getKey().toString().getBytes(StandardCharsets.UTF_8));
            try (InputStream in = en.getValue().open()) {
               md.update(in.readAllBytes());
            }
         }
         return HexFormat.of().formatHex(md.digest());
      } catch (Exception ex) {
         LOG.warn("Shaders des camos : empreinte impossible ({}), recompilés", ex.toString());
         return null;
      }
   }

   /**
    * Empreinte des sources quand elles viennent de la copie du serveur : ses fichiers (noms et octets) et les includes de
    * Minecraft qu'elle ne donne pas (fog.glsl, light.glsl…), tels que les packs en cours les donnent. Null si on ne peut
    * pas la faire : on recompile.
    */
   @Nullable
   private static String empreinte(Map<ResourceLocation, byte[]> copie) {
      try {
         MessageDigest md = MessageDigest.getInstance("SHA-256");
         for (Map.Entry<ResourceLocation, byte[]> en : new TreeMap<>(copie).entrySet()) {
            md.update(en.getKey().toString().getBytes(StandardCharsets.UTF_8));
            md.update(en.getValue());
         }
         Map<ResourceLocation, Resource> res = Minecraft.getInstance().getResourceManager().listResources("shaders",
            rl -> rl.getPath().startsWith("shaders/include/") && !copie.containsKey(rl));
         for (Map.Entry<ResourceLocation, Resource> en : new TreeMap<>(res).entrySet()) {
            md.update(en.getKey().toString().getBytes(StandardCharsets.UTF_8));
            try (InputStream in = en.getValue().open()) {
               md.update(in.readAllBytes());
            }
         }
         return "serveur:" + HexFormat.of().formatHex(md.digest());
      } catch (Exception ex) {
         LOG.warn("Shaders des camos (copie du serveur) : empreinte impossible ({}), recompilés", ex.toString());
         return null;
      }
   }

   @SubscribeEvent
   public static void onRegisterDecorations(net.minecraftforge.client.event.RegisterItemDecorationsEvent event) {
      BagCamo.registerBadges(event);
   }

   @SubscribeEvent
   public static void onRegisterReload(RegisterClientReloadListenersEvent event) {
      event.registerReloadListener((ResourceManagerReloadListener) rm -> {
         PosMapCache.clear();
         FxRenderTypes.clear();
      });
   }

   private CamoShaders() {}
}
