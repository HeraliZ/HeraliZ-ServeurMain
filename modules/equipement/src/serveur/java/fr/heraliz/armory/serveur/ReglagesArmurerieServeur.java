package fr.heraliz.armory.serveur;

import fr.heraliz.armory.armor.ArmorConfig;
import fr.heraliz.armory.armor.ReglagesArmurerie;
import fr.heraliz.armory.network.ArmoryNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Réglages de l'Armurerie, côté serveur seulement (audit du 02/11, point 11, §4 de split) : déclarés ici en COMMON, avec
 * le même nom de fichier qu'avant (config/heraliz_armory-common.toml du serveur, valeurs gardées), ils ne sont plus écrits
 * sur le PC des joueurs. À chaque chargement du fichier, ce que lit le code commun est recopié dans
 * {@link ReglagesArmurerie} ; ce dont le jeu du joueur a besoin (nage, bouteille, slots bloqués) part chez chaque joueur à
 * la connexion, et chez tous quand le fichier change. Protections, plafond et effets bloqués ne quittent jamais le serveur.
 */
public final class ReglagesArmurerieServeur {
    private ReglagesArmurerieServeur() {}

    static void brancher(IEventBus busMod) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ArmorConfig.SPEC, "heraliz_armory-common.toml");
        busMod.addListener((ModConfigEvent.Loading e) -> {
            if (e.getConfig().getSpec() == ArmorConfig.SPEC) appliquer(false);
        });
        busMod.addListener((ModConfigEvent.Reloading e) -> {
            if (e.getConfig().getSpec() == ArmorConfig.SPEC) appliquer(true);
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer joueur) {
                ArmoryNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> joueur), ArmoryNetwork.ReglagesJoueur.actuels());
            }
        });
    }

    private static void appliquer(boolean envoyer) {
        ReglagesArmurerie.definirJoueur(ArmorConfig.SWIM_BOOST.get(), ArmorConfig.TANK_SECONDS.get(), ArmorConfig.REFILL_SECONDS.get(),
                ArmorConfig.SUIT_BLOCKED_SLOTS.get());
        ReglagesArmurerie.definirPlafond(ArmorConfig.PLAFOND.get());
        MinecraftServer serveur = ServerLifecycleHooks.getCurrentServer();
        // fichier relu par Forge sur son propre fil : l'envoi se fait sur le fil du serveur
        if (envoyer && serveur != null) {
            serveur.execute(() -> ArmoryNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), ArmoryNetwork.ReglagesJoueur.actuels()));
        }
    }
}
