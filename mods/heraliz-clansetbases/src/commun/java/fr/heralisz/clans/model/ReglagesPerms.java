package fr.heralisz.clans.model;

import net.minecraft.network.FriendlyByteBuf;

import java.util.EnumSet;
import java.util.Map;

/**
 * Reglages individuels d'un membre sous forme de deux masques (bit = ordinal de {@link Perm}) : {@code autorise} et
 * {@code refuse}, disjoints ; une permission dans aucun des deux est {@link Perm.Etat#HERITE} (comme son grade).
 * Immuable : sert au snapshot (reseau) et a l'affichage optimiste de l'editeur.
 */
public record ReglagesPerms(int autorise, int refuse) {
    public static final ReglagesPerms AUCUN = new ReglagesPerms(0, 0);

    public ReglagesPerms {
        autorise &= Perm.TOUTES;
        refuse &= Perm.TOUTES & ~autorise;
    }

    public Perm.Etat etat(Perm p) {
        if ((autorise & p.bit()) != 0) return Perm.Etat.AUTORISE;
        if ((refuse & p.bit()) != 0) return Perm.Etat.REFUSE;
        return Perm.Etat.HERITE;
    }

    /** Copie avec cette permission dans cet etat. */
    public ReglagesPerms avec(Perm p, Perm.Etat e) {
        int a = autorise & ~p.bit(), r = refuse & ~p.bit();
        if (e == Perm.Etat.AUTORISE) a |= p.bit();
        else if (e == Perm.Etat.REFUSE) r |= p.bit();
        return a == autorise && r == refuse ? this : new ReglagesPerms(a, r);
    }

    public boolean vide() {
        return autorise == 0 && refuse == 0;
    }

    /** Nombre de permissions reglees individuellement. */
    public int nombre() {
        return Integer.bitCount(autorise) + Integer.bitCount(refuse);
    }

    /** Permission effective : le reglage s'il y en a un, sinon la valeur du grade. */
    public boolean effectif(Perm p, boolean parLeGrade) {
        Perm.Etat e = etat(p);
        return e == Perm.Etat.HERITE ? parLeGrade : e == Perm.Etat.AUTORISE;
    }

    /** Permissions effectives a partir de celles du grade. */
    public EnumSet<Perm> appliquer(EnumSet<Perm> duGrade) {
        EnumSet<Perm> s = EnumSet.copyOf(duGrade);
        s.addAll(Perm.depuisMasque(autorise));
        s.removeAll(Perm.depuisMasque(refuse));
        return s;
    }

    public static ReglagesPerms de(Map<Perm, Perm.Etat> m) {
        int a = 0, r = 0;
        for (Map.Entry<Perm, Perm.Etat> e : m.entrySet()) {
            if (e.getValue() == Perm.Etat.AUTORISE) a |= e.getKey().bit();
            else if (e.getValue() == Perm.Etat.REFUSE) r |= e.getKey().bit();
        }
        return a == 0 && r == 0 ? AUCUN : new ReglagesPerms(a, r);
    }

    public void write(FriendlyByteBuf b) {
        b.writeVarInt(autorise);
        b.writeVarInt(refuse);
    }

    public static ReglagesPerms read(FriendlyByteBuf b) {
        return new ReglagesPerms(b.readVarInt(), b.readVarInt());
    }
}
