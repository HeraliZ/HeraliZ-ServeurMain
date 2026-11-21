package com.Spartan.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

/**
 * Adresses qui identifient vraiment un joueur (bans, comptes liés et, depuis le 21/11, PC du disjoncteur des règles en
 * rodage) : jamais celle d'une passerelle Docker, d'un réseau local, du bouclage ou du NAT d'opérateur, que partagent
 * tous les joueurs (ou des milliers d'abonnés).
 */
class NetUtilTest {
    private static InetAddress v4(int a, int b, int c, int d) throws UnknownHostException {
        return InetAddress.getByAddress(new byte[] {(byte) a, (byte) b, (byte) c, (byte) d});
    }

    private static InetAddress v6(int premier, int second) throws UnknownHostException {
        byte[] o = new byte[16];
        o[0] = (byte) (premier >> 8);
        o[1] = (byte) premier;
        o[2] = (byte) (second >> 8);
        o[3] = (byte) second;
        o[15] = 1;
        return InetAddress.getByAddress(o);
    }

    @Test
    void adressesPartageesParTousLesJoueurs() throws UnknownHostException {
        assertFalse(NetUtil.isPublicAddress(null));
        assertFalse(NetUtil.isPublicAddress(v4(172, 18, 0, 1))); // passerelle Docker (Pterodactyl)
        assertFalse(NetUtil.isPublicAddress(v4(10, 0, 0, 7)));
        assertFalse(NetUtil.isPublicAddress(v4(192, 168, 1, 20)));
        assertFalse(NetUtil.isPublicAddress(v4(127, 0, 0, 1)));
        assertFalse(NetUtil.isPublicAddress(v4(100, 64, 3, 9))); // NAT d'opérateur
        assertFalse(NetUtil.isPublicAddress(v4(0, 0, 0, 0)));
        assertFalse(NetUtil.isPublicAddress(v6(0xfd00, 0))); // adresse unique locale
        assertFalse(NetUtil.isPublicAddress(v6(0xfe80, 0))); // lien local
        assertFalse(NetUtil.isPublicAddress(InetAddress.getLoopbackAddress()));
    }

    @Test
    void adressesPubliques() throws UnknownHostException {
        assertTrue(NetUtil.isPublicAddress(v4(82, 64, 12, 3)));
        assertTrue(NetUtil.isPublicAddress(v4(100, 128, 0, 1))); // juste après le NAT d'opérateur
        assertTrue(NetUtil.isPublicAddress(v6(0x2a01, 0x0e0a)));
    }

    @Test
    void memeRegleQueLaFormeTexte() throws UnknownHostException {
        for (InetAddress a : new InetAddress[] {v4(172, 18, 0, 1), v4(100, 64, 3, 9), v4(82, 64, 12, 3), v6(0x2a01, 0x0e0a),
                v6(0xfd00, 0)}) {
            assertTrue(NetUtil.isPublicAddress(a) == NetUtil.isPublicIp(a.getHostAddress()), a.getHostAddress());
        }
        assertFalse(NetUtil.isPublicIp(null));
        assertFalse(NetUtil.isPublicIp("pas une adresse"));
    }
}
