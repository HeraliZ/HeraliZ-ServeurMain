package fr.heralisz.bunker.barricade;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.bunker.HeraliszBunker;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Rendu de la barricade façon Rust : 9 gros rondins ronds aux hauteurs irrégulières, taillés en pointe, liés par deux
 * cordes, une traverse devant et deux jambes de force derrière. Rondins « ronds » : 4 cubes tournés de 22,5° (16 pans).
 * Texture 4x plus fine que la grille du modèle (tools/barricade_textures.py), suivant l'état (intacte -> très abîmée).
 */
@Mod.EventBusSubscriber(modid = HeraliszBunker.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class BarricadeRenderer extends EntityRenderer<BarricadeEntity> {
   public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(HeraliszBunker.MODID, "wooden_barricade"), "main");
   static final ResourceLocation[] TEXTURES = new ResourceLocation[4];
   private static final float STEP = (float) (Math.PI / 8);

   static {
      for (int i = 0; i < 4; i++) {
         TEXTURES[i] = new ResourceLocation(HeraliszBunker.MODID, "textures/entity/wooden_barricade_" + i + ".png");
      }
   }

   private final ModelPart root;

   public BarricadeRenderer(EntityRendererProvider.Context ctx) {
      super(ctx);
      this.root = ctx.bakeLayer(LAYER);
   }

   /** Cylindre vertical (4 cubes tournés autour de Y) de rayon r et hauteur h, posé en y. */
   private static void vertical(PartDefinition parent, String name, float x, float y, float z, float r, float h, int u, int v) {
      PartDefinition p = parent.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offset(x, y, z));
      for (int k = 0; k < 4; k++) {
         p.addOrReplaceChild("s" + k, CubeListBuilder.create().texOffs(u, v).addBox(-r, 0F, -r, 2 * r, h, 2 * r), PartPose.rotation(0F, k * STEP, 0F));
      }
   }

   /** Cylindre horizontal le long de X (4 cubes tournés autour de X), centré en (x, y, z). */
   private static void horizontal(PartDefinition parent, String name, float x, float y, float z, float r, float len, int u, int v) {
      PartDefinition p = parent.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offset(x, y, z));
      for (int k = 0; k < 4; k++) {
         p.addOrReplaceChild("s" + k, CubeListBuilder.create().texOffs(u, v).addBox(-len / 2, -r, -r, len, 2 * r, 2 * r), PartPose.rotation(k * STEP, 0F, 0F));
      }
   }

   /** Unités en pixels (16 = 1 bloc), sol à y = 0, hauteur vers le haut, avant de la barricade vers -z. */
   public static LayerDefinition createLayer() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      float r = BarricadeEntity.LOG_D / 2;
      for (int i = 0; i < BarricadeEntity.LOGS; i++) {
         float x = (float) (BarricadeEntity.logOffset(i) * 16), z = (i % 2 == 0) ? -0.35F : 0.35F, h = BarricadeEntity.LOG_H[i];
         PartDefinition log = root.addOrReplaceChild("log_" + i, CubeListBuilder.create(), PartPose.ZERO);
         vertical(log, "body", x, 0F, z, r, h, 0, 0);
         // pointe taillée : trois étages de plus en plus fins
         vertical(log, "tip0", x, h, z, r * 0.74F, 2.2F, 20, 0);
         vertical(log, "tip1", x, h + 2.2F, z, r * 0.46F, 2.0F, 20, 0);
         vertical(log, "tip2", x, h + 4.2F, z, r * 0.2F, 1.4F, 20, 0);
         // deux tours de corde
         vertical(log, "rope0", x, 8.5F, z, r + 0.3F, 1.4F, 20, 8);
         vertical(log, "rope1", x, 26.5F, z, r + 0.3F, 1.4F, 20, 8);
      }
      float width = (float) (BarricadeEntity.logOffset(BarricadeEntity.LOGS - 1) * 32) + BarricadeEntity.LOG_D + 4F;
      horizontal(root, "brace", 0F, 12F, -r - 1.5F, 1.8F, width, 0, 48);
      // jambes de force derrière, penchées vers les rondins
      for (int k = 0; k < 2; k++) {
         PartDefinition leg = root.addOrReplaceChild("leg_" + k, CubeListBuilder.create(),
            PartPose.offsetAndRotation(k == 0 ? -BarricadeEntity.LEG_X : BarricadeEntity.LEG_X, 0F, 11F, -0.36F, 0F, 0F));
         vertical(leg, "post", 0F, 0F, 0F, 1.6F, 29F, 44, 0);
      }
      return LayerDefinition.create(mesh, 128, 64);
   }

   @Override
   public void render(BarricadeEntity entity, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
      ps.pushPose();
      ps.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
      this.root.render(ps, buffers.getBuffer(RenderType.entityCutout(this.getTextureLocation(entity))), light, OverlayTexture.NO_OVERLAY);
      ps.popPose();
      super.render(entity, yaw, partialTick, ps, buffers, light);
   }

   @Override
   public ResourceLocation getTextureLocation(BarricadeEntity entity) {
      return TEXTURES[entity.damageStage()];
   }

   @SubscribeEvent
   public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
      event.registerLayerDefinition(LAYER, BarricadeRenderer::createLayer);
   }

   @SubscribeEvent
   public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(BarricadeRegistry.BARRICADE.get(), BarricadeRenderer::new);
   }
}
