package fr.heralisz.bases.blindage.client;

import fr.heralisz.bases.HeraliszBases;
import fr.heralisz.bases.blindage.BlindageBlock;
import fr.heralisz.bases.blindage.Protection;
import fr.heralisz.bases.pillage.PillageNet;
import fr.heralisz.clans.client.gui.kit.Ui;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/**
 * Cote client du blindage : infobulle « BLINDAGE 4 · 2/4 » sous le reticule (clic droit accroupi, main vide, sur un bloc
 * protege ; paquet {@link PillageNet.InfoBlindageS2C}) en verre HeraliZ (kit des clans, police DM Sans), segments de
 * coups encaisses ; table des niveaux recue du serveur ({@link PillageNet.ProtectionS2C}) pour l'infobulle des objets
 * proteges (blocs cfm, portes HBM) et pour ne pas actionner le bloc (porte, coffre) pendant ce clic. Les textes sont
 * construits a la reception : rien n'est alloue par image.
 */
public final class BlindageClient {
    private BlindageClient() {
    }

    private static final long DUREE_MS = 2600, FONDU_MS = 400;

    private static long recuLe;
    private static int niveau, coups, couleur;
    private static Component titre = Component.empty(), compte = Component.empty(), nom = Component.empty();
    private static final Component[] LIGNE_NIVEAU = new Component[9];

    // ------------------------------------------------------------------ paquets
    public static void info(PillageNet.InfoBlindageS2C m) {
        niveau = Math.max(1, m.niveau());
        coups = Math.max(0, Math.min(niveau, m.coups()));
        couleur = BlindageBlock.couleur(niveau);
        titre = Ui.lit(Component.translatable("hud.heralisz_bases.blindage.titre", niveau).getString().toUpperCase(Locale.ROOT), Ui.BOLD);
        compte = Ui.lit(coups == 0 ? Component.translatable("hud.heralisz_bases.blindage.intact").getString().toUpperCase(Locale.ROOT)
                : coups + "/" + niveau, Ui.BOLD);
        ResourceLocation rl = ResourceLocation.tryParse(m.bloc());
        Block b = rl == null ? null : ForgeRegistries.BLOCKS.getValue(rl);
        String n = b == null ? m.bloc() : Component.translatable(b.getDescriptionId()).getString();
        nom = Ui.lit(Component.translatable("hud.heralisz_bases.blindage.detail", n, niveau).getString(), Ui.SMALL);
        recuLe = Util.getMillis();
    }

    public static void table(PillageNet.ProtectionS2C m) {
        Protection.installer(m.niveaux(), m.parties());
    }

    // ------------------------------------------------------------------ rendu
    private static void dessiner(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        if (recuLe == 0) return;
        long age = Util.getMillis() - recuLe;
        if (age > DUREE_MS) return;
        float a = Math.min(1f, age / 120f) * Math.min(1f, (DUREE_MS - age) / (float) FONDU_MS);
        int alpha = (int) (a * 255);
        if (alpha < 12) return;
        Font font = Minecraft.getInstance().font;
        int pw = 150, ph = 40;
        int x = (w - pw) / 2, y = h / 2 + 16;
        Ui.glass(g, x, y, pw, ph, (int) (Ui.VERRE_ALPHA * a));
        // liseré du niveau a gauche
        g.fill(x + 2, y + 3, x + 4, y + ph - 3, Ui.argb(couleur, alpha));
        g.drawString(font, titre, x + 10, y + 7, Ui.argb(couleur, alpha), false);
        int cw = font.width(compte);
        g.drawString(font, compte, x + pw - 10 - cw, y + 7, Ui.argb(coups == 0 ? Ui.OK : Ui.TEXT, alpha), false);
        g.drawString(font, nom, x + 10, y + 18, Ui.argb(Ui.MUTED, alpha), false);
        // segments : un par coup a encaisser, rouges pour les coups deja pris
        int sx = x + 10, sw = pw - 20, gap = 2;
        int seg = Math.max(2, (sw - gap * (niveau - 1)) / niveau);
        for (int i = 0; i < niveau; i++) {
            int x0 = sx + i * (seg + gap);
            int c = i < coups ? Ui.DANGER : couleur;
            g.fill(x0, y + ph - 9, x0 + seg, y + ph - 6, Ui.argb(c, i < coups ? alpha : (int) (alpha * 0.55f)));
        }
    }

    private static Component ligneNiveau(int n) {
        int i = Math.max(1, Math.min(8, n));
        if (LIGNE_NIVEAU[i] == null) {
            MutableComponent c = Component.translatable("tooltip.heralisz_bases.blindage.niveau", n);
            LIGNE_NIVEAU[i] = c.withStyle(s -> s.withColor(BlindageBlock.couleur(i)));
        }
        return LIGNE_NIVEAU[i];
    }

    /** Bus Forge (client). */
    @Mod.EventBusSubscriber(modid = HeraliszBases.MODID, value = Dist.CLIENT)
    public static final class Forge {
        private Forge() {
        }

        /** Infobulle d'un objet protege d'un autre mod (les blindage_1..8 ont la leur). */
        @SubscribeEvent
        public static void infobulle(ItemTooltipEvent e) {
            if (!(e.getItemStack().getItem() instanceof BlockItem bi) || bi.getBlock() instanceof BlindageBlock) return;
            int n = Protection.niveau(bi.getBlock());
            if (n > 0) e.getToolTip().add(ligneNiveau(n));
        }

        /** Clic droit accroupi main vide sur un bloc protege : rien n'est actionne, le serveur repond par l'infobulle. */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void clic(PlayerInteractEvent.RightClickBlock e) {
            if (!e.getLevel().isClientSide || !e.getEntity().isShiftKeyDown() || !e.getEntity().getMainHandItem().isEmpty()) return;
            BlockState s = e.getLevel().getBlockState(e.getPos());
            if (!(s.getBlock() instanceof BlindageBlock) && e.getLevel().dimension() != fr.heralisz.bases.monde.Grille.BUNKERS) return;
            if (Protection.niveau(s) <= 0 && !Protection.partie(s)) return;
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }

        @SubscribeEvent
        public static void deconnexion(ClientPlayerNetworkEvent.LoggingOut e) {
            recuLe = 0;
        }
    }

    /** Bus du mod (client) : couche HUD de l'infobulle, au-dessus du reticule. */
    @Mod.EventBusSubscriber(modid = HeraliszBases.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Mod_ {
        private Mod_() {
        }

        @SubscribeEvent
        public static void couches(RegisterGuiOverlaysEvent e) {
            e.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "blindage_info", BlindageClient::dessiner);
        }
    }
}
