package fr.heraliz.recolte.client.modeles;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Un modèle cuit de HeraliZ Récolte ({@code assets/heraliz_recolte/modeles/<nom>.json}, écrit par
 * {@code modules/recolte/outils/recolte_blockbench.py} : on le lit, on ne le retouche jamais). Positions en mètres
 * (1 bloc), UV de 0 à 1 (v = 0 en haut de l'image), un quadrilatère par face. Chaque groupe est gardé tel quel dans un
 * {@code float[]} : x y z u v nx ny nz par sommet, 4 sommets par quadrilatère (dessin : {@link Maillage}).
 * <p>Lu une fois au chargement des ressources ({@link ModelesRecolte}) ; les types de rendu sont créés au premier dessin,
 * sur le fil de rendu (leur cache de Minecraft n'est pas fait pour d'autres fils).</p>
 */
public final class ModeleCuit {
    /** Flottants par sommet (x y z u v nx ny nz) et par quadrilatère. */
    public static final int PAR_SOMMET = 8, PAR_QUAD = 4 * PAR_SOMMET;
    private static final float[] VIDE = new float[0];

    public final String nom;
    public final ResourceLocation texture;
    private final Map<String, float[]> groupes;
    private final Map<String, float[]> pivots;
    /** Tous les groupes à la suite : le modèle entier en un envoi. */
    public final float[] tout;

    private RenderType decoupe, decoupeFaces, translucide, translucideFaces;

    ModeleCuit(String nom, ResourceLocation texture, Map<String, float[]> groupes, Map<String, float[]> pivots) {
        this.nom = nom;
        this.texture = texture;
        this.groupes = groupes;
        this.pivots = pivots;
        int n = 0;
        for (float[] g : groupes.values()) {
            n += g.length;
        }
        float[] t = new float[n];
        int o = 0;
        for (float[] g : groupes.values()) {
            System.arraycopy(g, 0, t, o, g.length);
            o += g.length;
        }
        this.tout = t;
    }

    /** Les quadrilatères d'un groupe (tableau vide s'il n'existe pas : rien n'est dessiné). */
    public float[] groupe(String nom) {
        float[] g = groupes.get(nom);
        return g == null ? VIDE : g;
    }

    public boolean aGroupe(String nom) {
        return groupes.containsKey(nom);
    }

    /** Un pivot du modèle ({@code pivots.tronc} des arbres), en mètres ; null s'il n'existe pas. */
    @Nullable
    public float[] pivot(String nom) {
        return pivots.get(nom);
    }

    /** Rendu du cahier des charges : découpé (transparence par seuil), sans élimination des faces arrière. */
    public RenderType decoupe() {
        RenderType r = decoupe;
        if (r == null) {
            decoupe = r = RenderType.entityCutoutNoCull(texture);
        }
        return r;
    }

    /**
     * Découpé AVEC élimination des faces arrière : pour les plans de feuillage, cuits avec leurs deux faces (une par
     * côté, chacune avec sa normale). Sans élimination, les deux faces confondues se disputeraient chaque point
     * (scintillement) et un côté serait éclairé comme l'autre.
     */
    public RenderType decoupeFaces() {
        RenderType r = decoupeFaces;
        if (r == null) {
            decoupeFaces = r = RenderType.entityCutout(texture);
        }
        return r;
    }

    /** Translucide (le tronc tombé qui s'efface). */
    public RenderType translucide() {
        RenderType r = translucide;
        if (r == null) {
            translucide = r = RenderType.entityTranslucent(texture);
        }
        return r;
    }

    /** Translucide avec élimination des faces arrière (feuillage qui s'efface). */
    public RenderType translucideFaces() {
        RenderType r = translucideFaces;
        if (r == null) {
            translucideFaces = r = RenderType.entityTranslucentCull(texture);
        }
        return r;
    }
}
