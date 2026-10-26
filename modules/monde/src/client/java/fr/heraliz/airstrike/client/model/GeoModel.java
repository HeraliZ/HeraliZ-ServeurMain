package fr.heraliz.airstrike.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Geometry + metadata of a generated model, with quick access to named parts. */
public final class GeoModel {
    public final GeoPart root;
    public final GeoLoader.Meta meta;
    public final List<GeoPart> solid = new ArrayList<>();
    public final List<GeoPart> glass = new ArrayList<>();
    public final Map<String, GeoPart> topLevel = new HashMap<>();
    private final Map<String, GeoPart> cache = new HashMap<>();

    public GeoModel(GeoPart root, GeoLoader.Meta meta) {
        this.root = root;
        this.meta = meta;
        for (Map.Entry<String, String> e : meta.passes.entrySet()) {
            GeoPart p = root.child(e.getKey());
            if (p == null) continue;
            topLevel.put(e.getKey(), p);
            switch (e.getValue()) {
                case "glass" -> glass.add(p);
                case "emissive" -> {
                }
                default -> solid.add(p);
            }
        }
    }

    /** Part by slash separated path, or null. */
    public GeoPart part(String path) {
        GeoPart c = cache.get(path);
        if (c != null) return c;
        GeoPart p = root;
        for (String s : path.split("/")) {
            p = p.child(s);
            if (p == null) return null;
        }
        cache.put(path, p);
        return p;
    }

    public Vec3 point(String name) {
        return meta.points.getOrDefault(name, Vec3.ZERO);
    }

    public void renderList(List<GeoPart> parts, PoseStack ps, VertexConsumer vc, int light, int overlay,
                           float r, float g, float b, float a) {
        for (GeoPart p : parts) p.render(ps, vc, light, overlay, r, g, b, a);
    }

    public void renderPart(String topLevelName, PoseStack ps, VertexConsumer vc, int light, int overlay,
                           float r, float g, float b, float a) {
        GeoPart p = topLevel.get(topLevelName);
        if (p != null) p.render(ps, vc, light, overlay, r, g, b, a);
    }
}
