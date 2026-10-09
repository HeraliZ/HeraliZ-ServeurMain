package fr.heralisz.clans.server;

import fr.heralisz.clans.ClansConfig;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.api.ClansApi;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.Evenement;
import fr.heralisz.clans.model.LienJoueur;
import fr.heralisz.clans.model.Membre;
import fr.heralisz.clans.model.QueteEtat;
import fr.heralisz.clans.network.ClansNet;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.territory.api.TerritoireEvents;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Evenements serveur des clans :
 * <ul>
 * <li>connexion/deconnexion : dernier nom, vuLe, snapshot + invitations + tags ;</li>
 * <li>stats : kills (joueurs avec anti-boost : pas de camarade, pas de victime fraichement reapparue, pas le meme
 * couple avant 10 min / rodeurs MONSTER hors boss / boss des 8 mods de boss, liste BOSS), morts, degats reels
 * ({@link LivingDamageEvent}, apres armure, bornes aux PV restants), temps de jeu (+20 ticks/s), loot ouvert (clic droit
 * sur un bloc {@code cfm:loot_*}, une fois par bloc et par joueur toutes les 5 min), distance parcourue (quete) ;</li>
 * <li>liste Tab : prefixe « [TAG] » colore ; chat : mode /cc ;</li>
 * <li>tick : synchronisation groupee, horaires des quetes, purge des invitations ;</li>
 * <li>territoires : XP, stats, quetes, journal et annonces pour Capture / Perdu / Ravitaillement / TenueMinute.</li>
 * </ul>
 * Un joueur en match (drapeau {@link ClansApi#EN_MATCH} dans persistentData, pose par HeraliZ Matchs) est ignore par
 * les stats et les quetes : morts, kills (tueur ou victime en match), degats, loot, temps de jeu, distance.
 * Tout est sur le fil serveur. Rien n'est alloue par tick hors des changements reels.
 */
public final class ClanEvents {
    private ClanEvents() {
    }

    /** Les boss des 8 mods de boss (les sbires, projectiles et parties de ces mods ne comptent pas). */
    private static final Set<String> BOSS = Set.of(
            "heralisz_knight:burned_king", "heralisz_kraken:kraken", "heralisz_mammoth:mammoth", "heralisz_mere:ruin_mother",
            "heralisz_n07:sujet_n07", "heralisz_plague:plague_bearer", "heralisz_sylvorn:sylvorn", "heralisz_vulkhar:vulkhar");
    private static final String PREFIXE_LOOT = "cfm:loot_";
    /** Au-dela (m/s), le deplacement est ignore (teleportation, vehicule rapide). */
    private static final double DISTANCE_MAX_PAR_S = 25;

    /** Dernier clic compte par joueur : pos.asLong() -> ms. */
    private static final Map<UUID, Long2LongOpenHashMap> LOOT = new HashMap<>();
    /** Derniere position connue (x, z, dimension) pour la distance. */
    private static final Map<UUID, double[]> POS = new HashMap<>();
    /** Metres accumules par clan, pas encore comptes dans la quete. */
    private static final Map<UUID, Double> DISTANCE_RESTE = new HashMap<>();
    /** Anti-boost : tueur -> (victime -> ms du dernier kill compte). */
    private static final Map<UUID, Map<UUID, Long>> KILLS_RECENTS = new HashMap<>();
    /** Anti-boost : joueur -> ms de sa derniere reapparition (ou connexion). */
    private static final Map<UUID, Long> REAPPARU = new HashMap<>();
    private static int ticks;
    /** Vrai pendant l'arret : les deconnexions qui suivent ne font plus de synchronisation (le store note juste « vu le »). */
    private static boolean arret;

    // ------------------------------------------------------------------ cycle de vie
    @SubscribeEvent
    public static void onServerStart(ServerAboutToStartEvent e) {
        Quetes.charger();
        viderTables();
        ticks = 0;
        arret = false;
    }

    /** Arret : le store a deja note les « vu le » et sauvegarde (ClanStore est enregistre avant sur le bus). */
    @SubscribeEvent
    public static void onServerStop(ServerStoppingEvent e) {
        arret = true;
        Sync.vider();
        ClanService.vider();
        viderTables();
    }

    private static void viderTables() {
        LOOT.clear();
        POS.clear();
        DISTANCE_RESTE.clear();
        KILLS_RECENTS.clear();
        REAPPARU.clear();
    }

    // ------------------------------------------------------------------ connexion
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ClanStore store = ClanStore.get();
        if (store == null) return;
        String nom = p.getGameProfile().getName();
        long now = System.currentTimeMillis();
        REAPPARU.put(p.getUUID(), now); // un joueur tue juste apres sa connexion ne compte pas
        LienJoueur l = store.lien(p.getUUID());
        if (!nom.equals(l.dernierNom)) {
            l.dernierNom = nom;
            store.marquerDirtyJoueurs();
        }
        Clan c = store.clanDe(p.getUUID());
        if (c != null) {
            Membre m = c.membre(p.getUUID());
            if (m != null) {
                m.dernierNom = nom;
                m.vuLe = now;
                store.marquerDirty();
            }
            Sync.marquer(c); // les autres voient le point vert
        }
        Sync.envoyerEtat(p);
        if (c != null) Sync.diffuserTags(c, null);
        else ClansNet.toPlayer(p, new Packets.TagsS2C(Sync.tableTags(null)));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        UUID u = p.getUUID();
        ServerHandlers.oublier(u);
        ClanService.oublier(u);
        Sync.ecranFerme(u);
        LOOT.remove(u);
        POS.remove(u);
        KILLS_RECENTS.remove(u);
        REAPPARU.remove(u);
        ClanStore store = ClanStore.get();
        if (store == null) return;
        Clan c = store.clanDe(u);
        if (c == null) return;
        Membre m = c.membre(u);
        if (m != null) {
            m.vuLe = System.currentTimeMillis();
            store.marquerDirty();
        }
        if (arret) return; // tout le monde part : pas n² tables de tags
        Sync.marquer(c);
        // la liste des joueurs contient encore le partant : on l'exclut explicitement
        Sync.diffuserTags(null, u);
    }

    // ------------------------------------------------------------------ liste Tab et chat
    @SubscribeEvent
    public static void onTabName(PlayerEvent.TabListNameFormat e) {
        ClanStore store = ClanStore.get();
        if (store == null) return;
        Clan c = store.clanDe(e.getEntity().getUUID());
        if (c == null) return;
        e.setDisplayName(ClanService.prefixe(c).append(e.getEntity().getDisplayName()));
    }

    /**
     * Mode bascule : priorite LOWEST pour laisser les autres ecouteurs (mute, filtres) annuler avant nous ; un
     * evenement deja annule ne nous parvient pas. Le message passe par {@link ClanService#chat} (cadence, mute).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChat(ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        if (!ClanService.enModeChat(p)) return;
        e.setCanceled(true);
        ClanService.chat(p, e.getRawText());
    }

    // ------------------------------------------------------------------ stats
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) REAPPARU.put(p.getUUID(), System.currentTimeMillis());
    }

    /**
     * Anti-boost d'un kill joueur : pas de camarade de clan, pas de victime reapparue (ou connectee) il y a moins de
     * {@code anti_boost_reapparition_s}, pas le meme couple tueur/victime avant {@code anti_boost_couple_min}. Un kill
     * accepte est memorise. S'applique aux stats, a l'XP et aux quetes KILL_JOUEURS.
     */
    private static boolean killJoueurValide(Clan c, ServerPlayer tueur, Player victime, long now) {
        if (c.estMembre(victime.getUUID())) return false;
        Long reapparu = REAPPARU.get(victime.getUUID());
        if (reapparu != null && now - reapparu < ClansConfig.BOOST_REAPPARITION_S.get() * 1000L) return false;
        long delai = ClansConfig.BOOST_COUPLE_MIN.get() * 60_000L;
        Map<UUID, Long> table = KILLS_RECENTS.computeIfAbsent(tueur.getUUID(), k -> new HashMap<>());
        Long dernier = table.get(victime.getUUID());
        if (dernier != null && now - dernier < delai) return false;
        if (table.size() > 256) table.values().removeIf(t -> now - t >= delai);
        table.put(victime.getUUID(), now);
        return true;
    }

    /**
     * Morts sans auteur qui suivent une mise a terre ou une fuite : le kill revient au joueur credite par le jeu
     * ({@code getKillCredit}). Reanimation (mod de l'utilisateur) : saignement, abandon, deconnexion a terre ; il pose
     * lui-meme le credit ({@code setLastHurtByPlayer}) sur celui qui a mis a terre juste avant de tuer. HeraliZ Matchs :
     * deconnexion en combat (le message de mort nomme deja ce joueur). L'execution ({@code reanimation:execution}) a un
     * auteur : le bourreau.
     */
    static final Set<String> MORTS_SANS_AUTEUR_CREDITEES = Set.of("reanimation:bleed_out", "reanimation:give_up",
            "reanimation:disconnect", "heralisz_matchs:deconnexion_combat");

    /** Tueur a compter : l'auteur de la source, sinon le joueur credite pour les morts de {@link #MORTS_SANS_AUTEUR_CREDITEES}. */
    static Entity tueur(LivingDeathEvent e) {
        Entity src = e.getSource().getEntity();
        if (src != null) return src;
        String type = e.getSource().typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("");
        if (!MORTS_SANS_AUTEUR_CREDITEES.contains(type)) return null;
        LivingEntity credit = e.getEntity().getKillCredit();
        return credit instanceof Player ? credit : null;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        ClanStore store = ClanStore.get();
        if (store == null) return;
        LivingEntity victime = e.getEntity();
        if (victime.level().isClientSide()) return;
        Entity src = tueur(e);
        // match (HeraliZ Matchs) : ni la mort ni le kill ne comptent
        if (ClansApi.enMatch(victime) || ClansApi.enMatch(src)) return;
        if (victime instanceof ServerPlayer mort) {
            Clan c = store.clanDe(mort.getUUID());
            Membre m = c == null ? null : c.membre(mort.getUUID());
            if (m != null) {
                m.stats.morts++;
                store.marquerDirty();
                Sync.marquerLent(c);
            }
        }
        if (!(src instanceof ServerPlayer tueur) || tueur == victime) return;
        Clan c = store.clanDe(tueur.getUUID());
        Membre m = c == null ? null : c.membre(tueur.getUUID());
        if (m == null) return;
        if (victime instanceof Player joueur) {
            if (!killJoueurValide(c, tueur, joueur, System.currentTimeMillis())) return;
            m.stats.kills++;
            ClanService.ajouterXp(c, ClansConfig.XP_KILL_JOUEUR.get(), tueur.getUUID());
            Quetes.progresser(c, QueteEtat.Type.KILL_JOUEURS, 1);
        } else if (estBoss(victime)) {
            m.stats.boss++;
            ClanService.ajouterXp(c, ClansConfig.XP_BOSS.get(), tueur.getUUID());
            Quetes.progresser(c, QueteEtat.Type.KILL_BOSS, 1);
        } else if (victime.getType().getCategory() == MobCategory.MONSTER) {
            m.stats.zombies++;
            ClanService.ajouterXp(c, ClansConfig.XP_KILL_ZOMBIE.get(), tueur.getUUID());
            Quetes.progresser(c, QueteEtat.Type.KILL_ZOMBIES, 1);
        } else {
            return;
        }
        store.marquerDirty();
        Sync.marquerLent(c);
    }

    private static boolean estBoss(LivingEntity v) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(v.getType());
        return id != null && BOSS.contains(id.toString());
    }

    /** Degats reels : apres armure/enchantements, bornes aux PV + absorption de la victime ; pas sur un camarade. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent e) {
        Entity src = e.getSource().getEntity();
        if (!(src instanceof ServerPlayer attaquant)) return;
        LivingEntity victime = e.getEntity();
        if (victime == attaquant || e.getAmount() <= 0) return;
        if (ClansApi.enMatch(attaquant) || ClansApi.enMatch(victime)) return;
        ClanStore store = ClanStore.get();
        if (store == null) return;
        Clan c = store.clanDe(attaquant.getUUID());
        Membre m = c == null ? null : c.membre(attaquant.getUUID());
        if (m == null) return;
        if (victime instanceof Player && c.estMembre(victime.getUUID())) return;
        double reel = Math.min(e.getAmount(), victime.getHealth() + victime.getAbsorptionAmount());
        if (reel <= 0) return;
        m.stats.degats += reel;
        store.marquerDirty();
        Quetes.progresser(c, QueteEtat.Type.DEGATS, (long) Math.floor(reel));
        Sync.marquerLent(c);
    }

    /** Loot ouvert : clic droit (main principale) sur un bloc cfm:loot_* ; une fois par bloc et par joueur toutes les 5 min. */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(e.getEntity() instanceof ServerPlayer p) || ClansApi.enMatch(p)) return;
        ClanStore store = ClanStore.get();
        if (store == null) return;
        BlockState etat = e.getLevel().getBlockState(e.getPos());
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(etat.getBlock());
        if (id == null || !id.toString().startsWith(PREFIXE_LOOT)) return;
        Clan c = store.clanDe(p.getUUID());
        Membre m = c == null ? null : c.membre(p.getUUID());
        if (m == null) return;
        long now = System.currentTimeMillis();
        long delai = ClansConfig.LOOT_DELAI_MIN.get() * 60_000L;
        Long2LongOpenHashMap table = LOOT.computeIfAbsent(p.getUUID(), k -> new Long2LongOpenHashMap());
        long cle = e.getPos().asLong();
        long dernier = table.getOrDefault(cle, 0L);
        if (dernier > 0 && now - dernier < delai) return;
        if (table.size() > 1024) table.values().removeIf(t -> now - t >= delai);
        table.put(cle, now);
        m.stats.lootOuverts++;
        store.marquerDirty();
        Quetes.progresser(c, QueteEtat.Type.LOOT_OUVERTS, 1);
        Sync.marquerLent(c);
    }

    // ------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        ClanStore store = ClanStore.get();
        if (store == null) return;
        ticks++;
        Sync.tick();
        if (ticks % 20 == 0) seconde(store, e.getServer());
        if (ticks % 400 == 0) vingtSecondes(store, e.getServer());
    }

    /**
     * Chaque seconde : temps de jeu (+20 ticks) et distance des membres en ligne ; chaque minute, « vu le » des membres
     * connectes (sans marquer sale : une mort brutale du serveur perd au plus une minute).
     */
    private static void seconde(ClanStore store, MinecraftServer server) {
        if (ticks % 1200 == 0) store.noterVus(server, System.currentTimeMillis());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (ClansApi.enMatch(p)) continue; // en match : ni temps de jeu ni distance (le saut au retour est ignore)
            Clan c = store.clanDe(p.getUUID());
            Membre m = c == null ? null : c.membre(p.getUUID());
            if (m == null) continue;
            long avant = m.stats.tempsJeuTicks;
            m.stats.tempsJeuTicks += 20;
            if (avant / 1200 != m.stats.tempsJeuTicks / 1200) {
                Quetes.progresser(c, QueteEtat.Type.TEMPS_JEU_MIN, 1);
                Sync.marquerLent(c);
                store.marquerDirty();
            }
            // distance horizontale (meme dimension, bornee : teleportation ignoree)
            double dim = p.level().dimension().location().hashCode();
            double[] prev = POS.get(p.getUUID());
            double x = p.getX(), z = p.getZ();
            if (prev != null && prev[2] == dim && p.isAlive()) {
                double d = Math.hypot(x - prev[0], z - prev[1]);
                if (d > 0.05 && d <= DISTANCE_MAX_PAR_S) {
                    double reste = DISTANCE_RESTE.getOrDefault(c.id, 0.0) + d;
                    long entier = (long) Math.floor(reste);
                    if (entier > 0) {
                        Quetes.progresser(c, QueteEtat.Type.DISTANCE_M, entier);
                        reste -= entier;
                    }
                    DISTANCE_RESTE.put(c.id, reste);
                }
            }
            if (prev == null) {
                prev = new double[3];
                POS.put(p.getUUID(), prev);
            }
            prev[0] = x;
            prev[1] = z;
            prev[2] = dim;
        }
    }

    /** Toutes les 20 s : horaires des quetes, purge des invitations expirees. */
    private static void vingtSecondes(ClanStore store, MinecraftServer server) {
        long now = System.currentTimeMillis();
        Set<UUID> invitesTouches = null;
        for (Clan c : store.tous()) {
            if (Quetes.verifier(c, now)) {
                store.marquerDirty();
                Sync.marquer(c);
            }
            if (!c.invitations.isEmpty()) {
                List<UUID> expires = new ArrayList<>(2);
                c.invitations.removeIf(i -> {
                    if (!i.expiree(now)) return false;
                    expires.add(i.invite());
                    return true;
                });
                if (!expires.isEmpty()) {
                    store.marquerDirty();
                    if (invitesTouches == null) invitesTouches = new HashSet<>();
                    invitesTouches.addAll(expires);
                }
            }
        }
        if (invitesTouches != null) {
            for (UUID u : invitesTouches) {
                ServerPlayer p = server.getPlayerList().getPlayer(u);
                if (p != null) Sync.envoyerInvitations(p);
            }
        }
        DISTANCE_RESTE.keySet().removeIf(id -> store.get(id) == null);
        ClanService.purger(now);
    }

    // ------------------------------------------------------------------ territoires
    @SubscribeEvent
    public static void onCapture(TerritoireEvents.Capture e) {
        ClanStore store = ClanStore.get();
        Clan c = store == null ? null : store.get(e.clanId);
        if (c == null) return;
        long now = System.currentTimeMillis();
        int xp = ClansConfig.XP_CAPTURE.get();
        int n = 0;
        for (UUID u : e.capteurs) {
            Membre m = c.membre(u);
            if (m != null) {
                m.stats.captures++;
                n++;
            }
        }
        // l'XP va au clan une fois ; la part de chaque capteur est comptee dans ses stats
        if (n > 0) {
            long part = xp / n;
            for (UUID u : e.capteurs) {
                Membre m = c.membre(u);
                if (m != null) m.stats.xpApporte += part;
            }
        }
        ClanService.ajouterXp(c, xp, null);
        Quetes.progresser(c, QueteEtat.Type.CAPTURES, 1);
        c.statZone(e.zoneId).captures++;
        c.ajouterEvenement(new Evenement(now, Evenement.Genre.CAPTURE, e.nomZone));
        store.marquerDirty();
        Sync.marquer(c);
        // annonce a tout le serveur : chat + toast + son
        annoncerATous(ClanService.prefixe(c).append(Component.translatable("msg.capture", e.nomZone, c.tag)),
                new Packets.NoticeS2C(Packets.NoticeS2C.Genre.CAPTURE, List.of(e.nomZone, c.tag), "capture_reussie", c.banniere));
    }

    @SubscribeEvent
    public static void onPerdu(TerritoireEvents.Perdu e) {
        ClanStore store = ClanStore.get();
        Clan c = store == null ? null : store.get(e.clanId);
        if (c == null) return;
        c.ajouterEvenement(new Evenement(System.currentTimeMillis(), Evenement.Genre.ZONE_PERDUE, e.nomZone));
        store.marquerDirty();
        Sync.marquer(c);
        ClanService.diffuserChat(c, "msg.zone_perdue", e.nomZone);
        ClanService.diffuser(c, new Packets.NoticeS2C(Packets.NoticeS2C.Genre.ZONE_PERDUE, List.of(e.nomZone), "zone_perdue", null));
    }

    @SubscribeEvent
    public static void onRavitaillement(TerritoireEvents.Ravitaillement e) {
        ClanStore store = ClanStore.get();
        Clan c = store == null ? null : store.get(e.clanId);
        if (c == null) return;
        Membre m = c.membre(e.joueur);
        if (m != null) m.stats.ravitaillements++;
        ClanService.ajouterXp(c, ClansConfig.XP_RAVITAILLEMENT.get(), e.joueur);
        Quetes.progresser(c, QueteEtat.Type.RAVITAILLEMENTS, 1);
        c.ajouterEvenement(new Evenement(System.currentTimeMillis(), Evenement.Genre.RAVITAILLEMENT, e.nomZone));
        store.marquerDirty();
        Sync.marquer(c);
        annoncerATous(ClanService.prefixe(c).append(Component.translatable("msg.ravitaillement", e.nomZone, c.tag)),
                new Packets.NoticeS2C(Packets.NoticeS2C.Genre.RAVITAILLEMENT, List.of(e.nomZone, c.tag), "signal_fusee", c.banniere));
    }

    /** Chat + toast (+ son) a tous les joueurs du serveur. */
    private static void annoncerATous(Component chat, Packets.NoticeS2C notice) {
        MinecraftServer server = Sync.serveur();
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(chat);
        ClansNet.toAll(notice);
    }

    /**
     * Chaque minute, pour un clan qui tient des zones : quete TENIR_ZONE_MIN, minutes tenues par zone, points de
     * territoire, revenu (ClansConfig.REVENU_HEURE herals par heure et par zone, verses a la banque a chaque heure-zone
     * complete) et XP (ClansConfig.XP_HEURE_VALEUR par heure et par point de valeur : une ville riche en loot rapporte
     * plus). Les restes sont gardes dans le clan : rien n'est perdu a un redemarrage.
     */
    @SubscribeEvent
    public static void onTenueMinute(TerritoireEvents.TenueMinute e) {
        ClanStore store = ClanStore.get();
        Clan c = store == null ? null : store.get(e.clanId);
        if (c == null || e.zonesTenues <= 0) return;
        Quetes.progresser(c, QueteEtat.Type.TENIR_ZONE_MIN, 1);
        for (String zoneId : e.zones) c.statZone(zoneId).minutes++;
        c.pointsTerritoire += e.valeurTenue;
        c.minutesZonesAPayer += e.zonesTenues;
        c.minutesValeurAConvertir += e.valeurTenue;
        store.marquerDirty();
        long heures = c.minutesZonesAPayer / 60;
        if (heures > 0) {
            c.minutesZonesAPayer -= heures * 60;
            long montant = heures * ClansConfig.REVENU_HEURE.get();
            if (montant > 0) ClansApi.crediterBanque(c.id, montant, "territoires");
        }
        long heuresValeur = c.minutesValeurAConvertir / 60;
        if (heuresValeur > 0) {
            c.minutesValeurAConvertir -= heuresValeur * 60;
            long xp = heuresValeur * ClansConfig.XP_HEURE_VALEUR.get();
            if (xp > 0) ClanService.ajouterXp(c, xp, null);
        }
    }

    /** /clan admin : vide la table des loots d'un joueur (re-tester un bloc). */
    static void oublierLoot(UUID joueur) {
        LOOT.remove(joueur);
    }
}
