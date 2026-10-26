package fr.heraliz.crac.heraliz;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.data.SafeSavedData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.loading.FMLPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * La Purge (saison des clans, docs/SAISON_CLANS.md §3) : les bases des clans ne se raident que pendant ses créneaux.
 * Hors Purge, les explosifs ne font rien aux constructions, aux objets posés ni aux blocs d'une base (contrôlé dans
 * {@code StructureService.damage}, {@code PlacableHpManager}, {@code RaidBlocks.breakable}) et le cœur de clan est
 * intouchable. Une base dont l'armoire a été vue moins de 48 h avant le début d'une Purge n'est pas raidable pendant
 * cette Purge.
 * <p>
 * Horaires : {@code config/crac/purge.json} (heure de Paris ; fichier du dépôt qui fait foi, écrit s'il manque).
 * Annonces 1 h et 10 min avant, au début et à la fin ; petite icône « PURGE » et temps restant en haut à gauche pendant
 * qu'elle dure (25/10, {@code S2CPurge} et {@code client/PurgeHud} ; avant : barre rouge de Minecraft). Staff
 * (permission 4) : {@code /purge forcer <minutes>}, {@code /purge arreter} (jusqu'au redémarrage). Pour tous :
 * {@code /purge}. Sans HeraliZ Clans, ou avec {@code "actif": false} : les raids marchent comme avant (à tout moment).
 */
@EventBusSubscriber(modid = "crac")
public final class Purge {
    private Purge() {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("EEEE HH:mm", Locale.FRANCE);

    /** Un créneau : jour de la semaine, début et fin (fin avant le début = passe minuit). */
    public record Creneau(DayOfWeek jour, LocalTime debut, LocalTime fin) {
    }

    /** Une Purge datée : identifiant (prises de cœur : une par clan et par Purge), début et fin en ms. */
    public record Fenetre(String id, long debut, long fin) {
    }

    // ---------------------------------------------------------------------------------------------- réglages
    static boolean actif = true;
    static ZoneId zone = ZoneId.of("Europe/Paris");
    static final List<Creneau> CRENEAUX = new ArrayList<>();
    /** Base dont l'armoire est plus récente que ça au début d'une Purge : pas raidable pendant celle-ci. */
    static long protectionMs = 48 * 3_600_000L;
    static int priseSecondes = 90;
    static double priseDistance = 3.0;
    static long deplacementPrix = 50_000L;
    static int deplacementMinutes = 10;
    static int deplacementAvantMinutes = 60;

    // ---------------------------------------------------------------------------------------------- état
    @Nullable
    private static Fenetre forcee;
    /** Purge du calendrier arrêtée par le staff (son identifiant). */
    @Nullable
    private static String arretee;
    @Nullable
    private static Fenetre enCours;
    private static final Set<String> ANNONCES = new HashSet<>();
    /** Purge annoncée aux jeux des joueurs (icône en haut à gauche) : son identifiant, ou null. */
    @Nullable
    private static String iconeEnvoyee;

    // ---------------------------------------------------------------------------------------------- lecture
    /** Le système de Purge commande les raids (HeraliZ Clans là, réglage actif). */
    public static boolean systeme() {
        return actif && BasesClans.actif();
    }

    /** La Purge en cours, ou null. */
    @Nullable
    public static Fenetre enCours() {
        return fenetre(System.currentTimeMillis());
    }

    @Nullable
    static Fenetre fenetre(long now) {
        if (forcee != null) {
            if (now < forcee.fin()) {
                return forcee;
            }
            forcee = null;
        }
        for (Fenetre f : calendrier(now - 2 * 86_400_000L, now + 1)) {
            if (f.debut() <= now && now < f.fin() && !f.id().equals(arretee)) {
                return f;
            }
        }
        return null;
    }

    /** La prochaine Purge du calendrier (pas commencée), ou null s'il n'y a aucun créneau. */
    @Nullable
    public static Fenetre prochaine() {
        long now = System.currentTimeMillis();
        Fenetre best = null;
        for (Fenetre f : calendrier(now, now + 8 * 86_400_000L)) {
            if (f.debut() > now && (best == null || f.debut() < best.debut())) {
                best = f;
            }
        }
        return best;
    }

    /** Toutes les Purges du calendrier qui commencent entre {@code de} et {@code a}. */
    static List<Fenetre> calendrier(long de, long a) {
        List<Fenetre> l = new ArrayList<>();
        LocalDate d = java.time.Instant.ofEpochMilli(de).atZone(zone).toLocalDate().minusDays(1);
        LocalDate fin = java.time.Instant.ofEpochMilli(a).atZone(zone).toLocalDate().plusDays(1);
        for (; !d.isAfter(fin); d = d.plusDays(1)) {
            for (Creneau c : CRENEAUX) {
                if (d.getDayOfWeek() != c.jour()) {
                    continue;
                }
                ZonedDateTime s = d.atTime(c.debut()).atZone(zone);
                ZonedDateTime e = (c.fin().isAfter(c.debut()) ? d : d.plusDays(1)).atTime(c.fin()).atZone(zone);
                long sm = s.toInstant().toEpochMilli();
                if (sm >= de - 2 * 86_400_000L && sm < a) {
                    l.add(new Fenetre("purge-" + s.toLocalDateTime(), sm, e.toInstant().toEpochMilli()));
                }
            }
        }
        return l;
    }

    /**
     * Un explosif ne fait rien à la base qui tient ce bloc : système actif, et pas de Purge, ou base trop récente.
     * Hors des bases (pas de claim) : rien ne change (règles de CRac).
     */
    public static boolean raidInterdit(ServerLevel level, BlockPos pos) {
        if (!systeme()) {
            return false;
        }
        Claim c = ClaimManager.getAt(level, pos);
        return c != null && raidInterdit(level.getServer(), c.groupId());
    }

    public static boolean raidInterdit(MinecraftServer server, @Nullable UUID groupe) {
        if (!systeme() || groupe == null) {
            return false;
        }
        Fenetre f = enCours();
        return f == null || baseRecente(server, groupe, f);
    }

    /**
     * Un explosif va partir en {@code pos} sur une base qui ne se raide pas maintenant : son lanceur (joueur) le sait
     * (25/10 : avant, C4 et charges partaient sans effet ni message, le joueur croyait à un bug).
     */
    public static void prevenirExplosifInutile(ServerLevel level, BlockPos pos, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity lanceur) {
        if (!(lanceur instanceof ServerPlayer p) || !systeme()) {
            return;
        }
        Claim c = ClaimManager.getAt(level, pos);
        if (c == null || !raidInterdit(level.getServer(), c.groupId())) {
            return;
        }
        Fenetre f = enCours();
        MutableComponent msg = f == null
            ? t("crac.purge.explosif_hors", "Hors de la Purge, les explosifs ne font rien aux bases des clans (/purge pour les horaires).")
            : t("crac.purge.explosif_recente", "Cette base est trop récente : elle n'est pas raidable pendant cette Purge.");
        p.displayClientMessage(msg.withStyle(ChatFormatting.RED), false);
    }

    /** L'armoire du groupe a été vue moins de {@link #protectionMs} avant le début de la Purge. */
    public static boolean baseRecente(MinecraftServer server, UUID groupe, Fenetre f) {
        long vue = Donnees.get(server).vues.getOrDefault(groupe, 0L);
        return vue == 0L || vue > f.debut() - protectionMs;
    }

    /** Toutes les 5 s (bases des clans) : date de première vue de l'armoire de chaque groupe qui en a une. */
    static void noterArmoires(MinecraftServer server) {
        Donnees d = Donnees.get(server);
        long now = System.currentTimeMillis();
        for (ServerLevel level : server.getAllLevels()) {
            for (Claim c : ClaimData.get(level).all()) {
                if (!d.vues.containsKey(c.groupId())) {
                    d.vues.put(c.groupId(), now);
                    d.setDirty();
                }
            }
        }
        // gardée tant que le groupe (le clan) existe : casser et reposer son armoire ne rend jamais la base « nouvelle »
        fr.heraliz.crac.group.GroupData groupes = fr.heraliz.crac.group.GroupData.get(server);
        if (d.vues.keySet().removeIf(g -> groupes.getById(g) == null)) {
            d.setDirty();
        }
    }

    /** Le cœur ne se déplace pas pendant la Purge ni dans l'heure qui la précède. */
    public static boolean deplacementInterdit() {
        if (!systeme()) {
            return false;
        }
        if (enCours() != null) {
            return true;
        }
        Fenetre p = prochaine();
        return p != null && p.debut() - System.currentTimeMillis() < deplacementAvantMinutes * 60_000L;
    }

    // ---------------------------------------------------------------------------------------------- fichier
    @SubscribeEvent
    public static void demarrage(ServerAboutToStartEvent e) {
        charger();
    }

    @SubscribeEvent
    public static void arret(ServerStoppingEvent e) {
        iconeEnvoyee = null;
        forcee = null;
        arretee = null;
        enCours = null;
    }

    static Path fichier() {
        return FMLPaths.CONFIGDIR.get().resolve("crac").resolve("purge.json");
    }

    static void charger() {
        Path f = fichier();
        defaut();
        try {
            if (!Files.exists(f)) {
                Files.createDirectories(f.getParent());
                Files.writeString(f, GSON.toJson(json()), StandardCharsets.UTF_8);
                Crac.LOGGER.info("[CRac] purge.json écrit avec les horaires par défaut");
                return;
            }
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            actif = !o.has("actif") || o.get("actif").getAsBoolean();
            if (o.has("fuseau")) {
                zone = ZoneId.of(o.get("fuseau").getAsString());
            }
            if (o.has("creneaux")) {
                CRENEAUX.clear();
                for (JsonElement el : o.getAsJsonArray("creneaux")) {
                    try {
                        JsonObject c = el.getAsJsonObject();
                        CRENEAUX.add(new Creneau(jour(c.get("jour").getAsString()), LocalTime.parse(c.get("debut").getAsString()),
                            LocalTime.parse(c.get("fin").getAsString())));
                    } catch (RuntimeException ex) {
                        Crac.LOGGER.warn("[CRac] purge.json : créneau illisible ignoré : {}", el);
                    }
                }
            }
            protectionMs = (long) (nombre(o, "protection_nouvelle_base_heures", 48) * 3_600_000L);
            priseSecondes = (int) Math.max(5, nombre(o, "prise_secondes", 90));
            priseDistance = Math.max(1.5, nombre(o, "prise_distance", 3));
            deplacementPrix = (long) Math.max(0, nombre(o, "deplacement_prix", 50_000));
            deplacementMinutes = (int) Math.max(1, nombre(o, "deplacement_minutes", 10));
            deplacementAvantMinutes = (int) Math.max(0, nombre(o, "deplacement_interdit_avant_minutes", 60));
            Crac.LOGGER.info("[CRac] Purge : {} créneau(x), {}", CRENEAUX.size(), actif ? "active" : "coupée (raids à tout moment)");
        } catch (IOException | RuntimeException ex) {
            Crac.LOGGER.error("[CRac] purge.json illisible : horaires par défaut", ex);
            defaut();
        }
    }

    private static double nombre(JsonObject o, String cle, double def) {
        return o.has(cle) && o.get(cle).isJsonPrimitive() ? o.get(cle).getAsDouble() : def;
    }

    private static void defaut() {
        actif = true;
        zone = ZoneId.of("Europe/Paris");
        CRENEAUX.clear();
        CRENEAUX.add(new Creneau(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(21, 0)));
        CRENEAUX.add(new Creneau(DayOfWeek.SATURDAY, LocalTime.of(15, 0), LocalTime.of(22, 0)));
        CRENEAUX.add(new Creneau(DayOfWeek.SUNDAY, LocalTime.of(15, 0), LocalTime.of(22, 0)));
        protectionMs = 48 * 3_600_000L;
        priseSecondes = 90;
        priseDistance = 3.0;
        deplacementPrix = 50_000L;
        deplacementMinutes = 10;
        deplacementAvantMinutes = 60;
    }

    private static final String[] JOURS = {"lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"};

    static DayOfWeek jour(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < JOURS.length; i++) {
            if (JOURS[i].equals(t)) {
                return DayOfWeek.of(i + 1);
            }
        }
        return DayOfWeek.valueOf(t.toUpperCase(Locale.ROOT));
    }

    static JsonObject json() {
        JsonObject o = new JsonObject();
        o.addProperty("_aide", "La Purge (saison des clans) : les bases ne se raident que pendant ces créneaux (heure de Paris)."
            + " Fin avant le début = passe minuit. actif false = raids à tout moment, comme avant.");
        o.addProperty("actif", actif);
        o.addProperty("fuseau", zone.getId());
        JsonArray a = new JsonArray();
        for (Creneau c : CRENEAUX) {
            JsonObject x = new JsonObject();
            x.addProperty("jour", JOURS[c.jour().getValue() - 1]);
            x.addProperty("debut", c.debut().toString());
            x.addProperty("fin", c.fin().toString());
            a.add(x);
        }
        o.add("creneaux", a);
        o.addProperty("protection_nouvelle_base_heures", protectionMs / 3_600_000L);
        o.addProperty("prise_secondes", priseSecondes);
        o.addProperty("prise_distance", priseDistance);
        o.addProperty("deplacement_prix", deplacementPrix);
        o.addProperty("deplacement_minutes", deplacementMinutes);
        o.addProperty("deplacement_interdit_avant_minutes", deplacementAvantMinutes);
        return o;
    }

    // ---------------------------------------------------------------------------------------------- horloge
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer() == null) {
            return;
        }
        MinecraftServer server = e.getServer();
        PriseCoeur.tick(server);
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        try {
            seconde(server);
            CoeurDeplacement.seconde(server);
        } catch (RuntimeException ex) {
            Crac.LOGGER.error("[CRac] Purge : horloge en erreur", ex);
        }
    }

    private static void seconde(MinecraftServer server) {
        if (!systeme()) {
            icone(null);
            return;
        }
        long now = System.currentTimeMillis();
        Fenetre f = fenetre(now);
        if (f != null && (enCours == null || !enCours.id().equals(f.id()))) {
            annoncer(server, t("crac.purge.debut", "☠ LA PURGE COMMENCE ! Les bases des clans sont raidables jusqu'à %s.",
                heure(f.fin())).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            Crac.LOGGER.info("[CRac] Purge {} commencée", f.id());
        } else if (f == null && enCours != null) {
            annoncer(server, t("crac.purge.fin", "La Purge est finie : les bases ne se raident plus. Une prise de cœur commencée va jusqu'au bout.")
                .withStyle(ChatFormatting.GOLD));
            Crac.LOGGER.info("[CRac] Purge {} finie", enCours.id());
        }
        enCours = f;
        icone(f);
        if (f == null) {
            Fenetre p = prochaine();
            if (p != null) {
                long reste = p.debut() - now;
                if (reste <= 3_600_000L && reste > 3_540_000L && ANNONCES.add(p.id() + "|60")) {
                    annoncer(server, t("crac.purge.bientot", "⚠ La Purge commence dans %s : les bases seront raidables.", "1 h")
                        .withStyle(ChatFormatting.RED));
                } else if (reste <= 600_000L && reste > 540_000L && ANNONCES.add(p.id() + "|10")) {
                    annoncer(server, t("crac.purge.bientot", "⚠ La Purge commence dans %s : les bases seront raidables.", "10 min")
                        .withStyle(ChatFormatting.RED));
                }
            }
            return;
        }
    }

    /**
     * Petite icône de la Purge en haut à gauche chez les joueurs (25/10, choix du propriétaire : à la place de la barre
     * rouge de Minecraft) : envoyée à tous seulement quand la Purge commence ou finit ; à la connexion, voir
     * {@link #connexion}.
     */
    private static void icone(@Nullable Fenetre f) {
        String id = f == null ? null : f.id();
        if (java.util.Objects.equals(id, iconeEnvoyee)) {
            return;
        }
        iconeEnvoyee = id;
        fr.heraliz.crac.network.NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), paquet(f));
    }

    private static fr.heraliz.crac.network.S2CPurge paquet(@Nullable Fenetre f) {
        long now = System.currentTimeMillis();
        return f == null ? new fr.heraliz.crac.network.S2CPurge(0, 0)
            : new fr.heraliz.crac.network.S2CPurge(Math.max(0, f.fin() - now), Math.max(1, f.fin() - f.debut()));
    }

    @SubscribeEvent
    public static void connexion(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && systeme()) {
            Fenetre f = enCours();
            if (f != null) {
                fr.heraliz.crac.network.NetworkHandler.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), paquet(f));
            }
        }
    }

    private static void annoncer(MinecraftServer server, Component c) {
        server.getPlayerList().broadcastSystemMessage(c, false);
    }

    static MutableComponent t(String cle, String fr, Object... args) {
        return Component.translatableWithFallback(cle, fr, args);
    }

    static String heure(long ms) {
        return java.time.Instant.ofEpochMilli(ms).atZone(zone).format(HEURE);
    }

    /** « 1 h 05 », « 12 min », « 45 s ». */
    static String duree(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 3600) {
            return String.format(Locale.ROOT, "%d h %02d", s / 3600, s % 3600 / 60);
        }
        if (s >= 60) {
            return s / 60 + " min";
        }
        return s + " s";
    }

    // ---------------------------------------------------------------------------------------------- commandes
    @SubscribeEvent
    public static void commandes(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("purge")
            .executes(c -> info(c.getSource()))
            .then(Commands.literal("forcer").requires(s -> s.hasPermission(4))
                .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 24 * 60)).executes(c -> {
                    long now = System.currentTimeMillis();
                    forcee = new Fenetre("forcee-" + now, now, now + IntegerArgumentType.getInteger(c, "minutes") * 60_000L);
                    Reply.ok(c.getSource(), Component.literal("Purge forcée jusqu'à " + heure(forcee.fin()) + "."));
                    Crac.LOGGER.info("[CRac] Purge forcée par {} ({} min)", c.getSource().getTextName(), IntegerArgumentType.getInteger(c, "minutes"));
                    return 1;
                })))
            .then(Commands.literal("arreter").requires(s -> s.hasPermission(4)).executes(c -> {
                Fenetre f = enCours();
                if (f == null) {
                    Reply.fail(c.getSource(), Component.literal("Aucune Purge en cours."));
                    return 0;
                }
                if (f == forcee) {
                    forcee = null;
                } else {
                    arretee = f.id();
                }
                Reply.ok(c.getSource(), Component.literal("Purge arrêtée (jusqu'au prochain créneau)."));
                Crac.LOGGER.info("[CRac] Purge {} arrêtée par {}", f.id(), c.getSource().getTextName());
                return 1;
            }))
            .then(Commands.literal("recharger").requires(s -> s.hasPermission(4)).executes(c -> {
                charger();
                Reply.ok(c.getSource(), Component.literal("purge.json relu : " + CRENEAUX.size() + " créneau(x)."));
                return 1;
            })));
    }

    private static int info(CommandSourceStack src) {
        if (!systeme()) {
            Reply.ok(src, t("crac.purge.coupee", "Pas de Purge sur ce serveur : les raids suivent les règles habituelles."));
            return 1;
        }
        Fenetre f = enCours();
        if (f != null) {
            Reply.ok(src, t("crac.purge.info_en_cours", "☠ La Purge est en cours : fin %s (dans %s).", heure(f.fin()),
                duree(f.fin() - System.currentTimeMillis())).withStyle(ChatFormatting.RED));
        } else {
            Fenetre p = prochaine();
            Reply.ok(src, p == null ? t("crac.purge.aucune", "Aucune Purge prévue.")
                : t("crac.purge.info_prochaine", "Prochaine Purge : %s (dans %s), jusqu'à %s.", heure(p.debut()),
                duree(p.debut() - System.currentTimeMillis()), heure(p.fin())).withStyle(ChatFormatting.GOLD));
        }
        StringBuilder sb = new StringBuilder();
        for (Creneau c : CRENEAUX) {
            if (!sb.isEmpty()) {
                sb.append(" · ");
            }
            sb.append(JOURS[c.jour().getValue() - 1]).append(' ').append(c.debut()).append('-').append(c.fin());
        }
        Reply.ok(src, t("crac.purge.horaires", "Horaires : %s.", sb.toString()).withStyle(ChatFormatting.GRAY));
        return 1;
    }

    // ---------------------------------------------------------------------------------------------- données
    /** Première vue de l'armoire de chaque groupe (protection des nouvelles bases), gardée avec le monde principal. */
    public static final class Donnees extends SafeSavedData {
        public static final String ID = "crac_purge";
        final Map<UUID, Long> vues = new HashMap<>();

        public static Donnees get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Donnees::load,
                () -> SafeSavedData.fresh(server.overworld(), ID, new Donnees()), ID);
        }

        public static Donnees load(CompoundTag tag) {
            Donnees d = new Donnees();
            ListTag l = tag.getList("Vues", 10);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag t = l.getCompound(i);
                if (t.hasUUID("Groupe")) {
                    d.vues.put(t.getUUID("Groupe"), t.getLong("Le"));
                }
            }
            return d;
        }

        @NotNull
        @Override
        public CompoundTag save(@NotNull CompoundTag tag) {
            ListTag l = new ListTag();
            for (Map.Entry<UUID, Long> e : this.vues.entrySet()) {
                CompoundTag t = new CompoundTag();
                t.putUUID("Groupe", e.getKey());
                t.putLong("Le", e.getValue());
                l.add(t);
            }
            tag.put("Vues", l);
            return tag;
        }
    }
}
