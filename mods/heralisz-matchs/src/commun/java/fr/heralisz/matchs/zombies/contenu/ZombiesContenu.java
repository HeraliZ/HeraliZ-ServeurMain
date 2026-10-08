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
 * Partie commune (registres, paquets) du module d'entree du contenu zombies (appele par {@code Modules}) : registres (entites, blocs-entites du decor,
 * particules, sons), attributs des creatures, enregistrement de la fabrique ({@link FabriqueContenu}) et du decor
 * ({@link DecorContenu}) aupres du jeu ({@link ZombiesApi}), commandes de developpement {@code /zbdev}.
 * <p>Cote client (rendus, HUD, musique, particules) : {@code zombies/client}, branche par annotations
 * ({@code @Mod.EventBusSubscriber}, bus du mod et bus Forge, client seulement).
 */
public final class ZombiesContenu {
    private ZombiesContenu() {
    }

    public static void construire(IEventBus modBus) {
        ZbRegistres.enregistrer(modBus);
        modBus.addListener(ZombiesContenu::attributs);
        // reglages client (musique) : ignores par un serveur dedie
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ZbConfigClient.SPEC, "heralisz_matchs-zombies-client.toml");
        // écouteurs, commandes et logique : ZombiesContenuLogique (src/serveur), appelé par Modules juste après
    }

    private static void attributs(EntityAttributeCreationEvent e) {
        e.put(ZbRegistres.ZOMBIE_LABO.get(), ZombieLabo.attributs().build());
        e.put(ZbRegistres.RODEUR.get(), Rodeur.attributs().build());
    }
}
