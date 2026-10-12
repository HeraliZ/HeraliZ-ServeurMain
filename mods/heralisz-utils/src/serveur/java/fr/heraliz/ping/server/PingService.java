package fr.heraliz.ping.server;

import fr.heraliz.ping.HeralizPing;
import fr.heraliz.ping.PingConfig;
import fr.heraliz.ping.PingType;
import fr.heraliz.ping.net.Paquets.CarteC2S;
import fr.heraliz.ping.net.Paquets.MajS2C;
import fr.heraliz.ping.net.Paquets.PingC2S;
import fr.heraliz.ping.net.Paquets.PingS2C;
import fr.heraliz.ping.net.Paquets.ReglagesS2C;
import fr.heraliz.ping.net.PingNet;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Cote serveur des pings : verification, relais au cercle de l'auteur, suivi. Fil serveur uniquement.
 * <p>Un ping recu passe, dans l'ordre : interrupteur general, auteur vivant et pas spectateur, cadence (seau a jetons :
 * {@code rafale} pings d'affilee puis un toutes les {@code recharge_ms}), cible (entite existante, visible et pas
 * spectatrice ; ou bloc dans un tronçon charge), portee, ligne de vue ({@link Vue}). Il est alors envoye a
 * {@link Cercles#destinataires} et a eux seuls.
 * <p>Au repos (aucun ping actif), {@link #tick} sort des la premiere ligne : aucun travail par tick. Pendant la vie d'un
 * ping : expiration, et toutes les 10 ticks la nouvelle place d'une entite pingee pour les destinataires trop loin
 * pour la suivre eux-memes.
 */
public final class PingService {
    private PingService() {
    }

    /** Au-dela de cette distance horizontale, le client risque de ne plus suivre l'entite (Mohist/Spigot : 32 a 48 blocs). */
    private static final double SUIVI_PROCHE = 28.0;
    /** Tolerance (blocs) entre le point vise et le bloc touche par le rayon du serveur. */
    private static final double TOLERANCE_VUE = 1.0;

    /** Un ping relaye, garde pendant sa vie (annulation, plafond par joueur, suivi d'entite). */
    static final class Actif {
        final int id;
        final UUID auteur;
        final ServerPlayer[] dest;
        final long finTick;
        @Nullable
        Entity entite;
        double x, y, z;

        Actif(int id, UUID auteur, ServerPlayer[] dest, long finTick, @Nullable Entity entite, double x, double y, double z) {
            this.id = id;
            this.auteur = auteur;
            this.dest = dest;
            this.finTick = finTick;
            this.entite = entite;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final ArrayList<Actif> ACTIFS = new ArrayList<>();
    private static final HashMap<UUID, Seau> SEAUX = new HashMap<>();
    private static final ArrayList<ServerPlayer> CERCLE = new ArrayList<>();
    /** Banc d'essai : faux joueurs a qui l'on envoie comme s'ils avaient le mod. */
    private static final Set<UUID> PRESENCE_FORCEE = new HashSet<>();
    private static int prochainId = 1;

    /** Compteurs (affiches par /ping admin). */
    public static long recus, relayes, paquets, refusCadence, refusPortee, refusVue, refusCible, refusSpectateur, refusInactif;
    /** Temps passe a traiter les pings acceptes (ns), pour la moyenne. */
    public static long nanosAcceptes;

    // ------------------------------------------------------------------ reception
    /** Ping propose par un client (deja sur le fil serveur). */
    public static void recevoir(ServerPlayer p, PingC2S m) {
        long t0 = System.nanoTime();
        recus++;
        if (!PingConfig.ACTIF.get()) {
            refusInactif++;
            return;
        }
        if (!Regles.peutPinguer(p.isAlive(), p.isSpectator())) {
            refusSpectateur++;
            return;
        }
        long maintenant = Util.getMillis();
        Seau seau = SEAUX.get(p.getUUID());
        if (seau == null) {
            seau = new Seau(PingConfig.RAFALE.get(), maintenant);
            SEAUX.put(p.getUUID(), seau);
        }
        if (!seau.prendre(maintenant, PingConfig.RAFALE.get(), PingConfig.RECHARGE_MS.get())) {
            refusCadence++;
            signaler(p, seau, maintenant, "heraliz_ping.msg.cadence");
            return;
        }
        ServerLevel niveau = p.serverLevel();
        int portee = PingConfig.PORTEE.get();
        boolean vue = PingConfig.VERIFIER_VUE.get();
        Vec3 oeil = p.getEyePosition();
        Vec3 oeilAvant = new Vec3(p.xo, p.yo + p.getEyeHeight(), p.zo);
        Entity cible = null;
        double x, y, z;
        long bloc;
        if (m.entite() >= 0) {
            cible = niveau.getEntity(m.entite());
            if (cible == null || cible == p || cible.isRemoved() || cible.isSpectator() || cible.isInvisibleTo(p)) {
                refusCible++;
                return;
            }
            x = cible.getX();
            y = cible.getY() + cible.getBbHeight();
            z = cible.getZ();
            bloc = BlockPos.containing(x, y, z).asLong();
            AABB boite = cible.getBoundingBox();
            if (!Regles.aPortee(boite.distanceToSqr(oeil), portee)) {
                refusPortee++;
                signaler(p, seau, maintenant, "heraliz_ping.msg.portee");
                return;
            }
            if (vue && !Vue.boiteVisible(niveau, oeil, boite) && !Vue.boiteVisible(niveau, oeilAvant, boite)) {
                refusVue++;
                signaler(p, seau, maintenant, "heraliz_ping.msg.vue");
                return;
            }
        } else {
            x = m.x();
            y = m.y();
            z = m.z();
            bloc = m.bloc();
            if (!Regles.aPortee(oeil.distanceToSqr(x, y, z), portee)) {
                refusPortee++;
                signaler(p, seau, maintenant, "heraliz_ping.msg.portee");
                return;
            }
            BlockPos pos = BlockPos.of(bloc);
            if (niveau.isOutsideBuildHeight(pos) || !niveau.hasChunkAt(pos)) {
                refusCible++;
                return;
            }
            Vec3 point = new Vec3(x, y, z);
            if (vue && !Vue.blocVisible(niveau, oeil, point, pos, TOLERANCE_VUE) && !Vue.blocVisible(niveau, oeilAvant, point, pos, TOLERANCE_VUE)) {
                refusVue++;
                signaler(p, seau, maintenant, "heraliz_ping.msg.vue");
                return;
            }
        }
        int couleur = Cercles.destinataires(p, CERCLE);
        if (couleur == Cercles.REFUS) {
            refusSpectateur++;
            return;
        }
        PingType type = m.type() == PingType.AUTO ? Contexte.resoudre(niveau, cible, bloc, CERCLE) : PingType.parId(m.type());
        if (type == null) return;
        plafonner(p.getUUID(), PingConfig.MAX_PAR_JOUEUR.get() - 1);
        int id = prochainId++;
        if (prochainId > 1_000_000_000) prochainId = 1;
        int duree = PingConfig.DUREE_S.get();
        PingS2C msg = new PingS2C(id, type.id(), false, cible == null ? -1 : cible.getId(), bloc, x, y, z, couleur, duree,
                p.getGameProfile().getName());
        ServerPlayer[] dest = CERCLE.toArray(new ServerPlayer[0]);
        for (ServerPlayer d : dest) envoyer(d, d == p ? msg.pourAuteur() : msg);
        ACTIFS.add(new Actif(id, p.getUUID(), dest, p.server.getTickCount() + duree * 20L + 10, cible, x, y, z));
        relayes++;
        nanosAcceptes += System.nanoTime() - t0;
    }

    /**
     * Ping pose depuis la carte du monde (clic droit) : memes regles que {@link #recevoir} pour l'auteur, la cadence et
     * les destinataires, mais sans portee ni ligne de vue (on marque un point de la carte, pas ce qu'on vise). Le point est
     * dans le monde de l'auteur et dans sa bordure ; sa hauteur est le dessus du terrain si le troncon est charge (aucun
     * chargement force), sinon celle de l'auteur. Interrupteur {@code carte} de la config.
     */
    public static void recevoirCarte(ServerPlayer p, CarteC2S m) {
        long t0 = System.nanoTime();
        recus++;
        if (!PingConfig.ACTIF.get()) {
            refusInactif++;
            return;
        }
        if (!Regles.peutPinguer(p.isAlive(), p.isSpectator())) {
            refusSpectateur++;
            return;
        }
        long maintenant = Util.getMillis();
        Seau seau = SEAUX.get(p.getUUID());
        if (seau == null) {
            seau = new Seau(PingConfig.RAFALE.get(), maintenant);
            SEAUX.put(p.getUUID(), seau);
        }
        if (!PingConfig.CARTE.get()) {
            refusInactif++;
            signaler(p, seau, maintenant, "heraliz_ping.msg.carte");
            return;
        }
        if (!seau.prendre(maintenant, PingConfig.RAFALE.get(), PingConfig.RECHARGE_MS.get())) {
            refusCadence++;
            signaler(p, seau, maintenant, "heraliz_ping.msg.cadence");
            return;
        }
        ServerLevel niveau = p.serverLevel();
        double x = Math.floor(m.x()) + 0.5, z = Math.floor(m.z()) + 0.5;
        if (!Double.isFinite(x) || !Double.isFinite(z) || !niveau.getWorldBorder().isWithinBounds(x, z)) {
            refusCible++;
            return;
        }
        BlockPos colonne = BlockPos.containing(x, 0, z);
        double y = niveau.hasChunkAt(colonne)
                ? niveau.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, colonne.getX(), colonne.getZ()) + 0.05
                : p.getY();
        long bloc = BlockPos.containing(x, y - 0.5, z).asLong();
        int couleur = Cercles.destinataires(p, CERCLE);
        if (couleur == Cercles.REFUS) {
            refusSpectateur++;
            return;
        }
        PingType type = m.type() == PingType.AUTO ? PingType.ALLER : PingType.parId(m.type());
        if (type == null) return;
        plafonner(p.getUUID(), PingConfig.MAX_PAR_JOUEUR.get() - 1);
        int id = prochainId++;
        if (prochainId > 1_000_000_000) prochainId = 1;
        int duree = PingConfig.DUREE_S.get();
        PingS2C msg = new PingS2C(id, type.id(), false, -1, bloc, x, y, z, couleur, duree, p.getGameProfile().getName());
        ServerPlayer[] dest = CERCLE.toArray(new ServerPlayer[0]);
        for (ServerPlayer d : dest) envoyer(d, d == p ? msg.pourAuteur() : msg);
        ACTIFS.add(new Actif(id, p.getUUID(), dest, p.server.getTickCount() + duree * 20L + 10, null, x, y, z));
        relayes++;
        nanosAcceptes += System.nanoTime() - t0;
    }

    /** L'auteur retire son ping (appui bref sur son propre marqueur). */
    public static void annuler(ServerPlayer p, int id) {
        for (int i = 0; i < ACTIFS.size(); i++) {
            Actif a = ACTIFS.get(i);
            if (a.id == id) {
                if (a.auteur.equals(p.getUUID())) {
                    ACTIFS.remove(i);
                    finir(a);
                }
                return;
            }
        }
    }

    /** Garde au plus {@code garder} pings actifs de l'auteur : les plus anciens s'effacent chez leurs destinataires. */
    private static void plafonner(UUID auteur, int garder) {
        int n = 0;
        for (int i = 0; i < ACTIFS.size(); i++) if (ACTIFS.get(i).auteur.equals(auteur)) n++;
        for (int i = 0; i < ACTIFS.size() && n > garder; ) {
            Actif a = ACTIFS.get(i);
            if (a.auteur.equals(auteur)) {
                ACTIFS.remove(i);
                finir(a);
                n--;
            } else {
                i++;
            }
        }
    }

    private static void finir(Actif a) {
        MajS2C fin = MajS2C.fin(a.id);
        for (ServerPlayer d : a.dest) envoyer(d, fin);
    }

    private static void envoyer(ServerPlayer d, Object msg) {
        if (d.hasDisconnected() || !PingNet.present(d)) return;
        PingNet.versJoueur(d, msg);
        paquets++;
    }

    /** Message discret dans la barre d'action (au plus un toutes les 1,5 s). Reponse directe au joueur. */
    private static void signaler(ServerPlayer p, Seau seau, long maintenant, String cle) {
        if (maintenant - seau.dernierMessage < 1500) return;
        seau.dernierMessage = maintenant;
        Component c = cle.endsWith("portee") ? Component.translatable(cle, PingConfig.PORTEE.get()) : Component.translatable(cle);
        p.sendSystemMessage(c.copy().withStyle(ChatFormatting.GRAY), true);
    }

    // ------------------------------------------------------------------ vie des pings
    /** Fin de tick serveur. Au repos : un test de liste vide, rien d'autre. */
    public static void tick(MinecraftServer serveur) {
        if (ACTIFS.isEmpty()) return;
        long t = serveur.getTickCount();
        boolean suivre = t % 10 == 0;
        for (int i = ACTIFS.size() - 1; i >= 0; i--) {
            Actif a = ACTIFS.get(i);
            if (t >= a.finTick) {
                ACTIFS.remove(i); // les clients effacent le ping d'eux-memes a la meme heure
                continue;
            }
            if (suivre && a.entite != null) suivre(a);
        }
    }

    /** Entite pingee qui bouge : sa nouvelle place aux destinataires trop loin pour la suivre (un paquet de 16 octets). */
    private static void suivre(Actif a) {
        Entity e = a.entite;
        if (e == null || e.isRemoved() || !e.isAlive()) {
            a.entite = null; // le marqueur reste a la derniere place connue
            return;
        }
        double x = e.getX(), y = e.getY() + e.getBbHeight(), z = e.getZ();
        double dx = x - a.x, dy = y - a.y, dz = z - a.z;
        if (dx * dx + dy * dy + dz * dz < 0.75 * 0.75) return;
        a.x = x;
        a.y = y;
        a.z = z;
        MajS2C msg = null;
        for (ServerPlayer d : a.dest) {
            if (d.hasDisconnected() || d.level() != e.level()) continue;
            double hx = d.getX() - x, hz = d.getZ() - z;
            if (hx * hx + hz * hz < SUIVI_PROCHE * SUIVI_PROCHE) continue; // le client suit l'entite lui-meme
            if (msg == null) msg = new MajS2C(a.id, false, BlockPos.containing(x, y, z).asLong(), x, y, z);
            envoyer(d, msg);
        }
    }

    // ------------------------------------------------------------------ connexions, config
    public static void connexion(ServerPlayer p) {
        if (PingNet.present(p)) PingNet.versJoueur(p, reglages());
    }

    public static void deconnexion(ServerPlayer p) {
        SEAUX.remove(p.getUUID());
    }

    /** Regles a jour envoyees a tous les clients (apres /ping admin). */
    public static void diffuserReglages(MinecraftServer serveur) {
        ReglagesS2C r = reglages();
        for (ServerPlayer p : serveur.getPlayerList().getPlayers()) if (PingNet.present(p)) PingNet.versJoueur(p, r);
    }

    private static ReglagesS2C reglages() {
        return new ReglagesS2C(PingConfig.PORTEE.get(), PingConfig.ACTIF.get());
    }

    /** Arret du serveur : tout est oublie. */
    public static void vider() {
        ACTIFS.clear();
        SEAUX.clear();
        CERCLE.clear();
        PRESENCE_FORCEE.clear();
        HeralizPing.LOG.info("[ping] {} pings recus, {} relayes ({} paquets), refus : cadence {}, portee {}, vue {}, cible {}, spectateur {}",
                recus, relayes, paquets, refusCadence, refusPortee, refusVue, refusCible, refusSpectateur);
    }

    /** Banc : pseudos des destinataires du dernier ping accepte. */
    public static String derniersDestinataires() {
        StringBuilder sb = new StringBuilder();
        for (ServerPlayer d : CERCLE) sb.append(sb.length() == 0 ? "" : ", ").append(d.getGameProfile().getName());
        return sb.toString();
    }

    public static int actifs() {
        return ACTIFS.size();
    }

    // ------------------------------------------------------------------ banc d'essai
    public static boolean presenceForcee(ServerPlayer p) {
        return !PRESENCE_FORCEE.isEmpty() && PRESENCE_FORCEE.contains(p.getUUID());
    }

    /** Banc : traite ce faux joueur comme un client qui a le mod. */
    public static void forcerPresence(UUID u, boolean oui) {
        if (oui) PRESENCE_FORCEE.add(u);
        else PRESENCE_FORCEE.remove(u);
    }

    /** Banc : seau plein pour ce joueur (les verifications ne se genent pas entre elles). */
    public static void remplirSeau(UUID u) {
        SEAUX.remove(u);
    }

    /** Banc : oublie tous les pings actifs (sans rien envoyer). */
    public static void oublierActifs() {
        ACTIFS.clear();
    }
}
