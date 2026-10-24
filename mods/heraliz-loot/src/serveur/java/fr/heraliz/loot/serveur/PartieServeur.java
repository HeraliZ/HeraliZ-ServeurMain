package fr.heraliz.loot.serveur;

import fr.heraliz.loot.ConfigLoot;
import fr.heraliz.loot.LootServeur;
import fr.heraliz.loot.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Partie serveur de HeraliZ Loot (docs/METHODE_CLIENT_SERVEUR.md §4) : tables et tirages, zones et rotation du loot,
 * coffres T7 (loot, identification, réapparitions), commandes ; réglages serveur {@code heraliz_loot-server.toml}
 * ({@link ConfigLoot}, enregistrés ici : appelé pendant la construction du mod, même nom de fichier qu'avant).
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ConfigLoot.SPEC, "heraliz_loot-server.toml");
        LootServeur.brancher(busMod);
    }
}
