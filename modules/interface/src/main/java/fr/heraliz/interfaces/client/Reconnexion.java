package fr.heraliz.interfaces.client;

import fr.heraliz.interfaces.HeralizInterface;
import fr.heraliz.interfaces.InterfaceConfig;
import fr.heraliz.interfaces.kit.Fond;
import fr.heraliz.interfaces.kit.HzButton;
import fr.heraliz.interfaces.kit.Logo;
import fr.heraliz.interfaces.kit.Ui;
import fr.heraliz.interfaces.mixin.EcranDeconnecteAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Reconnexion automatique après un redémarrage du serveur (24/10, « le jeu reconnecte tout seul les joueurs ») :
 * déconnecté avec le message du redémarrage ({@code heraliz.redemarrage.deconnexion}, envoyé par {@code /redemarrage}
 * du noyau), le joueur voit un écran d'attente au lieu de l'écran de déconnexion de Minecraft ; son jeu essaie de revenir
 * sur le même serveur 40 s après, puis toutes les 20 s, pendant 10 minutes (un échec pendant ce temps ramène à l'attente).
 * Bouton « Annuler » : écran titre.
 */
@Mod.EventBusSubscriber(modid = HeralizInterface.MODID, value = Dist.CLIENT)
public final class Reconnexion {
    private Reconnexion() {
    }

    static final String CLE = "heraliz.redemarrage.deconnexion";
    private static final long PREMIER_ESSAI = 40_000, ENTRE_ESSAIS = 20_000, DUREE = 600_000;

    private static String adresse, nom;
    /** Fin de l'attente (ms) ; 0 : pas de redémarrage en cours. */
    private static long jusqua;
    private static long prochainEssai;

    @SubscribeEvent
    public static void connexion(ClientPlayerNetworkEvent.LoggingIn e) {
        ServerData sd = Minecraft.getInstance().getCurrentServer();
        if (sd != null) {
            adresse = sd.ip;
            nom = sd.name;
        }
        jusqua = 0;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void ouverture(ScreenEvent.Opening e) {
        if (!(e.getNewScreen() instanceof DisconnectedScreen d)) return;
        long maintenant = System.currentTimeMillis();
        Component raison = ((EcranDeconnecteAccessor) d).heraliz$raison();
        boolean redemarrage = raison != null && raison.getContents() instanceof TranslatableContents tc && CLE.equals(tc.getKey());
        if (redemarrage) {
            jusqua = maintenant + DUREE;
            prochainEssai = maintenant + PREMIER_ESSAI;
        } else if (jusqua == 0 || maintenant > jusqua) {
            return;
        } else {
            // essai raté pendant le redémarrage (serveur pas encore revenu) : on attend encore
            prochainEssai = maintenant + ENTRE_ESSAIS;
        }
        e.setNewScreen(new Ecran());
    }

    private static String cible() {
        return adresse != null ? adresse : InterfaceConfig.texte(InterfaceConfig.SERVEUR_ADRESSE, "play.heraliz.fr").trim();
    }

    /** Écran d'attente, au style des écrans HeraliZ (verre sur fond flouté). */
    static final class Ecran extends Screen {
        private static final int PW = 300, PH = 120;

        Ecran() {
            super(Component.translatableWithFallback("gui.heraliz_interface.reconnexion.titre", "Redémarrage du serveur"));
        }

        @Override
        protected void init() {
            int px = (width - PW) / 2, py = (height - PH) / 2;
            addRenderableWidget(new HzButton(px + PW / 2 - 50, py + PH - 30, 100, 20,
                    Component.translatableWithFallback("gui.heraliz_interface.reconnexion.annuler", "Annuler"), Ui.accent(), () -> {
                jusqua = 0;
                minecraft.setScreen(new TitleScreen());
            }));
        }

        @Override
        public void tick() {
            long maintenant = System.currentTimeMillis();
            if (jusqua > 0 && maintenant <= jusqua && maintenant >= prochainEssai) {
                prochainEssai = maintenant + ENTRE_ESSAIS;
                String a = cible();
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(a),
                        new ServerData(nom != null ? nom : "HeraliZ", a, false), false);
            }
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            Fond.dessiner(g, width, height, 1f);
            int px = (width - PW) / 2, py = (height - PH) / 2;
            Ui.glass(g, px, py, PW, PH, Ui.VERRE_ALPHA);
            Ui.hairline(g, px + 24, px + PW - 24, py, Ui.accent(), 160, true, true);
            Logo.banniere(g, px + PW / 2 - 36, py + 10, 72, 1f);
            Ui.centered(g, font, Ui.in(title, Ui.BOLD), px + PW / 2f, py + 42, Ui.argb(Ui.TEXT, 255));
            long maintenant = System.currentTimeMillis();
            Component ligne;
            if (jusqua == 0 || maintenant > jusqua) {
                ligne = Component.translatableWithFallback("gui.heraliz_interface.reconnexion.abandon",
                        "Le serveur ne répond toujours pas : reviens par le bouton Jouer un peu plus tard.");
            } else {
                long s = Math.max(0, (prochainEssai - maintenant + 999) / 1000);
                ligne = Component.translatableWithFallback("gui.heraliz_interface.reconnexion.attente",
                        "Reconnexion automatique dans %s s…", String.valueOf(s));
            }
            // message coupe en lignes centrees dans le panneau (3 au plus, au-dessus du bouton)
            List<FormattedCharSequence> lignes = font.split(Ui.in(ligne, Ui.SMALL), PW - 32);
            for (int i = 0; i < Math.min(3, lignes.size()); i++) {
                FormattedCharSequence l = lignes.get(i);
                g.drawString(font, l, Math.round(px + PW / 2f - font.width(l) / 2f), py + 58 + 9 * i, Ui.argb(Ui.MUTED, 255), false);
            }
            super.render(g, mx, my, pt);
        }
    }
}
