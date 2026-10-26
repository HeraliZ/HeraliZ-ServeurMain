package fr.heraliz.banc;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Deflater;

/**
 * Réseau simulé des bots (demande du propriétaire, 26/10 : « simuler le réseau avec 90 joueurs ») : au lieu d'être jetés,
 * les paquets envoyés à un bot sont mis en forme ({@code Packet.write}) et compressés comme le fait la connexion d'un vrai
 * joueur (seuil de compression du serveur, Deflater au niveau par défaut, comme Minecraft), sur des fils à part comme les
 * fils réseau de Netty. On compte les paquets, les octets bruts, les octets qui partiraient sur internet et le temps de
 * calcul. Ce qui manque : l'envoi lui-même (carte réseau, internet, ping) et les paquets que les joueurs envoient au
 * serveur. Coupé par {@code /bots reseau false} (paquets jetés comme avant).
 */
final class Reseau {
    private Reseau() {
    }

    static volatile boolean actif = true;
    static volatile int seuil = 256;

    static final AtomicLong PAQUETS = new AtomicLong(), BRUTS = new AtomicLong(), SORTANTS = new AtomicLong(),
            NANOS = new AtomicLong(), ERREURS = new AtomicLong(), RATES = new AtomicLong();
    private static final AtomicInteger EN_ATTENTE = new AtomicInteger();
    private static final int ATTENTE_MAX = 200_000;
    private static final int FILS = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private static final ExecutorService POOL = new ThreadPoolExecutor(FILS, FILS, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
        Thread t = new Thread(r, "HeraliZ bots réseau");
        t.setDaemon(true);
        return t;
    });
    private static final ThreadLocal<Deflater> DEFLATER = ThreadLocal.withInitial(Deflater::new);
    private static final ThreadLocal<byte[]> TAMPON = ThreadLocal.withInitial(() -> new byte[8192]);

    /** Paquet envoyé à un bot : mis en forme et compressé à part, puis oublié. */
    static void recevoir(Object msg) {
        if (!actif || !(msg instanceof Packet<?> p)) {
            ReferenceCountUtil.release(msg);
            return;
        }
        if (EN_ATTENTE.incrementAndGet() > ATTENTE_MAX) {
            // les fils réseau n'arrivent plus à suivre : c'est déjà une mesure
            EN_ATTENTE.decrementAndGet();
            RATES.incrementAndGet();
            return;
        }
        POOL.execute(() -> {
            try {
                encoder(p);
            } finally {
                EN_ATTENTE.decrementAndGet();
            }
        });
    }

    private static void encoder(Packet<?> p) {
        if (p instanceof BundlePacket<?> b) {
            // la connexion d'un joueur déplie les paquets groupés et envoie chacun (plus un délimiteur avant et après)
            for (Packet<?> s : b.subPackets()) encoder(s);
            compter(2, 6);
            return;
        }
        long t0 = System.nanoTime();
        ByteBuf buf = Unpooled.buffer(256);
        try {
            FriendlyByteBuf f = new FriendlyByteBuf(buf);
            f.writeVarInt(0);   // numéro du paquet (1 à 2 octets ; 1 ici)
            p.write(f);
            int n = buf.readableBytes();
            long sortie;
            if (seuil >= 0 && n >= seuil) {
                sortie = compresser(buf, n) + taille(n);
            } else {
                sortie = n + (seuil >= 0 ? 1 : 0);
            }
            sortie += taille((int) sortie);   // longueur de la trame
            NANOS.addAndGet(System.nanoTime() - t0);
            compter(n, sortie);
        } catch (Throwable ex) {
            ERREURS.incrementAndGet();
        } finally {
            buf.release();
        }
    }

    private static void compter(long bruts, long sortants) {
        PAQUETS.incrementAndGet();
        BRUTS.addAndGet(bruts);
        SORTANTS.addAndGet(sortants);
    }

    private static int compresser(ByteBuf buf, int n) {
        byte[] entree = new byte[n];
        buf.getBytes(buf.readerIndex(), entree);
        Deflater d = DEFLATER.get();
        byte[] t = TAMPON.get();
        d.setInput(entree);
        d.finish();
        int total = 0;
        while (!d.finished()) total += d.deflate(t);
        d.reset();
        return total;
    }

    private static int taille(int v) {
        int n = 1;
        while ((v >>>= 7) != 0) n++;
        return n;
    }

    /** Relevé depuis le dernier appel : {paquets, octets bruts, octets sortants, ns de calcul, erreurs, ratés}. */
    static long[] relever() {
        return new long[]{PAQUETS.getAndSet(0), BRUTS.getAndSet(0), SORTANTS.getAndSet(0), NANOS.getAndSet(0), ERREURS.getAndSet(0),
                RATES.getAndSet(0)};
    }

    static int enAttente() {
        return EN_ATTENTE.get();
    }

    static int fils() {
        return FILS;
    }

    /** Ligne lisible pour {@code sec} secondes et {@code bots} bots. */
    static String ligne(long[] r, double sec, long bots) {
        double mbits = r[2] * 8 / 1_000_000.0 / sec;
        double koParBot = bots == 0 ? 0 : r[2] / 1024.0 / sec / bots;
        double cpu = r[3] / 1e9 / sec * 100;   // en % d'un cœur
        return String.format(java.util.Locale.ROOT,
                "réseau : %.1f Mbit/s sortants (%.0f Ko/s par bot), %.0f paquets/s, compression %.0f %% des octets, calcul %.0f %% d'un cœur, %d en attente, %d ratés, %d erreurs",
                mbits, koParBot, r[0] / sec, r[1] == 0 ? 100 : 100.0 * r[2] / r[1], cpu, enAttente(), r[5], r[4]);
    }
}
