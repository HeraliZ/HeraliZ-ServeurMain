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
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Zones VIP (Grades 1.2.0) : n'importe quelle zone délimitée au bloc près par le staff, où seuls entrent les joueurs d'un
 * grade de la boutique (Rescapé, Traqueur, Roi de la Zone ; {@link GradesApi#rangBoutique}). Les autres sont ramenés à leur
 * dernière place hors de la zone (vraie téléportation, suivie par Spartan), avec un message au-dessus de la barre d'objets.
 * Le staff sans grade boutique n'entre qu'en créatif ou en spectateur (comme la zone VIP du casino).
 * <p>
 * {@code /zonevip baguette} (bâton de blaze : clic gauche = coin 1, clic droit = coin 2, contour en particules chez celui
 * qui la tient), {@code coin1|coin2 [x y z]}, {@code creer <nom>}, {@code retirer <nom>}, {@code liste}, {@code voir <nom>},
 * {@code tp <nom>}. Op (permission 2) connecté sur AuthMe, ou console. Zones gardées hors du monde :
 * {@code heraliz_grades/zones_vip.json} (avec le nom du monde de chaque zone). Fil serveur seulement.
 */
final class ZonesVip {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String MARQUE_BAGUETTE = "HeralizZoneVip";
    private static final DustParticleOptions OR = new DustParticleOptions(new Vector3f(1.0F, 0.76F, 0.23F), 1.0F);
    private static final DustParticleOptions VERT = new DustParticleOptions(new Vector3f(0.3F, 1.0F, 0.4F), 1.0F);

    record Zone(String nom, ResourceKey<Level> monde, BlockPos a, BlockPos b) {
        int minX() { return Math.min(a.getX(), b.getX()); }
        int minY() { return Math.min(a.getY(), b.getY()); }
        int minZ() { return Math.min(a.getZ(), b.getZ()); }
        int maxX() { return Math.max(a.getX(), b.getX()) + 1; }
        int maxY() { return Math.max(a.getY(), b.getY()) + 1; }
        int maxZ() { return Math.max(a.getZ(), b.getZ()) + 1; }

        /** Le joueur (ses pieds ou sa tête) est-il dans la boîte ? */
        boolean contient(ServerPlayer p) {
            if (!p.level().dimension().equals(monde)) return false;
            double x = p.getX(), y = p.getY(), z = p.getZ();
            return x >= minX() && x < maxX() && z >= minZ() && z < maxZ() && y + p.getBbHeight() > minY() && y < maxY();
        }
    }

    record Place(ResourceKey<Level> monde, Vec3 pos, float yRot, float xRot) {
    }

    private static final Map<String, Zone> ZONES = new LinkedHashMap<>();
    private static final Map<UUID, BlockPos[]> COINS = new HashMap<>();
    /** Dernière place connue de chaque joueur hors de toute zone où il n'a pas le droit d'entrer. */
    private static final Map<UUID, Place> DEHORS = new HashMap<>();
    /** Zone montrée en particules par /zonevip voir : joueur → (zone, ticks restants). */
    private static final Map<UUID, Object[]> MONTREES = new HashMap<>();
    private static Path fichier;
    private static int ticks;

    private ZonesVip() {
    }

    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(ZonesVip::demarrage);
        MinecraftForge.EVENT_BUS.addListener(ZonesVip::commandes);
        MinecraftForge.EVENT_BUS.addListener(ZonesVip::tick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ZonesVip::clicGauche);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ZonesVip::clicDroit);
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            DEHORS.remove(e.getEntity().getUUID());
            MONTREES.remove(e.getEntity().getUUID());
        });
    }

    // ------------------------------------------------------------------ règle
    /** Ce joueur peut-il entrer dans une zone VIP ? */
    static boolean permis(ServerPlayer p) {
        return p.isCreative() || p.isSpectator() || GradesApi.rangBoutique(p.getUUID()) >= 1;
    }

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        ticks++;
        MinecraftServer srv = e.getServer();
        if (!ZONES.isEmpty() && ticks % 4 == 0) {
            for (ServerPlayer p : srv.getPlayerList().getPlayers()) garder(srv, p);
        }
        if (ticks % 10 == 0) particules(srv);
    }

    private static void garder(MinecraftServer srv, ServerPlayer p) {
        if (permis(p)) {
            DEHORS.remove(p.getUUID());
            return;
        }
        Zone dedans = null;
        for (Zone z : ZONES.values()) {
            if (z.contient(p)) {
                dedans = z;
                break;
            }
        }
        if (dedans == null) {
            if (p.onGround() || p.isPassenger() || p.isInWater()) {
                DEHORS.put(p.getUUID(), new Place(p.level().dimension(), p.position(), p.getYRot(), p.getXRot()));
            }
            return;
        }
        if (p.isPassenger()) p.stopRiding();
        Place place = DEHORS.get(p.getUUID());
        ServerLevel monde = place == null ? null : srv.getLevel(place.monde());
        if (monde == null || dansUneZone(place)) {
            monde = (ServerLevel) p.level();
            Vec3 sortie = sortie(dedans, p);
            place = new Place(monde.dimension(), sortie, p.getYRot(), p.getXRot());
        }
        p.teleportTo(monde, place.pos().x, place.pos().y, place.pos().z, place.yRot(), place.xRot());
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        p.displayClientMessage(Component.translatableWithFallback("heraliz_grades.zonevip.refus",
                "Zone VIP : réservée aux grades Rescapé, Traqueur et Roi de la Zone.").withStyle(ChatFormatting.GOLD), true);
    }

    private static boolean dansUneZone(Place place) {
        for (Zone z : ZONES.values()) {
            if (!z.monde().equals(place.monde())) continue;
            Vec3 v = place.pos();
            if (v.x >= z.minX() && v.x < z.maxX() && v.z >= z.minZ() && v.z < z.maxZ() && v.y >= z.minY() - 1.8 && v.y < z.maxY()) return true;
        }
        return false;
    }

    /** Pas de place connue : le bord le plus proche de la zone, un demi-bloc dehors, sur le sol. */
    private static Vec3 sortie(Zone z, ServerPlayer p) {
        double x = p.getX(), zz = p.getZ();
        double[] ecarts = {x - z.minX(), z.maxX() - x, zz - z.minZ(), z.maxZ() - zz};
        int bord = 0;
        for (int i = 1; i < 4; i++) if (ecarts[i] < ecarts[bord]) bord = i;
        switch (bord) {
            case 0 -> x = z.minX() - 0.7;
            case 1 -> x = z.maxX() + 0.7;
            case 2 -> zz = z.minZ() - 0.7;
            default -> zz = z.maxZ() + 0.7;
        }
        ServerLevel l = (ServerLevel) p.level();
        int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(zz));
        double sol = Math.abs(y - p.getY()) < 6 ? p.getY() : y;
        return new Vec3(x, sol, zz);
    }

    // ------------------------------------------------------------------ baguette et contours
    private static boolean baguette(ItemStack s) {
        return s.is(Items.BLAZE_ROD) && s.hasTag() && s.getTag().getBoolean(MARQUE_BAGUETTE);
    }

    private static boolean staffJoueur(ServerPlayer p) {
        return p.hasPermissions(2) && PontBukkit.authentifie(p);
    }

    private static void clicGauche(PlayerInteractEvent.LeftClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !baguette(e.getItemStack()) || !staffJoueur(p)) return;
        e.setCanceled(true);
        if (e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        poserCoin(p, 0, e.getPos());
    }

    private static void clicDroit(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !baguette(e.getItemStack()) || !staffJoueur(p)) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getHand() == InteractionHand.MAIN_HAND) poserCoin(p, 1, e.getPos());
    }

    private static void poserCoin(ServerPlayer p, int i, BlockPos pos) {
        COINS.computeIfAbsent(p.getUUID(), k -> new BlockPos[2])[i] = pos.immutable();
        p.sendSystemMessage(Component.translatableWithFallback("heraliz_grades.zonevip.coin", "Coin %s : %s",
                String.valueOf(i + 1), pos.getX() + " " + pos.getY() + " " + pos.getZ()).withStyle(ChatFormatting.GOLD));
    }

    private static void particules(MinecraftServer srv) {
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (baguette(p.getMainHandItem()) || baguette(p.getOffhandItem())) {
                BlockPos[] c = COINS.get(p.getUUID());
                if (c != null && c[0] != null && c[1] != null) contour(p, new Zone("", p.level().dimension(), c[0], c[1]), VERT);
                for (Zone z : ZONES.values()) if (z.monde().equals(p.level().dimension())) contour(p, z, OR);
            }
            Object[] m = MONTREES.get(p.getUUID());
            if (m != null) {
                Zone z = ZONES.get((String) m[0]);
                int reste = (int) m[1] - 10;
                if (z == null || reste <= 0) MONTREES.remove(p.getUUID());
                else {
                    m[1] = reste;
                    if (z.monde().equals(p.level().dimension())) contour(p, z, OR);
                }
            }
        }
    }

    /** Les 12 arêtes de la zone, une particule par bloc (au plus 64 par arête), envoyées à ce joueur seulement. */
    private static void contour(ServerPlayer p, Zone z, DustParticleOptions couleur) {
        ServerLevel l = (ServerLevel) p.level();
        double[] xs = {z.minX(), z.maxX()}, ys = {z.minY(), z.maxY()}, zs = {z.minZ(), z.maxZ()};
        if (p.position().distanceToSqr((z.minX() + z.maxX()) / 2.0, p.getY(), (z.minZ() + z.maxZ()) / 2.0) > 160 * 160) return;
        for (double y : ys) for (double zz : zs) arete(p, l, couleur, xs[0], y, zz, xs[1], y, zz);
        for (double x : xs) for (double zz : zs) arete(p, l, couleur, x, ys[0], zz, x, ys[1], zz);
        for (double x : xs) for (double y : ys) arete(p, l, couleur, x, y, zs[0], x, y, zs[1]);
    }

    private static void arete(ServerPlayer p, ServerLevel l, DustParticleOptions c, double x1, double y1, double z1, double x2, double y2, double z2) {
        double longueur = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
        int n = (int) Math.min(64, Math.max(1, Math.ceil(longueur)));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            l.sendParticles(p, c, true, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t, 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ commandes
    private static final SuggestionProvider<CommandSourceStack> NOMS = (c, b) -> SharedSuggestionProvider.suggest(ZONES.keySet(), b);

    private static boolean droit(CommandSourceStack s) {
        if (s.getEntity() instanceof ServerPlayer p) return staffJoueur(p);
        return CommandesGrades.console(s);
    }

    private static void commandes(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("zonevip").requires(ZonesVip::droit)
                .then(Commands.literal("baguette").executes(c -> donnerBaguette(c.getSource())))
                .then(Commands.literal("coin1").executes(c -> coinIci(c, 0))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> coinPos(c, 0))))
                .then(Commands.literal("coin2").executes(c -> coinIci(c, 1))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> coinPos(c, 1))))
                .then(Commands.literal("creer").then(Commands.argument("nom", StringArgumentType.word()).executes(ZonesVip::creer)))
                .then(Commands.literal("retirer").then(Commands.argument("nom", StringArgumentType.word()).suggests(NOMS).executes(ZonesVip::retirer)))
                .then(Commands.literal("liste").executes(c -> liste(c.getSource())))
                .then(Commands.literal("voir").then(Commands.argument("nom", StringArgumentType.word()).suggests(NOMS).executes(ZonesVip::voir)))
                .then(Commands.literal("tp").then(Commands.argument("nom", StringArgumentType.word()).suggests(NOMS).executes(ZonesVip::tp))));
    }

    private static int donnerBaguette(CommandSourceStack s) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        ItemStack it = new ItemStack(Items.BLAZE_ROD);
        CompoundTag t = it.getOrCreateTag();
        t.putBoolean(MARQUE_BAGUETTE, true);
        it.setHoverName(Component.translatableWithFallback("heraliz_grades.zonevip.baguette", "Baguette des zones VIP").withStyle(ChatFormatting.GOLD));
        if (!p.getInventory().add(it)) p.drop(it, false);
        s.sendSuccess(() -> Component.translatableWithFallback("heraliz_grades.zonevip.baguette.aide",
                "Clic gauche sur un bloc = coin 1, clic droit = coin 2, puis /zonevip creer <nom>."), false);
        return 1;
    }

    private static int coinIci(CommandContext<CommandSourceStack> c, int i) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        poserCoin(p, i, p.blockPosition());
        return 1;
    }

    private static int coinPos(CommandContext<CommandSourceStack> c, int i) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        poserCoin(p, i, BlockPosArgument.getBlockPos(c, "pos"));
        return 1;
    }

    private static int creer(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String nom = StringArgumentType.getString(c, "nom");
        BlockPos[] coins = COINS.get(p.getUUID());
        if (coins == null || coins[0] == null || coins[1] == null) {
            c.getSource().sendFailure(Component.translatableWithFallback("heraliz_grades.zonevip.sans_coins",
                    "Pose d'abord les deux coins (/zonevip baguette, ou /zonevip coin1 et coin2)."));
            return 0;
        }
        Zone z = new Zone(nom, p.level().dimension(), coins[0], coins[1]);
        boolean remplace = ZONES.put(nom, z) != null;
        sauver();
        COINS.remove(p.getUUID());
        String taille = (z.maxX() - z.minX()) + " × " + (z.maxY() - z.minY()) + " × " + (z.maxZ() - z.minZ());
        c.getSource().sendSuccess(() -> Component.translatableWithFallback(remplace ? "heraliz_grades.zonevip.redefinie" : "heraliz_grades.zonevip.creee",
                remplace ? "Zone VIP « %s » redéfinie (%s blocs)." : "Zone VIP « %s » créée (%s blocs).", nom, taille).withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static int retirer(CommandContext<CommandSourceStack> c) {
        String nom = StringArgumentType.getString(c, "nom");
        if (ZONES.remove(nom) == null) return inconnue(c.getSource(), nom);
        sauver();
        c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_grades.zonevip.retiree", "Zone VIP « %s » retirée.", nom), true);
        return 1;
    }

    private static int liste(CommandSourceStack s) {
        if (ZONES.isEmpty()) {
            s.sendSuccess(() -> Component.translatableWithFallback("heraliz_grades.zonevip.aucune", "Aucune zone VIP."), false);
            return 0;
        }
        for (Zone z : ZONES.values()) {
            s.sendSuccess(() -> Component.translatableWithFallback("heraliz_grades.zonevip.ligne", "%s : %s, de %s à %s",
                    z.nom(), z.monde().location().toString(), z.minX() + " " + z.minY() + " " + z.minZ(),
                    (z.maxX() - 1) + " " + (z.maxY() - 1) + " " + (z.maxZ() - 1)), false);
        }
        return ZONES.size();
    }

    private static int voir(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String nom = StringArgumentType.getString(c, "nom");
        if (!ZONES.containsKey(nom)) return inconnue(c.getSource(), nom);
        MONTREES.put(c.getSource().getPlayerOrException().getUUID(), new Object[]{nom, 200});
        return 1;
    }

    private static int tp(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String nom = StringArgumentType.getString(c, "nom");
        Zone z = ZONES.get(nom);
        if (z == null) return inconnue(c.getSource(), nom);
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel l = c.getSource().getServer().getLevel(z.monde());
        if (l == null) return inconnue(c.getSource(), nom);
        p.teleportTo(l, (z.minX() + z.maxX()) / 2.0, z.minY(), (z.minZ() + z.maxZ()) / 2.0, p.getYRot(), p.getXRot());
        return 1;
    }

    private static int inconnue(CommandSourceStack s, String nom) {
        s.sendFailure(Component.translatableWithFallback("heraliz_grades.zonevip.inconnue", "Zone VIP inconnue : %s", nom));
        return 0;
    }

    // ------------------------------------------------------------------ fichier
    private static void demarrage(ServerAboutToStartEvent e) {
        fichier = e.getServer().getServerDirectory().toPath().resolve("heraliz_grades").resolve("zones_vip.json");
        ZONES.clear();
        COINS.clear();
        DEHORS.clear();
        MONTREES.clear();
        if (!Files.exists(fichier)) return;
        try {
            JsonObject racine = JsonParser.parseString(Files.readString(fichier, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement el : racine.getAsJsonArray("zones")) {
                JsonObject o = el.getAsJsonObject();
                ResourceLocation monde = ResourceLocation.tryParse(o.get("monde").getAsString());
                if (monde == null) continue;
                String nom = o.get("nom").getAsString();
                ZONES.put(nom, new Zone(nom, ResourceKey.create(Registries.DIMENSION, monde), lirePos(o.getAsJsonArray("a")), lirePos(o.getAsJsonArray("b"))));
            }
            LOG.info("[zonevip] {} zone(s) VIP lue(s)", ZONES.size());
        } catch (IOException | RuntimeException ex) {
            // fichier illisible : jamais écrasé (aucune sauvegarde tant qu'il n'a pas été lu)
            LOG.error("[zonevip] {} illisible, zones VIP coupées et fichier gardé tel quel : {}", fichier, ex.toString());
            fichier = null;
        }
    }

    private static BlockPos lirePos(JsonArray a) {
        return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
    }

    private static JsonArray pos(BlockPos p) {
        JsonArray a = new JsonArray();
        a.add(p.getX());
        a.add(p.getY());
        a.add(p.getZ());
        return a;
    }

    private static void sauver() {
        if (fichier == null) return;
        JsonArray zones = new JsonArray();
        for (Zone z : ZONES.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("nom", z.nom());
            o.addProperty("monde", z.monde().location().toString());
            o.add("a", pos(z.a()));
            o.add("b", pos(z.b()));
            zones.add(o);
        }
        JsonObject racine = new JsonObject();
        racine.add("zones", zones);
        try {
            Files.createDirectories(fichier.getParent());
            Path tmp = fichier.resolveSibling("zones_vip.json.tmp");
            Files.writeString(tmp, GSON.toJson(racine), StandardCharsets.UTF_8);
            Files.move(tmp, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            LOG.error("[zonevip] enregistrement impossible : {}", ex.toString());
        }
    }
}
