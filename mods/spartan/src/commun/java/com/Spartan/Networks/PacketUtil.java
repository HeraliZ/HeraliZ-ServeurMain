package com.Spartan.Networks;

import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Lecture bornée des paquets envoyés par le client.
 *
 * FriendlyByteBuf.readList() pré-alloue la liste avec la taille annoncée par le
 * client AVANT de lire les éléments : quelques octets suffisent à demander une
 * allocation géante et à faire tomber le serveur (OutOfMemoryError). Ici, la
 * taille est vérifiée avant toute allocation ; un paquet hors limites lève une
 * DecoderException qui déconnecte uniquement le client fautif.
 */
public final class PacketUtil {
    public static final int MAX_LIST = 512;
    public static final int MAX_STRING = 256;

    private PacketUtil() {
    }

    public static List<String> readStringList(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_LIST) {
            throw new DecoderException("Spartan: liste trop grande (" + n + ")");
        }
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(buf.readUtf(MAX_STRING));
        }
        return out;
    }

    public static void writeStringList(FriendlyByteBuf buf, List<String> list) {
        int n = Math.min(list.size(), MAX_LIST);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            String s = list.get(i);
            buf.writeUtf(s.length() > MAX_STRING ? s.substring(0, MAX_STRING) : s, MAX_STRING);
        }
    }
}
