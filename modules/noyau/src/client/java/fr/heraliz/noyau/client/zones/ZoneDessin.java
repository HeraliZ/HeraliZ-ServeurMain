package fr.heraliz.noyau.client.zones;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Dessin commun des zones de la carte (03/11, choix du propriétaire après les rendus : « un mélange de A et B ») : zones
 * interdites de construction et limites des bases plan en main (CRac), cercles des zones d'extraction (Monde).
 * <ul>
 * <li>sol teinté dans la zone, très léger ({@code fond}, 0 pour aucun) ;</li>
 * <li>au bord : une bande nette au sol, un dégradé au sol vers l'intérieur, et un mur bas très léger qui s'efface vite
 * ({@link #MUR} bloc) : rien qui gêne la vue à l'intérieur de la zone ;</li>
 * <li>visible normalement jusqu'à la portée donnée par l'appelant ; en plus, à travers le décor (montagnes, bâtiments,
 * arbres, props) mais seulement près du joueur : morceau par morceau, jusqu'à {@link ReglagesZones#aTravers} blocs (10),
 * de plus en plus léger avec la distance ; au-delà, rien à travers les murs.</li>
 * </ul>
 * Géométrie construite UNE fois ({@link Construction}) en morceaux de {@link #MORCEAU} blocs, chacun dans son propre tampon
 * de la carte graphique ; à chaque image, seuls les morceaux à portée sont dessinés (deux appels chacun au plus), rien
 * n'est recalculé. Couleur donnée au dessin (vert, rouge, couleur de l'extraction qui avance...) : la géométrie est blanche,
 * seule son opacité est gardée. Toujours sur le fil du rendu ; {@link #close()} rend la mémoire de la carte graphique.
 */
public final class ZoneDessin implements AutoCloseable {
   /** Côté d'un morceau (blocs) : unité de la portée et du fondu « à travers ». */
   public static final int MORCEAU = 8;
   /** Hauteur du mur bas au bord (blocs) et son opacité en bas (0 en haut). */
   public static final float MUR = 0.9F, MUR_BAS = 0.22F;
   /** Bande nette au sol le long du bord (largeur, opacité), puis dégradé vers l'intérieur (largeur, opacité au départ). */
   public static final float NETTE = 0.14F, NETTE_A = 0.9F, DOUCE = 1.2F, DOUCE_A = 0.32F;
   /** Décollé du sol (pas de scintillement avec le bloc dessous). */
   public static final float DECOLLE = 0.03F;
   /** Opacité de la part cachée par le décor, tout près (baisse jusqu'à 0 à {@link ReglagesZones#aTravers}). */
   public static final float A_TRAVERS = 0.45F;

   private final double ox, oy, oz;
   private final List<Morceau> morceaux;
   private final Matrix4f matrice = new Matrix4f();
   private boolean ferme;

   private ZoneDessin(double ox, double oy, double oz, List<Morceau> morceaux) {
      this.ox = ox;
      this.oy = oy;
      this.oz = oz;
      this.morceaux = morceaux;
   }

   /** Nouvelle construction ; (ox, oy, oz) : un point de la zone (précision des sommets). */
   public static Construction construction(double ox, double oy, double oz) {
      return new Construction(ox, oy, oz);
   }

   /** Rien à dessiner. */
   public boolean vide() {
      return morceaux.isEmpty();
   }

   /**
    * Dessine la zone (événement RenderLevelStageEvent : {@code vue} = matrice de sa pile, {@code proj} = sa projection).
    * {@code force} : opacité générale (fondu d'ouverture, 0 = rien) ; {@code portee} : distance (blocs) au-delà de laquelle
    * un morceau n'est pas dessiné du tout.
    */
   public void dessiner(Matrix4f vue, Matrix4f proj, Vec3 cam, float r, float g, float b, float force, double portee) {
      if (ferme || force <= 0.01F || morceaux.isEmpty()) return;
      ShaderInstance sh = GameRenderer.getPositionColorShader();
      if (sh == null) return;
      double aTravers = ReglagesZones.aTravers;
      matrice.set(vue).translate((float) (ox - cam.x), (float) (oy - cam.y), (float) (oz - cam.z));
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.disableCull();
      try {
         for (Morceau m : morceaux) {
            double d = m.distance(cam.x, cam.y, cam.z);
            if (d > portee) continue;
            m.vbo.bind();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.setShaderColor(r, g, b, force);
            m.vbo.drawWithShader(matrice, proj, sh);
            if (d < aTravers) {
               // seulement la part cachée par le décor (profondeur plus loin que ce qui est déjà dessiné), plus légère
               RenderSystem.depthFunc(GL11.GL_GREATER);
               RenderSystem.setShaderColor(r, g, b, force * A_TRAVERS * (float) (1.0 - d / aTravers));
               m.vbo.drawWithShader(matrice, proj, sh);
            }
         }
      } finally {
         VertexBuffer.unbind();
         RenderSystem.depthFunc(GL11.GL_LEQUAL);
         RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
         RenderSystem.depthMask(true);
         RenderSystem.enableCull();
         RenderSystem.disableBlend();
      }
   }

   @Override
   public void close() {
      if (ferme) return;
      ferme = true;
      for (Morceau m : morceaux) m.vbo.close();
      morceaux.clear();
   }

   /** Un morceau de {@link #MORCEAU} x {@link #MORCEAU} blocs : son tampon et sa boîte (coordonnées du monde). */
   private static final class Morceau {
      final VertexBuffer vbo;
      final double x0, y0, z0, x1, y1, z1;

      Morceau(VertexBuffer vbo, float[] boite, double ox, double oy, double oz) {
         this.vbo = vbo;
         this.x0 = boite[0] + ox;
         this.y0 = boite[1] + oy;
         this.z0 = boite[2] + oz;
         this.x1 = boite[3] + ox;
         this.y1 = boite[4] + oy;
         this.z1 = boite[5] + oz;
      }

      double distance(double x, double y, double z) {
         double dx = Math.max(0, Math.max(x0 - x, x - x1));
         double dy = Math.max(0, Math.max(y0 - y, y - y1));
         double dz = Math.max(0, Math.max(z0 - z, z - z1));
         return Math.sqrt(dx * dx + dy * dy + dz * dz);
      }
   }

   /**
    * Construction d'une zone (une fois) : cases teintées ({@link #sol}) et arêtes du bord ({@link #bord}), rangées par
    * morceau ; {@link #finir()} envoie chaque morceau à la carte graphique.
    */
   public static final class Construction {
      private final double ox, oy, oz;
      private final Long2ObjectOpenHashMap<Sommets> parMorceau = new Long2ObjectOpenHashMap<>();

      private Construction(double ox, double oy, double oz) {
         this.ox = ox;
         this.oy = oy;
         this.oz = oz;
      }

      /** Case (x, z) de la zone teintée à l'opacité {@code a}, posée sur son sol {@code y} (dessus du bloc du sol). */
      public Construction sol(int x, int z, double y, float a) {
         if (a <= 0F) return this;
         float fy = (float) (y + DECOLLE - oy);
         float x0 = (float) (x - ox), x1 = x0 + 1, z0 = (float) (z - oz), z1 = z0 + 1;
         Sommets s = sommets(x + 0.5, z + 0.5);
         s.quad(x0, fy, z0, a, x0, fy, z1, a, x1, fy, z1, a, x1, fy, z0, a);
         return this;
      }

      /**
       * Une arête du bord, de A à B, chaque bout posé sur son sol ; (nx, nz) : direction de l'INTÉRIEUR de la zone
       * (longueur 1). Bande nette et dégradé au sol vers l'intérieur, mur bas qui s'efface vers le haut.
       */
      public Construction bord(double xa, double ya, double za, double xb, double yb, double zb, double nx, double nz) {
         float ax = (float) (xa - ox), ay = (float) (ya + DECOLLE * 2 - oy), az = (float) (za - oz);
         float bx = (float) (xb - ox), by = (float) (yb + DECOLLE * 2 - oy), bz = (float) (zb - oz);
         float fx = (float) nx, fz = (float) nz;
         Sommets s = sommets((xa + xb) / 2, (za + zb) / 2);
         float nnx = fx * NETTE, nnz = fz * NETTE, dnx = fx * (NETTE + DOUCE), dnz = fz * (NETTE + DOUCE);
         // bande nette, puis dégradé au sol (vers l'intérieur)
         s.quad(ax, ay, az, NETTE_A, bx, by, bz, NETTE_A, bx + nnx, by, bz + nnz, NETTE_A, ax + nnx, ay, az + nnz, NETTE_A);
         s.quad(ax + nnx, ay, az + nnz, DOUCE_A, bx + nnx, by, bz + nnz, DOUCE_A, bx + dnx, by, bz + dnz, 0F, ax + dnx, ay, az + dnz, 0F);
         // mur bas, très léger, effacé vers le haut
         s.quad(ax, ay, az, MUR_BAS, bx, by, bz, MUR_BAS, bx, by + MUR, bz, 0F, ax, ay + MUR, az, 0F);
         return this;
      }

      /** Envoie la zone à la carte graphique (fil du rendu). La construction ne sert plus ensuite. */
      public ZoneDessin finir() {
         List<Morceau> morceaux = new ArrayList<>(parMorceau.size());
         if (!parMorceau.isEmpty()) {
            BufferBuilder bb = tampon();
            for (Sommets s : parMorceau.values()) {
               if (s.n == 0) continue;
               bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
               for (int i = 0; i < s.n; i++) {
                  int k = i * 4;
                  bb.vertex(s.v[k], s.v[k + 1], s.v[k + 2]).color(1F, 1F, 1F, s.v[k + 3]).endVertex();
               }
               VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
               vbo.bind();
               vbo.upload(bb.end());
               morceaux.add(new Morceau(vbo, s.boite, ox, oy, oz));
            }
            VertexBuffer.unbind();
         }
         parMorceau.clear();
         return new ZoneDessin(ox, oy, oz, morceaux);
      }

      /** Tampon de construction partagé (fil du rendu) : un nouveau à chaque fois garderait sa mémoire hors de Java. */
      private static BufferBuilder tampon;

      private static BufferBuilder tampon() {
         if (tampon == null) tampon = new BufferBuilder(1 << 16);
         return tampon;
      }

      private Sommets sommets(double x, double z) {
         long cle = (long) Math.floorDiv((int) Math.floor(x), MORCEAU) << 32 | (Math.floorDiv((int) Math.floor(z), MORCEAU) & 0xFFFFFFFFL);
         Sommets s = parMorceau.get(cle);
         if (s == null) {
            s = new Sommets();
            parMorceau.put(cle, s);
         }
         return s;
      }
   }

   /** Sommets d'un morceau (x, y, z, opacité) et leur boîte, relatifs à l'origine de la zone. */
   private static final class Sommets {
      float[] v = new float[256];
      int n;
      final float[] boite = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

      void quad(float x0, float y0, float z0, float a0, float x1, float y1, float z1, float a1,
                float x2, float y2, float z2, float a2, float x3, float y3, float z3, float a3) {
         sommet(x0, y0, z0, a0);
         sommet(x1, y1, z1, a1);
         sommet(x2, y2, z2, a2);
         sommet(x3, y3, z3, a3);
      }

      private void sommet(float x, float y, float z, float a) {
         if ((n + 1) * 4 > v.length) v = java.util.Arrays.copyOf(v, v.length * 2);
         int k = n * 4;
         v[k] = x;
         v[k + 1] = y;
         v[k + 2] = z;
         v[k + 3] = a;
         n++;
         boite[0] = Math.min(boite[0], x);
         boite[1] = Math.min(boite[1], y);
         boite[2] = Math.min(boite[2], z);
         boite[3] = Math.max(boite[3], x);
         boite[4] = Math.max(boite[4], y + MUR);
         boite[5] = Math.max(boite[5], z);
      }
   }
}
