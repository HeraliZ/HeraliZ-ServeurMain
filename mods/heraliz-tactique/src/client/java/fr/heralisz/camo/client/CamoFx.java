package fr.heralisz.camo.client;

import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import fr.heralisz.camo.fx.FxContext;
import fr.heralisz.camo.fx.FxEffect;
import fr.heralisz.camo.fx.FxEffects;
import fr.heralisz.camo.fx.FxFrame;
import fr.heralisz.camo.fx.FxMesh;
import fr.heralisz.camo.fx.Surfaces;
import fr.heralisz.camo.fx.V;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Effets 3D des camos et des skins X (moteur : paquet fr.heralisz.camo.fx, sans Minecraft, aussi exécuté par l'aperçu).
 * Pendant le rendu d'une arme (BedrockModelFxMixin), les sommets envoyés par TaCZ sont copiés au passage : ils sont déjà
 * dans leur pose animée. On retrouve pour chaque face sa position au repos (carte de positions), on en déduit le repère
 * de l'arme, et l'effet construit sa géométrie, dessinée juste après l'arme avec le shader fx_&lt;id&gt;.
 * <p>Performances : rien côté serveur (le skin n'est qu'une étiquette NBT). Côté client, l'arme en main a toujours son effet ;
 * les autres (joueurs, armes au sol) seulement à moins de 24 blocs, 8 au plus et 1,5 ms au plus par image, en qualité réduite
 * avec la distance. Ce qui ne dépend que du modèle (position au repos de chaque face, silhouette) est gardé en cache, les
 * tableaux de travail sont réutilisés, et un effet invisible (arme en visée, juste après un tir) n'est pas calculé du tout.
 */
@Mod.EventBusSubscriber(modid = fr.heralisz.camo.HeraliszCamo.MODID, value = Dist.CLIENT)
public final class CamoFx {
   private static final float MAX_DIST = 24;
   /** Opacité des effets vus par le porteur de l'arme (au repos ; nulle en visée et au tir). */
   private static final float FP_FADE = 0.18F;
   /** Mélange « par défaut » des shaders Minecraft (voir emit). */
   static final BlendMode DEFAULT_BLEND = new BlendMode();
   private static final int MAX_WORLD_FX = 8;
   /** Temps au plus consacré par image aux effets des armes « dans le monde » (au-delà, les suivantes n'ont que le camo). */
   private static final long WORLD_FX_NANOS = 1_500_000L;
   private static int worldFxThisFrame;
   private static long worldFxNanos;
   /** Qualité des effets vus par le porteur (1re personne, arme en main) : ce n'est qu'un soupçon à 18 % d'opacité. */
   private static final float HELD_QUALITY = 0.6F;
   private static final Surfaces SURF = new Surfaces();
   private static float[] view = new float[0], uvs = new float[0], recNs = new float[0];
   private static final Logger LOG = LogUtils.getLogger();
   private static final Capture SKIP = new Capture(null, null, 0, new float[0]);
   private static final Deque<Capture> STACK = new ArrayDeque<>();
   /**
    * Tableaux de copie des sommets réutilisés d'une arme à l'autre : avant, chaque arme à effet dessinée (la sienne et celles
    * des joueurs autour) en créait un neuf de 128 Ko et plus à chaque image (ramasse-miettes chargé, saccades).
    */
   private static final ArrayDeque<float[]> TABLEAUX = new ArrayDeque<>();
   /** Au-delà (2 Mo), un tableau agrandi par un très gros modèle n'est pas gardé. */
   private static final int TABLEAU_MAX = 8 * 65536;
   /** Distance² à la caméra du modèle en cours (repère de rendu = caméra à l'origine), posée par context. */
   private static float pendingDist2;
   /** Marge (blocs) sur MAX_DIST pour le tri avant copie : le point d'origine du modèle n'est pas son premier sommet. */
   private static final float MARGE_TRI = 4;
   private static final FxMesh MESH = new FxMesh();
   private static ItemDisplayContext pendingCtx = ItemDisplayContext.NONE;
   private static int pendingLight = 0xF000F0;
   private static boolean warned;
   private static int debugCount;
   /** Uniformes du shader d'effet pour le lot en cours (lus par FxRenderTypes). */
   static float fxTime, fxFade = 1, fxGui, fxLight = 1, fxFp, fxScale = 1;
   /** Apparition de l'arme en cours de rendu (posé par BedrockGunModelMixin, lu par le rendu camo et la capture) : 0..1, -1 = aucune. */
   public static float pendingReveal = -1;
   /** Chaleur (arme du joueur local, ShotTracker) et kills (NBT) de l'arme en cours de rendu : lus par le rendu camo et les effets. */
   public static float pendingHeat;
   public static int pendingKills;
   /** Derniers tirs de l'arme en cours de rendu (ShotTracker) : âges et numéro du dernier. */
   public static float[] pendingAges;
   public static int pendingSeq;
   /** L'arme en cours de rendu est-elle celle du joueur local (1re ou 3e personne) ? Posé par BedrockGunModelMixin. */
   public static boolean pendingLocal;
   /** Repère et silhouette de l'arme tenue en 1re personne à l'image précédente (main gauche de l'apparition). */
   public static volatile FxFrame lastFpFrame;
   public static volatile fr.heralisz.camo.fx.GunShape lastFpShape;

   private CamoFx() {
   }

   /** Nouvelle image : le budget d'effets « dans le monde » repart à zéro. */
   @SubscribeEvent
   public static void onRenderTick(TickEvent.RenderTickEvent event) {
      if (event.phase == TickEvent.Phase.START) {
         worldFxThisFrame = 0;
         worldFxNanos = 0;
      }
   }

   /** Banc de mesure uniquement : remet à zéro le budget d'effets de l'image (sinon seuls 8 rendus par image ont leur effet). */
   public static void resetBudget() {
      worldFxThisFrame = 0;
      worldFxNanos = 0;
   }

   /** Tête du rendu d'un modèle : contexte d'affichage, lumière et distance à la caméra (avant begin). */
   public static void context(ItemDisplayContext ctx, int light, org.joml.Matrix4f pose) {
      pendingCtx = ctx;
      pendingLight = light;
      pendingDist2 = pose.m30() * pose.m30() + pose.m31() * pose.m31() + pose.m32() * pose.m32();
   }

   /** Début du rendu d'un modèle : si c'est une arme avec un camo à effet, on copie ses sommets au passage. */
   public static VertexConsumer begin(VertexConsumer buffer, RenderType type) {
      if (STACK.size() > 16) {
         STACK.clear();
      }
      CamoRenderType.Info info = CamoRenderType.info(type);
      PosMap map = info == null || !FxEffects.has(info.shaderId()) || CamoShaders.fx(info.shaderId()) == null ? null : PosMap.byGlId(info.posMap());
      if (map == null || horsBudget()) {
         STACK.push(SKIP);
         return buffer;
      }
      Capture c = new Capture(buffer, map, info.shaderId(), tableau());
      c.ctx = pendingCtx;
      c.light = pendingLight;
      c.reveal = pendingReveal;
      c.local = pendingLocal;
      c.heat = pendingHeat;
      c.kills = pendingKills;
      c.ages = pendingAges;
      c.seq = pendingSeq;
      STACK.push(c);
      return c;
   }

   /**
    * Arme « dans le monde » (autre joueur, arme au sol) que draw refuserait de toute façon : trop loin, ou budget de l'image
    * déjà pris. On ne copie pas ses sommets (avant : copie complète de chaque arme à effet visible, même à 100 blocs).
    */
   private static boolean horsBudget() {
      boolean fp = pendingCtx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || pendingCtx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
      if (fp || pendingCtx == ItemDisplayContext.GUI) {
         return false;
      }
      float loin = MAX_DIST + MARGE_TRI;
      return pendingDist2 > loin * loin || worldFxThisFrame >= MAX_WORLD_FX || worldFxNanos >= WORLD_FX_NANOS;
   }

   private static float[] tableau() {
      float[] t = TABLEAUX.poll();
      return t != null ? t : new float[8 * 4096];
   }

   private static void rendre(Capture c) {
      float[] t = c.data;
      c.data = null;
      if (t != null && t.length > 0 && t.length <= TABLEAU_MAX && TABLEAUX.size() < 4) {
         TABLEAUX.push(t);
      }
   }

   /** L'arme est finie (le reste du rendu TaCZ — accessoires, bras — n'est plus copié). */
   public static void freeze() {
      Capture c = STACK.peek();
      if (c != null) {
         c.frozen = true;
      }
   }

   /** Fin du rendu du modèle : construction et dessin de l'effet. */
   public static void end() {
      Capture c = STACK.poll();
      if (c == null || c == SKIP) {
         return;
      }
      try {
         if (c.count >= 32) {
            draw(c);
         }
      } catch (RuntimeException e) {
         if (!warned) {
            warned = true;
            LOG.warn("Effet 3D du camo {} impossible", c.shaderId, e);
         }
      } finally {
         rendre(c);
      }
   }

   /** Banc de mesure : temps cumulés (ns) par étape du pipeline et nombre de passages. */
   public static final long[] PROF = new long[9];

   public static String profile() {
      long n = Math.max(1, PROF[7]);
      String r = String.format(java.util.Locale.ROOT,
         "copie+repos %.0f | surfaces %.0f | calage %.0f | silhouette %.0f | effet %.0f | dessin %.0f µs (dont envoi %.0f) | %d sommets (x%d)",
         PROF[0] / 1000.0 / n, PROF[1] / 1000.0 / n, PROF[2] / 1000.0 / n, PROF[3] / 1000.0 / n, PROF[4] / 1000.0 / n, PROF[5] / 1000.0 / n,
         PROF[8] / 1000.0 / n, PROF[6] / n, n);
      java.util.Arrays.fill(PROF, 0);
      return r;
   }

   private static void draw(Capture c) {
      long p0 = System.nanoTime();
      FxEffect effect = FxEffects.get(c.shaderId);
      if (effect == null) return;
      boolean fp = c.ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || c.ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
      boolean gui = c.ctx == ItemDisplayContext.GUI;
      float[] d = c.data;
      float dist = 0;
      boolean world = !fp && !gui;
      if (world) {
         // armes des autres / au sol : distance à la caméra (repère de rendu = caméra à l'origine), budget par image
         dist = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
         if (dist > MAX_DIST || worldFxThisFrame >= MAX_WORLD_FX || worldFxNanos >= WORLD_FX_NANOS) return;
         worldFxThisFrame++;
      }
      Minecraft mc = Minecraft.getInstance();
      // Arme tenue en 1re personne : l'effet n'est qu'un soupçon (le porteur voit presque l'arme de base), il disparaît en
      // visée et au tir, et le shader dégage le centre de l'écran et ce qui passe devant l'œil. Partout ailleurs (3e personne,
      // autres joueurs, inventaire, écran des accessoires, inspection) : pleine intensité.
      // skins fondus dans l'arme : leurs pièces font partie de l'arme, dessinées hors de la ligne de visée → pleinement visibles
      // écran des accessoires (on y regarde son skin) et inspection d'un skin : rendu complet, comme vu de l'extérieur
      boolean revealing = fp && c.reveal >= 0;
      boolean showcase = fp && (refitOpen(mc) || SkinInspect.showcase() || revealing);
      boolean held = fp && !showcase;
      boolean fusedFx = effect.fused();
      float fade = 1;
      if (held && !fusedFx && !effect.fullInHand()) {
         fade = FP_FADE * effect.heldFade() * heldVisibility(mc);
      }
      // invisible (visée, juste après un tir) : rien à calculer
      if (fade <= 0.004F) return;
      int quads = c.count / 32;
      if (view.length < quads * 12) {
         int cap = quads + quads / 2;
         view = new float[cap * 12];
         uvs = new float[cap * 8];
         recNs = new float[cap * 3];
      }
      float[] uv = uvs, recN = recNs;
      // empreinte des coordonnées de texture dessinées : même empreinte = mêmes faces, dans le même ordre
      long h = 0xcbf29ce484222325L ^ quads ^ ((long) c.map.glId << 32);
      for (int q = 0; q < quads; q++) {
         for (int k = 0; k < 4; k++) {
            int o = q * 32 + k * 8;
            view[q * 12 + k * 3] = d[o];
            view[q * 12 + k * 3 + 1] = d[o + 1];
            view[q * 12 + k * 3 + 2] = d[o + 2];
            uv[q * 8 + k * 2] = d[o + 3];
            uv[q * 8 + k * 2 + 1] = d[o + 4];
            h = (h ^ Float.floatToRawIntBits(d[o + 3])) * 0x100000001B3L;
            h = (h ^ Float.floatToRawIntBits(d[o + 4])) * 0x100000001B3L;
         }
         recN[q * 3] = d[q * 32 + 5];
         recN[q * 3 + 1] = d[q * 32 + 6];
         recN[q * 3 + 2] = d[q * 32 + 7];
      }
      Faces faces = FACES.get(h);
      if (faces == null || faces.part.length != quads) {
         faces = Faces.of(c.map, uv, quads);
         FACES.put(h, faces);
      }
      float[] part = faces.part, rest = faces.rest;
      long p1 = System.nanoTime();
      Surfaces s = Surfaces.build(view, uv, part, rest, recN, quads, SURF);
      long p2 = System.nanoTime();
      if (s.n == 0 || s.total <= 0) return;

      float t = (Util.getMillis() % 3_600_000L) / 1000.0F;
      FxContext fc = new FxContext();
      fc.t = t;
      MESH.clear();
      fc.mesh = MESH;
      fc.surf = s;
      long p3 = System.nanoTime();
      fc.f = FxFrame.fit(s);
      long p4 = System.nanoTime();
      // silhouette : ne change qu'avec les pièces montées ou animées (chargeur retiré…) -> recalculée 4 fois par seconde
      long now = Util.getMillis();
      if (faces.shape == null || now - faces.shapeAt > 250) {
         faces.shape = drawnShape(fc.f, view, rest, part, quads, c.map.shape);
         faces.shapeAt = now;
      }
      fc.shape = faces.shape;
      long p5 = System.nanoTime();
      fc.gui = gui;
      fc.firstPerson = held;
      fc.inspect = SkinInspect.showcase() && fp ? SkinInspect.progress() : -1;
      fc.inspectTime = showcase ? SkinInspect.time() : 0;
      fc.reveal = revealing ? c.reveal : -1;
      fc.fireAge = c.local ? fireAge(mc) : 99;
      fc.shotAges = c.ages;
      fc.shotSeq = c.seq;
      fc.heat = c.heat;
      fc.kills = c.kills;
      fc.handPos = fp && SkinInspect.showcase() ? SkinInspect.handPos : null;
      fc.wristPos = fc.handPos != null ? SkinInspect.wristPos : null;
      if (fp) {
         lastFpFrame = fc.f;
         lastFpShape = fc.shape;
      }
      if (fc.gui) {
         fc.up = new V(0, -1, 0);
      } else {
         float xr = (float) Math.toRadians(mc.gameRenderer.getMainCamera().getXRot());
         fc.up = new V(0, (float) Math.cos(xr), (float) Math.sin(xr));
      }
      // effet presque effacé pour le porteur (chaînes : 4,5 % d'opacité) : qualité encore réduite
      fc.quality = fp ? (fade < 1 ? HELD_QUALITY * Math.max(0.5F, effect.heldFade()) : 1) : gui ? 0.5F : 0.65F * (1 - 0.6F * dist / MAX_DIST);
      long p6 = System.nanoTime();
      effect.build(fc);
      long p7 = System.nanoTime();

      if (fr.heralisz.camo.dev.CamoHarness.enabled() && debugCount % 120 == 0) {
         fr.heralisz.camo.fx.GunShape gs = fc.shape;
         StringBuilder sb = new StringBuilder();
         for (int i = 0; i <= 10; i++) {
            float gx = gs.xAt(i / 10F);
            sb.append(String.format(java.util.Locale.ROOT, " %.2f:[%.3f..%.3f w%.3f]", i / 10F, gs.bottom(gx), gs.top(gx), gs.halfW(gx)));
         }
         LOG.info("[fx shape] id={} ctx={} x={}..{} y={}..{} z={}..{} s={} |{}", c.shaderId, c.ctx, gs.xMin, gs.xMax, gs.yMin, gs.yMax, gs.zMin, gs.zMax, fc.f.s, sb);
      }
      if (fr.heralisz.camo.dev.CamoHarness.enabled() && (++debugCount % 120) == 1) {
         LOG.info("[fx debug] id={} ctx={} held={} showcase={} fade={} solid={} alpha={} add={}", c.shaderId, c.ctx, held, showcase, fade, MESH.verts[0],
            MESH.verts[1], MESH.verts[2]);
      }
      fxTime = t;
      fxFade = fade;
      fxGui = fc.gui ? 1 : 0;
      fxFp = held && !fusedFx ? 1 : revealing ? 2 : 0;
      fxScale = fc.f.s;
      int block = (c.light >> 4) & 0xF, sky = (c.light >> 20) & 0xF;
      fxLight = Math.max(block, sky) / 15F;
      emit(c.shaderId, held && !fusedFx);
      long p8 = System.nanoTime();
      if (world) {
         worldFxNanos += p8 - p0;
      }
      PROF[0] += p1 - p0;
      PROF[1] += p2 - p1;
      PROF[2] += p4 - p3;
      PROF[3] += p5 - p4;
      PROF[4] += p7 - p6;
      PROF[5] += p8 - p7;
      PROF[7]++;
   }

   /**
    * Ce qui ne dépend que des faces dessinées (leurs coordonnées de texture) et pas de la pose : partie et coins au repos de
    * chaque face, silhouette. Retrouvé par empreinte d'une image à l'autre et d'une arme à l'autre du même modèle.
    */
   private static final class Faces {
      final float[] part, rest;
      fr.heralisz.camo.fx.GunShape shape;
      long shapeAt;

      private Faces(int quads) {
         this.part = new float[quads];
         this.rest = new float[quads * 12];
      }

      static Faces of(PosMap map, float[] uv, int quads) {
         Faces f = new Faces(quads);
         float[] part = f.part, rest = f.rest;
         for (int q = 0; q < quads; q++) {
            float u0 = Float.MAX_VALUE, v0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
               float u = uv[q * 8 + k * 2], v = uv[q * 8 + k * 2 + 1];
               u0 = Math.min(u0, u); u1 = Math.max(u1, u);
               v0 = Math.min(v0, v); v1 = Math.max(v1, v);
            }
            float[] rec = map.restFor(u0, v0, u1, v1);
            if (rec == null) {
               part[q] = map.partAt((u0 + u1) * 0.5F, (v0 + v1) * 0.5F);
               Arrays.fill(rest, q * 12, q * 12 + 12, Float.NaN);
               continue;
            }
            part[q] = rec[20];
            // chaque coin dessiné reprend le coin au repos de même coordonnée de texture
            for (int k = 0; k < 4; k++) {
               float cu = uv[q * 8 + k * 2], cv = uv[q * 8 + k * 2 + 1];
               int best = 0;
               float bd = Float.MAX_VALUE;
               for (int j = 0; j < 4; j++) {
                  float dd = Math.abs(cu - rec[j * 5 + 3]) + Math.abs(cv - rec[j * 5 + 4]);
                  if (dd < bd) {
                     bd = dd;
                     best = j;
                  }
               }
               rest[q * 12 + k * 3] = rec[best * 5];
               rest[q * 12 + k * 3 + 1] = rec[best * 5 + 1];
               rest[q * 12 + k * 3 + 2] = rec[best * 5 + 2];
            }
         }
         return f;
      }
   }

   /** Empreintes récentes (quelques modèles d'armes à la fois à l'écran ; les plus anciennes sont oubliées). */
   private static final java.util.Map<Long, Faces> FACES = new java.util.LinkedHashMap<>(32, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(java.util.Map.Entry<Long, Faces> eldest) {
         return this.size() > 24;
      }
   };

   /** Rechargement des ressources : les cartes de positions changent. */
   public static void clearCache() {
      FACES.clear();
   }

   /**
    * Effet 3D d'un skin X sur un sac porté (BagCamo.afterRender) : même budget que les armes des autres joueurs (24 blocs,
    * 8 effets et 1,5 ms par image), qualité réduite avec la distance.
    */
   public static void drawBag(int shaderId, fr.heralisz.camo.fx.BagFrame b, int light) {
      fr.heralisz.camo.fx.BagEffects.BagEffect effect = fr.heralisz.camo.fx.BagEffects.get(shaderId);
      if (effect == null || CamoShaders.fx(shaderId) == null) return;
      float dist = b.o.len();
      if (dist > MAX_DIST || worldFxThisFrame >= MAX_WORLD_FX || worldFxNanos >= WORLD_FX_NANOS) return;
      worldFxThisFrame++;
      long p0 = System.nanoTime();
      try {
         Minecraft mc = Minecraft.getInstance();
         float t = (Util.getMillis() % 3_600_000L) / 1000.0F;
         FxContext fc = new FxContext();
         fc.t = t;
         MESH.clear();
         fc.mesh = MESH;
         float xr = (float) Math.toRadians(mc.gameRenderer.getMainCamera().getXRot());
         fc.up = new V(0, (float) Math.cos(xr), (float) Math.sin(xr));
         fc.quality = 0.8F * (1 - 0.55F * dist / MAX_DIST);
         FxFrame f = new FxFrame();
         f.o = b.o;
         f.x = b.up;
         f.y = b.out;
         f.z = b.side;
         f.s = b.scale;
         fc.f = f;
         effect.build(fc, b);
         fxTime = t;
         fxFade = 1;
         fxGui = 0;
         fxFp = 0;
         fxScale = b.scale;
         int block = (light >> 4) & 0xF, sky = (light >> 20) & 0xF;
         fxLight = Math.max(block, sky) / 15F;
         emit(shaderId, false);
      } catch (RuntimeException e) {
         if (!warned) {
            warned = true;
            LOG.warn("Effet 3D du skin {} sur un sac impossible", shaderId, e);
         }
      }
      worldFxNanos += System.nanoTime() - p0;
   }

   private static float[] shapeRest = new float[0], shapePart = new float[0];

   /**
    * Silhouette de l'arme telle qu'elle est dessinée à cette image. La carte de positions connaît toutes les pièces du modèle,
    * y compris les pièces optionnelles cachées (rails de lunette, chargeurs longs, variantes de crosse) : sur certaines armes
    * elles gonflaient l'enveloppe et les créatures faisaient de grandes arches au-dessus. On ne garde que les faces
    * réellement dessinées et bien calées sur le repère (celles dont la position au repos a été mal retrouvée sont écartées).
    */
   private static fr.heralisz.camo.fx.GunShape drawnShape(FxFrame f, float[] view, float[] rest, float[] part, int quads, fr.heralisz.camo.fx.GunShape fallback) {
      if (shapeRest.length < quads * 12) {
         shapeRest = new float[quads * 12 + 1200];
         shapePart = new float[quads + 100];
      }
      int m = 0;
      float tol = 0.04F * f.s;
      for (int q = 0; q < quads; q++) {
         // toutes les pièces dessinées comptent (frein de bouche, organes de visée : parfois hors camo)
         if (Float.isNaN(rest[q * 12])) continue;
         float rx = 0, ry = 0, rz = 0, vx = 0, vy = 0, vz = 0;
         for (int k = 0; k < 4; k++) {
            rx += rest[q * 12 + k * 3]; ry += rest[q * 12 + k * 3 + 1]; rz += rest[q * 12 + k * 3 + 2];
            vx += view[q * 12 + k * 3]; vy += view[q * 12 + k * 3 + 1]; vz += view[q * 12 + k * 3 + 2];
         }
         V pred = f.at(rx / 4, ry / 4, rz / 4);
         float dx = pred.x - vx / 4, dy = pred.y - vy / 4, dz = pred.z - vz / 4;
         if (dx * dx + dy * dy + dz * dz > tol * tol) continue;
         System.arraycopy(rest, q * 12, shapeRest, m * 12, 12);
         shapePart[m] = Math.max(part[q], 0.5F);
         m++;
      }
      return m >= 24 ? fr.heralisz.camo.fx.GunShape.build(shapeRest, shapePart, m) : fallback;
   }

   private static float fireAge(Minecraft mc) {
      if (mc.player == null) return 99;
      try {
         long shot = IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder().clientShootTimestamp;
         return shot > 0 ? (System.currentTimeMillis() - shot) / 1000F : 99;
      } catch (RuntimeException | LinkageError e) {
         return 99;
      }
   }

   private static boolean refitOpen(Minecraft mc) {
      try {
         return mc.screen instanceof GunRefitScreen || RefitTransform.getOpeningProgress() > 0.01F;
      } catch (RuntimeException | LinkageError e) {
         return false;
      }
   }

   /** 1 au repos, 0 en visée et juste après un tir (retour progressif en un peu moins d'une seconde). */
   private static float heldVisibility(Minecraft mc) {
      if (mc.player == null) return 1;
      try {
         IClientPlayerGunOperator op = IClientPlayerGunOperator.fromLocalPlayer(mc.player);
         float k = 1 - op.getClientAimingProgress(mc.getFrameTime());
         long shot = op.getDataHolder().clientShootTimestamp;
         if (shot > 0) {
            float since = (System.currentTimeMillis() - shot) / 1000F;
            k *= FxContext.smooth(0.35F, 0.95F, since);
         }
         return Math.max(0, k);
      } catch (RuntimeException | LinkageError ignored) {
         return 1;
      }
   }

   private static void emit(int id, boolean firstPerson) {
      MultiBufferSource.BufferSource bs = Minecraft.getInstance().renderBuffers().bufferSource();
      // Les shaders de surface des camos déclarent un mélange : Minecraft le retient comme « dernier appliqué », et le premier
      // shader d'effet (mélange par défaut) coupait alors le mélange de son lot. Le 1er lot (corps du serpent, du dragon)
      // sortait opaque, même en 1re personne. On repart d'un état neutre avant de dessiner.
      DEFAULT_BLEND.apply();
      for (int m = 0; m < FxMesh.MODES; m++) {
         int verts = MESH.verts[m];
         if (verts == 0) continue;
         // en 1re personne, les éléments opaques passent en transparent pour pouvoir s'effacer
         RenderType rt = FxRenderTypes.get(id, firstPerson && m == FxMesh.SOLID ? FxMesh.ALPHA : m);
         VertexConsumer vc = bs.getBuffer(rt);
         PROF[6] += verts;
         float[] b = MESH.buf[m];
         for (int i = 0; i < verts; i++) {
            int o = i * FxMesh.STRIDE;
            int light = ((int) b[o + 12] & 0xFFFF) | (((int) b[o + 13] & 0xFFFF) << 16);
            // le tampon tronque les couleurs (int)(c * 255) : on vise le milieu de chaque octet pour retrouver la valeur exacte
            vc.vertex(b[o], b[o + 1], b[o + 2], q8(b[o + 3]), q8(b[o + 4]), q8(b[o + 5]), q8(b[o + 6]), b[o + 7], b[o + 8],
               OverlayTexture.NO_OVERLAY, light, b[o + 9], b[o + 10], b[o + 11]);
         }
         long e0 = System.nanoTime();
         bs.endBatch(rt);
         PROF[8] += System.nanoTime() - e0;
      }
   }

   /** Valeurs 0..1 (bornées par FxMesh) : milieu de l'octet visé, sans Math.round (des milliers d'appels par image). */
   private static float q8(float v) {
      return ((int) (v * 255F + 0.5F) + 0.5F) * (1F / 255F);
   }

   // ------------------------------------------------------------------ copie des sommets de l'arme

   /** Transmet tout au tampon de TaCZ et garde une copie des sommets (x, y, z, u, v, nx, ny, nz). */
   static final class Capture implements VertexConsumer {
      private final VertexConsumer inner;
      final PosMap map;
      final int shaderId;
      ItemDisplayContext ctx = ItemDisplayContext.NONE;
      int light;
      float reveal = -1;
      boolean local;
      float heat;
      int kills;
      float[] ages;
      int seq;
      float[] data;
      int count;
      boolean frozen;

      Capture(@Nullable VertexConsumer inner, @Nullable PosMap map, int shaderId, float[] data) {
         this.inner = inner;
         this.map = map;
         this.shaderId = shaderId;
         this.data = data;
      }

      @Override
      public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay, int light, float nx, float ny, float nz) {
         this.inner.vertex(x, y, z, r, g, b, a, u, v, overlay, light, nx, ny, nz);
         if (this.frozen || this.data == null || this.count >= 8 * 131072) {
            return;
         }
         if (this.count + 8 > this.data.length) {
            this.data = Arrays.copyOf(this.data, this.data.length * 2);
         }
         float[] d = this.data;
         int i = this.count;
         d[i] = x; d[i + 1] = y; d[i + 2] = z; d[i + 3] = u; d[i + 4] = v; d[i + 5] = nx; d[i + 6] = ny; d[i + 7] = nz;
         this.count += 8;
      }

      @Override public VertexConsumer vertex(double x, double y, double z) { this.inner.vertex(x, y, z); return this; }
      @Override public VertexConsumer color(int r, int g, int b, int a) { this.inner.color(r, g, b, a); return this; }
      @Override public VertexConsumer uv(float u, float v) { this.inner.uv(u, v); return this; }
      @Override public VertexConsumer overlayCoords(int u, int v) { this.inner.overlayCoords(u, v); return this; }
      @Override public VertexConsumer uv2(int u, int v) { this.inner.uv2(u, v); return this; }
      @Override public VertexConsumer normal(float x, float y, float z) { this.inner.normal(x, y, z); return this; }
      @Override public void endVertex() { this.inner.endVertex(); }
      @Override public void defaultColor(int r, int g, int b, int a) { this.inner.defaultColor(r, g, b, a); }
      @Override public void unsetDefaultColor() { this.inner.unsetDefaultColor(); }
   }
}
