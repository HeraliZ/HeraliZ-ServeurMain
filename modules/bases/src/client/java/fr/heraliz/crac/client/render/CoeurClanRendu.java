package fr.heraliz.crac.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heraliz.crac.block.CoeurClanBlock;
import fr.heraliz.crac.block.entity.CoeurClanBlockEntity;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * Cœur de clan (modèle validé le 22/10) : par-dessus le modèle fixe du bloc (socle et bras), les filets lumineux en
 * pleine lumière, le cristal qui tourne et flotte, les points du clan sur la plaque du socle et, au-dessus, l'hologramme
 * des rangs (classement PVE, PVP, events, général ; 4 s chacun), tourné vers le joueur. Léger : quelques centaines de
 * faces déjà prêtes, rien au-delà de {@link #PORTEE} blocs, textes seulement à {@link #PORTEE_TEXTE}.
 */
public class CoeurClanRendu implements BlockEntityRenderer<CoeurClanBlockEntity> {
    public static final ResourceLocation LUEUR = new ResourceLocation("crac", "block/coeur_clan_lueur");
    public static final ResourceLocation CRISTAL = new ResourceLocation("crac", "block/coeur_clan_cristal");
    private static final double PORTEE = 48.0, PORTEE_TEXTE = 24.0;
    private static final int PLEIN_JOUR = 0xF000F0;
    private static final int CYAN = 0x5CE6FF, CYAN_PALE = 0x2C9CB8;
    /** Tour complet du cristal (ticks) et durée de chaque écran de l'hologramme. */
    private static final float TOUR = 160F;
    private static final int ECRAN = 80, FONDU = 6;
    private static final String[] TITRES = {"crac.coeur.holo.pve", "crac.coeur.holo.pvp", "crac.coeur.holo.events", "crac.coeur.holo.general"};
    /** Largeur de l'hologramme en unités de texte. */
    private static final float HOLO_UNITES = 110F;

    /** Graine fixe des quads (le modèle OBJ n'en dépend pas) : plus un objet neuf par image et par cœur. */
    private static final RandomSource ALEA = RandomSource.create(42L);

    private final Font font;

    public CoeurClanRendu(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(CoeurClanBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (be.getLevel() == null || !(be.getBlockState().getBlock() instanceof CoeurClanBlock)) {
            return;
        }
        double d2 = mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(Vec3.atCenterOf(be.getBlockPos()));
        if (d2 > PORTEE * PORTEE) {
            return;
        }
        float t = be.getLevel().getGameTime() + partial;
        Direction f = be.getBlockState().getValue(CoeurClanBlock.FACING);
        VertexConsumer vc = buffers.getBuffer(Sheets.cutoutBlockSheet());

        // filets lumineux, tournés comme le bloc
        ps.pushPose();
        tourner(ps, f);
        dessiner(mc, LUEUR, ps, vc);
        ps.popPose();

        // cristal : tourne sur lui-même et flotte d'un demi-pixel
        ps.pushPose();
        float cy = CoeurClanDonnees.CRISTAL_Y / 16F + Mth.sin(t * 0.06F) * 0.5F / 16F;
        ps.translate(0.5, cy, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees((t % TOUR) / TOUR * 360F));
        ps.translate(-0.5, -CoeurClanDonnees.CRISTAL_Y / 16F, -0.5);
        dessiner(mc, CRISTAL, ps, vc);
        ps.popPose();

        if (d2 > PORTEE_TEXTE * PORTEE_TEXTE) {
            return;
        }
        this.points(be, f, ps, buffers);
        this.hologramme(be, t, ps, buffers, mc);
    }

    private static void dessiner(Minecraft mc, ResourceLocation rl, PoseStack ps, VertexConsumer vc) {
        BakedModel m = mc.getModelManager().getModel(rl);
        List<BakedQuad> quads = m.getQuads(null, null, ALEA, ModelData.EMPTY, null);
        for (BakedQuad q : quads) {
            vc.putBulkData(ps.last(), q, 1F, 1F, 1F, PLEIN_JOUR, OverlayTexture.NO_OVERLAY);
        }
    }

    private static void tourner(PoseStack ps, Direction f) {
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-switch (f) {
            case EAST -> 90F;
            case SOUTH -> 180F;
            case WEST -> 270F;
            default -> 0F;
        }));
        ps.translate(-0.5, 0, -0.5);
    }

    /** Points du clan sur la plaque de l'avant du socle. */
    private void points(CoeurClanBlockEntity be, Direction f, PoseStack ps, MultiBufferSource buffers) {
        ps.pushPose();
        tourner(ps, f);
        float larg = (CoeurClanDonnees.PLAQUE_X1 - CoeurClanDonnees.PLAQUE_X0) / 16F;
        float haut = (CoeurClanDonnees.PLAQUE_Y1 - CoeurClanDonnees.PLAQUE_Y0) / 16F;
        float k = haut / 11F;                                    // une ligne de police (9) et un peu d'air
        ps.translate((CoeurClanDonnees.PLAQUE_X0 + CoeurClanDonnees.PLAQUE_X1) / 32F, CoeurClanDonnees.PLAQUE_Y1 / 16F,
            (CoeurClanDonnees.PLAQUE_Z - 0.03F) / 16F);
        ps.mulPose(Axis.YP.rotationDegrees(180));
        ps.scale(k, -k, k);
        String txt = Component.translatable("crac.coeur.points", espaces(be.points())).getString();
        float w = Math.min(this.font.width(txt), larg / k);
        this.font.drawInBatch(txt, -w / 2F, 1.5F, 0xFF000000 | CYAN, false, ps.last().pose(), buffers,
            Font.DisplayMode.POLYGON_OFFSET, 0, PLEIN_JOUR);
        ps.popPose();
    }

    /** Hologramme : cadre fin, titre du classement, rang en gros ; fondu entre deux écrans. */
    private void hologramme(CoeurClanBlockEntity be, float t, PoseStack ps, MultiBufferSource buffers, Minecraft mc) {
        int n = (int) (t / ECRAN) % TITRES.length;
        float dansEcran = t % ECRAN;
        float alpha = Mth.clamp(Math.min(dansEcran, ECRAN - dansEcran) / FONDU, 0F, 1F);
        // léger scintillement
        alpha *= 0.85F + 0.15F * Mth.sin(t * 0.9F + be.getBlockPos().getX());
        if (alpha <= 0.02F) {
            return;
        }
        ps.pushPose();
        ps.translate(0.5, CoeurClanDonnees.HOLO_Y / 16F, 0.5);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float k = CoeurClanDonnees.HOLO_LARGEUR / 16F / HOLO_UNITES;
        ps.scale(-k, -k, k);
        float w = HOLO_UNITES, h = CoeurClanDonnees.HOLO_HAUTEUR / 16F / k;
        Matrix4f m = ps.last().pose();
        int a = (int) (alpha * 255F);
        // fond très léger et cadre
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        rect(vc, m, -w / 2, -h / 2, w / 2, h / 2, 0x0E3A48, (int) (a * 0.25F));
        float e = 0.9F;
        rect(vc, m, -w / 2, -h / 2, w / 2, -h / 2 + e, CYAN_PALE, a);
        rect(vc, m, -w / 2, h / 2 - e, w / 2, h / 2, CYAN_PALE, a);
        rect(vc, m, -w / 2, -h / 2, -w / 2 + e, h / 2, CYAN_PALE, a);
        rect(vc, m, w / 2 - e, -h / 2, w / 2, h / 2, CYAN_PALE, a);
        for (float sx : new float[]{-w / 2, w / 2 - 9}) {                         // coins renforcés
            rect(vc, m, sx, -h / 2, sx + 9, -h / 2 + 2 * e, CYAN, a);
            rect(vc, m, sx, h / 2 - 2 * e, sx + 9, h / 2, CYAN, a);
        }
        String titre = Component.translatable(TITRES[n]).getString();
        int rang = be.rang(n);
        String ligne = rang > 0 ? Component.translatable("crac.coeur.holo.rang", rang).getString()
            : Component.translatable("crac.coeur.holo.non_classe").getString();
        int ca = Math.max(4, a) << 24;
        ps.pushPose();
        ps.translate(0, 0, -0.05F);
        this.font.drawInBatch(titre, -this.font.width(titre) / 2F, -h / 2 + 4F, ca | CYAN_PALE, false, ps.last().pose(), buffers,
            Font.DisplayMode.SEE_THROUGH, 0, PLEIN_JOUR);
        float g = 2.2F;                                                            // rang en gros
        ps.scale(g, g, 1F);
        float lw = Math.min(this.font.width(ligne), (w - 8) / g);
        this.font.drawInBatch(ligne, -lw / 2F, (-h / 2 + 15F) / g, ca | CYAN, false, ps.last().pose(), buffers,
            Font.DisplayMode.SEE_THROUGH, 0, PLEIN_JOUR);
        ps.popPose();
        ps.popPose();
    }

    private static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int rgb, int a) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        vc.vertex(m, x0, y0, 0).color(r, g, b, a).endVertex();
        vc.vertex(m, x0, y1, 0).color(r, g, b, a).endVertex();
        vc.vertex(m, x1, y1, 0).color(r, g, b, a).endVertex();
        vc.vertex(m, x1, y0, 0).color(r, g, b, a).endVertex();
        // les deux faces : visible quel que soit le sens du dessin
        vc.vertex(m, x1, y0, 0).color(r, g, b, a).endVertex();
        vc.vertex(m, x1, y1, 0).color(r, g, b, a).endVertex();
        vc.vertex(m, x0, y1, 0).color(r, g, b, a).endVertex();
        vc.vertex(m, x0, y0, 0).color(r, g, b, a).endVertex();
    }

    /** 12450 -> « 12 450 ». */
    private static String espaces(long n) {
        return String.format(Locale.ROOT, "%,d", n).replace(',', ' ');
    }

    /**
     * 25/10 (perf) : plus de rendu hors du champ de vision. La boîte de rendu du bloc
     * ({@link CoeurClanBlockEntity#getRenderBoundingBox}) couvre déjà le cristal et l'hologramme ; avec {@code true}, chaque
     * cœur à moins de 48 blocs était dessiné même dans le dos du joueur.
     */
    @Override
    public boolean shouldRenderOffScreen(CoeurClanBlockEntity be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return (int) PORTEE;
    }
}
