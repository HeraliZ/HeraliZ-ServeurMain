package fr.heralisz.matchs.duels.net;

import fr.heralisz.matchs.duels.DuelsLogique;

import fr.heralisz.matchs.duels.Defis;
import fr.heralisz.matchs.duels.DuelPartie;
import fr.heralisz.matchs.duels.Duels;
import fr.heralisz.matchs.duels.armes.Catalogue;
import fr.heralisz.matchs.duels.classes.ClassePerso;
import fr.heralisz.matchs.moteur.api.MatchApi;
import fr.heralisz.matchs.moteur.api.Partie;
import fr.heralisz.matchs.reseau.MatchsNet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Traitement serveur des paquets client -> serveur de PaquetsDuels (corps d'origine des « handle », docs/METHODE_CLIENT_SERVEUR.md
 * §6). Appelé par serveur/PartieServeur.
 */
public final class ReceptionPaquetsDuelsServeur {
    private ReceptionPaquetsDuelsServeur() {
    }

    /** Rend vrai si le paquet était l'un des siens. */
    public static boolean recevoir(MatchsNet.Recu r) {
        Object o = r.paquet();
        if (o instanceof PaquetsDuels.OuvrirC2S p) {
            ouvrirC2S(p, r.ctx());
            return true;
        }
        if (o instanceof PaquetsDuels.DefierC2S p) {
            defierC2S(p, r.ctx());
            return true;
        }
        if (o instanceof PaquetsDuels.ReponseDefiC2S p) {
            reponseDefiC2S(p, r.ctx());
            return true;
        }
        if (o instanceof PaquetsDuels.ClasseC2S p) {
            classeC2S(p, r.ctx());
            return true;
        }
        if (o instanceof PaquetsDuels.ChoixClasseC2S p) {
            choixClasseC2S(p, r.ctx());
            return true;
        }
        return false;
    }

    /** Corps d'origine de PaquetsDuels.OuvrirC2S.handle. */
    private static void ouvrirC2S(PaquetsDuels.OuvrirC2S p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().setPacketHandled(true);
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null || !cadenceOk(sp, 0)) return;
            if (p.ecran() == PaquetsDuels.OuvrirC2S.CLASSES) DuelsLogique.ouvrirClasses(sp);
            else DuelsLogique.ouvrirEcranDuel(sp, p.cible());
    }

    /** Corps d'origine de PaquetsDuels.DefierC2S.handle. */
    private static void defierC2S(PaquetsDuels.DefierC2S p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().setPacketHandled(true);
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null || !cadenceOk(sp, 1)) return;
            Defis.defier(sp, p.cible(), p.format(), p.mode(), p.carte());
    }

    /** Corps d'origine de PaquetsDuels.ReponseDefiC2S.handle. */
    private static void reponseDefiC2S(PaquetsDuels.ReponseDefiC2S p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().setPacketHandled(true);
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null || !cadenceOk(sp, 2)) return;
            Defis.repondre(sp, p.id(), p.accepte());
    }

    /** Corps d'origine de PaquetsDuels.ClasseC2S.handle. */
    private static void classeC2S(PaquetsDuels.ClasseC2S p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().setPacketHandled(true);
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null || !cadenceOk(sp, 3)) return;
            DuelsLogique.enregistrerClasse(sp, p.emplacement(), p.classe());
    }

    /** Corps d'origine de PaquetsDuels.ChoixClasseC2S.handle. */
    private static void choixClasseC2S(PaquetsDuels.ChoixClasseC2S p, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().setPacketHandled(true);
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null || !cadenceOk(sp, 4)) return;
            Partie partie = MatchApi.partieDe(sp);
            if (partie instanceof DuelPartie d) d.choisirClasse(sp, p.emplacement());
    }

    /** Cadence des C2S : un paquet par joueur et par genre toutes les 150 ms au plus (le reste est ignore). */
    private static final Map<UUID, long[]> CADENCE = new HashMap<>();

    private static boolean cadenceOk(ServerPlayer p, int genre) {
        long[] t = CADENCE.computeIfAbsent(p.getUUID(), u -> new long[8]);
        long now = System.currentTimeMillis();
        if (now - t[genre] < 150) return false;
        t[genre] = now;
        return true;
    }

    /** Oublie la cadence d'un joueur (deconnexion). */
    public static void oublier(UUID joueur) {
        CADENCE.remove(joueur);
    }
}
