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
 * Partie commune (registres, paquets) du module ClashWar (MATCHS_SPEC §2), classe d'entree appelee par {@code Modules} : registres (mur-tableau, sons),
 * paquets, commandes, ecouteurs du bus Forge (tick, connexions, evenements du mod clans). Le cote client
 * ({@code clashwar.client.ClashWarClient}) est branche ici par DistExecutor (rendu du mur, HUD).
 */
public final class ClashWar {
    private ClashWar() {
    }

    public static void construire(IEventBus modBus) {
        CwRegistres.enregistrer(modBus);
        // écouteurs, commandes et logique : ClashWarLogique (src/serveur), appelé par Modules juste après
    }

    public static void paquets(MatchsNet.Registre r) {
        CwPaquets.enregistrer(r);
    }
}
