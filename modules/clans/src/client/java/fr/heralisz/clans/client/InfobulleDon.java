package fr.heralisz.clans.client;

import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.client.gui.kit.Ui;
import fr.heralisz.clans.network.Packets;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Infobulle des objets que {@code /clan don} accepte (03/11, retour du propriétaire : « afficher sur les objets acceptés
 * "Se donne au clan pour de l'XP" ») : une ligne « Se donne au clan : +1 000 XP (/clan don, au spawn) » sur les objets de
 * valeur à revendre (étiquette {@code heraliz_composants:vente}) qui rapportent de l'XP. Mêmes règles que le serveur
 * ({@code SaisonCommandes.don}, {@code XpClan.donXp}) : XP de l'objet s'il est cité à part, sinon celle de sa rareté
 * (HeraliZ Loot, lue par réflexion) ; le barème vient du serveur ({@link Packets.DonsS2C}). Le serveur revérifie tout au
 * don (copie, spawn, plafond du jour) : la ligne n'est qu'une aide.
 */
@Mod.EventBusSubscriber(modid = HeraliszClans.MODID, value = Dist.CLIENT)
public final class InfobulleDon {
    private InfobulleDon() {
    }

    private static final TagKey<Item> VENTE = TagKey.create(Registries.ITEM, new ResourceLocation("heraliz_composants", "vente"));
    private static final Style STYLE = Style.EMPTY.withColor(TextColor.fromRgb(Ui.OR));

    private static long[] parRarete = new long[0];
    private static Map<String, Long> objets = Map.of();
    private static Method rarete;
    private static boolean rareteCherchee;

    static void recevoir(Packets.DonsS2C p) {
        parRarete = p.parRarete().clone();
        objets = Map.copyOf(p.objets());
    }

    static void vider() {
        parRarete = new long[0];
        objets = Map.of();
    }

    /** Rareté 0..5 de l'objet (HeraliZ Loot), -1 si inconnue. */
    private static int rarete(ItemStack st) {
        if (!rareteCherchee) {
            rareteCherchee = true;
            try {
                rarete = Class.forName("fr.heraliz.loot.RaretesObjets").getMethod("de", ItemStack.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                rarete = null;
            }
        }
        if (rarete == null) return -1;
        try {
            return rarete.invoke(null, st) instanceof Enum<?> en ? en.ordinal() : -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent e) {
        ItemStack st = e.getItemStack();
        if (st.isEmpty() || parRarete.length == 0 || !st.is(VENTE)) return;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
        Long cite = id == null ? null : objets.get(id.toString());
        long xp;
        if (cite != null) {
            xp = cite;
        } else {
            int r = rarete(st);
            xp = r < 0 || r >= parRarete.length ? 0 : parRarete[r];
        }
        if (xp <= 0) return;
        e.getToolTip().add(Component.translatable(st.getCount() > 1 ? "infobulle.heralisz_clans.don_chacun" : "infobulle.heralisz_clans.don",
                Ui.nombre(xp)).setStyle(STYLE));
    }
}
