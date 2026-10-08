package com.caracrea.client.voice;

import com.caracrea.CaraCrea;
import com.caracrea.network.CaraCreaNetwork;
import com.caracrea.voice.net.UdpVoiceClient;
import net.minecraft.client.Minecraft;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * How this client's voice reaches the server and the others' voices reach it: the dedicated UDP channel when the
 * server offers one and it works, otherwise the game connection (paquets {@code VoiceUp} / {@code VoiceDown}). The
 * switch is automatic in both directions ({@link UdpVoiceClient}); the player can also force the game connection.
 */
final class VoiceTransport implements UdpVoiceClient.Listener {
    private volatile UdpVoiceClient udp;
    private volatile boolean closed;
    private final boolean wantsUdp;

    private VoiceTransport(boolean wantsUdp) {
        this.wantsUdp = wantsUdp;
    }

    /**
     * Opens the transport for a session. The address is resolved and the socket opened on a background thread, so a
     * slow DNS never freezes the game.
     */
    static VoiceTransport open(CaraCreaNetwork.VoiceConfig config, boolean forceGameConnection, AtomicLong counter) {
        boolean wantsUdp = config.udpPort() > 0 && !forceGameConnection;
        VoiceTransport transport = new VoiceTransport(wantsUdp);
        if (wantsUdp) {
            SocketAddress remote = gameAddress();
            Thread connect = new Thread(() -> transport.connect(config, remote, counter), "CaraCrea voice connect");
            connect.setDaemon(true);
            connect.start();
        }
        return transport;
    }

    private static SocketAddress gameAddress() {
        Minecraft mc = Minecraft.getInstance();
        try {
            return mc.getConnection() == null ? null : mc.getConnection().getConnection().getRemoteAddress();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void connect(CaraCreaNetwork.VoiceConfig config, SocketAddress remote, AtomicLong counter) {
        InetSocketAddress server;
        try {
            String host = config.udpHost();
            int port = config.udpPort();
            if (host != null && !host.isBlank()) {
                String h = host.trim();
                int colon = h.lastIndexOf(':');
                if (colon > 0 && h.indexOf(':') == colon && h.substring(colon + 1).matches("\\d{1,5}")) {
                    port = Integer.parseInt(h.substring(colon + 1));
                    h = h.substring(0, colon);
                }
                server = new InetSocketAddress(h, port);
            } else if (remote instanceof InetSocketAddress inet && inet.getAddress() != null) {
                server = new InetSocketAddress(inet.getAddress(), port);
            } else {
                // Single player (local connection): the integrated server's UDP is on this machine.
                server = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
            }
        } catch (RuntimeException e) {
            CaraCrea.LOGGER.warn("Voice chat: bad voice address ({}); using the game connection", e.toString());
            return;
        }
        if (closed) {
            return;
        }
        UdpVoiceClient client = new UdpVoiceClient(server, config.session(), config.key(), this, UdpVoiceClient.Timings.DEFAULT, counter);
        udp = client;
        client.start();
        CaraCrea.LOGGER.info("Voice chat: dedicated UDP channel to {}", server);
    }

    /** Whether frames go through UDP right now. */
    boolean udpConnected() {
        UdpVoiceClient client = udp;
        return client != null && client.connected();
    }

    /** "UDP", "CONNECTING" (UDP being tried, game connection meanwhile) or "TCP". */
    String state() {
        UdpVoiceClient client = udp;
        if (client == null) {
            return wantsUdp && !closed ? "CONNECTING" : "TCP";
        }
        return switch (client.state()) {
            case CONNECTED -> "UDP";
            case CONNECTING -> "CONNECTING";
            default -> "TCP";
        };
    }

    int rttMs() {
        UdpVoiceClient client = udp;
        return client == null ? -1 : client.rttMs();
    }

    /** Bytes sent and received for the voice, over UDP and over the game connection. */
    long bytesSent() {
        UdpVoiceClient client = udp;
        return (client == null ? 0 : client.bytesSent()) + VoiceClient.TCP_BYTES_SENT.get();
    }

    long bytesReceived() {
        UdpVoiceClient client = udp;
        return (client == null ? 0 : client.bytesReceived()) + VoiceClient.TCP_BYTES_RECEIVED.get();
    }

    /** Tells the server, in the next pings, how the voices arrive here. */
    void report(int lossPermille, int bufferMs) {
        UdpVoiceClient client = udp;
        if (client != null) {
            client.report(lossPermille, bufferMs);
        }
    }

    /** Microphone thread: one frame of voice, through UDP or else the game connection. */
    void send(int sequence, int flags, byte[] opus, int length) {
        UdpVoiceClient client = udp;
        if (client != null && client.sendVoice(flags, sequence, opus, 0, length)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            VoiceClient.TCP_SENT.incrementAndGet();
            VoiceClient.TCP_BYTES_SENT.addAndGet(length + 16L);
            CaraCreaNetwork.CHANNEL.sendToServer(new CaraCreaNetwork.VoiceUp(sequence, flags, Arrays.copyOf(opus, length)));
        }
    }

    @Override
    public void onVoice(int speaker, int flags, int sequence, int dx, int dy, int dz, byte[] buf, int off, int len) {
        VoiceClient.received(speaker, flags, sequence, dx, dy, dz, buf, off, len);
    }

    void close() {
        closed = true;
        UdpVoiceClient client = udp;
        udp = null;
        if (client != null) {
            client.close();
        }
    }
}
