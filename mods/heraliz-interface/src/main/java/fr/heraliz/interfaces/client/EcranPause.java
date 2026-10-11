package fr.heraliz.interfaces.client;

import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Menu Echap HeraliZ (remplace {@link net.minecraft.client.gui.screens.PauseScreen}, voir {@link InterfaceClient}),
 * d'apres la maquette validee : a gauche le logo, la phrase d'accroche et le menu (REPRENDRE en accent, INFORMATIONS,
 * DISCORD, BOUTIQUE, OPTIONS, lignes sans cadre au repos), deux petits boutons (STATISTIQUES, JOUEURS ou OUVRIR AU LAN
 * en solo ; ni Mods ni Succes, choix du proprietaire) et SE DECONNECTER en rouge. A droite, la carte du joueur : tete,
 * pseudo, clan et grade, herals, joueurs en ligne, ping, extractions ouvertes, zones tenues par le clan (lus chez nos
 * autres mods par reflexion, {@link ProfilJoueur} ; une ligne sans donnees est cachee). En bas : le serveur a gauche,
 * les liens Mojang a droite. Fenetre trop etroite : menu centre, sans carte.
 * <p>Fond : monde floute + voile + vignette, plus sombre a gauche sous le menu. Echelle forcee comme les ecrans des
 * clans (canevas 640 x 360). Ouverture animee une seule fois (le retour depuis Options ou Informations ne la rejoue
 * pas). Meme deconnexion que le menu vanilla. Une exception au rendu rend la main au menu vanilla.
 */
public class EcranPause extends Screen {
    private long ouvert = -1;
    private final List<MenuLigne> lignes = new ArrayList<>();
    private final List<Tuile> tuiles = new ArrayList<>();
    private final List<Integer> separateurs = new ArrayList<>();
    private int colX, colW, logoX, logoY, logoW, logoH, sousY;
    @Nullable
    private String sousTitre;
    @Nullable
    private String pied;
    private int tick;
    // carte du joueur (relue chaque seconde)
    private boolean carte;
    private int carteX, carteY, carteW, carteH;
    @Nullable
    private Long solde;
    @Nullable
    private ProfilJoueur.Clan clan;
    private List<ProfilJoueur.Extraction> extractions = List.of();
    private int enLigne = -1, ping = -1;

    public EcranPause() {
        super(Component.translatable("menu.game"));
    }

    private static String t(String cle, Object... args) {
        return Component.translatable("gui.heraliz_interface." + cle, args).getString();
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
        String discord = InterfaceConfig.discord(), boutique = lien(InterfaceConfig.BOUTIQUE_LIEN);

        // ---- petits boutons : Joueurs (serveur multi : ni Succès, ni Statistiques, ni Ouvrir au LAN)
        record Petit(int icone, Component libelle, Runnable action) {
        }
        List<Petit> petits = new ArrayList<>();
        petits.add(new Petit(Icons.JOUEURS, Component.translatable("gui.heraliz_interface.pause.joueurs"), () -> mc.setScreen(new SocialInteractionsScreen())));
        int rangees = (petits.size() + 1) / 2;
        int nbLignes = 4 + (discord != null ? 1 : 0); // reprendre, informations, (discord), boutique, options

        // ---- tailles : normale (canevas 640 x 360) ou compacte (petite hauteur d'interface)
        boolean compact = height < 330;
        int lh = compact ? 20 : 22, th = compact ? 16 : 17, qh = compact ? 18 : 20, ecart = 4, groupe = compact ? 9 : 12;
        int marge = Math.max(16, Math.round(width * 0.05f));
        colW = Math.min(204, width - 32);
        // carte du joueur a droite s'il y a la place, sinon menu centre
        carteW = Math.min(204, width - 2 * marge - colW - 48);
        carte = mc.player != null && carteW >= 168;
        colX = carte ? marge : (width - colW) / 2;
        int contenu = nbLignes * lh + groupe + rangees * th + (rangees - 1) * ecart + groupe + qh;
        int piedH = 22;
        String st = InterfaceConfig.texte(InterfaceConfig.SOUS_TITRE, "");
        sousTitre = st.equals("-") ? null : st.isEmpty() ? t("pause.sous_titre") : st;
        int sousH = sousTitre != null ? 12 : 0;
        int dispo = height - piedH - 12 - contenu - sousH - 14;
        logoH = Math.min(compact ? 46 : 58, dispo);
        if (logoH < 24) {
            logoH = 0;
            if (dispo + 14 < sousH) sousTitre = null;
        }
        logoW = Math.round(logoH / Logo.RATIO_BANNIERE);
        int bloc = (logoH > 0 ? logoH + 6 : 0) + (sousTitre != null ? sousH + 14 : 0) + contenu;
        int y = Math.max(8, (height - piedH - bloc) / 2);
        logoX = carte ? colX - 4 : (width - logoW) / 2;
        logoY = y;
        if (logoH > 0) y += logoH + 6;
        sousY = y;
        if (sousTitre != null) y += sousH + 14;

        // ---- lignes (ordre de la maquette)
        int x = colX;
        lignes.add(new MenuLigne(x, y, colW, lh, Icons.REPRENDRE, Component.translatable("gui.heraliz_interface.pause.reprendre"), accent,
                this::onClose).style(MenuLigne.Style.PRINCIPAL).indice(t("pause.echap")));
        y += lh;
        lignes.add(new MenuLigne(x, y, colW, lh, Icons.INFO, libelle(InterfaceConfig.INFORMATIONS_LIBELLE, "pause.informations"), accent,
                () -> mc.setScreen(new EcranInformations(this))).iconeDroite(Icons.CHEVRON).sobre());
        y += lh;
        if (discord != null) {
            lignes.add(new MenuLigne(x, y, colW, lh, Icons.DISCUSSION, libelle(InterfaceConfig.DISCORD_LIBELLE, "pause.discord"), accent,
                    () -> ouvrirLien(discord)).indiceTexte(court(discord)).iconeDroite(Icons.EXTERNE).sobre());
            y += lh;
        }
        MenuLigne bout = new MenuLigne(x, y, colW, lh, Icons.BOUTIQUE, libelle(InterfaceConfig.BOUTIQUE_LIBELLE, "pause.boutique"), accent,
                () -> {
                    if (boutique != null) ouvrirLien(boutique);
                }).sobre();
        if (boutique == null) bout.actif(false).pastille(t("pause.bientot"));
        else bout.indiceTexte(court(boutique)).iconeDroite(Icons.EXTERNE);
        lignes.add(bout);
        y += lh;
        lignes.add(new MenuLigne(x, y, colW, lh, Icons.OPTIONS, Component.translatable("gui.heraliz_interface.pause.options"), accent,
                () -> mc.setScreen(new OptionsScreen(this, mc.options))).iconeDroite(Icons.CHEVRON).sobre());
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

        // ---- quitter (comme le vanilla : grise pendant la deconnexion, brouillon de signalement propose d'abord)
        Component quitter = Component.translatable(mc.isLocalServer() ? "gui.heraliz_interface.pause.quitter_solo" : "gui.heraliz_interface.pause.deconnecter");
        MenuLigne[] q = new MenuLigne[1];
        q[0] = new MenuLigne(x, y, colW, qh, Icons.QUITTER, quitter, accent, () -> {
            q[0].active = false;
            mc.getReportingContext().draftReportHandled(mc, this, this::deconnecter, true);
        }).style(MenuLigne.Style.DANGER).sobre();
        lignes.add(q[0]);

        for (MenuLigne l : lignes) addRenderableWidget(l);
        for (Tuile tu : tuiles) addRenderableWidget(tu);

        // ---- liens Mojang (avis, bugs), discrets en bas a droite
        if (InterfaceConfig.oui(InterfaceConfig.LIENS_MOJANG, true)) {
            String avisUrl = SharedConstants.getCurrentVersion().isStable() ? "https://aka.ms/javafeedback?ref=game" : "https://aka.ms/snapshotfeedback?ref=game";
            Component avis = Component.translatable("gui.heraliz_interface.pause.avis"), bugs = Component.translatable("gui.heraliz_interface.pause.bugs");
            int wb = (int) Ui.spacedWidth(font, bugs.getString(), Ui.SMALL, 0.8f) + 2, wa = (int) Ui.spacedWidth(font, avis.getString(), Ui.SMALL, 0.8f) + 2;
            int ly = height - 16;
            int lx = carte ? width - marge : width - 10;
            HzButton b = new HzButton(lx - wb, ly, wb, 10, bugs, accent, () -> ouvrirLien("https://aka.ms/snapshotbugs?ref=game")).look(HzButton.Look.LINK);
            b.active = !SharedConstants.getCurrentVersion().getDataVersion().isSideSeries();
            addRenderableWidget(b);
            addRenderableWidget(new HzButton(lx - wb - 12 - wa, ly, wa, 10, avis, accent, () -> ouvrirLien(avisUrl)).look(HzButton.Look.LINK));
        }
        majPied();
        majCarte();
        carteX = width - marge - carteW;
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

    /** Pied : le serveur (monde solo : son nom) ; sans carte, aussi le pseudo et le ping. */
    private void majPied() {
        pied = null;
        Minecraft mc = minecraft;
        if (!InterfaceConfig.oui(InterfaceConfig.PIED, true) || mc == null || mc.player == null) return;
        List<String> morceaux = new ArrayList<>();
        if (!carte) morceaux.add(mc.player.getGameProfile().getName());
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            morceaux.add(t("pause.solo") + " : " + mc.getSingleplayerServer().getWorldData().getLevelName());
        } else {
            ServerData sd = mc.getCurrentServer();
            if (sd != null && sd.name != null && !sd.name.isBlank()) morceaux.add(sd.name);
            if (!carte && ping >= 0) morceaux.add(t("pause.ping", ping));
        }
        pied = morceaux.isEmpty() ? null : String.join("  ·  ", morceaux);
    }

    /** Donnees de la carte du joueur (chaque seconde : solde, clan, extractions, joueurs en ligne, ping). */
    private void majCarte() {
        Minecraft mc = minecraft;
        if (mc == null || mc.player == null) return;
        ping = -1;
        enLigne = -1;
        if (mc.getConnection() != null && !mc.hasSingleplayerServer()) {
            PlayerInfo pi = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (pi != null) ping = pi.getLatency();
            enLigne = mc.getConnection().getOnlinePlayers().size();
        }
        if (!carte) return;
        solde = ProfilJoueur.solde();
        clan = ProfilJoueur.clan();
        extractions = mc.level == null ? List.of() : ProfilJoueur.extractions(mc.level.getGameTime());
        int n = Math.max(1, Math.min(3, extractions.size()));
        carteH = 108 + n * 15 + 44; // + ecart, titre « Ton clan », sa ligne, marge du bas
        carteY = Math.max(8, Math.min(height - 30 - carteH, (height - 22 - carteH) / 2));
    }

    @Override
    public void tick() {
        if (++tick % 20 == 0) {
            majCarte();
            majPied();
        }
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
        if (carte) {
            // plus sombre sous le menu : les lignes sans cadre se lisent sur le monde floute
            int fin = colX + colW + 90, pas = 24;
            for (int i = 0; i < pas; i++) {
                float u = 1 - (i + 0.5f) / pas;
                int a = (int) (120 * t * u * u * (3 - 2 * u));
                g.fill(fin * i / pas, 0, fin * (i + 1) / pas, height, Ui.argb(0x05060A, a));
            }
        }

        float tl = Anim.sortie(Anim.progression(ouvert, 40, 420));
        if (logoH > 0) {
            float cx = logoX + logoW / 2f;
            Fond.lueur(g, cx, logoY + logoH * 0.52f, logoW * 0.64f, logoH * 1.05f, 0x000000, Math.round(200 * tl));
            Fond.lueur(g, cx + logoW * 0.40f, logoY + logoH * 0.5f, logoH * 0.9f, logoH * 0.8f, accent, Math.round(26 * tl));
            Logo.banniere(g, logoX, logoY - (1 - tl) * 5, logoW, tl);
        }
        if (sousTitre != null && Ui.lisible(Math.round(255 * tl))) {
            int a = Math.round(255 * tl);
            if (carte) {
                // aligne a gauche, petit filet d'accent devant
                Ui.hairline(g, colX, colX + 16, sousY + 3, accent, Math.round(200 * tl), true, false);
                Ui.spaced(g, font, sousTitre, Ui.SMALL, colX + 22, sousY, Ui.argb(Ui.mix(Ui.MUTED, Ui.TEXT, 0.25f), a), 1.4f);
            } else {
                float tw = Ui.spacedWidth(font, sousTitre, Ui.SMALL, 1.4f);
                float cx = width / 2f;
                Ui.spacedCentered(g, font, sousTitre, Ui.SMALL, cx, sousY, Ui.argb(Ui.mix(Ui.MUTED, Ui.TEXT, 0.25f), a), 1.4f);
                int l = 34, e = 10, ly = sousY + 3;
                Ui.hairline(g, Math.round(cx - tw / 2 - e - l), Math.round(cx - tw / 2 - e), ly, accent, Math.round(170 * tl), true, false);
                Ui.hairline(g, Math.round(cx + tw / 2 + e), Math.round(cx + tw / 2 + e + l), ly, accent, Math.round(170 * tl), false, true);
            }
        }

        float tp = Anim.sortie(Anim.progression(ouvert, 60, 300));
        for (int sy : separateurs) Ui.hairline(g, colX + 8, colX + colW - 8, sy, 0xFFFFFF, Math.round(34 * tp), false, true);

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
        if (carte) dessinerCarte(g, Anim.sortie(Anim.progression(ouvert, 140, 320)), accent);
        super.render(g, mouseX, mouseY, partialTick);

        if (pied != null && Ui.lisible(Math.round(255 * t))) {
            Ui.at(g, font, Ui.lit(pied.toUpperCase(Locale.ROOT), Ui.SMALL), carte ? colX : 10, height - 15,
                    Ui.argb(Ui.mix(Ui.FAINT, Ui.MUTED, 0.35f), Math.round(255 * t)));
        }
    }

    /** Carte du joueur, a droite : tete, pseudo, clan ; herals, en ligne, ping ; extractions ouvertes ; zones du clan. */
    private void dessinerCarte(GuiGraphics g, float t, int accent) {
        Minecraft mc = minecraft;
        if (mc.player == null || t < 0.02f) return;
        int a = Math.round(255 * t);
        int x = carteX, y = Math.round(carteY + (1 - t) * 6), w = carteW, h = carteH;
        Fond.lueur(g, x + w / 2f, y + h * 0.55f, w * 0.62f, h * 0.62f, 0x000000, Math.round(90 * t));
        Ui.glass(g, x, y, w, h, Math.round(Ui.VERRE_ALPHA * t));
        Ui.hairline(g, x + 14, x + w - 14, y, accent, Math.round(170 * t), true, true);
        int px = x + 12, iw = w - 24;

        // tete (visage et chapeau du skin), pseudo, clan
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, t);
        PlayerFaceRenderer.draw(g, mc.player.getSkinTextureLocation(), px, y + 12, 28);
        g.setColor(1f, 1f, 1f, 1f);
        int tx = px + 36;
        String nom = Ui.ellipsis(font, mc.player.getGameProfile().getName(), Ui.BOLD, (int) ((x + w - 12 - tx) / 1.3f));
        Ui.gros(g, font, Ui.lit(nom, Ui.BOLD), tx, y + 14, 1.3f, Ui.argb(Ui.TEXT, a));
        ProfilJoueur.Clan c = clan;
        if (c != null) {
            String puce = "[" + c.tag() + "] " + c.nom();
            puce = Ui.ellipsis(font, puce, Ui.SMALL, x + w - 12 - tx - 40);
            int cw = Ui.chip(g, font, puce, tx, y + 29, c.accent(), a);
            if (!c.grade().isEmpty() && tx + cw + 6 + Ui.spacedWidth(font, c.grade(), Ui.SMALL, 0.8f) <= x + w - 12) {
                Ui.spaced(g, font, c.grade(), Ui.SMALL, tx + cw + 6, y + 31.5f, Ui.argb(Ui.MUTED, a), 0.8f);
            }
        } else {
            Ui.chip(g, font, t("pause.carte.sans_clan_puce"), tx, y + 29, Ui.MUTED, a);
        }

        // tuiles : herals, en ligne, ping
        int tw = (iw - 8) / 3, ty = y + 50;
        Component vSolde = solde == null ? Ui.lit("—", Ui.BOLD) : Ui.herals(solde, Ui.BOLD).withStyle(s -> s.withColor(Ui.OR));
        Ui.tile(g, font, px, ty, tw, 34, vSolde, t("pause.carte.herals"), a);
        Ui.tile(g, font, px + tw + 4, ty, tw, 34, Ui.lit(enLigne < 0 ? "—" : String.valueOf(enLigne), Ui.BOLD), t("pause.carte.en_ligne"), a);
        int cp = ping < 0 ? Ui.TEXT : ping < 80 ? Ui.OK : ping < 150 ? Ui.AMBRE : Ui.DANGER;
        Component vPing = Ui.lit(ping < 0 ? "—" : t("pause.ping", ping), Ui.BOLD).withStyle(s -> s.withColor(cp));
        Ui.tile(g, font, px + 2 * (tw + 4), ty, tw, 34, vPing, t("pause.carte.ping"), a);

        // extractions ouvertes
        int sy = y + 96;
        Ui.section(g, font, t("pause.carte.extractions"), px, sy, iw, a);
        int ry = sy + 12;
        if (extractions.isEmpty()) {
            Ui.at(g, font, Ui.lit(t("pause.carte.aucune_extraction"), Ui.FONT), px, ry + 3, Ui.argb(Ui.MUTED, a));
            ry += 15;
        } else {
            for (int n = 0; n < Math.min(3, extractions.size()); n++) {
                ProfilJoueur.Extraction e = extractions.get(n);
                String droite = e.secondes() < 0 ? t("pause.carte.permanente") : t("pause.carte.ferme_dans", Ui.duree(e.secondes() * 1000L));
                Component cd = Ui.lit(droite, Ui.SMALL);
                int dw = font.width(cd);
                Ui.point(g, px, ry + 5, Ui.OK, a);
                Ui.at(g, font, Ui.lit(Ui.ellipsis(font, e.nom(), Ui.FONT, iw - 12 - dw - 6), Ui.FONT), px + 10, ry + 3, Ui.argb(Ui.TEXT, a));
                Ui.right(g, font, cd, px + iw, ry + 4, Ui.argb(Ui.MUTED, a));
                if (n < Math.min(3, extractions.size()) - 1) Ui.hairline(g, px, px + iw, ry + 14, 0xFFFFFF, Math.round(18 * t), false, false);
                ry += 15;
            }
        }

        // le clan
        int cy = ry + 8;
        Ui.section(g, font, t("pause.carte.clan"), px, cy, iw, a);
        int ly = cy + 12;
        if (c == null) {
            Ui.at(g, font, Ui.lit(Ui.ellipsis(font, t("pause.carte.sans_clan"), Ui.FONT, iw), Ui.FONT), px, ly + 3, Ui.argb(Ui.MUTED, a));
        } else {
            String texte = c.zones().isEmpty() ? t("pause.carte.aucune_zone")
                    : (c.zones().size() == 1 ? t("pause.carte.zone") : t("pause.carte.zones", c.zones().size())) + " · " + String.join(", ", c.zones());
            Ui.point(g, px, ly + 5, c.accent(), a);
            Ui.at(g, font, Ui.lit(Ui.ellipsis(font, texte, Ui.FONT, iw - 10), Ui.FONT), px + 10, ly + 3, Ui.argb(Ui.TEXT, a));
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
