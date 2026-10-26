package fr.heraliz.evenements.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heraliz.evenements.HeralizEvenements;
import fr.heraliz.evenements.objets.ObjetsEvenement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Objets d'événement à revendre, côté serveur (docs/EVENEMENTS.md) : un exemplaire est créé dans chaque coffre
 * d'événement, tiré selon les chances de l'événement ; il porte un numéro unique, l'événement d'origine et une signature
 * du serveur (clé {@code <serveur>/heraliz_evenements/objets.cle}, créée au premier démarrage, jamais dans le dépôt).
 * Revendu à un marchand racheteur : son numéro est écrit dans {@code objets_vendus.txt} AVANT le paiement, et un numéro
 * déjà vendu (copie) ou une signature fausse (objet fait à la main) n'est jamais racheté. Prix et chances :
 * {@code config/heraliz_evenements/objets.json} (relu par {@code /event recharger}) ; points de saison : barème des clans,
 * {@code events.points.objet_<id>}.
 */
final class ObjetsServeur {
    private ObjetsServeur() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Random ALEA = new Random();

    private static final Map<String, Long> PRIX = new LinkedHashMap<>();
    /** Chances par type d'événement (« largage ») : objet -> poids. */
    private static final Map<String, Map<String, Integer>> TIRAGES = new LinkedHashMap<>();
    private static final Set<String> VENDUS = new HashSet<>();
    @Nullable
    private static Path vendus;
    @Nullable
    private static byte[] cle;

    // ------------------------------------------------------------------ chargement
    /** Clé et objets déjà vendus (démarrage du serveur ; les prix et chances : {@link #recharger}). */
    static void charger(Path dossier) {
        VENDUS.clear();
        vendus = dossier.resolve("objets_vendus.txt");
        Path fc = dossier.resolve("objets.cle");
        try {
            Files.createDirectories(dossier);
            if (!Files.exists(fc)) {
                byte[] k = new byte[32];
                new SecureRandom().nextBytes(k);
                Files.writeString(fc, HexFormat.of().formatHex(k), StandardCharsets.UTF_8);
            }
            cle = HexFormat.of().parseHex(Files.readString(fc, StandardCharsets.UTF_8).trim());
            if (Files.exists(vendus)) {
                for (String l : Files.readAllLines(vendus, StandardCharsets.UTF_8)) if (!l.isBlank()) VENDUS.add(l.trim());
            }
        } catch (IOException | RuntimeException ex) {
            cle = null;   // sans clé : aucun objet créé ni racheté (jamais d'objet sans signature)
            HeralizEvenements.LOG.error("[evenements] objets d'evenement : cle ou liste des ventes illisible, objets coupes", ex);
        }
    }

    static void recharger() {
        Path f = FMLPaths.CONFIGDIR.get().resolve("heraliz_evenements").resolve("objets.json");
        PRIX.clear();
        TIRAGES.clear();
        try {
            if (!Files.exists(f)) {
                Files.createDirectories(f.getParent());
                Files.writeString(f, GSON.toJson(defaut()), StandardCharsets.UTF_8);
            }
            lire(JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException | RuntimeException ex) {
            HeralizEvenements.LOG.error("[evenements] objets.json illisible : valeurs par defaut", ex);
            PRIX.clear();
            TIRAGES.clear();
            lire(defaut());
        }
        HeralizEvenements.LOG.info("[evenements] objets d'evenement : prix {}, tirages {}", PRIX, TIRAGES);
    }

    private static void lire(JsonObject o) {
        JsonObject prix = o.getAsJsonObject("prix");
        for (String id : ObjetsEvenement.IDS) if (prix.has(id)) PRIX.put(id, Math.max(0, prix.get(id).getAsLong()));
        JsonObject t = o.getAsJsonObject("tirages");
        for (String ev : t.keySet()) {
            Map<String, Integer> m = new LinkedHashMap<>();
            JsonObject x = t.getAsJsonObject(ev);
            for (String id : ObjetsEvenement.IDS) if (x.has(id) && x.get(id).getAsInt() > 0) m.put(id, x.get(id).getAsInt());
            if (!m.isEmpty()) TIRAGES.put(ev, m);
        }
    }

    private static JsonObject defaut() {
        JsonObject o = new JsonObject();
        o.addProperty("_aide", "Objets d'evenement a revendre (dossier classe Rare, mallette scellee Epique, disque dur militaire Legendaire ;"
                + " boite noire d'helicoptere Legendaire, seulement pour l'helicoptere)."
                + " prix : herals verses par le marchand racheteur (Mila). tirages : par type d'evenement, chance de chaque objet (poids) ;"
                + " un evenement absent de la liste tire avec \"defaut\". Points de saison : events.points.objet_<id> de"
                + " heralisz_clans/saison.json. Relu par /event recharger.");
        JsonObject prix = new JsonObject();
        prix.addProperty("dossier_classe", 120);
        prix.addProperty("mallette_scellee", 300);
        prix.addProperty("disque_dur_militaire", 700);
        prix.addProperty("boite_noire_helico", 900);
        o.add("prix", prix);
        JsonObject t = new JsonObject();
        JsonObject def = new JsonObject();
        def.addProperty("dossier_classe", 65);
        def.addProperty("mallette_scellee", 28);
        def.addProperty("disque_dur_militaire", 7);
        t.add("defaut", def);
        t.add("largage", def.deepCopy());
        JsonObject helico = new JsonObject();
        helico.addProperty("boite_noire_helico", 100);
        t.add("helico", helico);
        o.add("tirages", t);
        return o;
    }

    // ------------------------------------------------------------------ objets
    /** Type de l'objet (« mallette_scellee »), null si ce n'est pas un objet d'événement. */
    @Nullable
    static String type(ItemStack s) {
        if (s.isEmpty() || !(s.getItem() instanceof ObjetsEvenement.Objet)) return null;
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(s.getItem());
        return rl == null ? null : rl.getPath();
    }

    /** Un exemplaire neuf pour le coffre de l'événement {@code evenement} (« largage#12 ») ; vide sans clé. */
    static ItemStack creer(String evenement) {
        if (cle == null) return ItemStack.EMPTY;
        int i = evenement.indexOf('#');
        Map<String, Integer> t = TIRAGES.getOrDefault(i < 0 ? evenement : evenement.substring(0, i), TIRAGES.get("defaut"));
        if (t == null || t.isEmpty()) return ItemStack.EMPTY;
        int total = t.values().stream().mapToInt(Integer::intValue).sum(), r = ALEA.nextInt(total);
        String type = null;
        for (Map.Entry<String, Integer> e : t.entrySet()) {
            r -= e.getValue();
            if (r < 0) {
                type = e.getKey();
                break;
            }
        }
        // 26/10 (audit) : événement lancé par le staff sans « avec_points » : l'objet est un essai (hérals à la revente,
        // jamais de points), comme la boîte noire de l'hélicoptère ; avant, l'objet du largage donnait quand même des points
        Etat.Ouvert o = Etat.ouvert(evenement);
        String ev = o != null && !o.points() && !evenement.endsWith(SUFFIXE_ESSAI) ? evenement + SUFFIXE_ESSAI : evenement;
        return creerType(type, ev);
    }

    /** Un exemplaire neuf de ce type, signé pour {@code evenement} (« staff:Pseudo » : essai du staff, jamais de points). */
    static ItemStack creerType(String type, String evenement) {
        if (cle == null) return ItemStack.EMPTY;
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(HeralizEvenements.MODID, type));
        if (!(item instanceof ObjetsEvenement.Objet)) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(item);
        String id = UUID.randomUUID().toString();
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Ev", evenement);
        tag.putString("Sig", signer(type, id, evenement));
        s.getOrCreateTag().put(ObjetsEvenement.CLE, tag);
        Outils.journal("objet " + type + " " + id + " cree pour " + evenement);
        return s;
    }

    /**
     * Fin du numéro d'événement d'un objet venu d'un événement lancé par le staff sans « avec_points » (« helico#3!essai ») :
     * même tirage que l'événement, mais essai.
     */
    static final String SUFFIXE_ESSAI = "!essai";

    /**
     * Objet d'essai : donné par le staff ({@code /event objet}) ou venu d'un événement du staff sans points : hérals à la
     * revente, jamais de points de saison.
     */
    static boolean essai(ItemStack s) {
        if (s.getTag() == null) return false;
        String ev = s.getTag().getCompound(ObjetsEvenement.CLE).getString("Ev");
        return ev.startsWith("staff:") || ev.endsWith(SUFFIXE_ESSAI);
    }

    /** Prix de rachat : -1 si ce n'est pas un objet d'événement, 0 s'il ne se vend pas (copie, déjà vendu, fait main). */
    static long prix(ItemStack s) {
        String type = type(s);
        if (type == null) return -1;
        return numeroValide(s, type) != null ? PRIX.getOrDefault(type, 0L) : 0;
    }

    /**
     * Vente d'un exemplaire au marchand : numéro noté « vendu » sur le disque AVANT tout paiement, puis points de saison au
     * clan du vendeur. Rend {prix, points donnés}, ou null s'il ne se vend pas (rien n'est noté).
     */
    @Nullable
    static long[] vendre(ServerPlayer p, ItemStack s) {
        String type = type(s);
        String id = type == null ? null : numeroValide(s, type);
        if (id == null || vendus == null) return null;
        try {
            Files.writeString(vendus, id + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            HeralizEvenements.LOG.error("[evenements] vente de {} refusee : {} impossible a ecrire", id, vendus, ex);
            return null;
        }
        VENDUS.add(id);
        long prix = PRIX.getOrDefault(type, 0L);
        double pts = essai(s) ? 0 : Outils.pointsSaison(p, "objet_" + type);
        Outils.journal("objet " + type + " " + id + " (" + s.getTag().getCompound(ObjetsEvenement.CLE).getString("Ev") + ") vendu par "
                + p.getGameProfile().getName() + " : " + prix + " herals, " + pts + " point(s) de saison");
        return new long[]{prix, Math.round(pts)};
    }

    /** Numéro de l'exemplaire s'il est signé par ce serveur et pas encore vendu, sinon null. */
    @Nullable
    private static String numeroValide(ItemStack s, String type) {
        if (cle == null || s.getTag() == null || !s.getTag().contains(ObjetsEvenement.CLE)) return null;
        CompoundTag t = s.getTag().getCompound(ObjetsEvenement.CLE);
        String id = t.getString("Id"), ev = t.getString("Ev");
        if (id.isEmpty() || VENDUS.contains(id) || !signer(type, id, ev).equals(t.getString("Sig"))) return null;
        return id;
    }

    private static String signer(String type, String id, String evenement) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cle, "HmacSHA256"));
            byte[] h = mac.doFinal((type + "|" + id + "|" + evenement).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 16);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
