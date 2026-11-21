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
import net.minecraftforge.event.TickEvent;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Programmes des skins : camos, finitions des couteaux, effets 3D (un programme par camo, indexé par shaderId).
 * <p>
 * 21/11, étape 2 de la protection des skins (docs/SKINS_PROTEGES.md) : leurs dessins ne sont plus dans le jar des joueurs.
 * Le serveur en envoie une copie à chaque connexion ({@link DessinsRecus}) et les programmes sont fabriqués ici depuis elle,
 * sur le fil du rendu, par tranches d'au plus {@value #TRANCHE_MS} ms au début de chaque image (l'écran de connexion reste
 * vivant : jamais un long gel d'un coup). Une seule fois par copie : une reconnexion au même serveur (même copie) ne refait
 * rien, F3+T non plus si rien n'a changé. Sans programme (rien reçu, copie illisible, programme qui ne se fabrique pas sur
 * cette machine), le skin s'affiche sans effet : {@link #get} et {@link #fx} rendent null et chaque rendu dessine l'arme sans
 * skin (BedrockGunModelMixin, BagCamo, MeleeCamo, IconesArmes, PrechauffageCamo, CamoFx). Jamais de plantage : une erreur
 * est écrite au journal et le skin reste sans effet.
 */
@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CamoShaders {
   private static final Logger LOG = LogUtils.getLogger();
   /** Un petit programme par camo (sources écrites par modules/armes/outils/generer_camos.py), indexé par shaderId. */
   private static final Map<Integer, ShaderInstance> SHADERS = new HashMap<>();
   /** Shaders des effets 3D (fx_<id>), même numéro que le camo. */
   private static final Map<Integer, ShaderInstance> FX = new HashMap<>();
   /** Empreinte des sources des programmes en place (copie du serveur + includes de Minecraft) ; null : aucun programme. */
   @Nullable
   private static String empreinte;
   /** Empreinte annoncée par le serveur pour la copie qui a donné les programmes en place (reconnexion : rien à refaire). */
   @Nullable
   private static String copieEnPlace;
   /** Temps de compilation au plus par image pendant une fabrication (ms) ; au moins un programme par image. */
   private static final long TRANCHE_MS = 25L;
   /** Fabrication en cours (fil du rendu seulement) ; null : aucune. */
   @Nullable
   private static Fabrique enCours;

   @Nullable
   public static ShaderInstance get(int shaderId) {
      return SHADERS.get(shaderId);
   }

   @Nullable
   public static ShaderInstance fx(int shaderId) {
      return FX.get(shaderId);
   }

   /** Programmes prêts et en échec d'une construction. */
   private record Bilan(int prets, int echecs) {
   }

   /**
    * Une fabrication des programmes depuis une copie, programme par programme (fil du rendu). Les programmes faits servent
    * tout de suite ; ceux qui manquent encore : skin sans effet jusqu'à leur tour.
    */
   private static final class Fabrique {
      final ResourceProvider sources;
      @Nullable
      final String emp;
      @Nullable
      final String annonce;
      /** Demandée par le staff ({@code /camos dessins}) : bandeau et résultat envoyé au serveur à la fin. */
      final boolean staff;
      final Iterator<Map.Entry<Integer, Set<Integer>>> camos;
      final Iterator<Integer> effets;
      final long debut = System.nanoTime();
      /** Temps passé à compiler (sans les images entre deux tranches), en nanosecondes. */
      long compilation;
      int prets, echecs;

      Fabrique(ResourceProvider sources, @Nullable String emp, @Nullable String annonce, boolean staff) {
         this.sources = sources;
         this.emp = emp;
         this.annonce = annonce;
         this.staff = staff;
         this.camos = programmes().entrySet().iterator();
         List<Integer> ids = new ArrayList<>();
         FxEffects.ids().forEach(ids::add);
         this.effets = ids.iterator();
      }

      /** Fabrique le programme suivant ; faux s'il n'en reste plus. */
      boolean suivant() {
         if (this.camos.hasNext()) {
            Map.Entry<Integer, Set<Integer>> e = this.camos.next();
            if (camo(this.sources, e.getKey(), e.getValue())) this.prets++;
            else this.echecs++;
            return true;
         }
         if (this.effets.hasNext()) {
            if (effet(this.sources, this.effets.next())) this.prets++;
            else this.echecs++;
            return true;
         }
         return false;
      }
   }

   /** Programme -> skins qu'il dessine (les skins de couteaux par paliers partagent un seul programme). */
   private static Map<Integer, Set<Integer>> programmes() {
      Map<Integer, Set<Integer>> programmes = new TreeMap<>();
      for (Camo c : Camo.values()) programmes.computeIfAbsent(c.shaderId, k -> new TreeSet<>()).add(c.shaderId);
      for (KnifeFinish f : KnifeFinish.values()) programmes.computeIfAbsent(f.programme(), k -> new TreeSet<>()).add(f.shaderId);
      return programmes;
   }

   /** Le programme camo_<id> pour ces skins ; faux s'il ne se fait pas (le skin s'affichera sans effet). */
   private static boolean camo(ResourceProvider sources, int id, Set<Integer> skins) {
      try {
         ShaderInstance s = new ShaderInstance(sources, new ResourceLocation(HeraliszCamo.MODID, "camo_" + id), DefaultVertexFormat.NEW_ENTITY);
         for (int skin : skins) SHADERS.put(skin, s);
         return true;
      } catch (IOException | RuntimeException | LinkageError ex) {
         LOG.error("Shader camo_{} indisponible, ce skin s'affichera sans effet", id, ex);
         return false;
      }
   }

   /** Le programme fx_<id> d'un effet 3D ; faux s'il ne se fait pas (l'effet ne s'affichera pas). */
   private static boolean effet(ResourceProvider sources, int id) {
      try {
         FX.put(id, new ShaderInstance(sources, new ResourceLocation(HeraliszCamo.MODID, "fx_" + id), DefaultVertexFormat.NEW_ENTITY));
         return true;
      } catch (IOException | RuntimeException | LinkageError ex) {
         LOG.error("Shader fx_{} indisponible, l'effet 3D de ce skin ne s'affichera pas", id, ex);
         return false;
      }
   }

   /** Compile tous les programmes des skins d'un coup avec ces sources (rechargement des ressources) ; fil du rendu. */
   private static Bilan construire(ResourceProvider sources) {
      Fabrique f = new Fabrique(sources, null, null, false);
      while (f.suivant()) {
         // un programme de plus
      }
      return new Bilan(f.prets, f.echecs);
   }

   /**
    * Rechargement des ressources (démarrage du jeu, F3+T, changement de pack ou des mipmaps). 19/11 (audit des images par
    * seconde, point 11) : les programmes ne sont plus confiés à Minecraft (qui ferme à chaque rechargement tous ceux qu'on
    * lui a donnés) : gardés ici, refaits seulement si leurs sources ont changé (empreinte de la copie du serveur et des
    * includes de Minecraft). Les morceaux de programme partagés que Minecraft libère à chaque rechargement ne gênent pas : un
    * programme déjà lié reste valable.
    * <p>
    * 21/11 (étape 2) : les sources ne viennent plus que de la copie reçue du serveur. Sans copie (démarrage du jeu, menu,
    * avant la première connexion) : rien à fabriquer, une ligne au journal, aucune erreur ; les programmes d'une copie reçue
    * plus tôt dans la session restent en place. Une fabrication par tranches pas finie est terminée ici d'un coup.
    */
   @SubscribeEvent
   public static void onRegisterShaders(RegisterShadersEvent event) {
      Map<ResourceLocation, byte[]> copie = DessinsRecus.fichiers();
      if (copie == null) {
         if (enCours == null && distincts().isEmpty()) {
            LOG.info("[Camos] dessins des skins pas encore reçus : ils arrivent du serveur à la connexion (skins sans effet d'ici là)");
         } else {
            LOG.info("Shaders des camos inchangés : {} programmes gardés (copie du serveur reçue plus tôt)", distincts().size());
         }
         return;
      }
      String emp = empreinte(copie);
      if (enCours == null && emp != null && emp.equals(empreinte) && !SHADERS.isEmpty()) {
         LOG.info("Shaders des camos inchangés : {} programmes gardés, rien recompilé", distincts().size());
         return;
      }
      boolean staff = enCours != null && enCours.staff;
      enCours = null;
      fermer();
      long debut = System.nanoTime();
      Bilan b = construire(DessinsRecus.fournisseur(copie, event.getResourceProvider()));
      empreinte = emp;
      copieEnPlace = DessinsRecus.empreinteAnnoncee();
      long ms = (System.nanoTime() - debut) / 1_000_000L;
      LOG.info("[Camos] dessins des skins refaits depuis la copie du serveur (rechargement) : {} programmes prêts, {} en échec, {} ms",
         b.prets(), b.echecs(), ms);
      if (staff) resultatStaff(b.prets(), b.echecs(), ms);
   }

   /**
    * Copie complète reçue du serveur (fil du réseau, {@link DessinsRecus#morceau}) : la fabrication se décide sur le fil du
    * rendu.
    */
   static void copieRecue() {
      RenderSystem.recordRenderCall(CamoShaders::fabriquerDepuisCopie);
   }

   /**
    * Fil du rendu : lance la fabrication des programmes depuis la copie reçue, sauf si les programmes en place (ou en cours
    * de fabrication) viennent déjà de la même copie (reconnexion au même serveur) ou de sources identiques. Copie illisible :
    * rien n'est touché (journal ; skins sans effet, ou programmes d'une copie reçue plus tôt gardés).
    */
   private static void fabriquerDepuisCopie() {
      String annonce = DessinsRecus.empreinteAnnoncee();
      if (annonce == null) {
         return;
      }
      if (enCours != null ? annonce.equals(enCours.annonce) : annonce.equals(copieEnPlace) && !distincts().isEmpty()) {
         LOG.info("[Camos] dessins des skins : même copie que celle déjà en place, rien à refaire");
         return;
      }
      Map<ResourceLocation, byte[]> copie = DessinsRecus.fichiers();
      if (copie == null) {
         return;
      }
      String emp = empreinte(copie);
      if (enCours == null && emp != null && emp.equals(empreinte) && !SHADERS.isEmpty()) {
         copieEnPlace = annonce;
         LOG.info("[Camos] dessins des skins : sources identiques à celles en place, rien à refaire");
         return;
      }
      demarrer(copie, emp, annonce, false);
   }

   /**
    * Lance la fabrication par tranches depuis cette copie : les morceaux de programme que Minecraft garde en réserve sous
    * leur nom et les programmes d'avant sont d'abord fermés (tout est compilé depuis cette copie).
    */
   private static void demarrer(Map<ResourceLocation, byte[]> copie, @Nullable String emp, @Nullable String annonce, boolean staff) {
      oublierEtagesEnCache();
      fermer();
      empreinte = null;
      copieEnPlace = null;
      enCours = new Fabrique(DessinsRecus.fournisseur(copie, Minecraft.getInstance().getResourceManager()), emp, annonce, staff);
   }

   /**
    * Début de chaque image (bus de Forge, branché par PartieClient) : une tranche de la fabrication en cours, s'il y en a
    * une. Rien pendant un rechargement des ressources (écran de chargement) : il la termine lui-même.
    */
   static void image(TickEvent.RenderTickEvent e) {
      Fabrique f = enCours;
      if (f == null || e.phase != TickEvent.Phase.START || Minecraft.getInstance().getOverlay() != null) {
         return;
      }
      long t0 = System.nanoTime();
      long fin = t0 + TRANCHE_MS * 1_000_000L;
      boolean reste;
      try {
         do {
            reste = f.suivant();
         } while (reste && System.nanoTime() < fin);
      } catch (RuntimeException ex) {
         // jamais de plantage : la fabrication s'arrête là, les programmes déjà faits servent ; rien n'est noté comme en
         // place, un rechargement des ressources ou la prochaine copie reçue retentera
         LOG.error("[Camos] fabrication des dessins des skins arrêtée ({} prêts) : les autres skins restent sans effet", f.prets, ex);
         enCours = null;
         empreinte = null;
         copieEnPlace = null;
         IconesArmes.vider();
         return;
      }
      f.compilation += System.nanoTime() - t0;
      if (!reste && enCours == f) {
         terminer(f);
      }
   }

   private static void terminer(Fabrique f) {
      enCours = null;
      empreinte = f.emp;
      copieEnPlace = f.annonce;
      // icônes 3D photographiées pendant la fabrication (skin pas encore prêt) : refaites avec les nouveaux programmes
      IconesArmes.vider();
      long ms = f.compilation / 1_000_000L;
      LOG.info("[Camos] dessins des skins fabriqués depuis la copie du serveur : {} programmes prêts, {} en échec, {} ms de"
         + " compilation ({} ms en tout)", f.prets, f.echecs, ms, (System.nanoTime() - f.debut) / 1_000_000L);
      if (f.staff) resultatStaff(f.prets, f.echecs, ms);
   }

   /**
    * {@code /camos dessins} (staff ; 21/11, docs/SKINS_PROTEGES.md) : refait tous les programmes des skins (camos, finitions
    * des couteaux, effets 3D) depuis la copie reçue du serveur, par tranches sur le fil du rendu, comme à la connexion ; à la
    * fin, un bandeau donne le nombre de programmes prêts et le temps de compilation, aussi envoyés au serveur. Copie absente
    * ou illisible : rien n'est touché.
    */
   public static void refaireDepuisServeur() {
      if (!RenderSystem.isOnRenderThread()) {
         RenderSystem.recordRenderCall(CamoShaders::refaireDepuisServeur);
         return;
      }
      Map<ResourceLocation, byte[]> copie = DessinsRecus.fichiers();
      if (copie == null) {
         boolean rien = !DessinsRecus.recue();
         LOG.warn("[Camos] /camos dessins : {}, rien n'est refait", rien ? "rien reçu du serveur" : "copie du serveur illisible");
         bandeau(Component.translatable(rien ? "gui.heralisz_camo.dessins.rien" : "gui.heralisz_camo.dessins.illisible").getString(), Notifs.DANGER);
         bilan(new DessinsBilanPacket(rien ? DessinsBilanPacket.RIEN : DessinsBilanPacket.ILLISIBLE, 0, 0, 0));
         return;
      }
      LOG.info("[Camos] /camos dessins : le jeu refait ses programmes des skins depuis la copie du serveur");
      demarrer(copie, empreinte(copie), DessinsRecus.empreinteAnnoncee(), true);
   }

   /** Fin d'une fabrication demandée par le staff : résultat au serveur et bandeau. */
   private static void resultatStaff(int prets, int echecs, long ms) {
      bilan(new DessinsBilanPacket(DessinsBilanPacket.FAIT, prets, echecs, (int) Math.min(ms, Integer.MAX_VALUE)));
      bandeau(echecs == 0
            ? Component.translatable("gui.heralisz_camo.dessins.prets", String.valueOf(prets), String.valueOf(ms)).getString()
            : Component.translatable("gui.heralisz_camo.dessins.echecs", String.valueOf(prets), String.valueOf(echecs), String.valueOf(ms)).getString(),
         echecs == 0 ? Notifs.OK : Notifs.ALERTE);
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
    * (« heralisz_camo:camo », « heralisz_camo:camo_12 »…) : sinon un nouveau programme reprendrait ceux d'une copie d'avant.
    * Un programme déjà lié reste valable ; le sticker n'est pas touché.
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
