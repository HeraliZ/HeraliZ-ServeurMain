package fr.heraliz.armory.armor;

import java.util.List;

/** GENERE par tools/build_all.py : ne pas modifier a la main. */
public final class SetCatalog {
    private SetCatalog() {}

    public static final List<SetDef> SETS = List.of(
            new SetDef("citadin", 1, "bleu", true),
            new SetDef("rescape", 2, "rouge", true),
            new SetDef("ranger", 3, "beige", true),
            new SetDef("woodland", 4, "vert", true),
            new SetDef("nomade", 5, "beige", true),
            new SetDef("gardien", 6, "bleu", true),
            new SetDef("cobra", 7, "vert", true),
            new SetDef("nocturne_cheveche", 8, "vert", false),
            new SetDef("sentinelle", 8, "vert", true),
            new SetDef("rempart", 9, "gris", true),
            new SetDef("vipere", 10, "vert", true),
            new SetDef("siberie", 11, "vert", true),
            new SetDef("scorpion", 12, "beige", true),
            new SetDef("contrat", 13, "vert", true),
            new SetDef("belier", 14, "noir", true),
            new SetDef("nocturne_hulotte", 14, "vert", false),
            new SetDef("fennec", 15, "beige", true),
            new SetDef("blizzard", 16, "blanc", true),
            new SetDef("barricade", 17, "bleu", true),
            new SetDef("beton", 18, "gris", true),
            new SetDef("meute", 19, "gris", true),
            new SetDef("nocturne_effraie", 19, "noir", false),
            new SetDef("corbeau", 20, "noir", true),
            new SetDef("ours", 21, "marron", true),
            new SetDef("mastodonte", 22, "vert", true),
            new SetDef("titan", 23, "noir", true),
            new SetDef("nocturne_grand_duc", 24, "noir", false)
    );
}
