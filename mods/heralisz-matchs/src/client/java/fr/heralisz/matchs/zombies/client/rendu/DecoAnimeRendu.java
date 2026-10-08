package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocDecoAnime;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocDecoGiant;
import fr.heralisz.matchs.zombies.contenu.bloc.DecoAnimeBE;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Parties animees des decors de The Giant ({@link DecoAnimeBE}) : modeles {@code zbmodeles/zb_deco_levier_piege.json} et
 * {@code zb_deco_porte_confinement.json} (generes par {@code gen_decor_giant.py}, repere de la case maitresse), dessines
 * par-dessus le corps cuit. Levier : poignee (pivot, axe X, +128 deg actionne), voyants vert / rouge. Porte : battants qui
 * coulissent de 25,2 px chacun (depart lent, 2,5 s), hublots verts, voyants. Halos {@code bb_*} tournes vers la camera.
 * Rien au-dela de 32 blocs (porte : 48) ; aucune allocation par image.
 */
public class DecoAnimeRendu implements BlockEntityRenderer<DecoAnimeBE> {
    private static final int PLEIN = 0xF000F0, BLANC = 0xFFFFFF;
    private static final float PX = 1 / 16F;
    private static final Quaternionf Q = new Quaternionf(), QC = new Quaternionf();

    public DecoAnimeRendu(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(DecoAnimeBE be, float partiel, PoseStack ps, MultiBufferSource buf, int lumiere, int overlay) {
        if (be.getLevel() == null) return;
        BlockState s = be.getBlockState();
        if (!(s.getBlock() instanceof BlocDecoAnime bloc)) return;
        boolean porte = bloc.genre == BlocDecoAnime.PORTE;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double portee = porte ? 48 : 32;
        if (cam.distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.5, be.getBlockPos().getZ() + 0.5) > portee * portee) return;
        ModeleZb m = ModelesZb.get(porte ? "zb_deco_porte_confinement" : "zb_deco_levier_piege");
        if (m == null) return;
        Direction f = s.getValue(BlocDecoGiant.FACING);
        float age = (be.getLevel().getGameTime() % 24000L) + partiel;
        float t = be.ecoule(partiel);
        ModelesZb.Tex tx = ModelesZb.tex(m.texture);
        ps.pushPose();
        ps.translate(0.5F, 0, 0.5F);
        ps.mulPose(Q.rotationY(-f.toYRot() * Mth.DEG_TO_RAD));
        if (porte) porte(be, m, tx, ps, buf, lumiere, overlay, f, t, age);
        else levier(be, m, tx, ps, buf, lumiere, overlay, f, t, age);
        ps.popPose();
    }

    // ------------------------------------------------------------------ levier du piege
    private static void levier(DecoAnimeBE be, ModeleZb m, ModelesZb.Tex tx, PoseStack ps, MultiBufferSource buf, int l, int o, Direction f,
                               float t, float age) {
        int etat = be.etat();
        float angle = 0, vert = 0, rouge = 0;
        if (etat == DecoAnimeBE.EN_MARCHE) {
            float fin = be.duree() + be.recharge();
            if (t < fin) {
                // abaisse en 6 ticks (petit rebond), reste en bas pendant le piege et la recharge
                angle = t < 6 ? 128F * doux(t / 6F) : 128F - 3F * Mth.sin(Mth.clamp((t - 6) / 4F, 0, 1) * Mth.PI);
                boolean recharge = t >= be.duree();
                rouge = recharge ? (Mth.sin(age * 0.65F) > 0 ? 1 : 0.15F) : 1;
            } else {
                // fin de la recharge : la poignee remonte en 10 ticks, voyant vert
                float r = Mth.clamp((t - fin) / 10F, 0, 1);
                angle = 128F * (1 - doux(r));
                vert = r;
            }
        } else if (etat == DecoAnimeBE.DISPONIBLE) {
            vert = 1;
        }
        VertexConsumer vb = buf.getBuffer(tx.base());
        ModeleZb.Groupe g = m.groupe("poignee");
        ps.pushPose();
        if (g != null && angle != 0) {
            ps.translate(g.px, g.py, g.pz);
            ps.mulPose(Q.rotationX(angle * Mth.DEG_TO_RAD));
            ps.translate(-g.px, -g.py, -g.pz);
        }
        ModeleZb.dessiner(g, ModeleZb.BASE, ps, vb, l, o, 0xFFFFFFFF);
        ps.popPose();
        VertexConsumer vl = buf.getBuffer(tx.lueur());
        lueur(m, "lampe_verte", ps, vl, vert);
        lueur(m, "lampe_rouge", ps, vl, rouge);
        halo(m, "bb_halo_verte", ps, vl, vert * 0.85F, f);
        halo(m, "bb_halo_rouge", ps, vl, rouge * 0.85F, f);
    }

    // ------------------------------------------------------------------ porte de confinement
    private static void porte(DecoAnimeBE be, ModeleZb m, ModelesZb.Tex tx, PoseStack ps, MultiBufferSource buf, int l, int o, Direction f,
                              float t, float age) {
        boolean ouverte = be.etat() == DecoAnimeBE.OUVERTE;
        float p = ouverte ? Mth.clamp(t / DecoAnimeBE.OUVERTURE_TICKS, 0, 1) : 0;
        // depart lent (verins), petite secousse au debut
        float d = p * p * (3 - 2 * p) * 25.2F * PX;
        float secousse = ouverte && t < 6 ? 0.25F * PX * Mth.sin(t * 3.1F) : 0;
        VertexConsumer vb = buf.getBuffer(tx.base());
        VertexConsumer vl = buf.getBuffer(tx.lueur());
        ps.pushPose();
        ps.translate(-d, secousse, 0);
        ModeleZb.dessiner(m.groupe("battant_g"), ModeleZb.BASE, ps, vb, l, o, 0xFFFFFFFF);
        lueur(m, "hublot_g_lueur", ps, vl, 0.6F);
        ps.popPose();
        ps.pushPose();
        ps.translate(d, secousse, 0);
        ModeleZb.dessiner(m.groupe("battant_d"), ModeleZb.BASE, ps, vb, l, o, 0xFFFFFFFF);
        lueur(m, "hublot_d_lueur", ps, vl, 0.6F);
        ps.popPose();
        float rouge = !ouverte ? 1 : p < 1 ? (Mth.sin(age * 0.8F) > 0 ? 1 : 0.2F) : 0;
        float vert = ouverte && p >= 1 ? 1 : 0;
        lueur(m, "lampe_rouge", ps, vl, rouge);
        lueur(m, "lampe_verte", ps, vl, vert);
        halo(m, "bb_halo_rouge", ps, vl, rouge * 0.8F, f);
        halo(m, "bb_halo_verte", ps, vl, vert * 0.8F, f);
    }

    // ------------------------------------------------------------------ outils
    private static float doux(float u) {
        u = Mth.clamp(u, 0, 1);
        return u * u * (3 - 2 * u);
    }

    private static void lueur(ModeleZb m, String g, PoseStack ps, VertexConsumer vl, float k) {
        if (k > 0.01F) ModeleZb.dessiner(m.groupe(g), ModeleZb.LUEUR, ps, vl, PLEIN, OverlayTexture.NO_OVERLAY, Geo.additif(k, BLANC));
    }

    /** Halo {@code bb_*} tourne vers la camera autour de son pivot (meme methode que les machines v3). */
    private static void halo(ModeleZb m, String nom, PoseStack ps, VertexConsumer vl, float k, Direction f) {
        if (k <= 0.01F) return;
        ModeleZb.Groupe g = m.groupe(nom);
        if (g == null) return;
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        ps.pushPose();
        ps.translate(g.px, g.py, g.pz);
        ps.mulPose(Q.rotationY(f.toYRot() * Mth.DEG_TO_RAD));
        ps.mulPose(QC.set(cam.rotation()));
        ps.translate(-g.px, -g.py, -g.pz);
        ModeleZb.dessiner(g, ModeleZb.LUEUR, ps, vl, PLEIN, OverlayTexture.NO_OVERLAY, Geo.additif(k, BLANC));
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(DecoAnimeBE be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
