package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.client.modele.RodeurModele;
import fr.heralisz.matchs.zombies.contenu.entite.Rodeur;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Rendu du rodeur : texture ecorchee, yeux orange lumineux, s'affaisse sur le flanc en se dissolvant a la mort. */
public class RodeurRendu extends MobRenderer<Rodeur, RodeurModele> {
    private static final ResourceLocation PEAU = HeraliszMatchs.id("textures/entity/zombies/rodeur.png");
    private static final RenderType YEUX = RenderType.eyes(HeraliszMatchs.id("textures/entity/zombies/rodeur_yeux.png"));

    public RodeurRendu(EntityRendererProvider.Context ctx) {
        super(ctx, new RodeurModele(ctx.bakeLayer(RodeurModele.COUCHE)), 0.6F);
        this.addLayer(new RenderLayer<>(this) {
            @Override
            public void render(PoseStack ps, MultiBufferSource buf, int lumiere, Rodeur r, float marche, float ampl, float partiel,
                               float age, float lacetTete, float tangage) {
                if (r.deathTime > 4) return;
                float e = 0.8F + 0.2F * Mth.sin(age * 0.4F);
                getParentModel().renderToBuffer(ps, buf.getBuffer(YEUX), 0xF00000, OverlayTexture.NO_OVERLAY, e, e, e, 1.0F);
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Rodeur r) {
        return PEAU;
    }

    @Override
    protected void setupRotations(Rodeur r, PoseStack ps, float age, float lacetCorps, float partiel) {
        super.setupRotations(r, ps, age, lacetCorps, partiel);
        if (r.deathTime > 0) {
            float k = Mth.clamp((r.deathTime + partiel) / 14F, 0, 1);
            ps.scale(1 - 0.3F * k, 1 - 0.6F * k, 1 - 0.3F * k);
        }
    }

    @Override
    protected float getFlipDegrees(Rodeur r) {
        return 90.0F;
    }
}
