package fr.heralisz.clans.model;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Ligne du journal d'activite du clan (onglet Apercu). genre = cle de traduction courte ("evenement.<genre>"), args =
 * valeurs a inserer (nom du joueur, zone, quete, niveau...). Le client traduit : « il y a 12 min · Kamenka capturee ».
 */
public record Evenement(long quand, Genre genre, List<String> args) {
    public static final int MAX = 20;

    public enum Genre {
        ARRIVEE, DEPART, EXCLUSION, PROMOTION, RETROGRADATION, TRANSFERT, CAPTURE, ZONE_PERDUE, QUETE, NIVEAU,
        RAVITAILLEMENT, CREATION,
        /** Banniere modifiee (arg : nom du joueur). Ajoutes en dernier : ordinaux conserves. */
        BANNIERE,
        /** Banniere remise par defaut par la moderation (sans argument). */
        BANNIERE_REINITIALISEE,
        /**
         * Ligne libre d'un autre mod (ClansApi.journal) : args[0] = cle de traduction (fournie par ce mod), args[1..] =
         * ses arguments. Afficher avec {@link Evenement#texte()}.
         */
        PERSO,
        /**
         * Reglage individuel de permission : args = [cible, auteur, PERM, ETAT] pour un seul changement, ou
         * [cible, auteur, "*", n] quand plusieurs changements rapproches du meme auteur sur la meme cible ont ete
         * regroupes. Afficher avec {@link Evenement#texte()} (libelle de la permission traduit chez le client).
         */
        PERMISSION;

        public String libelle() {
            return "evenement." + name().toLowerCase();
        }

        public static Genre parOrdinal(int i) {
            Genre[] v = values();
            return v[Math.max(0, Math.min(v.length - 1, i))];
        }
    }

    public Evenement(long quand, Genre genre, String... args) {
        this(quand, genre, List.of(args));
    }

    /**
     * Texte a afficher (client) : « evenement.<genre> » avec les args, ou pour {@link Genre#PERSO} la cle args[0]
     * avec args[1..].
     */
    public net.minecraft.network.chat.MutableComponent texte() {
        if (genre == Genre.PERSO && !args.isEmpty()) {
            return net.minecraft.network.chat.Component.translatable(args.get(0), args.subList(1, args.size()).toArray());
        }
        if (genre == Genre.PERMISSION && args.size() >= 4) {
            String cible = args.get(0), auteur = args.get(1);
            if ("*".equals(args.get(2))) {
                return net.minecraft.network.chat.Component.translatable("evenement.permission.plusieurs", auteur, args.get(3), cible);
            }
            Perm p = Perm.parNom(args.get(2));
            Perm.Etat e = Perm.Etat.parNom(args.get(3));
            Object libelle = p == null ? args.get(2) : net.minecraft.network.chat.Component.translatable(p.libelle());
            String etat = (e == null ? Perm.Etat.HERITE : e).name().toLowerCase(java.util.Locale.ROOT);
            return net.minecraft.network.chat.Component.translatable("evenement.permission." + etat, auteur, libelle, cible);
        }
        if ((genre == Genre.PROMOTION || genre == Genre.RETROGRADATION) && args.size() >= 2) {
            // le grade est ecrit en nom d'enum (ancien journal : libelle francais) : traduit dans la langue du joueur
            Grade g = Grade.duJournal(args.get(1));
            Object grade = g == null ? args.get(1) : net.minecraft.network.chat.Component.translatable(g.libelle());
            return net.minecraft.network.chat.Component.translatable(genre.libelle(), args.get(0), grade);
        }
        return net.minecraft.network.chat.Component.translatable(genre.libelle(), args.toArray());
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("quand", quand);
        o.addProperty("genre", genre.name());
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        for (String s : args) a.add(s);
        o.add("args", a);
        return o;
    }

    public static Evenement fromJson(JsonObject o) {
        List<String> args = new ArrayList<>();
        for (var e : Json.arr(o, "args")) if (e.isJsonPrimitive()) args.add(e.getAsString());
        return new Evenement(Json.lng(o, "quand", 0), Json.enumOf(o, "genre", Genre.class, Genre.ARRIVEE), args);
    }

    public void write(FriendlyByteBuf b) {
        b.writeVarLong(quand);
        b.writeVarInt(genre.ordinal());
        b.writeVarInt(args.size());
        for (String s : args) Texte.utf(b, s, 96);
    }

    public static Evenement read(FriendlyByteBuf b) {
        long quand = b.readVarLong();
        Genre g = Genre.parOrdinal(b.readVarInt());
        int n = Math.min(8, b.readVarInt());
        List<String> args = new ArrayList<>(n);
        for (int i = 0; i < n; i++) args.add(b.readUtf(96));
        return new Evenement(quand, g, args);
    }
}
