package fr.heraliz.grades.serveur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import fr.heraliz.grades.Grade;
import fr.heraliz.grades.reseau.ReseauGrades;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Warps (Grades 1.3.0) : {@code /warp} ouvre le menu des warps (style des écrans de clan), {@code /warp <nom>} y va tout
 * de suite. Seulement depuis le monde du spawn ({@code heralisz:spawn}) : jamais depuis la carte, le monde des bases, un
 * match ou en combat ; 3 s sans bouger ni être touché, puis vraie téléportation. Un warp peut être réservé aux grades de
 * la boutique (« vip » = Rescapé et plus, ou un grade précis) : le serveur refuse les autres, et une zone VIP
 * ({@link ZonesVip}) empêche d'y entrer autrement. Le staff (op connecté sur AuthMe) va partout, sans délai.
 * <p>
 * Staff : {@code /warp set <nom> [vip|rescape|traqueur|roi_de_la_zone]} (à l'endroit et dans le sens où il se tient),
 * {@code del <nom>}, {@code liste}, {@code nom <nom> <texte>}, {@code description <nom> <texte>}, {@code icone <nom>} (objet
 * en main). Warps gardés hors du monde : {@code heraliz_grades/warps.json}. Le joueur ne fait que DEMANDER un warp par son
 * nom : lieu, grade et règles sont décidés ici. Fil serveur seulement.
 */
final class WarpsServeur {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final ResourceKey<Level> SPAWN = ResourceKey.create(Registries.DIMENSION, new ResourceLocation("heralisz", "spawn"));
    private static final int DELAI_TICKS = 60, MAX_WARPS = 32;
    private static final long ANTI_SPAM_MS = 1000;
    private static final Set<String> RESERVES = Set.of("set", "del", "liste", "nom", "description", "icone");
    private static final String CLE_COMBAT = "heralisz:combat_jusqua", DRAPEAU_MATCH = "heralisz:en_match";

    record Warp(String id, String nom, String description, ResourceKey<Level> monde, Vec3 pos, float yRot, float xRot, int rang, String icone) {
        Warp avec(String nom, String description, int rang, String icone) {
            return new Warp(id, nom, description, monde, pos, yRot, xRot, rang, icone);
        }
    }

    private record Attente(String warp, Vec3 depart, float vie, int fin) {
    }

    private static final Map<String, Warp> WARPS = new LinkedHashMap<>();
    private static final Map<UUID, Attente> ATTENTES = new HashMap<>();
    private static final Map<UUID, Long> DERNIERE_DEMANDE = new HashMap<>();
    private static Path fichier;
    private static int ticks;

    private WarpsServeur() {
    }

    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(WarpsServeur::demarrage);
        MinecraftForge.EVENT_BUS.addListener(WarpsServeur::commandes);
        MinecraftForge.EVENT_BUS.addListener(WarpsServeur::tick);
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            ATTENTES.remove(e.getEntity().getUUID());
            DERNIERE_DEMANDE.remove(e.getEntity().getUUID());
        });
        ReseauGrades.RECEPTION_WARP.brancher(d -> {
            long now = System.currentTimeMillis();
            Long avant = DERNIERE_DEMANDE.put(d.joueur().getUUID(), now);
            if (avant != null && now - avant < ANTI_SPAM_MS) return;
            aller(d.joueur(), d.id());
        });
    }

    // ------------------------------------------------------------------ règles
    private static boolean staff(ServerPlayer p) {
        return p.hasPermissions(2) && PontBukkit.authentifie(p);
    }

    static boolean auSpawn(ServerPlayer p) {
        return p.level().dimension().equals(SPAWN);
    }

    private static boolean permis(ServerPlayer p, Warp w) {
        return w.rang() <= 0 || staff(p) || p.isCreative() || p.isSpectator() || GradesApi.rangBoutique(p.getUUID()) >= w.rang();
    }

    private static Component nomGrade(int rang) {
        for (Grade g : Grade.values()) if (g.type == Grade.Type.BOUTIQUE && g.rang == rang) return g.nom();
        return Component.literal("VIP");
    }

    /** Demande d'aller à un warp (menu ou /warp <nom>) : vérifie tout, puis lance le délai (ou téléporte le staff). */
    private static int aller(ServerPlayer p, String id) {
        Warp w = WARPS.get(id.toLowerCase(Locale.ROOT));
        if (w == null) return refus(p, Component.translatableWithFallback("heraliz_grades.warp.inconnu", "Warp inconnu : %s", id));
        ServerLevel monde = p.server.getLevel(w.monde());
        if (monde == null) return refus(p, Component.translatableWithFallback("heraliz_grades.warp.monde_absent", "Ce warp mène à un monde absent."));
        if (staff(p)) {
            teleporter(p, monde, w);
            return 1;
        }
        if (!auSpawn(p)) return refus(p, Component.translatableWithFallback("heraliz_grades.warp.pas_spawn", "Les warps ne s'utilisent qu'au spawn."));
        if (p.getPersistentData().getBoolean(DRAPEAU_MATCH)) return refus(p, Component.translatableWithFallback("heraliz_grades.warp.match", "Impossible pendant un match."));
        if (p.getPersistentData().getLong(CLE_COMBAT) > System.currentTimeMillis()) {
            return refus(p, Component.translatableWithFallback("heraliz_grades.warp.combat", "Impossible en combat."));
        }
        Component non = Restrictions.empechement(p);
        if (non != null) return refus(p, non);
        if (!permis(p, w)) {
            return refus(p, Component.translatableWithFallback("heraliz_grades.warp.reserve", "Warp réservé au grade %s et plus.", nomGrade(w.rang())));
        }
        ATTENTES.put(p.getUUID(), new Attente(w.id(), p.position(), p.getHealth(), ticks + DELAI_TICKS));
        p.displayClientMessage(Component.translatableWithFallback("heraliz_grades.warp.attente", "Téléportation vers %s dans 3 s : ne bouge pas.", w.nom())
                .withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static int refus(ServerPlayer p, Component c) {
        p.displayClientMessage(c.copy().withStyle(ChatFormatting.RED), true);
        return 0;
    }

    private static void teleporter(ServerPlayer p, ServerLevel monde, Warp w) {
        if (p.isPassenger()) p.stopRiding();
        p.teleportTo(monde, w.pos().x, w.pos().y, w.pos().z, w.yRot(), w.xRot());
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        p.displayClientMessage(Component.translatableWithFallback("heraliz_grades.warp.arrive", "Bienvenue : %s", w.nom()).withStyle(ChatFormatting.GOLD), true);
    }

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        ticks++;
        if (ATTENTES.isEmpty()) return;
        MinecraftServer srv = e.getServer();
        ATTENTES.entrySet().removeIf(en -> {
            ServerPlayer p = srv.getPlayerList().getPlayer(en.getKey());
            Attente a = en.getValue();
            if (p == null || p.isDeadOrDying()) return true;
            Vec3 nd = Immobile.suivre(p, a.depart());
            if (nd != a.depart() && nd != null) {
                a = new Attente(a.warp(), nd, a.vie(), a.fin());
                en.setValue(a);
            }
            if (nd == null || p.getHealth() < a.vie() || !auSpawn(p)) {
                refus(p, Component.translatableWithFallback("heraliz_grades.warp.annule", "Téléportation annulée : tu as bougé."));
                return true;
            }
            if (ticks < a.fin()) return false;
            Component non = Restrictions.empechement(p);
            if (non != null) {
                refus(p, non);
                return true;
            }
            Warp w = WARPS.get(a.warp());
            ServerLevel monde = w == null ? null : srv.getLevel(w.monde());
            if (w != null && monde != null && permis(p, w)) teleporter(p, monde, w);
            return true;
        });
    }

    // ------------------------------------------------------------------ menu
    static void envoyerMenu(ServerPlayer p) {
        List<ReseauGrades.VueWarp> vues = new ArrayList<>();
        for (Warp w : WARPS.values()) {
            vues.add(new ReseauGrades.VueWarp(w.id(), w.nom(), w.description(), w.monde().location().toString(), w.rang(), permis(p, w), icone(w)));
        }
        ReseauGrades.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new ReseauGrades.EtatWarps(vues, auSpawn(p) || staff(p)));
    }

    private static ItemStack icone(Warp w) {
        ResourceLocation rl = ResourceLocation.tryParse(w.icone());
        Item it = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return new ItemStack(it == null || it == Items.AIR ? Items.COMPASS : it);
    }

    // ------------------------------------------------------------------ commandes
    private static final SuggestionProvider<CommandSourceStack> NOMS = (c, b) -> SharedSuggestionProvider.suggest(WARPS.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> GRADES = (c, b) -> SharedSuggestionProvider.suggest(
            List.of("aucun", "vip", "rescape", "traqueur", "roi_de_la_zone"), b);

    private static boolean droitStaff(CommandSourceStack s) {
        return s.getEntity() instanceof ServerPlayer p ? staff(p) : CommandesGrades.console(s);
    }

    private static void commandes(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("warp")
                .executes(c -> {
                    envoyerMenu(c.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("set").requires(WarpsServeur::droitStaff)
                        .then(Commands.argument("nom", StringArgumentType.word()).executes(c -> poser(c, "aucun"))
                                .then(Commands.argument("grade", StringArgumentType.word()).suggests(GRADES)
                                        .executes(c -> poser(c, StringArgumentType.getString(c, "grade"))))))
                .then(Commands.literal("del").requires(WarpsServeur::droitStaff)
                        .then(Commands.argument("nom", StringArgumentType.word()).suggests(NOMS).executes(WarpsServeur::retirer)))
                .then(Commands.literal("liste").requires(WarpsServeur::droitStaff).executes(c -> liste(c.getSource())))
                .then(Commands.literal("nom").requires(WarpsServeur::droitStaff)
                        .then(Commands.argument("nom", StringArgumentType.word()).suggests(NOMS)
                                .then(Commands.argument("texte", StringArgumentType.greedyString()).executes(c -> texte(c, false)))))
                .then(Commands.literal("description").requires(WarpsServeur::droitStaff)
                        .then(Commands.argument("nom", StringArgumentType.word()).suggests(NOMS)
                                .then(Commands.argument("texte", StringArgumentType.greedyString()).executes(c -> texte(c, true)))))
                .then(Commands.literal("icone").requires(WarpsServeur::droitStaff)
                        .then(Commands.argument("nom", StringArgumentType.word()).suggests(NOMS).executes(WarpsServeur::icone)))
                .then(Commands.argument("nom", StringArgumentType.word()).suggests(NOMS)
                        .executes(c -> aller(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "nom")))));
    }

    private static int rang(String grade) {
        return switch (grade.toLowerCase(Locale.ROOT)) {
            case "vip", "rescape" -> 1;
            case "traqueur" -> 2;
            case "roi_de_la_zone", "roi" -> 3;
            default -> 0;
        };
    }

    private static int poser(CommandContext<CommandSourceStack> c, String grade) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(c, "nom").toLowerCase(Locale.ROOT);
        if (RESERVES.contains(id)) {
            c.getSource().sendFailure(Component.translatableWithFallback("heraliz_grades.warp.nom_reserve", "Ce nom est réservé : %s", id));
            return 0;
        }
        Warp avant = WARPS.get(id);
        if (avant == null && WARPS.size() >= MAX_WARPS) {
            c.getSource().sendFailure(Component.translatableWithFallback("heraliz_grades.warp.trop", "%s warps au plus.", String.valueOf(MAX_WARPS)));
            return 0;
        }
        Warp w = new Warp(id, avant == null ? id : avant.nom(), avant == null ? "" : avant.description(), p.level().dimension(), p.position(),
                p.getYRot(), p.getXRot(), rang(grade), avant == null ? "minecraft:compass" : avant.icone());
        WARPS.put(id, w);
        sauver();
        c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_grades.warp.pose", "Warp « %s » posé ici (%s).", id,
                w.rang() == 0 ? Component.translatableWithFallback("heraliz_grades.warp.tous", "pour tous") : nomGrade(w.rang())).withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static int retirer(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "nom").toLowerCase(Locale.ROOT);
        if (WARPS.remove(id) == null) return inconnu(c.getSource(), id);
        sauver();
        c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_grades.warp.retire", "Warp « %s » retiré.", id), true);
        return 1;
    }

    private static int texte(CommandContext<CommandSourceStack> c, boolean description) {
        String id = StringArgumentType.getString(c, "nom").toLowerCase(Locale.ROOT);
        Warp w = WARPS.get(id);
        if (w == null) return inconnu(c.getSource(), id);
        String t = StringArgumentType.getString(c, "texte");
        if (t.length() > 120) t = t.substring(0, 120);
        WARPS.put(id, description ? w.avec(w.nom(), t, w.rang(), w.icone()) : w.avec(t.length() > 32 ? t.substring(0, 32) : t, w.description(), w.rang(), w.icone()));
        sauver();
        c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_grades.warp.modifie", "Warp « %s » modifié.", id), false);
        return 1;
    }

    private static int icone(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String id = StringArgumentType.getString(c, "nom").toLowerCase(Locale.ROOT);
        Warp w = WARPS.get(id);
        if (w == null) return inconnu(c.getSource(), id);
        ItemStack main = c.getSource().getPlayerOrException().getMainHandItem();
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(main.isEmpty() ? Items.COMPASS : main.getItem());
        WARPS.put(id, w.avec(w.nom(), w.description(), w.rang(), String.valueOf(rl)));
        sauver();
        c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_grades.warp.modifie", "Warp « %s » modifié.", id), false);
        return 1;
    }

    private static int liste(CommandSourceStack s) {
        if (WARPS.isEmpty()) {
            s.sendSuccess(() -> Component.translatableWithFallback("heraliz_grades.warp.aucun", "Aucun warp."), false);
            return 0;
        }
        for (Warp w : WARPS.values()) {
            String lieu = w.monde().location() + " " + (int) Math.floor(w.pos().x) + " " + (int) Math.floor(w.pos().y) + " " + (int) Math.floor(w.pos().z);
            s.sendSuccess(() -> Component.translatableWithFallback("heraliz_grades.warp.ligne", "%s (%s) : %s, %s", w.id(), w.nom(), lieu,
                    w.rang() == 0 ? Component.translatableWithFallback("heraliz_grades.warp.tous", "pour tous") : nomGrade(w.rang())), false);
        }
        return WARPS.size();
    }

    private static int inconnu(CommandSourceStack s, String id) {
        s.sendFailure(Component.translatableWithFallback("heraliz_grades.warp.inconnu", "Warp inconnu : %s", id));
        return 0;
    }

    // ------------------------------------------------------------------ fichier
    private static void demarrage(ServerAboutToStartEvent e) {
        fichier = e.getServer().getServerDirectory().toPath().resolve("heraliz_grades").resolve("warps.json");
        WARPS.clear();
        ATTENTES.clear();
        DERNIERE_DEMANDE.clear();
        if (!Files.exists(fichier)) return;
        try {
            JsonObject racine = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement el : racine.getAsJsonArray("warps")) {
                JsonObject o = el.getAsJsonObject();
                ResourceLocation monde = ResourceLocation.tryParse(o.get("monde").getAsString());
                if (monde == null) continue;
                JsonArray p = o.getAsJsonArray("pos");
                String id = o.get("id").getAsString();
                WARPS.put(id, new Warp(id, o.get("nom").getAsString(), o.has("description") ? o.get("description").getAsString() : "",
                        ResourceKey.create(Registries.DIMENSION, monde), new Vec3(p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble()),
                        o.get("yaw").getAsFloat(), o.get("pitch").getAsFloat(), o.has("rang") ? o.get("rang").getAsInt() : 0,
                        o.has("icone") ? o.get("icone").getAsString() : "minecraft:compass"));
            }
            LOG.info("[warps] {} warp(s) lu(s)", WARPS.size());
        } catch (IOException | RuntimeException ex) {
            // fichier illisible : jamais écrasé (aucune sauvegarde tant qu'il n'a pas été lu)
            LOG.error("[warps] {} illisible, warps coupés et fichier gardé tel quel : {}", fichier, ex.toString());
            fichier = null;
        }
    }

    private static void sauver() {
        if (fichier == null) return;
        JsonArray liste = new JsonArray();
        for (Warp w : WARPS.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", w.id());
            o.addProperty("nom", w.nom());
            o.addProperty("description", w.description());
            o.addProperty("monde", w.monde().location().toString());
            JsonArray p = new JsonArray();
            p.add(w.pos().x);
            p.add(w.pos().y);
            p.add(w.pos().z);
            o.add("pos", p);
            o.addProperty("yaw", w.yRot());
            o.addProperty("pitch", w.xRot());
            o.addProperty("rang", w.rang());
            o.addProperty("icone", w.icone());
            liste.add(o);
        }
        JsonObject racine = new JsonObject();
        racine.add("warps", liste);
        try {
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling("warps.json.tmp");
            Files.writeString(tmp, GSON.toJson(racine), StandardCharsets.UTF_8);
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            LOG.error("[warps] enregistrement impossible : {}", ex.toString());
        }
    }
}
