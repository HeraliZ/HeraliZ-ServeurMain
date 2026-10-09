package fr.heraliz.airstrike.client.model;

import fr.heraliz.airstrike.AirstrikeMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the {@code .geo} files produced by tools/modelgen.
 * Format (model space, 1/16 block units):
 * <pre>
 * texture W H
 * pass &lt;top-level part&gt; solid|glass|emissive
 * point &lt;name&gt; x y z          (natural frame: x right, y up, z back)
 * part &lt;path&gt; px py pz rx ry rz
 * box x y z w h d inflate u1 v1 u2 v2 (x6: down up west north east south)   (belongs to the last part)
 * quad (x y z u v nx ny nz) x4                  smooth surface, one normal per corner (belongs to the last part)
 * </pre>
 */
public final class GeoLoader {
    public static final class Meta {
        public int texW = 64, texH = 64;
        public final Map<String, String> passes = new LinkedHashMap<>();
        public final Map<String, Vec3> points = new HashMap<>();
    }

    private GeoLoader() {
    }

    private static List<String> read(ResourceLocation geo) {
        List<String> lines = new ArrayList<>();
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(geo);
            if (res.isPresent()) {
                try (BufferedReader r = res.get().openAsReader()) {
                    String l;
                    while ((l = r.readLine()) != null) lines.add(l);
                }
                return lines;
            }
        } catch (Exception e) {
            AirstrikeMod.LOGGER.warn("Could not read {} from resources, trying classpath", geo, e);
        }
        String cp = "/assets/" + geo.getNamespace() + "/" + geo.getPath();
        try (InputStream in = GeoLoader.class.getResourceAsStream(cp)) {
            if (in != null) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String l;
                    while ((l = r.readLine()) != null) lines.add(l);
                }
            } else {
                AirstrikeMod.LOGGER.error("Missing model geometry {}", geo);
            }
        } catch (Exception e) {
            AirstrikeMod.LOGGER.error("Could not read model geometry {}", geo, e);
        }
        return lines;
    }

    public static GeoModel load(ResourceLocation geo) {
        Meta meta = new Meta();
        GeoPart root = new GeoPart("root", 0, 0, 0, 0, 0, 0);
        Map<String, GeoPart> parts = new HashMap<>();
        GeoPart current = null;
        List<String> lines = read(geo);
        // first pass: texture size (needed before boxes)
        for (String raw : lines) {
            String[] t = raw.trim().split("\\s+");
            if (t.length >= 3 && t[0].equals("texture")) {
                meta.texW = Integer.parseInt(t[1]);
                meta.texH = Integer.parseInt(t[2]);
            }
        }
        int boxes = 0, quads = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] t = line.split("\\s+");
            try {
                switch (t[0]) {
                    case "pass" -> meta.passes.put(t[1], t[2]);
                    case "point" -> meta.points.put(t[1], new Vec3(f(t[2]), f(t[3]), f(t[4])));
                    case "part" -> {
                        String path = t[1];
                        int slash = path.lastIndexOf('/');
                        String name = slash < 0 ? path : path.substring(slash + 1);
                        GeoPart parent = slash < 0 ? root : parts.getOrDefault(path.substring(0, slash), root);
                        current = new GeoPart(name, f(t[2]), f(t[3]), f(t[4]), f(t[5]), f(t[6]), f(t[7]));
                        parent.children.put(name, current);
                        parts.put(path, current);
                    }
                    case "box" -> {
                        if (current != null && t.length >= 32) {
                            float[] uvs = new float[24];
                            for (int i = 0; i < 24; i++) uvs[i] = f(t[8 + i]);
                            current.addBox(f(t[1]), f(t[2]), f(t[3]), f(t[4]), f(t[5]), f(t[6]), f(t[7]), uvs,
                                    meta.texW, meta.texH);
                            boxes++;
                        }
                    }
                    case "quad" -> {
                        if (current != null && t.length >= 33) {
                            float[] q = new float[32];
                            for (int i = 0; i < 32; i++) q[i] = f(t[1 + i]);
                            current.addQuad(q, meta.texW, meta.texH);
                            quads++;
                        }
                    }
                    default -> {
                    }
                }
            } catch (RuntimeException e) {
                AirstrikeMod.LOGGER.warn("Bad line in {}: {}", geo, line);
            }
        }
        AirstrikeMod.LOGGER.debug("Loaded {} ({} parts, {} boxes, {} quads)", geo, parts.size(), boxes, quads);
        return new GeoModel(root, meta);
    }

    private static float f(String s) {
        return Float.parseFloat(s);
    }
}
