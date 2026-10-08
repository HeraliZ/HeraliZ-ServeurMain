package fr.heralisz.utils.train;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.heralisz.utils.HeraliszUtils;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Maillage d'un véhicule du train (assets/heralisz_utils/train/<nom>.bin, écrit par tools/gen_train.py) chargé une
 * fois dans des tampons de sommets du GPU : chaque groupe a sa texture, sa nature (caisse opaque, vitres, intérieur
 * éclairé par les plafonniers, lumières qui brillent seules) et, pour une porte, son vecteur de glissement. Format :
 * « HZT1 », nombre de groupes, puis par groupe : nom, texture, nature, glissement (3 flottants), numéro de porte
 * (-1 : aucune), nombre de sommets, sommets de 28 octets (position, uv, couleur, normale) — petit-boutiste.
 */
@OnlyIn(Dist.CLIENT)
public final class ModeleTrain {
    public static final int OPAQUE = 0, VERRE = 1, INTERIEUR = 2, LUMIERE = 3, FAISCEAU = 4;
    private static final Map<String, ModeleTrain> CACHE = new HashMap<>();
    private static final Set<ResourceLocation> MIPMAPS = new HashSet<>();

    public static final class Groupe {
        public final String nom;
        public final ResourceLocation texture;
        public final int nature, porte;
        public final float gx, gy, gz;
        final VertexBuffer vbo;

        Groupe(String nom, ResourceLocation texture, int nature, float gx, float gy, float gz, int porte, VertexBuffer vbo) {
            this.nom = nom;
            this.texture = texture;
            this.nature = nature;
            this.gx = gx;
            this.gy = gy;
            this.gz = gz;
            this.porte = porte;
            this.vbo = vbo;
        }
    }

    public final List<Groupe> groupes = new ArrayList<>();

    private ModeleTrain() {}

    public static ModeleTrain get(String nom) {
        return CACHE.computeIfAbsent(nom, ModeleTrain::charger);
    }

    public static void vider() {
        for (ModeleTrain m : CACHE.values()) {
            for (Groupe g : m.groupes) g.vbo.close();
        }
        CACHE.clear();
        MIPMAPS.clear();
    }

    private static String chaine(ByteBuffer b) {
        int l = b.getShort() & 0xFFFF;
        byte[] o = new byte[l];
        b.get(o);
        return new String(o, StandardCharsets.UTF_8);
    }

    private static ModeleTrain charger(String nom) {
        ModeleTrain m = new ModeleTrain();
        ResourceLocation rl = new ResourceLocation(HeraliszUtils.MODID, "train/" + nom + ".bin");
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(rl);
        if (res.isEmpty()) {
            HeraliszUtils.LOG.warn("Modèle de train introuvable : {}", rl);
            return m;
        }
        try (InputStream in = res.get().open()) {
            ByteBuffer b = ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            byte[] magic = new byte[4];
            b.get(magic);
            if (!"HZT1".equals(new String(magic, StandardCharsets.US_ASCII))) throw new IllegalStateException("pas un modèle HZT1");
            int ng = b.getInt();
            BufferBuilder bb = new BufferBuilder(1 << 20);
            for (int k = 0; k < ng; k++) {
                String gn = chaine(b);
                ResourceLocation tex = new ResourceLocation(chaine(b));
                int nature = b.get();
                float gx = b.getFloat(), gy = b.getFloat(), gz = b.getFloat();
                int porte = b.getInt();
                int nv = b.getInt();
                bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
                for (int i = 0; i < nv; i++) {
                    float x = b.getFloat(), y = b.getFloat(), z = b.getFloat(), u = b.getFloat(), v = b.getFloat();
                    int r = b.get() & 255, g = b.get() & 255, bl = b.get() & 255, a = b.get() & 255;
                    float nx = b.get() / 127f, ny = b.get() / 127f, nz = b.get() / 127f;
                    b.get();
                    bb.vertex(x, y, z).uv(u, v).color(r, g, bl, a).normal(nx, ny, nz).endVertex();
                }
                VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
                vbo.bind();
                vbo.upload(bb.end());
                VertexBuffer.unbind();
                m.groupes.add(new Groupe(gn, tex, nature, gx, gy, gz, porte, vbo));
            }
            HeraliszUtils.LOG.info("Modèle de train {} : {} groupes", nom, ng);
        } catch (Exception e) {
            HeraliszUtils.LOG.error("Modèle de train illisible : {}", rl, e);
        }
        return m;
    }

    /** Lie la texture du groupe (mipmaps générés la première fois : pas de scintillement au loin). */
    static void lier(ResourceLocation tex) {
        RenderSystem.setShaderTexture(0, tex);
        if (MIPMAPS.add(tex)) {
            AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(tex);
            GlStateManager._bindTexture(t.getId());
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAX_LEVEL, 5);   // l'image n'a qu'un niveau
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAX_LOD, 5f);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        }
    }
}
