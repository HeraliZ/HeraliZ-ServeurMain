package fr.heraliz.ping.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Les 6 paquets du canal {@code heraliz_ping:main}, tous minuscules (octets du message, en-tete du canal non compte) :
 * <ul>
 *     <li>{@link PingC2S} : le joueur marque un bloc ou une entite (13 a 17 octets) ;</li>
 *     <li>{@link AnnulerC2S} : il retire un de ses pings (1 a 5 octets) ;</li>
 *     <li>{@link PingS2C} : un ping a afficher (environ 20 octets + le pseudo) ;</li>
 *     <li>{@link MajS2C} : nouvelle place d'une entite pingee hors de portee de suivi, ou fin d'un ping (2 a 16 octets) ;</li>
 *     <li>{@link ReglagesS2C} : regles du serveur utiles au client (3 octets) ;</li>
 *     <li>{@link CarteC2S} : le joueur marque un point depuis la carte du monde (9 octets).</li>
 * </ul>
 */
public final class Paquets {
    private Paquets() {
    }

    /** Bit « cible = entite » dans l'octet d'en-tete (les 3 bits bas = type, {@code PingType.AUTO} = 7). */
    static final int BIT_ENTITE = 0x10, BIT_MOI = 0x20;

    /**
     * Ping propose par le client. {@code type} : 0 a 5, ou {@code PingType.AUTO} ; {@code entite} : id reseau de
     * l'entite visee, -1 pour un bloc ; {@code bloc} + (x, y, z) : point vise (voir {@link Pos}).
     */
    public record PingC2S(int type, int entite, long bloc, double x, double y, double z) {
        public void ecrire(FriendlyByteBuf buf) {
            buf.writeByte((type & 7) | (entite >= 0 ? BIT_ENTITE : 0));
            if (entite >= 0) buf.writeVarInt(entite);
            Pos.ecrire(buf, bloc, x, y, z);
        }

        public static PingC2S lire(FriendlyByteBuf buf) {
            int t = buf.readUnsignedByte();
            int e = (t & BIT_ENTITE) != 0 ? buf.readVarInt() : -1;
            long bloc = buf.readLong();
            double x = Pos.coord(BlockPos.getX(bloc), buf.readUnsignedByte());
            double y = Pos.coord(BlockPos.getY(bloc), buf.readUnsignedByte());
            double z = Pos.coord(BlockPos.getZ(bloc), buf.readUnsignedByte());
            return new PingC2S(t & 7, e, bloc, x, y, z);
        }
    }

    /** Le joueur retire son ping {@code id}. */
    public record AnnulerC2S(int id) {
        public void ecrire(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
        }

        public static AnnulerC2S lire(FriendlyByteBuf buf) {
            return new AnnulerC2S(buf.readVarInt());
        }
    }

    /**
     * Ping a afficher. {@code moi} : le destinataire en est l'auteur ; {@code couleur} : accent de l'auteur (clan ou
     * equipe) ; {@code dureeS} : duree de vie en secondes ; {@code nom} : pseudo de l'auteur (16 caracteres au plus).
     */
    public record PingS2C(int id, int type, boolean moi, int entite, long bloc, double x, double y, double z, int couleur, int dureeS,
                          String nom) {
        public void ecrire(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeByte((type & 7) | (entite >= 0 ? BIT_ENTITE : 0) | (moi ? BIT_MOI : 0));
            if (entite >= 0) buf.writeVarInt(entite);
            Pos.ecrire(buf, bloc, x, y, z);
            buf.writeMedium(couleur & 0xFFFFFF);
            buf.writeByte(Math.max(1, Math.min(255, dureeS)));
            buf.writeUtf(nom, 16);
        }

        public static PingS2C lire(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            int t = buf.readUnsignedByte();
            int e = (t & BIT_ENTITE) != 0 ? buf.readVarInt() : -1;
            long bloc = buf.readLong();
            double x = Pos.coord(BlockPos.getX(bloc), buf.readUnsignedByte());
            double y = Pos.coord(BlockPos.getY(bloc), buf.readUnsignedByte());
            double z = Pos.coord(BlockPos.getZ(bloc), buf.readUnsignedByte());
            int couleur = buf.readUnsignedMedium();
            int duree = buf.readUnsignedByte();
            String nom = buf.readUtf(16);
            return new PingS2C(id, t & 7, (t & BIT_MOI) != 0, e, bloc, x, y, z, couleur, duree, nom);
        }

        /** Le meme ping vu par son auteur. */
        public PingS2C pourAuteur() {
            return new PingS2C(id, type, true, entite, bloc, x, y, z, couleur, dureeS, nom);
        }
    }

    /** Suivi d'entite (nouvelle position) ou fin du ping {@code id} ({@code fin} vrai : plus de position). */
    public record MajS2C(int id, boolean fin, long bloc, double x, double y, double z) {
        public static MajS2C fin(int id) {
            return new MajS2C(id, true, 0L, 0, 0, 0);
        }

        public void ecrire(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeBoolean(fin);
            if (!fin) Pos.ecrire(buf, bloc, x, y, z);
        }

        public static MajS2C lire(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            if (buf.readBoolean()) return fin(id);
            long bloc = buf.readLong();
            double x = Pos.coord(BlockPos.getX(bloc), buf.readUnsignedByte());
            double y = Pos.coord(BlockPos.getY(bloc), buf.readUnsignedByte());
            double z = Pos.coord(BlockPos.getZ(bloc), buf.readUnsignedByte());
            return new MajS2C(id, false, bloc, x, y, z);
        }
    }

    /** Regles du serveur pour le client : portee de visee (blocs) et interrupteur general. */
    public record ReglagesS2C(int portee, boolean actif) {
        public void ecrire(FriendlyByteBuf buf) {
            buf.writeShort(portee);
            buf.writeBoolean(actif);
        }

        public static ReglagesS2C lire(FriendlyByteBuf buf) {
            return new ReglagesS2C(buf.readUnsignedShort(), buf.readBoolean());
        }
    }

    /**
     * Ping pose depuis la carte du monde (heraliz_carte) : type (0 a 5, ou {@code PingType.AUTO} = « On y va ») et point
     * (x, z) dans le monde du joueur ; le serveur choisit la hauteur. Sans portee ni ligne de vue (config {@code carte}).
     */
    public record CarteC2S(int type, int x, int z) {
        public void ecrire(FriendlyByteBuf buf) {
            buf.writeByte(type & 7);
            buf.writeInt(x);
            buf.writeInt(z);
        }

        public static CarteC2S lire(FriendlyByteBuf buf) {
            return new CarteC2S(buf.readUnsignedByte() & 7, buf.readInt(), buf.readInt());
        }
    }
}
