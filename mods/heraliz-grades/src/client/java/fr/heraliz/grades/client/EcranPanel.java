package fr.heraliz.grades.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.heraliz.grades.client.boutique.Trace;
import fr.heraliz.grades.client.boutique.Trace.P;
import fr.heraliz.grades.client.kit.Echelle;
import fr.heraliz.grades.client.kit.SonsUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleConsumer;

import static fr.heraliz.grades.client.boutique.Trace.a;

/**
 * Panneau admin des op (2.7.0, {@code /panel}, ouvert par le serveur après contrôle : op de niveau 4 connecté sur
 * AuthMe). Il montre TOUTES les commandes que le serveur permet à cet op : l'arbre des commandes que le serveur envoie à
 * chaque joueur (Brigadier, celui de la complétion du chat), rangé par catégories ({@link #CATEGORIE}). Une commande
 * choisie se construit pas à pas : à chaque étape, les mots-clés et les suggestions de Brigadier (celles du serveur
 * comprises : pseudos, objets…) à cliquer, ou une valeur à taper ; l'aperçu montre la commande entière, « Exécuter »
 * l'envoie comme si elle était tapée dans le chat (le serveur revérifie tout). Onglet « Utilisation » : toutes les formes
 * de la commande.
 * <p>
 * Canevas 640 x 360 (échelle forcée par {@link Echelle}), style du créateur de personnage (comme la boutique) : textes
 * DM Sans à leur vraie taille, posés sur un point entier de l'écran. Les mesures ci-dessous sont recopiées dans
 * {@code outils/apercu_panel.py}, qui refait l'écran dans les trois langues et refuse tout texte qui dépasse.
 */
public final class EcranPanel extends Screen {
    // ------------------------------------------------------------------ mesures (aussi dans outils/apercu_panel.py)
    private static final int GW = 640, GH = 360, M = 16, HAUT = 52, BAS = 334;
    private static final int SX = 414, SY = 13, SW = 210, SH = 22;
    private static final int CX = 16, CW = 128, MX = 152, MW = 168, DX = 328, DW = 296, DH = BAS - HAUT;
    private static final int RANG_CAT = 19, RANG_CMD = 15, RANG_CHOIX = 13, RANG_USAGE = 12;
    private static final int ACCENT = 0xE3201B, WHITE = 0xFFFFFF, LIGHT = 0xECEEF2, OPTION = 0xC3C8D0, MUTED = 0x8F95A0, FAINT = 0x5A5F69,
            PINK = 0xF08A86, NIGHT = 0x050608, GLASS = 0x0D0F14, OK = 0x6FD08C, DANGER = 0xFF8A80, GOLD = 0xF2C14E;

    private static final P TITRE = Trace.p("bq_titre", 13, 1.93F), NOM = Trace.p("bq_nom", 11, 0), BOUTON = Trace.p("bq_bouton", 9, 0.85F),
            MINI = Trace.p("bq_mini", 6.5F, 0.56F), MINI_NUM = Trace.p("bq_mini", 6.5F, 0.5F), TEXTE = Trace.p("bq_texte", 7.5F, 0),
            TEXTE_GRAS = Trace.p("bq_texte_gras", 7.5F, 0), PETIT = Trace.p("bq_petit", 6.5F, 0), PETIT_GRAS = Trace.p("bq_mini", 6.5F, 0);

    /** Catégories, dans l'ordre de la colonne de gauche (« toutes » d'abord, « autres » à la fin). */
    static final String[] CATS = {"toutes", "objets", "teleportation", "moderation", "grades", "clans", "bases", "loot", "matchs", "monde", "serveur", "autres"};
    /** Catégorie de chaque commande (nom de la racine) ; une racine inconnue va dans « autres ». */
    static final Map<String, String> CATEGORIE = new HashMap<>();

    private static void ranger(String cat, String... racines) {
        for (String r : racines) CATEGORIE.put(r, cat);
    }

    static {
        ranger("objets", "give", "clear", "item", "enchant", "xp", "experience", "loot", "replaceitem", "camos", "armory", "armurier", "rarete",
                "kit", "bon", "usure", "vehicules", "wonder", "wonderpz", "wonderfx", "jetgun", "jetgunfx", "blundergat", "blundergatfx", "sliq",
                "sliqfx", "givrefx", "emote", "emotetest", "corps", "airstrike", "recipe");
        ranger("teleportation", "tp", "teleport", "spreadplayers", "spawnpoint", "setworldspawn", "warp", "tpa", "tpaccept", "tpdeny", "home",
                "homes", "sethome", "delhome", "rtp", "spawn", "depart", "zone", "ride");
        ranger("moderation", "kick", "ban", "ban-ip", "banip", "ipban", "pardon", "pardon-ip", "unban", "untempban", "unbanplayer", "tempban",
                "tempipban", "mute", "tempmute", "ipmute", "unmute", "warn", "unwarn", "history", "staffhistory", "checkban", "checkmute",
                "banlist", "dupeip", "alts", "lastuuid", "litebans", "kill", "gamemode", "effect", "inspecter", "vanish", "nametag", "sban",
                "sunban", "banpc", "unbanpc", "sbaninfo", "spartan", "whitelist", "op", "deop", "msg", "tell", "w", "r", "say", "tellraw", "title",
                "wipe", "suicide", "authme", "kaurivpn", "damage", "attribute");
        ranger("grades", "grade", "grades", "abonnement", "boutique", "pb", "trade", "nick", "chatcouleur", "zonevip", "lier", "delier", "panel");
        ranger("clans", "clan", "territoire", "saison", "eco", "coeur", "purge");
        ranger("bases", "base", "carriere", "cracbuild", "cracghost", "protection", "tuto", "group", "groupe");
        ranger("loot", "heralizloot", "heraliz", "salle", "extraction", "extractions", "roll", "marchand");
        ranger("matchs", "duel", "duelbanc", "clashwar", "zombies", "matchs", "killcam", "zbdev", "zbmusique", "bots", "casino");
        ranger("monde", "time", "weather", "gamerule", "difficulty", "worldborder", "setblock", "fill", "fillbiome", "clone", "summon", "forceload",
                "locate", "place", "particle", "playsound", "stopsound", "carte", "brume", "zones", "train", "vitres", "cinematique", "pnj", "quete",
                "quetes", "ping", "pings", "perso", "caracrea", "voix", "mammouth", "mere", "n07", "peste", "sylvorn", "rg", "region", "regions",
                "worldguard", "wg", "worldedit", "we");
        ranger("serveur", "reload", "save-all", "save-on", "save-off", "stop", "forge", "mohist", "plugins", "pl", "version", "ver", "about",
                "spark", "lp", "luckperms", "perm", "perms", "permissions", "scoreboard", "team", "function", "datapack", "data", "execute",
                "schedule", "debug", "perf", "jfr", "tick", "trigger", "list", "seed", "help", "me", "defaultgamemode", "publish",
                "setidletimeout", "bossbar", "advancement", "return", "random", "consolespamfix", "tag", "teammsg", "tm", "config");
    }

    /** Catégorie d'une racine (les commandes de WorldEdit commencent par « / » : monde). */
    static String categorie(String racine) {
        if (racine.startsWith("/")) return "monde";
        return CATEGORIE.getOrDefault(racine, "autres");
    }

    // ------------------------------------------------------------------ état
    /** Champ de saisie simple (texte ajouté ou retiré à la fin). */
    private static final class Saisie {
        String v = "";
        final int max;

        Saisie(int max) {
            this.max = max;
        }
    }

    private record Choix(String texte, String valeur, String info, boolean motCle) {
    }

    private record Zone(float x, float y, float w, float h, Runnable action) {
        boolean dedans(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record Defil(float x, float y, float w, float h, DoubleConsumer action) {
        boolean dedans(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final Saisie recherche = new Saisie(48), valeur = new Saisie(256);
    private Saisie focus = recherche;
    private String cat = "toutes";
    private List<String> racines = List.of();
    private int compteRacines = -1;
    private String commande;
    private final List<String> morceaux = new ArrayList<>();
    private List<Choix> choix = List.of();
    private int demande;
    private String demandeSur;
    private List<String> usages = List.of();
    private String usagesSur;
    private boolean modeUsage;
    private String erreur = "", info = "";
    private boolean infoOk;
    private long infoA;
    private float defilCmd, defilChoix, defilUsage;
    private float maxCmd, maxChoix, maxUsage;
    private int ox, oy, sx, sy;
    private final List<Zone> zones = new ArrayList<>();
    private final List<Defil> defils = new ArrayList<>();

    private EcranPanel() {
        super(Component.translatable("gui.heraliz_grades.panel.titre"));
    }

    static void ouvrir() {
        SonsUi.ouvrir(0.6F);
        Minecraft.getInstance().setScreen(new EcranPanel());
    }

    private static String t(String cle, Object... args) {
        return Component.translatable("gui.heraliz_grades.panel." + cle, args).getString();
    }

    // ------------------------------------------------------------------ Brigadier
    private CommandDispatcher<SharedSuggestionProvider> disp() {
        ClientPacketListener c = Minecraft.getInstance().getConnection();
        return c == null ? null : c.getCommands();
    }

    private SharedSuggestionProvider source() {
        ClientPacketListener c = Minecraft.getInstance().getConnection();
        return c == null ? null : c.getSuggestionsProvider();
    }

    private String base() {
        return String.join(" ", morceaux);
    }

    private String entree() {
        String b = base();
        return b.isEmpty() ? valeur.v : b + " " + valeur.v;
    }

    /** Toutes les racines de l'arbre (sans les doublons « espace:nom » de Bukkit), triées. */
    private void lireRacines() {
        CommandDispatcher<SharedSuggestionProvider> d = disp();
        if (d == null) return;
        int n = d.getRoot().getChildren().size();
        if (n == compteRacines) return;
        compteRacines = n;
        List<String> l = new ArrayList<>();
        for (CommandNode<SharedSuggestionProvider> c : d.getRoot().getChildren()) {
            if (c instanceof LiteralCommandNode<SharedSuggestionProvider> lit && !lit.getLiteral().contains(":")) l.add(lit.getLiteral());
        }
        l.sort(String::compareTo);
        racines = l;
    }

    private static CommandContextBuilder<SharedSuggestionProvider> dernier(ParseResults<SharedSuggestionProvider> pr) {
        CommandContextBuilder<SharedSuggestionProvider> ctx = pr.getContext();
        while (ctx.getChild() != null) ctx = ctx.getChild();
        return ctx;
    }

    /** Nœud atteint par le texte (lu en entier), null s'il ne se lit pas. */
    private CommandNode<SharedSuggestionProvider> noeud(String texte) {
        CommandDispatcher<SharedSuggestionProvider> d = disp();
        SharedSuggestionProvider s = source();
        if (d == null || s == null) return null;
        if (texte.isEmpty()) return d.getRoot();
        ParseResults<SharedSuggestionProvider> pr = d.parse(texte, s);
        if (pr.getReader().canRead() || !pr.getExceptions().isEmpty()) return null;
        CommandContextBuilder<SharedSuggestionProvider> ctx = dernier(pr);
        List<ParsedCommandNode<SharedSuggestionProvider>> n = ctx.getNodes();
        CommandNode<SharedSuggestionProvider> node = n.isEmpty() ? ctx.getRootNode() : n.get(n.size() - 1).getNode();
        if (node.getChildren().isEmpty() && node.getRedirect() != null) node = node.getRedirect();
        return node;
    }

    /** Vrai si le texte est une commande complète (lue en entier, dernier nœud exécutable). */
    private boolean executable(String texte) {
        CommandDispatcher<SharedSuggestionProvider> d = disp();
        SharedSuggestionProvider s = source();
        if (d == null || s == null || texte.isBlank()) return false;
        ParseResults<SharedSuggestionProvider> pr = d.parse(texte, s);
        return !pr.getReader().canRead() && pr.getExceptions().isEmpty() && dernier(pr).getCommand() != null;
    }

    private String commandeFinale() {
        return valeur.v.isBlank() ? base() : entree();
    }

    /** Ajoute la valeur à la commande si elle se lit ; sinon rend le message d'erreur (null si c'est bon). */
    private String valider(String v) {
        v = v.strip();
        if (v.isEmpty()) return t("vide");
        String cand = base().isEmpty() ? v : base() + " " + v;
        if (noeud(cand) != null) {
            morceaux.add(v);
            valeur.v = "";
            defilChoix = 0;
            return null;
        }
        CommandDispatcher<SharedSuggestionProvider> d = disp();
        SharedSuggestionProvider s = source();
        if (d == null || s == null) return t("invalide");
        CommandSyntaxException ex = Commands.getParseException(d.parse(cand, s));
        return ex == null ? t("invalide") : ComponentUtils.fromMessage(ex.getRawMessage()).getString();
    }

    /** Redemande les suggestions quand la commande ou la valeur change (celles du serveur arrivent plus tard). */
    private void suggestions() {
        String e = entree();
        if (commande == null || e.equals(demandeSur)) return;
        demandeSur = e;
        CommandDispatcher<SharedSuggestionProvider> d = disp();
        SharedSuggestionProvider s = source();
        if (d == null || s == null) return;
        int id = ++demande;
        int debut = e.length() - valeur.v.length();
        CommandNode<SharedSuggestionProvider> ici = noeud(base());
        try {
            d.getCompletionSuggestions(d.parse(e, s)).thenAccept(sg -> Minecraft.getInstance().tell(() -> {
                if (id != demande) return;
                List<Choix> l = new ArrayList<>();
                for (Suggestion x : sg.getList()) {
                    if (x.getRange().getStart() < debut || l.size() >= 400) continue;
                    String v = x.apply(e).substring(debut);
                    boolean mot = ici != null && x.getRange().getStart() == debut && ici.getChild(x.getText()) instanceof LiteralCommandNode;
                    String inf = mot ? t("mot_cle") : x.getTooltip() == null ? "" : x.getTooltip().getString();
                    l.add(new Choix(x.getText(), v, inf.replace('\n', ' '), mot));
                }
                choix = l;
            }));
        } catch (RuntimeException ex) {
            choix = List.of();
        }
    }

    /** Toutes les formes de la commande à partir de l'étape en cours (onglet Utilisation). */
    private void lireUsages() {
        String b = base();
        if (b.equals(usagesSur)) return;
        usagesSur = b;
        CommandDispatcher<SharedSuggestionProvider> d = disp();
        SharedSuggestionProvider s = source();
        CommandNode<SharedSuggestionProvider> n = noeud(b);
        List<String> l = new ArrayList<>();
        if (d != null && s != null && n != null) {
            for (String u : d.getAllUsage(n, s, true)) {
                l.add("/" + b + " " + u);
                if (l.size() >= 400) break;
            }
        }
        if (l.isEmpty() && !b.isEmpty()) l.add("/" + b);
        usages = l;
        defilUsage = 0;
    }

    /** Indication des valeurs possibles à l'étape en cours : « <cible> · <objet> », plus les mots-clés. */
    private String indication() {
        CommandNode<SharedSuggestionProvider> n = noeud(base());
        if (n == null) return t("etape_invalide");
        List<String> args = new ArrayList<>();
        int mots = 0;
        for (CommandNode<SharedSuggestionProvider> c : n.getChildren()) {
            if (c instanceof ArgumentCommandNode) args.add(c.getUsageText());
            else mots++;
        }
        if (args.isEmpty() && mots == 0) return t("fin");
        if (args.isEmpty()) return t("choisis_mot", String.valueOf(mots));
        String a = String.join(" · ", args);
        return mots == 0 ? t("valeur", a) : t("valeur_ou_mot", a);
    }

    // ------------------------------------------------------------------ actions
    private void choisir(String racine) {
        commande = racine;
        morceaux.clear();
        morceaux.add(racine);
        valeur.v = "";
        erreur = "";
        defilChoix = 0;
        focus = valeur;
        demandeSur = null;
        usagesSur = null;
        SonsUi.clic(0.6F);
    }

    private void retour() {
        if (morceaux.size() > 1) morceaux.remove(morceaux.size() - 1);
        erreur = "";
        defilChoix = 0;
    }

    private void effacer() {
        if (commande == null) return;
        morceaux.clear();
        morceaux.add(commande);
        valeur.v = "";
        erreur = "";
        defilChoix = 0;
    }

    private void prendre(Choix c) {
        String e = valider(c.valeur());
        if (e != null) {
            // valeur pas encore complète (coordonnées « ~ »…) : on la met dans le champ pour la finir
            valeur.v = c.valeur();
        }
        erreur = "";
        focus = valeur;
        SonsUi.clic(0.6F);
    }

    private void executer() {
        String cmd = commandeFinale();
        ClientPacketListener co = Minecraft.getInstance().getConnection();
        if (co == null || !executable(cmd)) {
            erreur = t("incomplete");
            return;
        }
        co.sendCommand(cmd);
        info = t("envoye", "/" + cmd);
        infoOk = true;
        infoA = System.currentTimeMillis();
        erreur = "";
        if (!valeur.v.isBlank()) {
            morceaux.add(valeur.v.strip());
            valeur.v = "";
        }
        SonsUi.clic(0.8F);
    }

    // ------------------------------------------------------------------ écran
    @Override
    protected void init() {
        Echelle.forcer(this);
        ox = (width - GW) / 2;
        oy = (height - GH) / 2;
        lireRacines();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        Echelle.restaurer();
        super.removed();
    }

    @Override
    public void tick() {
        lireRacines();
    }

    private List<String> filtrees(String categorie) {
        String q = recherche.v.strip().toLowerCase(Locale.ROOT);
        List<String> l = new ArrayList<>();
        for (String r : racines) {
            if (!categorie.equals("toutes") && !categorie(r).equals(categorie)) continue;
            if (!q.isEmpty() && !r.toLowerCase(Locale.ROOT).contains(q)) continue;
            l.add(r);
        }
        return l;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (b == 0) {
            double x = mx - ox, y = my - oy;
            for (int i = zones.size() - 1; i >= 0; i--) {
                if (zones.get(i).dedans(x, y)) {
                    zones.get(i).action().run();
                    return true;
                }
            }
            focus = null;
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        double x = mx - ox, y = my - oy;
        for (Defil d : defils) {
            if (d.dedans(x, y)) {
                d.action().accept(-delta * 13);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(key, scan, mods);
        boolean entree = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (focus != null) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (focus.v.isEmpty()) {
                    if (focus == valeur) retour();
                } else {
                    focus.v = focus.v.substring(0, focus.v.offsetByCodePoints(focus.v.length(), -1));
                    erreur = "";
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_V && (mods & GLFW.GLFW_MOD_CONTROL) != 0) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null) ajouter(clip.replace("\n", " ").replace("\r", ""));
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            if (commande != null && !choix.isEmpty()) {
                valeur.v = choix.get(0).valeur();
                focus = valeur;
            }
            return true;
        }
        if (entree) {
            if (focus == recherche) {
                List<String> l = filtrees(cat);
                if (!l.isEmpty()) choisir(l.get(0));
            } else if (commande != null) {
                if (!valeur.v.isBlank() && !executable(entree())) {
                    String e = valider(valeur.v);
                    erreur = e == null ? "" : e;
                } else {
                    executer();
                }
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void ajouter(String s) {
        if (focus == null) return;
        StringBuilder b = new StringBuilder(focus.v);
        for (char c : s.toCharArray()) {
            if (c < 32 || c == '§' || b.length() >= focus.max) continue;
            b.append(c);
        }
        focus.v = b.toString();
        erreur = "";
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (focus == null || c < 32 || c == '§') return false;
        ajouter(String.valueOf(c));
        return true;
    }

    private void zone(float x, float y, float w, float h, Runnable r) {
        zones.add(new Zone(x, y, w, h, r));
    }

    private boolean survol(float x, float y, float w, float h) {
        double mx = sx - ox, my = sy - oy;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ dessin
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        sx = mx;
        sy = my;
        zones.clear();
        defils.clear();
        if (commande != null) {
            suggestions();
            if (modeUsage) lireUsages();
        }
        voile(g);
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        Font f = font;
        entete(g, f);
        categories(g, f);
        commandes(g, f);
        detail(g, f);
        pied(g, f);
        g.pose().popPose();
    }

    private void voile(GuiGraphics g) {
        float[] at = {0, 0.24F, 0.46F, 0.62F, 1};
        int[] al = {235, 215, 185, 200, 232};
        for (int i = 0; i < at.length - 1; i++) {
            Trace.gradH(g, width * at[i], 0, width * at[i + 1], height, a(NIGHT, al[i]), a(NIGHT, al[i + 1]));
        }
        Trace.gradV(g, 0, 0, width, height * 0.3F, a(0, 90), a(0, 0));
        Trace.gradV(g, 0, height * 0.6F, width, height, a(0, 0), a(0, 120));
    }

    private static void verre(GuiGraphics g, float x, float y, float w, float h, int alpha) {
        Trace.rect(g, x, y, x + w, y + h, a(GLASS, alpha));
        Trace.bord(g, x, y, w, h, 1 / 3F, a(WHITE, 26));
        Trace.rect(g, x + 1 / 3F, y + 1 / 3F, x + w - 1 / 3F, y + 2 / 3F, a(WHITE, 41));
    }

    private static void filetHaut(GuiGraphics g, float x, float y, float w, int c) {
        float mid = x + w / 2;
        Trace.gradH(g, x + 10, y, mid, y + 2 / 3F, a(c, 0), a(c, 255));
        Trace.gradH(g, mid, y, x + w - 10, y + 2 / 3F, a(c, 255), a(c, 0));
    }

    /** Texte coupé par le début (« …fin ») : on voit la fin de la commande, celle qu'on construit. */
    private static String fin(Font f, String s, P p, float maxw) {
        if (Trace.largeur(f, s, p) <= maxw + 0.05F) return s;
        String r = s;
        while (r.length() > 1 && Trace.largeur(f, "…" + r, p) > maxw) r = r.substring(r.offsetByCodePoints(0, 1));
        return "…" + r;
    }

    private void champ(GuiGraphics g, Font f, Saisie s, float x, float y, float w, float h, String invite, float decalage) {
        boolean actif = focus == s, hv = survol(x, y, w, h);
        Trace.rect(g, x, y, x + w, y + h, a(0, actif ? 150 : hv ? 115 : 90));
        Trace.bord(g, x, y, w, h, 1 / 3F, a(actif ? ACCENT : WHITE, actif ? 200 : hv ? 70 : 40));
        float tx = x + 6 + decalage, cy = y + h / 2, place = w - 12 - decalage;
        if (s.v.isEmpty()) {
            Trace.m(g, f, invite, tx, cy, TEXTE, a(FAINT, 255), place);
        }
        String vu = fin(f, s.v, TEXTE, place - 3);
        float tw = s.v.isEmpty() ? 0 : Trace.m(g, f, vu, tx, cy, TEXTE, a(LIGHT, 255), 0);
        if (actif && (System.currentTimeMillis() / 500) % 2 == 0) {
            Trace.rect(g, tx + tw + 1, cy - 4.5F, tx + tw + 1 + 2 / 3F, cy + 4.5F, a(ACCENT, 230));
        }
        zone(x, y, w, h, () -> focus = s);
    }

    private void entete(GuiGraphics g, Font f) {
        Trace.texte(g, f, Trace.tenir(f, t("titre"), TITRE, SX - M - 12), M, 16, TITRE, a(LIGHT, 255));
        Trace.texte(g, f, Trace.tenir(f, t("sous"), TEXTE, SX - M - 12), M, 33, TEXTE, a(MUTED, 255));
        champ(g, f, recherche, SX, SY, SW, SH, t("rechercher"), 12);
        // loupe
        Trace.anneau(g, SX + 10, SY + SH / 2F - 1, 3.2F, 4.2F, a(focus == recherche ? ACCENT : MUTED, 255));
        Trace.trait(g, SX + 12.8F, SY + SH / 2F + 1.8F, SX + 15, SY + SH / 2F + 4, 1.1F, a(focus == recherche ? ACCENT : MUTED, 255));
        Trace.rect(g, M, 44, GW - M, 44 + 1 / 3F, a(WHITE, 23));
    }

    private void categories(GuiGraphics g, Font f) {
        verre(g, CX, HAUT, CW, DH, 199);
        Trace.texte(g, f, Trace.tenir(f, t("categories"), MINI, CW - 20), CX + 10, HAUT + 9, MINI, a(MUTED, 255));
        for (int i = 0; i < CATS.length; i++) {
            String c = CATS[i];
            float x = CX + 4, y = HAUT + 22 + i * RANG_CAT, w = CW - 8, h = RANG_CAT - 2;
            boolean sel = c.equals(cat), hv = !sel && survol(x, y, w, h);
            if (sel) {
                Trace.gradH(g, x, y, x + w, y + h, a(ACCENT, 60), a(ACCENT, 8));
                Trace.rect(g, x, y + 2, x + 1.33F, y + h - 2, a(ACCENT, 255));
            } else if (hv) {
                Trace.rect(g, x, y, x + w, y + h, a(WHITE, 14));
            }
            String n = String.valueOf(filtrees(c).size());
            float nw = Trace.d(g, f, n, CX + CW - 10, y + h / 2, PETIT, a(sel ? PINK : FAINT, 255), 30);
            Trace.m(g, f, t("cat." + c), CX + 12, y + h / 2, TEXTE_GRAS, a(sel || hv ? WHITE : OPTION, 255), CW - 22 - nw - 6);
            zone(x, y, w, h, () -> {
                if (!cat.equals(c)) {
                    cat = c;
                    defilCmd = 0;
                    SonsUi.clic(0.5F);
                }
            });
        }
    }

    private void commandes(GuiGraphics g, Font f) {
        verre(g, MX, HAUT, MW, DH, 199);
        List<String> l = filtrees(cat);
        Trace.texte(g, f, Trace.tenir(f, t("commandes"), MINI, MW - 50), MX + 10, HAUT + 9, MINI, a(MUTED, 255));
        Trace.d(g, f, String.valueOf(l.size()), MX + MW - 10, HAUT + 11.5F, PETIT, a(FAINT, 255), 36);
        float lx = MX + 4, ly = HAUT + 22, lw = MW - 10, lh = DH - 30;
        maxCmd = Math.max(0, l.size() * RANG_CMD - lh);
        defilCmd = Math.max(0, Math.min(maxCmd, defilCmd));
        defils.add(new Defil(MX, HAUT, MW, DH, d -> defilCmd = Math.max(0, Math.min(maxCmd, defilCmd + (float) d))));
        if (l.isEmpty()) {
            Trace.c(g, f, t("aucune"), MX + MW / 2F, HAUT + DH / 2F, PETIT, a(FAINT, 255), MW - 20);
            return;
        }
        g.enableScissor(ox + Math.round(lx), oy + Math.round(ly), ox + Math.round(lx + lw), oy + Math.round(ly + lh));
        for (int i = 0; i < l.size(); i++) {
            float y = ly + i * RANG_CMD - defilCmd;
            if (y + RANG_CMD < ly || y > ly + lh) continue;
            String r = l.get(i);
            boolean sel = r.equals(commande), hv = !sel && survol(lx, Math.max(ly, y), lw, Math.min(ly + lh, y + RANG_CMD - 1) - Math.max(ly, y)) && survol(lx, ly, lw, lh);
            if (sel) {
                Trace.gradH(g, lx, y, lx + lw, y + RANG_CMD - 1, a(ACCENT, 55), a(ACCENT, 6));
                Trace.rect(g, lx, y + 2, lx + 1.33F, y + RANG_CMD - 3, a(ACCENT, 255));
            } else if (hv) {
                Trace.rect(g, lx, y, lx + lw, y + RANG_CMD - 1, a(WHITE, 14));
            }
            Trace.m(g, f, "/" + r, lx + 8, y + (RANG_CMD - 1) / 2F, TEXTE, a(sel || hv ? WHITE : LIGHT, 255), lw - 14);
            float y0 = Math.max(ly, y), y1 = Math.min(ly + lh, y + RANG_CMD - 1);
            if (y1 > y0) zone(lx, y0, lw, y1 - y0, () -> choisir(r));
        }
        g.disableScissor();
        barre(g, MX + MW - 5, ly, lh, defilCmd, maxCmd);
    }

    private static void barre(GuiGraphics g, float x, float y, float h, float defil, float max) {
        if (max <= 0) return;
        float total = h + max, th = Math.max(12, h * h / total), ty = y + (h - th) * defil / max;
        Trace.rect(g, x, y, x + 1.33F, y + h, a(WHITE, 14));
        Trace.rect(g, x, ty, x + 1.33F, ty + th, a(WHITE, 90));
    }

    /** Petit bouton à contour (Retour, Effacer, onglets) ; rend sa largeur. */
    private float petitBouton(GuiGraphics g, Font f, String lab, float x, float y, float h, boolean actif, boolean choisi, Runnable r) {
        float w = Trace.largeur(f, lab, MINI) + 16;
        boolean hv = actif && survol(x, y, w, h);
        if (choisi) {
            Trace.rect(g, x, y, x + w, y + h, a(ACCENT, 50));
            Trace.bord(g, x, y, w, h, 1 / 3F, a(ACCENT, 150));
        } else {
            Trace.rect(g, x, y, x + w, y + h, a(WHITE, !actif ? 5 : hv ? 20 : 9));
            Trace.bord(g, x, y, w, h, 1 / 3F, a(WHITE, !actif ? 18 : hv ? 70 : 35));
        }
        Trace.c(g, f, lab, x + w / 2, y + h / 2, MINI, a(!actif ? FAINT : choisi || hv ? WHITE : OPTION, 255), 0);
        if (actif) zone(x, y, w, h, r);
        return w;
    }

    private void detail(GuiGraphics g, Font f) {
        verre(g, DX, HAUT, DW, DH, 205);
        filetHaut(g, DX, HAUT, DW, ACCENT);
        Trace.texte(g, f, Trace.tenir(f, t("commande"), MINI, 110), DX + 10, HAUT + 9, MINI, a(MUTED, 255));
        // onglets Assistant / Utilisation, à droite
        String la = t("assistant"), lu = t("utilisation");
        float wu = Trace.largeur(f, lu, MINI) + 16, wa = Trace.largeur(f, la, MINI) + 16;
        float xu = DX + DW - 10 - wu, xa = xu - 4 - wa;
        boolean actif = commande != null;
        petitBouton(g, f, la, xa, HAUT + 5, 13, actif, actif && !modeUsage, () -> modeUsage = false);
        petitBouton(g, f, lu, xu, HAUT + 5, 13, actif, actif && modeUsage, () -> modeUsage = true);
        if (commande == null) {
            Trace.c(g, f, t("choisis"), DX + DW / 2F, HAUT + DH / 2F - 6, TEXTE, a(OPTION, 255), DW - 30);
            Trace.c(g, f, t("permises"), DX + DW / 2F, HAUT + DH / 2F + 7, PETIT, a(FAINT, 255), DW - 30);
            return;
        }
        float x = DX + 10, w = DW - 20;
        Trace.m(g, f, "/" + commande, x, HAUT + 30, NOM, a(WHITE, 255), w);
        // aperçu de la commande entière
        float py = HAUT + 42;
        Trace.rect(g, x, py, x + w, py + 20, a(0, 140));
        Trace.bord(g, x, py, w, 20, 1 / 3F, a(WHITE, 36));
        String apercu = "/" + commandeFinale();
        Trace.m(g, f, fin(f, apercu, TEXTE, w - 14), x + 7, py + 10, TEXTE, a(GOLD, 255), 0);
        float bx = x;
        bx += petitBouton(g, f, t("retour"), bx, HAUT + 68, 15, morceaux.size() > 1, false, this::retour) + 4;
        petitBouton(g, f, t("effacer"), bx, HAUT + 68, 15, morceaux.size() > 1 || !valeur.v.isEmpty(), false, this::effacer);
        float listeY = modeUsage ? HAUT + 92 : HAUT + 133, listeH = HAUT + DH - 46 - listeY;
        if (modeUsage) {
            usages(g, f, x, listeY, w, listeH);
        } else {
            Trace.texte(g, f, Trace.tenir(f, t("etape"), MINI, w), x, HAUT + 90, MINI, a(MUTED, 255));
            Trace.m(g, f, indication(), x, HAUT + 104, PETIT, a(OPTION, 255), w);
            champ(g, f, valeur, x, HAUT + 111, w, 18, t("invite"), 0);
            liste(g, f, x, listeY, w, listeH);
        }
        // état et bouton Exécuter
        String cmd = commandeFinale();
        boolean pret = executable(cmd);
        String etat;
        int ce;
        if (!erreur.isEmpty()) {
            etat = erreur;
            ce = DANGER;
        } else if (!info.isEmpty() && System.currentTimeMillis() - infoA < 6000) {
            etat = info;
            ce = infoOk ? OK : DANGER;
        } else if (pret) {
            etat = t("prete");
            ce = OK;
        } else {
            etat = t("incomplete");
            ce = FAINT;
        }
        Trace.m(g, f, etat, x, HAUT + DH - 36, PETIT, a(ce, 255), w);
        float ey = HAUT + DH - 28, eh = 20;
        boolean hv = pret && survol(x, ey, w, eh);
        if (pret) {
            Trace.gradH(g, x, ey, x + w, ey + eh, a(ACCENT, 90 + (hv ? 40 : 0)), a(ACCENT, 40 + (hv ? 30 : 0)));
            Trace.bord(g, x, ey, w, eh, 1 / 3F, a(ACCENT, hv ? 230 : 170));
        } else {
            Trace.rect(g, x, ey, x + w, ey + eh, a(WHITE, 8));
            Trace.bord(g, x, ey, w, eh, 1 / 3F, a(WHITE, 26));
        }
        Trace.c(g, f, t("executer"), x + w / 2, ey + eh / 2, BOUTON, a(pret ? WHITE : FAINT, 255), w - 10);
        if (pret) zone(x, ey, w, eh, this::executer);
    }

    private void liste(GuiGraphics g, Font f, float x, float y, float w, float h) {
        maxChoix = Math.max(0, choix.size() * RANG_CHOIX - h);
        defilChoix = Math.max(0, Math.min(maxChoix, defilChoix));
        defils.add(new Defil(x, y, w, h, d -> defilChoix = Math.max(0, Math.min(maxChoix, defilChoix + (float) d))));
        Trace.rect(g, x, y, x + w, y + h, a(0, 60));
        if (choix.isEmpty()) {
            Trace.c(g, f, t("pas_de_suggestion"), x + w / 2, y + h / 2, PETIT, a(FAINT, 255), w - 12);
            return;
        }
        g.enableScissor(ox + Math.round(x), oy + Math.round(y), ox + Math.round(x + w), oy + Math.round(y + h));
        for (int i = 0; i < choix.size(); i++) {
            float ry = y + i * RANG_CHOIX - defilChoix;
            if (ry + RANG_CHOIX < y || ry > y + h) continue;
            Choix c = choix.get(i);
            float y0 = Math.max(y, ry), y1 = Math.min(y + h, ry + RANG_CHOIX);
            boolean hv = y1 > y0 && survol(x, y0, w - 4, y1 - y0);
            if (hv) Trace.rect(g, x, ry, x + w - 4, ry + RANG_CHOIX, a(WHITE, 16));
            else if (i % 2 == 0) Trace.rect(g, x, ry, x + w - 4, ry + RANG_CHOIX, a(WHITE, 5));
            float iw = c.info().isEmpty() ? 0 : Math.min(90, Trace.largeur(f, c.info(), PETIT));
            if (iw > 0) Trace.d(g, f, c.info(), x + w - 10, ry + RANG_CHOIX / 2F, PETIT, a(c.motCle() ? PINK : FAINT, 255), 90);
            Trace.m(g, f, c.texte(), x + 6, ry + RANG_CHOIX / 2F, c.motCle() ? TEXTE_GRAS : TEXTE, a(hv ? WHITE : LIGHT, 255), w - 22 - iw);
            if (y1 > y0) zone(x, y0, w - 4, y1 - y0, () -> prendre(c));
        }
        g.disableScissor();
        barre(g, x + w - 2, y, h, defilChoix, maxChoix);
    }

    private void usages(GuiGraphics g, Font f, float x, float y, float w, float h) {
        maxUsage = Math.max(0, usages.size() * RANG_USAGE - h);
        defilUsage = Math.max(0, Math.min(maxUsage, defilUsage));
        defils.add(new Defil(x, y, w, h, d -> defilUsage = Math.max(0, Math.min(maxUsage, defilUsage + (float) d))));
        Trace.rect(g, x, y, x + w, y + h, a(0, 60));
        g.enableScissor(ox + Math.round(x), oy + Math.round(y), ox + Math.round(x + w), oy + Math.round(y + h));
        for (int i = 0; i < usages.size(); i++) {
            float ry = y + i * RANG_USAGE - defilUsage;
            if (ry + RANG_USAGE < y || ry > y + h) continue;
            if (i % 2 == 0) Trace.rect(g, x, ry, x + w - 4, ry + RANG_USAGE, a(WHITE, 5));
            Trace.m(g, f, usages.get(i), x + 6, ry + RANG_USAGE / 2F, PETIT, a(OPTION, 255), w - 14);
        }
        g.disableScissor();
        barre(g, x + w - 2, y, h, defilUsage, maxUsage);
    }

    private void pied(GuiGraphics g, Font f) {
        float cy = 347, x = M;
        String[][] touches = {{"entree", "valider"}, {"tab", "completer"}, {"molette", "defiler"}, {"echap", "fermer"}};
        for (String[] k : touches) {
            String kl = t("touche." + k[0]).toUpperCase(Locale.ROOT);
            float tw = Trace.largeur(f, kl, MINI_NUM), w = Math.max(9, tw + 4.67F);
            Trace.rect(g, x, cy - 4.5F, x + w, cy + 4.5F, a(WHITE, 30));
            Trace.bord(g, x, cy - 4.5F, w, 9, 1 / 3F, a(WHITE, 61));
            Trace.c(g, f, kl, x + w / 2, cy, MINI_NUM, a(WHITE, 255), 0);
            x += w + 3.5F;
            x += Trace.m(g, f, t("pied." + k[1]), x, cy, PETIT, a(MUTED, 255), 0) + 10;
        }
        Trace.d(g, f, t("note"), GW - M, cy, PETIT, a(FAINT, 255), GW - M - x - 8);
    }
}
