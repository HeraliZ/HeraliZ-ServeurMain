package com.caracrea.voice.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.atomic.AtomicLong;

import static com.caracrea.voice.net.VoiceProtocol.*;

/**
 * Client end of the dedicated voice channel: a UDP socket "connected" to the server (only its packets come in), a
 * receiving thread (decrypt, hand voices to the {@link Listener}) and a small service thread (handshake, keep-alive,
 * timeouts). If the server cannot be reached over UDP — blocked port, strict firewall — the state goes to
 * {@link State#FALLBACK} and the caller sends its voice through the game connection; UDP is tried again every
 * {@link Timings#retryMs}, and comes back by itself as soon as the server answers. Plain Java, tested on its own.
 */
public final class UdpVoiceClient implements AutoCloseable {
    public enum State { CONNECTING, CONNECTED, FALLBACK, CLOSED }

    public interface Listener {
        /** Receiving thread: a frame of someone's voice; {@code buf[off, off + len)} is valid during the call. */
        void onVoice(int speaker, int flags, int sequence, int dx, int dy, int dz, byte[] buf, int off, int len);

        /** The state changed (from either thread). */
        default void onState(State state) {
        }
    }

    /** Delays of the handshake and keep-alive (shorter in tests). */
    public record Timings(int helloMs, int connectTimeoutMs, int pingMs, int lostMs, int retryMs) {
        public static final Timings DEFAULT = new Timings(250, 4000, 1000, 4000, 15000);
    }

    private static final class Scratch {
        final byte[] buf = new byte[MAX_DATAGRAM + 64];
        final ByteBuffer out = ByteBuffer.allocateDirect(MAX_DATAGRAM + 64);
        final ChaCha20Poly1305 aead = new ChaCha20Poly1305();
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private final InetSocketAddress server;
    private final long session;
    private final ChaCha20Poly1305.Key key;
    private final Listener listener;
    private final Timings timings;
    private final AtomicLong counter;
    private final ReplayWindow replay = new ReplayWindow();
    private final long epoch = System.nanoTime();
    private volatile DatagramChannel channel;
    private volatile State state = State.CONNECTING;
    private volatile boolean running;
    private Thread receiver;
    private Thread service;
    private volatile long lastReceived;
    private volatile int rttMs = -1;
    private volatile int voiceId = -1;
    private volatile int reportLoss;
    private volatile int reportBuffer;
    // Statistics.
    private final AtomicLong framesSent = new AtomicLong();
    private final AtomicLong bytesSent = new AtomicLong();
    private volatile long framesReceived;
    private volatile long bytesReceived;
    private volatile long rejected;

    public UdpVoiceClient(InetSocketAddress server, long session, byte[] key, Listener listener) {
        this(server, session, key, listener, Timings.DEFAULT);
    }

    public UdpVoiceClient(InetSocketAddress server, long session, byte[] key, Listener listener, Timings timings) {
        this(server, session, key, listener, timings, new AtomicLong());
    }

    /**
     * {@code counter}: the packet counter of this session. A client that reconnects with the same session (the player
     * switched back from the game connection) must go on from where the previous one stopped, or the server would take
     * its packets for replays.
     */
    public UdpVoiceClient(InetSocketAddress server, long session, byte[] key, Listener listener, Timings timings, AtomicLong counter) {
        this.server = server;
        this.session = session;
        this.key = new ChaCha20Poly1305.Key(key);
        this.listener = listener;
        this.timings = timings;
        this.counter = counter;
    }

    /** Opens the socket and starts the threads; an unresolvable or unusable address goes straight to fallback. */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        try {
            if (server.isUnresolved()) {
                throw new IOException("unresolved " + server);
            }
            DatagramChannel ch = DatagramChannel.open();
            ch.setOption(StandardSocketOptions.SO_RCVBUF, 1 << 19);
            ch.connect(server);
            ch.configureBlocking(true);
            channel = ch;
        } catch (IOException | RuntimeException e) {
            setState(State.FALLBACK);
            return;
        }
        receiver = new Thread(this::receive, "CaraCrea voice UDP");
        receiver.setDaemon(true);
        receiver.setPriority(Thread.NORM_PRIORITY + 1);
        receiver.start();
        service = new Thread(this::service, "CaraCrea voice UDP service");
        service.setDaemon(true);
        service.start();
    }

    public State state() {
        return state;
    }

    /** Whether voice frames should go through UDP right now. */
    public boolean connected() {
        return state == State.CONNECTED;
    }

    /** Round trip measured by the keep-alive, -1 before the first answer. */
    public int rttMs() {
        return rttMs;
    }

    public int voiceId() {
        return voiceId;
    }

    /** What the next pings report to the server (for its statistics): loss of the voices heard and buffer delay. */
    public void report(int lossPermille, int bufferMs) {
        reportLoss = Math.max(0, Math.min(65535, lossPermille));
        reportBuffer = Math.max(0, Math.min(65535, bufferMs));
    }

    public long framesSent() {
        return framesSent.get();
    }

    public long bytesSent() {
        return bytesSent.get();
    }

    public long framesReceived() {
        return framesReceived;
    }

    public long bytesReceived() {
        return bytesReceived;
    }

    public long rejected() {
        return rejected;
    }

    @Override
    public void close() {
        if (!running) {
            return;
        }
        if (state == State.CONNECTED) {
            sendControl(BYE, new byte[0], 0);
        }
        running = false;
        setState(State.CLOSED);
        DatagramChannel ch = channel;
        if (ch != null) {
            try {
                ch.close();
            } catch (IOException ignored) {
                // Closing anyway.
            }
        }
        if (service != null) {
            service.interrupt();
        }
    }

    // ------------------------------------------------------------------ sending

    /** Sends one frame of voice (any thread, normally the microphone's). False when UDP is not usable. */
    public boolean sendVoice(int flags, int sequence, byte[] opus, int off, int len) {
        if (state != State.CONNECTED || len > MAX_OPUS || len <= 0) {
            return false;
        }
        Scratch s = SCRATCH.get();
        byte[] b = s.buf;
        int p = header(b, VOICE_UP);
        b[p] = (byte) (flags & UP_FLAGS);
        putInt(b, p + 1, sequence);
        p += VOICE_UP_FIXED;
        System.arraycopy(opus, off, b, p, len);
        p += len;
        return seal(s, p);
    }

    private int header(byte[] b, int type) {
        b[0] = (byte) MAGIC;
        b[1] = (byte) type;
        putLong(b, 2, session);
        return UP_HEADER;
    }

    private boolean sendControl(int type, byte[] payload, int length) {
        Scratch s = SCRATCH.get();
        byte[] b = s.buf;
        int p = header(b, type);
        System.arraycopy(payload, 0, b, p, length);
        return seal(s, p + length);
    }

    private boolean seal(Scratch s, int end) {
        DatagramChannel ch = channel;
        if (ch == null || !running) {
            return false;
        }
        long c = counter.incrementAndGet();
        putLong(s.buf, 10, c);
        s.aead.seal(key, NONCE_UP, c, s.buf, 0, UP_HEADER, s.buf, UP_HEADER, end - UP_HEADER);
        int length = end + TAG;
        s.out.clear();
        s.out.put(s.buf, 0, length);
        s.out.flip();
        try {
            ch.write(s.out);
        } catch (IOException e) {
            return false;
        }
        framesSent.incrementAndGet();
        bytesSent.addAndGet(length);
        return true;
    }

    private int clock() {
        return (int) ((System.nanoTime() - epoch) / 1_000_000L);
    }

    // ------------------------------------------------------------------ service thread

    private void service() {
        byte[] hello = new byte[HELLO_PAYLOAD];
        byte[] ping = new byte[10];
        long started = System.nanoTime();
        long lastHello = 0;
        long lastPing = 0;
        long probeUntil = 0;
        long nextRetry = 0;
        while (running) {
            long now = System.nanoTime();
            State current = state;
            if (current == State.CONNECTING || current == State.FALLBACK && now < probeUntil) {
                if (now - lastHello >= timings.helloMs() * 1_000_000L) {
                    hello[0] = (byte) VERSION;
                    putInt(hello, 1, clock());
                    sendControl(HELLO, hello, hello.length);
                    lastHello = now;
                }
                if (current == State.CONNECTING && now - started >= timings.connectTimeoutMs() * 1_000_000L) {
                    setState(State.FALLBACK);
                    nextRetry = now + timings.retryMs() * 1_000_000L;
                }
            } else if (current == State.CONNECTED) {
                if (now - lastPing >= timings.pingMs() * 1_000_000L) {
                    putInt(ping, 0, clock());
                    putShort(ping, 4, Math.max(0, Math.min(65535, rttMs)));
                    putShort(ping, 6, reportLoss);
                    putShort(ping, 8, reportBuffer);
                    sendControl(PING, ping, ping.length);
                    lastPing = now;
                }
                if (now - lastReceived >= timings.lostMs() * 1_000_000L) {
                    setState(State.FALLBACK);
                    nextRetry = now + timings.retryMs() * 1_000_000L;
                }
            } else if (current == State.FALLBACK && now >= nextRetry && channel != null) {
                // Try UDP again for a couple of seconds.
                probeUntil = now + Math.max(2000, timings.connectTimeoutMs() / 2) * 1_000_000L;
                nextRetry = now + timings.retryMs() * 1_000_000L;
                lastHello = 0;
            }
            try {
                Thread.sleep(Math.min(50, Math.max(5, timings.helloMs() / 5)));
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private synchronized void setState(State next) {
        if (state == next || state == State.CLOSED) {
            return;
        }
        state = next;
        try {
            listener.onState(next);
        } catch (RuntimeException ignored) {
            // The listener's problem.
        }
    }

    // ------------------------------------------------------------------ receiving thread

    private void receive() {
        DatagramChannel ch = channel;
        ByteBuffer in = ByteBuffer.allocateDirect(2048);
        byte[] buf = new byte[2048];
        ChaCha20Poly1305 aead = new ChaCha20Poly1305();
        while (running) {
            int n;
            try {
                in.clear();
                n = ch.read(in);
            } catch (PortUnreachableException e) {
                // Nothing listens on the server's voice port (ICMP): the service thread will fall back.
                pause();
                continue;
            } catch (ClosedChannelException e) {
                break;
            } catch (IOException e) {
                pause();
                continue;
            }
            if (n < DOWN_HEADER + TAG || n > MAX_DATAGRAM) {
                rejected++;
                continue;
            }
            in.flip();
            in.get(buf, 0, n);
            try {
                handle(buf, n, aead);
            } catch (RuntimeException e) {
                rejected++;
            }
        }
    }

    private void pause() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handle(byte[] buf, int n, ChaCha20Poly1305 aead) {
        if ((buf[0] & 0xff) != MAGIC) {
            rejected++;
            return;
        }
        int type = buf[1] & 0xff;
        long c = getLong(buf, 2);
        if (!replay.check(c)) {
            rejected++;
            return;
        }
        int length = n - DOWN_HEADER - TAG;
        if (!aead.open(key, NONCE_DOWN, c, buf, 0, DOWN_HEADER, buf, DOWN_HEADER, length)) {
            rejected++;
            return;
        }
        replay.accept(c);
        lastReceived = System.nanoTime();
        framesReceived++;
        bytesReceived += n;
        switch (type) {
            case HELLO_ACK -> {
                if (length >= 6) {
                    measure(getInt(buf, DOWN_HEADER));
                    voiceId = getUnsignedShort(buf, DOWN_HEADER + 4);
                }
                setState(State.CONNECTED);
            }
            case PONG -> {
                if (length >= 4) {
                    measure(getInt(buf, DOWN_HEADER));
                }
            }
            case VOICE_DOWN -> {
                if (length <= VOICE_DOWN_FIXED) {
                    return;
                }
                int p = DOWN_HEADER;
                listener.onVoice(getUnsignedShort(buf, p), buf[p + 2] & 0xff, getInt(buf, p + 3), getShort(buf, p + 7),
                        getShort(buf, p + 9), getShort(buf, p + 11), buf, p + VOICE_DOWN_FIXED, length - VOICE_DOWN_FIXED);
            }
            default -> rejected++;
        }
    }

    private void measure(int sentAt) {
        int rtt = clock() - sentAt;
        if (rtt >= 0 && rtt < 60_000) {
            rttMs = rttMs < 0 ? rtt : (rttMs * 7 + rtt) / 8;
        }
    }
}
