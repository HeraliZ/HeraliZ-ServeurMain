package fr.heraliz.wonder.wintershowl.client;

import fr.heraliz.wonder.wintershowl.CalculsGivre;

/**
 * Etat de givre des creatures cote client (tableaux de taille fixe, recherche lineaire sur les seules creatures givrees :
 * rien a faire quand il n'y en a pas). Recu du serveur ({@link FxGivre#souffle}) puis tenu a jour comme lui : le gel
 * fond apres le meme delai, une creature gelee attend son eclatement (paquet) ou est oubliee 2 s apres l'heure prevue.
 */
final class EtatsGivre {
    private EtatsGivre() {
    }

    static final int MAX = 192;
    static final int[] ID = new int[MAX];
    static final float[] GEL = new float[MAX];
    /** Tick client du dernier tir recu (le gel apparait a l'arrivee du front). */
    static final float[] T_COUP = new float[MAX];
    /** Tick client du gel complet (-1 si refroidie seulement). */
    static final float[] T_GEL = new float[MAX];
    /** Tick client prevu de l'eclatement. */
    static final float[] T_FIN = new float[MAX];
    static final byte[] PAP = new byte[MAX];
    static final boolean[] CRAQUE = new boolean[MAX];
    static int n;

    static int chercher(int id) {
        for (int i = 0; i < n; i++) if (ID[i] == id) return i;
        return -1;
    }

    /** Nouveau gel (refroidie) ou gel complet ({@code gelee}) a partir du tick {@code t}. */
    static void poser(int id, float gel, boolean gelee, float t, int delaiEclat, int pap) {
        int i = chercher(id);
        if (i < 0) {
            if (n >= MAX) return;
            i = n++;
            ID[i] = id;
            GEL[i] = 0;
            T_GEL[i] = -1;
            CRAQUE[i] = false;
        }
        PAP[i] = (byte) pap;
        T_COUP[i] = t;
        if (gelee) {
            if (T_GEL[i] < 0) {
                T_GEL[i] = t;
                T_FIN[i] = t + delaiEclat;
            }
            GEL[i] = 1f;
        } else if (T_GEL[i] < 0) {
            GEL[i] = Math.max(GEL[i], gel);
        }
    }

    static void retirer(int i) {
        int j = --n;
        ID[i] = ID[j];
        GEL[i] = GEL[j];
        T_COUP[i] = T_COUP[j];
        T_GEL[i] = T_GEL[j];
        T_FIN[i] = T_FIN[j];
        PAP[i] = PAP[j];
        CRAQUE[i] = CRAQUE[j];
    }

    static void retirerId(int id) {
        int i = chercher(id);
        if (i >= 0) retirer(i);
    }

    static boolean gelee(int i, float now) {
        return T_GEL[i] >= 0 && now >= T_GEL[i];
    }

    /** Avancement 0..1 entre le gel complet et l'eclatement prevu. */
    static float avancement(int i, float now) {
        if (T_GEL[i] < 0) return 0;
        return Math.max(0, Math.min(1, (now - T_GEL[i]) / Math.max(1, T_FIN[i] - T_GEL[i])));
    }

    /** Opacite de la couche de glace : voile qui s'epaissit avec le gel, glace qui gagne tout le corps en 6 ticks au gel complet. */
    static float opacite(int i, float now) {
        if (T_GEL[i] >= 0 && now >= T_GEL[i]) {
            float k = Math.min(1, (now - T_GEL[i]) / 6f);
            return 0.55f + 0.38f * k;
        }
        if (now < T_COUP[i]) return 0f;
        float entree = Math.min(1, (now - T_COUP[i]) / 3f);
        return (0.10f + 0.30f * GEL[i]) * entree;
    }

    /** Fissures : 0 (glace neuve), 1 puis 2 a l'approche de l'eclatement. */
    static int fissures(int i, float now) {
        float a = avancement(i, now);
        return a < 0.55f ? 0 : a < 0.84f ? 1 : 2;
    }

    /** Tick client : fonte du gel (comme le serveur), oubli des gelees dont le paquet d'eclatement n'est pas venu. */
    static void tick(float now, double dureeFroidTicks, double fonteParTick) {
        for (int i = 0; i < n; ) {
            if (T_GEL[i] >= 0) {
                if (now > T_FIN[i] + 40) {
                    retirer(i);
                    continue;
                }
            } else if (now >= T_COUP[i]) {
                GEL[i] = CalculsGivre.fondre(GEL[i], (long) (now - T_COUP[i]), dureeFroidTicks, fonteParTick);
                if (GEL[i] <= 0) {
                    retirer(i);
                    continue;
                }
            }
            i++;
        }
    }

    static void vider() {
        n = 0;
    }
}
