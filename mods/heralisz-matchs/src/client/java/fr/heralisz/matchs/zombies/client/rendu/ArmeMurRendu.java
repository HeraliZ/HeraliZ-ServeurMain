package fr.heralisz.matchs.zombies.client.rendu;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.contenu.CategoriesArmes;
import fr.heralisz.matchs.zombies.contenu.bloc.ArmeMurBE;
import fr.heralisz.matchs.zombies.contenu.bloc.BlocArmeMur;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Arme a la craie : silhouette de l'arme (selon sa categorie) tracee a la craie blanche sur le mur, 2,4 x 1,2 blocs,
 * toujours un peu lisible dans le noir (la craie accroche la lumiere) ; a l'achat, la craie s'illumine puis s'eteint
 * en 1,5 s. Repere local : origine au centre bas de la case, +z vers la piece, mur en z = -0,5.
 */
public class ArmeMurRendu implements BlockEntityRenderer<ArmeMurBE> {
    private static final Map<String, RenderType> CRAIE = new HashMap<>();
    private static final Map<String, RenderType> ECLAT = new HashMap<>();
    private static final float[] UV = {0, 0, 1, 1};

    static {
        for (String c : CategoriesArmes.TOUTES) {
            ResourceLocation tex = HeraliszMatchs.id("textures/entity/zombies/craie_" + c + ".png");
            CRAIE.put(c, RenderType.entityTranslucent(tex));
            ECLAT.put(c, ZbTypesRendu.lueur(HeraliszMatchs.id("textures/entity/zombies/craie_" + c + "_eclat.png")));
        }
    }

    public ArmeMurRendu(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(ArmeMurBE be, float partiel, PoseStack ps, MultiBufferSource buf, int lumiere, int overlay) {
        Direction f = be.getBlockState().hasProperty(BlocArmeMur.FACING) ? be.getBlockState().getValue(BlocArmeMur.FACING) : Direction.NORTH;
        RenderType rt = CRAIE.getOrDefault(be.categorie(), CRAIE.get(CategoriesArmes.FUSIL));
        ps.pushPose();
        ps.translate(0.5F, 0, 0.5F);
        ps.mulPose(Axis.YP.rotationDegrees(-f.toYRot()));
        ps.translate(0, 0.5F, -0.5F + 0.012F);
        // la craie reste lisible : lumiere de bloc relevee a 6 au moins
        int bloc = Math.max(LightTexture.block(lumiere), 6);
        int l = LightTexture.pack(bloc, LightTexture.sky(lumiere));
        Geo.plan(ps, buf.getBuffer(rt), -1.2F, -0.6F, 1.2F, 0.6F, UV, l, overlay, 0xF0FFFFFF);
        float t = be.ecoule(partiel);
        if (t < 30) {
            float k = 1 - t / 30F;
            ps.translate(0, 0, 0.004F);
            Geo.plan(ps, buf.getBuffer(ECLAT.getOrDefault(be.categorie(), ECLAT.get(CategoriesArmes.FUSIL))), -1.2F, -0.6F, 1.2F, 0.6F, UV,
                    0xF000F0, overlay, Geo.additif(Mth.clamp(k * k * 1.2F, 0, 1), 0xFFF2D8));
        }
        ps.popPose();
    }
}
