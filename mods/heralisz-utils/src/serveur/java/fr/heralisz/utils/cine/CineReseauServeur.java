package fr.heralisz.utils.cine;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Cinématique d'arrivée, côté serveur (jar fusionné : partie serveur de CineReseau, code d'origine déplacé tel quel) :
 * première connexion (pose au point de réveil, lancement 3 s après, une fois le joueur connecté sur AuthMe et son
 * personnage fait dans CaraCrea : {@link PontArrivee}), réapparition sans lit, /cinematique (rejouer, joueur, info,
 * reveil, epave ...) et envoi du décor aux joueurs. La fin de la cinématique (vue jusqu'au bout ou passée) est signalée
 * par le joueur (paquet Fin) et notée dans ses données ({@value #FINIE}, lue par HeraliZ Quêtes : « Allez voir le
 * Commandant Varga ») ; sans nouvelles 4 minutes après le lancement, elle est notée finie quand même.
 */
public final class CineReseauServeur {
    private CineReseauServeur() {}

    /** Branché par la partie serveur (serveur.PartieServeur), dans l'ordre d'origine des écouteurs. */
    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(CineReseauServeur::connexion);
        MinecraftForge.EVENT_BUS.addListener(CineReseauServeur::reapparition);
        MinecraftForge.EVENT_BUS.addListener(CineReseauServeur::tick);
        MinecraftForge.EVENT_BUS.addListener(CineReseauServeur::commandes);
        CineReseau.FIN_SERVEUR.brancher(CineReseauServeur::fin);
    }

    private static final String VUE = "heraliz_cinematique_vue";
    /** Donnée persistante (PlayerPersisted) : la cinématique d'arrivée est finie (lue par son nom par HeraliZ Quêtes). */
    public static final String FINIE = "heraliz_cinematique_finie";
    /** Sans nouvelles du joueur (paquet Fin) ce temps après le lancement, la cinématique est notée finie. */
    private static final int FIN_MAX_TICKS = 4 * 60 * 20;
    /** Cinématiques d'arrivée lancées, en attente de leur fin : joueur -> ticks restants. */
    private static final Map<UUID, Integer> JOUEES = new HashMap<>();
    private static final int RAYON_REFUGE = 160;
    private static final Map<UUID, Integer> EN_ATTENTE = new HashMap<>();
    /** Le point de réveil est-il praticable dans ce monde (place libre, sol dessous) ? */
    private static boolean decorPresent(MinecraftServer server) {
        CineDecorServeur d = CineDecorServeur.get(server);
        return CineDecorServeur.libre(d.niveau(server), d.etat().reveil());
    }

    /** Le point de réveil est-il praticable (monde du spawn déposé, place libre, sol dessous) ? */
    public static boolean reveilPraticable(MinecraftServer server) {
        return decorPresent(server);
    }

    /** SpawnApi.allerAuSpawn (extraction...) : pose le joueur au point de réveil s'il est praticable, sans cinématique. */
    public static boolean auSpawn(ServerPlayer sp) {
        if (!decorPresent(sp.server)) {
            return false;
        }
        sp.stopRiding();
        allerAuReveil(sp);
        sp.resetFallDistance();
        return true;
    }

    /** Pose le joueur au point de réveil, le regard réglé (le train couché derrière lui). */
    public static void allerAuReveil(ServerPlayer sp) {
        CineDecorServeur dec = CineDecorServeur.get(sp.server);
        CineDecor.Etat d = dec.etat();
        net.minecraft.server.level.ServerLevel l = dec.niveau(sp.server);
        net.minecraft.world.phys.Vec3 p = placeLibre(l, sp, d.reveil());
        sp.teleportTo(l, p.x, p.y, p.z, d.cap(), 0f);
    }

    /**
     * Plusieurs arrivants en même temps ne se posent pas les uns dans les autres : si quelqu'un est déjà au point de
     * réveil, on prend la première place libre (sol dessous, personne) sur des cercles de 1,5 à 9 blocs autour (plus de
     * 100 places, Utils 2.24.0 : arrivées de 20 à 30 joueurs à la fois) ; sinon le point de réveil lui-même.
     */
    private static net.minecraft.world.phys.Vec3 placeLibre(net.minecraft.server.level.ServerLevel l, ServerPlayer sp,
                                                            net.minecraft.world.phys.Vec3 r) {
        if (!occupee(l, sp, r)) {
            return r;
        }
        for (double rayon = 1.5; rayon <= 9.0; rayon += 1.25) {
            int n = (int) Math.ceil(rayon * 4);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                net.minecraft.world.phys.Vec3 p = r.add(Math.cos(a) * rayon, 0, Math.sin(a) * rayon);
                if (!occupee(l, sp, p) && CineDecorServeur.libre(l, p)) {
                    return p;
                }
            }
        }
        return r;
    }

    private static boolean occupee(net.minecraft.server.level.ServerLevel l, ServerPlayer sp, net.minecraft.world.phys.Vec3 p) {
        for (ServerPlayer o : l.players()) {
            if (o != sp && o.position().distanceToSqr(p) < 0.9 * 0.9) {
                return true;
            }
        }
        return false;
    }

    /** Mort sans lit ni ancre : on se réveille au spawn (dans son monde à part), sans la cinématique. */
    private static void reapparition(PlayerEvent.PlayerRespawnEvent e) {
        if (e.isEndConquered() || !(e.getEntity() instanceof ServerPlayer sp) || sp.getRespawnPosition() != null
                || sp.getTags().contains(fr.heralisz.utils.bots.Bots.TAG)) {
            return;
        }
        if (decorPresent(sp.server)) {
            allerAuReveil(sp);
        }
    }

    private static CompoundTag donnees(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            if (fr.heralisz.utils.bots.Bots.estBot(sp)) {         // bots de charge : ni cinématique ni monde du spawn
                donnees(sp).putBoolean(VUE, true);
                donnees(sp).putBoolean(FINIE, true);
                return;
            }
            envoyer(sp.server, sp);
            if (!donnees(sp).getBoolean(VUE)) {
                if (decorPresent(sp.server)) {
                    allerAuReveil(sp);                              // tout de suite dans le monde du spawn (rien vu d'autre)
                }
                EN_ATTENTE.put(sp.getUUID(), 60);                   // cinématique 3 s après l'arrivée (puis login et personnage)
            } else if (!donnees(sp).getBoolean(FINIE)) {
                donnees(sp).putBoolean(FINIE, true);                // parti pendant la cinématique : elle ne se rejoue pas
            }
        }
    }

    /** Le joueur signale la fin de sa cinématique (vue jusqu'au bout ou passée). */
    private static void fin(ServerPlayer sp) {
        JOUEES.remove(sp.getUUID());
        if (donnees(sp).getBoolean(VUE)) {
            donnees(sp).putBoolean(FINIE, true);
        }
    }

    /** Envoie le décor à un joueur, ou à tous (sp null). */
    private static void envoyer(MinecraftServer server, ServerPlayer sp) {
        CineReseau.Decor m = new CineReseau.Decor(CineDecorServeur.get(server).etat());
        CineReseau.CANAL.send(sp == null ? PacketDistributor.ALL.noArg() : PacketDistributor.PLAYER.with(() -> sp), m);
    }

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer() == null || EN_ATTENTE.isEmpty() && JOUEES.isEmpty()) {
            return;
        }
        MinecraftServer server = e.getServer();
        EN_ATTENTE.replaceAll((k, v) -> v - 1);
        EN_ATTENTE.entrySet().removeIf(en -> {
            if (en.getValue() > 0) {
                return false;
            }
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            if (sp == null) {
                return true;
            }
            if (!decorPresent(server)) {
                donnees(sp).putBoolean(FINIE, true);               // pas de cinématique dans ce monde : rien à attendre
                return true;
            }
            if (!PontArrivee.pret(sp)) {
                en.setValue(20);                                    // /login ou personnage pas encore faits : on revoit dans 1 s
                return false;
            }
            allerAuReveil(sp);
            jouer(sp);
            donnees(sp).putBoolean(VUE, true);
            JOUEES.put(sp.getUUID(), FIN_MAX_TICKS);
            return true;
        });
        JOUEES.replaceAll((k, v) -> v - 1);
        JOUEES.entrySet().removeIf(en -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            if (sp == null) {
                return true;
            }
            if (en.getValue() > 0) {
                return false;
            }
            donnees(sp).putBoolean(FINIE, true);
            return true;
        });
    }

    public static void jouer(ServerPlayer sp) {
        CineReseau.CANAL.send(PacketDistributor.PLAYER.with(() -> sp), new CineReseau.Jouer());
    }

    private static int rejouer(CommandSourceStack src, ServerPlayer sp, boolean force) {
        ServerLevel ow = CineDecorServeur.get(sp.server).niveau(sp.server);
        if (!decorPresent(sp.server)) {
            src.sendFailure(Component.translatableWithFallback("heralisz_utils.cine.reveil_impraticable",
                    "Le point de réveil de la cinématique n'est pas praticable dans ce monde (/cinematique reveil)."));
            return 0;
        }
        Vec3 r = CineDecorServeur.get(sp.server).etat().reveil();
        boolean auRefuge = sp.level() == ow && sp.position().distanceToSqr(r) < RAYON_REFUGE * RAYON_REFUGE;
        if (!force && !auRefuge) {
            src.sendFailure(Component.translatableWithFallback("heralisz_utils.cine.depuis_spawn", "La cinématique se rejoue depuis le spawn."));
            return 0;
        }
        allerAuReveil(sp);
        jouer(sp);
        return 1;
    }

    // ---------------------------------------------------------------------------------------------- réglages (ops)
    private static String pos(double x, double y, double z) {
        return String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", x, y, z);
    }

    private static int info(CommandSourceStack src) {
        CineDecorServeur d = CineDecorServeur.get(src.getServer());
        CineDecor.Etat e = d.etat();
        MutableComponent s = d.regle()
                ? Component.translatableWithFallback("heralisz_utils.cine.info.regle", "Décor réglé en jeu — monde %s", e.monde().toString())
                : Component.translatableWithFallback("heralisz_utils.cine.info.defaut", "Décor par défaut (refuge de la map) — monde %s",
                        e.monde().toString());
        String reveil = pos(e.reveil().x, e.reveil().y, e.reveil().z), cap = String.format(java.util.Locale.ROOT, "%.0f", e.cap());
        s.append("\n").append(decorPresent(src.getServer())
                ? Component.translatableWithFallback("heralisz_utils.cine.info.reveil", "Réveil : %s, regard %s° (praticable)", reveil, cap)
                : Component.translatableWithFallback("heralisz_utils.cine.info.reveil_bloque", "Réveil : %s, regard %s° (PAS praticable : bloc ou vide)",
                        reveil, cap));
        for (CineDecor.Piece p : e.epave()) {
            s.append("\n").append(Component.translatableWithFallback("heralisz_utils.cine.info.epave", "Épave : %s en %s, lacet %s°, roulis %s°",
                    p.modele(), pos(p.x(), p.y(), p.z()), String.format(java.util.Locale.ROOT, "%.0f", p.lacet()),
                    String.format(java.util.Locale.ROOT, "%.0f", p.roulis())));
        }
        src.sendSuccess(() -> s, false);
        return 1;
    }

    private static int reveil(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        CineDecorServeur.get(sp.server).reveil(sp.serverLevel(), sp.position(), sp.getYRot());
        envoyer(sp.server, null);
        src.sendSuccess(() -> Component.translatableWithFallback("heralisz_utils.cine.reveil_pose", "Point de réveil posé en %s, regard dans ta "
                + "direction. L'épave n'a pas bougé : /cinematique epave poser (derrière le réveil) ou /cinematique epave devant (devant toi).",
                pos(sp.getX(), sp.getY(), sp.getZ())), true);
        return 1;
    }

    /** Remplace l'épave par une nouvelle disposition (calculée dans le monde principal) et prévient tout le monde. */
    private static int epave(CommandSourceStack src, Function<ServerLevel, List<CineDecor.Piece>> f, Component quoi) {
        ServerLevel ow = CineDecorServeur.get(src.getServer()).niveau(src.getServer());
        if (src.getLevel() != ow) {
            src.sendFailure(Component.translatableWithFallback("heralisz_utils.cine.epave.mauvais_monde",
                    "L'épave se règle dans le monde du point de réveil (%s).", ow.dimension().location().toString()));
            return 0;
        }
        List<CineDecor.Piece> ep = f.apply(ow);
        int n = CineDecorServeur.get(src.getServer()).epave(ow, ep);
        envoyer(src.getServer(), null);
        src.sendSuccess(() -> Component.translatableWithFallback("heralisz_utils.cine.epave.resultat", "%s (%s pièces, %s barrières invisibles).",
                quoi, String.valueOf(ep.size()), String.valueOf(n)), true);
        return 1;
    }

    /** Nom de l'opération sur l'épave (clé heralisz_utils.cine.epave.<cle>), repris dans la réponse d'epave. */
    private static Component libelle(String cle, String francais) {
        return Component.translatableWithFallback("heralisz_utils.cine.epave." + cle, francais);
    }

    private static List<CineDecor.Piece> decalees(CineDecorServeur d, double dx, double dy, double dz) {
        List<CineDecor.Piece> l = new ArrayList<>();
        for (CineDecor.Piece p : d.etat().epave()) {
            l.add(p.deplacee(dx, dy, dz));
        }
        return l;
    }

    /** Toute l'épave tournée autour de la locomotive (ou de la première pièce). */
    private static List<CineDecor.Piece> tournees(CineDecorServeur d, float deg) {
        List<CineDecor.Piece> ep = d.etat().epave();
        if (ep.isEmpty()) {
            return ep;
        }
        CineDecor.Piece c = ep.stream().filter(p -> p.modele().equals("loco")).findFirst().orElse(ep.get(0));
        double a = Math.toRadians(deg), co = Math.cos(a), si = Math.sin(a);
        List<CineDecor.Piece> l = new ArrayList<>();
        for (CineDecor.Piece p : ep) {
            double dx = p.x() - c.x(), dz = p.z() - c.z();
            l.add(new CineDecor.Piece(p.modele(), c.x() + dx * co - dz * si, p.y(), c.z() + dx * si + dz * co, p.lacet() + deg, p.roulis()));
        }
        return l;
    }

    private static void commandes(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("cinematique")
                .executes(c -> rejouer(c.getSource(), c.getSource().getPlayerOrException(), false))
                .then(Commands.literal("joueur").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("cibles", EntityArgument.players()).executes(c -> {
                            int n = 0;
                            for (ServerPlayer sp : EntityArgument.getPlayers(c, "cibles")) {
                                n += rejouer(c.getSource(), sp, true);
                            }
                            return n;
                        })))
                .then(Commands.literal("info").requires(s -> s.hasPermission(2)).executes(c -> info(c.getSource())))
                .then(Commands.literal("reveil").requires(s -> s.hasPermission(2)).executes(c -> reveil(c.getSource())))
                .then(Commands.literal("epave").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("poser").executes(c -> {
                            CineDecor.Etat et = CineDecorServeur.get(c.getSource().getServer()).etat();
                            return epave(c.getSource(), l -> CineDecorServeur.derriere(l, et.reveil(), et.cap()),
                                    libelle("derriere", "Épave posée derrière le point de réveil"));
                        }))
                        .then(Commands.literal("devant").executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            return epave(c.getSource(), l -> CineDecorServeur.devant(l, sp.position(), sp.getYRot(), 3),
                                    libelle("devant", "Épave posée devant toi"));
                        }).then(Commands.argument("distance", DoubleArgumentType.doubleArg(0, 64)).executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            double dist = DoubleArgumentType.getDouble(c, "distance");
                            return epave(c.getSource(), l -> CineDecorServeur.devant(l, sp.position(), sp.getYRot(), dist),
                                    libelle("devant", "Épave posée devant toi"));
                        })))
                        .then(Commands.literal("decaler")
                                .then(Commands.argument("dx", DoubleArgumentType.doubleArg(-64, 64))
                                        .then(Commands.argument("dy", DoubleArgumentType.doubleArg(-64, 64))
                                                .then(Commands.argument("dz", DoubleArgumentType.doubleArg(-64, 64)).executes(c -> {
                                                    CineDecorServeur dec = CineDecorServeur.get(c.getSource().getServer());
                                                    List<CineDecor.Piece> ep = decalees(dec, DoubleArgumentType.getDouble(c, "dx"),
                                                            DoubleArgumentType.getDouble(c, "dy"), DoubleArgumentType.getDouble(c, "dz"));
                                                    return epave(c.getSource(), l -> ep, libelle("decalee", "Épave décalée"));
                                                })))))
                        .then(Commands.literal("tourner")
                                .then(Commands.argument("degres", FloatArgumentType.floatArg(-360, 360)).executes(c -> {
                                    List<CineDecor.Piece> ep = tournees(CineDecorServeur.get(c.getSource().getServer()), FloatArgumentType.getFloat(c, "degres"));
                                    return epave(c.getSource(), l -> ep, libelle("tournee", "Épave tournée"));
                                })))
                        .then(Commands.literal("retirer").executes(c -> epave(c.getSource(), l -> List.of(), libelle("retiree", "Épave retirée"))))));
    }
}
