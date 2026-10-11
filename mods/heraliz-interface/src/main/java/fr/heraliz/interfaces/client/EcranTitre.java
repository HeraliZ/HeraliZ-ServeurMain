package fr.heraliz.interfaces.client;

import fr.heraliz.interfaces.HeralizInterface;
import fr.heraliz.interfaces.InterfaceConfig;
import fr.heraliz.interfaces.kit.Anim;
import fr.heraliz.interfaces.kit.Echelle;
import fr.heraliz.interfaces.kit.Fond;
import fr.heraliz.interfaces.kit.Icons;
import fr.heraliz.interfaces.kit.Logo;
import fr.heraliz.interfaces.kit.MenuLigne;
import fr.heraliz.interfaces.kit.Ui;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ecran titre HeraliZ (remplace celui de Minecraft, voir {@link InterfaceClient}), d'apres la maquette validee : la
 * carte floutee en fond (lent zoom), logo et accroche au centre, panneau de verre avec JOUER (connexion directe au
 * serveur ; a droite, son etat : joueurs en ligne et ping), OPTIONS et QUITTER LE JEU. En bas, la version a gauche et le
 * choix de langue (drapeaux) a droite. Ni solo, ni liste de serveurs, ni Realms : HeraliZ seulement. Une exception au
 * rendu rend la main a l'ecran titre de Minecraft pour la session.
 */
public class EcranTitre extends Screen {
    private static final ResourceLocation FOND = HeralizInterface.id("textures/gui/fond_titre.png");
    private static final int FOND_L = 1600, FOND_H = 1058;
    /** Sans reponse du serveur apres ce delai : « injoignable ». */
    private static final long ATTENTE_MS = 8000, NOUVEAU_PING_MS = 30000;

    private long ouvert = -1;
    private final List<MenuLigne> lignes = new ArrayList<>();
    private int logoX, logoY, logoW, logoH, sousY, panneauX, panneauY, panneauW, panneauH, sepY;
    @Nullable
    private String sousTitre;
    @Nullable
    private MenuLigne jouer;
    @Nullable
    private Langues.Selecteur langue;
    // etat du serveur : meme ping que la liste des serveurs de Minecraft
    private final ServerStatusPinger pinger = new ServerStatusPinger();
    @Nullable
    private ServerData etat;
    private long pingLance;
    private volatile boolean pingRate;
    private int tick;

    public EcranTitre() {
        super(Component.literal("HeraliZ"));
    }

    private static String t(String cle, Object... args) {
        return Component.translatable("gui.heraliz_interface." + cle, args).getString();
    }

    private static String adresse() {
        return InterfaceConfig.texte(InterfaceConfig.SERVEUR_ADRESSE, "play.heraliz.fr").trim();
    }

    private static String nom() {
        String n = InterfaceConfig.texte(InterfaceConfig.SERVEUR_NOM, "HeraliZ").trim();
        return n.isEmpty() ? "HeraliZ" : n;
    }

    // ------------------------------------------------------------------ construction
    @Override
    protected void init() {
        if (ouvert < 0) ouvert = Util.getMillis();
        if (InterfaceConfig.oui(InterfaceConfig.ECHELLE_AUTO, true)) Echelle.forcer(this);
        lignes.clear();
        Minecraft mc = minecraft;
        int accent = Ui.accent();
        boolean compact = height < 330;
        int lh = compact ? 26 : 30, oh = compact ? 20 : 24, qh = compact ? 18 : 20, groupe = compact ? 8 : 10, pad = 8;
        panneauW = Math.min(224, width - 32);
        int iw = panneauW - 2 * pad;
        panneauH = pad + lh + 4 + oh + groupe + qh + pad;
        String st = InterfaceConfig.texte(InterfaceConfig.SOUS_TITRE, "");
        sousTitre = st.equals("-") ? null : st.isEmpty() ? t("pause.sous_titre") : st;
        int sousH = sousTitre != null ? 12 : 0, piedH = 24;
        int dispo = height - piedH - 16 - panneauH - (sousTitre != null ? sousH + 12 : 0) - 10;
        logoW = Math.min(214, Math.round(width * 0.36f));
        logoH = Math.round(Logo.hauteurBanniere(logoW));
        if (logoH > dispo) {
            logoH = dispo >= 24 ? dispo : 0;
            logoW = logoH > 0 ? Math.round(logoH / Logo.RATIO_BANNIERE) : 0;
        }
        int bloc = (logoH > 0 ? logoH + 10 : 0) + (sousTitre != null ? sousH + 12 : 0) + panneauH;
        int y = Math.max(8, (height - piedH - bloc) / 2);
        logoX = (width - logoW) / 2;
        logoY = y;
        if (logoH > 0) y += logoH + 10;
        sousY = y;
        if (sousTitre != null) y += sousH + 12;
        panneauX = (width - panneauW) / 2;
        panneauY = y;

        // ---- panneau : Jouer, Options, Quitter le jeu
        int x = panneauX + pad, ly = panneauY + pad;
        jouer = new MenuLigne(x, ly, iw, lh, Icons.REPRENDRE, Component.translatable("gui.heraliz_interface.titre.jouer"), accent,
                this::rejoindre).style(MenuLigne.Style.PRINCIPAL);
        lignes.add(jouer);
        ly += lh + 4;
        lignes.add(new MenuLigne(x, ly, iw, oh, Icons.OPTIONS, Component.translatable("gui.heraliz_interface.titre.options"), accent,
                () -> mc.setScreen(new OptionsScreen(this, mc.options))).iconeDroite(Icons.CHEVRON).sobre());
        ly += oh;
        sepY = ly + groupe / 2;
        ly += groupe;
        lignes.add(new MenuLigne(x, ly, iw, qh, Icons.QUITTER, Component.translatable("gui.heraliz_interface.titre.quitter"), accent,
                mc::stop).style(MenuLigne.Style.DANGER).sobre());
        for (MenuLigne l : lignes) addRenderableWidget(l);

        // ---- choix de langue, en bas a droite
        int dw = 14, lw = Langues.Selecteur.largeurDrapeaux(dw);
        langue = addRenderableWidget(new Langues.Selecteur(width - 12 - lw, height - 19, lw, 14, dw, null, accent));

        if (etat == null) {
            etat = new ServerData(nom(), adresse(), false);
            pinger();
        }
        majEtat();
    }

    /** Rejoint le serveur (comme un clic sur lui dans la liste des serveurs de Minecraft). */
    private void rejoindre() {
        Minecraft mc = minecraft;
        String a = adresse();
        ConnectScreen.startConnecting(this, mc, ServerAddress.parseString(a), new ServerData(nom(), a, false), false);
    }

    /** Ping du serveur en arriere-plan : joueurs en ligne et latence, affiches a droite de JOUER. */
    private void pinger() {
        ServerData sd = etat;
        if (sd == null) return;
        sd.ping = -1L;
        pingRate = false;
        pingLance = Util.getMillis();
        Util.backgroundExecutor().execute(() -> {
            try {
                pinger.pingServer(sd, () -> {
                });
            } catch (Exception e) {
                pingRate = true;
            }
        });
    }

    private void majEtat() {
        ServerData sd = etat;
        if (jouer == null || sd == null) return;
        String texte;
        if (sd.ping >= 0) {
            String joueurs = sd.status == null ? "" : sd.status.getString();
            int barre = joueurs.indexOf('/');
            if (barre > 0) joueurs = joueurs.substring(0, barre).trim();
            texte = joueurs.isEmpty() || !joueurs.chars().allMatch(Character::isDigit)
                    ? t("titre.ping", sd.ping) : t("titre.en_ligne", joueurs, sd.ping);
        } else if (!pingRate && Util.getMillis() - pingLance < ATTENTE_MS) {
            texte = t("titre.recherche");
        } else {
            texte = t("titre.injoignable");
        }
        jouer.indiceTexte(texte);
    }

    @Override
    public void tick() {
        pinger.tick();
        if (++tick % 10 == 0) majEtat();
        if (etat != null && Util.getMillis() - pingLance > NOUVEAU_PING_MS) pinger();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // ------------------------------------------------------------------ rendu
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        try {
            dessiner(g, mouseX, mouseY, partialTick);
        } catch (Throwable ex) {
            InterfaceClient.titreEnPanne(ex);
        }
    }

    private void dessiner(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int accent = Ui.accent();
        // fond : la carte en plein ecran, avec un zoom tres lent, puis flou, voile et vignette du kit
        float z = (float) (1.04 + 0.03 * Math.sin((Util.getMillis() - ouvert) / 9000.0));
        float k = Math.max(width / (float) FOND_L, height / (float) FOND_H) * z;
        g.pose().pushPose();
        g.pose().translate((width - FOND_L * k) / 2f, (height - FOND_H * k) / 2f, 0);
        g.pose().scale(k, k, 1f);
        g.blit(FOND, 0, 0, 0f, 0f, FOND_L, FOND_H, FOND_L, FOND_H);
        g.pose().popPose();
        Fond.dessiner(g, width, height, 1f);

        // logo et accroche
        float tl = Anim.sortie(Anim.progression(ouvert, 40, 420));
        if (logoH > 0) {
            float cx = logoX + logoW / 2f;
            Fond.lueur(g, cx, logoY + logoH * 0.52f, logoW * 0.64f, logoH * 1.05f, 0x000000, Math.round(200 * tl));
            Fond.lueur(g, cx + logoW * 0.40f, logoY + logoH * 0.5f, logoH * 0.9f, logoH * 0.8f, accent, Math.round(26 * tl));
            Logo.banniere(g, logoX, logoY - (1 - tl) * 5, logoW, tl);
        }
        if (sousTitre != null && Ui.lisible(Math.round(255 * tl))) {
            int a = Math.round(255 * tl);
            float tw = Ui.spacedWidth(font, sousTitre, Ui.SMALL, 1.4f), cx = width / 2f;
            Ui.spacedCentered(g, font, sousTitre, Ui.SMALL, cx, sousY, Ui.argb(Ui.mix(Ui.MUTED, Ui.TEXT, 0.25f), a), 1.4f);
            int l = 34, e = 10, ly = sousY + 3;
            Ui.hairline(g, Math.round(cx - tw / 2 - e - l), Math.round(cx - tw / 2 - e), ly, accent, Math.round(170 * tl), true, false);
            Ui.hairline(g, Math.round(cx + tw / 2 + e), Math.round(cx + tw / 2 + e + l), ly, accent, Math.round(170 * tl), false, true);
        }

        // panneau de verre
        float tp = Anim.sortie(Anim.progression(ouvert, 60, 320));
        int py = Math.round(panneauY + (1 - tp) * 6);
        Fond.lueur(g, panneauX + panneauW / 2f, py + panneauH * 0.5f, panneauW * 0.7f, panneauH * 0.8f, 0x000000, Math.round(110 * tp));
        Ui.glass(g, panneauX, py, panneauW, panneauH, Math.round(Ui.VERRE_ALPHA * tp));
        Ui.hairline(g, panneauX + 18, panneauX + panneauW - 18, py, accent, Math.round(170 * tp), true, true);
        Ui.hairline(g, panneauX + 14, panneauX + panneauW - 14, sepY + (py - panneauY), 0xFFFFFF, Math.round(34 * tp), true, true);

        // boutons en cascade
        for (int i = 0; i < lignes.size(); i++) {
            float ti = Anim.sortie(Anim.progression(ouvert, 110 + i * 30L, 260));
            lignes.get(i).opacite = ti;
            lignes.get(i).decalageY = (1 - ti) * 6;
        }
        float tb = Anim.sortie(Anim.progression(ouvert, 220, 300));
        if (langue != null) langue.opacite = tb;
        super.render(g, mouseX, mouseY, partialTick);

        // pied : version a gauche, « LANGUE » devant les drapeaux
        if (Ui.lisible(Math.round(255 * tb))) {
            int pied = Ui.argb(Ui.mix(Ui.FAINT, Ui.MUTED, 0.45f), Math.round(255 * tb));
            Ui.spaced(g, font, t("titre.version").toUpperCase(Locale.ROOT), Ui.SMALL, 12, height - 15, pied, 1.0f);
            if (langue != null) {
                Ui.spacedRight(g, font, t("titre.langue").toUpperCase(Locale.ROOT), Ui.SMALL, langue.getX() - 8, height - 15, pied, 1.0f);
            }
        }
    }

    @Override
    public void removed() {
        pinger.removeAll();
        Echelle.restaurer();
        super.removed();
    }
}
