package fr.heraliz.banc;

import fr.heraliz.noyau.serveur.diag.Diag;
import fr.heraliz.noyau.serveur.diag.Moniteur;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Scénario de charge (demande du propriétaire, 24/10 : « comme si d'un coup X joueurs se connectaient, qui tirent,
 * construisent des bases, envoient des fumigènes et des frappes ; pendant ce temps notre spark analyse tout ; à la fin
 * tous les bots partent et tout est effacé »).
 * <p>{@code /bots scenario <nombre> [minutes] [rayon]} (3 min et 96 blocs par défaut) autour de l'endroit d'où on la tape
 * (à faire dans le monde du tutoriel {@code crac:bases}, jamais sur la carte) ; {@code /bots scenario stop} pour arrêter
 * avant. Pendant le scénario : les bots arrivent d'un coup, au rythme « intense » ({@link Reglages#intense}), le relevé
 * des piles de {@link Diag} tourne, le chronomètre de Forge mesure entités et entités de bloc ({@link Moniteur}), et une
 * ligne est relevée toutes les 5 s (bots, ms par tick, mémoire, entités, pièces de CRac, actions). À la fin : bots retirés,
 * leurs bases effacées, objets au sol, projectiles, caisses de largage et cadavres des bots retirés dans la zone, puis
 * rapport {@code diag/charge-<date>.txt} (avec les objectifs : moyenne 30 ms, 95 % 40 ms, 99 % 50 ms).
 * Ce sont des bots sans réseau : ils chargent le serveur, pas la connexion (voir {@link BotJoueur}).
 */
final class Scenario {
    private Scenario() {
    }

    private static final DateTimeFormatter NOM = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Set<String> A_NETTOYER = Set.of("airstrike", "heralisz_tactical", "tacz");

    private static boolean actif;
    private static long debutMs, finMs;
    private static UUID demandeur;
    private static ServerLevel niveau;
    private static Vec3 centre;
    private static int rayon, nombre, minutes;
    private static double[] ticks = new double[8192];
    private static int nbTicks;
    private static long debutTick;
    private static final List<String> CHRONO = new ArrayList<>();
    private static long memMax;
    private static int entitesMax, piecesMax;
    private static long tirs, lancers, soins, fouilles, frappes, largages, morts, constructions;
    private static String profil;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.START && actif) debutTick = System.nanoTime();
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END && actif) tick(e.getServer());
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            if (actif) terminer(e.getServer(), false);
        });
    }

    static int lancer(CommandSourceStack s, int n, int min, int r) {
        if (actif) {
            s.sendFailure(Component.translatableWithFallback("heralisz_utils.bots.scenario_deja", "Un scénario est déjà en cours (/bots scenario stop)."));
            return 0;
        }
        MinecraftServer srv = s.getServer();
        demandeur = s.getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
        niveau = s.getLevel();
        centre = s.getPosition();
        rayon = r;
        minutes = min;
        nbTicks = 0;
        CHRONO.clear();
        memMax = 0;
        entitesMax = piecesMax = 0;
        tirs = lancers = soins = fouilles = frappes = largages = morts = constructions = 0;
        profil = null;
        Bots.reglages.intense();
        Bots.stats.zero();
        nombre = Bots.lancer(srv, niveau, centre, n, r);
        debutMs = System.currentTimeMillis();
        finMs = debutMs + min * 60_000L;
        debutTick = System.nanoTime();
        actif = true;
        Diag.lancer(min * 60 + 5, demandeur);
        Moniteur.mesurer(Math.min(300, min * 60));
        CHRONO.add(String.format(Locale.ROOT, "%6s  %5s  %7s  %6s  %8s  %8s  %7s  %s", "temps", "bots", "ms moy", "ms max", "mémoire", "entités",
                "pièces", "actions (5 s)"));
        String monde = niveau.dimension().location().toString();
        s.sendSuccess(() -> Component.translatableWithFallback("heralisz_utils.bots.scenario_lance",
                "Scénario : %s bots pendant %s min (rayon %s, monde %s). Analyse en cours, rapport à la fin.",
                nombre, min, r, monde), false);
        if (monde.equals("minecraft:overworld")) {
            s.sendSuccess(() -> Component.translatableWithFallback("heralisz_utils.bots.scenario_carte",
                    "Attention : c'est la carte. Les bases des bots seront effacées à la fin, mais fais plutôt le test dans crac:bases."), false);
        }
        HeralizBanc.LOG.info("[bots] scénario : {} bots, {} min, rayon {}, {}", nombre, min, r, monde);
        return nombre;
    }

    static int arreter(CommandSourceStack s) {
        if (!actif) {
            s.sendFailure(Component.translatableWithFallback("heralisz_utils.bots.scenario_aucun", "Aucun scénario en cours."));
            return 0;
        }
        terminer(s.getServer(), true);
        return 1;
    }

    private static void tick(MinecraftServer srv) {
        double ms = (System.nanoTime() - debutTick) / 1_000_000.0;
        if (nbTicks == ticks.length) ticks = Arrays.copyOf(ticks, ticks.length * 2);
        ticks[nbTicks++] = ms;
        if (nbTicks % 100 == 0) releve(srv);
        if (System.currentTimeMillis() >= finMs) terminer(srv, true);
    }

    private static void releve(MinecraftServer srv) {
        double somme = 0, max = 0;
        for (int i = nbTicks - 100; i < nbTicks; i++) {
            somme += ticks[i];
            max = Math.max(max, ticks[i]);
        }
        Runtime rt = Runtime.getRuntime();
        long mem = (rt.totalMemory() - rt.freeMemory()) >> 20;
        memMax = Math.max(memMax, mem);
        int ent = 0;
        for (ServerLevel l : srv.getAllLevels()) for (Entity e : l.getAllEntities()) if (e != null) ent++;
        entitesMax = Math.max(entitesMax, ent);
        int pieces = Bots.piecesCrac(srv);
        piecesMax = Math.max(piecesMax, pieces);
        Reglages.Stats st = Bots.stats;
        tirs += st.tirs;
        lancers += st.lancers;
        soins += st.soins;
        fouilles += st.fouilles;
        frappes += st.frappes;
        largages += st.largages;
        morts += st.morts;
        constructions += st.constructions;
        String actions = String.format(Locale.ROOT, "%d tirs, %d lancers, %d soins, %d fouilles, %d frappes, %d largages, %d bases, %d morts",
                st.tirs, st.lancers, st.soins, st.fouilles, st.frappes, st.largages, st.constructions, st.morts);
        st.zero();
        long s = (System.currentTimeMillis() - debutMs) / 1000;
        CHRONO.add(String.format(Locale.ROOT, "%3d:%02d  %5d  %7.1f  %6.0f  %5d Mo  %8d  %7d  %s", s / 60, s % 60, Bots.vivants(), somme / 100, max, mem,
                ent, pieces, actions));
    }

    private static double centile(double[] tri, double p) {
        if (tri.length == 0) return 0;
        return tri[Math.max(0, Math.min(tri.length - 1, (int) Math.ceil(p / 100.0 * tri.length) - 1))];
    }

    private static void terminer(MinecraftServer srv, boolean rapport) {
        actif = false;
        Bots.toutRetirer();
        int nettoyes = nettoyer();
        Bots.reglages.normal();
        profil = Diag.arreterSession(srv);
        if (!rapport) return;
        double[] tri = Arrays.copyOf(ticks, nbTicks);
        Arrays.sort(tri);
        double somme = 0;
        int lents = 0;
        for (double d : tri) {
            somme += d;
            if (d > 50) lents++;
        }
        double moy = nbTicks == 0 ? 0 : somme / nbTicks;
        double p50 = centile(tri, 50), p95 = centile(tri, 95), p99 = centile(tri, 99);
        double max = tri.length == 0 ? 0 : tri[tri.length - 1];
        StringBuilder t = new StringBuilder("HeraliZ — scénario de charge\n");
        t.append("Date : ").append(LocalDateTime.now()).append('\n');
        t.append(String.format(Locale.ROOT, "Bots : %d, durée : %d min, rayon %d autour de %.0f %.0f %.0f (%s)%n", nombre, minutes, rayon,
                centre.x, centre.y, centre.z, niveau.dimension().location()));
        t.append("Bots sans réseau : la charge porte sur le serveur (IA, tirs, explosions, constructions, sauvegardes), pas sur la connexion.\n\n");
        t.append("== Résultat ==\n");
        t.append(String.format(Locale.ROOT, "Ticks : %d ; moyenne %.1f ms, médiane %.1f, 95 %% %.1f, 99 %% %.1f, max %.0f ms ; ticks de plus de 50 ms : %d (%.1f %%)%n",
                nbTicks, moy, p50, p95, p99, max, lents, nbTicks == 0 ? 0 : 100.0 * lents / nbTicks));
        t.append(String.format(Locale.ROOT, "TPS estimé : %.1f%n", Math.min(20.0, 1000.0 / Math.max(50.0, moy))));
        t.append(String.format(Locale.ROOT, "Objectifs : moyenne <= 30 ms %s, 95 %% <= 40 ms %s, 99 %% < 50 ms %s%n", ok(moy <= 30), ok(p95 <= 40), ok(p99 < 50)));
        t.append(String.format(Locale.ROOT, "Mémoire au plus : %d Mo ; entités au plus : %d ; pièces de CRac au plus : %d%n", memMax, entitesMax, piecesMax));
        t.append(String.format(Locale.ROOT, "Actions : %d tirs, %d lancers (grenades, flashs, fumigènes), %d soins, %d fouilles, %d frappes aériennes, %d largages, %d bases bâties, %d morts%n",
                tirs, lancers, soins, fouilles, frappes, largages, constructions, morts));
        t.append("Nettoyage : bots retirés, leurs bases effacées, ").append(nettoyes).append(" entité(s) retirée(s) dans la zone.\n\n");
        t.append("== Toutes les 5 s ==\n");
        for (String l : CHRONO) t.append(l).append('\n');
        t.append('\n');
        Moniteur.ecrireMesure(t);
        t.append("== Autres rapports ==\n");
        if (profil != null) t.append("Profil des piles pendant le scénario : diag/").append(profil).append('\n');
        for (String b : blocages(srv)) t.append("Blocage : diag/").append(b).append('\n');
        String nom = "charge-" + LocalDateTime.now().format(NOM) + ".txt";
        Diag.ecrire(srv, nom, t.toString());
        String resume = String.format(Locale.ROOT, "%d bots : %.1f ms en moyenne, 95 %% %.1f, 99 %% %.1f, max %.0f ms, mémoire %d Mo", nombre, moy, p95, p99, max, memMax);
        Component c = Component.translatableWithFallback("heralisz_utils.bots.scenario_fin", "Scénario terminé : %s — rapport diag/%s", resume, nom);
        ServerPlayer p = demandeur == null ? null : srv.getPlayerList().getPlayer(demandeur);
        if (p != null) p.sendSystemMessage(c);
        else srv.sendSystemMessage(c);
        HeralizBanc.LOG.info("[bots] scénario terminé : {} (diag/{})", resume, nom);
    }

    private static String ok(boolean b) {
        return b ? "atteint" : "PAS atteint";
    }

    /** Objets au sol, projectiles, caisses de largage, grenades et cadavres des bots dans la zone du scénario. */
    private static int nettoyer() {
        if (niveau == null || centre == null) return 0;
        AABB zone = new AABB(centre, centre).inflate(rayon + 64, 256, rayon + 64);
        int n = 0;
        for (Entity e : niveau.getEntitiesOfClass(Entity.class, zone)) {
            if (e instanceof ServerPlayer) continue;
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
            boolean cadavreDeBot = id != null && id.getNamespace().equals("heraliz_corps") && e.getName().getString().contains("HzBot");
            if (e instanceof ItemEntity || e instanceof Projectile || cadavreDeBot || id != null && A_NETTOYER.contains(id.getNamespace())) {
                e.discard();
                n++;
            }
        }
        return n;
    }

    /** Rapports de blocage écrits pendant le scénario. */
    private static List<String> blocages(MinecraftServer srv) {
        List<String> l = new ArrayList<>();
        Path d = Diag.dossier(srv);
        if (!Files.isDirectory(d)) return l;
        try (Stream<Path> s = Files.list(d)) {
            s.filter(f -> f.getFileName().toString().startsWith("blocage-")).forEach(f -> {
                try {
                    if (Files.getLastModifiedTime(f).toMillis() >= debutMs) l.add(f.getFileName().toString());
                } catch (Exception ignore) {
                    // fichier disparu entre-temps
                }
            });
        } catch (Exception ignore) {
            // dossier illisible : pas de liste
        }
        l.sort(null);
        return l;
    }
}
