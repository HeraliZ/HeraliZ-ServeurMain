package fr.heralisz.clans.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.LienJoueur;
import fr.heralisz.clans.model.Membre;
import fr.heralisz.clans.model.Niveaux;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Stockage des clans et des liens joueurs : <dossier serveur>/heralisz_clans/clans.json et joueurs.json. Global au
 * serveur (independant du monde : survit a un wipe de map). Ecriture atomique (fichier temporaire puis renommage),
 * sauvegarde toutes les 60 s si quelque chose a change, a l'arret (ServerStopping puis ServerStopped), et a la demande
 * (apres toute operation d'argent).
 * Index par id, tag et nom (insensibles a la casse) et par joueur. Toujours utilise depuis le thread serveur.
 */
public final class ClanStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int AUTOSAVE_TICKS = 20 * 60;
    private static ClanStore instance;

    private final Path dossier;
    private final Map<UUID, Clan> clans = new LinkedHashMap<>();
    private final Map<String, Clan> parTag = new HashMap<>();
    private final Map<String, Clan> parNom = new HashMap<>();
    private final Map<UUID, LienJoueur> liens = new HashMap<>();
    private boolean dirtyClans, dirtyJoueurs;
    private int ticks;

    private ClanStore(Path dossier) {
        this.dossier = dossier;
    }

    /** Le store du serveur en cours (null hors serveur / avant le demarrage). */
    @Nullable
    public static ClanStore get() {
        return instance;
    }

    public Path dossier() {
        return dossier;
    }

    // ------------------------------------------------------------------ cycle de vie
    @SubscribeEvent
    public static void onServerStart(ServerAboutToStartEvent e) {
        MinecraftServer server = e.getServer();
        Path racine = server.getServerDirectory().toPath().resolve("heralisz_clans");
        instance = new ClanStore(racine);
        instance.charger();
        Niveaux.charger(FMLPaths.CONFIGDIR.get().resolve("heralisz_clans"));
        Economie.init(instance);
        HeraliszClans.LOG.info("[clans] {} clan(s), {} joueur(s) charges depuis {}", instance.clans.size(), instance.liens.size(), racine);
    }

    /**
     * Arret : Forge poste ServerStoppingEvent avant la deconnexion des joueurs, donc on note ici leur « vu le » puis on
     * sauvegarde tout ; le store reste disponible pour les PlayerLoggedOutEvent qui suivent et n'est ferme (derniere
     * sauvegarde de ce qui a change) qu'a ServerStoppedEvent.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        if (instance == null) return;
        instance.noterVus(e.getServer(), System.currentTimeMillis());
        instance.sauvegarder(true);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        if (instance == null) return;
        instance.sauvegarder(false);
        CoffreClan.fermer();
        instance = null;
    }

    /** Met a jour le « vu le » des membres connectes (sans marquer sale : la prochaine sauvegarde l'emportera). */
    public void noterVus(MinecraftServer server, long now) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Clan c = clanDe(p.getUUID());
            Membre m = c == null ? null : c.membre(p.getUUID());
            if (m != null) m.vuLe = now;
        }
    }

    /**
     * Sauvegarde du monde (automatique ou /save-all) : le coffre du clan est écrit en même temps que les joueurs (25/10,
     * audit : sinon, coffre ouvert, il ne l'était que toutes les 60 s et un plantage entre les deux copiait ou perdait un
     * objet).
     */
    @SubscribeEvent
    public static void onSauvegardeMonde(net.minecraftforge.event.level.LevelEvent.Save e) {
        if (instance == null || !(e.getLevel() instanceof net.minecraft.server.level.ServerLevel l)
                || l.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        CoffreClan.sauvegarder();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || instance == null) return;
        if (++instance.ticks >= AUTOSAVE_TICKS) {
            instance.ticks = 0;
            instance.sauvegarder(false);
        }
    }

    // ------------------------------------------------------------------ acces
    public Collection<Clan> tous() {
        return Collections.unmodifiableCollection(clans.values());
    }

    @Nullable
    public Clan get(UUID id) {
        return id == null ? null : clans.get(id);
    }

    @Nullable
    public Clan parTag(String tag) {
        return tag == null ? null : parTag.get(tag.toLowerCase(Locale.ROOT));
    }

    @Nullable
    public Clan parNom(String nom) {
        return nom == null ? null : parNom.get(nom.toLowerCase(Locale.ROOT));
    }

    /** Le clan d'un joueur (null s'il n'en a pas). */
    @Nullable
    public Clan clanDe(UUID joueur) {
        LienJoueur l = liens.get(joueur);
        if (l == null || l.clan == null) return null;
        Clan c = clans.get(l.clan);
        if (c == null || !c.estMembre(joueur)) {
            // lien perime (clan supprime a la main) : on le repare
            l.clan = null;
            dirtyJoueurs = true;
            return null;
        }
        return c;
    }

    /** Lien d'un joueur, cree au besoin. */
    public LienJoueur lien(UUID joueur) {
        return liens.computeIfAbsent(joueur, u -> {
            dirtyJoueurs = true;
            return new LienJoueur(u);
        });
    }

    public Collection<LienJoueur> liens() {
        return Collections.unmodifiableCollection(liens.values());
    }

    // ------------------------------------------------------------------ mutations
    /** Ajoute un clan (tag et nom supposes valides et libres) et lie ses membres. */
    public void ajouter(Clan c) {
        clans.put(c.id, c);
        indexer(c);
        for (UUID u : c.membres.keySet()) lien(u).clan = c.id;
        dirtyClans = dirtyJoueurs = true;
    }

    /**
     * Supprime un clan (dissolution) et detache ses membres : aucun delai de rejoindre (ils n'ont rien quitte, le
     * delai de 12 h est reserve a « quitter / exclu »), mode /cc coupe.
     */
    public void supprimer(Clan c) {
        clans.remove(c.id);
        desindexer(c);
        for (UUID u : c.membres.keySet()) {
            LienJoueur l = lien(u);
            if (c.id.equals(l.clan)) l.clan = null;
            l.quitteLe = 0;
            l.chatClan = false;
        }
        dirtyClans = dirtyJoueurs = true;
    }

    /** A appeler apres un changement de tag ou de nom (les anciennes cles sont retirees). */
    public void reindexer(Clan c) {
        parTag.values().removeIf(x -> x == c);
        parNom.values().removeIf(x -> x == c);
        indexer(c);
        dirtyClans = true;
    }

    private void indexer(Clan c) {
        parTag.put(c.tag.toLowerCase(Locale.ROOT), c);
        parNom.put(c.nom.toLowerCase(Locale.ROOT), c);
    }

    private void desindexer(Clan c) {
        parTag.remove(c.tag.toLowerCase(Locale.ROOT), c);
        parNom.remove(c.nom.toLowerCase(Locale.ROOT), c);
    }

    public boolean tagLibre(String tag) {
        return parTag(tag) == null;
    }

    public boolean nomLibre(String nom) {
        return parNom(nom) == null;
    }

    /** Signale une modification des clans (sauvegardee au prochain autosave). */
    public void marquerDirty() {
        dirtyClans = true;
    }

    /** Signale une modification des liens joueurs. */
    public void marquerDirtyJoueurs() {
        dirtyJoueurs = true;
    }

    // ------------------------------------------------------------------ fichiers
    private Path fClans() {
        return dossier.resolve("clans.json");
    }

    private Path fJoueurs() {
        return dossier.resolve("joueurs.json");
    }

    /**
     * Vrai si clans.json ou joueurs.json n'a pas pu etre lu en entier au demarrage (25/10, audit : avant, une seule faute
     * dans le fichier, par exemple une retouche a la main, faisait partir le serveur sans clans, puis la sauvegarde de
     * l'arret ecrasait le fichier : clans, banques et membres perdus). Le fichier d'origine est d'abord copie a cote
     * ({@code clans.json.illisible-<date>}) ; les operateurs sont prevenus a la connexion.
     */
    private boolean lectureIncomplete;

    public boolean lectureIncomplete() {
        return lectureIncomplete;
    }

    /** Copie d'un fichier illisible (ou lu en partie) a cote de lui, avant toute reecriture. */
    private void mettreDeCote(Path f, String pourquoi) {
        lectureIncomplete = true;
        Path copie = f.resolveSibling(f.getFileName() + ".illisible-" + System.currentTimeMillis());
        try {
            Files.copy(f, copie, StandardCopyOption.REPLACE_EXISTING);
            HeraliszClans.LOG.error("[clans] {} : {} ; copie de l'original gardee dans {}", f, pourquoi, copie);
        } catch (IOException ex) {
            HeraliszClans.LOG.error("[clans] {} : {} ; copie de l'original impossible", f, pourquoi, ex);
        }
    }

    private void charger() {
        clans.clear();
        parTag.clear();
        parNom.clear();
        liens.clear();
        lectureIncomplete = false;
        // les deux fichiers sont lus a part, entree par entree : une entree abimee ne fait plus perdre les autres
        if (Files.exists(fClans())) {
            int ignores = 0;
            try {
                JsonObject root = JsonParser.parseString(Files.readString(fClans(), StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement e : root.getAsJsonArray("clans")) {
                    if (!e.isJsonObject()) continue;
                    Clan c;
                    try {
                        c = Clan.fromJson(e.getAsJsonObject());
                    } catch (RuntimeException ex) {
                        ignores++;
                        HeraliszClans.LOG.error("[clans] entree de clans.json illisible, ignoree", ex);
                        continue;
                    }
                    if (c == null) continue;
                    if (parTag(c.tag) != null || parNom(c.nom) != null) {
                        HeraliszClans.LOG.warn("[clans] clan {} ({}) en double dans clans.json, ignore", c.tag, c.id);
                        continue;
                    }
                    clans.put(c.id, c);
                    indexer(c);
                }
            } catch (IOException | RuntimeException ex) {
                HeraliszClans.LOG.error("[clans] lecture de {} impossible", fClans(), ex);
                mettreDeCote(fClans(), "fichier illisible, " + clans.size() + " clan(s) lus avant l'erreur");
                ignores = 0;
            }
            if (ignores > 0) mettreDeCote(fClans(), ignores + " clan(s) illisible(s) ignores");
        }
        if (Files.exists(fJoueurs())) {
            int ignores = 0;
            try {
                JsonObject root = JsonParser.parseString(Files.readString(fJoueurs(), StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement e : root.getAsJsonArray("joueurs")) {
                    if (!e.isJsonObject()) continue;
                    try {
                        LienJoueur l = LienJoueur.fromJson(e.getAsJsonObject());
                        if (l != null) liens.put(l.joueur, l);
                    } catch (RuntimeException ex) {
                        ignores++;
                    }
                }
            } catch (IOException | RuntimeException ex) {
                HeraliszClans.LOG.error("[clans] lecture de {} impossible", fJoueurs(), ex);
                mettreDeCote(fJoueurs(), "fichier illisible");
                ignores = 0;
            }
            if (ignores > 0) mettreDeCote(fJoueurs(), ignores + " joueur(s) illisible(s) ignores");
        }
        // coherence : chaque membre connait son clan
        for (Clan c : clans.values()) {
            for (UUID u : c.membres.keySet()) {
                LienJoueur l = lien(u);
                if (!c.id.equals(l.clan)) {
                    l.clan = c.id;
                    dirtyJoueurs = true;
                }
            }
        }
    }

    /** Sauvegarde ce qui a change (tout si force). */
    public void sauvegarder(boolean force) {
        try {
            if (force || dirtyClans) {
                JsonObject root = new JsonObject();
                root.addProperty("version", 1);
                JsonArray a = new JsonArray();
                for (Clan c : clans.values()) a.add(c.toJson());
                root.add("clans", a);
                ecrireAtomique(fClans(), root);
                dirtyClans = false;
            }
            if (force || dirtyJoueurs) {
                JsonObject root = new JsonObject();
                root.addProperty("version", 1);
                JsonArray a = new JsonArray();
                for (LienJoueur l : liens.values()) a.add(l.toJson());
                root.add("joueurs", a);
                ecrireAtomique(fJoueurs(), root);
                dirtyJoueurs = false;
            }
        } catch (IOException ex) {
            HeraliszClans.LOG.error("[clans] sauvegarde impossible dans {}", dossier, ex);
        }
        CoffreClan.sauvegarder();
    }

    /** Sauvegarde immediate (apres toute operation d'argent). */
    public void sauvegarderMaintenant() {
        dirtyClans = dirtyJoueurs = true;
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

    /** Liste triee pour le classement : niveau puis xp totale decroissants. */
    public List<Clan> classement() {
        List<Clan> l = new ArrayList<>(clans.values());
        l.sort((a, b) -> {
            if (a.niveau != b.niveau) return Integer.compare(b.niveau, a.niveau);
            return Long.compare(b.xpTotale(), a.xpTotale());
        });
        return l;
    }
}
