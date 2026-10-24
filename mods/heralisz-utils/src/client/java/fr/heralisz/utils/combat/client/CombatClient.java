package fr.heralisz.utils.combat.client;

import fr.heralisz.utils.client.kit.Ui;
import fr.heralisz.utils.combat.CombatReseau;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Tag de combat chez le joueur (déplacé de HeraliZ Matchs le 24/10, matchs retirés) : secondes reçues du serveur
 * ({@link CombatReseau.Indicateur}), décomptées seul ; indicateur discret « EN COMBAT 12 s » en bas à droite, au-dessus
 * du compteur de munitions de TaCZ (point rouge qui pulse, capitales espacées, secondes en gras, jauge fine qui se vide ;
 * fondu à l'apparition et dans la dernière demi-seconde). Aucun message dans le chat.
 * <p>Lu par réflexion par d'autres mods (API stable, à ne jamais renommer) : {@link #combatRestantMs()} (HeraliZ Emote).
 */
public final class CombatClient {
    private CombatClient() {
    }

    private static int combatS;
    private static long combatRecuMs;
    /** Durée pleine du tag en cours (s), pour la jauge. */
    private static int combatTotalS;

    // dessin
    private static int montre = -1;
    private static MutableComponent valeur = Component.empty();
    /** Largeur des secondes affichées et largeur de référence « 00 s » (la pastille ne change pas de taille). */
    private static int largeurValeur, largeurRef;
    private static final TexteEspace TITRE = new TexteEspace(Ui.SMALL, 1.0f);
    private static long debut;

    public static void init(IEventBus modBus) {
        CombatReseau.RECEPTION_CLIENT.brancher(m -> recevoir(m.secondes()));
        modBus.addListener((RegisterGuiOverlaysEvent e) -> e.registerAboveAll("combat", CombatClient::dessiner));
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            combatS = 0;
            combatTotalS = 0;
        });
    }

    static void recevoir(int secondes) {
        boolean actif = combatRestantMs() > 0;
        if (secondes <= 0) combatTotalS = 0;
        else combatTotalS = actif ? Math.max(combatTotalS, secondes) : secondes;
        combatS = Math.max(0, secondes);
        combatRecuMs = Util.getMillis();
    }

    /** Millisecondes restantes du tag de combat (0 = aucun), décomptées localement. */
    public static long combatRestantMs() {
        if (combatS <= 0) return 0;
        return Math.max(0, combatS * 1000L - (Util.getMillis() - combatRecuMs));
    }

    /** Part restante du tag (jauge). */
    public static float combatRatio() {
        if (combatTotalS <= 0) return 0;
        return Math.min(1f, combatRestantMs() / (combatTotalS * 1000f));
    }

    private static void dessiner(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        long reste = combatRestantMs();
        if (mc.options.hideGui || mc.player == null || reste <= 0) {
            montre = -1;
            return;
        }
        Font font = mc.font;
        long now = Util.getMillis();
        int s = (int) ((reste + 999) / 1000);
        if (s != montre) {
            if (montre < 0) {
                debut = now;
                TITRE.texte(Component.translatable("heralisz_utils.combat.titre").getString());
                largeurRef = font.width(Ui.lit("00 s", Ui.BOLD));
            }
            montre = s;
            valeur = Ui.lit(s + " s", Ui.BOLD);
            largeurValeur = font.width(valeur);
        }
        int cw = 17 + (int) Math.ceil(TITRE.largeur()) + 10 + Math.max(largeurRef, largeurValeur) + 8, ch = 19;
        int x = w - 8 - cw, y = h - 50 - ch;
        float a = Math.min(Math.min(1f, (now - debut) / 180f), Math.min(1f, reste / 450f));
        int a255 = Math.max(4, (int) (255 * a));
        Ui.glass(g, x, y, cw, ch, (int) (180 * a));
        float pulse = 0.6f + 0.4f * (float) Math.cos((now % 1000) / 1000.0 * Math.PI * 2);
        Ui.soft(g, x + 7, y + 6, 5, 5, Ui.argb(Ui.DANGER, (int) (a255 * pulse)));
        TITRE.dessiner(g, x + 17, y + 5.5f, Ui.argb(Ui.mix(Ui.DANGER, 0xFFFFFF, 0.5f), a255));
        g.drawString(font, valeur, x + cw - 8 - largeurValeur, y + 5, Ui.argb(Ui.TEXT, a255), false);
        if (a > 0.5f) Ui.bar(g, x + 6, y + ch - 4, cw - 12, 1, combatRatio(), Ui.DANGER);
    }
}
