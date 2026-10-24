package fr.heralisz.utils.diag;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.heralisz.utils.HeraliszUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Diagnostic du serveur (HeraliZ Utils 2.27.0, demande du propriétaire après l'ouverture du 24/10 : « un mod comme
 * spark, ultra précis, qui nous dit où est le problème »). Serveur seulement.
 * <ul>
 * <li>Chien de garde des ticks ({@link ChienDeGarde}) : un tick (ou un travail entre deux ticks) plus long que
 * {@code seuil_ms} est échantillonné jusqu'à sa fin ; rapport « ticks longs » (regroupés) ou « blocage » (au-delà de
 * {@code blocage_ms}, piles entières et piles de tous les fils). Rien quand tout va bien : le fil dort.</li>
 * <li>{@code /diag profil <secondes>} ({@link Profileur}) : échantillonnage continu, temps par type d'entité, tronçons.</li>
 * <li>Compteurs permanents ({@link Compteurs}, {@link Instantane}) : {@code /diag etat}, et une ligne « [diag] » de
 * résumé dans le journal toutes les 5 min.</li>
 * <li>Rapports : {@code heraliz_diag/} à la racine du serveur ({@link Rapports}), lus par le workflow « Diagnostic du
 * serveur » (.github/workflows/diagnostic.yml).</li>
 * </ul>
 * Commandes : op niveau 4 ou console. Réglages : {@code heraliz_diag-server.toml} (section [diag]).
 */
public final class Diag {
    private Diag() {
    }

    /** Vrai entre le démarrage du serveur (monde chargé) et le début de son arrêt. */
    static volatile boolean enService;
    private static long dernierReleveMs;
    /** Dernières commandes (nom seul : jamais les arguments, où passent les mots de passe d'AuthMe). */
    private static final String[] COMMANDES = new String[12];
    private static int nbCommandes;

    public static void init() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, DiagConfig.SPEC, "heraliz_diag-server.toml");
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, Diag::tickDebut);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, Diag::tickFin);
        MinecraftForge.EVENT_BUS.addListener(Diag::demarre);
        MinecraftForge.EVENT_BUS.addListener(Diag::arret);
        MinecraftForge.EVENT_BUS.addListener(Diag::arrete);
        MinecraftForge.EVENT_BUS.addListener(Diag::commandes);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, Diag::commande);
        MinecraftForge.EVENT_BUS.addListener(Diag::chargeTroncon);
        MinecraftForge.EVENT_BUS.addListener(Diag::lisTroncon);
        MinecraftForge.EVENT_BUS.addListener(Diag::dechargeTroncon);
    }

    // ------------------------------------------------------------------ cycle de vie

    private static void demarre(ServerStartedEvent e) {
        try {
            DiagConfig.rafraichir();
            PausesGc.brancher();
            Rapports.demarrer(e.getServer().getServerDirectory().toPath().resolve("heraliz_diag"));
            ChienDeGarde.demarrer();
            enService = true;
            HeraliszUtils.LOG.info("[diag] diagnostic prêt : chien de garde {} (seuil {} ms, blocage {} ms), rapports dans heraliz_diag/",
                    DiagConfig.actif ? "actif" : "coupé", DiagConfig.seuilMs, DiagConfig.blocageMs);
        } catch (Throwable t) {
            HeraliszUtils.LOG.warn("[diag] diagnostic non démarré : {}", t.toString());
        }
    }

    private static void arret(ServerStoppingEvent e) {
        enService = false;
        Profileur.arreterCourant();
        SuiviEntites.mesure = false;
    }

    private static void arrete(ServerStoppedEvent e) {
        enService = false;
        ChienDeGarde.arreter();
        Rapports.arreter(2000);
    }

    // ------------------------------------------------------------------ fil du serveur : ticks

    private static void tickDebut(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        ChienDeGarde.debutTick();
    }

    private static void tickFin(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        ChienDeGarde.finTick();
        // relevé des entités et tronçons, une fois par minute (une boucle sur les entités, rien d'écrit)
        long now = System.currentTimeMillis();
        if (now - dernierReleveMs >= 60_000L && enService) {
            dernierReleveMs = now;
            try {
                Instantane.prendre(e.getServer());
            } catch (Throwable t) {
                HeraliszUtils.LOG.debug("[diag] relevé impossible : {}", t.toString());
            }
        }
    }

    // ------------------------------------------------------------------ commandes récentes et tronçons

    private static void commande(CommandEvent e) {
        try {
            String s = e.getParseResults().getReader().getString();
            if (s.startsWith("/")) s = s.substring(1);
            int sp = s.indexOf(' ');
            String racine = (sp < 0 ? s : s.substring(0, sp));
            if (racine.length() > 32) racine = racine.substring(0, 32);
            synchronized (COMMANDES) {
                COMMANDES[nbCommandes % COMMANDES.length] = Rapports.heure(System.currentTimeMillis()) + " /" + racine;
                nbCommandes++;
            }
        } catch (Throwable ignored) {
        }
    }

    /** Dernières commandes, de la plus ancienne à la plus récente. */
    static List<String> commandesRecentes() {
        synchronized (COMMANDES) {
            List<String> l = new ArrayList<>();
            int n = Math.min(nbCommandes, COMMANDES.length);
            for (int i = n; i >= 1; i--) l.add(COMMANDES[(nbCommandes - i) % COMMANDES.length]);
            return l;
        }
    }

    // tronçons comptés seulement pendant un profil ; niveau inconnu (tronçon en cours de génération) : compté aussi
    private static void chargeTroncon(ChunkEvent.Load e) {
        if (Profileur.courant != null && (e.getLevel() == null || !e.getLevel().isClientSide())) Profileur.CHARGES.incrementAndGet();
    }

    private static void lisTroncon(ChunkDataEvent.Load e) {
        if (Profileur.courant != null && (e.getLevel() == null || !e.getLevel().isClientSide())) Profileur.LUS.incrementAndGet();
    }

    private static void dechargeTroncon(ChunkEvent.Unload e) {
        if (Profileur.courant != null && (e.getLevel() == null || !e.getLevel().isClientSide())) Profileur.DECHARGES.incrementAndGet();
    }

    // ------------------------------------------------------------------ commandes /diag

    private static void commandes(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("diag").requires(s -> s.hasPermission(4))
                .then(Commands.literal("etat").executes(Diag::etat))
                .then(Commands.literal("profil")
                        .then(Commands.literal("arreter").executes(x -> {
                            if (Profileur.arreterCourant()) ok(x, "heralisz_utils.diag.profil.arrete", "Profil arrêté : le rapport est en cours d'écriture.");
                            else ok(x, "heralisz_utils.diag.profil.aucun", "Aucun profil en cours.");
                            return 1;
                        }))
                        .then(Commands.argument("secondes", IntegerArgumentType.integer(5, 600)).executes(Diag::profil)))
                .then(Commands.literal("rapports").executes(Diag::rapports))
                .then(Commands.literal("essai").then(Commands.argument("ms", IntegerArgumentType.integer(1, 10_000)).executes(x -> {
                    int ms = IntegerArgumentType.getInteger(x, "ms");
                    ok(x, "heralisz_utils.diag.essai", "Essai : le serveur s'arrête %s ms (le chien de garde doit écrire un rapport).", ms);
                    HeraliszUtils.LOG.warn("[diag] essai : fil du serveur arrêté {} ms à la demande", ms);
                    try {
                        Thread.sleep(ms);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    return 1;
                }))));
    }

    private static int profil(CommandContext<CommandSourceStack> x) {
        int s = IntegerArgumentType.getInteger(x, "secondes");
        Profileur deja = Profileur.lancer(x.getSource().getServer(), x.getSource(), s);
        if (deja != null) {
            ok(x, "heralisz_utils.diag.profil.deja", "Un profil est déjà en cours (%s s restantes).", deja.resteS());
            return 0;
        }
        ok(x, "heralisz_utils.diag.profil.lance", "Profil lancé pour %s s (un échantillon toutes les %s ms). Rapport à la fin dans heraliz_diag/.",
                s, DiagConfig.profilIntervalleMs);
        return 1;
    }

    private static int rapports(CommandContext<CommandSourceStack> x) {
        List<String> l = Rapports.derniers();
        if (l.isEmpty()) {
            ok(x, "heralisz_utils.diag.rapports.aucun", "Aucun rapport écrit depuis le démarrage.");
            return 0;
        }
        List<String> fin = l.subList(Math.max(0, l.size() - 8), l.size());
        ok(x, "heralisz_utils.diag.rapports.liste", "Derniers rapports (dossier heraliz_diag) : %s", String.join(", ", fin));
        return l.size();
    }

    private static int etat(CommandContext<CommandSourceStack> x) {
        MinecraftServer srv = x.getSource().getServer();
        int[] fenetres = {1, 5, 15};
        for (int m : fenetres) {
            Compteurs.Stats s = Compteurs.fenetre(m * 60_000L);
            ok(x, "heralisz_utils.diag.etat.ticks", "Ticks (%s min) : moyenne %s ms, 95 %% sous %s ms, pire %s ms, %s TPS ; %s au-delà de 50 ms",
                    m, f1(s.moyenne()), f1(s.p95()), f0(s.pire()), f1(s.tps()), s.auDela50());
        }
        Instantane i = Instantane.prendre(srv);
        ok(x, "heralisz_utils.diag.etat.serveur", "Joueurs : %s ; entités : %s ; tronçons chargés : %s", i.joueurs(), i.entites(), i.troncons());
        for (Instantane.Monde m : i.mondes()) {
            ok(x, "heralisz_utils.diag.etat.monde", "%s : %s entités, %s tronçons chargés", m.nom(), m.entites(), m.troncons());
        }
        ok(x, "heralisz_utils.diag.etat.types", "Entités les plus nombreuses : %s", i.plusNombreux(8));
        long now = System.currentTimeMillis();
        List<PausesGc.Pause> gc = PausesGc.entre(now - 15 * 60_000L, now);
        long somme = 0;
        for (PausesGc.Pause p : gc) somme += p.dureeMs();
        Runtime rt = Runtime.getRuntime();
        ok(x, "heralisz_utils.diag.etat.memoire", "Mémoire : %s / %s Mo ; ramasse-miettes (15 min) : %s ms, pire pause %s ms",
                (rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20, somme, PausesGc.pire(gc));
        List<String> derniers = Rapports.derniers();
        Component actif = DiagConfig.actif ? Component.translatableWithFallback("heralisz_utils.diag.actif", "actif")
                : Component.translatableWithFallback("heralisz_utils.diag.coupe", "coupé");
        ok(x, "heralisz_utils.diag.etat.chien", "Chien de garde : %s (seuil %s ms, blocage %s ms) ; rapports écrits : %s ; dernier : %s",
                actif, DiagConfig.seuilMs, DiagConfig.blocageMs, Rapports.ecrits, derniers.isEmpty() ? "-" : derniers.get(derniers.size() - 1));
        Profileur p = Profileur.courant;
        if (p != null) ok(x, "heralisz_utils.diag.profil.deja", "Un profil est déjà en cours (%s s restantes).", p.resteS());
        return 1;
    }

    private static String f1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String f0(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    private static void ok(CommandContext<CommandSourceStack> x, String cle, String fr, Object... args) {
        Component c = Component.translatableWithFallback(cle, fr, args).withStyle(ChatFormatting.GRAY);
        x.getSource().sendSuccess(() -> c, false);
    }
}
