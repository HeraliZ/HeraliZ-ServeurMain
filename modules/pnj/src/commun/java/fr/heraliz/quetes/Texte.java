package fr.heraliz.quetes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Un texte montré au joueur, dans les trois langues du serveur (fr, en, ru), sous une de deux formes :
 * <ul>
 *    <li><b>écrit</b> : les textes des PNJ et des quêtes, lus dans les fichiers de réglage
 *    ({@code "Bonjour"} = français seulement, ou {@code {"fr": "...", "en": "...", "ru": "..."}}) ;</li>
 *    <li><b>clé</b> : une clé de {@code assets/heraliz_quetes/lang/} (boutons, messages du mod), traduite chez le joueur,
 *    avec des arguments qui sont eux-mêmes des {@link Texte}.</li>
 * </ul>
 * Le serveur ne choisit jamais la langue : il envoie le texte entier, le jeu du joueur prend la sienne (sinon l'anglais,
 * sinon le français).
 */
public final class Texte {
    public static final Texte VIDE = new Texte(Map.of(), null, List.of());
    public static final List<String> LANGUES = List.of("fr", "en", "ru");
    private static final int MAX_LONGUEUR = 4096, MAX_ARGS = 8, MAX_PROFONDEUR = 3;

    private final Map<String, String> ecrit;
    private final String cle;
    private final List<Texte> args;

    private Texte(Map<String, String> ecrit, String cle, List<Texte> args) {
        this.ecrit = ecrit;
        this.cle = cle;
        this.args = args;
    }

    /** Texte écrit en français seulement. */
    public static Texte fr(String s) {
        return s == null || s.isEmpty() ? VIDE : new Texte(Map.of("fr", s), null, List.of());
    }

    /** Texte écrit, une entrée par langue (fr, en, ru). */
    public static Texte ecrit(Map<String, String> parLangue) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String l : LANGUES) {
            String s = parLangue.get(l);
            if (s != null && !s.isEmpty()) m.put(l, s);
        }
        return m.isEmpty() ? VIDE : new Texte(Collections.unmodifiableMap(m), null, List.of());
    }

    /** Texte traduit chez le joueur (clé du fichier de langue) avec ses arguments. */
    public static Texte cle(String cle, Texte... args) {
        return new Texte(Map.of(), cle, List.of(args));
    }

    /** Texte fait d'un nombre ou d'un mot identique dans toutes les langues. */
    public static Texte brut(Object o) {
        return fr(String.valueOf(o));
    }

    public boolean vide() {
        return cle == null && ecrit.isEmpty();
    }

    public String cleTraduction() {
        return cle;
    }

    public List<Texte> arguments() {
        return args;
    }

    public Map<String, String> ecrit() {
        return ecrit;
    }

    /**
     * Version écrite dans une langue (code Minecraft « fr_fr » ou « fr ») : la langue demandée, sinon l'anglais, sinon
     * le français. Pour un texte « clé », rend la clé (la traduction se fait chez le joueur).
     */
    public String choisir(String langue) {
        if (cle != null) return cle;
        if (ecrit.isEmpty()) return "";
        String l = langue == null ? "fr" : langue.length() >= 2 ? langue.substring(0, 2) : langue;
        String s = ecrit.get(l);
        if (s == null) s = ecrit.get("en");
        if (s == null) s = ecrit.get("fr");
        if (s == null) s = ecrit.values().iterator().next();
        return s;
    }

    /** Le français (journaux du serveur, console). */
    public String francais() {
        if (cle != null) return cle;
        return choisir("fr");
    }

    /**
     * Remplace les « {nom} » de chaque langue par la valeur de cette langue (textes écrits seulement ; un texte « clé »
     * est rendu tel quel).
     */
    public Texte avec(Map<String, Texte> valeurs) {
        if (cle != null || ecrit.isEmpty() || valeurs.isEmpty()) return this;
        Map<String, String> m = new LinkedHashMap<>();
        for (String l : LANGUES) {
            if (!ecrit.containsKey(l) && !l.equals("fr")) {
                boolean utile = false;
                for (Texte v : valeurs.values()) utile |= v.ecrit.containsKey(l);
                if (!utile) continue;
            }
            String s = choisir(l);
            for (Map.Entry<String, Texte> e : valeurs.entrySet()) {
                if (s.contains("{" + e.getKey() + "}")) s = s.replace("{" + e.getKey() + "}", e.getValue().choisir(l));
            }
            m.put(l, s);
        }
        return ecrit(m);
    }

    // ------------------------------------------------------------------ JSON (fichiers de réglage, sauvegardes)
    /** Lit « "texte" » (français) ou « {"fr": ..., "en": ..., "ru": ...} » ; null ou autre chose : vide. */
    public static Texte json(JsonElement e) {
        if (e == null || e.isJsonNull()) return VIDE;
        if (e.isJsonPrimitive()) return fr(e.getAsString());
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("cle")) {
                List<Texte> a = new ArrayList<>();
                if (o.has("args") && o.get("args").isJsonArray()) o.getAsJsonArray("args").forEach(x -> a.add(json(x)));
                return new Texte(Map.of(), o.get("cle").getAsString(), List.copyOf(a));
            }
            Map<String, String> m = new LinkedHashMap<>();
            for (String l : LANGUES) {
                if (o.has(l) && o.get(l).isJsonPrimitive()) m.put(l, o.get(l).getAsString());
            }
            return ecrit(m);
        }
        return VIDE;
    }

    public static Texte json(String s) {
        if (s == null || s.isEmpty()) return VIDE;
        try {
            return json(JsonParser.parseString(s));
        } catch (RuntimeException ex) {
            return fr(s);
        }
    }

    public JsonElement versJson() {
        if (cle != null) {
            JsonObject o = new JsonObject();
            o.addProperty("cle", cle);
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            for (Texte t : args) a.add(t.versJson());
            o.add("args", a);
            return o;
        }
        if (ecrit.size() == 1 && ecrit.containsKey("fr")) return new JsonPrimitive(ecrit.get("fr"));
        JsonObject o = new JsonObject();
        ecrit.forEach(o::addProperty);
        return o;
    }

    @Override
    public String toString() {
        return versJson().toString();
    }

    // ------------------------------------------------------------------ réseau
    public void ecrire(FriendlyByteBuf b) {
        ecrire(b, 0);
    }

    private void ecrire(FriendlyByteBuf b, int profondeur) {
        boolean avecCle = cle != null && profondeur < MAX_PROFONDEUR;
        b.writeBoolean(avecCle);
        if (avecCle) {
            b.writeUtf(cle, 128);
            int n = Math.min(args.size(), MAX_ARGS);
            b.writeVarInt(n);
            for (int i = 0; i < n; i++) args.get(i).ecrire(b, profondeur + 1);
            return;
        }
        Map<String, String> m = cle != null ? Map.of("fr", cle) : ecrit;
        b.writeVarInt(m.size());
        for (Map.Entry<String, String> e : m.entrySet()) {
            b.writeUtf(e.getKey(), 8);
            b.writeUtf(couper(e.getValue()), MAX_LONGUEUR);
        }
    }

    public static Texte lire(FriendlyByteBuf b) {
        return lire(b, 0);
    }

    private static Texte lire(FriendlyByteBuf b, int profondeur) {
        if (b.readBoolean()) {
            String cle = b.readUtf(128);
            int n = Math.min(b.readVarInt(), MAX_ARGS);
            List<Texte> a = new ArrayList<>(n);
            for (int i = 0; i < n; i++) a.add(profondeur < MAX_PROFONDEUR ? lire(b, profondeur + 1) : VIDE);
            return new Texte(Map.of(), cle, List.copyOf(a));
        }
        int n = Math.min(b.readVarInt(), 3);
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) m.put(b.readUtf(8), b.readUtf(MAX_LONGUEUR));
        return ecrit(m);
    }

    private static String couper(String s) {
        return s.length() > MAX_LONGUEUR ? s.substring(0, MAX_LONGUEUR) : s;
    }
}
