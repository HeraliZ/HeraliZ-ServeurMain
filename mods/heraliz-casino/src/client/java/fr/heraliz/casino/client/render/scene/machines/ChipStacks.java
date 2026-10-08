package fr.heraliz.casino.client.render.scene.machines;

import fr.heraliz.casino.client.render.mesh.Mat;
import fr.heraliz.casino.client.render.mesh.Mesh;
import fr.heraliz.casino.client.render.mesh.MeshBuilder;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Pose;

/**
 * Piles de jetons de poker posees sur les tables : une pile par valeur, hauteur selon le nombre, un peu de travers.
 * Faces et tranches sales de chaque valeur (1 ivoire, 10 rouge, 100 vert, 1 000 ardoise, 10 000 noir) dans la
 * feuille commune des pieces de recuperation (Recup.chipFace / chipEdge, peintes par tools/textures/PaintChips).
 */
public final class ChipStacks {
    /** Valeurs (voir economy.Chips). */
    public static final int[] VALUES = {1, 10, 100, 1000, 10000};
    public static final int MAX = 10;

    /** Piles toutes faites : [valeur][nombre de jetons]. */
    private final Mesh[][] stacks = new Mesh[VALUES.length][MAX + 1];
    public final float radius, thick;

    public ChipStacks(float radius, float thick, int segs) {
        // un peu plus petits que la place prevue : les piles voisines ne se touchent pas
        this.radius = radius * 0.86f;
        this.thick = thick * 0.8f;
        radius = this.radius;
        for (int kind = 0; kind < VALUES.length; kind++) {
            Mat face = Recup.chipFace(kind), edge = Recup.chipEdge(kind);
            for (int n = 1; n <= MAX; n++) {
                MeshBuilder b = new MeshBuilder();
                for (int k = 0; k < n; k++) {
                    // pile de travers : chaque jeton decale et tourne au hasard (toujours le meme hasard)
                    float jx = (hash(k * 7 + n + kind * 3) - 0.5f) * radius * 0.12f, jz = (hash(k * 13 + n * 3 + kind) - 0.5f) * radius * 0.12f;
                    b.push().translate(jx, k * this.thick, jz).rotateY(k * 47 + n * 11 + kind * 29).rotateZ((hash(k * 5 + kind) - 0.5f) * 2.5f);
                    b.cylinder(radius, 0, this.thick * 0.96f, segs, edge, k == 0 ? face : null, face);
                    b.pop();
                }
                stacks[kind][n] = b.build();
            }
        }
    }

    private static float hash(int i) {
        int h = i * 0x27d4eb2d;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h & 0xFFFF) / 65535f;
    }

    /** Pile de n jetons (1..MAX) de la valeur d'indice "kind", base en (x, y, z). */
    public void stackOf(Pose p, DrawList out, int n, int kind, float x, float y, float z) {
        if (n <= 0) return;
        n = Math.min(MAX, n);
        kind = Math.max(0, Math.min(VALUES.length - 1, kind));
        p.push().translate(x, y, z);
        out.sheet(Recup.SHEET);
        out.solid(p, stacks[kind][n], 0);
        out.sheet(null);
        p.pop();
    }

    /**
     * Represente un montant par des piles cote a cote (plus grosses valeurs d'abord), centrees en (x, y, z).
     * @return nombre de piles dessinees
     */
    public int amount(Pose p, DrawList out, long amount, float x, float y, float z, float spacing, int maxStacks) {
        if (amount <= 0) return 0;
        int[] counts = new int[VALUES.length];
        long rest = amount;
        for (int k = VALUES.length - 1; k >= 0; k--) {
            counts[k] = (int) Math.min(MAX * 3L, rest / VALUES[k]);
            rest -= (long) counts[k] * VALUES[k];
        }
        int stacksN = 0;
        int[] kinds = new int[maxStacks];
        int[] ns = new int[maxStacks];
        for (int k = VALUES.length - 1; k >= 0 && stacksN < maxStacks; k--) {
            int c = counts[k];
            while (c > 0 && stacksN < maxStacks) {
                int n = Math.min(MAX, c);
                kinds[stacksN] = k;
                ns[stacksN] = n;
                stacksN++;
                c -= n;
            }
        }
        float x0 = x - (stacksN - 1) * spacing / 2f;
        for (int i = 0; i < stacksN; i++) stackOf(p, out, ns[i], kinds[i], x0 + i * spacing, y, z);
        return stacksN;
    }
}
