package fr.heraliz.wonder.wintershowl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;

/**
 * Couche de glace des creatures givrees (ajoutee a tous les rendus de creatures, {@link ClientGivre#couches}) : le modele
 * de la creature est redessine dans sa pose, par-dessus, avec la texture de glace (translucide : voile leger quand elle
 * est ralentie, glace epaisse quand elle est gelee, fissures juste avant l'eclatement), plus un reflet additif qui
 * scintille la nuit. Ne coute qu'une recherche dans une petite table quand la creature n'est pas givree.
 */
final class CoucheGivre<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    CoucheGivre(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int lumiere, T e, float limb, float limbAmt, float partial, float age, float yaw, float pitch) {
        if (EtatsGivre.n == 0) return;
        int i = EtatsGivre.chercher(e.getId());
        if (i < 0) return;
        float now = FxGivre.maintenant() + partial;
        float a = EtatsGivre.opacite(i, now);
        if (a <= 0.01f) return;
        boolean gelee = EtatsGivre.gelee(i, now);
        int f = gelee ? EtatsGivre.fissures(i, now) : 0;
        boolean fury = EtatsGivre.PAP[i] > 0;
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(TypesGivre.COUCHE[f]));
        // la glace renvoie un peu de lumiere meme a l'ombre
        int bloc = Math.max((lumiere & 0xFFFF) >> 4, gelee ? 6 : 3), ciel = (lumiere >> 20) & 0xF;
        int l = (Math.min(15, bloc) << 4) | (ciel << 20);
        getParentModel().renderToBuffer(ps, vc, l, OverlayTexture.NO_OVERLAY, fury ? 0.86f : 0.92f, fury ? 0.92f : 0.97f, 1f, a);
        if (gelee) {
            float k = 0.25f + 0.15f * (float) Math.sin(now * 0.35f + e.getId());
            VertexConsumer g = buf.getBuffer(RenderType.eyes(TypesGivre.COUCHE_LUEUR));
            getParentModel().renderToBuffer(ps, g, 0xF000F0, OverlayTexture.NO_OVERLAY, k * (fury ? 0.8f : 0.6f), k * 0.85f, k, 1f);
        }
    }
}
