package fr.heralisz.camo.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.model.bedrock.BedrockCubeBox;
import com.tacz.guns.client.model.bedrock.BedrockCubePerFace;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import fr.heralisz.camo.fx.GunShape;
import fr.heralisz.camo.knife.KnifeType;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nullable;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Carte de positions d'une arme : pour chaque texel de la texture de l'arme, la position 3D (au repos, normalisée
 * dans le repère de l'arme) du point du modèle qui l'utilise. Le shader y lit la position et calcule le camo :
 * la couleur ne dépend que de la position -> motif continu d'une pièce à l'autre, sur n'importe quelle arme.
 * RGB = position (x = le long de l'arme, vers le canon ; y = hauteur ; z = côté), divisée par la longueur de l'arme.
 * A = partie de l'arme : 1 corps, 0.75 chargeur, 0.5 canon (couteaux : 1 lame, 0.75 caoutchouc, 0.5 métal couleur, 0.42 métal noir,
 * 0.31 insert, 0.25 manche métal), 0 jamais camouflé (mains, balles, verres, pièces lumineuses), PLUS 2 x (id de face 1..8) :
 * les faces voisines sur la texture ont des id différents, le shader ne mélange au filtrage que les texels de la même face
 * (sinon les petites faces des modèles très détaillés, 4 texels de côté, prennent des positions fausses : points et rayures).
 * Le shader choisit selon le camo quelles parties il couvre (les skins simples laissent chargeur et canon d'origine).
 * <p>Pièces lumineuses ({@code *_illuminated}) : seules leurs faces de couleur vive ou presque blanche (ampoules, tubes, voyants)
 * gardent leur texture ; les faces ternes (boîtiers, vis, caches) prennent le camo, lues sur la vraie texture de l'arme.
 * Quand une face du corps partage ses texels avec une pièce exclue (UV réutilisées par le modèle), le corps l'emporte, sauf
 * sur les texels d'une face lumineuse vive (0.9.13 : Wonder Weapons du gunpack heraliz_wonder).
 */
public final class PosMap {
   /** Pièces jamais camouflées (le camo suit aussi leurs enfants). */
   private static final Pattern EXCLUDE = Pattern.compile(
      "^(left|right|lefthand|righthand|lefthand_pos|righthand_pos)$|^bullet|762x|shell|muzzle_flash|glass|lens", Pattern.CASE_INSENSITIVE);
   /** Pièces lumineuses : candidates à l'exclusion, leur couleur décide (voir classerLumineuses). */
   private static final Pattern ILLUM = Pattern.compile("illuminated", Pattern.CASE_INSENSITIVE);
   /** Genres de quads : normal, exclu d'office (mains, balles, verres...), pièce lumineuse candidate. */
   static final byte K_NORMAL = 0, K_EXCLU = 1, K_LUMIERE = 2;
   /** Nombre d'id de face (alpha = partie + 2 x id, id 1..ID_MAX). */
   private static final int ID_MAX = 8;
   private static final Pattern MAG = Pattern.compile("(^|_)mag|magazine|clip|drum", Pattern.CASE_INSENSITIVE);
   private static final Pattern BARREL = Pattern.compile("barrel|muzzle|suppress|silenc|canon", Pattern.CASE_INSENSITIVE);
   private static final float BODY = 1.0F, MAGAZINE = 0.75F, BARREL_PART = 0.5F, NONE = 0.0F;
   /**
    * Carcasse de l'arme (Diamond : pavé de diamants) : pièces du corps nommées comme la carcasse (upper, lower, receiver,
    * body, frame, slide…) et leurs sous-pièces, sauf les petites pièces rapportées (détente, sûreté, rail, organes de
    * visée…) ; à défaut de noms, les pièces sans rôle entre l'avant de la crosse et l'arrière du garde-main, à hauteur du
    * haut du chargeur (carcasseParDefaut). Code 0.94 : reste « corps » pour tous les autres camos (seuil 0.9 du shader).
    * Même règle dans l'aperçu des camos (outils/apercu_camos/prep.py).
    */
   static final float RECEIVER = 0.94F;
   private static final Pattern RECEIVER_NAME = Pattern.compile(
      "^(upper|lower|receiver|upper_?receiver|lower_?receiver|body|gun_?body|upper_?body|lower_?body|frame|slide|slide_?base)\\d*$",
      Pattern.CASE_INSENSITIVE);
   private static final Pattern NOT_RECEIVER = Pattern.compile(
      "grip|stock|butt|cheek|sight|scope|rail|mount|carry|handle|trigger|safety|selector|switch|hammer|bolt|charg|release|catch|latch|lever"
         + "|pin|ping|screw|button|guard|gaurd|gas|barrel|muzzle", Pattern.CASE_INSENSITIVE);
   /**
    * Petites pièces rapportées et poignée (Diamond façon Call of Duty : elles restent en or jaune, le reste du corps prend le
    * pavé de diamants) : détente, sûreté, leviers, boutons, vis, culasse, levier d'armement, tube de crosse, bagues…
    * Code 0.97 : reste « corps » pour tous les autres camos (seuil 0.9 du shader). Même règle dans l'aperçu
    * (outils/apercu_camos/prep.py).
    */
   static final float ACCENT = 0.97F;
   private static final Pattern ACCENT_NAME = Pattern.compile(
      "grip|trigger|safety|selector|switch|hammer|bolt|charg|release|catch|latch|lever|(^|_)pin|ping|screw|button|buffer|deco|pull|ring",
      Pattern.CASE_INSENSITIVE);
   /**
    * Poignées (relief d'usinage du shader : grain antidérapant ; Diamond : en or comme les petites pièces). Code 0.985 :
    * « corps » pour les autres camos. En demi-précision dans la carte, 0.94 / 0.97 / 0.985 / 1 restent distincts (voir le
    * gabarit des camos). Même règle dans l'aperçu (prep.py).
    */
   static final float GRIP = 0.985F;
   private static final Pattern GRIP_NAME = Pattern.compile("grip", Pattern.CASE_INSENSITIVE);
   /** Rôle d'une pièce (hérité des parents) pour placer le pavé quand la carcasse n'est pas nommée. */
   static final byte R_NONE = 0, R_STOCK = 1, R_FRONT = 2, R_OTHER = 3;
   private static final Pattern STOCK_NAME = Pattern.compile("stock|butt", Pattern.CASE_INSENSITIVE);
   private static final Pattern FRONT_NAME = Pattern.compile(
      "handguard|hand_guard|^hg$|foregrip|fore_grip|forend|gas|barrel|muzzle|suppress|silenc", Pattern.CASE_INSENSITIVE);
   private static final Pattern OTHER_NAME = Pattern.compile("grip|sight|scope|carry|handle|rail|mount", Pattern.CASE_INSENSITIVE);
   /**
    * Taille de la carte (bornée 256..1024, voir mapSize). Dans une face, la position varie linéairement avec les coordonnées de
    * texture et le filtrage la restitue exactement ; mais au bord d'une face, il mélange les texels de la face voisine sur la
    * texture : il faut donc que les faces couvrent plusieurs texels (FACE_TEXELS). Mémoire vidéo par carte : 256² 0,5 Mo,
    * 512² 2 Mo, 1024² 8 Mo (RGBA16F).
    */
   public static final int MIN_SIZE = 256, MAX_SIZE = 1024;
   /** Côté minimal visé (en texels) des petites faces de l'arme. */
   private static final float FACE_TEXELS = 6F;
   /**
    * Plafond de mémoire vidéo de toutes les cartes (armes, couteaux, sacs) : 256 Mo, soit 32 cartes 1024². Au-delà, les
    * nouvelles armes reprennent la taille plancher (texture déclarée) au lieu de grossir ; les cartes sont vidées au
    * rechargement des ressources.
    */
   private static final long VRAM_BUDGET = 256L << 20;
   private static final java.util.concurrent.atomic.AtomicLong VRAM = new java.util.concurrent.atomic.AtomicLong();

   private static final java.util.Map<Integer, PosMap> BY_GL = new java.util.concurrent.ConcurrentHashMap<>();

   public final int glId;
   /** Côté de la carte (texels). */
   public final int size;
   /** Copie côté processeur de la partie de chaque texel (partie x 255), pour les effets 3D (CamoFx). */
   private final byte[] parts;
   /** Coins au repos de chaque quad, retrouvés par leurs coordonnées de texture (effets 3D : calage du repère de l'arme). */
   private final java.util.Map<Long, float[]> restByUv;
   /** Silhouette de l'arme au repos (les effets s'y adaptent). */
   public final GunShape shape;

   private PosMap(int glId, int size, byte[] parts, java.util.Map<Long, float[]> restByUv, GunShape shape) {
      this.glId = glId;
      this.size = size;
      this.parts = parts;
      this.restByUv = restByUv;
      this.shape = shape;
      BY_GL.put(glId, this);
      VRAM.addAndGet(bytes(size));
   }

   /** Mémoire vidéo d'une carte de côté n (RGBA16F : 8 octets par texel). */
   static long bytes(int n) {
      return (long) n * n * 8;
   }

   /** Mémoire vidéo occupée par toutes les cartes (octets). */
   public static long vramUsed() {
      return VRAM.get();
   }

   /**
    * Quad au repos correspondant au rectangle de texture donné : 4 coins (x, y, z, u, v) puis la partie (21 flottants),
    * ou null si inconnu.
    */
   @Nullable
   public float[] restFor(float u0, float v0, float u1, float v1) {
      return this.restByUv.get(uvKey(u0, v0, u1, v1));
   }

   static long uvKey(float u0, float v0, float u1, float v1) {
      long k = Math.round(u0 * 65536);
      k = k * 1_000_003L + Math.round(v0 * 65536);
      k = k * 1_000_003L + Math.round(u1 * 65536);
      return k * 1_000_003L + Math.round(v1 * 65536);
   }

   @Nullable
   public static PosMap byGlId(int glId) {
      return BY_GL.get(glId);
   }

   /** Partie de l'arme sous la coordonnée de texture (u, v) : 1 corps, 0.75 chargeur, 0.5 canon, 0 jamais camouflé. */
   public float partAt(float u, float v) {
      int n = this.size;
      int x = Math.min(n - 1, Math.max(0, (int) (u * n))), y = Math.min(n - 1, Math.max(0, (int) (v * n)));
      return (this.parts[y * n + x] & 0xFF) / 255F;
   }

   /** Libère la carte (une seule fois, même si plusieurs modèles la partagent). */
   public void release() {
      if (BY_GL.remove(this.glId, this)) {
         VRAM.addAndGet(-bytes(this.size));
         SHARED.values().remove(this);
         RenderSystem.recordRenderCall(() -> TextureUtil.releaseTextureId(this.glId));
      }
   }

   /**
    * Un quadrilatère du modèle : 4 sommets (x, y, z, u, v), sa partie (0 = jamais camouflé), la partie qu'il aurait s'il
    * prenait le camo (pièces lumineuses ternes), son genre (K_NORMAL, K_EXCLU, K_LUMIERE) et le rôle de sa pièce (R_*).
    */
   record Quad(float[][] v, float part, float alt, byte kind, byte role) {
      Quad(float[][] v, float part, float alt, byte kind) {
         this(v, part, alt, kind, R_NONE);
      }

      Quad(float[][] v, float part) {
         this(v, part, part, K_NORMAL, R_NONE);
      }
   }

   /**
    * Cartes partagées entre modèles identiques : TaCZ charge un modèle par affichage, donc les 20 skins de couteaux (et les
    * variantes d'une même arme) ont chacun leur instance d'un même modèle. Même géométrie, mêmes UV, même rôle -> même carte.
    */
   private static final java.util.Map<Long, PosMap> SHARED = new java.util.concurrent.ConcurrentHashMap<>();

   public static PosMap bake(BedrockModel model, @Nullable KnifeType knife) {
      return bake(model, knife, null);
   }

   /** @param texture pixels de la texture de l'arme (lus une fois sur la carte graphique), ou null : pièces lumineuses exclues en entier */
   public static PosMap bake(BedrockModel model, @Nullable KnifeType knife, @Nullable TexturePixels texture) {
      List<Quad> quads = new ArrayList<>();
      List<Vector3f> muzzles = new ArrayList<>();
      PoseStack ps = new PoseStack();
      for (BedrockPart root : model.getShouldRender()) {
         walk(root, ps, BODY, BODY, K_NORMAL, R_NONE, knife, null, quads, muzzles);
      }
      if (knife != null && knife.cut > 0) {
         quads = cut(quads, knife);
      }
      if (knife == null && texture != null) {
         quads = classerLumineuses(quads, texture);
      }
      long key = shapeKey(quads, muzzles, knife);
      PosMap shared = SHARED.get(key);
      if (shared != null) {
         return shared;
      }
      // couteaux : toujours la carte pleine. Les modèles ArkaniaZ déclarent une texture 64² / 128² mais leurs faces ont des UV
      // de fractions de pixel (textures réelles 2048-4096 px) : à 256², la moitié de la surface du Butterfly tombe sur des faces
      // de moins d'un texel -> positions et rôles mélangés entre faces voisines, finitions en taches (régression de la 0.9.9).
      PosMap map = build(quads, muzzles, knife, knife != null ? MAX_SIZE : mapSize(model, quads), null, 0, 0, knife == null);
      SHARED.put(key, map);
      return map;
   }

   /** Empreinte de ce qui détermine la carte : quads (positions, UV, rôle), bouches du canon, type de couteau. */
   private static long shapeKey(List<Quad> quads, List<Vector3f> muzzles, @Nullable KnifeType knife) {
      long h = mix(0xcbf29ce484222325L, knife == null ? -1 : knife.ordinal());
      for (Quad q : quads) {
         h = mix(mix(h, Float.floatToIntBits(q.part)), q.kind);
         for (float[] vx : q.v) {
            for (float f : vx) h = mix(h, Float.floatToIntBits(f));
         }
      }
      for (Vector3f m : muzzles) {
         h = mix(mix(mix(h, Float.floatToIntBits(m.x)), Float.floatToIntBits(m.y)), Float.floatToIntBits(m.z));
      }
      return mix(h, quads.size());
   }

   private static long mix(long h, int v) {
      h = (h ^ v) * 0x100000001b3L;
      return h ^ (h >>> 29);
   }

   /**
    * Carte d'un sac (BagCamo) : quads du modèle (x, y, z, u, v), coordonnées de texture ramenées à la texture du sac.
    * mask (mw x mh, 0 = pas de camo) : le tissu seul reçoit le camo ; boucles, têtes et visages d'animaux gardent leur texture.
    */
   static PosMap bakeQuads(List<Quad> quads, int n, @Nullable byte[] mask, int mw, int mh) {
      return build(quads, List.of(), null, n, mask, mw, mh, false);
   }

   /** @param gun arme à feu (carcasse à repérer pour le Diamond) ; ni couteau ni sac */
   private static PosMap build(List<Quad> quads, List<Vector3f> muzzles, @Nullable KnifeType knife, int n, @Nullable byte[] mask, int mw, int mh,
                               boolean gun) {
      // repère de l'arme : axe le plus long = longueur, orienté vers le canon
      float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
      float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
      // couteau : repère calé sur la lame seule (le dégradé d'un Fade va de la garde à la pointe)
      float frameCls = knife == null ? -1 : KnifeType.Part.BLADE.code;
      for (Quad q : quads) {
         if (q.part == NONE || (frameCls > 0 && q.part != frameCls)) continue;
         for (float[] vx : q.v) {
            for (int k = 0; k < 3; k++) {
               min[k] = Math.min(min[k], vx[k]);
               max[k] = Math.max(max[k], vx[k]);
            }
         }
      }
      float[] c = new float[3], ext = new float[3];
      for (int k = 0; k < 3; k++) {
         c[k] = (min[k] + max[k]) / 2;
         ext[k] = Math.max(max[k] - min[k], 1e-4F);
      }
      int a = 0;
      for (int k = 1; k < 3; k++) if (ext[k] > ext[a]) a = k;
      int b = a == 1 ? 0 : 1;
      for (int k = 0; k < 3; k++) if (k != a && ext[k] > ext[b]) b = k;
      int d = 3 - a - b;
      float len = ext[a];
      float sa = 1;
      if (knife != null) {
         // pointe = du côté opposé au manche
         float g = 0, cnt = 0;
         for (Quad q : quads) {
            if (q.part == NONE || q.part == KnifeType.Part.BLADE.code) continue;
            for (float[] vx : q.v) { g += vx[a]; cnt++; }
         }
         if (cnt > 0) sa = g / cnt <= c[a] ? 1 : -1;
      } else if (!muzzles.isEmpty()) {
         float m = 0;
         for (Vector3f v : muzzles) m += v.get(a);
         sa = m / muzzles.size() >= c[a] ? 1 : -1;
      }
      float sb = b == 1 ? -1 : 1; // dans TaCZ, y descend
      if (gun) {
         quads = carcasseParDefaut(quads, a, b, c, ext, len, sa, sb);
      }

      float[] px = new float[n * n * 4];
      boolean[] filled = new boolean[n * n];
      int[] owner = new int[n * n];
      java.util.Arrays.fill(owner, -1);
      // ordre d'écriture (un texel partagé par plusieurs faces garde la dernière) : pièces exclues d'office, puis le corps,
      // puis les faces lumineuses vives ; les couteaux : pièces exclues d'abord, puis l'ordre du modèle (les repères des mains
      // de la batte, dessinés après elle, effaçaient la moitié de ses faces : skin absent par endroits)
      int[] ordre = new int[quads.size()];
      int no = 0;
      if (knife == null) {
         for (byte k : new byte[] {K_EXCLU, K_NORMAL, K_LUMIERE}) {
            for (int qi = 0; qi < quads.size(); qi++) if (quads.get(qi).kind == k) ordre[no++] = qi;
         }
      } else {
         for (int qi = 0; qi < quads.size(); qi++) if (quads.get(qi).part == NONE) ordre[no++] = qi;
         for (int qi = 0; qi < quads.size(); qi++) if (quads.get(qi).part != NONE) ordre[no++] = qi;
      }
      for (int oi = 0; oi < no; oi++) {
         int qn = ordre[oi];
         Quad q = quads.get(qn);
         float umin = Float.MAX_VALUE, umax = -Float.MAX_VALUE, vmin = Float.MAX_VALUE, vmax = -Float.MAX_VALUE;
         for (float[] vx : q.v) {
            umin = Math.min(umin, vx[3]); umax = Math.max(umax, vx[3]);
            vmin = Math.min(vmin, vx[4]); vmax = Math.max(vmax, vx[4]);
         }
         if (umax - umin < 1e-6F || vmax - vmin < 1e-6F) continue;
         // coins : (umin,vmin) (umax,vmin) (umin,vmax) (umax,vmax)
         float[][] corner = new float[4][];
         for (float[] vx : q.v) {
            int i = (Math.abs(vx[3] - umax) < Math.abs(vx[3] - umin) ? 1 : 0) + (Math.abs(vx[4] - vmax) < Math.abs(vx[4] - vmin) ? 2 : 0);
            corner[i] = vx;
         }
         if (corner[0] == null || corner[1] == null || corner[2] == null || corner[3] == null) continue;
         int x0 = (int) Math.floor(umin * n), x1 = (int) Math.ceil(umax * n);
         int y0 = (int) Math.floor(vmin * n), y1 = (int) Math.ceil(vmax * n);
         for (int y = Math.max(y0, 0); y < Math.min(y1, n); y++) {
            float t = clamp01(((y + 0.5F) / n - vmin) / (vmax - vmin));
            for (int x = Math.max(x0, 0); x < Math.min(x1, n); x++) {
               float s = clamp01(((x + 0.5F) / n - umin) / (umax - umin));
               int i = (y * n + x);
               px[i * 4] = sa * (lerp2(corner, a, s, t) - c[a]) / len;
               px[i * 4 + 1] = sb * (lerp2(corner, b, s, t) - c[b]) / len;
               px[i * 4 + 2] = (lerp2(corner, d, s, t) - c[d]) / len;
               px[i * 4 + 3] = q.part;
               filled[i] = true;
               owner[i] = qn;
            }
         }
      }
      dilate(px, filled, owner, n, 2);
      if (mask != null) {
         for (int y = 0; y < n; y++) {
            int my = Math.min(mh - 1, y * mh / n);
            for (int x = 0; x < n; x++) {
               if (mask[my * mw + Math.min(mw - 1, x * mw / n)] == 0) px[(y * n + x) * 4 + 3] = NONE;
            }
         }
      }
      byte[] parts = new byte[n * n];
      for (int i = 0; i < n * n; i++) parts[i] = (byte) Math.round(clamp01(px[i * 4 + 3]) * 255);
      // id de face (1..ID_MAX) : deux faces qui se touchent sur la carte n'ont jamais le même (coloriage glouton)
      int[] id = colorier(owner, n, quads.size());
      for (int i = 0; i < n * n; i++) {
         int o = owner[i];
         px[i * 4 + 3] = clamp01(px[i * 4 + 3]) + 2F * (o < 0 ? 1 : id[o]);
      }
      // positions au repos de chaque quad (même repère que la carte) : calage des effets 3D, silhouette de l'arme
      java.util.Map<Long, float[]> rest = new java.util.HashMap<>();
      float[] restArr = new float[quads.size() * 12];
      float[] partArr = new float[quads.size()];
      int qi = 0;
      for (Quad q : quads) {
         float[] rec = new float[21];
         float u0 = Float.MAX_VALUE, v0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
         for (int k = 0; k < 4; k++) {
            float[] vx = q.v[k];
            float rx = sa * (vx[a] - c[a]) / len, ry = sb * (vx[b] - c[b]) / len, rz = (vx[d] - c[d]) / len;
            rec[k * 5] = rx;
            rec[k * 5 + 1] = ry;
            rec[k * 5 + 2] = rz;
            rec[k * 5 + 3] = vx[3];
            rec[k * 5 + 4] = vx[4];
            restArr[qi * 12 + k * 3] = rx;
            restArr[qi * 12 + k * 3 + 1] = ry;
            restArr[qi * 12 + k * 3 + 2] = rz;
            u0 = Math.min(u0, vx[3]); u1 = Math.max(u1, vx[3]);
            v0 = Math.min(v0, vx[4]); v1 = Math.max(v1, vx[4]);
         }
         rec[20] = q.part;
         partArr[qi++] = q.part;
         rest.putIfAbsent(uvKey(u0, v0, u1, v1), rec);
      }
      return new PosMap(upload(px, n), n, parts, rest, GunShape.build(restArr, partArr, qi));
   }

   /** Composante k du point (s, t) du quad (coins 0 1 en haut, 2 3 en bas). */
   private static float lerp2(float[][] corner, int k, float s, float t) {
      float top = corner[0][k] + (corner[1][k] - corner[0][k]) * s;
      float bot = corner[2][k] + (corner[3][k] - corner[2][k]) * s;
      return top + (bot - top) * t;
   }

   /**
    * Côté de la carte d'une arme (puissance de 2, MIN_SIZE..MAX_SIZE), d'après le détail réel des UV : les petites faces
    * (quart inférieur des côtés de face, pondéré par la surface 3D) doivent couvrir FACE_TEXELS texels, sinon le filtrage
    * mélange les positions des faces voisines sur la texture (motifs fins en escaliers, taches). La taille déclarée de la
    * texture ne suffit pas : la plupart des modèles posent des UV en fractions de pixel. Modèles détaillés : 1024² (8 Mo de
    * mémoire vidéo) ; modèles LOD (grosses faces) : 256² à 512² (0,5 à 2 Mo).
    */
   static int mapSize(BedrockModel model, List<Quad> quads) {
      int n = mapSize(model);
      float[] side = new float[quads.size()], area = new float[quads.size()];
      int k = 0;
      double total = 0;
      for (Quad q : quads) {
         if (q.part == NONE) continue;
         float umin = Float.MAX_VALUE, umax = -Float.MAX_VALUE, vmin = Float.MAX_VALUE, vmax = -Float.MAX_VALUE;
         for (float[] vx : q.v) {
            umin = Math.min(umin, vx[3]); umax = Math.max(umax, vx[3]);
            vmin = Math.min(vmin, vx[4]); vmax = Math.max(vmax, vx[4]);
         }
         float[] a = q.v[0], b = q.v[1], c = q.v[2], d = q.v[3];
         // aire du quadrilatère : moitié du produit vectoriel de ses diagonales
         float px = c[0] - a[0], py = c[1] - a[1], pz = c[2] - a[2], qx = d[0] - b[0], qy = d[1] - b[1], qz = d[2] - b[2];
         float cx = py * qz - pz * qy, cy = pz * qx - px * qz, cz = px * qy - py * qx;
         float s = Math.min(umax - umin, vmax - vmin), ar = 0.5F * (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
         if (!(s > 0) || !(ar > 0)) continue;
         side[k] = s;
         area[k++] = ar;
         total += ar;
      }
      if (k == 0) return n;
      Integer[] order = new Integer[k];
      for (int i = 0; i < k; i++) order[i] = i;
      java.util.Arrays.sort(order, (i, j) -> Float.compare(side[i], side[j]));
      float small = side[order[k - 1]];
      double acc = 0;
      for (Integer i : order) {
         acc += area[i];
         if (acc >= total * 0.25) {
            small = side[i];
            break;
         }
      }
      int floor = n;
      while (n < MAX_SIZE && small * n < FACE_TEXELS) n <<= 1;
      while (n > floor && VRAM.get() + bytes(n) > VRAM_BUDGET) n >>= 1;   // plafond global : on redescend, jusqu'au plancher
      return n;
   }

   /** Côté de la carte selon la texture déclarée du modèle (puissance de 2, 2 texels par pixel, bornée) : plancher de mapSize. */
   static int mapSize(BedrockModel model) {
      net.minecraft.world.phys.Vec2 tex = model.getSize();
      if (tex == null || !(tex.x > 0) || !(tex.y > 0)) return MAX_SIZE;
      int want = (int) Math.ceil(Math.max(tex.x, tex.y) * 2);
      int n = MIN_SIZE;
      while (n < want && n < MAX_SIZE) n <<= 1;
      return n;
   }

   /**
    * Modèle d'un seul bloc : les quads de lame situés avant knife.cut (fraction de la longueur depuis le pommeau) deviennent
    * le manche (grip dessiné par la finition). Côté de la pointe donné dans le repère de l'aperçu, où x et y sont inversés par rapport à
    * TaCZ (z identique).
    */
   private static List<Quad> cut(List<Quad> quads, KnifeType knife) {
      float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
      for (Quad q : quads) {
         if (q.part == NONE) continue;
         for (float[] vx : q.v) {
            for (int k = 0; k < 3; k++) {
               lo[k] = Math.min(lo[k], vx[k]);
               hi[k] = Math.max(hi[k], vx[k]);
            }
         }
      }
      int a = 0;
      for (int k = 1; k < 3; k++) if (hi[k] - lo[k] > hi[a] - lo[a]) a = k;
      int tip = a == 2 ? knife.tip : -knife.tip;
      List<Quad> out = new ArrayList<>(quads.size());
      for (Quad q : quads) {
         if (q.part == KnifeType.Part.BLADE.code) {
            float c = 0;
            for (float[] vx : q.v) c += vx[a];
            float t = (c / q.v.length - lo[a]) / Math.max(hi[a] - lo[a], 1e-6F);
            if (tip < 0) t = 1 - t;
            if (t < knife.cut) {
               out.add(new Quad(q.v, KnifeType.Part.GRIP.code));
               continue;
            }
         }
         out.add(q);
      }
      return out;
   }

   /** Parcourt les pièces dans leur pose de repos (rotations initiales), sans dépendre de l'animation en cours. */
   private static void walk(BedrockPart part, PoseStack ps, float parentPart, float parentAlt, byte parentKind, byte parentRole,
                            @Nullable KnifeType knife, @Nullable Vector3f anchor, List<Quad> out, List<Vector3f> muzzles) {
      ps.pushPose();
      ps.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F);
      if (part.getInitRotZ() != 0) ps.mulPose(Axis.ZP.rotation(part.getInitRotZ()));
      if (part.getInitRotY() != 0) ps.mulPose(Axis.YP.rotation(part.getInitRotY()));
      if (part.getInitRotX() != 0) ps.mulPose(Axis.XP.rotation(part.getInitRotX()));
      String name = part.name == null ? "" : part.name;
      float cls = parentPart, alt = parentAlt;
      byte kind = parentKind;
      byte role = knife == null ? roleOf(parentRole, name) : R_NONE;
      KnifeType.Part kp = knife == null ? null : knife.partOf(name);
      boolean lum = ILLUM.matcher(name).find() || (knife == null && part.illuminated);
      if (cls != NONE && EXCLUDE.matcher(name).find()) {
         cls = alt = NONE;
         kind = K_EXCLU;
      } else if (cls != NONE && lum) {
         if (knife != null) {
            cls = alt = NONE;
            kind = K_EXCLU;
         } else {
            // pièce lumineuse : exclue, sauf ses faces ternes si la texture est lue (classerLumineuses) -> partie de repli
            alt = classe(cls, name);
            cls = NONE;
            kind = K_LUMIERE;
         }
      } else if (cls == NONE && kind == K_LUMIERE) {
         alt = classe(alt, name);
      } else if (knife != null) {
         if (kp != null) {
            cls = kp.code;
            Vector4f o = new Vector4f(0, 0, 0, 1).mul(ps.last().pose());   // pivot de la pièce nommée (manche…)
            anchor = new Vector3f(o.x, o.y, o.z);
         }
      } else {
         cls = classe(cls, name);
      }
      if (name.toLowerCase().contains("muzzle")) {
         Vector4f o = new Vector4f(0, 0, 0, 1).mul(ps.last().pose());
         muzzles.add(new Vector3f(o.x, o.y, o.z));
      }
      if (knife != null && knife.inlayWidth > 0 && cls == KnifeType.Part.HANDLE.code) {
         // Butterfly : les plaques fines des manches restent en métal sombre, les formes en relief au milieu forment l'insert lisse
         for (BedrockCube cube : part.cubes) {
            float[] b = bounds(cube);
            float code = cls;
            if (b != null && b[3] - b[0] >= knife.inlayWidth) {
               code = KnifeType.Part.INLAY.code;
               if (anchor != null && knife.pivotRadius > 0) {
                  Vector4f w = new Vector4f((b[0] + b[3]) / 32F, (b[1] + b[4]) / 32F, (b[2] + b[5]) / 32F, 1).mul(ps.last().pose());
                  if (new Vector3f(w.x, w.y, w.z).distance(anchor) < knife.pivotRadius / 16F) code = KnifeType.Part.PIN.code;
               }
            }
            cube.compile(ps.last(), new Recorder(out, code, code, K_NORMAL, R_NONE), 0, 0, 1, 1, 1, 1);
         }
      } else {
         part.compile(ps.last(), new Recorder(out, cls, alt, kind, role), 0, 0, 1, 1, 1, 1);
      }
      for (BedrockPart child : part.children) {
         walk(child, ps, cls, alt, kind, role, knife, anchor, out, muzzles);
      }
      ps.popPose();
   }

   /**
    * Partie d'une pièce d'après son nom quand elle descend du corps : chargeur, canon, petites pièces et poignée (ACCENT),
    * carcasse (et, sous la carcasse, les autres pièces rapportées qui redeviennent du corps).
    */
   private static float classe(float parent, String name) {
      boolean corps = parent == BODY || parent == RECEIVER;
      if (corps && MAG.matcher(name).find()) return MAGAZINE;
      if (corps && BARREL.matcher(name).find() && !name.toLowerCase().contains("muzzle_flash")) return BARREL_PART;
      if (corps && GRIP_NAME.matcher(name).find()) return GRIP;
      if (corps && ACCENT_NAME.matcher(name).find()) return ACCENT;
      if (parent == BODY && RECEIVER_NAME.matcher(name).matches()) return RECEIVER;
      if (parent == RECEIVER && !RECEIVER_NAME.matcher(name).matches() && NOT_RECEIVER.matcher(name).find()) return BODY;
      return parent;
   }

   /** Surface d'un quad (parallélogramme sur ses deux côtés au sommet 0). */
   private static double aire(float[][] v) {
      double ux = v[1][0] - v[0][0], uy = v[1][1] - v[0][1], uz = v[1][2] - v[0][2];
      double wx = v[3][0] - v[0][0], wy = v[3][1] - v[0][1], wz = v[3][2] - v[0][2];
      double cx = uy * wz - uz * wy, cy = uz * wx - ux * wz, cz = ux * wy - uy * wx;
      return Math.sqrt(cx * cx + cy * cy + cz * cz);
   }

   /** Rôle d'une pièce pour la carcasse par défaut : crosse, avant (garde-main, canon), autre (poignée, visée, rail). */
   private static byte roleOf(byte parent, String name) {
      if (STOCK_NAME.matcher(name).find()) return R_STOCK;
      if (FRONT_NAME.matcher(name).find()) return R_FRONT;
      if (OTHER_NAME.matcher(name).find()) return R_OTHER;
      return parent;
   }

   /**
    * Carcasse d'une arme à feu, après le classement par noms :
    * <ul>
    *    <li>une « carcasse » qui couvre plus de 45 % de la surface d'une arme longue (pièce « body » qui contient toute
    *    l'arme) n'en est pas une : elle redevient du corps ;</li>
    *    <li>sans carcasse nommée (ex. modèles en « boneNN ») : les pièces du corps sans rôle dont la longueur tombe surtout entre
    *    l'avant de la crosse et l'arrière du garde-main, et dont le centre est à hauteur du haut du chargeur (± 0.075 longueur
    *    d'arme), deviennent la carcasse.</li>
    * </ul>
    * Repère de la carte : x vers la bouche, y vers le haut. Même règle dans l'aperçu (outils/apercu_camos/prep.py).
    */
   static List<Quad> carcasseParDefaut(List<Quad> quads, int a, int b, float[] c, float[] ext, float len, float sa, float sb) {
      double aRec = 0, aTout = 0;
      for (Quad q : quads) {
         if (q.part == NONE) continue;
         double s = aire(q.v);
         aTout += s;
         if (q.part == RECEIVER) aRec += s;
      }
      if (aRec > 0.45 * aTout && ext[b] / ext[a] < 0.5F) {
         List<Quad> corps = new ArrayList<>(quads.size());
         for (Quad q : quads) {
            corps.add(q.part == RECEIVER || q.alt == RECEIVER
               ? new Quad(q.v, q.part == RECEIVER ? BODY : q.part, q.alt == RECEIVER ? BODY : q.alt, q.kind, q.role) : q);
         }
         quads = corps;
      }
      for (Quad q : quads) {
         if (q.part == RECEIVER) return quads;
      }
      float mx0 = Float.MAX_VALUE, mx1 = -Float.MAX_VALUE, my1 = -Float.MAX_VALUE;
      for (Quad q : quads) {
         if (q.part != MAGAZINE) continue;
         for (float[] vx : q.v) {
            float x = sa * (vx[a] - c[a]) / len, y = sb * (vx[b] - c[b]) / len;
            mx0 = Math.min(mx0, x);
            mx1 = Math.max(mx1, x);
            my1 = Math.max(my1, y);
         }
      }
      float rear, front, y0, y1;
      if (mx0 <= mx1) {
         float w = mx1 - mx0, mxc = (mx0 + mx1) / 2;
         float stock = -Float.MAX_VALUE, avant = Float.MAX_VALUE;
         for (Quad q : quads) {
            if (q.part == NONE || (q.role != R_STOCK && q.role != R_FRONT)) continue;
            float qx0 = Float.MAX_VALUE, qx1 = -Float.MAX_VALUE;
            for (float[] vx : q.v) {
               float x = sa * (vx[a] - c[a]) / len;
               qx0 = Math.min(qx0, x);
               qx1 = Math.max(qx1, x);
            }
            if (q.role == R_STOCK && qx1 < mxc) stock = Math.max(stock, qx1);
            if (q.role == R_FRONT && qx0 > mxc) avant = Math.min(avant, qx0);
         }
         rear = stock > -Float.MAX_VALUE ? stock : mx0 - 1.3F * w - 0.03F;
         front = avant < Float.MAX_VALUE ? Math.min(avant, mx1 + 0.03F + 0.5F * w) : mx1 + 0.03F + 0.25F * w;
         y0 = my1 - 0.075F;
         y1 = my1 + 0.075F;
      } else {
         rear = -0.12F;
         front = 0.08F;
         y0 = -0.05F;
         y1 = 0.08F;
      }
      List<Quad> out = new ArrayList<>(quads.size());
      for (Quad q : quads) {
         if (q.part == BODY && q.role == R_NONE) {
            float qx0 = Float.MAX_VALUE, qx1 = -Float.MAX_VALUE, cy = 0;
            for (float[] vx : q.v) {
               float x = sa * (vx[a] - c[a]) / len;
               qx0 = Math.min(qx0, x);
               qx1 = Math.max(qx1, x);
               cy += sb * (vx[b] - c[b]) / len / q.v.length;
            }
            float dedans = (Math.min(qx1, front) - Math.max(qx0, rear)) / Math.max(qx1 - qx0, 1e-6F);
            if (dedans >= 0.6F && cy >= y0 && cy <= y1) {
               out.add(new Quad(q.v, RECEIVER, q.alt == BODY ? RECEIVER : q.alt, q.kind, q.role));
               continue;
            }
         }
         out.add(q);
      }
      return out;
   }

   /**
    * Pièces lumineuses ({@code *_illuminated}, éclairées à fond par TaCZ) : chaque face est lue sur la texture de l'arme. Une
    * face vive (au moins 18 % de texels saturés et lumineux, ou presque blancs : ampoule, tube, voyant, réticule) reste
    * exclue du camo ; une face terne (boîtier, vis, cache gris ou noir) prend la partie de repli et reçoit le camo, comme le
    * reste de l'arme (sinon une Wunderwaffe améliorée garde tout le bloc gris de ses ampoules).
    */
   static List<Quad> classerLumineuses(List<Quad> quads, TexturePixels tex) {
      List<Quad> out = new ArrayList<>(quads.size());
      for (Quad q : quads) {
         if (q.kind != K_LUMIERE || q.alt == NONE) {
            out.add(q);
            continue;
         }
         float umin = Float.MAX_VALUE, umax = -Float.MAX_VALUE, vmin = Float.MAX_VALUE, vmax = -Float.MAX_VALUE;
         for (float[] vx : q.v) {
            umin = Math.min(umin, vx[3]); umax = Math.max(umax, vx[3]);
            vmin = Math.min(vmin, vx[4]); vmax = Math.max(vmax, vx[4]);
         }
         out.add(tex.vive(umin, vmin, umax, vmax) ? q : new Quad(q.v, q.alt, q.alt, K_NORMAL, q.role));
      }
      return out;
   }

   /** Boîte d'un cube en pixels du modèle : {minX, minY, minZ, maxX, maxY, maxZ}, ou null si inconnue. */
   @Nullable
   private static float[] bounds(BedrockCube cube) {
      if (cube instanceof BedrockCubePerFace c) return new float[] {c.minX, c.minY, c.minZ, c.maxX, c.maxY, c.maxZ};
      if (cube instanceof BedrockCubeBox c) return new float[] {c.minX, c.minY, c.minZ, c.maxX, c.maxY, c.maxZ};
      return null;
   }

   /**
    * Étend les bords des îlots UV de quelques texels (évite les liserés au filtrage linéaire) : chaque texel vide reprend
    * un voisin rempli tel quel (position, partie et face), le shader le traite alors comme un texel de cette face.
    */
   private static void dilate(float[] px, boolean[] filled, int[] owner, int n, int passes) {
      boolean[] next = new boolean[filled.length];
      for (int pass = 0; pass < passes; pass++) {
         System.arraycopy(filled, 0, next, 0, filled.length);
         for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
               int i = y * n + x;
               if (filled[i]) continue;
               int src = -1;
               // voisins directs d'abord, puis diagonaux
               if (x > 0 && filled[i - 1]) src = i - 1;
               else if (x < n - 1 && filled[i + 1]) src = i + 1;
               else if (y > 0 && filled[i - n]) src = i - n;
               else if (y < n - 1 && filled[i + n]) src = i + n;
               else if (x > 0 && y > 0 && filled[i - n - 1]) src = i - n - 1;
               else if (x < n - 1 && y > 0 && filled[i - n + 1]) src = i - n + 1;
               else if (x > 0 && y < n - 1 && filled[i + n - 1]) src = i + n - 1;
               else if (x < n - 1 && y < n - 1 && filled[i + n + 1]) src = i + n + 1;
               if (src >= 0) {
                  System.arraycopy(px, src * 4, px, i * 4, 4);
                  owner[i] = owner[src];
                  next[i] = true;
               }
            }
         }
         System.arraycopy(next, 0, filled, 0, filled.length);
      }
   }

   /**
    * Coloriage glouton des faces d'après leurs contacts sur la carte (texels voisins de faces différentes, diagonales
    * comprises) : id 1..ID_MAX, le plus petit qu'aucune face voisine déjà coloriée n'a pris (si tous sont pris, le moins
    * présent : rarissime). Rend l'id par indice de quad.
    */
   private static int[] colorier(int[] owner, int n, int nq) {
      java.util.List<java.util.Set<Integer>> voisins = new java.util.ArrayList<>(nq);
      for (int q = 0; q < nq; q++) voisins.add(null);
      for (int y = 0; y < n; y++) {
         for (int x = 0; x < n; x++) {
            int o = owner[y * n + x];
            if (o < 0) continue;
            if (x + 1 < n) lier(voisins, o, owner[y * n + x + 1]);
            if (y + 1 < n) {
               lier(voisins, o, owner[(y + 1) * n + x]);
               if (x + 1 < n) lier(voisins, o, owner[(y + 1) * n + x + 1]);
               if (x > 0) lier(voisins, o, owner[(y + 1) * n + x - 1]);
            }
         }
      }
      int[] id = new int[nq];
      int[] compte = new int[ID_MAX + 1];
      for (int q = 0; q < nq; q++) {
         java.util.Set<Integer> vs = voisins.get(q);
         if (vs == null) {
            id[q] = 1;
            continue;
         }
         java.util.Arrays.fill(compte, 0);
         for (int v : vs) compte[id[v]]++;
         int choix = 0;
         for (int k = 1; k <= ID_MAX; k++) {
            if (compte[k] == 0) {
               choix = k;
               break;
            }
         }
         if (choix == 0) {
            choix = 1;
            for (int k = 2; k <= ID_MAX; k++) if (compte[k] < compte[choix]) choix = k;
         }
         id[q] = choix;
      }
      return id;
   }

   private static void lier(java.util.List<java.util.Set<Integer>> voisins, int a, int b) {
      if (b < 0 || b == a) return;
      if (voisins.get(a) == null) voisins.set(a, new java.util.HashSet<>());
      if (voisins.get(b) == null) voisins.set(b, new java.util.HashSet<>());
      voisins.get(a).add(b);
      voisins.get(b).add(a);
   }

   private static int upload(float[] px, int n) {
      RenderSystem.assertOnRenderThreadOrInit();
      int id = TextureUtil.generateTextureId();
      GlStateManager._bindTexture(id);
      GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
      GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
      GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
      GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
      GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
      GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
      GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
      GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
      FloatBuffer buf = MemoryUtil.memAllocFloat(px.length);
      try {
         buf.put(px).flip();
         GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, n, n, 0, GL11.GL_RGBA, GL11.GL_FLOAT, buf);
      } finally {
         MemoryUtil.memFree(buf);
      }
      return id;
   }

   private static float clamp01(float v) {
      return v < 0 ? 0 : (v > 1 ? 1 : v);
   }

   /** Enregistre les sommets émis par TaCZ (position dans le repère du modèle + UV). */
   private static final class Recorder implements VertexConsumer {
      private final List<Quad> out;
      private final float part, alt;
      private final byte kind, role;
      private final float[][] cur = new float[4][];
      private int k;

      Recorder(List<Quad> out, float part, float alt, byte kind, byte role) {
         this.out = out;
         this.part = part;
         this.alt = alt;
         this.kind = kind;
         this.role = role;
      }

      @Override
      public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay, int light, float nx, float ny, float nz) {
         this.cur[this.k++] = new float[]{x, y, z, u, v};
         if (this.k == 4) {
            this.out.add(new Quad(this.cur.clone(), this.part, this.alt, this.kind, this.role));
            this.k = 0;
         }
      }

      // TaCZ passe toujours par vertex(...) complet ; le reste n'est pas utilisé
      @Override public VertexConsumer vertex(double x, double y, double z) { return this; }
      @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
      @Override public VertexConsumer uv(float u, float v) { return this; }
      @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
      @Override public VertexConsumer uv2(int u, int v) { return this; }
      @Override public VertexConsumer normal(float x, float y, float z) { return this; }
      @Override public void endVertex() {}
      @Override public void defaultColor(int r, int g, int b, int a) {}
      @Override public void unsetDefaultColor() {}
   }
}
