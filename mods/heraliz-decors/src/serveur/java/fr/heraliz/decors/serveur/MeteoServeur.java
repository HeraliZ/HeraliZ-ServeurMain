package fr.heraliz.decors.serveur;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.heraliz.decors.HeralizDecors;
import fr.heraliz.decors.ambiance.Ambiance;
import java.util.Random;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Brouillard dynamique (1.4.0), côté serveur : un profil à la fois (clair, léger, moyen, fort, purée de pois), tenu
 * de 6 à 25 minutes, puis un autre, tiré au sort avec des poids qui dépendent de l'heure (plus de brume à l'aube et la
 * nuit), de la pluie (plus de brume) et de ce qui vient de passer (après un gros brouillard, une éclaircie). Le
 * passage d'un profil à l'autre prend 1,5 à 3 minutes (fondu côté client). Le temps clair reste le plus fréquent :
 * les armes à longue portée gardent tout leur intérêt.
 * Commandes (op) : /brume info, /brume <clair|leger|moyen|fort|puree> [secondes], /brume auto ; le réglage est retenu
 * dans le monde (data/heraliz_brume.json). 10/10 : brouillards plus légers et plus courts, purée jamais tirée.
 */
@Mod.EventBusSubscriber(modid = HeralizDecors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MeteoServeur {
    private MeteoServeur() {}

    private static Ambiance.Profil profil = Ambiance.Profil.CLAIR;
    private static float transition = 0;
    private static long finTicks = 0;                    // tick du serveur où l'on change de profil
    private static boolean force = false;                // fixé à la main (/brume) : plus de tirage
    private static final Random R = new Random();

    /** Nouveau monde (en solo, on peut en ouvrir plusieurs sans quitter le jeu) : on repart de zéro. Le monde peut
     *  couper le tirage (data/heraliz_brume.json : {"auto": false}, écrit par /brume) : ciel clair fixe. */
    @SubscribeEvent
    public static void demarrage(ServerStartedEvent e) {
        profil = Ambiance.Profil.CLAIR;
        transition = 0;
        finTicks = 0;
        force = false;
        java.nio.file.Path f = fichier(e.getServer());
        try {
            if (java.nio.file.Files.exists(f)) {
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject();
                if (o.has("auto") && !o.get("auto").getAsBoolean()) {
                    force = true;
                    Ambiance.Profil p = o.has("profil") ? Ambiance.Profil.de(o.get("profil").getAsString()) : null;
                    profil = p != null ? p : Ambiance.Profil.CLAIR;
                }
            }
        } catch (Exception ex) {
            HeralizDecors.LOG.warn("Brume : {} illisible ({})", f, ex.toString());
        }
        HeralizDecors.LOG.info("Brume : {}", force ? "fixée (" + profil.nom + ")" : "tirage automatique");
    }

    private static java.nio.file.Path fichier(MinecraftServer srv) {
        return srv.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve("heraliz_brume.json");
    }

    /** Retient le réglage dans le monde (survit au redémarrage). */
    private static void retenir(MinecraftServer srv) {
        java.nio.file.Path f = fichier(srv);
        try {
            java.nio.file.Files.createDirectories(f.getParent());
            java.nio.file.Files.writeString(f, "{\"auto\": " + !force + ", \"profil\": \"" + profil.nom + "\"}");
        } catch (Exception ex) {
            HeralizDecors.LOG.warn("Brume : impossible d'écrire {} ({})", f, ex.toString());
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || force) return;
        MinecraftServer srv = e.getServer();
        long t = srv.getTickCount();
        if (t % 20 != 0) return;
        if (finTicks == 0) {                             // démarrage : un profil au hasard, sans fondu
            changer(srv, tirer(srv), 0f, true);
            return;
        }
        if (t >= finTicks) changer(srv, tirer(srv), 90 + R.nextInt(91), true);
    }

    /** Poids de chaque profil : heure (aube et nuit plus brumeuses), pluie, profil précédent (pas deux gros brouillards
     *  de suite). Clair et léger restent largement majoritaires. */
    private static Ambiance.Profil tirer(MinecraftServer srv) {
        ServerLevel o = srv.overworld();
        long jour = o.getDayTime() % 24000;
        boolean aube = jour >= 22500 || jour < 1500;
        boolean nuit = jour >= 13000 && jour < 22500;
        boolean pluie = o.isRaining();
        double[] w = {40, 38, 15, 7, 0};                                      // clair, léger, moyen, fort, purée (jamais)
        if (aube) {
            w[1] += 8;
            w[2] += 6;
            w[3] += 3;
            w[0] -= 12;
        }
        if (nuit) {
            w[1] += 4;
            w[2] += 3;
        }
        if (pluie) {
            w[0] -= 18;
            w[2] += 8;
            w[3] += 4;
        }
        if (profil == Ambiance.Profil.FORT || profil == Ambiance.Profil.MOYEN) {   // après un brouillard : éclaircie
            w[3] = 0;
            w[2] *= 0.4;
            w[0] += 15;
        }
        double tot = 0;
        for (int i = 0; i < w.length; i++) {
            w[i] = i == 4 ? 0 : Math.max(i == 3 && w[i] == 0 ? 0 : 1, w[i]);
            tot += w[i];
        }
        double u = R.nextDouble() * tot;
        for (int i = 0; i < w.length; i++) {
            u -= w[i];
            if (u <= 0) return Ambiance.Profil.values()[i];
        }
        return Ambiance.Profil.CLAIR;
    }

    private static void changer(MinecraftServer srv, Ambiance.Profil p, float trans, boolean auto) {
        profil = p;
        transition = trans;
        int minutes = switch (p) {                                            // le brouillard épais ne dure pas
            case CLAIR, LEGER -> 12 + R.nextInt(14);
            case MOYEN -> 5 + R.nextInt(6);
            case FORT -> 3 + R.nextInt(3);
            case PUREE -> 2 + R.nextInt(3);
        };
        finTicks = srv.getTickCount() + minutes * 1200L + (long) (trans * 20);
        Ambiance.CANAL.send(PacketDistributor.ALL.noArg(), new Ambiance.Brume(p.ordinal(), trans));
        HeralizDecors.LOG.info("Brume : {} pendant {} min (fondu {} s{})", p.nom, minutes, (int) trans, auto ? "" : ", à la main");
    }

    @SubscribeEvent
    public static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            Ambiance.CANAL.send(PacketDistributor.PLAYER.with(() -> sp), new Ambiance.Brume(profil.ordinal(), 0f));
        }
    }

    @SubscribeEvent
    public static void commandes(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("brume").requires(s -> s.hasPermission(2))
                .then(Commands.literal("info").executes(c -> {
                    MinecraftServer srv = c.getSource().getServer();
                    long reste = Math.max(0, finTicks - srv.getTickCount()) / 20;
                    c.getSource().sendSuccess(() -> Component.literal("Brume : " + profil.nom + (force ? " (fixée)" : ", change dans "
                            + reste / 60 + " min " + reste % 60 + " s")), false);
                    return 1;
                }))
                .then(Commands.literal("auto").executes(c -> {
                    force = false;
                    finTicks = 1;
                    retenir(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal("Brume : tirage automatique"), true);
                    return 1;
                }))
                .then(Commands.argument("profil", StringArgumentType.word())
                        .executes(c -> fixer(c.getSource(), StringArgumentType.getString(c, "profil"), 60))
                        .then(Commands.argument("secondes", IntegerArgumentType.integer(0, 600))
                                .executes(c -> fixer(c.getSource(), StringArgumentType.getString(c, "profil"),
                                        IntegerArgumentType.getInteger(c, "secondes"))))));
    }

    private static int fixer(net.minecraft.commands.CommandSourceStack src, String nom, int secondes) {
        Ambiance.Profil p = Ambiance.Profil.de(nom);
        if (p == null) {
            src.sendFailure(Component.literal("Profils : clair, leger, moyen, fort, puree"));
            return 0;
        }
        force = true;
        changer(src.getServer(), p, secondes, false);
        retenir(src.getServer());
        src.sendSuccess(() -> Component.literal("Brume fixée : " + p.nom + " (/brume auto pour reprendre le tirage)"), true);
        return 1;
    }
}
