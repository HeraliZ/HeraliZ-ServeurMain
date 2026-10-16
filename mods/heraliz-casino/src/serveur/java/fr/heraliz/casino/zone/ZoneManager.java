package fr.heraliz.casino.zone;

import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.block.entity.CashierBlockEntity;
import fr.heraliz.casino.registry.Snd;
import fr.heraliz.casino.config.CasinoConfig;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.economy.ChipsServeur;
import fr.heraliz.casino.economy.EconomieServeur;
import fr.heraliz.casino.entity.GuardEntity;
import fr.heraliz.casino.entity.GuardEntityServeur;
import fr.heraliz.casino.net.CasinoNet;
import fr.heraliz.casino.net.ZoneDialogPacket;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les zones du casino cote serveur : les videurs arretent a l'entree les joueurs qui n'ont pas paye (retour dehors,
 * dialogue pour payer), et sortent ceux qui n'ont plus de quoi jouer. Zone VIP : meme chose, mais l'entree ne se paie
 * pas, elle est reservee aux grades de la boutique (Rescape au moins, {@link AccesVip}). Les joueurs en creatif ou
 * spectateur passent librement (ils construisent le casino).
 */
@Mod.EventBusSubscriber(modid = HeralizCasino.MODID)
public final class ZoneManager {
    private ZoneManager() {
    }

    /** Etat d'un joueur vis-a-vis de la zone (oublie a la deconnexion). */
    static final class Visit {
        /** Entre en regle (ou present a la creation de la zone) : il reste admis tant qu'il ne sort pas. */
        boolean admitted;
        /** A mise au moins une fois depuis son entree. */
        boolean played;
        @Nullable
        Vec3 lastOutside;
        long blockedAt = Long.MIN_VALUE / 2, enteredAt;
        int brokeTicks;
    }

    /** Coins de la future zone choisis par un operateur. */
    public record Selection(@Nullable ResourceKey<Level> dim, @Nullable BlockPos a, @Nullable BlockPos b) {
        public boolean complete() {
            return dim != null && a != null && b != null;
        }
    }

    /**
     * Ce que le joueur donnerait pour payer l'entree : ses jetons de casino (chips), sinon des jetons de son compte
     * (server, mod HeraliZ Economie), sinon des objets que la caisse achete (la monnaie etant rendue en jetons de casino).
     */
    public record Offer(boolean possible, long chips, @Nullable Item item, int count, long change, long server) {
        Offer(boolean possible, long chips, @Nullable Item item, int count, long change) {
            this(possible, chips, item, count, change, 0);
        }

        public Component text() {
            if (server > 0) return Component.translatable("casino.heralizcasino.from_account", EconomieServeur.format(server));
            if (item == null) return Chips.text(chips);
            return Component.literal(count + " × ").append(new ItemStack(item).getHoverName());
        }
    }

    private static final Map<CasinoZone.Kind, Map<UUID, Visit>> VISITS = new java.util.EnumMap<>(CasinoZone.Kind.class);
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private static final List<Ejection> EJECTIONS = new ArrayList<>();
    /** Joueurs qu'on vient de jeter dehors : ni chute ni coup pendant quelques instants (fin, en ticks du monde). */
    private static final Map<UUID, Long> SAFE_UNTIL = new HashMap<>();

    /** Ticks entre deux interpellations du meme joueur par un videur. */
    private static final int BLOCK_COOLDOWN = 60;

    // =================================================================== boucle du serveur
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        EJECTIONS.removeIf(e -> !e.tick());
        for (CasinoZone.Kind kind : CasinoZone.Kind.values()) {
            CasinoZone zone = CasinoZone.get(server, kind);
            if (!zone.exists()) continue;
            ServerLevel level = server.getLevel(zone.dimension());
            if (level == null) continue;
            long now = level.getGameTime();
            List<ServerPlayer> players = new ArrayList<>(level.players());
            for (ServerPlayer p : players) tickPlayer(zone, level, p, now);
        }
        long now = server.overworld().getGameTime();
        if (now % 200 == 0) SAFE_UNTIL.values().removeIf(until -> until < now);
    }

    static boolean exempt(ServerPlayer p) {
        return p.isCreative() || p.isSpectator() || p instanceof FakePlayer;
    }

    static Visit visit(ServerPlayer p, CasinoZone.Kind kind) {
        return VISITS.computeIfAbsent(kind, k -> new HashMap<>()).computeIfAbsent(p.getUUID(), k -> new Visit());
    }

    private static void tickPlayer(CasinoZone zone, ServerLevel level, ServerPlayer p, long now) {
        if (!p.isAlive()) return;
        Visit v = visit(p, zone.kind());
        boolean inside = zone.contains(p.getX(), p.getY(), p.getZ());
        if (ejecting(p.getUUID())) return;
        if (exempt(p)) {
            v.admitted = inside;
            if (!inside) v.lastOutside = p.position();
            return;
        }
        if (!inside) {
            // il ressort : le ticket ne vaut que pour une entree, il faudra repayer pour revenir (la zone VIP ne se paie pas)
            if (v.admitted && !zone.vip()) {
                zone.revoke(p.getUUID());
                p.displayClientMessage(Component.translatable("message.heralizcasino.zone.left").withStyle(ChatFormatting.GRAY), true);
            }
            v.lastOutside = p.position();
            v.admitted = false;
            v.played = false;
            v.brokeTicks = 0;
            return;
        }
        CasinoConfig.Entree cfg = CasinoConfig.get().caisse.entree;
        if (!v.admitted && zone.vip()) {
            if (AccesVip.autorise(p)) {
                v.admitted = true;
                v.played = false;
                v.enteredAt = now;
                v.brokeTicks = 0;
                p.displayClientMessage(Component.translatableWithFallback("message.heralizcasino.zone.vip_welcome", "Bienvenue en zone VIP.")
                        .withStyle(ChatFormatting.GOLD), true);
            } else {
                block(zone, level, p, v, now);
            }
            return;
        }
        if (!v.admitted) {
            if (cfg.prix <= 0 || zone.hasPass(p.getUUID())) {
                v.admitted = true;
                v.played = false;
                v.enteredAt = now;
                v.brokeTicks = 0;
            } else {
                block(zone, level, p, v, now);
            }
            return;
        }
        // admis : les videurs gardent un oeil sur ceux qui n'ont plus un jeton. S'il lui reste des objets que la caisse
        // rachete, il a un peu de temps pour aller les changer ; sinon les videurs arrivent tout de suite.
        if (now % 10 != 0 || !cfg.expulserFauches) return;
        boolean out = (v.played || (cfg.expulserSpectateurs && now - v.enteredAt > 200)) && isBroke(level, zone, p);
        if (!out) {
            v.brokeTicks = 0;
            return;
        }
        boolean canSell = hasSellable(p);
        int delay = cfg.delaiExpulsion * 20;
        if (canSell) delay = Math.max(delay, SELL_GRACE);
        if (v.brokeTicks == 0) {
            Component warn = canSell ? Component.translatable("message.heralizcasino.zone.broke_sell", (delay + 19) / 20)
                    : Component.translatable("message.heralizcasino.zone.broke");
            p.displayClientMessage(warn.copy().withStyle(ChatFormatting.GOLD), true);
        }
        v.brokeTicks += 10;
        if (v.brokeTicks >= delay) {
            v.brokeTicks = 0;
            eject(zone, level, p, Ejection.BROKE);
        }
    }

    /** Temps laisse a un joueur sans jeton pour aller changer a la caisse les objets qu'il a sur lui (ticks). */
    private static final int SELL_GRACE = 30 * 20;

    // =================================================================== entree
    /**
     * Joueur sans ticket (ou sans grade, zone VIP) dans la zone : retour dehors, et un videur l'interpelle (au plus une
     * fois toutes les 3 s).
     */
    private static void block(CasinoZone zone, ServerLevel level, ServerPlayer p, Visit v, long now) {
        // il ressort par ou il est entre (le mur de la salle le plus proche) ; s'il arrive de loin (teleportation),
        // il est depose devant l'entree
        CasinoZone.Entrance wall = zone.facade(p.position());
        Vec3 back = v.lastOutside;
        if (back == null || zone.contains(back) || back.distanceToSqr(p.position()) > 36) {
            CasinoZone.Entrance e = zone.nearest(p.position());
            if (e != null) wall = e;
            Vec3 g = ground(level, wall.at(1.2));
            back = g != null ? g : wall.at(1.2);
        }
        if (p.isPassenger()) {
            MachineBlockEntity.allowDismount = true;
            try {
                p.stopRiding();
            } finally {
                MachineBlockEntity.allowDismount = false;
            }
        }
        p.teleportTo(level, back.x, back.y, back.z, p.getYRot(), p.getXRot());
        // bourrade : il recule de trois blocs environ, de quoi voir en entier le videur qui lui parle
        p.setDeltaMovement(wall.nx() * 0.56, 0.25, wall.nz() * 0.56);
        p.hurtMarked = true;
        v.lastOutside = back;
        if (now - v.blockedAt < BLOCK_COOLDOWN) return;
        v.blockedAt = now;
        if (zone.vip()) {
            refuseVip(p, nearestGuard(level, p.position(), 20, CasinoZone.Kind.VIP));
            return;
        }
        long price = CasinoConfig.get().caisse.entree.prix;
        Component line = Component.translatable("guard.heralizcasino.stop." + level.random.nextInt(3), Chips.format(price));
        GuardEntity g = nearestGuard(level, p.position(), 20, CasinoZone.Kind.CASINO);
        if (g != null) g.challenge(p, line);
        sendDialog(p, g, line);
    }

    /** Le videur VIP refuse le joueur sans grade : il le dit (bulle) et le joueur le lit aussi dans son chat. */
    private static void refuseVip(ServerPlayer p, @Nullable GuardEntity g) {
        Component line = Component.translatableWithFallback("guard.heralizcasino.vip.stop",
                "Non, tu n'es pas désigné comme VIP (grade Rescapé, Traqueur ou Roi de la Zone).");
        if (g != null) g.challenge(p, line);
        p.displayClientMessage(Component.translatableWithFallback("message.heralizcasino.zone.vip_refused",
                "Accès refusé : tu n'es pas désigné comme VIP (grade Rescapé, Traqueur ou Roi de la Zone).").withStyle(ChatFormatting.RED), false);
    }

    private static void sendDialog(ServerPlayer p, @Nullable GuardEntity g, Component line) {
        CasinoConfig.Entree cfg = CasinoConfig.get().caisse.entree;
        Offer o = offer(p, cfg.prix);
        CasinoNet.sendTo(p, new ZoneDialogPacket(g == null ? -1 : g.getId(), line, cfg.prix, o.text(), o.change(), o.possible(), cfg.validiteMinutes));
    }

    /** Clic droit sur un videur : il dit ou en est le joueur, et propose de payer s'il n'a pas de ticket. */
    public static void talk(ServerPlayer p, GuardEntityServeur g) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        CasinoZone zone = CasinoZone.get(server, g.zone());
        CasinoConfig.Entree cfg = CasinoConfig.get().caisse.entree;
        if (p.isCreative()) {
            p.displayClientMessage(Component.translatable("message.heralizcasino.zone.creative").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (zone.vip()) {
            if (AccesVip.autorise(p)) g.say(Component.translatableWithFallback("guard.heralizcasino.vip.welcome", "Bienvenue en zone VIP. Fais comme chez toi."));
            else refuseVip(p, g);
            return;
        }
        if (!zone.exists() || cfg.prix <= 0) {
            g.say(Component.translatable("guard.heralizcasino.free"));
            return;
        }
        if (zone.hasPass(p.getUUID()) || visit(p, CasinoZone.Kind.CASINO).admitted) {
            g.say(Component.translatable("guard.heralizcasino.pass"));
            return;
        }
        Component line = Component.translatable("guard.heralizcasino.price", Chips.format(cfg.prix));
        g.challenge(p, line);
        sendDialog(p, g, line);
    }

    /**
     * Ce que le joueur peut donner pour "price" jetons de casino : ses jetons de casino, sinon des jetons de son compte
     * (au taux de la caisse, si l'economie est la et le permet), sinon des objets que la caisse achete.
     */
    public static Offer offer(ServerPlayer p, long price) {
        if (Chips.count(p) >= price) return new Offer(true, price, null, 0, 0);
        if (serverEntry()) {
            long cost = EconomieServeur.prixServeur(price);
            if (EconomieServeur.solde(p.getUUID()) >= cost) return new Offer(true, 0, null, 0, 0, cost);
        }
        Offer best = null;
        for (CasinoConfig.Exchange e : CasinoConfig.get().caisse.echanges) {
            if (!CasinoConfig.get().caisse.echangesObjets) break;
            if (!e.achat || e.jetons <= 0) continue;
            Item it = CashierBlockEntity.item(e);
            if (it == null) continue;
            long need = (price + e.jetons - 1) / e.jetons;
            if (need > Integer.MAX_VALUE || CashierBlockEntity.countItems(p, it) < need) continue;
            long change = need * e.jetons - price;
            if (best == null || change < best.change()) best = new Offer(true, 0, it, (int) need, change);
        }
        return best != null ? best : new Offer(false, price, null, 0, 0);
    }

    /** Le joueur paie son entree (demande par la fenetre de dialogue). */
    public static boolean pay(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null) return false;
        CasinoZone zone = CasinoZone.get(server);
        CasinoConfig.Entree cfg = CasinoConfig.get().caisse.entree;
        if (!zone.exists() || cfg.prix <= 0 || zone.hasPass(p.getUUID())) return false;
        Offer o = offer(p, cfg.prix);
        GuardEntity g = p.level() instanceof ServerLevel sl ? nearestGuard(sl, p.position(), 20, CasinoZone.Kind.CASINO) : null;
        if (!o.possible()) {
            p.displayClientMessage(Component.translatable("message.heralizcasino.zone.cant_pay", Chips.format(cfg.prix)).withStyle(ChatFormatting.RED), true);
            if (g != null) g.say(Component.translatable("guard.heralizcasino.no_money"));
            return false;
        }
        if (o.server() > 0) {
            // retrait atomique du compte (refuse si le solde a baisse entre-temps), inscrit au journal de l'economie
            if (!EconomieServeur.retirer(p.getUUID(), o.server(), EconomieServeur.MOTIF_ENTREE)) return false;
        } else if (o.item() == null) {
            if (!ChipsServeur.take(p, o.chips())) return false;
        } else {
            takeItems(p, o.item(), o.count());
            if (o.change() > 0) ChipsServeur.give(p, o.change());
        }
        // ticket pour une seule entree (a utiliser dans le delai "validiteMinutes") : qui ressort repaie
        zone.grant(p.getUUID(), cfg.validiteMinutes);
        zone.earn(cfg.prix);
        p.displayClientMessage(Component.translatable("message.heralizcasino.zone.paid_once", o.text()).withStyle(ChatFormatting.GREEN), true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModRegistry.sound(Snd.COINS), SoundSource.PLAYERS, 0.8f, 1f);
        if (g != null) {
            // il montre la porte
            CasinoZone.Entrance door = zone.nearest(g.position());
            g.pointAt(door != null ? door.at(-1.5) : zone.box().getCenter(), 50);
            g.say(Component.translatable("guard.heralizcasino.paid." + p.getRandom().nextInt(2)));
        }
        HeralizCasino.LOGGER.info("[HeralizCasino] {} paie son entree au casino ({} jetons de casino{})", p.getGameProfile().getName(), cfg.prix,
                o.server() > 0 ? ", " + o.server() + " jetons pris sur son compte" : o.item() != null ? ", en objets" : "");
        return true;
    }

    private static void takeItems(ServerPlayer p, Item item, int count) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && count > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item) && !s.hasTag()) {
                int k = Math.min(count, s.getCount());
                s.shrink(k);
                count -= k;
                if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            }
        }
        inv.setChanged();
    }

    // =================================================================== fauches
    /** Une mise vient d'etre posee : le joueur "a joue" (il pourra etre sorti s'il tombe a zero). */
    public static void onWager(@Nullable UUID player) {
        if (player == null) return;
        for (Map<UUID, Visit> visits : VISITS.values()) {
            Visit v = visits.get(player);
            if (v != null && v.admitted) v.played = true;
        }
    }

    /**
     * Le joueur est fauche : moins de jetons que la plus petite mise du casino, et aucune partie en cours (mise posee,
     * rouleaux lances, bille en vol, duel ouvert). Les objets qu'il a sur lui ne comptent pas : il a juste un peu de
     * temps pour aller les changer a la caisse (voir hasSellable).
     */
    public static boolean isBroke(ServerLevel level, CasinoZone zone, ServerPlayer p) {
        if (Chips.count(p) >= CasinoConfig.get().minimalBet()) return false;
        return !engaged(level, zone, p.getUUID());
    }

    /** L'entree peut se payer avec les jetons du compte (mod HeraliZ Economie present, change et option ouverts). */
    private static boolean serverEntry() {
        CasinoConfig.JetonsServeur e = CasinoConfig.get().caisse.economie;
        return e.entree && EconomieServeur.ouvert() && EconomieServeur.disponible();
    }

    /**
     * Le joueur peut encore se refaire a la caisse : il a sur lui au moins un objet qu'elle achete (emeraude, lingot
     * d'or...), ou assez de jetons sur son compte pour la plus petite mise (si le change en jetons du serveur est ouvert).
     */
    public static boolean hasSellable(ServerPlayer p) {
        CasinoConfig.JetonsServeur eco = CasinoConfig.get().caisse.economie;
        if (eco.achat && EconomieServeur.ouvert() && EconomieServeur.disponible()
                && EconomieServeur.solde(p.getUUID()) >= EconomieServeur.prixServeur(CasinoConfig.get().minimalBet())) {
            return true;
        }
        if (!CasinoConfig.get().caisse.echangesObjets) return false;
        for (CasinoConfig.Exchange e : CasinoConfig.get().caisse.echanges) {
            if (!e.achat || e.jetons <= 0) continue;
            Item it = CashierBlockEntity.item(e);
            if (it != null && CashierBlockEntity.countItems(p, it) > 0) return true;
        }
        return false;
    }

    /** Une machine de la salle (ou toute proche) a encore de l'argent du joueur en jeu. */
    public static boolean engaged(ServerLevel level, CasinoZone zone, UUID player) {
        AABB b = zone.box().inflate(10);
        int cx0 = SectionPos.blockToSectionCoord(Mth.floor(b.minX)), cx1 = SectionPos.blockToSectionCoord(Mth.floor(b.maxX));
        int cz0 = SectionPos.blockToSectionCoord(Mth.floor(b.minZ)), cz1 = SectionPos.blockToSectionCoord(Mth.floor(b.maxZ));
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof MachineBlockEntity m && m.engaged(player)) return true;
                }
            }
        }
        return false;
    }

    static boolean ejecting(UUID player) {
        for (Ejection e : EJECTIONS) if (e.player().equals(player)) return true;
        return false;
    }

    /**
     * Les videurs sortent le joueur de la zone ou il se trouve (la zone VIP d'abord, si elle est dans le casino).
     * @return false si c'est impossible (pas de zone, joueur ailleurs, deja en cours)
     */
    public static boolean eject(ServerLevel level, ServerPlayer p, int reason) {
        CasinoZone vip = CasinoZone.get(level.getServer(), CasinoZone.Kind.VIP);
        CasinoZone zone = vip.in(level) && vip.contains(p.position()) ? vip : CasinoZone.get(level.getServer());
        return eject(zone, level, p, reason);
    }

    /** Les videurs sortent le joueur de cette zone (animation d'une douzaine de secondes). */
    static boolean eject(CasinoZone zone, ServerLevel level, ServerPlayer p, int reason) {
        if (!zone.in(level) || p.level() != level || !p.isAlive() || ejecting(p.getUUID())) return false;
        Ejection e = Ejection.begin(zone, level, p, reason);
        if (e == null) return false;
        EJECTIONS.add(e);
        return true;
    }

    /** Fin d'une expulsion : ticket perdu, visite terminee, quelques secondes a l'abri des chutes et des coups. */
    static void ejected(CasinoZone zone, ServerLevel level, ServerPlayer p) {
        zone.revoke(p.getUUID());
        Visit v = visit(p, zone.kind());
        v.admitted = false;
        v.played = false;
        v.brokeTicks = 0;
        v.lastOutside = p.position();
        v.blockedAt = level.getGameTime();
        SAFE_UNTIL.put(p.getUUID(), level.getGameTime() + 80);
    }

    static void safe(ServerLevel level, UUID player, int ticks) {
        SAFE_UNTIL.put(player, level.getGameTime() + ticks);
    }

    private static boolean isSafe(net.minecraft.world.entity.Entity e) {
        if (!(e instanceof ServerPlayer p)) return false;
        if (ejecting(p.getUUID())) return true;
        Long until = SAFE_UNTIL.get(p.getUUID());
        return until != null && until >= p.level().getGameTime();
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (isSafe(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (isSafe(event.getEntity()) && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        for (Ejection e : EJECTIONS) if (e.player().equals(id)) e.abort();
        for (Map<UUID, Visit> visits : VISITS.values()) visits.remove(id);
        SELECTIONS.remove(id);
    }

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        VISITS.clear();
        SELECTIONS.clear();
        EJECTIONS.clear();
        SAFE_UNTIL.clear();
    }

    // =================================================================== creation (commandes, tablette)
    public static Selection selection(UUID op) {
        return SELECTIONS.getOrDefault(op, new Selection(null, null, null));
    }

    /** Coin A (which = 0) ou B (1) de la future zone ; changer de dimension recommence la selection. */
    public static Selection select(ServerPlayer op, int which, BlockPos pos) {
        Selection s = selection(op.getUUID());
        ResourceKey<Level> dim = op.level().dimension();
        if (s.dim() != null && !s.dim().equals(dim)) s = new Selection(null, null, null);
        s = which == 0 ? new Selection(dim, pos.immutable(), s.b()) : new Selection(dim, s.a(), pos.immutable());
        SELECTIONS.put(op.getUUID(), s);
        if (s.complete()) outline(op, new AABB(s.a()).minmax(new AABB(s.b())), true);
        return s;
    }

    /** Cree la zone a partir de la selection de l'operateur ; les joueurs deja dedans restent admis. */
    public static boolean create(ServerPlayer op, CasinoZone.Kind kind) {
        Selection s = selection(op.getUUID());
        MinecraftServer server = op.getServer();
        if (!s.complete() || server == null) return false;
        CasinoZone zone = CasinoZone.get(server, kind);
        if (zone.exists()) removeGuards(server, zone);
        zone.clear();
        zone.define(s.dim(), s.a(), s.b());
        ServerLevel level = server.getLevel(s.dim());
        if (level != null) {
            for (ServerPlayer p : level.players()) {
                Visit v = visit(p, kind);
                v.admitted = zone.contains(p.position());
                v.enteredAt = level.getGameTime();
                v.lastOutside = v.admitted ? null : p.position();
            }
        }
        SELECTIONS.remove(op.getUUID());
        outline(op, zone.box(), false);
        return true;
    }

    /** Entree a l'endroit ou se tient l'operateur : la porte est ramenee sur la facade, deux videurs la flanquent. */
    public static int addEntrance(ServerPlayer op, CasinoZone.Kind kind) {
        MinecraftServer server = op.getServer();
        if (server == null || !(op.level() instanceof ServerLevel level)) return -1;
        CasinoZone zone = CasinoZone.get(server, kind);
        if (!zone.in(level)) return -1;
        CasinoZone.Entrance e = zone.addEntrance(op.position());
        return postGuards(level, e, kind);
    }

    private static int postGuards(ServerLevel level, CasinoZone.Entrance e, CasinoZone.Kind kind) {
        Vec3 t = e.tangent();
        float yaw = yaw(e.nx(), e.nz());
        int n = 0;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 spot = null;
            // {ecart le long de la facade, distance devant la porte} : le plus pres d'abord
            for (double[] o : new double[][]{{1.6, 1.0}, {1.3, 1.5}, {2.3, 1.0}, {1.6, 2.2}, {1.0, 0.9}, {2.8, 1.6}}) {
                Vec3 c = ground(level, e.at(o[1]).add(t.scale(side * o[0])));
                if (c != null && free(level, c)) {
                    spot = c;
                    break;
                }
            }
            if (spot == null) continue;
            GuardEntityServeur.post(level, spot, yaw, side, kind);
            n++;
        }
        return n;
    }

    /** Videur supplementaire, exactement la ou se tient l'operateur, tourne comme lui. */
    public static void addGuard(ServerPlayer op, CasinoZone.Kind kind) {
        if (op.level() instanceof ServerLevel level) GuardEntityServeur.post(level, op.position(), op.getYRot(), op.getRandom().nextBoolean() ? 1 : -1, kind);
    }

    public static boolean delete(MinecraftServer server, CasinoZone.Kind kind) {
        CasinoZone zone = CasinoZone.get(server, kind);
        if (!zone.exists()) return false;
        removeGuards(server, zone);
        zone.clear();
        Map<UUID, Visit> visits = VISITS.get(kind);
        if (visits != null) for (Visit v : visits.values()) v.admitted = false;
        return true;
    }

    /** Retire les videurs en poste (charges) de cette zone, autour d'elle ; ceux de l'autre zone restent. */
    private static void removeGuards(MinecraftServer server, CasinoZone zone) {
        ServerLevel level = zone.dimension() == null ? null : server.getLevel(zone.dimension());
        if (level == null) return;
        for (GuardEntityServeur g : level.getEntitiesOfClass(GuardEntityServeur.class, zone.box().inflate(24), x -> !x.isTemporary() && x.zone() == zone.kind())) {
            g.discard();
        }
    }

    public static int guardCount(ServerLevel level, CasinoZone zone) {
        return level.getEntitiesOfClass(GuardEntityServeur.class, zone.box().inflate(24), g -> !g.isTemporary() && g.zone() == zone.kind()).size();
    }

    /** Le mod des grades est la : la zone VIP peut s'ouvrir aux joueurs gradés. */
    public static boolean vipDisponible() {
        return AccesVip.disponible();
    }

    /** Ticket offert (sans payer) pour "minutes" minutes. */
    public static void ticket(MinecraftServer server, ServerPlayer p, int minutes) {
        CasinoZone.get(server).grant(p.getUUID(), Math.max(1, minutes));
    }

    // =================================================================== outils
    @Nullable
    static GuardEntity nearestGuard(ServerLevel level, Vec3 at, double radius, CasinoZone.Kind kind) {
        GuardEntity best = null;
        double bd = Double.MAX_VALUE;
        for (GuardEntityServeur g : level.getEntitiesOfClass(GuardEntityServeur.class, new AABB(at, at).inflate(radius), x -> !x.isTemporary() && x.zone() == kind)) {
            double d = g.distanceToSqr(at);
            if (d < bd) {
                bd = d;
                best = g;
            }
        }
        return best;
    }

    /** Lacet Minecraft d'une direction horizontale. */
    static float yaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * Point ou l'on peut se tenir debout a la verticale de "p" : le sol le plus proche (de 2 blocs au-dessus a 4 en
     * dessous) avec deux blocs libres ; null s'il n'y en a pas.
     */
    @Nullable
    static Vec3 ground(ServerLevel level, Vec3 p) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(p.x), bz = Mth.floor(p.z), by = Mth.floor(p.y + 0.01);
        for (int dy : new int[]{0, 1, -1, 2, -2, -3, -4}) {
            int y = by + dy;
            m.set(bx, y - 1, bz);
            BlockState below = level.getBlockState(m);
            VoxelShape floor = below.getCollisionShape(level, m);
            if (floor.isEmpty()) continue;
            double top = y - 1 + floor.max(net.minecraft.core.Direction.Axis.Y);
            Vec3 c = new Vec3(p.x, top, p.z);
            if (free(level, c)) return c;
        }
        return null;
    }

    static boolean free(ServerLevel level, Vec3 c) {
        return level.noCollision(new AABB(c.x - 0.3, c.y + 0.01, c.z - 0.3, c.x + 0.3, c.y + 1.85, c.z + 0.3));
    }

    /** Contour de la zone en particules, pour l'operateur seulement. */
    public static void outline(ServerPlayer op, AABB raw, boolean selection) {
        if (!(op.level() instanceof ServerLevel level)) return;
        AABB b = selection ? new AABB(raw.minX, raw.minY, raw.minZ, raw.maxX, raw.maxY, raw.maxZ) : raw;
        double len = b.getXsize() + b.getYsize() + b.getZsize();
        double step = Math.max(0.5, len * 4 / 480);
        var type = selection ? ParticleTypes.FLAME : ParticleTypes.END_ROD;
        for (int edge = 0; edge < 12; edge++) {
            // 4 aretes le long de chaque axe
            int axis = edge / 4, k = edge % 4;
            double a0 = (k & 1) == 0 ? 0 : 1, a1 = (k & 2) == 0 ? 0 : 1;
            double size = axis == 0 ? b.getXsize() : axis == 1 ? b.getYsize() : b.getZsize();
            for (double s = 0; s <= size; s += step) {
                double x = axis == 0 ? b.minX + s : b.minX + a0 * b.getXsize();
                double y = axis == 1 ? b.minY + s : b.minY + (axis == 0 ? a0 : a1) * b.getYsize();
                double z = axis == 2 ? b.minZ + s : b.minZ + a1 * b.getZsize();
                level.sendParticles(op, type, true, x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }
}
