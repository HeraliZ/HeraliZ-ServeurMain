package fr.heraliz.armory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Map;

/**
 * Modele d'armure : cubes vanilla (ModelPart) + surfaces libres (quadrilateres a sommets quelconques : coques de
 * casque, visieres, tubes de jumelles, palmes...) attachees a une partie du corps (tete, torse, bras, jambes ; 0.9.0 :
 * bras et jambes pour les combinaisons), ou a un groupe mobile de ces parties (cle "partie/groupe" : la surface suit la
 * charniere des lunettes). Les surfaces suivent la pose de leur partie (rotation de la tete, accroupi...), recoivent la
 * lumiere comme les cubes et respectent la visibilite des parties par slot. Les surfaces lumineuses (cle prefixee de
 * "!") ne sont dessinees que par renderGlow.
 * <p>0.9.0, superposition avec les ghillies (GearLayering) : chaque partie peut etre agrandie ou resserree autour d'un
 * point (« ajustement »), le temps d'un rendu (clearFit apres). Boites des cubes de chaque partie (toutes, et « de base » :
 * les grands cubes qui font la manche ou le torse) calculees une fois au chargement.
 */
public class MeshArmorModel extends HumanoidModel<LivingEntity> {
    /** Sommets par quadrilatere (4) x (x, y, z, u, v, nx, ny, nz) : positions en unites de modele, uv normalises. */
    public static final int STRIDE = 8;
    /** Parties, dans l'ordre des tableaux ci-dessous. */
    public static final String[] PARTS = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};

    private final Map<String, float[]> meshes;
    private final boolean glow;
    /** Par partie : {min x, y, z, max x, y, z} de tous les cubes et surfaces, et des seuls cubes de base (null si aucun). */
    private final float[][] bounds, base;
    /** Ajustement par partie : {centre x, y, z (unites du modele), echelle x, y, z} ; actif si fitOn[i]. */
    private final float[][] fit = new float[6][6];
    private final boolean[] fitOn = new boolean[6];
    private boolean fitted;

    public MeshArmorModel(ModelPart root, Map<String, float[]> meshes, float[][] bounds, float[][] base) {
        super(root);
        this.meshes = meshes;
        this.glow = meshes.keySet().stream().anyMatch(k -> k.startsWith("!"));
        this.bounds = bounds;
        this.base = base;
    }

    /** Le modele a des surfaces lumineuses (objectifs de jumelles). */
    public boolean hasGlow() {
        return glow;
    }

    public static int partIndex(String name) {
        for (int i = 0; i < PARTS.length; i++) if (PARTS[i].equals(name)) return i;
        return -1;
    }

    @Nullable
    public float[] bounds(int part) {
        return bounds[part];
    }

    @Nullable
    public float[] base(int part) {
        return base[part];
    }

    // ------------------------------------------------------------------ ajustements (superposition avec les ghillies)

    /** Agrandit (ou resserre) la partie autour du point (cx, cy, cz), en unites du modele, pour le prochain rendu. */
    public void fit(int part, float cx, float cy, float cz, float sx, float sy, float sz) {
        if (part < 0 || (sx == 1.0F && sy == 1.0F && sz == 1.0F)) return;
        float[] f = fit[part];
        f[0] = cx;
        f[1] = cy;
        f[2] = cz;
        f[3] = sx;
        f[4] = sy;
        f[5] = sz;
        fitOn[part] = true;
        fitted = true;
    }

    /** Retire les ajustements (le modele est partage entre toutes les entites qui portent la piece). */
    public void clearFit() {
        if (!fitted) return;
        for (int i = 0; i < fitOn.length; i++) fitOn[i] = false;
        fitted = false;
    }

    private ModelPart partAt(int i) {
        return switch (i) {
            case 0 -> this.head;
            case 1 -> this.body;
            case 2 -> this.rightArm;
            case 3 -> this.leftArm;
            case 4 -> this.rightLeg;
            default -> this.leftLeg;
        };
    }

    private void applyFit(PoseStack pose, int i) {
        float[] f = fit[i];
        pose.translate(f[0] / 16.0F, f[1] / 16.0F, f[2] / 16.0F);
        pose.scale(f[3], f[4], f[5]);
        pose.translate(-f[0] / 16.0F, -f[1] / 16.0F, -f[2] / 16.0F);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer consumer, int light, int overlay, float r, float g, float b, float a) {
        if (!fitted) {
            super.renderToBuffer(pose, consumer, light, overlay, r, g, b, a);
        } else {
            // partie par partie (ajustements actifs : joueurs et mannequin seulement, jamais de tete de bebe)
            this.hat.render(pose, consumer, light, overlay, r, g, b, a);
            for (int i = 0; i < PARTS.length; i++) {
                ModelPart part = partAt(i);
                if (!fitOn[i]) {
                    part.render(pose, consumer, light, overlay, r, g, b, a);
                    continue;
                }
                if (!part.visible) continue;
                pose.pushPose();
                part.translateAndRotate(pose);
                applyFit(pose, i);
                PartPose saved = part.storePose();
                part.loadPose(PartPose.ZERO);
                part.render(pose, consumer, light, overlay, r, g, b, a);
                part.loadPose(saved);
                pose.popPose();
            }
        }
        for (var e : meshes.entrySet()) {
            if (!e.getKey().startsWith("!")) renderMesh(pose, consumer, e.getKey(), e.getValue(), light, overlay, r, g, b, a);
        }
    }

    /** Surfaces d'une seule partie (bras a la premiere personne), sans groupe mobile ni lueur. */
    public void renderPart(PoseStack pose, VertexConsumer consumer, String partName, int light, int overlay) {
        float[] data = meshes.get(partName);
        if (data != null) renderMesh(pose, consumer, partName, data, light, overlay, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Surfaces lumineuses en pleine lumiere, intensite k (0..1, estompee avec la distance) : a dessiner dans un tampon
     *  additif (RenderType.eyes). */
    public void renderGlow(PoseStack pose, VertexConsumer consumer, float k) {
        for (var e : meshes.entrySet()) {
            if (e.getKey().startsWith("!")) {
                renderMesh(pose, consumer, e.getKey().substring(1), e.getValue(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, k, k, k, 1.0F);
            }
        }
    }

    private void renderMesh(PoseStack pose, VertexConsumer consumer, String key, float[] data, int light, int overlay,
                            float r, float g, float b, float a) {
        int slash = key.indexOf('/');
        String name = slash < 0 ? key : key.substring(0, slash);
        int index = partIndex(name);
        if (index < 0) return;
        ModelPart part = partAt(index);
        if (!part.visible || part.skipDraw) return;
        ModelPart group = null;
        if (slash >= 0) {
            String child = "g_" + key.substring(slash + 1);
            if (!part.hasChild(child)) return;
            group = part.getChild(child);
            if (!group.visible) return;
        }
        pose.pushPose();
        if (this.young && index == 0) {
            // tete des bebes : meme mise a l'echelle que HumanoidModel (AgeableListModel)
            pose.scale(0.75F, 0.75F, 0.75F);
            pose.translate(0.0F, 1.0F, 0.0F);
        }
        part.translateAndRotate(pose);
        if (fitOn[index]) applyFit(pose, index);
        if (group != null) group.translateAndRotate(pose);
        PoseStack.Pose last = pose.last();
        Matrix4f m = last.pose();
        Matrix3f nm = last.normal();
        Vector4f p = new Vector4f();
        Vector3f n = new Vector3f();
        for (int i = 0; i < data.length; i += STRIDE) {
            p.set(data[i] / 16.0F, data[i + 1] / 16.0F, data[i + 2] / 16.0F, 1.0F);
            m.transform(p);
            n.set(data[i + 5], data[i + 6], data[i + 7]);
            nm.transform(n);
            consumer.vertex(p.x(), p.y(), p.z(), r, g, b, a, data[i + 3], data[i + 4], overlay, light, n.x(), n.y(), n.z());
        }
        pose.popPose();
    }
}
