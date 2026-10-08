package fr.heralisz.economie.serveur;

import fr.heraliz.clansetbases.pont.Partie;
import fr.heralisz.economie.api.EconomieApiServeur;
import fr.heralisz.economie.server.ComptesStore;
import fr.heralisz.economie.server.EconomieCommands;
import fr.heralisz.economie.server.ServerEvents;
import fr.heralisz.economie.vault.VaultPont;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie serveur de HeraliZ Economie : les comptes derriere l'API, puis les ecouteurs du bus Forge dans l'ordre
 * d'origine (store, evenements de connexion, commandes /eco /payer /solde, pont Vault).
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        EconomieApiServeur.installer();
        MinecraftForge.EVENT_BUS.register(ComptesStore.class);
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
        MinecraftForge.EVENT_BUS.register(EconomieCommands.class);
        MinecraftForge.EVENT_BUS.register(VaultPont.class);
    }
}
