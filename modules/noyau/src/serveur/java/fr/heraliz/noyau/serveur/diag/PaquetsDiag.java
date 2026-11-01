package fr.heraliz.noyau.serveur.diag;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Paquets reçus et envoyés par type pendant un profil (01/11) : nombre et octets (avant compression). Quatre petits
 * relais Netty sont posés le temps du profil sur la connexion de chaque joueur, autour de la mise en forme des paquets
 * (« decoder » et « encoder » de Minecraft) : celui d'avant note la taille, celui d'après le type. Ils tournent sur les fils
 * réseau, jamais sur le fil serveur, et sont retirés à la fin du profil : hors profil, rien. Les paquets des mods
 * (« custom_payload ») sont rangés par canal (heraliz_loot:main…). Ni pseudo, ni contenu.
 */
final class PaquetsDiag {
    private PaquetsDiag() {
    }

    private static final Logger LOG = LogUtils.getLogger();
    private static final String IN_OCTETS = "heraliz_diag_in_octets", IN = "heraliz_diag_in", OUT = "heraliz_diag_out", OUT_OCTETS = "heraliz_diag_out_octets";

    private static final class Compte {
        final LongAdder nb = new LongAdder(), octets = new LongAdder();
    }

    /** relais d'une connexion : taille du dernier paquet reçu, type du dernier paquet envoyé (fil réseau de la connexion) */
    private static final class Etat {
        int octetsRecus;
        String typeEnvoye;
    }

    private static volatile boolean actif;
    private static final ConcurrentHashMap<String, Compte> RECUS = new ConcurrentHashMap<>(), ENVOYES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, String> NOMS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ResourceLocation, String> CANAUX = new ConcurrentHashMap<>();
    private static final LongAdder BRANCHES = new LongAdder(), RATES = new LongAdder();

    static void demarrer(MinecraftServer srv) {
        RECUS.clear();
        ENVOYES.clear();
        BRANCHES.reset();
        RATES.reset();
        actif = true;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) brancher(p);
    }

    static void arreter(MinecraftServer srv) {
        actif = false;
        MinecraftServer s = srv != null ? srv : ServerLifecycleHooks.getCurrentServer();
        if (s == null) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            Channel ch = canal(p);
            if (ch == null) continue;
            ch.eventLoop().execute(() -> {
                ChannelPipeline pl = ch.pipeline();
                for (String n : new String[]{IN_OCTETS, IN, OUT, OUT_OCTETS}) {
                    try {
                        if (pl.get(n) != null) pl.remove(n);
                    } catch (RuntimeException ignore) {
                        // connexion fermée entre-temps
                    }
                }
            });
        }
    }

    private static Channel canal(ServerPlayer p) {
        try {
            if (p.connection == null) return null;
            Connection c = p.connection.connection;
            if (c == null || c.isMemoryConnection()) return null;   // bots du banc de charge : pas de vrai réseau
            return c.channel();
        } catch (RuntimeException e) {
            return null;
        }
    }

    static void brancher(ServerPlayer p) {
        if (!actif) return;
        Channel ch = canal(p);
        if (ch == null) return;
        ch.eventLoop().execute(() -> {
            try {
                ChannelPipeline pl = ch.pipeline();
                if (!actif || pl.get(IN) != null || pl.get(OUT) != null) return;
                Etat etat = new Etat();
                boolean ok = false;
                if (pl.get("decoder") != null) {
                    pl.addBefore("decoder", IN_OCTETS, new OctetsRecus(etat));
                    pl.addAfter("decoder", IN, new TypesRecus(etat));
                    ok = true;
                }
                if (pl.get("encoder") != null) {
                    // sortie : du bout du canal vers le réseau ; « après encoder » voit le paquet, « avant » ses octets
                    pl.addAfter("encoder", OUT, new TypesEnvoyes(etat));
                    pl.addBefore("encoder", OUT_OCTETS, new OctetsEnvoyes(etat));
                    ok = true;
                }
                (ok ? BRANCHES : RATES).increment();
            } catch (RuntimeException e) {
                RATES.increment();
                LOG.debug("[diag] paquets : connexion non suivie", e);
            }
        });
    }

    private static String nom(Object paquet) {
        if (paquet instanceof ClientboundCustomPayloadPacket c) return canal(c.getIdentifier());
        if (paquet instanceof ServerboundCustomPayloadPacket c) return canal(c.getIdentifier());
        return NOMS.computeIfAbsent(paquet.getClass(), Class::getSimpleName);
    }

    private static String canal(ResourceLocation id) {
        if (id == null) return "custom_payload ?";
        return CANAUX.computeIfAbsent(id, k -> "custom_payload " + k);
    }

    private static void compter(ConcurrentHashMap<String, Compte> m, String type, int octets) {
        Compte c = m.get(type);
        if (c == null) c = m.computeIfAbsent(type, k -> new Compte());
        c.nb.increment();
        c.octets.add(octets);
    }

    private static final class OctetsRecus extends ChannelInboundHandlerAdapter {
        private final Etat etat;

        OctetsRecus(Etat etat) {
            this.etat = etat;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf b) etat.octetsRecus = b.readableBytes();
            super.channelRead(ctx, msg);
        }
    }

    private static final class TypesRecus extends ChannelInboundHandlerAdapter {
        private final Etat etat;

        TypesRecus(Etat etat) {
            this.etat = etat;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try {
                if (actif && msg instanceof Packet<?>) compter(RECUS, nom(msg), etat.octetsRecus);
                etat.octetsRecus = 0;
            } catch (RuntimeException ignore) {
                // jamais casser la connexion pour une mesure
            }
            super.channelRead(ctx, msg);
        }
    }

    private static final class TypesEnvoyes extends ChannelOutboundHandlerAdapter {
        private final Etat etat;

        TypesEnvoyes(Etat etat) {
            this.etat = etat;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            try {
                etat.typeEnvoye = msg instanceof Packet<?> ? nom(msg) : null;
            } catch (RuntimeException ignore) {
                etat.typeEnvoye = null;
            }
            super.write(ctx, msg, promise);
        }
    }

    private static final class OctetsEnvoyes extends ChannelOutboundHandlerAdapter {
        private final Etat etat;

        OctetsEnvoyes(Etat etat) {
            this.etat = etat;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (actif && msg instanceof ByteBuf b && etat.typeEnvoye != null) {
                compter(ENVOYES, etat.typeEnvoye, b.readableBytes());
                etat.typeEnvoye = null;
            }
            super.write(ctx, msg, promise);
        }
    }

    // ------------------------------------------------------------------ texte
    static void ecrire(StringBuilder b, double secondes) {
        b.append(String.format(Locale.ROOT, "-- Paquets (connexions suivies : %d, non suivies : %d ; octets avant compression) --%n",
                BRANCHES.sum(), RATES.sum()));
        table(b, "reçus des joueurs", RECUS, secondes);
        table(b, "envoyés aux joueurs", ENVOYES, secondes);
    }

    private static void table(StringBuilder b, String titre, Map<String, Compte> m, double secondes) {
        List<Map.Entry<String, Compte>> l = new ArrayList<>(m.entrySet());
        l.sort((x, y) -> Long.compare(y.getValue().octets.sum(), x.getValue().octets.sum()));
        long n = 0, o = 0;
        for (Map.Entry<String, Compte> x : l) {
            n += x.getValue().nb.sum();
            o += x.getValue().octets.sum();
        }
        b.append(String.format(Locale.ROOT, "  %s : %d paquets (%.0f/s), %.1f Ko/s%n", titre, n, n / secondes, o / 1024.0 / secondes));
        b.append(String.format(Locale.ROOT, "  %10s %10s %10s %10s  %s%n", "paquets", "par s", "Ko/s", "octets/p", "type"));
        for (int i = 0; i < Math.min(25, l.size()); i++) {
            long nb = l.get(i).getValue().nb.sum(), oc = l.get(i).getValue().octets.sum();
            b.append(String.format(Locale.ROOT, "  %10d %10.1f %10.1f %10.0f  %s%n", nb, nb / secondes, oc / 1024.0 / secondes,
                    nb == 0 ? 0 : (double) oc / nb, l.get(i).getKey()));
        }
        b.append('\n');
    }
}
