package fr.heralisz.tags.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;

/**
 * Lot de decalques d'une meme cellule de 32 blocs, a alpha plein, fondus en un seul tampon GPU : un lot = un appel de
 * dessin, quel que soit le nombre de tags. Reconstruit (concatenation des copies natives des sommets, deja relatives
 * a l'origine de la cellule) seulement quand l'ensemble des tags change : pose, retrait, expiration, fin
 * d'apparition, debut de fondu, recuisson. Les tags en apparition / fondu sont dessines a part par le DecalRenderer.
 */
final class DecalLot implements AutoCloseable {
    /** Taille d'une cellule (blocs) ; les positions relatives restent petites, donc precises en float. */
    static final int CELLULE = 32;
    private static final BufferBuilder BUILDER = new BufferBuilder(256 * 1024);

    final long cle;
    final double ox, oy, oz;
    final ArrayList<Decal> decals = new ArrayList<>();
    private VertexBuffer tampon;
    AABB bornes;
    int triangles;

    DecalLot(long cle, double ox, double oy, double oz) {
        this.cle = cle;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
    }

    /** Cle d'une cellule a partir des coordonnees d'un point. */
    static long cle(double x, double y, double z) {
        long cx = Math.floorDiv((long) Math.floor(x), CELLULE), cy = Math.floorDiv((long) Math.floor(y), CELLULE), cz = Math.floorDiv((long) Math.floor(z), CELLULE);
        return ((cx & 0x1FFFFFL) << 42) | ((cy & 0x1FFFFFL) << 21) | (cz & 0x1FFFFFL);
    }

    /** Origine (multiple de la cellule) de la coordonnee donnee. */
    static double origine(double v) {
        return Math.floorDiv((long) Math.floor(v), CELLULE) * (double) CELLULE;
    }

    /** Reconstruit le tampon a partir des decalques du lot (thread de rendu). */
    void reconstruire() {
        triangles = 0;
        if (decals.isEmpty()) {
            bornes = null;
            return;
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        BUILDER.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
        try {
            for (int i = 0; i < decals.size(); i++) {
                Decal d = decals.get(i);
                if (d.sommets == null) continue;
                d.sommets.rewind();
                BUILDER.putBulkData(d.sommets);
                triangles += d.triangles;
                minX = Math.min(minX, d.bornes.minX);
                minY = Math.min(minY, d.bornes.minY);
                minZ = Math.min(minZ, d.bornes.minZ);
                maxX = Math.max(maxX, d.bornes.maxX);
                maxY = Math.max(maxY, d.bornes.maxY);
                maxZ = Math.max(maxZ, d.bornes.maxZ);
            }
            BufferBuilder.RenderedBuffer rendu = BUILDER.end();
            if (tampon == null) tampon = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            tampon.bind();
            tampon.upload(rendu);
            VertexBuffer.unbind();
        } finally {
            if (BUILDER.building()) BUILDER.discard();
        }
        bornes = triangles > 0 ? new AABB(minX, minY, minZ, maxX, maxY, maxZ) : null;
    }

    /** Dessine le lot (tampon lie puis delie par l'appelant via VertexBuffer.unbind). */
    void dessiner() {
        tampon.bind();
        tampon.draw();
    }

    @Override
    public void close() {
        if (tampon != null) {
            tampon.close();
            tampon = null;
        }
        decals.clear();
        triangles = 0;
    }
}
