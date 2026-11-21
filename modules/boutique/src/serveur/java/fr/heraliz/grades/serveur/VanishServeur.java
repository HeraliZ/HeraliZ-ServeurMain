package fr.heraliz.grades.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddPlayerPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Outils de modération du staff (2.7.0) : « Se cacher » et « Cacher son nom », boutons du panneau du staff (touche P,
 * {@link PanelStaff}). 02/11 (audit point 36) : les commandes {@code /staff vanish} et {@code /staff nametag} (op 4
 * connectés sur AuthMe), qui faisaient la même chose que ces boutons, sont retirées ; {@code /staff panel} reste.
 * <ul>
 *    <li>se cacher : le joueur disparaît pour tous ceux qui ne sont pas du staff (liste Tab, entité, nom au-dessus de la
 *    tête), sans message d'arrivée ni de départ ; il ne pousse plus personne et les créatures ne le prennent plus pour
 *    cible. Le staff (op 4 connecté) le voit toujours, marqué « (caché) » dans le tab. Par Bukkit ({@code hidePlayer}) sur
 *    Mohist, sinon par les paquets de Minecraft (liste et entité retirées chez chaque joueur, à chaque nouveau suivi).</li>
 *    <li>cacher son nom : son nom au-dessus de la tête caché (ou remontré) pour tout le monde (équipe du tableau des scores
 *    {@code hz_sansnom}, nom jamais affiché).</li>
 * </ul>
 * Les deux états sont gardés dans {@code heraliz_grades/staff vanish.json} à la racine du serveur (hors du monde) : ils restent
 * après une déconnexion ou un redémarrage. Fil serveur seulement (sauf {@link #cache}, lu aussi par l'écouteur Bukkit).
 */
public final class VanishServeur {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Équipes du tableau des scores : cachés (nom jamais affiché, aucune poussée) et noms cachés seulement. */
    private static final String EQUIPE_CACHE = "hz_cache", EQUIPE_SANS_NOM = "hz_sansnom";

    private static final Set<UUID> CACHES = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SANS_NOM = ConcurrentHashMap.newKeySet();
    /** Pour chaque voyant connecté : les joueurs qu'on lui cache en ce moment. */
    private static final Map<UUID, Set<UUID>> MASQUES = new HashMap<>();
    /**
     * Réponses permises à un membre du staff caché (21/11, choix du propriétaire) : joueur hors du staff -> (membre du staff
     * caché qui lui a écrit lui-même par /msg, /tell ou /w -> fin de la fenêtre, ms). Ce joueur seul peut lui répondre
     * pendant {@link #REPONSE_MS} ; oublié à la déconnexion de l'un des deux et quand le staff se montre ou se recache.
     */
    private static final Map<UUID, Map<UUID, Long>> REPONSES = new HashMap<>();
    private static final long REPONSE_MS = 5 * 60_000L;
    private static Path fichier;

    private VanishServeur() {
    }

    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(VanishServeur::suivi);
        MinecraftForge.EVENT_BUS.addListener(VanishServeur::cible);
        // 21/11 (décision du propriétaire : « le cacher partout ») : /msg, /tell, /w vers un caché, comme vers un absent
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL, false,
                net.minecraftforge.event.CommandEvent.class, VanishServeur::messagePrive);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStartedEvent e) -> PontBukkit.brancherConnexions(VanishServeur::cache));
    }

    // ------------------------------------------------------------------ état
    /** Vrai si ce joueur est caché par le vanish du staff (« Se cacher » du panneau). Lu aussi par d'autres mods ({@link GradesApi#cache}). */
    static boolean cache(UUID joueur) {
        return joueur != null && CACHES.contains(joueur);
    }

    static boolean sansNom(UUID joueur) {
        return joueur != null && SANS_NOM.contains(joueur);
    }

    /** Droit aux outils de modération : op de niveau 4, connecté sur AuthMe. */
    static boolean droit(ServerPlayer p) {
        return p != null && p.hasPermissions(4) && PontBukkit.authentifie(p);
    }

    /** Racine /staff : la console, un op 4 connecté, ou un joueur du staff (n'importe quel grade du staff). */
    static boolean racineStaff(CommandSourceStack s) {
        if (s.getEntity() == null) return s.hasPermission(4);
        return s.getEntity() instanceof ServerPlayer p && (droit(p) || (ServeurGrades.registre != null
                && ServeurGrades.registre.staffAuMoins(p.getUUID(), fr.heraliz.grades.Grade.GUIDE) && PontBukkit.authentifie(p)));
    }

    /**
     * Ce joueur voit les joueurs cachés : op 4 connecté sur AuthMe, ou (25/10, panel du staff) un joueur du staff connecté
     * sur AuthMe, quel que soit son grade du staff (un Guide doit voir le staff caché qui l'aide).
     */
    static boolean voitLesCaches(ServerPlayer p) {
        return droit(p) || (p != null && ServeurGrades.registre != null
                && ServeurGrades.registre.staffAuMoins(p.getUUID(), fr.heraliz.grades.Grade.GUIDE) && PontBukkit.authentifie(p));
    }

    /** {@code voyant} peut voir {@code cible} (cible pas cachée, ou voyant du staff, ou lui-même). */
    static boolean visible(ServerPlayer voyant, ServerPlayer cible) {
        return cible == null || voyant == cible || !cache(cible.getUUID()) || voitLesCaches(voyant);
    }

    static void demarrer(Path dossier) {
        fichier = dossier.resolve("vanish.json");
        CACHES.clear();
        SANS_NOM.clear();
        MASQUES.clear();
        REPONSES.clear();
        if (!Files.exists(fichier)) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
            lire(o, "caches", CACHES);
            lire(o, "sans_nom", SANS_NOM);
            LOG.info("[grades] {} joueur(s) caché(s), {} nom(s) caché(s) depuis {}", CACHES.size(), SANS_NOM.size(), fichier);
        } catch (Exception ex) {
            LOG.error("[grades] {} illisible ({}) : personne n'est caché (le fichier n'est pas touché tant qu'on ne change rien)", fichier, ex.toString());
        }
    }

    private static void lire(JsonObject o, String cle, Set<UUID> dans) {
        if (!o.has(cle)) return;
        for (JsonElement x : o.getAsJsonArray(cle)) {
            try {
                dans.add(UUID.fromString(x.getAsString()));
            } catch (IllegalArgumentException ex) {
                LOG.warn("[grades] {} : identifiant « {} » ignoré", fichier, x.getAsString());
            }
        }
    }

    private static void sauver() {
        if (fichier == null) return;
        JsonObject o = new JsonObject();
        JsonArray c = new JsonArray(), n = new JsonArray();
        CACHES.forEach(u -> c.add(u.toString()));
        SANS_NOM.forEach(u -> n.add(u.toString()));
        o.add("caches", c);
        o.add("sans_nom", n);
        try {
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling("vanish.json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomiqueImpossible) {
                Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            LOG.error("[grades] {} impossible à écrire : {}", fichier, ex.toString());
        }
    }

    static void arreter() {
        CACHES.clear();
        SANS_NOM.clear();
        MASQUES.clear();
        REPONSES.clear();
        fichier = null;
    }

    // ------------------------------------------------------------------ commandes
    private static Component msg(String cle, String fr, Object... args) {
        return Component.translatableWithFallback("message.heraliz_grades.moderation." + cle, fr, args);
    }

    /** Cache (ou montre) ce joueur à tous ceux qui ne sont pas du staff ; appelé par le panel du staff (« Se cacher »). */
    static void basculerCache(ServerPlayer p, boolean cache) {
        if (cache) CACHES.add(p.getUUID());
        else CACHES.remove(p.getUUID());
        // 21/11 : il se montre ou se recache : les joueurs à qui il avait écrit caché ne gardent pas leur droit de réponse
        oublierReponses(p.getUUID());
        sauver();
        appliquer(p.server);
        TabServeur.marquer();
        ServeurGrades.registreJournal(p.getGameProfile().getName() + " | vanish | " + (cache ? "caché" : "visible"));
        p.sendSystemMessage(cache
                ? msg("vanish_on", "Tu es caché : seul le staff connecté te voit (tab, monde, nom).").copy().withStyle(ChatFormatting.GRAY)
                : msg("vanish_off", "Tu es de nouveau visible de tous.").copy().withStyle(ChatFormatting.GRAY));
    }

    /** Cache (ou remet) le nom au-dessus de la tête ; appelé par le panel du staff (« Cacher son nom »). */
    static void basculerNom(ServerPlayer p, boolean cache) {
        if (cache) SANS_NOM.add(p.getUUID());
        else SANS_NOM.remove(p.getUUID());
        sauver();
        equipes(p.server);
        ServeurGrades.registreJournal(p.getGameProfile().getName() + " | nametag | " + (cache ? "caché" : "affiché"));
        p.sendSystemMessage(cache
                ? msg("nametag_on", "Ton nom au-dessus de la tête est caché pour tout le monde.").copy().withStyle(ChatFormatting.GRAY)
                : msg("nametag_off", "Ton nom au-dessus de la tête est de nouveau affiché.").copy().withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------ application
    /** À la connexion d'un joueur : on lui cache les joueurs cachés (et on le cache, s'il l'est) tout de suite. */
    static void connexion(ServerPlayer p) {
        PontBukkit.brancherConnexions(VanishServeur::cache);
        MASQUES.remove(p.getUUID());
        appliquer(p.server);
    }

    static void deconnexion(UUID joueur) {
        MASQUES.remove(joueur);
        for (Set<UUID> s : MASQUES.values()) s.remove(joueur);
        // 21/11 : fenêtres de réponse oubliées au départ de l'un des deux (joueur qui pouvait répondre, ou staff caché)
        REPONSES.remove(joueur);
        oublierReponses(joueur);
    }

    /**
     * Remet chaque voyant dans le bon état (appelé toutes les 2 s, au changement et à la connexion).
     * 1.3.4 (perf) : les joueurs cachés connectés sont cherchés une fois (avant : tous les joueurs pour chaque joueur,
     * 22 500 essais à 150 joueurs) ; sans joueur caché connecté, un voyant à qui rien n'est caché est passé sans rien
     * calculer (ni AuthMe, ni grade). Même résultat.
     */
    static void appliquer(MinecraftServer srv) {
        List<ServerPlayer> joueurs = srv.getPlayerList().getPlayers();
        retirerAnciensStaff(joueurs);
        List<ServerPlayer> caches = new ArrayList<>();
        if (!CACHES.isEmpty()) {
            for (ServerPlayer c : joueurs) if (CACHES.contains(c.getUUID())) caches.add(c);
        }
        for (ServerPlayer voyant : joueurs) {
            Set<UUID> actuel = MASQUES.get(voyant.getUUID());
            if (caches.isEmpty() && (actuel == null || actuel.isEmpty())) continue; // rien à cacher ni à remontrer
            Set<UUID> voulu = new HashSet<>();
            if (!voitLesCaches(voyant)) {
                for (ServerPlayer c : caches) if (c != voyant) voulu.add(c.getUUID());
            }
            if (actuel == null) {
                actuel = new HashSet<>();
                MASQUES.put(voyant.getUUID(), actuel);
            }
            for (UUID u : new ArrayList<>(actuel)) {
                if (voulu.contains(u)) continue;
                ServerPlayer c = srv.getPlayerList().getPlayer(u);
                if (c != null) montrer(voyant, c);
                actuel.remove(u);
            }
            for (UUID u : voulu) {
                if (actuel.contains(u)) continue;
                ServerPlayer c = srv.getPlayerList().getPlayer(u);
                if (c != null) masquer(voyant, c);
                actuel.add(u);
            }
        }
        equipes(srv);
    }

    /**
     * Un joueur caché (ou au nom caché) qui n'est plus du staff (grade du staff retiré, op retiré) redevient visible :
     * sinon il resterait invisible à vie, sans pouvoir se remontrer (le panneau du staff ne lui est plus ouvert).
     * Contrôle sur le grade et l'op seulement, pas sur AuthMe (un staff qui se reconnecte reste caché avant son /login).
     */
    private static void retirerAnciensStaff(List<ServerPlayer> joueurs) {
        RegistreGrades r = ServeurGrades.registre;
        if (r == null || (CACHES.isEmpty() && SANS_NOM.isEmpty())) return;
        boolean change = false;
        for (ServerPlayer p : joueurs) {
            UUID u = p.getUUID();
            if (!CACHES.contains(u) && !SANS_NOM.contains(u)) continue;
            if (p.hasPermissions(4) || r.staffAuMoins(u, fr.heraliz.grades.Grade.GUIDE)) continue;
            boolean etaitCache = CACHES.remove(u);
            if (etaitCache) oublierReponses(u);
            SANS_NOM.remove(u);
            change = true;
            ServeurGrades.registreJournal(p.getGameProfile().getName() + " | vanish | visible (plus du staff)");
            if (etaitCache) p.sendSystemMessage(msg("vanish_off", "Tu es de nouveau visible de tous.").copy().withStyle(ChatFormatting.GRAY));
        }
        if (change) {
            sauver();
            TabServeur.marquer();
        }
    }

    private static void masquer(ServerPlayer voyant, ServerPlayer cible) {
        if (PontBukkit.cacher(voyant, cible, true)) return;
        voyant.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(cible.getUUID())));
        voyant.connection.send(new ClientboundRemoveEntitiesPacket(cible.getId()));
    }

    private static void montrer(ServerPlayer voyant, ServerPlayer cible) {
        if (PontBukkit.cacher(voyant, cible, false)) return;
        // sans Bukkit : la ligne du tab, puis l'entité si elle est assez près pour être suivie
        voyant.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(cible)));
        if (voyant.level() != cible.level() || voyant.distanceToSqr(cible) > 64 * 64) return;
        voyant.connection.send(new ClientboundAddPlayerPacket(cible));
        List<SynchedEntityData.DataValue<?>> donnees = cible.getEntityData().getNonDefaultValues();
        if (donnees != null) voyant.connection.send(new ClientboundSetEntityDataPacket(cible.getId(), donnees));
        List<com.mojang.datafixers.util.Pair<EquipmentSlot, ItemStack>> equipement = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack s = cible.getItemBySlot(slot);
            if (!s.isEmpty()) equipement.add(com.mojang.datafixers.util.Pair.of(slot, s.copy()));
        }
        if (!equipement.isEmpty()) voyant.connection.send(new ClientboundSetEquipmentPacket(cible.getId(), equipement));
    }

    /** Un joueur caché qui entre dans le champ d'un autre : retiré chez celui qui ne doit pas le voir (sans Bukkit surtout). */
    private static void suivi(PlayerEvent.StartTracking e) {
        if (!(e.getTarget() instanceof ServerPlayer cible) || !(e.getEntity() instanceof ServerPlayer voyant)) return;
        if (!CACHES.contains(cible.getUUID()) || voitLesCaches(voyant)) return;
        voyant.connection.send(new ClientboundRemoveEntitiesPacket(cible.getId()));
    }

    /** Les créatures ne prennent pas un joueur caché pour cible. */
    private static void cible(LivingChangeTargetEvent e) {
        if (e.getNewTarget() instanceof ServerPlayer p && CACHES.contains(p.getUUID())) e.setCanceled(true);
    }

    /** Messages privés de Minecraft (et leurs autres noms) ; /teammsg et /tm sont retirées par le noyau. */
    private static final Set<String> MESSAGES_PRIVES = Set.of("msg", "tell", "w");

    /**
     * 21/11 (décision du propriétaire : « le cacher partout ») : {@code /msg}, {@code /tell} et {@code /w} de Minecraft vers
     * un membre du staff caché, tapées par un joueur qui ne voit pas le staff caché, reçoivent la réponse de Minecraft pour
     * un joueur absent (« Aucun joueur n'a été trouvé », en rouge) et rien n'est envoyé. Avant, le message partait et
     * « Tu chuchotes à … » disait qu'il était connecté. Le staff connecté garde l'accès ; {@code /r} du noyau passe par
     * {@code /msg} : même règle. Les noms proposés par la touche Tab viennent de la liste Tab du joueur, d'où le caché est
     * déjà retiré. Après les règles du noyau (combat, carte : elles répondent d'abord) ; rien à faire sans caché.
     * <p>Suite (21/11, choix du propriétaire) : quand le membre du staff caché écrit lui-même à un joueur, CE joueur seul
     * peut lui répondre pendant 5 min ({@link #REPONSES}). Seule une commande complète est jugée : une commande incomplète
     * ({@code /msg Pseudo} sans message) reçoit la réponse de Minecraft, la même pour un absent et pour un caché.
     */
    private static void messagePrive(net.minecraftforge.event.CommandEvent e) {
        if (CACHES.isEmpty()) return;
        com.mojang.brigadier.ParseResults<CommandSourceStack> pr = e.getParseResults();
        String entree = pr.getReader().getString();
        String brut = entree.strip();
        if (brut.startsWith("/")) brut = brut.substring(1);
        int espace = brut.indexOf(' ');
        String racine = (espace < 0 ? brut : brut.substring(0, espace)).toLowerCase(java.util.Locale.ROOT);
        int deuxPoints = racine.indexOf(':');
        if (deuxPoints >= 0) racine = racine.substring(deuxPoints + 1);
        if (!MESSAGES_PRIVES.contains(racine)) return;
        if (!(pr.getContext().getSource().getEntity() instanceof ServerPlayer de)) return;
        boolean voit = voitLesCaches(de);
        // staff qui n'est pas caché lui-même : rien à refuser ni à noter
        if (voit && !CACHES.contains(de.getUUID())) return;
        java.util.Collection<ServerPlayer> cibles = destinataires(pr, entree);
        if (cibles == null) return;   // commande incomplète ou personne de trouvé : Minecraft répond lui-même
        if (voit) {
            // un membre du staff caché écrit lui-même : chaque joueur hors du staff visé peut lui répondre 5 min
            long fin = System.currentTimeMillis() + REPONSE_MS;
            for (ServerPlayer x : cibles) {
                if (x != de && !voitLesCaches(x)) REPONSES.computeIfAbsent(x.getUUID(), k -> new HashMap<>()).put(de.getUUID(), fin);
            }
            return;
        }
        for (ServerPlayer x : cibles) {
            if (x != de && CACHES.contains(x.getUUID()) && !peutRepondre(de.getUUID(), x.getUUID())) {
                e.setCanceled(true);
                // la réponse de Minecraft pour un joueur absent, telle quelle (Commands.performCommand, sendFailure)
                de.sendSystemMessage(Component.empty().append(Component.translatable("argument.entity.notfound.player"))
                        .withStyle(ChatFormatting.RED));
                return;
            }
        }
    }

    /**
     * Destinataires d'une commande de message privé COMPLÈTE (lue jusqu'au bout, avec son message), comme Minecraft les
     * trouverait ; null si la commande est incomplète ou si personne n'est trouvé (Minecraft répond alors lui-même). /tell
     * et /w renvoient vers /msg : les destinataires sont dans le contexte qui suit la redirection.
     */
    @javax.annotation.Nullable
    private static java.util.Collection<ServerPlayer> destinataires(com.mojang.brigadier.ParseResults<CommandSourceStack> pr, String entree) {
        if (!pr.getExceptions().isEmpty() || pr.getReader().canRead()) return null;
        com.mojang.brigadier.context.CommandContextBuilder<CommandSourceStack> derniere = pr.getContext();
        while (derniere.getChild() != null) derniere = derniere.getChild();
        if (derniere.getCommand() == null) return null;
        for (com.mojang.brigadier.context.CommandContextBuilder<CommandSourceStack> c = pr.getContext(); c != null; c = c.getChild()) {
            if (!c.getArguments().containsKey("targets")) continue;
            try {
                return net.minecraft.commands.arguments.EntityArgument.getPlayers(c.build(entree), "targets");
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    /** Le membre du staff caché {@code staff} a écrit à {@code joueur} il y a moins de 5 min (fenêtre de réponse ouverte). */
    private static boolean peutRepondre(UUID joueur, UUID staff) {
        Map<UUID, Long> m = REPONSES.get(joueur);
        Long fin = m == null ? null : m.get(staff);
        if (fin == null) return false;
        if (fin > System.currentTimeMillis()) return true;
        m.remove(staff);
        if (m.isEmpty()) REPONSES.remove(joueur);
        return false;
    }

    /** Fenêtres de réponse ouvertes par ce membre du staff (il se montre, se recache, n'est plus du staff ou part). */
    private static void oublierReponses(UUID staff) {
        for (java.util.Iterator<Map<UUID, Long>> it = REPONSES.values().iterator(); it.hasNext(); ) {
            Map<UUID, Long> m = it.next();
            m.remove(staff);
            if (m.isEmpty()) it.remove();
        }
    }

    /**
     * Équipes du tableau des scores : caché = {@code hz_cache} (nom jamais affiché, ne pousse personne), nom caché seul =
     * {@code hz_sansnom} ; sinon le joueur quitte nos équipes (les équipes des autres ne sont pas touchées, sauf si le
     * joueur doit entrer dans une des nôtres : un joueur n'a qu'une équipe).
     */
    private static void equipes(MinecraftServer srv) {
        ServerScoreboard sb = srv.getScoreboard();
        PlayerTeam cache = equipe(sb, EQUIPE_CACHE, true), sansNom = equipe(sb, EQUIPE_SANS_NOM, false);
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            String nom = p.getScoreboardName();
            PlayerTeam voulue = CACHES.contains(p.getUUID()) ? cache : SANS_NOM.contains(p.getUUID()) ? sansNom : null;
            PlayerTeam actuelle = sb.getPlayersTeam(nom);
            if (actuelle == voulue) continue;
            if (voulue != null) sb.addPlayerToTeam(nom, voulue);
            else if (actuelle == cache || actuelle == sansNom) sb.removePlayerFromTeam(nom, actuelle);
        }
    }

    private static PlayerTeam equipe(ServerScoreboard sb, String nom, boolean sansPoussee) {
        PlayerTeam t = sb.getPlayerTeam(nom);
        if (t == null) t = sb.addPlayerTeam(nom);
        if (t.getNameTagVisibility() != Team.Visibility.NEVER) t.setNameTagVisibility(Team.Visibility.NEVER);
        Team.CollisionRule regle = sansPoussee ? Team.CollisionRule.NEVER : Team.CollisionRule.ALWAYS;
        if (t.getCollisionRule() != regle) t.setCollisionRule(regle);
        return t;
    }
}
