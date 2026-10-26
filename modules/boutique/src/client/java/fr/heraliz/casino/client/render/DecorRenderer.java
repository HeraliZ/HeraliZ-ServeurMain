package fr.heraliz.casino.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.casino.block.DecorBlock;
import fr.heraliz.casino.block.MachineBlock;
import fr.heraliz.casino.block.entity.DecorBlockEntity;
import fr.heraliz.casino.client.render.scene.Designs;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.Scene;
import fr.heraliz.casino.client.render.scene.decor.DecorDesign;
import fr.heraliz.casino.client.render.scene.decor.DecorView;
import fr.heraliz.casino.decor.DecorType;
import fr.heraliz.casino.net.JackpotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Rendu en jeu d'un element de decor : forme figee (VBO) eclairee par la lumiere du monde, puis les parties animees
 * (ecrans, neons, flammes) qui ont leur propre calque lumineux.
 */
public class DecorRenderer implements BlockEntityRenderer<DecorBlockEntity> {
    private final DrawList list = new DrawList();
    private final DecorView view = new DecorView();
    /** Modeles dont le rendu a deja echoue (erreur journalisee une seule fois). */
    private static final java.util.Set<String> FAILED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public DecorRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(DecorBlockEntity be, float partial, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        if (!RenderProfile.on) {
            draw(be, partial, pose, buf, light);
            return;
        }
        long t0 = System.nanoTime();
        try {
            draw(be, partial, pose, buf, light);
        } finally {
            RenderProfile.renderNanos += System.nanoTime() - t0;
            RenderProfile.renders++;
        }
    }

    private void draw(DecorBlockEntity be, float partial, PoseStack pose, MultiBufferSource buf, int light) {
        Level level = be.getLevel();
        if (level == null) return;
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof DecorBlock block)) return;
        float yRot = MachineRenderer.yRot(state);
        BlockPos pos = be.getBlockPos();
        MachineDesign<?> d = Designs.get(block.type.designId(be.mode));
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Scene s = new Scene().set(level.getGameTime(), partial);
        s.seed = be.seed;
        float[] c = MachineRenderer.toModel((float) (cam.x - pos.getX()), (float) (cam.y - pos.getY()), (float) (cam.z - pos.getZ()), yRot);
        s.camX = c[0];
        s.camY = c[1];
        s.camZ = c[2];
        DecorView v = fill(view, be, state, yRot, level.getGameTime(), partial);

        SceneRenderer.Frame f = new SceneRenderer.Frame(pose, CasinoBuffers.wrap(buf), d.atlas);
        f.model.translation(0.5f, 0, 0.5f).rotateY((float) Math.toRadians(yRot)).translate(-0.5f, 0, -0.5f).scale(1f / 16f);
        f.light = light;
        f.origin = new Vec3(pos.getX(), pos.getY(), pos.getZ());
        f.dist = (float) cam.distanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));

        StaticModel.Baked baked = StaticModel.get(d, yRot, false);
        StaticModel.drawLit(baked, pose, buf, light);
        SceneRenderer.sprites(f, baked.sprites);

        if (f.dist > MachineRenderer.SNAPSHOT_DISTANCE && StaticModel.useVbo()) {
            // de loin : parties animees figees dans des tampons graphiques, rafraichies quelques fois par seconde
            StaticModel.Snapshot snap = StaticModel.snapshot(be);
            float every = f.dist < 16 ? 1 : f.dist < 28 ? 2 : f.dist < 40 ? 4 : 8;
            if (s.time - snap.builtAt >= every || snap.design != d || snap.yRot != yRot || s.time < snap.builtAt) {
                animate(d, s, v);
                StaticModel.rebuild(snap, list, d.atlas, yRot);
                snap.builtAt = s.time;
                snap.design = d;
                snap.yRot = yRot;
            }
            StaticModel.drawLit(snap, d.atlas, pose, light);
            SceneRenderer.solid(f, snap.overlay);
            SceneRenderer.glow(f, snap.overlay);
            SceneRenderer.sprites(f, snap.overlay);
            SceneRenderer.text(f, snap.overlay);
            return;
        }
        animate(d, s, v);
        SceneRenderer.all(f, list);
    }

    private void animate(MachineDesign<?> d, Scene s, DecorView v) {
        list.clear();
        try {
            RenderProfile.animated++;
            d.renderAnimatedRaw(s, v, list);
        } catch (RuntimeException e) {
            if (FAILED.add(d.id)) fr.heraliz.casino.HeralizCasino.LOGGER.error("[HeralizCasino] Erreur de rendu du decor {}", d.id, e);
            list.clear();
        }
    }

    /**
     * Etat lu par le modele : allume, variante, jackpot, poteaux voisins (en px dans le repere du modele), temps depuis
     * l'ouverture de la porte ou la derniere saute de lumiere.
     */
    public static DecorView fill(DecorView v, DecorBlockEntity be, BlockState state, float yRot, long gameTime, float partial) {
        be.watch(gameTime);
        v.on = !state.hasProperty(DecorBlock.ON) || state.getValue(DecorBlock.ON);
        v.mode = be.mode;
        v.jackpot = JackpotPacket.clientValue;
        v.since = be.clientOnAt == Long.MIN_VALUE ? 1e6f : Math.min(1e6f, gameTime - be.clientOnAt + partial);
        v.flicker = be.flicker();
        v.flickerSince = be.clientFlickerAt == Long.MIN_VALUE ? 1e6f : Math.min(1e6f, gameTime - be.clientFlickerAt + partial);
        if (be.type().linked() && v.on && be.clientLinks.length > 0) {
            float[][] links = new float[be.clientLinks.length][];
            BlockPos pos = be.getBlockPos();
            for (int i = 0; i < links.length; i++) {
                BlockPos o = be.clientLinks[i];
                float[] m = MachineRenderer.toModel(o.getX() - pos.getX() + 0.5f, o.getY() - pos.getY(), o.getZ() - pos.getZ() + 0.5f, yRot);
                links[i] = new float[]{m[0] - 8, m[1], m[2] - 8};
            }
            v.links = links;
        } else {
            v.links = new float[0][];
        }
        return v;
    }

    // =================================================================== objet (inventaire, main, cadre)
    private static final DrawList ITEM_LIST = new DrawList();

    /** Rendu de l'objet : forme figee + contenu des ecrans au repos, centre dans la case. */
    public static void renderItem(DecorType type, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buf, int light) {
        MachineDesign<?> d = Designs.get(type.designId(0));
        float[] ctr = d instanceof DecorDesign dd ? dd.itemCenter : new float[]{8, 8, 8};
        float sc = d.itemScale;
        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        pose.scale(sc, sc, sc);
        pose.translate(-ctr[0] / 16f, -ctr[1] / 16f, -ctr[2] / 16f);
        StaticModel.Baked baked = StaticModel.get(d, 0, false);
        StaticModel.drawLit(baked, pose, buf, ctx == ItemDisplayContext.GUI ? LightTexture.FULL_BRIGHT : light);
        SceneRenderer.Frame f = new SceneRenderer.Frame(pose, buf, d.atlas);
        f.model.identity().scale(1f / 16f);
        f.item = true;
        f.light = ctx == ItemDisplayContext.GUI ? LightTexture.FULL_BRIGHT : light;
        ITEM_LIST.clear();
        try {
            d.renderAnimatedRaw(Scene.rest(true), d.newView(), ITEM_LIST);
        } catch (RuntimeException e) {
            ITEM_LIST.clear();
        }
        SceneRenderer.all(f, ITEM_LIST);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(DecorBlockEntity be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return 56;
    }
}
