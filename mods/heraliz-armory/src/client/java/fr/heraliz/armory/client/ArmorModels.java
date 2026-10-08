package fr.heraliz.armory.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.armor.ArmorCatalog;
import fr.heraliz.armory.armor.PieceDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.LivingEntity;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modeles en relief des pieces, lus dans assets/heraliz_armory/armor/<ensemble>/<piece>.json (generes par tools/)
 * et compiles UNE fois en modeles vanilla (ModelPart) : aucun cout d'animation, rendu en un seul passage.
 * 4 variantes par piece : bras larges / bras fins, complet / allege (sans petits details, au-dela de 24 blocs).
 * 0.9.0 : aussi les combinaisons (armor/<ensemble>/suit.json) ; mesures pour la superposition avec les ghillies.
 */
public final class ArmorModels implements ResourceManagerReloadListener {
    public static final ArmorModels INSTANCE = new ArmorModels();
    public static final double LOD_DISTANCE_SQR = 24 * 24;

    private static final String[] PARTS = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
    private static final float[][] PIVOTS = {{0, 0, 0}, {0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9f, 12, 0}, {1.9f, 12, 0}};

    /** Variantes d'une piece : [large, fin] x [complet, allege]. */
    public record Variants(HumanoidModel<LivingEntity> wide, HumanoidModel<LivingEntity> slim,
                           HumanoidModel<LivingEntity> wideLod, HumanoidModel<LivingEntity> slimLod, ResourceLocation texture,
                           boolean translucent, boolean hideHat) {
        public HumanoidModel<LivingEntity> pick(boolean slim, boolean lod) {
            return slim ? (lod ? slimLod : this.slim) : (lod ? wideLod : wide);
        }

        /** Type de rendu de la piece : translucide seulement si sa texture contient du verre (tri des faces), sinon decoupe. */
        public net.minecraft.client.renderer.RenderType renderType() {
            return translucent ? ArmorRenderTypes.armorTranslucent(texture) : ArmorRenderTypes.armorCutout(texture);
        }
    }

    /** Toutes les pieces a modele en relief : ensembles (catalogue genere) et combinaisons (0.9.0). */
    public static final List<PieceDef> ALL = java.util.stream.Stream.concat(ArmorCatalog.PIECES.stream(),
            fr.heraliz.armory.armor.SuitCatalog.SUITS.stream()).toList();

    private final Map<String, Variants> models = new HashMap<>();
    /** Mesures pour la superposition avec les ghillies (GearLayering), par piece. */
    private static final Map<String, Fit> FITS = new HashMap<>();

    /**
     * Mesures d'une piece (0.9.0), d'apres son modele (bras larges, complet) :
     * <ul>
     *   <li>ov : debord du haut porte hors de la peau {torse x, torse z, bras x, bras z} en pixels, mesure sur les cubes de
     *   base (manche, torse : cubes d'au moins 6 px de haut, sans les poches, capuches...) ; sert a elargir le brassard
     *   porte par-dessus, et a resserrer le haut sous une ghillie incomplete (sq) ;</li>
     *   <li>vest : distance de l'interieur du gilet a l'axe du corps {x (flancs), z (devant, dos)} : un gilet porte sur une
     *   ghillie n'est agrandi que de ce qu'il faut pour passer au-dessus de sa toile.</li>
     * </ul>
     */
    public record Fit(@org.jetbrains.annotations.Nullable float[] ov, @org.jetbrains.annotations.Nullable float[] sq,
                      @org.jetbrains.annotations.Nullable float[] vest) {
        static Fit of(PieceDef def, JsonObject json, Variants v) {
            float[] ov = null, sq = null, vest = null;
            if ("chest".equals(def.piece()) && v.wide() instanceof MeshArmorModel m) {
                float[] b = m.base(1), ra = m.base(2), la = m.base(3);
                float bx = 0, bz = 0, ax = 0, az = 0;
                if (b != null) {
                    bx = Math.max(Math.max(0, -4 - b[0]), b[3] - 4);
                    bz = Math.max(Math.max(0, -2 - b[2]), b[5] - 2);
                }
                if (ra != null) {
                    ax = Math.max(ax, Math.max(-3 - ra[0], ra[3] - 1));
                    az = Math.max(az, Math.max(-2 - ra[2], ra[5] - 2));
                }
                if (la != null) {
                    ax = Math.max(ax, Math.max(-1 - la[0], la[3] - 3));
                    az = Math.max(az, Math.max(-2 - la[2], la[5] - 2));
                }
                ov = new float[]{bx, bz, Math.max(0, ax), Math.max(0, az)};
                // meme regle que HeralisZ Armor (gen_armor.squeeze) : sous la toile de la veste (flancs 4,6 px, dos 2,7 px,
                // manches 2,35 px du centre du bras)
                sq = new float[]{Math.min(1, 4.45F / (4 + ov[0])), Math.min(1, 2.62F / (2 + ov[1])), Math.min(1, 2.3F / (2 + ov[2])),
                        Math.min(1, 2.45F / (2 + ov[3]))};
                if (Math.min(Math.min(sq[0], sq[1]), Math.min(sq[2], sq[3])) >= 0.999F) sq = null;
            }
            if ("vest".equals(def.piece())) vest = vestInner(json);
            return new Fit(ov, sq, vest);
        }
    }

    /** Mesures de la piece pour la superposition avec les ghillies (null si le modele manque). */
    @org.jetbrains.annotations.Nullable
    public Fit fit(PieceDef def) {
        return FITS.get(def.itemId());
    }

    /**
     * Interieur d'un gilet : plus petite distance a l'axe du corps des grands panneaux (cubes d'au moins 2 x 2 px, devant,
     * dos, flancs) et des plaques en surfaces libres (devant / dos, au milieu du torse). {x, z}, 0 si rien trouve.
     */
    private static float[] vestInner(JsonObject json) {
        float ix = Float.MAX_VALUE, iz = Float.MAX_VALUE;
        JsonObject parts = json.getAsJsonObject("parts");
        if (parts != null && parts.has("body")) {
            for (JsonElement e : parts.getAsJsonArray("body")) {
                JsonObject c = e.getAsJsonObject();
                if (c.has("rotation")) continue;
                JsonArray f = c.getAsJsonArray("from"), sz = c.getAsJsonArray("size");
                float x0 = f.get(0).getAsFloat(), z0 = f.get(2).getAsFloat();
                float sx = sz.get(0).getAsFloat(), sy = sz.get(1).getAsFloat(), szz = sz.get(2).getAsFloat();
                float x1 = x0 + sx, z1 = z0 + szz;
                if (sx >= 2 && sy >= 2 && z1 <= -1.99F) iz = Math.min(iz, -z1);
                if (sx >= 2 && sy >= 2 && z0 >= 1.99F) iz = Math.min(iz, z0);
                if (szz >= 1.5F && sy >= 2 && x1 <= -3.99F) ix = Math.min(ix, -x1);
                if (szz >= 1.5F && sy >= 2 && x0 >= 3.99F) ix = Math.min(ix, x0);
            }
        }
        JsonObject faces = json.has("faces") ? json.getAsJsonObject("faces") : null;
        if (faces != null && faces.has("body")) {
            for (JsonElement e : faces.getAsJsonArray("body")) {
                JsonArray v = e.getAsJsonObject().getAsJsonArray("v");
                for (int k = 0; k < 4; k++) {
                    float x = v.get(3 * k).getAsFloat(), z = v.get(3 * k + 2).getAsFloat();
                    if (Math.abs(x) < 3 && Math.abs(z) >= 1.99F) iz = Math.min(iz, Math.abs(z));
                }
            }
        }
        return new float[]{ix == Float.MAX_VALUE ? 0 : ix, iz == Float.MAX_VALUE ? 0 : iz};
    }

    /** Groupes mobiles (lunettes de vision nocturne...) de chaque modele compile : partie + rotation "relevee". */
    private static final Map<HumanoidModel<?>, List<Group>> GROUPS = new IdentityHashMap<>();

    public record Group(String name, ModelPart part, float upX, float upY, float upZ) {}

    /** Cubes inclines partageant un meme repere (groupe mobile eventuel, centre, rotation en degres). */
    private record Tilted(String group, float ox, float oy, float oz, float rx, float ry, float rz, CubeListBuilder cubes) {}

    /** Place les groupes mobiles d'un modele : down = 0 (releve, au repos) a 1 (baisse, en service). */
    public static void applyGroups(HumanoidModel<?> model, String name, float down) {
        List<Group> gs = GROUPS.get(model);
        if (gs == null) return;
        float k = 1.0F - down;
        for (Group g : gs) {
            if (!g.name().equals(name)) continue;
            g.part().xRot = g.upX() * k;
            g.part().yRot = g.upY() * k;
            g.part().zRot = g.upZ() * k;
        }
    }

    public static boolean hasGroup(HumanoidModel<?> model, String name) {
        List<Group> gs = GROUPS.get(model);
        return gs != null && gs.stream().anyMatch(g -> g.name().equals(name));
    }
    private static final HumanoidModel<LivingEntity> EMPTY = build(null, false, false);

    private ArmorModels() {}

    public Variants get(PieceDef def) {
        return models.get(def.itemId());
    }

    /** Modele a utiliser pour cette piece sur cette entite (bras fins des skins Alex, niveau de detail selon la distance). */
    public HumanoidModel<LivingEntity> modelFor(PieceDef def, LivingEntity entity) {
        Variants v = models.get(def.itemId());
        if (v == null) return EMPTY;
        boolean slim = entity instanceof AbstractClientPlayer p && "slim".equals(p.getModelName());
        boolean lod = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null && mc.gameRenderer.getMainCamera().isInitialized()) {
            lod = mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(entity.position()) > LOD_DISTANCE_SQR;
        }
        return v.pick(slim, lod);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        models.clear();
        GROUPS.clear();
        FITS.clear();
        for (PieceDef def : ALL) {
            ResourceLocation loc = HeralizArmory.id(def.modelPath());
            var res = manager.getResource(loc);
            if (res.isEmpty()) {
                HeralizArmory.LOGGER.warn("Modele d'armure manquant : {}", loc);
                continue;
            }
            try (Reader r = res.get().openAsReader()) {
                JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
                ResourceLocation tex = new ResourceLocation(json.get("texture").getAsString());
                boolean translucent = json.has("translucent") && json.get("translucent").getAsBoolean();
                boolean hideHat = json.has("hide_hat") && json.get("hide_hat").getAsBoolean();
                Variants v = new Variants(build(json, false, false), build(json, true, false),
                        build(json, false, true), build(json, true, true), tex, translucent, hideHat);
                models.put(def.itemId(), v);
                FITS.put(def.itemId(), Fit.of(def, json, v));
            } catch (Exception e) {
                HeralizArmory.LOGGER.error("Modele d'armure illisible : {}", loc, e);
            }
        }
        HeralizArmory.LOGGER.info("{} modeles d'armure charges", models.size());
    }

    /**
     * Surfaces libres de la piece ("faces" : partie -> quadrilateres {v: 12 coordonnees, uv: [u, v, w, h] en unites de
     * texture, n: 12 normales (surface lisse, facultatif), detail, group, glow}) en tableaux de sommets pour MeshArmorModel.
     * Cle : partie, ou "partie/groupe" pour une surface d'un groupe mobile (coordonnees relatives a la charniere), prefixee
     * de "!" pour une surface lumineuse (dessinee a part, lunettes baissees). Sans normales fournies : normale de la
     * facette (produit vectoriel des diagonales, vers l'exterieur).
     */
    private static Map<String, float[]> readFaces(JsonObject json, boolean lod, int tw, int th) {
        Map<String, float[]> out = new HashMap<>();
        if (json == null || !json.has("faces")) return out;
        JsonObject faces = json.getAsJsonObject("faces");
        JsonObject groups = json.has("groups") ? json.getAsJsonObject("groups") : new JsonObject();
        Map<String, List<float[]>> lists = new LinkedHashMap<>();
        for (String part : faces.keySet()) {
            JsonArray arr = faces.getAsJsonArray(part);
            for (JsonElement e : arr) {
                JsonObject f = e.getAsJsonObject();
                if (lod && f.has("detail") && f.get("detail").getAsBoolean()) continue;
                String g = f.has("group") && groups.has(f.get("group").getAsString()) ? f.get("group").getAsString() : null;
                boolean glow = f.has("glow") && f.get("glow").getAsBoolean();
                float px = 0, py = 0, pz = 0;
                if (g != null) {
                    JsonArray piv = groups.getAsJsonObject(g).getAsJsonArray("pivot");
                    px = piv.get(0).getAsFloat();
                    py = piv.get(1).getAsFloat();
                    pz = piv.get(2).getAsFloat();
                }
                JsonArray v = f.getAsJsonArray("v"), uv = f.getAsJsonArray("uv");
                float[] q = new float[4 * MeshArmorModel.STRIDE];
                float u0 = uv.get(0).getAsFloat() / tw, v0 = uv.get(1).getAsFloat() / th;
                float u1 = (uv.get(0).getAsFloat() + uv.get(2).getAsFloat()) / tw, v1 = (uv.get(1).getAsFloat() + uv.get(3).getAsFloat()) / th;
                float[][] st = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
                float[] fn = faceNormal(v);
                JsonArray nn = f.has("n") ? f.getAsJsonArray("n") : null;
                for (int k = 0; k < 4; k++) {
                    int o = k * MeshArmorModel.STRIDE;
                    q[o] = v.get(3 * k).getAsFloat() - px;
                    q[o + 1] = v.get(3 * k + 1).getAsFloat() - py;
                    q[o + 2] = v.get(3 * k + 2).getAsFloat() - pz;
                    q[o + 3] = st[k][0];
                    q[o + 4] = st[k][1];
                    q[o + 5] = nn != null ? nn.get(3 * k).getAsFloat() : fn[0];
                    q[o + 6] = nn != null ? nn.get(3 * k + 1).getAsFloat() : fn[1];
                    q[o + 7] = nn != null ? nn.get(3 * k + 2).getAsFloat() : fn[2];
                }
                String key = (glow ? "!" : "") + part + (g != null ? "/" + g : "");
                lists.computeIfAbsent(key, k -> new ArrayList<>()).add(q);
            }
        }
        for (var en : lists.entrySet()) {
            List<float[]> quads = en.getValue();
            float[] all = new float[quads.size() * 4 * MeshArmorModel.STRIDE];
            for (int i = 0; i < quads.size(); i++) System.arraycopy(quads.get(i), 0, all, i * quads.get(i).length, quads.get(i).length);
            if (all.length > 0) out.put(en.getKey(), all);
        }
        return out;
    }

    /** Groupes mobiles qui portent des surfaces libres, par partie (leur ModelPart doit exister meme sans cube). */
    private static Map<String, List<String>> faceGroups(JsonObject json) {
        Map<String, List<String>> out = new HashMap<>();
        if (json == null || !json.has("faces") || !json.has("groups")) return out;
        JsonObject faces = json.getAsJsonObject("faces"), groups = json.getAsJsonObject("groups");
        for (String part : faces.keySet()) {
            for (JsonElement e : faces.getAsJsonArray(part)) {
                JsonObject f = e.getAsJsonObject();
                if (!f.has("group")) continue;
                String g = f.get("group").getAsString();
                List<String> l = out.computeIfAbsent(part, k -> new ArrayList<>());
                if (groups.has(g) && !l.contains(g)) l.add(g);
            }
        }
        return out;
    }

    /** Normale sortante d'un quadrilatere (meme convention que les faces des cubes : diagonales, sens inverse). */
    private static float[] faceNormal(JsonArray v) {
        float[][] p = new float[4][3];
        for (int k = 0; k < 4; k++) for (int c = 0; c < 3; c++) p[k][c] = v.get(3 * k + c).getAsFloat();
        float[] a = sub(p[1], p[0]), b = sub(p[3], p[0]), c = sub(p[3], p[2]), d = sub(p[1], p[2]);
        float nx = -(a[1] * b[2] - a[2] * b[1] + c[1] * d[2] - c[2] * d[1]);
        float ny = -(a[2] * b[0] - a[0] * b[2] + c[2] * d[0] - c[0] * d[2]);
        float nz = -(a[0] * b[1] - a[1] * b[0] + c[0] * d[1] - c[1] * d[0]);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        return len < 1e-6F ? new float[]{0, 0, -1} : new float[]{nx / len, ny / len, nz / len};
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    /**
     * Boites d'une partie (unites du modele, repere de la partie) : tous les cubes (inclines compris) et surfaces libres
     * (bounds), et les seuls cubes « de base » : droits, hors groupe mobile, d'au moins 6 px de haut (base : la manche,
     * le torse, la jambe, sans les poches ni les accessoires).
     */
    private static void measure(JsonObject json, String name, boolean slim, float[][] bounds, float[][] base, int i) {
        if (json == null) return;
        JsonObject parts = json.getAsJsonObject("parts");
        JsonObject slimParts = json.has("slim_parts") ? json.getAsJsonObject("slim_parts") : null;
        JsonArray cubes = slim && slimParts != null && slimParts.has(name) ? slimParts.getAsJsonArray(name)
                : parts != null && parts.has(name) ? parts.getAsJsonArray(name) : null;
        float d = (float) Math.PI / 180.0F;
        if (cubes != null) {
            for (JsonElement e : cubes) {
                JsonObject c = e.getAsJsonObject();
                JsonArray f = c.getAsJsonArray("from"), sz = c.getAsJsonArray("size");
                float x0 = f.get(0).getAsFloat(), y0 = f.get(1).getAsFloat(), z0 = f.get(2).getAsFloat();
                float sx = sz.get(0).getAsFloat(), sy = sz.get(1).getAsFloat(), szz = sz.get(2).getAsFloat();
                org.joml.Matrix3f rot = null;
                float ox = 0, oy = 0, oz = 0;
                if (c.has("rotation")) {
                    JsonArray r = c.getAsJsonArray("rotation"), o = c.getAsJsonArray("origin");
                    rot = new org.joml.Matrix3f().rotationZYX(r.get(2).getAsFloat() * d, r.get(1).getAsFloat() * d, r.get(0).getAsFloat() * d);
                    ox = o.get(0).getAsFloat();
                    oy = o.get(1).getAsFloat();
                    oz = o.get(2).getAsFloat();
                }
                boolean isBase = rot == null && !c.has("group") && sy >= 6.0F;
                org.joml.Vector3f p = new org.joml.Vector3f();
                for (int k = 0; k < 8; k++) {
                    p.set(x0 + ((k & 1) != 0 ? sx : 0), y0 + ((k & 2) != 0 ? sy : 0), z0 + ((k & 4) != 0 ? szz : 0));
                    if (rot != null) {
                        p.sub(ox, oy, oz);
                        rot.transform(p);
                        p.add(ox, oy, oz);
                    }
                    bounds[i] = grow(bounds[i], p.x, p.y, p.z);
                    if (isBase) base[i] = grow(base[i], p.x, p.y, p.z);
                }
            }
        }
        if (json.has("faces") && json.getAsJsonObject("faces").has(name)) {
            for (JsonElement e : json.getAsJsonObject("faces").getAsJsonArray(name)) {
                JsonArray v = e.getAsJsonObject().getAsJsonArray("v");
                for (int k = 0; k < 4; k++) bounds[i] = grow(bounds[i], v.get(3 * k).getAsFloat(), v.get(3 * k + 1).getAsFloat(), v.get(3 * k + 2).getAsFloat());
            }
        }
    }

    private static float[] grow(float[] b, float x, float y, float z) {
        if (b == null) return new float[]{x, y, z, x, y, z};
        b[0] = Math.min(b[0], x);
        b[1] = Math.min(b[1], y);
        b[2] = Math.min(b[2], z);
        b[3] = Math.max(b[3], x);
        b[4] = Math.max(b[4], y);
        b[5] = Math.max(b[5], z);
        return b;
    }

    private static HumanoidModel<LivingEntity> build(JsonObject json, boolean slim, boolean lod) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        int tw = 64, th = 64;
        JsonObject parts = null, slimParts = null;
        if (json != null) {
            JsonArray size = json.getAsJsonArray("texture_size");
            tw = size.get(0).getAsInt();
            th = size.get(1).getAsInt();
            parts = json.getAsJsonObject("parts");
            slimParts = json.has("slim_parts") ? json.getAsJsonObject("slim_parts") : null;
        }
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        JsonObject groups = json != null && json.has("groups") ? json.getAsJsonObject("groups") : new JsonObject();
        List<String[]> made = new ArrayList<>();      // (partie, groupe)
        Map<String, List<String>> withFaces = faceGroups(json);
        float[][] bounds = new float[PARTS.length][], base = new float[PARTS.length][];
        for (int i = 0; i < PARTS.length; i++) {
            String name = PARTS[i];
            measure(json, name, slim, bounds, base, i);
            CubeListBuilder b = CubeListBuilder.create();
            Map<String, CubeListBuilder> gb = new HashMap<>();
            for (String g : withFaces.getOrDefault(name, List.of())) gb.put(g, CubeListBuilder.create());
            // cubes inclines : un enfant par repere (groupe, centre, rotation), partage par les cubes de meme inclinaison
            Map<String, Tilted> tilted = new LinkedHashMap<>();
            JsonArray cubes = null;
            if (slim && slimParts != null && slimParts.has(name)) cubes = slimParts.getAsJsonArray(name);
            else if (parts != null && parts.has(name)) cubes = parts.getAsJsonArray(name);
            if (cubes != null) {
                for (JsonElement e : cubes) {
                    JsonObject c = e.getAsJsonObject();
                    if (lod && c.has("detail") && c.get("detail").getAsBoolean()) continue;
                    JsonArray uv = c.getAsJsonArray("uv"), from = c.getAsJsonArray("from"), sz = c.getAsJsonArray("size");
                    EnumSet<Direction> faces = EnumSet.noneOf(Direction.class);
                    for (JsonElement f : c.getAsJsonArray("faces")) faces.add(Direction.valueOf(f.getAsString()));
                    String gName = c.has("group") ? c.get("group").getAsString() : null;
                    final String g = gName != null && groups.has(gName) ? gName : null;
                    if (g != null) gb.computeIfAbsent(g, k -> CubeListBuilder.create());
                    if (c.has("rotation")) {
                        // cube incline : enfant de la partie (ou du groupe) place sur son centre de rotation
                        JsonArray rot = c.getAsJsonArray("rotation"), org = c.getAsJsonArray("origin");
                        float ox = org.get(0).getAsFloat(), oy = org.get(1).getAsFloat(), oz = org.get(2).getAsFloat();
                        String key = g + "|" + ox + "|" + oy + "|" + oz + "|" + rot.get(0).getAsFloat() + "|" + rot.get(1).getAsFloat() + "|"
                                + rot.get(2).getAsFloat();
                        Tilted tl = tilted.computeIfAbsent(key, k -> new Tilted(g, ox, oy, oz, rot.get(0).getAsFloat(), rot.get(1).getAsFloat(),
                                rot.get(2).getAsFloat(), CubeListBuilder.create()));
                        tl.cubes().texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt())
                                .addBox(from.get(0).getAsFloat() - ox, from.get(1).getAsFloat() - oy, from.get(2).getAsFloat() - oz,
                                        sz.get(0).getAsFloat(), sz.get(1).getAsFloat(), sz.get(2).getAsFloat(), faces);
                        continue;
                    }
                    if (g != null) {
                        // cube d'un groupe mobile : enfant de la partie, coordonnees relatives a la charniere
                        JsonArray piv = groups.getAsJsonObject(g).getAsJsonArray("pivot");
                        gb.get(g).texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt())
                                .addBox(from.get(0).getAsFloat() - piv.get(0).getAsFloat(), from.get(1).getAsFloat() - piv.get(1).getAsFloat(),
                                        from.get(2).getAsFloat() - piv.get(2).getAsFloat(),
                                        sz.get(0).getAsFloat(), sz.get(1).getAsFloat(), sz.get(2).getAsFloat(), faces);
                        continue;
                    }
                    b.texOffs(uv.get(0).getAsInt(), uv.get(1).getAsInt())
                            .addBox(from.get(0).getAsFloat(), from.get(1).getAsFloat(), from.get(2).getAsFloat(),
                                    sz.get(0).getAsFloat(), sz.get(1).getAsFloat(), sz.get(2).getAsFloat(), faces);
                }
            }
            float[] p = PIVOTS[i];
            PartDefinition pd = root.addOrReplaceChild(name, b, PartPose.offset(p[0], p[1], p[2]));
            Map<String, PartDefinition> groupParts = new HashMap<>();
            for (var e : gb.entrySet()) {
                JsonArray piv = groups.getAsJsonObject(e.getKey()).getAsJsonArray("pivot");
                groupParts.put(e.getKey(), pd.addOrReplaceChild("g_" + e.getKey(), e.getValue(), PartPose.offset(piv.get(0).getAsFloat(),
                        piv.get(1).getAsFloat(), piv.get(2).getAsFloat())));
                made.add(new String[]{name, e.getKey()});
            }
            int n = 0;
            float d = (float) Math.PI / 180.0F;
            for (Tilted tl : tilted.values()) {
                PartDefinition parent = tl.group() == null ? pd : groupParts.get(tl.group());
                float px = 0, py = 0, pz = 0;
                if (tl.group() != null) {
                    JsonArray piv = groups.getAsJsonObject(tl.group()).getAsJsonArray("pivot");
                    px = piv.get(0).getAsFloat();
                    py = piv.get(1).getAsFloat();
                    pz = piv.get(2).getAsFloat();
                }
                parent.addOrReplaceChild("t_" + n++, tl.cubes(), PartPose.offsetAndRotation(tl.ox() - px, tl.oy() - py, tl.oz() - pz,
                        tl.rx() * d, tl.ry() * d, tl.rz() * d));
            }
        }
        ModelPart baked = LayerDefinition.create(mesh, tw, th).bakeRoot();
        HumanoidModel<LivingEntity> model = new MeshArmorModel(baked, readFaces(json, lod, tw, th), bounds, base);
        if (!made.isEmpty()) {
            List<Group> gs = new ArrayList<>();
            for (String[] m : made) {
                JsonArray up = groups.getAsJsonObject(m[1]).getAsJsonArray("up");
                float d = (float) Math.PI / 180.0F;
                gs.add(new Group(m[1], baked.getChild(m[0]).getChild("g_" + m[1]), up.get(0).getAsFloat() * d, up.get(1).getAsFloat() * d,
                        up.get(2).getAsFloat() * d));
            }
            GROUPS.put(model, gs);
            applyGroups(model, "nvg", 0.0F);
        }
        return model;
    }
}
