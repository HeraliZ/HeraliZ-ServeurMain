package fr.heralisz.matchs.zombies.contenu;

import com.mojang.brigadier.CommandDispatcher;
import fr.heralisz.matchs.zombies.contenu.entite.Rodeur;
import fr.heralisz.matchs.zombies.contenu.entite.ZombieLabo;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Partie serveur (docs/METHODE_CLIENT_SERVEUR.md) du module d'entree du contenu zombies (appele par {@code Modules}) : registres (entites, blocs-entites du decor,
 * particules, sons), attributs des creatures, enregistrement de la fabrique ({@link FabriqueContenu}) et du decor
 * ({@link DecorContenu}) aupres du jeu ({@link ZombiesApi}), commandes de developpement {@code /zbdev}.
 * <p>Cote client (rendus, HUD, musique, particules) : {@code zombies/client}, branche par annotations
 * ({@code @Mod.EventBusSubscriber}, bus du mod et bus Forge, client seulement).
 */
public final class ZombiesContenuLogique {
    private ZombiesContenuLogique() {
    }

    public static void construire(IEventBus modBus) {
        // demonstrations du banc (/zbdev fenetre demo)
        MinecraftForge.EVENT_BUS.addListener(ZbDemo::tick);
        ZombiesApi.fabrique(new FabriqueContenu());
        ZombiesApi.decor(new DecorContenu());
    }


    public static void commandes(CommandDispatcher<CommandSourceStack> d, CommandBuildContext c) {
        ZbDev.enregistrer(d);
    }
}
