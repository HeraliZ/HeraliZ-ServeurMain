package fr.heraliz.vehicules.dev;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import fr.heraliz.vehicules.HeralizVehicules;
import fr.heraliz.vehicules.VehiculesConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.ywzj.vehicle.all.AllKeys;
import org.ywzj.vehicle.entity.vehicle.AbstractVehicle;
import org.ywzj.vehicle.item.ParachutePackItem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Banc d'essai (client de dev seulement, activé par -Dheraliz.vehicules.harness=scenario.json, voir tools/vehicules_harness.py) :
 * ouvre un monde plat, joue un scénario (commandes, pose et conduite des véhicules, essence, descente « comme les bases »,
 * téléportation à bord, parachute, sacs sur le dos, captures), puis quitte. Journal : lignes [banc].
 */
@Mod.EventBusSubscriber(modid = HeralizVehicules.MODID, value = Dist.CLIENT)
public final class BancVehicules {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String FILE = System.getProperty("heraliz.vehicules.harness");
    private static boolean launched;
    private static long startTick = -1;
    private static final List<JsonObject> STEPS = new ArrayList<>();
    private static int next;

    private BancVehicules() {
    }

    public static boolean enabled() {
        return FILE != null;
    }

    @SubscribeEvent
    public static void onTitle(ScreenEvent.Init.Post e) {
        if (!enabled() || launched) return;
        if (e.getScreen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
            // premier lancement d'un dossier run neuf : écran d'accessibilité au lieu du menu
            Minecraft.getInstance().options.onboardAccessibility = false;
            Minecraft.getInstance().options.save();
            Minecraft.getInstance().setScreen(new TitleScreen());
            return;
        }
        if (!(e.getScreen() instanceof TitleScreen)) return;
        launched = true;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(Path.of(FILE))).getAsJsonObject();
            for (JsonElement el : root.getAsJsonArray("steps")) STEPS.add(el.getAsJsonObject());
        } catch (Exception ex) {
            LOG.error("Scénario illisible : {}", FILE, ex);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String name = "vehicules_banc";
        if (mc.getLevelSource().levelExists(name)) {
            mc.createWorldOpenFlows().loadLevel(e.getScreen(), name);
        } else {
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            // comme sur le serveur : retours de commande coupés
            rules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(false, null);
            LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(1234L, false, false),
                    reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (!enabled() || e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        if (startTick < 0) {
            startTick = mc.level.getGameTime() + 100;
            return;
        }
        long t = mc.level.getGameTime() - startTick;
        while (next < STEPS.size() && STEPS.get(next).get("t").getAsLong() <= t) {
            try {
                run(mc, STEPS.get(next++));
            } catch (RuntimeException ex) {
                LOG.error("[banc] étape {} en échec", STEPS.get(next - 1), ex);
            }
        }
    }

    private static ServerPlayer serverPlayer(MinecraftServer s) {
        return s.getPlayerList().getPlayers().isEmpty() ? null : s.getPlayerList().getPlayers().get(0);
    }

    private static AbstractVehicle nearestVehicle(Level level, Vec3 at) {
        return level.getEntitiesOfClass(AbstractVehicle.class, new AABB(at, at).inflate(40)).stream()
                .min(Comparator.comparingDouble(v -> v.distanceToSqr(at))).orElse(null);
    }

    private static KeyMapping key(Minecraft mc, String name) {
        for (KeyMapping k : mc.options.keyMappings) if (k.getName().equals(name)) return k;
        return null;
    }

    private static void run(Minecraft mc, JsonObject s) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (s.has("cmd")) {
            String cmd = s.get("cmd").getAsString();
            server.execute(() -> {
                ServerPlayer p = serverPlayer(server);
                var src = (p != null ? p.createCommandSourceStack() : server.createCommandSourceStack()).withPermission(4).withSuppressedOutput();
                server.getCommands().performPrefixedCommand(src, cmd);
            });
        }
        if (s.has("view")) {
            String v = s.get("view").getAsString();
            mc.options.setCameraType(v.equals("back") ? CameraType.THIRD_PERSON_BACK : v.equals("front") ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
        }
        if (s.has("slot")) mc.player.getInventory().selected = s.get("slot").getAsInt();
        if (s.has("hud")) mc.options.hideGui = !s.get("hud").getAsBoolean();
        if (s.has("fov")) mc.options.fov().set(s.get("fov").getAsInt());
        if (s.has("screen")) mc.setScreen(s.get("screen").getAsString().equals("inv") ? new InventoryScreen(mc.player) : null);
        if (s.has("look")) {
            // regarde un point [x, y, z] ou le véhicule le plus proche ("vehicule")
            JsonElement l = s.get("look");
            Vec3 target;
            if (l.isJsonArray()) {
                target = new Vec3(l.getAsJsonArray().get(0).getAsDouble(), l.getAsJsonArray().get(1).getAsDouble(), l.getAsJsonArray().get(2).getAsDouble());
            } else {
                AbstractVehicle v = nearestVehicle(mc.level, mc.player.position());
                target = v == null ? null : v.position().add(0, 1, 0);
            }
            if (target != null) mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
        }
        if (s.has("place")) {
            // clic droit de l'objet en main sur le dessus du bloc [x, y, z] (pose d'un véhicule)
            var a = s.getAsJsonArray("place");
            BlockPos pos = new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
        }
        if (s.has("interact")) {
            // clic droit sur le véhicule le plus proche (avec l'objet en main : jerrican = plein ; main vide = monter)
            AbstractVehicle v = nearestVehicle(mc.level, mc.player.position());
            LOG.info("[banc] interaction avec {} (main : {})", v == null ? "rien" : v.getVehicleId(), ForgeRegistries.ITEMS.getKey(mc.player.getMainHandItem().getItem()));
            if (v != null) mc.gameMode.interact(mc.player, v, InteractionHand.MAIN_HAND);
        }
        if (s.has("hold")) {
            KeyMapping k = key(mc, s.get("hold").getAsString());
            if (k != null) k.setDown(true);
            else LOG.warn("[banc] touche inconnue {}", s.get("hold").getAsString());
        }
        if (s.has("release")) {
            KeyMapping k = key(mc, s.get("release").getAsString());
            if (k != null) k.setDown(false);
        }
        if (s.has("keys")) {
            // chaque touche du moteur : touche, touche par défaut, contexte, et les touches du jeu en conflit (écran des touches)
            int conflits = 0;
            for (KeyMapping k : mc.options.keyMappings) {
                if (!k.getName().startsWith("key.ywzj_vehicle.") && !k.getName().startsWith("key.apricityui.")) continue;
                List<String> avec = new ArrayList<>();
                for (KeyMapping o : mc.options.keyMappings) {
                    if (o != k && k.same(o)) avec.add(o.getName());
                }
                conflits += avec.size();
                LOG.info("[banc] touche {} = {} (defaut {}, active : {}) conflits : {}", k.getName(), k.getKey().getName(), k.getDefaultKey().getName(),
                        k.getKeyConflictContext() == fr.heraliz.vehicules.client.ClientVehicules.A_BORD ? "a bord"
                                : k.getKeyConflictContext() == fr.heraliz.vehicules.client.ClientVehicules.EN_CHUTE ? "en chute" : k.getKeyConflictContext(),
                        avec.isEmpty() ? "aucun" : avec);
            }
            LOG.info("[banc] touches du moteur : {} conflit(s) au total", conflits);
        }
        if (s.has("engine")) {
            boolean on = s.get("engine").getAsBoolean();
            server.execute(() -> {
                ServerPlayer p = serverPlayer(server);
                if (p != null && p.getVehicle() instanceof AbstractVehicle v) {
                    v.toggleEngine(on);
                    LOG.info("[banc] moteur {} : {}", v.getVehicleId(), v.isEngineOn());
                } else LOG.warn("[banc] moteur : le joueur n'est dans aucun véhicule");
            });
        }
        if (s.has("approche")) {
            // le joueur (à pied) se place à 2 blocs du véhicule le plus proche, face à lui
            server.execute(() -> {
                ServerPlayer p = serverPlayer(server);
                AbstractVehicle v = p.level().getEntitiesOfClass(AbstractVehicle.class, p.getBoundingBox().inflate(150)).stream()
                        .min(Comparator.comparingDouble(a -> a.distanceToSqr(p))).orElse(null);
                if (v != null) {
                    p.teleportTo(v.getX() + 2.5, v.getY(), v.getZ() - 1.5);
                    LOG.info("[banc] approche de {} en {}", v.getVehicleId(), v.blockPosition());
                }
            });
        }
        if (s.has("descente")) {
            // exactement ce que font nos téléportations (heralisz_bases server/Descente, heralisz_matchs coffre/Descente)
            server.execute(() -> {
                ServerPlayer p = serverPlayer(server);
                Entity v = p.getVehicle();
                p.stopRiding();
                p.fallDistance = 0;
                p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
                LOG.info("[banc] descente forcee (comme les bases) de {} : passager = {}, joueur en {} / vehicule en {}",
                        v == null ? "rien" : ForgeRegistries.ENTITY_TYPES.getKey(v.getType()), p.isPassenger(), p.blockPosition(), v == null ? "-" : v.blockPosition());
            });
        }
        if (s.has("tpDim")) {
            // téléportation vers une autre dimension en étant à bord (comme une téléportation de base vers le bunker)
            String dim = s.get("tpDim").getAsString();
            server.execute(() -> {
                ServerPlayer p = serverPlayer(server);
                Entity v = p.getVehicle();
                ServerLevel lvl = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim)));
                p.teleportTo(lvl, 0.5, 100, 0.5, 0, 0);
                LOG.info("[banc] teleporte vers {} depuis un vehicule ({}) : passager = {}, dimension = {}, vehicule reste en {} {}", dim,
                        v == null ? "rien" : ForgeRegistries.ENTITY_TYPES.getKey(v.getType()), p.isPassenger(), p.level().dimension().location(),
                        v == null ? "-" : v.level().dimension().location(), v == null ? "-" : v.blockPosition());
            });
        }
        if (s.has("interdire")) {
            // ajoute une dimension aux dimensions sans véhicules (le banc n'a ni matchs ni bunkers)
            String dim = s.get("interdire").getAsString();
            server.execute(() -> {
                List<String> l = new ArrayList<>(VehiculesConfig.DIMENSIONS_INTERDITES.get());
                if (!l.contains(dim)) l.add(dim);
                VehiculesConfig.DIMENSIONS_INTERDITES.set(l);
                LOG.info("[banc] dimensions sans vehicules : {}", l);
            });
        }
        if (s.has("combat")) {
            int sec = s.get("combat").getAsInt();
            server.execute(() -> {
                ServerPlayer p = serverPlayer(server);
                if (sec <= 0) p.getPersistentData().remove("heralisz:combat_jusqua");
                else p.getPersistentData().putLong("heralisz:combat_jusqua", System.currentTimeMillis() + sec * 1000L);
                LOG.info("[banc] tag de combat : {} s", sec);
            });
        }
        if (s.has("parachute")) {
            server.execute(() -> {
                ServerPlayer p = serverPlayer(server);
                boolean peut = ParachutePackItem.canOpen(p);
                ParachutePackItem.open(p);
                LOG.info("[banc] parachute : pouvait s'ouvrir = {}, ouvert = {}, vitesse verticale = {}", peut,
                        ParachutePackItem.isOpen(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)), p.getDeltaMovement().y);
            });
        }
        if (s.has("backpackKey")) {
            KeyMapping k = key(mc, "key.backpack");
            if (k != null) {
                k.setDown(true);
                KeyMapping.click(k.getKey());
            }
            try {
                Class.forName("com.mrcrayfish.backpacked.client.ClientEvents").getMethod("onKeyInput", int.class, int.class, int.class, int.class)
                        .invoke(null, 66, 0, 1, 0);
            } catch (ReflectiveOperationException ex) {
                LOG.warn("[banc] touche du sac introuvable", ex);
            }
        }
        if (s.has("report")) report(mc, s.get("report").getAsString());
        if (s.has("shot")) Screenshot.grab(mc.gameDirectory, "vehicules_" + s.get("shot").getAsString() + ".png", mc.getMainRenderTarget(), msg -> {});
        if (s.has("quit")) mc.stop();
    }

    /** État écrit dans le journal (client + serveur) : véhicule, essence, position, parachute, écran, sac. */
    private static void report(Minecraft mc, String label) {
        MinecraftServer server = mc.getSingleplayerServer();
        String screen = mc.screen == null ? "aucun" : mc.screen.getClass().getSimpleName() + " « " + mc.screen.getTitle().getString() + " »";
        int cases = mc.player.containerMenu == null ? -1 : mc.player.containerMenu.slots.size();
        LOG.info("[banc] {} (client) : écran = {}, cases du menu = {} (sac = {}), main = {}", label, screen, cases, cases - 36,
                ForgeRegistries.ITEMS.getKey(mc.player.getMainHandItem().getItem()));
        server.execute(() -> {
            ServerPlayer p = serverPlayer(server);
            AbstractVehicle v = p.getVehicle() instanceof AbstractVehicle a ? a : nearestVehicle(p.level(), p.position());
            LOG.info("[banc] {} (serveur) : joueur {} en {} ({}), passager = {}, véhicule = {}{}", label, p.getGameProfile().getName(),
                    String.format("%.1f %.1f %.1f", p.getX(), p.getY(), p.getZ()), p.level().dimension().location(), p.isPassenger(),
                    v == null ? "aucun" : v.getVehicleId(),
                    v == null ? "" : String.format(" [à bord : %s, essence %.4f, puissance %.0f, moteur %s, position %.1f %.1f %.1f, vitesse %.2f]",
                            p.getVehicle() == v, v.getEnergy(), v.getPower(), v.isEngineOn(), v.getX(), v.getY(), v.getZ(), v.getDeltaMovement().length()));
            var chest = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
            if (!chest.isEmpty()) {
                LOG.info("[banc] {} : torse = {}, parachute ouvert = {}, au sol = {}, chute = {}", label, ForgeRegistries.ITEMS.getKey(chest.getItem()),
                        ParachutePackItem.isOpen(chest), p.onGround(), p.getDeltaMovement().y);
            }
        });
    }
}
