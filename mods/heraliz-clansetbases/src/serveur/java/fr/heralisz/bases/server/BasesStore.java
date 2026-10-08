package fr.heralisz.bases.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.model.Base;
import fr.heralisz.bases.model.EntreeJour;
import fr.heralisz.bases.model.FenetreVue;
import fr.heralisz.bases.model.Json;
import fr.heralisz.bases.model.Retour;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Donnees globales du serveur (BASES_SPEC §7) : {@code <dossier serveur>/heralisz_bases/bases.json} — bases (clan ->
 * parcelle, salles achetees, etat, journal de pillage, donnees libres du module pillage), positions de retour des
 * joueurs, entrees de pillage du jour, mode force de la fenetre de pillage. Independant du monde, ecriture atomique
 * (fichier temporaire puis renommage), sauvegarde toutes les 60 s si modifie, a l'arret, et tout de suite apres toute
 * operation d'argent ({@link #sauvegarderMaintenant()}). Fil serveur uniquement.
 */
public final class BasesStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int AUTOSAVE_TICKS = 20 * 60;
    private static BasesStore instance;

    private final Path dossier;
    private final Map<UUID, Base> bases = new LinkedHashMap<>();
    private final Map<Integer, Base> parParcelle = new HashMap<>();
    private final Map<UUID, Retour> retours = new HashMap<>();
    private final Map<UUID, EntreeJour> entreesJour = new HashMap<>();
    private FenetreVue.Mode fenetre = FenetreVue.Mode.AUTO;
    private boolean dirty;
    private int ticks;

    private BasesStore(Path dossier) {
        this.dossier = dossier;
    }

    /** Le store du serveur en cours (null hors serveur / avant le demarrage). */
    @Nullable
    public static BasesStore get() {
        return instance;
    }

    // ------------------------------------------------------------------ cycle de vie
    @SubscribeEvent
    public static void onServerStart(ServerAboutToStartEvent e) {
        MinecraftServer server = e.getServer();
        Path racine = server.getServerDirectory().toPath().resolve("heralisz_bases");
        instance = new BasesStore(racine);
        instance.charger();
        HeraliszBases.LOG.info("[bases] {} base(s), {} retour(s) charges depuis {}", instance.bases.size(), instance.retours.size(), racine);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        if (instance != null) instance.sauvegarder(true);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        if (instance == null) return;
        instance.sauvegarder(false);
        instance = null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || instance == null) return;
        if (++instance.ticks >= AUTOSAVE_TICKS) {
            instance.ticks = 0;
            instance.sauvegarder(false);
        }
    }

    // ------------------------------------------------------------------ bases
    public Collection<Base> bases() {
        return Collections.unmodifiableCollection(bases.values());
    }

    @Nullable
    public Base base(@Nullable UUID clan) {
        return clan == null ? null : bases.get(clan);
    }

    @Nullable
    public Base parParcelle(int parcelle) {
        return parParcelle.get(parcelle);
    }

    /** Base par dernier tag connu (insensible a la casse), pour l'admin quand le clan n'existe plus. */
    @Nullable
    public Base parTagConnu(String tag) {
        if (tag == null) return null;
        String t = tag.toLowerCase(Locale.ROOT);
        for (Base b : bases.values()) if (b.tag.toLowerCase(Locale.ROOT).equals(t)) return b;
        return null;
    }

    public void ajouter(Base b) {
        bases.put(b.clan, b);
        parParcelle.put(b.parcelle, b);
        dirty = true;
    }

    public void retirer(Base b) {
        bases.remove(b.clan, b);
        parParcelle.remove(b.parcelle, b);
        dirty = true;
    }

    /** Plus petite parcelle libre (ni base, ni parcelle occupee dans le monde selon {@code occupee}). */
    public int parcelleLibre(java.util.function.IntPredicate occupee) {
        for (int i = 0; ; i++) {
            if (!parParcelle.containsKey(i) && !occupee.test(i)) return i;
        }
    }

    // ------------------------------------------------------------------ retours
    @Nullable
    public Retour retour(UUID joueur) {
        return retours.get(joueur);
    }

    public void retour(UUID joueur, @Nullable Retour r) {
        if (r == null) {
            if (retours.remove(joueur) != null) dirty = true;
        } else {
            retours.put(joueur, r);
            dirty = true;
        }
    }

    // ------------------------------------------------------------------ entrees de pillage du jour (module pillage)
    @Nullable
    public EntreeJour entreeJour(UUID joueur) {
        return entreesJour.get(joueur);
    }

    public Map<UUID, EntreeJour> entreesJour() {
        return Collections.unmodifiableMap(entreesJour);
    }

    public void entreeJour(EntreeJour e) {
        entreesJour.put(e.joueur(), e);
        dirty = true;
    }

    /** Oublie les entrees d'un autre jour que {@code jour} (appele au changement de jour). */
    public void purgerEntrees(String jour) {
        if (entreesJour.values().removeIf(e -> !e.jour().equals(jour))) dirty = true;
    }

    // ------------------------------------------------------------------ fenetre forcee
    public FenetreVue.Mode fenetre() {
        return fenetre;
    }

    public void fenetre(FenetreVue.Mode m) {
        fenetre = m == null ? FenetreVue.Mode.AUTO : m;
        dirty = true;
    }

    // ------------------------------------------------------------------ fichier
    public void marquerDirty() {
        dirty = true;
    }

    private Path fichier() {
        return dossier.resolve("bases.json");
    }

    private void charger() {
        try {
            if (!Files.exists(fichier())) return;
            JsonObject root = JsonParser.parseString(Files.readString(fichier(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : Json.arr(root, "bases")) {
                if (!e.isJsonObject()) continue;
                Base b = Base.fromJson(e.getAsJsonObject());
                if (b == null) continue;
                if (bases.containsKey(b.clan) || parParcelle.containsKey(b.parcelle)) {
                    HeraliszBases.LOG.warn("[bases] base {} (parcelle {}) en double dans bases.json, ignoree", b.tag, b.parcelle);
                    continue;
                }
                bases.put(b.clan, b);
                parParcelle.put(b.parcelle, b);
            }
            JsonObject r = Json.obj(root, "retours");
            if (r != null) {
                for (Map.Entry<String, JsonElement> e : r.entrySet()) {
                    try {
                        Retour x = e.getValue().isJsonObject() ? Retour.fromJson(e.getValue().getAsJsonObject()) : null;
                        if (x != null) retours.put(UUID.fromString(e.getKey()), x);
                    } catch (IllegalArgumentException ignore) {
                        // uuid illisible
                    }
                }
            }
            for (JsonElement e : Json.arr(root, "entreesJour")) {
                if (!e.isJsonObject()) continue;
                EntreeJour x = EntreeJour.fromJson(e.getAsJsonObject());
                if (x != null) entreesJour.put(x.joueur(), x);
            }
            try {
                fenetre = FenetreVue.Mode.valueOf(Json.str(root, "fenetre", "AUTO").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                fenetre = FenetreVue.Mode.AUTO;
            }
        } catch (IOException | RuntimeException ex) {
            // fichier illisible : on garde une copie pour l'admin et on repart a vide (jamais d'ecrasement silencieux)
            HeraliszBases.LOG.error("[bases] lecture de {} impossible", fichier(), ex);
            try {
                Files.copy(fichier(), dossier.resolve("bases.json.illisible-" + System.currentTimeMillis()));
            } catch (IOException ignore) {
                // rien de plus a faire
            }
        }
    }

    /** Sauvegarde si quelque chose a change (tout si force). */
    public void sauvegarder(boolean force) {
        if (!force && !dirty) return;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.addProperty("fenetre", fenetre.name());
            JsonArray a = new JsonArray();
            for (Base b : bases.values()) a.add(b.toJson());
            root.add("bases", a);
            JsonObject r = new JsonObject();
            for (Map.Entry<UUID, Retour> e : retours.entrySet()) r.add(e.getKey().toString(), e.getValue().toJson());
            root.add("retours", r);
            JsonArray j = new JsonArray();
            for (EntreeJour e : entreesJour.values()) j.add(e.toJson());
            root.add("entreesJour", j);
            ecrireAtomique(fichier(), root);
            dirty = false;
        } catch (IOException ex) {
            HeraliszBases.LOG.error("[bases] sauvegarde impossible dans {}", dossier, ex);
        }
    }

    /** Sauvegarde immediate (apres toute operation d'argent). */
    public void sauvegarderMaintenant() {
        dirty = true;
        sauvegarder(false);
    }

    private static void ecrireAtomique(Path f, JsonObject o) throws IOException {
        Files.createDirectories(f.getParent());
        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
