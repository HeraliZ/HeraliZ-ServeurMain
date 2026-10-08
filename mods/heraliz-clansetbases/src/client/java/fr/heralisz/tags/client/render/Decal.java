package fr.heralisz.tags.client.render;

import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.world.phys.AABB;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Un tag cuit : ses sommets (POSITION_COLOR_TEX_LIGHTMAP, triangles, positions relatives a l'origine de sa cellule
 * de 32 blocs) gardes en memoire native pour les lots ({@link DecalLot}), son tampon GPU propre (utilise seulement
 * pendant l'apparition et le fondu, quand l'alpha lui est particulier), ses bornes monde (culling) et le point
 * d'impact. Ferme tampon et memoire a la fermeture. Rien n'est alloue au rendu : tout ce dont le DecalRenderer a
 * besoin est ici.
 */
public final class Decal implements AutoCloseable {
    /** Tampon GPU individuel ; null une fois ferme. */
    VertexBuffer tampon;
    /** Copie native des sommets (pour les lots) ; null une fois fermee. */
    ByteBuffer sommets;
    /** Bornes monde du decalque (legerement elargies). */
    public final AABB bornes;
    /** Point d'impact. */
    public final double x, y, z;
    /** Origine de la cellule (multiple de 32) a laquelle les positions des sommets sont relatives, et sa cle. */
    final double ox, oy, oz;
    final long cle;
    /** Nombre de triangles cuits (journal, banc d'essai). */
    public final int triangles;
    /** Etat de l'image courante : alpha calcule par le DecalRenderer et appartenance a un lot (alpha plein). */
    float alpha;
    boolean dansLot;

    Decal(VertexBuffer tampon, ByteBuffer sommets, AABB bornes, double x, double y, double z, double ox, double oy, double oz, long cle, int triangles) {
        this.tampon = tampon;
        this.sommets = sommets;
        this.bornes = bornes;
        this.x = x;
        this.y = y;
        this.z = z;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.cle = cle;
        this.triangles = triangles;
    }

    public boolean valide() {
        return tampon != null && !tampon.isInvalid();
    }

    @Override
    public void close() {
        if (tampon != null) {
            tampon.close();
            tampon = null;
        }
        if (sommets != null) {
            MemoryUtil.memFree(sommets);
            sommets = null;
        }
    }
}
