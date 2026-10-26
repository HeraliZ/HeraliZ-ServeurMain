package fr.heraliz.loot.client;

import fr.heraliz.loot.HeralizLoot;
import fr.heraliz.loot.client.rendu.conteneurs.AtlasConteneurs;
import fr.heraliz.loot.client.rendu.conteneurs.Conteneurs;
import fr.heraliz.loot.client.rendu.mesh.Mesh;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Conteneur T2-T6 cuit dans le rendu du terrain : le maillage du modèle (client/rendu/conteneurs) devient des faces de
 * bloc ordinaires, une fois pour toutes (coût nul par image, comme un modèle Blockbench). L'occlusion ambiante et
 * l'ombrage selon l'orientation sont cuits dans la couleur des sommets ; voyants et écrans en pleine lumière ; le verre
 * va dans la passe translucide. Remplace le modèle de blocs d'origine (dont on garde les vues d'objet et la particule).
 */
public final class ModeleConteneur implements BakedModel {
    private static final ChunkRenderTypeSet OPAQUE = ChunkRenderTypeSet.of(RenderType.cutoutMipped());
    private static final ChunkRenderTypeSet AVEC_VERRE = ChunkRenderTypeSet.of(RenderType.cutoutMipped(), RenderType.translucent());

    private final BakedModel ancien;
    private final String nom;
    private final float yRot;
    /** Vue d'objet (inventaire, main) : le modèle est recadré pour remplir la case (grands réduits, petits agrandis). */
    private final boolean objet;
    private volatile List<BakedQuad> opaques, verres, toutes;
    /**
     * Faces posées exactement sur un bord du bloc et tournées vers l'extérieur (dessous contre le sol, dos contre un
     * mur…), par côté (indice {@link Direction#get3DDataValue}) : Minecraft ne les dessine pas quand un bloc plein les
     * cache, comme pour n'importe quel bloc (24/10, sans rien changer à l'image). Vues d'objet : aucune.
     */
    private final List<BakedQuad>[] opaquesCote = vides(), verresCote = vides();

    @SuppressWarnings("unchecked")
    private static List<BakedQuad>[] vides() {
        List<BakedQuad>[] l = new List[6];
        for (int i = 0; i < 6; i++) l[i] = new ArrayList<>();
        return l;
    }

    public ModeleConteneur(BakedModel ancien, String nom, float yRot, boolean objet) {
        this.ancien = ancien;
        this.nom = nom;
        this.yRot = yRot;
        this.objet = objet;
    }

    private void preparer() {
        if (toutes != null) return;
        synchronized (this) {
            if (toutes != null) return;
            Mesh[] m = Conteneurs.maillages(nom);
            TextureAtlasSprite[] pages = m == null ? null : pages(m);
            if (pages == null) {
                List<BakedQuad> l = new ArrayList<>(ancien.getQuads(null, null, RandomSource.create()));
                for (Direction d : Direction.values()) l.addAll(ancien.getQuads(null, d, RandomSource.create()));
                opaques = l;
                verres = List.of();
            } else {
                opaques = cuire(m[0], pages, opaquesCote);
                verres = cuire(m[1], pages, verresCote);
            }
            List<BakedQuad> t = new ArrayList<>(opaques);
            t.addAll(verres);
            toutes = t;
        }
    }

    /** Les textures des pages d'atlas utilisées par le maillage (null si l'une manque : on garde l'ancien modèle). */
    private static TextureAtlasSprite[] pages(Mesh[] m) {
        TextureAtlasSprite[] sp = new TextureAtlasSprite[AtlasConteneurs.NOMS.length];
        for (Mesh mesh : m) {
            float[] d = mesh.data();
            for (int i = 0; i < mesh.vertexCount(); i += 4) {
                int k = AtlasConteneurs.page(d[i * Mesh.STRIDE + 3]);
                if (sp[k] != null) continue;
                ResourceLocation tex = new ResourceLocation(HeralizLoot.MODID, "block/" + AtlasConteneurs.NOMS[k]);
                sp[k] = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(tex);
                if (sp[k].contents().name().equals(MissingTextureAtlasSprite.getLocation())) return null;
            }
        }
        return sp;
    }

    private static final float BORD = 1e-3f;

    /** Côté du bloc sur lequel la face est posée, tournée vers l'extérieur, ou null (face libre). */
    private static Direction cote(float[] xs, float[] ys, float[] zs, float fx, float fy, float fz) {
        for (int k = 0; k < 4; k++) {
            if (xs[k] < -BORD || xs[k] > 1 + BORD || ys[k] < -BORD || ys[k] > 1 + BORD || zs[k] < -BORD || zs[k] > 1 + BORD) return null;
        }
        if (tous(xs, 0) && fx < -0.9f) return Direction.WEST;
        if (tous(xs, 1) && fx > 0.9f) return Direction.EAST;
        if (tous(ys, 0) && fy < -0.9f) return Direction.DOWN;
        if (tous(ys, 1) && fy > 0.9f) return Direction.UP;
        if (tous(zs, 0) && fz < -0.9f) return Direction.NORTH;
        if (tous(zs, 1) && fz > 0.9f) return Direction.SOUTH;
        return null;
    }

    private static boolean tous(float[] v, float val) {
        for (float x : v) if (Math.abs(x - val) > BORD) return false;
        return true;
    }

    private List<BakedQuad> cuire(Mesh mesh, TextureAtlasSprite[] pages, List<BakedQuad>[] cotes) {
        List<BakedQuad> out = new ArrayList<>(mesh.vertexCount() / 4);
        Matrix4f m = new Matrix4f().translation(0.5f, 0, 0.5f).rotateY((float) Math.toRadians(yRot)).translate(-0.5f, 0, -0.5f).scale(1f / 16f);
        float[] bo = objet ? Conteneurs.bornes(nom) : null;
        if (bo != null) {
            float dim = Math.max(bo[3] - bo[0], Math.max(bo[4] - bo[1], bo[5] - bo[2]));
            float k = Math.max(0.45f, Math.min(1.5f, 14f / dim));
            m = new Matrix4f().translation(0.5f, 0, 0.5f).rotateY((float) Math.toRadians(yRot)).scale(k / 16f)
                    .translate(-(bo[0] + bo[3]) / 2, -bo[1], -(bo[2] + bo[5]) / 2);
        }
        Matrix3f nm = m.normal(new Matrix3f());
        Vector3f p = new Vector3f(), n = new Vector3f();
        float[] d = mesh.data();
        for (int q = 0; q < mesh.vertexCount() / 4; q++) {
            int o0 = q * 4 * Mesh.STRIDE;
            float cu = 0, cv = 0, fx = 0, fy = 0, fz = 0;
            for (int k = 0; k < 4; k++) {
                int o = o0 + k * Mesh.STRIDE;
                cu += d[o + 3] / 4;
                cv += d[o + 4] / 4;
                nm.transform(d[o + 5], d[o + 6], d[o + 7], n);
                fx += n.x;
                fy += n.y;
                fz += n.z;
            }
            boolean lumineux = AtlasConteneurs.emissif(cu, cv);
            int page = AtlasConteneurs.page(cu);
            TextureAtlasSprite sp = pages[page];
            QuadBakingVertexConsumer.Buffered b = new QuadBakingVertexConsumer.Buffered();
            b.setSprite(sp);
            b.setTintIndex(-1);
            b.setShade(false);
            b.setHasAmbientOcclusion(!lumineux);
            b.setDirection(Direction.getNearest(fx, fy, fz));
            float[] xs = new float[4], ys = new float[4], zs = new float[4];
            for (int k = 0; k < 4; k++) {
                int o = o0 + k * Mesh.STRIDE;
                m.transformPosition(d[o], d[o + 1], d[o + 2], p);
                xs[k] = p.x;
                ys[k] = p.y;
                zs[k] = p.z;
                nm.transform(d[o + 5], d[o + 6], d[o + 7], n);
                if (n.lengthSquared() > 1e-8f) n.normalize();
                // ombrage des blocs (dessus 1, côtés 0,8 / 0,6, dessous 0,5), lissé selon la normale
                float s = lumineux ? 1f : n.x * n.x * 0.6f + n.z * n.z * 0.8f + n.y * n.y * (n.y > 0 ? 1f : 0.5f);
                int r = Math.min(255, (int) (d[o + 8] * s * 255 + 0.5f));
                int g = Math.min(255, (int) (d[o + 9] * s * 255 + 0.5f));
                int bl = Math.min(255, (int) (d[o + 10] * s * 255 + 0.5f));
                b.vertex(p.x, p.y, p.z);
                b.color(r, g, bl, 255);
                b.uv(sp.getU((d[o + 3] - page) * 16), sp.getV(d[o + 4] * 16));
                b.uv2(lumineux ? 240 : 0, lumineux ? 240 : 0);
                b.normal(n.x, n.y, n.z);
                b.endVertex();
            }
            float nl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
            Direction c = objet || nl < 1e-6f ? null : cote(xs, ys, zs, fx / nl, fy / nl, fz / nl);
            if (c != null) cotes[c.get3DDataValue()].add(b.getQuad());
            else out.add(b.getQuad());
        }
        return out;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState st, Direction side, RandomSource r) {
        preparer();
        if (side != null) {
            List<BakedQuad> l = new ArrayList<>(opaquesCote[side.get3DDataValue()]);
            l.addAll(verresCote[side.get3DDataValue()]);
            return l;
        }
        return toutes;
    }

    @Override
    public List<BakedQuad> getQuads(BlockState st, Direction side, RandomSource r, ModelData data, RenderType type) {
        preparer();
        if (side != null) {
            if (type == null) return getQuads(st, side, r);
            return type == RenderType.translucent() ? verresCote[side.get3DDataValue()] : opaquesCote[side.get3DDataValue()];
        }
        if (type == null) return toutes;
        return type == RenderType.translucent() ? verres : opaques;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState st, RandomSource r, ModelData data) {
        preparer();
        return verres.isEmpty() ? OPAQUE : AVEC_VERRE;
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack st, boolean fabuleux) {
        preparer();
        return List.of(verres.isEmpty() ? Sheets.cutoutBlockSheet() : Sheets.translucentCullBlockSheet());
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return ancien.getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return ancien.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
