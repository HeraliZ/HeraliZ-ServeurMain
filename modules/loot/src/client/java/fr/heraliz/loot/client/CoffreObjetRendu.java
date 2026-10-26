package fr.heraliz.loot.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.loot.CoffreBlock;
import fr.heraliz.loot.client.rendu.coffres.DesignCoffre;
import fr.heraliz.loot.client.rendu.coffres.Designs;
import fr.heraliz.loot.client.rendu.scene.DrawList;
import fr.heraliz.loot.client.rendu.scene.Pose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** Rendu des coffres T7 en objet (inventaire, main, cadre) : le modèle 3D fermé, avec ses voyants au repos. */
public final class CoffreObjetRendu extends BlockEntityWithoutLevelRenderer {
    private static CoffreObjetRendu instance;
    private final DrawList liste = new DrawList();
    private final Pose pose = new Pose();

    private CoffreObjetRendu() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static CoffreObjetRendu instance() {
        if (instance == null) instance = new CoffreObjetRendu();
        return instance;
    }

    @Override
    public void renderByItem(ItemStack st, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf, int lum, int overlay) {
        if (!(st.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof CoffreBlock cb)) return;
        DesignCoffre d = Designs.get(cb.type.nom);
        if (d == null) return;
        float h = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() + Minecraft.getInstance().getFrameTime() : 0;
        liste.clear();
        d.dessiner(0, h, true, pose.reset(), liste);
        // cadrage : les grands modèles (coffre d'armes, largage) sont réduits pour tenir dans la case, centrés
        float[] bo = d.bornes();
        float dim = Math.max(bo[3] - bo[0], Math.max(bo[4] - bo[1], bo[5] - bo[2]));
        float k = Math.min(1f, 16f / dim);
        Matrix4f m = new Matrix4f().translate(0.5f, 0, 0.5f).scale(k / 16f).translate(-(bo[0] + bo[3]) / 2, -bo[1], -(bo[2] + bo[5]) / 2);
        CoffreRendu.dessiner(liste, ps, buf, k < 1f ? m : new Matrix4f().scale(1f / 16f), lum, overlay);
    }
}
