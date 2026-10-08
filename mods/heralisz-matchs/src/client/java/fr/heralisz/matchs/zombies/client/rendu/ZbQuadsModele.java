package fr.heralisz.matchs.zombies.client.rendu;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.client.model.IModelBuilder;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.SimpleUnbakedGeometry;
import net.minecraftforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.function.Function;

/**
 * Modèle de bloc fait de quads libres (chargeur {@code heralisz_matchs:zbquads}), généré par les scripts des zombies v2
 * ({@code tools/matchs/zombies/gen_pap.py}) : la partie FIXE d'une grosse machine est cuite une fois dans le maillage des
 * tronçons (aucun coût par image, éclairage du monde), seules ses pièces animées restent au rendu de bloc-entité.
 * <p>JSON : {@code {"loader": "heralisz_matchs:zbquads", "textures": {"texture": "...", "particle": "..."}, "quads": [...]}},
 * 23 flottants par quad : 4 x (x, y, z, u, v) puis la normale ; positions en pixels, origine au centre bas de la case,
 * +z vers l'avant (même repère que {@link ModeleZb}) ; u, v de 0 à 1 dans la texture (une image de l'atlas des blocs).
 * L'orientation vient de l'état de bloc ({@code "y": 90...}), appliquée autour du centre de la case. Sommets dans le sens
 * trigonométrique vu de face (faces arrière éliminées), jamais retirés par un voisin (le modèle déborde de sa case).
 * <p>Facultatif : {@code "pieces": [{"texture": "<clé de textures>", "quads": [...]}]}, des quads de plus sur d'autres
 * images de l'atlas des blocs (emplacement vide de la boîte mystère : table, bidons et nounours dans un seul modèle cuit).
 * <p>Facultatif : {@code "shade": false} (machines v3 de The Giant) = pas d'ombrage directionnel de Minecraft sur les quads
 * (la lumiere est deja peinte dans la texture : sinon les formes rondes montrent des bandes d'ombre). Defaut : vrai.
 * <p>Facultatif (decors de The Giant, D-053) : {@code "lumineux": true} sur une entree de {@code pieces} = ses quads sont
 * cuits en pleine lumiere (braises, verres des lampes allumees, liquide 115, flammes animees, cadran de l'horloge) ;
 * Forge garde le maximum de la lumiere cuite et de celle du monde. Aucun cout par image.
 */
public final class ZbQuadsModele extends SimpleUnbakedGeometry<ZbQuadsModele> {
    public static final Chargeur CHARGEUR = new Chargeur();
    private static final int PAS = 23;
    /** Clés de texture (dans « textures ») et quads de chaque pièce ; la pièce 0 = « texture » et « quads ». */
    private final String[] cles;
    private final float[][] pieces;
    /** Ombrage directionnel de Minecraft (faux : lumiere peinte dans la texture). */
    private final boolean ombre;
    /** Pieces cuites en pleine lumiere. */
    private final boolean[] lumineux;

    private ZbQuadsModele(String[] cles, float[][] pieces, boolean ombre, boolean[] lumineux) {
        this.cles = cles;
        this.pieces = pieces;
        this.ombre = ombre;
        this.lumineux = lumineux;
    }

    @Override
    protected void addQuads(IGeometryBakingContext ctx, IModelBuilder<?> builder, ModelBaker baker, Function<Material, TextureAtlasSprite> sprites,
                            ModelState etat, ResourceLocation nom) {
        Transformation t = etat.getRotation();
        boolean tourne = !t.isIdentity();
        if (tourne) t = t.blockCenterToCorner();
        Vector4f p = new Vector4f();
        Vector3f n = new Vector3f();
        QuadBakingVertexConsumer.Buffered cuisson = new QuadBakingVertexConsumer.Buffered();
        for (int k = 0; k < pieces.length; k++) {
            TextureAtlasSprite sp = sprites.apply(ctx.getMaterial(cles[k]));
            cuire(pieces[k], sp, t, tourne, p, n, cuisson, builder, ombre, lumineux[k]);
        }
    }

    private static void cuire(float[] q, TextureAtlasSprite sp, Transformation t, boolean tourne, Vector4f p, Vector3f n,
                              QuadBakingVertexConsumer.Buffered cuisson, IModelBuilder<?> builder, boolean ombre, boolean lumineux) {
        int lum = lumineux ? 0xF0 : 0;
        for (int i = 0; i + PAS <= q.length; i += PAS) {
            n.set(q[i + 20], q[i + 21], q[i + 22]);
            if (tourne) t.transformNormal(n);
            cuisson.setSprite(sp);
            cuisson.setTintIndex(-1);
            cuisson.setShade(ombre);
            cuisson.setHasAmbientOcclusion(false);
            cuisson.setDirection(Direction.getNearest(n.x(), n.y(), n.z()));
            for (int s = 0; s < 4; s++) {
                int o = i + s * 5;
                p.set(q[o] / 16F + 0.5F, q[o + 1] / 16F, q[o + 2] / 16F + 0.5F, 1F);
                if (tourne) t.transformPosition(p);
                cuisson.vertex(p.x(), p.y(), p.z());
                cuisson.color(255, 255, 255, 255);
                cuisson.uv(sp.getU(q[o + 3] * 16D), sp.getV(q[o + 4] * 16D));
                cuisson.uv2(lum, lum);
                cuisson.normal(n.x(), n.y(), n.z());
                cuisson.endVertex();
            }
            BakedQuad bq = cuisson.getQuad();
            builder.addUnculledFace(bq);
        }
    }

    /** Lecture du JSON : tableau « quads » (multiple de 23 flottants), pièces facultatives, « shade » et « lumineux » facultatifs. */
    public static final class Chargeur implements IGeometryLoader<ZbQuadsModele> {
        private Chargeur() {
        }

        @Override
        public ZbQuadsModele read(JsonObject o, JsonDeserializationContext ctx) throws JsonParseException {
            JsonArray ps = o.has("pieces") ? GsonHelper.getAsJsonArray(o, "pieces") : new JsonArray();
            String[] cles = new String[1 + ps.size()];
            float[][] pieces = new float[1 + ps.size()][];
            boolean[] lumineux = new boolean[1 + ps.size()];
            lumineux[0] = GsonHelper.getAsBoolean(o, "lumineux", false);
            cles[0] = "texture";
            pieces[0] = quads(GsonHelper.getAsJsonArray(o, "quads"));
            for (int k = 0; k < ps.size(); k++) {
                JsonObject pi = GsonHelper.convertToJsonObject(ps.get(k), "pieces[" + k + "]");
                cles[k + 1] = GsonHelper.getAsString(pi, "texture");
                pieces[k + 1] = quads(GsonHelper.getAsJsonArray(pi, "quads"));
                lumineux[k + 1] = GsonHelper.getAsBoolean(pi, "lumineux", false);
            }
            return new ZbQuadsModele(cles, pieces, GsonHelper.getAsBoolean(o, "shade", true), lumineux);
        }

        private static float[] quads(JsonArray a) {
            if (a.size() % PAS != 0) throw new JsonParseException("zbquads : " + a.size() + " valeurs, pas un multiple de " + PAS);
            float[] q = new float[a.size()];
            for (int i = 0; i < q.length; i++) q[i] = a.get(i).getAsFloat();
            return q;
        }
    }
}
