package fr.heralisz.n07.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.client.vfx.VfxDraw;
import fr.heralisz.n07.client.vfx.VfxRenderTypes;
import fr.heralisz.n07.entity.N07Boulder;
import fr.heralisz.n07.entity.N07Isotope;
import fr.heralisz.n07.entity.N07Shell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/** Rendus des projectiles du Sujet N-07 : obus / gouttes d'acide, bloc de béton arraché, isotopes instables. */
public final class N07Props {
   private static final ResourceLocation FLASH = new ResourceLocation(HeraliszN07.MODID, "textures/vfx/flash.png");
   private static final ResourceLocation RING = new ResourceLocation(HeraliszN07.MODID, "textures/vfx/beam_ring.png");
   private static final ResourceLocation LIQUID = new ResourceLocation(HeraliszN07.MODID, "textures/entity/tank/liquid.png");

   private N07Props() {}

   /** Boule de liquide radioactif qui luit : halo face caméra + goutte sphérique qui ondule. */
   static void blob(EntityRenderer<?> r, Entity e, float partial, PoseStack ps, MultiBufferSource buf, float size, float glow) {
      float t = e.tickCount + partial;
      ps.pushPose();
      ps.translate(0, e.getBbHeight() * 0.5, 0);
      ps.pushPose();
      ps.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
      quad(ps, buf.getBuffer(VfxRenderTypes.glow(FLASH)), size * (0.9F + 0.1F * Mth.sin(t * 0.9F)), t * 4F, VfxDraw.argb(0xB8FF7A, glow));
      ps.popPose();
      float rr = 0.3F * size;
      ps.scale(rr * (1 + 0.12F * Mth.sin(t * 0.8F)), rr * (1 + 0.12F * Mth.sin(t * 0.8F + 2)), rr * (1 + 0.12F * Mth.sin(t * 0.8F + 4)));
      VfxDraw.sphere(buf.getBuffer(RenderType.entityTranslucentEmissive(LIQUID)), ps.last().pose(), 0, 0, 0, 1, 12, t * 0.02F, 0xF0FFFFFF);
      ps.popPose();
   }

   static void quad(PoseStack ps, VertexConsumer vc, float s, float rot, int argb) {
      ps.pushPose();
      ps.mulPose(Axis.ZP.rotationDegrees(rot));
      Matrix4f m = ps.last().pose();
      VfxDraw.v(vc, m, -s, -s, 0, 0, 1, argb);
      VfxDraw.v(vc, m, s, -s, 0, 1, 1, argb);
      VfxDraw.v(vc, m, s, s, 0, 1, 0, argb);
      VfxDraw.v(vc, m, -s, s, 0, 0, 0, argb);
      ps.popPose();
   }

   public static class Shell extends EntityRenderer<N07Shell> {
      public Shell(EntityRendererProvider.Context ctx) {
         super(ctx);
      }

      @Override
      public void render(N07Shell e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
         boolean shell = e.kind() == N07Shell.SHELL;
         blob(this, e, partial, ps, buf, shell ? 1.1F : 0.5F, shell ? 0.8F : 0.6F);
         super.render(e, yaw, partial, ps, buf, light);
      }

      @Override
      public ResourceLocation getTextureLocation(N07Shell e) {
         return FLASH;
      }
   }

   /** L'isotope : noyau liquide incandescent, halo qui pulse de plus en plus vite, anneau qui tourne autour. */
   public static class Isotope extends EntityRenderer<N07Isotope> {
      public Isotope(EntityRendererProvider.Context ctx) {
         super(ctx);
      }

      @Override
      public void render(N07Isotope e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
         float t = e.tickCount + partial;
         float s = e.strength(partial);
         float rate = 0.25F + 0.5F * Mth.clamp(t / N07Isotope.LIFE, 0, 1);
         float pulse = 0.75F + 0.25F * Mth.sin(t * rate * 3F);
         blob(this, e, partial, ps, buf, 1.5F * s * pulse, 0.9F);
         ps.pushPose();
         ps.translate(0, e.getBbHeight() * 0.5, 0);
         ps.mulPose(Axis.YP.rotationDegrees(t * 9F));
         ps.mulPose(Axis.XP.rotationDegrees(70 + 20 * Mth.sin(t * 0.1F)));
         float rs = 0.9F * s;
         Matrix4f m = ps.last().pose();
         VertexConsumer vc = buf.getBuffer(VfxRenderTypes.glow(RING));
         int c = VfxDraw.argb(0x9CFF5A, 0.8F * pulse);
         VfxDraw.v(vc, m, -rs, 0, -rs, 0, 0, c);
         VfxDraw.v(vc, m, rs, 0, -rs, 1, 0, c);
         VfxDraw.v(vc, m, rs, 0, rs, 1, 1, c);
         VfxDraw.v(vc, m, -rs, 0, rs, 0, 1, c);
         ps.popPose();
         super.render(e, yaw, partial, ps, buf, light);
      }

      @Override
      public ResourceLocation getTextureLocation(N07Isotope e) {
         return FLASH;
      }
   }

   /** Bloc de dalle de béton arraché au sol (avec ses fers tordus), qui tournoie une fois lancé. */
   public static class Boulder extends EntityRenderer<N07Boulder> {
      private static final BlockState CONCRETE = Blocks.GRAY_CONCRETE.defaultBlockState(), STONE = Blocks.ANDESITE.defaultBlockState(),
         DIRT = Blocks.COARSE_DIRT.defaultBlockState(), IRON = Blocks.IRON_BARS.defaultBlockState();

      public Boulder(EntityRendererProvider.Context ctx) {
         super(ctx);
         this.shadowRadius = 0.8F;
      }

      @Override
      public void render(N07Boulder e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
         Minecraft mc = Minecraft.getInstance();
         float ft = e.flightTicks() + (e.thrown() ? partial : 0);
         ps.pushPose();
         ps.translate(0, 0.7, 0);
         ps.mulPose(Axis.YP.rotationDegrees(e.getId() * 37 % 360));
         ps.mulPose(Axis.XP.rotationDegrees(ft * 14F));
         ps.mulPose(Axis.ZP.rotationDegrees(ft * 6F));
         block(mc, ps, buf, CONCRETE, 1.5F, 0, 0, 0, light);
         block(mc, ps, buf, STONE, 0.9F, 0.5F, -0.35F, 0.3F, light);
         block(mc, ps, buf, DIRT, 1.0F, -0.4F, -0.55F, -0.2F, light);
         block(mc, ps, buf, IRON, 0.9F, 0.2F, 0.6F, -0.3F, light);
         ps.popPose();
         super.render(e, yaw, partial, ps, buf, light);
      }

      private static void block(Minecraft mc, PoseStack ps, MultiBufferSource buf, BlockState s, float size, float x, float y, float z, int light) {
         ps.pushPose();
         ps.translate(x, y, z);
         ps.scale(size, size, size);
         ps.translate(-0.5, -0.5, -0.5);
         mc.getBlockRenderer().renderSingleBlock(s, ps, buf, light, OverlayTexture.NO_OVERLAY);
         ps.popPose();
      }

      @Override
      public ResourceLocation getTextureLocation(N07Boulder e) {
         return FLASH;
      }
   }
}
