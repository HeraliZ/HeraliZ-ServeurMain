package fr.heralisz.matchs.clashwar.net;

import fr.heralisz.clans.model.Banniere;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Paquets du ClashWar. S2C : traitement delegue a {@code clashwar.client.CwClient} (charge seulement sur le client
 * physique, via DistExecutor). C2S : tout est revalide par le serveur ({@link Tournois}) : droits, phase, cadence.
 */
public final class CwPaquets {
    /** Réception côté client des paquets serveur -> client (client/ReceptionCwPaquets) : branchée par la partie client. */
    public static final fr.heralisz.matchs.pont.Crochet<Object> RECEPTION_CLIENT = new fr.heralisz.matchs.pont.Crochet<>();

    private CwPaquets() {
    }

    /** Appele par {@code ClashWar.paquets} : ordre fixe. */
    public static void enregistrer(MatchsNet.Registre r) {
        r.s2c(TableauS2C.class, TableauS2C::write, TableauS2C::read, TableauS2C::handle);
        r.s2c(RencontreS2C.class, RencontreS2C::write, RencontreS2C::read, RencontreS2C::handle);
        r.s2c(InscriptionS2C.class, InscriptionS2C::write, InscriptionS2C::read, InscriptionS2C::handle);
        r.s2c(ToastS2C.class, ToastS2C::write, ToastS2C::read, ToastS2C::handle);
        r.s2c(ZoneS2C.class, ZoneS2C::write, ZoneS2C::read, ZoneS2C::handle);
        r.s2c(OuvrirS2C.class, OuvrirS2C::write, OuvrirS2C::read, OuvrirS2C::handle);
        r.c2s(InscriptionC2S.class, InscriptionC2S::write, InscriptionC2S::read, InscriptionC2S::handle);
        r.c2s(RetraitC2S.class, RetraitC2S::write, RetraitC2S::read, RetraitC2S::handle);
    }

    // ================================================================== serveur -> client
    /** Etat complet du tournoi (tableau, accueil). */
    public record TableauS2C(VueTableau vue) {
        public void write(FriendlyByteBuf buf) {
            vue.write(buf);
        }

        public static TableauS2C read(FriendlyByteBuf buf) {
            return new TableauS2C(VueTableau.read(buf));
        }

        public static void handle(TableauS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Compteurs en direct d'une rencontre (statut, gagnant, vivants / engages). */
    public record RencontreS2C(int tour, int index, int statut, int gagnant, int vivantsA, int totalA, int vivantsB, int totalB,
                               boolean forfait) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(tour);
            buf.writeVarInt(index);
            buf.writeByte(statut);
            buf.writeVarInt(gagnant + 1);
            buf.writeByte(vivantsA);
            buf.writeByte(totalA);
            buf.writeByte(vivantsB);
            buf.writeByte(totalB);
            buf.writeBoolean(forfait);
        }

        public static RencontreS2C read(FriendlyByteBuf buf) {
            return new RencontreS2C(buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readVarInt() - 1, buf.readByte(),
                    buf.readByte(), buf.readByte(), buf.readByte(), buf.readBoolean());
        }

        public static void handle(RencontreS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Membre connecte d'un clan dans l'ecran d'inscription (cle de traduction du grade du mod clans). */
    public record Membre(UUID uuid, String nom, String grade) {
        void write(FriendlyByteBuf buf) {
            buf.writeUUID(uuid);
            buf.writeUtf(nom, 24);
            buf.writeUtf(grade, 64);
        }

        static Membre read(FriendlyByteBuf buf) {
            return new Membre(buf.readUUID(), buf.readUtf(24), buf.readUtf(64));
        }
    }

    /**
     * Ecran d'inscription du clan (ouverture ou mise a jour).
     *
     * @param ouvrir        ouvrir l'ecran (sinon : mise a jour s'il est ouvert)
     * @param finDansMs     temps restant avant la cloture au moment de l'envoi
     * @param peutModifier  permission CLASHWAR_INSCRIRE
     * @param membres       membres connectes du clan
     * @param equipes       repartition enregistree (vide = clan non inscrit)
     * @param message       cle de traduction d'un message a afficher (refus, confirmation), vide = aucun
     * @param erreur        le message est une erreur
     */
    public record InscriptionS2C(boolean ouvrir, int edition, int taille, String format, long finDansMs, boolean peutModifier,
                                 String tag, String nomClan, int couleur, Banniere banniere, List<Membre> membres,
                                 List<List<UUID>> equipes, int clansInscrits, int equipesTotal, int maxEquipes, String message,
                                 boolean erreur) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(ouvrir);
            buf.writeVarInt(edition);
            buf.writeVarInt(taille);
            buf.writeUtf(format, 8);
            buf.writeVarLong(Math.max(0, finDansMs));
            buf.writeBoolean(peutModifier);
            buf.writeUtf(tag, 16);
            buf.writeUtf(nomClan, 48);
            buf.writeInt(couleur);
            banniere.write(buf);
            int n = Math.min(64, membres.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) membres.get(i).write(buf);
            ecrireEquipes(buf, equipes);
            buf.writeVarInt(clansInscrits);
            buf.writeVarInt(equipesTotal);
            buf.writeVarInt(maxEquipes);
            buf.writeUtf(message, 96);
            buf.writeBoolean(erreur);
        }

        public static InscriptionS2C read(FriendlyByteBuf buf) {
            boolean ouvrir = buf.readBoolean();
            int edition = buf.readVarInt(), taille = buf.readVarInt();
            String format = buf.readUtf(8);
            long fin = buf.readVarLong();
            boolean peut = buf.readBoolean();
            String tag = buf.readUtf(16), nom = buf.readUtf(48);
            int couleur = buf.readInt();
            Banniere b = Banniere.read(buf);
            int n = Math.min(64, buf.readVarInt());
            List<Membre> m = new ArrayList<>(n);
            for (int i = 0; i < n; i++) m.add(Membre.read(buf));
            List<List<UUID>> eq = lireEquipes(buf);
            return new InscriptionS2C(ouvrir, edition, taille, format, fin, peut, tag, nom, couleur, b, m, eq, buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readUtf(96), buf.readBoolean());
        }

        public static void handle(InscriptionS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Petit toast en haut a droite (annonces, inscription, appel de match). */
    public record ToastS2C(Component titre, Component texte, int couleur, String son) {
        public void write(FriendlyByteBuf buf) {
            buf.writeComponent(titre);
            buf.writeComponent(texte);
            buf.writeInt(couleur);
            buf.writeUtf(son, 96);
        }

        public static ToastS2C read(FriendlyByteBuf buf) {
            return new ToastS2C(buf.readComponent(), buf.readComponent(), buf.readInt(), buf.readUtf(96));
        }

        public static void handle(ToastS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * Zone de mort subite : cercle de centre (cx, cz) dont le rayon passe de r0 a r1 en {@code dureeTicks} ; inactive =
     * masquer.
     */
    public record ZoneS2C(boolean actif, double cx, double cz, double y, float r0, float r1, int dureeTicks) {
        public static final ZoneS2C AUCUNE = new ZoneS2C(false, 0, 0, 0, 0, 0, 0);

        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(actif);
            buf.writeDouble(cx);
            buf.writeDouble(cz);
            buf.writeDouble(y);
            buf.writeFloat(r0);
            buf.writeFloat(r1);
            buf.writeVarInt(dureeTicks);
        }

        public static ZoneS2C read(FriendlyByteBuf buf) {
            return new ZoneS2C(buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
                    buf.readVarInt());
        }

        public static void handle(ZoneS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Ouvre un ecran client ("tableau"). */
    public record OuvrirS2C(String ecran) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(ecran, 32);
        }

        public static OuvrirS2C read(FriendlyByteBuf buf) {
            return new OuvrirS2C(buf.readUtf(32));
        }

        public static void handle(OuvrirS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    // ================================================================== client -> serveur
    /** Nouvelle repartition du clan (liste vide = desinscrire le clan). */
    public record InscriptionC2S(List<List<UUID>> equipes) {
        public void write(FriendlyByteBuf buf) {
            ecrireEquipes(buf, equipes);
        }

        public static InscriptionC2S read(FriendlyByteBuf buf) {
            return new InscriptionC2S(lireEquipes(buf));
        }

        public static void handle(InscriptionC2S p, Supplier<NetworkEvent.Context> ctx) {
            // traitement : ReceptionCwPaquetsServeur (src/serveur), branché par la partie serveur
            MatchsNet.recevoirServeur(p, ctx);
        }
    }

    /** Le joueur se retire de l'inscription de son clan. */
    public record RetraitC2S() {
        public void write(FriendlyByteBuf buf) {
        }

        public static RetraitC2S read(FriendlyByteBuf buf) {
            return new RetraitC2S();
        }

        public static void handle(RetraitC2S p, Supplier<NetworkEvent.Context> ctx) {
            // traitement : ReceptionCwPaquetsServeur (src/serveur), branché par la partie serveur
            MatchsNet.recevoirServeur(p, ctx);
        }
    }

    // ================================================================== aides
    static void ecrireEquipes(FriendlyByteBuf buf, List<List<UUID>> equipes) {
        int n = Math.min(32, equipes.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            List<UUID> e = equipes.get(i);
            int m = Math.min(VueTableau.MAX_JOUEURS, e.size());
            buf.writeVarInt(m);
            for (int j = 0; j < m; j++) buf.writeUUID(e.get(j));
        }
    }

    static List<List<UUID>> lireEquipes(FriendlyByteBuf buf) {
        int n = Math.min(32, buf.readVarInt());
        List<List<UUID>> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int m = Math.min(VueTableau.MAX_JOUEURS, buf.readVarInt());
            List<UUID> e = new ArrayList<>(m);
            for (int j = 0; j < m; j++) e.add(buf.readUUID());
            l.add(e);
        }
        return l;
    }
}
