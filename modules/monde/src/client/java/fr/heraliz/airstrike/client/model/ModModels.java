package fr.heraliz.airstrike.client.model;

import fr.heraliz.airstrike.AirstrikeMod;
import net.minecraft.resources.ResourceLocation;

/** Lazily loaded generated models (reloaded with the resource packs). */
public final class ModModels {
    private static GeoModel jet, cargo, ordnance, crate, designator, helicopter, helicopterWreck;

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

    /**
     * Hélicoptère de l'événement (25/10). Tant que le fichier n'est pas livré, le chargeur rend un modèle vide (une seule
     * ligne au journal par rechargement des ressources) : le rendu ne dessine rien.
     */
    public static GeoModel helicopter() {
        if (helicopter == null) helicopter = GeoLoader.load(geo("helicopter"));
        return helicopter;
    }

    /** Épave de l'hélicoptère (même origine que l'hélicoptère). */
    public static GeoModel helicopterWreck() {
        if (helicopterWreck == null) helicopterWreck = GeoLoader.load(geo("helicopter_wreck"));
        return helicopterWreck;
    }

    /** Called on resource reload: models are re-read on next use. */
    public static void invalidate() {
        jet = cargo = ordnance = crate = designator = helicopter = helicopterWreck = null;
    }
}
