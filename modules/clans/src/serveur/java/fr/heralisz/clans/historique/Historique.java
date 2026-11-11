package fr.heralisz.clans.historique;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.heralisz.clans.ClansConfig;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.model.Clan;
import fr.heralisz.clans.model.Evenement;
import fr.heralisz.clans.model.Json;
import fr.heralisz.clans.model.LigneHistorique;
import fr.heralisz.clans.network.ClansNet;
import fr.heralisz.clans.network.Packets;
import fr.heralisz.clans.server.ClanStore;
import fr.heralisz.clans.server.EcrituresFond;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static fr.heralisz.clans.model.LigneHistorique.BASE;
import static fr.heralisz.clans.model.LigneHistorique.CLAN;
import static fr.heralisz.clans.model.LigneHistorique.COMBATS;
import static fr.heralisz.clans.model.LigneHistorique.MEMBRES;
import static fr.heralisz.clans.model.LigneHistorique.POINTS;
import static fr.heralisz.clans.model.LigneHistorique.PVE;
import static fr.heralisz.clans.model.LigneHistorique.PVP;
import static fr.heralisz.clans.model.LigneHistorique.SANS;

/**
 * Historique du clan (11/11, demande du proprietaire : « une page Historique avec toutes les logs de son clan : gain de
 * points, perte de points, mort de telle personne, kill de telle personne, gain de permission, join de clan, etc., et
 * pouvoir trier par categorie »). Cote serveur seulement, fil serveur seulement.
 * <ul>
 * <li>Rempli par les vraies sources : points de saison avec leur raison et le joueur ({@code saison.Saison}), morts et
 * kills des membres ({@code server.ClanEvents}), droits des grades ({@code server.ClanService}), et chaque ligne du
 * journal d'activite du clan ({@link Clan#ajouterEvenement} : arrivees, departs, exclusions, grades, permissions,
 * captures, niveau, ameliorations, banniere, quetes de clan, et les lignes des autres mods par {@code ClansApi.journal} :
 * coeur et armoire de CRac, ClashWar).</li>
 * <li>Petites actions (zombies, conteneurs : moins d'un point chacune) regroupees : une ligne par joueur et par source
 * toutes les {@code regroupement_minutes} (« Zombies tues x37 · +0,37 ») ; celles en cours sont montrees aussi.</li>
 * <li>Borne : {@code lignes_max} lignes par clan, {@code jours_max} jours ({@code [historique]} de
 * heralisz_clans-common.toml) ; garde hors du monde dans {@code heralisz_clans/historique/<clan>.json} (survit a une
 * nouvelle carte), ecrit sur le fil d'ecriture des clans au plus une fois par minute et a l'arret, lu a la premiere
 * ligne du clan apres le demarrage.</li>
 * <li>Envoye au joueur par pages de {@link #PAGE}, filtrees par categorie ICI ({@link #page}) ; aucun cout par tick
 * (deux compteurs ; travail toutes les 20 s et toutes les minutes seulement s'il y a quelque chose).</li>
 * </ul>
 */
public final class Historique {
    private Historique() {
    }

    /** Lignes par page envoyee au joueur. */
    public static final int PAGE = 40;
    private static final Gson GSON = new Gson();

    /** Lignes de chaque clan charge, de la plus ancienne a la plus recente. */
    private static final Map<UUID, ArrayList<LigneHistorique>> LIGNES = new HashMap<>();
    /** Clans dont le fichier est a reecrire. */
    private static final Set<UUID> SALES = new HashSet<>();
    /** Ecritures ratees (fil d'ecriture) : le clan redevient « a reecrire » au tick suivant. */
    private static final Set<UUID> ECHECS = ConcurrentHashMap.newKeySet();
    /** Petites actions en cours de regroupement : « clan|joueur|source » -> regroupement. */
    private static final Map<String, Regroupement> REGROUPEMENTS = new LinkedHashMap<>();
    /** Derniere demande de page de chaque joueur (une toutes les 150 ms au plus). */
    private static final Map<UUID, Long> DEMANDES = new HashMap<>();
    @Nullable
    private static Path dossier;
    private static int ticks;

    /** Petites actions d'un joueur pour une source, depuis {@code premier}. */
    private static final class Regroupement {
        final UUID clan;
        final String qui, source;
        final long premier;
        long dernier;
        double nombre, points;

        Regroupement(UUID clan, String qui, String source, long premier) {
            this.clan = clan;
            this.qui = qui;
            this.source = source;
            this.premier = premier;
        }

        LigneHistorique ligne() {
            return new LigneHistorique(dernier, POINTS, PVE, qui, "historique.source." + source, List.of(entier(nombre)), centiemes(points));
        }
    }

    /** Une fois, au chargement de la partie serveur (apres le magasin des clans : il est lu avant au demarrage). */
    public static void installer() {
        Clan.HISTORIQUE = Historique::evenement;
        MinecraftForge.EVENT_BUS.register(Historique.class);
    }

    // ------------------------------------------------------------------ cycle de vie
    @SubscribeEvent
    public static void onStart(ServerAboutToStartEvent e) {
        LIGNES.clear();
        SALES.clear();
        ECHECS.clear();
        REGROUPEMENTS.clear();
        DEMANDES.clear();
        dossier = e.getServer().getServerDirectory().toPath().resolve("heralisz_clans").resolve("historique");
    }

    /** Apres le demarrage : fichiers des clans qui n'existent plus et plus vieux que {@code jours_max} effaces (en fond). */
    @SubscribeEvent
    public static void onStarted(ServerStartedEvent e) {
        ClanStore s = ClanStore.get();
        Path dir = dossier;
        if (s == null || dir == null) return;
        Set<String> existants = new HashSet<>();
        for (Clan c : s.tous()) existants.add(c.id + ".json");
        long limite = System.currentTimeMillis() - reglage(ClansConfig.HISTORIQUE_JOURS, 30) * 86_400_000L;
        EcrituresFond.puis(() -> {
            if (!Files.isDirectory(dir)) return;
            int n = 0;
            try (DirectoryStream<Path> fichiers = Files.newDirectoryStream(dir, "*.json")) {
                for (Path f : fichiers) {
                    if (existants.contains(f.getFileName().toString())) continue;
                    if (Files.getLastModifiedTime(f).toMillis() < limite) {
                        Files.deleteIfExists(f);
                        n++;
                    }
                }
            } catch (IOException | RuntimeException ex) {
                HeraliszClans.LOG.warn("[historique] menage des clans dissous impossible : {}", ex.toString());
            }
            if (n > 0) HeraliszClans.LOG.info("[historique] {} historique(s) de clans dissous efface(s)", n);
        });
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || dossier == null) return;
        if (++ticks % 400 == 0) finirRegroupements(false);
        if (ticks % 1200 == 0) {
            for (Iterator<UUID> it = ECHECS.iterator(); it.hasNext(); ) {
                SALES.add(it.next());
                it.remove();
            }
            if (!SALES.isEmpty()) sauver();
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) {
        if (dossier == null) return;
        finirRegroupements(true);
        sauver();
    }

    // ------------------------------------------------------------------ ecriture des lignes
    /**
     * Une ligne pour le clan {@code c}. {@code points} : points de saison gagnes (perdus si negatif), 0 = aucun ;
     * {@code saison} : leur categorie ({@link LigneHistorique#PVE}...) ; {@code args} : textes du message (un argument
     * qui commence par « ~ » est une cle de langue).
     */
    public static void ajouter(@Nullable Clan c, int categorie, int saison, @Nullable String qui, String cle, double points, String... args) {
        if (c == null || dossier == null || cle == null || cle.isEmpty()) return;
        long cent = centiemes(points);
        ajouterLigne(c.id, new LigneHistorique(System.currentTimeMillis(), categorie, cent == 0 ? SANS : saison, qui == null ? "" : qui, cle,
                liste(args), cent));
    }

    /** Petite action (zombie, conteneur...) : regroupee par joueur et par source ({@code regroupement_minutes}). */
    public static void petite(@Nullable Clan c, String qui, String source, double quantite, double points) {
        if (c == null || dossier == null) return;
        String cle = c.id + "|" + qui + "|" + source;
        Regroupement r = REGROUPEMENTS.get(cle);
        long now = System.currentTimeMillis();
        if (r == null) {
            r = new Regroupement(c.id, qui, source, now);
            REGROUPEMENTS.put(cle, r);
        }
        r.dernier = now;
        r.nombre += quantite;
        r.points += points;
    }

    /** Mort d'un membre du clan {@code c} : tue par un joueur, par un zombie, ou autre ; {@code points} perdus (PVP). */
    public static void mort(@Nullable Clan c, ServerPlayer mort, @Nullable Entity src, double points) {
        if (c == null) return;
        String nom = mort.getGameProfile().getName();
        if (src instanceof Player tueur && tueur != mort) ajouter(c, COMBATS, PVP, "", "historique.mort.joueur", points, nom, nomEtTag(tueur));
        else if (src instanceof Enemy) ajouter(c, COMBATS, PVP, "", "historique.mort.zombie", points, nom);
        else ajouter(c, COMBATS, PVP, "", "historique.mort.autre", points, nom);
    }

    /** Un membre du clan {@code c} a tue un joueur ; {@code points} gagnes (PVP, 0 : le kill ne comptait pas). */
    public static void kill(@Nullable Clan c, ServerPlayer tueur, Player victime, double points) {
        if (c == null) return;
        ajouter(c, COMBATS, PVP, "", "historique.kill", points, tueur.getGameProfile().getName(), nomEtTag(victime));
    }

    /** « Pseudo [TAG] » (ou le pseudo seul, joueur sans clan). */
    private static String nomEtTag(Player p) {
        ClanStore s = ClanStore.get();
        Clan c = s == null ? null : s.clanDe(p.getUUID());
        String nom = p.getGameProfile().getName();
        return c == null ? nom : nom + " [" + c.tag + "]";
    }

    /** Une ligne du journal d'activite du clan, reprise telle quelle (texte traduit chez le joueur comme dans l'Apercu). */
    static void evenement(Clan c, Evenement e) {
        if (dossier == null || c == null) return;
        int categorie = switch (e.genre()) {
            case ARRIVEE, DEPART, EXCLUSION, PROMOTION, RETROGRADATION, TRANSFERT, PERMISSION -> MEMBRES;
            case CAPTURE, ZONE_PERDUE, RAVITAILLEMENT -> BASE;
            // lignes des autres mods (ClansApi.journal) : coeur et armoire de CRac dans « Base », le reste dans « Clan »
            case PERSO -> !e.args().isEmpty() && e.args().get(0).startsWith("crac.") ? BASE : CLAN;
            default -> CLAN;
        };
        ajouterLigne(c.id, new LigneHistorique(e.quand(), categorie, SANS, "", LigneHistorique.EVENEMENT + e.genre().name(),
                liste(e.args().toArray(new String[0])), 0));
    }

    private static void ajouterLigne(UUID clan, LigneHistorique x) {
        ArrayList<LigneHistorique> l = lignes(clan);
        int i = l.size();
        // une ligne de petites actions arrive avec l'heure de sa derniere action : rangee a sa place
        while (i > 0 && l.get(i - 1).quand() > x.quand()) i--;
        l.add(i, x);
        int max = reglage(ClansConfig.HISTORIQUE_LIGNES, 500);
        long limite = System.currentTimeMillis() - reglage(ClansConfig.HISTORIQUE_JOURS, 30) * 86_400_000L;
        int retirer = Math.max(0, l.size() - max);
        while (retirer < l.size() && l.get(retirer).quand() < limite) retirer++;
        if (retirer > 0) l.subList(0, retirer).clear();
        SALES.add(clan);
    }

    /** Petites actions dont le regroupement est fini (toutes avec {@code tout}) : une ligne chacune. */
    private static void finirRegroupements(boolean tout) {
        if (REGROUPEMENTS.isEmpty()) return;
        long now = System.currentTimeMillis(), duree = reglage(ClansConfig.HISTORIQUE_REGROUPEMENT_MIN, 30) * 60_000L;
        for (Iterator<Regroupement> it = REGROUPEMENTS.values().iterator(); it.hasNext(); ) {
            Regroupement r = it.next();
            if (!tout && now - r.premier < duree) continue;
            it.remove();
            ajouterLigne(r.clan, r.ligne());
        }
    }

    // ------------------------------------------------------------------ pages (onglet Historique)
    /**
     * Page {@code page} (0 = la plus recente) des lignes du clan du joueur dans la categorie {@code categorie}
     * ({@link LigneHistorique#TOUT} = tout), petites actions en cours comprises, envoyee au joueur.
     */
    public static void page(ServerPlayer p, int categorie, int page) {
        long now = System.currentTimeMillis();
        Long avant = DEMANDES.get(p.getUUID());
        if (avant != null && now - avant >= 0 && now - avant < 150) return;
        if (DEMANDES.size() > 512) DEMANDES.clear();
        DEMANDES.put(p.getUUID(), now);
        int filtre = categorie < LigneHistorique.TOUT || categorie >= LigneHistorique.CATEGORIES ? LigneHistorique.TOUT : categorie;
        ClanStore s = ClanStore.get();
        Clan c = s == null ? null : s.clanDe(p.getUUID());
        if (c == null || dossier == null) {
            ClansNet.toPlayer(p, new Packets.HistoriqueS2C(filtre, 0, 1, 0, List.of()));
            return;
        }
        List<LigneHistorique> choisies = new ArrayList<>();
        for (Regroupement r : REGROUPEMENTS.values()) {
            if (r.clan.equals(c.id)) {
                LigneHistorique x = r.ligne();
                if (x.dans(filtre)) choisies.add(x);
            }
        }
        ArrayList<LigneHistorique> l = lignes(c.id);
        for (int i = l.size() - 1; i >= 0; i--) {
            LigneHistorique x = l.get(i);
            if (x.dans(filtre)) choisies.add(x);
        }
        choisies.sort(Comparator.comparingLong(LigneHistorique::quand).reversed());
        int total = choisies.size(), pages = Math.max(1, (total + PAGE - 1) / PAGE), n = Math.max(0, Math.min(pages - 1, page));
        List<LigneHistorique> morceau = new ArrayList<>(choisies.subList(Math.min(total, n * PAGE), Math.min(total, (n + 1) * PAGE)));
        ClansNet.toPlayer(p, new Packets.HistoriqueS2C(filtre, n, pages, total, morceau));
    }

    // ------------------------------------------------------------------ fichiers
    /** Lignes du clan : lues dans son fichier a la premiere demande apres le demarrage (quelques dizaines de Ko au plus). */
    private static ArrayList<LigneHistorique> lignes(UUID clan) {
        ArrayList<LigneHistorique> l = LIGNES.get(clan);
        if (l == null) {
            l = lire(clan);
            LIGNES.put(clan, l);
        }
        return l;
    }

    private static ArrayList<LigneHistorique> lire(UUID clan) {
        ArrayList<LigneHistorique> l = new ArrayList<>();
        Path dir = dossier;
        if (dir == null) return l;
        Path f = dir.resolve(clan + ".json");
        if (!Files.exists(f)) return l;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : Json.arr(o, "lignes")) if (e.isJsonObject()) l.add(LigneHistorique.fromJson(e.getAsJsonObject()));
            l.sort(Comparator.comparingLong(LigneHistorique::quand));
        } catch (IOException | RuntimeException ex) {
            // jamais ecrase : copie a cote, l'historique de ce clan repart vide (journal du serveur)
            Path copie = f.resolveSibling(f.getFileName() + ".illisible-" + System.currentTimeMillis());
            try {
                Files.copy(f, copie, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignore) {
                // rien de plus a faire
            }
            HeraliszClans.LOG.error("[historique] {} illisible (copie : {}), historique du clan repart vide", f, copie, ex);
            l.clear();
        }
        return l;
    }

    /** Fichiers des clans changes : releve sur le fil serveur (lignes immuables), ecriture sur le fil d'ecriture des clans. */
    private static void sauver() {
        Path dir = dossier;
        if (dir == null) return;
        ClanStore s = ClanStore.get();
        for (UUID id : SALES) {
            ArrayList<LigneHistorique> l = LIGNES.get(id);
            if (l == null) continue;
            if (s != null && s.get(id) == null) {
                LIGNES.remove(id);   // clan dissous : oublie (son fichier reste jusqu'au menage du demarrage)
                continue;
            }
            List<LigneHistorique> copie = List.copyOf(l);
            Path f = dir.resolve(id + ".json");
            EcrituresFond.ecrire(f, () -> ecrire(f, copie), () -> ECHECS.add(id));
        }
        SALES.clear();
    }

    private static void ecrire(Path f, List<LigneHistorique> l) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("version", 1);
        JsonArray a = new JsonArray();
        for (LigneHistorique x : l) a.add(x.toJson());
        o.add("lignes", a);
        Files.createDirectories(f.getParent());
        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ------------------------------------------------------------------ outils
    private static List<String> liste(@Nullable String[] args) {
        if (args == null || args.length == 0) return List.of();
        List<String> l = new ArrayList<>(Math.min(args.length, LigneHistorique.ARGS_MAX));
        for (int i = 0; i < args.length && i < LigneHistorique.ARGS_MAX; i++) {
            l.add(fr.heralisz.clans.model.Texte.couper(args[i] == null ? "" : args[i], LigneHistorique.ARG_MAX));
        }
        return l;
    }

    /** Points de saison en centiemes (arrondis : -0,995 → -1,00). */
    static long centiemes(double points) {
        return Math.round(points * 100);
    }

    /** Nombre sans decimale inutile : 37, 2,5 (pour un texte). */
    static String entier(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-6 ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    /** Reglage du fichier heralisz_clans-common.toml, ou sa valeur par defaut s'il n'est pas encore lu. */
    private static int reglage(ForgeConfigSpec.IntValue v, int defaut) {
        try {
            return v.get();
        } catch (RuntimeException ex) {
            return defaut;
        }
    }
}
