package fr.heralisz.matchs.duels.killcam;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Extrait d'enregistrement envoye au client pour une killcam (commun aux deux cotes) : quelques secondes de pistes
 * (une par participant, une image par tick), les tirs, et une palette d'objets tenus. Construit par
 * {@link Enregistreur#extraire}, rejoue par {@code client.Lecteur}.
 * <p><b>Format reseau compact</b> (≈ 16 octets par image et par participant) : positions relatives a une origine
 * (le tueur a la derniere image) en 1/32 de bloc sur des shorts (±1024 blocs), angles sur des shorts (65536 = 360°),
 * objet tenu = index dans la palette (ItemStack complets, ecrits une seule fois). Toutes les tailles sont bornees a la
 * lecture.
 */
public final class Clip {
    /** Bornes du format (verifiees a l'ecriture et a la lecture). */
    public static final int MAX_IMAGES = 100, MAX_PARTICIPANTS = 8, MAX_OBJETS = 24, MAX_TIRS = 160, MAX_NOM = 32, MAX_ARME = 48;
    private static final int VERSION = 1;
    private static final float ECHELLE_POS = 32f, ECHELLE_ANGLE = 65536f / 360f;

    // ---- drapeaux d'image
    /** Le participant existe a cette image (sinon il est masque). */
    public static final int PRESENT = 1;
    public static final int ACCROUPI = 2;
    public static final int SPRINT = 4;
    /** Pose de nage / ramper. */
    public static final int NAGE = 8;
    /** Vient d'etre touche (eclair rouge du modele). */
    public static final int BLESSE = 16;
    /** Bras en mouvement (coup, utilisation). */
    public static final int FRAPPE = 32;
    /** Mort ou elimine a cette image. */
    public static final int MORT = 64;

    // ---- raisons
    /** Killcam de sa propre mort, envoyee a la victime. */
    public static final byte RAISON_MORT = 0;
    /** Derniere elimination de la manche, montree a tous (recherche et destruction). */
    public static final byte RAISON_DERNIERE = 1;
    /** Enregistrement factice du banc d'essai ou de {@code /killcam essai}. */
    public static final byte RAISON_ESSAI = 2;

    public byte raison = RAISON_MORT;
    /** Nombre d'images (1 par tick). */
    public int images;
    /** Index des participants tueur et victime. */
    public int tueur, victime;
    /** Index de palette de l'arme du tueur (-1 = mains nues). */
    public int arme = -1;
    /** Nom d'arme force (vide = nom de l'objet, resolu par le client : les noms TaCZ sont cote client). */
    public String armeNom = "";
    /** Distance tueur - victime a la derniere image, en blocs. */
    public float distance;
    /** Origine des positions (ecrite en double). */
    public double ox, oy, oz;
    public final List<ItemStack> objets = new ArrayList<>();
    public final List<Participant> participants = new ArrayList<>();
    public final List<Tir> tirs = new ArrayList<>();

    /** Piste d'un participant : tableaux de taille {@link #images}. */
    public static final class Participant {
        public UUID uuid;
        public String nom;
        /** Vrai joueur (skin par UUID) ou entite suivie (bot). */
        public boolean joueur;
        /** Couleur d'equipe RGB. */
        public int couleur;
        /** Armure (tete, torse, jambes, pieds) et main gauche : index de palette, -1 = rien. */
        public final int[] tenue = {-1, -1, -1, -1, -1};
        public double[] x, y, z;
        /** Lacet du corps, lacet de la tete, tangage (degres). */
        public float[] corps, tete, tangage;
        public byte[] drapeaux;
        /** Index de palette de l'objet tenu (-1 = rien). */
        public byte[] objet;

        public Participant(UUID uuid, String nom, boolean joueur, int couleur, int images) {
            this.uuid = uuid;
            this.nom = nom;
            this.joueur = joueur;
            this.couleur = couleur;
            x = new double[images];
            y = new double[images];
            z = new double[images];
            corps = new float[images];
            tete = new float[images];
            tangage = new float[images];
            drapeaux = new byte[images];
            objet = new byte[images];
        }

        public boolean present(int i) {
            return (drapeaux[i] & PRESENT) != 0;
        }

        public boolean a(int i, int drapeau) {
            return (drapeaux[i] & drapeau) != 0;
        }
    }

    /**
     * Un tir : participant, instant (en images, fraction comprise), direction (lacet, tangage en degres) au moment du
     * tir.
     */
    public record Tir(int participant, float temps, float lacet, float tangage) {
    }

    /** Copie superficielle (pistes et objets partages) avec une autre raison. */
    public Clip avecRaison(byte r) {
        Clip c = new Clip();
        c.raison = r;
        c.images = images;
        c.tueur = tueur;
        c.victime = victime;
        c.arme = arme;
        c.armeNom = armeNom;
        c.distance = distance;
        c.ox = ox;
        c.oy = oy;
        c.oz = oz;
        c.objets.addAll(objets);
        c.participants.addAll(participants);
        c.tirs.addAll(tirs);
        return c;
    }

    // ------------------------------------------------------------------ reseau
    public void write(FriendlyByteBuf buf) {
        int n = Math.min(images, MAX_IMAGES);
        buf.writeByte(VERSION);
        buf.writeByte(raison);
        buf.writeVarInt(n);
        buf.writeDouble(ox);
        buf.writeDouble(oy);
        buf.writeDouble(oz);
        buf.writeVarInt(tueur);
        buf.writeVarInt(victime);
        buf.writeVarInt(arme + 1);
        buf.writeUtf(couper(armeNom, MAX_ARME), MAX_ARME);
        buf.writeFloat(distance);
        int no = Math.min(objets.size(), MAX_OBJETS);
        buf.writeVarInt(no);
        for (int i = 0; i < no; i++) buf.writeItem(objets.get(i));
        int np = Math.min(participants.size(), MAX_PARTICIPANTS);
        buf.writeVarInt(np);
        for (int k = 0; k < np; k++) {
            Participant p = participants.get(k);
            buf.writeUUID(p.uuid);
            buf.writeUtf(couper(p.nom, MAX_NOM), MAX_NOM);
            buf.writeBoolean(p.joueur);
            buf.writeInt(p.couleur);
            for (int t : p.tenue) buf.writeByte(t + 1);
            for (int i = 0; i < n; i++) {
                buf.writeByte(p.drapeaux[i]);
                if (!p.present(i)) continue;
                buf.writeShort(pos(p.x[i] - ox));
                buf.writeShort(pos(p.y[i] - oy));
                buf.writeShort(pos(p.z[i] - oz));
                buf.writeShort(angle(p.corps[i]));
                buf.writeShort(angle(p.tete[i]));
                buf.writeShort(angle(p.tangage[i]));
                buf.writeByte(p.objet[i] + 1);
            }
        }
        int nt = Math.min(tirs.size(), MAX_TIRS);
        buf.writeVarInt(nt);
        for (int i = 0; i < nt; i++) {
            Tir t = tirs.get(i);
            buf.writeByte(t.participant());
            buf.writeShort(Mth.clamp(Math.round(t.temps() * 64f), 0, 32767));
            buf.writeShort(angle(t.lacet()));
            buf.writeShort(angle(t.tangage()));
        }
    }

    public static Clip read(FriendlyByteBuf buf) {
        Clip c = new Clip();
        int version = buf.readByte();
        if (version != VERSION) throw new IllegalStateException("killcam : version de clip inconnue " + version);
        c.raison = buf.readByte();
        int n = Mth.clamp(buf.readVarInt(), 0, MAX_IMAGES);
        c.images = n;
        c.ox = buf.readDouble();
        c.oy = buf.readDouble();
        c.oz = buf.readDouble();
        c.tueur = buf.readVarInt();
        c.victime = buf.readVarInt();
        c.arme = buf.readVarInt() - 1;
        c.armeNom = buf.readUtf(MAX_ARME);
        c.distance = buf.readFloat();
        int no = Math.min(buf.readVarInt(), MAX_OBJETS);
        for (int i = 0; i < no; i++) c.objets.add(buf.readItem());
        int np = Math.min(buf.readVarInt(), MAX_PARTICIPANTS);
        for (int k = 0; k < np; k++) {
            Participant p = new Participant(buf.readUUID(), buf.readUtf(MAX_NOM), buf.readBoolean(), buf.readInt(), n);
            for (int t = 0; t < p.tenue.length; t++) p.tenue[t] = palette(buf.readUnsignedByte() - 1, no);
            for (int i = 0; i < n; i++) {
                p.drapeaux[i] = buf.readByte();
                if (!p.present(i)) {
                    p.objet[i] = -1;
                    continue;
                }
                p.x[i] = c.ox + buf.readShort() / ECHELLE_POS;
                p.y[i] = c.oy + buf.readShort() / ECHELLE_POS;
                p.z[i] = c.oz + buf.readShort() / ECHELLE_POS;
                p.corps[i] = buf.readShort() / ECHELLE_ANGLE;
                p.tete[i] = buf.readShort() / ECHELLE_ANGLE;
                p.tangage[i] = buf.readShort() / ECHELLE_ANGLE;
                p.objet[i] = (byte) palette(buf.readUnsignedByte() - 1, no);
            }
            c.participants.add(p);
        }
        int nt = Math.min(buf.readVarInt(), MAX_TIRS);
        for (int i = 0; i < nt; i++) {
            int qui = buf.readUnsignedByte();
            float temps = buf.readShort() / 64f;
            float lacet = buf.readShort() / ECHELLE_ANGLE, tangage = buf.readShort() / ECHELLE_ANGLE;
            if (qui < c.participants.size()) c.tirs.add(new Tir(qui, temps, lacet, tangage));
        }
        if (c.tueur < 0 || c.tueur >= c.participants.size()) c.tueur = 0;
        if (c.victime < 0 || c.victime >= c.participants.size()) c.victime = 0;
        c.arme = palette(c.arme, no);
        return c;
    }

    /** Taille approximative une fois ecrit (journal). */
    public int octets() {
        io.netty.buffer.ByteBuf b = io.netty.buffer.Unpooled.buffer();
        try {
            write(new FriendlyByteBuf(b));
            return b.readableBytes();
        } finally {
            b.release();
        }
    }

    private static int palette(int i, int taille) {
        return i >= 0 && i < taille ? i : -1;
    }

    private static short pos(double v) {
        return (short) Mth.clamp(Math.round(v * ECHELLE_POS), Short.MIN_VALUE, Short.MAX_VALUE);
    }

    /** Angle en degres ramene sur un short (tour complet = 65536). */
    private static short angle(float deg) {
        return (short) Math.round(Mth.wrapDegrees(deg) * ECHELLE_ANGLE);
    }

    private static String couper(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
