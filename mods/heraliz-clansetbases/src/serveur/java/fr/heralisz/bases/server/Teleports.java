package fr.heralisz.bases.server;

import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.combat.CombatLocal;
import fr.heralisz.bases.network.BasesNet;
import fr.heralisz.bases.network.Packets;
import fr.heralisz.bases.registry.Sons;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Teleportations differees des bases (BASES_SPEC §3, §4 ; RETOURS du 01/10 §2.2), dans les deux sens (entree, sortie,
 * pillage) : le joueur descend d'abord de tout vehicule ({@link Descente}), puis doit rester immobile {@code delai} ticks
 * (10 s par defaut). Annulee s'il bouge reellement (sa PROPRE position : plus de 0,3 bloc a l'horizontale ou 0,5 bloc a
 * la verticale de son point de depart), s'il remonte dans un vehicule, change de monde, subit un degat ({@link #degat}),
 * passe en combat (ou en match), meurt ou se deconnecte. Descente impossible : refus « descendez d'abord ». A l'echeance, {@code action} est appelee (elle refait ses verifications et
 * renvoie faux si elle a refuse, message deja envoye). Le client recoit {@code TeleportS2C} (DEBUT, ANNULEE, FAITE) et
 * anime seul le compte a rebours. Une seule attente par joueur : une demande du meme genre pendant l'attente est ignoree
 * (le compte a rebours n'est pas relance par un double clic), une demande d'un autre genre la remplace.
 * <p>Garde d'arrivee ({@link #surveillerArrivee}) : pendant 10 s apres une entree, un joueur qui passe sous le plancher
 * du sas (chute) est replace au point d'arrivee. Fil serveur uniquement.
 */
public final class Teleports {
    private Teleports() {
    }

    private static final double TOLERANCE_H2 = 0.3 * 0.3;
    private static final double TOLERANCE_V = 0.5;
    private static final long GARDE_ARRIVEE_MS = 10_000;

    private static final class Attente {
        final Packets.TeleportS2C.Genre genre;
        final ResourceKey<Level> monde;
        final Vec3 depart;
        final int total;
        int restant;
        final Predicate<ServerPlayer> action;

        Attente(Packets.TeleportS2C.Genre genre, ResourceKey<Level> monde, Vec3 depart, int total, Predicate<ServerPlayer> action) {
            this.genre = genre;
            this.monde = monde;
            this.depart = depart;
            this.total = total;
            this.restant = total;
            this.action = action;
        }
    }

    /** Point d'arrivee surveille : plancher du sas (y), point sur ou replacer le joueur, fin de la garde (ms). */
    private record Arrivee(ResourceKey<Level> monde, Vec3 point, float yaw, double plancher, long jusqua) {
    }

    private static final Map<UUID, Attente> ATTENTES = new HashMap<>();
    private static final Map<UUID, Arrivee> ARRIVEES = new HashMap<>();

    /**
     * Programme une teleportation ; {@code ticks} = 0 : immediate. Le joueur descend d'abord de son vehicule.
     *
     * @return vrai si un compte a rebours a commence (ou si la teleportation immediate a ete faite), faux si une attente
     * du meme genre etait deja en cours (rien n'est relance)
     */
    public static boolean programmer(ServerPlayer p, Packets.TeleportS2C.Genre genre, int ticks, Predicate<ServerPlayer> action) {
        Attente deja = ATTENTES.get(p.getUUID());
        if (deja != null && deja.genre == genre) {
            BaseService.refus(p, "msg.heralisz_bases.tp_deja");
            return false;
        }
        // passager d'un vehicule : il descend d'abord (sinon refus clair, jamais de compte a rebours depuis un vehicule)
        if (!Descente.faireDescendre(p, genre.name().toLowerCase(java.util.Locale.ROOT))) {
            BaseService.erreur(p, "msg.heralisz_bases.vehicule");
            return false;
        }
        if (ticks <= 0) {
            ATTENTES.remove(p.getUUID());
            if (action.test(p)) BasesNet.toPlayer(p, new Packets.TeleportS2C(genre, Packets.TeleportS2C.Etat.FAITE, 0, ""));
            return true;
        }
        ATTENTES.put(p.getUUID(), new Attente(genre, p.level().dimension(), p.position(), ticks, action));
        BasesNet.toPlayer(p, new Packets.TeleportS2C(genre, Packets.TeleportS2C.Etat.DEBUT, ticks, ""));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), Sons.TP_CHARGE.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        HeraliszBases.LOG.info("[bases] {} : {} dans {} s depuis {} {} {}", p.getGameProfile().getName(), genre, ticks / 20,
                String.format(java.util.Locale.ROOT, "%.2f", p.getX()), String.format(java.util.Locale.ROOT, "%.2f", p.getY()),
                String.format(java.util.Locale.ROOT, "%.2f", p.getZ()));
        return true;
    }

    public static boolean enAttente(UUID joueur) {
        return ATTENTES.containsKey(joueur);
    }

    /** Annule l'attente du joueur (raison = cle de traduction, « » = silencieuse). */
    public static void annuler(ServerPlayer p, String raison) {
        Attente a = ATTENTES.remove(p.getUUID());
        if (a == null) return;
        BasesNet.toPlayer(p, new Packets.TeleportS2C(a.genre, Packets.TeleportS2C.Etat.ANNULEE, a.total, raison));
        if (!raison.isEmpty()) BaseService.erreur(p, raison);
        HeraliszBases.LOG.info("[bases] {} : {} annulee ({})", p.getGameProfile().getName(), a.genre, raison.isEmpty() ? "demande" : raison);
    }

    /** Degat subi : annule l'attente (appele par BaseEvents). */
    public static void degat(ServerPlayer p) {
        if (ATTENTES.containsKey(p.getUUID())) annuler(p, "msg.heralisz_bases.tp_degat");
    }

    public static void oublier(UUID joueur) {
        ATTENTES.remove(joueur);
        ARRIVEES.remove(joueur);
    }

    /**
     * Garde d'arrivee : pendant 10 s, si le joueur descend sous {@code plancher} - 1,5 (chute hors du sas), il est replace
     * en {@code point}.
     */
    static void surveillerArrivee(ServerPlayer p, Vec3 point, float yaw, double plancher) {
        ARRIVEES.put(p.getUUID(), new Arrivee(p.level().dimension(), point, yaw, plancher, System.currentTimeMillis() + GARDE_ARRIVEE_MS));
    }

    public static void tick(MinecraftServer server) {
        if (!ARRIVEES.isEmpty()) gardeArrivees(server);
        if (ATTENTES.isEmpty()) return;
        boolean seconde = server.getTickCount() % 20 == 0;
        List<Map.Entry<ServerPlayer, Attente>> echues = null;
        for (Iterator<Map.Entry<UUID, Attente>> it = ATTENTES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Attente> e = it.next();
            Attente a = e.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.isAlive()) {
                it.remove();
                continue;
            }
            String raison = null;
            Vec3 pos = p.position();
            double dx = pos.x - a.depart.x, dz = pos.z - a.depart.z;
            if (p.level().dimension() != a.monde || p.isPassenger() || dx * dx + dz * dz > TOLERANCE_H2
                    || Math.abs(pos.y - a.depart.y) > TOLERANCE_V) {
                raison = "msg.heralisz_bases.tp_bouge";
            } else if (seconde && CombatLocal.tague(p) && !BaseService.estAdmin(p)) {
                raison = "msg.heralisz_bases.tp_combat";
            } else if (seconde && fr.heralisz.clans.api.ClansApi.enMatch(p)) {
                raison = "msg.heralisz_bases.en_match";
            }
            if (raison != null) {
                it.remove();
                BasesNet.toPlayer(p, new Packets.TeleportS2C(a.genre, Packets.TeleportS2C.Etat.ANNULEE, a.total, raison));
                BaseService.erreur(p, raison);
                HeraliszBases.LOG.info("[bases] {} : {} annulee ({}, ecart {} / {})", p.getGameProfile().getName(), a.genre, raison,
                        String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(dx * dx + dz * dz)),
                        String.format(java.util.Locale.ROOT, "%.2f", pos.y - a.depart.y));
                continue;
            }
            if (--a.restant > 0) continue;
            it.remove();
            if (echues == null) echues = new ArrayList<>(2);
            echues.add(Map.entry(p, a));
        }
        // actions hors de l'iteration : une action peut programmer une autre attente
        if (echues == null) return;
        for (Map.Entry<ServerPlayer, Attente> e : echues) {
            ServerPlayer p = e.getKey();
            Attente a = e.getValue();
            boolean ok;
            try {
                ok = a.action.test(p);
            } catch (RuntimeException ex) {
                HeraliszBases.LOG.error("[bases] teleportation {} de {} en erreur", a.genre, p.getGameProfile().getName(), ex);
                BaseService.erreur(p, "msg.heralisz_bases.erreur_interne");
                ok = false;
            }
            BasesNet.toPlayer(p, new Packets.TeleportS2C(a.genre, ok ? Packets.TeleportS2C.Etat.FAITE : Packets.TeleportS2C.Etat.ANNULEE,
                    a.total, ""));
        }
    }

    private static void gardeArrivees(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Arrivee>> it = ARRIVEES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Arrivee> e = it.next();
            Arrivee a = e.getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || now > a.jusqua() || p.level().dimension() != a.monde()) {
                it.remove();
                continue;
            }
            if (p.getY() >= a.plancher() - 1.5 || !(p.level() instanceof ServerLevel level)) continue;
            HeraliszBases.LOG.warn("[bases] {} sous le plancher du sas apres son arrivee (y {}) : replace au point d'arrivee",
                    p.getGameProfile().getName(), String.format(java.util.Locale.ROOT, "%.1f", p.getY()));
            p.fallDistance = 0;
            p.setDeltaMovement(Vec3.ZERO);
            p.teleportTo(level, a.point().x, a.point().y, a.point().z, a.yaw(), 0f);
        }
    }

    public static void vider() {
        ATTENTES.clear();
        ARRIVEES.clear();
    }
}
