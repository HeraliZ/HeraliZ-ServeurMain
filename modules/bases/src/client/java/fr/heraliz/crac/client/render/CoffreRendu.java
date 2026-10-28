package fr.heraliz.crac.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heraliz.crac.block.Adosse;
import fr.heraliz.crac.block.CoffreBlock;
import fr.heraliz.crac.block.CoffresDonnees;
import fr.heraliz.crac.block.entity.CoffreBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Ouverture des coffres par rareté : la pièce qui bouge (couvercle, porte, volant), modèle cuit à part, tournée autour de
 * sa charnière. Rien n'est dessiné quand le coffre est fermé (le modèle du bloc a sa pièce) : seuls les coffres ouverts ou
 * qui s'ouvrent coûtent quelques dizaines de faces par image. Même calcul des images clés que le script
 * ({@code angle_cles} de {@code modules/bases/outils/coffres_modeles.py}).
 */
public class CoffreRendu implements BlockEntityRenderer<CoffreBlockEntity> {
    /** Graine fixe : aucun objet neuf par image. */
    private static final RandomSource ALEA = RandomSource.create(42L);

    public CoffreRendu(BlockEntityRendererProvider.Context ctx) {
    }

    /** Modèle de la pièce {@code os} du coffre {@code nom} (écrit par le script). */
    public static ResourceLocation modele(String nom, String os) {
        return new ResourceLocation("crac", "block/" + nom + "_mobile_" + os);
    }

    /** Tous les modèles de pièces mobiles, à charger avec les modèles du jeu. */
    public static List<ResourceLocation> modeles(String... noms) {
        List<ResourceLocation> out = new ArrayList<>();
        for (String nom : noms) {
            for (CoffresDonnees.Mobile m : CoffresDonnees.de(nom).mobiles()) {
                out.add(modele(nom, m.os()));
            }
        }
        return out;
    }

    @Override
    public void render(CoffreBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        BlockState s = be.getBlockState();
        if (be.getLevel() == null || !(s.getBlock() instanceof CoffreBlock c) || s.getValue(CoffreBlock.ANNEXE) || !be.mobileVisible()) {
            return;
        }
        float p = be.progression(partial);
        Direction f = s.getValue(CoffreBlock.FACING);
        double[] d = Adosse.decalage(Adosse.tourner(c.boiteNord(), f), s.getValue(Adosse.ADOSSE));
        Minecraft mc = Minecraft.getInstance();
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        CoffresDonnees.Mobile[] mobiles = c.mesures().mobiles();
        ps.pushPose();
        ps.translate(d[0] / 16.0, 0, d[1] / 16.0);     // collé au mur, comme le modèle du bloc
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-switch (f) {
            case EAST -> 90F;
            case SOUTH -> 180F;
            case WEST -> 270F;
            default -> 0F;
        }));
        ps.translate(-0.5, 0, -0.5);
        for (CoffresDonnees.Mobile m : mobiles) {
            ps.pushPose();
            if (m.parent() != null) {
                for (CoffresDonnees.Mobile q : mobiles) {
                    if (q.os().equals(m.parent())) {
                        tourner(ps, q, p);
                    }
                }
            }
            tourner(ps, m, p);
            BakedModel modele = mc.getModelManager().getModel(modele(c.nom(), m.os()));
            // comme un bloc qui bouge (piston) : éclairage doux du monde autour du coffre
            mc.getBlockRenderer().getModelRenderer().tesselateBlock(be.getLevel(), modele, s, be.getBlockPos(), ps, vc, false, ALEA,
                42L, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
            ps.popPose();
        }
        ps.popPose();
    }

    /** Rotation de la pièce autour de sa charnière (px du modèle), angle tiré de ses images clés. */
    private static void tourner(PoseStack ps, CoffresDonnees.Mobile m, float p) {
        float a = angle(m, p);
        if (a == 0F) {
            return;
        }
        ps.translate(m.px() / 16F, m.py() / 16F, m.pz() / 16F);
        ps.mulPose(switch (m.axe()) {
            case 'x' -> Axis.XP.rotationDegrees(a);
            case 'y' -> Axis.YP.rotationDegrees(a);
            default -> Axis.ZP.rotationDegrees(a);
        });
        ps.translate(-m.px() / 16F, -m.py() / 16F, -m.pz() / 16F);
    }

    /** Angle aux images clés, lissé entre deux (t * t * (3 - 2t)). */
    static float angle(CoffresDonnees.Mobile m, float t) {
        float[] ts = m.t(), as = m.angle();
        t = Mth.clamp(t, 0F, 1F);
        for (int k = 0; k + 1 < ts.length; k++) {
            if (t <= ts[k + 1]) {
                float u = ts[k + 1] <= ts[k] ? 0F : (t - ts[k]) / (ts[k + 1] - ts[k]);
                return as[k] + (as[k + 1] - as[k]) * u * u * (3F - 2F * u);
            }
        }
        return as[as.length - 1];
    }
}
