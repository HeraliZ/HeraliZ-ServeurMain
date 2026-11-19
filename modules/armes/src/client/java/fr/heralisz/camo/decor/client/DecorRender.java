package fr.heralisz.camo.decor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.api.TimelessAPI;
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
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Rendu de la personnalisation des armes (stickers, StatTrack), pour toute arme TaCZ, skinnée ou non.
 * <p>Pendant que TaCZ dessine l'arme (BedrockGunModelMixin : début, BedrockPartMixin : chaque pièce), on relève la matrice de
 * chaque pièce et ses cubes ; quand le modèle est fini (BedrockModelFxMixin, fin du rendu, le lot de l'arme est déjà envoyé)
 * on dessine par-dessus :
 * <ul>
 * <li>stickers : de vrais décalques, projetés sur les faces des cubes de la pièce (ils épousent les arêtes) ;</li>
 * <li>StatTrack : plaquette noire et chiffres orange lumineux (kills de l'arme, 10 000 au plus).</li>
 * </ul>
 * La dernière image en 1re personne est gardée (poses et cubes) pour viser l'arme à la souris dans le menu (DecorPick).
 * <p>
 * 19/11 (audit des images par seconde, point 9) : plus rien de créé à chaque image pour une arme stickée (avant, ≈ 600
 * objets par arme et par image : un os, une pièce et leurs deux matrices pour chaque pièce, la liste des stickers relue dans
 * l'objet) : os et pièces gardés d'une image à l'autre (une pièce par BedrockPart de TaCZ, un os par nom), seules leurs
 * matrices sont recopiées ; stickers et StatTrack lus une fois par données de l'objet ; StatTrack dessiné avec des vecteurs
 * de travail. L'image gardée pour le menu est une vraie copie (faite seulement écran ouvert).
 */
public final class DecorRender {
   public static final ResourceLocation STICKERS = new ResourceLocation("heralisz_camo", "textures/decor/stickers.png");
   public static final ResourceLocation DIGITS = new ResourceLocation("heralisz_camo", "textures/decor/stattrack_digits.png");
   public static final ResourceLocation PLATE = new ResourceLocation("heralisz_camo", "textures/decor/stattrack_plate.png");
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
   /** Pièce gardée par BedrockPart (ses matrices sont recopiées à chaque image ; cubes et faces sont ceux de la pièce). */
   private static final Map<BedrockPart, Piece> PIECE_DE = new WeakHashMap<>();
   /** Os gardés par nom (un par nom d'os des modèles : leurs matrices sont recopiées à chaque image). */
   private static final Map<String, Bone> OS_DE = new HashMap<>();
   /** Pièces déjà relevées pour l'arme en cours (vidé à chaque arme ; rien de créé tant qu'il ne grandit pas). */
   private static final java.util.IdentityHashMap<BedrockPart, Boolean> VUES = new java.util.IdentityHashMap<>();
   public static volatile Snapshot lastFp;
   /** Aperçu au menu (placement en cours) : élément fantôme dessiné en plus sur l'arme tenue. */
   @Nullable public static volatile Ghost ghost;

   /** Élément en cours de placement : type (0 sticker, 2 StatTrack), dessin, finition, pose ; skip = élément déjà posé masqué. */
   public record Ghost(int kind, String id, int finish, DecorData.Placement at, int skip) {
   }

   private DecorRender() {
   }

   // ------------------------------------------------------------------ relevé pendant le rendu de TaCZ
   public static void beginGun(Object model, ItemStack stack, ItemDisplayContext context, int packedLight) {
      boolean menu = context.firstPerson() && RefitDecorTab.open;
      active = context != ItemDisplayContext.GUI && (DecorData.any(stack) || menu);
      gunModel = model;
      gun = stack;
      ctx = context;
      light = packedLight;
      BONES.clear();
      PIECES.clear();
      STACK.clear();
      VUES.clear();
   }

   /** Juste avant que la pièce dessine ses cubes (pose de la pièce en haut de la pile). */
   public static void onPart(BedrockPart part, PoseStack ps) {
      if (!active) return;
      PoseStack.Pose last = ps.last();
      if (part.name != null) {
         STACK.push(part);
         if (!BONES.containsKey(part.name)) BONES.put(part.name, os(part.name, last));
      }
      if (part.cubes.isEmpty()) return;
      List<float[]> cubes = CUBES.computeIfAbsent(part, DecorRender::readCubes);
      if (cubes.isEmpty()) return;
      String bone = part.name != null ? part.name : STACK.isEmpty() ? ROOT : STACK.peek().name;
      if (bone.equals(ROOT) && !BONES.containsKey(ROOT)) BONES.put(ROOT, os(ROOT, last));
      Piece p = PIECE_DE.get(part);
      if (VUES.put(part, Boolean.TRUE) != null) {
         // pièce dessinée deux fois dans la même arme : une pièce à part pour la 2e fois, comme avant
         p = new Piece(bone, new Matrix4f(), new Matrix3f(), cubes, FACES_OF.computeIfAbsent(part, DecorRender::readFaces));
      } else if (p == null || !p.bone().equals(bone)) {
         p = new Piece(bone, new Matrix4f(), new Matrix3f(), cubes, FACES_OF.computeIfAbsent(part, DecorRender::readFaces));
         PIECE_DE.put(part, p);
      }
      p.pose().set(last.pose());
      p.normal().set(last.normal());
      PIECES.add(p);
   }

   /** Os gardé pour ce nom, matrices de l'image en cours. */
   private static Bone os(String nom, PoseStack.Pose last) {
      Bone b = OS_DE.get(nom);
      if (b == null) {
         b = new Bone(nom, new Matrix4f(), new Matrix3f());
         OS_DE.put(nom, b);
      }
      b.pose().set(last.pose());
      b.normal().set(last.normal());
      return b;
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
      if (!active || model != gunModel) return;
      active = false;
      STACK.clear();
      // image gardée seulement écran ouvert (seul DecorPick la lit, depuis l'écran de l'arme, et seulement fraîche) : en jeu,
      // plus de copie des os et des pièces à chaque image
      if (ctx.firstPerson() && (RefitDecorTab.open || Minecraft.getInstance().screen != null)) lastFp = new Snapshot(copieOs(), copiePieces(),
         new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix()), System.currentTimeMillis(), gunLength());
      try {
         draw();
      } finally {
         BONES.clear();
         PIECES.clear();
         gun = ItemStack.EMPTY;
      }
   }

   /** Copies des os et des pièces de l'image (gardés d'une image à l'autre : l'image du menu ne doit pas bouger avec eux). */
   private static Map<String, Bone> copieOs() {
      Map<String, Bone> out = new HashMap<>();
      for (Bone b : BONES.values()) out.put(b.name(), new Bone(b.name(), new Matrix4f(b.pose()), new Matrix3f(b.normal())));
      return out;
   }

   private static List<Piece> copiePieces() {
      List<Piece> out = new ArrayList<>(PIECES.size());
      for (Piece p : PIECES) out.add(new Piece(p.bone(), new Matrix4f(p.pose()), new Matrix3f(p.normal()), p.cubes(), p.faces()));
      return out;
   }

   // ------------------------------------------------------------------ données lues une fois par objet
   /**
    * Stickers (clés de décalque, null pour un dessin inconnu : l'index reste celui de la liste) et StatTrack d'une arme, lus
    * une fois par données de l'objet : la clé est l'objet même de ses données (le client reçoit une nouvelle pile à chaque
    * changement, le serveur seul les écrit). Quelques armes à la fois à l'écran : 8 places, la plus ancienne remplacée.
    */
   private record Lu(Object donnees, CleSticker[] cles, @Nullable DecorData.Placement statTrak) {
   }

   private static final Lu[] LUS = new Lu[8];
   private static int lusTour;
   private static final Lu RIEN = new Lu(null, new CleSticker[0], null);

   private static Lu lire(ItemStack stack) {
      Object d = DecorData.donnees(stack);
      if (d == null) return RIEN;
      for (Lu l : LUS) {
         if (l != null && l.donnees == d) return l;
      }
      List<DecorData.Sticker> stickers = DecorData.stickers(stack);
      CleSticker[] cles = new CleSticker[stickers.size()];
      for (int i = 0; i < cles.length; i++) {
         DecorData.Sticker s = stickers.get(i);
         DecorCatalog.Sticker c = DecorCatalog.Sticker.byId(s.id());
         cles[i] = c == null ? null : new CleSticker(s.at(), c.ordinal(), s.finish());
      }
      Lu l = new Lu(d, cles, DecorData.statTrak(stack));
      LUS[lusTour] = l;
      lusTour = (lusTour + 1) % LUS.length;
      return l;
   }

   // ------------------------------------------------------------------ dessin
   private static void draw() {
      MultiBufferSource.BufferSource bs = Minecraft.getInstance().renderBuffers().bufferSource();
      Ghost g = ctx.firstPerson() ? ghost : null;
      Lu lu = lire(gun);
      CleSticker[] cles = lu.cles;
      if (cles.length > 0 || g != null && g.kind == 0) {
         RenderType type = StickerRenderType.get(gunTexture());
         VertexConsumer vc = bs.getBuffer(type);
         for (int i = 0; i < cles.length; i++) {
            if (g != null && g.kind == 0 && g.skip == i) continue;
            if (cles[i] != null) sticker(vc, cles[i]);
         }
         if (g != null && g.kind == 0) {
            DecorCatalog.Sticker d = DecorCatalog.Sticker.byId(g.id);
            if (d != null) sticker(vc, new CleSticker(g.at, d.ordinal(), g.finish));
         }
         bs.endBatch(type);
      }
      DecorData.Placement st = lu.statTrak;
      if (g != null && g.kind == 2) st = g.at;
      if (st != null) statTrak(bs, st, Math.min(DecorData.MAX_SHOWN, CamoData.kills(gun)));
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
    * Audit du 02/11 (mineur 11) : la géométrie du décalque (pièces proches, faces cachées, découpe, dépliage) était refaite
    * à chaque image pour chaque arme visible. Elle est maintenant gardée par modèle d'arme et par sticker (pose, dessin,
    * finition), dans le repère de chaque pièce ; chaque image ne fait plus que vérifier que les pièces n'ont pas bougé les
    * unes par rapport aux autres (sinon, pendant une animation de culasse ou de chargeur, elle est refaite comme avant) et
    * renvoyer les sommets. Même dessin.
    */
   private static void sticker(VertexConsumer vc, CleSticker cle) {
      DecorData.Placement at = cle.at();
      Bone bone = BONES.get(at.bone());
      if (bone == null) return;
      int n = PIECES.size();
      float[] rel = relatifs(bone, n);
      Map<CleSticker, Decal> parModele = DECALS.get(gunModel);
      if (parModele == null) {
         parModele = new HashMap<>();
         DECALS.put(gunModel, parModele);
      }
      Decal d = parModele.get(cle);
      if (d == null || !d.valable(rel, n)) {
         d = new Decal(rel, n, cle.finish());
         calculer(d, bone, cle.cell(), at);
         if (parModele.size() >= DECALS_PAR_MODELE) parModele.clear();
         parModele.put(cle, d);
      }
      d.emettre(vc);
   }

   /** Repère os -> pièce de chaque pièce de l'image (16 nombres chacune), dans un tableau réutilisé. */
   private static float[] relatifs(Bone bone, int n) {
      if (REL.length < n * 16) REL = new float[Math.max(n * 16, REL.length * 2)];
      for (int i = 0; i < n; i++) {
         SCRATCH.set(PIECES.get(i).pose).invert().mul(bone.pose).get(REL, i * 16);
      }
      return REL;
   }

   private static float[] REL = new float[64 * 16];
   private static final Matrix4f SCRATCH = new Matrix4f();
   /** Écart toléré entre deux images pour une pièce « qui n'a pas bougé » (bruit des calculs ; 1/1000 de bloc). */
   private static final float EPS_RELATIF = 1e-3F;
   private static final int DECALS_PAR_MODELE = 64;
   /** Géométrie gardée : modèle d'arme -> sticker -> décalque (oubliée avec le modèle). */
   private static final Map<Object, Map<CleSticker, Decal>> DECALS = new WeakHashMap<>();

   private record CleSticker(DecorData.Placement at, int cell, int finish) {
   }

   /** Décalque d'un sticker dans le repère de chaque pièce : 10 nombres par sommet (x y z, u v, recouvrement, normale). */
   private static final class Decal {
      final int nbPieces;
      final float[] relatifs;
      final Object[] pieces;
      final float rouge;
      int[] piece = new int[48];
      float[] sommets = new float[48 * 10];
      int nb;

      Decal(float[] rel, int n, int finish) {
         this.nbPieces = n;
         this.relatifs = java.util.Arrays.copyOf(rel, n * 16);
         this.pieces = new Object[n];
         for (int i = 0; i < n; i++) this.pieces[i] = PIECES.get(i).faces();
         this.rouge = finish / 255F + 0.5F / 255F;
      }

      /** Mêmes pièces (mêmes faces, même ordre) et aucune n'a bougé par rapport à l'os du sticker. */
      boolean valable(float[] rel, int n) {
         if (n != nbPieces) return false;
         for (int i = 0; i < n; i++) if (pieces[i] != PIECES.get(i).faces()) return false;
         for (int i = 0; i < n * 16; i++) if (Math.abs(rel[i] - relatifs[i]) > EPS_RELATIF) return false;
         return true;
      }

      void ajouter(int indexPiece, float x, float y, float z, float u, float v, float ou, float ov, float nx, float ny, float nz) {
         if (nb == piece.length) {
            piece = java.util.Arrays.copyOf(piece, nb * 2);
            sommets = java.util.Arrays.copyOf(sommets, nb * 2 * 10);
         }
         piece[nb] = indexPiece;
         int o = nb * 10;
         sommets[o] = x;
         sommets[o + 1] = y;
         sommets[o + 2] = z;
         sommets[o + 3] = u;
         sommets[o + 4] = v;
         sommets[o + 5] = ou;
         sommets[o + 6] = ov;
         sommets[o + 7] = nx;
         sommets[o + 8] = ny;
         sommets[o + 9] = nz;
         nb++;
      }

      void emettre(VertexConsumer vc) {
         Vector3f nn = NORMALE;
         for (int k = 0; k < nb; k++) {
            Piece p = PIECES.get(piece[k]);
            int o = k * 10;
            nn.set(sommets[o + 7], sommets[o + 8], sommets[o + 9]);
            p.normal().transform(nn);
            vc.vertex(p.pose(), sommets[o], sommets[o + 1], sommets[o + 2]).color(rouge, 0, 0, 1).uv(sommets[o + 3], sommets[o + 4])
               .overlayCoords(Math.round(sommets[o + 5]), Math.round(sommets[o + 6])).uv2(light).normal(nn.x, nn.y, nn.z).endVertex();
         }
      }
   }

   private static final Vector3f NORMALE = new Vector3f();

   /** Calcul du décalque (le dessin d'avant, mot pour mot, mais rangé dans {@code d} au lieu d'être envoyé). */
   private static void calculer(Decal d, Bone bone, int cell, DecorData.Placement at) {
      Vector3f[] bb = basis(at, bone.pose.determinant());
      float size = stickerPx(gunLength()) * at.scale();
      float u0 = (cell % COLS) / (float) COLS, v0 = (cell / COLS) / (float) ROWS;
      float du = 1F / COLS, dv = 1F / ROWS;
      Vector3f tmp = new Vector3f();
      float reach = size * 0.75F + 1F;
      // pièces proches : repères os -> pièce et pièce -> os
      List<Piece> near = new ArrayList<>();
      List<Integer> nearIndex = new ArrayList<>();
      List<Matrix4f> toNear = new ArrayList<>(), fromNear = new ArrayList<>();
      for (int pi = 0; pi < PIECES.size(); pi++) {
         Piece piece = PIECES.get(pi);
         Matrix4f toPiece = new Matrix4f().set(d.relatifs, pi * 16);
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
         nearIndex.add(pi);
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
            decal(d, nearIndex.get(i), f, P, N, V, R, size, u0, v0, du, dv);
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

   private static void decal(Decal d, int indexPiece, float[] f, Vector3f P, Vector3f N, Vector3f V, Vector3f R, float size,
                             float u0, float v0, float du, float dv) {
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
      float off = 0.03F;
      for (int i = 1; i + 1 < poly.size(); i++) {
         int[] tri = {0, i, i + 1, i + 1};
         for (int k : tri) {
            float[] q = poly.get(k);
            // normale gardée dans le repère de la pièce, tournée à l'envoi (Decal.emettre) comme avant
            d.ajouter(indexPiece, (q[2] + fnx * off) / 16F, (q[3] + fny * off) / 16F, (q[4] + fnz * off) / 16F,
               u0 + (q[0] + 0.5F) * du, v0 + (0.5F - q[1]) * dv, q[5] * 32767F, q[6] * 32767F, fnx, fny, fnz);
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
   /** Vecteurs de travail du StatTrack (rendu sur le fil du rendu seulement). */
   private static final Vector3f[] T = new Vector3f[16];
   private static final int[] CHIFFRES = new int[5];
   private static final float[][] UV_FACE = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
   private static final float[][] UV_TRANCHE = {{0, 0.02F}, {0.02F, 0.02F}, {0.02F, 0}, {0, 0}};

   static {
      for (int i = 0; i < T.length; i++) T[i] = new Vector3f();
   }

   /** Repère de basis(), dans T[0..3] (point, normale, haut, droite) : mêmes calculs, sans rien créer. */
   private static void basisT(DecorData.Placement a, float det) {
      Vector3f n = T[1].set(a.nx(), a.ny(), a.nz()).normalize();
      Vector3f u = T[2].set(a.ux(), a.uy(), a.uz());
      u.sub(T[3].set(n).mul(u.dot(n)));
      if (u.lengthSquared() < 1e-6F) {
         if (Math.abs(n.y) < 0.9F) u.set(0, -1, 0);
         else u.set(0, 0, -1);
      }
      u.normalize();
      u.rotateAxis((float) Math.toRadians(a.rot()), n.x, n.y, n.z);
      Vector3f r = T[3].set(u).cross(n);
      if (det < 0) r.negate();
      T[0].set(a.px(), a.py(), a.pz());
   }

   private static void statTrak(MultiBufferSource.BufferSource bs, DecorData.Placement at, int kills) {
      Bone part = BONES.get(at.bone());
      if (part == null) return;
      basisT(at, part.pose.determinant());
      Vector3f P = T[0], N = T[1], V = T[2], R = T[3];
      float s = at.scale();
      float w = PLATE_W * s, h = PLATE_H * s, th = 0.3F * s;
      Vector3f c = T[4].set(N).mul(th * 0.5F + 0.02F).add(P);
      // plaquette (boîte fine) texturée
      RenderType plaque = RenderType.entityCutoutNoCull(PLATE);
      VertexConsumer plate = bs.getBuffer(plaque);
      box(plate, part, c, T[5].set(R).mul(w * 0.5F), T[6].set(V).mul(h * 0.5F), T[7].set(N).mul(th * 0.5F), light);
      bs.endBatch(plaque);
      // chiffres : 5 cases, zéros de tête éteints (seulement le fantôme « 8 »)
      int[] dg = CHIFFRES;
      int v = kills;
      for (int i = 4; i >= 0; i--) {
         dg[i] = v % 10;
         v /= 10;
      }
      int lead = 0;
      while (lead < 4 && dg[lead] == 0) lead++;
      RenderType yeux = RenderType.eyes(DIGITS);
      VertexConsumer vc = bs.getBuffer(yeux);
      Vector3f front = T[8].set(N).mul(th * 0.5F + 0.03F).add(c).add(T[9].set(V).mul(0.18F * h));
      Vector3f ax = T[10].set(R).mul(0.5F * s), ay = T[11].set(V).mul(0.72F * s);
      for (int i = 0; i < 5; i++) {
         Vector3f dc = T[12].set(R).mul((i - 2) * 1.2F * s).add(front);
         digitQuad(vc, part, dc, ax, ay, 10, 0.35F);
         if (i >= lead) digitQuad(vc, part, dc, ax, ay, dg[i], 1F);
      }
      bs.endBatch(yeux);
   }

   /** Coin k (0 à 3) d'un quadrilatère centré en c : c - ax - ay, c + ax - ay, c + ax + ay, c - ax + ay. */
   private static Vector3f coin(Vector3f out, Vector3f c, Vector3f ax, Vector3f ay, int k) {
      float sx = k == 1 || k == 2 ? 1 : -1, sy = k >= 2 ? 1 : -1;
      return out.set(c.x + sx * ax.x + sy * ay.x, c.y + sx * ax.y + sy * ay.y, c.z + sx * ax.z + sy * ay.z);
   }

   private static void digitQuad(VertexConsumer vc, Bone part, Vector3f c, Vector3f ax, Vector3f ay, int cell, float alpha) {
      float u0 = cell / 11F, u1 = (cell + 1) / 11F;
      Vector3f n = T[13].set(ax).cross(ay).normalize();
      part.normal.transform(n);
      Vector3f q = T[14];
      for (int k = 0; k < 4; k++) {
         coin(q, c, ax, ay, k);
         vc.vertex(part.pose, q.x / 16F, q.y / 16F, q.z / 16F).color(alpha, alpha, alpha, 1F).uv(k == 0 || k == 3 ? u0 : u1, k < 2 ? 1 : 0)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n.x, n.y, n.z).endVertex();
      }
   }

   /** Boîte (centre, demi-axes) dans le repère d'une pièce ; UV 0..1 sur chaque face. */
   private static void box(VertexConsumer vc, Bone part, Vector3f c, Vector3f ax, Vector3f ay, Vector3f az, int lt) {
      for (int k = 0; k < 3; k++) {
         Vector3f n = k == 0 ? ax : k == 1 ? ay : az, u = k == 0 ? ay : k == 1 ? az : ax, w = k == 0 ? az : k == 1 ? ax : ay;
         for (int sg = -1; sg <= 1; sg += 2) {
            Vector3f fc = T[13].set(n).mul(sg).add(c);
            Vector3f nn = T[15].set(n).normalize().mul(sg);
            part.normal.transform(nn);
            float[][] uv = k == 2 ? UV_FACE : UV_TRANCHE;
            Vector3f q = T[14];
            for (int i = 0; i < 4; i++) {
               coin(q, fc, u, w, i);
               vc.vertex(part.pose, q.x / 16F, q.y / 16F, q.z / 16F).color(1F, 1F, 1F, 1F).uv(uv[i][0], uv[i][1]).overlayCoords(OverlayTexture.NO_OVERLAY)
                  .uv2(lt).normal(nn.x, nn.y, nn.z).endVertex();
            }
         }
      }
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
}
