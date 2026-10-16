package fr.heraliz.crac.bases;

import fr.heraliz.crac.Crac;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Le monde des bases au serveur : horloge (chaque tick : départs de /home et /spawn, tutoriel ; chaque seconde :
 * tutoriel, protections, carrières, bordure), sa propre bordure (en Forge 1.20.1, chaque dimension reçoit celle de la
 * carte : elle est remise chaque seconde, comme pour les arènes de HeraliZ Matchs), pas de monstres, commandes.
 * Le monde lui-même est créé par Minecraft au démarrage (data/crac/dimension/bases.json) : rien à déposer.
 */
@EventBusSubscriber(modid = "crac")
public final class MondeBasesServeur {
    private static int ticks;

    private MondeBasesServeur() {
    }

    @SubscribeEvent
    public static void commandes(RegisterCommandsEvent e) {
        Deplacements.commandes(e.getDispatcher());
        CarrieresServeur.commandes(e.getDispatcher());
        Protections.commandes(e.getDispatcher());
        Tutoriel.commandes(e.getDispatcher());
    }

    @SubscribeEvent
    public static void demarre(ServerStartedEvent e) {
        ServerLevel l = e.getServer().getLevel(MondeBases.BASES);
        if (l == null) {
            Crac.LOGGER.warn("[CRac] monde des bases (crac:bases) absent : /home, tutoriel et carrières ne marcheront pas");
            return;
        }
        Crac.LOGGER.info("[CRac] monde des bases prêt ({})", l.dimension().location());
        bordure(l);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer() == null) {
            return;
        }
        MinecraftServer s = e.getServer();
        Deplacements.tick(s);
        Tutoriel.tick(s);
        if (++ticks % 20 != 0) {
            return;
        }
        etape(s, "tutoriel", () -> Tutoriel.seconde(s));
        etape(s, "protections", () -> Protections.seconde(s));
        etape(s, "carrières", () -> CarrieresServeur.seconde(s));
        ServerLevel l = s.getLevel(MondeBases.BASES);
        if (l != null) {
            bordure(l);
        }
    }

    private static void etape(MinecraftServer s, String nom, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            Crac.LOGGER.error("[CRac] monde des bases : {} en erreur", nom, ex);
        }
    }

    // ---------------------------------------------------------------------------------------------- bordure

    /** Bordure propre du monde des bases : centre 0 0, taille {@code [bases] bordure} (0 = celle du jeu). */
    static void bordure(ServerLevel l) {
        int taille = BasesReglages.i(BasesReglages.BORDURE, 6000);
        if (taille <= 0) {
            return;
        }
        WorldBorder b = l.getWorldBorder();
        if (b.getCenterX() == 0.0 && b.getCenterZ() == 0.0 && Math.abs(b.getSize() - taille) < 0.5) {
            return;
        }
        b.setCenter(0.0, 0.0);
        b.setSize(taille);
        ClientboundInitializeBorderPacket p = new ClientboundInitializeBorderPacket(b);
        for (ServerPlayer sp : l.players()) {
            sp.connection.send(p);
        }
    }

    private static void entree(ServerPlayer p) {
        if (MondeBases.estBases(p.level())) {
            bordure(p.serverLevel());
            p.connection.send(new ClientboundInitializeBorderPacket(p.serverLevel().getWorldBorder()));
        }
        CarrieresServeur.envoyer(p);
    }

    @SubscribeEvent
    public static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            entree(p);
        }
    }

    @SubscribeEvent
    public static void changement(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            entree(p);
        }
    }

    @SubscribeEvent
    public static void reapparition(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            entree(p);
        }
    }

    @SubscribeEvent
    public static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        Deplacements.oublier(e.getEntity().getUUID());
        Tutoriel.oublier(e.getEntity().getUUID());
    }

    // ---------------------------------------------------------------------------------------------- monstres

    /** Aucun monstre n'apparaît de lui-même dans le monde des bases ({@code [bases] monstres = false}). */
    @SubscribeEvent
    public static void apparition(MobSpawnEvent.FinalizeSpawn e) {
        MobSpawnType t = e.getSpawnType();
        if (t != MobSpawnType.NATURAL && t != MobSpawnType.CHUNK_GENERATION && t != MobSpawnType.PATROL && t != MobSpawnType.REINFORCEMENT
            && t != MobSpawnType.EVENT && t != MobSpawnType.JOCKEY) {
            return;
        }
        if (e.getEntity() instanceof Enemy && MondeBases.estBases(e.getLevel().getLevel()) && !BasesReglages.b(BasesReglages.MONSTRES, false)) {
            e.setSpawnCancelled(true);
        }
    }
}
