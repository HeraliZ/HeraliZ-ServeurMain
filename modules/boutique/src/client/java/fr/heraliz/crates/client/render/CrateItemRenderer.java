package fr.heraliz.crates.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.crates.client.render.scene.CrateState;
import fr.heraliz.crates.client.render.scene.Design;
import fr.heraliz.crates.client.render.scene.Designs;
import fr.heraliz.crates.client.render.scene.DrawList;
import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.item.CrateBlockItem;
import fr.heraliz.crates.item.CrateKeyItem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Rendu 3D anime des caisses et des cles en tant qu'objets (inventaire, main, sol, cadre).
 */
public class CrateItemRenderer extends BlockEntityWithoutLevelRenderer {
    /** Echelle d'une caisse (2 x 2 blocs) dans le cube d'un objet. */
    private static final float CRATE_SCALE = 0.42f;
    /** Echelle d'une cle (~24 px de long) dans l'icone. */
    private static final float KEY_SCALE = 0.8f;

    private final DrawList list = new DrawList();

    public CrateItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        float time = (Util.getMillis() % (CrateRenderer.CLOCK_PERIOD * 50L)) / 50f;
        if (stack.getItem() instanceof CrateKeyItem key) {
            renderKey(key.type, time, pose, buf, light);
        } else if (stack.getItem() instanceof CrateBlockItem crate) {
            renderCrate(crate.type, time, ctx, pose, buf, light);
        }
    }

    private static CrateState itemState(float time, CrateType type) {
        CrateState s = new CrateState();
        s.time = time;
        s.item = true;
        s.seed = (type.ordinal() * 0.137f) % 1f;
        return s;
    }

    private void renderKey(CrateType type, float time, PoseStack pose, MultiBufferSource buf, int light) {
        Design d = Designs.get(type.id);
        list.clear();
        d.renderKey(itemState(time, type), list);
        SceneRenderer.Frame f = new SceneRenderer.Frame(pose, buf, d.id);
        // pointe en haut a droite, tete en bas a gauche (comme une epee)
        f.model.translation(13.4f / 16f, 13.4f / 16f, 0.5f).rotateZ((float) Math.toRadians(135)).scale(KEY_SCALE / 16f);
        f.unit = KEY_SCALE / 16f;
        f.light = light;
        f.item = true;
        SceneRenderer.solid(f, list);
        SceneRenderer.glow(f, list);
    }

    private void renderCrate(CrateType type, float time, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buf, int light) {
        // modele fige et cuit (version allegee dans l'inventaire) : quasiment gratuit a dessiner. Cuisson en arriere-plan :
        // l'icone apparait un instant apres au lieu de figer l'ecran (onglet creatif plein de caisses)
        Design d = ctx == ItemDisplayContext.GUI ? Designs.getLod(type.id) : Designs.get(type.id);
        StaticCrate.Baked baked = StaticCrate.getAsync(d, 0, true);
        if (baked == null) return;
        pose.pushPose();
        pose.translate(0.5f, ctx == ItemDisplayContext.GUI ? 0.06f : 0f, 0.5f);
        pose.scale(CRATE_SCALE, CRATE_SCALE, CRATE_SCALE);
        pose.translate(-0.5f, 0, -0.5f);
        StaticCrate.draw(baked, pose, buf);
        pose.popPose();
    }
}
