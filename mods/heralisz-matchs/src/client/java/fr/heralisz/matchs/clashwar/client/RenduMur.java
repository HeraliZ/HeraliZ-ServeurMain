package fr.heralisz.matchs.clashwar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.matchs.clashwar.bloc.TableauBlocEntite;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Rendu du mur « tableau du tournoi » : un seul quadrilatere plein eclairage sur la face avant du mur, texture
 * {@link TextureTableau} (rapport 2:1, centree dans le mur si ses proportions different, bordure sombre autour). Aucune
 * allocation par image : la matrice et le tampon viennent du moteur de rendu.
 */
public class RenduMur implements BlockEntityRenderer<TableauBlocEntite> {
    private static final float DECALAGE = 0.502f;

    public RenduMur(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(TableauBlocEntite be, float partial, PoseStack pose, MultiBufferSource tampons, int lumiere, int recouvrement) {
        ResourceLocation tex = TextureTableau.demander();
        if (!TextureTableau.prete()) return;
        Direction face = be.face();
        int l = be.largeur(), h = be.hauteur();
        float aw = l, ah = h;
        if (aw / ah > 2f) aw = ah * 2f;
        else ah = aw / 2f;
        float x0 = -0.5f + (l - aw) / 2f, y0 = (h - ah) / 2f, x1 = x0 + aw, y1 = y0 + ah;
        pose.pushPose();
        pose.translate(0.5f, 0f, 0.5f);
        // repere local : +z = direction de l'ecran (vers les spectateurs), +x = droite du spectateur
        pose.mulPose(Axis.YP.rotationDegrees(-face.toYRot()));
        Matrix4f m = pose.last().pose();
        int lum = LightTexture.FULL_BRIGHT;
        if (aw < l || ah < h) {
            // bordure sombre la ou la texture ne couvre pas le mur
            VertexConsumer fond = tampons.getBuffer(RenderType.textBackground());
            quad(fond, m, -0.5f, 0f, l - 0.5f, h, DECALAGE - 0.001f, 0xFF07090E, lum);
        }
        VertexConsumer vc = tampons.getBuffer(RenderType.text(tex));
        vc.vertex(m, x0, y0, DECALAGE).color(255, 255, 255, 255).uv(0f, 0f).uv2(lum).endVertex();
        vc.vertex(m, x1, y0, DECALAGE).color(255, 255, 255, 255).uv(1f, 0f).uv2(lum).endVertex();
        vc.vertex(m, x1, y1, DECALAGE).color(255, 255, 255, 255).uv(1f, 1f).uv2(lum).endVertex();
        vc.vertex(m, x0, y1, DECALAGE).color(255, 255, 255, 255).uv(0f, 1f).uv2(lum).endVertex();
        pose.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb, int lum) {
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        vc.vertex(m, x0, y0, z).color(r, g, b, a).uv2(lum).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, a).uv2(lum).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, a).uv2(lum).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, a).uv2(lum).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(TableauBlocEntite be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
