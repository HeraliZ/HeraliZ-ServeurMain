package fr.heraliz.usure.regles;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Categories d'armes, chacune avec ses valeurs par defaut. Le type TaCZ ({@code pistol}, {@code smg}, {@code rifle},
 * {@code sniper}, {@code shotgun}, {@code mg}, {@code rpg}) donne la categorie ; deux cas sont deduits des donnees de
 * l'arme : un fusil qui ne tire qu'au coup par coup, ou un fusil de precision semi-automatique, est un DMR. Les armes
 * qui tirent des munitions « corps a corps » (couteaux faits en armes TaCZ) sont rangees a part. Une surcharge par arme
 * peut imposer la categorie.
 */
public enum Categorie {
    //            cle             usure  pluie eau  silencieux
    PISTOLET("pistolet", 0.10f, 25, 100, 30),
    PM("pm", 0.07f, 25, 100, 30),
    FUSIL("fusil", 0.08f, 25, 100, 30),
    DMR("dmr", 0.12f, 25, 100, 30),
    PRECISION("precision", 0.18f, 25, 100, 30),
    POMPE("pompe", 0.14f, 25, 100, 30),
    MITRAILLEUSE("mitrailleuse", 0.04f, 25, 100, 30),
    LANCEUR("lanceur", 0.40f, 25, 100, 0),
    MELEE("melee", 0.0f, 0, 0, 0);

    public static final Categorie[] TOUTES = values();

    /** Cle du fichier de reglages et des traductions ({@code heraliz_usure.categorie.<cle>}). */
    public final String cle;
    /** Valeurs par defaut : usure par tir (% d'etat), surplus pluie / eau / silencieux (%). */
    public final float usure, pluie, eau, silencieux;

    Categorie(String cle, float usure, float pluie, float eau, float silencieux) {
        this.cle = cle;
        this.usure = usure;
        this.pluie = pluie;
        this.eau = eau;
        this.silencieux = silencieux;
    }

    public String cleTraduction() {
        return "heraliz_usure.categorie." + cle;
    }

    @Nullable
    public static Categorie parCle(String cle) {
        if (cle == null) return null;
        String c = cle.trim().toLowerCase(Locale.ROOT);
        for (Categorie k : TOUTES) if (k.cle.equals(c) || k.name().toLowerCase(Locale.ROOT).equals(c)) return k;
        // noms anglais acceptes dans le fichier
        return switch (c) {
            case "pistol" -> PISTOLET;
            case "smg" -> PM;
            case "rifle" -> FUSIL;
            case "sniper" -> PRECISION;
            case "shotgun" -> POMPE;
            case "mg", "lmg" -> MITRAILLEUSE;
            case "rpg", "launcher" -> LANCEUR;
            case "knife", "couteau", "corps_a_corps" -> MELEE;
            default -> null;
        };
    }
}
