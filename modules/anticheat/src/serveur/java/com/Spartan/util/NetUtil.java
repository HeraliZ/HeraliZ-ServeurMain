package com.Spartan.util;

import com.google.common.net.InetAddresses;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/** Utilitaires d'adresses IP. */
public final class NetUtil {
    private NetUtil() {
    }

    /**
     * Vrai si l'adresse identifie réellement un joueur sur Internet.
     *
     * Derrière Docker / Pterodactyl, selon la configuration réseau, tous les
     * joueurs peuvent apparaître avec l'IP de la passerelle (172.x, 10.x…) :
     * bannir ou comparer une telle adresse toucherait TOUT le monde. Les
     * adresses locales, privées, de bouclage ou réservées sont donc ignorées.
     */
    public static boolean isPublicIp(String ip) {
        if (ip == null || !InetAddresses.isInetAddress(ip)) {
            return false;
        }
        return isPublicAddress(InetAddresses.forString(ip));
    }

    /**
     * Même règle que {@link #isPublicIp(String)}, pour une adresse déjà lue (adresse de la connexion d'un joueur). Faux
     * pour null (adresse inconnue).
     */
    public static boolean isPublicAddress(InetAddress a) {
        if (a == null || a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress()
                || a.isAnyLocalAddress() || a.isMulticastAddress()) {
            return false;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;
            // 100.64.0.0/10 : NAT d'opérateur (4G, fibre partagée) — partagée par des milliers d'abonnés.
            return !(first == 100 && second >= 64 && second <= 127) && first != 0;
        }
        if (a instanceof Inet6Address) {
            // fc00::/7 : adresses uniques locales (équivalent des réseaux privés).
            return (b[0] & 0xFE) != 0xFC;
        }
        return true;
    }

    public static boolean isIpv4(String ip) {
        return ip != null && InetAddresses.isInetAddress(ip) && InetAddresses.forString(ip) instanceof Inet4Address;
    }
}
