package fr.heralisz.n07.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.n07.HeraliszN07;
import fr.heralisz.n07.client.vfx.VfxDraw;
import fr.heralisz.n07.client.vfx.VfxRenderTypes;
import fr.heralisz.n07.dungeon.SirenBlock;
import fr.heralisz.n07.dungeon.SirenBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Sirène en alerte : deux faisceaux rouges opposés tournent autour de l'axe vertical et balaient la salle (ceux qui pointent vers
 * le mur s'effacent), halo qui pulse autour du dôme. Éteinte : rien (le modèle de bloc suffit).
 */
public class SirenRenderer implements BlockEntityRenderer<SirenBlockEntity> {
   private static final ResourceLocation T_CONE = new ResourceLocation(HeraliszN07.MODID, "textures/vfx/light_cone.png");
   private static final ResourceLocation T_GLOW = new ResourceLocation(HeraliszN07.MODID, "textures/vfx/glow.png");
   private static final int RED = 0xFF2A14;

   @Override
   public void render(SirenBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
      BlockState st = be.getBlockState();
      if (!st.hasProperty(SirenBlock.LIT) || !st.getValue(SirenBlock.LIT) || be.getLevel() == null) return;
      Direction f = st.getValue(SirenBlock.FACING);
      Vec3 n = Vec3.atLowerCornerOf(f.getNormal());
      Vec3 c = new Vec3(0.5, 0.5, 0.5).add(n.scale(0.2));
      float t = be.getLevel().getGameTime() + partial;
      float a = t * 0.26F + be.phase * Mth.TWO_PI;
      Matrix4f m = ps.last().pose();
      VertexConsumer cone = buf.getBuffer(VfxRenderTypes.glow(T_CONE));
      for (int k = 0; k < 2; k++) {
         double ang = a + k * Math.PI;
         Vec3 dir = new Vec3(Math.cos(ang), -0.08, Math.sin(ang)).normalize();
         double facing = dir.dot(n);
         if (facing < -0.05) continue;
         float k2 = (float) Mth.clamp((facing + 0.05) * 3, 0, 1);
         beam(cone, m, c, dir, 10.0, 0.12, 1.9, VfxDraw.argb(RED, 0.26F * k2));
         beam(cone, m, c, dir, 6.0, 0.06, 0.7, VfxDraw.argb(0xFF8A60, 0.22F * k2));
      }
      float pulse = 0.6F + 0.4F * Math.abs(Mth.sin(a));
      billboard(buf.getBuffer(VfxRenderTypes.glow(T_GLOW)), ps, c, 1.3F, VfxDraw.argb(RED, 0.8F * pulse));
      billboard(buf.getBuffer(VfxRenderTypes.glow(T_GLOW)), ps, c, 4.5F, VfxDraw.argb(RED, 0.16F * pulse));
   }

   /** Faisceau conique ouvert de c le long de dir (texture de cône : v 0 à la source). */
   private static void beam(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 dir, double len, double r0, double r1, int argb) {
      Vec3 up = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
      Vec3 u = dir.cross(up).normalize(), v = dir.cross(u).normalize();
      Vec3 end = c.add(dir.scale(len));
      int seg = 12, fade = argb & 0x00FFFFFF;
      for (int i = 0; i < seg; i++) {
         double a0 = i * Mth.TWO_PI / seg, a1 = (i + 1) * Mth.TWO_PI / seg;
         Vec3 p0 = c.add(u.scale(Math.cos(a0) * r0)).add(v.scale(Math.sin(a0) * r0));
         Vec3 p1 = c.add(u.scale(Math.cos(a1) * r0)).add(v.scale(Math.sin(a1) * r0));
         Vec3 q1 = end.add(u.scale(Math.cos(a1) * r1)).add(v.scale(Math.sin(a1) * r1));
         Vec3 q0 = end.add(u.scale(Math.cos(a0) * r1)).add(v.scale(Math.sin(a0) * r1));
         VfxDraw.v(vc, m, p0.x, p0.y, p0.z, (float) i / seg, 0, argb);
         VfxDraw.v(vc, m, p1.x, p1.y, p1.z, (float) (i + 1) / seg, 0, argb);
         VfxDraw.v(vc, m, q1.x, q1.y, q1.z, (float) (i + 1) / seg, 1, fade);
         VfxDraw.v(vc, m, q0.x, q0.y, q0.z, (float) i / seg, 1, fade);
      }
   }

   private static void billboard(VertexConsumer vc, PoseStack ps, Vec3 c, float size, int argb) {
      ps.pushPose();
      ps.translate(c.x, c.y, c.z);
      ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
      Matrix4f m = ps.last().pose();
      float h = size / 2;
      VfxDraw.v(vc, m, -h, -h, 0, 0, 1, argb);
      VfxDraw.v(vc, m, h, -h, 0, 1, 1, argb);
      VfxDraw.v(vc, m, h, h, 0, 1, 0, argb);
      VfxDraw.v(vc, m, -h, h, 0, 0, 0, argb);
      ps.popPose();
   }

   @Override
   public boolean shouldRenderOffScreen(SirenBlockEntity be) {
      // 1.1.0 : soumis a l occlusion des sections (ne se dessine plus a travers la roche, ex. depuis la carte au-dessus)
      return false;
   }

   @Override
   public int getViewDistance() {
      return 64;
   }
}
