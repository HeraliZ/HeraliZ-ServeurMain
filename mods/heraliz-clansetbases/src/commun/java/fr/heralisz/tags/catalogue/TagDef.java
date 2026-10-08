package fr.heralisz.tags.catalogue;

/**
 * Definition d'un tag du catalogue (une entree de tags.json).
 *
 * @param id     identifiant court (aussi suffixe de l'objet tag_&lt;id&gt;)
 * @param ordre  position dans le catalogue (ordre du fichier), stable
 * @param cas    index de la case dans l'atlas 8x8 (0..63)
 * @param taille cote du tag pose, en blocs (0,9 / 1,0 / 1,1 / 1,25 selon la rarete)
 * @param rarete rarete
 * @param nomFr  nom francais
 * @param nomEn  nom anglais
 */
public record TagDef(String id, int ordre, int cas, float taille, Rarete rarete, String nomFr, String nomEn) {

    /** Colonne de la case dans l'atlas (0..7). */
    public int colonne() {
        return cas % TagCatalog.ATLAS_COLONNES;
    }

    /** Ligne de la case dans l'atlas (0..7). */
    public int ligne() {
        return cas / TagCatalog.ATLAS_COLONNES;
    }

    /** UV normalisees de la case dans l'atlas : {u0, v0, u1, v1}. */
    public float[] uv() {
        float pas = 1f / TagCatalog.ATLAS_COLONNES;
        float u0 = colonne() * pas, v0 = ligne() * pas;
        return new float[]{u0, v0, u0 + pas, v0 + pas};
    }

    /** Nom dans la langue demandee (fr par defaut). */
    public String nom(boolean francais) {
        return francais || nomEn == null || nomEn.isBlank() ? nomFr : nomEn;
    }
}
