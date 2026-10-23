package fr.heraliz.grades.serveur;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Kit de départ (Grades 2.4.1, 23/10, choix du propriétaire) : donné UNE seule fois à chaque nouveau joueur, à son
 * arrivée au spawn après la cinématique (marque {@code heraliz_cinematique_finie} d'Utils) ; marque à nous
 * {@value #MARQUE} dans les données persistantes (effacée par /wipe de HeraliZ Quêtes). Contenu :
 * {@code config/heraliz_grades/kit_depart.json}, relu au démarrage. Inventaire plein : le reste tombe à ses pieds.
 * Pas en créatif ni en spectateur. Fil serveur.
 */
final class KitDepart {
    private static final Logger LOG = LogUtils.getLogger();
    static final String MARQUE = "heraliz_kit_depart";
    private static final String CINE = "heraliz_cinematique_finie";
    private static final ResourceLocation SPAWN = new ResourceLocation("heralisz", "spawn");
    private static List<ItemStack> objets = List.of();
    private static boolean actif;
    private static int ticks;

    private KitDepart() {
    }

    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(KitDepart::tick);
    }

    static void charger() {
        Path f = FMLPaths.CONFIGDIR.get().resolve("heraliz_grades").resolve("kit_depart.json");
        List<ItemStack> l = new ArrayList<>();
        actif = false;
        try {
            if (Files.isRegularFile(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                actif = !o.has("actif") || o.get("actif").getAsBoolean();
                for (JsonElement e : o.getAsJsonArray("objets")) {
                    JsonObject ob = e.getAsJsonObject();
                    Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(ob.get("item").getAsString()));
                    if (it == null || it == Items.AIR) {
                        LOG.warn("[grades] kit de départ : objet inconnu {}", ob.get("item").getAsString());
                        continue;
                    }
                    int n = ob.has("nombre") ? Math.max(1, Math.min(64, ob.get("nombre").getAsInt())) : 1;
                    ItemStack s = new ItemStack(it, n);
                    if (ob.has("nbt")) s.setTag(TagParser.parseTag(ob.get("nbt").getAsString()));
                    l.add(s);
                }
            }
        } catch (Exception ex) {
            LOG.error("[grades] kit_depart.json illisible : kit de départ coupé", ex);
            actif = false;
        }
        objets = List.copyOf(l);
    }

    private static CompoundTag donnees(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++ticks % 20 != 0 || !actif || objets.isEmpty()) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            if (!p.level().dimension().location().equals(SPAWN)) continue;
            CompoundTag d = donnees(p);
            if (!d.getBoolean(CINE) || d.getBoolean(MARQUE)) continue;
            d.putBoolean(MARQUE, true);
            for (ItemStack s : objets) {
                ItemStack c = s.copy();
                if (!p.getInventory().add(c) && !c.isEmpty()) p.drop(c, false);
            }
            p.inventoryMenu.broadcastChanges();
            p.sendSystemMessage(Component.translatableWithFallback("heraliz_grades.kit_depart.recu",
                    "Tu as reçu ton kit de départ : tenue, pistolet, munitions, batte et de quoi manger."));
        }
    }
}
