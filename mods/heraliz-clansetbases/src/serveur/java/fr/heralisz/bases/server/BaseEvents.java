package fr.heralisz.bases.server;

import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.api.BasesApi;
import fr.heralisz.bases.model.Base;
import fr.heralisz.bases.monde.Chantier;
import fr.heralisz.bases.monde.Grille;
import fr.heralisz.bases.network.BasesNet;
import fr.heralisz.bases.network.Packets;
import fr.heralisz.clans.api.ClanInfo;
import fr.heralisz.clans.api.ClansApi;
import fr.heralisz.clans.api.events.ClanDissousEvent;
import fr.heralisz.clans.api.events.MembreQuitteEvent;
import fr.heralisz.clans.model.Perm;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cycle de vie et tick du coeur : reprise au demarrage, travaux par tranches, teleportations differees, controle de
 * presence chaque seconde (qui n'a rien a faire dans une base en est sorti), HUD de base (envoye seulement quand il
 * change), dissolution et depart d'un membre (evenements du mod clans), arret propre.
 */
public final class BaseEvents {
    private BaseEvents() {
    }

    /** Dernier HUD envoye par joueur (rien n'est renvoye s'il ne change pas). */
    private static final Map<UUID, Packets.HudS2C> HUD = new HashMap<>();
    private static int seconde;
    private static String jourVu = "";

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        BaseService.reconcilier(e.getServer());
        jourVu = Fenetre.jour();
        BasesStore s = BasesStore.get();
        if (s != null) s.purgerEntrees(jourVu);
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        Chantier.vider();
        Teleports.vider();
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        HUD.clear();
        seconde = 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = e.getServer();
        Chantier.tick();
        Teleports.tick(server);
        if (++seconde < 20) return;
        seconde = 0;
        String jour = Fenetre.jour();
        if (!jour.equals(jourVu)) {
            jourVu = jour;
            BasesStore s = BasesStore.get();
            if (s != null) s.purgerEntrees(jour);
        }
        presence(server);
    }

    /** Chaque seconde : joueurs de la dimension des bases sans droit d'y etre renvoyes, HUD mis a jour. */
    private static void presence(MinecraftServer server) {
        ServerLevel level = Grille.niveau(server);
        if (level == null || level.players().isEmpty()) return;
        List<ServerPlayer> joueurs = new ArrayList<>(level.players());
        for (ServerPlayer p : joueurs) {
            // passager d'un vehicule « fantome » (retire, ou reste dans un autre monde : le train de HeraliZ Utils) : il
            // garderait une position serveur figee et le mod du train annulerait tous ses clics (ni poser ni casser)
            if (Descente.vehiculeFantome(p)) Descente.faireDescendre(p, "presence");
            // seuls les admins (op en creatif / spectateur) echappent au controle ; un spectateur non op est controle
            if (BaseService.estAdmin(p)) {
                hud(p);
                continue;
            }
            Base b = BaseService.baseA(level, p.blockPosition());
            if (b == null || !b.prete()) {
                BaseService.ejecter(p, "msg.heralisz_bases.base_indisponible");
                continue;
            }
            boolean membre = BaseService.estMembre(p, b);
            if (membre && !ClansApi.aPerm(p.getUUID(), b.clan, Perm.BASE_ENTRER)) {
                BaseService.ejecter(p, "msg.heralisz_bases.droit_perdu");
                continue;
            }
            if (!membre && !pillard(p, b)) {
                BaseService.ejecter(p, "msg.heralisz_bases.pas_autorise");
                continue;
            }
            hud(p);
        }
    }

    private static boolean pillard(ServerPlayer p, Base b) {
        try {
            return BasesApi.pillage().estPillard(p, b);
        } catch (RuntimeException ex) {
            HeraliszBases.LOG.error("[bases] module pillage : estPillard en erreur", ex);
            return false;
        }
    }

    /** Envoie le HUD tout de suite (apres une teleportation). */
    static void hudMaintenant(ServerPlayer p) {
        hud(p);
    }

    private static void hud(ServerPlayer p) {
        Packets.HudS2C h = Packets.HudS2C.AUCUN;
        Base b = Grille.estBunkers(p.level()) ? BaseService.baseA(p.level(), p.blockPosition()) : null;
        if (b != null) {
            Optional<ClanInfo> ci = ClansApi.infos(b.clan);
            String tag = ci.map(ClanInfo::tag).orElse(b.tag);
            int couleur = ci.map(c -> c.banniere().accent()).orElse(0x6FA8FF);
            String zone = BaseService.zone(b, p.blockPosition());
            if (BaseService.estMembre(p, b) || BaseService.estAdmin(p)) {
                h = new Packets.HudS2C(Packets.HudS2C.Mode.BASE, tag, couleur, zone, 0);
            } else {
                long fin = 0;
                try {
                    fin = BasesApi.pillage().finPillageMs(p);
                } catch (RuntimeException ex) {
                    HeraliszBases.LOG.error("[bases] module pillage : finPillageMs en erreur", ex);
                }
                if (fin <= 0) fin = Fenetre.finMs();
                h = new Packets.HudS2C(Packets.HudS2C.Mode.PILLAGE, tag, couleur, zone, fin);
            }
        }
        Packets.HudS2C avant = HUD.get(p.getUUID());
        if (h.equals(avant) || (avant == null && h.mode() == Packets.HudS2C.Mode.AUCUN)) return;
        HUD.put(p.getUUID(), h);
        BasesNet.toPlayer(p, h);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) HUD.remove(p.getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID u = e.getEntity().getUUID();
        HUD.remove(u);
        Teleports.oublier(u);
        ServerHandlers.oublier(u);
    }

    @SubscribeEvent
    public static void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !Grille.estBunkers(p.level())) hud(p);
    }

    /** Mort dans une base puis reapparition dehors : ce n'est pas un changement de dimension, le HUD doit s'effacer. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !Grille.estBunkers(p.level())) hud(p);
    }

    /** Un degat annule la teleportation en attente (apres les annulations des autres mods). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent e) {
        if (!e.isCanceled() && e.getAmount() > 0 && e.getEntity() instanceof ServerPlayer p) Teleports.degat(p);
    }

    // ------------------------------------------------------------------ evenements du mod clans
    @SubscribeEvent
    public static void onDissous(ClanDissousEvent e) {
        BasesStore s = BasesStore.get();
        Base b = s == null ? null : s.base(e.clanId);
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (b == null || server == null) return;
        BaseService.dissoudre(server, b);
    }

    @SubscribeEvent
    public static void onQuitte(MembreQuitteEvent e) {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(e.joueur);
        if (p == null || !Grille.estBunkers(p.level())) return;
        Base b = BaseService.baseA(p.level(), p.blockPosition());
        if (b != null && b.clan.equals(e.clanId) && !BaseService.estAdmin(p)) {
            BaseService.ejecter(p, e.exclu ? "msg.heralisz_bases.exclu" : "msg.heralisz_bases.quitte");
        }
        Sync.etat(p);
    }
}
