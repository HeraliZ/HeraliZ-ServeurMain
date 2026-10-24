package fr.heraliz.airstrike.client.model;

import fr.heraliz.airstrike.AirstrikeMod;
import net.minecraft.resources.ResourceLocation;

/** Lazily loaded generated models (reloaded with the resource packs). */
public final class ModModels {
    private static GeoModel jet, cargo, ordnance, crate, designator, helico, epave;

    private ModModels() {
    }

    public static ResourceLocation geo(String name) {
        return new ResourceLocation(AirstrikeMod.MODID, "geo/" + name + ".geo");
    }

    public static ResourceLocation texture(String name) {
        return new ResourceLocation(AirstrikeMod.MODID, "textures/entity/" + name + ".png");
    }

    public static GeoModel jet() {
        if (jet == null) jet = GeoLoader.load(geo("attack_jet"));
        return jet;
    }

    public static GeoModel cargo() {
        if (cargo == null) cargo = GeoLoader.load(geo("cargo_plane"));
        return cargo;
    }

    public static GeoModel ordnance() {
        if (ordnance == null) ordnance = GeoLoader.load(geo("ordnance"));
        return ordnance;
    }

    public static GeoModel designator() {
        if (designator == null) designator = GeoLoader.load(geo("designator"));
        return designator;
    }

    public static GeoModel crate() {
        if (crate == null) crate = GeoLoader.load(geo("supply_crate"));
        return crate;
    }

    /** Hélicoptère de patrouille (outils/modele/helico.py). */
    public static GeoModel helico() {
        if (helico == null) helico = GeoLoader.load(geo("helico"));
        return helico;
    }

    /** Morceaux de son épave et caisse T6 (même placement de texture, peinture brûlée). */
    public static GeoModel epave() {
        if (epave == null) epave = GeoLoader.load(geo("helico_epave"));
        return epave;
    }

    /** Called on resource reload: models are re-read on next use. */
    public static void invalidate() {
        jet = cargo = ordnance = crate = designator = helico = epave = null;
    }
}
