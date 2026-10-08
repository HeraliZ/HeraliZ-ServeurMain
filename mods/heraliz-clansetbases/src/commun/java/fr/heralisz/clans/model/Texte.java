package fr.heralisz.clans.model;

import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;

/**
 * Troncature des chaines avant ecriture reseau ou stockage. {@code FriendlyByteBuf.writeUtf(s, max)} leve une
 * {@code EncoderException} au lieu de couper : comme l'encodeur tourne sur le fil serveur au moment de l'envoi, une
 * seule chaine trop longue (titre de quete venu de la config, nom de zone saisi par un admin) ferait planter le tick.
 * Toute ecriture de chaine de {@code model/} et {@code network/} passe donc par {@link #utf}, avec une longueur
 * maximale explicite ; {@link #couper} sert aussi au stockage (journal de banque, catalogue de quetes).
 */
public final class Texte {
    private Texte() {
    }

    /** Points de suspension (un seul caractere) ajoutes quand une chaine est coupee. */
    private static final char SUSPENSION = '…';

    /**
     * La chaine limitee a {@code max} caracteres UTF-16 (la limite que verifie writeUtf) : null devient « », une chaine
     * trop longue est coupee et finit par « … » sans casser une paire de substitution.
     */
    public static String couper(@Nullable String s, int max) {
        if (s == null) return "";
        if (max <= 0) return "";
        if (s.length() <= max) return s;
        if (max == 1) return String.valueOf(SUSPENSION);
        int fin = max - 1;
        if (Character.isHighSurrogate(s.charAt(fin - 1))) fin--;
        return s.substring(0, fin) + SUSPENSION;
    }

    /** Ecrit la chaine coupee a {@code max} caracteres (jamais d'EncoderException). */
    public static void utf(FriendlyByteBuf b, @Nullable String s, int max) {
        b.writeUtf(couper(s, max), max);
    }
}
