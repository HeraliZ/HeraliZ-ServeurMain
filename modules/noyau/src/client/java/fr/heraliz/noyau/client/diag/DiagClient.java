package fr.heraliz.noyau.client.diag;

import com.mojang.blaze3d.platform.GlUtil;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import fr.heraliz.noyau.diag.Profil;
import fr.heraliz.noyau.net.NoyauNet;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Analyse du jeu d'un joueur (24/10, « d'où viennent les plus grosses pertes de performance, serveur et client ») :
 * pendant quelques secondes, durée de chaque image, images lentes (au-delà de 50 ms : relevé de la pile du fil de rendu
 * toutes les 5 ms, attribuée au mod responsable comme sur le serveur), temps de dessin par type d'entité et d'entité de
 * bloc ({@link MesureRendu}). Rapport écrit dans {@code diag/} du dossier du jeu ; envoyé au serveur quand c'est le staff
 * qui l'a demandé ({@code /diag client <joueur>}). Lancé à la main par {@code /diagclient [secondes]}.
 * Rien n'est mesuré en dehors d'un enregistrement.
 */
public final class DiagClient {
    private DiagClient() {
    }

    private static final DateTimeFormatter NOM = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final double LENTE_MS = 50.0;

    private static volatile boolean enCours;
    private static volatile boolean dansImage;
    private static volatile long debutImage;
    private static volatile Thread filRendu;
    private static long debut, fin;
    private static int jeton;
    private static boolean pourServeur;
    private static double[] images = new double[4096];
    private static int nbImages;
    private static final Object VERROU = new Object();
    private static Profil lentes = new Profil();

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (TickEvent.RenderTickEvent e) -> {
            if (e.phase == TickEvent.Phase.START) debutImage();
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (TickEvent.RenderTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) finImage();
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END && enCours && System.currentTimeMillis() >= fin) terminer();
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> e.getDispatcher().register(
                Commands.literal("diagclient").executes(c -> lancer(20, 0, false))
                        .then(Commands.argument("secondes", IntegerArgumentType.integer(5, 120))
                                .executes(c -> lancer(IntegerArgumentType.getInteger(c, "secondes"), 0, false)))));
        NoyauNet.DIAG_DEMANDE.brancher(d -> lancer(d.secondes(), d.jeton(), true));
    }

    private static int lancer(int secondes, int j, boolean serveur) {
        if (enCours) return 0;
        jeton = j;
        pourServeur = serveur;
        nbImages = 0;
        synchronized (VERROU) {
            lentes = new Profil();
        }
        debut = System.currentTimeMillis();
        fin = debut + secondes * 1000L;
        MesureRendu.demarrer();
        enCours = true;
        Thread v = new Thread(DiagClient::veiller, "HeraliZ diag (jeu)");
        v.setDaemon(true);
        v.start();
        dire(serveur ? Component.translatableWithFallback("noyau.diag.client_demande",
                "[Diag] Le staff analyse les performances de ton jeu pendant %s s (images par seconde et temps de dessin, rien d'autre).",
                String.valueOf(secondes))
                : Component.translatableWithFallback("noyau.diag.client_debut", "[Diag] Analyse de ton jeu pendant %s s : joue normalement.",
                String.valueOf(secondes)));
        return 1;
    }

    private static void debutImage() {
        if (!enCours) return;
        filRendu = Thread.currentThread();
        debutImage = System.nanoTime();
        dansImage = true;
        MesureRendu.image();
    }

    private static void finImage() {
        if (!enCours || !dansImage) return;
        dansImage = false;
        double ms = (System.nanoTime() - debutImage) / 1_000_000.0;
        if (nbImages == images.length) images = Arrays.copyOf(images, images.length * 2);
        if (nbImages < 2_000_000) images[nbImages++] = ms;
    }

    private static void veiller() {
        try {
            while (enCours) {
                Thread t = filRendu;
                if (t != null && dansImage && (System.nanoTime() - debutImage) / 1_000_000.0 > LENTE_MS) {
                    StackTraceElement[] pile = t.getStackTrace();
                    synchronized (VERROU) {
                        if (lentes.releves < 4000) lentes.ajouter(pile);
                    }
                    Thread.sleep(5);
                } else {
                    Thread.sleep(10);
                }
            }
        } catch (InterruptedException ignore) {
            // fin du jeu
        }
    }

    private static double centile(double[] tri, double p) {
        if (tri.length == 0) return 0;
        return tri[Math.max(0, Math.min(tri.length - 1, (int) Math.ceil(p / 100.0 * tri.length) - 1))];
    }

    private static void terminer() {
        enCours = false;
        MesureRendu.arreter();
        Minecraft mc = Minecraft.getInstance();
        double secondes = Math.max(0.001, (System.currentTimeMillis() - debut) / 1000.0);
        double[] tri = Arrays.copyOf(images, nbImages);
        Arrays.sort(tri);
        int nLentes = 0;
        for (double d : tri) if (d > LENTE_MS) nLentes++;
        double fps = nbImages / secondes, bas1 = 1000.0 / Math.max(1e-3, centile(tri, 99));
        StringBuilder b = new StringBuilder();
        b.append("Date : ").append(LocalDateTime.now()).append('\n');
        b.append(String.format(Locale.ROOT, "Durée : %.0f s, %d images : %.0f FPS en moyenne, 1 %% le plus lent : %.0f FPS%n", secondes, nbImages, fps, bas1));
        b.append(String.format(Locale.ROOT, "Durée d'une image : médiane %.1f ms, 95 %% %.1f ms, 99 %% %.1f ms, max %.0f ms ; images de plus de 50 ms : %d%n",
                centile(tri, 50), centile(tri, 95), centile(tri, 99), tri.length == 0 ? 0 : tri[tri.length - 1], nLentes));
        b.append("\n== Machine et réglages ==\n");
        try {
            b.append("Processeur : ").append(GlUtil.getCpuInfo()).append('\n');
            b.append("Carte graphique : ").append(GlUtil.getRenderer()).append(" (OpenGL ").append(GlUtil.getOpenGLVersion()).append(")\n");
        } catch (RuntimeException ex) {
            b.append("Machine : inconnue\n");
        }
        Runtime rt = Runtime.getRuntime();
        b.append(String.format(Locale.ROOT, "Mémoire du jeu : %d / %d Mo%n", (rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20));
        b.append("Fenêtre : ").append(mc.getWindow().getWidth()).append(" × ").append(mc.getWindow().getHeight()).append('\n');
        b.append("Distance de vue : ").append(mc.options.renderDistance().get()).append(", graphismes : ").append(mc.options.graphicsMode().get())
                .append(", particules : ").append(mc.options.particles().get()).append(", FPS max : ").append(mc.options.framerateLimit().get())
                .append(", synchronisation verticale : ").append(mc.options.enableVsync().get()).append(", mélange des biomes : ")
                .append(mc.options.biomeBlendRadius().get()).append('\n');
        b.append("Mods chargés : ").append(ModList.get().size()).append('\n');
        if (mc.level != null) {
            b.append("Entités chargées : ").append(mc.level.getEntityCount()).append(" ; ").append(mc.levelRenderer.getEntityStatistics()).append('\n');
            b.append("Tronçons : ").append(mc.levelRenderer.getChunkStatistics()).append(" ; particules : ").append(mc.particleEngine.countParticles()).append('\n');
        }
        b.append('\n');
        table(b, "Dessin des entités de bloc par type (conteneurs de loot, props animés, caisses…)", MesureRendu.BLOCS, nbImages, true);
        table(b, "Dessin des entités par type", MesureRendu.ENTITES, nbImages, false);
        synchronized (VERROU) {
            if (lentes.releves > 0) {
                b.append("== Images lentes : ce que faisait le jeu ==\n");
                lentes.ecrire(b);
            } else {
                b.append("Aucune image de plus de 50 ms : rien à relever.\n");
            }
        }
        String resume = String.format(Locale.ROOT, "%.0f FPS (1 %% bas : %.0f), %d images lentes, dessin le plus lourd : %s", fps, bas1, nLentes,
                plusLourd());
        String texte = b.toString();
        if (texte.length() > NoyauNet.RAPPORT_MAX - 200) texte = texte.substring(0, NoyauNet.RAPPORT_MAX - 200) + "\n[coupé]\n";
        if (pourServeur) NoyauNet.versServeur(new NoyauNet.DiagRapportC2S(jeton, resume.length() > 390 ? resume.substring(0, 390) : resume, texte));
        String nom = "client-" + LocalDateTime.now().format(NOM) + ".txt";
        try {
            Path d = mc.gameDirectory.toPath().resolve("diag");
            Files.createDirectories(d);
            Files.writeString(d.resolve(nom), "HeraliZ — rapport de ton jeu\n" + texte, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            nom = "?";
        }
        dire(Component.translatableWithFallback("noyau.diag.client_fin", "[Diag] %s — rapport dans diag/%s", resume, nom));
    }

    private static String plusLourd() {
        String meilleur = "rien";
        double max = 0;
        for (Map<Object, long[]> m : List.of(MesureRendu.BLOCS, MesureRendu.ENTITES)) {
            for (Map.Entry<Object, long[]> e : m.entrySet()) {
                if (e.getValue()[0] > max) {
                    max = e.getValue()[0];
                    meilleur = nomType(e.getKey());
                }
            }
        }
        return nbImages == 0 ? meilleur : String.format(Locale.ROOT, "%s (%.2f ms par image)", meilleur, max / 1_000_000.0 / nbImages);
    }

    private static String nomType(Object type) {
        ResourceLocation id = type instanceof EntityType<?> t ? ForgeRegistries.ENTITY_TYPES.getKey(t)
                : type instanceof BlockEntityType<?> t ? ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(t) : null;
        return id == null ? String.valueOf(type) : id.toString();
    }

    private static void table(StringBuilder b, String titre, Map<Object, long[]> m, int images, boolean blocs) {
        List<Map.Entry<Object, long[]>> l = new ArrayList<>(m.entrySet());
        l.sort((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0]));
        double total = 0;
        for (Map.Entry<Object, long[]> e : l) total += e.getValue()[0];
        int n = Math.max(1, images);
        b.append("== ").append(titre).append(String.format(Locale.ROOT, " : %.2f ms par image en tout ==%n", total / 1_000_000.0 / n));
        for (int i = 0; i < Math.min(20, l.size()); i++) {
            long[] v = l.get(i).getValue();
            b.append(String.format(Locale.ROOT, "%8.3f ms/image  %7.1f dessinés/image  %6.1f µs chacun  %s%n", v[0] / 1_000_000.0 / n,
                    (double) v[1] / n, v[1] == 0 ? 0 : v[0] / 1000.0 / v[1], nomType(l.get(i).getKey())));
        }
        b.append('\n');
    }

    private static void dire(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(c.copy().withStyle(s -> s.withColor(0x8FD3FF)), false);
    }
}
