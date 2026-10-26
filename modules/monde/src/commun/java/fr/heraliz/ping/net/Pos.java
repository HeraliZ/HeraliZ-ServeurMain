package fr.heraliz.ping.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Position compacte d'un ping (11 octets au lieu de 24) : le bloc vise ({@link BlockPos#asLong}, 8 octets) + la place
 * du point dans ce bloc sur 3 octets (1/255 de bloc, soit 4 mm). Le point peut etre sur une face (fraction 0 ou 1) :
 * le bloc transmis reste celui qu'on vise, utile au serveur (un coffre = Loot). Un point hors de son bloc (sommet d'une
 * entite, donnee fausse) est rattache au bloc qui le contient : la position n'est jamais faussee.
 */
public final class Pos {
    private Pos() {
    }

    public static void ecrire(FriendlyByteBuf buf, long bloc, double x, double y, double z) {
        double fx = x - BlockPos.getX(bloc), fy = y - BlockPos.getY(bloc), fz = z - BlockPos.getZ(bloc);
        if (dehors(fx) || dehors(fy) || dehors(fz)) {
            bloc = BlockPos.containing(x, y, z).asLong();
            fx = x - BlockPos.getX(bloc);
            fy = y - BlockPos.getY(bloc);
            fz = z - BlockPos.getZ(bloc);
        }
        buf.writeLong(bloc);
        buf.writeByte(frac(fx));
        buf.writeByte(frac(fy));
        buf.writeByte(frac(fz));
    }

    private static boolean dehors(double f) {
        return !(f >= -1.0E-3 && f <= 1.001);
    }

    private static int frac(double f) {
        return (int) Math.round(Math.max(0, Math.min(1, f)) * 255.0);
    }

    /** Coordonnee du point a partir du bloc et de l'octet lu. */
    public static double coord(int base, int octet) {
        return base + (octet & 255) / 255.0;
    }
}
