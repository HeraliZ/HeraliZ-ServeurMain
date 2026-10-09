package com.heraliz.extraction.serveur;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registre des zones d'extraction fixes créées par les admins (sauvegardé avec le monde,
 * dans data/heraliz_extraction_zones.dat). Chaque zone existe aussi sous forme d'entité
 * dans le monde ; une entité dont le nom n'est plus dans ce registre se supprime d'elle-même.
 */
public class ExtractionZones extends SavedData {
    private static final String FILE = "heraliz_extraction_zones";

    public record Zone(String name, ResourceLocation dimension, Vec3 pos, float radius, int durationSeconds) {
    }

    private final Map<String, Zone> zones = new LinkedHashMap<>();

    public static ExtractionZones get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ExtractionZones::load, ExtractionZones::new, FILE);
    }

    public boolean contains(String name) {
        return this.zones.containsKey(name);
    }

    public Zone get(String name) {
        return this.zones.get(name);
    }

    public Collection<Zone> all() {
        return this.zones.values();
    }

    public void add(Zone zone) {
        this.zones.put(zone.name(), zone);
        setDirty();
    }

    public void remove(String name) {
        if (this.zones.remove(name) != null) {
            setDirty();
        }
    }

    private static ExtractionZones load(CompoundTag tag) {
        ExtractionZones data = new ExtractionZones();
        ListTag list = tag.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag z = list.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(z.getString("Dimension"));
            if (dim == null) {
                continue;
            }
            data.zones.put(z.getString("Name"), new Zone(z.getString("Name"), dim,
                    new Vec3(z.getDouble("X"), z.getDouble("Y"), z.getDouble("Z")),
                    z.getFloat("Radius"), z.getInt("Duration")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Zone zone : this.zones.values()) {
            CompoundTag z = new CompoundTag();
            z.putString("Name", zone.name());
            z.putString("Dimension", zone.dimension().toString());
            z.putDouble("X", zone.pos().x);
            z.putDouble("Y", zone.pos().y);
            z.putDouble("Z", zone.pos().z);
            z.putFloat("Radius", zone.radius());
            z.putInt("Duration", zone.durationSeconds());
            list.add(z);
        }
        tag.put("Zones", list);
        return tag;
    }
}
