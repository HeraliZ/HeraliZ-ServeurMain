package fr.heraliz.casino.client.zone;

import fr.heraliz.casino.client.render.scene.UiAtlas;
import fr.heraliz.casino.client.screen.ui.UiButton;
import fr.heraliz.casino.client.screen.ui.UiKit;
import fr.heraliz.casino.economy.Chips;
import fr.heraliz.casino.entity.GuardEntity;
import fr.heraliz.casino.net.CasinoNet;
import fr.heraliz.casino.net.ZoneDialogPacket;
import fr.heraliz.casino.net.ZonePayPacket;
import fr.heraliz.casino.registry.ModRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Le videur parle au joueur qu'il vient d'arreter : sa replique s'ecrit lettre par lettre dans un bandeau en bas de
 * l'ecran (portrait, prix de l'entree, ce qui sera pris au joueur), avec deux boutons : payer, ou partir. La salle
 * reste visible : le regard du joueur est tourne vers le videur (voir {@link ZoneClient}).
 */
public class EntranceScreen extends Screen {
    private static final int INK2 = 0xC3C2B7, AMBER = 0xFFB040;
    /** Lettres ecrites par tick. */
    private static final float SPEED = 1.6f;

    private final ZoneDialogPacket data;
    private final String line;
    /** Videur du portrait : une entite a part (jamais ajoutee au monde), bras croises. */
    @Nullable
    private GuardEntity portrait;
    private int age;
    private int px, py, pw, ph;
    private UiButton pay;

    public EntranceScreen(ZoneDialogPacket data) {
        super(Component.translatable("screen.heralizcasino.entry.title"));
        this.data = data;
        this.line = data.line().getString();
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 336);
        ph = 86;
        px = (width - pw) / 2;
        py = height - ph - 12;
        if (portrait == null && minecraft != null && minecraft.level != null) {
            try {
                portrait = new GuardEntity(ModRegistry.GUARD.get(), minecraft.level);
            } catch (RuntimeException e) {
                portrait = null;
            }
        }
        int payW = Math.min(146, pw / 2), leaveW = 64, by = py + ph - 24;
        Component label = Component.translatable("screen.heralizcasino.entry.pay", data.payWith());
        pay = new UiButton(payW, 18, label, UiAtlas.GOLD, b -> pay()).scale(0.9f);
        pay.active = data.canPay();
        pay.setX(px + pw - 8 - payW);
        pay.setY(by);
        addRenderableWidget(pay);
        UiButton leave = new UiButton(leaveW, 18, Component.translatable("screen.heralizcasino.entry.leave"), UiAtlas.DARK, b -> onClose()).scale(0.9f);
        leave.setX(px + pw - 8 - payW - 4 - leaveW);
        leave.setY(by);
        addRenderableWidget(leave);
    }

    /** Le joueur paie : le serveur verifie tout, le videur repond dans sa bulle. */
    public void pay() {
        if (!data.canPay()) return;
        CasinoNet.CHANNEL.sendToServer(new ZonePayPacket());
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        onClose();
    }

    public boolean canPay() {
        return data.canPay();
    }

    /** La replique est entierement ecrite. */
    private boolean written() {
        return age * SPEED >= line.length();
    }

    @Override
    public void tick() {
        age++;
        if (portrait != null) portrait.tickCount++;
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        // pas de fond : on voit le videur
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        UiKit.panel(g, px, py, pw, ph);
        // ---- portrait
        int bx = px + 8, by = py + 8, bs = 46;
        g.fill(bx - 1, by - 1, bx + bs + 1, by + bs + 1, 0xFF000000 | 0x6A4A20);
        g.fillGradient(bx, by, bx + bs, by + bs, 0xFF2A2420, 0xFF14100C);
        if (portrait != null) {
            g.enableScissor(bx, by, bx + bs, by + bs);
            // buste : les pieds du videur sont loin sous le cadre
            // gros plan : tete et epaules (les pieds sont loin sous le cadre)
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, bx + bs / 2, by + 8 + Math.round(GuardRenderer.HEIGHT * 54), 54, 0, 0, portrait);
            g.disableScissor();
        }
        // ---- replique, ecrite lettre par lettre
        int tx = bx + bs + 8, tw = px + pw - 10 - tx;
        UiKit.text(g, Component.translatable("screen.heralizcasino.entry.name").withStyle(s -> s.withBold(true)), tx, py + 8, AMBER, 0.85f, -1, true);
        int n = Math.min(line.length(), Math.round((age + partial) * SPEED));
        List<FormattedCharSequence> lines = font.split(Component.literal(line.substring(0, n)), tw);
        for (int i = 0; i < Math.min(2, lines.size()); i++) g.drawString(font, lines.get(i), tx, py + 20 + i * 10, 0xFFFFFFFF, true);
        // ---- prix, ticket, ce qui sera pris
        // le ticket ne vaut que pour une entree : qui ressort repaie
        Component ticket = Component.translatable("screen.heralizcasino.entry.ticket_once");
        UiKit.fitted(g, Component.translatable("screen.heralizcasino.entry.price", Chips.format(data.price()), ticket), tx, py + 43, tw, UiKit.GOLD, 0.8f, -1, false);
        Component sub;
        int col = INK2;
        if (!data.canPay()) {
            sub = Component.translatable("screen.heralizcasino.entry.cant_pay");
            col = UiKit.RED;
        } else if (data.change() > 0) {
            sub = Component.translatable("screen.heralizcasino.entry.change", data.payWith(), Chips.format(data.change()));
        } else {
            sub = Component.translatable("screen.heralizcasino.entry.hint");
        }
        UiKit.fitted(g, sub, tx, py + 53, tw, col, 0.7f, -1, false);
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            // premiere pression : la replique s'ecrit d'un coup ; ensuite : payer
            if (!written()) age = (int) Math.ceil(line.length() / SPEED) + 1;
            else pay();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
