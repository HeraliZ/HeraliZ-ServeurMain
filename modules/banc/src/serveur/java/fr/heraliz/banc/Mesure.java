package fr.heraliz.banc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mesure du serveur pendant un banc de charge (01/11, workflow « Banc de charge » de GitHub) : {@code /bots mesure
 * <secondes>} (op 4 ou console), {@code /bots mesure stop} pour finir avant. Ne lance rien et ne change rien : les bots
 * déjà là continuent ({@code /bots lancer}), le profil de {@code /diag} se lance à part.
 * <ul>
 *     <li>durée de CHAQUE tick (comme {@link Scenario}) : moyenne, médiane, 95 %, 99 %, pire ; ticks de plus de 50 et
 *     100 ms ; vrai TPS (ticks faits / secondes écoulées, travail entre deux ticks compris) et plus long écart entre deux
 *     débuts de tick ;</li>
 *     <li>toutes les 5 s : mémoire, processeur, entités, monstres, zombies HeraliZ, objets au sol, tronçons et joueurs par
 *     monde, bots en vie ;</li>
 *     <li>pendant la mesure : tronçons chargés (et créés, si Forge le dit), ramasse-miettes (nombre, temps, mémoire gardée
 *     après), actions des bots, commandes tapées par les bots ({@link Commandes}).</li>
 * </ul>
 * À la fin : rapport {@code diag/banc-<date>.txt} et UNE ligne {@code [banc] MESURE {json}} dans le journal, lue par
 * {@code outils/banc_charge.py}. Coût hors mesure : un test de booléen par tick.
 * <p>01/11 (banc réaliste) : la mesure dit aussi la machine Java qui tourne vraiment ({@link Jvm} : arguments réels,
 * mémoire maximale, distances), ce que coûte le banc lui-même à part de ce que coûteraient de vrais joueurs
 * ({@link Couts} : « ms par tick hors banc »), et les rythmes vrais pendant la mesure (morts, réapparitions, connexions,
 * téléportations, tirs : par minute et ramenés à 150 joueurs, {@link Rythmes}).
 */
final class Mesure {
    private Mesure() {
    }

    private static final DateTimeFormatter NOM = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final ResourceLocation ZOMBIE = new ResourceLocation("heralisz_utils", "zombie_survie");

    private static boolean actif;
    private static long debutMs, finMs, prochainReleve;
    private static UUID demandeur;
    private static double[] ticks = new double[8192];
    private static int nbTicks;
    private static long debutTick, debutPrecedent;
    private static double ecartMax;
    private static final List<String> CHRONO = new ArrayList<>();
    /** Par monde : {entités, monstres, zombies, objets au sol, tronçons, joueurs} au plus, puis au dernier relevé. */
    private static final Map<String, int[]> MONDES = new LinkedHashMap<>();
    private static long memMax, memSomme, apresGcMax;
    private static int releves, entitesMax, monstresMax, zombiesMax, objetsMax, tronconsMax, joueursMax, botsMax;
    private static double cpuSomme, cpuMax;
    private static int cpuReleves;
    private static long gcNbDebut, gcMsDebut;
    private static long[] actionsDebut = new long[0];
    private static long[] coutsDebut = new long[0], rythmesDebut = new long[0];
    private static final AtomicLong CHARGES = new AtomicLong(), NEUFS = new AtomicLong();

    @Nullable
    private static EntityType<?> zombie;
    private static boolean zombieCherche, neufCherche;
    @Nullable
    private static Method estNeuf;

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (TickEvent.ServerTickEvent e) -> {
            if (e.phase != TickEvent.Phase.START || !actif) return;
            long t = System.nanoTime();
            if (debutPrecedent != 0) ecartMax = Math.max(ecartMax, (t - debutPrecedent) / 1_000_000.0);
            debutPrecedent = t;
            debutTick = t;
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END && actif) tick(e.getServer());
        });
        MinecraftForge.EVENT_BUS.addListener((ChunkEvent.Load e) -> {
            if (!actif || !(e.getLevel() instanceof ServerLevel)) return;
            CHARGES.incrementAndGet();
            if (neuf(e)) NEUFS.incrementAndGet();
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            if (actif) terminer(e.getServer());
        });
    }

    /** Début d'une mesure de {@code sec} secondes ; une mesure en cours est d'abord terminée (avec son rapport). */
    static int lancer(CommandSourceStack s, int sec) {
        MinecraftServer srv = s.getServer();
        if (actif) terminer(srv);
        demandeur = s.getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
        nbTicks = 0;
        dernierIndex = 0;
        ecartMax = 0;
        debutPrecedent = 0;
        CHRONO.clear();
        MONDES.clear();
        memMax = memSomme = apresGcMax = 0;
        releves = entitesMax = monstresMax = zombiesMax = objetsMax = tronconsMax = joueursMax = botsMax = 0;
        cpuSomme = cpuMax = 0;
        cpuReleves = 0;
        CHARGES.set(0);
        NEUFS.set(0);
        long[] gc = gc();
        gcNbDebut = gc[0];
        gcMsDebut = gc[1];
        actionsDebut = Bots.stats.totaux();
        coutsDebut = Couts.releve();
        rythmesDebut = Rythmes.COMPTES.clone();
        Rythmes.facteurMoyen();
        Commandes.zero();
        debutMs = System.currentTimeMillis();
        finMs = debutMs + sec * 1000L;
        prochainReleve = debutMs + 5000;
        CHRONO.add(String.format(Locale.ROOT, "%6s  %5s  %7s  %6s  %8s  %5s  %8s  %8s  %8s  %8s", "temps", "bots", "ms moy", "ms max",
                "mémoire", "cpu", "entités", "monstres", "zombies", "tronçons"));
        debutTick = System.nanoTime();
        actif = true;
        HeralizBanc.LOG.info("[banc] mesure lancee : {} s, {} bots", sec, Bots.nombre());
        return 1;
    }

    static int arreter(CommandSourceStack s) {
        if (!actif) {
            s.sendFailure(Component.translatableWithFallback("heralisz_utils.bots.scenario_aucun", "Aucun scénario en cours."));
            return 0;
        }
        terminer(s.getServer());
        return 1;
    }

    private static void tick(MinecraftServer srv) {
        if (nbTicks == ticks.length) ticks = Arrays.copyOf(ticks, ticks.length * 2);
        ticks[nbTicks++] = (System.nanoTime() - debutTick) / 1_000_000.0;
        long maintenant = System.currentTimeMillis();
        if (maintenant >= prochainReleve) {
            prochainReleve += 5000;
            long t0 = System.nanoTime();
            releve(srv, maintenant);
            Couts.banc += System.nanoTime() - t0;
        }
        if (maintenant >= finMs) terminer(srv);
    }

    // ------------------------------------------------------------------ relevé toutes les 5 s

    private static int dernierIndex;

    private static void releve(MinecraftServer srv, long maintenant) {
        Runtime rt = Runtime.getRuntime();
        long mem = (rt.totalMemory() - rt.freeMemory()) >> 20;
        memMax = Math.max(memMax, mem);
        memSomme += mem;
        releves++;
        apresGcMax = Math.max(apresGcMax, apresGc());
        double cpu = cpu();
        if (cpu >= 0) {
            cpuSomme += cpu;
            cpuMax = Math.max(cpuMax, cpu);
            cpuReleves++;
        }
        EntityType<?> z = zombie();
        int ent = 0, mon = 0, zom = 0, obj = 0, tro = 0, jou = 0;
        for (ServerLevel l : srv.getAllLevels()) {
            int e1 = 0, m1 = 0, z1 = 0, o1 = 0;
            for (Entity e : l.getAllEntities()) {
                if (e == null) continue;
                e1++;
                EntityType<?> t = e.getType();
                if (t == z) z1++;
                if (t.getCategory() == MobCategory.MONSTER) m1++;
                if (e instanceof ItemEntity) o1++;
            }
            int t1 = l.getChunkSource().getLoadedChunksCount(), p1 = l.players().size();
            int[] m = MONDES.computeIfAbsent(l.dimension().location().toString(), k -> new int[12]);
            int[] v = {e1, m1, z1, o1, t1, p1};
            for (int i = 0; i < 6; i++) {
                m[i] = Math.max(m[i], v[i]);
                m[6 + i] = v[i];
            }
            ent += e1;
            mon += m1;
            zom += z1;
            obj += o1;
            tro += t1;
            jou += p1;
        }
        entitesMax = Math.max(entitesMax, ent);
        monstresMax = Math.max(monstresMax, mon);
        zombiesMax = Math.max(zombiesMax, zom);
        objetsMax = Math.max(objetsMax, obj);
        tronconsMax = Math.max(tronconsMax, tro);
        joueursMax = Math.max(joueursMax, jou);
        int bots = (int) Bots.vivants();
        botsMax = Math.max(botsMax, bots);
        double somme = 0, max = 0;
        for (int i = dernierIndex; i < nbTicks; i++) {
            somme += ticks[i];
            max = Math.max(max, ticks[i]);
        }
        int n = nbTicks - dernierIndex;
        dernierIndex = nbTicks;
        long s = (maintenant - debutMs) / 1000;
        CHRONO.add(String.format(Locale.ROOT, "%3d:%02d  %5d  %7.1f  %6.0f  %5d Mo  %4.0f%%  %8d  %8d  %8d  %8d", s / 60, s % 60, bots,
                n == 0 ? 0 : somme / n, max, mem, cpu < 0 ? 0 : cpu * 100, ent, mon, zom, tro));
    }

    // ------------------------------------------------------------------ fin

    private static double centile(double[] tri, double p) {
        if (tri.length == 0) return 0;
        return tri[Math.max(0, Math.min(tri.length - 1, (int) Math.ceil(p / 100.0 * tri.length) - 1))];
    }

    private static double arrondi(double d) {
        return Math.round(d * 10) / 10.0;
    }

    private static void terminer(MinecraftServer srv) {
        actif = false;
        dernierIndex = 0;
        long fin = System.currentTimeMillis();
        double duree = Math.max(0.001, (fin - debutMs) / 1000.0);
        double[] tri = Arrays.copyOf(ticks, nbTicks);
        Arrays.sort(tri);
        double somme = 0;
        int lents50 = 0, lents100 = 0;
        for (double d : tri) {
            somme += d;
            if (d > 50) lents50++;
            if (d > 100) lents100++;
        }
        double moy = nbTicks == 0 ? 0 : somme / nbTicks;
        double p50 = centile(tri, 50), p95 = centile(tri, 95), p99 = centile(tri, 99), max = tri.length == 0 ? 0 : tri[tri.length - 1];
        double tps = Math.min(20.0, nbTicks / duree);
        long[] gc = gc();
        long gcNb = gc[0] - gcNbDebut, gcMs = gc[1] - gcMsDebut;
        long[] actions = Bots.stats.totaux();
        int coeurs = Runtime.getRuntime().availableProcessors();
        long memJava = Runtime.getRuntime().maxMemory() >> 20;
        long neufs = estNeuf == null && neufCherche ? -1 : NEUFS.get();
        String nom = "banc-" + LocalDateTime.now().format(NOM) + ".txt";

        JsonObject j = new JsonObject();
        j.addProperty("bots", Bots.nombre());
        j.addProperty("bots_max", botsMax);
        j.addProperty("secondes", arrondi(duree));
        j.addProperty("ticks", nbTicks);
        j.addProperty("ms_moyenne", arrondi(moy));
        j.addProperty("ms_mediane", arrondi(p50));
        j.addProperty("ms_p95", arrondi(p95));
        j.addProperty("ms_p99", arrondi(p99));
        j.addProperty("ms_max", arrondi(max));
        j.addProperty("ticks_plus_50ms", lents50);
        j.addProperty("ticks_plus_100ms", lents100);
        j.addProperty("tps_reel", Math.round(tps * 100) / 100.0);
        j.addProperty("ecart_max_ms", arrondi(ecartMax));
        j.addProperty("memoire_max_mo", memMax);
        j.addProperty("memoire_moyenne_mo", releves == 0 ? 0 : memSomme / releves);
        j.addProperty("memoire_apres_gc_max_mo", apresGcMax);
        j.addProperty("memoire_java_mo", memJava);
        j.addProperty("gc_nombre", gcNb);
        j.addProperty("gc_ms", gcMs);
        j.addProperty("cpu_moyen_pct", cpuReleves == 0 ? -1 : arrondi(cpuSomme / cpuReleves * 100));
        j.addProperty("cpu_max_pct", cpuReleves == 0 ? -1 : arrondi(cpuMax * 100));
        j.addProperty("coeurs", coeurs);
        j.addProperty("entites_max", entitesMax);
        j.addProperty("monstres_max", monstresMax);
        j.addProperty("zombies_max", zombiesMax);
        j.addProperty("objets_sol_max", objetsMax);
        j.addProperty("troncons_max", tronconsMax);
        j.addProperty("joueurs_max", joueursMax);
        j.addProperty("troncons_charges", CHARGES.get());
        j.addProperty("troncons_neufs", neufs);
        JsonObject mondes = new JsonObject();
        for (Map.Entry<String, int[]> e : MONDES.entrySet()) {
            int[] m = e.getValue();
            JsonObject o = new JsonObject();
            String[] cles = {"entites", "monstres", "zombies", "objets_sol", "troncons", "joueurs"};
            for (int i = 0; i < cles.length; i++) {
                o.addProperty(cles[i] + "_max", m[i]);
                o.addProperty(cles[i] + "_fin", m[6 + i]);
            }
            mondes.add(e.getKey(), o);
        }
        j.add("mondes", mondes);
        JsonObject act = new JsonObject();
        for (int i = 0; i < Reglages.Stats.NOMS.length && i < actions.length; i++) {
            act.addProperty(Reglages.Stats.NOMS[i], actions[i] - (i < actionsDebut.length ? actionsDebut[i] : 0));
        }
        j.add("actions", act);
        JsonObject cmd = new JsonObject();
        cmd.addProperty("jouees", Commandes.jouees);
        cmd.addProperty("reussies", Commandes.reussies);
        cmd.addProperty("ms_total", arrondi(Commandes.nanos / 1e6));
        cmd.addProperty("ms_max", arrondi(Commandes.nanosMax / 1e6));
        cmd.addProperty("plus_lente", Commandes.plusLente);
        JsonObject racines = new JsonObject();
        for (Map.Entry<String, long[]> e : Commandes.RACINES.entrySet()) {
            long[] v = e.getValue();
            JsonArray a = new JsonArray();
            a.add(v[0]);
            a.add(arrondi(v[1] / 1e6));
            a.add(arrondi(v[2] / 1e6));
            racines.add(e.getKey(), a);
        }
        cmd.add("par_commande", racines);
        j.add("commandes", cmd);

        // ---- machine Java, coût du banc à part, rythmes (01/11)
        JsonObject jvm = Jvm.infos(srv);
        j.add("jvm", jvm);
        long[] couts = Couts.releve();
        double parTick = nbTicks == 0 ? 0 : 1.0 / nbTicks / 1_000_000.0;
        double bancMs = (couts[0] - (coutsDebut.length > 0 ? coutsDebut[0] : 0)) * parTick;
        JsonObject co = new JsonObject();
        co.addProperty("banc_ms_tick", Math.round(bancMs * 100) / 100.0);
        co.addProperty("hors_banc_ms_tick", arrondi(Math.max(0, moy - bancMs)));
        JsonObject jo = new JsonObject();
        double joueursMs = 0;
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < Couts.CLES.length; i++) {
            double ms = (couts[i + 1] - (i + 1 < coutsDebut.length ? coutsDebut[i + 1] : 0)) * parTick;
            joueursMs += ms;
            jo.addProperty(Couts.CLES[i], Math.round(ms * 100) / 100.0);
            detail.append(String.format(Locale.ROOT, "  %7.2f ms  %s%n", ms, Couts.NOMS[i]));
        }
        co.add("joueurs_ms_tick", jo);
        co.addProperty("joueurs_total_ms_tick", Math.round(joueursMs * 100) / 100.0);
        j.add("couts", co);
        double minutes = Math.max(1e-3, duree / 60.0);
        double vers150 = 150.0 / Math.max(1, Bots.total());
        JsonObject ry = new JsonObject();
        ry.addProperty("actif", Rythmes.actif);
        ry.addProperty("bots", Bots.total());
        ry.addProperty("minutes", Math.round(minutes * 100) / 100.0);
        JsonObject comptes = new JsonObject(), parMin = new JsonObject(), pour150 = new JsonObject();
        long tele = 0;
        for (int i = 0; i < Rythmes.NOMS.length; i++) {
            long n = Rythmes.COMPTES[i] - (i < rythmesDebut.length ? rythmesDebut[i] : 0);
            comptes.addProperty(Rythmes.NOMS[i], n);
            parMin.addProperty(Rythmes.NOMS[i], Math.round(n / minutes * 100) / 100.0);
            pour150.addProperty(Rythmes.NOMS[i], Math.round(n / minutes * vers150 * 100) / 100.0);
        }
        for (int i : Rythmes.TELEPORTATIONS) tele += Rythmes.COMPTES[i] - (i < rythmesDebut.length ? rythmesDebut[i] : 0);
        long tirs = actions.length > 0 ? actions[0] - (actionsDebut.length > 0 ? actionsDebut[0] : 0) : 0;
        comptes.addProperty("teleportations", tele);
        parMin.addProperty("teleportations", Math.round(tele / minutes * 100) / 100.0);
        pour150.addProperty("teleportations", Math.round(tele / minutes * vers150 * 100) / 100.0);
        comptes.addProperty("tirs", tirs);
        parMin.addProperty("tirs", Math.round(tirs / minutes));
        pour150.addProperty("tirs", Math.round(tirs / minutes * vers150));
        ry.add("comptes", comptes);
        ry.add("par_minute", parMin);
        ry.add("pour_150", pour150);
        JsonObject cibles = new JsonObject();
        cibles.addProperty("morts", Rythmes.morts);
        cibles.addProperty("connexions", Rythmes.connexions);
        cibles.addProperty("voyages", Rythmes.voyages);
        cibles.addProperty("combat_pct", Rythmes.combat);
        ry.add("cibles_150", cibles);
        double facteur = Rythmes.facteurMoyen();
        ry.addProperty("facteur_degats", Math.round(facteur * 1000) / 1000.0);
        j.add("rythmes", ry);
        j.addProperty("rapport", "diag/" + nom);

        StringBuilder t = new StringBuilder("HeraliZ — mesure du banc de charge (/bots mesure)\n");
        t.append("Date : ").append(LocalDateTime.now()).append('\n');
        t.append(String.format(Locale.ROOT, "Durée : %.0f s ; bots connectés : %d (%d en vie au plus) ; machine : %d cœurs, mémoire Java %d Mo%n%n",
                duree, Bots.nombre(), botsMax, coeurs, memJava));
        t.append("== Ticks ==\n");
        t.append(String.format(Locale.ROOT, "%d ticks : moyenne %.1f ms, médiane %.1f, 95 %% %.1f, 99 %% %.1f, pire %.0f ms%n", nbTicks, moy, p50, p95, p99, max));
        t.append(String.format(Locale.ROOT, "Ticks de plus de 50 ms : %d (%.1f %%), de plus de 100 ms : %d%n", lents50,
                nbTicks == 0 ? 0 : 100.0 * lents50 / nbTicks, lents100));
        t.append(String.format(Locale.ROOT, "Vrai TPS : %.2f (ticks faits / secondes écoulées) ; plus long écart entre deux débuts de tick : %.0f ms%n", tps, ecartMax));
        t.append(String.format(Locale.ROOT, "Objectifs : moyenne <= 30 ms %s, 95 %% <= 40 ms %s, 99 %% < 50 ms %s%n%n", ok(moy <= 30), ok(p95 <= 40), ok(p99 < 50)));
        t.append("== Machine ==\n");
        t.append(String.format(Locale.ROOT, "Mémoire utilisée : %d Mo au plus, %d Mo en moyenne ; gardée après le ramasse-miettes : %d Mo au plus (sur %d Mo)%n",
                memMax, releves == 0 ? 0 : memSomme / releves, apresGcMax, memJava));
        t.append(String.format(Locale.ROOT, "Ramasse-miettes : %d passages, %d ms en tout%n", gcNb, gcMs));
        t.append(cpuReleves == 0 ? "Processeur : pas mesuré\n" : String.format(Locale.ROOT, "Processeur (tout Java, %d cœurs = 100 %%) : %.0f %% en moyenne, %.0f %% au plus%n%n",
                coeurs, cpuSomme / cpuReleves * 100, cpuMax * 100));
        t.append("== Machine Java du serveur ==\n");
        t.append(String.format(Locale.ROOT, "Processus %s (lancé par %s), mémoire max %s Mo, %s ; distances : vue %s, simulation %s%n",
                jvm.get("pid"), jvm.has("parent_programme") ? jvm.get("parent_programme").getAsString() : "?", jvm.get("memoire_max_mo"),
                jvm.get("java").getAsString(), jvm.has("vue") ? jvm.get("vue") : "?", jvm.has("simulation") ? jvm.get("simulation") : "?"));
        t.append("Arguments : ").append(jvm.get("arguments")).append("\n\n");
        t.append("== Coût du banc à part ==\n");
        t.append(String.format(Locale.ROOT, "Banc lui-même (cerveau des bots, préparation, rythmes, relevés) : %.2f ms par tick ; ms par tick hors banc : %.1f%n",
                bancMs, Math.max(0, moy - bancMs)));
        t.append(String.format(Locale.ROOT, "Ce que ferait aussi la connexion de vrais joueurs (compté dans « hors banc ») : %.2f ms par tick%n", joueursMs));
        t.append(detail).append('\n');
        t.append("== Rythmes pendant la mesure (par minute ; pour 150 joueurs) ==\n");
        for (String k : comptes.keySet()) {
            t.append(String.format(Locale.ROOT, "  %-14s %8s  %8s / min  %8s / min pour 150%n", k, comptes.get(k), parMin.get(k), pour150.get(k)));
        }
        t.append(String.format(Locale.ROOT, "Cibles pour 150 joueurs : %.1f morts, %.1f connexions, %.1f voyages par minute, combat %d %% du temps%s ; facteur des dégâts entre bots %.3f%n%n",
                Rythmes.morts, Rythmes.connexions, Rythmes.voyages, Rythmes.combat, Rythmes.actif ? "" : " (rythmes coupés)", facteur));
        t.append("== Monde ==\n");
        t.append(String.format(Locale.ROOT, "Au plus : %d entités, %d monstres, %d zombies HeraliZ, %d objets au sol, %d tronçons chargés, %d joueurs%n",
                entitesMax, monstresMax, zombiesMax, objetsMax, tronconsMax, joueursMax));
        t.append(String.format(Locale.ROOT, "Tronçons chargés pendant la mesure : %d%s%n", CHARGES.get(),
                neufs < 0 ? "" : String.format(Locale.ROOT, " (dont %d créés : %s)", neufs, neufs == 0 ? "aucune création de terrain" : "création de terrain pendant la mesure")));
        for (Map.Entry<String, int[]> e : MONDES.entrySet()) {
            int[] m = e.getValue();
            t.append(String.format(Locale.ROOT, "  %s : au plus %d entités (%d monstres, %d zombies, %d objets), %d tronçons, %d joueurs ; à la fin %d entités, %d tronçons, %d joueurs%n",
                    e.getKey(), m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[10], m[11]));
        }
        t.append("\n== Bots ==\n");
        StringBuilder a = new StringBuilder();
        for (int i = 0; i < Reglages.Stats.NOMS.length && i < actions.length; i++) {
            a.append(i > 0 ? ", " : "").append(actions[i] - (i < actionsDebut.length ? actionsDebut[i] : 0)).append(' ').append(Reglages.Stats.NOMS[i]);
        }
        t.append("Actions : ").append(a).append('\n');
        t.append(String.format(Locale.ROOT, "Commandes tapées : %d (%d réussies), %.1f ms en tout, la plus lente %.1f ms (%s)%n", Commandes.jouees,
                Commandes.reussies, Commandes.nanos / 1e6, Commandes.nanosMax / 1e6, Commandes.plusLente.isEmpty() ? "-" : Commandes.plusLente));
        for (Map.Entry<String, long[]> e : Commandes.RACINES.entrySet()) {
            long[] v = e.getValue();
            t.append(String.format(Locale.ROOT, "  /%s : %d fois, %.2f ms en moyenne, %.1f ms au plus%n", e.getKey(), v[0], v[1] / 1e6 / Math.max(1, v[0]), v[2] / 1e6));
        }
        t.append("\n== Toutes les 5 s ==\n");
        for (String l : CHRONO) t.append(l).append('\n');

        String json = j.toString();
        HeralizBanc.LOG.info("[banc] MESURE {}", json);
        try {
            Path d = srv.getServerDirectory().toPath().resolve("diag");
            Files.createDirectories(d);
            Files.writeString(d.resolve(nom), t.toString(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            HeralizBanc.LOG.warn("[banc] rapport {} non écrit", nom, ex);
        }
        String resume = String.format(Locale.ROOT, "mesure de %.0f s, %d bots : %.1f ms en moyenne, 95 %% %.1f, pire %.0f ms, %.1f TPS, mémoire %d Mo",
                duree, Bots.nombre(), moy, p95, max, tps, memMax);
        Component c = Component.translatableWithFallback("heralisz_utils.bots.scenario_fin", "Scénario terminé : %s — rapport diag/%s", resume, nom);
        ServerPlayer p = demandeur == null ? null : srv.getPlayerList().getPlayer(demandeur);
        if (p != null) p.sendSystemMessage(c);
        else srv.sendSystemMessage(c);
    }

    private static String ok(boolean b) {
        return b ? "atteint" : "PAS atteint";
    }

    // ------------------------------------------------------------------ outils

    @Nullable
    private static EntityType<?> zombie() {
        if (!zombieCherche) {
            zombieCherche = true;
            // registre « par défaut » : une clé inconnue rendrait le cochon, d'où le test d'abord
            zombie = ForgeRegistries.ENTITY_TYPES.containsKey(ZOMBIE) ? ForgeRegistries.ENTITY_TYPES.getValue(ZOMBIE) : null;
        }
        return zombie;
    }

    /** Tronçon tout juste créé (ChunkEvent.Load#isNewChunk, lu par réflexion : rien si Forge ne le dit pas). */
    private static boolean neuf(ChunkEvent.Load e) {
        if (!neufCherche) {
            neufCherche = true;
            try {
                estNeuf = ChunkEvent.Load.class.getMethod("isNewChunk");
            } catch (ReflectiveOperationException | LinkageError ex) {
                estNeuf = null;
            }
        }
        if (estNeuf == null) return false;
        try {
            return Boolean.TRUE.equals(estNeuf.invoke(e));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    /** {nombre de passages, ms} du ramasse-miettes depuis le démarrage. */
    private static long[] gc() {
        long n = 0, ms = 0;
        for (GarbageCollectorMXBean g : ManagementFactory.getGarbageCollectorMXBeans()) {
            n += Math.max(0, g.getCollectionCount());
            ms += Math.max(0, g.getCollectionTime());
        }
        return new long[]{n, ms};
    }

    /** Mémoire gardée après le dernier passage du ramasse-miettes (tas), en Mo. */
    private static long apresGc() {
        long o = 0;
        for (MemoryPoolMXBean m : ManagementFactory.getMemoryPoolMXBeans()) {
            if (m.getType() != MemoryType.HEAP) continue;
            MemoryUsage u = m.getCollectionUsage();
            if (u != null) o += u.getUsed();
        }
        return o >> 20;
    }

    /** Part du processeur prise par Java (0 à 1, tous les cœurs), -1 si inconnue. */
    private static double cpu() {
        try {
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) return os.getProcessCpuLoad();
        } catch (RuntimeException | LinkageError ex) {
            return -1;
        }
        return -1;
    }
}
