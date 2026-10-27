package fr.heralisz.camo.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.GunDisplayInstance;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.CamoData;
import fr.heralisz.camo.HeraliszCamo;
import fr.heralisz.camo.knife.KnifeSkins;
import fr.heralisz.camo.knife.KnifeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Icônes 3D des armes dans l'inventaire, à la place de l'image dessinée du gunpack : l'arme est « photographiée » une fois,
 * de profil (couteaux modélisés couchés : à plat, voir {@link #orientation}) et penchée comme les icônes dessinées, avec son
 * skin (camo, finition de lame) mais sans ses accessoires, dans
 * une petite texture gardée en mémoire graphique. L'inventaire affiche ensuite cette image comme l'icône d'origine (même
 * place, même coût). Réglages validés sur les aperçus hors jeu (outils/apercu_camos, vue « icone »).
 * <p>
 * Une icône par modèle d'arme et par skin (deux AK-47 Or partagent la même), MAX au plus (environ 5 Mo) : au-delà, la plus
 * anciennement vue laisse sa place. Rien sur le disque ni sur le serveur ; tout est libéré à la déconnexion et au
 * rechargement des ressources. Les photos se font au début d'une image (rien d'autre en cours de dessin), une par image :
 * en attendant, l'icône dessinée de TaCZ s'affiche. Une arme dont la carte du skin se calcule encore (PosMapCache) attend
 * qu'elle soit prête. Une arme qu'on ne peut pas photographier garde l'icône dessinée.
 */
@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID, value = Dist.CLIENT)
public final class IconesArmes {
   private static final Logger LOG = LogUtils.getLogger();
   /** Icône finale (px) ; la photo est prise au double puis réduite en moyenne : bords lisses. */
   private static final int TAILLE = 128;
   private static final int RENDU = TAILLE * 2;
   /** Réductions de l'icône (128 -> 1 px) : nette à toutes les tailles d'interface. */
   private static final int NIVEAUX = 7;
   /** Icônes gardées : 64 x 128² x 4 octets, réductions comprises, environ 5 Mo de mémoire graphique. */
   private static final int MAX = 64;
   /** Arme penchée, canon vers le bas à droite, comme les icônes dessinées des gunpacks. */
   private static final float ROULIS = -34.0F;
   private static final float MARGE = 0.03F;
   /** Arme placée loin devant : regard presque parallèle, reflets des camos comme sur l'aperçu validé. */
   private static final float LOIN = 50.0F;
   /** Couteau couché : au moins 2 fois plus étendu le long de sa lame que dans son épaisseur (écarts des sommets). */
   private static final float A_PLAT = 2.0F;

   /** Ce qui donne son aspect à l'icône : l'arme, son modèle d'affichage et son skin (camo ou finition de lame). */
   private record Cle(ResourceLocation arme, ResourceLocation affichage, @Nullable Object skin) {}

   /** Une icône prête : son adresse de texture et la dernière image où elle a été dessinée. */
   private static final class Prete {
      final ResourceLocation loc;
      long vue;

      Prete(ResourceLocation loc, long vue) {
         this.loc = loc;
         this.vue = vue;
      }
   }

   /** Icônes prêtes, de la plus anciennement vue à la plus récente. */
   private static final LinkedHashMap<Cle, Prete> PRETES = new LinkedHashMap<>(16, 0.75F, true);
   private static final LinkedHashMap<Cle, ItemStack> FILE = new LinkedHashMap<>();
   private static final Set<Cle> ECHECS = new HashSet<>();
   private static final ArrayDeque<ResourceLocation> LIBRES = new ArrayDeque<>();
   private static int adresses;
   private static boolean averti;
   /** Numéro de l'image en cours. */
   private static long image;
   @Nullable
   private static TextureTarget cible;
   /** La case de TaCZ (même géométrie que son icône dessinée), créée au premier dessin. */
   @Nullable
   private static SlotModel caseIcone;
   /** Non nul pendant le cadrage d'une photo : les sommets de l'arme y sont relevés au lieu d'être dessinés. */
   @Nullable
   private static Cadre cadre;

   /** Icône 3D de cette arme si elle est prête ; sinon null (la photo est demandée, TaCZ dessine son icône en attendant). */
   @Nullable
   public static ResourceLocation icone(ItemStack stack) {
      IGun gun = IGun.getIGunOrNull(stack);
      if (gun == null) {
         return null;
      }
      Cle cle = new Cle(gun.getGunId(stack), gun.getGunDisplayId(stack), skin(stack));
      Prete prete = PRETES.get(cle);
      if (prete != null) {
         prete.vue = image;
         return prete.loc;
      }
      if (!ECHECS.contains(cle) && !FILE.containsKey(cle) && FILE.size() < MAX) {
         FILE.put(cle, sansAccessoires(stack));
      }
      return null;
   }

   /** Dessine l'icône 3D à la place de l'icône du gunpack (même place et même taille que TaCZ). Faux si elle n'est pas prête. */
   public static boolean dessiner(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
      ResourceLocation loc = icone(stack);
      if (loc == null) {
         return false;
      }
      pose.pushPose();
      pose.translate(0.5, 1.5, 0.5);
      pose.mulPose(Axis.ZN.rotationDegrees(180.0F));
      if (caseIcone == null) {
         caseIcone = new SlotModel();
      }
      caseIcone.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(loc)), light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
      pose.popPose();
      return true;
   }

   /** Relevé des sommets en cours (cadrage d'une photo, voir BedrockModelFxMixin), sinon null. */
   @Nullable
   public static VertexConsumer cadre() {
      return cadre;
   }

   /** Une photo au début de chaque image où il y en a en attente. */
   @SubscribeEvent
   public static void onRenderTick(TickEvent.RenderTickEvent event) {
      if (event.phase != TickEvent.Phase.START) {
         return;
      }
      image++;
      if (FILE.isEmpty() || Minecraft.getInstance().getOverlay() != null) {
         return;
      }
      // toutes les icônes gardées sont à l'écran (grand coffre plein d'armes différentes) : on n'en chasse aucune, les armes
      // en trop gardent l'icône dessinée (sinon elles se chasseraient l'une l'autre à chaque image)
      if (PRETES.size() >= MAX && PRETES.values().iterator().next().vue >= image - 2) {
         FILE.clear();
         return;
      }
      Iterator<Map.Entry<Cle, ItemStack>> it = FILE.entrySet().iterator();
      Map.Entry<Cle, ItemStack> suivante = it.next();
      it.remove();
      photographier(suivante.getKey(), suivante.getValue());
   }

   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      vider();
   }

   /** Libère toutes les icônes (déconnexion ; rechargement des ressources : modèles et textures ont pu changer). */
   public static void vider() {
      if (!RenderSystem.isOnRenderThread()) {
         RenderSystem.recordRenderCall(IconesArmes::vider);
         return;
      }
      TextureManager textures = Minecraft.getInstance().getTextureManager();
      for (Prete prete : PRETES.values()) {
         // remplacer la texture par la texture manquante (partagée) libère la nôtre ; l'adresse resservira
         textures.register(prete.loc, MissingTextureAtlasSprite.getTexture());
         LIBRES.add(prete.loc);
      }
      PRETES.clear();
      FILE.clear();
      ECHECS.clear();
   }

   /** Le skin : le camo de l'arme, sinon la finition de lame d'un couteau. */
   @Nullable
   private static Object skin(ItemStack stack) {
      Object camo = CamoData.get(stack);
      return camo != null ? camo : KnifeSkins.of(stack);
   }

   /**
    * Vrai si le skin de l'arme n'est pas encore prêt : sa carte de positions se calcule à part (PosMapCache), et une photo
    * prise avant garderait l'arme sans skin jusqu'au prochain rechargement. La carte est demandée au passage.
    */
   private static boolean skinPasPret(ItemStack stack, BedrockGunModel modele, ResourceLocation texture) {
      Camo camo = CamoData.get(stack);
      KnifeSkins.Skin lame = camo == null ? KnifeSkins.of(stack) : null;
      if (camo == null && lame == null) {
         return false;
      }
      if (CamoShaders.get(camo != null ? camo.shaderId : lame.finish().shaderId) == null) {
         return false;
      }
      KnifeType couteau = camo != null ? null : lame.knife();
      PosMapCache.get(modele, couteau, texture);
      return PosMapCache.pasPrete(modele, couteau);
   }

   /** Copie de l'arme sans ses accessoires (lunette, silencieux…) : l'icône ne dépend que du modèle et du skin. */
   private static ItemStack sansAccessoires(ItemStack stack) {
      ItemStack copie = stack.copy();
      CompoundTag tag = copie.getTag();
      if (tag != null) {
         for (AttachmentType type : AttachmentType.values()) {
            tag.remove("Attachment" + type.name());
         }
      }
      return copie;
   }

   private static void photographier(Cle cle, ItemStack stack) {
      GunDisplayInstance display = TimelessAPI.getGunDisplay(stack).orElse(null);
      BedrockGunModel modele = display == null ? null : display.getGunModel();
      ResourceLocation texture = display == null ? null : display.getModelTexture();
      if (modele == null || texture == null) {
         ECHECS.add(cle);
         return;
      }
      if (skinPasPret(stack, modele, texture)) {
         FILE.put(cle, stack);   // reprise à une image suivante
         return;
      }
      // rendu « entité découpée » comme TaCZ hors de la 1re personne ; remplacé par le rendu camo quand l'arme a un skin
      RenderType type = RenderType.entityCutout(texture);
      try {
         // 1. cadrage : les sommets de l'arme, dans le repère des modèles TaCZ retourné (Y vers le haut), sans rien dessiner
         Cadre releve = new Cadre();
         cadre = releve;
         try {
            PoseStack pose = new PoseStack();
            pose.scale(-1.0F, -1.0F, 1.0F);
            modele.render(pose, stack, ItemDisplayContext.GUI, type, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
         } finally {
            cadre = null;
         }
         if (releve.n < 3) {
            ECHECS.add(cle);
            return;
         }
         // 2. orientation : canon vers la droite (côté droit de l'arme face à nous) ou couteau à plat, puis l'arme penchée
         Quaternionf rot = new Quaternionf(Axis.ZP.rotationDegrees(ROULIS)).mul(orientation(modele, releve));
         // 3. photo, réduction, rangement
         ranger(cle, photo(modele, stack, type, rot, releve.bornes(rot)));
      } catch (RuntimeException e) {
         ECHECS.add(cle);
         if (!averti) {
            averti = true;
            LOG.warn("Icône 3D impossible pour {} : l'icône dessinée du gunpack reste affichée", cle.arme(), e);
         }
      }
   }

   /**
    * Orientation de l'arme, avant de la pencher.
    * <ul>
    *    <li>Armes (canon le long de Z, comme toutes celles de TaCZ) : de profil, canon vers la droite (inchangé).</li>
    *    <li>Couteaux modélisés couchés : lame le long de X (ou de Y), plat de la lame face à Z, donc très minces le long de
    *    Z (« Knife » CT et T, « M9 Bayonet » et ses skins). Vus de profil comme une arme, on les voyait par le bout ou par
    *    la tranche, presque invisibles : ils sont photographiés à plat, lame à gauche et manche à droite, ce qui donne
    *    une fois penché la pointe en haut à gauche, comme les icônes dessinées de leurs gunpacks. Le manche est la moitié
    *    la plus épaisse (une lame est plus mince que sa poignée).</li>
    * </ul>
    * Seuls ces modèles changent : vérifié sur les 170 modèles d'armes des gunpacks du dépôt (12/10), aucun autre n'est
    * plus mince le long de Z que le long de X et de Y.
    */
   private static Quaternionf orientation(BedrockGunModel modele, Cadre releve) {
      float[] e = releve.ecarts();
      if (e[2] < e[0] && e[2] < e[1] && Math.max(e[0], e[1]) >= A_PLAT * e[2]) {
         int axeLong = e[0] >= e[1] ? 0 : 1;
         boolean manchePlus = releve.plusEpaisCotePlus(axeLong);
         if (axeLong == 0) {
            // lame le long de X : manche du côté +X déjà à droite ; sinon demi-tour autour de Y (le haut reste en haut)
            return manchePlus ? new Quaternionf() : Axis.YP.rotationDegrees(180.0F);
         }
         // lame le long de Y : quart de tour autour de Z (X -> haut, Y -> gauche), puis demi-tour si le manche est en +Y
         Quaternionf quart = Axis.ZP.rotationDegrees(90.0F);
         return manchePlus ? new Quaternionf(Axis.YP.rotationDegrees(180.0F)).mul(quart) : quart;
      }
      float lacet = canonVersPlusZ(modele, releve) ? 90.0F : -90.0F;
      return Axis.YP.rotationDegrees(lacet);
   }

   /** Vrai si la bouche du canon est du côté +Z (les armes TaCZ pointent vers -Z, mais un modèle peut faire autrement). */
   private static boolean canonVersPlusZ(BedrockGunModel modele, Cadre releve) {
      List<BedrockPart> chemin = modele.getMuzzleFlashPosPath();
      if (chemin == null || chemin.isEmpty()) {
         return false;
      }
      PoseStack pose = new PoseStack();
      pose.scale(-1.0F, -1.0F, 1.0F);
      for (BedrockPart part : chemin) {
         part.translateAndRotateAndScale(pose);
      }
      Vector3f bouche = pose.last().pose().transformPosition(new Vector3f());
      float[] b = releve.bornes(new Quaternionf());
      return bouche.z > (b[2] + b[5]) / 2;
   }

   /** Photo de l'arme (fond transparent, sans perspective), cadrée sur la boîte b des sommets tournés. */
   private static NativeImage photo(BedrockGunModel modele, ItemStack stack, RenderType type, Quaternionf rot, float[] b) {
      Minecraft mc = Minecraft.getInstance();
      if (cible == null) {
         cible = new TextureTarget(RENDU, RENDU, true, Minecraft.ON_OSX);
         cible.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
      }
      float cx = (b[0] + b[3]) / 2, cy = (b[1] + b[4]) / 2, cz = (b[2] + b[5]) / 2;
      float demi = Math.max(b[3] - b[0], b[4] - b[1]) / 2 * (1 + MARGE);
      float prof = b[5] - b[2] + 1.0F;
      Matrix4f projAvant = new Matrix4f(RenderSystem.getProjectionMatrix());
      VertexSorting triAvant = RenderSystem.getVertexSorting();
      Matrix3f vueAvant = new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
      PoseStack vue = RenderSystem.getModelViewStack();
      vue.pushPose();
      vue.setIdentity();
      RenderSystem.applyModelViewMatrix();
      NativeImage grande = new NativeImage(RENDU, RENDU, false);
      try {
         RenderSystem.depthMask(true);   // sinon la profondeur de la photo précédente ne serait pas effacée
         cible.clear(Minecraft.ON_OSX);
         cible.bindWrite(true);
         RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-demi, demi, -demi, demi, LOIN - prof, LOIN + prof),
            VertexSorting.ORTHOGRAPHIC_Z);
         // décor des reflets : de face, un peu tourné (réglage de l'aperçu validé : reflets marqués sur l'Or)
         RenderSystem.setInverseViewRotationMatrix(new Matrix3f().rotateY((float) Math.toRadians(35.0)).rotateX((float) Math.toRadians(2.0)));
         Lighting.setupFor3DItems();
         FogRenderer.setupNoFog();
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         PoseStack pose = new PoseStack();
         pose.translate(-cx, -cy, -cz - LOIN);
         pose.mulPose(rot);
         pose.scale(-1.0F, -1.0F, 1.0F);
         try {
            modele.render(pose, stack, ItemDisplayContext.GUI, type, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
         } finally {
            // effets 3D des skins et tout reste éventuel : dessinés dans la photo, pas à l'écran
            mc.renderBuffers().bufferSource().endBatch();
         }
         RenderSystem.bindTexture(cible.getColorTextureId());
         grande.downloadTexture(0, false);
         return reduire(grande);
      } finally {
         grande.close();
         vue.popPose();
         RenderSystem.applyModelViewMatrix();
         RenderSystem.setProjectionMatrix(projAvant, triAvant);
         RenderSystem.setInverseViewRotationMatrix(vueAvant);
         mc.getMainRenderTarget().bindWrite(true);
      }
   }

   /**
    * Photo 256² -> icône 128² : moyenne de 2 x 2 pixels pondérée par l'opacité (bords lisses, sans liseré sombre), image
    * remise à l'endroit (la lecture commence par le bas), couleur des pixels vides prise aux voisins (filtrage propre).
    */
   private static NativeImage reduire(NativeImage grande) {
      int n = TAILLE;
      float[] r = new float[n * n], g = new float[n * n], bl = new float[n * n], a = new float[n * n];
      for (int y = 0; y < n; y++) {
         for (int x = 0; x < n; x++) {
            float sr = 0, sg = 0, sb = 0, sa = 0;
            for (int dy = 0; dy < 2; dy++) {
               for (int dx = 0; dx < 2; dx++) {
                  int c = grande.getPixelRGBA(x * 2 + dx, y * 2 + dy);
                  float al = (c >>> 24) / 255.0F;
                  sr += (c & 0xFF) * al;
                  sg += (c >> 8 & 0xFF) * al;
                  sb += (c >> 16 & 0xFF) * al;
                  sa += al;
               }
            }
            int i = (n - 1 - y) * n + x;
            a[i] = sa / 4;
            if (sa > 0) {
               r[i] = sr / sa;
               g[i] = sg / sa;
               bl[i] = sb / sa;
            }
         }
      }
      bavure(r, g, bl, a, n);
      NativeImage petite = new NativeImage(n, n, false);
      for (int y = 0; y < n; y++) {
         for (int x = 0; x < n; x++) {
            int i = y * n + x;
            petite.setPixelRGBA(x, y, Math.round(a[i] * 255) << 24 | Math.round(bl[i]) << 16 | Math.round(g[i]) << 8 | Math.round(r[i]));
         }
      }
      return petite;
   }

   /** Les pixels vides prennent la couleur moyenne de leurs voisins visibles (4 passes) : pas de noir mêlé au filtrage. */
   private static void bavure(float[] r, float[] g, float[] b, float[] a, int n) {
      boolean[] connu = new boolean[n * n];
      for (int i = 0; i < n * n; i++) {
         connu[i] = a[i] > 0;
      }
      for (int passe = 0; passe < 4; passe++) {
         boolean[] apres = connu.clone();
         for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
               int i = y * n + x;
               if (connu[i]) {
                  continue;
               }
               float sr = 0, sg = 0, sb = 0;
               int k = 0;
               for (int dy = -1; dy <= 1; dy++) {
                  for (int dx = -1; dx <= 1; dx++) {
                     int xx = x + dx, yy = y + dy;
                     if (xx >= 0 && yy >= 0 && xx < n && yy < n && connu[yy * n + xx]) {
                        int j = yy * n + xx;
                        sr += r[j];
                        sg += g[j];
                        sb += b[j];
                        k++;
                     }
                  }
               }
               if (k > 0) {
                  r[i] = sr / k;
                  g[i] = sg / k;
                  b[i] = sb / k;
                  apres[i] = true;
               }
            }
         }
         connu = apres;
      }
   }

   /** Range l'icône sous une des MAX adresses ; plein : la plus anciennement vue laisse la sienne (sa texture est libérée). */
   private static void ranger(Cle cle, NativeImage photo) {
      ResourceLocation loc;
      if (PRETES.size() >= MAX) {
         Iterator<Map.Entry<Cle, Prete>> it = PRETES.entrySet().iterator();
         loc = it.next().getValue().loc;
         it.remove();
      } else if (!LIBRES.isEmpty()) {
         loc = LIBRES.poll();
      } else {
         loc = new ResourceLocation(HeraliszCamo.MODID, "icone_arme/" + adresses++);
      }
      // une adresse déjà utilisée : enregistrer la nouvelle texture libère l'ancienne
      Minecraft.getInstance().getTextureManager().register(loc, new Icone(photo));
      PRETES.put(cle, new Prete(loc, image));
   }

   /** Texture d'une icône, réductions comprises, toujours filtrée en douceur (même quand TaCZ la demande nette). */
   private static final class Icone extends AbstractTexture {
      Icone(NativeImage photo) {
         TextureUtil.prepareImage(this.getId(), NIVEAUX, TAILLE, TAILLE);
         photo.upload(0, 0, 0, 0, 0, TAILLE, TAILLE, true, true, true, true);
         GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
      }

      @Override
      public void load(ResourceManager manager) {
      }

      @Override
      public void setFilter(boolean blur, boolean mipmap) {
         super.setFilter(true, true);
      }
   }

   /** Relève les sommets dessinés par TaCZ (cadrage d'une photo), sans rien envoyer à la carte graphique. */
   private static final class Cadre implements VertexConsumer {
      private float[] p = new float[3 * 4096];
      private int n;

      private void ajouter(float x, float y, float z) {
         if (3 * n + 3 > p.length) {
            if (p.length >= 3 * 262_144) {
               return;
            }
            p = Arrays.copyOf(p, p.length * 2);
         }
         p[3 * n] = x;
         p[3 * n + 1] = y;
         p[3 * n + 2] = z;
         n++;
      }

      /** Écart type des sommets le long de X, Y et Z : l'étendue de l'arme dans chaque sens. */
      float[] ecarts() {
         double[] somme = new double[3];
         double[] carres = new double[3];
         for (int i = 0; i < n; i++) {
            for (int a = 0; a < 3; a++) {
               double v = p[3 * i + a];
               somme[a] += v;
               carres[a] += v * v;
            }
         }
         float[] e = new float[3];
         for (int a = 0; a < 3; a++) {
            e[a] = (float) Math.sqrt(variance(somme[a], carres[a], n));
         }
         return e;
      }

      /** Vrai si la moitié « + » de l'axe donné (au-delà du milieu de la boîte) est la plus épaisse le long de Z. */
      boolean plusEpaisCotePlus(int axe) {
         float min = Float.MAX_VALUE;
         float max = -Float.MAX_VALUE;
         for (int i = 0; i < n; i++) {
            min = Math.min(min, p[3 * i + axe]);
            max = Math.max(max, p[3 * i + axe]);
         }
         float milieu = (min + max) / 2;
         double[] somme = new double[2];
         double[] carres = new double[2];
         int[] nombre = new int[2];
         for (int i = 0; i < n; i++) {
            int moitie = p[3 * i + axe] >= milieu ? 1 : 0;
            double z = p[3 * i + 2];
            somme[moitie] += z;
            carres[moitie] += z * z;
            nombre[moitie]++;
         }
         return variance(somme[1], carres[1], nombre[1]) > variance(somme[0], carres[0], nombre[0]);
      }

      private static double variance(double somme, double carres, int nombre) {
         if (nombre == 0) {
            return 0.0;
         }
         double moyenne = somme / nombre;
         return Math.max(0.0, carres / nombre - moyenne * moyenne);
      }

      /** Boîte des sommets tournés par rot : x0, y0, z0, x1, y1, z1. */
      float[] bornes(Quaternionf rot) {
         float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
         Vector3f v = new Vector3f();
         for (int i = 0; i < n; i++) {
            rot.transform(v.set(p[3 * i], p[3 * i + 1], p[3 * i + 2]));
            b[0] = Math.min(b[0], v.x);
            b[1] = Math.min(b[1], v.y);
            b[2] = Math.min(b[2], v.z);
            b[3] = Math.max(b[3], v.x);
            b[4] = Math.max(b[4], v.y);
            b[5] = Math.max(b[5], v.z);
         }
         return b;
      }

      @Override
      public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay, int light, float nx, float ny, float nz) {
         ajouter(x, y, z);
      }

      @Override public VertexConsumer vertex(double x, double y, double z) { ajouter((float) x, (float) y, (float) z); return this; }
      @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
      @Override public VertexConsumer uv(float u, float v) { return this; }
      @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
      @Override public VertexConsumer uv2(int u, int v) { return this; }
      @Override public VertexConsumer normal(float x, float y, float z) { return this; }
      @Override public void endVertex() {}
      @Override public void defaultColor(int r, int g, int b, int a) {}
      @Override public void unsetDefaultColor() {}
   }

   private IconesArmes() {}
}
