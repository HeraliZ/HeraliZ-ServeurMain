package fr.heralisz.utils.train;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Panneaux de gare. « horaire » : boîtier de tôle rouillée sur deux poteaux, nom de la gare (latin, cyrillique),
 * direction, et l'attente du prochain train en diodes orangées, lisible des deux côtés — calculée sur l'horaire du
 * client (même temps du monde pour tous, rien à envoyer). « nom » : plaque émaillée blanche à liseré bleu.
 */
public class TableauRenderer implements BlockEntityRenderer<TableauBlockEntity> {
    private static final ResourceLocation TEX = new ResourceLocation(HeraliszUtils.MODID, "textures/entity/train/tableau.png");
    private static final int AMBRE = 0xFFFFA42A, CREME = 0xFFE8E0C8, GRIS = 0xFFA8A49A, BLEU = 0xFF1C2E5A;

    public TableauRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(TableauBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        Direction f = be.getBlockState().getValue(TableauBlock.FACING);
        TableauBlock.Genre g = be.getBlockState().getValue(TableauBlock.GENRE);
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180f - f.toYRot()));            // face lisible vers -Z du repère
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutout(TEX));
        Horaire h = Horaire.get();
        int k = be.gare();
        Reseau.Gare gare = h != null && k >= 0 ? h.reseau.gares[k] : null;
        if (g == TableauBlock.Genre.HORAIRE) {
            for (float px : new float[]{-1.0f, 1.0f}) boite(ps, vc, px - 0.05f, 0, -0.05f, px + 0.05f, 2.95f, 0.05f, 64, 32, 80, 48, light);
            boite(ps, vc, -1.25f, 1.9f, -0.12f, 1.25f, 3.15f, 0.12f, 64, 0, 128, 32, light);       // boîtier
            face(ps, vc, -1.25f, 1.9f, 1.25f, 3.15f, -0.121f, 0, 0, 64, 32, light, true);           // écran avant
            face(ps, vc, -1.25f, 1.9f, 1.25f, 3.15f, 0.121f, 0, 0, 64, 32, light, false);           // écran arrière
            boite(ps, vc, -1.35f, 3.15f, -0.2f, 1.35f, 3.21f, 0.2f, 64, 0, 128, 8, light);          // auvent
            if (gare != null) {
                String[] l = lignes(h, k);
                for (boolean avant : new boolean[]{true, false}) {
                    texte(ps, buf, gare.nom(), 0, 3.06f, 0.026f, CREME, avant);
                    texte(ps, buf, gare.cyr(), 0, 2.79f, 0.014f, GRIS, avant);
                    texte(ps, buf, l[0], 0, 2.60f, 0.010f, GRIS, avant);
                    texte(ps, buf, l[1], 0, 2.43f, 0.020f, AMBRE, avant);
                    texte(ps, buf, l[2], 0, 2.14f, 0.011f, AMBRE, avant);
                }
            }
        } else {
            for (float px : new float[]{-1.45f, 1.45f}) boite(ps, vc, px - 0.05f, 0, -0.05f, px + 0.05f, 2.75f, 0.05f, 64, 32, 80, 48, light);
            boite(ps, vc, -1.7f, 2.0f, -0.035f, 1.7f, 2.75f, 0.035f, 0, 32, 64, 46, light);
            face(ps, vc, -1.7f, 2.0f, 1.7f, 2.75f, -0.036f, 0, 32, 64, 46, light, true);
            face(ps, vc, -1.7f, 2.0f, 1.7f, 2.75f, 0.036f, 0, 32, 64, 46, light, false);
            if (gare != null) {
                for (boolean avant : new boolean[]{true, false}) {
                    texte(ps, buf, gare.nom(), 0, 2.66f, 0.026f, BLEU, avant);
                    texte(ps, buf, gare.cyr(), 0, 2.29f, 0.013f, BLEU, avant);
                }
            }
        }
        ps.popPose();
    }

    /** Les trois lignes du bas : direction, attente (grosses diodes), précision. */
    static String[] lignes(Horaire h, int k) {
        Horaire.Etat e = h.etat(TrainClient.temps(Minecraft.getInstance().getFrameTime()));
        Reseau r = h.reseau;
        String suivante = r.gares[(k + 1) % r.gares.length].nom();
        String dir = "DIRECTION " + suivante;
        if (e.aQuai() == k) {
            int s = (int) Math.ceil(e.reste());
            String dep = String.format("%d:%02d", s / 60, s % 60);
            return new String[]{dir, "À QUAI", "DÉPART DANS " + dep};
        }
        double a = h.attente(k, e);
        if (a < 30) return new String[]{dir, "TRAIN EN APPROCHE", "ÉLOIGNEZ-VOUS DE LA VOIE"};
        int m = (int) Math.ceil(a / 60);
        return new String[]{dir, "PROCHAIN TRAIN", "DANS " + m + " MIN"};
    }

    private static void texte(PoseStack ps, MultiBufferSource buf, String t, float x, float y, float echelle, int coul, boolean avant) {
        Font font = Minecraft.getInstance().font;
        ps.pushPose();
        ps.translate(x, y, avant ? -0.13f : 0.13f);
        if (avant) ps.mulPose(Axis.YP.rotationDegrees(180f));
        ps.scale(echelle, -echelle, echelle);
        font.drawInBatch(t, -font.width(t) / 2f, 0, coul, false, ps.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0,
                LightTexture.FULL_BRIGHT);
        ps.popPose();
    }

    private static void sommet(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int light,
                               float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u / 128f, v / 128f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n, nx, ny, nz).endVertex();
    }

    /** Face plane parallèle à XY en z (vers -Z si avant), texture (u0, v0) - (u1, v1) en pixels de 128. */
    private static void face(PoseStack ps, VertexConsumer vc, float x0, float y0, float x1, float y1, float z, float u0, float v0,
                             float u1, float v1, int light, boolean avant) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        if (avant) {
            sommet(vc, m, n, x1, y0, z, u0, v1, light, 0, 0, -1);
            sommet(vc, m, n, x0, y0, z, u1, v1, light, 0, 0, -1);
            sommet(vc, m, n, x0, y1, z, u1, v0, light, 0, 0, -1);
            sommet(vc, m, n, x1, y1, z, u0, v0, light, 0, 0, -1);
        } else {
            sommet(vc, m, n, x0, y0, z, u0, v1, light, 0, 0, 1);
            sommet(vc, m, n, x1, y0, z, u1, v1, light, 0, 0, 1);
            sommet(vc, m, n, x1, y1, z, u1, v0, light, 0, 0, 1);
            sommet(vc, m, n, x0, y1, z, u0, v0, light, 0, 0, 1);
        }
    }

    /** Pavé (côtés et dessus/dessous) d'une même zone de texture. */
    private static void boite(PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                              float u0, float v0, float u1, float v1, int light) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        // -X, +X
        sommet(vc, m, n, x0, y0, z1, u0, v1, light, -1, 0, 0);
        sommet(vc, m, n, x0, y0, z0, u1, v1, light, -1, 0, 0);
        sommet(vc, m, n, x0, y1, z0, u1, v0, light, -1, 0, 0);
        sommet(vc, m, n, x0, y1, z1, u0, v0, light, -1, 0, 0);
        sommet(vc, m, n, x1, y0, z0, u0, v1, light, 1, 0, 0);
        sommet(vc, m, n, x1, y0, z1, u1, v1, light, 1, 0, 0);
        sommet(vc, m, n, x1, y1, z1, u1, v0, light, 1, 0, 0);
        sommet(vc, m, n, x1, y1, z0, u0, v0, light, 1, 0, 0);
        // -Y, +Y
        sommet(vc, m, n, x0, y0, z0, u0, v0, light, 0, -1, 0);
        sommet(vc, m, n, x1, y0, z0, u1, v0, light, 0, -1, 0);
        sommet(vc, m, n, x1, y0, z1, u1, v1, light, 0, -1, 0);
        sommet(vc, m, n, x0, y0, z1, u0, v1, light, 0, -1, 0);
        sommet(vc, m, n, x0, y1, z1, u0, v1, light, 0, 1, 0);
        sommet(vc, m, n, x1, y1, z1, u1, v1, light, 0, 1, 0);
        sommet(vc, m, n, x1, y1, z0, u1, v0, light, 0, 1, 0);
        sommet(vc, m, n, x0, y1, z0, u0, v0, light, 0, 1, 0);
        // -Z, +Z
        face(ps, vc, x0, y0, x1, y1, z0, u0, v0, u1, v1, light, true);
        face(ps, vc, x0, y0, x1, y1, z1, u0, v0, u1, v1, light, false);
    }
}
