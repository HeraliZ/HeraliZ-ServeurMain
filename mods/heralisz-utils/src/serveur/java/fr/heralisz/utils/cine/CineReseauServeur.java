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
 * première connexion (pose au point de réveil, lancement 3 s après), réapparition sans lit, /cinematique (rejouer,
 * joueur, info, reveil, epave ...) et envoi du décor aux joueurs.
 */
public final class CineReseauServeur {
    private CineReseauServeur() {}

    /** Branché par la partie serveur (serveur.PartieServeur), dans l'ordre d'origine des écouteurs. */
    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(CineReseauServeur::connexion);
        MinecraftForge.EVENT_BUS.addListener(CineReseauServeur::reapparition);
        MinecraftForge.EVENT_BUS.addListener(CineReseauServeur::tick);
        MinecraftForge.EVENT_BUS.addListener(CineReseauServeur::commandes);
    }

    private static final String VUE = "heraliz_cinematique_vue";
    private static final int RAYON_REFUGE = 160;
    private static final Map<UUID, Integer> EN_ATTENTE = new HashMap<>();
    /** Le point de réveil est-il praticable dans ce monde (place libre, sol dessous) ? */
    private static boolean decorPresent(MinecraftServer server) {
        CineDecorServeur d = CineDecorServeur.get(server);
        return CineDecorServeur.libre(d.niveau(server), d.etat().reveil());
    }

    /** Pose le joueur au point de réveil, le regard réglé (le train couché derrière lui). */
    public static void allerAuReveil(ServerPlayer sp) {
        CineDecorServeur dec = CineDecorServeur.get(sp.server);
        CineDecor.Etat d = dec.etat();
        sp.teleportTo(dec.niveau(sp.server), d.reveil().x, d.reveil().y, d.reveil().z, d.cap(), 0f);
    }

    /** Mort sans lit ni ancre : on se réveille au spawn (dans son monde à part), sans la cinématique. */
    private static void reapparition(PlayerEvent.PlayerRespawnEvent e) {
        if (e.isEndConquered() || !(e.getEntity() instanceof ServerPlayer sp) || sp.getRespawnPosition() != null) {
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
            envoyer(sp.server, sp);
            if (!donnees(sp).getBoolean(VUE)) {
                if (decorPresent(sp.server)) {
                    allerAuReveil(sp);                              // tout de suite dans le monde du spawn (rien vu d'autre)
                }
                EN_ATTENTE.put(sp.getUUID(), 60);                   // cinématique 3 s après l'arrivée
            }
        }
    }

    /** Envoie le décor à un joueur, ou à tous (sp null). */
    private static void envoyer(MinecraftServer server, ServerPlayer sp) {
        CineReseau.Decor m = new CineReseau.Decor(CineDecorServeur.get(server).etat());
        CineReseau.CANAL.send(sp == null ? PacketDistributor.ALL.noArg() : PacketDistributor.PLAYER.with(() -> sp), m);
    }

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || EN_ATTENTE.isEmpty() || e.getServer() == null) {
            return;
        }
        EN_ATTENTE.replaceAll((k, v) -> v - 1);
        EN_ATTENTE.entrySet().removeIf(en -> {
            if (en.getValue() > 0) {
                return false;
            }
            ServerPlayer sp = e.getServer().getPlayerList().getPlayer(en.getKey());
            if (sp != null && decorPresent(e.getServer())) {
                allerAuReveil(sp);
                jouer(sp);
                donnees(sp).putBoolean(VUE, true);
            }
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
