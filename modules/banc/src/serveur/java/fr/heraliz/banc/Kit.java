package fr.heraliz.banc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import fr.heraliz.banc.HeralizBanc;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Équipement d'un bot, comme un joueur qui a fouillé : une arme TaCZ tirée au hasard parmi celles du loot (raretés
 * Commun à Épique de {@code config/heraliz_loot/raretes.json}), une boîte de munitions « créative » de TaCZ (rechargement
 * normal, munitions sans fin : un essai de charge ne doit pas s'arrêter faute de balles), des grenades, flashs,
 * fumigènes et soins du mod tactique. Les cases suivent {@link Cerveau}.
 */
final class Kit {
    private Kit() {
    }

    private static final Set<String> RARETES = Set.of("commun", "peu_commun", "rare", "epique");
    /** Secours si raretes.json est illisible : armes des gunpacks du dépôt. */
    private static final List<String> SECOURS = List.of("arkaniaz:aks74u", "arkaniaz:ump9", "arkaniaz:9a91", "arkaniaz:usp",
            "maxstuff:ak74", "arkaniaz:pp19", "arkaniaz:sr_2m");
    private static List<ResourceLocation> armes;

    static void donner(BotJoueur b, RandomSource r) {
        Inventory inv = b.getInventory();
        inv.clearContent();
        inv.setItem(Cerveau.CASE_ARME, arme(r));
        inv.setItem(Cerveau.CASE_GRENADE, lancer("heralisz_grenade:m67", 3));
        inv.setItem(Cerveau.CASE_FLASH, lancer("heralisz_grenade:flash_grenade", 2));
        inv.setItem(Cerveau.CASE_FUMI, lancer("heralisz_grenade:smoke_grenade", 2));
        inv.setItem(Cerveau.CASE_SOIN, soin(r.nextBoolean() ? "heralisz_heal:carfak" : "heralisz_heal:ai2", 3));
        ItemStack boite = objet("tacz:ammo_box", 1);
        if (!boite.isEmpty()) {
            boite.getOrCreateTag().putBoolean("AllTypeCreative", true);
            boite.getOrCreateTag().putInt("Level", 2);
            inv.setItem(8, boite);
        }
        inv.selected = Cerveau.CASE_ARME;
    }

    private static ItemStack arme(RandomSource r) {
        List<ResourceLocation> l = armes();
        for (int i = 0; i < 8 && !l.isEmpty(); i++) {
            ItemStack st = GunItemBuilder.create().setId(l.get(r.nextInt(l.size()))).setAmmoCount(30).setAmmoInBarrel(true).build();
            if (!st.isEmpty()) return st;
        }
        return ItemStack.EMPTY;
    }

    private static List<ResourceLocation> armes() {
        if (armes != null) return armes;
        List<ResourceLocation> l = new ArrayList<>();
        Path f = FMLPaths.CONFIGDIR.get().resolve("heraliz_loot/raretes.json");
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("armes");
            for (var e : o.entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(e.getKey());
                if (id != null && e.getValue().isJsonPrimitive() && RARETES.contains(e.getValue().getAsString())) l.add(id);
            }
        } catch (Exception ex) {
            HeralizBanc.LOG.warn("[bots] {} illisible : armes de secours", f, ex);
        }
        if (l.isEmpty()) SECOURS.forEach(s -> l.add(new ResourceLocation(s)));
        armes = l;
        return l;
    }

    private static ItemStack lancer(String id, int n) {
        ItemStack st = objet("heralisz_tactical:throwable", n);
        if (!st.isEmpty()) st.getOrCreateTag().putString("ThrowableId", id);
        return st;
    }

    private static ItemStack soin(String id, int n) {
        ItemStack st = objet("heralisz_tactical:consumable", n);
        if (!st.isEmpty()) st.getOrCreateTag().putString("ConsumableId", id);
        return st;
    }

    private static ItemStack objet(String id, int n) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (it == null || it == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        ItemStack st = new ItemStack(it, n);
        st.setTag(st.hasTag() ? st.getTag() : new CompoundTag());
        return st;
    }
}
