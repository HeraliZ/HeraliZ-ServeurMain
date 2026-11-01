package fr.heraliz.banc;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Commandes jouées par les bots comme de vrais joueurs (01/11, banc de charge de GitHub) : {@code /bots commandes
 * <secondes>} (0 = arrêt) ; chaque bot tape en moyenne une commande toutes les {@code secondes}, tirée au hasard dans
 * {@code config/heraliz_banc/commandes.txt} (une par ligne, sans « / », {@code #} = commentaire ; une ligne répétée
 * sort plus souvent), relu à chaque réglage, sinon dans {@link #DEFAUT}.
 * <p>Comme une commande tapée dans le chat : jouée avec les droits du bot (pas ceux de la console), donc avec les refus
 * du noyau (carte, combat, à terre), et entre deux ticks (file des tâches du serveur, comme les paquets des joueurs), pas
 * au milieu du tick. Chaque commande est chronométrée : nombre, réussites, temps total et la plus lente, par commande
 * ({@link Mesure} les écrit dans son rapport). Rien n'est jamais écrit sur le disque.
 */
final class Commandes {
    private Commandes() {
    }

    /** Commandes courantes des joueurs (permises ou refusées sur la carte : les deux chemins comptent). */
    static final List<String> DEFAUT = List.of("clan", "clan info", "clan top", "saison", "saison classement", "extractions",
            "event", "quetes", "journalieres", "home", "base info", "purge", "herals", "spawn", "kit", "warp");

    /** Période moyenne entre deux commandes d'un même bot, en ticks ; 0 = les bots ne tapent rien. */
    static int periode;
    /** Change à chaque réglage : chaque bot retire alors son délai au hasard (sinon tous tapent au même tick). */
    static int version;
    private static List<String> liste = DEFAUT;

    static long jouees, reussies, nanos, nanosMax;
    static String plusLente = "";
    /** Par commande (premier mot) : {nombre, nanos, nanos au plus}. */
    static final Map<String, long[]> RACINES = new LinkedHashMap<>();

    /** Règle la période ({@code secondes}, 0 = arrêt) et relit la liste ; nombre de commandes de la liste. */
    static int regler(int secondes) {
        periode = Math.max(0, secondes) * 20;
        version++;
        if (periode > 0) liste = lire();
        return liste.size();
    }

    private static List<String> lire() {
        Path f = FMLPaths.CONFIGDIR.get().resolve("heraliz_banc/commandes.txt");
        if (!Files.isRegularFile(f)) return DEFAUT;
        List<String> l = new ArrayList<>();
        try {
            for (String s : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                s = s.strip();
                if (s.startsWith("/")) s = s.substring(1).strip();
                if (!s.isEmpty() && !s.startsWith("#")) l.add(s);
            }
        } catch (Exception ex) {
            HeralizBanc.LOG.warn("[bots] {} illisible : commandes par défaut", f, ex);
            return DEFAUT;
        }
        HeralizBanc.LOG.info("[bots] {} commande(s) lue(s) dans {}", l.size(), f);
        return l.isEmpty() ? DEFAUT : List.copyOf(l);
    }

    /** Le bot « tape » une commande au hasard : elle part dans la file des tâches du serveur, jouée après le tick. */
    static void jouer(ServerPlayer b, RandomSource r) {
        List<String> l = liste;
        if (l.isEmpty()) return;
        String cmd = l.get(r.nextInt(l.size()));
        MinecraftServer srv = b.server;
        srv.tell(new TickTask(srv.getTickCount(), () -> executer(b, cmd)));
    }

    private static void executer(ServerPlayer b, String cmd) {
        if (!b.isAlive() || b.hasDisconnected()) return;
        long t0 = System.nanoTime();
        int res;
        try {
            res = b.server.getCommands().performPrefixedCommand(b.createCommandSourceStack(), cmd);
        } catch (RuntimeException ex) {
            res = 0;
            HeralizBanc.LOG.warn("[bots] commande « {} » de {} en erreur", cmd, b.getGameProfile().getName(), ex);
        }
        long dt = System.nanoTime() - t0;
        jouees++;
        if (res > 0) reussies++;
        nanos += dt;
        if (dt > nanosMax) {
            nanosMax = dt;
            plusLente = cmd;
        }
        int sp = cmd.indexOf(' ');
        String racine = (sp < 0 ? cmd : cmd.substring(0, sp)).toLowerCase(Locale.ROOT);
        long[] s = RACINES.computeIfAbsent(racine, k -> new long[3]);
        s[0]++;
        s[1] += dt;
        if (dt > s[2]) s[2] = dt;
    }

    /** Remet les comptes à zéro (début d'une mesure). */
    static void zero() {
        jouees = reussies = nanos = nanosMax = 0;
        plusLente = "";
        RACINES.clear();
    }
}
