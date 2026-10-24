package fr.heraliz.grades.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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
 * Outils de modération du staff (2.7.0) : {@code /vanish} et {@code /nametag}, réservés aux op de niveau 4 connectés sur
 * AuthMe (même règle que l'éditeur des grades).
 * <ul>
 *    <li>/vanish : le joueur disparaît pour tous ceux qui ne sont pas du staff (liste Tab, entité, nom au-dessus de la
 *    tête), sans message d'arrivée ni de départ ; il ne pousse plus personne et les créatures ne le prennent plus pour
 *    cible. Le staff (op 4 connecté) le voit toujours, marqué « (caché) » dans le tab. Par Bukkit ({@code hidePlayer}) sur
 *    Mohist, sinon par les paquets de Minecraft (liste et entité retirées chez chaque joueur, à chaque nouveau suivi).</li>
 *    <li>/nametag : son nom au-dessus de la tête caché (ou remontré) pour tout le monde (équipe du tableau des scores
 *    {@code hz_sansnom}, nom jamais affiché).</li>
 * </ul>
 * Les deux états sont gardés dans {@code heraliz_grades/vanish.json} à la racine du serveur (hors du monde) : ils restent
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
    private static Path fichier;

    private VanishServeur() {
    }

    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent e) -> enregistrer(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener(VanishServeur::suivi);
        MinecraftForge.EVENT_BUS.addListener(VanishServeur::cible);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStartedEvent e) -> PontBukkit.brancherConnexions(VanishServeur::cache));
    }

    // ------------------------------------------------------------------ état
    /** Vrai si ce joueur est caché par /vanish. Lu aussi par d'autres mods ({@link GradesApi#cache}). */
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

    /** Ce joueur voit les joueurs cachés (staff : op 4 connecté sur AuthMe). */
    static boolean voitLesCaches(ServerPlayer p) {
        return droit(p);
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
        fichier = null;
    }

    // ------------------------------------------------------------------ commandes
    private static Component msg(String cle, String fr, Object... args) {
        return Component.translatableWithFallback("message.heraliz_grades.moderation." + cle, fr, args);
    }

    private static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("vanish")
                .requires(s -> s.getEntity() instanceof ServerPlayer p && droit(p))
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!droit(p)) return 0;
                    boolean cache = !CACHES.contains(p.getUUID());
                    if (cache) CACHES.add(p.getUUID());
                    else CACHES.remove(p.getUUID());
                    sauver();
                    appliquer(p.server);
                    TabServeur.maj(p.server, true);
                    ServeurGrades.registreJournal(p.getGameProfile().getName() + " | vanish | " + (cache ? "caché" : "visible"));
                    p.sendSystemMessage(cache
                            ? msg("vanish_on", "Tu es caché : seuls les op connectés te voient (tab, monde, nom).").copy().withStyle(ChatFormatting.GRAY)
                            : msg("vanish_off", "Tu es de nouveau visible de tous.").copy().withStyle(ChatFormatting.GRAY));
                    return 1;
                }));
        d.register(Commands.literal("nametag")
                .requires(s -> s.getEntity() instanceof ServerPlayer p && droit(p))
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!droit(p)) return 0;
                    boolean cache = !SANS_NOM.contains(p.getUUID());
                    if (cache) SANS_NOM.add(p.getUUID());
                    else SANS_NOM.remove(p.getUUID());
                    sauver();
                    equipes(p.server);
                    ServeurGrades.registreJournal(p.getGameProfile().getName() + " | nametag | " + (cache ? "caché" : "affiché"));
                    p.sendSystemMessage(cache
                            ? msg("nametag_on", "Ton nom au-dessus de la tête est caché pour tout le monde.").copy().withStyle(ChatFormatting.GRAY)
                            : msg("nametag_off", "Ton nom au-dessus de la tête est de nouveau affiché.").copy().withStyle(ChatFormatting.GRAY));
                    return 1;
                }));
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
    }

    /** Remet chaque voyant dans le bon état (appelé toutes les 2 s, au changement et à la connexion). */
    static void appliquer(MinecraftServer srv) {
        List<ServerPlayer> joueurs = srv.getPlayerList().getPlayers();
        for (ServerPlayer voyant : joueurs) {
            Set<UUID> voulu = new HashSet<>();
            if (!voitLesCaches(voyant)) {
                for (ServerPlayer c : joueurs) if (c != voyant && CACHES.contains(c.getUUID())) voulu.add(c.getUUID());
            }
            Set<UUID> actuel = MASQUES.computeIfAbsent(voyant.getUUID(), k -> new HashSet<>());
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
