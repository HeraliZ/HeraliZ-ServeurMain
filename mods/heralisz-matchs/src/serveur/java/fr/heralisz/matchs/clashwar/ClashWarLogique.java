package fr.heralisz.matchs.clashwar;

import com.mojang.brigadier.CommandDispatcher;
import fr.heralisz.clans.api.events.ClanDissousEvent;
import fr.heralisz.clans.api.events.MembreQuitteEvent;
import fr.heralisz.matchs.clashwar.net.CwPaquets;
import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;

/**
 * Partie serveur (docs/METHODE_CLIENT_SERVEUR.md) du module ClashWar (MATCHS_SPEC §2), classe d'entree appelee par {@code Modules} : registres (mur-tableau, sons),
 * paquets, commandes, ecouteurs du bus Forge (tick, connexions, evenements du mod clans). Le cote client
 * ({@code clashwar.client.ClashWarClient}) est branche ici par DistExecutor (rendu du mur, HUD).
 */
public final class ClashWarLogique {
    private ClashWarLogique() {
    }

    public static void construire(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener(ClashWarLogique::demarrage);
        MinecraftForge.EVENT_BUS.addListener(ClashWarLogique::arret);
        MinecraftForge.EVENT_BUS.addListener(ClashWarLogique::tick);
        MinecraftForge.EVENT_BUS.addListener(ClashWarLogique::connexion);
        MinecraftForge.EVENT_BUS.addListener(ClashWarLogique::clanDissous);
        MinecraftForge.EVENT_BUS.addListener(ClashWarLogique::membreQuitte);
    }


    public static void commandes(CommandDispatcher<CommandSourceStack> d, CommandBuildContext c) {
        CwCommandes.enregistrer(d, c);
    }

    private static void demarrage(ServerStartedEvent e) {
        Tournois.demarrage(e.getServer());
    }

    private static void arret(ServerStoppedEvent e) {
        Tournois.arret();
    }

    private static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) Tournois.tick(e.getServer());
    }

    private static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Tournois.connexion(p);
    }

    private static void clanDissous(ClanDissousEvent e) {
        Tournoi t = Tournois.courant();
        if (t != null && t.actif()) t.clanDissous(e.clanId);
    }

    private static void membreQuitte(MembreQuitteEvent e) {
        Tournoi t = Tournois.courant();
        if (t != null && t.actif()) t.membreParti(e.clanId, e.joueur);
    }
}
