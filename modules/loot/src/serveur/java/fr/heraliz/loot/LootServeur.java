package fr.heraliz.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Branchements serveur de HeraliZ Loot (même paquet que les classes du mod : leurs méthodes sont « package-private »).
 * Écouteurs dans l'ordre du constructeur d'origine de {@link HeralizLoot}.
 */
public final class LootServeur {
    private LootServeur() {
    }

    public static void brancher(IEventBus bus) {
        HeralizLoot.COFFRE_USINE.serveur(CoffreBlockEntityServeur::new);
        FouilleServeur.brancher();
        ConteneurBlock.ROTATION = new ConteneurBlock.Rotation() {
            @Override
            public void ouvrir(ServerLevel sl, BlockPos pos, BlockState st, ConteneurBlock bloc, ServerPlayer sp) {
                RotationLoot.ouvrir(sl, pos, st, bloc, sp);
            }

            @Override
            public void blocPose(ServerLevel sl, BlockPos pos, String nom) {
                RotationLoot.blocPose(sl, pos, nom);
            }

            @Override
            public void blocRetire(ServerLevel sl, BlockPos pos, String nom) {
                RotationLoot.blocRetire(sl, pos, nom);
            }
        };
        bus.addListener((ModConfigEvent.Loading e) -> {
            if (e.getConfig().getSpec() == ConfigLoot.SPEC) {
                ZonesLoot.invalider();
                ConteneursVanilla.invalider();
                Presentoirs.invalider();
            }
        });
        bus.addListener((ModConfigEvent.Reloading e) -> {
            if (e.getConfig().getSpec() == ConfigLoot.SPEC) {
                ZonesLoot.invalider();
                ConteneursVanilla.invalider();
                Presentoirs.invalider();
            }
        });
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> {
            e.addListener(new TablesCoffres());
            e.addListener(new ZonesLoot());
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> {
            Commandes.enregistrer(e.getDispatcher());
            CommandesRarete.enregistrer(e.getDispatcher());
        });
        // conteneurs de Minecraft scellés et postes hors service pour les joueurs en survie ([conteneurs_vanilla], [postes_vanilla])
        ConteneursVanilla.brancher();
        // cadres, porte-armures, mannequins, peintures protégés ([presentoirs])
        Presentoirs.brancher();
        // seaux, feu, outils, engrais, panneaux ([nuisances])
        Nuisances.brancher();
        // butin des zombies de la survie (26/10, coffres/zombies*.json, config/heraliz_loot/zombies.json)
        ButinZombies.brancher();
        // raretés des objets : config/heraliz_loot/raretes.json, envoyées aux joueurs
        RaretesServeur.brancher();
        // toutes les secondes : réapparitions des coffres T7 ; rotation du loot (réapparitions, étoiles)
        MinecraftForge.EVENT_BUS.addListener((TickEvent.LevelTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END && e.level instanceof ServerLevel sl && sl.getGameTime() % 20 == 0) {
                PointsT7.get(sl).tick(sl);
                RotationLoot.tick(sl);
            }
        });
        // toutes les secondes, par joueur : nourriture ArkaniaZ venue d'ailleurs que du loot (état fixé, EtatNourriture)
        MinecraftForge.EVENT_BUS.addListener((TickEvent.PlayerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer sp && sp.tickCount % 20 == 0) {
                EtatNourriture.joueur(sp);
            }
        });
    }
}
