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
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Cœur de clan (modèle validé le 22/10) : par-dessus le modèle fixe du bloc (socle et bras), les filets lumineux en
 * pleine lumière, le cristal et les points du clan sur la plaque du socle. Léger : quelques centaines de faces déjà
 * prêtes, rien au-delà de {@link #PORTEE} blocs, points seulement à {@link #PORTEE_TEXTE}.
 * <p>
 * Retours du propriétaire du 03/11 (« plus simple, sans rotation ») : le cristal ne tourne plus et ne flotte plus (il
 * est tourné comme le bloc), et l'hologramme des rangs (qui tournait vers le joueur et changeait toutes les 4 s) n'est
 * plus dessiné : un membre du clan a les rangs et les points par un clic droit sur le cœur (écran du clan, onglet Saison,
 * {@code heraliz/PriseCoeur}).
 */
public class CoeurClanRendu implements BlockEntityRenderer<CoeurClanBlockEntity> {
    public static final ResourceLocation LUEUR = new ResourceLocation("crac", "block/coeur_clan_lueur");
    public static final ResourceLocation CRISTAL = new ResourceLocation("crac", "block/coeur_clan_cristal");
    private static final double PORTEE = 48.0, PORTEE_TEXTE = 24.0;
    private static final int PLEIN_JOUR = 0xF000F0;
    private static final int CYAN = 0x5CE6FF;

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
        Direction f = be.getBlockState().getValue(CoeurClanBlock.FACING);
        VertexConsumer vc = buffers.getBuffer(Sheets.cutoutBlockSheet());

        // filets lumineux et cristal (fixe depuis le 03/11), tournés comme le bloc
        ps.pushPose();
        tourner(ps, f);
        dessiner(mc, LUEUR, ps, vc);
        dessiner(mc, CRISTAL, ps, vc);
        ps.popPose();

        if (d2 > PORTEE_TEXTE * PORTEE_TEXTE) {
            return;
        }
        this.points(be, f, ps, buffers);
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

    /** 12450 -> « 12 450 ». */
    private static String espaces(long n) {
        return String.format(Locale.ROOT, "%,d", n).replace(',', ' ');
    }

    /**
     * 25/10 (perf) : plus de rendu hors du champ de vision. La boîte de rendu du bloc
     * ({@link CoeurClanBlockEntity#getRenderBoundingBox}) couvre déjà le cristal ; avec {@code true}, chaque
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
