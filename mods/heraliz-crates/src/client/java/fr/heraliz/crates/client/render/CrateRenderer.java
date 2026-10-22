package fr.heraliz.crates.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.crates.block.CrateAnim;
import fr.heraliz.crates.block.CrateBlock;
import fr.heraliz.crates.block.CrateBlockEntity;
import fr.heraliz.crates.client.render.scene.CrateState;
import fr.heraliz.crates.client.render.scene.Design;
import fr.heraliz.crates.client.render.scene.Designs;
import fr.heraliz.crates.client.render.scene.DrawList;
import fr.heraliz.crates.client.render.scene.Ease;
import fr.heraliz.crates.client.render.scene.Roulette;
import fr.heraliz.crates.client.render.scene.Seq;
import fr.heraliz.crates.config.ClientConfig;
import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.loot.LootRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Rendu en jeu d'une caisse : design 3D anime (modele, cadenas, cle, effets), roue d'objets et textes flottants.
 * Le dessin lui-meme est decrit par le design (pur Java, voir client.render.scene) et traduit par SceneRenderer.
 */
public class CrateRenderer implements BlockEntityRenderer<CrateBlockEntity> {
    /** Au-dela de cette distance (blocs), on utilise la version allegee du modele. */
    public static final double LOD_DISTANCE = 12.0;
    /** Caisses au repos : version allegee au-dela de cette distance (blocs). */
    public static final double IDLE_LOD_DISTANCE = 12.0;
    /** Objets de la roue dessines jusqu'a cette distance (blocs). */
    public static final double RING_ITEMS_DISTANCE = 20.0;
    /** Nom flottant : visible jusqu'a cette distance (blocs), estompe sur les HOLOGRAM_FADE derniers. */
    public static final double HOLOGRAM_DISTANCE = 8.0;
    public static final double HOLOGRAM_FADE = 2.0;
    /**
     * Animations des caisses dans le monde (sequence d'ouverture, tremblement, pieces animees au repos) : coupees
     * (choix du proprietaire, 22/10 : trop de lag). On ouvre une caisse dans l'interface facon CS:GO ; la caisse reste
     * figee, couvercle ferme. Le code des scenes est garde, jamais appele tant que ce reglage est faux.
     */
    public static final boolean ANIMATIONS_MONDE = false;
    /** Periode de l'horloge des animations (ticks) : garde une bonne precision en virgule flottante. */
    public static final long CLOCK_PERIOD = 240_000L;

    private final DrawList list = new DrawList();
    private final float[] tmp = new float[6];

    public CrateRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(CrateBlockEntity be, float partial, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof CrateBlock)) return;
        CrateType type = be.getCrateType();
        float yRot = yRot(state);
        BlockPos pos = be.getBlockPos();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dx = cam.x - (pos.getX() + 0.5), dy = cam.y - (pos.getY() + 0.75), dz = cam.z - (pos.getZ() + 0.5);
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float sh = level.getGameTime() - be.shakeTick + partial;
        boolean shaking = sh >= 0 && sh < 10;

        if (!ANIMATIONS_MONDE || (!be.isActive() && !shaking)) {
            // au repos : caisse figee, cuite une fois (en arriere-plan) et gardee par la carte graphique. Rien n'est
            // recalcule : trois appels de dessin par caisse, plus les quelques pieces animees au repos (Coeur de la Gemme).
            Design d = dist > IDLE_LOD_DISTANCE ? Designs.getLod(type.id) : Designs.get(type.id);
            StaticCrate.Baked baked = StaticCrate.getAsync(d, yRot);
            // version detaillee encore en cuisson : la version allegee en attendant
            if (baked == null && d.detail >= 1f) baked = StaticCrate.getAsync(Designs.getLod(type.id), yRot);
            SceneRenderer.Frame idle = null;
            if (ANIMATIONS_MONDE && baked != null && d.animatedAtRest() && d.isBuilt()) {
                // pieces animees : l'opaque avant la caisse (derriere son verre), les lueurs apres (cachees par ses pieces)
                list.clear();
                d.idle(idleState(level, partial), list);
                idle = frame(pose, buf, d, yRot, pos);
                idle.gpu = true;
                SceneRenderer.solid(idle, list);
            }
            if (baked != null) {
                StaticCrate.draw(baked, pose, buf);
                if (idle != null) {
                    SceneRenderer.translucent(idle, list);
                    SceneRenderer.glow(idle, list);
                    SceneRenderer.sprites(idle, list);
                }
                if (baked.sprites().size() > 0) {
                    SceneRenderer.Frame f = frame(pose, buf, d, yRot, pos);
                    SceneRenderer.sprites(f, baked.sprites());
                }
                if (baked.items().size() > 0) showcaseItems(baked.items(), pose, buf, level, yRot);
            }
            hologramText(type, 1f, pose, buf, dist);
            return;
        }

        Design d = dist > LOD_DISTANCE ? Designs.getLod(type.id) : Designs.get(type.id);
        CrateState s = state(be, level, partial, yRot, cam);
        SceneRenderer.Frame f = frame(pose, buf, d, yRot, pos);
        // pieces fixes dessinees depuis la carte graphique (GpuScene), le reste par le tampon classique
        f.gpu = true;
        list.clear();
        s.realItems = true;
        // la cuisson en arriere-plan peut utiliser le meme design au meme moment
        synchronized (d) {
            d.render(s, list);
        }
        SceneRenderer.solid(f, list);
        showcaseItems(list, pose, buf, level, yRot);
        // objets de la roue : inutiles de loin (quelques pixels), couteux pour les armes moddees
        if (s.active && dist <= RING_ITEMS_DISTANCE) ringItems(be, s, d, pose, buf, level, yRot);
        SceneRenderer.translucent(f, list);
        SceneRenderer.glow(f, list);
        SceneRenderer.sprites(f, list);

        if (s.active) revealText(be, s, d, pose, buf, yRot);
        hologramText(type, 1f - Seq.excite(s), pose, buf, dist);
    }

    private static SceneRenderer.Frame frame(PoseStack pose, MultiBufferSource buf, Design d, float yRot, BlockPos pos) {
        SceneRenderer.Frame f = new SceneRenderer.Frame(pose, buf, d.id);
        f.model.translation(0.5f, 0, 0.5f).rotateY((float) Math.toRadians(yRot)).translate(-0.5f, 0, -0.5f).scale(1f / 16f);
        // pleine lumiere, comme la caisse au repos et les pieces dessinees par la carte graphique : de nuit, les pieces
        // mobiles (tentacules...) ne sont pas plus sombres que le reste
        f.light = LightTexture.FULL_BRIGHT;
        f.origin = new Vec3(pos.getX(), pos.getY(), pos.getZ());
        return f;
    }

    public static float yRot(BlockState state) {
        return -state.getValue(CrateBlock.FACING).toYRot();
    }

    // =================================================================== etat
    /** Etat des pieces animees d'une caisse au repos (reutilise : fil de rendu seulement). */
    private final CrateState idleState = new CrateState();

    private CrateState idleState(Level level, float partial) {
        CrateState s = idleState;
        s.time = s.clock = (level.getGameTime() % CLOCK_PERIOD) + partial;
        return s;
    }

    /** Etat d'animation d'une caisse pour cette image. */
    public static CrateState state(CrateBlockEntity be, Level level, float partial, float yRot, Vec3 cam) {
        CrateState s = new CrateState();
        long gt = level.getGameTime();
        s.time = (gt % CLOCK_PERIOD) + partial;
        s.clock = s.time;
        s.seed = 0;
        s.active = be.isActive();
        if (s.active) {
            s.t = Math.max(0f, gt - be.getStartTick() + partial);
            // l'animation part exactement de la pose de repos figee
            s.time = s.t;
            s.ringSize = be.getRing().size();
            s.winIndex = be.getWinIndex();
            LootRarity r = be.getWinRarity();
            s.rarityRgb = r.color;
            s.rarityTier = r.soundTier;
            java.util.List<LootRarity> rr = be.getRingRarity();
            s.ringRgb = new int[rr.size()];
            s.ringRank = new int[rr.size()];
            for (int i = 0; i < rr.size(); i++) {
                s.ringRgb[i] = rr.get(i).color;
                s.ringRank[i] = rr.get(i).ordinal();
            }
            s.target = openerTarget(be, level, partial, yRot);
        }
        float sh = gt - be.shakeTick + partial;
        s.shake = sh >= 0 && sh < 10 ? sh : -1;
        if (!s.active && s.shake >= 0) s.time = 0;
        s.beams = ClientConfig.BEAMS.get();
        s.runes = ClientConfig.RUNE_CIRCLE.get();
        BlockPos pos = be.getBlockPos();
        float[] c = toModel((float) (cam.x - pos.getX()), (float) (cam.y - pos.getY()), (float) (cam.z - pos.getZ()), yRot);
        s.camX = c[0];
        s.camY = c[1];
        s.camZ = c[2];
        return s;
    }

    /** Destination de l'objet gagne : les yeux du joueur qui a ouvert la caisse (px, repere du modele). */
    private static float[] openerTarget(CrateBlockEntity be, Level level, float partial, float yRot) {
        UUID id = be.getOpener();
        Player p = id == null ? null : level.getPlayerByUUID(id);
        if (p == null) return null;
        Vec3 eye = p.getEyePosition(partial);
        BlockPos pos = be.getBlockPos();
        return toModel((float) (eye.x - pos.getX()), (float) (eye.y - 0.35 - pos.getY()), (float) (eye.z - pos.getZ()), yRot);
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

    // =================================================================== objets reels du decor (armes TaCZ skinnees...)
    private static final java.util.Map<String, ItemStack> SHOWCASE = new java.util.HashMap<>();

    private static ItemStack showcaseStack(String item, String nbt) {
        return SHOWCASE.computeIfAbsent(item + "|" + nbt, k -> {
            net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(item);
            net.minecraft.world.item.Item it = rl == null ? null : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(rl);
            if (it == null || it == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
            ItemStack st = new ItemStack(it);
            if (nbt != null && !nbt.isEmpty()) {
                try {
                    st.setTag(net.minecraft.nbt.TagParser.parseTag(nbt));
                } catch (Exception e) {
                    return ItemStack.EMPTY;
                }
            }
            return st;
        });
    }

    /** Boites englobantes des objets du decor (centre x,y,z puis dimensions), mesurees une fois. */
    private static final java.util.Map<String, float[]> SHOWCASE_BOUNDS = new java.util.HashMap<>();

    /**
     * Mesure l'objet tel que Minecraft le dessine en vue FIXED (y compris les rendus speciaux comme les armes TaCZ) :
     * on le "dessine" dans un tampon qui ne fait que noter les sommets.
     */
    private static float[] showcaseBounds(String key, ItemStack stack, Level level) {
        return SHOWCASE_BOUNDS.computeIfAbsent(key, k -> {
            float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            com.mojang.blaze3d.vertex.VertexConsumer probe = new com.mojang.blaze3d.vertex.VertexConsumer() {
                @Override
                public com.mojang.blaze3d.vertex.VertexConsumer vertex(double x, double y, double z) {
                    b[0] = Math.min(b[0], (float) x);
                    b[1] = Math.min(b[1], (float) y);
                    b[2] = Math.min(b[2], (float) z);
                    b[3] = Math.max(b[3], (float) x);
                    b[4] = Math.max(b[4], (float) y);
                    b[5] = Math.max(b[5], (float) z);
                    return this;
                }

                @Override
                public com.mojang.blaze3d.vertex.VertexConsumer color(int r, int g, int bl, int a) {
                    return this;
                }

                @Override
                public com.mojang.blaze3d.vertex.VertexConsumer uv(float u, float v) {
                    return this;
                }

                @Override
                public com.mojang.blaze3d.vertex.VertexConsumer overlayCoords(int u, int v) {
                    return this;
                }

                @Override
                public com.mojang.blaze3d.vertex.VertexConsumer uv2(int u, int v) {
                    return this;
                }

                @Override
                public com.mojang.blaze3d.vertex.VertexConsumer normal(float x, float y, float z) {
                    return this;
                }

                @Override
                public void endVertex() {
                }

                @Override
                public void defaultColor(int r, int g, int bl, int a) {
                }

                @Override
                public void unsetDefaultColor() {
                }
            };
            try {
                Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, new PoseStack(), rt -> probe, level, 0);
            } catch (Throwable ignored) {
                // rendu special qui refuse notre tampon : on garde la boite par defaut
            }
            if (b[3] < b[0]) return new float[]{0, 0, 0, 1, 1, 1};
            return new float[]{(b[0] + b[3]) / 2, (b[1] + b[4]) / 2, (b[2] + b[5]) / 2,
                    Math.max(1e-3f, b[3] - b[0]), Math.max(1e-3f, b[4] - b[1]), Math.max(1e-3f, b[5] - b[2])};
        });
    }

    /** Rotation qui envoie la plus grande dimension sur Y et la deuxieme sur X. */
    private static org.joml.Quaternionf uprightRotation(float[] bb) {
        Integer[] ax = {0, 1, 2};
        java.util.Arrays.sort(ax, (a, c) -> Float.compare(bb[3 + c], bb[3 + a]));
        org.joml.Matrix3f m = new org.joml.Matrix3f().zero();
        m.setColumn(ax[0], 0, 1, 0);
        m.setColumn(ax[1], 1, 0, 0);
        m.setColumn(ax[2], 0, 0, 1);
        if (m.determinant() < 0) m.setColumn(ax[2], 0, 0, -1);
        return new org.joml.Quaternionf().setFromNormalized(m);
    }

    private void showcaseItems(DrawList list, PoseStack pose, MultiBufferSource buf, Level level, float yRot) {
        ItemRenderer ir = null;
        org.joml.Vector3f tr = new org.joml.Vector3f(), sc = new org.joml.Vector3f();
        org.joml.Quaternionf q = new org.joml.Quaternionf();
        for (int i = 0; i < list.size(); i++) {
            DrawList.Op o = list.get(i);
            if (o.type != DrawList.ITEM) continue;
            ItemStack stack = showcaseStack(o.item, o.nbt);
            if (stack.isEmpty()) continue;
            if (ir == null) ir = Minecraft.getInstance().getItemRenderer();
            o.m.getTranslation(tr);
            o.m.getNormalizedRotation(q);
            o.m.getScale(sc);
            float[] bb = showcaseBounds(o.item + "|" + o.nbt, stack, level);
            float k = sc.x * o.size / 16f / Math.max(bb[3], Math.max(bb[4], bb[5]));
            pose.pushPose();
            pose.translate(0.5f, 0, 0.5f);
            pose.mulPose(Axis.YP.rotationDegrees(yRot));
            pose.translate(-0.5f, 0, -0.5f);
            pose.translate(tr.x / 16f, tr.y / 16f, tr.z / 16f);
            pose.mulPose(q);
            if (o.upright) pose.mulPose(uprightRotation(bb));
            pose.scale(k, k, k);
            pose.translate(-bb[0], -bb[1], -bb[2]);
            ir.renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buf, level, 0);
            pose.popPose();
        }
    }

    // =================================================================== roue d'objets
    private void ringItems(CrateBlockEntity be, CrateState s, Design d, PoseStack pose, MultiBufferSource buf, Level level, float yRot) {
        List<ItemStack> ring = be.getRing();
        if (ring.isEmpty()) return;
        ItemRenderer ir = Minecraft.getInstance().getItemRenderer();
        for (int i = 0; i < ring.size(); i++) {
            ItemStack stack = ring.get(i);
            if (stack.isEmpty() || !Roulette.item(s, d, i, tmp)) continue;
            BakedModel model = ir.getModel(stack, level, null, i);
            float sc = tmp[3] * (model.isGui3d() ? 1.9f : 1.25f);
            pose.pushPose();
            pose.translate(0.5f, 0, 0.5f);
            pose.mulPose(Axis.YP.rotationDegrees(yRot));
            pose.translate(-0.5f, 0, -0.5f);
            pose.translate(tmp[0] / 16f, tmp[1] / 16f, tmp[2] / 16f);
            pose.mulPose(Axis.YP.rotationDegrees(tmp[4]));
            pose.scale(sc, sc, sc);
            pose.translate(0, -0.125f, 0);
            ir.render(stack, ItemDisplayContext.GROUND, false, pose, buf, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
            pose.popPose();
        }
    }

    // =================================================================== textes
    private void revealText(CrateBlockEntity be, CrateState s, Design d, PoseStack pose, MultiBufferSource buf, float yRot) {
        float t = s.t;
        if (t < CrateAnim.REVEAL + 4 || t >= CrateAnim.FLY + 2) return;
        List<ItemStack> ring = be.getRing();
        if (ring.isEmpty()) return;
        LootRarity rarity = be.getWinRarity();
        Roulette.winner(s, d, tmp);
        float alpha = Ease.range(t, CrateAnim.REVEAL + 4, CrateAnim.REVEAL + 10) * (1 - Ease.range(t, CrateAnim.FLY - 2, CrateAnim.FLY + 2));
        ItemStack stack = ring.get(Math.min(be.getWinIndex(), ring.size() - 1));
        MutableComponent name = stack.getHoverName().copy();
        if (be.getWinCount() > 1) name.append(Component.literal(" x" + be.getWinCount()).withStyle(ChatFormatting.WHITE));
        Component title = name.withStyle(st -> st.withColor(rarity.color).withBold(true));
        Component sub = Component.literal("✦ ").append(rarity.displayName()).append(" ✦");
        float[] tp = toLocal(tmp[0], tmp[1] + 12f, tmp[2], yRot);
        text(pose, buf, tp[0], tp[1], tp[2], title, 0.028f, alpha, true);
        text(pose, buf, tp[0], tp[1] - 0.3f, tp[2], sub, 0.02f, alpha, false);
    }

    /**
     * Nom flottant au-dessus de la caisse, sans fond, toujours face au joueur : rarete de la caisse en petites capitales
     * espacees entre deux traits (couleur de la rarete), nom de la caisse (sa couleur), puis la cle et le clic droit en
     * gris. Police DM Sans du mod. S'efface pendant l'ouverture et de loin.
     */
    private void hologramText(CrateType type, float fade, PoseStack pose, MultiBufferSource buf, double dist) {
        if (!ClientConfig.HOLOGRAM_TEXT.get() || dist > HOLOGRAM_DISTANCE) return;
        float near = 1f - Ease.smooth((float) ((dist - (HOLOGRAM_DISTANCE - HOLOGRAM_FADE)) / HOLOGRAM_FADE));
        float alpha = fade * near;
        if (alpha < 0.05f) return;
        Font font = Minecraft.getInstance().font;
        Plate plate = PLATES.computeIfAbsent(type, Plate::new);
        float y = (fr.heraliz.crates.block.CrateShapes.height(type) + LABEL_GAP) / 16f;
        pose.pushPose();
        pose.translate(0.5f, y, 0.5f);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-PLATE_SCALE, -PLATE_SCALE, PLATE_SCALE);
        org.joml.Matrix4f m = pose.last().pose();
        int a = Math.max(5, Math.min(255, (int) (alpha * 255)));
        // ligne du haut : rarete espacee, centree, entre deux traits
        float rx = -plate.rarityWidth / 2f, ry = -PLATE_TITLE_Y - PLATE_RARITY_H;
        for (int i = 0; i < plate.rarity.length; i++) {
            font.drawInBatch(plate.rarity[i], rx + plate.rarityX[i], ry, (a << 24) | type.tier.color, false, m, buf,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }
        float ly = ry + 5.2f, gap = 5f;
        rule(m, buf, rx - gap - PLATE_RULE, rx - gap, ly, type.tier.color, a, true);
        rule(m, buf, -rx + gap, -rx + gap + PLATE_RULE, ly, type.tier.color, a, false);
        // nom de la caisse, en grand
        pose.pushPose();
        pose.scale(PLATE_TITLE_K, PLATE_TITLE_K, 1f);
        font.drawInBatch(plate.title, -plate.titleWidth / 2f, -PLATE_TITLE_Y / PLATE_TITLE_K, (a << 24) | type.glow, false,
                pose.last().pose(), buf, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
        // cle et clic droit
        font.drawInBatch(plate.hint, -plate.hintWidth / 2f, PLATE_HINT_Y, (a << 24) | 0xA9AEB8, false, m, buf,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /** Ecart entre le haut de la caisse et le bas du nom (px), taille d'un pixel de texte (blocs). */
    private static final float LABEL_GAP = 9f, PLATE_SCALE = 0.0125f;
    /** Mise en page (pixels de texte) : hauteur de la rarete, haut du nom, echelle du nom, ligne de la cle, longueur des traits. */
    private static final float PLATE_RARITY_H = 12f, PLATE_TITLE_Y = 22f, PLATE_TITLE_K = 1.85f, PLATE_HINT_Y = 2f, PLATE_RULE = 34f;
    /** Espacement des lettres de la rarete (pixels de texte). */
    private static final float PLATE_TRACKING = 2.2f;
    private static final net.minecraft.resources.ResourceLocation FONT = fr.heraliz.crates.HeralizCrates.id("ui"),
            FONT_BOLD = fr.heraliz.crates.HeralizCrates.id("ui_bold"), FONT_SMALL = fr.heraliz.crates.HeralizCrates.id("ui_small");

    /** Textes et largeurs d'un nom flottant, calcules une fois par caisse (et par langue). */
    private static final class Plate {
        final net.minecraft.util.FormattedCharSequence[] rarity;
        final float[] rarityX;
        final float rarityWidth, titleWidth, hintWidth;
        final net.minecraft.util.FormattedCharSequence title, hint;

        Plate(CrateType type) {
            Font font = Minecraft.getInstance().font;
            String r = type.tier.displayName().getString().toUpperCase(java.util.Locale.ROOT);
            rarity = new net.minecraft.util.FormattedCharSequence[r.length()];
            rarityX = new float[r.length()];
            float x = 0;
            for (int i = 0; i < r.length(); i++) {
                Component c = Component.literal(String.valueOf(r.charAt(i))).withStyle(st -> st.withFont(FONT_SMALL));
                rarity[i] = c.getVisualOrderText();
                rarityX[i] = x;
                x += font.width(rarity[i]) + (i < r.length() - 1 ? PLATE_TRACKING : 0);
            }
            rarityWidth = x;
            Component t = Component.translatable("block.heralizcrates." + type.blockId()).withStyle(st -> st.withFont(FONT_BOLD));
            title = t.getVisualOrderText();
            titleWidth = font.width(title);
            Component h = Component.translatable("hologram.heralizcrates.hint",
                    Component.translatable("item.heralizcrates." + type.keyId())).withStyle(st -> st.withFont(FONT));
            hint = h.getVisualOrderText();
            hintWidth = font.width(hint);
        }
    }

    private static final java.util.Map<CrateType, Plate> PLATES = new java.util.EnumMap<>(CrateType.class);

    /** Vide les textes gardes (changement de langue, rechargement des ressources). */
    public static void clearPlates() {
        PLATES.clear();
    }

    /** Trait horizontal fin, qui s'estompe vers l'exterieur. */
    private static void rule(org.joml.Matrix4f m, MultiBufferSource buf, float x0, float x1, float y, int rgb, int alpha, boolean fadeLeft) {
        com.mojang.blaze3d.vertex.VertexConsumer vc = buf.getBuffer(net.minecraft.client.renderer.RenderType.textBackground());
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int a0 = fadeLeft ? alpha / 6 : alpha, a1 = fadeLeft ? alpha : alpha / 6;
        float h = 0.55f;
        vc.vertex(m, x0, y - h, 0).color(r, g, b, a0).uv2(LightTexture.FULL_BRIGHT).endVertex();
        vc.vertex(m, x0, y + h, 0).color(r, g, b, a0).uv2(LightTexture.FULL_BRIGHT).endVertex();
        vc.vertex(m, x1, y + h, 0).color(r, g, b, a1).uv2(LightTexture.FULL_BRIGHT).endVertex();
        vc.vertex(m, x1, y - h, 0).color(r, g, b, a1).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }

    private static void text(PoseStack pose, MultiBufferSource buf, float x, float y, float z, Component text, float scale,
                             float alpha, boolean background) {
        if (alpha <= 0.05f) return;
        Font font = Minecraft.getInstance().font;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-scale, -scale, scale);
        float w = -font.width(text) / 2f;
        int a = Math.max(5, Math.min(255, (int) (alpha * 255)));
        int color = (a << 24) | 0xFFFFFF;
        int bg = background ? ((int) (alpha * 0.35f * 255) << 24) : 0;
        font.drawInBatch(text, w, 0, color, false, pose.last().pose(), buf, Font.DisplayMode.NORMAL, bg, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(CrateBlockEntity be) {
        // false : les caisses hors de l'ecran ne sont pas dessinees (boite englobante du bloc)
        return false;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
