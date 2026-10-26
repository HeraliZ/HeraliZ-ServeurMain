package fr.heraliz.casino.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.casino.block.MachineBlock;
import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.client.Views;
import fr.heraliz.casino.client.render.scene.Designs;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.MachineDesign;
import fr.heraliz.casino.client.render.scene.Scene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Rendu en jeu d'une machine : carrosserie figee (VBO) + parties animees + textes (pas de nom flottant au-dessus :
 * les machines s'affichent d'elles-memes).
 */
public class MachineRenderer implements BlockEntityRenderer<MachineBlockEntity> {
    /** Au-dela de cette distance (blocs), version allegee du modele. */
    public static final double LOD_DISTANCE = 24.0;
    /** Au-dela, les parties animees sont figees dans des tampons graphiques rafraichis quelques fois par seconde. */
    public static final double SNAPSHOT_DISTANCE = 9.0;

    private final DrawList list = new DrawList();
    /** Machines dont le rendu a deja echoue (erreur journalisee une seule fois). */
    private static final java.util.Set<String> FAILED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public MachineRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(MachineBlockEntity be, float partial, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
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

    private void draw(MachineBlockEntity be, float partial, PoseStack pose, MultiBufferSource buf, int light) {
        Level level = be.getLevel();
        if (level == null) return;
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof MachineBlock)) return;
        float yRot = yRot(state);
        BlockPos pos = be.getBlockPos();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dist = cam.distanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5));
        MachineDesign<?> d = dist > LOD_DISTANCE ? Designs.getLod(be.type.id) : Designs.get(be.type.id);
        Scene s = scene(be, level, partial, yRot, cam);
        Object view = Views.view(be, s);

        // parties animees, sprites et textes : tampons regroupes (dessines en une fois apres tous les blocs-entites)
        MultiBufferSource batched = CasinoBuffers.wrap(buf);
        SceneRenderer.Frame f = new SceneRenderer.Frame(pose, batched, d.atlas);
        f.model.translation(0.5f, 0, 0.5f).rotateY((float) Math.toRadians(yRot)).translate(-0.5f, 0, -0.5f).scale(1f / 16f);
        // lumiere baissee par les operateurs : la machine s'assombrit avec (carrosserie eclairee par la salle, neons,
        // ecrans et halos attenues) ; a son reglage d'usine ou au-dessus, plein eclat comme toujours. Le joueur qui y
        // joue (camera du casino) la voit en plein eclat.
        boolean using = fr.heraliz.casino.client.camera.CasinoCamera.isOn(pos);
        float dim = using ? 1f : dimming(be.type, state);
        boolean dimmed = dim < 1f;
        f.light = dimmed ? dimmedLight(light, dim) : LightTexture.FULL_BRIGHT;
        f.glow = dimmed ? GLOW_FLOOR + (1 - GLOW_FLOOR) * dim : 1f;
        f.origin = new Vec3(pos.getX(), pos.getY(), pos.getZ());
        f.dist = (float) dist;

        RenderProfile.start();
        StaticModel.Baked baked = StaticModel.get(d, yRot, false);
        if (dimmed) StaticModel.drawLit(baked, pose, buf, f.light, f.glow);
        else StaticModel.draw(baked, pose, buf);
        SceneRenderer.sprites(f, baked.sprites);
        RenderProfile.lap(0);

        if (dist > SNAPSHOT_DISTANCE && !using && StaticModel.useVbo()) {
            // de loin : parties animees figees dans des tampons graphiques, rafraichies quelques fois par seconde
            StaticModel.Snapshot snap = StaticModel.snapshot(be);
            float every = dist < 16 ? 1 : dist < 28 ? 2 : dist < 40 ? 4 : 8;
            if (s.time - snap.builtAt >= every || snap.design != d || snap.yRot != yRot || s.time < snap.builtAt) {
                animate(d, s, view);
                RenderProfile.lap(1);
                StaticModel.rebuild(snap, list, d.atlas, yRot);
                snap.builtAt = s.time;
                snap.design = d;
                snap.yRot = yRot;
                RenderProfile.lap(2);
            }
            if (dimmed) StaticModel.drawLit(snap, d.atlas, pose, f.light, f.glow);
            else StaticModel.draw(snap, d.atlas, pose);
            RenderProfile.lap(0);
            SceneRenderer.solid(f, snap.overlay);
            SceneRenderer.glow(f, snap.overlay);
            SceneRenderer.sprites(f, snap.overlay);
            RenderProfile.lap(5);
            SceneRenderer.text(f, snap.overlay);
            RenderProfile.lap(6);
        } else {
            animate(d, s, view);
            RenderProfile.lap(1);
            SceneRenderer.solid(f, list);
            RenderProfile.lap(2);
            SceneRenderer.translucent(f, list);
            RenderProfile.lap(3);
            SceneRenderer.glow(f, list);
            RenderProfile.lap(4);
            SceneRenderer.sprites(f, list);
            RenderProfile.lap(5);
            SceneRenderer.text(f, list);
            RenderProfile.lap(6);
        }

        if (be instanceof fr.heraliz.casino.block.entity.DuelBlockEntity du && du.botSide != 0) BotFigure.render(du, pose, buf, light, s.time);
        if (HotspotHighlight.isOn(pos)) HotspotHighlight.render(pose, buf, f.model, s.time, s.camX, s.camY, s.camZ);
        RenderProfile.lap(7);
    }

    /** Parties animees de la machine dans la liste (une erreur de dessin ne fait jamais planter le jeu). */
    private void animate(MachineDesign<?> d, Scene s, Object view) {
        list.clear();
        try {
            RenderProfile.animated++;
            d.renderAnimatedRaw(s, view, list);
        } catch (RuntimeException e) {
            // donnees d'une ancienne version...
            if (FAILED.add(d.id)) fr.heraliz.casino.HeralizCasino.LOGGER.error("[HeralizCasino] Erreur de rendu de la machine {}", d.id, e);
            list.clear();
        }
    }

    /** Eclat minimal des ecrans et neons d'une machine eteinte (lumiere a 0) : le jeu reste lisible. */
    static final float GLOW_FLOOR = 0.35f;

    /** Lumiere de la machine rapportee a son reglage d'usine (0 a 1 ; 1 = plein eclat). */
    static float dimming(fr.heraliz.casino.machine.MachineType type, BlockState state) {
        if (type.light <= 0 || !state.hasProperty(MachineBlock.LIGHT)) return 1f;
        return Math.min(1f, state.getValue(MachineBlock.LIGHT) / (float) type.light);
    }

    /** Eclairage de la carrosserie d'une machine baissee : celui de la salle, ou sa propre lueur s'il est plus fort. */
    static int dimmedLight(int world, float dim) {
        int own = Math.round(15 * dim);
        return LightTexture.pack(Math.max(LightTexture.block(world), own), LightTexture.sky(world));
    }

    public static float yRot(BlockState state) {
        return -state.getValue(MachineBlock.FACING).toYRot();
    }

    public static Scene scene(MachineBlockEntity be, Level level, float partial, float yRot, Vec3 cam) {
        Scene s = new Scene().set(level.getGameTime(), partial);
        s.seed = be.seed;
        BlockPos pos = be.getBlockPos();
        float[] c = toModel((float) (cam.x - pos.getX()), (float) (cam.y - pos.getY()), (float) (cam.z - pos.getZ()), yRot);
        s.camX = c[0];
        s.camY = c[1];
        s.camZ = c[2];
        return s;
    }

    // =================================================================== repere
    /** Point du repere modele (px) -> repere local du bloc (blocs), en tenant compte de l'orientation. */
    public static float[] toLocal(float px, float py, float pz, float yRotDeg) {
        double r = Math.toRadians(yRotDeg);
        float dx = px / 16f - 0.5f, dz = pz / 16f - 0.5f;
        float cs = (float) Math.cos(r), sn = (float) Math.sin(r);
        return new float[]{dx * cs + dz * sn + 0.5f, py / 16f, -dx * sn + dz * cs + 0.5f};
    }

    /** Repere local du bloc (blocs) -> repere modele (px). */
    public static float[] toModel(float lx, float ly, float lz, float yRotDeg) {
        double r = Math.toRadians(-yRotDeg);
        float dx = lx - 0.5f, dz = lz - 0.5f;
        float cs = (float) Math.cos(r), sn = (float) Math.sin(r);
        return new float[]{(dx * cs + dz * sn + 0.5f) * 16f, ly * 16f, (-dx * sn + dz * cs + 0.5f) * 16f};
    }

    @Override
    public boolean shouldRenderOffScreen(MachineBlockEntity be) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
