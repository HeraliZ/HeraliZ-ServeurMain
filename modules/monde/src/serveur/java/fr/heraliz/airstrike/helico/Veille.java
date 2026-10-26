package fr.heraliz.airstrike.helico;

import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.api.HelicoApi;
import fr.heraliz.airstrike.entity.HelicopterServeur;
import fr.heraliz.airstrike.entity.HelicopterWreckServeur;
import fr.heraliz.airstrike.network.HelicoCartePacket;
import fr.heraliz.airstrike.network.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Veille de l'hélicoptère, côté serveur (inscrite par la partie serveur d'Airstrike) :
 * <ul>
 * <li><b>départs</b> : un hélicoptère lancé attend que son tronçon d'arrivée soit chargé et actif (demandé par un ticket,
 * jamais chargé sur le fil du serveur) ; au plus {@link #ATTENTE_DEPART_S} secondes, sinon il est perdu ;</li>
 * <li><b>carte M</b> : toutes les 2 s, la position de chaque hélicoptère et de chaque épave est envoyée à tous les joueurs
 * de la carte (overworld) ; « retiré » à leur fin ({@link #retire}) ;</li>
 * <li><b>épaves</b> : fin du feu et retrait vérifiés en heure de jeu, même quand l'épave ne tourne pas.</li>
 * </ul>
 * Sans départ, hélicoptère ni épave, le tick sort tout de suite (trois tests de listes vides).
 */
public final class Veille {
    private Veille() {
    }

    /** Attente maximale du tronçon d'arrivée (secondes). */
    public static final int ATTENTE_DEPART_S = 60;
    /** Temps maximal pour quitter la carte après la fin de la patrouille (secondes) : au-delà, il disparaît. */
    public static final int SORTIE_MAX_S = 300;
    /** Tient chargé le tronçon d'arrivée en attendant l'hélicoptère ; rafraîchi chaque seconde, expire seul. */
    public static final TicketType<Integer> TICKET_DEPART = TicketType.create("airstrike_helico_depart", Integer::compareTo, 300);

    private record Depart(ServerLevel level, String id, HelicoApi.Reglages r, Vec3 p, float lacet,
                          @Nullable Function<UUID, UUID> clanDe, HelicoApi.Suivi suivi, ChunkPos cp, int cle, long limite) {
    }

    private static final List<Depart> DEPARTS = new ArrayList<>();
    /** Valeur du ticket d'attente : négative, propre à chaque départ (jamais celle d'une entité). */
    private static int cles;
    private static int ticks;

    // ------------------------------------------------------------------ départs
    @Nullable
    public static Vec3 lancer(ServerLevel level, String id, HelicoApi.Reglages r, @Nullable Vec3 depart,
                              @Nullable Function<UUID, UUID> clanDe, HelicoApi.Suivi suivi) {
        if (level.dimension() != Level.OVERWORLD || enAttente(id) || HelicopterServeur.enVol(id) != null) return null;
        RandomSource rnd = level.random;
        Vec3 p;
        float lacet;
        if (depart != null) {
            p = depart;
            lacet = rnd.nextFloat() * 360f;
        } else {
            double[] e = HelicopterServeur.entree(level, rnd);
            p = new Vec3(e[0], 0, e[1]);
            lacet = (float) e[2];
        }
        ChunkPos cp = new ChunkPos(BlockPos.containing(p));
        int cle = --cles;
        level.getChunkSource().addRegionTicket(TICKET_DEPART, cp, HelicopterServeur.RAYON_TICKET, cle);
        DEPARTS.add(new Depart(level, id, r, p, lacet, clanDe, suivi, cp, cle, level.getGameTime() + ATTENTE_DEPART_S * 20L));
        return p;
    }

    public static boolean enAttente(String id) {
        for (Depart d : DEPARTS) if (d.id().equals(id)) return true;
        return false;
    }

    /** Annule un départ en attente (staff, arrêt) : ticket rendu, suivi prévenu. */
    public static boolean annulerDepart(String id) {
        Iterator<Depart> it = DEPARTS.iterator();
        while (it.hasNext()) {
            Depart d = it.next();
            if (!d.id().equals(id)) continue;
            it.remove();
            rendre(d);
            prevenir(d, "annule");
            return true;
        }
        return false;
    }

    private static void rendre(Depart d) {
        d.level().getChunkSource().removeRegionTicket(TICKET_DEPART, d.cp(), HelicopterServeur.RAYON_TICKET, d.cle());
    }

    private static void prevenir(Depart d, String cause) {
        try {
            d.suivi().fini(d.id(), cause);
        } catch (RuntimeException ex) {
            AirstrikeMod.LOGGER.error("[helico] {} : suivi en erreur", d.id(), ex);
        }
    }

    private static void departs() {
        Iterator<Depart> it = DEPARTS.iterator();
        while (it.hasNext()) {
            Depart d = it.next();
            ServerLevel level = d.level();
            if (level.getChunkSource().getChunkNow(d.cp().x, d.cp().z) != null && level.isPositionEntityTicking(BlockPos.containing(d.p()))) {
                it.remove();
                HelicopterServeur h = HelicopterServeur.creer(level, d.id(), d.r(), d.p(), d.lacet(), d.clanDe(), d.suivi());
                rendre(d);   // après le ticket de l'hélicoptère lui-même : le tronçon ne se décharge jamais entre les deux
                if (h == null) {
                    AirstrikeMod.LOGGER.warn("[helico] {} : impossible a poser en {}", d.id(), BlockPos.containing(d.p()).toShortString());
                    prevenir(d, "perdu");
                }
            } else if (level.getGameTime() > d.limite()) {
                it.remove();
                rendre(d);
                AirstrikeMod.LOGGER.warn("[helico] {} : troncon d'arrivee pas pret en {} s, abandonne", d.id(), ATTENTE_DEPART_S);
                prevenir(d, "perdu");
            } else if (ticks % 20 == 0) {
                level.getChunkSource().addRegionTicket(TICKET_DEPART, d.cp(), HelicopterServeur.RAYON_TICKET, d.cle());   // rafraîchi
            }
        }
    }

    // ------------------------------------------------------------------ carte M
    /** Marques de la carte M : hélicoptères et épaves, à tous les joueurs de la carte. */
    private static void carte(MinecraftServer server) {
        ServerLevel carte = server.overworld();
        List<ServerPlayer> joueurs = carte.players();
        if (joueurs.isEmpty()) return;
        List<HelicoCartePacket> paquets = new ArrayList<>();
        for (HelicopterServeur h : HelicopterServeur.tous()) {
            if (h.level() == carte && !h.isRemoved()) paquets.add(new HelicoCartePacket(HelicoCartePacket.HELICO, h.getId(), h.getX(), h.getZ(), h.getYRot()));
        }
        for (HelicopterWreckServeur w : HelicopterWreckServeur.toutes()) {
            if (w.level() == carte && !w.isRemoved()) paquets.add(new HelicoCartePacket(HelicoCartePacket.EPAVE, w.getId(), w.getX(), w.getZ(), w.getYRot()));
        }
        for (ServerPlayer p : joueurs) for (HelicoCartePacket x : paquets) Network.sendTo(p, x);
    }

    /** Un hélicoptère ou une épave n'est plus là : sa marque est retirée de la carte de tous. */
    public static void retire(ServerLevel level, int entite) {
        if (level.dimension() != Level.OVERWORLD) return;
        HelicoCartePacket x = new HelicoCartePacket(HelicoCartePacket.RETIRE, entite, 0, 0, 0);
        for (ServerPlayer p : level.players()) Network.sendTo(p, x);
    }

    // ------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer() == null) return;
        if (DEPARTS.isEmpty() && HelicopterServeur.tous().isEmpty() && HelicopterWreckServeur.toutes().isEmpty()) return;
        ticks++;
        if (!DEPARTS.isEmpty() && ticks % 5 == 0) departs();
        if (ticks % 40 == 0) carte(e.getServer());
        if (ticks % 20 == 0 && !HelicopterWreckServeur.toutes().isEmpty()) {
            long now = e.getServer().overworld().getGameTime();
            for (HelicopterWreckServeur w : new ArrayList<>(HelicopterWreckServeur.toutes())) w.verifier(now);
        }
    }

    @SubscribeEvent
    public static void arret(ServerStoppedEvent e) {
        DEPARTS.clear();
    }
}
