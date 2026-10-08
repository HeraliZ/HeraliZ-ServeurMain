package fr.heralisz.camo.decor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockCube;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.model.bedrock.BedrockCubeBox;
import com.tacz.guns.client.model.bedrock.BedrockCubePerFace;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import fr.heralisz.camo.CamoData;
import fr.heralisz.camo.decor.DecorCatalog;
import fr.heralisz.camo.decor.DecorData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Rendu de la personnalisation des armes (stickers, pendentif, StatTrack), pour toute arme TaCZ, skinnée ou non.
 * <p>Pendant que TaCZ dessine l'arme (BedrockGunModelMixin : début, BedrockPartMixin : chaque pièce), on relève la matrice de
 * chaque pièce et ses cubes ; quand le modèle est fini (BedrockModelFxMixin, fin du rendu, le lot de l'arme est déjà envoyé)
 * on dessine par-dessus :
 * <ul>
 * <li>stickers : de vrais décalques, projetés sur les faces des cubes de la pièce (ils épousent les arêtes) ;</li>
 * <li>pendentif : l'objet de Minecraft au bout d'une chaînette, balancé par les mouvements de l'arme (pendule amorti) ;</li>
 * <li>StatTrack : plaquette noire et chiffres orange lumineux (kills de l'arme, 10 000 au plus).</li>
 * </ul>
 * La dernière image en 1re personne est gardée (poses et cubes) pour viser l'arme à la souris dans le menu (DecorPick).
 */
public final class DecorRender {
   public static final ResourceLocation STICKERS = new ResourceLocation("heralisz_camo", "textures/decor/stickers.png");
   public static final ResourceLocation DIGITS = new ResourceLocation("heralisz_camo", "textures/decor/stattrack_digits.png");
   public static final ResourceLocation PLATE = new ResourceLocation("heralisz_camo", "textures/decor/stattrack_plate.png");
   public static final ResourceLocation CHAIN = new ResourceLocation("heralisz_camo", "textures/decor/chain.png");
   /** Planche des stickers : cases de 128 px, 8 par ligne, 1024 x 512. */
   private static final int COLS = 8, ROWS = 4;
   /** Taille d'un sticker d'échelle 1 (pixels du modèle). */
   public static final float STICKER_PX = 4.2F;
   /** Plaquette StatTrack d'échelle 1 (pixels du modèle). */
   public static final float PLATE_W = 7.2F, PLATE_H = 2.5F;

   /** Os nommé du modèle (repère où sont rangés les éléments posés) : matrice au moment de son dessin (unités = blocs). */
   public record Bone(String name, Matrix4f pose, Matrix3f normal) {
   }

   /**
    * Pièce qui porte des cubes. Chez TaCZ ce sont des sous-pièces sans nom (un cube chacune, avec son pivot et sa rotation),
    * rattachées ici à leur os nommé le plus proche.
    */
   public record Piece(String bone, Matrix4f pose, Matrix3f normal, List<float[]> cubes, List<float[]> faces) {
   }

   /** Image gardée pour viser l'arme au menu ; gunLen = longueur de l'arme (pixels du modèle). */
   public record Snapshot(Map<String, Bone> bones, List<Piece> pieces, Matrix4f proj, long time, float gunLen) {
   }

   /**
    * Côté d'un sticker d'échelle 1 selon la longueur de l'arme : les armes TaCZ gardent leurs proportions réelles (un Deagle fait
    * ~13 px, un AK ~42 px), donc un sticker de taille fixe couvrirait tout un pistolet.
    */
   public static float stickerPx(float gunLen) {
      return Math.max(2.2F, Math.min(STICKER_PX, gunLen * 0.16F));
   }

   private static boolean active;
   private static Object gunModel;
   private static ItemStack gun = ItemStack.EMPTY;
   private static ItemDisplayContext ctx = ItemDisplayContext.NONE;
   private static int light;
   private static final Map<String, Bone> BONES = new HashMap<>();
   private static final List<Piece> PIECES = new ArrayList<>();
   /** Os nommés en cours de dessin (TaCZ ne renseigne pas toujours BedrockPart.parent). */
   private static final ArrayDeque<BedrockPart> STACK = new ArrayDeque<>();
   /** Cubes d'une pièce, relus une fois (pixels, repère de la pièce). */
   private static final Map<BedrockPart, List<float[]>> CUBES = new WeakHashMap<>();
   /**
    * Faces des cubes d'une pièce, relues une fois : 4 sommets (pixels, repère de la pièce), leurs UV dans la texture de l'arme
    * et la normale (23 valeurs par face). Les stickers se posent dessus.
    */
   private static final Map<BedrockPart, List<float[]>> FACES_OF = new WeakHashMap<>();
   private static final String ROOT = "#root";
   public static volatile Snapshot lastFp;
   /** Aperçu au menu (placement en cours) : élément fantôme dessiné en plus sur l'arme tenue. */
   @Nullable public static volatile Ghost ghost;

   /** Élément en cours de placement : type (0 sticker, 1 pendentif, 2 StatTrack), dessin, finition, pose ; skip = élément déjà posé masqué. */
   public record Ghost(int kind, String id, int finish, DecorData.Placement at, int skip) {
   }

   private DecorRender() {
   }

   // ------------------------------------------------------------------ relevé pendant le rendu de TaCZ
   /** > 0 pendant le dessin d'une réplique en pendentif (rendu TaCZ imbriqué : ne rien relever). */
   private static int nesting;

   public static void beginGun(Object model, ItemStack stack, ItemDisplayContext context, int packedLight) {
      if (nesting > 0) return;
      boolean menu = context.firstPerson() && RefitDecorTab.open;
      active = context != ItemDisplayContext.GUI && (DecorData.any(stack) || menu);
      gunModel = model;
      gun = stack;
      ctx = context;
      light = packedLight;
      BONES.clear();
      PIECES.clear();
      STACK.clear();
   }

   /** Juste avant que la pièce dessine ses cubes (pose de la pièce en haut de la pile). */
   public static void onPart(BedrockPart part, PoseStack ps) {
      if (!active) return;
      PoseStack.Pose last = ps.last();
      if (part.name != null) {
         STACK.push(part);
         BONES.putIfAbsent(part.name, new Bone(part.name, new Matrix4f(last.pose()), new Matrix3f(last.normal())));
      }
      if (part.cubes.isEmpty()) return;
      List<float[]> cubes = CUBES.computeIfAbsent(part, DecorRender::readCubes);
      if (cubes.isEmpty()) return;
      String bone = part.name != null ? part.name : STACK.isEmpty() ? ROOT : STACK.peek().name;
      if (bone.equals(ROOT)) BONES.putIfAbsent(ROOT, new Bone(ROOT, new Matrix4f(last.pose()), new Matrix3f(last.normal())));
      PIECES.add(new Piece(bone, new Matrix4f(last.pose()), new Matrix3f(last.normal()), cubes, FACES_OF.computeIfAbsent(part, DecorRender::readFaces)));
   }

   /** Fin du dessin d'une pièce (enfants compris). */
   public static void onPartEnd(BedrockPart part) {
      if (!STACK.isEmpty() && STACK.peek() == part) STACK.pop();
   }

   private static List<float[]> readCubes(BedrockPart part) {
      List<float[]> cubes = new ArrayList<>(part.cubes.size());
      for (BedrockCube c : part.cubes) {
         if (c instanceof BedrockCubeBox b) cubes.add(new float[] {b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ});
         else if (c instanceof BedrockCubePerFace b) cubes.add(new float[] {b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ});
      }
      return cubes;
   }

   private static List<float[]> readFaces(BedrockPart part) {
      List<float[]> out = new ArrayList<>();
      for (BedrockCube c : part.cubes) {
         com.tacz.guns.client.model.bedrock.BedrockPolygon[] polys = c instanceof BedrockCubeBox b ? ((fr.heralisz.camo.mixin.BedrockCubeBoxAccessor) b).heralisz$polygons()
            : c instanceof BedrockCubePerFace b ? ((fr.heralisz.camo.mixin.BedrockCubePerFaceAccessor) b).heralisz$polygons() : null;
         if (polys == null) continue;
         for (var poly : polys) {
            if (poly == null || poly.vertices.length != 4) continue;
            float[] f = new float[23];
            for (int i = 0; i < 4; i++) {
               var v = poly.vertices[i];
               f[i * 3] = v.pos.x();
               f[i * 3 + 1] = v.pos.y();
               f[i * 3 + 2] = v.pos.z();
               f[12 + i * 2] = v.u;
               f[13 + i * 2] = v.v;
            }
            // face vide (UV par face absentes) : sommets confondus
            float ex = Math.abs(f[0] - f[6]) + Math.abs(f[1] - f[7]) + Math.abs(f[2] - f[8]);
            if (ex < 1e-4F) continue;
            f[20] = poly.normal.x();
            f[21] = poly.normal.y();
            f[22] = poly.normal.z();
            out.add(f);
         }
      }
      return out;
   }

   public static void endModel(Object model) {
      if (nesting > 0 || !active || model != gunModel) return;
      active = false;
      STACK.clear();
      if (ctx.firstPerson()) lastFp = new Snapshot(new HashMap<>(BONES), new ArrayList<>(PIECES),
         new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix()), System.currentTimeMillis(), gunLength());
      try {
         draw();
      } finally {
         BONES.clear();
         PIECES.clear();
         gun = ItemStack.EMPTY;
      }
   }

   // ------------------------------------------------------------------ dessin
   private static void draw() {
      MultiBufferSource.BufferSource bs = Minecraft.getInstance().renderBuffers().bufferSource();
      Ghost g = ctx.firstPerson() ? ghost : null;
      List<DecorData.Sticker> stickers = DecorData.stickers(gun);
      if (!stickers.isEmpty() || g != null && g.kind == 0) {
         RenderType type = StickerRenderType.get(gunTexture());
         VertexConsumer vc = bs.getBuffer(type);
         for (int i = 0; i < stickers.size(); i++) {
            if (g != null && g.kind == 0 && g.skip == i) continue;
            DecorData.Sticker s = stickers.get(i);
            DecorCatalog.Sticker d = DecorCatalog.Sticker.byId(s.id());
            if (d != null) sticker(vc, d.ordinal(), s.finish(), s.at());
         }
         if (g != null && g.kind == 0) {
            DecorCatalog.Sticker d = DecorCatalog.Sticker.byId(g.id);
            if (d != null) sticker(vc, d.ordinal(), g.finish, g.at);
         }
         bs.endBatch(type);
      }
      DecorData.Placement st = DecorData.statTrak(gun);
      if (g != null && g.kind == 2) st = g.at;
      if (st != null) statTrak(bs, st, Math.min(DecorData.MAX_SHOWN, CamoData.kills(gun)));
      DecorData.Charm ch = DecorData.charm(gun);
      if (g != null && g.kind == 1) ch = new DecorData.Charm(g.id, g.at);
      if (ch != null) charm(bs, ch);
   }

   /** Repère local d'un élément posé : point (px), normale, haut (tourné de rot), droite. */
   private static Vector3f[] basis(DecorData.Placement a, float det) {
      Vector3f n = new Vector3f(a.nx(), a.ny(), a.nz()).normalize();
      Vector3f u = new Vector3f(a.ux(), a.uy(), a.uz());
      u.sub(new Vector3f(n).mul(u.dot(n)));
      if (u.lengthSquared() < 1e-6F) u = Math.abs(n.y) < 0.9F ? new Vector3f(0, -1, 0) : new Vector3f(0, 0, -1);
      u.normalize();
      // rotation autour de la normale
      u.rotateAxis((float) Math.toRadians(a.rot()), n.x, n.y, n.z);
      Vector3f r = new Vector3f(u).cross(n);
      if (det < 0) r.negate();
      return new Vector3f[] {new Vector3f(a.px(), a.py(), a.pz()), n, u, r};
   }

   // ------------------------------------------------------------------ stickers : décalques projetés sur les faces
   static final float[][] FACES = {
      {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

   /** Texture du modèle d'arme en cours (principal ou simplifié) : le sticker ne se pose que sur ses pixels opaques. */
   private static ResourceLocation gunTexture() {
      GunDisplayInstance d = TimelessAPI.getGunDisplay(gun).orElse(null);
      if (d == null) return WHITE;
      if (d.getGunModel() == gunModel) return d.getModelTexture();
      var lod = d.getLodModel();
      if (lod != null && lod.getLeft() == gunModel) return lod.getRight();
      return d.getModelTexture();
   }

   private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");

   /**
    * Sticker = vrai décalque qui épouse l'arme :
    * <ul>
    * <li>posé sur les faces réelles des cubes de toutes les pièces autour (culasse, carcasse, garde-main... pas seulement l'os
    * où on l'a collé) : il passe par-dessus une carcasse plus épaisse au lieu de disparaître dessous ;</li>
    * <li>faces tournées vers lui : projection à plat ; faces de côté (dessus de la culasse, rebord) : on « déplie » le sticker
    * par-dessus l'arête (la distance parcourue sur la face prolonge le dessin), il se replie au lieu de s'arrêter net ;</li>
    * <li>chaque sommet porte l'UV de la texture de l'arme dessous : le shader ne dessine pas là où l'arme est transparente
    * (cubes invisibles, découpes) — plus de sticker qui flotte dans le vide.</li>
    * </ul>
    */
   private static void sticker(VertexConsumer vc, int cell, int finish, DecorData.Placement at) {
      Bone bone = BONES.get(at.bone());
      if (bone == null) return;
      Vector3f[] bb = basis(at, bone.pose.determinant());
      float size = stickerPx(gunLength()) * at.scale();
      float u0 = (cell % COLS) / (float) COLS, v0 = (cell / COLS) / (float) ROWS;
      float du = 1F / COLS, dv = 1F / ROWS;
      Vector3f tmp = new Vector3f();
      float reach = size * 0.75F + 1F;
      // pièces proches : repères os -> pièce et pièce -> os
      List<Piece> near = new ArrayList<>();
      List<Matrix4f> toNear = new ArrayList<>(), fromNear = new ArrayList<>();
      for (Piece piece : PIECES) {
         Matrix4f toPiece = new Matrix4f(piece.pose).invert().mul(bone.pose);
         Vector3f P = toPiece.transformPosition(tmp.set(bb[0]).div(16F), new Vector3f()).mul(16F);
         boolean isNear = false;
         for (float[] c : piece.cubes) {
            float ex = Math.max(Math.max(c[0] - P.x, P.x - c[3]), 0), ey = Math.max(Math.max(c[1] - P.y, P.y - c[4]), 0),
               ez = Math.max(Math.max(c[2] - P.z, P.z - c[5]), 0);
            if (ex * ex + ey * ey + ez * ez < reach * reach) {
               isNear = true;
               break;
            }
         }
         if (!isNear) continue;
         near.add(piece);
         toNear.add(toPiece);
         fromNear.add(new Matrix4f(toPiece).invert());
      }
      for (int i = 0; i < near.size(); i++) {
         Piece piece = near.get(i);
         Matrix4f toPiece = toNear.get(i);
         Vector3f P = toPiece.transformPosition(tmp.set(bb[0]).div(16F), new Vector3f()).mul(16F);
         Vector3f N = toPiece.transformDirection(new Vector3f(bb[1])).normalize();
         Vector3f V = toPiece.transformDirection(new Vector3f(bb[2])).normalize();
         Vector3f R = toPiece.transformDirection(new Vector3f(bb[3])).normalize();
         for (float[] f : piece.faces) {
            if (covered(f, i, near, toNear, fromNear)) continue;
            decal(vc, piece, f, P, N, V, R, size, u0, v0, du, dv, finish);
         }
      }
   }

   /** Face cachée par un autre cube collé contre elle (faces internes d'un empilement) : on n'y pose rien. */
   private static boolean covered(float[] f, int self, List<Piece> near, List<Matrix4f> toNear, List<Matrix4f> fromNear) {
      float cx = (f[0] + f[3] + f[6] + f[9]) * 0.25F + f[20] * 0.12F, cy = (f[1] + f[4] + f[7] + f[10]) * 0.25F + f[21] * 0.12F,
         cz = (f[2] + f[5] + f[8] + f[11]) * 0.25F + f[22] * 0.12F;
      Vector3f inBone = fromNear.get(self).transformPosition(new Vector3f(cx, cy, cz).div(16F));
      Vector3f q = new Vector3f();
      for (int j = 0; j < near.size(); j++) {
         toNear.get(j).transformPosition(inBone, q).mul(16F);
         for (float[] c : near.get(j).cubes) {
            if (q.x > c[0] + 0.01F && q.x < c[3] - 0.01F && q.y > c[1] + 0.01F && q.y < c[4] - 0.01F && q.z > c[2] + 0.01F && q.z < c[5] - 0.01F) return true;
         }
      }
      return false;
   }

   private static void decal(VertexConsumer vc, Piece piece, float[] f, Vector3f P, Vector3f N, Vector3f V, Vector3f R, float size,
                             float u0, float v0, float du, float dv, int finish) {
      float fnx = f[20], fny = f[21], fnz = f[22];
      float facing = fnx * N.x + fny * N.y + fnz * N.z;
      if (facing <= -0.1F) return;                      // face tournée à l'opposé
      boolean wrap = facing < 0.35F;                     // face de côté : on déplie par-dessus l'arête
      float tr = 0, tv = 0;
      if (wrap) {
         float tx = fnx - N.x * facing, ty = fny - N.y * facing, tz = fnz - N.z * facing;
         float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
         if (tl < 1e-3F) return;
         tr = (tx * R.x + ty * R.y + tz * R.z) / tl;
         tv = (tx * V.x + ty * V.y + tz * V.z) / tl;
      }
      // sommets dans le repère du sticker : [r, v, x, y, z, u, v] (r, v en tailles de sticker)
      List<float[]> poly = new ArrayList<>(8);
      float minDepth = Float.MAX_VALUE;
      for (int i = 0; i < 4; i++) {
         float x = f[i * 3], y = f[i * 3 + 1], z = f[i * 3 + 2];
         float dx = x - P.x, dy = y - P.y, dz = z - P.z;
         float depth = -(dx * N.x + dy * N.y + dz * N.z);    // > 0 : derrière le plan du sticker
         minDepth = Math.min(minDepth, Math.abs(depth));
         float r = dx * R.x + dy * R.y + dz * R.z, v = dx * V.x + dy * V.y + dz * V.z;
         if (wrap) {
            r += depth * tr;
            v += depth * tv;
         }
         poly.add(new float[] {r / size, v / size, x, y, z, f[12 + i * 2], f[13 + i * 2]});
      }
      // face trop loin du plan du sticker (autre côté de la pièce, fond d'une rainure...)
      if (minDepth > (wrap ? 0.25F : 0.45F) * size + 0.6F) return;
      poly = clip(poly);
      if (poly.size() < 3) return;
      Vector3f nn = new Vector3f(fnx, fny, fnz);
      piece.normal.transform(nn);
      float off = 0.03F;
      for (int i = 1; i + 1 < poly.size(); i++) {
         int[] tri = {0, i, i + 1, i + 1};
         for (int k : tri) {
            float[] q = poly.get(k);
            vc.vertex(piece.pose, (q[2] + fnx * off) / 16F, (q[3] + fny * off) / 16F, (q[4] + fnz * off) / 16F)
               .color(finish / 255F + 0.5F / 255F, 0, 0, 1).uv(u0 + (q[0] + 0.5F) * du, v0 + (0.5F - q[1]) * dv)
               .overlayCoords(Math.round(q[5] * 32767F), Math.round(q[6] * 32767F)).uv2(light).normal(nn.x, nn.y, nn.z).endVertex();
         }
      }
   }

   /** Les 4 coins d'une face d'un cube (pixels). */
   static float[][] face(float[] c, int f) {
      float x0 = c[0], y0 = c[1], z0 = c[2], x1 = c[3], y1 = c[4], z1 = c[5];
      return switch (f) {
         case 0 -> new float[][] {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}};
         case 1 -> new float[][] {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
         case 2 -> new float[][] {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
         case 3 -> new float[][] {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
         case 4 -> new float[][] {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
         default -> new float[][] {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}};
      };
   }

   /** Découpe d'un polygone (plan du sticker) au carré [-0.5, 0.5]² (Sutherland-Hodgman). */
   private static List<float[]> clip(List<float[]> poly) {
      float[][] planes = {{1, 0, 0.5F}, {-1, 0, 0.5F}, {0, 1, 0.5F}, {0, -1, 0.5F}};
      for (float[] pl : planes) {
         List<float[]> out = new ArrayList<>(poly.size() + 2);
         for (int i = 0; i < poly.size(); i++) {
            float[] a = poly.get(i), b = poly.get((i + 1) % poly.size());
            float da = pl[2] - (a[0] * pl[0] + a[1] * pl[1]), db = pl[2] - (b[0] * pl[0] + b[1] * pl[1]);
            if (da >= 0) out.add(a);
            if (da >= 0 != db >= 0) {
               float t = da / (da - db);
               float[] m = new float[a.length];
               for (int j = 0; j < a.length; j++) m[j] = a[j] + (b[j] - a[j]) * t;
               out.add(m);
            }
         }
         poly = out;
         if (poly.isEmpty()) break;
      }
      return poly;
   }

   // ------------------------------------------------------------------ StatTrack : plaquette et chiffres
   private static void statTrak(MultiBufferSource.BufferSource bs, DecorData.Placement at, int kills) {
      Bone part = BONES.get(at.bone());
      if (part == null) return;
      Vector3f[] b = basis(at, part.pose.determinant());
      Vector3f P = b[0], N = b[1], V = b[2], R = b[3];
      float s = at.scale();
      float w = PLATE_W * s, h = PLATE_H * s, th = 0.3F * s;
      Vector3f c = new Vector3f(P).add(new Vector3f(N).mul(th * 0.5F + 0.02F));
      // plaquette (boîte fine) texturée
      VertexConsumer plate = bs.getBuffer(RenderType.entityCutoutNoCull(PLATE));
      box(plate, part, c, new Vector3f(R).mul(w * 0.5F), new Vector3f(V).mul(h * 0.5F), new Vector3f(N).mul(th * 0.5F), light);
      bs.endBatch(RenderType.entityCutoutNoCull(PLATE));
      // chiffres : 5 cases, zéros de tête éteints (seulement le fantôme « 8 »)
      int[] dg = new int[5];
      int v = kills;
      for (int i = 4; i >= 0; i--) {
         dg[i] = v % 10;
         v /= 10;
      }
      int lead = 0;
      while (lead < 4 && dg[lead] == 0) lead++;
      VertexConsumer vc = bs.getBuffer(RenderType.eyes(DIGITS));
      Vector3f front = new Vector3f(c).add(new Vector3f(N).mul(th * 0.5F + 0.03F)).add(new Vector3f(V).mul(0.18F * h));
      for (int i = 0; i < 5; i++) {
         Vector3f dc = new Vector3f(front).add(new Vector3f(R).mul((i - 2) * 1.2F * s));
         digitQuad(vc, part, dc, new Vector3f(R).mul(0.5F * s), new Vector3f(V).mul(0.72F * s), 10, 0.35F);
         if (i >= lead) digitQuad(vc, part, dc, new Vector3f(R).mul(0.5F * s), new Vector3f(V).mul(0.72F * s), dg[i], 1F);
      }
      bs.endBatch(RenderType.eyes(DIGITS));
   }

   private static void digitQuad(VertexConsumer vc, Bone part, Vector3f c, Vector3f ax, Vector3f ay, int cell, float alpha) {
      float u0 = cell / 11F, u1 = (cell + 1) / 11F;
      Vector3f[] q = {new Vector3f(c).sub(ax).sub(ay), new Vector3f(c).add(ax).sub(ay), new Vector3f(c).add(ax).add(ay), new Vector3f(c).sub(ax).add(ay)};
      float[][] uv = {{u0, 1}, {u1, 1}, {u1, 0}, {u0, 0}};
      Vector3f n = new Vector3f(ax).cross(ay).normalize();
      part.normal.transform(n);
      for (int k = 0; k < 4; k++) {
         vc.vertex(part.pose, q[k].x / 16F, q[k].y / 16F, q[k].z / 16F).color(alpha, alpha, alpha, 1F).uv(uv[k][0], uv[k][1]).overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(LightTexture.FULL_BRIGHT).normal(n.x, n.y, n.z).endVertex();
      }
   }

   /** Boîte (centre, demi-axes) dans le repère d'une pièce ; UV 0..1 sur chaque face. */
   private static void box(VertexConsumer vc, Bone part, Vector3f c, Vector3f ax, Vector3f ay, Vector3f az, int lt) {
      Vector3f[] axes = {ax, ay, az};
      for (int k = 0; k < 3; k++) {
         Vector3f n = axes[k], u = axes[(k + 1) % 3], w = axes[(k + 2) % 3];
         for (int sg = -1; sg <= 1; sg += 2) {
            Vector3f fc = new Vector3f(c).add(new Vector3f(n).mul(sg));
            Vector3f[] q = {new Vector3f(fc).sub(u).sub(w), new Vector3f(fc).add(u).sub(w), new Vector3f(fc).add(u).add(w), new Vector3f(fc).sub(u).add(w)};
            Vector3f nn = new Vector3f(n).normalize().mul(sg);
            part.normal.transform(nn);
            float[][] uv = k == 2 ? new float[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}} : new float[][] {{0, 0.02F}, {0.02F, 0.02F}, {0.02F, 0}, {0, 0}};
            for (int i = 0; i < 4; i++) {
               vc.vertex(part.pose, q[i].x / 16F, q[i].y / 16F, q[i].z / 16F).color(1F, 1F, 1F, 1F).uv(uv[i][0], uv[i][1]).overlayCoords(OverlayTexture.NO_OVERLAY)
                  .uv2(lt).normal(nn.x, nn.y, nn.z).endVertex();
            }
         }
      }
   }

   // ------------------------------------------------------------------ pendentif : chaînette et objet, pendule amorti
   /** État du balancier par arme et par vue (1re personne ou non). */
   private static final class Swing {
      final Vector3f dir = new Vector3f(0, -1, 0);
      final Vector3f vel = new Vector3f();
      final Vector3f lastAnchor = new Vector3f();
      final Vector3f lastVel = new Vector3f();
      long last;
      boolean init;
   }

   private static final Map<ItemStack, Swing[]> SWINGS = new WeakHashMap<>();

   private static void charm(MultiBufferSource.BufferSource bs, DecorData.Charm charm) {
      DecorCatalog.Charm d = DecorCatalog.Charm.byId(charm.id());
      if (d == null) return;
      Bone part = BONES.get(charm.at().bone());
      if (part == null) return;
      DecorData.Placement at = charm.at();
      // point d'accroche (anneau), dans le repère de la vue
      Vector3f local = new Vector3f(at.px() + at.nx() * 0.4F, at.py() + at.ny() * 0.4F, at.pz() + at.nz() * 0.4F).div(16F);
      Vector3f anchor = part.pose.transformPosition(local, new Vector3f());
      Swing sw = SWINGS.computeIfAbsent(gun, k -> new Swing[] {new Swing(), new Swing()})[ctx.firstPerson() ? 0 : 1];
      Vector3f down = gravity(part);
      float unit = (float) Math.cbrt(Math.abs(part.pose.determinant()));   // 1 bloc du modèle, dans la vue
      float gunPx = gunLength();
      float len = Math.max(1.4F, Math.min(2.6F, gunPx * 0.05F)) / 16F * unit;  // chaînette : ~1/20 de l'arme
      step(sw, anchor, down, len);
      Vector3f end = new Vector3f(anchor).add(new Vector3f(sw.dir).mul(len));
      // chaînette : 3 maillons
      VertexConsumer vc = bs.getBuffer(RenderType.entityCutoutNoCull(CHAIN));
      Vector3f side = new Vector3f(sw.dir).cross(0, 0, 1);
      if (side.lengthSquared() < 1e-6F) side.set(1, 0, 0);
      side.normalize().mul(len * 0.09F);
      for (int i = 0; i < 3; i++) {
         Vector3f a = new Vector3f(anchor).lerp(end, i / 3F), bb2 = new Vector3f(anchor).lerp(end, (i + 1) / 3F);
         Vector3f s2 = i % 2 == 0 ? side : new Vector3f(sw.dir).cross(side).normalize().mul(len * 0.09F);
         linkQuad(vc, a, bb2, s2);
      }
      bs.endBatch(RenderType.entityCutoutNoCull(CHAIN));
      // le pendentif : tout petit (~1/10 de l'arme qui le porte), pendu à l'anneau, qui tourne doucement sur lui-même
      float size = Math.max(2.2F, Math.min(4.2F, gunPx * 0.1F)) / 16F * unit;
      PoseStack ps = new PoseStack();
      ps.translate(end.x, end.y, end.z);
      ps.mulPose(new Quaternionf().rotationTo(0, -1, 0, sw.dir.x, sw.dir.y, sw.dir.z));
      ps.mulPose(new Quaternionf().rotationY((float) ((System.currentTimeMillis() % 9000L) / 9000.0 * Math.PI * 2)));
      if (d.kind == DecorCatalog.Charm.Kind.MOB) {
         MobCharms.renderHanging(d, ps, bs, light, size);
         return;
      }
      ItemStack replica = REPLICAS.computeIfAbsent(d, DecorRender::replica);
      GunDisplayInstance display = TimelessAPI.getGunDisplay(replica).orElse(null);
      BedrockGunModel model = display == null ? null : display.getGunModel();
      if (model == null) return;
      ModelInfo info = bounds(model);
      float[] bb = info.bb;
      float ex = bb[3] - bb[0], ey = bb[4] - bb[1], ez = bb[5] - bb[2];
      float longest = Math.max(ex, Math.max(ey, ez));
      if (longest <= 1e-4F) return;
      ps.scale(size / longest, size / longest, size / longest);
      if (d.kind == DecorCatalog.Charm.Kind.KNIFE) {
         // couteau : son grand axe à la verticale, bout du manche (le plus épais ; la lame s'affine) en haut, lame en bas
         int ax = info.axis;
         float sign = info.handleAtMax ? 1F : -1F;
         // sens du manche après le retournement de TaCZ (x et y inversés)
         Vector3f dir = new Vector3f(ax == 0 ? -sign : 0, ax == 1 ? -sign : 0, ax == 2 ? sign : 0);
         ps.mulPose(new Quaternionf().rotationTo(dir.x, dir.y, dir.z, 0, 1, 0));
         ps.scale(-1F, -1F, 1F);
         float[] c = info.knifeAttach;
         ps.translate(-c[0], -c[1], -c[2]);
      } else {
         // arme : à plat, suspendue par le milieu du dessus (y vers le bas chez TaCZ : le dessus est le y le plus petit)
         ps.scale(-1F, -1F, 1F);
         float[] c = info.gunAttach;
         ps.translate(-c[0], -c[1], -c[2]);
      }
      RenderType type = display.enablesTransparency() ? RenderType.entityTranslucent(display.getModelTexture())
         : RenderType.entityCutout(display.getModelTexture());
      nesting++;
      try {
         model.render(ps, replica, ItemDisplayContext.FIXED, type, light, OverlayTexture.NO_OVERLAY);
      } finally {
         nesting--;
      }
   }

   /** Répliques des pendentifs : une arme TaCZ (modèle, skin de caisse) et, au besoin, son camo. */
   private static final Map<DecorCatalog.Charm, ItemStack> REPLICAS = new java.util.EnumMap<>(DecorCatalog.Charm.class);

   private static ItemStack replica(DecorCatalog.Charm d) {
      ItemStack st = GunItemBuilder.create().setId(new ResourceLocation(d.ref)).forceBuild();
      if (d.variant != null) st.getOrCreateTag().putString(CamoData.KEY, d.variant);
      return st;
   }

   /**
    * Modèle d'une réplique au repos (blocs, repère du modèle), sans les os techniques de TaCZ : boîte englobante, grand axe et
    * bout le plus épais le long de ce grand axe (le manche d'un couteau ; la lame s'affine vers la pointe). Une fois par modèle.
    */
   private record ModelInfo(float[] bb, int axis, boolean handleAtMax, float[] knifeAttach, float[] gunAttach) {
   }

   private static final Map<Object, ModelInfo> BOUNDS = new WeakHashMap<>();

   private static ModelInfo bounds(BedrockGunModel model) {
      return BOUNDS.computeIfAbsent(model, k -> {
         List<float[]> boxes = new ArrayList<>();
         PoseStack ps = new PoseStack();
         for (BedrockPart p : model.getShouldRender()) walkBounds(p, ps, boxes);
         float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
         for (float[] x : boxes) {
            for (int i = 0; i < 3; i++) {
               b[i] = Math.min(b[i], x[i]);
               b[i + 3] = Math.max(b[i + 3], x[i + 3]);
            }
         }
         if (boxes.isEmpty()) return new ModelInfo(new float[6], 2, true, new float[3], new float[3]);
         int ax = 0;
         for (int i = 1; i < 3; i++) {
            if (b[i + 3] - b[i] > b[ax + 3] - b[ax]) ax = i;
         }
         // épaisseur (étendue sur les deux autres axes) des cubes présents dans le premier et le dernier cinquième
         float len = b[ax + 3] - b[ax];
         float[] lo = thickness(boxes, ax, b[ax], b[ax] + len * 0.2F), hi = thickness(boxes, ax, b[ax + 3] - len * 0.2F, b[ax + 3]);
         boolean atMax = hi[0] * hi[1] >= lo[0] * lo[1];
         float sink = len * 0.03F;
         // couteau : bout du manche le long du grand axe ; arme : dessus (y le plus petit chez TaCZ) au milieu
         return new ModelInfo(b, ax, atMax, surfaceAttach(boxes, b, ax, atMax ? 1 : -1, sink), surfaceAttach(boxes, b, 1, -1, sink));
      });
   }

   /**
    * Point d'accroche sur la surface du modèle : le plus loin possible le long de l'axe ax, du côté sign (+1 : max, -1 : min),
    * au droit du centre des deux autres axes (sinon au centre de la pièce la plus en bout), enfoncé de sink vers l'intérieur
    * pour que le bout de la chaîne entre dans la pièce.
    */
   private static float[] surfaceAttach(List<float[]> boxes, float[] bb, int ax, float sign, float sink) {
      int u = (ax + 1) % 3, v = (ax + 2) % 3;
      float cu = (bb[u] + bb[u + 3]) * 0.5F, cv = (bb[v] + bb[v + 3]) * 0.5F;
      float best = Float.NaN;
      for (float[] x : boxes) {
         if (cu < x[u] || cu > x[u + 3] || cv < x[v] || cv > x[v + 3]) continue;
         float e = sign > 0 ? x[ax + 3] : x[ax];
         if (Float.isNaN(best) || (sign > 0 ? e > best : e < best)) best = e;
      }
      if (Float.isNaN(best)) {
         float[] far = null;
         for (float[] x : boxes) {
            float e = sign > 0 ? x[ax + 3] : x[ax];
            if (far == null || (sign > 0 ? e > best : e < best)) {
               best = e;
               far = x;
            }
         }
         cu = (far[u] + far[u + 3]) * 0.5F;
         cv = (far[v] + far[v + 3]) * 0.5F;
      }
      float[] p = new float[3];
      p[u] = cu;
      p[v] = cv;
      p[ax] = best - sign * sink;
      return p;
   }

   /** Étendue des cubes qui touchent la tranche [from, to] le long de l'axe, sur les deux autres axes. */
   private static float[] thickness(List<float[]> boxes, int ax, float from, float to) {
      int u = (ax + 1) % 3, v = (ax + 2) % 3;
      float u0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v0 = Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
      for (float[] x : boxes) {
         if (x[ax + 3] < from || x[ax] > to) continue;
         u0 = Math.min(u0, x[u]);
         u1 = Math.max(u1, x[u + 3]);
         v0 = Math.min(v0, x[v]);
         v1 = Math.max(v1, x[v + 3]);
      }
      return u1 < u0 ? new float[2] : new float[] {u1 - u0, v1 - v0};
   }

   private static void walkBounds(BedrockPart p, PoseStack ps, List<float[]> boxes) {
      if (!p.visible || p.name != null && (p.name.endsWith("_pos") || p.name.equals("camera") || p.name.equals("views") || p.name.equals("positioning"))) return;
      ps.pushPose();
      p.translateAndRotateAndScale(ps);
      Matrix4f m = ps.last().pose();
      Vector3f t = new Vector3f();
      for (float[] c : CUBES.computeIfAbsent(p, DecorRender::readCubes)) {
         float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
         for (int i = 0; i < 8; i++) {
            m.transformPosition(t.set((i & 1) == 0 ? c[0] : c[3], (i & 2) == 0 ? c[1] : c[4], (i & 4) == 0 ? c[2] : c[5]).div(16F));
            b[0] = Math.min(b[0], t.x);
            b[1] = Math.min(b[1], t.y);
            b[2] = Math.min(b[2], t.z);
            b[3] = Math.max(b[3], t.x);
            b[4] = Math.max(b[4], t.y);
            b[5] = Math.max(b[5], t.z);
         }
         boxes.add(b);
      }
      for (BedrockPart c : p.children) walkBounds(c, ps, boxes);
      ps.popPose();
   }

   /**
    * Longueur de l'arme (pixels du modèle), mesurée une fois par modèle : plus grande étendue des cubes dans le repère de l'os
    * qui porte le plus de pièces (le corps de l'arme ; pas les os techniques de TaCZ comme « camera », qui peuvent être tournés).
    */
   private static final Map<Object, Float> GUN_LEN = new WeakHashMap<>();

   private static float gunLength() {
      if (PIECES.isEmpty()) return 40F;
      return GUN_LEN.computeIfAbsent(gunModel, k -> {
         Map<String, Integer> count = new HashMap<>();
         for (Piece p : PIECES) count.merge(p.bone, 1, Integer::sum);
         String main = count.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
         Bone bone = main == null ? null : BONES.get(main);
         if (bone == null) return 40F;
         Matrix4f inv = new Matrix4f(bone.pose).invert(), m = new Matrix4f();
         Vector3f t = new Vector3f(), lo = new Vector3f(Float.MAX_VALUE), hi = new Vector3f(-Float.MAX_VALUE);
         for (Piece p : PIECES) {
            m.set(inv).mul(p.pose);
            for (float[] c : p.cubes) {
               for (int i = 0; i < 8; i++) {
                  m.transformPosition(t.set((i & 1) == 0 ? c[0] : c[3], (i & 2) == 0 ? c[1] : c[4], (i & 4) == 0 ? c[2] : c[5]).div(16F)).mul(16F);
                  lo.min(t);
                  hi.max(t);
               }
            }
         }
         float len = Math.max(hi.x - lo.x, Math.max(hi.y - lo.y, hi.z - lo.z));
         return len > 0 && len < 400 ? len : 40F;
      });
   }

   private static void linkQuad(VertexConsumer vc, Vector3f a, Vector3f b, Vector3f side) {
      Vector3f[] q = {new Vector3f(a).sub(side), new Vector3f(a).add(side), new Vector3f(b).add(side), new Vector3f(b).sub(side)};
      float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
      Vector3f n = new Vector3f(side).cross(new Vector3f(b).sub(a)).normalize();
      for (int k = 0; k < 4; k++) {
         vc.vertex(q[k].x, q[k].y, q[k].z).color(1F, 1F, 1F, 1F).uv(uv[k][0], uv[k][1]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
            .normal(n.x, n.y, n.z).endVertex();
      }
   }

   /** Bas du monde dans le repère de la vue : en 1re personne, d'après l'inclinaison de la caméra ; sinon, bas de l'arme. */
   private static Vector3f gravity(Bone part) {
      if (ctx.firstPerson()) {
         float pitch = (float) Math.toRadians(Minecraft.getInstance().gameRenderer.getMainCamera().getXRot());
         return new Vector3f(0, -(float) Math.cos(pitch), -(float) Math.sin(pitch)).normalize();
      }
      Vector3f d = part.normal.transform(new Vector3f(0, 1, 0));
      return d.normalize();
   }

   /** Pendule : la direction de la chaîne suit la gravité, freinée, secouée par l'accélération du point d'accroche. */
   private static void step(Swing sw, Vector3f anchor, Vector3f down, float len) {
      long now = System.nanoTime();
      if (!sw.init) {
         sw.init = true;
         sw.dir.set(down);
         sw.lastAnchor.set(anchor);
         sw.last = now;
         return;
      }
      float dt = Math.min(0.05F, (now - sw.last) / 1e9F);
      sw.last = now;
      if (dt <= 0) return;
      Vector3f v = new Vector3f(anchor).sub(sw.lastAnchor).div(dt);
      Vector3f acc = new Vector3f(v).sub(sw.lastVel).div(dt);
      if (acc.length() > 400) acc.normalize().mul(400);
      sw.lastAnchor.set(anchor);
      sw.lastVel.set(v);
      // force : gravité moins l'accélération du point d'accroche (repère accéléré), freinage
      Vector3f f = new Vector3f(down).mul(9.8F).sub(new Vector3f(acc).mul(0.12F));
      Vector3f tang = new Vector3f(f).sub(new Vector3f(sw.dir).mul(f.dot(sw.dir)));
      sw.vel.add(tang.div(Math.max(len * 9, 0.02F)).mul(dt));
      sw.vel.mul((float) Math.exp(-3.2F * dt));
      sw.dir.add(new Vector3f(sw.vel).mul(dt)).normalize();
      sw.vel.sub(new Vector3f(sw.dir).mul(sw.vel.dot(sw.dir)));
   }
}
