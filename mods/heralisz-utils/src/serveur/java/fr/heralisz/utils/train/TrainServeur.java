package fr.heralisz.utils.train;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Le train de HeraliZ, côté serveur (jar fusionné : partie serveur de Train, code d'origine déplacé tel quel) : données du
 * monde « heralisz_train » (actif + décalage de l'horaire), annonce aux joueurs, commandes /train, montée et descentes
 * (filet de sécurité : téléportation, changement de monde, mort, déconnexion), paquets Marche / Descente reçus.
 */
public final class TrainServeur {
    private TrainServeur() {}

    /** Branché par la partie serveur (serveur.PartieServeur), dans l'ordre d'origine des écouteurs. */
    public static void init() {
        Train.RECEPTION_SERVEUR.brancher(TrainServeur::recevoir);
        MinecraftForge.EVENT_BUS.addListener(TrainServeur::connexion);
        MinecraftForge.EVENT_BUS.addListener(TrainServeur::commandes);
        MinecraftForge.EVENT_BUS.addListener(TrainServeur::tickMonde);
        MinecraftForge.EVENT_BUS.addListener(TrainServeur::deconnexion);
        MinecraftForge.EVENT_BUS.addListener(TrainServeur::monture);
        // LOWEST : on ne fait descendre que si la téléportation a vraiment lieu (pas annulée par un autre mod)
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, TrainServeur::teleportation);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, TrainServeur::dimension);
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, TrainServeur::mort);
        MinecraftForge.EVENT_BUS.addListener(TrainServeur::dimensionChangee);
    }

    /** Paquets client -> serveur (traitements d'origine de Train.init). */
    private static void recevoir(Train.Recu r) {
        if (r.paquet() instanceof Train.Marche m) {
            VoyageursServeur.marche(r.joueur(), m.vehicule(), m.lx(), m.lz(), m.assis());
        } else if (r.paquet() instanceof Train.Descente) {
            VoyageursServeur.descenteDemandee(r.joueur(), "Maj");
        }
    }

    static void recadrer(ServerPlayer p, int v, float x, float z) {
        Train.CANAL.send(PacketDistributor.PLAYER.with(() -> p), new Train.Recadrer(v, x, z));
    }

    private static void tickMonde(TickEvent.LevelTickEvent e) {
        if (e.phase == TickEvent.Phase.END && !e.level.isClientSide && e.level.dimension() == Level.OVERWORLD) {
            VoyageursServeur.tickMonde((ServerLevel) e.level);
        }
    }

    private static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) VoyageursServeur.deconnexion(sp);
    }

    /** Aucune descente n'est plus annulée : Maj est effacée avant le tick du passager (VoyageursServeur.tickServeur) et passe
     *  par le paquet Descente ; toute autre descente (commande, autre mod, mort...) va vers la sortie sûre
     *  (Voyageur.getDismountLocationForPassenger). On note seulement qui vient de descendre (pas de remontée immédiate). */
    private static void monture(EntityMountEvent e) {
        if (!e.getLevel().isClientSide() && e.isDismounting() && e.getEntityBeingMounted() instanceof Voyageur
                && e.getEntityMounting() instanceof ServerPlayer sp) {
            VoyageursServeur.descendu(sp);
        }
    }

    /** Filet de sécurité : un passager téléporté (commande, perle, autre mod via l'événement), qui change de dimension
     *  ou qui meurt descend d'abord proprement (sinon le serveur le garde passager et sa position reste figée). */
    private static void teleportation(net.minecraftforge.event.entity.EntityTeleportEvent e) {
        if (e.getEntity().getVehicle() instanceof Voyageur v && !e.getEntity().level().isClientSide()) {
            VoyageursServeur.liberer(v, e.getEntity(), new net.minecraft.world.phys.Vec3(e.getTargetX(), e.getTargetY(), e.getTargetZ()));
        }
    }

    private static void dimension(net.minecraftforge.event.entity.EntityTravelToDimensionEvent e) {
        if (e.getEntity().getVehicle() instanceof Voyageur v && !e.getEntity().level().isClientSide()) {
            VoyageursServeur.liberer(v, e.getEntity(), null);
        }
    }

    private static void dimensionChangee(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity().getVehicle() instanceof Voyageur v && e.getEntity() instanceof ServerPlayer sp) {
            VoyageursServeur.liberer(v, sp, null);                                     // véhicule resté dans l'autre niveau
        }
    }

    private static void mort(net.minecraftforge.event.entity.living.LivingDeathEvent e) {
        if (e.getEntity().getVehicle() instanceof Voyageur v && !e.getEntity().level().isClientSide()) {
            VoyageursServeur.liberer(v, e.getEntity(), VoyageursServeur.sortie(v, e.getEntity()));
        }
    }

    /** Données du monde : le train roule-t-il ici, et de combien l'horaire est-il décalé (ticks). */
    public static final class Donnees extends SavedData {
        public boolean actif;
        public long decalage;
        /** Réseau de ce monde (fichier du jar) : « reseau » (ancienne map, par défaut) ou « reseau_v2 » (nouvelle map). */
        public String reseau = "reseau";

        static Donnees lire(CompoundTag t) {
            Donnees d = new Donnees();
            d.actif = t.getBoolean("actif");
            d.decalage = t.getLong("decalage");
            if (t.contains("reseau")) d.reseau = t.getString("reseau");
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            t.putBoolean("actif", actif);
            t.putLong("decalage", decalage);
            t.putString("reseau", reseau);
            return t;
        }
    }

    public static Donnees donnees(MinecraftServer server) {
        ServerLevel o = server.overworld();
        Donnees d = o.getDataStorage().computeIfAbsent(Donnees::lire, Donnees::new, "heralisz_train");
        Reseau.choisir(d.reseau);
        return d;
    }

    /** Le train roule-t-il sur ce serveur (monde de la map, réseau présent dans le jar) ? */
    public static boolean actif(MinecraftServer server) {
        if (server == null) return false;
        Donnees d = donnees(server);
        return Horaire.get() != null && d.actif;
    }

    /** Secondes de l'horaire côté serveur. */
    public static double temps(MinecraftServer server) {
        return (server.overworld().getGameTime() + donnees(server).decalage) / 20.0;
    }

    private static void annoncer(MinecraftServer server, ServerPlayer p) {
        Donnees d = donnees(server);
        Train.Annonce a = new Train.Annonce(d.actif && Horaire.get() != null, d.decalage, d.reseau);
        if (p != null) {
            Train.CANAL.send(PacketDistributor.PLAYER.with(() -> p), a);
        } else {
            Train.CANAL.send(PacketDistributor.ALL.noArg(), a);
        }
    }

    private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.getServer() != null) {
            annoncer(sp.getServer(), sp);
            if (sp.getVehicle() instanceof Voyageur) VoyageursServeur.deconnexion(sp);   // ancien monde : réveil sur le quai
        }
    }

    private static String duree(double sec) {
        int s = (int) Math.round(sec);
        return s >= 60 ? (s / 60) + " min " + (s % 60) + " s" : s + " s";
    }

    /** Cale l'horaire pour que le train soit à quai à la gare (secondes depuis son arrivée ; négatif : avant). */
    private static int placer(net.minecraft.commands.CommandSourceStack src, String nom, int secondes) {
        Horaire h = Horaire.get();
        if (h == null) return 0;
        int k = -1;
        for (int i = 0; i < h.reseau.gares.length; i++) {
            if (h.reseau.gares[i].nom().equalsIgnoreCase(nom) || String.valueOf(i).equals(nom)) k = i;
        }
        if (k < 0) {
            src.sendFailure(Component.literal("Gare inconnue : " + nom));
            return 0;
        }
        MinecraftServer srv = src.getServer();
        Donnees d = donnees(srv);
        double voulu = h.debutArret[k] + secondes;                         // instant voulu dans le cycle
        long t = srv.overworld().getGameTime();
        long per = Math.round(h.periode * 20);
        long dec = Math.floorMod(Math.round(voulu * 20) - t, per);
        d.decalage = dec;
        d.setDirty();
        annoncer(srv, null);
        String g = h.reseau.gares[k].nom();
        src.sendSuccess(() -> Component.literal("Train calé à " + g + (secondes != 0 ? " (" + secondes + " s)" : "")), true);
        return 1;
    }

    private static void commandes(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("train").requires(s -> s.hasPermission(2))
                .then(Commands.literal("info").executes(c -> {
                    MinecraftServer srv = c.getSource().getServer();
                    Horaire h = Horaire.get();
                    if (h == null) {
                        c.getSource().sendFailure(Component.literal("Pas de réseau ferré dans ce jar."));
                        return 0;
                    }
                    Reseau r = h.reseau;
                    for (int rame = 0; rame < Horaire.RAMES; rame++) {
                        Horaire.Etat et = h.etatRame(temps(srv), rame);
                        double[] p = new double[3];
                        r.point(et.s(), p);
                        String ou = et.aQuai() >= 0 ? "à quai à " + r.gares[et.aQuai()].nom() + ", départ dans " + duree(et.reste())
                                : "en route vers " + r.gares[et.suivante()].nom() + String.format(" à %.0f km/h", et.v() * 3.6);
                        int n = rame + 1;
                        c.getSource().sendSuccess(() -> Component.literal(String.format("Train %d (%s, réseau %s) %s : %s (x %.0f, y %.0f, z %.0f). Tour : %s.",
                                n, donnees(srv).actif ? "actif" : "ARRÊTÉ", Reseau.nom(), "", ou, p[0], p[1], p[2], duree(h.periode))), false);
                    }
                    return 1;
                }))
                .then(Commands.literal("reseau").then(Commands.argument("nom", StringArgumentType.word()).executes(c -> {
                    MinecraftServer srv = c.getSource().getServer();
                    Donnees d = donnees(srv);
                    d.reseau = StringArgumentType.getString(c, "nom");
                    d.setDirty();
                    Reseau.choisir(d.reseau);
                    annoncer(srv, null);
                    boolean ok = Horaire.get() != null;
                    c.getSource().sendSuccess(() -> Component.literal("Réseau ferré : " + d.reseau + (ok ? "" : " (introuvable dans le jar : pas de train)")), true);
                    return ok ? 1 : 0;
                })))
                .then(Commands.literal("actif").then(Commands.argument("oui", BoolArgumentType.bool()).executes(c -> {
                    MinecraftServer srv = c.getSource().getServer();
                    Donnees d = donnees(srv);
                    d.actif = BoolArgumentType.getBool(c, "oui");
                    d.setDirty();
                    annoncer(srv, null);
                    c.getSource().sendSuccess(() -> Component.literal("Train " + (d.actif ? "en service" : "arrêté")), true);
                    return 1;
                })))
                .then(Commands.literal("gare").then(Commands.argument("nom", StringArgumentType.word())
                        .executes(c -> placer(c.getSource(), StringArgumentType.getString(c, "nom"), 0))
                        .then(Commands.argument("secondes", IntegerArgumentType.integer())
                                .executes(c -> placer(c.getSource(), StringArgumentType.getString(c, "nom"), IntegerArgumentType.getInteger(c, "secondes"))))))
                .then(Commands.literal("decalage").then(Commands.argument("secondes", IntegerArgumentType.integer()).executes(c -> {
                    MinecraftServer srv = c.getSource().getServer();
                    Donnees d = donnees(srv);
                    d.decalage += 20L * IntegerArgumentType.getInteger(c, "secondes");
                    d.setDirty();
                    annoncer(srv, null);
                    c.getSource().sendSuccess(() -> Component.literal("Horaire décalé (total " + d.decalage / 20 + " s)"), true);
                    return 1;
                }))));
    }
}
