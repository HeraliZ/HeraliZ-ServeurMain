package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.contenu.entite.OuvertureEntite;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Animation d'ouverture d'un element payant ({@link OuvertureEntite}, 2,2 s), avec le meme modele que l'element ferme :
 * <ul>
 *     <li>porte : planches et barre qui tombent, battants qui s'ouvrent, puis l'ensemble s'enfonce dans le sol ;</li>
 *     <li>grille : chaine, cadenas et panneau qui tombent, battants qui s'ouvrent ;</li>
 *     <li>debris : le tas s'effondre dans le sol en tremblant (poussiere envoyee par le decor).</li>
 * </ul>
 * Les modeles et pivots sont en blocs (voir {@link ModeleZb}).
 */
public class OuvertureRendu extends EntityRenderer<OuvertureEntite> {
    private static final ResourceLocation TEXTURE = HeraliszMatchs.id("textures/entity/zombies/v2/obstacle_porte.png");

    public OuvertureRendu(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    private static float doux(float u) {
        u = Mth.clamp(u, 0, 1);
        return u * u * (3 - 2 * u);
    }

    private static float sortie(float u) {
        u = Mth.clamp(u, 0, 1);
        return 1 - (1 - u) * (1 - u);
    }

    @Override
    public void render(OuvertureEntite e, float lacet, float partiel, PoseStack ps, MultiBufferSource buf, int lumiere) {
        int i = ObstacleRendu.indice(e.type());
        ModeleZb m = ObstacleRendu.modele(i, e.largeur(), e.hauteur());
        if (m == null) return;
        float t = (e.tickCount + partiel) / 20F;
        float h = Math.min(e.hauteur(), 4);
        // enfoncement final dans le sol (porte, grille)
        float fin = i == 2 ? 0 : doux((t - 1.25F) / 0.9F);
        if (fin >= 0.999F) return;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
        ps.scale(ObstacleRendu.etirementX(e.largeur()), ObstacleRendu.etirementY(e.hauteur()), i == 2 ? e.profondeur() : 1);
        ps.translate(0, -h * fin * fin, 0);
        VertexConsumer vb = buf.getBuffer(ObstacleRendu.tex(i).base());
        for (ModeleZb.Groupe g : m.groupes()) {
            if (!e.cadre() && "cadre".equals(g.nom)) continue;
            ps.pushPose();
            if (transformer(ps, g, i, t, h)) ModeleZb.dessiner(g, ModeleZb.BASE, ps, vb, lumiere, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            ps.popPose();
        }
        ps.popPose();
    }

    /** Pose du groupe a l'instant t (secondes) ; faux = groupe deja disparu. Unites : blocs. */
    private static boolean transformer(PoseStack ps, ModeleZb.Groupe g, int type, float t, float h) {
        switch (g.nom) {
            case "planches" -> {
                float u = Mth.clamp(t / 0.7F, 0, 1);
                if (u >= 1) return false;
                ps.translate(0, -h * 0.9F * u * u, 0);
                ps.translate(0, h / 2, 0);
                ps.mulPose(Axis.ZP.rotationDegrees(25 * u));
                ps.translate(0, -h / 2, 0);
            }
            case "chaine", "cadenas", "panneau" -> {
                float d = "panneau".equals(g.nom) ? 0.1F : 0;
                float u = Mth.clamp((t - d) / 0.75F, 0, 1);
                if (u >= 1) return false;
                ps.translate(g.px, g.py - 1.6F * u * u, g.pz);
                ps.mulPose(Axis.XP.rotationDegrees(70 * u));
                ps.mulPose(Axis.ZP.rotationDegrees(("cadenas".equals(g.nom) ? -40 : 30) * u));
                ps.translate(-g.px, -g.py, -g.pz);
            }
            case "battant_g", "battant_d" -> {
                float debut = type == 1 ? 0.4F : 0.15F;
                float a = 100 * sortie((t - debut) / 0.85F);
                float signe = "battant_g".equals(g.nom) ? 1 : -1;
                ps.translate(g.px, g.py, g.pz);
                ps.mulPose(Axis.YP.rotationDegrees(signe * a));
                ps.translate(-g.px, -g.py, -g.pz);
            }
            case "tas" -> {
                float u = doux(t / 1.6F);
                if (u >= 0.999F) return false;
                float tremble = (1 - u) * 0.05F * Mth.sin(t * 60);
                ps.translate(tremble, -h * 1.05F * u, 0);
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    public boolean shouldRender(OuvertureEntite e, Frustum f, double x, double y, double z) {
        return e.distanceToSqr(x, y, z) < 80 * 80;
    }

    @Override
    public ResourceLocation getTextureLocation(OuvertureEntite e) {
        return TEXTURE;
    }
}
