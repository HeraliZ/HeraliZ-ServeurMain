package fr.heraliz.crates.opening;

import fr.heraliz.crates.HeralizCrates;
import fr.heraliz.crates.block.CrateBlock;
import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.item.CrateKeyItem;
import fr.heraliz.crates.loot.CrateLoot;
import fr.heraliz.crates.loot.GeneralConfig;
import fr.heraliz.crates.loot.LootEntry;
import fr.heraliz.crates.loot.LootManager;
import fr.heraliz.crates.loot.PendingRewards;
import fr.heraliz.crates.loot.RewardHelper;
import fr.heraliz.crates.network.HzNet;
import fr.heraliz.crates.network.Packets;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ouverture des caisses dans l'interface (accroupi + clic droit) : pas d'animation dans le monde, une roue facon CS:GO
 * dans l'ecran du joueur. Le serveur decide tout : cles consommees et objets tires des la demande. Les objets sont
 * donnes quand la roue s'arrete (sinon ils apparaitraient dans la barre d'objets pendant qu'elle tourne) ; en attendant
 * ils sont notes dans PendingRewards, qui est sauvegarde avec le monde : rien ne se perd si le joueur part ou si le
 * serveur s'arrete.
 */
public final class CaseOpening {
    private CaseOpening() {
    }

    /** Nombre de cases de la bande, index de l'objet gagne (tire entre WIN_MIN et WIN_MIN + WIN_SPREAD - 1). */
    public static final int STRIP = 60, WIN_MIN = 46, WIN_SPREAD = 6;
    /** Duree de la roue (ticks) : l'objet attend jusque-la (ou le Skip). */
    public static final int REEL_TICKS = fr.heraliz.crates.block.CrateAnim.REEL_TICKS;
    /** Marge pour le trajet reseau : l'objet arrive juste apres l'arret de la roue chez le joueur, pas avant. */
    private static final int LATENCY = 4;
    /** Distance maximale a la caisse (blocs) et delai minimal entre deux demandes (ticks). */
    private static final double MAX_DIST = 10;
    private static final int COOLDOWN = 4;

    private static final class Session {
        final int id;
        final CrateType type;
        @Nullable
        final BlockPos pos;
        final ResourceKey<Level> dim;
        long last = Long.MIN_VALUE / 4;

        Session(int id, CrateType type, @Nullable BlockPos pos, ResourceKey<Level> dim) {
            this.id = id;
            this.type = type;
            this.pos = pos;
            this.dim = dim;
        }
    }

    /** Tirage en attente de la fin de la roue : l'etiquette enregistree dans PendingRewards. */
    private record Batch(UUID player, CrateType type, CompoundTag reward, long due) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final List<Batch> BATCHES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static int nextSession = 1;

    // =================================================================== interface
    /** Ouvre l'interface d'une caisse ; pos = null quand elle est ouverte par commande (PNJ, menu...). */
    public static void openScreen(ServerPlayer player, CrateType type, @Nullable BlockPos pos) {
        flush(player);
        Session session = new Session(nextSession++, type, pos == null ? null : pos.immutable(), player.level().dimension());
        SESSIONS.put(player.getUUID(), session);
        CrateLoot loot = LootManager.get(type);
        List<LootEntry> entries = new ArrayList<>();
        for (LootEntry e : loot.entries) if (e.isValid()) entries.add(e);
        entries.sort(Comparator.<LootEntry>comparingInt(e -> -e.rarity.ordinal()).thenComparingInt(e -> e.weight));
        List<Packets.Card> contents = new ArrayList<>();
        for (LootEntry e : entries) contents.add(card(loot, e));
        HzNet.toPlayer(player, new Packets.CaseScreen(type.id, session.id, free(player), contents));
    }

    /** Interface fermee : les objets de l'animation en cours sont donnes tout de suite. */
    public static void close(ServerPlayer player, int session) {
        flush(player);
        Session s = SESSIONS.get(player.getUUID());
        // une interface remplacee par une nouvelle se ferme apres l'ouverture de la suivante : on garde la nouvelle
        if (s != null && s.id == session) SESSIONS.remove(player.getUUID());
    }

    public static void logout(ServerPlayer player) {
        flush(player);
        SESSIONS.remove(player.getUUID());
    }

    /** Cles non consommees (creatif, selon general.json). */
    private static boolean free(ServerPlayer player) {
        return player.getAbilities().instabuild && !GeneralConfig.consumeKeyInCreative;
    }

    // =================================================================== ouverture
    public static void handleOpen(ServerPlayer player, Packets.CaseOpen msg) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.id != msg.session()) {
            reply(player, "gui.heralizcrates.case.error.session");
            return;
        }
        if (s.pos != null) {
            if (!player.level().dimension().equals(s.dim)
                    || player.distanceToSqr(s.pos.getX() + 0.5, s.pos.getY() + 0.5, s.pos.getZ() + 0.5) > MAX_DIST * MAX_DIST) {
                reply(player, "gui.heralizcrates.case.error.far");
                return;
            }
            if (!(player.level().getBlockState(s.pos).getBlock() instanceof CrateBlock cb) || cb.type != s.type) {
                reply(player, "gui.heralizcrates.case.error.session");
                return;
            }
        }
        MinecraftServer server = player.server;
        long now = server.getTickCount();
        if (now - s.last < COOLDOWN) {
            reply(player, "gui.heralizcrates.case.error.busy");
            return;
        }
        s.last = now;
        // la roue precedente est finie (ou passee) : son objet d'abord
        flush(player);

        CrateType type = s.type;
        CrateLoot loot = LootManager.get(type);
        if (loot.isEmpty()) {
            reply(player, "message.heralizcrates.empty");
            return;
        }
        LootEntry win = loot.roll(RANDOM, fr.heraliz.crates.loot.EmotesCaisse.permis(player, type));
        if (win == null) {
            // caisse des emotes : il a deja toutes celles qu'elle peut donner (la cle n'est pas consommee)
            reply(player, type == CrateType.SPECTACLE ? "gui.heralizcrates.case.error.all_emotes" : "message.heralizcrates.empty");
            return;
        }
        if (!free(player) && !consumeKey(player, type)) {
            reply(player, "gui.heralizcrates.case.error.nokey");
            return;
        }
        CompoundTag stored = PendingRewards.get(server).add(player.getUUID(), win.toTag(), type);
        BATCHES.add(new Batch(player.getUUID(), type, stored, now + REEL_TICKS + LATENCY));

        List<Packets.Card> strip = new ArrayList<>();
        int winIndex = WIN_MIN + RANDOM.nextInt(WIN_SPREAD);
        for (int i = 0; i < STRIP; i++) {
            LootEntry e = i == winIndex ? win : loot.rollDisplayEntry(RANDOM, win);
            strip.add(card(loot, e == null ? win : e));
        }
        HzNet.toPlayer(player, new Packets.CaseResult("", winIndex, strip));
    }

    private static Packets.Card card(CrateLoot loot, LootEntry e) {
        return new Packets.Card(e.displayStack(), e.rarity.ordinal(), e.count, (float) loot.chance(e));
    }

    private static void reply(ServerPlayer player, String error) {
        HzNet.toPlayer(player, new Packets.CaseResult(error, -1, List.of()));
    }

    // =================================================================== cles
    private static boolean consumeKey(ServerPlayer player, CrateType type) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.getItem() instanceof CrateKeyItem k && k.type == type && !st.isEmpty()) {
                st.shrink(1);
                if (st.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
                inv.setChanged();
                return true;
            }
        }
        return false;
    }

    // =================================================================== remise differee
    /** A chaque tick serveur : tirages dont l'animation est finie. */
    public static void tick(MinecraftServer server) {
        if (BATCHES.isEmpty()) return;
        long now = server.getTickCount();
        List<Batch> due = new ArrayList<>();
        for (Iterator<Batch> it = BATCHES.iterator(); it.hasNext(); ) {
            Batch b = it.next();
            if (b.due <= now) {
                due.add(b);
                it.remove();
            }
        }
        for (Batch b : due) {
            ServerPlayer p = server.getPlayerList().getPlayer(b.player);
            // joueur parti entre-temps : les recompenses restent dans PendingRewards (donnees a la connexion)
            if (p != null) deliver(p, b);
        }
    }

    /** Donne tout de suite ce qui attend la fin de l'animation (animation passee, ecran ferme, deconnexion). */
    public static void flush(ServerPlayer player) {
        List<Batch> mine = new ArrayList<>();
        for (Iterator<Batch> it = BATCHES.iterator(); it.hasNext(); ) {
            Batch b = it.next();
            if (b.player.equals(player.getUUID())) {
                mine.add(b);
                it.remove();
            }
        }
        for (Batch b : mine) deliver(player, b);
    }

    private static void deliver(ServerPlayer player, Batch b) {
        // deja donnee (reconnexion apres un arret brutal) : rien a faire
        if (!PendingRewards.get(player.server).remove(b.player, b.reward)) return;
        LootEntry e = LootEntry.fromTag(b.reward);
        RewardHelper.deliver(player, e, b.type);
        RewardHelper.announceInterface(player, e, b.type);
    }

    /** Arret du serveur : on donne ce qui attend encore aux joueurs connectes, puis on oublie tout (monde solo suivant). */
    public static void serverStopping(MinecraftServer server) {
        for (Batch b : new ArrayList<>(BATCHES)) {
            ServerPlayer p = server.getPlayerList().getPlayer(b.player);
            if (p != null) deliver(p, b);
        }
        if (!BATCHES.isEmpty()) HeralizCrates.LOGGER.info("[HeralizCrates] {} ouverture(s) en attente remise(s) a l'arret", BATCHES.size());
        BATCHES.clear();
        SESSIONS.clear();
    }
}
