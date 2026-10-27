package fr.heraliz.casino.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Compte de capsules du joueur (serveur -> ce joueur seulement, 27/10) : solde, compte utilisable ou non, la caisse
 * prend-elle sur le compte, derniers mouvements, et le resultat d'une operation faite au distributeur (ecrit dans son
 * ecran, pas dans le chat). Envoye a la connexion, a chaque changement et a l'ouverture du distributeur. Il ne sert qu'a
 * l'affichage : le serveur reverifie tout a chaque operation.
 */
public record ComptePacket(long solde, boolean actif, boolean paiement, List<Mouvement> mouvements, @Nullable Component message, boolean ok) {
    /** Mouvements envoyes, au plus (les plus recents). */
    public static final int MAX_MOUVEMENTS = 12;

    /**
     * Un mouvement : genre (depot, retrait, caisse, rendu, entree, staff, wipe, autre), detail (objet de la caisse :
     * identifiant et NBT, ou nom d'un encaissement), capsules (+ ou -), age en secondes a l'envoi.
     */
    public record Mouvement(String genre, String detail, long n, long age) {
    }

    // ---- cote client : dernier etat recu
    public static volatile long clientSolde = 0;
    public static volatile boolean clientActif = true, clientPaiement = true;
    public static volatile List<Mouvement> clientMouvements = List.of();
    /** Heure de reception (ms) : l'age d'un mouvement est age + (maintenant - clientRecu) / 1000. */
    public static volatile long clientRecu = 0;
    @Nullable
    public static volatile Component clientMessage;
    public static volatile boolean clientMessageOk;
    /** Heure de reception du dernier message (ms). */
    public static volatile long clientMessageA = 0;

    public static void encode(ComptePacket p, FriendlyByteBuf buf) {
        buf.writeVarLong(Math.max(0, p.solde));
        buf.writeBoolean(p.actif);
        buf.writeBoolean(p.paiement);
        int n = Math.min(MAX_MOUVEMENTS, p.mouvements.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Mouvement m = p.mouvements.get(i);
            buf.writeUtf(m.genre, 16);
            buf.writeUtf(m.detail.length() > 200 ? m.detail.substring(0, 200) : m.detail, 200);
            buf.writeLong(m.n);
            buf.writeVarLong(Math.max(0, m.age));
        }
        buf.writeBoolean(p.message != null);
        if (p.message != null) {
            buf.writeComponent(p.message);
            buf.writeBoolean(p.ok);
        }
    }

    public static ComptePacket decode(FriendlyByteBuf buf) {
        long solde = buf.readVarLong();
        boolean actif = buf.readBoolean(), paiement = buf.readBoolean();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_MOUVEMENTS) throw new IllegalArgumentException("mouvements : " + n);
        List<Mouvement> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(new Mouvement(buf.readUtf(16), buf.readUtf(200), buf.readLong(), buf.readVarLong()));
        Component msg = null;
        boolean ok = false;
        if (buf.readBoolean()) {
            msg = buf.readComponent();
            ok = buf.readBoolean();
        }
        return new ComptePacket(solde, actif, paiement, l, msg, ok);
    }

    public static void handle(ComptePacket p, Supplier<NetworkEvent.Context> ctx) {
        clientSolde = Math.max(0, p.solde);
        clientActif = p.actif;
        clientPaiement = p.paiement;
        clientMouvements = List.copyOf(p.mouvements);
        clientRecu = System.currentTimeMillis();
        if (p.message != null) {
            clientMessage = p.message;
            clientMessageOk = p.ok;
            clientMessageA = clientRecu;
        }
    }
}
