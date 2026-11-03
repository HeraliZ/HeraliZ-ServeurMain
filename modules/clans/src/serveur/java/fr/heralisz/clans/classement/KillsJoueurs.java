package fr.heralisz.clans.classement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.Membre;
import fr.heralisz.clans.server.ClanStore;
import fr.heralisz.clans.server.EcrituresFond;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Kills de joueurs de chaque joueur, pour l'hologramme « Meilleurs tueurs » (03/11). Compte tous les joueurs, avec ou sans
 * clan, avec les memes regles que les statistiques des clans ({@code ClanEvents.onDeath} : jamais en match, ni un bot de
 * charge, ni un camarade de clan, ni la meme adresse IP, ni une victime tout juste reapparue, ni le meme couple avant
 * {@code anti_boost_couple_min}). Jamais de zombies. Avant ce compteur, seuls les membres d'un clan avaient des kills
 * (depuis leur arrivee dans leur clan) : au premier demarrage, il part de ces chiffres.
 * <p>
 * Fichier {@code heralisz_clans/kills_joueurs.json} (hors du monde), ecrit sur le fil d'ecriture des clans au plus une
 * fois par minute et a l'arret. Remis a zero par {@code /wipe <joueur>} et {@code /wipe tout}. Fil serveur.
 */
public final class KillsJoueurs {
    private KillsJoueurs() {
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** Kills et dernier pseudo connu d'un joueur. */
    static final class Fiche {
        int kills;
        String nom;

        Fiche(int kills, String nom) {
            this.kills = kills;
            this.nom = nom == null ? "" : nom;
        }
    }

    private static final Map<UUID, Fiche> FICHES = new HashMap<>();
    private static Path fichier;
    private static boolean sale;
    /** Change a chaque kill compte (le classement n'est retrie que s'il a change). */
    private static int version;
    private static int ticks;
    private static final java.util.concurrent.atomic.AtomicBoolean ECHEC = new java.util.concurrent.atomic.AtomicBoolean();

    // ------------------------------------------------------------------ cycle de vie
    @SubscribeEvent
    public static void onStart(ServerStartedEvent e) {
        FICHES.clear();
        version++;
        fichier = e.getServer().getServerDirectory().toPath().resolve("heralisz_clans").resolve("kills_joueurs.json");
        if (Files.exists(fichier)) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> x : o.entrySet()) {
                    try {
                        JsonObject f = x.getValue().getAsJsonObject();
                        int k = f.has("kills") ? f.get("kills").getAsInt() : 0;
                        if (k > 0) FICHES.put(UUID.fromString(x.getKey()), new Fiche(k, f.has("nom") ? f.get("nom").getAsString() : ""));
                    } catch (RuntimeException ignore) {
                        HeraliszClans.LOG.warn("[classements] ligne « {} » ignoree dans {}", x.getKey(), fichier.getFileName());
                    }
                }
                HeraliszClans.LOG.info("[classements] kills de {} joueur(s) charges", FICHES.size());
            } catch (IOException | RuntimeException ex) {
                // fichier illisible : on ne l'ecrase pas (copie de cote), on repart des statistiques des clans
                HeraliszClans.LOG.error("[classements] {} illisible : mis de cote, kills repris des statistiques des clans", fichier, ex);
                try {
                    Files.move(fichier, fichier.resolveSibling("kills_joueurs.illisible-" + System.currentTimeMillis() + ".json"),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignore) {
                }
                depuisLesClans();
            }
        } else {
            depuisLesClans();
        }
    }

    /** Premier demarrage : les kills des membres des clans (comptes depuis leur arrivee dans le clan). */
    private static void depuisLesClans() {
        ClanStore s = ClanStore.get();
        if (s == null) return;
        for (Clan c : s.tous()) {
            for (Membre m : c.membres.values()) {
                if (m.stats != null && m.stats.kills > 0) FICHES.put(m.uuid, new Fiche(m.stats.kills, m.dernierNom));
            }
        }
        HeraliszClans.LOG.info("[classements] kills de depart repris des clans : {} joueur(s)", FICHES.size());
        sale = true;
        sauvegarder();
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) {
        if (sale) sauvegarder();
        fichier = null;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || fichier == null) return;
        if (++ticks < 1200) return;
        ticks = 0;
        if (ECHEC.getAndSet(false)) sale = true;
        if (sale) sauvegarder();
    }

    private static void sauvegarder() {
        if (fichier == null) return;
        JsonObject o = new JsonObject();
        for (Map.Entry<UUID, Fiche> x : FICHES.entrySet()) {
            JsonObject f = new JsonObject();
            f.addProperty("kills", x.getValue().kills);
            f.addProperty("nom", x.getValue().nom);
            o.add(x.getKey().toString(), f);
        }
        Path f = fichier;
        sale = false;
        EcrituresFond.ecrire(f, () -> {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }, () -> ECHEC.set(true));
    }

    // ------------------------------------------------------------------ comptes
    /** Un kill de joueur valide (regles verifiees par l'appelant, {@code ClanEvents.onDeath}). */
    public static void ajouter(UUID tueur, String nom) {
        if (fichier == null) return;
        Fiche f = FICHES.computeIfAbsent(tueur, u -> new Fiche(0, nom));
        f.kills++;
        if (nom != null && !nom.isEmpty()) f.nom = nom;
        sale = true;
        version++;
    }

    /** {@code /wipe <joueur>} : ses kills repartent de 0. */
    public static void effacer(UUID joueur) {
        if (FICHES.remove(joueur) != null) {
            sale = true;
            version++;
            sauvegarder();
        }
    }

    /** {@code /wipe tout} : tous les kills repartent de 0. */
    public static void toutEffacer() {
        FICHES.clear();
        sale = true;
        version++;
        sauvegarder();
    }

    static int version() {
        return version;
    }

    /** Une ligne du classement : joueur, kills, dernier pseudo garde ici. */
    record Rang(UUID joueur, int kills, String nom) {
    }

    /** Les {@code n} premiers (kills decroissants, puis pseudo), sans ceux que {@code exclu} refuse. */
    static List<Rang> top(int n, Predicate<UUID> exclu) {
        List<Rang> l = new ArrayList<>(FICHES.size());
        for (Map.Entry<UUID, Fiche> x : FICHES.entrySet()) {
            if (x.getValue().kills > 0) l.add(new Rang(x.getKey(), x.getValue().kills, x.getValue().nom));
        }
        l.sort((a, b) -> a.kills() != b.kills() ? Integer.compare(b.kills(), a.kills()) : a.nom().compareToIgnoreCase(b.nom()));
        List<Rang> out = new ArrayList<>(n);
        for (Rang r : l) {
            if (out.size() >= n) break;
            if (!exclu.test(r.joueur())) out.add(r);
        }
        return out;
    }
}
