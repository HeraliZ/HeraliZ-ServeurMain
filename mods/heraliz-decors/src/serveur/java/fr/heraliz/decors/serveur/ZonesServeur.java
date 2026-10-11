package fr.heraliz.decors.serveur;

import com.google.gson.JsonParser;
import fr.heraliz.decors.HeralizDecors;
import fr.heraliz.decors.ambiance.Ambiance;
import fr.heraliz.decors.ambiance.Zone;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Zones dangereuses (1.4.0), côté serveur. Les zones du monde (data/heraliz_zones.json, écrit par la map) : gaz,
 * radiation, froid. Chaque seconde, pour chaque joueur, une exposition qui monte quand il est dans une zone sans la
 * protection voulue (plus vite au cœur qu'en bordure) et redescend dehors ; selon l'exposition, des effets de plus en
 * plus durs, puis des dégâts :
 * <ul>
 *   <li>gaz (masque à gaz ou combinaison Hazmat de l'Armurerie) : nausée, poison, faiblesse, puis flétrissement ;</li>
 *   <li>radiation (Hazmat) : faim, faiblesse, effet « irradié » s'il existe (GeckoLib du serveur), dégâts magiques que
 *   l'Armurerie réduit selon la protection contre la radiation ;</li>
 *   <li>froid (combinaison arctique) : gel (le givre de Minecraft, que l'Armurerie annule pour les combinaisons).</li>
 * </ul>
 * Les objets qui protègent sont reconnus à leur identifiant (Armurerie), portés en armure, en main gauche ou dans un
 * emplacement Curios (lu par réflexion : rien à installer en plus). Le client reçoit l'exposition pour l'écran.
 * Commandes (op) : /zones info, /zones recharger.
 */
@Mod.EventBusSubscriber(modid = HeralizDecors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ZonesServeur {
    private ZonesServeur() {}

    private static List<Zone> zones = new ArrayList<>();
    private static final Map<UUID, float[]> EXPO = new HashMap<>();          // gaz, radiation, froid (secondes)

    // protections (identifiants, * = n'importe quelle fin)
    private static final String[] P_GAZ = {"heraliz_armory:gas_mask_*", "heraliz_armory:hazmat_suit", "heraliz_armory:hazmat_renforce_suit",
            "heraliz_armory:*respirator*", "heraliz_armory:*gasmask*"};
    private static final String[] P_RAD = {"heraliz_armory:hazmat_suit", "heraliz_armory:hazmat_renforce_suit"};
    private static final String[] P_FROID = {"heraliz_armory:arctique_suit", "heraliz_armory:arctique_renforcee_suit"};

    @SubscribeEvent
    public static void demarrage(ServerStartedEvent e) {
        EXPO.clear();
        charger(e.getServer());
    }

    static void charger(MinecraftServer srv) {
        Path f = srv.getWorldPath(LevelResource.ROOT).resolve("data").resolve("heraliz_zones.json");
        List<Zone> l = new ArrayList<>();
        if (Files.exists(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                l = Zone.lireJson(JsonParser.parseReader(r).getAsJsonObject());
            } catch (Exception ex) {
                HeralizDecors.LOG.error("Zones dangereuses illisibles ({})", f, ex);
            }
        }
        zones = l;
        HeralizDecors.LOG.info("Zones dangereuses : {}", zones.size());
        Ambiance.CANAL.send(PacketDistributor.ALL.noArg(), new Ambiance.Zones(zones));
    }

    @SubscribeEvent
    public static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            Ambiance.CANAL.send(PacketDistributor.PLAYER.with(() -> sp), new Ambiance.Zones(zones));
        }
    }

    @SubscribeEvent
    public static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        EXPO.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || zones.isEmpty()) return;
        MinecraftServer srv = e.getServer();
        if (srv.getTickCount() % 20 != 0) return;
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            if (p.isCreative() || p.isSpectator() || p.level() != srv.overworld()) continue;
            float g = 0, ra = 0, fr = 0;
            for (Zone z : zones) {
                float n = z.niveau(p.getX(), p.getY(), p.getZ());
                if (n <= 0) continue;
                switch (z.genre()) {
                    case "gaz" -> g = Math.max(g, n);
                    case "radiation" -> ra = Math.max(ra, n);
                    case "froid" -> fr = Math.max(fr, n);
                    default -> { }
                }
            }
            boolean pg = protege(p, P_GAZ), pr = protege(p, P_RAD), pf = protege(p, P_FROID);
            float[] ex = EXPO.computeIfAbsent(p.getUUID(), k -> new float[3]);
            ex[0] = avancer(ex[0], pg ? 0 : g);
            ex[1] = avancer(ex[1], pr ? 0 : ra);
            ex[2] = avancer(ex[2], pf ? 0 : fr);
            effetsGaz(p, ex[0]);
            effetsRadiation(p, ex[1]);
            effetsFroid(p, ex[2], fr > 0 && !pf);
            Ambiance.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new Ambiance.Exposition(
                    Math.min(1f, ex[0] / 60f), Math.min(1f, ex[1] / 60f), Math.min(1f, ex[2] / 60f), pg && g > 0, pr && ra > 0, pf && fr > 0));
        }
    }

    /** Une seconde de plus : l'exposition monte selon l'intensité (jusqu'à 120 s), redescend deux fois plus vite dehors. */
    private static float avancer(float e, float n) {
        return n > 0 ? Math.min(120f, e + 0.4f + n) : Math.max(0f, e - 2f);
    }

    private static void effet(ServerPlayer p, MobEffect m, int secondes, int niveau) {
        if (m == null) return;
        p.addEffect(new MobEffectInstance(m, secondes * 20, niveau, true, false, true));
    }

    private static void effetsGaz(ServerPlayer p, float e) {
        if (e < 3) return;
        effet(p, MobEffects.CONFUSION, 8, 0);
        if (e >= 10) effet(p, MobEffects.POISON, 3, e >= 30 ? 1 : 0);
        if (e >= 25) effet(p, MobEffects.WEAKNESS, 4, 0);
        if (e >= 45) effet(p, MobEffects.WITHER, 3, e >= 80 ? 1 : 0);
    }

    private static final ResourceLocation IRRADIE = new ResourceLocation("geckolib", "irradiated");

    private static void effetsRadiation(ServerPlayer p, float e) {
        if (e < 5) return;
        effet(p, MobEffects.HUNGER, 4, e >= 30 ? 1 : 0);
        if (e >= 15) effet(p, MobEffects.WEAKNESS, 4, 0);
        MobEffect irr = ForgeRegistries.MOB_EFFECTS.getValue(IRRADIE);
        if (irr != null) effet(p, irr, 4, e >= 60 ? 1 : 0);
        if (e >= 20) p.hurt(p.damageSources().magic(), e >= 60 ? 2.0f : 1.0f);   // l'Armurerie réduit selon la radiation
    }

    private static void effetsFroid(ServerPlayer p, float e, boolean dedans) {
        if (e < 8 || !dedans) return;
        int voulu = Math.min(p.getTicksRequiredToFreeze() + 40, (int) (e * 4));
        if (p.getTicksFrozen() < voulu) p.setTicksFrozen(Math.min(voulu, p.getTicksFrozen() + 60));
        if (e >= 40) effet(p, MobEffects.MOVEMENT_SLOWDOWN, 3, 0);
    }

    // ------------------------------------------------------------------------------------------------ protection
    static boolean protege(ServerPlayer p, String[] ids) {
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.OFFHAND}) {
            if (correspond(p.getItemBySlot(s), ids)) return true;
        }
        for (ItemStack st : curios(p)) {
            if (correspond(st, ids)) return true;
        }
        return false;
    }

    private static boolean correspond(ItemStack st, String[] ids) {
        if (st == null || st.isEmpty()) return false;
        ResourceLocation k = ForgeRegistries.ITEMS.getKey(st.getItem());
        if (k == null) return false;
        String id = k.toString();
        for (String m : ids) {
            if (m.equals(id)) return true;
            if (m.contains("*")) {
                String re = m.replace(".", "\\\\.").replace("*", ".*");
                if (id.matches(re)) return true;
            }
        }
        return false;
    }

    /** Objets portés dans les emplacements Curios (par réflexion : Curios n'est pas une dépendance de compilation). */
    private static List<ItemStack> curios(ServerPlayer p) {
        List<ItemStack> out = new ArrayList<>();
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Object lazy = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class).invoke(null, p);
            Object h = lazy.getClass().getMethod("orElse", Object.class).invoke(lazy, (Object) null);
            if (h == null) return out;
            Object inv = h.getClass().getMethod("getEquippedCurios").invoke(h);
            int n = (int) inv.getClass().getMethod("getSlots").invoke(inv);
            var get = inv.getClass().getMethod("getStackInSlot", int.class);
            for (int i = 0; i < n; i++) out.add((ItemStack) get.invoke(inv, i));
        } catch (Throwable ignore) {
            // pas de Curios (ou autre version) : seules l'armure et la main gauche comptent
        }
        return out;
    }

    @SubscribeEvent
    public static void commandes(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("zones").requires(s -> s.hasPermission(2))
                .then(Commands.literal("info").executes(c -> {
                    // noms et genres des zones : ceux du fichier de la map (data/heraliz_zones.json), tels quels
                    StringBuilder b = new StringBuilder();
                    for (Zone z : zones) b.append("\n - ").append(z.nom()).append(" (").append(z.genre()).append(")");
                    Component t = Component.translatableWithFallback("heraliz_decors.zones.info", "Zones dangereuses : %s",
                            String.valueOf(zones.size())).append(b.toString());
                    c.getSource().sendSuccess(() -> t, false);
                    return 1;
                }))
                .then(Commands.literal("recharger").executes(c -> {
                    charger(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.translatableWithFallback("heraliz_decors.zones.rechargees", "Zones rechargées : %s",
                            String.valueOf(zones.size())), true);
                    return 1;
                })));
    }
}
