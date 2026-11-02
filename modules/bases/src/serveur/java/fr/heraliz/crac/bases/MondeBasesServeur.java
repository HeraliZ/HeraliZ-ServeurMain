package fr.heraliz.crac.bases;

import fr.heraliz.crac.Crac;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * Les bases au serveur (sur la carte depuis le 21/10) : horloge (chaque tick : départs de /home, /rtp et /spawn,
 * messages, tutoriel ; chaque seconde : tutoriel, protections, carrières, bases des clans), commandes. Créatures et seaux
 * du monde du tutoriel : {@link NatureBases}. L'ancien monde des bases (crac:bases), fermé le 02/11 :
 * {@link AncienMondeBases}.
 */
@EventBusSubscriber(modid = "crac")
public final class MondeBasesServeur {
    private static int ticks;

    private MondeBasesServeur() {
    }

    @SubscribeEvent
    public static void commandes(RegisterCommandsEvent e) {
        Deplacements.commandes(e.getDispatcher());
        Homes.commandes(e.getDispatcher());
        CarrieresServeur.commandes(e.getDispatcher());
        Protections.commandes(e.getDispatcher());
        Tutoriel.commandes(e.getDispatcher());
        EffacerBase.commandes(e.getDispatcher());
        FicheBase.commandes(e.getDispatcher());
    }

    @SubscribeEvent
    public static void demarre(ServerStartedEvent e) {
        ServerLevel l = e.getServer().getLevel(MondeBases.BASES);
        Crac.LOGGER.info("[CRac] bases des clans sur {}", l == null ? "?" : l.dimension().location());
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
        etape(s, "bases des clans", () -> fr.heraliz.crac.heraliz.BasesClans.seconde(s));
    }

    private static void etape(MinecraftServer s, String nom, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            Crac.LOGGER.error("[CRac] monde des bases : {} en erreur", nom, ex);
        }
    }

    private static void entree(ServerPlayer p) {
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
}
