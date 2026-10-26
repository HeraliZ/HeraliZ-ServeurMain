package fr.heralisz.economie.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.economie.EconomieConfig;
import fr.heralisz.economie.HeraliszEconomie;
import fr.heralisz.economie.api.Compte;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stockage des comptes : {@code <dossier serveur>/heralisz_economie/comptes.json}, global au serveur (independant du
 * monde : survit a un wipe de map). Toujours mute depuis le fil serveur ({@link #verifierFil()}).
 *
 * <p><b>Sauvegardes.</b> L'instantane JSON est construit sur le fil serveur, l'ecriture (JSON compact, fichier
 * temporaire puis renommage atomique) se fait sur un fil IO unique : les ecritures sont sequentielles, la derniere
 * gagne. Autosave toutes les 60 s si quelque chose a change ; une transaction d'un montant >= sauvegarde_immediate
 * demande une sauvegarde groupee (au plus une ecriture par 1,5 s) ; a l'arret et via
 * {@link #sauvegarderMaintenant()} l'ecriture est synchrone. A chaque sauvegarde reussie, l'ancien fichier est
 * conserve en copie tournante comptes.json.1 … .5.
 *
 * <p><b>Protection des soldes.</b> Si le fichier existe mais ne peut pas etre lu (verrou, JSON casse), le store
 * demarre vide et <i>toute ecriture est bloquee</i> (autosave, transactions, arret) tant qu'une relecture
 * ({@code /eco recharger}) n'a pas reussi ou qu'un operateur n'a pas leve le blocage ({@code /eco sauvegarder
 * --forcer}, qui met d'abord le fichier illisible de cote en comptes.json.corrompu-&lt;date&gt;). Les operateurs
 * connectes sont prevenus a la connexion et toutes les 5 min.
 *
 * <p><b>Lecture depuis d'autres fils.</b> {@link #soldeMiroir(UUID)} et {@link #resoudre(String)} s'appuient sur des
 * tables concurrentes tenues a jour a chaque mutation (pont Vault appele en asynchrone par des plugins).
 *
 * <pre>
 * {
 *   "version": 1,
 *   "comptes": {
 *     "&lt;uuid&gt;": { "solde": 1250, "dernierNom": "Joueur",
 *                 "journal": [ { "quand": 1760000000000, "delta": 250, "apres": 1250, "motif": "quete:zombies", "autre": "&lt;uuid&gt;" } ] }
 *   }
 * }
 * </pre>
 */
public final class ComptesStore {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int AUTOSAVE_TICKS = 20 * 60;
    /** Delai de regroupement des sauvegardes « urgentes » (1,5 s). */
    private static final int DEBOUNCE_TICKS = 30;
    /** Rappel aux operateurs toutes les 5 min tant que la sauvegarde est bloquee. */
    private static final int ALERTE_TICKS = 20 * 300;
    /** Nombre de copies tournantes comptes.json.1 … .N. */
    private static final int ROTATION = 5;
    /** Attente maximale d'une sauvegarde synchrone. */
    private static final long ATTENTE_SYNC_S = 30;
    /** Le classement n'est retrie qu'au plus une fois par seconde. */
    private static final long CLASSEMENT_CACHE_MS = 1000;
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Nullable
    private static ComptesStore instance;

    private final MinecraftServer server;
    private final Path fichier;
    private final Map<UUID, Compte> comptes = new LinkedHashMap<>();
    /** Miroir uuid -> solde lisible depuis n'importe quel fil (pont Vault asynchrone). */
    private final ConcurrentHashMap<UUID, Long> miroirSoldes = new ConcurrentHashMap<>();
    /** Index dernier nom (minuscules) -> compte, tenu a jour au fil de l'eau (resolution par nom sans lookup reseau). */
    private final ConcurrentHashMap<String, Compte> indexNoms = new ConcurrentHashMap<>();
    /** Fil IO unique : ecritures sequentielles, la derniere gagne. */
    private final ExecutorService io;
    /** Positionne par le fil IO quand une ecriture a echoue : le store se remarque modifie au tick suivant. */
    private final AtomicBoolean reessayer = new AtomicBoolean();

    private boolean dirty;
    /** Vrai tant que le fichier n'a pas pu etre lu : aucune ecriture n'est autorisee. */
    private boolean chargementEchoue;
    private int ticksAutosave, ticksAlerte;
    private long tick, echeanceDebounce;
    /** Compteur de mutations (invalide le cache du classement). */
    private long version;
    private List<Compte> cacheClassement = List.of();
    private long cacheVersion = -1, cacheQuand;

    private ComptesStore(MinecraftServer server, Path fichier) {
        this.server = server;
        this.fichier = fichier;
        this.io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "HeraliZ-Economie-IO");
            t.setDaemon(true);
            return t;
        });
    }

    /** Le store du serveur en cours (null hors serveur / avant le demarrage). */
    @Nullable
    public static ComptesStore get() {
        return instance;
    }

    public MinecraftServer server() {
        return server;
    }

    public Path fichier() {
        return fichier;
    }

    /**
     * Le store pret, sur le fil serveur. Leve IllegalStateException sinon : l'API est reservee au fil serveur
     * (les autres fils doivent passer par {@code server.execute(...)}).
     */
    public static ComptesStore verifierFil() {
        ComptesStore s = instance;
        if (s == null) throw new IllegalStateException("HeraliZ Économie : serveur non démarré (économie indisponible)");
        if (!s.server.isSameThread()) throw new IllegalStateException("HeraliZ Économie : l'API doit être appelée depuis le fil serveur");
        return s;
    }

    // ------------------------------------------------------------------ cycle de vie
    @SubscribeEvent
    public static void onServerStart(ServerAboutToStartEvent e) {
        MinecraftServer server = e.getServer();
        Path f = server.getServerDirectory().toPath().resolve("heralisz_economie").resolve("comptes.json");
        instance = new ComptesStore(server, f);
        instance.charger();
        HeraliszEconomie.LOG.info("[economie] {} compte(s) charge(s) depuis {} ; unite « {} / {} »", instance.comptes.size(), f,
                EconomieConfig.NOM_SINGULIER.get(), EconomieConfig.NOM_PLURIEL.get());
    }

    @SubscribeEvent
    public static void onServerStop(ServerStoppingEvent e) {
        ComptesStore s = instance;
        if (s == null) return;
        s.sauvegarderMaintenant();
        s.fermer();
        instance = null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        ComptesStore s = instance;
        if (e.phase != TickEvent.Phase.END || s == null) return;
        s.tick++;
        if (s.reessayer.compareAndSet(true, false)) s.dirty = true;
        if (s.echeanceDebounce != 0 && s.tick >= s.echeanceDebounce) {
            s.echeanceDebounce = 0;
            s.sauvegarder(false);
        }
        if (++s.ticksAutosave >= AUTOSAVE_TICKS) {
            s.ticksAutosave = 0;
            s.sauvegarder(false);
        }
        if (s.chargementEchoue && ++s.ticksAlerte >= ALERTE_TICKS) {
            s.ticksAlerte = 0;
            s.alerterOps();
        }
    }

    /** Arrete le fil IO (apres la derniere ecriture synchrone). */
    private void fermer() {
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) HeraliszEconomie.LOG.warn("[economie] le fil IO ne s'est pas arrete a temps");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ acces
    /** Compte existant (null si le joueur n'en a jamais eu). */
    @Nullable
    public Compte get(UUID uuid) {
        return uuid == null ? null : comptes.get(uuid);
    }

    /** Compte du joueur, cree au besoin avec le solde de depart (journalise « depart » si > 0). */
    public Compte compte(UUID uuid, @Nullable String nom) {
        Compte c = comptes.get(uuid);
        if (c == null) {
            long depart = EconomieConfig.SOLDE_DEPART.get();
            c = new Compte(uuid, 0, nom);
            if (depart > 0) c.appliquer(Math.min(depart, EconomieConfig.MAX_SOLDE.get()), "depart", null);
            comptes.put(uuid, c);
            miroirSoldes.put(uuid, c.solde());
            indexer(c, null);
            changer();
        } else if (nom != null && !nom.isEmpty() && !nom.equals(c.dernierNom())) {
            String ancien = c.dernierNom();
            c.setDernierNom(nom);
            indexer(c, ancien);
            changer();
        }
        return c;
    }

    public Collection<Compte> tous() {
        return Collections.unmodifiableCollection(comptes.values());
    }

    /**
     * Les n comptes les plus riches (solde decroissant, puis nom). Liste non modifiable, servie depuis un cache retrie
     * au plus une fois par seconde quand les comptes ont change.
     */
    public List<Compte> classement(int n) {
        long now = System.currentTimeMillis();
        if (cacheVersion != version && (cacheVersion < 0 || now - cacheQuand >= CLASSEMENT_CACHE_MS)) {
            List<Compte> l = new ArrayList<>(comptes.values());
            l.sort((a, b) -> {
                if (a.solde() != b.solde()) return Long.compare(b.solde(), a.solde());
                return a.dernierNom().compareToIgnoreCase(b.dernierNom());
            });
            cacheClassement = Collections.unmodifiableList(l);
            cacheVersion = version;
            cacheQuand = now;
        }
        return cacheClassement.subList(0, Math.min(Math.max(0, n), cacheClassement.size()));
    }

    /**
     * Retrouve un joueur par son nom : en ligne d'abord (sur le fil serveur), puis dernier nom connu d'un compte
     * (insensible a la casse). Jamais de lookup reseau ni de cache de profils : un joueur jamais venu est inconnu.
     * Utilisable depuis n'importe quel fil. Null si inconnu.
     */
    @Nullable
    public UUID resoudre(String nom) {
        if (nom == null || nom.isEmpty()) return null;
        if (server.isSameThread()) {
            ServerPlayer p = server.getPlayerList().getPlayerByName(nom);
            if (p != null) return p.getUUID();
        }
        Compte c = indexNoms.get(nom.toLowerCase(Locale.ROOT));
        return c == null ? null : c.uuid();
    }

    /** Nom affichable d'un compte : dernier nom connu, sinon l'uuid abrege. */
    public String nomDe(UUID uuid) {
        ServerPlayer p = server.getPlayerList().getPlayer(uuid);
        if (p != null) return p.getGameProfile().getName();
        Compte c = comptes.get(uuid);
        if (c != null && !c.dernierNom().isEmpty()) return c.dernierNom();
        String s = uuid.toString();
        return s.substring(0, 8);
    }

    /** Solde depuis n'importe quel fil (miroir des comptes ; 0 sans compte). Sur le fil serveur, identique a l'API. */
    public long soldeMiroir(UUID uuid) {
        Long v = uuid == null ? null : miroirSoldes.get(uuid);
        return v == null ? 0 : v;
    }

    /** Vrai tant que le fichier n'a pas pu etre lu : la sauvegarde est bloquee (voir la javadoc de la classe). */
    public boolean chargementEchoue() {
        return chargementEchoue;
    }

    // ------------------------------------------------------------------ mutations
    /**
     * Applique un delta a un compte (journal, miroir des soldes, store marque modifie). Reserve a
     * {@code EconomieApi} : le solde final est suppose deja valide (>= 0, <= plafond). Ne declenche pas de sauvegarde :
     * l'appelant appelle {@link #marquerDirty(long)} une fois par operation.
     */
    public void appliquer(Compte c, long delta, String motif, @Nullable UUID autre) {
        c.appliquer(delta, motif, autre);
        miroirSoldes.put(c.uuid(), c.solde());
        changer();
    }

    /** Signale un changement ; demande une sauvegarde groupee si le montant atteint sauvegarde_immediate. */
    public void marquerDirty(long montant) {
        changer();
        if (Math.abs(montant) >= EconomieConfig.SAUVEGARDE_IMMEDIATE.get()) sauvegarderBientot();
    }

    private void changer() {
        dirty = true;
        version++;
    }

    private void indexer(Compte c, @Nullable String ancienNom) {
        if (ancienNom != null && !ancienNom.isEmpty()) indexNoms.remove(ancienNom.toLowerCase(Locale.ROOT), c);
        if (!c.dernierNom().isEmpty()) indexNoms.put(c.dernierNom().toLowerCase(Locale.ROOT), c);
    }

    // ------------------------------------------------------------------ lecture du fichier
    /**
     * Lit le fichier dans une table neuve puis remplace la memoire ; fichier absent = aucun compte (succes). En cas
     * d'echec la memoire est inchangee et le blocage des ecritures est pose. Vrai si la lecture a reussi.
     */
    private boolean charger() {
        Map<UUID, Compte> lus = new LinkedHashMap<>();
        if (Files.exists(fichier)) {
            try {
                JsonObject root = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject cs = root.has("comptes") && root.get("comptes").isJsonObject() ? root.getAsJsonObject("comptes") : new JsonObject();
                for (Map.Entry<String, JsonElement> e : cs.entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    UUID u;
                    try {
                        u = UUID.fromString(e.getKey());
                    } catch (IllegalArgumentException ex) {
                        HeraliszEconomie.LOG.warn("[economie] cle « {} » ignoree dans comptes.json (pas un uuid)", e.getKey());
                        continue;
                    }
                    Compte c = Compte.fromJson(u, e.getValue().getAsJsonObject());
                    if (c != null) lus.put(u, c);
                }
            } catch (IOException | RuntimeException ex) {
                chargementEchoue = true;
                ticksAlerte = 0;
                HeraliszEconomie.LOG.error("[economie] lecture de {} impossible : SAUVEGARDE BLOQUEE pour proteger les soldes. "
                        + "Corrigez le fichier puis /eco recharger, ou /eco sauvegarder --forcer pour repartir des comptes en memoire "
                        + "(le fichier illisible sera conserve en comptes.json.corrompu-<date>)", fichier, ex);
                return false;
            }
        }
        comptes.clear();
        comptes.putAll(lus);
        miroirSoldes.clear();
        indexNoms.clear();
        for (Compte c : lus.values()) {
            miroirSoldes.put(c.uuid(), c.solde());
            indexer(c, null);
        }
        chargementEchoue = false;
        dirty = false;
        echeanceDebounce = 0;
        version++;
        cacheVersion = -1;
        return true;
    }

    /**
     * Relit le fichier (apres une edition a la main) ; ce qui n'etait pas sauvegarde est perdu. Faux si la lecture
     * echoue : la memoire est alors inchangee et la sauvegarde (re)bloquee jusqu'a une lecture reussie ou --forcer.
     */
    public boolean recharger() {
        return charger();
    }

    /**
     * Leve le blocage pose par une lecture echouee. Le fichier illisible est d'abord mis de cote en
     * comptes.json.corrompu-&lt;date&gt; (chemin renvoye ; null s'il n'existait pas ou si rien n'etait bloque).
     * IOException si la mise de cote echoue : le blocage reste.
     */
    @Nullable
    public Path leverBlocage() throws IOException {
        if (!chargementEchoue) return null;
        Path copie = null;
        if (Files.exists(fichier)) {
            copie = fichier.resolveSibling(fichier.getFileName() + ".corrompu-" + HORODATAGE.format(LocalDateTime.now()));
            Files.move(fichier, copie);
            HeraliszEconomie.LOG.warn("[economie] blocage leve par un operateur : fichier illisible conserve en {}", copie);
        }
        chargementEchoue = false;
        ticksAlerte = 0;
        dirty = true;
        return copie;
    }

    /** Previent les operateurs connectes (niveau 2) que la sauvegarde est bloquee. */
    private void alerterOps() {
        Component msg = messageBlocage();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.hasPermissions(2)) p.sendSystemMessage(msg);
        }
    }

    /** Message de blocage a un operateur (connexion). */
    public void alerter(ServerPlayer p) {
        if (chargementEchoue) p.sendSystemMessage(messageBlocage());
    }

    private Component messageBlocage() {
        return Component.translatable("economie.msg.admin.chargement_echoue", fichier.getFileName().toString()).withStyle(ChatFormatting.RED);
    }

    // ------------------------------------------------------------------ sauvegardes
    /** Demande une sauvegarde groupee : au plus une ecriture par 1,5 s (fil IO). */
    public void sauvegarderBientot() {
        dirty = true;
        if (echeanceDebounce == 0) echeanceDebounce = tick + DEBOUNCE_TICKS;
    }

    /**
     * Sauvegarde asynchrone si quelque chose a change (toujours si force) : instantane sur le fil serveur, ecriture
     * sur le fil IO. Refusee tant que le chargement a echoue.
     */
    public void sauvegarder(boolean force) {
        if (!force && !dirty) return;
        if (refuserSiBloque()) return;
        soumettre(instantane(), false);
    }

    /**
     * Sauvegarde synchrone (operations critiques des autres mods, arret du serveur) : attend la fin de l'ecriture,
     * derriere les ecritures asynchrones deja en file. Vrai si le fichier a ete ecrit. Refusee tant que le
     * chargement a echoue.
     */
    public boolean sauvegarderMaintenant() {
        if (refuserSiBloque()) return false;
        return soumettre(instantane(), true);
    }

    private boolean refuserSiBloque() {
        if (!chargementEchoue) return false;
        HeraliszEconomie.LOG.error("[economie] sauvegarde refusee : {} n'a pas pu etre lu ; corrigez le fichier puis /eco recharger, ou /eco sauvegarder --forcer",
                fichier);
        return true;
    }

    /** Instantane JSON des comptes (fil serveur) ; le store n'est plus marque modifie. */
    private JsonObject instantane() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject cs = new JsonObject();
        for (Compte c : comptes.values()) cs.add(c.uuid().toString(), c.toJson());
        root.add("comptes", cs);
        dirty = false;
        echeanceDebounce = 0;
        return root;
    }

    /** Confie l'ecriture au fil IO (ou l'ecrit sur place si l'executeur est ferme) ; attend le resultat si demande. */
    private boolean soumettre(JsonObject root, boolean attendre) {
        Future<Boolean> f;
        try {
            f = io.submit(() -> ecrire(root));
        } catch (RejectedExecutionException ex) {
            return ecrire(root);
        }
        if (!attendre) return true;
        try {
            return f.get(ATTENTE_SYNC_S, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            HeraliszEconomie.LOG.error("[economie] sauvegarde synchrone : pas de reponse du fil IO apres {} s", ATTENTE_SYNC_S);
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException ex) {
            HeraliszEconomie.LOG.error("[economie] sauvegarde synchrone en echec", ex.getCause());
            return false;
        }
    }

    /** Fil IO : JSON compact dans un fichier temporaire, rotation des copies, renommage atomique. */
    private boolean ecrire(JsonObject root) {
        try {
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling(fichier.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, w);
            }
            tourner();
            try {
                Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ex) {
            HeraliszEconomie.LOG.error("[economie] sauvegarde impossible dans {} (nouvel essai a la prochaine sauvegarde)", fichier, ex);
            reessayer.set(true);
            return false;
        }
    }

    /** Fil IO : comptes.json.4 -> .5 … .1 -> .2, puis copie du fichier courant en .1. Un echec n'empeche pas la sauvegarde. */
    private void tourner() {
        if (!Files.exists(fichier)) return;
        try {
            for (int i = ROTATION; i >= 2; i--) {
                Path de = copie(i - 1);
                if (Files.exists(de)) Files.move(de, copie(i), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.copy(fichier, copie(1), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            HeraliszEconomie.LOG.warn("[economie] rotation des copies de secours impossible : {}", ex.toString());
        }
    }

    private Path copie(int i) {
        return fichier.resolveSibling(fichier.getFileName() + "." + i);
    }

    /** Index nom -> compte (insensible a la casse), copie instantanee pour un usage ponctuel. */
    public Map<String, Compte> parNom() {
        return new HashMap<>(indexNoms);
    }
}
