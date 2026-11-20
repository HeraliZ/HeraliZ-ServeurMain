package fr.heraliz.banc;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.zip.Deflater;

/**
 * Réseau simulé des bots (demande du propriétaire, 26/10 : « simuler le réseau avec 90 joueurs ») : au lieu d'être jetés,
 * les paquets envoyés à un bot sont mis en forme ({@code Packet.write}) et compressés comme le fait la connexion d'un vrai
 * joueur (seuil de compression du serveur, Deflater au niveau par défaut, comme Minecraft), sur des fils à part comme les
 * fils réseau de Netty. On compte les paquets, les octets bruts, les octets qui partiraient sur internet et le temps de
 * calcul. Ce qui manque : l'envoi lui-même (carte réseau, internet, ping) et les paquets que les joueurs envoient au
 * serveur. Coupé par {@code /bots reseau false} (paquets jetés comme avant).
 * <p>20/11 (audit réseau, mémoire et disque) : aussi compté par sorte de paquet (classe de Minecraft, ou canal du mod pour
 * les paquets des mods), pour savoir ce qui remplit la bande passante ; tableau dans le rapport de {@code /bots mesure}.</p>
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

    /** Paquets, octets bruts et octets sortants d'une sorte de paquet. */
    private static final class Sorte {
        final LongAdder paquets = new LongAdder(), bruts = new LongAdder(), sortants = new LongAdder();
    }

    private static final ConcurrentHashMap<String, Sorte> SORTES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, String> NOMS = new ConcurrentHashMap<>();
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
            Sorte so = SORTES.computeIfAbsent(nom(p), k -> new Sorte());
            so.paquets.increment();
            so.bruts.add(n);
            so.sortants.add(sortie);
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

    /** Sorte d'un paquet : canal pour les paquets des mods, sinon nom de la classe de Minecraft. */
    private static String nom(Packet<?> p) {
        if (p instanceof ClientboundCustomPayloadPacket c) return "custom_payload " + c.getIdentifier();
        return NOMS.computeIfAbsent(p.getClass(), Class::getSimpleName);
    }

    /** Début d'une mesure : comptes par sorte remis à zéro. */
    static void viderSortes() {
        SORTES.clear();
    }

    /** Tableau des sortes de paquets, les plus lourdes d'abord (octets sortants, après compression). */
    static void ecrireSortes(StringBuilder t, double secondes, int bots) {
        List<Map.Entry<String, Sorte>> l = new ArrayList<>(SORTES.entrySet());
        l.sort((a, b) -> Long.compare(b.getValue().sortants.sum(), a.getValue().sortants.sum()));
        long total = 0;
        for (Map.Entry<String, Sorte> e : l) total += e.getValue().sortants.sum();
        double sec = Math.max(0.001, secondes);
        t.append(String.format(Locale.ROOT, "%10s %10s %10s %10s %7s  %s%n", "paquets/s", "Ko/s", "Ko/s/bot", "octets/p", "part", "sorte (octets sortants, compression comprise)"));
        for (int i = 0; i < Math.min(30, l.size()); i++) {
            Sorte so = l.get(i).getValue();
            long nb = so.paquets.sum(), out = so.sortants.sum();
            t.append(String.format(Locale.ROOT, "%10.0f %10.1f %10.2f %10.0f %6.1f%%  %s%n", nb / sec, out / 1024.0 / sec,
                    bots <= 0 ? 0 : out / 1024.0 / sec / bots, nb == 0 ? 0 : (double) so.bruts.sum() / nb, total == 0 ? 0 : 100.0 * out / total, l.get(i).getKey()));
        }
        if (l.size() > 30) t.append("  (").append(l.size() - 30).append(" autres sortes, plus légères)\n");
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
