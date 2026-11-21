package fr.heraliz.grades.client.chat;

import fr.heraliz.grades.client.boutique.Trace;
import fr.heraliz.grades.client.boutique.Trace.P;
import fr.heraliz.grades.client.kit.Ui;
import fr.heraliz.grades.reseau.ReseauChat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static fr.heraliz.grades.client.boutique.Trace.a;

/**
 * Chat HeraliZ (25/10, maquettes {@code maquettes/staff_chat/4_} et {@code 5_} validées) : remplace l'affichage du chat
 * de Minecraft, rien d'autre (les messages arrivent comme avant, {@code ChatComponentMixin}).
 * <ul>
 *     <li><b>fermé</b> : 4 lignes au plus, chacune s'efface au bout de 8 s, jamais par-dessus une interface (inventaire,
 *     fouille, menus…) ;</li>
 *     <li><b>ouvert</b> ({@link EcranChat}) : panneau de verre, onglets Proximité / Global / Clan (Tab pour changer, messages
 *     non lus), défilement à la molette, liens cliquables ;</li>
 *     <li>chaque ligne porte son canal : PROX, GLOBAL, CLAN, MP (messages privés) ou INFO (serveur, commandes) ;</li>
 *     <li>les annonces d'extraction ne vont plus dans le chat : petite notification en haut à gauche ;</li>
 *     <li>les comptes rendus de commandes des autres op (« [X : …] ») ne s'affichent plus.</li>
 * </ul>
 */
public final class ChatHeraliz {
    private ChatHeraliz() {
    }

    /** Canaux d'affichage : ceux d'écriture (ReseauChat) puis messages privés et informations. */
    public static final int MP = 3, INFO = 4;
    private static final int MAX_LIGNES = 100, VIE_MS = 8000, FONDU_MS = 1000, LIGNES_FERME = 4, H = 10;
    static final int LIGNES_OUVERT = 12;
    private static final int[] COULEUR_CANAL = {0xA0A6B0, 0xE6E8EC, 0x6FD08C, 0xB48CFF, 0xF2C14E};
    private static final String[] TAG = {"prox", "global", "clan", "mp", "info"};
    static final P TEXTE = Trace.p("bq_texte", 7.5F, 0), GRAS = Trace.p("bq_texte_gras", 7.5F, 0), MICRO = Trace.p("bq_micro", 5.6F, 0.4F),
            MINI = Trace.p("bq_mini", 6.5F, 0.56F), PETIT = Trace.p("bq_petit", 6.5F, 0), ONGLET = Trace.p("bq_mini", 6.5F, 0);

    /** Une ligne : canal, texte complet (polices posées), heure ; lignes coupées gardées pour une largeur. */
    static final class Ligne {
        final int canal;
        final Component texte;
        final long t;
        private List<FormattedCharSequence> rangs;
        private int largeur = -1;

        Ligne(int canal, Component texte) {
            this.canal = canal;
            this.texte = texte;
            this.t = System.currentTimeMillis();
        }

        List<FormattedCharSequence> rangs(Font f, int w) {
            if (w != largeur || rangs == null) {
                rangs = f.split(texte, Math.max(20, w));
                largeur = w;
            }
            return rangs;
        }
    }

    private static final ArrayDeque<Ligne> LIGNES = new ArrayDeque<>();
    static int canal = ReseauChat.GLOBAL;
    static final int[] NON_LUS = new int[ReseauChat.NOMBRE];

    public static void init() {
        ReseauChat.RECEPTION_LIGNE.brancher(ChatHeraliz::recevoir);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ChatHeraliz::overlay);
        MinecraftForge.EVENT_BUS.addListener(ChatHeraliz::ouverture);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> ReseauChat.CANAL.sendToServer(new ReseauChat.Choisir(canal)));
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> vider());
    }

    // ------------------------------------------------------------------ messages
    /**
     * Le composant dans la police {@code p} du chat, sans le « gras » de Minecraft (18/11, retour du propriétaire : le grade
     * « FONDATEUR » semblait écrit deux fois l'un sur l'autre). Minecraft fait le gras en redessinant chaque lettre décalée
     * d'un point : sur une police DM Sans, déjà grasse pour le nom, on voyait deux textes. Les morceaux en gras passent dans
     * la police grasse du chat, les autres dans {@code p} ; un morceau qui a sa propre police (icônes...) la garde.
     * Couleurs, liens et survols restent (ils sont dans le style de chaque morceau).
     */
    private static Component police(Component c, P p) {
        MutableComponent r = Component.empty();
        c.visit((Style st, String s) -> {
            if (!s.isEmpty()) {
                boolean propre = !Style.DEFAULT_FONT.equals(st.getFont());
                r.append(Component.literal(s).withStyle(propre ? st : st.withBold(false).withFont(st.isBold() ? GRAS.police() : p.police())));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return r;
    }

    private static void ajouter(int c, Component texte) {
        LIGNES.addLast(new Ligne(c, texte));
        while (LIGNES.size() > MAX_LIGNES) LIGNES.removeFirst();
        if (c < ReseauChat.NOMBRE && (c != canal || !(Minecraft.getInstance().screen instanceof EcranChat))) NON_LUS[c]++;
    }

    /** Ligne de chat d'un joueur, envoyée par le serveur avec son canal. */
    private static void recevoir(ReseauChat.Ligne l) {
        MutableComponent t = Component.empty();
        t.append(police(l.nom(), GRAS));
        t.append(police(Component.literal("  "), TEXTE));
        int c = l.couleur() != 0 ? l.couleur() : 0xE6E8EC;
        t.append(Component.literal(l.message()).withStyle(s -> s.withFont(TEXTE.police()).withColor(c)));
        ajouter(Math.max(0, Math.min(ReseauChat.NOMBRE - 1, l.canal())), t);
    }

    /** Toute ligne ajoutée au chat de Minecraft (serveur, commandes, mods côté joueur). */
    public static void ajouterMinecraft(Component m) {
        if (m == null) return;
        String cle = m.getContents() instanceof TranslatableContents tc ? tc.getKey() : "";
        if (cle.equals("heraliz_extraction.chat.prefixe") || cle.equals("heraliz_evenements.chat.prefixe")) {
            NotifExtraction.recevoir(m, cle.startsWith("heraliz_evenements"));
            return;
        }
        if (cle.equals("chat.type.admin")) return;
        int c = cle.equals("chat.clan") ? ReseauChat.CLAN
                : cle.startsWith("commands.message.display") ? MP
                : cle.equals("chat.type.text") || cle.equals("chat.type.announcement") ? ReseauChat.GLOBAL : INFO;
        ajouter(c, police(m, TEXTE));
    }

    /** Chat vidé (F3 + D, ou le staff pour tout le monde). */
    public static void vider() {
        LIGNES.clear();
        java.util.Arrays.fill(NON_LUS, 0);
    }

    /** Le joueur a un clan (tag envoyé par le serveur dans les infos du tab). */
    static boolean aUnClan() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        fr.heraliz.grades.reseau.ReseauGrades.LigneTab l = fr.heraliz.grades.client.TabHeraliz.info(mc.player.getUUID());
        return l != null && !l.clan().isEmpty();
    }

    static void choisir(int c) {
        if (c < 0 || c >= ReseauChat.NOMBRE || c == canal) return;
        canal = c;
        NON_LUS[c] = 0;
        ReseauChat.CANAL.sendToServer(new ReseauChat.Choisir(c));
    }

    /** Canal suivant (Tab) ; Clan sauté sans clan. */
    static void suivant() {
        int c = canal;
        for (int i = 0; i < ReseauChat.NOMBRE; i++) {
            c = (c + 1) % ReseauChat.NOMBRE;
            if (c != ReseauChat.CLAN || aUnClan()) break;
        }
        choisir(c);
    }

    // ------------------------------------------------------------------ écran de chat
    private static Field initial;

    /** Le chat de Minecraft s'ouvre (T, /) : c'est le nôtre qui s'ouvre, avec le même texte de départ. */
    private static void ouverture(ScreenEvent.Opening e) {
        if (e.getNewScreen() == null || e.getNewScreen().getClass() != ChatScreen.class) return;
        String texte = "";
        try {
            if (initial == null) initial = ObfuscationReflectionHelper.findField(ChatScreen.class, "f_95576_");
            texte = (String) initial.get(e.getNewScreen());
        } catch (RuntimeException | IllegalAccessException ex) {
            texte = "";
        }
        e.setNewScreen(new EcranChat(texte == null ? "" : texte));
    }

    // ------------------------------------------------------------------ dessin
    /** Largeur du chat (unités d'interface) : un tiers de l'écran, entre 200 et 320. */
    static int largeur(int ecran) {
        return Math.max(200, Math.min(320, Math.round(ecran * 0.34F)));
    }

    private static void overlay(RenderGuiOverlayEvent.Pre e) {
        if (!e.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) return;
        e.setCanceled(true);
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = e.getWindow().getGuiScaledWidth(), h = e.getWindow().getGuiScaledHeight();
        if (mc.screen == null) dessinerFerme(g, mc.font, w, h);
    }

    private static boolean visible(Ligne l) {
        ChatVisiblity v = Minecraft.getInstance().options.chatVisibility().get();
        if (v == ChatVisiblity.HIDDEN) return false;
        return v != ChatVisiblity.SYSTEM || l.canal == INFO || l.canal == MP;
    }

    /**
     * Texte de la pastille de chaque canal, dans la langue du jeu (21/11, audit des images par seconde) : avant, traduit et
     * mis en capitales deux fois par ligne affichée, à chaque image ; maintenant refait seulement quand la langue change.
     */
    private static final String[] TEXTE_TAG = new String[TAG.length];
    private static String langueTags;

    static String texteTag(int c) {
        String langue = Minecraft.getInstance().getLanguageManager().getSelected();
        if (langueTags == null || !langueTags.equals(langue)) {
            for (int i = 0; i < TAG.length; i++) {
                TEXTE_TAG[i] = Component.translatable("gui.heraliz_grades.chat.tag." + TAG[i]).getString().toUpperCase(Locale.ROOT);
            }
            langueTags = langue;
        }
        return TEXTE_TAG[c];
    }

    /** Pastille du canal ; rend sa largeur (avec l'écart qui suit). */
    static float pastille(GuiGraphics g, Font f, int c, float x, float cy, float alpha) {
        String s = texteTag(c);
        float tw = Trace.largeur(f, s, MICRO);
        Trace.rect(g, x, cy - 4, x + tw + 6, cy + 4, a(0, 120 * alpha));
        Trace.c(g, f, s, x + (tw + 6) / 2, cy, MICRO, a(COULEUR_CANAL[c], 255 * alpha), 0);
        return tw + 6 + 4;
    }

    /** Un rang de texte, milieu des capitales sur cy. */
    static void rang(GuiGraphics g, Font f, FormattedCharSequence s, float x, float cy, float alpha, boolean ombre) {
        g.pose().pushPose();
        Ui.placer(g, x, cy - TEXTE.cap() / 2 - TEXTE.hautCap());
        g.drawString(f, s, 0, 0, a(0xFFFFFF, 255 * alpha), ombre);
        g.pose().popPose();
    }

    private static void dessinerFerme(GuiGraphics g, Font f, int w, int h) {
        long now = System.currentTimeMillis();
        float opacite = (float) (double) Minecraft.getInstance().options.chatOpacity().get();
        int largeur = largeur(w);
        // 25/10 (audit en jeu) : au-dessus des cœurs et de l'armure (h - 49) : à h - 44, la dernière ligne passait dessus
        float x0 = 8, cy = h - 56;
        int rangs = 0;
        Iterator<Ligne> it = LIGNES.descendingIterator();
        while (it.hasNext() && rangs < LIGNES_FERME) {
            Ligne l = it.next();
            long age = now - l.t;
            if (age > VIE_MS) break;
            if (!visible(l)) continue;
            float alpha = opacite * Math.min(1, (VIE_MS - age) / (float) FONDU_MS);
            float decal = Trace.largeur(f, texteTag(l.canal), MICRO) + 10;
            List<FormattedCharSequence> rs = l.rangs(f, (int) (largeur - decal));
            for (int i = rs.size() - 1; i >= 0 && rangs < LIGNES_FERME; i--) {
                float y = cy - rangs * H;
                if (i == 0) pastille(g, f, l.canal, x0, y, alpha);
                rang(g, f, rs.get(i), x0 + decal, y, alpha, true);
                rangs++;
            }
        }
    }

    /** Lignes visibles du chat ouvert, du bas vers le haut (après le défilement) : ligne, rang, position. */
    record Place(Ligne ligne, FormattedCharSequence rang, boolean premier, float x, float cy) {
    }

    /** Places du chat ouvert ; défilement en rangs ; rend aussi le nombre total de rangs dans total[0]. */
    static List<Place> places(Font f, int largeur, float x0, float bas, int defil, int[] total) {
        List<Place> out = new ArrayList<>();
        int n = 0;
        Iterator<Ligne> it = LIGNES.descendingIterator();
        while (it.hasNext()) {
            Ligne l = it.next();
            if (!visible(l)) continue;
            float decal = Trace.largeur(f, texteTag(l.canal), MICRO) + 10;
            List<FormattedCharSequence> rs = l.rangs(f, (int) (largeur - decal - 8));
            for (int i = rs.size() - 1; i >= 0; i--) {
                int k = n - defil;
                if (k >= 0 && k < LIGNES_OUVERT) out.add(new Place(l, rs.get(i), i == 0, x0 + decal, bas - k * H));
                n++;
            }
        }
        if (total != null) total[0] = n;
        return out;
    }

    /** Style du texte sous la souris (liens, survol) dans le chat ouvert. */
    static Style styleA(Font f, List<Place> places, double mx, double my) {
        for (Place p : places) {
            if (my < p.cy() - H / 2F || my >= p.cy() + H / 2F) continue;
            if (mx < p.x()) return null;
            return f.getSplitter().componentStyleAtWidth(p.rang(), (int) Math.floor(mx - p.x()));
        }
        return null;
    }

    // ------------------------------------------------------------------ notification des extractions
    /**
     * Annonces d'extraction (HeraliZ Extraction, préfixe « [Extraction] ») : jamais dans le chat ; depuis le 18/11 dans les
     * bandeaux communs à droite de l'écran (maquette validée, {@code fr.heraliz.noyau.client.Bandeaux}). Les ouvertures
     * d'une même vague (arrivées à moins de 2 s) sont groupées dans un seul bandeau qui se met à jour : « Extractions
     * ouvertes · Extraction 3 · 7 · 12 — pendant 1 h ». Même chose pour les annonces des événements de la carte (HeraliZ
     * Événements, préfixe « [Événement] »), trait orange.
     */
    static final class NotifExtraction {
        private static final List<String> ZONES = new ArrayList<>();
        private static final int VERT = 0x5FD08A, AMBRE = 0xE8B34A, NEUTRE = 0x8F95A0, ORANGE = 0xFF7A45;
        private static String duree = "";
        private static long derniere;

        static void recevoir(Component m, boolean annonceEvenement) {
            long now = System.currentTimeMillis();
            if (!annonceEvenement) {
                for (Component s : m.getSiblings()) {
                    if (s.getContents() instanceof TranslatableContents tc && tc.getKey().equals("heraliz_extraction.zone.ouverte")
                            && tc.getArgs().length >= 2) {
                        if (now - derniere > 2000) ZONES.clear();
                        Object nom = tc.getArgs()[0], d = tc.getArgs()[1];
                        String n = nom instanceof Component c ? c.getString() : String.valueOf(nom);
                        ZONES.add(n.replaceAll("(?i)^extraction\\s+", ""));
                        duree = d instanceof Component c ? c.getString() : String.valueOf(d);
                        derniere = now;
                        fr.heraliz.noyau.client.Bandeaux.remplacer("extractions_ouvertes",
                                Component.translatable("gui.heraliz_grades.chat.extractions_ouvertes").getString(),
                                Component.translatable("gui.heraliz_grades.chat.extractions_liste", String.join(" · ", ZONES), duree).getString(),
                                VERT, 10);
                        return;
                    }
                }
            }
            StringBuilder b = new StringBuilder();
            int couleur = annonceEvenement ? ORANGE : AMBRE;
            for (Component s : m.getSiblings()) {
                b.append(s.getString());
                if (s.getContents() instanceof TranslatableContents tc && tc.getKey().equals("heraliz_extraction.zone.fermee")) couleur = NEUTRE;
            }
            String texte = b.toString().strip();
            if (texte.isEmpty()) return;
            String titre = Component.translatable(annonceEvenement ? "gui.heraliz_grades.chat.evenement" : "gui.heraliz_grades.chat.extraction").getString();
            fr.heraliz.noyau.client.Bandeaux.ajouter(titre, texte, couleur, annonceEvenement ? 10 : 6);
        }
    }
}
