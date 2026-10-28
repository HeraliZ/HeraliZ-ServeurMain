package fr.heraliz.grades.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.ParseResults;
import com.mojang.logging.LogUtils;
import fr.heraliz.grades.Grade;
import fr.heraliz.grades.staff.ActionStaff;
import fr.heraliz.grades.staff.CommandesStaff;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Onglet « Commandes du Fondateur » du panel du staff (28/10) : le Fondateur donne à un grade du staff (Guide, Helpeur,
 * Modérateur, Admin) le droit d'utiliser UNE commande précise, commande par commande, grade par grade. Gardé à vie hors du
 * monde : {@code heraliz_grades/commandes_staff.json} (jamais touché par la mise en ligne).
 * <p>
 * Le SERVEUR applique la permission, à deux étages :
 * <ul>
 *     <li>commandes de Minecraft et des mods (Brigadier) : à chaque commande d'un membre du staff qui n'est pas op 4,
 *     connecté sur AuthMe, dont le grade a reçu cette racine, la commande est relue avec la permission 4
 *     ({@link CommandEvent}) ; sinon rien ne change (refusée comme avant s'il n'en a pas le droit) ;</li>
 *     <li>commandes des plugins (LiteBans…) et enveloppe Bukkit de Mohist : permissions Bukkit de la commande posées sur le
 *     joueur par une pièce jointe ({@link PontBukkit#accorder}), refaites toutes les 5 s et à chaque changement (grade
 *     retiré, /login pas fait, case décochée : retirées).</li>
 * </ul>
 * Jamais donnables : {@link CommandesStaff#INTERDITES}. Chaque changement et chaque usage d'une commande donnée sont notés
 * au journal du staff. Fil serveur seulement.
 */
final class CommandesDonnees {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Grade[] PAR_RANG = {null, Grade.GUIDE, Grade.HELPER, Grade.MODERATEUR, Grade.ADMIN, Grade.FONDATEUR};
    /** Permissions Bukkit données à tout le staff connecté : voir les sanctions de LiteBans, silencieuses depuis le 28/10. */
    private static final Set<String> STAFF_TOUJOURS = Set.of("litebans.notify.silent");
    private static final int MAX_RACINES = 500, PERIODE_TICKS = 100;

    /** racine -> masque des rangs 1 à 4 (bit r = rang r). */
    private static final Map<String, Integer> MASQUES = new TreeMap<>();
    /** Permissions Bukkit posées sur chaque joueur (pour ne refaire la pièce jointe que si ça change). */
    private static final Map<UUID, Set<String>> APPLIQUE = new HashMap<>();
    /** Permissions Bukkit de chaque racine (table des commandes de Bukkit), vidées à chaque changement et toutes les 5 min. */
    private static final Map<String, Set<String>> PERMS_RACINE = new HashMap<>();
    private static Path fichier;
    private static int ticks;
    private static boolean relecture;

    private CommandesDonnees() {
    }

    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, CommandesDonnees::commande);
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END && ++ticks % PERIODE_TICKS == 0) rafraichirTous(e.getServer(), ticks % 6000 == 0);
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            APPLIQUE.remove(e.getEntity().getUUID());
            PontBukkit.oublierPieces(e.getEntity().getUUID());
        });
    }

    static void demarrer(Path dossier) {
        fichier = dossier.resolve("commandes_staff.json");
        relecture = false;
        MASQUES.clear();
        APPLIQUE.clear();
        PERMS_RACINE.clear();
        if (!Files.exists(fichier)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("commandes");
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                String r = CommandesStaff.racine(e.getKey());
                if (!CommandesStaff.donnable(r) || MASQUES.size() >= MAX_RACINES) continue;
                int m = 0;
                for (JsonElement g : e.getValue().getAsJsonArray()) {
                    for (int k = 1; k < ActionStaff.RANG_MAX; k++) if (PAR_RANG[k].id.equals(g.getAsString())) m |= 1 << k;
                }
                if (m != 0) MASQUES.put(r, m);
            }
            LOG.info("[grades] commandes données aux grades du staff : {} (depuis {})", MASQUES.size(), fichier);
        } catch (Exception ex) {
            // fichier illisible : rien n'est donné (on refuse plutôt que d'accorder par erreur), fichier laissé tel quel
            MASQUES.clear();
            relecture = true;
            LOG.error("[grades] {} illisible ({}) : AUCUNE commande donnée aux grades tant qu'il n'est pas corrigé", fichier, ex.toString());
        }
    }

    static void arreter() {
        fichier = null;
        MASQUES.clear();
        APPLIQUE.clear();
        PERMS_RACINE.clear();
    }

    // ------------------------------------------------------------------ lecture
    static Map<String, Integer> tous() {
        return new TreeMap<>(MASQUES);
    }

    /** Ce rang (1 à 4) a reçu cette racine. Le Fondateur (op 4) a déjà tout : rien à donner. */
    static boolean permis(String racine, int rang) {
        if (rang < 1 || rang >= ActionStaff.RANG_MAX) return false;
        Integer m = MASQUES.get(racine);
        return m != null && (m & 1 << rang) != 0;
    }

    // ------------------------------------------------------------------ changement (Fondateur)
    /** Coche ou décoche une case ; faux si refusé (commande interdite, rang hors 1 à 4, trop de commandes) ou non enregistré. */
    static boolean changer(MinecraftServer srv, String racine, int rang, boolean permis) {
        if (relecture || !CommandesStaff.donnable(racine) || rang < 1 || rang >= ActionStaff.RANG_MAX) return false;
        int avant = MASQUES.getOrDefault(racine, 0), apres = permis ? avant | 1 << rang : avant & ~(1 << rang);
        if (apres == avant) return true;
        if (avant == 0 && MASQUES.size() >= MAX_RACINES) return false;
        if (apres == 0) MASQUES.remove(racine);
        else MASQUES.put(racine, apres);
        if (!sauver()) {
            if (avant == 0) MASQUES.remove(racine);
            else MASQUES.put(racine, avant);
            return false;
        }
        PERMS_RACINE.clear();
        rafraichirTous(srv, true);
        return true;
    }

    private static boolean sauver() {
        if (fichier == null) return false;
        JsonObject commandes = new JsonObject();
        for (Map.Entry<String, Integer> e : MASQUES.entrySet()) {
            JsonArray l = new JsonArray();
            for (int r = 1; r < ActionStaff.RANG_MAX; r++) if ((e.getValue() & 1 << r) != 0) l.add(PAR_RANG[r].id);
            commandes.add(e.getKey(), l);
        }
        JsonObject o = new JsonObject();
        o.addProperty("version", 1);
        o.addProperty("_aide", "Commandes données à un grade du staff par le Fondateur (panel, onglet « Commandes du Fondateur »). "
                + "Racine de la commande -> grades qui peuvent l'utiliser (guide, helper, moderateur, admin).");
        o.add("commandes", commandes);
        try {
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling("commandes_staff.json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomiqueImpossible) {
                Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ex) {
            LOG.error("[grades] {} impossible à écrire : {}", fichier, ex.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------ application : commandes de Minecraft et des mods
    /**
     * Commande tapée par un joueur : s'il est du staff (connecté sur AuthMe), pas op 4, et que son grade a reçu cette racine,
     * la commande est relue avec la permission 4. Les refus qui suivent (combat, carte, match, tutoriel) s'appliquent
     * toujours : ils écoutent le même évènement après nous.
     */
    private static void commande(CommandEvent e) {
        if (MASQUES.isEmpty() || e.isCanceled()) return;
        ParseResults<CommandSourceStack> pr = e.getParseResults();
        CommandSourceStack src = pr.getContext().getSource();
        if (!(src.getEntity() instanceof ServerPlayer p) || p.hasPermissions(4)) return;
        String brut = pr.getReader().getString();
        String racine = CommandesStaff.racine(brut);
        if (racine.isEmpty() || !MASQUES.containsKey(racine) || !CommandesStaff.donnable(racine)) return;
        int rang = PanelStaff.rang(p);
        if (!permis(racine, rang)) return;
        e.setParseResults(p.server.getCommands().getDispatcher().parse(brut, src.withPermission(4)));
        String texte = brut.length() > 150 ? brut.substring(0, 150) + "…" : brut;
        JournalStaff.noterId(p.getGameProfile().getName(), PermsStaff.nomRang(rang), "commande", "", racine, "/" + texte);
    }

    // ------------------------------------------------------------------ application : permissions Bukkit (plugins)
    private static Set<String> permsRacine(String racine) {
        return PERMS_RACINE.computeIfAbsent(racine, PontBukkit::permissionsCommande);
    }

    /** Toutes les 5 s, et à chaque changement : permissions Bukkit de chaque joueur en ligne remises à jour si besoin. */
    private static void rafraichirTous(MinecraftServer srv, boolean relire) {
        if (srv == null) srv = ServerLifecycleHooks.getCurrentServer();
        if (srv == null) return;
        if (relire) PERMS_RACINE.clear();
        RegistreGrades r = ServeurGrades.registre;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            RegistreGrades.Fiche f = r == null ? null : r.fiche(p.getUUID());
            boolean staff = (f != null && f.staff() != null) || p.hasPermissions(4);
            Set<String> voulu = new LinkedHashSet<>();
            if (staff) {
                int rang = PanelStaff.rang(p);
                if (rang > 0) {
                    voulu.addAll(STAFF_TOUJOURS);
                    for (Map.Entry<String, Integer> e : MASQUES.entrySet()) {
                        if (rang < ActionStaff.RANG_MAX && (e.getValue() & 1 << rang) != 0) voulu.addAll(permsRacine(e.getKey()));
                    }
                }
            }
            Set<String> avant = APPLIQUE.get(p.getUUID());
            if (voulu.isEmpty() && avant == null) continue;
            if (voulu.equals(avant)) continue;
            if (PontBukkit.accorder(p, voulu)) {
                if (voulu.isEmpty()) APPLIQUE.remove(p.getUUID());
                else APPLIQUE.put(p.getUUID(), voulu);
                if (!voulu.isEmpty()) LOG.info("[grades] {} : {} permission(s) Bukkit de commandes données par le Fondateur", p.getGameProfile().getName(), voulu.size());
            }
        }
    }
}
