package fr.heraliz.grades.serveur;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Dernier kit pris par chaque joueur (heure en ms) : {@code heraliz_grades/recharges.json} à la racine du serveur.
 * Clé : voir {@link #cle} (une seule recharge par kit de Survivant ou de la boutique quel que soit le grade : changer de
 * grade ne permet pas d'en prendre deux). Même règle que
 * le registre des grades : un fichier illisible n'est jamais écrasé et plus aucun kit ne se prend tant qu'il l'est.
 * <p><b>Écritures (15/11, audit perf n° 3c)</b> : avant, le fichier ENTIER était réécrit sur le fil du serveur à chaque
 * kit pris et deux fois par /tpa, et il grossissait toute la saison (une recharge finie depuis des semaines y restait).
 * Maintenant, chaque changement ajoute seulement une petite ligne à la fin d'un journal, tout de suite (quelques dizaines
 * d'octets : la règle « recharge écrite avant de donner le kit » tient, même après un plantage) ; le fichier complet est
 * réécrit sur un fil à part, au plus une fois toutes les 30 s, puis les morceaux du journal qu'il contient sont effacés.
 * Au démarrage, les lignes du journal plus récentes que le fichier complet sont rejouées ; à l'arrêt du serveur, une
 * dernière réécriture complète est attendue. Les recharges finies sont oubliées (dans le fichier et en mémoire) : /tpa
 * fini depuis 10 min ; kit pris depuis plus longtemps que la plus longue recharge réglée, plus un jour.
 */
final class Recharges {
    private static final Logger LOG = LogUtils.getLogger();
    /** Le fichier complet est réécrit au plus une fois par ce délai (secondes) ; entre-temps, le journal suffit. */
    private static final long REGROUPEMENT_S = 30;
    /** Une recharge du /tpa finie depuis ce temps est oubliée. */
    private static final long MARGE_TPA_MS = 10 * 60_000L;
    /** Une recharge de kit est oubliée ce temps après la plus longue recharge réglée. */
    private static final long MARGE_KITS_MS = 24 * 3_600_000L;
    /** Clés du /tpa (TpaServeur) : heure de fin, durée. */
    private static final String TPA = ":tpa", DUREE = "_duree";

    private static final ScheduledExecutorService FIL = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "HeraliZ-recharges");
        t.setDaemon(true);
        return t;
    });
    /** Fichiers ouverts (kits, /tpa) : réécrits une dernière fois à l'arrêt du serveur. */
    private static final Set<Recharges> OUVERTS = ConcurrentHashMap.newKeySet();
    private static boolean arretEcoute;

    private final Path fichier;
    /** Recharges en mémoire ; gardées par {@code this} (fil serveur, et fil d'écriture pour la copie). */
    private final Map<String, Long> derniers = new HashMap<>();
    private boolean illisible;
    /** Numéro du morceau de journal où vont les prochaines lignes (gardé par {@code this}). */
    private long morceau = 1;
    private final AtomicBoolean reecritureDemandee = new AtomicBoolean();

    Recharges(Path dossier) {
        this(dossier, "recharges.json");
    }

    /** Autre fichier du même format (26/10 : {@code recharges_tp.json}, recharges du /tpa). */
    Recharges(Path dossier, String nom) {
        this.fichier = dossier.resolve(nom);
        if (Files.exists(fichier)) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) derniers.put(cleValide(e.getKey()), e.getValue().getAsLong());
            } catch (Exception ex) {
                derniers.clear();
                illisible = true;
                LOG.error("[grades] {} ILLISIBLE ({}) : aucun kit ne peut être pris tant qu'il n'est pas réparé. Le fichier n'est pas touché.", fichier, ex.toString());
                return;
            }
        }
        // journal : changements plus récents que le fichier complet (serveur arrêté brutalement), dans l'ordre
        List<Path> morceaux = morceaux();
        for (Path m : morceaux) rejouer(m);
        if (!morceaux.isEmpty()) morceau = numero(morceaux.get(morceaux.size() - 1)) + 1;
        long ttl = ttlKits();
        if (!morceaux.isEmpty() || !finies(derniers, System.currentTimeMillis(), ttl).isEmpty()) {
            reecrire(ttl);   // démarrage : fichier complet propre tout de suite, journal effacé
        }
        OUVERTS.add(this);
        ecouterArret();
    }

    /** Clé du fichier vérifiée (UUID, éventuellement suivi de « :… ») ; exception sinon. */
    private static String cleValide(String cle) {
        int i = cle.indexOf(':');
        UUID.fromString(i < 0 ? cle : cle.substring(0, i));
        return cle;
    }

    /**
     * Clé d'un kit (2.0.0) : groupe « base » (kits de Survivant et de la boutique, une seule recharge quel que soit le
     * grade), « soutien », ou l'identifiant d'un grade du staff ou spécial. Le kit « kit » des groupes base et soutien garde
     * la clé d'avant la 2.0.0 (UUID, UUID:soutien) : les recharges en cours sont gardées.
     */
    static String cle(UUID uuid, String groupe, String kit) {
        if ("kit".equals(kit) && "base".equals(groupe)) return uuid.toString();
        if ("kit".equals(kit) && "soutien".equals(groupe)) return uuid + ":soutien";
        return uuid + ":" + groupe + ":" + kit;
    }

    boolean illisible() {
        return illisible;
    }

    /** Heure du dernier kit pris (0 = jamais). */
    synchronized long dernier(String cle) {
        return derniers.getOrDefault(cle, 0L);
    }

    /** Note un kit pris et l'écrit tout de suite (journal) ; faux (et rien de changé) si l'écriture échoue. */
    boolean noter(String cle, long quand) {
        if (illisible) return false;
        synchronized (this) {
            Long avant = derniers.put(cle, quand);
            try {
                journaliser(ligne(cle, quand));
            } catch (IOException ex) {
                if (avant == null) derniers.remove(cle);
                else derniers.put(cle, avant);
                LOG.error("[grades] écriture de {} impossible : {}", fichier, ex.toString());
                return false;
            }
        }
        demanderReecriture();
        return true;
    }

    /** Remet toutes les recharges de kits du joueur à zéro ; faux s'il n'en avait pas ou si l'écriture échoue. */
    boolean effacer(UUID uuid) {
        String u = uuid.toString();
        synchronized (this) {
            Map<String, Long> avant = new HashMap<>();
            derniers.forEach((c, t) -> {
                if (c.equals(u) || c.startsWith(u + ":")) avant.put(c, t);
            });
            if (illisible || avant.isEmpty()) return false;
            avant.keySet().forEach(derniers::remove);
            StringBuilder lignes = new StringBuilder();
            for (String c : avant.keySet()) lignes.append(ligne(c, null));
            try {
                journaliser(lignes.toString());
            } catch (IOException ex) {
                derniers.putAll(avant);
                LOG.error("[grades] écriture de {} impossible : {}", fichier, ex.toString());
                return false;
            }
        }
        demanderReecriture();
        return true;
    }

    // ------------------------------------------------------------------ journal
    /** Une ligne du journal : {"c": clé, "t": heure} ; sans « t », la recharge est effacée. */
    private static String ligne(String cle, Long quand) {
        JsonObject o = new JsonObject();
        o.addProperty("c", cle);
        if (quand != null) o.addProperty("t", quand);
        return o + "\n";
    }

    /** Ajoute à la fin du morceau de journal courant (appelé en tenant {@code this}). */
    private void journaliser(String lignes) throws IOException {
        Files.createDirectories(fichier.getParent());
        Files.writeString(chemin(morceau), lignes, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private Path chemin(long n) {
        return fichier.resolveSibling(fichier.getFileName() + ".journal." + n);
    }

    /** Numéro d'un morceau de journal, -1 si le nom n'en est pas un. */
    private long numero(Path p) {
        String debut = fichier.getFileName() + ".journal.";
        String n = p.getFileName().toString();
        if (!n.startsWith(debut)) return -1;
        try {
            return Long.parseLong(n.substring(debut.length()));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Morceaux du journal présents à côté du fichier, dans l'ordre. */
    private List<Path> morceaux() {
        List<Path> l = new ArrayList<>();
        Path dossier = fichier.getParent();
        if (dossier == null || !Files.isDirectory(dossier)) return l;
        try (Stream<Path> s = Files.list(dossier)) {
            s.filter(p -> numero(p) > 0).forEach(l::add);
        } catch (IOException ex) {
            LOG.warn("[grades] journal de {} illisible : {}", fichier, ex.toString());
        }
        l.sort((a, b) -> Long.compare(numero(a), numero(b)));
        return l;
    }

    /** Rejoue un morceau du journal (démarrage) ; une ligne abîmée (arrêt brutal pendant l'écriture) est ignorée. */
    private void rejouer(Path m) {
        try {
            for (String l : Files.readAllLines(m, StandardCharsets.UTF_8)) {
                if (l.isBlank()) continue;
                try {
                    JsonObject o = JsonParser.parseString(l).getAsJsonObject();
                    String c = cleValide(o.get("c").getAsString());
                    if (o.has("t")) derniers.put(c, o.get("t").getAsLong());
                    else derniers.remove(c);
                } catch (RuntimeException ex) {
                    LOG.warn("[grades] ligne abîmée ignorée dans {} : {}", m.getFileName(), l);
                }
            }
        } catch (IOException ex) {
            LOG.error("[grades] journal {} illisible : {}", m, ex.toString());
        }
    }

    // ------------------------------------------------------------------ fichier complet
    /** Une réécriture complète sur le fil d'écriture, dans 30 s (les changements d'ici là en profitent). */
    private void demanderReecriture() {
        if (!reecritureDemandee.compareAndSet(false, true)) return;
        long ttl = ttlKits();   // fil serveur : définitions des kits lues ici
        try {
            FIL.schedule(() -> {
                reecritureDemandee.set(false);
                reecrire(ttl);
            }, REGROUPEMENT_S, TimeUnit.SECONDS);
        } catch (RejectedExecutionException ex) {
            reecritureDemandee.set(false);
        }
    }

    /**
     * Réécrit le fichier complet sans les recharges finies (relevé en tenant {@code this}, les lignes suivantes vont dans
     * un nouveau morceau du journal), puis efface les morceaux qu'il contient et oublie les recharges finies en mémoire.
     */
    private void reecrire(long ttlKits) {
        if (illisible) return;
        try {
            Map<String, Long> copie;
            long fin;
            synchronized (this) {
                fin = morceau++;
                copie = new HashMap<>(derniers);
            }
            Map<String, Long> finies = finies(copie, System.currentTimeMillis(), ttlKits);
            JsonObject o = new JsonObject();
            copie.forEach((c, t) -> {
                if (!finies.containsKey(c)) o.addProperty(c, t);
            });
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling(fichier.getFileName() + ".tmp");
            Files.writeString(tmp, o.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomiqueImpossible) {
                Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING);
            }
            // dans l'ordre, et on s'arrête au premier refus : un vieux morceau resté derrière un plus récent effacé
            // pourrait remettre une ancienne valeur au prochain démarrage
            for (Path m : morceaux()) {
                if (numero(m) > fin) break;
                Files.deleteIfExists(m);
            }
            if (!finies.isEmpty()) {
                synchronized (this) {
                    finies.forEach(derniers::remove);   // seulement si la valeur n'a pas changé entre-temps
                }
            }
        } catch (IOException | RuntimeException ex) {
            LOG.error("[grades] écriture de {} impossible (le journal est gardé) : {}", fichier, ex.toString());
        }
    }

    /**
     * Recharges finies, à oublier : /tpa (clé « …:tpa » = heure de fin, « …:tpa_duree » = durée) fini depuis 10 min ; kit
     * (heure de prise) plus vieux que {@code ttlKits} (0 : aucun kit n'est oublié).
     */
    static Map<String, Long> finies(Map<String, Long> m, long maintenant, long ttlKits) {
        Map<String, Long> out = new HashMap<>();
        for (Map.Entry<String, Long> e : m.entrySet()) {
            String c = e.getKey();
            long v = e.getValue();
            if (c.endsWith(TPA + DUREE)) {
                Long fin = m.get(c.substring(0, c.length() - DUREE.length()));
                if (fin == null || fin + MARGE_TPA_MS < maintenant) out.put(c, v);
            } else if (c.endsWith(TPA) && m.containsKey(c + DUREE)) {
                if (v + MARGE_TPA_MS < maintenant) out.put(c, v);
            } else if (ttlKits > 0 && v + ttlKits < maintenant) {
                out.put(c, v);
            }
        }
        return out;
    }

    /** Plus longue recharge de kit réglée (définitions des grades), plus un jour ; 0 si on ne la connaît pas. */
    private static long ttlKits() {
        try {
            if (DefinitionsGrades.illisible()) return 0;
            long max = -1;
            for (DefinitionsGrades.KitDef k : DefinitionsGrades.kits()) max = Math.max(max, k.rechargeH() * 3_600_000L);
            return max < 0 ? 0 : max + MARGE_KITS_MS;
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ arrêt
    private static synchronized void ecouterArret() {
        if (arretEcoute) return;
        arretEcoute = true;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ServerStoppedEvent.class, e -> fermerTout());
    }

    /** Arrêt du serveur : réécritures en file faites, puis une dernière, complète (journal effacé). Rien n'est perdu. */
    private static void fermerTout() {
        long ttl = ttlKits();
        for (Recharges r : OUVERTS) {
            try {
                FIL.submit(() -> r.reecrire(ttl)).get(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException | RejectedExecutionException ex) {
                LOG.error("[grades] dernière écriture de {} pas terminée (le journal est gardé)", r.fichier, ex);
            }
        }
        OUVERTS.clear();
    }
}
