package fr.heraliz.armory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.armor.GearDef;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.RenderUtils;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Rendu 3D GeckoLib d'un objet repris de HeralisZ Armor (0.9.0 : copie de son HzGeoRenderer, meme rendu, memes
 * optimisations), porte en armure (objets event) ou dans le panneau Curios (ghillies, brassards, masques a gaz).
 * Modele : assets/heraliz_armory/geo/<geo>.geo.json, texture : textures/armor/<tex>.png (+ _glowmask si lumineuse).
 * Avec une ghillie modelisee, applique les regles de superposition de {@link GearLayering} ; sans ghillie, le brassard
 * passe au-dessus du haut de l'Armurerie porte.
 */
public class GearRenderer<T extends Item & GeoItem> extends GeoArmorRenderer<T> implements ICurioRenderer {
    /** Grossissement de l'équipement porté par-dessus une ghillie (largeur, hauteur, profondeur). */
    private static final float[] VEST = {1.12F, 1.0F, 1.22F};
    /** Distance de l'intérieur d'un gilet (px depuis l'axe du corps, x puis z) et du brassard (depuis l'axe du bras). */
    private static final float VEST_X = 4.4F, VEST_Z = 2.4F, BAND_R = 2.4F;

    private final GearDef piece;
    /** Pour ce rendu : os à grossir ou resserrer autour de leur centre -> {cx, cy, cz, sx, sy, sz}. */
    private final Map<GeoBone, float[]> scaled = new IdentityHashMap<>();
    private final Map<GeoBone, float[]> centers = new IdentityHashMap<>();
    /**
     * Cubes de chaque os pré-transformés : GeckoLib recalcule à chaque image le pivot et la rotation de chaque cube (3
     * rotations + allocations par cube), alors qu'ils sont fixes dans l'os. Ici c'est fait une fois : par face,
     * {nx, ny, nz, drapeaux « cube plat »} puis 4 × {x, y, z, u, v}. Les ghillies (2 000 cartes) passent de ~2 ms à une
     * fraction ; toutes les tenues en profitent. Clés faibles : un rechargement des ressources libère les anciens os.
     */
    private static final Map<GeoBone, float[]> BAKED = new WeakHashMap<>();
    private static final int STRIDE = 24, HEAD = 2;
    /** Niveau de détail des ghillies : part des cartes dessinées selon la distance à la caméra (la base reste pleine). */
    private static final double LOD1 = 10 * 10, LOD2 = 22 * 22, LOD3 = 45 * 45;
    private float lodFrac = 1.0F;
    /** Rendu Curios en un seul passage (toutes les parties visibles) au lieu d'un passage par partie. */
    private boolean allParts;
    private final Vector3f tmpNormal = new Vector3f();
    private final Vector4f tmpPos = new Vector4f();

    public GearRenderer(GearDef piece) {
        super(new Model<>(piece));
        this.piece = piece;
        if (piece.glow()) {
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
        }
    }

    private static final class Model<T extends GeoItem> extends GeoModel<T> {
        private final ResourceLocation geo, tex, anim;

        Model(GearDef p) {
            geo = new ResourceLocation(HeralizArmory.MOD_ID, "geo/" + p.geo() + ".geo.json");
            tex = new ResourceLocation(HeralizArmory.MOD_ID, "textures/armor/" + p.tex() + ".png");
            anim = new ResourceLocation(HeralizArmory.MOD_ID, "animations/" + (p.anim() != null ? p.anim() : "none") + ".animation.json");
        }

        @Override
        public ResourceLocation getModelResource(T animatable) {
            return geo;
        }

        @Override
        public ResourceLocation getTextureResource(T animatable) {
            return tex;
        }

        @Override
        public ResourceLocation getAnimationResource(T animatable) {
            return anim;
        }
    }

    /** Équipement Curios : on rend chaque partie du modèle (tête, torse, jambes, pieds) qui existe. */
    @Override
    @SuppressWarnings("unchecked")
    public <E extends LivingEntity, M extends EntityModel<E>> void render(ItemStack stack, SlotContext slotContext, PoseStack poseStack,
            RenderLayerParent<E, M> parent, MultiBufferSource buffers, int light, float limbSwing, float limbSwingAmount, float partialTicks,
            float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(parent.getModel() instanceof HumanoidModel<?> base)) {
            return;
        }
        T item = (T) stack.getItem();
        prepForRender(slotContext.entity(), stack, EquipmentSlot.CHEST, base);
        VertexConsumer consumer = buffers.getBuffer(RenderType.armorCutoutNoCull(getTextureLocation(item)));
        allParts = true;
        try {
            renderToBuffer(poseStack, consumer, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            allParts = false;
        }
    }

    /** Curios : toutes les parties du modèle (tête, torse et bras, jambes, pieds) en un passage. */
    @Override
    protected void applyBoneVisibilityBySlot(EquipmentSlot currentSlot) {
        if (!allParts) {
            super.applyBoneVisibilityBySlot(currentSlot);
            return;
        }
        setAllVisible(false);
        List<String> parts = piece.parts().isEmpty() ? List.of("chest") : piece.parts();
        for (String part : parts) {
            switch (part) {
                case "head" -> setBoneVisible(head, true);
                case "legs" -> {
                    setBoneVisible(rightLeg, true);
                    setBoneVisible(leftLeg, true);
                }
                case "feet" -> {
                    setBoneVisible(rightBoot, true);
                    setBoneVisible(leftBoot, true);
                }
                default -> {
                    setBoneVisible(body, true);
                    setBoneVisible(rightArm, true);
                    setBoneVisible(leftArm, true);
                }
            }
        }
    }

    // ------------------------------------------------------------------ superposition avec les ghillies

    @Override
    protected void applyBaseTransformations(HumanoidModel<?> baseModel) {
        super.applyBaseTransformations(baseModel);
        scaled.clear();
        GearLayering.State layers = GearLayering.of(getCurrentEntity());
        if (piece.gh() != null) {
            applyZones(layers);
            double d = Lod.distSqr(getCurrentEntity());
            lodFrac = d < LOD1 ? 1.0F : d < LOD2 ? 0.5F : d < LOD3 ? 0.25F : 0.1F;
            return;
        }
        lodFrac = 1.0F;
        if (layers.ghillie() == null) {
            overClothes(layers.chestOv());
            return;
        }
        if (!piece.armor()) {
            switch (piece.slot()) {
                case "belt" -> scale(body, VEST);
                case "bracelet" -> scale(rightArm, GearLayering.ARMBAND);
                case "head" -> scale(head, GearLayering.HEADGEAR);
                default -> {
                }
            }
        } else if ("head".equals(piece.slot())) {
            scale(head, GearLayering.HEADGEAR);
        }
    }

    /** Sans ghillie : le gilet et le brassard passent au-dessus du haut porté, élargis juste assez s'il est épais. */
    private void overClothes(float[] ov) {
        if (ov == null || ov.length < 4 || piece.armor()) {
            return;
        }
        if ("belt".equals(piece.slot())) {
            scale(body, new float[] {Math.max(1.0F, (4.0F + ov[0] + 0.12F) / VEST_X), 1.0F, Math.max(1.0F, (2.0F + ov[1] + 0.12F) / VEST_Z)});
        } else if ("bracelet".equals(piece.slot())) {
            scale(rightArm, new float[] {Math.max(1.0F, (2.0F + ov[2] + 0.12F) / BAND_R), 1.0F, Math.max(1.0F, (2.0F + ov[3] + 0.12F) / BAND_R)});
        }
    }

    /** Ghillie : cache les zones sur lesquelles un équipement vient appuyer (le modèle est partagé : on remet tout à chaque rendu). */
    private void applyZones(GearLayering.State layers) {
        BakedGeoModel model = getGeoModel().getBakedModel(getGeoModel().getModelResource(animatable));
        hide(model, "armorHead_top", layers.head());
        hide(model, "armorHead_cap", !layers.head());
        hide(model, "armorHead_veil", layers.face());
        hide(model, "armorBody_chest", layers.vest());
        hide(model, "armorBody_back", layers.vest() || layers.back());
        hide(model, "armorRightArm_band", layers.armband());
        hide(model, "armorRightArm_bandshort", !layers.armband());
    }

    private static void hide(BakedGeoModel model, String bone, boolean hidden) {
        model.getBone(bone).ifPresent(b -> b.setHidden(hidden));
    }

    private void scale(GeoBone bone, float[] s) {
        if (bone == null || (s[0] == 1 && s[1] == 1 && s[2] == 1)) {
            return;
        }
        float[] c = centers.computeIfAbsent(bone, GearRenderer::centerOf);
        // la tête grandit à partir du cou (le casque ne descend pas sur les yeux)
        float cy = bone == head ? bone.getPivotY() / 16F : c[1];
        scaled.put(bone, new float[] {c[0], cy, c[2], s[0], s[1], s[2]});
    }

    /** Centre de la boîte englobante des cubes de l'os (et de ses enfants), dans le repère où ils sont dessinés. */
    private static float[] centerOf(GeoBone bone) {
        float[] mn = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, mx = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        collect(bone, mn, mx);
        if (mn[0] > mx[0]) {
            return new float[] {bone.getPivotX() / 16F, bone.getPivotY() / 16F, bone.getPivotZ() / 16F};
        }
        return new float[] {(mn[0] + mx[0]) / 2, (mn[1] + mx[1]) / 2, (mn[2] + mx[2]) / 2};
    }

    private static void collect(GeoBone bone, float[] mn, float[] mx) {
        for (GeoCube cube : bone.getCubes()) {
            PoseStack ps = new PoseStack();
            RenderUtils.translateToPivotPoint(ps, cube);
            RenderUtils.rotateMatrixAroundCube(ps, cube);
            RenderUtils.translateAwayFromPivotPoint(ps, cube);
            Matrix4f m = ps.last().pose();
            for (GeoQuad quad : cube.quads()) {
                if (quad == null) {
                    continue;
                }
                for (GeoVertex v : quad.vertices()) {
                    Vector4f p = m.transform(new Vector4f(v.position(), 1.0F));
                    mn[0] = Math.min(mn[0], p.x);
                    mn[1] = Math.min(mn[1], p.y);
                    mn[2] = Math.min(mn[2], p.z);
                    mx[0] = Math.max(mx[0], p.x);
                    mx[1] = Math.max(mx[1], p.y);
                    mx[2] = Math.max(mx[2], p.z);
                }
            }
        }
        for (GeoBone child : bone.getChildBones()) {
            collect(child, mn, mx);
        }
    }

    private static void push(PoseStack poseStack, float[] s) {
        poseStack.pushPose();
        poseStack.translate(s[0], s[1], s[2]);
        poseStack.scale(s[3], s[4], s[5]);
        poseStack.translate(-s[0], -s[1], -s[2]);
    }

    private static float[] bake(GeoBone bone) {
        float[] base = pack(bone, true), cards = pack(bone, false);
        boolean shell = bone.getName().endsWith("_shell");
        float[] out = new float[HEAD + base.length + cards.length];
        out[0] = (shell ? base.length + cards.length : base.length) / STRIDE;
        out[1] = (base.length + cards.length) / STRIDE;
        System.arraycopy(base, 0, out, HEAD, base.length);
        System.arraycopy(cards, 0, out, HEAD + base.length, cards.length);
        return out;
    }

    /** Faces des cubes de l'os : base (boîtes, plusieurs faces) ou cartes (une seule face). */
    private static float[] pack(GeoBone bone, boolean boxes) {
        float[] out = new float[64];
        int n = 0;
        for (GeoCube cube : bone.getCubes()) {
            int faces = 0;
            for (GeoQuad q : cube.quads()) {
                if (q != null) {
                    faces++;
                }
            }
            if ((faces > 1) != boxes) {
                continue;
            }
            PoseStack ps = new PoseStack();
            RenderUtils.translateToPivotPoint(ps, cube);
            RenderUtils.rotateMatrixAroundCube(ps, cube);
            RenderUtils.translateAwayFromPivotPoint(ps, cube);
            Matrix4f m = ps.last().pose();
            Matrix3f nm = ps.last().normal();
            double sx = cube.size().x, sy = cube.size().y, sz = cube.size().z;
            int flags = (sy == 0 || sz == 0 ? 1 : 0) | (sx == 0 || sz == 0 ? 2 : 0) | (sx == 0 || sy == 0 ? 4 : 0);
            for (GeoQuad quad : cube.quads()) {
                if (quad == null || quad.vertices().length != 4) {
                    continue;
                }
                if (n + STRIDE > out.length) {
                    out = Arrays.copyOf(out, out.length * 2);
                }
                Vector3f nrm = nm.transform(new Vector3f(quad.normal()));
                out[n++] = nrm.x;
                out[n++] = nrm.y;
                out[n++] = nrm.z;
                out[n++] = flags;
                for (GeoVertex v : quad.vertices()) {
                    Vector4f p = m.transform(new Vector4f(v.position(), 1.0F));
                    out[n++] = p.x;
                    out[n++] = p.y;
                    out[n++] = p.z;
                    out[n++] = v.texU();
                    out[n++] = v.texV();
                }
            }
        }
        return Arrays.copyOf(out, n);
    }

    /** Même résultat que GeckoLib (y compris la correction des normales des cubes plats), sans recalcul par cube. */
    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int light, int overlay, float red, float green,
            float blue, float alpha) {
        if (bone.isHidden()) {
            return;
        }
        float[] s = scaled.get(bone);
        if (s != null) {
            push(poseStack, s);
        }
        float[] d = BAKED.computeIfAbsent(bone, GearRenderer::bake);
        int always = (int) d[0], total = (int) d[1];
        int count = lodFrac >= 1.0F ? total : always + (int) Math.ceil((total - always) * lodFrac);
        int end = HEAD + count * STRIDE;
        Matrix4f pose = poseStack.last().pose();
        Matrix3f nm = poseStack.last().normal();
        Vector3f n = tmpNormal;
        Vector4f p = tmpPos;
        for (int i = HEAD; i < end; i += STRIDE) {
            nm.transform(d[i], d[i + 1], d[i + 2], n);
            int f = (int) d[i + 3];
            if (n.x < 0 && (f & 1) != 0) {
                n.x = -n.x;
            }
            if (n.y < 0 && (f & 2) != 0) {
                n.y = -n.y;
            }
            if (n.z < 0 && (f & 4) != 0) {
                n.z = -n.z;
            }
            for (int k = i + 4; k < i + STRIDE; k += 5) {
                pose.transform(d[k], d[k + 1], d[k + 2], 1.0F, p);
                buffer.vertex(p.x, p.y, p.z, red, green, blue, alpha, d[k + 3], d[k + 4], overlay, light, n.x, n.y, n.z);
            }
        }
        if (s != null) {
            poseStack.popPose();
        }
    }

    /**
     * Même parcours que GeckoLib, mais sans les 3 rotations + translations d'un os qui n'est ni tourné, ni déplacé, ni
     * agrandi (zones, coques, groupes au repos) : la plupart des os des ghillies.
     */
    @Override
    public void renderRecursively(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
            VertexConsumer buffer, boolean isReRender, float partialTick, int light, int overlay, float red, float green, float blue, float alpha) {
        if (bone.isTrackingMatrices() || bone.getRotX() != 0 || bone.getRotY() != 0 || bone.getRotZ() != 0 || bone.getPosX() != 0
                || bone.getPosY() != 0 || bone.getPosZ() != 0 || bone.getScaleX() != 1 || bone.getScaleY() != 1 || bone.getScaleZ() != 1) {
            super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, light, overlay, red,
                    green, blue, alpha);
            return;
        }
        renderCubesOfBone(poseStack, bone, buffer, light, overlay, red, green, blue, alpha);
        if (!isReRender) {
            applyRenderLayersForBone(poseStack, animatable, bone, renderType, bufferSource, buffer, partialTick, light, overlay);
        }
        renderChildBones(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, light, overlay, red, green,
                blue, alpha);
    }

    @Override
    public void renderChildBones(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
            VertexConsumer buffer, boolean isReRender, float partialTick, int light, int overlay, float red, float green, float blue, float alpha) {
        float[] s = scaled.get(bone);
        if (s == null) {
            super.renderChildBones(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, light, overlay, red,
                    green, blue, alpha);
            return;
        }
        push(poseStack, s);
        super.renderChildBones(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, light, overlay, red,
                green, blue, alpha);
        poseStack.popPose();
    }
}
