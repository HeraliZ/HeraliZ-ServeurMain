package com.caracrea.server;

import com.caracrea.CaraCrea;
import com.caracrea.CaraCreaConfig;
import com.caracrea.api.voice.VoiceEvent;
import com.caracrea.api.voice.VoiceFrame;
import com.caracrea.api.voice.VoiceRecipients;
import com.caracrea.api.voice.VoiceRouter;
import com.caracrea.network.CaraCreaNetwork;
import com.caracrea.network.CaraCreaNetwork.GroupAction;
import com.caracrea.network.CaraCreaNetwork.GroupInfo;
import com.caracrea.network.CaraCreaNetwork.PlayerVoice;
import com.caracrea.voice.net.TokenBucket;
import com.caracrea.voice.net.UdpVoiceServer;
import com.caracrea.voice.net.VoiceProtocol;
import com.caracrea.voice.net.VoiceRouting;
import com.caracrea.voice.net.VoiceSnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkDirection;

import javax.annotation.Nullable;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.caracrea.voice.net.VoiceSnapshot.*;

/**
 * Server half of the built-in voice chat (CaraCrea 1.1): one voice session per player (random id and key, given over
 * the game connection), the dedicated UDP channel ({@link UdpVoiceServer}), the game-connection fallback, who hears
 * whom ({@link VoiceRouting}), groups, voice states, the per-player overrides and routing hooks of the API, and the
 * talk events.
 *
 * <p>The game thread only copies, once per tick, where everyone is and what they may do into a
 * {@link VoiceSnapshot}. Voice frames are routed and sent from the UDP thread (or, for a player whose UDP is blocked,
 * from the network thread of their connection, see {@link VoiceInbound}), never on the game thread: a lagging server
 * does not make the voice stutter, and fifty players talking do not cost the tick anything but the copy.
 *
 * <p>Groups work like the usual voice chat mod's: members hear each other from anywhere. In a <i>normal</i> group, what
 * members say stays in the group, and they still hear the players around them; in an <i>open</i> group, the players
 * around them also hear them; in an <i>isolated</i> group, members only hear each other.
 */
public final class VoiceServer {
    public static final int NORMAL = VoiceRouting.GROUP_NORMAL;
    public static final int OPEN = VoiceRouting.GROUP_OPEN;
    public static final int ISOLATED = VoiceRouting.GROUP_ISOLATED;

    /**
     * Development only (-Dcaracrea.voice.echo=true): every frame is also sent back to its speaker, as if heard from
     * someone standing at the same place, so a single test client exercises the whole path (encode, send, relay,
     * receive, decode, play, lips).
     */
    private static final boolean ECHO = Boolean.getBoolean("caracrea.voice.echo");
    /** Development only (-Dcaracrea.voice.dev=true): simulated peers for the test bench. */
    private static final boolean DEV = Boolean.getBoolean("caracrea.voice.dev");
    private static final long TALK_NS = 250_000_000L;
    private static final long UDP_TIMEOUT_NS = 8_000_000_000L;

    /** Frames relayed since start (any transport). */
    public static final AtomicLong RELAYED = new AtomicLong();
    /** Frames taken out of the connections on their network thread by {@link VoiceInbound}. */
    public static final AtomicLong INBOUND = new AtomicLong();
    private static final AtomicLong TCP_OUT = new AtomicLong();
    private static final AtomicLong UDP_OUT = new AtomicLong();

    /** One player's voice session (or a simulated peer of the test bench). */
    static final class Member {
        final UUID uuid;
        final String name;
        final int voiceId;
        final long session;
        final byte[] key;
        final boolean simulated;
        final TokenBucket tcpLimit = new TokenBucket(60, 20);
        volatile ServerPlayer player;
        volatile UdpVoiceServer.Peer peer;
        /** The client says its UDP works (otherwise voices go through the game connection). */
        volatile boolean clientUdp;
        volatile boolean reported;
        volatile boolean disabled;
        volatile boolean selfMuted;
        /** Le joueur a coupé tout le monde : il n'entend plus aucune voix (les autres l'entendent toujours). */
        volatile boolean selfDeafened;
        volatile boolean adminMuted;
        // API overrides (not kept).
        volatile float range = Float.NaN;
        volatile boolean silenced;
        volatile boolean deafened;
        /** Index in the latest snapshot (a hint). */
        volatile int slot = -1;
        volatile long lastVoice;
        volatile boolean ended;
        volatile boolean lastWhisper;
        volatile boolean lastShout;
        // Server thread.
        boolean talking;
        String lastTransport;
        // Simulated peer.
        volatile ResourceKey<Level> simLevel;
        volatile double simX;
        volatile double simY;
        volatile double simZ;

        Member(UUID uuid, String name, int voiceId, long session, byte[] key, boolean simulated) {
            this.uuid = uuid;
            this.name = name;
            this.voiceId = voiceId;
            this.session = session;
            this.key = key;
            this.simulated = simulated;
        }
    }

    private static final class Group {
        final int id;
        final String name;
        final String password;
        final int type;
        final Set<UUID> members = new HashSet<>();

        Group(int id, String name, String password, int type) {
            this.id = id;
            this.name = name;
            this.password = password;
            this.type = type;
        }
    }

    private static final Map<UUID, Member> MEMBERS = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final VoiceSnapshot.Ring SNAPSHOTS = new VoiceSnapshot.Ring();
    private static final ThreadLocal<VoiceRouting.Recipients> RECIPIENTS = ThreadLocal.withInitial(VoiceRouting.Recipients::new);
    private static final ThreadLocal<ApiView> API_VIEW = ThreadLocal.withInitial(ApiView::new);
    private static volatile VoiceRouter[] routers = new VoiceRouter[0];
    private static volatile VoiceRouting.Rules rules = new VoiceRouting.Rules(false, 48, 24, 96, 1, 1, true, true, false, true, true);
    private static volatile UdpVoiceServer udp;
    private static volatile boolean active;
    private static volatile String[] levelNames = new String[0];
    // Server thread only.
    private static final List<Member> ORDER = new ArrayList<>();
    private static final BitSet VOICE_IDS = new BitSet();
    private static final Map<Integer, Group> GROUPS = new LinkedHashMap<>();
    private static final Map<UUID, Integer> MEMBER_OF = new HashMap<>();
    private static final Map<ResourceKey<Level>, Integer> LEVELS = new HashMap<>();
    private static MinecraftServer server;
    private static String udpSettings = "";
    private static int nextGroup = 1;
    private static boolean rosterDirty;
    private static boolean warnedSvc;
    private static long tickNanos;
    private static long ticks;
    /** Recent cost of the per-tick copy (exponential average, switching on and off excluded), in nanoseconds. */
    private static double tickRecent;

    private VoiceServer() {
    }

    // ------------------------------------------------------------------ state

    /** Whether the voice chat should run: {@code voix.actif}, and Simple Voice Chat's presence in AUTO. */
    public static boolean wanted() {
        return com.caracrea.voice.VoiceGate.serverRuns(CaraCreaConfig.SERVER.voiceMode.get().name(), ModList.get().isLoaded("voicechat"));
    }

    public static boolean active() {
        return active;
    }

    /** Whether the player talks right now (any thread). */
    public static boolean isTalking(UUID player) {
        Member m = MEMBERS.get(player);
        return m != null && talking(m, System.nanoTime());
    }

    private static boolean talking(Member m, long now) {
        long last = m.lastVoice;
        return last != 0 && now - last < TALK_NS && !m.ended;
    }

    /** "UDP", "TCP" or null. */
    @Nullable
    public static String transport(UUID player) {
        Member m = MEMBERS.get(player);
        return m == null ? null : transport(m);
    }

    private static String transport(Member m) {
        UdpVoiceServer.Peer peer = m.peer;
        return peer != null && peer.bound() && m.clientUdp ? "UDP" : "TCP";
    }

    // ------------------------------------------------------------------ lifecycle (server thread)

    public static void serverStarted(MinecraftServer started) {
        server = started;
        VoiceMutes.load(started);
    }

    public static void serverStopping() {
        deactivate(false);
        VoiceMutes.unload();
        GROUPS.clear();
        MEMBER_OF.clear();
        server = null;
    }

    public static void onLogin(ServerPlayer player) {
        VoiceMutes.migrate(player);
        if (!active) {
            CaraCreaNetwork.sendTo(player, CaraCreaNetwork.VoiceConfig.off());
            return;
        }
        join(player);
    }

    public static void onLogout(ServerPlayer player) {
        Member m = MEMBERS.get(player.getUUID());
        if (m != null && !m.simulated) {
            remove(m);
        }
        leave(player.getUUID());
        rosterDirty = true;
    }

    private static void join(ServerPlayer player) {
        Member old = MEMBERS.get(player.getUUID());
        if (old != null) {
            remove(old);
        }
        Member m = create(player.getUUID(), player.getGameProfile().getName(), false);
        m.player = player;
        CaraCreaNetwork.sendTo(player, configFor(m));
        VoiceInbound.install(player);
    }

    private static Member create(UUID uuid, String name, boolean simulated) {
        int voiceId = VOICE_IDS.nextClearBit(1);
        VOICE_IDS.set(voiceId);
        long session;
        do {
            session = RANDOM.nextLong();
        } while (session == 0);
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        Member m = new Member(uuid, name, voiceId, session, key, simulated);
        UdpVoiceServer u = udp;
        if (u != null) {
            m.peer = u.register(session, key, voiceId, m);
        }
        MEMBERS.put(uuid, m);
        ORDER.add(m);
        rosterDirty = true;
        return m;
    }

    private static void remove(Member m) {
        MEMBERS.remove(m.uuid, m);
        ORDER.remove(m);
        VOICE_IDS.clear(m.voiceId);
        UdpVoiceServer u = udp;
        if (u != null && m.peer != null) {
            u.unregister(m.peer);
        }
        if (m.talking) {
            m.talking = false;
            MinecraftForge.EVENT_BUS.post(new VoiceEvent.TalkStop(m.uuid));
        }
        rosterDirty = true;
    }

    private static CaraCreaNetwork.VoiceConfig configFor(Member m) {
        CaraCreaConfig.Server c = CaraCreaConfig.SERVER;
        UdpVoiceServer u = udp;
        int port = u != null && u.running() ? u.port() : 0;
        return new CaraCreaNetwork.VoiceConfig(true, c.voiceRange.get().floatValue(), c.whisperRange.get().floatValue(),
                c.shoutRange.get().floatValue(), c.allowGroups.get(), c.allowRecording.get(), c.bitrate.get(), port,
                c.publicAddress.get().trim(), m.session, m.key, m.voiceId);
    }

    private static String udpSettings(MinecraftServer s) {
        CaraCreaConfig.Server c = CaraCreaConfig.SERVER;
        return c.udp.get() + "|" + c.udpPort.get() + "|" + c.bindAddress.get() + "|" + c.publicAddress.get() + "|" + s.isDedicatedServer();
    }

    private static void activate(MinecraftServer s) {
        active = true;
        startUdp(s);
        for (ServerPlayer player : s.getPlayerList().getPlayers()) {
            join(player);
        }
        CaraCrea.LOGGER.info("Voice chat on ({}){}", udp != null ? "UDP port " + udp.port() + ", game connection as fallback"
                : "game connection only", ModList.get().isLoaded("voicechat") ? " — Simple Voice Chat is installed too" : "");
    }

    private static void deactivate(boolean tell) {
        active = false;
        rules = new VoiceRouting.Rules(false, 48, 24, 96, 1, 1, true, true, false, true, true);
        for (Member m : List.copyOf(ORDER)) {
            remove(m);
            ServerPlayer player = m.player;
            if (tell && player != null && !m.simulated) {
                CaraCreaNetwork.sendTo(player, CaraCreaNetwork.VoiceConfig.off());
            }
        }
        stopUdp();
        GROUPS.clear();
        MEMBER_OF.clear();
    }

    private static void startUdp(MinecraftServer s) {
        stopUdp();
        udpSettings = udpSettings(s);
        CaraCreaConfig.Server c = CaraCreaConfig.SERVER;
        if (!c.udp.get()) {
            return;
        }
        // Single player and LAN: any free port, given to the clients.
        int port = !s.isDedicatedServer() ? 0 : c.udpPort.get() == -1 ? s.getPort() : c.udpPort.get();
        UdpVoiceServer u = new UdpVoiceServer(new UdpVoiceServer.Handler() {
            @Override
            public void onVoice(UdpVoiceServer.Peer from, int flags, int sequence, byte[] buf, int off, int len) {
                if (from.owner instanceof Member m) {
                    relay(m, flags, sequence, buf, off, len);
                }
            }
        });
        try {
            int bound = u.start(c.bindAddress.get().trim(), port);
            udp = u;
            CaraCrea.LOGGER.info("Voice chat: UDP port {} open (open it to UDP on the host)", bound);
        } catch (IOException | RuntimeException e) {
            CaraCrea.LOGGER.warn("Voice chat: could not open UDP port {} ({}); voices go through the game connection", port, e.toString());
        }
    }

    private static void stopUdp() {
        UdpVoiceServer u = udp;
        udp = null;
        if (u != null) {
            u.close();
        }
    }

    // ------------------------------------------------------------------ tick (server thread)

    /** Once per tick: switch on or off, copy where everyone is, talk events, roster. */
    public static void tick(MinecraftServer s) {
        long start = System.nanoTime();
        server = s;
        boolean want = wanted();
        boolean switched = want != active;
        if (want != active) {
            if (want) {
                activate(s);
            } else {
                deactivate(true);
                CaraCrea.LOGGER.info("Voice chat off");
            }
        }
        if (!active) {
            if (!warnedSvc && CaraCreaConfig.SERVER.voiceMode.get() == CaraCreaConfig.VoiceMode.AUTO && ModList.get().isLoaded("voicechat")) {
                warnedSvc = true;
                CaraCrea.LOGGER.info("Simple Voice Chat is installed: the built-in voice chat stays off (voix.actif = AUTO)");
            }
            return;
        }
        if (s.getTickCount() % 20 == 0 && !udpSettings.equals(udpSettings(s))) {
            CaraCrea.LOGGER.info("Voice chat: network settings changed, restarting the voice sessions");
            deactivate(true);
            activate(s);
            switched = true;
        }
        VoiceMutes.tick();
        CaraCreaConfig.Server c = CaraCreaConfig.SERVER;
        VoiceRouting.Rules current = rules;
        float range = c.voiceRange.get().floatValue();
        float whisper = c.whisperRange.get().floatValue();
        float shout = c.shoutRange.get().floatValue();
        float crouch = c.crouchMultiplier.get().floatValue();
        int spectators = c.spectators.get().ordinal();
        if (!current.enabled() || current.range() != range || current.whisperRange() != whisper || current.shoutRange() != shout
                || current.crouch() != crouch
                || current.spectators() != spectators || current.groups() != c.allowGroups.get()
                || current.isolateMatches() != c.isolateMatches.get() || current.teamOnly() != c.matchTeamOnly.get()
                || current.matchSpectatorsTogether() != c.matchSpectatorsTogether.get() || current.downedMuffled() != c.downedMuffled.get()) {
            rules = new VoiceRouting.Rules(true, range, whisper, shout, crouch, spectators, c.allowGroups.get(), c.isolateMatches.get(),
                    c.matchTeamOnly.get(), c.matchSpectatorsTogether.get(), c.downedMuffled.get());
        }
        long now = System.nanoTime();
        snapshot(s, now);
        events(now);
        if (rosterDirty) {
            sendRoster(s);
        }
        long spent = System.nanoTime() - start;
        tickNanos += spent;
        ticks++;
        if (!switched) {
            tickRecent = tickRecent == 0 ? spent : tickRecent + (spent - tickRecent) * 0.02;
        }
    }

    private static void snapshot(MinecraftServer s, long now) {
        VoiceSnapshot snap = SNAPSHOTS.back();
        snap.ensure(ORDER.size());
        int n = 0;
        for (Member m : ORDER) {
            if (m.simulated) {
                UdpVoiceServer.Peer peer = m.peer;
                int flags = TALK | (peer != null && peer.bound() ? REACHABLE : 0) | (m.deafened ? DEAF : 0);
                snap.set(n, m, m.uuid, m.voiceId, levelId(m.simLevel), m.simX, m.simY, m.simZ, flags);
                groupOf(m, snap, n);
                m.slot = n++;
                continue;
            }
            ServerPlayer player = s.getPlayerList().getPlayer(m.uuid);
            if (player == null) {
                continue;
            }
            m.player = player;
            boolean muted = VoiceMutes.isMuted(m.uuid);
            if (muted != m.adminMuted) {
                m.adminMuted = muted;
                rosterDirty = true;
            }
            int flags = REACHABLE;
            if (player.isAlive() && !muted && !m.silenced) {
                flags |= TALK;
            }
            if (m.disabled || m.deafened || m.selfDeafened) {
                flags |= DEAF;
            }
            if (player.isCrouching()) {
                flags |= CROUCH;
            }
            if (player.isSpectator()) {
                flags |= SPECTATOR;
            }
            if (HeralizVoiceCompat.downed(player)) {
                flags |= DOWNED;
            }
            snap.set(n, m, m.uuid, m.voiceId, levelId(player.level().dimension()), player.getX(), player.getEyeY(), player.getZ(), flags);
            HeralizVoiceCompat.match(player, snap, n);
            groupOf(m, snap, n);
            m.slot = n++;
            UdpVoiceServer.Peer peer = m.peer;
            if (peer != null && peer.bound() && now - peer.lastHeard() > UDP_TIMEOUT_NS) {
                // No keep-alive for 8 s: its UDP is gone, back to the game connection until it says hello again.
                peer.unbind();
            }
        }
        snap.count = n;
        snap.tick = s.getTickCount();
        SNAPSHOTS.publish(snap);
    }

    /** Group and imposed range of entry {@code n}. */
    private static void groupOf(Member m, VoiceSnapshot snap, int n) {
        Integer group = MEMBER_OF.get(m.uuid);
        if (group != null) {
            Group g = GROUPS.get(group);
            if (g != null) {
                snap.group[n] = g.id;
                snap.groupType[n] = g.type;
            }
        }
        if (!Float.isNaN(m.range)) {
            snap.range[n] = m.range;
        }
    }

    private static int levelId(@Nullable ResourceKey<Level> level) {
        if (level == null) {
            return 0;
        }
        Integer id = LEVELS.get(level);
        if (id == null) {
            id = LEVELS.size() + 1;
            LEVELS.put(level, id);
            String[] names = Arrays.copyOf(levelNames, id + 1);
            names[id] = level.location().toString();
            levelNames = names;
        }
        return id;
    }

    private static void events(long now) {
        for (Member m : ORDER) {
            boolean talking = talking(m, now);
            if (talking != m.talking) {
                m.talking = talking;
                MinecraftForge.EVENT_BUS.post(talking ? new VoiceEvent.TalkStart(m.uuid, m.lastWhisper, m.lastShout)
                        : new VoiceEvent.TalkStop(m.uuid));
            }
            if (!m.simulated && m.reported) {
                String transport = transport(m);
                if (!transport.equals(m.lastTransport)) {
                    m.lastTransport = transport;
                    MinecraftForge.EVENT_BUS.post(new VoiceEvent.Connected(m.uuid, transport));
                }
            }
        }
    }

    /** Something shown in the roster changed (mutes, from {@link VoiceMutes}). */
    static void rosterChanged() {
        rosterDirty = true;
    }

    /** Everyone gets the groups and voice states (a few bytes per player, only when something changed, at most once a tick). */
    private static void sendRoster(MinecraftServer s) {
        rosterDirty = false;
        if (!active) {
            return;
        }
        List<GroupInfo> groups = new ArrayList<>();
        for (Group group : GROUPS.values()) {
            groups.add(new GroupInfo(group.id, group.name, group.type, !group.password.isEmpty()));
        }
        List<PlayerVoice> players = new ArrayList<>();
        for (Member m : ORDER) {
            players.add(new PlayerVoice(m.uuid, m.voiceId, m.disabled, m.selfMuted, m.adminMuted || m.silenced, MEMBER_OF.getOrDefault(m.uuid, 0)));
        }
        CaraCreaNetwork.sendToAll(new CaraCreaNetwork.VoiceRoster(groups, players));
    }

    // ------------------------------------------------------------------ from the clients (server thread)

    public static void clientState(ServerPlayer player, CaraCreaNetwork.VoiceStateUp state) {
        Member m = MEMBERS.get(player.getUUID());
        if (m == null) {
            return;
        }
        if (m.disabled != state.disabled() || m.selfMuted != state.muted()) {
            rosterDirty = true;
        }
        m.disabled = state.disabled();
        m.selfMuted = state.muted();
        m.selfDeafened = state.deafened();
        m.clientUdp = state.udp();
        m.reported = true;
        UdpVoiceServer.Peer peer = m.peer;
        if (!state.udp() && peer != null) {
            // Its UDP does not work (or it chose the game connection): stop sending there until it says hello again.
            peer.unbind();
        }
    }

    public static void groupAction(ServerPlayer player, GroupAction action) {
        if (!active) {
            return;
        }
        UUID uuid = player.getUUID();
        switch (action.action()) {
            case GroupAction.CREATE -> {
                if (!CaraCreaConfig.SERVER.allowGroups.get()) {
                    return;
                }
                String name = action.name().trim();
                if (name.isEmpty() || name.length() > 24) {
                    return;
                }
                leave(uuid);
                Group group = new Group(nextGroup++, name, action.password(), Math.max(NORMAL, Math.min(ISOLATED, action.type())));
                GROUPS.put(group.id, group);
                group.members.add(uuid);
                MEMBER_OF.put(uuid, group.id);
            }
            case GroupAction.JOIN -> {
                Group group = GROUPS.get(action.group());
                if (group == null || !CaraCreaConfig.SERVER.allowGroups.get()) {
                    return;
                }
                if (!group.password.isEmpty() && !group.password.equals(action.password())) {
                    player.displayClientMessage(Component.translatable("caracrea.voice.group.wrong_password"), true);
                    return;
                }
                leave(uuid);
                group.members.add(uuid);
                MEMBER_OF.put(uuid, group.id);
            }
            case GroupAction.LEAVE -> leave(uuid);
            default -> {
                return;
            }
        }
        rosterDirty = true;
    }

    private static void leave(UUID uuid) {
        Integer id = MEMBER_OF.remove(uuid);
        if (id != null) {
            Group group = GROUPS.get(id);
            if (group != null) {
                group.members.remove(uuid);
                if (group.members.isEmpty()) {
                    GROUPS.remove(id);
                }
            }
        }
    }

    // ------------------------------------------------------------------ relay (voice threads)

    /** A frame from the game connection (network thread of the speaker's connection, see {@link VoiceInbound}). */
    public static void relayTcp(UUID speaker, int sequence, int flags, byte[] data, int off, int len) {
        Member m = MEMBERS.get(speaker);
        if (m == null || len <= 0 || len > VoiceProtocol.MAX_OPUS || !m.tcpLimit.take()) {
            return;
        }
        relay(m, flags & VoiceProtocol.UP_FLAGS, sequence, data, off, len);
    }

    /** Routes a frame and sends it to each listener over UDP, or over the game connection when its UDP does not work. */
    static void relay(Member from, int flags, int sequence, byte[] buf, int off, int len) {
        VoiceRouting.Rules r = rules;
        if (!r.enabled()) {
            return;
        }
        VoiceSnapshot s = SNAPSHOTS.current();
        int index = s.indexOf(from, from.slot);
        if (index < 0 || (s.flags[index] & TALK) == 0) {
            return;
        }
        // Chuchoter, parler ou crier : choisi par le joueur, porté par les drapeaux de la trame.
        int mode = VoiceProtocol.mode(flags);
        from.lastVoice = System.nanoTime();
        from.ended = (flags & VoiceProtocol.F_END) != 0;
        from.lastWhisper = mode == VoiceProtocol.MODE_WHISPER;
        from.lastShout = mode == VoiceProtocol.MODE_SHOUT;
        RELAYED.incrementAndGet();
        VoiceRouting.Recipients out = RECIPIENTS.get();
        VoiceRouting.route(s, r, index, mode, out);
        VoiceRouter[] hooks = routers;
        if (hooks.length > 0) {
            ApiView view = API_VIEW.get();
            view.set(s, index, mode, sequence, out);
            for (VoiceRouter hook : hooks) {
                try {
                    hook.route(view, view);
                } catch (RuntimeException e) {
                    removeRouter(hook);
                    CaraCrea.LOGGER.error("Voice chat: a routing hook failed and was removed", e);
                }
            }
            view.clear();
        }
        int end = flags & VoiceProtocol.F_END;
        UdpVoiceServer u = udp;
        byte[] copy = null;
        for (int i = 0; i < out.count(); i++) {
            int l = out.listener(i);
            Member to = (Member) s.member[l];
            int f = out.flags(i) | end;
            int dx = VoiceProtocol.quantize(s.x[index] - s.x[l]);
            int dy = VoiceProtocol.quantize(s.y[index] - s.y[l]);
            int dz = VoiceProtocol.quantize(s.z[index] - s.z[l]);
            if (u != null && to.peer != null && to.clientUdp && u.sendVoice(to.peer, from.voiceId, f, sequence, dx, dy, dz, buf, off, len)) {
                UDP_OUT.incrementAndGet();
                continue;
            }
            if (to.simulated) {
                continue;
            }
            if (copy == null) {
                copy = Arrays.copyOfRange(buf, off, off + len);
            }
            sendTcp(to.player, new CaraCreaNetwork.VoiceDown(from.voiceId, sequence, f, (short) dx, (short) dy, (short) dz, copy));
        }
        if (ECHO && !from.simulated) {
            int f = VoiceProtocol.flag(mode) | end;
            if (u == null || from.peer == null || !from.clientUdp || !u.sendVoice(from.peer, from.voiceId, f, sequence, 0, 0, 0, buf, off, len)) {
                sendTcp(from.player, new CaraCreaNetwork.VoiceDown(from.voiceId, sequence, f, (short) 0, (short) 0, (short) 0,
                        copy != null ? copy : Arrays.copyOfRange(buf, off, off + len)));
            }
        }
    }

    private static void sendTcp(@Nullable ServerPlayer player, CaraCreaNetwork.VoiceDown message) {
        if (player == null) {
            return;
        }
        try {
            Packet<?> packet = CaraCreaNetwork.CHANNEL.toVanillaPacket(message, NetworkDirection.PLAY_TO_CLIENT);
            player.connection.send(packet);
            TCP_OUT.incrementAndGet();
        } catch (RuntimeException e) {
            // The listener is disconnecting: nothing to do.
        }
    }

    // ------------------------------------------------------------------ API

    /** Per-player overrides of the API ({@code range} NEGATIVE_INFINITY = unchanged, null = unchanged). */
    public static void override(UUID player, float range, @Nullable Boolean silenced, @Nullable Boolean deafened) {
        Member m = MEMBERS.get(player);
        if (m == null) {
            return;
        }
        if (range != Float.NEGATIVE_INFINITY) {
            m.range = range;
        }
        if (silenced != null && m.silenced != silenced) {
            m.silenced = silenced;
            rosterDirty = true;
        }
        if (deafened != null) {
            m.deafened = deafened;
        }
    }

    public static synchronized void addRouter(VoiceRouter router) {
        VoiceRouter[] next = Arrays.copyOf(routers, routers.length + 1);
        next[next.length - 1] = Objects.requireNonNull(router);
        routers = next;
    }

    public static synchronized void removeRouter(VoiceRouter router) {
        List<VoiceRouter> list = new ArrayList<>(Arrays.asList(routers));
        if (list.remove(router)) {
            routers = list.toArray(new VoiceRouter[0]);
        }
    }

    /** The frame and its listeners as the routing hooks see them; one per voice thread, reused. */
    private static final class ApiView implements VoiceFrame, VoiceRecipients {
        private VoiceSnapshot s;
        private int speaker;
        private int mode;
        private int sequence;
        private VoiceRouting.Recipients out;

        void set(VoiceSnapshot snapshot, int index, int voiceMode, int frame, VoiceRouting.Recipients recipients) {
            s = snapshot;
            speaker = index;
            mode = voiceMode;
            sequence = frame;
            out = recipients;
        }

        void clear() {
            s = null;
            out = null;
        }

        @Override
        public UUID speaker() {
            return s.uuid[speaker];
        }

        @Override
        public boolean whisper() {
            return mode == VoiceProtocol.MODE_WHISPER;
        }

        @Override
        public boolean shout() {
            return mode == VoiceProtocol.MODE_SHOUT;
        }

        @Override
        public int sequence() {
            return sequence;
        }

        @Override
        public String dimension() {
            String[] names = levelNames;
            int id = s.level[speaker];
            return id > 0 && id < names.length ? names[id] : "";
        }

        @Override
        public double x() {
            return s.x[speaker];
        }

        @Override
        public double y() {
            return s.y[speaker];
        }

        @Override
        public double z() {
            return s.z[speaker];
        }

        @Override
        public boolean add(UUID listener, int effects) {
            int l = s.indexOf(listener);
            if (l < 0 || l == speaker || (s.flags[l] & DEAF) != 0 || (s.flags[l] & REACHABLE) == 0) {
                return false;
            }
            out.add(l, (effects & (NO_POSITION | RADIO | MUFFLED)) | VoiceProtocol.flag(mode));
            return true;
        }

        @Override
        public void remove(UUID listener) {
            out.remove(s.indexOf(listener));
        }

        @Override
        public boolean contains(UUID listener) {
            return out.contains(s.indexOf(listener));
        }

        @Override
        public int size() {
            return out.count();
        }

        @Override
        public UUID get(int i) {
            return s.uuid[out.listener(i)];
        }
    }

    // ------------------------------------------------------------------ information (commands)

    /** One player's voice connection, for {@code /voix info}. */
    public record Info(String name, String transport, int rttMs, int lossPermille, int bufferMs, boolean talking, String group,
                       boolean disabled, boolean selfMuted, boolean adminMuted, long framesIn, long framesOut) {
    }

    @Nullable
    public static Info info(UUID player) {
        Member m = MEMBERS.get(player);
        if (m == null) {
            return null;
        }
        UdpVoiceServer.Peer peer = m.peer;
        Integer id = MEMBER_OF.get(player);
        Group group = id == null ? null : GROUPS.get(id);
        return new Info(m.name, transport(m), peer == null ? -1 : peer.rttMs(), peer == null ? 0 : peer.lossPermille(),
                peer == null ? 0 : peer.bufferMs(), talking(m, System.nanoTime()), group == null ? "" : group.name, m.disabled,
                m.selfMuted, m.adminMuted, peer == null ? 0 : peer.framesIn(), peer == null ? 0 : peer.framesOut());
    }

    /** Global figures for {@code /voix stats}. */
    public record Stats(boolean active, int udpPort, int sessions, int udpBound, UdpVoiceServer.Stats udp, long relayed, long tcpIn,
                        long tcpOut, long udpOut, double tickMicros, double tickRecentMicros, int groups) {
    }

    public static Stats stats() {
        UdpVoiceServer u = udp;
        int bound = 0;
        for (Member m : MEMBERS.values()) {
            if (m.peer != null && m.peer.bound() && m.clientUdp) {
                bound++;
            }
        }
        return new Stats(active, u == null ? 0 : u.port(), MEMBERS.size(), bound, u == null ? null : u.stats(), RELAYED.get(), INBOUND.get(),
                TCP_OUT.get(), UDP_OUT.get(), ticks == 0 ? 0 : tickNanos / 1000.0 / ticks, tickRecent / 1000.0, GROUPS.size());
    }

    // ------------------------------------------------------------------ test bench (development only)

    /** What a simulated peer needs to connect over UDP. */
    public record SimulatedPeer(UUID uuid, int voiceId, long session, byte[] key, int port) {
    }

    /** Adds a simulated peer standing at a position (-Dcaracrea.voice.dev=true only). Server thread. */
    @Nullable
    public static SimulatedPeer devAddPeer(UUID uuid, String name, ResourceKey<Level> level, double x, double y, double z) {
        UdpVoiceServer u = udp;
        if (!DEV || !active || u == null) {
            return null;
        }
        Member old = MEMBERS.get(uuid);
        if (old != null) {
            remove(old);
        }
        Member m = create(uuid, name, true);
        m.simLevel = level;
        m.simX = x;
        m.simY = y;
        m.simZ = z;
        m.clientUdp = true;
        return new SimulatedPeer(uuid, m.voiceId, m.session, m.key.clone(), u.port());
    }

    public static void devMovePeer(UUID uuid, double x, double y, double z) {
        Member m = MEMBERS.get(uuid);
        if (m != null && m.simulated) {
            m.simX = x;
            m.simY = y;
            m.simZ = z;
        }
    }

    public static void devRemovePeer(UUID uuid) {
        Member m = MEMBERS.get(uuid);
        if (m != null && m.simulated) {
            remove(m);
        }
    }

    /** Puts a member (the simulated peer) into the voice group of another player. Server thread. */
    public static boolean devJoinGroupOf(UUID uuid, UUID player) {
        Integer group = MEMBER_OF.get(player);
        if (group == null) {
            return false;
        }
        devSetGroup(uuid, group);
        return true;
    }

    public static void devSetGroup(UUID uuid, int groupId) {
        leave(uuid);
        Group group = GROUPS.get(groupId);
        if (group != null) {
            group.members.add(uuid);
            MEMBER_OF.put(uuid, groupId);
            rosterDirty = true;
        }
    }
}
