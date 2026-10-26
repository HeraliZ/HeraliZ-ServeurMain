package fr.heraliz.loot.client;

import fr.heraliz.loot.HeralizLoot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Filtres de l'onglet créatif HeraliZ Loot : une colonne de cases à gauche de l'inventaire créatif (Tout, T1 à T7,
 * Nourriture, Soins, Munitions) ; un clic n'affiche plus que les conteneurs de ce grade. Visible seulement quand
 * l'onglet HeraliZ Loot est ouvert. Le grade se lit dans le nom du bloc (t1_..., soins2_...).
 */
@Mod.EventBusSubscriber(modid = HeralizLoot.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FiltresOnglet {
    private FiltresOnglet() {
    }

    /** {préfixe du nom (vide = tout), libellé, info-bulle, couleur} ; textes dans heraliz_loot.filtre.* (T1 à T7 : tels quels). */
    private static final Object[][] FILTRES = {
            {"", texte("tout"), texte("tout.info"), 0xFFB8BCC4},
            {"t1_", Component.literal("T1"), texte("t1.info"), 0xFF9AA0A8},
            {"t2_", Component.literal("T2"), texte("t2.info"), 0xFF6FAE5A},
            {"t3_", Component.literal("T3"), texte("t3.info"), 0xFF4C8FD6},
            {"t4_", Component.literal("T4"), texte("t4.info"), 0xFF9A6CD0},
            {"t5_", Component.literal("T5"), texte("t5.info"), 0xFFE08A2E},
            {"t6_", Component.literal("T6"), texte("t6.info"), 0xFFD0453A},
            {"t7_", Component.literal("T7"), texte("t7.info"), 0xFFE6C24A},
            {"nourriture", texte("vivres"), texte("vivres.info"), 0xFFD9D2C0},
            {"soins", texte("soins"), texte("soins.info"), 0xFFE07FA8},
            {"munitions", texte("munitions"), texte("munitions.info"), 0xFFA9825A},
            {"vehicules", texte("vehicules"), texte("vehicules.info"), 0xFF7FA3B8},
    };
    private static final int LARGEUR = 40, HAUTEUR = 14, PAS = 16;

    private static Component texte(String cle) {
        return Component.translatable("heraliz_loot.filtre." + cle);
    }

    private static String filtre = "";
    private static CreativeModeTab dernier;
    private static final List<Case> CASES = new ArrayList<>();

    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post e) {
        if (!(e.getScreen() instanceof CreativeModeInventoryScreen s)) return;
        CASES.clear();
        int x = s.getGuiLeft() - LARGEUR - 6, y = s.getGuiTop() + 2;
        for (int i = 0; i < FILTRES.length; i++) {
            Case c = new Case(x, y + i * PAS, (String) FILTRES[i][0], (Component) FILTRES[i][1], (int) FILTRES[i][3]);
            c.setTooltip(Tooltip.create((Component) FILTRES[i][2]));
            CASES.add(c);
            e.addListener(c);
        }
        dernier = null;
        majVisibilite(s);
    }

    @SubscribeEvent
    public static void rendu(ScreenEvent.Render.Pre e) {
        if (!(e.getScreen() instanceof CreativeModeInventoryScreen s)) return;
        CreativeModeTab t = onglet();
        if (t != dernier) {
            dernier = t;
            majVisibilite(s);
        }
    }

    private static CreativeModeTab onglet() {
        return ObfuscationReflectionHelper.getPrivateValue(CreativeModeInventoryScreen.class, null, "f_98507_");
    }

    private static boolean notreOnglet() {
        return onglet() == HeralizLoot.ONGLET.get();
    }

    private static void majVisibilite(CreativeModeInventoryScreen s) {
        boolean v = notreOnglet();
        for (Case c : CASES) c.visible = v;
        if (v) appliquer(s);
    }

    private static void appliquer(CreativeModeInventoryScreen s) {
        CreativeModeInventoryScreen.ItemPickerMenu menu = s.getMenu();
        menu.items.clear();
        for (RegistryObject<Item> o : HeralizLoot.objetsOnglet()) {
            Item it = o.get();
            String nom = ForgeRegistries.ITEMS.getKey(it).getPath();
            if (filtre.isEmpty() || nom.startsWith(filtre)) menu.items.add(new ItemStack(it));
        }
        ObfuscationReflectionHelper.setPrivateValue(CreativeModeInventoryScreen.class, s, 0f, "f_98508_");
        menu.scrollTo(0f);
    }

    /** Case de filtre : fond sombre, liseré de la couleur du grade, pleine quand elle est choisie. */
    private static final class Case extends AbstractButton {
        private final String prefixe;
        private final int couleur;

        Case(int x, int y, String prefixe, Component libelle, int couleur) {
            super(x, y, LARGEUR, HAUTEUR, libelle);
            this.prefixe = prefixe;
            this.couleur = couleur;
        }

        @Override
        public void onPress() {
            filtre = prefixe;
            if (Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen s) appliquer(s);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean choisi = filtre.equals(prefixe);
            int x0 = getX(), y0 = getY(), x1 = x0 + width, y1 = y0 + height;
            g.fill(x0, y0, x1, y1, choisi ? (couleur & 0x00FFFFFF) | 0xE0000000 : 0xC0141519);
            int bord = isHoveredOrFocused() || choisi ? couleur : (couleur & 0x00FFFFFF) | 0x70000000;
            g.fill(x0, y0, x1, y0 + 1, bord);
            g.fill(x0, y1 - 1, x1, y1, bord);
            g.fill(x0, y0, x0 + 1, y1, bord);
            g.fill(x1 - 1, y0, x1, y1, bord);
            int texte = choisi ? 0xFF101012 : 0xFFE8E8EA;
            var f = Minecraft.getInstance().font;
            g.drawString(f, getMessage(), x0 + (width - f.width(getMessage())) / 2, y0 + (height - 8) / 2 + 1, texte, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput o) {
            defaultButtonNarrationText(o);
        }
    }
}
