package fr.heralisz.clans.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Une ligne de l'historique du clan (11/11, demande du proprietaire : « une page Historique avec toutes les logs de son
 * clan », triable par categorie). Ecrite par le serveur ({@code historique.Historique}), envoyee par pages
 * ({@code Packets.HistoriqueS2C}) et traduite chez le joueur :
 * <ul>
 * <li>{@code cle} : cle de langue du texte (fichiers de langue du mod des clans, ou d'un autre mod pour une ligne
 * {@code ClansApi.journal}), remplie avec {@code args} ; un argument qui commence par {@link #CLE_ARG} est lui-meme une
 * cle de langue (« ~historique.source.largage ») ; une cle qui commence par {@link #EVENEMENT} reprend une ligne du
 * journal d'activite du clan ({@link Evenement}, « @ARRIVEE ») et se traduit comme elle ;</li>
 * <li>{@code qui} : le joueur qui a fait l'action (vide : aucun) ;</li>
 * <li>{@code centiemes} : points de saison gagnes (ou perdus si negatif) multiplies par 100, 0 = aucun ;
 * {@code saison} : leur categorie ({@link #PVE}, {@link #PVP}, {@link #EVENTS}, {@link #TOUTES} pour une prise de coeur ou
 * une perte totale, {@link #SANS} sans points).</li>
 * </ul>
 */
public record LigneHistorique(long quand, int categorie, int saison, String qui, String cle, List<String> args, long centiemes) {
    /** Categories de l'historique (filtres de l'onglet) ; {@link #TOUT} : pas de filtre. */
    public static final int TOUT = -1, POINTS = 0, COMBATS = 1, MEMBRES = 2, BASE = 3, CLAN = 4, CATEGORIES = 5;
    /** Categorie des points de saison d'une ligne. */
    public static final int SANS = -1, PVE = 0, PVP = 1, EVENTS = 2, TOUTES = 3;
    public static final String EVENEMENT = "@", CLE_ARG = "~";
    public static final int ARGS_MAX = 8, ARG_MAX = 96, QUI_MAX = 32, CLE_MAX = 96;

    public LigneHistorique {
        args = List.copyOf(args.size() > ARGS_MAX ? args.subList(0, ARGS_MAX) : args);
    }

    /**
     * La ligne est-elle dans ce filtre ? Le filtre « Points » prend toute ligne qui rapporte ou coute des points (un kill
     * de la categorie Combats, une prise de coeur de la categorie Base...), plus ses propres lignes (plafond atteint...).
     */
    public boolean dans(int filtre) {
        if (filtre == TOUT) return true;
        if (filtre == POINTS) return centiemes != 0 || categorie == POINTS;
        return categorie == filtre;
    }

    /** Texte de la ligne dans la langue du joueur. */
    public MutableComponent texte() {
        if (cle.startsWith(EVENEMENT)) {
            Evenement.Genre g = genre(cle.substring(EVENEMENT.length()));
            if (g != null) return new Evenement(quand, g, args).texte();
        }
        Object[] a = new Object[args.size()];
        for (int i = 0; i < a.length; i++) {
            String s = args.get(i);
            a[i] = s.startsWith(CLE_ARG) ? Component.translatable(s.substring(CLE_ARG.length())) : s;
        }
        return Component.translatable(cle, a);
    }

    private static Evenement.Genre genre(String nom) {
        for (Evenement.Genre g : Evenement.Genre.values()) if (g.name().equals(nom)) return g;
        return null;
    }

    // ------------------------------------------------------------------ reseau
    public void write(FriendlyByteBuf b) {
        b.writeVarLong(quand);
        b.writeByte(categorie);
        b.writeByte(saison + 1);
        Texte.utf(b, qui, QUI_MAX);
        Texte.utf(b, cle, CLE_MAX);
        b.writeVarInt(args.size());
        for (String s : args) Texte.utf(b, s, ARG_MAX);
        b.writeVarLong(centiemes);
    }

    public static LigneHistorique read(FriendlyByteBuf b) {
        long quand = b.readVarLong();
        int categorie = b.readByte(), saison = b.readByte() - 1;
        String qui = b.readUtf(QUI_MAX), cle = b.readUtf(CLE_MAX);
        int n = b.readVarInt();
        if (n < 0 || n > ARGS_MAX) throw new IllegalArgumentException("LigneHistorique : " + n + " arguments");
        List<String> args = new ArrayList<>(n);
        for (int i = 0; i < n; i++) args.add(b.readUtf(ARG_MAX));
        return new LigneHistorique(quand, categorie, saison, qui, cle, args, b.readVarLong());
    }

    // ------------------------------------------------------------------ fichier (heralisz_clans/historique/<clan>.json)
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("t", quand);
        o.addProperty("c", categorie);
        if (saison != SANS) o.addProperty("s", saison);
        if (!qui.isEmpty()) o.addProperty("q", qui);
        o.addProperty("k", cle);
        if (!args.isEmpty()) {
            JsonArray a = new JsonArray();
            for (String s : args) a.add(s);
            o.add("a", a);
        }
        if (centiemes != 0) o.addProperty("p", centiemes);
        return o;
    }

    public static LigneHistorique fromJson(JsonObject o) {
        List<String> args = new ArrayList<>();
        for (JsonElement e : Json.arr(o, "a")) if (e.isJsonPrimitive()) args.add(Texte.couper(e.getAsString(), ARG_MAX));
        return new LigneHistorique(Json.lng(o, "t", 0), Math.max(0, Math.min(CATEGORIES - 1, Json.integer(o, "c", CLAN))),
                Json.integer(o, "s", SANS), Texte.couper(Json.str(o, "q", ""), QUI_MAX), Texte.couper(Json.str(o, "k", ""), CLE_MAX), args,
                Json.lng(o, "p", 0));
    }
}
