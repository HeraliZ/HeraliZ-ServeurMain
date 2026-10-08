package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocEasterEgg;
import fr.heralisz.matchs.zombies.contenu.bloc.EasterBE;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesDecor.EtatEtape;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * Etape d'easter egg ({@link EasterBE}) : radio, interrupteur ou fragment lumineux (modeles {@code easter_*} generes par
 * {@code tools/matchs/zombies/gen_divers.py}). Voir la Javadoc du bloc-entite pour les etats.
 */
public class EasterRendu implements BlockEntityRenderer<EasterBE> {
    private static final String[] MODELES = {"easter_radio", "easter_interrupteur", "easter_objet"};
    private static final RenderType HALO = ZbTypesRendu.lueur(HeraliszMatchs.id("textures/entity/zombies/halo.png"));
    private static final float[] PLEIN = {0, 0, 1, 1};
    private static ModelesZb.Tex tex;

    public EasterRendu(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(EasterBE be, float partiel, PoseStack ps, MultiBufferSource buf, int lumiere, int overlay) {
        int genre = be.getBlockState().hasProperty(BlocEasterEgg.GENRE) ? be.getBlockState().getValue(BlocEasterEgg.GENRE) : 0;
        ModeleZb m = ModelesZb.get(MODELES[genre]);
        if (m == null) return;
        if (tex == null) tex = ModelesZb.tex(m.texture);
        Direction f = be.getBlockState().hasProperty(BlocEasterEgg.FACING) ? be.getBlockState().getValue(BlocEasterEgg.FACING) : Direction.NORTH;
        float age = (be.getLevel() == null ? 0 : be.getLevel().getGameTime() % 24000L) + partiel;
        float t = be.ecoule(partiel);
        EtatEtape etat = be.etat();
        boolean fin = etat == EtatEtape.TERMINE;
        float pulse = fin ? 0.7F + 0.3F * Mth.sin(age * 0.4F) : 0;
        ps.pushPose();
        ps.translate(0.5F, 0, 0.5F);
        ps.mulPose(Axis.YP.rotationDegrees(-f.toYRot()));
        // tampons pris l'un apres l'autre (base, puis lueur, puis halo) : jamais entrelaces
        int plein = 0xF000F0;
        switch (genre) {
            case 0 -> {
                // radio : cadran qui gresille (cachee), haut-parleur qui « parle » (trouvee)
                boolean parle = be.trouvee() || fin;
                float gres = etat == EtatEtape.REFUSEE && t < 20 ? (Mth.sin(t * 3.1F) > 0 ? 1 : 0.1F) : 0;
                float cadran = parle ? 0.95F : 0.35F + 0.25F * Math.max(0, Mth.sin(age * 0.9F) * Mth.sin(age * 0.37F));
                float hp = parle ? (t < 160 || fin ? 0.4F + 0.6F * Math.abs(Mth.sin(age * 0.55F)) : 0.3F) : 0;
                VertexConsumer vb = buf.getBuffer(tex.base());
                for (ModeleZb.Groupe g : m.groupes()) ModeleZb.dessiner(g, ModeleZb.BASE, ps, vb, lumiere, overlay, 0xFFFFFFFF);
                VertexConsumer vl = buf.getBuffer(tex.lueur());
                ModeleZb.dessiner(m.groupe("cadran"), ModeleZb.LUEUR, ps, vl, plein, OverlayTexture.NO_OVERLAY, Geo.additif(Math.max(Math.max(cadran, gres), pulse), 0xFFFFFF));
                ModeleZb.dessiner(m.groupe("haut_parleur"), ModeleZb.LUEUR, ps, vl, plein, OverlayTexture.NO_OVERLAY, Geo.additif(Math.max(hp, pulse), 0xFFFFFF));
            }
            case 1 -> {
                // interrupteur : levier qui s'abaisse avec rebond, voyant rouge -> vert
                boolean bas = be.trouvee() || fin;
                float p = bas ? Mth.clamp(t / 8F, 0, 1) : 0;
                float e = bas ? 1 + 2.2F * (float) Math.pow(p - 1, 3) + 1.2F * (float) Math.pow(p - 1, 2) : 0;
                VertexConsumer vb = buf.getBuffer(tex.base());
                for (ModeleZb.Groupe g : m.groupes()) {
                    ps.pushPose();
                    if ("levier".equals(g.nom)) ModeleZb.tourner(ps, g, Axis.XP, 150 * e);
                    ModeleZb.dessiner(g, ModeleZb.BASE, ps, vb, lumiere, overlay, 0xFFFFFFFF);
                    ps.popPose();
                }
                float rouge = bas ? 0.05F : 0.55F + 0.45F * Math.max(0, Mth.sin(age * 0.15F));
                if (etat == EtatEtape.REFUSEE && t < 20) rouge = Mth.sin(t * 2.5F) > 0 ? 1 : 0.1F;
                VertexConsumer vl = buf.getBuffer(tex.lueur());
                ModeleZb.dessiner(m.groupe("voyant_rouge"), ModeleZb.LUEUR, ps, vl, plein, OverlayTexture.NO_OVERLAY, Geo.additif(rouge, 0xFFFFFF));
                ModeleZb.dessiner(m.groupe("voyant_vert"), ModeleZb.LUEUR, ps, vl, plein, OverlayTexture.NO_OVERLAY,
                        Geo.additif(bas ? Math.max(0.9F, pulse) : 0.05F, 0xFFFFFF));
            }
            default -> {
                // fragment : veines qui pulsent ; trouve = s'eleve en tournant et se dissout
                float monte = be.trouvee() || fin ? Mth.clamp(t / 16F, 0, 1) : 0;
                ModeleZb.dessiner(m.groupe("corps"), ModeleZb.BASE, ps, buf.getBuffer(tex.base()), lumiere, overlay, 0xFFFFFFFF);
                if (monte < 1) {
                    ModeleZb.Groupe g = m.groupe("pierre");
                    for (int passe = 0; passe < 2 && g != null; passe++) {
                        ps.pushPose();
                        ps.translate(0, monte * 0.6F + 0.02F * Mth.sin(age * 0.1F), 0);
                        ModeleZb.tourner(ps, g, Axis.YP, age * 1.5F + monte * 360);
                        float s = 1 - monte;
                        ps.translate(g.px, g.py, g.pz);
                        ps.scale(s, s, s);
                        ps.translate(-g.px, -g.py, -g.pz);
                        if (passe == 0) ModeleZb.dessiner(g, ModeleZb.BASE, ps, buf.getBuffer(tex.base()), lumiere, overlay, 0xFFFFFFFF);
                        else ModeleZb.dessiner(g, ModeleZb.LUEUR, ps, buf.getBuffer(tex.lueur()), plein, OverlayTexture.NO_OVERLAY,
                                Geo.additif(0.55F + 0.45F * Mth.sin(age * 0.12F) + monte, 0xFFFFFF));
                        ps.popPose();
                    }
                    ps.pushPose();
                    ps.translate(0, 0.3F, 0);
                    ps.mulPose(Axis.YP.rotationDegrees(age * 2));
                    Geo.plan(ps, buf.getBuffer(HALO), -0.45F, -0.3F, 0.45F, 0.45F, PLEIN, plein, OverlayTexture.NO_OVERLAY,
                            Geo.additif(0.25F + 0.15F * Mth.sin(age * 0.12F), 0x6CFFB0));
                    ps.popPose();
                }
            }
        }
        ps.popPose();
    }
}
