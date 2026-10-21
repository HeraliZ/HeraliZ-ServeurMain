package fr.heralisz.clans.territory;

import fr.heralisz.clans.ClansConfig;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.ClanSnapshot;
import fr.heralisz.clans.model.EtatZone;
import fr.heralisz.clans.model.Niveaux;
import fr.heralisz.clans.model.Perm;
import fr.heralisz.clans.model.ZoneVue;
import fr.heralisz.clans.network.ClansNet;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.registry.ClansRegistry;
import fr.heralisz.clans.registry.Sons;
import fr.heralisz.clans.server.ClanStore;
import fr.heralisz.clans.server.ServerHandlers;
import fr.heralisz.clans.territory.api.TerritoireEvents;
import fr.heralisz.clans.territory.supply.Ravitaillement;
import fr.heralisz.clans.territory.supply.TableButin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Moteur des territoires (serveur, monde principal) : zones de zones.json, etat persistant (SavedData), presence et
 * capture toutes les 10 ticks, barre de capture (TerritoireHudS2C) aux joueurs presents, bonus de loot (HeraliZ Loot),
 * drapeaux synchronises, signal de ravitaillement, commandes admin. API statique pour les autres modules ; les
 * changements importants sont postes sur le bus Forge ({@link TerritoireEvents}).
 *
 * <p>Machine d'etat d'une zone (toutes les 10 ticks ; O = proprietaire, C = clans presents hors O, eligibles =
 * survie/aventure, vivants, dans un clan, perm CAPTURER) :
 * <ul>
 * <li>aucun C : la progression decroit de 1 %/s vers 0 ; etat NEUTRE (libre) ou TENUE ; un membre de O voit DEFENSE
 * (1 - progression) tant qu'elle n'est pas revenue a 0 ;</li>
 * <li>un seul C mais des membres de O presents, ou >= 2 C : CONTESTE, rien ne bouge ;</li>
 * <li>un seul C, zone protegee (10 min apres une capture) : PROTEGEE, rien ne bouge ;</li>
 * <li>un seul C qui a atteint la limite de territoires de son niveau : LIMITE, rien ne bouge ;</li>
 * <li>un seul C : CAPTURE, progression += 0,5 s / duree (60 s si libre, 90 s sinon) x min(2, 1 + 0,25 (n - 1)) ;
 * a 100 % : capture (proprietaire = C, protection, prochain ravitaillement dans 1 h 30, evenements Perdu + Capture).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = HeraliszClans.MODID)
public final class Territoires {
    private static final int PERIODE_TICKS = 10;
    private static final int MINUTE_TICKS = 1200;
    /** Decroissance : 1 %/s, soit 0,5 % par cycle de 10 ticks. */
    private static final float DECROISSANCE = 0.005f;
    public static final int COULEUR_NEUTRE = 0x6FA8FF;

    private static Territoires instance;

    private final MinecraftServer server;
    private final Path fichierZones;
    private final Map<String, Zone> zones = new LinkedHashMap<>();
    private final TerritoireData data;
    private final Map<String, EtatZone> etatsAffiches = new HashMap<>();
    private final Map<UUID, Packets.TerritoireHudS2C> dernierHud = new HashMap<>();
    private final Map<String, List<ServerPlayer>> presents = new HashMap<>();
    private final LootBonus lootBonus;
    private final Ravitaillement ravitaillement;
    private final Assauts assauts = new Assauts();
    /** zones.json illisible au dernier chargement : aucune ecriture tant qu'un rechargement n'a pas reussi. */
    private boolean zonesIllisibles;
    private int ticks;

    private Territoires(MinecraftServer server) {
        this.server = server;
        this.fichierZones = FMLPaths.CONFIGDIR.get().resolve("heralisz_clans").resolve("zones.json");
        List<Zone> lues = Zones.charger(fichierZones);
        if (lues == null) {
            zonesIllisibles = true;
            HeraliszClans.LOG.error("[clans] zones.json illisible : territoires en lecture seule, corrigez le fichier puis /territoire recharger");
        } else {
            for (Zone z : lues) zones.put(z.id, z);
        }
        this.data = server.overworld().getDataStorage().computeIfAbsent(TerritoireData::load, TerritoireData::new, TerritoireData.NOM);
        this.lootBonus = new LootBonus(server);
        this.ravitaillement = new Ravitaillement(server);
        TableButin.recharger();
        Assauts.charger();
        for (Zone z : zones.values()) z.calerHauteur(monde());
        HeraliszClans.LOG.info("[clans] territoires prets : {} zone(s), {} etat(s) sauvegardes", zones.size(), data.tous().size());
    }

    // ------------------------------------------------------------------ cycle de vie
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        instance = new Territoires(e.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        if (instance != null) instance.arreter();
        instance = null;
    }

    /**
     * Les vols en cours ne survivent pas a l'arret : une zone dont le signal a pose le delai mais dont la caisse n'est
     * pas encore larguee retrouve son delai a 0 (la SavedData est ecrite juste apres cet evenement).
     */
    private void arreter() {
        assauts.arreter(monde());
        for (String zoneId : ravitaillement.zonesDelaiARendre()) {
            EtatTerritoire e = data.tous().get(zoneId);
            if (e == null || e.prochainRavitaillement == 0) continue;
            e.prochainRavitaillement = 0;
            data.setDirty();
            HeraliszClans.LOG.info("[clans] arret pendant le ravitaillement de {} : delai rendu au clan", zoneId);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && instance != null) instance.tick();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (instance != null && e.getEntity() instanceof ServerPlayer p) {
            instance.dernierHud.remove(p.getUUID());
            instance.lootBonus.oublier(p.getUUID());
            instance.derniereCarte.remove(p.getUUID());
        }
    }

    // ------------------------------------------------------------------ carte du monde (CarteApi, HeraliZ Utils)
    /** Au plus une reponse de carte par joueur et par seconde (les demandes en trop sont ignorees, sans message). */
    private static final long CARTE_CADENCE_MS = 900;
    private final Map<UUID, Long> derniereCarte = new HashMap<>();

    /**
     * Le joueur a la carte du monde ouverte : il recoit ce qu'il a le droit de voir. Toutes les zones avec leur etat
     * public (celui du drapeau : libre, tenue, protegee, capture et clan qui capture, contestee), le ravitaillement des
     * zones de son clan, les membres de son clan connectes dans le meme monde (personne en match) et son clan. Hors du
     * monde des territoires : ni zones ni allies.
     */
    public static void carte(ServerPlayer p) {
        if (instance != null) instance.envoyerCarte(p, System.currentTimeMillis());
    }

    private void envoyerCarte(ServerPlayer p, long now) {
        Long der = derniereCarte.get(p.getUUID());
        if (der != null && now - der < CARTE_CADENCE_MS) return;
        derniereCarte.put(p.getUUID(), now);
        ClanStore store = ClanStore.get();
        Clan mien = store == null ? null : store.clanDe(p.getUUID());
        boolean ici = p.level() == monde() && !fr.heralisz.clans.api.ClansApi.enMatch(p);
        List<fr.heralisz.clans.api.CarteApi.Zone> vues = new ArrayList<>();
        if (ici && store != null) {
            for (Zone z : zones.values()) {
                EtatTerritoire e = data.etat(z.id);
                Clan O = clanProprio(store, e);
                EtatZone aff = etatsAffiches.getOrDefault(z.id, O == null ? EtatZone.NEUTRE : EtatZone.TENUE);
                EtatZone etat = aff == EtatZone.CAPTURE || aff == EtatZone.CONTESTE ? aff : O == null ? EtatZone.NEUTRE : EtatZone.TENUE;
                long protegee = O != null && e.protegeeJusque > now ? e.protegeeJusque - now : 0;
                if (etat == EtatZone.TENUE && protegee > 0) etat = EtatZone.PROTEGEE;
                Clan C = etat == EtatZone.CAPTURE && e.capteur != null ? store.get(e.capteur) : null;
                boolean membre = O != null && mien != null && mien.id.equals(O.id);
                boolean peut = membre && mien.peut(p.getUUID(), Perm.RAVITAILLEMENT) && z.drapeau != null && !ravitaillement.enCours(z.id);
                vues.add(new fr.heralisz.clans.api.CarteApi.Zone(z.id, z.nom, z.x, z.z, z.rayon, z.drapeau != null,
                        z.drapeau == null ? 0 : z.drapeau.getX(), z.drapeau == null ? 0 : z.drapeau.getZ(),
                        O == null ? "" : O.tag, O == null ? "" : O.nom, O == null ? COULEUR_NEUTRE : O.banniere.accent(), O == null ? null : O.banniere,
                        etat, C == null ? "" : C.tag, C == null ? "" : C.nom, C == null ? 0 : C.banniere.accent(), C == null ? null : C.banniere,
                        C == null ? 0f : e.progression, O == null ? 0 : Math.max(0, now - e.captureLe), protegee, z.valeur,
                        ClansConfig.LOOT_BONUS.get(), membre, membre ? Math.max(0, e.prochainRavitaillement - now) : -1, peut));
            }
        }
        List<fr.heralisz.clans.api.CarteApi.Allie> allies = new ArrayList<>();
        fr.heralisz.clans.api.CarteApi.MonClan monClan = null;
        if (mien != null) {
            int enLigne = 0;
            for (UUID m : mien.membres.keySet()) {
                ServerPlayer d = server.getPlayerList().getPlayer(m);
                if (d == null) continue;
                enLigne++;
                if (!ici || d == p || d.level() != p.level() || !d.isAlive() || d.isSpectator() || fr.heralisz.clans.api.ClansApi.enMatch(d)) continue;
                allies.add(new fr.heralisz.clans.api.CarteApi.Allie(d.getGameProfile().getName(), d.getX(), d.getZ()));
            }
            monClan = new fr.heralisz.clans.api.CarteApi.MonClan(mien.tag, mien.nom, mien.banniere.accent(), mien.banniere, enLigne,
                    compterTenus(mien.id));
        }
        ClansNet.toPlayer(p, new Packets.CarteS2C(vues, allies, monClan));
    }

    /** Le moteur du serveur en cours (null hors serveur). */
    @Nullable
    public static Territoires get() {
        return instance;
    }

    /** Le module ravitaillement (null hors serveur). */
    @Nullable
    public static Ravitaillement ravitaillement() {
        return instance == null ? null : instance.ravitaillement;
    }

    public ServerLevel monde() {
        return server.overworld();
    }

    // ------------------------------------------------------------------ API statique (autres modules)
    public static Collection<Zone> zones() {
        return instance == null ? List.of() : Collections.unmodifiableCollection(instance.zones.values());
    }

    public static Collection<String> ids() {
        return instance == null ? List.of() : instance.zones.keySet();
    }

    @Nullable
    public static Zone zone(String id) {
        return instance == null || id == null ? null : instance.zones.get(Zone.normaliserId(id));
    }

    /** Zones tenues par un clan (pour le snapshot). */
    public static List<ClanSnapshot.TerritoireVue> tenusPar(UUID clanId) {
        List<ClanSnapshot.TerritoireVue> l = new ArrayList<>();
        if (instance == null || clanId == null) return l;
        for (Zone z : instance.zones.values()) {
            EtatTerritoire e = instance.data.tous().get(z.id);
            if (e != null && clanId.equals(e.proprio)) l.add(new ClanSnapshot.TerritoireVue(z.id, z.nom, e.captureLe));
        }
        return l;
    }

    public static int compterTenus(UUID clanId) {
        if (instance == null || clanId == null) return 0;
        int n = 0;
        for (Zone z : instance.zones.values()) {
            EtatTerritoire e = instance.data.tous().get(z.id);
            if (e != null && clanId.equals(e.proprio)) n++;
        }
        return n;
    }

    /** Proprietaire d'une zone (null si libre ou zone inconnue). */
    @Nullable
    public static UUID proprietaire(String zoneId) {
        Zone z = zone(zoneId);
        if (z == null) return null;
        EtatTerritoire e = instance.data.tous().get(z.id);
        return e == null ? null : e.proprio;
    }

    /**
     * Demande de signal de ravitaillement (panneau du drapeau ou /clan) : zone connue avec drapeau, joueur membre
     * du clan proprietaire avec la perm RAVITAILLEMENT, delai ecoule, pas de largage en cours. Tout est verifie ici.
     */
    public static void demanderRavitaillement(ServerPlayer p, String zoneId) {
        if (instance == null) return;
        instance.ravitailler(p, zoneId);
    }

    /** Envoie la vue de la zone au joueur (ouvre le panneau du drapeau chez lui). */
    public static void ouvrirPanneau(ServerPlayer p, String zoneId) {
        if (instance == null) return;
        Zone z = zone(zoneId);
        if (z == null) {
            ServerHandlers.erreur(p, "zone.erreur.inconnue", zoneId);
            return;
        }
        ClansNet.toPlayer(p, new Packets.PanneauDrapeauS2C(instance.vue(z, p, System.currentTimeMillis())));
    }

    /** Libere toutes les zones d'un clan (dissolution, suppression admin) : evenement Perdu pour chacune. */
    public static void liberer(UUID clanId) {
        if (instance == null || clanId == null) return;
        for (Zone z : instance.zones.values()) {
            EtatTerritoire e = instance.data.tous().get(z.id);
            if (e == null) continue;
            boolean change = false;
            if (clanId.equals(e.proprio)) {
                e.liberer();
                change = true;
                MinecraftForge.EVENT_BUS.post(new TerritoireEvents.Perdu(clanId, z.id, z.nom));
            }
            if (clanId.equals(e.capteur)) {
                e.capteur = null;
                e.progression = 0;
                change = true;
            }
            if (change) {
                instance.data.setDirty();
                instance.rafraichirDrapeau(z, e);
            }
        }
    }

    // ------------------------------------------------------------------ cycle
    private void tick() {
        ticks++;
        ravitaillement.tick();
        assauts.tick(monde(), System.currentTimeMillis());
        if (ticks % PERIODE_TICKS != 0) return;
        ClanStore store = ClanStore.get();
        if (store == null) return;
        long now = System.currentTimeMillis();
        ServerLevel level = monde();
        for (List<ServerPlayer> l : presents.values()) l.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Zone z = localisable(p) ? zoneA(p.getX(), p.getY(), p.getZ()) : null;
            if (z == null) {
                sortie(p);
                continue;
            }
            presents.computeIfAbsent(z.id, k -> new ArrayList<>()).add(p);
        }
        for (Zone z : zones.values()) {
            List<ServerPlayer> l = presents.get(z.id);
            traiter(level, store, z, l == null ? List.of() : l, now);
        }
        if (ticks % MINUTE_TICKS == 0) tenueMinute();
    }

    /** Zone contenant le point (la plus proche de son centre si plusieurs se chevauchent). */
    @Nullable
    public Zone zoneA(double x, double y, double z) {
        Zone best = null;
        double bd = Double.MAX_VALUE;
        for (Zone zo : zones.values()) {
            if (!zo.contient(x, y, z)) continue;
            double d = zo.distance2(x, z);
            if (d < bd) {
                bd = d;
                best = zo;
            }
        }
        return best;
    }

    /**
     * Le joueur peut etre compte dans une zone : monde principal, vivant et a pied. Un passager de vehicule (train,
     * ascenseur, monture, chariot...) a pour position serveur celle de son vehicule, qui peut etre restee ailleurs
     * (train dont la descente a ete refusee, puis /tp : position figee, barre de zone affichee a 60 blocs) : il n'est
     * compte dans aucune zone (ni barre, ni capture, ni bonus de loot) tant qu'il n'est pas descendu.
     */
    public boolean localisable(ServerPlayer p) {
        return p.level() == monde() && p.isAlive() && !p.isPassenger();
    }

    /** Resultat de {@link #descendre} : le joueur etait passager, la descente a ete refusee, nom du vehicule. */
    public record Descente(boolean etaitPassager, boolean bloque, Component vehicule) {
        static final Descente A_PIED = new Descente(false, false, Component.empty());
    }

    /**
     * Fait descendre un passager avant une action qui depend de sa position (commandes /territoire, signal) : sa
     * position serveur est celle du vehicule, peut-etre figee loin de lui. Apres la descente, le serveur le pose a
     * cote du vehicule et y teleporte son client : la position redevient sure, l'action peut s'en servir. Certains
     * vehicules refusent la descente (train en marche, ascenseur en mouvement : EntityMountEvent annule) ; le joueur
     * reste alors passager ({@code bloque}) et l'action doit etre refusee.
     */
    public static Descente descendre(ServerPlayer p) {
        Entity v = p.getVehicle();
        if (v == null) return Descente.A_PIED;
        Component nom = v.getDisplayName();
        p.stopRiding();
        boolean bloque = p.isPassenger();
        HeraliszClans.LOG.info("[clans] territoires : {} etait passager de {} : {}", p.getGameProfile().getName(), v,
                bloque ? "descente refusee par le vehicule" : "descendu en " + p.blockPosition().toShortString());
        return new Descente(true, bloque, nom);
    }

    /** Le joueur n'est dans aucune zone : barre cachee, bonus retire. */
    private void sortie(ServerPlayer p) {
        if (dernierHud.remove(p.getUUID()) != null) {
            ClansNet.toPlayer(p, new Packets.TerritoireHudS2C("", "", 0, 0, EtatZone.NEUTRE, "", 0));
        }
        lootBonus.absent(p);
    }

    private static boolean eligible(ServerPlayer p) {
        GameType g = p.gameMode.getGameModeForPlayer();
        return p.isAlive() && (g == GameType.SURVIVAL || g == GameType.ADVENTURE);
    }

    /** Clan proprietaire valide (un clan disparu libere la zone). */
    @Nullable
    private Clan clanProprio(ClanStore store, EtatTerritoire e) {
        if (e.proprio == null) return null;
        Clan c = store.get(e.proprio);
        if (c == null) {
            e.liberer();
            data.setDirty();
        }
        return c;
    }

    private void traiter(ServerLevel level, ClanStore store, Zone zone, List<ServerPlayer> joueurs, long now) {
        EtatTerritoire e = data.etat(zone.id);
        Clan O = clanProprio(store, e);
        float progAvant = e.progression;
        // regroupement des presents : membres de O (bonus de loot), un clan capteur C (n membres), ou plusieurs
        int nO = 0, nC = 0;
        Clan C = null;
        boolean plusieurs = false;
        List<UUID> capteurs = null;
        for (ServerPlayer p : joueurs) {
            Clan c = store.clanDe(p.getUUID());
            boolean membreO = c != null && O != null && c.id.equals(O.id);
            if (membreO) lootBonus.present(p, now);
            else lootBonus.absent(p);
            if (c == null || !eligible(p) || !c.peut(p.getUUID(), Perm.CAPTURER)) continue;
            if (membreO) {
                nO++;
            } else if (C == null) {
                C = c;
                nC = 1;
                capteurs = new ArrayList<>(4);
                capteurs.add(p.getUUID());
            } else if (c.id.equals(C.id)) {
                nC++;
                capteurs.add(p.getUUID());
            } else {
                plusieurs = true;
            }
        }
        EtatZone etat;
        boolean dirty = false;
        if (C == null) {
            if (e.progression > 0) {
                e.progression = Math.max(0, e.progression - DECROISSANCE);
                if (e.progression == 0) e.capteur = null;
                dirty = true;
            }
            etat = O == null ? EtatZone.NEUTRE : EtatZone.TENUE;
        } else if (plusieurs || nO > 0) {
            etat = EtatZone.CONTESTE;
        } else if (e.protegeeJusque > now) {
            etat = EtatZone.PROTEGEE;
        } else if (compterTenus(C.id) >= Niveaux.territoiresMax(C.niveau)) {
            etat = EtatZone.LIMITE;
        } else {
            if (!C.id.equals(e.capteur)) {
                e.capteur = C.id;
                e.progression = 0;
                progAvant = 0;
            }
            float bonus = Math.min(2f, 1f + 0.25f * (nC - 1));
            int duree = O == null ? ClansConfig.DUREE_CAPTURE_NEUTRE_S.get() : ClansConfig.DUREE_CAPTURE_S.get();
            if (progAvant <= 0) son(level, zone, Sons.CAPTURE_DEBUT.get(), 1.5f);
            e.progression = Math.min(1f, e.progression + (PERIODE_TICKS / 20f) / Math.max(1, duree) * bonus);
            dirty = true;
            etat = EtatZone.CAPTURE;
            if (e.progression >= 1f) {
                capturer(level, zone, e, C, O, capteurs, now);
                assauts.prise(zone.id, level, joueurs, now);
                O = C;
                etat = EtatZone.PROTEGEE;
            }
        }
        if (dirty) data.setDirty();
        List<ServerPlayer> joueursCapteurs = new ArrayList<>();
        if (capteurs != null) for (ServerPlayer p : joueurs) if (capteurs.contains(p.getUUID())) joueursCapteurs.add(p);
        assauts.cycle(level, zone, e.capteur, e.progression, C, etat == EtatZone.CAPTURE, joueursCapteurs, joueurs, now);
        etatsAffiches.put(zone.id, etat);
        rafraichirDrapeau(level, zone, O, etat);
        // barre de capture des joueurs presents (4 Hz max : ce cycle fait 2 Hz ; envoi seulement si changement).
        // Le reste de protection est arrondi a la minute superieure (ce que le client affiche) : sinon chaque cycle
        // produirait un paquet different pendant les 10 min de protection.
        long protegeeMs = e.protegeeJusque > now ? (e.protegeeJusque - now + 59_999) / 60_000 * 60_000 : 0;
        String capteurTag = "";
        if (e.capteur != null) {
            Clan cc = store.get(e.capteur);
            if (cc != null) capteurTag = cc.tag;
        }
        for (ServerPlayer p : joueurs) {
            Clan mien = store.clanDe(p.getUUID());
            boolean membreO = O != null && mien != null && mien.id.equals(O.id);
            EtatZone ev = etat;
            float prog = e.progression;
            if (membreO) {
                if (etat == EtatZone.TENUE && e.progression > 0) {
                    ev = EtatZone.DEFENSE;
                    prog = 1 - e.progression;
                }
            } else if (etat == EtatZone.LIMITE && (mien == null || C == null || !mien.id.equals(C.id))) {
                ev = O == null ? EtatZone.NEUTRE : EtatZone.TENUE;
            }
            Packets.TerritoireHudS2C hud = new Packets.TerritoireHudS2C(zone.nom, O == null ? "" : O.tag,
                    O == null ? COULEUR_NEUTRE : O.banniere.accent(), prog, ev, capteurTag, protegeeMs);
            if (!hud.equals(dernierHud.get(p.getUUID()))) {
                dernierHud.put(p.getUUID(), hud);
                ClansNet.toPlayer(p, hud);
            }
        }
    }

    private void capturer(ServerLevel level, Zone zone, EtatTerritoire e, Clan C, @Nullable Clan O, @Nullable List<UUID> capteurs, long now) {
        UUID ancien = O == null ? null : O.id;
        e.proprio = C.id;
        e.captureLe = now;
        e.prochainRavitaillement = now + ClansConfig.RAVITAILLEMENT_MIN.get() * 60_000L;
        e.protegeeJusque = now + ClansConfig.PROTECTION_MIN.get() * 60_000L;
        e.progression = 0;
        e.capteur = null;
        data.setDirty();
        son(level, zone, Sons.CAPTURE_REUSSIE.get(), 2f);
        HeraliszClans.LOG.info("[clans] zone {} capturee par [{}]{}", zone.id, C.tag, O == null ? "" : " (perdue par [" + O.tag + "])");
        if (ancien != null) MinecraftForge.EVENT_BUS.post(new TerritoireEvents.Perdu(ancien, zone.id, zone.nom));
        MinecraftForge.EVENT_BUS.post(new TerritoireEvents.Capture(C.id, zone.id, zone.nom, ancien, capteurs == null ? List.of() : capteurs));
    }

    private void son(ServerLevel level, Zone zone, SoundEvent son, float volume) {
        if (zone.drapeau == null) return;
        level.playSound(null, zone.drapeau.getX() + 0.5, zone.drapeau.getY() + 1, zone.drapeau.getZ() + 0.5, son, SoundSource.PLAYERS, volume, 1f);
    }

    private void tenueMinute() {
        Map<UUID, List<Zone>> parClan = new HashMap<>();
        for (Zone z : zones.values()) {
            EtatTerritoire e = data.tous().get(z.id);
            if (e != null && e.proprio != null) parClan.computeIfAbsent(e.proprio, k -> new ArrayList<>(3)).add(z);
        }
        for (Map.Entry<UUID, List<Zone>> en : parClan.entrySet()) {
            List<Zone> l = en.getValue();
            int valeur = 0;
            List<String> ids = new ArrayList<>(l.size());
            for (Zone z : l) {
                valeur += z.valeur;
                ids.add(z.id);
            }
            MinecraftForge.EVENT_BUS.post(new TerritoireEvents.TenueMinute(en.getKey(), l.size(), valeur, ids));
        }
    }

    // ------------------------------------------------------------------ drapeaux
    /** Etat affiche par le drapeau : mi-mat seulement pendant une capture ou une contestation. */
    private static EtatZone etatDrapeau(@Nullable Clan O, EtatZone etat) {
        if (etat == EtatZone.CAPTURE || etat == EtatZone.CONTESTE) return etat;
        return O == null ? EtatZone.NEUTRE : EtatZone.TENUE;
    }

    /** Met a jour le bloc-entite du drapeau de la zone si son chunk est charge (synchronise seulement si ca change). */
    private void rafraichirDrapeau(ServerLevel level, Zone zone, @Nullable Clan O, EtatZone etat) {
        BlockPos pos = zone.drapeau;
        if (pos == null || !level.isLoaded(pos)) return;
        if (level.getBlockEntity(pos) instanceof DrapeauBlockEntity be) {
            if (be.appliquer(zone.id, zone.nom, O == null ? "" : O.tag, O == null ? null : O.banniere, etatDrapeau(O, etat))) be.synchroniser();
        }
    }

    private void rafraichirDrapeau(Zone zone, EtatTerritoire e) {
        ClanStore store = ClanStore.get();
        Clan O = store == null ? null : clanProprio(store, e);
        EtatZone etat = etatsAffiches.getOrDefault(zone.id, O == null ? EtatZone.NEUTRE : EtatZone.TENUE);
        rafraichirDrapeau(monde(), zone, O, etat);
    }

    /** Appele quand un bloc-entite de drapeau est charge cote serveur : lui donne l'etat courant de sa zone. */
    public void rafraichirDrapeau(DrapeauBlockEntity be) {
        Zone zone = zoneDuDrapeau(be.getBlockPos());
        if (zone == null) {
            if (be.appliquer(be.zoneId, be.zoneNom, "", null, EtatZone.NEUTRE)) be.synchroniser();
            return;
        }
        rafraichirDrapeau(zone, data.etat(zone.id));
    }

    @Nullable
    public Zone zoneDuDrapeau(BlockPos pos) {
        for (Zone z : zones.values()) if (pos.equals(z.drapeau)) return z;
        return null;
    }

    /**
     * Le bloc drapeau d'une zone a ete retire du monde (setblock, WorldEdit, chunk supprime : il ne casse pas
     * autrement) : la zone n'a plus de drapeau, la surface au centre sert de reference verticale, les ops en ligne
     * sont prevenus.
     */
    public void drapeauRetire(BlockPos pos) {
        Zone z = zoneDuDrapeau(pos);
        if (z == null) return;
        z.drapeau = null;
        z.calerHauteur(monde());
        sauvegarderZones();
        HeraliszClans.LOG.warn("[clans] drapeau de la zone {} retire en {} : a reposer avec /territoire drapeau {}", z.id, pos, z.id);
        Component msg = Component.translatable("zone.cmd.drapeau_retire", z.nom, pos.getX(), pos.getY(), pos.getZ(), z.id);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.hasPermissions(2)) p.sendSystemMessage(msg);
        }
    }

    // ------------------------------------------------------------------ panneau et ravitaillement
    /** Vue de la zone pour le panneau du drapeau d'un joueur. */
    public ZoneVue vue(Zone zone, ServerPlayer p, long now) {
        ClanStore store = ClanStore.get();
        EtatTerritoire e = data.etat(zone.id);
        Clan O = store == null ? null : clanProprio(store, e);
        Clan mien = store == null ? null : store.clanDe(p.getUUID());
        boolean membre = O != null && mien != null && mien.id.equals(O.id);
        boolean peutLancer = membre && mien.peut(p.getUUID(), Perm.RAVITAILLEMENT) && zone.drapeau != null && !ravitaillement.enCours(zone.id);
        long ravDans = O == null ? 0 : Math.max(0, e.prochainRavitaillement - now);
        EtatZone etat = etatsAffiches.getOrDefault(zone.id, O == null ? EtatZone.NEUTRE : EtatZone.TENUE);
        return new ZoneVue(zone.id, zone.nom, O == null ? "" : O.tag, O == null ? "" : O.nom, O == null ? null : O.banniere,
                O == null ? 0 : Math.max(0, now - e.captureLe), ClansConfig.LOOT_BONUS.get(), membre, peutLancer, ravDans, etat);
    }

    /** Portee (au carre) entre le joueur et le drapeau pour lancer le signal : la portee du clic droit sur le socle. */
    private static final double PORTEE_SIGNAL2 = 8 * 8;

    private void ravitailler(ServerPlayer p, String zoneId) {
        Zone zone = zone(zoneId);
        if (zone == null) {
            ServerHandlers.erreur(p, "zone.erreur.inconnue", String.valueOf(zoneId));
            return;
        }
        if (zone.drapeau == null) {
            ServerHandlers.erreur(p, "zone.erreur.sans_drapeau", zone.nom);
            return;
        }
        // le signal part du panneau du drapeau : le joueur doit etre la, en jeu (pas de paquet envoye de loin)
        if (!eligible(p)) {
            ServerHandlers.erreur(p, "zone.erreur.mode_jeu");
            return;
        }
        ClanStore store = ClanStore.get();
        if (store == null) return;
        long now = System.currentTimeMillis();
        EtatTerritoire e = data.etat(zone.id);
        Clan O = clanProprio(store, e);
        Clan mien = store.clanDe(p.getUUID());
        if (mien == null) {
            ServerHandlers.erreur(p, "zone.erreur.sans_clan");
            return;
        }
        if (O == null || !O.id.equals(mien.id)) {
            ServerHandlers.erreur(p, "zone.erreur.pas_proprietaire", zone.nom);
            return;
        }
        if (!mien.peut(p.getUUID(), Perm.RAVITAILLEMENT)) {
            ServerHandlers.erreur(p, "zone.erreur.perm_ravitaillement");
            return;
        }
        if (ravitaillement.enCours(zone.id)) {
            ServerHandlers.erreur(p, "zone.erreur.en_cours", zone.nom);
            return;
        }
        if (e.prochainRavitaillement > now) {
            ServerHandlers.erreur(p, "zone.erreur.cooldown", duree(e.prochainRavitaillement - now));
            return;
        }
        // position en dernier (on ne fait descendre personne pour rien) : un passager a pour position serveur celle
        // de son vehicule, peut-etre figee ailleurs ; on le fait descendre, puis il doit etre dans la zone et au drapeau
        if (p.isPassenger() && descendre(p).bloque()) {
            ServerHandlers.erreur(p, "zone.erreur.vehicule", p.getVehicle() == null ? "" : p.getVehicle().getDisplayName());
            return;
        }
        if (p.level() != monde() || !zone.contient(p.getX(), p.getY(), p.getZ()) || p.distanceToSqr(zone.drapeau.getCenter()) > PORTEE_SIGNAL2) {
            ServerHandlers.erreur(p, "zone.erreur.trop_loin", zone.nom);
            return;
        }
        e.prochainRavitaillement = now + ClansConfig.RAVITAILLEMENT_MIN.get() * 60_000L;
        data.setDirty();
        ravitaillement.lancer(monde(), zone, mien.tag, Ravitaillement.DELAI_AVION_TICKS, true);
        ServerHandlers.info(p, "ravitaillement.lance", zone.nom, Ravitaillement.DELAI_AVION_TICKS / 20);
        MinecraftForge.EVENT_BUS.post(new TerritoireEvents.Ravitaillement(mien.id, zone.id, zone.nom, p.getUUID()));
        ClansNet.toPlayer(p, new Packets.PanneauDrapeauS2C(vue(zone, p, now)));
    }

    /** « 1 h 03 », « 12 min », « 45 s » (traduit chez le joueur ; memes cles que ClanService.duree). */
    public static net.minecraft.network.chat.MutableComponent duree(long ms) {
        long s = Math.max(0, ms) / 1000;
        if (s >= 3600) {
            return Component.translatableWithFallback("heralisz_clans.duree.heures", "%s h %s", String.valueOf(s / 3600),
                    String.format(java.util.Locale.ROOT, "%02d", (s % 3600) / 60));
        }
        if (s >= 60) return Component.translatableWithFallback("heralisz_clans.duree.minutes", "%s min", String.valueOf(s / 60));
        return Component.translatableWithFallback("heralisz_clans.duree.secondes", "%s s", String.valueOf(s));
    }

    // ------------------------------------------------------------------ administration (/territoire)
    public void sauvegarderZones() {
        if (zonesIllisibles) {
            HeraliszClans.LOG.error("[clans] zones.json non reecrit : le fichier etait illisible au chargement (corrigez-le puis /territoire recharger)");
            return;
        }
        Zones.ecrire(fichierZones, zones.values());
    }

    /** Cree une zone (id libre et valide, nom valide) ; retourne null si l'id est pris ou invalide. */
    @Nullable
    public Zone creer(String id, int rayon, String nom, int x, int z) {
        id = Zone.normaliserId(id);
        if (!Zone.idValide(id) || zones.containsKey(id) || !Zone.nomValide(nom)) return null;
        Zone zo = new Zone(id, nom, x, z, rayon, null);
        zo.calerHauteur(monde());
        zones.put(id, zo);
        data.etat(id);
        sauvegarderZones();
        return zo;
    }

    /** Supprime une zone, son drapeau dans le monde et son etat ; le proprietaire la perd. */
    public void supprimer(Zone zone) {
        EtatTerritoire e = data.tous().get(zone.id);
        if (e != null && e.proprio != null) MinecraftForge.EVENT_BUS.post(new TerritoireEvents.Perdu(e.proprio, zone.id, zone.nom));
        retirerBlocDrapeau(zone);
        zones.remove(zone.id);
        data.retirer(zone.id);
        etatsAffiches.remove(zone.id);
        sauvegarderZones();
    }

    private void retirerBlocDrapeau(Zone zone) {
        BlockPos pos = zone.drapeau;
        if (pos == null) return;
        ServerLevel level = monde();
        if (level.getBlockState(pos).is(ClansRegistry.DRAPEAU.get())) {
            zone.drapeau = null; // avant removeBlock : onRemove ne doit pas re-sauvegarder
            level.removeBlock(pos, false);
        }
        zone.drapeau = null;
    }

    /** Resultat d'une pose de drapeau : centre avant la pose, le centre a-t-il suivi le drapeau, la zone en avait-elle un. */
    public record Pose(int ancienX, int ancienZ, boolean centreSuit, boolean avaitDrapeau) {
        /** Le centre a reellement bouge. */
        public boolean centreDeplace(Zone zone) {
            return centreSuit && (ancienX != zone.x || ancienZ != zone.z);
        }
    }

    /**
     * Pose le bloc drapeau de la zone en pos (l'ancien est retire) et l'enregistre dans zones.json. Le centre de la
     * zone suit le drapeau (la zone = le cercle autour de son drapeau), sauf si zones.json le fixe a la main
     * ({@code centreFixe}) : il est alors garde et la commande dit l'ecart.
     */
    public Pose poserDrapeau(Zone zone, BlockPos pos, Direction facing) {
        ServerLevel level = monde();
        int ax = zone.x, az = zone.z;
        boolean avait = zone.drapeau != null;
        boolean suit = !zone.centreFixe;
        if (zone.drapeau != null && !zone.drapeau.equals(pos)) retirerBlocDrapeau(zone);
        if (suit) {
            zone.x = pos.getX();
            zone.z = pos.getZ();
        }
        BlockState etat = ClansRegistry.DRAPEAU.get().defaultBlockState().setValue(DrapeauBlock.FACING, facing);
        zone.drapeau = pos; // avant setBlock : onLoad du bloc-entite retrouve sa zone
        level.setBlock(pos, etat, 3);
        sauvegarderZones();
        rafraichirDrapeau(zone, data.etat(zone.id));
        HeraliszClans.LOG.info("[clans] drapeau de la zone {} pose en {} ; centre {}, {}{} ; rayon {}", zone.id, pos.toShortString(),
                zone.x, zone.z, ax != zone.x || az != zone.z ? " (avant " + ax + ", " + az + ")" : "", zone.rayon);
        return new Pose(ax, az, suit, avait);
    }

    /**
     * Deplace le centre de la zone en (x, z), et change son rayon si {@code rayon} &gt; 0 (reposer une zone de l'ancienne
     * carte sur la nouvelle). L'etat de jeu (proprietaire, protection, statistiques) est garde. Un drapeau qui se
     * retrouve hors du nouveau cercle est retire du monde (a reposer avec /territoire drapeau) ; rend vrai dans ce cas.
     */
    public boolean deplacer(Zone zone, int x, int z, int rayon) {
        int ax = zone.x, az = zone.z;
        zone.x = x;
        zone.z = z;
        if (rayon > 0) zone.rayon = Math.max(5, rayon);
        boolean retire = false;
        if (zone.drapeau != null && !zone.contientHorizontal(zone.drapeau.getX() + 0.5, zone.drapeau.getZ() + 0.5)) {
            retirerBlocDrapeau(zone);
            retire = true;
        }
        zone.calerHauteur(monde());
        sauvegarderZones();
        if (zone.drapeau != null) rafraichirDrapeau(zone, data.etat(zone.id));
        HeraliszClans.LOG.info("[clans] zone {} deplacee : centre {}, {} (avant {}, {}), rayon {}{}", zone.id, zone.x, zone.z, ax, az, zone.rayon,
                retire ? ", drapeau retire" : "");
        return retire;
    }

    /** Remet la zone libre (le proprietaire la perd). */
    public void reset(Zone zone) {
        EtatTerritoire e = data.etat(zone.id);
        UUID ancien = e.proprio;
        e.liberer();
        data.setDirty();
        etatsAffiches.put(zone.id, EtatZone.NEUTRE);
        if (ancien != null) MinecraftForge.EVENT_BUS.post(new TerritoireEvents.Perdu(ancien, zone.id, zone.nom));
        rafraichirDrapeau(zone, e);
    }

    /** Capture forcee par un op (evenements normaux, sans capteurs) ; faux si le clan tient deja la zone. */
    public boolean capturerAdmin(Zone zone, Clan clan) {
        ClanStore store = ClanStore.get();
        EtatTerritoire e = data.etat(zone.id);
        Clan O = store == null ? null : clanProprio(store, e);
        if (O != null && O.id.equals(clan.id)) return false;
        capturer(monde(), zone, e, clan, O, List.of(), System.currentTimeMillis());
        etatsAffiches.put(zone.id, EtatZone.PROTEGEE);
        rafraichirDrapeau(zone, e);
        return true;
    }

    /** Largage immediat (test) : fusee + fumee + avion sans attente, sans toucher au delai de la zone. */
    public boolean ravitaillerAdmin(Zone zone) {
        if (zone.drapeau == null) return false;
        ClanStore store = ClanStore.get();
        EtatTerritoire e = data.etat(zone.id);
        Clan O = store == null ? null : clanProprio(store, e);
        ravitaillement.lancer(monde(), zone, O == null ? "" : O.tag, 0, false);
        return true;
    }

    /**
     * Relit zones.json (les etats sont gardes) et la table de butin. Si le fichier est illisible, les zones en memoire
     * sont gardees et -1 est renvoye (le mode lecture seule reste ou s'installe).
     */
    public int recharger() {
        List<Zone> lues = Zones.charger(fichierZones);
        TableButin.recharger();
        Assauts.charger();
        if (lues == null) {
            zonesIllisibles = true;
            return -1;
        }
        zonesIllisibles = false;
        zones.clear();
        for (Zone z : lues) zones.put(z.id, z);
        for (Zone z : zones.values()) {
            z.calerHauteur(monde());
            rafraichirDrapeau(z, data.etat(z.id));
        }
        return zones.size();
    }

    /** Etat persistant d'une zone (lecture pour /territoire liste). */
    public EtatTerritoire etat(Zone zone) {
        return data.etat(zone.id);
    }

    public EtatZone etatAffiche(Zone zone) {
        return etatsAffiches.getOrDefault(zone.id, EtatZone.NEUTRE);
    }
}
