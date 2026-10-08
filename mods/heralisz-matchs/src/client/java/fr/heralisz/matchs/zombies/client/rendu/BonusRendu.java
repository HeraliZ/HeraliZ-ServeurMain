package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.contenu.entite.BonusEntite;
import fr.heralisz.matchs.zombies.jeu.api.TypeBonus;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Rendu d'un bonus au sol : icone maison (blanche, teintee de la couleur du bonus) face a la camera qui flotte et se
 * balance, double halo additif qui respire, anneau qui tourne a plat sous l'icone. Clignote de plus en plus vite dans
 * ses dernieres secondes.
 */
public class BonusRendu extends EntityRenderer<BonusEntite> {
    private static final ResourceLocation HALO = HeraliszMatchs.id("textures/entity/zombies/halo.png");
    private static final RenderType HALO_RT = ZbTypesRendu.lueur(HALO);
    private static final ResourceLocation[] ICONES = new ResourceLocation[TypeBonus.values().length];
    private static final RenderType[] ICONES_RT = new RenderType[ICONES.length];
    private static final float[] UV = {0, 0, 1, 1};

    static {
        for (TypeBonus t : TypeBonus.values()) {
            ICONES[t.ordinal()] = HeraliszMatchs.id("textures/gui/zombies/bonus_" + t.id() + ".png");
            ICONES_RT[t.ordinal()] = RenderType.entityTranslucentEmissive(ICONES[t.ordinal()]);
        }
    }

    public BonusRendu(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(BonusEntite b) {
        return ICONES[b.type().ordinal()];
    }

    @Override
    public void render(BonusEntite b, float lacet, float partiel, PoseStack ps, MultiBufferSource buf, int lumiere) {
        float t = b.tickCount + partiel;
        int reste = b.reste();
        if (reste > 0) {
            // clignotement : 4 Hz puis 8 Hz sous 2 s
            float f = reste <= 2 ? 0.8F : 0.4F;
            if (Mth.sin(t * f) < -0.2F) return;
        }
        TypeBonus type = b.type();
        int c = type.couleur;
        int r = (c >> 16) & 255, g = (c >> 8) & 255, bl = c & 255;
        float flotte = 0.55F + 0.12F * Mth.sin(t * 0.12F);
        ps.pushPose();
        ps.translate(0, flotte, 0);
        // anneau a plat qui tourne
        ps.pushPose();
        ps.translate(0, -0.42F, 0);
        ps.mulPose(Axis.XP.rotationDegrees(90));
        ps.mulPose(Axis.ZP.rotationDegrees(t * 3));
        float ra = 0.55F + 0.05F * Mth.sin(t * 0.2F);
        Geo.plan(ps, buf.getBuffer(HALO_RT), -ra, -ra, ra, ra, UV, 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(0.45F, c));
        ps.popPose();
        // halo et icone face a la camera
        ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
        ps.mulPose(Axis.ZP.rotationDegrees(8 * Mth.sin(t * 0.09F)));
        float h = 0.75F + 0.08F * Mth.sin(t * 0.25F);
        Geo.plan(ps, buf.getBuffer(HALO_RT), -h, -h, h, h, UV, 0xF000F0, OverlayTexture.NO_OVERLAY, Geo.additif(0.6F, c));
        Geo.plan(ps, buf.getBuffer(HALO_RT), -h * 0.45F, -h * 0.45F, h * 0.45F, h * 0.45F, UV, 0xF000F0, OverlayTexture.NO_OVERLAY,
                Geo.additif(0.75F, 0xFFFFFF));
        ps.translate(0, 0, 0.01F);
        float i = 0.32F;
        Geo.plan(ps, buf.getBuffer(ICONES_RT[type.ordinal()]), -i, -i, i, i, UV, 0xF000F0, OverlayTexture.NO_OVERLAY,
                argb(255, Math.min(255, r + 60), Math.min(255, g + 60), Math.min(255, bl + 60)));
        ps.popPose();
        super.render(b, lacet, partiel, ps, buf, lumiere);
    }

    private static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
