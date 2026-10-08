package fr.heraliz.wonder.blundergat.serveur;

import fr.heraliz.wonder.blundergat.net.BlunderReseau;
import fr.heraliz.wonder.blundergat.net.Eclats;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;

/**
 * Impacts des obus du Blundergat (balles TaCZ) relevés pendant le tick serveur, puis envoyés en fin de tick : un paquet
 * {@link Eclats} par tireur (les 8 obus d'une gerbe touchent en général dans le même tick). Réserve fixe de 128 impacts
 * par tick (les suivants sont simplement ignorés : l'effet est déjà chargé).
 */
public final class EclatsServeur {
    private EclatsServeur() {
    }

    private static final int MAX = 128;
    private static final ServerLevel[] NIV = new ServerLevel[MAX];
    private static final int[] TIREUR = new int[MAX];
    private static final byte[] PAP = new byte[MAX], FACE = new byte[MAX], DRAP = new byte[MAX];
    private static final float[] POS = new float[MAX * 3];
    private static final boolean[] PRIS = new boolean[MAX];
    private static int n;

    /** Un obus a touché : point, face du bloc (-1 = créature), drapeaux. */
    public static void ajouter(ServerLevel niveau, int tireur, int pap, double x, double y, double z, int face, byte drapeaux) {
        if (n >= MAX) return;
        int i = n++;
        NIV[i] = niveau;
        TIREUR[i] = tireur;
        PAP[i] = (byte) pap;
        FACE[i] = (byte) face;
        DRAP[i] = drapeaux;
        POS[i * 3] = (float) x;
        POS[i * 3 + 1] = (float) y;
        POS[i * 3 + 2] = (float) z;
        PRIS[i] = false;
    }

    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || n == 0) return;
        long t0 = System.nanoTime();
        int paquets = 0;
        for (int i = 0; i < n; i++) {
            if (PRIS[i]) continue;
            // regroupe les impacts du même tireur dans le même monde
            int m = 0;
            for (int j = i; j < n && m < Eclats.MAX; j++) if (!PRIS[j] && TIREUR[j] == TIREUR[i] && NIV[j] == NIV[i]) m++;
            float[] pos = new float[m * 3];
            byte[] f = new byte[m], d = new byte[m];
            int k = 0;
            for (int j = i; j < n && k < m; j++) {
                if (PRIS[j] || TIREUR[j] != TIREUR[i] || NIV[j] != NIV[i]) continue;
                PRIS[j] = true;
                pos[k * 3] = POS[j * 3];
                pos[k * 3 + 1] = POS[j * 3 + 1];
                pos[k * 3 + 2] = POS[j * 3 + 2];
                f[k] = FACE[j];
                d[k] = DRAP[j];
                k++;
            }
            BlunderReseau.autour(NIV[i], pos[0], pos[1], pos[2], new Eclats(TIREUR[i], PAP[i], k, pos, f, d));
            paquets++;
        }
        for (int i = 0; i < n; i++) NIV[i] = null;
        int impacts = n;
        n = 0;
        BlunderPerf.eclats(System.nanoTime() - t0, impacts, paquets);
    }
}
