package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.contenu.bloc.ObstacleBE;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/**
 * Element d'ouverture payant ({@link ObstacleBE}) : modele {@code obstacle_<type>_<L>x<H>} (genere par
 * {@code tools/matchs/zombies/gen_obstacles.py} pour 1 a 5 blocs de large et 2 a 4 de haut ; autre taille = modele le
 * plus proche etire), et pancarte de prix : ardoise suspendue, chiffres a la craie ({@code craie_prix.png}), eclairee
 * au minimum comme une craie qui accroche la lumiere (lisible dans le noir). Sans position de pancarte donnee par la
 * carte, une ardoise est accrochee sur chaque face de l'element, un peu sous les yeux.
 * Les methodes statiques servent aussi a l'animation d'ouverture ({@link OuvertureRendu}).
 */
public class ObstacleRendu implements BlockEntityRenderer<ObstacleBE> {
    private static final ResourceLocation CRAIE = HeraliszMatchs.id("textures/entity/zombies/v2/craie_prix.png");
    private static final RenderType CRAIE_RT = RenderType.entityTranslucent(CRAIE);
    private static final String[] TYPES = {"porte", "grille", "debris"};
    private static final ModelesZb.Tex[] TEX = new ModelesZb.Tex[3];
    private static final String[][][] NOMS = new String[3][6][5];
    /** Chiffres du prix (tampon reutilise). */
    private static final int[] CHIFFRES = new int[8];

    public ObstacleRendu(BlockEntityRendererProvider.Context ctx) {
    }

    static int indice(String type) {
        return "grille".equals(type) ? 1 : "debris".equals(type) ? 2 : 0;
    }

    static ModelesZb.Tex tex(int i) {
        if (TEX[i] == null) TEX[i] = ModelesZb.tex(HeraliszMatchs.id("textures/entity/zombies/v2/obstacle_" + TYPES[i] + ".png"));
        return TEX[i];
    }

    /** Modele genere le plus proche de la taille demandee (largeur 1..5, hauteur 2..4). */
    @Nullable
    static ModeleZb modele(int i, int l, int h) {
        int lm = Mth.clamp(l, 1, 5), hm = Mth.clamp(h, 2, 4);
        String nom = NOMS[i][lm][hm];
        if (nom == null) {
            nom = "obstacle_" + TYPES[i] + "_" + lm + "x" + hm;
            NOMS[i][lm][hm] = nom;
        }
        return ModelesZb.get(nom);
    }

    /** Facteurs d'etirement du modele (taille reelle / taille du modele). */
    static float etirementX(int l) {
        return l / (float) Mth.clamp(l, 1, 5);
    }

    static float etirementY(int h) {
        return h / (float) Mth.clamp(h, 2, 4);
    }

    /** Distance (px) de l'ardoise par defaut devant le plan median, selon le type. */
    static float avant(int i) {
        return i == 0 ? 4.9F : i == 1 ? 3.7F : 8.7F;
    }

    @Override
    public void render(ObstacleBE be, float partiel, PoseStack ps, MultiBufferSource buf, int lumiere, int overlay) {
        int i = indice(be.type());
        ModeleZb m = modele(i, be.largeur(), be.hauteur());
        ps.pushPose();
        ps.translate(be.etendueX() / 2F, 0, be.etendueZ() / 2F);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-be.normale().toYRot()));
        if (m != null) {
            ps.pushPose();
            ps.scale(etirementX(be.largeur()), etirementY(be.hauteur()), i == 2 ? be.profondeur() : 1);
            VertexConsumer vb = buf.getBuffer(tex(i).base());
            for (ModeleZb.Groupe g : m.groupes()) {
                if (!be.cadre() && "cadre".equals(g.nom)) continue;
                ModeleZb.dessiner(g, ModeleZb.BASE, ps, vb, lumiere, overlay, 0xFFFFFFFF);
            }
            ps.popPose();
        }
        if (be.pancartes() == 0 && be.cout() > 0) {
            // une ardoise sur chaque face, un peu sous la hauteur des yeux
            float bas = Math.min(18.5F, be.hauteur() * 16 - 14) / 16F;
            float z = (avant(i) + (i == 2 ? (be.profondeur() - 1) * 8 : 0)) / 16F;
            for (int face = 0; face < 2; face++) {
                ps.pushPose();
                if (face == 1) ps.mulPose(Axis.YP.rotationDegrees(180));
                ps.translate(0, bas, z);
                pancarte(ps, buf, lumiere, be.cout());
                ps.popPose();
            }
        }
        ps.popPose();
        if (be.cout() > 0) {
            for (int k = 0; k < be.pancartes(); k++) {
                ps.pushPose();
                ps.translate(be.px(k), be.py(k), be.pz(k));
                ps.mulPose(Axis.YP.rotationDegrees(-be.pyaw(k)));
                pancarte(ps, buf, lumiere, be.cout());
                ps.popPose();
            }
        }
        ps.popPose();
    }

    /**
     * Ardoise et prix a la craie ; origine = centre bas de l'ardoise, dos contre le support (z = 0), face vers +z.
     * Lumiere relevee (la craie reste lisible dans le noir).
     */
    public static void pancarte(PoseStack ps, MultiBufferSource buf, int lumiere, int prix) {
        int l = LightTexture.pack(Math.max(LightTexture.block(lumiere), 11), LightTexture.sky(lumiere));
        ModeleZb m = ModelesZb.get("pancarte");
        if (m != null) {
            VertexConsumer vb = buf.getBuffer(ModelesZb.tex(m.texture).base());
            for (ModeleZb.Groupe g : m.groupes()) ModeleZb.dessiner(g, ModeleZb.BASE, ps, vb, l, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
        int n = 0;
        int v = Math.max(0, prix);
        do {
            CHIFFRES[n++] = v % 10;
            v /= 10;
        } while (v > 0 && n < CHIFFRES.length);
        float h = 6.4F, w = 4.2F, pas = w * 0.82F;
        float total = n * pas + (w - pas);
        float s = Math.min(1F, 11.6F / total);
        VertexConsumer vc = buf.getBuffer(CRAIE_RT);
        ps.pushPose();
        ps.translate(0, 4.5F / 16F, 0.66F / 16F);
        ps.scale(s / 16F, s / 16F, 1 / 16F);
        float x = -total / 2F;
        for (int k = n - 1; k >= 0; k--) {
            int d = CHIFFRES[k];
            float[] uv = UV[d];
            Geo.plan(ps, vc, x, -h / 2, x + w, h / 2, uv, l, OverlayTexture.NO_OVERLAY, 0xFFF4F2EA);
            x += pas;
        }
        ps.popPose();
    }

    private static final float[][] UV = new float[10][];

    static {
        for (int d = 0; d < 10; d++) UV[d] = Geo.uv(d * 64, 0, 64, 96, 640, 96);
    }

    @Override
    public boolean shouldRenderOffScreen(ObstacleBE be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
