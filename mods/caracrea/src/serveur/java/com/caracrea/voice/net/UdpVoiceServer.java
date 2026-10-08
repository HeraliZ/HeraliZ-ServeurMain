package com.caracrea.voice.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static com.caracrea.voice.net.VoiceProtocol.*;

/**
 * Server end of the dedicated voice channel: one UDP port, one thread that receives, authenticates and hands each
 * voice frame to the {@link Handler} (which routes it and sends it on with {@link #sendVoice}, from the same thread),
 * and answers the handshake and keep-alives. Plain Java, no Minecraft: it runs the same on Forge and on Mohist, never
 * on the game thread, and is tested on its own.
 *
 * <p>Nothing is ever sent back to a packet that does not authenticate (no reflection or amplification), and the
 * answers are shorter than the questions. Steady state allocates nothing per packet, except the sender address that the
 * JDK rebuilds when packets of different players interleave.
 */
public final class UdpVoiceServer implements AutoCloseable {
    /** What the server does with authenticated traffic. Called on the receiving thread. */
    public interface Handler {
        /** A frame of voice from {@code from}: {@code buf[off, off + len)} is the Opus packet (valid during the call). */
        void onVoice(Peer from, int flags, int sequence, byte[] buf, int off, int len);

        /** The peer's address was bound by a valid HELLO ({@code first}: the first time for this session). */
        default void onBound(Peer peer, boolean first) {
        }
    }

    /** One player's voice session: key, counters, address, limits and statistics. */
    public static final class Peer {
        public final long session;
        public final int voiceId;
        /** The game side's object for this player (opaque here). */
        public final Object owner;
        final ChaCha20Poly1305.Key key;
        final ReplayWindow replay = new ReplayWindow();
        final AtomicLong counter = new AtomicLong();
        final TokenBucket voiceLimit = new TokenBucket(60, 20);
        final TokenBucket controlLimit = new TokenBucket(10, 10);
        volatile InetSocketAddress address;
        volatile boolean bound;
        volatile long lastHeard;
        /** As reported by the client in its pings. */
        volatile int rttMs = -1;
        volatile int lossPermille;
        volatile int bufferMs;
        volatile long framesIn;
        volatile long bytesIn;
        final AtomicLong framesOut = new AtomicLong();
        final AtomicLong bytesOut = new AtomicLong();

        Peer(long session, byte[] key, int voiceId, Object owner) {
            this.session = session;
            this.key = new ChaCha20Poly1305.Key(key);
            this.voiceId = voiceId;
            this.owner = owner;
        }

        /** Whether the client proved it can reach us over UDP (and we can answer). */
        public boolean bound() {
            return bound;
        }

        public void unbind() {
            bound = false;
        }

        public InetSocketAddress address() {
            return address;
        }

        /** {@link System#nanoTime()} of the latest authenticated packet, 0 if none. */
        public long lastHeard() {
            return lastHeard;
        }

        public int rttMs() {
            return rttMs;
        }

        public int lossPermille() {
            return lossPermille;
        }

        public int bufferMs() {
            return bufferMs;
        }

        public long framesIn() {
            return framesIn;
        }

        public long bytesIn() {
            return bytesIn;
        }

        public long framesOut() {
            return framesOut.get();
        }

        public long bytesOut() {
            return bytesOut.get();
        }
    }

    /** Scratch of a sending thread (the receiving thread, or a network thread relaying a fallback speaker). */
    private static final class Scratch {
        final byte[] buf = new byte[MAX_DATAGRAM + 64];
        final ByteBuffer out = ByteBuffer.allocateDirect(MAX_DATAGRAM + 64);
        final ChaCha20Poly1305 aead = new ChaCha20Poly1305();
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private final Handler handler;
    private final PeerMap<Peer> peers = new PeerMap<>();
    private volatile DatagramChannel channel;
    private volatile Thread thread;
    private volatile boolean running;
    private int port;

    // Statistics (the receiving thread writes the inbound ones).
    private volatile long packetsIn;
    private volatile long bytesIn;
    private volatile long invalid;
    private volatile long replayed;
    private volatile long limited;
    private volatile long relayNanos;
    private volatile long relayed;
    private final AtomicLong packetsOut = new AtomicLong();
    private final AtomicLong bytesOut = new AtomicLong();

    public UdpVoiceServer(Handler handler) {
        this.handler = handler;
    }

    /**
     * Binds the port ({@code 0}: any free port) on {@code host} (empty: every interface) and starts the thread.
     * Returns the port actually bound.
     */
    public synchronized int start(String host, int port) throws IOException {
        if (running) {
            return this.port;
        }
        DatagramChannel ch = DatagramChannel.open();
        try {
            ch.setOption(StandardSocketOptions.SO_RCVBUF, 1 << 20);
            ch.setOption(StandardSocketOptions.SO_SNDBUF, 1 << 20);
            ch.bind(host == null || host.isBlank() ? new InetSocketAddress(port) : new InetSocketAddress(host, port));
            ch.configureBlocking(true);
        } catch (IOException | RuntimeException e) {
            ch.close();
            throw e;
        }
        channel = ch;
        this.port = ((InetSocketAddress) ch.getLocalAddress()).getPort();
        running = true;
        Thread t = new Thread(this::run, "CaraCrea voice UDP");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY + 1);
        thread = t;
        t.start();
        return this.port;
    }

    public int port() {
        return port;
    }

    public boolean running() {
        return running;
    }

    /** The receiving thread (CPU measurements). */
    public Thread thread() {
        return thread;
    }

    @Override
    public synchronized void close() {
        running = false;
        DatagramChannel ch = channel;
        if (ch != null) {
            try {
                ch.close();
            } catch (IOException ignored) {
                // Closing anyway.
            }
        }
        Thread t = thread;
        if (t != null && t != Thread.currentThread()) {
            try {
                t.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ------------------------------------------------------------------ sessions

    /** Opens a session (the key and id were given to the client over the game connection). */
    public Peer register(long session, byte[] key, int voiceId, Object owner) {
        Peer peer = new Peer(session, key, voiceId, owner);
        peers.put(session, peer);
        return peer;
    }

    public void unregister(Peer peer) {
        if (peer != null) {
            peer.bound = false;
            peers.remove(peer.session);
        }
    }

    public int sessions() {
        return peers.size();
    }

    public void forEachPeer(Consumer<Peer> action) {
        peers.forEach(action);
    }

    // ------------------------------------------------------------------ receiving thread

    private void run() {
        DatagramChannel ch = channel;
        ByteBuffer in = ByteBuffer.allocateDirect(2048);
        byte[] buf = new byte[2048];
        ChaCha20Poly1305 aead = new ChaCha20Poly1305();
        while (running) {
            SocketAddress from;
            try {
                in.clear();
                from = ch.receive(in);
            } catch (ClosedChannelException e) {
                break;
            } catch (IOException e) {
                // Windows reports an ICMP "port unreachable" of an earlier send as an error here: keep going.
                continue;
            }
            if (!(from instanceof InetSocketAddress source)) {
                continue;
            }
            in.flip();
            int n = in.remaining();
            packetsIn++;
            bytesIn += n;
            if (n < UP_HEADER + TAG || n > MAX_DATAGRAM) {
                invalid++;
                continue;
            }
            in.get(buf, 0, n);
            try {
                handle(source, buf, n, aead);
            } catch (RuntimeException e) {
                invalid++;
            }
        }
        running = false;
    }

    private void handle(InetSocketAddress source, byte[] buf, int n, ChaCha20Poly1305 aead) {
        if ((buf[0] & 0xff) != MAGIC) {
            invalid++;
            return;
        }
        int type = buf[1] & 0xff;
        Peer peer = peers.get(getLong(buf, 2));
        if (peer == null) {
            invalid++;
            return;
        }
        long counter = getLong(buf, 10);
        if (!peer.replay.check(counter)) {
            replayed++;
            return;
        }
        int length = n - UP_HEADER - TAG;
        if (!aead.open(peer.key, NONCE_UP, counter, buf, 0, UP_HEADER, buf, UP_HEADER, length)) {
            invalid++;
            return;
        }
        peer.replay.accept(counter);
        long now = System.nanoTime();
        peer.lastHeard = now;
        peer.framesIn++;
        peer.bytesIn += n;
        switch (type) {
            case HELLO -> {
                if (!peer.controlLimit.take(now)) {
                    limited++;
                    return;
                }
                boolean first = peer.address == null;
                peer.address = source;
                peer.bound = true;
                reply(peer, HELLO_ACK, length >= 5 ? getInt(buf, UP_HEADER + 1) : 0, peer.voiceId);
                handler.onBound(peer, first);
            }
            case PING -> {
                if (!peer.controlLimit.take(now) || !peer.bound) {
                    limited++;
                    return;
                }
                migrate(peer, source);
                if (length >= 10) {
                    peer.rttMs = getUnsignedShort(buf, UP_HEADER + 4);
                    peer.lossPermille = getUnsignedShort(buf, UP_HEADER + 6);
                    peer.bufferMs = getUnsignedShort(buf, UP_HEADER + 8);
                }
                reply(peer, PONG, length >= 4 ? getInt(buf, UP_HEADER) : 0, -1);
            }
            case VOICE_UP -> {
                if (!peer.bound || length <= VOICE_UP_FIXED || length - VOICE_UP_FIXED > MAX_OPUS) {
                    invalid++;
                    return;
                }
                if (!peer.voiceLimit.take(now)) {
                    limited++;
                    return;
                }
                migrate(peer, source);
                int flags = buf[UP_HEADER] & UP_FLAGS;
                int sequence = getInt(buf, UP_HEADER + 1);
                long start = System.nanoTime();
                handler.onVoice(peer, flags, sequence, buf, UP_HEADER + VOICE_UP_FIXED, length - VOICE_UP_FIXED);
                relayNanos += System.nanoTime() - start;
                relayed++;
            }
            case BYE -> peer.bound = false;
            default -> invalid++;
        }
    }

    /** NAT rebinding: an authenticated, never-seen packet from a new address moves the session there. */
    private static void migrate(Peer peer, InetSocketAddress source) {
        if (!source.equals(peer.address)) {
            peer.address = source;
        }
    }

    private void reply(Peer peer, int type, int clientTime, int voiceId) {
        Scratch s = SCRATCH.get();
        byte[] b = s.buf;
        b[0] = (byte) MAGIC;
        b[1] = (byte) type;
        long counter = peer.counter.incrementAndGet();
        putLong(b, 2, counter);
        int p = DOWN_HEADER;
        putInt(b, p, clientTime);
        p += 4;
        if (voiceId >= 0) {
            putShort(b, p, voiceId);
            putShort(b, p + 2, 0);
            p += 4;
        }
        s.aead.seal(peer.key, NONCE_DOWN, counter, b, 0, DOWN_HEADER, b, DOWN_HEADER, p - DOWN_HEADER);
        transmit(s, peer, p + TAG);
    }

    // ------------------------------------------------------------------ sending (any thread)

    /**
     * Sends a frame of {@code speaker}'s voice to {@code to} if its UDP session is bound; false otherwise (the caller
     * then uses the game connection). {@code dx, dy, dz}: speaker minus listener, already quantized (1/8 block).
     */
    public boolean sendVoice(Peer to, int speaker, int flags, int sequence, int dx, int dy, int dz, byte[] opus, int off, int len) {
        if (!to.bound || to.address == null || len > MAX_OPUS || !running) {
            return false;
        }
        Scratch s = SCRATCH.get();
        byte[] b = s.buf;
        b[0] = (byte) MAGIC;
        b[1] = (byte) VOICE_DOWN;
        long counter = to.counter.incrementAndGet();
        putLong(b, 2, counter);
        int p = DOWN_HEADER;
        putShort(b, p, speaker);
        b[p + 2] = (byte) flags;
        putInt(b, p + 3, sequence);
        putShort(b, p + 7, dx);
        putShort(b, p + 9, dy);
        putShort(b, p + 11, dz);
        p += VOICE_DOWN_FIXED;
        System.arraycopy(opus, off, b, p, len);
        p += len;
        s.aead.seal(to.key, NONCE_DOWN, counter, b, 0, DOWN_HEADER, b, DOWN_HEADER, p - DOWN_HEADER);
        return transmit(s, to, p + TAG);
    }

    private boolean transmit(Scratch s, Peer to, int length) {
        InetSocketAddress address = to.address;
        DatagramChannel ch = channel;
        if (address == null || ch == null) {
            return false;
        }
        s.out.clear();
        s.out.put(s.buf, 0, length);
        s.out.flip();
        try {
            ch.send(s.out, address);
        } catch (IOException e) {
            return false;
        }
        packetsOut.incrementAndGet();
        bytesOut.addAndGet(length);
        to.framesOut.incrementAndGet();
        to.bytesOut.addAndGet(length);
        return true;
    }

    // ------------------------------------------------------------------ statistics

    /** Counters since start: packets and bytes in and out, rejected packets, relays and their total time. */
    public record Stats(long packetsIn, long bytesIn, long packetsOut, long bytesOut, long invalid, long replayed, long limited,
                        long relayed, long relayNanos, int sessions) {
    }

    public Stats stats() {
        return new Stats(packetsIn, bytesIn, packetsOut.get(), bytesOut.get(), invalid, replayed, limited, relayed, relayNanos,
                peers.size());
    }
}
