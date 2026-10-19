package fr.heraliz.crac.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Fiche technique des bases ({@code /base info}, CRac 1.13.0, maquette validée le 19/10) : tous les chiffres sont lus
 * par le serveur dans ses réglages (coûts, solidité, réparation, entretien, explosifs) et les prix du Bâtisseur ; le
 * joueur n'a qu'à les afficher. Les objets sont donnés par leur identifiant (nom et icône lus chez le joueur).
 */
public record S2CFicheBase(List<Pose> poses, List<Palier> paliers, Map<String, Integer> prix, int reparePourcent,
                           float repareSecondes, float entretienHeures, int degatsC4, int degatsCharge) {
    /** Un objet et sa quantité. */
    public record Cout(String objet, int nombre) {
    }

    /** Poser une pièce (en brindilles). */
    public record Pose(String piece, List<Cout> couts) {
    }

    /**
     * Un palier (twig, wood, stone, metal, armored) : amélioration d'une pièce entière (vide pour les brindilles), le même
     * prix pour toutes les pièces ou non, solidité d'un mur, réparation, entretien par pièce, explosifs pour un mur.
     */
    public record Palier(String id, List<Cout> ameliorer, boolean memePartout, int pvMur, List<Cout> reparer,
                         Cout entretien, int c4, int charges) {
    }

    private static void couts(FriendlyByteBuf b, List<Cout> l) {
        b.writeVarInt(l.size());
        for (Cout c : l) {
            b.writeUtf(c.objet, 128);
            b.writeVarInt(c.nombre);
        }
    }

    private static List<Cout> couts(FriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), 16);
        List<Cout> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            l.add(new Cout(b.readUtf(128), b.readVarInt()));
        }
        return l;
    }

    public static void encode(S2CFicheBase m, FriendlyByteBuf b) {
        b.writeVarInt(m.poses.size());
        for (Pose p : m.poses) {
            b.writeUtf(p.piece, 64);
            couts(b, p.couts);
        }
        b.writeVarInt(m.paliers.size());
        for (Palier p : m.paliers) {
            b.writeUtf(p.id, 32);
            couts(b, p.ameliorer);
            b.writeBoolean(p.memePartout);
            b.writeVarInt(p.pvMur);
            couts(b, p.reparer);
            couts(b, List.of(p.entretien));
            b.writeVarInt(p.c4);
            b.writeVarInt(p.charges);
        }
        b.writeVarInt(m.prix.size());
        m.prix.forEach((k, v) -> {
            b.writeUtf(k, 128);
            b.writeVarInt(v);
        });
        b.writeVarInt(m.reparePourcent);
        b.writeFloat(m.repareSecondes);
        b.writeFloat(m.entretienHeures);
        b.writeVarInt(m.degatsC4);
        b.writeVarInt(m.degatsCharge);
    }

    public static S2CFicheBase decode(FriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), 32);
        List<Pose> poses = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            poses.add(new Pose(b.readUtf(64), couts(b)));
        }
        n = Math.min(b.readVarInt(), 8);
        List<Palier> paliers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String id = b.readUtf(32);
            List<Cout> am = couts(b);
            boolean meme = b.readBoolean();
            int pv = b.readVarInt();
            List<Cout> rep = couts(b);
            List<Cout> ent = couts(b);
            paliers.add(new Palier(id, am, meme, pv, rep, ent.isEmpty() ? new Cout("", 0) : ent.get(0), b.readVarInt(), b.readVarInt()));
        }
        n = Math.min(b.readVarInt(), 256);
        Map<String, Integer> prix = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            prix.put(b.readUtf(128), b.readVarInt());
        }
        return new S2CFicheBase(poses, paliers, prix, b.readVarInt(), b.readFloat(), b.readFloat(), b.readVarInt(), b.readVarInt());
    }

    public static void handle(S2CFicheBase msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                NetworkHandler.RECEPTION_CLIENT.accept(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
