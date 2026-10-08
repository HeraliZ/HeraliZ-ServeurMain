package fr.heraliz.interfaces.client;

import com.mojang.realmsclient.RealmsMainScreen;
import fr.heraliz.interfaces.InterfaceConfig;
import fr.heraliz.interfaces.kit.Anim;
import fr.heraliz.interfaces.kit.Echelle;
import fr.heraliz.interfaces.kit.Fond;
import fr.heraliz.interfaces.kit.HzButton;
import fr.heraliz.interfaces.kit.Icons;
import fr.heraliz.interfaces.kit.Logo;
import fr.heraliz.interfaces.kit.MenuLigne;
import fr.heraliz.interfaces.kit.Tuile;
import fr.heraliz.interfaces.kit.Ui;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.ModListScreen;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Menu Echap HeraliZ (remplace {@link net.minecraft.client.gui.screens.PauseScreen}, voir {@link InterfaceClient}).
 * Colonne centree : logo officiel sur une lueur sombre, phrase d'accroche, panneau de verre avec REPRENDRE (accent),
 * DISCORD, BOUTIQUE, INFORMATIONS, OPTIONS, puis deux rangees de petits boutons (SUCCES, STATISTIQUES, MODS, OUVRIR AU
 * LAN ou JOUEURS) et SE DECONNECTER (rouge). En bas a gauche : pseudo, serveur et ping ; a droite : petits liens Mojang.
 * Tout ce que propose le menu vanilla reste accessible (avis et bugs Mojang compris), avec la meme logique de
 * deconnexion (brouillon de signalement, sauvegarde du monde solo, retour a l'ecran multijoueur ou Realms).
 * <p>Fond : monde floute + voile diagonal + vignette. Echelle forcee comme les ecrans des clans (canevas 640 x 360),
 * sinon mise en page compacte qui tient a l'echelle 4 en 1080p. Ouverture animee une seule fois (le retour depuis
 * Options ou Informations ne rejoue pas l'animation). Une exception au rendu rend la main au menu vanilla.
 */
public class EcranPause extends Screen {
    private long ouvert = -1;
    private final List<MenuLigne> lignes = new ArrayList<>();
    private final List<Tuile> tuiles = new ArrayList<>();
    private final List<Integer> separateurs = new ArrayList<>();
    private int panX, panY, panW, panH, pad, logoX, logoY, logoW, logoH, sousY;
    @Nullable
    private String sousTitre;
    @Nullable
    private String pied;
    private int tickPied;

    public EcranPause() {
        super(Component.translatable("menu.game"));
    }

    private static String t(String cle) {
        return Component.translatable("gui.heraliz_interface." + cle).getString();
    }

    /** Libelle de config, ou la traduction si vide. */
    private static Component libelle(net.minecraftforge.common.ForgeConfigSpec.ConfigValue<String> v, String cle) {
        String s = InterfaceConfig.texte(v, "");
        return s.isEmpty() ? Component.translatable("gui.heraliz_interface." + cle) : Component.literal(s);
    }

    @Nullable
    private static String lien(net.minecraftforge.common.ForgeConfigSpec.ConfigValue<String> v) {
        String s = InterfaceConfig.texte(v, "");
        return s.isEmpty() ? null : s;
    }

    /** « https://discord.gg/abc » -> « discord.gg/abc ». */
    private static String court(String url) {
        String s = url.replaceFirst("(?i)^https?://", "").replaceFirst("^www\\.", "");
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    // ------------------------------------------------------------------ construction
    @Override
    protected void init() {
        if (ouvert < 0) ouvert = Util.getMillis();
        if (InterfaceConfig.oui(InterfaceConfig.ECHELLE_AUTO, true)) Echelle.forcer(this);
        lignes.clear();
        tuiles.clear();
        separateurs.clear();
        Minecraft mc = minecraft;
        int accent = Ui.accent();
        String discord = lien(InterfaceConfig.DISCORD_LIEN), boutique = lien(InterfaceConfig.BOUTIQUE_LIEN);
        boolean lanPossible = mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null && !mc.getSingleplayerServer().isPublished();

        // ---- petits boutons (2 par rangee)
        record Petit(int icone, Component libelle, Runnable action) {
        }
        List<Petit> petits = new ArrayList<>();
        petits.add(new Petit(Icons.SUCCES, Component.translatable("gui.heraliz_interface.pause.succes"),
                () -> mc.setScreen(new AdvancementsScreen(mc.player.connection.getAdvancements()))));
        petits.add(new Petit(Icons.STATS, Component.translatable("gui.heraliz_interface.pause.statistiques"),
                () -> mc.setScreen(new StatsScreen(this, mc.player.getStats()))));
        if (InterfaceConfig.oui(InterfaceConfig.BOUTON_MODS, true)) {
            petits.add(new Petit(Icons.MODS, Component.translatable("gui.heraliz_interface.pause.mods"), () -> mc.setScreen(new ModListScreen(this))));
        }
        if (lanPossible) {
            petits.add(new Petit(Icons.LAN, Component.translatable("gui.heraliz_interface.pause.lan"), () -> mc.setScreen(new ShareToLanScreen(this))));
        } else {
            petits.add(new Petit(Icons.JOUEURS, Component.translatable("gui.heraliz_interface.pause.joueurs"), () -> mc.setScreen(new SocialInteractionsScreen())));
        }
        int rangees = (petits.size() + 1) / 2;
        int nbLignes = 3 + (discord != null ? 1 : 0) + 1; // reprendre, (discord), boutique, informations, options

        // ---- tailles : normale (canevas 640 x 360) ou compacte (petite hauteur d'interface)
        boolean compact = height < 330;
        int lh = compact ? 18 : 20, ph = compact ? 20 : 22, th = compact ? 16 : 18, ecart = compact ? 3 : 4, groupe = compact ? 9 : 11;
        pad = compact ? 10 : 12;
        int colW = Math.min(228, width - 48);
        int contenu = ph + (nbLignes - 1) * (ecart + lh) + groupe + rangees * th + (rangees - 1) * ecart + groupe + lh;
        panW = colW + 2 * pad;
        panH = contenu + 2 * pad;
        int piedH = InterfaceConfig.oui(InterfaceConfig.PIED, true) ? 20 : 8;
        String st = InterfaceConfig.texte(InterfaceConfig.SOUS_TITRE, "");
        sousTitre = st.equals("-") ? null : st.isEmpty() ? t("pause.sous_titre") : st;
        int sousH = sousTitre != null ? 16 : 0;
        int dispo = height - piedH - 10 - panH - sousH - 8;
        logoH = Math.min(compact ? 54 : 74, dispo);
        if (logoH < 24) {
            logoH = 0;
            if (dispo + 8 < sousH) sousTitre = null;
        }
        logoW = Math.round(logoH / Logo.RATIO_BANNIERE);
        int bloc = (logoH > 0 ? logoH + 8 : 0) + (sousTitre != null ? sousH : 0) + panH;
        int y = Math.max(6, (height - piedH - bloc) / 2 + 2);
        logoX = (width - logoW) / 2;
        logoY = y;
        if (logoH > 0) y += logoH + 8;
        sousY = y;
        if (sousTitre != null) y += sousH;
        panX = (width - panW) / 2;
        panY = y;

        // ---- lignes
        int x = panX + pad;
        y = panY + pad;
        lignes.add(new MenuLigne(x, y, colW, ph, Icons.REPRENDRE, Component.translatable("gui.heraliz_interface.pause.reprendre"), accent,
                this::onClose).style(MenuLigne.Style.PRINCIPAL).indice(t("pause.echap")));
        y += ph + ecart;
        if (discord != null) {
            lignes.add(new MenuLigne(x, y, colW, lh, Icons.DISCUSSION, libelle(InterfaceConfig.DISCORD_LIBELLE, "pause.discord"), accent,
                    () -> ouvrirLien(discord)).indiceTexte(court(discord)).iconeDroite(Icons.EXTERNE));
            y += lh + ecart;
        }
        MenuLigne bout = new MenuLigne(x, y, colW, lh, Icons.BOUTIQUE, libelle(InterfaceConfig.BOUTIQUE_LIBELLE, "pause.boutique"), accent,
                () -> {
                    if (boutique != null) ouvrirLien(boutique);
                });
        if (boutique == null) bout.actif(false).pastille(t("pause.bientot"));
        else bout.indiceTexte(court(boutique)).iconeDroite(Icons.EXTERNE);
        lignes.add(bout);
        y += lh + ecart;
        lignes.add(new MenuLigne(x, y, colW, lh, Icons.INFO, libelle(InterfaceConfig.INFORMATIONS_LIBELLE, "pause.informations"), accent,
                () -> mc.setScreen(new EcranInformations(this))).iconeDroite(Icons.CHEVRON));
        y += lh + ecart;
        lignes.add(new MenuLigne(x, y, colW, lh, Icons.OPTIONS, Component.translatable("gui.heraliz_interface.pause.options"), accent,
                () -> mc.setScreen(new OptionsScreen(this, mc.options))).iconeDroite(Icons.CHEVRON));
        y += lh;
        separateurs.add(y + groupe / 2);
        y += groupe;

        // ---- petits boutons
        int demi = (colW - 6) / 2;
        for (int i = 0; i < petits.size(); i++) {
            Petit p = petits.get(i);
            boolean seulSurRangee = i == petits.size() - 1 && i % 2 == 0;
            int tx = x + (i % 2 == 0 ? 0 : colW - demi);
            int ty = y + (i / 2) * (th + ecart);
            tuiles.add(new Tuile(tx, ty, seulSurRangee ? colW : demi, th, p.icone(), p.libelle(), accent, p.action()));
        }
        y += rangees * th + (rangees - 1) * ecart;
        separateurs.add(y + groupe / 2);
        y += groupe;

        // ---- quitter
        Component quitter = Component.translatable(mc.isLocalServer() ? "gui.heraliz_interface.pause.quitter_solo" : "gui.heraliz_interface.pause.deconnecter");
        // comme le vanilla : le bouton se grise pendant la deconnexion, un brouillon de signalement est propose d'abord
        MenuLigne[] q = new MenuLigne[1];
        q[0] = new MenuLigne(x, y, colW, lh, Icons.QUITTER, quitter, accent, () -> {
            q[0].active = false;
            mc.getReportingContext().draftReportHandled(mc, this, this::deconnecter, true);
        }).style(MenuLigne.Style.DANGER);
        lignes.add(q[0]);

        for (MenuLigne l : lignes) addRenderableWidget(l);
        for (Tuile tu : tuiles) addRenderableWidget(tu);

        // ---- liens Mojang (avis, bugs), discrets en bas a droite
        if (InterfaceConfig.oui(InterfaceConfig.LIENS_MOJANG, true)) {
            String avisUrl = SharedConstants.getCurrentVersion().isStable() ? "https://aka.ms/javafeedback?ref=game" : "https://aka.ms/snapshotfeedback?ref=game";
            Component avis = Component.translatable("gui.heraliz_interface.pause.avis"), bugs = Component.translatable("gui.heraliz_interface.pause.bugs");
            int wb = (int) Ui.spacedWidth(font, bugs.getString(), Ui.SMALL, 0.8f) + 2, wa = (int) Ui.spacedWidth(font, avis.getString(), Ui.SMALL, 0.8f) + 2;
            int ly = height - 16;
            HzButton b = new HzButton(width - 10 - wb, ly, wb, 10, bugs, accent, () -> ouvrirLien("https://aka.ms/snapshotbugs?ref=game")).look(HzButton.Look.LINK);
            b.active = !SharedConstants.getCurrentVersion().getDataVersion().isSideSeries();
            addRenderableWidget(b);
            addRenderableWidget(new HzButton(width - 10 - wb - 12 - wa, ly, wa, 10, avis, accent, () -> ouvrirLien(avisUrl)).look(HzButton.Look.LINK));
        }
        majPied();
    }

    private void ouvrirLien(String url) {
        Minecraft mc = minecraft;
        mc.setScreen(new ConfirmLinkScreen(ok -> {
            if (ok) Util.getPlatform().openUri(url);
            mc.setScreen(this);
        }, url, true));
    }

    /** Deconnexion : identique au menu vanilla (PauseScreen#onDisconnect). */
    private void deconnecter() {
        Minecraft mc = minecraft;
        boolean local = mc.isLocalServer();
        boolean realms = mc.isConnectedToRealms();
        mc.level.disconnect();
        if (local) mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        else mc.clearLevel();
        TitleScreen titre = new TitleScreen();
        if (local) mc.setScreen(titre);
        else if (realms) mc.setScreen(new RealmsMainScreen(titre));
        else mc.setScreen(new JoinMultiplayerScreen(titre));
    }

    /** Pied : « Pseudo · Serveur · 42 ms » (monde solo : nom du monde). */
    private void majPied() {
        pied = null;
        Minecraft mc = minecraft;
        if (!InterfaceConfig.oui(InterfaceConfig.PIED, true) || mc == null || mc.player == null) return;
        List<String> morceaux = new ArrayList<>();
        morceaux.add(mc.player.getGameProfile().getName());
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            morceaux.add(t("pause.solo") + " : " + mc.getSingleplayerServer().getWorldData().getLevelName());
        } else {
            ServerData sd = mc.getCurrentServer();
            if (sd != null && sd.name != null && !sd.name.isBlank()) morceaux.add(sd.name);
            if (mc.getConnection() != null) {
                PlayerInfo pi = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (pi != null) morceaux.add(Component.translatable("gui.heraliz_interface.pause.ping", pi.getLatency()).getString());
            }
        }
        pied = String.join("  ·  ", morceaux);
    }

    @Override
    public void tick() {
        if (++tickPied % 20 == 0) majPied();
    }

    // ------------------------------------------------------------------ rendu
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        try {
            dessiner(g, mouseX, mouseY, partialTick);
        } catch (Throwable ex) {
            InterfaceClient.secours(ex);
        }
    }

    private void dessiner(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (fr.heraliz.interfaces.dev.BancInterface.panne) {
            fr.heraliz.interfaces.dev.BancInterface.panne = false;
            throw new IllegalStateException("panne simulee par le banc d'essai");
        }
        int accent = Ui.accent();
        float t = Anim.sortie(Anim.progression(ouvert, 0, 260));
        Fond.dessiner(g, width, height, t);

        float tl = Anim.sortie(Anim.progression(ouvert, 40, 420));
        if (logoH > 0) {
            // lueur sombre derriere le logo : les silhouettes noires des lettres se lisent comme sur l'original
            Fond.lueur(g, width / 2f, logoY + logoH * 0.52f, logoW * 0.64f, logoH * 1.05f, 0x000000, Math.round(200 * tl));
            Fond.lueur(g, width / 2f + logoW * 0.40f, logoY + logoH * 0.5f, logoH * 0.9f, logoH * 0.8f, accent, Math.round(26 * tl));
            Logo.banniere(g, logoX, logoY - (1 - tl) * 5, logoW, tl);
        }
        if (sousTitre != null) {
            int a = Math.round(255 * tl);
            float tw = Ui.spacedWidth(font, sousTitre, Ui.SMALL, 1.4f);
            float cx = width / 2f;
            Ui.spacedCentered(g, font, sousTitre, Ui.SMALL, cx, sousY, Ui.argb(Ui.mix(Ui.MUTED, Ui.TEXT, 0.25f), a), 1.4f);
            int l = 34, e = 10, ly = sousY + 3;
            Ui.hairline(g, Math.round(cx - tw / 2 - e - l), Math.round(cx - tw / 2 - e), ly, accent, Math.round(170 * tl), true, false);
            Ui.hairline(g, Math.round(cx + tw / 2 + e), Math.round(cx + tw / 2 + e + l), ly, accent, Math.round(170 * tl), false, true);
        }

        float tp = Anim.sortie(Anim.progression(ouvert, 60, 300));
        // ombre portee douce sous le panneau, puis le verre
        Fond.lueur(g, width / 2f, panY + panH * 0.55f, panW * 0.62f, panH * 0.62f, 0x000000, Math.round(90 * tp));
        Ui.glass(g, panX, panY, panW, panH, Math.round(Ui.VERRE_ALPHA * tp));
        Ui.hairline(g, panX + 20, panX + panW - 20, panY, accent, Math.round(160 * tp), true, true);
        for (int sy : separateurs) Ui.hairline(g, panX + pad, panX + panW - pad, sy, 0xFFFFFF, Math.round(30 * tp), true, true);

        // apparition en cascade des boutons
        int i = 0;
        for (MenuLigne l : lignes) {
            if (i == lignes.size() - 1) i += tuiles.size(); // « quitter » apres les petits boutons
            float ti = Anim.sortie(Anim.progression(ouvert, 90 + i * 26L, 260));
            l.opacite = ti;
            l.decalageY = (1 - ti) * 6;
            i++;
        }
        int k = lignes.size() - 1;
        for (Tuile tu : tuiles) {
            float ti = Anim.sortie(Anim.progression(ouvert, 90 + k * 26L, 260));
            tu.opacite = ti;
            tu.decalageY = (1 - ti) * 6;
            k++;
        }
        super.render(g, mouseX, mouseY, partialTick);

        if (pied != null) {
            // en bas a gauche (les liens Mojang sont a droite) : jamais de chevauchement, meme en interface compacte
            Ui.at(g, font, Ui.lit(pied, Ui.SMALL), 10, height - 15, Ui.argb(Ui.mix(Ui.FAINT, Ui.MUTED, 0.35f), Math.round(255 * t)));
        }
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    /** Banc d'essai : libelles des lignes et des petits boutons, dans l'ordre. */
    public List<String> libelles() {
        List<String> l = new ArrayList<>();
        for (MenuLigne m : lignes) l.add(m.libelle().toUpperCase(Locale.ROOT));
        for (Tuile tu : tuiles) l.add(tu.getMessage().getString().toUpperCase(Locale.ROOT));
        return l;
    }
}
