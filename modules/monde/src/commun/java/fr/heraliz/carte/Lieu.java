package fr.heraliz.carte;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Un lieu nommé de la carte du monde (ville, village, aérodrome, base militaire...), posé en jeu par le staff
 * ({@code /carte lieu ajouter <type> <nom>} à l'endroit où il se tient) et gardé avec le monde. Les villes et les villages
 * n'ont que leur nom sur la carte ; les autres ont aussi une icône.
 * <p>19/11 : les lieux d'un type {@linkplain Type#automatique() automatique} (un par recycleur de la carte) ne sont jamais
 * posés par le staff : le serveur les ajoute et les retire tout seul ({@code serveur/RecycleursCarte}). Ils n'ont pas de
 * nom (vide) : l'écran écrit celui de leur type.
 *
 * @param dimension monde où le lieu a été posé (la carte ne montre que ceux du monde du joueur)
 */
public record Lieu(String nom, Type type, ResourceLocation dimension, int x, int z) {
    /** Longueur maximale d'un nom de lieu. */
    public static final int NOM_MAX = 40;

    /**
     * Types de lieux : identifiant (commandes, traductions {@code heraliz_carte.type.<id>}), icône de l'atlas, couleur. Le
     * réseau les envoie par leur rang : un nouveau type va TOUJOURS à la fin.
     */
    public enum Type {
        VILLE("ville", "", 0xECEEF2),
        VILLAGE("village", "", 0xC9CED8),
        AERODROME("aerodrome", "avion", 0xD9DEE8),
        MILITAIRE("militaire", "danger", 0xFF9A5C),
        CENTRALE("centrale", "eclair", 0xF5D76E),
        USINE("usine", "usine", 0xD6B98A),
        PORT("port", "ancre", 0x7FC4FF),
        HOPITAL("hopital", "croix", 0xFF7A7A),
        GARE("gare", "train", 0xC8CED8),
        POINT("point", "etoile", 0xE8C36A),
        /** 19/11 : un recycleur de la carte ({@code heraliz_quetes:recycleur_carte}), repéré tout seul ({@link #automatique()}). */
        RECYCLEUR("recycleur", "recyclage", 0xA8E063);

        public final String id;
        /** Nom de l'icône dans l'atlas (vide : seulement le nom, pour les villes et les villages). */
        public final String icone;
        public final int couleur;

        Type(String id, String icone, int couleur) {
            this.id = id;
            this.icone = icone;
            this.couleur = couleur;
        }

        public boolean texteSeul() {
            return icone.isEmpty();
        }

        /** Posé et retiré par le serveur lui-même, jamais par {@code /carte lieu} (19/11 : les recycleurs de la carte). */
        public boolean automatique() {
            return this == RECYCLEUR;
        }

        public String cle() {
            return "heraliz_carte.type." + id;
        }

        public static Type parId(String id) {
            String s = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
            for (Type t : values()) if (t.id.equals(s)) return t;
            return null;
        }

        public static Type parOrdinal(int i) {
            Type[] v = values();
            return v[Math.max(0, Math.min(v.length - 1, i))];
        }
    }

    /** Nom nettoyé (caractères de contrôle et « § » retirés, espaces réduits) et coupé à {@link #NOM_MAX}. */
    public static String nettoyer(String nom) {
        String n = nom == null ? "" : nom.replaceAll("[\\p{Cntrl}§]", "").replaceAll("\\s+", " ").trim();
        return n.length() > NOM_MAX ? n.substring(0, NOM_MAX).trim() : n;
    }

    public void ecrire(FriendlyByteBuf b) {
        b.writeUtf(nettoyer(nom), NOM_MAX);
        b.writeVarInt(type.ordinal());
        b.writeResourceLocation(dimension);
        b.writeInt(x);
        b.writeInt(z);
    }

    public static Lieu lire(FriendlyByteBuf b) {
        return new Lieu(b.readUtf(NOM_MAX), Type.parOrdinal(b.readVarInt()), b.readResourceLocation(), b.readInt(), b.readInt());
    }
}
