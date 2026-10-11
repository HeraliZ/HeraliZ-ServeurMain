package fr.heralisz.utils.train;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Retouches de la voie dessinée, posées en jeu par le staff (/train voie genre | retirer) : purement visuelles, le tracé
 * du train ne bouge jamais (l'horaire, les voitures et les heurts lisent toujours Reseau). Une retouche = à partir du
 * point d'indice debut, sur longueur points (la boucle se referme), dessiner le genre g (Reseau.PLAT à Reseau.PASSAGE)
 * ou rien du tout (CACHE). Appliquées dans l'ordre : la dernière gagne. Gardées par le serveur dans les données du
 * monde (TrainServeur.Donnees, une liste par réseau), envoyées aux joueurs (paquet Train.Voie), appliquées par
 * VoieRendu quand il construit ses tronçons et par /train voie remplir.
 */
public final class Retouches {
    /** Genre « rien de dessiné » ; AUCUNE : pas de retouche sur ce point (genre du tracé). */
    public static final byte CACHE = -1, AUCUNE = -2;
    /** Lettres des genres, dans l'ordre de Reseau (celles du fichier du réseau). */
    public static final String LETTRES = "prtTPx";
    /** Au plus autant de retouches par réseau (taille du paquet, temps de construction des tronçons). */
    public static final int MAX = 500;

    /** Une retouche : premier point, nombre de points, genre dessiné (CACHE : rien). */
    public record Retouche(int debut, int longueur, byte genre) {}

    private Retouches() {}

    /** Genre dessiné de chaque point du réseau (AUCUNE : celui du tracé) ; null s'il n'y a aucune retouche. */
    public static byte[] table(Reseau r, List<Retouche> liste) {
        if (r == null || liste == null || liste.isEmpty()) return null;
        byte[] t = new byte[r.n];
        java.util.Arrays.fill(t, AUCUNE);
        for (Retouche x : liste) {
            int l = Math.max(1, Math.min(r.n, x.longueur()));
            for (int k = 0; k < l; k++) t[Math.floorMod(x.debut() + k, r.n)] = x.genre();
        }
        return t;
    }

    /** Genre dessiné du point i (retouche comprise) : Reseau.PLAT à Reseau.PASSAGE, ou CACHE. */
    public static byte genre(Reseau r, byte[] table, int i) {
        int j = Math.floorMod(i, r.n);
        byte g = table == null ? AUCUNE : table[j];
        return g == AUCUNE ? r.genre[j] : g;
    }

    /** Lettre d'un genre (« - » : caché). */
    public static String lettre(byte g) {
        if (g == CACHE) return "-";
        return g >= 0 && g < LETTRES.length() ? String.valueOf(LETTRES.charAt(g)) : "?";
    }

    /** Genre d'une lettre (p, r, t, T, P, x ; la casse compte : t tranchée, T pont, p plat, P tunnel), -3 si inconnue. */
    public static byte depuisLettre(String s) {
        if (s == null || s.length() != 1) return -3;
        int i = LETTRES.indexOf(s.charAt(0));
        return i < 0 ? -3 : (byte) i;
    }

    /** Suffixe de la clé de traduction du nom d'un genre (heralisz_utils.train.voie.genre.<suffixe>). */
    public static String nom(byte g) {
        return switch (g) {
            case Reseau.PLAT -> "plat";
            case Reseau.REMBLAI -> "remblai";
            case Reseau.TRANCHEE -> "tranchee";
            case Reseau.PONT -> "pont";
            case Reseau.TUNNEL -> "tunnel";
            case Reseau.PASSAGE -> "passage";
            default -> "cache";
        };
    }

    /** Nom français d'un genre (texte de secours des messages du serveur). */
    public static String nomFrancais(byte g) {
        return switch (g) {
            case Reseau.PLAT -> "plat";
            case Reseau.REMBLAI -> "remblai";
            case Reseau.TRANCHEE -> "tranchée";
            case Reseau.PONT -> "pont";
            case Reseau.TUNNEL -> "tunnel";
            case Reseau.PASSAGE -> "passage à niveau";
            default -> "caché";
        };
    }

    static void ecrire(FriendlyByteBuf b, List<Retouche> l) {
        int n = Math.min(MAX, l.size());
        b.writeVarInt(n);
        for (int k = 0; k < n; k++) {
            Retouche x = l.get(k);
            b.writeVarInt(x.debut());
            b.writeVarInt(x.longueur());
            b.writeByte(x.genre());
        }
    }

    static List<Retouche> lire(FriendlyByteBuf b) {
        int n = Math.min(MAX, b.readVarInt());
        List<Retouche> l = new ArrayList<>(n);
        for (int k = 0; k < n; k++) l.add(new Retouche(b.readVarInt(), b.readVarInt(), b.readByte()));
        return l;
    }
}
