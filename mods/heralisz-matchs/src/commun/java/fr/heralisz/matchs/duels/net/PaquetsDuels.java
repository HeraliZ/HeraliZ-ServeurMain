package fr.heralisz.matchs.duels.net;

import fr.heralisz.matchs.duels.Duels;
import fr.heralisz.matchs.duels.armes.Catalogue;
import fr.heralisz.matchs.duels.classes.ClassePerso;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Paquets des duels (enregistres dans cet ordre par {@link #enregistrer}). Les S2C delegent a
 * {@code duels.client.DuelsClient} (par DistExecutor, jamais d'import client ici) ; les C2S revalident tout cote
 * serveur (cadence, etat, droits) avant d'appeler {@link Duels}, {@link Defis} ou la {@link DuelPartie} du joueur.
 */
public final class PaquetsDuels {
    /** Réception côté client des paquets serveur -> client (client/ReceptionPaquetsDuels) : branchée par la partie client. */
    public static final fr.heralisz.matchs.pont.Crochet<Object> RECEPTION_CLIENT = new fr.heralisz.matchs.pont.Crochet<>();

    private PaquetsDuels() {
    }

    public static void enregistrer(MatchsNet.Registre r) {
        r.s2c(CatalogueS2C.class, CatalogueS2C::write, CatalogueS2C::read, CatalogueS2C::handle);
        r.s2c(ClassesS2C.class, ClassesS2C::write, ClassesS2C::read, ClassesS2C::handle);
        r.s2c(EcranDuelS2C.class, EcranDuelS2C::write, EcranDuelS2C::read, EcranDuelS2C::handle);
        r.s2c(DefiS2C.class, DefiS2C::write, DefiS2C::read, DefiS2C::handle);
        r.s2c(ChoixS2C.class, ChoixS2C::write, ChoixS2C::read, ChoixS2C::handle);
        r.s2c(RdS2C.class, RdS2C::write, RdS2C::read, RdS2C::handle);
        r.s2c(ScoresS2C.class, ScoresS2C::write, ScoresS2C::read, ScoresS2C::handle);
        r.s2c(EffetS2C.class, EffetS2C::write, EffetS2C::read, EffetS2C::handle);
        r.c2s(OuvrirC2S.class, OuvrirC2S::write, OuvrirC2S::read, OuvrirC2S::handle);
        r.c2s(DefierC2S.class, DefierC2S::write, DefierC2S::read, DefierC2S::handle);
        r.c2s(ReponseDefiC2S.class, ReponseDefiC2S::write, ReponseDefiC2S::read, ReponseDefiC2S::handle);
        r.c2s(ClasseC2S.class, ClasseC2S::write, ClasseC2S::read, ClasseC2S::handle);
        r.c2s(ChoixClasseC2S.class, ChoixClasseC2S::write, ChoixClasseC2S::read, ChoixClasseC2S::handle);
    }


    // ================================================================== S2C
    /** Catalogue de l'armurerie (envoye avant le premier ecran qui en a besoin). */
    public record CatalogueS2C(Catalogue catalogue) {
        public void write(FriendlyByteBuf b) {
            catalogue.write(b);
        }

        public static CatalogueS2C read(FriendlyByteBuf b) {
            return new CatalogueS2C(Catalogue.read(b));
        }

        public static void handle(CatalogueS2C p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().setPacketHandled(true);
            RECEPTION_CLIENT.accept(p);
        }
    }

    /** Les 5 classes du joueur ; ouvrir = ouvrir l'ecran CLASSES. */
    public record ClassesS2C(List<ClassePerso> classes, int derniere, boolean ouvrir) {
        public void write(FriendlyByteBuf b) {
            ClassePerso.ecrireListe(b, classes);
            b.writeVarInt(derniere);
            b.writeBoolean(ouvrir);
        }

        public static ClassesS2C read(FriendlyByteBuf b) {
            return new ClassesS2C(ClassePerso.lireListe(b), b.readVarInt(), b.readBoolean());
        }

        public static void handle(ClassesS2C p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().setPacketHandled(true);
            RECEPTION_CLIENT.accept(p);
        }
    }

    /** Contenu de l'ecran DUEL (ouvre l'ecran, ou le met a jour s'il est ouvert). */
    public record EcranDuelS2C(Vues.EcranDuel ecran) {
        public void write(FriendlyByteBuf b) {
            ecran.write(b);
        }

        public static EcranDuelS2C read(FriendlyByteBuf b) {
            return new EcranDuelS2C(Vues.EcranDuel.read(b));
        }

        public static void handle(EcranDuelS2C p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().setPacketHandled(true);
            RECEPTION_CLIENT.accept(p);
        }
    }

    /**
     * Defi recu (notification discrete en haut a droite pendant 30 s, sans ecran) ou fin d'un defi (defi null, raison =
     * cle de traduction affichee en toast, vide = rien ; id negatif = debut d'un duel, tous les defis affiches tombent).
     */
    public record DefiS2C(@Nullable Vues.DefiVue defi, int idFini, String raison) {
        public void write(FriendlyByteBuf b) {
            b.writeBoolean(defi != null);
            if (defi != null) defi.write(b);
            b.writeVarInt(idFini);
            b.writeUtf(raison, 96);
        }

        public static DefiS2C read(FriendlyByteBuf b) {
            Vues.DefiVue d = b.readBoolean() ? Vues.DefiVue.read(b) : null;
            return new DefiS2C(d, b.readVarInt(), b.readUtf(96));
        }

        public static void handle(DefiS2C p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().setPacketHandled(true);
            RECEPTION_CLIENT.accept(p);
        }
    }

    /**
     * Choix de classe (5 s) : ouvre l'ecran de choix ; secondes = 0 le ferme.
     *
     * @param contexte      ligne de contexte (« MANCHE 2 · ATTAQUE »)
     * @param couleur       couleur de l'equipe du joueur
     * @param fermerAuChoix l'ecran se ferme des le choix (reapparition) ; sinon il reste ouvert jusqu'au bout (gel)
     * @param hote          pseudo de l'hote du duel quand les classes proposees sont les siennes (les memes pour tous) ;
     *                      vide = les propres classes du joueur (entrainement), qui remplacent alors sa liste cote client
     */
    public record ChoixS2C(int secondes, List<ClassePerso> classes, int choisie, Component contexte, int couleur, boolean fermerAuChoix,
                           String hote) {
        public ChoixS2C {
            hote = hote == null ? "" : hote;
        }

        public void write(FriendlyByteBuf b) {
            b.writeVarInt(secondes);
            ClassePerso.ecrireListe(b, classes);
            b.writeVarInt(choisie);
            b.writeComponent(contexte);
            b.writeInt(couleur);
            b.writeBoolean(fermerAuChoix);
            b.writeUtf(hote, 64);
        }

        public static ChoixS2C read(FriendlyByteBuf b) {
            return new ChoixS2C(b.readVarInt(), ClassePerso.lireListe(b), b.readVarInt(), b.readComponent(), b.readInt(), b.readBoolean(),
                    b.readUtf(64));
        }

        public static void handle(ChoixS2C p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().setPacketHandled(true);
            RECEPTION_CLIENT.accept(p);
        }
    }

    /** Etat de la recherche et destruction pour ce joueur ; null = effacer. */
    public record RdS2C(@Nullable Vues.RdVue vue) {
        public void write(FriendlyByteBuf b) {
            b.writeBoolean(vue != null);
            if (vue != null) vue.write(b);
        }

        public static RdS2C read(FriendlyByteBuf b) {
            return new RdS2C(b.readBoolean() ? Vues.RdVue.read(b) : null);
        }

        public static void handle(RdS2C p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().setPacketHandled(true);
            RECEPTION_CLIENT.accept(p);
        }
    }

    /** Tableau des scores final (ouvre l'ecran des scores). */
    public record ScoresS2C(Vues.Tableau tableau) {
        public void write(FriendlyByteBuf b) {
            tableau.write(b);
        }

        public static ScoresS2C read(FriendlyByteBuf b) {
            return new ScoresS2C(Vues.Tableau.read(b));
        }

        public static void handle(ScoresS2C p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().setPacketHandled(true);
            RECEPTION_CLIENT.accept(p);
        }
    }

    /** Effets ponctuels : {@link #EXPLOSION} (eclair blanc + son), {@link #FIN} (fin du duel : tout l'etat client efface). */
    public record EffetS2C(int effet, double x, double y, double z) {
        public static final int EXPLOSION = 1, FIN = 2, POSEE = 3, DESAMORCEE = 4;

        public void write(FriendlyByteBuf b) {
            b.writeVarInt(effet);
            b.writeDouble(x);
            b.writeDouble(y);
            b.writeDouble(z);
        }

        public static EffetS2C read(FriendlyByteBuf b) {
            return new EffetS2C(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble());
        }

        public static void handle(EffetS2C p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().setPacketHandled(true);
            RECEPTION_CLIENT.accept(p);
        }
    }

    // ================================================================== C2S
    /** Ouvre un ecran : {@link #DUEL} (avec cible preselectionnee facultative) ou {@link #CLASSES}. */
    public record OuvrirC2S(int ecran, @Nullable UUID cible) {
        public static final int DUEL = 0, CLASSES = 1;

        public void write(FriendlyByteBuf b) {
            b.writeVarInt(ecran);
            b.writeBoolean(cible != null);
            if (cible != null) b.writeUUID(cible);
        }

        public static OuvrirC2S read(FriendlyByteBuf b) {
            int e = b.readVarInt();
            return new OuvrirC2S(e, b.readBoolean() ? b.readUUID() : null);
        }

        public static void handle(OuvrirC2S p, Supplier<NetworkEvent.Context> ctx) {
            // traitement : ReceptionPaquetsDuelsServeur (src/serveur), branché par la partie serveur
            MatchsNet.recevoirServeur(p, ctx);
        }
    }

    /** Lance un defi. */
    public record DefierC2S(UUID cible, String format, String mode, String carte) {
        public void write(FriendlyByteBuf b) {
            b.writeUUID(cible);
            b.writeUtf(format, 8);
            b.writeUtf(mode, 32);
            b.writeUtf(carte, 64);
        }

        public static DefierC2S read(FriendlyByteBuf b) {
            return new DefierC2S(b.readUUID(), b.readUtf(8), b.readUtf(32), b.readUtf(64));
        }

        public static void handle(DefierC2S p, Supplier<NetworkEvent.Context> ctx) {
            // traitement : ReceptionPaquetsDuelsServeur (src/serveur), branché par la partie serveur
            MatchsNet.recevoirServeur(p, ctx);
        }
    }

    /** Repond a un defi (le defie : accepter/refuser ; le defiant : refuser = annuler). */
    public record ReponseDefiC2S(int id, boolean accepte) {
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(id);
            b.writeBoolean(accepte);
        }

        public static ReponseDefiC2S read(FriendlyByteBuf b) {
            return new ReponseDefiC2S(b.readVarInt(), b.readBoolean());
        }

        public static void handle(ReponseDefiC2S p, Supplier<NetworkEvent.Context> ctx) {
            // traitement : ReceptionPaquetsDuelsServeur (src/serveur), branché par la partie serveur
            MatchsNet.recevoirServeur(p, ctx);
        }
    }

    /** Enregistre une classe (assainie par le serveur, qui renvoie la liste a jour). */
    public record ClasseC2S(int emplacement, ClassePerso classe) {
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(emplacement);
            classe.write(b);
        }

        public static ClasseC2S read(FriendlyByteBuf b) {
            return new ClasseC2S(b.readVarInt(), ClassePerso.read(b));
        }

        public static void handle(ClasseC2S p, Supplier<NetworkEvent.Context> ctx) {
            // traitement : ReceptionPaquetsDuelsServeur (src/serveur), branché par la partie serveur
            MatchsNet.recevoirServeur(p, ctx);
        }
    }

    /** Choix de la classe pendant la fenetre de choix d'un duel. */
    public record ChoixClasseC2S(int emplacement) {
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(emplacement);
        }

        public static ChoixClasseC2S read(FriendlyByteBuf b) {
            return new ChoixClasseC2S(b.readVarInt());
        }

        public static void handle(ChoixClasseC2S p, Supplier<NetworkEvent.Context> ctx) {
            // traitement : ReceptionPaquetsDuelsServeur (src/serveur), branché par la partie serveur
            MatchsNet.recevoirServeur(p, ctx);
        }
    }
}
