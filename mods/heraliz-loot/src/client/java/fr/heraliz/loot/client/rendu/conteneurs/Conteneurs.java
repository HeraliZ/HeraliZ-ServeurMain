package fr.heraliz.loot.client.rendu.conteneurs;

import fr.heraliz.loot.client.rendu.mesh.Mesh;
import fr.heraliz.loot.client.rendu.mesh.MeshBuilder;
import fr.heraliz.loot.client.rendu.mesh.Occlusion;
import fr.heraliz.loot.client.rendu.mesh.Salissure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Modèles 3D des conteneurs T1-T6 au niveau du casino (Java pur). Fixes : ils sont cuits une fois dans le rendu du
 * terrain (comme un modèle Blockbench), donc gratuits même par centaines. Repère : pixels, sol en y = 0, avant vers +Z,
 * dos au mur en z = 0 (rien derrière : le joueur doit pouvoir poser le bloc d'appui). Un conteneur sans modèle ici garde
 * son ancien modèle de blocs. Les matières viennent de plusieurs pages d'atlas ({@link AtlasConteneurs}).
 * Les nouveaux conteneurs (lots LotXxx) se déclarent avec {@link #ajouter} : nom, tiers, matière, noms FR / EN.
 */
public final class Conteneurs {
    private Conteneurs() {
    }

    /** Fiche d'un conteneur déclaré en Java (les anciens ont leur fiche dans tools/modeles_*.py). */
    public record Fiche(String nom, String tiers, String matiere, String fr, String en) {
    }

    private static final Map<String, Consumer<MeshBuilder>> OPAQUES = new LinkedHashMap<>();
    private static final Map<String, Consumer<MeshBuilder>> VERRES = new LinkedHashMap<>();
    private static final Map<String, Fiche> FICHES = new LinkedHashMap<>();
    private static final Map<String, Float> USURE = new java.util.HashMap<>();
    private static final Map<String, Mesh[]> FAITS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, float[][]> COLLISIONS = new java.util.HashMap<>();

    /** Lots de nouveaux modèles (classes LotXxx de ce paquet, chargées si présentes : les outils compilent un lot seul). */
    private static final String[] LOTS = {"T1a", "T1b", "T2b", "T3b", "T4b", "T56"};

    private static void civil(String nom, Consumer<MeshBuilder> f) {
        OPAQUES.put(nom, f);
    }

    /**
     * Déclare un nouveau conteneur.
     *
     * @param tiers   "t1" ... "t6"
     * @param matiere "bois", "metal", "plastique", "tissu", "carton" (son et dureté du bloc)
     * @param fr      nom affiché en français (sans le préfixe [T1], ajouté seul)
     */
    public static void ajouter(String nom, String tiers, String matiere, String fr, String en, Consumer<MeshBuilder> f) {
        if (OPAQUES.containsKey(nom)) throw new IllegalStateException("conteneur en double : " + nom);
        OPAQUES.put(nom, f);
        FICHES.put(nom, new Fiche(nom, tiers, matiere, fr, en));
    }

    /** Parties en verre (passe translucide) d'un conteneur. */
    public static void verre(String nom, Consumer<MeshBuilder> f) {
        VERRES.put(nom, f);
    }

    /** Boîtes de collision à la main (px, repère du modèle) quand la boîte englobante ne convient pas. */
    public static void collision(String nom, float[]... boites) {
        COLLISIONS.put(nom, boites);
    }

    /** Usure cuite dans les sommets (terre au pied, crasse) : 1 par défaut (0,6 pour le T6, entretenu), 0 = aucune. */
    public static void usure(String nom, float force) {
        USURE.put(nom, force);
    }

    public static Fiche fiche(String nom) {
        return FICHES.get(nom);
    }

    public static Iterable<Fiche> fiches() {
        return FICHES.values();
    }

    static {
        civil("t2_caisse_outils", ConteneursT2::caisseOutils);
        civil("t2_caisse_ferme", ConteneursT2::caisseFerme);
        civil("t2_boite_aux_lettres", ConteneursT2::boiteAuxLettres);
        civil("t2_frigo", ConteneursT2::frigo);
        civil("t2_valise", ConteneursT2::valise);
        civil("t2_cartons", ConteneursT2::cartons);
        civil("t2_commode", ConteneursT2::commode);
        civil("t2_sacs_grain", ConteneursT2::sacsGrain);
        civil("t2_caisses_pommes", ConteneursT2::caissesPommes);
        civil("t2_glaciere", ConteneursT2::glaciere);
        civil("t2_panier", ConteneursT2::panier);
        civil("t2_bac_rangement", ConteneursT2::bacRangement);
        civil("t2_caisse_bouteilles", ConteneursT2::caisseBouteilles);
        civil("t2_sac_sport", ConteneursT2::sacSport);
        civil("t2_malle", ConteneursT2::malle);
        civil("t2_seau", ConteneursT2::seau);
        civil("t2_caisse_lait", ConteneursT2::caisseLait);
        civil("t2_coffre_jardin", ConteneursT2::coffreJardin);
        civil("t2_boites_chaussures", ConteneursT2::boitesChaussures);
        civil("t2_sacoche", ConteneursT2::sacoche);
        civil("t3_valise_arme", ConteneursT3::valiseArme);
        civil("t3_housse_fusil", ConteneursT3::housseFusil);
        civil("t3_caisse_police", ConteneursT3::caissePolice);
        civil("t3_sac_police", ConteneursT3::sacPolice);
        civil("t3_armoire_fusils", ConteneursT3::armoireFusils);
        VERRES.put("t3_armoire_fusils", ConteneursT3::armoireFusilsVerre);
        civil("t3_caisse_preuves", ConteneursT3::caissePreuves);
        civil("t3_gilet_caisse", ConteneursT3::giletCaisse);
        civil("t3_sac_chasse", ConteneursT3::sacChasse);
        civil("t3_coffre_chasse", ConteneursT3::coffreChasse);
        OPAQUES.put("t4_caisses_munitions", ConteneursT4::caissesMunitions);
        OPAQUES.put("t4_caisse_fusils", ConteneursT4::caisseFusils);
        OPAQUES.put("t4_cantine", ConteneursT4::cantine);
        OPAQUES.put("t4_paquetage", ConteneursT4::paquetage);
        OPAQUES.put("t4_caisse_grenades", ConteneursT4::caisseGrenades);
        OPAQUES.put("t4_caisse_radio", ConteneursT4::caisseRadio);
        OPAQUES.put("t4_caisse_filet", ConteneursT4::caisseFilet);
        OPAQUES.put("t5_caisse_armes_onu", ConteneursT5T6::caisseArmesOnu);
        OPAQUES.put("t6_coffre_code", ConteneursT5T6::coffreCode);
        OPAQUES.put("t6_mallette_ouverte", ConteneursT5T6::malletteOuverte);
        OPAQUES.put("t6_caisse_missiles", ConteneursT5T6::caisseMissiles);
        VERRES.put("t6_caisse_missiles", ConteneursT5T6::caisseMissilesVerre);
        OPAQUES.put("t6_vitrine_arme", ConteneursT5T6::vitrineArme);
        VERRES.put("t6_vitrine_arme", ConteneursT5T6::vitrineArmeVerre);
        OPAQUES.put("t6_conteneur_securise", ConteneursT5T6::conteneurSecurise);
    }

    static {
        for (String lot : LOTS) {
            try {
                Class.forName(Conteneurs.class.getPackageName() + ".Lot" + lot).getMethod("enregistrer").invoke(null);
            } catch (ClassNotFoundException e) {
                // lot absent : outil compilé avec un autre lot
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("lot " + lot, e);
            }
        }
    }

    public static Iterable<String> noms() {
        return OPAQUES.keySet();
    }

    public static boolean existe(String nom) {
        return OPAQUES.containsKey(nom);
    }

    /** {opaque (occlusion ambiante cuite), verre (ou Mesh.EMPTY)}, ou null si le conteneur n'a pas de modèle ici. */
    public static Mesh[] maillages(String nom) {
        Mesh[] m = FAITS.get(nom);
        if (m == null) {
            Consumer<MeshBuilder> f = OPAQUES.get(nom);
            if (f == null) return null;
            MeshBuilder b = new MeshBuilder();
            f.accept(b);
            Occlusion.bake(b, 0.35f, 2.6f, 0.55f, 0f);
            Salissure.appliquer(b, USURE.getOrDefault(nom, nom.startsWith("t6_") ? 0.6f : 1f), nom.hashCode());
            Mesh opaque = b.build();
            Mesh verre = Mesh.EMPTY;
            Consumer<MeshBuilder> g = VERRES.get(nom);
            if (g != null) {
                MeshBuilder v = new MeshBuilder();
                g.accept(v);
                verre = v.build();
            }
            m = new Mesh[]{opaque, verre};
            FAITS.put(nom, m);
        }
        return m;
    }

    /**
     * Boîtes de collision (px, repère du modèle, avant vers +Z) : la boîte englobante du modèle, sauf pour les objets
     * dont une pièce fine dépasse (manche du balai, antenne, bouteille posée sur le frigo...).
     */
    public static float[][] collisions(String nom) {
        float[][] o = COLLISIONS.get(nom);
        if (o != null) return o;
        float[] b = bornes(nom);
        return b == null ? null : new float[][]{b};
    }

    static {
        COLLISIONS.put("t2_seau", new float[][]{{3.0f, 0, 0.6f, 15.0f, 5.0f, 7.0f}});
        COLLISIONS.put("t2_frigo", new float[][]{{2.8f, 0, 0.5f, 13.2f, 29.6f, 10.6f}});
        COLLISIONS.put("t4_caisse_radio", new float[][]{{2.5f, 0, 0.5f, 13.5f, 12.8f, 7.5f}});
        COLLISIONS.put("t2_panier", new float[][]{{3.6f, 0, 1.6f, 12.4f, 4.4f, 7.2f}});
    }

    /** Boîte englobante du modèle (px, avant vers +Z) : {x0, y0, z0, x1, y1, z1}. */
    public static float[] bornes(String nom) {
        Mesh[] m = maillages(nom);
        if (m == null) return null;
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (Mesh mesh : m) {
            float[] d = mesh.data();
            for (int i = 0; i < mesh.vertexCount() * Mesh.STRIDE; i += Mesh.STRIDE) {
                for (int k = 0; k < 3; k++) {
                    b[k] = Math.min(b[k], d[i + k]);
                    b[k + 3] = Math.max(b[k + 3], d[i + k]);
                }
            }
        }
        return b;
    }
}
