package fr.heraliz.crac.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.crac.block.CoeurClanBlock;
import fr.heraliz.crac.block.entity.CoeurClanBlockEntity;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Cœur de clan : les points du clan écrits sur la plaque du socle, à {@link #PORTEE_TEXTE} blocs au plus. Tout le reste
 * (socle, filet et cristal cyan qui éclairent) est le modèle fixe du bloc, écrit par
 * {@code modules/bases/outils/coeur_simple.py} (option A « simple et fixe », validée par le propriétaire le 03/11) : rien
 * ne tourne, plus d'hologramme ; un membre du clan a les rangs et les points par un clic droit sur le cœur (écran du clan,
 * onglet Saison, {@code heraliz/PriseCoeur}).
 */
public class CoeurClanRendu implements BlockEntityRenderer<CoeurClanBlockEntity> {
    private static final double PORTEE_TEXTE = 24.0;
    private static final int PLEIN_JOUR = 0xF000F0;
    private static final int CYAN = 0x5CE6FF;

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
        if (d2 > PORTEE_TEXTE * PORTEE_TEXTE) {
            return;
        }
        this.points(be, be.getBlockState().getValue(CoeurClanBlock.FACING), ps, buffers);
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
        return (int) PORTEE_TEXTE;
    }
}
